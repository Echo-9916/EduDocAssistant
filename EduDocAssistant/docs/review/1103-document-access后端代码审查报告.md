# 1103 文档接入（02 切片 B-01~B-08）后端代码审查与测试报告

> 审查日期：2026-11-03　审查人：Codex（根 Agent，双轴由根 Agent 亲自完成）
> 基线：`main` = `41cf02f`，`HEAD` = `f8cfee0`（分支 `feat/fullstack-document`，10 个提交）
> 范围：`git diff main...HEAD -- EduDocAssistant/backend`，90 个文件、+4053/-32 行
> 规格来源：`docs/tasks/02-document-access.md` B-01~B-08 验收标准、《详细设计说明书》§2.4/§3.1、《概要设计说明书》§2.4/§5.4、`CLAUDE.md` 架构红线
> 结论：**有条件通过**。功能主链路实机全绿，但存在 1 个 P1、4 个 P2 需整改后再推送。

---

## 一、结论摘要

文档接入的核心闭环（上传会话 → STS 直传 → complete 五写建档 → Outbox/Stream → Tika 解析分块 → 列表/详情 → 回收站/恢复/重解析）在**真实 Oracle + 真实 Redis + 真实阿里云 OSS** 上跑通，`mvn test` 125 项全绿，实机回归 118 项经复核后 117 项符合契约。状态机、幂等分层、owner 隔离、LIKE 转义、软删 purge_at、审计副作用、分块字段完整性均与设计一致。

阻断项集中在**错误处理契约**而非业务流程：`GlobalExceptionHandler` 只认 `CidasBusinessException`，导致所有 Spring MVC 框架异常（404/405/400/415/参数类型不匹配）统一塌缩成 `HTTP 500 + 50001`。该问题可被任意客户端触发，且本切片的上一个提交 `9c461aa` 刚刚为它的一个特例（非法 hex documentId）打过局部补丁，根因未动，同类漏洞在 `courseId` 过滤参数上依然存在。

---

## 二、测试执行结果（实测，非引用）

### 2.1 自动化单测

| 项目 | 命令 | 实际结果 |
|---|---|---|
| 后端全量单测 | `backend/` 下 `mvn test` | **125 通过，0 failure，0 error，0 skipped** |
| 真库集成测试 | `RbacLifecycleIntegrationTest` | **Tests run: 0** —— 未注入 Oracle 凭据时被 assumption 静默跳过 |

125 项按类分布已逐类核对（`target/surefire-reports`）：文档域 6 个测试类共 52 项（CompleteUploadTest 12、DocumentLifecycleServiceTest 12、UploadSessionServiceImplTest 12、DocumentQueryServiceTest 7、DocumentParseWorkerTest 5、DocumentProvisioningServiceImplTest 4），领域层 ChunkingServiceTest 5、TikaTextExtractorTest 2，基础设施 OssStorageAdapterTest 2、OutboxDispatcherTest 6、RedisIdempotencyStoreTest 3。

断言密度抽样（`@Test` 数 vs `assert*/verify/assertThrows` 数）：DocumentQueryServiceTest 7/21、DocumentLifecycleServiceTest 12/26、CompleteUploadTest 12/37。非"调用即通过"的空壳测试。

> **注意**：`mvn test` 报 125 全绿，但真库集成测试实为 0 项。上一轮复盘已记录同一问题（P3 可观测性），本切片仍未修，容易让人误以为真库路径被覆盖。

### 2.2 实机端到端回归

环境：`oracle-dev`（FREEPDB1）+ `redis-dev`（v8.10.1）+ 真实阿里云 OSS（`cidas-oss-bucket` / cn-beijing / RAM 角色 `edudoc-upload`）。为拿到完整日志，另起 8081 实例（审查结束后已停止，用户原有 8080 实例未动）。测试脚本自研 PowerShell 工具链：手写 xref 的真 PDF 生成器、OOXML docx 生成器、OSS V1 签名直传（`HMAC-SHA1` + `x-oss-security-token`）。

首轮结果：**回归项 118，PASS 107，FAIL 11；缺陷探针 8，确认缺陷 8**。

对 11 项 FAIL 逐条复核后定性如下——**这一点必须区分清楚，否则会把测试脚本的错当成产品的错**：

