package com.insight.quantlife;

import android.content.Context;
import android.net.Uri;
import android.graphics.*;
import android.media.ExifInterface;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** App-private, content-addressed JPEGs. No remote fetches, original paths or EXIF export. */
public final class RecordMedia {
 public static boolean hasBody(String note){return !note.replaceAll("!\\[[^\\]\\n]*\\]\\(zhishi-media:[a-f0-9]{64}\\)","").replaceAll("[\\s#*_`>~\\-]","").isEmpty();}
 public static final int INPUT_LIMIT=20*1024*1024,IMAGE_LIMIT=4*1024*1024;
 private static File directory(Context c)throws IOException{File f=new File(c.getFilesDir(),"record-media");if(!f.isDirectory()&&!f.mkdirs())throw new IOException("无法创建图片目录");return f;}
 public static File file(Context c,String id)throws Exception{if(!id.matches("[a-f0-9]{64}"))throw new Exception("图片编号无效");return new File(directory(c),id+".jpg");}
 private static byte[] bytes(InputStream in,int max)throws Exception{if(in==null)throw new Exception("无法读取图片");try(InputStream s=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int n;while((n=s.read(buf))!=-1){if(out.size()+n>max)throw new Exception("图片或备份过大，请分批处理");out.write(buf,0,n);}return out.toByteArray();}}
 private static String hash(byte[] b)throws Exception{StringBuilder s=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
 private static JSONObject metadata(String id,String name,byte[] bytes)throws Exception{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(o.outWidth<1||o.outHeight<1||!"image/jpeg".equals(o.outMimeType)||o.outWidth>2400||o.outHeight>2400)throw new Exception("备份图片不是受支持的本地 JPEG");return new JSONObject().put("id",id).put("name",name==null?"图片":name.substring(0,Math.min(300,name.length()))).put("width",o.outWidth).put("height",o.outHeight).put("bytes",bytes.length);}
 private static void put(Context c,String id,byte[] b)throws Exception{File target=file(c,id);if(target.isFile())return;File temp=File.createTempFile("image-",".tmp",directory(c));try{try(FileOutputStream out=new FileOutputStream(temp)){out.write(b);out.getFD().sync();}if(!temp.renameTo(target)&&!target.isFile())throw new IOException("图片保存失败");}finally{if(temp.exists())temp.delete();}}
 public static JSONObject importImage(Context c,Uri uri,String name)throws Exception{return storeImage(c,bytes(c.getContentResolver().openInputStream(uri),INPUT_LIMIT),name);}
 public static JSONObject fromData(Context c,String data,String name)throws Exception{if(!data.startsWith("data:image/")||data.length()>INPUT_LIMIT*4L/3+200)throw new Exception("图片数据格式无效");int at=data.indexOf(";base64,");if(at<0)throw new Exception("图片编码无效");return storeImage(c,Base64.decode(data.substring(at+8),Base64.DEFAULT),name);}
 public static JSONObject fromBytes(Context c,byte[] source,String name)throws Exception{if(source.length>INPUT_LIMIT)throw new Exception("图片最大20 MB");return storeImage(c,source,name);}
 private static JSONObject storeImage(Context c,byte[] source,String name)throws Exception{
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(source,0,source.length,bounds);if(bounds.outWidth<1||bounds.outHeight<1)throw new Exception("无法解码图片，请换用 JPG 或 PNG");
  BitmapFactory.Options options=new BitmapFactory.Options();while(Math.max(bounds.outWidth,bounds.outHeight)/Math.max(1,options.inSampleSize)>2400)options.inSampleSize=Math.max(1,options.inSampleSize)*2;options.inSampleSize=Math.max(1,options.inSampleSize);
  Bitmap bitmap=BitmapFactory.decodeByteArray(source,0,source.length,options);if(bitmap==null)throw new Exception("无法读取这张图片");
  try{
   int orientation=ExifInterface.ORIENTATION_NORMAL;try{orientation=new ExifInterface(new ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
   Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;}
   if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}}
   double scale=Math.min(1,2400.0/Math.max(bitmap.getWidth(),bitmap.getHeight()));if(scale<1){Bitmap small=Bitmap.createScaledBitmap(bitmap,(int)(bitmap.getWidth()*scale),(int)(bitmap.getHeight()*scale),true);bitmap.recycle();bitmap=small;}
   // Flatten transparency on white; decode/re-encode also strips GPS/EXIF metadata.
   Bitmap flat=Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.RGB_565);Canvas canvas=new Canvas(flat);canvas.drawColor(Color.WHITE);canvas.drawBitmap(bitmap,0,0,null);
   byte[] jpeg;try(ByteArrayOutputStream out=new ByteArrayOutputStream()){flat.compress(Bitmap.CompressFormat.JPEG,88,out);jpeg=out.toByteArray();}finally{flat.recycle();}
   if(jpeg.length>IMAGE_LIMIT)throw new Exception("处理后的图片仍超过 4 MB，请裁剪后插入");String id=hash(jpeg);JSONObject result=metadata(id,name,jpeg);put(c,id,jpeg);return result;
  }finally{bitmap.recycle();}
 }
 public static void validate(Context c,JSONObject r)throws Exception{JSONArray list=r.optJSONArray("images");if(r.has("images")&&list==null)throw new Exception("记录图片格式无效");if(list==null)return;if(list.length()>100)throw new Exception("每条记录最多 100 张图片");Set<String> ids=new HashSet<>();for(int i=0;i<list.length();i++){JSONObject image=list.getJSONObject(i);String id=image.getString("id");if(!ids.add(id)||!file(c,id).isFile())throw new Exception("记录图片缺失或重复，请重新插入／恢复含图片的备份");if(image.optString("name").length()>300)throw new Exception("图片名称过长");}}
 public static JSONArray exportImages(Context c,JSONArray records)throws Exception{Set<String> ids=new LinkedHashSet<>();for(int i=0;i<records.length();i++){JSONArray list=records.getJSONObject(i).optJSONArray("images");if(list!=null)for(int j=0;j<list.length();j++)ids.add(list.getJSONObject(j).getString("id"));}JSONArray out=new JSONArray();long total=0;for(String id:ids){byte[] b=bytes(new FileInputStream(file(c,id)),IMAGE_LIMIT);total+=b.length;if(total>128L*1024*1024)throw new Exception("图片备份超过 128 MB，请先分批备份图片记录");out.put(new JSONObject().put("id",id).put("data",Base64.encodeToString(b,Base64.NO_WRAP)));}return out;}
 public static void restoreImages(Context c,JSONArray media)throws Exception{if(media==null)return;long total=0;Set<String> ids=new HashSet<>();for(int i=0;i<media.length();i++){JSONObject image=media.getJSONObject(i);String id=image.getString("id"),data=image.getString("data");if(data.length()>IMAGE_LIMIT*4L/3+8||!ids.add(id))throw new Exception("备份图片过大或重复");byte[] b=Base64.decode(data,Base64.DEFAULT);total+=b.length;if(total>128L*1024*1024||!hash(b).equals(id))throw new Exception("备份图片校验失败");metadata(id,"图片",b);file(c,id);}
  // Validate every entry first. Never replace an existing content-addressed image.
  for(int i=0;i<media.length();i++){JSONObject image=media.getJSONObject(i);put(c,image.getString("id"),Base64.decode(image.getString("data"),Base64.DEFAULT));}
 }
 private RecordMedia(){}
}
