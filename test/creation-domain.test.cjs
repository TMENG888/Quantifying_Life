const test=require('node:test'),assert=require('node:assert/strict');
const D=require('../app/src/main/assets/domain.js'),R=require('../app/src/main/assets/document-domain.js'),I=require('../app/src/main/assets/information-stats.js'),W=require('../app/src/main/assets/wiki.js');
const base=()=>({id:'creation-source',type:'creation',date:'2026-11-02',title:'一次学习如何成为可发布的故事',note:'先提出问题，再用自己的经历给出例子。',tags:['写作'],durationMinutes:0,creatorPlatform:'公众号',creationStatus:'draft',publishedUrl:''});
test('creation is an independent validated record and not learning evidence',()=>{const r=base();assert.equal(D.validate(r),r);assert.equal(D.isThought(r),false);assert.equal(I.eligible(r),false);assert.equal(W.topicRecords([r],'写作').length,0);});
test('creation keeps time ledgers separate and validates publishing metadata',()=>{
 for(const patch of [{durationMinutes:1},{workStart:'2026-11-02T09:00:00'},{studyTiming:{}},{creationStatus:'sending'},{publishedUrl:'javascript:alert(1)'},{creatorPlatform:'x'.repeat(101)}])assert.throws(()=>D.validate({...base(),...patch}));
 for(const creationStatus of ['draft','ready','published'])assert.doesNotThrow(()=>D.validate({...base(),creationStatus,publishedUrl:'https://example.com/my-post'}));
});
test('creation metadata drafts survive but default empty editor does not create a draft',()=>{
 const blank={...base(),title:'',note:'',tags:[],creatorPlatform:''};assert.equal(R.draftContent(blank),false);
 for(const patch of [{creatorPlatform:'公众号'},{creationStatus:'ready'},{publishedUrl:'https://example.com'}])assert.equal(R.draftContent({...blank,...patch}),true);
});
test('explicit bare creation commands open the right editor without intercepting content',()=>{
 for(const text of ['新建自媒体创作','打开自媒体稿件','写一篇创作图文'])assert.equal(R.openIntent(text),'creation');
 for(const text of ['记录自媒体创作的学习心得：写作要用例子','自媒体创作应该怎么做？'])assert.equal(R.openIntent(text),null);
});
