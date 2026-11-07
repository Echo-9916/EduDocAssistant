# 1106 模板格式切片（04）后端与 ai-service 符合性审查报告

> 审查日期：2026-11-06  
> 审查人：Codex（根 Agent；Spec / Standards 双轴由根 Agent 完成）  
> 审查分支：`feat/fullstack-template-format`  
> 审查范围：`docs/tasks/04-template-format.md` D-01~D-07 后端实现，以及 ai-service `/v1/convert`  
> 用户口径：本轮只检查当前已经执行的后端与 ai-service 部分，不评价尚未开始的前端 D-08~D-14  
> 总体结论：**不通过完整验收（NOT FULLY VERIFIED）**。主体骨架和自动化测试已落地，但存在 2 个 P1 核心缺陷、4 个 P2 规格/交付缺口，同时真实数据库、真实转换引擎和 P-07 准确率尚未验证。

---

## 一、结论摘要

当前实现已基本覆盖模板主档、模板版本与规则、结构树、格式校验、一键修复、按模板新建、格式转换和 ai-service 转换端点。自动化结果与任务文档自报基线一致：

- 后端 `mvn test`：**340/340 通过**
- ai-service `uv run python -m pytest -q`：**28/28 通过**
- `git diff --check`：通过
- 两个真实数据库集成测试仍被跳过
- 真实 LibreOffice、pdf2docx、服务间令牌、Oracle/Redis/OSS 链路未完成验证

但不能据此判定“当前能够很好地完成要求”。本轮通过代码检查和 JShell 探针确认了以下核心问题：

1. **格式校验会静默漏报继承样式错误**，直接影响字体、字号、行距等核心规则和 P-07 ≥95% 目标。
2. **Caption 样式遇到非“图/表 x-y”文本会让整次校验落入 ERROR**。
3. **模板版本规则没有读取接口，后续教师规则编辑页和学生只读要求页无法取得规则内容**。
4. **A4 纸型没有解析、校验或生成**。
5. **模板版本允许不带 docx 文件创建，甚至可以只靠手工规则进入 VALID 并发布**，与“上传模板 docx”验收口径不一致。
6. **转换功能可以编译和降级，但当前环境不能实际完成 DOCX↔PDF**；内部接口契约和超时码也与任务文档不一致。

因此当前状态更准确的描述是：**后端与 ai-service 主体实现已成形，自动化回归通过，但尚不具备完整验收和交付条件。**

---

## 二、验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 后端全量测试 | `EduDocAssistant/backend` 下 `mvn test` | **340 passed / 0 failure / 0 error** |
| 后端真库 IT | Maven 日志 | 两个 IT 为 `Tests run: 0`，因 `EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD` 缺失而跳过 |
| ai-service 测试 | `EduDocAssistant/ai-service` 下 `uv run python -m pytest -q` | **28 passed**，1 个 Starlette/httpx 弃用告警 |
| 空白与格式检查 | `git diff --check` | 通过；仅提示 LF→CRLF |
| 本机 soffice | `Get-Command soffice` | 未找到 |
| pdf2docx 依赖 | `uv run python -c "importlib.util.find_spec('pdf2docx')"` | `False` |
| 服务间令牌 | 环境变量检查 | `EDUDOC_AI_INTERNAL_TOKEN` 缺失 |
| 真实转换探针 | 直接调用 `docx_to_pdf_bytes` / `pdf_to_docx_bytes` | 两端均返回 `ENGINE_UNAVAILABLE` |
| P-07 准确率 | `docs/test/FormatTest.md`、错题集 | 尚未生成，未执行 |
| D-15 e2e | 任务文档 D-15 | 未开始 |

工作区状态说明：本次审查期间，template 域后端实现、Mapper、测试和 ai-service 转换代码大量仍为未提交或未跟踪文件。本轮只读审查，没有修改业务代码。

---

## 三、Spec 轴（规格符合性）

| 项目 | 结论 | 主要依据 |
|---|---|---|
| D-01 模板主档管理 | **基本符合** | 列表可见性、草稿创建、状态变更、草稿删除、审计和权限主体已实现；同名冲突与课程范围校验存在；未发现本轮级别的阻断问题 |
| D-02 模板版本解析与发布 | **部分符合** | OSS 写入、POI 解析、规则落库、发布 CAS、历史版本只读已实现；但模板版本可无文件创建，缺少规则读取接口，A4 未处理 |
| D-03 文档结构树解析 | **部分符合** | 标题、图表、代码、公式、章内编号、幂等覆盖均已实现；但 Caption 样式非匹配文本会崩溃，`element_key` 也未严格使用 `{type}-{序}` |
| D-04 格式校验任务 | **不符合核心精度要求** | 任务、去重、Outbox、Worker、问题清单和汇总已实现；但样式继承漏报，Caption 非章内规则为空操作，P-07 未验证 |
| D-05 一键修复成版 | **有条件符合** | 四类修复、版本 CAS、自动复审、事务回写和审计结构齐全；其最终效果受 D-04 漏报与结构树异常影响 |
| D-06 格式转换任务 | **未验证/部分偏离** | 任务、Worker、OSS 结果对象和下载 URL 已实现；但当前环境不能真实转换，内部契约与超时码存在偏差 |
| D-07 按模板新建文档 | **部分符合** | 大纲、标题样式、页边距、文档四写绑定已实现；但没有写 A4 纸型，生成结果无法保证 A4 |
| ai-service `/v1/convert` | **部分符合** | 同步端点、token 守卫、soffice 独立 profile、pdf2docx 懒加载和结构化错误已实现；pdf2docx 未安装，接口改为 multipart，与任务契约不一致 |

