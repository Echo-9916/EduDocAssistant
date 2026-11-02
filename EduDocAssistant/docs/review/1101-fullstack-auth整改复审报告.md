# 01-fullstack-auth 前后端联合整改复审报告

> 复审日期：2026-11-01（Asia/Shanghai）
> 审查基线：`a40f8a7`（后端 A-04 完成点）
> 当前版本：`HEAD=2de49b2` + 已暂存/未暂存工作区改动
> 审查范围：Auth 后端、登录与角色选择、账号受限、找回密码与权限申请入口、RBAC 查询、相关测试、任务与手动测试文档
> 审查方式：`code-review` Standards/Spec 双轴 + 自动化测试 + 生产构建 + 真实 Oracle 联调 + 桌面渲染检查
> 前序报告：《1101-fullstack-auth前端代码审查与修改报告.md》

## 一、联合复审结论

**本轮结论：不通过，暂不建议合并。**

前后端主链路可以编译和运行，真实 Oracle 环境下三角色登录、`/auth/me`、登出、旧 token 失效等核心能力通过；`displayName`、真实受限原因和角色选择页主流程也已落地。按用户最新决策排除移动端验收后，仍有两个 P1 问题直接影响当前业务验收：

1. 角色选择页仍存在三条旁路，用户可以不经过 `/roles` 直接进入工作台。
2. RBAC 角色与权限查询未完整执行生命周期和角色状态过滤，未来角色可能提前生效，停用角色仍可能贡献权限。

移动端响应式适配不纳入本轮验收；实测结果仅作为范围外观察保留，不计入本轮 P1 或合并阻断项。

此外，登录成功链路缺少事务边界，`40101` 仍依赖中文提示文案区分账号受限，权限申请入口在真实鉴权下会返回登录页，均需在合并前处理。

| 维度 | 结论 | 说明 |
| --- | --- | --- |
| Standards | 不通过 | 存在 RBAC 生命周期缺陷、登录写入事务缺失、前端依赖文案契约 |
| Spec | 部分通过 | 认证主链路和真实受限原因已实现；角色选择流程仍不满足验收；移动端本轮不纳入 |
| 回归影响 | 有风险 | 后端主链路未发现破坏性回归；前端角色入口和受限申请入口存在业务回归；移动端不计入 |
| 自动化验证 | 通过 | 后端 48/48、前端 48/48、生产构建通过 |
| 真实环境联调 | 后端通过 | Oracle 三角色认证、审计、登录时间和令牌撤销均实测通过 |
| 可提交/合并 | 暂不建议 | 先关闭 P1，并处理与当前决策冲突的文档和契约 |

## 二、验证结果

| 验证项 | 命令/方式 | 实际结果 |
| --- | --- | --- |
| 后端单元与集成测试 | `mvn test` | 通过：48/48 |
| 前端单元测试 | `pnpm test --run` | 通过：11 个测试文件，48/48 |
| 前端类型与生产构建 | `pnpm build` | 通过：`tsc --noEmit` + `vite build` |
| 差异空白检查 | `git diff --check a40f8a7` | 通过；仅有 LF/CRLF 提示，无空白错误 |
| Oracle 连通性 | `oracle-dev` 容器健康检查 | 通过：容器健康，Oracle 监听 1521 |
| 后端真实登录 | 启动服务后对真实 Oracle 发起请求 | 通过：student/teacher/ops 登录均为 HTTP 200 |
| 当前用户接口 | 三角色携带 token 请求 `/auth/me` | 通过：HTTP 200，角色和 `displayName` 正确 |
| 错误凭据 | 错误密码登录 | 通过：HTTP 401，`code=40101` |
| 锁定账号 | locked 账号登录 | 通过：HTTP 401，`code=40101`，受限文案正确 |
| 登出与撤销 | logout 后复用旧 token | 通过：logout 204；旧 token 复用 HTTP 401，`code=40102` |
| 异常 Authorization | malformed Bearer、无 Header、Basic | 通过：均为 HTTP 401/40101，未退化为 50001 |
| 占位接口 | reset-password、access-request | 通过：HTTP 200/200，返回“该功能正在开发中” |
| 数据库审计 | 查询 Oracle 审计与登录时间 | 通过：`last_login_time` 更新；AUTH SUCCESS/40101 审计新增 |
| 桌面渲染 | Chrome `1440x900` 访问 `/login` | 通过：左右分栏和表单均可见 |
| 移动端渲染 | Chrome `390x844` 访问 `/login` | 本轮不考虑：实测表单不可见，仅作为范围外观察，不计入验收结论 |

