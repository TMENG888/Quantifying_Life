package com.insight.quantlife;
import android.content.*;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import org.json.*;import java.text.SimpleDateFormat;import java.util.*;
/** Durable ID/version queue in the same database transaction as records; preferences contain no secrets. */
public final class WikiAutomation {
 private WikiAutomation(){}
 static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS wiki_queue (id TEXT PRIMARY KEY,version INTEGER NOT NULL,operation TEXT NOT NULL)");}
 static void enqueue(SQLiteDatabase db,String id,long version,String operation){ContentValues v=new ContentValues();v.put("id",id);v.put("version",version);v.put("operation",operation);db.insertWithOnConflict("wiki_queue",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 static String classificationKey(JSONObject r){return r.optInt("wikiClassificationVersion")+":"+r.optLong("wikiDomainVersion",-1)+":"+r.optLong("wikiTopicVersion",-1)+":"+r.optJSONArray("wikiDomains")+":"+r.optJSONArray("wikiTopics");}
 static JSONObject queue(SQLiteDatabase db,Context context)throws Exception{
  db.execSQL("DELETE FROM wiki_queue WHERE id IN (SELECT id FROM records WHERE type='creation')");
  JSONArray jobs=new JSONArray();
  try(Cursor c=db.rawQuery("SELECT q.id,q.version,q.operation,r.payload FROM wiki_queue q LEFT JOIN records r ON r.id=q.id ORDER BY q.version,q.id LIMIT 10",null)){
   while(c.moveToNext()){JSONObject j=new JSONObject().put("id",c.getString(0)).put("version",c.getLong(1)).put("operation",c.getString(2));if(!c.isNull(3))j.put("classificationKey",classificationKey(new JSONObject(c.getString(3))));jobs.put(j);}}
  long count;try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM wiki_queue",null)){c.moveToFirst();count=c.getLong(0);}
  return new JSONObject().put("jobs",jobs).put("count",count).put("settings",settings(context));
 }
 static int acknowledge(SQLiteDatabase db,JSONArray jobs)throws Exception{int n=0;if(jobs.length()>10)throw new Exception("整理确认批次过大");for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(j.has("classificationKey")){try(Cursor c=db.rawQuery("SELECT payload FROM records WHERE id=?",new String[]{j.getString("id")})){if(c.moveToFirst()&&!j.getString("classificationKey").equals(classificationKey(new JSONObject(c.getString(0)))))continue;}}n+=db.delete("wiki_queue","id=? AND version=? AND operation=?",new String[]{j.getString("id"),Long.toString(j.getLong("version")),j.getString("operation")});}return n;}
 static JSONArray assign(SQLiteDatabase db,JSONArray items)throws Exception{if(items.length()>10)throw new Exception("归类批次过大");JSONArray result=new JSONArray();boolean own=!db.inTransaction();if(own)db.beginTransaction();try{for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i),r;String id=item.getString("id");try(Cursor c=db.rawQuery("SELECT payload FROM records WHERE id=?",new String[]{id})){if(!c.moveToFirst())throw new Exception("待归类来源已删除");r=new JSONObject(c.getString(0));}if(r.optString("type").equals("creation"))throw new Exception("创作稿件不加入学习与工作知识库");if(r.optLong("updatedAt")!=item.getLong("version"))throw new Exception("待归类内容已更新，请重新整理");JSONArray topics=item.getJSONArray("topics");if(topics.length()==0||topics.length()>3)throw new Exception("每条记录须归入1至3个主题");for(int j=0;j<topics.length();j++)if(!(topics.get(j) instanceof String)||topics.getString(j).trim().length()<2||topics.getString(j).length()>60)throw new Exception("知识主题格式无效");if(item.has("domains")){
 JSONArray domains=item.getJSONArray("domains");String type=r.optString("type");
 String allowed=type.equals("learning")?"AI 与技术|商业与管理|人文与阅读|语言与表达|健康与生活|其他主题":(type.equals("work")||type.equals("work_note"))?"研究与分析|产品与技术|沟通与协作|规划与复盘|执行与交付|其他主题":"技术思考|工作与成长|产品与创意|人文与生活|自我与关系|其他主题";
 Set<String> choices=new HashSet<>(Arrays.asList(allowed.split("\\|")));
 if(domains.length()<1||domains.length()>2)throw new Exception("知识领域数量无效");
 for(int k=0;k<domains.length();k++)if(!(domains.get(k) instanceof String)||!choices.contains(domains.getString(k)))throw new Exception("知识领域格式无效");
 r.put("wikiDomains",domains);r.put("wikiDomainVersion",r.optLong("updatedAt"));r.put("wikiClassificationVersion",4);
 }r.put("wikiTopics",topics);r.put("wikiTopicVersion",r.optLong("updatedAt"));ContentValues v=new ContentValues();v.put("payload",r.toString());db.update("records",v,"id=?",new String[]{id});enqueue(db,id,r.optLong("updatedAt"),"upsert");result.put(r);}if(own)db.setTransactionSuccessful();}finally{if(own)db.endTransaction();}return result;}
 static int backfill(SQLiteDatabase db)throws Exception{int count=0;boolean own=!db.inTransaction();if(own)db.beginTransaction();try{try(Cursor c=db.rawQuery("SELECT id,payload FROM records",null)){while(c.moveToNext()){JSONObject r=new JSONObject(c.getString(1));if(!WikiClassificationService.content(r))continue;enqueue(db,c.getString(0),r.optLong("updatedAt"),"upsert");count++;}}if(own)db.setTransactionSuccessful();}finally{if(own)db.endTransaction();}return count;}
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences("wiki-automation",Context.MODE_PRIVATE);}
 /** One-time history queue initialization; new edits already enqueue in their save transaction. */
 static synchronized void ensureBackfill(SQLiteDatabase db,Context c)throws Exception{SharedPreferences p=prefs(c);if(p.getBoolean("internal-backfill-v3-domains",false)){repairUnclassified(db);return;}backfill(db);if(!p.edit().putBoolean("internal-backfill-v3-domains",true).commit())throw new Exception("自动知识库初始化暂未完成，将稍后重试");}
 /** Recover missing classification jobs, including imported or previously acknowledged history. */
 static int repairUnclassified(SQLiteDatabase db)throws Exception{
  int count=0;
  try(Cursor c=db.rawQuery("SELECT r.id,r.payload FROM records r LEFT JOIN wiki_queue q ON q.id=r.id WHERE q.id IS NULL",null)){
   while(c.moveToNext()){
    JSONObject r=new JSONObject(c.getString(1));if(!WikiClassificationService.content(r))continue;
    JSONArray domains=r.optJSONArray("wikiDomains"),topics=r.optJSONArray("wikiTopics");
    if(WikiClassificationService.needs(r)||r.optInt("wikiClassificationVersion")<3||domains==null||domains.length()==0||topics==null||topics.length()==0||r.optLong("wikiDomainVersion",-1)!=r.optLong("updatedAt")||r.optLong("wikiTopicVersion",-1)!=r.optLong("updatedAt")){
     enqueue(db,c.getString(0),r.optLong("updatedAt"),"upsert");count++;
    }
   }
  }return count;
 }
 private static String day(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
 public static synchronized JSONObject settings(Context c)throws Exception{SharedPreferences p=prefs(c);return new JSONObject().put("enabled",true).put("dailyCallLimit",0).put("dailyUsed",p.getString("day","").equals(day())?p.getInt("used",0):0);}
 /** Compatibility bridge; no app request quota; provider billing/rate limits still apply. */
 public static synchronized JSONObject configure(Context c,JSONObject s)throws Exception{return settings(c);}
 public static synchronized void reserve(Context c)throws Exception{SharedPreferences p=prefs(c);String today=day();int used=p.getString("day","").equals(today)?p.getInt("used",0):0;p.edit().putString("day",today).putInt("used",used+1).commit();}
}
