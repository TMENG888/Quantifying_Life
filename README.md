# 知时 · 学习与工作

个人 Android App，包名 `com.insight.quantlife`，版本 1.4.3（versionCode 11）。Java 原生宿主 + 本地 HTML/CSS/JavaScript UI，无需后台服务器。

手机用户请先读 [用户使用说明](docs/用户使用说明.md)。App 内入口：设置 → 关于知时 → 使用说明；随安装包保存，无需联网。

1.4.3验证：87项Node、34项生产轨迹过滤回归、18项Android11截图／离线说明／覆盖升级检查通过（139项）。在1.4.2种入合成记录及虚构密钥，再覆盖安装1.4.3；核对原记录、学习目标和加密配置保留。关闭模拟器Wi-Fi和移动数据后打开说明，核对10个章节、滚动、返回、关闭、重开和无横向溢出；实际系统截图已人工查看。未在小米实体机验证系统截图手势或长截图，未使用真实模型Key，也未重新跑旧版本所有网络测试。

1.4.3明确允许用户主动进行系统截图，新增折叠式离线使用说明，涵盖学习／工作、API Key 配置、AI 与链接读取、轨迹、备份恢复及隐私。不自行截屏或上传屏幕，不绕过设备管理策略。截图和系统最近任务预览可能显示敏感笔记或位置，分享前应检查。

1.4.2修复微信短链接紧接中文说明时把说明吞进URL的问题；只按已知微信公众号/s/ID格式分离，不全局截断合法中文网址。保留用户提供的文章要点，不以网页摘要替换。新增conversation.js，将记录、文章问题、历史查询、主题回顾、规律分析分开：新文章记录／问题不传全量统计、每日序列、无关Wiki和上轮长答；记录回复按应用实际接纳的候选数量生成简短状态，不使用模型的占位标题承诺或无关长答。来源状态可折叠展开。

1.4.2增加Exa的web_fetch_exa读取工具：直接网页读取→Exa同URL有限正文→必要时同URL搜索。微信验证码重定向不继续访问、不把临时参数送给搜索；Exa仍仅接收原公开URL，不接收个人历史、笔记或任何Key。读取正文最多5000字，单次读取预算18秒，单次搜索22秒，单链接外部请求总体40秒；最多3链接，现有桥接等待预算160秒。读取失败、限流、协议变化均可手动补标题／截图，不自动切换付费服务。contentStatus区分limited_text、metadata_only与unavailable；不声称全文、实时、已看视频或读完整本书。未开放agent_run、任意远程工具或无限模型自主工具循环。

1.4.1新增Exa官方匿名HTTP MCP搜索兜底，无需搜索API Key、无需电脑常开或自建后端。新装／没有搜索设置时默认选择Exa，仍在发送链接前征求联网同意；旧Tavily设置与关闭偏好原样保留，可在设置→链接识别与搜索切换为Exa。模型聊天仍需模型Key。1.4.1只开放web_search_exa；1.4.2增加web_fetch_exa，均用于原公开链接，不开放付费研究工具或任意MCP工具。

`ExaMcp.java`固定连接`https://mcp.exa.ai/mcp`，不传Authorization、Cookie或任何模型／Tavily Key；初始化与工具发现后核对schema（当前objective必填），支持JSON／SSE、协商会话和协议。每次搜索总预算22秒，响应上限256KiB，最多3条结果；仅原URL和固定核对要求进入搜索。成功结果在进程内缓存10分钟／最多32项，429冷却2分钟，不自动重试或切换收费服务。同页校验仍由LinkReader执行；无可靠标题时请用户补充。匿名服务有免费限流、政策及网络可达性风险，不保证永久免费或无限额度。参考 https://exa.ai/docs/reference/exa-mcp 。

1.4.0新增公开链接识别：发送前征求联网同意，原生只读提取标题／作者／平台／内容类型及有限文本。裸链接直接生成待确认候选，0分钟、空个人笔记；带问题或记录描述时将不可信网页材料交给所选模型。网页读取失败可启用Tavily搜索，固定官方HTTPS端点，搜索Key独立Keystore加密；只发送URL，不发送个人历史或模型Key。只接受同页搜索命中补标题。无可靠标题会追问。候选确认、查重、原生校验后才入库。候选出现时输入框停止浮动，防止遮挡确认按钮。

