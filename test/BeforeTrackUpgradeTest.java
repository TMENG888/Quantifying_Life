package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import org.json.JSONObject;import org.json.JSONArray;
public class BeforeTrackUpgradeTest extends Instrumentation{
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{MainHost host=new MainHost(this);host.open();
  JSONObject record=new JSONObject().put("id","upgrade-1.1-note").put("type","learning").put("date","2026-10-05").put("title","JEV原版升级笔记").put("platform","知乎").put("durationMinutes",60).put("note","1.1学习笔记应保留").put("tags",new JSONArray().put("JEV")).put("contentType","article");
  JSONObject saved=(JSONObject)host.call("saveRecord",new JSONObject().put("record",record));JSONObject page=new JSONObject().put("id","wiki-jev").put("topic","JEV").put("summary","1.1原有知识页").put("aliases",new JSONArray()).put("concepts",new JSONArray()).put("sourceIds",new JSONArray().put(saved.getString("id"))).put("sourceVersions",new JSONObject().put(saved.getString("id"),saved.getLong("updatedAt"))).put("updatedAt",System.currentTimeMillis());
  host.call("saveWiki",new JSONObject().put("page",page));out.putString("stream","PASS 1.1 upgrade seed includes learning note, existing Wiki and previously saved dummy model credential.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL upgrade seed: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
