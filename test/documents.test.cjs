const test=require('node:test'),assert=require('node:assert/strict'),R=require('../app/src/main/assets/document-domain.js');
const id='a'.repeat(64),image={id,name:'hello [image]',width:20,height:20};
test('draft policy excludes default-only blanks but preserves recoverable input',()=>{
 const blank={type:'idea',title:'',note:'',date:'2026-10-19',occurredAt:'2026-10-19T10:10:59',contentType:'article',tags:[],durationMinutes:0};
 for(const note of ['', '  \n', '# **  ', '> \n---'])assert.equal(R.visibleDraft({record:{...blank,note},expectedVersion:null}),false);
 for(const extra of [{title:'标题'},{note:'未命名但有正文'},{note:R.imageToken(image)},{images:[image]},{tags:['标签']},{durationMinutes:20},{platform:'公众号'},{url:'https://example.com'},{contentType:'book'}])assert.equal(R.visibleDraft({record:{...blank,...extra}}),true,JSON.stringify(extra));
 assert.equal(R.visibleDraft({record:blank,expectedVersion:123}),true,'existing-record deletion is recoverable');
 assert.equal(R.visibleDraft({record:blank,metadataOnly:true}),true,'intentional date-only change is recoverable');
 assert.equal(R.visibleDraft(null),false);
});
test('draft excerpts remove Markdown and image IDs without joining English words',()=>{
 assert.equal(R.draftExcerpt('# Heading\n\n**hello world**\n- item\n'+R.imageToken(image)), 'Heading hello world item');
 assert.equal(R.draftExcerpt('[source](https://example.com)\n<script>alert(1)</script>'),'source <script>alert(1)</script>');
 assert.equal(R.draftExcerpt('a'.repeat(200)).length,150);
 assert.equal(R.plain('hello world'),'helloworld','lossless AI guard retains its original contract');
});
test('bare requests open editors but content and questions never silently navigate',()=>{
 for(const [s,t] of [['我想记录一条学习记录','learning'],['请帮我记录一篇学习笔记','learning'],['打开工作日志','work_note'],['我要写一条经验','experience'],['添加一个灵感创意','idea']])assert.equal(R.openIntent(s),t,s);
 for(const s of ['我今天学习了Python，笔记是列表推导式','我想记录一条学习记录：今天读了数据库','我的工作记录在哪里？','我现在上班','记录学习了什么'])assert.equal(R.openIntent(s),null,s);
});
test('body mandatory, local pictures cannot substitute for text; attachments stay addressable',()=>{
 assert.equal(R.hasBody(' \n# **  '),false);assert.equal(R.hasBody(R.imageToken(image)),false);assert.equal(R.hasBody('正文\n'+R.imageToken(image)),true);
 let r=R.attach({note:'原文',title:'title'},image);assert.equal(r.images.length,1);assert.match(r.note,/zhishi-media:/);assert.deepEqual(R.attach(r,image),r);
 assert.throws(()=>R.validateImages([{id:'../secrets',name:'bad'}]));assert.throws(()=>R.validateImages([image,image]));assert.throws(()=>R.validateImages(Array(101).fill(image)));
});
test('chunking reads every character including long documents, surrogate pairs and images',()=>{
 const text=('原文🌳'.repeat(2400)+'\n'+R.imageToken(image)+'\n').repeat(3),parts=R.chunks(text);
 assert.equal(parts.join(''),text);assert.ok(parts.length>10);for(const part of parts){assert.doesNotMatch(part,/^[\uDC00-\uDFFF]|[\uD800-\uDBFF]$/);assert.ok(!part.includes('zhishi-media:')||[...part.matchAll(R.imageRE())].length>0);}
});
test('AI format is guarded against paraphrase, omission, injection and image reordering',()=>{
 const source='第一段正文\n第二段正文\n'+R.imageToken(image);assert.ok(R.guardMarkdown(source,'# 第一段正文\n\n**第二段正文**\n'+R.imageToken(image)));
 for(const bad of ['第一段正文',source+'\n新增结论',source.replace('正文','摘要'),source.replace(R.imageToken(image),'')])assert.throws(()=>R.guardMarkdown(source,bad));
 assert.throws(()=>R.guardMarkdown(source,source.replace('hello  image ','other')));
});
test('metadata is strict and swipe direction follows explicit user preference',()=>{
 assert.deepEqual(R.metadata({title:' 标题 ',tags:['学习','学习']}),{title:'标题',tags:['学习']});assert.throws(()=>R.metadata({title:'',tags:[]}));assert.throws(()=>R.metadata({title:'标题',tags:[4]}));
 assert.equal(R.swipe(-90,10),1);assert.equal(R.swipe(90,10),-1);assert.equal(R.swipe(20,0),0);assert.equal(R.swipe(90,100),0);assert.equal(R.pageForOffset(500,100,3),2);assert.equal(R.pageForOffset(-1,100,3),0);
 assert.equal(R.swipe(-54,0),0);assert.equal(R.swipe(-55,0),1);assert.equal(R.swipe(55,0),-1);assert.equal(R.swipe(0,90),0);
});
test('reader counts only columns occupied by content, not rounded scroll overflow',()=>{
 assert.equal(R.occupiedPages([0,24,400,824,3224,NaN],400),9);
 assert.equal(R.occupiedPages([],400),1);
 assert.equal(R.occupiedPages([0,364],400),1);
 assert.equal(R.occupiedPages([0,400],400),2);
});
