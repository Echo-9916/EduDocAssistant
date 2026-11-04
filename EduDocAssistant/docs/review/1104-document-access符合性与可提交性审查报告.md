# 1104 文档接入（02 切片）代码符合性与可提交性审查报告

> 审查日期：2026-11-04　审查人：Codex（根 Agent，Spec / Standards 双轴由根 Agent 亲自完成）
> 分支：`feat/fullstack-document`（领先 `main` 16 个提交；审查期间新增 `4f982f5`、`87cd6ff`，审查结束时工作区干净）
> 审查范围：`docs/tasks/02-document-access.md` B-01~B-13；`backend/` 的 document / access / common / outbox / oss；`web/` 的文档页面、上传封装与 API 模块
> 规格来源：《02 fullstack-document 垂直切片 · 文档接入》、《详细设计说明书》§2.4/§3.1/§5.1、《概要设计说明书》§2.4/§5.4、《数据库设计说明书》文档域表、`CLAUDE.md` 架构红线
> **结论：有条件通过（不建议立即推送）**。核心闭环在真实 Oracle + Redis + 阿里云 OSS 上 21/21 通过，自动化测试与构建全绿；但按验收标准仍有 **2 项 P2 功能缺口**（列表缺状态筛选、上传弹窗状态残留），修复后可推送。

> 后续复验：[1104-document-access整改复验报告.md](1104-document-access整改复验报告.md)（整改后结论：通过）。

---

## 一、结论摘要

本切片的工程质量整体较高：B-01~B-08 后端主链路、B-09~B-12 前端接线、B-13 联调都已落地；状态机、幂等分层、owner 隔离、Outbox/Stream、Tika 分块、回收站与重解析均在真实环境验证通过。审查期间唯一一次全链路重跑（30 页 PDF + ≥5MB 分片）结果为 **21 PASS / 0 FAIL**，30 页 PDF 从 `complete` 到 `READY` 仅 **1.1s**（P-04 预算 60s），≥5MB 分片文件 **5.2s** 转 `READY`。

不阻断主链路、但影响"完全符合验收标准"的问题集中在两处：

1. **我的文档列表缺少状态筛选**：页面只有课程、类型、关键词筛选和"我的文档 / 回收站"切换；B-11 与需求 §5.1.3 要求的 `PARSING/READY/FAILED` 状态筛选没有对应控件。
2. **上传弹窗关闭后状态残留**：`items/title/courseId/docType` 不会在关闭或成功后重置，重新打开仍保留已上传文件与标题，再次点"开始上传"会触发重复上传；B-11 要求的"成功后关 Modal 刷新列表"也未实现（只刷新列表）。

另有 8 项 P3：前端 `ErrorCode` 缺 `40501`、`/courses` 未回填接口契约速查、B-03/B-10 验收文字与实现/测试存在口径差异、`DocumentTest.md` 一处过期说明、真库 IT 默认 0 项、分块 offset 已知债务、真实 OLE2 `.doc` 与断网续传仍待人工样本。均已在下方逐项列出。

---

## 二、验证执行结果（本轮实测，非引用历史结论）

### 2.1 自动化测试与构建

| 项目 | 命令 | 实际结果 |
|---|---|---|
| 后端全量单测 | `backend/` 下 `mvn test` | **135 通过 / 0 failure / 0 error / 0 skipped**（surefire 32 个报告文件聚合） |
| 真库集成测试 | `RbacLifecycleIntegrationTest` | **Tests run: 0**；启动日志输出 `[IT-SKIP]` 说明未注入 `EDUDOC_DB_USERNAME`/`EDUDOC_DB_PASSWORD`，覆盖缺口已知 |
| 前端全量单测 | `web/` 下 `pnpm test -- --reporter=basic` | **17 个测试文件 / 76 项全部通过** |
| 前端类型检查与生产构建 | `web/` 下 `pnpm build`（`tsc --noEmit && vite build`） | **通过**，产物 `dist/index.html` + 资源正常生成 |
| 后端打包 | `backend/` 下 `mvn -q -DskipTests package` | **通过**，生成 `edudoc-backend-0.1.0-SNAPSHOT.jar` |
| 空白/格式检查 | `git diff --check` | 退出码 **0**（仅提示 LF→CRLF，不影响内容） |

