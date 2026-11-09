# 05 前端全量呈现（web-full-presentation）

## 一、为什么改序

原计划按垂直切片推进（00 foundation → 01 认证 → 02 文档接入 → 03 智能处理 → 04 格式与模板 → …），每片三端齐做。到 04 结束时剩余切片（协作与版本、课程与作业、消息与个人中心、运维端全端）按原节奏做不完，且演示时会出现大片「建设中」空页。

调整为：**先把三端 81 屏门面全量呈现（本切片），后端与 ai-service 随后按页面反推需求补齐**。

- 已联调的 01–04 不动，继续打真实 `/api/v1`。
- 未实现的分区用前端 mock 骨架页：信息架构、字段、状态与文案对齐 `docs/prototype/screens/*`，不追求逐像素还原原型。
- 每张 mock 页顶部带「演示数据 · 后端未接入」标记与覆盖屏号，评审可逐屏对照原型，不假装是真接口。

## 二、页面落位

一级导航仍按详细设计 §4.1（学生 8 / 教师 9 / 运维 7 项），原型的子屏收敛为页内 Tabs/Segmented + 弹窗/抽屉，不新增一级路由。

| 分区（nav key） | 页面 | 覆盖原型屏 | 数据来源 |
| --- | --- | --- | --- |
| workbench | `pages/student/Workbench.tsx` | 学生 01 | 真实 + 消息 mock |
| documents | `pages/Documents.tsx`（+ 上传弹窗、详情抽屉、回收站、转换） | 学生 02/03/04、教师 02/03、学生 22/23 入口 | 真实（02 切片） |
| documents/:id/view | `pages/DocumentView.tsx`（摘要/要点/问答/润色/格式面板） | 学生 05/06/07/08/09/10/13/14、教师 04/05/06/07/08/12/13 | 真实（03/04 切片） |
| ai | `pages/shared/Smart.tsx` | 学生 06–10、教师 05–08（脱离真文档的独立演示位） | mock `domain/aiDemo.ts` |
| format | `pages/FormatSection.tsx`（课程模板 + 规范化） | 学生 11/12/15 | 真实（04 切片） |
| templates | `pages/teacher/Templates.tsx` | 教师 09/10/11 | 真实（04 切片） |
| collab | `pages/shared/Collab.tsx` | 学生 16–21、教师 15–19 | mock `domain/collab.ts` |
| courses | `pages/student/Courses.tsx` | 学生 22/23/24/25 | mock `domain/course.ts` |
| courses | `pages/teacher/Courses.tsx` | 教师 20/21/22/23/24/25 | mock `domain/course.ts` |
| workbench | `pages/teacher/Workbench.tsx` | 教师 01 | mock `domain/course.ts` |
| courses | `pages/operations/Courses.tsx` | 运维 02/03/04/05/06 | mock `domain/governance.ts` |
| users | `pages/operations/Users.tsx` | 运维 07/08/09/10/11/12 | mock `domain/governance.ts` |
| audit | `pages/operations/Audit.tsx` | 运维 13/14/15/16/17/18/19/20 | mock `domain/security.ts` |
| storage | `pages/operations/Storage.tsx` | 运维 21/22/23/24 | mock `domain/resource.ts` |
| messages | `pages/shared/Messages.tsx` | 学生 26、教师 26、运维 25 | mock `domain/messages.ts` |
| profile | `pages/shared/Profile.tsx` | 学生 27、教师 27、运维 26/27 | mock `domain/profile.ts` |
| workbench | `pages/operations/Workbench.tsx` | 运维 01 | mock 聚合各域 |
| 认证 5 屏 | `pages/Login.tsx`、`auth/RoleEntry.tsx`、`AccountRestricted`、`ForgotPassword`、`Forbidden` | 认证 A01–A05 | 真实（01 切片） |

三端视觉区分接的是原型设计口径（`docs/prototype/README.md`）：`theme.ts` 的 `rolePalette` + `roleTheme`，由 `AppShell` 按当前角色套 `ConfigProvider`——学生 `#2563EB`/导航 `#1F3864`、教师 `#0F766E`/`#193C3B`、运维 `#0E7490`/`#18344B`。侧栏品牌位与登录页同一套口径：Logo（`assets/logo-mark.png`，深色导航上垫白底圆角 chip）+ 中文简称「校园智能文档助手」+ 英文昵称 `EduDocAssistant`。

