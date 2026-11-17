(function(root){
 'use strict';
 // Cache only ordinary list/detail views. Maps, chat, timers and model forms
 // retain their own lifecycle, input connection and permission handling.
 const cache=new Map(),LIMIT=5,keys=['data-record','data-goal-row','data-goal-open','data-goal-progress-entry','data-record-list','data-record-tab','data-evidence-day','data-calendar-bar'];
 let current='',currentCacheable=false,animation=null,pressed=null,origin=null;
 const counters={created:0,patched:0,skipped:0,restored:0};
 function key(n){if(n.nodeType!==1)return '';if(n.id)return 'id:'+n.id;for(const a of keys)if(n.hasAttribute(a))return n.tagName+':'+a+':'+n.getAttribute(a);return '';}
 function same(a,b){return a.nodeType===b.nodeType&&(a.nodeType!==1||a.tagName===b.tagName&&key(a)===key(b));}
 function patch(old,next){
  if(old.isEqualNode(next))return;
  if(old.nodeType!==1){if(old.nodeValue!==next.nodeValue)old.nodeValue=next.nodeValue;return;}
  const focused=old===document.activeElement,field=/^(INPUT|TEXTAREA|SELECT)$/.test(old.tagName),open=old.tagName==='DETAILS'&&old.open&&!old.id;
  for(const a of [...old.attributes])if(!next.hasAttribute(a.name)&&!['data-ui-pressed','data-ui-exiting'].includes(a.name)&&!(a.name==='aria-busy'&&old.hasAttribute('data-ui-exiting'))&&!(a.name==='open'&&open))old.removeAttribute(a.name);
  for(const a of [...next.attributes])if(!(focused&&field&&a.name==='value')&&old.getAttribute(a.name)!==a.value)old.setAttribute(a.name,a.value);
  // Focused text is owned by the user/IME until a deliberate input handler
  // changes it. Never replace the input node or write its value during polling.
  if(old.tagName!=='TEXTAREA'&&old.tagName!=='INPUT')children(old,next);
  if(field&&!focused){if(old.value!==next.value)old.value=next.value;if(old.tagName==='INPUT')old.checked=next.checked;}
 }
 function children(parent,next){
  const keyed=new Map([...parent.childNodes].map(n=>[key(n),n]).filter(([k])=>k));
  let cursor=parent.firstChild;
  for(const desired of [...next.childNodes]){
   let actual=key(desired)?keyed.get(key(desired)):null;
   if(actual&&!same(actual,desired))actual=null;
   if(!actual&&cursor&&same(cursor,desired))actual=cursor;
   if(actual){if(actual!==cursor)parent.insertBefore(actual,cursor);patch(actual,desired);cursor=actual.nextSibling;}
   else{actual=desired.cloneNode(true);parent.insertBefore(actual,cursor);}
  }
  while(cursor){const nextNode=cursor.nextSibling;parent.removeChild(cursor);cursor=nextNode;}
 }
 function stop(){animation?.cancel();animation=null;}
 function enter(main,direction){
  stop();if(!main.animate||root.matchMedia?.('(prefers-reduced-motion: reduce)').matches)return;
  animation=main.animate([{opacity:.72,transform:'translateX('+(direction==='back'?-10:10)+'px)'},{opacity:1,transform:'translateX(0)'}],{duration:160,easing:'cubic-bezier(.2,.65,.3,1)'});
  animation.onfinish=()=>{animation=null;};
 }
 function render(main,html,view,options={}){
  const changed=current!==view,oldY=options.previousScroll?.y??root.scrollY,oldX=options.previousScroll?.x??root.scrollX;
  let cached=cache.get(view),entry=cache.get(current);
  const positions=!changed&&options.transition==='list'?root.ViewMotion?.capture(main):null;
  if(changed){
   root.ViewMotion?.stop();
   stop();clearPress();
   if(currentCacheable){entry=entry||{};entry.x=oldX;entry.y=oldY;entry.nodes=document.createDocumentFragment();while(main.firstChild)entry.nodes.append(main.firstChild);cache.delete(current);cache.set(current,entry);}
   else main.textContent='';
   if(cached?.nodes){main.append(cached.nodes);counters.restored++;}else cached=null;
  }
  if(!changed&&options.replace)main.textContent='';
  const fresh=!main.firstChild;let updated=false;
  if(fresh){main.innerHTML=html;counters.created++;}
  else if(cached?.html===html||!changed&&entry?.html===html)counters.skipped++;
  else{const template=document.createElement('template');template.innerHTML=html;children(main,template.content);counters.patched++;updated=true;}
  current=view;currentCacheable=!!options.cache;
  cache.delete(view);cache.set(view,{html,x:changed?cached?.x||0:oldX,y:changed?cached?.y||0:oldY});
  while(cache.size>LIMIT){const oldest=cache.keys().next().value;cache.delete(oldest);}
  // Retained inputs keep their caret naturally. Even setting the same range
  // can commit/cancel Android IME composition, so polling never touches it.
  const x=changed?cached?.x||0:oldX,y=changed?cached?.y||0:oldY;
  if(root.scrollX!==x||root.scrollY!==y)root.scrollTo({left:x,top:y,behavior:'instant'});
  if(changed&&options.motion!==false)enter(main,options.direction);
  else if((updated||fresh)&&positions)root.ViewMotion?.play(main,positions);
  return {changed,fresh,updated,restored:!!cached};
 }
 function clearPress(){if(pressed)pressed.removeAttribute('data-ui-pressed');pressed=null;origin=null;}
 function init(){
  document.documentElement.dataset.inputMode='pointer';
  document.addEventListener('keydown',event=>{if(['Tab','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(event.key)){clearPress();document.documentElement.dataset.inputMode='keyboard';}},true);
  document.addEventListener('pointerdown',event=>{
   clearPress();document.documentElement.dataset.inputMode='pointer';
   if(event.isPrimary===false||event.button>0)return;
   const button=event.target.closest('button,a,summary,[role="button"]');
   if(!button||button.disabled||button.getAttribute('aria-disabled')==='true'||event.target.closest('input,textarea,[contenteditable="true"]'))return;
   pressed=button;origin={id:event.pointerId,x:event.clientX,y:event.clientY};pressed.setAttribute('data-ui-pressed','');
  },{capture:true,passive:true});
  document.addEventListener('pointermove',event=>{if(origin&&(origin.id!==event.pointerId||Math.hypot(event.clientX-origin.x,event.clientY-origin.y)>10))clearPress();},{capture:true,passive:true});
  for(const type of ['pointerup','pointercancel','lostpointercapture'])document.addEventListener(type,clearPress,{capture:true,passive:true});
  document.addEventListener('visibilitychange',()=>{if(document.hidden){clearPress();stop();root.ViewMotion?.stop();}});
 }
 root.ViewRuntime={init,render,clearPress,stats:()=>({...counters,cached:cache.size}),stop};
})(window);
