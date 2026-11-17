package com.insight.quantlife;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persisted stopwatch, independent of WebView timers and wall-clock changes. */
public final class StudyTimerStore {
    public static final long MAX_MS=7L*86400000;
    public static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS study_timers (id TEXT PRIMARY KEY,finished INTEGER NOT NULL,payload TEXT NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS study_single_active ON study_timers(finished) WHERE finished=0");
    }
    private static JSONObject find(SQLiteDatabase db,String id)throws Exception{
        try(Cursor c=db.rawQuery("SELECT payload FROM study_timers WHERE id=?",new String[]{id})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}
    }
    private static void put(SQLiteDatabase db,JSONObject s)throws Exception{
        ContentValues v=new ContentValues();v.put("id",s.getString("id"));v.put("finished","finished".equals(s.getString("state"))?1:0);v.put("payload",s.toString());
        if(db.insertWithOnConflict("study_timers",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new Exception("学习计时保存失败，请重试");
    }
    private static long elapsed(JSONObject s,MeditationStore.Clock c){
        long ms=s.optLong("accumulatedMs");
        if("running".equals(s.optString("state"))&&s.optInt("boot")==c.boot()&&c.elapsed()>=s.optLong("segmentElapsed"))ms+=c.elapsed()-s.optLong("segmentElapsed");
        return Math.min(MAX_MS,Math.max(0,ms));
    }
    private static void recover(JSONObject s,MeditationStore.Clock c)throws Exception{
        if("running".equals(s.optString("state"))&&(s.optInt("boot")!=c.boot()||c.elapsed()<s.optLong("segmentElapsed"))){
            s.put("state","paused").put("accumulatedMs",s.optLong("checkpointMs",s.optLong("accumulatedMs"))).put("recoveredAfterReboot",true);
        }
        long ms=elapsed(s,c);s.put("checkpointMs",ms);
        if(ms>=MAX_MS&&"running".equals(s.optString("state")))s.put("accumulatedMs",ms).put("state","paused").put("limitReached",true);
    }
    private static JSONObject publicTimer(JSONObject s,MeditationStore.Clock c)throws Exception{
        JSONObject out=new JSONObject(s.toString()).put("durationMs",elapsed(s,c));
        for(String k:new String[]{"segmentElapsed","boot","checkpointMs","accumulatedMs"})out.remove(k);return out;
    }
    private static JSONObject snapshot(SQLiteDatabase db,MeditationStore.Clock c)throws Exception{
        JSONObject s=null;try(Cursor cur=db.rawQuery("SELECT payload FROM study_timers WHERE finished=0",null)){if(cur.moveToFirst())s=new JSONObject(cur.getString(0));}
        if(s!=null){recover(s,c);put(db,s);}return new JSONObject().put("active",s==null?JSONObject.NULL:publicTimer(s,c));
    }
    private static String local(long time,String pattern){return new SimpleDateFormat(pattern,Locale.CHINA).format(new Date(time));}
    private static JSONObject record(JSONObject s,MeditationStore.Clock c)throws Exception{
        long start=s.getLong("startedAt"),end=s.getLong("endedAt"),ms=elapsed(s,c);
        JSONObject timing=new JSONObject().put("id",s.getString("id")).put("startedAt",start).put("endedAt",end).put("durationMs",ms).put("recoveredAfterReboot",s.optBoolean("recoveredAfterReboot")).put("limitReached",s.optBoolean("limitReached"));
        return new JSONObject().put("id","study-"+s.getString("id")).put("type","learning").put("date",s.getString("date")).put("title","").put("studyInput",s.optString("title")).put("platform","").put("contentType","other").put("durationMinutes",ms/60000d).put("note","").put("tags",new JSONArray()).put("url","").put("source","manual").put("createdAt",start).put("studyTiming",timing);
    }
    public static synchronized JSONObject execute(SQLiteDatabase db,JSONObject p,MeditationStore.Clock c)throws Exception{
        create(db);db.beginTransaction();try{
            String command=p.optString("command","status"),id=p.optString("id");JSONObject active=snapshot(db,c).optJSONObject("active");
            if(!"status".equals(command)&&!id.matches("[A-Za-z0-9_-]{8,80}"))throw new Exception("学习计时编号无效");
            if("start".equals(command)){
                if(active!=null&&!id.equals(active.getString("id")))throw new Exception("已有学习正在计时，请先结束");
                if(find(db,id)==null)put(db,new JSONObject().put("id",id).put("state","running").put("startedAt",c.wall()).put("date",local(c.wall(),"yyyy-MM-dd")).put("segmentElapsed",c.elapsed()).put("boot",c.boot()).put("accumulatedMs",0).put("checkpointMs",0).put("title",""));
            }else if(!"status".equals(command)){
                JSONObject s=find(db,id);if(s==null)throw new Exception("找不到这次学习计时，请刷新后重试");recover(s,c);String state=s.getString("state");
                if("discard".equals(command)){
                    if("finished".equals(state))throw new Exception("已保存的学习记录请到记录中删除");db.delete("study_timers","id=? AND finished=0",new String[]{id});
                }else{
                    if(p.has("title")&&!"finished".equals(state)){String title=p.getString("title").trim();if(title.length()>300)throw new Exception("学习内容最多300字");s.put("title",title);}
                    if("finish".equals(command)){
                        if(!"finished".equals(state)){
                            s.put("accumulatedMs",elapsed(s,c)).put("endedAt",c.wall()).put("state","finished");
                            // Finish prepares only a recoverable draft. It never inserts a learning record.
                            s.put("draft",record(s,c));
                        }
                    }else if("pause".equals(command)){
                        if("running".equals(state))s.put("accumulatedMs",elapsed(s,c)).put("state","paused");
                    }else if("resume".equals(command)){
                        if("paused".equals(state)){if(s.optBoolean("limitReached"))throw new Exception("本次已达7天，请结束记录后重新开始");s.put("state","running").put("segmentElapsed",c.elapsed()).put("boot",c.boot());}
                    }else if(!"title".equals(command))throw new Exception("未知学习计时操作");
                    put(db,s);if("finish".equals(command))active=s;
                }
            }
            JSONObject result=snapshot(db,c);if("finish".equals(command)&&active!=null)result.put("draft",active.getJSONObject("draft"));
            db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }
}
