# 04 · 格式与模板垂直切片（template 域）

> 承接 03 之后的第四个垂直切片，落地「课程模板库 → 结构树与规则比对 → 格式校验 → 一键修复成版 → 格式转换」闭环，前端补齐教师模板管理三屏、学生模板只读屏、预览页格式校验面板、一键修复确认与格式转换页。
> 状态取值：`未开始` / `进行中` / `已完成`。**后端 D-01~D-07 与前端 D-08~D-14 已实现并通过全部审查（2026-11-06~08：内部终审 + 1106 符合性审查×4 + 1107 阶段全量审查 + 1108 阶段复审 + 1108 新一轮检查，代码面 P1/P2/P3 全部闭环），回归基线 `mvn test` 400/400（含真库 IT×6 与 P-07 错题集）、`pytest` 33/33、`pnpm test` 169/169、`pnpm build` 通过**。**D-15 已于 2026-11-09 闭合（浏览器手工全链路除外）：真库 ALTER 迁移、三件套真库 IT、P-07 错题集 20/20 recall=100%、ai-service 引擎级双向转换反例组、HTTP 三服务全链 e2e 逐步 PASS（含修复前后 52→48 阻断清零与双向下载魔数验证），全部观测见 `docs/test/FormatTest.md`**。
> 契约源：需求 §5.3（REQ-01/02/04/08/14；REQ-07 引用生成为 Could 级「本期未实现」不做）、P-07（格式问题定位准确率 ≥95%）、详细设计 §3.3（表 3-5 核心类、§3.3.2 比对与修复伪代码、表 3-6 九接口）、§5.1（42202 语义，本期仅提交域消费）、§6.2（UT-11/12/13）、§6.3（e2e「格式校验修复」场景）、概要 §3（格式与模板域）、§4.3（P4 模板规则：只用文档绑定的模板版本，模板变更不影响历史版本）、§5.4（模板规则缓存与版本号失效）、数据库设计 §3.4.20–3.4.26、`05_系统设计/数据库脚本/01_schema.sql`（template/template_version/format_rule/document_element/format_check/format_issue/conversion_task/reference_item 共 8 表已全量建齐，**本切片原设计零 DDL；1107 阶段审查 P2-2 经批准追加一列迁移 `ALTER TABLE edudoc_template_version ADD parse_error VARCHAR2(1000)`，为至今唯一 DDL 变更**；`ck_oss_role` 含 CONVERTED、`ck_docver_type` 含 EDIT、`fk_document_3` document→template_version 已备）、原型 `student.md` 11–15 屏、`teacher.md` 09–14 屏、`05_学生端/06/08` 设计说明。
> 现状基线：`template` 域仅 package-info；权限点与角色映射已 seed（STUDENT：TEMPLATE_READ/FORMAT_CHECK/FORMAT_CONVERT；TEACHER：另有 TEMPLATE_MANAGE；OPS：TEMPLATE_MANAGE/FORMAT_CHECK，**无 FORMAT_CONVERT**，`04_seed.sql` L33–35）；`ErrorCode.FORMAT_BLOCKER(42202)` 已定义；前端 `format`/`templates` 导航为占位页；DocumentView 四面板 Tabs 与 `highlightRange` 高亮原语可复用；Outbox 路由表已有 parse/embed/ai-task 三条流。

## 用户拍板决策（2026-11-06，四项均按推荐口径拍板）

| # | 决策 | 拍板内容 | 未采纳备选 |
| --- | --- | --- | --- |
| J1 | PDF 文档的校验范围 | 排版样式（字体/字号/行距/页边距）只对 **.docx** 全量校验；PDF 文档发起校验 → 任务 `ERROR` + 干净提示「PDF 暂无法提取排版信息，Word 文档可校验」。不做 pdfbox 样式反推（成本大、正确率低，威胁 P-07） | PDF 限定项校验（仅参考文献正则 + 页数），届时 `format_rule.target_selector` 加 DOC_SCOPE |
| J2 | 转换引擎落点 | ai-service 新增 `POST /v1/convert`（PDF→DOCX 用 `pdf2docx` 纯 Python；DOCX→PDF 子进程调 `soffice --headless`），后端 ConversionWorker 经既有 `X-Internal-Token` 通道调用——两个引擎一个落点，后端不 spawn 外部进程。与「ai-service 只做模型」的概要口径冲突，需拍板 | 后端 worker 直接 ProcessBuilder(soffice)，PDF→DOCX 本期不做 |
| J3 | 图表编号「增删自动更新」（REQ-01/02 编辑态） | 本期无在线编辑器（协作切片未做），落地为**识别与展示**：解析产出 `document_element`（图 x-y 编号、题注、代码块元素）+ CAPTION/CODE 规则参与校验；粘贴高亮、增删重排归协作切片 | — |
| J4 | 学生「按模板新建」（REQ-08.3） | 后端 POI 按模板规则生成大纲 .docx（标题层级+页面设置）→ putObject → 建档走 02 上传通道状态机，学生得一份可直接编辑的初始文档 | 仅生成模板文件下载，学生手工再上传 |

**口径记录（非决策）**：修复产物为改样后的新 .docx，其版本源对象 `object_role=ORIGINAL`（「每个版本对应一个原始对象」数据库设计 §3.2，schema 合法）；`CONVERTED` 只用于转换结果，不产生新版本。

## 不属本切片

作业发布/提交/统计（05 导出与统计域；42202 由提交域消费）、REQ-07 参考文献自动生成（Could，本期未实现；`edudoc_reference_item` 表闲置）、协作编辑与版本列表/对比/恢复 UI、运维端页面、模板规则缓存（概要 §5.4，本切片直读规则表，规则量小——记债务 R7）、SSE（轮询沿用既有口径）、编辑器内代码语法高亮与编号重排（J3）。

## 前置 · 分支与环境（用户配合项）

- 分支：`feat/fullstack-template-format`（已建，当前分支）。
- 用户侧环境：LibreOffice 桌面版安装并给出 `soffice` 可执行路径（环境变量 `EDUDOC_SOFFICE_BIN`，Windows 本机 + 届时部署的服务器同款依赖）；模板样例四份课程设计报告模板（`01_需求调研/需求调研原件`，用于 D-02 解析演示与 D-15 样本集起点）；OSS/Redis 凭据沿用 02，无新增外部凭据。

---

## D-01 后端 · 模板主档管理

- **引用**：表 3-6 `GET /templates`、`POST /templates`；需求 §5.3.1.4（停用/删除不影响历史版本）；数据库设计 §3.4.20。
- **现状**：接口与表访问全缺；`04_seed.sql` 权限映射就绪。
- **验收标准**：
  - `GET /api/v1/templates?courseId=&status=`（TEMPLATE_READ）→ 分页模板列表（含 currentVersionNo、ruleCount）；非本课程的 TEACHER 仅可见本人维护（owner_id）模板，学生仅见课程当前已发布（status=P）模板；越权 40401 口径同 02。
  - `POST /api/v1/templates`（TEMPLATE_MANAGE）`{courseId,templateName,description?}` → 201 草稿（status=D）；同名（uk_template_name）40901；courseId 须为申请人（TEACHER/OPS）任教/治理课程，否则 40001/40301。
  - `PATCH /api/v1/templates/{id}/status`（契约外辅助，B-02 先例回填）`{status: P|A}`——停用= A（学生端不可见新使用，已绑定文档不受影响）；删除=软口径：仅 D 且无版本可删（物理删草稿行），已发布模板不可删只可停用（表 3-22 无软删列，**不改 DDL**，理由记文档）。
  - 模板行归属校验一律经 owner_id + course 双重口径；写接口带 `Idempotency-Key`（自动过滤器）；审计 `TEMPLATE_CREATED/TEMPLATE_STATUS_CHANGED`。
