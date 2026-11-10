# 后端与 ai-service 跟进实施计划（06-backend-followup）

> **For agentic workers：** 实施本计划必须使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans，逐任务执行。步骤用 `- [ ]` 勾选跟踪。

**Goal：** 把 05 期用 mock 呈现的三端门面逐块换成真实后端能力，第一块是「课程与作业」，之后依次是消息与个人中心、协作与版本、运维端治理。

**Architecture：** 沿用模块化单体（Controller → ApplicationService → Domain/Repository），重活走 Outbox + Redis Stream；新增 `coursework` 业务域承载作业/提交/批改，不把它们塞进 `export`（导出的职责是产物与统计，业务事务落 coursework 才能让「版本不可变 + 提交快照」有一条清晰的写路径）。

**Tech Stack：** Java 21 / Spring Boot 3 / MyBatis-Plus / Oracle / Redis Stream / React 18 + TS + antd 5（web 接线）；ai-service 本批基本不动。

**Spec（计划书论证依据，实施时两份都要读）：**
- `docs/requirements/需求规格说明书.md`（REQ-03/06/08/09/14、NFR-02/05/06 优先级与验收口径）
- `docs/design/概要设计说明书.md`、`docs/design/详细设计说明书.md`（§3.6 导出与统计仅职责级——见「风险」）、`docs/design/数据库设计说明书.md`
- `docs/tasks/05-web-full-presentation.md`（门面屏号落位与 §五 跟进清单）
- `docs/design/接口契约速查.md`（envelope、错误码、分页、幂等口径）

**事实来源：** 2026-11-09 四份只读盘点——后端 55 个已实现端点与两域空壳、38 张表与 14 项缺口、门面 mock 反推净新增约 55–58 端点、REQ 优先级 × 设计成熟度。

## Global Constraints

1. 调用顺序红线：Controller 只做校验与封装；Mapper、OSS 客户端、HTTP 客户端只出现在 `infrastructure`；Service 先接口后 `*Impl`；构造器注入（`@RequiredArgsConstructor`）。
2. Java 业务代码不写 SQL，SQL 一律落 `backend/src/main/resources/mapper/*.xml`。
3. 主键 `id RAW(16)`；写入前 `Ids.isHex16()` 校验，XML 里 `HEXTORAW(#{id})`；每表三公共列（`id` / `create_time` / `update_time`）且顺序在前；新表必须补 `trg_<表>_bu` 触发器，否则 DBD 附录 A「触发器数=表数」核对失败。
4. 表名 `edudoc_` 前缀；约束名 `pk_/uk_…/fk_…/ck_…`；索引 `ix_`；布尔 `CHAR(1) CHECK (… IN ('Y','N'))`；状态短码 + CHECK 枚举全值；删除一律软删（照 `document` 的 `status+deleted_at+purge_at`）。
5. 防重复靠复合唯一约束，不靠应用先查（先例 `uk_submission_1(assignment_id,student_id,attempt_no)`；带时区列做唯一要用 `SYS_EXTRACT_UTC()` 函数索引，Oracle 不能直接 UNIQUE）。
6. **`IdempotencyFilter` 对 `/api/v1/auth/` 以外的所有写操作强制要求 `Idempotency-Key`**（缺→40001，重放→40902）。速查表 §3「只有 5 类要」是过期口径，接口示例必须带 key。
7. 新异步任务三处齐动：`edudoc_outbox_event` 事件类型 + `application.yml` `edudoc.outbox.stream-keys` 登记（漏登记事件直接 FAILED）+ 新 Worker（沿用 `DocumentParseWorker` 骨架：MKSTREAM 幂等建组、`pid` consumer 名、XACK 兜底）。
8. 版本不可变：提交/编辑/修复/恢复都生成新版本，历史只读。提交必须冻结 `document_version_id` 快照。
9. 鉴权顺序：认证→账号状态→角色→课程范围→文档 ACL→动作约束。第 1–4 步靠 `@RequirePermission` 自动得到；**第 5–6 步（`courseScope`、owner/ACL）没有公共组件，必须在应用服务里手写**，且当前 owner 不符返回 40401，不是 40302（40302/40303/42202/42901 有码无抛出方，属"定义未接线"）。
10. 设计文档改动三步走且不许跳步：改 `05_系统设计/*.docx` 源（`TempStore/docx-*.py` 工具链）→ 同步 `EduDocAssistant/docs/design/*.md` 复刻 → 才动 `05_系统设计/数据库脚本/01→02→03→04`。
11. path 归属红线，五个同名不同物不得混用：`/courses`（精简下拉，已存在）· `/teacher/courses` · `/ops/courses` · `/documents/{id}/versions` · `/templates/{id}/versions`。
12. 切片顺序已拍板：课程与作业 → 消息与个人中心 → 协作与版本（REST，无网关）→ 运维端；`gateway/`（Yjs）本期不建。
13. 交付物、注释、提交信息简体中文；`mvn test` 必须在无 Oracle 时全绿（409 个 @Test，0 个 `@SpringBootTest`，3 个真库集成测试靠 `EDUDOC_DB_USERNAME/PASSWORD` + `assumeTrue` 跳过）。
14. 每个切片收尾必须做一次「页面字段 ↔ 真接口响应」符合性对照并重刷 `TempStore/facade-shots/` 截图——05 门面至今没有独立审查报告，字段漂移只能靠这道门拦。

