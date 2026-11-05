# 03 · 智能处理垂直切片（AI 域）

> 承接 02 文档接入切片的产出（分块 + `index_status=PENDING` 占位 + Outbox/Stream 骨架 + ai-service `/health` 空壳），打通「向量化 → 摘要/要点/问答/润色 → 引用回跳 → 润色采纳成版」的智能处理闭环，前端补齐预览页宿主与四类 AI 面板。
> 契约源：需求 §5.2（REQ-10~13、P-04 摘要 60s、P-05 问答 5s、图 6-9）、详细设计 §3.2（表 3-3/3-4、问答算法、任务状态机）、§4.1（原型 05-10 屏映射）、§4.2（引用回跳高亮、长任务轮询）、§5.1（50201/50401）、§6.2（UT-08/09/10/14）、概要 §4.2/§5.4（向量化数据流）、§7（I04 模型适配、ADR-03 Oracle 向量）、`01_schema.sql`（AI 相关 7 表已全量建齐，**零 DDL**）。

## 用户拍板决策（2026-11-04）

| # | 决策 | 内容 |
| --- | --- | --- |
| D1 | 真实模型接入 | OpenAI 兼容适配层，推荐硅基流动 SiliconFlow 一路凭据：对话模型 + `BAAI/bge-m3` 嵌入（恰好 1024 维，匹配 `edudoc_chunk_embedding.VECTOR(1024)` 列）+ `bge-reranker-v2-m3`。API Key 由用户经环境变量提供，不入仓 |
| D2 | 预览页形态 = 文本阅读视图 | 按已解析分块（text + heading_path + page_no + char 区间）渲染可读视图，页码锚点；引用回跳=滚动至对应块并高亮 char_start~char_end。02 排除的预览页由本切片补上（不做 pdf.js 原样渲染） |
| D3 | 润色采纳落地物 = 派生全文 .txt 对象 | 以解析文本为底、替换被采纳段落，生成派生 oss_object（**角色落字典值 PARSED**，`ck_oss_role` 无 DERIVED——概念名与列值解耦，1105 审查确认）→ `version_type=EDIT` 新版本；撤销=当前 EDIT 版本弹栈、`current_version_id` 回拨前一版本（版本行保留，不违不可变红线；撤销前经 POLISH_APPLIED 审计回溯校验当前版本确由该行最近一次采纳产生，「撤销→重采纳」孤儿链显式 40901 拒绝——**1105 审查 P2/C07 整改后口径**，单次链下与「回拨至 document_version_id」等价）；docx 写回属协作编辑域，不做 |

## 不属本切片

版本列表/对比/恢复 UI、关键词检索高亮（`GET /documents/{id}/search` 仍不做）、协作编辑（gateway/Yjs）、模板与格式校验（04 切片）、导出域完整能力（摘要 .docx 仅 ai 域最小实现，迁移标注见 C-04）、课程共享 ACL、SSE（轮询沿用 `// ponytail` 口径）。

---

## C-01 前置债务 · 分块 offset 修正（S10/N7 关闭）

- **引用**：1103 审查 S10、1104 复审 P3-7「ai 域用 offset 做引用定位前必须修」；§4.2「高亮字符范围」。
- **现状**：`ChunkingService.hardSplit` 按 UTF-16 code unit 硬切，emoji/生僻字可切出孤立代理项；跨块 `\n` 连接符未计入 `char_start/char_end`，存在漂移。`ChunkingServiceTest` 现口径把漂移当预期。
- **验收标准**：
  - 硬切改 **code-point 安全**：`offsetByCodePoints` 定位切点，孤立代理对不得跨块切断（切点落在代理对之间时前移一位）。
  - 连接符计入偏移：块间以 `\n` 连接时，下一块 `char_start` 含前导连接符位；文档级不变量：**同版本按 chunk_no 排序后首块 char_start=0、相邻块 char_end+1=后块 char_start（连接符语义一致）**。
  - 存量重生成：修复后 `POST /{id}/parse` 重解析即产出正确偏移；不做历史数据迁移（本环境测试文档全量重灌，正式环境尚无真实用户数据，任务文档记录该前提）。