## 三、问题清单

### P1-1 角色选择页仍有三条绕过路径

最新业务决策要求所有用户先进入 `/roles`，再点击「进入工作台」进入对应工作台。当前只在普通登录且没有 `redirect` 时遵守该流程：

- `web/src/pages/Login.tsx:17-18`：已登录访问 `/login` 时直接执行 `homeOf(user.roles)`。
- `web/src/pages/Login.tsx:28`：存在 `redirect` 时优先回跳目标工作台，跳过 `/roles`。
- `web/src/App.tsx:16-20`：已登录访问根路径 `/` 时直接执行 `homeOf(user.roles)`。
- `web/src/pages/Login.test.tsx:92-98`、`web/src/pages/Login.test.tsx:120-123`：测试把旧行为固化为预期，导致测试全绿但业务口径仍错误。

这违反 `docs/tasks/01-fullstack-auth.md:76` 的统一角色选择要求，也与用户“先进选择页，再进入工作台”的最新决策冲突。

**建议**：所有认证落点统一指向 `/roles`；将原始 `redirect` 安全保存在路由 state 中，用户点击「进入工作台」后再回跳。同步覆盖普通登录、深链回跳、已登录访问 `/login`、已登录访问 `/` 四种入口。

### P1-2 RBAC 生命周期与角色状态过滤不完整

`backend/src/main/resources/mapper/UserAccountMapper.xml:21-27` 的角色查询只校验 `r.status = 'A'` 和 `effective_to`，未校验 `effective_from <= SYSTIMESTAMP`，未来生效角色会提前进入用户角色集合。

`backend/src/main/resources/mapper/UserAccountMapper.xml:29-36` 的权限查询既未关联 `edudoc_role.status = 'A'`，也未校验 `effective_from`，停用角色仍可能贡献权限点，未来角色关联也可能提前生效。

这会直接破坏后端授权边界，并影响所有依赖 `UserPrincipal.roles/permissions` 的接口。

**建议**：角色与权限查询统一使用相同时间窗口条件：

```sql
ur.effective_from <= SYSTIMESTAMP
AND (ur.effective_to IS NULL OR ur.effective_to > SYSTIMESTAMP)
```

权限查询还必须连接 `edudoc_role` 并过滤 `status = 'A'`。增加“未来角色、已过期角色、停用角色、有效角色”四类测试数据。

### P2-1 登录成功写入缺少事务边界

`backend/src/main/java/com/cidas/edudoc/access/application/impl/AuthServiceImpl.java:41-44` 依次更新登录时间、写 SUCCESS 审计、签发 token，但服务方法没有事务边界，仓库内也未发现 `@Transactional`。

可能出现两类不一致：

- 登录时间已更新，但审计写入失败，接口返回失败。
- 登录时间与 SUCCESS 审计已提交，但 token 签发失败，留下“成功登录”的虚假审计。

**建议**：明确事务范围。若数据库写入必须原子，给登录编排加事务；若审计必须独立留存，应使用独立事务并明确失败策略，避免“响应失败但部分状态已提交”的隐性行为。补充故障注入测试。

### P2-2 `40101` 多语义仍依赖中文文案

`backend/src/main/java/com/cidas/edudoc/access/application/impl/LocalAuthProvider.java:35-38` 仍返回带固定中文前缀的字符串；`web/src/pages/Login.tsx:30-33` 使用 `message.includes('账号受限')` 区分受限账号；`web/src/pages/AccountRestricted.tsx:5-10` 又通过“锁定/未激活/注销”中文子串决定标签。

本轮修正了“所有受限账号都显示为锁定”的表面问题，但没有建立稳定契约。后端只要调整标点、前缀或本地化文案，前端受限流程就会退回普通登录错误。

**建议**：保留顶层 `40101` 兼容性，在响应 `data` 或专用子码中返回稳定字段，例如 `accountStatus: "L" | "I" | "D"`；前端基于结构字段分支，中文只用于展示。

### P2-3 权限申请入口在真实鉴权下会回到登录页

