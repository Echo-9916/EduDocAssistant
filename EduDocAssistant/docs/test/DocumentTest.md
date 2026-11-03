# 文档接入接口 · 手动测试说明

> 覆盖 02 切片 B-01~B-08 后端接口（课程查询、上传会话、complete 建档、解析闭环、文档查询、回收站与重解析）。结构与 [AuthTest.md](AuthTest.md) 同源，便于脱离 Apifox 手工验证。
> Apifox 可直接导入 [apifox/document-access.postman_collection.json](apifox/document-access.postman_collection.json)：8 组 35 个请求含全部反例；登录/课程/会话变量自动串联，STS 直传由 pre-request 脚本现场做 OSS V1 签名，Body 内嵌 583 字节真 PDF（SHA-256=01cb9afc…，脚本现算 contentHash），按文件夹顺序执行即可走完上传→解析→查询→回收站→重解析全链路。
> **回归基线**：2026-11-03 首轮 32/32；1103 审查整改后复验 **41/41**（新增框架异常/跨用户幂等/内容伪装探针），`mvn test` 130 全绿。

## 环境与前置

- **Base URL**：`http://localhost:8080`（后端 `mvn spring-boot:run` 启动后）
- **依赖服务**：Oracle FREEPDB1（`oracle-dev` 容器）、Redis（`redis-dev` 容器，`redis-cli ping` 应回 PONG）
- **OSS 环境变量**（用户级，不入仓）：`EDUDOC_OSS_ACCESS_KEY_ID` / `EDUDOC_OSS_ACCESS_KEY_SECRET`；bucket=`cidas-oss-bucket`，region=`cn-beijing`，RAM 角色 `edudoc-upload`
- **鉴权头**：`Authorization: Bearer <token>`（登录返回的 `data.token`）
- **幂等键**：所有写接口必须带 `Idempotency-Key`（缺失 40001；同 key 重放 40902）。键作用域 = **认证主体 + 方法 + 路径**（1103 审查 S4 整改），跨用户、跨端点使用相同 key 互不顶替。**业务重试必须换新 key**——幂等过滤器防双击，业务幂等靠上传会话状态机（详见 §3 说明）
- **框架异常契约**（1103 审查 S1 整改，不再塌缩为 500/50001）：未匹配路由 → 404/40401；方法不允许（如 `DELETE /courses`）→ 405/**40501**（整改新增码位，需同步设计文档 §5.1 字典）；Content-Type 不支持 → 415/41501；畸形 JSON / 参数类型错误（`page=abc`）/ 缺必填参数 → 400/40001
- **种子数据**：账号见 AuthTest.md；课程 2 门（CS101 教师授课+学生选课、MATH201 仅教师），启动时 `SeedDataRunner` 幂等写入
- **测试样本**：结构完整的 PDF（可用 `GET /` 前的 `gen_pdf.py` 方式生成；**纯文本改后缀的假 PDF 会被解析置 FAILED**，属预期）
- **观测命令**：

  ```bash
  docker exec redis-dev redis-cli XLEN edudoc:stream:document-parse   # Stream 消息数
  docker exec redis-dev redis-cli XPENDING edudoc:stream:document-parse cg-document-parse  # 首列 pending 应为 0
  docker exec redis-dev redis-cli --scan --pattern 'edudoc:upsession:*'
  docker exec -i oracle-dev sqlplus -s user_cidas/cidas123@//localhost/FREEPDB1  # SELECT status FROM edudoc_document;
  ```

---

## 1. 课程列表（上传表单/筛选数据源）

- **Method / URL**：`GET /api/v1/courses`
- **预期返回**：HTTP 200，`data` 为当前用户成员课程数组 `[{id,courseCode,courseName,termCode,memberRole}]`；teacher 见 2 门，student 见 1 门（CS101）

```bash
curl -s -H "Authorization: Bearer $TT" http://localhost:8080/api/v1/courses
```

## 2. 创建上传会话（STS 签发）

- **Method / URL**：`POST /api/v1/documents/upload-sessions`
- **Header**：`Idempotency-Key: <唯一值>`
- **Body**：

  ```json
  { "courseId": "<CS101 hex>", "title": "第一课件", "fileName": "a.pdf",
    "size": 1048576, "mimeType": "application/pdf", "docType": "COURSEWARE" }
  ```

- **预期返回**：HTTP 201，`data={sessionId,objectKey,bucket,region,endpoint,sts:{accessKeyId,accessKeySecret,securityToken,expiration}}`；STS 有效期 900s，inline policy 精确到单 objectKey
- **反例**：

  | 场景 | 预期 |
  |---|---|
  | `size` > 20MB（22020096） | 41301 文件超过 20MB 上限 |
  | `.txt` / `text/plain` | 41501 仅支持 Word(.doc/.docx) 与 PDF |
  | 教师不传 `courseId` | 40001 教师上传须绑定课程 |
  | student 绑未选课程（MATH201） | 40301 非该课程成员，禁止绑定 |
  | OPS 调用（无 DOCUMENT_CREATE） | 40301 |
  | 标题空白/超 200 字 | 40001 |

## 3. 浏览器直传 + 完成回调（核心事务）

直传：用 §2 返回的 STS 凭证向 `https://{bucket}.oss-cn-beijing.aliyuncs.com/{objectKey}` PUT（前端走 ali-oss，手工可用任意 V1 签名工具；成功回 200 + ETag）。

- **Method / URL**：`POST /api/v1/documents/upload-sessions/{sessionId}/complete`
- **Header**：`Idempotency-Key: <唯一值>`
- **Body**：`{ "etag": "…", "contentHash": "<浏览器实算 SHA-256 hex>" }`
- **预期返回**：HTTP 201，`data={documentId,versionId}`；服务端流程：Redis CAS 锁会话 → headObject 校大小 → **服务端流式实算 SHA-256 比对**（不信任上报值）→ 单事务五写（oss_object/document=PARSING/version=1 UPLOAD/current_version_id/outbox 事件）+ 审计 `UPLOAD_COMPLETED`
- **幂等两层语义**：①同 `Idempotency-Key` 重放 → 40902（过滤器防双击）；②**换新 key** 重放同 sessionId → 200 返回同一组 `{documentId,versionId}`（会话 COMPLETED 重放，业务幂等）
- **反例**：

  | 场景 | 预期 |
  |---|---|
  | sessionId 不存在 | 40401 上传会话不存在或已过期 |
  | `contentHash` 非 64 位 hex | 40001 |
  | hash 与实算不符 | 40901 内容摘要校验失败（会话回 OPEN 可重试） |
  | 未直传/大小不符就 complete | 40901 文件尚未上传完成或大小不符 |
  | 同 owner+课程+标题已存在（含回收站） | 40901 同名文档已存在或位于回收站（2026-11-02 决策：不改标题不加列） |
  | 非本人会话 | 40301 |

## 4. 解析闭环（Outbox → Stream → Worker）

无需调接口。观测：complete 后 `edudoc_outbox_event` 出现 `DOCUMENT_PARSE_REQUESTED`(NEW)；调度器 ≤1s 投 Stream 并置 PUBLISHED；worker 消费 → Tika 提取分块 → 文档转 **READY**，`edudoc_document_chunk` 落库（chunk_no/heading_path/page_no/char_start/char_end/token_count/content_hash/index_status=PENDING），审计 `PARSE_SUCCEEDED`。30 页以内文档应在 60s 预算内（实测单页 0.5s）。

- **失败分支**：损坏文件 → 文档 FAILED + 审计 `PARSE_FAILED(42201)`；重试走 §7 重新解析
- **内容伪装防御**（1103 审查 S5 整改）：纯文本改名 `.pdf` 上传——会话/直传/建档均通过（自报值只做初筛），但解析时按 **Tika 真实探测的 Content-Type 复验**（允许 pdf/msword/OOXML word），不符 → FAILED + 审计；提取文本另有 8M 字符上限防内容爆炸

```bash
curl -s -H "Authorization: Bearer $TT" "http://localhost:8080/api/v1/documents/$DOCID" | grep -o '"status":"[A-Z]*"'
```

## 5. 文档列表

- **Method / URL**：`GET /api/v1/documents?courseId=&type=&status=&keyword=&page=1&pageSize=20`
- **预期返回**：HTTP 200，`data={items:[{id,title,docType,status,courseId,courseName,sizeBytes,mimeType,versionNo,createTime,updateTime}],total,page,pageSize}`；默认 `update_time DESC`；**强制 owner=当前用户**；默认排除 DELETED，显式 `status=DELETED` 查回收站；`pageSize` 钳制 1..100（缺省 20）；keyword LIKE 已转义 `% _ \`；offset 按 long 计算，`page=2147483647` 返回空页而非溢出回首页（1103 审查 S3）
- **反例**：无 token → HTTP 401；`courseId=y`（非 hex）→ 40001（1103 审查 S2，此前穿透 HEXTORAW 变 500/50001）；`page=abc` → 400/40001
- **权限说明**：种子三角色均持有 DOCUMENT_READ（04_seed.sql），该反例无法用种子数据触发，需临时收回权限验证；可触发的等价反例见 §7（OPS 调 parse → 40301）

## 6. 文档详情

- **Method / URL**：`GET /api/v1/documents/{documentId}`
- **预期返回**：200，含 `currentVersionId/versionNo/contentHash/chunkCount/deletedAt/purgeAt`
- **反例**：他人文档 → **40401**（不泄露存在性）；非 hex ID → 40001（防 HEXTORAW 数据库异常，2026-11-03 回归发现并修复）

## 7. 删除 / 恢复 / 重新解析

| 接口 | 权限 | 预期 |
|---|---|---|
| `DELETE /{id}` | DOCUMENT_DELETE | 204；status=DELETED、purge_at=+30d；不动 OSS 对象；审计 DOCUMENT_DELETED |
| `POST /{id}/restore` | DOCUMENT_DELETE | 204；按当前版本有无分块推断 READY/FAILED；撞同名活跃文档 → 40901 |
| `POST /{id}/parse`（body `{}` 或 `{"versionId":"…"}`） | DOCUMENT_EDIT | 200 `{taskId}`（=outbox eventId）；仅 READY/FAILED 可重解析，PARSING/UPLOADING/DELETED → 40901；versionId 仅允许当前版本 |

- **反例**：已删再删 → 40901「已在回收站」；非回收站恢复 → 40901；非 owner → 40401；OPS 调 `parse` → 40301（无 DOCUMENT_EDIT，种子数据可触发的权限反例）；`POST /{id}/parse` 传非当前版本 versionId → 40901

## 8. 登录链路回归（01 切片不回归即视为损坏）

登录 200 → me 200 → logout 204 → 旧 token 40102。详见 AuthTest.md §1-6。

---

## 建议测试顺序

1. `1 课程` → 记录 CS101 的 `id`
2. `2 创建会话` → 记录 `sessionId/objectKey/sts`（反例 41301/41501/40001/40301 可先行）
3. `3 直传 PUT` → `complete`（先跑反例：不存在 40401、hash 非法 40001、hash 不符 40901，再用正确 hash）→ 记录 `documentId`
4. `4 解析闭环` → 轮询详情至 READY，核查查 chunk 行数与 outbox/审计
5. `3 重放`（新 key）→ 同 documentId；同名再传 → 40901
6. `5 列表`（默认/keyword/status=DELETED/pageSize 钳制）→ `6 详情`（本人/他人 40401/非法 id 40001）
7. `7 DELETE→回收站可见→restore→204→再 restore 40901→parse 重解析→READY`
8. `9 Stream 观测`：XLEN 增长、XPENDING 首列=0（无积压）
9. `8 登录链路回归`

## 回归结果

- **首轮（2026-11-03 上午）**：32 项全过。发现并修复：非法 hex 文档 ID 曾触发 `HEXTORAW` 异常返回 50001 → 入口校验 40001（`Ids.isHex16`）。
- **1103 审查整改复验（2026-11-03）**：**41/41 通过**（`mvn test` 130 全绿）。新增探针全部通过：courseId 非 hex → 40001；未匹配路由 → 404/40401；`DELETE /courses` → 405/40501；`page=abc`、畸形 JSON → 400/40001；跨用户同幂等键不再互相顶替；`page=2147483647` 返回空页；纯文本伪装 `.pdf` 建档后经真实类型复验置 FAILED。整改清单：S1 框架异常分支、S2 courseId 入口校验、S3 分页 offset long 化、S4 幂等键作用域（主体+方法+路径）、S5 Tika 内容类型复验+提取上限、S6 SDK 异常原文只进日志、S7 审计序列化失败告警、S8 parts 契约注释纠偏、S9 JSON_SERIALIZE 上限注释、S11 sessionId 统一大写 hex、P3② consumer 名补 pid、P5 解析回写加 `status='PARSING'` 守卫。
