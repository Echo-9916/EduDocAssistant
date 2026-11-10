# 06-切片1 课程与作业 + 审计查询 · 完成记录

对应计划：[06-backend-followup.md](06-backend-followup.md) 第二节「切片 1 任务分解」。
执行口径：2026-11-10 追加的「速度优先」五条 + 已拍板五条。
状态标记：`完成` = 有可复跑证据；`进行中` = 有 agent 在写、尚无验证；`遗留债` = 有意不做，见文末清单。

## 一、任务台账

| 任务 | 交付物 | 验收方式 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| 1-1 DDL 与结构 | `01_schema.sql`：`assignment` 加 `submission_format`/`require_format_check`/`rubric_json`、`submission` 加 `score_json`、`course_member` 加 `group_name`；`02_indexes.sql` 两条索引；`04_seed.sql` 新增权限点 `SUBMISSION_SELF_READ` 并授 STUDENT | sqlplus 查列与权限行；实库同步 ALTER | 完成 | `group_name_col=1`；`perm=SUBMISSION_SELF_READ`；SQL 形状逐条在 `oracle-dev` 上跑通 |
| 1-1 coursework 底座 | `CourseworkRules`（迟交判定/学生四态派生/分项校验）、`CourseworkRow`+`CourseworkMapper`(.java/.xml)、`CourseworkRepository`、`CourseworkJson`、`package-info` | `mvn compile` | 完成 | 编译零输出 |
| 1-1 演示种子 | `SeedDataRunner`：账号显示名与课程名对齐原型、student 补进第二门课、`group_name` 回填、1 份 PUBLISHED + 1 份 DRAFT 作业（`require_format_check='N'`，演示不依赖真上传） | `SeedDataRunnerTest` + 实库查询 | 完成 | 实库 `assignments=2`、API 返回 `Web开发技术/李老师` |
| 1-2 持久层 | 同上仓储层（分页装配 + 唯一约束→40902 翻译） | 单测 + 真实 HTTP | 完成 | 见 1-5 实跑 |
| 1-3 教师发布与统计 | `TeacherCourseworkService(Impl)`、`TeacherCourseworkController` | `TeacherCourseworkServiceTest` | 完成 | `Tests run: 6` |
| 1-4 学生作业列表 | `StudentCourseworkService(Impl)`、`StudentCourseworkController` | 定向单测 + HTTP | 完成 | `Tests run: 11`；`/student/assignments` 只返回 PUBLISHED 且状态派生正确 |
| 1-5 提交 | 同上 | 定向单测 + HTTP | 完成 | `POST .../submissions` → `201 {attemptNo:1,status:SUBMITTED}` |
| 1-6 批改 | 同上（分项 JSON、总分服务端算） | 定向单测 + HTTP | 完成 | `PUT .../grade` → `200 {score:80,status:GRADED}`，前端传乱序与缺 `maxScore` 仍归一化 |
| 1-7 课程管理与工作台 | 同上 | 定向单测 + HTTP | 完成 | `Tests run: 6`；`/teacher/workbench-summary` → `{dueThisWeek:1,...,remainText:"5 天后截止"}` |
| 1-9 审计查询 | `AuditQueryService(Impl)`、`AuditQueryController`、`AuditLogMapper` 追加 4 个查询 | 定向单测 + HTTP | 完成 | `Tests run: 7`；`?range=30d` → `total=750`，非法 range → 400 中文提示，详情 `chain/impacts/advice` 齐 |
| 前端接线（1-8） | `api/coursework.ts`、`api/audit.ts`、`DemoPage` 的 `live`/`mixedNote`、`routes.ts` 按 `SUBMISSION_SELF_READ` 收口、四页换真接口 + 测试、删除 `mock/domain/course.ts`（376 行） | 契约测试 + 页面测试 + 全量 + 真机巡检 | 完成 | `npx tsc --noEmit` 0 错误；`npx vitest run` **49 文件 227 例全绿**；真机巡检 **24 页 0 失败**（脚本已升级为区分 LIVE/PARTIAL/MOCK 三类断言）；截图确认教师课程页出的是库里真数据 |
| 验收文档 | `docs/test/CourseTest.md`（手工 curl 全链路 + 10 条负例 + 已知空档清单） | 逐条实跑 | 完成 | 负例中 9 条已在 curl 中实测（DRAFT 提交 409、非法 range 400、幂等缺失 40001 等） |

