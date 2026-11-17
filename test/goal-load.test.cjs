const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
function harness(){
 let resolve,reject,calls=0,renders=0,failure=null;const command={dataset:{goalCommand:'reopen',id:'task-test'}},nodes={editor:{open:false,addEventListener(){}},'goal-root':{},'goal-back':{}};
 const doc={getElementById:id=>nodes[id],querySelectorAll:s=>s==='[data-goal-command]'?[command]:[],addEventListener(){}};
 const item={id:'task-test',updatedAt:1,actualAmount:1,targetAmount:4,status:'active'},state={page:'tasks',goalTasks:[item],goalTasksLoaded:true};
 const env={state,render:()=>renders++,go:p=>state.page=p,toast(){},api:(name,p)=>{if(p.command==='list'){calls++;return new Promise((yes,no)=>{resolve=yes;reject=no;});}if(failure)return Promise.reject(failure);return Promise.resolve({items:[{...item,updatedAt:2,actualAmount:2}]});}};
 const window={scrollY:0,scrollTo(){},GoalDomain:{number:String},GoalProgressSwipe:{bind(){}}};vm.runInNewContext(fs.readFileSync('app/src/main/assets/goal-tasks.js','utf8'),{window,document:doc,setInterval(){},Date});window.GoalTasksUI.init(env);
 return {ui:window.GoalTasksUI,state,item,command,fail:e=>failure=e,resolve:v=>resolve(v),reject:e=>reject(e),calls:()=>calls,renders:()=>renders};
}
test('concurrent goal reads share one native request and unchanged data does not render',async()=>{
 const h=harness(),a=h.ui.load(),b=h.ui.load();assert.equal(h.calls(),1);h.resolve({items:[h.item]});await Promise.all([a,b]);assert.equal(h.renders(),0);assert.equal(h.state.goalTasks[0].updatedAt,1);
});
test('late results do not redraw a different module but refresh the current task view exactly once',async()=>{
 const h=harness(),a=h.ui.load();h.state.page='records';h.resolve({items:[{...h.item,updatedAt:2}]});await a;assert.equal(h.renders(),0);assert.equal(h.state.goalTasks[0].updatedAt,2);
 const k=harness(),b=k.ui.load();k.ui.open('another-task');k.resolve({items:[{...k.item,updatedAt:3}]});await b;assert.equal(k.renders(),1,'only the current route consumer renders the shared changed result');
});
test('an old read cannot overwrite a newer successful progress command',async()=>{
 const h=harness(),read=h.ui.load();h.ui.bind();await h.command.onclick();assert.equal(h.state.goalTasks[0].actualAmount,2);h.resolve({items:[h.item]});await read;assert.equal(h.state.goalTasks[0].actualAmount,2);assert.equal(h.renders(),1);
});
test('failed goal reads release the request so retry can succeed',async()=>{
 const h=harness(),a=h.ui.load();h.reject(Error('network'));await a;assert.equal(h.renders(),1);const b=h.ui.load();assert.equal(h.calls(),2);h.resolve({items:[h.item]});await b;assert.equal(h.renders(),2);
});
test('a failed mutation refreshes versions so the next action is not stuck on a stale snapshot',async()=>{
 const h=harness();h.fail(Error('任务已更新'));h.ui.bind();const pending=h.command.onclick();await new Promise(setImmediate);assert.equal(h.calls(),1);h.resolve({items:[{...h.item,updatedAt:3}]});await pending;assert.equal(h.state.goalTasks[0].updatedAt,3);
});
