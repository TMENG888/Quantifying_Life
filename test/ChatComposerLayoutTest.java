package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.view.KeyEvent;import org.json.*;

/** Real WebView/keyboard; synthetic temporary session, no model calls or account changes. */
public final class ChatComposerLayoutTest extends DocumentEditorTest {
 String originalSession="",temporarySession="";
 void layout(String stage)throws Exception{
  Bundle geometry=new Bundle();geometry.putString("stream",stage+" geometry: "+h.js("JSON.stringify(['#chat-bottom','.composer','.chat-shell','.chat-footnote'].map(s=>{let n=document.querySelector(s),r=n.getBoundingClientRect();return {s,top:r.top,bottom:r.bottom,left:r.left,right:r.right,b:n.style.bottom}}))")+"\n");sendStatus(0,geometry);
  check(h.js("(()=>{const b=document.querySelector('#chat-bottom').getBoundingClientRect(),c=document.querySelector('.composer').getBoundingClientRect(),s=document.querySelector('.chat-shell').getBoundingClientRect(),send=document.querySelector('.chat-send').getBoundingClientRect();return Math.abs((b.left+b.right-c.left-c.right)/2)<1&&Math.abs(c.top-b.bottom-12)<1&&b.width>=47&&b.height>=47&&b.left>=s.left&&b.right<=s.right&&b.bottom<=send.top&&b.top>=0})()").equals("true"),stage+": centered 48px jump target stays 12px above composer and clear of send");
  check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),stage+": no horizontal overflow");
 }
 void showJump()throws Exception{h.js("document.querySelector('#chat-scroll').scrollTop=0;document.querySelector('#chat-scroll').dispatchEvent(new Event('scroll'))");until("!document.querySelector('#chat-bottom').hidden");Thread.sleep(150);}
 void checks()throws Exception{
  h=new MainHost(this);h.open();JSONObject before=(JSONObject)h.call("init",new JSONObject());originalSession=before.getJSONObject("chatSession").getString("id");JSONObject fresh=(JSONObject)h.call("newChat",new JSONObject());temporarySession=fresh.getJSONObject("session").getString("id");
  for(int i=0;i<36;i++)h.call("saveMessage",new JSONObject().put("message",new JSONObject().put("id","qa-composer-"+System.nanoTime()).put("sessionId",temporarySession).put("role",i%2==0?"user":"assistant").put("text","合成对话第 "+i+" 条。\n\n回到底部按钮应在输入框正上方，不能遮挡输入或发送。\n\n这里只验证界面，没有请求模型。").put("createdAt",1700000000000L+i)));
  h.js("window.__composerReload=true;location.reload()");until("window.__composerReload===undefined&&!!document.querySelector('.today-workspace')");h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");click("[data-page=chat]");until("document.querySelectorAll('.chat-message').length===36");Thread.sleep(250);showJump();layout("portrait");h.screenshot("chat-composer-centered.png");
  h.js("document.querySelector('#chat-input').value=Array(8).fill('保留未发送草稿，测试多行输入。').join('\\n');document.querySelector('#chat-input').dispatchEvent(new Event('input'))");Thread.sleep(150);layout("multiline input");
  h.js("nativeEvent('attachment',{kind:'text',name:'合成附件.txt',text:'只用于布局验证'})");until("!!document.querySelector('.attachment')");showJump();layout("attachment and multiline");
  int height=Integer.parseInt(h.js("innerHeight"));h.tap("#chat-input",false);until("innerHeight<"+(height-100));until("document.body.classList.contains('chat-keyboard')");showJump();layout("keyboard open");
  check(h.js("(()=>{const t=getComputedStyle(document.querySelector('#chat-input'));return t.outlineStyle==='none'&&t.boxShadow==='none'&&getComputedStyle(document.querySelector('.composer')).borderTopColor===getComputedStyle(document.querySelector('#chat-bottom')).color})()").equals("true"),"Focused input has no blue outline; the composer keeps a green border cue");h.screenshot("chat-composer-keyboard.png");
  sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);until("innerHeight>"+(height-50));Thread.sleep(250);showJump();layout("keyboard closed");
  runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(300);showJump();layout("200 percent text");h.screenshot("chat-composer-large.png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));Thread.sleep(250);
  String draft=h.js("document.querySelector('#chat-input').value");h.tap("#chat-bottom",false);until("document.querySelector('#chat-bottom').hidden");check(h.js("document.querySelector('#chat-input').value").equals(draft),"Jumping to latest preserves the unsent draft");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;out.putString("stream","PASS ChatComposerLayoutTest: "+passed+" checks\n");
 }catch(Throwable error){out.putString("stream","FAIL ChatComposerLayoutTest after "+passed+": "+android.util.Log.getStackTraceString(error));try{if(h!=null)h.screenshot("chat-composer-failure.png");}catch(Exception ignored){}}
 finally{try{if(h!=null){runOnMainSync(()->h.web.getSettings().setTextZoom(100));if(!temporarySession.isEmpty())h.call("deleteChat",new JSONObject().put("sessionId",temporarySession));if(!originalSession.isEmpty())h.call("getChat",new JSONObject().put("sessionId",originalSession));runOnMainSync(()->h.activity.finish());}}catch(Exception ignored){}}finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