## 二、过程中改掉的三个真缺陷

1. **未选修学生可提交任意已发布作业**（鉴权第 4 步缺失）→ `StudentCourseworkServiceImpl` 补 `isCourseMember` → 40301 + 用例。发现者：实现该端点的 agent 自报「留待拍板」，判定为必须堵，当场修。
2. **草稿（DRAFT）作业可以提交** → 真机 curl 打出 `SUBMIT->draft 201` 才发现（单测与列表 SQL 都拦不住：列表过滤了 DRAFT，提交端点没查状态）→ 补 409「作业尚未发布，不能提交」+ 用例，8080 复验通过。
3. **`CourseworkMapper.xml` 分节注释写成 `<!-- ---------- X ---------- -->`**：注释体内含 `--` 是非法 XML，**`mvn test` 全绿但应用启动即崩**（MyBatis 只在启动时解析 XML）→ 改 `===== X =====` 并扫描全部 mapper 确认无同类写法。
4. **我自己加的两个冗余索引**：`ix_assignment_course_due(course_id,due_time)` 与 `ix_submission_assignment_student(assignment_id,student_id)`——库里早有 `ix_assignment_course(course_id,status,due_time)` 和 `uk_submission_1(assignment_id,student_id,attempt_no)`，前缀完全覆盖我的查询。已从 `02_indexes.sql` 删除并在实库 drop（索引只增写放大，不白留）。教训：**加索引前必须先查 `user_indexes`，别照着「感觉会用」写脚本**。

## 三、与设计/计划文档的偏离（均已回写 06 文档「落地差异」）

- 评分标准与分项得分走 JSON 列（`rubric_json`/`score_json`），不建 `edudoc_assignment_rubric_item`/`edudoc_submission_rubric_score` 两表；代价：分项不能 SQL 聚合。
- 课程组不落新表，改存 `edudoc_course_member.group_name`；`edudoc_course_group` 仍归切片 3/4（届时一并收口 `share_grant.principal_type='GROUP'` 的悬空引用）。
- 路径统一角色前缀：`/teacher/*`、`/student/*`、`/ops/*`（原任务正文里 `/assignments/{id}/submissions` 一类写法作废）。
- 审计成功码兼容 `'200'` 与 `'SUCCESS'`：既有写入路径两种都写过，严格照 `'200'` 判会把所有成功登录标成 FAIL。
- 文档同步进度：`docs/design/数据库设计说明书.md` 复刻**已补**（`course_member.group_name`、`assignment` 三列、`submission.score_json`，并注明「按速度优先口径不建明细表」的代价）；**唯一剩余欠账是 `05_系统设计/02_数据库设计说明书.docx` 源文件**，需要走 `TempStore/docx-*.py` 工具链改 Word 交付物——属于对外交付文档，改坏成本高，待你点头再动。表数与触发器数无变化（本切片没建新表）。

## 三点五、独立审查结论与处置（2026-11-10，两个审查 agent）

审查口径：**不可放行**（两份报告都点名两条 P0）。以下是逐条处置，已复验：

