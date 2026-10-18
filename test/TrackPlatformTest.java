package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.location.*;import org.json.*;import com.insight.quantlife.*;import java.util.*;
/** Newer Android system fused path, synthetic locations, no network. */
public final class TrackPlatformTest extends Instrumentation {
 MainHost h;int passed;LocationManager manager;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception(label);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
 void provider(String name){manager.addTestProvider(name,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);manager.setTestProviderEnabled(name,true);}
 Location p(String source,double lat){Location l=new Location(source);l.setLatitude(lat);l.setLongitude(.01);l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());l.setAccuracy(12);l.setSpeed(1);l.setSpeedAccuracyMetersPerSecond(.1f);return l;}
 JSONObject find(TrackStore db,String date,long t)throws Exception{JSONArray rows=db.diagnostics(date).getJSONArray("rawPoints");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getLong("time")==t)return rows.getJSONObject(i);throw new Exception("Callback not saved: "+t);}
 @Override public void onStart(){Bundle result=new Bundle();try(TrackStore db=new TrackStore(getTargetContext())){
  check(Build.VERSION.SDK_INT>=31,"New Android system fused API available");h=new MainHost(this);h.open();h.js("AutoWiki.pause()");
  getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));
  for(String permission:new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.POST_NOTIFICATIONS})getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),permission);
  manager=(LocationManager)getTargetContext().getSystemService(Context.LOCATION_SERVICE);provider("gps");provider("fused");h.call("trackStart",new JSONObject());Thread.sleep(700);check(TrackingService.running,"Location foreground service starts on API "+Build.VERSION.SDK_INT);
  Location gps=p("gps",.01);manager.setTestProviderLocation("gps",gps);Thread.sleep(2500);String today=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());JSONObject saved=find(db,today,gps.getTime());check(saved.getJSONObject("telemetry").getInt("requestedIntervalMs")==2000,"Real GPS subscription writes 2-second capture telemetry");
  Location fresh=p("fused",.01001);manager.setTestProviderLocation("fused",fresh);Thread.sleep(600);saved=find(db,today,fresh.getTime());check(saved.getString("provider").equals("fused")&&saved.getJSONObject("telemetry").getLong("gpsAgeMs")<15000,"Real fused callback retained with original source while GPS is fresh");
  JSONArray early=db.day(today,TrackFusion.ID,false).getJSONArray("points");boolean used=false;for(int i=0;i<early.length();i++)if(early.getJSONObject(i).getLong("time")==fresh.getTime())used=true;check(!used,"Fresh GPS prevents fused callback from entering trial route");
  Thread.sleep(15000);Location backup=p("fused",.0101);manager.setTestProviderLocation("fused",backup);Thread.sleep(800);saved=find(db,today,backup.getTime());check(saved.getJSONObject("telemetry").getLong("gpsAgeMs")>=15000,"No-GPS fallback age measured by monotonic clock");
  JSONArray trial=db.day(today,TrackFusion.ID,false).getJSONArray("points");JSONObject fallback=null;for(int i=0;i<trial.length();i++)if(trial.getJSONObject(i).getLong("time")==backup.getTime())fallback=trial.getJSONObject(i);check(fallback==null,"Unconfirmed isolated fallback stays raw, not a fabricated journey");
  JSONArray baseline=db.day(today,TrackVariants.ID,false).getJSONArray("points");used=false;for(int i=0;i<baseline.length();i++)if(baseline.getJSONObject(i).getString("provider").equals("fused"))used=true;check(!used,"Default D remains GPS-only on modern Android");
  h.call("trackStop",new JSONObject());Thread.sleep(600);check(!TrackingService.running,"Pause stops modern foreground service");
  result.putString("stream","PASS "+passed+" modern Android system-provider checks; offline synthetic locations only\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){e.printStackTrace();result.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,result);}finally{getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));if(manager!=null)for(String s:new String[]{"gps","fused"})try{manager.removeTestProvider(s);}catch(Exception ignored){}}}
}
