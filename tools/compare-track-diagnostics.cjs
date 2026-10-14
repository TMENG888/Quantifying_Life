// Local read-only replay of the sole Android production D filter. Never prints coordinates or writes input files.
const fs=require('node:fs'),path=require('node:path'),{spawnSync}=require('node:child_process');
const file=process.argv[2];if(!file)throw Error('Pass the local diagnostic JSON path');
const data=JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
if(!Array.isArray(data.rawPoints)||data.rawPoints.length>20000)throw Error('Expected at most 20000 raw points');
const number=v=>Number.isFinite(v)?v:'NaN';
const lines=data.rawPoints.map(p=>[p.id,p.time,p.lat,p.lon,p.accuracy,number(p.speed),number(p.speedAccuracy),String(p.session).replace(/[\t\r\n]/g,''),p.provider,p.telemetry?.satellitesUsed??-1,number(p.telemetry?.meanCn0),p.telemetry?.gnssAgeMs??-1].join('\t')).join('\n');
const root=path.resolve(__dirname,'..'),jdk=fs.readdirSync(path.join(__dirname,'android/jdk')).find(n=>fs.statSync(path.join(__dirname,'android/jdk',n)).isDirectory());
const result=spawnSync(path.join(__dirname,'android/jdk',jdk,'bin/java.exe'),['-cp',path.join(root,'build/filter-test'),'com.insight.quantlife.tests.TrackReplay'],{input:lines,encoding:'utf8',maxBuffer:1024*1024});
if(result.status!==0)throw Error(result.stderr||'Replay failed');
console.log('Local diagnostic replay; distances are smoothed-path estimates, NOT verified travel distance.');
process.stdout.write(result.stdout);
