package com.insight.quantlife;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.util.Xml;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import com.insight.quantlife.TrackFilter.Point;

/** Separate, replaceable OSM network. No track DB writes, HTTP, SDK keys or coordinate conversion. */
public final class RoadNetwork {
    private static final ReentrantReadWriteLock LOCK=new ReentrantReadWriteLock();
    private static final AtomicBoolean IMPORTING=new AtomicBoolean();
    private static String cacheKey="";private static JSONObject cached;
    private static File file(Context c){return new File(c.getFilesDir(),"offline-road-network.db");}
    public static JSONObject info(Context c)throws Exception{return new JSONObject(TrackingService.prefs(c).getString("roadNetworkInfo","{}"))
        .put("configured",file(c).isFile()).put("importing",IMPORTING.get()).put("enabled",TrackingService.prefs(c).getBoolean("roadMatchingEnabled",true))
        .put("mode",TrackingService.prefs(c).getString("roadMatchingMode","auto")).put("localOnly",true).put("inferred",true).put("revision",RoadMatcher.REVISION);}
    public static void configure(Context c,JSONObject p)throws Exception{
        android.content.SharedPreferences.Editor e=TrackingService.prefs(c).edit();if(p.has("enabled"))e.putBoolean("roadMatchingEnabled",p.getBoolean("enabled"));if(p.has("mode")){String mode=p.getString("mode");if(!mode.matches("auto|walk|bike|car"))throw new Exception("出行方式无效");e.putString("roadMatchingMode",mode);}e.apply();
    }
    private static String attr(XmlPullParser p,String key){String v=p.getAttributeValue(null,key);return v==null?"":v;}
    private static boolean denied(String s){return s.equals("no")||s.equals("private");}
    private static int modes(Map<String,String> t){
        String h=t.getOrDefault("highway","");if(!h.matches("motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service|pedestrian|footway|path|steps|cycleway|track"))return 0;
        if(t.getOrDefault("area","").equals("yes")||t.getOrDefault("indoor","").equals("yes")||t.containsKey("construction")||t.containsKey("proposed"))return 0;
        int result=h.matches("motorway.*|trunk.*")?4:h.equals("steps")?1:h.matches("footway|pedestrian")?1:h.equals("cycleway")?2:h.equals("path")?3:7;
        if(denied(t.getOrDefault("access","")))result=0;if(denied(t.getOrDefault("vehicle","")))result&=1;
        for(String[] k:new String[][]{{"foot","1"},{"bicycle","2"},{"motor_vehicle","4"},{"motorcar","4"}}){String s=t.getOrDefault(k[0],"");int flag=Integer.parseInt(k[1]);if(denied(s)||s.equals("use_sidepath"))result&=~flag;else if(s.matches("yes|designated|permissive|destination"))result|=flag;}
        // Conditional rules cannot safely be interpreted without their full context.
        if(t.containsKey("access:conditional"))return 0;
        if(t.containsKey("foot:conditional"))result&=~1;if(t.containsKey("bicycle:conditional"))result&=~2;if(t.containsKey("motor_vehicle:conditional")||t.containsKey("motorcar:conditional"))result&=~4;
        return result;
    }
    private static XmlPullParser parser(File f,InputStream in)throws Exception{XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false);p.setInput(in,"UTF-8");return p;}
    private static int step(XmlPullParser p)throws Exception{int e=p.nextToken();if(e==XmlPullParser.DOCDECL)throw new Exception("不支持含DTD的路网文件");if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("路网导入已中止");return e;}
    public static void importFile(Context c,Uri uri)throws Exception{
        if(!IMPORTING.compareAndSet(false,true))throw new Exception("正在导入路网，请等待完成");
        File xml=new File(c.getFilesDir(),"road-import.osm.tmp"),temp=new File(c.getFilesDir(),"road-import.db.tmp");SQLiteDatabase db=null;
        try{
            long bytes=0;try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(xml)){if(in==null)throw new Exception("无法读取路网");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){bytes+=n;if(bytes>128L*1024*1024)throw new Exception("路网最大128MB，请选择城区范围的OSM XML");if(c.getFilesDir().getUsableSpace()<64L*1024*1024)throw new Exception("手机剩余空间不足，旧路网未改变");out.write(b,0,n);}}
            if(temp.exists()&&!temp.delete())throw new IOException("无法创建路网临时库");db=SQLiteDatabase.openOrCreateDatabase(temp,null);try(Cursor pragma=db.rawQuery("PRAGMA journal_mode=DELETE",null)){pragma.moveToFirst();}
            db.execSQL("CREATE TABLE nodes(id INTEGER PRIMARY KEY,lat REAL,lon REAL,blocked INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE edges(id INTEGER PRIMARY KEY,a INTEGER,b INTEGER,alat REAL,alon REAL,blat REAL,blon REAL,forward INTEGER,backward INTEGER,minlat REAL,maxlat REAL,minlon REAL,maxlon REAL)");
            db.beginTransaction();int nodes=0,edges=0,missing=0;boolean root=false;long started=System.nanoTime();
            try(SQLiteStatement insert=db.compileStatement("INSERT INTO nodes(id,lat,lon,blocked) VALUES(?,?,?,?)")){
                // Two passes retain topology even if an OSM producer writes ways before nodes.
                try(InputStream in=new BufferedInputStream(new FileInputStream(xml))){XmlPullParser p=parser(xml,in);long id=0;double lat=0,lon=0;boolean node=false;Map<String,String> tags=new HashMap<>();
                    for(int e=p.getEventType();e!=XmlPullParser.END_DOCUMENT;e=step(p)){if(System.nanoTime()-started>180000000000L)throw new Exception("路网过大，导入超时，请缩小区域");if(e==XmlPullParser.START_TAG){String name=p.getName();if(!root){if(!name.equals("osm"))throw new Exception("请选择OSM XML（.osm）；地图图片、.map和PBF不能作为路网");root=true;}if(name.equals("node")){id=Long.parseLong(attr(p,"id"));lat=Double.parseDouble(attr(p,"lat"));lon=Double.parseDouble(attr(p,"lon"));if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>85||Math.abs(lon)>180)throw new Exception("路网坐标无效");node=true;tags.clear();}else if(name.equals("tag")&&node)tags.put(attr(p,"k"),attr(p,"v"));}
                        else if(e==XmlPullParser.END_TAG&&p.getName().equals("node")&&node){int blocked=denied(tags.getOrDefault("access",""))?7:0;String barrier=tags.getOrDefault("barrier","");if(barrier.matches("wall|fence|yes"))blocked=7;else if(barrier.matches("bollard|block|cycle_barrier"))blocked|=4;for(String[] k:new String[][]{{"foot","1"},{"bicycle","2"},{"motor_vehicle","4"}})if(denied(tags.getOrDefault(k[0],"")))blocked|=Integer.parseInt(k[1]);insert.bindLong(1,id);insert.bindDouble(2,lat);insert.bindDouble(3,lon);insert.bindLong(4,blocked);insert.executeInsert();node=false;if(++nodes>1500000)throw new Exception("路网节点过多，请缩小区域");}}
                }
            }
            if(!root||nodes<2)throw new Exception("路网缺少节点，旧路网未改变");
            try(SQLiteStatement insert=db.compileStatement("INSERT INTO edges VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)");InputStream in=new BufferedInputStream(new FileInputStream(xml))){XmlPullParser p=parser(xml,in);List<Long> refs=new ArrayList<>();Map<String,String> tags=new HashMap<>();boolean way=false;
                for(int e=p.getEventType();e!=XmlPullParser.END_DOCUMENT;e=step(p)){if(System.nanoTime()-started>180000000000L)throw new Exception("路网导入超时，请缩小区域");if(e==XmlPullParser.START_TAG){String name=p.getName();if(name.equals("way")){way=true;refs.clear();tags.clear();}else if(way&&name.equals("nd")){if(refs.size()>=30000)throw new Exception("单条道路节点过多");refs.add(Long.parseLong(attr(p,"ref")));}else if(way&&name.equals("tag")){if(tags.size()>256)throw new Exception("路网属性过多");tags.put(attr(p,"k"),attr(p,"v"));}}
                    else if(e==XmlPullParser.END_TAG&&p.getName().equals("way")&&way){way=false;int flags=modes(tags);if(flags==0)continue;int forward=flags,backward=flags;String one=tags.getOrDefault("oneway",tags.getOrDefault("junction","").equals("roundabout")?"yes":"");if(one.matches("yes|1|true"))backward&=1;else if(one.equals("-1"))forward&=1;if(tags.getOrDefault("oneway:bicycle","").equals("no")){forward|=flags&2;backward|=flags&2;}if(tags.getOrDefault("oneway:foot","").equals("yes"))backward&=~1;
                        double[] prev=null;long prevId=0;for(long ref:refs){double[] now=null;try(Cursor q=db.rawQuery("SELECT lat,lon,blocked FROM nodes WHERE id=?",new String[]{String.valueOf(ref)})){if(q.moveToFirst())now=new double[]{q.getDouble(0),q.getDouble(1),q.getInt(2)};else missing++;}
                            if(prev!=null&&now!=null){double len=RoadMatcher.distance(prev[0],prev[1],now[0],now[1]);int allowed=~((int)prev[2]|(int)now[2]);int f=forward&allowed,r=backward&allowed;if(len>=.05&&len<=1500&&(f|r)!=0){if(++edges>400000)throw new Exception("路网道路过多，请缩小区域");long[] longs={edges,prevId,ref};for(int k=0;k<3;k++)insert.bindLong(k+1,longs[k]);insert.bindDouble(4,prev[0]);insert.bindDouble(5,prev[1]);insert.bindDouble(6,now[0]);insert.bindDouble(7,now[1]);insert.bindLong(8,f);insert.bindLong(9,r);insert.bindDouble(10,Math.min(prev[0],now[0]));insert.bindDouble(11,Math.max(prev[0],now[0]));insert.bindDouble(12,Math.min(prev[1],now[1]));insert.bindDouble(13,Math.max(prev[1],now[1]));insert.executeInsert();}}
                            prev=now;prevId=ref;
                        }
                    }}
            }
            if(edges<1)throw new Exception("未找到可用道路，请提供包含道路节点的完整OSM XML");
            db.execSQL("CREATE INDEX edge_lat ON edges(minlat,maxlat)");db.execSQL("DROP TABLE nodes");db.setTransactionSuccessful();db.endTransaction();db.close();db=null;
            JSONObject details=new JSONObject().put("networkId",UUID.randomUUID().toString()).put("edges",edges).put("nodes",nodes).put("missingNodeReferences",missing).put("importedAt",System.currentTimeMillis()).put("source","OpenStreetMap XML").put("coordinateSystem","WGS84");
            LOCK.writeLock().lock();try{android.system.Os.rename(temp.getPath(),file(c).getPath());TrackingService.prefs(c).edit().putString("roadNetworkInfo",details.toString()).apply();synchronized(RoadNetwork.class){cacheKey="";cached=null;}}finally{LOCK.writeLock().unlock();}
        }finally{try{if(db!=null){try{if(db.inTransaction())db.endTransaction();}finally{db.close();}}}finally{try{xml.delete();temp.delete();new File(temp.getPath()+"-journal").delete();}finally{IMPORTING.set(false);}}}
    }
    private static RoadMatcher.Graph graph(SQLiteDatabase db,List<Point> points)throws Exception{
        double minlat=90,maxlat=-90,minlon=180,maxlon=-180;for(Point p:points){minlat=Math.min(minlat,p.lat);maxlat=Math.max(maxlat,p.lat);minlon=Math.min(minlon,p.lon);maxlon=Math.max(maxlon,p.lon);}double pad=.003,lonpad=pad/Math.max(.1,Math.cos(Math.toRadians(minlat)));
        RoadMatcher.Graph g=new RoadMatcher.Graph();Map<Long,RoadMatcher.Node> nodes=new HashMap<>();
        try(Cursor c=db.rawQuery("SELECT id,a,b,alat,alon,blat,blon,forward,backward FROM edges WHERE minlat<=? AND maxlat>=? AND minlon<=? AND maxlon>=? LIMIT 30001",new String[]{""+(maxlat+pad),""+(minlat-pad),""+(maxlon+lonpad),""+(minlon-lonpad)})){
            int n=0;while(c.moveToNext()){if(++n>30000)throw new Exception("当前区域路网过密，保留原轨迹");long a=c.getLong(1),b=c.getLong(2);RoadMatcher.Node x=nodes.get(a),y=nodes.get(b);if(x==null){x=new RoadMatcher.Node(a,c.getDouble(3),c.getDouble(4));nodes.put(a,x);}if(y==null){y=new RoadMatcher.Node(b,c.getDouble(5),c.getDouble(6));nodes.put(b,y);}g.add(new RoadMatcher.Edge(c.getLong(0),x,y,c.getInt(7),c.getInt(8)));}}
        return g;
    }
    private static int profile(List<Point> points,String requested){if(requested.equals("walk"))return 1;if(requested.equals("bike"))return 2;if(requested.equals("car"))return 4;List<Double> speeds=new ArrayList<>();for(Point p:points)if(p.reliableSpeed())speeds.add(p.speed);if(speeds.size()<3)return 1;Collections.sort(speeds);double v=speeds.get(speeds.size()/2);return v>9?4:v>3.2?2:1;}
    public static JSONObject match(Context c,JSONObject day)throws Exception{
        JSONObject status=info(c),out=new JSONObject().put("routes",new JSONArray()).put("localOnly",true).put("inferred",true).put("affectsMileage",false).put("revision",RoadMatcher.REVISION);
        if(!status.getBoolean("configured"))return out.put("state","no_network").put("message","导入本地区域的OSM路网后启用道路推断");if(!status.getBoolean("enabled"))return out.put("state","disabled").put("message","道路推断已关闭");
        if(!day.getJSONObject("filter").optString("id").equals(TrackFusion.ID))return out.put("state","unsupported_filter").put("message","请切换“停留与移动识别”以使用道路推断");
        JSONArray input=day.getJSONArray("points");String fingerprint=android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes("UTF-8")),android.util.Base64.NO_WRAP);String key=status.optString("networkId")+":"+status.getString("mode")+":"+day.getString("date")+":"+day.optInt("filterVersion")+":"+day.optJSONObject("filter")+":"+fingerprint;
        synchronized(RoadNetwork.class){if(key.equals(cacheKey)&&cached!=null)return new JSONObject(cached.toString());}
        long deadline=System.nanoTime()+6000000000L;List<List<Point>> windows=new ArrayList<>();List<Point> run=new ArrayList<>();Point last=null;int selected=0;boolean partial=false;
        for(int i=0;i<input.length();i++){JSONObject o=input.getJSONObject(i);Point p=new Point();p.time=o.getLong("time");p.lat=o.getDouble("lat");p.lon=o.getDouble("lon");p.accuracy=o.getDouble("accuracy");p.session=o.getString("session");p.motionState=o.optString("motionState");p.provider=o.optString("provider");p.speed=o.optDouble("reportedSpeed",o.optDouble("speed",Double.NaN));p.speedAccuracy=o.optDouble("speedAccuracy",Double.NaN);p.mocked=o.optBoolean("mocked");
            if(!Double.isFinite(p.lat)||!Double.isFinite(p.lon)||Math.abs(p.lat)>85||Math.abs(p.lon)>179.8||p.mocked||!p.motionState.equals("moving")){if(run.size()>=3)windows.add(run);run=new ArrayList<>();last=null;continue;}
            if(last!=null&&(!last.session.equals(p.session)||p.time<=last.time||p.time-last.time>60000||run.size()>=80||RoadMatcher.distance(run.get(0).lat,run.get(0).lon,p.lat,p.lon)>2200)){if(run.size()>=3)windows.add(run);Point tail=last;run=new ArrayList<>();if(tail.session.equals(p.session)&&p.time>tail.time&&p.time-tail.time<=60000)run.add(tail);last=null;}
            if(last==null||p.time-last.time>=8000||i==input.length()-1){run.add(p);last=p;if(++selected>=1800){partial=true;break;}}
        }if(run.size()>=3)windows.add(run);JSONArray routes=new JSONArray();int attempted=0;
        LOCK.readLock().lock();try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file(c).getPath(),null,SQLiteDatabase.OPEN_READONLY)){
            for(List<Point> window:windows){if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted()){partial=true;break;}attempted++;RoadMatcher.Route route=RoadMatcher.match(graph(db,window),window,profile(window,status.getString("mode")),deadline);if(route==null)continue;JSONArray path=new JSONArray();for(double[] p:route.path)path.put(new JSONObject().put("lat",p[0]).put("lon",p[1]));routes.put(new JSONObject().put("start",route.start).put("end",route.end).put("path",path).put("inferred",true).put("observations",route.observations).put("mode",route.mode==1?"walk":route.mode==2?"bike":"car"));}
        }catch(Exception e){return out.put("state","unavailable").put("message","路网暂不可用，已保留原轨迹");}finally{LOCK.readLock().unlock();}
        out.put("routes",routes).put("state",partial?"partial":routes.length()>0?"matched":"no_match").put("attemptedWindows",attempted).put("message",(partial?"已完成部分道路推断；其余保留原轨迹":routes.length()>0?"已生成 "+routes.length()+" 段离线道路推断，不计里程":"没有足够可信的道路匹配，保留原轨迹"));
        synchronized(RoadNetwork.class){cacheKey=key;cached=new JSONObject(out.toString());}return out;
    }
    private RoadNetwork(){}
}
