(function(root){
 'use strict';
 const D=root.LifeDomain,$=id=>document.getElementById(id),MAX=7*86400000;
 let e,snapshot={active:null},anchor=0,lastSync=0,busy=false,ready=false,fault='',sequence=0,startId='',finishId='',titleDraft=null;
 const clock=ms=>{const n=Math.floor(Math.max(0,ms)/1000);return [Math.floor(n/3600),Math.floor(n/60)%60,n%60].map(v=>String(v).padStart(2,'0')).join(':');};
 function duration(){const s=snapshot.active;return s?Math.min(MAX,s.durationMs+(s.state==='running'?Math.max(0,performance.now()-anchor):0)):0;}
 function accept(value){const previous=snapshot.active?.id;snapshot=value||{active:null};anchor=lastSync=performance.now();ready=true;fault='';if(previous!==snapshot.active?.id)titleDraft=null;tick();}
 function visible(){return e.state.page==='study'||e.state.page==='today'||e.state.page==='learning'||e.state.page==='records'&&e.state.recordTab==='learning'&&!e.state.topicView;}
 function redraw(){if(visible()&&!$('editor')?.open)e.render();}
 async function refresh(){if(busy)return;const seq=++sequence;try{const value=await e.api('studyTimer',{command:'status'});if(seq!==sequence||busy)return;const changed=!ready||!!fault||snapshot.active?.id!==value.active?.id||snapshot.active?.state!==value.active?.state;accept(value);if(changed)redraw();}catch(err){if(seq!==sequence)return;fault=err.message;ready=false;redraw();}}
 function entry(){const s=snapshot.active;return `<button class="study-entry" data-study-open ${busy||!ready&&!fault?'disabled':''}><span class="study-entry-label"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="13" r="8"/><path d="M9 2h6M12 5v2M12 9v4l3 2"/></svg><span><strong>学习计时</strong><span id="study-entry-state">${fault?'计时读取失败，点击重试':s?s.state==='running'?'正在学习':'已暂停':'自动记录起止时间与时长'}</span></span></span><span class="study-entry-action" id="study-entry-clock">${s?clock(duration())+' ›':fault?'重试':!ready?'读取中…':'开始学习'}</span></button>`;}
 function stamp(ms){return D.localTime(new Date(ms)).replace('T',' ').slice(0,16);}
 function timingHTML(r){const t=r?.studyTiming;if(!t)return '';const sameDay=D.localDate(new Date(t.startedAt))===D.localDate(new Date(t.endedAt));return `<div class="study-record-time"><time>${e.esc(stamp(t.startedAt))} — ${e.esc(sameDay?stamp(t.endedAt).slice(11):stamp(t.endedAt))}</time><span>原始计时 ${clock(t.durationMs)}${t.recoveredAfterReboot?' · 曾因重启暂停':''}${t.limitReached?' · 达到7天上限':''}</span></div>`;}
 function page(){const s=snapshot.active;return `<section class="study-page"><header class="study-header"><button id="study-back" class="text-button">‹ 返回学习</button><h1>学习计时</h1></header><div class="study-panel"><p id="study-state" role="status">${fault?'读取失败，请重试':s?s.state==='running'?'正在学习':'已暂停':'准备开始学习'}</p><div id="study-clock" role="timer" aria-label="有效学习时长" aria-live="off">${clock(duration())}</div>${s?`<p class="study-started">开始于 ${e.esc(stamp(s.startedAt))}</p><label class="field study-title-field"><span>学习内容或链接 · 选填</span><input id="study-title" maxlength="300" placeholder="输入学习内容或粘贴文章、视频链接" value="${e.esc(titleDraft??s.title??'')}"></label>${s.recoveredAfterReboot?'<p class="study-notice">手机曾重启，已暂停至最后确认的时长。</p>':''}${s.limitReached?'<p class="study-notice">本次已达7天，请结束记录后重新开始。</p>':''}<div class="study-controls"><button id="study-toggle" class="secondary" ${busy||s.limitReached?'disabled':''}>${s.state==='running'?'暂停':'继续'}</button><button id="study-finish" class="primary" ${busy?'disabled':''}>结束并填写</button></div><button id="study-discard" class="text-button" ${busy?'disabled':''}>放弃本次计时</button>`:`<button id="study-start" class="primary" ${busy||!ready&&!fault?'disabled':''}>${fault?'重新读取':'开始学习'}</button>`}</div></section>`;}
 function tick(){if($('study-clock'))$('study-clock').textContent=clock(duration());if($('study-entry-clock')&&snapshot.active)$('study-entry-clock').textContent=clock(duration())+' ›';}
 function lock(){document.querySelectorAll('[data-study-open],#study-start,#study-toggle,#study-finish,#study-discard,#study-title').forEach(b=>b.disabled=busy||(b.id==='study-toggle'&&!!snapshot.active?.limitReached));}
 async function openDraft(result){
  let record=result.draft;
  if(!result.committed){
   const initial=(await e.api('getDocumentDraft')).find(d=>d.record?.id===record.id);
   if(!initial?.studySeed){if(initial)record=initial.record;e.go('learning');await root.RecordEditor.open(record,null,'learning');return;}
   const baseline=JSON.stringify(record);
   const enriched=await root.StudyLearning.enrich(record,record.studyInput||'',{api:e.api,modelConfigured:e.modelConfigured,settings:e.state.settings,progress:text=>{if($('study-state'))$('study-state').textContent=text;}});
   const drafts=await e.api('getDocumentDraft'),draft=drafts.find(d=>d.record?.id===record.id);
   if(draft&&JSON.stringify(draft.record)===baseline){record=enriched.record;await e.api('saveDocumentDraft',{draft:{...draft,record,studySeed:false,savedAt:Date.now()}});}
   else if(draft)record=draft.record;
   if(enriched.message)e.toast(enriched.message);
  }else e.state.records=await e.api('getRecords');
  e.go('learning');await root.RecordEditor.open(record,null,'learning');e.toast('学习时长已带入，填写后保存');
 }
 async function command(command){
  if(busy)return;if(command==='start'&&!ready){await refresh();return;}
  busy=true;sequence++;lock();const id=command==='start'?(startId||(startId=D.uid())):snapshot.active?.id||finishId;
  if(command==='finish')finishId=id;
  const title=$('study-title')?.value??titleDraft??snapshot.active?.title??'';titleDraft=title;
  let result;
  try{result=await e.api('studyTimer',{command,id,title});accept(result);if(command==='start')startId='';}
  catch(err){e.toast('操作未确认：'+err.message+'，可重试');try{accept(await e.api('studyTimer',{command:'status'}));}catch(ignored){} }
  finally{if(!result?.draft)busy=false;}
  if(result?.draft){try{await openDraft(result);finishId='';}finally{busy=false;lock();}}
  else if(result){if(command==='discard')finishId='';if(command==='start')e.go('study');else redraw();}
  else {
   // A lost reply may follow a persisted draft. Retry the same operation id,
   // never create a second record. Offer recovery on the empty timer screen.
   redraw();
  }
  lock();
 }
 function bind(){
  document.querySelectorAll('[data-study-open]').forEach(b=>b.onclick=()=>e.safe(async()=>{if(fault){await refresh();return;}if(snapshot.active)e.go('study');else if(finishId){await command('finish');}else await command('start');}));
  if(!$('study-back'))return;$('study-back').onclick=()=>e.go('learning');
  if($('study-start')){$('study-start').textContent=finishId?'继续填写学习记录':fault?'重新读取':'开始学习';$('study-start').onclick=()=>e.safe(()=>finishId?command('finish'):fault?refresh():command('start'));}
  if($('study-title'))$('study-title').oninput=ev=>{titleDraft=ev.target.value;};
  if($('study-toggle'))$('study-toggle').onclick=()=>e.safe(()=>command(snapshot.active?.state==='running'?'pause':'resume'));
  if($('study-finish'))$('study-finish').onclick=()=>e.safe(()=>command('finish'));
  if($('study-discard'))$('study-discard').onclick=()=>e.safe(async()=>{if(await e.confirm('放弃本次学习计时？','本次时长不会保存为学习记录。','放弃计时'))await command('discard');});tick();
 }
 function leave(){if(!busy&&snapshot.active&&titleDraft!==null&&titleDraft!==snapshot.active.title)e.api('studyTimer',{command:'title',id:snapshot.active.id,title:titleDraft}).catch(err=>e.toast('学习内容未保存：'+err.message));}
 function durationLabel(r){const n=Number(r.durationMinutes||0);return n>0&&n<1?Math.max(1,Math.round(n*60))+' 秒':Math.round(n)+' 分钟';}
 function init(env){e=env;setInterval(()=>{if(document.hidden)return;tick();if(!busy&&performance.now()-lastSync>15000){lastSync=performance.now();refresh();}},500);document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();else leave();});refresh();}
 root.StudyTimerUI={init,entry,page,bind,refresh,accept,leave,timingHTML,durationLabel,clock};
})(window);
