package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.location.*;
import com.insight.quantlife.*;import org.json.*;import java.io.*;import java.lang.reflect.*;
/** Synthetic data only, old-APK seed -> signed cover install -> actual SQLite/WebView verification. */
public final class TrackRecoveryTest extends Instrumentation {
 MainHost h;int passed;String phase;final String date="2026-11-13",sparseDate="2026-11-14",loopDate="2026-11-15";
 @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"verify":b.getString("phase","verify");start();}
 void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception(label);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 void until(String expr)throws Exception{for(int i=0;i<150;i++){if(h.js(expr).equals("true"))return;Thread.sleep(100);}throw new Exception("UI timeout: "+expr);}
 JSONObject state()throws Exception{JSONObject o=(JSONObject)h.call("init",new JSONObject());if(o.has("settings")&&o.getJSONObject("settings").has("wikiAutomation"))o.getJSONObject("settings").getJSONObject("wikiAutomation").remove("dailyUsed");return o;}
 Location fix(long time,double meters,boolean moving){Location l=new Location("gps");l.setTime(time);l.setLatitude(.02+meters/111195.0);l.setLongitude(.04);l.setAccuracy(25);if(moving){l.setSpeed(1.4f);l.setSpeedAccuracyMetersPerSecond(.1f);}return l;}
 void write(JSONObject o)throws Exception{try(OutputStream f=getTargetContext().openFileOutput("track-recovery-upgrade.json",0)){f.write(o.toString().getBytes("UTF-8"));}}
 JSONObject read()throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=getTargetContext().openFileInput("track-recovery-upgrade.json")){byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)b.write(x,0,n);}return new JSONObject(b.toString("UTF-8"));}
 void burstTest()throws Exception{
  Class<?> c=Class.forName("com.insight.quantlife.MotionSampler");Constructor<?> ctor=c.getDeclaredConstructor(Context.class);ctor.setAccessible(true);Object sampler=ctor.newInstance(getTargetContext());
  Method snapshot=c.getDeclaredMethod("snapshot"),stop=c.getDeclaredMethod("stop");snapshot.setAccessible(true);stop.setAccessible(true);
  Field registered=c.getDeclaredField("registered"),last=c.getDeclaredField("last"),count=c.getDeclaredField("count");for(Field f:new Field[]{registered,last,count})f.setAccessible(true);
  try{registered.setBoolean(sampler,true);last.setLong(sampler,SystemClock.elapsedRealtimeNanos()-10000000000L);count.setInt(sampler,20);
   JSONObject a=(JSONObject)snapshot.invoke(sampler),b=(JSONObject)snapshot.invoke(sampler);
   check(a.getBoolean("motionBurstActive")&&a.getInt("motionBurstCount")==1,"Real bounded CPU wake lock acquired for stale non-wake sensor");
   check(!a.getBoolean("motionAvailable")&&!a.has("motionRms"),"Starting a sampling burst never turns stale evidence into fresh motion");
   check(b.getInt("motionBurstCount")==1,"Repeated callbacks respect 30-second burst rate limit");
   Thread.sleep(2800);check(!((JSONObject)snapshot.invoke(sampler)).getBoolean("motionBurstActive"),"Wake lock automatically expires after at most 2.5 seconds");
   Field gate=c.getDeclaredField("lastBurst");gate.setAccessible(true);gate.setLong(sampler,0);snapshot.invoke(sampler);stop.invoke(sampler);
   Field wake=c.getDeclaredField("burst");wake.setAccessible(true);check(!((PowerManager.WakeLock)wake.get(sampler)).isHeld(),"Stopping sampling explicitly releases active burst");
  }finally{stop.invoke(sampler);}
 }
 @Override public void onStart(){Bundle result=new Bundle();LocationManager manager=null;try(TrackStore db=new TrackStore(getTargetContext())){
  h=new MainHost(this);h.open();h.js("AutoWiki.pause()");getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));
  if(phase.equals("seed")||phase.equals("seed195")){
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals(phase.equals("seed195")?"1.9.5":"1.9.4"),"Seed previous signed source version");
   h.call("saveRecord",new JSONObject().put("allowDuplicate",true).put("record",new JSONObject().put("id","track-recovery-note").put("type","learning").put("date",date).put("title","升级保留验证").put("note","合成笔记不能因轨迹升级被覆盖").put("durationMinutes",25)));
   h.call("trackConfig",new JSONObject().put("filterMode","weighted65").put("mapMode","offline").put("mapConsent",false));
   db.deleteDay(date);db.deleteDay(sparseDate);db.deleteDay(loopDate);long start=TrackStore.dayStart(date)+36000000;
   for(int i=0;i<900;i++)db.add(fix(start+i*2000,i<180?Math.sin(i)*4:240+Math.sin(i)*4,false),"clouds",new JSONObject().put("eligible",true).put("motionAgeMs",500000));
   for(int i=0;i<80;i++)db.add(fix(start+1800000+i*2000,i*2.8,true),"clouds",new JSONObject().put("eligible",true).put("motionAgeMs",500).put("motionRms",.4));
   for(int i=0;i<20;i++){Location q=fix(TrackStore.dayStart(sparseDate)+36000000+i*31000,i*40,false);q.setAccuracy(8);db.add(q,"sparse",new JSONObject().put("eligible",true));}
   for(int t=0;t<360;t+=2){double angle=Math.max(0,Math.min(60,t-120))*2*Math.PI/60;Location q=fix(TrackStore.dayStart(loopDate)+36000000+t*1000,10*(1-Math.cos(angle)),false);q.setLongitude(.04+10*Math.sin(angle)/(111195*Math.cos(Math.toRadians(.02))));q.setAccuracy(8);db.add(q,"short-loop",new JSONObject().put("eligible",true).put("motionAgeMs",500).put("motionRms",t>=120&&t<=180?.5:.01));}
   if(phase.equals("seed195"))TrackingService.prefs(getTargetContext()).edit().putInt("processingRevision",8).commit();
   write(new JSONObject().put("loopOriginal",db.gpx(loopDate,true)).put("oldLoopMeters",db.day(loopDate,TrackFusion.ID,false).getLong("estimatedMeters")).put("state",state()).put("raw",db.diagnostics(date).getJSONArray("rawPoints")).put("original",db.gpx(date,true)).put("oldD",db.gpx(date)).put("sparseOriginal",db.gpx(sparseDate,true)));
   check(true,"Synthetic records, Key settings, chats and raw positions snapshotted without printing contents");
  }else{
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.6"),"Signed 1.9.6 installed over previous version");
   JSONObject old=read(),now=state();for(String key:new String[]{"records","settings","wikiPages","messages"})check(old.getJSONObject("state").get(key).toString().equals(now.get(key).toString()),"Cover upgrade preserves "+key);
   if(phase.equals("from195"))check(TrackingService.prefs(getTargetContext()).getString("filterMode","").equals("weighted65"),"Patch upgrade preserves deliberately selected D comparison");
   if(phase.equals("current")||phase.equals("from195"))h.call("trackConfig",new JSONObject().put("filterMode","motion65"));
   check(TrackingService.prefs(getTargetContext()).getString("filterMode","").equals("motion65"),(phase.equals("current")||phase.equals("from195"))?"Current-version replay selects revised algorithm":"One-time processing migration activates revised algorithm even from old D");
   check(db.getReadableDatabase().getVersion()==3&&db.day(date).getInt("filterVersion")==TrackFusion.REVISION,"Read-time processing upgrade needs no raw database migration");
   check(old.getJSONArray("raw").toString().equals(db.diagnostics(date).getJSONArray("rawPoints").toString()),"Every raw coordinate, time and telemetry field preserved");
   check(old.getString("original").equals(db.gpx(date,true))&&old.getString("sparseOriginal").equals(db.gpx(sparseDate,true)),"Original GPX is byte-identical across upgrade");
   JSONObject day=db.day(date);check(day.getInt("processedPoints")>=978&&day.getJSONObject("quality").getInt("uncertainPoints")>0,"Ambiguous observations retained with uncertainty; only impossible spikes rejected (processed="+day.getInt("processedPoints")+")");
   JSONArray points=day.getJSONArray("points");double first=points.getJSONObject(0).getDouble("lat"),extent=0;for(int i=0;i<points.length()&&points.getJSONObject(i).getLong("time")<TrackStore.dayStart(date)+36000000+1800000;i++)extent=Math.max(extent,Math.abs(points.getJSONObject(i).getDouble("lat")-first)*111195);
   check(extent<2&&day.getLong("estimatedMeters")>200&&day.getLong("estimatedMeters")<230,"Stationary 240m cloud jump suppressed while subsequent real walk survives (extent="+extent+", meters="+day.getLong("estimatedMeters")+")");
   JSONObject sparse=db.day(sparseDate);check(sparse.getInt("processedPoints")==20&&sparse.getLong("estimatedMeters")>750,"Actual SQLite pipeline preserves sparse 31-second history");
   JSONObject loop=db.day(loopDate);check(loop.getLong("estimatedMeters")>55&&loop.getLong("estimatedMeters")<70,"Real SQLite retains sensor-supported short loop without GPS speed");
   check(old.getString("loopOriginal").equals(db.gpx(loopDate,true)),"Short-loop raw GPX stays byte-identical");
   if(phase.equals("from195"))check(old.getLong("oldLoopMeters")<50,"Previous signed 1.9.5 short-loop undercount reproduced before cover upgrade");
   check(db.gpx(date).split("<trkpt ",-1).length-1==day.getInt("processedPoints"),"Normal GPX exports all processed timestamps, uncertain spans remain split");
   h.call("trackConfig",new JSONObject().put("filterMode","weighted65"));check(old.getString("oldD").equals(db.gpx(date)),"Old D comparison remains reproducible, no destructive rewrite");
   h.call("trackConfig",new JSONObject().put("filterMode","motion65"));check(TrackingService.prefs(getTargetContext()).getString("filterMode","").equals("motion65"),"Subsequent preference reads preserve user choice");
   h.js("document.querySelector('[data-page=tracks]').click()");until("!!document.querySelector('#track-date')");h.js("document.querySelector('#track-date').value='"+date+"';document.querySelector('#track-date').dispatchEvent(new Event('change'))");
   until("document.querySelector('#track-filter').value==='motion65'&&document.querySelector('#track-bridge-legend').textContent.includes('停留与移动')");
   h.js("window.__uncertainBridge=LifeTracks.bridges("+points+")");
   check(h.js("window.__uncertainBridge.some(b=>b.uncertain&&b.path.length>100)").equals("true"),"Real WebView coalesces uncertain interval while preserving its chronological path");
   check(h.js("LifeTracks.summary("+points+").meters").equals(String.valueOf(day.getLong("estimatedMeters"))),"Web and native mileage agree; dashed estimates add zero distance");
   check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Track controls fit phone width");h.js("document.querySelector('#track-map').scrollIntoView({block:'center'})");Thread.sleep(800);h.screenshot("track-recovery-196.png");
   burstTest();
   getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_FINE_LOCATION);getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_COARSE_LOCATION);
   manager=(LocationManager)getTargetContext().getSystemService(Context.LOCATION_SERVICE);manager.addTestProvider("gps",false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);manager.setTestProviderEnabled("gps",true);h.call("trackStart",new JSONObject());Thread.sleep(500);
   Location fix=fix(System.currentTimeMillis(),0,false);fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation("gps",fix);Thread.sleep(1000);
   JSONArray rows=db.diagnostics(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date())).getJSONArray("rawPoints");JSONObject telemetry=null;for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getLong("time")==fix.getTime())telemetry=rows.getJSONObject(i).getJSONObject("telemetry");
   check(TrackingService.running&&telemetry!=null&&telemetry.getInt("motionBurstMaxMs")==2500&&telemetry.has("motionBurstActive"),"Actual foreground-service callback saves bounded-sampling diagnostics");
   h.call("trackStop",new JSONObject());Thread.sleep(400);check(!TrackingService.running,"Pause stops foreground location and motion sampling");
   check(!((JSONObject)h.call("init",new JSONObject())).has("points"),"Personal locations remain excluded from AI initialization");
  }
  result.putString("stream","PASS "+passed+" signed-upgrade and Android track checks ("+phase+")\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){e.printStackTrace();result.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,result);}finally{getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));if(manager!=null)try{manager.removeTestProvider("gps");}catch(Exception ignored){}}}
}
