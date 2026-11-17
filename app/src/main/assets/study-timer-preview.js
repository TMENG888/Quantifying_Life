(function(root){
 'use strict';
 // Browser preview only. Android uses elapsedRealtime and SQLite transactions.
 function execute(data,p,now=Date.now()){
  const D=root.LifeDomain,m=data.studyTimer||(data.studyTimer={active:null,finished:{}}),command=p.command||'status',id=p.id,MAX=7*86400000;
  const elapsed=s=>Math.min(MAX,Math.max(0,(s.ms||0)+(s.state==='running'?Math.max(0,now-s.segmentStart):0)));
  if(m.active&&elapsed(m.active)>=MAX){m.active.ms=MAX;m.active.state='paused';m.active.limitReached=true;}
  const snapshot=()=>({active:m.active?{...m.active,durationMs:elapsed(m.active)}:null});
  if(command==='status')return snapshot();
  if(!/^[A-Za-z0-9_-]{8,80}$/.test(id||''))throw Error('学习计时编号无效');
  if(command==='start'){
   if(m.active&&m.active.id!==id)throw Error('已有学习正在计时，请先结束');
   if(!m.active&&!m.finished[id])m.active={id,state:'running',startedAt:now,date:D.localDate(new Date(now)),segmentStart:now,ms:0,title:''};return snapshot();
  }
  const s=m.active?.id===id?m.active:m.finished[id];if(!s)throw Error('找不到这次学习计时，请刷新后重试');
  if(command==='discard'){if(s.state==='finished')throw Error('已保存的学习记录请到记录中删除');m.active=null;return snapshot();}
  if(p.title!==undefined&&s.state!=='finished'){if(typeof p.title!=='string'||p.title.trim().length>300)throw Error('学习内容最多300字');s.title=p.title.trim();}
  if(command==='finish'){
   if(s.state!=='finished'){
    const ms=elapsed(s);
    s.draft={id:'study-'+id,type:'learning',date:s.date,title:'',studyInput:s.title||'',platform:'',contentType:'other',durationMinutes:ms/60000,note:'',tags:[],url:'',source:'manual',createdAt:s.startedAt,studyTiming:{id,startedAt:s.startedAt,endedAt:now,durationMs:ms,recoveredAfterReboot:false,limitReached:!!s.limitReached}};
    s.state='finished';s.ms=ms;s.endedAt=now;m.finished[id]=s;m.active=null;
   }
   const committed=data.records.find(r=>r.id===s.draft.id);
   data.documentDrafts=data.documentDrafts||[];
   let draft=data.documentDrafts.find(d=>d.record.id===s.draft.id);
   if(!draft&&!committed){draft={record:structuredClone(s.draft),expectedVersion:null,caret:0,savedAt:now,metadataOnly:true,studySeed:true};data.documentDrafts.push(draft);}
   return {...snapshot(),draft:committed||draft.record,committed:!!committed};
  }
  if(command==='pause'){if(s.state==='running'){s.ms=elapsed(s);s.state='paused';}}
  else if(command==='resume'){if(s.state==='paused'){if(s.limitReached)throw Error('本次已达7天，请结束记录后重新开始');s.segmentStart=now;s.state='running';}}
  else if(command!=='title')throw Error('未知学习计时操作');return snapshot();
 }
 if(typeof module==='object'&&module.exports)module.exports={execute};else root.StudyTimerPreview={execute};
})(typeof window!=='undefined'?window:globalThis);
