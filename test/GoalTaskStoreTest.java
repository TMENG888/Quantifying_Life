package com.insight.quantlife.tests;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import com.insight.quantlife.GoalTaskStore;
import com.insight.quantlife.MeditationStore;
import org.json.*;
import java.io.File;

/** Isolated native ledger tests. No location, account, model or user records. */
public final class GoalTaskStoreTest extends DocumentEditorTest {
    static final class Clock implements MeditationStore.Clock {
        long wall=1793365200000L,elapsed=10000;int boot=7;
        public long wall(){return wall;}public long elapsed(){return elapsed;}public int boot(){return boot;}
        void add(long ms){wall+=ms;elapsed+=ms;}
    }
    interface Throwing {void run()throws Exception;}
    void rejects(Throwing op,String label)throws Exception{boolean failed=false;try{op.run();}catch(Exception expected){failed=true;}check(failed,label);}
    JSONObject item(String id,String kind,String title,double target,String unit)throws Exception{return new JSONObject().put("id",id).put("kind",kind).put("title",title).put("area","life").put("parentId","").put("targetAmount",target).put("targetUnit",unit).put("plannedMinutes",0).put("deadline","").put("scheduledStart","").put("scheduledEnd","");}
    JSONObject save(JSONObject item,JSONObject prior)throws Exception{return new JSONObject().put("command","save").put("item",item).put("expectedVersion",prior==null?JSONObject.NULL:prior.getLong("updatedAt"));}
    JSONObject cmd(String command,JSONObject prior)throws Exception{return new JSONObject().put("command",command).put("id",prior.getString("id")).put("expectedVersion",prior.getLong("updatedAt"));}
    JSONObject run(SQLiteDatabase db,Clock c,JSONObject p)throws Exception{return GoalTaskStore.execute(db,p,c);}
    JSONObject get(SQLiteDatabase db,Clock c,String id)throws Exception{JSONArray all=run(db,c,new JSONObject()).getJSONArray("items");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(id))return all.getJSONObject(i);return null;}
    JSONObject saved(SQLiteDatabase db,Clock c,JSONObject p)throws Exception{return run(db,c,p).getJSONObject("saved");}
    JSONObject entry(JSONObject prior,String id,double amount,double minutes)throws Exception{return cmd("progress",prior).put("entryId",id).put("amount",amount).put("minutes",minutes).put("note","合成进度");}
    void verify()throws Exception{
        Clock c=new Clock();try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            JSONObject goal=saved(db,c,save(item("goal-fitness","goal","体能长期目标",100,"组").put("deadline","2026-12-31T23:59"),null));
            JSONObject task=saved(db,c,save(item("task-situps","task","19点做仰卧起坐",28,"个").put("parentId",goal.getString("id")).put("deadline","2026-10-30T19:30").put("scheduledStart","2026-10-30T19:00").put("scheduledEnd","2026-10-30T19:30").put("plannedMinutes",30).put("actualAmount",99).put("status","completed"),null));
            check(task.getDouble("actualAmount")==0&&task.getString("status").equals("active"),"Creation cannot smuggle in unrecorded completion");
            check(task.getString("scheduledStart").equals("2026-10-30T19:00")&&task.getDouble("targetAmount")==28,"Time window, target and unit remain separate");
            JSONObject staleTask=new JSONObject(task.toString()),p=entry(task,"progress-first",5,10);task=saved(db,c,p);
            check(task.getDouble("actualAmount")==5&&task.getDouble("actualMinutes")==10&&task.getJSONArray("entries").length()==1,"Progress records quantity, minutes and evidence once");
            check(get(db,c,goal.getString("id")).getDouble("actualAmount")==0,"Child units never inflate differently measured parent goal");
            long version=task.getLong("updatedAt");JSONObject replay=saved(db,c,p);
            check(replay.getDouble("actualAmount")==5&&replay.getLong("updatedAt")==version,"Lost progress reply is idempotent even with an old version");
            final JSONObject conflict=new JSONObject(p.toString()).put("amount",6);rejects(()->run(db,c,conflict),"Reused entry id with different facts is rejected");
            final JSONObject outdated=save(new JSONObject(staleTask.toString()).put("title","旧页面覆盖"),staleTask);rejects(()->run(db,c,outdated),"Concurrent metadata changes cannot overwrite fresh progress");
            final JSONObject beforeUnit=task;rejects(()->run(db,c,save(new JSONObject(beforeUnit.toString()).put("targetUnit","分钟"),beforeUnit)),"A measured quantity cannot change units after progress");
            JSONObject meta=new JSONObject(task.toString()).put("title","重新命名动作").put("actualAmount",0).put("actualMinutes",0).put("entries",new JSONArray());task=saved(db,c,save(meta,task));
            check(task.getDouble("actualAmount")==5&&task.getDouble("actualMinutes")==10&&task.getJSONArray("entries").length()==1,"Metadata saves preserve native progress and history");
            final JSONObject incomplete=task;rejects(()->run(db,c,cmd("complete",incomplete)),"Cannot mark a 28-count action complete after only five");
            for(String bad:new String[]{"2026-02-30T12:00","2026-13-01T12:00","2026-10-30T24:00","2026-10-30","<script>"}){
                final String value=bad;rejects(()->run(db,c,save(item("bad-date","task","无效日期",1,"项").put("deadline",value),null)),"Invalid local date rejected: "+bad);
            }
            rejects(()->run(db,c,save(item("bad-half","task","单边时段",1,"项").put("scheduledStart","2026-10-30T19:00"),null)),"A scheduled window requires both endpoints");
            rejects(()->run(db,c,save(item("bad-order","task","时段顺序",1,"项").put("scheduledStart","2026-10-30T19:30").put("scheduledEnd","2026-10-30T19:00"),null)),"Window end cannot precede start");
            rejects(()->run(db,c,save(item("bad-due","task","晚于截止",1,"项").put("deadline","2026-10-30T19:00").put("scheduledStart","2026-10-30T19:00").put("scheduledEnd","2026-10-30T19:30"),null)),"Scheduled work must precede its own deadline");
            rejects(()->run(db,c,save(item("bad-parent","task","无关联目标",1,"项").put("parentId","missing-goal"),null)),"Unknown parent cannot create an orphan action");
            rejects(()->run(db,c,save(item("bad-kind-parent","task","动作不能做目标",1,"项").put("parentId","task-situps"),null)),"Tasks cannot be used as long-term goal parents");
            rejects(()->run(db,c,save(item("bad-goal-parent","goal","目标不能嵌套",1,"项").put("parentId","goal-fitness"),null)),"Goal cannot become an action child");
            rejects(()->run(db,c,save(item("bad-self-parent","task","自身关联",1,"项").put("parentId","bad-self-parent"),null)),"Self parent is rejected");
            rejects(()->run(db,c,save(item("bad-after-parent","task","晚于总目标",1,"项").put("parentId","goal-fitness").put("deadline","2027-01-01T12:00"),null)),"Back-planned actions cannot outlive parent deadline");
            final JSONObject oldGoal=goal;rejects(()->run(db,c,save(new JSONObject(oldGoal.toString()).put("deadline","2026-10-29T12:00"),oldGoal)),"Parent deadline cannot silently invalidate existing action windows");
            check(get(db,c,"goal-fitness").getString("deadline").equals("2026-12-31T23:59"),"Rejected parent edit leaves prior plan intact");
            task=saved(db,c,cmd("start",task));c.add(65000);JSONObject live=get(db,c,"task-situps");
            check(Math.abs(live.getDouble("elapsedMinutes")-11.0833333333)<.00001&&live.getDouble("actualMinutes")==10,"Persisted stopwatch adds only its live segment");
            check(live.getLong("updatedAt")==task.getLong("updatedAt"),"Reading live elapsed time does not rewrite ledger versions");
            c.wall-=3600000;check(Math.abs(get(db,c,"task-situps").getDouble("elapsedMinutes")-live.getDouble("elapsedMinutes"))<.000001,"Wall-clock correction does not change actual elapsed work");
            final JSONObject running=live;rejects(()->run(db,c,entry(running,"progress-double-time",0,3)),"Manual minutes cannot duplicate an active timer segment");
            JSONObject another=saved(db,c,save(item("task-other","task","另一个行动",1,"项"),null));final JSONObject other=another;
            rejects(()->run(db,c,cmd("start",other)),"Only one goal/action stopwatch may run at a time");
            task=saved(db,c,cmd("pause",get(db,c,"task-situps")));double paused=task.getDouble("actualMinutes");c.add(600000);
            check(Math.abs(get(db,c,"task-situps").getDouble("elapsedMinutes")-paused)<.000001,"Paused time never accumulates");
            task=saved(db,c,cmd("start",task));c.add(30000);task=saved(db,c,entry(task,"progress-rest",23,0));
            check(task.getDouble("actualAmount")==28&&task.getLong("timerStartedAt")>0,"Quantity may progress while actual time continues");
            task=saved(db,c,cmd("complete",task));
            check(task.getString("status").equals("completed")&&task.getLong("timerStartedAt")==0&&Math.abs(task.getDouble("actualMinutes")-(paused+.5))<.000001,"Completing a reached target settles exact timer minutes");
            task=saved(db,c,cmd("reopen",task));check(task.getString("status").equals("active")&&task.getDouble("actualAmount")==28,"Reopening preserves previously achieved quantity");
            task=saved(db,c,cmd("start",task));c.add(120000);c.boot++;c.elapsed=100;
            JSONObject recovered=get(db,c,"task-situps");check(recovered.getLong("timerStartedAt")==0&&recovered.getBoolean("timerRecovered")&&Math.abs(recovered.getDouble("elapsedMinutes")-task.getDouble("actualMinutes"))<.000001,"Reboot pauses unknown live time instead of counting powered-off duration");
            task=saved(db,c,entry(recovered,"reboot-manual-time",0,1));check(task.getLong("timerStartedAt")==0&&Math.abs(task.getDouble("actualMinutes")-(recovered.getDouble("actualMinutes")+1))<.000001,"Recovered paused timer accepts manual minutes without falsely treating it as running");
            task=saved(db,c,cmd("start",task));check(task.getLong("timerStartedAt")>0,"Recovered action can start a fresh monotonic segment");
            final JSONObject activeTask=task;rejects(()->run(db,c,cmd("delete",activeTask)),"Running action must pause before deletion");
            final JSONObject parentBeforeDelete=get(db,c,"goal-fitness");rejects(()->run(db,c,cmd("delete",parentBeforeDelete)),"Deleting a parent never silently erases children");
            JSONArray backup=GoalTaskStore.export(db,c);check(get(db,c,"task-situps").getLong("timerStartedAt")>0,"Export freezes a copy without pausing the source stopwatch");
            try(SQLiteDatabase restored=SQLiteDatabase.create(null)){
                JSONArray reverse=new JSONArray();for(int i=backup.length()-1;i>=0;i--)reverse.put(backup.get(i));int n=GoalTaskStore.restore(restored,reverse);
                check(n==3&&GoalTaskStore.restore(restored,backup)==0,"Backup restores child-before-parent order and merges idempotently");
                JSONObject restoredTask=get(restored,c,"task-situps");check(restoredTask.getLong("timerStartedAt")==0&&restoredTask.getDouble("actualAmount")==28&&restoredTask.getJSONArray("entries").length()==3,"Restored task preserves measures and progress but never resumes an old timer");
                JSONObject changed=saved(restored,c,save(new JSONObject(restoredTask.toString()).put("title","本机修改"),restoredTask));GoalTaskStore.restore(restored,backup);check(get(restored,c,"task-situps").getString("title").equals("本机修改"),"Restore never overwrites locally updated matching task ids");
                JSONObject fresh=item("restore-fresh","task","恢复事务",1,"项").put("actualAmount",0).put("actualMinutes",0).put("status","active").put("createdAt",100).put("updatedAt",101).put("entries",new JSONArray());
                JSONObject bad=new JSONObject(fresh.toString()).put("id","restore-bad").put("actualAmount",5);
                rejects(()->GoalTaskStore.restore(restored,new JSONArray().put(fresh).put(bad)),"Inconsistent backup quantities reject the entire merge");check(get(restored,c,"restore-fresh")==null,"Invalid later backup row cannot leave partial restored tasks");
            }
            JSONObject batchGoal=item("batch-goal","goal","倒推总目标",1,"项").put("deadline","2026-12-31T23:59"),batchTask=item("batch-task","task","本周里程碑",1,"项").put("parentId","batch-goal").put("deadline","2026-11-01T12:00");
            run(db,c,new JSONObject().put("command","batch").put("commands",new JSONArray().put(save(batchGoal,null)).put(save(batchTask,null))));
            check(get(db,c,"batch-goal")!=null&&get(db,c,"batch-task").getString("parentId").equals("batch-goal"),"AI-confirmed plan creates goal and linked action in one batch");
            JSONArray invalidBatch=new JSONArray().put(save(item("batch-will-rollback","goal","不会残留",1,"项"),null)).put(save(item("batch-invalid","task","无效数量",0,"项"),null));
            rejects(()->run(db,c,new JSONObject().put("command","batch").put("commands",invalidBatch)),"Invalid second action rolls back the full batch");check(get(db,c,"batch-will-rollback")==null,"Failed AI plan cannot leave a half-created parent");
            rejects(()->run(db,c,new JSONObject().put("command","batch").put("commands",new JSONArray().put(cmd("delete",other)))),"Batch cannot sneak in deletes or completion commands");
            task=saved(db,c,cmd("pause",get(db,c,"task-situps")));run(db,c,cmd("delete",task));run(db,c,cmd("delete",get(db,c,"goal-fitness")));
            check(get(db,c,"task-situps")==null&&get(db,c,"goal-fitness")==null,"Explicit child then parent deletion removes exact items only");check(get(db,c,"task-other")!=null,"Unrelated action survives goal deletion");
        }
        File file=new File(getTargetContext().getFilesDir(),"goal-task-ledger-isolated.db");SQLiteDatabase.deleteDatabase(file);
        try{
            String id="persistent-timer";try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null)){JSONObject task=saved(db,c,save(item(id,"task","重建后继续",1,"项"),null));saved(db,c,cmd("start",task));}
            c.add(90000);try(SQLiteDatabase reopened=SQLiteDatabase.openOrCreateDatabase(file,null)){JSONObject task=get(reopened,c,id);check(task.getDouble("elapsedMinutes")==1.5&&task.getLong("timerStartedAt")>0,"Closing and reopening SQLite retains the same running stopwatch");}
        }finally{SQLiteDatabase.deleteDatabase(file);}
    }
    void verifyDeleteProgress()throws Exception{
        Clock c=new Clock();try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            JSONObject goal=saved(db,c,save(item("delete-parent","goal","长期目标",1,"成果"),null));
            JSONObject t=saved(db,c,save(item("delete-test","task","改错测试",2,"节").put("parentId","delete-parent"),null));
            t=saved(db,c,entry(t,"delete-one",1,5));JSONObject oldRequest=entry(t,"delete-two",1,7);t=saved(db,c,oldRequest);t=saved(db,c,cmd("complete",t));
            JSONObject stale=new JSONObject(t.toString()),remove=cmd("deleteProgress",t).put("entryId","delete-two");t=saved(db,c,remove);
            check(t.getDouble("actualAmount")==1&&t.getDouble("actualMinutes")==5&&t.getJSONArray("entries").length()==1,"Deleting progress reverses its own amount and manual minutes only");
            check(t.getString("status").equals("active")&&!t.has("completedAt"),"Deleting below target reopens completed work");
            check(t.getJSONArray("entries").getJSONObject(0).getString("id").equals("delete-one"),"Other progress evidence is preserved");
            long version=t.getLong("updatedAt");check(saved(db,c,remove).getLong("updatedAt")==version,"Retrying a deletion with its stale version does not subtract twice");
            rejects(()->run(db,c,oldRequest),"Delayed retry of a removed progress request cannot resurrect it");
            final JSONObject staleRemove=cmd("deleteProgress",stale).put("entryId","delete-one");rejects(()->run(db,c,staleRemove),"Stale deletion cannot remove a newer ledger version");
            final JSONObject missing=cmd("deleteProgress",t).put("entryId","missing-entry");rejects(()->run(db,c,missing),"Unknown progress id is rejected without changing totals");
            check(get(db,c,"delete-test").getLong("updatedAt")==version&&get(db,c,"delete-parent").getDouble("actualAmount")==0,"Failed deletion and child correction leave parent and task totals intact");
            JSONObject other=saved(db,c,save(item("delete-other","task","其他任务",1,"项"),null));other=saved(db,c,entry(other,"other-entry",1,3));
            final JSONObject cross=cmd("deleteProgress",t).put("entryId","other-entry");rejects(()->run(db,c,cross),"An entry belonging to another task cannot be deleted");
            t=saved(db,c,cmd("start",t));c.add(120000);t=saved(db,c,cmd("deleteProgress",t).put("entryId","delete-one"));
            check(t.getDouble("actualAmount")==0&&t.getDouble("actualMinutes")==0&&t.getDouble("elapsedMinutes")==2&&t.getLong("timerStartedAt")>0,"Correcting manual progress preserves the live monotonic stopwatch");
            check(get(db,c,"delete-other").getDouble("actualAmount")==1,"Unrelated task progress is unchanged");
            JSONArray backup=GoalTaskStore.export(db,c);try(SQLiteDatabase restored=SQLiteDatabase.create(null)){GoalTaskStore.restore(restored,backup);check(get(restored,c,"delete-test").getJSONArray("entries").length()==0&&get(restored,c,"delete-test").getDouble("actualMinutes")==2,"Backup and restore preserve the corrected ledger and independent timer time");}
            t=saved(db,c,cmd("pause",t));t=saved(db,c,entry(t,"delete-three",3,0));t=saved(db,c,entry(t,"delete-extra",1,0));t=saved(db,c,cmd("complete",t));t=saved(db,c,cmd("deleteProgress",t).put("entryId","delete-extra"));
            check(t.getString("status").equals("completed")&&t.getDouble("actualAmount")==3,"Completed work stays completed if remaining quantity still reaches its target");
        }
    }
    void verifyEditProgress()throws Exception{
        Clock c=new Clock();try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            JSONObject t=saved(db,c,save(item("edit-task","task","课程第1至4节",4,"节"),null));
            t=saved(db,c,entry(t,"edit-one",1,5));t=saved(db,c,entry(t,"edit-two",1,0));t=saved(db,c,entry(t,"edit-three",3,7));long at=t.getJSONArray("entries").getJSONObject(2).getLong("at");t=saved(db,c,cmd("complete",t));
            JSONObject request=cmd("updateProgress",t).put("entryId","edit-three").put("editId","revision-one").put("amount",1).put("minutes",4).put("note","应为一节");t=saved(db,c,request);
            check(t.getDouble("actualAmount")==3&&t.getJSONArray("entries").length()==3,"Editing three to one changes five to three without adding history");
            JSONObject ev=t.getJSONArray("entries").getJSONObject(2);check(ev.getLong("at")==at&&ev.getString("id").equals("edit-three")&&ev.getString("note").equals("应为一节")&&ev.has("editedAt"),"Editing preserves entry identity and original occurrence time while updating its note");
            check(t.getDouble("actualMinutes")==9&&t.getString("status").equals("active")&&!t.has("completedAt"),"Editing manual time replaces its old contribution and reopens incomplete work");
            long version=t.getLong("updatedAt");check(saved(db,c,request).getLong("updatedAt")==version,"Lost edit reply can be retried without applying the difference twice");
            final JSONObject conflict=new JSONObject(request.toString()).put("amount",2);rejects(()->run(db,c,conflict),"An edit id cannot be reused with different data");
            final JSONObject stale=new JSONObject(request.toString()).put("editId","revision-stale");rejects(()->run(db,c,stale),"A stale editor cannot overwrite a newer correction");
            final JSONObject invalid=cmd("updateProgress",t).put("entryId","edit-three").put("editId","revision-invalid").put("amount",-1).put("minutes",0);rejects(()->run(db,c,invalid),"Negative correction is rejected transactionally");
            t=saved(db,c,cmd("start",t));c.add(120000);t=saved(db,c,cmd("updateProgress",t).put("entryId","edit-three").put("editId","revision-running").put("amount",1).put("minutes",3).put("note","只改手动耗时"));
            check(t.getLong("timerStartedAt")>0&&t.getDouble("actualMinutes")==8&&t.getDouble("elapsedMinutes")==10,"Editing a historical entry leaves the monotonic timer running");
            JSONArray backup=GoalTaskStore.export(db,c);try(SQLiteDatabase restored=SQLiteDatabase.create(null)){GoalTaskStore.restore(restored,backup);JSONObject restoredTask=get(restored,c,"edit-task");check(restoredTask.getDouble("actualAmount")==3&&restoredTask.getJSONArray("entries").getJSONObject(2).getDouble("amount")==1,"Backup and restore preserve corrected history and totals");}
            t=saved(db,c,cmd("deleteProgress",t).put("entryId","edit-three"));check(t.getDouble("actualAmount")==2&&t.getDouble("actualMinutes")==5,"Deleting an edited entry deducts its corrected contribution");
            final JSONObject deleted=cmd("updateProgress",t).put("entryId","edit-three").put("editId","revision-deleted").put("amount",1).put("minutes",0);rejects(()->run(db,c,deleted),"Editing a deleted entry cannot resurrect it");
        }
    }
    void verifyRollup()throws Exception{
        Clock c=new Clock();try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            JSONObject g=saved(db,c,save(item("course-goal","goal","短视频课程",28,"节"),null)),other=saved(db,c,save(item("course-other","goal","其他课程",28,"节"),null));
            JSONObject t=saved(db,c,save(item("course-day-one","task","第一天课程",4,"节").put("parentId","course-goal"),null));t=saved(db,c,entry(t,"course-entry",4,239.8));t=saved(db,c,cmd("complete",t));
            g=get(db,c,"course-goal");check(g.getDouble("actualAmount")==4&&g.getDouble("elapsedMinutes")==239.8&&g.getDouble("ownAmount")==0,"Completed four-lesson task contributes four of twenty-eight and its exact time to the goal");
            check(g.getJSONArray("entries").length()==0&&g.getDouble("childAmount")==4,"Derived child contribution never creates duplicate parent progress entries");
            long version=g.getLong("updatedAt");for(int i=0;i<3;i++)check(get(db,c,"course-goal").getLong("updatedAt")==version&&get(db,c,"course-goal").getDouble("actualAmount")==4,"Reading existing goals repeats no addition and preserves version "+i);
            JSONArray backup=GoalTaskStore.export(db,c);JSONObject backed=null;for(int i=0;i<backup.length();i++)if(backup.getJSONObject(i).getString("id").equals("course-goal"))backed=backup.getJSONObject(i);check(backed.getDouble("actualAmount")==0&&backed.getDouble("actualMinutes")==0,"Backup stores only the parent's own ledger instead of aggregated totals");
            t=saved(db,c,cmd("updateProgress",t).put("entryId","course-entry").put("editId","course-correction").put("amount",3).put("minutes",180));g=get(db,c,"course-goal");check(g.getDouble("actualAmount")==3&&g.getDouble("actualMinutes")==180&&g.getLong("updatedAt")>version,"Child correction synchronizes parent quantity, time and concurrency version");
            JSONObject move=new JSONObject(t.toString()).put("parentId","course-other");t=saved(db,c,save(move,t));check(get(db,c,"course-goal").getDouble("actualAmount")==0&&get(db,c,"course-other").getDouble("actualAmount")==3,"Relinking removes contribution from old goal and adds to new goal");
            t=saved(db,c,cmd("deleteProgress",t).put("entryId","course-entry"));check(get(db,c,"course-other").getDouble("actualAmount")==0&&get(db,c,"course-other").getDouble("actualMinutes")==0,"Deleting child progress synchronizes its parent back to zero");
            t=saved(db,c,entry(t,"course-new",4,5));other=get(db,c,"course-other");other=saved(db,c,entry(other,"course-own",24,2));other=saved(db,c,cmd("complete",other));check(other.getDouble("actualAmount")==28&&other.getDouble("ownAmount")==24&&other.getString("status").equals("completed"),"Goal completion validates combined direct and linked quantity");
            JSONArray completedBackup=GoalTaskStore.export(db,c);try(SQLiteDatabase restored=SQLiteDatabase.create(null)){GoalTaskStore.restore(restored,completedBackup);JSONObject re=get(restored,c,"course-other");check(re.getDouble("actualAmount")==28&&re.getDouble("actualMinutes")==7&&re.getString("status").equals("completed"),"Completed aggregate goal restores without doubling child contribution");}
            final JSONObject staleParent=cmd("complete",other);t=saved(db,c,cmd("updateProgress",t).put("entryId","course-new").put("editId","course-reduce").put("amount",3).put("minutes",5));other=get(db,c,"course-other");check(other.getString("status").equals("active")&&!other.has("completedAt")&&other.getDouble("actualAmount")==27,"Lower child quantity reopens a previously completed parent");rejects(()->run(db,c,staleParent),"Stale parent completion cannot race a child correction");
            JSONObject mixed=saved(db,c,save(item("course-mixed","task","写学习心得",1,"篇").put("parentId","course-other"),null));mixed=saved(db,c,entry(mixed,"course-mixed-entry",1,7));other=get(db,c,"course-other");check(other.getDouble("actualAmount")==27&&other.getDouble("actualMinutes")==14&&other.getInt("excludedQuantityChildren")==1,"Different units contribute time but never combine incompatible quantities");
            t=saved(db,c,cmd("start",t));c.add(120000);other=get(db,c,"course-other");check(other.getDouble("elapsedMinutes")==16&&other.getBoolean("childTimerRunning"),"Parent live elapsed time includes its running child without settling either timer");
            t=saved(db,c,cmd("pause",t));run(db,c,cmd("delete",t));check(get(db,c,"course-other").getDouble("actualAmount")==24&&get(db,c,"course-other").getDouble("actualMinutes")==9,"Deleting an action removes both its quantity and its settled timer contribution");
        }
    }
    @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{verify();verifyDeleteProgress();verifyEditProgress();verifyRollup();ok=true;out.putString("stream","PASS GoalTaskStoreTest: "+passed+" checks\n");}catch(Throwable e){out.putString("stream","FAIL GoalTaskStoreTest after "+passed+": "+android.util.Log.getStackTraceString(e));}finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
