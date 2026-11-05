# 智能处理切片 C01-C09 符合性审查报告

- 审查日期：2026-11-05
- 审查对象：`EduDocAssistant/docs/tasks/03-ai-processing.md`
- 审查范围：C01-C09，包括后端 AI 域、文档预览、ai-service、数据库 Mapper、配置、测试与运行状态
- 审查方式：只读代码审查 + 自动化测试复跑 + 当前 Oracle/Redis 状态核对 + 登录与预览接口只读验证
- 审查结论：**部分通过，尚不能判定完整满足项目要求**

## 1. 结论摘要

C01-C09 的主体功能已经实现，不是空壳或仅有测试桩：

- 分块偏移与 code-point 安全已实现并有单测。
- 服务间客户端、模型档案、向量化 Outbox/Stream、embed worker 已实现。
- 摘要、要点、取消、查询、导出链路已实现。
- 问答的 embed、向量检索、rerank 降级、阈值拒答、引用校验和消息落库已实现。
- 润色的对照、采纳成版、派生对象、EDIT 版本和撤销链路已实现。
- 预览结构树端点已实现。
- ai-service 六类内部接口、内部 token、trace、超时、重试和密钥脱敏已实现。

但仍有会影响数据一致性、错误码契约和验收目标的实质问题：

- C03 多批次向量化仍可能留下半向量。
- C07 成版与关键审计不在同一事务，可能造成业务已生效但接口报错、撤销链不可用。
- C06 的 P-05 问答 5 秒目标未达标，项目自己的真实模型冒烟为 24/25。
- ai-service 返回 504 时，后端会错误映射为 50201。
- ai-service 对“合法 JSON、错误结构”没有 schema 校验，会返回非结构化 500。
- C04/C06 未同时检查 `DOCUMENT_READ` 与对应 `AI_*` 权限。
- C07 对重复段落使用首处文本匹配，可能替换错位置。
- 当前环境中的存量 READY 文档早于 C01 修复，偏移不变量仍不成立，且 chunk 仍为 PENDING。

综合判断：**C01、C05、C08 基本通过；C02、C03、C04、C07、C09 部分通过；C06 当前未达标。**

## 2. 验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 后端测试 | `EduDocAssistant/backend` 下 `mvn test` | 224 通过 / 0 failure / 0 error / 0 skipped |
| 后端真库 IT | Maven 运行日志 | `RbacLifecycleIntegrationTest`、`ChunkEmbeddingVectorIntegrationTest` 均输出 `[IT-SKIP]`，实际 `Tests run: 0` |
| ai-service 测试 | `EduDocAssistant/ai-service` 下 `uv run python -m pytest -q` | 16 passed，1 个 Starlette/httpx 弃用告警 |
| 后端健康检查 | `GET http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| ai-service 健康检查 | `GET http://localhost:8000/health` | `{"service":"ai-service","status":"UP"}` |
| Redis embed Stream | `XLEN` / `XPENDING` | XLEN=10，pending=0 |
| Redis AI task Stream | `XLEN` / `XPENDING` | XLEN=10，pending=0 |
| Oracle AI 任务 | `edudoc_ai_task` 聚合 | RUNNING=0 |
| Oracle QA 消息 | `edudoc_qa_message` 聚合 | READY=17、REFUSED=8、FAILED=9 |
| 当前 READY 文档预览 | 登录后 `GET /api/v1/documents/{id}/preview` | 文档创建于 2026-11-03，首块 `charStart=0,charEnd=32,text.length=32`，偏移不变量失败 |
| 当前 READY 文档向量状态 | Oracle `edudoc_document_chunk` | `index_status=PENDING`，当前不能直接创建摘要/问答 |
| 项目自报真实冒烟 | `docs/test/AiTest.md` | 24/25，唯一 FAIL 为 P-05 问答时延 |

