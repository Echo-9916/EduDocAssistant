# 智能处理切片 C01-C09 整改复审报告

- 复审日期：2026-11-06
- 复审基线：`52a74eb`（上一版 C01-C09 审查报告提交）
- 复审目标：`d98b0a7`（最新整改提交）
- 复审范围：`52a74eb..d98b0a7` 的 9 个提交，共 39 个文件变更
- 复审方式：提交差异审查 + 完整测试复跑 + 当前服务/Redis/Oracle 只读核对 + 最小边界探针
- 工作区状态：干净，`git diff --check 52a74eb..HEAD` 通过
- 复审结论：**本轮整改实质有效，但仍为部分通过；P-05 不能关闭，另有数个新 P2/P3 风险需处理**

## 1. 结论摘要

上一轮审查提出的主要问题中，以下整改已经完成：

- C03 向量化改为两阶段，后批次维度失败不会落前批次半向量。
- C07 成版、撤销与对应审计已进入同一事务。
- ai-service 504/TIMEOUT 已正确映射为后端 50401。
- QA 与任务编排已补 `DOCUMENT_READ + AI_*` 权限组合检查。
- ai-service 已增加模型输出结构校验，合法 JSON 坏结构转 502 `MODEL_FAILED`。
- C07 重复段落已支持 `charStart` 消歧；未带锚点且重复时返回 40901。
- C04 已增加真正的摘要 reduce 步骤。
- C05 导出标题已按结果类型区分。
- C06 已增加 QA 端到端 deadline，并将剩余预算传入 embed/rerank/answer。
- 活跃任务创建已增加确定性锁键，顺序并发可收敛到同一任务。
- C03 STALE、C09 取消/幂等口径已在任务文档中完成解释性裁决。

但以下问题仍未关闭或为新引入风险：

- `P1` C06 的 P-05 默认 5 秒目标仍未通过；项目记录仍为 24/25，9 秒预算才得到 READY。
- `P2` 润色请求传负 `charStart` 会抛 `StringIndexOutOfBoundsException`，最终变成 50001，而非可解释的 40001/40901。
- `P2` ACTIVE 锁键在任务终态事务之外释放，进程崩溃或释放失败后可能永久复用已完成旧任务。
- `P2` QA 动态超时没有约束连接超时，且按原始毫秒值缓存 RestClient，实例数量可能持续膨胀。
- `P2` embed 阶段超时时没有写入 FAILED 双消息，与任务文档“段超时 → FAILED 消息”不一致。
- `P2` 摘要 reduce 调用没有二次预算检查，且减少后的 token、模型和引用覆盖不完整。
- `P3` 模型输出结构非法时只转结构化 502，没有按“解析失败重试一次”的完整语义重试。
- `P3` ai-service README 未补充 `EDUDOC_ANSWER_TIMEOUT_S`，`QaProperties` 注释仍写默认阈值 0.35。

## 2. 验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 提交范围 | `git log 52a74eb..HEAD` | 9 个整改/复测提交 |
| 文件变更 | `git diff --stat 52a74eb..HEAD` | 39 files，+1043 / -188 |
| 空白检查 | `git diff --check 52a74eb..HEAD` | 通过，无输出 |
| 后端单测 | `EduDocAssistant/backend` 下 `mvn test` | 239 通过 / 0 failure / 0 error / 0 skipped |
| 后端真库 IT | Maven 运行日志 | `RbacLifecycleIntegrationTest`、`ChunkEmbeddingVectorIntegrationTest` 仍为 `Tests run: 0`，输出 `[IT-SKIP]` |
| ai-service 测试 | `uv run python -m pytest -q` | 19 passed，1 个 Starlette/httpx 弃用告警 |
| 后端健康 | `GET /actuator/health` | UP |
| ai-service 健康 | `GET /health` | UP |
| Redis embed Stream | `XLEN` / `XPENDING` | XLEN=23，pending=0 |
| Redis AI task Stream | `XLEN` / `XPENDING` | XLEN=24，pending=0 |
| Oracle AI 任务 | 聚合查询 | 无 RUNNING；SUMMARY/KEYPOINT/POLISH/EMBED 均有成功记录 |
| Oracle QA 消息 | 聚合查询 | READY=43、REFUSED=27、FAILED=14 |
| 当前 READY 文档 | 登录后调用预览接口 | 仍为 2026-11-03 修复前数据；首块偏移断言仍失败 |
| 负偏移探针 | 反射调用 `PolishServiceImpl.resolveOccurrence(..., -1)` | 实际抛出 `StringIndexOutOfBoundsException: Index -1 out of bounds` |
| 真实模型 e2e | `docs/test/AiTest.md` 记录 | 默认 5s 为 24/25；放宽到 9s 后 QA READY+引用通过 |

