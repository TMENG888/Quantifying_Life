const test=require('node:test'),assert=require('node:assert/strict');
const M=require('../app/src/main/assets/chat-markdown.js');
test('code blocks survive blank lines, comments and literal Markdown with exact offsets',()=>{
 const code='import os\n\n# 不是标题\n\tprint("**不是加粗**")  \n![不是图片](zhishi-media:'+'a'.repeat(64)+')\n<script>neverRun()</script>\n';
 const note='前文\n\n'+M.fenced(code,'python')+'\n\n后文';const blocks=M.blocks(note),codes=blocks.filter(b=>b.type==='code');
 assert.equal(blocks.map(b=>b.source).join(''),note);assert.equal(codes.length,1);assert.equal(codes[0].code,code);assert.equal(codes[0].language,'python');
 for(const b of blocks)assert.equal(note.slice(b.start,b.end),b.source);
 const rendered=M.render(codes[0].source);assert.equal((rendered.match(/<pre/g)||[]).length,1);assert.doesNotMatch(rendered,/<h3>|<img|<script>|<strong>/);assert.match(rendered,/# 不是标题/);
});
test('longer and tilde fences do not terminate at nested shorter literals',()=>{
 for(const marker of ['````','~~~~']){const note=marker+'markdown\n```python\n# code\n```\n\nlast\n'+marker+'\n\nend';const codes=M.blocks(note).filter(b=>b.type==='code');assert.equal(codes.length,1);assert.ok(codes[0].code.endsWith('last'));assert.equal(codes[0].closed,true);assert.match(M.render(note),/<p>end<\/p>$/);}
 const embedded='`````\nmore\n`````';const source=M.fenced(embedded,'markdown');assert.equal(M.blocks(source)[0].code,embedded);
});
test('adjacent code, CRLF, unclosed fence and trailing whitespace remain addressable',()=>{
 for(const note of ['```py\na\n```\n```js\nb\n```','x\r\n\r\n```python\r\na\r\n\r\n# b\r\n```\r\nend','```python\nx\n\n# y','\n\nonly prose\n\n']){const blocks=M.blocks(note);assert.equal(blocks.map(b=>b.source).join(''),note);for(const b of blocks)assert.equal(note.slice(b.start,b.end),b.source);}
 assert.equal(M.blocks('```py\nx\n\n# y')[0].closed,false);
});
