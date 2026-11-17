package com.insight.quantlife;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolated, durable export delivery; never shares the legacy backup/GPX byte buffer. */
public final class AnalysisExportManager {
    private static final AtomicBoolean building=new AtomicBoolean(),copying=new AtomicBoolean();
    private AnalysisExportManager(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("analysis-export",Context.MODE_PRIVATE);}
    private static File file(Context c,String ticket,String suffix)throws IOException{
        if(!ticket.matches("[a-f0-9-]{36}"))throw new IOException("导出任务编号无效");
        File dir=new File(c.getCacheDir(),"analysis-export");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建导出临时目录");
        return new File(dir,"analysis-"+ticket+suffix);
    }
    private static void state(Context c,JSONObject s)throws Exception{if(!prefs(c).edit().putString("status",s.toString()).commit())throw new IOException("导出进度未能保存，请重试");}
    public static synchronized JSONObject status(Context c)throws Exception{
        JSONObject s=new JSONObject(prefs(c).getString("status","{\"state\":\"idle\",\"message\":\"\"}"));String phase=s.optString("state");
        if(phase.equals("preparing")&&!building.get()||phase.equals("saving")&&!copying.get()){s.put("state","failed").put("message","上次导出未完成，请重新导出");state(c,s);}
        return s;
    }
    public static synchronized JSONObject prepare(Context c,SQLiteDatabase db)throws Exception{
        JSONObject old=status(c);if(building.get()||copying.get()||old.optString("state").matches("preparing|waiting|saving"))throw new Exception("已有分析导出正在进行，请先完成或取消保存");
        String ticket=UUID.randomUUID().toString();File part=file(c,ticket,".part"),ready=file(c,ticket,".json");
        JSONObject next=new JSONObject().put("state","preparing").put("ticket",ticket).put("message","正在整理全部历史记录…");
        building.set(true);
        try{
            // Only remove our own previous staged file, never user exports.
            String prior=old.optString("ticket");if(prior.matches("[a-f0-9-]{36}")){file(c,prior,".json").delete();file(c,prior,".part").delete();}
            state(c,next);
            JSONObject summary;try(FileOutputStream out=new FileOutputStream(part)){summary=LearningAnalysisExport.write(db,out,c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName,(done,total)->{});out.getFD().sync();}
            if(!part.renameTo(ready))throw new IOException("导出快照暂存失败");
            next.put("state","waiting").put("summary",summary).put("bytes",ready.length()).put("fileName","知时学习工作分析-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".json").put("message","请选择保存位置");
            state(c,next);return next;
        }catch(Exception error){part.delete();ready.delete();next.put("state","failed").put("message","导出准备失败，请重试");try{state(c,next);}catch(Exception ignored){}throw error;}
        finally{building.set(false);}
    }
    public static synchronized JSONObject cancel(Context c)throws Exception{
        JSONObject s=status(c);if(s.optString("state").equals("waiting")){file(c,s.getString("ticket"),".json").delete();s.put("state","cancelled").put("message","已取消导出");state(c,s);}return s;
    }
    public static synchronized JSONObject failPicker(Context c)throws Exception{
        JSONObject s=status(c);if(s.optString("state").equals("waiting")){file(c,s.getString("ticket"),".json").delete();s.put("state","failed").put("message","无法打开保存位置，请重试");state(c,s);}return s;
    }
    public static JSONObject save(Context c,Uri destination)throws Exception{
        JSONObject s;File source;
        synchronized(AnalysisExportManager.class){
            s=status(c);if(!s.optString("state").equals("waiting")||!copying.compareAndSet(false,true))throw new Exception("这次导出已处理，请重新导出");
            try{source=file(c,s.getString("ticket"),".json");s.put("state","saving").put("message","正在保存分析文件…");state(c,s);}
            catch(Exception error){copying.set(false);throw error;}
        }
        try{
            if(!source.isFile())throw new IOException("导出快照已被系统清理，请重新导出");
            try(InputStream in=new FileInputStream(source);OutputStream out=c.getContentResolver().openOutputStream(destination,"wt")){
                if(out==null)throw new IOException("无法写入所选位置");byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.flush();
            }
            s.put("state","complete").put("message","已保存全部学习、工作和随笔分析数据");source.delete();
        }catch(Exception error){s.put("state","failed").put("message","保存失败，文件可能不完整，请重新导出");}
        finally{synchronized(AnalysisExportManager.class){try{state(c,s);}finally{copying.set(false);}}}
        return s;
    }
}
