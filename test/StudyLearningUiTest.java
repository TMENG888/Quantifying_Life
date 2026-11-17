package com.insight.quantlife.tests;
import android.app.Activity;import android.os.*;import org.json.*;
/** Real Android editor and timer integration, deterministic link/LLM replies, no paid calls. */
public final class StudyLearningUiTest extends DocumentEditorTest {
 @Override public void onStart(){Bundle out=new Bundle();String timerId=null,draftId=null;try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");model();
  h.js("window.__linkBridge=LifeNative;LifeNative={call(action,payload,id){if(action==='readLinks'){const p=JSON.parse(payload);setTimeout(()=>nativeReply({id,data:[{url:p.urls[0],status:'metadata',title:'知识库的长期积累与检索',platform:'飞书',contentType:'article',description:'知识维护与索引的配合'}]}),5);return;}__linkBridge.call(action,payload,id);}}");
  if(!((JSONObject)h.call("studyTimer",new JSONObject())).isNull("active"))throw new Exception("Existing stopwatch");
  until("typeof document.querySelector('[data-page=records]').onclick==='function'");click("[data-page=records]");until("!!document.querySelector('[data-study-open]')&&!document.querySelector('[data-study-open]').disabled");click("[data-study-open]");until("!!document.querySelector('#study-finish')");
  timerId=((JSONObject)h.call("studyTimer",new JSONObject())).getJSONObject("active").getString("id");draftId="study-"+timerId;int before=records().length();
  h.js("document.querySelector('#study-title').value='索引保留原文依据 https://example.com/knowledge';document.querySelector('#study-title').dispatchEvent(new Event('input'))");
  click("#study-finish");until("!!document.querySelector('#doc-title')");check(records().length()==before,"Link recognition does not create a standalone timing record");check(h.js("document.querySelector('#doc-title').value==='知识库的长期积累与检索'").equals("true"),"Reliable source title takes priority over model wording");
  check(h.js("DocumentRich.serialize(document.querySelector('#doc-body')).includes('索引保留原文依据')").equals("true"),"User input is preserved as note, source summary is excluded");
  click("#doc-info");check(h.js("document.querySelector('[data-meta=url]').value==='https://example.com/knowledge'&&document.querySelector('[data-meta=platform]').value==='飞书'").equals("true"),"Cleaned URL and source platform reach native record information");
  check(h.js("document.querySelector('[data-meta=tags]').value.includes('合成测试')&&!!document.querySelector('#doc-meta .study-record-time')").equals("true"),"AI tags and timer interval appear in original metadata drawer");h.screenshot("study-link-information.png");click("#doc-meta-done");close();
  JSONArray drafts=(JSONArray)h.call("getDocumentDraft",new JSONObject());JSONObject found=null;for(int i=0;i<drafts.length();i++)if(drafts.getJSONObject(i).getJSONObject("record").getString("id").equals(draftId))found=drafts.getJSONObject(i);check(found!=null&&found.getJSONObject("record").getDouble("durationMinutes")>=0,"Unsubmitted enriched draft persists locally");
  out.putString("stream","PASS StudyLearningUiTest: "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable error){try{if(h!=null)h.screenshot("study-link-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL StudyLearningUiTest after "+passed+": "+error+"\n"+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,out);}
 finally{if(h!=null)try{if(draftId!=null)h.call("clearDocumentDraft",new JSONObject().put("id",draftId));JSONObject active=((JSONObject)h.call("studyTimer",new JSONObject())).optJSONObject("active");if(active!=null&&active.optString("id").equals(timerId))h.call("studyTimer",new JSONObject().put("command","discard").put("id",timerId));}catch(Exception ignored){}}
 }
}
