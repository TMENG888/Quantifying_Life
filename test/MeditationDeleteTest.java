package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.database.sqlite.SQLiteDatabase;import com.insight.quantlife.*;import org.json.*;import java.util.*;
/** Disposable emulator, exact synthetic ids; no real user records or model requests. */
public class MeditationDeleteTest extends DocumentEditorTest {
 final ArrayList<String> fixtureIds=new ArrayList<>();String fixtureDay="2026-07-03";int baselineCount;JSONArray original;
 JSONObject cmd(String command,String id)throws Exception{return new JSONObject().put("command",command).put("id",id);}
 JSONObject status()throws Exception{return (JSONObject)h.call("meditation",cmd("status",""));}
 boolean contains(JSONArray rows,String id)throws Exception{for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return true;return false;}
 void nativeChecks()throws Exception{try(SQLiteDatabase db=SQLiteDatabase.create(null)){
  MeditationMapPhotosTest.FakeClock c=new MeditationMapPhotosTest.FakeClock();String a="delete-finished-a",b="delete-finished-b",live="delete-live-timer";
  MeditationStore.execute(db,cmd("start",a),c);c.elapsed+=3000;MeditationStore.execute(db,cmd("finish",a),c);MeditationStore.execute(db,cmd("note",a).put("note","误记"),c);
  MeditationStore.execute(db,cmd("start",b),c);c.elapsed+=60000;MeditationStore.execute(db,cmd("finish",b),c);MeditationStore.execute(db,cmd("note",b).put("note","另一条感受不能被覆盖"),c);
  MeditationStore.execute(db,cmd("start",live),c);c.elapsed+=4000;
  try{MeditationStore.execute(db,cmd("delete",live),c);throw new Exception("Active delete accepted");}catch(Exception ex){check(ex.getMessage().contains("请先结束"),"Running stopwatch cannot be deleted");}
  MeditationStore.execute(db,cmd("pause",live),c);try{MeditationStore.execute(db,cmd("delete",live),c);throw new Exception("Paused delete accepted");}catch(Exception ex){check(ex.getMessage().contains("请先结束"),"Paused stopwatch cannot be deleted");}
  try{MeditationStore.execute(db,cmd("delete","' OR 1=1 --"),c);throw new Exception("Invalid delete accepted");}catch(Exception ex){check(ex.getMessage().contains("编号无效"),"Invalid ids never broaden deletion");}
  JSONObject s=MeditationStore.execute(db,cmd("delete",a),c);check(s.getJSONArray("sessions").length()==1&&!contains(s.getJSONArray("sessions"),a),"Delete removes exact completed record");
  check(s.getJSONObject("active").getString("id").equals(live)&&s.getJSONObject("active").getLong("durationMs")==4000,"Deleting history does not stop or reset current timer");
  check(s.getJSONArray("sessions").getJSONObject(0).getString("note").equals("另一条感受不能被覆盖"),"Other reflection survives deletion unchanged");
  check(MeditationStore.execute(db,cmd("delete",a),c).getJSONArray("sessions").length()==1,"Retrying deletion is idempotent");
  check(MeditationStore.export(db).length()==1&&!contains(MeditationStore.export(db),a),"New backups exclude deleted record");
 }}
 void observe()throws Exception{h.js("MeditationUI.accept("+status()+")");}
 void reopen()throws Exception{runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{};window.__native=LifeNative;window.__deleteCalls=0;window.__failDelete=false;LifeNative={call:(a,p,id)=>{const q=JSON.parse(p);if(a==='ask'){setTimeout(()=>nativeReply({id,error:'Synthetic tests do not use models'}),5);return;}if(a==='meditation'&&q.command==='delete'){__deleteCalls++;if(__failDelete){setTimeout(()=>nativeReply({id,error:'合成删除错误'}),5);return;}}__native.call(a,p,id);}}");}
 void seedHistory()throws Exception{
  long start=new java.math.BigDecimal(h.js("new Date('"+fixtureDay+"T09:30:00').getTime()")).longValueExact();JSONArray rows=new JSONArray();
  for(int i=0;i<2;i++){String id="delete-history-"+System.nanoTime();fixtureIds.add(id);rows.put(new JSONObject().put("id",id).put("state","finished").put("startedAt",start+i*3600000).put("endedAt",start+i*3600000+60000*(i+1)).put("accumulatedMs",60000*(i+1)).put("note",i==0?"合成误记，可删除":"合成感受，保留后再删除"));}
  h.call("import",new JSONObject().put("records",new JSONArray()).put("meditationSessions",rows));observe();
 }
 String deleteCss(String id){return "[data-meditation-delete=\""+id+"\"]";}
 void selectFixtureDay()throws Exception{h.js("document.querySelector('#meditation-month').value='2026-07';document.querySelector('#meditation-month').dispatchEvent(new Event('change'))");click("[data-meditation-day='"+fixtureDay+"']");}
 void uiChecks()throws Exception{
  original=status().getJSONArray("sessions");baselineCount=original.length();if(!status().isNull("active"))throw new Exception("Existing active timer; refusing to interfere");
  for(int i=0;i<original.length();i++)if(new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date(original.getJSONObject(i).getLong("startedAt"))).equals(fixtureDay))throw new Exception("Fixture day contains unrelated data");
  click("[data-go=meditation]");h.tap("#meditation-start",false);until("!!document.querySelector('#meditation-end')");Thread.sleep(1200);h.tap("#meditation-end",false);until("!!document.querySelector('.meditation-delete-record')");
  String id=new JSONArray("["+h.js("document.querySelector('[data-meditation-delete]').dataset.meditationDelete")+"]").getString(0);fixtureIds.add(id);
  check(status().getJSONArray("sessions").length()==baselineCount+1,"Ending a real timer saves one session with delete action");h.screenshot("meditation-result-delete.png");
  h.js("document.querySelector('#meditation-note').value='取消删除时保留输入';document.querySelector('#meditation-note').dispatchEvent(new Event('input'));document.querySelector('.meditation-delete-record').scrollIntoView({block:'center'})");Thread.sleep(180);h.tap(".meditation-delete-record",false);until("document.querySelector('#confirm-dialog').open");
  check(h.js("document.querySelector('#confirm-message').textContent.includes('无法撤销')&&document.activeElement.id==='confirm-cancel'").equals("true"),"Deletion confirmation identifies irreversible action and defaults to cancel");
  check(h.js("(()=>{const b=document.querySelector('#confirm-cancel'),r=b.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight&&b.contains(document.elementFromPoint((r.left+r.right)/2,(r.top+r.bottom)/2));})()").equals("true"),"Confirmation is visible and receives touch above the page");Thread.sleep(700);h.screenshot("meditation-delete-confirm.png");h.tap("#confirm-cancel",false);until("!document.querySelector('#confirm-dialog').open");
  check(contains(status().getJSONArray("sessions"),id)&&h.js("document.querySelector('#meditation-note').value==='取消删除时保留输入'").equals("true"),"Cancel retains exact saved record and unsaved reflection");
  click(".meditation-delete-record");until("document.querySelector('#confirm-dialog').open");h.tap("#confirm-ok",false);until("!!document.querySelector('#meditation-month')");
  check(!contains(status().getJSONArray("sessions"),id)&&status().getJSONArray("sessions").length()==baselineCount,"Confirm deletes only selected result and returns to history");
  seedHistory();selectFixtureDay();String first=fixtureIds.get(1),second=fixtureIds.get(2);h.js("document.querySelector('.meditation-session-row').scrollIntoView({block:'center'})");Thread.sleep(1800);h.screenshot("meditation-history-delete.png");
  check(h.js("document.querySelectorAll('.meditation-session-row').length===2&&document.querySelector('.meditation-history-summary').textContent.includes('3 分 0 秒')").equals("true"),"History displays correct count and combined time before deletion");
  check(h.js("!document.querySelector('.meditation-session button')&&document.querySelector('.meditation-delete-session').getBoundingClientRect().width>=48").equals("true"),"History delete is a separate 48px target, not nested in open action");
  h.js("__failDelete=true;document.querySelector("+JSONObject.quote(deleteCss(first))+").scrollIntoView({block:'center'})");Thread.sleep(180);h.tap(deleteCss(first),false);until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("document.querySelector('#toast').textContent.includes('删除未完成')");
  check(contains(status().getJSONArray("sessions"),first)&&h.js("!document.querySelector("+JSONObject.quote(deleteCss(first))+").disabled").equals("true"),"Failed deletion keeps history intact and enables retry");
  h.js("__failDelete=false");click(deleteCss(first));until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("document.querySelectorAll('.meditation-session-row').length===1");
  check(h.js("document.querySelector('.meditation-history-summary').textContent.includes('2 分 0 秒')&&document.querySelector('.section-title .count').textContent==='1 次'&&document.querySelector('[data-meditation-day=\"2026-07-03\"]').getAttribute('aria-label').includes('1 次')").equals("true"),"Delete updates duration, selected day count and calendar marker immediately");
  check(contains(status().getJSONArray("sessions"),second),"Adjacent record remains after deletion");
  click(deleteCss(second));until("document.querySelector('#confirm-dialog').open");click("#confirm-ok");until("!!document.querySelector('#meditation-root .empty')");
  check(h.js("document.querySelector('.meditation-history-summary').textContent.includes('0 秒')&&document.querySelector('.section-title .count').textContent==='0 次'&&!document.querySelector('[data-meditation-day=\"2026-07-03\"]').getAttribute('aria-label').includes('次')").equals("true"),"Last deletion clears day marker and shows zero monthly total");
  reopen();check(status().getJSONArray("sessions").length()==baselineCount,"Deletion persists after closing and reopening activity");
  JSONArray now=status().getJSONArray("sessions");for(int i=0;i<original.length();i++){JSONObject old=original.getJSONObject(i);boolean match=false;for(int j=0;j<now.length();j++)if(now.getJSONObject(j).getString("id").equals(old.getString("id")))match=now.getJSONObject(j).toString().equals(old.toString());check(match,"Unrelated existing meditation preserved: "+i);}
 }
 void layoutChecks()throws Exception{
  seedHistory();click("[data-go=meditation]");click("#meditation-history");selectFixtureDay();
  for(int width:new int[]{320,375,768}){h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width="+width+",initial-scale=1')");Thread.sleep(300);check(h.js("document.documentElement.scrollWidth<=innerWidth+1&&[...document.querySelectorAll('.meditation-delete-session')].every(b=>b.getBoundingClientRect().height>=48&&b.getBoundingClientRect().width>=48)").equals("true"),"History deletion fits viewport "+width);}
  h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width=device-width,initial-scale=1,viewport-fit=cover')");runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(300);check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"200 percent text stays within history layout");h.js("document.querySelector('.meditation-session-row').scrollIntoView({block:'center'})");Thread.sleep(400);h.screenshot("meditation-delete-large-text.png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
 }
 @Override public void onStart(){Bundle out=new Bundle();int result=Activity.RESULT_CANCELED;String settings=getTargetContext().getSharedPreferences("secure",0).getString("settings",null);try{
  if(!Build.MODEL.contains("SDK")&&!Build.FINGERPRINT.contains("generic"))throw new Exception("Disposable emulator only");getTargetContext().getSharedPreferences("secure",0).edit().remove("settings").commit();nativeChecks();h=new MainHost(this);h.open();reopen();
  if(phase.equals("layout")){layoutChecks();check(h.js("innerWidth>innerHeight").equals("true"),"Actual landscape viewport exercised");}else{uiChecks();layoutChecks();}
  out.putString("stream","PASS "+passed+" meditation deletion checks\n");result=Activity.RESULT_OK;
 }catch(Throwable ex){try{h.screenshot("meditation-delete-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL after "+passed+": "+ex+"\n");ex.printStackTrace();}
 finally{if(h!=null)for(String id:fixtureIds)try{h.call("meditation",cmd("delete",id));}catch(Exception ignored){}android.content.SharedPreferences.Editor p=getTargetContext().getSharedPreferences("secure",0).edit();if(settings==null)p.remove("settings");else p.putString("settings",settings);p.commit();}finish(result,out);}
}
