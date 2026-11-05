# 智能处理接口 · 手动测试说明

> 覆盖 03 切片 C-01~C-09 后端与 ai-service 接口（预览结构树、向量化流水线、摘要/要点任务、问答、润色成版与撤销、结果导出、ai-service 六类内部接口）。结构与 [AuthTest.md](AuthTest.md)、[DocumentTest.md](DocumentTest.md) 同源。
> Apifox 可直接导入 [apifox/ai-processing.postman_collection.json](apifox/ai-processing.postman_collection.json)：9 组 38 个请求含全部反例；token/documentId/taskId/resultId/sessionId/polishId 链式变量自动串联，预览请求自动挑润色段与问句片段，按文件夹顺序执行即可走完全链路。
> **回归基线**：2026-11-05 真库+真模型冒烟 **24/25**（唯一未过项为 P-05 时延校准，属既定裁决遗留，见「回归结果」）；1105 符合性审查 13 项整改后 `mvn test` **239 全绿**、`python -m pytest` **19 全绿**（含 READY 路径真机实证，见「1105 审查整改复测」）。

## 环境与前置

- **Base URL**：后端 `http://localhost:8080`（`mvn spring-boot:run`）；ai-service `http://localhost:8000`（`cd ai-service && uv sync && uv run uvicorn app.main:app --port 8000`）
- **依赖服务**：Oracle FREEPDB1（`oracle-dev`）、Redis（`redis-dev`）、阿里云 OSS（重解析下载源件用；账号欠费/禁用会以 `OSSException: UserDisable` 形式把文档置 FAILED）
- **模型凭据**（用户级环境变量，不入仓）：
  - `SILICONFLOW_API_KEY`：硅基流动密钥，**账户余额需可用**（402 `code=30001` = 余额不足，全链路会在向量化/生成环节按 50201 干净失败）
  - `EDUDOC_AI_INTERNAL_TOKEN`：**后端与 ai-service 两侧同名同值**（服务间共享密钥）。ai-service 未配置 → `/v1/*` 一律 503；不匹配 → 401；后端未配置 → AI 接口 50301 干净降级（启动不炸）
- **模型默认值**（可被环境变量覆盖）：chat=`deepseek-ai/DeepSeek-V3`（摘要/要点/润色，60s 预算内）、答案=`Qwen/Qwen2.5-7B-Instruct`（P-05 5s 预算的快档位）、嵌入=`BAAI/bge-m3`（**必须 1024 维**，与 `edudoc_chunk_embedding.VECTOR(1024)` 对齐，维度不符整批拒）、重排=`BAAI/bge-reranker-v2-m3`。原计划推荐的 `Pro/moonshotai/Kimi-K2-Instruct` 已在硅基流动下架（403 `Model disabled`），勿再配置
- **启动种子**：`SeedDataRunner` 幂等写 `edudoc_ai_model_profile` 三条（LLM_MAIN / EMB_BGE_M3 / RERANK_BGE_V2）；二次启动日志应显示「模型档案已存在，跳过」
- **测试样本**：一份 READY 文档（先跑 DocumentTest 集合灌入 30 页课件；**样例为 lorem-ipsum 生成体**，对问答天然 adversarial——正式验收建议换真实课件，见「已知债务」）
- **鉴权/幂等口径**：同 DocumentTest——`Authorization: Bearer <token>`、写接口 `Idempotency-Key`（缺失 40001、同 key 重放 40902）；**业务幂等不靠 Idempotency-Key**：同 (版本, 任务类型, 发起人) 存在 QUEUED/RUNNING 任务时直接复用其 taskId（R1 裁决，过滤器语义不变）
- **观测命令**：

  ```bash
  docker exec redis-dev redis-cli XLEN edudoc:stream:document-embed          # 向量化事件数
  docker exec redis-dev redis-cli XPENDING edudoc:stream:document-embed cg-document-embed   # 首列应为 0
  docker exec redis-dev redis-cli XLEN edudoc:stream:ai-task                 # 生成任务事件数
  docker exec redis-dev redis-cli XPENDING edudoc:stream:ai-task cg-ai-task  # 首列应为 0
  # Oracle（docker exec -i oracle-dev sqlplus -s user_cidas/cidas123@//localhost/FREEPDB1）：
  #   SELECT index_status, COUNT(*) FROM edudoc_document_chunk
  #    WHERE document_version_id=HEXTORAW('<versionId>') GROUP BY index_status;   -- 期望全 READY
  #   SELECT COUNT(*) FROM edudoc_chunk_embedding;                                -- =块数
  #   SELECT task_type,status,progress,error_code FROM edudoc_ai_task
  #    ORDER BY create_time DESC FETCH FIRST 10 ROWS ONLY;                        -- 无卡 RUNNING
  #   SELECT action_type,COUNT(*) FROM edudoc_audit_log
  #    WHERE action_type LIKE 'AI_%' OR action_type LIKE 'POLISH_%' GROUP BY action_type;
  ```

