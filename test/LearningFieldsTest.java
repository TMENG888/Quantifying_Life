package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;import com.insight.quantlife.*;
/** Offline synthetic responses, actual Android WebView + SQLite; synthetic equivalents of the reported input. */
public class LearningFieldsTest extends LearningIntegrityTest {
 final String realUrl="https://weixin.qq.com/sph/SyntheticFields";
 final String prose="学习了这个视频内容，有关rea，为最近的一个逆向Web，app的开源项目，当中封装了一些示例工具，skill指导，以及过程的记录机制，目前使用它的核心目的是理解合成样例，然后复现出样例的功能，因为其是通过公开测试的方式实现，因此可以说在测试中，可以分析一些公开示例的实现机制";
 void fieldsMock(boolean modelRecord)throws Exception{
  chatMock();
  h.js("window.__fieldsOrig=LifeNative.call;LifeNative.call=function(action,payload,rid){if(action==='readLinks'){setTimeout(()=>nativeReply({id:rid,data:[{url:"+JSONObject.quote(realUrl)+",status:'metadata',title:'视频号',platform:'微信视频号',contentType:'video'}]}),15);return;}if(action==='ask'&&JSON.parse(payload).tool.name==='prepare_life_records'){const r={type:'learning',date:LifeDomain.localDate(),url:"+JSONObject.quote(realUrl)+",title:'视频号',platform:'微信视频号',contentType:'video',durationMinutes:15,note:"+JSONObject.quote(prose+"，学了15分钟")+"};setTimeout(()=>nativeReply({id:rid,data:{text:JSON.stringify({answer:'已准备待确认记录',records:"+(modelRecord?"[r]":"[]")+",citations:[]}),model:'synthetic-model'}}),15);return;}__fieldsOrig(action,payload,rid)}");
 }
 JSONObject exactSaved()throws Exception{JSONArray rows=init().getJSONArray("records");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("url").equals(realUrl))return rows.getJSONObject(i);return null;}
 void removeSynthetic()throws Exception{JSONObject row=exactSaved();if(row!=null)h.call("deleteRecord",new JSONObject().put("id",row.getString("id")));}
 void fields(boolean modelRecord)throws Exception{
  removeSynthetic();h.call("newChat",new JSONObject());reload();holdAutomation();fieldsMock(modelRecord);click("[data-page=chat]");
  String input=realUrl+"\n"+prose+"，学了15分钟";linkText(input);
  jsCheck("document.querySelectorAll('[data-save-candidate]').length===1","Same-turn input produces one candidate: modelRecord="+modelRecord);
  jsCheck("document.querySelector('.candidate').innerText.includes('15分钟')&&document.querySelector('.candidate').innerText.includes('非原文标题')","Candidate separately shows duration and generated-title provenance");
  check(exactSaved()==null,"Candidate has not prematurely written database");
  h.js("ChatUI.bottom()");Thread.sleep(250);h.screenshot("learning-fields-candidate-1.7.10-"+modelRecord+".png");
  click("[data-save-candidate]");until("!document.querySelector('[data-save-candidate]')||document.querySelector('#confirm-dialog').open");if(h.js("document.querySelector('#confirm-dialog').open").equals("true"))click("#confirm-ok");until("!document.querySelector('[data-save-candidate]')");
  JSONObject r=exactSaved();check(r!=null&&r.getString("note").equals(prose),"SQLite note equals full supplied knowledge, without tracking suffix");
  check(r.getInt("durationMinutes")==15,"SQLite stores 15 minutes in duration field");
  check(r.getString("titleOrigin").equals("note")&&r.getString("title").contains("rea")&&!r.getString("title").equals("视频号"),"SQLite title derived from note, not generic platform title");
  check(chat().getJSONArray("messages").toString().contains("学了15分钟"),"Original chat input is preserved for traceability");
  click("[data-page=records]");click("[data-record-tab=learning]");click("[data-record='"+r.getString("id")+"']");
  jsCheck("document.querySelector('.note-full').innerText==="+JSONObject.quote(prose),"Actual learning detail renders clean, complete note");
  jsCheck("document.querySelector('#editor').innerText.includes('15分钟')&&document.querySelector('#editor').innerText.includes('非原文标题')","Actual detail displays duration outside note and derived-title label");
  h.screenshot("learning-fields-detail-1.7.10-"+modelRecord+".png");click("#close-editor");
  reload();holdAutomation();check(exactSaved().getString("note").equals(prose)&&exactSaved().getInt("durationMinutes")==15,"Fields persist after application reload");
 }
 void titles()throws Exception{
  for(String t:new String[]{"视频号","微信视频号","首页","登录","打开 App","微信公众号"})check(!LinkReader.usableTitle(t,"微信视频号"),"Native rejects generic title "+t);
  check(LinkReader.usableTitle("视频号里的REA工具介绍","微信视频号"),"Native accepts specific title containing platform word");
  LinkReader r=new LinkReader((u,d)->new LinkReader.Page(200,"","text/html","<title>视频号</title>".getBytes("UTF-8")),null);
  check(r.resolve(realUrl,null).getString("status").equals("unavailable"),"Direct generic metadata is not trusted");
  r=new LinkReader((u,d)->new LinkReader.Page(403,"","",new byte[0]),(q,k)->new JSONObject().put("results",new JSONArray().put(new JSONObject().put("url",realUrl).put("title","视频号"))));
  JSONObject config=new JSONObject().put("provider","tavily").put("enabled",true).put("apiKey","SYNTHETIC-ONLY");
  check(r.resolve(realUrl,config).getString("status").equals("unavailable"),"Same-link search generic metadata is not trusted");
  ArticleReadTest fixture=new ArticleReadTest();
  ExaMcp exa=new ExaMcp((q,s,p,d)->{
   String method=q.getString("method");if(method.equals("initialize"))return fixture.reply(q,new JSONObject().put("protocolVersion","2024-11-05"));
   if(method.equals("notifications/initialized"))return new ExaMcp.Reply(202,"","","");
   if(method.equals("tools/list"))return fixture.reply(q,new JSONObject().put("tools",new JSONArray().put(new JSONObject().put("name","web_search_exa").put("inputSchema",fixture.searchSchema())).put(new JSONObject().put("name","web_fetch_exa").put("inputSchema",fixture.fetchSchema()))));
   String tool=q.getJSONObject("params").getString("name");
   return fixture.reply(q,fixture.text(tool.equals("web_fetch_exa")?"# 视频号\nURL: "+realUrl+"\n\n"+fixture.prose():"Title: 视频号\nURL: "+realUrl+"\nHighlights:\n搜索摘要"));
  });
  r=new LinkReader((u,d)->new LinkReader.Page(403,"","",new byte[0]),null,exa);
  check(r.resolve(realUrl,null).getString("status").equals("unavailable"),"Exa generic fetch and search titles are not trusted");
 }
 @Override public void onStart(){Bundle out=new Bundle();try{h=new MainHost(this);h.open();holdAutomation();if(phase.equals("seed"))seedIntegrity();else{
  if(phase.equals("verify"))upgradeIntegrity();titles();
  h.call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",model("SYNTHETIC-ONLY"))));
  fields(true);fields(false);
 }out.putString("stream","PASS "+passed+" learning fields/native/upgrade/detail checks; fully mocked network\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){try{h.screenshot("learning-fields-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
