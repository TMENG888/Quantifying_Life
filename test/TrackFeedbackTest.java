package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.location.*;import android.net.Uri;
import com.insight.quantlife.*;import org.json.*;import java.io.*;import java.util.*;import java.security.*;import java.util.zip.*;import java.lang.reflect.*;
/** Disposable emulator, synthetic coordinates only. Real SQLite, ZIP and Activity export callbacks. */
public final class TrackFeedbackTest extends Instrumentation {
 MainHost h;int passed;String phase;final String seedDate="2026-11-20",bigDate="2026-11-21";
 @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"verify":b.getString("phase","verify");start();}
 void check(boolean ok,String text)throws Exception{if(!ok)throw new Exception(text);passed++;Bundle s=new Bundle();s.putString("stream","PASS "+text+"\n");sendStatus(0,s);}
 void until(String s)throws Exception{for(int i=0;i<600;i++){if(h.js(s).equals("true"))return;Thread.sleep(100);}throw new Exception("UI timeout: "+s);}
 JSONObject init()throws Exception{JSONObject o=(JSONObject)h.call("init",new JSONObject());if(o.has("settings")&&o.getJSONObject("settings").has("wikiAutomation"))o.getJSONObject("settings").getJSONObject("wikiAutomation").remove("dailyUsed");return o;}
 long count(TrackStore db)throws Exception{try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM points",null)){c.moveToFirst();return c.getLong(0);}}
 Location p(long time,int i){Location l=new Location(i%3==0?"fused":"gps");l.setTime(time);l.setLatitude(.02+(i%20)*.000001);l.setLongitude(.04);l.setAccuracy(i%7==0?300:20);if(i%2==0){l.setSpeed(.7f);l.setSpeedAccuracyMetersPerSecond(.2f);}return l;}
 String raw(TrackStore db)throws Exception{return db.diagnostics(seedDate).getJSONArray("rawPoints").toString();}
 String read(InputStream in)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);return b.toString("UTF-8");}
 boolean sameJson(Object a,Object b)throws Exception{
  if(a instanceof JSONObject&&b instanceof JSONObject){JSONObject x=(JSONObject)a,y=(JSONObject)b;if(x.length()!=y.length())return false;Iterator<String> keys=x.keys();while(keys.hasNext()){String k=keys.next();if(!y.has(k)||!sameJson(x.get(k),y.get(k)))return false;}return true;}
  if(a instanceof JSONArray&&b instanceof JSONArray){JSONArray x=(JSONArray)a,y=(JSONArray)b;if(x.length()!=y.length())return false;for(int i=0;i<x.length();i++)if(!sameJson(x.get(i),y.get(i)))return false;return true;}
  return String.valueOf(a).equals(String.valueOf(b));
 }
 boolean sameRecords(JSONArray a,JSONArray b)throws Exception{if(a.length()!=b.length())return false;Map<String,JSONObject> rows=new HashMap<>();for(int i=0;i<b.length();i++)rows.put(b.getJSONObject(i).getString("id"),b.getJSONObject(i));for(int i=0;i<a.length();i++){JSONObject old=new JSONObject(a.getJSONObject(i).toString()),source=rows.get(old.getString("id"));if(source==null)return false;JSONObject now=new JSONObject(source.toString());/* Existing automatic Wiki topic annotation is derived metadata, not source editing. */for(String key:new String[]{"wikiTopics","wikiTopicVersion"}){old.remove(key);now.remove(key);}if(!sameJson(old,now))return false;}return true;}
 JSONObject snapshot()throws Exception{try(InputStream in=getTargetContext().openFileInput("feedback-upgrade.json")){return new JSONObject(read(in));}}
 void callback(int result,Uri uri)throws Exception{Method m=h.activity.getClass().getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);m.setAccessible(true);final Throwable[] error={null};runOnMainSync(()->{try{m.invoke(h.activity,13,result,uri==null?null:new Intent().setData(uri));}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
 JSONObject status()throws Exception{return (JSONObject)h.call("trackFeedbackStatus",new JSONObject());}
 void emptyArchive()throws Exception{
  try(android.database.sqlite.SQLiteDatabase memory=android.database.sqlite.SQLiteDatabase.create(null);
      TrackStore stub=new TrackStore(getTargetContext()){@Override public android.database.sqlite.SQLiteDatabase getReadableDatabase(){return memory;}}){
   stub.onCreate(memory);ByteArrayOutputStream bytes=new ByteArrayOutputStream();JSONObject manifest=TrackFeedback.write(getTargetContext(),stub,bytes,null);
   check(manifest.getLong("totalRawPoints")==0&&manifest.getInt("dateCount")==0&&manifest.isNull("firstTime")&&manifest.getBoolean("complete"),"Empty history produces an explicit complete zero-point report");
   boolean empty=false;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){ZipEntry e;while((e=zip.getNextEntry())!=null){if(e.getName().equals("raw-points.jsonl"))empty=zip.read()==-1;zip.closeEntry();}}
   check(empty,"Empty history still has a valid empty raw stream");
  }
 }
 JSONObject inspect(File file,long expected,long extraId)throws Exception{
  long lines=0,big=0;boolean coarse=false,excluded=false,nullSpeed=false,motion=false,notExtra=true,parseError=false;JSONObject header=null,manifest=null;MessageDigest digest=MessageDigest.getInstance("SHA-256");Set<String> entries=new HashSet<>();
  try(ZipInputStream zip=new ZipInputStream(new FileInputStream(file))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){entries.add(entry.getName());if(entry.getName().equals("raw-points.jsonl")){
    BufferedReader r=new BufferedReader(new InputStreamReader(zip,"UTF-8"));String line;while((line=r.readLine())!=null){digest.update((line+"\n").getBytes("UTF-8"));JSONObject p=new JSONObject(line);lines++;if(p.getString("date").equals(bigDate))big++;if(p.getLong("id")==extraId)notExtra=false;coarse|=p.getDouble("accuracy")>65;nullSpeed|=p.isNull("speed");JSONObject t=p.optJSONObject("telemetry");if(t!=null){excluded|=!t.optBoolean("eligible",true);motion|=t.has("motionRms")&&t.has("motionWakeUp")&&t.has("customFutureField");}parseError|=p.optBoolean("telemetryParseError");if(line.contains("feedback-private-note-content"))throw new Exception("Life record leaked into raw archive");}
   }else{String text=read(zip);if(entry.getName().equals("header.json"))header=new JSONObject(text);if(entry.getName().equals("manifest.json"))manifest=new JSONObject(text);check(!text.contains("profiles")&&!text.contains("apiKey")&&!text.contains("feedback-private-note-content"),"Metadata has no model configuration or personal notes: "+entry.getName());}zip.closeEntry();}}
  check(entries.equals(new HashSet<>(Arrays.asList("header.json","raw-points.jsonl","manifest.json"))),"Single feedback ZIP has only documented entries");
  check(header!=null&&manifest!=null&&manifest.getBoolean("complete")&&!manifest.getBoolean("rawTruncated"),"Archive completion and non-truncation are explicit");
  check(lines==expected&&manifest.getLong("totalRawPoints")==expected&&header.getLong("snapshotRawPoints")==expected,"Every snapshot raw point exported exactly once");
  check(big==120005,"More than 120000 points from one day export without truncation");
  StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b&255));check(hex.toString().equals(manifest.getString("rawSha256")),"Raw stream SHA256 agrees with manifest");
  long dayTotal=0;JSONArray dates=manifest.getJSONArray("dates");String prev="";for(int i=0;i<dates.length();i++){JSONObject d=dates.getJSONObject(i);check(d.getString("date").compareTo(prev)>0,"Date summary ordered: "+d.getString("date"));prev=d.getString("date");dayTotal+=d.getLong("rawPoints");}check(dayTotal==expected&&dates.length()==manifest.getInt("dateCount"),"All per-day counts sum to complete history");
  check(coarse&&excluded&&nullSpeed&&motion,"Coarse/excluded fixes, missing speeds and complete future telemetry retained");check(notExtra,"Fix captured after snapshot ID ceiling is deferred to next export");check(parseError,"Malformed legacy telemetry is preserved rather than dropping its point");return manifest;
 }
 @Override public void onStart(){Bundle result=new Bundle();try(TrackStore db=new TrackStore(getTargetContext())){
  h=new MainHost(this);h.open();h.js("AutoWiki.pause()");getTargetContext().stopService(new Intent(getTargetContext(),TrackingService.class));
  if(phase.equals("seed")){
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.1"),"Seed actual signed 1.9.1 before cover installation");
   h.call("saveRecord",new JSONObject().put("allowDuplicate",true).put("record",new JSONObject().put("id","feedback-private-note").put("type","learning").put("date",seedDate).put("title","feedback-private-note-title").put("note","feedback-private-note-content").put("durationMinutes",18)));
   db.deleteDay(seedDate);for(int i=0;i<8;i++)db.add(p(TrackStore.dayStart(seedDate)+i*2000,i),"feedback-upgrade",new JSONObject().put("eligible",false).put("motionAgeMs",-1));
   JSONObject s=new JSONObject().put("init",init()).put("raw",raw(db)).put("gpx",db.gpx(seedDate,true));try(OutputStream out=getTargetContext().openFileOutput("feedback-upgrade.json",0)){out.write(s.toString().getBytes("UTF-8"));}check(true,"Prior records, settings, Wiki, chat, raw and GPX saved for upgrade check");
  }else{
   check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.9.2"),"Signed 1.9.2 installed over prior version");JSONObject before=snapshot(),now=init();for(String key:new String[]{"records","settings","messages","wikiPages"})if(before.getJSONObject("init").has(key))check(key.equals("records")?sameRecords(before.getJSONObject("init").getJSONArray(key),now.getJSONArray(key)):sameJson(before.getJSONObject("init").get(key),now.get(key)),"Cover install preserves "+key);
   check(before.getString("raw").equals(raw(db))&&before.getString("gpx").equals(db.gpx(seedDate,true)),"Cover install preserves source GPS and original GPX");
   db.getWritableDatabase().delete("points","session IN (?,?)",new String[]{"feedback-big","feedback-after-snapshot"});long start=TrackStore.dayStart(bigDate);
   db.getWritableDatabase().beginTransaction();try{JSONObject t=new JSONObject().put("eligible",false).put("motionRms",.02).put("motionAgeMs",200).put("motionWakeUp",true).put("customFutureField","preserve-unknown-diagnostic");for(int i=0;i<120005;i++)db.add(p(start+i*500,i),"feedback-big",t);db.add(p(start+86400000,1),"feedback-big",t);db.add(p(start-8L*86400000,2),"feedback-big",t);db.getWritableDatabase().setTransactionSuccessful();}finally{db.getWritableDatabase().endTransaction();}
   db.getWritableDatabase().execSQL("UPDATE points SET telemetry=? WHERE id=(SELECT MAX(id) FROM points)",new Object[]{"legacy-invalid-json"});
   final long expected=count(db);final boolean[] injected={false},deleteBlocked={false};final long[] extraId={-1};File archive=new File(getTargetContext().getCacheDir(),"feedback-stream-test.zip");
   JSONObject manifest;try(OutputStream out=new FileOutputStream(archive)){manifest=TrackFeedback.write(getTargetContext(),db,out,(done,total)->{if(done>=1000&&!injected[0]){injected[0]=true;try(TrackStore other=new TrackStore(getTargetContext())){other.add(p(start+2L*86400000,3),"feedback-after-snapshot");try(android.database.Cursor c=other.getReadableDatabase().rawQuery("SELECT MAX(id) FROM points",null)){c.moveToFirst();extraId[0]=c.getLong(0);}try{other.deleteDay(bigDate);}catch(Exception busy){deleteBlocked[0]=busy.getMessage().contains("正在导出");}}}});}
   check(injected[0]&&count(db)==expected+1,"Recording can append while a full-history export is running");check(deleteBlocked[0],"Concurrent delete is refused while full-history feedback is being generated");inspect(archive,expected,extraId[0]);
   check(before.getString("raw").equals(raw(db))&&before.getString("gpx").equals(db.gpx(seedDate,true)),"Export never modifies prior raw observations or GPX");
   emptyArchive();
   boolean failed=false;try{TrackFeedback.write(getTargetContext(),db,new OutputStream(){public void write(int b)throws IOException{throw new IOException("Synthetic storage full");}},null);}catch(IOException e){failed=true;}check(failed,"Output storage error propagates rather than reporting a successful export");
   db.deleteDay("2020-01-01");check(true,"History lock is released even after a write failure");
   Thread.currentThread().interrupt();boolean interrupted=false;try{TrackFeedback.write(getTargetContext(),db,new ByteArrayOutputStream(),null);}catch(InterruptedException|InterruptedIOException e){interrupted=true;}finally{Thread.interrupted();}check(interrupted,"Cancelled/interrupted export is not reported complete");
   h.js("document.querySelector('[data-page=tracks]').click()");until("!!document.querySelector('#track-data-tools')");h.js("document.querySelector('#track-data-tools').click()");until("!!document.querySelector('#track-export-all')");
   check(h.js("document.querySelector('#track-export-all').classList.contains('primary')&&document.querySelector('#track-export-all').getBoundingClientRect().width>250").equals("true"),"Full-history feedback is a readable full-width primary action");
   check(h.js("document.querySelector('.track-day-tools').getBoundingClientRect().width<=document.querySelector('#editor').clientWidth&&document.querySelector('#editor').scrollWidth<=innerWidth").equals("true"),"Day actions use two-column phone layout without horizontal overflow");
   h.js("document.querySelector('#track-export-all').click()");until("document.querySelector('#confirm-dialog').open");check(h.js("document.querySelector('#confirm-dialog').innerText.includes('完整行踪')&&document.querySelector('#confirm-dialog').innerText.includes('不自动上传')").equals("true"),"Full-history privacy warning is shown before opening file picker");h.js("document.querySelector('#confirm-cancel').click()");Thread.sleep(200);
   h.call("trackExportAll",new JSONObject());Thread.sleep(700);check(status().optString("state").equals("choosing"),"Actual ACTION_CREATE_DOCUMENT opens in choosing state");boolean repeat=false;try{h.call("trackExportAll",new JSONObject());}catch(Exception e){repeat=true;}check(repeat,"Rapid repeated export cannot open overlapping document requests");
   getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);for(int i=0;i<50&&!status().optString("state").equals("cancelled");i++)Thread.sleep(100);check(status().optString("state").equals("cancelled"),"Cancelling actual system document picker restores the export action");
   File destination=new File(getTargetContext().getCacheDir(),"feedback-activity-result.zip");callback(Activity.RESULT_OK,Uri.fromFile(destination));for(int i=0;i<1200&&!Arrays.asList("complete","failed").contains(status().optString("state"));i++)Thread.sleep(100);
   check(status().optString("state").equals("complete")&&status().getLong("done")==expected+1&&destination.length()>0,"Actual Activity result writes complete feedback through ContentResolver");
   until("!document.querySelector('#track-export-all').disabled&&document.querySelector('#track-feedback-status').textContent.includes('已保存全部定位')");check(true,"Export progress/completion reaches WebView and button becomes reusable");
   check(sameRecords(now.getJSONArray("records"),init().getJSONArray("records")),"Full-history export never changes original note fields");
   h.js("document.querySelector('#editor').scrollTop=0");Thread.sleep(500);h.tap("#track-feedback-status",false);runOnMainSync(()->h.web.invalidate());Thread.sleep(1200);h.screenshot("tracks-192-feedback.png");
  }
  result.putString("stream","PASS "+passed+" full-history feedback checks ("+phase+")\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){e.printStackTrace();result.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
