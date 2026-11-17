(function(root){
 'use strict';let e;
 function open(){if(!e.modelConfigured())throw Error('请先在设置中配置可用模型');const target=root.RecordEditor.rewriteTarget();root.SelectionRewrite.request({text:target.text,instruction:'改写'});root.RecordEditor.suspendSelection();
  root.RewritePanel.open({original:target.text,generate:async(instruction,previous)=>{const p=root.SelectionRewrite.request({...target,instruction,previous});const r=await e.api('ask',{purpose:'selection-rewrite',messages:[{role:'system',content:p.system+'当前是富文本选区，只返回纯文本改写，不用Markdown标记；保留段落换行。'},{role:'user',content:JSON.stringify(p.input)}]});return root.SelectionRewrite.result(r.text);},apply:text=>root.RecordEditor.replaceSelection(target,text)});
 }
 root.RecordRewrite={init:env=>{e=env;},open};
})(window);
