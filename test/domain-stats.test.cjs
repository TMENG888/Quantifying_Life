const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const T=require('../app/src/main/assets/topic-stats.js'),D=require('../app/src/main/assets/domain-stats.js');
const now=new Date('2026-10-27T16:00:00'),row=(id,tags,minutes=30)=>({id,type:'learning',date:'2026-10-27',title:'来源 '+id,note:'不能被分类改写',tags,durationMinutes:minutes});
test('coarse fields merge related topics without replacing source tags or multiplying within-field minutes',()=>{const records=[row('a',['RAG','PageIndex','LLM Wiki']),row('b',['数据治理','数据驱动']),row('c',[])],before=JSON.stringify(records),data=T.summarize(records,{range:'all',now}),b=D.summarize(data);assert.equal(b.find(x=>x.label==='AI 与技术').minutes,30);assert.equal(b.find(x=>x.label==='AI 与技术').topics.length,3);assert.equal(b.find(x=>x.label==='商业与管理').count,1);assert.equal(b.find(x=>x.label==='待分类').count,1);assert.equal(JSON.stringify(records),before);assert.equal(data.total.minutes,90);});
test('cross-field overlap remains explicit while summary total stays unique',()=>{const data=T.summarize([row('a',['RAG','历史'],50)],{range:'all',now}),b=D.summarize(data);assert.equal(data.total.minutes,50);assert.equal(b.reduce((n,x)=>n+x.minutes,0),100);assert.deepEqual(b.map(x=>x.ids),[['a'],['a']]);});
test('unknown topics do not invent a semantic match, mapping is explicit and local to module',()=>{assert.equal(D.label('不确定的陌生主题'),'其他主题');assert.equal(D.label('RAG','learning',{rag:'我的研究'}),'我的研究');assert.equal(D.label('RAG','work'),'产品与技术');assert.equal(D.label('  ＡＩ  '),'AI 与技术');assert.equal(D.label(''),'未分类');});
test('work domains use exact day-clipped net shifts, never spread shift time across work journals',()=>{const records=[{id:'w',type:'work',date:'2026-10-26',workStart:'2026-10-26T23:00:00',workEnd:'2026-10-27T02:00:00',breaks:[{start:'2026-10-27T00:30:00',end:'2026-10-27T01:00:00'}],tags:['开发','编程']},{id:'n',type:'work_note',date:'2026-10-27',tags:['客户沟通'],durationMinutes:999}];const data=T.summarize(records,{module:'work',range:'today',now}),b=D.summarize(data);assert.equal(data.total.minutes,90);assert.equal(b.find(x=>x.label==='产品与技术').minutes,90);assert.equal(b.find(x=>x.label==='产品与技术').shifts,1);assert.equal(b.find(x=>x.label==='沟通与协作').count,1);assert.equal(b.find(x=>x.label==='沟通与协作').minutes,0);});
test('essays aggregate experience and ideas, with zero invented duration and genuine active days',()=>{const rows=[{id:'a',type:'idea',date:'2026-10-27',tags:['产品设计'],durationMinutes:200},{id:'b',type:'experience',date:'2026-10-26',tags:['产品设计']}],b=D.summarize(T.summarize(rows,{module:'thoughts',range:'all',now}));assert.equal(b[0].count,2);assert.equal(b[0].days,2);assert.equal(b[0].minutes,0);});
test('field ranking supports count and time separately and handles empty history',()=>{const data=T.summarize([row('a',['历史'],1),row('b',['历史'],1),row('c',['RAG'],100)],{range:'all',now});assert.equal(D.summarize(data,{},'count')[0].label,'人文与阅读');assert.equal(D.summarize(data,{},'minutes')[0].label,'AI 与技术');assert.deepEqual(D.summarize(T.summarize([],{now})),[]);});
function dashboard(records,stored='{}'){const context={window:{TopicStats:T,DomainStats:D,LifeDomain:require('../app/src/main/assets/domain.js')},localStorage:{getItem:()=>stored}};vm.createContext(context);vm.runInContext(fs.readFileSync(require.resolve('../app/src/main/assets/domain-dashboard.js'),'utf8'),context);context.window.DomainDashboard.init({state:{records},esc:s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),minutes:n=>n+'分钟'});return context.window.DomainDashboard;}
test('render escapes raw titles and classification labels, empty and corrupt storage stay usable',()=>{const rows=[row('a',['<img src=x onerror=alert(1)>'])],ui=dashboard(rows,JSON.stringify({learning:{'<img src=x onerror=alert(1)>':'<script>alert(1)</script>'}})),html=ui.page(T.summarize(rows,{now,range:'all'}),'minutes');assert.doesNotMatch(html,/<script>|<img src=x/);assert.match(html,/&lt;script&gt;/);assert.match(html,/data-domain=/);assert.match(dashboard([],'broken').page(T.summarize([],{now}),'minutes'),/读取失败/);assert.match(dashboard([]).page(T.summarize([],{now}),'minutes'),/领域分布会从记录中长出来/);});
test('shared navigation includes named SVGs and everyday styles load after all legacy sheets',()=>{const html=fs.readFileSync(require.resolve('../app/src/main/assets/index.html'),'utf8');assert.equal((html.match(/<svg viewBox="0 0 24 24"/g)||[]).length,4);assert.ok(html.indexOf('href="app-ui.css"')>html.indexOf('href="topic-insights.css"'));assert.match(html,/domain-stats.js/);assert.match(html,/domain-dashboard.js/);const java=fs.readFileSync(require.resolve('../app/src/main/java/com/insight/quantlife/MainActivity.java'),'utf8');assert.match(java,/setStatusBarColor\(0xffededed\)/);});

