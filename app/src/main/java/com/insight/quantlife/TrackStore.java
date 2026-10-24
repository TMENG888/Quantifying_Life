package com.insight.quantlife;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.Locale;

/** Separate local database: adding tracking cannot migrate or erase learning/work data. */
public class TrackStore extends SQLiteOpenHelper {
    private final Context context;
    public TrackStore(Context c){super(c.getApplicationContext(),"quantlife-tracks.db",null,3);context=c.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE points (id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,accuracy REAL NOT NULL,session TEXT NOT NULL,provider TEXT NOT NULL,mocked INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX point_time ON points(time)");
        db.execSQL("ALTER TABLE points ADD COLUMN speed REAL");db.execSQL("ALTER TABLE points ADD COLUMN speed_accuracy REAL");
        db.execSQL("ALTER TABLE points ADD COLUMN telemetry TEXT");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){if(old<1||next!=3)throw new IllegalStateException("Unsupported track schema migration");if(old<2){db.execSQL("ALTER TABLE points ADD COLUMN speed REAL");db.execSQL("ALTER TABLE points ADD COLUMN speed_accuracy REAL");}if(old<3)db.execSQL("ALTER TABLE points ADD COLUMN telemetry TEXT");}
    public static long dayStart(String date)throws Exception{
        if(!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new Exception("轨迹日期无效");
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);ParsePosition p=new ParsePosition(0);java.util.Date d=f.parse(date,p);
        if(d==null||p.getIndex()!=date.length())throw new Exception("轨迹日期不存在");return d.getTime();
    }
    public static long dayEnd(String date)throws Exception{java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(dayStart(date));c.add(java.util.Calendar.DATE,1);return c.getTimeInMillis();}
    public synchronized boolean add(Location l,String session)throws Exception{
        return add(l,session,new JSONObject());
    }
    public synchronized boolean add(Location l,String session,JSONObject telemetry)throws Exception{
        if(!rawValid(l)||session==null||session.isEmpty())return false;
        ContentValues v=new ContentValues();v.put("time",l.getTime());v.put("lat",l.getLatitude());v.put("lon",l.getLongitude());v.put("accuracy",l.getAccuracy());v.put("session",session);v.put("provider",l.getProvider()==null?"unknown":l.getProvider());v.put("mocked",l.isFromMockProvider()?1:0);
        if(l.hasSpeed()&&Float.isFinite(l.getSpeed())&&l.getSpeed()>=0)v.put("speed",l.getSpeed());if(l.hasSpeedAccuracy()&&Float.isFinite(l.getSpeedAccuracyMetersPerSecond())&&l.getSpeedAccuracyMetersPerSecond()>=0)v.put("speed_accuracy",l.getSpeedAccuracyMetersPerSecond());
        JSONObject details=new JSONObject(telemetry.toString());if(l.getElapsedRealtimeNanos()>0)details.put("elapsedRealtimeNanos",l.getElapsedRealtimeNanos());if(l.hasBearing()&&Float.isFinite(l.getBearing()))details.put("bearing",l.getBearing());if(l.hasBearingAccuracy()&&Float.isFinite(l.getBearingAccuracyDegrees()))details.put("bearingAccuracy",l.getBearingAccuracyDegrees());v.put("telemetry",details.toString());
        return getWritableDatabase().insertOrThrow("points",null,v)!=-1;
    }
    public static boolean valid(Location l){return l!=null&&Double.isFinite(l.getLatitude())&&Double.isFinite(l.getLongitude())&&Math.abs(l.getLatitude())<=90&&Math.abs(l.getLongitude())<=180&&l.hasAccuracy()&&Float.isFinite(l.getAccuracy())&&l.getAccuracy()>0&&l.getAccuracy()<=100&&l.getTime()>0;}
    public static boolean rawValid(Location l){return l!=null&&Double.isFinite(l.getLatitude())&&Double.isFinite(l.getLongitude())&&Math.abs(l.getLatitude())<=90&&Math.abs(l.getLongitude())<=180&&l.hasAccuracy()&&Float.isFinite(l.getAccuracy())&&l.getAccuracy()>0&&l.getAccuracy()<=1000&&l.getTime()>0;}
    public synchronized JSONObject day(String date)throws Exception{return day(date,TrackingService.prefs(context).getString("filterMode",TrackFusion.ID),true);}
    public synchronized JSONObject day(String date,String policy,boolean display)throws Exception{
        if(!policy.equals(TrackVariants.ID)&&!policy.equals(TrackFusion.ID))throw new Exception("轨迹处理方案无效");
        long a=dayStart(date),z=dayEnd(date);JSONArray points=new JSONArray();int total=0;java.util.List<TrackFilter.Point> raw=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,time,lat,lon,accuracy,session,provider,mocked,speed,speed_accuracy,telemetry FROM points WHERE time>=? AND time<? ORDER BY time,id",new String[]{String.valueOf(a-3*TrackFilter.GAP),String.valueOf(z+2*TrackFilter.GAP)})){
            while(c.moveToNext()){long time=c.getLong(1);if(time>=a&&time<z)total++;if(raw.size()<120200){TrackFilter.Point p=new TrackFilter.Point();p.id=c.getLong(0);p.time=time;p.lat=c.getDouble(2);p.lon=c.getDouble(3);p.accuracy=c.getDouble(4);p.session=c.getString(5);p.provider=c.getString(6);p.mocked=c.getInt(7)==1;if(!c.isNull(8))p.speed=c.getDouble(8);if(!c.isNull(9))p.speedAccuracy=c.getDouble(9);if(!c.isNull(10)){JSONObject t=new JSONObject(c.getString(10));p.satellitesUsed=t.optInt("satellitesUsed",-1);p.meanCn0=t.optDouble("meanCn0",Double.NaN);p.gnssAgeMs=t.optLong("gnssAgeMs",-1);p.eligible=t.optBoolean("eligible",true);p.motionRms=t.optDouble("motionRms",Double.NaN);p.motionAgeMs=t.optLong("motionAgeMs",-1);p.gpsAgeMs=t.optLong("gpsAgeMs",-1);}raw.add(p);}}
        }
        int nonGps=0,poor=0,reliable=0;for(TrackFilter.Point p:raw)if(p.time>=a&&p.time<z){if(!"gps".equals(p.provider))nonGps++;if(p.accuracy>TrackVariants.MAX_ACCURACY)poor++;if(p.reliableSpeed())reliable++;}
        boolean fusion=policy.equals(TrackFusion.ID);java.util.List<TrackFilter.Point> view=new java.util.ArrayList<>();
        for(TrackFilter.Point p:fusion?TrackFusion.apply(raw):TrackVariants.apply(raw))if(p.time>=a&&p.time<z)view.add(p);
        java.util.List<TrackFilter.Point> shown=display?TrackDisplay.simplify(view):view;
        for(TrackFilter.Point p:shown){JSONObject o=new JSONObject();o.put("id",p.id);o.put("time",p.time);o.put("lat",p.lat);o.put("lon",p.lon);o.put("accuracy",p.accuracy);o.put("reportedAccuracy",p.reportedAccuracy);o.put("session",p.session);o.put("provider",p.provider);o.put("mocked",p.mocked);o.put("denoised",false);o.put("pathDistance",true);o.put("filtered",p.filtered);o.put("motionState",p.motionState);o.put("breakBefore",p.breakBefore);if(Double.isFinite(p.speed))o.put("speed",p.speed);if(Double.isFinite(p.reportedSpeed))o.put("reportedSpeed",p.reportedSpeed);if(Double.isFinite(p.speedAccuracy))o.put("speedAccuracy",p.speedAccuracy);points.put(o);}
        int stops=0,moves=0,uncertain=0,freshMotion=0;for(TrackFilter.Point p:view){if(p.motionState.equals("stationary_estimate"))stops++;if(p.motionState.equals("moving"))moves++;if(p.motionState.equals("uncertain"))uncertain++;}for(TrackFilter.Point p:raw)if(p.time>=a&&p.time<z&&Double.isFinite(p.motionRms)&&p.motionAgeMs>=0&&p.motionAgeMs<=3000)freshMotion++;
        JSONObject quality=new JSONObject().put("nonGpsPoints",nonGps).put("poorAccuracyPoints",poor).put("reliableSpeedPoints",reliable).put("excludedPoints",Math.max(0,total-view.size())).put("countsComplete",total<=120000).put("stationaryEstimatePoints",stops).put("movingPoints",moves).put("uncertainPoints",uncertain).put("freshMotionPoints",freshMotion);
        JSONObject filter=new JSONObject().put("id",policy).put("label",fusion?TrackFusion.LABEL:TrackVariants.LABEL).put("color",fusion?TrackFusion.COLOR:TrackVariants.COLOR).put("maxAccuracy",TrackVariants.MAX_ACCURACY).put("estimatedMeters",Math.round(TrackVariants.meters(view)));
        JSONObject out=new JSONObject();out.put("date",date);out.put("points",points);out.put("quality",quality);out.put("totalPoints",total);out.put("truncated",total>120000);out.put("processedPoints",view.size());out.put("displaySimplified",shown.size()<view.size());out.put("estimatedMeters",Math.round(TrackVariants.meters(view)));out.put("processedGaps",TrackVariants.gaps(view));
        if(display){java.util.List<TrackFilter.Point> other=new java.util.ArrayList<>();for(TrackFilter.Point p:fusion?TrackVariants.apply(raw):TrackFusion.apply(raw))if(p.time>=a&&p.time<z)other.add(p);out.put("comparison",new JSONObject().put("id",fusion?TrackVariants.ID:TrackFusion.ID).put("label",fusion?TrackVariants.LABEL:TrackFusion.LABEL).put("points",other.size()).put("meters",Math.round(TrackVariants.meters(other))).put("gaps",TrackVariants.gaps(other)));}out.put("filter",filter);out.put("filterVersion",TrackFusion.REVISION);out.put("maxGapSeconds",60);return out;
    }
    public synchronized JSONObject diagnostics(String date)throws Exception{
        long a=dayStart(date),z=dayEnd(date);JSONArray raw=new JSONArray();int total=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,time,lat,lon,accuracy,session,provider,mocked,speed,speed_accuracy,telemetry FROM points WHERE time>=? AND time<? ORDER BY time,id",new String[]{String.valueOf(a),String.valueOf(z)})){
            while(c.moveToNext()){total++;if(raw.length()>=120000)continue;JSONObject p=new JSONObject();p.put("id",c.getLong(0));p.put("time",c.getLong(1));p.put("lat",c.getDouble(2));p.put("lon",c.getDouble(3));p.put("accuracy",c.getDouble(4));p.put("session",c.getString(5));p.put("provider",c.getString(6));p.put("mocked",c.getInt(7)==1);p.put("speed",c.isNull(8)?JSONObject.NULL:c.getDouble(8));p.put("speedAccuracy",c.isNull(9)?JSONObject.NULL:c.getDouble(9));p.put("telemetry",c.isNull(10)?JSONObject.NULL:new JSONObject(c.getString(10)));raw.put(p);}
        }
        JSONObject filtered=day(date,TrackingService.prefs(context).getString("filterMode",TrackFusion.ID),false);JSONArray decisions=new JSONArray();java.util.Map<Long,JSONObject> kept=new java.util.HashMap<>();JSONArray processed=filtered.getJSONArray("points");for(int i=0;i<processed.length();i++){JSONObject p=processed.getJSONObject(i);kept.put(p.getLong("id"),p);}
        boolean gpsOnly=filtered.getJSONObject("filter").getString("id").equals(TrackVariants.ID);
        for(int i=0;i<raw.length();i++){JSONObject p=raw.getJSONObject(i),t=p.optJSONObject("telemetry"),accepted=kept.get(p.getLong("id"));String reason;
            if(accepted!=null){String state=accepted.optString("motionState","unknown");reason=state.equals("unknown")?"accepted_observation":state;}
            else if(p.optBoolean("mocked"))reason="rejected_mock";
            else if(t!=null&&!t.optBoolean("eligible",true))reason=t.optString("excludedReason","rejected_capture");
            else if(gpsOnly&&!p.optString("provider").equals("gps"))reason="ignored_source";
            else if(p.optDouble("accuracy",Double.NaN)>65)reason="rejected_accuracy";
            else reason="excluded_by_policy"; // Do not invent an exact outlier cause without a filter trace.
            decisions.put(new JSONObject().put("id",p.getLong("id")).put("status",reason).put("retained",accepted!=null).put("breakBefore",accepted!=null&&accepted.optBoolean("breakBefore")));
        }
        return new JSONObject().put("format","zhishi-track-diagnostics").put("version",1).put("appVersion",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName).put("date",date).put("coordinateSystem","WGS84").put("mapProjection","EPSG:3857").put("rawPoints",raw).put("processingDecisions",decisions).put("totalRawPoints",total).put("rawTruncated",total>raw.length()).put("collection",new JSONObject().put("rawPolicy","raw-v2").put("requestedGpsIntervalMs",2000).put("rawMaxAccuracy",1000).put("processingLimit",120000).put("resubscriptions",TrackingService.prefs(context).getInt("resubscriptions",0)).put("inertialNavigation",false).put("roadMatching",false)).put("filtered",filtered);
    }
    public synchronized JSONArray dates(){JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT strftime('%Y-%m-%d',time/1000,'unixepoch','localtime') AS day FROM points ORDER BY day DESC",null)){while(c.moveToNext())out.put(c.getString(0));}return out;}
    public synchronized int deleteDay(String date)throws Exception{SQLiteDatabase db=getWritableDatabase();if(!TrackFeedback.HISTORY_LOCK.writeLock().tryLock())throw new Exception("正在导出全部定位，请等待完成后再删除");try{return db.delete("points","time>=? AND time<?",new String[]{String.valueOf(dayStart(date)),String.valueOf(dayEnd(date))});}finally{TrackFeedback.HISTORY_LOCK.writeLock().unlock();}}
    public synchronized String gpx(String date)throws Exception{return gpx(date,false);}
    public synchronized String gpx(String date,boolean original)throws Exception{
        JSONObject data;
        if(original){JSONArray raw=new JSONArray();long start=dayStart(date),end=dayEnd(date);try(Cursor c=getReadableDatabase().rawQuery("SELECT time,lat,lon,session FROM points WHERE time>=? AND time<? ORDER BY time,id",new String[]{String.valueOf(start),String.valueOf(end)})){while(c.moveToNext()){if(raw.length()>=120000)throw new Exception("原始点超过导出限制，请分段导出");raw.put(new JSONObject().put("time",c.getLong(0)).put("lat",c.getDouble(1)).put("lon",c.getDouble(2)).put("session",c.getString(3)));}}data=new JSONObject().put("points",raw).put("truncated",false);}
        else data=day(date,TrackingService.prefs(context).getString("filterMode",TrackFusion.ID),false);if(data.getBoolean("truncated"))throw new Exception("当天点数超过导出限制，请联系开发者分段导出");
        JSONArray points=data.getJSONArray("points");StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"Zhishi\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>知时 "+date+"</name>");
        String session="";long last=0;boolean open=false;SimpleDateFormat utc=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);utc.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        for(int i=0;i<points.length();i++){JSONObject p=points.getJSONObject(i);long time=p.getLong("time");if(!open||!session.equals(p.getString("session"))||time<=last||time-last>TrackFilter.GAP||p.optBoolean("breakBefore")){if(open)s.append("</trkseg>");s.append("<trkseg>");open=true;}session=p.getString("session");last=time;s.append("<trkpt lat=\"").append(p.getDouble("lat")).append("\" lon=\"").append(p.getDouble("lon")).append("\"><time>").append(utc.format(new java.util.Date(time))).append("</time></trkpt>");}
        if(open)s.append("</trkseg>");return s.append("</trk></gpx>").toString();
    }
}