- **错误码**：40001 参数 · 40301 权限点 · 40401 不存在/非本人 · 40901 冲突（未就绪/终态/重复决策/版本竞争）· 50201 模型服务失败 · 50301 服务间密钥未配置 · 50401 处理超时

---

## 1. 预览结构树（C-08）

- **Method / URL**：`GET /api/v1/documents/{documentId}/preview?versionId=`（DOCUMENT_READ；versionId 仅允许当前版本，同 02 parse 口径）
- **预期返回**：200，`data={documentId,versionId,versionNo,blocks:[{chunkId,chunkNo,headingPath,pageNo,charStart,charEnd,text}]}`；blocks 按 chunk_no 升序一次全量
- **C-01 偏移不变量**（引用回跳与润色替换的地基）：`char_start/char_end` 为**闭区间**、全文（非空块以 `\n` 连接）密铺——首块 `charStart=0`、相邻块 `charEnd+1=后块 charStart`、`text` 恒等于全文切片、块间连接符归后块（后块 text 以 `\n` 开头）；硬切按 code point，代理对（emoji/生僻字）不切断
- **反例**：他人文档 → 40401；非当前 versionId → 40901；非 hex id → 40401

```bash
curl -s -H "Authorization: Bearer $TT" "http://localhost:8080/api/v1/documents/$DOCID/preview" \
  | head -c 400
```

## 2. 向量化流水线（C-03，无接口，观测为主）

触发方式 = `POST /api/v1/documents/{id}/parse`（重解析）或润色采纳（派生版自动衔接）。链路：解析成功**同事务**追加 outbox `DOCUMENT_EMBED_REQUESTED` → 调度器投 `edudoc:stream:document-embed` → `EmbedWorker`（消费组 `cg-document-embed`，独立线程池）按 version 取全部 PENDING 块 → **25/批**调 ai-service `/v1/embed` → 维度校验（≠1024 → 任务 FAILED(50201)，**该批不落半向量**）→ 单批事务 `MERGE` upsert `edudoc_chunk_embedding`（`TO_VECTOR('[a,b,…]', 1024, FLOAT32)` 字符串绑定，12KB 级实测通过）+ 块置 READY + 进度回写。

- **幂等**：消息重投按 `(chunk_id, model_profile_id)` uk 覆盖写；EMBED 任务行 `idempotency_key=eventId`，重投命中既有行即跳过
- **重解析清理**（冒烟抓出 ORA-02292 后整改）：换块前同事务先删该版本 `edudoc_ai_citation` 引用行与 `edudoc_chunk_embedding` 向量行（两表 FK 无级联）——**重解析会使旧 AI 结果的回跳锚点失效**（结果行保留、citations 清空），属零 DDL 下的既定取舍
- **失败口径**：ai-service 停机/502 → EMBED 任务 FAILED(50201 retryable)，文档仍 READY、块仍 PENDING，重解析即补发新事件重试
- 观测见「环境与前置」；15 块文档实测 3s 级完成

## 3. 摘要任务（C-04）

