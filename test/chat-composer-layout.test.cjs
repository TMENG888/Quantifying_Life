const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const read=file=>fs.readFileSync(path.join(__dirname,'../',file),'utf8');
function fixture(shellBottom,composerTop,inputHeight=44){
 const input={style:{},value:'unsent draft',scrollHeight:inputHeight},button={style:{}},send={},box={isConnected:true,scrollHeight:1000,clientHeight:400,scrollTop:0,scrollTo(o){this.scrollTop=o.top;}};
 const composer={getBoundingClientRect:()=>({top:composerTop})},shell={getBoundingClientRect:()=>({bottom:shellBottom}),querySelector:()=>composer};
 const nodes={'chat-input':input,'chat-bottom':button,'chat-scroll':box},events={};
 const sandbox={window:{addEventListener:(name,fn)=>events[name]=fn},innerHeight:800,document:{activeElement:null,getElementById:id=>nodes[id],body:{classList:{contains:()=>true,toggle(){},remove(){}}},querySelector:s=>s==='.chat-shell'?shell:s==='.chat-send'?send:composer}};
 vm.runInNewContext(read('app/src/main/assets/chat-ui.js'),sandbox);sandbox.window.ChatUI.init({state:{busy:false}});
 events.resize();return {input,button,send};
}
test('latest button remains above the actual composer with multiline text, attachments and changed viewport',()=>{
 for(const [shellBottom,composerTop,height] of [[800,650,44],[800,540,144],[460,250,144],[700,410,300]]){
  const f=fixture(shellBottom,composerTop,height);assert.equal(f.button.style.bottom,(shellBottom-composerTop+12)+'px');assert.equal(f.input.style.height,Math.min(144,Math.max(44,height))+'px');assert.equal(f.input.value,'unsent draft');assert.equal(f.send.disabled,false);
 }
});
test('button is centered with a separate 48px target and no textarea focus outline',()=>{
 const css=read('app/src/main/assets/app-ui.css');assert.match(css,/\.chat-bottom\s*\{[^}]*left:50%;\s*right:auto;\s*transform:translateX\(-50%\)[^}]*width:48px;\s*height:48px/);
 assert.match(css,/\.chat-mode \.composer textarea:focus, \.chat-mode \.composer textarea:focus-visible\s*\{\s*outline:none;\s*box-shadow:none;/);
 assert.match(css,/\.chat-mode \.composer:focus-within\s*\{\s*border-color:var\(--green\)/);
 assert.match(read('app/src/main/assets/chat-ui.js'),/composerObserver\.observe\(document\.querySelector\('\.chat-shell \.composer'\)\)/);
});
