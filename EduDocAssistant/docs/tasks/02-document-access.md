
# 02 fullstack-document 垂直切片 · 文档接入

> 状态取值：`未开始` / `进行中` / `已完成`（本阶段新建，全部 `未开始`）。
> 范围：上传会话 → STS 浏览器直传（真实阿里云 OSS，含断点续传）→ complete 回调 → Outbox + Redis Stream 异步解析（Tika 提取 + 分块入库，状态机 UPLOADING/PARSING/READY/FAILED，失败可重解析）→ 我的文档列表（筛选/搜索/分页）→ 详情 → 软删回收站/恢复；学生 + 教师双端页面。
> 契约源：《详细设计说明书》§2.4/§3.1/§5.1、《概要设计说明书》§2.1–§2.4/§5.4/ADR-05、《数据库设计说明书》文档域各表、`../05_系统设计/数据库脚本/01_schema.sql`（38 表已全量建齐，本切片零 DDL）、《需求规格说明书》§5.1/§5.4.2/P-03/P-04/NFR-03/NFR-05、原型 `student.md` 01–04 屏、`teacher.md` 02–04 屏。
> 不属本切片（后续单独切）：版本列表/对比/恢复（学生 17–19、教师 16–18 屏）、文档预览页与关键词检索高亮（student 05 屏）、向量化与智能处理（ai 域）、PATCH 重命名（需求 §5.1.3「重命名与复制不在本期」）、运维端回收站页面（接口先行）、课程共享文档与文档 ACL、时间范围筛选（详细设计表 3-2 无此参数，以契约为准）。
> 已确认决策（2026-11-02 用户决策）：①中间件用真实 OSS + Redis，不做桩；②范围为核心闭环；③软删后同名重传撞 `uk_document_1`（不含 deleted_at，已核实 schema L347）→ 返回 40901 友好提示，不改 DDL、不改标题；④断点续传入本切片（NFR-03/P-03 验收项）。

## 前置 · 分支与环境（用户配合项）

- **分支收尾**：`feat/fullstack-auth` 已过复审，提交遗留的 CLAUDE.md 改动 → rebase `main` → 合并回 `main` → 删除分支 → 从最新 `main` 拉 `feat/fullstack-document`。
- **环境清单（用户需提供，B-01 开工前就绪）**：
  - 阿里云私有 Bucket（中国大陆地域，禁公共读，开 SSE，建议开版本控制）；
  - Bucket CORS：允许来源 `http://localhost:5173`（联调期），方法 PUT/POST/GET/HEAD，AllowedHeader `*`，ExposeHeader `ETag`——漏配则浏览器直传全部失败（联调第一步先传测试对象验证）；
  - RAM 用户（长期 AK，权限仅 `sts:AssumeRole`）+ RAM 角色（信任该用户，授权最小化 `oss:PutObject/GetObject/HeadObject` 到目标 Bucket）；
  - 环境变量：`EDUDOC_OSS_ACCESS_KEY_ID` / `EDUDOC_OSS_ACCESS_KEY_SECRET` / `EDUDOC_OSS_ROLE_ARN` / `EDUDOC_OSS_BUCKET` / `EDUDOC_OSS_ENDPOINT` / `EDUDOC_OSS_REGION`（AK 不入仓）；
  - 本地 Redis 7（Windows 推荐 `docker run -d --name edudoc-redis -p 6379:6379 redis:7-alpine`，备选 WSL2/Memurai）；
  - 测试样本：约 30 页 PDF（P-04 计时验收）、一份 .docx、一份老式 .doc。
- **状态**：未开始

## B-01 后端 · 依赖与中间件接入（OSS/Redis 配置 + Redis 幂等存储）