| 首轮 FAIL 项 | 复核结论 | 依据 |
|---|---|---|
| `CP-40901N` / `CP-REOPEN2` | **脚本缺陷**，产品正确 | 脚本的 `Send-Document -DoComplete $false` 仍执行了 OSS PUT，对象其实已上传。重做"完全不上传"场景：`complete → 409/40901 文件尚未上传完成或大小不符`，会话回置 `OPEN`，符合 B-04 ② |
| `PARSE-PARSING40901` | **脚本竞态**，产品正确 | 重解析 0.8s 即完成，首次断言落在 READY 窗口。确定性复测（先确认状态为 `PARSING` 再调）：`409/40901 当前状态不允许重新解析`，符合 B-08 |
| `DB-VERSION` / `DB-OSSOBJ` / `DB-CHUNKNO` | **脚本 SQL 列名写错**，产品正确 | 误用 `change_type`/`kind`（实际为 `version_type`/`object_role`）。改正后：`VERSION_NO=1, VERSION_TYPE=UPLOAD, VERSION_STATUS=A, OBJECT_ROLE=ORIGINAL, SIZE_BYTES=830, MIME=application/pdf`；`chunk_no MIN=1 MAX=1 CNT=1` |
| `DOCX-HEADING` | **脚本缺陷**，产品正确 | 手写 docx 缺 `word/_rels/document.xml.rels`，Tika 无法解析样式表 → 无标题。单测 `TikaTextExtractorTest` 用 POI 生成（自带 rels），heading 栈断言通过 |
| `JUNK-FAILED` / `JUNK-AUDIT` / `JUNK-REFAIL` | **脚本样本选错**，FAILED 链路正确；但暴露新缺陷 D5 | 我的"损坏文件"是无 `%PDF` 头的纯文本，Tika 自动识别为 text/plain 正常提取 → READY。改用 `%PDF-1.4` 头 + 损坏结构：**0.7s 转 FAILED，审计 `PARSE_FAILED / result_code=42201`，chunkCount=0**，与 B-06 完全一致 |
| `UP-REDISJSON` | **真实发现**（D6） | Redis 会话 JSON 字段名与 B-03 验收标准列举的契约字段集不一致 |

复核后有效结论：**回归项 117/118 符合契约，1 项为契约漂移（D6）；缺陷探针 8/8 全部确认为真实缺陷。**

补充的定向探针（均实测复现）：

| 探针 | 实测结果 |
|---|---|
| `GET /documents?courseId=y` | **HTTP 500 / 50001**（`courseId=0000…0` 合法 hex 则 200） |
| `GET /documents?page=abc` / `pageSize=abc` | **HTTP 500 / 50001** |
| `GET /documents?page=2147483647&pageSize=2` | **HTTP 200，返回第 1 页的 2 条数据，`page` 字段回显 2147483647**（`page=999999` 正确返回 0 条） |
| teacher 占用某 `Idempotency-Key` 后 student 复用同 key | **student 得到 409/40902**（key 全局共享，未按用户/端点隔离） |
| 无 `%PDF` 头的纯文本改名 `.pdf` 上传 | **被接受，解析成功转 READY** |
| `GET /api/v1/definitely-not-a-route`、`GET /documents/`、`DELETE /courses`、畸形 JSON body、`etag` 传数组、`Content-Type: text/plain` | **6/6 全部 HTTP 500 / 50001** |

### 2.3 实机验证通过的关键项（节选）

STS 直传 PUT 200；`STS.` 前缀证明下发的是临时凭据而非长期 AK；objectKey 格式与危险文件名清洗（`..\..\evil name?.pdf` → `evil_name_.pdf`）；Redis 会话 TTL=7200s；41301/41501/40001/40301 全部反例；20MB 边界值不误报；`.pdf.exe` 双扩展名拦截；`.doc` 空 mimeType 按扩展名放行；hash 不符 → 40901 且会话回 OPEN；同 key 重放 40902、换新 key 重放返回同一组 id 且 `edudoc_document` 只有 1 行；同名 40901 文案与 2026-11-02 决策一致；不同 owner 同标题允许（唯一键含 owner）；PDF 2 页 **0s 转 READY**（P-04 预算 60s）、`page_no` 已填充；学生无课程绑定文档同样解析成功；`XPENDING` 首列=0、outbox 19 条全 `PUBLISHED` 无 NEW/FAILED 残留；列表 11 字段齐全、`update_time DESC`、owner 隔离、courseName/sizeBytes/mimeType join 正确、`%`/`_` LIKE 转义、pageSize 钳制 1..100、负数与 0 钳制、分页不重叠；详情非 owner → 40401 不泄露存在性、非 hex id → 40001；软删 204 + purge_at 恰为 +30 天 + 回收站可见 + 恢复后 `READY` 且 deletedAt/purgeAt 清空 + 重复删除/恢复 40901；重解析 taskId 等于 outbox eventId、非当前版本 40901、当前版本 200、重解析后 chunkCount 不翻倍；审计 `UPLOAD_COMPLETED / PARSE_SUCCEEDED / PARSE_REQUESTED / DOCUMENT_DELETED / DOCUMENT_RESTORED` 全部落库且 result_code 正确；01 切片登录链路（me/logout/旧 token 40102）无回归。

---

## 三、Standards 轴（是否符合仓库既定规范 + Fowler 坏味道基线）

> 说明：`code-review` Skill 要求双轴由两个 fresh 子代理并行执行。本轮两个子代理（`standards_axis` / `spec_axis`）均在派发后立即失败，报 `unsupported_encrypted_agent_content`（Chat Completions 上游无法处理加密 agent 消息）。这与 `10-Lessons/2026-11-02-fullstack-auth复审` 记录的现象完全一致，属环境限制而非本轮操作失误。**两轴均由根 Agent 亲自完成，未合并、未互相重排序。**

### S1【P1】全局异常处理器缺少 Spring MVC 框架异常分支

