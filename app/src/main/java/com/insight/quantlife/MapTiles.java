package com.insight.quantlife;
import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.net.*;

/** Explicit opt-in, visible-viewport tiles only; no bulk/offline tile download. */
public final class MapTiles{
    private static volatile long retryAfter=0;
    public static synchronized JSONObject get(Context context,int z,int x,int y,String cartoKey,boolean offline)throws Exception{
        if(z<0||z>18||x<0||y<0||x>=(1<<z)||y>=(1<<z))throw new Exception("地图坐标无效");
        if(offline){JSONObject local=OfflineMap.tile(context,z,x,y);if(local!=null)return local;}
        if(!offline&&!TrackingService.prefs(context).getBoolean("mapConsent",false))throw new Exception("请先同意在线地图隐私提示");
        String provider=TrackingService.prefs(context).getString("mapProvider","osm");boolean carto="carto".equals(provider);
        if(offline){provider="osm";carto=false;}
        if(carto&&cartoKey.isEmpty())throw new Exception("CARTO底图需要你自己的地图Key，请在轨迹页填写");
        String revision=TrackingService.prefs(context).getString("mapRevision","default");
        File dir=new File(context.getCacheDir(),"map-tiles-"+provider+"-"+revision);dir.mkdirs();File f=new File(dir,z+"-"+x+"-"+y+".png");
        byte[] data=null;long now=System.currentTimeMillis();
        if(f.isFile()&&(offline||f.lastModified()>now-7*86400000L))try(InputStream in=new FileInputStream(f)){data=read(in);}
        if(data==null){
            if(offline)throw new Exception("此区域或缩放级别无离线底图；仅显示本地路线");
            if(now<retryAfter)throw new Exception("地图服务暂不可用，请稍后重试");
            String endpoint=carto?"https://basemaps.cartocdn.com/light_all/"+z+"/"+x+"/"+y+".png?key="+java.net.URLEncoder.encode(cartoKey,"UTF-8"):"https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png";
            HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
            c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Zhishi/1.4.0 (Android personal trajectory viewer)");
            if(carto){c.setRequestProperty("X-Android-Package",context.getPackageName());try{android.content.pm.PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),android.content.pm.PackageManager.GET_SIGNATURES);byte[] cert=java.security.MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray());StringBuilder fingerprint=new StringBuilder();for(byte v:cert)fingerprint.append(String.format(java.util.Locale.US,"%02X",v&255));c.setRequestProperty("X-Android-Cert",fingerprint.toString());}catch(Exception ignored){}}
            try{
                int code=c.getResponseCode();if(code!=200){if(code==429||code==403||code>=500)retryAfter=now+60000;throw new Exception("在线地图不可用（"+code+"），本地轨迹不受影响");}
                if(c.getContentType()==null||!c.getContentType().startsWith("image/png"))throw new Exception("地图响应格式不正确");data=read(c.getInputStream());
                if(data.length<8||data[0]!=(byte)137||data[1]!=80||data[2]!=78||data[3]!=71)throw new Exception("地图图片无效");
                try(OutputStream out=new FileOutputStream(f)){out.write(data);}File[] files=dir.listFiles();if(files!=null&&files.length>256){java.util.Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));for(int i=0;i<files.length-256;i++)files[i].delete();}
            }catch(java.io.IOException e){throw new Exception("地图连接失败，请检查网络；CARTO还需有效地图Key");}finally{c.disconnect();}
        }
        JSONObject out=new JSONObject();out.put("data","data:image/png;base64,"+Base64.encodeToString(data,Base64.NO_WRAP));return out;
    }
    private static byte[] read(InputStream in)throws Exception{try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if(out.size()+n>1024*1024)throw new Exception("地图图片过大");out.write(b,0,n);}return out.toByteArray();}}
}