当前运行中的后端和 ai-service 进程启动时间均晚于对应源码修改时间，按时间顺序可认为加载了本轮代码。但本轮没有自行执行会消耗模型额度或改写业务数据的完整 e2e；真实模型结论引用项目 2026-11-05 的复测记录。

## 3. 原审查问题闭环情况

| 原问题 | 复审状态 | 说明 |
|---|---|---|
| P1-1 C03 多批次半向量 | 已关闭 | 先完成全部批次 embed + 维度校验，再进入落库；新增后批失败零落库测试 |
| P1-2 C07 成版与审计分离 | 已关闭 | 对照行、成版、撤销与审计均进入 `PolishTxWriter` 事务 |
| P1-3 C06 P-05 未达标、无总预算 | 部分关闭 | deadline 与分段预算已实现；默认 5s 成功目标仍未通过 |
| P2-1 ai-service 504 映射 50201 | 已关闭 | 504 或 code=TIMEOUT 均映射为 50401，并有单测 |
| P2-2 C09 坏结构返回裸 500 | 已关闭 | 增加 Pydantic 输出校验，坏结构转结构化 502 |
| P2-3 C04/C06 权限组合 | 已关闭 | 服务层增加 `DOCUMENT_READ + AI_*` 组合校验及测试 |
| P2-4 C07 重复段落误替换 | 部分关闭 | 增加锚点与重复拒绝；负 `charStart` 产生新 500 风险 |
| P2-5 C04 活跃去重竞态 | 部分关闭 | 确定性 ACTIVE 键解决并发插入；锁键释放不具原子性 |
| P3-1 C03 STALE 语义 | 已关闭（口径） | 任务文档明确重解析为整版换块，物理删除旧向量/引用后重建 |
| P3-2 C04 未实现 reduce | 部分关闭 | 已调用真 reduce；预算、token 和引用覆盖仍不完整 |
| P3-3 C05 导出标题固定摘要 | 已关闭 | DOCX 标题按 SUMMARY/KEYPOINT/ANSWER/POLISH 区分 |
| P3-4 C09 取消/幂等键 | 已关闭（口径） | 文档明确 ai-service 无状态、任务级幂等与取消由后端承载 |

## 4. 新发现与剩余问题

### P1-1 C06 P-05 默认 5 秒仍未通过

- 现象：`docs/test/AiTest.md` 仍记录默认 5 秒预算下真机冒烟 24/25；只有把预算放宽到 9 秒后，QA READY+引用才通过。
- 触发条件：共享模型推理偶发排队、answer 调用接近或超过 4.8 秒。
- 影响：C06 的 P-05 是业务验收指标，当前实现能按 5 秒失败并落 FAILED 消息，但不能稳定在 5 秒内返回有效答案。
- 证据：
  - `docs/test/AiTest.md:5`
  - `docs/test/AiTest.md:199`
  - `docs/tasks/03-ai-processing.md:4`
- 根因：当前完成的是“超时契约与总预算”整改，不是“5 秒内成功”的性能整改；更高预算下通过说明主要限制来自上游模型时延。
- 建议：P-05 保留为未关闭项。C-14 需要使用真实课件复测；若仍不满足，应更换更快模型、调整推理并发或重新确认性能验收口径。
- 状态：unresolved。

### P2-1 负 `charStart` 会触发 50001

