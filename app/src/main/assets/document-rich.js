(function(root){
 'use strict';
 // Markdown remains the portable storage format. Untouched blocks retain their
 // exact original source (including unsupported syntax), not a lossy round trip.
 const originals=new WeakMap(),seams=new WeakMap(),block=/^(DIV|P|H[1-6]|UL|OL|LI|PRE|BLOCKQUOTE|FIGURE|TABLE)$/;
 function render(note,imageHTML){
  const fragment=document.createDocumentFragment();let previous=null;
  function append(el,source,prefix=''){originals.set(el,{source,html:el.innerHTML,prefix});seams.set(el,previous);previous=el;fragment.append(el);}
  function text(value,afterImage=false){
   // The first newline after a block image is a source separator, not an empty
   // paragraph. Keep it losslessly, without drawing an additional blank row.
   let prefix=afterImage&&value.startsWith('\n')?'\n':'';if(prefix)value=value.slice(1);
   if(!value&&prefix){const el=document.createElement('span');el.hidden=true;el.contentEditable='false';el.className='rich-source-separator';append(el,prefix);return;}
   const lines=value.split('\n');let i=0;
   while(i<lines.length){let start=i,fence=false;
    if(/^\s*```/.test(lines[i])){fence=true;i++;while(i<lines.length&&!/^\s*```/.test(lines[i]))i++;if(i<lines.length)i++;}
    else {while(i<lines.length&&lines[i].trim())i++;}
    if(i===start)i++;while(i<lines.length&&!lines[i].trim())i++;
    const raw=lines.slice(start,i).join('\n')+(i<lines.length?'\n':'');
    if(!raw)continue;const el=document.createElement('div');el.className='rich-block';
    el.innerHTML=root.ChatMarkdown.render(raw);if(!el.childNodes.length)el.append(document.createElement('br'));
    if(/^H[1-6]$/.test(el.firstElementChild?.tagName||''))el.classList.add('doc-heading');
    // Links are display text while editing; opening links is available in reader.
    el.querySelectorAll('[data-chat-url]').forEach(b=>{const a=document.createElement('a');a.dataset.richUrl=b.dataset.chatUrl;a.textContent=b.textContent;b.replaceWith(a);});
    append(el,prefix+raw,prefix);prefix='';
   }
  }
  let at=0,afterImage=false;for(const m of String(note).matchAll(root.RecordDocument.imageRE())){text(note.slice(at,m.index),afterImage);const box=document.createElement('div');box.innerHTML=imageHTML(m[2]);afterImage=!!box.firstChild;if(afterImage)append(box.firstChild,m[0]);else text(m[0]);at=m.index+m[0].length;}text(note.slice(at),afterImage);return fragment;
 }
 function serialize(node,partial=false){
  if(node.nodeType===3)return node.nodeValue;if(node.nodeType!==1&&node.nodeType!==11)return '';
  if(node.dataset?.token)return node.dataset.token;
  const old=originals.get(node);if(!partial&&old&&old.html===node.innerHTML)return old.source;
  const tag=node.nodeName;if(tag==='BR')return '\n';
  let out='',previous=null;for(const child of node.childNodes){const value=serialize(child,partial);if(out&&value&&block.test(child.nodeName)&&seams.get(child)!==previous&&!out.endsWith('\n')&&!value.startsWith('\n'))out+='\n';out+=value;previous=child;}
  if(/^(STRONG|B)$/.test(tag))return out?'**'+out+'**':'';
  if(/^(EM|I)$/.test(tag))return out?'*'+out+'*':'';
  if(tag==='A'&&/^https?:\/\//.test(node.dataset.richUrl||node.getAttribute('href')||''))return '['+out+']('+(node.dataset.richUrl||node.getAttribute('href'))+')';
  if(tag==='PRE')return '```\n'+node.textContent.replace(/\n$/,'')+'\n```\n';
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
 root.DocumentRich={render,serialize,remember};
})(window);
