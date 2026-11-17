(function(root){
 'use strict';
 const SELECTOR='[data-goal-row],[data-goal-progress-entry],[data-record],[data-motion-key]',MAX=28;
 const running=new Set(),exits=new WeakMap(),counts={exits:0,moves:0,inserts:0,cancelled:0};
 const reduced=()=>!!root.document?.hidden||!!root.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
 function key(el){for(const attr of ['data-goal-row','data-goal-progress-entry','data-record','data-motion-key'])if(el.hasAttribute(attr))return attr+':'+el.getAttribute(attr);return '';}
 function visible(rect){return rect.width>0&&rect.height>0&&rect.bottom>0&&rect.top<(root.innerHeight||10000);}
 function animate(el,frames,options,kind){
  if(reduced()||!el?.animate)return null;
  const animation=el.animate(frames,options),job={animation,kind};running.add(job);
  const done=()=>running.delete(job);animation.onfinish=done;animation.oncancel=done;
  return animation;
 }
 function stop(kind){for(const job of [...running])if(!kind||job.kind===kind){running.delete(job);counts.cancelled++;job.animation.cancel();}}
 function capture(container){
  if(reduced()||!container?.querySelectorAll)return null;
  const before=new Map();for(const el of container.querySelectorAll(SELECTOR)){
   const rect=el.getBoundingClientRect();if(visible(rect))before.set(key(el),{el,rect});if(before.size>=MAX)break;
  }
  // Read every current visual position before cancelling an interrupted move.
  stop('layout');return before;
 }
 function play(container,before){
  if(!before||reduced())return;
  const plans=[];let seen=0;
  for(const el of container.querySelectorAll(SELECTOR)){
   const rect=el.getBoundingClientRect();if(!visible(rect))continue;
   const old=before.get(key(el));plans.push({el,rect,old});if(++seen>=MAX)break;
  }
  // All layout reads precede animation writes. No animated height or per-frame reflow.
  for(const {el,rect,old}of plans){
   if(old){const x=old.rect.left-rect.left,y=old.rect.top-rect.top;if(Math.abs(x)+Math.abs(y)<1)continue;
    if(Math.abs(y)>(root.innerHeight||1000)){animate(el,[{opacity:0},{opacity:1}],{duration:160},'layout');}
    else animate(el,[{transform:`translate(${x}px,${y}px)`},{transform:'translate(0,0)'}],{duration:240,easing:'cubic-bezier(.2,.75,.25,1)'},'layout');
    counts.moves++;
   }else if(seen<=MAX){animate(el,[{opacity:0,transform:'translateY(6px)'},{opacity:1,transform:'translateY(0)'}],{duration:180,easing:'ease-out'},'layout');counts.inserts++;}
  }
 }
 function exit(el){
  if(!el||!el.isConnected||reduced()||!el.animate||!visible(el.getBoundingClientRect()))return Promise.resolve();
  if(exits.has(el))return exits.get(el);
  const promise=new Promise(resolve=>{
   const aria=el.getAttribute('aria-busy');el.setAttribute('data-ui-exiting','');el.setAttribute('aria-busy','true');
   const animation=animate(el,[{opacity:1,transform:'scale(1)',transformOrigin:'50% 0'},{opacity:0,transform:'scale(.98,.64)',transformOrigin:'50% 0'}],{duration:180,easing:'cubic-bezier(.4,0,1,1)',fill:'forwards'},'exit');
   if(!animation){el.removeAttribute('data-ui-exiting');if(aria===null)el.removeAttribute('aria-busy');else el.setAttribute('aria-busy',aria);resolve();return;}
   counts.exits++;let settled=false,timer;
   const finish=()=>{if(settled)return;settled=true;clearTimeout(timer);for(const job of running)if(job.animation===animation)running.delete(job);animation.oncancel=null;animation.onfinish=null;animation.cancel();el.removeAttribute('data-ui-exiting');if(aria===null)el.removeAttribute('aria-busy');else el.setAttribute('aria-busy',aria);exits.delete(el);resolve();};
   animation.onfinish=finish;animation.oncancel=finish;timer=setTimeout(finish,260);
  });exits.set(el,promise);return promise;
 }
 function append(nodes){if(reduced())return;[...nodes].slice(0,6).forEach((el,i)=>animate(el,[{opacity:0,transform:'translateY(6px)'},{opacity:1,transform:'translateY(0)'}],{duration:160,delay:i*18,easing:'ease-out'},'layout'));}
 root.ViewMotion={capture,play,exit,append,stop,reduced,stats:()=>({...counts,active:running.size})};
})(window);
