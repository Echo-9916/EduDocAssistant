# 智能处理切片 C01-C14 进一步复查报告

- 复查日期：2026-11-06
- 上一复审基线：`34be39f`
- 当前复查目标：`86c5b6e`
- 整改提交：`86c5b6e`（4 项前端整改）
- 审查范围：C01-C14 当前代码、自动化测试、构建与桌面浏览器实际行为
- 特别口径：按用户决策，问答 5 秒成功目标暂不作为失败项；按用户要求不检查移动端
- 桌面验证环境：Google Chrome，视口 1440×1000
- 总体结论：**上一轮 4 项全部关闭，无新增 P1/P2；C01-C14 主体功能判定通过。新增 2 个非阻断 P3，C14 全量真实样例链路仍保留证据边界**

## 1. 复查结论

`86c5b6e` 已完成上一轮复审剩余的 4 项整改：

1. `vectorReady=false` 时持续轮询，索引完成后自动解禁 AI 入口。
2. 摘要/要点任务创建失败后保留可点击的生成按钮。
3. FAILED 任务终态只显示一条结构化失败提示。
4. FAILED 问答消息标签由“超时”改为“失败”。

独立重跑结果：

- 后端测试：**243/243 通过**
- ai-service 测试：**20/20 通过**
- 前端测试：**25 个文件 / 117 个断言通过，退出码 0**
- 前端构建：`tsc --noEmit` 与 `vite build` 通过
- 真实 Chrome 登录、角色确认、文档列表、预览入口通过
- 真实模型摘要任务从 `QUEUED` 运行至 `SUCCEEDED`
- 浏览器 mock 已验证四个整改点的实际 DOM 和时序行为

当前没有发现会阻断 C01-C14 主流程的 P1/P2。发现 2 个 P3：一是已有 FAILED 任务时再次发起生成且创建失败，页面继续显示旧错误码，新创建错误不可见；二是任务文档与 `AiTest.md` 的前端测试基线仍写 114，而最新实际为 117。两项均不影响运行，但分别影响错误反馈准确性和验收记录一致性。

## 2. 验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 后端测试 | `EduDocAssistant/backend` 下 `mvn test` | **243 passed / 0 failure / 0 error** |
| 后端真库 IT | Maven 日志 | 2 个 IT 为 `[IT-SKIP]`、`Tests run: 0` |
| ai-service 测试 | `EduDocAssistant/ai-service` 下 `uv run python -m pytest -q` | **20 passed**，1 个 Starlette/httpx 弃用告警 |
| 前端测试 | `EduDocAssistant/web` 下 `pnpm test` | **25 files / 117 tests passed**，退出码 0 |
| 前端构建 | `pnpm build` | 通过 |
| 差异空白检查 | `git diff --check 34be39f...86c5b6e`、`git diff --check` | 通过 |
| 真实桌面主路径 | Chrome + 实际后端 | 登录、角色确认、文档列表、预览路由通过 |
| 真实模型摘要 | Chrome 实际发起并查询任务 | HTTP 202；最终 `SUCCEEDED`，progress 100，1 条合法引用 |
| 浏览器异常态 | Playwright API mock | 四个整改点均通过 |

说明：真实摘要任务使用 `deepseek-ai/DeepSeek-V3`，结果长度 32 字符，`promptVersion=v1`，引用数 1。该验证新增 1 条摘要任务及其结果数据，未修改文档、版本或业务代码。

## 3. 上一轮 4 项闭环

| 上一轮问题 | 最新状态 | 代码依据 | 验证证据 |
|---|---|---|---|
| P1 索引未就绪后不会自动恢复 | 已关闭 | `web/src/pages/DocumentView.tsx:44-51` | preview 首次 false、第二次 true；第二次请求距首次 2031ms；按钮由 disabled 变为 enabled；索引横幅由 2 处降为 0 |
| P2 创建失败后生成按钮消失 | 已关闭 | `web/src/pages/view/PanelShared.tsx:111-115` | 创建返回 502 后错误 Alert 数 1，生成按钮数 1，`disabled=false`，可再次点击 |
| P3 FAILED 任务双 Alert | 已关闭 | `web/src/pages/view/PanelShared.tsx:129-134` | 恢复 FAILED 任务后 Alert 总数 1，文案包含 `50401` |
| P3 FAILED 问答固定显示“超时” | 已关闭 | `web/src/pages/view/QaPanel.tsx:139-143` | FAILED 消息显示“失败”1 处，“超时”0 处 |