---

## 一、切片总览

| 切片 | 覆盖 REQ / 屏 | 要接线的表 | 需新 DDL（★） | 净新增端点 | 外部依赖 | 退出判据 |
| --- | --- | --- | --- | --- | --- | --- |
| **1 课程与作业 + 审计查询插队**（本文展开到任务级） | REQ-08（Should）+ REQ-09 查询侧（**Must**）；学生 22/23/24/25、教师 20–25、教师 01、运维 15/16 | `course`/`course_member`（已存在）、`assignment`/`submission`（已建表零代码接线）、`audit_log`（写入已真、查询 API 从无） | ★`assignment_rubric_item`、★`submission_rubric_score`、`assignment` 加 `submission_format`/`max_size_mb`/`require_format_check` | ≈11 | 复用 `format-check` Stream 与 `/conversions`；无新组件 | 三端作业页与运维「操作日志/事件详情」无「演示数据」标记，提交-批改闭环可端到端演示 |
| 2 消息与个人中心 | 支撑 REQ-08/09；学生 26/27、教师 26/27、运维 25/26/27 | `audit_log`（登录记录复用）、Redis（会话，规格明确不落库） | ★`notification`、★`notification_preference`、★`access_policy`、`user` 加 `mfa_enabled` | ≈10 | ★新事件类型 `NOTIFICATION_REQUESTED` + stream-keys 登记；I06 邮件网关未定 → 本期只做站内 | 消息由「提交/批改/格式异常/任务完成」四类生产者真实产生，已读与偏好落库 |
| 3 协作与版本（REST） | REQ-03（**Must**）+ 回收 REQ-01/02 编辑态（`NormalizeSection.tsx:124-125` 承诺的粘贴识别与编号重排）；学生 16–21、教师 15–19 | `document_version`（已存在）、`document_share`/`share_grant`/`collab_session`/`collab_member`（已建表零接线） | ★`document_annotation`、★`member_import_batch`（若切片1 走异步则提前） | ≈12 | 无 `gateway`：版本对比现算、恢复走 REST；Yjs 三表继续 dormant | 版本恢复生成新版本、共享链接有有效期与撤销、批注可解决；`VersionDrawer.tsx:56`「随协作切片提供」承诺兑现 |
| 4 运维端治理 | REQ-06 运维侧、REQ-09 详情侧、NFR-01/03/07；运维 01–14、17–24（15/16 已在切片 1 交付） | `audit_log` 检索补量（`user`/`role`/`user_role`/`role_permission`、`oss_object`） | ★`permission_conflict_review`、★`anomaly_review`、★`security_incident`、★`storage_quota`、★`cleanup_job`、★`config_change` | ≈20 | Redis（会话/队列计数）、OSS 生命周期兑现 `TemplateVersionTxWriter.java:40` 孤儿对象清理；`deploy/` 未建 → 服务健康仍 mock | ops 账号每屏真数据；审计检索/详情/导出、角色矩阵写库、冻结走单事务原子写审计 |
| （0）aiDemo 退场 | 学生 06–10、教师 05–08 | 无 | 无 | **0** | 无 | `Smart.tsx` 改接真 `/ai-tasks`·`/qa-sessions`·`/polish`，删 `mock/domain/aiDemo.ts`；顺带把 `length/focus`、拒答口径、`citations` 回填进现有契约 |

**ai-service 面：** 01–04 已功能完备（embed/summarize/extract-keypoints/answer/polish/rerank/convert 九条路由真跑通，`ai-service/app/main.py:160-219`），四个切片都不需要新模型端点。只有两件会牵动它，且都不在本批：REQ-07 参考文献 DOI→GB/T 7714（Could 级，需求文档已标未实现）、PDF 结构树（04 期 J1 决议本期不做）。

**明确不在本批（不是遗漏，是划出去）：** REQ-05 500 并发上传压测与 NFR-01/03 容量类指标（属部署与压测切片，`deploy/` 目录还没建）；REQ-07 参考文献 DOI→GB/T 7714（Could 级，需求文档已标未实现）；PDF 结构树（04 期 J1 决议）；实时协同网关（本批拍板不做）。这几项在门面里都没有被承诺成真数据，所以不算欠账。

