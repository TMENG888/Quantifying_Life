const http=require('node:http');
const reply=(answer,citations=[])=>({answer,records:[],citations});
function mock(body,protocol){
 const name=protocol==='responses'?body.tools[0].name:body.tools[0].function.name;
 const messages=protocol==='responses'?body.input:body.messages;
 const system=protocol==='responses'?body.instructions:messages.find(m=>m.role==='system').content;
 const last=messages.at(-1).content;
 let result;
 if(name==='plan_record_search'){
  const input=JSON.parse(last),q=input.question;
  const intent=/今天在知乎|提取图片|提取.*记录|记录/.test(q)&&!/什么|多久|上个月/.test(q)?'record':/上个月/.test(q)?'history':/JEV|jev/i.test(q)?'insight':'history';
  result={intent,topic:/JEV|jev/i.test(q)?'JEV':'',keywords:/JEV|jev/i.test(q)?['JEV模型']:[],dateStart:'',dateEnd:''};
 }else if(name==='compile_learning_wiki'){
  const input=JSON.parse(last);const ids=input.records?input.records.map(r=>r.id):input.sourceIds;
  result={summary:'JEV 主题笔记：从概念理解到应用实践。这是个人笔记整理，不是通用熟练门槛。',aliases:['JEV模型'],concepts:[{title:'应用实践',description:'通过独立案例验证理解，区分自评和实践证据。',sourceIds:[ids[0]]}],sourceIds:ids};
 }else if(protocol==='responses'&&system==='test')result=reply('Responses 接口调用成功');
 else{
  const context=JSON.parse(system.split('本地检索与确定性汇总：')[1]);const question=typeof last==='string'?last:last[0].text;
  if(question.includes('example.com')||question.includes('mp.weixin.qq.com')){const material=messages.find(m=>m.role==='user'&&typeof m.content==='string'&&m.content.includes('untrustedLinkSources'));if(!material)throw Error('Missing separate untrusted source message');const sources=JSON.parse(material.content).untrustedLinkSources,s=sources[0];if(system.includes(s.excerpt||'NEVERMATCH')||material.content.includes('tvly-'))throw Error('Unsafe source/key handling');result={answer:'已根据链接准备候选；请核对来源。'+(question.includes('mp.weixin.qq.com')?'全部历史统计、平台分布、熟练度评估：'+('无关内容'.repeat(100)):''),records:[{type:'learning',date:'2026-10-07',title:s.title||'模型不该猜的标题',platform:s.platform||'',contentType:s.contentType||'other',url:s.url,durationMinutes:question.includes('35分钟')?35:999,note:question.includes('我的笔记')?'用户的笔记':'网上摘要被模型误作笔记',tags:[]}],citations:[]};}
  else if(question.includes('上个月6号'))result=reply(context.period+'；历史学习 '+context.summary.learningMinutes+' 分钟；净工作 '+context.summary.workMinutes+' 分钟。',context.records.map(r=>r.id));
  else if(/JEV|jev/i.test(question)){
   const stats=context.topicSummary,m=stats.milestones.at(-1);
   result=reply('JEV 学习累计 '+stats.recordCount+' 条 / '+stats.learningMinutes+' 分钟。'+(m?'截至该次实践标记累计 '+m.cumulativeRecords+' 条 / '+m.cumulativeMinutes+' 分钟；不能当作普遍的熟练门槛。':'没有掌握程度标记，不能仅凭时长断言熟练。')+' Wiki 页数 '+context.wikiPages.filter(p=>!p.stale).length+'。',context.availableCitationIds.slice(0,8));
  }else if(question.includes('这周'))result=reply('总学习时长：'+context.summary.learningMinutes+' 分钟。已引用本地记录。',['test-learning']);
  else result={answer:'已提取一条学习记录，请确认后保存。',records:[{type:'learning',date:'2026-10-05',title:'时间管理',platform:'知乎',contentType:'article',durationMinutes:30,tags:['时间管理'],note:'今日学习记录'}],citations:[]};
 }
 return {name,result};
}
http.createServer((req,res)=>{
 let raw='';req.on('data',x=>raw+=x);req.on('end',()=>{
  try{
   if(req.headers.authorization!=='Bearer local-test-only-not-a-real-key'){res.writeHead(401);return res.end('{}');}
   const body=JSON.parse(raw),protocol=req.url==='/v1/responses'?'responses':'chat';
   if(!body.tools?.length||!(protocol==='responses'?Array.isArray(body.input)&&body.store===false:req.url==='/v1/chat/completions'&&Array.isArray(body.messages)))throw Error('Bad synthetic request');
   const {name,result}=mock(body,protocol);
   res.writeHead(200,{'Content-Type':'application/json'});
   if(protocol==='responses')res.end(JSON.stringify({output:[{type:'function_call',name,arguments:JSON.stringify(result)}]}));
   else res.end(JSON.stringify({choices:[{message:{content:null,tool_calls:[{type:'function',function:{name,arguments:JSON.stringify(result)}}]}}]}));
  }catch(e){res.writeHead(400);res.end(JSON.stringify({error:e.message}));}
 });
}).listen(8790,'127.0.0.1',()=>console.log('Synthetic AI service on 8790; only dummy test credential accepted'));
