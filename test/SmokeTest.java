package com.insight.quantlife.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import com.insight.quantlife.MainActivity;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SmokeTest extends Instrumentation {
    private MainActivity activity;
    private WebView web;
    private Method execute;
    private int passed;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private void check(boolean condition,String label)throws Exception { if(!condition)throw new Exception(label);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b); }
    private JSONObject object(String s)throws Exception{return new JSONObject(s);}
    private Object call(String name,JSONObject args)throws Exception{return execute.invoke(activity,name,args);}
    private String js(String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();runOnMainSync(()->web.evaluateJavascript(script,result->{value.set(result);latch.countDown();}));if(!latch.await(10,TimeUnit.SECONDS))throw new Exception("JS callback timeout");return value.get();}
    private boolean waitText(String text)throws Exception{for(int i=0;i<60;i++){String body=js("document.body.innerText");if(body!=null&&body.contains(text))return true;Thread.sleep(150);}return false;}
    private void attach(String name)throws Exception{File file=new File(getTargetContext().getFilesDir(),name);try(java.io.InputStream in=getContext().getAssets().open(name);FileOutputStream out=new FileOutputStream(file)){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}Method read=MainActivity.class.getDeclaredMethod("readAttachment",Uri.class,String.class);read.setAccessible(true);read.invoke(activity,Uri.fromFile(file),"attachment");}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity=(MainActivity)startActivitySync(intent);
        Field f=MainActivity.class.getDeclaredField("web");f.setAccessible(true);web=(WebView)f.get(activity);execute=MainActivity.class.getDeclaredMethod("execute",String.class,JSONObject.class);execute.setAccessible(true);
        check(waitText("今天的投入"),"Android WebView renders home");
        check(js("typeof window.LifeNative.call").contains("function"),"Native bridge available");
        JSONObject record=object("{\"id\":\"test-learning\",\"type\":\"learning\",\"date\":\"2026-10-05\",\"title\":\"量化学习实践\",\"platform\":\"B站\",\"contentType\":\"video\",\"durationMinutes\":45,\"tags\":[\"AI\"],\"note\":\"把知识转化成行动\",\"source\":\"manual\"}");
        record.put("date",new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(new java.util.Date()));
        call("saveRecord",new JSONObject().put("record",record));JSONObject initial=(JSONObject)call("init",new JSONObject());check(initial.getJSONArray("records").length()>0,"SQLite record persisted");
        boolean duplicate=false;try{call("saveRecord",new JSONObject().put("record",new JSONObject(record.toString()).put("id","duplicate")));}catch(Exception e){duplicate=true;}check(duplicate,"Native duplicate rejection");
        JSONObject profile=object("{\"id\":\"custom\",\"label\":\"模拟接口\",\"baseUrl\":\"http://10.0.2.2:8790/v1\",\"model\":\"mock-vision\",\"protocol\":\"chat\",\"effort\":\"off\",\"apiKey\":\"local-test-only-not-a-real-key\",\"vision\":true,\"allowHttp\":true}");
        call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",profile).put("dailyLearningGoal",60)));
        JSONObject settings=(JSONObject)call("getSettings",new JSONObject());check(settings.getJSONObject("profiles").getJSONObject("custom").getBoolean("keyConfigured"),"Keystore credential saved");
        check(!settings.toString().contains("local-test-only"),"UI settings do not expose key");
        String prefs=new String(java.nio.file.Files.readAllBytes(new File(getTargetContext().getApplicationInfo().dataDir,"shared_prefs/secure.xml").toPath()),java.nio.charset.StandardCharsets.UTF_8);check(!prefs.contains("local-test-only"),"Credential encrypted at rest");
        js("window.__smokeReloadMarker='old-document'");runOnMainSync(()->web.reload());boolean reloaded=false;for(int i=0;i<80;i++){if(js("typeof window.__smokeReloadMarker").equals("\"undefined\"")){reloaded=true;break;}Thread.sleep(100);}check(reloaded&&waitText("量化学习实践"),"Reload with SQLite data");
        js("document.querySelector('[data-page=chat]').click()");check(waitText("说一说"),"Chat page opens");
        js("document.querySelector('#chat-input').value='今天在知乎学习时间管理 30 分钟'; document.querySelector('[data-action=send]').click()");
        check(waitText("待确认的记录"),"Chat sends native HTTP and receives tool result");
        JSONObject before=(JSONObject)call("init",new JSONObject());int count=before.getJSONArray("records").length();check(count==1,"AI draft is not silently inserted");
        js("document.querySelector('[data-save-candidate]').click()");check(waitText("已保存 1 条记录"),"Tool draft confirmed and saved");
        JSONObject after=(JSONObject)call("init",new JSONObject());check(after.getJSONArray("records").length()==2,"Confirmed AI record reaches SQLite");
        js("document.querySelector('#chat-input').value='这周我学习了多久？'; document.querySelector('[data-action=send]').click()");check(waitText("总学习时长"),"RAG question answered");
        check(js("document.querySelectorAll('.citation').length").equals("1"),"RAG has clickable source citation");
        attach("note.txt");check(waitText("note.txt"),"Text file attached via native file reader");js("document.querySelector('[data-action=remove-attachment]').click()");
        attach("note.docx");check(waitText("note.docx"),"Word text extracted on Android");js("document.querySelector('[data-action=remove-attachment]').click()");
        js("window.qaLogs=[];['warn','error','log'].forEach(k=>{const old=console[k];console[k]=function(...args){window.qaLogs.push(args.map(String).join(' '));old.apply(console,args);};})");
        attach("note.pdf");check(waitText("note.pdf"),"PDF text extraction and worker on Android");js("document.querySelector('[data-action=remove-attachment]').click()");
        File imageFile=new File(getTargetContext().getFilesDir(),"note-image.png");Bitmap image=Bitmap.createBitmap(240,120,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.WHITE);Paint paint=new Paint();paint.setColor(Color.BLACK);paint.setTextSize(20);canvas.drawText("Learning: 20 minutes",10,60,paint);try(FileOutputStream out=new FileOutputStream(imageFile)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();Method read=MainActivity.class.getDeclaredMethod("readAttachment",Uri.class,String.class);read.setAccessible(true);read.invoke(activity,Uri.fromFile(imageFile),"attachment");check(waitText("note-image.png"),"Image compression and attachment on Android");
        js("document.querySelector('#chat-input').value='提取图片中的学习内容';document.querySelector('[data-action=send]').click()");check(waitText("待确认的记录"),"Image passes through native multimodal tool request");
        profile.put("protocol","responses");call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",profile)));
        JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content","test")).put(new JSONObject().put("role","user").put("content","response-protocol-test"));JSONObject tool=object("{\"name\":\"prepare_life_records\",\"description\":\"test\",\"parameters\":{\"type\":\"object\",\"properties\":{}}}");
        JSONObject response=(JSONObject)call("ask",new JSONObject().put("messages",messages).put("tool",tool));check(response.getString("text").contains("Responses"),"Responses API tool compatibility");
        js("document.querySelector('[data-page=learning]').click()");check(waitText("学习档案"),"Learning list renders persisted records");
        js("document.querySelector('#learning-search').value='时间管理';document.querySelector('#learning-search').dispatchEvent(new Event('input'))");check(js("document.querySelectorAll('.record').length").equals("1"),"Search filters stored record");
        JSONObject work=object("{\"id\":\"test-work\",\"type\":\"work\",\"date\":\"2026-10-01\",\"title\":\"工作测试\",\"workStart\":\"2026-10-01T09:00:00\",\"workEnd\":\"2026-10-01T18:00:00\",\"durationMinutes\":480,\"breaks\":[{\"start\":\"2026-10-01T12:00:00\",\"end\":\"2026-10-01T13:00:00\"}]}");
        JSONObject saved=(JSONObject)call("saveRecord",new JSONObject().put("record",work));check(saved.getJSONArray("tags").length()==0&&saved.getString("note").isEmpty(),"Import defaults normalized by native store");
        boolean overlap=false;try{call("saveRecord",new JSONObject().put("record",new JSONObject(work.toString()).put("id","work-overlap").put("workStart","2026-10-01T10:00:00")));}catch(Exception e){overlap=true;}check(overlap,"Native store rejects overlapping shifts");
        boolean malformed=false;try{call("saveRecord",new JSONObject().put("record",new JSONObject(work.toString()).put("id","malformed").put("date","2026-02-30")));}catch(Exception e){malformed=true;}check(malformed,"Native store rejects invalid calendar date");
        JSONObject added=new JSONObject(record.toString()).put("id","rollback-record").put("title","不应保存的测试条目");JSONArray badImport=new JSONArray().put(added).put(new JSONObject(work.toString()).put("id","bad-import-work").put("workStart","2026-10-01T10:00:00"));boolean rollback=false;try{call("import",new JSONObject().put("records",badImport));}catch(Exception e){rollback=true;}JSONObject rolled=(JSONObject)call("init",new JSONObject());check(rollback&&!rolled.toString().contains("rollback-record"),"Invalid import rolls back all new entries");
        JSONObject imported=(JSONObject)call("import",new JSONObject().put("records",new JSONArray().put(record)));check(imported.getInt("skipped")==1&&imported.getInt("inserted")==0,"Backup restore skips existing records");
        call("deleteRecord",new JSONObject().put("id","test-work"));
        js("document.querySelector('[data-page=today]').click()");check(waitText("今天的投入"),"Return to home for screenshot");
        waitForIdleSync();Thread.sleep(5000);
        File dir=new File(getTargetContext().getFilesDir(),"qa");dir.mkdirs();Bitmap screenshot=getUiAutomation().takeScreenshot();if(screenshot!=null){try(FileOutputStream out=new FileOutputStream(new File(dir,"android-home.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}
        result.putString("stream","\nPASS: "+passed+" Android integration checks.\n");result.putInt("passed",passed);finish(Activity.RESULT_OK,result);
    }catch(Throwable e){String detail="";try{detail=js("document.body.innerText+'\\nTOAST:'+document.querySelector('#toast').textContent");}catch(Exception ignored){}result.putString("stream","\nFAIL after "+passed+" checks: "+e+"\nPage: "+detail+"\n");result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