**依赖链（不能倒着排）：** 作业发布 → 提交 → 批改 →（反馈）消息；成员导入 → 课程已建；版本恢复 → 版本链已入库；审计查询 → 前序写操作先产生事件；清理/回滚 → 先有变更与审批记录。所以 security/审计查询必须晚于业务写路径，切片 4 在最后。

---

## 二、切片 1 任务分解

### 执行口径（2026-11-10 追加，速度优先）

用户定的原则：**实现速度优先、代码量最少、功能闭环即可（不追求精细），尽快出成品。** 下面五处是对本计划已定内容的偏离，都是有意的取舍，不是遗漏；执行时以本节为准：

1. **评分标准不建新表**：`edudoc_assignment_rubric_item` 与 `edudoc_submission_rubric_score` 两张表 → 改为 `edudoc_assignment.rubric_json`（`[{name,maxScore}]`）与 `edudoc_submission.score_json`（`[{name,maxScore,score}]`）两个 CLOB/VARCHAR2 JSON 列。库里已有 `rule_config_json`/`profile_json`/`parsed_json` 同类先例，符合惯例。代价：不能按分项做 SQL 聚合，统计只用总分——演示够用。总分仍由服务端按 `score_json` 现算，不信任前端。
2. **不加 `max_size_mb`**：发布表单里这个字段后端不校验（上传大小限制在 OSS 直传策略层），纯摆设 → 只做 `submission_format` 与 `require_format_check` 两列。
3. **设计文档同步挪到切片末**：`.docx` 源 + `docs/design/*.md` 复刻的更新不在 Task 1-1 阻塞代码，改为切片 1 收尾一次批量补（`docs: 06 切片1 设计文档同步`）。这是对全局约束第 10 条「三步走顺序」的一次性破例，代价是这一段时间库里结构与 DBD 不一致，切片 1 的审查必须核这条。
4. **测试只留关键分支**：每任务 1 个 service 测试，覆盖「主路径 + 一条越权/权限 + 一条业务约束」，不再逐层铺 Repository/Mapper 打桩测试；集成验收靠 `node TempStore/smoke-facade.mjs` 真机逐页重跑（一次抓 24 页，比补一堆单测更快发现字段漂移）。时区边界、重放幂等等 Review Focus 条目仍测，但合进同一条 service 测试里断言。
5. **成员导入不做 CSV 解析**：前端把行数据（学号/姓名/课程组）直接 POST，后端逐行落 `course_member` 并回传 `created/activated/conflicts/failures`。CSV 与教务接口（I04）都是下游真事，本期不做。

另外为使演示**不依赖真实上传**：种子作业 `require_format_check='N'`，种子文档/版本行直接入库（提交链路只读库，不碰 OSS）。要演示格式预检时再按 `docs/test/DocumentTest.md` 真传一份。

**新增权限点：** `SUBMISSION_SELF_READ`（学生查看本人作业与提交）。05 门面当时把学生「课程与作业」入口的 `SUBMISSION_READ` 收权解除是权宜，Task 1-8 要按新点重新收口——`SUBMISSION_READ` 语义是查看提交统计，属教师/运维。

### Task 1-1: DDL 与设计文档同步 + 演示种子

**Files:**
- Modify: `05_系统设计/02_数据库设计说明书.docx`（源，走 `TempStore/docx-update.py`）→ 同步 `EduDocAssistant/docs/design/数据库设计说明书.md`
- Modify: `05_系统设计/数据库脚本/01_schema.sql`（`edudoc_assignment` 加列、两张新表）、`02_indexes.sql`、`03_triggers.sql`、`04_seed.sql`
- Modify: `EduDocAssistant/backend/src/main/resources/db_seed/seed_accounts.csv`、`backend/src/main/java/com/cidas/edudoc/access/infrastructure/SeedDataRunner.java`
- Modify: `EduDocAssistant/CLAUDE.md`（六域 → 七域，补 `coursework` 一行）
- Test: `backend/src/test/java/com/cidas/edudoc/coursework/infrastructure/AssignmentSchemaIntegrationTest.java`

**Interfaces:**
- Produces: 列 `edudoc_assignment.submission_format VARCHAR2(10) CHECK (… IN ('DOCX','PDF','BOTH'))`、`max_size_mb NUMBER(4)`、`require_format_check CHAR(1)`；表 `edudoc_assignment_rubric_item(id, assignment_id FK, item_name, max_score, seq_no, uk_rubric_item_1(assignment_id,seq_no))`、`edudoc_submission_rubric_score(id, submission_id FK, rubric_item_id FK, earned_score, uk_score_1(submission_id,rubric_item_id))`。
- Produces: `coursework` 包骨架（api/application/domain/infrastructure 四层，先只有 `package-info.java` 与空目录，禁止留"声称有四层"的假注释——collaboration 域踩过这个坑）。

