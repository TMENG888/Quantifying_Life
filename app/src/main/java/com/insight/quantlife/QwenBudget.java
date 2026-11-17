package com.insight.quantlife;

import android.content.*;import org.json.*;
/** One durable rolling-minute request/token budget for the App's Qwen labeling workers. */
public final class QwenBudget {
 public static final int RPM=24000,TPM=4000000,MAX_PARALLEL=8;
 public static JSONObject reserve(JSONObject state,long now,int estimate)throws Exception{
  JSONArray rows=state.optJSONArray("requests"),kept=new JSONArray();int tokens=0;if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);long at=r.getLong("at");if(at>now-60000){kept.put(r);tokens+=r.getInt("tokens");}}
  if(estimate>TPM||kept.length()>=RPM||tokens+estimate>TPM)throw new QwenTransport.Failure("打标达到80%速率预算，稍后继续",429,60000);
  String id=java.util.UUID.randomUUID().toString();kept.put(new JSONObject().put("id",id).put("at",now).put("tokens",estimate));state.put("requests",kept);return new JSONObject().put("id",id).put("reserved",estimate);
 }
 public static void reconcile(JSONObject state,String id,int actual)throws Exception{JSONArray rows=state.optJSONArray("requests");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(id.equals(r.optString("id"))&&actual>=0)r.put("tokens",actual);}}
 private static JSONObject load(Context c)throws Exception{return new JSONObject(c.getSharedPreferences("qwen-budget",0).getString("window","{}"));}
 private static void save(Context c,JSONObject s)throws Exception{if(!c.getSharedPreferences("qwen-budget",0).edit().putString("window",s.toString()).commit())throw new Exception("打标速率预算未能保存");}
 static synchronized String acquire(Context c,int estimate)throws Exception{JSONObject s=load(c),ticket=reserve(s,System.currentTimeMillis(),estimate);save(c,s);return ticket.getString("id");}
 static synchronized void finish(Context c,String id,int actual)throws Exception{JSONObject s=load(c);reconcile(s,id,actual);save(c,s);}
 private QwenBudget(){}
}
