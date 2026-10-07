(function(){
  'use strict';
  const T=LifeTracks,D=LifeDomain,$=id=>document.getElementById(id),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let hooks,date=D.localDate(),data={points:[],dates:[],status:{}},camera=null,token=0,mapToken=0,timer,loading=false,pendingRefresh=false;
  let showBridges=true;try{showBridges=localStorage.getItem('zhishi-track-inferred-bridges')!=='off';}catch(e){}
  const images=new Map(),clock=t=>t?new Date(t).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}):'—';
  function page(){return '<section id="track-root"><div class="empty">正在读取本地轨迹…</div></section>';}
  function init(h){hooks=h;}
  async function refresh(){if(!$('track-root'))return;if(loading){pendingRefresh=true;return;}loading=true;const selected=date,current=token;try{
    const result=await hooks.api('trackDay',{date:selected});if(current!==token||selected!==date||!$('track-root'))return;data=result;drawPage();
  }catch(e){hooks.toast(e.message);}finally{loading=false;if(pendingRefresh){pendingRefresh=false;refresh();}}}
  function mount(){token++;camera=null;clearInterval(timer);drawPage();refresh();timer=setInterval(()=>{if(!$('track-root')){clearInterval(timer);return;}const editing=$('track-root').contains(document.activeElement)&&['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName);if(!document.hidden&&!editing)refresh();},15000);}
  async function importMap(){if(await hooks.confirm('导入离线 OSM 地图？','请选择你有权使用的栅格 MBTiles（PNG/JPEG/WebP，最大512MB），不支持PBF矢量文件。仅覆盖地图文件，不改变轨迹；不上传、不自动下载地图。','选择文件'))await hooks.api('trackImportMap');}
  function mapImported(){images.clear();camera=null;if($('track-root'))refresh();if($('track-settings'))mountSettings();}
  function drawPage(){const root=$('track-root');if(!root)return;const s=data.status||{},stats=T.summary(data.points||[]),gaps=T.breaks(data.points||[]),bridges=T.bridges(data.points||[]),bridgeIndices=new Set(bridges.map(b=>b.gapIndex)),quality=data.quality||{},isRunning=s.running,warning=s.enabled&&!s.running,online=s.mapMode==='online',offline=s.offlineMap||{};
    root.innerHTML=`<div class="eyebrow">YOUR DAY, ON THE MOVE</div><h1 class="page-title">每天走过的地方。</h1><p class="subtitle">全天定位 · 本地保存 · 不自动发送给 AI</p>
      <div class="track-status ${isRunning?'recording':''}"><strong>${isRunning?'● 全天记录中':warning?'◌ 记录已中断':'○ 记录已暂停'}</strong><p>${esc(warning?'请点继续记录。':s.message||'主动开启后，锁屏也可持续定位。')}</p><small>最近定位：${s.lastPoint?new Date(s.lastPoint).toLocaleString('zh-CN'):'尚未收到'}</small></div>
      <div class="actions track-actions"><button class="${isRunning?'secondary':'primary'}" id="track-toggle">${isRunning?'暂停全天记录':'开启 / 继续记录'}</button><button class="secondary" id="track-refresh">刷新轨迹</button></div>
      <label class="field"><span>查看日期</span><input id="track-date" type="date" value="${date}"></label><div class="chip-row">${(data.dates||[]).slice(0,7).map(d=>`<button class="chip ${d===date?'active':''}" data-track-day="${d}">${esc(d.slice(5))}</button>`).join('')}</div>
      <div class="track-map-choice"><label class="field"><span>OpenStreetMap 底图</span><select id="track-map-mode"><option value="offline" ${online?'':'selected'}>离线 · 导入地图 / 已有缓存</option><option value="online" ${online?'selected':''}>在线 · 请求当前区域</option></select></label>${!online?'<button class="secondary" id="track-import-map">导入离线地图</button>':''}</div>
      ${!online?`<p class="hint">${offline.configured?esc(offline.name||'本地MBTiles')+' · 缩放 '+offline.minZoom+'—'+offline.maxZoom:'尚未导入地图；无缓存区域只显示路线网格，不包含街道。'}</p>`:''}
      <div class="track-map-frame"><canvas id="track-map" aria-label="当天位置轨迹图"></canvas><div class="track-map-tools"><button id="track-zoom-in" aria-label="放大地图">＋</button><button id="track-zoom-out" aria-label="缩小地图">−</button><button id="track-fit">全程</button></div><div id="track-map-message" class="track-map-message">${stats.pointCount?'本地路线 · 起点绿色 / 终点橙色':'这一天还没有有效定位点'}</div></div>
      <div class="track-map-credit"><span id="map-attribution">© <button class="text-button" id="osm-copyright">OpenStreetMap contributors</button>${!online&&offline.attribution?' · '+esc(offline.attribution):''}</span></div><p id="track-selected-point" class="hint">可拖动、缩放；点击路线查看时间与精度。</p>
      <label class="checkbox"><input id="track-show-bridges" type="checkbox" ${showBridges?'checked':''}><span>短断点虚线补连（推测）</span></label><p id="track-bridge-legend" class="hint">绿色实线：已记录轨迹 · 紫色虚线：推测连接，不计里程。${showBridges?'显示':'已关闭；符合条件'} ${bridges.length} 处短缺口参考；不是沿道路导航或恢复真实路线。</p>
      <div class="grid"><div class="stat"><span class="stat-label">移动距离</span><strong>${(stats.meters/1000).toFixed(2)} <small>km</small></strong><small>步行、骑行、乘车的定位估算</small></div><div class="stat"><span class="stat-label">轨迹时间</span><strong style="font-size:20px">${clock(stats.firstTime)} — ${clock(stats.lastTime)}</strong><small>${stats.pointCount} 个有效点 · ${stats.gaps} 处断点</small></div></div>
      <p class="hint">已过滤静止漂移与异常跳点；已记录轨迹超过60秒或信号不可信时仍断开。推测虚线只作视觉参考，不写入数据库或普通GPX，距离仅包含有效连续采样；点间直线不代表真实道路，手机精度数值也不是误差保证。</p>
      ${stats.medianSampleSeconds>15?`<p class="warning">这天有效点约 ${stats.medianSampleSeconds} 秒一采样，转弯之间可能没有记录；放大底图不会恢复缺失位置。</p>`:''}
      ${quality.excludedPoints?`<p class="hint">${quality.countsComplete?'当天':'已读取数据中'}未展示 ${quality.excludedPoints} 个原始点；含非 GPS ${quality.nonGpsPoints||0} 个、误差估计超过35米 ${quality.poorAccuracyPoints||0} 个（两类可能重叠）。原始记录仍保留。</p>`:''}
      ${gaps.length?`<details id="track-gap-list" class="panel"><summary>查看 ${gaps.length} 处断点的时间</summary><p class="hint">橙色空心点是断点两端，孤立点仍显示。仅同会话、60—120秒、两端相距5—600米且有相邻移动证据的短缺口可作推测；定位不可信、方向反转及长缺口仍断开。</p><div class="chip-row">${gaps.slice(0,60).map((g,i)=>`<button class="chip" data-track-gap="${i}">${clock(g.a.time)} → ${clock(g.b.time)} · ${g.seconds}秒 · ${esc(g.reason)} · ${showBridges&&bridgeIndices.has(i)?'虚线参考':'保留断开'}</button>`).join('')}</div>${gaps.length>60?'<p class="hint">仅列出前60处断点，完整记录可导出定位诊断。</p>':''}</details>`:''}
      ${stats.mocked?'<p class="warning">包含模拟定位，仅用于测试。</p>':''}${data.truncated?'<p class="warning">超过20000点，仅展示部分；距离也仅基于展示点。</p>':''}`;
    $('track-toggle').onclick=()=>hooks.safe(async()=>{if(isRunning){await hooks.api('trackStop');setTimeout(refresh,500);}else if(await hooks.confirm('开启全天定位？','锁屏后持续采集，直到你暂停。位置只存手机，不发给AI；持续GPS会耗电。请允许精确定位和通知。','同意并开启')){await hooks.api('trackStart');hooks.toast('请完成系统权限提示');setTimeout(refresh,1200);}});
    $('track-refresh').onclick=refresh;$('track-date').onchange=e=>{if(e.target.value){date=e.target.value;camera=null;token++;refresh();}};
    root.querySelectorAll('[data-track-day]').forEach(el=>el.onclick=()=>{date=el.dataset.trackDay;camera=null;token++;refresh();});
    $('track-map-mode').onchange=e=>hooks.safe(async()=>{const mode=e.target.value;if(mode==='online'&&!await hooks.confirm('加载在线 OSM 地图？','OpenStreetMap会收到当前区域图块请求和IP，可推知查看区域；不会收到完整轨迹或大模型Key。网络不可用时仍保留本地路线。','同意加载')){e.target.value=s.mapMode||'offline';return;}await hooks.api('trackConfig',{mapMode:mode,mapConsent:mode==='online'});images.clear();await refresh();});
    if($('track-import-map'))$('track-import-map').onclick=()=>hooks.safe(importMap);
    $('osm-copyright').onclick=()=>hooks.safe(()=>hooks.api('openUrl',{url:'https://www.openstreetmap.org/copyright'}));
    bindMap();drawMap();
    $('track-show-bridges').onchange=e=>{showBridges=e.target.checked;try{localStorage.setItem('zhishi-track-inferred-bridges',showBridges?'on':'off');}catch(err){}drawPage();};
    root.querySelectorAll('[data-track-gap]').forEach(el=>el.onclick=()=>{const index=Number(el.dataset.trackGap),g=gaps[index],c=$('track-map');camera=T.fit([g.a,g.b],c.clientWidth,c.clientHeight);c.scrollIntoView({block:'center'});$('track-selected-point').textContent=clock(g.a.time)+' → '+clock(g.b.time)+' · '+g.seconds+'秒 · '+g.reason+(showBridges&&bridgeIndices.has(index)?'；紫色虚线为推测直线，未计入里程。':'；保留断开，未知路线没有补画。');drawMap();});
  }
  function settings(){return '<div class="panel settings-card"><h2>轨迹数据</h2><div id="track-settings"><p class="hint">正在读取…</p></div></div>';}
  async function mountSettings(){if(!$('track-settings'))return;try{const result=await hooks.api('trackDay',{date:D.localDate()}),s=result.status;if(!$('track-settings'))return;
    $('track-settings').innerHTML=`<p class="hint">导出或删除指定日期的位置。普通学习备份不含轨迹。离线地图不会发送给AI。</p><label class="field"><span>轨迹日期</span><input id="track-manage-date" type="date" value="${D.localDate()}"></label><div class="actions"><button class="secondary" id="track-export">导出 GPX</button><button class="secondary" id="track-diagnostics">导出定位诊断</button><button class="danger" id="track-delete">删除当天轨迹</button></div><label class="checkbox"><input id="track-reboot" type="checkbox" ${s.resumeAfterReboot?'checked':''}><span>重启后尝试恢复记录（需始终允许定位）</span></label><p class="hint">暂停后不会自动恢复。后台权限及电池策略请自行到手机系统设置调整。</p>`;
    $('track-export').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('导出位置轨迹？','GPX包含去漂移后的精确位置和时间，请妥善保管。','导出'))await hooks.api('trackExport',{date:selected});});
    $('track-diagnostics').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('导出原始定位诊断？','包含当天原始与过滤后精确位置、时间、速度和可用的GPS信号信息；仅保存本地，不自动发送AI。请只分享给信任的人。','导出'))await hooks.api('trackDiagnostics',{date:selected});});
    $('track-delete').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('永久删除当天轨迹？','无法撤销，导出的文件不受影响；采集中仍会产生新位置。','删除')){await hooks.api('trackDeleteDay',{date:selected});hooks.toast('当天轨迹已删除');}});
    $('track-reboot').onchange=e=>hooks.safe(async()=>{const checked=e.target.checked;if(checked&&!s.precisePermission){e.target.checked=false;hooks.toast('请先开启轨迹并允许精确定位');return;}await hooks.api('trackConfig',{resumeAfterReboot:checked});if(checked&&!s.backgroundPermission)await hooks.api('trackBackgroundPermission');});
  }catch(e){if($('track-settings'))$('track-settings').textContent=e.message;}}
  function bindMap(){const c=$('track-map');let drag=null;
    c.onpointerdown=e=>{drag={x:e.clientX,y:e.clientY,ox:camera?.x||0,oy:camera?.y||0,moved:false};c.setPointerCapture(e.pointerId);};
    c.onpointermove=e=>{if(!drag||!camera)return;const dx=e.clientX-drag.x,dy=e.clientY-drag.y;if(Math.abs(dx)+Math.abs(dy)>5)drag.moved=true;camera.x=drag.ox-dx;camera.y=drag.oy-dy;drawMap(false);};
    c.onpointerup=e=>{if(drag&&!drag.moved&&camera){const r=c.getBoundingClientRect(),x=e.clientX-r.left,y=e.clientY-r.top;let nearest=null,min=25;for(const p of data.points){const v=T.project(p,camera.z),d=Math.hypot(v.x-camera.x+c.clientWidth/2-x,v.y-camera.y+c.clientHeight/2-y);if(d<min){min=d;nearest=p;}}if(nearest)$('track-selected-point').textContent=clock(nearest.time)+' · '+nearest.lat.toFixed(5)+', '+nearest.lon.toFixed(5)+' · 原始定位误差估计约'+Math.round(nearest.reportedAccuracy||nearest.accuracy)+'米'+(nearest.filtered?' · 已去漂移，非原始坐标':'');}drag=null;drawMap();};
    c.onpointercancel=()=>{drag=null;drawMap();};$('track-fit').onclick=()=>{camera=null;drawMap();};
    for(const [id,delta] of [['track-zoom-in',1],['track-zoom-out',-1]])$(id).onclick=()=>{if(!camera)return;const z=Math.min(18,Math.max(2,camera.z+delta)),scale=2**(z-camera.z);camera={z,x:camera.x*scale,y:camera.y*scale};drawMap();};
  }
  async function drawMap(load=true){const canvas=$('track-map');if(!canvas)return;const seq=++mapToken,w=canvas.clientWidth,h=canvas.clientHeight,dpr=Math.min(2,devicePixelRatio||1),parts=T.segments(data.points||[]),bridges=showBridges?T.bridges(data.points||[]):[];
    canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);const ctx=canvas.getContext('2d');ctx.scale(dpr,dpr);ctx.fillStyle='#e8eee6';ctx.fillRect(0,0,w,h);ctx.strokeStyle='#d2ded2';for(let x=0;x<w;x+=32){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke();}for(let y=0;y<h;y+=32){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke();}
    if(!parts.length)return;if(!camera)camera=T.fit(data.points,w,h);
    const view={...camera},left=view.x-w/2,top=view.y-h/2,n=2**view.z,mode=data.status.mapMode||'offline',tiles=[];
    for(let x=Math.floor(left/256);x<=Math.floor((left+w)/256);x++)for(let y=Math.floor(top/256);y<=Math.floor((top+h)/256);y++)if(y>=0&&y<n)tiles.push({z:view.z,x:((x%n)+n)%n,y,px:x*256-left,py:y*256-top});
    function paintRoute(){ctx.save();ctx.setLineDash([7,5]);ctx.strokeStyle='#8754a1';ctx.lineWidth=2;for(const bridge of bridges){const a=T.project(bridge.a,view.z),b=T.project(bridge.b,view.z);ctx.beginPath();ctx.moveTo(a.x-left,a.y-top);ctx.lineTo(b.x-left,b.y-top);ctx.stroke();}ctx.restore();ctx.setLineDash([]);ctx.strokeStyle='#216a54';ctx.lineWidth=3;ctx.lineJoin='round';for(const part of parts){ctx.beginPath();part.forEach((p,i)=>{const v=T.project(p,view.z);i?ctx.lineTo(v.x-left,v.y-top):ctx.moveTo(v.x-left,v.y-top);});ctx.stroke();if(part.length===1){const v=T.project(part[0],view.z);ctx.beginPath();ctx.arc(v.x-left,v.y-top,3,0,2*Math.PI);ctx.fillStyle='#216a54';ctx.fill();}}
      ctx.strokeStyle='#b67e45';ctx.lineWidth=2;for(let i=1;i<parts.length;i++)for(const p of [parts[i-1][parts[i-1].length-1],parts[i][0]]){const v=T.project(p,view.z);ctx.beginPath();ctx.arc(v.x-left,v.y-top,4,0,2*Math.PI);ctx.stroke();}
      const all=parts.flat();for(const [p,color] of [[all[0],'#216a54'],[all[all.length-1],'#b67e45']]){const v=T.project(p,view.z);ctx.beginPath();ctx.arc(v.x-left,v.y-top,6,0,2*Math.PI);ctx.fillStyle=color;ctx.fill();ctx.strokeStyle='#fff';ctx.lineWidth=2;ctx.stroke();}}
    for(const t of tiles){const img=images.get(mode+'/'+t.z+'/'+t.x+'/'+t.y);if(img)ctx.drawImage(img,t.px,t.py,256,256);}paintRoute();if(!load)return;
    let loaded=0,missing=0,error='';
    for(const t of tiles.slice(0,16)){
      if(seq!==mapToken||!$('track-map')||(data.status.mapMode||'offline')!==mode)return;const key=mode+'/'+t.z+'/'+t.x+'/'+t.y;if(images.has(key)){loaded++;continue;}
      try{const r=await hooks.api('trackTile',{z:t.z,x:t.x,y:t.y});const img=new Image();await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=()=>reject(Error('地图图片无法加载'));img.src=r.data;});images.set(key,img);if(images.size>80)images.delete(images.keys().next().value);if(seq!==mapToken)return;loaded++;ctx.drawImage(img,t.px,t.py,256,256);paintRoute();}
      catch(e){missing++;error=e.message;if(mode==='online')break;}
    }
    if(seq===mapToken)$('track-map-message').textContent=loaded?(mode==='offline'?'离线 OSM 底图':'在线 OSM 底图')+(missing?' · 部分区域无底图':''):'本地路线网格 · '+(error||'此区域没有底图');
  }
  window.TrackUI={init,page,mount,settings,mountSettings,mapImported};
})();
