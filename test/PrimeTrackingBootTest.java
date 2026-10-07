package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import org.json.JSONObject;import com.insight.quantlife.TrackingService;
/** Dedicated emulator only. Follow with actual adb reboot and a service-state check. */
public class PrimeTrackingBootTest extends Instrumentation{
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle out=new Bundle();try{
  MainHost host=new MainHost(this);host.open();getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_FINE_LOCATION);getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_COARSE_LOCATION);
  if(android.os.Build.VERSION.SDK_INT>=29)getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
  if(android.os.Build.VERSION.SDK_INT>=33)getUiAutomation().grantRuntimePermission(getTargetContext().getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
  host.call("trackConfig",new JSONObject().put("resumeAfterReboot",true));host.call("trackStart",new JSONObject());for(int i=0;i<40&&!TrackingService.running;i++)Thread.sleep(100);
  if(!TrackingService.running)throw new Exception("Foreground service failed");out.putString("stream","PASS Reboot test primed: explicit resume enabled and background location granted.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL prime reboot: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