- **测试**：`TemplateServiceTest`（学生只见已发布、教师跨课程不可见、同名 40901、停用后历史文档绑定不变=UT-13 前半）。
- **依赖**：无 | **状态**：已完成

## D-02 后端 · 模板版本解析与发布

- **引用**：表 3-6 versions/upload+publish 两接口；§3.3.1（发布时解析 Word 模板→ `edudoc_format_rule`，规则配置 JSON）；表 3-23/3-24；UT-13。
- **验收标准**：
  - `POST /api/v1/templates/{id}/versions`（TEMPLATE_MANAGE，multipart 上传 .docx 模板）→ version_no+1 新行（validation_status=PENDING）+ 源对象入 OSS（role=ORIGINAL）；`TemplateRuleService` 用 POI XWPF 解析 styles.xml/sectPr：标题层级→HEADING_1..3 字体字号行距、正文默认样式、页边距（A4 与 margin 四值）、页眉页脚存在性、题注样式、CODE 段落样式 → `edudoc_format_rule` 批量行（rule_type/target_selector/severity/auto_fixable/rule_order 全按字典值）。
  - **规则 JSON 契约**（本文档定义、双端对齐）：`rule_config_json = {expect:{...}, tolerance?, fix:{...}?}`——按 rule_type 固定键集（FONT→family(ASCII 西文槽)+eastAsia(中文槽，第三轮复审 P3-1 回填：两槽独立比对、独立写回，缺 eastAsia 键则中文槽不参与校验)、SIZE→pt、LINE_SPACING→multiplier 或 lineRule(exact|atLeast)+pt（第四轮 P1-2：固定磅值显式表达，比对/生成/修复共用 LineSpacing 模型；expect 键集缺失保存即拒）、MARGIN→top/bottom/left/right mm+width/height mm(纸型，纸型属可修复面)、HEADER_FOOTER→present、CAPTION→pattern+perChapter（pattern 须合法正则）、CODE→style+keepIndent、REFERENCE→standard 预留）；`PUT /api/v1/templates/{id}/versions/{versionId}/rules`（契约外辅助，屏 10「编辑课程模板」规则表单）整组替换草稿版规则（仅 D/PENDING 可改，已发布不可改）。
  - 解析失败（非 docx/坏文件）→ validation_status=INVALID（42201 语义入 result_summary 列），可编辑规则后转 VALID；`POST .../versions/{versionId}/publish`：仅 VALID → template.current_version_id 前移 + published_by/at + 模板 status=P + 审计 `TEMPLATE_PUBLISHED`；**历史版本行与规则行不可变**（再编辑=新版本，UT-13：已绑定旧版本文档校验仍用旧版规则）。
- **测试**：`TemplateRuleParseTest`（真样例 docx 解析出规则集断言键值；坏文件 INVALID）、`TemplatePublishTest`（INVALID 拒发 40901、发布前移、历史版本规则不可变）。
- **依赖**：D-01 | **状态**：已完成

## D-03 后端 · 文档结构树解析器

- **引用**：表 3-25 `edudoc_document_element`；§3.3.2 伪代码 `structureParser.parse(documentVersion)`；REQ-01/02、屏「代码与图表规范化」。
- **验收标准**：单服务 `StructureParser`（template.domain）：读版本源对象（.docx）POI 遍历 → HEADING（层级+章号推导）/FIGURE/TABLE/CODE/EQUATION 元素行——element_key=`{TYPE}-{正文元素索引}`（H-/T-/F-/C-/E- 前缀 + bodyIndex，版本内稳定唯一；1106 审查 P3-2 口径回填，原「{type}-{序}」文字以此为准）、chapter_no/sequence_no/display_label（`图 2-1` 章内格式）/caption_text/location_json（**段落索引+字符区间；docx 无绝对页码，page_no 留空为既定口径**，J2 文本视图按分页标记近似）；按 (document_version_id, element_key) uk 幂等覆盖写（重解析安全）。触发点：校验 worker 执行前按需解析（不入 02 Tika 通道、不加新事件——解析与校验同进程一次取数）；`GET /api/v1/documents/{id}/elements`（契约外辅助，学生规范化屏数据源）。
- **测试**：`StructureParserTest`（样例文档标题层级、图表题注捕获、章内编号连续、重跑幂等）。
- **依赖**：无 | **状态**：已完成

## D-04 后端 · 格式校验任务（创建 + Worker + 清单查询）

- **引用**：§3.3.2 check() 伪代码；表 3-6 format-checks 两接口；表 3-24/3-26/3-27；UT-11；需求 §5.3.2（逐项检查、问题四要素、统计与结论）；J1。
- **验收标准**：
  - `POST /api/v1/documents/{id}/format-checks`（FORMAT_CHECK + `Idempotency-Key`）：文档 READY 且版本源对象为 .docx（**否则任务直接落 ERROR + result_summary 提示，J1**）；模板选择口径：`document.template_version_id` 非空用之（概要 §4.3 P4），否则取课程当前已发布模板，均无 → 40901「课程尚未发布模板」；活跃去重沿用 03 R1（同版本+模板版本+用户 QUEUED/RUNNING 复用 checkId）。单事务插 `edudoc_format_check(QUEUED)` + outbox `FORMAT_CHECK_REQUESTED` → 路由新流 `edudoc:stream:format-check`。
  - `FormatCheckWorker`（cg-format-check）：D-03 解析结构树 → 按 rule_order 逐规则 `targets=select(targetSelector)` → docx 实际样式提取（styles.xml + 段落直接格式 + sectPr）→ `matches` 比对产出 `edudoc_format_issue` 行（issue_type/location_type=PARAGRAPH|ELEMENT|PAGE+location_ref JSON/expected_value/actual_value/severity/auto_fixable/guidance 文案）；汇总 total/blocking → PASSED（blocking=0）/FAILED；异常 → ERROR（不半写：问题行与汇总同事务）。
  - `GET /api/v1/format-checks/{checkId}`（status/counts/结论）+ `GET .../issues?page&pageSize&severity=`（文档 READ，非可见者 40401）；审计 `FORMAT_CHECK_DONE`（计数不含正文）。
- **测试**：`FormatCheckWorkerTest`（mock POI 树：UT-11 字号不符出 BLOCKER+期望值、WARNING 项不阻断、全 PASS 零行、异常整批回滚）、`FormatCheckServiceTest`（模板选择口径、非 docx 落 ERROR、去重复用、40401）。
- **依赖**：D-02、D-03 | **状态**：已完成

## D-05 后端 · 一键修复成版与复审

