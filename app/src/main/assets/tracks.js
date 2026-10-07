(function(root){
  'use strict';
  const GAP=60000,R=6371008.8;
  function valid(p){return p&&Number.isFinite(p.lat)&&Number.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180&&Number.isFinite(p.time)&&p.time>0&&Number.isFinite(p.accuracy)&&p.accuracy>0&&p.accuracy<=100&&typeof p.session==='string'&&p.session.length>0;}
  function distance(a,b){const rad=Math.PI/180,x=(b.lat-a.lat)*rad,y=(b.lon-a.lon)*rad;const h=Math.sin(x/2)**2+Math.cos(a.lat*rad)*Math.cos(b.lat*rad)*Math.sin(y/2)**2;return R*2*Math.asin(Math.sqrt(Math.min(1,h)));}
  function connected(a,b){const dt=b.time-a.time;return !b.breakBefore&&a.session===b.session&&dt>0&&dt<=GAP&&distance(a,b)/(dt/1000)<=100;}
  function segments(input){const points=input.filter(valid).sort((a,b)=>a.time-b.time),out=[];let current=[];for(const p of points){if(current.length&&!connected(current[current.length-1],p)){out.push(current);current=[];}current.push(p);}if(current.length)out.push(current);return out;}
  function breaks(input){const parts=segments(input),out=[];for(let i=1;i<parts.length;i++){const a=parts[i-1][parts[i-1].length-1],b=parts[i][0],dt=b.time-a.time;out.push({a,b,seconds:Math.max(0,Math.round(dt/1000)),reason:a.session!==b.session?'记录会话变化':b.breakBefore?'定位不可信 / 过滤断点':dt<=0?'重复时间':dt>GAP?'有效采样间隔超过60秒':'异常位置跳移'});}return out;}
  // Display-only hypotheses. Never pass these to segments(), summary(), native storage or GPX.
  function bridges(input){const parts=segments(input),out=[];
    function quality(p){const acc=p.reportedAccuracy??p.accuracy;return Number.isFinite(acc)&&acc>0&&acc<=35;}
    function gpsSpeed(p){return Number.isFinite(p.reportedSpeed)&&p.reportedSpeed>=0&&p.reportedSpeed<=100&&Number.isFinite(p.speedAccuracy)&&p.speedAccuracy>=0&&p.speedAccuracy<=0.6?p.reportedSpeed:null;}
    function vector(a,b){let dl=b.lon-a.lon;dl=((dl+540)%360)-180;return {x:dl*Math.cos((a.lat+b.lat)*Math.PI/360),y:b.lat-a.lat};}
    function compatible(a,b,c,d){const u=vector(a,b),v=vector(c,d),n=Math.hypot(u.x,u.y)*Math.hypot(v.x,v.y);return n===0|| (u.x*v.x+u.y*v.y)/n>=-0.25;}
    for(let i=1;i<parts.length;i++){const left=parts[i-1],right=parts[i],a=left[left.length-1],b=right[0],dt=b.time-a.time,d=distance(a,b);
      if(a.session!==b.session||b.breakBefore||dt<=GAP||dt>120000||d<5||d>600||!a.denoised||!b.denoised||a.provider!=='gps'||b.provider!=='gps'||!quality(a)||!quality(b))continue;
      const before=left.length>1?left[left.length-2]:null,after=right.length>1?right[1]:null,edges=[];
      if(before)edges.push([before,a]);if(after)edges.push([b,after]);
      const moving=edges.filter(([p,q])=>distance(p,q)/( (q.time-p.time)/1000)>=0.3);
      const sa=gpsSpeed(a),sb=gpsSpeed(b),doppler=sa!==null&&sb!==null&&sa>=0.6&&sb>=0.6;
      if(sa!==null&&sb!==null&&sa<=0.35&&sb<=0.35)continue;
      // Without adjacent movement evidence this could be two stationary fixes, not a missing journey.
      if(!moving.length&&!doppler||moving.some(([p,q])=>!compatible(p,q,a,b)))continue;
      const pace=Math.max(doppler?(sa+sb)/2:0,...moving.map(([p,q])=>distance(p,q)/((q.time-p.time)/1000)));
      if(d/(dt/1000)>Math.max(1.5,pace*3)+(a.accuracy+b.accuracy)/(dt/1000))continue;
      out.push({a,b,gapIndex:i-1,seconds:Math.round(dt/1000),inferred:true});
    }return out;
  }
  function summary(input){const parts=segments(input),points=parts.flat();let meters=0,movingMs=0,observedMs=0;const stops=[];
    for(const part of parts){let anchor=0;for(let i=1;i<part.length;i++){
      const a=part[i-1],b=part[i],dt=b.time-a.time,d=distance(a,b),noise=Math.max(15,Math.min(60,a.accuracy+b.accuracy));observedMs+=dt;
      if(a.denoised&&b.denoised?d>0:d>noise){meters+=d;if(d/(dt/1000)>=0.5)movingMs+=dt;}
      if(distance(part[anchor],b)>100){const last=part[i-1];if(last.time-part[anchor].time>=600000)stops.push({start:part[anchor].time,end:last.time,lat:part[anchor].lat,lon:part[anchor].lon});anchor=i;}
    }if(part.length&&part[part.length-1].time-part[anchor].time>=600000)stops.push({start:part[anchor].time,end:part[part.length-1].time,lat:part[anchor].lat,lon:part[anchor].lon});}
    const intervals=[];for(let i=1;i<points.length;i++)if(points[i].session===points[i-1].session&&points[i].time>points[i-1].time)intervals.push((points[i].time-points[i-1].time)/1000);intervals.sort((a,b)=>a-b);
    return {pointCount:points.length,segments:parts.length,gaps:Math.max(0,parts.length-1),medianSampleSeconds:intervals.length?Math.round(intervals[Math.floor(intervals.length/2)]):null,meters:Math.round(meters),movingMinutes:Math.round(movingMs/60000),observedMinutes:Math.round(observedMs/60000),stops,firstTime:points[0]?.time||null,lastTime:points[points.length-1]?.time||null,mocked:points.some(p=>p.mocked),note:'位置采样估算；点间连线不是道路还原。不连接超过60秒、信号不可信或暂停的断点。移动不等于运动，缺失位置不等于静止。'};
  }
  function project(p,z){const n=256*2**z,lat=Math.max(-85.05112878,Math.min(85.05112878,p.lat)),s=Math.sin(lat*Math.PI/180);return {x:(p.lon+180)/360*n,y:(0.5-Math.log((1+s)/(1-s))/(4*Math.PI))*n};}
  function fit(points,width,height){const good=points.filter(valid);if(!good.length)return {z:12,x:0,y:0};let z=16;
    for(;z>2;z--){const xy=good.map(p=>project(p,z)),xs=xy.map(p=>p.x),ys=xy.map(p=>p.y);if(Math.max(...xs)-Math.min(...xs)<width-70&&Math.max(...ys)-Math.min(...ys)<height-70)break;}
    const xy=good.map(p=>project(p,z)),xs=xy.map(p=>p.x),ys=xy.map(p=>p.y);return {z,x:(Math.min(...xs)+Math.max(...xs))/2,y:(Math.min(...ys)+Math.max(...ys))/2};
  }
  const api={valid,distance,connected,segments,breaks,bridges,summary,project,fit};if(typeof module==='object'&&module.exports)module.exports=api;else root.LifeTracks=api;
})(typeof window!=='undefined'?window:globalThis);
