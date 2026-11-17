(function(root){
 'use strict';
 const WIDTH=80,bindings=new WeakMap();
 function bind(container,onDelete,onEdit){
  const nodes=[...container.querySelectorAll('[data-goal-progress-entry]')],prior=bindings.get(container);
  if(prior&&prior.nodes.length===nodes.length&&prior.nodes.every((n,i)=>n===nodes[i])){prior.refresh();return;}
  prior?.dispose();const listeners=[];
  function on(node,type,fn){node.addEventListener(type,fn);listeners.push([node,type,fn]);}
  bindings.set(container,{nodes,refresh:()=>{for(const row of nodes){if(gesture?.row===row&&gesture.horizontal){row.classList.add('is-dragging');row.querySelector('[data-goal-entry-delete]').style.transform='translateX('+(WIDTH+gesture.offset)+'px)';}else setOpen(row,row===opened);}},dispose:()=>{finish(true);for(const [node,type,fn] of listeners)node.removeEventListener?.(type,fn);}});
  let opened=null,gesture=null,suppressUntil=0;
  function setOpen(row,open){
   if(open&&opened&&opened!==row)setOpen(opened,false);
   const content=row.querySelector('.goal-progress-content'),button=row.querySelector('[data-goal-entry-delete]');
   row.classList.toggle('is-open',open);row.classList.remove('is-dragging');button.style.transform='';content.setAttribute('aria-expanded',String(open));
   button.tabIndex=open?0:-1;button.setAttribute('aria-hidden',String(!open));
   const edit=row.querySelector('[data-goal-entry-edit]');if(edit){edit.tabIndex=open?-1:0;edit.setAttribute('aria-hidden',String(open));}
   if(open)opened=row;else if(opened===row)opened=null;
  }
  function finish(cancelled){
   if(!gesture)return;const g=gesture;gesture=null;
   if(g.horizontal){suppressUntil=Date.now()+350;setOpen(g.row,cancelled?g.wasOpen:g.offset<-WIDTH/2);}
  }
  container.querySelectorAll('[data-goal-progress-entry]').forEach(row=>{
   const content=row.querySelector('.goal-progress-content'),button=row.querySelector('[data-goal-entry-delete]');
   content.setAttribute('aria-label',content.textContent.trim()+'，点击编辑，左滑或按左方向键显示删除');
   const edit=()=>onEdit?.(row.dataset.goalTask,row.dataset.goalProgressEntry);
   const editButton=row.querySelector('[data-goal-entry-edit]');if(editButton)on(editButton,'click',edit);
   on(content,'pointerdown',event=>{
    if(event.isPrimary===false||event.button>0){finish(true);return;}
    if(gesture){finish(true);return;}
    // A new intentional touch must not inherit the swipe's ghost-click delay.
    suppressUntil=0;
    if(opened&&opened!==row)setOpen(opened,false);
    // Leave the screen edge available for Android's back gesture.
    if(event.clientX<24)return;
    gesture={row,pointer:event.pointerId,x:event.clientX,y:event.clientY,wasOpen:row.classList.contains('is-open'),offset:row.classList.contains('is-open')?-WIDTH:0,horizontal:false,vertical:false};
   });
   on(content,'pointermove',event=>{
    const g=gesture;if(!g||g.row!==row||g.pointer!==event.pointerId||g.vertical)return;
    const dx=event.clientX-g.x,dy=event.clientY-g.y;
    if(!g.horizontal){if(Math.max(Math.abs(dx),Math.abs(dy))<10)return;if(Math.abs(dy)>=Math.abs(dx)){g.vertical=true;return;}g.horizontal=true;row.classList.add('is-dragging');try{content.setPointerCapture(event.pointerId);}catch(_){} }
    g.offset=Math.max(-WIDTH,Math.min(0,(g.wasOpen?-WIDTH:0)+dx));button.style.transform='translateX('+(WIDTH+g.offset)+'px)';
   });
   on(content,'pointerup',event=>{if(gesture?.pointer===event.pointerId)finish(false);});
   on(content,'pointercancel',()=>finish(true));
   // Taking capture from a child text element also emits a bubbling loss event.
   // Only losing capture on this surface cancels the active gesture.
   on(content,'lostpointercapture',event=>{if(event.target===content&&gesture?.pointer===event.pointerId)finish(true);});
   on(content,'click',()=>{if(Date.now()<suppressUntil)return;if(opened===row)setOpen(row,false);else edit();});
   on(content,'keydown',event=>{
    if(['Enter',' '].includes(event.key)&&onEdit){event.preventDefault();edit();}
    else if(['ArrowLeft','Enter',' '].includes(event.key)){event.preventDefault();setOpen(row,true);button.focus({preventScroll:true});}
    else if(['ArrowRight','Escape'].includes(event.key)){event.preventDefault();setOpen(row,false);}
   });
   on(button,'keydown',event=>{if(event.key==='Escape'||event.key==='ArrowRight'){event.preventDefault();setOpen(row,false);content.focus({preventScroll:true});}});
   on(button,'click',()=>onDelete(row.dataset.goalTask,row.dataset.goalProgressEntry));
  });
  on(container,'pointerdown',event=>{if(opened&&!event.target.closest('[data-goal-progress-entry]'))setOpen(opened,false);});
 }
 const api={bind};if(typeof module==='object'&&module.exports)module.exports=api;else root.GoalProgressSwipe=api;
})(typeof window!=='undefined'?window:globalThis);
