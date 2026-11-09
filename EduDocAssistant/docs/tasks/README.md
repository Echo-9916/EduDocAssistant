# 任务文档

## 阶段1 契约与基础设施（foundation）

- [00-foundation.md](00-foundation.md) —— 共享类型与 API 契约、认证与三角色 RBAC、通用布局/路由守卫/请求封装/错误处理、基础组件库（Ant Design）。

## 阶段2 垂直切片（vertical slice）

- [01-fullstack-auth.md](01-fullstack-auth.md) —— 登录切片：认证种子数据、登录/登出/令牌撤销、登录页/角色入口/账号受限/找回密码/权限申请、端到端联调。
- [02-document-access.md](02-document-access.md) —— 文档接入切片：真实 OSS STS 直传（含断点续传）、Outbox + Redis Stream 异步解析（Tika 分块）、我的文档列表/详情/回收站、学生与教师双端页面、端到端联调。
- [03-ai-processing.md](03-ai-processing.md) —— 智能处理切片：Oracle 向量检索与分块向量化、摘要/要点异步任务、问答（证据阈值+拒答）、润色采纳成版、ai-service 模型适配层（OpenAI 兼容）、预览文本阅读视图与三类面板、端到端联调。
- [04-template-format.md](04-template-format.md) —— 格式与模板切片：课程模板版本与规则解析发布、文档结构树、格式校验任务与问题清单、一键修复成版与复审、Word/PDF 格式转换、按模板新建、教师/学生模板页与校验/修复/转换面板、端到端联调（J1–J4 已于 2026-11-06 按推荐口径拍板）。

## 阶段3 门面先行（策略调整，2026-11-09）

- [05-web-full-presentation.md](05-web-full-presentation.md) —— 前端全量呈现：三端 81 屏逐屏可达（已联调分区保持真实接口，其余分区用 mock 骨架页 + 页头屏号与「演示数据」标记）、三端主题接线、演示口径与已知限制、后端与 ai-service 跟进清单。
