(function(root){
  'use strict';
  const D=typeof module==='object'&&module.exports?require('./domain.js'):root.LifeDomain;
  function parseJSON(text){const raw=String(text||'').trim().replace(/^```(?:json)?\s*/i,'').replace(/\s*```$/,'');try{return JSON.parse(raw);}catch(e){const a=raw.indexOf('{'),b=raw.lastIndexOf('}');if(a<0||b<a)throw Error('模型返回的检索计划或 Wiki 格式无法识别');return JSON.parse(raw.slice(a,b+1));}}
  function validatePlan(value){const p=typeof value==='string'?parseJSON(value):value;
    if(!p||!['record','history','topic','insight','general'].includes(p.intent))throw Error('检索意图格式不正确');
    if(typeof p.topic!=='string'||p.topic.length>100||!Array.isArray(p.keywords)||p.keywords.length>8||p.keywords.some(x=>typeof x!=='string'||x.length>60))throw Error('检索主题或关键词格式不正确');
    for(const k of ['dateStart','dateEnd'])if(typeof p[k]!=='string'||(p[k]&&!/^\d{4}-\d{2}-\d{2}$/.test(p[k])))throw Error('检索日期格式不正确');
    if(Boolean(p.dateStart)!==Boolean(p.dateEnd))throw Error('检索日期范围不完整');if(p.dateStart)D.questionPeriod(p.dateStart+'至'+p.dateEnd);
    return {...p,topic:p.topic.trim(),keywords:[...new Set(p.keywords.map(x=>x.trim()).filter(Boolean))]};
  }
  function fingerprint(r){const raw=JSON.stringify([r.id,r.type,r.date,r.title,r.platform,r.contentType,r.tags,r.note,r.durationMinutes,r.workStart,r.workEnd,r.breaks,r.masteryLevel,r.evidenceType,r.evidenceNote]);let h=2166136261;for(let i=0;i<raw.length;i++){h^=raw.charCodeAt(i);h=Math.imul(h,16777619);}return (h>>>0).toString(16);}
  function topicMatch(r,words){const corpus=D.normalizeText([r.title,r.note,(r.tags||[]).join(' ')].join(' '));return words.some(w=>{const n=D.normalizeText(w);return n.length>=2&&corpus.includes(n);});}
  function topicRecords(records,topic,keywords=[],pages=[]){const related=pages.filter(p=>[p.topic,...(p.aliases||[])].some(x=>D.normalizeText(x)===D.normalizeText(topic)));const words=[topic,...keywords,...related.flatMap(p=>[p.topic,...p.aliases,...(p.searchKeywords||[])])].filter(Boolean);return records.filter(r=>r.type==='learning'&&topicMatch(r,words)).sort((a,b)=>a.date.localeCompare(b.date)||a.id.localeCompare(b.id));}
  function topicStats(records){const seen=new Set(),days=new Set(),platforms={},types={};let cumulativeMinutes=0;const milestones=[];
    for(let i=0;i<records.length;i++){const r=records[i];cumulativeMinutes+=Number(r.durationMinutes||0);days.add(r.date);seen.add(D.normalizeText(r.title)+'|'+D.normalizeText(r.platform));platforms[r.platform||'未标记平台']=(platforms[r.platform||'未标记平台']||0)+1;types[r.contentType||'article']=(types[r.contentType||'article']||0)+1;
      if(r.masteryLevel&&r.masteryLevel!=='unspecified')milestones.push({recordId:r.id,date:r.date,level:r.masteryLevel,evidenceType:r.evidenceType||'self',evidenceNote:r.evidenceNote||'',cumulativeRecords:i+1,cumulativeMinutes:Math.round(cumulativeMinutes),uniqueContents:seen.size});
    }
    return {recordCount:records.length,uniqueContents:seen.size,articleRecords:types.article||0,learningMinutes:Math.round(cumulativeMinutes),learningDays:days.size,firstDate:records[0]?.date||null,lastDate:records[records.length-1]?.date||null,platformCounts:platforms,milestones,evidenceCaveat:'熟练度是用户填写的阶段性自评或实践/测验证据，累计时间是关联而非因果门槛；没有熟练度标记时不能从文章数和时长断言熟练。'};
  }
  function isStale(page,records){const sources=topicRecords(records,page.topic,page.searchKeywords||[],[]),indexed=page.indexSourceIds||page.sourceIds;if(sources.length!==indexed.length)return true;return sources.some(r=>page.fingerprints?.[r.id]!==fingerprint(r));}
  function validatePage(raw,sources,topic){const p=typeof raw==='string'?parseJSON(raw):raw;
    if(!p||typeof p.summary!=='string'||p.summary.length>12000||!Array.isArray(p.aliases)||p.aliases.length>12||p.aliases.some(x=>typeof x!=='string'||x.length>60)||!Array.isArray(p.concepts)||p.concepts.length>40)throw Error('Wiki 知识页格式不正确');
    const ids=new Set(sources.map(r=>r.id));
    const concepts=p.concepts.map(c=>{if(typeof c.title!=='string'||c.title.length>200||typeof c.description!=='string'||c.description.length>2000||!Array.isArray(c.sourceIds)||!c.sourceIds.length||c.sourceIds.some(id=>!ids.has(id)))throw Error('Wiki 概念必须引用当前输入中真实存在的学习记录');return {title:c.title,description:c.description,sourceIds:[...new Set(c.sourceIds)]};});
    if(!Array.isArray(p.sourceIds)||!p.sourceIds.length||p.sourceIds.some(id=>!ids.has(id)))throw Error('Wiki 摘要来源无效');
    const sourceIds=sources.map(r=>r.id);return {id:'wiki-'+D.normalizeText(topic),topic,summary:p.summary,aliases:[...new Set(p.aliases.filter(x=>x.trim()))],concepts,sourceIds,fingerprints:Object.fromEntries(sources.map(r=>[r.id,fingerprint(r)])),stats:topicStats(sources),updatedAt:Date.now(),compiledFrom:sources.length};
  }
  function compilerBatches(records){const batches=[];let batch=[],size=0;for(const r of records){const item={...r,note:String(r.note||'').slice(0,6000)},n=JSON.stringify(item).length;if(batch.length&&(batch.length>=20||size+n>36000)){batches.push(batch);batch=[];size=0;}batch.push(item);size+=n;}if(batch.length)batches.push(batch);return batches;}
  function indexPage(page,records,keywords=[]){
    page.searchKeywords=[...new Set([...keywords,...page.aliases])];
    const indexed=topicRecords(records,page.topic,page.searchKeywords);
    page.indexSourceIds=indexed.map(r=>r.id);page.fingerprints=Object.fromEntries(indexed.map(r=>[r.id,fingerprint(r)]));page.sourceVersions=Object.fromEntries(indexed.map(r=>[r.id,r.updatedAt||0]));page.stats=topicStats(indexed);page.notesTruncated=indexed.some(r=>String(r.note||'').length>6000);page.partial=page.sourceIds.length<indexed.length||page.notesTruncated;return page;
  }
  function context(records,pages,question,plan,now=new Date()){
    const parsed=validatePlan(plan);const localPeriod=D.questionPeriod(question,now),period=localPeriod||(parsed.dateStart?D.questionPeriod(parsed.dateStart+'至'+parsed.dateEnd,now):null);
    const scoped=records.filter(r=>!period||(r.type==='work'?D.workMinutesInRange(r,period.a,period.b,now.getTime()).gross>0:r.date>=period.start&&r.date<=period.end));
    const dateQuestion=period?period.start+'至'+period.end:'全部历史';const base=D.localContext(scoped,dateQuestion,now);
    const related=parsed.topic?topicRecords(scoped,parsed.topic,parsed.keywords,pages):[];
    const pageMatches=pages.filter(p=>parsed.topic&&[p.topic,...p.aliases].some(x=>D.normalizeText(x)===D.normalizeText(parsed.topic))&&(!period||p.sourceIds.every(id=>scoped.some(r=>r.id===id)))).map(p=>{const stale=isStale(p,records);return {topic:p.topic,summary:stale?'':p.summary,concepts:stale?[]:p.concepts,sourceIds:stale?[]:p.sourceIds,stale,updatedAt:p.updatedAt,compiledFrom:p.compiledFrom,indexedRecords:(p.indexSourceIds||p.sourceIds).length,partial:Boolean(p.partial),notesTruncated:Boolean(p.notesTruncated)};});
    const useTopic=['topic','insight'].includes(parsed.intent)&&Boolean(parsed.topic);
    if(useTopic)base.records=D.retrieval(related,question,24).map(r=>({...r,note:String(r.note||'').slice(0,3500)}));
    const dates=new Set();scoped.forEach(r=>{if(!period||(r.date>=period.start&&r.date<=period.end))dates.add(r.date);if(r.type==='work'){const a=new Date(r.workStart),b=new Date(r.workEnd||now);for(let i=0;D.localDate(a)<=D.localDate(b)&&i<3660;i++,a.setDate(a.getDate()+1))if(!period||(a.getTime()<period.b&&a.getTime()>=period.a))dates.add(D.localDate(a));}});
    const dailySeries=[...dates].sort().slice(-180).map(date=>{const all=scoped.filter(r=>r.type==='learning'&&r.date===date);return {date,learningRecords:all.length,learningMinutes:Math.round(all.reduce((n,r)=>n+Number(r.durationMinutes||0),0)),workMinutes:Math.round(scoped.filter(r=>r.type==='work').reduce((n,r)=>n+D.workMinutesOnDate(r,date,now.getTime()),0)),topicLearningMinutes:Math.round(related.filter(r=>r.date===date).reduce((n,r)=>n+Number(r.durationMinutes||0),0))};});
    return {...base,plan:parsed,topic:parsed.topic||null,topicSummary:parsed.topic?topicStats(related):null,wikiPages:pageMatches,dailySeries,records:base.records,availableCitationIds:[...new Set([...base.records.map(r=>r.id),...pageMatches.filter(p=>!p.stale).flatMap(p=>p.sourceIds)])],coverage:{period:base.period,totalRecords:scoped.length,topicRecords:related.length,returnedSourceRecords:base.records.length,dailySeriesDays:dailySeries.length,dailySeriesTruncated:dates.size>180},retrievalNote:'LLM 检索规划 + 本地精确日期/时长统计 + 持久化 LLM Wiki 主题知识页。原始记录为事实来源，Wiki 为模型整理，不是向量库或外部知识真值；未记录日期不是零投入。'};
  }
  const api={parseJSON,validatePlan,fingerprint,topicRecords,topicStats,isStale,validatePage,compilerBatches,indexPage,context};if(typeof module==='object'&&module.exports)module.exports=api;else root.LifeWiki=api;
})(typeof window!=='undefined'?window:globalThis);
