package com.insight.quantlife.tests;
import android.app.*;import android.os.Bundle;import org.json.*;
public class BeforeDeepSeekUpgradeTest extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{MainHost h=new MainHost(this);h.open();
 JSONObject p=new JSONObject().put("id","deepseek").put("label","DeepSeek").put("baseUrl","https://api.deepseek.com/v1").put("apiKey","synthetic-official-key").put("model","deepseek-chat").put("protocol","chat").put("effort","off");
 h.call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",p)));
 h.call("saveRecord",new JSONObject().put("allowDuplicate",true).put("record",new JSONObject().put("id","deepseek-upgrade-marker").put("type","learning").put("date","2026-10-17").put("title","升级保留原始笔记").put("note","旧版原始学习笔记，不能被链接资料覆盖").put("durationMinutes",15).put("createdAt",100).put("tags",new JSONArray()).put("platform","测试平台").put("contentType","article")));
 out.putString("stream","PASS old 1.9.2 fixture seeded using synthetic data\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL "+e);finish(Activity.RESULT_CANCELED,out);}}
}
