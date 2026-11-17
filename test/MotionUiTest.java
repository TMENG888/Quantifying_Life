package com.insight.quantlife.tests;
import android.app.Activity;import android.database.sqlite.SQLiteDatabase;import android.os.*;import android.view.inputmethod.*;import org.json.*;

/** Samples actual rendered frames and durable deletion through the production bridge. */
public final class MotionUiTest extends DocumentEditorTest {
 final String prefix="qa-motion-",first=prefix+"first",second=prefix+"second";
 JSONObject item(String id,int index)throws Exception{return new JSONObject().put("id",id).put("kind","task").put("title",index==0?"阅读课程并整理笔记":"练习一个实际案例").put("area","learning").put("parentId","").put("targetAmount",4).put("targetUnit","节").put("plannedMinutes",30).put("deadline","2000-01-0"+(index+1)+"T10:00").put("scheduledStart","").put("scheduledEnd","");}
 JSONObject current(String id)throws Exception{JSONArray a=((JSONObject)h.call("goalTasks",new JSONObject())).getJSONArray("items");for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return a.getJSONObject(i);return null;}
 void clean()throws Exception{try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){db.delete("goal_task_entries","task_id LIKE ?",new String[]{prefix+"%"});db.delete("goal_tasks","id LIKE ?",new String[]{prefix+"%"});db.delete("records","id LIKE ?",new String[]{prefix+"%"});}}
 void observe(String selector)throws Exception{
  h.js("window.__samples=[];window.__motions=ViewMotion.stats();window.__start=performance.now();window.__sample=()=>{const n=document.querySelector("+JSONObject.quote(selector)+");if(n){const r=n.getBoundingClientRect(),s=getComputedStyle(n);__samples.push({t:performance.now()-__start,opacity:+s.opacity,height:r.height,exiting:n.hasAttribute('data-ui-exiting'),transform:s.transform});}if(performance.now()-__start<1100)requestAnimationFrame(__sample);};requestAnimationFrame(__sample)");
 }
 void checks()throws Exception{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");h.call("goalTasks",new JSONObject());clean();
  for(int i=0;i<2;i++)h.call("goalTasks",new JSONObject().put("command","save").put("expectedVersion",JSONObject.NULL).put("item",item(i==0?first:second,i)));
  h.js("GoalTasksUI.open()");until("!!document.querySelector('[data-goal-row=\""+first+"\"]')");Thread.sleep(250);
  String row="[data-goal-row='"+first+"']",next="[data-goal-row='"+second+"']";
  h.js("document.querySelector("+JSONObject.quote(row+" .goal-item-body")+").dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true}))");
  check(h.js("document.querySelector("+JSONObject.quote(row)+").classList.contains('is-open')").equals("true"),"Task delete action is exposed before refresh");
  h.js("document.querySelector('[data-goal-row=\""+second+"\"] .goal-item-title').textContent+=' ';GoalTasksUI.load()");Thread.sleep(200);
  check(h.js("document.querySelector("+JSONObject.quote(row)+").classList.contains('is-open')").equals("true"),"No-op background read preserves the open swipe action");
  // Force a real unrelated data patch and ensure binding refresh does not close the row.
  JSONObject r=current(second);h.call("goalTasks",new JSONObject().put("command","save").put("item",new JSONObject(r.toString()).put("title","练习一个实际案例，整理结果")).put("expectedVersion",r.getLong("updatedAt")));
  h.js("GoalTasksUI.load()");until("document.querySelector('[data-goal-row=\""+second+"\"] .goal-item-title').textContent.includes('整理结果')");
  check(h.js("document.querySelector("+JSONObject.quote(row)+").classList.contains('is-open')").equals("true"),"Real background patch preserves swipe state and listeners");
  click(row+" .goal-row-delete");until("document.querySelector('#confirm-dialog').open");
  observe(row);h.js("window.__neighbor=[];window.__nextStart=performance.now();window.__nextFrame=()=>{const n=document.querySelector("+JSONObject.quote(next)+");if(n)__neighbor.push({y:n.getBoundingClientRect().top,transform:getComputedStyle(n).transform});if(performance.now()-__nextStart<900)requestAnimationFrame(__nextFrame);};requestAnimationFrame(__nextFrame);document.querySelector('#confirm-ok').click()");
  until("!document.querySelector("+JSONObject.quote(row)+")");Thread.sleep(400);
  check(h.js("__samples.some(s=>s.exiting&&s.opacity>.01&&s.opacity<.98)").equals("true"),"Deletion contains visible intermediate fade frames");
  check(h.js("__samples.some(s=>s.exiting&&s.height<__samples[0].height*.95)").equals("true"),"The deleted card visibly shrinks before removal");
  check(h.js("__neighbor.some(s=>s.transform!=='none'&&s.transform!=='matrix(1, 0, 0, 1, 0, 0)')&&ViewMotion.stats().moves>__motions.moves").equals("true"),"The next card glides into the gap through transform animation");
  check(current(first)==null&&current(second)!=null,"Animated deletion removes only the confirmed task from the native ledger");
  check(h.js("ViewMotion.stats().active===0&&!document.querySelector('[data-ui-exiting]')").equals("true"),"All animation handles and busy markers are released");h.screenshot("motion-task-list.png");
  // A rejected write must not play a successful disappearance.
  r=current(second);h.call("goalTasks",new JSONObject().put("command","save").put("item",new JSONObject(r.toString()).put("title","新版本任务")).put("expectedVersion",r.getLong("updatedAt")));
  h.js("document.querySelector("+JSONObject.quote(next+" .goal-item-body")+").dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true}));window.__exitCount=ViewMotion.stats().exits");click(next+" .goal-row-delete");until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");Thread.sleep(350);
  check(current(second)!=null&&h.js("!!document.querySelector("+JSONObject.quote(next)+")&&ViewMotion.stats().exits===__exitCount").equals("true"),"A failed stale deletion leaves its card visible and does not animate success");
  h.js("GoalTasksUI.load()");until("document.querySelector('[data-goal-row=\""+second+"\"] .goal-item-title').textContent==='新版本任务'");
  h.js("window.__match=matchMedia;window.matchMedia=q=>q==='(prefers-reduced-motion: reduce)'?{matches:true}:__match(q);window.__beforeReduced=ViewMotion.stats().exits;document.querySelector("+JSONObject.quote(next+" .goal-item-body")+").dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowLeft',bubbles:true}))");click(next+" .goal-row-delete");until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("!document.querySelector("+JSONObject.quote(next)+")");
  check(h.js("ViewMotion.stats().exits===__beforeReduced&&ViewMotion.stats().active===0").equals("true"),"Reduced-motion setting skips the effects without blocking deletion");h.js("window.matchMedia=__match");
  // Shared list motion works outside goals and keeps record data unchanged.
  for(int i=0;i<3;i++){String id=prefix+"record-"+i;h.call("saveRecord",new JSONObject().put("record",new JSONObject().put("id",id).put("type","learning").put("date","2026-11-16").put("title","动效验证笔记 "+i).put("note","这是一条测试正文，用来验证记录列表的过渡。").put("durationMinutes",5).put("platform","本机测试").put("contentType","article").put("tags",new JSONArray())).put("allowDuplicate",true));}
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");click("[data-page=records]");until("!!document.querySelector('#learning-search')");h.js("document.querySelector('#learning-search').value='动效验证笔记';document.querySelector('#learning-search').dispatchEvent(new Event('input'));window.__input=document.querySelector('#learning-search');window.__before=ViewMotion.capture(document.querySelector('#main'));document.querySelector('[data-record]').remove();ViewMotion.play(document.querySelector('#main'),__before)");
  check(h.js("ViewMotion.stats().moves>0&&document.querySelector('#learning-search')===__input").equals("true"),"The shared transition moves record rows while retaining the search input node");Thread.sleep(350);
  // Sample a burst of retained-node patches; this is a WebView check, not a physical-phone FPS claim.
  h.js("window.__durations=[];for(let i=0;i<12;i++){const t=performance.now();document.querySelector('#learning-search').value='动效验证笔记'+(i%2?'':' ');document.querySelector('#learning-search').dispatchEvent(new Event('input'));__durations.push(performance.now()-t)}");
  check(h.js("document.querySelector('#learning-search')===__input&&__durations.every(Number.isFinite)").equals("true"),"Repeated filtering retains the input rather than rebuilding the page");
  android.util.Log.i("MotionQA","render samples ms="+h.js("JSON.stringify(__durations)"));
  String recordId=prefix+"record-1",recordRow="[data-record='"+recordId+"']";
  click(recordRow);until("document.querySelector('#editor').open&&!!document.querySelector('#doc-mode')");click("#doc-mode");click("#doc-more");click("#doc-delete");until("document.querySelector('#confirm-dialog').open");observe(recordRow);click("#confirm-ok");until("!document.querySelector('#editor').open&&!document.querySelector("+JSONObject.quote(recordRow)+")");Thread.sleep(300);
  boolean deleted=true;JSONArray remaining=records();for(int i=0;i<remaining.length();i++)if(remaining.getJSONObject(i).getString("id").equals(recordId))deleted=false;
  check(deleted&&h.js("ViewMotion.stats().exits>__motions.exits").equals("true"),"Reader deletion uses the same transition and removes the actual saved learning record");
  check(h.js("__samples.some(s=>s.exiting&&s.opacity>.01&&s.opacity<.98)").equals("true"),"Learning-record deletion has intermediate visible fade frames");
  for(String tab:new String[]{"thoughts","creation"}){
   click("[data-record-tab="+tab+"]");String selector=tab.equals("thoughts")?"#thought-search":"#creation-search";until("!!document.querySelector("+JSONObject.quote(selector)+")");h.tap(selector,false);Thread.sleep(200);
   h.js("window.__composingInput=document.querySelector("+JSONObject.quote(selector)+")");final InputConnection[] connection={null};runOnMainSync(()->{connection[0]=h.web.onCreateInputConnection(new EditorInfo());connection[0].setComposingText("ceshi",1);});until("__composingInput.value.includes('ceshi')");
   h.js("document.dispatchEvent(new Event('visibilitychange'))");Thread.sleep(100);runOnMainSync(()->connection[0].commitText("测试",1));until("__composingInput.value.includes('测试')&&!__composingInput.value.includes('ceshi')");
   check(h.js("document.querySelector("+JSONObject.quote(selector)+")===__composingInput&&document.activeElement===__composingInput").equals("true"),tab+" filtering preserves the native Chinese input connection and focus");
  }
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;}catch(Throwable err){out.putString("stream","FAIL MotionUiTest after "+passed+": "+android.util.Log.getStackTraceString(err));try{if(h!=null)h.screenshot("motion-failure.png");}catch(Exception ignored){}}
 finally{try{if(h!=null){h.js("ViewMotion.stop()");clean();runOnMainSync(()->h.activity.finish());}}catch(Exception err){ok=false;out.putString("stream",out.getString("stream","")+" cleanup: "+err);}}
 if(ok)out.putString("stream","PASS MotionUiTest: "+passed+" checks\n");finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
