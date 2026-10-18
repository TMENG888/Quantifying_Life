package com.insight.quantlife;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** All-history feedback, bounded pages, no filtering, model calls or in-memory archive.
 * Freeze an insertion-ID ceiling; recording may continue. Deletions are excluded until finished. */
public final class TrackFeedback {
    static final ReentrantReadWriteLock HISTORY_LOCK=new ReentrantReadWriteLock();
    public interface Progress {void update(long done,long total)throws Exception;}
    private static final int PAGE=1000;
    private static void checkInterrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("定位导出已中断");}
    private static void json(ZipOutputStream zip,String name,JSONObject object)throws Exception{
        zip.putNextEntry(new ZipEntry(name));zip.write(object.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
    /** Owns/finishes the output stream; absence of a complete manifest means an invalid export. */
    public static JSONObject write(Context context,TrackStore store,OutputStream target,Progress progress)throws Exception{
        SQLiteDatabase db=store.getReadableDatabase();HISTORY_LOCK.readLock().lockInterruptibly();
        try{
            long ceiling,total,first,last,started=System.currentTimeMillis();
            try(Cursor c=db.rawQuery("SELECT COALESCE(MAX(id),0),COUNT(*),MIN(time),MAX(time) FROM points",null)){
                c.moveToFirst();ceiling=c.getLong(0);total=c.getLong(1);first=c.isNull(2)?0:c.getLong(2);last=c.isNull(3)?0:c.getLong(3);
            }
            TimeZone zone=TimeZone.getDefault();SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd",Locale.US);date.setTimeZone(zone);
            android.content.pm.PackageInfo app=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            JSONObject header=new JSONObject().put("format","zhishi-track-feedback").put("version",1).put("appVersion",app.versionName).put("appVersionCode",app.versionCode)
                .put("exportStartedAt",started).put("snapshotMaxId",ceiling).put("snapshotRawPoints",total).put("firstTime",first==0?JSONObject.NULL:first).put("lastTime",last==0?JSONObject.NULL:last)
                .put("timeZone",zone.getID()).put("coordinateSystem","WGS84").put("timeUnit","unix-milliseconds").put("rawFile","raw-points.jsonl").put("rawOrder","id-ascending")
                .put("device",new JSONObject().put("manufacturer",android.os.Build.MANUFACTURER).put("model",android.os.Build.MODEL).put("androidApi",android.os.Build.VERSION.SDK_INT))
                .put("processing",new JSONObject().put("filter",TrackingService.prefs(context).getString("filterMode",TrackFusion.ID)).put("filterVersion",TrackFusion.REVISION).put("maxAccuracy",65).put("maxGapSeconds",60).put("inertialNavigation",false).put("roadMatching",false).put("roadMatchingScope","raw-processing-only"))
                .put("roadOverlay",RoadNetwork.info(context).put("resultsIncluded",false))
                .put("collection",new JSONObject().put("rawPolicy","raw-v2").put("requestedGpsIntervalMs",2000).put("requestedFusedIntervalMs",5000).put("rawMaxAccuracy",1000).put("resubscriptions",TrackingService.prefs(context).getInt("resubscriptions",0)))
                .put("scope","all-stored-raw-locations-including-excluded-and-mocked").put("includesApiKeys",false).put("includesLifeRecords",false).put("processedRoutesIncluded",false)
                .put("privacy","Contains precise location history. Local export only; share manually with trusted recipients.");
            Map<String,JSONObject> days=new TreeMap<>();MessageDigest digest=MessageDigest.getInstance("SHA-256");long done=0,after=0;JSONObject manifest;
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(target,65536))){
                json(zip,"header.json",header);zip.putNextEntry(new ZipEntry("raw-points.jsonl"));
                if(progress!=null)progress.update(0,total);
                while(after<ceiling){checkInterrupted();int fetched=0;
                    try(Cursor c=db.rawQuery("SELECT id,time,lat,lon,accuracy,session,provider,mocked,speed,speed_accuracy,telemetry FROM points WHERE id>? AND id<=? ORDER BY id LIMIT "+PAGE,new String[]{String.valueOf(after),String.valueOf(ceiling)})){
                        while(c.moveToNext()){checkInterrupted();long id=c.getLong(0),time=c.getLong(1);String day=date.format(new Date(time));
                            JSONObject p=new JSONObject().put("id",id).put("time",time).put("date",day).put("lat",c.getDouble(2)).put("lon",c.getDouble(3)).put("accuracy",c.getDouble(4))
                                .put("session",c.getString(5)).put("provider",c.getString(6)).put("mocked",c.getInt(7)==1).put("speed",c.isNull(8)?JSONObject.NULL:c.getDouble(8)).put("speedAccuracy",c.isNull(9)?JSONObject.NULL:c.getDouble(9));
                            if(c.isNull(10))p.put("telemetry",JSONObject.NULL);else{String text=c.getString(10);try{p.put("telemetry",new JSONObject(text));}catch(Exception malformed){p.put("telemetry",JSONObject.NULL).put("telemetryRaw",text).put("telemetryParseError",true);}}
                            byte[] line=(p.toString()+"\n").getBytes(StandardCharsets.UTF_8);zip.write(line);digest.update(line);
                            JSONObject stats=days.get(day);if(stats==null){stats=new JSONObject().put("date",day).put("rawPoints",0L).put("firstTime",time).put("lastTime",time);days.put(day,stats);}
                            stats.put("rawPoints",stats.getLong("rawPoints")+1).put("firstTime",Math.min(time,stats.getLong("firstTime"))).put("lastTime",Math.max(time,stats.getLong("lastTime")));
                            after=id;done++;fetched++;
                        }
                    }
                    if(progress!=null)progress.update(done,total);if(fetched==0)break;
                }
                if(done!=total)throw new IOException("定位记录在导出中发生变化，文件不完整，请重试");checkInterrupted();zip.closeEntry();
                JSONArray dates=new JSONArray();for(JSONObject d:days.values())dates.put(d);
                manifest=new JSONObject(header.toString()).put("complete",true).put("rawTruncated",false).put("totalRawPoints",done).put("dateCount",days.size()).put("dates",dates).put("rawSha256",hex(digest.digest())).put("exportFinishedAt",System.currentTimeMillis());
                json(zip,"manifest.json",manifest);
            }
            return manifest;
        }finally{HISTORY_LOCK.readLock().unlock();}
    }
    private TrackFeedback(){}
}