- **Method / URL**：`POST /api/v1/documents/{documentId}/summaries`（AI_SUMMARY + DOCUMENT_READ）
- **预期返回**：**202** + `data={taskId,status:"QUEUED"}`；创建校验链 = 文档 READY **且当前版本向量全 READY**（否则 40901「文档尚未就绪」——重解析/采纳后向量重建期间即此态，稍候重试）→ 同事务插 `edudoc_ai_task(QUEUED)` + outbox `AI_TASK_REQUESTED`
- **活跃去重（UT-08/R1）**：同 (版本, SUMMARY, 发起人) 存在 QUEUED/RUNNING → 返回**既有 taskId**（202，不新建）；与 Idempotency-Key 的 40902 语义并存——换新幂等键、任务仍活跃时得到的是同一 taskId
- **执行**：`AiTaskWorker`（`cg-ai-task`）CAS 置 RUNNING → 分段 map-reduce（分组 ≤6000 字符，逐组进度 0-100 回写）→ 结果 `edudoc_ai_result(SUMMARY)`：content_text 汇总结论、structured_json `{segments:[{text,chunkIds}],groupCount}`（**chunkIds 已过滤非法引用**）→ 每合法引用一行 `edudoc_ai_citation`（page/char 由分块表回填，quote_text 截 600 字）→ 审计 `AI_TASK_SUCCEEDED`（model/promptVersion/耗时/tokens，**不含正文**）
- **超时**：单任务预算 60s（`edudoc.ai-task.timeout-seconds`），超预算 FAILED(50401)
- **反例**：无 AI_SUMMARY 权限（student 种子有、ops 无）→ 40301；他人文档 → 40401；文档非 READY / 向量未就绪 → 40901

```bash
TASK=$(curl -s -X POST -H "Authorization: Bearer $TT" -H "Idempotency-Key: $(uuidgen)" \
  "http://localhost:8080/api/v1/documents/$DOCID/summaries" | sed -n 's/.*"taskId":"\([^"]*\)".*/\1/p')
```

## 4. 任务查询与取消

- `GET /api/v1/ai-tasks/{taskId}`（DOCUMENT_READ）：200 `data={taskId,documentId,taskType,status,progress,errorCode,result}`；SUCCEEDED 才带 `result{resultId,resultType,contentText,structuredJson,modelName,promptVersion,inputTokens,outputTokens,citations[]}`；**非本人任务 → 40401**
- `POST /api/v1/ai-tasks/{taskId}/cancel`：QUEUED → 204（CANCELED）；RUNNING → 204（表无 cancel_flag 列，**裁决**：直接置 CANCELED，worker 结果写回以 `status='RUNNING'` 为守卫，取消竞争时已生成的结果被丢弃不回写）；终态（SUCCEEDED/FAILED/CANCELED）→ **40901**；非本人 → 40401

```bash
watch -n2 "curl -s -H \"Authorization: Bearer $TT\" http://localhost:8080/api/v1/ai-tasks/$TASK \
  | grep -o '\"status\":\"[A-Z]*\"\|\"progress\":[0-9]*'"
```

## 5. 要点任务（C-05）

- **Method / URL**：`POST /api/v1/documents/{documentId}/keypoints`（AI_KEYPOINT）——复用 §3 编排，task_type=KEYPOINT
- **预期返回**：202 + taskId；SUCCEEDED 后 `structuredJson={keypoints:[{text,chunkId,charStart}],underMin}`：**按引用块 charStart 重排为文档顺序**（模型乱序输出不影响）；非法/空 chunkId 条目**丢弃**；条数 <5 → `underMin=true`（**不判失败**，UI 提示口径）；content_text 为编号清单；citations 与摘要同口径
- **反例**：同 §3（权限点为 AI_KEYPOINT）
- **取消竞态**：创建后立即 cancel——QUEUED→204；已被 worker 领取→RUNNING 置取消、结果丢弃；极快跑完→40901，三者皆合法

## 6. 问答（C-06，同步链路）

- `POST /api/v1/qa/sessions`（AI_QA）body `{documentId,title?}` → **201** `data={sessionId,documentId,title,status:"A"}`；同 (文档,用户) 已有活跃会话 → **复用返回同一 sessionId**（服务层保证，无 uk）；文档非 READY → 40901
- `GET /api/v1/qa/sessions?documentId=` → 本人该文档会话列表（历史回看，≤50）
- `POST /api/v1/qa/sessions/{sessionId}/messages` body `{question}`（**1-1000 字符**，空白 → 40001）→ 同步执行 §3.2.2 算法：embed → `VECTOR_DISTANCE` 版本内 **topK=6** → rerank **take(4)**（RERANK 档案缺失/调用失败 → **降级向量序**，R2）→ `evidenceScore = max(0, 1 - min(distance)/2)`（余弦距离 [0,2] 归一，1105 冒烟校准：同文≈0.94/改写≈0.78/文档外≈0.50-0.59）vs **阈值 0.60**（`edudoc.qa.evidence-threshold` 校准口）→
  - **不足/无命中** → 200 `{status:"REFUSED", answer:"未在文档中找到依据，无法回答该问题。"}`（**拒答是业务成功**；USER+ASSISTANT 双消息落库，status=REFUSED）
  - **足够** → evidence-only 生成（maxTokens=900、提示收敛 150 字）→ 引用硬校验（chunkId ∈ 本证据集，quote 前 20 字符可核对）→ **全部非法 → REFUSED**（UT-10）→ 200 `{status:"READY",answer,citations[{chunkId,pageNo,charStart,charEnd,quoteText}]}`；同事务落 QA 任务行（token 统计）+ `ai_result(ANSWER)` + citations + 双消息（sequenceNo 会话内自增）
  - **连续追问**：服务端自动取会话最近 6 条 READY 消息拼 prompt（前端不传历史，防注入）
