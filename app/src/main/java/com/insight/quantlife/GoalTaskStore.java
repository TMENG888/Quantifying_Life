package com.insight.quantlife;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;

/** Local goal/action ledger. Quantity, elapsed time and child completion are independent measures. */
public final class GoalTaskStore {
    private static final int MAX_ITEMS=50000,MAX_ENTRIES=50000;
    private static final double MAX_AMOUNT=1000000000d,MAX_MINUTES=10000000d;
    private GoalTaskStore(){}
    private static MeditationStore.Clock clock(){return new MeditationStore.Clock(){public long wall(){return System.currentTimeMillis();}public long elapsed(){return SystemClock.elapsedRealtime();}public int boot(){return Integer.MIN_VALUE;}};}
    public static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS goal_tasks(id TEXT PRIMARY KEY,kind TEXT NOT NULL,parent_id TEXT NOT NULL,status TEXT NOT NULL,running INTEGER NOT NULL,updated INTEGER NOT NULL,payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS goal_tasks_parent ON goal_tasks(parent_id)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS goal_tasks_one_timer ON goal_tasks(running) WHERE running=1");
        db.execSQL("CREATE TABLE IF NOT EXISTS goal_task_entries(id TEXT PRIMARY KEY,task_id TEXT NOT NULL,payload TEXT NOT NULL)");
    }
    private static JSONObject find(SQLiteDatabase db,String id)throws Exception{try(Cursor c=db.rawQuery("SELECT payload FROM goal_tasks WHERE id=?",new String[]{id})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
    private static List<JSONObject> rows(SQLiteDatabase db)throws Exception{List<JSONObject> out=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT payload FROM goal_tasks ORDER BY CASE status WHEN 'active' THEN 0 ELSE 1 END,updated DESC,id",null)){while(c.moveToNext())out.add(new JSONObject(c.getString(0)));}return out;}
    private static void put(SQLiteDatabase db,JSONObject r)throws Exception{
        ContentValues v=new ContentValues();v.put("id",r.getString("id"));v.put("kind",r.getString("kind"));v.put("parent_id",r.getString("parentId"));v.put("status",r.getString("status"));v.put("running",r.optLong("timerStartedAt")>0?1:0);v.put("updated",r.getLong("updatedAt"));v.put("payload",r.toString());
        if(db.insertWithOnConflict("goal_tasks",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new Exception("任务保存失败，请重试");
    }
    private static String id(JSONObject p,String key)throws Exception{String s=text(p,key,"",100);if(!s.matches("[A-Za-z0-9_-]{3,100}"))throw new Exception("任务或进度编号无效");return s;}
    private static String text(JSONObject p,String key,String fallback,int limit)throws Exception{
        if(!p.has(key))return fallback;Object value=p.get(key);if(!(value instanceof String))throw new Exception("任务字段格式无效："+key);String s=((String)value).trim();if(s.length()>limit)throw new Exception("任务字段过长："+key);return s;
    }
    private static double number(JSONObject p,String key,double fallback,double max)throws Exception{
        if(!p.has(key))return fallback;Object v=p.get(key);if(!(v instanceof Number))throw new Exception("数量和分钟必须是数字");double n=((Number)v).doubleValue();if(Double.isNaN(n)||Double.isInfinite(n)||n<0||n>max)throw new Exception("数量或分钟超出有效范围");return n;
    }
    private static String date(JSONObject p,String key)throws Exception{
        String s=text(p,key,"",19);if(s.isEmpty())return s;
        if(!s.matches("(?:19|20|21|22)\\d{2}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?"))throw new Exception("请填写有效的本地日期与时间");
        SimpleDateFormat f=new SimpleDateFormat(s.length()==16?"yyyy-MM-dd'T'HH:mm":"yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT);f.setLenient(false);ParsePosition pos=new ParsePosition(0);Date parsed=f.parse(s,pos);if(parsed==null||pos.getIndex()!=s.length()||!f.format(parsed).equals(s))throw new Exception("任务日期或时间无效");return s;
    }
    private static long millis(String date)throws Exception{if(date.isEmpty())return 0;SimpleDateFormat f=new SimpleDateFormat(date.length()==16?"yyyy-MM-dd'T'HH:mm":"yyyy-MM-dd'T'HH:mm:ss",Locale.ROOT);f.setLenient(false);return f.parse(date).getTime();}
    private static JSONObject metadata(JSONObject input)throws Exception{
        String key=id(input,"id"),kind=text(input,"kind","task",8),title=text(input,"title","",300),area=text(input,"area","life",12),parent=text(input,"parentId","",100),unit=text(input,"targetUnit","项",30);
        if(!Arrays.asList("goal","task").contains(kind)||!Arrays.asList("learning","work","life").contains(area)||title.isEmpty()||unit.isEmpty())throw new Exception("请填写任务名称、类别和计量单位");
        if(!parent.isEmpty()&&!parent.matches("[A-Za-z0-9_-]{3,100}"))throw new Exception("关联目标编号无效");if(kind.equals("goal")&&!parent.isEmpty())throw new Exception("长期目标不能作为其他任务的子项");if(key.equals(parent))throw new Exception("任务不能关联自身");
        double target=number(input,"targetAmount",1,MAX_AMOUNT);if(target<=0)throw new Exception("目标数量必须大于零");
        JSONObject r=new JSONObject().put("id",key).put("kind",kind).put("parentId",parent).put("title",title).put("area",area).put("targetAmount",target).put("targetUnit",unit).put("plannedMinutes",number(input,"plannedMinutes",0,MAX_MINUTES)).put("deadline",date(input,"deadline")).put("scheduledStart",date(input,"scheduledStart")).put("scheduledEnd",date(input,"scheduledEnd")).put("note",text(input,"note","",10000));
        validateTimes(r);return r;
    }
    private static void validateTimes(JSONObject r)throws Exception{
        long a=millis(r.getString("scheduledStart")),b=millis(r.getString("scheduledEnd")),due=millis(r.getString("deadline"));
        if((a>0)!=(b>0))throw new Exception("执行时段需要同时填写开始和结束时间");if(a>0&&b<=a)throw new Exception("执行结束时间必须晚于开始时间");if(due>0&&b>due)throw new Exception("执行时段不能晚于截止时间");
    }
    private static void validateParent(SQLiteDatabase db,JSONObject r,Map<String,JSONObject> additions)throws Exception{
        String parent=r.getString("parentId");if(parent.isEmpty())return;JSONObject goal=additions==null?null:additions.get(parent);if(goal==null)goal=find(db,parent);
        if(goal==null||!"goal".equals(goal.getString("kind")))throw new Exception("找不到关联的长期目标，请刷新后选择");
        long due=millis(goal.getString("deadline"));if(due>0&&(millis(r.getString("deadline"))>due||millis(r.getString("scheduledEnd"))>due))throw new Exception("子任务应安排在长期目标截止时间之前");
    }
    private static void validateChildren(SQLiteDatabase db,JSONObject goal)throws Exception{
        if(!goal.getString("kind").equals("goal"))return;long due=millis(goal.getString("deadline"));if(due<=0)return;
        try(Cursor c=db.rawQuery("SELECT payload FROM goal_tasks WHERE parent_id=?",new String[]{goal.getString("id")})){while(c.moveToNext()){JSONObject child=new JSONObject(c.getString(0));if(millis(child.getString("deadline"))>due||millis(child.getString("scheduledEnd"))>due)throw new Exception("已有子任务晚于这个截止时间，请先调整子任务");}}
    }
    private static void version(JSONObject p,JSONObject existing)throws Exception{
        if(!p.has("expectedVersion"))throw new Exception("任务版本缺失，请刷新后重试");
        if(existing==null){if(!p.isNull("expectedVersion"))throw new Exception("任务已不存在，请刷新后重试");return;}
        if(p.isNull("expectedVersion")||!(p.get("expectedVersion") instanceof Number)||p.getLong("expectedVersion")!=existing.getLong("updatedAt"))throw new Exception("任务已更新，请刷新后再操作");
    }
    private static void bump(JSONObject r,MeditationStore.Clock c)throws Exception{r.put("updatedAt",Math.max(c.wall(),r.optLong("updatedAt")+1));}
    private static boolean invalidOrigin(JSONObject r,MeditationStore.Clock c){return r.optLong("timerStartedAt")>0&&((c.boot()!=Integer.MIN_VALUE&&r.optInt("_timerBoot",c.boot())!=c.boot())||c.elapsed()<r.optLong("_timerElapsed"));}
    private static double elapsed(JSONObject r,MeditationStore.Clock c){double total=r.optDouble("actualMinutes");if(r.optLong("timerStartedAt")>0&&!invalidOrigin(r,c))total+=Math.max(0,c.elapsed()-r.optLong("_timerElapsed"))/60000d;return Math.min(MAX_MINUTES,total);}
    private static void settle(JSONObject r,MeditationStore.Clock c)throws Exception{if(invalidOrigin(r,c))r.put("timerRecovered",true);r.put("actualMinutes",elapsed(r,c)).put("timerStartedAt",0);r.remove("_timerBoot");r.remove("_timerElapsed");}
    private static JSONObject visible(JSONObject item,MeditationStore.Clock c)throws Exception{
        JSONObject r=new JSONObject(item.toString());r.put("elapsedMinutes",elapsed(r,c));if(invalidOrigin(r,c))r.put("timerStartedAt",0).put("timerRecovered",true);r.remove("_timerBoot");r.remove("_timerElapsed");return r;
    }
    private static Map<String,List<JSONObject>> children(List<JSONObject> all){Map<String,List<JSONObject>> out=new HashMap<>();for(JSONObject item:all){String parent=item.optString("parentId");if(!parent.isEmpty()){if(!out.containsKey(parent))out.put(parent,new ArrayList<>());out.get(parent).add(item);}}return out;}
    private static JSONObject projected(JSONObject item,Map<String,List<JSONObject>> children,MeditationStore.Clock c)throws Exception{
        JSONObject r=visible(item,c);if(!item.optString("kind").equals("goal"))return r;
        double amount=0,minutes=0,live=0;int matched=0,excluded=0;boolean running=false;
        List<JSONObject> linked=children.get(item.getString("id"));if(linked!=null)for(JSONObject child:linked){
            if(child.optString("targetUnit").trim().equals(item.optString("targetUnit").trim())){amount+=child.optDouble("actualAmount");matched++;}else excluded++;
            minutes+=child.optDouble("actualMinutes");live+=elapsed(child,c);running|=child.optLong("timerStartedAt")>0&&!invalidOrigin(child,c);
        }
        return r.put("ownAmount",item.optDouble("actualAmount")).put("ownMinutes",item.optDouble("actualMinutes")).put("ownElapsedMinutes",r.getDouble("elapsedMinutes"))
            .put("childAmount",amount).put("childMinutes",minutes).put("childElapsedMinutes",live).put("childTimerRunning",running).put("matchedChildren",matched).put("excludedQuantityChildren",excluded)
            .put("actualAmount",item.optDouble("actualAmount")+amount).put("actualMinutes",item.optDouble("actualMinutes")+minutes).put("elapsedMinutes",r.getDouble("elapsedMinutes")+live);
    }
    private static double totalAmount(SQLiteDatabase db,JSONObject item,MeditationStore.Clock c)throws Exception{return item.optString("kind").equals("goal")?projected(item,children(rows(db)),c).getDouble("actualAmount"):item.getDouble("actualAmount");}
    private static JSONObject snapshot(SQLiteDatabase db,MeditationStore.Clock c)throws Exception{List<JSONObject> all=rows(db);Map<String,List<JSONObject>> linked=children(all);JSONArray result=new JSONArray();for(JSONObject r:all)result.put(projected(r,linked,c));return new JSONObject().put("items",result);}
    private static JSONObject save(SQLiteDatabase db,JSONObject p,MeditationStore.Clock c)throws Exception{
        JSONObject r=metadata(p.getJSONObject("item")),before=find(db,r.getString("id"));version(p,before);
        if(before!=null){if(!before.getString("kind").equals(r.getString("kind")))throw new Exception("目标和行动类型不能直接互换");for(String k:new String[]{"createdAt","updatedAt","actualAmount","actualMinutes","status","entries","timerStartedAt","_timerElapsed","_timerBoot","timerRecovered","completedAt","manualCompleted"})if(before.has(k))r.put(k,before.get(k));}
        else r.put("createdAt",c.wall()).put("updatedAt",c.wall()).put("actualAmount",0).put("actualMinutes",0).put("status","active").put("entries",new JSONArray()).put("timerStartedAt",0);
        if(before!=null&&before.getDouble("actualAmount")>0&&!before.getString("targetUnit").equals(r.getString("targetUnit")))throw new Exception("已有完成数量，不能改变计量单位；请新建任务");
        validateParent(db,r,null);validateChildren(db,r);bump(r,c);put(db,r);return r;
    }
    private static JSONObject event(SQLiteDatabase db,String id)throws Exception{try(Cursor c=db.rawQuery("SELECT payload FROM goal_task_entries WHERE id=?",new String[]{id})){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
    private static void putEvent(SQLiteDatabase db,String taskId,JSONObject entry)throws Exception{ContentValues v=new ContentValues();v.put("id",entry.getString("id"));v.put("task_id",taskId);v.put("payload",entry.toString());if(db.insertOrThrow("goal_task_entries",null,v)<0)throw new Exception("任务进度保存失败");}
    private static boolean sameEvent(JSONObject a,JSONObject b){return a.optString("taskId").equals(b.optString("taskId"))&&a.optDouble("amount")==b.optDouble("amount")&&a.optDouble("minutes")==b.optDouble("minutes")&&a.optString("note").equals(b.optString("note"));}
    private static JSONObject progress(SQLiteDatabase db,JSONObject p,JSONObject r,MeditationStore.Clock c)throws Exception{
        double amount=number(p,"amount",0,MAX_AMOUNT),minutes=number(p,"minutes",0,MAX_MINUTES);if(amount==0&&minutes==0)throw new Exception("请填写本次完成数量或实际用时");
        JSONObject entry=new JSONObject().put("id",id(p,"entryId")).put("taskId",r.getString("id")).put("amount",amount).put("minutes",minutes).put("note",text(p,"note","",10000)).put("at",c.wall());
        JSONObject prior=event(db,entry.getString("id"));if(prior!=null){if(!sameEvent(prior,entry))throw new Exception("进度编号已用于另一笔记录，请刷新重试");if(prior.optBoolean("deleted"))throw new Exception("这条进度已删除，请重新登记");return r;}
        version(p,r);if(!r.getString("status").equals("active"))throw new Exception("请先重新开启任务，再记录进度");if(invalidOrigin(r,c))settle(r,c);if(r.optLong("timerStartedAt")>0&&minutes>0)throw new Exception("正在计时，请先暂停后再补录分钟，避免重复计时");
        double total=r.getDouble("actualAmount")+amount,time=r.getDouble("actualMinutes")+minutes;if(total>MAX_AMOUNT||time>MAX_MINUTES)throw new Exception("累计数量或用时超出范围");
        JSONArray entries=r.getJSONArray("entries");if(entries.length()>=MAX_ENTRIES)throw new Exception("本任务进度记录过多，请新建后续任务");entries.put(entry);r.put("actualAmount",total).put("actualMinutes",time);bump(r,c);putEvent(db,r.getString("id"),entry);put(db,r);return r;
    }
    private static JSONObject updateProgress(SQLiteDatabase db,JSONObject p,JSONObject r,MeditationStore.Clock c)throws Exception{
        String entryId=id(p,"entryId"),editId=id(p,"editId");double amount=number(p,"amount",0,MAX_AMOUNT),minutes=number(p,"minutes",0,MAX_MINUTES);
        if(amount==0&&minutes==0)throw new Exception("请填写本条完成量或手动耗时；不需要的进度可删除");
        JSONArray entries=r.getJSONArray("entries");int index=-1;JSONObject before=null;
        for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).getString("id").equals(entryId)){index=i;before=entries.getJSONObject(i);break;}
        if(before==null)throw new Exception("这条进度已不存在，请刷新后重试");
        JSONObject entry=new JSONObject(before.toString()).put("taskId",r.getString("id")).put("amount",amount).put("minutes",minutes).put("note",text(p,"note","",10000));
        if(before.optString("lastEditId").equals(editId)){if(!sameEvent(before,entry))throw new Exception("编辑编号已使用，请重新打开进度后修改");return r;}
        version(p,r);
        double total=Math.max(0,r.getDouble("actualAmount")-before.getDouble("amount")+amount),time=Math.max(0,r.getDouble("actualMinutes")-before.getDouble("minutes")+minutes);
        if(total>MAX_AMOUNT||time>MAX_MINUTES)throw new Exception("累计数量或用时超出范围");
        entry.put("editedAt",c.wall()).put("lastEditId",editId);entries.put(index,entry);r.put("actualAmount",total).put("actualMinutes",time);
        if(r.getString("status").equals("completed")&&!r.optBoolean("manualCompleted")&&totalAmount(db,r,c)<r.getDouble("targetAmount")){r.put("status","active");r.remove("completedAt");}
        ContentValues v=new ContentValues();v.put("payload",entry.toString());if(db.update("goal_task_entries",v,"id=? AND task_id=?",new String[]{entryId,r.getString("id")})==0)putEvent(db,r.getString("id"),entry);
        bump(r,c);put(db,r);return r;
    }
    private static JSONObject deleteProgress(SQLiteDatabase db,JSONObject p,JSONObject r,MeditationStore.Clock c)throws Exception{
        String entryId=id(p,"entryId");JSONArray entries=r.getJSONArray("entries");JSONObject removed=null;JSONArray keep=new JSONArray();
        for(int i=0;i<entries.length();i++){JSONObject one=entries.getJSONObject(i);if(one.getString("id").equals(entryId))removed=one;else keep.put(one);}
        if(removed==null){JSONObject prior=event(db,entryId);if(prior!=null&&prior.optBoolean("deleted")&&prior.optString("taskId").equals(r.getString("id")))return r;throw new Exception("这条进度已不存在，请刷新后重试");}
        version(p,r);
        r.put("actualAmount",Math.max(0,r.getDouble("actualAmount")-removed.getDouble("amount"))).put("actualMinutes",Math.max(0,r.getDouble("actualMinutes")-removed.getDouble("minutes"))).put("entries",keep);
        if(r.getString("status").equals("completed")&&!r.optBoolean("manualCompleted")&&totalAmount(db,r,c)<r.getDouble("targetAmount")){r.put("status","active");r.remove("completedAt");}
        // Keep an invisible tombstone so a delayed retry of the old progress request cannot restore it.
        JSONObject tombstone=new JSONObject(removed.toString()).put("taskId",r.getString("id")).put("deleted",true).put("deletedAt",c.wall());
        ContentValues v=new ContentValues();v.put("payload",tombstone.toString());
        if(db.update("goal_task_entries",v,"id=? AND task_id=?",new String[]{entryId,r.getString("id")})==0)putEvent(db,r.getString("id"),tombstone);
        bump(r,c);put(db,r);return r;
    }
    private static JSONObject mutate(SQLiteDatabase db,JSONObject p,MeditationStore.Clock c)throws Exception{
        String command=p.optString("command","list");if(command.equals("save"))return save(db,p,c);
        String key=id(p,"id");JSONObject r=find(db,key);if(command.equals("delete")&&r==null)return null;if(r==null)throw new Exception("任务已不存在，请刷新后重试");
        if(command.equals("progress"))return progress(db,p,r,c);if(command.equals("updateProgress"))return updateProgress(db,p,r,c);if(command.equals("deleteProgress"))return deleteProgress(db,p,r,c);version(p,r);
        if(command.equals("delete")){
            try(Cursor child=db.rawQuery("SELECT id FROM goal_tasks WHERE parent_id=? LIMIT 1",new String[]{key})){if(child.moveToFirst()&&!p.optBoolean("detachChildren"))throw new Exception("此目标还有子任务，请先解除关联或删除子任务");}
            if(r.optLong("timerStartedAt")>0&&!invalidOrigin(r,c)&&!p.optBoolean("stopTimer"))throw new Exception("请先暂停任务计时，再删除");
            if(p.optBoolean("detachChildren"))for(JSONObject child:rows(db))if(child.optString("parentId").equals(key)){child.put("parentId","");bump(child,c);put(db,child);}
            db.delete("goal_task_entries","task_id=?",new String[]{key});db.delete("goal_tasks","id=?",new String[]{key});return null;
        }
        boolean changed=true;
        if(command.equals("start")){throw new Exception("任务计时功能已移除；仍可设置执行时段、截止时间与计划用时");
        }else if(command.equals("pause")){if(r.optLong("timerStartedAt")>0)settle(r,c);else changed=false;}
        else if(command.equals("check")){
            if(!r.getString("status").equals("completed")){settle(r,c);r.put("status","completed").put("completedAt",c.wall()).put("manualCompleted",true);}else changed=false;
        }else if(command.equals("complete")){
            if(totalAmount(db,r,c)<r.getDouble("targetAmount"))throw new Exception("实际完成数量尚未达到目标，请先记录进度");
            if(!r.getString("status").equals("completed")){settle(r,c);r.put("status","completed").put("completedAt",c.wall());}else changed=false;
        }else if(command.equals("reopen")){if(r.getString("status").equals("completed")){r.put("status","active");r.remove("completedAt");r.remove("manualCompleted");}else changed=false;}
        else throw new Exception("未知任务操作");
        if(changed){bump(r,c);put(db,r);}return r;
    }
    public static JSONObject execute(SQLiteDatabase db,JSONObject p)throws Exception{return execute(db,p,clock());}
    public static synchronized JSONObject execute(SQLiteDatabase db,JSONObject p,MeditationStore.Clock c)throws Exception{
        create(db);db.beginTransaction();try{
            String command=p.optString("command","list");JSONObject saved=null;List<JSONObject> before=rows(db);
            if(command.equals("batch")){
                JSONArray commands=p.getJSONArray("commands");if(commands.length()==0||commands.length()>20)throw new Exception("一次最多提交20个任务");
                for(int i=0;i<commands.length();i++){JSONObject one=commands.getJSONObject(i);if(!one.optString("command").equals("save"))throw new Exception("批量操作只支持新建或编辑任务");saved=save(db,one,c);}
            }else if(!command.equals("list"))saved=mutate(db,p,c);
            // Parent totals are derived, never copied into its own ledger. Bump its version
            // when a child changes so stale parent editors cannot finalize outdated totals.
            List<JSONObject> after=rows(db);Map<String,JSONObject> old=new HashMap<>();for(JSONObject r:before)old.put(r.getString("id"),r);Set<String> affected=new HashSet<>();
            for(JSONObject r:after){JSONObject prior=old.remove(r.getString("id"));if(prior==null||prior.optLong("updatedAt")!=r.optLong("updatedAt")){affected.add(r.optString("parentId"));if(prior!=null)affected.add(prior.optString("parentId"));}}
            for(JSONObject r:old.values())affected.add(r.optString("parentId"));Map<String,List<JSONObject>> linked=children(after);
            for(JSONObject r:after){
                boolean changed=affected.contains(r.getString("id"));
                if(r.optLong("timerStartedAt")>0){settle(r,c);changed=true;}
                boolean reached=projected(r,linked,c).getDouble("actualAmount")+1e-9>=r.getDouble("targetAmount");
                if(!r.optBoolean("manualCompleted")){
                    if(reached&&!r.optString("status").equals("completed")){r.put("status","completed").put("completedAt",c.wall());changed=true;}
                    else if(!reached&&r.optString("status").equals("completed")){r.put("status","active");r.remove("completedAt");changed=true;}
                }
                if(changed){bump(r,c);put(db,r);}
            }
            JSONObject result=snapshot(db,c);if(saved!=null){JSONArray all=result.getJSONArray("items");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("id").equals(saved.getString("id")))result.put("saved",all.getJSONObject(i));}if(command.equals("delete"))result.put("deleted",p.getString("id"));db.setTransactionSuccessful();return result;
        }finally{db.endTransaction();}
    }
    public static JSONArray export(SQLiteDatabase db)throws Exception{return export(db,clock());}
    public static synchronized JSONArray export(SQLiteDatabase db,MeditationStore.Clock c)throws Exception{
        create(db);JSONArray out=new JSONArray();for(JSONObject item:rows(db)){JSONObject r=visible(item,c);r.put("actualMinutes",r.getDouble("elapsedMinutes")).put("timerStartedAt",0);r.remove("elapsedMinutes");out.put(r);}return out;
    }
    public static synchronized int restore(SQLiteDatabase db,JSONArray items)throws Exception{
        if(items==null)return 0;if(items.length()>MAX_ITEMS)throw new Exception("目标任务备份过大");create(db);db.beginTransaction();try{
            Map<String,JSONObject> incoming=new LinkedHashMap<>();Set<String> entryIds=new HashSet<>();
            for(int i=0;i<items.length();i++){
                JSONObject source=items.getJSONObject(i),r=metadata(source);String key=r.getString("id");if(find(db,key)!=null)continue;if(incoming.containsKey(key))throw new Exception("备份中出现重复的任务编号");
                String state=text(source,"status","active",12);if(!Arrays.asList("active","completed").contains(state))throw new Exception("任务备份状态无效");
                double amount=number(source,"actualAmount",0,MAX_AMOUNT),minutes=number(source,"actualMinutes",0,MAX_MINUTES);if(source.has("manualCompleted")&&!(source.get("manualCompleted") instanceof Boolean))throw new Exception("任务勾选状态无效");
                if(state.equals("completed")&&!source.optBoolean("manualCompleted")&&r.getString("kind").equals("task")&&amount<r.getDouble("targetAmount"))throw new Exception("已完成任务的数量与目标不一致");
                long created=source.getLong("createdAt"),updated=source.getLong("updatedAt");if(created<=0||updated<created)throw new Exception("任务备份时间无效");
                JSONArray entries=source.optJSONArray("entries");if(entries==null)entries=new JSONArray();if(entries.length()>MAX_ENTRIES)throw new Exception("任务进度备份过多");JSONArray clean=new JSONArray();
                double entryAmount=0,entryMinutes=0;for(int j=0;j<entries.length();j++){
                    JSONObject ev=entries.getJSONObject(j);String entryId=id(ev,"id");if(!entryIds.add(entryId)||event(db,entryId)!=null)throw new Exception("任务进度编号冲突，未导入备份");
                    double n=number(ev,"amount",0,MAX_AMOUNT),m=number(ev,"minutes",0,MAX_MINUTES);long at=ev.getLong("at");if(at<=0||!key.equals(ev.optString("taskId",key))||(n==0&&m==0))throw new Exception("任务进度备份无效");
                    entryAmount+=n;entryMinutes+=m;clean.put(new JSONObject().put("id",entryId).put("taskId",key).put("amount",n).put("minutes",m).put("note",text(ev,"note","",10000)).put("at",at));
                }
                if(Math.abs(entryAmount-amount)>1e-6||entryMinutes-minutes>1e-6)throw new Exception("任务累计进度与历史明细不一致");
                r.put("actualAmount",amount).put("actualMinutes",minutes).put("status",state).put("createdAt",created).put("updatedAt",updated).put("entries",clean).put("timerStartedAt",0);if(state.equals("completed")){r.put("completedAt",source.optLong("completedAt",updated));if(source.optBoolean("manualCompleted"))r.put("manualCompleted",true);}incoming.put(key,r);
            }
            for(JSONObject r:incoming.values())validateParent(db,r,incoming);
            for(JSONObject r:incoming.values()){put(db,r);for(int j=0;j<r.getJSONArray("entries").length();j++)putEvent(db,r.getString("id"),r.getJSONArray("entries").getJSONObject(j));}
            Map<String,List<JSONObject>> linked=children(rows(db));for(JSONObject r:incoming.values())if(r.optString("kind").equals("goal")&&r.optString("status").equals("completed")&&!r.optBoolean("manualCompleted")&&projected(r,linked,clock()).getDouble("actualAmount")<r.getDouble("targetAmount"))throw new Exception("已完成目标的数量与关联行动不一致");
            db.setTransactionSuccessful();return incoming.size();
        }finally{db.endTransaction();}
    }
}
