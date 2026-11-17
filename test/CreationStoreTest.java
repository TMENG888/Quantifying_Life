package com.insight.quantlife.tests;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import com.insight.quantlife.*;
import org.json.*;
import java.lang.reflect.*;

/** Local creation records preserve content/media without becoming learning/work evidence. */
public final class CreationStoreTest extends DocumentEditorTest {
 final String ID="qa-creation-native-record";
 JSONObject base()throws Exception{return new JSONObject().put("id",ID).put("type","creation").put("date","2026-11-02").put("title","自媒体创作测试稿").put("note","# 本文结构\n\n**保持语义**，使用图文表达具体方法。\n\n- 第一个要点\n- 第二个要点").put("tags",new JSONArray().put("图文创作")).put("durationMinutes",0).put("creatorPlatform","公众号").put("creationStatus","draft").put("publishedUrl","");}
 JSONObject commit(JSONObject r,Long version)throws Exception{return (JSONObject)h.call("saveDocument",new JSONObject().put("record",new JSONObject(r.toString())).put("allowDuplicate",true).put("expectedVersion",version==null?JSONObject.NULL:version));}
 void reject(JSONObject r,Long version,String label)throws Exception{boolean denied=false;try{commit(r,version);}catch(InvocationTargetException expected){denied=true;}check(denied,label);}
 void isolateWiki()throws Exception{
  try(SQLiteDatabase db=SQLiteDatabase.create(null)){
   db.execSQL("CREATE TABLE records(id TEXT PRIMARY KEY,type TEXT,payload TEXT)");db.execSQL("CREATE TABLE wiki_queue(id TEXT PRIMARY KEY,version INTEGER,operation TEXT)");
   JSONObject c=base().put("updatedAt",100),learning=base().put("id","learning-control").put("type","learning").put("updatedAt",100);
   db.execSQL("INSERT INTO records VALUES(?,?,?)",new Object[]{ID,"creation",c.toString()});db.execSQL("INSERT INTO records VALUES(?,?,?)",new Object[]{"learning-control","learning",learning.toString()});
   for(String method:new String[]{"backfill","repairUnclassified"}){Method m=WikiAutomation.class.getDeclaredMethod(method,SQLiteDatabase.class);m.setAccessible(true);m.invoke(null,db);try(Cursor rows=db.rawQuery("SELECT id FROM wiki_queue WHERE id=?",new String[]{ID})){check(!rows.moveToFirst(),method+" excludes creation without reclassifying it as an essay");}}
   check(!InformationUnits.eligible(c)&&!QwenLabels.eligible(c)&&!WikiClassificationService.needs(c),"Creation cannot enter information or native classification queues");
   Method assign=WikiAutomation.class.getDeclaredMethod("assign",SQLiteDatabase.class,JSONArray.class);assign.setAccessible(true);boolean denied=false;try{assign.invoke(null,db,new JSONArray().put(new JSONObject().put("id",ID).put("version",100).put("topics",new JSONArray().put("不可混入主题"))));}catch(InvocationTargetException expected){denied=true;}check(denied,"Native classification assignment refuses creation records");
  }
 }
 void checks()throws Exception{
  isolateWiki();h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");h.call("deleteRecord",new JSONObject().put("id",ID));
  JSONObject r=base();JSONObject media=(JSONObject)h.call("storeRecordImage",new JSONObject().put("data",image()).put("name","本地图文.png"));String mediaId=media.getString("id");
  r.put("note",r.getString("note")+"\n\n![插画](zhishi-media:"+mediaId+")\n\n图片后原文不丢失。").put("images",new JSONArray().put(media));String body=r.getString("note");
  JSONObject draft=new JSONObject().put("record",r).put("expectedVersion",JSONObject.NULL).put("caret",4).put("savedAt",System.currentTimeMillis());h.call("saveDocumentDraft",new JSONObject().put("draft",draft));JSONArray drafts=(JSONArray)h.call("getDocumentDraft",new JSONObject());boolean found=false;for(int i=0;i<drafts.length();i++){JSONObject v=drafts.getJSONObject(i);if(v.getJSONObject("record").getString("id").equals(ID))found=v.getJSONObject("record").getString("note").equals(body);}check(found,"Creation editor draft round-trips full text and inline image");
  JSONObject saved=commit(r,null);check(saved.getString("type").equals("creation")&&saved.getString("note").equals(body)&&saved.getJSONArray("images").getJSONObject(0).getString("id").equals(mediaId),"Committed creation keeps markup, image order and original content");
  check(saved.getDouble("durationMinutes")==0&&saved.getString("creationStatus").equals("draft"),"Saved manuscript remains separate from time and publishing actions");
  JSONObject update=new JSONObject(saved.toString()).put("title","已修改创作标题").put("creationStatus","ready");saved=commit(update,saved.getLong("updatedAt"));check(saved.getString("note").equals(body)&&saved.getString("creationStatus").equals("ready"),"Metadata update preserves all original text and media");
  reject(new JSONObject(saved.toString()).put("durationMinutes",30),saved.getLong("updatedAt"),"Creation refuses learning minutes");reject(new JSONObject(saved.toString()).put("workStart","2026-11-02T09:00:00"),saved.getLong("updatedAt"),"Creation refuses work shifts");reject(new JSONObject(saved.toString()).put("studyTiming",new JSONObject()),saved.getLong("updatedAt"),"Creation refuses learning timer intervals");
  reject(new JSONObject(saved.toString()).put("creationStatus","automatic-publish"),saved.getLong("updatedAt"),"Only explicit draft/ready/published status values are stored");reject(new JSONObject(saved.toString()).put("publishedUrl","javascript:alert(1)"),saved.getLong("updatedAt"),"Publishing link only accepts web URL schemes");reject(new JSONObject(saved.toString()).put("creatorPlatform",new JSONObject()),saved.getLong("updatedAt"),"Creation metadata refuses non-text fields");
  reject(new JSONObject(saved.toString()).put("note","![only](zhishi-media:"+mediaId+")"),saved.getLong("updatedAt"),"Image-only creation cannot replace the text manuscript");reject(new JSONObject(saved.toString()).put("type","learning"),saved.getLong("updatedAt"),"Document updates cannot silently move creation into learning");reject(update,update.getLong("updatedAt"),"Stale updates cannot overwrite the newer creation");
  check(record(ID).getString("note").equals(body)&&record(ID).getString("creationStatus").equals("ready"),"All rejected updates preserve the committed manuscript");
  try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor q=db.rawQuery("SELECT id FROM wiki_queue WHERE id=?",new String[]{ID})){check(!q.moveToFirst(),"Creation save does not enqueue Wiki maintenance");}
  saved=commit(new JSONObject(saved.toString()).put("creationStatus","published").put("publishedUrl","https://example.com/notes/creation"),saved.getLong("updatedAt"));check(saved.getString("publishedUrl").startsWith("https://"),"User can record an existing publication URL without an upload action");
  JSONArray mediaBackup=RecordMedia.exportImages(getTargetContext(),new JSONArray().put(saved));check(mediaBackup.length()==1&&mediaBackup.getJSONObject(0).getString("id").equals(mediaId),"Full backup retains creation image content");h.call("deleteRecord",new JSONObject().put("id",ID));
  JSONObject restored=(JSONObject)h.call("import",new JSONObject().put("records",new JSONArray().put(saved)).put("recordMedia",mediaBackup));check(restored.getInt("inserted")==1&&record(ID).getString("note").equals(body)&&record(ID).getString("creatorPlatform").equals("公众号")&&record(ID).getString("creationStatus").equals("published"),"Restore round-trips creation type, metadata, text and media");
  JSONObject repeated=(JSONObject)h.call("import",new JSONObject().put("records",new JSONArray().put(saved)));check(repeated.getInt("skipped")==1,"Repeated backup restore preserves the local manuscript");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;out.putString("stream","PASS CreationStoreTest: "+passed+" checks\n");}catch(Throwable error){out.putString("stream","FAIL CreationStoreTest after "+passed+": "+android.util.Log.getStackTraceString(error));}finally{try{if(h!=null){h.call("deleteRecord",new JSONObject().put("id",ID));h.call("clearDocumentDraft",new JSONObject().put("id",ID));runOnMainSync(()->h.activity.finish());}}catch(Exception ignored){}}finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