说明：当前终端进程未注入 `SILICONFLOW_API_KEY`、`EDUDOC_AI_INTERNAL_TOKEN`、`EDUDOC_DB_USERNAME`、`EDUDOC_DB_PASSWORD`。运行中的 8080/8000 服务和基础设施可用，但本轮未自行执行会消耗模型额度或改写业务数据的完整真实模型 e2e。

## 3. 问题清单

### P1-1 C03 多批次向量化仍可能落半向量

- 现象：`EmbedWorker` 每 25 块调用一次 embed，校验维度后立即提交该批事务；如果前面的批次成功、后续批次维度错误，前面批次已经写入 READY。
- 触发条件：单版本超过 25 块，前一批返回 1024 维，后续批次返回其他维度。
- 影响：任务最终 FAILED，但版本内已有部分分块和向量 READY，违反“维度不符整批失败且不落半向量”的验收口径。
- 代码位置：`backend/src/main/java/com/cidas/edudoc/ai/application/EmbedWorker.java:203-215`
- 现有测试不足：`EmbedWorkerTest` 只覆盖首批维度错误，没有覆盖“前一批成功、后一批失败”。
- 建议：先完成整个版本所有批次的 embed 和维度校验，再统一落库；或引入版本级临时状态，所有批次成功后一次性切换 READY。
- 状态：未修复。

### P1-2 C07 成版与审计不在同一事务

- 现象：`PolishTxWriter.applyVersion` 在一个事务内写入派生对象、EDIT 版本、current 指针、文档状态、decision 和分块；方法返回后，`PolishServiceImpl` 才单独写 `POLISH_APPLIED` 审计。
- 触发条件：成版事务成功、审计写入失败或进程中断。
- 影响：
  - 用户收到接口失败，但新版本已经生效。
  - 撤销逻辑依赖 `POLISH_APPLIED` 审计回溯，缺少审计时可能无法定位并撤销当前版本。
- 代码位置：
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/PolishServiceImpl.java:134-138`
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/PolishTxWriter.java:59-85`
- 建议：把审计写入放入同一事务；或让业务表直接保存本次采纳产生的 versionId，避免把审计表当作撤销链主数据。
- 状态：未修复。

### P1-3 C06 P-05 问答 5 秒目标未达标

- 现象：项目自己的真实模型冒烟为 24/25，唯一失败项是问答时延。
- 代码事实：当前只给 answer 单次调用设置 4.8 秒读取超时，没有 embed、rerank、生成和数据库访问的统一 5 秒 deadline。
- 触发条件：共享推理排队、embed 或 rerank 变慢、多次外部调用串联。
- 影响：请求可能超过 5 秒；当前不能宣称 P-05 已满足。
- 代码位置：
  - `backend/src/main/java/com/cidas/edudoc/ai/infrastructure/AiServiceClient.java:43-45`
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/QaServiceImpl.java:106-133`
  - `EduDocAssistant/docs/test/AiTest.md:177`
- 建议：给整条 QA 请求设置统一 deadline，剩余时间逐段传递；若模型供应商无法稳定满足，需要调整模型、并发或验收口径。
- 状态：未修复。

### P2-1 ai-service 的 504 被后端映射为 50201

- 现象：`AiServiceClient` 捕获 `RestClientResponseException` 后只根据是否 5xx 判断 retryable，随后统一抛 `MODEL_FAILED`。ai-service 返回 HTTP 504 + `code=TIMEOUT` 时，后端不会映射为 `TIMEOUT/50401`。
- 触发条件：ai-service 因上游模型超时返回 504。
- 影响：违反 C02/C06 的 50401/HTTP 504 契约；前端无法区分超时和普通模型失败。
- 代码位置：`backend/src/main/java/com/cidas/edudoc/ai/infrastructure/AiServiceClient.java:178-184`
- 现有测试不足：只测试了后端本地 read timeout，没有测试内部服务返回 HTTP 504。
- 建议：优先解析 ai-service 结构化 `code`，再以 HTTP status 兜底；补充 504/TIMEOUT 映射测试。
- 状态：未修复。

### P2-2 C09 对模型输出结构缺少 schema 校验

- 现象：用 httpx MockTransport 分别让 `/v1/summarize` 返回 `{"segments":["bad"]}`、`/v1/answer` 返回 `{"citations":["bad"]}`，两端点均返回 HTTP 500 纯文本 `Internal Server Error`。
- 触发条件：模型输出合法 JSON，但字段类型和约定不一致。
- 影响：违反内部接口结构化错误要求，后端无法获得可解释的 `MODEL_FAILED`。
- 代码位置：`ai-service/app/main.py:104-116`
- 建议：用 Pydantic 模型校验 summarize、keypoints、answer 的输出结构；校验失败转换为 `MODEL_FAILED`，并补充回归测试。
- 状态：未修复。

### P2-3 C04/C06 权限组合不完整

- 现象：创建摘要和要点只有 `AI_SUMMARY`/`AI_KEYPOINT` 权限注解；QA 端点只有 `AI_QA`。任务文档要求 `DOCUMENT_READ` 与对应 `AI_*` 同时满足。
- 当前影响：种子角色都含 `DOCUMENT_READ`，因此暂未形成实际越权；但权限配置变化后会失去这层防护。
- 代码位置：
  - `backend/src/main/java/com/cidas/edudoc/ai/api/AiGenerationController.java:24-35`
  - `backend/src/main/java/com/cidas/edudoc/ai/api/QaController.java:34-57`
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/AiTaskOrchestratorImpl.java:43-57`
- 建议：在服务层显式检查第二个权限，或增强权限注解支持多权限；补缺权限组合测试。
- 状态：未修复。

