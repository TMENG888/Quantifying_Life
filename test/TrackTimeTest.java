package com.insight.quantlife.tests;
import android.app.*;import android.os.Bundle;import android.location.Location;
import com.insight.quantlife.*;import org.json.*;import java.util.*;

/** Disposable emulator: synthetic day/coordinates only, no location upload or paid APIs. */
public class TrackTimeTest extends Instrumentation {
    MainHost h;int passed;boolean landscape,night;final String date="2026-12-22";long base;String raw,gpx,original;JSONObject init;
    @Override public void onCreate(Bundle b){super.onCreate(b);landscape=b!=null&&"true".equals(b.getString("landscape"));night=b!=null&&"true".equals(b.getString("night"));start();}
    void check(boolean ok,String message)throws Exception{if(!ok)throw new Exception(message);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+message+"\n");sendStatus(0,b);}
    void until(String js,String message)throws Exception{for(int i=0;i<120;i++){if(h.js(js).equals("true"))return;Thread.sleep(100);}throw new Exception(message+": "+h.js("document.querySelector('#track-time-status')?.textContent"));}
    void click(String selector)throws Exception{h.js("document.querySelector("+JSONObject.quote(selector)+").click()");}
    void input(String selector,String value)throws Exception{h.js("document.querySelector("+JSONObject.quote(selector)+").value="+JSONObject.quote(value));}
    JSONObject request(String mode)throws Exception{return new JSONObject().put("date",date).put("mode",mode);}
    JSONObject query(JSONObject p)throws Exception{return (JSONObject)h.call("trackTime",p);}
    JSONObject point(long time,double lat,String state)throws Exception{return new JSONObject().put("time",time).put("lat",lat).put("lon",121.47).put("accuracy",8).put("session","synthetic").put("motionState",state).put("pathDistance",true);}
    JSONObject day(JSONArray points)throws Exception{return new JSONObject().put("date",date).put("points",points).put("filter",new JSONObject().put("id","weighted65"));}
    void seed()throws Exception{
        h.call("trackConfig",new JSONObject().put("filterMode","weighted65").put("mapMode","offline").put("mapConsent",false));
        base=TrackStore.dayStart(date);try(TrackStore db=new TrackStore(getTargetContext())){
            JSONArray prior=db.diagnostics(date).getJSONArray("rawPoints");for(int i=0;i<prior.length();i++)if(!prior.getJSONObject(i).getString("session").equals("time-fixture"))throw new Exception("Fixture day has unrelated data; refusing to overwrite");if(prior.length()>0)db.deleteDay(date);
            check(db.day(date).getInt("totalPoints")==0,"Synthetic fixture day is unused or contains only prior test fixture");
            for(int i=0;i<=36;i++)db.add(BeforeGapFixTest.p(base+9*3600000+i*5000,i*7,1.4f),"time-fixture");
            db.add(BeforeGapFixTest.p(base+9*3600000+29*60000+56000,300,1.4f),"time-fixture");
            db.add(BeforeGapFixTest.p(base+9*3600000+30*60000+4000,311.2,1.4f),"time-fixture");
            db.add(BeforeGapFixTest.p(base+9*3600000+59*60000+55000,500,1.4f),"time-fixture");
            db.add(BeforeGapFixTest.p(base+10*3600000,507,1.4f),"time-fixture");
            db.add(BeforeGapFixTest.p(base+24*3600000-5000,550,0),"time-fixture");
            raw=db.diagnostics(date).getJSONArray("rawPoints").toString();gpx=db.gpx(date);original=db.gpx(date,true);
        }
        init=(JSONObject)h.call("init",new JSONObject());
    }
    void nativeChecks()throws Exception{
        JSONObject range=query(request("range").put("start","09:00").put("end","10:00"));JSONArray points=range.getJSONArray("points");
        check(points.length()==40,"09–10 range queries all processed points before display reduction");
        check(points.getJSONObject(0).getLong("time")==base+9*3600000,"Start boundary is inclusive");
        check(points.getJSONObject(points.length()-1).getLong("time")<base+10*3600000,"End boundary is exclusive, no outside point borrowed");
        check(range.getLong("estimatedMeters")<300,"Gaps and outside edges do not inflate range mileage");
        JSONObject moment=query(request("moment").put("at","09:30"));
        check(moment.getJSONObject("match").getLong("time")==base+9*3600000+29*60000+56000,"Nearest point found with actual timestamp; equal-distance tie chooses earlier");
        check(query(request("moment").put("at","09:29:56")).getJSONObject("match").getLong("time")==moment.getJSONObject("match").getLong("time"),"Exact second query uses original processed observation");
        JSONObject empty=query(request("moment").put("at","12:00"));
        check(empty.isNull("match")&&empty.getJSONArray("points").length()==0,"No interpolation across missing hours");
        check(!empty.isNull("before")&&!empty.isNull("after"),"Missing target offers separate earlier/later observations");
        check(query(request("range").put("start","23:00").put("end","00:00")).getInt("processedPoints")==1,"Final hour supports next midnight");
        check(query(request("range").put("start","00:00").put("end","00:00")).getInt("processedPoints")==42,"Midnight to midnight means full selected day");
        check(query(request("range").put("start","11:00").put("end","12:00")).getInt("processedPoints")==0,"Empty range is honest empty view");
        for(String[] bounds:new String[][]{{"10:00","09:00"},{"09:00","09:00"},{"25:00","26:00"},{"","10:00"}}){boolean rejected=false;try{query(request("range").put("start",bounds[0]).put("end",bounds[1]));}catch(Exception e){rejected=true;}check(rejected,"Invalid range rejected: "+Arrays.toString(bounds));}
        boolean bad=false;try{query(request("moment").put("at","24:00"));}catch(Exception e){bad=true;}check(bad,"Invalid moment rejected");
        long target=base+9*3600000;JSONArray sparse=new JSONArray().put(point(target-60001,31.23,"moving")).put(point(target,31.23,"uncertain")).put(point(target+60001,31.23,"moving"));
        check(TrackTime.query(day(sparse),request("moment").put("at","09:00")).isNull("match"),"Uncertain held coordinates and >60s neighbors cannot claim target location");
        sparse.put(point(target+60000,31.23,"stationary_estimate"));check(!TrackTime.query(day(sparse),request("moment").put("at","09:00")).isNull("match"),"60-second boundary retained with stationary-estimate label");
        JSONArray large=new JSONArray();List<TrackFilter.Point> full=new ArrayList<>();
        for(int i=0;i<22000;i++){long t=base+6*3600000+i*1000;large.put(point(t,31.23+i*.000001,"moving"));TrackFilter.Point p=new TrackFilter.Point();p.time=t;p.lat=31.23+i*.000001;p.lon=121.47;p.accuracy=8;p.session="synthetic";full.add(p);}
        long omitted=base+9*3600000+30*60000;boolean retained=false;for(TrackFilter.Point p:TrackDisplay.simplify(full))if(p.time==omitted)retained=true;
        check(!retained,"Fixture exact target really omitted by all-day display simplification");
        check(TrackTime.query(day(large),request("moment").put("at","09:30")).getJSONObject("match").getLong("time")==omitted,"Moment query still finds omitted full-data point");
        JSONObject largeRange=TrackTime.query(day(large),request("range").put("start","06:00").put("end","13:00"));
        check(largeRange.getInt("processedPoints")==22000&&largeRange.getBoolean("displaySimplified")&&largeRange.getJSONArray("points").length()<22000,"Range computes full stats then simplifies only its display");
    }
    void uiChecks()throws Exception{
        click("[data-page=tracks]");until("!!document.querySelector('#track-date')","Track page opens");
        h.js("document.querySelector('#track-date').value='"+date+"';document.querySelector('#track-date').dispatchEvent(new Event('change'))");
        until("document.querySelector('#track-map').dataset.pointCount==='42'","Synthetic full day loads");
        check(h.js("document.querySelector('[data-track-time-mode=all]').getAttribute('aria-pressed')").equals("\"true\""),"All-day is default");
        click("[data-track-time-mode=range]");until("document.querySelector('#track-map').dataset.pointCount==='40'","Default 09–10 range renders");
        check(h.js("document.querySelector('#track-map').dataset.timeMode==='range' && document.querySelector('#track-fit').textContent==='适应'").equals("true"),"Map controls and data reflect selected range");
        h.js("document.querySelector('.track-time').scrollIntoView({block:'start'})");Thread.sleep(700);h.js("document.querySelector('.track-time').scrollIntoView({block:'start'})");h.screenshot("track-time-range.png");
        click("[data-track-time-mode=moment]");until("document.querySelector('#track-map').dataset.selectedTime==='"+(base+9*3600000+29*60000+56000)+"'","Moment marker appears at nearest real sample");
        check(h.js("document.querySelector('#track-selected-point').textContent.includes('09:29:56') && document.querySelector('#track-time-status').textContent.includes('4 秒')").equals("true"),"Actual time and offset displayed, not fabricated 09:30");
        check(h.js("document.querySelector('#track-map').dataset.pointCount==='1' && document.querySelector('#track-map').dataset.roadRoutes==='0'").equals("true"),"Moment view shows one marker and no all-day routes");
        h.js("document.querySelector('.track-time').scrollIntoView({block:'start'})");h.screenshot("track-time-moment.png");
        input("#track-time-at","12:00");click("#track-time-apply");until("document.querySelector('#track-map').dataset.pointCount==='0'&&!document.querySelector('#track-time-apply').disabled","Missing moment completes without stuck loading");
        check(h.js("document.querySelectorAll('[data-track-near-time]').length===2 && document.querySelector('#track-map').dataset.selectedTime===''").equals("true"),"Missing moment clears stale marker and offers explicit neighbor buttons");
        h.screenshot("track-time-missing.png");click("[data-track-near-time]");until("document.querySelector('#track-map').dataset.pointCount==='1'","Selecting prior observation shows that actual time");
        click("[data-track-time-mode=range]");until("!document.querySelector('#track-time-apply').disabled","Range settles");
        input("#track-time-start","11:00");input("#track-time-end","10:00");click("#track-time-apply");until("document.querySelector('#track-time-status').classList.contains('warning')","Inline invalid-range error");
        check(h.js("!document.querySelector('#track-time-apply').disabled && document.querySelector('#track-time-start').value==='11:00'").equals("true"),"Error preserves inputs and allows retry");
        h.js("document.querySelector('#track-time-hour').value='9';document.querySelector('#track-time-hour').dispatchEvent(new Event('change'))");until("document.querySelector('#track-map').dataset.pointCount==='40'","Hourly shortcut applies 09–10 in one selection");
        h.js("document.querySelector('#track-time-hour').value='23';document.querySelector('#track-time-hour').dispatchEvent(new Event('change'))");until("document.querySelector('#track-map').dataset.pointCount==='1'","23–24 preset includes final hour");
        check(h.js("document.querySelector('#track-time-end').value==='00:00' && document.querySelector('#track-time-status').textContent.includes('24:00')").equals("true"),"Midnight range is clearly labeled");
        // Delay one synthetic result to ensure old lookups cannot overwrite newer mode/date.
        h.js("window.__timeNative=LifeNative;window.__timeFail=false;LifeNative={call:function(action,payload,id){const p=JSON.parse(payload);if(action==='trackTime'&&window.__timeFail){window.__timeFail=false;nativeReply({id,error:'测试查询失败，可重试'});return;}if(action==='trackTime'&&p.at==='09:31'&&p.mode==='moment'){setTimeout(()=>nativeReply({id,data:{date:p.date,mode:'moment',points:[],match:null,filter:{id:'weighted65'}}}),1200);return;}window.__timeNative.call(action,payload,id)}}");
        click("[data-track-time-mode=moment]");until("!document.querySelector('#track-time-apply').disabled","Moment settles before race");
        input("#track-time-at","09:31");click("#track-time-apply");click("[data-track-time-mode=all]");Thread.sleep(1600);
        check(h.js("document.querySelector('#track-map').dataset.timeMode==='all' && document.querySelector('#track-map').dataset.pointCount==='42'").equals("true"),"Late time query cannot replace all-day selection");
        click("[data-track-time-mode=moment]");input("#track-time-at","09:30");h.js("window.__timeFail=true;document.querySelector('#track-time-at').dispatchEvent(new Event('change'))");until("document.querySelector('#track-time-status').textContent.includes('测试查询失败')","Native failure shown inline");
        click("#track-time-apply");until("document.querySelector('#track-map').dataset.pointCount==='1'","Failed lookup can retry successfully");
        click("#track-refresh");until("!document.querySelector('#track-time-apply').disabled && document.querySelector('#track-map').dataset.pointCount==='1'","Refresh keeps time selection");
        h.js("document.querySelector('#track-date').value='2026-12-23';document.querySelector('#track-date').dispatchEvent(new Event('change'))");until("document.querySelector('#track-map').dataset.timeMode==='all' && document.querySelector('#track-map').dataset.pointCount==='0'","Date change resets selection and old point");
        h.js("document.querySelector('#track-date').value='"+date+"';document.querySelector('#track-date').dispatchEvent(new Event('change'))");until("document.querySelector('#track-map').dataset.pointCount==='42'","Return date reloads full day");
        click("[data-track-time-mode=range]");until("!document.querySelector('#track-time-apply').disabled","Range ready for responsive checks");
        h.js("document.querySelector('#track-time-hour').value='9';document.querySelector('#track-time-hour').dispatchEvent(new Event('change'))");until("document.querySelector('#track-map').dataset.pointCount==='40'","Range restored");
        h.js("document.querySelector('.track-time').scrollIntoView({block:'start'})");Thread.sleep(300);h.js("window.__timeOldCanvas=document.querySelector('#track-map');window.__timeOldScroll=scrollY;document.querySelector('#track-refresh').click()");
        until("document.querySelector('#track-map')!==window.__timeOldCanvas && document.querySelector('#track-map').dataset.pointCount==='40'","Refresh finishes in time view");Thread.sleep(400);
        check(h.js("Math.abs(scrollY-window.__timeOldScroll)<3").equals("true"),"Refreshing selected time does not jump the page to top");
        for(int width:new int[]{320,375,667}){
            h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width="+width+",initial-scale=1');document.querySelector('.track-time').scrollIntoView({block:'start'})");Thread.sleep(600);
            check(h.js("document.documentElement.scrollWidth<=innerWidth+1 && [...document.querySelectorAll('.track-time button,.track-time input,.track-time select')].every(e=>e.getBoundingClientRect().height>=48)").equals("true"),"No horizontal overflow and 48px targets at width "+width);
            h.screenshot("track-time-width-"+width+".png");
        }
        h.js("document.querySelector('meta[name=viewport]').setAttribute('content','width=device-width,initial-scale=1,viewport-fit=cover');document.documentElement.style.fontSize='22px'");Thread.sleep(400);
        check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Large root text does not create horizontal overflow");
        h.js("document.documentElement.style.fontSize='';LifeNative=window.__timeNative");
        runOnMainSync(()->h.web.getSettings().setTextZoom(150));Thread.sleep(700);
        check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"WebView 150 percent text zoom stays within screen");h.js("document.querySelector('.track-time').scrollIntoView({block:'start'})");h.screenshot("track-time-large-text.png");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
    }
    @Override public void onStart(){Bundle out=new Bundle();int code=Activity.RESULT_CANCELED;String previous=getTargetContext().getSharedPreferences("secure",0).getString("settings",null);try{
        // Avoid any background model request while exercising unrelated local tracking UI.
        getTargetContext().getSharedPreferences("secure",0).edit().remove("settings").commit();
        h=new MainHost(this);h.open();h.js("AutoWiki.pause();AutoWiki.kick=()=>{}");seed();nativeChecks();uiChecks();
        if(landscape)check(h.js("innerWidth>innerHeight").equals("true"),"Actual Android landscape viewport verified");
        if(night){check((getTargetContext().getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES,"Android night mode active");check(h.js("getComputedStyle(document.querySelector('.track-time')).backgroundColor==='rgb(255, 255, 255)'").equals("true"),"Existing light app theme remains readable in system night mode");}
        try(TrackStore db=new TrackStore(getTargetContext())){check(db.diagnostics(date).getJSONArray("rawPoints").toString().equals(raw),"All interactions leave raw positions unchanged");check(db.gpx(date).equals(gpx)&&db.gpx(date,true).equals(original),"Processed and raw GPX exactly unchanged");db.deleteDay(date);}
        JSONObject after=(JSONObject)h.call("init",new JSONObject());for(String key:new String[]{"records","messages","wikiPages","settings"})check(after.get(key).toString().equals(init.get(key).toString()),"Time lookup does not mutate "+key);
        out.putString("stream","PASS "+passed+" time lookup checks; synthetic local coordinates only\n");code=Activity.RESULT_OK;
    }catch(Throwable e){try{h.screenshot("track-time-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL after "+passed+": "+e+"\n");}
    finally{android.content.SharedPreferences.Editor e=getTargetContext().getSharedPreferences("secure",0).edit();if(previous==null)e.remove("settings");else e.putString("settings",previous);e.commit();}finish(code,out);}
}
