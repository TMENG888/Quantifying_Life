package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;import java.io.*;import com.insight.quantlife.*;
/** Disposable synthetic AVD only: real WebView/SQLite with model and web reads mocked. */
public class LearningIntegrityTest extends ChatRedesignTest {
 final String url="https://weixin.qq.com/sph/SyntheticLearning";
 final String original="学习了这个视频内容，有关REA，为最近的一个逆向Web，app的开源项目，当中封装了一些逆向的工具，skill指导，以及过程的记录机制，目前使用它的核心目的是了解软件，然后理解软件的功能。";
 JSONObject snapshotData()throws Exception{JSONObject data=init();try(TrackStore t=new TrackStore(getTargetContext())){data.put("raw",t.diagnostics("2026-12-15").getJSONArray("rawPoints")).put("gpx",t.gpx("2026-12-15"));}return data;}
 void seedIntegrity()throws Exception{
  holdAutomation();check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.7.9"),"Baseline previous signed 1.7.9");
  try(FileOutputStream out=getTargetContext().openFileOutput("learning-integrity-upgrade.json",0)){out.write(snapshotData().toString().getBytes("UTF-8"));}
 }
 void upgradeIntegrity()throws Exception{
  JSONObject before;try(InputStream in=getTargetContext().openFileInput("learning-integrity-upgrade.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);before=new JSONObject(out.toString("UTF-8"));}
  JSONObject after=snapshotData();check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.7.10"),"Signed 1.7.10 cover installation");
  for(String k:new String[]{"records","messages","wikiPages","chatSession","settings","raw","gpx"})check(before.get(k).toString().equals(after.get(k).toString()),"Cover upgrade preserves "+k);
 }
 void learningMock()throws Exception{
  chatMock();
  h.js("window.__readCount=0;window.__actions=[];const callBeforeLearning=LifeNative.call;LifeNative.call=function(action,payload,rid){if(action==='readLinks'){__readCount++;setTimeout(()=>nativeReply({id:rid,data:[{url:"+JSONObject.quote(url)+",status:'unavailable',title:'',platform:'微信视频号',contentType:'video'}]}),15);return;}if(action==='applyRecordAction')__actions.push(JSON.parse(payload));callBeforeLearning(action,payload,rid)}");
 }
 @Override void sendText(String text)throws Exception{h.js("document.querySelector('#chat-input').value="+JSONObject.quote(text)+";document.querySelector('#chat-input').dispatchEvent(new Event('input'))");super.sendText(text);}
 void linkText(String text)throws Exception{int count=new JSONArray(h.js("[document.querySelectorAll('.chat-message').length]")).getInt(0);h.js("document.querySelector('#chat-input').value="+JSONObject.quote(text)+";document.querySelector('#chat-input').dispatchEvent(new Event('input'))");click("[data-action=send]");until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("document.querySelectorAll('.chat-message').length>="+(count+2)+"&&!document.querySelector('#chat-input').disabled");}
 JSONObject latestLearning()throws Exception{JSONArray rows=init().getJSONArray("records");for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optString("url").equals(url))return rows.getJSONObject(i);return null;}
 void verifyIntegrity()throws Exception{
  holdAutomation();if(phase.equals("verify"))upgradeIntegrity();
  JSONArray old=init().getJSONArray("records");for(int i=0;i<old.length();i++)if(old.getJSONObject(i).optString("url").equals(url)||old.getJSONObject(i).optString("url").equals("https://weixin.qq.com/sph/SyntheticFields")||old.getJSONObject(i).getString("id").equals("integrity-long"))h.call("deleteRecord",new JSONObject().put("id",old.getJSONObject(i).getString("id")));
  h.call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",model("SYNTHETIC-ONLY"))));h.call("newChat",new JSONObject());reload();holdAutomation();learningMock();click("[data-page=chat]");
  linkText(url+"\n"+original);
  jsCheck("document.querySelectorAll('[data-save-candidate]').length===1","Empty model result still yields valid note-derived learning candidate");
  check(latestLearning()==null,"Initial candidate has not written SQLite");
  jsCheck("document.querySelector('.candidate').innerText.includes('非原文标题')&&document.querySelector('.candidate').innerText.includes("+JSONObject.quote(original)+")","Candidate visibly labels generated title and retains full supplied note");
  sendText("而且刚刚那个视频我学习了15分钟");
  jsCheck("document.querySelectorAll('[data-save-candidate]').length===1&&document.querySelector('.candidate').innerText.includes('15分钟')","Unconfirmed candidate accepts duration without losing its note");
  check(latestLearning()==null,"Candidate duration followup remains uncommitted");
  click("[data-save-candidate]");until("!document.querySelector('[data-save-candidate]')||document.querySelector('#confirm-dialog').open");if(h.js("document.querySelector('#confirm-dialog').open").equals("true"))click("#confirm-ok");until("!document.querySelector('[data-save-candidate]')");
  JSONObject saved=latestLearning();check(saved!=null&&saved.getString("note").equals(original)&&saved.getInt("durationMinutes")==15,"Confirmed candidate saves full original and duration in SQLite");
  check(saved.getString("titleOrigin").equals("note")&&!saved.getString("title").contains("未获取"),"Saved title is generated from note, not placeholder");
  String rid=saved.getString("id"),title=saved.getString("title");
  h.js("window.__calls=[]");sendText("刚刚那个视频又学了15分钟");
  check(find(rid).getInt("durationMinutes")==15,"Duration update waits for user confirmation");
  jsCheck("document.querySelectorAll('[data-confirm-change]').length===1&&__calls.length===0","Exact followup resolves confirmed current-session record without model");
  click("[data-confirm-change]");until("!document.querySelector('[data-confirm-change]')");
  JSONObject updated=find(rid);check(updated.getInt("durationMinutes")==30&&updated.getString("note").equals(original)&&updated.getString("title").equals(title)&&updated.getString("url").equals(url),"Duration-only native patch preserves note/title/source");
  jsCheck("__actions.at(-1).patch.durationMinutes===30&&Object.keys(__actions.at(-1).patch).length===1&&!__actions.at(-1).record","Actual bridge sends only changed fields, never whole-record overwrite");
  h.js("ChatUI.bottom()");Thread.sleep(400);h.screenshot("learning-integrity-1.7.9.png");
  reload();holdAutomation();learningMock();click("[data-page=chat]");sendText("刚刚那个视频我学习了45分钟");click("[data-confirm-change]");until("!document.querySelector('[data-confirm-change]')");
  check(find(rid).getInt("durationMinutes")==45&&find(rid).getString("note").equals(original),"Persisted structured receipt survives application reload");
  h.call("newChat",new JSONObject());reload();holdAutomation();learningMock();click("[data-page=chat]");sendText("刚刚那个视频我学习了15分钟");
  jsCheck("!document.querySelector('[data-confirm-change]')&&document.querySelector('#chat-messages').innerText.includes('无法确定')","New conversation never guesses target from globally latest record");
  check(find(rid).getInt("durationMinutes")==45,"Ambiguous followup leaves database unchanged");
  JSONObject fresh=find(rid);long version=fresh.getLong("updatedAt");
  h.call("applyRecordAction",new JSONObject().put("kind","update").put("recordId",rid).put("expectedVersion",version).put("patch",new JSONObject().put("note","").put("durationMinutes",50)));
  check(find(rid).getString("note").equals(original),"Native layer independently refuses accidental empty note");
  boolean rejected=false;try{h.call("applyRecordAction",new JSONObject().put("kind","update").put("recordId",rid).put("expectedVersion",version).put("patch",new JSONObject().put("durationMinutes",999)));}catch(Exception expected){rejected=true;}check(rejected&&find(rid).getInt("durationMinutes")==50,"Stale concurrent update cannot overwrite newer data");
  StringBuilder longText=new StringBuilder();for(int i=0;i<2400;i++)longText.append(original);String whole=longText.toString();
  fresh=find(rid);fresh.put("id","integrity-long").put("title","长笔记合成测试").put("url","").put("note",whole);h.call("saveRecord",new JSONObject().put("record",fresh).put("allowDuplicate",true));fresh=find("integrity-long");
  h.call("applyRecordAction",new JSONObject().put("kind","update").put("recordId",fresh.getString("id")).put("expectedVersion",fresh.getLong("updatedAt")).put("patch",new JSONObject().put("durationMinutes",60)));
  check(find("integrity-long").getString("note").equals(whole)&&whole.length()>160000,"160k+ original note survives native duration patch without truncation");
  updated=find(rid);h.call("applyRecordAction",new JSONObject().put("kind","update").put("recordId",rid).put("expectedVersion",updated.getLong("updatedAt")).put("patch",new JSONObject().put("note","")).put("clearFields",new JSONArray().put("note")));
  check(find(rid).getString("note").isEmpty(),"Explicit confirmed note clearing remains supported");
  jsCheck("document.documentElement.scrollWidth<=innerWidth+1","Phone chat layout has no horizontal overflow");
 }
 @Override public void onStart(){Bundle out=new Bundle();try{h=new MainHost(this);h.open();if(phase.equals("seed"))seedIntegrity();else verifyIntegrity();out.putString("stream","PASS "+passed+" learning integrity/native/upgrade/UI checks; mock only\n");finish(Activity.RESULT_OK,out);}catch(Throwable ex){out.putString("stream","FAIL after "+passed+": "+ex+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
