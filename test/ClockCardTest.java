package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;
/** Isolated synthetic AVD: real chat -> preview -> native persistence, mock model. */
public final class ClockCardTest extends WorkClockTest {
 void compact(String label)throws Exception{
  jsCheck("!!document.querySelector('.clock-candidate')&&!document.querySelector('.clock-candidate details')","Compact clock card has no irrelevant field diffs: "+label);
  jsCheck("document.querySelector('.clock-candidate h3').textContent==="+JSONObject.quote(label),"Action is clear: "+label);
  jsCheck("document.querySelector('.clock-card-time strong').textContent.length===8&&document.querySelector('.clock-card-time>span').textContent.length===10","Exact time and event date remain visible");
  jsCheck("!document.querySelector('.section-title')&&document.querySelectorAll('.chat-message').length>0","No repeated changes heading for clock-only preview");
  h.js("ChatUI.bottom()");Thread.sleep(450);
  jsCheck("(()=>{let r=document.querySelector('.clock-candidate').getBoundingClientRect(),b=document.querySelector('[data-confirm-change]').getBoundingClientRect(),c=document.querySelector('.composer').getBoundingClientRect();return r.height<240&&r.top>=0&&b.bottom<=c.top+1&&b.height>=44&&document.documentElement.scrollWidth<=innerWidth+1})()","Card and confirmation fit above composer without scrolling a long field list");
 }
 @Override public void onStart(){Bundle out=new Bundle();try{
  h=new MainHost(this);h.open();holdAutomation();
  // Only synthetic work records on this dedicated test AVD.
  JSONArray all=init().getJSONArray("records");for(int i=0;i<all.length();i++)if(all.getJSONObject(i).optString("type").equals("work"))h.call("deleteRecord",new JSONObject().put("id",all.getJSONObject(i).getString("id")));
  h.call("saveSettings",new JSONObject().put("settings",new JSONObject().put("profile",model("SYNTHETIC-ONLY"))));h.call("newChat",new JSONObject());reload();holdAutomation();clockMock();click("[data-page=chat]");
  sendText("上班了");compact("上班");check(active()==null,"Preview is not persisted before confirmation");
  jsCheck("Array.from(document.querySelectorAll('.chat-message')).at(-1).innerText.includes('请确认上班时间。')","Short purpose-specific assistant reply");h.screenshot("clock-compact-start.png");
  click("[data-drop-change]");check(active()==null,"Cancel never starts a shift");
  confirmClock("上班了");String id=active().getString("id");check(active()!=null,"Confirmed compact start persists actual shift");
  Thread.sleep(1100);sendText("开始休息");compact("开始休息");check(active().getJSONArray("breaks").length()==0,"Rest still awaits confirmation");click("[data-confirm-change]");until("!document.querySelector('[data-confirm-change]')");
  Thread.sleep(1100);sendText("结束休息");compact("结束休息");click("[data-confirm-change]");until("!document.querySelector('[data-confirm-change]')");check(!active().getJSONArray("breaks").getJSONObject(0).getString("end").isEmpty(),"Confirmed return closes actual rest");
  Thread.sleep(1100);sendText("下班了");compact("下班");click("[data-confirm-change]");until("!document.querySelector('[data-confirm-change]')");check(active()==null&&!find(id).getString("workEnd").isEmpty(),"Confirmed end retains one complete native shift");
  out.putString("stream","PASS ClockCardTest: "+passed+" checks; mock model only\n");finish(Activity.RESULT_OK,out);
 }catch(Throwable ex){try{h.screenshot("clock-card-failure.png");}catch(Exception ignored){}out.putString("stream","FAIL ClockCardTest after "+passed+": "+ex+"\n");finish(Activity.RESULT_CANCELED,out);}}
}