- **超时口径（P-05，1105 审查 P1-3 整改后）**：**端到端统一预算** `edudoc.qa.budget-ms=5000` 作 deadline，剩余时间逐段传给 embed/rerank/answer（answer 客户端上限 4.8s、ai-service 侧 4.6s）；预算耗尽或段超时 → ASSISTANT 落 **FAILED** 消息 + HTTP **504**/50401，**不写伪造答案**；50201 同理 502
- **反例**：非本人会话 → 40401；空/超长问题 → 40001；归档会话提问 → 40901

```bash
SID=$(curl -s -X POST -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -d "{\"documentId\":\"$DOCID\"}" \
  http://localhost:8080/api/v1/qa/sessions | sed -n 's/.*"sessionId":"\([^"]*\)".*/\1/p')
curl -s -X POST -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"question":"文档里关于「<预览块片段>」的部分讲了什么？"}' \
  "http://localhost:8080/api/v1/qa/sessions/$SID/messages"
# 拒答验证：{"question":"红烧肉怎么做才好吃？"} → status=REFUSED
```

## 7. 润色（C-07）

- `POST /api/v1/documents/{documentId}/polish`（AI_POLISH）body `{paragraphs:[{chunkId?,text}]}`——text 合计 ≤5000（超限 **40001**）；**只送选中段落**（§4.2，不把整份文档发给模型）；chunkId 仅前端回跳用（表无该列）。同步调 `/v1/polish` → 每段一行 `edudoc_polish_result(PENDING)` → 200 `[{polishId,sourceText,polishedText,decision}]`
- `POST /api/v1/polish/{polishId}/decision` body `{decision,acceptedText?}`：
  - `ACCEPT`（用润色文本）/`PARTIAL`（**必须带 acceptedText**，空 → 40001）→ 单事务「成版五写」：读当前版本文块拼接全文 → **indexOf 替换目标段**（链式采纳基于最新版重放，防并发覆盖；目标段不在当前版本 → **40901**）→ OSS 派生 `.txt`（角色落字典值 **PARSED**，`ck_oss_role` 无 DERIVED，D3 名词仅概念）→ EDIT 新版本（version_no+1）→ current 前移 → 简化解析（按段落成块，跳 Tika）同事务补发向量化 → 200 `{versionId,versionNo,documentStatus}`；并发撞 `uk(document_id,version_no)` → **40901**（非 50001）；审计 `POLISH_APPLIED`
  - `REJECT` → 仅记状态，不动版本
  - `REVERT` → **裁决**（表无 REVERT 决策值，零 DDL）：采纳时被替换的版本 ID 已回写 `polish_result.document_version_id`，撤销=直接回拨至该行（D3 公式，链式/孤儿场景逐行正确）；仅**最近一次未撤销采纳**可撤（否则 40901）、目标已=current（重复撤销）40901、决策回写 PENDING（**还原可重润**）、审计 `POLISH_REVERTED` 与回拨同事务、版本行全保留（不可变红线不破）
- **反例**：非本人行 → 40401；已决策行再决策 → 40901；PENDING 行 REVERT → 40901；文档非 READY → 40901

```bash
# 采纳后核对：preview.versionId 应=新 versionId；REVERT 后应=采纳前 versionId
curl -s -X POST -H "Authorization: Bearer $TT" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -d '{"decision":"ACCEPT"}' \
  "http://localhost:8080/api/v1/polish/$PID/decision"
```

## 8. 结果导出（C-04 最小实现，R6）