### P2-4 C07 重复段落可能替换错位置，响应状态不一致

- 现象：
  - 采纳时使用 `docText.indexOf(row.getSourceText())`，忽略请求中的 `chunkId`；同一文本重复出现时会替换第一处。
  - 派生文本简化解析实际已同步完成并设置文档 READY，但接口固定返回 `PARSING`。
- 触发条件：文档包含相同段落文本，或前端依赖采纳响应状态显示处理进度。
- 影响：可能改错原文；前端展示状态与数据库实际状态不一致。
- 代码位置：
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/PolishServiceImpl.java:120`
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/PolishTxWriter.java:73-85`
- 项目自报：`docs/test/AiTest.md:178` 已登记重复段落首次匹配债务。
- 建议：按请求 chunkId 和 C01 字符偏移定位替换；返回实际文档状态。
- 状态：未修复。

### P2-5 C04 活跃任务去重存在并发竞态

- 现象：创建任务先 `findActive`，再插入新任务；AI 任务插入时 `idempotency_key` 为 null，Oracle 唯一约束允许多个 null，不能阻止同一版本、类型、用户并发创建多个 QUEUED 任务。
- 触发条件：两个创建请求几乎同时通过 `findActive`。
- 影响：违反活跃去重幂等语义，可能产生重复模型调用和重复费用。
- 代码位置：
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/AiTaskOrchestratorImpl.java:55-68`
  - `backend/src/main/resources/mapper/AiTaskMapper.xml:36-45`
- 现有测试不足：只覆盖顺序调用复用，没有并发测试。
- 建议：使用数据库唯一索引/锁、带条件的插入，或为活跃任务生成稳定幂等键。
- 状态：未修复。

### P3-1 C03 未实现任务文档要求的 STALE 语义

- 任务文档要求旧向量置 STALE，并测试 STALE 到 READY。
- 当前实现选择先删 AI 引用、再删旧向量和旧分块，然后重新生成新分块。
- 影响：STALE 状态不会被消费；现有 `EmbedWorkerTest` 没有 STALE 用例。该差异已在 `docs/test/AiTest.md` 中以“既定取舍”说明，但与任务原文仍不一致。
- 代码位置：
  - `backend/src/main/java/com/cidas/edudoc/document/application/impl/DocumentParseTxServiceImpl.java:38-47`
  - `backend/src/main/java/com/cidas/edudoc/ai/application/impl/EmbeddingTxServiceImpl.java:34-40`
- 状态：口径不一致，未按任务原文实现。

### P3-2 C04 摘要未实现真正的 reduce

- 当前实现逐组 summarize 后直接拼接各段摘要，没有对组摘要再做一次 reduce/汇总。
- 任务文档要求“分段 map-reduce”，该实现更接近 map + concat。对长文档可能得到多段并列摘要，而非最终收敛摘要。
- 代码位置：`backend/src/main/java/com/cidas/edudoc/ai/application/AiTaskWorker.java:197-250`
- 状态：语义偏差。

### P3-3 C05 导出标题固定为摘要

- KEYPOINT 结果复用 DOCX 导出时，标题固定追加“摘要”，但结果类型是 KEYPOINT。
- 代码位置：`backend/src/main/java/com/cidas/edudoc/ai/application/impl/AiResultExportServiceImpl.java:70-90`
- 建议：根据 `resultType` 使用“摘要”“要点”等标题。
- 状态：未修复。

### P3-4 C09 取消和幂等键支持不完整

- 任务背景要求内部任务接口支持取消、超时、幂等键和结构化错误。
- 当前 FastAPI 六类接口没有接收或处理幂等键，也没有独立取消接口；后端取消是修改任务状态，不会取消已发出的模型 HTTP 调用。
- 影响：重复调用可能重复消耗模型额度；取消运行中任务不能真正中止上游推理。
- 位置：`ai-service/app/main.py`
- 状态：契约覆盖不足，未实机验证取消对上游调用的影响。

## 4. C01-C09 逐项结论

| 项目 | 结论 | 已验证内容 | 主要缺口 |
|---|---|---|---|
| C01 分块 offset | 基本通过 | code-point 硬切、代理对不切断、连接符偏移连续、Tika 贯通测试 | 当前存量文档未重灌；现有 READY 样本偏移仍不一致 |
| C02 模型档案与服务客户端 | 部分通过 | 三条 profile seed；token/trace；本地超时；不可达 50201；缺 token 50301 | 内部服务返回 504 时映射错误 |
| C03 向量化流水线 | 部分通过 | 解析同事务发 outbox；Stream 消费；25/批；MERGE upsert；真库向量往返测试存在 | 后批维度失败可能留半向量；STALE 未实现；真库 IT 默认跳过 |
| C04 任务编排与摘要 | 部分通过 | 任务状态机、取消、进度、引用、导出、审计、分组 | 双权限未检查；活跃去重并发竞态；无真正 reduce；审计在结果事务外 |
| C05 要点提取 | 基本通过 | 乱序重排、非法引用丢弃、`underMin`、引用回填 | KEYPOINT DOCX 标题固定为“摘要” |
| C06 问答 | 未达标 | topK=6、rerank take4/降级、阈值拒答、引用硬校验、双消息、history | P-05 未达标；无端到端 5s deadline；双权限未检查；内部 504 映射错误 |
| C07 润色 | 部分通过 | 段落限额、对照行、OSS 派生对象、EDIT 版本、索引衔接、REJECT、REVERT | 审计事务分离；重复段落可能错替换；响应状态不一致；REVERT 与任务文字存在语义偏差 |
| C08 预览 | 通过 | 当前版本限制、owner 40401、chunkNo 排序、偏移断言 | 无阻断性问题 |
| C09 ai-service | 部分通过 | 六类接口、内部 token、trace、超时重试、Retry-After、密钥脱敏、提示词集中管理 | 输出 schema 未校验；取消/幂等键契约不完整 |

## 5. 当前运行环境观察

### 5.1 服务状态

- 后端 8080 和 ai-service 8000 均在监听，健康检查通过。
- Oracle 1521 和 Redis 6379 可用。
- Redis 两个 AI 相关 Stream 均无 pending 积压。
- Oracle `edudoc_ai_task` 当前无 RUNNING 任务。

### 5.2 当前业务数据

- 学生账号可访问 1 篇 READY 文档。
- 该文档创建于 2026-11-03，早于 C01 修复提交 `71c1705`（2026-11-04）。
- 首块数据为 `charStart=0`、`charEnd=32`、`text.length=32`，闭区间长度应为 33，当前偏移不满足 C01 密铺不变量。
- 该文档 chunk 的 `index_status=PENDING`，因此当前不能直接创建摘要或问答，创建接口应返回 40901。
- 这不是当前代码新引入的回归，而是任务文档所述的“不做历史迁移”前提尚未在当前环境执行重解析/重灌。

### 5.3 历史 QA 结果

- READY=17
- REFUSED=8
- FAILED=9

历史 FAILED 消息与项目文档记录的 P-05 超时校准问题方向一致，说明问答链路虽然有正确的失败落库行为，但性能目标仍未稳定达成。

## 6. 与任务文档的符合性判断

### 6.1 已符合或基本符合

- C01 的算法与测试口径符合要求，前提是新解析或重灌后的数据。
- C08 的预览结构树接口符合要求。
- C05 的核心要点处理符合要求。
- C02 的主体配置和客户端行为符合要求，除 504 细分映射。
- C03 的主链路结构符合 Outbox → Stream → EmbedWorker 设计。
- C04 的任务、进度、取消和结果结构主体符合设计。
- C09 的六类接口和模型适配主体符合设计。

### 6.2 不符合或未完全符合

- C03 的“无半向量”与 STALE 验收未完全满足。
- C06 的 P-05 5 秒目标未满足。
- C07 的审计事务边界不符合“单事务”要求。
- C02/C06 的 50401 映射不符合契约。
- C09 的异常输出结构化不符合要求。
- C04/C06 的权限组合不符合任务文字。
- C07 的重复段落替换存在错改风险。
- C04 的 map-reduce 语义不完整。

## 7. 建议整改顺序

1. 修复 C03 版本级原子性：所有批次 embed 和维度校验完成后统一落库，禁止部分 READY。
2. 修复 C07 成版事务：将 `POLISH_APPLIED`、`POLISH_REVERTED` 与版本/指针/decision 写入同一事务。
3. 修复 QA 5 秒端到端预算，真实模型复测通过后再关闭 P-05。
4. 修复 ai-service 504 到后端 50401 的映射，并补内部 504 测试。
5. 给 ai-service 输出增加 schema 校验和结构化错误测试。
6. 在服务层补齐 `DOCUMENT_READ + AI_*` 权限组合。
7. 用 chunkId 和 C01 偏移替换润色目标段落，修正响应状态。
8. 增加活跃任务去重的数据库级并发约束。
9. 重解析或重灌当前存量 READY 文档，验证 C01 新偏移语义。

## 8. 可提交性判断

- 是否可继续开发 C10-C13：**可以**，当前后端接口和数据结构足以支撑前端继续开发。
- 是否可作为 C01-C09 完整验收通过：**不可以**。
- 是否可进入 C14 全链路验收：**可以有条件进入**，但必须先关闭 C03 半向量、C07 审计事务和 C06 P-05 三个 P1 问题。
- 当前最稳妥的表述：**C01-C09 主链路已完成，处于部分通过、待整改状态。**

## 9. 审查边界

- 本轮没有修改任何业务代码、测试断言或业务数据。
- 本轮没有执行会消耗模型额度的完整真实模型 e2e。
- 本轮没有复现多实例 Stream 消费、并发任务创建和真实上游 504 场景。
- 两个真库 IT 因缺少环境变量而跳过，不能把 224 个后端测试全部理解为包含真实 Oracle 向量往返。
- 当前真实模型冒烟数据引用自 `docs/test/AiTest.md` 的项目记录，本轮未重复执行。
