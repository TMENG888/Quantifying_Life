package com.insight.quantlife;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/** Read-only MediaStore integration. No copies of originals, network calls or AI ingestion. */
public final class TrackPhotos {
    private static boolean allowed(Context c,String p){return c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    public static boolean readable(Context c){return Build.VERSION.SDK_INT>=33?allowed(c,Manifest.permission.READ_MEDIA_IMAGES)||(Build.VERSION.SDK_INT>=34&&allowed(c,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)):allowed(c,Manifest.permission.READ_EXTERNAL_STORAGE);}
    public static boolean enabled(Context c){return c.getSharedPreferences("track-photos",Context.MODE_PRIVATE).getBoolean("enabled",false);}
    public static JSONObject status(Context c)throws Exception {
        return new JSONObject().put("enabled",enabled(c)).put("readable",readable(c)).put("limited",Build.VERSION.SDK_INT>=34&&!allowed(c,Manifest.permission.READ_MEDIA_IMAGES)&&allowed(c,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)).put("locationReadable",Build.VERSION.SDK_INT<29||allowed(c,Manifest.permission.ACCESS_MEDIA_LOCATION));
    }
    public static void configure(Activity c,boolean enable){
        c.getSharedPreferences("track-photos",Context.MODE_PRIVATE).edit().putBoolean("enabled",enable).apply();
        if(!enable)return;
        java.util.ArrayList<String> permissions=new java.util.ArrayList<>();
        if(!readable(c))permissions.add(Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE);
        if(Build.VERSION.SDK_INT>=34)permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        if(Build.VERSION.SDK_INT>=29&&!allowed(c,Manifest.permission.ACCESS_MEDIA_LOCATION))permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
        if(!permissions.isEmpty())c.requestPermissions(permissions.toArray(new String[0]),243);
    }
    private static Uri original(Context c,Uri uri){return Build.VERSION.SDK_INT>=29&&allowed(c,Manifest.permission.ACCESS_MEDIA_LOCATION)?MediaStore.setRequireOriginal(uri):uri;}
    private static JSONObject metadata(Context c,android.database.sqlite.SQLiteDatabase index,String id,long modified,long size)throws Exception{
        boolean gps=Build.VERSION.SDK_INT<29||allowed(c,Manifest.permission.ACCESS_MEDIA_LOCATION);
        String key=id+":"+modified+":"+size+":"+gps;
        try(Cursor cursor=index.rawQuery("SELECT payload FROM photo_metadata WHERE cache_key=?",new String[]{key})){if(cursor.moveToFirst())return new JSONObject(cursor.getString(0));}
        JSONObject result=new JSONObject();Uri uri=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,Long.parseLong(id));
        try(InputStream in=c.getContentResolver().openInputStream(original(c,uri))){
            ExifInterface exif=new ExifInterface(in);float[] ll=new float[2];
            if(gps&&exif.getLatLong(ll)&&Float.isFinite(ll[0])&&Float.isFinite(ll[1])&&Math.abs(ll[0])<=85.051129&&Math.abs(ll[1])<=180)result.put("lat",(double)ll[0]).put("lon",(double)ll[1]);
            String value=exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);if(value==null)value=exif.getAttribute(ExifInterface.TAG_DATETIME);
            if(value!=null){java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss",java.util.Locale.US);format.setLenient(false);java.text.ParsePosition position=new java.text.ParsePosition(0);java.util.Date date=format.parse(value,position);if(date!=null&&position.getIndex()==value.length()&&date.getTime()>0)result.put("captureTime",date.getTime());}
        }catch(Exception ignored){result.put("metadataUnavailable",true);}
        // Cache only successful reads. Cloud-only or still-being-written photos may become available later.
        if(!result.optBoolean("metadataUnavailable")){android.content.ContentValues values=new android.content.ContentValues();values.put("cache_key",key);values.put("media_id",id);values.put("payload",result.toString());index.delete("photo_metadata","media_id=?",new String[]{id});index.insertWithOnConflict("photo_metadata",null,values,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);}
        return result;
    }
    public static JSONObject day(Context c,String date)throws Exception {
        long start=TrackStore.dayStart(date),end=TrackStore.dayEnd(date);
        JSONObject out=status(c);JSONArray photos=new JSONArray();out.put("photos",photos).put("date",date);
        if(!enabled(c)||!readable(c))return out;
        String taken=MediaStore.Images.Media.DATE_TAKEN,added=MediaStore.Images.Media.DATE_ADDED;
        // Some providers leave DATE_TAKEN empty even when JPEG EXIF has the original capture time.
        // Resolve those via a local incremental metadata cache, never by inventing a date or position.
        String where="("+taken+">=? AND "+taken+"<?) OR "+taken+" IS NULL OR "+taken+"=0";
        String[] args={String.valueOf(start),String.valueOf(end)};
        String[] columns={MediaStore.Images.Media._ID,taken,added,MediaStore.Images.Media.DATE_MODIFIED,MediaStore.Images.Media.SIZE};
        try(android.database.sqlite.SQLiteDatabase index=c.openOrCreateDatabase("track-photo-index.db",Context.MODE_PRIVATE,null);Cursor cursor=c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,columns,where,args,taken+" ASC")){
            index.execSQL("CREATE TABLE IF NOT EXISTS photo_metadata(cache_key TEXT PRIMARY KEY,media_id TEXT NOT NULL,payload TEXT NOT NULL)");
            if(cursor==null)return out;
            while(cursor.moveToNext()){
                if(Thread.currentThread().isInterrupted())break;
                if(photos.length()>=500){out.put("truncated",true);break;}
                String id=cursor.getString(0);JSONObject meta=metadata(c,index,id,cursor.getLong(3),cursor.getLong(4));long capture=cursor.getLong(1);if(capture<=0)capture=meta.optLong("captureTime");long time=capture>0?capture:cursor.getLong(2)*1000;
                if(time<start||time>=end)continue;
                JSONObject photo=new JSONObject().put("id",id).put("time",time).put("timeSource",capture>0?"taken":"added");
                if(meta.has("lat"))photo.put("lat",meta.getDouble("lat")).put("lon",meta.getDouble("lon")).put("locationSource","photo_exif");
                if(meta.optBoolean("metadataUnavailable"))photo.put("metadataUnavailable",true);
                photos.put(photo);
            }
        }catch(SecurityException error){return status(c).put("photos",new JSONArray()).put("date",date);}
        return out;
    }
    public static byte[] image(Context c,String id,boolean full)throws Exception {
        if(!enabled(c)||!readable(c)||!id.matches("[0-9]{1,19}"))throw new Exception("未获得照片访问权限");
        Uri uri=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,Long.parseLong(id));
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        int limit=full?1600:160;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>limit*2)options.inSampleSize*=2;options.inJustDecodeBounds=false;
        Bitmap bitmap;try(InputStream in=c.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,options);}if(bitmap==null)throw new Exception("照片已移动、删除或暂不可用");
        try{
            int orientation=ExifInterface.ORIENTATION_NORMAL;try(InputStream in=c.getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(Exception ignored){}
            Matrix transform=new Matrix();switch(orientation){case 2:transform.setScale(-1,1);break;case 3:transform.setRotate(180);break;case 4:transform.setScale(1,-1);break;case 5:transform.setRotate(90);transform.postScale(-1,1);break;case 6:transform.setRotate(90);break;case 7:transform.setRotate(-90);transform.postScale(-1,1);break;case 8:transform.setRotate(-90);break;default:break;}
            if(!transform.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),transform,true);if(rotated!=bitmap){bitmap.recycle();bitmap=rotated;}}
            double scale=Math.min(1,limit/(double)Math.max(bitmap.getWidth(),bitmap.getHeight()));
            if(scale<1){Bitmap resized=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),Math.max(1,(int)(bitmap.getHeight()*scale)),true);if(resized!=bitmap){bitmap.recycle();bitmap=resized;}}
            ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,full?90:78,out);return out.toByteArray();
        }finally{bitmap.recycle();}
    }
}
