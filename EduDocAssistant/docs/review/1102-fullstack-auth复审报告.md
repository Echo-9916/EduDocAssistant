# 01-fullstack-auth 新一轮前后端联合复审报告

> 复审日期：2026-11-02（Asia/Shanghai）
> 审查基线：`a40f8a7`
> 当前版本：`HEAD=2de49b2` + 已暂存/未暂存工作区改动
> 任务依据：`docs/tasks/01-fullstack-auth.md`
> 设计依据：`docs/design/04_登录与认证页面设计规范.md`、`docs/design/06_三角色路线与交互边界.md`
> 审查范围：Auth 后端、登录与角色选择、RBAC、账号受限、找回密码、权限申请、自动化测试、真实 Oracle 接口和桌面浏览器联调
> 范围说明：按用户最新要求，移动端不在本轮验收范围内，不列为 finding 或阻断项。

## 复审结论

**核心认证闭环通过，但本阶段仍不能记为“完全通过”。**

- 上一轮两个 P1 已关闭：所有登录/已登录入口统一先进入 `/roles`；RBAC 角色和权限查询已正确执行生效窗口与角色状态过滤。
- 上一轮结构化账号状态、登录事务、公开权限申请入口已闭环；真实 Oracle 和真实桌面浏览器主流程均通过。
- 本轮仍发现 1 个 Standards 轴 P2：账号状态在登录期间并发变化时，`Optional.of(null)` 会把认证请求变成 50001。
- 本轮仍发现 1 个 Spec 轴 P2：A-06 要求的学生/教师/运维 3 个演示入口，对单角色真实账号不可用；学生点击“教师”入口实测进入 `/403`，第三个入口文案仍为“教学管理”而不是“运维”。
- 另有 2 个 P3 健壮性/测试卫生问题：外部 `redirect` 会让“进入工作台”停在 `/roles`；新增 Oracle 集成测试包含本地数据库凭据 fallback。

| 评估项 | 结论 | 摘要 |
| --- | --- | --- |
| Standards | 不通过 | P2 并发状态错误 1 项；P3 重定向边界与测试凭据卫生 2 项 |
| Spec | 不通过 | A-01～A-05、A-07～A-10 通过；A-06 演示入口仍有 P2 偏差 |
| 核心业务回归 | 未发现阻断性回归 | 三角色登录、角色姓名、登出撤销、受限流程均通过 |
| 自动化验证 | 通过 | 后端 52/52，前端 52/52，生产构建通过 |
| 真实环境验证 | 通过（核心链路） | Oracle 三角色接口与桌面三角色工作台落点通过 |
| 是否建议宣告阶段完成 | 暂不建议 | 先关闭角色演示入口 P2 与后端并发状态 P2 |

## Standards

### Findings

#### P2-S1 账号状态并发变化时，登录失败会退化为 50001

`backend/src/main/java/com/cidas/edudoc/access/application/impl/LocalAuthProvider.java:42` 使用：

```java
return Optional.of(repository.loadPrincipal(found.getId(), found.getUsername()));
```

`backend/src/main/java/com/cidas/edudoc/access/infrastructure/UserAccountRepository.java:31` 在第二次账号状态检查失败时返回 `null`。因此，账号在两次查询之间被锁定、注销或删除时，`Optional.of(null)` 会抛出 `NullPointerException`，最终由全局异常处理器映射为 HTTP 500、`50001`。

这不是正常的错误分支，而是认证竞态下的服务端异常。建议改为 `Optional.ofNullable(...)`，并将 `null` 明确映射为稳定的 40101 结果；补充“首次查询为正常、二次状态检查返回 null”的单测。

#### P3-S1 外部或畸形 `redirect` 会让“进入工作台”无响应

`web/src/pages/Login.tsx:17` 直接读取 query 中的 `redirect`，`web/src/auth/RoleEntry.tsx:71` 直接执行 `navigate(redirect || homeOf(user.roles))`，未限制其为站内单斜杠路径。

真实浏览器验证 `redirect=https://evil.example/path` 后，点击“进入工作台”仍停在 `/roles`，没有离开站内，也没有触发页面异常。因此未确认开放重定向，但会形成无反馈的死路。建议仅接受以单个 `/` 开头且不以 `//` 开头的内部路径，非法值回退到 `homeOf(...)`。

#### P3-S2 真实 Oracle 集成测试含本地数据库凭据 fallback

