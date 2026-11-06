# 智能处理切片 C01-C14 全面测试与审查报告

- 审查日期：2026-11-06
- 审查对象：`EduDocAssistant/docs/tasks/03-ai-processing.md`
- 审查范围：C01-C14，覆盖后端、ai-service、前端预览页与四类 AI 面板
- 特别口径：按用户 2026-11-06 决策，**问答 5 秒成功目标本次不作为失败项**
- 桌面端范围：Google Chrome 1440×1000；按用户要求不检查移动端
- 工作区状态：代码工作区干净，未修改业务代码
- 总体结论：**功能主体完整，但本轮不能判定通过；存在 3 个 P1、3 个 P2、2 个 P3 问题**

## 1. 总结

C01-C09 后端与 ai-service 主链路已完成，上一轮复审的 P2/P3 整改也已关闭。C10-C13 前端代码已经实现，摘要、要点、问答、润色四类面板具备主要交互，预览页可直接打开并通过 API 加载。

但前端存在会阻断真实使用的功能缺陷：

- 文档列表点击“预览”生成错误相对路由，页面回落到 `/roles`，从主界面无法进入 C10 预览页。
- 两段相同文本同时润色时，第二段采纳仍提交第一段的 `charStart`，可能修改错误段落。
- `pnpm test` 的 107 个断言虽然通过，但进程因 `QaPanel` 的 `scrollTo` 未处理 jsdom 兼容而退出码 1，C14 的“前端全绿”基线不成立。

此外还有索引重建状态未呈现、首次问答超时不回载 FAILED 消息、归档会话仍可提问等问题。

## 2. 验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 后端测试 | `EduDocAssistant/backend` 下 `mvn test` | **242 通过 / 0 failure / 0 error / 0 skipped** |
| 后端真库 IT | Maven 日志 | 2 个真库 IT 仍为 `Tests run: 0`、`[IT-SKIP]` |
| ai-service 测试 | `uv run python -m pytest -q` | **20 passed**，1 个 Starlette/httpx 弃用告警 |
| 前端测试 | `EduDocAssistant/web` 下 `pnpm test` | 24 文件 / 107 断言执行通过，但 **1 个未处理异常，退出码 1** |
| 前端构建 | `pnpm build` | 通过；`tsc --noEmit` + Vite build 成功 |
| 空白检查 | `git diff --check` | 通过 |
| 真服务健康 | `GET /actuator/health`、`GET /health` | 后端 UP，ai-service UP |
| Redis 状态 | `XLEN` / `XPENDING` | 两个 AI Stream 均 pending=0 |
| Oracle 状态 | AI 任务、QA 消息聚合 | 无 RUNNING；历史任务含成功和失败记录 |
| 桌面浏览器登录 | Playwright + 本机 Chrome | 登录、身份确认、进入学生工作台成功 |
| 文档列表 | 浏览器真实 API | 1 篇 READY 文档可见，列表/筛选控件正常 |
| 预览入口 | 点击列表“预览” | URL 错误跳转并回落 `/roles` |
| 直接预览路由 | 手动打开正确 URL | 预览页正常渲染块与四面板 |
| 面板成功态 | Playwright API mock | 摘要定位高亮、要点 underMin、问答引用、润色采纳均能交互 |
| 首次问答失败 | Playwright mock 504 | 只发出 POST，没有回载服务端 FAILED 消息 |
| 重复段落采纳 | Playwright mock 两块同文 | 第二行采纳请求错误携带 `charStart=0`，应为第二块的偏移 |
| 归档会话 | Playwright mock A/C 会话 | 选择归档会话后提问框仍可输入/提交 |

说明：5 秒时延按用户决策忽略；真实模型上游时延、P-05 不作为本轮结论依据。

## 3. P1 级问题

### P1-1 C10 文档列表“预览”入口路由错误

- 现象：点击文档列表中的“预览”后，实际导航到：
  `.../student/documents/documents/{documentId}/view`
  该 URL 不匹配路由，被通配路由重定向到 `/`，随后回落到 `/roles`。
- 影响：用户无法从“我的文档”主入口进入 C10 预览页。直接手动输入正确 URL 时页面可用，说明是导航路径错误，不是页面或权限问题。
- 代码位置：`web/src/pages/Documents.tsx:237`
- 当前代码：`navigate(\`documents/${row.id}/view\`)`
- 正确方向：使用当前路由相对路径 `navigate(\`${row.id}/view\`)`，或按角色生成绝对路径。
- 浏览器证据：导航序列记录到错误 URL 后跳到 `/` 和 `/roles`。
- 现有测试不足：`Documents.test.tsx:73-77` 只断言 `navigate` 收到相对字符串，没有放进真实 `MemoryRouter` 验证最终 URL。
- 状态：unresolved。

### P1-2 C13 重复段落可能采纳到错误位置

