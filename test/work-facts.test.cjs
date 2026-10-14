const test=require('node:test'),assert=require('node:assert/strict'),D=require('../app/src/main/assets/domain.js'),F=require('../app/src/main/assets/work-facts.js'),C=require('../app/src/main/assets/conversation.js'),W=require('../app/src/main/assets/wiki.js'),A=require('../app/src/main/assets/agent-actions.js');
const now=new Date('2026-10-13T22:35:00'),row={id:'screen-shift',type:'work',date:'2026-10-13',title:'工作班次',workStart:'2026-10-13T09:38:00',workEnd:'2026-10-13T19:13:00',breaks:[{start:'2026-10-13T12:35:00',end:'2026-10-13T14:05:00'}],durationMinutes:485,tags:[],note:'',updatedAt:101};
const plan={intent:'history',topic:'',keywords:[],dateStart:'2026-10-13',dateEnd:'2026-10-13',recordTypes:['work']};
test('screenshot regression: gross575 - rest90 = net485, never395',()=>{
 const c=C.focusedContext([row],[],'今天工作多久',plan,[],now);assert.equal(c.summary.workMinutes,485);assert.equal(c.summary.grossWorkMinutes,575);assert.equal(c.summary.netWorkMinutes,485);assert.equal(c.summary.restMinutes,90);assert.equal(c.summary.workMinutesBasis,F.basis);
 assert.deepEqual(c.workTiming.totals,{grossMinutes:575,restMinutes:90,netMinutes:485,netRestAlreadyDeducted:true,shiftCount:1,ongoingShiftCount:0});assert.equal(c.records[0].workTiming.wholeShift.netMinutes,485);
 for(const question of ['我今天下班了嘛','我今天下班了吗','今天工作了多久','今天上班时长','今天工时合计多久','今天几点下班？','今天一共工作多少小时','可以查一下今天的工作时长是多少吗']){const q=F.query(question,now);assert.ok(q,question);const r=F.reply(q,[row],now);assert.match(r.answer,/8 小时 5 分/);assert.doesNotMatch(r.answer,/6 小时 35 分/);assert.deepEqual(r.citations,[row.id]);}
});
test('questions do not fall into update tools or vague general planner',()=>{
 const p=C.directPlan('我今天下班了嘛',null);assert.equal(p.intent,'history');assert.equal(p.purpose,'work_facts');
 for(const text of ['我上班了','今天09:00上班','开始休息','我现在已经下班了 然后你可以帮我打一下','把今天下班时间改为20:00','项目A工作了多久','分析今天学习与工作效率','工作日志写了什么？','我明天几点上班？'])assert.equal(F.query(text,now),null,text);
 assert.equal(F.query('记录编号:screen-shift 工作多久',now).period,null);
});
test('wrong numeric model paragraph is replaced, not left next to an accurate disclaimer',()=>{
 const facts=F.context([row],[row],D.questionPeriod('今天',now),now),bad='你今天09:38上班，19:13下班，午休90分钟，净工作约6小时35分。\n\n笔记记录了测试进展。',result=F.guard(bad,facts);
 assert.doesNotMatch(result.answer,/6小时35分/);assert.match(result.answer,/8 小时 5 分/);assert.match(result.answer,/9 小时 35 分/);assert.match(result.answer,/1 小时 30 分/);assert.match(result.answer,/测试进展/);assert.deepEqual(result.sourceIds,[row.id]);
 assert.doesNotMatch(F.guard('你净工作六小时三十五分。',facts).answer,/六小时三十五分/);
 assert.doesNotMatch(F.guard('今天工作6小时35分',facts).answer,/6小时35分/);
 assert.doesNotMatch(F.guard('净工作 **6** 小时 **35** 分。',facts).answer,/\*\*6\*\*/);
 assert.equal(F.guard('建议每工作50分钟休息10分钟。',facts).answer,'建议每工作50分钟休息10分钟。');
});
test('scope distinguishes a whole overnight shift from queried natural-day totals',()=>{
 const r={...row,date:'2026-10-12',workStart:'2026-10-12T22:00:00',workEnd:'2026-10-13T06:00:00',breaks:[{start:'2026-10-12T23:30:00',end:'2026-10-13T00:30:00'}]},q=F.query('今天工作多久',now),f=F.context([r],[r],q.period,now);
 assert.equal(f.totals.netMinutes,330);assert.equal(f.shifts[0].wholeShift.netMinutes,420);const a=F.reply(q,[r],now).answer;assert.match(a,/5 小时 30 分/);assert.match(a,/整班净工作 7 小时/);assert.match(a,/查询范围内净工作 5 小时 30 分/);
});
test('in-progress timer ignores stale stored duration and explicitly states as-of time',()=>{
 const r={...row,workEnd:'',durationMinutes:0},f=F.shift(r,null,now);assert.equal(f.wholeShift.netMinutes,687);assert.equal(f.state,'working');const a=F.reply(F.query('今天工时多久',now),[r],now).answer;assert.match(a,/11 小时 27 分/);assert.match(a,/截至 2026-10-13 22:35/);assert.match(a,/尚未记录下班/);
 const resting={...r,breaks:[{start:'2026-10-13T12:35:00',end:''}]};assert.equal(F.shift(resting,null,now).wholeShift.netMinutes,177);assert.match(F.reply(F.query('我今天下班了吗',now),[resting],now).answer,/正在休息/);
});
test('date range sums all shifts, not only the retrieval or displayed subset',()=>{
 const rows=Array.from({length:30},(_,i)=>({...row,id:'s'+i,date:'2026-09-'+String(i+1).padStart(2,'0'),workStart:'2026-09-'+String(i+1).padStart(2,'0')+'T09:00:00',workEnd:'2026-09-'+String(i+1).padStart(2,'0')+'T10:00:00',breaks:[]}));
 const q=F.query('上个月工作多久',now),f=F.context(rows,rows.slice(0,2),q.period,now);assert.equal(f.totals.netMinutes,1800);assert.equal(f.shifts.length,2);const answer=F.reply(q,rows,now);assert.match(answer.answer,/30 小时/);assert.match(answer.answer,/汇总覆盖 30 个班次/);assert.equal(answer.citations.length,8);
 const old={...rows[0],id:'old'};assert.match(F.reply(F.query('记录编号:old 工作多久',now),[old,row],now).answer,/1 小时/);
});
test('missing records are unknown activity, not confidently zero hours',()=>{
 assert.match(F.reply(F.query('今天工作多久',now),[],now).answer,/未记录不代表没有工作/);
 assert.doesNotMatch(F.reply(F.query('今天工作多久',now),[],now).answer,/净工作.*0/);
 assert.match(F.guard('你今天工时为0分钟。',F.context([],[],D.questionPeriod('今天',now),now)).answer,/不能据此判断实际工时为零/);
});
test('already ended clock gives status and exact facts without creating updates',()=>{
 const before=JSON.stringify(row);let err;try{A.clockActions(A.clockRequest('现在下班',now),[row],now);}catch(e){err=e;}assert.ok(err.workStatus);assert.match(err.message,/19:13 下班/);assert.match(err.message,/8 小时 5 分/);assert.match(err.message,/不需要重复打卡/);assert.deepEqual(err.citations,[row.id]);assert.equal(JSON.stringify(row),before);
 assert.throws(()=>A.prepare([{kind:'end_work',recordId:row.id}],[row],[row.id],now),e=>e.workStatus===true&&e.message.includes('8 小时 5 分'));
 assert.equal(A.clockRequest('我现在已经下班了 然后你可以帮我打一下',now).kind,'end_work');
 assert.throws(()=>A.clockActions({kind:'end_work'},[{...row,date:'2026-10-12',workEnd:'2026-10-12T19:13:00'}],now),/先记录上班/);
});
test('explicit correction still prepares a preview, preserves old data until confirmed',()=>{
 const a=A.prepare([{kind:'update_record',recordId:row.id,patch:{workEnd:'2026-10-13T20:00:00'}}],[row],[row.id],now)[0];assert.equal(a.before.workEnd,row.workEnd);assert.equal(a.after.durationMinutes,532);assert.equal(a.after.workEnd,'2026-10-13T20:00:00');assert.equal(row.workEnd,'2026-10-13T19:13:00');
});
test('topic/update retrieval carries computed timings even when base selection changes',()=>{
 const tagged={...row,tags:['测试项目']},topic=W.context([tagged],[],'测试项目工作多久',{...plan,intent:'topic',topic:'测试项目'},now);assert.equal(topic.records[0].workTiming.wholeShift.netMinutes,485);assert.equal(topic.workTiming.totals.netMinutes,485);
 const update=C.focusedContext([{...row,workEnd:'',date:'2026-10-12',workStart:'2026-10-12T09:38:00',breaks:[],durationMinutes:0}],[],'更正昨天工作记录',{...plan,intent:'update',dateStart:'',dateEnd:''},[],now);assert.ok(update.records[0].workTiming);
 assert.match(C.prompt(plan,'今天工作多久',D.localTime(now),{}),/严禁再减restMinutes/);
});
test('topic-constrained work facts cannot silently include unrelated shifts or generic book advice',()=>{
 const own={...row,id:'project-a',tags:['甲项目']},other={...row,id:'project-b',tags:['乙项目']};
 const c=W.context([own,other],[],'甲项目工作多久',{...plan,topic:'甲项目'},now);assert.equal(c.workTiming.totals.shiftCount,1);assert.equal(c.workTiming.totals.netMinutes,485);assert.deepEqual(c.workTiming.shifts.map(s=>s.recordId),['project-a']);
 const generic='书中介绍标准工时为每天8小时。';assert.equal(F.guard(generic,F.context([])).answer,generic);
});
