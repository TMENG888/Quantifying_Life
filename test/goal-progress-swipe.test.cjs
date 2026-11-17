const test=require('node:test'),assert=require('node:assert/strict');
const {bind}=require('../app/src/main/assets/goal-progress-swipe.js');
function el(){const classes=new Set(),listeners={};return {textContent:'合成进度记录',style:{},attributes:{},tabIndex:-1,listeners,classList:{toggle:(k,on)=>on?classes.add(k):classes.delete(k),add:k=>classes.add(k),remove:k=>classes.delete(k),contains:k=>classes.has(k)},setAttribute(k,v){this.attributes[k]=v;},addEventListener(k,fn){listeners[k]=fn;},fire(k,v={}){listeners[k]?.({pointerId:1,button:0,isPrimary:true,preventDefault(){},...v});},focus(){this.focused=true;},setPointerCapture(){}};}
function fixture(onEdit){const rows=Array.from({length:2},(_,i)=>{const row=el(),content=el(),button=el(),edit=el();return Object.assign(row,{content,button,edit,dataset:{goalTask:'task-test',goalProgressEntry:'entry-'+i},querySelector:s=>s==='.goal-progress-content'?content:s==='[data-goal-entry-delete]'?button:s==='[data-goal-entry-edit]'?edit:null});}),container=el(),calls=[];container.querySelectorAll=()=>rows;bind(container,(...args)=>calls.push(args),onEdit);return {rows,calls,container};}
test('horizontal left swipe reveals exactly one right-side action without deleting',()=>{
 const {rows,calls}=fixture(),r=rows[0];r.content.fire('pointerdown',{clientX:200,clientY:100});r.content.fire('pointermove',{clientX:130,clientY:104});
 assert.equal(r.content.style.transform,undefined);assert.equal(r.button.style.transform,'translateX(10px)');r.content.fire('pointerup');
 assert(r.classList.contains('is-open'));assert.equal(r.button.tabIndex,0);assert.equal(r.button.attributes['aria-hidden'],'false');assert.equal(calls.length,0);
 rows[1].content.fire('keydown',{key:'ArrowLeft'});assert(!r.classList.contains('is-open'));assert(rows[1].classList.contains('is-open'));
 rows[1].button.fire('click');assert.deepEqual(calls,[['task-test','entry-1']]);
});
test('vertical scroll, screen edge, short swipe and cancelled gestures never expose deletion',()=>{
 for(const scenario of ['vertical','edge','short','cancel']){const {rows}=fixture(),r=rows[0];r.content.fire('pointerdown',{clientX:scenario==='edge'?10:200,clientY:100});r.content.fire('pointermove',{clientX:scenario==='vertical'?196:scenario==='edge'?0:scenario==='short'?180:130,clientY:scenario==='vertical'?170:104});r.content.fire(scenario==='cancel'?'pointercancel':'pointerup');assert(!r.classList.contains('is-open'),scenario);assert.equal(r.button.tabIndex,-1);}
});
test('keyboard reveals and closes action without relying on gestures',()=>{
 const {rows}=fixture(),r=rows[0];r.content.fire('keydown',{key:'Enter'});assert(r.classList.contains('is-open'));assert(r.button.focused);r.button.fire('keydown',{key:'Escape'});assert(!r.classList.contains('is-open'));assert(r.content.focused);assert.equal(r.content.attributes['aria-expanded'],'false');
});
test('capture transfer from child text keeps the horizontal gesture active',()=>{
 const {rows}=fixture(),r=rows[0];r.content.fire('pointerdown',{clientX:200,clientY:100});r.content.fire('pointermove',{clientX:180,clientY:102});
 r.content.fire('lostpointercapture',{target:{}});r.content.fire('pointermove',{clientX:130,clientY:104});r.content.fire('pointerup');
 assert(r.classList.contains('is-open'));assert.equal(r.content.style.transform,undefined);
 r.content.fire('pointerdown',{clientX:130,clientY:100});r.content.fire('pointermove',{clientX:190,clientY:102});r.content.fire('lostpointercapture',{target:r.content});r.content.fire('pointerup');
 assert(r.classList.contains('is-open'),'actual capture cancellation restores the previous open state');
});
test('edit action and keyboard edit an existing entry without invoking Delete',()=>{
 const edits=[],{rows,calls}=fixture((...args)=>edits.push(args)),r=rows[0];r.edit.fire('click');r.content.fire('keydown',{key:'Enter'});
 assert.deepEqual(edits,[['task-test','entry-0'],['task-test','entry-0']]);assert.equal(calls.length,0);
 r.content.fire('keydown',{key:'ArrowLeft'});assert.equal(r.edit.tabIndex,-1);assert.equal(r.edit.attributes['aria-hidden'],'true');r.button.fire('keydown',{key:'Escape'});assert.equal(r.edit.tabIndex,0);
});

test('a fresh tap after a swipe works immediately while the swipe ghost click is ignored',()=>{
 const {rows}=fixture(),r=rows[0];r.content.fire('pointerdown',{clientX:200,clientY:100});r.content.fire('pointermove',{clientX:130,clientY:102});r.content.fire('pointerup');
 r.content.fire('click');assert(r.classList.contains('is-open'),'the swipe itself does not close the action');
 r.content.fire('pointerdown',{clientX:160,clientY:100});r.content.fire('pointerup');r.content.fire('click');assert(!r.classList.contains('is-open'),'a new intentional touch is not swallowed by a timer');
});