- 现象：同时选择两个文本完全相同的块进行润色后，对第二行执行采纳，请求仍携带第一块的 `charStart=0`。
- 触发条件：`selectedBlocks` 中存在两个及以上相同 `text`，且分别生成不同 `polishId`。
- 影响：后端按错误偏移校验和替换，可能把用户选择的第二段采纳到第一段，造成文档内容错误。
- 代码位置：`web/src/pages/view/PolishPanel.tsx:62-68`
- 当前代码：`selectedBlocks.find((b) => b.text.trim() === row.sourceText)`
- 根因：用文本反查块，忽略行与所选块的一一顺序；重复文本必然命中第一块。
- 浏览器证据：模拟 C1/C2 同文，点击第二行 `P2` 采纳，请求 body 实际为 `{"decision":"ACCEPT","charStart":0}`。
- 修复方向：保持 `rows` 与 `selectedBlocks` 的索引映射，按 `rows.indexOf(row)` 取对应块；或让后端返回 `sourceChunkId/charStart`。
- 现有测试不足：当前测试没有覆盖两个相同文本块。
- 状态：unresolved。

### P1-3 前端测试门禁实际失败

- 现象：`pnpm test` 的测试文件 24/24、断言 107/107 均显示通过，但 Vitest 捕获未处理异常并以退出码 1 结束。
- 异常：`TypeError: listRef.current?.scrollTo is not a function`
- 位置：`web/src/pages/view/QaPanel.tsx:54-56`
- 触发：问答发送完成后调用 `requestAnimationFrame` 滚动消息列表；jsdom 的元素没有实现 `scrollTo`。
- 影响：与 `docs/tasks/03-ai-processing.md` 的 C14 “`pnpm test` 全绿”口径直接冲突；文档中的 `web vitest 107 全绿` 不准确。
- 修复方向：改为 `listRef.current?.scrollTo?.(...)`，或在测试 setup 中补齐 `Element.prototype.scrollTo`。
- 状态：unresolved。

## 4. P2 级问题

### P2-1 READY 文档的“索引重建中”状态未在前端呈现

- 现象：当前真实文档状态为 READY，但当前版本的 chunk 仍为 PENDING。页面显示 READY 和可操作的摘要/问答/润色入口；点击摘要后返回 409 “文档尚未就绪（向量索引未完成）”。
- 触发条件：采纳润色后或重解析后，文档状态已 READY，但向量化仍在异步执行。
- 影响：违反风险表 R3 的“新版索引中”提示要求，用户看到可用入口却立即失败。
- 证据：
  - 浏览器真实页面显示 `v1 · READY` 和“生成摘要”；
  - 点击生成后页面出现多个“文档尚未就绪（向量索引未完成）”错误；
  - 代码只根据 `doc.status === 'PARSING'` 显示索引提示：`web/src/pages/DocumentView.tsx:44-49`、`134-143`。
- 根因：文档详情/预览没有暴露 chunk/vector 的 READY 状态，前端无法区分“文本 READY”和“索引 READY”。
- 建议：在文档详情或预览响应中增加 `vectorReady/indexStatus`，或提供轻量 readiness 字段，据此显示“新版索引中”并禁用 AI 入口。
- 状态：unresolved。

### P2-2 首次提问失败后不会回载 FAILED 消息

- 现象：没有活跃会话时发送第一个问题，服务端已经落库 USER/ASSISTANT FAILED 双消息，但前端 catch 分支使用旧的 `sessionId`（仍为 null），没有重新加载消息。
- 触发条件：首次提问时网络错误、504 或模型失败。
- 影响：用户只看到通用 toast，看不到服务端已持久化的失败气泡，也无法从历史消息确认失败状态。
- 代码位置：`web/src/pages/view/QaPanel.tsx:63-79`
- 错误分支：`if (sessionId) loadMessages(sessionId)`，应使用本次新建的 `sid`。
- 浏览器证据：首次会话 POST 返回 504 后，请求记录只有 `POST`，没有 `GET messages`。
- 状态：unresolved。

### P2-3 归档会话仍可继续提问

- 现象：历史会话选择器包含状态 `C` 的归档会话，选中后输入框和“提问”按钮仍可用。
- 触发条件：用户选择已归档会话后继续提问。
- 影响：请求必然被后端按“会话已归档”返回 409；历史回看与继续对话没有明确边界。
- 代码位置：
  - `web/src/pages/view/QaPanel.tsx:88-103` 允许选择任意状态会话；
  - `web/src/pages/view/QaPanel.tsx:137-155` 未根据会话状态禁用输入。
- 建议：归档会话只读；若要继续提问，自动创建新会话或显式“新建会话”。
- 浏览器证据：选择“已归档”会话后，输入框 `disabled=false`。
- 状态：unresolved。

## 5. P3 级问题

### P3-1 摘要/要点创建失败时错误提示重复

- 现象：文档向量未就绪时点击“生成摘要”，页面正文中同一错误出现两次，另有 toast，共出现三次相同文案。
- 原因：`TaskHeader` 在 `error && !task` 时渲染错误 Alert，`SummaryPanel/KeypointPanel` 外层又渲染一次 `ctrl.error` Alert，同时 `start()` 还触发 toast。
- 位置：
  - `web/src/pages/view/PanelShared.tsx:103-105`
  - `web/src/pages/view/SummaryPanel.tsx:72-73`
  - `web/src/pages/view/KeypointPanel.tsx:67-68`
