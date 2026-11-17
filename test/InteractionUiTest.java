package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.view.*;import android.view.inputmethod.*;import org.json.*;import java.util.*;

/** Real production WebView and Android IME. Disposable emulator; no model calls. */
public final class InteractionUiTest extends Instrumentation {
 MainHost h;int passed;final List<String> ids=new ArrayList<>();
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception(label);passed++;android.util.Log.i("InteractionQA","PASS "+label);}
 void until(String expr)throws Exception{for(int i=0;i<100;i++){if(h.js(expr).equals("true"))return;Thread.sleep(80);}throw new Exception("Timeout: "+expr);}
 void click(String selector)throws Exception{h.js("document.querySelector("+JSONObject.quote(selector)+").click()");Thread.sleep(200);}
 void condition(String expr,String label)throws Exception{check(h.js(expr).equals("true"),label);}
 void value(String selector,String text)throws Exception{h.js("(()=>{const n=document.querySelector("+JSONObject.quote(selector)+");n.value="+JSONObject.quote(text)+";n.dispatchEvent(new Event('input',{bubbles:true}));})()");}
 void checks()throws Exception{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  for(int i=0;i<44;i++){
   String id="qa-touch-record-"+i;JSONObject r=new JSONObject().put("id",id).put("type","learning").put("date","2026-11-03").put("title","交互回归笔记 "+i).put("note","保留原文与连续阅读位置。第 "+i+" 篇记录用于测试滚动、缓存与搜索输入连接。").put("durationMinutes",5).put("platform","本机测试").put("contentType","article").put("tags",new JSONArray());
   h.call("saveRecord",new JSONObject().put("record",r).put("allowDuplicate",true));ids.add(id);
  }
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  condition("getComputedStyle(document.querySelector('.bottom-nav button')).webkitTapHighlightColor==='rgba(0, 0, 0, 0)'","Navigation uses transparent browser tap highlight: "+h.js("getComputedStyle(document.querySelector('.bottom-nav button')).webkitTapHighlightColor"));
  click("[data-page=records]");until("!!document.querySelector('#learning-search')");
  h.js("window.__search=document.querySelector('#learning-search');window.__row=document.querySelector('[data-record]');window.__navigations=ViewRuntime.stats().created");
  h.js("document.dispatchEvent(new Event('visibilitychange'))");Thread.sleep(350);
  condition("document.querySelector('#learning-search')===__search&&document.querySelector('[data-record]')===__row","Unchanged background refresh retains actual input and record nodes");
  condition("ViewRuntime.stats().created===__navigations","Unchanged refresh creates no replacement page");
  h.tap("#learning-search",false);Thread.sleep(350);value("#learning-search","交互回归笔记");
  condition("document.querySelector('#learning-search')===__search&&document.activeElement===__search","Search filtering preserves the focused Android input node");
  h.js("__search.setSelectionRange(1,3);document.dispatchEvent(new Event('visibilitychange'))");Thread.sleep(200);
  condition("__search.selectionStart===1&&__search.selectionEnd===3&&__search.value==='交互回归笔记'","Background patch preserves search text and caret range");
  final InputConnection[] connection={null};runOnMainSync(()->{connection[0]=h.web.onCreateInputConnection(new EditorInfo());connection[0].setComposingText("ceshi",1);});until("__search.value.includes('ceshi')");
  h.js("document.dispatchEvent(new Event('visibilitychange'))");Thread.sleep(180);runOnMainSync(()->connection[0].commitText("测试",1));
  until("__search.value.includes('测试')&&!__search.value.includes('ceshi')");check(true,"Same Android IME connection commits Chinese after filtering and refresh");
  value("#learning-search","");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(300);h.js("document.activeElement?.blur();window.scrollTo(0,650)");Thread.sleep(150);
  h.js("window.__savedY=scrollY;window.__cachedSearch=document.querySelector('#learning-search');window.__cachedRow=document.querySelector('[data-record]')");
  click("[data-page=today]");click("[data-page=records]");
  condition("document.querySelector('#learning-search')===__cachedSearch&&document.querySelector('[data-record]')===__cachedRow","Returning to a main tab reuses cached DOM instead of rebuilding its list");
  condition("Math.abs(scrollY-__savedY)<3&&scrollY>100","Returning to records restores its previous scroll position");
  for(int i=0;i<4;i++){click("[data-page=today]");click("[data-page=records]");}
  condition("ViewRuntime.stats().cached<=5","View cache remains bounded after repeated navigation");
  h.js("window.scrollTo(0,650);window.__crossY=scrollY");click("[data-page=chat]");click("[data-page=records]");
  condition("Math.abs(scrollY-__crossY)<3&&scrollY>100","Chat layout changes do not erase the previous record scroll position");
  h.js("window.scrollTo(0,0)");Thread.sleep(100);h.tap("#learning-search",false);Thread.sleep(150);
  condition("getComputedStyle(__search).outlineStyle==='none'&&document.documentElement.dataset.inputMode==='pointer'","Touch-focused inputs have no colored outline");
  sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);Thread.sleep(150);h.js("document.querySelector('[data-page=today]').focus()");
  condition("document.documentElement.dataset.inputMode==='keyboard'&&getComputedStyle(document.querySelector('[data-page=today]')).outlineStyle!=='none'","Keyboard navigation retains an accessible focus indicator");
  h.js("document.querySelector('[data-page=records]').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:7,isPrimary:true,clientX:100,clientY:100}))");
  condition("document.querySelector('[data-page=records]').hasAttribute('data-ui-pressed')","Pointer down gives immediate local pressed feedback");
  h.js("document.dispatchEvent(new PointerEvent('pointermove',{bubbles:true,pointerId:7,clientX:100,clientY:160}))");
  condition("!document.querySelector('[data-ui-pressed]')","Scrolling cancels the pressed state without triggering an action");
  h.js("document.querySelector('[data-page=records]').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:8,isPrimary:true,clientX:100,clientY:100}));document.dispatchEvent(new PointerEvent('pointercancel',{bubbles:true,pointerId:8}))");
  condition("!document.querySelector('[data-ui-pressed]')","Cancelled touches leave no stuck feedback");
  value("#learning-search","交互回归笔记");click("[data-page=chat]");until("!!document.querySelector('#chat-input')");value("#chat-input","切换栏目后保留未发送草稿");
  click("[data-page=records]");condition("document.querySelector('#learning-search').value==='交互回归笔记'","Tab switching retains the record search filter");
  h.js("window.__match=window.matchMedia;window.matchMedia=q=>q==='(prefers-reduced-motion: reduce)'?{matches:true}:__match(q)");
  condition("(()=>{document.querySelector('[data-page=today]').click();return document.querySelector('#main').getAnimations().length===0})()","Reduced-motion preference disables page animations immediately");h.js("window.matchMedia=__match");click("[data-page=records]");
  click("[data-page=chat]");condition("document.querySelector('#chat-input').value==='切换栏目后保留未发送草稿'","Chat draft survives navigation with the new renderer");
  click("[data-page=records]");value("#learning-search","");h.js("document.activeElement?.blur();window.scrollTo(0,0);window.__visible=document.querySelector('[data-record]');window.__opened=0;window.__originalOpen=RecordEditor.open;RecordEditor.open=(...args)=>{__opened++;return __originalOpen(...args)}");
  for(int i=0;i<4;i++){h.js("document.dispatchEvent(new Event('visibilitychange'))");Thread.sleep(60);}
  h.js("__visible.click()");until("document.querySelector('#editor').open");condition("__opened===1","Repeated binds still open a record exactly once");h.js("RecordEditor.open=__originalOpen;RecordEditor.close()");until("!document.querySelector('#editor').open");
  runOnMainSync(()->h.web.getSettings().setTextZoom(200));click("[data-page=today]");click("[data-page=records]");condition("document.documentElement.scrollWidth<=innerWidth+1","Retained views fit at 200 percent text size");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
  h.js("window.scrollTo(0,0)");h.screenshot("interaction-records.png");
 }
 @Override public void onStart(){Bundle result=new Bundle();boolean ok=false;try{if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;result.putString("stream","PASS InteractionUiTest: "+passed+" checks\n");}catch(Throwable err){result.putString("stream","FAIL InteractionUiTest after "+passed+": "+android.util.Log.getStackTraceString(err));try{if(h!=null)h.screenshot("interaction-failure.png");}catch(Exception ignored){}}
  finally{if(h!=null){for(String id:ids)try{h.call("deleteRecord",new JSONObject().put("id",id));}catch(Exception ignored){}try{runOnMainSync(()->{h.web.getSettings().setTextZoom(100);h.activity.finish();});}catch(Exception ignored){}}}finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
