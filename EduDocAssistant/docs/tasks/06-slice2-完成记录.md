# 06-切片2 消息与个人中心 · 完成记录

对应计划：[06-slice2-messages-profile.md](06-slice2-messages-profile.md)（任务表 2-1…2-8）；设计：[11_消息与个人中心设计说明.md](../design/11_消息与个人中心设计说明.md)。
执行口径：2026-11-10「速度优先」五条 + 已拍板（D3：可暂缓即自行决定并记录）。

状态标记：`完成` = 有可复跑证据；`遗留债` = 有意不做，见文末。

## 一、任务台账

| 任务 | 交付物 | 验收 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| 2-1 DDL | `edudoc_notification`（`uk(recipient_id,dedup_key)` 承担投递幂等）、`edudoc_notification_preference`（无记录=接收，EMAIL 仅占位）、`edudoc_access_policy`、`edudoc_user.mfa_enabled`；三索引三触发器；`数据库设计说明书.md` 复刻同步 | 实库与脚本计数一致 | 完成 | 实库 **41 表 / 41 触发器**（不变式「触发器数=表数」成立） |
| 2-2 信箱持久层 | `message/infrastructure/*`（含 `deliver` 的 dedup + 偏好过滤语义） | `mvn test` | 完成 | `InboxServiceTest 14`、`NotificationRepositoryTest 3` |
| 2-3 消息端点 | `/messages`、`/messages/unread-count`、`messages:read`、`messages:read-all`、`me/notification-preferences` | 定向测试 + curl | 完成 | 列表/未读/批量已读/偏好 8 类均 200（见 `docs/test/MessageTest.md`） |
| 2-4 事件与 Worker | `NOTIFICATION_REQUESTED` 事件类型 + `stream-keys` 登记 + `NotificationWorker` + `NotificationWorkerProperties` | `NotificationWorkerTest` | 完成 | 6 例：一事件多收件人、键形状、重放不重复、单人失败不拖累、坏载荷丢弃 |
| 2-5 四类生产者 | 提交→课程教师、批改→提交学生、格式 BLOCKER→文档 owner、`@Scheduled` 48h 临近截止→未交学生 | 定向测试 + 真链路 | 完成 | 38 例；三端同起后实测：提交 2.5s 内教师收「作业提交通知」，批改后学生收「作业批改反馈：…总分 80 分…」 |
| 2-6 个人中心与安全 | `/me/profile`、`/me/security`、`/me/password`、`/me/sessions`、`/me/login-records` | `MeServiceTest` | 完成 | 14 例（OPS 关双因素 40301、资料白名单 40001、改密撤销当前令牌、非法 range 40001 等） |
| 2-7 前端接线 | `api/messages.ts`、`api/me.ts` + 契约测试；`shared/Messages.tsx`、`shared/Profile.tsx` 换真；学生工作台未读角标 | tsc + vitest + 巡检 | 完成 | tsc 0 错；`vitest` 51 文件 **237 例全绿**；巡检 24 页 0 失败（脚本已区分 LIVE / LIVE_ALLOW_EMPTY / PARTIAL / MOCK） |
| 2-8 文档 | 本文件 + `docs/test/MessageTest.md` + 契约速查 §8 + 死 mock 删除 | 逐条实跑 | 完成 | `mock/domain/messages.ts`、`mock/domain/profile.ts` 已删（grep 零引用） |

## 二、过程中改掉的真缺陷

1. **`GET /me/security` 500**：`MeMapper.xml` 的 `SELECT TO_CHAR(MAX(occurred_at), …)` **没有列别名**，Oracle 报 `ORA-17133 标识符或文字无效`（`MAX()` 零行也返回一行，所以必现）。补别名，并把三处时间列统一成 `SYS_EXTRACT_UTC(...) || 'Z'`。
2. **改密旧密码错会把人踢出登录**：原实现回 40101，而前端 `http.ts` 对 401 系一律清令牌硬跳 `/login`，用户连「原密码不正确」都看不到。改为 **40001**，并把「已认证请求里的参数校验不要用 401 系」写进 `CLAUDE.md` 工程约定。
3. **`actorRole` 断言写错（我的测试）**：`resultType=map` 会丢弃 null 列，而 `actor_role` 在 761 行历史数据里只有 247 行有值 → 键可能整个缺席。测试改为「有值时校验」，并把这条事实记进待确认 B3。
4. **测试三层全部 mock 掉 SQL**：本条不是单个 bug，是**类别**缺陷——所以新增 `MapperSqlSmokeIntegrationTest`（真连 Oracle 跑四个 Mapper，无凭据自动跳过）。它第一轮就抓出上面的取列问题同类风险。

