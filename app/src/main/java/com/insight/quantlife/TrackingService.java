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
    private LocationManager locations;private TrackStore store;private Location previous;private String session;private MotionSampler motion;private long lastGpsElapsed=0,lastSubscribeElapsed=0;private final java.util.Map<String,Long> sourceTimes=new java.util.HashMap<>();
    private final Handler handler=new Handler();private boolean subscribed=false;
    private int satellitesUsed=-1;private double meanCn0=Double.NaN;private long gnssTime=0;
    private final GnssStatus.Callback gnss=new GnssStatus.Callback(){@Override public void onSatelliteStatusChanged(GnssStatus status){int count=0;double sum=0;for(int i=0;i<status.getSatelliteCount();i++)if(status.usedInFix(i)){count++;sum+=status.getCn0DbHz(i);}satellitesUsed=count;meanCn0=count>0?sum/count:Double.NaN;gnssTime=SystemClock.elapsedRealtime();}};
    public static synchronized SharedPreferences prefs(Context c){SharedPreferences p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);int revision=p.getInt("processingRevision",0);if(revision<TrackFusion.REVISION){SharedPreferences.Editor edit=p.edit().putInt("processingRevision",TrackFusion.REVISION);if(revision<8)edit.putString("filterMode",TrackFusion.ID);edit.apply();}return p;}
    public static boolean precise(Context c){return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    public static boolean background(Context c){return Build.VERSION.SDK_INT<29||c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    public static boolean locationEnabled(Context c){LocationManager m=(LocationManager)c.getSystemService(LOCATION_SERVICE);return Build.VERSION.SDK_INT>=28?m.isLocationEnabled():m.isProviderEnabled(LocationManager.GPS_PROVIDER)||m.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}
    private final Runnable heartbeat=new Runnable(){public void run(){if(!running)return;try{
        prefs(TrackingService.this).edit().putLong("heartbeat",System.currentTimeMillis()).apply();
        if(!precise(TrackingService.this)){stopWith("定位权限已撤销，记录停止",true);return;}
        if(!locationEnabled(TrackingService.this)){prefs(TrackingService.this).edit().putString("message","系统定位已关闭，无法获取位置").apply();}
        else if(previous==null||System.currentTimeMillis()-previous.getTime()>30000){prefs(TrackingService.this).edit().putString("message","GPS暂时缺失 · 没有新采样，缺口只作虚线参考").apply();if(SystemClock.elapsedRealtime()-lastSubscribeElapsed>=120000){subscribeLocations();SharedPreferences p=prefs(TrackingService.this);p.edit().putInt("resubscriptions",p.getInt("resubscriptions",0)+1).apply();}}
        handler.postDelayed(this,30000);
    }catch(Exception e){stopWith("定位服务异常，请重新开启",true);}}};
    @Override public void onCreate(){super.onCreate();locations=(LocationManager)getSystemService(LOCATION_SERVICE);store=new TrackStore(this);motion=new MotionSampler(this);}
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
                if(locations.getAllProviders().contains(LocationManager.GPS_PROVIDER)){subscribeLocations();available=true;}
                // Android system fused only: no Google Play dependency or network provider subscription.
                motion.start();sourceTimes.clear();lastGpsElapsed=0;
                try{locations.registerGnssStatusCallback(gnss,handler);}catch(Exception ignored){}
                if(!available)throw new IllegalStateException("没有可用定位提供器");subscribed=true;handler.removeCallbacks(heartbeat);handler.post(heartbeat);
            }
            return START_STICKY;
        }catch(Exception e){stopWith("无法开启持续定位，请检查权限并在App前台重试",true);return START_NOT_STICKY;}
    }
    private void subscribeLocations(){locations.removeUpdates(this);locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,0,this);if(Build.VERSION.SDK_INT>=31&&locations.getAllProviders().contains("fused"))try{locations.requestLocationUpdates("fused",5000,0,this);}catch(Exception ignored){}lastSubscribeElapsed=SystemClock.elapsedRealtime();}
    private void stopWith(String message,boolean disable){prefs(this).edit().putString("message",message).putBoolean("enabled",disable?false:prefs(this).getBoolean("enabled",false)).apply();stopForeground(true);stopSelf();running=false;}
    @Override public void onLocationChanged(Location l){try{
        long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();long age=l.getElapsedRealtimeNanos()>0?(SystemClock.elapsedRealtimeNanos()-l.getElapsedRealtimeNanos())/1000000:Math.abs(now-l.getTime());if(!TrackStore.rawValid(l)){reject("无效坐标未保存");return;}
        boolean gps=LocationManager.GPS_PROVIDER.equals(l.getProvider());String source=l.getProvider()==null?"unknown":l.getProvider();
        boolean fresh=age>=-1000&&age<=30000&&Math.abs(now-l.getTime())<=30000;
        Long priorTime=sourceTimes.get(source);boolean ordered=priorTime==null||l.getTime()>priorTime;boolean eligible=fresh&&ordered&&(gps||"fused".equals(source));
        if(fresh&&ordered)sourceTimes.put(source,l.getTime());
        long gpsAge=lastGpsElapsed==0?Long.MAX_VALUE:elapsed-lastGpsElapsed;
        if(gps&&eligible&&l.getAccuracy()<=65){lastGpsElapsed=elapsed;gpsAge=0;}
        JSONObject telemetry=motion.snapshot();telemetry.put("fixAgeMs",age);telemetry.put("requestedIntervalMs",gps?2000:5000);telemetry.put("eligible",eligible);telemetry.put("gpsAgeMs",gpsAge);telemetry.put("capturePolicy","raw-v2");
        if(!eligible)telemetry.put("excludedReason",!fresh?"stale":"duplicate-or-provider");if(gnssTime>0&&!l.isFromMockProvider()){telemetry.put("satellitesUsed",satellitesUsed);telemetry.put("gnssAgeMs",SystemClock.elapsedRealtime()-gnssTime);if(Double.isFinite(meanCn0))telemetry.put("meanCn0",meanCn0);}
        if(store.add(l,session,telemetry)){if(eligible&&gps)previous=new Location(l);boolean poor=l.getAccuracy()>TrackVariants.MAX_ACCURACY;SharedPreferences.Editor edit=prefs(this).edit();if(eligible)edit.putLong("lastPoint",l.getTime());edit.putString("message",!eligible?"回调已保存，过旧或重复点不计入轨迹":poor?"弱定位已保存，暂不纳入轨迹":"正在记录 · 原始定位保存在本机").apply();}
    }catch(Exception e){prefs(this).edit().putString("message","位置保存失败，请检查手机可用空间").apply();}}
    private void reject(String reason){SharedPreferences p=prefs(this);p.edit().putInt("rejected",p.getInt("rejected",0)+1).putString("message",reason).apply();}
    @Override public void onProviderDisabled(String provider){prefs(this).edit().putString("message","定位信号暂不可用，没有新采样；缺口只作虚线参考").apply();}
    @Override public void onProviderEnabled(String provider){prefs(this).edit().putString("message","信号恢复，等待有效定位").apply();}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){running=false;handler.removeCallbacksAndMessages(null);if(locations!=null)try{locations.removeUpdates(this);locations.unregisterGnssStatusCallback(gnss);}catch(Exception ignored){}if(motion!=null)motion.stop();if(store!=null)store.close();super.onDestroy();}
}
