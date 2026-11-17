const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const T=require('../app/src/main/assets/tracks.js'),M=require('../app/src/main/assets/map-tiles.js'),G=require('../app/src/main/assets/map-gestures.js');

// Run the real TrackUI lifecycle and pointer handlers with a minimal canvas DOM.
// API results remain asynchronous, just like native replies, including late photos.
function harness(options={}){
 const nodes=new Map(),intervals=new Map(),frames=new Map(),calls=[],payloads=[];let tick=100000,next=0,photos=[],photoReply=null,roadReply=null,mode='offline',filter=options.filter||'motion65',timeFilter=null;
 const selected='2026-11-02',base=new Date(selected+'T09:00:00').getTime();
 let points=options.points||[{id:1,lat:31.23,lon:121.47,time:base,accuracy:8,session:'qa',provider:'network'},{id:2,lat:31.231,lon:121.471,time:base+10000,accuracy:8,session:'qa',provider:'fused'}];
 const noop=()=>{},ctx=new Proxy({measureText:()=>({width:60})},{get:(o,k)=>o[k]||noop,set:(o,k,v)=>(o[k]=v,true)});
 function element(id=''){
  const n={id,dataset:{},style:{},clientWidth:375,clientHeight:320,tagName:'DIV',children:[],textContent:'',value:'',contains:()=>false,getBoundingClientRect:()=>({left:0,top:0}),getContext:()=>ctx,setPointerCapture:noop,scrollIntoView:noop,setAttribute:noop,remove:noop,appendChild(x){this.children.push(x);},append(x){this.children.push(x);},querySelector:()=>element(),querySelectorAll:()=>[],insertAdjacentHTML(where,html){parse(html);}};
  Object.defineProperty(n,'innerHTML',{get:()=>n.html||'',set(html){n.html=html;if(id==='track-root'){for(const key of nodes.keys())if(key!=='track-root')nodes.delete(key);}parse(html);}});
  return n;
 }
 function parse(html){for(const match of html.matchAll(/<[^>]*\bid="([^"]+)"[^>]*>/g)){if(!nodes.has(match[1]))nodes.set(match[1],element(match[1]));const n=nodes.get(match[1]);n.disabled=/\bdisabled\b/.test(match[0]);n.value=(/\bvalue="([^"]*)"/.exec(match[0])||[])[1]||'';}for(const match of html.matchAll(/data-track-time-mode="([^"]+)"/g)){const n=element();n.dataset.trackTimeMode=match[1];nodes.set('time-'+match[1],n);}}
 const doc={hidden:false,activeElement:null,getElementById:id=>nodes.get(id)||null,addEventListener:noop,createElement:()=>element(),querySelector:s=>s==='.track-map-frame'?nodes.get('track-frame'):s==='.track-map-credit'?nodes.get('track-credit'):null,querySelectorAll:s=>s==='[data-track-time-mode]'?[...nodes].filter(([k])=>k.startsWith('time-')).map(([,v])=>v):[]};
 // These two class containers survive only in the test DOM; real children are recreated.
 nodes.set('track-root',element('track-root'));const frame=element('track-frame'),credit=element('track-credit');
 doc.querySelector=s=>s==='.track-map-frame'?frame:s==='.track-map-credit'?credit:null;
 const timers={setInterval(fn){const id=++next;intervals.set(id,fn);return id;},clearInterval:id=>intervals.delete(id)};
 const context={window:{scrollY:0,scrollTo:noop},document:doc,LifeTracks:T,LifeMapTiles:M,LifeMapGestures:G,LifeDomain:{localDate:()=>selected},devicePixelRatio:1,Date:class extends Date{static now(){return tick;}},...timers,setTimeout:noop,requestAnimationFrame:fn=>{const id=++next;frames.set(id,fn);return id;},cancelAnimationFrame:id=>frames.delete(id)};
 vm.runInNewContext(fs.readFileSync(__dirname+'/../app/src/main/assets/track-ui.js','utf8'),context);
 const ui=context.window.TrackUI;
 ui.init({toast:()=>{},confirm:async()=>true,safe:fn=>fn(),api:async(action,payload)=>{
  calls.push(action);payloads.push({action,payload});
  const visible=()=>points.map(p=>filter==='raw'?{...p,raw:true,filtered:false,motionState:'raw'}:{...p});
  if(action==='trackDay')return {date:payload.date,points:visible(),dates:[selected],status:{mapMode:mode},filter:{id:filter},roadMatching:options.roads,totalPoints:options.totalPoints??points.length,truncated:options.truncated};
  if(action==='trackRoadStatus')return {configured:true,enabled:true,networkId:'qa'};
  if(action==='trackRoadMatch')return roadReply?await roadReply:options.roads||{routes:[]};
  if(action==='trackPhotos'){if(photoReply)return await photoReply;return {date:payload.date,enabled:true,readable:true,photos};}
  if(action==='trackConfig'){mode=payload.mapMode||mode;filter=payload.filterMode||filter;return {mapMode:mode};}
  if(action==='trackMapView')return {};
  if(action==='trackTile')throw Error('Synthetic no-map fixture');
  if(action==='trackTime')return {points:visible(),processedPoints:points.length,target:points[0].time,match:visible()[0],filter:{id:timeFilter||filter}};
  throw Error('Unexpected API '+action);
 }});
 const settle=async()=>{for(let i=0;i<16;i++)await new Promise(setImmediate);};
 const camera=()=>{const d=nodes.get('track-map').dataset;return ['zoom','centerX','centerY'].map(k=>Number(d[k]));};
 function flush(){for(const [id,fn]of [...frames]){frames.delete(id);fn();}}
 function pointer(type,id,x,y){const c=nodes.get('track-map');c['onpointer'+type]({button:0,pointerId:id,clientX:x,clientY:y,preventDefault:noop});flush();}
 function pinch(){pointer('down',1,90,160);pointer('down',2,190,160);pointer('move',1,40,160);pointer('move',2,240,160);pointer('up',2,240,160);pointer('move',1,65,180);pointer('up',1,65,180);}
 return {ui,nodes,calls,payloads,settle,camera,pinch,pointer,points,async mount(){ui.page();ui.mount();await settle();},async refresh(){for(const fn of [...intervals.values()])fn();await settle();},async selectFilter(value){await nodes.get('track-filter').onchange({target:{value}});await settle();},async selectTime(value){nodes.get('time-'+value).onclick();await settle();},setTimeFilter:value=>timeFilter=value,newPoint(){points.push({...points.at(-1),lat:31.25,lon:121.49,time:base+20000});},async photos(value){photos=value;tick+=61000;ui.photosChanged();await settle();},deferRoad(){let resolve;roadReply=new Promise(r=>resolve=r);return value=>{resolve(value);roadReply=null;};},deferPhoto(){let resolve;photoReply=new Promise(r=>resolve=r);return value=>{resolve({date:selected,enabled:true,readable:true,photos:value});photoReply=null;};}};
}

test('manual pinch and pan persist through 15-second refresh, new samples, page remount, road and photo replies',async()=>{
 const h=harness();await h.mount();assert.equal(h.nodes.has('track-zoom-in'),false);assert.equal(h.nodes.has('track-zoom-out'),false);
 const initial=h.camera();h.pinch();const manual=h.camera();assert.ok(manual[0]>initial[0]+.9);
 h.newPoint();await h.refresh();assert.deepEqual(h.camera(),manual);assert.ok(h.calls.filter(x=>x==='trackDay').length>=2,'refresh remains active');
 await h.mount();assert.deepEqual(h.camera(),manual,'ordinary parent render keeps same date viewport');
 await h.photos([{id:'far',lat:33,lon:123,time:Date.now()}]);assert.deepEqual(h.camera(),manual,'late photo refresh must not reframe a manually moved map');
 h.ui.mapImported();await h.settle();assert.deepEqual(h.camera(),manual,'new bottom map and road data retain viewport');
 await h.nodes.get('track-map-mode').onchange({target:{value:'online'}});await h.settle();assert.deepEqual(h.camera(),manual,'base-map switching retains viewport');
 h.nodes.get('track-fit').onclick();assert.notDeepEqual(h.camera(),manual,'explicit whole-route button still reframes');
 h.pinch();const zoomed=h.camera();h.nodes.get('track-date').onchange({target:{value:'2026-11-01'}});await h.settle();assert.notDeepEqual(h.camera(),zoomed,'changing date explicitly fits the selected route');assert.ok(h.camera()[0]<zoomed[0]);
});

test('in-flight initial photo response cannot steal manual camera after a page remount',async()=>{
 const h=harness(),finish=h.deferPhoto();await h.mount();h.pinch();const manual=h.camera();await h.mount();
 finish([{id:'late',lat:34,lon:125,time:Date.now()}]);await h.settle();assert.deepEqual(h.camera(),manual);
});

test('initial photos still join automatic bounds before any map interaction',async()=>{
 const h=harness();await h.mount();const initial=h.camera();await h.photos([{id:'first',lat:32,lon:122,time:Date.now()}]);assert.notDeepEqual(h.camera(),initial);
});

const roadFixture={message:'Synthetic road match',routes:[{inferred:true,start:Date.parse('2026-11-02T09:00:00'),end:Date.parse('2026-11-02T09:00:10'),path:[{lat:31.23,lon:121.47},{lat:31.231,lon:121.471}]}]};
test('raw mode skips road matching and ignores existing road overlays while preserving preference and manual camera',async()=>{
 const h=harness({filter:'raw',roads:roadFixture,totalPoints:123000,truncated:true});await h.mount();
 assert.equal(h.calls.includes('trackRoadMatch'),false);assert.equal(h.nodes.get('track-map').dataset.roadRoutes,'0');assert.equal(h.nodes.get('track-road-enabled').disabled,true);assert.equal(h.nodes.get('track-import-roads').disabled,true);
 const html=h.nodes.get('track-root').innerHTML;assert.match(html,/原始连线长度/);assert.match(html,/显示 2 \/ 已保存 123000 个原始点/);assert.match(html,/超过当天查看上限，数据仍保存在本机/);assert.doesNotMatch(html,/处理后 2 个点|停留估计 0 点|未纳入轨迹/);
 await h.nodes.get('track-road-enabled').onchange({target:{checked:false}});assert.equal(h.calls.includes('trackRoadConfig'),false);
 h.pinch();const manual=h.camera();await h.refresh();await h.mount();await h.photos([{id:'late',lat:34,lon:125,time:Date.now()}]);assert.deepEqual(h.camera(),manual);
 await h.selectFilter('motion65');assert.ok(h.calls.includes('trackRoadMatch'),'returning to processed mode restores matching from saved preference');assert.equal(h.nodes.get('track-road-enabled').disabled,false);assert.equal(h.nodes.get('track-map').dataset.roadRoutes,'1');
});

test('road reply started before switching to raw cannot repaint inferred geometry or stale status',async()=>{
 const h=harness(),finish=h.deferRoad();await h.mount();assert.equal(h.calls.filter(c=>c==='trackRoadMatch').length,1);
 await h.selectFilter('raw');finish(roadFixture);await h.settle();assert.equal(h.calls.filter(c=>c==='trackRoadMatch').length,1);assert.equal(h.nodes.get('track-map').dataset.roadRoutes,'0');assert.doesNotMatch(h.nodes.get('track-road-message').textContent,/Synthetic/);
});

test('raw range and moment keep all returned observations and enforce filter consistency',async()=>{
 const h=harness({filter:'raw'});await h.mount();await h.selectTime('range');
 assert.equal(h.nodes.get('track-map').dataset.pointCount,'2');assert.equal(h.payloads.find(p=>p.action==='trackTime').payload.filterMode,'raw');assert.equal(h.calls.includes('trackRoadMatch'),false);
 await h.selectTime('moment');assert.equal(h.nodes.get('track-map').dataset.timeMode,'moment');assert.match(h.nodes.get('track-selected-point').textContent,/来源：network/);
 h.setTimeFilter('motion65');await h.selectTime('range');assert.equal(h.nodes.get('track-map').dataset.pointCount,'0');assert.match(h.nodes.get('track-root').innerHTML,/处理方案已变化/);
});

test('overlapping raw observations can each be selected and show true provider, accuracy and point id',async()=>{
 const time=Date.parse('2026-11-02T09:00:00'),p={time,lat:31.23,lon:121.47,session:'qa',accuracy:650,eligible:false};
 const h=harness({filter:'raw',points:[{...p,id:1,provider:'network'},{...p,id:2,provider:'fused',mocked:true}]});await h.mount();
 const tap=()=>{h.pointer('down',1,187.5,160);h.pointer('up',1,187.5,160);};
 assert.equal(h.nodes.get('track-map').dataset.pointCount,'2');tap();assert.match(h.nodes.get('track-selected-point').textContent,/650米 · 来源：network.*点 1/);tap();assert.match(h.nodes.get('track-selected-point').textContent,/来源：fused · 模拟定位.*点 2/);
});
