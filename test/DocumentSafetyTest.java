package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;import java.io.*;import com.insight.quantlife.*;
/** Additional safety/recovery checks on the same synthetic AVD. */
public final class DocumentSafetyTest extends DocumentEditorTest {
 @Override public void onStart(){Bundle result=new Bundle();try{
  h=new MainHost(this);h.open();model();open("learning");
  check(h.js("(()=>{const r=document.querySelector('#editor').getBoundingClientRect();return Math.abs(r.top)<2&&r.bottom<=innerHeight+2&&r.height>innerHeight*.9})()").equals("true"),"Document dialog fills phone viewport without old static-position offset");
  check(h.js("document.querySelector('#doc-tools').getBoundingClientRect().bottom<=innerHeight+1").equals("true"),"Editing tools remain visible at phone bottom");
  input("手动标题必须保留","恢复测试正文：有独立草稿，不进入数据库。");click("#doc-complete");until("!document.querySelector('#doc-complete').disabled");check(h.js("document.querySelector('#doc-title').value").equals(JSONObject.quote("手动标题必须保留")),"Metadata completion does not replace an existing manual title");
  close();JSONArray drafts=(JSONArray)h.call("getDocumentDraft",new JSONObject());JSONObject draft=null;for(int i=0;i<drafts.length();i++)if(drafts.getJSONObject(i).getJSONObject("record").optString("note").contains("恢复测试正文"))draft=drafts.getJSONObject(i);if(draft==null)throw new Exception("Draft missing");
  open("experience");input("另一份草稿","另一份正文不会覆盖学习草稿。");click("#doc-drafts");until("!!document.querySelector('#doc-drafts-panel')");
  h.js("Array.from(document.querySelectorAll('[data-draft]')).find(b=>b.innerText.includes('手动标题必须保留')).click()");check(h.js("document.querySelector('#doc-body').textContent").contains("恢复测试正文"),"Draft picker restores exact abandoned body across content types");
  check(((JSONArray)h.call("getDocumentDraft",new JSONObject())).length()>=2,"Opening another category preserves multiple independent drafts");
  h.js("__delay=900");click("#doc-format");close();open("work_note");input("新文档","新正文：迟到的AI不能覆盖我。");Thread.sleep(1600);check(h.js("!document.querySelector('#doc-preview')&&document.querySelector('#doc-body').textContent.includes('迟到的AI不能覆盖我')").equals("true"),"Late AI response cannot mutate a newly opened document");close();
  JSONObject img=(JSONObject)h.call("storeRecordImage",new JSONObject().put("data",image()).put("name","安全图片.png"));open("learning");input("只含图片","");h.js("window.nativeEvent('recordImage',"+img+")");until("!!document.querySelector('#doc-body img')");int count=records().length();click("#doc-save");check(records().length()==count,"Image-only document is rejected before database write");close();
  JSONArray media=RecordMedia.exportImages(getTargetContext(),records());check(media.length()>0,"Image-bearing record included in backup");File imageFile=RecordMedia.file(getTargetContext(),media.getJSONObject(0).getString("id"));byte[] previous=java.nio.file.Files.readAllBytes(imageFile.toPath());if(!imageFile.delete())throw new Exception("Cannot remove isolated synthetic image for restore test");RecordMedia.restoreImages(getTargetContext(),media);check(java.util.Arrays.equals(previous,java.nio.file.Files.readAllBytes(imageFile.toPath())),"Backup restores a physically missing JPEG byte-for-byte");
  try{RecordMedia.fromData(getTargetContext(),"data:text/html;base64,PGgxPmJhZDwvaDE+","bad");throw new Exception("Invalid data accepted");}catch(Exception expected){check(!expected.getMessage().contains("accepted"),"Non-image data cannot enter record-media storage");}
  check(!h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("false"),"Normal app remains within phone viewport after document closure");
  result.putString("stream","PASS DocumentSafetyTest: "+passed+" checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable err){result.putString("stream","FAIL DocumentSafetyTest after "+passed+": "+err+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