### 3.1 P1 索引自动恢复

`DocumentView` 的轮询条件已从仅判断 `PARSING` 扩展为：

```ts
if (doc?.status !== 'PARSING' && preview?.vectorReady !== false) return
```

新增测试位于 `web/src/pages/DocumentView.test.tsx:108-119`，覆盖 `vectorReady=false → true`。

浏览器复验：

- 首次 preview 返回 `vectorReady=false`，摘要按钮禁用。
- 2 秒后第二次 preview 返回 `vectorReady=true`。
- 页面停止显示“新版索引中”，摘要按钮自动启用。
- preview 实际调用 3 次，间隔记录为 `1ms`、`2031ms`；第一个间隔来自首次渲染与依赖更新，第二次为真正的 2 秒轮询。

### 3.2 P2 创建失败保留重试入口

`TaskHeader` 不再在 `error && !task` 时提前返回 Alert，生成按钮与创建错误可以同时存在。

新增测试 `web/src/pages/view/SummaryPanel.test.tsx:81-89` 断言失败后按钮仍存在且可点击。浏览器验证同时确认页面只出现 1 条错误 Alert。

### 3.3 FAILED 任务单提示

FAILED 终态下通用 error Alert 被抑制，只保留结构化失败 Alert；`errorCode` 缺失时使用 `error` 或“未知错误”回退。

新增测试 `web/src/pages/view/SummaryPanel.test.tsx:91-102` 断言 Alert 总数为 1 且包含错误码。

### 3.4 问答失败标签

`QaPanel` 对 FAILED 消息使用通用“失败”标签，不再把模型失败、网络失败误报为“超时”。

新增测试 `web/src/pages/view/QaPanel.test.tsx:42-50` 同时断言“失败”存在、“超时”不存在。

## 4. 新增问题

### P3 已有 FAILED 任务时，新的创建失败被旧错误码遮蔽

- 现象：页面从 `localStorage` 恢复一个 FAILED 任务后，用户再次点击生成；若本次创建请求返回 502，页面仍只显示旧任务的失败码，新的创建失败没有可见反馈。
- 触发条件：
  - 当前面板已有 `task.status=FAILED`；
  - 用户再次发起生成；
  - 新任务创建请求失败，未产生新的 task 对象。
- 影响：用户可以再次点击按钮，但无法判断本次重试是否失败；错误码和提示内容可能停留在旧状态。
- 代码位置：`EduDocAssistant/web/src/pages/view/PanelShared.tsx:130-133`
- 当前渲染条件：
  ```tsx
  {error && task?.status !== 'FAILED' && <Alert ... message={error} />}
  {task?.status === 'FAILED' && (
    <Alert ... message={`处理失败（${task.errorCode ?? error ?? '未知错误'}）...`} />
  )}
  ```
- 根因：旧 `task.errorCode` 优先于本次 `start()` 写入的新 `error`；同时通用错误 Alert 被 FAILED 条件抑制。
- 浏览器复现：
  - 初始恢复任务：`FAILED / 50401`
  - 再次创建：HTTP 502 / `50201 / traceId=ui-new-fail`
  - 操作前 Alert：`处理失败（50401），可重试或稍后再试`
  - 操作后 Alert：文案不变
  - 新错误 `ui-new-fail` 在页面中不可见
- 影响范围：摘要与要点共用 `TaskHeader`，两处均可能触发。
- 建议：将“当前任务状态错误”和“本次启动错误”分开建模；本次 `error` 非空时优先显示本次错误，或在重新发起时清除旧任务展示。
- 状态：`unresolved`，P3，不阻断主流程。

### P3 测试基线文档仍记录 114 个前端断言

- 现象：最新 `pnpm test` 为 25 个文件、117 个断言，但任务文档和 `AiTest.md` 回归基线仍记录 114。
- 代码位置：
  - `EduDocAssistant/docs/tasks/03-ai-processing.md:150`
  - `EduDocAssistant/docs/test/AiTest.md:5`
- 影响：验收基线滞后，后续审查容易误判测试数量或漏掉新增用例；不阻断构建和运行。
- 建议：同步改为 `25 files / 117 tests`，并在每次新增回归用例时更新基线。
- 状态：`unresolved`，P3。

