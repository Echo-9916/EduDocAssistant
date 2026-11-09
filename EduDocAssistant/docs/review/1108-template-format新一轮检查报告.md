# 04 格式与模板切片新一轮检查报告

> 检查日期：2026-11-08  
> 检查性质：对上一轮 `1108-template-format阶段复审报告` 之后的新增量进行复审  
> 基线：`HEAD=5da5130`；本轮未新增提交，审查对象为当前工作区相对 `HEAD` 的全部修改  
> 检查范围：新增的 CODE `keepIndent`、D-11/D-14 定位、D-13 目标格式、P07 错题集、真库 SQL 集成测试及相关任务文档  
> 结论：**代码面已形成闭环；D-15 仍部分未完成，故阶段交付仍不通过。**

## 一、总结

上一轮剩余的 4 个 P2 已通过本轮增量处理：

- CODE `keepIndent` 已进入保存校验和 `matchCode`。
- D-11 已消费 `location_ref` 的区间并接入共享 `locate.ts`，失败时有页面标记退化。
- D-13 已增加目标格式选择，下载通道正文改为 presigned GET，单一契约。
- D-14 已通过 `navigate(state.focus)` 把元素位置传给预览页，并按 D-11 同机制定位；语言项按范围缩减声明处理。

本轮真实执行新增测试后，发现进度已经不只是一次代码复审：

- `TemplateFormatSqlIntegrationTest` 4 条真实 Oracle 用例全部执行并通过，不是跳过。
- `P07ErrorSetTest` 对 4 份真实模板注入 20 个已知问题，命中 20 个，recall=100%，生成了 `mutated/*.docx` 和 `groundtruth.json`。
- `RbacLifecycleIntegrationTest`、`ChunkEmbeddingVectorIntegrationTest` 也已真库执行，不再是 `Tests run: 0`。

剩余没有闭合的是运行级 D-15：完整 HTTP 服务链路、真实 DOCX↔PDF 双向转换、加密/超时反例、下载可打开性、浏览器端到端、curl/SQL/stream 观测文档和最终测试基线的文档同步。

## 二、实际测试证据

| 项 | 结果 |
| --- | --- |
| 后端 `mvn test` | 400 tests，0 failure，0 error，0 skipped，BUILD SUCCESS |
| `RbacLifecycleIntegrationTest` | 1/1 真库通过，非跳过 |
| `ChunkEmbeddingVectorIntegrationTest` | 1/1 真库通过，非跳过 |
| `TemplateFormatSqlIntegrationTest` | 4/4 真库通过，覆盖 parse_error、selectActive/30 分钟窗口、markRunning CAS、行锁/CAS、JSON_VALUE 段落序 |
| `P07ErrorSetTest` | 1/1 通过；20 expected，20 hits，recall=100% |
| ai-service `pytest` | 33 passed，1 个第三方 Starlette 弃用警告 |
| 前端 `pnpm test -- --run` | 35 files，168 passed |
| 前端 `pnpm build` | 3135 modules transformed，成功 |
| `git diff --check HEAD` | 退出码 0，仅有 CRLF 提示 |

## 三、新增量核验

### 3.1 CODE `keepIndent` 已接通

- `RuleConfig` 对 CODE 要求 `style`，且 `keepIndent` 若出现必须为 JSON 布尔：`backend/.../RuleConfig.java`。
- `FormatRuleMatcher.matchCode(rule, config, snapshot, issues)` 消费 `keepIndent`，逐代码段检查首个有内容 run 的行首是否为空格/制表符。
- `FormatRuleMatcherTest.codeKeepIndentViolationsReported` 覆盖缩进丢失和保留两种状态。
- 风格比较没有跨文档比对样式 id，而是由 CODE_BLOCK selector 分类承载，已在注释中说明。这个口径与范围缩减后的任务文档一致。

### 3.2 D-11/D-14 共用定位

- 新增 `web/src/pages/view/locate.ts`：
  - 用段均字符估算段起点，再叠加 `charStart/charEnd` 得高亮区间；
  - 定位失败退化为 `scrollToPageMarker`。
- `FormatPanel` 现在先尝试 `locateByParagraph`，再页标记退化，最后才提示无法定位。
- `NormalizeSection` 行点击通过 `navigate(..., state:{focus})` 传递元素位置。
- `DocumentView` 读取 focus state 并调用同一套定位逻辑。
- 页面分隔节点已补充 `page-anchor-{n}`。

这满足上一轮要求的“位置参数真进入预览页并复用 D-11 机制”。当前算法是明确的近似值，不是 POI 段流与 Tika 块流的精确恒等映射，但已写入 `AVG_PARA_CHARS/AVG_PAGE_CHARS` 策略注释，属于可接受的未完成精度而非断链。

### 3.3 D-13 目标格式与下载契约

- `ConversionModal` 新增目标格式选择：自动匹配、全部 PDF、全部 Word。
- 手选时仅处理方向匹配项，其余跳过并提示。
- 下载统一为 presigned GET 直链，任务文档 D-13 的 fetch blob 口径已删除。
- 后端 `listByDocument` 对 SUCCEEDED 行逐行签名，历史抽屉不再永远显示“链接已过期”。

### 3.4 P07 错题集