- [ ] **Step 1：写失败的真库契约测试**——断言 `edudoc_assignment` 含 `submission_format`、两张新表存在且各有 `trg_*_bu` 触发器，无凭据时 `Assumptions.assumeTrue` 跳过（照 `TemplateFormatSqlIntegrationTest` 写法）。
- [ ] **Step 2：跑它确认失败**：`mvn -f backend/pom.xml test -Dtest=AssignmentSchemaIntegrationTest` → 期望 FAIL（列/表不存在）。
- [ ] **Step 3：按 DBD → 脚本顺序落 DDL**：先改设计说明书（新增两表数据字典 + 附录 A 表数/触发器数），再改 `01/02/03`；`02_indexes.sql` 加 `ix_assignment_course_due(course_id,due_time)`；`03_triggers.sql` 补两个 BEFORE UPDATE 触发器。
- [ ] **Step 4：补演示种子**：`SeedDataRunner` 加 `seedCoursework()`——2 门课按原型世界观改名（`CS101`→`Web开发技术`、`MATH201`→`数据库原理`，`displayName` 学生测试→张同学 / 教师测试→李老师；改名幂等：存在则 `UPDATE`），并 seed 1 份 `PUBLISHED` 作业（含 5 个 rubric 项）+ 1 份 `DRAFT` 作业。**注意：提交链路要求文档真实解析过，seed 造不出可校验的文档**，演示前按 `docs/test/DocumentTest.md` 真传 1–2 份，本任务只在 `docs/test/CourseTest.md` 写清这一步。
- [ ] **Step 5：跑测试确认通过 + 提交**：`mvn -f backend/pom.xml test`（无库时新集成测试应 SKIP、其余全绿）。

```
feat(backend): 作业提交评分表结构与演示种子

- edudoc_assignment 加 submission_format/max_size_mb/require_format_check
- 新表 edudoc_assignment_rubric_item / edudoc_submission_rubric_score 与触发器、索引
- 数据库设计说明书、01/02/03/04 脚本、SeedDataRunner 演示世界观对齐原型
```

### Task 1-2: coursework 持久层

**Files:**
- Create: `coursework/infrastructure/{Assignment,RubricItem,Submission,SubmissionScore}.java`（MyBatis-Plus 实体）、`{…}Mapper.java`、`{…}Repository.java`
- Create: `backend/src/main/resources/mapper/{AssignmentMapper,RubricItemMapper,SubmissionMapper,SubmissionScoreMapper}.xml`
- Test: `coursework/infrastructure/AssignmentRepositoryTest.java`、`SubmissionRepositoryTest.java`（Mockito 打桩 Mapper，断言 `HEXTORAW` 入参前已 `Ids.isHex16`）

**Interfaces:**
- Consumes: Task 1-1 的表与列。
- Produces: `AssignmentRepository#save/findByCourse(courseId, status)/findPublishedById(id)`；`SubmissionRepository#insertIfAbsent(submission)`（撞 `uk_submission_1` 抛 `DuplicateSubmissionException`）、`nextAttemptNo(assignmentId, studentId)`、`findPage(assignmentId, status, page, pageSize)`、`updateGrade(id, score, feedback, status)`；行视图 `SubmissionRow`。

- [ ] **Step 1：写失败测试**——`AssignmentRepositoryTest`：`HEXTORAW` 前非 32 位大写十六进制 ID 必须先被拒（断言抛 `CidasBusinessException(40001)`，不落库）。
- [ ] **Step 2：跑测试确认失败**：`mvn -f backend/pom.xml test -Dtest=AssignmentRepositoryTest`。
- [ ] **Step 3：写四层最小实现**——实体 + Mapper 接口 + XML（分页用 `PageResult` 口径、`create_time` 不手写交给触发器/默认值）+ Repository 包装（`Ids.isHex16` 校验在 Repository 入口）。
- [ ] **Step 4：`SubmissionRepositoryTest` 补并发重复提交用例**——`insertIfAbsent` 捕获唯一约束冲突 → `DuplicateSubmissionException`（供 Task 1-5 映射 40902，不许冒 500）。
- [ ] **Step 5：跑测试全绿 + 提交** `feat(backend): coursework 作业与提交持久层`。

### Task 1-3: 教师作业发布与统计

**Files:**
- Create: `coursework/api/AssignmentController.java`、`coursework/application/{AssignmentService,AssignmentServiceImpl,AssignmentPublishTxWriter}.java`、`coursework/domain/{AssignmentDraft,AssignmentStatus,AssignmentStats}.java`
- Modify: `common/security/PermissionCode.java`（加 `SUBMISSION_SELF_READ`）、`04_seed.sql` 角色映射、`docs/design/接口契约速查.md`
- Test: `coursework/application/AssignmentServiceTest.java`