- **引用**：表 3-6 `POST /format-checks/{checkId}/fix`；§3.3.2 autoFix 伪代码（含 verifyAgain）；UT-12；屏「一键修复确认」；需求 §5.3.2.4。
- **验收标准**：入参 `{issueIds}`：仅取 auto_fixable=Y 且 fix_status=OPEN（不可修复项忽略并回执列出）；`AutoFixService` 在版本源 docx 的 POI 副本上逐条应用 `rule_config_json.fix`（段落字体双槽/字号/行距（倍数或固定磅值）/sectPr 页边距+纸型——**修复面五类（第四轮 P1-1：纸型纳入，杜绝「标记已修复但复审仍失败」假闭环）**，超出面规则 auto_fixable 必须为 N，R1）→ 新 oss_object（role=ORIGINAL，口径记录）→ 新 `document_version(version_no+1, version_type=EDIT)` + current_version_id 前移 + issue 置 FIXED + 审计 `FORMAT_FIX_APPLIED`，单事务；并发口径：校验后原版本已被其他写前移 → 40901（同文档活跃 EDIT 链基于最新版重放，沿用 03 C-07 冲突策略）。**当前版本源对象非 .docx（03 润色 .txt 链）→ 40901 明确指引**（R2 分叉记录）。修复提交后自动补发同模板版本的新校验任务（伪代码 verifyAgain；UT-12：修复后仍有阻断 → 新版本产生且新任务 FAILED）。
- **测试**：`AutoFixServiceTest`（四类修复各改对样式且版本五写、不可修复项跳过回执、链分叉 40901、并发 40901、修复后自动复审事件）。
- **依赖**：D-04 | **状态**：已完成

## D-06 后端 · 格式转换任务

- **引用**：表 3-6 conversions 两接口；表 3-28；需求 §5.3.3（批量、原文件不变、失败原因、记录可检索）；概要 I03；J2。
- **验收标准**：`POST /api/v1/conversions`（FORMAT_CONVERT + `Idempotency-Key`）`{documentId, targetFormat: PDF|DOCX}`（批量=前端循环多任务，后端单文档单任务，`// ponytail: 批量聚合任务待需求出现再加`）→ `edudoc_conversion_task(QUEUED)` + outbox `CONVERSION_REQUESTED` → `edudoc:stream:conversion`；Worker 调 ai-service `POST /v1/convert`（最终内部契约=**multipart**：`file` 源文件字节 + `targetFormat` 字段，成功回二进制、失败回结构化 `{code,message}`；X-Internal-Token/X-Trace-Id 透传同 AiServiceClient——1106 审查 P2-3 口径回填，ai-service 不接触 OSS 凭据，后端读超时=引擎预算+15s）：DOCX→PDF=`soffice --headless --convert-to pdf`（独立 `-env:UserInstallation` 临时目录避免单实例锁，超时 120s → FAILED 50401，R5）；PDF→DOCX=pdf2docx；加密/损坏 → 结构化 `{code: ENCRYPTED|CORRUPTED|UNSUPPORTED|TIMEOUT(=50401), message}` 落 FAILED 保留记录（第四轮 P3-1：与实现/README 统一，`reason` 仅为任务查询字段）；成功结果入 OSS（role=CONVERTED，**不建版本、current_version 不动**——需求 §5.3.3.3「原文件保持不变」）+ 签名下载 URL（GET 响应携带，900s 口径同 02）。`GET /api/v1/conversions/{taskId}`：仅发起人（40401 口径）含 status/reason/downloadUrl；`GET /api/v1/conversions?documentId=` 记录列表。
- **测试**：`ConversionServiceTest`（权限/仅发起人/QUEUED 幂等）、`ConversionWorkerTest`（mock 引擎：成功五写、ENCRYPTED 映射、超时 50401）。
- **依赖**：D-04（流与 worker 基建复用）、J2 拍板 + ai-service `/v1/convert`（convert 端点 pytest 13 例已落：soffice 缺失 503、ENCRYPTED 422（OLE 容器魔数+stderr 双路）、UNSUPPORTED 415（非 zip/非 %PDF 容器先判，先输入后依赖）、token 401、25MB 上限、超时/损坏分类；pdf2docx 为懒加载可选依赖组，部署用 `uv sync --extra convert`）| **状态**：已完成

## D-07 后端 · 按模板新建文档（J4）

- **验收标准**：`POST /api/v1/templates/{id}/documents`（DOCUMENT_CREATE，学生限课程已发布模板；`{title, docType}`）→ POI 按规则集生成大纲 .docx（HEADING 层级骨架 + sectPr 页面设置 + 封面占位段落）→ 服务端 putObject（role=ORIGINAL，私有桶既有口径）→ 建档四写复用 B-04 同构事务（version_type=UPLOAD、document.template_version_id=所用版本——**自此该校验默认用绑定版本，UT-13 后半成立**）+ 补发解析事件走既有通道 → 201 `{documentId,versionId}`。
- **测试**：`TemplateProvisionTest`（生成 docx 可被 Tika/POI 读、绑定版本落库、学生越权课程 40301）。
- **依赖**：D-02、D-03（样式写回同一 POI 工具面）| **状态**：已完成

## D-08 前端 · API 模块

- **验收标准**：`web/src/api/template.ts`（列表/新建/状态/版本/规则/发布/按模板新建）+ `web/src/api/format.ts`（校验创建/查询/issues/fix/elements/转换创建/进度/记录），类型对齐 D-02 规则 JSON 契约与表 3-26/3-27/3-28 字段；复用 http.ts 幂等键/trace/401；vitest 参数断言。
- **依赖**：D-01~D-07 契约定型即可并行 | **状态**：已完成

## D-09 前端 · 教师课程模板库（屏 09/10/11）

- **验收标准**：`pages/teacher/Templates.tsx` 替换占位：列表（课程筛选、状态徽标 D/P/A、版本号）→ 新建草稿 Modal → 编辑页（屏 10：左规则表格按 rule_type 分组可编辑 expect/tolerance/severity/auto_fixable，右「学生端效果预览」静态示意；上传模板 docx 解析出规则后进入同一表格）→ 发布确认 Modal（屏 11：版本、适用范围、变更说明、历史版本只读折叠）。停用/删除按 D-01 语义；无 TEMPLATE_MANAGE 权限整页 40301 态。
- **测试**：规则表格序列化=JSON 契约、发布门（INVALID 不可点）、历史版本只读。
- **依赖**：D-08 | **状态**：已完成

## D-10 前端 · 学生课程模板页（屏 11 只读）

- **验收标准**：`pages/student/Templates.tsx`：只读要求视图（按当前发布版规则渲染「字体/字号/行距/页边距/图表编号/参考文献」要求表，**不出现编辑/发布/版本维护操作**——设计 05 明文）+「按模板新建文档」（D-07 → 成功后跳预览页轮询解析态）。工作台快捷入口「按模板新建」接线。
- **依赖**：D-08 | **状态**：已完成

## D-11 前端 · 预览页格式校验面板（屏 13/12）

