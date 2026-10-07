package com.insight.quantlife;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import java.io.*;
import org.json.JSONObject;

/** Local raster MBTiles (TMS, Web Mercator). Never sends the file or its tiles over a network. */
public final class OfflineMap {
    private static File file(Context c){return new File(c.getFilesDir(),"offline-osm.mbtiles");}
    private static String metadata(SQLiteDatabase db,String name){try(Cursor c=db.rawQuery("SELECT substr(value,1,2000) FROM metadata WHERE name=? LIMIT 1",new String[]{name})){return c.moveToFirst()&&!c.isNull(0)?c.getString(0):"";}catch(Exception e){return "";}}
    private static String mime(byte[] bytes)throws Exception{
        if(bytes==null||bytes.length>1024*1024)throw new Exception("离线地图图片过大或无效");
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        if(o.outWidth<=0||o.outHeight<=0||o.outWidth>512||o.outHeight>512||!("image/png".equals(o.outMimeType)||"image/jpeg".equals(o.outMimeType)||"image/webp".equals(o.outMimeType)))throw new Exception("仅支持PNG/JPEG/WebP栅格MBTiles，不支持PBF矢量地图");return o.outMimeType;
    }
    public static synchronized JSONObject info(Context context){JSONObject out=new JSONObject();try{if(file(context).isFile())out=new JSONObject(TrackingService.prefs(context).getString("offlineMapInfo","{}"));out.put("configured",file(context).isFile());}catch(Exception ignored){}return out;}
    public static synchronized void importFile(Context context,Uri uri)throws Exception{
        File tmp=new File(context.getFilesDir(),"offline-osm-import.tmp");
        try{
            long total=0;try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){if(in==null)throw new Exception("无法读取地图文件");byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){total+=n;if(total>512L*1024*1024)throw new Exception("离线地图文件不能超过512MB，请选择较小区域");if(context.getFilesDir().getUsableSpace()<32L*1024*1024)throw new Exception("手机剩余空间不足");out.write(buf,0,n);}}
            JSONObject details=new JSONObject();try(SQLiteDatabase db=SQLiteDatabase.openDatabase(tmp.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
                String scheme=metadata(db,"scheme"),format=metadata(db,"format");if(!scheme.isEmpty()&&!"tms".equalsIgnoreCase(scheme))throw new Exception("仅支持标准TMS行号的MBTiles");if(format.equalsIgnoreCase("pbf"))throw new Exception("不支持PBF矢量MBTiles，请选择栅格版本");
                try(Cursor c=db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level BETWEEN 0 AND 18 LIMIT 1",null)){if(!c.moveToFirst())throw new Exception("地图文件没有可用图块");mime(c.getBlob(0));}
                try(Cursor c=db.rawQuery("SELECT zoom_level FROM tiles WHERE zoom_level<0 OR zoom_level>18 OR tile_column<0 OR tile_row<0 LIMIT 1",null)){if(c.moveToFirst())throw new Exception("地图文件包含不支持的缩放级别或坐标");}
                details.put("name",metadata(db,"name"));details.put("attribution",metadata(db,"attribution").replaceAll("<[^>]*>",""));try(Cursor c=db.rawQuery("SELECT MIN(zoom_level),MAX(zoom_level) FROM tiles",null)){if(c.moveToFirst()){details.put("minZoom",c.getInt(0));details.put("maxZoom",c.getInt(1));}}
            }catch(android.database.SQLException e){throw new Exception("不是有效的MBTiles地图文件，原地图未改变");}
            // Same-filesystem atomic replacement; invalid imports never remove the previous map.
            android.system.Os.rename(tmp.getPath(),file(context).getPath());
            TrackingService.prefs(context).edit().putString("offlineMapInfo",details.toString()).apply();
        }finally{if(tmp.isFile())tmp.delete();}
    }
    public static synchronized JSONObject tile(Context context,int z,int x,int y)throws Exception{
        if(file(context).isFile())try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file(context).getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=? LIMIT 1",new String[]{String.valueOf(z),String.valueOf(x),String.valueOf((1<<z)-1-y)})){
            if(c.moveToFirst()){byte[] bytes=c.getBlob(0);return new JSONObject().put("data","data:"+mime(bytes)+";base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP));}
        }
        return null;
    }
    private OfflineMap(){}
}
