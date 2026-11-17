package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.net.Uri;
import com.insight.quantlife.*;import org.json.*;import java.io.*;import java.lang.reflect.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;

public final class PublicationLibraryTest extends DocumentEditorTest {
 boolean disposable;
 @Override public void onCreate(Bundle args){disposable=args!=null&&"127.0.0.1:16384".equals(args.getString("disposableEmulator"));super.onCreate(args);}
 File fixture(String name)throws Exception{File f=new File(getTargetContext().getCacheDir(),name);try(InputStream in=getContext().getAssets().open(name);OutputStream out=new FileOutputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return f;}
 JSONObject adopt(JSONObject data)throws Exception{Method method=MainActivity.class.getDeclaredMethod("adoptPublication",JSONObject.class);method.setAccessible(true);return (JSONObject)method.invoke(h.activity,data);}
 void capture(String name)throws Exception{h.screenshot(name);try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("screencap -p /sdcard/Download/"+name);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){while(in.read()!=-1){}}}
 void seed()throws Exception{h=new MainHost(this);h.open();Files.write(new File(getTargetContext().getFilesDir(),"publication-record-before.json").toPath(),records().toString().getBytes(StandardCharsets.UTF_8));check(true,"Saved original records before signed upgrade");}
 void verify()throws Exception{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  JSONArray old=new JSONArray(new String(Files.readAllBytes(new File(getTargetContext().getFilesDir(),"publication-record-before.json").toPath()),StandardCharsets.UTF_8));for(int i=0;i<old.length();i++){JSONObject before=old.getJSONObject(i);check(record(before.getString("id")).toString().equals(before.toString()),"Signed upgrade preserves original record "+before.getString("id"));}
  PublicationLibrary library=new PublicationLibrary(getTargetContext());JSONObject pkg=library.importZip(Uri.fromFile(fixture("actual-publication.zip"))),r=adopt(pkg);String id=r.getString("id");
  check(r.getString("type").equals("creation")&&r.getJSONArray("images").length()==9,"Actual ZIP is a regular creation record with nine inline illustrations");
  String note=r.getString("note");check(note.contains("DeepSeek")&&note.split("zhishi-media:",-1).length-1==9,"Full original article and all image anchors are retained");
  check(adopt(pkg).getString("id").equals(id),"Repeated article import reuses regular record");
  h.js("window.nativeEvent('publicationImport',"+new JSONObject().put("state","complete").put("record",r)+")");until("!!document.querySelector('#doc-read') && !document.querySelector('#doc-read').hidden");
  check(h.js("document.querySelector('#doc-mode').textContent==='编辑' && document.querySelector('#doc-flow').textContent.includes('DeepSeek')").equals("true"),"Import opens the existing paginated reader");
  until("[...document.querySelectorAll('#doc-flow img')].length===9 && [...document.querySelectorAll('#doc-flow img')].every(i=>i.complete&&i.naturalWidth>0)");check(true,"All nine article images load in original reader");
  check(h.js("!document.querySelector('#publication-copy-body')&&!document.querySelector('[data-publication-save]')&&!document.querySelector('.publication-specific')").equals("true"),"Dedicated publishing copy, gallery and platform panels are absent");
  capture("zhishi-article-reader.png");click("#doc-mode");until("!document.querySelector('#doc-edit-scroll').hidden");
  h.js("document.querySelector('#doc-title').value='手机编辑后保留的原稿';document.querySelector('#doc-title').dispatchEvent(new Event('input'))");click("#doc-save");until("!document.querySelector('#doc-save').disabled && !document.querySelector('#doc-read').hidden");
  JSONObject edited=record(id);check(edited.getString("title").equals("手机编辑后保留的原稿")&&edited.getString("note").equals(note),"Existing editor saves metadata without changing article body or images");
  check(adopt(pkg).getString("title").equals("手机编辑后保留的原稿"),"Reimport preserves local edits instead of overwriting them");
  JSONArray backup=RecordMedia.exportImages(getTargetContext(),new JSONArray().put(edited));check(backup.length()==9,"Existing record backup includes all imported illustration bytes");
  close();click("[data-page=records]");click("[data-record-tab=creation]");check(h.js("!!document.querySelector('[data-record=\""+id+"\"]')&&!document.querySelector('[data-publication]')").equals("true"),"Imported article appears in the ordinary creation list");capture("zhishi-article-records.png");
  int count=library.list().length();File malformed=new File(getTargetContext().getCacheDir(),"bad.zip");Files.write(malformed.toPath(),new byte[]{1,2});boolean denied=false;try{library.importZip(Uri.fromFile(malformed));}catch(Exception expected){denied=true;}check(denied&&library.list().length()==count,"Invalid ZIP cannot alter saved article records");
  JSONObject rich=adopt(library.importZip(Uri.fromFile(fixture("rich-publication.zip"))));check(rich.getString("note").contains("## 原始小标题")&&rich.getString("note").contains("**正文强调**")&&rich.getString("note").contains("zhishi-media:"),"New ZIP preserves original Markdown formatting and inline image positions");
  runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();check(record(id).getString("title").equals("手机编辑后保留的原稿")&&record(id).getString("note").equals(note),"Imported and edited record survives restart and legacy migration");
  library.close();
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{if(!disposable)throw new Exception("Explicit disposable emulator required");if("seed".equals(phase))seed();else verify();ok=true;out.putString("stream","PASS PublicationLibraryTest: "+passed+" checks\n");}catch(Throwable error){out.putString("stream","FAIL PublicationLibraryTest after "+passed+": "+android.util.Log.getStackTraceString(error));}finally{try{if(h!=null)runOnMainSync(()->h.activity.finish());}catch(Exception ignored){}}finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
