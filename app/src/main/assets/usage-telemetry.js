(function(root){
 'use strict';const node=typeof module==='object'&&module.exports;
 const pages={today:'首页',learning:'学习',work:'工作',thoughts:'随笔',creation:'自媒体创作',tracks:'轨迹',chat:'AI 助手',meditation:'禅定',study:'学习计时',settings:'模型配置',statistics:'主题统计',usage:'使用反馈',editor:'记录编辑',reader:'记录阅读',dialog:'其他面板'};
 const features={study_start:'开始学习计时',study_finish:'完成学习计时',chat_send:'发送 AI 消息',manual_learning:'手写学习入口',manual_work_note:'手写工作日志入口',manual_essay:'手写随笔入口',meditation_start:'开始禅定',meditation_finish:'完成禅定',work_clock:'工作打卡',tags:'编辑标签',format:'AI 排版',metadata:'AI 补全标题与标签',image:'插入图片',illustration:'生成配图',feishu:'飞书导入',manual_learning_saved:'手写学习已保存',ai_learning_saved:'AI 学习已保存',record_saved:'记录保存',chat_reply:'助手回复',layout:'排版请求',image_request:'配图提交'};
 const storageKey='zhishi-usage-v1',empty=()=>({version:1,enabled:true,since:'',days:{}});
 function dayKey(ms){const d=new Date(ms);return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}
 function create(storage,clock=()=>Date.now()){
  let data=empty(),screen='',last=clock(),active=last,main='today',visible=true,lastSession=0,fault=false;
  try{const raw=JSON.parse(storage.getItem(storageKey)||'null');if(raw?.version===1&&raw.days&&typeof raw.days==='object')data=raw;}catch{fault=true;}lastSession=Number(data.lastActive)||0;
  function persist(){try{const cutoff=dayKey(clock()-179*86400000);for(const d of Object.keys(data.days))if(d<cutoff)delete data.days[d];storage.setItem(storageKey,JSON.stringify(data));fault=false;}catch{fault=true;}}
  function day(at=clock()){const date=dayKey(at);if(!data.since)data.since=date;return data.days[date]||(data.days[date]={sessions:0,pages:{},events:{},paths:{}});}
  function event(id,status='tap'){if(!data.enabled||!features[id]||!['tap','success','failure'].includes(status))return;const d=day(),v=d.events[id]||(d.events[id]={tap:0,success:0,failure:0});v[status]++;persist();}
  function flush(persistNow=true){const now=clock(),end=Math.min(now,active+60000);if(data.enabled&&visible&&pages[screen]&&end>last){let cursor=last;while(cursor<end){const next=new Date(dayKey(cursor)+'T00:00:00');next.setDate(next.getDate()+1);const until=Math.min(end,next.getTime()),d=day(cursor),p=d.pages[screen]||(d.pages[screen]={visits:0,ms:0});p.ms+=until-cursor;cursor=until;}}last=now;if(persistNow)persist();}
  function interact(){flush(false);session();active=clock();lastSession=active;data.lastActive=active;}
  function enter(id){if(!pages[id]||id===screen)return;flush();const old=screen;screen=id;if(data.enabled&&visible){const d=day(),p=d.pages[id]||(d.pages[id]={visits:0,ms:0});p.visits++;if(old){const path=old+'>'+id;d.paths[path]=(d.paths[path]||0)+1;}persist();}}
  function visibility(value){flush();visible=value;last=clock();if(value){active=last;session();}}
  function session(){if(data.enabled&&(!lastSession||clock()-lastSession>30*60000)){day().sessions++;lastSession=clock();data.lastActive=lastSession;persist();}}
  function enabled(value){flush();data.enabled=!!value;last=active=clock();persist();}
  function clear(){const on=data.enabled;data=empty();data.enabled=on;screen='';last=active=clock();lastSession=0;persist();}
  function summary(days=30){flush();const now=clock(),cutoff=days?dayKey(now-(days-1)*86400000):'',result={since:data.since,enabled:data.enabled,fault,activeDays:0,sessions:0,pages:[],events:[],paths:[]},ps={},es={},ts={};
   for(const [date,d] of Object.entries(data.days)){if(date<cutoff)continue;result.activeDays++;result.sessions+=d.sessions||0;for(const [id,p] of Object.entries(d.pages||{})){if(!pages[id])continue;const v=ps[id]||(ps[id]={id,label:pages[id],visits:0,ms:0});v.visits+=p.visits||0;v.ms+=p.ms||0;}for(const [id,p] of Object.entries(d.events||{})){if(!features[id])continue;const v=es[id]||(es[id]={id,label:features[id],tap:0,success:0,failure:0});for(const k of ['tap','success','failure'])v[k]+=p[k]||0;}for(const [path,count] of Object.entries(d.paths||{})){const [a,b]=path.split('>');if(pages[a]&&pages[b])ts[path]=(ts[path]||0)+count;}}
   result.pages=Object.values(ps).sort((a,b)=>b.ms-a.ms||b.visits-a.visits);result.events=Object.values(es).sort((a,b)=>(b.tap+b.success+b.failure)-(a.tap+a.success+a.failure));result.paths=Object.entries(ts).map(([path,count])=>({path,label:path.split('>').map(x=>pages[x]).join(' → '),count})).sort((a,b)=>b.count-a.count);return result;
  }
  return {event,flush,interact,enter,visibility,session,enabled,clear,summary,setMain:id=>{main=id;},main:()=>main};
 }
 function recommendations(s){const notes=[];if(s.activeDays<7||s.sessions<10)return ['观察期不足：至少积累 7 个活跃日、10 次会话，再判断低频功能。'];const ev=id=>s.events.find(e=>e.id===id)||{tap:0,success:0,failure:0},pg=id=>s.pages.find(p=>p.id===id)||{visits:0,ms:0};
  const ai=ev('ai_learning_saved').success,manual=ev('manual_learning_saved').success;if(ai+manual>=10&&ai/(ai+manual)>=.8)notes.push('学习保存来源：AI '+ai+' 次、手写 '+manual+' 次。可优先突出 AI 入口，将手写入口弱化或折叠；不要直接删除备用输入方式。');
  if(pg('chat').visits>=10&&pg('today').ms<pg('chat').ms*.1)notes.push('AI 页访问 '+pg('chat').visits+' 次，首页活跃停留不足 AI 页的 10%。可考虑增加默认打开 AI 页的选项，不代表首页没有价值。');
  if(ev('meditation_start').tap<2)notes.push('观察范围内禅定开始操作 '+ev('meditation_start').tap+' 次。可考虑折叠入口；低频不等于无用，也可能尚未发现或仅偶尔需要。');
  for(const f of s.events){const completed=f.success+f.failure;if(completed>=3&&f.failure/completed>=.2)notes.push(f.label+'：'+f.failure+' / '+completed+' 次结果失败，应先排查可靠性，再评估功能需求。');}
  if(ev('manual_learning').tap>=5&&manual/ev('manual_learning').tap<.3)notes.push('手写学习入口 '+ev('manual_learning').tap+' 次、保存 '+manual+' 次，建议检查编辑流程。入口与保存次数仅是粗略对照，不是严格漏斗或放弃率。');
  return notes.length?notes:['暂未发现足够明确的调整信号，继续观察；这些是使用习惯线索，不是功能价值评分。'];
 }
 if(node){module.exports={create,recommendations,pages,features,storageKey};return;}
 let store;try{store=root.localStorage;}catch{store={getItem:()=>null,setItem:()=>{throw Error('unavailable');}};}const tracker=create(store);
 function update(){const modal=document.getElementById('editor');tracker.enter(modal?.open?(document.getElementById('doc-body')?(!document.getElementById('doc-read').hidden?'reader':'editor'):'dialog'):tracker.main());}
 const clickMap={'add-thought':'manual_essay','add-learning':'manual_learning','add-work-note':'manual_work_note','add-idea':'manual_essay','add-experience':'manual_essay','start-work':'work_clock','stop-work':'work_clock','toggle-break':'work_clock'};
 document.addEventListener('click',ev=>{tracker.interact();const b=ev.target.closest('button');if(!b)return;const id=b.id,f=b.hasAttribute('data-study-open')||id==='study-start'?'study_start':id==='study-finish'?'study_finish':id==='meditation-start'?'meditation_start':id==='meditation-end'?'meditation_finish':id==='doc-info'?'tags':id==='doc-format'?'format':id==='doc-complete'?'metadata':id==='doc-image'?'image':id==='doc-illustrate'?'illustration':b.classList.contains('chat-send')?'chat_send':clickMap[b.dataset.action||b.dataset.quick];if(f)tracker.event(f);setTimeout(update,0);},true);
 for(const name of ['pointerdown','keydown','scroll'])document.addEventListener(name,()=>tracker.interact(),{passive:true,capture:true});
 document.addEventListener('visibilitychange',()=>{tracker.visibility(!document.hidden);if(!document.hidden)update();});root.addEventListener('pagehide',tracker.flush);setInterval(()=>tracker.flush(),15000);tracker.session();
 const apiFeatures={saveDocument:'record_saved',saveRecord:'record_saved',feishuCommit:'feishu',createIllustration:'image_request',layoutRecord:'layout'};
 root.UsageTelemetry={...tracker,recommendations,screen:id=>{tracker.setMain(pages[id]?id:'today');update();},result:(action,payload,success)=>{const f=action==='ask'&&payload?.tool?.name==='plan_record_layout'?'layout':apiFeatures[action];if(f)tracker.event(f,success?'success':'failure');if(success&&['saveDocument','saveRecord'].includes(action)&&payload?.record?.type==='learning'&&payload.record.source!=='timer')tracker.event(payload.record.source==='ai'?'ai_learning_saved':'manual_learning_saved','success');},chatResult:ok=>tracker.event('chat_reply',ok?'success':'failure')};
})(typeof window!=='undefined'?window:globalThis);
