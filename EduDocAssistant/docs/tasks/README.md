# 任务文档

## 阶段1 契约与基础设施（foundation）

- [00-foundation.md](00-foundation.md) —— 共享类型与 API 契约、认证与三角色 RBAC、通用布局/路由守卫/请求封装/错误处理、基础组件库（Ant Design）。

## 阶段2 垂直切片（vertical slice）

- [01-fullstack-auth.md](01-fullstack-auth.md) —— 登录切片：认证种子数据、登录/登出/令牌撤销、登录页/角色入口/账号受限/找回密码/权限申请、端到端联调。
- [02-document-access.md](02-document-access.md) —— 文档接入切片：真实 OSS STS 直传（含断点续传）、Outbox + Redis Stream 异步解析（Tika 分块）、我的文档列表/详情/回收站、学生与教师双端页面、端到端联调。