- **测试**：`ChunkingServiceTest` 改断言：代理对（`😀`、生僻字 `𠮷`）不切断、连接符偏移连续、单块硬切连续；`TikaTextExtractorTest` 增补含代理对文本的提取-分块贯通例。
- **依赖**：无 | **状态**：已完成

## C-02 模型配置档案 + 服务间客户端

- **引用**：概要 §7 I04（超时、熔断、重试、可解释失败码）、`edudoc_ai_model_profile`（endpoint_config_key 指配置中心键，不存明文密钥）、ADR-03（向量维度固定并由适配层校验）。
- **现状**：`edudoc.ai-service.base-url` 已置 `http://localhost:8000`；`ai` 域仅 package-info；profile 表空。
- **验收标准**：
  - `SeedDataRunner` 幂等 seed 三条 `edudoc_ai_model_profile`：`LLM_MAIN`（provider_type=LLM）、`EMB_BGE_M3`（EMBEDDING，embedding_dimension=1024、metric_type=COSINE）、`RERANK_BGE_V2`（RERANK）；模型名/端点经 `edudoc.ai.{llm,embedding,rerank}.*` 配置注入（环境变量可覆盖），`profile_json` 存 temperature 等非敏感参数。
  - 新建 `ai.infrastructure.AiServiceClient`：RestClient 调 ai-service 内部接口，超时分级（embed/retrieve 短、生成类长）、透传 `X-Trace-Id`、服务间鉴权头 `X-Internal-Token`（`EDUDOC_AI_INTERNAL_TOKEN` 环境变量，缺失时 AI 接口 50301 干净降级——沿用 1104 启动健壮性教训，客户端 Bean 不得炸启动）；ai-service 不可达 → 50201 可重试。
  - `ai.application` 定义 `AiGateway` 接口（embed/summarize/extractKeypoints/answer/polish/rerank），Impl 在 infrastructure（红线：HTTP 客户端只在 infrastructure）。
- **测试**：`AiModelProfileSeedTest`（幂等）、`AiServiceClientTest`（mock：超时映射 50201/50401、token 头、trace 透传、缺 token 启动不炸）。
- **依赖**：无 | **状态**：已完成

## C-03 向量化流水线（Outbox → Stream → EmbedWorker）

- **引用**：概要 §4.2（AI 服务向量化写 Oracle）、§4.3 表（分块保留页码与字符范围、维度匹配）、`index_status` 占位由 02 预留。
- **现状**：解析成功只置文档 READY，chunk `index_status=PENDING`；无 EMBED 事件与消费者。
- **验收标准**：
  - `DocumentParseTxServiceImpl.writeSuccess` 同事务追加 outbox 事件 `DOCUMENT_EMBED_REQUESTED`（aggregateRef=versionId）；重解析成功后同样补发（旧向量行与旧 AI 引用行同事务物理删除后随新块重建——`fk_chunk_embedding_1`/`fk_ai_citation_2` 均无级联，且 `ck_chunk_index` 无 STALE 值；STALE 语义仅适用「分块存活」场景，重解析是整版换块。**1105 审查 P3-1 口径对齐：本行为既定实现，审查已确认合理**）。
  - 新事件路由 `edudoc:stream:document-embed`（`OutboxProperties` 映射表扩展），`EmbedWorker` 消费组 `cg-document-embed`、独立线程池：按 version 取全部 chunk → 25/批调 `AiGateway.embed` → 维度校验（≠1024 → 50201 任务 FAILED 且不落半向量）→ 单事务 upsert `edudoc_chunk_embedding`（VECTOR 列以 `[a,b,…]` 字符串绑定 + `TO_VECTOR(?, 1024, FLOAT32)`）并置 chunk `index_status=READY`。
  - 幂等：消息重投按 (chunk_id, model_profile_id) uk 覆盖写；`EMBED` 任务行入 `edudoc_ai_task`（task_type=EMBED）记录进度与失败（retry_count 复用）。
  - 观测：`redis-cli XLEN/XPENDING`、`SELECT index_status, COUNT(*) … GROUP BY`。
- **测试**：`EmbedWorkerTest`（mock AiGateway：批量切分、维度不符整批 FAILED——1105 审查 P1-1 后为**两阶段**：全部批次校验通过才统一落库、后批失败不留半向量；重投幂等=upsert 覆盖写）；`ChunkEmbeddingRepositoryIT` 风格真库向量往返（TO_VECTOR→VECTOR_DISTANCE 自检，沿用 `[IT-SKIP]` 告警先例）。
- **依赖**：C-02 | **状态**：已完成

