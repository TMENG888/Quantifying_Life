(function(root){
 'use strict';const node=typeof module==='object'&&module.exports,D=node?require('./domain.js'):root.LifeDomain,W=node?require('./wiki.js'):root.LifeWiki;
 const clean=topics=>[...new Set((topics||[]).filter(t=>typeof t==='string').map(t=>t.trim()).filter(t=>t.length>=2&&t.length<=60))].slice(0,3);
 function coversJobs(page,sources,ids){return Boolean(page)&&sources.filter(r=>ids.includes(r.id)).every(r=>page.sourceIds.includes(r.id));}
 function validateTopics(raw,records){const p=W.parseJSON(raw);if(!Array.isArray(p.items)||p.items.length>10)throw Error('自动Wiki归类格式无效');const ids=new Set(records.map(r=>r.id));if(p.items.some(i=>!ids.has(i.recordId)||!Array.isArray(i.topics)||i.topics.length>3||i.topics.some(t=>typeof t!=='string'||t.length>60)))throw Error('自动归类引用了不存在的来源');return records.map(r=>{const topics=clean(p.items.find(i=>i.recordId===r.id)?.topics);return {id:r.id,version:r.updatedAt||0,topics:topics.length?topics:[['work','work_note'].includes(r.type)?'工作项目':r.type==='idea'?'灵感创意':r.type==='experience'?'经验感悟':'学习积累']};});}
 const topicSchema={type:'object',properties:{items:{type:'array',items:{type:'object',properties:{recordId:{type:'string'},topics:{type:'array',maxItems:3,items:{type:'string'}}},required:['recordId','topics']}}},required:['items']};
 let e,timer,running=false,count=0,status='等待自动整理',retryAfter=0,failures=0;
 const config=()=>({...e.state.settings.wikiAutomation,enabled:true,dailyCallLimit:0,dailyUsed:e.state.settings.wikiAutomation?.dailyUsed||0});
 function notify(text){status=text;document.querySelectorAll('[data-wiki-auto-status]').forEach(el=>el.textContent=status+(count?' · 待整理'+count+'条':''));}
 function strip(){return `<div class="wiki-strip"><span data-wiki-auto-status>${e.esc(status+(count?' · 待整理'+count+'条':''))}</span><small>自动整理 · 无每日次数限制 · 请求可能计费</small></div>`;}
 function tools(){return `<p class="hint" data-wiki-auto-status>${e.esc(status)}</p>`;}
 function bind(){}
 function kick(delay=1500){clearTimeout(timer);timer=setTimeout(()=>pump().catch(err=>{failures++;const delay=Math.min(15*60000,30000*Math.pow(2,failures-1));retryAfter=Date.now()+delay;notify('整理暂未完成，将自动重试：'+err.message);kick(delay);}),delay);}
 async function ask(payload){if(document.hidden||e.state.busy||!config().enabled)throw Error('自动整理已暂停，待App前台空闲后继续');return e.api('ask',{...payload,automaticWiki:true});}
 async function discover(){
  const data=await e.api('getWikiInsights');e.state.wikiInsights=data;
  const pending=WikiInsights.nextTask(e.state.wikiPages,e.state.records,data.reports||[]);
  if(!pending){notify('知识整理与发现已更新');return false;}
  notify('正在发现联系、核对冲突与证据缺口…');
  const result=await WikiInsights.run({pages:e.state.wikiPages,records:e.state.records,reports:data.reports||[],api:e.api,ask});
  if(result.report){e.state.wikiInsights=await e.api('getWikiInsights');if(e.insightsChanged)e.insightsChanged();}
  notify(result.pending?'已完成一批知识发现，稍后继续':'知识整理与发现已更新');return result.pending;
 }
 async function pump(){
  if(running||document.hidden)return;if(e.state.busy||Date.now()<retryAfter){kick(10000);return;}running=true;
  try{
   const q=await e.api('getWikiQueue');count=q.count;e.state.settings.wikiAutomation=q.settings;const s=config();
   if(!s.enabled){notify('自动整理未开启');return;}if(!e.modelConfigured()){notify('待配置模型Key后自动整理');return;}if(!count){const more=await discover();failures=0;retryAfter=0;if(more)kick(5000);return;}
   const jobs=q.jobs.slice(0,3),ids=jobs.map(j=>j.id),records=jobs.map(j=>e.state.records.find(r=>r.id===j.id&&Number(r.updatedAt||0)===Number(j.version))).filter(Boolean),knowledge=records.filter(r=>r.type!=='work'||r.note?.trim()),needs=knowledge.filter(r=>r.wikiTopicVersion!==r.updatedAt||!r.wikiTopics?.length),assigned=[];
   notify('正在自动归类与整理…');const infer=[];for(const r of needs){const topics=clean(r.tags);if(topics.length)assigned.push({id:r.id,version:r.updatedAt||0,topics});else infer.push(r);}
   let pending=false;for(const r of infer){const topics=await WikiCompiler.classify(r,{api:e.api,ask,budget:2});if(topics===null){pending=true;break;}assigned.push({id:r.id,version:r.updatedAt||0,topics:topics.length?topics:[r.type==='work_note'?'工作项目':r.type==='idea'?'灵感创意':r.type==='experience'?'经验感悟':'学习积累']});}
   if(assigned.length){const updated=await e.api('saveWikiTopics',{items:assigned});for(const r of updated)e.state.records=e.state.records.map(x=>x.id===r.id?r:x);}
   if(pending){notify('正在分块归类长笔记，稍后继续');kick(5000);return;}
   const affected=[...new Set([...ids.flatMap(id=>e.state.records.find(r=>r.id===id)?.wikiTopics||[]),...e.state.wikiPages.filter(p=>(p.indexSourceIds||p.sourceIds).some(id=>ids.includes(id))).map(p=>p.topic)])];
   let compiled=0;for(const topic of affected){const sources=W.topicRecords(e.state.records,topic,[],e.state.wikiPages),page=e.state.wikiPages.find(p=>D.normalizeText(p.topic)===D.normalizeText(topic)),covered=coversJobs(page,sources,ids);if(!sources.length){if(page){await e.api('deleteWiki',{id:page.id});e.state.wikiPages=e.state.wikiPages.filter(p=>p.id!==page.id);}continue;}if(page&&!W.isStale(page,e.state.records)&&covered)continue;if(compiled>=2)break;await e.compileWiki(topic,[],!covered,{automatic:true,priorityIds:ids});compiled++;}
   const done=jobs.filter(j=>{const r=e.state.records.find(r=>r.id===j.id);if(j.operation==='delete')return !e.state.wikiPages.some(p=>(p.indexSourceIds||p.sourceIds).includes(j.id));if(!r||Number(r.updatedAt||0)!==Number(j.version))return false;if(r.type==='work'&&!r.note.trim())return true;return r.wikiTopics?.length&&r.wikiTopics.every(topic=>{const p=e.state.wikiPages.find(p=>D.normalizeText(p.topic)===D.normalizeText(topic));return p&&!W.isStale(p,e.state.records)&&p.sourceIds.includes(r.id);})&&!e.state.wikiPages.some(p=>(p.indexSourceIds||p.sourceIds).includes(r.id)&&W.isStale(p,e.state.records));});
   if(done.length)await e.api('ackWikiQueue',{jobs:done});const after=await e.api('getWikiQueue');count=after.count;e.state.settings.wikiAutomation=after.settings;notify(count?'已整理一批，剩余自动继续':'已整理最新记录');failures=0;kick(5000);
  }finally{running=false;}
 }
 const api={clean,coversJobs,validateTopics,topicSchema,init:env=>{e=env;document.addEventListener('visibilitychange',()=>{if(!document.hidden)kick();});},kick,resume:()=>{retryAfter=0;failures=0;kick();},tools,strip,bind,ask};if(node)module.exports=api;else root.AutoWiki=api;
})(typeof window!=='undefined'?window:globalThis);