| 级别 | 问题 | 处置 | 复验证据 |
| --- | --- | --- | --- |
| P0 | 教师 UI 发布作业不传 `allowLate` → 后端 400，屏 24 主流程不可用 | 后端按 DDL 默认兜 `'N'`（root cause，不在前端打补丁）；前端补「允许迟交」开关并显式提交；新增防回归用例 | curl 缺 `allowLate` 发布 → **201**；`TeacherCourseworkServiceTest` 7 例绿 |
| P0 | 教师 token 可查全库审计（`AUDIT_READ` 曾授 TEACHER 且端点无课程范围过滤） | 双管：`SecurityConfig` 对 `/api/v1/ops/**` 加 `hasRole("OPS")`；`04_seed.sql` 与实库收回 TEACHER 的 `AUDIT_READ`。需求本意「教师看所授课程日志」列为待确认 **B1** | curl 教师 → **403/40301**；运维 → 200 正常返回 |
| P1 | 审计时间与学生提交时间显示早 8 小时（`TO_CHAR` 丢偏移 + 前端裸 slice） | SQL 改 `SYS_EXTRACT_UTC(...) \|\| 'Z'`；前端 `dt()` 改本地化解析 | 实库返回 `2026-11-10T11:38:33Z`；`tsc`+页面测试 6 例绿 |
| P1 | 同课程重名发布 → `TooManyResultsException` 500 + 孤儿行 | 主键改应用侧 `Ids.hex16()` 随 INSERT 落库，删掉「按 course+title 回读」 | curl 连发两次同标题 → **201 / 201**（不再 500），垃圾数据已按主键清库 |
| P1 | 三处 UI 文案反向承诺后端没有的行为（阻断提交 / 执行校验 / 发送邀请） | 文案改为「提交后异步回填，暂不阻断」「邀请投递随消息切片」；`requireFormatCheck` 默认不勾 | 见 `web/src/pages/teacher/Courses.tsx`、`student/Courses.tsx` |
| P1 | 审计列表缺 `actorRole`（REQ-09 明确要角色） | view + SQL 补 `actor_role` 并贯通前端 | curl 首行 `actorRole="TEACHER"` |
| P1 | 需求 5.6.2 三项（批量下载提交文档、导出统计表格、作业关联模板）在计划里就漏了，且未披露 | **承认是计划漏项**，补记为待确认 **B4** | — |
| P2 | 审计 `source_ip` / `user_agent` 自 01 切片起从未写入（761 行全空），登录记录与请求上下文因此恒空 | 属既有链路的 Must 级字段缺失，动它要改已过审的认证链路 → 记 **B3** 请你定夺，我没擅自动 | 实库 `COUNT(source_ip)=0` |
| P2 | `GET /teacher/assignments/{id}/unsubmitted` 带审计写副作用 | 改为纯读（提醒投递本就随切片 2），去掉 record 调用 | 回归 6 例绿 |
| P2 | 死代码 `archiveCourse` / `updateCourseStatus` | 三处删净（归档属运维屏 06） | 编译零引用 |
| P2 | 文档债：CLAUDE.md 仍写六域、契约速查无新端点、详细设计表 3-9 路径分叉 | 三份都补：CLAUDE.md 改七域并加两条分层红线；契约速查新增 §6/§7/§8；分叉记在 §三 | — |

**我此前两处夸大，据此更正**（原话保留以便追溯）：

1. 「端到端：发布→提交→批改全链路真返回」——只对 **curl** 成立（示例请求带了 `allowLate`）；UI 发布当时必 400，所以「闭环可演示」这条退出判据在前端**不成立**，现已按上表修复并复验。
2. 「唯一剩余欠账是 02_数据库设计说明书.docx」——不准确：CLAUDE.md、接口契约速查、详细设计表 3-9 同样欠着，均已在本轮补齐；`mvn test` 的「436 例」里还包含 3 个真库集成测试的 6 个方法（本机 Oracle 不可达时它们不执行，实测 `tests_total=430`），两个数字都对但要说清口径。

