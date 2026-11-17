(function(root){
 'use strict';let e;
 function bind(){const button=document.getElementById('publication-import');if(button)button.onclick=()=>e.safe(()=>e.api('publicationPick'));}
 async function accept(data){if(data.state==='loading'){e.toast('正在导入文章…');return;}if(data.state==='failed'){e.toast(data.message||'导入失败');return;}if(data.state!=='complete')return;
  e.state.records=await e.api('getRecords');e.state.recordTab='creation';e.state.topicView=false;e.go('records');e.toast(data.record.alreadyImported?'文章已在创作记录中':'已导入创作记录');await root.RecordEditor.open(data.record,null,'creation',true);
 }
 root.PublicationUI={init:env=>{e=env;},bind,accept,active:()=>false,key:()=> 'list',back:()=>false};
})(window);
