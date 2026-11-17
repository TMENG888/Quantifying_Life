const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/analysis-export.js'),'utf8');
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
function harness(){
 const button={disabled:false,textContent:'',onclick:null},status={textContent:'',role:'',setAttribute(k,v){this[k]=v},classList:{toggle(){}}},listeners={};let timer;
 const apiCalls=[],requests=[],context={window:{},document:{hidden:false,getElementById:id=>id==='analysis-export'?button:id==='analysis-export-status'?status:null,addEventListener:(n,f)=>listeners[n]=f},setInterval:f=>{timer=f}};
 vm.runInNewContext(source,context);const ui=context.window.AnalysisExportUI;
 ui.init({api:action=>{apiCalls.push(action);const d=deferred();requests.push({action,...d});return d.promise},safe:f=>f()});
 return {ui,button,status,apiCalls,requests,listeners,poll:()=>timer()};
}
const tick=()=>new Promise(r=>setImmediate(r));
test('double tap starts one native export and exposes pending status',async()=>{
 const h=harness();h.ui.bind();h.requests.shift().resolve({state:'idle'});await tick();
 const p=h.button.onclick(),second=h.button.onclick();assert.equal(h.apiCalls.filter(x=>x==='analysisExportStart').length,1);
 assert.equal(h.button.disabled,true);assert.match(h.button.textContent,/整理/);
 h.requests.shift().resolve({state:'waiting',message:'请选择保存位置'});await Promise.all([p,second]);
 assert.equal(h.button.disabled,true);assert.match(h.status.textContent,/请选择/);
 h.ui.accept({state:'cancelled',message:'已取消导出'});assert.equal(h.button.disabled,false);
});
test('late preparation response cannot overwrite completed native event',async()=>{
 const h=harness();h.ui.bind();h.requests.shift().resolve({state:'idle'});await tick();
 const p=h.button.onclick();h.ui.accept({state:'complete',message:'已保存'});
 h.requests.shift().resolve({state:'waiting',message:'过期状态'});await p;
 assert.equal(h.button.disabled,false);assert.equal(h.status.textContent,'已保存');
});
test('stale status poll cannot regress newer native delivery',async()=>{
 const h=harness();h.ui.bind();h.ui.accept({state:'complete',message:'最新成功'});
 h.requests.shift().resolve({state:'saving',message:'过期保存'});await tick();
 assert.equal(h.button.disabled,false);assert.equal(h.status.textContent,'最新成功');
});
test('native rejection is visible as literal text and retry succeeds',async()=>{
 const h=harness();h.ui.bind();h.requests.shift().resolve({state:'idle'});await tick();
 const p=h.button.onclick();h.requests.shift().reject(new Error('<img onerror=alert(1)>'));await p;
 assert.equal(h.button.disabled,false);assert.equal(h.status.role,'alert');assert.match(h.status.textContent,/<img/);
 const retry=h.button.onclick();h.requests.shift().resolve({state:'waiting',message:'请选择'});await retry;
 h.ui.accept({state:'failed',message:'保存失败，可重试'});assert.equal(h.button.disabled,false);
});
test('polling refreshes pending export, omits idle and overlapping status calls',async()=>{
 const h=harness();h.ui.bind();h.requests.shift().resolve({state:'idle'});await tick();let n=h.apiCalls.length;h.poll();assert.equal(h.apiCalls.length,n);
 h.ui.accept({state:'saving'});h.poll();h.poll();assert.equal(h.apiCalls.length,n+1);
 h.requests.shift().resolve({state:'complete',message:'保存完成'});await tick();assert.equal(h.button.disabled,false);
 assert.match(h.ui.entry(),/全部日期/);assert.match(h.ui.entry(),/aria-live="polite"/);
});
