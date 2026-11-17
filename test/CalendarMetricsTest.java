package com.insight.quantlife.tests;
import android.app.*;import android.database.sqlite.SQLiteDatabase;import android.os.*;import android.view.MotionEvent;import com.insight.quantlife.*;import org.json.*;

/** Offline fixtures, real WebView taps/holds. No model credentials or API requests. */
public final class CalendarMetricsTest extends DocumentEditorTest {
 JSONArray original;double gestureScroll;
 JSONObject r(String id,String type,String date,String note,int n,int minutes)throws Exception{
  JSONArray units=new JSONArray();for(int i=0;i<n;i++)units.put(new JSONObject().put("text","独立知识点 "+(i+1)).put("sources",new JSONArray().put(new JSONObject().put("start",0).put("end",note.length()).put("quote",note))));
  return new JSONObject().put("id",id).put("type",type).put("date",date).put("title","日历验证 "+id).put("note",note).put("durationMinutes",minutes).put("createdAt",100).put("updatedAt",100).put("tags",new JSONArray()).put("informationUnits",new JSONObject().put("schemaVersion",InformationUnits.VERSION).put("model","deepseek-flash").put("provider",InformationUnits.PROVIDER).put("unitRule",InformationUnits.RULE).put("sourceVersion",100).put("sourceHash",QwenLabels.hash(new JSONObject().put("note",note))).put("analyzedAt",100).put("complete",true).put("count",n).put("characters",InformationUnits.characters(note)).put("units",units));
 }
 void put(SQLiteDatabase db,JSONObject r)throws Exception{db.execSQL("INSERT INTO records(id,type,date,title,platform,payload) VALUES(?,?,?,?,?,?)",new Object[]{r.getString("id"),r.getString("type"),r.getString("date"),r.optString("title"),"",r.toString()});}
 void seed()throws Exception{try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){db.beginTransaction();try{
  db.delete("records",null,null);
  put(db,r("cal-a","learning","2000-02-28","短文",2,10));put(db,r("cal-b","learning","2000-02-29","一二三四五六七八九十一二三四五六七八",8,50));
  put(db,r("cal-zero","learning","2000-01-02","没有知识点",0,0));
  JSONObject pending=r("cal-pending","learning","2000-03-01","待分析内容",3,20);pending.remove("informationUnits");put(db,pending);
  JSONObject media=r("cal-media","learning","2000-03-02","![图片](media:x)",0,0);media.remove("informationUnits");put(db,media);
  JSONObject w=r("cal-shift","work","2000-02-28","",0,0).put("workStart","2000-02-28T23:00:00").put("workEnd","2000-02-29T02:00:00").put("breaks",new JSONArray("[{\"start\":\"2000-02-29T00:30:00\",\"end\":\"2000-02-29T01:00:00\"}]"));put(db,w);
  put(db,r("cal-log","work_note","2000-02-29","工作知识",4,999));put(db,r("cal-essay","idea","2000-02-29","随笔知识",3,999));put(db,r("cal-essay2","experience","2000-02-29","经验知识",2,999));
  db.setTransactionSuccessful();
 }finally{db.endTransaction();}}}
 void select(String id,String value)throws Exception{h.js("document.querySelector("+JSONObject.quote(id)+").value="+JSONObject.quote(value)+";document.querySelector("+JSONObject.quote(id)+").dispatchEvent(new Event('change'))");}
 double scrollY()throws Exception{return Double.parseDouble(h.js("window.scrollY"));}
 void sameScroll(double y,String action)throws Exception{check(Math.abs(scrollY()-y)<=1,action+": background scroll stays unchanged");}
 void fits(String mode)throws Exception{
  check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),mode+": no horizontal overflow");
  check(h.js("[...document.querySelectorAll('.calendar-bar,.calendar-chart-heading button,#calendar-metric')].every(b=>{const r=b.getBoundingClientRect();return r.height>=47&&r.left>=-1&&r.right<=innerWidth+1})").equals("true"),mode+": usable controls fit");
  check(h.js("[...document.querySelectorAll('.evidence-days small,.calendar-bar-value,.calendar-bar-date')].every(x=>x.scrollWidth<=x.clientWidth+1)").equals("true"),mode+": values stay inside their cells");
 }
 void gesture(String selector,boolean move)throws Exception{
  h.js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center'})");Thread.sleep(300);
  gestureScroll=scrollY();
  JSONArray p=new JSONArray(h.js("(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [(r.left+r.right)/2,r.top+70,innerWidth]})()"));
  final float x=(float)(p.getDouble(0)*h.web.getWidth()/p.getDouble(2)),y=(float)(p.getDouble(1)*h.web.getWidth()/p.getDouble(2));final long t=SystemClock.uptimeMillis();
  runOnMainSync(()->{MotionEvent v=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);h.web.dispatchTouchEvent(v);v.recycle();});
  Thread.sleep(120);
  if(move)runOnMainSync(()->{MotionEvent v=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x,y-35,0);h.web.dispatchTouchEvent(v);v.recycle();});
  Thread.sleep(550);
  if(!move)check(h.js("document.querySelector("+JSONObject.quote(selector)+").classList.contains('is-held')").equals("true"),"Physical long press highlights the chosen bar");
  runOnMainSync(()->{MotionEvent v=MotionEvent.obtain(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,move?y-35:y,0);h.web.dispatchTouchEvent(v);v.recycle();});Thread.sleep(200);
 }
 void checks()throws Exception{
  h=new MainHost(this);h.open();original=records();runOnMainSync(()->h.activity.finish());getTargetContext().getSystemService(android.app.job.JobScheduler.class).cancel(7305);seed();
  h=new MainHost(this);h.open();until("typeof document.querySelector('[data-page=records]').onclick==='function'");h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  String before=records().toString();click("[data-page=records]");click("#topic-entry");click("[data-topic-range=custom]");
  h.js("document.querySelector('#topic-start').value='2000-01-01';document.querySelector('#topic-start').dispatchEvent(new Event('input'));document.querySelector('#topic-end').value='2000-03-02';document.querySelector('#topic-end').dispatchEvent(new Event('input'));document.querySelector('#topic-dates').requestSubmit()");
  select("#evidence-month","2000-02");
  check(h.js("!document.querySelector('.quant-card,.information-card')&&document.querySelectorAll('#calendar-metric').length===1").equals("true"),"One metric calendar replaces both independent modules");
  check(h.js("document.querySelectorAll('.evidence-days button').length===29&&document.querySelector('[data-evidence-day=\"2000-02-29\"] small').textContent==='8'").equals("true"),"Leap calendar displays daily information units");
  check(h.js("parseFloat(document.querySelector('.calendar-month-total strong').textContent)===10").equals("true"),"Month information total is ten");
  h.js("document.querySelector('[data-evidence-day=\"2000-02-29\"]').scrollIntoView({block:'center'})");Thread.sleep(250);double dayScroll=scrollY();h.tap("[data-evidence-day='2000-02-29']",false);until("EvidenceDashboard.active()");sameScroll(dayScroll,"Tap calendar date");
  check(h.js("document.querySelector('#calendar-detail-dialog').open&&document.querySelectorAll('#evidence-day-detail [data-record]').length===1&&document.querySelector('[data-record-metrics=cal-b] [data-record-metric=units] strong').textContent==='8'").equals("true"),"Date opens isolated sheet with per-record metrics");
  click("#evidence-day-close");sameScroll(dayScroll,"Close calendar detail");
  select("#calendar-metric","density");check(h.js("parseFloat(document.querySelector('.calendar-month-total strong').textContent)===500&&document.querySelector('.calendar-trend').getAttribute('aria-label').includes('信息密度柱状图')&&!document.querySelector('.calendar-trend svg')").equals("true"),"Density uses pooled 10/20 denominator and bars");
  select("#calendar-metric","efficiency");check(h.js("parseFloat(document.querySelector('.calendar-month-total strong').textContent)===10").equals("true"),"Efficiency pools ten units per hour");
  select("#calendar-metric","units");click("[data-calendar-grain=month]");
  check(h.js("document.querySelectorAll('[data-calendar-bar]').length===3&&document.querySelector('[data-calendar-bar=\"2000-02\"] .calendar-bar-value').textContent==='10'").equals("true"),"Monthly bars match the calendar totals");
  h.js("document.querySelector('[data-calendar-bar=\"2000-02\"]').scrollIntoView({block:'center'})");Thread.sleep(250);double barScroll=scrollY();h.tap("[data-calendar-bar='2000-02']",false);until("EvidenceDashboard.active()");sameScroll(barScroll,"Tap monthly bar");
  check(h.js("document.querySelectorAll('#evidence-day-detail [data-record]').length===2&&document.querySelector('.evidence-day-total strong').textContent==='10'").equals("true"),"Real tap opens monthly records and matching total");
  click("[data-calendar-record='cal-a']");check(h.js("document.querySelectorAll('.calendar-units li').length===2&&document.querySelector('.calendar-units blockquote').textContent==='短文'").equals("true"),"Information units retain exact original evidence");
  h.js("document.querySelector('[data-calendar-quote]').open=true");Thread.sleep(10400);check(h.js("document.querySelector('[data-calendar-quote]').open&&document.querySelector('[data-calendar-record=cal-a]').getAttribute('aria-expanded')==='true'").equals("true"),"Background refresh preserves expanded original evidence");
  click("#evidence-day-detail [data-record='cal-a']");until("!!document.querySelector('#doc-flow')");check(h.js("document.querySelector('#doc-flow').textContent.includes('短文')").equals("true"),"Detail opens original record reader");close();check(h.js("EvidenceDashboard.active()").equals("true"),"Reader close returns to the existing calendar detail");
  click("#evidence-day-close");sameScroll(barScroll,"Close monthly bar detail");gesture("[data-calendar-bar='2000-02']",false);sameScroll(gestureScroll,"Long press monthly bar");
  check(h.js("document.querySelectorAll('#evidence-day-detail').length===1&&document.querySelector('.evidence-day-total strong').textContent==='10'").equals("true"),"Long press release retains exactly one detail panel");
  h.screenshot("calendar-detail.png");click("#evidence-day-close");sameScroll(gestureScroll,"Close long-press detail");h.js("document.querySelector('.calendar-trend').scrollIntoView({block:'start'})");h.screenshot("calendar-bars.png");gesture("[data-calendar-bar='2000-02']",true);
  check(h.js("!EvidenceDashboard.active()").equals("true"),"Scrolling cancels the long-press gesture");
  select("#evidence-month","2000-03");check(h.js("document.querySelector('[data-evidence-day=\"2000-03-01\"] small').textContent==='待分析'&&document.querySelector('[data-evidence-day=\"2000-03-02\"] small').textContent==='无正文'").equals("true"),"Pending and media-only days differ from zero");
  select("#evidence-month","2000-01");check(h.js("document.querySelector('[data-evidence-day=\"2000-01-02\"] small').textContent==='0'").equals("true"),"True zero information remains zero");select("#calendar-metric","efficiency");check(h.js("document.querySelector('[data-evidence-day=\"2000-01-02\"] small').textContent==='未计时'").equals("true"),"Missing time remains missing");
  click("[data-record-tab=work]");check(h.js("!document.querySelector('#calendar-metric option[value=efficiency]')&&document.querySelector('[data-evidence-day=\"2000-02-29\"] small').textContent==='4'").equals("true"),"Work uses log information without fictitious productivity");
  select("#calendar-metric","hours");click("[data-evidence-day='2000-02-29']");check(h.js("document.querySelector('.evidence-day-total strong').textContent==='1.5'&&document.querySelectorAll('#evidence-day-detail [data-record]').length===2").equals("true"),"Net work hours deduct rests, preserve separate journal");fits("work");click("#evidence-day-close");
  click("[data-record-tab=thoughts]");check(h.js("document.querySelector('[data-evidence-day=\"2000-02-29\"] small').textContent==='5'&&document.querySelectorAll('#calendar-metric option').length===3").equals("true"),"Unified essays show five units and appropriate metrics");
  click("[data-record-tab=learning]");select("#evidence-month","2000-02");select("#calendar-metric","units");fits("portrait");h.js("document.querySelector('.evidence-calendar').scrollIntoView({block:'start'})");h.screenshot("calendar-units.png");
  runOnMainSync(()->h.web.getSettings().setTextZoom(200));Thread.sleep(400);fits("200% text");h.js("document.querySelector('.evidence-calendar').scrollIntoView({block:'start'})");h.screenshot("calendar-large.png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
  check(records().toString().equals(before),"All metric switching and gestures preserve source records");
  JSONObject nativeStatus=(JSONObject)h.call("quantLabelStatus",new JSONObject());check(nativeStatus.getInt("informationPending")==1&&nativeStatus.getInt("pending")==1,"Background queue only counts missing information, not removed content/action labels");
 }
 @Override public void onStart(){Bundle out=new Bundle();boolean ok=false;try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");checks();ok=true;out.putString("stream","PASS CalendarMetricsTest: "+passed+" checks\n");
 }catch(Throwable error){out.putString("stream","FAIL CalendarMetricsTest after "+passed+": "+android.util.Log.getStackTraceString(error));try{if(h!=null)h.screenshot("calendar-failure.png");}catch(Exception ignored){}}
 finally{try{if(h!=null){runOnMainSync(()->h.web.getSettings().setTextZoom(100));runOnMainSync(()->h.activity.finish());}if(original!=null)try(SQLiteDatabase db=SQLiteDatabase.openDatabase(getTargetContext().getDatabasePath("quantlife.db").getPath(),null,SQLiteDatabase.OPEN_READWRITE)){db.beginTransaction();try{db.delete("records",null,null);for(int i=0;i<original.length();i++)put(db,original.getJSONObject(i));db.setTransactionSuccessful();}finally{db.endTransaction();}}}catch(Exception ignored){}}
 finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
