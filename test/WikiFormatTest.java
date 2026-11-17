package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;
/** Disposable emulator only: real toolbar taps, native persistence, semantic domain attribution. */
public final class WikiFormatTest extends DocumentEditorTest {
 @Override void input(String title,String note)throws Exception{h.js("document.querySelector('#doc-title').value="+JSONObject.quote(title)+";document.querySelector('#doc-body').innerHTML='';document.querySelector('#doc-body').append(DocumentRich.render("+JSONObject.quote(note)+",()=>''));document.querySelector('#doc-body').dispatchEvent(new Event('input'))");}
 void selectText(String needle)throws Exception{h.js("(()=>{const b=document.querySelector('#doc-body'),w=document.createTreeWalker(b,NodeFilter.SHOW_TEXT);let n;while(n=w.nextNode()){const at=n.textContent.indexOf("+JSONObject.quote(needle)+");if(at>=0){const r=document.createRange();r.setStart(n,at);r.setEnd(n,at+"+needle.length()+");b.focus();getSelection().removeAllRanges();getSelection().addRange(r);document.dispatchEvent(new Event('selectionchange'));return true;}}throw Error('selection missing')})()");Thread.sleep(300);}
 String source()throws Exception{return new JSONArray("["+h.js("DocumentRich.serialize(document.querySelector('#doc-body'))")+"]").getString(0);}
 boolean queued(String id)throws Exception{try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("quantlife.db").getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);android.database.Cursor c=db.rawQuery("SELECT 1 FROM wiki_queue WHERE id=?",new String[]{id})){return c.moveToFirst();}}
 void ack(JSONObject r)throws Exception{h.call("ackWikiQueue",new JSONObject().put("jobs",new JSONArray().put(new JSONObject().put("id",r.getString("id")).put("version",r.getLong("updatedAt")).put("operation","upsert"))));}
 @Override public void onStart(){Bundle out=new Bundle();try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Requires disposable emulator");
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  open("work_note");input("格式回归","专题二的Qwen打标\n\n今天下午，对比了Qwen与Codex的打标数据。\n\n其他段落保持原样。");
  selectText("专题二的Qwen打标");h.tap("[data-doc-command=heading]",false);Thread.sleep(250);
  check(h.js("!!document.querySelector('#doc-body h2')").equals("true"),"Real touch applies heading to selected paragraph");
  check(source().contains("## 专题二的Qwen打标"),"Heading serializes to Markdown");
  selectText("对比了Qwen与Codex");h.tap("[data-doc-command=bold]",false);Thread.sleep(250);
  check(h.js("document.querySelector('#doc-body b,#doc-body strong')?.textContent").equals(JSONObject.quote("对比了Qwen与Codex")),"Bold targets exact Chinese selection");
  String synthesis=h.js("getComputedStyle(document.querySelector('#doc-body strong,#doc-body b')).fontSynthesis");
  if(phase.equals("baseline")){check(synthesis.equals(JSONObject.quote("none")),"Reproduced old theme suppressing synthetic Chinese bold");h.screenshot("format-before.png");close();out.putString("stream","PASS baseline: "+passed+" checks\n");finish(Activity.RESULT_OK,out);return;}
  check(!synthesis.equals(JSONObject.quote("none")),"Chinese glyphs may synthesize a visible bold face");
  check(h.js("parseFloat(getComputedStyle(document.querySelector('#doc-body h2')).fontSize)>parseFloat(getComputedStyle(document.querySelector('#doc-body')).fontSize)").equals("true"),"Heading is visibly larger than body");
  check(source().contains("**对比了Qwen与Codex**")&&source().contains("其他段落保持原样。"),"Markdown contains exact bold and untouched remainder");
  h.screenshot("format-edit-after.png");String expected=source();click("#doc-save");until("document.querySelector('#doc-mode').textContent==='编辑'&&!document.querySelector('#doc-save').disabled");
  check(h.js("!!document.querySelector('#doc-flow strong')&&!!document.querySelector('#doc-flow h3')").equals("true"),"Saved reader renders heading and bold");
  check(h.js("parseInt(getComputedStyle(document.querySelector('#doc-flow strong')).fontWeight)>=700&&getComputedStyle(document.querySelector('#doc-flow strong')).fontSynthesis!=='none'").equals("true"),"Reader also enables visible Chinese bold");
  h.screenshot("format-reader-after.png");close();
  JSONArray rows=records();String savedId=null;for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("title").equals("格式回归")&&rows.getJSONObject(i).optString("note").equals(expected))savedId=rows.getJSONObject(i).getString("id");
  check(savedId!=null,"Native database retained heading and bold Markdown");
  JSONObject r=new JSONObject().put("id","wiki-domain-qa").put("type","learning").put("date","2026-10-27").put("title","数据治理的质量分析").put("note","先验证企业数据的完整性，再分析业务质量。").put("tags",new JSONArray().put("历史")).put("durationMinutes",35);
  h.call("getWikiQueue",new JSONObject());
  h.call("saveRecord",new JSONObject().put("record",r));r=record("wiki-domain-qa");
  ack(r);check(!queued(r.getString("id")),"Simulated historical classification job is missing");h.call("getWikiQueue",new JSONObject());check(queued(r.getString("id")),"Unclassified history automatically re-enters durable queue");
  JSONArray assigned=(JSONArray)h.call("saveWikiTopics",new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id",r.getString("id")).put("version",r.getLong("updatedAt")).put("topics",new JSONArray().put("数据质量治理")).put("domains",new JSONArray().put("商业与管理")))));
  JSONObject classified=assigned.getJSONObject(0);check(classified.getInt("wikiClassificationVersion")==3&&classified.getLong("wikiDomainVersion")==r.getLong("updatedAt"),"Native Wiki classification carries matching source version");
  ack(classified);h.call("getWikiQueue",new JSONObject());check(!queued(r.getString("id")),"Finished fresh classification is not repeatedly re-enqueued");
  check(classified.getString("note").equals(r.getString("note"))&&classified.getJSONArray("tags").toString().equals(r.getJSONArray("tags").toString()),"AI classification preserves original note and handwritten tags");
  h.js("window.qaWikiRecord="+classified+";window.qaStats=TopicStats.summarize([qaWikiRecord],{range:'all'});window.qaRender=()=>{document.querySelector('#main').innerHTML=DomainDashboard.page(qaStats,'minutes');DomainDashboard.bind()};DomainDashboard.init({state:{records:[qaWikiRecord],recordTab:'learning'},esc:v=>String(v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/\"/g,'&quot;'),minutes:n=>n+' 分钟',render:qaRender});qaRender()");
  check(h.js("document.querySelector('[data-domain]').dataset.domain==='商业与管理'&&!document.querySelector('[data-domain=人文与阅读]')").equals("true"),"Wiki semantic domain wins over unrelated manual source tag");
  check(h.js("!!document.querySelector('.domain-chart')&&!document.querySelector('[data-topic-key]')&&!document.querySelector('#main').textContent.includes('主题细目')").equals("true"),"Chart displays only coarse domains, not fine topic ranking");
  check(h.js("document.querySelector('.domain-coverage').textContent.includes('1 / 1')").equals("true"),"Fresh Wiki coverage is explicit");
  h.screenshot("wiki-domain-chart.png");click("[data-domain]");check(h.js("document.querySelector('.domain-detail [data-record]').dataset.record==='wiki-domain-qa'").equals("true"),"Domain drilldown reaches exact source record");
  boolean rejected=false;try{h.call("saveWikiTopics",new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id",r.getString("id")).put("version",r.getLong("updatedAt")).put("topics",new JSONArray().put("数据治理")).put("domains",new JSONArray().put("任意伪造领域")))));}catch(Exception ex){rejected=true;}check(rejected,"Native store rejects out-of-taxonomy model domains");
  classified.put("note",classified.getString("note")+"新增业务复盘。");h.call("saveRecord",new JSONObject().put("record",classified).put("allowDuplicate",true));JSONObject edited=record(r.getString("id"));ack(edited);h.call("getWikiQueue",new JSONObject());check(queued(r.getString("id"))&&edited.getLong("updatedAt")!=edited.getLong("wikiDomainVersion"),"Edited notes with stale domains are automatically queued again");
  h.call("deleteRecord",new JSONObject().put("id","wiki-domain-qa"));h.call("deleteRecord",new JSONObject().put("id",savedId));
  out.putString("stream","PASS WikiFormatTest: "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable ex){try{if(h!=null)h.screenshot("wiki-format-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL WikiFormatTest after "+passed+": "+ex+"\n"+android.util.Log.getStackTraceString(ex));finish(Activity.RESULT_CANCELED,out);}}
}
