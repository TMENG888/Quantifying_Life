(function(root){
 'use strict';
 const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function inline(text){
  const tokens=[];const hold=html=>'\u0000'+(tokens.push(html)-1)+'\u0000';
  text=String(text).replace(/\\([\\`*_{}\[\]()#+.!|>~-])|`([^`\n]+)`/g,(_,literal,code)=>hold(literal!==undefined?esc(literal):'<code>'+esc(code)+'</code>'));
  text=text.replace(/\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g,(_,label,url)=>{try{const u=new URL(url);if(u.username||u.password)return label;return hold('<button class="inline-link" data-chat-url="'+esc(u.href)+'">'+esc(label)+'</button>');}catch{return label;}});
  text=esc(text).replace(/\*\*([^*\n]+)\*\*/g,'<strong>$1</strong>').replace(/__([^_\n]+)__/g,'<strong>$1</strong>').replace(/\*([^*\n]+)\*/g,'<em>$1</em>');
  return text.replace(/\u0000(\d+)\u0000/g,(_,i)=>tokens[+i]||'');
 }
 function opening(line){const m=/^\s*(`{3,}|~{3,})([^\r\n]*)$/.exec(line);return m&&!(m[1][0]==='`'&&m[2].includes('`'))?{mark:m[1][0],length:m[1].length,language:m[2].trim().split(/\s/)[0]||''}:null;}
 function closing(line,f){const m=/^\s*(`+|~+)\s*$/.exec(line);return !!m&&m[1][0]===f.mark&&m[1].length>=f.length;}
 // Source offsets and whitespace remain exact. A blank line inside a fence is
 // code, never a paragraph boundary (nor an attachment placeholder).
 function blocks(text){
  text=String(text??'');const lines=[...text.matchAll(/[^\n]*\n|[^\n]+$/g)],out=[];let i=0;
  const line=n=>lines[n][0].replace(/\n$/,'').replace(/\r$/,'');
  while(i<lines.length){const first=i,start=lines[i].index,f=opening(line(i));
   if(f){i++;const body=i;while(i<lines.length&&!closing(line(i),f))i++;const closed=i<lines.length,rawCode=lines.slice(body,i).map(x=>x[0]).join(''),code=closed?rawCode.replace(/\r?\n$/,''):rawCode;let suffix='';if(closed){suffix=lines[i][0].endsWith('\r\n')?'\r\n':lines[i][0].endsWith('\n')?'\n':'';i++;while(i<lines.length&&!line(i).trim())suffix+=lines[i++][0];}const end=i<lines.length?lines[i].index:text.length;out.push({type:'code',start,end,source:text.slice(start,end),code,language:f.language,closed,suffix});}
   else {i++;while(i<lines.length&&!opening(line(i))&&line(i-1).trim())i++;const end=i<lines.length?lines[i].index:text.length;out.push({type:'text',start,end,source:text.slice(start,end)});}
   if(i===first)i++;
  }return out;
 }
 function fenced(code,language=''){const runs=String(code).match(/`{3,}/g)||[],fence='`'.repeat(Math.max(3,...runs.map(x=>x.length+1)));return fence+String(language).replace(/[^\w+#.-]/g,'')+'\n'+code+'\n'+fence;}
 function render(text){
  const lines=String(text??'').replace(/\r/g,'').replace(/\u0000/g,'').split('\n'),out=[];let i=0;
  const marker=s=>/^\s*(?:[-*+]\s+|\d+[.)、]\s+)/.test(s);
  while(i<lines.length){const line=lines[i];if(!line.trim()){i++;continue;}
   const fence=opening(line);if(fence){const code=[];i++;while(i<lines.length&&!closing(lines[i],fence))code.push(lines[i++]);if(i<lines.length)i++;out.push('<pre data-code-language="'+esc(fence.language)+'"><code>'+esc(code.join('\n'))+'</code></pre>');continue;}
   const heading=/^\s*#{1,6}\s+(.+)$/.exec(line)||/^\s*【([^】]+)】\s*$/.exec(line);if(heading){out.push('<h3>'+inline(heading[1])+'</h3>');i++;continue;}
   if(/^-{3,}\s*$/.test(line)){out.push('<hr>');i++;continue;}
   if(line.includes('|')&&i+1<lines.length&&/^\s*\|?\s*:?-{3,}:?\s*\|/.test(lines[i+1])){const cells=s=>s.trim().replace(/^\|/,'').replace(/\|$/,'').split('|');const heads=cells(line);i+=2;const rows=[];while(i<lines.length&&lines[i].includes('|'))rows.push(cells(lines[i++]));out.push('<div class="table-scroll"><table><thead><tr>'+heads.map(s=>'<th>'+inline(s.trim())+'</th>').join('')+'</tr></thead><tbody>'+rows.map(r=>'<tr>'+r.map(s=>'<td>'+inline(s.trim())+'</td>').join('')+'</tr>').join('')+'</tbody></table></div>');continue;}
   if(marker(line)){const ordered=/^\s*\d/.test(line),tag=ordered?'ol':'ul',items=[];const start=ordered?+(line.match(/\d+/)||[1])[0]:1;while(i<lines.length&&marker(lines[i])&&/^\s*\d/.test(lines[i])===ordered){let item=lines[i++].replace(/^\s*(?:[-*+]\s+|\d+[.)、]\s+)/,'');while(i<lines.length&&/^\s{2,}\S/.test(lines[i])&&!marker(lines[i]))item+='\n'+lines[i++].trim();items.push('<li>'+inline(item).replace(/\n/g,'<br>')+'</li>');}out.push('<'+tag+(ordered?' start="'+start+'"':'')+'>'+items.join('')+'</'+tag+'>');continue;}
   if(/^\s*>\s?/.test(line)){const quote=[];while(i<lines.length&&/^\s*>/.test(lines[i]))quote.push(lines[i++].replace(/^\s*>\s?/,''));out.push('<blockquote>'+inline(quote.join('\n')).replace(/\n/g,'<br>')+'</blockquote>');continue;}
   const paragraph=[line];i++;while(i<lines.length&&lines[i].trim()&&!marker(lines[i])&&!opening(lines[i])&&!/^\s*(?:#|>|【)/.test(lines[i])&&!(i+1<lines.length&&lines[i].includes('|')&&lines[i+1].includes('---')))paragraph.push(lines[i++]);out.push('<p>'+inline(paragraph.join('\n')).replace(/\n/g,'<br>')+'</p>');
  }return out.join('');
 }
 const api={render,inline,blocks,fenced};if(typeof module==='object'&&module.exports)module.exports=api;else root.ChatMarkdown=api;
})(typeof window!=='undefined'?window:globalThis);
