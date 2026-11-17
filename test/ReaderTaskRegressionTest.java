package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.net.Uri;import android.database.sqlite.SQLiteDatabase;
import com.insight.quantlife.*;import org.json.*;import java.io.*;import java.lang.reflect.*;import java.nio.file.Files;

public final class ReaderTaskRegressionTest extends DocumentEditorTest {
 boolean disposable;
 @Override public void onCreate(Bundle b){disposable=b!=null&&"127.0.0.1:16384".equals(b.getString("disposableEmulator"));super.onCreate(b);}
 JSONObject item(String id,String kind)throws Exception{return new JSONObject().put("id",id).put("kind",kind).put("title","阅读一本书").put("area","learning").put("parentId","").put("targetAmount",1).put("targetUnit","项").put("plannedMinutes",30).put("deadline","2026-12-01T18:00").put("scheduledStart","2026-11-17T09:00").put("scheduledEnd","2026-11-17T10:00");}
 JSONObject cmd(String op,JSONObject r)throws Exception{return new JSONObject().put("command",op).put("id",r.getString("id")).put("expectedVersion",r.getLong("updatedAt"));}
 JSONObject save(SQLiteDatabase db,JSONObject r)throws Exception{return GoalTaskStore.execute(db,new JSONObject().put("command","save").put("item",r).put("expectedVersion",JSONObject.NULL)).getJSONObject("saved");}
 JSONObject get(SQLiteDatabase db,String id)throws Exception{JSONArray a=GoalTaskStore.execute(db,new JSONObject()).getJSONArray("items");for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return a.getJSONObject(i);throw new Exception("missing task");}
 void ledger()throws Exception{try(SQLiteDatabase db=SQLiteDatabase.create(null)){
  JSONObject g=save(db,item("reader-goal","goal")),t=save(db,item("reader-task","task").put("parentId","reader-goal"));
  t=GoalTaskStore.execute(db,cmd("progress",t).put("entryId","reader-entry").put("amount",1).put("minutes",0)).getJSONObject("saved");
  check(t.getString("status").equals("completed")&&!t.optBoolean("manualCompleted"),"Reaching target auto completes task without manual check");
  check(get(db,"reader-goal").getString("status").equals("completed"),"Matching-unit linked quantity auto completes parent goal");
  t=GoalTaskStore.execute(db,cmd("updateProgress",t).put("entryId","reader-entry").put("editId","reader-edit").put("amount",0.5).put("minutes",0)).getJSONObject("saved");
  check(t.getString("status").equals("active")&&get(db,"reader-goal").getString("status").equals("active"),"Reducing progress reopens auto completed task and goal");
  boolean rejected=false;try{GoalTaskStore.execute(db,cmd("start",t));}catch(Exception ex){rejected=ex.getMessage().contains("已移除");}check(rejected,"Removed task timer rejects new starts");
  check(t.getInt("plannedMinutes")==30&&t.getString("deadline").equals("2026-12-01T18:00")&&t.getString("scheduledStart").equals("2026-11-17T09:00"),"Deadline schedule and planned duration survive progress edits");
  t=GoalTaskStore.execute(db,cmd("updateProgress",t).put("entryId","reader-entry").put("editId","reader-edit-full").put("amount",1).put("minutes",0)).getJSONObject("saved");
  db.execSQL("UPDATE goal_tasks SET status='active',payload=json_set(payload,'$.status','active') WHERE id='reader-task'");
  check(get(db,"reader-task").getString("status").equals("completed"),"Old already reached task is repaired on next read");
  t=get(db,"reader-task");t=GoalTaskStore.execute(db,cmd("check",t)).getJSONObject("saved");
  JSONArray backup=GoalTaskStore.export(db);try(SQLiteDatabase restored=SQLiteDatabase.create(null)){GoalTaskStore.restore(restored,backup);check(get(restored,"reader-task").getString("status").equals("completed"),"Completion survives backup and fresh database restore");}
 }}
 void capture(String name)throws Exception{h.screenshot(name);try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("screencap -p /sdcard/Download/"+name);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){while(in.read()!=-1){}}}
 void reader()throws Exception{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  File f=new File(getTargetContext().getCacheDir(),"reader-user-article.zip");try(InputStream in=getContext().getAssets().open("reader-user-article.zip");OutputStream out=new FileOutputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))>0)out.write(b,0,n);}
  PublicationLibrary library=new PublicationLibrary(getTargetContext());JSONObject pkg=library.importZip(Uri.fromFile(f));Method m=MainActivity.class.getDeclaredMethod("adoptPublication",JSONObject.class);m.setAccessible(true);JSONObject r=(JSONObject)m.invoke(h.activity,pkg);String note=r.getString("note");
  h.js("window.__readerRecord="+r+";RecordEditor.open(__readerRecord,null,'creation',true)");until("!!document.querySelector('#doc-flow img') && [...document.querySelectorAll('#doc-flow img')].every(i=>i.complete&&i.naturalWidth>0)");Thread.sleep(400);
  check(h.js("!(/\\[[RC]\\d+\\]/.test(document.querySelector('#doc-flow').innerText)) && !document.querySelector('#doc-flow h3')").equals("true"),"Legacy ZIP displays no citations or internal chapter headings");
  check(h.js("document.querySelectorAll('#doc-flow h1').length===1").equals("true"),"Article main title displays once");
  click("#doc-mode");check(h.js("DocumentRich.serialize(document.querySelector('#doc-body'))===__readerRecord.note").equals("true"),"Editor hides source metadata without altering original Markdown");
  check(h.js("!(/\\[[RC]\\d+\\]/.test(document.querySelector('#doc-body').innerText)) && [...document.querySelectorAll('#doc-body h3')].every(n=>!n.getClientRects().length)").equals("true"),"Editing also hides citations and internal headings");
  h.js("document.querySelector('#doc-title').value='测试标题';document.querySelector('#doc-title').dispatchEvent(new Event('input'));document.querySelector('#doc-title').value=__readerRecord.title;document.querySelector('#doc-title').dispatchEvent(new Event('input'))");
  click("#doc-mode");until("document.querySelector('#doc-page').textContent.split(' / ')[1]>6");for(int i=0;i<40&&!h.js("document.querySelector('#doc-prev').disabled").equals("true");i++)click("#doc-prev");for(int i=0;i<6;i++)click("#doc-next");Thread.sleep(250);String page=h.js("document.querySelector('#doc-page').textContent");check(page.startsWith("\"7 /"),"Reader advances to page seven: "+page);capture("zhishi-reader-page7.png");close();
  h.js("RecordEditor.open(__readerRecord,null,'creation',true)");until("document.querySelector('#doc-page').textContent.startsWith('7 /')");check(h.js("document.querySelector('#doc-page').textContent").equals(page),"Closing and reopening restores exact last page");close();
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{};RecordEditor.open("+r+",null,'creation',true)");until("document.querySelector('#doc-page').textContent.startsWith('7 /')");check(true,"Reading position survives activity restart");
  check(record(r.getString("id")).getString("note").equals(note),"Reading and bookmark persistence never modify article body");capture("zhishi-reader-restored.png");close();
  try{JSONObject oldTask=null;JSONArray allTasks=((JSONObject)h.call("goalTasks",new JSONObject())).getJSONArray("items");for(int i=0;i<allTasks.length();i++)if(allTasks.getJSONObject(i).getString("id").equals("reader-ui-task"))oldTask=allTasks.getJSONObject(i);if(oldTask!=null)h.call("goalTasks",cmd("delete",oldTask));}catch(Exception ignored){}
  JSONObject task=(JSONObject)h.call("goalTasks",new JSONObject().put("command","save").put("item",item("reader-ui-task","task")).put("expectedVersion",JSONObject.NULL));JSONObject tr=task.getJSONObject("saved");
  h.call("goalTasks",cmd("progress",tr).put("entryId","reader-ui-entry").put("amount",1).put("minutes",0));h.js("GoalTasksUI.open('reader-ui-task')");until("!!document.querySelector('[data-goal-detail]')");
  check(h.js("document.querySelector('[data-goal-check]').getAttribute('aria-checked')==='true'").equals("true"),"Target completion checkbox is checked in actual task UI");
  check(h.js("!document.querySelector('[data-goal-command=start]') && !document.querySelector('[data-goal-live]') && document.querySelector('#goal-root').innerText.includes('执行时段') && document.querySelector('#goal-root').innerText.includes('计划用时')").equals("true"),"Task timer UI removed while time settings remain");capture("zhishi-task-completed.png");library.close();
 }
 @Override public void onStart(){Bundle out=new Bundle();try{if(!disposable)throw new Exception("Disposable emulator required");ledger();reader();out.putString("stream","PASS ReaderTaskRegressionTest: "+passed+" checks\n");finish(Activity.RESULT_OK,out);}catch(Throwable ex){out.putString("stream","FAIL ReaderTaskRegressionTest after "+passed+": "+android.util.Log.getStackTraceString(ex));finish(Activity.RESULT_CANCELED,out);}}
}
