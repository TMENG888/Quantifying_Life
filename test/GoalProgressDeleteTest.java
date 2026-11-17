package com.insight.quantlife.tests;

import android.app.*;import android.os.*;import android.view.*;
import com.insight.quantlife.*;import org.json.*;

/** Real swipes in the production WebView and bridge; owns only its synthetic task. */
public class GoalProgressDeleteTest extends DocumentEditorTest {
 final String id="qa-progress-swipe-task",one="qa-progress-swipe-one",two="qa-progress-swipe-two";boolean owns=false,verifyStationary=false;String baseline="";
 JSONObject all()throws Exception{return (JSONObject)h.call("goalTasks",new JSONObject().put("command","list"));}
 JSONObject task()throws Exception{JSONArray items=all().getJSONArray("items");for(int i=0;i<items.length();i++)if(items.getJSONObject(i).getString("id").equals(id))return items.getJSONObject(i);throw new Exception("Missing fixture task");}
 String others()throws Exception{JSONArray out=new JSONArray(),items=all().getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject t=items.getJSONObject(i);if(!t.getString("id").equals(id)){t.remove("elapsedMinutes");out.put(t);}}return out.toString();}
 JSONObject command(String command)throws Exception{return new JSONObject().put("command",command).put("id",id).put("expectedVersion",task().getLong("updatedAt"));}
 void refresh()throws Exception{h.js("GoalTasksUI.load()");until("!!document.querySelector('[data-goal-progress-entry=\""+two+"\"]')");}
 String row(String entry){return "[data-goal-progress-entry=\""+entry+"\"]";}
 String geometry(String entry)throws Exception{return h.js("JSON.stringify([...document.querySelectorAll("+JSONObject.quote(row(entry)+" .goal-progress-content, "+row(entry)+" time, "+row(entry)+" strong, "+row(entry)+" p")+")].map(n=>{const r=n.getBoundingClientRect();return [r.left,r.top,r.width,r.height]}))");}
 void swipe(String entry,boolean left,boolean vertical)throws Exception{
  until("!window.ViewMotion||ViewMotion.stats().active===0");
  String selector=row(entry)+" .goal-progress-content";h.js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center'})");Thread.sleep(150);
  JSONArray rect=new JSONArray(h.js("(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [r.left,r.top,r.width,r.height,innerWidth]})()"));
  float scale=(float)(h.web.getWidth()/rect.getDouble(4)),x=(float)((rect.getDouble(0)+rect.getDouble(2)*.6)*scale),y=(float)((rect.getDouble(1)+rect.getDouble(3)*.5)*scale),dx=vertical?2:(left?-110:110)*scale,dy=vertical?-70*scale:2;
  String before=verifyStationary?geometry(entry):"";
  long start=SystemClock.uptimeMillis();touch(start,MotionEvent.ACTION_DOWN,x,y);for(int i=1;i<=10;i++){Thread.sleep(25);touch(start,MotionEvent.ACTION_MOVE,x+dx*i/10,y+dy*i/10);}
  if(verifyStationary)check(geometry(entry).equals(before),"Date, amount and note stay in place while the finger is swiping");
  touch(start,MotionEvent.ACTION_UP,x+dx,y+dy);Thread.sleep(300);
  if(verifyStationary){check(geometry(entry).equals(before),"Progress text keeps its exact layout after Delete is revealed");verifyStationary=false;}
 }
 void touch(long start,int action,float x,float y){runOnMainSync(()->{MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);h.web.dispatchTouchEvent(event);event.recycle();});}
 boolean opened(String entry)throws Exception{return h.js("document.querySelector("+JSONObject.quote(row(entry))+").classList.contains('is-open')").equals("true");}
 void checks()throws Exception{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");baseline=others();JSONArray existing=all().getJSONArray("items");for(int i=0;i<existing.length();i++)if(existing.getJSONObject(i).getString("id").equals(id))throw new Exception("Fixture namespace is already in use");
  JSONObject item=new JSONObject().put("id",id).put("kind","task").put("title","学习小红书封面课程").put("area","learning").put("targetAmount",2).put("targetUnit","节").put("plannedMinutes",120).put("deadline","").put("scheduledStart","").put("scheduledEnd","");
  h.call("goalTasks",new JSONObject().put("command","save").put("item",item).put("expectedVersion",JSONObject.NULL));owns=true;
  h.call("goalTasks",command("progress").put("entryId",one).put("amount",1).put("minutes",5).put("note","正确进度"));
  h.call("goalTasks",command("progress").put("entryId",two).put("amount",1).put("minutes",7).put("note","误填的进度"));h.call("goalTasks",command("complete"));
  h.js("GoalTasksUI.open()");until("!!document.querySelector('[data-goal-view=tasks]')");click("[data-goal-view=tasks]");until("!!document.querySelector('[data-goal-open=\""+id+"\"]')");click("[data-goal-open='"+id+"']");until("!!document.querySelector('.goal-history')");
  check(h.js("document.querySelectorAll('.goal-history li').length===2&&[...document.querySelectorAll('[data-goal-entry-delete]')].every(b=>getComputedStyle(b).visibility==='hidden'&&b.tabIndex===-1)").equals("true"),"Progress actions start concealed and outside keyboard focus order");
  swipe(two,true,true);check(!opened(two),"Actual vertical scroll never reveals Delete");verifyStationary=true;swipe(two,true,false);check(opened(two),"Actual left swipe reveals the right-side Delete action");
  check(task().getDouble("actualAmount")==2&&task().getDouble("actualMinutes")==12,"Swiping by itself never modifies the task");
  check(h.js("(()=>{const b=document.querySelector("+JSONObject.quote(row(two)+" [data-goal-entry-delete]")+").getBoundingClientRect();return b.width>=44&&b.height>=44&&b.right<=innerWidth+1&&document.documentElement.scrollWidth<=innerWidth+1;})()").equals("true"),"Delete is reachable without horizontal page overflow");h.screenshot("task-progress-swipe.png");
  swipe(two,false,false);check(!opened(two),"Right swipe closes the action");swipe(two,true,false);swipe(one,true,false);check(!opened(two)&&opened(one),"Only one progress record is open at a time");
  h.tap(row(one)+" .goal-progress-content",false);Thread.sleep(150);check(!opened(one),"Tapping the open record closes its action");
  swipe(two,true,false);h.tap(row(two)+" [data-goal-entry-delete]",false);until("document.querySelector('#confirm-dialog').open");click("#confirm-cancel");
  check(task().getJSONArray("entries").length()==2&&task().getDouble("actualAmount")==2,"Cancelling deletion preserves the original progress");
  h.tap(row(two)+" [data-goal-entry-delete]",false);until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("document.querySelectorAll('.goal-history li').length===1");
  check(task().getDouble("actualAmount")==1&&task().getDouble("actualMinutes")==5,"Confirming deletes just the incorrect row and reverses its quantity and manual time");
  check(task().getString("status").equals("active")&&h.js("!!document.querySelector('[data-goal-check][aria-checked=false]')").equals("true"),"Completed task reopens when corrected quantity drops below target");
  check(h.js("document.querySelector('.goal-history').innerText.includes('正确进度')&&!document.querySelector('.goal-history').innerText.includes('误填的进度')").equals("true"),"Correct progress remains visible after deletion");
  h.screenshot("task-progress-corrected.png");
  runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(250);verifyStationary=true;swipe(one,true,false);check(h.js("document.documentElement.scrollWidth<=innerWidth+1&&document.querySelector('[data-goal-entry-delete]').getBoundingClientRect().height>=44&&[...document.querySelectorAll('.goal-progress-content')].every(n=>n.scrollWidth<=n.clientWidth+1)").equals("true"),"200 percent text remains readable with a reachable Delete action");
  check(h.js("(()=>{const c=document.querySelector('.goal-progress-content').getBoundingClientRect(),b=document.querySelector('[data-goal-entry-delete]').getBoundingClientRect();return c.right<=b.left+1;})()").equals("true"),"Delete occupies a separate reserved area and never covers the text");h.screenshot("task-progress-large.png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
  h.js("document.querySelector('.goal-progress-content').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}))");h.js("document.querySelector('.goal-progress-content').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true}))");check(opened(one)&&h.js("document.activeElement.hasAttribute('data-goal-entry-delete')").equals("true"),"Keyboard activation exposes and focuses Delete");
  click("[data-goal-command=start]");until("!!document.querySelector('[data-goal-command=pause]')");swipe(one,true,false);if(!opened(one))swipe(one,true,false);h.tap(row(one)+" [data-goal-entry-delete]",false);until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("document.querySelectorAll('.goal-history li').length===0");
  check(task().getDouble("actualAmount")==0&&task().getDouble("actualMinutes")==0&&task().getLong("timerStartedAt")>0,"Deleting final manual entry does not stop or erase the running stopwatch");
  check(h.js("document.querySelector('.goal-history').innerText.includes('暂无进度')").equals("true"),"Deleting final entry shows the empty history state");check(others().equals(baseline),"All other goals and progress remain unchanged");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;}catch(Throwable error){out.putString("stream","FAIL "+getClass().getSimpleName()+" after "+passed+": "+android.util.Log.getStackTraceString(error));try{if(h!=null)h.screenshot("task-progress-failure.png");}catch(Exception ignored){}}
 finally{try{if(h!=null){runOnMainSync(()->h.web.getSettings().setTextZoom(100));if(owns){h.call("goalTasks",command("pause"));h.call("goalTasks",command("delete"));}if(!baseline.isEmpty()&&!others().equals(baseline))throw new Exception("Other tasks changed during QA");runOnMainSync(()->h.activity.finish());}}catch(Exception error){ok=false;out.putString("stream",out.getString("stream","")+"\nCleanup: "+error);}}
 if(ok)out.putString("stream","PASS "+getClass().getSimpleName()+": "+passed+" checks\n");finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
