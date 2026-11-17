package com.insight.quantlife;
import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.List;

public final class PublicationProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private String[] parts(Uri uri)throws FileNotFoundException{List<String> p=uri.getPathSegments();if(p.size()!=2)throw new FileNotFoundException("图片地址无效");return p.toArray(new String[0]);}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("只允许读取图片");try(PublicationLibrary library=new PublicationLibrary(getContext())){String[] p=parts(uri);return ParcelFileDescriptor.open(library.assetFile(p[0],p[1]),ParcelFileDescriptor.MODE_READ_ONLY);}catch(Exception e){throw new FileNotFoundException("图片不可读取");}}
    @Override public String getType(Uri uri){try(PublicationLibrary library=new PublicationLibrary(getContext())){String[] p=parts(uri);return library.asset(p[0],p[1]).getString("mime");}catch(Exception e){return null;}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try(PublicationLibrary library=new PublicationLibrary(getContext())){String[] p=parts(uri);File file=library.assetFile(p[0],p[1]);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)row[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?file.getName():OpenableColumns.SIZE.equals(columns[i])?file.length():null;cursor.addRow(row);return cursor;}catch(Exception e){return null;}}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
