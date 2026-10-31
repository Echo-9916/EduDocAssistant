# CLAUDE.md

EduDocAssistant（CIDAS，校园智能文档辅助系统）的实现工作区。`backend/`（Java 21 + Spring Boot 3）、`web/`（React 18 + Vite）、`ai-service/`（Python 3.13 + FastAPI）三服务骨架已搭建并通过端到端冒烟测试。本文件约束编码阶段的约定，需求、设计与页面的**事实**以 `docs/` 为准。

## 先看哪里

| 要做的事                                  | 读                                                                   |
| ----------------------------------------- | -------------------------------------------------------------------- |
| 确认需求条目（REQ-xx / NFR-xx）与验收口径 | `docs/requirements/需求规格说明书.md`                              |
| 改分层、分包、接口、异步流程              | `docs/design/概要设计说明书.md`、`docs/design/详细设计说明书.md` |
| 建表、改字段、写 SQL                      | `docs/design/数据库设计说明书.md`                                  |
| 对齐页面行为与 81 屏                      | `docs/prototype/screens/{student,teacher,operations}.md`           |
| 查原型需求覆盖                            | `docs/requirements/原型需求合规检查.md`                            |

`docs/` 是仓库上一级 `02_需求规格说明/`、`05_系统设计/`、`03_页面原型/` 的复刻：需求、设计、原型改动时，Word/HTML 源文件与 `docs/` 同步更新，源文件为准；`docs/prototype/{student,staff}/README.md` 是本地改写版，同步时保留。

## 代码布局

| 目录            | 技术栈                           | 职责                                           |
| --------------- | -------------------------------- | ---------------------------------------------- |
| `backend/`    | Java 21、Spring Boot 3、Maven    | 模块化单体：业务事务、REST、鉴权、审计、Outbox |
| `web/`        | React 18、TypeScript、Vite、pnpm | 学生、教师、运维三端界面                       |
| `ai-service/` | Python 3.13、FastAPI             | 摘要、要点、问答、润色、向量化                 |
| `gateway/`    | Node.js 22、Yjs                  | 协作 WebSocket 网关；做实时协作时再建          |
| `deploy/`     | Nginx、systemd                   | 单机部署配置；做可部署版本时再建               |

`backend/` 按六个业务域分包：`document`、`ai`、`template`、`collaboration`、`access`、`export`；每个域内含 `api` / `application` / `domain` / `infrastructure` 四层。横切能力（统一响应、错误码、trace、事务模板）放 `common`，不携带业务状态。

## 架构红线

- 调用顺序固定：Controller → ApplicationService → Domain/Repository。Controller 只做参数校验与响应封装，Mapper、OSS 客户端只出现在 infrastructure。
- 请求线程只做编排：解析、向量化、格式转换、导出、通知走 Redis Stream；业务事件通过 `edudoc_outbox_event` 与业务事务同库提交。
- 数据表统一 `edudoc_` 前缀、`id RAW(16)`、`create_time` / `update_time`。建库顺序：`01_schema.sql → 02_indexes.sql → 03_triggers.sql → 04_seed.sql`（位于上一级 `05_系统设计/数据库脚本/`）。
- 鉴权顺序固定：认证 → 账号状态 → 角色 → 课程范围 → 文档 ACL/共享链接 → 动作约束。
- 版本不可变：上传、编辑、协作、恢复都生成新版本，历史版本只读。
- 写接口接收并校验 `Idempotency-Key`。
- 文件访问只经私有 OSS Bucket + 短期 STS/签名 URL，浏览器不持有长期云凭据。

## 工程约定

- 交付物、注释、文档、提交信息用简体中文；表名、接口名、错误码、术语沿用设计文档。
- 提交信息沿用 `type(scope): 说明`，scope 取 `backend` / `web` / `ai` / `infra` / `docs`，例如 `feat(backend): 文档上传会话`、`fix(web): 登录态失效跳转`。
- 后端数据访问统一 MyBatis-Plus，Java 业务代码不写 SQL；必要的 SQL 落在 mapper.xml。
- 后端依赖注入统一构造器注入（Lombok `@RequiredArgsConstructor`），不手写 `this.x = x` 构造器、不用字段注入。
- 连续多行 get/set 改用流式/链式写法（`Optional.filter`/`map`/Builder）。
- 后端 Service 先定义接口，再写 `*Impl` 实现。
- 改动在对应服务目录内用该服务的构建与测试命令验证（`mvn test`、`pnpm test`、`pytest`）；
- 测试通过后总结任务内容,并编写提交message留给用户确认。

## Git 分支

`main` 始终可交付。所有改动走短生命周期分支，合并回 `main` 后删除；从最新 `main` 拉出，合并前 rebase。

| 分支                   | 用途             | 示例                             |
| ---------------------- | ---------------- | -------------------------------- |
| `main`               | 可运行基线       | —                               |
| `feat/<area>-<slug>` | 新功能           | `feat/backend-document-upload` |
| `fix/<area>-<slug>`  | 缺陷修复         | `fix/web-login-redirect`       |
| `docs/<slug>`        | 需求、设计、原型 | `docs/db-index-review`         |

- `<area>` 取 `backend` / `web` / `ai` / `infra` / `fullstack`（跨端联调）。
- 一个分支只做一件可验收的事；同一时刻只开与当前任务相关的分支。
- 三个服务并行且 `main` 频繁无法通过验证时，再加 `develop` 集成分支；当前不设。