### 3.1 D-01 模板主档

已确认存在：

- `GET /api/v1/templates` 的课程、状态、分页筛选
- 学生只看课程已发布模板
- 教师可见本人维护或任教课程模板
- OPS 全量可见
- 同名模板冲突映射为 40901
- 状态变更、仅草稿可删、审计记录

本项未作为本轮失败点。

### 3.2 D-02 模板版本

已确认存在：

- 模板版本号递增
- 上传文件写入 OSS，对象角色为 `ORIGINAL`
- POI 规则解析
- 解析失败落 `INVALID`
- 规则整组替换
- 发布仅接受 `VALID`
- 发布时前移 `current_version_id`
- 历史已发布版本拒绝再次修改规则
- 发布 CAS 与并发冲突处理

但存在两项规格缺口：

1. Controller 允许 `file` 为空，Service 会创建没有 `source_object_id` 的版本；随后可用 `PUT /rules` 把它改为 `VALID` 并发布。
2. 没有读取规则集接口，前端无法反显模板规则。

### 3.3 D-03 文档结构树

已确认存在：

- HEADING、FIGURE、TABLE、CODE、EQUATION 五类元素
- 标题层级与章号推导
- 图表题注识别
- 章内序号
- `location_json.paragraphIndex`
- `(document_version_id, element_key)` 重解析覆盖

主要缺口见第四章 P1-2、P3-2。

### 3.4 D-04 格式校验

已确认存在：

- `POST /documents/{id}/format-checks`
- 非 docx 任务落 ERROR 的设计
- 绑定模板版本优先
- Outbox 和 Stream Worker
- 结构树解析、样式提取、规则匹配、问题表写入
- PASSED/FAILED/WARNING 统计
- 查询校验与问题清单

但“问题定位准确率 ≥95%”目前没有任何实测证据，且已确认存在可直接影响漏报和 ERROR 的代码缺陷。

### 3.5 D-05 一键修复

已确认存在：

- 仅选取自动可修复且 OPEN 的问题
- 只修复字体、字号、行距、页边距四类
- 不可修复项进入 skipped
- 新 docx 上传为 `ORIGINAL`
- 新 EDIT 版本
- current version CAS
- 自动发起复审任务
- 审计 `FORMAT_FIX_APPLIED`

本项实现结构较完整，但在 D-04 漏报闭合前，不能证明“修复后准确复审”。

### 3.6 D-06 格式转换

已确认存在：

- 单文档单任务
- QUEUED + Outbox
- Redis Stream Worker
- 调 ai-service
- 结果对象 `CONVERTED`
- 不创建文档版本、不修改 current version
- 发起人隔离
- 900 秒下载 URL
- 结构化失败码

当前不能验收的原因见第四章 P2-3。

### 3.7 D-07 按模板新建

已确认存在：

- 学生课程范围校验
- 仅当前已发布模板可用
- 按规则生成标题样式和页边距
- 上传 OSS
- 复用文档建档事务
- 绑定模板版本
- 解析事件

当前缺口见第四章 P2-2。

---

## 四、Standards 轴（工程标准与运行风险）

### 4.1 做得较好的部分

- Service/Repository/Mapper 分层总体清晰。
- 模板版本校验与规则持久化通过独立事务组件完成，网络和 POI 解析放在事务外。
- 发布版本使用 `published_at IS NULL` CAS。
- 修复成版使用 `current_version_id` CAS，避免同基版双写。
- 格式问题与元素行使用 100 行分片，避免 Oracle 绑定数和大语句问题。
- 文档、模板、校验、转换主流程均通过 Outbox → Stream，不在请求线程执行长任务。
- 临时文件在 finally 中清理。
- ai-service 转换端点使用同步 `def`，避免阻塞 FastAPI 事件循环。

### 4.2 工程/交付缺口

