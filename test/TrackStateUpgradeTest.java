package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.hardware.*;import android.location.*;
import com.insight.quantlife.*;import org.json.*;import java.io.*;import java.lang.reflect.*;
/** Isolated emulator only: synthetic data, old signed APK -> cover install. */
public final class TrackStateUpgradeTest extends Instrumentation {
 MainHost h;int passed;String phase;final String date="2026-11-18";
 @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"verify":b.getString("phase","verify");start();}
 void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception(label);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 void until(String expr)throws Exception{for(int i=0;i<150;i++){if(h.js(expr).equals("true"))return;Thread.sleep(100);}throw new Exception("UI timeout: "+expr);}
 JSONObject state()throws Exception{JSONObject o=(JSONObject)h.call("init",new JSONObject());o.getJSONObject("settings").getJSONObject("wikiAutomation").remove("dailyUsed");return o;}
 Location fix(long time,double meters,boolean moving){Location l=new Location("gps");l.setTime(time);l.setLatitude(.02+meters/111195);l.setLongitude(.04);l.setAccuracy(8);l.setSpeed(moving?1.4f:0);l.setSpeedAccuracyMetersPerSecond(.1f);return l;}
 void write(JSONObject o)throws Exception{try(OutputStream f=getTargetContext().openFileOutput("track-state-upgrade.json",0)){f.write(o.toString().getBytes("UTF-8"));}}
 JSONObject read()throws Exception{return new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(getTargetContext().getFilesDir(),"track-state-upgrade.json").toPath()),"UTF-8"));}
 String dGpx(TrackStore db)throws Exception{android.content.SharedPreferences prefs=TrackingService.prefs(getTargetContext());String mode=prefs.getString("filterMode",TrackFusion.ID);try{prefs.edit().putString("filterMode",TrackVariants.ID).commit();return db.gpx(date);}finally{prefs.edit().putString("filterMode",mode).commit();}}
 void sensorTest()throws Exception{
  Class<?> c=Class.forName("com.insight.quantlife.MotionSampler");Constructor<?> ctor=c.getDeclaredConstructor(Context.class);ctor.setAccessible(true);Object sampler=ctor.newInstance(getTargetContext());
  Method start=c.getDeclaredMethod("start"),snapshot=c.getDeclaredMethod("snapshot"),stop=c.getDeclaredMethod("stop");for(Method m:new Method[]{start,snapshot,stop})m.setAccessible(true);
  try{start.invoke(sampler);JSONObject initial=(JSONObject)snapshot.invoke(sampler);check(initial.getBoolean("motionRegistered")&&initial.getBoolean("motionDedicatedThread"),"Real Android sensor registers on dedicated callback thread");
   Field handler=c.getDeclaredField("handler"),manager=c.getDeclaredField("manager"),last=c.getDeclaredField("last"),count=c.getDeclaredField("count"),wake=c.getDeclaredField("wakeUp"),gate=c.getDeclaredField("lastBurst"),failed=c.getDeclaredField("failedBursts");for(Field f:new Field[]{handler,manager,last,count,wake,gate,failed})f.setAccessible(true);
   check(((Handler)handler.get(sampler)).getLooper()!=Looper.getMainLooper(),"Motion callback queue is independent of UI and GPS processing");
   ((SensorManager)manager.get(sampler)).unregisterListener((SensorEventListener)sampler);last.setLong(sampler,SystemClock.elapsedRealtimeNanos()-10000000000L);count.setInt(sampler,20);wake.setBoolean(sampler,false);gate.setLong(sampler,0);failed.setInt(sampler,0);
   JSONObject a=(JSONObject)snapshot.invoke(sampler),b=(JSONObject)snapshot.invoke(sampler);check(a.getBoolean("motionBurstActive")&&!a.getBoolean("motionAvailable")&&!a.has("motionRms"),"Stale samples trigger bounded wake but never become movement evidence");
   check(a.getInt("motionBurstCount")==b.getInt("motionBurstCount"),"Repeated snapshots respect 30-second recovery rate limit");gate.setLong(sampler,0);JSONObject recovered=(JSONObject)snapshot.invoke(sampler);
   check(recovered.getInt("motionRecoveryCount")==1&&recovered.getBoolean("motionRegistered"),"Two unsuccessful windows actually re-register the fallback sensor");
   check(recovered.getInt("motionSamples")==0&&!recovered.getBoolean("motionAvailable"),"Listener recovery resets insufficient evidence instead of reporting fresh motion");
   ((SensorManager)manager.get(sampler)).unregisterListener((SensorEventListener)sampler);Field selected=c.getDeclaredField("selectedSensor");selected.setAccessible(true);Sensor sensor=(Sensor)selected.get(sampler);Constructor<SensorEvent> eventCtor=SensorEvent.class.getDeclaredConstructor(int.class);eventCtor.setAccessible(true);
   synchronized(sampler){last.setLong(sampler,0);count.setInt(sampler,0);long base=SystemClock.elapsedRealtimeNanos()-700000000L;for(int i=0;i<10;i++){SensorEvent e=eventCtor.newInstance(3);e.sensor=sensor;e.timestamp=base+i*60000000L;e.values[2]=9.81f;((SensorEventListener)sampler).onSensorChanged(e);}check(((JSONObject)snapshot.invoke(sampler)).getBoolean("motionAvailable"),"Ten fresh sensor events provide valid evidence after recovery");
    last.setLong(sampler,SystemClock.elapsedRealtimeNanos()-10000000000L);SensorEvent e=eventCtor.newInstance(3);e.sensor=sensor;e.timestamp=SystemClock.elapsedRealtimeNanos();e.values[2]=9.81f;((SensorEventListener)sampler).onSensorChanged(e);check(!((JSONObject)snapshot.invoke(sampler)).getBoolean("motionAvailable")&&count.getInt(sampler)==1,"An event after a long gap restarts warmup rather than reusing historical sample count");}
   Field lock=c.getDeclaredField("burst");lock.setAccessible(true);Thread.sleep(2800);check(!((PowerManager.WakeLock)lock.get(sampler)).isHeld(),"Recovery wake lock expires within its 2.5-second bound");
   stop.invoke(sampler);check(handler.get(sampler)==null&&!((JSONObject)snapshot.invoke(sampler)).getBoolean("motionRegistered"),"Stop releases listener, worker reference and wake lock");
  }finally{stop.invoke(sampler);}
 }
 @Override public void onStart(){Bundle result=new Bundle();try(TrackStore db=new TrackStore(getTargetContext())){
  h=new MainHost(this);h.open();h.js("AutoWiki.pause()");getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));
  if(phase.equals("seed")){
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.7"),"Seed actual previous signed 1.9.7");
   h.call("saveRecord",new JSONObject().put("allowDuplicate",true).put("record",new JSONObject().put("id","state-upgrade-note").put("type","learning").put("date",date).put("title","Synthetic preservation").put("note","Original note must survive location upgrade").put("durationMinutes",25)));
   h.call("saveMessage",new JSONObject().put("message",new JSONObject().put("id","state-upgrade-message").put("role","user").put("createdAt",1700000000000L).put("text","Synthetic local chat survivor")));
   h.call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",new JSONObject().put("id","custom").put("label","Local synthetic").put("baseUrl","https://127.0.0.1:9/v1").put("model","synthetic").put("protocol","chat").put("effort","off").put("apiKey","synthetic-not-a-real-key"))));
   h.call("trackConfig",new JSONObject().put("filterMode","motion65").put("mapMode","offline").put("mapConsent",false));long time=TrackStore.dayStart(date)+43200000;
   for(int i=0;i<210;i++)db.add(fix(time+i*2000,i<90?0:180,false),"synthetic",new JSONObject().put("eligible",true));
   for(int i=0;i<90;i++)db.add(fix(time+788000+i*2000,180,false),"synthetic",new JSONObject().put("eligible",true));
   for(int i=0;i<80;i++)db.add(fix(time+1000000+i*2000,i*2.8,true),"synthetic",new JSONObject().put("eligible",true).put("motionRms",.4).put("motionAgeMs",500));
   JSONObject day=db.day(date);JSONArray points=day.getJSONArray("points");double beforeLat=Double.NaN,afterLat=Double.NaN;for(int i=0;i<points.length();i++){JSONObject q=points.getJSONObject(i);if(q.getLong("time")==time+418000)beforeLat=q.getDouble("lat");if(q.getLong("time")==time+788000)afterLat=q.getDouble("lat");}double jump=Math.abs(afterLat-beforeLat)*111195;
   check(jump>170,"Previous production APK reproduces fabricated stop reset after outage (meters="+jump+")");
   write(new JSONObject().put("state",state()).put("raw",db.diagnostics(date).getJSONArray("rawPoints")).put("original",db.gpx(date,true)).put("D",dGpx(db)));
  }else{
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.8"),"Same-signature 1.9.8 cover install starts successfully");JSONObject old=read(),now=state();
   for(String key:new String[]{"records","settings","wikiPages","messages"})check(old.getJSONObject("state").get(key).toString().equals(now.get(key).toString()),"Cover upgrade preserves "+key);
   check(old.getJSONArray("raw").toString().equals(db.diagnostics(date).getJSONArray("rawPoints").toString()),"Every raw fix and telemetry field survives upgrade unchanged");
   check(old.getString("original").equals(db.gpx(date,true)),"Original GPX stays byte-identical");check(old.getString("D").equals(dGpx(db)),"D comparison stays byte-identical");
   JSONObject day=db.day(date);check(day.getInt("filterVersion")==10&&db.getReadableDatabase().getVersion()==3,"Historical tracks recompute revision 10 without database replacement");
   JSONArray points=day.getJSONArray("points");double lat=points.getJSONObject(0).getDouble("lat"),extent=0;for(int i=0;i<points.length();i++){JSONObject q=points.getJSONObject(i);if(q.getLong("time")<TrackStore.dayStart(date)+44200000)extent=Math.max(extent,Math.abs(q.getDouble("lat")-lat)*111195);}
   check(extent<1,"Actual SQLite processing removes the unsupported stop reset");check(day.getLong("estimatedMeters")>200&&day.getLong("estimatedMeters")<230,"Subsequent genuine walking remains measurable");
   check(points.length()>=378,"Processing preserves observation timestamps instead of deleting the noisy interval");
   h.js("document.querySelector('[data-page=tracks]').click()");until("!!document.querySelector('#track-date')");h.js("document.querySelector('#track-date').value='"+date+"';document.querySelector('#track-date').dispatchEvent(new Event('change'))");until("!!document.querySelector('#track-map')&&document.querySelector('#track-filter').value==='motion65'");
   check(h.js("LifeTracks.summary("+points+").meters").equals(String.valueOf(day.getLong("estimatedMeters"))),"Real WebView and Java reference mileage agree");
   check(h.js("(()=>{const b=LifeTracks.bridges("+points+");return b.some(x=>x.uncertain)&&b.every(x=>x.inferred===true&&x.b.time>x.a.time);})()").equals("true"),"Unknown spans remain chronological inference, separate from solid measured mileage");
   check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Updated track page fits phone width");h.js("document.querySelector('#track-map').scrollIntoView({block:'center'})");h.screenshot("track-state-198.png");sensorTest();
   check(!now.has("points"),"Precise locations remain excluded from AI initialization");
  }
  result.putString("stream","PASS "+passed+" Android state/upgrade checks ("+phase+")\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){e.printStackTrace();result.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,result);}finally{getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));}}
}
