package com.insight.quantlife;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
public class TrackingBootReceiver extends BroadcastReceiver{
    @Override public void onReceive(Context c,Intent i){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction()))return;
        SharedPreferences p=TrackingService.prefs(c);
        if(!p.getBoolean("enabled",false)||!p.getBoolean("resumeAfterReboot",false))return;
        if(!TrackingService.precise(c)||!TrackingService.background(c)){p.edit().putString("message","重启后未恢复：需始终允许定位，请打开App").apply();return;}
        try{c.startForegroundService(new Intent(c,TrackingService.class));}catch(Exception e){p.edit().putString("message","系统阻止自动恢复，请打开App继续记录").apply();}
    }
}
