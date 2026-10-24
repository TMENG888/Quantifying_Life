package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;
/** Real finger taps with Android keyboard, not just synthetic DOM clicks. */
public class ReaderNativeTest extends ReaderSaveTest {
 @Override public void onStart(){Bundle result=new Bundle();try{
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");metrics();open("experience");input("真机交互模拟 "+System.currentTimeMillis(),"第一段：编辑完成后点勾，应保存并进入阅读模式。\n\n第二段：不能返回首页，也不能读到空白页。");Thread.sleep(600);int fullHeight=Integer.parseInt(h.js("innerHeight"));
  h.tap("#doc-body",false);Thread.sleep(700);log("native keyboard: height "+h.js("innerHeight")+" / "+fullHeight);endCaret();h.tap("#doc-save",false);until("document.querySelector('#doc-mode').textContent==='编辑'&&!document.querySelector('#doc-save').disabled");Thread.sleep(700);check(h.js("document.querySelector('#editor').open&&!document.querySelector('#doc-read').hidden&&readerMetrics().visible>0").equals("true"),"Real checkmark tap with keyboard saves into occupied reader");check(Integer.parseInt(h.js("innerHeight"))>=fullHeight-2,"Reading mode dismisses keyboard and restores viewport height");h.screenshot("reader-native-save.png");
  h.tap("#doc-mode",false);Thread.sleep(200);h.tap("#doc-body",false);Thread.sleep(400);endCaret();h.tap("#doc-mode",false);Thread.sleep(700);check(h.js("readerMetrics().visible>0&&getSelection().rangeCount===0").equals("true"),"Native edit/read toggle clears editor selection and shows content");close();
  result.putString("stream","PASS ReaderNativeTest: "+passed+" checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable err){try{if(h!=null)h.screenshot("reader-native-failure.png");}catch(Exception ignored){}result.putString("stream","FAIL ReaderNativeTest after "+passed+": "+err+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
