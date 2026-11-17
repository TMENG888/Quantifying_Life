package com.insight.quantlife.tests;
import android.app.Activity;import android.os.*;import org.json.*;
/** Run in real portrait, landscape and tablet emulator configurations. */
public final class StudyTimerLayoutTest extends DocumentEditorTest {
 void fit(String label)throws Exception{check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),label+" has no horizontal overflow");check(h.js("[...document.querySelectorAll('.study-entry,.study-controls button')].every(b=>b.getBoundingClientRect().height>=48)").equals("true"),label+" retains 48px touch targets");}
 @Override public void onStart(){Bundle out=new Bundle();String id=null;try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  until("typeof document.querySelector('[data-page=records]').onclick==='function'");click("[data-page=records]");until("!!document.querySelector('#learning-search')&&!document.querySelector('[data-study-open]').disabled");fit("Learning "+phase);
  if(!((JSONObject)h.call("studyTimer",new JSONObject())).isNull("active"))throw new Exception("Existing stopwatch; refusing to replace");
  h.tap("[data-study-open]",false);until("!!document.querySelector('#study-finish')");id=((JSONObject)h.call("studyTimer",new JSONObject())).getJSONObject("active").getString("id");fit("Timer "+phase);
  runOnMainSync(()->h.web.getSettings().setTextZoom(160));Thread.sleep(400);fit("Timer 160% "+phase);check(h.js("getComputedStyle(document.querySelector('.study-panel')).backgroundColor==='rgb(255, 255, 255)'").equals("true"),"Fixed light theme stays readable under system night mode");
  h.screenshot("study-layout-"+phase+".png");click("#study-back");until("!!document.querySelector('#learning-search')");fit("Learning 160% "+phase);h.screenshot("study-entry-"+phase+".png");
  runOnMainSync(()->h.web.getSettings().setTextZoom(100));h.call("studyTimer",new JSONObject().put("command","discard").put("id",id));id=null;
  out.putString("stream","PASS StudyTimerLayoutTest "+phase+": "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable ex){try{if(h!=null)h.screenshot("study-layout-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL StudyTimerLayoutTest "+phase+": "+ex+"\n"+android.util.Log.getStackTraceString(ex));finish(Activity.RESULT_CANCELED,out);
 }finally{if(h!=null)try{runOnMainSync(()->h.web.getSettings().setTextZoom(100));if(id!=null)h.call("studyTimer",new JSONObject().put("command","discard").put("id",id));}catch(Exception ignored){}}}
}
