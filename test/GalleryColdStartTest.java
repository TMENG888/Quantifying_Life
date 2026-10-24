package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;import java.io.*;import com.insight.quantlife.*;
/** Run after adb am force-stop: verifies an actual live-generated image survives a cold process. */
public final class GalleryColdStartTest extends DocumentEditorTest {
 @Override public void onStart(){Bundle out=new Bundle();try{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");JSONArray jobs=(JSONArray)h.call("getIllustrationJobs",new JSONObject());JSONObject ready=null;
  for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(j.optString("status").equals("READY")&&j.optString("recordTitle").startsWith("PageIndex 与 Wiki")){ready=j;break;}}
  check(ready!=null,"Cold process loads the durable real provider job and images");JSONObject img=ready.getJSONArray("images").getJSONObject(0);byte[] bytes=java.nio.file.Files.readAllBytes(RecordMedia.file(getTargetContext(),img.getString("id")).toPath());try(FileOutputStream stream=new FileOutputStream(new File(getTargetContext().getFilesDir(),"qa/live-app-result.jpg"))){stream.write(bytes);}check(bytes.length>10000,"Real generated JPEG remains in private local media storage");
  open("idea");input("冷启动后的图片库","图片库独立于原记录和旧光标，可在这里手动插图。");click("#doc-image");until("!!document.querySelector('[data-select]')");check(h.js("document.querySelector('[data-select]').getAttribute('aria-pressed')==='false'&&document.querySelector('#gallery-insert').disabled").equals("true"),"Cold-start gallery requires explicit selection before insertion");h.screenshot("gallery-cold-start.png");
  String before=h.js("DocumentRich.serialize(document.querySelector('#doc-body'))");click("[data-select]");click("#gallery-insert");until("document.querySelectorAll('#doc-body img').length===1");check(h.js("RecordDocument.plain(DocumentRich.serialize(document.querySelector('#doc-body')))").equals(JSONObject.quote("图片库独立于原记录和旧光标，可在这里手动插图。")),"Cold-start manual insertion preserves every original body character");close();out.putString("stream","PASS GalleryColdStartTest: "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream","FAIL GalleryColdStartTest after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