`web/src/pages/AccountRestricted.tsx:35-37` 的「申请临时访问权限」直接跳转 `/operations/users`。受限用户此时未认证，真实应用中的 `/operations/users` 位于 `RequireAuth` 下，会被拦截并跳回 `/login?redirect=...`。

`web/src/pages/AccountRestricted.test.tsx:53-59` 使用的是未包裹真实鉴权守卫的模拟路由，因此测试通过但无法证明业务流程可用。

**建议**：明确权限申请的真实落点。若必须公开，注册不受 `RequireAuth` 保护的申请页；若运维页是目标，则需要先说明申请用户为何能访问该页，并使用真实 `RequireAuth` 覆盖测试。

### P2-4 任务、手动测试与 Apifox 文档仍冲突

- `docs/tasks/01-fullstack-auth.md:65` 仍要求已登录访问 `/login` 直回 `homeOf`，与同一文件 `:76` 的统一 `/roles` 流程冲突。
- `docs/tasks/01-fullstack-auth.md:88` 仍把“返回登录”列为账号受限页验收项，与用户最新决策冲突。
- `docs/tasks/01-fullstack-auth.md:122-123` 仍写登录后直接回原页及“权限申请占位提示”，与角色选择流程和当前实际入口不一致。
- `docs/tasks/01-fullstack-auth.md:128` 仍写浏览器人工冒烟留待部署环境，但 `:130` 已标“已完成”。
- `docs/test/AuthTest.md:93` 仍预期旧文案“账号受限，请联系管理员”。
- `docs/test/AuthTest.md:47-52`、`:108-113` 的响应示例仍没有 `displayName`。
- `docs/test/apifox/auth-login.postman_collection.json:88` 仍记录旧锁定文案。

**建议**：把任务、手动测试和 Apifox 预期统一到当前契约。深链回跳应明确为“登录 → `/roles` → 进入工作台后回跳”，受限页不再保留“返回登录”，响应示例补齐 `displayName`。

### P3-1 关键链路测试覆盖不足

- `backend/src/test/java/com/cidas/edudoc/access/infrastructure/UserAccountRepositoryTest.java:26-39` 没有 stub 或断言 `selectDisplayName`，无法证明姓名进入 `UserPrincipal`。
- 后端缺少 `displayName` 从数据库到 `/auth/me` JSON 的契约断言。
- 前端缺少 `displayName` 为空时回退 `username` 的测试。
- 受限页测试只覆盖锁定和未激活，未覆盖注销、未知状态与无 state 回退。
- 未覆盖 RBAC 的未来角色、过期角色、停用角色和权限生命周期。
- 未覆盖登录写入过程中审计或签发失败时的事务行为。
- 未覆盖 `/roles` 的 `redirect`、已登录 `/login` 和根路径集成行为。

这些缺口不会单独阻断功能，但会让“数据库 → UserPrincipal → HTTP → 前端”链路和 RBAC 边界在测试全绿时仍然断裂。

## 四、逐项检查清单

