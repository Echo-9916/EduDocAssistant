# 智能处理切片 C01-C14 整改复审报告

- 复审日期：2026-11-06
- 上一审查基线：`7a127ac`
- 当前复审目标：`34be39f`
- 整改提交：`5ea05e7`、`34be39f`
- 审查范围：C01-C14 当前代码与运行逻辑
- 特别口径：按用户决策，问答 5 秒目标不作为失败项；按用户要求不检查移动端
- 桌面验证环境：Google Chrome 1440×1000
- 总体结论：**主体整改通过；仍有 1 个 P1、1 个 P2、2 个 P3，暂不能整体关闭**

## 1. 总结

上一轮全面测试报告指出的 8 个问题已完成代码整改：

- 文档列表预览入口路由已修正。
- 同文重复段落的润色锚点已按行与块一一映射。
- `pnpm test` 的 `scrollTo` 未处理异常已消除，测试退出码恢复为 0。
- 预览接口增加 `vectorReady`，前端可在文本 READY、向量未 READY 时显示索引状态并禁用 AI 入口。
- 首次问答失败会使用本次新建的会话 ID 回载 FAILED 消息。
- 归档会话已转为只读，并提供新建会话入口。
- 摘要/要点创建错误已去重为单处 Alert。
- DOCX 导出会解析后端 `Content-Disposition` 文件名。

自动化验证现已全部通过：

- 后端：243/243
- ai-service：20/20
- 前端：25 文件 / 114 断言，退出码 0
- 前端构建：通过

但复审发现一个索引状态恢复缺口：

- 前端在 `vectorReady=false` 时正确禁用 AI 入口，但预览轮询条件仍只判断 `doc.status=PARSING`。当文档已 READY、向量仍在建立时，页面不会继续轮询，索引完成后仍保持禁用和“新版索引中”，必须手动刷新页面才能恢复。

因此当前状态为：**上一轮问题已整改通过，但索引就绪后的自动恢复逻辑仍需补一轮小修。**

## 2. 验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 后端测试 | `EduDocAssistant/backend` 下 `mvn test` | **243/243**，0 failure / 0 error |
| 后端真库 IT | Maven 日志 | 2 个 IT 仍为 `[IT-SKIP]`、`Tests run: 0` |
| ai-service 测试 | `uv run python -m pytest -q` | **20 passed**，1 个 Starlette/httpx 弃用告警 |
| 前端测试 | `EduDocAssistant/web` 下 `pnpm test` | **25 文件 / 114 断言通过，退出码 0** |
| 前端构建 | `pnpm build` | 通过 |
| 空白检查 | `git diff --check 7a127ac..HEAD` | 通过 |
| 真实桌面预览入口 | Chrome + 当前前端 | `/student/documents/{id}/view` 命中成功 |
| 真实 409 错误提示 | Chrome | 同一错误仅显示 1 次，无三重 Alert/toast |
| `vectorReady=false` 门控 | Playwright mock | 横幅显示，摘要与问答入口禁用 |
| 重复段落采纳 | Playwright mock | 第二行采纳提交 `charStart=4`，不再串到第一块 |
| 首次问答失败 | Playwright mock | 请求序列为 `POST → GET messages`，FAILED 消息可见 |
| 归档会话 | Playwright mock | 输入框禁用，出现“新建会话”按钮 |
| 索引就绪自动恢复 | Playwright mock | 5.5 秒内 preview 请求数保持 2→2，未继续轮询，入口持续禁用 |

## 3. 上一报告问题闭环

| 上一问题 | 复审状态 | 验证 |
|---|---|---|
| P1-1 预览入口错误路由 | 已关闭 | 真实 Chrome 点击预览命中 `/student/documents/{id}/view` |
| P1-2 重复段落使用错误 charStart | 已关闭 | 第二行 P2 采纳请求实际为 `charStart=4` |
| P1-3 `pnpm test` 退出码 1 | 已关闭 | 114/114，退出码 0 |
| P2-1 索引未就绪状态未呈现 | 部分关闭 | 已显示横幅并禁用入口；索引完成后没有自动恢复 |
| P2-2 首次问答失败不回载 | 已关闭 | POST 失败后实际执行 GET messages |
| P2-3 归档会话仍可提问 | 已关闭 | 输入框禁用并有新建会话入口 |
| P3-1 生成错误三重提示 | 已关闭 | 真实 409 页面仅显示一次错误 |
| P3-2 导出文件名 | 已关闭 | `parseFilename` RFC5987 测试与调用链已更新 |

## 4. 剩余问题

### P1 索引未就绪后不会自动恢复可用状态

- 现象：当文档状态为 READY、当前版本 `vectorReady=false` 时，页面正确显示“新版索引中”并禁用摘要/要点/问答。但索引随后完成后，前端没有再次请求预览，按钮仍保持禁用。
- 触发条件：
  - 润色采纳生成新版本后，文本解析同步完成使文档 READY；
  - 新版本向量仍在 EmbedWorker 异步处理中；
  - 用户保持页面不刷新。
- 影响：AI 入口会一直禁用并显示“新版索引中”，用户必须手动刷新页面才能使用；如果用户在后台完成索引后仍需重复刷新，C11/C12 的流程不闭环。
- 代码位置：`web/src/pages/DocumentView.tsx:44-49`
- 当前轮询条件：
  ```ts
  if (!documentId || doc?.status !== 'PARSING') return
  ```