## C-04 AI 任务编排 + 摘要链路（含结果导出最小实现）

- **引用**：§3.2.1 状态机（QUEUED→RUNNING→SUCCEEDED/FAILED/CANCELED）、表 3-4（summaries / ai-tasks/{taskId} / cancel）、UT-08/UT-20、P-04（30 页 60s 含摘要）、§4.2 长任务轮询、REQ-10.5 导出 .docx/.txt。
- **现状**：无 ai_task 访问层；前端无任务面板。
- **验收标准**：
  - `ai.application.AiTaskOrchestrator`：创建任务 = 校验文档 READY 且该版本向量 READY（否则 40901「文档尚未就绪」）+ DOCUMENT_READ 与 `AI_SUMMARY` 权限点 + owner/课程成员口径同 02；单事务插 `edudoc_ai_task(QUEUED, request_payload)` + outbox `AI_TASK_REQUESTED`；**活跃去重**：同 (document_version_id, task_type, requested_by) 存在 QUEUED/RUNNING 任务 → 直接返回既有 taskId（幂等语义落地方式，与 R6 过滤器 40902 并存，差异记录见风险表）。
  - `AiTaskWorker` 消费 `edudoc:stream:ai-task`：CAS 置 RUNNING（重复投递按 taskId+状态去重，UT-20）→ 按类型路由 summarize/keypoint handler → 分段 map-reduce：全部分块按 char 序分组送 `AiGateway.summarize`（分组 ≤6k 字符，逐组进度 progress 0-100 回写）→ 汇总结论文本入 `edudoc_ai_result`（SUMMARY，structured_json 按段落分组、组含引用 chunkId），每引用一行 `edudoc_ai_citation`（page_no/char_start/char_end 由 CitationService 从分块表回填，模型不引用不存在的 chunkId=非法引用丢弃，全部非法→REFUSED 语义按 §3.2.2 仅问答适用，摘要失败记 FAILED）+ 审计 `AI_TASK_SUCCEEDED`（含 provider/model/耗时/token 统计，**不含正文**）。
  - 取消：`POST /ai-tasks/{taskId}/cancel`——QUEUED→CANCELED（204）；RUNNING 置 `cancel_flag`（worker 写结果前检查，已取消则丢弃不回写）；终态 → 40901。
  - `GET /api/v1/ai-tasks/{taskId}`：status/progress/errorCode/result（SUCCEEDED 含结果与引用）；非本人任务 40401。
  - 导出最小实现：`GET /api/v1/ai-results/{id}/export?format=txt|docx`——txt 前端可自产，此端点服务 docx（POI XWPF：标题+段落+引用脚注），`Content-Disposition` 文件名含文档标题；**ponytail 标注**：export 域切片落地后迁出。
  - 超时：单任务预算 60s（`ai.task-timeout-seconds` 配置），worker 超预算置 FAILED(50401)。
- **测试**：`AiTaskOrchestratorTest`（UT-08 活跃去重、40901 未就绪、权限 40301、非 owner 40401）、`AiTaskWorkerTest`（状态机 CAS、取消竞争、分组进度、非法引用丢弃）、`AiResultExportTest`（docx 生成可读、含引用、不越权）。
- **依赖**：C-03 | **状态**：已完成

## C-05 要点提取任务

- **引用**：REQ-11（≥5 条、按文档顺序、每条可定位）、表 3-4 keypoints。
- **验收标准**：`POST /documents/{id}/keypoints`（AI_KEYPOINT）复用 C-04 编排（task_type=KEYPOINT）；ai-service 返回 JSON 数组（text + chunkId + 可选 char 建议），后端按引用 chunk 的 char_start **重排为文档顺序**、截断非法引用；条数 <5 时在 structured_json 标记 `under_min`（不判失败，UI 提示）。结果渲染与导出复用摘要通道（result_type=KEYPOINT）。
- **测试**：`KeypointHandlerTest`（乱序入参重排、非法 chunkId 丢弃、under_min 标记）。
- **依赖**：C-04 | **状态**：已完成

## C-06 问答：检索、证据阈值与拒答（同步链路）

