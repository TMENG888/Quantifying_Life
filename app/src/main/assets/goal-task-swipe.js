(function(root){
 'use strict';
 const WIDTH=76,bindings=new WeakMap();
 function bind(container){
  const existing=bindings.get(container);if(existing){existing.refresh();return;}
  let opened=null,gesture=null,suppressUntil=0;const listeners=[];
  function on(type,fn,options){container.addEventListener(type,fn,options);listeners.push([type,fn,options]);}
  function setOpen(row,open){
   if(open&&opened&&opened!==row)setOpen(opened,false);
   row.classList.toggle('is-open',open);row.classList.remove('is-dragging');row.querySelector('.goal-item').style.transform='';
   const button=row.querySelector('.goal-row-delete'),body=row.querySelector('.goal-item-body');
   button.tabIndex=open?0:-1;button.setAttribute('aria-hidden',String(!open));body.setAttribute('aria-expanded',String(open));
   if(open)opened=row;else if(opened===row)opened=null;
  }
  function finish(cancel){
   if(!gesture)return;const g=gesture;gesture=null;
   if(g.horizontal){suppressUntil=Date.now()+350;setOpen(g.row,cancel?g.wasOpen:g.offset<-WIDTH/2);}
  }
  function refresh(){
   const rows=[...container.querySelectorAll('[data-goal-row]')];if(opened&&!rows.includes(opened))opened=null;
   if(gesture&&!rows.includes(gesture.row))finish(true);
   for(const row of rows){if(gesture?.row===row&&gesture.horizontal){row.classList.add('is-dragging');row.querySelector('.goal-item').style.transform='translateX('+gesture.offset+'px)';}
    else setOpen(row,row===opened);}
  }
  refresh();
  on('pointerdown',event=>{
   if(gesture){finish(true);return;}
   suppressUntil=0;
   const row=event.target.closest('[data-goal-row]');if(opened&&opened!==row)setOpen(opened,false);
   if(!row||event.isPrimary===false||event.button>0||event.clientX<24||event.target.closest('.goal-check,.goal-row-delete'))return;
   gesture={row,pointer:event.pointerId,x:event.clientX,y:event.clientY,wasOpen:row===opened,offset:row===opened?-WIDTH:0,horizontal:false,vertical:false};
  });
  on('pointermove',event=>{
   const g=gesture;if(!g||g.pointer!==event.pointerId||g.vertical)return;
   const dx=event.clientX-g.x,dy=event.clientY-g.y;
   if(!g.horizontal){if(Math.max(Math.abs(dx),Math.abs(dy))<10)return;if(Math.abs(dy)>=Math.abs(dx)){g.vertical=true;return;}g.horizontal=true;g.row.classList.add('is-dragging');try{g.row.setPointerCapture(event.pointerId);}catch(_){} }
   g.offset=Math.max(-WIDTH,Math.min(0,(g.wasOpen?-WIDTH:0)+dx));g.row.querySelector('.goal-item').style.transform='translateX('+g.offset+'px)';
  });
  on('pointerup',event=>{if(gesture?.pointer===event.pointerId)finish(false);});
  on('pointercancel',()=>finish(true));
  on('lostpointercapture',event=>{if(gesture?.pointer===event.pointerId&&event.target===gesture.row)finish(true);});
  on('click',event=>{
   if(!event.target.closest('[data-goal-row]'))return;
   if(Date.now()<suppressUntil){event.preventDefault();event.stopImmediatePropagation();return;}
   if(opened&&event.target.closest('.goal-item-body')){event.preventDefault();event.stopImmediatePropagation();setOpen(opened,false);}
  },true);
  on('keydown',event=>{
   const row=event.target.closest('[data-goal-row]');if(!row)return;
   if(event.key==='ArrowLeft'){event.preventDefault();setOpen(row,true);row.querySelector('.goal-row-delete').focus({preventScroll:true});}
   else if(event.key==='ArrowRight'||event.key==='Escape'){event.preventDefault();setOpen(row,false);row.querySelector('.goal-item-body').focus({preventScroll:true});}
  });
  bindings.set(container,{refresh});
 }
 const api={bind};if(typeof module==='object'&&module.exports)module.exports=api;else root.GoalTaskSwipe=api;
})(typeof window!=='undefined'?window:globalThis);
