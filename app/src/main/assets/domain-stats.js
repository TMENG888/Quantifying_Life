(function(root){
 'use strict';
 const node=typeof module==='object'&&module.exports,T=node?require('./topic-stats.js'):root.TopicStats;
 const defaults={
  learning:[['AI 与技术',/^(ai|人工智能|大模型|机器学习|深度学习|rag|graphrag|llm|llmwiki|llm wiki|pageindex|jev|编程|软件开发|数据分析|数据科学|算法|检索系统|python|sql|知识图谱|智能体)(?:$|[\s/：:·-])|人工智能|大模型|模型应用|程序设计|数据分析|检索系统|智能体/i],['商业与管理',/商业|企业|财务|运营|管理|营销|市场|数据驱动|数据治理|数据化决策|项目执行|项目复盘/],['人文与阅读',/历史|文学|哲学|社会|读书|人文|阅读/],['语言与表达',/英语|外语|写作|表达|沟通|演讲/],['健康与生活',/健康|运动|健身|睡眠|生活|心理|禅定/]],
  work:[['研究与分析',/研究|调研|分析|数据|实验|测试|检索/],['产品与技术',/产品|设计|技术|开发|代码|编程|算法|模型|ai|rag/i],['沟通与协作',/沟通|会议|协作|汇报|客户|访谈/],['规划与复盘',/规划|计划|复盘|战略|目标|管理/],['执行与交付',/执行|交付|项目|任务|生产|运营/]],
  thoughts:[['技术思考',/技术|编程|算法|模型|人工智能|ai|rag|wiki|pageindex/i],['工作与成长',/工作|成长|职业|项目|管理|学习|复盘/],['产品与创意',/产品|设计|创意|灵感|创业/],['人文与生活',/历史|文学|哲学|生活|人文|读书|阅读/],['自我与关系',/自我|情绪|心理|关系|沟通|感悟|健康/]]
 };
 function label(tag,module='learning',mapping={}){const k=T.key(tag);if(!k)return '未分类';const chosen=Object.prototype.hasOwnProperty.call(mapping,k)?mapping[k]:null;if(typeof chosen==='string'&&chosen.trim())return chosen.trim().slice(0,24);return (defaults[module]||defaults.learning).find(x=>x[1].test(k))?.[0]||'其他主题';}
 function classify(record,module='learning',mapping={}){
  const version=Number(record.updatedAt||0),tags=T.tags(record.tags);
  const overrides=tags.filter(t=>Object.prototype.hasOwnProperty.call(mapping,T.key(t))&&typeof mapping[T.key(t)]==='string'&&mapping[T.key(t)].trim()).map(t=>label(t,module,mapping));
  if(overrides.length)return {labels:[...new Set(overrides)],source:'manual'};
  const domains=T.tags(record.wikiDomains).filter(t=>choices(module).includes(t));
  if(record.wikiClassificationVersion>=3&&Number(record.wikiDomainVersion)===version&&domains.length)return {labels:domains,source:'wiki'};
  const topics=Number(record.wikiTopicVersion)===version?T.tags(record.wikiTopics):[];
  if(topics.length){const labels=[...new Set(topics.map(t=>label(t,module,mapping)))].filter(x=>!['其他主题','未分类'].includes(x));if(labels.length)return {labels,source:'wiki-topics'};}
  const labels=[...new Set(tags.map(t=>label(t,module,mapping)))].filter(x=>!['其他主题','未分类'].includes(x));return {labels:labels.length?labels:['待分类'],source:'tags'};
 }
 function summarize(data,mapping={},sort='minutes'){
  const buckets=new Map();
  for(const item of data.items){const classification=classify(item.record,data.module,mapping);for(const name of classification.labels){if(!buckets.has(name))buckets.set(name,{label:name,count:0,shifts:0,minutes:0,ids:[],topics:new Set(),days:new Set(),wikiCount:0,provisionalCount:0});const b=buckets.get(name);b.ids.push(item.record.id);b.minutes+=item.minutes;if(classification.source==='wiki')b.wikiCount++;else b.provisionalCount++;if(item.record.type==='work')b.shifts++;else b.count++;b.days.add(item.record.date);for(const k of item.keys)if(k&&(classification.source!=='tags'||label(k,data.module,mapping)===name))b.topics.add(k);}}
  const metric=data.module==='thoughts'?'count':sort==='count'?'count':'minutes';
  return [...buckets.values()].map(b=>({...b,topics:[...b.topics],days:b.days.size})).sort((a,b)=>b[metric]-a[metric]||b.count-a.count||b.minutes-a.minutes||a.label.localeCompare(b.label,'zh-CN'));
 }
 function choices(module){return [...(defaults[module]||defaults.learning).map(x=>x[0]),'其他主题'];}
 // A part-to-whole view must conserve each record's metric across overlapping domains.
 function distribution(data,mapping={},sort='minutes'){
  const count=data.module==='thoughts'||sort==='count',buckets=new Map();let total=0;
  for(const item of data.items){
   const labels=classify(item.record,data.module,mapping).labels;
   const value=count?(item.record.type==='work'?0:1):Math.max(0,Number(item.minutes)||0);
   total+=value;
   for(const name of labels){if(!buckets.has(name))buckets.set(name,{label:name,value:0,ids:[],members:[name]});const b=buckets.get(name);b.value+=value/labels.length;b.ids.push(item.record.id);}
  }
  let list=[...buckets.values()].sort((a,b)=>b.value-a.value||a.label.localeCompare(b.label,'zh-CN'));
  // A ring stays readable with six slices; preserve drilldown for the aggregate.
  if(list.length>6){const tail=list.slice(5);list=list.slice(0,5).concat({label:'其余领域',value:tail.reduce((n,b)=>n+b.value,0),ids:[...new Set(tail.flatMap(b=>b.ids))],members:tail.flatMap(b=>b.members)});}
  let remaining=total>0?100:0;
  list=list.map(b=>{const exact=total?b.value/total*100:0,percent=Math.floor(exact);remaining-=percent;return {...b,percent,remainder:exact-percent};});
  const order=list.map((b,i)=>i).sort((a,b)=>list[b].remainder-list[a].remainder||a-b);
  for(let i=0;i<remaining;i++)list[order[i%order.length]].percent++;
  return {total,buckets:list};
 }
 const api={label,classify,summarize,distribution,choices};if(node)module.exports=api;else root.DomainStats=api;
})(typeof window!=='undefined'?window:globalThis);
