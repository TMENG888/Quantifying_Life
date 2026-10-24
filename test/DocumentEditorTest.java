package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.util.Base64;
import com.insight.quantlife.*;import org.json.*;import java.io.*;
/** Isolated emulator, synthetic records and mocked AI only. Never uses a real key or location. */
public class DocumentEditorTest extends Instrumentation {
 MainHost h;int passed;String phase;
 @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"verify":b.getString("phase","verify");start();}
 void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception(label);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 void until(String expr)throws Exception{for(int i=0;i<180;i++){if(h.js(expr).equals("true"))return;Thread.sleep(100);}throw new Exception("UI timeout: "+expr+"; UI: "+h.js("document.body.innerText.slice(-2200)"));}
 void click(String selector)throws Exception{h.js("document.querySelector("+JSONObject.quote(selector)+").click()");Thread.sleep(120);}
 JSONObject state()throws Exception{JSONObject o=(JSONObject)h.call("init",new JSONObject());o.getJSONObject("settings").getJSONObject("wikiAutomation").remove("dailyUsed");return o;}
 JSONArray records()throws Exception{return (JSONArray)h.call("getRecords",new JSONObject());}
 JSONObject record(String id)throws Exception{JSONArray a=records();for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return a.getJSONObject(i);throw new Exception("Missing record: "+id);}
 void input(String title,String note)throws Exception{h.js("document.querySelector('#doc-title').value="+JSONObject.quote(title)+";document.querySelector('#doc-body').textContent="+JSONObject.quote(note)+";document.querySelector('#doc-body').dispatchEvent(new Event('input'))");}
 void model()throws Exception{
  // Each mocked run has an isolated conversation; accumulated prior QA history
  // must not trigger the unrelated real conversation-compaction tool.
  h.call("newChat",new JSONObject());
  h.call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",new JSONObject().put("id","custom").put("label","Synthetic model").put("baseUrl","https://127.0.0.1:9/v1").put("model","synthetic").put("protocol","chat").put("effort","off").put("apiKey","synthetic-not-a-real-key"))));
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{};window.__calls=[];window.__bad=false;window.__delay=5;window.__nextType='learning';window.__orig=LifeNative;LifeNative={call:function(action,payload,rid){if(action!=='ask'){__orig.call(action,payload,rid);return;}const p=JSON.parse(payload),n=p.tool.name;if(p.automaticWiki){setTimeout(()=>nativeReply({id:rid,error:'Synthetic background paused'}),5);return;}__calls.push(p);let out;if(n==='complete_record_metadata')out={title:'AI生成的主题标题',tags:['合成测试','文档']};else if(n==='plan_record_layout'){const us=JSON.parse(p.messages[p.messages.length-1].content).units;out={sections:__bad?[]:[{heading:[],blocks:us.map(u=>({ids:[u.id],style:u.protected?'preserve':'paragraph',emphasis:[]}))}]};}else if(n==='plan_record_search')out={intent:'record',topic:'',keywords:[],dateStart:'',dateEnd:'',recordTypes:[]};else out={answer:'待确认',records:[{type:__nextType,date:LifeDomain.localDate(),occurredAt:LifeDomain.localTime(),title:'',note:'聊天正文：今天完成了一个项目案例。',tags:[],durationMinutes:0}],citations:[]};setTimeout(()=>nativeReply({id:rid,data:{text:JSON.stringify(out),model:'synthetic-offline-model'}}),__delay);}};");
 }
 void saveUI()throws Exception{click("#doc-save");if(h.js("document.querySelector('#confirm-dialog').open").equals("true"))click("#confirm-ok");until("document.querySelector('#editor').open && !document.querySelector('#doc-save').disabled && document.querySelector('#doc-mode').textContent==='编辑'");check(h.js("!document.querySelector('#doc-read').hidden").equals("true"),"Save remains in reader until explicit back");close();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");}
 void open(String type)throws Exception{h.js("RecordEditor.open(null,null,"+JSONObject.quote(type)+")");until("!!document.querySelector('#doc-body')");}
 void close()throws Exception{h.js("RecordEditor.close()");until("!document.querySelector('#editor').open");}
 String image()throws Exception{Bitmap bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xffc8dfb6);Paint p=new Paint();p.setColor(0xff216a54);p.setTextSize(30);canvas.drawText("Synthetic image",20,100,p);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return "data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);}
 @Override public void onStart(){Bundle result=new Bundle();try{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  if(phase.equals("seed")){
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.8"),"Seed actual previously delivered signed APK");
   h.call("saveRecord",new JSONObject().put("record",new JSONObject().put("id","document-upgrade-survivor").put("type","learning").put("date","2026-10-01").put("title","升级前的长文笔记").put("note","升级前内容与学习时长必须保留。\n原文第二段。").put("durationMinutes",37)));
   h.call("saveMessage",new JSONObject().put("message",new JSONObject().put("id","document-upgrade-chat").put("role","user").put("createdAt",1700000000000L).put("text","Synthetic history")));
   JSONObject snapshot=state();try(FileOutputStream out=getTargetContext().openFileOutput("document-upgrade.json",0)){out.write(snapshot.toString().getBytes("UTF-8"));}
  }else{
   JSONObject old=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(getTargetContext().getFilesDir(),"document-upgrade.json").toPath()),"UTF-8")),now=state();
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.10.5"),"Signed 1.10.5 cover install starts");
   if(phase.equals("verify"))for(String key:new String[]{"records","settings","messages","wikiPages"})check(old.get(key).toString().equals(now.get(key).toString()),"Cover upgrade preserves "+key);
   // Local navigation works without any configured model.
   h.js("document.querySelector('[data-page=chat]').click();document.querySelector('#chat-input').value='我想记录一条学习记录';document.querySelector('#chat-input').dispatchEvent(new Event('input'));document.querySelector('.chat-send').click()");until("!!document.querySelector('#doc-body')");
   check(h.js("document.querySelector('.document-kind').textContent").contains("学习"),"Bare learning request opens native editor without API");check(!h.js("document.body.innerText").contains("请先在设置中填写"),"No model gate for local editor navigation");
   int before=records().length();input("只有标题","");click("#doc-save");check(records().length()==before,"Empty body cannot commit");
   check(h.js("getComputedStyle(document.querySelector('#doc-save')).color").contains("33, 106, 84"),"Green app palette retained");
   click("#doc-info");check(h.js("!document.querySelector('#doc-meta').hidden && document.querySelector('#doc-meta').getBoundingClientRect().right<=innerWidth+1").equals("true"),"Metadata opens right-side drawer within viewport");click("#doc-meta-done");awaitNothing();close();
   model();
   // All four content types use same editor and explicit metadata completion.
   for(String type:new String[]{"learning","work_note","idea","experience"}){
    open(type);int calls=new JSONArray(h.js("__calls")).length();input("","正文："+type+"的真实理解与下一步，保留不改写。");Thread.sleep(900);
    check(new JSONArray(h.js("__calls")).length()==calls,"Typing "+type+" never calls AI automatically");
    click("#doc-complete");until("document.querySelector('#doc-title').value==='AI生成的主题标题'");check(h.js("DocumentRich.serialize(document.querySelector('#doc-body'))").contains("真实理解"),"AI metadata completion preserves "+type+" body");
    if(type.equals("learning")){
     String data=image();JSONObject image=(JSONObject)h.call("storeRecordImage",new JSONObject().put("data",data).put("name","合成图片.png"));
     check(RecordMedia.file(getTargetContext(),image.getString("id")).isFile(),"Imported image saved in app private storage");check(image.getInt("width")==320&&image.getInt("height")==180,"Image dimensions decoded correctly");
     h.js("window.nativeEvent('recordImage',"+image.toString()+")");until("!!document.querySelector('#doc-body img') && document.querySelector('#doc-body img').naturalWidth===320");
     check(h.js("document.querySelector('#doc-body img').src").contains("appassets.androidplatform.net/record-media/"),"Inline image served from private same-origin endpoint");
     click("#doc-info");h.js("document.querySelector('[data-meta=durationMinutes]').value='42';document.querySelector('[data-meta=platform]').value='本地学习';document.querySelector('#doc-meta').dispatchEvent(new Event('input'))");click("#doc-meta-done");h.screenshot("document-editor.png");
    }
    saveUI();JSONObject last=records().getJSONObject(0); // Store query ordering may differ; find matching note.
    for(int j=0;j<records().length();j++)if(records().getJSONObject(j).optString("note").contains("正文："+type))last=records().getJSONObject(j);
    check(last.getString("title").equals("AI生成的主题标题")&&last.getJSONArray("tags").length()==2,"Completed "+type+" metadata persists");
    if(type.equals("learning")){check(last.getInt("durationMinutes")==42&&last.getJSONArray("images").length()==1,"Learning duration remains metadata, images persist with body");h.js("window.__imageRecord="+JSONObject.quote(last.getString("id")));}
    else check(last.getInt("durationMinutes")==0,"Document "+type+" does not add work or learning hours");
   }
   // Long note, caret-aware pagination, requested swipe direction.
   open("learning");StringBuilder body=new StringBuilder();for(int i=0;i<120;i++)body.append("第").append(i).append("段：长文阅读测试，保留每一段正文。我们从记录中理解自己的生活，不丢弃任何文字。\n\n");input("长文分页阅读",body.toString());
   h.js("(()=>{const node=document.querySelector('#doc-body').firstChild,r=document.createRange();r.setStart(node,2600);r.collapse(true);getSelection().removeAllRanges();getSelection().addRange(r);})()");click("#doc-mode");Thread.sleep(500);
   String page=h.js("document.querySelector('#doc-page').textContent");check(!page.startsWith("\"1 /"),"Reader enters caret page rather than resetting to first page ("+page+")");
   h.js("window.__page=+document.querySelector('#doc-page').textContent.split('/')[0]");
   h.js("(()=>{const v=document.querySelector('#doc-viewport');const a=new Event('touchstart');a.touches=[{clientX:250,clientY:300}];v.dispatchEvent(a);const b=new Event('touchend');b.changedTouches=[{clientX:100,clientY:300}];v.dispatchEvent(b);})()");
   check(h.js("+document.querySelector('#doc-page').textContent.split('/')[0]===__page+1").equals("true"),"Left swipe advances to next page");
   h.js("(()=>{const v=document.querySelector('#doc-viewport');const a=new Event('touchstart');a.touches=[{clientX:100,clientY:300}];v.dispatchEvent(a);const b=new Event('touchend');b.changedTouches=[{clientX:250,clientY:300}];v.dispatchEvent(b);})()");
   check(h.js("+document.querySelector('#doc-page').textContent.split('/')[0]===__page").equals("true"),"Right swipe returns to previous page");h.screenshot("document-reader.png");click("#doc-mode");
   click("#doc-format");until("!!document.querySelector('#doc-preview')");check(h.js("DocumentRich.serialize(document.querySelector('#doc-body'))").equals(JSONObject.quote(body.toString())),"AI preview never mutates original editable body");click("#doc-cancel-format");
   check(h.js("DocumentRich.serialize(document.querySelector('#doc-body'))").equals(JSONObject.quote(body.toString())),"Cancel retains full long original");
   click("#doc-format");until("!!document.querySelector('#doc-preview')");click("#doc-apply");check(h.js("RecordDocument.plain(DocumentRich.serialize(document.querySelector('#doc-body'))).replace(/，/g,'。')").equals(JSONObject.quote(body.toString().replaceAll("\\s", "").replace('，','。'))),"Accepted multichunk Markdown preserves all words with paragraph-ending punctuation normalization");
   check(new JSONArray(h.js("__calls.filter(p=>p.tool.name==='plan_record_layout')")).length()>4,"Long document is processed in multiple chunks, not truncated at 6000 characters");
   h.js("__bad=true");click("#doc-mode");String formatted=h.js("DocumentRich.serialize(document.querySelector('#doc-body'))");click("#doc-format");until("!document.querySelector('#doc-format').disabled");check(h.js("DocumentRich.serialize(document.querySelector('#doc-body'))").equals(formatted),"Malformed AI formatting cannot overwrite body");h.js("__bad=false");close();
   JSONArray drafts=(JSONArray)h.call("getDocumentDraft",new JSONObject());check(drafts.length()>0,"Abandoned long editor is retained as durable draft");String draftId=drafts.getJSONObject(drafts.length()-1).getJSONObject("record").getString("id");
   // Restart ensures images and drafts survive activity/process reconstruction.
   runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
   check(((JSONArray)h.call("getDocumentDraft",new JSONObject())).length()>0,"Draft survives activity restart");JSONObject ir=null;for(int i=0;i<records().length();i++)if(records().getJSONObject(i).has("images"))ir=records().getJSONObject(i);if(ir==null)throw new Exception("Image record missing");
   h.js("RecordEditor.open("+ir.toString()+",null,'learning',true)");until("!!document.querySelector('#doc-flow img') && document.querySelector('#doc-flow img').naturalWidth===320");check(true,"Persisted image displays after restart in Markdown reader");h.screenshot("document-image-reader.png");close();
   JSONArray currentRecords=records(),media=RecordMedia.exportImages(getTargetContext(),currentRecords);java.util.Set<String> referenced=new java.util.HashSet<>();for(int i=0;i<currentRecords.length();i++){JSONArray images=currentRecords.getJSONObject(i).optJSONArray("images");if(images!=null)for(int j=0;j<images.length();j++)referenced.add(images.getJSONObject(j).getString("id"));}check(media.length()>0&&media.length()==referenced.size(),"Backup exports each referenced content-addressed image once");RecordMedia.restoreImages(getTargetContext(),media);check(true,"Checksummed image backup round trip validates");
   try{RecordMedia.restoreImages(getTargetContext(),new JSONArray().put(new JSONObject().put("id","../outside").put("data",media.getJSONObject(0).getString("data"))));throw new Exception("Traversal accepted");}catch(Exception expected){check(!expected.getMessage().contains("accepted"),"Backup rejects forged IDs and directory traversal");}
   JSONObject stale=record("document-upgrade-survivor"),copy=new JSONObject(stale.toString());copy.put("durationMinutes",41);h.call("saveRecord",new JSONObject().put("record",copy));try{h.call("saveDocument",new JSONObject().put("record",stale).put("expectedVersion",stale.getLong("updatedAt")));throw new Exception("Stale write accepted");}catch(Exception expected){check(record(stale.getString("id")).getInt("durationMinutes")==41,"Stale document version cannot overwrite a newer AI/time edit");}
   // Chat actual content stays in confirmation workflow and auto completes metadata.
   model();for(String type:new String[]{"learning","work_note","idea","experience"}){h.js("__nextType="+JSONObject.quote(type)+";document.querySelector('[data-page=chat]').click();document.querySelector('#chat-input').value="+JSONObject.quote("帮我记录"+(type.equals("learning")?"学习":type.equals("work_note")?"工作日志":type.equals("idea")?"灵感":"经验")+"：今天完成了一个项目案例，并且有真实正文。")+";document.querySelector('#chat-input').dispatchEvent(new Event('input'));document.querySelector('.chat-send').click()");until("!!document.querySelector('[data-save-candidate]') && !document.querySelector('.loading')");check(h.js("document.body.innerText").contains("AI生成的主题标题"),"Chat "+type+" automatically completes title and tags before confirmation");int count=records().length();check(!h.js("document.querySelector('#editor').open").equals("true"),"Content chat remains in candidate workflow, not empty editor");click("[data-save-candidate]");if(h.js("document.querySelector('#confirm-dialog').open").equals("true"))click("#confirm-ok");until("!document.querySelector('[data-save-candidate]')");check(records().length()==count+1,"Chat "+type+" commits only after explicit confirmation");}
  }
  result.putString("stream","PASS DocumentEditorTest "+phase+": "+passed+" checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable err){try{if(h!=null)h.screenshot("document-failure.png");}catch(Exception ignored){}result.putString("stream","FAIL DocumentEditorTest after "+passed+": "+err+"\n");finish(Activity.RESULT_CANCELED,result);}}
 void awaitNothing(){}
}
