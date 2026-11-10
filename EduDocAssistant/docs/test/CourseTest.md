# 课程与作业 · 验收说明（06 切片 1）

覆盖端点：`/teacher/courses`、`/teacher/courses/{id}/member-imports`、`/teacher/assignments`、`/teacher/assignments/{id}/unsubmitted`、`/teacher/submissions`、`/teacher/submissions/{id}/grade`、`/teacher/workbench-summary`、`/student/assignments`、`/student/assignments/{id}/submissions`，以及审计查询 `/ops/audit-events`。

## 前置

```powershell
docker ps               # 需要 oracle-dev、redis-dev 在跑
cd EduDocAssistant/backend
mvn spring-boot:run     # http://localhost:8080（vite proxy 指向它）
cd ../web
pnpm dev                # http://localhost:5173
```

- 账号：`student` / `teacher` / `ops`，口令 `Cidas@123`（`db_seed/seed_accounts.csv`，显示名已由 `SeedDataRunner` 对齐为 张同学 / 李老师 / 赵工）。
- 启动期幂等种子会补齐：两门课（Web开发技术 CS101、数据库原理 MATH201）、课程组成员与 `group_name`、1 份 PUBLISHED 作业（含 5 项评分标准、`require_format_check='N'`）+ 1 份 DRAFT 作业。
- **提交链路要求文档真实存在**（后端只读库，不下载文件），所以演示前确认 `student` 至少有一份 `READY` 文档；没有就先走一遍 `docs/test/DocumentTest.md` 的上传-解析闭环。

## 1. 自动化

```bash
cd EduDocAssistant/backend && mvn -q test                      # 全量，含 coursework/审计查询
mvn -q test -Dtest='TeacherCourseworkServiceTest,StudentCourseworkServiceTest,AuditQueryServiceTest'
cd ../web && npx vitest run src/api/coursework.test.ts src/api/audit.test.ts \
  src/pages/student/Courses.test.tsx src/pages/teacher/Courses.test.tsx \
  src/pages/teacher/Workbench.test.tsx src/pages/operations/Audit.test.tsx
```

> 注意：`mvn clean` 在有实例跑着时会失败（`target/template-sample.docx` 被占用），改用 `rm -rf target/classes target/test-classes`；`mvn -q test -Dtest=X` 可能跑**过期 class**，核对 surefire XML 的 `tests="N"` 是否等于源码里的 `@Test` 数。

## 2. 手工主链路（curl）