- **需求/设计/原型引用**：概要 §2.4 技术选型（Redis 7.x、阿里云 OSS）、§5.4；详细设计 §2.4（幂等）；CLAUDE.md 架构红线（私有 Bucket + 短期 STS，浏览器不持长期凭据）。
- **现状**：pom 无 OSS SDK/Redis/Tika；application.yml 无 redis 与 oss 配置节；幂等键存于 `InMemoryIdempotencyStore`（其 ponytail 注释指明升级路径为 Redis）。
- **验收标准**：
  - pom 新增：`com.aliyun.oss:aliyun-sdk-oss`（3.18.x）、`com.aliyun:sts20150401`（V2.0 OpenAPI SDK，不选维护态的旧 `aliyun-java-sdk-sts`）、`spring-boot-starter-data-redis`、`org.apache.tika:tika-core` + `tika-parsers-standard-package`（3.x）；版本号落地时锁定并回填本文档；JDK 21 若报 JAXB 缺类按官方文档补（大概率不需要）。
  - 实际锁定版本（2026-11-02）：aliyun-sdk-oss **3.18.2**、sts20150401 **1.1.7**、tika **3.3.1**（4.0.0-alpha 不用）、data-redis 随 Boot 3.5.4；JAXB 无需补充。
  - `application.yml` 新增：`spring.data.redis.host/port`（默认 localhost:6379）；`edudoc.oss.{endpoint,region,bucket,access-key-id,access-key-secret,role-arn,sts-endpoint,object-key-prefix=edudoc/uploads,session-ttl-minutes=120,sts-duration-seconds=900}`（敏感项走环境变量）；`edudoc.outbox.{enabled,poll-interval-ms=1000,batch-size=50,max-retry=5}`；`edudoc.parse.{enabled,concurrency=2,stream-key=edudoc:stream:document-parse,group=cg-document-parse,chunk-size=1500}`。
  - 新建 `infrastructure.oss.OssProperties`（`@ConfigurationProperties("edudoc.oss")`，包路径对齐详细设计表 2-1）。
  - 新建 `common.api.RedisIdempotencyStore`（`SET key NX EX 86400`）替换 `InMemoryIdempotencyStore` 装配；`IdempotencyFilter` 及其测试若引用旧实现改为依赖 `IdempotencyStore` 端口。
- **测试**：`RedisIdempotencyStoreTest`（mock StringRedisTemplate：首见 true / 重放 false / Redis 返回 null 视为重复）；`mvn test` 存量全绿。
- **依赖**：前置环境就绪
- **状态**：已完成（2026-11-02，`mvn test` 55 全绿；Redis 复用用户已有 `redis-dev` 容器 v8.10.1:6379；`InMemoryIdempotencyStore` 已删除；`EduDocApplication` 加 `@ConfigurationPropertiesScan`）

## B-02 后端 · 课程种子 + courseScope 填充 + 课程查询接口

- **需求/设计/原型引用**：需求 §5.1.1（上传可指定课程）；原型 teacher 03「绑定课程与用途」；详细设计 §2.2 请求上下文（courseScope）；数据库设计 `edudoc_course` / `edudoc_course_member`。
- **现状**：`04_seed.sql` 无课程数据；`UserPrincipal.courseScope` 为空占位（`UserAccountRepository.java` L36 ponytail 注释）；前端上传表单与课程筛选无数据源。
- **验收标准**：
  - `SeedDataRunner` 扩展：幂等 seed 2 门课程 + 成员（teacher=TEACHER、student=STUDENT；按 course_code+term_code 判存）。
  - 新增 `access.infrastructure.{Course,CourseMapper,CourseRepository}` + `mapper/CourseMapper.xml`（HEXTORAW/RAWTOHEX 先例）；courseScope 查询复用 `CourseRepository.courseIdsOf`（不另加 `UserAccountMapper.selectCourseIds`，一条 SQL 两处用）。
  - `loadPrincipal` 填充 courseScope = `edudoc_course_member` 有效成员课程集合，删除占位注释并同步更新 `AccessGuard.java` L23 注释。
  - 新增 `GET /api/v1/courses`（认证即可，无权限点）→ 当前用户课程 `[{id,courseCode,courseName,termCode,memberRole}]`。**契约外辅助接口**（接口契约速查无此端点），理由：上传表单与列表筛选的数据源；回填契约速查。
- **测试**：`CourseQueryServiceImplTest`、`CourseControllerTest`、`SeedDataRunnerTest` 扩展（课程幂等）、`UserAccountRepositoryTest`（courseScope 断言）；真实 Oracle 的课程查询验证并入 B-13 联调（本地 mock 单测已覆盖装配）。
- **依赖**：无（可与 B-01 并行）
- **状态**：已完成（2026-11-02，`mvn test` 58 全绿；种子 CS101/MATH201，teacher 授两课、student 仅 CS101）

## B-03 后端 · 上传会话 + STS 签发