`LinkReader.java`限制标准公网HTTPS（HTTP自动尝试HTTPS），拒绝凭据／敏感参数／本机及私网，逐跳验证重定向及DNS，以已验证IP建立TLS并验证原主机名，防DNS重绑定；不执行JS、不带cookies、不加载外部资源。最多3个链接，网页最多512KiB，有限文本最多5000字；不宣称读取全文、观看视频、读取整本书。`links.js`提供URL提取、来源核对、裸链接候选和时长／笔记保护。Exa无需搜索Key；选择Tavily时填写独立搜索Key，留空保留、可删除，切换Exa不会删除旧Key也绝不将其发送给Exa。Tavily协议参考 https://docs.tavily.com/documentation/api-reference/endpoint/search 。

1.3.3新增短断点推测图层：默认开启，可关闭；时间相邻、同会话、60—120秒且距离／GPS误差／相邻速度方向满足条件时绘制紫色虚线。可靠原始GPS速度可辅助判断，静止、不可信速度、方向反转和长空缺保留断开。图层不进入summary、轨迹数据库、普通GPX或AI；原生过滤规则与数据库版本不变。只有显示偏好存WebView本地存储。BridgeSeedTest在1.3.2上种入匿名样本，覆盖1.3.3运行BridgeOverlayTest验证保留原始数据／GPX及开关绘制；不要在个人手机运行测试。

1.3.2修复：低频历史轨迹不再套用5秒采样的续行与尾段窗口；最近三次连续位移可重新确认移动，避免绕行后长期锁在旧锚点；跨午夜读取足够上下文。保留原始定位误差估计，显示孤立点／断点两端及可定位查看的断点时间，低频采样明确提示点间直线不等于道路。仍保留60秒空缺保护，不恢复不可信网络定位、不补画缺失路线、不做未经验证的坐标偏移。实际诊断文件只读重放，不进入源码、安装包或AI。

1.3.1修复：采样空缺阈值从5分钟改为60秒，屏幕／距离／GPX统一分段；弱GNSS证据覆盖过度乐观精度，连续弱点明确断开；已确认的无速度步行保留转弯与短尾段；静止锚定结束时按已显示位置校验跳移。原始位置不删除或覆盖。新增本地定位诊断导出（原始／过滤后位置、速度精度、可用卫星信息），用户确认后保存文件，不进入AI。

## 功能

学习条目、平台和类型筛选、笔记、标签、每日目标、阶段性掌握程度与证据；工作班次、多段休息、跨夜自然日统计；模型连接配置、图片/文本/PDF/DOCX 附件、AI 候选确认、查重、LLM 检索规划与持久化主题 Wiki；SQLite 持久化；JSON 导出和记录恢复。平台不再使用 WebView 原生 datalist，而是自由输入和显式可收起的行内常用平台按钮。

Android 8 / API26以上，WebView83以上。网络用于模型、用户同意的链接读取／可选搜索与可选地图；全天轨迹声明精确／粗略／后台定位、定位前台服务、通知和开机权限，由用户主动开启。通过系统文档选择器取得单个文件访问，无全盘存储、通讯录或后台录屏权限。

## 架构与边界

`app/src/main/java/.../MainActivity.java`：WebView 虚拟 HTTPS 资源、异步受控桥、SQLite、Keystore AES-GCM 设置、HTTP 模型请求、文档选择器、Android 分享入口、导出和事务恢复。

`app/src/main/assets/domain.js`：记录校验、时长和日期范围算法、标题规范化、近似查重、关键词检索、确定性 RAG 汇总、模型 JSON 解析。Node 与前端共用，方便独立测试。

`app/src/main/assets/wiki.js`：检索计划校验、主题匹配、投入和掌握程度轨迹、Wiki 引用和来源指纹校验、分批整理、日期隔离的问答上下文。

`app/src/main/assets/app.js`：六个主页面、编辑器、模型选择、附件处理、Wiki 编译及浏览、检索上下文、提取候选确认。Web 预览仅用于界面测试，使用浏览器 localStorage；不调用真实模型、不保存密钥。

`index.html/style.css/compatibility.js`：本地 UI、响应式布局、旧 WebView 兼容层。PDF.js 3.11.174 legacy 构建经 esbuild 转为 Chrome83目标，关闭 PDF eval。DOCX 使用受限 ZIP/XML 文本解析，拒绝 DOCTYPE/ENTITY。

模型工具 `prepare_life_records` 准备答复、候选记录和引用，不直接写数据库。候选经前端校验、用户确认、原生校验后写入。支持 Chat Completions function tools 与 Responses function tools；缺乏工具能力但返回同结构 JSON 的模型可以解析，不代表任意厂商均兼容。

SQLite `records` 保存 indexed id/type/date/title/platform 和完整 JSON payload；`messages` 保留最近100条对话；v2新增 `wiki_pages`，从v1升级只增表，不删除数据。密钥只保存在 Keystore 加密设置中，不返回给网页、不进入备份。数据库没有额外加密；不声称端到端加密，发送 AI 时模型厂商能够读取输入。

