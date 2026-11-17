const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const T=require('../app/src/main/assets/topic-stats.js'),S=require('../app/src/main/assets/evidence-stats.js');
const now=new Date('2026-10-29T12:00:00'),row=(id,date,minutes,tags=['RAG'])=>({id,type:'learning',date,durationMinutes:minutes,tags,title:'标题 '+id});
const summarize=(records,module='learning',options={})=>S.summarize(T.summarize(records,{range:'all',module,now,...options}),{now});
test('positive learning durations are raw observations, missing values do not lower median',()=>{
 const records=[row('a','2026-10-01',20),row('b','2026-10-01',30),row('c','2026-10-02',0),row('d','2026-03-30',undefined)],before=JSON.stringify(records),s=summarize(records);
 assert.equal(s.durations.count,2);assert.equal(s.durations.missing,2);assert.equal(s.durations.median,25);
 assert.equal(s.daily.reduce((n,d)=>n+d.minutes,0),50);assert.equal(s.daily.length,3);assert.equal(s.daily.find(d=>d.date==='2026-10-01').count,2);assert.deepEqual(s.months,['2026-03','2026-10']);assert.equal(JSON.stringify(records),before);
});
test('cross-domain points retain full duration without multiplying unique median sample or daily totals',()=>{
 const r={...row('x','2026-10-01',50),updatedAt:1,wikiDomainVersion:1,wikiClassificationVersion:4,wikiDomains:['AI 与技术','商业与管理']},s=summarize([r]);
 assert.equal(s.durations.count,1);assert.equal(s.durations.rows.length,2);assert.equal(s.durations.rows[0].points[0].minutes,50);assert.equal(s.daily[0].minutes,50);assert.equal(s.durations.median,50);
});
test('work calendar clips midnight and rests to each day; journals do not acquire hours',()=>{
 const work={id:'w',type:'work',workStart:'2026-10-28T23:00:00',workEnd:'2026-10-29T02:00:00',breaks:[{start:'2026-10-28T23:30:00',end:'2026-10-29T00:30:00'}]},log={id:'j',type:'work_note',date:'2026-10-29',durationMinutes:999},s=summarize([work,log],'work');
 assert.deepEqual(s.daily.map(d=>[d.date,d.minutes,d.count,d.shifts]),[['2026-10-28',30,0,1],['2026-10-29',90,1,1]]);assert.equal(s.durations.count,0);
 const clipped=summarize([work,log],'work',{range:'custom',start:'2026-10-29',end:'2026-10-29'});assert.equal(clipped.daily.length,1);assert.equal(clipped.daily[0].minutes,90);
});
test('ongoing overnight shift marks current day partial and respects open break',()=>{
 const w={id:'w',type:'work',workStart:'2026-10-28T23:00:00',breaks:[{start:'2026-10-29T01:00:00',end:''}]},s=summarize([w],'work');
 assert.deepEqual(s.daily.map(d=>[d.minutes,d.ongoing]),[[60,false],[60,true]]);assert.equal(s.daily.at(-1).ids[0],'w');
});
test('multiple shifts per date remain one day, with independent log counts and source ids',()=>{
 const work=(id,start,end)=>({id,type:'work',workStart:'2026-10-29T'+start,workEnd:'2026-10-29T'+end}),s=summarize([work('a','09:00:00','10:00:00'),work('b','10:30:00','11:00:00'),{id:'j',type:'work_note',date:'2026-10-29'}],'work');
 assert.equal(s.daily.length,1);assert.equal(s.daily[0].shifts,2);assert.equal(s.daily[0].minutes,90);assert.equal(s.daily[0].count,1);assert.equal(s.daily[0].ids.length,3);
});
test('both essay types aggregate as records and never invent time',()=>{
 const s=summarize([{id:'e',type:'experience',date:'2026-10-29',durationMinutes:90},{id:'i',type:'idea',date:'2026-10-29'}],'thoughts');assert.equal(s.daily[0].count,2);assert.equal(s.daily[0].minutes,0);
});
test('calendar bounds render at most six weeks, real leap dates and empty days stay distinct',()=>{
 const stats=summarize([row('a','2024-02-29',0)]),cells=S.monthCells('2024-02',stats.daily,{start:'2024-02-20',end:'2024-02-29'});
 assert.equal(cells.length,35);assert.equal(cells.find(d=>d?.number===29).value.count,1);assert.equal(cells.find(d=>d?.number===28).value,null);assert.equal(cells.find(d=>d?.number===1).inRange,false);
 assert.equal(S.monthCells('2026-03',[]).length,42);assert.equal(S.monthCells('2026-13',[]).length,0);assert.equal(S.validDate('2026-02-29'),false);assert.equal(S.validDate('2024-02-29'),true);
});
test('empty history and invalid dates produce no observations',()=>{
 const s=summarize([row('bad','2026-02-31',20)]);assert.equal(s.daily.length,0);assert.equal(s.durations.median,null);assert.deepEqual(summarize([]).months,[]);assert.equal(S.scale(51),100);assert.equal(S.scale(25),50);
});
test('calendar UI removes redundant duration dots and folds methodology',()=>{
 const context={window:{EvidenceStats:S,InformationStats:require('../app/src/main/assets/information-stats.js'),CalendarMetrics:require('../app/src/main/assets/calendar-metrics.js'),LifeDomain:require('../app/src/main/assets/domain.js'),DomainDashboard:{mapping:()=>({})}}};vm.createContext(context);vm.runInContext(fs.readFileSync(require.resolve('../app/src/main/assets/evidence-dashboard.js'),'utf8'),context);
 const ui=context.window.EvidenceDashboard;ui.init({esc:v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),minutes:n=>n+'分钟'});
 const s=ui.page(T.summarize([row('x','2026-10-29',25,['<img src=x onerror=evil()>']),row('y','2026-10-29',0)],{range:'all',now}));
 assert.doesNotMatch(s,/evidence-dot-row|每条学习时长|中位 25分钟/);assert.doesNotMatch(s,/<img|NaN|Infinity/);assert.match(s,/<details class="evidence-method">/);assert.doesNotMatch(s,/15 条有时长|每点代表一条记录/);
});
