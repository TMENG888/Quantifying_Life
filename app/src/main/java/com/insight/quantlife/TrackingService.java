package com.insight.quantlife;

import android.Manifest;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GnssStatus;
import android.os.SystemClock;
import org.json.JSONObject;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import java.util.UUID;

public class TrackingService extends Service implements LocationListener {
    public static final String PREFS="quantlife-tracking",STOP="com.insight.quantlife.STOP_TRACK";
    public static volatile boolean running=false;
    private LocationManager locations;private TrackStore store;private Location previous;private String session;
    private final Handler handler=new Handler();private boolean subscribed=false;
    private int satellitesUsed=-1;private double meanCn0=Double.NaN;private long gnssTime=0;
    private final GnssStatus.Callback gnss=new GnssStatus.Callback(){@Override public void onSatelliteStatusChanged(GnssStatus status){int count=0;double sum=0;for(int i=0;i<status.getSatelliteCount();i++)if(status.usedInFix(i)){count++;sum+=status.getCn0DbHz(i);}satellitesUsed=count;meanCn0=count>0?sum/count:Double.NaN;gnssTime=SystemClock.elapsedRealtime();}};
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static boolean precise(Context c){return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    public static boolean background(Context c){return Build.VERSION.SDK_INT<29||c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    public static boolean locationEnabled(Context c){LocationManager m=(LocationManager)c.getSystemService(LOCATION_SERVICE);return Build.VERSION.SDK_INT>=28?m.isLocationEnabled():m.isProviderEnabled(LocationManager.GPS_PROVIDER)||m.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}
    private final Runnable heartbeat=new Runnable(){public void run(){if(!running)return;try{
        prefs(TrackingService.this).edit().putLong("heartbeat",System.currentTimeMillis()).apply();
        if(!precise(TrackingService.this)){stopWith("定位权限已撤销，记录停止",true);return;}
        if(!locationEnabled(TrackingService.this)){prefs(TrackingService.this).edit().putString("message","系统定位已关闭，无法获取位置").apply();}
        else if(previous!=null&&System.currentTimeMillis()-previous.getTime()>30000){prefs(TrackingService.this).edit().putString("message","GPS暂时缺失 · 缺失位置不补画，恢复后可能分段").apply();}
        handler.postDelayed(this,30000);
    }catch(Exception e){stopWith("定位服务异常，请重新开启",true);}}};
    @Override public void onCreate(){super.onCreate();locations=(LocationManager)getSystemService(LOCATION_SERVICE);store=new TrackStore(this);}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&STOP.equals(intent.getAction())){stopWith("已暂停记录",true);return START_NOT_STICKY;}
        if(!precise(this)){stopWith("需要精确定位权限，请打开App重新开启",true);return START_NOT_STICKY;}
        if(intent==null&&!prefs(this).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("daily-track","每日轨迹持续记录",NotificationManager.IMPORTANCE_LOW));
            int immutable=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
            PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),immutable);
            PendingIntent pause=PendingIntent.getService(this,1,new Intent(this,TrackingService.class).setAction(STOP),immutable);
            Notification n=new Notification.Builder(this,"daily-track").setSmallIcon(com.insight.quantlife.R.drawable.ic_launcher).setContentTitle("知时 · 全天轨迹记录中").setContentText("位置仅保存在手机 · 点击打开或暂停").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"暂停记录",pause).build()).build();
            if(Build.VERSION.SDK_INT>=29)startForeground(2401,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);else startForeground(2401,n);
            running=true;prefs(this).edit().putBoolean("enabled",true).putString("message","正在等待有效定位").putLong("heartbeat",System.currentTimeMillis()).apply();
            if(!subscribed){session=UUID.randomUUID().toString();previous=null;boolean available=false;
                // Network fixes can jump between cells even when stationary. Do not mix them into a GPS route.
                if(locations.getAllProviders().contains(LocationManager.GPS_PROVIDER)){locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000,0,this);available=true;}
                try{locations.registerGnssStatusCallback(gnss,handler);}catch(Exception ignored){}
                if(!available)throw new IllegalStateException("没有可用定位提供器");subscribed=true;handler.removeCallbacks(heartbeat);handler.post(heartbeat);
            }
            return START_STICKY;
        }catch(Exception e){stopWith("无法开启持续定位，请检查权限并在App前台重试",true);return START_NOT_STICKY;}
    }
    private void stopWith(String message,boolean disable){prefs(this).edit().putString("message",message).putBoolean("enabled",disable?false:prefs(this).getBoolean("enabled",false)).apply();stopForeground(true);stopSelf();running=false;}
    @Override public void onLocationChanged(Location l){try{
        long now=System.currentTimeMillis();long age=l.getElapsedRealtimeNanos()>0?(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1000000:Math.abs(now-l.getTime());if(!TrackStore.valid(l)||!LocationManager.GPS_PROVIDER.equals(l.getProvider())||age< -1000||age>30000||Math.abs(now-l.getTime())>30000){reject("定位无效或已过旧，等待新GPS位置");return;}
        if(previous!=null){long delta=l.getTime()-previous.getTime();if(delta<=0)return;float distance=previous.distanceTo(l);
            if(delta<=300000&&distance/(delta/1000.0)>100){reject("已忽略异常跳点");return;}
            if(delta<4000)return;
        }
        JSONObject telemetry=new JSONObject();telemetry.put("fixAgeMs",age);telemetry.put("requestedIntervalMs",5000);if(gnssTime>0&&!l.isFromMockProvider()){telemetry.put("satellitesUsed",satellitesUsed);telemetry.put("gnssAgeMs",SystemClock.elapsedRealtime()-gnssTime);if(Double.isFinite(meanCn0))telemetry.put("meanCn0",meanCn0);}
        if(store.add(l,session,telemetry)){previous=new Location(l);boolean weak=l.getAccuracy()>35||(!l.isFromMockProvider()&&gnssTime>0&&SystemClock.elapsedRealtime()-gnssTime<=15000&&(satellitesUsed<4||Double.isFinite(meanCn0)&&meanCn0<20));prefs(this).edit().putLong("lastPoint",l.getTime()).putString("message",weak?"GPS信号较弱 · 已保留诊断，不绘制不可信位置":"正在记录 · 信号空缺不连线").apply();}
    }catch(Exception e){prefs(this).edit().putString("message","位置保存失败，请检查手机可用空间").apply();}}
    private void reject(String reason){SharedPreferences p=prefs(this);p.edit().putInt("rejected",p.getInt("rejected",0)+1).putString("message",reason).apply();}
    @Override public void onProviderDisabled(String provider){prefs(this).edit().putString("message","定位信号暂不可用，轨迹可能中断").apply();}
    @Override public void onProviderEnabled(String provider){prefs(this).edit().putString("message","信号恢复，等待有效定位").apply();}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){running=false;handler.removeCallbacksAndMessages(null);if(locations!=null)try{locations.removeUpdates(this);locations.unregisterGnssStatusCallback(gnss);}catch(Exception ignored){}if(store!=null)store.close();super.onDestroy();}
}
