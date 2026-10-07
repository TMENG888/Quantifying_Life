package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import android.database.sqlite.SQLiteDatabase;import android.content.ContentValues;
public class BeforeTrackRefinementTest extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{MainHost host=new MainHost(this);host.open();host.call("trackDay",new org.json.JSONObject().put("date","2026-10-05"));
  try(SQLiteDatabase db=getTargetContext().openOrCreateDatabase("quantlife-tracks.db",0,null)){
   if(db.getVersion()!=1)throw new Exception("Seed must run on original 1.2 schema1");db.delete("points","session=?",new String[]{"legacy-drift"});
   long time=com.insight.quantlife.TrackStore.dayStart("2026-10-05")+12*3600000;
   for(int i=0;i<60;i++){ContentValues v=new ContentValues();v.put("time",time+i*30000);v.put("lat",31.23+(i==0?0:(i%2==0?65:-65))/111195.0);v.put("lon",121.47);v.put("accuracy",8);v.put("session","legacy-drift");v.put("provider","gps");v.put("mocked",1);db.insertOrThrow("points",null,v);}
  }out.putString("stream","PASS original 1.2 schema1 speedless drift history seeded\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL old-version fixture: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
