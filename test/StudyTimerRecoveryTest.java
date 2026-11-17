package com.insight.quantlife.tests;
import android.app.Activity;import android.os.*;import org.json.*;
/** Two invocations separated by adb force-stop, on the disposable emulator only. */
public final class StudyTimerRecoveryTest extends DocumentEditorTest {
 @Override public void onStart(){Bundle out=new Bundle();try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  android.content.SharedPreferences p=getTargetContext().getSharedPreferences("study-timer-qa",0);
  if(phase.equals("cleanup-layout-fixture")){
   JSONObject active=((JSONObject)h.call("studyTimer",new JSONObject())).optJSONObject("active");if(active!=null){if(!active.optString("title").isEmpty())throw new Exception("Not an empty layout fixture");h.call("studyTimer",new JSONObject().put("command","discard").put("id",active.getString("id")));}check(true,"Discarded only empty stopwatch left by disposable-emulator layout checks");
  }else if(phase.equals("seed")){
   if(!((JSONObject)h.call("studyTimer",new JSONObject())).isNull("active"))throw new Exception("Existing active timer; refusing to replace");String id="study-recovery-"+System.nanoTime();JSONObject s=((JSONObject)h.call("studyTimer",new JSONObject().put("command","start").put("id",id))).getJSONObject("active");p.edit().putString("id",id).putLong("duration",s.getLong("durationMs")).commit();check(true,"Committed a stopwatch before process termination");
  }else{
   String id=p.getString("id","");JSONObject s=((JSONObject)h.call("studyTimer",new JSONObject())).getJSONObject("active");check(s.getString("id").equals(id)&&s.getString("state").equals("running"),"Force-stopped process restores the same active stopwatch");check(s.getLong("durationMs")>p.getLong("duration",0)+1000,"Time while process was absent is counted from monotonic clock");h.call("studyTimer",new JSONObject().put("command","discard").put("id",id));p.edit().clear().commit();
  }out.putString("stream","PASS StudyTimerRecoveryTest "+phase+": "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable ex){out.putString("stream","FAIL StudyTimerRecoveryTest: "+ex+"\n"+android.util.Log.getStackTraceString(ex));finish(Activity.RESULT_CANCELED,out);}}
}
