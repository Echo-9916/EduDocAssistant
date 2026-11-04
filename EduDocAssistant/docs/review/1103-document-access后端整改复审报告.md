# 1103 文档接入后端整改复审报告（第二轮）

> 复审日期：2026-11-03　复审人：Codex（根 Agent，双轴由根 Agent 亲自完成）
> 上一轮报告：[1103-document-access后端代码审查报告.md](1103-document-access后端代码审查报告.md)
> 整改提交：`4483632` `fix(backend): 1103 审查整改——框架异常契约、幂等作用域、分页溢出、courseId 校验、内容类型复验`
> 本轮基线：`git diff f8cfee0..4483632`，23 个文件 +531/-37
> 结论：**上一轮 1 项 P1、4 项 P2 全部关闭并独立复验通过**；新发现 **1 项 P2**（S5 整改引入的类型白名单过窄）+ 若干 P3。建议修掉新 P2 或明确接受后再推送。

---

## 一、结论摘要

整改质量高：五个必修项都改在根因上而不是再打局部补丁，`GlobalExceptionHandler` 补齐框架异常分支后**整轮实机回归的服务端日志里 `未捕获异常` ERROR 记录为 0 条**，说明 5xx 噪声是真的消失了而不是被换个地方吞掉。P5 的状态守卫还顺手修掉了一个上一轮我没发现的潜在缺陷（迟到的解析回写会把软删文档从 DELETED 覆盖回 READY，等于复活已删文档）。

新引入的风险集中在一处：S5 的"真实内容类型复验"用了**三个精确字符串的白名单**，而上传会话层仍按**扩展名**放行。两层判据不一致，导致"会话通过 → 对象入库 → 建档成功 → 解析 FAILED"这种先接受后失败的体验。已实测复现：RTF 内容改名 `legacy.doc` 会以 `application/rtf` 被拒。而 `.doc` 这条路径**没有任何自动化测试覆盖**，B-13 的验收步骤 8 恰恰要求"老式 .doc 上传解析不崩"。

---

## 二、整改项复验结果（全部实测，非引用提交说明）

提交说明声称"`mvn test` 130 全绿；真机探针 41/41"。我没有采信，独立重跑：

- **`mvn test`：130 通过 / 0 failure / 0 error / 0 skipped**（上一轮 125，新增 5 项：GlobalExceptionHandlerTest 4→5、IdempotencyFilterTest 4→5、DocumentQueryServiceTest 7→9、TikaTextExtractorTest 2→3）。`RbacLifecycleIntegrationTest` 仍为 `Tests run: 0`。
- **实机复验：52 项断言，50 PASS / 2 FAIL，两项 FAIL 复核后均为我的测试脚本缺陷，产品行为正确**（详见第四节）。另加 curl 定向探针 3 项、内容伪装探针 2 项。
- 环境：真实 Oracle FREEPDB1 + Redis + 阿里云 OSS。复验前确认 8080/8081 均无进程，**由我独占启动单实例**（新代码），避免新旧代码混跑污染结论。

### S1【P1 已关闭】框架异常不再塌缩 500

`GlobalExceptionHandler` 新增 4 组 handler，并抽出 `clientError(...)` 统一按 WARN 级记录、只回固定文案。实测逐项：

| 输入 | 上一轮 | 本轮实测 |
|---|---|---|
| `GET /api/v1/definitely-not-a-route` | 500 / 50001 | **404 / 40401** |
| `GET /api/v1/documents/`（空路径变量） | 500 / 50001 | **404 / 40401** |
| `DELETE /api/v1/courses` | 500 / 50001 | **405 / 40501** |
| complete 传畸形 JSON `{"etag":` | 500 / 50001 | **400 / 40001**（日志：`JSON parse error: Unexpected end-of-input`） |
| complete `etag` 传数组 | 500 / 50001 | **400 / 40001**（日志：`Cannot deserialize value of type String from Array value`） |
| `?page=abc` / `?pageSize=abc` | 500 / 50001 | **400 / 40001**（日志：`Failed to convert value of type String to Integer`） |
| `Content-Type: text/plain` / `application/xml` / 无 Content-Type | 500 / 50001 | **415 / 41501**（curl 三种变体全部确认） |

