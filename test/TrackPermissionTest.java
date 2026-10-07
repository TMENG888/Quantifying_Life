package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import android.view.accessibility.AccessibilityNodeInfo;import org.json.JSONObject;import com.insight.quantlife.TrackingService;
public class TrackPermissionTest extends Instrumentation{
 MainHost host;int passed;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean value,String label)throws Exception{if(!value)throw new Exception(label);passed++;Bundle out=new Bundle();out.putString("stream","PASS "+label+"\n");sendStatus(0,out);}
 boolean clickPermission(String id)throws Exception{
  for(int i=0;i<80;i++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null){for(String pkg:new String[]{"com.android.permissioncontroller","com.google.android.permissioncontroller"})for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByViewId(pkg+":id/"+id))if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}Thread.sleep(100);}return false;
 }
 JSONObject status()throws Exception{return ((JSONObject)host.call("trackDay",new JSONObject().put("date","2026-10-05"))).getJSONObject("status");}
 @Override public void onStart(){Bundle out=new Bundle();try{
  android.accessibilityservice.AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;getUiAutomation().setServiceInfo(info);
  getTargetContext().stopService(new android.content.Intent(getTargetContext(),TrackingService.class));TrackingService.prefs(getTargetContext()).edit().clear().commit();
  host=new MainHost(this);host.open();host.js("document.querySelector('[data-page=tracks]').click()");host.waitText("每天走过的地方");
  check(!status().getBoolean("precisePermission"),"No precise location permission before user opt-in");
  host.js("document.querySelector('#track-toggle').click()");check(host.waitText("开启全天定位"),"App disclosure precedes system location permission");host.js("document.querySelector('#confirm-cancel').click()");check(!TrackingService.running,"Canceling consent does not start tracking");
  host.js("document.querySelector('#track-toggle').click();document.querySelector('#confirm-ok').click()");check(clickPermission("permission_deny_button"),"User can deny Android location permission");Thread.sleep(400);check(!TrackingService.running&&!status().getBoolean("enabled"),"Denied location permission cannot silently start service");
  host.js("document.querySelector('#track-toggle').click();document.querySelector('#confirm-ok').click()");check(clickPermission("permission_allow_foreground_only_button"),"Android while-in-use precise permission can be granted");
  if(android.os.Build.VERSION.SDK_INT>=33)check(clickPermission("permission_allow_button"),"Android notification permission requested separately after location");
  for(int i=0;i<40&&!TrackingService.running;i++)Thread.sleep(150);check(TrackingService.running,"Foreground location service starts after interactive permission flow");
  check(!status().getBoolean("backgroundPermission"),"First start does not force always-allow background permission");
  host.js("document.querySelector('#track-refresh').click()");check(host.waitText("● 全天记录中"),"UI reports real foreground recording state");
  host.js("document.querySelector('#track-toggle').click()");for(int i=0;i<40&&TrackingService.running;i++)Thread.sleep(100);check(!TrackingService.running&&!status().getBoolean("enabled"),"In-app pause stops interactive-started service");
  String dummy="dummy-map-key-for-local-security-test";host.call("trackSaveMap",new JSONObject().put("provider","carto").put("key",dummy));
  check(status().getBoolean("mapKeyConfigured")&&!status().getBoolean("mapConsent"),"Map key configuration is independent and resets map consent");
  check(!((JSONObject)host.call("getSettings",new JSONObject())).toString().contains(dummy)&&!((JSONObject)host.call("init",new JSONObject())).toString().contains(dummy)&&!status().toString().contains(dummy),"Map key never appears in UI settings or AI initialization");
  java.io.File secure=new java.io.File(getTargetContext().getApplicationInfo().dataDir,"shared_prefs/secure.xml");String encrypted=new String(java.nio.file.Files.readAllBytes(secure.toPath()),java.nio.charset.StandardCharsets.UTF_8);check(!encrypted.contains(dummy),"Map key uses encrypted Keystore settings at rest");
  host.call("trackSaveMap",new JSONObject().put("provider","carto").put("key",""));check(status().getBoolean("mapKeyConfigured"),"Empty map key preserves saved key");
  host.call("trackSaveMap",new JSONObject().put("provider","carto").put("clearKey",true));check(!status().getBoolean("mapKeyConfigured"),"Explicit clear removes saved map key");
  host.call("trackConfig",new JSONObject().put("mapMode","online").put("mapConsent",true));host.call("trackSaveMap",new JSONObject().put("provider","carto"));host.call("trackConfig",new JSONObject().put("mapConsent",true));boolean blocked=false;try{host.call("trackTile",new JSONObject().put("z",0).put("x",0).put("y",0));}catch(Exception e){blocked=true;}check(blocked,"CARTO requests cannot run without map key");
  host.call("trackSaveMap",new JSONObject().put("provider","osm"));check(!status().getBoolean("mapConsent"),"Changing map provider requires new privacy consent");
  host.js("document.querySelector('#track-refresh').click()");Thread.sleep(500);check(host.js("document.querySelector('#track-map-key')===null").equals("true"),"Unused map key form removed from mobile UI");
  out.putString("stream","\nPASS: "+passed+" interactive permission and map-key security checks (API"+android.os.Build.VERSION.SDK_INT+").\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,out);}finally{try{host.call("trackStop",new JSONObject());host.call("trackSaveMap",new JSONObject().put("provider","osm").put("clearKey",true));}catch(Exception ignored){}}}
}
