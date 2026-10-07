package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import android.location.Location;import com.insight.quantlife.TrackStore;
public class BeforeGapFixTest extends Instrumentation {
 boolean reseedCurrent;
 @Override public void onCreate(Bundle b){super.onCreate(b);reseedCurrent="true".equals(b.getString("reseedCurrent"));start();}
 static Location p(long time,double meters,float speed){Location l=new Location("gps");l.setTime(time);l.setLatitude(31.23+meters/111195.0);l.setLongitude(121.47);l.setAccuracy(8);l.setSpeed(speed);l.setSpeedAccuracyMetersPerSecond(.15f);return l;}
 @Override public void onStart(){Bundle out=new Bundle();try(TrackStore db=new TrackStore(getTargetContext())){int version=db.getReadableDatabase().getVersion();if(version!=2&&!(reseedCurrent&&version==3))throw new Exception("Must seed on original1.3/schema2 or explicitly request current-schema repeat fixture");db.deleteDay("2026-10-08");long base=TrackStore.dayStart("2026-10-08")+12*3600000;for(int i=0;i<=24;i++)db.add(p(base+i*5000,0,0),"gap-fixture");db.add(p(base+338000,275,1.4f),"gap-fixture");db.add(p(base+343000,282,1.4f),"gap-fixture");db.add(p(base+348000,289,1.4f),"gap-fixture");out.putString("stream","PASS schema"+version+" fixture: 218-second gap then 14m walk, no real user coordinates.\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){out.putString("stream","FAIL seed: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
