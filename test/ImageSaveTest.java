package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.net.Uri;import android.database.Cursor;import android.provider.MediaStore;import org.json.*;import java.io.*;import java.util.*;import com.insight.quantlife.*;
/** Real private media -> MediaStore -> byte verification. Synthetic pixels, no provider requests. */
public final class ImageSaveTest extends DocumentEditorTest {
 final List<Uri> exports=new ArrayList<>();
 byte[] bytes(InputStream input)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
 @Override public void onStart(){Bundle result=new Bundle();try{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");JSONObject image=(JSONObject)h.call("storeRecordImage",new JSONObject().put("data",image()).put("name","合成AI插画.png"));String id=image.getString("id");byte[] original=bytes(new FileInputStream(RecordMedia.file(getTargetContext(),id)));
  for(int i=0;i<2;i++){
   JSONObject saved=(JSONObject)h.call("saveRecordImage",new JSONObject().put("id",id));Uri uri=Uri.parse(saved.getString("uri"));exports.add(uri);check(saved.getBoolean("saved")&&saved.getString("message").contains("相册"),"Shipping bridge reports completed gallery save "+i);
   check(Arrays.equals(original,bytes(getTargetContext().getContentResolver().openInputStream(uri))),"Export preserves exact private JPEG bytes without recompression "+i);
   try(Cursor cursor=getTargetContext().getContentResolver().query(uri,new String[]{MediaStore.Images.Media.MIME_TYPE,MediaStore.Images.Media.RELATIVE_PATH,MediaStore.Images.Media.IS_PENDING,MediaStore.Images.Media.DISPLAY_NAME},null,null,null)){check(cursor!=null&&cursor.moveToFirst()&&cursor.getString(0).equals("image/jpeg")&&cursor.getString(1).equals("Pictures/知时/")&&cursor.getInt(2)==0&&cursor.getString(3).endsWith(".jpg"),"Gallery item is published in Pictures/知时 with correct MIME/name "+i);}
  }
  check(!exports.get(0).equals(exports.get(1)),"Deliberate later save creates a separate item rather than overwriting another image");
  for(String bad:new String[]{"../secure.xml","https://example.com/private.jpg",new String(new char[64]).replace('\0','a')}){boolean refused=false;try{h.call("saveRecordImage",new JSONObject().put("id",bad));}catch(Exception expected){refused=true;}check(refused,"Invalid/missing local image cannot export: "+bad);}
  boolean failed=false;try{RecordImageExport.write(getTargetContext(),id,new OutputStream(){public void write(int b)throws IOException{throw new IOException("synthetic full storage");}});}catch(IOException expected){failed=true;}check(failed&&Arrays.equals(original,bytes(new FileInputStream(RecordMedia.file(getTargetContext(),id)))),"Destination write failure propagates and never changes private original");
  result.putString("stream","PASS ImageSaveTest: "+passed+" checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable ex){result.putString("stream","FAIL ImageSaveTest after "+passed+": "+ex+" cause="+ex.getCause()+"\n");finish(Activity.RESULT_CANCELED,result);}finally{for(Uri uri:exports)try{getTargetContext().getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}}
}
