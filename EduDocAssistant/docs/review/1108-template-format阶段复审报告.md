# 04 格式与模板切片阶段复审报告

> 复审日期：2026-11-08  
> 复审范围：依据 `docs/tasks/04-template-format.md` 对 D-01～D-15 做复审，重点核对上一轮 `1107-template-format阶段全量审查报告` 的 2 P1 + 9 P2 是否闭合  
> 固定点/基线：`HEAD=5da5130`；本次未产生新提交，审查对象为相对 `HEAD` 的当前工作区修改  
> 复审方式：Spec / Standards 双轴只读检查、全量自动化回归、代码路径核验、验收材料与环境盘点  
> 复审结论：**代码面基本通过；阶段交付不通过。两条 P1 已关闭，6 个 P2 完整闭环，4 个 P2 部分闭环，D-15 仍未完成。**

## 一、结论摘要

本轮不再把上一轮问题当作“自动已修复”，而是逐项对照实际 diff 和测试。结果：

- P1-1 模板状态接口方法错配已关闭：前端已切到 `PATCH`，测试同步绑定方法族。
- P1-2 `tolerance/aliases` 契约断裂已关闭：`RuleConfig` 改为根级三键模型，字体别名被匹配器消费，已补测试。
- 大部分前端屏幕级 P2 已闭环：教师规则分组/发布确认、工作台入口、修复影响面与 40901 重查、转换历史抽屉均已接入。
- 仍有 4 个 P2 是“改进但未完全达到任务契约”：CODE 规则弱存在性检查、D-11 非文本类仍近似定位、D-13 无目标格式选择且下载契约文字未同步、D-14 代码语言缺失且行点击不是 D-11 同机制。
- P2-9 / D-15 仍然未完成，真实联调和 P-07 统计缺失，因此不能判定阶段交付通过。

## 二、验证证据

| 验证项 | 实际结果 |
| --- | --- |
| 后端自动化 `mvn test` | 391 tests，0 failure，0 error，0 skipped，BUILD SUCCESS |
| 真库 IT | `RbacLifecycleIntegrationTest`、`ChunkEmbeddingVectorIntegrationTest` 均为 `Tests run: 0` |
| ai-service `uv run python -m pytest -q` | 33 passed，1 个 Starlette 弃用警告 |
| 前端 `pnpm test -- --run` | 34 test files，162 passed |
| 前端 `pnpm build` | 3134 modules transformed，build 成功 |
| `git diff --check HEAD` | 退出码 0，仅 CRLF 提示 |
| 真实 DB/真实服务/真实转换 | 未执行，DB 凭据未注入 |
| P-07 错题集与准确率 | 未生成，仍不满足 D-15 |

## 三、上一轮问题闭环矩阵

| 上一轮问题 | 状态 | 证据与结论 |
| --- | --- | --- |
| P1-1 状态接口 POST/PATCH 错配 | 已关闭 | `http.ts:81` 新增 `patch`；`template.ts:60-61` 使用 PATCH；测试 `template.test.ts:37-39` 断言 patch 且未误调 post |
| P1-2 `tolerance/aliases` 契约断裂 | 已关闭 | `RuleConfig` 根级三键；`FormatRuleMatcher.matchFont` 消费 aliases；`fontAliasSatisfiesExpectation`、`rootTolerancePtIsConsumed` 回归 |
| P2-1 无动作规则可入库 | 部分关闭 | CAPTION 空串/MARGIN 错类型/CODE.style 已强校验；但 CODE 的 `keepIndent` 未进入不变量，匹配器仍只检查 `CODE_BLOCK` 是否存在 |
| P2-2 解析失败原因不持久化 | 已关闭（代码面） | `TemplateVersion.parseError`、Mapper、DDL、服务和 `listVersions` 已贯通；真实库仍需执行 ALTER |
| P2-3 D-09 教师模板页缺口 | 已关闭 | 分组、学生端示意、发布确认、已发布只读回显均在 `teacher/Templates.tsx` 落地 |
| P2-4 学生工作台入口 | 已关闭 | `student/Workbench.tsx` + `App.tsx` 接线，四入口测试通过 |
| P2-5 D-11 定位高亮 | 部分关闭 | 题注/引用可块内检索精确高亮；字体/字号等仍用 `paragraphIndex / 3` 近似滚动，未按 location_ref 的 charStart/charEnd 计算，也没有“跳至该页标记”退化 |
| P2-6 D-12 修复影响面与冲突重查 | 已关闭 | `FixModal` 三段清单，`40901` 进入冲突回调查并 `track` 重查 |
| P2-7 D-13 转换入口/历史/下载 | 部分关闭 | 行级入口、抽屉、检索、时间列、历史签名 URL 已实现；但仍缺目标格式选择，任务正文仍写 fetch blob，与实现/整改记录不一致 |
| P2-8 D-14 规范化页 | 部分关闭 | 增加位置列和行点击直达预览；但代码语言字段/数据未实现，行点击只是打开整页预览，未传入元素位置做高亮 |
| P2-9 D-15 验收证据包 | 未关闭 | 错题集、ground truth、P-07 ≥95%、完整 e2e、真库迁移实证均未完成 |

