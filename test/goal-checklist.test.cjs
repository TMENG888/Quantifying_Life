const test=require('node:test'),assert=require('node:assert/strict');
const D=require('../app/src/main/assets/goal-domain.js');
const now=1794800000000;
const item=(id,kind='task',parentId='')=>({id,kind,parentId,title:'阅读并整理笔记',area:'learning',targetAmount:10,targetUnit:'节',plannedMinutes:30,deadline:'',scheduledStart:'',scheduledEnd:''});
function action(data,command,id,extra={},time=now){return D.execute(data,{command,id,expectedVersion:data.goalTasks?.find(t=>t.id===id)?.updatedAt,...extra},time).saved;}
function create(data,id,kind='task',parentId=''){return D.execute(data,{command:'save',item:item(id,kind,parentId),expectedVersion:null},now).saved;}
test('checkbox completes zero-progress task and goal without inventing quantity; reopen keeps the ledger',()=>{
 for(const kind of ['task','goal']){const data={};create(data,'test-one',kind);const checked=action(data,'check','test-one');assert.equal(checked.status,'completed');assert.equal(checked.manualCompleted,true);assert.equal(checked.actualAmount,0);assert.equal(checked.actualMinutes,0);assert.deepEqual(checked.entries,[]);assert.equal(D.execute(data,{command:'list'}).items[0].status,'completed');const reopened=action(data,'reopen','test-one');assert.equal(reopened.status,'active');assert.equal(reopened.actualAmount,0);assert.equal(reopened.manualCompleted,undefined);assert.equal(reopened.completedAt,undefined);}
});
test('checking settles a running timer once, never starts it on reopening',()=>{
 const data={};create(data,'test-timer');data.goalTasks[0].timerStartedAt=now;const checked=action(data,'check','test-timer',{},now+120000);assert.equal(checked.actualMinutes,2);assert.equal(checked.timerStartedAt,0);assert.equal(action(data,'check','test-timer',{},now+240000).actualMinutes,2);const reopened=action(data,'reopen','test-timer',{},now+240000);assert.equal(reopened.actualMinutes,2);assert.equal(reopened.timerStartedAt,0);
});
test('parent manual completion survives child changes, backup and metadata edits',()=>{
 const data={};create(data,'test-goal','goal');create(data,'test-child','task','test-goal');action(data,'check','test-goal');action(data,'progress','test-child',{entryId:'entry-one',amount:2,minutes:3});const goal=data.goalTasks.find(t=>t.id==='test-goal');assert.equal(goal.status,'completed');assert.equal(goal.actualAmount,0);assert.equal(D.execute(data,{command:'list'}).items.find(t=>t.id===goal.id).actualAmount,2);
 D.execute(data,{command:'save',item:{...goal,title:'更新标题',targetAmount:20},expectedVersion:goal.updatedAt},now);assert.equal(data.goalTasks.find(t=>t.id===goal.id).manualCompleted,true);
 action(data,'check','test-child');action(data,'updateProgress','test-child',{entryId:'entry-one',editId:'edit-one',amount:1,minutes:3});assert.equal(data.goalTasks.find(t=>t.id==='test-child').status,'completed');action(data,'deleteProgress','test-child',{entryId:'entry-one'});assert.equal(data.goalTasks.find(t=>t.id==='test-child').status,'completed');
 const restored={};assert.equal(D.restore(restored,D.backup(data.goalTasks,now)),2);assert(restored.goalTasks.every(t=>t.manualCompleted&&t.status==='completed'));assert(restored.goalTasks.every(t=>t.actualAmount===0));
});
test('checkbox rejects stale writes; explicit deletes detach children and stop deleted timers',()=>{
 const data={};const initial=create(data,'test-goal','goal');create(data,'test-child','task','test-goal');assert.throws(()=>D.execute(data,{command:'check',id:initial.id,expectedVersion:initial.updatedAt},now),/已变化/);
 data.goalTasks.find(t=>t.id==='test-child').timerStartedAt=now;assert.throws(()=>action(data,'delete','test-goal'),/解除/);action(data,'delete','test-goal',{detachChildren:true,stopTimer:true});assert.equal(data.goalTasks.length,1);assert.equal(data.goalTasks[0].parentId,'');assert.equal(data.goalTasks[0].timerStartedAt,0);action(data,'delete','test-child',{stopTimer:true});assert.equal(data.goalTasks.length,0);
});
test('completed unscheduled and overdue tasks stay in today so their boxes can be unchecked',()=>{
 const today=new Date('2026-11-16T12:00').getTime();
 for(const extra of [{},{scheduledStart:'2026-11-15T10:00',scheduledEnd:'2026-11-15T11:00'}]){
  assert(D.isToday({...item('test-today'),...extra,status:'completed',completedAt:today},today));
  assert(!D.isToday({...item('test-yesterday'),...extra,status:'completed',completedAt:today-86400000},today));
 }
});
