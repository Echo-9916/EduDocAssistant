# 1104 文档接入（02 切片）符合性审查整改复验报告

> 复验日期：2026-11-04　复验人：Codex
> 初始审查：[1104-document-access符合性与可提交性审查报告.md](1104-document-access符合性与可提交性审查报告.md)（初始结论：有条件通过，2 项 P2 + 8 项 P3）
> 整改范围：工作区 9 个文件，`+118/-15`，基线 `87cd6ff`；后端零改动，整改集中在前端页面、契约常量与任务/测试文档
> **结论：通过。** 2 项 P2 与 5 项可操作 P3 全部关闭；3 项 P3（真库 IT、分块 offset、真实 `.doc`/断网续传）按既定口径保留；另有 1 项既有已知限制（`course.teacher_id` 兜底未实现，当前依赖课程成员表）不阻塞本切片可提交判断。

---

## 一、结论摘要

本轮整改不是只改断言：状态筛选真正接入了 `GET /documents?status=…` 的参数链路；上传弹窗新增了成功后 `reset()` + `onClose()`，并在部分失败重试时跳过已 `done` 的文件；`40501` 同时补进前端常量、契约速查与测试；B-03/B-10/DocumentTest 的口径差异也已回填。

独立复验结果：`pnpm test` **78 项全绿**（较整改前 76 增加 2 项）、`pnpm build` 通过、`mvn test` **135 项全绿**（后端零改动）、真实端到端 **21/21** 通过（30 页 PDF 1.1s 转 READY，≥5MB 分片 6.6s 转 READY）、`git diff --check` 退出码 0。

---

## 二、复验执行结果

| 项目 | 命令 | 实际结果 |
|---|---|---|
| 前端全量单测 | `web/` 下 `pnpm test -- --reporter=basic` | **17 个文件 / 78 项全部通过**（整改前 76） |
| 前端构建 | `web/` 下 `pnpm build` | `tsc --noEmit` + `vite build` **通过** |
| 后端全量单测 | `backend/` 下 `mvn test` | **135 通过 / 0 failure / 0 error / 0 skipped**（后端零改动） |
| 真实端到端 | `%TEMP%\edudoc-b13\e2e.cjs` | **21 PASS / 0 FAIL**；30 页 PDF **1.1s READY**；≥5MB multipart **6.6s READY** |
| 格式检查 | `git diff --check` | 退出码 **0**（仅 LF→CRLF 提示） |

> 说明：本轮端到端复跑时同时有两条脚本运行，两条均 `21 PASS / 0 FAIL`；上面记录的是本次主运行结果。Oracle/Redis/OSS 均为真实环境，上传使用真实 STS 临时凭据。

---

## 三、逐项关闭核对

| 项 | 整改内容 | 复验证据 | 状态 |
|---|---|---|---|
| **P2-1** 列表缺状态筛选 | `Documents.tsx` 新增「按状态筛选」Select（解析中/就绪/失败）；仅我的文档模式显示；回收站强制 `status=DELETED` 并切视图时清状态筛选；选择后 `page=1` | 代码通读确认 `status: view === 'trash' ? 'DELETED' : filters.status || undefined`；`Documents.test.tsx` 新增「状态筛选透传 status 且重置页码」；78 项全绿 | **已关闭** |
| **P2-2** 上传弹窗状态残留/成功不关窗 | `DocumentUploadModal.tsx` 新增 `reset()` 清空 `items/title/courseId/docType`；全部成功时 `reset()+onClose()`；循环跳过已 `done` 项，部分失败重试不重传已建档文件 | 代码通读确认 `remaining === items.length` 分支与 `done` 跳过；`DocumentUploadModal.test.tsx` 新增「全部成功后自动关闭并清空，重开为空」；78 项全绿 | **已关闭** |
| **P3-1** 前端 `ErrorCode` 缺 40501 | `types.ts` 补 `METHOD_NOT_ALLOWED: 40501`；`types.test.ts` 精确对象断言同步 | 78 项全绿；`types.test.ts` 断言对象含该码位 | **已关闭** |
| **P3-2** `/courses` 未回填契约速查 | `docs/design/接口契约速查.md` 新增 §5 文档域接口表（8 端点，含 `/courses`、`endpoint`、权限点、幂等键说明）；§2 错误码表补 40501 | 文档 diff 逐行核对 | **已关闭** |
| **P3-3** B-10 验收口径不一致 | 任务文档 B-10 将 `parts` 改为「不回传」（S8 决策），测试 7 例修正为实数 6 例 | 文档 diff 逐行核对；`ossUpload.test.ts` 实为 6 个 `it` | **已关闭** |
| **P3-4** DocumentTest 40501 说明过期 | 改为「已回填设计文档 §5.1 与接口契约速查」，并补注 `DELETE /courses` 不带幂等键先 40001 | 文档 diff 逐行核对 | **已关闭** |
| **P3-5** B-03 响应缺 `endpoint` | 任务文档 B-03 验收行补 `endpoint`，写明 `region`（地域名）与 `endpoint`（OSS 端点）分离及 ali-oss 误用后果 | 文档 diff 逐行核对 | **已关闭** |
| **P3-6** 真库 IT 默认 0 项 | **接受现状**：仓库暂无 CI；`[IT-SKIP]` 告警已存在，接入 CI 时用独立 profile 强制凭据 | `mvn test` 仍 135；`RbacLifecycleIntegrationTest` 仍 `Tests run: 0` 且有 `[IT-SKIP]` | **保留（已接受）** |
| **P3-7** 分块 offset 漂移/代理对 | **维持遗留**：任务文档 S10/N7 口径不变，ai 域引用定位前必须改为 code-point 安全切分并把连接符计入 offset | 后端零改动；任务文档保留该项 | **保留（已接受）** |
| **P3-8** 真实 OLE2 `.doc`/断网续传 | **遗留待用户样本/交互**：真实 `.doc` 全链路、Chrome DevTools offline 续传 | 任务文档已记录；本轮未构造真实 OLE2 样本 | **保留（待人工）** |

