package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.view.*;import org.json.*;
/** Real touches and production SQLite; synthetic replies only, no model API usage. */
public class ChatMessageActionsTest extends DocumentEditorTest {
 final String original="请帮我回顾昨天的学习，分成三个要点。",edited="请帮我回顾昨天的学习，只用一句话回答。";
 void longPress(String selector,boolean drag)throws Exception {
  JSONArray xy=new JSONArray(h.js("(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [r.left+r.width/2,r.top+r.height/2,innerWidth]})()"));
  float scale=h.web.getWidth()/(float)xy.getDouble(2),x=(float)xy.getDouble(0)*scale,y=(float)xy.getDouble(1)*scale;long t=SystemClock.uptimeMillis();
  runOnMainSync(()->{MotionEvent ev=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);h.web.dispatchTouchEvent(ev);ev.recycle();});
  if(drag){Thread.sleep(100);runOnMainSync(()->{MotionEvent ev=MotionEvent.obtain(t,t+100,MotionEvent.ACTION_MOVE,x,y-60*scale,0);h.web.dispatchTouchEvent(ev);ev.recycle();});}
  Thread.sleep(650);runOnMainSync(()->{MotionEvent ev=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y-(drag?60*scale:0),0);h.web.dispatchTouchEvent(ev);ev.recycle();});Thread.sleep(180);
 }
 void draft(String value)throws Exception{h.js("document.querySelector('#chat-input').value="+JSONObject.quote(value)+";document.querySelector('#chat-input').dispatchEvent(new Event('input'))");}
 android.view.accessibility.AccessibilityNodeInfo copyNode(android.view.accessibility.AccessibilityNodeInfo node){
  if(node==null)return null;String label=String.valueOf(node.getText());if(label.equals("Copy")||label.equals("COPY")||label.equals("复制"))return node;
  for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo match=copyNode(node.getChild(i));if(match!=null)return match;}return null;
 }
 void nativeCopy()throws Exception{
  android.accessibilityservice.AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(info);
  for(int i=0;i<30;i++){for(android.view.accessibility.AccessibilityWindowInfo window:getUiAutomation().getWindows()){android.view.accessibility.AccessibilityNodeInfo node=copyNode(window.getRoot());if(node!=null){android.graphics.Rect r=new android.graphics.Rect();node.getBoundsInScreen(r);long t=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent ev=MotionEvent.obtain(t,SystemClock.uptimeMillis(),action,r.centerX(),r.centerY(),0);ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(ev,true);ev.recycle();}return;}}Thread.sleep(100);}throw new Exception("No native Copy action");
 }
 void sheet()throws Exception{longPress(".chat-message.user .message-content",false);click("[data-message-action=select]");until("!!document.querySelector('#chat-select-text')");}
 @Override public void onStart(){Bundle out=new Bundle();int code=Activity.RESULT_CANCELED;String previous=getTargetContext().getSharedPreferences("secure",0).getString("settings",null);try{
  if(!Build.MODEL.contains("SDK")&&!Build.FINGERPRINT.contains("generic"))throw new Exception("Disposable emulator only");
  getTargetContext().getSharedPreferences("secure",0).edit().remove("settings").commit();h=new MainHost(this);h.open();model();
  h.call("saveMessage",new JSONObject().put("message",new JSONObject().put("id","edit-user-"+System.nanoTime()).put("role","user").put("text",original).put("createdAt",1700000000000L)));
  h.call("saveMessage",new JSONObject().put("message",new JSONObject().put("id","edit-assistant-"+System.nanoTime()).put("role","assistant").put("text","合成回答，仅测试交互。").put("createdAt",1700000000001L)));
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();
  h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{};window.__native=LifeNative;window.__asks=0;LifeNative={call:(a,p,id)=>{if(a!=='ask'){__native.call(a,p,id);return;}const q=JSON.parse(p);if(q.automaticWiki){setTimeout(()=>nativeReply({id,error:'Synthetic background paused'}),10);return;}__asks++;const v=q.tool?.name==='plan_record_search'?{intent:'general',topic:'',keywords:[],dateStart:'',dateEnd:'',recordTypes:[]}:{answer:'合成回复：已收到修改后的提示词。',records:[],citations:[]};setTimeout(()=>nativeReply({id,data:{text:JSON.stringify(v),model:'synthetic'}}),10);}};document.querySelector('[data-page=chat]').click()");
  until("document.querySelectorAll('.chat-message').length===2");String selector=".chat-message.user .message-content";
  if(phase.equals("layout")){
   h.js("document.querySelector('.chat-message.user .message-content').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}))");click("[data-message-action=select]");
   h.js("document.querySelector('#chat-select-text').textContent='<b>原文符号</b>\\n'+('长文本布局：学习记录与工作经验。\\n'.repeat(100))");
   check(h.js("innerWidth>innerHeight").equals("true"),"Real Android landscape viewport active");
   check(h.js("(()=>{const d=document.querySelector('#chat-message-menu').getBoundingClientRect(),c=document.querySelector('.chat-menu-close').getBoundingClientRect(),s=document.querySelector('.chat-select-scroll');s.scrollTop=s.scrollHeight;return d.top>=0&&Math.abs(d.bottom-innerHeight)<2&&d.left>=0&&d.right<=innerWidth+1&&c.height>=48&&s.scrollTop>0})()").equals("true"),"Landscape sheet, close target and long text remain within viewport");
   runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(400);check(h.js("(()=>{const s=document.querySelector('.chat-select-scroll');return s.clientHeight>0&&s.scrollWidth<=s.clientWidth+1&&getComputedStyle(document.querySelector('#chat-select-text')).color==='rgb(30, 48, 41)'})()").equals("true"),"Landscape night-system large text keeps original light palette and wraps");h.screenshot("chat-select-landscape-large-text.png");
   runOnMainSync(()->h.web.getSettings().setTextZoom(100));click(".chat-menu-close");check(h.js("!document.querySelector('#chat-message-menu').open&&__asks===0").equals("true"),"Landscape selection closes without model requests");out.putString("stream","PASS "+passed+" landscape checks\n");finish(Activity.RESULT_OK,out);return;
  }
  draft("尚未发送的草稿");h.tap(selector,false);until("!!document.querySelector('#chat-edit-banner')");
  check(h.js("document.querySelector('#chat-input').value").equals(JSONObject.quote(original)),"Single real tap fills composer with original text");
  check(h.js("__asks===0").equals("true"),"Tap never sends a model request");
  check(h.js("(()=>{const s=getComputedStyle(document.querySelector('.chat-message.user .message-content'));return s.outlineStyle==='none'&&s.boxShadow==='none'})()").equals("true"),"Touch editing has no message outline or shadow frame");
  h.screenshot("chat-edit-input.png");click("#chat-edit-banner button");
  check(h.js("document.querySelector('#chat-input').value==='尚未发送的草稿'").equals("true"),"Cancel restores previous unsent draft");
  h.js("document.activeElement?.blur()");runOnMainSync(()->((android.view.inputmethod.InputMethodManager)getTargetContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(h.web.getWindowToken(),0));Thread.sleep(700);
  h.js("document.querySelector('.chat-message.user').scrollIntoView({block:'center'})");longPress(selector,false);until("document.querySelector('#chat-message-menu').open");
  check(h.js("document.querySelectorAll('[data-message-action]').length===3&&!document.querySelector('#chat-edit-banner')").equals("true"),"Real long press opens copy/select/edit only without accidental tap edit");h.screenshot("chat-message-menu.png");
  click("[data-message-action=copy]");Thread.sleep(250);final String[] clip={null};runOnMainSync(()->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getTargetContext().getSystemService(Context.CLIPBOARD_SERVICE);clip[0]=cm.getPrimaryClip().getItemAt(0).getText().toString();});check(original.equals(clip[0]),"Copy reaches actual Android clipboard");
  sheet();
  check(h.js("document.querySelector('#chat-select-text').textContent==="+JSONObject.quote(original)+"&&!document.querySelector('#chat-message-menu textarea')&&!document.querySelector('.chat-copy-selection')&&!getSelection().toString()").equals("true"),"Selection sheet shows exact plain text, no custom input/button or automatic selection");
  check(h.js("(()=>{const d=document.querySelector('#chat-message-menu'),r=d.getBoundingClientRect(),x=document.querySelector('.chat-menu-close').getBoundingClientRect();return Math.abs(r.bottom-innerHeight)<2&&r.width>=innerWidth-1&&r.top>innerHeight*.25&&x.width>=48&&x.height>=48})()").equals("true"),"Bottom sheet is edge-aligned with an accessible close target");
  h.screenshot("chat-select-sheet.png");longPress("#chat-select-text",false);until("getSelection().toString().length>0");String chosen=new JSONArray("["+h.js("getSelection().toString()")+"]").getString(0);
  check(chosen.length()<original.length()&&original.contains(chosen),"Real native long press selects part of the text");h.screenshot("chat-native-selection.png");nativeCopy();Thread.sleep(200);
  runOnMainSync(()->clip[0]=((android.content.ClipboardManager)getTargetContext().getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip().getItemAt(0).getText().toString());check(chosen.equals(clip[0]),"System Copy copies only native selected text to Android clipboard");
  click(".chat-menu-close");until("!document.querySelector('#chat-message-menu').open");check(h.js("!getSelection().toString()&&!document.querySelector('#chat-edit-banner')").equals("true"),"Close clears selection without editing or sending");
  // Escape/back must close the sheet; keyboard users still get a focus indicator.
  sheet();sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);until("!document.querySelector('#chat-message-menu').open");check(h.js("!!document.querySelector('.chat-shell')").equals("true"),"Android Back closes selection sheet and stays in chat");
  sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);h.js("document.querySelector('.chat-message.user .message-content').focus()");check(h.js("getComputedStyle(document.querySelector('.chat-message.user .message-content')).outlineStyle==='solid'").equals("true"),"Keyboard navigation retains visible focus");
  h.js("document.documentElement.classList.remove('chat-keyboard-navigation');document.activeElement.blur()");
  longPress(selector,true);check(h.js("!document.querySelector('#chat-message-menu').open&&!document.querySelector('#chat-edit-banner')").equals("true"),"Scrolling a message does not trigger edit or long-press menu");
  h.tap(selector,false);until("!!document.querySelector('#chat-edit-banner')");draft(edited);Thread.sleep(700);h.tap("[data-action=send]",false);until("document.querySelectorAll('.chat-message').length===4&&!document.querySelector('.loading')");
  check(h.js("document.querySelectorAll('.chat-message.user')[0].querySelector('.message-content').textContent==="+JSONObject.quote(original)+"&&document.querySelectorAll('.chat-message.user')[1].querySelector('.message-content').textContent==="+JSONObject.quote(edited)).equals("true"),"Edited send appends a new user message, never overwrites original");
  check(h.js("!document.querySelector('#chat-edit-banner')&&document.querySelector('#chat-input').value===''").equals("true"),"Send clears editing banner and composer");
  JSONObject chat=(JSONObject)h.call("getChat",new JSONObject());check(chat.getJSONArray("messages").length()==4,"Both original and edited conversation persist in SQLite");until("(()=>{const b=document.querySelector('#chat-scroll');return Math.abs(b.scrollHeight-b.clientHeight-b.scrollTop)<3})()");check(h.js("(()=>{const b=document.querySelector('#chat-scroll');return Math.abs(b.scrollHeight-b.clientHeight-b.scrollTop)<3})()").equals("true"),"Edited reply stays at latest conversation, not history top");
  h.screenshot("chat-edited-sent.png");
  h.js("document.activeElement?.blur()");runOnMainSync(()->((android.view.inputmethod.InputMethodManager)getTargetContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(h.web.getWindowToken(),0));Thread.sleep(700);
  h.js("document.querySelector('.chat-message.user').scrollIntoView({block:'center'})");sheet();
  // Isolated long-content fixture exercises scrolling/layout without changing saved history.
  h.js("document.querySelector('#chat-select-text').textContent='<b>这里是原文，不是 HTML</b>\\n'+('学习记录与工作经验；保留换行，长按选择。\\n'.repeat(100))");
  for(int width:new int[]{320,375,768}){
   h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width="+width+",initial-scale=1')");Thread.sleep(350);
   check(h.js("(()=>{const d=document.querySelector('#chat-message-menu'),r=d.getBoundingClientRect(),s=document.querySelector('.chat-select-scroll');s.scrollTop=s.scrollHeight;return r.left>=0&&r.right<=innerWidth+1&&Math.abs(r.bottom-innerHeight)<2&&s.scrollTop>0&&!document.querySelector('#chat-select-text b')&&s.scrollWidth<=s.clientWidth+1})()").equals("true"),"Long plain text scrolls and sheet fits viewport width "+width);
  }
  h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width=device-width,initial-scale=1,viewport-fit=cover')");runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(400);
  check(h.js("(()=>{const d=document.querySelector('#chat-message-menu').getBoundingClientRect(),c=document.querySelector('.chat-menu-close').getBoundingClientRect();return d.right<=innerWidth+1&&c.right<=innerWidth&&c.height>=48&&document.querySelector('.chat-select-scroll').clientHeight>0})()").equals("true"),"200 percent text remains readable with reachable close button");h.screenshot("chat-select-large-text.png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
  out.putString("stream","PASS "+passed+" message interaction checks\n");code=Activity.RESULT_OK;
 }catch(Throwable error){try{h.screenshot("chat-message-actions-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL after "+passed+": "+error+"\n");error.printStackTrace();}
 finally{android.content.SharedPreferences.Editor prefs=getTargetContext().getSharedPreferences("secure",0).edit();if(previous==null)prefs.remove("settings");else prefs.putString("settings",previous);prefs.commit();}finish(code,out);}
}
