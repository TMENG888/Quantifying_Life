(function(root){
 'use strict';
 const STEP=20,lists=new Map();let env,observer;
 function section(name,key,items,card,empty,classes='panel'){
  let s=lists.get(name);if(!s||s.key!==key)s={key,limit:STEP};
  Object.assign(s,{items,card,empty,classes});lists.set(name,s);
  const n=Math.min(s.limit,items.length);
  return `<section data-record-list="${name}"><div class="record-list-items ${classes}">${n?items.slice(0,n).map(card).join(''):empty}</div>${footer(n,items.length)}</section>`;
 }
 function footer(n,total){return `<div class="record-list-footer" aria-live="polite"><span>已显示 ${n} / ${total} 条</span>${n<total?'<button type="button" class="secondary" data-load-records>加载更多</button>':''}</div>`;}
 function more(el){
  const s=lists.get(el.dataset.recordList);if(!s||!el.isConnected)return;
  const start=Math.min(s.limit,s.items.length),end=Math.min(start+STEP,s.items.length);if(end===start)return;
  const box=el.querySelector('.record-list-items');
  box.insertAdjacentHTML('beforeend',s.items.slice(start,end).map(s.card).join(''));s.limit=end;
  // Only new rows need handlers. Appending preserves the search input, scroll and existing DOM.
  const added=Array.from(box.children).slice(start);root.ViewMotion?.append(added);added.forEach(row=>{
   const buttons=row.matches('[data-record]')?[row]:Array.from(row.querySelectorAll('[data-record]'));
   buttons.forEach(b=>b.onclick=()=>env.safe(()=>env.showRecord(b.dataset.record)));
  });
  const old=el.querySelector('.record-list-footer');if(observer)observer.unobserve(old);
  old.outerHTML=footer(end,s.items.length);watch(el);
 }
 function watch(el){const button=el.querySelector('[data-load-records]');if(!button)return;button.onclick=()=>more(el);if(observer)observer.observe(el.querySelector('.record-list-footer'));}
 function bind(){
  if(observer)observer.disconnect();
  observer=typeof root.IntersectionObserver==='function'?new root.IntersectionObserver(entries=>{
   entries.forEach(entry=>{if(entry.isIntersecting&&entry.target.isConnected)more(entry.target.closest('[data-record-list]'));});
  },{rootMargin:'0px 0px 160px 0px'}):null;
  document.querySelectorAll('[data-record-list]').forEach(watch);
 }
 root.RecordList={init:e=>{env=e;},section,bind,more,STEP};
})(window);