- **引用**：§3.2.2 问答核心算法（embed→search topK=6→rerank take(4)→阈值→evidence-only prompt→citations，两处 REFUSED 分支）、REQ-12（≤5s、连续追问、限当前文档、无依据明确提示）、UT-09/UT-10、`qa_session/qa_message`（status READY/FAILED/REFUSED）。
- **现状**：表空；前端无问答面板。
- **验收标准**：
  - `POST /qa/sessions` `{documentId, title?}`（AI_QA + READ）→ 201 会话（owner 唯一，uk 无则按 (document_id,user_id) 复用活跃会话口径由服务层保证，状态 A/C 支持归档重建）；`GET /qa/sessions?documentId=` 历史回看（5.1.3 历史记录要求）。
  - `POST /qa/sessions/{id}/messages` `{question}`（1-1000 字符，§4.3 校验）：同步执行——question→`AiGateway.embed`→`RetrievalService`（`VECTOR_DISTANCE(e, TO_VECTOR(?), 1, COSINE)` 走版本内 topK=6，SQL 全在 mapper.xml）→ `rerank`（未配置 RERANK profile 或调用失败 → **降级按向量序 take(4)**，`// ponytail` 标注）→ `evidenceScore = 1 - min(distance)` 与 `edudoc.qa.evidence-threshold`（默认 0.35，配置可调——物理世界校准口）比对 → 不足：qa_message(REFUSED)×2（USER+ASSISTANT），200 返回拒答结构（**拒答是业务成功**，状态机口径 §3.2.1）；足够：`answer`（evidence-only prompt，maxTokens=900）→ CitationService 校验 chunkId 属本版本且引用文本与分块内容可核对（前 20 字符包含即可，防漂移放大）→ 全部非法→REFUSED（UT-10）→ 落库 ASSISTANT 消息（ai_task_id=同步建的 QA 任务行，供 token 统计与审计）+ citations，`sequence_no` 会话内自增（uk_qa_message_1）。
  - 连续追问：请求可带 `history` 由服务端自动取会话最近 6 条消息拼 prompt（前端不传历史，防注入）。
  - 超时预算 5s：ai-service answer 客户端 timeout=4.2s，超时→消息 FAILED(50401)、HTTP 504 契约、不写伪造答案（§7.2「问答超时」端到端口径）。
- **测试**：`QaAnswerServiceTest`（阈值拒答不调生成 UT-09、非法引用 UT-10、rerank 降级、history 拼接、REFUSED 双消息与 sequence_no）、`QaControllerTest`（校验、40301/40401）。
- **依赖**：C-03、C-04（任务表与审计复用）| **状态**：已完成

## C-07 润色：段落对照、采纳成版与撤销

- **引用**：REQ-13（左右对照、采纳/部分/放弃、一次撤销、还原可重润）、§4.3 润色段 ≤5000 字符且 ≥1 段、表 3-4 polish/decision、D3 决策。
- **验收标准**：
  - `POST /documents/{id}/polish` `{paragraphs:[{chunkId? ,text}]}`：text 合计 ≤5000 字符（40001）；同步调 `AiGateway.polish`（只送选中段落，§4.2「不把整份文档发送给模型」）→ 每段落一行 `edudoc_polish_result`（PENDING，source/polished 对照；比对标记由前端 diff 库或后端可选 `diff` 字段——本切片前端渲染左右对照即可）。
  - `POST /polish/{id}/decision` `{decision: ACCEPT|PARTIAL|REJECT, acceptedText?}`：ACCEPT/PARTIAL 单事务——读当前版本解析文本（chunk 全量顺序拼接）→ 替换目标段落 → 新 `edudoc_oss_object`（DERIVED，`.txt`）→ 新版本（version_no+1、version_type=EDIT、source_object=派生对象）→ `current_version_id` 前移 → decision 与 accepted_text 回写 + 审计 `POLISH_APPLIED`；同文档已有未撤销 EDIT 链时**基于最新版本**重放替换（防并发覆盖，冲突→40901 §4.2）。REJECT 仅记状态。
  - 撤销：`decision` 支持 `REVERT`——弹出版本链顶层 EDIT、`current_version_id` 回拨前一版本（版本行保留历史；`ck_polish_decision` 无 REVERT 值，决策回写 PENDING 支持重润重采纳，1105 审查确认口径），仅允许撤销**最近一次**未撤销的采纳且须审计回溯确认当前版本由该行产生（否则 40901），审计 `POLISH_REVERTED` 与回拨**同事务**（1105 审查 P1-2）。
  - 采纳替换定位（1105 审查 P2-4）：决策请求可带 `charStart`（预览块全文锚点）精确替换该偏移处出现；未带锚点且同文存在多个相同段落 → 40901 拒绝消歧，不静默替换首处。
  - 采纳后文档转 PARSING？——**否**：派生 .txt 走简化解析（文本即结构：按段落成块，跳过 Tika），与解析 worker 复用分块+向量化通道（新 outbox `DOCUMENT_EMBED_REQUESTED` 自然衔接待 C-03 组件），文档 READY 前预览与问答读旧版本内容（current_version 指向策略见风险表 R3）。
