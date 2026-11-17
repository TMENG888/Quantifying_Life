(function(root){
 'use strict';
 // Markdown remains the portable storage format. Untouched blocks retain their
 // exact original source (including unsupported syntax), not a lossy round trip.
 const originals=new WeakMap(),seams=new WeakMap(),block=/^(DIV|P|H[1-6]|UL|OL|LI|PRE|BLOCKQUOTE|FIGURE|TABLE)$/;
 function presentation(container,raw){
  if(/^\s{0,3}#{1,6}\s+/m.test(raw))for(const node of container.querySelectorAll('h3')){node.hidden=true;node.contentEditable='false';node.dataset.richStructure='true';}
  const walker=document.createTreeWalker(container,NodeFilter.SHOW_TEXT),nodes=[];let node;
  while(node=walker.nextNode())if(!node.parentElement.closest('code,pre,[hidden]')&&/\[[RC]\d+\]/.test(node.nodeValue))nodes.push(node);
  for(const text of nodes){const fragment=document.createDocumentFragment();let at=0;for(const match of text.nodeValue.matchAll(/\[[RC]\d+\]/g)){fragment.append(document.createTextNode(text.nodeValue.slice(at,match.index)));const hidden=document.createElement('span');hidden.hidden=true;hidden.contentEditable='false';hidden.dataset.richHidden=match[0];fragment.append(hidden);at=match.index+match[0].length;}fragment.append(document.createTextNode(text.nodeValue.slice(at)));text.replaceWith(fragment);}
  return container;
 }
 function render(note,imageHTML,readingPolicy=false){
  const fragment=document.createDocumentFragment();let previous=null;
  function append(el,source,prefix=''){originals.set(el,{source,html:el.innerHTML,prefix});seams.set(el,previous);previous=el;fragment.append(el);}
  function text(value,afterImage=false){
   // The first newline after a block image is a source separator, not an empty
   // paragraph. Keep it losslessly, without drawing an additional blank row.
   let prefix=afterImage&&value.startsWith('\n')?'\n':'';if(prefix)value=value.slice(1);
   if(!value&&prefix){const el=document.createElement('span');el.hidden=true;el.contentEditable='false';el.className='rich-source-separator';append(el,prefix);return;}
   for(const part of root.ChatMarkdown.blocks(value)){
    const raw=part.source;
    if(!raw)continue;const el=document.createElement('div');el.className='rich-block';
    el.innerHTML=part.type==='code'?root.DocumentCode.card(part.code,part.language,true):root.ChatMarkdown.render(raw);if(!el.childNodes.length)el.append(document.createElement('br'));
    if(readingPolicy&&part.type!=='code')presentation(el,raw);
    if(el.children.length&&Array.from(el.children).every(n=>n.hidden)){el.hidden=true;el.contentEditable='false';el.dataset.richStructure='true';}
    if(/^H[1-6]$/.test(el.firstElementChild?.tagName||''))el.classList.add('doc-heading');
    // Links are display text while editing; opening links is available in reader.
    el.querySelectorAll('[data-chat-url]').forEach(b=>{const a=document.createElement('a');a.dataset.richUrl=b.dataset.chatUrl;a.textContent=b.textContent;b.replaceWith(a);});
    append(el,prefix+raw,prefix);prefix='';
   }
  }
  let afterImage=false;for(const part of root.ChatMarkdown.blocks(String(note))){if(part.type==='code'){text(part.source,afterImage);afterImage=false;continue;}let at=0;for(const m of part.source.matchAll(root.RecordDocument.imageRE())){text(part.source.slice(at,m.index),afterImage);const box=document.createElement('div');box.innerHTML=imageHTML(m[2]);afterImage=!!box.firstChild;if(afterImage)append(box.firstChild,m[0]);else text(m[0]);at=m.index+m[0].length;}text(part.source.slice(at),afterImage);if(part.source.slice(at))afterImage=false;}return fragment;
 }
 function serialize(node,partial=false){
  if(node.nodeType===3)return node.nodeValue;if(node.nodeType!==1&&node.nodeType!==11)return '';
  if(node.dataset?.richHidden)return partial?'':node.dataset.richHidden;
  if(node.dataset?.token)return node.dataset.token;
  const old=originals.get(node);if(!partial&&old&&old.html===node.innerHTML)return old.source;
  if(node.classList?.contains('doc-code-card'))return root.ChatMarkdown.fenced(node.querySelector('code').textContent,node.dataset.codeLanguage||'');
  const tag=node.nodeName;if(tag==='BR')return '\n';
  let out='',previous=null;for(const child of node.childNodes){const value=serialize(child,partial);if(out&&value&&block.test(child.nodeName)&&seams.get(child)!==previous&&!out.endsWith('\n')&&!value.startsWith('\n'))out+='\n';out+=value;previous=child;}
  if(/^(STRONG|B)$/.test(tag))return out?'**'+out+'**':'';
  if(/^(EM|I)$/.test(tag))return out?'*'+out+'*':'';
  if(tag==='A'&&/^https?:\/\//.test(node.dataset.richUrl||node.getAttribute('href')||''))return '['+out+']('+(node.dataset.richUrl||node.getAttribute('href'))+')';
  if(tag==='PRE')return root.ChatMarkdown.fenced(node.textContent,node.dataset.codeLanguage||'')+'\n';
  if(tag==='CODE')return '`'+out+'`';
  if(/^H[1-6]$/.test(tag))return '#'.repeat(+tag[1])+' '+out.replace(/\n+$/,'')+'\n';
  if(tag==='LI'){const ordered=node.parentNode?.nodeName==='OL',items=Array.from(node.parentNode?.children||[]);return (ordered?((+node.parentNode.start||1)+items.indexOf(node))+'. ':'- ')+out.replace(/\n+$/,'')+'\n';}
  if(tag==='BLOCKQUOTE')return out.replace(/\n+$/,'').split('\n').map(l=>'> '+l).join('\n')+'\n';
  if(tag==='TR')return '| '+Array.from(node.children).map(c=>serialize(c,partial).trim()).join(' | ')+' |\n';
  if(tag==='THEAD')return out+'| '+Array.from(node.querySelectorAll('th')).map(()=> '---').join(' | ')+' |\n';
  if(/^(P|DIV)$/.test(tag)&&node.id!=='doc-body')return (old?.prefix||'')+out+(out&&!out.endsWith('\n')?'\n':'');
  return out;
 }
 function remember(node,source){originals.set(node,{source,html:node.innerHTML});}
 function updateCode(card,code){const host=card.closest('.rich-block'),old=originals.get(host);if(!host||!old)throw Error('代码位置已变化，请重新打开记录');const part=root.ChatMarkdown.blocks(old.source).find(p=>p.type==='code');if(!part)throw Error('未找到原代码块');const raw=root.ChatMarkdown.fenced(code,part.language)+(part.suffix||'');host.innerHTML=root.DocumentCode.card(code,part.language,true);remember(host,old.source.slice(0,part.start)+raw+old.source.slice(part.end));}
 function captureRewrite(range,body){return Array.from(body.querySelectorAll('.rich-block')).filter(node=>range.intersectsNode(node)).map(node=>({node,tail:/\n*$/.exec(originals.get(node)?.source||'')[0]}));}
 function finishRewrite(snapshot){for(const {node,tail} of snapshot)if(node.isConnected)remember(node,serialize(node,true).replace(/\n+$/,'')+tail);}
 root.DocumentRich={render,serialize,remember,updateCode,captureRewrite,finishRewrite,presentation};
})(window);
