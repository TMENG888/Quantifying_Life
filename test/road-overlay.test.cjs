const test=require('node:test'),assert=require('node:assert/strict'),T=require('../app/src/main/assets/tracks.js');
test('road inference is display-only and validates coordinates and time',()=>{
 const p=(time,lon)=>({time,lon,lat:0,accuracy:5,session:'a',pathDistance:true});const input=[p(1000,0),p(11000,.0001),p(21000,.0002)],before=JSON.stringify(input),stats=T.summary(input);
 const value={routes:[{start:1000,end:21000,inferred:true,path:[{lat:0,lon:0},{lat:.0002,lon:.0001},{lat:0,lon:.0002}]}]};
 const roads=T.roadOverlays(value);assert.equal(roads.length,1);assert.equal(T.roadCovered(input[0],input[1],roads),true);assert.equal(T.roadCovered(input[1],p(30000,.0003),roads),false);assert.deepEqual(T.summary(input),stats);assert.equal(JSON.stringify(input),before);
 assert.equal(T.roadOverlays({routes:[{...value.routes[0],inferred:false}]}).length,0);assert.equal(T.roadOverlays({routes:[{...value.routes[0],path:[{lat:NaN,lon:0},{lat:0,lon:0}]}]}).length,0);assert.equal(T.roadOverlays({routes:[{...value.routes[0],end:1000}]}).length,0);assert.deepEqual(T.roadOverlays(null),[]);
});
