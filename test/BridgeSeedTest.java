package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import android.location.Location;import org.json.*;import com.insight.quantlife.*;import java.io.*;
/** Dedicated emulator only. No user coordinates or model credentials. */
public class BridgeSeedTest extends Instrumentation{
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try(TrackStore db=new TrackStore(getTargetContext())){
  if(!getTargetContext().getPackageManager().getPackageInfo("com.insight.quantlife",0).versionName.equals("1.3.2"))throw new Exception("Seed requires original1.3.2");
  MainHost host=new MainHost(this);host.open();JSONObject init=(JSONObject)host.call("init",new JSONObject());String date="2026-10-17";db.deleteDay(date);long base=TrackStore.dayStart(date)+12*3600000;int[] seconds={0,30,120,150,368,398},meters={0,30,120,150,425,455};for(int i=0;i<seconds.length;i++)db.add(BeforeGapFixTest.p(base+seconds[i]*1000,meters[i],1.4f),"bridge-fixture");
  JSONObject snap=new JSONObject().put("records",init.getJSONArray("records")).put("wiki",init.getJSONArray("wikiPages")).put("settings",init.getJSONObject("settings")).put("raw",db.diagnostics(date).getJSONArray("rawPoints")).put("gpx",db.gpx(date));try(FileOutputStream f=getTargetContext().openFileOutput("bridge-upgrade-fixture.json",0)){f.write(snap.toString().getBytes("UTF-8"));}
  out.putString("stream","PASS1.3.2 synthetic seed: six points,90-second short gap,218-second long gap; learning/Wiki/settings/raw/GPX snapshot.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL seed: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