- **需求/设计/原型引用**：详细设计 §3.1.2 上传流程、§3.1.3 `POST /upload-sessions`、§4.3 表单校验；概要 §5.4（会话返回 STS/objectKey 前缀/过期时间）；需求 §5.1.1（≤20MB、Word/PDF、多文件）。
- **现状**：document 域空壳（package-info.java）；无任何上传接口。
- **验收标准**：
  - `POST /api/v1/documents/upload-sessions`，`@RequirePermission(DOCUMENT_CREATE)`，入参 `{courseId?,title,fileName,size,mimeType,docType}` → 201 `{sessionId,objectKey,expiration,sts:{accessKeyId,accessKeySecret,securityToken,expiration},region,bucket}`；写接口自动被 `IdempotencyFilter` 覆盖。
  - 校验：size>20MB → 41301；扩展名 + mimeType 双重校验仅 .doc/.docx/.pdf → 41501；title 1–200 去首尾空格；courseId 提供时校验成员身份（教师须为该课程 TEACHER/ASSISTANT 成员或 course.teacher_id，学生须为成员）→ 40301。教师端 courseId 必填、学生端可空（course_id 为 NULL 时 Oracle 复合唯一索引不生效，学生无课程文档天然不撞 `uk_document_1`）。
  - objectKey = `{prefix}/{yyyyMM}/{userIdHex}/{sessionId}-{清洗文件名}`；文件名去路径分隔符/控制字符（概要 §7.1 防注入）。
  - 会话存 Redis（无数据库表，概要 §2.2）：key `edudoc:upsession:{sessionId}`，TTL=session-ttl-minutes，value JSON `{sessionId,ownerUserIdHex,courseIdHex|null,title,docType,fileName,sizeBytes,mimeType,objectKey,status:OPEN|COMPLETING|COMPLETED,completedDocumentIdHex|null,completedVersionIdHex|null,createdAt}`。
  - STS 最小权限：AssumeRole 带 inline policy，Action 仅 `oss:PutObject/InitiateMultipartUpload/UploadPart/CompleteMultipartUpload/AbortMultipartUpload/ListParts`，Resource 精确到单 objectKey；DurationSeconds=900；长期 AK 只在服务端。
  - 新建：`document.api.UploadController` + dto、`document.application.UploadSessionService(+impl)`、`document.domain.{UploadSession,DocumentType}`、`document.infrastructure.UploadSessionRepository`（Redis）、`infrastructure.oss.{OssStorageAdapter,OssConfig}`。
- **测试**：`UploadSessionServiceImplTest`（12 例：41301/41501/课程范围 40301/教师必填课程/标题校验/objectKey 清洗）、`OssStorageAdapterTest`（mock STS：inline policy 精确到单 objectKey、DurationSeconds=900、失败映射 50301 可重试）；空 mimeType 按扩展名单侧校验（个别浏览器对 .doc 送空值）。
- **依赖**：B-01、B-02
- **状态**：已完成（2026-11-02，`mvn test` 72 全绿；本地起服务对**真实阿里云 STS** 实测：教师登录 → `POST /upload-sessions` 201 返回 STS 临时凭据/objectKey/桶定位，RAM 角色与策略配置验证通过；反例 21MB→41301、.txt→41501、缺幂等键→40001；Redis `edudoc:upsession:*` 键与 TTL 落盘。附：用户环境变量名笔误已修正为 `EDUDOC_OSS_ACCESS_KEY_ID/_SECRET`）

## B-04 后端 · complete 回调（核心事务）