`common/api/GlobalExceptionHandler.java:15-31` 只有两个 handler：`CidasBusinessException` 与兜底 `Exception`。任何在业务代码之前抛出的框架异常都落进 `handleUnknown`，被记为 `ERROR 未捕获异常` 并返回 `500/50001`。

实测复现 8 种输入（见 2.2 表）：未匹配路由应 404、`GET /documents/` 应 4xx、`DELETE /courses` 应 405、畸形 JSON 应 400、`etag` 传数组应 400、`Content-Type: text/plain` 应 415、`page=abc` 应 400、`courseId=y` 应 400 —— 全部返回 `HTTP 500 + code 50001`。

违反的既定标准：`ErrorCode` 的类注释写明"业务 code 与 HTTP 状态一一对应"，《详细设计说明书》§5.1 错误码契约同样如此约定；`CLAUDE.md` 要求交付物对齐设计文档术语与契约。

危害不止于语义错误：客户端可稳定触发 5xx，会污染错误率指标与告警、掩盖真实故障，且 `handleUnknown` 走 `log.error` 打全栈，可被用于放大日志量。

建议：补一组 handler，`ErrorCode` 已有现成码位，无需新增枚举——

```java
@ExceptionHandler(NoResourceFoundException.class)            // → NOT_FOUND 40401 / 404
@ExceptionHandler(HttpRequestMethodNotSupportedException.class) // → 405（建议新增 METHOD_NOT_ALLOWED 码位）
@ExceptionHandler(HttpMediaTypeNotSupportedException.class)    // → UNSUPPORTED_MEDIA_TYPE 41501 / 415
@ExceptionHandler({HttpMessageNotReadableException.class,
                   MethodArgumentTypeMismatchException.class,
                   MissingServletRequestParameterException.class,
                   MethodArgumentNotValidException.class})      // → BAD_REQUEST 40001 / 400
```

统一按 `log.warn` 记录（客户端错误不应是 ERROR 级），并补 `GlobalExceptionHandlerTest` 对应用例。

### S2【P2】`9c461aa` 的修复是症状级，同类根因在 `courseId` 上仍在

提交 `9c461aa` 的说明是"修复非法 hex ID 返回 50001→40001"，做法是在详情/删除/恢复/重解析四个入口加 `Ids.isHex16` 守卫（`DocumentQueryServiceImpl.java:42`、`DocumentLifecycleServiceImpl.java:98`）。但**列表接口的 `courseId` 过滤参数没有守卫**：`DocumentController.java:38` 直接把原始字符串传下去，`DocumentMapper.xml` 的 `listWhere` 用 `HEXTORAW(#{courseId})` 拼进 SQL，非法 hex 触发 Oracle 异常 → 500/50001（实测 `courseId=y`、`courseId=ZZZZ` 均复现）。

这是 S1 的一个具体实例，但值得单列：它说明"逐个入口加守卫"的策略天然漏项。修好 S1 之后仍建议在 `DocumentQueryServiceImpl.list` 入口对 `courseId` 做 `Ids.isHex16` 校验并返回 40001，语义比笼统的 400 更清楚。

### S3【P2】分页 offset 整型溢出，返回错误数据而非报错

`DocumentQueryServiceImpl.java:34`：`(page - 1) * pageSize`，`page` 与 `pageSize` 都是 `int`，`DocumentRepository.page(...)` 的 `offset` 形参也是 `int`。`page` 只做下界钳制（`< 1 → 1`）无上界。

实测 `page=2147483647&pageSize=2`：`(2147483646 * 2)` 溢出为 `-4`，Oracle 将其按 0 处理，接口返回**第 1 页的 2 条数据**，同时响应里 `page` 字段回显 `2147483647`。`page=999999` 则正确返回 0 条。即同一个接口对超大页码一会儿返回空、一会儿返回首页数据，且状态码都是 200。

静默返回错数据比抛错更危险。建议 offset 用 `long` 计算，并对 `page` 加上界（或直接 `long offset = (long)(page-1)*pageSize; if (offset > Integer.MAX_VALUE) return 空页`）。

### S4【P2】幂等键未按用户与端点隔离，跨用户互相顶掉

`RedisIdempotencyStore.java:21`：`setIfAbsent(PREFIX + key, "1", TTL)`，键空间只有 `edudoc:idem:{key}`。`IdempotencyFilter.java:37` 据此判定重复。

实测：teacher 先用 `Idempotency-Key: SHARED-KEY-COLLISION-TEST-001` 创建会话得 201，随后 student 用**同一个 key** 调同一接口 → `409/40902 重复提交或幂等键冲突`，student 的合法请求被拒。

后果：任何使用确定性键的客户端（例如按业务序号生成 `upload-1`）会跨用户互相干扰；恶意用户可预先占用常见键，对其他用户形成低成本拒绝服务。业界惯例（Stripe、IETF 幂等键草案）都把键限定在"认证主体 + 路由"范围内。

建议把键改为 `edudoc:idem:{userId}:{method}:{path}:{key}`，或至少加 `userId`。注意这属跨切片公共组件（`common/api`），改动会同时影响 auth 域，需回归 `IdempotencyFilterTest`。

