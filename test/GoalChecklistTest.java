package com.insight.quantlife.tests;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.MotionEvent;
import com.insight.quantlife.*;
import org.json.*;

/** Isolated ledger tests and actual WebView touch gestures. Never run on a user's phone. */
public final class GoalChecklistTest extends DocumentEditorTest {
 final String prefix="qa-checklist-",goal=prefix+"goal",task=prefix+"task";
 JSONObject item(String id,String kind)throws Exception{return new JSONObject().put("id",id).put("kind",kind).put("title",kind.equals("goal")?"完成短视频课程":"阅读课程并整理笔记").put("area","learning").put("parentId","").put("targetAmount",10).put("targetUnit","节").put("plannedMinutes",30).put("deadline","2000-01-01T12:00").put("scheduledStart","").put("scheduledEnd","");}
 JSONObject cmd(String command,JSONObject r)throws Exception{return new JSONObject().put("command",command).put("id",r.getString("id")).put("expectedVersion",r.getLong("updatedAt"));}
 JSONObject save(JSONObject r)throws Exception{return new JSONObject().put("command","save").put("item",r).put("expectedVersion",JSONObject.NULL);}
 JSONObject exec(SQLiteDatabase db,GoalTaskStoreTest.Clock c,JSONObject p)throws Exception{return GoalTaskStore.execute(db,p,c).optJSONObject("saved");}
 JSONObject get(SQLiteDatabase db,GoalTaskStoreTest.Clock c,String key)throws Exception{JSONArray all=GoalTaskStore.execute(db,new JSONObject(),c).getJSONArray("items");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(key))return all.getJSONObject(i);throw new Exception("Missing test task");}
 void ledger()throws Exception{
  GoalTaskStoreTest.Clock c=new GoalTaskStoreTest.Clock();
  try(SQLiteDatabase db=SQLiteDatabase.create(null)){
   JSONObject g=exec(db,c,save(item(goal,"goal"))),t=exec(db,c,save(item(task,"task").put("parentId",goal)));
   t=exec(db,c,cmd("start",t));c.add(120000);t=exec(db,c,cmd("check",t));
   check(t.getString("status").equals("completed")&&t.optBoolean("manualCompleted")&&t.getDouble("actualAmount")==0&&t.getJSONArray("entries").length()==0,"Manual check completes a task without fabricating progress");
   check(t.getDouble("actualMinutes")==2&&t.getLong("timerStartedAt")==0,"Checking settles the actual running timer once");
   g=exec(db,c,cmd("check",get(db,c,goal)));check(g.getString("status").equals("completed")&&g.getDouble("actualAmount")==0,"A long-term goal can also be checked at zero quantity");
   t=exec(db,c,cmd("reopen",t));check(t.getString("status").equals("active")&&!t.optBoolean("manualCompleted")&&!t.has("completedAt")&&t.getDouble("actualMinutes")==2&&t.getLong("timerStartedAt")==0,"Unchecking restores active state without restarting or erasing time");
   JSONObject stale=cmd("check",t);t=exec(db,c,cmd("progress",t).put("entryId",prefix+"entry").put("amount",2).put("minutes",1));
   boolean rejected=false;try{exec(db,c,stale);}catch(Exception expected){rejected=true;}check(rejected,"Stale checkbox writes cannot override newer progress");
   check(get(db,c,goal).getString("status").equals("completed")&&get(db,c,goal).getDouble("actualAmount")==2,"Child changes preserve manual parent completion and true rollup quantity");
   t=exec(db,c,cmd("check",t));t=exec(db,c,cmd("updateProgress",t).put("entryId",prefix+"entry").put("editId",prefix+"edit").put("amount",1).put("minutes",1));
   check(t.getString("status").equals("completed")&&t.getDouble("actualAmount")==1,"Editing a progress entry preserves an explicit check");
   t=exec(db,c,cmd("deleteProgress",t).put("entryId",prefix+"entry"));check(t.getString("status").equals("completed")&&t.getDouble("actualAmount")==0,"Deleting an entry preserves an explicit check");
   JSONArray backup=GoalTaskStore.export(db,c);try(SQLiteDatabase restored=SQLiteDatabase.create(null)){
    check(GoalTaskStore.restore(restored,backup)==2,"Backup restores checked tasks and goals below target");
    check(get(restored,c,goal).getString("status").equals("completed")&&get(restored,c,task).optBoolean("manualCompleted"),"Restore preserves checkbox state after a fresh database load");
   }
   exec(db,c,cmd("delete",get(db,c,goal)).put("detachChildren",true).put("stopTimer",true));t=get(db,c,task);
   check(t.getString("parentId").isEmpty()&&t.getDouble("actualMinutes")==2,"Deleting a goal detaches its task and preserves task history and time");
   t=exec(db,c,cmd("reopen",t));t=exec(db,c,cmd("start",t));exec(db,c,cmd("delete",t).put("stopTimer",true));
   check(GoalTaskStore.execute(db,new JSONObject(),c).getJSONArray("items").length()==0,"Explicit confirmed deletion also works for a running task");
  }
 }
 JSONObject current(String key)throws Exception{JSONArray all=((JSONObject)h.call("goalTasks",new JSONObject())).getJSONArray("items");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(key))return all.getJSONObject(i);return null;}
 void clean()throws Exception{try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){db.delete("goal_task_entries","task_id LIKE ?",new String[]{prefix+"%"});db.delete("goal_tasks","id LIKE ?",new String[]{prefix+"%"});}}
 void realGesture(String selector,float dx,float dy)throws Exception{
  h.js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center'})");Thread.sleep(200);
  JSONArray point=new JSONArray(h.js("(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [r.right-25,r.top+r.height/2,innerWidth]})()"));
  float scale=(float)(h.web.getWidth()/point.getDouble(2)),x=(float)point.getDouble(0)*scale,y=(float)point.getDouble(1)*scale;
  long start=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){final int step=i;runOnMainSync(()->{int action=step==0?MotionEvent.ACTION_DOWN:step==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x+dx*scale*step/12,y+dy*scale*step/12,0);h.web.dispatchTouchEvent(event);event.recycle();});Thread.sleep(20);}Thread.sleep(300);
 }
 void ui()throws Exception{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");h.call("goalTasks",new JSONObject());clean();
  h.call("goalTasks",save(item(goal,"goal")));h.call("goalTasks",save(item(task,"task").put("parentId",goal)));
  h.js("GoalTasksUI.open()");until("!!document.querySelector('[data-goal-check=\""+task+"\"]')");
  check(h.js("document.querySelectorAll('.goal-swipe-row .goal-area,.goal-swipe-row .goal-status,.goal-form-hint,.goal-rollup-note,.goal-plan-hint').length===0").equals("true"),"Compact rows have no category, overdue or explanatory labels");
  click("[data-goal-check='"+task+"']");until("document.querySelector('[data-goal-check=\""+task+"\"]')?.getAttribute('aria-checked')==='true'");
  check(current(task).getString("status").equals("completed")&&current(task).getDouble("actualAmount")==0,"List checkbox calls the native store and leaves quantity unchanged");
  check(h.js("!!document.querySelector('.goal-completed [data-goal-row=\""+task+"\"]')").equals("true"),"A task checked today remains accessible in the completed group");
  click(".goal-completed>summary");check(h.js("!document.querySelector('.goal-completed').open").equals("true"),"Completed rows can be collapsed");click(".goal-completed>summary");
  click("[data-goal-check='"+task+"']");until("document.querySelector('[data-goal-check=\""+task+"\"]')?.getAttribute('aria-checked')==='false'");
  check(current(task).getString("status").equals("active"),"Second checkbox tap restores the task without opening its detail");
  h.screenshot("goal-checklist-tasks.png");
  String row="[data-goal-row='"+task+"']",body=row+" .goal-item-body";
  realGesture(body,-66,2);check(h.js("document.querySelector("+JSONObject.quote(row)+").classList.contains('is-open')").equals("true"),"Real finger swipe reveals task deletion");
  check(h.js("Math.abs(new DOMMatrix(getComputedStyle(document.querySelector("+JSONObject.quote(row+" .goal-item")+")).transform).m41+76)<1").equals("true"),"The entire task row follows the finger to the left");h.screenshot("goal-checklist-swipe.png");
  realGesture(body,66,1);check(h.js("!document.querySelector("+JSONObject.quote(row)+").classList.contains('is-open')").equals("true"),"Rightward swipe closes deletion");
  realGesture(body,-10,1);check(h.js("!document.querySelector("+JSONObject.quote(row)+").classList.contains('is-open')").equals("true"),"A short swipe does not expose deletion");
  click("[data-goal-view=goals]");until("!!document.querySelector('[data-goal-row=\""+goal+"\"]')");click("[data-goal-check='"+goal+"']");until("document.querySelector('[data-goal-check=\""+goal+"\"]')?.getAttribute('aria-checked')==='true'");
  check(current(goal).optBoolean("manualCompleted"),"Long-term goal list uses the same reversible checkbox");h.screenshot("goal-checklist-goals.png");
  runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(200);check(h.js("document.documentElement.scrollWidth<=innerWidth+1&&[...document.querySelectorAll('.goal-item-title')].every(e=>e.scrollWidth<=e.clientWidth+1)").equals("true"),"Checklist remains readable without horizontal overflow at 200% text");h.screenshot("goal-checklist-large.png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{};GoalTasksUI.open()");until("!!document.querySelector('[data-goal-view=goals]')");click("[data-goal-view=goals]");until("document.querySelector('[data-goal-check=\""+goal+"\"]')?.getAttribute('aria-checked')==='true'");check(current(goal).getString("status").equals("completed"),"Checkbox state persists across activity reconstruction");
  realGesture("[data-goal-row='"+goal+"'] .goal-item-body",-66,1);h.tap("[data-goal-row='"+goal+"'] .goal-row-delete",false);until("document.querySelector('#confirm-dialog').open");click("#confirm-cancel");check(current(goal)!=null,"Cancelling delete preserves a checked goal");
  realGesture("[data-goal-row='"+goal+"'] .goal-item-body",-66,1);h.tap("[data-goal-row='"+goal+"'] .goal-row-delete",false);until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("!document.querySelector('[data-goal-row=\""+goal+"\"]')");
  check(current(goal)==null&&current(task)!=null&&current(task).getString("parentId").isEmpty(),"Confirmed left-swipe delete preserves and detaches the goal's task");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");ledger();ui();ok=true;
 }catch(Throwable error){out.putString("stream","FAIL GoalChecklistTest after "+passed+": "+android.util.Log.getStackTraceString(error));try{if(h!=null)h.screenshot("goal-checklist-failure.png");}catch(Exception ignored){}}
 finally{try{if(h!=null){runOnMainSync(()->h.web.getSettings().setTextZoom(100));clean();runOnMainSync(()->h.activity.finish());}}catch(Exception error){ok=false;out.putString("stream",out.getString("stream","")+" cleanup: "+error);}}
 if(ok)out.putString("stream","PASS GoalChecklistTest: "+passed+" checks\n");finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
