package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.location.*;
import com.insight.quantlife.*;import org.json.*;import java.io.*;
/** Disposable emulator and synthetic fixtures only. Never imports personal diagnostics. */
public final class TrackStopMoveTest extends Instrumentation {
 MainHost h;int passed;String phase;final String date="2026-11-11";
 @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"verify":b.getString("phase","verify");start();}
 void check(boolean ok,String text)throws Exception{if(!ok)throw new Exception(text);passed++;Bundle r=new Bundle();r.putString("stream","PASS "+text+"\n");sendStatus(0,r);}
 void until(String s)throws Exception{for(int i=0;i<150;i++){if(h.js(s).equals("true"))return;Thread.sleep(100);}throw new Exception("UI timeout: "+s);}
 JSONObject init()throws Exception{return (JSONObject)h.call("init",new JSONObject());}
 void stableSettings(JSONObject o)throws Exception{if(o.has("settings")){JSONObject s=o.getJSONObject("settings");if(s.has("wikiAutomation"))s.getJSONObject("wikiAutomation").remove("dailyUsed");}}
 @Override public void onStart(){Bundle result=new Bundle();LocationManager lm=null;try(TrackStore db=new TrackStore(getTargetContext())){
  h=new MainHost(this);h.open();h.js("AutoWiki.pause()");getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));
  if(phase.equals("seed")){
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.0"),"Seed signed previous 1.9.0");
   h.call("saveRecord",new JSONObject().put("allowDuplicate",true).put("record",new JSONObject().put("id","stop-move-upgrade-note").put("type","learning").put("date",date).put("title","轨迹升级保留测试").put("note","合成笔记原文保留").put("durationMinutes",22)));
   db.deleteDay(date);long start=TrackStore.dayStart(date)+36000000;
   for(int i=0;i<360;i++){Location p=new Location("gps");p.setTime(start+i*2000);p.setLatitude(.02+Math.sin(i*.8)*35/111195);p.setLongitude(.04+Math.cos(i*.7)*20/111195);p.setAccuracy(i%20<2?90:60);db.add(p,"stop-move-seed",new JSONObject().put("eligible",true).put("motionAgeMs",-1));}
   h.call("trackConfig",new JSONObject().put("filterMode","weighted65"));JSONObject state=init();stableSettings(state);
   JSONObject snapshot=new JSONObject().put("init",state).put("raw",db.diagnostics(date).getJSONArray("rawPoints")).put("rawGpx",db.gpx(date,true)).put("oldD",db.gpx(date));
   try(OutputStream o=getTargetContext().openFileOutput("stop-move-upgrade.json",0)){o.write(snapshot.toString().getBytes("UTF-8"));}
   check(true,"Synthetic records, settings, Wiki, messages, raw fixes and GPX snapshotted");
  }else{
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.1"),"Signed 1.9.1 cover upgrade without uninstall");
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getTargetContext().openFileInput("stop-move-upgrade.json")){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)bytes.write(buf,0,n);}JSONObject old=new JSONObject(bytes.toString("UTF-8")),now=init();stableSettings(now);
   for(String key:new String[]{"records","settings","messages","wikiPages"})if(old.getJSONObject("init").has(key))check(old.getJSONObject("init").get(key).toString().equals(now.get(key).toString()),"Cover install preserves "+key);
   if(phase.equals("current"))h.call("trackConfig",new JSONObject().put("filterMode","motion65"));else check(TrackingService.prefs(getTargetContext()).getString("filterMode","").equals("motion65"),"Upgrade activates stop/move revision once");
   check(old.getJSONArray("raw").toString().equals(db.diagnostics(date).getJSONArray("rawPoints").toString()),"Raw locations and telemetry are unchanged");
   check(old.getString("rawGpx").equals(db.gpx(date,true)),"Original GPX stays byte-identical");
   JSONObject day=db.day(date);check(day.getInt("filterVersion")==7&&day.getLong("estimatedMeters")==0,"Real SQLite pipeline removes synthetic stationary mileage");
   check(day.getJSONObject("quality").getInt("stationaryEstimatePoints")>0&&day.getJSONObject("quality").getInt("freshMotionPoints")==0,"Missing sensors handled as explicit geometric stop estimates");
   check(day.getJSONArray("points").getJSONObject(0).getString("motionState").equals("stationary_estimate"),"State is carried to native API");
   String normal=db.gpx(date);check(normal.split("<trkpt ",-1).length-1==day.getInt("processedPoints"),"GPX and screen use identical processed observations");
   h.call("trackConfig",new JSONObject().put("filterMode","weighted65"));check(old.getString("oldD").equals(db.gpx(date)),"Original D can still reproduce previous export");
   check(db.day(date).getJSONObject("filter").getString("id").equals("weighted65"),"User choice is not overwritten by subsequent preference reads");
   h.call("trackConfig",new JSONObject().put("filterMode","motion65").put("mapMode","offline").put("mapConsent",false));
   h.js("document.querySelector('[data-page=tracks]').click()");until("!!document.querySelector('#track-date')");h.js("document.querySelector('#track-date').value='"+date+"';document.querySelector('#track-date').dispatchEvent(new Event('change'))");
   until("document.querySelector('#track-filter').value==='motion65'&&document.querySelector('#track-bridge-legend').textContent.includes('停留与移动')");
   check(h.js("document.querySelector('#track-root').innerText.includes('停留估计')").equals("true"),"Green WebView UI explains stop estimate rather than certain physical immobility");
   check(h.js("document.querySelector('#track-filter').options.length===2").equals("true"),"Only new policy and old D comparison are exposed");
   h.js("document.querySelector('.track-map-frame').scrollIntoView({block:'center'})");Thread.sleep(1200);h.tap("#track-map-message",false);runOnMainSync(()->h.web.invalidate());Thread.sleep(1200);h.screenshot("tracks-191-stationary.png");
   h.js("document.querySelector('#track-filter').value='weighted65';document.querySelector('#track-filter').dispatchEvent(new Event('change'))");until("document.querySelector('#track-bridge-legend').textContent.includes('65米')");
   h.js("document.querySelector('#track-filter').value='motion65';document.querySelector('#track-filter').dispatchEvent(new Event('change'))");until("document.querySelector('#track-bridge-legend').textContent.includes('停留与移动')");check(true,"Repeated UI policy switching reaches native processing");
   check(old.getJSONArray("raw").toString().equals(db.diagnostics(date).getJSONArray("rawPoints").toString()),"UI switches never rewrite raw fixes");
   getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_FINE_LOCATION);getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_COARSE_LOCATION);
   lm=(LocationManager)getTargetContext().getSystemService(Context.LOCATION_SERVICE);lm.addTestProvider("gps",false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);lm.setTestProviderEnabled("gps",true);
   h.call("trackStart",new JSONObject());Thread.sleep(1000);check(TrackingService.running,"Foreground recording with motion sampler starts");
   Location fix=new Location("gps");long t=System.currentTimeMillis();fix.setTime(t);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());fix.setLatitude(.02);fix.setLongitude(.04);fix.setAccuracy(20);lm.setTestProviderLocation("gps",fix);Thread.sleep(1500);
   JSONArray rows=db.diagnostics(new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(new java.util.Date(t))).getJSONArray("rawPoints");JSONObject telemetry=null;for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getLong("time")==t)telemetry=rows.getJSONObject(i).getJSONObject("telemetry");
   check(telemetry!=null&&telemetry.has("motionAvailable")&&telemetry.has("motionRegistered")&&telemetry.has("motionWakeUp")&&telemetry.has("motionSamples"),"Real GPS callback includes sensor availability and wake-up diagnostics");
   h.call("trackStop",new JSONObject());Thread.sleep(500);check(!TrackingService.running,"Pause stops service and sensor collection");
  }
  result.putString("stream","PASS "+passed+" stop/move Android checks ("+phase+")\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){e.printStackTrace();result.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,result);}finally{getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));if(lm!=null)try{lm.removeTestProvider("gps");}catch(Exception ignored){}}}
}
