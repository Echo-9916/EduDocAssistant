# foundation 阶段代码审查与修改报告

> 报告日期：2026-10-31（Asia/Shanghai）
> 审查基线：`main`（`5974f5b`）与 `feat/foundation`（`fdffed9`）的合并基
> 审查范围：14 个提交，76 个文件，+4550 / -109 行
> 审查方法：code-review 双轴审查（Standards 标准轴 + Spec 规格轴）+ 可执行验证

## 一、总体结论

foundation 阶段当前**不能整体判定为过关**。8 项任务中 3 项通过、4 项部分通过、1 项未达标。

| 结论 | 数量 | 任务 |
| --- | --- | --- |
| 通过 | 3 | F-01、F-02、F-05 |
| 部分通过 | 4 | F-03、F-06、F-07、F-08 |
| 未达标 | 1 | F-04 |

阻断项是 **F-04 RBAC 鉴权框架**：`@RequirePermission` 注解与 `AccessGuard` 切面完全缺失，鉴权顺序只实现到「账号状态」，CLAUDE.md 的鉴权红线未闭环。

## 二、可执行验证结果

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端单元测试 | `mvn test` | 11 个测试类、29 个测试全部通过 |
| 前端单元测试 | `pnpm test` | 7 个测试文件、23 个测试全部通过 |
| 前端构建 | `pnpm build` | `tsc --noEmit` 与 `vite build` 成功 |
| 构建警告 | - | 产物约 1.04 MB，antd 未做代码分割，仅警告 |

测试全绿不能掩盖验收缺口：F-04 的鉴权注解与切面不存在，对应的真实鉴权路径测试自然也没有，现有 403 测试只覆盖 `RestAccessDeniedHandler` 的响应映射。

## 三、规格轴逐项检查（依据 00-foundation.md 验收标准）

| 任务 | 结论 | 达标项 | 缺口 |
| --- | --- | --- | --- |
| F-01 后端契约 | 通过 | envelope 含 traceId/timestamp、成功 code=200；错误码 19 项（40001~50401）与 HTTP 映射正确；`PageResult`、`CidasBusinessException`、`@RestControllerAdvice` 就位；`/api/smoke` 返回标准 envelope | 无 |
| F-02 前端类型 | 通过 | `ApiResponse/ErrorCode/PageResult/UserPrincipal` 与后端 DTO 一致；错误码 19、权限 20、角色 3、账号状态 4 | 无 |
| F-03 认证 | 部分通过 | 登录/me/登出、BCrypt 校验、账号状态校验、`AuthProvider` 预留 SSO 扩展点 | JWT 只签 `sub`/`username`，缺 roles claim；logout 服务端空实现，撤销（40102）未落地 |
| F-04 RBAC | 未达标 | `UserPrincipal` 装配角色/权限点；`@EnableMethodSecurity` 开启；401/403 统一 envelope | 无 `@RequirePermission`/`AccessGuard`，无任何权限点校验；鉴权顺序缺「角色、课程范围」两步；courseScope 恒为空 |
| F-05 认证流程 | 通过 | 登录校验与密码可见、token 存取策略、守卫未登录回跳、401 保留回跳、403 统一无权限态、按角色拦截 | 无 |
| F-06 布局 | 部分通过 | 应用壳、三角色路由骨架、8/9/7 导航项、加载与空态占位 | 集中式路由表缺「所需权限」接线，权限只用于菜单过滤 |
| F-07 请求封装 | 部分通过 | Bearer 与 `X-Trace-Id` 自动附加、超时、错误映射、5xx 带 traceId | 幂等键只有生成函数，从不附加到请求，无重试复用 |
| F-08 组件库 | 部分通过 | antd 接入、全局主题令牌、demo 页跑通 | 按钮/表单/表格/弹窗/分页统一封装缺失，无 components 目录 |

## 四、标准轴发现（依据 CLAUDE.md 工程约定与架构红线）

### 硬违规

| 编号 | 位置 | 问题 | 对应约定 |
| --- | --- | --- | --- |
| S-1 | `backend/common/security/SecurityConfig.java:26-27` | 仅 `anyRequest().authenticated()`，角色/权限装配后无校验点，`@EnableMethodSecurity` 空挂 | 鉴权顺序固定：认证 → 账号状态 → 角色 → 课程范围 → ACL → 动作约束 |
| S-2 | `web/src/api/http.ts:89` 与后端主代码 | `Idempotency-Key` 只生成不附加不复用，后端写接口不接收校验 | 写接口接收并校验 `Idempotency-Key` |

### 判断性发现

| 编号 | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| S-3 | `common/security/JwtAuthenticationFilter` | 横切层直接依赖 `access.infrastructure.UserAccountRepository` | 在 common 定义 PrincipalLoader 端口，由 access 实现 |
| S-4 | `LocalAuthProvider.java:21`、`UserAccountRepository.java:16` | `STATUS_ACTIVE = "A"` 等裸字符串重复定义 | 收敛为 AccountStatus/AuthSource 常量或枚举 |
 |
| S-6 | `web/src/api/http.ts` 与 `auth/RequireAuth.tsx` | `login?redirect=` 拼接逻辑重复 | 提取公共函数 |

## 五、修改建议清单

| 编号 | 优先级 | 修改内容 | 涉及文件 | 验收标准 |
| --- | --- | --- | --- | --- |
| M-1 | P0 | 补 F-04：实现 `@RequirePermission` 注解与 `AccessGuard` 切面，按固定顺序执行认证→账号状态→角色→课程范围校验，接入 SecurityConfig；courseScope 本阶段先预留加载接口与占位校验 | `backend/common/security`、`access` | 无令牌→40101、无权限→40301、账号锁定→拒绝，单元测试覆盖；全仓库权限注解可真实生效 |
| M-2 | P1 | 补幂等键闭环：前端写请求自动附加 `Idempotency-Key` 并支持重试复用；后端写接口校验并处理 40902 冲突 | `web/src/api/http.ts`、后端 api 层 | F-07 验收文字「生成与重试复用封装」落地 |
| M-3 | P1 | JWT roles claim：补签 roles，或更新 00-foundation.md 验收口径并保留 40102 撤销方案说明 | `backend/common/security/JwtService.java`、`docs/tasks/00-foundation.md` | F-03 验收与实现一致，无未说明的偏差 |
| M-4 | P2 | 建 `web/src/components`，统一封装按钮/表单（校验）/表格（分页筛选）/弹窗/分页 | `web/src/components`、`pages/Demo.tsx` | F-08 验收「提供统一封装」落地，demo 改用封装 |
| M-5 | P2 | 路由表补「所需权限」字段并接到路由守卫 | `web/src/routes.ts`、`App.tsx`、`RequireAuth.tsx` | F-06 验收「路径→页面组件→所需权限」落地 |
| M-6 | P3 | common 与业务模块解耦：PrincipalLoader 端口 | `backend/common/security`、`access/infrastructure` | common 不依赖 access |
| M-7 | P3 | 常量收敛与配置安全：AccountStatus 枚举 | `access`、`application.yml` | 无裸字符串重复 |
| M-8 | P3 | 前端产物代码分割（antd 分包或路由懒加载） | `web/vite.config.ts`、`App.tsx` | 消除 500 kB 分块警告 |

