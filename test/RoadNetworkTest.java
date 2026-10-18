package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.location.Location;import android.net.Uri;
import com.insight.quantlife.*;import java.io.*;import java.util.*;import org.json.*;
/** Real Android SQLite/import/WebView checks. Synthetic roads/locations ONLY. */
public final class RoadNetworkTest extends Instrumentation {
 MainHost h;int passed;String phase;final String date="2026-11-17";
 @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"current":b.getString("phase","current");start();}
 void check(boolean value,String name)throws Exception{if(!value)throw new Exception(name);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+name+"\n");sendStatus(0,b);}
 void until(String js)throws Exception{for(int i=0;i<150;i++){if(h.js(js).equals("true"))return;Thread.sleep(100);}throw new Exception("UI timeout "+js);}
 File write(String name,String s)throws Exception{File f=new File(getTargetContext().getFilesDir(),name);try(FileOutputStream out=new FileOutputStream(f)){out.write(s.getBytes("UTF-8"));}return f;}
 String osm(){return "<osm version=\"0.6\"><way id=\"10\"><nd ref=\"1\"/><nd ref=\"2\"/><nd ref=\"3\"/><tag k=\"highway\" v=\"residential\"/></way>"+node(1,0,0)+node(2,100,0)+node(3,100,100)+"</osm>";}
 String node(long id,double x,double y){return "<node id=\""+id+"\" lat=\""+(y/111195)+"\" lon=\""+(x/111195)+"\"/>";}
 JSONObject day()throws Exception{return (JSONObject)h.call("trackDay",new JSONObject().put("date",date));}
 JSONObject match()throws Exception{return (JSONObject)h.call("trackRoadMatch",new JSONObject().put("date",date));}
 void seed()throws Exception{
  JSONObject r=new JSONObject().put("id","road-upgrade-note").put("type","learning").put("date",date).put("title","Road upgrade survivor").put("platform","test").put("contentType","article").put("durationMinutes",20).put("note","Original note must not change").put("tags",new JSONArray());h.call("saveRecord",new JSONObject().put("record",r));
  h.call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",new JSONObject().put("id","custom").put("label","Synthetic").put("baseUrl","https://example.com/v1").put("model","synthetic").put("protocol","chat").put("effort","off").put("apiKey","synthetic-local-only-not-real"))));
  double[][] coords={{20,3},{40,4},{65,3},{92,0},{103,20},{102,40},{101,65}};try(TrackStore tracks=new TrackStore(getTargetContext())){long time=TrackStore.dayStart(date)+12*3600000L;for(int i=0;i<coords.length;i++){Location l=new Location("gps");l.setTime(time+i*15000L);l.setLatitude(coords[i][1]/111195);l.setLongitude(coords[i][0]/111195);l.setAccuracy(6);l.setSpeed(1.4f);l.setSpeedAccuracyMetersPerSecond(.1f);tracks.add(l,"road-synthetic",new JSONObject().put("motionRms",.3).put("motionAgeMs",0));}
   write("road-original.gpx",tracks.gpx(date,true));write("road-processed.gpx",tracks.gpx(date));}
  write("road-original-records.json",((JSONObject)h.call("init",new JSONObject())).getJSONArray("records").toString());
  String filter=TrackingService.prefs(getTargetContext()).getString("filterMode",TrackFusion.ID);TrackingService.prefs(getTargetContext()).edit().putString("filterMode",filter).commit();write("road-original-filter.txt",filter);
 }
 String read(String name)throws Exception{return new String(java.nio.file.Files.readAllBytes(new File(getTargetContext().getFilesDir(),name).toPath()),"UTF-8");}
 @Override public void onStart(){Bundle result=new Bundle();try{h=new MainHost(this);h.open();
  if(phase.equals("seed")){seed();check(true,"Seeded 1.9.6 synthetic note, encrypted key and raw track for cover install");result.putString("stream","PASS seed\n");finish(Activity.RESULT_OK,result);return;}
  check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.7"),"Signed 1.9.7 runs on Android");
  check(TrackingService.prefs(getTargetContext()).getString("filterMode","").equals(read("road-original-filter.txt")),"Cover upgrade preserves selected processing policy");
  try(TrackStore tracks=new TrackStore(getTargetContext())){check(tracks.gpx(date,true).equals(read("road-original.gpx")),"Cover update retains raw GPX byte-for-byte");check(tracks.gpx(date).equals(read("road-processed.gpx")),"Cover update retains processed GPX byte-for-byte");}
  check(((JSONObject)h.call("init",new JSONObject())).getJSONArray("records").toString().equals(read("road-original-records.json")),"Learning notes unchanged after cover upgrade");check(((JSONObject)h.call("getSettings",new JSONObject())).getJSONObject("profiles").getJSONObject("custom").getBoolean("keyConfigured"),"Encrypted model key remains configured");
  if(!RoadNetwork.info(getTargetContext()).getBoolean("configured"))check(match().getString("state").equals("no_network"),"No offline network leaves original track usable");
  String originalFilter=day().getJSONObject("filter").getString("id");h.call("trackConfig",new JSONObject().put("filterMode","motion65"));
  RoadNetwork.importFile(getTargetContext(),Uri.fromFile(write("synthetic-roads.osm",osm())));JSONObject info=RoadNetwork.info(getTargetContext());check(info.getBoolean("configured")&&info.getInt("edges")==2,"OSM ways-before-nodes import builds genuine shared-node edges");
  JSONObject before=day(),matched=match();check(matched.getJSONArray("routes").length()>0,"Actual Android filtered walking history matches offline roads");check(matched.getBoolean("inferred")&&!matched.getBoolean("affectsMileage"),"Road output is separate inference, never measured distance");check(day().getLong("estimatedMeters")==before.getLong("estimatedMeters"),"Road rendering cannot change reference distance");
  String id=info.getString("networkId");boolean rejected=false;try{RoadNetwork.importFile(getTargetContext(),Uri.fromFile(write("bad-roads.osm","<not-osm/>")));}catch(Exception e){rejected=true;}check(rejected&&RoadNetwork.info(getTargetContext()).getString("networkId").equals(id),"Invalid import preserves previous network atomically");
  rejected=false;try{RoadNetwork.importFile(getTargetContext(),Uri.fromFile(write("dtd-roads.osm","<!DOCTYPE osm [<!ENTITY x SYSTEM 'file:///not-allowed'>]><osm/>")));}catch(Exception e){rejected=true;}check(rejected&&RoadNetwork.info(getTargetContext()).getString("networkId").equals(id),"DTD/external entity import rejected without replacing network");
  String missing="<osm version=\"0.6\">"+node(1,0,0)+node(2,100,0)+node(3,100,100)+"<way id=\"1\"><nd ref=\"1\"/><nd ref=\"999\"/><nd ref=\"2\"/><nd ref=\"3\"/><tag k=\"highway\" v=\"residential\"/></way></osm>";
  RoadNetwork.importFile(getTargetContext(),Uri.fromFile(write("missing-roads.osm",missing)));check(RoadNetwork.info(getTargetContext()).getInt("edges")==1&&RoadNetwork.info(getTargetContext()).getInt("missingNodeReferences")==1,"Missing node does not create a false shortcut");
  RoadNetwork.importFile(getTargetContext(),Uri.fromFile(write("synthetic-roads.osm",osm())));
  h.call("trackRoadConfig",new JSONObject().put("enabled",false));check(match().getString("state").equals("disabled"),"Inference can be disabled independently");h.call("trackRoadConfig",new JSONObject().put("enabled",true).put("mode","walk"));check(match().getJSONArray("routes").length()>0,"Re-enabling inference restores local matching");
  h.js("document.querySelector('[data-page=tracks]').click()");until("!!document.querySelector('#track-date')");h.js("document.querySelector('#track-date').value='"+date+"';document.querySelector('#track-date').dispatchEvent(new Event('change'))");until("Number(document.querySelector('#track-map')?.dataset.roadRoutes)>0");check(h.js("document.querySelector('#track-road-enabled').checked").equals("true"),"Actual phone UI enables inference for imported network");check(h.waitText("不计里程"),"Visible UI explicitly identifies inference and excluded mileage");
  h.js("document.querySelector('#track-road-enabled').click()");until("document.querySelector('#track-map')?.dataset.roadRoutes==='0'");check(true,"Disabling overlay restores original route without deleting data");h.js("document.querySelector('#track-road-enabled').click()");until("Number(document.querySelector('#track-map')?.dataset.roadRoutes)>0");
  check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Road controls fit phone width");h.js("document.querySelector('#track-map').scrollIntoView({block:'center'})");h.screenshot("road-matching-197.png");
  h.call("trackConfig",new JSONObject().put("filterMode",originalFilter));TrackingService.prefs(getTargetContext()).edit().putString("filterMode",originalFilter).commit();try(TrackStore tracks=new TrackStore(getTargetContext())){check(tracks.gpx(date,true).equals(read("road-original.gpx")),"Import, matching and UI toggles preserve raw GPX bytes");check(tracks.gpx(date).equals(read("road-processed.gpx")),"Inference never leaks into processed GPX");}
  check(!((JSONObject)h.call("init",new JSONObject())).has("points"),"Location inference stays outside AI initialization");
  result.putString("stream","PASS "+passed+" Android offline-network/upgrade checks; synthetic data only\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){e.printStackTrace();result.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
