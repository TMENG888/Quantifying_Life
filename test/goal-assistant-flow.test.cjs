const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const D=require('../app/src/main/assets/goal-domain.js');
const source=fs.readFileSync(require.resolve('../app/src/main/assets/goal-assistant.js'),'utf8');
const clone=value=>JSON.parse(JSON.stringify(value));
const proposal=[{ref:'goal',kind:'goal',title:'完成学习项目',area:'learning',targetAmount:1,targetUnit:'项成果',deadline:'2026-11-30T18:00'},
 {ref:'action',kind:'task',title:'实现第一个练习',area:'learning',targetAmount:2,targetUnit:'个练习',parentRef:'goal',plannedMinutes:30}];
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function fixture(saved){
 const store=clone(saved||{goalTasks:[],messages:[]}),state={goalTasks:[],messages:clone(store.messages),chatSession:{id:'session-test'},candidates:[{type:'work_note',note:'原工作日志候选'}],changes:[]},calls=[],toasts=[],faults={},responses=[],buttons=[];
 let uid=0,html='',holdBatch=null;
 const w={GoalDomain:D,LifeDomain:{localTime:()=> '2026-10-30T12:00',uid:()=>`goal-test-${++uid}`},LifeWiki:{parseJSON:JSON.parse}};
 const context={window:w,document:{querySelectorAll:()=>buttons}};vm.createContext(context);vm.runInContext(source,context);const A=w.GoalAssistant;
 function fail(name){if(faults[name]){faults[name]--;throw Error('Synthetic '+name+' failure');}}
 const env={state,esc:escape,progress:()=>{},toast:t=>toasts.push(t),render:()=>{html=A.cards();buttons.length=0;for(const m of html.matchAll(/data-goal-plan="([^"]+)" data-id="([^"]+)"/g))buttons.push({dataset:{goalPlan:m[1],id:m[2]}});A.bind();},api:async(action,p={})=>{
  calls.push({action,p:clone(p)});
  if(action==='ask'){fail('ask');return {text:JSON.stringify(responses.shift()||{answer:'建议先完成练习，再整理成果。',items:proposal}),model:'synthetic'};}
  if(action==='goalTasks'){
   if(p.command==='batch'){fail('batchBefore');if(holdBatch)await holdBatch;const result=D.execute(store,clone(p),1793332800000);fail('batchAfter');return clone(result);}
   fail('list');return clone(D.execute(store,p));
  }
  if(action==='saveMessage'){fail('message');const index=store.messages.findIndex(m=>m.id===p.message.id);if(index<0)store.messages.push(clone(p.message));else store.messages[index]=clone(p.message);return {};}
  throw Error('Unexpected bridge action '+action);
 }};A.init(env);env.render();
 return {A,store,state,calls,toasts,faults,responses,env,get html(){return html;},get buttons(){return buttons;},hold(p){holdBatch=p;},async reply(text='帮我拆解目标'){await A.reply(text);env.render();},click(action,id=state.messages.at(-1)?.id){const button=buttons.find(b=>b.dataset.goalPlan===action&&b.dataset.id===id);assert.ok(button,'pending plan action exists');return button.onclick();},count(command){return calls.filter(c=>c.action==='goalTasks'&&c.p.command===command).length;}};
}

