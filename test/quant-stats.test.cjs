const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const Q=require('../app/src/main/assets/quant-stats.js');
const note='我修改了重试机制，并应用在项目中。目标80%的回归测试还在运行，没有结论。';
function row(id,primary='improve',actor='self',status='completed',kind='method_change'){
 const quote=note.slice(0,18);return {id,type:'learning',title:'合成记录',date:'2026-10-29',updatedAt:10,note,durationMinutes:25,quantLabels:{model:'qwen3.7-flash',schemaVersion:1,sourceVersion:10,complete:true,primary,evidence:[{kind,actor,status,claim:'方法调整',quote,start:0,end:quote.length}]}};
}
const data=records=>({module:'learning',period:{start:'',end:''},items:records.map(record=>({record,minutes:record.durationMinutes||0}))});
test('classification coverage uses content records and excludes shifts, stale, incomplete and wrong-model results',()=>{
 const valid=row('a'),stale={...row('s'),updatedAt:11},partial=row('p');partial.quantLabels.complete=false;
 const wrong=row('wrong');wrong.quantLabels.model='deepseek-flash';const shift={id:'w',type:'work',note:''};
 const stats=Q.summarize(data([valid,stale,partial,wrong,shift]));assert.equal(stats.total,4);assert.equal(stats.done,1);assert.equal(stats.pending,3);assert.equal(stats.buckets[0].percent,100);
});
test('planned/ongoing tests and source quotations never count as personal measured outcomes',()=>{
 const planned=row('plan','plan','self','planned','verification'),ongoing=row('run','verify','self','ongoing','verification'),source=row('source','reference','source','measured','verification'),measured=row('result','verify','self','measured','verification');
 const stats=Q.summarize(data([planned,ongoing,source,measured]));assert.deepEqual(stats.facets.find(f=>f.key==='verification').ids,['result']);assert.deepEqual(stats.facets.find(f=>f.key==='plan').ids,['plan']);
});
test('facets count notes once, can overlap, and do not manufacture a composite cognition score',()=>{
 const r=row('a');r.quantLabels.evidence.push({...r.quantLabels.evidence[0],kind:'application'},r.quantLabels.evidence[0]);const before=JSON.stringify(r),s=Q.summarize(data([r]));assert.equal(s.facets.find(f=>f.key==='change').ids.length,1);assert.equal(s.facets.find(f=>f.key==='practice').ids.length,1);assert.equal(s.done,1);assert.equal(JSON.stringify(r),before);assert.equal(s.score,undefined);
});
test('ring percentages conserve the labeled set; pending and time do not masquerade as zero evidence',()=>{
 const rows=[row('a','understand'),row('b','apply'),row('c','plan'),{...row('pending'),updatedAt:20}];const s=Q.summarize(data(rows));assert.equal(s.buckets.reduce((n,b)=>n+b.value,0),3);assert.equal(s.buckets.reduce((n,b)=>n+b.percent,0),100);assert.equal(s.pending,1);
});
test('eight kinds fit six ring segments without losing original records or drilldown membership',()=>{
 const s=Q.summarize(data(Object.keys(Q.labels).map((k,i)=>row('x'+i,k))));assert.equal(s.buckets.length,6);assert.equal(s.buckets.at(-1).members.length,3);assert.equal(s.buckets.reduce((n,b)=>n+b.value,0),8);assert.equal(s.buckets.reduce((n,b)=>n+b.percent,0),100);
});
test('invalid evidence coordinates and invented quote make labels unusable',()=>{
 const wrong=row('x');wrong.quantLabels.evidence[0].quote='不存在的原文';assert.equal(Q.current(wrong),false);const r=row('y');r.quantLabels.evidence[0].start=-1;assert.equal(Q.current(r),false);assert.equal(Q.summarize(data([])).buckets.length,0);
});

test('phone uses encrypted illustration key and background job; bridge never accepts an arbitrary model override',()=>{
 const java=fs.readFileSync(require.resolve('../app/src/main/java/com/insight/quantlife/QwenTransport.java'),'utf8');assert.match(java,/imageGeneration/);assert.match(java,/enable_thinking/);assert.doesNotMatch(java,/sk-[\w.-]{8}/);
 const main=fs.readFileSync(require.resolve('../app/src/main/java/com/insight/quantlife/MainActivity.java'),'utf8');assert.match(main,/case "quantLabelStatus"/);assert.match(main,/QwenLabelingService.schedule\(MainActivity.this\)/);
 const manifest=fs.readFileSync(require.resolve('../app/src/main/AndroidManifest.xml'),'utf8');assert.match(manifest,/QwenLabelingService.*BIND_JOB_SERVICE.*exported="false"/);
});
