package com.insight.quantlife.tests;
import android.app.*;import android.os.Bundle;import org.json.*;import java.io.*;import com.insight.quantlife.*;
public class BeforeExaUpgradeTest extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{MainHost h=new MainHost(this);h.open();JSONObject snap=(JSONObject)h.call("init",new JSONObject());snap.put("secureCiphertext",getTargetContext().getSharedPreferences("secure",0).getString("settings",""));try(TrackStore db=new TrackStore(getTargetContext())){snap.put("gpx",db.gpx("2026-10-17"));snap.put("raw",db.diagnostics("2026-10-17").getJSONArray("rawPoints"));}try(FileOutputStream f=getTargetContext().openFileOutput("exa-upgrade-snapshot.json",0)){f.write(snap.toString().getBytes("UTF-8"));}out.putString("stream","PASS 1.4.0 data/settings snapshot for Exa upgrade\n");finish(Activity.RESULT_OK,out);}catch(Throwable e){out.putString("stream","FAIL "+e);finish(Activity.RESULT_CANCELED,out);}}
}
