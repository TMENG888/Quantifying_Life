const test=require('node:test');
const assert=require('node:assert/strict');
const D=require('../app/src/main/assets/goal-domain.js');
const clock=new Date('2026-10-30T10:00:00').getTime();
const base=(id='task-001',extra={})=>({id,kind:'task',title:'完成仰卧起坐',area:'life',targetAmount:28,targetUnit:'个',plannedMinutes:30,deadline:'2026-10-30T19:30',scheduledStart:'2026-10-30T19:00',scheduledEnd:'2026-10-30T19:30',parentId:'',...extra});
const save=(data,item,now=clock)=>D.execute(data,{command:'save',item,expectedVersion:data.goalTasks?.find(t=>t.id===item.id)?.updatedAt??null},now).saved;
const mutate=(data,command,id='task-001',extra={},now=clock)=>D.execute(data,{command,id,expectedVersion:data.goalTasks.find(t=>t.id===id).updatedAt,...extra},now);
test('valid calendar dates include seconds, reject normalized dates and impossible times',()=>{
 for(const value of ['2026-10-30T19:30','2028-02-29T09:30:00'])assert.equal(D.dateTime(value),true);
 for(const value of ['2026-02-29T09:30','2026-10-30T25:30','2026-10-30T09:30:61','1899-10-30T09:30'])assert.equal(D.dateTime(value),false);
 assert.doesNotThrow(()=>D.validate(base('task-001',{scheduledEnd:'2026-10-30T19:30:00'}),[]));
});
test('today includes scheduled, overdue and unscheduled active actions without future actions or goals',()=>{
 const now=new Date('2026-10-30T10:00').getTime();
 assert.equal(D.isToday(base(),now),true);
 assert.equal(D.isToday(base('task-001',{scheduledStart:'2026-10-29T09:00',scheduledEnd:'2026-10-29T10:00',status:'active'}),now),true);
 assert.equal(D.isToday(base('task-001',{scheduledStart:'2026-10-31T09:00',scheduledEnd:'2026-10-31T10:00'}),now),false);
 assert.equal(D.isToday(base('task-001',{scheduledStart:'',scheduledEnd:'',deadline:'',status:'active'}),now),true);
 assert.equal(D.isToday(base('task-001',{kind:'goal'}),now),false);
});
test('deadline and parent constraints validate both child deadline and scheduled end',()=>{
 const goal=base('goal-001',{kind:'goal',deadline:'2026-10-30T12:00',scheduledStart:'',scheduledEnd:''});
 assert.throws(()=>D.validate(base('task-001',{parentId:goal.id,deadline:''}),[goal]),/长期目标/);
 assert.throws(()=>D.validate(base('task-001',{scheduledEnd:''}),[]),/同时填写/);
 assert.throws(()=>D.validate(base('task-001',{parentId:'missing'}),[]),/不存在/);
});
test('goal pacing uses its outcome quantity, child actions count completion without mixing units',()=>{
 const goal=base('goal-001',{kind:'goal',targetAmount:10,actualAmount:2,deadline:'2026-11-01T10:00'});
 assert.deepEqual(D.pacing(goal,clock),{remaining:8,days:2,daily:4});
 assert.deepEqual(D.children([{id:'a',parentId:goal.id,status:'completed',targetAmount:100},{id:'b',parentId:goal.id,status:'active',targetAmount:3}],goal.id).completed,1);
});
test('save and progress protect versions and preserve quantity / time independently',()=>{
 const data={};const first=save(data,base());
 assert.equal(first.actualAmount,0);assert.equal(first.actualMinutes,0);
 assert.throws(()=>D.execute(data,{command:'save',item:base(),expectedVersion:null},clock),/已变化/);
 const update=mutate(data,'progress',first.id,{entryId:'entry-001',amount:10,minutes:5,note:'第一组'});
 assert.equal(update.saved.actualAmount,10);assert.equal(update.saved.actualMinutes,5);
 const retry=D.execute(data,{command:'progress',id:first.id,expectedVersion:first.updatedAt,entryId:'entry-001',amount:10,minutes:5,note:'第一组'},clock);
 assert.equal(retry.saved.actualAmount,10);assert.equal(retry.saved.entries.length,1);
 assert.throws(()=>mutate(data,'progress',first.id,{entryId:'entry-001',amount:12,minutes:5,note:'第一组'}),/编号已用于/);
 assert.throws(()=>save(data,base(first.id,{targetUnit:'组'})),/计量单位/);
 assert.throws(()=>mutate(data,'complete'),/尚未达到/);
 mutate(data,'progress',first.id,{entryId:'entry-002',amount:18,minutes:0});
 assert.equal(mutate(data,'complete').saved.status,'completed');
 assert.equal(save(data,base(first.id,{targetAmount:30})).status,'active');
});
test('removed timer rejects starts and legacy running time settles once on load',()=>{
 const data={};save(data,base());save(data,base('task-002'));
 assert.throws(()=>mutate(data,'start'),/已移除/);
 data.goalTasks[0].timerStartedAt=clock;
 let result=D.execute(data,{command:'list'},clock+120000).items.find(i=>i.id==='task-001');
 assert.equal(result.actualMinutes,2);assert.equal(result.timerStartedAt,0);
 result=D.execute(data,{command:'list'},clock+240000).items.find(i=>i.id==='task-001');assert.equal(result.actualMinutes,2);
 assert.equal(result.plannedMinutes,30);assert.equal(result.deadline,'2026-10-30T19:30');
});