## 四、仍影响阶段验收的问题

### P2-1 残余：CODE 规则仍是弱存在性检查

- 任务契约要求 `CODE→style+keepIndent`：`docs/tasks/04-template-format.md:47`。
- `RuleConfig` 现在只校验 `style` 存在：`backend/.../RuleConfig.java:116`，没有校验或消费 `keepIndent`。
- `FormatRuleMatcher.matchCode` 仍只判断 `CODE_BLOCK` 是否存在：`FormatRuleMatcher.java:254-257`。
- 影响：配置了代码样式和缩进语义的规则仍不会比对该语义，属于“能保存但不执行”的同类残余。
- 建议：补齐 `keepIndent` 不变量，并在事实快照中承载代码样式/缩进数据后消费。

### P2-3 残余：D-11 定位尚未完全按任务口径

- 任务要求“按 location_ref 的段落索引映射到文本视图块并 highlightRange 近似高亮，映射失败退化为跳页标记”：`docs/tasks/04-template-format.md:107`。
- 当前题注/引用通过 `actualValue` 做块内文本检索：`web/src/pages/view/FormatPanel.tsx:130-139`，这是有效但针对文本锚点的路径。
- 字体、字号等格式问题的近似路径仍为 `Math.floor(paragraphIndex / 3)`：`FormatPanel.tsx:146`，没有利用 `location_ref.charStart/charEnd`，也没有页面级兜底。
- 影响：D-11 的定位精度和对齐性仍不能宣称完全满足。
- 建议：对段落问题建立段落索引到块的映射函数；映射失败时尽量跳至相邻页面标记，而不是仅提示“近似定位”。

### P2-7 残余：D-13 目标格式选择和下载契约未收口

- 任务要求用户在转换 Modal 选择目标格式，并列明“方向合法性前端预检”：`docs/tasks/04-template-format.md:118`。
- 当前 `ConversionModal` 仅按 MIME 自动确定方向，没有目标格式选择：`web/src/pages/ConversionModal.tsx:18-22,64,124`。
- 任务 D-13 正文仍写“下载 fetch blob，token 头”，但实现和整改记录已裁定为 presigned GET 直链；`docs/tasks/04-template-format.md:118` 未同步，同一文件内仍存在两套下载口径。
- 影响：单文本文档仍有自相矛盾；混合类型批量时用户无法显式选择目标格式。若产品上“自动匹配”已经拍板，则必须回改任务正文并删除 fetch blob 段落，而不是只在整改记录中说明。
- 建议：先统一 D-13 正文为 presigned 直链；如要保留“选目标格式”，需在 Modal 加入可选目标控件和合法性预检。

### P2-8 残余：D-14 代码语言缺失，定位不是 D-11 同机制