回归保护也确认未被削弱：缺 `Idempotency-Key` 仍 `40001`（没有被新分支吞掉）；真实服务端故障仍走 `Exception` 兜底 500（`GlobalExceptionHandlerTest.unknownException` 覆盖）。**整轮 52 项实机断言期间，服务端日志 `未捕获异常` ERROR 计数 = 0。**

### S2【P2 已关闭】courseId 入口 hex 校验

`DocumentQueryServiceImpl.list` 在进 SQL 前校验，并 `verify(repository, never()).count(...)` 证明短路。实测：`courseId=y` → `400/40001 courseId 格式不合法`；`courseId=ZZZZ` → 40001；全 0 合法 hex → 200 且 `total=0`；真实 courseId → 200 且 `total=13`；空串 → 200（视为不筛选，行为合理）。

### S3【P2 已关闭】分页 offset long 化

`long offset = (long)(page-1)*pageSize` 贯穿 Service → Repository → Mapper（`@Param("offset") long`）。实测：

- `page=2147483647&pageSize=2` → **200 且 items=0**（上一轮是返回第 1 页的 2 条数据）。Oracle 接受 offset=4294967292 并正常返回空集，未产生新异常。
- `page=99999999&pageSize=100` → items=0；`page=1&pageSize=2` → items=2；`page=-1&pageSize=-5` → 仍钳制 1/20。
- 单测 `hugePageDoesNotOverflowToFirstPage` 断言 `offset == 4294967292L` 且 items 为空，是有意义的断言。

### S4【P2 已关闭】幂等键作用域 = 主体 + 方法 + 路径

这是本轮我重点怀疑的一项：`scopedKey()` 依赖 `AuthContext.get()`，而 `IdempotencyFilter` 是裸 `@Component` 过滤器、`JwtAuthenticationFilter` 挂在 Security 链上（`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`）。**如果两者顺序不对，`principal` 会是 null，作用域退化成 `-:METHOD:URI:key`，跨用户隔离形同虚设——而单元测试手工 `AuthContext.set(...)`，永远测不出这个问题。**

实测结论：顺序是对的，修复真实有效。

- teacher 与 student 用**同一个** `Idempotency-Key` 调同一端点：**两者都 201**（上一轮 student 被顶成 40902）。
- Redis 实际键形如 `edudoc:idem:5F2F10AD580424B6E063020011AC034B:POST:/api/v1/documents/upload-sessions:SCOPE-TEST-KEY-2134655211` —— **userId 段已填充**，直接证明过滤器执行时主体已就位。
- 扫描 `edudoc:idem:*SCOPE-TEST-KEY*` 共 3 个键，**无一个以 `-:` 开头**（退化形态为零）。
- 防双击未回归：同用户 + 同端点 + 同 key → 仍 `409/40902`。
- 同 key 换路径不互相阻塞：同 key 用于 `DELETE /documents/{id}` → 204。
- auth 域未受影响：`/api/v1/auth/**` 仍在 `requiresIdempotency` 白名单外；logout 204、旧 token 401/40102 全部通过。

### S5【P2 部分关闭 → 见新发现 N1】真实内容类型复验

生效部分（实测）：

- 纯文本改名 `spoof.pdf` → 会话/直传/建档均通过（自报值只做初筛），解析阶段 **FAILED**，审计 `PARSE_FAILED / 42201`，`detail_json.reason` 记录 `文件实际类型不在支持范围：text/plain; charset=ISO-8859-1`，**chunk 行数 = 0**。
- HTML 改名 `page.docx` → FAILED，reason 记录 `text/html; charset=ISO-8859-1`。
- **真 PDF 与真 .docx 仍正常 READY（0.7s / 0.7s），没有误拒**——这是此项整改最大的回归风险，已排除。
- 8M 字符提取上限已加入。

未关闭部分见第四节 N1。

### 顺手项复验

