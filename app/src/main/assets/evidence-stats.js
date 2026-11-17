(function(root){
 'use strict';
 const node=typeof module==='object'&&module.exports,
  D=node?require('./domain.js'):root.LifeDomain,
  C=node?require('./domain-stats.js'):root.DomainStats;
 const validDate=s=>typeof s==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(s)&&D.localDate(new Date(s+'T12:00:00'))===s;
 function summarize(data,{now=new Date(),mapping={}}={}){
  const days=new Map(),groups=new Map(),durations=[];let missing=0;
  function day(date){if(!days.has(date))days.set(date,{date,minutes:0,count:0,shifts:0,ongoing:false,ids:[]});return days.get(date);}
  function add(v,r){if(!v.ids.includes(r.id)){v.ids.push(r.id);if(r.type==='work')v.shifts++;else v.count++;}}
  for(const item of data.items){
   const r=item.record;
   if(r.type==='work'){
    const a=Math.max(data.period.a,Date.parse(r.workStart)),b=Math.min(data.period.b,r.workEnd?Date.parse(r.workEnd):now.getTime());
    if(!Number.isFinite(a)||!Number.isFinite(b)||b<=a)continue;
    const cursor=new Date(D.localDate(new Date(a))+'T00:00:00');
    while(cursor.getTime()<b){const next=new Date(cursor);next.setDate(next.getDate()+1);
     const net=D.workMinutesInRange(r,Math.max(a,cursor.getTime()),Math.min(b,next.getTime()),now.getTime()),v=day(D.localDate(cursor));
     v.minutes+=net.net;add(v,r);if(!r.workEnd&&v.date===D.localDate(now))v.ongoing=true;cursor.setTime(next.getTime());
    }
    continue;
   }
   if(!validDate(r.date))continue;
   const v=day(r.date);add(v,r);v.minutes+=item.minutes;
   if(r.type!=='learning')continue;
   const minutes=Number(item.minutes);
   if(!Number.isFinite(minutes)||minutes<=0){missing++;continue;}
   const p={id:r.id,date:r.date,title:String(r.title||'未命名记录'),minutes};durations.push(p);
   for(const label of C.classify(r,data.module,mapping).labels){if(!groups.has(label))groups.set(label,[]);groups.get(label).push(p);}
  }
  const daily=[...days.values()].sort((a,b)=>a.date.localeCompare(b.date)),values=durations.map(x=>x.minutes).sort((a,b)=>a-b),n=values.length;
  return {daily,months:[...new Set(daily.map(x=>x.date.slice(0,7)))],durations:{count:n,missing,median:n?(values[Math.floor((n-1)/2)]+values[Math.floor(n/2)])/2:null,
   rows:[...groups].map(([label,points])=>({label,points:points.sort((a,b)=>a.date.localeCompare(b.date)||String(a.id).localeCompare(String(b.id)))})).sort((a,b)=>b.points.length-a.points.length||a.label.localeCompare(b.label,'zh-CN'))}};
 }
 function monthCells(month,daily,period={start:'',end:''}){
  if(!/^\d{4}-\d{2}$/.test(month)||!validDate(month+'-01'))return [];
  const first=new Date(month+'-01T12:00:00'),offset=(first.getDay()+6)%7,end=new Date(first);end.setMonth(end.getMonth()+1,0);
  const count=Math.ceil((offset+end.getDate())/7)*7,lookup=new Map(daily.map(x=>[x.date,x]));
  return Array.from({length:count},(_,i)=>{
   const number=i-offset+1;if(number<1||number>end.getDate())return null;
   const date=month+'-'+String(number).padStart(2,'0'),value=lookup.get(date);
   return {date,number,inRange:(!period.start||date>=period.start)&&(!period.end||date<=period.end),value:value||null};
  });
 }
 function scale(max){const v=Math.max(1,Number(max)||1),p=10**Math.floor(Math.log10(v)),unit=v/p;return (unit<=1?1:unit<=2?2:unit<=5?5:10)*p;}
 const api={validDate,summarize,monthCells,scale};if(node)module.exports=api;else root.EvidenceStats=api;
})(typeof window!=='undefined'?window:globalThis);