- **Method / URL**：`GET /api/v1/ai-results/{resultId}/export?format=docx|txt`（DOCUMENT_READ；**仅本人结果**，越权 40401）
- **预期返回**：docx → 200 `application/vnd.openxmlformats-...document`，`Content-Disposition: attachment; filename*=UTF-8''<标题>.docx`（PK 头、Word 可开：标题+段落+`[引用 n]` 脚注+模型元信息）；txt → `text/plain` UTF-8 正文+引用段；`format` 非法 → **40001**
- **注意**：重解析/再采纳后旧结果的 citations 已被清理（§2），导出只剩正文与元信息，属预期
- `// ponytail`：export 域切片（06）落地后迁出 ai 域

## 9. ai-service 直连（C-09 内部接口）

全部需 `X-Internal-Token: $TOKEN`（缺失 401 / ai-service 未配置 503），`X-Trace-Id` 透传回写。错误响应统一 `{code:MODEL_FAILED|TIMEOUT|BAD_INPUT,message,provider,model,elapsedMs}`，**不回显密钥**（上游错误体中 `sk-*` 串被过滤为 `***`）；429/5xx/超时自动重试一次并透传 `Retry-After`。

| 接口 | Body | 预期 |
|---|---|---|
| `POST /v1/embed` | `{texts:[…]}` | `{model,vectors:[[1024]…]}` 等长对齐；维度不符 → 502 MODEL_FAILED |
| `POST /v1/summarize` | `{chunks:[{chunkId,text}]}` | `{segments:[{text,chunkIds}],model,promptVersion,usage}`；输出坏 JSON 自动重试一次再报 |
| `POST /v1/extract-keypoints` | 同上 | `{keypoints:[{text,chunkId}],…}` 强制 JSON 数组 |
| `POST /v1/answer` | `{question,evidence,history,maxTokens}` | `{answer,citations:[{chunkId,quote}],refused,…}`；证据不足模型自拒 `refused:true` |
| `POST /v1/polish` | `{paragraphs:[…]}` | `{polished:[{text}]}` 等长；**纯文本走中性 system**（不裹 JSON、围栏自动剥离） |
| `POST /v1/rerank` | `{query,documents,topN}` | `{results:[{index,relevanceScore}]}` 分数降序 |

```bash
curl -s -X POST http://localhost:8000/v1/embed -H "X-Internal-Token: $TOKEN" \
  -H 'Content-Type: application/json' -d '{"texts":["探测"]}' | head -c 120
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8000/v1/embed \
  -H 'Content-Type: application/json' -d '{"texts":["x"]}'   # 无 token → 401
```

## 10. 登录链路回归（01 切片不回归即视为损坏）

登录 200 → me 200 → logout 204 → 旧 token 40102。详见 AuthTest.md §1-6。

---

## 建议测试顺序

1. 前置就绪：ai-service `/health`、后端 `/actuator/health`、Redis/Oracle、`EDUDOC_AI_INTERNAL_TOKEN` 两侧一致（§9 无 token 反例先验 401）
2. `10 登录` → 记录 `$TT`；选一篇 READY 文档 `$DOCID`
3. `1 预览` → 核对 C-01 密铺不变量（首块 0、相邻 +1）；记录 versionId 与一段块文本
4. `2 重解析` → 观测 embed Stream 消费、`index_status` 全 READY、`edudoc_chunk_embedding` 行数=块数（**R-A 12KB 绑定首验点**）
5. `3 摘要` → 40901 重试至 202 → `4 轮询` SUCCEEDED（P-04 ≤60s，实测 ~38s）→ 核对 citations 的 page/char 可在预览回跳 → 活跃去重同 taskId → 终态取消 40901
6. `5 要点` →（可选先验取消竞态）→ SUCCEEDED 后核对文档序与 underMin
7. `6 问答` → 有依据问题（片段取自步骤 3 的块文本）READY+引用 / 文档外问题 REFUSED / 消息 sequence 连续；越权与空问题反例
8. `7 润色` → 对照 PENDING → ACCEPT 成版（预览含润色文本、versionNo+1）→ REVERT 回拨 → 重复撤销 40901 → 重润+REJECT 不动版本
9. `8 导出` → docx Word 打开核对、txt 含引用段；越权 40401、坏 format 40001
10. `9 直连` 六接口正反例（注意会消耗模型额度，embed/rerank/polish 各一次即可）