- **测试**：`PolishServiceTest`（段落校验、采纳成版五写、REVERT 仅最近一次、并发采纳 40901、REJECT 不动版本）。
- **依赖**：C-03、C-04 | **状态**：已完成

## C-08 预览结构树端点

- **引用**：§3.1.3 `GET /documents/{id}/preview`（预览对象或结构树；本切片按 D2 取「结构树」语义）、§5.1.2（预览确认内容结构）。
- **验收标准**：`GET /api/v1/documents/{id}/preview?versionId=`（DOCUMENT_READ，versionId 仅允许当前版本，同 02 parse 口径）→ 200 `{documentId,versionId,versionNo,blocks:[{chunkId,chunkNo,headingPath,pageNo,charStart,charEnd,text}]}`，blocks 按 chunk_no 排序；文本视图数据源即此端点，一次全量返回（20MB 上限内分块总量可控，`// ponytail: 超长文换分页`）。
- **测试**：`DocumentPreviewServiceTest`（owner 40401、非当前版本 40901、块序与偏移断言含 C-01 不变量）。
- **依赖**：C-01 | **状态**：已完成

## C-09 ai-service · 六类内部接口与模型适配层

- **引用**：概要 §7.3（`summarize`/`extract-keypoints`/`embed`/`answer`/`polish` 五类 + 取消/超时/幂等键/结构化错误）、§7 I04（OpenAI 兼容、超时熔断重试、可解释失败码、不伪造结果）、§4.2（拒答不以常识补写）。
- **现状**：FastAPI 空壳（`/health`、`/smoke`），uv 管理依赖。
- **验收标准**：
  - `POST /v1/embed`（批量 texts → 等长向量数组，维度校验失败 500 结构化错误）、`/v1/summarize`（分组文本+提示模板 → 摘要段与 chunkId 引用）、`/v1/extract-keypoints`（强制 JSON 数组输出，解析失败重试一次再报错）、`/v1/rerank`（query+docs→相关性序）、`/v1/answer`（evidence-only prompt，要求逐句带 chunkId 引用标记，maxTokens 上限）、`/v1/polish`（保留专业术语口径进模板）。
  - OpenAI 兼容客户端（httpx 直连 chat/embeddings/rerank endpoints），模型名与 base_url 走 `.env`（`SILICONFLOW_API_KEY`、`SILICONFLOW_BASE_URL` 默认 `https://api.siliconflow.cn/v1`，模型默认 `Pro/moonshotai/Kimi-K2-Instruct`/`BAAI/bge-m3`/`BAAI/bge-reranker-v2-m3`，可覆盖）；请求超时分级 + 一次重试 + `Retry-After` 透传；错误响应 `{code:MODEL_FAILED|TIMEOUT|BAD_INPUT, message, provider, model, elapsedMs}`，**不回显 API Key**。
  - `X-Internal-Token` 校验（401）；`X-Trace-Id` 透传进日志；提示模板集中在 `app/prompts.py`（`prompt_version` 常量随结果返回，供 `edudoc_ai_result.prompt_version`）；输出经 Pydantic 结构校验（合法 JSON 坏结构 → 502 MODEL_FAILED，1105 审查 P2-2）。
  - **取消与幂等键口径（1105 审查 P3-4 回执）**：ai-service 六接口为无状态纯函数式调用，幂等天然成立（同请求可安全重放）；任务级幂等与取消由后端任务层承载（`uk_ai_task_1` + ACTIVE 确定性锁键 + QUEUED/RUNNING 状态机守卫，取消=结果写回丢弃）。OpenAI 兼容上游无取消/中断 API——**已发出的推理无法真正中止**，取消只保证不消费、不伪造结果；如需省额度须在任务领取前取消（QUEUED 态）。不做假取消接口。
  - 启动：`uvicorn app.main:app --port 8000` 写入 README；`.venv` 已有，`uv sync` 补 httpx/pytest。