`backend/src/test/java/com/cidas/edudoc/access/infrastructure/RbacLifecycleIntegrationTest.java:35-36` 为数据库用户名和密码提供了本地 fallback。该做法与现有 `application.yml` 的开发 fallback 一致，但新测试再次复制了隐含凭据，属于测试卫生问题。

建议测试环境显式注入凭据；缺失时使用 assumption 跳过真实库测试，而不是默认连接一个带已知凭据的本地实例。本报告不复述凭据内容。

### Standards Conclusion

- 没有发现事务边界、RBAC 有效期过滤或结构化错误响应方面的回归。
- `canonical` diff 与现有编码风格基本一致，测试与生产构建均通过。
- 本次最严重 Standards 问题是 `P2-S1`，其次是两个 P3。
- 轻微的重复状态映射（后端受限原因、前端状态标签）属于边界展示逻辑，不单独列为 smell。

## Spec

### Findings

#### P2-A06 单角色账号无法使用另外两个“演示角色路线”，且运维文案不一致

任务要求见 `docs/tasks/01-fullstack-auth.md:77`：

> 展示身份卡片（姓名/角色/课程）+ 学生/教师/运维 3 个角色演示入口。

设计说明见 `docs/design/04_登录与认证页面设计规范.md:70-71` 和 `docs/design/06_三角色路线与交互边界.md:15-17`，均要求 3 个角色入口进入对应路线。

当前实现有两处偏差：

1. `web/src/auth/RoleEntry.tsx:15` 将 OPS 入口显示为“教学管理”，与任务和当前设计要求的“运维”不一致。
2. `web/src/auth/RoleEntry.tsx:82` 直接导航到目标角色工作台，但 `web/src/App.tsx:46` 的工作台仍由 `RequireAuth roles={[role]}` 严格保护。种子账号均为单角色，因此：
   - 学生点击“教师”入口实测进入 `/403`；
   - 学生点击“教学管理”入口同样不能进入对应路线。

`RoleEntry.test.tsx` 使用同时具备 `STUDENT` 和 `TEACHER` 的模拟用户验证教师入口，未覆盖真实单角色种子账号，因此测试全绿但没有证明 A-06 的真实演示链路可用。

**建议**：将“演示角色路线”与生产 RBAC 明确隔离，例如使用受控的 demo 路由/演示会话，并保持真实工作台鉴权不放宽；同时将入口名称统一为“运维”。如果产品决定不再要求可点击演示，应同步修改任务与设计验收口径，而不是保留一个必然进入 403 的入口。

### Spec Conclusion

| 任务 | 检查结果 | 说明 |
| --- | --- | --- |
| A-01 种子账号 | 通过 | 4 个 LOCAL 种子账号可登录/受限，真实 Oracle 可用 |
| A-02 登录闭环 | 通过 | 登录、`me`、`displayName`、last_login、审计、40101 |
| A-03 令牌撤销 | 通过 | 三角色 logout 204，旧 token 均为 40102 |
| A-04 占位接口 | 通过 | reset-password/access-request 均返回 200 占位文案 |
| A-05 登录页与回跳 | 通过 | 三角色先进 `/roles`，“进入工作台”后进入各自工作台 |
| A-06 角色入口 | **不通过（P2）** | 3 个入口能渲染，但单角色账号无法进入另外两个演示路线；OPS 文案错误 |
| A-07 账号受限 | 通过 | locked 返回 40101、`data.accountStatus=L`，页面显示“已锁定” |
| A-08 找回密码 | 通过 | 字段、强度/一致性校验和占位提示可用 |
| A-09 权限申请 | 通过 | 受限用户调用公开占位接口并显示提示 |
| A-10 端到端闭包 | 核心通过，A-06 例外 | 三角色真实接口与桌面工作台落点通过；演示入口偏差未闭环 |

- Spec 轴共 1 个 P2 finding，包含“不可用”和“文案错误”两个同源表现。
- 移动端按要求未检查、未列为偏差。

## 验证证据