test('draft generation persists an auditable proposal without writing tasks, records or progress',async()=>{
 const f=fixture(),candidates=clone(f.state.candidates);await f.reply();assert.equal(f.store.goalTasks.length,0);assert.equal(f.count('batch'),0);assert.deepEqual(f.state.candidates,candidates);assert.equal(f.store.messages.length,1);const message=f.store.messages[0];assert.equal(message.goalPlan.status,'pending');assert.equal(message.goalPlan.commands.length,2);assert.equal(message.goalPlan.commands[1].item.parentId,message.goalPlan.commands[0].item.id);assert.match(f.html,/确认保存/);assert.ok(f.calls.every(c=>!['saveRecord','applyChanges'].includes(c.action)));
});
test('cancel is durable and does not create tasks; restored cancelled messages have no confirmation card',async()=>{
 const f=fixture();await f.reply();await f.click('cancel');assert.equal(f.store.goalTasks.length,0);assert.equal(f.count('batch'),0);assert.equal(f.store.messages[0].goalPlan.status,'cancelled');assert.equal(f.html,'');assert.equal(fixture(f.store).html,'');
});
test('confirmation atomically creates linked tasks and restores applied message state',async()=>{
 const f=fixture();await f.reply();const beforeIds=f.store.messages[0].goalPlan.commands.map(c=>c.item.id);await f.click('apply');assert.equal(f.count('batch'),1);assert.deepEqual(f.store.goalTasks.map(t=>t.id),beforeIds);assert.equal(f.state.goalTasks.length,2);assert.equal(f.store.messages[0].goalPlan.status,'applied');assert.equal(f.store.goalTasks[1].actualAmount,0);assert.equal(f.store.goalTasks[1].actualMinutes,0);assert.equal(fixture(f.store).html,'');
});
test('failed task save leaves enabled pending actions and retries the same IDs',async()=>{
 const f=fixture();await f.reply();const ids=f.store.messages[0].goalPlan.commands.map(c=>c.item.id);f.faults.batchBefore=1;await f.click('apply');assert.equal(f.store.goalTasks.length,0);assert.equal(f.state.messages[0].goalPlan.status,'pending');assert.doesNotMatch(f.html,/disabled/);assert.match(f.toasts.at(-1),/batchBefore/);await f.click('apply');assert.deepEqual(f.store.goalTasks.map(t=>t.id),ids);assert.equal(f.count('batch'),2);assert.equal(f.store.messages[0].goalPlan.status,'applied');
});
test('lost native success response retries by reconciling IDs instead of writing a duplicate batch',async()=>{
 const f=fixture();await f.reply();f.faults.batchAfter=1;await f.click('apply');assert.equal(f.store.goalTasks.length,2);assert.equal(f.state.messages[0].goalPlan.status,'pending');await f.click('apply');assert.equal(f.count('batch'),1);assert.equal(f.store.goalTasks.length,2);assert.equal(f.store.messages[0].goalPlan.status,'applied');
});
test('task commit followed by message failure survives refresh and retries only message state',async()=>{
 const f=fixture();await f.reply();f.faults.message=1;await f.click('apply');assert.equal(f.store.goalTasks.length,2);assert.equal(f.store.messages[0].goalPlan.status,'pending');assert.match(f.toasts.at(-1),/任务已经保存/);const restored=fixture(f.store);await restored.click('apply');assert.equal(restored.count('batch'),0);assert.equal(restored.store.goalTasks.length,2);assert.equal(restored.store.messages[0].goalPlan.status,'applied');
});
test('cancel after a lost success never falsely reports already-created tasks as cancelled',async()=>{
 const f=fixture();await f.reply();f.faults.batchAfter=1;await f.click('apply');await f.click('cancel');assert.equal(f.store.goalTasks.length,2);assert.equal(f.count('batch'),1);assert.equal(f.store.messages[0].goalPlan.status,'applied');assert.match(f.toasts.at(-1),/已经保存.*不能取消/);assert.doesNotMatch(f.store.messages[0].text,/此计划已取消/);
});
test('partial existing plan refuses both apply and cancel without guessing or writing',async()=>{
 for(const action of ['apply','cancel']){const f=fixture();await f.reply();D.execute(f.store,clone(f.store.messages[0].goalPlan.commands[0]));await f.click(action);assert.equal(f.count('batch'),0);assert.equal(f.store.messages[0].goalPlan.status,'pending');assert.equal(f.store.goalTasks.length,1);assert.match(f.toasts.at(-1),/部分任务已变化/);}
});
test('rapid repeated confirmation is guarded while the first batch remains in flight',async()=>{
 const f=fixture();await f.reply();let release;f.hold(new Promise(resolve=>release=resolve));const first=f.click('apply');await Promise.resolve();const second=f.click('apply');await second;assert.equal(f.count('batch'),1);assert.match(f.html,/disabled/);release();await first;assert.equal(f.store.goalTasks.length,2);assert.equal(f.count('batch'),1);assert.equal(f.store.messages[0].goalPlan.status,'applied');
});
test('all pending plans remain visible chronologically and can be cancelled independently',async()=>{
 const f=fixture();await f.reply();const first=f.state.messages[0];first.createdAt=1;await f.reply();f.state.messages[1].createdAt=2;f.env.render();assert.equal((f.html.match(/aria-label="待确认的任务计划"/g)||[]).length,2);assert.ok(f.html.indexOf(first.id)<f.html.indexOf(f.state.messages[1].id));await f.click('cancel',first.id);assert.equal(first.goalPlan.status,'cancelled');assert.equal(f.state.messages[1].goalPlan.status,'pending');assert.equal((f.html.match(/aria-label="待确认的任务计划"/g)||[]).length,1);assert.equal(f.count('batch'),0);
});
test('query-only responses and failed model calls never create task plans or task mutations',async()=>{
 const f=fixture();f.responses.push({answer:'当前没有已完成的任务。',items:[]});await f.reply('查询我的任务');assert.equal(f.html,'');assert.equal(f.state.messages[0].goalPlan,undefined);f.faults.ask=1;await assert.rejects(f.reply(),/ask failure/);assert.equal(f.count('batch'),0);assert.equal(f.store.goalTasks.length,0);
});