- **验收标准**：DocumentView Tabs 增「格式校验」：发起（默认模板口径与 40901 提示）→ 2s 轮询（QUEUED/RUNNING 进度、恢复口径同 03 C-11，checkId 存 localStorage）→ 结论横幅（通过/未通过+计数统计）→ 问题清单（类型/位置/期望值 vs 当前值/severity 徽标），**点击定位=按 location_ref 的段落索引映射到文本视图块并 highlightRange 近似高亮**（J2 口径，映射失败退化为「跳至该页标记」）；不可修复项展示 guidance；非 docx 文档发起 → ERROR 干净提示（J1）。学生/教师共用。
- **测试**：轮询恢复、定位回调、ERROR 态、统计渲染。
- **依赖**：D-08、后端 D-04 | **状态**：已完成

## D-12 前端 · 一键修复确认 Modal（屏 14/13）

- **验收标准**：问题清单勾选（auto_fixable 才可勾）→ 确认弹窗（影响范围说明「生成新修订版本」、不可修复项清单提示）→ fix → 成功提示「已生成新版本 v_n」+ 自动复审进行中态 + 清单刷新；40901 冲突提示重查。
- **依赖**：D-11 | **状态**：已完成

## D-13 前端 · 格式转换（屏 15/14）

- **验收标准**：我的文档列表行/多选批量「格式转换」→ Modal 选目标格式（默认自动匹配，可显式指定 PDF/Word，方向合法性前端预检、不符者跳过提示）→ 任务进度（轮询 GET /conversions/{taskId}）→ **下载=presigned GET 直链（唯一口径，1108 复审确认；fetch blob+token 头口径废弃——签名 URL 属红线允许的短期凭据，且避免大文件经主应用内存中转）**；转换记录抽屉（文档选择+关键字检索含失败原因+源/目标/起止时间全列，SUCCEEDED 行即时签名）。OPS 端不接（无 FORMAT_CONVERT 权限点，R8）。
- **测试**：方向预检、进度轮询、失败原因渲染。
- **依赖**：D-08 | **状态**：已完成

## D-14 前端 · 代码与图表规范化页（屏 12）

- **验收标准**：`pages/student/Normalize.tsx`（只读，J3）：文档选择 → `GET /documents/{id}/elements` 结构树视图——标题层级、图/表/公式编号清单（图 2-1 式）、代码块列表（样式/位置），点击定位同 D-11 机制（行点击携 `{focus:{paragraphIndex,charStart,charEnd}}` 入预览页，映射成功高亮段内区间、失败退化跳页标记）；页脚注「编辑态自动编号随协作版本上线」。**语言标注范围缩减声明（1108 复审 P2-8）：编程语言识别需词法分析、依赖编辑器切片，本切片结构树仅承载代码样式与位置，验收不含语言列。**
- **依赖**：D-08、D-10 路由骨架 | **状态**：已完成

## D-15 联调 · 端到端 + 测试文档

- **验收标准**：
  - `docs/test/FormatTest.md`（同构：LibreOffice 前置、逐接口 curl 正反例、stream/SQL 观测、建议顺序）。
  - **P-07 准确率样本集**：以 4 份课程模板样例为底本，POI 脚本注入已知问题（字号/行距/边距/题注各若干处）生成「错题集」docx + 期望问题清单 JSON；验收=校验命中数/应命中数 ≥95%（漏报口径），逐条记录进测试文档；脚本入 `docs/test/fixtures/` 幂等可重跑。
  - e2e：教师上传模板→解析规则→编辑→发布 → 学生按模板新建→文档建档 → 上传错题集 docx→校验→问题清单定位可核 → 勾选一键修复→新 EDIT 版本→复审（阻断数下降）→ 转换 DOCX→PDF 与 PDF→DOCX 各一（下载可开）+ 加密 PDF 反例（FAILED 原因）→ 反例：PDF 发起校验 ERROR（J1）、无课程模板 40901、历史模板版本不受新发布影响（UT-13）、越权他人校验 40401、非 .docx 链修复 40901。
  - 全绿口径：`mvn test`、`cd ai-service && python -m pytest`、`pnpm test`、`pnpm build`。
- **依赖**：D-01–D-14 | **状态**：未开始

---

## 建议执行顺序

```
D-01 → D-02 ∥ D-03 → D-04 →（D-05 ∥ D-06 ∥ D-07）
→ D-08（契约定型后随时并行）→ D-09 ∥ D-10 → D-11 →（D-12 ∥ D-13 ∥ D-14）→ D-15
```

## 风险与已知债务

| # | 事项 | 处理 |
| --- | --- | --- |
| R1 | POI 改样式对复杂 docx（文本框/域代码/嵌入对象）可能破坏版式 | 修复面五类（run 字体双槽、字号、行距倍数/固定、sectPr 边距、pgSz 纸型），其余规则 auto_fixable=N 只出指引；样本集回归验证破坏性 |
| R2 | 03 润色链（.txt 版本）与本切片 docx 修复链在版本树上分叉 | D-05 拒绝在非 docx 当前版本上修复（40901+指引）；两链交叠的产品化归并待协作切片 |
| R3 | P-07 95% 为「定位准确率」，无现成标准答案集 | 自造错题集（D-15）：已知注入点=ground truth；CAPTION/REFERENCE 类依赖 D-03 解析质量，一并入样本 |
| R4 | pdf2docx 转换保真有限（扫描件必败） | 失败原因结构化（ENCRYPTED/CORRUPTED/UNSUPPORTED），保留记录；扫描件明确不支持，不硬凑 |
| R5 | soffice 单实例锁与长任务占线程 | 独立 UserInstallation 临时目录 + 120s 超时 → FAILED(50401)；转换 worker 独立线程池（不抢解析/AI 池） |
| R6 | docx 无绝对页码，问题定位与文本视图页码近似 | location_json 以段落索引为准、page_no 留空；前端跳「该页标记」近似——J2/D2 既定口径 |
| R7 | 模板规则短期缓存（概要 §5.4） | 本切片每任务直读规则表（单模板规则 <50 行），`// ponytail: 缓存待模板量上来按版本号失效再加` |
| R8 | OPS 无 FORMAT_CONVERT 权限点（04_seed 口径） | 运维端不做转换入口；若需改 seed 走 DDL/seed 变更评审，不在本切片 |
| R9 | 「删除模板」需求与表 3-22 无软删列 | D-01 采「仅草稿可物理删、已发布只可停用」，与设计 §5.3.1.4 文字差异记录待需求方确认 |

## 测试与验证基线

- 后端单测预计 +45 例（模板/规则/结构树/校验/修复/转换/建档）；ai-service pytest +6 例（convert 接口正反例与依赖缺失降级）；前端 +16 例（两 api 模块 + 六页面）；`mvn test`/`pytest`/`pnpm test`/`pnpm build` 全绿后进 D-15 e2e。
- 架构红线复核：Word 解析/样式提取只在 template.infrastructure（POI）；转换引擎只在 ai-service/worker，请求线程不 spawn 外部进程；业务事件全部经 Outbox→Stream；版本不可变（修复成版 EDIT，转换不生成版本）；模板历史版本与规则行发布后只读（UT-13）；审计不含文档正文；写接口全带 `Idempotency-Key`。

## 终审整改记录（fresh reviewer,2026-11-06）

后端完成後做一次全分支只读终审，5 Critical + 8 Important 全部修复（`mvn test` 330/330、`pytest` 26/26）：

