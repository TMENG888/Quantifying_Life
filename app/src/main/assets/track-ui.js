(function(){
  'use strict';
  const T=LifeTracks,M=LifeMapTiles,D=LifeDomain,$=id=>document.getElementById(id),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let hooks,date=D.localDate(),data={points:[],dates:[],status:{}},camera=null,token=0,mapToken=0,timer,loading=false,pendingRefresh=false,modeChanging=false,lastView=0;
  let feedback={},roadStatus={},roadRequest=0;
  const tileQueue=new M.Queue(2);
  const images=new Map(),clock=t=>t?new Date(t).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}):'—';
  function page(){return '<section id="track-root"><div class="empty">正在读取本地轨迹…</div></section>';}
  function init(h){hooks=h;}
  async function refresh(){if(!$('track-root'))return;if(loading||modeChanging){pendingRefresh=true;return;}loading=true;const selected=date,current=token;try{
    const [result,roads]=await Promise.all([hooks.api('trackDay',{date:selected}),hooks.api('trackRoadStatus').catch(()=>({}))]);if(current!==token||selected!==date||!$('track-root')||modeChanging)return;if(data.date===result.date&&data.filter?.id===result.filter?.id&&roadStatus.networkId===roads.networkId&&roadStatus.mode===roads.mode&&roads.enabled!==false)result.roadMatching=data.roadMatching;roadStatus=roads;data=result;drawPage();loadRoads(selected,current);
  }catch(e){hooks.toast(e.message);}finally{loading=false;if(pendingRefresh){pendingRefresh=false;refresh();}}}
  function mount(){token++;camera=null;clearInterval(timer);drawPage();refresh();timer=setInterval(()=>{if(!$('track-root')){clearInterval(timer);return;}const editing=$('track-root').contains(document.activeElement)&&['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName);if(!document.hidden&&!editing&&!modeChanging)refresh();},15000);}
  async function importMap(){if(await hooks.confirm('导入离线 OSM 地图？','请选择你有权使用的 Mapsforge .map 矢量地图，或栅格 MBTiles（最大512MB）。地图只存本机，不上传、不自动下载；不支持OSM PBF或矢量MBTiles。','选择文件'))await hooks.api('trackImportMap');}
  async function importRoads(){if(await hooks.confirm('导入离线路网？','请选择本地区域的 OpenStreetMap XML（.osm，最大128MB）。只在本机处理，无需Key；.map、MBTiles、PBF不能作为路网。推断路线用虚线显示，不计里程。','选择文件'))await hooks.api('trackImportRoads');}
  async function loadRoads(selected,current){const request=++roadRequest;if(!roadStatus.configured||roadStatus.enabled===false)return;try{const result=await hooks.api('trackRoadMatch',{date:selected});if(request!==roadRequest||current!==token||selected!==date||!$('track-root'))return;data.roadMatching=result;const status=$('track-road-message');if(status)status.textContent=result.message||'';drawMap();}catch(e){if(request===roadRequest&&selected===date&&$('track-road-message'))$('track-road-message').textContent='道路推断暂不可用，原轨迹仍可查看';}}
  function mapImported(){images.clear();camera=null;if($('track-root'))refresh();if($('track-settings'))mountSettings();}
  function drawPage(){const root=$('track-root');if(!root)return;const s=data.status||{},stats={...T.summary(data.points||[]),meters:data.estimatedMeters??T.summary(data.points||[]).meters,pointCount:data.processedPoints??(data.points||[]).length},gaps=T.breaks(data.points||[]),bridges=T.bridges(data.points||[]),bridgeIndices=new Set(bridges.map(b=>b.gapIndex)),quality=data.quality||{},isRunning=s.running,warning=s.enabled&&!s.running,online=s.mapMode==='online',offline=s.offlineMap||{};
    root.innerHTML=`<div class="eyebrow">YOUR DAY, ON THE MOVE</div><h1 class="page-title">每天走过的地方。</h1><p class="subtitle">全天定位 · 本地保存 · 不自动发送给 AI</p>
      <div class="track-status ${isRunning?'recording':''}"><strong>${isRunning?'● 全天记录中':warning?'◌ 记录已中断':'○ 记录已暂停'}</strong><p>${esc(warning?'请点继续记录。':s.message||'主动开启后，锁屏也可持续定位。')}</p><small>最近定位：${s.lastPoint?new Date(s.lastPoint).toLocaleString('zh-CN'):'尚未收到'}</small></div>
      <div class="actions track-actions"><button class="${isRunning?'secondary':'primary'}" id="track-toggle">${isRunning?'暂停全天记录':'开启 / 继续记录'}</button><button class="secondary" id="track-refresh">刷新轨迹</button></div>
      <label class="field"><span>查看日期</span><input id="track-date" type="date" value="${date}"></label><div class="chip-row">${(data.dates||[]).slice(0,7).map(d=>`<button class="chip ${d===date?'active':''}" data-track-day="${d}">${esc(d.slice(5))}</button>`).join('')}</div>
      <label class="field"><span>轨迹处理</span><select id="track-filter"><option value="motion65" ${data.filter?.id!=='weighted65'?'selected':''}>停留与移动识别</option><option value="weighted65" ${data.filter?.id==='weighted65'?'selected':''}>旧版 D · 误差加权平滑</option></select></label>
      <div class="track-map-choice"><label class="field"><span>OpenStreetMap 底图</span><select id="track-map-mode" ${modeChanging?'disabled':''}><option value="offline" ${online?'':'selected'}>离线 · 导入地图 / 已有缓存</option><option value="online" ${online?'selected':''}>在线 · 请求当前区域</option></select></label>${!online?'<button class="secondary" id="track-import-map">导入离线地图</button>':''}</div>
      ${!online?`<p class="hint">${offline.configured?esc(offline.name||'本地地图')+' · 缩放 '+offline.minZoom+'—'+offline.maxZoom:'尚未导入地图；已有缓存可离线使用。放大时会借用低清底图，完全无图块的区域仍显示网格。'}</p>`:''}
      <div class="track-road-controls"><label class="checkbox"><input id="track-road-enabled" type="checkbox" ${roadStatus.enabled!==false&&roadStatus.configured?'checked':''} ${!roadStatus.configured?'disabled':''}><span>离线道路推断</span></label><button class="text-button" id="track-import-roads">${roadStatus.configured?'更换路网':'导入路网'}</button></div><p id="track-road-message" class="hint" role="status">${esc(data.roadMatching?.message||(roadStatus.importing?'路网导入中…':!roadStatus.configured?'需要本地区域的OSM路网文件；底图不等于路网。':roadStatus.enabled===false?'道路推断已关闭':data.filter?.id==='weighted65'?'请切换“停留与移动识别”以使用道路推断':'正在本机匹配道路…'))}</p>
      <div class="track-map-frame"><canvas id="track-map" aria-label="当天位置轨迹图"></canvas><div class="track-map-tools"><button id="track-zoom-in" aria-label="放大地图">＋</button><button id="track-zoom-out" aria-label="缩小地图">−</button><button id="track-fit">全程</button></div><div id="track-map-message" class="track-map-message">${stats.pointCount?'本地路线 · 起点绿色 / 终点橙色':'这一天还没有有效定位点'}</div></div>
      <div class="track-map-credit"><span id="map-attribution">© <button class="text-button" id="osm-copyright">OpenStreetMap contributors</button>${!online&&offline.attribution?' · '+esc(offline.attribution):''}</span></div><p id="track-selected-point" class="hint">可拖动、缩放；点击路线查看时间与定位状态。</p>
      <p id="track-bridge-legend" class="hint">实线：处理后轨迹。紫色虚线：推断，不计里程；有可信路网匹配时沿道路绘制，否则按时间补连，不代表已确认的实际路线。</p>
      <div class="grid"><div class="stat"><span class="stat-label">轨迹参考距离</span><strong>${(stats.meters/1000).toFixed(2)} <small>km</small></strong><small>仍按原处理点计算，不按拟合长度计量</small></div><div class="stat"><span class="stat-label">轨迹时间</span><strong style="font-size:20px">${clock(stats.firstTime)} — ${clock(stats.lastTime)}</strong><small>${stats.pointCount} 个处理点 · ${stats.gaps} 处虚线补连</small></div></div>
      <p class="hint">原始位置与GPX保持不变；道路推断只改变地图绘制，可关闭对照。</p>
      ${stats.medianSampleSeconds>15?`<p class="warning">这天有效点约 ${stats.medianSampleSeconds} 秒一采样，转弯之间可能没有记录；放大底图不会恢复缺失位置。</p>`:''}
      <p class="hint">${data.filter?.id==='motion65'?`停留估计 ${quality.stationaryEstimatePoints||0} 点 · 移动 ${quality.movingPoints||0} 点 · 待确认 ${quality.uncertainPoints||0} 点。运动数据缺失时参考位置趋势；不能确认的片段不画成实线。`:``}${data.totalPoints??stats.pointCount} 个已保存原始点 → 处理后 ${stats.pointCount} 个点；${quality.excludedPoints||0} 个未纳入轨迹。${data.displaySimplified?'地图已简化绘制；距离与GPX仍按完整处理点计算。':data.truncated?'统计为部分数据。':'原始记录仍完整保留。'}</p>
      ${data.comparison?`<p class="hint">对照 · ${esc(data.comparison.label)}：${data.comparison.points}点 · ${(data.comparison.meters/1000).toFixed(2)}km · ${data.comparison.gaps}处缺口。切换上方方案查看同一天的路线。</p>`:''}
      ${gaps.length?`<details id="track-gap-list" class="panel"><summary>查看 ${gaps.length} 处虚线补连的时间</summary><p class="hint">已去除密集断点圆圈。按时间顺序连接前一段终点和后一段起点，不按空间最近匹配，也不再限制缺口时长、距离、速度或会话；暂停或长时间未定位期间的路线仍未知。</p><div class="chip-row">${gaps.slice(0,60).map((g,i)=>`<button class="chip" data-track-gap="${i}">${clock(g.a.time)} → ${clock(g.b.time)} · ${g.seconds}秒 · ${esc(g.reason)} · ${bridgeIndices.has(i)?'虚线参考':'无可用端点'}</button>`).join('')}</div>${gaps.length>60?'<p class="hint">仅列出前60处断点，完整记录可导出定位诊断。</p>':''}</details>`:''}
      ${stats.mocked?'<p class="warning">包含模拟定位，仅用于测试。</p>':''}${data.truncated?'<p class="warning">超过读取安全上限，仅显示部分，建议导出诊断核对。</p>':''}<button class="secondary full" id="track-data-tools">轨迹数据 · 导出与管理</button>`;
    $('track-toggle').onclick=()=>hooks.safe(async()=>{if(isRunning){await hooks.api('trackStop');setTimeout(refresh,500);}else if(await hooks.confirm('开启全天定位？','锁屏后持续采集，直到你暂停。位置只存手机，不发给AI；持续GPS会耗电。请允许精确定位和通知。','同意并开启')){await hooks.api('trackStart');hooks.toast('请完成系统权限提示');setTimeout(refresh,1200);}});
    $('track-data-tools').onclick=()=>{hooks.editorFrame('轨迹数据',settings());mountSettings();};
    $('track-filter').onchange=e=>hooks.safe(async()=>{token++;await hooks.api('trackConfig',{filterMode:e.target.value});await refresh();});
    $('track-refresh').onclick=refresh;$('track-date').onchange=e=>{if(e.target.value){date=e.target.value;camera=null;token++;refresh();}};
    root.querySelectorAll('[data-track-day]').forEach(el=>el.onclick=()=>{date=el.dataset.trackDay;camera=null;token++;refresh();});
    $('track-map-mode').onchange=e=>hooks.safe(()=>changeMode(e.target.value));
    if($('track-import-map'))$('track-import-map').onclick=()=>hooks.safe(importMap);
    $('track-import-roads').onclick=()=>hooks.safe(importRoads);$('track-road-enabled').onchange=e=>hooks.safe(async()=>{roadRequest++;token++;roadStatus=await hooks.api('trackRoadConfig',{enabled:e.target.checked});delete data.roadMatching;drawPage();refresh();});
    $('osm-copyright').onclick=()=>hooks.safe(()=>hooks.api('openUrl',{url:'https://www.openstreetmap.org/copyright'}));
    bindMap();drawMap();

    root.querySelectorAll('[data-track-gap]').forEach(el=>el.onclick=()=>{const index=Number(el.dataset.trackGap),g=gaps[index],c=$('track-map');camera=T.fit([g.a,g.b],c.clientWidth,c.clientHeight);c.scrollIntoView({block:'center'});$('track-selected-point').textContent=clock(g.a.time)+' → '+clock(g.b.time)+' · '+g.seconds+'秒 · '+g.reason+'；紫色虚线按时间补连，非真实路线，未计入里程。';drawMap();});
  }
  async function changeMode(mode){
    const previous=data.status?.mapMode||'offline';if(modeChanging||mode===previous)return;
    modeChanging=true;token++;mapToken++;const select=$('track-map-mode');if(select)select.disabled=true;
    try{
      if(mode==='online'&&!await hooks.confirm('加载在线 OSM 地图？','OpenStreetMap会收到当前区域图块请求和IP，可推知查看区域；不会收到完整轨迹或大模型Key。网络不可用时仍保留本地路线。','同意加载'))return;
      const status=await hooks.api('trackConfig',{mapMode:mode,mapConsent:mode==='online'});
      data={...data,status};hooks.toast(mode==='online'?'已切换在线地图':'已切换离线地图，不再请求网络图块');
    }finally{
      modeChanging=false;if($('track-root'))drawPage();
      if(pendingRefresh&&!loading){pendingRefresh=false;refresh();}
    }
  }
  function settings(){return '<div class="panel settings-card" aria-label="轨迹导出与管理"><div id="track-settings"><p class="hint">正在读取…</p></div></div>';}
  function feedbackProgress(value){feedback=value||{};const busy=['choosing','writing'].includes(feedback.state),button=$('track-export-all'),status=$('track-feedback-status');if(button){button.disabled=busy;button.textContent=busy?'正在导出…':'导出全部定位反馈包';}if(status)status.textContent=feedback.message||'包含所有日期的原始定位与诊断，仅保存本地。';if($('track-delete'))$('track-delete').disabled=busy;}
  async function mountSettings(){if(!$('track-settings'))return;try{const [result,exportStatus]=await Promise.all([hooks.api('trackDay',{date:D.localDate()}),hooks.api('trackFeedbackStatus')]),s=result.status;if(!$('track-settings'))return;
    $('track-settings').innerHTML=`<div class="track-feedback"><button class="primary full" id="track-export-all">导出全部定位反馈包</button><p class="hint" id="track-feedback-status" role="status" aria-live="polite"></p></div><label class="field"><span>单日导出与管理</span><input id="track-manage-date" type="date" value="${date}"></label><div class="track-day-tools"><button class="secondary" id="track-export">导出 GPX</button><button class="secondary" id="track-export-raw">导出原始 GPX</button><button class="secondary" id="track-diagnostics">导出定位诊断</button><button class="danger" id="track-delete">删除当天轨迹</button></div><label class="checkbox"><input id="track-reboot" type="checkbox" ${s.resumeAfterReboot?'checked':''}><span>重启后尝试恢复记录（需始终允许定位）</span></label><p class="hint">普通学习备份不含轨迹；反馈包不含模型 Key 或学习笔记。</p>`;
    feedbackProgress(exportStatus);
    const roads=await hooks.api('trackRoadStatus');if(!$('track-settings'))return;const panel=document.createElement('div');panel.className='panel';panel.innerHTML=`<h3>离线路网</h3><p class="hint">${roads.configured?esc(roads.source)+' · '+roads.edges+'条道路边':'尚未导入路网'}</p><button class="secondary" id="track-settings-roads">导入 / 更换 OSM 路网</button><label class="field"><span>道路推断的出行方式</span><select id="track-road-mode">${[['auto','自动（依据可信速度）'],['walk','步行'],['bike','骑行'],['car','乘车']].map(([id,label])=>`<option value="${id}" ${roads.mode===id?'selected':''}>${label}</option>`).join('')}</select></label><p class="hint">只支持OSM XML（.osm），最大128MB。地图文件不能代替路网；不上传定位，不用于导航。</p>`;$('track-settings').append(panel);$('track-settings-roads').onclick=()=>hooks.safe(importRoads);$('track-road-mode').onchange=e=>hooks.safe(async()=>{roadRequest++;token++;roadStatus=await hooks.api('trackRoadConfig',{mode:e.target.value});if($('track-root'))refresh();});
    $('track-export-all').onclick=()=>hooks.safe(async()=>{if(['choosing','writing'].includes(feedback.state))return;if(await hooks.confirm('导出全部定位反馈包？','包含所有日期的精确位置、时间、速度、定位误差及已有运动传感器诊断，包括被过滤的点；可能暴露住址与完整行踪。不含模型 Key 或笔记，不自动上传。文件较多时请保持 App 打开，完成后再自行分享给信任的人。','选择保存位置'))await hooks.api('trackExportAll');});
    $('track-export').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('导出位置轨迹？','GPX包含平滑后的精确位置和时间，请妥善保管。','导出'))await hooks.api('trackExport',{date:selected});});
    $('track-export-raw').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('导出原始定位？','含未经筛选的精确坐标，可能有漂移、过旧或重复点，不代表可靠行程。仅保存本地，请妥善保管。','导出'))await hooks.api('trackExport',{date:selected,raw:true});});
    $('track-diagnostics').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('导出原始定位诊断？','包含当天原始与过滤后精确位置、时间、速度和可用的GPS信号信息；仅保存本地，不自动发送AI。请只分享给信任的人。','导出'))await hooks.api('trackDiagnostics',{date:selected});});
    $('track-delete').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('永久删除当天轨迹？','无法撤销，导出的文件不受影响；采集中仍会产生新位置。','删除')){await hooks.api('trackDeleteDay',{date:selected});hooks.toast('当天轨迹已删除');}});
    $('track-reboot').onchange=e=>hooks.safe(async()=>{const checked=e.target.checked;if(checked&&!s.precisePermission){e.target.checked=false;hooks.toast('请先开启轨迹并允许精确定位');return;}await hooks.api('trackConfig',{resumeAfterReboot:checked});if(checked&&!s.backgroundPermission)await hooks.api('trackBackgroundPermission');});
  }catch(e){if($('track-settings'))$('track-settings').textContent=e.message;}}
  function bindMap(){const c=$('track-map');let drag=null;
    c.onpointerdown=e=>{drag={x:e.clientX,y:e.clientY,ox:camera?.x||0,oy:camera?.y||0,moved:false};c.setPointerCapture(e.pointerId);};
    c.onpointermove=e=>{if(!drag||!camera)return;const dx=e.clientX-drag.x,dy=e.clientY-drag.y;if(Math.abs(dx)+Math.abs(dy)>5)drag.moved=true;camera.x=drag.ox-dx;camera.y=drag.oy-dy;drawMap(false);};
    c.onpointerup=e=>{if(drag&&!drag.moved&&camera){const r=c.getBoundingClientRect(),x=e.clientX-r.left,y=e.clientY-r.top;let nearest=null,min=25;for(const p of data.points){const v=T.project(p,camera.z),d=Math.hypot(v.x-camera.x+c.clientWidth/2-x,v.y-camera.y+c.clientHeight/2-y);if(d<min){min=d;nearest=p;}}if(nearest)$('track-selected-point').textContent=clock(nearest.time)+' · '+nearest.lat.toFixed(5)+', '+nearest.lon.toFixed(5)+' · 原始定位误差估计约'+Math.round(nearest.reportedAccuracy||nearest.accuracy)+'米'+(nearest.provider==='fused'?' · 系统fused备援':' · GPS')+(nearest.motionState==='stationary_estimate'?' · 停留估计':nearest.motionState==='moving'?' · 移动估计':nearest.motionState==='uncertain'?' · 位置待确认（可能沿用上次停留位置）':'')+(nearest.filtered?' · 处理后位置':' · 原坐标');}drag=null;drawMap();};
    c.onpointercancel=()=>{drag=null;drawMap();};$('track-fit').onclick=()=>{camera=null;drawMap();};
    for(const [id,delta] of [['track-zoom-in',1],['track-zoom-out',-1]])$(id).onclick=()=>{if(!camera)return;const z=Math.min(18,Math.max(2,camera.z+delta)),scale=2**(z-camera.z);camera={z,x:camera.x*scale,y:camera.y*scale};drawMap();};
  }
  async function drawMap(load=true){
    const canvas=$('track-map');if(!canvas)return;
    const seq=++mapToken,w=canvas.clientWidth,h=canvas.clientHeight,dpr=Math.min(2,devicePixelRatio||1),parts=T.segments(data.points||[]),bridges=T.bridges(data.points||[]),roads=roadStatus.enabled!==false?T.roadOverlays(data.roadMatching):[];
    canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);const ctx=canvas.getContext('2d');ctx.scale(dpr,dpr);
    function grid(){ctx.fillStyle='#e8eee6';ctx.fillRect(0,0,w,h);ctx.strokeStyle='#d2ded2';ctx.lineWidth=1;ctx.setLineDash([]);for(let x=0;x<w;x+=32){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke();}for(let y=0;y<h;y+=32){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke();}}
    grid();if(!parts.length)return;if(!camera)camera=T.fit(data.points,w,h);
    const view={...camera},left=view.x-w/2,top=view.y-h/2,mode=data.status.mapMode||'offline',tiles=M.visible(view,w,h),key=t=>mode+'/'+t.z+'/'+t.x+'/'+t.y;
    canvas.dataset.zoom=String(view.z);canvas.dataset.mapMode=mode;
    const current=()=>seq===mapToken&&canvas===$('track-map')&&(data.status?.mapMode||'offline')===mode&&!modeChanging;
    function drawPath(path,hideMatched){ctx.beginPath();let pen=false;for(let i=1;i<path.length;i++){if(hideMatched&&T.roadCovered(path[i-1],path[i],roads)){pen=false;continue;}const a=T.project(path[i-1],view.z),b=T.project(path[i],view.z);if(!pen)ctx.moveTo(a.x-left,a.y-top);ctx.lineTo(b.x-left,b.y-top);pen=true;}ctx.stroke();}
    function paintRoute(){ctx.save();ctx.setLineDash([7,5]);ctx.strokeStyle='#8754a1';ctx.lineWidth=2;for(const bridge of bridges)drawPath(bridge.path||[bridge.a,bridge.b],true);ctx.restore();ctx.setLineDash([]);ctx.strokeStyle=data.filter?.color||'#b47719';ctx.lineWidth=3;ctx.lineJoin='round';for(const part of parts)drawPath(part,true);
      ctx.save();ctx.setLineDash([9,5]);ctx.strokeStyle='#8754a1';ctx.lineWidth=3;for(const road of roads)drawPath(road.path,false);ctx.restore();canvas.dataset.roadRoutes=String(roads.length);
      // Only start/end markers: hundreds of gap-end circles obscure the route.
      const all=parts.flat();for(const [p,color] of [[all[0],'#216a54'],[all[all.length-1],'#b67e45']]){const v=T.project(p,view.z);ctx.beginPath();ctx.arc(v.x-left,v.y-top,6,0,2*Math.PI);ctx.fillStyle=color;ctx.fill();ctx.strokeStyle='#fff';ctx.lineWidth=2;ctx.stroke();}}
    function entryFor(t){
      const direct=images.get(key(t));if(direct)return direct;
      for(let z=t.z-1;z>=0;z--){const scale=2**(t.z-z),entry=images.get(mode+'/'+z+'/'+Math.floor(t.x/scale)+'/'+Math.floor(t.y/scale));if(entry)return entry;}
      return null;
    }
    function paint(){
      if(!current())return;grid();
      for(const t of tiles){const entry=entryFor(t);if(!entry)continue;try{const r=M.crop(t,entry,entry.image.naturalWidth,entry.image.naturalHeight);ctx.drawImage(entry.image,r.sx,r.sy,r.sw,r.sh,t.px,t.py,256,256);}catch(e){}}
      paintRoute();
    }
    function remember(t,entry){images.delete(key(t));images.set(key(t),entry);const sourceKey=mode+'/'+entry.sourceZ+'/'+entry.sourceX+'/'+entry.sourceY;images.set(sourceKey,entry);while(images.size>160)images.delete(images.keys().next().value);}
    function message(remaining,error=''){
      if(!current())return;let ready=0,coarse=0;for(const t of tiles){const entry=entryFor(t);if(entry){ready++;if((entry.sourceZ??t.z)<t.z)coarse++;}}
      const prefix=mode==='offline'?'离线 OSM 底图':'在线 OSM 底图';
      $('track-map-message').textContent=ready?prefix+(coarse?' · 低清放大，未增加街道细节':'')+(ready<tiles.length?' · '+(tiles.length-ready)+'块区域暂无底图':'')+(remaining?' · 加载中':''):remaining?'正在加载当前区域底图…':'本地路线网格 · '+(error||'此区域没有可用底图');
    }
    paint();message(load?tiles.length:0);if(!load||modeChanging)return;
    const ticket=lastView=Math.max(lastView+1,Date.now()*1000);
    try{await hooks.api('trackMapView',{view:ticket,mode});if(!current())return;}catch(e){if(current())message(0,e.message);return;}
    let remaining=tiles.length,error='';
    await Promise.all(tiles.map(t=>tileQueue.run(async()=>{
      if(!current())return;
      const existing=images.get(key(t));
      if(existing?.sourceZ===t.z)return;
      try{
        const response=await hooks.api('trackTile',{z:t.z,x:t.x,y:t.y,mode,view:ticket});if(!current())return;
        const image=new Image();await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(Error('地图图片无法加载'));image.src=response.data;});
        const entry={image,sourceZ:response.sourceZ??t.z,sourceX:response.sourceX??t.x,sourceY:response.sourceY??t.y};
        M.crop(t,entry,image.naturalWidth,image.naturalHeight);if(!current())return;remember(t,entry);paint();
      }catch(e){if(current())error=e.message;}
    },current).catch(()=>{}).finally(()=>{remaining--;message(remaining,error);})));
  }
  window.TrackUI={init,page,mount,settings,mountSettings,mapImported,feedbackProgress};
})();
