(function (root) {
  'use strict';
  const localDate = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  const localTime = (d = new Date()) => `${localDate(d)}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`;
  const uid = () => 'r-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2,10);
  const normalizeText = s => String(s || '').normalize('NFKC').toLowerCase().replace(/[\s\p{P}\p{S}]/gu,'');
  function validate(r) {
    if (!r || !['learning','work'].includes(r.type)) throw Error('记录类型不正确');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(r.date || '') || localDate(new Date(r.date+'T12:00:00')) !== r.date) throw Error('日期无效');
    if (typeof r.title!=='string'||!r.title.trim()||r.title.length>300) throw Error('请填写标题，最多 300 字');
    if (r.tags!==undefined&&(!Array.isArray(r.tags)||r.tags.some(t=>typeof t!=='string'))) throw Error('标签格式不正确');
    if(r.note!==undefined&&typeof r.note!=='string')throw Error('笔记格式不正确');
    if(r.masteryLevel!==undefined&&!['unspecified','beginner','familiar','proficient'].includes(r.masteryLevel))throw Error('熟练度格式不正确');
    if(r.evidenceType!==undefined&&!['none','self','practice','tested'].includes(r.evidenceType))throw Error('掌握证据格式不正确');
    if(r.evidenceNote!==undefined&&(typeof r.evidenceNote!=='string'||r.evidenceNote.length>5000))throw Error('掌握证据最多5000字');
    if (!Number.isFinite(Number(r.durationMinutes)) || Number(r.durationMinutes)<0 || Number(r.durationMinutes)>10080) throw Error('时长应为 0～10080 分钟');
    if (r.type === 'work') {
      if(r.breaks!==undefined&&!Array.isArray(r.breaks))throw Error('休息时间格式不正确');
      if (!r.workStart || !Number.isFinite(Date.parse(r.workStart))) throw Error('请填写上班时间');
      const start = Date.parse(r.workStart), end = r.workEnd ? Date.parse(r.workEnd) : Date.now();
      if (r.workEnd && (!Number.isFinite(end) || end <= start)) throw Error('下班时间必须晚于上班时间；跨夜请选择次日');
      const intervals = [...(r.breaks || [])].sort((a,b)=>Date.parse(a.start)-Date.parse(b.start));
      let previous = start;
      intervals.forEach((b,i) => {
        const bs = Date.parse(b.start), be = b.end ? Date.parse(b.end) : end;
        if (!Number.isFinite(bs) || (b.end && !Number.isFinite(be)) || bs < start || (r.workEnd && be > end) || be < bs || bs < previous) throw Error('休息时间应处于班次内，且不能重叠');
        if (!b.end && (r.workEnd || i !== intervals.length-1)) throw Error('请先结束休息');
        previous = be;
      });
      if (r.workEnd && (end-start)>7*86400000) throw Error('单次工作不能超过七天');
    }
    return r;
  }
  function workMinutes(r, now = Date.now()) {
    const start = Date.parse(r.workStart), end = r.workEnd ? Date.parse(r.workEnd) : now;
    if (!Number.isFinite(start) || !Number.isFinite(end)) return {gross:0,rest:0,net:0};
    const gross = Math.max(0,end-start)/60000;
    const rest = (r.breaks || []).reduce((n,b)=>n+Math.max(0,Math.min(end,b.end?Date.parse(b.end):end)-Math.max(start,Date.parse(b.start)))/60000,0);
    return {gross,rest:Math.min(rest,gross),net:Math.max(0,gross-rest)};
  }
  function workMinutesOnDate(r,date, now=Date.now()) {
    const a = Date.parse(date+'T00:00:00'), next = new Date(date+'T00:00:00'); next.setDate(next.getDate()+1);
    return workMinutesInRange(r,a,next.getTime(),now).net;
  }
  function workMinutesInRange(r,a,b,now=Date.now()) {
    const start=Math.max(a,Date.parse(r.workStart)),end=Math.min(b,r.workEnd?Date.parse(r.workEnd):now);
    if (!Number.isFinite(start) || !Number.isFinite(end) || end<=start) return {gross:0,rest:0,net:0};
    const rest=(r.breaks||[]).reduce((n,x)=>n+Math.max(0,Math.min(end,x.end?Date.parse(x.end):now)-Math.max(start,Date.parse(x.start))),0);
    return {gross:(end-start)/60000,rest:rest/60000,net:Math.max(0,end-start-rest)/60000};
  }
  function questionPeriod(question,now=new Date()) {
    const today=new Date(localDate(now)+'T00:00:00');let start=new Date(today),end=new Date(today);
    question=String(question).normalize('NFKC').replace(/\s/g,'');
    const explicit=question.match(/\d{4}-\d{2}-\d{2}/g);
    const cn=n=>{if(/^\d+$/.test(n))return Number(n);const map={'零':0,'一':1,'二':2,'两':2,'三':3,'四':4,'五':5,'六':6,'七':7,'八':8,'九':9};if(n.includes('十')){const p=n.split('十');return (p[0]?map[p[0]]:1)*10+(p[1]?map[p[1]]:0);}return map[n];};
    const number='([零一二两三四五六七八九十\\d]{1,3})';
    const relative=question.match(new RegExp('(上上个月|上上月|上个月|上月|这个月|本月|这月)'+number+'(?:号|日)'));
    const chinese=question.match(new RegExp('(?:(\\d{4})年)?'+number+'月'+number+'(?:号|日)?'));
    const checked=s=>{const d=new Date(s+'T00:00:00');if(!Number.isFinite(d.getTime())||localDate(d)!==s)throw Error('查询日期不存在，请核对日期后重新提问');return d;};
    if(explicit){start=checked(explicit[0]);end=checked(explicit[1]||explicit[0]);if(end<start)throw Error('查询结束日期不能早于开始日期');}
    else if(relative){const offset=/上上/.test(relative[1])?-2:/上/.test(relative[1])?-1:0;const first=new Date(today);first.setDate(1);first.setMonth(first.getMonth()+offset);start=checked(`${first.getFullYear()}-${String(first.getMonth()+1).padStart(2,'0')}-${String(cn(relative[2])).padStart(2,'0')}`);end=new Date(start);}
    else if(chinese){start=checked(`${chinese[1]||today.getFullYear()}-${String(cn(chinese[2])).padStart(2,'0')}-${String(cn(chinese[3])).padStart(2,'0')}`);end=new Date(start);}
    else if(/昨天|昨日/.test(question)){start.setDate(start.getDate()-1);end=new Date(start);}
    else if(/前天/.test(question)){start.setDate(start.getDate()-2);end=new Date(start);}
    else if(/上周|上星期/.test(question)){start.setDate(start.getDate()-((start.getDay()+6)%7)-7);end=new Date(start);end.setDate(end.getDate()+6);}
    else if(/本周|这周|本星期|这个星期|这星期/.test(question))start.setDate(start.getDate()-((start.getDay()+6)%7));
    else if(/近七天|近7天|最近一周/.test(question))start.setDate(start.getDate()-6);
    else if(/本月|这个月/.test(question))start.setDate(1);
    else if(/上个月|上月/.test(question)){start.setDate(1);start.setMonth(start.getMonth()-1);end=new Date(today);end.setDate(0);}
    else if(!/今天|今日/.test(question))return null;
    const after=new Date(end);after.setDate(after.getDate()+1);
    return {start:localDate(start),end:localDate(end),a:start.getTime(),b:after.getTime()};
  }
  function localContext(records,question,now=new Date()) {
    const period=questionPeriod(question,now),time=now.getTime();
    const work=r=>period?workMinutesInRange(r,period.a,period.b,time):workMinutes(r,time);
    const candidates=records.filter(r=>!period||(r.type==='work'?work(r).gross>0:r.date>=period.start&&r.date<=period.end));
    const learning=candidates.filter(r=>r.type==='learning'),works=candidates.filter(r=>r.type==='work'),platforms={};
    learning.forEach(r=>{const key=r.platform||'未标记平台';platforms[key]=(platforms[key]||0)+Number(r.durationMinutes||0);});
    return {period:period?`${period.start} 至 ${period.end}`:'全部历史',summary:{totalMatchingRecords:candidates.length,learningCount:learning.length,learningMinutes:Math.round(learning.reduce((s,r)=>s+Number(r.durationMinutes||0),0)),workMinutes:Math.round(works.reduce((s,r)=>s+work(r).net,0)),restMinutes:Math.round(works.reduce((s,r)=>s+work(r).rest,0)),platforms},records:retrieval(candidates,question,14).map(r=>({...r,note:String(r.note||'').slice(0,3500),computedMinutes:Math.round(r.type==='work'?work(r).net:Number(r.durationMinutes||0))})),retrievalNote:'关键词检索的有限记录，汇总覆盖该范围的全部记录；工作和休息按自然日截取；不代表所有笔记全文。'};
  }
  function duplicate(candidate, records) {
    return records.find(r=> r.id!==candidate.id && r.type===candidate.type && r.date===candidate.date && (r.type==='work' ? r.workStart===candidate.workStart : (normalizeText(r.title)===normalizeText(candidate.title) && normalizeText(r.platform)===normalizeText(candidate.platform))));
  }
  function similar(candidate,records) {
    const tokens=s=>new Set([...normalizeText(s)]), a=tokens(candidate.title);
    return records.filter(r=>r.id!==candidate.id&&r.type===candidate.type&&r.date===candidate.date).map(r=>{
      const b=tokens(r.title); const common=[...a].filter(c=>b.has(c)).length;
      return {record:r,score:common/Math.max(1,new Set([...a,...b]).size)};
    }).filter(x=>x.score>=0.65).sort((a,b)=>b.score-a.score).slice(0,3);
  }
  function retrieval(records, question, limit=12) {
    const q=normalizeText(question), tokens=q.match(/[a-z0-9]{2,}|[\u4e00-\u9fff]{2}/g)||[];
    return records.map(r=>{
      const corpus=normalizeText([r.title,r.platform,r.note,(r.tags||[]).join(' '),r.date].join(' '));
      let score=tokens.reduce((s,t)=>s+(corpus.includes(t)?1:0),0);
      if (/学习|阅读|文章|视频/.test(question)&&r.type==='learning') score++;
      if (/工作|上班|休息|下班/.test(question)&&r.type==='work') score++;
      return {record:r,score};
    }).sort((a,b)=>b.score-a.score||b.record.date.localeCompare(a.record.date)).slice(0,limit).map(x=>x.record);
  }
  function aiRecords(items) {
    if (!Array.isArray(items) || items.length>30) throw Error('AI 返回的记录列表格式无效');
    return items.map(x=> {
      const r={id:uid(),type:x.type,date:x.date,title:String(x.title||'').slice(0,300),platform:String(x.platform||'').slice(0,100),contentType:['article','video','course','book','other'].includes(x.contentType)?x.contentType:'article',tags:Array.isArray(x.tags)?x.tags.slice(0,20).map(String):[],note:String(x.note||'').slice(0,30000),url:String(x.url||''),durationMinutes:Number(x.durationMinutes||0),workStart:x.workStart||'',workEnd:x.workEnd||'',breaks:Array.isArray(x.breaks)?x.breaks:[],source:'ai',status:'completed'};
      r.masteryLevel=x.masteryLevel||'unspecified';r.evidenceType=x.evidenceType||'none';r.evidenceNote=String(x.evidenceNote||'').slice(0,5000);
      if(r.type==='work') { r.workStart=r.workStart.length===16?r.workStart+':00':r.workStart;r.workEnd=r.workEnd.length===16?r.workEnd+':00':r.workEnd;r.breaks=r.breaks.map(b=>({start:b.start?.length===16?b.start+':00':b.start,end:b.end?.length===16?b.end+':00':b.end||''}));r.status=r.workEnd?'completed':'running'; r.durationMinutes=Math.round(workMinutes(r).net); }
      return validate(r);
    });
  }
  function parseAI(text) {
    const raw=String(text||'').trim().replace(/^```(?:json)?\s*/i,'').replace(/\s*```$/,'');
    let result;
    try { result=JSON.parse(raw); } catch { const a=raw.indexOf('{'),b=raw.lastIndexOf('}'); if(a<0||b<a)throw Error('模型没有返回可识别的数据，请换用支持 JSON 输出的模型或重新描述'); result=JSON.parse(raw.slice(a,b+1)); }
    if(typeof result.answer!=='string') throw Error('模型回复格式不正确');
    return {answer:result.answer,records:aiRecords(result.records||[]),citations:Array.isArray(result.citations)?result.citations.map(String):[]};
  }
  const api={localDate,localTime,uid,validate,workMinutes,workMinutesOnDate,workMinutesInRange,questionPeriod,localContext,duplicate,similar,retrieval,aiRecords,parseAI,normalizeText};
  if(typeof module==='object'&&module.exports)module.exports=api; else root.LifeDomain=api;
})(typeof window!=='undefined'?window:globalThis);