- 现象：`resolveOccurrence` 在检查 `charStart >= 0` 之前，先用负值调用 `docText.charAt(anchor)`。
- 触发条件：请求体携带 `charStart: -1` 或更小值。
- 影响：用户输入错误触发未捕获异常，最终返回 HTTP 500/50001，而不是 40001 或 40901。
- 证据：
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/PolishServiceImpl.java:184-197`
  - 最小反射探针实际得到 `StringIndexOutOfBoundsException: Index -1 out of bounds for length 12`
- 修复建议：先校验 `charStart < 0` 并抛 40001，再做空白跳过与字符串访问；补负值测试。
- 状态：unresolved。

### P2-2 ACTIVE 锁键释放不在终态事务内

- 现象：任务成功/失败状态在 result transaction 或 `markFailed` 中写入，`releaseActiveKey` 在 worker 的 `finally` 或 cancel 方法中另行执行。
- 触发条件：进程在状态提交后、释放锁键前崩溃，或释放 SQL 执行失败。
- 影响：
  - 已完成任务仍保留 `ACTIVE:` 唯一键。
  - 后续创建请求的 `findActive` 查不到 SUCCEEDED/FAILED，但插入同键会命中旧行，`insertOrReuse` 返回旧任务 ID。
  - 用户点击“重新生成”可能一直得到旧结果，且不会创建新任务或发新事件。
- 证据：
  - `backend/src/main/java/com/cidas/edudoc/ai/application/AiTaskWorker.java:185-192`
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/AiTaskOrchestratorImpl.java:89-98`
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/AiResultTxServiceImpl.java:21-30`
- 修复建议：把 ACTIVE 键释放放入 markSucceeded/markFailed/cancel 的同一事务；或增加恢复扫描清理非活跃任务的 ACTIVE 键。
- 状态：unresolved。

### P2-3 QA 动态超时客户端不完全受 5 秒预算约束，且缓存无上限

- 现象：
  - 动态 RestClient 的 connect timeout 固定为 3 秒，不随剩余预算缩小。
  - `timedClients` 以原始 `timeoutMs` 为 key 永久缓存，剩余时间每毫秒变化都会产生不同客户端。
- 触发条件：内部服务网络连接慢，或持续有 QA 请求导致多种剩余毫秒值。
- 影响：总时延可能超过 deadline；长期运行会积累大量 RestClient/RequestFactory 实例。
- 证据：
  - `backend/src/main/java/com/cidas/edudoc/ai/infrastructure/AiServiceClient.java:48-54`
  - `backend/src/main/java/com/cidas/edudoc/ai/infrastructure/AiServiceClient.java:191-197`
- 修复建议：connect timeout 也按剩余预算设置；按少量固定档位缓存客户端，或使用每请求创建且可释放的 RequestFactory；设置缓存上限。
- 状态：unresolved。

### P2-4 embed 阶段超时未写 FAILED 双消息

- 现象：deadline 检查本身会写 FAILED 消息，但 `aiGateway.embed` 抛出的超时/模型异常没有被捕获，直接向上抛出。
- 触发条件：embed 请求在允许的短预算内超时，或 ai-service embed 返回 502/504。
- 影响：接口返回 504/502，但会话中没有 USER+ASSISTANT FAILED 消息，违反任务文档“段超时 → FAILED 消息”的口径。
- 证据：
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/QaServiceImpl.java:113-114`
  - 只有 answer 阶段在 `QaServiceImpl.java:129-137` 捕获并写 FAILED。
- 现状测试：`QaAnswerServiceTest` 覆盖 answer 超时与 deadline 前置耗尽，未覆盖 embed 超时。
- 修复建议：把 embed/answer 等外部调用统一纳入失败落库包装，避免重复写消息。
- 状态：unresolved。

### P2-5 reduce 阶段没有预算检查，统计与引用不完整

- 现象：
  - map 循环结束后只做一次 `checkBudget`，随后 reduce 调用没有再次检查预算。
  - reduce 返回后的 token usage 未累加，model/promptVersion 未更新。
  - 送入 reduce 的每个摘要段只携带首个 chunkId，其他来源 ID 会丢失。
- 触发条件：多段摘要需要 reduce，且 reduce 耗时较长或原摘要引用多个块。
- 影响：总摘要可能超过 60 秒仍被标为 SUCCEEDED；token 统计偏低；最终引用覆盖减少。
- 证据：
  - `backend/src/main/java/com/cidas/edudoc/ai/application/AiTaskWorker.java:227-238`
  - `backend/src/main/java/com/cidas/edudoc/ai/application/AiTaskWorker.java:251-265`
- 修复建议：reduce 前后检查预算，累加 reduce usage，传递完整来源 ID，并更新最终 model/promptVersion。
- 状态：unresolved。