```bash
T=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
    -d '{"username":"teacher","password":"Cidas@123"}' | jq -r .data.token)
S=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
    -d '{"username":"student","password":"Cidas@123"}' | jq -r .data.token)
K() { uuidgen; }

# 1) 课程列表：期望 2 门课、groups≥1、students≥1、dueWeek 有数
curl -s "localhost:8080/api/v1/teacher/courses?pageSize=100" -H "Authorization: Bearer $T" | jq '.data.total, .data.items[0]'

# 2) 发布作业（dueTime 必须带偏移；rubric 至少一项且 maxScore>0）
curl -s -X POST localhost:8080/api/v1/teacher/assignments -H "Authorization: Bearer $T" \
  -H "Idempotency-Key: $(K)" -H 'Content-Type: application/json' -d '{
  "courseId":"<上一步的 courseId>",
  "title":"验收作业-日期","requirement":"按模板提交","dueTime":"2026-11-30T23:59:59+08:00",
  "allowLate":"Y","submissionFormat":"BOTH","requireFormatCheck":"N",
  "rubric":[{"name":"需求分析","maxScore":20},{"name":"格式规范","maxScore":20}]}' | jq .data

# 3) 学生作业列表（只显示 PUBLISHED/CLOSED；草稿不可见；含服务端算好的 remainText 与 format）
curl -s "localhost:8080/api/v1/student/assignments?pageSize=100" -H "Authorization: Bearer $S" | jq '.data.items[]|{title,status,remainText,format}'

# 4) 提交（文档必须属于该学生且 READY）
D=$(curl -s "localhost:8080/api/v1/documents?status=READY&pageSize=1" -H "Authorization: Bearer $S" | jq -r '.data.items[0].id')
curl -s -X POST "localhost:8080/api/v1/student/assignments/<assignmentId>/submissions" \
  -H "Authorization: Bearer $S" -H "Idempotency-Key: $(K)" -H 'Content-Type: application/json' \
  -d "{\"documentId\":\"$D\"}" | jq .data            # 期望 {submissionId,attemptNo:1,status:"SUBMITTED"}

# 5) 未交名单（本期只出名单 + 写审计，不投递）
curl -s "localhost:8080/api/v1/teacher/assignments/<assignmentId>/unsubmitted" -H "Authorization: Bearer $T" | jq .data

# 6) 批改（总分由服务端按分项求和，项名必须命中 rubric）
SID=$(curl -s "localhost:8080/api/v1/teacher/submissions?pageSize=100" -H "Authorization: Bearer $T" | jq -r '.data.items[0].id')
curl -s -X PUT "localhost:8080/api/v1/teacher/submissions/$SID/grade" -H "Authorization: Bearer $T" \
  -H "Idempotency-Key: $(K)" -H 'Content-Type: application/json' \
  -d '{"items":[{"name":"需求分析","score":16},{"name":"格式规范","score":16}],"feedback":"参考文献补卷期号"}' | jq .data   # 期望 score=32

# 7) 工作台聚合（collabActive 恒 0，协作统计随切片 3）
curl -s localhost:8080/api/v1/teacher/workbench-summary -H "Authorization: Bearer $T" | jq '.data|{dueThisWeek,pendingGrade,formatIssues,collabActive}'

# 8) 审计检索与详情（REQ-09）
curl -s "localhost:8080/api/v1/ops/audit-events?range=24h&pageSize=5" -H "Authorization: Bearer $T" | jq '.data.total, [.data.items[]|{actionType,result,riskLevel}]'
curl -s "localhost:8080/api/v1/ops/audit-events/<eventId>" -H "Authorization: Bearer $T" | jq '.data|{chain:[.chain|length],impacts:[.impacts|length],advice:[.advice|length]}'
```

## 3. 预期拒绝（负例，逐条打过）

| 场景 | 期望 |
| --- | --- |
| 缺 `Idempotency-Key` 的写请求 | 40001（全局 `IdempotencyFilter`，auth 域除外） |
| 同一 `Idempotency-Key` 重放 | 40902 |
| 提交到 DRAFT 作业 | 409「作业尚未发布，不能提交」 |
| 提交他人文档 / 未解析完成的文档 | 40401 |
| 未选修该课程的学生提交 | 40301 |
| 已过截止且 `allowLate='N'` | 409「已过截止时间且不允许迟交」 |
| 非本课程教师发布/查看 | 40301 |
| 评分项缺项或超满分 | 40001 |
| `range` 传 `24d` 等非法值 | 400「range 仅支持 24h、7d、30d」 |
| 无 `AUDIT_READ` 查审计 | 40301 |

## 4. 前端验收（`node TempStore/smoke-facade.mjs`）

- 已接真接口的三页（学生课程与作业、教师课程与作业、教师工作台）**不得**再出现「演示数据 · 后端未接入」标记，且列表必须有真数据行。
- 审计页仍含 mock 分区，页头必须写「部分演示数据：安全事件 / 异常与冲突复核 / 访问策略」。
- 其余 mock 分区保持标记与真行数（脚本逐页断言，24 页全过）。
- 真机结果：`共 24 页，失败 0`，截图 `TempStore/facade-shots/`。

## 5. 已知空档（不是缺陷，勿当 bug 报）

- 提醒未提交学生只写审计不投递；反馈消息要到切片 2 才出现在消息中心。
- 提交弹窗不再有「格式预检清单」：`require_format_check='Y'` 的同步阻断随校验结果回调实现，本期不阻断。
- 成员导入的 CSV 上传框是演示件（后端不解析文件），真实发送的是预览表格里的行数据。
- 下载中心复用 `/conversions`，因此没有「大小/有效期」两列，也没有全量任务列表（需按文档查）。
- 课程列表的 `students` 目前为 1（真实选课人数）；演示要人多需补学生账号与选课种子。
- 审计页「冻结账号」等仍写 mock 数组，不会出现在真实操作日志里（两数据源分叉，随切片 4 收口）。
