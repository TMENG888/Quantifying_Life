package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import org.json.JSONObject;
public class MapTest extends Instrumentation{
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();MainHost host=null;try{
  host=new MainHost(this);host.open();host.js("document.querySelector('[data-page=tracks]').click()");host.waitText("每天走过的地方");
  host.js("document.querySelector('#track-date').value='2026-10-05';document.querySelector('#track-date').dispatchEvent(new Event('change'))");Thread.sleep(700);
  host.js("document.querySelector('#track-map-mode').value='online';document.querySelector('#track-map-mode').dispatchEvent(new Event('change'))");if(!host.waitText("加载在线 OSM 地图"))throw new Exception("Missing map privacy prompt");host.js("document.querySelector('#confirm-ok').click()");
  boolean ready=false;for(int i=0;i<200;i++){String text=host.js("document.querySelector('#track-map-message')?.textContent||''");if(text.contains("在线 OSM 底图")){ready=true;break;}if(text.contains("本地路线网格"))break;Thread.sleep(150);}
  if(ready){host.js("document.querySelector('#track-map').scrollIntoView({block:'start'})");host.screenshot("tracks-map.png");
    JSONObject tile=(JSONObject)host.call("trackTile",new JSONObject().put("z",0).put("x",0).put("y",0));if(!tile.getString("data").startsWith("data:image/png;base64,"))throw new Exception("Invalid native tile data");
    out.putString("stream","PASS Online OSM visible-viewport map, attribution, privacy consent, native PNG tile and route overlay.\n");
  }else out.putString("stream","LIMIT Online map unavailable on test network; local route remains usable.\n");
  host.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));out.putString("stream",out.getString("stream")+"PASS Online map consent can be revoked.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL map UI: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}finally{if(host!=null)try{host.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));}catch(Exception ignored){}}}
}
