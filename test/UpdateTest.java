package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import org.json.JSONObject;import org.json.JSONArray;
public class UpdateTest extends Instrumentation{
 private int passed;private MainHost host;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean value,String label)throws Exception{if(!value)throw new Exception(label);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 @Override public void onStart(){Bundle result=new Bundle();try{
  host=new MainHost(this);host.open();JSONObject initial=(JSONObject)host.call("init",new JSONObject());
  check(initial.toString().contains("upgrade-survivor")&&initial.toString().contains("升级前笔记必须保留"),"1.0 to 1.1 migration preserves original learning notes");
  check(initial.toString().contains("upgrade-work"),"Migration preserves work shifts");
  check(initial.getJSONObject("settings").getJSONObject("profiles").getJSONObject("custom").getBoolean("keyConfigured"),"Migration preserves encrypted API credential");
  check(initial.getJSONArray("wikiPages").length()==0,"New Wiki table initialized without clearing records");
  host.js("document.querySelector('#quick-add').click()");
  check(host.js("document.querySelector('#record-platform').getAttribute('list')").equals("null")&&host.js("document.querySelectorAll('datalist').length").equals("0"),"No native datalist remains in learning editor");
  host.js("document.querySelector('#record-platform').focus()");host.tap("#record-platform",false);Thread.sleep(350);
  check(host.js("document.querySelector('#platform-choices').hidden").equals("true"),"Platform input does not automatically open suggestions");host.screenshot("platform-input-fixed.png");
  host.js("document.querySelector('#toggle-platforms').click()");check(host.js("!document.querySelector('#platform-choices').hidden").equals("true"),"Platform choices open only on explicit button");
  host.js("document.querySelector('[data-platform-choice=知乎]').click()");check(host.js("document.querySelector('#record-platform').value").contains("知乎")&&host.js("document.querySelector('#platform-choices').hidden").equals("true"),"Selecting platform closes choices and restores editable input");
  host.js("document.querySelector('#toggle-platforms').click();window.appBack()");check(host.js("document.querySelector('#platform-choices').hidden&&document.querySelector('#editor').open").equals("true"),"Back dismisses inline platform choices without closing editor");
  host.js("document.querySelector('#toggle-platforms').click();document.querySelector('#record-platform').value='个人学习网站';document.querySelector('#record-platform').dispatchEvent(new Event('input'))");check(host.js("document.querySelector('#platform-choices').hidden").equals("true"),"Typing custom platform dismisses choices");
  host.js("document.querySelector('#record-title').value='JEV模型应用独立案例';document.querySelector('#record-duration').value='30';document.querySelector('#record-tags').value='JEV';document.querySelector('#record-note').value='独立实现JEV案例并复盘不足';document.querySelector('#mastery-level').value='proficient';document.querySelector('#evidence-type').value='practice';document.querySelector('#evidence-note').value='独立完成应用任务';document.querySelector('#record-form').dispatchEvent(new Event('submit',{cancelable:true}))");check(host.waitText("记录已保存到本地"),"Learning record saves custom platform and mastery evidence");
  JSONObject saved=(JSONObject)host.call("init",new JSONObject());check(saved.toString().contains("独立完成应用任务")&&saved.toString().contains("个人学习网站"),"Mastery evidence reaches SQLite");
  host.js("document.querySelector('[data-page=chat]').click();document.querySelector('#chat-input').value='上个月6号学习了什么，学多久，工作多久？';document.querySelector('[data-action=send]').click()");check(host.waitText("历史学习 60 分钟；净工作 480 分钟"),"Natural-language previous-month date passes real HTTP planning and exact local totals");
  check(host.js("document.querySelectorAll('.citation').length>=2").equals("true"),"Historical date answer cites original learning and work records");
  host.js("document.querySelector('#chat-input').value='分析我JEV模型应用的熟练程度，并整理知识Wiki';document.querySelector('[data-action=send]').click()");check(host.waitText("Wiki 页数 1"),"Topic chat compiles LLM Wiki and uses it for grounded answer");
  JSONObject wikiState=(JSONObject)host.call("init",new JSONObject());check(wikiState.getJSONArray("wikiPages").length()==1,"Compiled LLM Wiki persists in native SQLite");
  JSONObject page=wikiState.getJSONArray("wikiPages").getJSONObject(0);check(page.getJSONArray("concepts").getJSONObject(0).getJSONArray("sourceIds").length()>0,"Wiki concept has valid source record links");
  check(host.waitText("不能当作普遍的熟练门槛"),"Mastery analysis separates personal evidence from universal threshold");
  host.js("document.querySelector('[data-action=open-wiki]').click()");check(host.waitText("本地 LLM Wiki"),"User can inspect saved Wiki library");
  host.js("document.querySelector('[data-wiki-page]').click()");check(host.waitText("应用实践"),"Topic knowledge page and concepts render");host.screenshot("wiki-topic.png");
  host.js("document.querySelector('[data-wiki-source]').click()");check(host.waitText("学习详情"),"Wiki source opens original learning note");host.js("window.appBack()");
  JSONObject original=null;for(int i=0;i<wikiState.getJSONArray("records").length();i++){JSONObject r=wikiState.getJSONArray("records").getJSONObject(i);if(r.getString("id").equals("upgrade-survivor"))original=r;}
  original.put("note","JEV新笔记：增加边界条件");host.call("saveRecord",new JSONObject().put("record",original));
  host.test.runOnMainSync(()->host.web.reload());check(host.waitText("今天的投入"),"Restart loads migrated record and Wiki data");
  host.js("document.querySelector('[data-page=chat]').click();document.querySelector('[data-action=open-wiki]').click()");check(host.waitText("待更新"),"Editing source marks old Wiki stale");host.js("window.appBack()");
  boolean invalid=false;JSONObject bad=new JSONObject(page.toString());bad.put("id","fake-wiki").put("sourceIds",new JSONArray().put("missing-source"));try{host.call("saveWiki",new JSONObject().put("page",bad));}catch(Exception e){invalid=true;}check(invalid,"Native Wiki store rejects fabricated or changed sources");
  host.js("document.querySelector('#chat-input').value='总结JEV模型应用笔记';document.querySelector('[data-action=send]').click()");check(host.waitText("Wiki 页数 1"),"Subsequent topic question updates stale Wiki automatically");Thread.sleep(600);
  JSONObject updated=(JSONObject)host.call("init",new JSONObject());check(updated.getJSONArray("wikiPages").getJSONObject(0).getLong("updatedAt")>page.getLong("updatedAt"),"Updated Wiki replaces old persisted page");
  host.screenshot("wiki-chat.png");
  for(int i=0;i<21;i++){JSONObject r=new JSONObject().put("id","batch-"+i).put("type","learning").put("date","2026-09-07").put("title","JEV批次学习 "+i).put("platform","知乎").put("contentType","article").put("note","JEV知识案例 "+i).put("durationMinutes",15);host.call("saveRecord",new JSONObject().put("record",r));}
  host.test.runOnMainSync(()->host.web.reload());check(host.waitText("今天的投入"),"Multiple-batch learning sources persisted");
  host.js("document.querySelector('[data-page=chat]').click();document.querySelector('#chat-input').value='整理JEV知识Wiki';document.querySelector('[data-action=send]').click()");check(host.waitText("JEV 学习累计 23 条"),"Multi-batch compilation and merge use exact all-source totals");Thread.sleep(600);
  JSONObject batchState=(JSONObject)host.call("init",new JSONObject());JSONObject batchPage=batchState.getJSONArray("wikiPages").getJSONObject(0);check(batchPage.getInt("compiledFrom")==23&&batchPage.getJSONArray("sourceIds").length()==23,"Merged Wiki persists all 23 reviewed sources");
  JSONObject indexedOnly=new JSONObject(batchPage.toString());indexedOnly.put("sourceIds",new JSONArray().put("batch-20"));boolean rejected=false;try{host.call("saveWiki",new JSONObject().put("page",indexedOnly));}catch(Exception e){rejected=true;}check(rejected,"Native Wiki concepts cannot cite indexed but uncompiled sources");
  result.putString("stream","\nPASS: "+passed+" update and Wiki Android checks.\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){String body="";try{body=host.js("document.body.innerText+'\\nTOAST:'+document.querySelector('#toast').textContent");}catch(Exception ignored){}result.putString("stream","FAIL after "+passed+": "+e+"\n"+body+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
