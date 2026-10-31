# foundation 契约与基础设施

> 状态取值：`未开始` / `进行中` / `已完成`（本阶段新建，全部 `未开始`）。
> 契约源：《详细设计说明书》§2.3/§5.1/§4.4 与 `04_seed.sql`；本清单任务按"一个会话能完成"拆分。

## F-01 后端 · API 契约与统一响应/异常/分页

- **需求/设计/原型引用**：详细设计 §2.3 统一响应与错误模型、§2.4 分页(page/pageSize/sort)、§5.1 错误码字典、§5.2 业务异常机制；概要设计 §5.3 REST 规范（envelope `{code,message,data,traceId,timestamp}`）。现状 `common/api/ApiResponse.java` 为 `code=0`，需对齐。
- **验收标准**：
  - `ApiResponse` 对齐 envelope（含 `traceId`/`timestamp`），成功 `code=200`；`ErrorCode` 枚举覆盖 §5.1 全部错误码（40001~50401）并映射正确 HTTP 状态。
  - 落地 `PageResult<T>`、`CidasBusinessException`（code/message/可重试/上下文键）、`@RestControllerAdvice` 全局异常处理器（未知异常→50001）。
  - traceId 从 `X-Trace-Id` 透传，缺省生成；冒烟接口 `/api/smoke` 改造后仍 200 且返回标准 envelope。
  - 产出精简契约速查 `docs/design/接口契约速查.md`：统一 envelope、错误码表、分页/幂等/鉴权头约定（前端据此手写类型）。
- **依赖**：无
- **状态**：已完成

## F-02 前端 · 共享 TS 类型与常量

- **需求/设计/原型引用**：与 F-01 契约速查一一对应；错误码 §5.1、envelope §5.3；角色/权限编码见 `04_seed.sql`。
- **验收标准**：`web/src/api/types.ts`（或 `types/`）定义 `ApiResponse<T>`、`ErrorCode`、`PageResult<T>`、`UserPrincipal`(userId/roles/courseScope)、账号状态/角色/权限编码常量；字段与后端 DTO 一致；`pnpm build` 通过。
- **依赖**：F-01
- **状态**：已完成

## F-03 后端 · 认证与令牌签发

- **需求/设计/原型引用**：详细设计 §2.2 请求上下文、§2.5 安全上下文；概要 §5.2 I01（统一身份认证/本地兼容）、§7.1（令牌短期有效、支持撤销）；DB `edudoc_user`(username/password_hash/auth_source/account_status)；原型认证 5 屏与 `04_登录与认证页面设计规范.md`。
- **验收标准**：`POST /api/v1/auth/login`（LOCAL：BCrypt 校验 password_hash、校验 account_status）、`GET /api/v1/auth/me`（返回用户/角色/权限点）、`POST /api/v1/auth/logout`；签发短期 JWT（claims 含 userId/roles），`AuthProvider` 抽象预留 SSO 扩展点；40101/40102 与账号状态错误码对齐。
- **依赖**：F-01（错误码/envelope）、`01_schema.sql` 已执行
- **状态**：未开始

## F-04 后端 · RBAC 鉴权框架

- **需求/设计/原型引用**：详细设计 §2.5 鉴权顺序、§3.5.1 鉴权算法、§3.5.2 角色/权限接口；DB `edudoc_role`/`edudoc_permission`/`edudoc_user_role`/`edudoc_role_permission`；`04_seed.sql`（3 内置角色 + 20 权限点）。
- **验收标准**：Spring Security 过滤器链 + JWT 过滤器；`UserPrincipal`/`AuthContext` 建立请求上下文；`@RequirePermission("DOCUMENT_READ")` 注解 + `AccessGuard` 切面，鉴权顺序固定（认证→账号状态→角色→课程范围，ACL 留给文档模块）；预置角色/权限常量与 seed 一致；单元测试覆盖无令牌→40101、无权限→40301、账号锁定→拒绝。
- **依赖**：F-03
- **状态**：未开始

## F-05 前端 · 认证流程与路由守卫

- **需求/设计/原型引用**：原型认证 5 屏、`04_登录与认证页面设计规范.md`、`06_三角色路线与交互边界.md`（SSO 回调 3 角色入口）；详细设计 §4.1 路由（/login、/student/*、/teacher/*、/operations/*）、§4.4 前后端交互（401 保留回跳）。
- **验收标准**：登录页（表单校验、密码可见）对接 F-03；token 存取策略明确；路由守卫实现未登录→/login、401→跳登录并保留回跳、403→统一无权限态、按角色路由拦截；登录成功→SSO 回调/角色入口→对应工作台。
- **依赖**：F-02、F-03、F-04、F-08（组件，弱依赖）
- **状态**：未开始

## F-06 前端 · 通用布局与三端框架

- **需求/设计/原型引用**：`docs/prototype/screens/{student,teacher,operations}.md`（三端导航 8/9/7 项、逐屏 ID）；桌面 1440×900；详细设计 §4.1 路由表。
- **验收标准**：应用壳（侧边导航按角色渲染 + 顶部用户/角色/退出 + 内容区）；三端 `/student` `/teacher` `/operations` 路由骨架可切换；集中式路由表（路径→页面组件→所需权限）；对齐原型导航项与工作台入口，含加载/空态占位。
- **依赖**：F-05、F-08
- **状态**：未开始

## F-07 前端 · 请求封装与错误处理

- **需求/设计/原型引用**：详细设计 §4.4 前后端交互（Axios 处理令牌/traceId/超时/错误映射、幂等键）、§2.3 错误码客户端处理、§4.2 错误保留输入。
- **验收标准**：Axios 实例自动附加 `Authorization: Bearer` 与 `X-Trace-Id`、统一超时与错误映射（40101 跳登录、40301/40302 无权限态、4xx 字段提示、5xx 带 traceId 提示）；`Idempotency-Key` 生成与重试复用封装。
- **依赖**：F-02、F-01
- **状态**：未开始

## F-08 前端 · 基础组件库（Ant Design）

- **需求/设计/原型引用**：81 屏大量使用表格（文档列表/用户目录/审计）、表单（上传/新建课程/发布作业）、弹窗（确认/详情）、分页；详细设计 §4.2 交互原则、§4.3 表单校验规则。方案已定：Ant Design。
- **验收标准**：接入 antd + 全局主题/设计令牌（主色/圆角/字号对齐原型）；提供统一封装（按钮、表单含校验、表格含分页/筛选、弹窗、分页）；一个 demo 页跑通。
- **依赖**：F-02（类型）
- **状态**：未开始

## 建议执行顺序

F-01 →（F-02 ∥ F-03）→ F-04 →（F-05 ∥ F-08）→（F-06 ∥ F-07）。组件库 F-08 在 F-02 后可随时并行。
