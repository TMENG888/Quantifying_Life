package com.insight.quantlife;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import org.json.JSONObject;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.renderer.DatabaseRenderer;
import org.mapsforge.map.layer.renderer.RendererJob;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

/** Mapsforge 0.25.0 offline renderer. No HTTP, route inference or coordinate conversion. */
public final class VectorMap {
    private static MapFile map; private static DatabaseRenderer renderer;
    private static RenderThemeFuture theme; private static InMemoryTileCache cache; private static DisplayModel display;
    static File file(Context c){return new File(c.getFilesDir(),"offline-osm.map");}
    static boolean isMap(File f)throws IOException{byte[] header=new byte[20];try(InputStream in=new FileInputStream(f)){return in.read(header)==20&&new String(header,"US-ASCII").equals("mapsforge binary OSM");}}
    static JSONObject validate(File f)throws Exception{
        MapFile candidate=null;
        try{candidate=new MapFile(f);org.mapsforge.map.reader.header.MapFileInfo i=candidate.getMapFileInfo();
            return new JSONObject().put("kind","mapsforge").put("name","本机矢量地图").put("minZoom",2).put("maxZoom",18)
                .put("dataMinZoom",i.zoomLevelMin).put("dataMaxZoom",i.zoomLevelMax).put("bounds",i.boundingBox.toString()).put("mapDate",i.mapDate)
                .put("attribution","Mapsforge · OpenStreetMap contributors");
        }catch(Exception e){throw new Exception("无效的 Mapsforge .map 文件，原地图未改变");}finally{if(candidate!=null)candidate.close();}
    }
    static synchronized void reset(){if(renderer!=null)renderer.interruptAndDestroy();if(cache!=null)cache.destroy();if(theme!=null)theme.decrementRefCount();if(map!=null)map.close();map=null;renderer=null;theme=null;cache=null;}
    static synchronized JSONObject tile(Context c,int z,int x,int y)throws Exception{
        if(!file(c).isFile())return null;
        try{
            if(map==null){AndroidGraphicFactory.createInstance(c.getApplicationContext());map=new MapFile(file(c));display=new DisplayModel();display.setFixedTileSize(256);display.setUserScaleFactor(1);
                theme=new RenderThemeFuture(AndroidGraphicFactory.INSTANCE,MapsforgeThemes.DEFAULT,display);theme.run();theme.get();cache=new InMemoryTileCache(32);
                renderer=new DatabaseRenderer(map,AndroidGraphicFactory.INSTANCE,cache,null,true,false,null);
            }
            Tile tile=new Tile(x,y,(byte)z,256);if(!map.supportsTile(tile))return null;
            RendererJob job=new RendererJob(tile,map,theme,display,1,false,false);
            TileBitmap bitmap=cache.get(job);if(bitmap==null)bitmap=renderer.executeJob(job);if(bitmap==null)throw new IOException("地图渲染失败");
            try(ByteArrayOutputStream out=new ByteArrayOutputStream()){bitmap.compress(out);return new JSONObject().put("data","data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)).put("sourceZ",z).put("sourceX",x).put("sourceY",y).put("source","mapsforge");}
            finally{bitmap.decrementRefCount();}
        }catch(Exception e){reset();throw new Exception("离线矢量地图读取失败，请核对文件或重新导入");}
    }
    private VectorMap(){}
}