### S5【P2】只校验客户端申报的类型，不校验真实内容；Tika 无解析上限

`UploadSessionServiceImpl.java:170-182` 的 `requireSupportedFile` 校验的是请求体里的 `fileName` 扩展名与 `mimeType` 字段，`complete` 阶段只校验对象大小与服务端实算 SHA-256（`UploadSessionServiceImpl.java:96-113`）。**全流程没有任何一处比对 Tika 实际探测到的内容类型。**

实测：把纯文本字节命名为 `broken.pdf`、`mimeType=application/pdf` 上传，会话创建通过、直传通过、complete 建档成功、解析 **READY**、分块正常落库。也就是说"仅支持 Word/PDF"这条规则只对客户端自报的元数据生效。

配套问题：`TikaTextExtractor.java:27` 直接 `new AutoDetectParser()`，未配置 `TikaConfig`，即没有 `writeLimit`、`maxEmbeddedDepth`、容器爆炸防护。对不可信输入用默认配置跑 `AutoDetectParser`，遇到嵌套容器/zip 炸弹类样本时缺少资源上限保护；虽然 20MB 上限与临时文件落盘（`DocumentParseWorker.java:172`，避免堆内膨胀，这点做得对）降低了影响，但纵深不足。

建议：解析时读取 `Metadata.CONTENT_TYPE`，与允许集合（`application/pdf`、`application/msword`、OOXML wordprocessing）比对，不符则按 B-06 既有路径置 FAILED 并落审计（可复用 42201 或 41501 语义）；同时为 Tika 配置显式 `TikaConfig` 上限。

### S6【P3】OSS/STS 异常原文直接回传客户端

`OssStorageAdapter.java:48/66/83/94` 四处把 `e.getMessage()` 拼进面向客户端的 50301 文案。阿里云 SDK 的异常消息常含 endpoint、RoleArn、RequestId、错误码等内部信息，等于向已认证用户暴露基础设施细节。建议客户端只回固定文案，原文仅进日志（已有 `GlobalExceptionHandler` 的日志链路可承接）。

### S7【P3】审计明细序列化失败被静默吞掉

`AuditServiceImpl.java:37-39`：`catch (JsonProcessingException e) { return null; }`。审计行的 `detail_json` 会静默变成 NULL，而审计恰恰是最需要"写不进去就要吵"的数据。同时 `AuthServiceImpl` 侧 `AuthContext.get()` 为 null 时 `actor_id` 落 NULL —— 解析 worker 是后台线程无主体，因此 `PARSE_SUCCEEDED/PARSE_FAILED` 全部无操作者。建议至少 `log.warn` 一次，并为后台任务引入 `SYSTEM` 之类的固定 actor。

### S8【P3】`parts` 字段声明"透传留存"但实际被丢弃

`CompleteUploadRequest.java:5-8` 定义了 `List<Part> parts`，Javadoc 写"parts 仅透传留存"。但 `UploadController.java:48-49` 只取 `request.etag()` 与 `request.contentHash()` 构造 `CompleteCommand`，`parts` 从未传递、从未落库。分片清单没有任何留存，注释与实现不符。B-10 的断点续传依赖 `checkpoint.uploadedParts` 传给 complete，这条链路目前是断的。建议要么真的存下来（`edudoc_oss_object` 或版本表），要么删掉字段并修正注释，别留一个会误导前端接线（B-09/B-10 尚未开工）的假契约。

### S9【P3】`JSON_SERIALIZE(… RETURNING VARCHAR2(4000))` 是隐性上限

`OutboxEventMapper.xml:14`。当前 payload 约 200 字节，安全；一旦某个事件类型的负载超过 4000 字节，读取端会直接抛 ORA-40478 而非截断，调度器整批投递失败。属"现在没事、将来炸"的埋伏，建议加注释标注上限并考虑 `CLOB` 返回。

### S10【P3】`ChunkingService` 两处边界细节

`ChunkingService.java:73-83` 的 `hardSplit` 以 `maxChars` 切 `String.substring`，即按 UTF-16 code unit 切分。BMP 内中文安全，但遇到代理对（emoji、部分生僻字）可能切出孤立代理项，进而影响 CLOB 写入与后续 JSON 序列化。另外 `chunk()` 在硬切片之间会 `append('\n')`（第 51-53 行），而这个换行没有计入 `blockStart`/`localFrom` 的偏移累加，因此当单段被硬切且跨块时，`content` 与 `[char_start, char_end)` 指向的全文切片会有细微漂移。当前样本未触发，属数据保真度隐患，ai 域用 offset 做引用定位时会被放大。

### S11【P3】编码风格与一致性小项