## 已知债务与校准项（执行前必读）

- **P-05 问答 ≤5s vs 真实上游时延**（2026-11-05 裁决暂保留不调）：答案模型已走快档位（Qwen2.5-7B，实测 0.6~0.8s），但共享推理偶发排队 >4.8s → 504+FAILED 消息（契约行为）。复测建议：真实课件样本（lorem-ipsum 样例对问答天然 adversarial）、避开高峰、或用 `EDUDOC_ANSWER_MODEL` 换更快模型
- **重复段落首次匹配**：润色采纳按全文 `indexOf` 首处替换，同文重复段落可能替换错处（表无 chunkId 列的既定债务，样例 PDF 的重复 lorem 段已触发过；C-13 前端按块选择可缓解）
- **重解析使旧引用失效**：§2 清理语义，导出/回看旧结果无 citations 属预期
- **卡 RUNNING 观测**：worker 无 pending 回收（ponytail），进程中途死会留 RUNNING 任务行——用「环境与前置」的 ai_task 查询兜底排查，重解析/重建任务即可恢复
- **向量索引未建**（R8）：topK 走全表 `VECTOR_DISTANCE`，单课程级数据量可接受；上量后按 23ai AI Vector Index 补建

## 回归结果

- **Smoke-1/2（2026-11-05 上午）**：环境侧问题两连——OSS `UserDisable`（账号侧处理）、`Pro/moonshotai/Kimi-K2-Instruct` 上游 403 `Model disabled`（chat 默认改 `deepseek-ai/DeepSeek-V3`，问答另设答案模型位）。
- **Smoke-3/4（2026-11-05 下午，真库+真模型）**：**24/25 通过**。抓出并修复两个真缺陷：① 重解析 ORA-02292——`ai_citation` FK 阻挡删块 → 清理事务先删引用行（`ReparseCleanupTest` 锁顺序）；② 采纳成版 ORA-02290——D3 的 DERIVED 不在 `ck_oss_role` 字典 → 落库角色 PARSED。关键实测：1024 维 12KB 向量串 `TO_VECTOR` 绑定+`MERGE` 覆盖写真库通过（审查 R-A 解除）；解析→向量 READY 3.1s；摘要 SUCCEEDED 38~41s（30 条引用回填）；要点 26~35 条文档序；拒答双路（阈值+模型自拒）；润色采纳 EDIT 成版→撤销回拨全链；docx PK 头/越权 40401。唯一 FAIL = §6 P-05 时延（504 契约正确，校准项见上）。
- 修复后回归：`mvn test` **224/224**、`uv run python -m pytest` **16/16**。

## 1105 审查整改复测（2026-11-05 晚）

按 `docs/review/1105-ai-processing-C01-C09符合性审查报告.md` 逐项整改（P1-1/2/3、P2-1/2/3/4/5、P3-1/2/3/4），整改中冒烟复测又抓出并修复三枚审查未覆盖的真缺陷：

| # | 缺陷（复测实证） | 修复 |
|---|---|---|
| 1 | QA ANSWER 落库消息先于任务行 → 真库 ORA-02291（fk_qa_message_2） | QaTxService 顺序调整 + InOrder 回归锁；READY 路径真机实证通过（引用回填、消息/任务/结果齐） |
| 2 | `evidenceScore=1-min(d)` 对同文原文也低于 0.35 → 全线恒拒（分数定义错）；且分数在 rerank 后子集上算，排序波动翻转拒答判定 | 归一 `max(0,1-d/2)`、阈值 0.60（校准：同文≈0.94/改写≈0.78/文档外≈0.50-0.59）、判定固定于 topK=6 全集 |
| 3 | 撤销 `version_no-1` 弹栈在「撤销→重采纳」孤儿 EDIT 链弹错版本（跨链实测复现） | 采纳时回写被替换版本至 `polish_result.document_version_id`，撤销=D3 原公式逐行回拨 |

- 复测结果：默认配置（QA 预算 5s）冒烟 **24/25**，QA 命中项在 lorem 对抗性样例上于 504 契约路径（预算内 FAILED 双消息、不伪造答案）与模型自拒之间波动；放宽预算至 9s 后 READY+引用真机通过——坐实为上游时延而非代码缺陷，P-05 校准移交 C-14（换真实课件样例）。
- 回归：`mvn test` **239/239**、`pytest` **19/19**。