### P3-1 模型输出结构错误没有重试

- 现象：`chat_json` 只在解析 JSON 失败时重试；Pydantic 结构校验在 `chat_json` 返回后执行，失败立即转 502。
- 触发条件：模型输出可解析 JSON，但字段类型不正确。
- 影响：已经修复“裸 500”，但未完全实现任务文档要求的“输出解析/结构失败重试一次再报错”。
- 证据：
  - `ai-service/app/main.py:60-66`
  - `ai-service/app/main.py:158-186`
- 建议：把 schema 校验纳入 `chat_json` 的重试回调，或在各接口收到结构错误后重试一次。
- 状态：unresolved。

### P3-2 文档与环境变量说明仍有小偏差

- `QaProperties` 类注释仍写默认阈值 0.35，实际配置文件已改为 0.60。
- `ai-service/README.md` 未补充 `EDUDOC_ANSWER_TIMEOUT_S` 校准环境变量。
- 影响：不影响编译和运行，但会误导现场调优。
- 位置：
  - `backend/src/main/java/com/cidas/edudoc/ai/infrastructure/QaProperties.java`
  - `ai-service/README.md`
- 状态：unresolved。

## 5. C01-C09 复审结论

| 项目 | 复审结论 | 依据 |
|---|---|---|
| C01 分块 offset | 基本通过 | 算法与测试通过；当前存量 READY 文档仍为修复前数据 |
| C02 模型档案与客户端 | 通过 | 504 映射、配置档案、token/trace 均有测试 |
| C03 向量化流水线 | 通过 | 两阶段嵌入修复半向量；后批失败测试通过 |
| C04 任务编排与摘要 | 部分通过 | 权限、并发锁键、reduce 已补；锁键原子性和 reduce 预算/统计仍有风险 |
| C05 要点提取 | 通过 | 核心逻辑未回归；导出标题已修正 |
| C06 问答 | 未达标 | 权限、阈值、预算和 504 已改善；P-05 默认 5 秒仍未通过，embed 超时未落 FAILED 消息 |
| C07 润色 | 部分通过 | 审计事务、重复段落、REVERT 已改善；负偏移输入仍有 500 |
| C08 预览 | 通过 | 本轮无回归 |
| C09 ai-service | 基本通过 | 结构和错误已改善；schema 错误没有重试，文档有轻微遗漏 |

## 6. 推荐整改顺序

1. 保留 P-05 为未关闭项，在 C-14 使用真实课件复测；默认 5 秒未稳定通过前不得标记 C06 完成。
2. 修复润色 `charStart < 0` 校验，补 40001 测试。
3. 把 ACTIVE 锁键释放合并到任务终态事务，或增加启动/定时恢复扫描。
4. 约束 QA 动态客户端连接超时和缓存数量。
5. 统一 embed/answer 外部调用的 FAILED 消息落库路径。
6. 在 reduce 前后检查 60 秒预算，补齐 reduce 的 token、模型、promptVersion 和引用来源。
7. 将 schema 校验失败纳入一次重试，并同步 README/配置注释。

## 7. 当前可提交性判断

- 本轮代码改动是否实质推进：**是**。
- 上一轮 P1-1、P1-2 是否关闭：**是**。
- C01-C09 是否可整体关闭：**否**。
- 是否可进入 C10-C13 前端开发：**可以**，接口已足够稳定。
- 是否可进入 C14 全链路验收：**可以有条件进入**；C14 必须把 P-05、ACTIVE 锁恢复和 QA FAILED 消息一致性纳入验收。
- 当前最准确口径：**整改复审有条件通过，仍需关闭 1 个 P1、5 个 P2 和 2 个 P3 风险。**

## 8. 复审边界

- 本轮未修改业务代码、业务数据或测试断言，只新增本复审报告。
- 本轮完整运行了后端和 ai-service 自动化测试。
- 本轮未自行重跑会消耗模型额度的完整真实模型 e2e；真实模型结果引用 `docs/test/AiTest.md`。
- 两个真库 IT 仍因未注入数据库环境变量而跳过，不能把 239 个后端测试全部视为包含真实 Oracle 向量往返。
- 当前可访问 READY 文档仍是 C01 修复前数据，其偏移不变量失败属于存量数据状态，不是本轮代码回归。
