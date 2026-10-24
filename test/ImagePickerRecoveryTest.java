package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.view.*;import android.content.*;import org.json.*;
/** Blank-first-image, cancellation and real activity reconstruction. Synthetic only. */
public final class ImagePickerRecoveryTest extends ImagePickerFlowTest {
 void backPicker(){long now=SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0),true);getUiAutomation().injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true);}
 @Override public void onStart(){Bundle out=new Bundle();try{
  seed();h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");open("learning");
  launchPicker();Thread.sleep(900);
  JSONArray drafts=(JSONArray)h.call("getDocumentDraft",new JSONObject());String id="";for(int i=0;i<drafts.length();i++)if(drafts.getJSONObject(i).optBoolean("pickerOnly"))id=drafts.getJSONObject(i).getJSONObject("record").getString("id");
  check(!id.isEmpty(),"Blank first-image picker keeps hidden recovery context");
  backPicker();Thread.sleep(800);drafts=(JSONArray)h.call("getDocumentDraft",new JSONObject());boolean found=false;for(int i=0;i<drafts.length();i++)if(drafts.getJSONObject(i).getJSONObject("record").getString("id").equals(id))found=true;check(!found,"Cancelling blank picker removes recovery context; no empty draft remains");
  launchPicker();waitPicker();drafts=(JSONArray)h.call("getDocumentDraft",new JSONObject());for(int i=0;i<drafts.length();i++)if(drafts.getJSONObject(i).optBoolean("pickerOnly"))id=drafts.getJSONObject(i).getJSONObject("record").getString("id");
  ActivityMonitor monitor=addMonitor("com.insight.quantlife.MainActivity",null,false);runOnMainSync(()->h.activity.recreate());Thread.sleep(1000);check(choose(filename),"First image selected after real activity recreation");Activity next=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);check(next!=null,"Blank editor activity reconstructed");h.activity=next;java.lang.reflect.Field wf=next.getClass().getDeclaredField("web");wf.setAccessible(true);h.web=(android.webkit.WebView)wf.get(next);
  until("document.querySelectorAll('#doc-body img').length===1&&document.querySelector('#doc-body img').naturalWidth>0");Thread.sleep(500);check(h.call("pendingRecordImage",new JSONObject())==JSONObject.NULL,"First image shown and acknowledged after recovery");
  h.js("document.querySelector('#doc-title').value='先插图再写笔记';const b=document.querySelector('#doc-body');b.append(document.createTextNode('正文保留图片。'));b.dispatchEvent(new Event('input'));AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");saveUI();JSONObject saved=record(id);check(saved.getJSONArray("images").length()==1&&saved.getString("note").contains("正文保留图片"),"First-image workflow saves image and subsequently typed text");
  out.putString("stream","PASS ImagePickerRecoveryTest: "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable error){out.putString("stream","FAIL ImagePickerRecoveryTest after "+passed+": "+error+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
