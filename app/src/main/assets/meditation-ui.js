(function(){
  'use strict';
  const $=id=>document.getElementById(id),D=LifeDomain;
  let e,snapshot={active:null,sessions:[]},anchor=0,busy=false,view='timer',saved=null,sequence=0,lastSync=0;
  let date=D.localDate(),month=date.slice(0,7),requestId='';
  const drafts=new Map();
  function clock(ms){const seconds=Math.floor(Math.max(0,ms)/1000),hours=Math.floor(seconds/3600);return (hours?String(hours).padStart(2,'0')+':':'')+[Math.floor(seconds/60)%60,seconds%60].map(x=>String(x).padStart(2,'0')).join(':');}
  function duration(s){return s?s.durationMs+(s.state==='running'?Math.max(0,performance.now()-anchor):0):0;}
  function label(ms){const seconds=Math.floor(ms/1000);return seconds>=60?Math.floor(seconds/60)+' 分 '+seconds%60+' 秒':seconds+' 秒';}
  function accept(value){snapshot=value||{active:null,sessions:[]};anchor=performance.now();lastSync=anchor;tick();}
  async function refresh(){if(busy)return;const id=++sequence;try{const value=await e.api('meditation',{command:'status'});if(id===sequence&&!busy){const changed=snapshot.active?.id!==value.active?.id||snapshot.active?.state!==value.active?.state;accept(value);if(changed&&e.state.page==='meditation')e.render();}}catch(error){if($('meditation-root'))e.toast(error.message);}}
  function init(env){e=env;setInterval(()=>{if(document.hidden)return;tick();if(!busy&&performance.now()-lastSync>15000){lastSync=performance.now();refresh();}},500);document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});refresh();}
  function entry(){const s=snapshot.active,today=snapshot.sessions.filter(x=>D.localDate(new Date(x.startedAt))===D.localDate()).reduce((n,x)=>n+x.durationMs,0);return `<button class="meditation-entry" data-go="meditation"><span><strong>禅定</strong><span id="meditation-entry-status">${s?s.state==='running'?'进行中':'已暂停':today?'今日 '+label(today):'给自己一段安静的时间'}</span></span><span class="meditation-entry-value" id="meditation-entry-value">${s?clock(duration(s)):'开始'}<span aria-hidden="true"> ›</span></span></button>`;}
  function tick(){const s=snapshot.active;if($('meditation-clock'))$('meditation-clock').textContent=clock(duration(s));if($('meditation-entry-value')){$('meditation-entry-value').textContent=(s?clock(duration(s)):'开始')+' ›';const today=snapshot.sessions.filter(x=>D.localDate(new Date(x.startedAt))===D.localDate()).reduce((n,x)=>n+x.durationMs,0);$('meditation-entry-status').textContent=s?s.state==='running'?'进行中':'已暂停':today?'今日 '+label(today):'给自己一段安静的时间';}}
  function page(){
    const top=`<header class="meditation-header"><button class="text-button" id="meditation-back">‹ 返回</button><h1>禅定</h1><button class="text-button" id="meditation-history">${view==='history'?'计时':'历史'}</button></header>`;
    if(view==='history')return `<section id="meditation-root">${top}${history()}</section>`;
    if(view==='result'&&saved)return `<section id="meditation-root">${top}<div class="meditation-result"><p class="meditation-kicker">已记录</p><h2>留一刻，给自己。</h2><strong class="meditation-result-time">${clock(saved.durationMs)}</strong><p>${time(saved.startedAt)} — ${time(saved.endedAt)}</p></div>${noteForm(saved)}<button class="secondary full" id="meditation-done">完成</button><button class="meditation-delete-record" data-meditation-delete="${e.esc(saved.id)}" ${busy?'disabled':''}>删除这条记录</button></section>`;
    const s=snapshot.active;return `<section id="meditation-root" class="meditation-timer">${top}<div class="meditation-space"><p class="meditation-kicker" id="meditation-state">${s?s.state==='running'?'正在禅定':'已暂停':'不设终点，只记当下'}</p><div class="meditation-orbit"><span id="meditation-clock" role="timer" aria-label="禅定已用时间" aria-live="off">${clock(duration(s))}</span><span>${s?'已用时间':'准备好，就开始'}</span></div>${s?.recoveredAfterReboot&&s.state==='paused'?'<p class="meditation-recovery" role="status">手机曾重启，已暂停至最后确认的时长。</p>':''}<div class="meditation-controls">${s?`<button class="primary" id="meditation-toggle" ${busy?'disabled':''}>${s.state==='running'?'暂停':'继续'}</button><button class="text-button" id="meditation-end" ${busy?'disabled':''}>结束并记录</button>`:`<button class="primary" id="meditation-start" ${busy?'disabled':''}>开始禅定</button>`}</div></div></section>`;
  }
  function time(t){return new Date(t).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false});}
  function noteForm(s){return `<form id="meditation-note-form" data-id="${e.esc(s.id)}"><label class="field"><span>此刻的感受 · 选填</span><textarea id="meditation-note" rows="4" placeholder="留下一句话，也可以什么都不写。">${e.esc(drafts.has(s.id)?drafts.get(s.id):s.note||'')}</textarea></label><button class="primary full" type="submit">保存感受</button></form>`;}
  function history(){
    const sessions=[...snapshot.sessions].sort((a,b)=>b.startedAt-a.startedAt),group=sessions.filter(s=>D.localDate(new Date(s.startedAt)).startsWith(month)),selected=sessions.filter(s=>D.localDate(new Date(s.startedAt))===date),counts={};for(const s of group){const day=D.localDate(new Date(s.startedAt));counts[day]=(counts[day]||0)+1;}
    const first=new Date(month+'-01T12:00:00'),offset=(first.getDay()+6)%7,days=new Date(first.getFullYear(),first.getMonth()+1,0).getDate();
    return `<div class="meditation-history-summary"><div><strong>${label(group.reduce((n,s)=>n+s.durationMs,0))}</strong><span>当月累计</span></div><div><strong>${group.length}</strong><span>禅定次数</span></div></div><label class="field"><span>查看月份</span><input id="meditation-month" type="month" value="${month}"></label><div class="meditation-calendar" aria-label="禅定日历">${['一','二','三','四','五','六','日'].map(x=>`<span class="meditation-weekday">${x}</span>`).join('')}${'<span aria-hidden="true"></span>'.repeat(offset)}${Array.from({length:days},(_,i)=>{const day=month+'-'+String(i+1).padStart(2,'0');return `<button data-meditation-day="${day}" aria-pressed="${day===date}" aria-label="${day}${counts[day]?'，'+counts[day]+' 次禅定':''}">${i+1}<span aria-hidden="true">${counts[day]?'·':''}</span></button>`;}).join('')}</div><div class="section-title"><h2>${date}</h2><span class="count">${selected.length} 次</span></div>${selected.length?selected.map(s=>`<div class="meditation-session-row" data-motion-key="meditation-${e.esc(s.id)}"><button class="meditation-session" data-meditation-session="${e.esc(s.id)}"><span><strong>${time(s.startedAt)} — ${time(s.endedAt)}</strong>${s.note?`<span>${e.esc(s.note)}</span>`:''}</span><b>${clock(s.durationMs)}</b></button><button class="meditation-delete-session" data-meditation-delete="${e.esc(s.id)}" aria-label="删除 ${e.esc(date)} ${time(s.startedAt)} 的禅定记录" ${busy?'disabled':''}><svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5"></path></svg></button></div>`).join(''):'<div class="empty">这一天还没有禅定记录</div>'}`;
  }
  function lockControls(){document.querySelectorAll('.meditation-controls button,#meditation-note-form button,[data-meditation-delete]').forEach(b=>b.disabled=busy);}
  async function remove(id){
    if(busy)return;const session=snapshot.sessions.find(s=>s.id===id);if(!session)return;
    busy=true;sequence++;lockControls();let removed=false;
    const dialog=$('confirm-dialog');
    try{
      const answer=e.confirm('删除这条禅定记录？',`${D.localDate(new Date(session.startedAt))} ${time(session.startedAt)} — ${time(session.endedAt)} · ${label(session.durationMs)}。本次时长和感受将一并删除，无法撤销。`,'删除');
      dialog?.classList.add('meditation-delete-confirm');$('confirm-cancel')?.focus();
      if(!await answer)return;
      const result=await e.api('meditation',{command:'delete',id});await window.ViewMotion?.exit([...document.querySelectorAll('[data-motion-key]')].find(n=>n.dataset.motionKey==='meditation-'+id));accept(result);removed=true;drafts.delete(id);
      if(saved?.id===id){saved=null;view='history';date=D.localDate(new Date(session.startedAt));month=date.slice(0,7);}
      e.toast('禅定记录已删除');
    }catch(error){e.toast('删除未完成：'+error.message+'，可重试');}
    finally{dialog?.classList.remove('meditation-delete-confirm');busy=false;if(removed)e.render({transition:'list'});else lockControls();}
  }
  async function command(command){
    if(busy)return;busy=true;sequence++;document.querySelectorAll('.meditation-controls button').forEach(b=>b.disabled=true);
    // Retry a timed-out start with the same id; never silently create another session.
    const id=command==='start'?(requestId||(requestId=D.uid())):snapshot.active?.id;
    try{const value=await e.api('meditation',{command,id});accept(value);if(command==='finish'){saved=value.saved;view='result';}if(command==='start')requestId='';}
    catch(error){e.toast(error.message);try{accept(await e.api('meditation',{command:'status'}));}catch(ignored){}}
    finally{busy=false;e.render();}
  }
  function bind(){
    document.body.classList.toggle('meditation-mode',e.state.page==='meditation'&&view==='timer');
    if(!$('meditation-root'))return;
    $('meditation-back').onclick=()=>e.go('today');$('meditation-history').onclick=()=>{view=view==='history'?'timer':'history';e.render();};
    if($('meditation-start'))$('meditation-start').onclick=()=>command('start');
    if($('meditation-toggle'))$('meditation-toggle').onclick=()=>command(snapshot.active?.state==='running'?'pause':'resume');
    if($('meditation-end'))$('meditation-end').onclick=()=>command('finish');
    if($('meditation-done'))$('meditation-done').onclick=()=>{view='timer';e.go('today');};
    document.querySelectorAll('[data-meditation-delete]').forEach(b=>b.onclick=()=>remove(b.dataset.meditationDelete));
    if($('meditation-month'))$('meditation-month').onchange=event=>{if(!/^\d{4}-\d{2}$/.test(event.target.value))return;month=event.target.value;date=month+'-01';e.render();};
    document.querySelectorAll('[data-meditation-day]').forEach(b=>b.onclick=()=>{date=b.dataset.meditationDay;e.render();});
    document.querySelectorAll('[data-meditation-session]').forEach(b=>b.onclick=()=>{saved=snapshot.sessions.find(s=>s.id===b.dataset.meditationSession);view='result';e.render();});
    if($('meditation-note-form')){
      const id=$('meditation-note-form').dataset.id;$('meditation-note').oninput=event=>drafts.set(id,event.target.value);
      $('meditation-note-form').onsubmit=event=>{event.preventDefault();if(busy)return;e.safe(async()=>{const note=$('meditation-note').value;busy=true;sequence++;lockControls();try{accept(await e.api('meditation',{command:'note',id,note}));saved=snapshot.sessions.find(s=>s.id===id);drafts.delete(id);e.toast('感受已保存');}finally{busy=false;lockControls();}});};
    }
    tick();
  }
  window.MeditationUI={init,entry,page,bind,accept,refresh};
})();
