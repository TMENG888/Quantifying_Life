(function(root){
 'use strict';
 const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 function inline(text){
  const tokens=[];const hold=html=>'\u0000'+(tokens.push(html)-1)+'\u0000';
  text=String(text).replace(/`([^`\n]+)`/g,(_,s)=>hold('<code>'+esc(s)+'</code>'));
  text=text.replace(/\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g,(_,label,url)=>{try{const u=new URL(url);if(u.username||u.password)return label;return hold('<button class="inline-link" data-chat-url="'+esc(u.href)+'">'+esc(label)+'</button>');}catch{return label;}});
  text=esc(text).replace(/\*\*([^*\n]+)\*\*/g,'<strong>$1</strong>').replace(/__([^_\n]+)__/g,'<strong>$1</strong>').replace(/\*([^*\n]+)\*/g,'<em>$1</em>');
  return text.replace(/\u0000(\d+)\u0000/g,(_,i)=>tokens[+i]||'');
 }
 function render(text){
  const lines=String(text??'').replace(/\r/g,'').replace(/\u0000/g,'').split('\n'),out=[];let i=0;
  const marker=s=>/^\s*(?:[-*+]\s+|\d+[.)、]\s+)/.test(s);
  while(i<lines.length){const line=lines[i];if(!line.trim()){i++;continue;}
   if(/^\s*```/.test(line)){const code=[];i++;while(i<lines.length&&!/^\s*```/.test(lines[i]))code.push(lines[i++]);if(i<lines.length)i++;out.push('<pre><code>'+esc(code.join('\n'))+'</code></pre>');continue;}
   const heading=/^\s*#{1,6}\s+(.+)$/.exec(line)||/^\s*【([^】]+)】\s*$/.exec(line);if(heading){out.push('<h3>'+inline(heading[1])+'</h3>');i++;continue;}
   if(/^-{3,}\s*$/.test(line)){out.push('<hr>');i++;continue;}
   if(line.includes('|')&&i+1<lines.length&&/^\s*\|?\s*:?-{3,}:?\s*\|/.test(lines[i+1])){const cells=s=>s.trim().replace(/^\|/,'').replace(/\|$/,'').split('|');const heads=cells(line);i+=2;const rows=[];while(i<lines.length&&lines[i].includes('|'))rows.push(cells(lines[i++]));out.push('<div class="table-scroll"><table><thead><tr>'+heads.map(s=>'<th>'+inline(s.trim())+'</th>').join('')+'</tr></thead><tbody>'+rows.map(r=>'<tr>'+r.map(s=>'<td>'+inline(s.trim())+'</td>').join('')+'</tr>').join('')+'</tbody></table></div>');continue;}
   if(marker(line)){const ordered=/^\s*\d/.test(line),tag=ordered?'ol':'ul',items=[];const start=ordered?+(line.match(/\d+/)||[1])[0]:1;while(i<lines.length&&marker(lines[i])&&/^\s*\d/.test(lines[i])===ordered){let item=lines[i++].replace(/^\s*(?:[-*+]\s+|\d+[.)、]\s+)/,'');while(i<lines.length&&/^\s{2,}\S/.test(lines[i])&&!marker(lines[i]))item+='\n'+lines[i++].trim();items.push('<li>'+inline(item).replace(/\n/g,'<br>')+'</li>');}out.push('<'+tag+(ordered?' start="'+start+'"':'')+'>'+items.join('')+'</'+tag+'>');continue;}
   if(/^\s*>\s?/.test(line)){const quote=[];while(i<lines.length&&/^\s*>/.test(lines[i]))quote.push(lines[i++].replace(/^\s*>\s?/,''));out.push('<blockquote>'+inline(quote.join('\n')).replace(/\n/g,'<br>')+'</blockquote>');continue;}
   const paragraph=[line];i++;while(i<lines.length&&lines[i].trim()&&!marker(lines[i])&&!/^\s*(?:#|```|>|【)/.test(lines[i])&&!(i+1<lines.length&&lines[i].includes('|')&&lines[i+1].includes('---')))paragraph.push(lines[i++]);out.push('<p>'+inline(paragraph.join('\n')).replace(/\n/g,'<br>')+'</p>');
  }return out.join('');
 }
 const api={render,inline};if(typeof module==='object'&&module.exports)module.exports=api;else root.ChatMarkdown=api;
})(typeof window!=='undefined'?window:globalThis);