### 2.2 真实端到端复跑（headless，真实 OSS + Redis + Oracle）

- 运行实例：`localhost:8080`，PID 62100，启动时间 2026-11-04 11:03:04，晚于最后一次后端源码修改（10:51）与最后一次后端提交（10:55）；Redis 消费组中仅该实例的 `parse-{host}-62100-{0,1}` 为活跃消费者，其余均为长空闲历史 consumer。
- 脚本：`%TEMP%\edudoc-b13\e2e.cjs`（Node + `ali-oss`，真实 STS 直传）。
- 结果：**21 PASS / 0 FAIL**。

| # | 断言 | 实测结果 |
|---|---|---|
| 1-2 | teacher / student 登录 | 均拿到有效 token |
| 3-5 | `GET /courses` | teacher=200 且 2 门课（CS101、MATH201）；student=200 且 1 门（CS101） |
| 6 | 21MB 会话反例 | `413 / 41301` |
| 7 | `.txt` 会话反例 | `415 / 41501` |
| 8 | 30 页 PDF 创建会话 | `201` |
| 9 | STS 直传 PUT | 成功 |
| 10 | complete | `201`，返回 `documentId/versionId` |
| 11 | 30 页解析耗时 | **READY，1.1s**（P-04 ≤60s） |
| 12 | 同名同课程重传 | `409 / 40901`「同名文档已存在或位于回收站…」 |
| 13 | 详情分块数 | `chunkCount=15` |
| 14 | student 访问 teacher 文档 | `404 / 40401`（不泄露存在性） |
| 15-16 | DELETE + 回收站可见 | `204`；`status=DELETED` 列表可见 |
| 17 | restore | `204` |
| 18-19 | reparse + 轮询 | `200` 返回 `taskId`；随后 `READY` |
| 20-21 | ≥5MB multipart | complete `201`；**5.2s 转 READY** |

### 2.3 数据落库核查（Oracle FREEPDB1）

以本轮 30 页 PDF 文档为样本：

| 核查项 | 实测结果 |
|---|---|
| 文档状态 | `READY` |
| 版本 | `VERSION_NO=1`、`VERSION_TYPE=UPLOAD`、`VERSION_STATUS=A` |
| 分块 | 15 行；`chunk_no` 1~15 连续；`char_start=0`、`char_end=21290`；`index_status=PENDING` |
| Outbox | 本轮相关 3 条 `DOCUMENT_PARSE_REQUESTED` 全部 `PUBLISHED`，`retry_count=0` |
| 审计链 | `UPLOAD_COMPLETED → PARSE_SUCCEEDED → DOCUMENT_DELETED → DOCUMENT_RESTORED → PARSE_REQUESTED → PARSE_SUCCEEDED`，`result_code` 均为 `200` |

### 2.4 异常与边界探针

| 输入 | 实测结果 |
|---|---|
| 未匹配路由 `GET /api/v1/definitely-not-a-route` | `404 / 40401` |
| `DELETE /api/v1/courses`（带 `Idempotency-Key`） | `405 / 40501` |
| `POST …/parse` + `Content-Type: text/plain`（带幂等键） | `415 / 41501` |
| `?page=abc` | `400 / 40001` |
| `?courseId=y` | `400 / 40001` |
| 畸形 JSON body（新幂等键） | `400 / 40001` |
| `?page=2147483647&pageSize=2` | `200` 且 `items=0`（未溢出回首页） |
| 跨用户同一 `Idempotency-Key` | teacher 与 student **均 201**；Redis 键分别为 `edudoc:idem:5F2F10AD…224B6…` 与 `…2424B6…`，按用户隔离 |

> 说明：`DELETE /courses` 不带 `Idempotency-Key` 时返回 `400/40001` 是 `IdempotencyFilter` 的预期行为（所有非 auth 写请求强制要求幂等键），不是 405 契约回归。补上幂等键后 405/40501 正确。

---

## 三、Spec 轴（B-01~B-13 对照）

