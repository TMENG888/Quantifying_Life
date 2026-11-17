const test=require('node:test'),assert=require('node:assert/strict');
const A=require('../app/src/main/assets/goal-assistant.js');
let id=0;const uid=()=>`test-task-${++id}`;
const goal={ref:'g',kind:'goal',title:'完成打标程序',area:'work',targetAmount:1,targetUnit:'项成果',deadline:'2026-11-01T12:00'};
const action={ref:'t',kind:'task',title:'实现接口',area:'work',targetAmount:1,targetUnit:'个接口',scheduledStart:'2026-10-31T09:00',scheduledEnd:'2026-10-31T11:00',parentRef:'g'};
test('AI proposals create metadata only, sort parents first, never accept actual progress',()=>{
 const commands=A.prepare([{...action,actualAmount:999},goal],[],uid);assert.equal(commands[0].item.kind,'goal');assert.equal(commands[1].item.parentId,commands[0].item.id);assert.equal(commands[1].item.actualAmount,undefined);assert.equal(commands[0].expectedVersion,null);
});
test('AI proposals reject unknown or duplicate parents and invalid windows',()=>{
 assert.throws(()=>A.prepare([action],[],uid));assert.throws(()=>A.prepare([goal,goal],[],uid));assert.throws(()=>A.prepare([goal,{...action,scheduledEnd:'2026-11-02T10:00'}],[],uid));assert.throws(()=>A.prepare(Array(21).fill(goal),[],uid));
});
test('explicit planning intent avoids hijacking completed work notes and articles',()=>{
 for(const text of ['帮我把目标拆解为量化行动','新建任务，19:00到19:30仰卧起坐28个','查看我的任务进度'])assert.equal(A.intent(text),true,text);
 for(const text of ['今天完成了接口任务，记录工作日志','学习笔记：RAG的原理','https://example.com/任务计划'])assert.equal(A.intent(text),false,text);
});
test('existing goals must be current active real IDs',()=>{
 const old={id:'existing-goal',...goal,status:'active',parentId:''};const t={...action,parentRef:'',existingGoalId:'existing-goal'};
 assert.equal(A.prepare([t],[old],uid)[0].item.parentId,old.id);assert.throws(()=>A.prepare([t],[{...old,status:'completed'}],uid));assert.throws(()=>A.prepare([t],[],uid));
});