- `P07ErrorSetTest` 使用真实模板源文件，调用与生产相同链路的 `TemplateRuleParser`、`RuleConfig`、`DocxStyleExtractor`、`FormatRuleMatcher`。
- 已产出 4 份 `.mutated.docx` 和 1 份 `groundtruth.json`。
- 当前结果 20/20，recall=100%，高于 95%。
- 该结果仍是本机测试过程，不等同于通过 HTTP 服务的真实验收 e2e。

### 3.5 真库 SQL

- `TemplateFormatSqlIntegrationTest` 在 `EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD` 已注入时真实连接 Oracle。
- Surefire 报告显示 tests=4，errors=0，skipped=0，failures=0。
- 覆盖 04 切片最依赖的四条 SQL/事务行为，解决了前几轮“真库全部 0”的核心证据缺口。

## 四、仍存在的问题

### P2-1 文档基线数字不一致

- `docs/tasks/04-template-format.md:4` 和 `:297` 写 `mvn test 392/392`；本轮实际 Surefire 总数为 `400/400`。
- `docs/test/FormatTest.md:38-41` 仍把真库四条 SQL、错题集、P-07 和端到端全部标为未完成；其中真库 SQL 和错题集已经实际执行并通过。
- 影响：文档仍是过时口径，会被后续 reviewer/发布流程误读。
- 建议：更新 `mvn test` 数字为 400/400；将真库 SQL、P07 错题集两项标记为已执行，并附报告结果；端到端、真实 HTTP 转换保持未完成。

### P2-2 D-15 仍未完全闭合

当前已经完成：

- 真库 SQL 冒烟。
- 错题集脚本与 P-07 本机统计。
- 真实数据库的两个既有 IT。
- DDL 脚本、Mapper、EE 与设计文档同步。

仍缺少：

- 以三服务 HTTP 链路串接的教师上传模板→发布→按模板新建→校验→修复→复审→转换→下载。
- 真实 ai-service `/v1/convert` 通过后端 HTTP 调用，完成 DOCX→PDF、PDF→DOCX、加密件反例和超时反例。
- 对生成文件的下载打开性与正确性检查。
- 浏览器全链路和 JSON/SQL/stream 观测记录。
- `FormatTest.md` 的最终测试文档闭合。

因此 D-15 仍未达到“完成”，阶段交付判断维持不通过。

### P3-1 定位退化对第 1 页不完整

- 页锚点只在分块页号发生变化时渲染。估算得到第 1 页时，`page-anchor-1` 本身不存在。
- 影响：段落定位失败且估算为第 1 页时，退化跳页也可能失败，最终只提示人工浏览。
- 建议：在正文流头部或首个块增加稳定 `page-anchor-1`，或让 `scrollToPageMarker` 对第 1 页回退到首个块滚动。

### P3-2 DocumentView focus 的 effect 依赖偏窄

- `DocumentView` 的 focus effect 只依赖 `blocks.length`。
- 如果同一组件实例内预览不同文档且块数恰好相同，focus state 变化可能不触发再次定位。
- 当前从规范化页跳转通常是新导航，实际风险低；但建议把 `location.state.focus` 和 `docId` 纳入依赖，避免高块数文档的边界情况。

### P3-3 测试文档仍未记录新产物

- P07 错题集的执行结果主要存在于 `groundtruth.json` 和控制台输出，尚未回写到 `docs/test/FormatTest.md`。
- 建议：把 20/20 命中明细、生成命令/入口和统计口径写入 FormatTest，形成可追溯交付证据。

## 五、Spec 轴

Spec 轴判定：**代码规格已基本通过，仍受 D-15 门禁限制。**

- D-01～D-14 当前与最新任务正文口径一致。
- 剩余规格问题集中在 D-15 证据链和文档数字漂移，不是确定性业务功能错误。
- Spec 轴最严重项：P2-1 测试基线数字和 D-15 文档状态与实际不符。

## 六、Standards 轴

Standards 轴判定：**通过，含 2 个判断型风险。**

- 未发现新的架构红线违规。
- 真实库集成测试和 P07 错题集结构合理，测试用同一生产解析/匹配链，离线构建跳过逻辑保留。
- 判断风险一：任务文档基线数字没有随新增测试同步；这类数字容易在后续复审中被直接引用。
- 判断风险二：`locate.ts` 引入固定经验常数，虽注释清楚，但属于“近似正确”实现，后续若文档块密度变化，需要校准；建议在 P07 测试中记录校准依据。
- Standards 轴最严重判断项：文档侧过时数字。

## 七、阶段放行建议

- 在修正 P2-1 文档数字和 P3 文档记录前，不要宣称 D-15 完成。
- 如只需“代码面放行”，D-01～D-14 已满足当前任务正文和上一轮整改口径。
- 如需“阶段交付放行”，仍需完成完整 HTTP/转换/浏览器 e2e 并闭合 `FormatTest.md`。

## 八、过程限制

- 本轮未修改业务代码、测试或文档；只新增本检查报告并结合知识库做复核。
- 本轮未派发独立子代理，Spec / Standards 由根 Agent 顺序完成，不能表述为独立双代理审查。
- 本轮已真实执行数据库凭据注入后的集成测试，但仍未启动三服务做 HTTP 端到端。