**Interfaces:**
- Produces:
  - `POST /api/v1/teacher/assignments`（`ASSIGNMENT_MANAGE`，带 `Idempotency-Key`）req `{courseId,title,requirement,dueTime,submissionFormat,maxSizeMb,requireFormatCheck,rubric:[{name,maxScore}]}` → 201 `{id,status}`
  - `GET /api/v1/teacher/assignments?courseId&status` → `PageResult<AssignmentStatsView{id,course,title,deadline,remainText,expected,submitted,overdue,formatIssues,graded,unsubmitted:[{account,name}]}>`
- Consumes: Task 1-2 Repository；`TemplateAccess` 式课程范围校验（`principal.courseScope()` 含 courseId，否则 40301）。

- [ ] **Step 1：写失败测试**——`AssignmentServiceTest#publishRejectsCourseOutOfScope`：非本课程教师发布 → 40301 且不落库；`publishRejectsEmptyRubric`：rubric 空 → 40001。
- [ ] **Step 2：跑测试确认失败**：`mvn -f backend/pom.xml test -Dtest=AssignmentServiceTest`。
- [ ] **Step 3：实现 `AssignmentServiceImpl#publish`**——校验 → `AssignmentDraft` → `AssignmentPublishTxWriter` 同事务写 assignment + rubric 项 + `OutboxEventRepository.publish`（本任务不发事件，仅预留聚合号）→ 审计一行。
- [ ] **Step 4：实现统计查询 `stats`**——SQL 全落 `AssignmentMapper.xml`：应交=课程成员中学生数、已交/逾期按 `submission.status` 与 `due_time` 现算、未提交名单走 `NOT EXISTS`；`remainText` 由服务端算（不要前端拿 `Date.now()` 猜）。
- [ ] **Step 5：跑测试全绿 + 起服务 curl 两笔（`docs/test/CourseTest.md` 记录命令与返回）+ 提交** `feat(backend): 作业发布与提交统计`。

### Task 1-4: 学生作业列表（派生状态）

**Files:**
- Create: `coursework/api/StudentAssignmentController.java`、`coursework/application/{StudentAssignmentQueryService,StudentAssignmentQueryServiceImpl}.java`、`coursework/domain/StudentAssignmentStatus.java`
- Test: `coursework/application/StudentAssignmentQueryServiceTest.java`

**Interfaces:**
- Produces: `GET /api/v1/student/assignments?status&courseId&page&pageSize`（`SUBMISSION_SELF_READ`）→ `PageResult<StudentAssignmentView{id,course,title,requirement,deadline,remainText,status:'PENDING'|'SUBMITTED'|'GRADED'|'OVERDUE',format,formatTone,score?,feedback?,submittedDoc?,submitTime?,latestDocumentVersionId?}>`
- Produces: `StudentAssignmentStatus#derive(assignment, submission, now)` → 优先级 `GRADED > SUBMITTED > OVERDUE > PENDING`（仅 `PUBLISHED` 作业可见，`DRAFT`/`CLOSED` 不出现）。

- [ ] **Step 1：写失败测试**——四条状态派生用例 + 一条「未发布作业不得出现在列表」+ 一条「空结果返回 `items:[], total:0`，不得是 null」。
- [ ] **Step 2：跑测试确认失败**。
- [ ] **Step 3：实现查询**——一次 join（本人可见课程 + 本人提交 + 文档版本 + 最近一次 `format_check` 结论）落 XML，状态派生放 `StudentAssignmentStatus`（纯函数，便于 Review Focus 第 1 条的时区测试）。
- [ ] **Step 4：跑测试全绿 + 提交** `feat(backend): 学生作业列表与派生状态`。

### Task 1-5: 提交（快照 + 迟交 + 格式阻断）

**Files:**
- Create: `coursework/api/SubmissionController.java`、`coursework/application/{SubmissionService,SubmissionServiceImpl,SubmissionTxWriter}.java`、`coursework/domain/{SubmissionDraft,CutOffPolicy}.java`
- Test: `coursework/application/SubmissionServiceTest.java`

**Interfaces:**
- Consumes: `AssignmentRepository#findPublishedById`（Task 1-2，非 `PUBLISHED` 一律 40901）、`SubmissionRepository#insertIfAbsent` 与 `DuplicateSubmissionException`（Task 1-2）、`document` 域读文档与当前版本（owner + `READY`）、`FormatCheckService#create`（已有 `/format-checks`）。`DuplicateSubmissionException` 在 Controller 层映射 40902，不得冒 50001。
- Produces: `POST /api/v1/assignments/{id}/submissions`（`DOCUMENT_CREATE`，带 `Idempotency-Key`）req `{documentId}` → 201 `{submissionId,attemptNo,status:'SUBMITTED'|'LATE',formatCheckId,blocked:boolean}`；`blocked=true` 时响应携带 `42202 FORMAT_BLOCKER` 语义且不落提交。
- Produces: `CutOffPolicy#status(dueTime, allowLate, now)` → `SUBMITTED` / `LATE` / 抛 40901（超期且不允许迟交）。

