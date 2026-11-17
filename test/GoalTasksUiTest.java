package com.insight.quantlife.tests;

import android.app.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.webkit.WebView;
import com.insight.quantlife.*;
import org.json.*;
import java.lang.reflect.*;

/** Real local bridge and WebView. Only qa-goal-ui-* fixtures are written or removed. */
public final class GoalTasksUiTest extends DocumentEditorTest {
 final String prefix="qa-goal-ui-",goalId=prefix+"goal",taskId=prefix+"task";
 String originalRecords="",originalGoals="";int originalOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
 String plainRows(JSONArray rows)throws Exception{JSONArray keep=new JSONArray();for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).getString("id").startsWith(prefix))keep.put(rows.getJSONObject(i));return keep.toString();}
 JSONObject goals()throws Exception{return (JSONObject)h.call("goalTasks",new JSONObject().put("command","list"));}
 JSONObject task(String id)throws Exception{JSONArray rows=goals().getJSONArray("items");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return rows.getJSONObject(i);throw new Exception("Missing QA goal/task: "+id);}
 boolean exists(String id)throws Exception{JSONArray rows=goals().getJSONArray("items");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return true;return false;}
 void clean()throws Exception{try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){db.beginTransaction();try{
  db.delete("goal_task_entries","task_id LIKE ? OR id LIKE ?",new String[]{prefix+"%",prefix+"%"});db.delete("goal_tasks","id LIKE ?",new String[]{prefix+"%"});db.delete("records","id LIKE ?",new String[]{prefix+"%"});db.setTransactionSuccessful();
 }finally{db.endTransaction();}}}
 void seedRecords()throws Exception{
  String date=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(new java.util.Date());
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){db.beginTransaction();try{
   for(String type:new String[]{"learning","work_note","idea"}){
    String note="先确认目标，再拆出行动。数量与时间分别记录，不能把投入直接视为已经掌握。";JSONArray units=new JSONArray();for(int i=0;i<4;i++)units.put(new JSONObject().put("text","独立方法 "+(i+1)).put("sources",new JSONArray().put(new JSONObject().put("start",0).put("end",note.length()).put("quote",note))));
    JSONObject r=new JSONObject().put("id",prefix+type).put("type",type).put("date",date).put("occurredAt",date+"T12:00:00").put("title","QA单篇指标：先厘清结果，再制定可以度量的行动与时间计划，持续检查真实完成进度").put("note",note).put("durationMinutes",30).put("platform","QA本地").put("contentType","article").put("tags",new JSONArray().put("QA指标验证")).put("createdAt",100).put("updatedAt",100).put("informationUnits",new JSONObject().put("schemaVersion",InformationUnits.VERSION).put("model","deepseek-flash").put("provider",InformationUnits.PROVIDER).put("unitRule",InformationUnits.RULE).put("sourceVersion",100).put("sourceHash",QwenLabels.hash(new JSONObject().put("note",note))).put("analyzedAt",100).put("complete",true).put("count",4).put("characters",InformationUnits.characters(note)).put("units",units));
    db.execSQL("INSERT INTO records(id,type,date,title,platform,payload) VALUES(?,?,?,?,?,?)",new Object[]{r.getString("id"),type,date,r.getString("title"),r.getString("platform"),r.toString()});
   }db.setTransactionSuccessful();
  }finally{db.endTransaction();}}
 }
 void offline()throws Exception{until("typeof document.querySelector('[data-page=records]').onclick==='function'");h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");}
 void uid(String id)throws Exception{h.js("(()=>{const prior=LifeDomain.uid;LifeDomain.uid=()=>{LifeDomain.uid=prior;return "+JSONObject.quote(id)+";};})()");}
 void value(String id,String value)throws Exception{h.js("document.getElementById("+JSONObject.quote(id)+").value="+JSONObject.quote(value));}
 void submit(String id)throws Exception{h.js("document.getElementById("+JSONObject.quote(id)+").requestSubmit()");}
 void detail(String id)throws Exception{until("document.querySelector('[data-goal-detail]')?.dataset.goalDetail==="+JSONObject.quote(id)+"&&!document.querySelector('#editor').open");}
 void goalFits(String label)throws Exception{
  check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),label+": no page horizontal overflow");
  check(h.js("[...document.querySelectorAll('#goal-root button,.goal-task-dialog input,.goal-task-dialog select,.goal-task-dialog textarea,.goal-task-dialog button')].every(b=>{const r=b.getBoundingClientRect(),s=getComputedStyle(b);return s.visibility==='hidden'||s.display==='none'||!r.width||(r.left>=-1&&r.right<=innerWidth+1&&r.height>=47)})").equals("true"),label+": visible controls fit and stay touchable");
  check(h.js("[...document.querySelectorAll('.goal-item-title,.goal-result h1,.goal-measures dd,.goal-form')].every(x=>x.scrollWidth<=x.clientWidth+1)").equals("true"),label+": text and quantities remain in their containers");
 }
 void recordMetrics(String module,String type,String search)throws Exception{
  click("[data-record-tab="+module+"]");if(module.equals("work"))h.js("WorkCalendar.selectDay(LifeDomain.localDate())");
  h.js("document.querySelector("+JSONObject.quote(search)+").value='QA单篇指标';document.querySelector("+JSONObject.quote(search)+").dispatchEvent(new Event('input'))");
  String metric="[data-record=\""+prefix+type+"\"] [data-record-metrics]";
  check(h.js("document.querySelector("+JSONObject.quote(metric+" [data-record-metric=units] strong")+")?.textContent==='4'&&document.querySelectorAll("+JSONObject.quote(metric+" [data-record-metric]")+").length===3").equals("true"),module+": list shows per-record information metrics");
  check(h.js("document.querySelector("+JSONObject.quote(metric+" [data-record-metric=efficiency] strong")+")?.textContent==="+JSONObject.quote(type.equals("learning")?"8":"无独立耗时")).equals("true"),module+": efficiency uses only independent learning time");
  click("[data-record='"+prefix+type+"']");until("!!document.querySelector('#doc-flow')");click("#doc-mode");click("#doc-info");
  check(h.js("!document.querySelector('#doc-meta').hidden&&document.querySelector('#doc-meta [data-record-metric=units] strong').textContent==='4'&&document.querySelector('#doc-meta [data-meta=tags]').value==='QA指标验证'").equals("true"),module+": tags and metadata contain the same per-record metrics");
  if(module.equals("learning")){runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(250);check(h.js("document.querySelector('#doc-meta').scrollWidth<=document.querySelector('#doc-meta').clientWidth+1&&[...document.querySelectorAll('#doc-meta .record-metric')].every(x=>x.scrollWidth<=x.clientWidth+1)").equals("true"),"200% text: record metric labels do not overflow the drawer");h.screenshot("goal-record-metrics-large.png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));}
  click("#doc-meta-close");close();
 }
 void rotate(int orientation)throws Exception{
  ActivityMonitor monitor=addMonitor("com.insight.quantlife.MainActivity",null,false);runOnMainSync(()->h.activity.setRequestedOrientation(orientation));Activity next=waitForMonitorWithTimeout(monitor,5000);removeMonitor(monitor);
  if(next!=null){h.activity=next;Field f=next.getClass().getDeclaredField("web");f.setAccessible(true);h.web=(WebView)f.get(next);h.execute=next.getClass().getDeclaredMethod("execute",String.class,JSONObject.class);h.execute.setAccessible(true);}
  Thread.sleep(350);offline();
 }
 void checks()throws Exception{
  h=new MainHost(this);h.open();offline();originalOrientation=h.activity.getRequestedOrientation();goals();clean();originalRecords=plainRows(records());originalGoals=plainRows(goals().getJSONArray("items"));
  JSONArray existing=goals().getJSONArray("items");for(int i=0;i<existing.length();i++)check(existing.getJSONObject(i).optLong("timerStartedAt")==0,"Precondition: no pre-existing task timer will be interrupted");
  runOnMainSync(()->h.activity.finish());seedRecords();h=new MainHost(this);h.open();offline();
  h.js("document.querySelector('[data-goal-entry]').scrollIntoView({block:'center'})");h.tap("[data-goal-entry]",false);until("!!document.querySelector('#goal-root')&&!!document.querySelector('.goal-overview')");
  check(h.js("document.querySelector('.bottom-nav').children.length===4").equals("true"),"Goals have a home entry without adding a navigation tab");
  click("[data-goal-view=goals]");uid(goalId);click("[data-goal-add=goal]");until("!!document.querySelector('#goal-form')");submit("goal-form");until("!document.querySelector('#goal-form-error').hidden");check(!exists(goalId),"Empty goal cannot be committed");
  value("goal-title","QA在期限前完成二十八个可以量化的成果，拆成明确行动并持续校验实际进度");value("goal-amount","28");value("goal-unit","个");value("goal-area","life");
  h.js("document.querySelector('#goal-deadline').value=GoalDomain.localDate(new Date(Date.now()+30*86400000))+'T23:59'");submit("goal-form");detail(goalId);
  check(task(goalId).getString("kind").equals("goal")&&h.js("!!document.querySelector('.goal-time-details')&&!!document.querySelector('[data-goal-child]')").equals("true"),"Long-term goal shows its deadline and associated actions");
  uid(taskId);click("[data-goal-child]");until("!!document.querySelector('#goal-parent')");check(h.js("document.querySelector('#goal-parent').value==="+JSONObject.quote(goalId)).equals("true"),"Child form automatically links its long-term goal");
  value("goal-title","QA十九点到十九点半完成仰卧起坐二十八个，数量与实际耗时分别记录");value("goal-amount","28");value("goal-unit","个");value("goal-minutes","30");h.js("document.querySelector('.goal-scheduling').open=true;document.querySelector('#goal-start').value=GoalDomain.localDate()+'T19:30';document.querySelector('#goal-end').value=GoalDomain.localDate()+'T19:00'");submit("goal-form");until("!document.querySelector('#goal-form-error').hidden");check(!exists(taskId),"Reversed execution interval is rejected before saving");
  h.js("document.querySelector('#goal-start').value=GoalDomain.localDate()+'T19:00';document.querySelector('#goal-end').value=GoalDomain.localDate()+'T19:30'");submit("goal-form");detail(taskId);
  check(task(taskId).getString("parentId").equals(goalId)&&task(taskId).getDouble("plannedMinutes")==30,"Action persists parent, quantity, scheduled interval and plan minutes");
  click("[data-goal-check='"+taskId+"']");until("!!document.querySelector('[data-goal-command=reopen]')");check(task(taskId).getString("status").equals("completed")&&task(taskId).getDouble("actualAmount")==0,"Checkbox marks incomplete task done without inventing quantity");click("[data-goal-check='"+taskId+"']");until("!!document.querySelector('[data-goal-progress]')");
  uid(prefix+"entry-one");click("[data-goal-progress]");value("goal-progress-amount","12");value("goal-progress-minutes","10");value("goal-progress-note","QA第一轮完成十二个");submit("goal-progress-form");detail(taskId);
  check(task(taskId).getDouble("actualAmount")==12&&task(taskId).getDouble("actualMinutes")==10&&h.js("document.querySelector('.goal-history').innerText.includes('十二个')").equals("true"),"Progress stores independent quantity and time with visible history");
  click("[data-goal-command=start]");until("!!document.querySelector('[data-goal-command=pause]')");check(task(taskId).getLong("timerStartedAt")>0,"Task timer starts through the native bridge");Thread.sleep(1200);
  uid(prefix+"entry-two");click("[data-goal-progress]");check(h.js("document.querySelector('#goal-progress-minutes').disabled").equals("true"),"Running timer prevents duplicate manual time entry");value("goal-progress-amount","16");submit("goal-progress-form");detail(taskId);click("[data-goal-command=pause]");until("!!document.querySelector('[data-goal-command=start]')");
  JSONObject paused=task(taskId);check(paused.getLong("timerStartedAt")==0&&paused.getDouble("actualMinutes")>10&&paused.getDouble("actualMinutes")<11&&paused.getDouble("actualAmount")==28,"Pause settles elapsed time once and preserves quantity");
  click("[data-goal-check='"+taskId+"']");until("!!document.querySelector('[data-goal-command=reopen]')");check(task(taskId).getString("status").equals("completed"),"Task checkbox preserves recorded amount when completing");
  click(".goal-parent-link");detail(goalId);check(task(goalId).getDouble("actualAmount")==28&&task(goalId).getDouble("ownAmount")==0&&h.js("document.querySelector('.goal-children header p').textContent.includes('1 / 1')").equals("true"),"Child completion contributes same-unit quantity while parent direct quantity remains separate");
  goalFits("portrait goal detail");h.screenshot("goal-detail.png");runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(250);goalFits("200% goal detail");h.screenshot("goal-detail-large.png");uid(prefix+"unsaved");click("[data-goal-child]");until("!!document.querySelector('#goal-form')");goalFits("200% child form");h.screenshot("goal-form-large.png");click("#close-editor");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
  click("[data-page=records]");recordMetrics("learning","learning","#learning-search");recordMetrics("work","work_note","#work-note-search");recordMetrics("thoughts","idea","#thought-search");
  rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);check(getTargetContext().getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE,"Landscape viewport is active");
  // Rotation recreates the WebView. Follow the visible list after its native load
  // rather than deep-linking into an empty client cache before it has hydrated.
  h.js("GoalTasksUI.open()");until("!!document.querySelector('[data-goal-open="+taskId+"]')");click("[data-goal-open="+taskId+"]");detail(taskId);goalFits("landscape task detail");h.screenshot("goal-landscape.png");
  check(plainRows(records()).equals(originalRecords),"Existing records remain byte-for-byte unchanged");check(plainRows(goals().getJSONArray("items")).equals(originalGoals),"Existing goals and progress remain unchanged");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;
 }catch(Throwable error){out.putString("stream","FAIL GoalTasksUiTest after "+passed+": "+android.util.Log.getStackTraceString(error));try{if(h!=null)h.screenshot("goal-ui-failure.png");}catch(Exception ignored){}}
 finally{try{if(h!=null){runOnMainSync(()->h.web.getSettings().setTextZoom(100));clean();if(!originalRecords.isEmpty()&&!plainRows(records()).equals(originalRecords))throw new Exception("Existing records changed during QA");if(!originalGoals.isEmpty()&&!plainRows(goals().getJSONArray("items")).equals(originalGoals))throw new Exception("Existing goals changed during QA");runOnMainSync(()->h.activity.setRequestedOrientation(originalOrientation));runOnMainSync(()->h.activity.finish());}}catch(Exception cleanup){ok=false;out.putString("stream",out.getString("stream","")+"\nQA cleanup failed: "+android.util.Log.getStackTraceString(cleanup));}}
 if(ok)out.putString("stream","PASS GoalTasksUiTest: "+passed+" checks\n");finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
