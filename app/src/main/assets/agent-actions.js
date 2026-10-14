(function(root){
 'use strict';const node=typeof module==='object'&&module.exports,D=node?require('./domain.js'):root.LifeDomain,F=node?require('./work-facts.js'):root.WorkFacts;
 const fields=['title','date','platform','contentType','tags','note','url','durationMinutes','occurredAt','workStart','workEnd','breaks'];
 const schema={type:'array',maxItems:10,items:{type:'object',properties:{kind:{type:'string',enum:['update_record','delete_record','start_work','start_break','end_break','end_work','create_work_note']},recordId:{type:'string',description:'真实班次/记录ID；休息及下班可省略，仅在唯一进行中班次时自动定位'},at:{type:'string',description:'打卡发生时间，本地yyyy-MM-ddTHH:mm:ss。用户明确指定时填写；现在打卡省略。不猜过去日期的时刻'},patch:{type:'object',properties:{title:{type:'string'},date:{type:'string'},platform:{type:'string'},contentType:{type:'string'},tags:{type:'array',items:{type:'string'}},note:{type:'string'},url:{type:'string'},durationMinutes:{type:'number'},occurredAt:{type:'string'},workStart:{type:'string'},workEnd:{type:'string'},breaks:{type:'array',items:{type:'object',properties:{start:{type:'string'},end:{type:'string'}},required:['start','end']}}}},appendNote:{type:'string'},title:{type:'string'},note:{type:'string'},date:{type:'string'},occurredAt:{type:'string'}},required:['kind']}};
 const clone=x=>JSON.parse(JSON.stringify(x));
 // Only self-contained clock statements get a deterministic fallback. Questions,
 // negation, plans and richer narratives are left to the model, never auto-written.
 function clockRequest(text,now=new Date()){
  let s=String(text||'').trim().replace(/[。！!，,]+$/,'');if(/^我?(?:现在|今天)?(?:已经)?下班了(?:然后)?(?:你)?(?:可以)?(?:请)?帮我(?:打一下|打卡|记录一下|记一下)$/.test(s.replace(/[，,\s]/g,'')))return {kind:'end_work'};
  if(/[?？]|(?:不|没|不要|取消|查询|查看|几点|多久|什么时候|如何|怎么|明天|准备|计划|打算|如果|假如|可能|可以|能否|提醒|应该|将要|想要)/.test(s))return null;
  s=s.replace(/^(?:(?:请|麻烦)?(?:帮我)?(?:记录|记下|登记|打卡)(?:一下)?[，,：:\s]*|(?:请|麻烦)?帮我[，,：:\s]*)/,'').replace(/^我[，,\s]*/,'');
  const verbs=[['start_break',/开始休息(?:了)?|休息开始(?:了)?|开始午休(?:了)?/],['end_break',/结束休息(?:了)?|休息结束(?:了)?|返回工作(?:了)?|继续工作(?:了)?|结束午休(?:了)?/],['end_work',/下班(?:了)?|结束工作(?:了)?/],['start_work',/开始上班(?:了)?|上班(?:了)?|开始工作(?:了)?/]];
  const found=verbs.map(([kind,re])=>({kind,m:s.match(re)})).filter(x=>x.m);if(found.length!==1)return null;
  const {kind,m}=found[0];let rest=(s.slice(0,m.index)+s.slice(m.index+m[0].length)).replace(/(?:现在|刚刚|刚才|此刻|打卡|时间|已经|是在|是|在|我|的|于)/g,'').trim();
  let date=D.localDate(now),explicitDate=false;
  const dates=[...rest.matchAll(/\d{4}-\d{2}-\d{2}|今天|今日|昨天|昨日|前天/g)];if(dates.length>1)return null;
  if(dates.length){const token=dates[0][0];explicitDate=true;if(/^\d/.test(token))date=token;else{const d=new Date(now);d.setDate(d.getDate()-(token==='前天'?2:/昨/.test(token)?1:0));date=D.localDate(d);}rest=rest.replace(token,'').trim();}
  rest=rest.replace(/^(?:现在|刚刚|刚才|此刻)/,'').trim();
  if(!rest)return {kind,...(explicitDate&&date!==D.localDate(now)?{needsTime:true,date}:{})};
  const time=rest.match(/^(凌晨|早上|上午|中午|下午|晚上|晚)?\s*(\d{1,2})(?:[:：](\d{1,2})(?:[:：](\d{1,2}))?|[点时](?:(\d{1,2})分?|(半|一刻|三刻))?)\s*$/);if(!time)return null;
  let hour=Number(time[2]),minute=Number(time[3]||time[5]||({半:30,一刻:15,三刻:45}[time[6]])||0),second=Number(time[4]||0);
  if(hour>23||minute>59||second>59||!/^\d{4}-\d{2}-\d{2}$/.test(date)||D.localDate(new Date(date+'T12:00:00'))!==date)return {kind,error:'时间无效，请提供明确的日期和时间，如今天09:00。'};
  if(time[1]&&/下午|晚上|晚/.test(time[1])&&hour===12)return {kind,needsTime:true,date};
  if(time[1]&&/下午|晚上|晚/.test(time[1])&&hour<12)hour+=12;
  if(time[1]==='中午'&&hour>=1&&hour<=3)hour+=12;if(time[1]==='凌晨'&&hour===12)hour=0;
  // Bare 1..7 o'clock can mean either AM or PM; do not silently choose.
  if(!time[1]&&!rest.includes(':')&&!rest.includes('：')&&hour>=1&&hour<=7)return {kind,needsTime:true,date};
  const at=date+'T'+String(hour).padStart(2,'0')+':'+String(minute).padStart(2,'0')+':'+String(second).padStart(2,'0');
  return {kind,at};
 }
 function clockActions(request,records,now=new Date()){
  if(!request)return null;if(request.error)throw Error(request.error);if(request.needsTime)throw Error('请补充具体时间（建议24小时制，如今天13:00）；不会替你猜测时间。');
  if(request.kind==='start_work')return [{kind:request.kind,at:request.at||D.localTime(now)}];
  const active=records.filter(r=>r.type==='work'&&!r.workEnd);if(!active.length&&request.kind==='end_work'){const date=(request.at||D.localTime(now)).slice(0,10),ended=records.filter(r=>r.type==='work'&&r.workEnd&&r.workEnd.slice(0,10)===date).sort((a,b)=>b.workEnd.localeCompare(a.workEnd))[0];if(ended)throw F.completedError(ended,now);}if(active.length!==1)throw Error(active.length?'有多个进行中班次，请先核对工作记录。':'没有进行中的班次，请先记录上班时间。');
  return [{kind:request.kind,recordId:active[0].id,at:request.at||D.localTime(now)}];
 }
 function actionTime(a,now){
  const time=a.at===undefined?D.localTime(now):a.at;
  if(typeof time!=='string'||!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(time)||!Number.isFinite(Date.parse(time))||D.localTime(new Date(time))!==time)throw Error('打卡时间无效，请使用明确的本地日期时间');
  if(Date.parse(time)>now.getTime())throw Error('不能提前记录尚未发生的打卡，请核对时间');return time;
 }
 function prepare(items,records,allowedIds,now=new Date()){
  if(!Array.isArray(items)||items.length>10)throw Error('变更操作列表无效');const seen=new Set();let starting=false;
  return items.flatMap(a=>{
   if(!a||!['update_record','delete_record','start_work','start_break','end_break','end_work','create_work_note'].includes(a.kind))throw Error('不支持的AI操作');
   if(a.kind==='create_work_note'){if(typeof a.note!=='string'||!a.note.trim())throw Error('请填写日志内容');const log=logAction(a.note,a.date||D.localDate(now),now);if(a.title)log.after.title=a.title;if(a.occurredAt!==undefined)log.after.occurredAt=a.occurredAt;D.validate(log.after);return [log];}
   if(a.kind==='start_work'){if(starting)throw Error('同一轮不能重复上班');starting=true;const time=actionTime(a,now);if(records.some(r=>r.type==='work'&&!r.workEnd))throw Error('已有进行中班次，不能重复上班');if(records.some(r=>r.type==='work'&&Date.parse(r.workEnd)>Date.parse(time)))throw Error('该时间之后已有班次，不能补录为进行中。请一次提供完整的上下班及休息时间，补录已结束班次。');const after={id:D.uid(),type:'work',date:time.slice(0,10),title:String(a.title||'工作班次').slice(0,300),note:'',workStart:time,workEnd:'',breaks:[],tags:[],durationMinutes:0,status:'running',source:'ai'};D.validate(after,now.getTime());const result=[{kind:a.kind,before:null,after}];if(a.note?.trim())result.push(logAction(a.note,after.date,now));return result;}
   const active=records.filter(r=>r.type==='work'&&!r.workEnd),clock=['start_break','end_break','end_work'].includes(a.kind),targetId=a.recordId||(clock&&active.length===1?active[0].id:'');const r=records.find(r=>r.id===targetId);if(!r||!allowedIds.includes(r.id))throw Error('更新目标未在本轮检索中找到，请指明日期、标题或记录编号');if(seen.has(r.id))throw Error('同一条记录每轮只能准备一次变更');seen.add(r.id);
   const before=clone(r);if(a.kind==='delete_record')return {kind:a.kind,before,after:null};
   if(a.patch!==undefined&&(!a.patch||typeof a.patch!=='object'||Array.isArray(a.patch)))throw Error('AI试图更改不允许的字段');const after=clone(r),patch={...(a.patch||{})},logs=[];if(r.type==='work'&&a.kind==='update_record'){const content=[patch.note,a.appendNote].filter(v=>v!==undefined);for(const v of content){if(typeof v!=='string')throw Error('日志内容无效');if(v.trim())logs.push(logAction(v,patch.date||r.date,now));}delete patch.note;a={...a,appendNote:undefined};if(!Object.keys(patch).length&&logs.length)return logs;}if(typeof patch!=='object'||Array.isArray(patch)||Object.keys(patch).some(k=>!fields.includes(k)))throw Error('AI试图更改不允许的字段');
   if(a.kind==='update_record'){
    for(const [k,v] of Object.entries(patch))after[k]=v;
    if(a.appendNote!==undefined){if(typeof a.appendNote!=='string'||!a.appendNote.trim())throw Error('追加内容无效');after.note=[after.note||'',a.appendNote.trim()].filter(Boolean).join('\n\n');}
    if(!Object.keys(patch).length&&!a.appendNote&&!logs.length)throw Error('请说明要更正或追加什么内容');
   }else{
    if(r.type==='work'&&r.workEnd&&a.kind==='end_work')throw F.completedError(r,now);if(r.type!=='work'||r.workEnd)throw Error('计时操作需要真实的进行中工作班次');const open=after.breaks.find(b=>!b.end),time=actionTime(a,now);if(Date.parse(time)<Date.parse(r.workStart))throw Error('打卡时间不能早于上班时间');
    if(a.kind==='start_break'){if(open)throw Error('已经在休息');after.breaks.push({start:time,end:''});}
    if(a.kind==='end_break'){if(!open)throw Error('没有进行中的休息');open.end=time;}
    if(a.kind==='end_work'){after.workEnd=time;if(open)open.end=time;}
   }
   if(after.type==='work'){after.durationMinutes=Math.round(D.workMinutes(after,now.getTime()).net);after.status=after.workEnd?'completed':'running';if(after.workStart.slice(0,10)!==after.date)throw Error('班次日期应与上班日期一致');}
   D.validate(after,now.getTime());if(JSON.stringify(before)===JSON.stringify(after)){if(logs.length)return logs;throw Error('这次操作没有改变记录');}return [{kind:a.kind,before,after},...logs];
  });
 }
 function workDraft(text,now=new Date()){
  if(!/^(?:我[，,：:\s]*)?(?:今天|今日|昨天|昨日|前天|\d{4}-\d{2}-\d{2})(?:的)?工作[，,：:\s]/.test(text)||/[?？]|(?:怎么|如何|有哪些|查找|查询)/.test(text))return null;
  const period=D.questionPeriod(text,now),date=period?.start||D.localDate(now);return D.aiRecords([{type:'work_note',date,title:D.thoughtTitle(text.replace(/^.*?工作[，,：:\s]+/,'')),note:text,tags:[],durationMinutes:0}])[0];
 }
 function logAction(note,date,now){const after=D.aiRecords([{type:'work_note',date,title:D.thoughtTitle(note),note,durationMinutes:0}])[0];if(date===D.localDate(now))after.occurredAt=D.localTime(now);return {kind:'create_work_note',before:null,after};}
 let e;const label=a=>({create_work_note:'新增工作日志',update_record:'修改记录',delete_record:'删除记录',start_work:'记录上班',start_break:'开始休息',end_break:'返回工作',end_work:'记录下班'}[a.kind]);
 function card(a,i){const esc=e.esc;const changed=fields.filter(k=>JSON.stringify(a.before?.[k])!==JSON.stringify(a.after?.[k])),labels={title:'标题',date:'日期',platform:'平台',contentType:'类型',tags:'标签',note:'内容',url:'链接',durationMinutes:'时长',occurredAt:'发生时间',workStart:'上班',workEnd:'下班',breaks:'休息'};const clockAt=a.kind==='start_work'?a.after.workStart:a.kind==='start_break'?a.after.breaks[a.after.breaks.length-1].start:a.kind==='end_break'?a.after.breaks[a.after.breaks.length-1].end:a.kind==='end_work'?a.after.workEnd:'';return `<div class="candidate change-candidate"><h3>${label(a)}：${esc((a.after||a.before).title)}</h3><p>${esc((a.after||a.before).date)} · ${a.kind==='delete_record'?'删除需再次确认':'尚未写入，确认后才更新'}</p>${clockAt?`<p class="clock-preview"><strong>${esc(clockAt.replace('T',' '))}</strong> · ${esc(label(a))}</p>`:''}${changed.map(k=>`<details><summary>${labels[k]}：查看修改前后</summary><p>原：${esc(typeof a.before?.[k]==='object'?JSON.stringify(a.before[k]):a.before?.[k]??'无')}</p><p>新：${esc(typeof a.after?.[k]==='object'?JSON.stringify(a.after[k]):a.after?.[k]??'无')}</p></details>`).join('')}<div class="actions"><button class="primary" data-confirm-change="${i}">确认${a.kind==='delete_record'?'删除':'更新'}</button><button class="secondary" data-drop-change="${i}">忽略</button></div></div>`;}
 function bind(){document.querySelectorAll('[data-confirm-change]').forEach(b=>b.onclick=()=>e.safe(()=>apply(Number(b.dataset.confirmChange))));document.querySelectorAll('[data-drop-change]').forEach(b=>b.onclick=()=>{e.state.changes.splice(Number(b.dataset.dropChange),1);e.render();});}
 async function apply(i){const a=e.state.changes[i];if(!a)return;if(a.kind==='delete_record'&&!await e.confirm('确认删除「'+a.before.title+'」？','只删除这条记录；已整理的相关Wiki会失效，随后自动更新。','删除'))return;
  if(['start_work','create_work_note'].includes(a.kind))await e.save(a.after);else{
   const result=await e.api('applyRecordAction',{kind:a.kind==='delete_record'?'delete':'update',recordId:a.before.id,expectedVersion:a.before.updatedAt||0,record:a.after});e.state.records=e.state.records.filter(r=>r.id!==a.before.id);if(a.after)e.state.records.unshift(result);
  }if(!['start_work','create_work_note'].includes(a.kind)&&e.outcome)await e.outcome(a.kind==='delete_record'?'deleted':'saved',a.after||a.before);e.state.changes.splice(i,1);e.render();e.toast('操作已应用到本地记录');e.kickWiki();
 }
 const api={schema,fields,prepare,workDraft,clockRequest,clockActions,init:env=>{e=env;},card,bind};if(node)module.exports=api;else root.LifeAgent=api;
})(typeof window!=='undefined'?window:globalThis);