- 影响：错误反馈重复，页面噪声明显。
- 状态：unresolved。

### P3-2 导出文件名没有使用后端 Content-Disposition

- 现象：后端返回的 DOCX `Content-Disposition` 包含文档标题，但前端 `fetchExportBlob` 丢弃响应 headers，调用方固定使用 `摘要.docx`。
- 位置：
  - `web/src/api/ai.ts:73-82`
  - `web/src/pages/view/SummaryPanel.tsx:55-65`
- 影响：导出文件名与文档标题不一致，偏离后端导出契约。
- 建议：解析 `Content-Disposition` 的 `filename*`/`filename`，解析失败时再使用固定文件名。
- 状态：unresolved。

## 6. C01-C14 验收矩阵

| 项目 | 结论 | 依据 |
|---|---|---|
| C01 分块 offset | 代码通过 | 后端单测通过；当前真实存量样本仍是修复前数据，不作为本轮代码失败 |
| C02 模型档案与服务客户端 | 通过 | 504/502/503、权限、超时测试通过 |
| C03 向量化流水线 | 通过 | 两阶段落库、失败零半向量、Stream 观测正常 |
| C04 任务编排与摘要 | 基本通过 | 后端任务、取消、reduce、统计测试通过 |
| C05 要点提取 | 基本通过 | 后端排序/非法引用/underMin 与前端渲染均通过 |
| C06 问答 | 功能通过，界面有缺陷 | 首次失败消息回载与归档会话控制不合格；P-05 按用户决策忽略 |
| C07 润色 | 后端通过，前端阻断 | 后端重复段落/事务整改已关闭；前端仍发送错误 charStart |
| C08 预览结构树 | 通过 | 真实接口返回块序与文本正常 |
| C09 ai-service | 通过 | 六接口、schema 重试、结构化错误测试通过 |
| C10 预览页宿主 | 不通过 | 主列表入口路由错误；直接 URL 可用 |
| C11 摘要/要点面板 | 部分通过 | 轮询、恢复、分组、定位、导出测试通过；索引状态与重复错误提示有缺陷 |
| C12 问答面板 | 部分通过 | 三态渲染、引用、历史加载可用；首次失败回载与归档只读有缺陷 |
| C13 润色面板 | 不通过 | 重复文本会提交错误偏移，存在改错段落风险 |
| C14 联调与测试文档 | 不通过 | `pnpm test` 退出码 1；桌面主路径仍有阻断问题；真实模型 5s 已按用户决策忽略 |

## 7. 桌面浏览器验证结果

- 登录页正常，登录后进入身份确认页，学生工作台可进入。
- “我的文档”列表正常展示 READY 文档及筛选控件。
- 直接访问正确预览 URL 时，正文块、页码、四个 Tabs、润色块选择均渲染正常。
- 摘要任务恢复成功态、引用高亮、要点 underMin、问答引用点击、润色左右对照和采纳提示均能工作。
- 摘要 → 要点 → 问答 → 润色四个面板切换正常，未出现文字重叠或遮挡。
- 按用户要求没有执行移动端检查。

截图证据：

- [登录页](1106-ui-login.png)
- [文档列表](1106-ui-documents.png)
- [直接预览页](1106-ui-document-view.png)
- [四面板成功态](1106-ui-panel-e2e.png)
- [预览入口错误跳转](1106-ui-preview-navigation.png)
- [首次问答失败态](1106-ui-qa-timeout.png)

## 8. 建议修复顺序

1. 修复 `Documents.tsx` 预览相对路由，并补真实 Router 集成测试。
2. 修复 `PolishPanel` 的 selectedBlocks 与 polish rows 索引映射，补重复文本测试。
3. 修复 `QaPanel.scrollTo` 兼容，让 `pnpm test` 退出码为 0。
4. 在文档详情/预览中暴露索引状态，按 R3 显示“新版索引中”并禁用 AI 入口。
5. 首次问答失败时使用本次 `sid` 回载 FAILED 消息。
6. 归档会话只读，禁止继续发送。
7. 去重 Summary/Keypoint 错误 Alert。
8. 解析并复用后端导出的 Content-Disposition 文件名。

## 9. 复审边界

- 本轮未修改业务代码和业务数据，只生成报告与桌面浏览器截图。
- 未检查移动端。
- 未重新跑真实模型成功链路；真实模型结果引用 `docs/test/AiTest.md`，且 5 秒目标按用户决策排除。
- 两个真库 IT 仍因环境变量未注入而跳过，不能把后端 242 个测试全部视为收益于真库覆盖。
- 桌面浏览器测试使用真实登录、真实文档列表和真实预览接口；AI 任务成功态使用浏览器路由拦截 mock，以验证前端交互而不消耗模型额度或改写测试数据。