| 检查项 | 结果 | 证据/说明 |
| --- | --- | --- |
| L/I/D 账号返回不同受限原因 | 通过 | `LocalAuthProvider.java:43-53`；I/D 测试通过 |
| 未知账号状态有回退文案 | 通过 | 返回“账号状态异常，请联系管理员” |
| 错误密码仍返回通用凭据错误 | 通过 | 口令匹配失败时返回 `Optional.empty()` |
| 未在口令校验前泄露账号状态 | 通过 | 仅口令匹配后读取并返回具体状态 |
| SSO 账号不走本地口令校验 | 通过 | `LocalAuthProviderTest` 覆盖 |
| 登录成功更新登录时间 | 通过 | 真实 Oracle 查询确认 `last_login_time` 更新 |
| 登录成功写 SUCCESS 审计 | 通过 | 真实 Oracle 查询确认新增记录 |
| 登录失败写 40101 审计 | 通过 | 真实 Oracle 查询确认新增记录 |
| 登录成功写入具备事务原子性 | **不通过** | `AuthServiceImpl.java:41-44` 无事务，故障时可能部分提交 |
| JWT 签发与 token 契约 | 通过 | 三角色真实登录和 `/auth/me` 通过 |
| 登出撤销 token | 通过 | logout 204，旧 token 复用 40102 |
| 锁定账号旧 token 失效 | 通过 | JWT 过滤器每次从数据库刷新账号状态 |
| 无效 Authorization 返回 40101 | 通过 | malformed Bearer、无 Header、Basic 均实测通过 |
| `displayName` 进入 UserPrincipal | 静态通过 | Mapper、Repository、record 已串联 |
| `displayName` DB→API→UI 有测试 | **不通过** | 关键测试未 stub/断言 displayName |
| 姓名为空时前端回退 username | 代码通过、测试缺失 | `RoleEntry.tsx:40` 使用 `displayName` 或 `username` |
| 身份卡不显示 32 位 GUID | 通过 | `RoleEntry.tsx:42` 显示 username |
| 角色查询过滤过期时间 | 通过 | 过滤 `effective_to` |
| 角色查询过滤未来生效时间 | **不通过** | 缺少 `effective_from` 条件 |
| 角色查询过滤停用角色 | 通过 | 过滤 `edudoc_role.status = 'A'` |
| 权限查询过滤过期时间 | 通过 | 过滤 `effective_to` |
| 权限查询过滤未来生效时间 | **不通过** | 缺少 `effective_from` 条件 |
| 权限查询过滤停用角色 | **不通过** | 未连接或过滤 `edudoc_role.status` |
| 40101 密码错误不跳受限页 | 通过 | `Login.test.tsx:110-118` |
| 40101 受限账号跳受限页 | 通过（当前文案契约） | `Login.test.tsx:101-108` |
| 40101 使用稳定结构化状态 | **不通过** | 前后端依赖中文 `includes` |
| 其它 4xx 在表单内提示 | 通过 | `Login.tsx:36-37` |
| 登录失败保留输入 | 通过 | 未重置表单字段 |
| 普通登录成功先进入 `/roles` | 通过 | `Login.tsx:28` 默认分支 |
| 带 `redirect` 仍先进入 `/roles` | **不通过** | redirect 优先，绕过 `/roles` |
| 已登录访问 `/login` 仍先进入 `/roles` | **不通过** | `Login.tsx:17-18` 直进工作台 |
| 已登录访问 `/` 仍先进入 `/roles` | **不通过** | `App.tsx:16-20` 直进工作台 |
| `/roles` 未登录回登录页 | 通过 | `RoleEntry.tsx:24` |
| `/roles` 身份卡、课程和进入工作台 | 通过 | `RoleEntry.test.tsx` |
| 演示角色路线可临时使用 | 通过 | `RoleEntry.tsx:75` |
| 切换账号登出并回登录页 | 通过 | `RoleEntry` 测试覆盖 |
| 账号受限页不显示“返回登录” | 通过 | 符合用户最新决策 |
| 申请临时访问权限入口真实可达 | **不通过** | 未认证时 `/operations/users` 会被 `RequireAuth` 拦回登录 |
| 找回密码占位流程 | 通过 | `ForgotPassword.test.tsx` 通过 |
| 40101/40102 全局失效跳转 | 静态通过 | `web/src/api/http.ts:49-58`；未做全部浏览器路径联调 |
| 三角色工作台权限接线未破坏 | 通过（单测） | 相关路由和 AppShell 测试通过 |
| 桌面布局 | 通过 | Chrome `1440x900` |
| 移动端布局 | 不纳入本轮 | Chrome `390x844` 实测表单不可见，但用户明确当前不考虑移动端 |
| 前端构建 | 通过 | `pnpm build` |
| 后端测试 | 通过 | `mvn test` 48/48 |
| 手动测试文档与实现一致 | **不通过** | 旧文案、缺 `displayName` |
| 任务验收口径一致 | **不通过** | 角色选择、返回登录、权限申请和 A-10 状态冲突 |
| 真实 Oracle 后端端到端 | 通过 | 三角色、me、错误密码、受限、登出、撤销、审计均通过 |
| 真实浏览器三角色端到端 | 未完整验证 | 已做桌面登录页渲染；未将三角色浏览器主流程作为通过证据 |

## 五、前序问题闭环情况