- **需求/设计/原型引用**：详细设计 §3.1.2 完成上传伪代码（L116–130）、§3.1.3 `POST /upload-sessions/{id}/complete`、§2.4（乐观并发/对象唯一标识）；概要 §2.4（Outbox 与业务写入同事务）、ADR-05；数据库设计 `edudoc_oss_object`/`edudoc_document`/`edudoc_document_version`/`edudoc_outbox_event`。
- **现状**：无；`AuditLog` 实体缺 `document_id/detail_json` 列映射；无跨域审计接口。
- **验收标准**：
  - `POST /api/v1/documents/upload-sessions/{id}/complete`，`@RequirePermission(DOCUMENT_CREATE)`，入参 `{etag,contentHash,parts?}` → 201 `{documentId,versionId}`；冲突 40901。
  - 流程逐条落地伪代码：①Redis Lua CAS status OPEN→COMPLETING（即 `uploadSessionRepo.lock`）；已 COMPLETED → 重放返回原 `{documentId,versionId}`；COMPLETING → 40901 稍后重试；②`oss.headObject` 不存在或 size≠会话 → 40901，会话回置 OPEN 可重试；③`oss.getObject` 流式实算 SHA-256 ≠ contentHash → 40901（服务端实算为准，不信任浏览器）；④单个 `@Transactional`：insert oss_object（ORIGINAL）→ insert document（status 直接落 PARSING）→ insert version（version_no=1，UPLOAD，source_object_id）→ update current_version_id（同事务写，无 FK 是设计允许）→ insert outbox（DOCUMENT_PARSE_REQUESTED）→ 审计 UPLOAD_COMPLETED；⑤提交后 Redis 会话置 COMPLETED 回填两 id。
  - 边界：撞 `uk_document_1` → 捕 `DuplicateKeyException` → 40901「同名文档已存在或位于回收站，请恢复或等待清理」（2026-11-02 用户决策，不改标题不加列）；提交成功但 Redis 回填失败 → 会话仍 OPEN 时按 oss_object uk（bucket+objectKey+versionId）反查 version 重放，不重复建档。
  - 幂等语义分层记录进 DocumentTest.md：`IdempotencyFilter` 同 key 重放 = 40902（防双击），业务重试须用新 key（http.ts 默认行为），业务幂等靠会话重放。
  - 新建：dto、`document.infrastructure.{OssObject,Document,DocumentVersion}` 各 + Mapper + Repository + 3 个 XML；`access.application.AuditService(+impl)`（详细设计表 3-1 契约类，document 域经接口跨域调用）；`AuditLog`/`AuditLogMapper.xml` 补 documentIdHex/detailJson。
- **测试**：`CompleteUploadTest`(12 例:五写一事务、大小不符 40901 且会话回 OPEN、hash 不符 40901、COMPLETED 重放、COMPLETING 恢复路径、同名 40901、非本人 40301、摘要格式 40001、会话缺失 40401;对应详细设计 UT-01/UT-02)、`DocumentProvisioningServiceImplTest`(建档字段断言)。
- **依赖**：B-03
- **状态**：已完成（2026-11-02，`mvn test` 88 全绿；**真实端到端**：STS 凭据直传 OSS PUT 200 → complete 建档 → 重放同 id；Oracle 核查查证 document=PARSING/version=1 UPLOAD/oss_object=ORIGINAL/size=50、Outbox `DOCUMENT_PARSE_REQUESTED`=NEW 且 `JSON_VALUE` 可读（`CAST(x AS JSON)` 可行）、审计 UPLOAD_COMPLETED=200。遗留的 "B04 live" 测试文档留在库中作 B-07 列表联调数据）

## B-05 后端 · Outbox 调度器 + Redis Stream 发布

- **需求/设计/原型引用**：概要 §2.2（Redis Stream 为队列、outbox 表可靠发布）、§3.3（调度器=主应用内独立线程池，扫描未发布事件指数退避）、ADR-05；详细设计 §2.4、§7.1（消息含 schemaVersion）；数据库设计 `edudoc_outbox_event` + `ix_outbox_status`。
- **现状**：表已建，无任何发布/调度代码。
- **验收标准**：
  - 新建 `common.outbox.{OutboxEvent,OutboxEventMapper,OutboxEventRepository,OutboxDispatcher}` + XML（outbox 为横切设施，后续转换/导出域复用，放 common 对齐 CLAUDE.md）。
  - 调度器：单线程 `ThreadPoolTaskScheduler` + `@Scheduled(fixedDelayString="${edudoc.outbox.poll-interval-ms}")`；扫描 `status='NEW' AND (next_retry_at IS NULL OR next_retry_at<=SYSTIMESTAMP) ORDER BY create_time FETCH FIRST {batch} ROWS ONLY`；XADD 成功 → PUBLISHED+published_at；异常 → retry_count+1、next_retry_at=now+10s*2^n（封顶 10min），超 max-retry → FAILED。
  - 按 event_type 路由 Stream key（`DOCUMENT_PARSE_REQUESTED → edudoc:stream:document-parse`，`// ponytail: 新事件类型在此加路由`）；消息字段 `{eventId,eventType,taskId,documentIdHex,versionIdHex,schemaVersion=1}`；taskId=eventId hex——不建独立解析任务表，任务状态最终事实 = `edudoc_document.status`（对齐概要 §2.4）。
  - `payload_json`（Oracle JSON 类型）由 Jackson 序列化保证 IS JSON 合法。
  - 单机不抢锁：`// ponytail: 单节点轮询，多实例加 FOR UPDATE SKIP LOCKED`。