正面确认（审查独立核过，供放行参考）：批改总分确为服务端算（乱序/缺 `maxScore`/缺项/超分/多项全拒，有测试）；提交五道门禁齐（未选修、非本人文档、DRAFT、CLOSED、超期禁迟交，均带 `never()` 断言不落库）；`teacherCourseScope` 使越权查询自然得空集；LIKE 已转义、成功码双口径兼容、`collabActive` 恒 0 带 Tooltip——**未发现在披露上撒谎的情况**，问题集中在「UI 声称的比后端做的多」。

- 后端：`rm -rf target/classes target/test-classes && mvn -q test` → **436 例，0 失败 0 错误**（新增 coursework 17 例 + 审计 7 例）。注：`mvn clean` 不可用——8080 实例锁着 `target/template-sample.docx`（04 期 E2E 产物）。
- 契约：`npx vitest run src/api/coursework.test.ts` → 3 例通过（前端 URL/参数与后端 Controller 逐条对齐）。
- 端到端（8080 新代码）：登录三账号 → 发布 → 提交 → 批改 → 审计检索全链路真返回；关键行已在台账表「证据」列留痕。
- 数据现状：`edudoc_assignment=2`（1 PUBLISHED / 1 DRAFT）、`edudoc_submission=1`（PUBLISHED 作业的 SUBMITTED 提交）、`group_name` 两课各 1 组。误产生的草稿提交行已按主键删除。

## 五、遗留债（有意不做，按演示优先级排序）

1. 新课必须把创建者写成 `course_member`，否则 `courseScope` 里连自己都看不见——这是课程可见性的唯一口径，`course.teacher_id` 与成员表存在双源风险，切片 4 用户目录收口时一并处理。
2. `TeacherCourseworkServiceImpl` 内自建 ObjectMapper 与 `CourseworkJson` 重复（当时该文件冻结），收尾合并。
3. `require_format_check='Y'` 不做提交前同步阻断（`format_check_id` 恒 null）：校验结果异步落库，同步阻断要等结果回调，随消息切片一起做。接入点已注释标出。
4. 提醒未提交学生只出名单 + 写审计，无投递；消息生产者是切片 2。
5. `collabActive` 恒 0（协作统计随切片 3）；教师工作台「最近动态」区随消息切片恢复。
6. 学生作业列表、教师统计的分页/过滤在内存做（单用户几十条前提，`ponytail:` 注释已给下沉 SQL 的升级路径）。
7. 成员导入同步解析、无批次表；下载中心复用 `/conversions`、`conversion_task` 无 `expires_at`（有效期文案已按 OSS 保留口径改写）。
8. 业务级幂等重放：全局 filter 保证同 key 重放返回 40902，但不回放首次结果；真「重放即原结果」需要 `edudoc_ai_task.idempotency_key` 那种表内唯一键，作业/提交暂无。
9. **审计页两数据源分叉**：操作日志 Tab 走真接口，「冻结账号」等动作仍往 mock 数组插行，这些行不会出现在真实操作日志里。随切片 4 统一。
10. 演示数据规模偏薄：课程 `students` 实测为 1（真实选课人数，不再是从前 mock 的 32/35/42），作业统计要多学生才有观感——需要补学生账号与选课种子（`SeedDataRunner` 加 8-10 个 `I` 状态学生即可，属演示打磨项）。
11. 契约字段名曾写错并已修正：`TeacherCourseView.template` → `templateName`（与 Java record 逐字对齐），页面里那条 `template ?? templateName` 双兜底随之删除；`/teacher/assignments`、`/teacher/submissions` 的分页参数补上（后端缺省每页 10 条，不传会静默截断）。这两处由接线 agent 自报，属我给的契约文件自己的错。

## 六、下一步（切片 2 入口）

前端接线四页落地 → 门面页去掉 `mock/domain/course.ts` → 补 `docs/test/CourseTest.md` 手工步骤 → 切片 1 设计文档同步（`.docx` + docs 复刻）→ 切片 2 消息与个人中心（`edudoc_notification`/`notification_preference` DDL 先行，四类生产者：提交、批改、格式异常、导出完成）。
