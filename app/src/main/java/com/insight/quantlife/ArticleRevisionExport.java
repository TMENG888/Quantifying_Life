package com.insight.quantlife;
import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.*;

public final class ArticleRevisionExport {
    private static File pending(Context c){return new File(c.getFilesDir(),"article-revision-export.json");}
    public static synchronized JSONObject document(Context context,PublicationLibrary library,JSONObject record)throws Exception{
        String creation=record.optString("knowflowCreationId");if(!creation.matches("[a-fA-F0-9-]{36}"))throw new IOException("这条记录没有知流原稿编号，请先导入文章资料包");
        JSONObject pkg=library.get(record.getString("publicationPackageId")),map=record.optJSONObject("knowflowImageMap");
        if(map==null){map=new JSONObject();JSONArray originals=pkg.getJSONObject("common").getJSONArray("illustrations");for(int i=0;i<originals.length();i++){JSONObject image=originals.getJSONObject(i),local=RecordMedia.fromBytes(context,Files.readAllBytes(library.assetFile(pkg.getString("id"),image.getString("sha256")).toPath()),image.optString("title"));map.put(local.getString("id"),image.optString("sourceImageId").isEmpty()?"legacy-image:"+(i+1):image.getString("sourceImageId"));}}
        String note=record.optString("note");if(note.length()>300000)throw new IOException("文章过长");Matcher matcher=Pattern.compile("!\\[[^\\]\\n]*\\]\\(zhishi-media:([a-f0-9]{64})\\)").matcher(note);StringBuffer out=new StringBuffer();
        while(matcher.find()){String source=map.optString(matcher.group(1));if(!source.matches("[a-fA-F0-9-]{36}|legacy-image:[1-9][0-9]{0,2}"))throw new IOException("存在新增或无法对应的图片，请先在知流处理配图");String token=matcher.group().replace("zhishi-media:"+matcher.group(1),source);matcher.appendReplacement(out,Matcher.quoteReplacement(token));}matcher.appendTail(out);
        String markdown=out.toString();if(markdown.contains("zhishi-media:"))throw new IOException("有配图未匹配原文，未导出");
        JSONObject revision=record.optJSONObject("knowflowRevision");if(revision==null)revision=pkg.optJSONObject("revision");
        JSONObject result=new JSONObject().put("format","knowflow-revision").put("version",1).put("creationId",creation).put("title",record.optString("title")).put("markdown",markdown).put("exportedAt",System.currentTimeMillis()).put("legacyImages",pkg.getJSONObject("common").getJSONArray("illustrations"));
        if(revision==null){String baseline=PublicationArticle.record(context,library,pkg,"export-baseline").getString("note");Matcher original=Pattern.compile("!\\[[^\\]\\n]*\\]\\(zhishi-media:([a-f0-9]{64})\\)").matcher(baseline);StringBuffer converted=new StringBuffer();while(original.find()){String source=map.optString(original.group(1));original.appendReplacement(converted,Matcher.quoteReplacement(original.group().replace("zhishi-media:"+original.group(1),source)));}original.appendTail(converted);result.put("baseMarkdown",converted.toString());}
        if(revision!=null){result.put("baseMarkdown",revision.optString("baseMarkdown"));result.put("baseDraftHash",revision.optString("baseDraftHash"));}
        return result;
    }
    public static synchronized void prepare(Context c,PublicationLibrary library,JSONObject record)throws Exception{
        byte[] bytes=document(c,library,record).toString(2).getBytes(StandardCharsets.UTF_8);File staging=new File(c.getFilesDir(),"article-revision-export.tmp");Files.write(staging.toPath(),bytes);Files.move(staging.toPath(),pending(c).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    public static synchronized void save(Context c,Uri uri)throws Exception{
        File file=pending(c);if(!file.isFile())throw new IOException("待导出的改稿不存在，请重新导出");try(InputStream in=new FileInputStream(file);OutputStream out=c.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("无法写入所选位置");byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.flush();}file.delete();
    }
    private ArticleRevisionExport(){}
}