test('atomic batch can add a goal plus child and rolls back every save on failure',()=>{
 const data={};const goal=base('goal-001',{kind:'goal',deadline:'2026-11-01T19:30'});
 const commands=[goal,base('task-001',{parentId:goal.id})].map(item=>({command:'save',item,expectedVersion:null}));
 const result=D.execute(data,{command:'batch',commands},clock);assert.equal(result.items.length,2);assert.equal(result.saved.id,'task-001');
 const before=JSON.stringify(data);
 assert.throws(()=>D.execute(data,{command:'batch',commands:[{command:'save',item:base('task-002'),expectedVersion:null},{command:'save',item:base('task-003',{parentId:'missing'}),expectedVersion:null}]},clock),/不存在/);
 assert.equal(JSON.stringify(data),before);
});
test('backup freezes running time; restore merges and leaves original unchanged on conflicts',()=>{
 const data={};save(data,base());data.goalTasks[0].timerStartedAt=clock;
 const backup=D.backup(data.goalTasks,clock+180000);assert.equal(backup[0].actualMinutes,3);assert.equal(backup[0].timerStartedAt,0);
 const target={};D.restore(target,backup);assert.equal(target.goalTasks.length,1);
 assert.equal(D.restore(target,backup),0);assert.equal(target.goalTasks.length,1);
 const invalid=structuredClone(backup);invalid[0].id='task-002';invalid[0].parentId='missing';
 assert.throws(()=>D.restore(target,invalid),/不存在/);assert.equal(target.goalTasks.length,1);
});

test('deleting one progress reverses only its quantity and manual minutes, safely reopens completed work',()=>{
 const data={};save(data,base('task-001',{targetAmount:2}));
 const first=mutate(data,'progress','task-001',{entryId:'entry-one',amount:1,minutes:5,note:'正确记录'}).saved;
 mutate(data,'progress','task-001',{entryId:'entry-two',amount:1,minutes:7,note:'误填记录'});
 mutate(data,'complete');
 const oldVersion=data.goalTasks[0].updatedAt;
 const removed=mutate(data,'deleteProgress','task-001',{entryId:'entry-two'}).saved;
 assert.equal(removed.actualAmount,1);assert.equal(removed.actualMinutes,5);assert.equal(removed.status,'active');assert.equal(removed.completedAt,undefined);
 assert.deepEqual(removed.entries.map(x=>x.id),['entry-one']);
 assert.equal(D.execute(data,{command:'deleteProgress',id:'task-001',entryId:'entry-two',expectedVersion:oldVersion},clock).saved.updatedAt,removed.updatedAt);
 assert.throws(()=>D.execute(data,{command:'progress',id:'task-001',entryId:'entry-two',expectedVersion:oldVersion,amount:1,minutes:7,note:'误填记录'},clock),/已删除/);
 assert.equal(D.restore({},D.backup(data.goalTasks)),1);
 const before=JSON.stringify(data);
 assert.throws(()=>D.execute(data,{command:'deleteProgress',id:'task-001',entryId:first.entries[0].id,expectedVersion:first.updatedAt},clock),/已变化/);
 assert.throws(()=>mutate(data,'deleteProgress','task-001',{entryId:'missing-entry'}),/不存在/);
 assert.equal(JSON.stringify(data),before);
});

