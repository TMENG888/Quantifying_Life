// Read-only local diagnosis and replay. Never prints absolute coordinates or model keys.
const fs=require('node:fs'),path=require('node:path'),{spawnSync}=require('node:child_process'),T=require('../app/src/main/assets/tracks.js');
const filename=process.argv[2];if(!filename)throw Error('Pass a diagnostic JSON path');
const data=JSON.parse(fs.readFileSync(filename,'utf8').replace(/^\uFEFF/,'')),raw=data.rawPoints||[],shown=data.filtered?.points||[],intervals=[];
const clock=t=>new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(t);
let poor=0,reliable=0,telemetry=0;const providers={},gaps=[];
for(let i=0;i<raw.length;i++){const p=raw[i];providers[p.provider]=(providers[p.provider]||0)+1;if(p.accuracy>35)poor++;if(Number.isFinite(p.speed)&&Number.isFinite(p.speedAccuracy)&&p.speedAccuracy<=.6)reliable++;if(Object.keys(p.telemetry||{}).length)telemetry++;if(i&&p.session===raw[i-1].session){const a=raw[i-1],dt=(p.time-a.time)/1000;intervals.push(dt);if(dt>60)gaps.push({from:clock(a.time),to:clock(p.time),seconds:+dt.toFixed(1),endpointDisplacementMeters:Math.round(T.distance(a,p))});}}
intervals.sort((a,b)=>a-b);const stats=T.summary(shown);
console.log(JSON.stringify({date:data.date,appVersion:data.appVersion,rawPoints:raw.length,shownPoints:shown.length,medianIntervalSeconds:intervals[Math.floor(intervals.length/2)],poorAccuracyPoints:poor,reliableSpeedPoints:reliable,telemetryPoints:telemetry,providers,storedDisplay:{segments:stats.segments,meters:stats.meters},rawGaps:gaps},null,2));
if(process.argv.includes('--replay')){
  const root=path.resolve(__dirname,'..'),jdk=fs.readdirSync(path.join(__dirname,'android/jdk')).find(n=>fs.statSync(path.join(__dirname,'android/jdk',n)).isDirectory()),java=path.join(__dirname,'android/jdk',jdk,'bin/java.exe');
  const number=v=>Number.isFinite(v)?v:'NaN',lines=raw.map(p=>[p.id,p.time,p.lat,p.lon,p.accuracy,number(p.speed),number(p.speedAccuracy),String(p.session).replace(/[\t\r\n]/g,''),p.provider,p.telemetry?.satellitesUsed??-1,number(p.telemetry?.meanCn0),p.telemetry?.gnssAgeMs??-1].join('\t')).join('\n');
  const result=spawnSync(java,['-cp',path.join(root,'build/filter-test'),'com.insight.quantlife.tests.TrackReplay'],{input:lines,encoding:'utf8',maxBuffer:1024*1024});if(result.status!==0)throw Error(result.stderr||'Replay failed');console.log('Current production filter replay (not true travelled distance): '+result.stdout.trim());
}
