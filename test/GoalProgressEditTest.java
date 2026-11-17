package com.insight.quantlife.tests;

import org.json.*;

/** Existing entry correction through real taps and the production native ledger. */
public final class GoalProgressEditTest extends GoalProgressDeleteTest {
 final String third="qa-progress-edit-three";
 void field(String id,String value)throws Exception{h.js("document.querySelector("+JSONObject.quote("#"+id)+").value="+JSONObject.quote(value)+";document.querySelector("+JSONObject.quote("#"+id)+").dispatchEvent(new Event('input',{bubbles:true}))");}
 void editThird()throws Exception{String selector=row(third)+" [data-goal-entry-edit]";h.js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center'})");Thread.sleep(150);h.tap(selector,false);until("!!document.querySelector('#goal-progress-form')");}
 @Override void checks()throws Exception{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");baseline=others();JSONArray items=all().getJSONArray("items");for(int i=0;i<items.length();i++)if(items.getJSONObject(i).getString("id").equals(id))throw new Exception("Fixture namespace in use");
  JSONObject item=new JSONObject().put("id",id).put("kind","task").put("title","短视频课程第1天：完成第1—4节").put("area","learning").put("targetAmount",4).put("targetUnit","节").put("plannedMinutes",120).put("deadline","").put("scheduledStart","").put("scheduledEnd","");
  h.call("goalTasks",new JSONObject().put("command","save").put("item",item).put("expectedVersion",JSONObject.NULL));owns=true;
  h.call("goalTasks",command("progress").put("entryId",one).put("amount",1).put("minutes",5).put("note","小红书封面课"));h.call("goalTasks",command("progress").put("entryId",two).put("amount",1).put("minutes",0).put("note","自媒体选题课"));h.call("goalTasks",command("progress").put("entryId",third).put("amount",3).put("minutes",7).put("note","自媒体标题"));h.call("goalTasks",command("complete"));
  long at=task().getJSONArray("entries").getJSONObject(2).getLong("at");
  h.js("GoalTasksUI.open()");until("!!document.querySelector('[data-goal-view=tasks]')");click("[data-goal-view=tasks]");until("!!document.querySelector('[data-goal-open=\""+id+"\"]')");click("[data-goal-open='"+id+"']");until("!!document.querySelector('.goal-history')");
  check(h.js("document.querySelectorAll('[data-goal-entry-edit]').length===3&&document.querySelector('.goal-measures dd').innerText.includes('5')").equals("true"),"Each existing progress row has an Edit action, including the incorrect 5 of 4 fixture");
  editThird();check(h.js("document.querySelector('#goal-progress-amount').value==='3'&&document.querySelector('#goal-progress-minutes').value==='7'&&document.querySelector('#goal-progress-note').value==='自媒体标题'").equals("true"),"Edit prefills the original amount, manual minutes and note");
  field("goal-progress-amount","1");check(h.js("document.querySelector('#goal-progress-total').textContent==='保存后累计：3 / 4 节'").equals("true"),"Preview shows five becoming three when the entry changes from three to one");h.screenshot("task-progress-edit-form.png");
  click("#close-editor");check(task().getDouble("actualAmount")==5,"Closing the editor without saving leaves persisted progress unchanged");
  editThird();field("goal-progress-amount","1");field("goal-progress-note","自媒体标题（已纠正为一节）");h.js("document.querySelector('#goal-progress-form').requestSubmit();document.querySelector('#goal-progress-form')?.requestSubmit()");until("!document.querySelector('#editor').open");
  JSONObject corrected=task(),entry=corrected.getJSONArray("entries").getJSONObject(2);
  check(corrected.getDouble("actualAmount")==3&&corrected.getJSONArray("entries").length()==3&&entry.getDouble("amount")==1,"Saving replaces the same entry and updates five to three exactly once");
  check(entry.getLong("at")==at&&entry.getString("id").equals(third)&&corrected.getDouble("actualMinutes")==12,"Correction preserves original time, entry identity and unrelated manual minutes");
  check(corrected.getString("status").equals("active")&&h.js("document.querySelector('.goal-measures dd').innerText.replace(/\\s/g,'').includes('3/4节')").equals("true"),"Updated quantity and completion status agree in the task detail");h.js("document.querySelector('#toast').classList.remove('show')");h.screenshot("task-progress-edit-result.png");
  String selector=row(third)+" .goal-progress-content";h.js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center'})");h.tap(selector,false);until("!!document.querySelector('#goal-progress-form')");field("goal-progress-amount","-1");click("#goal-progress-save");until("!document.querySelector('#goal-form-error').hidden");check(task().getDouble("actualAmount")==3&&h.js("document.querySelector('#editor').open").equals("true"),"Invalid edits display an inline error and preserve the existing entry");click("#close-editor");
  click("[data-goal-command=start]");until("!!document.querySelector('[data-goal-command=pause]')");editThird();check(h.js("!document.querySelector('#goal-progress-minutes').disabled").equals("true"),"Historical manual minutes can be corrected while the task timer is running");field("goal-progress-minutes","4");click("#goal-progress-save");until("!document.querySelector('#editor').open");check(task().getDouble("actualMinutes")==9&&task().getLong("timerStartedAt")>0,"Editing historical minutes updates only that contribution and leaves the timer running");
  verifyStationary=true;swipe(third,true,false);check(opened(third),"Left swipe still reveals Delete with text anchored after adding Edit");h.tap(row(third)+" [data-goal-entry-delete]",false);until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("document.querySelectorAll('.goal-history li').length===2");check(task().getDouble("actualAmount")==2&&task().getDouble("actualMinutes")==5,"Deleting an edited entry deducts its corrected values");
  check(others().equals(baseline),"Editing progress leaves unrelated goals and entries unchanged");
 }
}