- **测试**：`OutboxDispatcherTest`（6 例：NEW→PUBLISHED、失败退避 `10*2^n` 秒、600s 封顶、超限 FAILED、无路由 FAILED、停用空转；对应 UT-20 发布侧）。路由表落 `edudoc.outbox.stream-keys`（event_type→stream 映射，比设计伪代码的 switch 更省一个类）。
- **依赖**：B-01
- **状态**：已完成（2026-11-03，`mvn test` 94 全绿；真机：启动后 B-04 的 NEW 事件被调度器 XADD 进 `edudoc:stream:document-parse`，`XLEN=1`、XRANGE 可见 eventId/taskId/schemaVersion/payload 全字段，DB 侧 status=PUBLISHED、published_at 落定；`@EnableScheduling` 已开）

## B-06 后端 · 解析 worker（Tika 提取 + 分块 + 状态机）

- **需求/设计/原型引用**：需求 P-04（30 页 60s 内完成解析）；概要 §2.3（不得在请求线程做长任务）、§2.4（consumerGroup+messageId+taskId 去重，任务表状态为最终事实）；详细设计 §3.1.1（ParsingTaskPublisher/解析进程）；数据库设计 `edudoc_document_chunk`（§3.4.11）。
- **现状**：无解析代码；chunk 表已建。
- **验收标准**：
  - Stream 消费：`StreamMessageListenerContainer`，group `cg-document-parse`（启动 `XGROUP CREATE … MKSTREAM` 幂等），consumer 名 `parse-{hostname}-{pid}`，并发 = `edudoc.parse.concurrency`，独立线程池不占请求线程。
  - 去重：目标 version 已 READY 且事件非「重新解析」→ 跳过直接 XACK；否则先置 PARSING。
  - 解析：`oss.getObject` 落临时文件（防 20MB 堆内膨胀）→ Tika `AutoDetectParser` + 自定义 SAX handler（h1–h6 维护 heading 栈、PDFBox 页分隔维护 page_no、段落累积全局字符偏移）；TikaException/空文档 → FAILED（42201 语义落审计）。
  - 分块（`document.domain.ChunkingService` 纯函数）：段落累积至 chunk-size（默认 1500 字符）在段落边界切，单段超限硬切；chunk_no 从 1 递增；heading_path="H1 / H2"（截 1000，允许为空——老式 .doc 结构识别弱）；char_start/char_end 为全文偏移；token_count≈ceil(chars/2)（`// ponytail: 中文估算，ai 域接入真 tokenizer 时替换`）；content_hash=SHA-256(块文本)；index_status='PENDING'（向量化属 ai 域不入本切片）。
  - 事务边界：删该 version 旧 chunks + 分批（50/批）插新（CLOB `jdbcType=CLOB`）+ document.status=READY 单事务；成功 XACK + 审计 PARSE_SUCCEEDED；异常 → FAILED + 审计 PARSE_FAILED + XACK（重试走 `POST /{id}/parse`；`// ponytail: 无 DLQ/pending 回收，单机+人工重试够用，需要时加 XAUTOCLAIM`）。
  - 新建：`document.application.DocumentParseWorker`、`document.domain.{ChunkingService,Chunk}`、`document.infrastructure.{RedisStreamConfig,TikaTextExtractor,DocumentChunk+Mapper+Repository}` + XML。
- **测试**：`ChunkingServiceTest`（切块边界/超长段/heading 路径/hash 与 token 估算）、`TikaTextExtractorTest`（test resources 程序生成小 docx/pdf 样本，断言文本与页码）、`DocumentParseWorkerTest`（mock：PARSING→READY、失败→FAILED+审计、重复消息跳过；对应 UT-20 消费侧）。
- **依赖**：B-04、B-05
- **状态**：未开始

## B-07 后端 · 文档列表 + 详情

