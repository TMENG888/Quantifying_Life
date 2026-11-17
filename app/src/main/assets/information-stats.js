(function(root){
 'use strict';
 function prose(s){return String(s||'').replace(/!\[[^\]\n]*\]\([^\n)]*\)/g,'').replace(/^\s*(?:`{3,}|~{3,})[^\n]*$/gm,'').replace(/\[([^\]\n]+)\]\([^\n)]*\)/g,'$1').replace(/^\s{0,3}(?:#{1,6}\s+|>\s*|[-+*]\s+|\d+[.)]\s+)/gm,'').replace(/https?:\/\/[^\s<>]+/g,'').replace(/\\([!"#$%&'()*+,\-./:;<=>?@[\]\\^_`{|}~])/g,'$1').replace(/[*_`~]/g,'');}
 function plain(s){let fence='',out='';for(const line of String(s||'').split('\n')){const m=/^[ \t]{0,3}(`{3,}|~{3,})(.*)$/s.exec(line);if(m){if(!fence){fence=m[1];continue;}if(m[1][0]===fence[0]&&m[1].length>=fence.length&&!m[2].replace(/[\s\u0085\u001c-\u001f]/g,'')){fence='';continue;}}if(fence)out+=line;else{const re=/`+([^`\n]+)`+/g;let at=0,m;while((m=re.exec(line))){out+=prose(line.slice(at,m.index))+m[1];at=re.lastIndex;}out+=prose(line.slice(at));}out+='\n';}return out;}
 const characters=s=>Array.from(plain(s)).filter(c=>!/[\s\u001c-\u001f\u0085\u2007\u202f]/u.test(c)).length;
 const eligible=r=>['learning','work_note','idea','experience'].includes(r.type)&&characters(r.note)>0;
 function current(r){const q=r.informationUnits;return eligible(r)&&q?.schemaVersion===2&&q.provider==='assistant-deepseek'&&q.unitRule==='semantic-propositions-v2'&&typeof q.model==='string'&&q.model.toLowerCase().includes('deepseek')&&q.complete===true&&q.sourceVersion===r.updatedAt&&q.characters===characters(r.note)&&Array.isArray(q.units)&&q.count===q.units.length&&q.units.every(u=>typeof u.text==='string'&&u.text.trim()&&Array.isArray(u.sources)&&u.sources.length&&u.sources.every(s=>Number.isInteger(s.start)&&Number.isInteger(s.end)&&s.start>=0&&s.end>s.start&&r.note.slice(s.start,s.end)===s.quote));}
 function date(s){return new Date(s+'T12:00:00Z');}
 function day(d){return d.toISOString().slice(0,10);}
 function key(s,grain){if(grain==='month')return s.slice(0,7);if(grain==='week'){const d=date(s);d.setUTCDate(d.getUTCDate()-(d.getUTCDay()+6)%7);return day(d);}return s;}
 function bounds(k,grain){if(grain==='month'){const d=date(k+'-01');d.setUTCMonth(d.getUTCMonth()+1,0);return {start:k+'-01',end:day(d)};}if(grain==='week'){const d=date(k);d.setUTCDate(d.getUTCDate()+6);return {start:k,end:day(d)};}return {start:k,end:k};}
 function roll(a){let units=0,chars=0,timedUnits=0,minutes=0,timed=0,done=0;for(const r of a){if(!current(r))continue;const q=r.informationUnits;done++;units+=q.count;chars+=q.characters;if(r.type==='learning'&&Number.isFinite(Number(r.durationMinutes))&&Number(r.durationMinutes)>0){timed++;timedUnits+=q.count;minutes+=Number(r.durationMinutes);}}
  return {total:a.length,done,pending:a.length-done,units:done?units:null,characters:done?chars:null,density:done&&chars>0?units/chars*1000:null,timed,timedUnits:timed?timedUnits:null,minutes:timed?minutes:null,efficiency:timed&&minutes>0?timedUnits/minutes*60:null,cost:timedUnits>0?minutes/timedUnits:null};
 }
 function summarize(data,grain='day'){
  const records=data.items.map(x=>x.record).filter(eligible),map=new Map();for(const r of records){if(!/^\d{4}-\d{2}-\d{2}$/.test(r.date))continue;const k=key(r.date,grain);if(!map.has(k))map.set(k,[]);map.get(k).push(r);}
  const keys=[...map.keys()].sort();let first=data.period.start?key(data.period.start,grain):keys[0],last=data.period.end?key(data.period.end,grain):keys.at(-1);const buckets=[];
  if(first&&last){let k=first;for(let i=0;i<50000&&k<=last;i++){const rows=map.get(k)||[],span=bounds(k,grain);if(data.period.start&&span.start<data.period.start)span.start=data.period.start;if(data.period.end&&span.end>data.period.end)span.end=data.period.end;buckets.push({key:k,...span,...roll(rows),ids:rows.map(r=>r.id)});const d=date(grain==='month'?k+'-01':k);if(grain==='month')d.setUTCMonth(d.getUTCMonth()+1);else d.setUTCDate(d.getUTCDate()+(grain==='week'?7:1));k=grain==='month'?day(d).slice(0,7):day(d);}}
  return {...roll(records),records,buckets,grain};
 }
 const api={plain,characters,eligible,current,key,bounds,roll,summarize};if(typeof module==='object'&&module.exports)module.exports=api;else root.InformationStats=api;
})(typeof window!=='undefined'?window:globalThis);
