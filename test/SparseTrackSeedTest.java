package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import android.location.Location;import org.json.*;import com.insight.quantlife.*;import java.io.*;
/** Only run on the dedicated emulator with original1.3.1. Synthetic coordinates, not user files. */
public class SparseTrackSeedTest extends Instrumentation{
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try(TrackStore db=new TrackStore(getTargetContext())){
  if(!getTargetContext().getPackageManager().getPackageInfo("com.insight.quantlife",0).versionName.equals("1.3.1"))throw new Exception("Seed must run on original1.3.1");
  MainHost host=new MainHost(this);host.open();JSONObject init=(JSONObject)host.call("init",new JSONObject());JSONObject snapshot=new JSONObject().put("learningCount",init.getJSONArray("records").length()).put("wikiCount",init.getJSONArray("wikiPages").length()).put("settings",init.getJSONObject("settings"));
  String date="2026-10-11";db.deleteDay(date);long base=TrackStore.dayStart(date)+12*3600000;for(int i=0;i<=10;i++){Location l=BeforeGapFixTest.p(base+i*31000,i*40,0);l.removeSpeed();l.removeSpeedAccuracy();db.add(l,"sparse-fixture");}
  Location network=BeforeGapFixTest.p(base+350000,800,0);network.setProvider("network");network.removeSpeed();network.removeSpeedAccuracy();db.add(network,"sparse-fixture");Location poor=BeforeGapFixTest.p(base+360000,400,0);poor.setAccuracy(60);db.add(poor,"sparse-fixture");
  JSONObject original=db.day(date);snapshot.put("oldShownPoints",original.getJSONArray("points").length());snapshot.put("rawCount",original.getInt("totalPoints"));snapshot.put("oldMeters",host.js("LifeTracks.summary("+original.getJSONArray("points")+").meters"));
  try(FileOutputStream file=getTargetContext().openFileOutput("sparse-upgrade-fixture.json",0)){file.write(snapshot.toString().getBytes("UTF-8"));}
  out.putString("stream","PASS synthetic1.3.1 upgrade fixture: raw13, old distance "+snapshot.getString("oldMeters")+"m; saved record/Wiki/settings snapshot.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL seed: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