- 任务要求代码块列表包含“语言/位置”，点击定位同 D-11：`docs/tasks/04-template-format.md:124`。
- 当前后端 `DocumentStructureService.ElementView` 无语言字段；前端仅显示文档段落位置，并注明语言待编辑器切片：`web/src/pages/NormalizeSection.tsx:106`。
- 行点击仅 `navigate` 到预览页：`NormalizeSection.tsx:82`，未传递元素位置，也没有触发对应高亮。
- 影响：D-14 只完成了位置展示和跳转，未完成本次切片明确要求的语言列和 D-11 同机制定位。
- 建议：若语言确实未列入后端结构树字段，应在需求侧承认范围缩减；否则补齐 `language`/`locationJson` 消费和定位参数。

## 五、D-15 与真实运行缺口

- `docs/test/FormatTest.md` 仍只有模板语料和待办项。
- 没有 POI 注入脚本、错题集 docx、预期命中清单、ground truth JSON。
- 没有 P-07 命中数/应命中数和 ≥95% 统计。
- 没有完整 curl 正反例、SQL/stream 观测记录和三服务 e2e。
- 新增的 `parse_error` 字段仍需要在真实数据库执行 `ALTER TABLE edudoc_template_version ADD parse_error VARCHAR2(1000)` 并冒烟。

因此，即使代码面回归已全绿，阶段交付仍不可放行。

## 六、Spec 轴结论

Spec 轴判定：**有条件通过，阶段交付不通过。**

- 两条 P1 的规格错误已修复。
- 主要功能页面已覆盖并接入。
- 仍有 4 个部分闭环项与 D-15 未闭环。
- Spec 轴最严重问题是 D-13 的目标选择/下载口径文本与实现未统一，且 D-15 未提供可核验验收证据。

## 七、Standards 轴结论

Standards 轴判定：**通过，另有 2 个判断型风险。**

- 本轮对架构红线的遵守基本稳定，未发现新的文档化硬违规。
- API 方法漂移测试已明显改进，`patch` 被引入并断言，之前 false green 的核心风险已消除。
- 判断风险一：任务文本文档直接写入“已完成/整改闭环”，但 D-13 下载正文和“零 DDL”声明未同步更新，容易让后续读者沿用旧契约。
- 判断风险二：前后端仍各自维护 selector 与规则不变量，虽然本次已明显收敛，仍未达到单一 schema 来源；未来新增规则类仍可能再次分叉。
- Standards 轴最严重判断项：任务文档与实现的下载通道/零 DDL 描述不一致。

## 八、环境与限制

- 未执行真实 Oracle/Redis/OSS 业务链路。
- 未执行真实 ai-service HTTP、完整双向转换和下载打开性检查。
- 未执行浏览器全链路，只做了 Vitest 组件/路由测试和生产构建。
- 本轮没有派发独立子代理；Spec / Standards 两轴由根 Agent 顺序完成，因此不能表述为独立双代理复审。

## 九、整改优先级

1. 统一 D-13 目标格式选择与下载通道正文，消除 fetch blob/presigned 冲突。
2. 补齐 D-11 的段落索引到块映射或页面退化路径。
3. 补齐 D-14 的语言字段/定位链路，或明确将语言范围移出本次切片验收。
4. 让 CODE 规则真正消费 `style+keepIndent`，避免“可保存但不执行”。
5. 完成 D-15 错题集、真实库迁移、真实转换和 P-07 统计。
6. 修正任务文档顶部“本切片零 DDL”等与当前实现不一致的陈述。

## 十、过程与结论

- 本轮实际读取了任务文档、上一轮报告、当前工作区、相关代码与测试，并重跑四项自动化与前端构建。
- 本轮未修改业务代码、测试断言、数据库脚本或 ai-service 代码，只新增本复审报告。
- 结论：**代码面复审基本通过；阶段交付仍不通过。**
- 最大剩余风险不在确定性逻辑，而在运行验收：D-15 没有真实库、真实转换和 P-07 证据，不能从自动化测试推导出可交付。
