const test=require('node:test'),assert=require('node:assert/strict'),T=require('../app/src/main/assets/tracks.js');
const base=Date.parse('2026-11-03T09:00:00+08:00');
const point=(id,seconds,extra={})=>({id,time:base+seconds*1000,lat:31.23,lon:121.47+id*.00001,accuracy:650,reportedAccuracy:650,provider:'network',session:'raw-session',raw:true,filtered:false,motionState:'raw',...extra});

test('raw drawing keeps high-error, mocked, ineligible and duplicate-time points sorted by time/id without changing source',()=>{
 const input=[point(4,5),point(3,0,{mocked:true,eligible:false}),point(1,0,{accuracy:1000,breakBefore:true}),point(2,0,{eligible:false})],before=JSON.stringify(input);
 const rendered=T.segments(input).flat();assert.deepEqual(rendered.map(p=>p.id),[1,2,3,4]);assert.equal(rendered.length,input.length);assert.equal(JSON.stringify(input),before);
 assert.equal(T.valid(point(5,6,{accuracy:1000.01})),false);assert.equal(T.valid(point(5,6,{raw:false})),false,'processed validation remains unchanged');
 assert.deepEqual(T.breaks(input).map(g=>g.reason),['重复时间','重复时间']);assert.equal(T.bridges(input).length,2);
});

test('raw continuity ignores speed and processing break flags but retains session and 60-second boundary',()=>{
 const a=point(1,0),b=point(2,1,{lat:32.23,breakBefore:true});
 assert.equal(T.connected(a,b),true);assert.equal(T.connected({...a,raw:false,accuracy:8},{...b,raw:false,accuracy:8}),false);
 assert.equal(T.connected(a,point(3,60)),true);assert.equal(T.connected(a,point(3,60.001)),false);assert.equal(T.connected(a,point(3,0)),false);assert.equal(T.connected(a,point(3,1,{session:'other'})),false);
});

test('raw length sums every continuous straight segment including small drift, excludes dashed gaps and derives no motion or stops',()=>{
 const input=[point(1,0),point(2,10),point(3,100),point(4,110,{session:'new'}),point(5,120,{session:'new'})];
 const summary=T.summary(input),expected=Math.round(T.distance(input[0],input[1])+T.distance(input[3],input[4]));
 assert.equal(summary.meters,expected);assert.ok(summary.meters>0);assert.equal(summary.pointCount,5);assert.equal(summary.gaps,2);assert.equal(summary.movingMinutes,null);assert.deepEqual(summary.stops,[]);
 const stationary=Array.from({length:130},(_,i)=>point(i,i*5,{lon:121.47}));assert.deepEqual(T.summary(stationary).stops,[]);assert.equal(T.summary(stationary).movingMinutes,null);
});

test('raw renderer data retains all points beyond the processed 20000-point simplification threshold',()=>{
 const input=Array.from({length:25001},(_,i)=>point(i,i,{eligible:false,mocked:i%2===0}));
 assert.equal(T.segments(input).flat().length,input.length);assert.equal(T.summary(input).pointCount,input.length);
});