| 项 | 状态 | 实测/读码依据 |
|---|---|---|
| S6 SDK 异常原文只进日志 | 已关闭 | `OssStorageAdapter` 四处改为 `log.warn(..., e)` + 固定文案（"上传凭据签发失败，请稍后重试"等）；客户端响应不再含 endpoint/RoleArn/RequestId |
| S7 审计明细序列化失败告警 | 已关闭 | 补 `log.warn`，且只打 `detail.keySet()` 不打 value——避免把业务明细写进日志，这个取舍是对的 |
| S8 `parts` 假契约 | 已关闭（按文档口径） | Javadoc 改为"接收但**不校验、不落库**"，并显式警告 B-10 不要依赖此处回读分片清单。字段仍在但不承诺，属可接受的文档化处置 |
| S9 `JSON_SERIALIZE` 4000 上限 | 已关闭（文档化债务） | mapper 补注释说明上限与 CLOB 迁移路径 |
| S11a 全限定名 | 已关闭 | `detail` 改用 import 后的 `Ids.isHex16` |
| S11b objectKey 大小写 | 已关闭 | sessionId 改走 `Ids.hex16()`；实测 objectKey = `edudoc/uploads/202611/5F2F10AD580424B6E063020011AC034B/6FC9B9257C35480D81AF6782F84E4BC8-r2.pdf`，**两段 hex 均大写** |
| P3② consumer 名 | 已关闭 | `parse-{host}-{pid}-{index}`，`ProcessHandle.current().pid()` |
| P5 解析回写守卫 | 已关闭，且修掉一个我上轮漏报的缺陷 | 新增 `updateStatusFromParsing`（`AND status='PARSING'`），success/failure 双向守卫，返回 0 行则整笔跳过 |
| P1/P2/P4 文档漂移 | 已关闭（回填口径） | 任务文档新增"文档口径回填"节，明确 Redis 会话与 Stream 消息**以代码为事实契约**、"ai 域接线以此为准"、PARSING 由生产方置位 |
| P6 不可触发的反例 | 已关闭 | `DocumentTest.md` 改为说明"种子三角色均持 DOCUMENT_READ，该反例需临时收回权限验证"，并给出可触发等价反例；实测 OPS 调 `parse` → **40301** 成立 |

### P5 附带修掉的潜在缺陷（上一轮我漏报）

上一轮我只把 `writeFailure` 无条件置 FAILED 记为 P3。**实际影响比我的定级更重**：`writeSuccess` 同样无条件 `updateStatus(documentId,"READY")`，因此一个迟到的解析事件可以把处于 `DELETED` 的文档覆盖回 `READY`，等于复活已删文档、绕过回收站与 purge_at 语义。加了 `AND status='PARSING'` 后此路径被封死。本轮实测：软删 docx 文档后等待 3s，状态仍为 `DELETED`（`P5-NOREVIVE` 通过），未被回写复活。

---

## 三、主链路无回归确认

整改触及 `common/api`（跨域公共件）与解析事务边界，因此重跑了主链路：列表 200 且 11 字段齐全（total=16）、owner 隔离（student 访问 teacher 文档 40401）、`keyword='%'` 仍被转义、`pageSize=1000` 仍钳制 100、回收站可见已删文档、恢复 204 且回 READY、OPS 调 parse 40301、21MB→41301、`.txt`→41501、auth me 200、logout 204 + 旧 token 401/40102、`XPENDING` 首列=0（XLEN=31）、outbox 31 条全 PUBLISHED 无 NEW/FAILED 残留。重解析后 `chunkCount` 前后一致（1→1，不翻倍）。

---

## 四、本轮新发现

### N1【P2】内容类型白名单过窄，与扩展名放行层判据不一致，且 `.doc` 路径零测试覆盖

`TikaTextExtractor.ALLOWED_CONTENT_TYPES` 只有三个精确字符串：`application/pdf`、`application/msword`、`application/vnd.openxmlformats-officedocument.wordprocessingml.document`。而会话创建层（`UploadSessionServiceImpl.requireSupportedFile`）按**扩展名 + 自报 mimeType** 放行 `.doc/.docx/.pdf`。两层判据不同源，产生"先接受、后失败"。

实测复现（均为真实上传 → 直传 → complete → 解析全链路）：

