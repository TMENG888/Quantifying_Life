const {test}=require('node:test'),assert=require('node:assert/strict');
const F=require('../app/src/main/assets/feishu-import.js');
const url='https://sample.feishu.cn/docx/SyntheticFeishuSampleToken';
test('飞书链接规范化：去掉分享参数，支持知识库及国际站',()=>{
 assert.equal(F.canonical(url+'?from=from_copylink#x'),url);
 assert.equal(F.canonical('https://a.larksuite.com/wiki/ABCDefghijkl/'),'https://a.larksuite.com/wiki/ABCDefghijkl');
 assert.equal(F.canonical('https://a.larkoffice.com/docx/ABCDefghijkl'),'https://a.larkoffice.com/docx/ABCDefghijkl');
});
test('拒绝内网、冒充域名、用户名、非文档链接和不安全协议',()=>{
 for(const x of ['http://a.feishu.cn/docx/ABCDefghijkl','https://feishu.cn.evil.test/docx/ABCDefghijkl','https://evilfeishu.cn/docx/ABCDefghijkl','https://127.0.0.1/docx/ABCDefghijkl','https://user@a.feishu.cn/docx/ABCDefghijkl','https://a.feishu.cn:444/docx/ABCDefghijkl','https://a.feishu.cn/base/ABCDefghijkl','https://a.feishu.cn/docx/ABCDefghijkl/../../login'])assert.equal(F.canonical(x),null,x);
});
test('直接发链接或明确导入才打开预览，普通问答不拦截',()=>{
 for(const text of [url,'['+url+']('+url+')','导入学习笔记 '+url,'同步：'+url+'，这是今天的学习笔记'])assert.equal(F.intent(text),url,text);
 for(const text of ['总结这篇文章 '+url,'这篇文章的观点是什么？'+url,'对比 '+url+' https://a.feishu.cn/docx/ABCDefghijkl','介绍飞书是什么'])assert.equal(F.intent(text),null,text);
});
test('从中文说明中提取，不把说明拼入文档编号',()=>{
 assert.deepEqual(F.links(url+'这是我的学习笔记'),[url]);
 assert.deepEqual(F.links(url+'?from=from_copylink。'),[url]);
});
test('学习日期默认来自文档创建日，不用最后编辑日或导入当天冒充',()=>{
 assert.equal(F.initialDate({documentCreatedDate:'2026-03-27'}),'2026-03-27');
 assert.equal(F.initialDate({documentCreatedDate:'2026-03-27',existing:{date:'2026-04-02'}}),'2026-04-02','preserve manually selected dates on updates');
 for(const data of [{},{documentCreatedDate:'2026-02-30'},{documentCreatedDate:'2026-03-27<script>'},{lastEditedDate:'2026-10-27'}])assert.equal(F.initialDate(data),'');
 assert.equal(F.sourceDate({documentCreatedDate:'2024-02-29'}),'2024-02-29');
 assert.equal(F.sourceDate({documentCreatedDate:'2025-02-29'}),'');
});
test('导入的普通符号与代码不显示多余转义、不产生 HTML 注入',()=>{
 const M=require('../app/src/main/assets/chat-markdown.js');
 assert.equal(M.inline('scrapy\\_field \\*普通星号\\*'),'scrapy_field *普通星号*');
 assert.equal(M.inline('\\`字面反引号\\`'),'`字面反引号`');
 assert.equal(M.inline('`code\\_field`'),'<code>code\\_field</code>');
 assert.equal(M.inline('List<int> <script>x</script>'),'List&lt;int&gt; &lt;script&gt;x&lt;/script&gt;');
 assert.equal(M.inline('**真实加粗**'),'<strong>真实加粗</strong>');
});