test('deleting a zero-quantity time entry preserves a live stopwatch and unrelated work',()=>{
 const data={};save(data,base());save(data,base('task-002'));
 mutate(data,'progress','task-001',{entryId:'entry-time',amount:0,minutes:3});
 data.goalTasks[0].timerStartedAt=clock;
 const result=mutate(data,'deleteProgress','task-001',{entryId:'entry-time'},clock+120000);
 assert.equal(result.saved.actualAmount,0);assert.equal(result.saved.actualMinutes,2);assert.equal(result.saved.elapsedMinutes,2);assert.equal(result.saved.timerStartedAt,0);
 assert.equal(result.items.find(t=>t.id==='task-002').actualAmount,0);
});

test('completed tasks remain completed when remaining progress still meets target',()=>{
 const data={};save(data,base('task-001',{targetAmount:2}));
 mutate(data,'progress','task-001',{entryId:'entry-one',amount:1,minutes:0});
 mutate(data,'progress','task-001',{entryId:'entry-three',amount:3,minutes:0});mutate(data,'complete');
 assert.equal(mutate(data,'deleteProgress','task-001',{entryId:'entry-one'}).saved.status,'completed');
});
test('editing 3 to 1 replaces an entry, changing total 5 to 3 without duplicating it',()=>{
 const data={};save(data,base('task-001',{targetAmount:4}));
 mutate(data,'progress','task-001',{entryId:'entry-one',amount:1,minutes:5});mutate(data,'progress','task-001',{entryId:'entry-two',amount:1,minutes:0});mutate(data,'progress','task-001',{entryId:'entry-three',amount:3,minutes:7,note:'误填'});mutate(data,'complete');
 const at=data.goalTasks[0].entries[2].at,request={command:'updateProgress',id:'task-001',entryId:'entry-three',editId:'edit-first',expectedVersion:data.goalTasks[0].updatedAt,amount:1,minutes:4,note:'已纠正'};
 const edited=D.execute(data,request,clock+1000).saved;
 assert.equal(edited.actualAmount,3);assert.equal(edited.actualMinutes,9);assert.equal(edited.entries.length,3);assert.equal(edited.entries[2].at,at);assert.equal(edited.entries[2].note,'已纠正');assert.equal(edited.status,'active');assert.equal(edited.completedAt,undefined);
 assert.equal(D.execute(data,request,clock+2000).saved.updatedAt,edited.updatedAt);
 assert.throws(()=>D.execute(data,{...request,amount:2},clock+3000),/编辑编号/);
 const before=JSON.stringify(data);assert.throws(()=>D.execute(data,{...request,editId:'edit-stale'},clock+3000),/已变化/);assert.equal(JSON.stringify(data),before);
 data.goalTasks[0].timerStartedAt=clock;const live=mutate(data,'updateProgress','task-001',{entryId:'entry-three',editId:'edit-live',amount:1,minutes:3,note:'修订耗时'},clock+120000).saved;
 assert.equal(live.timerStartedAt,0);assert.equal(live.actualMinutes,10);assert.equal(live.elapsedMinutes,10);
 mutate(data,'deleteProgress','task-001',{entryId:'entry-three'},clock+120000);assert.equal(data.goalTasks[0].actualAmount,2);assert.equal(data.goalTasks[0].actualMinutes,7);
 assert.throws(()=>mutate(data,'updateProgress','task-001',{entryId:'entry-three',editId:'edit-deleted',amount:1,minutes:0}),/不存在/);
});
test('existing child progress contributes to the parent without rewriting or duplicating its ledger',()=>{
 const data={};save(data,base('goal-course',{kind:'goal',targetAmount:28,targetUnit:'节'}));save(data,base('task-001',{parentId:'goal-course',targetAmount:4,targetUnit:'节'}));
 mutate(data,'progress','task-001',{entryId:'course-first',amount:4,minutes:239.8});mutate(data,'complete');
 let parent=D.execute(data,{command:'list'},clock).items.find(t=>t.id==='goal-course');
 assert.equal(parent.actualAmount,4);assert.equal(parent.elapsedMinutes,239.8);assert.equal(parent.childAmount,4);assert.equal(parent.ownAmount,0);assert.equal(parent.entries.length,0);assert.equal(data.goalTasks.find(t=>t.id==='goal-course').actualAmount,0);
 assert.equal(D.children(data.goalTasks,'goal-course').completed,1);
 const raw=JSON.stringify(data);for(let i=0;i<3;i++)assert.equal(D.execute(data,{command:'list'},clock).items.find(t=>t.id==='goal-course').actualAmount,4);assert.equal(JSON.stringify(data),raw);
 mutate(data,'updateProgress','task-001',{entryId:'course-first',editId:'course-edit',amount:3,minutes:180,note:''});parent=D.execute(data,{command:'list'},clock).items.find(t=>t.id==='goal-course');assert.equal(parent.actualAmount,3);assert.equal(parent.elapsedMinutes,180);
 mutate(data,'deleteProgress','task-001',{entryId:'course-first'});parent=D.execute(data,{command:'list'},clock).items.find(t=>t.id==='goal-course');assert.equal(parent.actualAmount,0);assert.equal(parent.elapsedMinutes,0);
});
test('goal rollup handles mixed units, reparenting, deletion, own progress and live time',()=>{
 const data={};for(const id of ['goal-one','goal-two'])save(data,base(id,{kind:'goal',targetAmount:4,targetUnit:'节'}));
 save(data,base('task-001',{parentId:'goal-one',targetAmount:4,targetUnit:'节'}));save(data,base('task-mixed',{parentId:'goal-one',targetAmount:1,targetUnit:'篇'}));
 mutate(data,'progress','goal-one',{entryId:'parent-own',amount:1,minutes:2});mutate(data,'progress','task-001',{entryId:'child-own',amount:3,minutes:5});mutate(data,'progress','task-mixed',{entryId:'mixed-own',amount:1,minutes:7});
 let parent=D.execute(data,{command:'list'},clock).items.find(t=>t.id==='goal-one');assert.equal(parent.actualAmount,4);assert.equal(parent.actualMinutes,14);assert.equal(parent.excludedQuantityChildren,1);
 mutate(data,'complete','goal-one');const oldVersion=data.goalTasks.find(t=>t.id==='goal-one').updatedAt;
 mutate(data,'updateProgress','task-001',{entryId:'child-own',editId:'lower-child',amount:2,minutes:5});parent=D.execute(data,{command:'list'},clock).items.find(t=>t.id==='goal-one');assert.equal(parent.status,'active');assert.equal(parent.actualAmount,3);assert(parent.updatedAt>oldVersion);
 assert.throws(()=>D.execute(data,{command:'complete',id:'goal-one',expectedVersion:oldVersion},clock),/已变化/);
 data.goalTasks.find(t=>t.id==='task-001').timerStartedAt=clock;parent=D.execute(data,{command:'list'},clock+120000).items.find(t=>t.id==='goal-one');assert.equal(parent.elapsedMinutes,16);assert.equal(parent.childTimerRunning,false);
 mutate(data,'pause','task-001',{},clock+120000);
 save(data,base('task-001',{parentId:'goal-two',targetAmount:4,targetUnit:'节'}));const result=D.execute(data,{command:'list'},clock+120000).items;assert.equal(result.find(t=>t.id==='goal-one').actualAmount,1);assert.equal(result.find(t=>t.id==='goal-two').actualAmount,2);
 const backed=D.backup(result,clock+120000),restored={};assert.equal(backed.find(t=>t.id==='goal-two').actualAmount,0);D.restore(restored,backed);assert.equal(D.execute(restored,{command:'list'},clock+120000).items.find(t=>t.id==='goal-two').actualAmount,2);
 mutate(data,'delete','task-001',{},clock+120000);assert.equal(D.execute(data,{command:'list'},clock+120000).items.find(t=>t.id==='goal-two').actualAmount,0);
});
