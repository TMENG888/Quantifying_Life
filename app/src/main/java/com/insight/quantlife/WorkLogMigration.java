package com.insight.quantlife;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import org.json.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
/** Atomic, content-addressed split: legacy backups and repeated opens cannot duplicate logs. */
final class WorkLogMigration {
 static void put(SQLiteDatabase db,JSONObject r)throws Exception{ContentValues v=new ContentValues();for(String k:new String[]{"id","type","date","title","platform"})v.put(k,r.optString(k));v.put("payload",r.toString());if(db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new Exception("工作日志迁移失败");WikiAutomation.enqueue(db,r.getString("id"),r.optLong("updatedAt"),"upsert");}
 static void split(SQLiteDatabase db,JSONObject shift)throws Exception{
  if(!shift.optString("type").equals("work")||shift.optString("note").trim().isEmpty())return;
  String note=shift.getString("note");
  byte[] hash=MessageDigest.getInstance("SHA-256").digest((shift.getString("id")+"\u0000"+note).getBytes(StandardCharsets.UTF_8));StringBuilder key=new StringBuilder("work-log-");for(byte b:hash)key.append(String.format(Locale.ROOT,"%02x",b&255));
  boolean exists;try(Cursor c=db.rawQuery("SELECT id FROM records WHERE id=?",new String[]{key.toString()})){exists=c.moveToFirst();}
  long now=System.currentTimeMillis();if(!exists){JSONObject log=new JSONObject().put("id",key.toString()).put("type","work_note").put("date",shift.getString("date")).put("title",shift.optString("title","工作日志")).put("note",note).put("platform","").put("url",shift.optString("url")).put("tags",shift.optJSONArray("tags")==null?new JSONArray():shift.getJSONArray("tags")).put("durationMinutes",0).put("occurredAt","").put("createdAt",shift.optLong("createdAt",now)).put("updatedAt",now).put("source",shift.optString("source","manual")).put("sourceShiftId",shift.getString("id")).put("migratedFrom","shift_note");if(shift.has("wikiTopics"))log.put("wikiTopics",shift.getJSONArray("wikiTopics"));put(db,log);}
  shift.put("note","").put("updatedAt",Math.max(now,shift.optLong("updatedAt")+1));put(db,shift);
 }
 static void run(SQLiteDatabase db)throws Exception{db.beginTransaction();try{ArrayList<JSONObject> shifts=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT payload FROM records WHERE type='work'",null)){while(c.moveToNext())shifts.add(new JSONObject(c.getString(0)));}for(JSONObject r:shifts)split(db,r);db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
