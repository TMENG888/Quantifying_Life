(function(root){
 'use strict';
 const node=typeof module==='object'&&module.exports,D=node?require('./domain.js'):root.LifeDomain;
 const basis='net_rest_already_deducted';
 const minutes=n=>{n=Math.max(0,Math.round(n));return n>=60?Math.floor(n/60)+' 小时'+(n%60?' '+n%60+' 分':''):n+' 分钟';};
 const stamp=s=>s?String(s).replace('T',' ').slice(0,16):'尚未记录';
 function amounts(value){return {grossMinutes:Math.round(value.gross),restMinutes:Math.round(value.rest),netMinutes:Math.round(value.net),netRestAlreadyDeducted:true};}
 function shift(r,period=null,now=new Date()){
  const whole=amounts(D.workMinutes(r,now.getTime())),range=period?amounts(D.workMinutesInRange(r,period.a,period.b,now.getTime())):whole;
  return {recordId:r.id,title:r.title,date:r.date,workStart:r.workStart,workEnd:r.workEnd||'',state:r.workEnd?'completed':(r.breaks||[]).some(b=>!b.end)?'resting':'working',computedAt:D.localTime(now),wholeShift:whole,inQueryRange:range,breaks:(r.breaks||[]).map(b=>({start:b.start,end:b.end||'',minutes:Math.round(Math.max(0,Math.min(r.workEnd?Date.parse(r.workEnd):now.getTime(),b.end?Date.parse(b.end):now.getTime())-Math.max(Date.parse(r.workStart),Date.parse(b.start)))/60000)}))};
 }
 function context(records,returned=records,period=null,now=new Date()){
  const works=records.filter(r=>r.type==='work'),raw=works.reduce((sum,r)=>{const v=period?D.workMinutesInRange(r,period.a,period.b,now.getTime()):D.workMinutes(r,now.getTime());sum.gross+=v.gross;sum.rest+=v.rest;sum.net+=v.net;return sum;},{gross:0,rest:0,net:0});
  return {basis,scope:period?period.start+' 至 '+period.end:'全部历史',computedAt:D.localTime(now),totals:{...amounts(raw),shiftCount:works.length,ongoingShiftCount:works.filter(r=>!r.workEnd).length},shifts:returned.filter(r=>r.type==='work').map(r=>shift(r,period,now)),rule:'净工时已扣除休息，严禁再次相减。整班时长与查询日期范围内时长分别列出；进行中班次只算到computedAt，保存字段durationMinutes可能过期，不作为当前计时依据。'};
 }
 // Recognize bounded factual questions only. Project/topic filters, advice, mutations
 // and mixed learning queries remain with the planner; do not silently broaden scope.
 function query(text,now=new Date()){
  let s=String(text||'').normalize('NFKC').replace(/\s/g,'');
  if(!/工作|上班|下班|休息|午休|工时|班次/.test(s)||!/多久|多少|多长|几小时|几分钟|时长|工时|时间|几点|什么时候|[?？]|[吗嘛么]|了没|是否|有没有|查询|查一下|查查|查看/.test(s))return null;
  if(/https?:|修改|更正|更新|改成|改为|删除|打卡|帮我记|请记|保存|补录|学习|文章|视频|建议|如何|怎么|为什么|原因|分析|规律|效率|经验|灵感|计划|明天|后天/.test(s))return null;
  const reference=s.match(/记录编号[:：]([\w-]+)/),all=/全部历史|所有|累计|总共|一直以来/.test(s);
  const period=D.questionPeriod(s,now)||(!all&&!reference?D.questionPeriod('今天',now):null);
  s=s.replace(/记录编号[:：][\w-]+/g,'').replace(/\d{4}-\d{2}-\d{2}/g,'').replace(/(?:\d{4}年)?[零一二两三四五六七八九十\d]{1,3}月[零一二两三四五六七八九十\d]{1,3}(?:日|号)?/g,'').replace(/(?:上上个月|上上月|上个月|上月|这个月|本月|这月)[零一二两三四五六七八九十\d]{1,3}(?:日|号)/g,'');
  s=s.replace(/(?:什么时候|是否已经|有没有|全部历史|一直以来|最近一周|近七天|近7天|上星期|上周|本星期|这星期|本周|这周|上个月|上月|这个月|本月|今天|今日|昨天|昨日|前天|查一下|查询|查看|告诉我|帮我|请问|请|我的|我|目前|现在|已经|记录|工作|上班|下班|休息|午休|工时|班次|净|总|时长|时间|累计|总共|所有|合计|多久|多长|几点|开始|结束|扣除|扣掉|扣|后的|后|过|还|正在|是否|没有|有|吗|嘛|么|呢|没|了|的|是|和|与|及|、|至|到|~|—|–|[?？。，,！!：:])/g,'');
  s=s.replace(/(?:多少|几小时|几分钟|小时|分钟|一共|总计|查查|可以|能否|能|一下|啊|呀|吧)/g,'');if(s)return null;
  const status=/下班|上班/.test(text)&&/[吗嘛么]|了没|是否|有没有|[?？]/.test(text)&&!/多久|多长|时长|工时|几点|什么时候|时间/.test(text);
  return {kind:status?'status':/休息|午休/.test(text)&&!/工作|上班|下班|工时/.test(text)?'rest':'time',period,recordId:reference?.[1]||''};
 }
 function selected(records,q,now){return records.filter(r=>r.type==='work'&&(!q.recordId||r.id===q.recordId)&&(!q.period||D.workMinutesInRange(r,q.period.a,q.period.b,now.getTime()).gross>0||(r.date===q.period.start&&!r.workEnd&&Date.parse(r.workStart)<=now.getTime()))).sort((a,b)=>a.workStart.localeCompare(b.workStart));}
 function line(r,f,period,restOnly=false){
  const cross=period&&(r.workStart.slice(0,10)<period.start||(r.workEnd||f.computedAt).slice(0,10)>period.end);
  const schedule=`${stamp(r.workStart)} 上班，${r.workEnd?stamp(r.workEnd)+' 下班':'尚未记录下班'}`;
  const breaks=f.breaks.map(b=>`${stamp(b.start)}–${b.end?stamp(b.end):'仍在休息'}（${minutes(b.minutes)}）`).join('；');
  if(restOnly)return breaks?`${r.date}：${breaks}`:`${r.date}：没有已记录的休息时段。`;
  return `${schedule}。${breaks?'\n休息：'+breaks+'。':''}${cross?'\n整班净工作 '+minutes(f.wholeShift.netMinutes)+'；查询范围内净工作 '+minutes(f.inQueryRange.netMinutes)+'。':''}`;
 }
 function reply(q,records,now=new Date()){
  const rows=selected(records,q,now),scope=q.period?(q.period.start===q.period.end?q.period.start:q.period.start+' 至 '+q.period.end):'全部历史';
  if(!rows.length)return {answer:`${scope} 没有找到${q.recordId?'该编号的':'已保存的'}工作班次，无法据此确认上下班状态或工时；未记录不代表没有工作。`,citations:[]};
  const facts=context(rows,rows.slice(0,8),q.period,now),t=facts.totals,open=rows.filter(r=>!r.workEnd),parts=[];
  if(q.kind==='status')parts.push(open.length?`该范围有 **${open.length} 个尚未记录下班的班次**${open.some(r=>(r.breaks||[]).some(b=>!b.end))?'，其中有正在休息的班次':''}。`:`该范围的 **${rows.length} 个班次均已记录下班**。`);
  if(q.kind==='rest')parts.push(`${scope} 已记录休息合计 **${minutes(t.restMinutes)}**。`);
  else parts.push(`${scope} 净工作 **${minutes(t.netMinutes)}**（在岗总时长 ${minutes(t.grossMinutes)}，已扣除休息 ${minutes(t.restMinutes)}，不再重复扣减）。`);
  parts.push(...rows.slice(0,8).map((r,i)=>line(r,facts.shifts[i],q.period,q.kind==='rest')));
  if(rows.length>8)parts.push(`汇总覆盖 ${rows.length} 个班次，上面只展示前 8 个班次明细。`);
  if(open.length)parts.push('进行中的时长截至 '+stamp(facts.computedAt)+'，不是最终工时。');
  return {answer:parts.join('\n\n'),citations:rows.slice(0,8).map(r=>r.id)};
 }
 function completedReply(r,now=new Date()){
  const f=shift(r,null,now);return {answer:`这条班次已记录 ${stamp(r.workEnd)} 下班，不需要重复打卡，本次未更改记录。\n\n上班 ${stamp(r.workStart)}；在岗总时长 ${minutes(f.wholeShift.grossMinutes)}，休息 ${minutes(f.wholeShift.restMinutes)}，净工作 **${minutes(f.wholeShift.netMinutes)}**（已扣休息）。\n\n如果原下班时间不对，请说“把这条班次的下班时间改为……”并提供实际日期和时间，核对后再确认。`,citations:[r.id]};
 }
 function completedError(r,now=new Date()){const result=completedReply(r,now),err=Error(result.answer);err.workStatus=true;err.citations=result.citations;return err;}
 // Numeric personal work claims are replaced, not merely accompanied by a warning.
 // Interpretive paragraphs and general advice remain; exact facts come from local data.
 function guard(answer,facts){
  if(!facts)return {answer,sourceIds:[]};
  const numeric=/(?:\d|[零一二两三四五六七八九十百])\s*(?:小时|分钟|分(?:钟)?|天)|\d{1,2}[:：]\d{2}/;
  const personal=/净工|工时|在岗|上班|下班|午休|班次|休息(?:合计|时长|时间)|工作/;
  let replaced=false;const paragraphs=String(answer||'').split(/\n\s*\n/).filter(p=>{const plain=p.replace(/[*_`]/g,'');if(!facts.totals.shiftCount&&!/你|已记录|实际记录|本地汇总/.test(plain))return true;if(numeric.test(plain)&&personal.test(plain)&&!(/建议|例如|比如/.test(plain)&&!/(?:你|已记录|实际|净工|工时(?:为|是|约))/.test(plain))){replaced=true;return false;}return true;});
  if(!replaced)return {answer,sourceIds:[]};
  const t=facts.totals,detail=facts.shifts.map(f=>`${stamp(f.workStart)} 上班，${f.workEnd?stamp(f.workEnd)+' 下班':'尚未记录下班'}；整班净工作 ${minutes(f.wholeShift.netMinutes)}。`);
  const block=t.shiftCount?`工时核对（本地计算，${facts.scope}）：净工作 **${minutes(t.netMinutes)}**，在岗总时长 ${minutes(t.grossMinutes)}，休息 ${minutes(t.restMinutes)}；净工时已扣休息，不再相减。${t.ongoingShiftCount?'进行中班次截至 '+stamp(facts.computedAt)+'。':''}`:`${facts.scope} 没有找到已保存的工作班次，不能据此判断实际工时为零。`;
  return {answer:[block,...detail.slice(0,4),...paragraphs].join('\n\n'),sourceIds:facts.shifts.slice(0,4).map(f=>f.recordId)};
 }
 const api={basis,minutes,shift,context,query,reply,completedReply,completedError,guard};if(node)module.exports=api;else root.WorkFacts=api;
})(typeof window!=='undefined'?window:globalThis);
