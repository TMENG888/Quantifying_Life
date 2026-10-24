const test=require('node:test'),assert=require('node:assert/strict'),A=require('../app/src/main/assets/agent-actions.js');
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
A.init({esc});const now=new Date('2026-10-21T23:00:00');
test('all four clock cards show only action, exact date/time and confirm/cancel',()=>{
 let records=[];for(const [kind,at,label] of [['start_work','09:00:00','上班'],['start_break','12:00:00','开始休息'],['end_break','13:00:00','结束休息'],['end_work','18:00:00','下班']]){
  const a=A.prepare([{kind,at:'2026-10-21T'+at}],records,records.map(r=>r.id),now)[0],html=A.card(a,3);
  assert.match(html,/clock-candidate/);assert.ok(html.includes(at)&&html.includes('2026-10-21'));assert.ok(html.includes('确认'+label));assert.match(html,/data-confirm-change="3"/);assert.match(html,/data-drop-change="3">取消/);
  assert.doesNotMatch(html,/<details|查看修改前后|标题：|标签：|内容：|时长：|工作班次|尚未写入/);assert.equal(A.changeReply([a]),'请确认'+label+'时间。');records=[a.after];
 }
});
test('cross-midnight card shows event date and shift origin; end while resting discloses both effects',()=>{
 const r={id:'s',type:'work',date:'2026-10-20',title:'班次',note:'',workStart:'2026-10-20T22:00:00',workEnd:'',breaks:[{start:'2026-10-20T23:00:00',end:''}],tags:[],durationMinutes:0};
 const a=A.prepare([{kind:'end_work',at:'2026-10-21T01:00:00'}],[r],['s'],now)[0],html=A.card(a,0);
 assert.match(html,/2026-10-21/);assert.match(html,/01:00:00/);assert.match(html,/班次始于 2026-10-20 22:00:00/);assert.match(html,/同时结束本次休息/);assert.equal(r.workEnd,'');
});
test('ordinary edits and deletion retain before-after disclosure and delete safeguards',()=>{
 const r={id:'l',type:'learning',date:'2026-10-21',title:'<b>原文</b>',note:'原笔记',tags:[],durationMinutes:20};
 const a=A.prepare([{kind:'update_record',recordId:'l',appendNote:'新笔记'}],[r],['l'],now)[0],html=A.card(a,0);
 assert.match(html,/<details>/);assert.match(html,/原笔记/);assert.match(html,/新笔记/);assert.match(html,/&lt;b&gt;/);assert.doesNotMatch(html,/<b>原文/);
 const del=A.prepare([{kind:'delete_record',recordId:'l'}],[r],['l'],now)[0];assert.match(A.card(del,1),/删除需再次确认/);assert.match(A.card(del,1),/确认删除/);assert.match(A.changeReply([a,del]),/2项变更/);
});