| # | 发现 | 处置 |
| --- | --- | --- |
| C1 | `DocumentElementMapper.xml` 的 `JSON_VALUE` 语法非法（RETURNING/ERROR ON NULL 位置）| ORDER BY 改 `TO_NUMBER(JSON_VALUE(col,'$.paragraphIndex'))`；列入 D-15 真库冒烟 |
| C2 | 结构树对裸「参考文献」段产出 `REFERENCE`，越 `ck_element_type` 五值字典 → 含该段文档校验恒 ERROR | 删除该分支；新增字典守卫用例 |
| C3 | Heading 4+ 样式触发 `counters[4]` 越界 → 解析崩 | `headingLevel` 仅认 1..3；补 Heading 4 用例 |
| C4 | `create()` 缺 `@Transactional`：插 check 与 outbox 分提交，崩溃留孤儿 QUEUED 且被去重永久锁死 | FormatCheck/Conversion 两处 create 加事务（对齐 03 先例） |
| C5 | ai-service `/v1/convert` 用 `async def` 内跑同步子进程最长 120s → 冻结事件循环拖死全服务 | 改同步 `def` + `file.file.read()`（FastAPI 线程池） |
| I1 | 修复成版 `updateCurrentVersion` 无 CAS → 并发双修复同基版双 EDIT | 新增 `updateCurrentVersionCas`，0 行回滚 40901；TxWriter 补 3 例单测 |
| I2 | 转换超时映射失准（后端读超时=引擎超时，超时被记「不可达」） | 读超时+15s 余量；`SocketTimeout→TIMEOUT` 静态映射 + 3 例单测 |
| I3 | `issueIds` 未过 hex 信任边界（S2 口径唯一遗漏） | 非 hex 直接计入 skipped |
| I4/I5 | issue/element 批量插入无分片、长文本撑爆列宽整批 ORA-12899 | 100 行/语句分片（沿 03 先例）+ 落库前列宽裁剪 |
| I6 | 规则编辑不设 selector 词表/修复面联动 → 校验静默失效、按模板新建可被 `HEADING_X` 打崩 | selector 白名单 + 仅四类可 auto_fixable 后端硬校验；generator 纵深 `HEADING_[1-3]` |
| I7 | worker 崩溃后 RUNNING 永久阻塞再校验；`failure_code` 超长撑爆 FAILED 回写 | `findActive` 排除 started_at 超 30min 的 RUNNING；failure_code 裁剪 60 |
| I8 | 按模板新建把 OSS 写入包进 `@Transactional`（红线：事务内网络 IO） | IO 出事务，建档+绑定经 `TemplateProvisionTxWriter` 同事务 |

Ruling 记录：expect 键集校验留给 D-09 表单层（后端保两条崩溃防线）；C1/I7a/@Transactional 类结构修复纯 Mockito 体系无法证明，**D-15 开工前先真库冒烟 `selectByVersion`/`selectActive` 两条新 SQL**。Minor 全量缓修清单见执行台账。

### Minor 处置（用户指令：本阶段能修的即修，2026-11-06）

已修 12 项（均补测试，`mvn test` 340/340、`pytest` 28/28）：并发建版本撞 uk→40901；非 docx 且无模板时提示两事实；半磅字号精确（getFontSizeAsDouble）；EXACT/AT_LEAST 固定行距双端不再误读为倍数；题注识别收紧为「图x-y」式（裸「图N…」正文不误判，样式题注不受影响）；listVersions 行内规则数消 N+1；publish 走 `published_at IS NULL` CAS 输者 40901；审计动作统一 `FORMAT_CHECK_DONE`（结论走 resultCode）；模板解析失败原因经 `failureReason` 响应回显；soffice 无产出时 stderr 尾部记日志；`/v1/convert` 输入 25MB 上限；UT-13 前半显式用例。

缓修 3 项（后期任务触及）：CODE 存在性规则的误报口径→D-15 样本集验证；转换记录列表分页→D-13 前端面；listElements 首读线程内解析→D-15 压测观察项。

## 1106 符合性审查整改记录（Codex 审查报告，2026-11-06 处置）

报告 2 P1 + 4 P2 + 3 P3，逐条技术验证成立；除 P3-3（真实链路，D-15 范围）外全部整改，回归 `mvn test` 353/353、`pytest` 28/28：

| # | 发现 | 处置 |
| --- | --- | --- |
| P1-1 | 继承 Normal/basedOn/docDefaults 的样式不参与比对（静默漏报） | 提取器按 Word 渲染语义重写：run→样式 basedOn 链（≤5 防环）→docDefaults；行距无设置=单倍 1.0 可比对（`paragraphWithoutStyleFallsBackToNormal`/`basedOnChainResolves`/`unsetLineSpacingDefaultsToSingle` RED→GREEN）；空段不参与字体/字号/行距比对 |
| P1-2 | Caption 样式非「图/表 x-y」文本 `m.group` 越界 → 整次校验 ERROR | 先判 `find()`；非章序样式题注按首字归类补自动编号（`looseStyledCaptionDoesNotCrash` RED→GREEN） |
| P2-1 | 规则无读取接口 | 新增 `GET /templates/{id}/versions/{v}/rules`（MANAGE，编辑回显）与 `GET /templates/{id}/current-rules`（READ，学生只读要求页；未发布 40901、门外 40401）+4 例 |
| P2-2 | A4 纸型未解析/校验/生成 | 规则 MARGIN/PAGE 契约增 `width/height`（mm）；解析读 pgSz；比对 ±3mm；生成器显式写 pgSz（规则缺省 A4 兜底）——`parsesPageSize`/`letterPageViolatesA4Expectation`/生成往返断言 RED→GREEN |
| P2-3 | 转换契约/超时码/依赖缺口 | (a) 内部契约定型 multipart 并回填 D-06 文字；(b) 引擎超时落约定码 `50401`（`engineTimeoutRecordsAgreedCode50401` RED→GREEN）；(c) pdf2docx 入 `pyproject` 可选组 `convert`（README：`uv sync --extra convert`） |
| P2-4 | CAPTION perChapter=false 规则空操作 | 解析保存可执行 `pattern`；匹配器优先按 pattern 校验、无 pattern 回退章序式（`captionPatternCheckRunsForNonChapterNumbering` RED→GREEN） |
| P3-1 | 去重「先查后插」非原子 | create 事务内 `SELECT ... FOR UPDATE` 文档行锁（happy 路径 verify 锁调用）；不改 DDL 口径 |
| P3-2 | element_key 与验收文字不一致 | 采「回填文档」方案：稳定键=`{TYPE}-{正文元素索引}`（见 D-03） |
| P3-3 | 真实链路覆盖不足（真库 IT/真实转换/P-07） | D-15 范围：开工前置=真库冒烟 `selectByVersion`/`selectActive`/`selectForUpdate`；真实 LibreOffice（`EDUDOC_SOFFICE_BIN`）+ `uv sync --extra convert` + `EDUDOC_AI_INTERNAL_TOKEN` 到位后跑双向转换与错题集 |

审查 4.2(3)「Controller 无接口级测试」沿用 02/03 既有口径（薄委托、真机覆盖），随 D-15 闭合。

