# 消息与个人中心 · 验收说明（06 切片 2）

覆盖端点：`/messages`、`/messages/unread-count`、`PATCH /messages:read`、`/messages:read-all`、`/me/profile`、`/me/security`、`/me/password`、`/me/sessions`、`/me/login-records`、`/me/notification-preferences`。
异步链路：业务事务 → `edudoc_outbox_event` → `edudoc:stream:notification` → `NotificationWorker` → `edudoc_notification`。

## 前置（三端）

```powershell
docker ps                        # oracle-dev、redis-dev 在跑
cd EduDocAssistant/ai-service && .venv/Scripts/python.exe -m uvicorn app.main:app --port 8000
cd EduDocAssistant/backend   && mvn spring-boot:run          # 8080
cd EduDocAssistant/web       && pnpm dev                     # 5173
```

- 账号：`student`/`teacher`/`ops` + `Cidas@123`。
- ai-service 未起时 `/api/smoke` 返回 500（探针连不上 8000），**业务端点不受影响**。
- Worker 开关在 `application.yml` 的 `edudoc.notification.enabled`；关掉后消息不再投递、临近截止扫描也停（扫描复用同一开关，避免产生无人消费的事件）。

## 1. 自动化

```bash
cd EduDocAssistant/backend
EDUDOC_DB_USERNAME=user_cidas EDUDOC_DB_PASSWORD=cidas123 mvn -q test     # 真库 IT 参与
mvn -q test -Dtest='NotificationWorkerTest,DeadlineReminderSchedulerTest,FormatCheckTxServiceNotificationTest,InboxServiceTest,NotificationRepositoryTest,MeServiceTest,MapperSqlSmokeIntegrationTest'
cd ../web && npx tsc --noEmit && npx vitest run
```

> 两个坑：`mvn clean` 会被在跑实例锁住 `target/template-sample.docx`（先停实例），`mvn -q test -Dtest=X` 可能跑过期 class（`rm -rf target/classes target/test-classes` 后重跑，并用 surefire 的 `tests="N"` 与源码 `@Test` 数核对）。

## 2. 主链路（已实测，2026-11-11）

| 步骤 | 操作 | 期望（实测结果） |
| --- | --- | --- |
| 1 | 学生 `POST /student/assignments/{id}/submissions` | 201 `{attemptNo,status:"SUBMITTED"}` |
| 2 | 等 2~3 秒，教师 `GET /messages` | 出现 `type=SUBMISSION`「作业提交通知」，`unread-count` +1 |
| 3 | 教师 `PUT /teacher/submissions/{id}/grade`（分项须与作业 rubric 项名一致） | 200 `{score:80,status:"GRADED"}` |
| 4 | 等 2~3 秒，学生 `GET /messages` | 出现 `type=FEEDBACK`，正文「《…》已批改，总分 80 分。教师评语：…」 |
| 5 | 学生 `GET /student/assignments` | 该作业 `status=GRADED, score=80` |

`GET /messages/unread-count`、`PATCH /messages:read`（只改本人行，越权 id 静默跳过）、`PATCH /messages:read-all` 与上面同批实测通过。

## 3. 负例（自动化已覆盖，手工可复跑）

| 场景 | 期望 |
| --- | --- |
| 同一事件重复投递 / 同日重复扫描 | 不产生第二封信（`uk_notification_1(recipient_id,dedup_key)`） |
| 某类消息在偏好里关闭 | `deliver` 返回 false、不写行 |
| `messages:read` 传空 `ids` | 40001 |
| 改他人消息的已读状态 | `updated` 只计本人命中数，不报错也不泄露 |
| 旧密码错 | **40001**「原密码不正确」（不是 401，否则前端拦截器会把人踢到登录页） |
| 弱密码（<8 位或缺字母/数字） | 40001 |
| SSO 账号改密 | 40001「请通过学校门户修改」 |
| OPS 关闭双因素 | 40301（前端也前置拦截并提示必须开启） |
| 当前会话下线 / 任意会话下线 | 40901（无会话索引，令牌自然过期，见待确认 B2） |
| `login-records?range=24d` | 40001 |

## 4. 前端验收

- `node TempStore/smoke-facade.mjs`：24 页 0 失败。三端消息页属于「已接真接口」，**不得**再出现「演示数据 · 后端未接入」；运维端标题仍是「消息与告警」并标注「告警与审批类消息随切片 4 产生」。
- 个人中心页：会话「下线」按钮点击后如实提示后端 40901 文案、**不从表里删行**；登录记录的 IP/设备为空时显示「-」（审计写入侧缺口，B3）。
- 改密成功后应清 token 并跳登录页。

## 5. 已知空档（不是缺陷）

- 临近截止提醒按小时扫描（cron 每小时第 10 分钟），现场演示不会立刻触发；需要时手动 `UPDATE edudoc_assignment SET due_time = SYSTIMESTAMP + INTERVAL '2' HOUR` 后等一次扫描。
- 同一提交重复批改不再发第二封（去重键=submissionId）；要每次批改都通知属口径变更。
- FORMAT 类消息正文不含文档标题（只带阻塞数与 documentId，前端按 `resourceRef` 跳转）。
- 多实例同时扫描不会重复发信（去重键吸收），但会产生冗余 outbox 行与冗余消费。
- 运维 `ALERT`/`APPROVAL` 类消息没有生产者（切片 4）。