- **需求/设计/原型引用**：需求 §5.1.3（最近修改倒序、课程/类型/状态筛选、标题搜索、分页）；详细设计 §3.1.3 `GET /` 与 `GET /{id}`、§3.5（非 owner 的 OWNER 分支）；原型 student 02、teacher 02。
- **现状**：无查询接口。
- **验收标准**：
  - `GET /api/v1/documents`，`@RequirePermission(DOCUMENT_READ)`，参数 `courseId,type,status,keyword,page(从1),pageSize(默认20,≤100)` → `PageResult<DocumentSummary>`；默认 `update_time DESC`；**强制 owner_id=当前用户**；默认排除 DELETED，显式 `status=DELETED` 才查回收站；keyword LIKE 标题（转义 `%_`，走 ix_doc_title_search/ix_doc_owner_status）；Oracle `OFFSET … FETCH NEXT` 分页。
  - `DocumentSummary{id,title,docType,status,courseId,courseName,sizeBytes,mimeType,versionNo,createTime,updateTime}`（id 均十六进制字符串；sizeBytes/mimeType/courseName join 当前版本 oss_object 与 course）。
  - `GET /api/v1/documents/{id}`：非 owner → **40401**（不泄露存在性；课程可见/ACL 分支后续切片）→ `DocumentDetail`（含 currentVersionId、versionNo、contentHash、chunkCount、deletedAt/purgeAt）。
  - 新建：`document.api.DocumentController` + dto、`document.application.DocumentQueryService(+impl)`、DocumentMapper.xml 查询语句。
- **测试**：`DocumentQueryServiceTest`（筛选组合、分页钳制、回收站开关）、`DocumentControllerTest`（MockMvc：默认排除 DELETED、他人文档 40401、无权限 40301）。
- **依赖**：B-04
- **状态**：未开始

## B-08 后端 · 删除 / 恢复 / 重新解析

- **需求/设计/原型引用**：需求 §5.1.3（删除进回收站，运维端统一管理）、D02；详细设计 §3.1.3 `DELETE /{id}`、`POST /{id}/restore`、`POST /{id}/parse`；概要 §6.3（回收站 30 天）。
- **现状**：无生命周期接口；seed 的 20 个权限点无 RESTORE/PARSE 专用码。
- **验收标准**：
  - `DELETE /api/v1/documents/{id}`（DOCUMENT_DELETE）→ 204：owner 校验；status=DELETED、deleted_at=now、purge_at=now+30d；不动 OSS 对象；审计 DOCUMENT_DELETED（detail_json 记 prevStatus）。
  - `POST /api/v1/documents/{id}/restore`（DOCUMENT_DELETE）→ 204：仅 DELETED 可恢复否则 40901；恢复后状态按当前 version 有无 chunks 推断 READY/FAILED；恢复撞 `uk_document_1`（同名活跃文档）→ 40901；清 deleted_at/purge_at；审计 DOCUMENT_RESTORED。
  - `POST /api/v1/documents/{id}/parse`（DOCUMENT_EDIT，body `{versionId?}` 缺省当前版本）：仅 READY/FAILED 允许，PARSING/UPLOADING/DELETED → 40901；事务 status=PARSING + outbox(DOCUMENT_PARSE_REQUESTED) + 审计 PARSE_REQUESTED；返回 `{taskId}`（=新 outbox eventId hex）。
  - 权限映射决策记入本文档：恢复并入 DOCUMENT_DELETE（回收站管理动作），重解析用 DOCUMENT_EDIT（2026-11-02 规划确认）。
  - 新建：`document.application.DocumentLifecycleService(+impl)`、`dto/ParseResponse`；DocumentController 增三端点；DocumentMapper.xml 增 update 语句。
- **测试**：`DocumentLifecycleServiceTest`（软删字段与 purge_at、恢复状态推断两分支、非 DELETED 恢复 40901、parse 各状态 40901 分支、outbox 与审计写入）。
- **依赖**：B-05、B-07
- **状态**：未开始

## B-09 前端 · API 模块与类型

- **需求/设计/原型引用**：详细设计 §3.1.3 接口表、§4.4；契约速查（envelope/错误码）。
- **现状**：`api/http.ts` 已具备幂等键/trace/401 回跳/`request<T>` 透传 config；`api/types.ts` 已有 PageResult、ErrorCode（41301/41501/42201 等）、PermissionCode（DOCUMENT_*）；无文档域 API 模块。
- **验收标准**：
  - 新建 `web/src/api/documents.ts`：`listDocuments(params)` / `createUploadSession(req)` / `completeUpload(sessionId,req)` / `getDocument(id)` / `deleteDocument(id)` / `restoreDocument(id)` / `reparseDocument(id,versionId?)` / `listCourses()`；全部复用 http.ts，不改既有封装。
  - `api/types.ts` 增 `DocType`（COURSEWARE/HOMEWORK/THESIS/REPORT）与 `DocStatus`（UPLOADING/PARSING/READY/FAILED/DELETED）常量及 Summary/Detail 类型。