| 验证项 | 方式 | 实际结果 |
| --- | --- | --- |
| 后端测试 | `mvn test` | 52/52 通过，0 failure，0 error |
| RBAC 真库集成测试 | `RbacLifecycleIntegrationTest` | 1/1 实跑通过，未被 assumption 跳过 |
| 前端测试 | `pnpm test --run` | 11 个文件，52/52 通过 |
| 生产构建 | `pnpm build` | `tsc --noEmit` + Vite build 通过 |
| 差异检查 | `git diff --check a40f8a7` | 退出码 0；仅 LF/CRLF 警告 |
| 三角色登录 | 真实 Oracle HTTP 请求 | student/teacher/ops 均 HTTP 200 |
| 三角色 `me` | 真实 Oracle HTTP 请求 | 角色分别为 STUDENT/TEACHER/OPS，displayName 正确 |
| 三角色登出 | 真实 Oracle HTTP 请求 | logout 204；旧 token 复用均为 401/40102 |
| 错误密码 | 真实 Oracle HTTP 请求 | 401/40101，无结构化账号状态 |
| 锁定账号 | 真实 Oracle HTTP 请求 | 401/40101，`data.accountStatus=L` |
| 占位接口 | 真实 Oracle HTTP 请求 | 均为 200 + “该功能正在开发中” |
| 桌面三角色浏览器 | Chrome 1440×900 | 三个账号均先到 `/roles`，再进入各自工作台 |
| 深链闭包 | Chrome 真实浏览器 | `/student/workbench` → 登录 → `/roles` → 回跳原页 |
| 已登录入口 | Chrome 真实浏览器 | 访问 `/login`、`/` 均转到 `/roles` |
| 切换账号 | Chrome 真实浏览器 | 登出后到 `/login` |
| 受限流程 | Chrome 真实浏览器 | locked → `/account-restricted` → 权限申请占位提示 |
| 演示入口实测 | Chrome 真实浏览器 | 学生点击“教师”入口进入 `/403` |
| 外部 redirect | Chrome 真实浏览器 | 不离开站内，但“进入工作台”停在 `/roles` |
| 移动端 | 未执行验收 | 按用户要求本轮不考虑 |

### 浏览器残留观察

桌面主流程渲染正常，但控制台出现 2 个未定位的 404 资源错误和 1 个预期内的登录 401。页面、Logo、表单和工作台均正常显示，因此暂不把它列为独立 finding；后续发布前仍建议定位 404 的资源 URL。

## 上轮问题闭环表

| 上轮问题 | 本轮结果 | 证据 |
| --- | --- | --- |
| P1-1 三条路径绕过 `/roles` | 已关闭 | 登录、深链、已登录 `/login`、根路径真实浏览器均先到 `/roles` |
| P1-2 RBAC 未过滤未来/停用角色 | 已关闭 | XML 已校验 `effective_from/effective_to/status`；真库测试实跑通过 |
| P2-1 登录写入无事务 | 已关闭 | `TransactionTemplate` 覆盖 last_login、SUCCESS 审计和签发；故障注入测试回滚 |
| P2-2 40101 依赖中文文案 | 已关闭 | 后端返回 `data.accountStatus`；登录页按结构化字段分支 |
| P2-3 受限用户权限申请不可达 | 已关闭 | 改为公开 `/auth/access-request`，真实浏览器提示可见 |
| P2-4 文档与实现冲突 | 基本关闭 | 任务、AuthTest、Apifox 已同步；A-06 自身的演示偏差仍存在 |
| P3-1 关键链路测试不足 | 大部分关闭 | displayName、RBAC、事务、受限状态、redirect 已补；并发状态和单角色演示仍未覆盖 |

## 完整检查清单

