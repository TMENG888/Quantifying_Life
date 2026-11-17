(function(root){
 'use strict';
 let e,current={state:'idle',message:''},starting=false,refreshing=false,sequence=0;
 const active=()=>starting||['preparing','waiting','saving'].includes(current.state);
 function paint(){
  const button=document.getElementById('analysis-export'),status=document.getElementById('analysis-export-status');if(!button)return;
  button.disabled=active();button.textContent=starting||current.state==='preparing'?'正在整理…':current.state==='waiting'?'等待选择保存位置':current.state==='saving'?'正在保存…':'导出分析数据';
  status.textContent=current.message||'';status.setAttribute('role',current.state==='failed'?'alert':'status');status.classList.toggle('is-error',current.state==='failed');
 }
 function accept(value){sequence++;current=value||{state:'idle',message:''};paint();}
 function entry(){return '<section class="analysis-export-panel" aria-label="记录分析导出"><div><h2>分析数据</h2><p>一次导出全部日期的学习、工作和随笔，保留完整笔记、时间、标签及 Wiki 来源，便于分析指标与图表。</p></div><button type="button" id="analysis-export" class="secondary">导出分析数据</button><p class="analysis-export-scope">不含模型配置、聊天、定位或图片文件。仅保存到你选择的位置。</p><p id="analysis-export-status" class="analysis-export-status" role="status" aria-live="polite"></p></section>';}
 async function refresh(){if(refreshing||starting)return;refreshing=true;const version=sequence;try{const value=await e.api('analysisExportStatus');if(version===sequence)accept(value);}catch{}finally{refreshing=false;}}
 async function start(){
  if(active())return;starting=true;sequence++;current={state:'preparing',message:'正在整理全部历史记录…'};paint();
  const version=sequence;
  try{const value=await e.api('analysisExportStart');if(version===sequence)accept(value);}
  catch(error){if(version===sequence)accept({state:'failed',message:'导出未完成：'+error.message+'。可重试。'});}
  finally{starting=false;paint();}
 }
 function bind(){const b=document.getElementById('analysis-export');if(!b)return;b.onclick=()=>e.safe(start);paint();refresh();}
 function init(env){e=env;setInterval(()=>{if(!document.hidden&&document.getElementById('analysis-export')&&active())refresh();},1500);document.addEventListener('visibilitychange',()=>{if(!document.hidden&&document.getElementById('analysis-export'))refresh();});}
 root.AnalysisExportUI={init,entry,bind,accept,refresh};
})(window);
