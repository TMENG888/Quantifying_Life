(function (root) {
  'use strict';
  const localDate = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  const localTime = (d = new Date()) => `${localDate(d)}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`;
  const uid = () => 'r-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2,10);
  const normalizeText = s => String(s || '').normalize('NFKC').toLowerCase().replace(/[\s\p{P}\p{S}]/gu,'');
  const isThought = r => ['idea','experience'].includes(r?.type);
  const thoughtTitle = note => String(note||'').trim().split(/[\n。！？!?]/)[0].slice(0,60).trim() || '未命名记录';
  const genericLearningTitle = (title,platform='') => {const key=normalizeText(title);return !key||!!platform&&key===normalizeText(platform)||['视频号','微信视频号','视频号视频','微信','微信公众号','公众号','抖音','b站','哔哩哔哩','bilibili','youtube','小红书','知乎','视频','文章','课程','书籍','学习记录','首页','首页视频号','登录','用户登录','login','signin','打开app','在app中打开'].includes(key);};
  const missingLearningTitle = title => genericLearningTitle(title) || /未获取到|未识别|待补.*标题|未命名|待确认标题|无法获取.*标题/.test(String(title));
  // Strip independent tracking metadata, never minutes inside knowledge or quotations.
  function splitLearningInput(input){
    const text=String(input||''),parts=text.split(/([，,。；;\n])/),found=[];
    const number='(?:\\d+(?:\\.\\d+)?|[零一二两三四五六七八九十百]+|半)';
    const amount='('+number+')\\s*(?:个?小时|hours?|h)(?:\\s*('+number+')\\s*(?:分钟|分|minutes?|mins?))?|('+number+')\\s*(?:分钟|分|minutes?|mins?)';
    const pattern=new RegExp('^(?:(?:我)?(?:今天|刚刚|刚才)?(?:学习时长|学习时间|本次学习用时|本次用时)\\s*[:：为是]?\\s*|(?:我)?(?:今天|刚刚|刚才)?(?:学习了|学了|学习|学|看了|阅读了|读了|用时|花了)\\s*)('+amount+')(?:左右)?[。.!！]?$', 'i');
    const value=s=>{if(!s)return 0;if(s==='半')return .5;if(/^\d/.test(s))return Number(s);const digits={'零':0,'一':1,'二':2,'两':2,'三':3,'四':4,'五':5,'六':6,'七':7,'八':8,'九':9};let n=0,a=0;for(const c of s){if(c==='百'||c==='十'){n+=(a||1)*(c==='百'?100:10);a=0;}else a=digits[c];}return n+a;};
    const nonempty=[];for(let i=0;i<parts.length;i+=2)if(parts[i].trim())nonempty.push(i);
    for(const i of nonempty){const clause=parts[i].trim(),m=clause.match(pattern);if(!m)continue;if(i!==nonempty[0]&&i!==nonempty.at(-1)&&!/^学习时长|^学习时间|^本次学习用时|^本次用时/.test(clause))continue;const minutes=m[4]?value(m[4]):value(m[2])*60+value(m[3]);if(minutes>=0&&minutes<=10080)found.push({i,minutes});}
    if(!found.length||new Set(found.map(x=>x.minutes)).size>1)return {noteText:text,durationMinutes:null};
    const starts=[];let offset=0;for(const part of parts){starts.push(offset);offset+=part.length;}
    const ranges=found.map(({i})=>({start:i===nonempty[0]?0:starts[i]-parts[i-1].length,end:i===nonempty.at(-1)?text.length:starts[i]+parts[i].length+(i===nonempty[0]?parts[i+1].length:0)}));
    let note='',cursor=0;for(const range of ranges){note+=text.slice(cursor,Math.max(cursor,range.start));cursor=Math.max(cursor,range.end);}note+=text.slice(cursor);
    return {noteText:note.trim(),durationMinutes:found[0].minutes};
  }
  function learningTitle(note){const text=String(note||'').replace(/^(?:我)?(?:今天|刚刚|刚才)?(?:学习|阅读|看|读)(?:了)?(?:这个|这篇|这段)?(?:视频|文章|内容)?(?:内容)?[，,：:\s]*(?:有关|关于)?/,'').trim(),parts=text.split(/[\n。！？!?，,；;]/).map(s=>s.replace(/^(?:为最近的一个|这是一个|为一个|是一个|有关|关于)/,'').trim()).filter(Boolean);let title='';for(const part of parts.slice(0,3)){if(title.length+part.length+3>40)break;title+=(title?' · ':'')+part;}return title||(parts[0]?parts[0].slice(0,39)+(parts[0].length>39?'…':''):'学习笔记');}
  function validLocalTime(s){return typeof s==='string'&&/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(s)&&Number.isFinite(Date.parse(s))&&localTime(new Date(s))===s;}
  function validate(r,now=Date.now()) {
    if (!r || !['learning','work','work_note','idea','experience'].includes(r.type)) throw Error('记录类型不正确');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(r.date || '') || localDate(new Date(r.date+'T12:00:00')) !== r.date) throw Error('日期无效');
    if (typeof r.title!=='string'||!r.title.trim()||r.title.length>300) throw Error('请填写标题，最多 300 字');
    if (r.tags!==undefined&&(!Array.isArray(r.tags)||r.tags.some(t=>typeof t!=='string'))) throw Error('标签格式不正确');
    if(r.note!==undefined&&typeof r.note!=='string')throw Error('笔记格式不正确');
    if(r.wikiTopics!==undefined&&(!Array.isArray(r.wikiTopics)||r.wikiTopics.length>3||r.wikiTopics.some(t=>typeof t!=='string'||t.trim().length<2||t.length>60)))throw Error('Wiki主题元信息无效');
    if(r.masteryLevel!==undefined&&!['unspecified','beginner','familiar','proficient'].includes(r.masteryLevel))throw Error('熟练度格式不正确');
    if(r.evidenceType!==undefined&&!['none','self','practice','tested'].includes(r.evidenceType))throw Error('掌握证据格式不正确');
    if(r.evidenceNote!==undefined&&(typeof r.evidenceNote!=='string'||r.evidenceNote.length>5000))throw Error('掌握证据最多5000字');
    if (!Number.isFinite(Number(r.durationMinutes)) || Number(r.durationMinutes)<0 || Number(r.durationMinutes)>10080) throw Error('时长应为 0～10080 分钟');
    if(r.type==='work_note'){if(typeof r.note!=='string'||!r.note.trim())throw Error('请填写工作日志内容，');if(Number(r.durationMinutes)!==0||r.workStart||r.workEnd)throw Error('工作日志不包含班次或工时');if(r.occurredAt&&(!validLocalTime(r.occurredAt)||r.occurredAt.slice(0,10)!==r.date))throw Error('工作日志时间与日期不一致');}
    if(isThought(r)){
      if(!validLocalTime(r.occurredAt)||r.occurredAt.slice(0,10)!==r.date)throw Error('灵感／经验的发生时间无效，日期须与时间点一致');
      if(typeof r.note!=='string'||!r.note.trim())throw Error('请填写灵感或经验内容，');
      if(Number(r.durationMinutes)!==0)throw Error('灵感／经验不记录学习或工作时长');
      if((r.masteryLevel&&r.masteryLevel!=='unspecified')||(r.evidenceType&&r.evidenceType!=='none'))throw Error('灵感／经验不作为掌握程度证据');
    }
    if (r.type === 'work') {
      if(r.breaks!==undefined&&!Array.isArray(r.breaks))throw Error('休息时间格式不正确');
      if (!r.workStart || !Number.isFinite(Date.parse(r.workStart))) throw Error('请填写上班时间');
      const start = Date.parse(r.workStart), end = r.workEnd ? Date.parse(r.workEnd) : now;
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
    return {period:period?`${period.start} 至 ${period.end}`:'全部历史',summary:{totalMatchingRecords:candidates.length,learningCount:learning.length,learningMinutes:Math.round(learning.reduce((s,r)=>s+Number(r.durationMinutes||0),0)),workNoteCount:candidates.filter(r=>r.type==='work_note').length,ideaCount:candidates.filter(r=>r.type==='idea').length,experienceCount:candidates.filter(r=>r.type==='experience').length,grossWorkMinutes:Math.round(works.reduce((s,r)=>s+work(r).gross,0)),netWorkMinutes:Math.round(works.reduce((s,r)=>s+work(r).net,0)),workMinutesBasis:'net_rest_already_deducted',workMinutes:Math.round(works.reduce((s,r)=>s+work(r).net,0)),restMinutes:Math.round(works.reduce((s,r)=>s+work(r).rest,0)),platforms},records:retrieval(candidates,question,14).map(r=>({...r,note:String(r.note||'').slice(0,3500),computedMinutes:Math.round(r.type==='work'?work(r).net:Number(r.durationMinutes||0))})),retrievalNote:'关键词检索的有限记录，汇总覆盖该范围的全部记录；工作和休息按自然日截取；不代表所有笔记全文。'};
  }
  function duplicate(candidate, records) {
    return records.find(r=> r.id!==candidate.id && r.type===candidate.type && r.date===candidate.date && (r.type==='work' ? r.workStart===candidate.workStart : r.type==='work_note' ? String(r.note||'').trim()===String(candidate.note||'').trim() : (normalizeText(r.title)===normalizeText(candidate.title) && normalizeText(r.platform)===normalizeText(candidate.platform))));
  }
  function similar(candidate,records) {
    if(candidate.type==='work_note')return [];const tokens=s=>new Set([...normalizeText(s)]), a=tokens(candidate.title);
    return records.filter(r=>r.id!==candidate.id&&r.type===candidate.type&&r.date===candidate.date).map(r=>{
      const b=tokens(r.title); const common=[...a].filter(c=>b.has(c)).length;
      return {record:r,score:common/Math.max(1,new Set([...a,...b]).size)};
    }).filter(x=>x.score>=0.65).sort((a,b)=>b.score-a.score).slice(0,3);
  }
  function retrieval(records, question, limit=12) {
    const q=normalizeText(question), tokens=q.match(/[a-z0-9]{2,}|[\u4e00-\u9fff]{2}/g)||[];
    return records.map(r=>{
      const corpus=normalizeText([r.title,r.platform,r.note,(r.tags||[]).join(' '),r.date,r.occurredAt].join(' '));
      let score=tokens.reduce((s,t)=>s+(corpus.includes(t)?1:0),0);
      if (/学习|阅读|文章|视频/.test(question)&&r.type==='learning') score++;
      if (/工作|上班|休息|下班/.test(question)&&['work','work_note'].includes(r.type)) score++;
      if (/灵感|创意|点子/.test(question)&&r.type==='idea') score+=2;
      if (/经验|感悟|启发|反思/.test(question)&&r.type==='experience') score+=2;
      return {record:r,score};
    }).sort((a,b)=>b.score-a.score||b.record.date.localeCompare(a.record.date)).slice(0,limit).map(x=>x.record);
  }
  function aiRecords(items) {
    if (!Array.isArray(items) || items.length>30) throw Error('AI 返回的记录列表格式无效');
    return items.flatMap(x=> {
      const r={id:uid(),type:x.type,date:x.date,title:String(x.title||'').slice(0,300),platform:String(x.platform||'').slice(0,100),contentType:['article','video','course','book','other'].includes(x.contentType)?x.contentType:'article',tags:Array.isArray(x.tags)?x.tags.slice(0,20).map(String):[],note:String(x.note||''),url:String(x.url||''),durationMinutes:Number(x.durationMinutes||0),workStart:x.workStart||'',workEnd:x.workEnd||'',breaks:Array.isArray(x.breaks)?x.breaks:[],source:'ai',status:'completed'};
      if(r.type==='learning'){const fields=splitLearningInput(r.note);if(fields.durationMinutes!==null){r.note=fields.noteText;r.durationMinutes=fields.durationMinutes;}}
      if(r.type==='learning'&&missingLearningTitle(r.title)&&r.note.trim()){r.title=learningTitle(r.note);r.titleOrigin='note';}else if(r.type==='learning'&&['note','web','user'].includes(x.titleOrigin))r.titleOrigin=x.titleOrigin;
      r.masteryLevel=x.masteryLevel||'unspecified';r.evidenceType=x.evidenceType||'none';r.evidenceNote=String(x.evidenceNote||'').slice(0,5000);
      if(r.type==='work_note'){r.occurredAt=String(x.occurredAt||'');if(r.occurredAt.length===16)r.occurredAt+=':00';r.title=r.title.trim()||thoughtTitle(r.note);r.durationMinutes=0;r.workStart=r.workEnd='';r.breaks=[];r.masteryLevel='unspecified';r.evidenceType='none';r.evidenceNote='';}
      if(isThought(r)){r.occurredAt=String(x.occurredAt||'');if(r.occurredAt.length===16)r.occurredAt+=':00';r.title=r.title.trim()||thoughtTitle(r.note);r.durationMinutes=0;r.masteryLevel='unspecified';r.evidenceType='none';r.evidenceNote='';r.contentType='other';}
      if(r.type==='work') { r.workStart=r.workStart.length===16?r.workStart+':00':r.workStart;r.workEnd=r.workEnd.length===16?r.workEnd+':00':r.workEnd;r.breaks=r.breaks.map(b=>({start:b.start?.length===16?b.start+':00':b.start,end:b.end?.length===16?b.end+':00':b.end||''}));r.status=r.workEnd?'completed':'running'; r.durationMinutes=Math.round(workMinutes(r).net); }
      if(r.type==='work'&&r.note.trim()){const log=aiRecords([{type:'work_note',date:r.date,title:thoughtTitle(r.note),note:r.note,tags:r.tags,durationMinutes:0}])[0];r.note='';return [validate(r),log];}return [validate(r)];
    });
  }
  function parseAI(text,allowRecords=true) {
    const raw=String(text||'').trim().replace(/^```(?:json)?\s*/i,'').replace(/\s*```$/,'');
    let result;
    try { result=JSON.parse(raw); } catch { const a=raw.indexOf('{'),b=raw.lastIndexOf('}'); if(a<0||b<a)throw Error('模型没有返回可识别的数据，请换用支持 JSON 输出的模型或重新描述'); result=JSON.parse(raw.slice(a,b+1)); }
    if(typeof result.answer!=='string') throw Error('模型回复格式不正确');
    return {answer:result.answer,records:allowRecords?aiRecords(result.records||[]):[],citations:Array.isArray(result.citations)?result.citations.map(String):[]};
  }
  const api={localDate,localTime,uid,validate,workMinutes,workMinutesOnDate,workMinutesInRange,questionPeriod,localContext,duplicate,similar,retrieval,aiRecords,parseAI,normalizeText,isThought,thoughtTitle,learningTitle,missingLearningTitle,genericLearningTitle,splitLearningInput,validLocalTime};
  if(typeof module==='object'&&module.exports)module.exports=api; else root.LifeDomain=api;
})(typeof window!=='undefined'?window:globalThis);
