package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;

/** Subpixel column widths must not accumulate into clipped line starts on later pages. */
public class ReaderClippingTest extends ReaderSaveTest {
 @Override public void onStart(){Bundle result=new Bundle();try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Synthetic tests require an emulator");
  h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  StringBuilder note=new StringBuilder();
  for(int i=0;i<65;i++)note.append("### 案例 ").append(i+1).append("：自动操作与学习记录\n\npyautogui.hotkey(\"win\", \"e\")\ntime.sleep(1)\n\n普通正文首字必须完整，工作与学习的笔记内容不能因为分页而消失。abcdefghijklmnopqrstuvwxyz\n\n```python\nimport pyautogui\npyautogui.typewrite(\"Project_2024_long_unbroken_code_text\", interval=0.05)\n```\n\n");
  JSONObject r=new JSONObject().put("id","reader-clipping-synthetic").put("type","learning").put("date","2026-10-27").put("title","阅读器首字完整性回归").put("note",note.toString()).put("durationMinutes",0).put("tags",new JSONArray());
  h.js("RecordEditor.open("+r.toString()+",null,'learning',true)");until("!!document.querySelector('#doc-flow p')");Thread.sleep(400);
  h.js("window.scanReaderEdges=()=>{const f=document.querySelector('#doc-flow'),v=document.querySelector('#doc-viewport'),box=v.getBoundingClientRect(),style=getComputedStyle(f),width=parseFloat(style.width),gap=parseFloat(style.columnGap),stride=width+gap;const rects=[],w=document.createTreeWalker(f,NodeFilter.SHOW_TEXT);let n;while(n=w.nextNode()){if(!n.textContent.trim()||n.parentElement.closest('pre,.table-scroll'))continue;const r=document.createRange();r.selectNodeContents(n);rects.push(...r.getClientRects());}for(const el of f.querySelectorAll('pre,.table-scroll,img'))rects.push(...el.getClientRects());const occupied=rects.filter(r=>r.width>0&&r.height>0&&r.bottom>box.top&&r.top<box.bottom);return {page:document.querySelector('#doc-page').textContent,viewport:box.width,client:v.clientWidth,width,stride,minLeft:Math.min(...occupied.filter(r=>r.right>box.left+1&&r.left<box.right-1).map(r=>r.left-box.left)),visible:occupied.filter(r=>r.right>box.left+1&&r.left<box.right-1).length,scroll:v.scrollLeft};};");
  for(String mode:new String[]{"native","fraction-up","fraction-down","large-text","reduced-motion"}){
   h.js("document.querySelector('#doc-viewport').style.width='';document.querySelector('.document-shell').style.setProperty('--doc-text-size','17px');");
   if(!mode.equals("native"))h.js("(()=>{const v=document.querySelector('#doc-viewport'),w=Math.floor(v.getBoundingClientRect().width)-1;v.style.width=(w+"+(mode.equals("fraction-down")?".25":".75")+")+ 'px';})()");
   if(mode.equals("large-text"))h.js("document.querySelector('.document-shell').style.setProperty('--doc-text-size','26px')");
   if(mode.equals("reduced-motion"))h.js("document.querySelector('#doc-flow').style.transition='none'");
   h.js("dispatchEvent(new Event('resize'))");Thread.sleep(400);
   // Batch layout checks, turning pages through the real buttons, with animation disabled.
   String scan=h.js("(()=>{const f=document.querySelector('#doc-flow');f.style.transition='none';while(!document.querySelector('#doc-prev').disabled)document.querySelector('#doc-prev').click();const pages=+document.querySelector('#doc-page').textContent.split('/')[1],samples=[];for(let p=0;p<pages;p++){samples.push(scanReaderEdges());if(p<pages-1)document.querySelector('#doc-next').click();}return {pages,minLeft:Math.min(...samples.map(s=>s.minLeft)),empty:samples.filter(s=>!s.visible).length,scrolled:samples.some(s=>s.scroll!==0),first:samples[0],last:samples[samples.length-1]};})()");
   log(mode+" "+scan);JSONObject data=new JSONObject(scan);
   if(!phase.equals("probe")){check(data.getDouble("minLeft")>=-.1,mode+": no clipped first letters across "+data.getInt("pages")+" pages");check(data.getInt("empty")==0&&!data.getBoolean("scrolled"),mode+": no blank pages or unwanted viewport scrolling");}
   if(mode.equals("native")||mode.equals("fraction-up")){h.js("while(+document.querySelector('#doc-page').textContent.split('/')[0]>12)document.querySelector('#doc-prev').click()");h.screenshot("reader-clipping-"+phase+"-"+mode+".png");}
  }
  h.js("document.querySelector('#doc-viewport').style.width='';document.querySelector('.document-shell').style.removeProperty('--doc-text-size');dispatchEvent(new Event('resize'))");Thread.sleep(400);close();
  result.putString("stream","PASS ReaderClippingTest "+phase+": "+passed+" checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable ex){try{if(h!=null)h.screenshot("reader-clipping-failure.png");}catch(Exception ignored){}result.putString("stream","FAIL ReaderClippingTest: "+ex+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
