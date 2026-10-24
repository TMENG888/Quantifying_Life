const {test}=require('node:test'),assert=require('node:assert/strict'),T=require('../app/src/main/assets/tracks.js');
const road=(start,end)=>({inferred:true,start,end,path:[{lat:31,lon:121},{lat:31.001,lon:121.001}]});
test('time range shows only complete road inferences within the selected half-open interval',()=>{
  const value={routes:[road(10,20),road(5,15),road(15,25),road(11,19),road(21,30)]};
  assert.deepEqual(T.roadsInTime(value,{mode:'range',start:10,end:20}).map(r=>[r.start,r.end]),[[11,19]]);
});
test('moment never shows road geometry; all-day keeps original overlays',()=>{
  const value={routes:[road(10,20)]};assert.deepEqual(T.roadsInTime(value,{mode:'moment'}),[]);
  assert.deepEqual(T.roadsInTime(value),T.roadOverlays(value));assert.deepEqual(T.roadsInTime(value,{mode:'all'}),T.roadOverlays(value));
});
test('unapplied/failed selection cannot leak all-day road overlays',()=>{
  assert.deepEqual(T.roadsInTime({routes:[road(10,20)]},{}),[]);
  assert.deepEqual(T.roadsInTime({routes:[road(10,20)]},{mode:'range'}),[]);
});
test('time display selection never mutates source data or changes mileage semantics',()=>{
  const value={routes:[road(10,20)]},before=JSON.stringify(value);T.roadsInTime(value,{mode:'range',start:5,end:30});assert.equal(JSON.stringify(value),before);
  const p=(time,lat,extra={})=>({time,lat,lon:121,accuracy:8,session:'s',pathDistance:true,...extra});
  const points=[p(100000,31),p(105000,31.0001),p(220000,31.01,{breakBefore:true}),p(225000,31.0101)],snapshot=JSON.stringify(points);
  const slice=points.filter(p=>p.time>=105000&&p.time<225000);
  assert.equal(T.summary(slice).meters,0);assert.equal(T.bridges(slice).length,1);assert.equal(T.bridges(slice)[0].inferred,true);assert.equal(JSON.stringify(points),snapshot);
});
