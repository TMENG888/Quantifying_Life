package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.location.Location;import android.database.sqlite.SQLiteDatabase;
import com.insight.quantlife.*;import org.json.*;

/** Disposable emulator; original-coordinate and UI regressions, no external requests. */
public final class RawTrackTest extends DocumentEditorTest {
 final String date="2026-12-26",largeDate="2026-12-27";long base;String beforeRaw,beforeGpx,dBefore,fBefore;String policy;boolean ownsFixtures;
 Location point(long time,int i,float accuracy,String provider){Location p=new Location(provider);p.setTime(time);p.setLatitude(31.23+i*.0001);p.setLongitude(121.47);p.setAccuracy(accuracy);return p;}
 JSONObject query(String mode)throws Exception{return (JSONObject)h.call("trackTime",new JSONObject().put("date",date).put("mode",mode).put("start","09:30").put("end","09:31").put("at","09:30:10"));}
 void seed()throws Exception{
  base=TrackStore.dayStart(date)+9*3600000+30*60000;
  try(TrackStore db=new TrackStore(getTargetContext())){
   check(db.day(date).getInt("totalPoints")==0&&db.day(largeDate).getInt("totalPoints")==0,"Synthetic raw fixture days are unused");ownsFixtures=true;
   db.add(point(base,0,5,"gps"),"raw-fixture");db.add(point(base+5000,1,85,"gps"),"raw-fixture");
   db.add(point(base+10000,2,650,"fused"),"raw-fixture",new JSONObject().put("eligible",false).put("excludedReason","stale_callback"));
   db.add(point(base+15000,50,1000,"network"),"raw-fixture");db.add(point(base+20000,160,10,"gps"),"raw-fixture");db.add(point(base+20000,161,12,"gps"),"raw-fixture");
   db.add(point(base+81000,3,6,"gps"),"raw-fixture");db.add(point(base+82000,4,6,"gps"),"raw-restart");
   db.getWritableDatabase().execSQL("UPDATE points SET mocked=1 WHERE time=?",new Object[]{base+20000});
   beforeRaw=db.diagnostics(date).getJSONArray("rawPoints").toString();beforeGpx=db.gpx(date,true);dBefore=db.day(date,TrackVariants.ID,false).toString();fBefore=db.day(date,TrackFusion.ID,false).toString();
  }
 }
 void nativeChecks()throws Exception{
  h.call("trackConfig",new JSONObject().put("filterMode","raw"));
  try(TrackStore db=new TrackStore(getTargetContext())){
   JSONObject result=db.day(date);JSONArray points=result.getJSONArray("points"),saved=new JSONArray(beforeRaw);
   check(points.length()==8&&result.getInt("processedPoints")==8&&result.getInt("totalPoints")==8,"All eight saved callbacks survive raw display");
   check(result.getJSONObject("quality").getInt("excludedPoints")==0&&!result.getBoolean("displaySimplified")&&!result.getBoolean("truncated"),"Raw display performs no filtering or simplification");
   for(int i=0;i<points.length();i++){JSONObject a=points.getJSONObject(i),b=saved.getJSONObject(i);check(a.getLong("id")==b.getLong("id")&&a.getLong("time")==b.getLong("time")&&a.getDouble("lat")==b.getDouble("lat")&&a.getDouble("lon")==b.getDouble("lon")&&a.getDouble("accuracy")==b.getDouble("accuracy")&&a.getString("provider").equals(b.getString("provider"))&&a.getBoolean("mocked")==b.getBoolean("mocked"),"Coordinate, source, error and stable time/id order unchanged: "+i);}
   check(points.getJSONObject(2).getDouble("accuracy")==650&&points.getJSONObject(2).getBoolean("raw")&&points.getJSONObject(4).getBoolean("mocked")&&!points.getJSONObject(4).getBoolean("filtered"),"Poor accuracy, ineligible and mock callbacks remain marked raw");
   check(result.getInt("processedGaps")==3&&result.getLong("estimatedMeters")>1700,"Only duplicate time, missing interval and session change break raw lines; fast hops retained");
   check(query("range").getJSONArray("points").length()==6,"Time range uses unfiltered original callbacks and exclusive end");
   JSONObject moment=query("moment").getJSONObject("match");check(moment.getLong("time")==base+10000&&moment.getDouble("accuracy")==650&&moment.getString("provider").equals("fused"),"Moment lookup can locate an original callback rejected by processed modes");
   JSONObject roads=(JSONObject)h.call("trackRoadMatch",new JSONObject().put("date",date));check(roads.getJSONArray("routes").length()==0&&roads.getString("state").equals("raw_view"),"Native raw path refuses all road matching independently of UI");
   check(db.gpx(date).equals(beforeGpx),"Selected raw GPX contains exactly original coordinates and segments");
   check(db.day(date,TrackVariants.ID,false).toString().equals(dBefore)&&db.day(date,TrackFusion.ID,false).toString().equals(fBefore),"Both existing processing policies produce unchanged results");
   SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{long start=TrackStore.dayStart(largeDate)+6*3600000;for(int i=0;i<20005;i++)db.add(point(start+i*1000,i,90,"gps"),"raw-large");sql.setTransactionSuccessful();}finally{sql.endTransaction();}
   JSONObject large=db.day(largeDate);check(large.getJSONArray("points").length()==20005&&!large.getBoolean("displaySimplified"),"More than 20000 raw points are displayed without simplification");
   JSONObject range=TrackTime.query(db.day(largeDate,"raw",false),new JSONObject().put("mode","range").put("start","00:00").put("end","00:00"));check(range.getJSONArray("points").length()==20005&&!range.getBoolean("displaySimplified"),"Raw all-day time range also bypasses display reduction");
  }
  boolean invalid=false;try{h.call("trackConfig",new JSONObject().put("filterMode","invalid"));}catch(Exception expected){invalid=true;}check(invalid&&TrackingService.prefs(getTargetContext()).getString("filterMode","").equals("raw"),"Invalid option cannot replace persisted raw selection");
 }
 void ui()throws Exception{
  click("[data-page=tracks]");until("!!document.querySelector('#track-date')");h.js("document.querySelector('#track-date').value='"+date+"';document.querySelector('#track-date').dispatchEvent(new Event('change'))");until("document.querySelector('#track-map')?.dataset.pointCount==='8'");
  check(h.js("document.querySelector('#track-filter').value==='raw'&&document.querySelectorAll('#track-filter option').length===3").equals("true"),"Persisted raw mode appears beside both existing options");
  check(h.js("document.querySelector('#track-road-enabled').disabled&&document.querySelector('#track-map').dataset.roadRoutes==='0'").equals("true"),"Raw UI disables road inference and shows no matched overlays");
  check(h.waitText("原始连线长度"),"Raw length is labelled without claiming actual travel distance");
  click("[data-track-time-mode=moment]");h.js("document.querySelector('#track-time-at').value='09:30:10'");click("#track-time-apply");until("document.querySelector('#track-map')?.dataset.selectedTime==='"+(base+10000)+"'");
  check(h.waitText("650")&&h.waitText("fused"),"Selected moment reveals actual weak callback error and source");
  click("[data-track-time-mode=all]");until("document.querySelector('#track-map')?.dataset.pointCount==='8'");
  h.js("document.querySelector('#track-map').scrollIntoView({block:'center'})");h.screenshot("raw-track-map.png");
  for(String p:new String[]{"weighted65","motion65","raw"}){h.js("document.querySelector('#track-filter').value='"+p+"';document.querySelector('#track-filter').dispatchEvent(new Event('change'))");until("document.querySelector('#track-filter')?.value==='"+p+"'");Thread.sleep(600);check(((JSONObject)h.call("trackDay",new JSONObject().put("date",date))).getJSONObject("filter").getString("id").equals(p),"Switching policy persists native selection: "+p);}
  h.js("document.querySelector('#track-filter').scrollIntoView({block:'center'})");h.screenshot("raw-track-selector.png");
  runOnMainSync(()->h.web.getSettings().setTextZoom(160));Thread.sleep(400);check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Raw labels remain within viewport with 160 percent text");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;String settings=getTargetContext().getSharedPreferences("secure",0).getString("settings",null);policy=TrackingService.prefs(getTargetContext()).getString("filterMode",TrackFusion.ID);try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");getTargetContext().getSharedPreferences("secure",0).edit().remove("settings").commit();h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");seed();nativeChecks();ui();
  try(TrackStore db=new TrackStore(getTargetContext())){check(db.diagnostics(date).getJSONArray("rawPoints").toString().equals(beforeRaw)&&db.gpx(date,true).equals(beforeGpx),"All mode and time interactions preserve raw database and original GPX");}ok=true;out.putString("stream","PASS RawTrackTest: "+passed+" checks\n");
 }catch(Throwable error){out.putString("stream","FAIL RawTrackTest after "+passed+": "+android.util.Log.getStackTraceString(error));try{if(h!=null)h.screenshot("raw-track-failure.png");}catch(Exception ignored){}}
 finally{try{runOnMainSync(()->{if(h!=null){h.web.getSettings().setTextZoom(100);h.activity.finish();}});TrackingService.prefs(getTargetContext()).edit().putString("filterMode",policy).apply();try(TrackStore db=new TrackStore(getTargetContext())){if(ownsFixtures){db.deleteDay(date);db.deleteDay(largeDate);}}}catch(Exception ignored){}android.content.SharedPreferences.Editor e=getTargetContext().getSharedPreferences("secure",0).edit();if(settings==null)e.remove("settings");else e.putString("settings",settings);e.commit();}finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
