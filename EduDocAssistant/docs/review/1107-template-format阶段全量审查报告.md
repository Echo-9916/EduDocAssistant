# 04 格式与模板切片阶段全量审查报告

> 审查日期：2026-11-07  
> 审查范围：`docs/tasks/04-template-format.md` 的 D-01～D-15，覆盖后端、ai-service、前端及 D-15 验收材料  
> 审查基线：分支 `feat/fullstack-template-format`，`HEAD=5da5130`，并包含当前工作区未提交改动  
> 审查方式：Spec / Standards 双轴只读审查、全量自动化回归复核、环境与证据盘点、针对性代码路径核验  
> 审查结论：**不通过阶段放行；当前可判定“功能主体已实现、自动化回归已通过”，但存在 2 个 P1、9 个 P2，且 D-15 尚未闭合。**

## 一、结论摘要

本轮没有把任务文档中的“已完成”声明直接当作验收结论。实际审查确认：

- 后端 D-01～D-07 主体实现完整，前几轮点名问题多数已关闭，自动化回归通过。
- 前端 D-08～D-14 已有可运行页面和基础交互，但多个屏幕级验收项仍为部分实现。
- ai-service `/v1/convert` 的代码、错误映射和 pytest 覆盖已具备；真实 HTTP 双向转换仍未完成。
- D-15 仍缺错题集、ground truth、P-07 ≥95% 统计、完整 curl/SQL/stream 观测和全链路 e2e。
- 当前最严重的问题是模板状态接口方法错配，以及规则 `tolerance` / `aliases` 契约断裂；后者会直接威胁 P-07 准确率。

因此：

- **代码主体完成度：较高。**
- **阶段功能验收：不通过。**
- **可交付/可放行：否。**

## 二、审查基线与范围

### 2.1 代码基线

- 当前分支：`feat/fullstack-template-format`
- 当前提交：`5da5130 test(backend,docs): 04 模板真实语料接入 + 语料暴露的两处契约修复`
- 对比 `main...HEAD`：122 个文件、11291 行新增、7 行删除；另有多项前端 04 改动尚未提交。
- 本轮未修改业务代码、测试断言、数据库脚本或 ai-service 代码，只新增本审查报告。

### 2.2 任务契约

以 `docs/tasks/04-template-format.md` 当前内容为准，并按 D-01～D-15 逐项核验。任务文档顶部的“已完成”状态仅作为待验证声明；D-15 在同一文档中仍标记为“未开始”，与该声明形成阶段性矛盾。

### 2.3 审查限制

- 当前仓库未发现独立的 `CONTRIBUTING.md`、`CODING_STANDARDS.md` 或同类编码规范文件。Standards 轴依据任务文档中的架构红线、既有代码约定和 Fowler smell baseline 判断，不把主观 smell 表述成文档硬违规。
- 本轮未派发独立子代理。Spec / Standards 两轴由根 Agent 顺序完成，结论没有经过独立子代理复核。
- 真实 Oracle/Redis/OSS 业务链路、真实服务间 HTTP、完整浏览器链路和 P-07 样本集未在当前环境全部执行。

## 三、本轮可执行验证证据

| 验证项 | 命令/证据 | 实际结果 | 判定 |
| --- | --- | --- | --- |
| 后端自动化 | `backend`: `mvn test` | 387 tests，0 failure，0 error，0 skipped，退出码 0 | 通过 |
| 真库 IT 覆盖 | Surefire 报告 | `RbacLifecycleIntegrationTest`、`ChunkEmbeddingVectorIntegrationTest` 均为 `Tests run: 0` | 未覆盖真库 |
| ai-service 自动化 | `ai-service`: `uv run python -m pytest -q` | 33 passed，1 个第三方 Starlette 弃用警告，退出码 0 | 通过 |
| 前端自动化 | `web`: `pnpm test -- --run` | 32 个测试文件，151 passed，退出码 0 | 通过 |
| 前端生产构建 | `web`: `pnpm build` | 3132 modules，构建成功，退出码 0 | 通过 |
| 差异空白检查 | `git diff --check HEAD` | 退出码 0，仅有 CRLF 提示 | 通过 |
| ai-service 内部令牌 | `EDUDOC_AI_INTERNAL_TOKEN` | 已设置 | 可用于本地握手前置 |
| 数据库凭据 | `EDUDOC_DB_USERNAME` / `EDUDOC_DB_PASSWORD` | 未设置 | 真库 IT 无法执行 |
| LibreOffice | PATH 查找 | `C:\Program Files\LibreOffice\program\soffice.com` | 引擎可执行文件存在 |
| LibreOffice 显式配置 | `EDUDOC_SOFFICE_BIN` | 未设置 | 需按部署配置显式注入 |
| OSS 凭据 | `EDUDOC_OSS_ACCESS_KEY_ID` / `EDUDOC_OSS_ACCESS_KEY_SECRET` | 已设置 | 仍需真实 put/get/presign 验证 |
| P-07 样本 | `docs/test/fixtures/` | 仅 7 个真实模板/转换件，未发现注入错题集和 ground truth JSON | 不满足 D-15 |

