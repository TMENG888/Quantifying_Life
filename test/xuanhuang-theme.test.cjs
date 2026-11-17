const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const assets=path.join(__dirname,'../app/src/main/assets'),read=p=>fs.readFileSync(path.join(assets,p),'utf8');
const css=read('tokens.css'),root=css.split('/* Declared paper theme:')[0];
const colors=Object.fromEntries([...root.matchAll(/(--[\w-]+):\s*(#[\da-f]{6});/g)].map(m=>[m[1],m[2]]));
function luminance(hex){const a=hex.slice(1).match(/../g).map(x=>parseInt(x,16)/255).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);return a[0]*.2126+a[1]*.7152+a[2]*.0722;}
function ratio(a,b){a=luminance(a);b=luminance(b);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
test('everyday normal text, muted labels and accent pairs retain accessible contrast',()=>{
 for(const bg of ['--color-paper','--color-paper-2','--color-paper-3']){
  for(const ink of ['--color-ink','--color-muted','--color-accent'])assert.ok(ratio(colors[bg],colors[ink])>=4.5,bg+' '+ink);
  assert.ok(ratio(colors[bg],colors['--color-rule-2'])>=3,bg+' field boundary');
 }
 for(const [bg,ink] of [['--color-field','--color-field-ink'],['--color-accent','--color-accent-ink'],['--color-reading-paper','--color-reading-ink'],['--color-error-well','--color-error']])assert.ok(ratio(colors[bg],colors[ink])>=4.5,bg+' '+ink);
});
test('all stylesheet tokens resolve to a declaration and palette loads before components',()=>{
 const sheets=fs.readdirSync(assets).filter(n=>n.endsWith('.css')).map(read).join('\n');
 const names=new Set([...sheets.matchAll(/(--[\w-]+)\s*:/g)].map(m=>m[1]));
 for(const m of sheets.matchAll(/var\((--[\w-]+)\)/g))assert.ok(names.has(m[1]),'undeclared '+m[1]);
 const html=read('index.html');assert.ok(html.indexOf('href="tokens.css"')<html.indexOf('href="style.css"'));
});