- **测试**：pytest（httpx mock 传输层）：embed 批量对齐、answer 引用标记解析、阈值外 JSON 坏输出重试与结构化错误、token 校验 401、超时映射；`python -m pytest` 进验收命令。
- **依赖**：C-02 契约对齐（可并行开发，凭据到位后联调）| **状态**：已完成

## C-10 前端 · API 模块 + 预览页（文本阅读视图）

- **引用**：§4.1 原型 05/04 屏、§4.2 交互原则、D2。
- **验收标准**：`web/src/api/ai.ts`（八端点封装 + 类型，复用 http.ts 幂等键/trace/401 处理）+ `ai.test.ts`；新建 `pages/DocumentView.tsx` 路由页（`pageOf` 扩展或文档详情 Drawer 增「打开预览」入口 → 左右布局：正文块流 + 右侧面板 Tabs；锚点 `block-{chunkNo}`、页码分隔标记、**高亮原语** `highlightRange(chunkId, start, end)`（块内字符级 `<mark>`，跨块分段）供三类面板复用；轮询沿用文档态 PARSING 停步逻辑；学生/教师共用。
- **测试**：api 模块参数断言；DocumentView 渲染块序、锚点、高亮切片正确（含 C-01 修复后的偏移语义）。
- **依赖**：C-08 | **状态**：未开始

## C-11 前端 · 摘要/要点面板

- **验收标准**：生成按钮（权限点缺省置灰）→ 任务创建后 2s 轮询 `GET /ai-tasks/{id}`（阶段+进度条+取消，§4.2「刷新后从任务接口恢复」：taskId 存 `localStorage['edudoc-aitask-{docId}'`）；摘要按段落分组折叠、组展开显示原文片段、**句/条点击 → C-10 highlightRange 跳原文**；重新生成=新任务；复制（clipboard）；导出 .txt（Blob）/ .docx（后端端点下载，token 走 header → 用 fetch blob 而非直链）。要点：勾选+复制选中+导出清单。
- **测试**：轮询恢复、引用点击调高亮、导出触发（mock）。
- **依赖**：C-04、C-05、C-10 | **状态**：未开始

## C-12 前端 · 问答面板（含拒答态）

- **验收标准**：会话进入=创建/复用 session；消息流（USER/ASSISTANT/REFUSED 三态样式，09 屏拒答文案「未在文档中找到依据」）；引用 chip 列表（页码+片段前 40 字）点击跳原文高亮；连续追问禁言至上一条返回（同步 5s，超时提示可重试不显示伪造答案）；历史回看=GET sessions 载入消息。
- **测试**：拒答渲染、引用跳转回调、超时态、历史载入。
- **依赖**：C-06、C-10 | **状态**：未开始

## C-13 前端 · 润色面板

- **验收标准**：预览页块多选（勾选段落，合计 ≤5000 字符前端预检同 §4.3 文案）→ 润色 → 左右对照（原文/润色，块级并排）→ 操作：采纳/部分采纳（逐段勾选）/放弃；采纳成功 → 提示「已生成新版本」+ 撤销按钮（一次）；重润=对未采纳段再次提交。
- **测试**：选择限制、对照渲染、采纳→撤销调用链、放弃不动版本。
- **依赖**：C-07、C-10 | **状态**：未开始

## C-14 联调 · 端到端 + 测试文档

