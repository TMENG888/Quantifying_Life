package com.insight.quantlife;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public final class PublicationLibrary extends SQLiteOpenHelper {
    private final Context context;
    public PublicationLibrary(Context context){super(context,"publication-library.db",null,1);this.context=context.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE packages(id TEXT PRIMARY KEY,fingerprint TEXT UNIQUE NOT NULL,created INTEGER NOT NULL,payload TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("Unsupported publication migration");}
    private File home(){File home=new File(context.getFilesDir(),"publication-library");home.mkdirs();return home;}
    public static File inside(File root,String path)throws IOException{
        if(path==null||path.isEmpty()||path.startsWith("/")||path.contains("\\")||path.contains(":"))throw new IOException("资料路径无效");
        File file=new File(root,path).getCanonicalFile();if(!file.getPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("资料路径超出目录");return file;
    }
    private File root(String id)throws IOException{if(!id.matches("[a-f0-9-]{36}"))throw new IOException("资料编号无效");return inside(home(),id);}
    private static void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
    private static long copy(InputStream in,OutputStream out,long max)throws IOException{if(in==null||out==null)throw new IOException("无法访问文件");long total=0;byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){total+=n;if(total>max)throw new IOException("资料超过大小限制");out.write(buffer,0,n);}return total;}
    private static String sha(File file)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] block=new byte[32768];int n;while((n=in.read(block))!=-1)md.update(block,0,n);}StringBuilder result=new StringBuilder();for(byte b:md.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
    private static String text(File root,String path)throws Exception{File file=inside(root,path);if(!file.isFile()||file.length()>4*1024*1024)throw new IOException("正文或说明文件缺失或过大");return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).replaceFirst("^\uFEFF","").replace("\r\n","\n").replace('\r','\n');}
    private static JSONObject image(File root,String path)throws Exception{File file=inside(root,path);BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),size);if(size.outWidth<1||size.outHeight<1||(long)size.outWidth*size.outHeight>100000000)throw new IOException("图片不可读取或尺寸过大");String mime=size.outMimeType;if(!Arrays.asList("image/jpeg","image/png","image/webp").contains(mime))throw new IOException("不支持的图片格式");return new JSONObject().put("file",path).put("sha256",sha(file)).put("mime",mime);}
    private static JSONArray images(File root,JSONArray source)throws Exception{if(source.length()>300)throw new IOException("配图数量过多");JSONArray result=new JSONArray();for(int i=0;i<source.length();i++){JSONObject row=source.getJSONObject(i);result.put(image(root,row.getString("file")).put("title",row.optString("title","正文配图")).put("afterParagraph",Math.max(0,row.getInt("afterParagraph"))).put("anchor",row.optString("anchor")).put("sourceImageId",row.optString("id")));}return result;}
    private static boolean sameImages(JSONArray a,JSONArray b)throws Exception{if(a.length()!=b.length())return false;for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i),y=b.getJSONObject(i);for(String key:new String[]{"sha256","afterParagraph","anchor","title"})if(!x.optString(key).equals(y.optString(key)))return false;}return true;}
    public synchronized JSONObject importZip(Uri uri)throws Exception{
        File archive=File.createTempFile("publication-",".zip",context.getCacheDir());File folder=null;
        try{
            try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(archive)){copy(in,out,150L*1024*1024);}
            String fingerprint=sha(archive);try(Cursor cursor=getReadableDatabase().rawQuery("SELECT id FROM packages WHERE fingerprint=?",new String[]{fingerprint})){if(cursor.moveToFirst())return get(cursor.getString(0)).put("alreadyImported",true);}
            String id=UUID.randomUUID().toString();folder=root(id);if(!folder.mkdirs())throw new IOException("无法创建资料目录");long total=0;int count=0;
            try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive),StandardCharsets.UTF_8)){ZipEntry entry;Set<String> seen=new HashSet<>();while((entry=zip.getNextEntry())!=null){if(++count>2000)throw new IOException("资料包文件过多");File target=inside(folder,entry.getName());if(!seen.add(target.getCanonicalPath())||target.exists()&&!entry.isDirectory())throw new IOException("资料包含重复路径");if(entry.isDirectory()){target.mkdirs();continue;}target.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(target)){total+=copy(zip,out,Math.min(100L*1024*1024,400L*1024*1024-total));}zip.closeEntry();}}
            JSONObject manifest=new JSONObject(text(folder,"资料清单.json"));if(manifest.getInt("version")!=1)throw new IOException("不支持此版本的知流资料包");JSONArray source=manifest.getJSONArray("channels");if(source.length()<1||source.length()>10)throw new IOException("资料包缺少平台内容");
            JSONObject first=source.getJSONObject(0);String body=text(folder,first.getString("bodyFile"));JSONArray commonImages=images(folder,first.getJSONArray("illustrations")),channels=new JSONArray();Set<String> keys=new HashSet<>();
            for(int i=0;i<source.length();i++){JSONObject channel=source.getJSONObject(i);String key=channel.getString("channel");if(!key.matches("[a-z][a-z0-9_-]{0,39}")||!keys.add(key))throw new IOException("平台编号无效或重复");JSONObject normalized=new JSONObject().put("key",key).put("name",channel.getString("name")).put("title",text(folder,channel.getString("titleFile")).trim()).put("cover",image(folder,channel.getString("coverFile"))).put("guide",text(folder,channel.getString("guideFile")));String other=text(folder,channel.getString("bodyFile"));JSONArray otherImages=i==0?commonImages:images(folder,channel.getJSONArray("illustrations"));if(!body.equals(other))normalized.put("bodyOverride",other);if(!sameImages(commonImages,otherImages))normalized.put("illustrationsOverride",otherImages);channels.put(normalized);}
            JSONObject result=new JSONObject().put("id",id).put("sourceId",manifest.optString("creationId")).put("title",manifest.getString("articleTitle")).put("importedAt",System.currentTimeMillis()).put("common",new JSONObject().put("body",body).put("illustrations",commonImages)).put("channels",channels);
            if(manifest.has("revision"))result.put("revision",manifest.getJSONObject("revision"));
            if(first.has("articleFile"))result.put("articleMarkdown",text(folder,first.getString("articleFile")));
            ContentValues values=new ContentValues();values.put("id",id);values.put("fingerprint",fingerprint);values.put("created",result.getLong("importedAt"));values.put("payload",result.toString());getWritableDatabase().insertOrThrow("packages",null,values);folder=null;return result;
        }finally{archive.delete();if(folder!=null)deleteTree(folder);}
    }
    public synchronized JSONArray list()throws Exception{JSONArray result=new JSONArray();try(Cursor cursor=getReadableDatabase().rawQuery("SELECT payload FROM packages ORDER BY created DESC",null)){while(cursor.moveToNext()){JSONObject row=new JSONObject(cursor.getString(0)),common=row.getJSONObject("common");JSONArray channels=row.getJSONArray("channels"),names=new JSONArray();for(int i=0;i<channels.length();i++)names.put(channels.getJSONObject(i).getString("name"));result.put(new JSONObject().put("id",row.getString("id")).put("title",row.getString("title")).put("importedAt",row.getLong("importedAt")).put("platforms",names).put("images",common.getJSONArray("illustrations").length()));}}return result;}
    public synchronized JSONObject get(String id)throws Exception{root(id);try(Cursor cursor=getReadableDatabase().rawQuery("SELECT payload FROM packages WHERE id=?",new String[]{id})){if(!cursor.moveToFirst())throw new IOException("资料记录不存在");return new JSONObject(cursor.getString(0));}}
    public JSONObject asset(String id,String hash)throws Exception{if(!hash.matches("[a-f0-9]{64}"))throw new IOException("图片编号无效");JSONObject data=get(id);JSONArray all=new JSONArray();JSONArray channels=data.getJSONArray("channels");for(int i=0;i<channels.length();i++){JSONObject channel=channels.getJSONObject(i);all.put(channel.getJSONObject("cover"));JSONArray images=channel.optJSONArray("illustrationsOverride");if(images!=null)for(int j=0;j<images.length();j++)all.put(images.getJSONObject(j));}JSONArray shared=data.getJSONObject("common").getJSONArray("illustrations");for(int i=0;i<shared.length();i++)all.put(shared.getJSONObject(i));for(int i=0;i<all.length();i++)if(all.getJSONObject(i).getString("sha256").equals(hash))return all.getJSONObject(i);throw new IOException("图片不存在");}
    public File assetFile(String id,String hash)throws Exception{return inside(root(id),asset(id,hash).getString("file"));}
}
