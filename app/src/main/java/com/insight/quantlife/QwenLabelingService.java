package com.insight.quantlife;

import android.app.job.*;import android.content.*;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;
import org.json.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;

/** Durable background queue, independent of chat/WebView. Historical and edited records self-repair. */
public final class QwenLabelingService extends JobService {
 private static final int JOB=7305;
 private static final AtomicBoolean running=new AtomicBoolean(),requested=new AtomicBoolean();
 private volatile boolean stopped;private volatile ExecutorService workers;
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("qwen-labeling",MODE_PRIVATE);}
 static JSONObject checkpoint(SQLiteDatabase db,JSONObject r)throws Exception{
  JSONObject j=QwenLabels.checkpoint(db,r);
  // Old Qwen cooldowns must not postpone the first analysis by the new provider.
  if(!InformationUnits.RULE.equals(j.optString("informationRule"))){j.remove("retryAt");j.remove("failures");j.remove("error");j.put("informationRule",InformationUnits.RULE);}
  return j;
 }
 public static boolean configured(Context c){try{return InformationTransport.config(ModelRequests.settings(c))!=null;}catch(Exception ignored){return false;}}
 public static synchronized void schedule(Context c){try{
  if(!configured(c))return;if(running.get()){requested.set(true);return;}
  JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs.getPendingJob(JOB)==null)jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(c,QwenLabelingService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(1000).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).setPersisted(true).build());
 }catch(RuntimeException ignored){prefs(c).edit().putString("error","DeepSeek后台任务尚未调度，将在下次打开时重试").apply();}}
 static List<JSONObject> pending(SQLiteDatabase db,long now,boolean ready)throws Exception{
  QwenLabels.create(db);List<JSONObject> list=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT r.payload FROM records r ORDER BY r.date DESC,r.id",null)){while(c.moveToNext()){
   JSONObject r=new JSONObject(c.getString(0));if(InformationUnits.needs(r)&&(!ready||checkpoint(db,r).optLong("retryAt")<=now))list.add(r);
  }}return list;
 }
 public static JSONObject status(Context c,SQLiteDatabase db)throws Exception{
  QwenLabels.create(db);int total=0,done=0,failed=0,informationTotal=0,informationDone=0;long retryAt=0;try(Cursor rows=db.rawQuery("SELECT payload FROM records",null)){while(rows.moveToNext()){
   JSONObject r=new JSONObject(rows.getString(0));if(!InformationUnits.eligible(r))continue;total++;if(InformationUnits.current(r))done++;if(InformationUnits.eligible(r)){informationTotal++;if(InformationUnits.current(r))informationDone++;}if(InformationUnits.needs(r)){JSONObject job=checkpoint(db,r);if(job.optInt("failures")>0){failed++;retryAt=retryAt==0?job.optLong("retryAt"):Math.min(retryAt,job.optLong("retryAt"));}}
  }}
  boolean configured=configured(c);String error=configured?prefs(c).getString("error",""):"请在AI助手中选择并配置DeepSeek，信息单元将自动整理";String state=!configured?"unconfigured":done==total&&informationDone==informationTotal?"complete":running.get()?"running":!prefs(c).getString("blocked","").isEmpty()?"blocked":failed>0?"retrying":"queued";
  return new JSONObject().put("model",configured?InformationTransport.config(ModelRequests.settings(c)).optString("model"):"deepseek").put("provider",InformationUnits.PROVIDER).put("configured",configured).put("total",total).put("done",done).put("pending",total-done).put("informationTotal",informationTotal).put("informationDone",informationDone).put("informationPending",informationTotal-informationDone).put("failed",failed).put("retryAt",retryAt).put("state",state).put("error",error).put("parallel",prefs(c).getInt("parallel",2)).put("rpmLimit",JSONObject.NULL).put("tpmLimit",JSONObject.NULL);
 }
 public static JSONObject retry(Context c,SQLiteDatabase db)throws Exception{
  for(JSONObject r:pending(db,System.currentTimeMillis(),false)){JSONObject j=checkpoint(db,r);j.put("retryAt",0).put("failures",0).remove("error");QwenLabels.putJob(db,r.getString("id"),j);}
  prefs(c).edit().remove("blocked").remove("error").remove("cooldown").apply();schedule(c);return status(c,db);
 }
 private synchronized void failure(SQLiteDatabase db,JSONObject r,Exception error)throws Exception{
  if(stopped)return;JSONObject latest=QwenLabels.get(db,r.getString("id"));if(!QwenLabels.same(latest,r))return;
  JSONObject j=checkpoint(db,r);int failures=j.optInt("failures")+1;long delay=Math.min(6*3600000L,30000L*(1L<<Math.min(10,failures-1)));
  String message="DeepSeek打标暂未完成，已保留进度";if(error instanceof QwenTransport.Failure){QwenTransport.Failure f=(QwenTransport.Failure)error;message=f.getMessage();delay=Math.max(delay,f.retryMs);
   if(f.code==401||f.code==403||f.code==404)prefs(this).edit().putString("blocked",InformationTransport.fingerprint(InformationTransport.config(ModelRequests.settings(this)))).apply();
   if(f.code==429)prefs(this).edit().putLong("cooldown",System.currentTimeMillis()+delay).apply();
  }else if(error.getMessage()!=null&&(error.getMessage().startsWith("打标")||error.getMessage().startsWith("信息单元")))message=error.getMessage();
  j.put("failures",failures).put("retryAt",System.currentTimeMillis()+delay).put("error",message);QwenLabels.putJob(db,r.getString("id"),j);
  prefs(this).edit().putInt("parallel",Math.max(1,prefs(this).getInt("parallel",2)/2)).putString("error",message).apply();
 }
 @Override public boolean onStartJob(JobParameters params){
  if(!running.compareAndSet(false,true))return false;stopped=false;requested.set(false);
  new Thread(()->{
   boolean again=false,progress=false;ExecutorService pool=Executors.newFixedThreadPool(InformationTransport.MAX_PARALLEL);workers=pool;
   try{
    JSONObject config=InformationTransport.config(ModelRequests.settings(this));if(config==null)return;
    if(!InformationUnits.RULE.equals(prefs(this).getString("informationRule","")))prefs(this).edit().putString("informationRule",InformationUnits.RULE).remove("blocked").remove("error").remove("cooldown").putInt("parallel",2).apply();
    String fingerprint=InformationTransport.fingerprint(config),blocked=prefs(this).getString("blocked","");if(blocked.equals(fingerprint))return;if(!blocked.isEmpty())prefs(this).edit().remove("blocked").remove("error").apply();
    long deadline=android.os.SystemClock.elapsedRealtime()+150000;int successes=0;
    try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE|SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)){
     QwenLabels.create(db);
     while(!stopped&&android.os.SystemClock.elapsedRealtime()<deadline){
      if(prefs(this).getLong("cooldown",0)>System.currentTimeMillis()||!prefs(this).getString("blocked","").isEmpty())break;
      List<JSONObject> todo=pending(db,System.currentTimeMillis(),true);if(todo.isEmpty())break;
      int parallel=Math.max(1,Math.min(InformationTransport.MAX_PARALLEL,prefs(this).getInt("parallel",2)));List<Future<Boolean>> futures=new ArrayList<>();
      for(JSONObject r:todo.subList(0,Math.min(parallel,todo.size())))futures.add(pool.submit(()->{
       if(stopped)return false;try(SQLiteDatabase own=SQLiteDatabase.openDatabase(getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE|SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)){
        try{InformationUnits.step(own,r,new InformationUnits.Model(){public String model(){return config.optString("model");}public JSONObject ask(String task,JSONObject in)throws Exception{if(stopped)throw new InterruptedException();return InformationTransport.information(QwenLabelingService.this,config,task,in);}});return true;}
        catch(Exception ex){failure(own,r,ex);return false;}
       }
      }));
      for(Future<Boolean> future:futures){if(future.get()){progress=true;successes++;}}
      if(successes>=4){prefs(this).edit().putInt("parallel",Math.min(InformationTransport.MAX_PARALLEL,parallel+1)).apply();successes=0;}
     }
     again=!pending(db,System.currentTimeMillis(),false).isEmpty()&&prefs(this).getString("blocked","").isEmpty();
     if(!again&&prefs(this).getString("blocked","").isEmpty())prefs(this).edit().remove("error").apply();
    }
   }catch(Exception ignored){again=true;prefs(this).edit().putString("error","DeepSeek后台打标暂未完成，保留进度重试").apply();}
   finally{
    pool.shutdownNow();workers=null;
    synchronized(QwenLabelingService.class){boolean requestedAgain=requested.getAndSet(false);running.set(false);if(!stopped){boolean immediate=(progress||requestedAgain)&&prefs(this).getLong("cooldown",0)<=System.currentTimeMillis()&&prefs(this).getString("blocked","").isEmpty();jobFinished(params,again&&!immediate);if(immediate&&(again||requestedAgain))new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->schedule(getApplicationContext()),1000);}}
   }
  },"information-label-queue").start();return true;
 }
 @Override public boolean onStopJob(JobParameters p){stopped=true;ExecutorService w=workers;if(w!=null)w.shutdownNow();return true;}
}