- [ ] **Step 1：写失败测试**——五例：非本人文档→40401；文档非 `READY`→40901；`allowLate='N'` 超期→40901；重复提交 `attempt_no` 递增且第二次成功（原型允许重交）；唯一约束冲突→40902 不是 500。
- [ ] **Step 2：跑测试确认失败**。
- [ ] **Step 3：实现 `SubmissionServiceImpl#submit`**——取文档当前版本冻结为 `document_version_id` → 触发一次 `format-check`（复用现有 Stream，不新造事件类型）→ 有 BLOCKER 且 `require_format_check='Y'` 则 42202 阻断（**同步返回**，长文校验留给 `/format-checks` 轮询）→ `SubmissionTxWriter` 单事务落 submission + 审计。
- [ ] **Step 4：时区边界测试**——`CutOffPolicyTest`：`due_time` 存 `TIMESTAMP(6) WITH TIME ZONE`，断言 23:59:59+08:00 与次日 00:00+08:00 判定差一天，服务端统一用 `OffsetDateTime` 不用本地 `LocalDateTime.now()`。
- [ ] **Step 5：跑测试全绿 + 提交** `feat(backend): 作业提交与迟交判定`。

### Task 1-6: 批改与反馈

**Files:**
- Create: `coursework/api/SubmissionGradeController.java`、`coursework/application/{GradeService,GradeServiceImpl}.java`、`coursework/domain/RubricTotal.java`
- Test: `coursework/application/GradeServiceTest.java`

**Interfaces:**
- Produces: `GET /api/v1/teacher/submissions?assignmentId&status`（`SUBMISSION_READ`）→ `PageResult<SubmissionGradeView{id,assignment,student,sid,doc,formatOk,status,score,items:[{rubricItemId,name,maxScore,earnedScore?}],feedback}>`；`PUT /api/v1/submissions/{id}/grade`（带 `Idempotency-Key`）req `{items:[{rubricItemId,score}],feedback}` → 200 `{score,status:'GRADED'}`。
- Produces: `RubricTotal#sum(items)` 校验每项不超 `max_score`、总分=分项和（不信任前端总分）。

- [ ] **Step 1：写失败测试**——四项：分项超满分→40001；`CLOSED` 作业仍可批改（原型口径）；重复批改以最新为准且分项同步覆盖；非本课程教师→40301。
- [ ] **Step 2：跑测试确认失败**。
- [ ] **Step 3：实现 `GradeServiceImpl#grade`**——按 rubric 现算总分 → 写 `submission_rubric_score` + `submission.score/feedback/status='GRADED'` → 审计。**本任务不发反馈消息**（切片 2 接生产者）。
- [ ] **Step 4：跑测试全绿 + 提交** `feat(backend): 分项评分与批改反馈`。

### Task 1-7: 课程管理与教师工作台

**Files:**
- Create: `coursework/api/{TeacherCourseController,TeacherWorkbenchController}.java`、`coursework/application/{TeacherCourseService,TeacherCourseServiceImpl,WorkbenchSummaryService,WorkbenchSummaryServiceImpl}.java`
- Test: `coursework/application/TeacherCourseServiceTest.java`、`WorkbenchSummaryServiceTest.java`

**Interfaces:**
- Consumes: `access` 域 `Course`/`CourseMember` 现有实体（`/courses` 精简下拉端点保持不动）。
- Produces: `GET /api/v1/teacher/courses?term&status&keyword`（`ASSIGNMENT_MANAGE`）→ `PageResult<TeacherCourseView>`；`POST /api/v1/teacher/courses`（`Idempotency-Key`）→ 201；`POST /api/v1/teacher/courses/{id}/member-imports` req `{sourceType:'CSV'|'ACADEMIC',rows:[{studentId,name,group}]}` → 200 `{total,created,activated,conflicts,failures:[{row,reason}]}`（**同步实现**，见「待拍板 3」）；`GET /api/v1/teacher/workbench-summary` → `{dueThisWeek,pendingGrade,formatIssues,collabActive,priority:[…]}`。
- Produces: `collabActive` 在切片 3 前恒为 0，并在响应注释与 `CourseTest.md` 写明原因，不做假数。

- [ ] **Step 1：写失败测试**——`member-imports`：学号已在本课→`conflicts` 计数不新增；空文件→40001；导入后学生数增加且 `course_member.status='A'`。工作台：`priority` 按 `due_time` 升序、逾期项排最前。
- [ ] **Step 2：跑测试确认失败**。
- [ ] **Step 3：实现课程列表与新建**（列表聚合走 XML：成员数、关联模板、本周截止数一次查完，避免 N+1）。
- [ ] **Step 4：实现成员导入（同步、单事务、逐行结果回传 + 审计）**与工作台聚合。
- [ ] **Step 5：跑测试全绿 + 提交** `feat(backend): 教师课程管理与工作台聚合`。

