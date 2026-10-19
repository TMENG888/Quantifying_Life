const test=require('node:test'),assert=require('node:assert/strict'),R=require('../app/src/main/assets/document-domain.js');
const id='a'.repeat(64),image={id,name:'hello [image]',width:20,height:20};
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
 assert.equal(R.swipe(-90,10),-1);assert.equal(R.swipe(90,10),1);assert.equal(R.swipe(20,0),0);assert.equal(R.swipe(90,100),0);assert.equal(R.pageForOffset(500,100,3),2);assert.equal(R.pageForOffset(-1,100,3),0);
});
