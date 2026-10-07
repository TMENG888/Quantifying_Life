package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import android.location.Location;import android.net.Uri;import java.io.File;import org.json.JSONObject;import com.insight.quantlife.*;
public class OfflineMapUiTest extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  MainHost host=new MainHost(this);host.open();OfflineMap.importFile(getTargetContext(),Uri.fromFile(new File(getTargetContext().getFilesDir(),"test-offline-map.mbtiles")));host.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));
  try(TrackStore db=new TrackStore(getTargetContext())){db.deleteDay("2026-10-09");long time=TrackStore.dayStart("2026-10-09")+9*3600000;for(int i=0;i<=30;i++){Location l=new Location("gps");l.setLatitude(31.23+i*7/111195.0);l.setLongitude(121.47);l.setAccuracy(8);l.setTime(time+i*5000);l.setSpeed(1.4f);l.setSpeedAccuracyMetersPerSecond(.15f);db.add(l,"qa-map");}}
  host.js("document.querySelector('[data-page=tracks]').click()");host.waitText("每天走过的地方");host.js("document.querySelector('#track-date').value='2026-10-09';document.querySelector('#track-date').dispatchEvent(new Event('change'))");if(!host.waitText("离线 OSM 底图"))throw new Exception("Local canvas failed to render imported tiles");if(!host.waitText("0.21"))throw new Exception("Distance display incorrect");
  host.js("document.querySelector('#track-map-mode').scrollIntoView({block:'start'})");host.screenshot("tracks-map-refined.png");
  host.call("trackImportMap",new JSONObject());Thread.sleep(600);getUiAutomation().executeShellCommand("input keyevent 4").close();Thread.sleep(300);if(!OfflineMap.info(getTargetContext()).getBoolean("configured"))throw new Exception("Picker cancellation changed offline file");
  out.putString("stream","PASS Local raster tiles render in real WebView with consent off; distance, attribution, no online requests and file-picker cancellation preserved existing map. Synthetic QA map, not real streets.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL offline map UI: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
