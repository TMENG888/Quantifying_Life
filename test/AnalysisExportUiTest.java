package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import android.content.*;import android.view.accessibility.AccessibilityNodeInfo;
import com.insight.quantlife.*;import org.json.*;import java.util.*;

/** Actual WebView taps + Android DocumentsUI using emulator-only synthetic records. */
public class AnalysisExportUiTest extends DocumentEditorTest {
 final String prefix="analysis-ui-fixture-";String name="";JSONArray before;
 void removeFixtures()throws Exception{for(String id:new String[]{"old","new","log","idea"})h.call("deleteRecord",new JSONObject().put("id",prefix+id));}
 void reload()throws Exception{runOnMainSync(()->h.activity.finish());h=new MainHost(this);h.open();until("typeof document.querySelector('[data-page=records]').onclick==='function'");h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");}
 void dashboard()throws Exception{click("[data-page=records]");click("#topic-entry");until("!!document.querySelector('#analysis-export')");h.js("document.querySelector('#analysis-export').scrollIntoView({block:'center'})");Thread.sleep(250);}
 AccessibilityNodeInfo find(AccessibilityNodeInfo node,String word){if(node==null)return null;CharSequence text=node.getText();if(text!=null&&text.toString().equalsIgnoreCase(word)&&node.isVisibleToUser())return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo v=find(node.getChild(i),word);if(v!=null)return v;}return null;}
 void picker(String state)throws Exception{for(int i=0;i<100;i++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null&&root.getPackageName()!=null&&root.getPackageName().toString().contains("documentsui"))return;Thread.sleep(100);}throw new Exception("System picker did not open: "+state);}
 boolean choose(String text){AccessibilityNodeInfo item=find(getUiAutomation().getRootInActiveWindow(),text);for(int i=0;item!=null&&i<4;i++,item=item.getParent())if(item.isClickable())return item.performAction(AccessibilityNodeInfo.ACTION_CLICK);return false;}
 void layout(String suffix)throws Exception{
  h.js("document.querySelector('#analysis-export').scrollIntoView({block:'center'})");Thread.sleep(250);
  check(h.js("document.documentElement.scrollWidth<=innerWidth+1").equals("true"),"Export layout has no horizontal overflow: "+suffix);
  check(h.js("(()=>{const b=document.querySelector('#analysis-export'),r=b.getBoundingClientRect();return r.height>=48&&r.width>=144&&r.left>=0&&r.right<=innerWidth})()").equals("true"),"Export button is readable, within viewport and at least 48px: "+suffix);
  check(h.js("document.querySelector('#analysis-export-status').getAttribute('aria-live')==='polite'").equals("true"),"Accessible export progress: "+suffix);
  h.screenshot("analysis-export-"+suffix+".png");
  h.js("document.querySelector('#analysis-export-status').scrollIntoView({block:'end'});window.scrollBy(0,200)");Thread.sleep(200);
  check(h.js("(()=>{const r=document.querySelector('#analysis-export-status').getBoundingClientRect(),n=document.querySelector('.bottom-nav').getBoundingClientRect();return r.bottom<=n.top+1&&r.top>=0})()").equals("true"),"Export status can scroll clear of fixed navigation: "+suffix);
 }
 @Override public void onStart(){Bundle result=new Bundle();try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Disposable emulator required");
  h=new MainHost(this);h.open();until("typeof document.querySelector('[data-page=records]').onclick==='function'");h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  if(phase.equals("layout")){
   dashboard();runOnMainSync(()->h.web.getSettings().setTextZoom(200));layout("large-text");
   runOnMainSync(()->h.web.getSettings().setTextZoom(100));layout("responsive");
  }else{
   removeFixtures();for(String kind:new String[]{"old","new","log","idea"}){
    JSONObject record=new JSONObject().put("id",prefix+kind).put("type",kind.equals("log")?"work_note":kind.equals("idea")?"idea":"learning")
     .put("date",kind.equals("old")?"2001-01-02":"2026-10-28").put("title","测试导出："+kind).put("note","真实保存的合成笔记："+kind+"\n**正文不会丢失**").put("durationMinutes",kind.equals("old")?45:0).put("tags",new JSONArray().put("测试标签"));
    if(kind.equals("idea"))record.put("occurredAt","2026-10-28T12:00:00");
    h.call("saveRecord",new JSONObject().put("record",record).put("allowDuplicate",true));
   }
   before=records();reload();dashboard();layout("portrait");
   h.tap("#analysis-export",false);picker("first");
   JSONObject waiting=AnalysisExportManager.status(getTargetContext());check(waiting.getString("state").equals("waiting"),"Real button opens Android save picker after staging snapshot");
   sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
   until("!document.querySelector('#analysis-export').disabled && document.querySelector('#analysis-export-status').textContent.includes('已取消')");
   check(AnalysisExportManager.status(getTargetContext()).getString("state").equals("cancelled"),"System picker cancellation returns to enabled export button");
   h.js("document.querySelector('#analysis-export').scrollIntoView({block:'center'})");h.tap("#analysis-export",false);picker("second");name=AnalysisExportManager.status(getTargetContext()).getString("fileName");
   check(choose("Save")||choose("保存"),"Android save control can be activated");
   until("!document.querySelector('#analysis-export').disabled && document.querySelector('#analysis-export-status').textContent.includes('已保存')");
   check(AnalysisExportManager.status(getTargetContext()).getString("state").equals("complete"),"Real system save completes and re-enables export");
   check(records().toString().equals(before.toString()),"Saving and cancelling export leave original records unchanged");
   reload();dashboard();check(h.js("document.querySelector('#analysis-export-status').textContent.includes('已保存')").equals("true"),"Export result survives activity recreation");
   layout("saved");removeFixtures();result.putString("fileName",name);
  }
  result.putString("stream","PASS AnalysisExportUiTest "+phase+": "+passed+" checks\nEXPORTED_FILE="+name+"\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable ex){try{if(h!=null)h.screenshot("analysis-export-ui-failure.png");}catch(Exception ignored){}result.putString("stream","FAIL AnalysisExportUiTest after "+passed+": "+ex+"\n"+android.util.Log.getStackTraceString(ex));finish(Activity.RESULT_CANCELED,result);}}
}
