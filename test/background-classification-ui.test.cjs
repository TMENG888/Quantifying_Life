const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');

test('classification refresh updates data everywhere but only redraws the visible, unobscured statistics page',async()=>{
 for(const [page,topicView,dialogOpen,expected] of [['chat',true,false,0],['today',true,false,0],['settings',true,false,0],['records',false,false,0],['records',true,true,0],['records',true,false,1]]){
  let tick,renders=0;const state={page,topicView,settings:{},records:[]};
  const context={window:{LifeDomain:{},LifeWiki:{}},document:{hidden:false,addEventListener(){},querySelectorAll:()=>[],getElementById:()=>({open:dialogOpen})},setTimeout:fn=>(tick=fn,1),clearTimeout(){},Date};
  vm.runInNewContext(fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/auto-wiki.js'),'utf8'),context);
  context.window.AutoWiki.init({state,render:()=>renders++,modelConfigured:()=>false,api:async action=>action==='getWikiQueue'?{count:0,settings:{},nativeClassification:true}:[{id:'fresh-classification'}]});
  context.window.AutoWiki.kick(0);await tick();
  assert.equal(state.records[0].id,'fresh-classification');
  assert.equal(renders,expected,JSON.stringify({page,topicView,dialogOpen}));
 }
});