## 5. 残余风险

### 向量索引失败状态与“索引中”不可区分

- `DocumentPreviewServiceImpl` 仅返回布尔值 `vectorReady`：所有分块 `READY` 才为 true，其余情况均为 false，见 `backend/src/main/java/com/cidas/edudoc/document/application/impl/DocumentPreviewServiceImpl.java:43-51`。
- `EmbedWorker` 失败时把 EMBED 任务置为 FAILED，但分块保持 PENDING，见 `backend/src/main/java/com/cidas/edudoc/ai/application/EmbedWorker.java:180-188`。
- 因此当前前端无法区分“向量仍在处理”和“向量处理已经失败”，页面会持续显示“新版索引中”，并在 2 秒周期内轮询。
- C03 已明确失败任务落 FAILED、不落半向量；R3 只要求未就绪期间提示“新版索引中”，未明确要求前端区分 EMBED 失败态。因此本轮将其列为残余风险，不升级为不符合项。
- 建议后续在预览或文档详情中增加 `vectorState=PENDING|READY|FAILED`，或在页面提供进入重解析/重试的入口。

## 6. C01-C14 最新矩阵

| 项目 | 结论 | 依据 |
|---|---|---|
| C01 分块 offset | 通过 | `ChunkingServiceTest`、`TikaTextExtractorTest` 随后端 243 项通过 |
| C02 模型档案与客户端 | 通过 | 模型 seed、超时/错误码、token 与 trace 测试通过 |
| C03 向量化流水线 | 通过 | 两阶段落库、失败零半向量、幂等测试通过；EMBED 失败 UI 可辨识性列为残余风险 |
| C04 任务编排与摘要 | 通过 | 状态机、取消、分组、引用与真实摘要 `SUCCEEDED` 验证通过 |
| C05 要点提取 | 通过 | 排序、非法引用丢弃、`underMin` 与前端渲染测试通过 |
| C06 问答 | 通过（5 秒目标忽略） | 检索、拒答、引用、失败消息回载、归档只读测试通过 |
| C07 润色 | 通过 | 重复段落锚点、成版事务、撤销与重润逻辑通过 |
| C08 预览结构树 | 通过 | `vectorReady`、块序、偏移与真实预览页验证通过 |
| C09 ai-service | 通过 | 六接口、结构化错误、坏 JSON 重试与 token 校验测试通过 |
| C10 预览页宿主 | 通过 | 列表预览入口、正文渲染、索引自动恢复均通过 |
| C11 摘要/要点面板 | 通过（1 个 P3） | 创建、轮询、恢复、定位、导出与失败重试通过；组合失败反馈仍有 P3 |
| C12 问答面板 | 通过 | 三态消息、引用回跳、历史、失败标签、归档只读通过 |
| C13 润色面板 | 通过 | 选择限制、重复文本锚点、采纳/撤销链通过 |
| C14 联调与测试文档 | 有条件通过 | 三层自动化、构建、真实登录/预览/摘要通过；测试基线文档仍为 114；本机 2 个真库 IT 仍跳过，完整 30 页真实模型全链路本轮未重跑 |

## 7. 验证边界

- 按用户要求未检查移动端。
- 按用户决策忽略问答 5 秒成功目标；问答超时/失败契约仍由自动化测试覆盖。
- 两个真库 IT 因 `EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD` 未注入仍为 `[IT-SKIP]`、`Tests run: 0`，不能计为通过。
- 本轮未重跑 30 页 PDF 上传、解析、全量向量化、要点、问答、润色、撤销、重解析的完整真实模型链路。
- 本轮重验了真实登录、文档列表预览、预览页加载与一次真实摘要生成，摘要任务成功且含 1 条合法引用。
- 浏览器异常态使用 API mock，验证 React 实际交互与 DOM 状态，不消耗模型额度。
- 本轮未修改业务代码、测试断言、文档或版本数据。

## 8. 提交建议

当前可以进入提交或合并阶段。建议后续单独处理 P3：

1. 在复用 FAILED 任务的面板中为“本次启动错误”建立独立显示优先级。
2. 补一个“已恢复 FAILED 任务后再次创建失败”的组件测试。
3. 将 `03-ai-processing.md` 与 `AiTest.md` 的前端测试基线同步为 117。
4. 可选：在预览契约中增加 `vectorState`，区分索引 PENDING 与 FAILED。
