package com.insight.quantlife;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import org.json.*;import java.util.*;
/** Derived knowledge only. Quotes and source/page versions are checked independently of the WebView/model. */
public final class WikiInsightStore {
 private WikiInsightStore(){}
 public static void create(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS wiki_insights (id TEXT PRIMARY KEY,payload TEXT NOT NULL)");db.execSQL("CREATE TABLE IF NOT EXISTS wiki_insight_reviews (id TEXT PRIMARY KEY,payload TEXT NOT NULL)");db.execSQL("CREATE TABLE IF NOT EXISTS wiki_insight_history (id TEXT PRIMARY KEY,payload TEXT NOT NULL)");}
 static JSONObject row(SQLiteDatabase db,String table,String id)throws Exception{try(Cursor c=db.rawQuery("SELECT payload FROM "+table+" WHERE id=?",new String[]{id})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
 static JSONArray rows(SQLiteDatabase db,String table)throws Exception{JSONArray a=new JSONArray();try(Cursor c=db.rawQuery("SELECT payload FROM "+table+" ORDER BY rowid DESC",null)){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;}
 public static JSONObject get(SQLiteDatabase db)throws Exception{return new JSONObject().put("reports",rows(db,"wiki_insights")).put("reviews",rows(db,"wiki_insight_reviews")).put("history",rows(db,"wiki_insight_history"));}
 private static String text(JSONObject o,String key,int min,int max)throws Exception{Object v=o.get(key);if(!(v instanceof String)||((String)v).trim().length()<min||((String)v).length()>max)throw new Exception("知识发现文本或编号无效");return (String)v;}
 private static Set<String> ids(JSONArray a)throws Exception{Set<String> out=new HashSet<>();for(int i=0;i<a.length();i++){if(!(a.get(i) instanceof String))throw new Exception("知识来源编号无效");out.add(a.getString(i));}return out;}
 private static Map<String,JSONObject> scope(SQLiteDatabase db,JSONObject report)throws Exception{
  if(report.getInt("analyzerVersion")!=1)throw new Exception("知识发现版本无效");
  JSONArray pageIds=report.getJSONArray("pageIds"),key=new JSONArray(report.getString("taskKey"));if(pageIds.length()<1||pageIds.length()>2||!pageIds.toString().equals(key.toString())||ids(pageIds).size()!=pageIds.length())throw new Exception("知识发现范围无效");
  JSONObject pageVersions=report.getJSONObject("pageVersions"),versions=report.getJSONObject("sourceVersions");Set<String> expected=new HashSet<>();Map<String,JSONObject> pages=new HashMap<>();
  for(int i=0;i<pageIds.length();i++){String id=pageIds.getString(i);JSONObject page=row(db,"wiki_pages",id);if(page==null||page.optBoolean("partial")||page.optBoolean("notesTruncated")||page.optInt("compilerVersion")!=2||page.getLong("updatedAt")!=pageVersions.getLong(id))throw new Exception("知识页已变化，发现结果等待重新分析");
   JSONArray sources=page.getJSONArray("sourceIds"),indexed=page.optJSONArray("indexSourceIds");if(indexed==null)indexed=sources;
   JSONObject pv=page.getJSONObject("sourceVersions");for(String rid:ids(indexed)){JSONObject r=row(db,"records",rid);if(r==null||r.optLong("updatedAt")!=pv.getLong(rid))throw new Exception("知识页来源已变化，请等待重新整理");}
   if(!ids(indexed).equals(ids(sources))||!ids(indexed).equals(matching(db,page)))throw new Exception("主题来源集合已变化，请等待重新整理");
   expected.addAll(ids(sources));pages.put(id,page);
  }
  Set<String> actual=new HashSet<>();Iterator<String> it=versions.keys();while(it.hasNext()){String id=it.next();actual.add(id);JSONObject r=row(db,"records",id);if(r==null||r.optLong("updatedAt")!=versions.getLong(id))throw new Exception("发现来源已变化，未覆盖新内容");}
  if(!expected.equals(actual)||expected.isEmpty())throw new Exception("发现来源集合不完整");return pages;
 }
 public static JSONObject save(SQLiteDatabase db,JSONObject report)throws Exception{
  db.beginTransaction();try{
   String id=text(report,"id",9,100),signature=text(report,"signature",1,100);if(!id.matches("insight-[a-f0-9]+-[0-9]+"))throw new Exception("知识发现编号无效");
   JSONObject prior=row(db,"wiki_insights",id);if(prior!=null&&!prior.getString("taskKey").equals(report.getString("taskKey")))throw new Exception("知识范围编号冲突");
   Map<String,JSONObject> pages=scope(db,report);JSONArray findings=report.getJSONArray("findings");if(findings.length()>6||report.toString().length()>100000)throw new Exception("知识发现批次过大");Set<String> findingIds=new HashSet<>();
   for(int i=0;i<findings.length();i++){
    JSONObject f=findings.getJSONObject(i);String fid=text(f,"id",9,100),kind=f.getString("kind");if(!fid.matches("finding-[a-f0-9]+-[0-9]+")||!findingIds.add(fid)||!kind.matches("connection|conflict|gap")||kind.equals("connection")&&pages.size()!=2)throw new Exception("发现类型或编号无效");
    text(f,"title",1,100);text(f,"reason",1,1000);text(f,"question",1,500);text(f,"nextStep",1,500);JSONArray sides=f.getJSONArray("sides");if(sides.length()!=(kind.equals("gap")?1:2))throw new Exception("发现缺少双方证据");
    Set<String> recordIds=new HashSet<>(),sidePages=new HashSet<>();
    for(int j=0;j<sides.length();j++){JSONObject side=sides.getJSONObject(j);String pid=side.getString("pageId");JSONObject page=pages.get(pid);if(page==null)throw new Exception("证据主题无效");sidePages.add(pid);text(side,"claim",1,500);JSONArray evidence=side.getJSONArray("evidence");if(evidence.length()<1||evidence.length()>3)throw new Exception("证据数量无效");Set<String> allowed=ids(page.getJSONArray("sourceIds"));
     for(int k=0;k<evidence.length();k++){JSONObject e=evidence.getJSONObject(k);String rid=e.getString("recordId"),quote=text(e,"quote",8,400);JSONObject record=row(db,"records",rid);if(!allowed.contains(rid)||record==null||!record.optString("note").contains(quote))throw new Exception("引文与原始笔记不符");recordIds.add(rid);}
    }
    if(!kind.equals("gap")&&(recordIds.size()<2||pages.size()==2&&sidePages.size()!=2))throw new Exception("发现必须有双方不同原始记录");
   }
   if(prior!=null&&!prior.getString("signature").equals(signature)&&prior.getJSONArray("findings").length()>0)put(db,"wiki_insight_history",id+"|"+prior.getString("signature"),prior);
   report.put("signature",signature).put("updatedAt",System.currentTimeMillis());put(db,"wiki_insights",id,report);db.setTransactionSuccessful();return report;
  }finally{db.endTransaction();}
 }
 public static JSONObject review(SQLiteDatabase db,JSONObject p)throws Exception{
  db.beginTransaction();try{JSONObject report=row(db,"wiki_insights",p.getString("reportId"));if(report==null||!report.getString("signature").equals(p.getString("signature")))throw new Exception("发现已更新，请重新打开后处理");scope(db,report);
   String fid=p.getString("findingId");boolean exists=false;JSONArray fs=report.getJSONArray("findings");for(int i=0;i<fs.length();i++)if(fs.getJSONObject(i).getString("id").equals(fid))exists=true;if(!exists)throw new Exception("发现条目不存在");
   String state=p.getString("state"),note=p.optString("note").trim();if(!state.matches("pending|followup|dismissed|resolved")||note.length()>1000||state.equals("resolved")&&note.isEmpty())throw new Exception("标记已处理时，请写下核对结论或适用条件");
   String id=report.getString("id")+"|"+fid+"|"+report.getString("signature");JSONObject old=row(db,"wiki_insight_reviews",id);if((old==null?0:old.optLong("updatedAt"))!=p.optLong("expectedVersion"))throw new Exception("处理说明已变化，请刷新后再保存");
   JSONObject out=new JSONObject().put("id",id).put("reportId",report.getString("id")).put("findingId",fid).put("signature",report.getString("signature")).put("state",state).put("note",note).put("updatedAt",Math.max(System.currentTimeMillis(),old==null?1:old.optLong("updatedAt")+1));
   put(db,"wiki_insight_reviews",id,out);db.setTransactionSuccessful();return out;
  }finally{db.endTransaction();}
 }
 private static String norm(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}\\p{P}\\p{S}\\uFEFF]","");}
 private static Set<String> matching(SQLiteDatabase db,JSONObject page)throws Exception{
  List<String> words=new ArrayList<>();words.add(norm(page.getString("topic")));JSONArray keys=page.optJSONArray("searchKeywords");if(keys!=null)for(int i=0;i<keys.length();i++)words.add(norm(keys.getString(i)));Set<String> result=new HashSet<>();
  try(Cursor c=db.rawQuery("SELECT payload FROM records WHERE type IN ('learning','work','work_note','idea','experience')",null)){while(c.moveToNext()){JSONObject r=new JSONObject(c.getString(0));String corpus=norm(r.optString("title")+" "+r.optString("note")+" "+r.optJSONArray("tags"));boolean match=false;for(String w:words)if(w.length()>=2&&corpus.contains(w))match=true;JSONArray topics=r.optJSONArray("wikiTopics");if(topics!=null)for(int i=0;i<topics.length();i++)if(words.contains(norm(topics.getString(i))))match=true;if(match)result.add(r.getString("id"));}}
  return result;
 }
 private static void put(SQLiteDatabase db,String table,String id,JSONObject payload)throws Exception{ContentValues v=new ContentValues();v.put("id",id);v.put("payload",payload.toString());if(db.insertWithOnConflict(table,null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new Exception("知识发现保存失败");}
}
