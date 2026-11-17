const test=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const I=require('../app/src/main/assets/information-stats.js'),M=require('../app/src/main/assets/calendar-metrics.js'),E=require('../app/src/main/assets/evidence-stats.js'),T=require('../app/src/main/assets/topic-stats.js'),D=require('../app/src/main/assets/domain.js');
function row(id,n,note,minutes,date,type='learning'){return {id,type,title:'标题',note,date,updatedAt:10,durationMinutes:minutes,informationUnits:{schemaVersion:2,model:'deepseek-flash',provider:'assistant-deepseek',unitRule:'semantic-propositions-v2',sourceVersion:10,complete:true,characters:I.characters(note),count:n,units:Array.from({length:n},()=>({text:'独立知识',sources:[{start:0,end:note.length,quote:note}]}))}};}
function state(rows,module='learning',extra={}){const data=T.summarize(rows,{module,range:'all',...extra});return {data,stats:E.summarize(data)};}
test('calendar month/week ratios pool matching numerators and denominators',()=>{
 const {data,stats}=state([row('a',2,'短',10,'2026-10-28'),row('b',8,'一二三四五六七八九',50,'2026-10-29')]),week=M.buckets(data,stats.daily,'week')[0],month=M.buckets(data,stats.daily,'month')[0];
 for(const v of [week,month]){a.equal(v.units,10);a.equal(v.density,1000);a.equal(v.efficiency,10);a.equal(v.cost,6);a.equal(v.duration,60);a.equal(v.count,2);}
});
test('calendar distinguishes no records, media only, pending, untimed and genuine zero',()=>{
 const pending={...row('p',1,'内容',30,'2026-10-29'),updatedAt:11},media={id:'m',type:'learning',note:'![图片](media:x)',date:'2026-10-28'},zero=row('z',0,'没有知识点',0,'2026-10-30');
 const {data,stats}=state([pending,media,zero]),at=d=>M.aggregate(data,stats.daily,d,d);
 a.equal(M.reason(at('2026-10-27'),'units'),'无记录');a.equal(M.reason(at('2026-10-28'),'units'),'无正文');a.equal(M.reason(at('2026-10-29'),'units'),'待分析');
 a.equal(M.value(at('2026-10-30'),'units'),0);a.equal(M.value(at('2026-10-30'),'density'),0);a.equal(M.reason(at('2026-10-30'),'efficiency'),'未计时');
});
test('partial results exclude pending records from density/time and preserve source set',()=>{
 const r=row('ok',3,'方法',30,'2026-10-29'),p={...row('pending',50,'大量内容',600,'2026-10-29'),updatedAt:12},s=state([r,p]),v=M.aggregate(s.data,s.stats.daily,'2026-10-29','2026-10-29');
 a.equal(v.pending,1);a.equal(v.units,3);a.equal(v.density,1500);a.equal(v.efficiency,6);a.deepEqual(v.ids,['ok','pending']);
});
test('work chart retains cross-midnight net hours without attributing hours to log information',()=>{
 const w={id:'w',type:'work',workStart:'2026-10-28T23:00:00',workEnd:'2026-10-29T02:00:00',breaks:[{start:'2026-10-29T00:30:00',end:'2026-10-29T01:00:00'}]},log=row('log',4,'工作知识',999,'2026-10-29','work_note'),{data,stats}=state([w,log],'work'),v=M.aggregate(data,stats.daily,'2026-10-29','2026-10-29');
 a.equal(M.value(v,'hours'),1.5);a.equal(v.units,4);a.equal(v.efficiency,null);a.equal(v.count,1);a.deepEqual(new Set(v.ids),new Set(['w','log']));
});
test('boundary weekly/month buckets clip and keep imported record dates and leap dates',()=>{
 const {data,stats}=state([row('a',1,'知识',20,'2024-02-29'),row('b',2,'知识',20,'2024-03-01')],'learning',{range:'custom',start:'2024-02-29',end:'2024-03-01'}),b=M.buckets(data,stats.daily,'week');
 a.equal(b.length,1);a.equal(b[0].start,'2024-02-29');a.equal(b[0].end,'2024-03-01');a.equal(b[0].units,3);a.equal(M.buckets(data,stats.daily,'month').length,2);
});
test('all-history chart has null gaps and no invented zero bars',()=>{
 const {data,stats}=state([row('a',2,'内容',20,'2026-10-28'),row('b',2,'内容',20,'2026-10-30')]),b=M.buckets(data,stats.daily,'day');
 a.equal(b.length,3);a.equal(M.value(b[1],'units'),null);a.equal(M.reason(b[1],'units'),'无记录');
});
test('module metric choices avoid fictitious essay/work productivity',()=>{
 a.ok(M.choices('learning').includes('efficiency'));a.ok(M.choices('work').includes('hours'));a.ok(!M.choices('work').includes('efficiency'));a.deepEqual(M.choices('thoughts'),['units','density','count']);
});
test('integrated calendar replaces separate modules, renders bars and safely escapes original text',()=>{
 const c={window:{EvidenceStats:E,InformationStats:I,CalendarMetrics:M,LifeDomain:D,DomainDashboard:{mapping:()=>({})}}};vm.createContext(c);vm.runInContext(fs.readFileSync(require.resolve('../app/src/main/assets/evidence-dashboard.js'),'utf8'),c);
 const ui=c.window.EvidenceDashboard;ui.init({esc:s=>String(s??'').replace(/[<>&"]/g,c=>({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;'}[c])),minutes:n=>n+'分钟'});
 const {data}=state([row('a',3,'<img src=x onerror=evil()>',30,'2026-10-30')]),before=JSON.stringify(data),html=ui.page(data);
 a.match(html,/id="calendar-metric"/);a.match(html,/data-calendar-bar=/);a.match(html,/data-calendar-grain="month"/);a.match(html,/value="density"/);a.match(html,/value="efficiency"/);a.match(html,/点击或长按/);a.doesNotMatch(html,/<svg|<path|<img|quant-card|information-card|NaN|Infinity/);a.equal(JSON.stringify(data),before);
 const index=fs.readFileSync(require.resolve('../app/src/main/assets/index.html'),'utf8'),topic=fs.readFileSync(require.resolve('../app/src/main/assets/topic-insights.js'),'utf8');
 a.doesNotMatch(index,/quant-dashboard|information-dashboard/);a.doesNotMatch(topic,/QuantDashboard|InformationDashboard/);
});
test('production worker uses the main DeepSeek profile with bounded concurrency and retries',()=>{
 const java=fs.readFileSync(require.resolve('../app/src/main/java/com/insight/quantlife/QwenLabelingService.java'),'utf8');
 a.match(java,/InformationUnits.step/);a.match(java,/InformationTransport.MAX_PARALLEL/);a.match(java,/InformationTransport.config\(ModelRequests.settings/);a.match(java,/InformationTransport.information/);a.doesNotMatch(java,/QwenTransport.information|QwenBudget.MAX_PARALLEL|QwenLabels.step|QwenLabels.needs/);a.match(java,/retryAt/);
});