- **测试**：`documents.test.ts`（参数拼装、路径、解包）。
- **依赖**：B-03/B-04/B-07/B-08 契约固定（可先行按契约开发）
- **状态**：未开始

## B-10 前端 · OSS 直传封装（含断点续传）

- **需求/设计/原型引用**：概要 §5.4（浏览器 STS 分片直传私有 Bucket）；需求 NFR-03（故障演练验证断点续传）、P-03、§6.3（分片与断点续传）。
- **现状**：无；package.json 无 ali-oss。
- **验收标准**：
  - 安装 `ali-oss`（^6，缺类型则补 `@types/ali-oss`）；不自研 OSS V4 签名。
  - 新建 `web/src/upload/ossUpload.ts`：`sha256Hex(file)` 用 `crypto.subtle.digest`；`uploadToOss(session,sts,file,onProgress)`——<5MB `client.put`，≥5MB `client.multipartUpload`（partSize 1MB、parallel 3、headers 带 Content-Type 与 x-oss-meta-sha256）。
  - 断点续传（2026-11-02 用户决策入本切片）：checkpoint 存 `localStorage['edudoc-ckpt-'+sessionId]`，重传时读出传入，成功后清除；`parts` 清单从 checkpoint.uploadedParts 取传 complete。
- **测试**：`ossUpload.test.ts`（mock ali-oss：put/multipart 分流阈值、progress 回调、checkpoint 存取清除、sha256 十六进制正确性）。
- **依赖**：B-09
- **状态**：未开始

## B-11 前端 · 我的文档页 + 上传 Modal + 详情 Drawer（学生/教师共用）

- **需求/设计/原型引用**：原型 student 02/03/04 屏、teacher 02/03 屏（教师上传绑定课程与用途）；需求 §5.1.1（多文件、进度、类型大小校验文案）、§5.1.3（倒序/筛选/搜索/删除）；详细设计 §4.2（状态徽标文字+颜色双编码、失败保留输入）。
- **现状**：`/student/documents`、`/teacher/documents` 渲染 SectionPlaceholder；antd 5.29 已装（Upload.Dragger/Table/Progress 可用）。
- **验收标准**：
  - 新建 `pages/Documents.tsx`（prop `role:'STUDENT'|'TEACHER'` 共用）：筛选区（课程下拉用 listCourses、类型、状态、标题关键词）+ 分页；默认最近修改倒序；状态徽标文字+颜色双编码（上传中/解析中 processing/就绪 success/失败 error）；Segmented「我的文档 | 回收站」（回收站传 status=DELETED，行操作=恢复）；行操作：详情 Drawer、删除 Popconfirm→204→刷新、FAILED 行「重新解析」→行转 PARSING。
  - 轮询：列表存在 UPLOADING/PARSING 行时 3s `setInterval` 重拉当前页，全部终态即停，卸载/视图切换清理定时器（`// ponytail: 单机轮询够用，并发大了换 SSE`）。
  - 新建 `pages/DocumentUploadModal.tsx`：Upload.Dragger 多文件（逐个会话串行上传）；前端校验扩展名+MIME+≤20MB（§4.3 文案）；表单：标题（默认文件名去扩展名可改）、课程（教师必填/学生可选）、类型下拉（必填；SRS「默认自动识别」不可实现，默认留空，差异记入本文档待评审）；每文件独立进度条与错误态；流程 createUploadSession→sha256→直传→complete→关 Modal 刷新列表；失败保留输入不回空白页。
  - 新建 `pages/DocumentDetailDrawer.tsx`：元数据 + 当前版本（versionNo/contentHash 前 8 位）+ chunkCount + 重新解析/删除入口；不做预览页（排除项）。
- **测试**：`Documents.test.tsx`（渲染/筛选/分页/搜索参数、状态徽标、轮询启停 fake timers、删除恢复调用、FAILED 重解析）、`DocumentUploadModal.test.tsx`（校验拦截、多文件流程 mock ossUpload、失败保留输入）、`DocumentDetailDrawer.test.tsx`。
- **依赖**：B-09、B-10
- **状态**：未开始

## B-12 前端 · 教师端接线与路由

