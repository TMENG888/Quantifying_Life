(function(root){
 'use strict';
 const clone=x=>JSON.parse(JSON.stringify(x)),areas={learning:'学习',work:'工作',life:'生活'};
 const number=n=>Number(n).toLocaleString('zh-CN',{maximumFractionDigits:1});
 function localDate(d=new Date()){return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-');}
 function localTime(d=new Date()){return localDate(d)+'T'+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');}
 function dateTime(s){if(!s)return true;if(!/^(?:19|20|21|22)\d{2}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?$/.test(s))return false;const d=new Date(s);return Number.isFinite(d.getTime())&&localTime(d)===s.slice(0,16)&&(s.length===16||String(d.getSeconds()).padStart(2,'0')===s.slice(17));}
 function actualMinutes(item,now=Date.now()){return Math.max(0,Number(item.actualMinutes)||0)+(item.timerStartedAt?Math.max(0,now-item.timerStartedAt)/60000:0);}
 const ratio=item=>Math.min(1,Math.max(0,Number(item.actualAmount)||0)/Math.max(1e-9,Number(item.targetAmount)||1));
 function isToday(item,now=Date.now()){const today=localDate(new Date(now)),start=item.scheduledStart?.slice(0,10),end=(item.scheduledEnd||item.scheduledStart)?.slice(0,10),due=item.deadline?.slice(0,10);if(item.kind!=='task')return false;if(item.status==='completed'&&item.completedAt&&localDate(new Date(item.completedAt))===today)return true;if(item.timerStartedAt)return true;if(start)return start<=today&&end>=today||item.status==='active'&&end<today;return due?due<=today:item.status==='active';}
 function timing(item,now=Date.now()){if(item.timerStartedAt)return '计时中';if(item.status==='completed')return '已完成';const due=item.deadline||item.scheduledEnd;if(!due)return '待安排';const delta=Date.parse(due)-now;if(delta<0)return '已逾期';if(delta<3600000)return '剩余 '+Math.max(1,Math.ceil(delta/60000))+' 分钟';if(delta<86400000)return '剩余 '+number(delta/3600000)+' 小时';return '剩余 '+Math.ceil(delta/86400000)+' 天';}
 function pacing(item,now=Date.now()){if(item.kind!=='goal'||item.status==='completed'||!item.deadline)return null;const left=Math.max(0,item.targetAmount-item.actualAmount),ms=Date.parse(item.deadline)-now;if(left<=0)return {remaining:0,days:0,daily:0};if(ms<=0)return {remaining:left,days:0,daily:null};const days=Math.max(1,Math.ceil(ms/86400000));return {remaining:left,days,daily:left/days};}
 function children(items,id){const rows=items.filter(r=>r.parentId===id);return {items:rows,total:rows.length,completed:rows.filter(r=>r.status==='completed').length};}
 function rollup(item,items,now=Date.now()){
  const ownAmount=item.ownAmount??item.actualAmount,ownMinutes=item.ownMinutes??item.actualMinutes,ownElapsedMinutes=item.ownElapsedMinutes??actualMinutes(item,now);
  if(item.kind!=='goal')return {...clone(item),elapsedMinutes:actualMinutes(item,now)};
  const linked=items.filter(t=>t.parentId===item.id),matched=linked.filter(t=>t.targetUnit.trim()===item.targetUnit.trim()),childAmount=matched.reduce((n,t)=>n+t.actualAmount,0),childMinutes=linked.reduce((n,t)=>n+t.actualMinutes,0),childElapsedMinutes=linked.reduce((n,t)=>n+actualMinutes(t,now),0);
  return {...clone(item),ownAmount,ownMinutes,ownElapsedMinutes,childAmount,childMinutes,childElapsedMinutes,childTimerRunning:linked.some(t=>t.timerStartedAt),matchedChildren:matched.length,excludedQuantityChildren:linked.length-matched.length,actualAmount:ownAmount+childAmount,actualMinutes:ownMinutes+childMinutes,elapsedMinutes:ownElapsedMinutes+childElapsedMinutes};
 }
 function validate(item,items){
  if(!item||!['goal','task'].includes(item.kind))throw Error('请选择目标或任务');
  if(typeof item.title!=='string'||!item.title.trim()||item.title.trim().length>200)throw Error('名称应为1至200字');
  if(!Number.isFinite(Number(item.targetAmount))||Number(item.targetAmount)<=0||Number(item.targetAmount)>1e9)throw Error('目标数量应大于0且不超过10亿');
  if(typeof item.targetUnit!=='string'||!item.targetUnit.trim()||item.targetUnit.trim().length>20)throw Error('请填写20字以内的数量单位');
  if(!Object.hasOwn(areas,item.area))throw Error('请选择学习、工作或生活');
  if(!Number.isFinite(Number(item.plannedMinutes))||Number(item.plannedMinutes)<0||Number(item.plannedMinutes)>1e7)throw Error('计划分钟数无效');
  for(const k of ['deadline','scheduledStart','scheduledEnd'])if(!dateTime(item[k]))throw Error('请输入有效的本地日期和时间');
  if(!!item.scheduledStart!==!!item.scheduledEnd)throw Error('执行时段需要同时填写开始和结束');
  if(item.scheduledStart&&Date.parse(item.scheduledStart)>=Date.parse(item.scheduledEnd))throw Error('执行结束应晚于开始');
  if(item.deadline&&item.scheduledEnd&&Date.parse(item.scheduledEnd)>Date.parse(item.deadline))throw Error('执行时段不能晚于截止时间');
  if(item.kind==='goal'&&item.parentId)throw Error('长期目标不能再归属任务');
  const parent=item.parentId&&items.find(g=>g.id===item.parentId);
  if(item.parentId&&(!parent||parent.kind!=='goal'||parent.id===item.id))throw Error('关联的长期目标不存在');
  if(parent?.deadline&&[item.deadline,item.scheduledEnd].some(v=>v&&Date.parse(v)>Date.parse(parent.deadline)))throw Error('行动不能安排在长期目标截止时间之后');
  if(item.note!==undefined&&(typeof item.note!=='string'||item.note.length>10000))throw Error('任务说明不能超过10000字');
  return item;
 }
 function normalize(items,now){for(const item of items){let changed=false;if(item.timerStartedAt){item.actualMinutes=actualMinutes(item,now);item.timerStartedAt=0;changed=true;}const reached=rollup(item,items,now).actualAmount+1e-9>=item.targetAmount;if(!item.manualCompleted&&reached!==(item.status==='completed')){item.status=reached?'completed':'active';if(reached)item.completedAt=now;else delete item.completedAt;changed=true;}if(changed)item.updatedAt=Math.max(now,(item.updatedAt||0)+1);}}
 function execute(data,p,now=Date.now()){
  const items=clone(data.goalTasks||[]),before=clone(items),visible=i=>rollup(i,items,now),snapshot=()=>({items:items.map(visible)}),command=p.command||'list';if(command==='list'){normalize(items,now);data.goalTasks=items;return snapshot();}
  if(command==='batch'){if(!Array.isArray(p.commands)||!p.commands.length||p.commands.length>20)throw Error('一次最多提交20个任务');const draft={goalTasks:items};let result;for(const c of p.commands){if(c.command!=='save')throw Error('批量操作只支持新建或编辑任务');result=execute(draft,c,now);}data.goalTasks=draft.goalTasks;return result;}
  const id=command==='save'?p.item?.id:p.id,prior=items.find(i=>i.id===id);if(!/^[A-Za-z0-9_-]{3,100}$/.test(id||''))throw Error('任务编号无效');
  const deletedEntries=data.goalTaskDeletedEntries||[];
  if(command==='progress'){if(deletedEntries.some(x=>x.id===p.entryId))throw Error('这条进度已删除，请重新登记');const priorEvent=items.flatMap(t=>t.entries||[]).find(x=>x.id===p.entryId);if(priorEvent){if(priorEvent.taskId!==id||priorEvent.amount!==p.amount||priorEvent.minutes!==p.minutes||priorEvent.note!==String(p.note||'').trim())throw Error('进度编号已用于另一笔记录，请刷新重试');return {...snapshot(),saved:visible(prior)};}}
  if(command==='deleteProgress'&&prior&&deletedEntries.some(x=>x.id===p.entryId&&x.taskId===id))return {...snapshot(),saved:visible(prior)};
  if(command==='updateProgress'){const entry=prior?.entries?.find(v=>v.id===p.entryId);if(!entry)throw Error('这条进度已不存在，请刷新后重试');if(!/^[A-Za-z0-9_-]{3,100}$/.test(p.editId||''))throw Error('编辑编号无效');if(entry.lastEditId===p.editId){if(entry.amount!==p.amount||entry.minutes!==p.minutes||entry.note!==String(p.note||'').trim())throw Error('编辑编号已使用，请重新打开进度后修改');return {...snapshot(),saved:visible(prior)};}}
  if(command==='delete'&&!prior)return {...snapshot(),deleted:id};
  if(!Object.hasOwn(p,'expectedVersion')||command==='save'&&!prior&&p.expectedVersion!==null||prior&&(typeof p.expectedVersion!=='number'||p.expectedVersion!==prior.updatedAt))throw Error('任务已变化，请刷新后重试');
  if(command!=='save'&&!prior)throw Error('任务不存在');
  const version=Math.max(now,(prior?.updatedAt||0)+1);
  if(command==='save'){
   const input=p.item;if(prior&&input.kind!==prior.kind)throw Error('不能改变目标或任务类型');
   const item={id,kind:input.kind,parentId:input.parentId||'',title:String(input.title||'').trim(),area:input.area||'life',deadline:input.deadline||'',scheduledStart:input.scheduledStart||'',scheduledEnd:input.scheduledEnd||'',note:String(input.note||'').trim(),targetAmount:Number(input.targetAmount),targetUnit:String(input.targetUnit||'').trim(),plannedMinutes:Number(input.plannedMinutes)||0,actualAmount:prior?.actualAmount||0,actualMinutes:prior?.actualMinutes||0,status:prior?.status||'active',createdAt:prior?.createdAt||now,updatedAt:version,entries:prior?.entries||[],timerStartedAt:prior?.timerStartedAt||0,...(prior?.manualCompleted?{manualCompleted:true}:{}),...(prior?.completedAt?{completedAt:prior.completedAt}:{})};
   validate(item,items);if(prior?.actualAmount>0&&prior.targetUnit!==item.targetUnit)throw Error('已有完成数量，不能改变计量单位；请新建任务');if(item.kind==='goal'&&item.deadline&&items.some(t=>t.parentId===id&&[t.deadline,t.scheduledEnd].some(v=>v&&Date.parse(v)>Date.parse(item.deadline))))throw Error('目标截止前仍有未调整的子任务时段');
   if(prior)items.splice(items.indexOf(prior),1,item);else items.push(item);
  }else{
   if(command==='delete'){if(items.some(t=>t.parentId===id)&&!p.detachChildren)throw Error('请先解除或删除关联行动，再删除长期目标');if(prior.timerStartedAt&&!p.stopTimer)throw Error('请先暂停计时');if(p.detachChildren)for(const child of items)if(child.parentId===id){child.parentId='';child.updatedAt=Math.max(now,child.updatedAt+1);}items.splice(items.indexOf(prior),1);}
   else if(command==='progress'){if(prior.status!=='active')throw Error('请先重新开启任务');if(!/^[A-Za-z0-9_-]{3,100}$/.test(p.entryId||''))throw Error('进度编号无效');const amount=p.amount,minutes=p.minutes;if(typeof amount!=='number'||!Number.isFinite(amount)||amount<0||typeof minutes!=='number'||!Number.isFinite(minutes)||minutes<0||amount===0&&minutes===0)throw Error('请填写本次完成量或耗时');if(prior.actualAmount+amount>1e9||prior.actualMinutes+minutes>1e7)throw Error('累计数量或用时超出范围');if(prior.timerStartedAt&&minutes>0)throw Error('计时进行中，请先暂停再登记手动耗时');prior.actualAmount+=amount;prior.actualMinutes+=minutes;prior.entries.push({id:p.entryId,taskId:id,amount,minutes,note:String(p.note||'').trim(),at:now});}
   else if(command==='deleteProgress'){const entry=prior.entries.find(v=>v.id===p.entryId);if(!entry)throw Error('这条进度已不存在，请刷新后重试');prior.actualAmount=Math.max(0,prior.actualAmount-entry.amount);prior.actualMinutes=Math.max(0,prior.actualMinutes-entry.minutes);prior.entries=prior.entries.filter(v=>v.id!==p.entryId);if(prior.status==='completed'&&!prior.manualCompleted&&rollup(prior,items,now).actualAmount<prior.targetAmount){prior.status='active';delete prior.completedAt;}data.goalTaskDeletedEntries=[...deletedEntries,{id:p.entryId,taskId:id}];}
   else if(command==='updateProgress'){const entry=prior.entries.find(v=>v.id===p.entryId),amount=p.amount,minutes=p.minutes;if(typeof amount!=='number'||!Number.isFinite(amount)||amount<0||amount>1e9||typeof minutes!=='number'||!Number.isFinite(minutes)||minutes<0||minutes>1e7||amount===0&&minutes===0)throw Error('请填写本条完成量或手动耗时；不需要的进度可删除');const total=Math.max(0,prior.actualAmount-entry.amount+amount),time=Math.max(0,prior.actualMinutes-entry.minutes+minutes);if(total>1e9||time>1e7)throw Error('累计数量或用时超出范围');const note=String(p.note||'').trim();if(note.length>10000)throw Error('进展说明不能超过10000字');prior.actualAmount=total;prior.actualMinutes=time;Object.assign(entry,{amount,minutes,note,editedAt:now,lastEditId:p.editId});if(prior.status==='completed'&&!prior.manualCompleted&&total<prior.targetAmount){prior.status='active';delete prior.completedAt;}}
   else if(command==='start'){throw Error('任务计时功能已移除');}
   else if(['pause','complete','check'].includes(command)){if(command==='complete'&&rollup(prior,items,now).actualAmount<prior.targetAmount)throw Error('实际完成数量尚未达到目标，请先记录进度');if(prior.timerStartedAt){prior.actualMinutes=actualMinutes(prior,now);prior.timerStartedAt=0;}if(command==='complete'||command==='check'){prior.status='completed';prior.completedAt=now;if(command==='check')prior.manualCompleted=true;}}
   else if(command==='reopen'){prior.status='active';delete prior.completedAt;delete prior.manualCompleted;}else throw Error('未知任务操作');
   prior.updatedAt=version;
  }
  const affected=new Set();for(const item of items){const old=before.find(t=>t.id===item.id);if(!old||old.updatedAt!==item.updatedAt){affected.add(item.parentId);if(old)affected.add(old.parentId);}}for(const old of before)if(!items.some(t=>t.id===old.id))affected.add(old.parentId);
  for(const item of items)if(item.kind==='goal'){let changed=affected.has(item.id);if(item.status==='completed'&&!item.manualCompleted&&rollup(item,items,now).actualAmount<item.targetAmount){item.status='active';delete item.completedAt;changed=true;}if(changed)item.updatedAt=Math.max(now,item.updatedAt+1);}
  normalize(items,now);data.goalTasks=items;const saved=items.find(i=>i.id===id);return {...snapshot(),...(saved?{saved:visible(saved)}:{deleted:id})};
 }
 function backup(items,now=Date.now()){return clone(items||[]).map(i=>{i.actualMinutes=i.ownElapsedMinutes??actualMinutes(i,now);i.actualAmount=i.ownAmount??i.actualAmount;i.timerStartedAt=0;for(const k of ['elapsedMinutes','ownAmount','ownMinutes','ownElapsedMinutes','childAmount','childMinutes','childElapsedMinutes','childTimerRunning','matchedChildren','excludedQuantityChildren'])delete i[k];return i;});}
 function restore(data,incoming){if(!Array.isArray(incoming))throw Error('任务备份格式无效');const merged=clone(data.goalTasks||[]),ids=new Set(merged.map(i=>i.id)),entries=new Set(merged.flatMap(i=>(i.entries||[]).map(v=>v.id)));let inserted=0;for(const raw of incoming){if(ids.has(raw.id))continue;if(!/^[A-Za-z0-9_-]{3,100}$/.test(raw.id||''))throw Error('任务编号无效');const i=clone(raw);if(i.manualCompleted!==undefined&&typeof i.manualCompleted!=='boolean')throw Error('任务勾选状态无效');i.timerStartedAt=0;delete i.elapsedMinutes;if(!['active','completed'].includes(i.status)||!Number.isFinite(i.actualAmount)||i.actualAmount<0||!Number.isFinite(i.actualMinutes)||i.actualMinutes<0||i.kind==='task'&&i.status==='completed'&&!i.manualCompleted&&i.actualAmount<i.targetAmount)throw Error('任务进度格式无效');for(const event of i.entries||[]){if(entries.has(event.id))throw Error('任务进度编号冲突，未导入备份');entries.add(event.id);}merged.push(i);ids.add(i.id);inserted++;}for(const i of merged){validate(i,merged);if(i.status==='completed'&&!i.manualCompleted&&rollup(i,merged).actualAmount<i.targetAmount)throw Error('已完成目标的数量与关联行动不一致');}data.goalTasks=merged;return inserted;}
 const api={areas,number,localDate,localTime,dateTime,actualMinutes,ratio,isToday,timing,pacing,children,rollup,validate,execute,backup,restore};
 if(typeof module==='object'&&module.exports)module.exports=api;else {root.GoalDomain=api;root.GoalTasksPreview={execute};}
})(typeof window!=='undefined'?window:globalThis);