| 样本 | 扩展名/自报 mime | 会话+建档 | 解析结果 | 审计 reason |
|---|---|---|---|---|
| RTF 文本 | `legacy.doc` / `application/msword` | **通过** | **FAILED** | `文件实际类型不在支持范围：application/rtf` |
| Word 导出的 HTML | `page.docx` / OOXML word | 通过 | FAILED | `…：text/html; charset=ISO-8859-1` |
| 纯文本 | `spoof.pdf` / `application/pdf` | 通过 | FAILED | `…：text/plain; charset=ISO-8859-1` |
| OLE2 魔数但结构损坏 | `ole.doc` / `application/msword` | 通过 | FAILED | `TIKA-198: Illegal IOException from OfficeParser`（此项属真损坏，FAILED 正确） |

风险点：

1. **RTF、Word 导出的 HTML、Word 2003 XML 三类在真实场景里常常就带着 `.doc` 扩展名**（Word 本身可另存为这些格式，历史文件尤其多）。它们在上一版代码里能被 Tika 正常提取文本并转 READY，现在会硬失败。
2. Tika 对无法细分的 OLE2/OOXML 容器会给出泛型类型 `application/x-tika-msoffice` / `application/x-tika-ooxml`，两者都不在白名单内，同样会 FAILED。
3. **`.doc` 这条路径完全没有自动化测试**：`TikaTextExtractorTest` 三个用例是 docx 标题栈、pdf 页码、纯文本伪装拒绝，没有 `application/msword` 样本。而 B-13 的 e2e 步骤 8 明确要求"老式 .doc 上传解析不崩（heading_path 允许为空）"，风险 R7 也是围绕 .doc 写的。
4. 失败后**没有恢复路径**：`FAILED` 态允许重解析，但重解析走的是同一份对象与同一套判据，必然再次 FAILED（上一轮 `JUNK-REFAIL` 已证失败是稳定幂等的），用户只能删除重传。

好的方面：审计 `detail_json.reason` 完整记录了 Tika 探测到的类型，可诊断性到位；失败不落任何 chunk，数据干净。

建议（按优先级）：

- 白名单补 `application/x-tika-msoffice`、`application/x-tika-ooxml`；就 `application/rtf`、`text/html`、`application/xml`（Word 2003 XML）做一次**明确决策**——要么纳入（与"扩展名 `.doc` 放行"保持一致），要么在会话创建层就拒（让用户立刻拿到 41501，而不是入库后再 FAILED）。
- 补一个 `application/msword` 样本的 `TikaTextExtractorTest` 用例（POI `HWPFDocument` 可程序生成，与现有 docx 用例同风格），把 B-13 步骤 8 的风险前移到单测。
- 若决定保持严格白名单，请在 `DocumentTest.md` 与任务文档 R7 里写明"非 pdf/msword/OOXML-word 的真实类型一律 FAILED 且重解析不可恢复"，避免 B-13 联调时按旧口径验收。

### N2【P3】`40501` 已对外发出，但权威错误码字典里没有它

`ErrorCode` 新增 `METHOD_NOT_ALLOWED(40501, 405)`，代码注释与任务文档都诚实标注了"§5.1 字典未含 405 码位"。实测 grep `docs/design/详细设计说明书.md`：字典含 `40401`、`41501`，**不含 `40501`**；本轮提交只改了 `docs/tasks/` 与 `docs/test/`，未动 `docs/design/`。

按 `CLAUDE.md`，`docs/` 是需求与设计的事实来源，错误码沿用设计文档。现状是一个已对外发出的公共错误码在权威字典中不存在。连带影响：`ErrorCodeTest.countMatchesDesignDictionary` 的断言从 19 改成 20，**测试名声称"与设计字典一致"，断言内容却正好相反**。B-09 要按 §5.1 定义前端 `ErrorCode` 联合类型，照抄会漏掉 40501。

建议：回填《详细设计说明书》§5.1（并按 CLAUDE.md 同步 Word 源文件），或把该测试改名为 `countMatchesImplementation` 并在注释里指向待回填项。任务文档已把它列为遗留，方向是对的，但前端接线（B-09）在回填之前就会开始。

### N3【P3】新测试证明了映射，没证明分发

