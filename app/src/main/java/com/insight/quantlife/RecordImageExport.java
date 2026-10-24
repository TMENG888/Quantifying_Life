package com.insight.quantlife;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import org.json.JSONObject;

/** Copies only an existing app-private JPEG; never fetches or regenerates an image. */
public final class RecordImageExport {
 public static File source(Context c,String id)throws Exception{
  File file=RecordMedia.file(c,id);
  if(!file.isFile()||file.length()==0||file.length()>RecordMedia.IMAGE_LIMIT)throw new IOException("本机图片不存在或已损坏，未保存");
  return file;
 }
 public static String name(String id)throws Exception{
  if(!id.matches("[a-f0-9]{64}"))throw new IOException("图片编号无效");
  return "知时图片-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS",java.util.Locale.US).format(new java.util.Date())+"-"+id.substring(0,8)+".jpg";
 }
 public static void write(Context c,String id,OutputStream out)throws Exception{
  if(out==null)throw new IOException("无法写入所选保存位置");
  try(InputStream in=new FileInputStream(source(c,id))){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.flush();}
 }
 public static JSONObject toGallery(Context c,String id)throws Exception{
  source(c,id);if(Build.VERSION.SDK_INT<29)throw new IOException("请使用系统文件选择器保存图片");
  ContentResolver resolver=c.getContentResolver();ContentValues values=new ContentValues();
  values.put(MediaStore.Images.Media.DISPLAY_NAME,name(id));values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
  values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/知时");values.put(MediaStore.Images.Media.IS_PENDING,1);
  Uri uri=resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),values);
  if(uri==null)throw new IOException("无法创建相册图片，请检查存储空间");
  try{
   try(OutputStream out=resolver.openOutputStream(uri,"w")){write(c,id,out);}
   ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);
   if(resolver.update(uri,ready,null,null)!=1)throw new IOException("无法完成相册保存，请重试");
   return new JSONObject().put("saved",true).put("uri",uri.toString()).put("message","已保存到相册 · 知时（Pictures/知时）");
  }catch(Exception ex){try{resolver.delete(uri,null,null);}catch(Exception ignored){}throw ex;}
 }
 private RecordImageExport(){}
}