## 1106 整改复审记录（第二轮复审报告，2026-11-06 处置）

复审确认 P1-2/P2-1/P3-1/P3-2 关闭，其余部分关闭项本轮补齐；`mvn test` 361/361、`pytest` 28/28：

| # | 发现 | 处置 |
| --- | --- | --- |
| P1-1 | 模板侧 basedOn 字体继承未解析 + East Asia 中文槽被忽略 | 字体事实模型双槽化（`ParagraphFact.familyEastAsia`）：提取/规则（`expect.family`+`expect.eastAsia`）/比对三处独立；模板样式属性沿 basedOn 链解析（与提取器共用 `styleChain`）——4 例 RED→GREEN（含复现同型：ascii 相同中文槽不符 → BLOCKER） |
| P1-2 | 仅段前/段后 spacing 截断样式行距继承（误报回归） | `spacingLine` 三态（值/EXACT 终止/NOT_SET 继续上溯）；「样式 1.5 + 段落仅 before」提取=1.5 ✅ |
| P2-1 | 文档缺 pgSz 时期望 A4 静默通过 | 未声明=不符：actual `width=未声明` 产 BLOCKER ✅ |
| P2-2 | 生成的题注 pattern 强制尾随空格 | pattern 改前缀语义（与 CAPTION_START 一致），「图1-2系统架构图」不再误报 ✅ |
| P2-3 | 模板版本仍可无 docx 创建 | **拍板：file 必填**（40001）——与 D-02「上传并解析」契约与可追溯性一致；规则编辑仍在带文件的 PENDING/INVALID 草稿版本上进行 ✅ |
| P2-4 | 非法正则保存后运行时静默跳过 | `replaceRules` 对 CAPTION `expect.pattern` 编译校验，坏正则 40001 拒绝入库 ✅ |
| 一致性 | ai-service README 缺 `/v1/convert` 与 `EDUDOC_SOFFICE_BIN` | 接口清单与环境变量表已补（含 multipart 契约与错误码映射） |

维持 D-15 前置（本轮无法在开发机闭合）：真实 LibreOffice 双向转换、pdf2docx（`uv sync --extra convert`）、真库 IT 与 `selectForUpdate`/新 SQL 冒烟、P-07 错题集、`EDUDOC_AI_INTERNAL_TOKEN`/`EDUDOC_DB_*` 环境注入。

## 1106 第三轮复审记录（2026-11-06 处置）

四项代码面全部闭合（+5 例，`mvn test` 366/366；ai-service 本轮无改动）：

| # | 发现 | 处置 |
| --- | --- | --- |
| P1-1 | 生成/修复写回链把 ASCII 字体写进 East Asia 槽 | 双槽贯通：`FixAction.eastAsia` 新字段；`DocxStyleEditor.applyFont` ascii/中文槽各写各的（缺侧不越界）；`FormatFixServiceImpl` 读 `fix.eastAsia`；`TemplateOutlineGenerator` 按规则双槽分别落 styles.xml——复审点名的两处缺失断言已补（生成后回读 `generatedOutlineKeepsSeparateFontSlots`、修复后回读 `fontFixWritesSlotsSeparately`） |
| P1-2 | 子样式 EXACT 固定行距穿透父样式倍数产出失真规则 | 解析链行距语义改「继承终止」：本层显式 EXACT/AT_LEAST → 终止且**不产** LINE_SPACING 规则（`childExactLineStopsInheritanceFromParentMultiplier` RED→GREEN） |
| P2-1 | 文档缺 pgMar 时页边距静默通过 | 与缺纸型同口径：期望存在而实际未声明 → BLOCKER（actual=`key=未声明`）✅ |
| P2-2 | 期望中文槽而段落 East Asia 未声明时静默通过 | 「中文槽未声明」即不符（Word 中文实际回退主题字体，无法证明符合）✅ |
| P3-1 | FONT 契约文档未同步 eastAsia | D-02 键集已回填（含 MARGIN width/height 纸型键）✅ |
| P3-2 | 真实链路/P-07/FormatTest.md | 维持 D-15 前置清单不变（本环境无 soffice/pdf2docx/DB 凭据，非代码缺陷） |

教训入台账：缺失值语义须一次性覆盖同类面（pgSz 修了、pgMar/EastAsia 槽漏了）；双值模型改造必须同轮贯通「提取-规则-比对-生成-修复」五处并各有回读断言。

## 1106 第四轮复审记录（2026-11-07 处置）

四项点名问题全部闭合，另自查补齐同类未点名面与用户要求的冗余/过度复杂审计；`mvn test` 377/377、`pytest` 31/31：

| # | 发现 | 处置 |
| --- | --- | --- |
| P1-1 | 纸型问题标记可修复但修复器不写 pgSz（假闭环） | `FixAction.Page` 携带 top/bottom/left/right+width/height，写回同触 pgMar+pgSz；「Letter→A4 修复后回读」用例钉死；文档修复面改五类、R1 同步 |
| P1-2 | 固定行距无规则表达且比对 null 跳过 | `LineSpacing(kind,value)` 建模贯通四处：解析（EXACT 终止继承**并产出固定规则**）、比对（固定vs倍数=不符；期望 lineRule+pt 可比，±0.6pt）、生成（保真 EXACT 磅值）、修复（`FixAction.Line`）；旧「无规则」测试语义按新契约升级 |
| P2-1 | East Asia-only 被复制填充 ASCII 期望 | 解析两槽各自独立成键；生成器缺侧用文档化安全默认（Times/黑体），不伪装模板期望 |
| P2-2 | ASCII 槽/字号缺失静默通过 | 缺失值语义统一「未声明=不符」：西文槽未声明、字号未声明（继承链+docDefaults 解析后仍缺才计）——第三轮未点名面一并闭合 |
| P2-3 | 加密 DOCX 归为 CORRUPTED | converter 双防线：OOXML 加密容器魔数（D0CF11E0）调用 soffice 前识别 + stderr encrypt/password 兜底；3 pytest 用例（含反向 CORRUPTED） |
| P2-4 | 正则/键集校验缺 SIZE/LINE/MARGIN 同类面 | `validateExpectKeys` 保存期强制各类型最小可执行键集（FONT 双槽之一/SIZE pt/LINE_SPACING multiplier|lineRule/MARGIN 任一边/CAPTION pattern|perChapter/HEADER_FOOTER present）；4 用例 |
| P3-1 | D-06 契约 `{reason}` 与 `{code,message}` 混写 | 统一 `{code,message}`（`reason` 归任务查询字段）——见文末 D-06 行 |

**冗余/过度复杂审计（用户指令）**：
1. 收敛：`FixAction` 六可空参数 record → sealed 变体（Font/Size/Line/Page），根治 data clump 漏分支（Standards 轴同判）；`StyleSnapshot` 行距三态（NaN 哨兵+双重 null）→ `LineSpacing` 显式建模；行距解析双份实现（parser/extractor）→ 单源 `spacingLine`。
2. 清理死代码：`TemplateAccess.scope()`、worker 测试死桩、parser 无用 import（CTFonts/CTSpacing）。
3. 保留并说明：`resolveLine` 与 `emitStyleRules` 链遍历结构相似，但一个产模型值一个产规则草稿，合并成本高于重复；`dxa` 换算两处一行重复同理。

