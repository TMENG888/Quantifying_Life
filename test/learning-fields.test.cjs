const {test}=require('node:test'),assert=require('node:assert/strict');
const D=require('../app/src/main/assets/domain.js'),L=require('../app/src/main/assets/links.js');
const url='https://weixin.qq.com/sph/SyntheticFields';
const note='学习了这个视频内容，有关rea，为最近的一个逆向Web，app的开源项目，当中封装了一些示例工具，skill指导，以及过程的记录机制，目前使用它的核心目的是理解合成样例，然后复现出样例的功能，因为其是通过公开测试的方式实现，因此可以说在测试中，可以分析一些公开示例的实现机制';
const input=url+'\n'+note+'，学了15分钟';
const source={url,status:'metadata',title:'视频号',platform:'微信视频号',contentType:'video'};
test('reported same-turn input shape splits personal duration from verbatim note',()=>{
 assert.deepEqual(D.splitLearningInput(note+'，学了15分钟'),{noteText:note,durationMinutes:15});
 assert.equal(L.annotations(input),note);
 const fallback=L.noteRecords(input,[source],'2026-10-15')[0];
 assert.equal(fallback.note,note);assert.equal(fallback.durationMinutes,15);
 const model={...fallback,title:'视频号',titleOrigin:'web',note:note+'，学了15分钟',durationMinutes:999};
 for(const r of [fallback,...L.guardRecords([model],[source],input)]){
  assert.equal(r.note,note);assert.equal(r.durationMinutes,15);assert.equal(r.titleOrigin,'note');
  assert.ok(r.title.includes('rea'));assert.ok(!D.missingLearningTitle(r.title));
  assert.equal(L.applySources([r],[source])[0].title,r.title);
 }
});
test('platform/home/login titles never count as content metadata',()=>{
 for(const title of ['视频号','微信视频号','视频号视频','B站','YouTube','首页','登录','打开 App','微信公众号']){
  const s={...source,title};assert.equal(L.reliable(s),false,title);
  assert.deepEqual(L.bareRecords([s],'2026-10-15'),[]);
  assert.deepEqual(L.guardRecords([{type:'learning',url,title,note:'',durationMinutes:0}],[s],url),[]);
 }
 assert.equal(L.reliable({...source,title:'视频号里的REA工具介绍'}),true);
 assert.equal(L.reliable({...source,title:'其他站点',platform:'其他站点'}),false);
});
test('duration parser handles edges, labels, decimals and Chinese counts',()=>{
 for(const [text,minutes,expected] of [
  ['学了35分钟，我的笔记：知识正文。',35,'我的笔记：知识正文。'],
  ['知识正文，学习时长：15分钟，更多笔记。',15,'知识正文，更多笔记。'],
  ['知识正文，学习了半小时',30,'知识正文'],
  ['知识正文，学了1小时15分钟',75,'知识正文'],
  ['知识正文，学习时长：一小时十五分钟',75,'知识正文'],
  ['知识正文，学了1.5小时',90,'知识正文'],
  ['知识正文\n本次学习用时：0分钟',0,'知识正文']]){
  assert.deepEqual(D.splitLearningInput(text),{noteText:expected,durationMinutes:minutes},text);
 }
});
test('teaching content, quotations, plans and ambiguous personal times stay untouched',()=>{
 for(const text of ['番茄钟每25分钟休息5分钟','视频建议每天学习15分钟','作者说“学了15分钟”','知识正文，明天学15分钟','知识正文，我不是学了15分钟','学了15分钟，笔记正文，学了20分钟','知识正文，学了15分钟吗？','知识正文，学了15分钟就能精通是错误的']){
  assert.deepEqual(D.splitLearningInput(text),{noteText:text,durationMinutes:null},text);
 }
});
test('metadata removal preserves paragraph breaks, spaces and original punctuation',()=>{
 const body='第一段：教学内容。\n\n第二段  保留空格，细节不变。';
 assert.deepEqual(D.splitLearningInput(body+'\n学了15分钟'),{noteText:body,durationMinutes:15});
 assert.deepEqual(D.splitLearningInput('学习时长：15分钟\n'+body),{noteText:body,durationMinutes:15});
});
test('nonlink AI record also separates metadata before title fallback',()=>{
 const [r]=D.aiRecords([{type:'learning',date:'2026-10-15',title:'视频号',note:note+'，学了15分钟',durationMinutes:0}]);
 assert.equal(r.durationMinutes,15);assert.equal(r.note,note);assert.equal(r.titleOrigin,'note');
});
test('genuine web title and explicit custom title remain preferred',()=>{
 const s={...source,title:'REA工具介绍'};
 const [r]=L.noteRecords(input,[s],'2026-10-15');assert.equal(L.applySources([r],[s])[0].title,s.title);
 const [custom]=L.guardRecords([{...r,title:'我的REA实践',titleOrigin:'user'}],[s],input+'\n标题：我的REA实践');
 assert.equal(custom.title,'我的REA实践');assert.equal(custom.titleOrigin,'user');
});