| 项 | 结论 | 关键证据 |
|---|---|---|
| B-01 依赖与中间件 | **符合** | `pom.xml` 锁定 OSS 3.18.2、STS 1.1.7、Tika 3.3.1、data-redis；`application.yml` 含 `spring.data.redis`、`edudoc.oss/outbox/parse` 全量配置；`RedisIdempotencyStore` 已替换内存实现 |
| B-02 课程种子 + `courseScope` + `/courses` | **部分符合（P3-2）** | `SeedDataRunner.seedCourses()` 幂等写 CS101/MATH201 与成员；`UserAccountRepository` 填充 `courseScope`；`GET /courses` 实测角色课程正确；但 B-02 要求"回填契约速查"未落实 |
| B-03 上传会话 + STS | **符合（文档 P3-5）** | ≤20MB、扩展名 + MIME 双校验、title 1–200、teacher 必填课程、成员身份校验、objectKey 清洗、Redis 会话、STS inline policy 精确到单 objectKey + 900s 均在代码中确认；响应含 `endpoint`，任务文档验收行未回填 |
| B-04 complete 核心事务 | **符合** | CAS OPEN→COMPLETING、COMPLETED 重放、COMPLETING 恢复、head 大小校验、服务端流式 SHA-256、单事务五写 + 审计、`findByObjectKey` 回填失败恢复路径均存在；实测五表落库与审计正确 |
| B-05 Outbox 调度 | **符合** | `@Scheduled` 单线程、`NEW` 扫描、XADD 路由、10s×2^n 封顶 600s、超限 FAILED、无路由 FAILED；实测 3 条事件全 `PUBLISHED`、`retry_count=0` |
| B-06 Tika 解析 + 分块 | **符合（已知债务 S10）** | Stream 消费组 `ReadOffset.from("0")`、consumer 名含 pid、临时文件、AutoDetectParser、SAX 标题栈/页码、8M 字符上限、真实类型复验、50/批插入、`AND status='PARSING'` 回写守卫；分块 offset 漂移与代理对切断为已记录债务 |
| B-07 列表 + 详情 | **符合** | 强制 `owner_id`、默认排除 DELETED、显式 DELETED、LIKE 转义、`OFFSET … FETCH`、long offset、pageSize 1..100、详情非 owner 40401；实测列表/详情/越权全部正确 |
| B-08 删除 / 恢复 / 重解析 | **符合** | 软删 + `purge_at=+30d`、恢复按 chunkCount 推 READY/FAILED、同名恢复 40901、重解析仅 READY/FAILED、仅当前版本、`taskId=outbox eventId`；实测完整闭环通过 |
| B-09 前端 API 与类型 | **部分符合（P3-1）** | `documents.ts` 8 个方法与 `types.ts` 文档类型/状态齐全；`documents.test.ts` 覆盖参数/路径/写接口；但 `ErrorCode` 缺 `40501` |
| B-10 OSS 直传封装 | **部分符合（P3-3）** | `ali-oss` 6.23.0、`sha256Hex`、<5MB `put`、≥5MB `multipartUpload`、checkpoint 存取/清除、`endpoint` 修复与断言均在；实际 6 个测试（验收写 7 例），`parts` 未回传（S8 已决策不依赖） |
| B-11 文档页 / 上传 / 详情 | **部分符合（P2-1、P2-2）** | 列表、状态徽标双编码、回收站、FAILED 重解析、轮询、多文件串行上传、进度/错误态、详情 Drawer 均实现；缺状态筛选，上传弹窗关闭后状态残留且成功后不自动关闭 |
| B-12 教师端接线 | **符合** | `App.tsx` 的 `pageOf` 将 `documents` 映射真实页面，教师课程列/必填课程由 role 分支控制，其他 key 仍占位 |
| B-13 联调 + 测试文档 | **部分符合（已知遗留）** | `DocumentTest.md` 已含 21/21、SQL、反例、幂等两层说明；真实 OLE2 `.doc`（e2e 步骤 8）与断网续传（DevTools 手工）仍待用户样本/交互验证 |

---

## 四、Standards 轴（架构红线与工程约定）

