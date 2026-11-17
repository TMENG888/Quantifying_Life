package com.insight.quantlife.tests;

import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import com.insight.quantlife.MeditationStore;
import com.insight.quantlife.StudyTimerStore;
import org.json.JSONObject;

/** Disposable emulator: synthetic clock edge cases and real Android/WebView taps. No model requests. */
public final class StudyTimerTest extends DocumentEditorTest {
 static class Clock implements MeditationStore.Clock {long wall=1799971200000L,elapsed=10000;int boot=1;public long wall(){return wall;}public long elapsed(){return elapsed;}public int boot(){return boot;}void add(long ms){wall+=ms;elapsed+=ms;}}
 JSONObject cmd(String command,String id)throws Exception{return new JSONObject().put("command",command).put("id",id);}
 void nativeChecks()throws Exception{
  try(SQLiteDatabase db=SQLiteDatabase.create(null)){
   Clock c=new Clock();final JSONObject[] saved={null};String id="study-native-one";
   StudyTimerStore.execute(db,cmd("start",id),c);c.add(65000);c.wall-=3600000;
   JSONObject s=StudyTimerStore.execute(db,cmd("status",""),c).getJSONObject("active");check(s.getLong("durationMs")==65000,"Wall-clock edits do not corrupt elapsed duration");
   StudyTimerStore.execute(db,cmd("pause",id),c);c.add(600000);check(StudyTimerStore.execute(db,cmd("status",""),c).getJSONObject("active").getLong("durationMs")==65000,"Paused rest is excluded");
   StudyTimerStore.execute(db,cmd("resume",id),c);c.add(15000);StudyTimerStore.execute(db,cmd("status",""),c);
   c.boot++;c.elapsed=100;JSONObject paused=StudyTimerStore.execute(db,cmd("status",""),c).getJSONObject("active");check(paused.getString("state").equals("paused")&&paused.getLong("durationMs")==80000&&paused.getBoolean("recoveredAfterReboot"),"Reboot preserves checkpoint and pauses instead of counting powered-off time");
   saved[0]=StudyTimerStore.execute(db,cmd("finish",id).put("title","阅读：索引与知识整理"),c).getJSONObject("draft");check(saved[0].getLong("createdAt")==1799971200000L&&saved[0].getJSONObject("studyTiming").getLong("durationMs")==80000,"Finish records exact original timestamps and effective milliseconds");check(saved[0].getString("note").isEmpty(),"Timing is metadata, never invented note text");
   StudyTimerStore.execute(db,cmd("finish",id),c);StudyTimerStore.execute(db,cmd("start",id),c);check(StudyTimerStore.execute(db,cmd("status",""),c).isNull("active"),"Lost finish/start replies are idempotent and cannot duplicate or overwrite records");
   String second="study-native-two";StudyTimerStore.execute(db,cmd("start",second),c);boolean conflict=false;try{StudyTimerStore.execute(db,cmd("start","study-other-one"),c);}catch(Exception expected){conflict=true;}check(conflict,"Only one learning stopwatch can run at once");
   check(saved[0].getString("title").isEmpty()&&saved[0].getString("studyInput").contains("索引"),"Finish returns input and timing as draft without writing a formal record");
   StudyTimerStore.execute(db,cmd("discard",second),c);check(StudyTimerStore.execute(db,cmd("status",""),c).isNull("active"),"Discard creates no empty learning record");
  }
 }
 void fits(String label)throws Exception{check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),label+" fits viewport");check(h.js("[...document.querySelectorAll('.study-controls button,.study-entry')].every(x=>x.getBoundingClientRect().height>=48)").equals("true"),label+" touch targets remain at least 48px");}
 @Override public void onStart(){Bundle out=new Bundle();String recordId=null;try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");nativeChecks();h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  JSONObject prior=(JSONObject)h.call("studyTimer",new JSONObject());if(!prior.isNull("active"))throw new Exception("Existing timer present; refusing to interfere");
  until("typeof document.querySelector('[data-page=records]').onclick==='function'");click("[data-page=records]");until("!!document.querySelector('#learning-search')&&!!document.querySelector('[data-study-open]')&&!document.querySelector('[data-study-open]').disabled");h.screenshot("study-learning.png");fits("Learning entry");
  h.tap("[data-study-open]",false);until("!!document.querySelector('#study-finish')");until("document.querySelector('#study-clock').textContent!=='00:00:00'");check(true,"Single real touch starts stopwatch");
  h.js("document.querySelector('#study-title').value='实测：阅读知识索引';document.querySelector('#study-title').dispatchEvent(new Event('input'))");h.screenshot("study-running.png");fits("Running timer");
  click("#study-toggle");until("document.querySelector('#study-toggle').textContent==='继续'");String stopped=h.js("document.querySelector('#study-clock').textContent");Thread.sleep(1200);check(stopped.equals(h.js("document.querySelector('#study-clock').textContent")),"Real pause freezes display");click("#study-toggle");until("document.querySelector('#study-toggle').textContent==='暂停'");
  JSONObject before=((JSONObject)h.call("studyTimer",new JSONObject())).getJSONObject("active");long elapsed=before.getLong("durationMs");runOnMainSync(()->h.activity.moveTaskToBack(true));Thread.sleep(1600);Intent i=new Intent();i.setClassName(getTargetContext(),"com.insight.quantlife.MainActivity");i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);getTargetContext().startActivity(i);Thread.sleep(500);
  check(((JSONObject)h.call("studyTimer",new JSONObject())).getJSONObject("active").getLong("durationMs")>=elapsed+1600,"Native stopwatch includes background time");
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");until("document.querySelector('#study-entry-clock').textContent.includes(':')");click("[data-study-open]");until("!!document.querySelector('#study-finish')");check(((JSONObject)h.call("studyTimer",new JSONObject())).getJSONObject("active").getString("id").equals(before.getString("id")),"Activity/WebView recreation restores same stopwatch");
  int countBefore=records().length();h.tap("#study-finish",false);until("!!document.querySelector('#doc-title')");recordId="study-"+before.getString("id");JSONObject r=null;org.json.JSONArray drafts=(org.json.JSONArray)h.call("getDocumentDraft",new JSONObject());for(int d=0;d<drafts.length();d++)if(drafts.getJSONObject(d).getJSONObject("record").getString("id").equals(recordId))r=drafts.getJSONObject(d).getJSONObject("record");check(r!=null&&r.getDouble("durationMinutes")>0&&records().length()==countBefore,"Finish prepares a draft; no record or Wiki input before user saves");check(h.js("document.querySelector('#doc-title').value").contains("实测：阅读知识索引"),"Finish opens matching learning editor");h.screenshot("study-editor.png");
  input("阅读知识索引的收获 "+System.nanoTime(),"原文索引帮助找到来源，知识整理帮助连接概念。");click("#doc-save");until("document.querySelector('#doc-mode').textContent==='编辑'");r=record(recordId);check(r.getString("note").contains("原文索引")&&r.has("studyTiming"),"Saving note preserves independent timing metadata");check(h.js("!document.querySelector('#doc-flow .study-record-time')").equals("true"),"Original reading UI is unchanged");click("#doc-mode");click("#doc-info");check(h.js("!document.querySelector('#doc-meta').hidden&&!!document.querySelector('#doc-meta .study-record-time')").equals("true"),"Time interval lives in record information drawer");check(h.js("document.querySelector('[data-meta=durationMinutes]').value.length<12").equals("true"),"Timer minutes display compact decimals while original precision is preserved");h.screenshot("study-information.png");click("#doc-meta-done");click("#doc-mode");h.screenshot("study-reader.png");
  h.call("studyTimer",cmd("finish",before.getString("id")));check(record(recordId).getString("note").equals(r.getString("note")),"Retrying native finish after editing does not erase written note");close();
  for(int width:new int[]{375,667,1024}){h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width="+width+",initial-scale=1')");Thread.sleep(300);fits("Learning "+width);}
  h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width=375,initial-scale=1')");runOnMainSync(()->h.web.getSettings().setTextZoom(160));Thread.sleep(400);fits("Learning 160% font");h.screenshot("study-large-text.png");click("[data-study-open]");until("!!document.querySelector('#study-finish')");fits("Timer 160% font");h.screenshot("study-timer-large-text.png");click("#study-discard");until("document.querySelector('#confirm-dialog').open");click("#confirm-cancel");check(!((JSONObject)h.call("studyTimer",new JSONObject())).isNull("active"),"Cancel discard preserves active stopwatch");click("#study-discard");click("#confirm-ok");until("!!document.querySelector('#study-start')");check(((JSONObject)h.call("studyTimer",new JSONObject())).isNull("active"),"Confirmed discard ends timer without saving a record");
  out.putString("stream","PASS StudyTimerTest: "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable ex){try{if(h!=null)h.screenshot("study-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL StudyTimerTest after "+passed+": "+ex+"\n"+android.util.Log.getStackTraceString(ex));finish(Activity.RESULT_CANCELED,out);
 }finally{if(h!=null){try{runOnMainSync(()->h.web.getSettings().setTextZoom(100));h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width=device-width,initial-scale=1,viewport-fit=cover')");if(recordId!=null)h.call("deleteRecord",new JSONObject().put("id",recordId));}catch(Exception ignored){}}}}
}