## 1107 第五轮复审记录（2026-11-07 处置）

口径：本轮以「功能完全实现 + 常规输入不报错」为验收线；真库 IT/真实引擎/P-07 属 D-15。5 P2 + 2 P3 全部闭合（+8 java 例、+2~3 pytest 例），`mvn test` 385/385、`pytest` 33/33：

| # | 发现 | 处置 |
| --- | --- | --- |
| P2-1 | 规则 JSON 四处各自解释，非法/空操作期望入库后静默失效 | 新增 **`RuleConfig` 领域对象**（单点解释+完整不变量：数值范围、lineRule 枚举域且必带 pt、present/perChapter 必须 JSON 布尔、CAPTION 至少一个可执行条件、正则合法性、FONT 双槽至少其一）；保存闸改为调用它（`validateExpectKeys`/`patternOf`/CAPTION 正则块三个旧碎片删除）；**匹配器改为失败关闭**——无法解释的存量脏配置产出「规则配置非法」问题而非静默跳过（`legacyInvalidConfigFailsClosed` 见证）；生成器与修复服务同源消费（`asFixView`） |
| P2-2 | auto_fixable=Y 可无有效 fix | 保存闸新不变量 `Y ⇒ fixExecutable`；4 类各拒绝「可修复但缺 fix」；前端不会再出现可勾选但实际 skipped |
| P2-3 | pgSz-only 模板丢纸型规则 | `emitPageRules` 改 pgMar/pgSz 独立读取、逐键判断（pgSz-only 产 width/height 且**不伪造边距键**；部分 pgMar 不拖累其余键） |
| P2-4 | 解析器不回退 docDefaults | `emitStyleRules` 链尾回退 docDefaults（字体双槽/字号/行距），与提取器同一解析顺序——docDefaults-only 模板的 Arial/12pt 现产出规则，D-07 生成不再退回安全默认 |
| P2-5 | 多 run 段落只取首值，混合格式漏报 | 事实模型下沉 run 级：`ParagraphFact` 段落层=继承值、`RunFact(charStart,charEnd,text,显式三槽)` 明细；比对逐 run 取生效值（显式?:继承）去重收集违规，空 run 跳过；兼容读取 `family()/pt()` 行为不变（385 例全量回归见证）；「首 run 符合、后 run 错误」检出用例钉死 |
| P3-1 | location_json 缺字符区间 | 元素与问题位置统一 `{paragraphIndex, charStart, charEnd}`（区间=段落文本内，文档化等价口径；混 run 问题的区间取首个违规 run）；D-11 前端按此精确标亮 |
| P3-2 | UNSUPPORTED 未实现+文档漂移 | converter 容器先判：docx 侧非 zip 非 OLE→UNSUPPORTED、pdf 侧非 %PDF→UNSUPPORTED（415，先输入后依赖）；D-06 测试计数与 pdf2docx 部署口径（`uv sync --extra convert`）、「修复面四类→五类」注释同步 |

Standards 轴回应：本轮以 `RuleConfig` 单点收口「Primitive Obsession / Divergent Change」——同一规则类型的语义变更现在只需要改 RuleConfig + 各消费方的类型化取值，保存/比对/生成/修复不再分叉。

## 1107 阶段全量审查整改记录（2026-11-07 处置）

2 P1 + 9 P2 全部按「功能完全实现 + 常规不报错」口径处置；回归 `mvn test` 391/391、`pytest` 33/33、`pnpm test` 162/162、`pnpm build` 通过：

| # | 发现 | 处置 |
| --- | --- | --- |
| P1-1 | 模板状态接口前端 POST/后端 PATCH 漂移（真链路 405，测试 false green） | http.ts 补 `patch`；changeTemplateStatus 改 PATCH；测试断言 patch 且显式断言 post 未被误调；FormatTest.md 契约清单收录方法列 |
| P1-2 | 根级 `tolerance/aliases` 消费断裂（自定义容差失效、字体别名误报） | RuleConfig 持根级 tolerance 节点，`tolerancePt()`/`aliases()` 从正确位置读取；matchFont 命中「主名或别名」即通过；前端 validateRules 同步镜像。回归例：`fontAliasSatisfiesExpectation`、`rootTolerancePtIsConsumed`、parser 别名写出断言 |
| P2-1 | 保存闸只查键存在（空串 pattern/错类型边距/CODE 空 expect 可入库静默失效） | RuleConfig 全键值域强校验（含 CODE.style、REFERENCE.standard 必备；present/perChapter 必须 JSON 布尔；空串 pattern 归一为缺失）→ 非法配置保存 40001 + 匹配器对存量脏配置 fail-closed |
| P2-2 | INVALID 原因一次性回显、重开编辑页丢失 | **最小 DDL 迁移**：`edudoc_template_version.parse_error VARCHAR2(1000)`（01_schema.sql + 数据库设计说明书表 3-23 已同步）；创建时随版本落库，listVersions 回填；真库需执行 `ALTER TABLE edudoc_template_version ADD parse_error VARCHAR2(1000)`（已列 D-15 前置） |
| P2-3 | D-09 缺分组/发布确认/只读回显/学生端示意 | 规则按 rule_type 分组编辑（groupRules）；屏 11 发布确认弹窗（版本/适用范围/变更说明/影响提示，确认才调接口）；已发布版本只读回显（口语化要求列）；右侧「学生端效果预览（静态示意）」卡 |
| P2-4 | 工作台快捷入口未接线 | 学生 Workbench 四快捷卡（上传文档/按模板新建/查看作业/教学消息，设计 05 口径，无教师运维能力项）；pageOf 接线 |
| P2-5 | D-11 定位无高亮无退化 | 题注/引用类以违规文本块内检索绝对偏移走 `highlightRange` 精确高亮（findAbsRange）；字体/字号类段落密度近似滚动 + 「近似定位」明示；空块/页面级各有提示 |
| P2-6 | D-12 影响面不全、40901 无重查 | FixModal 三段清单（本次将修复/未勾选可修复/不可修复+指引）；40901 走冲突分支：关弹窗+提示+重查当前校验状态 |
| P2-7 | D-13 入口/历史/下载/列不完整 + 契约两口径 | **下载通道裁定：presigned GET 直链唯一口径**（fetch blob 废弃，文档已同步）；行级「转换」入口；独立转换记录 Drawer（文档选择+关键字检索含失败原因+源/目标/起止时间全列）；后端 listByDocument SUCCEEDED 行携带签名 URL（消除「链接已过期」）；Modal 聚焦本次任务 |
| P2-8 | D-14 无定位/语言/位置 | 位置列（第 N 段，来自 location_json）+ 行点击直达预览；语言标注边界注明随编辑器切片（结构树当前识别代码样式与位置，不做词法猜语言） |
| P2-9 | D-15 证据包 | 维持「未开始」：待 EDUDOC_DB_* 注入后执行真库三条 SQL+ALTER、三服务 e2e、错题集与 P-07 统计、curl/观测清单入 FormatTest.md |