| 检查项 | 结论 | 证据 |
|---|---|---|
| Controller → Application → Domain/Repository | **符合** | `DocumentController`/`UploadController` 只做参数接收与委托；Mapper/OSS 客户端只在 infrastructure |
| 请求线程不做长任务 | **符合** | 解析走 Redis Stream + 独立线程池；Outbox 调度器独立线程；complete 只做 head/hash 与事务 |
| Outbox 与业务同事务 | **符合** | `DocumentProvisioningServiceImpl.provision` 单 `@Transactional` 内五写 + outbox + 审计 |
| 私有 Bucket + 短期 STS | **符合** | STS inline policy 仅 PutObject/multipart 相关 Action，Resource 精确到单 objectKey，DurationSeconds 来自 900s 配置；浏览器不持长期 AK |
| 写接口强制幂等键 | **符合** | `IdempotencyFilter` 覆盖非 auth 的 POST/PUT/PATCH/DELETE；键作用域含 userId + method + URI，实测跨用户不互顶 |
| 版本不可变 | **符合** | 上传版本 `version_no=1`、`version_type=UPLOAD`；重解析不新增版本、不重复 chunk |
| SQL 只在 mapper.xml、无 `${}` | **符合** | `rg '\$\{' src/main/resources/mapper` 无命中；分页/筛选均参数化 |
| Service 接口 + Impl | **符合** | document 域与 access 域对应接口/实现成对存在 |
| 构造器注入 | **符合** | 统一 `@RequiredArgsConstructor`，未见字段注入 |
| 错误处理不塌缩 500 | **符合** | `GlobalExceptionHandler` 覆盖 NoResourceFound / MethodNotSupported / MediaType / MessageNotReadable / TypeMismatch / MissingParam / MissingHeader / BindException，统一 WARN + 固定文案；实测 400/404/405/415 均正确 |
| 启动健壮性 | **符合** | `OssConfig` 的 OSS/STS 客户端 `@Lazy`；`OssStorageAdapter` 经 `ObjectProvider` 取用，缺凭据时 OSS 接口返回 50301 而非启动失败 |

---

## 五、发现清单

### P2-1【功能缺口】我的文档列表缺少状态筛选

- 位置：`web/src/pages/Documents.tsx` 筛选区。
- 现状：只有课程下拉、类型下拉、标题关键词；`Segmented` 的"我的文档 / 回收站"仅切换是否传 `status=DELETED`，没有 `PARSING/READY/FAILED` 状态筛选。
- 规格：B-11 验收"筛选区（课程下拉用 listCourses、类型、状态、标题关键词）"；需求 §5.1.3 也要求课程/类型/状态筛选。后端 `GET /documents?status=…` 已支持。
- 影响：用户无法只查看解析中/失败/就绪文档，只能翻页人工查找；属需求项缺失。
- 建议：增加状态 `Select`（全部 / 解析中 / 就绪 / 失败），写入 `filters.status` 并重置 `page=1`；回收站模式下强制 `DELETED`。补一条断言 status 透传的前端测试。

### P2-2【功能/体验】上传弹窗关闭后状态残留，成功后不自动关闭

- 位置：`web/src/pages/DocumentUploadModal.tsx` 与 `Documents.tsx` 的 `onClose` 接线。
- 现状：`items/title/courseId/docType` 是组件内部 state，父层 `onClose` 只把 `open` 置 false；组件始终挂载，关闭再打开时旧文件与标题仍在。上传全部成功后只调用 `onUploaded()` 刷新列表，不关闭 Modal。
- 规格：B-11 流程要求"createUploadSession → sha256 → 直传 → complete → **关 Modal 刷新列表**"；"失败保留输入"意味着成功路径应清理。
- 影响：重新打开后可再次提交已完成的同名文件，得到 `40901 同名文档已存在`；也可能让用户误以为上次上传未完成。属核心上传流程的可达缺陷。
- 建议：全部文件成功后关闭 Modal 并清空状态；`onClose` 时若全部为 `done` 则重置（失败项按规格保留）；补"关闭→重开为空"与"成功后自动关闭"的前端测试。

### P3-1【契约】前端 `ErrorCode` 缺 `40501`

- 位置：`web/src/api/types.ts` 的 `ErrorCode` 常量与 `types.test.ts` 的精确对象断言。
- 现状：后端已发出 `40501`，设计文档 §5.1 已回填；前端常量与测试都没有 `40501`，测试反而把缺失锁定。
- 影响：前端无法通过常量识别方法不允许；未来按 `ErrorCode` 做错误分支会漏掉该码位。当前页面流程很少触发 405，故定 P3。
- 建议：补 `METHOD_NOT_ALLOWED: 40501` 并更新 `types.test.ts`。

### P3-2【文档】`/courses` 未回填接口契约速查

