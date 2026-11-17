package com.insight.quantlife;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local stopwatch. Elapsed realtime includes sleep and is independent of wall-clock edits. */
public final class MeditationStore {
    public interface Clock { long wall(); long elapsed(); int boot(); }
    public static Clock clock(Context context) {
        return new Clock() {
            public long wall(){return System.currentTimeMillis();}
            public long elapsed(){return SystemClock.elapsedRealtime();}
            public int boot(){return Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
        };
    }
    public static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meditation_sessions (id TEXT PRIMARY KEY,finished INTEGER NOT NULL,payload TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS meditation_single_active ON meditation_sessions(finished) WHERE finished=0");
    }
    private static JSONObject find(SQLiteDatabase db,String id)throws Exception {
        try(Cursor c=db.rawQuery("SELECT payload FROM meditation_sessions WHERE id=?",new String[]{id})){
            return c.moveToFirst()?new JSONObject(c.getString(0)):null;
        }
    }
    private static void put(SQLiteDatabase db,JSONObject s)throws Exception {
        ContentValues v=new ContentValues();v.put("id",s.getString("id"));v.put("finished","finished".equals(s.getString("state"))?1:0);v.put("payload",s.toString());
        if(db.insertWithOnConflict("meditation_sessions",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new Exception("禅定记录保存失败");
    }
    private static long elapsed(JSONObject s,Clock clock) {
        long ms=s.optLong("accumulatedMs");
        if("running".equals(s.optString("state"))&&s.optInt("boot")==clock.boot()&&clock.elapsed()>=s.optLong("segmentElapsed"))ms+=clock.elapsed()-s.optLong("segmentElapsed");
        return Math.max(0,ms);
    }
    private static JSONObject recover(JSONObject s,Clock clock)throws Exception {
        if("running".equals(s.optString("state"))&&(s.optInt("boot")!=clock.boot()||clock.elapsed()<s.optLong("segmentElapsed"))){
            // A reboot destroys the monotonic origin. Never count unknown powered-off time.
            s.put("state","paused").put("accumulatedMs",s.optLong("checkpointMs",s.optLong("accumulatedMs"))).put("recoveredAfterReboot",true);
        }
        s.put("checkpointMs",elapsed(s,clock));return s;
    }
    private static JSONObject publicSession(JSONObject s,Clock clock)throws Exception {
        JSONObject out=new JSONObject(s.toString());out.put("durationMs",elapsed(s,clock));
        out.remove("segmentElapsed");out.remove("boot");out.remove("checkpointMs");out.remove("accumulatedMs");return out;
    }
    private static JSONObject snapshot(SQLiteDatabase db,Clock clock)throws Exception {
        JSONObject active=null;JSONArray sessions=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT payload FROM meditation_sessions ORDER BY rowid DESC",null)){
            while(c.moveToNext()){
                JSONObject s=new JSONObject(c.getString(0));
                if("finished".equals(s.optString("state")))sessions.put(publicSession(s,clock));
                else active=recover(s,clock);
            }
        }
        if(active!=null)put(db,active);
        return new JSONObject().put("active",active==null?JSONObject.NULL:publicSession(active,clock)).put("sessions",sessions);
    }
    public static synchronized JSONObject execute(SQLiteDatabase db,JSONObject p,Clock clock)throws Exception {
        create(db);db.beginTransaction();
        try {
            String command=p.optString("command","status"),id=p.optString("id");
            JSONObject status=snapshot(db,clock),active=status.optJSONObject("active"),saved=null;
            if("start".equals(command)){
                if(!id.matches("[A-Za-z0-9_-]{8,100}"))throw new Exception("禅定编号无效");
                if(active!=null&&!id.equals(active.getString("id")))throw new Exception("已有禅定正在进行，请先结束");
                // Same operation id is safe to retry, even after the session was finished.
                if(find(db,id)==null)put(db,new JSONObject().put("id",id).put("state","running").put("startedAt",clock.wall()).put("segmentElapsed",clock.elapsed()).put("boot",clock.boot()).put("accumulatedMs",0).put("checkpointMs",0).put("note",""));
            } else if("delete".equals(command)) {
                if(!id.matches("[A-Za-z0-9_-]{8,100}"))throw new Exception("禅定编号无效");
                JSONObject s=find(db,id);
                if(s!=null&&!"finished".equals(s.optString("state")))throw new Exception("请先结束这次禅定，再删除记录");
                // Exact finished id only. A repeated deletion is safe after a lost reply.
                db.delete("meditation_sessions","id=? AND finished=1",new String[]{id});
            } else if(!"status".equals(command)) {
                JSONObject s=find(db,id);if(s==null)throw new Exception("找不到这次禅定记录");recover(s,clock);
                String state=s.getString("state");
                if("note".equals(command)){
                    if(!"finished".equals(state))throw new Exception("请先结束这次禅定");s.put("note",p.getString("note"));
                }else if("finish".equals(command)){
                    if(!"finished".equals(state))s.put("accumulatedMs",elapsed(s,clock)).put("endedAt",clock.wall()).put("state","finished");
                    saved=s;
                }else if("pause".equals(command)){
                    if("running".equals(state))s.put("accumulatedMs",elapsed(s,clock)).put("state","paused");
                }else if("resume".equals(command)){
                    if("paused".equals(state))s.put("state","running").put("segmentElapsed",clock.elapsed()).put("boot",clock.boot());
                }else throw new Exception("未知禅定操作");
                put(db,s);
            }
            JSONObject result=snapshot(db,clock);if(saved!=null)result.put("saved",publicSession(saved,clock));
            db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }
    public static synchronized JSONArray export(SQLiteDatabase db)throws Exception {
        create(db);JSONArray result=new JSONArray();try(Cursor c=db.rawQuery("SELECT payload FROM meditation_sessions WHERE finished=1 ORDER BY rowid",null)){
            while(c.moveToNext())result.put(new JSONObject(c.getString(0)));
        }return result;
    }
    public static synchronized int restore(SQLiteDatabase db,JSONArray sessions)throws Exception {
        if(sessions==null)return 0;if(sessions.length()>50000)throw new Exception("禅定记录过多");create(db);int inserted=0;
        for(int i=0;i<sessions.length();i++){
            JSONObject s=sessions.getJSONObject(i);String id=s.getString("id");
            if(!id.matches("[A-Za-z0-9_-]{8,100}")||!"finished".equals(s.getString("state"))||s.getLong("startedAt")<=0||s.getLong("endedAt")<=0||s.getLong("accumulatedMs")<0)throw new Exception("禅定备份格式无效");
            if(find(db,id)==null){put(db,s);inserted++;}
        }return inserted;
    }
}