- `DocumentQueryServiceImpl.java:42` 用全限定名 `com.cidas.edudoc.common.persistence.Ids.isHex16(...)`，而同项目 `DocumentLifecycleServiceImpl` 是正常 import。同一工具类两种写法，建议统一为 import。
- objectKey 大小写不一致：`Ids.hex16()`（`common/persistence/Ids.java:11`）显式 `toUpperCase()`，而 `UploadSessionServiceImpl.java:57` 的 sessionId 用 `UUID.randomUUID().toString().replace("-","")` 是小写。实测 objectKey 为 `edudoc/uploads/202611/5F2F10AD…034B/32d5dce8…7b66-evil_name_.pdf`，同一路径里两段 hex 一大一小。功能无碍（比对均走 `equalsIgnoreCase`），但控制台浏览与日志检索时观感割裂，建议 sessionId 也走 `Ids.hex16()`。
- `DocumentMapper.xml` 的 `softDelete` 带 `AND status != 'DELETED'`，而 service 层 `delete()` 已先判过一次；`restore` 的 `AND status = 'DELETED'` 则被 service 用于乐观并发（返回 0 行即抛 40901），这处是有意义的。前者属冗余防御，可留可删。

### Standards 轴小结

共 11 项：1 个 P1、4 个 P2、6 个 P3。轴内最严重的是 **S1（框架异常全部塌缩为 500/50001）**——它是 S2 的根因，8 种输入实测复现，客户端可稳定触发，且上一提交刚为它的一个特例打过局部补丁。
架构红线合规性良好：分层严格（Controller 仅委托，Mapper/OSS 客户端只在 infrastructure）、长任务不在请求线程（Stream + 独立线程池）、Outbox 与业务写入同事务、私有 Bucket + 短期 STS（实测下发 `STS.` 前缀临时凭据）、写接口强制 `Idempotency-Key`、版本不可变（version_no=1/UPLOAD）、构造器注入统一走 `@RequiredArgsConstructor`、SQL 全部落 mapper.xml 且**无一处 `${}` 拼接**、`// ponytail:` 债务标注规范（单节点轮询、无 DLQ、OSS 客户端不 shutdown 均已标注）。`git diff --check main...HEAD` 退出码 0。

---

## 四、Spec 轴（是否忠实实现 `02-document-access.md` B-01~B-08）

### P1【P3】B-03 会话 JSON 字段名与验收标准列举的契约不符

验收标准原文：value JSON `{sessionId,ownerUserIdHex,courseIdHex|null,title,docType,fileName,sizeBytes,mimeType,objectKey,status:OPEN|COMPLETING|COMPLETED,completedDocumentIdHex|null,completedVersionIdHex|null,createdAt}`。

实测 Redis 里的 JSON（`docker exec redis-dev redis-cli GET edudoc:upsession:{sid}`）字段名为 `id`（非 `sessionId`）、`ownerUserId`（非 `ownerUserIdHex`）、`courseId`（非 `courseIdHex`）、`documentType`（非 `docType`）。原因是 `UploadSession.java:6` 的 record 直接由 Jackson 按组件名序列化，未加 `@JsonProperty` 对齐契约。

影响面：Redis 会话是内部态、只被 `UploadSessionRepository` 读写，功能无碍（状态机、CAS、TTL、重放全部实测正确）。但任务文档明确列举了字段名，`DocumentTest.md` 也未记录这一差异，后续按文档排障或写运维脚本的人会踩空。建议二选一：加 `@JsonProperty` 对齐文档，或在任务文档/`DocumentTest.md` 里把实际字段名回填为事实契约。

### P2【P3】B-05 Stream 消息字段与验收标准不符

验收标准原文：消息字段 `{eventId,eventType,taskId,documentIdHex,versionIdHex,schemaVersion=1}`。

实际 `OutboxDispatcher.java:47-53` 投递的字段是 `{eventId, eventType, taskId, aggregateType, aggregateRef, schemaVersion, payload}`——**没有 `documentIdHex`/`versionIdHex` 两个顶层字段**；versionId 由 `aggregateRef` 承载，documentId 与 objectKey 藏在 `payload` JSON 里。`DocumentParseWorker.java:139-145` 正是按这个实际形状读的（`value.get("aggregateRef")` 当 versionId、`payload.documentId`、`payload.objectKey`），所以当前自洽。

风险在于这是**跨域契约**：ai 域将来要消费同一条 stream，若按任务文档的字段名接线会直接拿到 null。`taskId = eventId hex` 这条已正确实现（实测 `PARSE-TASKID` 通过，taskId 与 `edudoc_outbox_event.id` 的 RAWTOHEX 一致）。建议把实际字段形状回填进任务文档与《详细设计说明书》，或补齐文档要求的两个冗余顶层字段。

### P3【P3】B-06 消费机制与验收标准指定的实现方式不同

验收标准原文：「Stream 消费：`StreamMessageListenerContainer`，group `cg-document-parse`（启动 `XGROUP CREATE … MKSTREAM` 幂等），consumer 名 `parse-{hostname}-{pid}`」，且"新建：… `document.infrastructure.{RedisStreamConfig,…}`"。

实际：全仓 `StreamMessageListenerContainer` 引用数为 **0**，`RedisStreamConfig.java` **不存在**。`DocumentParseWorker.java:62-77` 改为自建 `Executors.newFixedThreadPool(concurrency)` + `consumeLoop` 阻塞式 `XREADGROUP`（`block(2s)`、`count(1)`、`ReadOffset.lastConsumed()`）。consumer 名是 `parse-{host}-{index}`（`DocumentParseWorker.java:75`，index 为 0..concurrency-1），**不含 pid**。