自动化全绿只能证明当前单元/组件测试没有回归，不能证明真库 SQL、真实 HTTP、文档转换保真、页面点击定位和 P-07 准确率已通过。

## 四、P1 问题

### P1-1 模板状态接口前后端方法不一致

- 前端 API 使用 `POST`：`web/src/api/template.ts:59-60`。
- 后端只接受 `PATCH`：`backend/src/main/java/com/cidas/edudoc/template/api/TemplateController.java:74`。
- 前端测试还把错误的 `POST` 固化为断言：`web/src/api/template.test.ts:37-38`。
- 影响：教师在模板列表中执行“停用”或“重新启用”时会收到 405，D-01 辅助状态能力在真实前端调用链中不可用。
- 测试为何未发现：API 测试 mock 了 `http.ts`，只断言前端自身发出的错误方法，没有与后端路由做契约对齐。
- 建议修复：前端改为 `PATCH`，同时将测试改为断言 `patch`；增加一个真实路由契约测试或轻量 e2e，防止同类前后端方法漂移。

### P1-2 `tolerance` / `aliases` 契约位置断裂，真实字体回退链会误报

- 任务契约规定根级 `tolerance?`：`docs/tasks/04-template-format.md` D-02。
- 解析器实际输出根级 `tolerance`：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:139` 输出 `tolerance.aliases`。
  - `TemplateRuleParser.java:147` 输出 `tolerance.pt`。
- `RuleConfig` 却从 `expect.tolerance` 读取字号容差：`backend/src/main/java/com/cidas/edudoc/template/domain/RuleConfig.java:122-125`。
- `FormatRuleMatcher.matchFont`：`backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:65-89`，只比较 `expect` 与生效字体值，没有消费 `aliases`。
- 真实语料会触发该问题：`C语言课程设计报告格式.docx` 解析出 `黑体;SimHei`，当前主名进入 `expect.family`，`SimHei` 只进入根级 `tolerance.aliases`。若 Word 实际提取为 `SimHei`，当前比对器会产生假阳性。
- 针对性探针已验证：根级 `tolerance.pt=0.2` 通过 `RuleConfig.tolerancePt()` 读取为 `null`，自定义容差回退到默认 `0.5pt`；字体别名应通过时产生 1 条问题。
- 影响：真实模板的自定义容差失效，字体回退链可能误报，直接威胁 P-07 “格式问题定位准确率 ≥95%”。
- 测试盲区：`backend/src/test/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParserTest.java:308-310` 只断言解析器写出根级 aliases；`FormatRuleMatcherTest` 使用 `expect.tolerance.pt`，没有用解析器产物做比对闭环。

## 五、P2 问题

### P2-1 规则保存仍接受无执行意义或不完整的配置

`RuleConfig` 已明显改善保存期校验，但仍有三个可复现缺口：

- `CAPTION.pattern=""` 被接受。`RuleConfig.java:83-93` 只判断字段是否为文本并尝试编译空正则；空正则合法。随后 `RuleConfig.pattern()` 对空白文本返回 `null`，`FormatRuleMatcher.matchCaption` 在无 `perChapter` 时直接返回，规则实际无动作。
- `MARGIN` 的非法字段类型被静默过滤。`RuleConfig.java:77-80` 只要至少一个键是有效数值即通过，`pageValues()` 会丢弃其他类型错误字段。探针 `top:"bad", left:20` 被接受为 `{left=20}`。
- `CODE` 允许完全空的 `expect`。`RuleConfig.java` 的 default 分支不校验 `style` / `keepIndent`；`FormatRuleMatcher.matchCode` 只检查是否存在 `CODE_BLOCK`，不消费契约声明的样式和保留缩进语义。
- 影响：规则可被保存并发布为 VALID，但运行时静默跳过或放宽检查，继续形成“保存成功、校验失真”的风险。
- 建议：对所有已知键做类型/值域强校验；CAPTION 的空字符串应归一为缺失；CODE 必须要求契约键；匹配器应对不支持的键结构失败关闭。

### P2-2 模板解析失败原因未持久化，重开编辑页后丢失

- 创建版本响应携带 `failureReason`：`backend/src/main/java/com/cidas/edudoc/template/application/impl/TemplateVersionServiceImpl.java:100-107`。
- `listVersions()` 明确回填 `null`：`TemplateVersionServiceImpl.java:178-188`。
- 持久化对象 `TemplateVersion.java` 没有对应字段；服务注释承认表 3-23 无承载列：`TemplateVersionService.java:30-33`。
- 影响：上传坏文件后首次能显示失败原因，关闭并重新打开编辑页后 INVALID 原因消失，D-09 的解析失败反馈不完整；任务文档要求的 `result_summary` 语义没有真正落库。
- 建议：在不改 DDL 的约束下，明确选用现有可承载字段或评审最小迁移；不得只在一次性响应中回显。

### P2-3 D-09 教师模板页仅完成基础流程，屏幕级验收未闭合

`web/src/pages/teacher/Templates.tsx` 已实现列表、新建、上传、规则 JSON 编辑、保存和发布调用，但缺少任务文档明确要求：

- 规则表没有按 `rule_type` 分组，而是单一平表：`Templates.tsx:391`。
- 没有右侧“学生端效果预览”静态示意。
- 发布按钮直接调用接口：`Templates.tsx:227-239`、`Templates.tsx:344`，没有发布确认 Modal，也没有确认版本、适用范围和变更说明。
- 选择已发布版本时只显示只读提示：`Templates.tsx:386`，编辑表被整体隐藏，未展示只读规则。
- 页面路由有 `TEMPLATE_MANAGE` 守卫（`web/src/routes.ts:23`、`web/src/App.tsx:75-79`），因此不应误报为完全没有权限保护；但屏幕级 40301 展示仍建议纳入浏览器验收。
- 影响：D-09 的核心编辑体验和发布前确认流程未达到契约，不是纯视觉差异。

### P2-4 D-10 学生模板页主体可用，但工作台快捷入口未接线

- 学生只读要求页和“按模板新建”已实现：`web/src/pages/student/Templates.tsx:25-121`。
- 只读要求表按模板规则渲染，未出现编辑/发布入口，主体语义正确。
- 工作台路由仍是占位：`web/src/routes.ts:19`，`web/src/App.tsx:22-27` 的 `pageOf` 没有 `workbench` 映射，落到 `SectionPlaceholder`。
- 影响：D-10 明确要求的“工作台快捷入口「按模板新建」接线”未完成。

### P2-5 D-11 问题定位未使用 `highlightRange`，也没有映射失败退化路径

- `FormatPanel` 使用 `Math.floor(loc.paragraphIndex / 3)` 近似滚动：`web/src/pages/view/FormatPanel.tsx:118-124`。
- `DocumentView` 已提供 `highlightRange(absStart, absEnd)`：`web/src/pages/DocumentView.tsx:72-85`，但 `FormatPanel` 没有调用。
- 当前没有字符区间高亮；解析失败或无块时直接 `return`，没有“跳至该页标记”的退化路径。
- 影响：D-11 的“点击定位并按 `location_ref` 映射 + `highlightRange`”只完成了粗略滚动，问题清单无法真实验证定位精度。

### P2-6 D-12 修复确认弹窗缺少完整影响面提示和冲突重查

- 传给 `FixModal` 的 issues 仅为已勾选项：`web/src/pages/view/FormatPanel.tsx:209-212`。
- 弹窗只列出已勾选的可修复项，没有展示不可修复项和未勾选项清单。
- 40901 目前只走通用 `message.error`：`FormatPanel.tsx:235-248`，没有“提示冲突后重新查询当前校验/清单”的动作。
- 影响：用户无法在确认前看清本次未处理范围；并发版本冲突后页面状态可能停在旧清单。

### P2-7 D-13 转换仅完成批量 Modal 和轮询，多个验收项与契约冲突

代码已实现批量选择、方向预检、2s 轮询、失败原因和基本历史列表，但：

- 只有文档列表批量入口：`web/src/pages/Documents.tsx:205-218`，没有行级“格式转换”操作。
- 下载使用 `window.open`：`web/src/pages/ConversionModal.tsx:114-116`，不是 D-13 要求的 fetch blob + token 头。
- 历史记录在 Modal 页签中：`ConversionModal.tsx:130-148`，不是抽屉；只有单选时可用：`ConversionModal.tsx:84-86`、`ConversionModal.tsx:133`。
- 历史表没有检索框，也没有完整源/目标文件、时间字段展示；当前列只有文档、目标格式、状态、操作：`ConversionModal.tsx:106-118`。
- 后端历史列表固定传 `downloadUrl=null`：`backend/src/main/java/com/cidas/edudoc/template/application/impl/ConversionServiceImpl.java:103-111`，所以成功记录在历史页会立刻显示“链接已过期”。
- 内部契约本身存在待统一项：D-06 定义 GET 返回签名下载 URL，D-13 又要求 fetch blob + token。这不是单纯前端实现偏差，需要先确定唯一下载通道。
- 影响：转换记录可用性和 D-13 体验验收不完整，且契约冲突会继续造成前后端各自解释。

### P2-8 D-14 代码与图表规范化页缺少定位、语言和代码块位置

- `NormalizeSection` 的表格没有行点击定位：`web/src/pages/NormalizeSection.tsx:75-96`。
- 仅有整页“打开预览”按钮：`NormalizeSection.tsx:61-63`。
- 表格列只有元素、章节、编号、题注、章内序，没有代码语言和代码块位置。
- 后端 `DocumentStructureService.ElementView` 也没有语言字段，只有 `elementKey/elementType/chapterNo/sequenceNo/displayLabel/captionText/locationJson`。
- 影响：D-14 的“点击定位同 D-11 机制”“代码块列表（语言/位置）”均未实现。

### P2-9 D-15 尚未形成可核验的阶段验收 evidence package

`docs/test/FormatTest.md` 当前仅登记：

- 4 组模板真实语料及解析冒烟结论。
- PDF→DOCX 引擎级转换和部分质量观察。
- D-15 待办清单。

仍缺失：

- 逐接口 curl 正反例及预期响应。
- stream/SQL 观测步骤和实际结果。
- POI 注入脚本、错题集 docx、ground truth JSON。
- P-07 命中数/应命中数、漏报率和 ≥95% 统计。
- 教师上传→发布→按模板新建→校验→修复→复审→双向转换→下载的完整 e2e。
- 真库三条 SQL 冒烟、真实服务握手、加密件反例和 50401 超时实测。

因此 D-15 不能判定通过，阶段不能放行。

## 六、D-01～D-15 验收矩阵

| 任务 | 代码面判定 | 关键依据 | 运行/交付判定 |
| --- | --- | --- | --- |
| D-01 模板主档 | 通过 | 列表、创建、状态、删除与权限逻辑有实现和测试 | 真库未验证 |
| D-02 模板版本解析发布 | 部分符合 | P1-2、P2-1、P2-2 仍开放 | 不可放行 |
| D-03 结构树 | 通过 | 段落索引、字符区间、元素键和幂等逻辑存在 | 真库未验证 |
| D-04 格式校验 | 通过 | 创建、Worker、查询和问题清单代码完整，测试通过 | 真实服务链路未验证 |
| D-05 一键修复 | 通过 | 五类修复面、成版、复审代码和单测存在 | 真实 docx 回读与 e2e 未验证 |
| D-06 格式转换后端 | 基本通过 | Worker、超时、错误码、multipart 契约和审计存在 | HTTP/引擎 e2e 未完成 |
| D-07 按模板新建 | 通过 | POI 生成、OSS、建档绑定和事务边界存在 | 真实 Tika/OSS 链路未验证 |
| D-08 API 模块 | 不通过 | P1-1 状态方法错配；类型面仍有契约缺口 | 前端真实请求会 405 |
| D-09 教师模板页 | 部分符合 | P2-3：无分组、预览、发布确认、只读规则展示 | 功能不完整 |
| D-10 学生模板页 | 部分符合 | 只读页和新建已实现；工作台入口仍占位 | 入口缺失 |
| D-11 校验面板 | 部分符合 | 轮询/恢复/ERROR/统计已实现；定位无高亮和退化路径 | 定位验收不通过 |
| D-12 修复确认 | 部分符合 | 只展示勾选项；不可修复清单和 40901 重查缺失 | 体验与冲突处理不完整 |
| D-13 格式转换前端 | 部分符合 | 批量/轮询有；行级入口、抽屉、检索、下载通道和历史 URL 不完整 | 不通过 |
| D-14 规范化页 | 部分符合 | 结构树只读表存在；定位、语言、位置缺失 | 不通过 |
| D-15 端到端与测试文档 | 不通过 | P-07、错题集、e2e、真实链路均未闭合 | 阻断放行 |

## 七、Spec 轴结论

Spec 轴判定：**不通过。**

- 要求已实现但行为错误：模板状态方法错配。
- 要求已实现但契约错误：根级 `tolerance` / `aliases` 与规则消费位置不一致。
- 要求部分实现：D-09、D-10、D-11、D-12、D-13、D-14。
- 要求尚未完成：D-15。
- Spec 轴最严重问题：P1-2，因为真实模板字体别名/容差会产生误报，直接威胁 P-07 的核心指标。

## 八、Standards 轴结论

Standards 轴判定：**有条件通过；存在 2 个判断型风险。**

- 未发现独立编码规范硬违规。
- 架构红线大体遵守：POI 解析/写回集中在模板基础设施，转换引擎在 ai-service/worker，异步任务经 Outbox，修复不直接改转换结果版本。
- P1 动作可改进判断：测试只 mock 前端 HTTP 层，实际把错误方法固化为绿色断言，说明契约测试不能只验证调用者自己的实现。
- P2-1 可改进判断：`RuleConfig` 已降低 Primitive Obsession，但前后端仍各自手写规则校验和 selector 词表，存在 `Divergent Change` 风险；建议共享契约夹具或由后端返回可执行 schema。
- P2-7 可改进判断：下载 URL 与 fetch blob 两套口径并存，属于跨模块 contract divergence；应先统一设计决策，再补实现。
- Standards 轴最严重问题：测试 false green。它没有直接改变用户行为，但掩盖了 P1-1，必须随 P1 一起修复。

## 九、环境与未验证项

| 项目 | 状态 | 影响 |
| --- | --- | --- |
| 真实 Oracle/Redis | 未执行 | 行锁、JSON_VALUE 排序、活跃去重和事务边界仍只有 mock/单测证据 |
| 真实 OSS | 未执行 | 凭据存在，但 put/get/presign 与角色流转未实测 |
| 真实 ai-service HTTP | 未执行 | token、trace、multipart、失败字段映射没有跨进程证据 |
| LibreOffice 双向转换 | 未完整执行 | `soffice` 可执行文件存在，但显式配置、HTTP 服务链路和文件可打开性未验证 |
| pdf2docx | 仅有引擎级记录 | PDF→DOCX 已做过样本转换，但没有完整服务端 e2e |
| 加密/损坏/超时反例 | 单测覆盖为主 | 真实文件和真实超时未验收 |
| P-07 错题集 | 未生成 | 无法证明 ≥95% |
| 浏览器全链路 | 未执行 | 页面布局、权限守卫、下载、定位点击和交互状态未在真实服务栈复核 |

## 十、整改优先级

### 放行前必须完成

1. 修复 D-08 状态方法，改为 `PATCH`，并让测试与后端路由对齐。
2. 统一 `tolerance` 根级位置，消费 `aliases`，用真实字体回退链和自定义容差补断言。
3. 收紧 `CAPTION`、`MARGIN`、`CODE` 的规则不变量，禁止可保存但无动作的配置。
4. 持久化解析失败原因，确保重新打开编辑页仍可解释 INVALID。
5. 完成 D-09～D-14 的任务文档明确缺口。
6. 完成 D-15 错题集、P-07 统计、真库/真实服务/双向转换/e2e。

### 建议顺序

1. 先修两条 P1，并重跑后端、ai-service、前端、build。
2. 再修规则配置和失败原因持久化，补负向契约测试。
3. 再补 D-09～D-14 的交互缺口，避免业务行为继续漂移。
4. 最后执行 D-15，只有在真实验收证据齐全后才把任务状态改为“已完成”。

## 十一、审查过程与限制说明

- 本轮实际读取并核对了任务文档、当前工作区、后端/ai-service/前端实现、相关测试、Surefire 报告、FormatTest 和真实 fixtures。
- 本轮实际重跑了后端、ai-service、前端测试和前端构建；结果见第三节。
- 本轮没有修改业务代码；没有把自动化测试通过等同于阶段验收通过。
- 本轮没有派发独立子代理，Spec / Standards 两轴由根 Agent 顺序完成。因此本报告应表述为“根 Agent 双轴审查”，不能表述为“独立双代理审查”。
- 本轮没有执行真实 Oracle/Redis/OSS 业务、真实跨服务 HTTP、完整浏览器 e2e 或 P-07 统计；这些项目不得标记为通过。
