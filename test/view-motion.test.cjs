const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
function harness(){
 const timers=new Set(),jobs=[],window={innerHeight:800,document:{hidden:false},matchMedia:()=>({matches:false})};
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/view-motion.js','utf8'),{window,Promise,Map,WeakMap,Set,setTimeout(fn){timers.add(fn);return fn;},clearTimeout:fn=>timers.delete(fn)});
 function row(id,top=0){const attrs={'data-goal-row':id};let y=top;return {isConnected:true,hasAttribute:k=>Object.hasOwn(attrs,k),getAttribute:k=>attrs[k]??null,setAttribute:(k,v)=>attrs[k]=v,removeAttribute:k=>delete attrs[k],top:v=>y=v,getBoundingClientRect:()=>({left:0,top:y,bottom:y+80,width:350,height:80}),animate(frames,options){const a={frames,options,cancel(){this.cancelled=true;this.oncancel?.();}};jobs.push(a);return a;}};}
 return {ui:window.ViewMotion,window,jobs,timers,row};
}
test('removal fades and shrinks via compositor properties, finishes once and cleans temporary state',async()=>{
 const {ui,row,jobs,timers}=harness(),el=row('one');const a=ui.exit(el),b=ui.exit(el);assert.equal(a,b);assert.equal(jobs.length,1);assert.equal(el.getAttribute('aria-busy'),'true');assert.equal(jobs[0].options.duration,180);assert.equal(jobs[0].frames.at(-1).opacity,0);assert.match(jobs[0].frames.at(-1).transform,/scale/);assert(!jobs[0].frames.some(f=>'height'in f));jobs[0].onfinish();await a;assert.equal(el.getAttribute('data-ui-exiting'),null);assert.equal(el.getAttribute('aria-busy'),null);assert.equal(ui.stats().active,0);assert.equal(timers.size,0);
});
test('FLIP reads first, animates only changed visible rows and leaves unchanged rows untouched',()=>{
 const {ui,row,jobs}=harness(),a=row('a',50),b=row('b',150),offscreen=row('far',1200);let rows=[a,b,offscreen];const box={querySelectorAll:()=>rows};const before=ui.capture(box);rows=[b];b.top(50);ui.play(box,before);assert.equal(jobs.length,1);assert.equal(jobs[0].frames[0].transform,'translate(0px,100px)');assert.equal(jobs[0].options.duration,240);const same=ui.capture(box);ui.play(box,same);assert.equal(jobs.length,1);assert(jobs[0].cancelled);
});
test('reduced motion, missing and offscreen nodes skip effects without delaying completion',async()=>{
 const {ui,window,row,jobs}=harness();window.matchMedia=()=>({matches:true});await ui.exit(row('a'));assert.equal(ui.capture({querySelectorAll:()=>[row('a')]}),null);ui.append([row('a')]);assert.equal(jobs.length,0);window.matchMedia=()=>({matches:false});await ui.exit(null);await ui.exit(row('far',1200));assert.equal(jobs.length,0);
});
test('navigation cancellation settles exit promises and leaves no animation handles',async()=>{
 const {ui,row,jobs}=harness(),el=row('a'),p=ui.exit(el);ui.stop();await p;assert(jobs[0].cancelled);assert.equal(ui.stats().active,0);assert.equal(el.getAttribute('data-ui-exiting'),null);
});
test('missing animation completion event has a bounded fallback',async()=>{
 const {ui,row,timers}=harness(),p=ui.exit(row('one'));for(const fn of timers)fn();await p;assert.equal(ui.stats().active,0);
});