功能等价性我已实测确认：group 幂等创建正确处理了 BUSYGROUP（`isBusyGroup` 沿 cause 链判定，规避 Spring 把 `RedisBusyException` 包成 `RedisSystemException` 的坑，这点做得很扎实）、并发数取 `edudoc.parse.concurrency`、daemon 线程池不占请求线程、`@PreDestroy` 置 `running=false` 并 `shutdownNow`、成功与失败都 XACK（实测 `XPENDING` 首列=0）。

但两点需记录：①consumer 名不含 pid，同主机跑两个 JVM 实例时 consumer 名会撞车，pending 归属混淆；②设计文档指定的组件没建，后续接手者按文档找不到 `RedisStreamConfig` 会困惑。属实现选型偏离而非功能缺陷，建议回填文档说明选型理由（自建循环比 listener container 更容易控制 XACK 时机与异常兜底），并把 pid 补进 consumer 名。

### P4【P3】B-06 "否则先置 PARSING" 未在 worker 内实现

验收标准原文：「去重：目标 version 已 READY 且事件非「重新解析」→ 跳过直接 XACK；否则先置 PARSING。」

实际 `DocumentParseWorker.java:146-152` 的判定是：文档不存在 → 丢弃；`status != PARSING || versionId != currentVersionId` → 记日志跳过；否则 `process(...)`。worker **不会**把状态置为 PARSING，而是要求生产方先置好（`DocumentProvisioningServiceImpl.java:44` 建档即写 `"PARSING"`；`DocumentLifecycleServiceImpl.java:88` 重解析先 `updateStatus(documentId,"PARSING")`）。

我认为**实现优于文档**：状态只有一个写入方，避免了 worker 与接口层争抢。但这确实与验收标准的字面描述不同，且带来一个副作用——若事件到达时文档已不是 PARSING（例如重复投递、或人工改了状态），worker 会静默跳过并 XACK，事件被消费掉而文档状态不变。建议把这条口径回填文档，明确"PARSING 由生产方负责，worker 只做状态守卫"。

### P5【P3】B-06 失败回写缺少状态守卫

`DocumentParseTxServiceImpl.java:38-43` 的 `writeFailure` 无条件 `updateStatus(documentId,"FAILED")`，不校验文档当前是否仍为 PARSING、versionId 是否仍是当前版本。`writeSuccess`（同文件 27-36 行）同样无条件置 READY。在迟到事件或并发重解析的场景下，一个旧的失败回写可能覆盖掉已经成功的状态。窗口很小（worker 入口有守卫、单机 concurrency=2），但既然 `DocumentMapper.xml` 的 `restore` 已经用了 `AND status = 'DELETED'` 这种条件更新范式，这里建议同样加 `AND status = 'PARSING'` 保持一致。

### P6【P3】`DocumentTest.md` 声称的一个反例无法用种子数据触发

`docs/test/DocumentTest.md` §5 反例表写「无 DOCUMENT_READ 权限 → 40301」。但 `04_seed.sql:33-35` 给 STUDENT、TEACHER、OPS **三个角色都授予了 `DOCUMENT_READ`**（实测 `/auth/me` 确认 OPS 权限集含 `DOCUMENT_READ`）。因此该反例在当前种子下无法复现——实测 OPS 调 `GET /documents` 返回 200 且 `total=0`（owner 强制隔离生效，行为正确）。

同理 §7 的权限矩阵里，`POST /{id}/parse` 要求 `DOCUMENT_EDIT`，OPS 没有该权限，这个反例是可触发的。建议把文档里不可触发的反例改为说明"需临时收回权限验证"，或换成可触发的 OPS + parse 组合，避免测试文档给出无法执行的步骤。

### Spec 轴小结：已逐条核对且**符合**验收标准的部分

为避免只报问题造成偏差，以下均经实测或读码确认与验收标准一致：

