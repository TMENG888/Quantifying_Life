package com.insight.quantlife;
import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;import java.net.*;import java.util.concurrent.ConcurrentHashMap;

/** Visible-viewport requests only. Offline never opens a network connection. */
public final class MapTiles {
    private static final Object LOCK=new Object();
    private static volatile long retryAfter=0,currentView=0,newestView=0;
    private static final ConcurrentHashMap<HttpURLConnection,Long> active=new ConcurrentHashMap<>();
    public static JSONObject begin(Context c,long view,String mode)throws Exception{
        if(view<=0||!mode.matches("online|offline"))throw new Exception("地图视图无效");
        check(c,mode.equals("offline"),0);
        synchronized(LOCK){if(view<=newestView)throw new Exception("地图视图已更新");newestView=view;currentView=view;disconnectAll();}
        return new JSONObject().put("view",view);
    }
    public static void cancelRequests(){synchronized(LOCK){currentView=0;disconnectAll();}}
    private static void disconnectAll(){for(HttpURLConnection c:active.keySet())c.disconnect();active.clear();}
    private static void check(Context c,boolean offline,long view)throws Exception{
        if(view>0&&view!=currentView)throw new Exception("地图视图已更新");
        boolean currentOffline=!TrackingService.prefs(c).getString("mapMode","offline").equals("online");
        if(offline!=currentOffline)throw new Exception("地图模式已切换");
        if(!offline&&!TrackingService.prefs(c).getBoolean("mapConsent",false))throw new Exception("请先同意在线地图隐私提示");
    }
    private static File directory(Context c,String provider){String revision=TrackingService.prefs(c).getString("mapRevision","default");return new File(c.getCacheDir(),"map-tiles-"+provider+"-"+revision);}
    private static File cachedFile(File dir,int z,int x,int y){return new File(dir,z+"-"+x+"-"+y+".png");}
    private static boolean png(byte[] bytes){return bytes!=null&&bytes.length>=8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;}
    private static JSONObject response(byte[] bytes,int z,int x,int y,String source)throws Exception{return new JSONObject().put("data","data:image/png;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)).put("sourceZ",z).put("sourceX",x).put("sourceY",y).put("source",source);}
    private static JSONObject cached(File dir,int z,int x,int y,boolean fresh)throws Exception{
        File f=cachedFile(dir,z,x,y);if(!f.isFile()||fresh&&f.lastModified()<System.currentTimeMillis()-7*86400000L)return null;
        try(InputStream in=new FileInputStream(f)){byte[] bytes=read(in);return png(bytes)?response(bytes,z,x,y,"cache"):null;}catch(Exception e){return null;}
    }
    private static JSONObject ancestor(Context c,File dir,int z,int x,int y,boolean offline)throws Exception{
        JSONObject packaged=offline?OfflineMap.tileOrAncestor(c,z,x,y):null;
        int packageZoom=packaged==null?-1:packaged.getInt("sourceZ");
        for(int sourceZ=z-1;sourceZ>=0;sourceZ--){
            if(packageZoom>=sourceZ)return packaged;
            int scale=1<<(z-sourceZ);JSONObject local=cached(dir,sourceZ,x/scale,y/scale,false);if(local!=null)return local;
        }
        return packaged;
    }
    public static JSONObject get(Context c,int z,int x,int y,String key,boolean offline)throws Exception{return get(c,z,x,y,key,offline,0);}
    public static JSONObject get(Context context,int z,int x,int y,String cartoKey,boolean offline,long view)throws Exception{
        if(z<0||z>18||x<0||y<0||x>=(1<<z)||y>=(1<<z))throw new Exception("地图坐标无效");check(context,offline,view);
        if(offline){JSONObject local=OfflineMap.tile(context,z,x,y);if(local!=null){check(context,true,view);return local;}}
        String provider=offline?"osm":TrackingService.prefs(context).getString("mapProvider","osm");boolean carto="carto".equals(provider);
        File dir=directory(context,provider);JSONObject exact=cached(dir,z,x,y,!offline);if(exact!=null){check(context,offline,view);return exact;}
        if(offline){JSONObject parent=ancestor(context,dir,z,x,y,true);check(context,true,view);if(parent!=null)return parent;throw new Exception("此区域没有离线底图或可用低清图；轨迹仍可查看");}
        if(carto&&cartoKey.isEmpty())throw new Exception("CARTO底图需要你自己的地图Key");
        long now=System.currentTimeMillis();Exception failure=null;
        if(now<retryAfter)failure=new Exception("地图服务暂不可用，请稍后刷新");
        else {
            String endpoint=carto?"https://basemaps.cartocdn.com/light_all/"+z+"/"+x+"/"+y+".png?key="+java.net.URLEncoder.encode(cartoKey,"UTF-8"):"https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png";
            HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
            connection.setConnectTimeout(5000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","Zhishi/1.9.0 (Android personal trajectory viewer)");
            if(carto){connection.setRequestProperty("X-Android-Package",context.getPackageName());try{android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_SIGNATURES);byte[] cert=java.security.MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray());StringBuilder fingerprint=new StringBuilder();for(byte v:cert)fingerprint.append(String.format(java.util.Locale.US,"%02X",v&255));connection.setRequestProperty("X-Android-Cert",fingerprint.toString());}catch(Exception ignored){}}
            synchronized(LOCK){check(context,false,view);active.put(connection,view);}
            try {
                check(context,false,view);int code=connection.getResponseCode();
                if(code!=200){if(code==429||code==403||code>=500)retryAfter=System.currentTimeMillis()+60000;throw new Exception("在线地图不可用（"+code+"）");}
                if(connection.getContentType()==null||!connection.getContentType().startsWith("image/png"))throw new Exception("地图响应格式不正确");
                byte[] bytes=read(connection.getInputStream());if(!png(bytes))throw new Exception("地图图片无效");check(context,false,view);
                dir.mkdirs();File temp=File.createTempFile("tile-", ".tmp",dir);
                try {try(OutputStream out=new FileOutputStream(temp)){out.write(bytes);}check(context,false,view);android.system.Os.rename(temp.getPath(),cachedFile(dir,z,x,y).getPath());}finally{if(temp.isFile())temp.delete();}
                File[] files=dir.listFiles((folder,name)->name.endsWith(".png"));if(files!=null&&files.length>256){java.util.Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));for(int i=0;i<files.length-256;i++)files[i].delete();}
                return response(bytes,z,x,y,"network");
            }catch(Exception e){failure=e instanceof IOException?new Exception("地图连接失败，可切换离线或稍后刷新"):e;}
            finally{active.remove(connection);connection.disconnect();}
        }
        check(context,false,view);JSONObject parent=ancestor(context,dir,z,x,y,false);
        if(parent!=null)return parent.put("warning",failure.getMessage());
        throw failure;
    }
    private static byte[] read(InputStream in)throws Exception{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(out.size()+n>1024*1024)throw new Exception("地图图片过大");out.write(b,0,n);}return out.toByteArray();}}
    private MapTiles(){}
}
