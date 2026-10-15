const {test}=require('node:test'),assert=require('node:assert/strict');
const D=require('../app/src/main/assets/domain.js'),L=require('../app/src/main/assets/links.js'),A=require('../app/src/main/assets/agent-actions.js');
const url='https://weixin.qq.com/sph/SyntheticLearning',note='学习了这个视频内容，有关REA，为最近的一个逆向Web，app的开源项目，当中封装了一些逆向的工具，skill指导，以及过程的记录机制，目前使用它的核心目的是了解软件，然后理解软件的功能。';
const source={url,status:'unavailable',title:'',platform:'微信视频号',contentType:'video',excerpt:'不可信网页内容'};
const now=new Date('2026-10-15T15:00:00'),text='而且刚刚那个视频我学习了15分钟';
const learn={id:'learning-integrity',type:'learning',date:'2026-10-15',title:'REA工具学习',note:note.repeat(2200),url,platform:'微信视频号',contentType:'video',tags:['REA'],durationMinutes:0,updatedAt:5,wikiTopics:['REA'],source:'ai'};
test('screenshot narrative retained verbatim and unavailable title generated locally',()=>{
 const input=url+'\n'+note,r=L.noteRecords(input,[source],D.localDate(now))[0];
 assert.equal(r.note,note);assert.equal(r.titleOrigin,'note');assert.equal(r.contentType,'video');assert.equal(r.platform,'微信视频号');assert.ok(r.title.includes('REA'));
 const [guarded]=L.guardRecords([{...r,note:'模型缩写',title:'视频号视频（未获取到原标题）'}],[source],input);
 assert.equal(guarded.note,note);assert.equal(guarded.titleOrigin,'note');assert.ok(!D.missingLearningTitle(guarded.title));
});
test('domain derives missing or placeholder title before validating',()=>{
 for(const title of ['', '视频号视频（未获取到原标题）','（待补原标题）']){const r=D.aiRecords([{type:'learning',date:'2026-10-15',title,note,durationMinutes:0}])[0];assert.equal(r.titleOrigin,'note');assert.ok(r.title.includes('REA'));assert.equal(r.note,note);}
});
test('real same-link original title supersedes derived title, user title wins',()=>{
 const [r]=L.noteRecords(url+'\n'+note,[source],D.localDate(now)),s={...source,status:'metadata',title:'可靠原标题'};
 assert.equal(L.applySources([r],[s])[0].title,s.title);
 assert.equal(L.applySources([{...r,title:'自定标题',titleOrigin:'user'}],[s])[0].title,'自定标题');
});
test('web summaries and bare links never become user notes or guessed original title',()=>{
 assert.deepEqual(L.noteRecords('请总结 '+url,[source],D.localDate(now)),[]);assert.deepEqual(L.noteRecords(url,[source],D.localDate(now)),[]);
 assert.deepEqual(L.noteRecords(url+'\nhttps://example.com/other\n'+note,[source],D.localDate(now)),[]);
 assert.deepEqual(L.guardRecords([{...learn,title:'虚构标题',note:'网页笔记'}],[source],'记一下 '+url),[]);
});
test('exact followup is duration-only even with corrupt full model patch and 160k+ note',()=>{
 assert.deepEqual(A.learningTimeRequest(text),{minutes:15,add:false});
 const [a]=A.prepare([{kind:'update_record',recordId:learn.id,patch:{durationMinutes:999,note:'',title:'无标题',url:'',tags:[],platform:'错误平台'},appendNote:'捏造内容'}],[learn],[learn.id],now,{text});
 assert.deepEqual(A.diff(a.before,a.after),{durationMinutes:15});assert.equal(a.after.note,learn.note);assert.equal(learn.durationMinutes,0);
});
test('empty fields are ignored, clear requires explicitly named content',()=>{
 const [a]=A.prepare([{kind:'update_record',recordId:learn.id,patch:{note:'',title:'',url:'',tags:[],durationMinutes:35}}],[learn],[learn.id],now,{text:'修改学习时长为35分钟'});
 assert.deepEqual(A.diff(a.before,a.after),{durationMinutes:35});
 const [b]=A.prepare([{kind:'update_record',recordId:learn.id,patch:{note:''}}],[learn],[learn.id],now,{text:'清空笔记'});
 assert.equal(b.after.note,'');assert.equal(learn.note.length>160000,true);
});
test('append and explicit replacement preserve clone semantics',()=>{
 const [a]=A.prepare([{kind:'update_record',recordId:learn.id,appendNote:'新的发现'}],[learn],[learn.id],now,{text:'追加笔记'});
 assert.equal(a.after.note,learn.note+'\n\n新的发现');
 const [b]=A.prepare([{kind:'update_record',recordId:learn.id,patch:{note:'用户明确替换'}}],[learn],[learn.id],now,{text:'替换笔记为用户明确替换'});
 assert.equal(b.after.note,'用户明确替换');assert.equal(learn.note.startsWith(note),true);
});
test('time parsing distinguishes total/add and rejects questions, negation and ambiguity',()=>{
 assert.deepEqual(A.learningTimeRequest('刚才那篇文章学了半小时'),{minutes:30,add:false});
 assert.deepEqual(A.learningTimeRequest('那个视频又学了15分钟'),{minutes:15,add:true});
 for(const t of ['刚才那个视频学了15分钟吗？','刚才那个视频不是学了15分钟','那个视频明天学15分钟','那个视频学了15分钟又学20分钟','今天学习15分钟','那个视频学了15分钟，笔记改成ABC'])assert.equal(A.learningTimeRequest(t),null,t);
});
test('target comes from same-session actual receipt, not global latest or model suggestion',()=>{
 const receipt={role:'assistant',recordEvent:{kind:'saved',recordId:learn.id}};
 assert.equal(A.learningTarget([receipt,{role:'user',text}], [learn]).id,learn.id);
 assert.equal(A.learningTarget([{role:'assistant',text:'已保存',recordId:learn.id}], [learn]),null);
 assert.equal(A.learningTarget([], [learn]),null);assert.equal(A.learningTarget([receipt], []),null);
 assert.equal(A.learningTarget([receipt,{role:'assistant',contextText:'待确认候选（尚未入库，仅供核对）'}],[learn]),null);
 assert.equal(A.learningTarget([{role:'assistant',contextText:'应用实际执行回执（用户确认后成功执行）：'+JSON.stringify({kind:'saved',recordId:learn.id})}],[learn]).id,learn.id);
});
test('duration-only instruction blocks nonempty accidental replacements too',()=>{
 const [a]=A.prepare([{kind:'update_record',recordId:learn.id,patch:{durationMinutes:35,note:'模型缩写',title:'无关标题'}}],[learn],[learn.id],now,{text:'把REA学习时长改为35分钟，笔记和其他信息不变'});
 assert.deepEqual(A.diff(a.before,a.after),{durationMinutes:35});
 const [b]=A.prepare([{kind:'update_record',recordId:learn.id,patch:{durationMinutes:40,note:''}}],[learn],[learn.id],now,{text:'改为40分钟，不要清空笔记'});
 assert.equal(b.after.note,learn.note);
 assert.equal(A.learningTarget([{role:'assistant',recordEvent:{kind:'saved',recordId:learn.id}}],[{...learn,contentType:'article'}],text),null);
});
