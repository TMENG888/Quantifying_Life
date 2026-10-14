package com.insight.quantlife.tests;
import android.app.*;
import android.os.*;
import org.json.*;
import java.io.*;
import com.insight.quantlife.*;
/** Only the disposable synthetic AVD is used; no real API calls or user phone data. */
public class WorkLayoutTest extends WorkCalendarWikiTest {
 JSONObject snapshot()throws Exception{
  JSONObject data=init();
  try(TrackStore t=new TrackStore(getTargetContext())){data.put("gpx",t.gpx("2026-12-15")).put("raw",t.diagnostics("2026-12-15").getJSONArray("rawPoints"));}
  return data;
 }
 void seedLayout()throws Exception{
  holdAutomation();
  check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.7.6"),"Previous signed version 1.7.6");
  try(FileOutputStream out=getTargetContext().openFileOutput("work-layout-upgrade.json",0)){out.write(snapshot().toString().getBytes("UTF-8"));}
 }
 void checkUpgrade()throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  try(InputStream in=getTargetContext().openFileInput("work-layout-upgrade.json")){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);}
  JSONObject before=new JSONObject(out.toString("UTF-8")),after=snapshot();
  check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.7.7"),"Signed 1.7.7 cover installation");
  for(String key:new String[]{"records","messages","wikiPages","chatSession","settings","gpx","raw"})check(before.get(key).toString().equals(after.get(key).toString()),"Upgrade preserves "+key);
 }
 void compactLayout()throws Exception{
  holdAutomation();JSONArray all=init().getJSONArray("records");
  // Remove only test fixtures and active synthetic shifts from this disposable AVD.
  for(int i=0;i<all.length();i++){JSONObject r=all.getJSONObject(i);if(r.getString("id").startsWith("layout-")||(r.optString("type").equals("work")&&r.optString("workEnd").isEmpty()))h.call("deleteRecord",new JSONObject().put("id",r.getString("id")));}
  String today=new JSONArray(h.js("[LifeDomain.localDate()]")).getString(0);
  h.call("saveRecord",new JSONObject().put("record",note("layout-log-a","完成本周需求梳理，下一步验证关键流程。").put("title","需求梳理完成").put("date",today)).put("allowDuplicate",true));
  h.call("saveRecord",new JSONObject().put("record",note("layout-log-b","整理用户反馈，已将问题拆分为三个任务。").put("title","用户反馈整理").put("date",today)).put("allowDuplicate",true));
  String start=new JSONArray(h.js("[LifeDomain.localTime(new Date(Date.now()-68*60000))]")).getString(0);
  h.call("saveRecord",new JSONObject().put("record",new JSONObject().put("id","layout-current").put("type","work").put("title","工作班次").put("date",start.substring(0,10)).put("workStart",start).put("workEnd","").put("durationMinutes",0).put("note","").put("tags",new JSONArray()).put("breaks",new JSONArray())));
  reload();holdAutomation();click("[data-page=records]");click("[data-record-tab=work]");click("[data-range=today]");
  jsCheck("!document.querySelector('.records-tools')&&!document.querySelector('.work-overview')&&!document.querySelector('.work-day-heading')&&!document.querySelector('#work-day-input')","All three circled UI regions absent in actual Android WebView");
  jsCheck("document.querySelectorAll('[data-record-tab]').length===3&&document.querySelectorAll('.bottom-nav button').length===4","Record categories and bottom navigation retained");
  jsCheck("getComputedStyle(document.querySelector('.work-current')).color==='rgb(33, 106, 84)'","Original green theme retained");
  jsCheck("document.querySelector('[data-work-view=logs]').getAttribute('aria-selected')==='true'&&document.querySelectorAll('.work-day-list [data-record]').length>=2","Today logs are primary and immediately available");
  jsCheck("(()=>{const r=document.querySelector('.work-day-list [data-record]').getBoundingClientRect(),n=document.querySelector('.bottom-nav').getBoundingClientRect();return r.top>=0&&r.bottom<=n.top})()","First work log fully visible above bottom navigation without scrolling");
  jsCheck("document.documentElement.scrollWidth<=innerWidth+1","Compact work layout fits phone width");
  jsCheck("(()=>{const a=document.querySelector('[data-range=today]').getBoundingClientRect(),b=document.querySelector('[data-range=week]').getBoundingClientRect();return b.left-a.right>=16})()","Period buttons retain spacing on older Android WebViews");
  jsCheck("(()=>{const a=document.querySelector('[data-action=toggle-break]').getBoundingClientRect(),b=document.querySelector('[data-action=stop-work]').getBoundingClientRect();return b.left-a.right>=6})()","Rest and end-work touch targets are separated");
  Thread.sleep(400);h.screenshot("work-compact-active-1.7.7.png");
  click("[data-action=toggle-break]");until("document.querySelector('.work-current').innerText.includes('正在休息')");
  check(find("layout-current").getJSONArray("breaks").length()==1,"Actual rest start still updates native SQLite");
  click("[data-action=toggle-break]");until("document.querySelector('.work-current').innerText.includes('工作中')");
  check(!find("layout-current").getJSONArray("breaks").getJSONObject(0).optString("end").isEmpty(),"Actual rest end retained");
  click("[data-action=stop-work]");until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("document.querySelector('[data-action=start-work]')!==null");
  check(!find("layout-current").optString("workEnd").isEmpty(),"Actual end-work action retained");
  click("[data-range=week]");jsCheck("document.querySelectorAll('[data-work-day]').length===7","Week date strip remains navigable without separate date row");
  click("[data-range=month]");jsCheck("!document.querySelector('#work-month-calendar').open&&!document.querySelector('.work-overview')","Month stays compact with calendar initially closed");
  click("#work-month-calendar summary");Thread.sleep(300);h.screenshot("work-compact-month-1.7.7.png");
  click("[data-work-month='-1']");jsCheck("document.querySelector('.work-list-heading').innerText.includes('年')","Historical date is clear in content heading");
  click("[data-work-day]");jsCheck("!document.querySelector('#work-month-calendar').open","Choosing a day closes calendar and keeps logs in reach");
  Thread.sleep(300);h.screenshot("work-compact-empty-1.7.7.png");
  click("[data-action=add-work-note]");jsCheck("document.querySelector('#work-note-date').value===document.querySelector('[data-work-day][aria-pressed=true]').dataset.workDay","Write-log date follows selected calendar date");click("#close-editor");
  click("[data-record-tab=learning]");jsCheck("document.querySelector('[data-action=record-tools]')!==null&&document.querySelector('[data-action=user-guide]')!==null","Backup and manual are accessible outside work tab");
  click("[data-action=record-tools]");jsCheck("document.querySelector('#record-export')!==null&&document.querySelector('#record-import')!==null","Native backup entry opens correctly");click("#close-editor");
  click("[data-record-tab=work]");click("[data-range=today]");Thread.sleep(300);h.screenshot("work-compact-logs-1.7.7.png");
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
  h=new MainHost(this);h.open();holdAutomation();
  if(phase.equals("seed"))seedLayout();else{if(phase.equals("flow"))checkUpgrade();if(!phase.equals("layout"))calendar();compactLayout();}
  result.putString("stream","PASS "+passed+" compact work/calendar/native checks; phase="+phase+"; synthetic data only\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable ex){result.putString("stream","FAIL after "+passed+": "+ex+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
