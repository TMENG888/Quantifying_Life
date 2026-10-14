package com.insight.quantlife.tests;
import android.app.*;import android.os.Bundle;import android.location.Location;import android.graphics.*;import android.database.sqlite.SQLiteDatabase;import android.net.Uri;import com.insight.quantlife.*;import org.json.*;import java.io.*;import java.util.concurrent.*;

/** Disposable emulator only; synthetic raster/coordinates and no external network in repro. */
public class MapViewportTest extends Instrumentation {
    String phase;int passed;MainHost h;final String date="2026-10-23";
    @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"repro":b.getString("phase","repro");start();}
    void check(boolean ok,String name)throws Exception{if(!ok)throw new Exception(name);passed++;Bundle e=new Bundle();e.putString("stream","PASS "+name+"\n");sendStatus(0,e);}
    void event(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
    int x(int z){return (int)Math.floor((121.47+180)/360*(1<<z));}
    int y(int z){double s=Math.sin(31.23*Math.PI/180);return (int)Math.floor((.5-Math.log((1+s)/(1-s))/(4*Math.PI))*(1<<z));}
    void fixture()throws Exception{
        TrackingService.prefs(getTargetContext()).edit().putString("mapRevision","viewport-synthetic-147").apply();
        File source=new File(getTargetContext().getFilesDir(),"viewport-fixture.mbtiles");
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(source,null)){
            db.execSQL("DROP TABLE IF EXISTS metadata");db.execSQL("DROP TABLE IF EXISTS tiles");
            db.execSQL("CREATE TABLE metadata(name TEXT,value TEXT)");db.execSQL("CREATE TABLE tiles(zoom_level INTEGER,tile_column INTEGER,tile_row INTEGER,tile_data BLOB)");
            db.execSQL("INSERT INTO metadata VALUES('name','Synthetic map z14'),('format','png'),('scheme','tms'),('attribution','Synthetic QA only')");
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){
                Bitmap bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.rgb(194+dx*10,210+dy*10,170));Paint paint=new Paint();paint.setColor(Color.rgb(70,105,130));paint.setStrokeWidth(8);for(int i=0;i<256;i+=64){canvas.drawLine(i,0,i,256,paint);canvas.drawLine(0,i,256,i,paint);}paint.setTextSize(18);canvas.drawText("SYNTHETIC MAP z14",16,100,paint);
                ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();db.execSQL("INSERT INTO tiles VALUES(?,?,?,?)",new Object[]{14,x(14)+dx,(1<<14)-1-y(14)-dy,out.toByteArray()});
            }
        }
        OfflineMap.importFile(getTargetContext(),Uri.fromFile(source));h.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));
        try(TrackStore db=new TrackStore(getTargetContext())){db.deleteDay(date);long base=TrackStore.dayStart(date)+12*3600000;for(int i=0;i<=30;i++){Location p=BeforeGapFixTest.p(base+i*5000,i*7,1.4f);db.add(p,"map-fixture");}
            JSONObject snapshot=new JSONObject().put("init",h.call("init",new JSONObject())).put("raw",db.diagnostics(date).getJSONArray("rawPoints")).put("gpx",db.gpx(date));try(FileOutputStream f=getTargetContext().openFileOutput("map-upgrade-fixture.json",0)){f.write(snapshot.toString().getBytes("UTF-8"));}}
    }
    void openMap()throws Exception{h.js("document.querySelector('[data-page=tracks]').click()");check(h.waitText("每天走过的地方"),"Track page opens");h.js("document.querySelector('#track-date').value='2026-10-23';document.querySelector('#track-date').dispatchEvent(new Event('change'))");Thread.sleep(1200);}
    @Override public void onStart(){Bundle out=new Bundle();try{
        h=new MainHost(this);h.open();
        if(phase.equals("live")){
            try {h.call("trackConfig",new JSONObject().put("mapMode","online").put("mapConsent",true));JSONObject tile=(JSONObject)h.call("trackTile",new JSONObject().put("z",0).put("x",0).put("y",0).put("mode","online"));check(tile.getString("data").startsWith("data:image/png;base64,"),"LIVE public OSM world tile loads through real native network path");out.putString("stream","PASS single public z0 world tile, no personal coordinates or keys\n");}
            catch(Exception e){Throwable reason=e instanceof java.lang.reflect.InvocationTargetException?((java.lang.reflect.InvocationTargetException)e).getTargetException():e;out.putString("stream","LIMIT real map network unavailable: "+reason.getMessage()+"; offline/controlled tests are separate\n");}
            finally{h.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));}
            finish(Activity.RESULT_OK,out);return;
        }
        if(phase.equals("repro")||phase.equals("seed")){
            check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.4.6"),"Original 1.4.6 installed");fixture();
            if(phase.equals("seed")){out.putString("stream","PASS upgrade seed, synthetic map/track only\n");finish(Activity.RESULT_OK,out);return;}
            check(OfflineMap.tile(getTargetContext(),14,x(14),y(14))!=null,"Coarse local tile exists");
            boolean highMissing=false;try{h.call("trackTile",new JSONObject().put("z",18).put("x",x(18)).put("y",y(18)));}catch(Exception e){highMissing=true;}
            check(highMissing,"REPRO native maximum zoom ignores existing parent tile");openMap();check(h.waitText("本地路线网格"),"REPRO actual canvas blank at zoom without exact tiles");
            h.js("document.querySelector('#track-map').scrollIntoView({block:'center'})");h.screenshot("map-before-1.4.6.png");
            CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);Thread hold=new Thread(()->{synchronized(MapTiles.class){locked.countDown();try{release.await(40,TimeUnit.SECONDS);}catch(Exception ignored){}}});hold.start();locked.await();
            try{
                h.js("window.__queueReplies=[];window.__queueOldReply=window.nativeReply;window.nativeReply=function(r){window.__queueReplies.push(r.id);window.__queueOldReply(r)};window.__queueNative=LifeNative;LifeNative={call:function(action,payload,id){if(action==='trackTile'&&!id.startsWith('blocked-map')){nativeReply({id,error:'Synthetic tile blocked; no networking'});return;}window.__queueNative.call(action,payload,id)}};for(let i=0;i<3;i++)LifeNative.call('trackTile',JSON.stringify({z:18,x:"+x(18)+",y:"+y(18)+"}),'blocked-map-'+i)");Thread.sleep(300);
                h.js("document.querySelector('#track-map-mode').value='online';document.querySelector('#track-map-mode').dispatchEvent(new Event('change'))");check(h.waitText("加载在线 OSM 地图"),"Actual mode-switch consent appears");h.js("document.querySelector('#confirm-ok').click()");
                boolean timeout=false;for(int i=0;i<34;i++){if(h.js("document.querySelector('#toast').textContent").contains("操作超时")){timeout=true;break;}Thread.sleep(1000);}
                check(timeout,"REPRO slow tile lock saturates shared workers and real mode switch times out");
            }finally{release.countDown();hold.join(2000);Thread.sleep(1000);h.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));}
            out.putString("stream","PASS reproduced both defects using synthetic maps and controlled slow requests; no external tile request\n");finish(Activity.RESULT_OK,out);return;
        }
        verify();out.putString("stream","PASS: "+passed+" Android map/upgrade checks; synthetic data only\n");finish(Activity.RESULT_OK,out);
    }catch(Throwable e){out.putString("stream","FAIL after "+passed+": "+e+"\n");finish(Activity.RESULT_CANCELED,out);}}

    void until(String expression,String name)throws Exception{for(int i=0;i<100;i++){if(h.js(expression).equals("true"))return;Thread.sleep(100);}throw new Exception(name);}
    void switchMode(String mode,boolean approve)throws Exception{
        h.js("document.querySelector('#track-map-mode').value="+JSONObject.quote(mode)+";document.querySelector('#track-map-mode').dispatchEvent(new Event('change'))");
        if(mode.equals("online")){until("document.querySelector('#confirm-dialog').open","Consent did not open");check(h.js("document.querySelector('#track-map-mode').disabled").equals("true"),"Selector locked only while switch pending");h.js("document.querySelector("+JSONObject.quote(approve?"#confirm-ok":"#confirm-cancel")+").click()");}
        until("!document.querySelector('#track-map-mode').disabled && document.querySelector('#track-map-mode').value==="+JSONObject.quote(approve?mode:"offline"),"Mode switch did not finish");
    }
    static class DummyConnection extends java.net.HttpURLConnection {
        boolean stopped;DummyConnection()throws Exception{super(new java.net.URL("https://example.invalid/"));}public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){stopped=true;}
    }
    void verify()throws Exception{
        check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionName.equals("1.4.7"),"New 1.4.7 installed");
        StringBuilder text=new StringBuilder();try(BufferedReader reader=new BufferedReader(new InputStreamReader(getTargetContext().openFileInput("map-upgrade-fixture.json"),"UTF-8"))){String line;while((line=reader.readLine())!=null)text.append(line);}JSONObject snapshot=new JSONObject(text.toString());
        JSONObject init=(JSONObject)h.call("init",new JSONObject());for(String key:new String[]{"records","wikiPages","messages","settings"})check(init.get(key).toString().equals(snapshot.getJSONObject("init").get(key).toString()),"Upgrade retains "+key);
        try(TrackStore db=new TrackStore(getTargetContext())){check(db.diagnostics(date).getJSONArray("rawPoints").toString().equals(snapshot.getJSONArray("raw").toString()),"Raw positions preserved");check(db.gpx(date).equals(snapshot.getString("gpx")),"D coordinates and GPX exactly preserved");check(db.day(date).getJSONObject("filter").getString("id").equals("weighted65"),"Sole D unchanged");}
        h.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));
        JSONObject parent=(JSONObject)h.call("trackTile",new JSONObject().put("z",18).put("x",x(18)).put("y",y(18)).put("mode","offline"));
        check(parent.getInt("sourceZ")==14&&parent.getInt("sourceX")==x(14)&&parent.getInt("sourceY")==y(14),"Native maximum zoom finds correct parent tile");
        check(parent.getString("source").equals("package"),"Offline raster package used without consent");
        File cacheDir=new File(getTargetContext().getCacheDir(),"map-tiles-osm-viewport-synthetic-147");cacheDir.mkdirs();byte[] png=android.util.Base64.decode(parent.getString("data").substring(parent.getString("data").indexOf(',')+1),android.util.Base64.DEFAULT);
        File cachedParent=new File(cacheDir,"12-2048-2048.png");try(FileOutputStream f=new FileOutputStream(cachedParent)){f.write(png);}
        JSONObject cacheOnly=(JSONObject)h.call("trackTile",new JSONObject().put("z",18).put("x",131072).put("y",131072).put("mode","offline"));
        check(cacheOnly.getInt("sourceZ")==12&&cacheOnly.getString("source").equals("cache"),"Cache-only area uses correct cached parent even outside imported map");
        java.lang.reflect.Field cooldown=MapTiles.class.getDeclaredField("retryAfter");cooldown.setAccessible(true);long oldCooldown=cooldown.getLong(null);
        try{cooldown.setLong(null,System.currentTimeMillis()+60000);h.call("trackConfig",new JSONObject().put("mapMode","online").put("mapConsent",true));JSONObject onlineFallback=(JSONObject)h.call("trackTile",new JSONObject().put("z",18).put("x",131072).put("y",131072).put("mode","online"));check(onlineFallback.getInt("sourceZ")==12&&onlineFallback.has("warning"),"Unavailable online service falls back to cached coarse map without retry storm");}
        finally{cooldown.setLong(null,oldCooldown);h.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));}
        boolean missing=false;try{h.call("trackTile",new JSONObject().put("z",18).put("x",0).put("y",0).put("mode","offline"));}catch(Exception e){missing=true;}check(missing,"Uncovered area remains honest missing map, not invented imagery");
        boolean consent=false;try{h.call("trackConfig",new JSONObject().put("mapMode","online").put("mapConsent",false));}catch(Exception e){consent=true;}check(consent,"Online still requires consent");
        long ticket=System.currentTimeMillis()*1000L;h.call("trackMapView",new JSONObject().put("view",ticket).put("mode","offline"));
        java.lang.reflect.Field field=MapTiles.class.getDeclaredField("active");field.setAccessible(true);java.util.concurrent.ConcurrentHashMap connections=(java.util.concurrent.ConcurrentHashMap)field.get(null);DummyConnection dummy=new DummyConnection();connections.put(dummy,ticket);
        h.call("trackMapView",new JSONObject().put("view",ticket+1).put("mode","offline"));check(dummy.stopped&&connections.isEmpty(),"New view cancels old connection registry");
        boolean stale=false;try{h.call("trackTile",new JSONObject().put("z",18).put("x",x(18)).put("y",y(18)).put("mode","offline").put("view",ticket));}catch(Exception e){stale=true;}check(stale,"Stale view request rejected before lookup/network");
        DummyConnection revoke=new DummyConnection();connections.put(revoke,ticket+1);h.call("trackConfig",new JSONObject().put("mapMode","offline").put("mapConsent",false));check(revoke.stopped&&connections.isEmpty(),"Mode selection cancels old connection registry");
        h.js("window.__tileRequests=[];window.__tileMock=false;window.__tileDelay=0;window.__tileFailOnce=false;window.__tileFailed=false;window.__tileNative=LifeNative;window.__fixturePng="+JSONObject.quote(parent.getString("data"))+";LifeNative={call:function(action,payload,id){const p=JSON.parse(payload);if(action==='trackTile'){window.__tileRequests.push(p);if(p.mode==='online'&&window.__tileMock){const fail=window.__tileFailOnce&&!window.__tileFailed;window.__tileFailed=window.__tileFailed||fail;setTimeout(()=>nativeReply(fail?{id,error:'Synthetic one-tile failure'}:{id,data:{data:window.__fixturePng,sourceZ:p.z,sourceX:p.x,sourceY:p.y,source:'synthetic-online'}}),window.__tileDelay);return;}}window.__tileNative.call(action,payload,id)}};window.__tileCrops=[];window.__tileDraw=CanvasRenderingContext2D.prototype.drawImage;CanvasRenderingContext2D.prototype.drawImage=function(...args){if(this.canvas.id==='track-map')window.__tileCrops.push({args:args.length,sw:args[3],mode:this.canvas.dataset.mapMode,z:this.canvas.dataset.zoom});return window.__tileDraw.apply(this,args)}");
        openMap();check(h.waitText("低清放大"),"Actual offline canvas uses parent tiles and labels lower detail");
        check(h.js("window.__tileCrops.some(x=>x.args===9&&x.sw<256)").equals("true"),"Actual canvas draws cropped parent rather than wrong whole tile");
        h.js("for(let i=0;i<5;i++)document.querySelector('#track-zoom-in').click()");until("document.querySelector('#track-map').dataset.zoom==='18'","Maximum zoom not reached");
        until("window.__tileCrops.some(x=>x.z==='18'&&x.sw===16)","Maximum-zoom parent crop not painted");check(h.waitText("低清放大"),"Maximum zoom keeps existing basemap with coarse notice");
        check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Map controls fit phone width");h.js("document.querySelector('#track-map').scrollIntoView({block:'center'})");h.screenshot("map-max-1.4.7.png");
        h.js("window.__tileMock=true");switchMode("online",false);check(TrackingService.prefs(getTargetContext()).getString("mapMode","").equals("offline"),"Cancelled consent preserves offline state");
        for(int i=0;i<5;i++){switchMode("online",true);check(TrackingService.prefs(getTargetContext()).getString("mapMode","").equals("online"),"Online switch cycle "+(i+1));switchMode("offline",true);check(TrackingService.prefs(getTargetContext()).getString("mapMode","").equals("offline"),"Offline switch cycle "+(i+1));}
        // Occupy BOTH dedicated map workers, then switch mode using real native controls.
        h.js("window.__tileMock=false");java.lang.reflect.Field workersField=h.activity.getClass().getDeclaredField("mapTasks");workersField.setAccessible(true);ExecutorService workers=(ExecutorService)workersField.get(h.activity);
        CountDownLatch busy=new CountDownLatch(2),release=new CountDownLatch(1);for(int i=0;i<2;i++)workers.execute(()->{busy.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(Exception ignored){}});check(busy.await(3,TimeUnit.SECONDS),"Both slow map workers occupied");
        try{long start=System.currentTimeMillis();switchMode("online",true);check(System.currentTimeMillis()-start<2500,"Mode switch responsive despite all map workers busy");switchMode("offline",true);check(TrackingService.prefs(getTargetContext()).getString("mapMode","").equals("offline"),"Can switch again while old online tiles still pending");}finally{release.countDown();}
        check(h.waitText("低清放大"),"Offline map returns after slow-work cancellation");
        h.js("window.__tileMock=true;window.__tileDelay=1200;window.__tileRequests=[];TrackUI.mapImported()");switchMode("online",true);Thread.sleep(150);switchMode("offline",true);Thread.sleep(1800);
        check(h.js("document.querySelector('#track-map').dataset.mapMode==='offline' && document.querySelector('#track-map-mode').value==='offline'").equals("true"),"Late online responses cannot overwrite offline canvas/mode");
        check(!h.js("document.querySelector('#track-map-message').textContent").contains("在线 OSM"),"No stale online status after switch");
        h.js("window.__tileDelay=0;window.__tileFailOnce=true;window.__tileFailed=false;window.__tileRequests=[];TrackUI.mapImported()");switchMode("online",true);
        until("window.__tileFailed && window.__tileCrops.some(x=>x.mode==='online')","Remaining tiles not painted after first failure");Thread.sleep(700);
        check(h.js("window.__tileRequests.filter(p=>p.mode==='online').length>1").equals("true"),"First failed online tile does not abort rest of viewport");
        h.js("window.__tileFailOnce=false;document.querySelector('#track-refresh').click()");until("!document.querySelector('#track-map-message').textContent.includes('暂无底图')&&!document.querySelector('#track-map-message').textContent.includes('加载中')","Failed tile not recovered by refresh");check(h.waitText("在线 OSM 底图"),"Refresh retries missing tile and restores coverage");
        switchMode("offline",true);
        boolean oldMode=false;try{h.call("trackTile",new JSONObject().put("z",0).put("x",0).put("y",0).put("mode","online"));}catch(Exception e){oldMode=true;}check(oldMode,"Explicit stale online mode cannot fetch after revocation");
        try(TrackStore db=new TrackStore(getTargetContext())){check(db.gpx(date).equals(snapshot.getString("gpx")),"Map interactions never alter D GPX");check(db.diagnostics(date).getJSONArray("rawPoints").toString().equals(snapshot.getJSONArray("raw").toString()),"Map interactions never change raw data");}
        check(!((JSONObject)h.call("init",new JSONObject())).has("points"),"Locations still excluded from AI init");
        h.js("document.querySelector('[data-page=settings]').click();document.querySelector('[data-action=user-guide]').click()");check(h.waitText("第一次使用"),"Offline guide opens");h.js("Array.from(document.querySelectorAll('.user-guide details')).find(d=>d.querySelector('summary').innerText.includes('全天轨迹')).open=true");check(h.waitText("低清放大"),"Help explains coarse map and privacy limits");
    }
}
