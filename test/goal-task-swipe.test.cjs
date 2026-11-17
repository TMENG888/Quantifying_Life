const test=require('node:test'),assert=require('node:assert/strict');
const {bind}=require('../app/src/main/assets/goal-task-swipe.js');
function fixture(){
 const node=()=>{const classes=new Set();return {style:{},attrs:{},classList:{add:k=>classes.add(k),remove:k=>classes.delete(k),toggle:(k,on)=>on?classes.add(k):classes.delete(k),contains:k=>classes.has(k)},setAttribute(k,v){this.attrs[k]=v;},setPointerCapture(){},focus(){this.focused=true;}};};
 const rows=[0,1].map(()=>{const row=node(),item=node(),body=node(),button=node(),check=node();row.querySelector=s=>s==='.goal-item'?item:s==='.goal-item-body'?body:button;row.closest=s=>s==='[data-goal-row]'?row:null;for(const [el,selector]of [[item,'.goal-item'],[body,'.goal-item-body'],[button,'.goal-row-delete'],[check,'.goal-check']])el.closest=s=>s==='[data-goal-row]'?row:s.split(',').includes(selector)?el:null;return Object.assign(row,{item,body,button,check});});
 const listeners=new Map(),container={querySelectorAll:()=>rows,addEventListener:(t,f,o)=>listeners.set(t+!!o,f),removeEventListener:(t,f,o)=>listeners.delete(t+!!o)};
 function fire(type,target,extra={}){const e={target,pointerId:1,button:0,isPrimary:true,clientX:220,clientY:100,preventDefault(){this.prevented=true;},stopImmediatePropagation(){this.stopped=true;},...extra};listeners.get(type+'true')?.(e);if(!e.stopped)listeners.get(type+'false')?.(e);return e;}
 bind(container);return {rows,container,listeners,fire};
}
test('task row follows finger; only one delete action can remain open',()=>{
 const {rows,fire}=fixture(),r=rows[0];fire('pointerdown',r.body);fire('pointermove',r.body,{clientX:160});assert.equal(r.item.style.transform,'translateX(-60px)');fire('pointerup',r.body);assert(r.classList.contains('is-open'));assert.equal(r.button.tabIndex,0);assert.equal(r.button.attrs['aria-hidden'],'false');
 const ghost=fire('click',r.body);assert(ghost.prevented&&ghost.stopped);assert(r.classList.contains('is-open'));
 fire('pointerdown',rows[1].body);fire('pointermove',rows[1].body,{clientX:150});fire('pointerup',rows[1].body);assert(!r.classList.contains('is-open'));assert(rows[1].classList.contains('is-open'));
 fire('pointerdown',rows[1].body);fire('pointerup',rows[1].body);assert(fire('click',rows[1].body).stopped);assert(!rows[1].classList.contains('is-open'));
});
test('vertical scroll, short drags, edge gestures, secondary pointers and cancel never open deletion',()=>{
 for(const scenario of ['vertical','short','edge','secondary','cancel']){const {rows,fire}=fixture(),r=rows[0];fire('pointerdown',r.body,{clientX:scenario==='edge'?15:220,isPrimary:scenario!=='secondary'});fire('pointermove',r.body,{clientX:scenario==='vertical'?215:scenario==='short'?205:140,clientY:scenario==='vertical'?175:101});fire(scenario==='cancel'?'pointercancel':'pointerup',r.body);assert(!r.classList.contains('is-open'),scenario);assert.equal(r.button.tabIndex,-1);}
 const {rows,fire}=fixture();fire('pointerdown',rows[0].check);fire('pointermove',rows[0].check,{clientX:140});fire('pointerup',rows[0].check);assert(!rows[0].classList.contains('is-open'),'a checkbox tap cannot begin a row drag');
});
test('keyboard access and repeated binding retain one set of listeners',()=>{
 const {rows,fire,container,listeners}=fixture(),r=rows[0],count=listeners.size;fire('keydown',r.body,{key:'ArrowLeft'});assert(r.classList.contains('is-open'));assert(r.button.focused);fire('keydown',r.button,{key:'Escape'});assert(!r.classList.contains('is-open'));assert(r.body.focused);bind(container);assert.equal(listeners.size,count);fire('keydown',r.body,{key:'ArrowLeft'});assert(r.classList.contains('is-open'));
});
test('rebinding a patched list preserves open state and an in-flight drag',()=>{
 const {rows,fire,container}=fixture(),r=rows[0];fire('keydown',r.body,{key:'ArrowLeft'});r.classList.remove('is-open');bind(container);assert(r.classList.contains('is-open'));
 fire('keydown',r.body,{key:'ArrowRight'});fire('pointerdown',r.body);fire('pointermove',r.body,{clientX:170});r.item.style.transform='';r.classList.remove('is-dragging');bind(container);assert.equal(r.item.style.transform,'translateX(-50px)');assert(r.classList.contains('is-dragging'));fire('pointerup',r.body);assert(r.classList.contains('is-open'));
});
