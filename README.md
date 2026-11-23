# CIDAS 校园智能文档辅助系统（EduDocAssistant）

面向高校师生的智能文档协作平台：文档上传与结构化阅读、课程模板与格式校验、AI 摘要 / 要点 / 问答 / 润色、作业发布 / 提交 / 批改、协作批注与共享链接。

本仓库同时包含需求、原型、系统设计等课程交付物，以及可运行的三服务实现（`EduDocAssistant/`）。

## 技术栈

| 服务 | 技术 | 端口 | 职责 |
| --- | --- | --- | --- |
| `backend` | Java 21 · Spring Boot 3 · MyBatis-Plus · Maven | 8080 | 业务事务、REST、鉴权、审计、Outbox、异步 Worker |
| `web` | React 18 · TypeScript · Vite · Ant Design · pnpm | 5173 | 学生 / 教师 / 运维三端界面 |
| `ai-service` | Python 3.13 · FastAPI · uv | 8000 | 摘要、要点、问答、润色、向量化、重排、格式转换 |
| 基础设施 | Oracle（`FREEPDB1`）· Redis | 1521 / 6379 | 业务数据、向量索引、任务 Stream |

调用链：`web → backend → (Oracle / Redis Stream / ai-service)`。格式校验、解析、向量化、AI 任务、通知、转换均通过 Redis Stream 异步执行。

## 目录结构

```
SoftwareProjectImpl/
├── 01_需求调研/                 # 调研材料
├── 02_需求规格说明/             # 需求陈述 / 需求规格（Word 源文件）
├── 03_页面原型/                 # 81 屏高保真原型
├── 04_演示汇报/                 # 演示汇报材料
├── 05_系统设计/                 # 概要 / 详细 / 数据库设计 + 数据库脚本
│   └── 数据库脚本/              # 01_schema → 02_indexes → 03_triggers → 04_seed
├── EduDocAssistant/
│   ├── backend/                 # Spring Boot 模块化单体（七个业务域）
│   ├── web/                     # React 三端前端
│   ├── ai-service/              # FastAPI 智能处理服务
│   ├── docs/                    # 需求 / 设计 / 原型 / 任务 / 测试 / 审查文档（Markdown 复刻）
│   ├── scripts/                 # 启动脚本、演示数据生成与复位
│   └── CLAUDE.md                # 编码约定、架构红线、踩坑清单
└── README.md
```

## 功能概览

- **学生端**：我的文档、结构化预览、按模板新建/块编辑（标题、正文、代码块、图片）、格式校验与一键修复、AI 摘要/要点/问答/润色、作业提交（支持作业附件下载与「基于附件编辑」）、消息通知、共享与批注。
- **教师端**：课程与成员导入、课程模板与规则版本发布、作业发布（模板 + `.docx` 附件 + 评分标准）、提交统计、批改与反馈（反馈选填）、教学工作台。
- **运维端**：用户目录与账号冻结/解冻、审计日志、存储与工作台 KPI。
- **协作**：文档协作者、批注、在线状态、版本历史与恢复、免登录只读共享链接（按结构层渲染正文与图片）。

## Docker 一键部署（推荐）

已提供全量 compose：Oracle + Redis + backend + ai-service + Nginx Web，默认只暴露 `8088`。

```bash
cp deploy/.env.example deploy/.env
# 按需填写阿里云 OSS 凭据与 SILICONFLOW_API_KEY
./deploy/start.sh          # Windows: .\deploy\start.ps1
```

启动后访问 `http://localhost:8088`，演示账号 `teacher / student / ops`，密码 `Cidas@123`。
详细说明（Oracle 首次初始化、OSS CORS、日志、重置、排错）见 [deploy/README.md](deploy/README.md)。

## 本地开发（源码方式）

### 0. 前置依赖