## 三、与设计的偏离（已回写设计与契约文档）

- **dedup_key 不拼 recipientId**：设计 §4.3 写 `{eventType}:{aggregateRef}:{recipientId}` 需 88 字符，超 `VARCHAR2(64)` 会让 `deliver` 直接 40001；且 `uk_notification_1` 本身就是 `(recipient_id, dedup_key)`，收件人维度已覆盖。实际为 `{type}:{aggregateRef}[:{yyyyMMdd}]`。
- **会话与登录记录不建表**：会话读 Redis（实为内存 jti 黑名单）、记录读 `audit_log` 的 AUTH，符合库设计「Redis 不作为业务事实来源」。
- **phone 借 `edudoc_user.remark` 存小 JSON**：不加 DDL 的最小可用取舍；将来要正经维护「管理备注」需拆列。
- **重启量**：`PATCH /messages:read-all` 无 body；`PUT /me/notification-preferences` 返回**裸整数**（初稿文档写 `{updated}`，已按代码更正契约速查 §8）。

## 四、验证汇总

- 后端：`EDUDOC_DB_USERNAME/PASSWORD` 注入后全量 `482 例 0 失败 0 错误`（真库 IT 实际执行，不是跳过）。
- 前端：`tsc` 0 错误；`vitest` **51 文件 237 例全绿**。
- 三端同起：Web 5173 / Backend 8080（`/api/smoke` 200，含 ai-service 探针）/ ai-service 8000 `UP`。
- 真链路：见 `docs/test/MessageTest.md` §2，五步全部实测通过。
- 巡检：`node TempStore/smoke-facade.mjs` 24 页 0 失败。

## 五、遗留债

1. **会话精确下线 / 改密即时全撤**：无 user→jti 索引（`TokenRevocationStore` 纯内存），`DELETE /me/sessions/{id}` 一律 40901 并如实提示；改密只撤当前令牌。方案见待确认 **B2**。
2. **审计 `source_ip`/`user_agent`/`actor_role` 写入不全**：自 01 切片起从未写 IP/UA（761 行全空），`actor_role` 仅 247 行有值 → 登录记录与请求上下文大面积为「-」。属既有认证链路，见待确认 **B3**（含修法与成本）。
3. **运维 ALERT / APPROVAL 类消息无生产者**：切片 4 随安全事件表一起做；前端已用 mixedNote 标注。
4. **临近截止扫描按小时**：现场演示需手动改 `due_time` 或临时触发；多实例重复扫描被去重键吸收，但会产生冗余 outbox 行（Dispatcher 本就标注单节点假设）。
5. **重复批改不再通知**：去重键=submissionId，改分重批不发第二封；要每次通知需把 attempt/时间戳进键。
6. **FORMAT 消息正文不含文档标题**：只带阻塞数与 `documentId`，前端按 `resourceRef` 跳转。
7. **事件载荷 4KB 硬上限**：`JSON_SERIALIZE … VARCHAR2(4000)`，扫描按 50 人/事件拆分（≈2KB）；超限需 mapper 换 `RETURNING CLOB`。
8. **通知偏好逐项保存**（改一个 PUT 一项），无批量暂存/放弃。
9. **消息页列表分页 pageSize 跟后端默认 10**，未提供条数切换。

## 六、下一步（切片 3 入口）

切片 3「协作与版本（REST，不建 gateway）」：版本链对比/恢复成新版本、共享范围与链接有效期（`document_share`/`share_grant` 已建表零接线）、协作者与批注（需新表 `document_annotation`）。设计成熟度只到时序图+接口表，动工前补一份 LDD；实时协同（Yjs）按已拍板不做。