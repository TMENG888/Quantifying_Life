package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;
/** Disposable emulator only. Real navigation/taps, synthetic local content, no paid model requests. */
public final class EverydayUiTest extends DocumentEditorTest {
 java.util.List<String> ids=new java.util.ArrayList<>();
 void fits(String screen)throws Exception {
  check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),screen+": no horizontal overflow");
  check(h.js("[...document.querySelectorAll('.bottom-nav button')].every(x=>x.getBoundingClientRect().height>=48)").equals("true"),screen+": navigation touch targets");
 }
 void shot(String screen)throws Exception { fits(screen);h.screenshot("everyday-"+screen+".png"); }
 void put(String id,String type,String title,String note,int minutes,String...tags)throws Exception{
  JSONObject r=new JSONObject().put("id",id).put("type",type).put("date","2026-10-27").put("title",title).put("note",note).put("durationMinutes",minutes).put("tags",new JSONArray(tags));
  if(type.equals("idea"))r.put("occurredAt","2026-10-27T10:00:00");
  h.call("saveRecord",new JSONObject().put("record",r).put("allowDuplicate",true));ids.add(id);
 }
 @Override public void onStart(){Bundle out=new Bundle();try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Requires disposable emulator");
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  put("everyday-qa-learning","learning","知识如何持续积累","## 从阅读到理解\n\n**知识索引**帮助找回原文，长期整理帮助连接不同主题。\n\n今天完成一次检索方案对照。",45,"RAG","数据治理");
  put("everyday-qa-reading","learning","把读过的书变成自己的思考","记录重要观点，保留证据，再写下自己的理解。",30,"阅读");
  put("everyday-qa-work","work_note","完成笔记导入流程","对照文档检查图片、日期与代码块，完成问题复盘。",0,"产品设计");
  put("everyday-qa-idea","idea","让记录自然融入每一天","从一次阅读、一段工作或一个念头开始。",0,"产品设计");
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  check(h.js("getComputedStyle(document.body).backgroundColor==='rgb(237, 237, 237)'").equals("true"),"New gray shell applied");
  check(h.js("getComputedStyle(document.querySelector('.day-ledger')).backgroundColor==='rgb(255, 255, 255)'").equals("true"),"Daily ledger is white rather than a color block");
  shot("today");
  h.tap("[data-page=records]",false);Thread.sleep(300);check(h.js("!!document.querySelector('#learning-search')").equals("true"),"Native touch navigates to learning");shot("learning");
  h.tap("#topic-entry",false);Thread.sleep(300);check(h.js("!!document.querySelector('.domain-ring-figure')").equals("true"),"Ring is default");
  click("[data-topic-range=all]");shot("ring");
  h.tap("[data-domain-chart=bars]",false);Thread.sleep(200);check(h.js("!!document.querySelector('.domain-chart.is-bars')").equals("true"),"Native touch switches to comparison");
  shot("bars");click("[data-domain-chart=ring]");click("[data-domain]");
  check(h.js("!!document.querySelector('.domain-detail [data-record]')").equals("true"),"Ring drilldown retains source records");
  click("#topic-entry");h.js("document.querySelector('#learning-search').value='知识如何持续积累';document.querySelector('#learning-search').dispatchEvent(new Event('input'))");click("[data-record='everyday-qa-learning']");until("!!document.querySelector('#doc-flow')");
  check(h.js("!!document.querySelector('#doc-flow strong')").equals("true"),"Reader preserves bold");h.screenshot("everyday-reader.png");close();
  click("[data-record-tab=work]");shot("work");click("[data-record-tab=thoughts]");shot("essays");
  click("[data-page=tracks]");until("!!document.querySelector('#track-map')");shot("tracks");
  click("[data-page=chat]");until("!!document.querySelector('#chat-input')");shot("chat");
  h.tap("[data-action=chat-history]",false);Thread.sleep(200);check(h.js("document.querySelector('#chat-history-drawer').open").equals("true"),"History remains accessible");h.screenshot("everyday-history.png");
  click("#drawer-model");until("!!document.querySelector('.model-card')");shot("models");
  click("[data-page=today]");click("[data-go=meditation]");until("!!document.querySelector('#meditation-root')");h.screenshot("everyday-meditation.png");
  out.putString("stream","PASS EverydayUiTest: "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable ex){try{if(h!=null)h.screenshot("everyday-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL EverydayUiTest after "+passed+": "+ex+"\n"+android.util.Log.getStackTraceString(ex));finish(Activity.RESULT_CANCELED,out);
 }finally{if(h!=null)for(String id:ids)try{h.call("deleteRecord",new JSONObject().put("id",id));}catch(Exception ignored){}}}
}
