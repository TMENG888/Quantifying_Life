package com.insight.quantlife.tests;
import org.json.*;
import android.os.*;import android.app.Activity;

/** Exercises already-saved linked goals, UI totals, editing, deletion and live time. */
public final class GoalRollupUiTest extends GoalProgressDeleteTest {
 final String goal="qa-progress-rollup-goal",other="qa-progress-rollup-other",entry="qa-progress-rollup-entry";boolean parentOwned=false;
 JSONObject getGoal(String key)throws Exception{JSONArray all=all().getJSONArray("items");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(key))return all.getJSONObject(i);throw new Exception("Missing goal");}
 JSONObject cmd(String command,JSONObject r)throws Exception{return new JSONObject().put("command",command).put("id",r.getString("id")).put("expectedVersion",r.getLong("updatedAt"));}
 void openGoal(String key)throws Exception{h.js("GoalTasksUI.open("+JSONObject.quote(key)+")");until("document.querySelector('[data-goal-detail]')?.dataset.goalDetail==="+JSONObject.quote(key));}
 void value(String key,String value)throws Exception{h.js("document.querySelector("+JSONObject.quote("#"+key)+").value="+JSONObject.quote(value)+";document.querySelector("+JSONObject.quote("#"+key)+").dispatchEvent(new Event('input'))");}
 @Override void checks()throws Exception{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");baseline=others();
  for(String key:new String[]{id,goal,other}){JSONArray all=all().getJSONArray("items");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(key))throw new Exception("Fixture id in use");}
  JSONObject g=new JSONObject().put("id",goal).put("kind","goal").put("title","自媒体运营短视频课程").put("area","learning").put("targetAmount",28).put("targetUnit","节").put("plannedMinutes",0).put("deadline","2026-11-09T23:59").put("scheduledStart","").put("scheduledEnd","");
  h.call("goalTasks",new JSONObject().put("command","save").put("item",g).put("expectedVersion",JSONObject.NULL));parentOwned=true;
  JSONObject t=new JSONObject(g.toString()).put("id",id).put("kind","task").put("title","短视频课程第1天：完成第1—4节").put("parentId",goal).put("targetAmount",4);
  h.call("goalTasks",new JSONObject().put("command","save").put("item",t).put("expectedVersion",JSONObject.NULL));owns=true;
  h.call("goalTasks",command("progress").put("entryId",entry).put("amount",4).put("minutes",239.8).put("note","已完成第1至4节"));h.call("goalTasks",command("complete"));
  // Cold recreation proves old persisted tasks need no replay or extra registration.
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");openGoal(goal);
  check(h.js("document.querySelector('.goal-measures dd').innerText.replace(/\\s/g,'').includes('4/28节')&&document.querySelector('[data-goal-live=\""+goal+"\"]').textContent==='239.8'").equals("true"),"Existing completed child shows four of twenty-eight and 239.8 minutes after reopening the app");
  check(h.js("document.querySelector('.goal-children header p').textContent.includes('1 / 1')").equals("true"),"Completed-action count agrees with child status");
  check(getGoal(goal).getDouble("ownAmount")==0&&getGoal(goal).getDouble("childAmount")==4,"Goal retains direct and child contributions without explanatory labels or manufactured entries");h.screenshot("goal-rollup-four-of-28.png");
  h.js("window.__goalRoot=document.querySelector('#goal-root');window.scrollTo(0,180);window.__goalY=scrollY");click("[data-goal-open='"+id+"']");until("!!document.querySelector('[data-goal-entry-edit]')");click("#goal-back");until("document.querySelector('[data-goal-detail]')?.dataset.goalDetail==="+JSONObject.quote(goal));
  check(h.js("document.querySelector('#goal-root')===__goalRoot").equals("true"),"Task Back restores its actual parent view from the navigation stack");
  check(h.js("Math.abs(scrollY-__goalY)<3").equals("true"),"Task Back restores the parent goal scroll position");
  click("[data-goal-open='"+id+"']");until("!!document.querySelector('[data-goal-entry-edit]')");click("[data-goal-entry-edit]");value("goal-progress-amount","3");value("goal-progress-minutes","180");click("#goal-progress-save");until("!document.querySelector('#editor').open");click(".goal-parent-link");until("document.querySelector('[data-goal-detail]')?.dataset.goalDetail==="+JSONObject.quote(goal));
  check(getGoal(goal).getDouble("actualAmount")==3&&h.js("document.querySelector('.goal-measures dd').innerText.replace(/\\s/g,'').includes('3/28节')&&document.querySelector('[data-goal-live=\""+goal+"\"]').textContent==='180'").equals("true"),"Correcting child progress updates parent quantity and time immediately");
  click("[data-goal-open='"+id+"']");until("!!document.querySelector('[data-goal-command=start]')");click("[data-goal-command=start]");until("!!document.querySelector('[data-goal-command=pause]')");click(".goal-parent-link");until("document.querySelector('[data-goal-detail]')?.dataset.goalDetail==="+JSONObject.quote(goal));
  String expr="document.querySelector('[data-goal-live=\""+goal+"\"]').textContent";String before=h.js(expr);Thread.sleep(7000);check(!h.js(expr).equals(before),"Parent elapsed time keeps ticking while its child timer runs");
  click("[data-goal-open='"+id+"']");until("!!document.querySelector('[data-goal-command=pause]')");click("[data-goal-command=pause]");until("!!document.querySelector('[data-goal-command=start]')");
  verifyStationary=true;swipe(entry,true,false);h.tap(row(entry)+" [data-goal-entry-delete]",false);until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("document.querySelectorAll('.goal-history li').length===0");click(".goal-parent-link");until("document.querySelector('[data-goal-detail]')?.dataset.goalDetail==="+JSONObject.quote(goal));
  check(getGoal(goal).getDouble("actualAmount")==0&&getGoal(goal).getDouble("actualMinutes")>0&&h.js("document.querySelector('.goal-measures dd').innerText.replace(/\\s/g,'').includes('0/28节')").equals("true"),"Deleting child manual progress removes its quantity and manual time while preserving automatic timer time");
  // Remove owned goal before inherited cleanup compares unrelated rows.
  h.call("goalTasks",new JSONObject().put("command","save").put("item",new JSONObject(task().toString()).put("parentId","")).put("expectedVersion",task().getLong("updatedAt")));
  h.call("goalTasks",cmd("delete",getGoal(goal)));parentOwned=false;check(others().equals(baseline),"Goal sync leaves unrelated tasks and records unchanged");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;}catch(Throwable error){out.putString("stream","FAIL GoalRollupUiTest after "+passed+": "+android.util.Log.getStackTraceString(error));try{if(h!=null)h.screenshot("goal-rollup-failure.png");}catch(Exception ignored){}}
 finally{try{if(h!=null){if(owns){h.call("goalTasks",command("pause"));h.call("goalTasks",command("delete"));}if(parentOwned)h.call("goalTasks",cmd("delete",getGoal(goal)));if(!baseline.isEmpty()&&!others().equals(baseline))throw new Exception("Other tasks changed during QA");runOnMainSync(()->h.activity.finish());}}catch(Exception error){ok=false;out.putString("stream",out.getString("stream","")+"\nCleanup: "+error);}}if(ok)out.putString("stream","PASS GoalRollupUiTest: "+passed+" checks\n");finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