- **需求/设计/原型引用**：原型 teacher 02/03（课程列、上传绑定课程）；详细设计 §4.1 路由表。
- **现状**：`App.tsx` 所有 nav key 落 SectionPlaceholder（L52–64）；routes.ts nav 已含 documents（DOCUMENT_READ）。
- **验收标准**：
  - `App.tsx` 增 `pageOf` 映射：`documents` → `<Documents role=…/>`（学生/教师各自 role），其余 key 仍走占位；routes.ts 不动。
  - 教师端差异：课程列显示、上传课程必填、课程筛选来自所授课程；「课程共享文档」不入本切片（依赖 ACL 域）。
- **测试**：扩展 `routes.test.ts` 或 App 路由级测试：`/student/documents`、`/teacher/documents` 渲染真实页面而非占位；无权限仍走 403 逻辑。
- **依赖**：B-11
- **状态**：未开始

## B-13 联调 · 端到端文档闭环 + 测试文档

- **需求/设计/原型引用**：需求 P-03/P-04、NFR-03、表 11-1 合格性口径；`docs/test/AuthTest.md` 结构范本。
- **验收标准**：
  - 新建 `docs/test/DocumentTest.md`（按 AuthTest.md 结构）：环境与前置（Redis/OSS 环境变量/种子课程/样本文件）+ 按接口逐节 curl（含 41301/41501/40901/40401 反例、`redis-cli XLEN/XRANGE` 与 SQL 核查 chunk 行数、幂等两层语义说明）+ 建议测试顺序。
  - e2e 手工验收（真实 OSS+Redis+Oracle）：
    1. teacher/student 登录 → `GET /courses` 见各自课程；
    2. 上传 30 页 PDF → 进度可见 → complete 201 → 列表 PARSING → **计时 ≤60s 转 READY（P-04，实测值记录）**；SQL 核查 chunk 行数>0、outbox PUBLISHED、审计 UPLOAD_COMPLETED/PARSE_SUCCEEDED；
    3. ≥5MB 文件传一半 DevTools offline → 恢复重传 → checkpoint 续传（NFR-03）；
    4. 反例：21MB → 41301；.txt → 41501；同名同课程重传 → 40901；
    5. 详情 Drawer 数据正确；损坏文件 → FAILED →「重新解析」→ 转 PARSING → 结果正确；
    6. 删除 → 列表消失、回收站可见（purge_at≈+30d）→ 恢复回列表；
    7. 学生访问他人文档 id → 40401；无 DOCUMENT_READ 角色 → 40301；
    8. 老式 .doc 上传解析不崩（heading_path 允许为空）。
  - `mvn test` + `pnpm test` + `tsc --noEmit` + `pnpm build` 全绿；提交按 `type(scope)` 分次，全绿后本地提交留 message 给用户确认，审查/复审通过后推送。
- **测试**：e2e 冒烟清单（单测已分散到 B-01~B-12）
- **依赖**：B-06、B-07、B-08、B-11、B-12
- **状态**：未开始

## 建议执行顺序

前置（auth 分支收尾 ∥ 用户备齐环境）→ B-01 → B-02 → B-03 → B-04 → B-05 → B-06 →（B-07 ∥ B-08）→ B-10 →（B-11 ∥ B-12）→ B-13。B-02 与 B-01 可并行；B-09 在接口契约固定（B-03/B-04 评审通过）后可与后端 B-05~B-08 并行。

## 风险与已知债务

| # | 事项 | 处理 |
| --- | --- | --- |
| R5 | OSS CORS 漏配 → 直传全挂 | 前置清单已列；联调第一步先传测试对象验证 |
| R6 | IdempotencyFilter 同 key 重放=40902 与设计「复用 key 读结果」有差异 | 过滤器防双击、业务幂等靠会话重放，前端重试用新 key；差异写入 DocumentTest.md，不改过滤器 |
| R7 | 老式 .doc heading 识别弱（POI HWPF） | heading_path 允许为空，按段落切块；e2e 步骤 8 验证 |
| R8 | SRS 时间范围筛选/类型自动识别不做 | 以详细设计契约为准，差异已在头部「不属本切片」记录，待评审 |
| R9 | course_id 单值，一份文档只绑一门课 | 「课程共享文档」依赖 ACL 域，不入本切片 |
| R11 | 解析 worker 在主应用进程内（非独立解析进程） | 与概要 §3.3 不冲突（调度器本就主应用内）；拆进程时 consumer group 原样平移，`// ponytail` 标注 |
