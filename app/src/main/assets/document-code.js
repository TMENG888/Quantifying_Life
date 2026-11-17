(function(root){
 'use strict';
 const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function card(code,language='',editing=false){return `<div class="doc-code-card" contenteditable="false" data-code-language="${esc(language)}"><header class="doc-code-head"><span>${esc(language||'代码')}<small>${String(code).split('\n').length} 行</small></span><button type="button" data-code-open>${editing?'编辑代码':'全屏查看'}</button></header><pre tabindex="0" aria-label="${esc(language||'代码')}，可滚动查看"><code>${esc(code)}</code></pre></div>`;}
 function bind(container,env,editing=false,onchange=()=>{}){container.querySelectorAll('.doc-code-card').forEach(card=>{card.querySelector('[data-code-open]').onclick=()=>open(card,env,editing,onchange);});}
 function close(){const view=document.getElementById('doc-code-viewer');if(!view)return false;view._close();return true;}
 function open(card,env,editing,onchange){
  if(document.getElementById('doc-code-viewer'))return;const previous=document.activeElement;document.activeElement?.blur();getSelection()?.removeAllRanges();const code=card.querySelector('code').textContent,language=card.dataset.codeLanguage||'',view=document.createElement('aside');
  view.id='doc-code-viewer';view.className='doc-code-viewer';view.setAttribute('role','dialog');view.setAttribute('aria-modal','true');view.setAttribute('aria-label',editing?'编辑代码':'代码阅读');
  view.innerHTML=`<header><button type="button" data-code-close aria-label="返回记录">‹</button><strong>${esc(language||'代码')}</strong>${editing?'':'<button type="button" data-code-wrap aria-pressed="false">换行</button>'}<button type="button" data-code-copy>复制</button>${editing?'<button type="button" data-code-save>完成</button>':''}</header>${editing?'<label class="doc-code-edit-label" for="doc-code-input">代码内容</label><textarea id="doc-code-input" spellcheck="false" autocapitalize="off" autocomplete="off" wrap="off"></textarea>':`<pre tabindex="0"><code>${esc(code)}</code></pre>`}<p class="doc-code-status" role="status"></p>`;
  const shell=document.querySelector('.document-shell'),under=Array.from(shell.children);shell.append(view);under.forEach(el=>{el._codeInert=el.inert;el.inert=true;});
  const input=view.querySelector('textarea'),status=view.querySelector('.doc-code-status');if(input)input.value=code;
  const cleanup=()=>{document.removeEventListener('keydown',keys);under.forEach(el=>{el.inert=!!el._codeInert;delete el._codeInert;});view.remove();if(previous?.isConnected)previous.focus({preventScroll:true});};
  view._close=cleanup;
  const dismiss=async()=>{if(input&&input.value!==code&&!await env.confirm('放弃代码修改？','尚未完成的代码修改将不保留。','放弃'))return;cleanup();};
  view._close=dismiss;view.querySelector('[data-code-close]').onclick=dismiss;
  view.querySelector('[data-code-copy]').onclick=()=>env.safe(async()=>{await env.api('copyText',{text:input?input.value:code});status.textContent='代码已复制';});
  const wrap=view.querySelector('[data-code-wrap]');if(wrap)wrap.onclick=()=>{const enabled=wrap.getAttribute('aria-pressed')!=='true';wrap.setAttribute('aria-pressed',String(enabled));view.classList.toggle('is-wrapped',enabled);};
  if(input)view.querySelector('[data-code-save]').onclick=()=>env.safe(async()=>{if(input.value!==code){root.DocumentRich.updateCode(card,input.value);onchange();}cleanup();});
  function keys(event){if(event.key==='Escape'){event.preventDefault();event.stopPropagation();dismiss();}else if(event.key==='Tab'){const nodes=Array.from(view.querySelectorAll('button,textarea,pre[tabindex]')),first=nodes[0],last=nodes[nodes.length-1];if(event.shiftKey&&document.activeElement===first){last.focus();event.preventDefault();}else if(!event.shiftKey&&document.activeElement===last){first.focus();event.preventDefault();}}}
  document.addEventListener('keydown',keys);view.querySelector('[data-code-close]').focus({preventScroll:true});
 }
 root.DocumentCode={card,bind,close};
})(window);