## 三、mock 层约定

- `web/src/mock/store.ts`：`delay`（走 loading 态）、`insert/update/remove`（就地改内存数组，点击有反应）、`paginate`、`nextId`、`ago`。
- `web/src/mock/domain/<域>.ts`：一域一文件，只导出类型 + 种子数组 + 读取函数。真实接口落地后删文件、页面改调 `api/*`。
- 演示世界观统一：课程《Web开发技术》《数据库原理》《操作系统》《编译原理》；李老师/王老师、张同学/陈同学/刘同学、赵工；基准时间 2026-11-09。
- 数据不落 localStorage，刷新回种子态——方便反复讲同一个故事。

## 四、演示口径与已知限制

```powershell
cd EduDocAssistant\web
pnpm dev            # http://localhost:5173  （登录依赖后端：backend mvn spring-boot:run）
```

- 种子账号 `student` / `teacher` / `ops`，口令 `Cidas@123`（见 `docs/test/AuthTest.md`）。
- **登录本身仍走真实后端**（01 切片已联调），演示前需起 backend；`/roles` 角色入口页选端后进对应工作台。
- mock 页的写操作只在内存，刷新即回种子态；导出/下载类按钮是假成功。
- 学生「课程与作业」入口不再按 `SUBMISSION_READ` 收权：该权限点语义是「查看提交统计」，属教师/运维。课程作业切片落地时按新权限点（如 `SUBMISSION_SELF_READ`）重新收口。

## 五、后端与 ai-service 跟进清单（下一批）

门面暴露出的真实缺口，按演示优先级排：

1. **协作与版本**：版本对比与恢复成版（`POST /documents/{id}/versions/{versionId}/restore`）、共享设置与链接有效期、协作者增删、批注 CRUD；实时协同需 `gateway/`（Yjs）。
2. **课程与作业**：课程 CRUD 与成员导入（教务名单/CSV 映射）、作业发布与截止提醒、提交与评分（`edudoc_assignment` / `edudoc_submission`）、导出任务与下载中心。
3. **运维端**：课程治理与归档、用户目录与角色矩阵、权限冲突检查、操作日志与审计事件详情、安全事件与账号冻结、存储配额与回收站清理、服务健康与配置变更记录。
4. **消息与个人中心**：消息生成与已读状态、资料修改、会话下线、登录记录、通知偏好。
5. **权限点补齐**：上述域的 permission_code 进 `04_seed.sql`，前端 `routes.ts` 的 nav `permission` 随之收紧。

## 六、验收口径

- 三端 81 屏逐屏可达（子屏以 Tabs/Segmented + 弹窗/抽屉呈现，屏号写在页头）。
- 每页有真实内容：表格有行、状态用 Tag、筛选可用、写操作有反馈；空态与错误提示齐。
- 每页至少一个 smoke test；已联调的 01–04 页面行为不回退（真实接口不变）。

## 七、本次实测结果（2026-11-09）

- `npx tsc --noEmit`：零错误。`pnpm build`：通过。`npx vitest run`：48 个文件 219 例全绿，其中 `App.pageOf.test.tsx` 新增结构断言——三端 24 个一级导航项全部映射到真实页面，无一落到 `SectionPlaceholder`。
- 真机巡检（`TempStore/smoke-facade.mjs`，Edge + playwright-core，1440×900）：以学生/教师/运维三种登录态逐页访问 24 个一级页面，**0 失败**；mock 页要求「有演示数据标记 + 列表非空」，真实页要求关键元素命中。截图落在 `TempStore/facade-shots/`（24 张，可直接用于汇报素材）。
- 演示前置：登录与 01–04 页面依赖 backend（`localhost:8080`）在跑；本机库若尚未发布课程模板，学生「格式与模板」显示「课程尚未发布模板」空态——这是真实数据状态，不是页面缺陷，演示前先以教师账号发一个模板。
- 三端主题已按原型口径生效（导航底色 + 主操作色），巡检截图可核对。
