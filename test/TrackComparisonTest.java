package com.insight.quantlife.tests;
import android.app.*;import android.os.Bundle;import android.location.Location;import com.insight.quantlife.*;import org.json.*;import java.io.*;
import javax.xml.parsers.DocumentBuilderFactory;import org.w3c.dom.*;

/** Disposable emulator ONLY: seed 1.4.5, install 1.4.6 -r, verify sole D offline. */
public class TrackComparisonTest extends Instrumentation {
    String phase;int passed;MainHost h;final String date="2026-10-19",gapDate="2026-10-20";
    @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"verify":b.getString("phase","verify");start();}
    void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception(label);passed++;Bundle e=new Bundle();e.putString("stream","PASS "+label+"\n");sendStatus(0,e);}
    void watch()throws Exception{h.js("window.__day=null;window.__dayRequests=[];window.__dayNative=LifeNative;LifeNative={call:function(action,payload,id){window.__dayRequests.push(action);window.__dayNative.call(action,payload,id)}};window.__dayReply=window.nativeReply;window.nativeReply=function(r){if(r.data?.filterVersion===5)window.__day=r.data;window.__dayReply(r)}");}
    void selectDate(String d,int count)throws Exception{h.js("document.querySelector('#track-date').value="+JSONObject.quote(d)+";document.querySelector('#track-date').dispatchEvent(new Event('change'))");for(int i=0;i<100;i++){if(h.js("window.__day?.date==="+JSONObject.quote(d)+" && window.__day?.points.length==="+count).equals("true"))return;Thread.sleep(100);}throw new Exception("Day UI not refreshed: "+d);}
    void reload()throws Exception{h.js("window.__reloadMarker=true;location.reload()");for(int i=0;i<100;i++){if(h.js("typeof window.__reloadMarker").equals("\"undefined\""))break;Thread.sleep(100);}check(h.waitText("今天的投入"),"Application reloads");watch();h.js("document.querySelector('[data-page=tracks]').click()");check(h.waitText("65米＋误差加权平滑"),"D remains only display after reload");selectDate(date,136);}
    @Override public void onStart(){Bundle result=new Bundle();try(TrackStore db=new TrackStore(getTargetContext())){
        h=new MainHost(this);h.open();
        if(phase.equals("seed")){
            check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.4.5"),"Seed signed previous 1.4.5");
            db.deleteDay(date);db.deleteDay(gapDate);long base=TrackStore.dayStart(date)+12*3600000;
            for(int i=0;i<136;i++){Location p=BeforeGapFixTest.p(base+i*5000,i*7,1.4f);if(i==40||i==41)p.setAccuracy(45);JSONObject t=new JSONObject().put("satellitesUsed",i<3||i==130||i==131||i==135?10:0).put("gnssAgeMs",0);if(t.getInt("satellitesUsed")>0)t.put("meanCn0",35);db.add(p,"d-fixture",t);}
            long g=TrackStore.dayStart(gapDate)+12*3600000;db.add(BeforeGapFixTest.p(g,0,1.4f),"gap");db.add(BeforeGapFixTest.p(g+5000,7,1.4f),"gap");db.add(BeforeGapFixTest.p(g+100000,100,1.4f),"gap");db.add(BeforeGapFixTest.p(g+105000,107,1.4f),"gap");db.add(BeforeGapFixTest.p(g+110000,120,1.4f),"resumed");
            h.js("localStorage.setItem('zhishi-track-preferred-scheme','raw100')");
            Thread.sleep(1800);
            check(h.js("localStorage.getItem('zhishi-track-preferred-scheme')").equals("\"raw100\""),"Synthetic old preference seeded before upgrade");
            TrackingService.prefs(getTargetContext()).edit().putString("message","GPS原始点已保留，可切换轨迹方案对比").commit();
            JSONObject snapshot=new JSONObject().put("init",h.call("init",new JSONObject())).put("raw",db.diagnostics(date).getJSONArray("rawPoints")).put("oldD",h.call("trackCompare",new JSONObject().put("date",date).put("scheme","weighted65")));
            try(FileOutputStream f=getTargetContext().openFileOutput("sole-d-upgrade-fixture.json",0)){f.write(snapshot.toString().getBytes("UTF-8"));}
            result.putString("stream","PASS 1.4.5 upgrade fixture saved; synthetic points only\n");finish(Activity.RESULT_OK,result);return;
        }
        check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.4.6"),"New version installed");
        StringBuilder text=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(getTargetContext().openFileInput("sole-d-upgrade-fixture.json"),"UTF-8"))){String line;while((line=reader.readLine())!=null)text.append(line);}
        JSONObject snapshot=new JSONObject(text.toString()),init=(JSONObject)h.call("init",new JSONObject());
        for(String key:new String[]{"records","wikiPages","messages","settings"})check(init.get(key).toString().equals(snapshot.getJSONObject("init").get(key).toString()),"Upgrade preserves "+key);
        check(db.getReadableDatabase().getVersion()==3,"No track schema migration");
        JSONObject day=(JSONObject)h.call("trackDay",new JSONObject().put("date",date));JSONArray points=day.getJSONArray("points"),expected=snapshot.getJSONObject("oldD").getJSONArray("points");
        check(day.getInt("filterVersion")==5&&day.getJSONObject("filter").getString("id").equals("weighted65"),"Native default is sole D");
        check(points.length()==136&&day.getInt("totalPoints")==136,"136 original samples shown by D");
        boolean same=points.length()==expected.length();for(int i=0;i<points.length()&&same;i++){JSONObject p=points.getJSONObject(i),q=expected.getJSONObject(i);same=p.getLong("id")==q.getLong("id")&&p.getLong("time")==q.getLong("time")&&p.getDouble("lat")==q.getDouble("lat")&&p.getDouble("lon")==q.getDouble("lon")&&p.getBoolean("breakBefore")==q.getBoolean("breakBefore");}
        check(same,"Every coordinate/time/break matches previous D exactly");
        check(day.getJSONObject("filter").getLong("estimatedMeters")==snapshot.getJSONObject("oldD").getJSONObject("activeScheme").getLong("estimatedMeters"),"D reference distance unchanged");
        check(!day.has("schemes")&&!day.has("activeScheme")&&!day.has("comparisonOnly"),"Native result contains no alternative scheme metadata");
        check(((JSONObject)h.call("trackDay",new JSONObject().put("date",date).put("scheme","strict"))).getJSONArray("points").length()==136,"Old scheme payload cannot select A");
        boolean rejected=false;try{h.call("trackCompare",new JSONObject().put("date",date).put("scheme","strict"));}catch(Exception e){rejected=true;}check(rejected,"Old comparison bridge removed");
        JSONObject diag=db.diagnostics(date);check(diag.getString("appVersion").equals("1.4.6"),"Diagnostic version actual");
        check(diag.getJSONObject("filtered").getJSONArray("points").toString().equals(points.toString()),"Diagnostic filtered output matches display");
        check(diag.getJSONArray("rawPoints").toString().equals(snapshot.getJSONArray("raw").toString()),"Raw points untouched by upgrade");
        String gpx=db.gpx(date);NodeList nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(gpx.getBytes("UTF-8"))).getElementsByTagName("trkpt");
        check(nodes.getLength()==points.length(),"GPX has exactly the D point count");
        boolean gpxMatches=true;for(int i=0;i<nodes.getLength();i++){Element p=(Element)nodes.item(i);JSONObject q=points.getJSONObject(i);if(Double.parseDouble(p.getAttribute("lat"))!=q.getDouble("lat")||Double.parseDouble(p.getAttribute("lon"))!=q.getDouble("lon"))gpxMatches=false;}
        check(gpxMatches,"Every GPX coordinate equals displayed D coordinates");
        check(Math.abs(Double.parseDouble(h.js("LifeTracks.summary("+points+").meters"))-day.getJSONObject("filter").getDouble("estimatedMeters"))<2,"Native and JS distance agree");
        JSONObject gaps=(JSONObject)h.call("trackDay",new JSONObject().put("date",gapDate));JSONArray gapPoints=gaps.getJSONArray("points");
        check(gapPoints.length()==5,"Gap endpoints remain recorded");
        check(h.js("LifeTracks.bridges("+gapPoints+").length").equals("2"),"All gaps have chronological dashed connections");
        check(Math.abs(Double.parseDouble(h.js("LifeTracks.summary("+gapPoints+").meters"))-14)<1,"Gap and pause distance excluded");
        check(db.gpx(gapDate).split("<trkseg>",-1).length-1==3,"GPX stays segmented and never exports inferred bridges");
        watch();h.js("window.__dStrokes=[];window.__dOldStroke=CanvasRenderingContext2D.prototype.stroke;CanvasRenderingContext2D.prototype.stroke=function(){if(this.canvas.id==='track-map')window.__dStrokes.push({color:this.strokeStyle,dash:this.getLineDash().join(',')});return window.__dOldStroke.apply(this,arguments)};document.querySelector('[data-page=tracks]').click()");
        check(h.waitText("65米＋误差加权平滑"),"Actual offline UI displays D");
        selectDate(date,136);
        check(h.js("document.querySelectorAll('#track-comparison,#track-scheme,[data-track-scheme],#track-prefer-scheme').length").equals("0"),"No comparison panel/select/cards/favourite button");
        check(h.js("document.body.innerText.includes('轨迹方案对比')").equals("false"),"No obsolete comparison wording");
        check(h.js("window.__dayRequests.includes('trackDay')&&!window.__dayRequests.includes('trackCompare')").equals("true"),"Actual UI calls only day bridge");
        check(h.js("window.__dStrokes.some(x=>x.color==='#b47719'&&!x.dash)").equals("true"),"Map paints D colour");
        check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"No horizontal overflow on phone screen");
        check(h.js("localStorage.getItem('zhishi-track-preferred-scheme')").equals("\"raw100\""),"Old F preference remains inert");
        h.js("document.querySelector('#track-refresh').click()");Thread.sleep(500);check(h.js("window.__day.filter.id").equals("\"weighted65\""),"Refresh keeps D");
        h.js("document.querySelector('#track-map').scrollIntoView({block:'center'})");h.screenshot("sole-D-map-1.4.6.png");
        selectDate(gapDate,5);
        check(h.js("window.__dStrokes.some(x=>x.color==='#8754a1'&&x.dash==='7,5')").equals("true"),"Actual map draws dashed gap connectors");
        h.js("document.querySelector('#track-date').value='2026-10-20';document.querySelector('#track-date').dispatchEvent(new Event('change'));document.querySelector('#track-date').value='2026-10-19';document.querySelector('#track-date').dispatchEvent(new Event('change'))");Thread.sleep(800);
        check(h.js("window.__day.date").equals("\"2026-10-19\""),"Rapid date changes cannot leave stale day on screen");
        for(String oldPreference:new String[]{"strict","zero_unknown","kalman100"}){h.js("localStorage.setItem('zhishi-track-preferred-scheme',"+JSONObject.quote(oldPreference)+")");reload();check(h.js("window.__day.filter.id").equals("\"weighted65\""),"Ignores old preference "+oldPreference);}
        h.js("localStorage.removeItem('zhishi-track-preferred-scheme')");reload();check(h.js("window.__day.filter.id").equals("\"weighted65\""),"No saved preference also defaults to D");
        check(db.diagnostics(date).getJSONArray("rawPoints").toString().equals(snapshot.getJSONArray("raw").toString()),"UI views do not mutate raw track DB");
        check(!((JSONObject)h.call("init",new JSONObject())).has("points"),"AI initialization still excludes locations");
        h.js("document.querySelector('[data-page=settings]').click()");check(h.waitText("GPX使用与地图相同"),"Export guidance matches D");
        h.js("document.querySelector('[data-action=user-guide]').click()");check(h.waitText("第一次使用"),"Offline guide opens");
        h.js("Array.from(document.querySelectorAll('.user-guide details')).find(d=>d.querySelector('summary').innerText.includes('轨迹处理')).open=true");
        check(h.waitText("地图、轨迹参考距离"),"Offline guide describes sole shared D pipeline");
        check(h.js("document.querySelector('.user-guide').innerText.includes('A 原保守方案')").equals("false"),"No alternative scheme list in guide");
        result.putString("stream","PASS: "+passed+" Android sole-D/upgrade checks; synthetic data only\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