test('ring conserves time and fractional counts across overlapping wiki domains',()=>{
 const r=row('a',[],61);Object.assign(r,{updatedAt:2,wikiDomainVersion:2,wikiClassificationVersion:3,wikiDomains:['AI 与技术','商业与管理']});
 const data=T.summarize([r],{range:'all',now}),before=JSON.stringify(data),p=D.distribution(data);
 assert.equal(p.total,61);assert.deepEqual(p.buckets.map(b=>b.value),[30.5,30.5]);assert.equal(p.buckets.reduce((n,b)=>n+b.percent,0),100);
 assert.deepEqual(D.distribution(data,{},'count').buckets.map(b=>b.value),[.5,.5]);assert.equal(JSON.stringify(data),before);
});
test('ring percentages use deterministic largest remainders and keep exact total',()=>{
 const data=T.summarize([row('a',['RAG'],1),row('b',['历史'],1),row('c',['管理'],1)],{range:'all',now}),p=D.distribution(data);
 assert.deepEqual(p.buckets.map(b=>b.percent).sort(),[33,33,34]);assert.equal(p.total,3);
});
test('ring merges excess domains without losing sources or values',()=>{
 const rows=Array.from({length:9},(_,i)=>row('a'+i,['tag'+i],i+1)),mapping=Object.fromEntries(rows.map((r,i)=>['tag'+i,'领域'+i])),data=T.summarize(rows,{range:'all',now}),p=D.distribution(data,mapping);
 assert.equal(p.buckets.length,6);assert.equal(p.total,45);assert.equal(p.buckets.reduce((n,b)=>n+b.value,0),45);
 const tail=p.buckets.at(-1);assert.equal(tail.members.length,4);assert.equal(tail.ids.length,4);assert.equal(p.buckets.reduce((n,b)=>n+b.percent,0),100);
});
test('zero, empty and essay rings never invent time or percentages',()=>{
 assert.deepEqual(D.distribution(T.summarize([],{range:'all',now})),{total:0,buckets:[]});
 const zero=D.distribution(T.summarize([row('z',['RAG'],0)],{range:'all',now}));assert.equal(zero.total,0);assert.equal(zero.buckets[0].percent,0);
 const essay=T.summarize([{id:'e',type:'idea',date:'2026-10-27',durationMinutes:999,tags:['设计','技术']}],{module:'thoughts',range:'all',now});
 assert.equal(D.distribution(essay).total,1);assert.equal(D.distribution(essay).buckets.reduce((n,b)=>n+b.value,0),1);
});
test('work ring counts logs, times only shifts, preserves zero-time log drilldown',()=>{
 const rows=[{id:'w',type:'work',date:'2026-10-27',workStart:'2026-10-27T09:00:00',workEnd:'2026-10-27T10:00:00',breaks:[],tags:['技术','管理']},{id:'l',type:'work_note',date:'2026-10-27',tags:['沟通'],durationMinutes:900}];
 const data=T.summarize(rows,{module:'work',range:'all',now}),p=D.distribution(data),c=D.distribution(data,{},'count');
 assert.equal(p.total,60);assert.equal(p.buckets.find(x=>x.label==='沟通与协作').value,0);assert.equal(c.total,1);
});
test('dashboard defaults to accessible local ring with explicit allocation and alternative',()=>{
 const rows=[row('x',['RAG','历史'],10)],html=dashboard(rows).page(T.summarize(rows,{range:'all',now}),'minutes');
 assert.match(html,/domain-ring-figure/);assert.match(html,/data-domain-chart="bars"/);assert.match(html,/均分/);assert.match(html,/aria-hidden="true"/);assert.equal((html.match(/data-domain="/g)||[]).length,2);assert.doesNotMatch(html,/NaN|Infinity/);
});
