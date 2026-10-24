package com.insight.quantlife.tests;
import android.app.*;import android.os.Bundle;import android.location.Location;import com.insight.quantlife.*;import org.json.*;

/** Local disposable emulator fixture. No real locations, external services or model requests. */
public class TrackReliabilityTest extends Instrumentation {
    MainHost h;int passed;String date="2026-12-23",session="reliability-fixture";long base;
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    void check(boolean ok,String label)throws Exception{if(!ok)throw new Exception(label);passed++;Bundle b=new Bundle();b.putString("stream","PASS "+label+"\n");sendStatus(0,b);}
    Location fix(long time,double meters,String provider,float speed){Location l=BeforeGapFixTest.p(time,meters,speed);l.setProvider(provider);return l;}
    JSONObject evidence(boolean moving)throws Exception{return new JSONObject().put("eligible",true).put("motionRms",moving?.5:.02).put("motionAgeMs",20).put("fixAgeMs",15).put("gpsAgeMs",0);}
    @Override public void onStart(){Bundle result=new Bundle();String settings=getTargetContext().getSharedPreferences("secure",0).getString("settings",null);int code=Activity.RESULT_CANCELED;
        try{getTargetContext().getSharedPreferences("secure",0).edit().remove("settings").commit();h=new MainHost(this);h.open();h.js("AutoWiki.pause();AutoWiki.kick=()=>{}");
            check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.13.1"),"Signed 1.13.1 cover upgrade is installed");
            h.call("trackConfig",new JSONObject().put("filterMode","weighted65").put("mapMode","offline").put("mapConsent",false));base=TrackStore.dayStart(date)+9*3600000;
            try(TrackStore db=new TrackStore(getTargetContext())){
                JSONArray prior=db.diagnostics(date).getJSONArray("rawPoints");for(int i=0;i<prior.length();i++)if(!prior.getJSONObject(i).getString("session").equals(session))throw new Exception("Refuse to overwrite unrelated fixture date");if(prior.length()>0)db.deleteDay(date);
                for(int i=0;i<=60;i++)db.add(fix(base+i*2000,Math.sin(i*.5)*3,"gps",0),session,evidence(false));
                for(int i=1;i<=40;i++){db.add(fix(base+120000+i*5000,i*7,"gps",1.4f),session,evidence(true));db.add(fix(base+120000+i*5000+1000,300,"fused",0),session,evidence(false));db.add(fix(base+120000+i*5000+2000,310,"fused",0),session,evidence(false));}
                db.add(fix(base+500000,500,"gps",1.4f),session,evidence(true));db.add(fix(base+505000,507,"gps",1.4f),session,evidence(true));
                String raw=db.diagnostics(date).getJSONArray("rawPoints").toString(),original=db.gpx(date,true);JSONObject day=db.day(date,TrackVariants.ID,false);
                check(day.getInt("filterVersion")==11&&db.getReadableDatabase().getVersion()==3,"Recompute uses revision 11 without schema replacement");
                check(day.getInt("totalPoints")==183&&day.getInt("processedPoints")==103,"All raw callbacks retained; GPS observations remain present");
                JSONArray ps=day.getJSONArray("points");int stops=0,fused=0;for(int i=0;i<ps.length();i++){JSONObject p=ps.getJSONObject(i);if(p.getString("motionState").equals("stationary_estimate"))stops++;if(!p.getString("provider").equals("gps"))fused++;}
                check(stops>40&&fused==0,"Quiet GPS drift is estimated as stopped; Fused positions never shift D route");
                check(day.getLong("estimatedMeters")>250&&day.getLong("estimatedMeters")<310,"Walking preserved without adding stop drift or long outage to mileage");
                check(day.getInt("processedGaps")<=4,"Ignored source callbacks do not manufacture dozens of gaps");
                JSONObject diagnostic=db.diagnostics(date);JSONArray decisions=diagnostic.getJSONArray("processingDecisions");int ignored=0;for(int i=0;i<decisions.length();i++)if(decisions.getJSONObject(i).getString("status").equals("ignored_source"))ignored++;
                check(decisions.length()==183&&ignored==80,"Export explains each raw point separately from immutable raw payload");
                JSONObject moment=(JSONObject)h.call("trackTime",new JSONObject().put("date",date).put("mode","moment").put("at","09:01"));check(moment.getJSONObject("match").getString("motionState").equals("stationary_estimate"),"Time lookup labels stationary position as an estimate");
                check(h.js("LifeTracks.bridges("+ps+").every(x=>x.inferred===true&&x.b.time>x.a.time)").equals("true"),"Gaps stay chronological inferred connectors");
                check(h.js("LifeTracks.summary("+ps+").meters").equals(String.valueOf(day.getLong("estimatedMeters"))),"Java and real WebView agree on mileage");
                check(db.diagnostics(date).getJSONArray("rawPoints").toString().equals(raw)&&db.gpx(date,true).equals(original),"Queries and replay never rewrite raw telemetry or original GPX");
                h.js("document.querySelector('[data-page=tracks]').click()");for(int i=0;i<60&&!h.js("!!document.querySelector('#track-date')").equals("true");i++)Thread.sleep(100);
                h.js("document.querySelector('#track-date').value='"+date+"';document.querySelector('#track-date').dispatchEvent(new Event('change'))");
                for(int i=0;i<100&&!h.js("document.querySelector('#track-map')?.dataset.pointCount==='103'").equals("true");i++)Thread.sleep(100);
                check(h.js("document.querySelector('#track-map')?.dataset.pointCount==='103'").equals("true"),"Native processed day reaches the real track map");
                check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Phone viewport remains readable");
                db.deleteDay(date);
            }result.putString("stream","PASS "+passed+" native reliability checks\n");code=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("stream","FAIL after "+passed+": "+e+"\n");e.printStackTrace();}
        finally{android.content.SharedPreferences.Editor e=getTargetContext().getSharedPreferences("secure",0).edit();if(settings==null)e.remove("settings");else e.putString("settings",settings);e.commit();}finish(code,result);
    }
}