`GlobalExceptionHandlerTest.frameworkExceptionsMapToClientErrors` 直接 `new` 出框架异常再调 handler 方法，断言状态码与业务码。这证明了**映射关系**正确，但没有证明 Spring MVC 真的会把这些异常类型路由到这些 handler——中间隔着 Security 链、`HandlerExceptionResolver` 顺序、以及 `NoResourceFoundException` 是否被静态资源处理器先接走等环节。注释写"8 类框架输入"，实际只断言了 4 类（`MethodArgumentTypeMismatch`、`MissingServletRequestParameter`、`MissingRequestHeader`、`BindException` 共用 `handleMalformedRequest`，未单独断言）。

本轮的 52 项实机探针确实端到端确认了分发正确（含 curl 验证的三种 415 变体），所以**行为没问题**；但自动化套件挡不住将来的回归，比如有人调整 Security 链顺序后 404 又被别处接走。建议补一个 `@WebMvcTest` 或 MockMvc 切片，对"未匹配路由 / 错误方法 / 畸形 body"各断言一次真实 HTTP 响应。

### N4【P3 · 读码推断，未实测触发】回写被跳过时 worker 仍记"解析成功"

`DocumentParseTxServiceImpl.writeSuccess` 在 `markStatusIfParsing(...) == 0` 时 `log.info("解析结果回写跳过…")` 后直接 `return`；调用方 `DocumentParseWorker.process` 无法区分"真写入"与"被跳过"，紧接着仍会 `log.info("解析成功 documentId=… chunks=N")`，同时照常 XACK 且不落审计。

标注为推断的原因：本轮实机运行中 `解析结果回写跳过` 日志出现 **0 次**（跳过路径未被触发），也没有单元测试覆盖 `markStatusIfParsing` 返回 0 的分支，所以我没有观察到实际的误导性日志。建议 `writeSuccess` 返回 boolean，让 worker 按实际结果记日志；同时给跳过分支补一个单测。

### N5【P3】提取上限的实现与注释不符

`MAX_TEXT_CHARS` 靠在 SAX `characters()` 回调里 `throw new IllegalStateException(...)` 生效，注释写"SAX 异常沿 extract 转 IOException → worker 置 FAILED"。但 `extract()` 的 catch 只覆盖 `SAXException | TikaException`，`IllegalStateException` 是非受检异常，**不会被转成 IOException**，而是原样穿透。最终仍被 `DocumentParseWorker.handle` 的 `catch (Exception e)` 兜住并置 FAILED，所以**行为正确、注释错误**。另外 `totalChars` 只在 `mode != null`（即处于 `<p>`/`<h1-6>` 内）时累加，元素外文本不计入上限。建议修正注释，并把上限计数移到无条件累加。

### N6【P3 · 遗留未修，连续第二轮】真库集成测试在默认 `mvn test` 下静默跑 0 项

`RbacLifecycleIntegrationTest` 仍为 `Tests run: 0`（无 Oracle 凭据时被 assumption 跳过）。本轮 130 全绿里真库测试实为 0 项。任务文档已把它列为遗留，但这是连续第二轮出现同一个可观测性问题：总数增长会让人误以为真库路径被覆盖。修法很轻（跳过时打印一条显式 WARN，或用 profile 让缺凭据直接失败），建议别再往后放。

### N7【P3 · 已确认接受】分块 offset 漂移与代理对切断（上一轮 S10）

任务文档已明确记为遗留，并注明"ai 域用 offset 做引用定位前须修"。本轮未改动，接受该处置，仅在此留痕以便 ai 域开工时能检索到。

---

## 五、两项 FAIL 的定性（诚实记录）

52 项实机断言中有 2 项 FAIL，**两项都是我的测试脚本缺陷，产品行为正确**：

1. `S1-415`：脚本 `Api` 函数的参数名是 `-CT`，我却用 `-RawContentType` 调用，导致 Content-Type 落回默认的 `application/json`，于是 body `x=1` 被当 JSON 解析 → `400/40001`（这本身也是正确行为）。改用 curl 显式发 `text/plain`、`application/xml`、无 Content-Type 三种请求，**全部 `415/41501`**。
2. `S11-CASE`：脚本按 `objectKey -split "/"` 取 `$kp[2]` 当 userId 段，但前缀 `edudoc/uploads` 占两段，`$kp[2]` 实际是 `202611`（年月），比较对象错位。脚本打印的完整 key 显示 userId 段与 sessionId 段**均为大写**，整改生效。

