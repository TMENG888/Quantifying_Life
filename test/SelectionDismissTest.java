package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.view.*;import org.json.*;
/** Native long-press, real WebView ActionMode, then the illustration toolbar. */
public final class SelectionDismissTest extends DocumentEditorTest {
 @Override public void onStart(){Bundle out=new Bundle();try{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");open("learning");input("选区隐藏实测","目录索引和知识主题关联，共同帮助我们回到原文证据。");
  h.js("document.querySelector('#doc-body').focus()");Thread.sleep(600);JSONArray xy=new JSONArray(h.js("(()=>{const b=document.querySelector('#doc-body'),r=document.createRange();r.setStart(b.firstChild,2);r.setEnd(b.firstChild,3);const a=r.getBoundingClientRect();return [(a.left+a.right)/2,(a.top+a.bottom)/2,innerWidth]})()"));float x=(float)(xy.getDouble(0)*h.web.getWidth()/xy.getDouble(2)),y=(float)(xy.getDouble(1)*h.web.getWidth()/xy.getDouble(2));long down=SystemClock.uptimeMillis();
  runOnMainSync(()->{MotionEvent m=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);h.web.dispatchTouchEvent(m);m.recycle();});Thread.sleep(950);runOnMainSync(()->{MotionEvent m=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);h.web.dispatchTouchEvent(m);m.recycle();});Thread.sleep(400);
  check(h.js("getSelection().toString().trim().length>0").equals("true"),"Actual native long-press selects editor text");java.lang.reflect.Field action=h.activity.getClass().getDeclaredField("editorActionMode");action.setAccessible(true);check(action.get(h.activity)!=null,"WebView creates real Android floating text menu (ActionMode)");h.screenshot("selection-native-before.png");
  h.tap("#doc-illustrate",false);until("!!document.querySelector('#illustration-prompt')");Thread.sleep(500);check(h.js("getSelection().rangeCount===0").equals("true")&&action.get(h.activity)==null,"Opening illustration dismisses actual native menu, selection and handles");h.screenshot("selection-native-after.png");
  out.putString("stream","PASS SelectionDismissTest: "+passed+" checks\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable ex){out.putString("stream","FAIL SelectionDismissTest after "+passed+": "+ex+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