---

## 四、整改代码审阅

### 4.1 状态筛选

`Documents.tsx` 的筛选参数现在是：

```ts
status: view === 'trash' ? 'DELETED' : filters.status || undefined
```

- 我的文档模式：可选 `PARSING/READY/FAILED`，未选时不传 `status`（后端默认排除 DELETED）。
- 回收站模式：强制 `DELETED`，且切换视图时清空 `filters.status`，不会把 `READY` 等状态带进回收站。
- 每次状态变更都 `setPage(1)`，避免停留在旧页码。
- `params` 的 `useMemo` 依赖包含 `filters`，会触发重新请求；轮询也使用同一 `params`。

### 4.2 上传弹窗

- `reset()` 清空四个表单状态；只有 `remaining === items.length`（全部成功或原本已完成）时才关窗清空。
- 部分失败时保留失败项与表单值，符合"失败保留输入"；再次提交时已 `done` 项被跳过，避免重复建档撞 `40901`。
- 成功路径先 `onUploaded()` 刷新列表，再 `reset()+onClose()`，顺序合理。

### 4.3 契约与文档

- `ErrorCode` 现在与后端 `ErrorCode` 枚举一致（含 40501）。
- `接口契约速查` 的文档域接口表覆盖 `/courses`、上传会话、complete、列表、详情、删除、恢复、重解析，并明确 `endpoint` 直传口径。
- B-03/B-10/DocumentTest 的口径已与代码、S8 决策一致。

---

## 五、残留风险与已知限制

1. **`course.teacher_id` 兜底未实现（既有，非本轮整改引入）**：`CourseMapper.selectCoursesByUser` 只查 `edudoc_course_member`；任务文档 B-03 写的是"TEACHER/ASSISTANT 成员 **或** course.teacher_id"。若某课程只有 `teacher_id` 而无成员行，该教师上传会被 40301。种子数据中教师同时有成员行，因此本切片 e2e 不受影响；课程管理模块落地时应补该兜底并补测试。
2. **真库 IT 默认 0 项**：`[IT-SKIP]` 已保证不静默，但默认 `mvn test` 仍不覆盖真库 RBAC 路径。
3. **分块 offset 债务**：S10/N7 未变，ai 域使用 `char_start/char_end` 做引用定位前必须修复。
4. **真实 OLE2 `.doc` 与断网续传**：仍需用户样本与浏览器交互验证。
5. **未做浏览器截图/视觉 QA**：本轮以 78 项单测、`pnpm build` 和代码通读为准；建议提交前用实际浏览器过一遍状态筛选、上传成功自动关窗、部分失败重试三条路径。
6. **多实例并发**：Outbox 抢投与 consumer 撞名仍按 `// ponytail` 单机假设，未压测。

---

## 六、放行判断

**通过（整改有效）。**

- 初始审查的 2 项 P2 均已关闭，且不是仅改测试：状态筛选、弹窗重置/自动关闭都落在实际组件逻辑上。
- 5 项可操作 P3 已关闭；3 项 P3 按既定口径保留，且均有明确后续触发条件。
- 自动化、构建、真实端到端三类证据齐全，未发现新增回归。

建议提交信息（留给用户确认）：

- `fix(web): 文档列表补状态筛选，上传弹窗成功后关闭并清理状态`
- `fix(web): 补齐 40501 错误码常量与契约测试`
- `docs: 回填 courses 契约与 B-03/B-10/40501 口径`
- `docs(review): 归档 1104 文档接入审查与整改复验报告`

---

## 七、复验限制说明

- 运行实例为外部启动（PID 62100）；本轮仍以 HTTP、Oracle、Redis/OSS 的实际结果为准，未读取其控制台日志。
- 未修改任何业务代码；仅复验工作区整改与新增审查文档。
- 本轮 e2e 复验同时有两条脚本运行，两条均 21/21，未出现共享 Redis Stream/数据库冲突。
- 真实 OLE2 `.doc`、DevTools 断网续传、多实例并发未覆盖，已在上方残留风险中列明。