- 应纳入条件：`doc?.status === 'PARSING' || preview?.vectorReady === false`
- 浏览器证据：mock 首次 `vectorReady=false`、后续返回 true；等待 5.5 秒后 preview 请求数仍为 2，摘要按钮保持禁用。
- 现有测试不足：`DocumentView.test.tsx` 只断言 `vectorReady=false` 时禁用，没有断言后续轮询自动恢复。
- 状态：unresolved。

### P2 生成任务创建失败后“生成”按钮消失，无法重试

- 现象：摘要创建请求返回 502 后，TaskHeader 只显示错误 Alert，原“生成摘要”按钮不再渲染。
- 触发条件：创建任务返回 50201、50001 或其他非活动态错误；用户没有 task 对象。
- 影响：用户无法在面板内重试，只能切出页面/刷新，不符合“失败可重试”的交互预期。
- 代码位置：`web/src/pages/view/PanelShared.tsx:103-105`
- 当前逻辑：
  ```ts
  if (error && !task) return <Alert type="error" ... />
  ```
- 建议：错误 Alert 与生成按钮同时保留；错误状态不应隐藏启动入口。
- 浏览器证据：mock 摘要创建 502 后，`gen-摘要` 元素数量为 0。
- 状态：unresolved。

### P3 FAILED 任务可能同时出现两条失败提示

- 现象：任务进入 FAILED 后，`track` 将 error 设为 errorCode；TaskHeader 同时渲染 `error && task` Alert 和 `task.status === 'FAILED'` Alert。
- 影响：同一失败可能显示两条提示，信息重复。
- 代码位置：`web/src/pages/view/PanelShared.tsx:126-130`
- 建议：FAILED 终态只使用结构化失败 Alert，不再使用通用 error Alert。
- 状态：unresolved。

### P3 所有 FAILED 问答消息都显示“超时”标签

- 现象：QaPanel 对 `status=FAILED` 固定显示 `<Tag color="error">超时</Tag>`。
- 影响：模型服务 502、配置错误或网络失败也会显示为“超时”，用户无法区分原因。
- 代码位置：`web/src/pages/view/QaPanel.tsx:117`
- 建议：使用“失败”通用标签；若需要细分，由后端消息补充 errorCode。
- 状态：unresolved。

## 5. C01-C14 复审矩阵

| 项目 | 结论 | 说明 |
|---|---|---|
| C01 分块 offset | 通过 | 后端测试通过 |
| C02 模型档案与客户端 | 通过 | 504/502/503 与权限测试通过 |
| C03 向量化流水线 | 通过 | 两阶段落库、失败不留半向量 |
| C04 任务编排与摘要 | 通过 | 任务、取消、reduce、统计与审计测试通过 |
| C05 要点提取 | 通过 | 排序、非法引用、underMin 与前端列表通过 |
| C06 问答 | 通过（5s 忽略） | 首次失败回载、归档只读已补齐；5s 按用户决策忽略 |
| C07 润色 | 通过 | 事务与 D3 撤销逻辑通过；前端重复段落锚点已修正 |
| C08 预览结构树 | 通过 | 真实预览、vectorReady 响应与测试通过 |
| C09 ai-service | 通过 | 六接口、schema 重试、结构化错误通过 |
| C10 预览页宿主 | 有条件通过 | 入口路由已修复；索引完成后的自动恢复仍缺失 |
| C11 摘要/要点面板 | 有条件通过 | 生成、轮询、恢复、定位、导出通过；创建失败后重试入口消失 |
| C12 问答面板 | 有条件通过 | 三态、引用、历史、失败回载、归档只读通过；失败标签语义偏窄 |
| C13 润色面板 | 通过 | 同文重复段锚点映射已修正并测试 |
| C14 联调与测试文档 | 有条件通过 | 自动化、构建、桌面主路径通过；索引恢复和任务重试仍需补 |

## 6. 桌面浏览器与截图

- 真实 Chrome 登录、身份确认、学生工作台可用。
- 从文档列表点击“预览”已正确进入 `/student/documents/{id}/view`。
- 直接预览页正文、页码和四个 Tabs 正常。
- 真实 409 错误只显示一次，不再出现三重重复。
- 索引未就绪时正确显示“新版索引中”，摘要和问答入口置灰。
- 按用户要求未检查移动端。

本次复审截图：

- [修复后的预览入口与单次错误提示](1106-ui-recheck-route-error.png)
- [索引未就绪门控](1106-ui-recheck-indexing.png)

上一轮成功态面板截图仍可作为交互证据：

- [四面板成功态](1106-ui-panel-e2e.png)

## 7. 建议修复顺序

1. 将 `vectorReady=false` 纳入预览轮询条件，并增加 false→true 自动恢复测试。
2. 任务创建失败时保留生成按钮，允许页面内重试。
3. 合并 FAILED 任务的双重错误提示。
4. 将 FAILED 问答标签改为“失败”，或补充错误类型。

## 8. 复审边界

- 本轮未修改业务代码或业务数据，只生成复审报告和两张新桌面截图。
- 未检查移动端。
- 未重新执行真实模型成功链路；5 秒目标按用户决策忽略。
- 两个真库 IT 因环境变量未注入仍为 `Tests run: 0`。
- 当前运行的 8080 后端进程启动于上一日，未自动重载本轮后端 `vectorReady` 变更；`vectorReady` 后端行为由单元测试覆盖，前端门控由浏览器 API mock 验证。