- 位置：`docs/design/接口契约速查.md`、`docs/design/详细设计说明书.md`。
- 现状：B-02 明确要求"契约外辅助接口……回填契约速查"；实际两处文档都没有 `/api/v1/courses`（`rg` 无命中），只在任务文档与 `DocumentTest.md` 中说明。
- 影响：前后端对齐依赖任务文档而非契约速查；后续切片的课程数据源缺少权威契约入口。
- 建议：在接口契约速查增加"文档域接口"表，至少列出 `/courses` 与文档域 8 个端点；或回填详细设计 §3.1.3。

### P3-3【文档】B-10 验收与实现/测试口径不一致

- 位置：`docs/tasks/02-document-access.md` B-10 验收标准。
- 现状：验收写"7 例"与"parts 清单从 checkpoint.uploadedParts 取传 complete"；实际 `ossUpload.test.ts` 为 6 例（两条校验合并），前端 `completeUpload` 只传 `{contentHash}`，`parts` 未回传。
- 说明：S8 已决策"服务端接收但不校验、不落库，不依赖回读"，代码 Javadoc 也已注明；但 B-10 验收文字没有同步。
- 建议：把 B-10 改为 6 例，并写明 `parts` 不参与完整性判定；若坚持验收原文，则前端传 `parts` 并在文档说明其仅审计用途。

### P3-4【文档】`DocumentTest.md` 的 40501 说明过期

- 位置：`docs/test/DocumentTest.md` 第 14 行。
- 现状：仍写"整改新增码位，需同步设计文档 §5.1 字典"；实际 `详细设计说明书.md:383` 已回填 `40501`。
- 建议：改为"已回填设计文档 §5.1"或删除括注。

### P3-5【文档】B-03 响应新增 `endpoint` 未回填任务验收行

- 位置：`docs/tasks/02-document-access.md` B-03 验收标准。
- 现状：验收列出的响应为 `{sessionId,objectKey,expiration,sts,region,bucket}`；实现额外返回 `endpoint`（B-13 修复直传域名所必需），仅在 `DocumentTest.md` 说明。
- 建议：在 B-03 验收行补 `endpoint` 并注明"阿里云地域 `region` 与 OSS 端点 `endpoint` 分离"。

### P3-6【测试】真库集成测试默认 0 项

- 现状：`RbacLifecycleIntegrationTest` 在默认 `mvn test` 下 `Tests run: 0`，已有 `[IT-SKIP]` 显式告警（N6 整改有效），但"135 全绿"不包含真库 RBAC 路径。
- 建议：CI 使用独立 profile 强制要求凭据；本地保留 `[IT-SKIP]` 告警。

### P3-7【已知债务】分块 offset 漂移与代理对切断

- 位置：`ChunkingService.hardSplit`、`chunk()` 的 `blockStart += text.length() + 1`。
- 现状：按 UTF-16 code unit 硬切，可能在 emoji/生僻字处切出孤立代理项；跨块用 `\n` 连接但 `char_start/char_end` 未计入连接符，存在细微偏移。任务文档 S10/N7 已记录，ai 域用 offset 做引用前必须修。
- 建议：在 ai 域引用定位前改为 code-point 安全切分，并把连接符计入 offset。

### P3-8【遗留】真实 OLE2 `.doc` 与断网续传未覆盖

- 现状：B-13 e2e 步骤 8 需要真实 OLE2 二进制 `.doc` 样本，本环境无法程序构造；断网续传是浏览器 DevTools 手工交互，自动化只覆盖 checkpoint 存取/清除逻辑。
- 建议：用用户提供的真实 `.doc` 走一次全链路；在 Chrome 用 DevTools offline 做一次"传一半断网→恢复续传"并记录 checkpoint 与最终 READY。

---

## 六、放行判断与建议

**判断：有条件通过。**

- 代码主体达到可提交质量：分层、事务、幂等、STS 最小权限、状态机、异常契约、真实端到端都已验证。
- 但 B-11 的 2 项 P2 是用户可达的功能缺口，不建议把它们留到推送后再补。
- 建议顺序：
  1. 修 P2-1（状态筛选）与 P2-2（弹窗重置/成功后关闭），补对应前端测试。
  2. 修 P3-1（`40501`）并跑 `pnpm test` + `pnpm build`。
  3. 回填 P3-2~P3-5 文档口径（`/courses`、B-03 `endpoint`、B-10 `parts`/6 例、DocumentTest 40501）。
  4. 重新跑 `mvn test`（135+）+ `pnpm test`（76+）+ `pnpm build`，再按 CLAUDE.md 推送。