一般聊天先调用 `plan_record_search`，全历史主题问题按需调用 `compile_learning_wiki`，最后调用 `prepare_life_records`；裸链接候选直接使用网页元信息，不依赖模型猜测。Wiki 摘要存本地，可打开引用笔记，来源新增、修改或删除后失效；重新整理失败则只用原始记录检索。日期与时长来自本地确定性计算。不是向量库，不同步外部 Wiki，不自动抓取文章全文。

一次最多6批、每批20条且约36000字符、单条笔记最多6000字符；多批增加一次合并请求。超出内容明确标为部分整理，主题统计仍计入全部匹配记录。问答原文片段和每日序列有上下文限额，不能视为完整笔记导出。掌握程度由用户自评、实践或测验证据标注，不从时长自动打分，不声称个人关联是通用学习阈值。相关笔记会发送给所选厂商，整理增加API费用。

备份导出包含 Wiki 缓存；恢复目前只合并原始记录，Wiki 可重新生成，不恢复 Key、对话或模型配置。

## Windows 构建

全天轨迹：TrackingService为location类型前台服务，用户同意后申请精确位置与通知；TrackingBootReceiver仅在此前开启、用户启用恢复且具备后台权限时尝试启动。只记录GPS、约5秒请求一次；展示要求精度<=35米及可用信号证据不过弱。质量较差但有效的原始点保留诊断，非法／过旧／超过100米精度的点拒绝。定位年龄同时检查单调时钟与墙上时间，支持GnssStatus，不要求设备必定返回卫星数据。TrackStore独立SQLite schema3，从schema1／2仅新增缺少的speed、speed_accuracy、telemetry列，不改变quantlife.db或删除位置。TrackFilter.java由历史显示、距离和GPX共用，不做道路吸附或交通方式识别；极慢移动、弱信号、速度缺失或单向漂移仍有边界，不能保证导航级精度。

轨迹页只保留路线／距离／时间和地图模式；停留及小米设置快捷入口移除，导出／删除／重启偏好移到设置。默认离线OSM，MapTiles只读取本地图块或缓存，不联网；OfflineMap支持用户自备的标准TMS栅格MBTiles（最大512MB，PNG/JPEG/WebP，缩放0—18），不支持PBF或偏移坐标底图，APK不内置城市街道包。在线模式经同意后仅请求OSM当前视图，不批量下载。CARTO兼容桥与旧加密Key保留，但新版UI不使用。位置、离线文件、地图Key均不进入模型上下文／学习备份；位置库未额外加密。

测试：1.3.2有44项Node与33项生产Java过滤器回归。SparseTrackSeedTest须在原1.3.1专用模拟器种入合成31秒采样后覆盖安装1.3.2，SparseTrackTest验证22项低频、覆盖保留、午夜上下文、孤立点与断点UI。GapFixTest覆盖信号空缺、弱GNSS、无速度转弯、原始／展示诊断、GPX同分段与文件选择器；LiveGpsRefinementTest验证真实5秒模拟GPS回调与锁屏。哪些在当前版本重新跑过以交付验证记录为准。测试APK只用于可清除的专用模拟器，绝不可在个人手机运行。旧数据库升级须先种入对应旧版本fixture，不将schema3复测冒充schema2迁移。定位回调测试设appops mock_location为allow；权限测试要求屏幕唤醒、解锁并使用未授权新数据。OfflineMapUiTest依赖合成地图。小米实体精度与电量必须实际验证。

setup-modern-emulator.ps1可下载官方API35镜像并校验归档；约需额外10GB测试空间。若C盘空间不足，将仅用于测试的AVD及镜像置于有空余空间的目录并更新AVD注册路径，不要清理个人手机或其他数据。测试时顺序运行模拟器以减少内存压力。真实小米全天GPS／耗电仍未验证；当前网络OSM超时、CARTO未用真实Key验证，详见发布说明。

本源码包含预处理后的 PDF 资源；不含大型 Android/JDK 下载、构建产物和签名密钥。

