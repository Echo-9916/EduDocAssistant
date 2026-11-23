# Docker 一键部署

本目录提供 CIDAS / EduDocAssistant 的全量本地容器化部署：

```
浏览器 → web(Nginx :8088) → backend(:8080) → oracle(:1521)
                                            ├─ redis(:6379)
                                            └─ ai-service(:8000)
                        外部依赖：阿里云 OSS、硅基流动模型 API
```

默认只暴露 Web 端口 `8088`；Oracle、Redis、backend、AI 服务均在 `edudoc-net` 内部网络，不占用宿主机 1521/6379/8080。

## 1. 前置条件

- Docker Desktop（或 Docker Engine + Compose v2）
- 建议内存 ≥ 6 GB、磁盘 ≥ 15 GB
- 首次拉取 Oracle 镜像需要登录 Oracle 容器仓库（免费账号）：

```bash
docker login container-registry.oracle.com
```

> 如果本机已有 `container-registry.oracle.com/database/free:latest`（例如现有 `oracle-dev` 使用的镜像），可跳过登录与拉取。

## 2. 配置环境变量

```bash
cp deploy/.env.example deploy/.env
```

至少填写以下项：

| 变量 | 说明 |
| --- | --- |
| `EDUDOC_OSS_ACCESS_KEY_ID` / `EDUDOC_OSS_ACCESS_KEY_SECRET` | 阿里云 OSS 凭据；不填则上传、作业附件、文档图片接口返回 503 |
| `EDUDOC_OSS_BUCKET` / `EDUDOC_OSS_ROLE_ARN` | 与现有开发环境一致时可保持默认；不同账号需替换 |
| `SILICONFLOW_API_KEY` | 硅基流动模型密钥；不填则 AI 摘要/要点/问答/润色失败 |
| `EDUDOC_WEB_PORT` | Web 对外端口，默认 `8088`，被占用时改这里 |
| `EDUDOC_ORACLE_PWD` | Oracle SYSDBA 密码；**首次启动后修改需重建数据卷** |
| `EDUDOC_DB_PASSWORD` | 业务库 `user_cidas` 密码，默认 `cidas123` |

`EDUDOC_JWT_SECRET` 与 `EDUDOC_AI_INTERNAL_TOKEN` 留空即可，启动脚本会自动生成。

### 阿里云 OSS CORS

浏览器直传 OSS 需要给 Bucket 配置 CORS，至少允许：

- 来源：`http://localhost:8088`（或实际部署的域名/端口）
- 方法：`GET, PUT, POST, HEAD`
- 请求头：`*`
- 暴露头：`ETag`

否则上传会在浏览器端跨域失败。

## 3. 一键启动

Windows PowerShell：

```powershell
.\deploy\start.ps1
```

Linux / macOS / Git Bash：

```bash
chmod +x deploy/start.sh
./deploy/start.sh
```

脚本会：

1. 检查 Docker / Compose；
2. 复制 `.env.example` 为 `.env`（不存在时）；
3. 自动生成 JWT 密钥与服务间 Token；
4. `docker compose --env-file deploy/.env up -d --build`；
5. 等待 Web 就绪并打印访问地址与演示账号。

首次启动需要初始化 Oracle 数据库，通常 5–10 分钟；后续启动约 1 分钟。

## 4. 访问与账号

- Web：`http://localhost:8088`
- 教师：`teacher / Cidas@123`
- 学生：`student / Cidas@123`
- 运维：`ops / Cidas@123`

演示流程见 [演示操作手册](../EduDocAssistant/docs/demo/演示操作手册.md)。注意：容器部署入口是 `8088`，不是开发模式的 `5173`；后端/AI 端口不对外暴露。

## 5. 日常运维

```bash
# 状态
docker compose --env-file deploy/.env ps

# 日志（全部 / 单个服务）
docker compose --env-file deploy/.env logs -f
docker compose --env-file deploy/.env logs -f backend
docker compose --env-file deploy/.env logs -f db-init

# 停止（保留数据卷）
./deploy/stop.sh          # Windows: .\deploy\stop.ps1

# 重启
docker compose --env-file deploy/.env restart

# 重新构建应用镜像
docker compose --env-file deploy/.env up -d --build backend web ai-service
```

数据卷：

- `edudoc_oracle_data`：Oracle 数据文件
- `edudoc_redis_data`：Redis AOF

`db-init` 每次启动都会执行，但检测到 `EDUDOC_USER` 已存在会直接跳过，因此重复 `up` 是幂等的。

## 6. 重置 / 清空

```bash
# 停止并删除容器 + 数据卷（下次启动会重新建库）
docker compose --env-file deploy/.env down -v
```

> `down -v` 会删除 Oracle 与 Redis 数据，请确认不需要保留演示数据。

## 7. 常见问题

| 现象 | 处理 |
| --- | --- |
| Oracle 启动很慢 / `db-init` 一直等 | 首次建库正常，`docker compose logs -f oracle` 观察；内存不足时先关闭其他容器 |
| `db-init` 失败 | 查看 `logs db-init`；若 schema 只建了一半，`down -v` 后重来 |
| 上传文档 / 附件 / 图片 503 | 检查 OSS 凭据、Bucket、STS 角色；后端日志会给出“OSS 未配置”类提示 |
| 浏览器上传跨域失败 | 检查 Bucket CORS 是否包含 `http://<host>:8088` 与 PUT/POST 头 |
| AI 接口 502 / MODEL_FAILED | 检查 `SILICONFLOW_API_KEY`、模型名与账户额度 |
| 8088 被占用 | 修改 `deploy/.env` 的 `EDUDOC_WEB_PORT` 后重新 `up -d` |
| 修改了 `EDUDOC_ORACLE_PWD` 后连不上 | 已有数据卷不会自动改 SYSDBA 密码；需 `down -v` 重建，或手动改密码 |
| 想调试后端/AI 端口 | 临时加 `-p 8080:8080` / `-p 8000:8000`，或使用 `docker compose exec` |

### 向量索引说明

Oracle Free 容器默认 `vector_memory_size=0`，首次初始化会跳过 `02_indexes.sql` 中的向量索引（日志提示 `ORA-51962`）。这不影响演示与普通检索，向量查询会走精确检索。数据量较大时可由管理员开启：

```sql
-- 以 SYSDBA 连接 CDB 后执行
ALTER SYSTEM SET vector_memory_size=128M SCOPE=SPFILE;
-- 重启 oracle 容器，再执行 02_indexes.sql 中的 CREATE VECTOR INDEX 语句
```

## 8. 生产环境提示

- 本方案面向本机/内网演示，默认 HTTP。对外部署请在 Web 前再加 TLS 反向代理。
- 不要提交 `deploy/.env`（已被 `*.env` 忽略）；生产环境建议改用 Docker Secret 或外部密钥管理。
- 首次演示前可在应用内按《演示操作手册》走一遍模板、作业附件、格式校验、图片与批改消息闭环。