| 检查项 | 结果 | 证据/说明 |
| --- | --- | --- |
| LOCAL 账号登录 | 通过 | 三角色真实 Oracle 200 |
| SSO 账号不按本地密码认证 | 通过 | `LocalAuthProviderTest` |
| 错误密码统一 40101 | 通过 | 真库 401/40101 |
| L/I/D 受限原因区分 | 通过 | provider 与前端标签映射 |
| 口令校验前不暴露账号状态 | 通过 | 状态只在密码匹配后返回 |
| 锁定账号真实联调 | 通过 | 401/40101 + `accountStatus=L` |
| 登录成功更新 last_login | 通过 | 真实库验证 |
| 登录成功/拒绝写审计 | 通过 | 真库与单测验证 |
| 登录写入事务原子性 | 通过 | 签发失败回滚测试 |
| 登录状态并发变化返回稳定 40101 | **不通过（P2）** | `loadPrincipal` 返回 null 时 `Optional.of` 抛 NPE |
| JWT 含唯一 jti | 通过 | `JwtServiceTest` |
| JWT 短期有效 | 通过 | 默认 1800 秒 |
| RBAC 每次请求实时加载 | 通过 | Filter 使用 PrincipalLoader |
| 角色未来生效过滤 | 通过 | 真库集成测试 |
| 角色过期过滤 | 通过 | 真库集成测试 |
| 停用角色过滤 | 通过 | `r.status='A'` + 集成测试 |
| 权限生命周期过滤 | 通过 | 与角色关联窗口一致 |
| 停用角色不贡献权限 | 通过 | 真库集成测试 |
| `displayName` DB→Principal→API | 通过 | 单测、集成测试、真实 HTTP |
| logout 撤销当前 JWT | 通过 | 三角色 204 + 旧 token 40102 |
| 无 token/坏 token 返回 40101 | 通过 | 测试与安全入口处理器 |
| reset-password 占位 | 通过 | 200 + 占位文案 |
| access-request 公开可用 | 通过 | 受限页面真实浏览器可达 |
| 登录表单字段/校验 | 通过 | 前端测试 |
| 登录失败保留输入 | 通过 | 未重置表单状态 |
| 用户名 trim | 通过 | 提交测试 |
| 密码可见切换 | 通过 | `Input.Password` + 测试 |
| 所有登录入口先进 `/roles` | 通过 | 单元测试与真实浏览器 |
| redirect 深链回跳 | 通过 | 合法站内路径实测回跳 |
| redirect 非法值安全回退 | **不通过（P3）** | 外部 URL 点击后停留在 `/roles` |
| `/roles` 身份卡/课程/按钮 | 通过 | 组件测试与桌面浏览器 |
| 姓名为空回退 username | 通过 | `RoleEntry.test.tsx` |
| 进入工作台按当前角色落点 | 通过 | 三角色浏览器进入各自工作台 |
| 学生演示入口可进入学生路线 | 通过 | 当前账号角色匹配 |
| 教师演示入口对单角色学生可用 | **不通过（P2）** | 真实浏览器进入 `/403` |
| 运维演示入口对单角色账号可用 | **不通过（P2）** | 受相同 `RequireAuth` 限制 |
| OPS 入口显示“运维” | **不通过（P2）** | 当前显示“教学管理” |
| 切换账号登出 | 通过 | 真实浏览器 |
| 账号受限页 L/I/D/未知 | 通过 | 6 个组件用例 |
| 受限页不提供返回登录 | 通过 | 符合用户决策 |
| 找回密码字段/强度/一致性 | 通过 | 4 个组件用例 |
| 工作台角色权限接线未破坏 | 通过 | `RequireAuth`/`AppShell` 测试 |
| 桌面布局 | 通过 | 1440×900 真实浏览器 |
| 移动端 | 不纳入本轮 | 按用户要求 |
| 真实 Oracle 测试默认可跳过 | 通过但需卫生整改 | 凭据 fallback 见 P3-S2 |
| 文档与当前角色选择流程 | 通过 | A-05/A-07/A-09/A-10 已同步 |
| A-06 任务文档与实现 | **不通过（P2）** | 文档要求“运维”，实现为“教学管理”；演示入口不可达 |
| 自动化全绿 | 通过 | 前后端各 52/52 |
| 生产构建 | 通过 | Vite build 成功 |
| 核心认证回归 | 通过 | 三角色真实接口及浏览器主链路 |

## 新增代码对既有功能的影响

- `/login`、根路径和深链入口改为统一进入 `/roles` 后，原先直接进入工作台的行为被有意替换；三角色后续仍能通过“进入工作台”正确落到各自首页，未发现路由回归。
- `40101` 结构化 `data` 没有改变错误码或 HTTP 状态；密码错误仍无账号状态，锁定账号可稳定分派到受限页。
- `TransactionTemplate` 将 last_login、SUCCESS 审计和 JWT 签发放进同一事务；故障注入测试证明签发失败会回滚，未发现正常登录回归。
- RBAC 增加时间窗口和角色状态过滤后，真实 Oracle 四类关联测试通过；既有种子账号角色与权限仍正确加载。
- `displayName` 全链路接通，API 和 UI 均返回真实姓名；为空时前端回退 username。
- 角色演示按钮没有放宽生产 `RequireAuth`，因此没有造成越权访问；代价是演示路线本身不可用，这是当前 A-06 的 Spec 缺口。
- 外部 redirect 未造成跨站跳转，但当前没有输入清理与回退，属于低风险健壮性缺口。
- 登录期间账号状态并发变化属于窄窗口，但会让服务端把拒绝请求报成 50001；修复面小，应在本阶段关闭。
- 移动端未做结论；现有桌面布局与路由改造未纳入移动端回归判断。

