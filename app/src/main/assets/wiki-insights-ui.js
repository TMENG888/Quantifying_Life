(function(root){
 'use strict';
 let env,kind='connection',showHidden=false;
 const labels={connection:'跨领域联系',conflict:'观点冲突',gap:'知识缺口'},states={pending:'待核对',followup:'待跟进',dismissed:'暂不采纳',resolved:'已处理'};
 const hints={connection:'发现不同主题可能共用的方法，先看证据，再验证能否迁移。',conflict:'并列保留不同主张，核对情境和时间，不由 AI 自动裁定。',gap:'指出当前记录缺少的证据，不等同于你不会。'};
 const el=id=>document.getElementById(id),e=v=>env.esc(String(v||''));
 const all=()=>WikiInsights.items(env.state.wikiInsights,env.state.wikiPages,env.state.records,{includeStale:true});
 const find=id=>all().find(f=>f.key===id);
 function tabs(){return '<div class="record-tabs">'+Object.keys(labels).map(k=>'<button data-insight-kind="'+k+'" class="'+(k===kind?'active':'')+'">'+labels[k]+'</button>').join('')+'</div>';}
 function entry(){const items=all();return '<div class="insight-entry" id="insight-entry">'+Object.keys(labels).map(k=>'<button class="secondary" data-open-insight="'+k+'"><span>'+items.filter(f=>f.kind===k&&!f.stale&&f.review.state!=='dismissed').length+'</span>'+labels[k]+'</button>').join('')+'</div>';}
 function bindEntry(){document.querySelectorAll('[data-open-insight]').forEach(b=>b.onclick=()=>env.safe(()=>open(b.dataset.openInsight)));}
 function cards(){const items=all().filter(f=>f.kind===kind&&(showHidden||!f.stale&&f.review.state!=='dismissed'));return items.length?items.map(f=>'<button class="insight-card" data-insight="'+e(f.key)+'"><span class="insight-topics">'+f.topics.map(e).join(' ↔ ')+'</span><strong>'+e(f.title)+'</strong><span class="insight-preview">'+e(f.reason)+'</span><span class="insight-state">'+(f.archived?'历史版本 · 待复核':f.stale?'来源变化 · 待复核':states[f.review.state]||states.pending)+' · AI 线索</span></button>').join(''):'<div class="empty"><strong>还没有'+labels[kind]+'</strong><p>前台空闲时会自动分析已整理主题；没有结果不代表不存在。</p></div>';}
 function help(){return '<details class="insight-help"><summary>这些结果如何产生？</summary><p>主题摘要结合抽样原文分析，逐字核对引文和来源版本。原笔记变化后，旧结果等待重新分析且不用于问答。这里标记已处理只保存你的核对说明，不修改原笔记，也不代表 AI 已证实结论。</p><p>使用已配置模型，可能产生 API 费用。分析不是全文穷举，退出 App 后不会继续运行。</p></details>';}
 function bindList(){document.querySelectorAll('[data-insight-kind]').forEach(b=>b.onclick=()=>{kind=b.dataset.insightKind;list();});el('insight-hidden').onclick=()=>{showHidden=!showHidden;list();};document.querySelectorAll('[data-insight]').forEach(b=>b.onclick=()=>detail(b.dataset.insight));}
 function list(){env.editorFrame('知识发现','<div id="insight-list">'+tabs()+'<p class="hint">'+hints[kind]+'</p><button class="text-button" id="insight-hidden">'+(showHidden?'隐藏待复核与暂不采纳':'查看待复核与暂不采纳')+'</button>'+cards()+help()+'<button class="secondary full" id="insight-wiki">返回主题知识页</button></div>');bindList();el('insight-wiki').onclick=env.showWiki;}
 async function open(k){kind=labels[k]?k:'connection';showHidden=false;env.state.wikiInsights=await env.api('getWikiInsights');list();}
 function detail(id){
  const f=find(id);if(!f)return list();
  const sideHTML=f.sides.map(s=>'<section class="insight-side"><strong>'+e(env.state.wikiPages.find(p=>p.id===s.pageId)?.topic||'来源主题')+'</strong><p>'+e(s.claim)+'</p>'+s.evidence.map(x=>{const r=env.state.records.find(r=>r.id===x.recordId);return '<blockquote>'+e(x.quote)+'</blockquote>'+(r?'<button class="citation" data-insight-source="'+e(x.recordId)+'">'+e(r.date)+' · '+e(r.title)+'</button>':'<p class="hint">原始记录已删除</p>');}).join('')+'</section>').join('');
  const previous=(env.state.wikiInsights.reviews||[]).filter(r=>r.findingId===f.id&&r.signature!==f.signature);
  const history=previous.length?'<details class="insight-help"><summary>此前版本的核对说明</summary>'+previous.map(r=>'<p>'+e(states[r.state])+' · '+e(r.note||'未填写说明')+'</p>').join('')+'</details>':'';
  env.editorFrame(labels[f.kind],'<div id="insight-detail" data-finding="'+e(id)+'"><button class="text-button" id="insight-back">← 返回列表</button><h3>'+e(f.title)+'</h3><p class="hint">'+(f.stale?(f.archived?'历史版本':'来源变化')+' · 待复核，不用于问答':e(states[f.review.state])+' · AI 待验证线索')+'</p>'+sideHTML+'<h4>依据与适用条件</h4><p class="insight-prose">'+e(f.reason)+'</p><h4>需要核实</h4><p class="insight-prose">'+e(f.question)+'</p><h4>可选下一步</h4><p class="insight-prose">'+e(f.nextStep)+'</p><p class="hint">核对了 '+Number(f.coverage?.sampledSources||0)+' / '+Number(f.coverage?.totalSources||0)+' 条来源的代表片段，不是全文穷举。</p>'+history+(f.stale?'<p class="hint">等待主题页和知识发现重新分析后，可继续核对。</p>':'<label class="field">我的核对说明<textarea id="insight-review-note" maxlength="1000" rows="4" placeholder="例如：适用于不同阶段，并非真正矛盾">'+e(f.review.note)+'</textarea></label><div class="actions insight-review-actions">'+['followup','resolved','dismissed','pending'].map(s=>'<button class="'+(s==='followup'?'primary':'secondary')+'" data-insight-review="'+s+'">'+states[s]+'</button>').join('')+'</div>')+'</div>');
  el('insight-back').onclick=list;
  document.querySelectorAll('[data-insight-source]').forEach(b=>b.onclick=()=>{env.showRecord(b.dataset.insightSource);const body=document.querySelector('#editor .dialog-body'),back=document.createElement('button');back.type='button';back.className='text-button';back.textContent='← 返回知识发现';back.onclick=()=>detail(id);body.insertBefore(back,body.querySelector('.dialog-top').nextSibling);});
  document.querySelectorAll('[data-insight-review]').forEach(b=>b.onclick=()=>env.safe(async()=>{
   const note=el('insight-review-note').value.trim(),state=b.dataset.insightReview;if(state==='resolved'&&!note)return env.toast('请写下核对结论或适用条件');
   await env.api('reviewWikiInsight',{reportId:f.reportId,findingId:f.id,signature:f.signature,state,note,expectedVersion:f.review.updatedAt||0});
   env.state.wikiInsights=await env.api('getWikiInsights');detail(id);env.toast('已保存核对状态，原笔记未修改');
  }));
 }
 function refresh(){if(el('insight-entry')){el('insight-entry').outerHTML=entry();bindEntry();}if(el('insight-list'))list();/* Do not replace a review draft while background analysis finishes. */}
 root.WikiInsightsUI={init:value=>{env=value;},entry,bindEntry,open,refresh};
})(window);
