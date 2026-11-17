(function(root){
 'use strict';
 const node=typeof module==='object'&&module.exports,D=node?require('./domain.js'):root.LifeDomain;
 function key(tag){return String(tag||'').normalize('NFKC').trim().replace(/^#+/,'').trim().replace(/\s+/g,' ').toLowerCase();}
 function tags(input){const seen=new Set();return (Array.isArray(input)?input:String(input||'').split(/[,，、;；\n]/)).filter(t=>typeof t==='string').map(t=>t.normalize('NFKC').trim().replace(/^#+/,'').trim().replace(/\s+/g,' ')).filter(t=>{const k=key(t);if(!k||seen.has(k))return false;seen.add(k);return true;});}
 function group(r){return r.type==='learning'?'learning':['work','work_note'].includes(r.type)?'work':['idea','experience'].includes(r.type)?'thoughts':'';}
 function bounds(range='month',now=new Date(),start='',end=''){
  const today=D.localDate(now),a=new Date(today+'T00:00:00');let b=new Date(a);b.setDate(b.getDate()+1);
  if(range==='all')return {start:'',end:'',a:-Infinity,b:Infinity};
  if(range==='week')a.setDate(a.getDate()-6);else if(range==='month')a.setDate(1);
  else if(range==='custom'){
   const valid=s=>/^\d{4}-\d{2}-\d{2}$/.test(s)&&D.localDate(new Date(s+'T12:00:00'))===s;
   if(!valid(start)||!valid(end)||start>end)throw Error('请选择有效的起止日期，开始不能晚于结束');
   a.setTime(Date.parse(start+'T00:00:00'));b=new Date(end+'T00:00:00');b.setDate(b.getDate()+1);
  }else if(range!=='today')throw Error('统计范围无效');
  const last=new Date(b);last.setDate(last.getDate()-1);return {start:D.localDate(a),end:D.localDate(last),a:a.getTime(),b:b.getTime()};
 }
 function summarize(records,options={}){
  const now=options.now||new Date(),period=bounds(options.range||'month',now,options.start,options.end),module=options.module||'learning';
  const items=[],topics=new Map(),total={count:0,shifts:0,minutes:0,untagged:0,unmeasured:0,ongoing:0};
  for(const r of records){if(group(r)!==module)continue;let minutes=0;
   if(r.type==='work'){const v=D.workMinutesInRange(r,period.a,period.b,now.getTime());if(v.gross<=0)continue;minutes=v.net;total.shifts++;if(!r.workEnd)total.ongoing++;}
   else{if(!r.date||(period.start&&(r.date<period.start||r.date>period.end)))continue;total.count++;if(r.type==='learning'){minutes=Math.max(0,Number(r.durationMinutes)||0);if(!minutes)total.unmeasured++;}}
   total.minutes+=minutes;const labels=tags(r.tags);if(!labels.length)total.untagged++;
   const item={record:r,minutes,keys:labels.length?labels.map(key):['']};items.push(item);
   (labels.length?labels:['']).forEach(label=>{const k=key(label);if(!topics.has(k))topics.set(k,{key:k,label:label||'未分类',count:0,shifts:0,minutes:0,ids:[]});const t=topics.get(k);t.ids.push(r.id);t.minutes+=minutes;if(r.type==='work')t.shifts++;else t.count++;});
  }
  const sort=options.sort==='minutes'?'minutes':'count';return {module,period,total,items,topics:[...topics.values()].sort((a,b)=>b[sort]-a[sort]||b.count-a.count||b.minutes-a.minutes||a.label.localeCompare(b.label,'zh-CN'))};
 }
 function suggestions(records){const counts=new Map();for(const r of records)for(const t of tags(r.tags)){const k=key(t),v=counts.get(k)||{label:t,count:0};v.count++;counts.set(k,v);}return [...counts.values()].sort((a,b)=>b.count-a.count||a.label.localeCompare(b.label,'zh-CN'));}
 const api={key,tags,group,bounds,summarize,suggestions};if(node)module.exports=api;else root.TopicStats=api;
})(typeof window!=='undefined'?window:globalThis);
