(function(root){
  'use strict';
  const GAP=60000,R=6371008.8;
  function valid(p){return p&&Number.isFinite(p.lat)&&Number.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180&&Number.isFinite(p.time)&&p.time>0&&Number.isFinite(p.accuracy)&&p.accuracy>0&&p.accuracy<=100&&typeof p.session==='string'&&p.session.length>0;}
  function distance(a,b){const rad=Math.PI/180,x=(b.lat-a.lat)*rad,y=(b.lon-a.lon)*rad;const h=Math.sin(x/2)**2+Math.cos(a.lat*rad)*Math.cos(b.lat*rad)*Math.sin(y/2)**2;return R*2*Math.asin(Math.sqrt(Math.min(1,h)));}
  function connected(a,b){const dt=b.time-a.time;return !b.breakBefore&&a.session===b.session&&dt>0&&dt<=GAP&&distance(a,b)/(dt/1000)<=100;}
  function segments(input){const points=input.filter(valid).sort((a,b)=>a.time-b.time),out=[];let current=[];for(const p of points){if(current.length&&!connected(current[current.length-1],p)){out.push(current);current=[];}current.push(p);}if(current.length)out.push(current);return out;}
  function breaks(input){const parts=segments(input),out=[];for(let i=1;i<parts.length;i++){const a=parts[i-1][parts[i-1].length-1],b=parts[i][0],dt=b.time-a.time,uncertain=(a.motionState==='uncertain'||b.motionState==='uncertain')&&a.session===b.session&&dt>0&&dt<=GAP,last=out[out.length-1];
    // One uncertain interval, not hundreds of gap badges. Keep every intermediate
    // observation in chronological order so the dashed path cannot skip a turn.
    if(uncertain&&last?.uncertain&&last.b===a){last.b=b;last.path.push(b);last.seconds=Math.max(0,Math.round((b.time-last.a.time)/1000));continue;}
    out.push({a,b,seconds:Math.max(0,Math.round(dt/1000)),reason:uncertain?'位置待确认':a.session!==b.session?'记录会话变化':b.breakBefore?'定位不可信 / 过滤断点':dt<=0?'重复时间':dt>GAP?'有效采样间隔超过60秒':'异常位置跳移',...(uncertain?{uncertain:true,path:[a,b]}:{})});}return out;}
  // Display-only chronological connectors between every pair of consecutive recorded segments.
  // Includes long gaps, session changes and filtered breaks; never used for mileage, storage or GPX.
  function bridges(input){return breaks(input).map((gap,gapIndex)=>({...gap,gapIndex,inferred:true}));}
  function summary(input){const parts=segments(input),points=parts.flat();let meters=0,movingMs=0,observedMs=0;const stops=[];
    for(const part of parts){let anchor=0;for(let i=1;i<part.length;i++){
      const a=part[i-1],b=part[i],dt=b.time-a.time,d=distance(a,b),noise=Math.max(15,Math.min(60,a.accuracy+b.accuracy));observedMs+=dt;
      if((a.denoised&&b.denoised||a.pathDistance&&b.pathDistance)?d>0:d>noise){meters+=d;if(d/(dt/1000)>=0.5)movingMs+=dt;}
      if(distance(part[anchor],b)>100){const last=part[i-1];if(last.time-part[anchor].time>=600000)stops.push({start:part[anchor].time,end:last.time,lat:part[anchor].lat,lon:part[anchor].lon});anchor=i;}
    }if(part.length&&part[part.length-1].time-part[anchor].time>=600000)stops.push({start:part[anchor].time,end:part[part.length-1].time,lat:part[anchor].lat,lon:part[anchor].lon});}
    const intervals=[];for(let i=1;i<points.length;i++)if(points[i].session===points[i-1].session&&points[i].time>points[i-1].time)intervals.push((points[i].time-points[i-1].time)/1000);intervals.sort((a,b)=>a-b);
    return {pointCount:points.length,segments:parts.length,gaps:breaks(points).length,medianSampleSeconds:intervals.length?Math.round(intervals[Math.floor(intervals.length/2)]):null,meters:Math.round(meters),movingMinutes:Math.round(movingMs/60000),observedMinutes:Math.round(observedMs/60000),stops,firstTime:points[0]?.time||null,lastTime:points[points.length-1]?.time||null,mocked:points.some(p=>p.mocked),note:'位置采样估算；点间连线不是道路还原。超过60秒、信号不可信或暂停的缺口仅作虚线连接，不计距离。移动不等于运动，缺失位置不等于静止。'};
  }
  function project(p,z){const n=256*2**z,lat=Math.max(-85.05112878,Math.min(85.05112878,p.lat)),s=Math.sin(lat*Math.PI/180);return {x:(p.lon+180)/360*n,y:(0.5-Math.log((1+s)/(1-s))/(4*Math.PI))*n};}
  function bounds(points,z){let minX=Infinity,maxX=-Infinity,minY=Infinity,maxY=-Infinity;for(const p of points){const v=project(p,z);minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);}return {minX,maxX,minY,maxY};}
  function fit(points,width,height){const good=points.filter(valid);if(!good.length)return {z:12,x:0,y:0};let z=16;
    for(;z>2;z--){const b=bounds(good,z);if(b.maxX-b.minX<width-70&&b.maxY-b.minY<height-70)break;}
    const b=bounds(good,z);return {z,x:(b.minX+b.maxX)/2,y:(b.minY+b.maxY)/2};
  }
  // Separate, display-only inference. Never feeds summary(), segments(), or exports.
  function roadOverlays(value){return (Array.isArray(value?.routes)?value.routes:[]).filter(r=>r.inferred===true&&Number.isFinite(r.start)&&Number.isFinite(r.end)&&r.end>r.start&&Array.isArray(r.path)&&r.path.length>=2&&r.path.length<=20000&&r.path.every(p=>Number.isFinite(p.lat)&&Number.isFinite(p.lon)&&Math.abs(p.lat)<=85&&Math.abs(p.lon)<=180)).sort((a,b)=>a.start-b.start);}
  function roadCovered(a,b,routes){return b.time>a.time&&routes.some(r=>a.time>=r.start&&b.time<=r.end);}
  // Road geometry has no per-vertex time. Never pretend to clip a partial route.
  function roadsInTime(value,selection){const roads=roadOverlays(value);if(!selection||selection.mode==='all')return roads;if(selection.mode!=='range')return [];return roads.filter(r=>r.start>=selection.start&&r.end<selection.end);}
  const api={valid,distance,connected,segments,breaks,bridges,summary,project,fit,roadOverlays,roadCovered,roadsInTime};if(typeof module==='object'&&module.exports)module.exports=api;else root.LifeTracks=api;
})(typeof window!=='undefined'?window:globalThis);
