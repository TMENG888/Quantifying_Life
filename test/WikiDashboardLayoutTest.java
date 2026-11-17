package com.insight.quantlife.tests;
import android.app.*;import android.os.*;
/** Responsive chart check using synthetic local records; never sends model requests. */
public final class WikiDashboardLayoutTest extends DocumentEditorTest {
 void fits(String mode)throws Exception{
  check(h.js("document.documentElement.scrollWidth<=innerWidth+1&&[...document.querySelectorAll('.domain-row')].every(x=>{const r=x.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1&&r.height>=48})").equals("true"),mode+": chart fits viewport and preserves touch targets");
  check(h.js("[...document.querySelectorAll('.domain-row-heading strong,.domain-row-heading b')].every(x=>x.scrollWidth<=x.clientWidth+1)").equals("true"),mode+": Chinese labels and exact values remain visible");
 }
 @Override public void onStart(){Bundle out=new Bundle();try{
  if(!Build.HARDWARE.matches("ranchu|goldfish"))throw new Exception("Requires disposable emulator");h=new MainHost(this);h.open();h.js("AutoWiki.kick=()=>{};AutoWiki.resume=()=>{}");
  h.js("window.qaRecords=['AI 与技术','商业与管理','人文与阅读'].map((domain,i)=>({id:'layout-'+i,type:'learning',date:'2026-10-27',title:'阅读与学习的本地合成记录',note:'合成笔记',tags:[],updatedAt:1,wikiDomains:[domain],wikiDomainVersion:1,wikiClassificationVersion:3,durationMinutes:(i+1)*95}));window.qaStats=TopicStats.summarize(qaRecords,{range:'all'});window.qaRender=()=>{document.querySelector('#main').innerHTML=DomainDashboard.page(qaStats,'minutes');DomainDashboard.bind()};DomainDashboard.init({state:{records:qaRecords,recordTab:'learning'},esc:v=>String(v),minutes:n=>Math.floor(n/60)+'小时'+n%60+'分',render:qaRender});qaRender()");
  check(h.js("document.querySelectorAll('.domain-row').length===3").equals("true"),"Multi-domain chart contains all three classified sources");fits(phase);h.screenshot("wiki-chart-"+phase+".png");click("[data-domain-chart=bars]");fits(phase+" bars");h.screenshot("wiki-bars-"+phase+".png");click("[data-domain-chart=ring]");
  runOnMainSync(()->h.web.getSettings().setTextZoom(160));Thread.sleep(600);fits(phase+" large text");h.screenshot("wiki-chart-"+phase+"-large.png");click("[data-domain-chart=bars]");fits(phase+" bars large text");click("[data-domain-chart=ring]");
  check(h.js("(()=>{const c=document.querySelector('.domain-ring-center');return c.scrollHeight<=c.clientHeight+1&&c.scrollWidth<=c.clientWidth+1})()").equals("true"),phase+": enlarged ring total stays within its center");runOnMainSync(()->h.web.getSettings().setTextZoom(100));
  out.putString("stream","PASS WikiDashboardLayoutTest "+phase+": "+passed+" checks; CSS viewport "+h.js("[innerWidth,innerHeight]")+"; reduced motion "+h.js("matchMedia('(prefers-reduced-motion: reduce)').matches")+"\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable ex){try{if(h!=null){runOnMainSync(()->h.web.getSettings().setTextZoom(100));h.screenshot("wiki-layout-failure.png");}}catch(Exception ignored){}out.putString("stream","FAIL WikiDashboardLayoutTest: "+ex+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
