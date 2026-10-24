(function(root){
 'use strict';
 const imageRE=()=>/!\[([^\]\n]*)\]\(zhishi-media:([a-f0-9]{64})\)/g;
 const idOK=id=>typeof id==='string'&&/^[a-f0-9]{64}$/.test(id);
 const imageToken=image=>'!['+String(image.name||'图片').replace(/[\[\]\n]/g,' ').slice(0,300)+'](zhishi-media:'+image.id+')';
 function hasBody(note){return typeof note==='string'&&note.replace(imageRE(),'').replace(/[\s#*_`>~\-]/g,'').length>0;}
 function plain(note){return String(note||'').replace(imageRE(),'').replace(/^\s*```[^\n]*$/gm,'').replace(/^\s{0,3}(?:#{1,6}|>|[-*+] |\d+[.)、] )\s*/gm,'').replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,'$1 $2').replace(/[*_`~]/g,'').replace(/\s/g,'');}
 // Drafts are more permissive than committed documents: title / image / metadata alone is recoverable.
 function draftContent(r={}){return !!(String(r.title||'').trim()||hasBody(r.note)||imageRE().test(r.note||'')||r.images?.length||String(r.platform||'').trim()||String(r.url||'').trim()||r.tags?.some(t=>String(t).trim())||Number(r.durationMinutes)>0||(r.contentType&&r.contentType!=='article')||String(r.evidenceNote||'').trim());}
 function visibleDraft(d){return !!(d?.record&&(draftContent(d.record)||d.expectedVersion!=null||d.metadataOnly===true));}
 function draftExcerpt(note,limit=150){return String(note||'').replace(imageRE(),' ').replace(/^\s*```[^\n]*$/gm,' ').replace(/^\s{0,3}(?:#{1,6}\s+|>\s*|[-*+]\s+|\d+[.)、]\s+)/gm,'').replace(/!?\[([^\]]*)\]\([^)]*\)/g,'$1').replace(/[*_`~]/g,'').replace(/\s+/g,' ').trim().slice(0,limit);}
 function validateImages(images){if(images===undefined)return [];if(!Array.isArray(images)||images.length>100)throw Error('每条记录最多100张图片');const ids=new Set();for(const image of images){if(!image||!idOK(image.id)||ids.has(image.id)||typeof image.name!=='string'||image.name.length>300)throw Error('记录图片格式无效');ids.add(image.id);}return images;}
 function withImages(note,images=[]){let result=String(note||'');for(const image of validateImages(images)){if(![...result.matchAll(imageRE())].some(m=>m[2]===image.id))result+=(result?'\n\n':'')+imageToken(image);}return result;}
 function attach(record,image){validateImages([image]);const images=[...(record.images||[])];if(!images.some(i=>i.id===image.id))images.push(image);return {...record,images,note:withImages(record.note,images)};}
 function chunks(text,limit=2200){text=String(text);const out=[];let at=0;while(at<text.length){let end=Math.min(text.length,at+limit);if(end<text.length){const paragraph=text.lastIndexOf('\n',end);if(paragraph>at+limit/2)end=paragraph+1;const tokens=[...text.slice(at,end+500).matchAll(imageRE())];for(const token of tokens){const start=at+token.index,stop=start+token[0].length;if(start<end&&stop>end)end=start>at?start:stop;}if(end>at&&/[\uD800-\uDBFF]/.test(text[end-1])&&/[\uDC00-\uDFFF]/.test(text[end]))end--; }out.push(text.slice(at,end));at=end;}return out;}
 function guardMarkdown(source,markdown){if(typeof markdown!=='string'||!markdown.trim()||plain(source)!==plain(markdown))throw Error('AI排版改变或遗漏了正文，未采用；原文仍保留');const a=[...source.matchAll(imageRE())].map(x=>x[0]),b=[...markdown.matchAll(imageRE())].map(x=>x[0]);if(JSON.stringify(a)!==JSON.stringify(b))throw Error('AI改变了图片引用，未采用');return markdown;}
 function metadata(value){if(!value||typeof value.title!=='string'||!value.title.trim()||value.title.length>300||!Array.isArray(value.tags)||value.tags.length>20||value.tags.some(t=>typeof t!=='string'||!t.trim()||t.length>60))throw Error('AI返回的标题或标签格式无效');return {title:value.title.trim(),tags:[...new Set(value.tags.map(t=>t.trim()))]};}
 function openIntent(text){const clean=String(text||'').trim().replace(/[。！!？?\s]+$/,'');const m=/^(?:(?:请)?(?:帮我|给我)|我(?:想要|想|要))?(?:打开|新建|添加|写|记|记录)(?:一下|下)?(?:一条|一篇|一个|条|篇)?(?:新的)?(学习(?:记录|笔记)|工作(?:记录|日志)|灵感(?:创意)?|创意(?:记录)?|经验(?:感悟|记录)?)(?:的?(?:编辑器|界面))?$/.exec(clean);if(!m)return null;return /学习/.test(m[1])?'learning':/工作/.test(m[1])?'work_note':/经验/.test(m[1])?'experience':'idea';}
 function pageForOffset(offset,stride,count){return Math.max(0,Math.min(Math.max(0,count-1),Math.floor(Math.max(0,offset)/Math.max(1,stride))));}
 function occupiedPages(offsets,stride){return offsets.reduce((count,x)=>Number.isFinite(x)?Math.max(count,Math.floor(Math.max(0,x)/Math.max(1,stride))+1):count,1);}
 // Natural reading direction: swipe left -> next, right -> previous.
 function swipe(dx,dy){return Math.abs(dx)>=55&&Math.abs(dx)>1.6*Math.abs(dy)?dx<0?1:-1:0;}
 const api={imageRE,idOK,imageToken,hasBody,plain,draftContent,visibleDraft,draftExcerpt,validateImages,withImages,attach,chunks,guardMarkdown,metadata,openIntent,pageForOffset,occupiedPages,swipe};if(typeof module==='object'&&module.exports)module.exports=api;else root.RecordDocument=api;
})(typeof window!=='undefined'?window:globalThis);
