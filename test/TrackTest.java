package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import android.os.SystemClock;import android.content.Intent;import android.location.Location;import android.location.LocationManager;import android.location.Criteria;import org.json.JSONObject;import org.json.JSONArray;
import com.insight.quantlife.TrackStore;import com.insight.quantlife.TrackingService;
public class TrackTest extends Instrumentation{
 private MainHost host;private int passed;private LocationManager manager;private TrackStore db;
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean b,String label)throws Exception{if(!b)throw new Exception(label);passed++;Bundle out=new Bundle();out.putString("stream","PASS "+label+"\n");sendStatus(0,out);}
 JSONObject day(String date)throws Exception{return (JSONObject)host.call("trackDay",new JSONObject().put("date",date));}
 Location point(long time,double lat,double lon){Location l=new Location("gps");l.setLatitude(lat);l.setLongitude(lon);l.setAccuracy(8);l.setTime(time);l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());return l;}
 void send(long time,double lat,double lon)throws Exception{manager.setTestProviderLocation("gps",point(time,lat,lon));Thread.sleep(700);}
 @Override public void onStart(){Bundle out=new Bundle();try{
  getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));TrackingService.prefs(getTargetContext()).edit().clear().commit();
  host=new MainHost(this);host.open();JSONObject initial=(JSONObject)host.call("init",new JSONObject());
  check(initial.toString().contains("upgrade-1.1-note"),"1.1 to 1.2 upgrade preserves learning notes");
  check(initial.getJSONObject("settings").getJSONObject("profiles").getJSONObject("custom").getBoolean("keyConfigured"),"Upgrade preserves encrypted model credential");
  check(initial.getJSONArray("wikiPages").length()>0,"Upgrade preserves existing LLM Wiki");
  JSONObject status=day("2026-10-05").getJSONObject("status");check(!status.getBoolean("enabled")&&!status.getBoolean("running"),"Tracking is off by default and does not silently collect");
  check(!status.getBoolean("mapConsent"),"Online maps are disabled by default");
  boolean tileBlocked=false;try{host.call("trackTile",new JSONObject().put("z",1).put("x",0).put("y",0));}catch(Exception e){tileBlocked=true;}check(tileBlocked,"Native map fetch requires explicit map consent");
  db=new TrackStore(getTargetContext());JSONArray cleanup=db.dates();for(int i=0;i<cleanup.length();i++)db.deleteDay(cleanup.getString(i));
  long base=TrackStore.dayStart("2026-10-05")+9*3600000;for(int i=0;i<12;i++)db.add(point(base+i*60000,31.23,121.47),"s1");db.add(point(base+12*60000,31.231,121.47),"s1");db.add(point(base+13*60000,31.232,121.47),"s1");db.add(point(base+30*60000,31.24,121.48),"s2");
  db.add(point(TrackStore.dayStart("2026-10-05")-1,30,120),"prior");
  check(day("2026-10-05").getJSONArray("points").length()==15,"Daily SQLite query returns exact selected-day points");
  check(day("2026-10-04").getJSONArray("points").length()==1,"Midnight boundary keeps previous-day point separate");
  Location invalid=point(base,91,121);check(!db.add(invalid,"s1"),"Native store rejects invalid coordinates");
  boolean invalidDay=false;try{day("2026-02-30");}catch(Exception e){invalidDay=true;}check(invalidDay,"Native date validation rejects impossible days");
  check(day("2026-10-05").getJSONArray("dates").length()==2,"Recorded days index comes from location database");
  String gpx=db.gpx("2026-10-05");check(gpx.contains("xmlns=\"http://www.topografix.com/GPX/1/1\"")&&gpx.split("<trkpt ",-1).length-1==15,"GPX contains precise WGS84 points and standard namespace");
  check(gpx.split("<trkseg>",-1).length-1==2,"GPX does not connect paused sessions");
  check(!initial.has("points")&&!initial.has("tracks"),"Raw location points are excluded from AI initialization context");
  host.js("document.querySelector('[data-page=tracks]').click()");check(host.waitText("每天走过的地方"),"Six-tab navigation opens trajectory page");
  host.js("document.querySelector('#track-date').value='2026-10-05';document.querySelector('#track-date').dispatchEvent(new Event('change'))");check(host.waitText("09:00"),"Historical day route and times render");
  check(host.js("LifeTracks.summary("+day("2026-10-05").getJSONArray("points").toString()+").stops.length").equals("1"),"Real WebView calculates evidenced 11-minute stop");
  check(host.js("document.querySelector('#track-map').width>0&&document.querySelector('#track-map').height>0").equals("true"),"Local route canvas renders without network or model key");
  host.js("document.querySelector('#track-map').scrollIntoView({block:'start'})");host.screenshot("tracks-offline.png");
  host.test.runOnMainSync(()->host.web.reload());check(host.waitText("今天的投入"),"Restart reloads existing learning/work data");
  check(day("2026-10-05").getJSONArray("points").length()==15,"Track history persists independently after reload");
  host.js("document.querySelector('[data-page=tracks]').click()");
  getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_FINE_LOCATION);
  getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_COARSE_LOCATION);
  if(android.os.Build.VERSION.SDK_INT>=33)getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
  manager=(LocationManager)getTargetContext().getSystemService(android.content.Context.LOCATION_SERVICE);
  manager.addTestProvider("gps",false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);manager.setTestProviderEnabled("gps",true);
  host.call("trackStart",new JSONObject());for(int i=0;i<30&&!TrackingService.running;i++)Thread.sleep(150);check(TrackingService.running,"Location foreground service starts only after opt-in and permission");
  long now=System.currentTimeMillis();send(now-20000,31.22,121.46);String today=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(new java.util.Date());int count=day(today).getJSONArray("points").length();
  check(count>0,"Real LocationManager callback persists a simulated GPS point");
  android.app.NotificationManager nm=(android.app.NotificationManager)getTargetContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);check(nm.getActiveNotifications().length>0,"Ongoing location notification is present");
  Intent home=new Intent(Intent.ACTION_MAIN);home.addCategory(Intent.CATEGORY_HOME);home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);getTargetContext().startActivity(home);getUiAutomation().executeShellCommand("input keyevent 223").close();Thread.sleep(31000);send(System.currentTimeMillis(),31.2205,121.46);
  check(TrackingService.running&&day(today).getJSONArray("points").length()>count,"Location updates persist while screen is locked and Activity is backgrounded");
  int backgroundCount=day(today).getJSONArray("points").length();
  Intent stop=new Intent(getTargetContext(),TrackingService.class).setAction(TrackingService.STOP);getTargetContext().startService(stop);Thread.sleep(800);
  check(!TrackingService.running&&!day(today).getJSONObject("status").getBoolean("enabled"),"Notification pause action stops service and clears desired tracking");
  send(now-30000,31.221,121.46);check(day(today).getJSONArray("points").length()==backgroundCount,"Pausing prevents further GPS points from being stored");
  getUiAutomation().executeShellCommand("input keyevent 224").close();getUiAutomation().executeShellCommand("wm dismiss-keyguard").close();
  Intent reopen=new Intent();reopen.setClassName(getTargetContext(),"com.insight.quantlife.MainActivity");reopen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);getTargetContext().startActivity(reopen);Thread.sleep(500);host.js("document.querySelector('#track-refresh').click()");check(host.waitText("已暂停记录"),"Paused state is visible when reopening app");
  host.call("trackConfig",new JSONObject().put("resumeAfterReboot",true));new com.insight.quantlife.TrackingBootReceiver().onReceive(getTargetContext(),new Intent(Intent.ACTION_BOOT_COMPLETED));Thread.sleep(300);check(!TrackingService.running,"Boot receiver does not resume user-paused recording");
  host.call("trackConfig",new JSONObject().put("resumeAfterReboot",false));
  db.deleteDay("2026-10-04");check(day("2026-10-04").getJSONArray("points").length()==0,"Deleting a day removes that day's raw locations");
  check(((JSONObject)host.call("init",new JSONObject())).toString().contains("upgrade-1.1-note"),"Deleting tracks never deletes learning/work/Wiki");
  host.screenshot("tracks-current.png");
  out.putString("stream","\nPASS: "+passed+" Android trajectory checks (API"+android.os.Build.VERSION.SDK_INT+").\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){String body="";try{body=host.js("document.body.innerText+'\\nTOAST:'+document.querySelector('#toast').textContent");}catch(Exception ignored){}out.putString("stream","FAIL after "+passed+": "+e+"\n"+body+"\n");finish(Activity.RESULT_CANCELED,out);}
 finally{if(manager!=null)try{manager.removeTestProvider("gps");}catch(Exception ignored){}if(db!=null)db.close();}}
}