建议提交信息（留给用户确认）：

- `fix(web): 文档列表补状态筛选，上传弹窗成功后关闭并清理状态`
- `fix(web): 补齐 40501 错误码常量与契约测试`
- `docs: 回填 courses 契约与 B-03/B-10/40501 口径`

---

## 七、审查过程与限制

- 运行实例为外部启动（PID 62100，11:03:04），未读取其控制台日志；本轮结论以 HTTP 响应、Oracle 落库、Redis 键/消费组为准，未对"未捕获异常 ERROR 计数"下结论。
- 消费组 `cg-document-parse` 中仅 `parse-{host}-62100-{0,1}` 为活跃 consumer，其余为历史长空闲 consumer；未发生新旧实例混跑。
- 未做浏览器截图/视觉 QA；前端结论来自代码通读、76 项单测与 `pnpm build`。
- 未覆盖：真实 OLE2 `.doc`、DevTools 断网续传、多实例 Outbox 抢投/consumer 撞名、30 页真实课件（本轮为程序生成 PDF）。
- 审查期间工作区被提交为 `4f982f5`、`87cd6ff`，审查结束时 `git status` 干净、分支领先 `main` 16 个提交。

---

## 八、经验检索与预防清单

### 经验检索结果

- Vault 实际访问：成功（`C:\Users\admin\Temp\ObsidianAgentKnowledgeLoop`）。
- 实际读取：`00-Index/README.md`、`00-Index/Knowledge-Index.md`、`10-Lessons/2026-11-03-document-access后端代码审查与实机回归.md`、`10-Lessons/2026-11-03-document-access整改复审.md`、`20-Patterns/PAT-spring-framework-exception-500.md`。
- 任务分类：backend / frontend / API 契约 / database / testing / security / concurrency。
- 命中经验：`PAT-spring-framework-exception-500`（verified, cross-project）；2026-11-03 两轮 document-access 复盘（project:edudoc）。

### 任务前预防清单

| 经验来源 | 本次必须采取的动作 | 验证方式 | 状态 |
|---|---|---|---|
| PAT-spring-framework-exception-500 | 用异常输入实测而非只看单测 | 404/405/415/400 探针 | 已执行 |
| 2026-11-03 复审 | 拒绝类修复要双向验证（合法输入不被误拒） | e2e 真 PDF/docx 均 READY | 已执行 |
| 2026-11-03 复审 | 依赖运行时上下文的修复查外部存储实际形态 | 扫 Redis 幂等键，确认 userId 段存在 | 已执行 |
| 2026-11-03 复审 | 整改复验前确认无其他实例混跑 | `Get-NetTCPConnection` + `XINFO CONSUMERS` | 已执行 |
| 2026-11-03 两轮复盘 | 不采信自述测试数，独立重跑 | `mvn test` 135、`pnpm test` 76、e2e 21/21 | 已执行 |
| 2026-11-03 复审 | 对 assumption 跳过的真库测试单独核对项数 | 确认 `Tests run: 0` + `[IT-SKIP]` | 已执行 |
| 2026-11-03 复审 | 每条 FAIL 独立复测 | 本轮 e2e 0 FAIL；异常探针中 400 为幂等过滤器预期 | 已执行 |

---

## 九、相关文件

- 任务文档：[02-document-access.md](../tasks/02-document-access.md)
- 手动测试：[DocumentTest.md](../test/DocumentTest.md)
- 上轮审查：[1103-document-access后端代码审查报告.md](1103-document-access后端代码审查报告.md)
- 上轮复审：[1103-document-access后端整改复审报告.md](1103-document-access后端整改复审报告.md)
- 关键代码：`backend/src/main/java/com/cidas/edudoc/document/**`、`backend/src/main/java/com/cidas/edudoc/common/{api,outbox,security}/**`、`web/src/pages/{Documents,DocumentUploadModal,DocumentDetailDrawer}.tsx`、`web/src/upload/ossUpload.ts`
