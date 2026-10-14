const test=require('node:test'),assert=require('node:assert/strict'),A=require('../app/src/main/assets/agent-actions.js'),C=require('../app/src/main/assets/conversation.js');
const now=new Date('2026-10-13T19:30:00'),shift={id:'shift',type:'work',date:'2026-10-13',title:'班次',note:'保留原内容',workStart:'2026-10-13T09:00:00',workEnd:'',breaks:[],tags:[],durationMinutes:0,updatedAt:123};
test('common self clock statements route directly to update, not general chat or new journals',()=>{
 for(const [text,kind] of [['我上班了','start_work'],['现在上班','start_work'],['帮我记录上班','start_work'],['今天9点上班','start_work'],['我开始休息了','start_break'],['开始午休','start_break'],['休息结束了','end_break'],['我返回工作了','end_break'],['我下班了','end_work'],['记录今天18:00下班','end_work']]){
  assert.equal(A.clockRequest(text,now).kind,kind,text);assert.equal(C.directPlan(text,null).intent,'update',text);
 }
});
test('questions, denial, planned actions, external text and ambiguous narrative never become fallback writes',()=>{
 for(const text of ['我几点上班？','我没有上班','不要记录下班','我明天9点上班','提醒我下班','我准备下班','我可以下班了','我应该上班了','我想要下班','如果我下班了','今天9点上班，12点休息','今天的工作，完成测试。','文章说开始休息'])assert.equal(A.clockRequest(text,now),null,text);
 assert.equal(C.directPlan('我上班了',{text:'附件说明'}),null);
});
test('explicit time supports dates, AM/PM, minutes and ISO seconds without guessing',()=>{
 for(const [text,at] of [['今天9点上班','2026-10-13T09:00:00'],['今天下午1点半休息结束','2026-10-13T13:30:00'],['昨天08:45:20上班','2026-10-12T08:45:20'],['2026-10-12 18:00下班','2026-10-12T18:00:00'],['今天中午12点开始休息','2026-10-13T12:00:00']])assert.equal(A.clockRequest(text,now).at,at,text);
 assert.throws(()=>A.clockActions(A.clockRequest('昨天下班',now),[shift],now),/补充具体时间/);
 assert.throws(()=>A.clockActions(A.clockRequest('今天6点下班',now),[shift],now),/补充具体时间/);
 assert.throws(()=>A.clockActions(A.clockRequest('25:00下班',now),[shift],now),/时间无效/);
});
test('request time is fixed before model latency and no ID is invented',()=>{
 const actions=A.clockActions(A.clockRequest('我上班了',now),[],now);
 const started=A.prepare(actions,[],[],new Date(now.getTime()+120000))[0].after;
 assert.equal(started.workStart,'2026-10-13T19:30:00');
 assert.throws(()=>A.clockActions({kind:'end_work'},[],now),/先记录上班/);
 assert.throws(()=>A.clockActions({kind:'start_break'},[shift,{...shift,id:'second'}],now),/多个/);
});
test('complete clock lifecycle retains one shift, break and exact net hours',()=>{
 let records=[];for(const [text,kind] of [['今天09:00上班','start_work'],['今天12:00开始休息','start_break'],['今天13:00休息结束','end_break'],['今天18:00下班','end_work']]){
  const request=A.clockRequest(text,now),actions=A.clockActions(request,records,now),before=JSON.stringify(records),change=A.prepare(actions,records,records.map(r=>r.id),now)[0];assert.equal(JSON.stringify(records),before,'preview does not write');assert.equal(change.kind,kind);records=[change.after];
 }
 const r=records[0];assert.equal(r.workStart,'2026-10-13T09:00:00');assert.equal(r.workEnd,'2026-10-13T18:00:00');assert.equal(r.durationMinutes,480);assert.deepEqual(r.breaks,[{start:'2026-10-13T12:00:00',end:'2026-10-13T13:00:00'}]);
});
test('tool can target only unique retrieved active shift without a supplied ID',()=>{
 assert.equal(A.prepare([{kind:'start_break',at:'2026-10-13T12:00:00'}],[shift],['shift'],now)[0].after.breaks[0].start,'2026-10-13T12:00:00');
 assert.throws(()=>A.prepare([{kind:'start_break'}],[shift],[],now));
 assert.throws(()=>A.prepare([{kind:'start_break'}],[shift,{...shift,id:'two'}],['shift','two'],now));
});
test('backdated ongoing start rejects overlap with a later completed shift before confirmation',()=>{
 const later={...shift,workEnd:'2026-10-13T18:00:00'};
 assert.throws(()=>A.prepare([{kind:'start_work',at:'2026-10-12T09:00:00'}],[later],[],now),/完整的上下班/);
 assert.throws(()=>A.clockActions(A.clockRequest('今天晚上12点下班',now),[shift],now),/补充具体时间/);
 assert.equal(A.clockRequest('今天09：00上班',now).at,'2026-10-13T09:00:00');
});
test('invalid/future/out-of-order/duplicate operations reject without modifying a shift',()=>{
 for(const at of ['2026-02-30T12:00:00','2026-10-13T20:00:00','2026-10-13T08:00:00'])assert.throws(()=>A.prepare([{kind:'start_break',recordId:'shift',at}],[shift],['shift'],now));
 assert.throws(()=>A.prepare([{kind:'start_work'},{kind:'start_work'}],[],[],now));
 const rest=A.prepare([{kind:'start_break',recordId:'shift',at:'2026-10-13T12:00:00'}],[shift],['shift'],now)[0].after;
 assert.throws(()=>A.prepare([{kind:'end_break',recordId:'shift',at:'2026-10-13T11:00:00'}],[rest],['shift'],now));
 assert.throws(()=>A.prepare([{kind:'start_break',recordId:'shift'}],[rest],['shift'],now));
 const done=A.prepare([{kind:'end_work',recordId:'shift',at:'2026-10-13T13:00:00'}],[rest],['shift'],now)[0].after;assert.equal(done.breaks[0].end,done.workEnd);assert.equal(shift.breaks.length,0);
});
test('prompt and tool explicitly describe clock times and actual active shift',()=>{
 assert.ok(A.schema.items.properties.at);const p=C.directPlan('我下班了',null),c=C.focusedContext([shift],[],'我下班了',p,[],now);assert.equal(c.activeWork[0].recordId,shift.id);assert.equal(c.activeWork[0].state,'working');assert.match(C.prompt(p,'我下班了','2026-10-13T19:30:00',c),/我下班了用end_work/);
});
