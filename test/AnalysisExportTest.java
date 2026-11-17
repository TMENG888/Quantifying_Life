package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import android.net.Uri;
import com.insight.quantlife.*;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;

/** Emulator-only full-history export and failure lifecycle tests. No real user files or API requests. */
public class AnalysisExportTest extends DocumentEditorTest {
 void schema(SQLiteDatabase db){db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,type TEXT,date TEXT,payload TEXT)");for(String t:new String[]{"wiki_pages","wiki_insights","wiki_insight_reviews","settings","chat_messages","track_points"})db.execSQL("CREATE TABLE "+t+"(id TEXT PRIMARY KEY,payload TEXT)");}
 void put(SQLiteDatabase db,String table,JSONObject r)throws Exception{db.execSQL("INSERT INTO "+table+"(id"+(table.equals("records")?",type,date":"")+",payload) VALUES("+ (table.equals("records")?"?,?,?,?":"?,?")+")",table.equals("records")?new Object[]{r.getString("id"),r.getString("type"),r.getString("date"),r.toString()}:new Object[]{r.getString("id"),r.toString()});}
 JSONObject row(String id,String type)throws Exception{return new JSONObject().put("id",id).put("type",type).put("date","2026-10-28").put("title","合成测试："+id).put("note","正文原样保留").put("updatedAt",100).put("tags",new JSONArray());}
 JSONObject snapshot(SQLiteDatabase db,ByteArrayOutputStream out)throws Exception{LearningAnalysisExport.write(db,out,"1.19.1",(n,total)->{});return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
 void checks()throws Exception{
  try(SQLiteDatabase db=SQLiteDatabase.create(null)){
   schema(db);ByteArrayOutputStream emptyBytes=new ByteArrayOutputStream();JSONObject empty=snapshot(db,emptyBytes);
   check(empty.getJSONArray("records").length()==0&&empty.getJSONObject("summary").isNull("dateStart"),"Empty history is valid JSON with no invented date range");
   StringBuilder longNote=new StringBuilder("# 原文\n**加粗**\n\n代码：\n"+(char)96+(char)96+(char)96+"java\nint n=1;\n"+(char)96+(char)96+(char)96+"\n");
   for(int i=0;i<9000;i++)longNote.append("第").append(i).append("段🙂：保留全文、引号\"和换行。\n");
   JSONObject learning=row("learn","learning").put("date","1980-01-01").put("note",longNote.toString()).put("durationMinutes",42.5).put("tags",new JSONArray().put("RAG")).put("wikiTopics",new JSONArray().put("检索")).put("wikiDomains",new JSONArray().put("AI 与技术")).put("wikiClassificationVersion",4).put("wikiDomainVersion",100).put("wikiTopicVersion",100)
    .put("studyTiming",new JSONObject().put("id","timer").put("startedAt",1000).put("endedAt",7000).put("durationMs",3000).put("apiKey","DO_NOT_EXPORT"))
    .put("feishuSource",new JSONObject().put("documentId","doc").put("documentCreatedDate","1980-01-01").put("apiKey","DO_NOT_EXPORT"))
    .put("images",new JSONArray().put(new JSONObject().put("id","DO_NOT_EXPORT").put("data","DO_NOT_EXPORT")))
    .put("apiKey","DO_NOT_EXPORT").put("location","DO_NOT_EXPORT").put("profile",new JSONObject().put("key","DO_NOT_EXPORT"));
   put(db,"records",learning);put(db,"records",row("missing","learning"));put(db,"records",row("zero","learning").put("durationMinutes",0).put("note",""));
   JSONObject work=row("work","work").put("workStart","2026-10-28T23:00:00").put("workEnd","2026-10-29T02:00:00").put("breaks",new JSONArray().put(new JSONObject().put("start","2026-10-29T00:30:00").put("end","2026-10-29T01:00:00").put("apiKey","DO_NOT_EXPORT")));
   put(db,"records",work);for(String type:new String[]{"work_note","idea","experience"})put(db,"records",row(type,type));
   for(int i=0;i<300;i++)put(db,"records",row("bulk-"+i,"learning").put("durationMinutes",1).put("wikiDomainVersion",i==0?100:99).put("wikiTopicVersion",100).put("wikiDomains",new JSONArray().put("AI 与技术")).put("wikiTopics",new JSONArray().put("检索")).put("wikiClassificationVersion",i==0?3:4));
   put(db,"records",row("meditation","meditation"));for(String table:new String[]{"settings","chat_messages","track_points"})put(db,table,new JSONObject().put("id","hidden").put("apiKey","DO_NOT_EXPORT"));
   put(db,"wiki_pages",new JSONObject().put("id","wiki").put("topic","检索").put("summary","派生摘要").put("sourceIds",new JSONArray().put("learn")).put("sourceVersions",new JSONObject().put("learn",100)).put("concepts",new JSONArray().put(new JSONObject().put("title","概念").put("description","说明").put("sourceIds",new JSONArray().put("learn")).put("apiKey","DO_NOT_EXPORT"))));
   put(db,"wiki_insights",new JSONObject().put("id","report").put("findings",new JSONArray().put(new JSONObject().put("id","finding").put("kind","gap").put("title","知识缺口").put("sides",new JSONArray().put(new JSONObject().put("pageId","wiki").put("claim","待检验观点").put("evidence",new JSONArray().put(new JSONObject().put("recordId","learn").put("quote","正文原样保留").put("apiKey","DO_NOT_EXPORT"))))))));
   put(db,"wiki_insight_reviews",new JSONObject().put("id","review").put("reportId","report").put("state","accepted").put("note","核对说明").put("apiKey","DO_NOT_EXPORT"));
   ByteArrayOutputStream out=new ByteArrayOutputStream();JSONObject root=snapshot(db,out),summary=root.getJSONObject("summary");JSONArray records=root.getJSONArray("records");JSONObject first=records.getJSONObject(0);
   check(records.length()==307&&summary.getInt("recordCount")==307,"All five record types and 300 extra rows exported without list caps");
   check(first.getString("id").equals("learn")&&first.getString("note").equals(longNote.toString()),"Long Unicode Markdown note beyond 6000 characters survives exactly");
   check(first.getJSONObject("analysisFields").getInt("noteCodePoints")==longNote.toString().codePointCount(0,longNote.length()),"Unicode length handles emoji without declaring cognition gain");
   check(first.getDouble("durationMinutes")==42.5&&first.getJSONObject("studyTiming").getLong("durationMs")==3000&&first.getJSONObject("studyTiming").getLong("endedAt")==7000,"Recorded minutes and pause-adjusted timer fields remain distinct");
   check(summary.getInt("learningWithPositiveMinutes")==301&&summary.getInt("learningWithMissingMinutes")==1&&summary.getInt("learningWithZeroMinutes")==1,"Missing, zero and positive minutes remain separately auditable");
   check(summary.getString("dateStart").equals("1980-01-01")&&summary.getString("dateEnd").equals("2026-10-28"),"Export covers every saved date regardless of dashboard range");
   check(summary.getInt("emptyNotes")==1&&summary.getInt("currentDomainClassifications")==1,"Completeness flags preserve empty notes and stale classifications");
   check(root.getInt("classificationVersion")==WikiClassificationService.currentVersion()&&!records.getJSONObject(1).getJSONObject("analysisFields").getBoolean("domainCurrent")&&!records.getJSONObject(1).getJSONObject("analysisFields").getBoolean("topicCurrent"),"Current classifier version is explicit and older classifier output stays stale");
   check(first.getInt("imageCount")==1&&!root.toString().contains("DO_NOT_EXPORT")&&!root.has("settings"),"Explicit field allowlists exclude key storage, unknown fields, chat, location and image files");
   JSONObject exportedWork=null;for(int i=0;i<records.length();i++)if(records.getJSONObject(i).getString("id").equals("work"))exportedWork=records.getJSONObject(i);
   check(exportedWork.getString("workEnd").equals(work.getString("workEnd"))&&exportedWork.getJSONArray("breaks").getJSONObject(0).getString("end").equals("2026-10-29T01:00:00"),"Cross-day work and original breaks exported without inventing journal hours");
   check(root.getJSONArray("wikiPages").getJSONObject(0).getJSONObject("sourceVersions").getInt("learn")==100&&root.getJSONObject("wikiInsights").getJSONArray("reports").getJSONObject(0).getJSONArray("findings").getJSONObject(0).getJSONArray("sides").getJSONObject(0).getJSONArray("evidence").getJSONObject(0).getString("recordId").equals("learn"),"Wiki concept, evidence and source versions remain available for verification");
   check(root.getJSONObject("fieldGuide").getString("records.date").contains("文档创建日期")&&root.getJSONObject("fieldGuide").getString("records.note").contains("不能全部视为个人理解"),"Analysis guide explains imported dates and note provenance limitations");
   try(Cursor c=db.rawQuery("SELECT payload FROM records WHERE id='learn'",null)){c.moveToFirst();check(c.getString(0).equals(learning.toString())&&!db.inTransaction(),"Export leaves source payload and transaction state unchanged");}
   boolean failed=false;try{LearningAnalysisExport.write(db,new OutputStream(){public void write(int b)throws IOException{throw new IOException("synthetic full disk");}public void write(byte[] b,int o,int n)throws IOException{throw new IOException("synthetic full disk");}},"test",(n,t)->{});}catch(IOException expected){failed=true;}
   check(failed&&!db.inTransaction(),"Output failure releases snapshot transaction and can be retried");
   Context context=getTargetContext();context.getSharedPreferences("analysis-export",0).edit().clear().commit();
   JSONObject pending=AnalysisExportManager.prepare(context,db);check(pending.getString("state").equals("waiting")&&pending.getJSONObject("summary").getInt("recordCount")==307,"Native prepare stages complete durable snapshot");
   boolean duplicate=false;try{AnalysisExportManager.prepare(context,db);}catch(Exception expected){duplicate=true;}check(duplicate,"Duplicate export cannot overwrite pending file");
   check(AnalysisExportManager.cancel(context).getString("state").equals("cancelled"),"Cancel does not report success");
   AnalysisExportManager.prepare(context,db);JSONObject bad=AnalysisExportManager.save(context,Uri.parse("content://invalid.synthetic.provider/export"));check(bad.getString("state").equals("failed"),"Unwritable destination is visible and retryable");
   AnalysisExportManager.prepare(context,db);File target=new File(context.getCacheDir(),"analysis-test.json");JSONObject saved=AnalysisExportManager.save(context,Uri.fromFile(target));
   check(saved.getString("state").equals("complete")&&new JSONObject(new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8)).getJSONArray("records").length()==307,"Successful retry writes parseable complete JSON through content resolver");
   target.delete();check(AnalysisExportManager.status(context).getString("state").equals("complete"),"Success survives status refresh");
   AnalysisExportManager.prepare(context,db);check(AnalysisExportManager.failPicker(context).getString("state").equals("failed"),"Unavailable system picker does not leave pending state");
   context.getSharedPreferences("analysis-export",0).edit().putString("status","{\"state\":\"saving\"}").commit();
   check(AnalysisExportManager.status(context).getString("state").equals("failed"),"Interrupted delivery becomes retryable failure on restart");
   AnalysisExportManager.prepare(context,db);AnalysisExportManager.cancel(context);
  }
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();
  result.putString("stream","PASS AnalysisExportTest: "+passed+" checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable ex){result.putString("stream","FAIL AnalysisExportTest after "+passed+": "+ex+"\n"+android.util.Log.getStackTraceString(ex));finish(Activity.RESULT_CANCELED,result);}}
}
