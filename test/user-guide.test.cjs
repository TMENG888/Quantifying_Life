const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const guide = read('app/src/main/assets/user-guide.html');

test('offline guide covers each user workflow and important limitations', () => {
  for (const text of ['第一次使用', 'API Key', '净工作时间', '待确认的记录', '截图', '紫色虚线', '恢复只合并原始记录', '验证码', '最近任务预览']) assert.ok(guide.includes(text), text);
  assert.equal((guide.match(/<details(?: open)?>/g) || []).length, 10);
  assert.equal((guide.match(/<\/details>/g) || []).length, 10);
});
test('bundled guide has no executable or external resources', () => {
  assert.doesNotMatch(guide, /<script|<iframe|<img|<link|on\w+\s*=|(?:src|href)\s*=/i);
  assert.ok(read('app/src/main/assets/app.js').includes("fetch('user-guide.html')"));
  assert.ok(read('app/src/main/assets/app.js').includes('data-action="user-guide"'));
});
test('repository manual contains the same guidance as the in-app guide', () => {
  const manual = read('docs/用户使用说明.md');
  for (const match of guide.matchAll(/<(?:p|li|summary|blockquote)(?: [^>]*)?>(.*?)<\/(?:p|li|summary|blockquote)>/g)) assert.ok(manual.includes(match[1]), match[1]);
});
test('screenshot policy permits system capture without adding capture permissions', () => {
  assert.ok(read('app/src/main/java/com/insight/quantlife/MainActivity.java').includes('clearFlags(WindowManager.LayoutParams.FLAG_SECURE)'));
  assert.doesNotMatch(read('app/src/main/AndroidManifest.xml'), /READ_MEDIA_IMAGES|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|CAPTURE_VIDEO_OUTPUT/);
});
test('version identifiers agree for the new signed upgrade', () => {
  assert.equal(JSON.parse(read('package.json')).version, '1.4.3');
  assert.equal(JSON.parse(read('package-lock.json')).packages[''].version, '1.4.3');
  assert.match(read('app/build.gradle'), /versionCode 11/);
  assert.match(read('tools/build.ps1'), /'--version-code','11','--version-name','1.4.3'/);
});
