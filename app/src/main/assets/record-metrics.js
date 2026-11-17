(function(root){
 'use strict';
 const node=typeof module==='object'&&module.exports,I=node?require('./information-stats.js'):root.InformationStats;
 const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const number=v=>v===null?'—':Number(v).toLocaleString('zh-CN',{maximumFractionDigits:1});
 function values(record){
  if(!I.eligible(record))return {eligible:false,state:'empty',units:null,density:null,efficiency:null,cost:null,minutes:null};
  const s=I.roll([record]);
  return {...s,eligible:true,state:s.done?'complete':record.informationUnits?'stale':'pending',efficiencyReason:record.type==='learning'?'未计时':'无独立耗时'};
 }
 function html(record,{compact=false}={}){
  const v=values(record);if(!v.eligible)return '';
  const pending=v.state!=='complete',items=[['units','信息量','个单元'],['density','信息密度','单元／千字符'],['efficiency','产出效率','单元／小时']];
  return `<span class="record-metrics ${compact?'is-compact':''}" data-record-metrics="${esc(record.id)}" role="group" aria-label="本篇信息指标">${items.map(([key,label,unit])=>`<span class="record-metric" data-record-metric="${key}"><span class="record-metric-label">${label}</span><strong>${pending?(v.state==='stale'?'待重新分析':'待分析'):v[key]===null?(key==='efficiency'?v.efficiencyReason:'—'):number(v[key])}</strong><small>${!pending&&v[key]!==null?unit:'\u00a0'}</small></span>`).join('')}</span>`;
 }
 const api={values,html,number};if(node)module.exports=api;else root.RecordMetrics=api;
})(typeof window!=='undefined'?window:globalThis);