### Task 1-8: 前端接线换真 + mock 退场

**Files:**
- Create: `web/src/api/coursework.ts`（+ `coursework.test.ts`）
- Modify: `web/src/pages/{student/Courses.tsx,teacher/Courses.tsx,teacher/Workbench.tsx}` 及三者测试
- Modify: `web/src/pages/DemoPage.tsx`（加 `live?: boolean`，去掉「演示数据 · 后端未接入」标记但保留屏号说明）
- Modify: `web/src/routes.ts` + `web/src/pages/student/Workbench.tsx`（按 `SUBMISSION_SELF_READ` 重新收口）
- Delete: `web/src/mock/domain/course.ts`
- Create: `EduDocAssistant/docs/test/CourseTest.md`（手工步骤：登录→发布→上传→提交→批改，含 curl 与 `Idempotency-Key` 示例）

**Interfaces:**
- Consumes: Task 1-3…1-7 的全部端点；`web/src/api/http.ts` 已自动带 `Idempotency-Key`（`resolveIdempotencyKey`）。
- Produces: `coursework.ts` 导出 `listTeacherCourses/createTeacherCourse/importCourseMembers/listTeacherAssignments/publishAssignment/listStudentAssignments/submitAssignment/listSubmissions/gradeSubmission/teacherWorkbenchSummary`，类型与后端视图逐字段对齐。

- [ ] **Step 1：写失败测试**——`coursework.test.ts` 断言 URL/method/参数；页面测试断言「学生作业页不再出现 `演示数据` 文案且渲染真接口返回的行」（vi.mock `api/coursework`）。
- [ ] **Step 2：跑测试确认失败**。
- [ ] **Step 3：三个页面换真接口**——删 mock import，`useEffect` 走 service；空态/错误态用 `getErrorMessage`；格式阻断走 42202 分支提示「先去格式与模板修复」。
- [ ] **Step 4：删 `mock/domain/course.ts` 并跑 `npx tsc --noEmit && npx vitest run`**——期望全绿且 `grep -r "mock/domain/course" web/src` 零命中。
- [ ] **Step 5：跑 `node TempStore/smoke-facade.mjs` 重刷截图 + 门面符合性对照（逐字段核对响应 vs 页面）+ 提交** `feat(web): 课程与作业页面接真实接口`。

### Task 1-9: 审计查询端点（REQ-09 Must，只读插队项）

**Files:**
- Create: `access/api/AuditQueryController.java`、`access/application/{AuditQueryService,AuditQueryServiceImpl}.java`、`access/domain/AuditTimeRange.java`
- Create: `backend/src/main/resources/mapper/AuditLogMapper.xml` 增检索语句（已有文件，只加 select）
- Modify: `web/src/api/audit.ts`（新建）、`web/src/pages/operations/Audit.tsx`（仅「操作日志」Tab 换真）、`Audit.test.tsx`、`DemoPage.tsx` 的 `live` 用法
- Test: `access/application/AuditQueryServiceTest.java`

**Interfaces:**
- Consumes: `AuditService`（02/03/04 已在写 `edudoc_audit_log`）、`AuditLogMapper` 现有三索引、`PermissionCode.AUDIT_READ`。
- Produces: `GET /api/v1/ops/audit-events?range=24h|7d|30d&resourceType&actor&keyword&page&pageSize`（`AUDIT_READ`）→ `PageResult<AuditEventView{id,occurredAt,actor,actorRole,actionType,resourceType,resourceRef,result,resultCode,traceId,sourceIp,userAgent,riskLevel,summary}>`；`GET /api/v1/ops/audit-events/{id}` → `AuditEventDetailView{…,chain:[{step,at,detail}],impacts:[{type,name,detail,mark}]}`。
- Produces: `AuditTimeRange#from(rangeCode, now)` 纯函数（跨时区口径与 Task 1-5 Step 4 同源，避免两套时间处理）。
- 事件详情 `chain`/`impacts` 由同 `trace_id` 的审计行聚合而来，**不新建表**；`riskLevel` 现算（同 actor 15 分钟内多次 FAIL → HIGH），阈值进 `application.yml` 的 `edudoc.audit.risk-threshold`，演示期可调。