Windows PowerShell / PowerShell7，推荐使用 PowerShell7；Node22可用于算法测试和预览，Android构建使用JDK17。

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\setup.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\build.ps1
npm.cmd test
node .\tools\preview.cjs
```

这里的 ExecutionPolicy Bypass 仅针对该次已检查的本地构建脚本进程，不更改系统全局策略；手机安装不需要运行任何 PowerShell 脚本。

setup 从官方 Azul / Google 下载任务本地JDK17、API35平台和build-tools35，固定URL和归档校验值。PowerShell7 的 `Invoke-WebRequest -NoProxy` 用于避开失败的系统代理；网络需可连接下载站。手动构建路径使用 aapt2 → javac --release8 → d8 → zipalign → apksigner，不依赖 Gradle。兼容 Windows aapt2 生成的资源反斜杠路径。

同时提供标准 Gradle项目，可在 Android Studio 中导入（需自备Gradle工具链、SDK35和release签名配置，没有打包Gradle wrapper）。手动 build.ps1 是已实际验证的路径；Gradle路径未独立验证。

更新 PDF 资源：`npm.cmd ci` 后运行 `tools/refresh-pdf.ps1`；脚本重新下载官方 npm 发布的 legacy资源并用 `tools/transpile-pdf.cjs` 转译。PDF第三方许可见 `app/src/main/assets/vendor/LICENSE`。

首次构建会创建 `tools/quantlife-release.jks` 与 `tools/signing-secret.xml`。**源码压缩包不含已发布APK的签名密钥**，解压后重新构建会生成新签名，不能覆盖安装已发布APK。本机原项目保留了原签名文件，以后同签名更新必须使用它们；密码文件使用 Windows DPAPI，仅原Windows用户可解密。备份前注意密钥及密码的独立可恢复性，不要提交到公共仓库。

## 测试

`npm.cmd test`：算法和记录解析测试。

`tools/setup-emulator.ps1`：准备独立任务本地Android模拟器。`tools/build-test.ps1`：编译 instrumentation 测试APK。`tools/mock-ai.cjs`：本地合成模型服务，仅接受显式虚构的测试密钥；不得使用真实Key。

```powershell
node .\tools\mock-ai.cjs
# 另一个终端：先安装 build/quantlife-1.4.3.apk 和 build/test/tests.apk 到专用测试模拟器
# SmokeTest需要空白测试数据，勿在升级验证的中途运行。
.\tools\android\platform-tools\adb.exe -s emulator-5582 shell am instrument -w com.insight.quantlife.tests/com.insight.quantlife.tests.SmokeTest
```

SmokeTest 使用反射验证原生服务，并通过 WebView操作 UI：数据库、查重、Keystore、模型HTTP/工具、候选确认、引用、文档/图片、两种API结构、原生校验和导入事务。只在可清除数据的专用模拟器使用，不要直接在个人手机运行测试APK。

1.4.1验证：64项Node测试、34项生产轨迹过滤回归、67项Exa合成协议／Android设置测试、61项原生链接／Keystore检查、46项实际WebView链接流程检查、8项1.4.0覆盖升级检查及2项真实公开网络检查通过（共282项检查）。ExaLiveTest以模拟403页面强制触发真实匿名Exa搜索，核对B站视频标题／类型；另外通过实际App界面发送同一公开链接，确认网页优先读取、0分钟空笔记候选和确认前不入库。真实网络测试不使用模型Key，中文搜索单次约2.65秒。ExaMcpTest不访问外部服务；ExaLiveTest会访问公开B站链接与Exa，需单独按需运行，不用于用户私人数据。

1.4.2验证：82项Node、34项轨迹过滤、67项Exa协议／设置、61项原生链接／密钥、46项WebView链接流程、8项覆盖升级、18项有限正文读取合成测试、12项公众号真实读取／实际UI、2项B站真实搜索／UI均通过，共330项检查。覆盖安装来源为1.4.1，原ExaUpgradeTest日志沿用早期版本标签，实际ADB包版本已核对。ArticleLiveTest真实访问用户给定公开文章，模型部分仅调用本地合成服务，刻意注入无关长答并验证被记录状态展示器替换；未测试真实DeepSeek回答质量。公众号标题“企业大数据如何变成利润？”与相关正文实测读取成功。一次独立匿名搜索测试短暂返回unavailable，随后一次人工重测成功，体现服务波动，不宣称永久可用。Node/合成测试不依赖外部网络；网络测试按需单独运行，不使用私人数据或真实模型Key。

1.1验证：23项Node测试、29项SmokeTest、30项UpdateTest通过。升级测试先在1.0用BeforeUpdateTest种入合成记录，再不清数据覆盖安装1.1，验证旧数据/Key、平台面板收起、日期检索、Wiki持久化及23条来源的多批编译与合并。UpdateTest依赖旧版种入数据及模拟API配置，不是独立启动即可运行的测试。

真实厂商 API 和 Xiaomi HyperOS 实体设备尚需用户提供有效配置后验证。测试fixture和工具中的密钥字符串全是虚构数据。

## 下一阶段

语义向量检索、学习计时器、CSV报表、跨设备加密同步、语音输入、扫描PDF OCR、后台提醒以及财务/健身/睡眠模块都不属于这一版本。
