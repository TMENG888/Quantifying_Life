(function(){
  'use strict';
  function at(index){let n=Math.trunc(Number(index)||0);if(n<0)n+=this.length;return n>=0&&n<this.length?this[n]:undefined;}
  [Array.prototype,String.prototype,Object.getPrototypeOf(Uint8Array.prototype)].forEach(function(p){if(!p.at)Object.defineProperty(p,'at',{value:at,writable:true,configurable:true});});
  if(!String.prototype.replaceAll)Object.defineProperty(String.prototype,'replaceAll',{value:function(search,replacement){if(search instanceof RegExp){if(!search.global)throw TypeError('replaceAll requires a global RegExp');return String(this).replace(search,replacement);}return String(this).replace(new RegExp(String(search).replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),'g'),replacement);},writable:true,configurable:true});
  if(typeof window!=='undefined')window.addEventListener('error',function(e){if(String(e.filename||'').includes('pdf'))window.pdfLoadError=String(e.message||'PDF 组件加载失败');});
})();
