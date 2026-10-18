const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const read=p=>fs.readFileSync(path.join(__dirname,'..',p),'utf8');
test('all-history feedback is separate from date-based export and requires privacy confirmation',()=>{
 const ui=read('app/src/main/assets/track-ui.js');for(const t of ['track-export-all','导出全部定位反馈包','所有日期','完整行踪','不自动上传','trackExportAll','aria-live="polite"','track-day-tools'])assert.ok(ui.includes(t),t);
 assert.match(ui,/hooks\.confirm\('导出全部定位反馈包？'/);assert.ok(!ui.includes("hooks.api('trackExportAll',{date:"));
});
test('feedback uses paginated raw rows, not limited/filtered day results or a giant byte array',()=>{
 const src=read('app/src/main/java/com/insight/quantlife/TrackFeedback.java');for(const t of ['LIMIT "+PAGE','id>? AND id<=?','snapshotMaxId','raw-points.jsonl','manifest.json','rawSha256','MessageDigest','rawTruncated','telemetryParseError','lockInterruptibly'])assert.ok(src.includes(t),t);
 for(const t of ['120000','store.day(','store.diagnostics(','ByteArrayOutputStream','HttpURLConnection','privateSettings'])assert.ok(!src.includes(t),t);
});
test('all-history document lifecycle is isolated from existing shared export bytes',()=>{
 const src=read('app/src/main/java/com/insight/quantlife/MainActivity.java');assert.ok(src.includes('EXPORT_TRACK_FEEDBACK=13'));assert.ok(src.includes('intent.setType("application/zip")'));assert.ok(src.includes('TrackFeedback.write'));assert.ok(src.includes('feedbackEvent("cancelled"'));assert.ok(src.includes('文件可能不完整'));assert.ok(read('app/src/main/assets/app.js').includes("name==='trackFeedback'"));
});
