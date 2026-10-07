package com.insight.quantlife.tests;
import android.app.Activity;import android.app.Instrumentation;import android.os.Bundle;import android.view.accessibility.AccessibilityNodeInfo;import android.content.Intent;import org.json.JSONObject;import com.insight.quantlife.TrackingService;
public class TrackingBootSettingsTest extends Instrumentation{
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 boolean clickText(String text)throws Exception{
  for(int i=0;i<80;i++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(!text.contentEquals(node.getText()==null?"":node.getText()))continue;for(int j=0;node!=null&&j<5;j++,node=node.getParent())if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}Thread.sleep(100);}return false;
 }
 @Override public void onStart(){Bundle out=new Bundle();try{
  MainHost host=new MainHost(this);host.open();host.call("trackBackgroundPermission",new JSONObject());if(!clickText("Allow all the time")){if(!clickText("Go to settings")&&!clickText("Allow in settings"))throw new Exception("No system background-location settings entry");if(!clickText("Allow all the time"))throw new Exception("System settings has no always-allow option");}
  Thread.sleep(500);if(!TrackingService.background(getTargetContext()))throw new Exception("System all-time location not granted");
  Intent reopen=new Intent();reopen.setClassName(getTargetContext(),"com.insight.quantlife.MainActivity");reopen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);getTargetContext().startActivity(reopen);Thread.sleep(500);
  host.call("trackConfig",new JSONObject().put("resumeAfterReboot",true));host.call("trackStart",new JSONObject());for(int i=0;i<40&&!TrackingService.running;i++)Thread.sleep(100);
  if(!TrackingService.running)throw new Exception("Foreground service not started");out.putString("stream","PASS Real Android Settings always-allow location granted, reboot restoration opted in, foreground tracker running.\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL system settings reboot prime: "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
