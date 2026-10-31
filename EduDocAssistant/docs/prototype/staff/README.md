# 教师与运维端 v3

教师端与运维端第三版原型，共 54 屏。视觉规范、字号、控件密度和校验方式与学生端 v3 保持一致；页面内状态可以操作，页面之间不设置跳转。

2026-10-30 起，81 个路线画面已按登录页规范完成图层归组与命名：每个画面统一为“总画布 / 侧边栏 / 内容区”三层，命名与分组规则见 [../figma/NORMALIZE_DESIGN.md](../figma/NORMALIZE_DESIGN.md)；需求覆盖核对见 [../figma/figma-requirements-audit-2026-10-30.md](../figma/figma-requirements-audit-2026-10-30.md)。

## 访问方式

在 `staff_routes_v3` 目录启动服务器：

```powershell
$env:PORT=4180
node .\capture-server.mjs
```

- 路线入口：`http://127.0.0.1:4180/`
- 教师端：`http://127.0.0.1:4180/teacher.html#workbench`
- 运维端：`http://127.0.0.1:4180/operations.html#workbench`
- 教师总览：`http://127.0.0.1:4180/board.html?role=teacher`
- 运维总览：`http://127.0.0.1:4180/board.html?role=operations`

## Figma 交付入口

- 教师端 27 屏总览：https://www.figma.com/design/sVEDsRFiVY022tTAd9u5cG?node-id=228-3
- 教师端工作台：https://www.figma.com/design/sVEDsRFiVY022tTAd9u5cG?node-id=228-4
- 运维端 27 屏总览：https://www.figma.com/design/sVEDsRFiVY022tTAd9u5cG?node-id=228-32
- 运维端工作台：https://www.figma.com/design/sVEDsRFiVY022tTAd9u5cG?node-id=228-33

Figma 中教师端和运维端各自位于独立页面，每页包含 1 张原生总览和 27 个完整业务 Frame。画面内的文字、卡片、线条、表格、按钮和 Lucide 图标均为可编辑 Figma 节点，不使用整屏截图作为画面填充；只有真实 Logo 保留图片填充。

学生端 27 屏也在主页面中完成同样的原生对象转换。三条路线合计 81 个业务 Frame、4907 个文本图层、5247 个矩形图层和 4904 个矢量图层，整屏截图填充为 0。

为便于当前只审阅学生端，学生总览放在主页面 `x=0, y=0`，27 个画面按 5 列排列在总览下方；Figma 最终视图定位到学生总览，缩放约 40%。

## 教师端 v3

一级导航共 9 项：

1. 工作台
2. 我的文档
3. 智能处理
4. 格式与模板
5. 课程模板
6. 协作与版本
7. 课程与作业
8. 消息通知
9. 个人中心

27 屏覆盖教学总览、教学文档、智能处理、模板建设、格式校验、协作版本、课程治理、作业发布、批改反馈、消息与个人资料。

## 运维端 v3

一级导航共 7 项：

1. 运维工作台
2. 课程治理
3. 用户与权限
4. 审计与安全
5. 存储与资源
6. 消息与告警
7. 个人中心

教学管理员与系统运维已合并为一个运维角色。27 屏覆盖课程治理、成员同步、用户目录、角色权限、访问策略、权限冲突、操作日志、异常复核、安全事件、账号冻结、存储清理、服务状态、配置变更、消息与账号安全。

## 交互边界

- 每条路线按 `#页面id` 直接定位单屏，供审阅和截图使用。
- 左侧导航只表达信息架构，不触发页面切换。
- 页面内保留筛选、勾选、开关、选项切换和弹窗关闭。
- 删除路线翻页、方向键换页、哈希变化监听和跨页业务跳转。
- 教师端与运维端共 54 张路线画面均无原型反应、无热点、无跨页跳转。

## 交付文件

- `index.html`：两套路线的入口页
- `teacher.html`、`operations.html`：教师端与运维端路线入口
- `app.js`、`styles.css`：共享页面结构与角色主题
- `previews/teacher/`、`previews/operations/`：各 27 张 `1440×900` 预览
- `teacher-route-v3-overview.png`、`operations-route-v3-overview.png`：路线总览
- `board.html`：可切换查询参数的总览浏览页
- `capture-server.mjs`：本地静态服务器
- `TEACHER_DESIGN.md`、`OPERATIONS_DESIGN.md`：两条路线的设计说明

Figma 原生源数据、归一化报告与必要插件配置位于本复刻的 `../figma/`；历史验证脚本、调试截图与中间 JSON 位于仓库 `03_页面原型/_archive/staff_routes_v3/`。

## 验证结果

- 教师页面：27
- 运维页面：27
- 布局与导航失败：0
- 小于 11px 的正文：0
- 对比度低于 4.5:1 的正文：0
- 可见文本重叠：0
- 横向溢出：0
- 页面内容溢出：0
- 页面间导航入口：0
- 两套导航稳定性：54 / 54
- 页内交互回归：22 / 22
- Figma 教师端：1 张总览 + 27 张画面
- Figma 运维端：1 张总览 + 27 张画面
- Figma 学生端：1 张总览 + 27 张画面
- Figma 可编辑业务画面：81 / 81
- Figma 整屏截图填充：0
- Figma 路线画面原型反应：0
- Figma 角色总览卡片：3
- Figma 认证角色入口：3
- Figma 失效原型目标：0
- 旧教师、教学管理、系统运维画面残留：0
