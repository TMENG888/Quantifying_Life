(function(){
  'use strict';
  const T=LifeTracks,M=LifeMapTiles,D=LifeDomain,$=id=>document.getElementById(id),esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let hooks,date=D.localDate(),data={points:[],dates:[],status:{}},camera=null,token=0,mapToken=0,timer,loading=false,pendingRefresh=false,modeChanging=false,lastView=0;
  let feedback={},roadStatus={},roadRequest=0;
  let mapGesture=null,mapTouched=false;
  let photoResult={photos:[]},photoRequest=0,photoLoadedAt=0,photoLoading=false,photoDirty=false;
  const photoNodes=new Map();
  let timeView={mode:'all',start:'09:00',end:'10:00',at:'09:30',result:null,busy:false,error:''},timeRequest=0,selectedPoint=null;
  const precise=t=>new Date(t).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false});
  const timeValue=t=>{const d=new Date(t);return [d.getHours(),d.getMinutes(),d.getSeconds()].map(x=>String(x).padStart(2,'0')).join(':');};
  function viewData(){return timeView.mode==='all'?data:timeView.result||{points:[],estimatedMeters:0,processedPoints:0};}
  function rawMode(){return data.filter?.id==='raw';}
  function changeDate(value){date=value;camera=null;mapTouched=false;token++;timeRequest++;timeView={...timeView,mode:'all',result:null,busy:false,error:''};selectedPoint=null;data={points:[],dates:data.dates,status:data.status,filter:data.filter};drawPage();refresh();}
  async function queryTime(){
    const request=++timeRequest,selected=date,mode=timeView.mode,filter=data.filter?.id;
    if(mode==='all'){timeView.busy=false;return true;}
    timeView.busy=true;timeView.error='';
    try{const result=await hooks.api('trackTime',{date:selected,mode,start:timeView.start,end:timeView.end,at:timeView.at,filterMode:filter});
      if(request!==timeRequest||selected!==date||mode!==timeView.mode||filter!==data.filter?.id||!$('track-root'))return false;
      if(filter&&result.filter&&result.filter.id!==filter)throw Error('处理方案已变化，请刷新后重试');
      timeView.result=result;selectedPoint=mode==='moment'?result.match:null;
    }catch(e){if(request!==timeRequest||selected!==date||!$('track-root'))return false;timeView.result=null;timeView.error=e.message||'时间查询失败，请重试';}
    timeView.busy=false;return true;
  }
  async function applyTime(){camera=null;mapTouched=false;selectedPoint=null;timeView.result=null;timeView.busy=timeView.mode!=='all';timeView.error='';drawPage();if(await queryTime())drawPage();}
  function timeControls(){const v=timeView,r=v.result;
    const status=v.busy?'正在查询本机记录…':v.error?v.error:v.mode==='all'?'查看当天全部轨迹':!r?'选择时间后查看':v.mode==='range'?(r.processedPoints?`${v.start}—${v.end==='00:00'?'24:00':v.end} · ${r.processedPoints} 个${rawMode()?'原始':'处理'}点`:'这段时间没有定位记录，试试其他时段'):r.match?`记录于 ${precise(r.match.time)}${r.match.time!==r.target?'，距所选时刻 '+Math.round(Math.abs(r.match.time-r.target)/1000)+' 秒':''} · ${r.match.motionState==='stationary_estimate'?'停留估计':r.match.filtered?'处理后位置':'记录位置'}`:'该时刻前后 60 秒内没有可用位置，不推算缺失位置';
    return `<section class="track-time" aria-label="按时间查看轨迹"><div class="track-time-tabs" role="group" aria-label="时间查看方式">${[['all','全天'],['range','时间段'],['moment','时刻']].map(([id,label])=>`<button data-track-time-mode="${id}" aria-pressed="${v.mode===id}">${label}</button>`).join('')}</div>
      ${v.mode==='range'?`<div class="track-time-fields"><label class="field"><span>开始</span><input id="track-time-start" type="time" value="${esc(v.start)}" required></label><label class="field"><span>结束</span><input id="track-time-end" type="time" value="${esc(v.end)}" required></label></div><div class="track-time-actions"><label class="field"><span>整点时段</span><select id="track-time-hour"><option value="">快速选择一小时</option>${Array.from({length:24},(_,i)=>`<option value="${i}">${String(i).padStart(2,'0')}:00—${String(i+1).padStart(2,'0')}:00</option>`).join('')}</select></label><button class="primary" id="track-time-apply" ${v.busy?'disabled':''}>${v.busy?'查询中…':'查看轨迹'}</button></div>${v.end==='00:00'?'<p class="hint">结束 00:00 表示次日零点。</p>':''}`:v.mode==='moment'?`<div class="track-time-actions"><label class="field"><span>定位时刻</span><input id="track-time-at" type="time" step="1" value="${esc(v.at)}" required></label><button class="primary" id="track-time-apply" ${v.busy?'disabled':''}>${v.busy?'查询中…':'定位'}</button></div>`:''}
      <p id="track-time-status" class="track-time-status ${v.error?'warning':''}" role="status" aria-live="polite">${esc(status)}</p>${v.mode==='moment'&&r&&!r.match&&!v.busy?[['before','此前'],['after','此后']].filter(([key])=>r[key]).map(([key,label])=>`<button class="chip" data-track-near-time="${r[key].time}">${label}记录 ${precise(r[key].time)}</button>`).join(''):''}</section>`;
  }
  function bindTime(){
    document.querySelectorAll('[data-track-time-mode]').forEach(b=>b.onclick=()=>{if(timeView.mode===b.dataset.trackTimeMode)return;timeView.mode=b.dataset.trackTimeMode;applyTime();});
    for(const [id,key] of [['track-time-start','start'],['track-time-end','end'],['track-time-at','at']]){const input=$(id);if(input)input.onchange=()=>{timeView[key]=input.value;applyTime();};}
    if($('track-time-apply'))$('track-time-apply').onclick=()=>{for(const [id,key] of [['track-time-start','start'],['track-time-end','end'],['track-time-at','at']])if($(id))timeView[key]=$(id).value;applyTime();};
    if($('track-time-hour'))$('track-time-hour').onchange=e=>{if(e.target.value==='')return;const hour=Number(e.target.value);timeView.start=String(hour).padStart(2,'0')+':00';timeView.end=String((hour+1)%24).padStart(2,'0')+':00';applyTime();};
    document.querySelectorAll('[data-track-near-time]').forEach(b=>b.onclick=()=>{timeView.at=timeValue(Number(b.dataset.trackNearTime));applyTime();});
  }
  const tileQueue=new M.Queue(2);
  const images=new Map(),clock=t=>t?new Date(t).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}):'—';
  // Recreating the page or receiving new samples must not discard a user's map view.
  // Only explicit date/time/filter/fit actions reset the camera below.
  function page(){return '<section id="track-root"><div class="empty">正在读取本地轨迹…</div></section>';}
  function init(h){hooks=h;document.addEventListener('visibilitychange',()=>{if(!document.hidden)photosChanged();});}
  async function refresh(){if(!$('track-root'))return;if(loading||modeChanging){pendingRefresh=true;return;}loading=true;const selected=date,current=token;try{
    const [result,roads]=await Promise.all([hooks.api('trackDay',{date:selected}),hooks.api('trackRoadStatus').catch(()=>({}))]);if(current!==token||selected!==date||!$('track-root')||modeChanging||mapGesture?.active)return;if(result.filter?.id==='raw')delete result.roadMatching;else if(data.date===result.date&&data.filter?.id===result.filter?.id&&roadStatus.networkId===roads.networkId&&roadStatus.mode===roads.mode&&roads.enabled!==false)result.roadMatching=data.roadMatching;roadStatus=roads;data=result;if(timeView.mode!=='all')await queryTime();if(current!==token||selected!==date||!$('track-root')||mapGesture?.active)return;drawPage();loadRoads(selected,current);loadPhotos();
  }catch(e){hooks.toast(e.message);}finally{loading=false;if(pendingRefresh){pendingRefresh=false;refresh();}}}
  function mount(){token++;timeRequest++;timeView.busy=false;clearInterval(timer);drawPage();refresh();timer=setInterval(()=>{if(!$('track-root')){clearInterval(timer);return;}const editing=$('track-root').contains(document.activeElement)&&['INPUT','SELECT','TEXTAREA'].includes(document.activeElement.tagName);if(!document.hidden&&!editing&&!modeChanging)refresh();},15000);}
  async function importMap(){if(await hooks.confirm('导入离线 OSM 地图？','请选择你有权使用的 Mapsforge .map 矢量地图，或栅格 MBTiles（最大512MB）。地图只存本机，不上传、不自动下载；不支持OSM PBF或矢量MBTiles。','选择文件'))await hooks.api('trackImportMap');}
  async function importRoads(){if(await hooks.confirm('导入离线路网？','请选择本地区域的 OpenStreetMap XML（.osm，最大128MB）。只在本机处理，无需Key；.map、MBTiles、PBF不能作为路网。推断路线用虚线显示，不计里程。','选择文件'))await hooks.api('trackImportRoads');}
  async function loadRoads(selected,current){const request=++roadRequest,filter=data.filter?.id;if(rawMode()||!roadStatus.configured||roadStatus.enabled===false)return;try{const result=await hooks.api('trackRoadMatch',{date:selected});if(request!==roadRequest||current!==token||selected!==date||filter!==data.filter?.id||rawMode()||!$('track-root'))return;data.roadMatching=result;const status=$('track-road-message');if(status)status.textContent=result.message||'';drawMap();}catch(e){if(request===roadRequest&&current===token&&!rawMode()&&selected===date&&$('track-road-message'))$('track-road-message').textContent='道路推断暂不可用，原轨迹仍可查看';}}
  function mapImported(){images.clear();if($('track-root'))refresh();if($('track-settings'))mountSettings();}
  function drawPage(){const root=$('track-root');if(!root)return;const scrollTop=window.scrollY,visible=viewData(),s=data.status||{},raw=rawMode(),summary=T.summary(visible.points||[]),stats={...summary,meters:raw?summary.meters:visible.estimatedMeters??summary.meters,pointCount:visible.processedPoints??(visible.points||[]).length},gaps=T.breaks(visible.points||[]),bridges=T.bridges(visible.points||[]),bridgeIndices=new Set(bridges.map(b=>b.gapIndex)),quality=data.quality||{},isRunning=s.running,warning=s.enabled&&!s.running,online=s.mapMode==='online',offline=s.offlineMap||{};
    root.innerHTML=`<header class="page-bar"><h1>轨迹</h1></header>
      <div class="track-status ${isRunning?'recording':''}"><strong>${isRunning?'● 全天记录中':warning?'◌ 记录已中断':'○ 记录已暂停'}</strong><p>${esc(warning?'请点继续记录。':s.message||'主动开启后，锁屏也可持续定位。')}</p><small>最近定位：${s.lastPoint?new Date(s.lastPoint).toLocaleString('zh-CN'):'尚未收到'}</small></div>
      <div class="actions track-actions"><button class="${isRunning?'secondary':'primary'}" id="track-toggle">${isRunning?'暂停全天记录':'开启 / 继续记录'}</button><button class="secondary" id="track-refresh">刷新轨迹</button></div>
      <label class="field"><span>查看日期</span><input id="track-date" type="date" value="${date}"></label><div class="chip-row">${(data.dates||[]).slice(0,7).map(d=>`<button class="chip ${d===date?'active':''}" data-track-day="${d}">${esc(d.slice(5))}</button>`).join('')}</div>
      <label class="field"><span>轨迹处理</span><select id="track-filter"><option value="motion65" ${!raw&&data.filter?.id!=='weighted65'?'selected':''}>停留与移动识别</option><option value="weighted65" ${data.filter?.id==='weighted65'?'selected':''}>旧版 D · 误差加权平滑</option><option value="raw" ${raw?'selected':''}>原始轨迹</option></select></label>
      <div class="track-map-choice"><label class="field"><span>OpenStreetMap 底图</span><select id="track-map-mode" ${modeChanging?'disabled':''}><option value="offline" ${online?'':'selected'}>离线 · 导入地图 / 已有缓存</option><option value="online" ${online?'selected':''}>在线 · 请求当前区域</option></select></label>${!online?'<button class="secondary" id="track-import-map">导入离线地图</button>':''}</div>
      ${!online?`<p class="hint">${offline.configured?esc(offline.name||'本地地图')+' · 缩放 '+offline.minZoom+'—'+offline.maxZoom:'尚未导入地图；已有缓存可离线使用。放大时会借用低清底图，完全无图块的区域仍显示网格。'}</p>`:''}
      <div class="track-road-controls"><label class="checkbox"><input id="track-road-enabled" type="checkbox" ${!raw&&roadStatus.enabled!==false&&roadStatus.configured?'checked':''} ${raw||!roadStatus.configured?'disabled':''}><span>离线道路推断</span></label><button class="text-button" id="track-import-roads" ${raw?'disabled':''}>${roadStatus.configured?'更换路网':'导入路网'}</button></div><p id="track-road-message" class="hint" role="status">${raw?'原始轨迹不做平滑或道路匹配；保留全部已保存定位点。':esc(data.roadMatching?.message||(roadStatus.importing?'路网导入中…':!roadStatus.configured?'需要本地区域的OSM路网文件；底图不等于路网。':roadStatus.enabled===false?'道路推断已关闭':data.filter?.id==='weighted65'?'请切换“停留与移动识别”以使用道路推断':'正在本机匹配道路…'))}</p>
      ${timeControls()}
      <div class="track-map-frame"><canvas id="track-map" aria-label="${timeView.mode==='moment'?'所选时刻记录位置':timeView.mode==='range'?'所选时段轨迹图':'当天位置轨迹图'}"></canvas><div class="track-map-tools"><button id="track-fit">${timeView.mode==='all'?'全程':'适应'}</button></div><div id="track-map-message" class="track-map-message">${stats.pointCount?'本地路线 · 起点绿色 / 终点橙色':timeView.busy?'正在查询本机记录…':timeView.mode==='all'?'这一天还没有有效定位点':'所选时间没有可显示的位置'}</div></div>
      <div class="track-map-credit"><span id="map-attribution">© <button class="text-button" id="osm-copyright">OpenStreetMap contributors</button>${!online&&offline.attribution?' · '+esc(offline.attribution):''}</span></div><p id="track-selected-point" class="hint">双指缩放，单指拖动；点击路线查看时间与定位状态。</p>
      <p id="track-bridge-legend" class="hint">${raw?'实线：原始定位按时间连线。紫色虚线：缺口推断，不计长度；不代表已确认的实际路线。':'实线：处理后轨迹。紫色虚线：推断，不计里程；有可信路网匹配时沿道路绘制，否则按时间补连，不代表已确认的实际路线。'}</p>
      ${timeView.mode!=='moment'?`<div class="grid"><div class="stat"><span class="stat-label">${raw?'原始连线长度':(timeView.mode==='range'?'时段':'轨迹')+'参考距离'}</span><strong>${(stats.meters/1000).toFixed(2)} <small>km</small></strong><small>${raw?'漂移可能夸大长度，不代表实际行程':'仍按原处理点计算，不按拟合长度计量'}</small></div><div class="stat"><span class="stat-label">轨迹时间</span><strong style="font-size:20px">${clock(stats.firstTime)} — ${clock(stats.lastTime)}</strong><small>${stats.pointCount} 个${raw?'原始':'处理'}点 · ${visible.processedGaps??stats.gaps} 处虚线补连</small></div></div>`:''}
      ${raw?'':'<p class="hint">原始位置与GPX保持不变；道路推断只改变地图绘制，可关闭对照。</p>'}
      ${stats.medianSampleSeconds>15?`<p class="warning">这天${raw?'原始定位':'有效点'}约 ${stats.medianSampleSeconds} 秒一采样，转弯之间可能没有记录；放大底图不会恢复缺失位置。</p>`:''}
      <p class="hint">${raw?(timeView.mode==='all'?`显示 ${stats.pointCount} / 已保存 ${data.totalPoints??stats.pointCount} 个原始点；点击查看时间、误差与来源。`:timeView.mode==='range'?'显示所选时段原始点，不跨边界补线。':'显示所选时刻前后 60 秒内最近的原始定位，不代表精确位置。'):timeView.mode==='all'?`${data.filter?.id==='motion65'?`停留估计 ${quality.stationaryEstimatePoints||0} 点 · 移动 ${quality.movingPoints||0} 点 · 待确认 ${quality.uncertainPoints||0} 点。运动数据缺失时参考位置趋势；不能确认的片段不画成实线。`:``}${data.totalPoints??stats.pointCount} 个已保存原始点 → 处理后 ${stats.pointCount} 个点；${quality.excludedPoints||0} 个未纳入轨迹。`:timeView.mode==='range'?'只显示开始时刻起、结束时刻前的处理点；不跨边界补线，横跨时段边界的道路推断不显示。':'显示所选时刻前后 60 秒内最近的可用处理点，不代表当时的精确位置；误差与停留估计仍可能存在。'}${raw?'':visible.displaySimplified?'地图已简化绘制；距离与GPX仍按完整处理点计算。':data.truncated?'统计为部分数据。':'原始记录仍完整保留。'}</p>
      ${!raw&&timeView.mode==='all'&&data.comparison?`<p class="hint">对照 · ${esc(data.comparison.label)}：${data.comparison.points}点 · ${(data.comparison.meters/1000).toFixed(2)}km · ${data.comparison.gaps}处缺口。切换上方方案查看同一天的路线。</p>`:''}
      ${gaps.length?`<details id="track-gap-list" class="panel"><summary>查看 ${gaps.length} 处虚线补连的时间</summary><p class="hint">已去除密集断点圆圈。按时间顺序连接前一段终点和后一段起点，不按空间最近匹配，也不再限制缺口时长、距离、速度或会话；暂停或长时间未定位期间的路线仍未知。</p><div class="chip-row">${gaps.slice(0,60).map((g,i)=>`<button class="chip" data-track-gap="${i}">${clock(g.a.time)} → ${clock(g.b.time)} · ${g.seconds}秒 · ${esc(g.reason)} · ${bridgeIndices.has(i)?'虚线参考':'无可用端点'}</button>`).join('')}</div>${gaps.length>60?'<p class="hint">仅列出前60处断点，完整记录可导出定位诊断。</p>':''}</details>`:''}
      ${stats.mocked?'<p class="warning">包含模拟定位，仅用于测试。</p>':''}${data.truncated?`<p class="warning">${raw?'超过当天查看上限，数据仍保存在本机。':'超过读取安全上限，仅显示部分，建议导出诊断核对。'}</p>`:''}<button class="secondary full" id="track-data-tools">轨迹数据 · 导出与管理</button>`;
    $('track-toggle').onclick=()=>hooks.safe(async()=>{if(isRunning){await hooks.api('trackStop');setTimeout(refresh,500);}else if(await hooks.confirm('开启全天定位？','锁屏后持续采集，直到你暂停。位置只存手机，不发给AI；持续GPS会耗电。请允许精确定位和通知。','同意并开启')){await hooks.api('trackStart');hooks.toast('请完成系统权限提示');setTimeout(refresh,1200);}});
    $('track-data-tools').onclick=()=>{hooks.editorFrame('轨迹数据',settings());mountSettings();};
    $('track-filter').onchange=e=>hooks.safe(async()=>{token++;roadRequest++;timeRequest++;timeView.result=null;selectedPoint=null;camera=null;mapTouched=false;await hooks.api('trackConfig',{filterMode:e.target.value});await refresh();});
    $('track-refresh').onclick=refresh;$('track-date').onchange=e=>{if(e.target.value)changeDate(e.target.value);};
    root.querySelectorAll('[data-track-day]').forEach(el=>el.onclick=()=>changeDate(el.dataset.trackDay));
    $('track-map-mode').onchange=e=>hooks.safe(()=>changeMode(e.target.value));
    if($('track-import-map'))$('track-import-map').onclick=()=>hooks.safe(importMap);
    $('track-import-roads').onclick=()=>{if(!rawMode())return hooks.safe(importRoads);};$('track-road-enabled').onchange=e=>{if(rawMode())return;return hooks.safe(async()=>{roadRequest++;token++;roadStatus=await hooks.api('trackRoadConfig',{enabled:e.target.checked});delete data.roadMatching;drawPage();refresh();});};
    $('osm-copyright').onclick=()=>hooks.safe(()=>hooks.api('openUrl',{url:'https://www.openstreetmap.org/copyright'}));
    bindTime();bindMap();mountPhotos();drawMap();if(selectedPoint)$('track-selected-point').textContent=pointDetails(selectedPoint);window.scrollTo(0,scrollTop);

    root.querySelectorAll('[data-track-gap]').forEach(el=>el.onclick=()=>{const index=Number(el.dataset.trackGap),g=gaps[index],c=$('track-map');mapTouched=true;camera=T.fit([g.a,g.b],c.clientWidth,c.clientHeight);c.scrollIntoView({block:'center'});$('track-selected-point').textContent=clock(g.a.time)+' → '+clock(g.b.time)+' · '+g.seconds+'秒 · '+g.reason+'；紫色虚线按时间补连，非真实路线，未计入里程。';drawMap();});
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
    const roads=await hooks.api('trackRoadStatus');if(!$('track-settings'))return;const raw=result.filter?.id==='raw',panel=document.createElement('div');panel.className='panel';panel.innerHTML=`<h3>离线路网</h3><p class="hint">${raw?'原始轨迹不使用路网；已有配置保留。':roads.configured?esc(roads.source)+' · '+roads.edges+'条道路边':'尚未导入路网'}</p><button class="secondary" id="track-settings-roads" ${raw?'disabled':''}>导入 / 更换 OSM 路网</button><label class="field"><span>道路推断的出行方式</span><select id="track-road-mode" ${raw?'disabled':''}>${[['auto','自动（依据可信速度）'],['walk','步行'],['bike','骑行'],['car','乘车']].map(([id,label])=>`<option value="${id}" ${roads.mode===id?'selected':''}>${label}</option>`).join('')}</select></label><p class="hint">只支持OSM XML（.osm），最大128MB。地图文件不能代替路网；不上传定位，不用于导航。</p>`;$('track-settings').append(panel);$('track-settings-roads').onclick=()=>{if(!raw)return hooks.safe(importRoads);};$('track-road-mode').onchange=e=>{if(raw)return;return hooks.safe(async()=>{roadRequest++;token++;roadStatus=await hooks.api('trackRoadConfig',{mode:e.target.value});if($('track-root'))refresh();});};
    $('track-export-all').onclick=()=>hooks.safe(async()=>{if(['choosing','writing'].includes(feedback.state))return;if(await hooks.confirm('导出全部定位反馈包？','包含所有日期的精确位置、时间、速度、定位误差及已有运动传感器诊断，包括被过滤的点；可能暴露住址与完整行踪。不含模型 Key 或笔记，不自动上传。文件较多时请保持 App 打开，完成后再自行分享给信任的人。','选择保存位置'))await hooks.api('trackExportAll');});
    $('track-export').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('导出位置轨迹？','GPX包含精确位置和时间，请妥善保管。','导出'))await hooks.api('trackExport',{date:selected});});
    $('track-export-raw').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('导出原始定位？','含未经筛选的精确坐标，可能有漂移、过旧或重复点，不代表可靠行程。仅保存本地，请妥善保管。','导出'))await hooks.api('trackExport',{date:selected,raw:true});});
    $('track-diagnostics').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('导出原始定位诊断？','包含当天原始与过滤后精确位置、时间、速度和可用的GPS信号信息；仅保存本地，不自动发送AI。请只分享给信任的人。','导出'))await hooks.api('trackDiagnostics',{date:selected});});
    $('track-delete').onclick=()=>hooks.safe(async()=>{const selected=$('track-manage-date').value;if(selected&&await hooks.confirm('永久删除当天轨迹？','无法撤销，导出的文件不受影响；采集中仍会产生新位置。','删除')){await hooks.api('trackDeleteDay',{date:selected});hooks.toast('当天轨迹已删除');}});
    $('track-reboot').onchange=e=>hooks.safe(async()=>{const checked=e.target.checked;if(checked&&!s.precisePermission){e.target.checked=false;hooks.toast('请先开启轨迹并允许精确定位');return;}await hooks.api('trackConfig',{resumeAfterReboot:checked});if(checked&&!s.backgroundPermission)await hooks.api('trackBackgroundPermission');});
  }catch(e){if($('track-settings'))$('track-settings').textContent=e.message;}}
  function bindMap(){const c=$('track-map'),gesture=mapGesture=new LifeMapGestures.Gesture();let frame=0;
    const point=e=>{const r=c.getBoundingClientRect();return {x:e.clientX-r.left,y:e.clientY-r.top};};
    const paint=()=>{if(!frame)frame=requestAnimationFrame(()=>{frame=0;if(c===$('track-map'))drawMap(false);});};
    c.onpointerdown=e=>{if(!camera||e.button>0)return;if(gesture.down(e.pointerId,point(e),camera)){mapTouched=true;e.preventDefault();c.setPointerCapture(e.pointerId);}};
    c.onpointermove=e=>{if(!camera)return;const next=gesture.move(e.pointerId,point(e),c.clientWidth,c.clientHeight);if(next){e.preventDefault();camera=next;paint();}};
    const end=(e,cancel)=>{if(!camera||!gesture.points.has(e.pointerId))return;const tap=gesture.up(e.pointerId,camera,cancel);if(tap){const {x,y}=point(e);let nearest=null,min=25,candidates=[];for(const p of viewData().points||[]){const v=T.project(p,camera.z),d=Math.hypot(v.x-camera.x+c.clientWidth/2-x,v.y-camera.y+c.clientHeight/2-y);if(d<min){min=d;nearest=p;}if(rawMode()&&d<25)candidates.push({point:p,distance:d});}if(rawMode()&&nearest){candidates=candidates.filter(p=>p.distance<=min+2).sort((a,b)=>a.point.time-b.point.time||(Number(a.point.id)||0)-(Number(b.point.id)||0));const previous=candidates.findIndex(p=>p.point===selectedPoint);nearest=candidates[(previous+1)%candidates.length].point;}if(nearest){selectedPoint=nearest;$('track-selected-point').textContent=pointDetails(nearest);}}
      if(frame){cancelAnimationFrame(frame);frame=0;}drawMap(!gesture.active);};
    c.onpointerup=e=>end(e,false);c.onpointercancel=e=>end(e,true);c.onlostpointercapture=e=>end(e,true);
    $('track-fit').onclick=()=>{mapTouched=false;camera=null;drawMap();};
  }
  function pointDetails(p){return precise(p.time)+' · '+p.lat.toFixed(5)+', '+p.lon.toFixed(5)+' · 原始定位误差估计约'+Math.round(p.reportedAccuracy||p.accuracy)+'米 · 来源：'+(p.provider||'未记录')+(p.mocked?' · 模拟定位':'')+(p.motionState==='stationary_estimate'?' · 停留估计':p.motionState==='moving'?' · 移动估计':p.motionState==='uncertain'?' · 位置待确认（可能沿用上次停留位置）':'')+(p.filtered?' · 处理后位置':' · 原坐标')+(p.raw&&p.id!=null?' · 点 '+p.id:'');}
  async function drawMap(load=true){
    const canvas=$('track-map');if(!canvas)return;
    const visible=viewData(),points=visible.points||[],seq=++mapToken,w=canvas.clientWidth,h=canvas.clientHeight,dpr=Math.min(2,devicePixelRatio||1),parts=T.segments(points),bridges=T.bridges(points),roads=!rawMode()&&roadStatus.enabled!==false?T.roadsInTime(data.roadMatching,timeView.mode==='all'?null:visible):[];
    canvas.dataset.timeMode=timeView.mode;canvas.dataset.pointCount=String(points.length);canvas.dataset.selectedTime=selectedPoint?String(selectedPoint.time):'';canvas.dataset.roadRoutes='0';
    canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);const ctx=canvas.getContext('2d');ctx.scale(dpr,dpr);
    function grid(){ctx.fillStyle='#e8eee6';ctx.fillRect(0,0,w,h);ctx.strokeStyle='#d2ded2';ctx.lineWidth=1;ctx.setLineDash([]);for(let x=0;x<w;x+=32){ctx.beginPath();ctx.moveTo(x,0);ctx.lineTo(x,h);ctx.stroke();}for(let y=0;y<h;y+=32){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke();}}
    const locatedPhotos=visiblePhotos().filter(p=>Number.isFinite(p.lat)&&Number.isFinite(p.lon));
    grid();if(!parts.length&&!locatedPhotos.length){$('track-map-message').textContent=timeView.busy?'正在查询本机记录…':timeView.mode==='all'?'这一天还没有有效定位点':'所选时间没有可显示的位置';paintPhotos();return;}if(!camera)camera=T.fitLocations(parts.flat().concat(locatedPhotos),w,h);
    const view={...camera},left=view.x-w/2,top=view.y-h/2,mode=data.status.mapMode||'offline',tiles=M.visible(view,w,h),key=t=>mode+'/'+t.z+'/'+t.x+'/'+t.y;
    canvas.dataset.zoom=String(view.z);canvas.dataset.mapMode=mode;
    canvas.dataset.centerX=String(view.x);canvas.dataset.centerY=String(view.y);
    paintPhotos();
    const current=()=>seq===mapToken&&canvas===$('track-map')&&(data.status?.mapMode||'offline')===mode&&!modeChanging;
    function drawPath(path,hideMatched){ctx.beginPath();let pen=false;for(let i=1;i<path.length;i++){if(hideMatched&&T.roadCovered(path[i-1],path[i],roads)){pen=false;continue;}const a=T.project(path[i-1],view.z),b=T.project(path[i],view.z);if(!pen)ctx.moveTo(a.x-left,a.y-top);ctx.lineTo(b.x-left,b.y-top);pen=true;}ctx.stroke();}
    function paintRoute(){ctx.save();ctx.setLineDash([7,5]);ctx.strokeStyle='#8754a1';ctx.lineWidth=2;for(const bridge of bridges)drawPath(bridge.path||[bridge.a,bridge.b],true);ctx.restore();ctx.setLineDash([]);ctx.strokeStyle=data.filter?.color||'#b47719';ctx.lineWidth=3;ctx.lineJoin='round';for(const part of parts)drawPath(part,true);
      ctx.save();ctx.setLineDash([9,5]);ctx.strokeStyle='#8754a1';ctx.lineWidth=3;for(const road of roads)drawPath(road.path,false);ctx.restore();canvas.dataset.roadRoutes=String(roads.length);
      // Raw observations remain inspectable, including isolated and overlapping points.
      if(rawMode()){ctx.fillStyle='#216a5480';for(const p of parts.flat()){const v=T.project(p,view.z),x=v.x-left,y=v.y-top;if(x< -3||x>w+3||y< -3||y>h+3)continue;ctx.beginPath();ctx.arc(x,y,2.5,0,2*Math.PI);ctx.fill();}}
      // Processed routes show only start/end markers to avoid dense gap circles.
      const all=parts.flat();for(const [p,color] of [[all[0],'#216a54'],[all[all.length-1],'#b67e45']]){if(!p)continue;const v=T.project(p,view.z);ctx.beginPath();ctx.arc(v.x-left,v.y-top,6,0,2*Math.PI);ctx.fillStyle=color;ctx.fill();ctx.strokeStyle='#fff';ctx.lineWidth=2;ctx.stroke();}
      if(selectedPoint&&points.some(p=>p.time===selectedPoint.time)){const p=selectedPoint,v=T.project(p,view.z),x=v.x-left,y=v.y-top;ctx.save();ctx.setLineDash([]);ctx.fillStyle='#216a5430';ctx.beginPath();ctx.arc(x,y,18,0,2*Math.PI);ctx.fill();ctx.fillStyle='#216a54';ctx.beginPath();ctx.arc(x,y,8,0,2*Math.PI);ctx.fill();ctx.strokeStyle='#fff';ctx.lineWidth=3;ctx.stroke();const label=precise(p.time);ctx.font='600 14px sans-serif';const tw=ctx.measureText(label).width,lx=Math.max(4,Math.min(w-tw-16,x-tw/2-6)),ly=Math.max(24,y-24);ctx.fillStyle='#fff';ctx.fillRect(lx,ly-18,tw+12,25);ctx.fillStyle='#174b3b';ctx.fillText(label,lx+6,ly);ctx.restore();}}
    function entryFor(t){
      const direct=images.get(key(t));if(direct)return direct;
      for(let z=t.z-1;z>=0;z--){const scale=2**(t.z-z),entry=images.get(mode+'/'+z+'/'+Math.floor(t.x/scale)+'/'+Math.floor(t.y/scale));if(entry)return entry;}
      return null;
    }
    function paint(){
      if(!current())return;grid();
      for(const t of tiles){const entry=entryFor(t);if(!entry)continue;try{const r=M.crop(t,entry,entry.image.naturalWidth,entry.image.naturalHeight);ctx.drawImage(entry.image,r.sx,r.sy,r.sw,r.sh,t.px,t.py,t.size,t.size);}catch(e){}}
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
  function visiblePhotos(){
    if(photoResult.date!==date||!photoResult.enabled||!photoResult.readable)return [];
    const photos=photoResult.photos||[];if(timeView.mode==='all')return photos;
    if(timeView.busy||!timeView.result)return [];
    if(timeView.mode==='range')return photos.filter(p=>p.time>=timeView.result.start&&p.time<timeView.result.end);
    const target=timeView.result.target??new Date(date+'T'+timeView.at).getTime();return photos.filter(p=>Math.abs(p.time-target)<=60000);
  }
  function mountPhotos(){
    photoNodes.clear();const frame=document.querySelector('.track-map-frame');if(!frame)return;
    frame.insertAdjacentHTML('beforeend','<div id="track-photo-markers" aria-label="照片拍摄地点"></div>');
    const credit=document.querySelector('.track-map-credit');credit.insertAdjacentHTML('afterend','<section class="track-photo-panel" id="track-photo-panel" aria-label="轨迹照片"></section>');paintPhotoPanel();
  }
  function paintPhotoPanel(){
    const panel=$('track-photo-panel');if(!panel)return;
    const s=photoResult,photos=visiblePhotos(),ready=s.enabled&&s.readable,missing=photos.filter(p=>!Number.isFinite(p.lat)).length;
    panel.innerHTML=`<div class="track-photo-heading"><strong>沿途照片</strong><button class="text-button" id="track-photo-enable">${s.enabled?(s.readable?'停止同步':'授权相册'):'开启同步'}</button></div><p class="track-photo-summary" role="status">${photoLoading?'正在读取本机照片…':!s.enabled?'开启后，自动读取照片的时间和拍摄地点，仅在本机显示。':!s.readable?'尚未获得相册权限；请在系统弹窗中选择允许访问的照片。':`${photos.length} 张照片${missing?' · '+missing+' 张无拍摄地点':''}${s.limited?' · 仅限已授权照片':''}${!s.locationReadable?' · 未授权照片位置信息':''}${s.truncated?' · 当天仅显示前 500 张':''}`}</p>${ready&&!s.locationReadable?'<button class="text-button" id="track-photo-location">授权照片位置信息</button>':''}${photos.length?`<div class="track-photo-strip">${photos.map(p=>`<button data-track-photo="${p.id}" aria-label="查看 ${clock(p.time)} 的照片${Number.isFinite(p.lat)?'，照片拍摄定位':'，无拍摄地点'}"><img src="/track-photos/${p.id}/thumb.jpg" loading="lazy" alt="${clock(p.time)} 的照片"><span>${clock(p.time)}${Number.isFinite(p.lat)?'':' · 无地点'}</span></button>`).join('')}</div>`:''}`;
    $('track-photo-enable').onclick=()=>hooks.safe(async()=>{const enabled=!s.enabled||!s.readable;if(enabled&&!await hooks.confirm('在轨迹上显示相册照片？','读取你授权的照片、拍摄时间和照片位置信息，仅本机展示，不上传、不发送 AI。相机需开启保存位置信息；部分照片可能没有地点。','继续授权'))return;await hooks.api('trackPhotoConfig',{enabled});photosChanged();});
    if($('track-photo-location'))$('track-photo-location').onclick=()=>hooks.safe(()=>hooks.api('trackPhotoConfig',{enabled:true}));
    panel.querySelectorAll('[data-track-photo]').forEach(b=>b.onclick=()=>openPhoto(photos.find(p=>p.id===b.dataset.trackPhoto)));
  }
  async function loadPhotos(){
    if(photoLoading||photoResult.date===date&&Date.now()-photoLoadedAt<60000)return;
    const selected=date,request=++photoRequest;photoLoading=true;paintPhotoPanel();
    try{const result=await hooks.api('trackPhotos',{date:selected});if(request!==photoRequest||selected!==date||!$('track-root'))return;photoResult=result;photoLoadedAt=Date.now();if(!mapTouched)camera=null;}
    catch(error){if(selected===date)hooks.toast('照片读取失败：'+error.message);}
    finally{photoLoading=false;if(selected===date&&$('track-root')){paintPhotoPanel();drawMap();}if(photoDirty){photoDirty=false;photoLoadedAt=0;if($('track-root'))loadPhotos();}else if(selected!==date&&$('track-root'))loadPhotos();}
  }
  function photosChanged(){photoLoadedAt=0;if(photoLoading){photoDirty=true;return;}if($('track-root'))loadPhotos();}
  function openPhoto(photo){if(!photo)return;hooks.editorFrame('沿途照片',`<div class="track-photo-preview"><img src="/track-photos/${photo.id}/full.jpg" alt="${esc(precise(photo.time))} 的照片"><p>${esc(new Date(photo.time).toLocaleString('zh-CN'))}${photo.timeSource==='added'?' · 相册加入时间，未获取拍摄时间':' · 拍摄时间'}</p><p>${Number.isFinite(photo.lat)?`照片自带定位 · ${photo.lat.toFixed(5)}, ${photo.lon.toFixed(5)}`:'照片无可用拍摄地点，未关联虚假位置'}</p></div>`);}
  function paintPhotos(){
    const overlay=$('track-photo-markers'),canvas=$('track-map');if(!overlay||!canvas)return;if(!camera){for(const node of photoNodes.values())node.remove();photoNodes.clear();return;}
    const photos=visiblePhotos(),groups=new Map(),w=canvas.clientWidth,h=canvas.clientHeight;
    for(const p of photos){if(!Number.isFinite(p.lat)||!Number.isFinite(p.lon))continue;const point=T.project(p,camera.z),x=point.x-camera.x+w/2,y=point.y-camera.y+h/2;if(x<24||x>w-24||y<28||y>h-24)continue;const key=Math.floor(x/72)+':'+Math.floor(y/72);if(!groups.has(key))groups.set(key,{x,y,items:[]});groups.get(key).items.push(p);}
    const keep=new Set();for(const group of [...groups.values()].slice(0,40)){const p=group.items[0],key=p.id;keep.add(key);let node=photoNodes.get(key);if(!node){node=document.createElement('button');node.className='track-photo-marker';node.innerHTML=`<img src="/track-photos/${p.id}/thumb.jpg" alt=""><span></span>`;overlay.appendChild(node);photoNodes.set(key,node);}node.style.left=group.x+'px';node.style.top=group.y+'px';node.setAttribute('aria-label',group.items.length>1?`查看此处 ${group.items.length} 张照片`:`查看 ${clock(p.time)} 的拍摄照片`);node.querySelector('span').textContent=group.items.length>1?String(group.items.length):'';node.onclick=()=>{if(group.items.length===1)openPhoto(p);else{hooks.editorFrame('此处的照片',`<div class="track-photo-group">${group.items.map(item=>`<button data-photo-group="${item.id}"><img src="/track-photos/${item.id}/thumb.jpg" alt="${clock(item.time)} 的照片"><span>${clock(item.time)}</span></button>`).join('')}</div>`);document.querySelectorAll('[data-photo-group]').forEach(b=>b.onclick=()=>openPhoto(group.items.find(item=>item.id===b.dataset.photoGroup)));}};}
    for(const [key,node] of photoNodes)if(!keep.has(key)){node.remove();photoNodes.delete(key);}
  }
  window.TrackUI={init,page,mount,settings,mountSettings,mapImported,feedbackProgress,photosChanged};
})();