- **验收标准**：
  - `docs/test/AiTest.md`（AuthTest/DocumentTest 同构：环境前置含 SiliconFlow 凭据与 ai-service 启动、逐接口 curl 正反例、redis/SQL 观测命令、建议顺序）。
  - e2e（真实模型）：上传 30 页 PDF → 解析 → **向量 READY 全量**（`index_status` 计数）→ 摘要任务 SUCCEEDED ≤60s（P-04 含摘要计时）→ 引用回跳可核 → 要点 ≥5 条有序 → 问答命中 ≤5s + 引用正确 + 无依据问题 REFUSED（文档外主题）→ 润色采纳出新版本（EDIT）+ 撤销回旧 → 重解析后向量重建 STALE→READY。反例：向量未就绪发摘要 40901、润色段超限 40001、越权他人文档 40401、ai-service 停机 → AI 接口 50201 干净降级（启动不炸探针）。
  - 全绿口径：`mvn test`、`cd ai-service && python -m pytest`、`pnpm test`、`pnpm build`。
- **依赖**：C-01~C-13 | **状态**：未开始

---

## 环境与凭据清单（用户侧，B-xx 先例格式）

| # | 项 | 方式 |
| --- | --- | --- |
| 1 | SiliconFlow（或同构 OpenAI 兼容）API Key | 环境变量 `SILICONFLOW_API_KEY`（`setx`，新终端生效） |
| 2 | 模型选择 | 默认可用；如需换 `chat/embedding/rerank` 模型名，给三个名字（embedding **必须 1024 维**，否则维度校验整批拒） |
| 3 | 服务间共享密钥 | 由我生成随机值，用户 `setx EDUDOC_AI_INTERNAL_TOKEN`（两侧同名） |
| 4 | ai-service 运行 | 本机 `uv sync && uvicorn`（`.venv` 已存在），无需 Docker |

## 建议执行顺序

```
C-01 → C-02 ∥ C-09（契约先行可并行，凭据到位后合流）
→ C-03 → C-04 →（C-05 ∥ C-06 ∥ C-07）→ C-08
→ C-10 →（C-11 ∥ C-12 ∥ C-13）→ C-14
```

## 风险与已知债务

| # | 事项 | 处理 |
| --- | --- | --- |
| R1 | 设计 UT-08「同幂等键复用任务」与 `IdempotencyFilter` 同键 40902 冲突（02 R6 同源） | 业务幂等改用「同版本+类型+用户活跃任务去重」；过滤器语义不改，差异记录于 AiTest.md |
| R2 | rerank 属额外一次模型调用与凭据面 | 做成可缺省降级（向量序 take4），有 RERANK profile 即启用；不阻塞验收 |
| R3 | 采纳成版后 current_version 指向新版本，但新文本向量化异步未完 → 问答读不到新内容 | 预览/问答按 current_version 的向量 READY 子集检索；未就绪期间结果标注「新版索引中」，不静默读旧版 |
| R4 | 模型费用与限流（真实接入） | e2e 控制在最小样本（≤3 文档、单轮问答），AiTest.md 记录 token 统计；压测不在本切片 |
| R5 | 摘要质量验收口径主观 | 按表 11-1「抽样评审 + 原文定位核查」：只验收「可定位、不编造、引用合法」，不比对文笔 |
| R6 | `.docx` 导出落在 ai 域（POI 直出） | `// ponytail`：export 域切片（06）落地后迁移；本切片端点仅覆盖摘要/要点文本+引用结构 |
| R7 | 派生 .txt 非原文档版式 | D3 既定取舍：采纳链以「文本演进」为准，原件 ORIGINAL 对象永久保留；docx 保真写回属协作编辑域 |
| R8 | 向量化后 Oracle 负载 | topK 检索走 `VECTOR_DISTANCE` 全表小数据量（单课程级），`// ponytail`：数据量上来再建向量索引（23ai AI Vector Index 语法届时验证） |
| R9 | prompt 注入（文档正文不可信） | evidence-only 模板 + 引用硬校验（chunkId 必须属本版本）双保险；答案无合法引用即 REFUSED，不自由发挥 |

## 测试与验证基线

- 后端单测预计 +40 例；ai-service pytest +12 例；前端 +14 例（api/预览/三面板）；e2e 脚本沿用 `%TEMP%\edudoc-b13` 模式扩到 AI 链路。
- 架构红线复核：模型调用只经 ai-service；HTTP/OSS/Mapper 只在 infrastructure；请求线程不跑推理（摘要/要点走 Stream，问答同步但 5s 预算内且走独立线程池的 RestClient）；版本不可变（润色成版+撤销回拨）；审计不写正文。
