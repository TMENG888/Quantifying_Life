package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.view.KeyEvent;import android.view.inputmethod.*;import org.json.*;

/** Real Android IME and production WebView; synthetic session and no paid model calls. */
public final class ChatInputStabilityTest extends DocumentEditorTest {
 String original="",temporary="";int fullHeight;
 void input(String text)throws Exception{h.js("document.querySelector('#chat-input').value="+JSONObject.quote(text)+";document.querySelector('#chat-input').dispatchEvent(new Event('input'))");}
 void watch()throws Exception{Thread.sleep(250);h.js("window.__input=document.querySelector('#chat-input');window.__blur=0;__input.addEventListener('blur',()=>__blur++);__input.setSelectionRange(3,7)");Thread.sleep(100);}
 void stable(String stage)throws Exception{
  check(h.js("document.querySelector('#chat-input')===__input&&__input.isConnected").equals("true"),stage+": original input node stays connected");
  check(h.js("document.activeElement===__input&&__blur===0").equals("true"),stage+": no blur or focus recovery required");
  check(Integer.parseInt(h.js("innerHeight"))<fullHeight-100,stage+": Android keyboard remains visible");
 }
 void checks()throws Exception{
  h=new MainHost(this);h.open();original=((JSONObject)h.call("init",new JSONObject())).getJSONObject("chatSession").getString("id");temporary=((JSONObject)h.call("newChat",new JSONObject())).getJSONObject("session").getString("id");
  h.call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",new JSONObject().put("id","custom").put("label","Synthetic input test").put("baseUrl","https://127.0.0.1:9/v1").put("model","synthetic").put("protocol","chat").put("effort","off").put("apiKey","synthetic-not-a-real-key"))));
  h.js("window.__reload=true;location.reload()");until("window.__reload===undefined&&!!document.querySelector('.today-workspace')");
  h.js("window.__native=LifeNative;window.__polls=0;window.__reply=null;LifeNative={call:(a,p,id)=>{if(a==='getWikiQueue'){__polls++;setTimeout(()=>nativeReply({id,data:{count:0,nativeClassification:true,settings:{},jobs:[]}}),10);return;}if(a==='ask'){const req=JSON.parse(p);if(req.automaticWiki){nativeReply({id,error:'Synthetic background AI paused'});return;}if(req.tool?.name==='plan_record_search'){nativeReply({id,data:{text:JSON.stringify({intent:'general',topic:'',keywords:[],dateStart:'',dateEnd:'',recordTypes:[]})}});return;}__reply=()=>{nativeReply({id,data:{text:JSON.stringify({answer:'合成测试回复',records:[],citations:[]}),model:'synthetic'}});__reply=null;};return;}__native.call(a,p,id);}};");
  // topicView intentionally remains selected when navigating from statistics to chat.
  click("[data-page=records]");click("#topic-entry");click("[data-page=chat]");fullHeight=Integer.parseInt(h.js("innerHeight"));
  input("后台整理时保留输入内容及选择范围");h.tap("#chat-input",false);until("innerHeight<"+(fullHeight-100));watch();
  h.js("window.__beforePoll=__polls;AutoWiki.resume()");until("__polls>__beforePoll");Thread.sleep(700);stable("Background classification refresh");
  check(h.js("__input.value==='后台整理时保留输入内容及选择范围'&&__input.selectionStart===3&&__input.selectionEnd===7").equals("true"),"Background refresh preserves text and selected range");
  // Send the current request, then compose a new one while its reply is pending.
  input("请直接回答一次测试确认，不要保存记录。");h.tap("[data-action=send]",false);until("!!__reply");input("下一条还没发送的草稿，光标位置应保留。");h.tap("#chat-input",false);watch();
  h.js("__reply()");until("[...document.querySelectorAll('.chat-message')].some(x=>x.textContent.includes('合成测试回复'))");Thread.sleep(400);stable("Model reply arrives while typing");
  check(h.js("__input.value==='下一条还没发送的草稿，光标位置应保留。'&&__input.selectionStart===3&&__input.selectionEnd===7").equals("true"),"Reply preserves unsent draft and selected range: "+h.js("JSON.stringify([__input.value,__input.selectionStart,__input.selectionEnd])"));
  h.js("window.__compositionEnd=0;__input.addEventListener('compositionend',()=>__compositionEnd++)");
  final InputConnection[] connection={null};runOnMainSync(()->{connection[0]=h.web.onCreateInputConnection(new EditorInfo());connection[0].setComposingText("ceshi",1);});until("__input.value.includes('ceshi')");
  String composing=h.js("__input.value");h.js("nativeEvent('attachment',{kind:'text',name:'合成附件.txt',text:'本机测试'})");until("!!document.querySelector('.attachment')");Thread.sleep(200);stable("Attachment arrives during IME composition");
  check(h.js("__input.value").equals(composing)&&h.js("__compositionEnd===0").equals("true"),"Attachment refresh leaves the actual Android composing text intact");
  runOnMainSync(()->connection[0].commitText("测试",1));until("__input.value.includes('测试')&&!__input.value.includes('ceshi')");check(true,"Same Android input connection commits Chinese text after the refresh");
  click("[data-action=remove-attachment]");stable("Attachment removal");
  h.screenshot("chat-input-stable.png");
  // Repeated updates cannot register extra send handlers.
  h.js("window.__pollsBefore=__polls;AutoWiki.resume()");until("__polls>__pollsBefore");Thread.sleep(200);stable("Repeated background update");
  input("请直接回答第二次测试确认，不要保存记录。");h.tap("[data-action=send]",false);until("!!__reply");h.js("__reply()");until("document.querySelectorAll('.chat-message').length===4");Thread.sleep(300);
  check(h.js("document.querySelectorAll('.chat-message.user').length===2").equals("true"),"One send touch produces exactly one user message after repeated refreshes");
  sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(300);input("收起键盘后保留草稿");h.js("window.__input=document.querySelector('#chat-input');window.__pollsBefore=__polls;AutoWiki.resume()");until("__polls>__pollsBefore");Thread.sleep(200);
  check(h.js("document.querySelector('#chat-input')===__input&&__input.value==='收起键盘后保留草稿'").equals("true"),"Refresh also retains the composer after keyboard dismissal");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;String previous=getTargetContext().getSharedPreferences("secure",0).getString("settings",null);try{if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;out.putString("stream","PASS ChatInputStabilityTest: "+passed+" checks\n");}catch(Throwable error){out.putString("stream","FAIL ChatInputStabilityTest after "+passed+": "+android.util.Log.getStackTraceString(error));try{h.screenshot("chat-input-failure.png");}catch(Exception ignored){}}finally{try{if(h!=null){if(!temporary.isEmpty())h.call("deleteChat",new JSONObject().put("sessionId",temporary));if(!original.isEmpty())h.call("getChat",new JSONObject().put("sessionId",original));runOnMainSync(()->h.activity.finish());}}catch(Exception ignored){}android.content.SharedPreferences.Editor ed=getTargetContext().getSharedPreferences("secure",0).edit();if(previous==null)ed.remove("settings");else ed.putString("settings",previous);ed.commit();}finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
