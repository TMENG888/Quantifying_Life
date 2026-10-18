package com.insight.quantlife;
import android.content.Context;
import android.hardware.*;
import android.os.SystemClock;
import android.os.PowerManager;
import org.json.JSONObject;

/** Evidence only. Prefer wake-up sensors; bounded CPU bursts refresh non-wake sensors.
 * No always-on wake lock, no inertial position integration. */
final class MotionSampler implements SensorEventListener {
    private final SensorManager manager;private long last;private double energy;private int count;
    private final PowerManager.WakeLock burst;private long lastBurst;private int burstCount;
    private final double[] gravity=new double[3];private boolean registered,wakeUp,accelerometer;
    private String source="unavailable";
    MotionSampler(Context c){manager=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);burst=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Zhishi:MotionSample");burst.setReferenceCounted(false);}
    synchronized void start(){
        manager.unregisterListener(this);if(burst.isHeld())burst.release();last=0;count=0;energy=0;lastBurst=0;burstCount=0;registered=false;source="unavailable";
        for(int type:new int[]{Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_ACCELEROMETER}){
            Sensor sensor=manager.getDefaultSensor(type,true);
            if(sensor==null)sensor=manager.getDefaultSensor(type);
            if(sensor!=null&&manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_NORMAL)){
                registered=true;wakeUp=sensor.isWakeUpSensor();accelerometer=type==Sensor.TYPE_ACCELEROMETER;
                source=accelerometer?"accelerometerGravityRemoved":"linearAcceleration";break;
            }
        }
    }
    synchronized void stop(){manager.unregisterListener(this);if(burst.isHeld())burst.release();registered=false;last=0;count=0;energy=0;}
    @Override public synchronized void onSensorChanged(SensorEvent e){
        long age=SystemClock.elapsedRealtimeNanos()-e.timestamp;
        if(age<0||age>3000000000L||e.timestamp<=last)return;
        boolean reset=last==0||e.timestamp-last>3000000000L;double square=0;
        for(int i=0;i<3;i++){double v=e.values[i];if(!Double.isFinite(v))return;
            if(accelerometer){if(reset)gravity[i]=v;else gravity[i]=.9*gravity[i]+.1*v;v-=gravity[i];}square+=v*v;
        }
        if(reset){count=0;energy=square;}else energy=.9*energy+.1*square;
        count++;last=e.timestamp;
    }
    synchronized JSONObject snapshot()throws Exception{JSONObject o=new JSONObject();long age=last==0?-1:(SystemClock.elapsedRealtimeNanos()-last)/1000000;
        // A non-wake-up sensor can stop delivering while the CPU sleeps even though
        // GPS callbacks continue. At most 2.5s per 30s, only on a location callback.
        // The current stale sample remains stale: a burst is not movement evidence.
        long now=SystemClock.elapsedRealtime();
        if(registered&&!wakeUp&&(age<0||age>3000)&&(lastBurst==0||now-lastBurst>=30000)){
            try{burst.acquire(2500);lastBurst=now;burstCount++;}catch(SecurityException ignored){}
        }
        o.put("motionAgeMs",age);o.put("motionSource",source);o.put("motionRegistered",registered);o.put("motionWakeUp",wakeUp);o.put("motionSamples",count);
        o.put("motionBurstCount",burstCount);o.put("motionBurstActive",burst.isHeld());o.put("motionBurstIntervalMs",30000);o.put("motionBurstMaxMs",2500);
        boolean available=registered&&count>=10&&age>=0&&age<=3000;o.put("motionAvailable",available);
        if(available)o.put("motionRms",Math.sqrt(energy));return o;
    }
    @Override public void onAccuracyChanged(Sensor s,int accuracy){}
}