1. 真实数据库 SQL、Oracle JSON 语法和两处集成测试尚未执行。
2. 真实 LibreOffice 与 pdf2docx 未安装，无法验证转换效果、超时和临时目录行为。
3. 新增原型的 Controller 层没有对应的 MockMvc 或集成测试，当前主要验证的是 Service 和 Worker。
4. template 域主体仍处于未提交工作区，当前状态不适合作为稳定交付基线。
5. 活跃校验去重仍是“先查后插”，且数据库没有同版本/模板/用户的部分唯一约束；两个并发请求使用不同 Idempotency-Key 时仍可能生成两个 QUEUED 任务。

---

## 五、问题清单

### P1-1【核心漏报】继承 Normal/basedOn 的样式不会参与比较

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleExtractor.java:52`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:62`
- 现象：正文段落省略 `pStyle`、依赖 Normal 样式时，提取器返回 `family=null`、`pt=null`、`lineMultiplier=null`；匹配器只在实际值非空时比较，因此返回零问题。
- 触发条件：常见的 Word 正文继承 Normal 样式，且错误通过样式而不是 run 直接属性引入。
- 影响：字体、字号、行距和部分页边距错误可能被静默判为通过，直接威胁 P-07。
- 根因：已确认。代码注释声称支持 basedOn 一层继承，但 `resolveStyle` 只返回当前 style，未解析默认样式、basedOn 或 docDefaults。
- 复现证据：JShell 构造 Normal 为 `SimSun`、正文段无 pStyle、规则期望 `SimHei`，最终 `issues=0`。
- 修复建议：解析 default paragraph style、basedOn 链和 docDefaults；实际值缺失时不能直接视为符合，应产生“无法解析实际样式”问题或明确失败。
- 状态：`unresolved`，P1。

### P1-2【校验崩溃】Caption 样式遇到非匹配文本会抛异常

- 位置：`backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStructureParser.java:90`
- 现象：`isCaptionParagraph` 只要样式名包含 `caption/题注` 就返回 true；调用处执行 `m.find()` 后直接 `m.group(1)`，没有检查是否匹配。
- 触发条件：Caption 样式段落为“图3 无章号”“Figure 1-1”“表 A”等非“图/表 x-y”格式。
- 影响：`DocxStructureParser.parse` 抛 `TemplateParseException: No match found`，FormatCheckWorker 将整次校验写为 ERROR，无法给出问题清单。
- 复现证据：JShell 构造 Caption 样式段落“图3 无章号的题注样式段”，解析返回 `No match found`。
- 修复建议：先判断 `m.find()`；非匹配时按普通正文或明确问题处理，不能继续取分组。
- 状态：`unresolved`，P1。

### P2-1【接口缺失】没有模板规则读取端点

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/api/TemplateVersionController.java:34`
  - `backend/src/main/java/com/cidas/edudoc/template/application/TemplateVersionService.java:9`
- 现状：只有 `PUT /templates/{templateId}/versions/{versionId}/rules`，没有 GET；版本列表只返回 `ruleCount`，没有 `rule_config_json`。
- 影响：D-09 教师规则编辑页无法回填规则，D-10 学生只读要求页无法展示字体、字号、行距、页边距和图表规则。
- 修复建议：新增只读规则列表接口，区分模板管理者与课程内学生的可见范围，并补越权测试。
- 状态：`unresolved`，P2。

### P2-2【规格/生成】A4 纸型没有处理

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:139`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateOutlineGenerator.java:62`
- 事实：解析和校验只处理页边距；生成器只写 `pgMar`，不写 `pgSz`。
- 复现证据：JShell 调用 `TemplateOutlineGenerator.generate` 后读取文档，`hasSectPr=true`、`hasPgSz=false`。
- 影响：按模板新建不能保证 A4；Letter 或其他纸型模板也不会被校验发现。
- 修复建议：在规则契约中补 A4 期望值；解析/校验 `w:pgSz`；生成时显式写入 A4 宽高。
- 状态：`unresolved`，P2。

### P2-3【接口契约/运行】转换当前不可实际使用

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/ConversionClient.java:61`
  - `ai-service/app/main.py:219`
  - `ai-service/app/converter.py:72`
- 事实：
  - 规格约定内部请求为 `{sourceSignedUrl|objectKey, targetFormat}`，当前改为 multipart 文件上传。
  - 规格写超时落 `FAILED 50401`，当前 Worker 保存字符串 `TIMEOUT`。
  - `pdf2docx` 未加入当前依赖环境和 `pyproject.toml` 主依赖。
  - 本机没有 soffice，也没有服务间令牌。
- 复现证据：直接调用两个转换函数，均返回 `ENGINE_UNAVAILABLE`。
- 影响：D-06 当前只能展示降级错误，不能完成 DOCX↔PDF 交付，也不能证明加密 PDF、超时和失败码契约。
- 修复建议：确认最终内部契约；补齐部署依赖；把超时映射为约定码；完成真实双向转换和反例测试。
- 状态：`unresolved`，P2。