上一轮我也犯过同类错误（11 项 FAIL 里 10 项是脚本问题）。这次的教训已写进知识库：出报告前每条 FAIL 必须有一次独立复测。

---

## 六、放行判断

**必修项状态**：上一轮 1 项 P1（S1）+ 4 项 P2（S2/S3/S4/S5）——S1/S2/S3/S4 **完全关闭**，S5 **主体关闭但引入 N1**。

**建议推送前处理**：

1. **N1**（P2）——补白名单或在会话层前移拒绝，并补 `.doc`/`application/msword` 单测。这是本轮唯一的 P2，且直接关系 B-13 验收步骤 8。若判断"严格白名单"就是产品意图，也可以**明确接受**，但需要把口径写进 `DocumentTest.md` 与任务文档 R7，否则 B-13 联调会按旧口径判失败。
2. **N2**（P3）——`40501` 回填《详细设计说明书》§5.1。建议赶在 B-09 前端定义 `ErrorCode` 之前完成，否则前端会漏码位。

**可随 B-09/B-10/B-13 处理**：N3（补 MockMvc 分发测试）、N4（`writeSuccess` 返回 boolean + 跳过分支单测）、N5（注释与计数位置修正）、N6（真库测试 0 项的可观测性）、N7（分块 offset，已标注 ai 域前须修）。

按 `CLAUDE.md`"阶段任务全部完成（通过测试、审查、复审）后推送"的口径：本轮**复审结论为"整改有效，待 N1 处置后即可放行"**。相比上一轮的"审查未通过"，状态已实质推进。

---

## 七、复审过程与限制说明

- **经验检索**：本轮为同一会话内的后续任务，Vault 已在上一轮实际检索并归档；本轮直接复用了上一轮写入的 `10-Lessons/2026-11-03-document-access后端代码审查与实机回归.md` 与 `20-Patterns/PAT-spring-framework-exception-500.md`。据其落实的预防动作：①出报告前对每条 FAIL 做独立复测（据此排除 2 项脚本假阳性）；②不采信提交说明里的"41/41"，独立重跑并扩大到 52 项；③对"单元测试通过"不等于"真实链路正确"保持警惕——S4 的过滤器顺序问题正是单测手工 `AuthContext.set(...)` 无法覆盖的，靠实扫 Redis 键形态才确认真实生效。
- **双轴子代理**：未再尝试派发。上一轮与 2026-11-02 两次独立任务均已确认本环境派发子代理会返回 `unsupported_encrypted_agent_content`（已沉淀为 `PAT-subagent-encrypted-content-failure`），本轮直接由根 Agent 完成 Standards 与 Spec 双轴，分开陈述、不跨轴重排序。
- **未修改任何业务代码**：本轮只读复审 + 实机测试。`git status` 在复审开始时为干净，结束时仅新增本报告一个文件。
- **运行环境副作用**：复审开始时 8080/8081 均无监听，我启动了单个实例（8080，新代码）用于复验，**该实例目前仍在运行**，需要时可自行停止或重启。实机测试在共享库产生数据，标题前缀 `R2-`（含 4 篇 FAILED 的伪装样本、1 篇 OLE2 损坏样本、1 篇 RTF 样本）与若干 `S4-*`/`REV-*` 会话残留；`edudoc_document` 增至约 18 行，outbox 31 条全 PUBLISHED，OSS 中新增对应对象。未做清理，与既有"遗留测试数据留作联调"的做法一致。
- **未覆盖**：多实例并发（Outbox 抢投、consumer 撞名——代码以 `// ponytail:` 标注为单机假设）；30 页真实课件的 P-04 60s 预算（本轮仍以 2 页小样本验证，0.7s）；真实老式 `.doc` 二进制样本（无法在本环境构造 OLE2 Word 文档，N1 的 RTF/HTML 变体是可构造的等价证据）；`MAX_TEXT_CHARS` 8M 上限的实际触发（需超大文档，未构造）；前端 B-09~B-12（未开工）。
