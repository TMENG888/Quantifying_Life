package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;import java.util.*;
/** Local synthetic fixtures, production bridge + WebView. Never calls a model. */
public final class EvidenceDashboardTest extends DocumentEditorTest {
 final String prefix="evidence-fixture-";
 void clean()throws Exception{for(String s:new String[]{"a","b","c","w","j","e"})h.call("deleteRecord",new JSONObject().put("id",prefix+s));}
 void seed(String id,String type,String date,int minutes,String tag)throws Exception{
  JSONObject r=new JSONObject().put("id",prefix+id).put("type",type).put("date",date).put("title","合成记录 "+id).put("note",type.equals("work")?"":"合成正文，不能被统计改写").put("durationMinutes",minutes).put("tags",new JSONArray().put(tag));
  if(type.equals("experience"))r.put("occurredAt",date+"T10:00:00");
  if(type.equals("work"))r.put("workStart","2000-02-28T23:00:00").put("workEnd","2000-02-29T02:00:00").put("breaks",new JSONArray("[{\"start\":\"2000-02-29T00:30:00\",\"end\":\"2000-02-29T01:00:00\"}]"));
  h.call("saveRecord",new JSONObject().put("record",r).put("allowDuplicate",true));
 }
 void fits(String mode)throws Exception{
  check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),mode+": no horizontal overflow");
  check(h.js("[...document.querySelectorAll('.evidence-card button,.evidence-card select')].every(x=>{const r=x.getBoundingClientRect();return r.width===0||(r.left>=-1&&r.right<=innerWidth+1&&r.height>=47)})").equals("true"),mode+": chart controls fit and remain touchable");
  check(h.js("[...document.querySelectorAll('.evidence-days small')].every(x=>x.scrollWidth<=x.clientWidth+1)").equals("true"),mode+": calendar numbers remain inside cells");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean success=false;try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");
  h=new MainHost(this);h.open();until("typeof document.querySelector('[data-page=records]').onclick==='function'");h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");clean();
  seed("a","learning","2000-02-28",25,"RAG");seed("b","learning","2000-02-29",50,"企业管理");seed("c","learning","2000-01-02",0,"RAG");seed("w","work","2000-02-28",999,"项目执行");seed("j","work_note","2000-02-29",0,"产品开发");seed("e","experience","2000-02-29",0,"复盘");
  String before=records().toString();runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();until("typeof document.querySelector('[data-page=records]').onclick==='function'");h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  click("[data-page=records]");click("#topic-entry");click("[data-topic-range=custom]");h.js("document.querySelector('#topic-start').value='2000-01-01';document.querySelector('#topic-start').dispatchEvent(new Event('input'));document.querySelector('#topic-end').value='2000-02-29';document.querySelector('#topic-end').dispatchEvent(new Event('input'));document.querySelector('#topic-dates').requestSubmit()");
  h.js("document.querySelector('#calendar-metric').value='minutes';document.querySelector('#calendar-metric').dispatchEvent(new Event('change'))");
  check(h.js("!document.querySelector('.evidence-duration')&&!document.querySelector('.evidence-dot-row')&&!document.querySelector('.quant-card,.information-card')").equals("true"),"Independent modules removed; calendar retains evidence");
  check(h.js("document.querySelector('#evidence-month').value==='2000-02'&&document.querySelectorAll('.evidence-days button').length===29").equals("true"),"Leap month has 29 days and starts at newest data month");
  check(h.js("[...document.querySelectorAll('.domain-method,.evidence-method')].every(x=>!x.open)").equals("true"),"Method notes stay folded by default");fits(phase);h.screenshot("evidence-learning-"+phase+".png");h.js("document.querySelector(\'.evidence-calendar\').scrollIntoView({block:\'start\'})");h.screenshot("evidence-calendar-"+phase+".png");
  h.js("document.querySelector('[data-evidence-day=\"2000-02-29\"]').scrollIntoView({block:'center'})");h.tap("[data-evidence-day='2000-02-29']",false);until("EvidenceDashboard.active()");
  check(h.js("document.querySelector('#evidence-day-detail').innerText.includes('50分钟')&&document.querySelectorAll('#evidence-day-detail [data-record]').length===1").equals("true"),"Real calendar tap opens exact day and duration");
  click("#evidence-day-detail [data-record]");until("!!document.querySelector('#doc-mode')");check(h.js("document.querySelector('#doc-flow').textContent.includes('合成正文')").equals("true"),"Day drilldown opens original reader");close();check(h.js("EvidenceDashboard.active()").equals("true"),"Closing reader retains the day detail sheet");click("#evidence-day-close");until("!EvidenceDashboard.active()");
  h.js("document.querySelector('#evidence-month').value='2000-01';document.querySelector('#evidence-month').dispatchEvent(new Event('change'))");check(h.js("document.querySelector('[data-evidence-day=\"2000-01-02\"] small').textContent==='未计时'").equals("true"),"Unknown time distinguished from zero learning");
  click("[data-record-tab=work]");check(h.js("document.querySelectorAll('[data-topic-sort]').length===0&&document.querySelectorAll('.domain-row').length===1&&document.querySelector('.domain-row').textContent.includes('产品与技术')").equals("true"),"Work topics come from journals, not time-only shifts");
  h.js("document.querySelector('#calendar-metric').value='hours';document.querySelector('#calendar-metric').dispatchEvent(new Event('change'))");
  click("[data-evidence-day='2000-02-29']");until("EvidenceDashboard.active()");check(h.js("parseFloat(document.querySelector('.evidence-day-total strong').textContent)===1.5&&document.querySelectorAll('#evidence-day-detail [data-record]').length===2").equals("true"),"Daily net hours clip midnight and rest; journal retained separately");fits("work "+phase);h.screenshot("evidence-work-"+phase+".png");click("#evidence-day-close");until("!EvidenceDashboard.active()");
  click("[data-record-tab=thoughts]");h.js("document.querySelector('#calendar-metric').value='count';document.querySelector('#calendar-metric').dispatchEvent(new Event('change'))");check(h.js("!document.querySelector('.evidence-duration')&&document.querySelector('[data-evidence-day=\"2000-02-29\"] small').textContent==='1'").equals("true"),"Essay calendar counts real records without invented hours");
  click("[data-record-tab=learning]");runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(400);fits("large "+phase);h.js("document.querySelector(\'.evidence-calendar\').scrollIntoView({block:\'start\'})");h.screenshot("evidence-large-"+phase+".png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
  check(records().toString().equals(before),"Chart navigation, drilldown and text scaling never alter saved records");
  success=true;
 }catch(Throwable ex){out.putString("stream","FAIL EvidenceDashboardTest after "+passed+": "+ex+"\n"+android.util.Log.getStackTraceString(ex));try{if(h!=null)h.screenshot("evidence-failure.png");}catch(Exception ignored){}
 }finally{try{if(h!=null){runOnMainSync(()->h.web.getSettings().setTextZoom(100));clean();}}catch(Exception ignored){}}
 if(success)out.putString("stream","PASS EvidenceDashboardTest "+phase+": "+passed+" checks\n");finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
 }
}
