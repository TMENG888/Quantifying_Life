const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const D=require('../app/src/main/assets/domain.js');
function fixture(){const state={messages:[],records:[],candidates:[],changes:[],chatSession:{id:'qa'},chatSessions:[],wikiPages:[],busy:false};
 const nodes={};const c={window:{addEventListener(){}},ChatMarkdown:{render:s=>s},document:{querySelector:s=>nodes[s]}};
 vm.runInNewContext(fs.readFileSync(__dirname+'/../app/src/main/assets/chat-ui.js','utf8'),c);
 const ui=c.window.ChatUI;ui.init({state,profile:()=>({model:'deepseek-flash'}),esc:s=>String(s??'').replace(/[<>&"]/g,v=>({'<':'&lt;','>':'&gt;','&':'&amp;','"':'&quot;'}[v]))});return{state,nodes,ui};}
test('turn metadata stays accessible without a centered timeline or repeated reply status',()=>{const {state,ui}=fixture();state.messages=[{id:'user',role:'user',text:'今天学习了哪些知识？',createdAt:Date.now()},{id:'answer',role:'assistant',text:'这里是本轮回答。',followUps:['这几个要点之间有什么关系？','如何实践今天学到的方法？']}];const html=ui.page();assert.match(html,/chat-turn-time sr-only/);assert.match(html,/assistant-mark/);assert.ok(!html.includes('已回复'));assert.match(html,/chat-followups/);assert.match(html,/data-followup="如何实践/);assert.match(html,/composer-main/);assert.ok(html.indexOf('data-action="attach"')<html.indexOf('id="chat-input"'));});
test('suggestions are optional, bounded plain text and hidden while busy, attaching or awaiting confirmation',()=>{const {state,ui}=fixture();assert.equal(ui.followUps(['相同问题如何处理？','相同问题如何处理？',null,'短','x'.repeat(65)]).length,1);state.messages=[{id:'a',role:'assistant',text:'回答',followUps:['<img src=x onerror=evil()>']}];assert.match(ui.page(),/data-followup="&lt;img/);state.busy=true;assert.ok(!ui.page().includes('data-followup='));state.busy=false;state.attachment={kind:'text',name:'attachment.txt'};assert.ok(!ui.page().includes('data-followup='));state.attachment=null;state.candidates=[{}];state.candidates.map=()=>[];assert.ok(!ui.page().includes('data-followup='));});
test('progress reports actual stages without replacing the input or fabricating percent progress',()=>{const {state,nodes,ui}=fixture();nodes['[data-chat-stage]']={};nodes['[data-chat-progress]']={};ui.progress('正在读取链接内容');assert.equal(nodes['[data-chat-stage]'].textContent,'查阅相关资料');assert.equal(nodes['[data-chat-progress]'].textContent,'正在读取链接内容');state.busy=true;state.busyLabel='正在压缩旧会话';assert.match(ui.page(),/整理对话上下文/);});
test('follow-up fields survive structured parsing but cannot bypass record validation',()=>{const r=D.parseAI(JSON.stringify({answer:'回答',records:[],citations:[],followUps:['下一步如何实践？',{}]}),false);assert.deepEqual(r.followUps,['下一步如何实践？']);assert.throws(()=>D.parseAI(JSON.stringify({answer:'a',records:[{type:'invalid'}],citations:[],followUps:['继续呢？']})));});

function interactive(send){
 const nodes={},buttons=[{dataset:{followup:'如何实践今天学到的方法？'}},{dataset:{followup:'这些知识之间有什么联系？'}}],state={chatSession:{id:'synthetic'},busy:false},classes={contains:()=>true,toggle(){},remove(){}};
 const node=id=>nodes[id]||(nodes[id]={id,value:'',style:{},scrollHeight:44,clientHeight:400,scrollTop:0,isConnected:true,classList:classes,selectionStart:0,selectionEnd:0,querySelector:selector=>node(id+' '+selector),getBoundingClientRect:()=>({top:650,bottom:800}),addEventListener(){},scrollTo(){},setSelectionRange(start,end){this.selectionStart=start;this.selectionEnd=end;},dispatchEvent(){this.oninput?.();},focus(){throw Error('A follow-up must not summon the keyboard');}});
 const document={activeElement:null,getElementById:node,body:{classList:classes},querySelector:node,querySelectorAll:selector=>selector==='[data-followup]'?buttons:[]};
 const context={window:{addEventListener(){}},document,innerHeight:800,Event,requestAnimationFrame:fn=>fn()};vm.runInNewContext(fs.readFileSync(__dirname+'/../app/src/main/assets/chat-ui.js','utf8'),context);
 const ui=context.window.ChatUI;let count=0;ui.init({state,safe:fn=>fn(),send:()=>{count++;return send(node('chat-input').value);}});ui.bind();return{ui,state,buttons,input:node('chat-input'),count:()=>count};
}
test('tapping a suggested question sends once through the normal path without replacing the input or opening keyboard',async()=>{
 let finish;const sent=[],h=interactive(text=>{sent.push(text);return new Promise(resolve=>finish=resolve);}),input=h.input;
 const first=h.buttons[0].onclick();await h.buttons[1].onclick();assert.equal(h.count(),1);assert.deepEqual(sent,['如何实践今天学到的方法？']);assert.ok(h.buttons.every(b=>b.disabled));assert.equal(h.input,input);finish();await first;assert.ok(h.buttons.every(b=>!b.disabled));
});
test('failed follow-up releases the tap lock and keeps the attempted text available for retry',async()=>{
 const h=interactive(async()=>{throw Error('network unavailable');});await assert.rejects(h.buttons[0].onclick(),/network unavailable/);assert.equal(h.input.value,h.buttons[0].dataset.followup);assert.ok(h.buttons.every(b=>!b.disabled));await assert.rejects(h.buttons[0].onclick(),/network unavailable/);assert.equal(h.count(),2);
});
test('stale suggestions cannot send while a request or attachment is active',async()=>{
 const h=interactive(async()=>{});h.state.busy=true;await h.buttons[0].onclick();h.state.busy=false;h.state.attachment={};await h.buttons[0].onclick();assert.equal(h.count(),0);
});
test('quick follow-up immediately restores the previous draft and selection after normal send acknowledgement',async()=>{
 let finish;const sent=[],h=interactive(text=>{sent.push(text);h.ui.sent();return new Promise(resolve=>finish=resolve);});h.input.value='尚未发送的另一个问题';h.input.setSelectionRange(2,5);
 const pending=h.buttons[0].onclick();assert.deepEqual(sent,['如何实践今天学到的方法？']);assert.equal(h.input.value,'尚未发送的另一个问题');assert.equal(h.input.selectionStart,2);assert.equal(h.input.selectionEnd,5);
 h.input.value='继续修改原草稿';finish();await pending;assert.equal(h.input.value,'继续修改原草稿');
});
test('pre-send failure restores an unrelated draft without leaving the composer locked',async()=>{
 const h=interactive(async()=>{throw Error('save failed');});h.input.value='保留我的原草稿';h.input.setSelectionRange(1,3);await assert.rejects(h.buttons[0].onclick(),/save failed/);assert.equal(h.input.value,'保留我的原草稿');assert.equal(h.input.selectionStart,1);assert.equal(h.input.selectionEnd,3);assert.ok(h.buttons.every(b=>!b.disabled));
});