## 建议整改顺序

1. `P2-A06`：明确演示模式边界，使三个演示入口在真实单角色账号下可用，且不放宽生产工作台权限；将 OPS 入口统一为“运维”，补真实单角色测试。
2. `P2-S1`：使用 `Optional.ofNullable` 或显式结果类型处理二次状态检查失败，确保并发锁定/注销返回稳定 40101，并补竞态单测。
3. `P3-S1`：校验 `redirect` 只允许安全站内路径，非法值回退当前角色首页。
4. `P3-S2`：真实 Oracle 测试改为必须显式注入凭据；缺失时跳过，不内置本地凭据 fallback。
5. 发布前定位浏览器控制台中的 2 个 404 资源 URL。
6. 完成上述 P2 后执行一次定向复审，即可判断本阶段能否正式通过。

## 审查限制与如实说明

- 本轮实际使用 `code-review` 的 Standards/Spec 两轴方法，并检索了 AgentKnowledge 中 auth 相关历史 Lesson。
- 本轮尝试的两个 fresh Standards/Spec 子代理均因 `unsupported_encrypted_agent_content` 失败，未产出独立子代理结论；本报告由根审查代理基于实际 diff、代码、测试、真实 Oracle 和浏览器证据汇总。
- 本轮未修改业务代码、测试、任务文档或旧审查报告，只新增本复审报告和任务复盘资料。
- 工作区已有大量暂存/未暂存改动，本报告不改变这些改动，也不对其归属作额外推断。
- 所有测试数字均来自本轮实际执行；未执行移动端验收，也未把移动端结果计入通过或失败。
- 报告未记录密码、JWT 原文、数据库凭据或敏感用户信息。

## 整改执行记录（2026-11-02）

用户决策优先项：**删除 `/roles` 页「演示角色路线」入口及相关逻辑**，其余按本报告整改。

| 编号 | 执行结果 |
| --- | --- |
| P2-A06 | 按用户决策走报告建议的第二分支（产品不再要求可点击演示）：`RoleEntry.tsx` 删除 `DEMO_ENTRIES`、「演示角色路线」分隔线与 3 个演示按钮及 `rolePath` 引用；身份卡角色 Tag 使用的 `roleLabel.OPS` 本就是「运维」，文案偏差随演示入口删除一并消除。任务文档 A-06 验收口径同步改为「演示入口不实现（2026-11-02 用户决策），设计文档中该元素仅保留为原型记录」；`RoleEntry.test` 删除演示入口用例、新增「无演示角色入口」断言 |
| P2-S1 | `LocalAuthProvider` 对 `loadPrincipal` 返回 `null`（两次查询间账号状态并发变化）显式抛 40101「账号状态已变更，请重新登录」，不再 `Optional.of(null)` → NPE → 50001；新增 `concurrentStatusChangeReturnsStable40101` 竞态单测 |
| P3-S1 | `RoleEntry.tsx` 新增 `safeRedirect`：仅接受 `/^\/(?!\/|\\)/`（站内单斜杠、排除 `//` 与 `/\` 协议相对地址），非法值回退 `homeOf`；新增外部 URL、`//evil.example` 两个回退用例 |
| P3-S2 | `RbacLifecycleIntegrationTest` 移除内置凭据 fallback：`EDUDOC_DB_USERNAME`/`EDUDOC_DB_PASSWORD` 必须显式注入，缺失时 assumption 跳过；URL 支持 `EDUDOC_DB_URL` 覆盖 |
| 建议 5（404 资源定位） | 未执行：需部署环境浏览器复现，留待发布前处理 |

验证：`mvn test` 52/52 全绿（新增竞态单测；无凭据时 RBAC 集成测试按 assumption 干净跳过，即 P3-S2 要求的行为——其 SQL 已在上一轮以真实 Oracle 实跑验证，本轮 SQL 未再变更，需实跑时显式注入 `EDUDOC_DB_USERNAME`/`EDUDOC_DB_PASSWORD`）；`pnpm test` 53/53 全绿；`tsc --noEmit`、`pnpm build` 通过。
