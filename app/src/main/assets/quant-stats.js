(function(root){
 'use strict';const labels={understand:'概念与知识',analyze:'问题分析',apply:'实践尝试',improve:'方法调整',verify:'验证记录',reflect:'复盘感悟',plan:'创意计划',reference:'资料摘录'};
 const facets=[['practice','已报告实践'],['change','方法调整'],['verification','验证结果'],['reflection','复盘感悟'],['plan','想法与计划']];
 function eligible(r){return ['learning','work_note','idea','experience'].includes(r.type)&&!!String(r.note||'').trim();}
 function current(r){const q=r.quantLabels;return eligible(r)&&q?.schemaVersion===1&&q.model==='qwen3.7-flash'&&q.complete===true&&Number(q.sourceVersion)===Number(r.updatedAt||0)&&Object.hasOwn(labels,q.primary)&&Array.isArray(q.evidence)&&q.evidence.every(v=>Number.isInteger(v.start)&&Number.isInteger(v.end)&&v.start>=0&&v.end>v.start&&r.note.slice(v.start,v.end)===v.quote);}
 function matches(ev,kind){if(ev.actor!=='self')return false;
  if(kind==='practice')return ev.kind==='application'&&['ongoing','completed','measured'].includes(ev.status);
  if(kind==='change')return ev.kind==='method_change'&&['completed','measured'].includes(ev.status);
  if(kind==='verification')return ['verification','result'].includes(ev.kind)&&ev.status==='measured';
  if(kind==='reflection')return ev.kind==='reflection'&&ev.status!=='planned';
  if(kind==='plan')return ev.kind==='plan'||ev.status==='planned';return false;
 }
 function summarize(data){const records=data.items.map(x=>x.record).filter(eligible),done=records.filter(current),groups=new Map();
  for(const r of done){const primary=r.quantLabels.primary;if(!groups.has(primary))groups.set(primary,{key:primary,label:labels[primary],ids:[],members:[primary]});groups.get(primary).ids.push(r.id);}
  let buckets=[...groups.values()].sort((a,b)=>b.ids.length-a.ids.length||a.key.localeCompare(b.key));
  if(buckets.length>6){const rest=buckets.slice(5);buckets=buckets.slice(0,5).concat({key:'remaining',label:'更多类型',ids:rest.flatMap(b=>b.ids),members:rest.flatMap(b=>b.members)});}
  let remainder=done.length?100:0;buckets=buckets.map(b=>{const exact=b.ids.length/done.length*100,percent=Math.floor(exact);remainder-=percent;return {...b,value:b.ids.length,percent,remainder:exact-percent};});
  const ordered=buckets.map((_,i)=>i).sort((a,b)=>buckets[b].remainder-buckets[a].remainder||a-b);for(let i=0;i<remainder;i++)buckets[ordered[i%ordered.length]].percent++;
  return {total:records.length,done:done.length,pending:records.length-done.length,buckets,facets:facets.map(([key,label])=>({key,label,ids:done.filter(r=>r.quantLabels.evidence.some(ev=>matches(ev,key))).map(r=>r.id)}))};
 }
 const api={labels,eligible,current,matches,summarize};if(typeof module==='object'&&module.exports)module.exports=api;else root.QuantStats=api;
})(typeof window!=='undefined'?window:globalThis);
