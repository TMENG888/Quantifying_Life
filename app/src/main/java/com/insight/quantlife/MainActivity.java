package com.insight.quantlife;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;

public class MainActivity extends Activity {
    private WebView web;
    private Store store;
    private TrackStore tracks;
    private boolean pendingTrackStart;
    private String exportLabel="备份已保存（不包含 API Key；轨迹需单独导出）";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService tasks = Executors.newFixedThreadPool(3);
    private byte[] exportBytes;
    private String pickKind = "attachment";
    private boolean loaded;
    private Intent pendingShare;
    private static final int PICK = 10, EXPORT = 11, PICK_MAP=12;
    private static final int FILE_LIMIT = 12 * 1024 * 1024;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Allow user-initiated system screenshots; never capture or upload the screen ourselves.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new Store();
        tracks = new TrackStore(this);
        getWindow().setStatusBarColor(0xfff6f5f0);
        getWindow().setNavigationBarColor(0xfff6f5f0);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        FrameLayout frame = new FrameLayout(this);
        web = new WebView(this);
        frame.addView(web, new FrameLayout.LayoutParams(-1,-1));
        frame.setOnApplyWindowInsetsListener((v,insets)-> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(frame);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true); ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false); ws.setAllowFileAccessFromFileURLs(false); ws.setAllowUniversalAccessFromFileURLs(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "LifeNative");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) { return !"appassets.androidplatform.net".equals(req.getUrl().getHost()); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                Uri uri=req.getUrl();
                if(!"appassets.androidplatform.net".equals(uri.getHost()))return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new java.io.ByteArrayInputStream(new byte[0]));
                String path=uri.getPath();if(path==null||!path.startsWith("/assets/")||path.contains(".."))return new WebResourceResponse("text/plain","UTF-8",404,"Not found",null,new java.io.ByteArrayInputStream(new byte[0]));
                path=path.substring(8);String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".html")?"text/html":"text/plain";
                try{return new WebResourceResponse(mime,"UTF-8",getAssets().open(path));}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",404,"Not found",null,new java.io.ByteArrayInputStream(new byte[0]));}
            }
            @Override public void onPageFinished(WebView v, String url) { loaded=true; if(pendingShare!=null){handleShare(pendingShare);pendingShare=null;} }
        });
        if(Intent.ACTION_SEND.equals(getIntent().getAction())) pendingShare=getIntent();
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if(loaded)handleShare(intent);else pendingShare=intent; }
    @Override public void onBackPressed() { web.evaluateJavascript("window.appBack && window.appBack()",value->{if(!"true".equals(value))super.onBackPressed();}); }
    @Override protected void onDestroy() { tasks.shutdownNow(); if(tracks!=null)tracks.close(); if(web!=null){web.removeJavascriptInterface("LifeNative");web.destroy();} super.onDestroy(); }

    private JSONObject trackingStatus()throws Exception{
        SharedPreferences p=TrackingService.prefs(this);JSONObject s=new JSONObject();
        s.put("enabled",p.getBoolean("enabled",false));s.put("running",TrackingService.running);
        s.put("precisePermission",TrackingService.precise(this));s.put("backgroundPermission",TrackingService.background(this));
        s.put("notificationPermission",((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()&&(android.os.Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED));
        s.put("locationEnabled",TrackingService.locationEnabled(this));
        s.put("resumeAfterReboot",p.getBoolean("resumeAfterReboot",false));s.put("mapConsent",p.getBoolean("mapConsent",false));s.put("lastPoint",p.getLong("lastPoint",0));
        s.put("mapProvider",p.getString("mapProvider","osm"));s.put("mapKeyConfigured",!privateSettings().optString("mapTileKey").isEmpty());
        s.put("mapMode",p.getString("mapMode","offline"));s.put("offlineMap",OfflineMap.info(this));
        s.put("rejected",p.getInt("rejected",0));s.put("message",p.getString("message","尚未开启定位记录"));
        s.put("batteryRestricted",!((android.os.PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()));return s;
    }
    private void startTrackingUI(){
        if(!TrackingService.precise(this)){pendingTrackStart=true;requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},240);return;}
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingTrackStart=true;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},241);return;}
        pendingTrackStart=false;try{startForegroundService(new Intent(this,TrackingService.class));}catch(Exception e){TrackingService.prefs(this).edit().putString("message","系统阻止启动，请在App前台重新开启").apply();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==240&&pendingTrackStart){if(TrackingService.precise(this))startTrackingUI();else {pendingTrackStart=false;TrackingService.prefs(this).edit().putString("message","未获得精确定位权限，未开始记录").apply();}}
        if(request==241&&pendingTrackStart){pendingTrackStart=false;try{startForegroundService(new Intent(this,TrackingService.class));}catch(Exception e){TrackingService.prefs(this).edit().putString("message","无法启动，请检查权限").apply();}}
        if(request==242){try{JSONObject notice=new JSONObject();notice.put("message",TrackingService.background(this)?"始终允许定位已开启；重启恢复仍受系统后台限制":"未授予始终允许定位，重启不能自动恢复；仍可在App前台开启记录");event("notice",notice);}catch(Exception ignored){}}
    }

    private void deliver(String id, Object data, String error) {
        main.post(()-> {try {JSONObject result=new JSONObject(); result.put("id",id);result.put("data",data);result.put("error",error==null?JSONObject.NULL:error); web.evaluateJavascript("window.nativeReply("+result.toString()+")",null);}catch(Exception ignored){}});
    }
    private void event(String name, Object data) { main.post(()->web.evaluateJavascript("window.nativeEvent("+JSONObject.quote(name)+","+data.toString()+")",null)); }
    private class Bridge {
        @JavascriptInterface public void call(String action,String input,String id) {
            tasks.execute(()->{try{deliver(id,execute(action,new JSONObject(input)),null);}catch(Exception e){deliver(id,null,cleanError(e));}});
        }
    }
    private String cleanError(Exception e) {
        String m=e.getMessage(); if(m==null)return "操作失败，请稍后重试";
        if(m.contains("Unable to resolve host"))return "无法连接模型服务，请检查网络和 API 地址";
        if(m.contains("timeout")||m.contains("timed out"))return "模型请求超时，请稍后重试";
        return m.length()>400?m.substring(0,400):m;
    }
    private Object execute(String action,JSONObject p) throws Exception {
        switch(action) {
            case "init": {
                JSONObject o=new JSONObject();o.put("records",store.records());o.put("messages",store.messages());o.put("wikiPages",store.wikiPages());o.put("settings",publicSettings());return o;
            }
            case "saveWiki": return store.saveWiki(p.getJSONObject("page"));
            case "trackDay": {JSONObject day=tracks.day(p.getString("date"));day.put("status",trackingStatus());day.put("dates",tracks.dates());return day;}
            case "trackStart": main.post(this::startTrackingUI);return true;
            case "trackStop": TrackingService.prefs(this).edit().putBoolean("enabled",false).putString("message","已暂停记录").apply();main.post(()->stopService(new Intent(this,TrackingService.class)));return true;
            case "trackConfig": {SharedPreferences.Editor edit=TrackingService.prefs(this).edit();if(p.has("resumeAfterReboot"))edit.putBoolean("resumeAfterReboot",p.getBoolean("resumeAfterReboot"));if(p.has("mapConsent"))edit.putBoolean("mapConsent",p.getBoolean("mapConsent"));if(p.has("mapMode")){String mode=p.getString("mapMode");if(!mode.matches("online|offline"))throw new Exception("地图模式无效");if(mode.equals("online")&&!p.optBoolean("mapConsent"))throw new Exception("在线地图需先取得同意");edit.putString("mapMode",mode).putString("mapProvider","osm");}edit.apply();return trackingStatus();}
            case "trackDeleteDay": return tracks.deleteDay(p.getString("date"));
            case "trackTile": return MapTiles.get(this,p.getInt("z"),p.getInt("x"),p.getInt("y"),privateSettings().optString("mapTileKey"),!TrackingService.prefs(this).getString("mapMode","offline").equals("online"));
            case "trackImportMap": main.post(()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK_MAP);});return true;
            case "trackSaveMap": {
                String provider=p.getString("provider");if(!provider.matches("osm|carto"))throw new Exception("地图厂商无效");String key=p.optString("key").trim();if(key.length()>500)throw new Exception("地图Key过长");
                JSONObject settings=privateSettings();if(p.optBoolean("clearKey"))settings.remove("mapTileKey");else if(!key.isEmpty())settings.put("mapTileKey",key);encryptSettings(settings);
                TrackingService.prefs(this).edit().putString("mapProvider",provider).putString("mapRevision",java.util.UUID.randomUUID().toString()).putBoolean("mapConsent",false).apply();return trackingStatus();
            }
            case "trackExport": {
                String date=p.getString("date");exportBytes=tracks.gpx(date).getBytes(StandardCharsets.UTF_8);exportLabel="轨迹GPX已保存，含精确位置，请妥善保管";
                main.post(()->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/gpx+xml");intent.putExtra(Intent.EXTRA_TITLE,"知时轨迹-"+date+".gpx");startActivityForResult(intent,EXPORT);});return true;
            }
            case "trackDiagnostics": {
                String date=p.getString("date");JSONObject diagnostic=tracks.diagnostics(date);diagnostic.put("device",new JSONObject().put("manufacturer",android.os.Build.MANUFACTURER).put("model",android.os.Build.MODEL).put("androidApi",android.os.Build.VERSION.SDK_INT));exportBytes=diagnostic.toString(2).getBytes(StandardCharsets.UTF_8);exportLabel="轨迹诊断已保存，包含原始精确位置，请妥善保管";
                main.post(()->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"知时轨迹诊断-"+date+".json");startActivityForResult(intent,EXPORT);});return true;
            }
            case "trackAppSettings": main.post(()->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));return true;
            case "trackBackgroundPermission": if(!TrackingService.precise(this))throw new Exception("请先开启记录并允许精确定位，再申请始终允许");if(android.os.Build.VERSION.SDK_INT>=29&&!TrackingService.background(this))main.post(()->requestPermissions(new String[]{android.Manifest.permission.ACCESS_BACKGROUND_LOCATION},242));return true;
            case "trackLocationSettings": main.post(()->startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)));return true;
            case "trackBatterySettings": main.post(()->{try{startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}catch(Exception e){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}});return true;
            case "saveRecord": return store.save(p.getJSONObject("record"),p.optBoolean("allowDuplicate"));
            case "deleteRecord": store.getWritableDatabase().delete("records","id=?",new String[]{p.getString("id")});return true;
            case "saveMessage": store.saveMessage(p.getJSONObject("message"));return true;
            case "clearMessages": store.getWritableDatabase().delete("messages",null,null);return true;
            case "getSettings": return publicSettings();
            case "saveLinkSettings": {
                JSONObject settings=privateSettings(),incoming=p.getJSONObject("settings"),previous=settings.optJSONObject("linkSearch");
                String provider=incoming.optString("provider",previous==null?"exa":previous.optString("provider","tavily"));if(!provider.matches("exa|tavily"))throw new Exception("搜索服务无效");
                String key=incoming.optString("apiKey").trim();if(key.isEmpty())key=previous==null?"":previous.optString("apiKey");if(incoming.optBoolean("clearKey"))key="";
                if(key.length()>512||key.matches("(?s).*[\\r\\n].*"))throw new Exception("搜索Key格式无效");
                settings.put("linkSearch",new JSONObject().put("provider",provider).put("enabled",incoming.optBoolean("enabled")).put("apiKey",key));encryptSettings(settings);return publicSettings();
            }
            case "readLinks": {
                JSONArray urls=p.getJSONArray("urls");if(urls.length()>3)throw new Exception("每次最多读取3个链接，请分批发送");
                LinkReader reader=new LinkReader();JSONArray sources=new JSONArray();JSONObject search=privateSettings().optJSONObject("linkSearch");
                for(int i=0;i<urls.length();i++)sources.put(reader.resolve(urls.getString(i),search));return sources;
            }
            case "saveSettings": {
                JSONObject old=privateSettings(), incoming=p.getJSONObject("settings");
                JSONObject profile=incoming.getJSONObject("profile");
                String kind=profile.getString("id");
                if(!kind.matches("[a-zA-Z0-9_-]{1,40}"))throw new Exception("配置名称无效");
                JSONObject profiles=old.optJSONObject("profiles");if(profiles==null)profiles=new JSONObject();
                JSONObject previous=profiles.optJSONObject(kind);
                String newKey=profile.optString("apiKey").trim();
                if(newKey.isEmpty())newKey=previous==null?"":previous.optString("apiKey");
                if(profile.optBoolean("clearKey"))newKey="";
                profile.put("apiKey",newKey);profile.remove("clearKey");
                String endpoint=profile.getString("baseUrl").trim().replaceAll("/+$", "");
                URL u=new URL(endpoint);if(!u.getProtocol().equals("https")&&!u.getProtocol().equals("http"))throw new Exception("API 地址必须使用 http 或 https");
                if(u.getUserInfo()!=null)throw new Exception("请勿在地址中填写密钥");
                if(u.getProtocol().equals("http")&&!profile.optBoolean("allowHttp"))throw new Exception("HTTP 地址需要开启可信内网连接");
                profile.put("baseUrl",endpoint);profiles.put(kind,profile);old.put("profiles",profiles);old.put("activeProfile",kind);
                old.put("dailyLearningGoal",incoming.optInt("dailyLearningGoal",60));
                encryptSettings(old);return publicSettings();
            }
            case "ask": return ask(p);
            case "pick": {
                pickKind=p.optString("kind","attachment");main.post(()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK);});return true;
            }
            case "export": {
                exportLabel="备份已保存（不包含 API Key；轨迹需在轨迹页单独导出）";
                JSONObject backup=new JSONObject();backup.put("format","quantlife");backup.put("version",1);backup.put("exportedAt",System.currentTimeMillis());backup.put("records",store.records());backup.put("messages",store.messages());backup.put("wikiPages",store.wikiPages());
                exportBytes=backup.toString(2).getBytes(StandardCharsets.UTF_8);
                main.post(()->{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"知时备份-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".json");startActivityForResult(intent,EXPORT);});return true;
            }
            case "import": {
                JSONArray list=p.getJSONArray("records");if(list.length()>50000)throw new Exception("一次最多导入 50000 条记录");
                SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();int inserted=0,skipped=0;
                try{for(int i=0;i<list.length();i++){JSONObject r=list.getJSONObject(i);if(store.has(r.getString("id"))||store.duplicate(r)){skipped++;continue;}store.save(r,false);inserted++;}db.setTransactionSuccessful();}finally{db.endTransaction();}
                JSONObject result=new JSONObject();result.put("inserted",inserted);result.put("skipped",skipped);result.put("records",store.records());return result;
            }
            case "openUrl": {
                Uri uri=Uri.parse(p.getString("url"));if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))throw new Exception("链接格式不正确");
                main.post(()->startActivity(new Intent(Intent.ACTION_VIEW,uri)));return true;
            }
            default: throw new Exception("未知操作");
        }
    }
    private class Store extends SQLiteOpenHelper {
        Store(){super(MainActivity.this,"quantlife.db",null,2);}
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY,type TEXT NOT NULL,date TEXT NOT NULL,title TEXT NOT NULL,platform TEXT NOT NULL,payload TEXT NOT NULL)");
            db.execSQL("CREATE INDEX record_date ON records(date)");
            db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY,created INTEGER NOT NULL,payload TEXT NOT NULL)");
            db.execSQL("CREATE TABLE wiki_pages (id TEXT PRIMARY KEY,payload TEXT NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int old,int next) {if(old<2&&next==2)db.execSQL("CREATE TABLE IF NOT EXISTS wiki_pages (id TEXT PRIMARY KEY,payload TEXT NOT NULL)");else throw new IllegalStateException("Unsupported schema migration");}
        synchronized JSONArray wikiPages()throws Exception{JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM wiki_pages ORDER BY rowid DESC",null)){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;}
        synchronized JSONObject saveWiki(JSONObject page)throws Exception{
            String id=page.getString("id"),topic=page.getString("topic");if(id.isEmpty()||id.length()>160||topic.trim().isEmpty()||topic.length()>100||page.getString("summary").length()>12000||page.toString().length()>500000)throw new Exception("Wiki 知识页格式无效");
            JSONArray ids=page.getJSONArray("sourceIds"),indexed=page.optJSONArray("indexSourceIds");if(indexed==null)indexed=ids;java.util.HashSet<String> valid=new java.util.HashSet<>();JSONObject versions=page.getJSONObject("sourceVersions");for(int i=0;i<indexed.length();i++){String source=indexed.getString(i);try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM records WHERE id=? AND type='learning'",new String[]{source})){if(!c.moveToFirst()||new JSONObject(c.getString(0)).optLong("updatedAt")!=versions.getLong(source))throw new Exception("Wiki 来源已修改或删除，请重新整理");valid.add(source);}}
            if(ids.length()==0)throw new Exception("Wiki 必须保留来源记录");java.util.HashSet<String> compiled=new java.util.HashSet<>();for(int i=0;i<ids.length();i++){if(!valid.contains(ids.getString(i)))throw new Exception("Wiki 来源无效");compiled.add(ids.getString(i));}JSONArray concepts=page.getJSONArray("concepts");for(int i=0;i<concepts.length();i++){JSONArray refs=concepts.getJSONObject(i).getJSONArray("sourceIds");if(refs.length()==0)throw new Exception("Wiki 概念缺少来源");for(int j=0;j<refs.length();j++)if(!compiled.contains(refs.getString(j)))throw new Exception("Wiki 概念引用不存在的记录");}
            android.content.ContentValues v=new android.content.ContentValues();v.put("id",id);v.put("payload",page.toString());if(getWritableDatabase().insertWithOnConflict("wiki_pages",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new Exception("Wiki 保存失败");return page;
        }
        synchronized JSONArray records() throws Exception {
            JSONArray list=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM records ORDER BY date DESC,rowid DESC",null)){while(c.moveToNext())list.put(new JSONObject(c.getString(0)));}return list;
        }
        synchronized boolean has(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM records WHERE id=?",new String[]{id})){return c.moveToFirst();}}
        synchronized boolean duplicate(JSONObject r)throws Exception {
            try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM records WHERE type=? AND date=? AND id<>?",new String[]{r.getString("type"),r.getString("date"),r.getString("id")})){
                while(c.moveToNext()){JSONObject other=new JSONObject(c.getString(0));if("work".equals(r.getString("type"))){if(r.optString("workStart").equals(other.optString("workStart")))return true;}else if(norm(r.optString("title")).equals(norm(other.optString("title")))&&norm(r.optString("platform")).equals(norm(other.optString("platform"))))return true;}
            }return false;
        }
        synchronized JSONObject save(JSONObject r,boolean allow) throws Exception {
            String id=r.getString("id"),type=r.getString("type"),date=r.getString("date"),title=r.getString("title").trim();
            if(id.isEmpty()||id.length()>100||!type.matches("learning|work")||!date.matches("\\d{4}-\\d{2}-\\d{2}")||title.isEmpty()||title.length()>300)throw new Exception("记录格式无效");
            parseLocal(date+"T00:00:00");
            for(String field:new String[]{"note","platform","url"}){if(r.has(field)&&!(r.get(field) instanceof String))throw new Exception("笔记、平台和链接必须是文本");if(!r.has(field))r.put(field,"");}
            if(!r.has("tags"))r.put("tags",new JSONArray());JSONArray tags=r.getJSONArray("tags");if(tags.length()>100)throw new Exception("标签过多");for(int i=0;i<tags.length();i++)if(!(tags.get(i) instanceof String))throw new Exception("标签格式无效");
            if(r.has("masteryLevel")&&!r.getString("masteryLevel").matches("unspecified|beginner|familiar|proficient"))throw new Exception("熟练度格式无效");if(r.has("evidenceType")&&!r.getString("evidenceType").matches("none|self|practice|tested"))throw new Exception("掌握证据类型无效");if(r.has("evidenceNote")&&(!(r.get("evidenceNote") instanceof String)||r.getString("evidenceNote").length()>5000))throw new Exception("掌握证据格式无效");
            double duration=r.optDouble("durationMinutes",0);if(!Double.isFinite(duration)||duration<0||duration>10080)throw new Exception("时长格式无效");
            if(r.toString().length()>150000)throw new Exception("单条笔记内容过长");
            if(!allow&&duplicate(r))throw new Exception("检测到相同日期、标题、平台的重复记录");
            if(type.equals("work")){
                long start=parseLocal(r.optString("workStart")),end=r.optString("workEnd").isEmpty()?System.currentTimeMillis():parseLocal(r.optString("workEnd"));boolean ongoing=r.optString("workEnd").isEmpty();
                if(!r.optString("workStart").startsWith(date+"T")||end<=start||(!ongoing&&end-start>7*86400000L))throw new Exception("工作时间格式无效，班次日期须等于上班日期");
                if(!r.has("breaks"))r.put("breaks",new JSONArray());JSONArray breaks=r.getJSONArray("breaks");java.util.ArrayList<JSONObject> intervals=new java.util.ArrayList<>();for(int i=0;i<breaks.length();i++)intervals.add(breaks.getJSONObject(i));java.util.Collections.sort(intervals,(a,b)->a.optString("start").compareTo(b.optString("start")));long previous=start;
                for(int i=0;i<intervals.size();i++){JSONObject b=intervals.get(i);long bs=parseLocal(b.optString("start")),be=b.optString("end").isEmpty()?end:parseLocal(b.optString("end"));if(bs<start||bs<previous||be<bs||be>end||(b.optString("end").isEmpty()&&(!ongoing||i!=intervals.size()-1)))throw new Exception("休息段必须在班次内，且不能重叠");previous=be;}
                try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM records WHERE type='work' AND id<>?",new String[]{id})){while(c.moveToNext()){JSONObject other=new JSONObject(c.getString(0));long os=parseLocal(other.optString("workStart")),oe=other.optString("workEnd").isEmpty()?Long.MAX_VALUE:parseLocal(other.optString("workEnd"));long newEnd=ongoing?Long.MAX_VALUE:end;if(start<oe&&newEnd>os)throw new Exception("工作班次不能重叠，请调整时间");}}
            }
            if(!r.has("createdAt"))r.put("createdAt",System.currentTimeMillis());r.put("updatedAt",System.currentTimeMillis());
            android.content.ContentValues values=new android.content.ContentValues();values.put("id",id);values.put("type",type);values.put("date",date);values.put("title",title);values.put("platform",r.optString("platform"));values.put("payload",r.toString());
            if(getWritableDatabase().insertWithOnConflict("records",null,values,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new Exception("记录写入失败");return r;
        }
        synchronized JSONArray messages()throws Exception{JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM (SELECT created,payload FROM messages ORDER BY created DESC LIMIT 100) ORDER BY created",null)){while(c.moveToNext())a.put(new JSONObject(c.getString(0)));}return a;}
        synchronized void saveMessage(JSONObject m)throws Exception{android.content.ContentValues v=new android.content.ContentValues();v.put("id",m.getString("id"));v.put("created",m.getLong("createdAt"));v.put("payload",m.toString());getWritableDatabase().insertWithOnConflict("messages",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    }
    private static String norm(String x){return java.text.Normalizer.normalize(x,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}\\p{S}]", "");}
    private static long parseLocal(String x)throws Exception{if(!x.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"))throw new Exception("时间格式无效");SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",Locale.US);f.setLenient(false);ParsePosition p=new ParsePosition(0);Date d=f.parse(x,p);if(d==null||p.getIndex()!=x.length())throw new Exception("时间格式无效");return d.getTime();}
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("quantlife-secrets")){KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder("quantlife-secrets",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
        return (SecretKey)ks.getKey("quantlife-secrets",null);
    }
    private JSONObject privateSettings() throws Exception {
        SharedPreferences sp=getSharedPreferences("secure",MODE_PRIVATE);String encrypted=sp.getString("settings",null);if(encrypted==null)return new JSONObject();
        String[] parts=encrypted.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new JSONObject(new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    private void encryptSettings(JSONObject s)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());String encrypted=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(s.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);if(!getSharedPreferences("secure",MODE_PRIVATE).edit().putString("settings",encrypted).commit())throw new Exception("设置保存失败");}
    private JSONObject publicSettings()throws Exception{JSONObject s=privateSettings();s.remove("mapTileKey");JSONObject search=LinkReader.searchSettings(s.optJSONObject("linkSearch"));search.put("keyConfigured",!search.optString("apiKey").isEmpty());search.remove("apiKey");s.put("linkSearch",search);JSONObject profiles=s.optJSONObject("profiles");if(profiles!=null){java.util.Iterator<String> it=profiles.keys();while(it.hasNext()){JSONObject p=profiles.getJSONObject(it.next());p.put("keyConfigured",!p.optString("apiKey").isEmpty());p.remove("apiKey");}}return s;}

    private JSONObject ask(JSONObject p) throws Exception {
        JSONObject settings=privateSettings(),profiles=settings.optJSONObject("profiles");String active=settings.optString("activeProfile");
        JSONObject profile=profiles==null?null:profiles.optJSONObject(active);if(profile==null||profile.optString("apiKey").isEmpty())throw new Exception("请先在「设置」中配置模型和 API Key");
        String endpoint=profile.getString("baseUrl"),protocol=profile.optString("protocol","chat");
        URL url=new URL(endpoint+(protocol.equals("responses")?"/responses":"/chat/completions"));
        if(url.getProtocol().equals("http")&&!profile.optBoolean("allowHttp"))throw new Exception("请在设置中允许可信内网 HTTP 连接");
        JSONArray chat=p.getJSONArray("messages");JSONObject body=new JSONObject();body.put("model",profile.getString("model"));body.put("stream",false);
        JSONObject tool=p.optJSONObject("tool");
        String effort=profile.optString("effort","off");
        if(protocol.equals("responses")) {
            JSONArray input=new JSONArray();String instruction="";
            for(int i=0;i<chat.length();i++){JSONObject m=chat.getJSONObject(i);if(m.getString("role").equals("system")){instruction+=m.getString("content");continue;}
                JSONObject out=new JSONObject();out.put("role",m.getString("role"));Object content=m.get("content");
                if(content instanceof JSONArray){JSONArray items=new JSONArray();JSONArray parts=(JSONArray)content;for(int j=0;j<parts.length();j++){JSONObject part=parts.getJSONObject(j),item=new JSONObject();if(part.getString("type").equals("text")){item.put("type","input_text");item.put("text",part.getString("text"));}else{item.put("type","input_image");item.put("image_url",part.getJSONObject("image_url").getString("url"));}items.put(item);}out.put("content",items);}else out.put("content",content);input.put(out);
            }body.put("instructions",instruction);body.put("input",input);body.put("store",false);body.put("max_output_tokens",4096);
            if(!effort.equals("off")){JSONObject reasoning=new JSONObject();reasoning.put("effort",effort);body.put("reasoning",reasoning);}
            if(tool!=null){JSONObject t=new JSONObject(tool.toString());t.put("type","function");body.put("tools",new JSONArray().put(t));body.put("tool_choice","auto");}
        } else {
            body.put("messages",chat);body.put("max_tokens",4096);
            if(tool!=null){JSONObject t=new JSONObject();t.put("type","function");t.put("function",tool);body.put("tools",new JSONArray().put(t));body.put("tool_choice","auto");}
            if(profile.optString("id").equals("glm")){JSONObject thinking=new JSONObject();thinking.put("type",effort.equals("off")?"disabled":"enabled");body.put("thinking",thinking);}
            else if(!effort.equals("off")&&!profile.optString("id").equals("deepseek"))body.put("reasoning_effort",effort);
        }
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(20000);conn.setReadTimeout(120000);conn.setInstanceFollowRedirects(false);conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.setRequestProperty("Authorization","Bearer "+profile.getString("apiKey"));
        try {
            try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=conn.getResponseCode();if(code<200||code>=300){
                if(code==401||code==403)throw new Exception("模型服务拒绝认证（"+code+"），请检查 API Key 和模型权限");
                if(code==429)throw new Exception("模型服务限流或额度不足，请稍后重试并检查余额");
                if(code==404)throw new Exception("接口或模型不存在（404），请检查 API 地址、协议和模型名称");
                throw new Exception("模型请求失败（HTTP "+code+"）；请检查模型是否支持图片、输入长度与服务状态");
            }
            JSONObject response=new JSONObject(new String(readBytes(conn.getInputStream(),8*1024*1024),StandardCharsets.UTF_8));StringBuilder answer=new StringBuilder();
            if(protocol.equals("responses")){JSONArray outputs=response.optJSONArray("output");if(outputs!=null)for(int i=0;i<outputs.length();i++){JSONObject output=outputs.getJSONObject(i);if("function_call".equals(output.optString("type"))&&(tool==null?"prepare_life_records":tool.optString("name")).equals(output.optString("name"))){answer.setLength(0);answer.append(output.getString("arguments"));break;}JSONArray parts=output.optJSONArray("content");if(parts!=null)for(int j=0;j<parts.length();j++)if("output_text".equals(parts.getJSONObject(j).optString("type")))answer.append(parts.getJSONObject(j).optString("text"));}}
            else{JSONArray choices=response.optJSONArray("choices");if(choices!=null&&choices.length()>0){JSONObject message=choices.getJSONObject(0).getJSONObject("message");JSONArray calls=message.optJSONArray("tool_calls");if(calls!=null){for(int i=0;i<calls.length();i++){JSONObject function=calls.getJSONObject(i).getJSONObject("function");if((tool==null?"prepare_life_records":tool.optString("name")).equals(function.optString("name"))){answer.append(function.getString("arguments"));break;}}}if(answer.length()==0)answer.append(message.optString("content", ""));}}
            if(answer.length()==0)throw new Exception("模型未返回文本，请切换非推理模型或提高输出额度");
            JSONObject result=new JSONObject();result.put("text",answer.toString());result.put("model",profile.getString("model"));return result;
        }finally{conn.disconnect();}
    }
    private static byte[] readBytes(InputStream input,int max)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new Exception("文件超过大小限制");out.write(buffer,0,n);}return out.toByteArray();}}
    private void handleShare(Intent intent) {
        if(!Intent.ACTION_SEND.equals(intent.getAction()))return;
        Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if(uri!=null)readAttachment(uri,"attachment");else{String text=intent.getStringExtra(Intent.EXTRA_TEXT);if(text!=null){try{JSONObject data=new JSONObject();data.put("kind","text");data.put("name","分享内容");data.put("text",text);event("attachment",data);}catch(Exception ignored){}}}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;Uri uri=data.getData();if(uri==null)return;
        if(request==PICK)readAttachment(uri,pickKind);
        if(request==PICK_MAP){tasks.execute(()->{try{OfflineMap.importFile(this,uri);event("offlineMap",new JSONObject().put("message","离线地图已导入，不影响轨迹记录"));}catch(Exception e){try{event("notice",new JSONObject().put("message","地图导入失败："+cleanError(e)));}catch(Exception ignored){}}});}
        if(request==EXPORT){tasks.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(exportBytes);JSONObject o=new JSONObject();o.put("message",exportLabel);event("notice",o);}catch(Exception e){try{JSONObject o=new JSONObject();o.put("message","导出失败："+cleanError(e));event("notice",o);}catch(Exception ignored){}}});}
    }
    private void readAttachment(Uri uri,String kind){tasks.execute(()->{try{
        String name=uri.getLastPathSegment()==null?"附件":uri.getLastPathSegment();try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(idx>=0)name=c.getString(idx);}}
        byte[] bytes=readBytes(getContentResolver().openInputStream(uri),FILE_LIMIT);String mime=getContentResolver().getType(uri);if(mime==null)mime="application/octet-stream";
        JSONObject o=new JSONObject();o.put("name",name);o.put("size",bytes.length);String lower=name.toLowerCase(Locale.ROOT);
        if(kind.equals("backup")){JSONObject b=new JSONObject(new String(bytes,StandardCharsets.UTF_8));if(!"quantlife".equals(b.optString("format"))||b.optInt("version")!=1)throw new Exception("这不是知时的数据备份");event("backup",b);return;}
        if(mime.startsWith("image/")||lower.matches(".*\\.(png|jpe?g|webp|gif|heic|heif)$")){
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);BitmapFactory.Options opts=new BitmapFactory.Options();int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>1600)sample*=2;opts.inSampleSize=sample;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);if(bitmap==null)throw new Exception("无法读取这张图片");
            ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,85,out);bitmap.recycle();o.put("kind","image");o.put("data","data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP));
        } else if(lower.endsWith("pdf")||mime.equals("application/pdf")){o.put("kind","pdf");o.put("data",Base64.encodeToString(bytes,Base64.NO_WRAP));}
        else if(lower.endsWith("docx")){
            String text="";try(ZipInputStream zip=new ZipInputStream(new java.io.ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(entry.getName().equals("word/document.xml")){
                String xml=new String(readBytes(zip,3*1024*1024),StandardCharsets.UTF_8);if(xml.contains("<!DOCTYPE")||xml.contains("<!ENTITY"))throw new Exception("不支持包含外部实体的文档");
                XmlPullParser parser=Xml.newPullParser();parser.setInput(new java.io.StringReader(xml));StringBuilder txt=new StringBuilder();boolean inText=false;for(int ev=parser.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=parser.next()){String tag=parser.getName();if(ev==XmlPullParser.START_TAG&&("t".equals(tag)||"w:t".equals(tag)))inText=true;else if(ev==XmlPullParser.TEXT&&inText)txt.append(parser.getText());else if(ev==XmlPullParser.END_TAG){if("t".equals(tag)||"w:t".equals(tag))inText=false;if("p".equals(tag)||"w:p".equals(tag))txt.append('\n');}}text=txt.toString();break;
            }}}if(text.isEmpty())throw new Exception("Word 文档没有可提取的文本");o.put("kind","text");o.put("text",text);
        } else if(mime.startsWith("text/")||lower.matches(".*\\.(txt|md|csv|json|log)$")){o.put("kind","text");o.put("text",new String(bytes,StandardCharsets.UTF_8));}
        else throw new Exception("支持图片、TXT、Markdown、CSV、PDF 和 DOCX 文件");
        event("attachment",o);
    }catch(Exception e){try{JSONObject o=new JSONObject();o.put("message",cleanError(e));event("notice",o);}catch(Exception ignored){}}});}
}