### P2-4【规则语义】CAPTION 非章内编号规则没有实际校验

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:109`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:125`
- 事实：解析器保存 `perChapter=false` 的规则，但匹配器遇到 false 直接 return。
- 影响：使用“图1、表1”等非章内编号模板时，CAPTION 规则不会产生任何问题。
- 修复建议：规则配置保存 actual pattern；匹配器对所有 pattern 执行校验，perChapter 只控制编号形式。
- 状态：`unresolved`，P2。

### P3-1【并发】活跃校验去重不是原子操作

- 位置：`backend/src/main/resources/mapper/FormatCheckMapper.xml` 的 `selectActive`
- 现状：先查询活跃任务，再插入 QUEUED；数据库没有对应部分唯一约束，也没有加锁。
- 影响：两个并发请求使用不同幂等键时，可能创建两个 QUEUED 任务。
- 建议：增加锁、唯一键或插入后二次去重，并补并发测试。
- 状态：`unresolved`，P3。

### P3-2【契约】结构树 `element_key` 与任务文档不完全一致

- 位置：`backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStructureParser.java:139`
- 现状：任务文档要求 `element_key={type}-{序}`，实现使用 `H-/T-/F-/C-/E-` 加 bodyIndex。
- 影响：当前用于唯一标识没有直接故障，但与验收文字不一致，后续前端若依赖 key 规则会产偏差。
- 建议：二选一：修改实现与测试，或回填任务文档明确稳定键口径。
- 状态：`unresolved`，P3。

### P3-3【测试】真实链路覆盖不足

- 现状：
  - 两个真库 IT 默认 `Tests run: 0`
  - 新增 Controller 未发现 MockMvc/接口级契约测试
  - 没有 P-07 错题集和 FormatTest.md
  - 转换测试全部使用 mock
- 影响：当前 340/28 的数字不能证明 Oracle SQL、真实 OOS 流、真实转换和 ≥95% 准确率。
- 建议：按 D-15 先完成真实冒烟，再决定是否需要额外修复。
- 状态：`unresolved`，P3。

---

## 六、放行判断

**结论：不建议按“后端与 ai-service 已完成”验收或合并。**

- 可以先保留当前实现作为候选基线。
- 必须先修复 P1-1、P1-2，再补规则读取接口、A4 和转换依赖。
- 修复后至少重新执行：
  - `mvn test`
  - `uv run python -m pytest -q`
  - 两个真库 IT
  - 真实 LibreOffice DOCX→PDF、pdf2docx PDF→DOCX、加密 PDF 反例
  - 4 份模板错题集 P-07 统计
  - D-15 端到端链路

当前不建议把任务文档中的“D-01~D-07 已完成”直接当作可交付结论；该状态更接近于“功能主体已实现，验收证据尚未闭合”。

---

## 七、审查过程与限制

- 本轮只读审查 `04-template-format.md` 的后端和 ai-service 部分，不评价前端 D-08~D-14。
- 经验检索成功，读取了 `00-Index/README.md`、`Knowledge-Index.md`、C01-C14 相关 Lesson、`PAT-spring-framework-exception-500` 与 `PAT-subagent-encrypted-content-failure`。
- 原始双轴子代理调用均因 `unsupported_encrypted_agent_content` 失败；本次 Spec/Standards 双轴由根 Agent 亲自完成。
- 未修改业务代码、测试断言、任务文档或项目数据。
- 未验证移动端，因本轮只涉及后端和 ai-service。
- 未执行真实数据库、Redis、OSS、LibreOffice 和 pdf2docx，相关结论均明确标注为未验证或环境缺失。

---

## 八、经验归档

本轮复盘已写入：

- [2026-11-06-template-format后端与ai-service符合性审查.md](../../../../../../Temp/ObsidianAgentKnowledgeLoop/10-Lessons/2026-11-06-template-format后端与ai-service符合性审查.md)

知识库索引与 Change Log 已更新；没有新增或修改 Pattern/Rule。

---

## 九、相关文件

- [04-template-format.md](../tasks/04-template-format.md)
- [DocxStyleExtractor.java](../../backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleExtractor.java)
- [FormatRuleMatcher.java](../../backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java)
- [DocxStructureParser.java](../../backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStructureParser.java)
- [TemplateVersionController.java](../../backend/src/main/java/com/cidas/edudoc/template/api/TemplateVersionController.java)
- [TemplateOutlineGenerator.java](../../backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateOutlineGenerator.java)
- [ConversionClient.java](../../backend/src/main/java/com/cidas/edudoc/template/infrastructure/ConversionClient.java)
- [ai-service converter.py](../../ai-service/app/converter.py)