- JDK 21、Maven 3.9+
- Node.js 22 + pnpm
- Python 3.13 + [uv](https://docs.astral.sh/uv/)
- Docker，且已有 `oracle-dev`（1521）与 `redis-dev`（6379）容器
- OSS 凭据（上传文档、作业附件、文档图片时需要）

### 1. 启动基础设施

```bash
docker start oracle-dev redis-dev
docker ps   # 两个容器均为 Up
```

### 2. 初始化数据库（首次）

在仓库根目录按顺序执行（通过 `oracle-dev` 容器内的 sqlplus）：

```bash
for f in 01_schema 02_indexes 03_triggers 04_seed; do
  docker exec -i oracle-dev sqlplus -s user_cidas/cidas123@//localhost:1521/FREEPDB1 \
    < "05_系统设计/数据库脚本/${f}.sql"
done
```

也可以使用任意 Oracle 客户端连接 `localhost:1521/FREEPDB1`（`user_cidas` / `cidas123`）后依次执行四个脚本。

演示账号由后端启动种子写入（`backend/src/main/resources/db_seed/seed_accounts.csv`），首次启动 backend 后可用。

### 3. 配置环境变量

| 变量 | 服务 | 说明 |
| --- | --- | --- |
| `EDUDOC_DB_USERNAME` / `EDUDOC_DB_PASSWORD` | backend | 默认 `user_cidas` / `cidas123` |
| `EDUDOC_OSS_ACCESS_KEY_ID` / `EDUDOC_OSS_ACCESS_KEY_SECRET` | backend | 必填，否则上传/附件/图片接口 503 |
| `EDUDOC_AI_INTERNAL_TOKEN` | backend + ai-service | 服务间共享密钥，两边一致 |
| `EDUDOC_JWT_SECRET` | backend | 生产环境必须覆盖，长度 ≥32 字节 |
| `SILICONFLOW_API_KEY` | ai-service | 模型凭据，缺失时 AI 调用返回结构化错误 |

完整变量见 [backend/src/main/resources/application.yml](EduDocAssistant/backend/src/main/resources/application.yml) 与 [ai-service/README.md](EduDocAssistant/ai-service/README.md)。

### 4. 启动三服务

```bash
# 1) AI 服务（8000）
cd EduDocAssistant/ai-service
uv sync
uvicorn app.main:app --host 127.0.0.1 --port 8000

# 2) 后端（8080）
cd EduDocAssistant/backend
mvn spring-boot:run

# 3) 前端（5173）
cd EduDocAssistant/web
pnpm install
pnpm dev
```

Windows 也可直接运行 `EduDocAssistant/scripts/start-backend.bat` 与 `start-ai-service.bat`。

成功标志：`http://127.0.0.1:8000/health` 返回 `"status":"UP"`；后端日志出现 `Started EduDocApplication`；浏览器可打开 `http://localhost:5173/login`。

### 5. 演示账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 教师 | `teacher` | `Cidas@123` |
| 学生 | `student` | `Cidas@123` |
| 运维 | `ops` | `Cidas@123` |

从 0 到 1 的完整演示流程、演示文件、检查清单与常见问题见
[演示操作手册](EduDocAssistant/docs/demo/演示操作手册.md)。

## 测试

```bash
# 后端
cd EduDocAssistant/backend && mvn test

# 前端
cd EduDocAssistant/web && pnpm test && npx tsc --noEmit

# AI 服务
cd EduDocAssistant/ai-service && python -m pytest
```

> 后端全量测试依赖测试库配置；前端测试使用 Vitest + Testing Library；AI 服务测试使用 pytest。

## 文档导航

| 内容 | 位置 |
| --- | --- |
| 需求规格（Word 源文件） | [02_需求规格说明/需求规格说明书.docx](02_需求规格说明/需求规格说明书.docx) |
| 需求规格（Markdown） | [docs/requirements/需求规格说明书.md](EduDocAssistant/docs/requirements/需求规格说明书.md) |
| 概要设计 | [docs/design/概要设计说明书.md](EduDocAssistant/docs/design/概要设计说明书.md) |
| 详细设计 | [docs/design/详细设计说明书.md](EduDocAssistant/docs/design/详细设计说明书.md) |
| 数据库设计 | [docs/design/数据库设计说明书.md](EduDocAssistant/docs/design/数据库设计说明书.md) |
| 页面原型 | [03_页面原型/](03_页面原型/) 与 [docs/prototype/](EduDocAssistant/docs/prototype/) |
| 编码约定与架构红线 | [EduDocAssistant/CLAUDE.md](EduDocAssistant/CLAUDE.md) |
| 用户操作手册 | [总览](EduDocAssistant/docs/user/用户操作手册.md) · [学生版](EduDocAssistant/docs/user/学生用户操作手册.md) · [教师版](EduDocAssistant/docs/user/教师用户操作手册.md) · [运维版](EduDocAssistant/docs/user/运维用户操作手册.md) |
| 演示操作手册 | [docs/demo/演示操作手册.md](EduDocAssistant/docs/demo/演示操作手册.md) |
| Docker 部署说明 | [deploy/README.md](deploy/README.md) |
| 审查与测试报告 | [docs/review/](EduDocAssistant/docs/review/) |

## 演示数据复位

演示结束后在 `EduDocAssistant/` 下执行：

```powershell
.\scripts\demo-reset.ps1
```

复位会清理作业、提交、消息与演示文档的新版本；不会删除新建课程、课程成员，也不会自动清理作业附件与文档图片（必要时按手册中的 SQL 手动清理）。

## 约定

- 交付物、注释、文档、提交信息使用简体中文。
- 分支与提交规范见 [EduDocAssistant/CLAUDE.md](EduDocAssistant/CLAUDE.md)：`feat/<area>-<slug>`、`fix/<area>-<slug>`，提交信息形如 `feat(backend): …`。
- 数据库脚本按 `01_schema → 02_indexes → 03_triggers → 04_seed` 顺序执行。