Standards 轴回应：false-green 教训采纳（API 测试断言绑定方法族并防漂移）；规则契约「前后端各写一份」的 Divergent Change 风险本轮以「前端 validateRules 镜像 + 注释指向 RuleConfig 单源」缓解，彻底共享 schema 夹具待 D-09 浏览器联调时以后端 OpenAPI/校验响应为准（错误信息已透传，后端仍是裁判）。

## 1108 阶段复审处置记录（2026-11-08）

复审确认 2 P1 与 6 P2 关闭；4 个 P2 残余 + 文档一致性本轮处置完毕，回归 `mvn test` 392/392、`pytest` 33/33、`pnpm test` 168/168、`pnpm build` 通过：

| 残余 | 处置 |
| --- | --- |
| P2-1 CODE 弱存在性 | `keepIndent` 入不变量（必须 JSON 布尔）并被 `matchCode` 消费：keepIndent=true 时逐代码段检查首个有内容 run 的行首缩进，丢失者逐段报告（无 run 原文证据的段落不报，宁缺勿误）；`style` 语义=提取端 CODE_BLOCK 样式归类来源，文档注释说明跨文档样式 id 不可二次比对。回归例 `codeKeepIndentViolationsReported` |
| P2-5 D-11 定位口径 | 新建共享机制 `view/locate.ts`：`paragraphRange` 按「段均 120 字」估计段起点 + **消费 location_ref 的 charStart/charEnd 得段内高亮区间**；`locateByParagraph` 成功→滚动+highlightRange；失败→`scrollToPageMarker` 跳估计页码的页分隔锚点（DocumentView 页标记已加 `page-anchor-{n}` id）；再失败→明示无法定位。面板与 D-14 共用 |
| P2-7 D-13 目标选择/契约文本 | Modal 增目标格式控件（自动匹配默认，可显式 PDF/Word；手选时方向不符者跳过并提示，按钮可用性联动）；**任务正文 D-13 的 fetch blob 口径删除，统一 presigned 直链**；顶部「零 DDL」陈述同步为「原设计零 DDL + parse_error 一列经批准迁移」 |
| P2-8 D-14 语言与定位 | 行点击改携 `{state:{focus:{paragraphIndex,charStart,charEnd}}}`，DocumentView 加载后按 locate 同机制高亮/退化跳页（与 D-11 真正同机制，不再是裸跳转）；**语言列按复审建议在需求侧声明范围缩减**（D-14 正文已注明：词法级语言识别随编辑器切片） |
| Standards 判断项 | 「单一 schema 来源」风险接受并记录：前端 validateRules 为后端 RuleConfig 的镜像（注释双向锚定），D-15 联调时以接口错误信息验证镜像一致性；文档"已完成"声明与 D-15 未启动的矛盾本轮以头部基线口径消除（代码面完成 ≠ 阶段交付） |

至此阶段复审清单中「放行前必须完成」仅剩第 5 项 D-15（真实库迁移/真实转换/错题集/P-07/e2e，需 DB 凭据与新终端环境）。

## 1108 新一轮检查处置记录（2026-11-08）

D-15 实跑推进 + 检查报告 P2/P3 处置。**当前回归基线：`mvn test` 400/400（真库 IT 注入凭据时全执行）、`pytest` 33/33、`pnpm test` 169/169、`pnpm build` 通过**；上文 297 行的 392/392 为 1108 阶段复审时点的历史记录，不改写。

| 项 | 处置 |
| --- | --- |
| D-15 真库迁移 | `ALTER TABLE edudoc_template_version ADD parse_error VARCHAR2(1000)` 已在 oracle-dev 真库执行（jshell+ojdbc，幂等先查 ALL_TAB_COLUMNS），与 `01_schema.sql`/设计文档一致 |
| D-15 真库 IT | 三条真库执行全部通过：Rbac 1/1、Embedding 1/1、新增 `TemplateFormatSqlIntegrationTest` 4/4（parse_error 往返 / selectActive 30min 窗口+markRunning CAS / selectForUpdate+updateCurrentVersionCas / JSON_VALUE 段落序）。首跑暴露并修复两处**既有 IT 从未真跑过的潜伏缺陷**：①`setupConn` 默认 autoCommit 下显式 commit 抛 ORA-17273；②seed 违反真库约束（oss_object.content_hash NOT NULL、`ck_doc_type` 枚举 REPORT 而非 PDF/DOCX、version_type 'ORIGINAL'/'CURRENT' 非法→UPLOAD/'A'）。另修 MyBatis 一级缓存导致的窗口断言假象（外部提交后需 `clearCache`） |
| D-15 P-07 错题集 | `P07ErrorSetTest`：四模板底本 POI 注入 20 已知问题（FONT6/SIZE8/LINE2/MARGIN4）→ `docs/test/fixtures/mutated/*.mutated.docx` + `groundtruth.json`；与生产同链比对 **20/20，recall=100% ≥ 95%**。CAPTION 无真语料出题面（转换件/要求文档题注为纯文本，破 pattern 即脱 selector，不硬造，已记 skipped），题注识别由单元例覆盖。首跑暴露**第三处契约打架**：JavaEE 转换件 pgMar top=0 使解析产出 MARGIN.top=0 被 RuleConfig 保存闸拒绝——解析端 `putIfPresent` 改为与保存闸同值域（边距 (0,100]、纸型 (50,1000]），回归例 `marginKeysOutsideSaveGateDomainAreNotEmitted` |
| D-15 真实转换（引擎级） | ai-service `/v1/convert` 本机 HTTP 实测：DOCX→PDF 200/7.9s（%PDF 魔数）、PDF→DOCX 200/0.6s（PK 魔数）、OLE 魔数件→ENCRYPTED 422、随机字节→UNSUPPORTED 415。**注意 EDUDOC_SOFFICE_BIN 含空格路径**，`setx` 值须整体引用 |
| P2-1 文档数字 | 本段与文件头基线已更新为实际数；历史记录保留 |
| P2-2 D-15 剩余 | **已闭合（2026-11-09）**：HTTP 三服务全链 e2e 逐步 PASS 至 ALL-E2E-PASS（教师建模板→发布→J4 新建初验 PASSED 15/阻断0→学生 STS 上传错题集→初验 FAILED 52 含 1 阻断→一键修复 4 条→自动重查 PASSED 48 阻断清零→DOCX↔PDF 双向转换 presigned 魔数验证→J1 反例 40901 创建闸），观测表回填 `docs/test/FormatTest.md`「e2e 执行记录」节；仅剩浏览器手工全链路随用户验收 |
| P3-1 页 1 退化 | `scrollToPageMarker`：pageNo=1 且 `page-anchor-1` 不存在时回退滚动 `block-1`；回归例「第 1 页无页分隔锚 → 退化滚至首个块」 |
| P3-2 focus 依赖 | `DocumentView` focus effect 依赖扩为 `[blocks.length, focus, docId]`，同实例切文档/块数相同场景重新定位 |
| P3-3 证据回写 | 错题集 20/20 明细、生成入口（`mvn test -Dtest=P07ErrorSetTest`）、统计口径与 locate 经验常数校准依据（错题集语料段均字符实测 ~120 字/段）写入 `docs/test/FormatTest.md` |