| 前序编号 | 本次结论 | 复审说明 |
| --- | --- | --- |
| S-1 受限状态恒显锁定 | 部分解决 | 已按 L/I/D 显示，但仍依赖中文文案 |
| S-2 GUID 代替姓名 | 代码已解决、测试有缺口 | `displayName` 全链路已补；缺少 DB→API→UI 断言 |
| S-3 “记住此设备”无行为 | 已按占位接受 | 已注明待接线 |
| S-4/S-5 设计令牌 | 豁免 | 遵从用户“后续明确要求优先”的决策 |
| S-6 找回密码演示邮箱 | 已按占位接受 | 已注明待接线 |
| N-1 同步 A-06 口径 | 部分解决 | A-06 已更新，A-05/A-07/A-10 仍保留旧口径 |
| N-2 返回登录入口 | 已撤销 | 页面无该入口，符合决策 |
| N-3 测试数字与结构 | 基本解决 | 实测前后端均 48/48；文档仍有旧契约 |
| N-4 受限原因按真实状态 | 代码已解决 | 显示层已分 L/I/D；结构化契约仍待补 |
| N-5 displayName/占位注释 | 代码已解决、测试有缺口 | 主链路可读，缺端到端断言 |
| 角色选择页旁路 | **未关闭** | `/login` 已登录、redirect、`/` 三条路径仍绕过 |
| 移动端登录 | 不纳入本轮 | 用户明确当前不考虑移动端；实测事实保留但不作为阻断项 |
| RBAC 生命周期 | **新发现/未关闭** | 未来角色提前生效，停用角色权限未过滤 |
| 登录写入一致性 | **新发现/未关闭** | 登录时间、审计、签发无统一事务边界 |

## 六、新增代码对原有功能的影响

- `UserPrincipal` 新增 `displayName` 后，所有构造点均已同步；主代码和测试可编译，未发现该字段导致的既有登录回归。
- `displayName` 不进入 JWT 载荷；JWT 过滤器仍按用户 ID/用户名重新加载主体，未发现数据结构变化破坏鉴权链。
- 账号状态判断仍位于口令校验之后，没有新增“仅凭用户名即可枚举状态”的直接泄露。
- 后端认证主链路在真实 Oracle 下通过，新增 `displayName` 与状态代码未破坏三角色登录、登出和旧 token 撤销。
- 前端新增登录页和角色选择页后，普通无 redirect 登录路径正确；但 redirect、已登录入口和根路径仍保留旧跳转，属于业务回归。
- 认证页桌面布局正常；移动端核心动作不可见，但用户已明确当前不考虑移动端，因此不计入本轮回归验收。
- 权限申请按钮在测试路由中可达，在真实 `RequireAuth` 路由下不可达，属于测试环境与生产行为不一致。
- 自动化测试通过不能替代业务验收；本轮真实 Oracle 支持后端主链路，但角色选择旁路、RBAC 和事务一致性仍需修复。

## 七、必须修改项

| 优先级 | 修改项 | 验收标准 |
| --- | --- | --- |
| P1 | 统一所有登录/已登录入口先走 `/roles` | 普通登录、redirect 登录、已登录 `/login`、已登录 `/` 均先到 `/roles`；进入工作台后再按规则回跳 |
| 范围 | 移动端响应式适配 | 当前不考虑，不作为本轮整改或合并阻断项 |
| P1 | 修复 RBAC 生命周期过滤 | 角色和权限均校验 `effective_from/effective_to`；权限查询过滤停用角色 |
| P2 | 增加结构化账号状态 | 前端不再依赖 `message.includes()` 判断 40101 业务语义 |
| P2 | 明确登录写入事务 | 登录时间、审计与 token 签发失败时不产生不一致状态，并有故障注入测试 |
| P2 | 修复权限申请真实落点 | 受限用户可实际进入公开申请页，或在真实鉴权下明确可达路径 |
| P2 | 同步任务和测试文档 | A-05/A-07/A-10、AuthTest、Apifox 与当前决策和实现一致 |
| P3 | 补关键链路测试 | 覆盖 displayName DB→API→UI、空姓名回退、D/未知状态、RBAC 生命周期和事务失败 |

## 八、审查限制与如实说明

