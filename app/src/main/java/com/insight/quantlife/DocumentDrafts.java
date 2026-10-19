package com.insight.quantlife;
import android.content.*;import android.database.*;import android.database.sqlite.*;import org.json.*;
/** A recoverable editor draft, separate from committed records and AI/Wiki inputs. */
public final class DocumentDrafts {
 private static SQLiteDatabase open(Context c){SQLiteDatabase db=c.openOrCreateDatabase("quantlife-document-drafts.db",0,null);db.execSQL("CREATE TABLE IF NOT EXISTS drafts(id TEXT PRIMARY KEY,payload TEXT NOT NULL)");return db;}
 public static synchronized Object get(Context c)throws Exception{JSONArray out=new JSONArray();try(SQLiteDatabase db=open(c);Cursor cursor=db.rawQuery("SELECT payload FROM drafts ORDER BY rowid DESC",null)){while(cursor.moveToNext())out.put(new JSONObject(cursor.getString(0)));}return out;}
 public static synchronized boolean save(Context c,JSONObject draft)throws Exception{String id=draft.getJSONObject("record").getString("id");if(id.isEmpty()||id.length()>100)throw new Exception("草稿编号无效");try(SQLiteDatabase db=open(c)){ContentValues v=new ContentValues();v.put("id",id);v.put("payload",draft.toString());return db.insertWithOnConflict("drafts",null,v,SQLiteDatabase.CONFLICT_REPLACE)>=0;}}
 public static synchronized boolean clear(Context c,String id){try(SQLiteDatabase db=open(c)){db.delete("drafts","id=?",new String[]{id});return true;}}
 private DocumentDrafts(){}
}