- [ ] **Step 1：写失败测试**——三例：无 `AUDIT_READ` → 40301；`range=24h` 边界不吞掉 24h 整点那条（时区）；同 traceId 的多行必须聚合成一条详情的 `chain` 且顺序稳定（按 `occurred_at` 升序，不能靠 DB 返回顺序）。
- [ ] **Step 2：跑测试确认失败**：`mvn -f backend/pom.xml test -Dtest=AuditQueryServiceTest`。
- [ ] **Step 3：实现检索与详情聚合**——检索 SQL 落 `AuditLogMapper.xml`（分页 + 走 `ix_audit_*`，禁止全表 `LIKE` 无索引扫；keyword 命中动作/对象/traceId 三列，用现有索引列优先）。
- [ ] **Step 4：前端「操作日志」Tab 换真**——`operations/Audit.tsx` 其余三个 Tab（安全事件/异常与冲突/访问策略）仍走 mock 并保留标记；同页混排时把 `live` 从页面级降到 Tab 级（`DemoPage` 的标记文案改为「部分演示数据：安全事件/异常复核/访问策略」），别整页假绿也别整页假灰。
- [ ] **Step 5：跑测试全绿 + `docs/test/AuditTest.md` 新增手工步骤 + 提交** `feat(backend): 审计事件检索与详情聚合`。

---

## Review Focus（规格隐含、但默认没有任务测试会覆盖的五类，逐条已在上面挂好测试）

1. **截止时刻的时区边界**：`due_time` 是 `TIMESTAMP(6) WITH TIME ZONE`，迟交判定跨 +08:00 会差一天 → Task 1-5 Step 4。
2. **并发/重复提交的唯一约束冲突**：必须映射 40902（重放）或业务 40901，绝不允许冒 50001 → Task 1-2 Step 4 + Task 1-5 Step 1。
3. **越权读写**：非本课程教师发布/查看、学生提交他人或未解析文档、无 `AUDIT_READ` 查审计 → Task 1-3 Step 1、1-5 Step 1、1-6 Step 1、1-9 Step 1（第 5–6 步鉴权没有公共组件，每处手写就是最容易漏的地方）。
4. **`Idempotency-Key` 重放语义**：同一 key 重放不得产生第二条作业/提交/批改 → Task 1-3/1-5/1-6 各留一条重放用例（全局 filter 只保证 key 存在，不保证业务幂等，业务幂等靠唯一约束）。
5. **空态与不可见态**：未发布（`DRAFT`）作业不得出现在学生列表；空结果必须是 `items:[]`；`CLOSED` 后禁提交仍可批改 → Task 1-4 Step 1、Task 1-6 Step 1。

---

## 已拍板（2026-11-09，四问四答，本文按此定稿）

1. **执行方式＝子 agent 逐任务**：一个任务一个新子 agent 实现 + 一个新审查者放行再进下一个；切片 1 收尾做一次整枝复审，产出 `docs/review/` 报告（跟 04 期同样的审查/复审节奏）。
2. **提醒投递＝切片 1 只出名单**：`GET /teacher/assignments` 已带 `unsubmitted[]`，「提醒」按钮本期落审计（记一次动作），消息投递随切片 2 的 `NOTIFICATION_REQUESTED` 一起做。代价：演示时反馈消息不会当场出现在消息中心，页面提示已写明「随消息切片开放」，不假装成功。
3. **下载中心＝复用 `/conversions`**：前端把 `DownloadTask` 视图映射到现有转换任务（状态机/Worker/预览字节流都是真的），不启用 `edudoc_export_task`，避免两套任务模型并存。代价：`conversion_task` 没有 `expires_at`，「有效期倒计时」文案改为「产物按 OSS 保留策略回收」，等真有保留需求再走 DBD 三步走加列。
4. **审计查询＝插进切片 1（Task 1-9）**：`REQ-09` 是 Must 且 `audit_log` 三索引与写入链路都是现成的，约半天点亮运维 15/16 屏；`edudoc_export_task`、成员导入批次表、其余运维写路径仍留切片 4。
5. **顺带定一条实现细节**：课程成员导入走同步解析 + 逐行结果回传（原型只需映射与校验统计，没有批次历史屏），批次表与异步 Worker 归切片 4 与运维成员导入合并做。

## 风险（最可能被低估的三处）

1. **切片 3 的实时协作**：`gateway/` 目录不存在、详细设计 §3.4 只到「时序图 + 接口表」，协作票据/WS 协议/快照与「版本不可变」的交叠无契约；而门面已经对评审演示过在线光标与并发合并。本计划已按「不做网关」压窄范围，仍需在切片 3 前补一份 LDD。
2. **切片 2 的消息模型**：看着「就一个列表」，实际无 REQ 号、无表、详细设计只有 §5.2 I06 一句话，却被提交/批改/共享/任务完成四条流依赖，DDL + 生产者 + 消费者 + 已读 + 偏好是整链成本。
3. **切片 4 的运维写路径**：`权限冲突复核 / 异常复核 / 安全事件 / 配置变更` 四张状态表在 DBD 层完全不存在，属「先补设计再建表」，估工别按门面观感算。