- 本轮已实际调用 `code-review`、`agent-knowledge-recall` 和 `agent-task-retrospective` 规则，并读取经验库入口、索引、模板及相关 auth Lesson。
- 两个 fresh Standards/Spec 子代理再次因上游 `unsupported_encrypted_agent_content` 失败，未产出独立审查结论；本报告由主审查基于实际 diff、测试、代码、真实 Oracle 联调和渲染证据完成。
- 本轮未修改业务代码、测试或任务文档，仅更新本复审报告。
- 工作区在本轮开始前已存在大量已暂存/未暂存改动，本报告不回退、不重排、不声称这些改动的归属。
- 未记录账号密码、JWT 原文或其他敏感凭据。
- 已完成后端真实 Oracle 联调，但未将“三角色浏览器完整业务链路”作为通过证据；该缺口不能由单元测试替代。

## 九、最终判定

本轮整改方向正确，后端认证主链路、真实 Oracle 联调、`displayName` 和真实受限原因均取得实质进展，新增代码未发现编译或单测层面的破坏性回归。

但当前版本仍不满足本轮业务验收：角色选择页可被三条路径绕过，RBAC 生命周期过滤存在基础授权风险。移动端已明确暂不考虑，不计入本次阻断结论。

关闭两个 P1、P2 事务与入口问题、同步文档，并补真实浏览器主流程证据后，可进入下一轮联合复审。

## 十、整改执行记录（2026-11-01）

| 编号 | 执行结果 |
| --- | --- |
| P1-1 | 四条入口统一先进 `/roles`：`Login.tsx` 已登录访问改为 `<Navigate to="/roles">`（深链存 state）、登录成功改为 `navigate(landingPath, {state:{redirect}})`；`App.tsx` Home 改跳 `/roles`；`RoleEntry` 读取 `state.redirect`，「进入工作台」按 `redirect || homeOf` 回跳。`Login.test` 两用例改为断言先进角色选择页，`RoleEntry.test` 新增 redirect state 回跳用例 |
| P1-2 | `UserAccountMapper.xml`：角色查询补 `ur.effective_from <= SYSTIMESTAMP`；权限查询补 `effective_from` 并 JOIN `edudoc_role` 过滤 `status='A'`。新增 `RbacLifecycleIntegrationTest`（MyBatis 直载 mapper XML 对真实 Oracle）：有效/未来/过期/停用四类关联 + displayName 契约，本轮实跑通过（非跳过） |
| P2-1 | `AuthServiceImpl` 注入 `TransactionTemplate`，登录时间 + SUCCESS 审计 + 令牌签发同事务，任一失败整体回滚；拒绝审计保持事务外独立留存。`AuthServiceTest` 新增故障注入测试（签发失败 → `rollback` 调用、`commit` 未调用） |
| P2-2 | 后端 `LocalAuthProvider` 抛异常携带 `context={accountStatus}`，`GlobalExceptionHandler` 将非空 context 写入 envelope `data`（`ApiResponse.error` 新增 data 重载）；前端 `ApiError` 增加 `data` 字段，`Login.tsx` 改用 `data.accountStatus` 判定受限，`AccountRestricted.tsx` 按 L/I/D 状态码渲染标签，中文文案仅用于展示 |
| P2-3 | 「申请临时访问权限」不再跳 `/operations/users`（受限用户未认证会被 `RequireAuth` 拦回登录页），改为调用公开占位接口 `POST /auth/access-request` 并页内提示「该功能正在开发中」；A-09 任务口径同步 |
| P2-4 | 任务文档 A-05/A-07/A-09/A-10 口径统一到「先 `/roles` 再回跳」「无返回登录」「占位接口申请」「52/52 实测数字」；`AuthTest.md` §1/§4 响应示例补 `displayName`、§3 改新受限文案 + `data.accountStatus`；Apifox 集合锁定用例描述同步 |
| P3-1 | 已补：`UserAccountRepositoryTest.principalCarriesDisplayNameFromDb`、RBAC 生命周期集成测试（含 displayName DB→principal）、事务故障注入、受限页 D/未知/无 state 回退用例、`RoleEntry` displayName 空回退用例、`/roles` redirect 用例。未补：根路径 `/` 集成测试（Home 为与已登录 `/login` 相同模式的简单 `Navigate`，由代码审查覆盖）；真实浏览器三角色端到端证据仍待部署环境补齐 |
| 移动端 | 按决策不纳入本轮，未改动 |

验证：`mvn test` 52/52 全绿（含真实 Oracle 集成测试实跑）；`pnpm test` 52/52 全绿；`tsc --noEmit`、`pnpm build` 通过。
