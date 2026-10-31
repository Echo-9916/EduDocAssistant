# 桌面端页面原型说明

校园智能文档辅助系统（EduDocAssistant）的桌面端页面原型，覆盖三条角色路线：

- 学生端 v3：27 屏，位于 `student_route_v3/`
- 教师端 v3：27 屏，位于 `staff_routes_v3/`
- 运维端 v3：27 屏，位于 `staff_routes_v3/`

教学管理员与系统运维已合并为一个运维角色。三条路线均按 `1440×900` 桌面画布设计，页面之间不设置跳转、翻页和热点；筛选、勾选、开关、表单和单页弹窗等页内交互保留。Figma 交付为原生可编辑对象，业务画面不使用整屏截图填充。

## 当前目录

| 路径 | 说明 |
| --- | --- |
| `student_route_v3/` | 学生端 27 屏可交互原型、预览图与总览图 |
| `staff_routes_v3/` | 教师端与运维端 54 屏可交互原型、预览图与总览图 |
| `assets/` | 品牌 Logo 素材 |
| `figma/` | Figma 原生源数据、归一化报告与必要的插件配置 |
| `_archive/` | 历史版本、旧截图与中间产物归档，供追溯使用 |

## 快速查看

### 学生端

```powershell
cd .\student_route_v3
$env:PORT=4173
node .\capture-server.mjs
```

- 页面：http://127.0.0.1:4173/
- 27 屏总览：http://127.0.0.1:4173/board.html

### 教师端与运维端

```powershell
cd .\staff_routes_v3
$env:PORT=4180
node .\capture-server.mjs
```

- 路线入口：http://127.0.0.1:4180/
- 教师端：http://127.0.0.1:4180/teacher.html#workbench
- 运维端：http://127.0.0.1:4180/operations.html#workbench
- 教师总览：http://127.0.0.1:4180/board.html?role=teacher
- 运维总览：http://127.0.0.1:4180/board.html?role=operations

## Figma

文件：https://www.figma.com/design/sVEDsRFiVY022tTAd9u5cG

- 学生端 27 屏总览：https://www.figma.com/design/sVEDsRFiVY022tTAd9u5cG?node-id=205-2
- 教师端 27 屏总览：https://www.figma.com/design/sVEDsRFiVY022tTAd9u5cG?node-id=228-3
- 运维端 27 屏总览：https://www.figma.com/design/sVEDsRFiVY022tTAd9u5cG?node-id=228-32

Figma 中保留学生、教师、运维三个角色入口。学生端位于主页面，教师端和运维端分别占独立页面；三条路线均为 1 张原生总览与 27 个可编辑业务 Frame。文字、图形和图标可以分别选中修改，整屏截图填充为 0。

## 图层规范

2026-10-30 起，81 个路线画面已按登录页规范完成图层归组与命名：每个画面统一为“总画布 / 侧边栏 / 内容区”三层。完整规则与验证记录见 `figma/NORMALIZE_DESIGN.md`，需求覆盖核对见 `figma/figma-requirements-audit-2026-10-30.md`。

## 设计口径

- 画布：`1440×900`
- 正文最小字号：`11px`
- 学生端主题：`#1F3864`，主操作 `#2563EB`
- 教师端主题：`#0F766E`，导航 `#193C3B`
- 运维端主题：`#0E7490`，导航 `#18344B`
- 页面底色：`#F4F7FB`
- 卡片圆角不超过 `8px`
- 页面内交互保留，页面之间不设置业务跳转

## 验证结果

- 学生端页面：27，教师端页面：27，运维端页面：27
- 页面间导航入口：0，跨页跳转：0，原型热点：0
- 正文低于 11px：0，对比度低于 4.5:1 的正文：0
- 可见文本重叠：0，横向溢出：0
- Figma 可编辑业务画面：81 / 81，整屏截图填充：0
- 顶层“总画布 / 侧边栏 / 内容区”结构：81 / 81