- **B-01**：`aliyun-sdk-oss 3.18.2`、`sts20150401 1.1.7`、`tika-core` + `tika-parsers-standard-package` 3.3.1、`spring-boot-starter-data-redis` 版本与文档锁定值逐一相符；`application.yml` 的 `edudoc.oss.*`（含 `object-key-prefix=edudoc/uploads`、`session-ttl-minutes=120`、`sts-duration-seconds=900`）、`edudoc.outbox.*`、`edudoc.parse.*` 键与默认值齐全；`OssProperties` 位于 `infrastructure.oss` 且用 `@ConfigurationProperties`；`RedisIdempotencyStore` 用 `setIfAbsent(key,"1",Duration.ofDays(1))` 等价 `SET NX EX 86400`，`InMemoryIdempotencyStore` 已删除，`IdempotencyFilter` 依赖 `IdempotencyStore` 端口。
- **B-02**：种子课程按 course_code+term_code 幂等（实测重启日志"种子课程已存在，跳过"）；`GET /courses` 仅需认证、返回 5 字段、teacher 2 门 student 1 门、memberRole 正确；`loadPrincipal` 的 courseScope 已填充并生效（student 绑 MATH201 → 40301）。
- **B-03**：`objectKey` 格式 `{prefix}/{yyyyMM}/{userIdHex}/{sessionId}-{清洗名}` 实测吻合；文件名清洗去掉路径分隔符/控制字符/空格（`..\..\evil name?.pdf` → `evil_name_.pdf`）；STS inline policy 的 Action 恰为规定的 6 个、Resource 精确到单 objectKey、DurationSeconds=900；size>20MB→41301、扩展名+MIME 双重校验→41501、教师必须绑课程→40001、非成员课程→40301、标题 1..200→40001 全部实测通过；Redis key/TTL/状态机 OPEN|COMPLETING|COMPLETED 正确（字段名见 P1）。
- **B-04**：五步伪代码顺序逐条落地——Lua CAS `OPEN→COMPLETING`（`UploadSessionRepository.java:26-40`，值比较交换，实现正确）、COMPLETED 重放原 id、COMPLETING 走 `recoverOrConflict` 反查 `oss_object` uk 重放、headObject 缺失或 size 不符→40901 且回置 OPEN（实测复现）、服务端流式实算 SHA-256 不信任上报值（`sha256Of` 8KB 缓冲流式，try-with-resources 双资源）、单个 `@Transactional` 内完成 oss_object(ORIGINAL)→document(PARSING)→version(1/UPLOAD/source_object_id)→updateCurrentVersion→outbox→审计（实测 DB 字段全部吻合）、提交后回填 COMPLETED；`DuplicateKeyException`→40901 且文案与 2026-11-02 决策一致（实测）。特别核对了 CAS 的期望值传递链：`verifyAndProvision(locked, …)` 收到的是 COMPLETING 态对象，第二次 CAS 以 COMPLETING 为期望值，不存在"拿陈旧 OPEN 态做比较导致回填失败"的问题。
- **B-05**：`selectPublishable` 的谓词 `status='NEW' AND (next_retry_at IS NULL OR next_retry_at <= SYSTIMESTAMP) ORDER BY create_time FETCH FIRST #{batchSize} ROWS ONLY` 与验收标准逐字对应；退避 `Math.min(600, 10L << retryCount)` 即 10·2ⁿ 秒封顶 600s；超 `max-retry` 置 FAILED；无路由置 FAILED；`enabled=false` 空转；路由表走 `edudoc.outbox.stream-keys`；`markPublished/markRetry/markFailed` 均带 `AND status='NEW'` 守卫，状态迁移幂等；`CAST(#{payloadJson} AS JSON)` 保证 IS JSON 合法（实测 `JSON_VALUE` 可读）。
- **B-06**：解析落临时文件防堆内膨胀、`finally` 里 `Files.deleteIfExists`；SAX handler 维护 h1-h6 标题栈（置位后清空更深层级，避免兄弟标题污染）、`div.page` 与 `\f` 双路维护 page_no；TikaException/空文档→FAILED 且审计落 42201（实测 `%PDF` 头损坏样本 0.7s 转 FAILED）；分块规则全中——段落累积至 chunk-size 在边界切、单段超限硬切、chunk_no 从 1 递增、heading_path 截 1000 且允许为空、char_start/char_end 为全文偏移、`token_count=ceil(去空白字符数/2)`、`content_hash=SHA-256(块文本)`、`index_status='PENDING'`；单事务内"删旧块 + 50/批插新（`jdbcType=CLOB`）+ 置 READY"（`DocumentChunkRepository.java:13-21` BATCH_SIZE=50）；成功/失败均 XACK。
- **B-07**：`@RequirePermission(DOCUMENT_READ)`；六个参数齐全；`page` 1 起、`pageSize` 默认 20 上限 100（负数与 0 均钳制，实测）；`ORDER BY d.update_time DESC`；`listWhere` 首条即 `d.owner_id = HEXTORAW(#{ownerId})` 强制 owner；默认 `status != 'DELETED'`、显式传 DELETED 才查回收站；keyword `UPPER(...) LIKE '%'||UPPER(#{keyword})||'%' ESCAPE '\'` 且 Java 侧按 `\`→`%`→`_` 顺序转义（实测 `%`、`_` 均不匹配全部）；Oracle `OFFSET … FETCH NEXT`；Summary 11 字段、sizeBytes/mimeType/courseName 经 current_version→oss_object→course 三跳 join（实测填充正确）；详情非 owner→40401 不泄露存在性；Detail 含 currentVersionId/versionNo/contentHash/chunkCount/deletedAt/purgeAt。要求的 6 个测试用例在 `DocumentQueryServiceTest`（7 项）中均有对应。
- **B-08**：软删置 DELETED + `purge_at = SYSTIMESTAMP + NUMTODSINTERVAL(30,'DAY')`（实测 days=30）、不动 OSS 对象、审计带 `prevStatus`；已删再删 40901；恢复按 `countByVersion > 0` 推断 READY/FAILED（实测 READY）、撞同名活跃文档由 `DuplicateKeyException` → 40901、`restore` 返回 0 行时抛"状态已变化"40901；重解析仅 `Set.of("READY","FAILED")` 放行（PARSING/UPLOADING/DELETED → 40901，实测确定性复现）、`taskId` = outbox eventId（实测）、versionId 仅允许当前版本（实测 40901）、源对象缺失 40901。要求的 10 个测试用例在 `DocumentLifecycleServiceTest`（12 项）中均有对应。
- **排除项未被越界实现**：版本列表/对比/恢复、预览页与检索高亮、向量化（`index_status` 一律 PENDING）、PATCH 重命名、运维端回收站页面、课程共享文档与 ACL、时间范围筛选——均未出现在 diff 中。**零 DDL 得到确认**：diff 内无任何 schema 变更，所有表/列均沿用既有 38 表（我逐一核对了代码依赖的列名与 `user_tab_columns` 实际定义）。

Spec 轴共 6 项发现，全部为 P3 级契约/文档漂移或实现选型偏离，**无功能性缺失、无越界实现**。轴内最严重的是 **P2（Stream 消息字段与文档不符）**——它是 ai 域将来要对接的跨域契约，按文档接线会拿到 null。

---

## 五、整改建议与放行判断

**建议推送前必修（P1/P2）**

1. **S1** 补齐 `GlobalExceptionHandler` 的框架异常分支（404/405/400/415），并顺带覆盖 S2 的 `courseId` 校验。这一项修完，本次实测的 8 个 500 全部消失，且 `9c461aa` 那类"逐入口打补丁"的做法可以停止。
2. **S3** 分页 offset 改 `long` 或加 `page` 上界——当前会对超大页码静默返回首页数据。
3. **S4** 幂等键加用户/端点维度。注意它在 `common/api`，会同时影响 auth 域，需一并回归。
4. **S5** 解析前比对 Tika 探测到的内容类型，并给 `AutoDetectParser` 配置显式上限。

**可与 B-09/B-10 前端接线一并处理（P3）**

S8（`parts` 假契约，直接影响 B-10 断点续传接线）、P1/P2（Redis 会话与 Stream 消息字段名回填文档，影响 B-09 类型定义与 ai 域对接）、S6（异常原文外泄）、S7（审计明细静默丢失）、P3/P4/P5（消费机制选型与状态守卫口径回填）、S9~S11、P6、以及 `RbacLifecycleIntegrationTest` 在默认 `mvn test` 下静默跑 0 项的可观测性问题（上一轮已记录，仍未修）。

**放行判断**：业务流程与数据正确性已达到可继续推进 B-09~B-13 的水平，主链路无需返工。但 S1 属对外契约破坏且客户端可稳定触发，S3 会静默返回错数据，S4 存在跨用户干扰，建议这四项在合并回 `main` 与远程推送之前修掉；`CLAUDE.md` 的"阶段任务全部完成（通过测试、审查、复审）后推送"口径下，当前状态为**审查未通过、待整改复审**。

---

## 六、审查过程与限制说明（如实记录）

- **经验检索**：已调用 `$agent-knowledge-recall` 流程，Vault `C:\Users\admin\Temp\ObsidianAgentKnowledgeLoop` 访问成功。实读 `00-Index/Knowledge-Index.md`、`10-Lessons/2026-11-02-fullstack-auth复审.md`、`10-Lessons/2026-11-02-fullstack-auth修改后测试.md`；`20-Patterns/` 与 `30-Rules/` 目录仅含 `.gitkeep`，**无已验证规则或缺陷模式可命中**。据历史复盘落实的预防动作：①固定基线后再分轴审查；②对 assumption 跳过的真库测试单独核对项数（据此发现 125 全绿中真库实为 0 项）；③不止看 HTTP 200，直接查 Oracle 核对落库副作用；④不轻信任务文档 `状态：已完成` 与 `DocumentTest.md` 的 32/32 结论，全部重新实测。
- **双轴子代理未生效**：按 `code-review` Skill 派发了 `standards_axis` 与 `spec_axis` 两个子代理，二者均在派发后立即失败，报 `unsupported_encrypted_agent_content`。两轴改由根 Agent 独立完成，分开陈述、未合并排序。这是本环境第二次出现同一失败（前次见 `2026-11-02-fullstack-auth复审`）。
- **测试副作用**：本轮在共享库中产生了测试数据，标题前缀 `REV-1103-*` 与 `RT-*`（`edudoc_document` 由 4 行增至 10 行，chunk 8 行，outbox 19 条全 PUBLISHED，OSS 中新增对应对象），未做清理，与任务文档"遗留测试文档留作联调数据"的既有做法一致。审查用脚本置于 `%TEMP%\edudoc_review\`，未写入仓库。
- **未修改任何业务代码**：本轮只读审查 + 实机测试，`git status` 中 backend 源码无改动。为取日志临时启动的 8081 实例已停止，用户原有 8080 实例全程未干预。
- **未覆盖**：前端 B-09~B-12（尚未开工）、多实例并发下的 Outbox 抢投与 consumer 撞名（代码已用 `// ponytail:` 标注为单机假设，本轮未做双实例压测）、断点续传（属 B-10 前端）、大文件性能（P-04 仅以 2 页小样本验证，30 页真实课件的 60s 预算未实测）。
