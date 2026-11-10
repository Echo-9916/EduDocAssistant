# 06 切片 2 · 消息与个人中心（执行计划）

> 设计依据：`docs/design/11_消息与个人中心设计说明.md`（提案已按「可忽略即推进」原则采纳默认口径：
> 未读数单接口、提醒提前量 48h 且每天一封、格式异常只做通知不阻断、运维 ALERT 生产者留切片 4）。
> 依赖：切片 1 已完成（提交/批改是两个生产者的挂点）。

## 任务表

| # | 任务 | 交付 | 验收 |
| --- | --- | --- | --- |
| 2-1 | DDL 与结构同步 | `01_schema.sql` 三表（`notification`/`notification_preference`/`access_policy`）+ `user.mfa_enabled`；`02_indexes.sql` 三索引；`03_triggers.sql` 三个 `trg_*_bu`；`04_seed.sql` 补演示偏好行；`docs/design/数据库设计说明书.md` 同步；实库 ALTER/CREATE 并核对列 | sqlplus 查列与触发器数一致；`mvn -q compile` 通过 |
| 2-2 | 信箱持久层 | `message` 域（或 `access` 下 `notification` 子包）entity/mapper/XML/repository；`dedup_key` 唯一键写入即幂等 | `NotificationRepositoryTest`：重复 dedup_key 不产生第二行 |
| 2-3 | 消息端点 | `GET /messages`（`PageResult<MessageView>`+`unreadCount`）、`PATCH /messages:read`（越权 id 静默跳过并回 `updated`）、`GET/PUT /me/notifications-preference` | 服务层单测覆盖未读过滤、批量已读、越权跳过 |
| 2-4 | 事件与 Worker | `NOTIFICATION_REQUESTED` 事件类型 + `application.yml` `stream-keys` 登记 + `NotificationWorker`（沿用既有骨架） | 定向测试：事件落库→消费→按偏好过滤→信箱行数 |
| 2-5 | 四个生产者接线 | 提交→教师、批改→学生、格式 BLOCKER>0→文档 owner、`@Scheduled` 每小时扫临近截止未交学生 | 端到端：提交后教师 `/messages` 出现该类；重复提交不重复发信（dedup） |
| 2-6 | 个人中心与安全 | `/me/profile` GET/PUT、`/me/security` GET/PUT（OPS 关双因素 40301）、`/me/password`（改密撤销其余会话）、`/me/sessions` GET/DELETE（Redis）、`/me/login-records`（读 audit `AUTH`） | 单测 + curl：改密后旧 token 40102；当前会话不可下线 |
| 2-7 | 前端接线 | `api/messages.ts`、`api/me.ts`；`shared/Messages.tsx`、`shared/Profile.tsx` 换真并去掉「演示数据」标记；删除 `mock/domain/messages.ts`、`mock/domain/profile.ts` | `tsc` 零错误、`vitest` 全绿、`smoke-facade.mjs` 三端消息与个人中心进 LIVE 名单且 0 失败 |
| 2-8 | 文档 | `docs/test/MessageTest.md` 手工步骤；`docs/tasks/06-slice2-完成记录.md`；待确认清单若有新增 | 逐条实跑留证 |

## 顺序与并行

2-1 → 2-2 → 2-3 → 2-4 → 2-5 串行（生产者依赖 Worker）；2-6 与 2-3/2-4 并行（独立端点）；2-7 等 2-3 与 2-6 落地。

## 本切片明确不做

邮件/企业微信等外部投递（`channel='EMAIL'` 只留值）；运维 `ALERT`/`APPROVAL` 生产者（切片 4）；
`access_policy` 的读写端点（切片 4 屏 13/24），本切片只用它的会话口径常量。
