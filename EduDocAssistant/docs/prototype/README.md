# 页面原型

本目录为 `03_页面原型/` 的最新版复刻：三条角色路线的可交互 HTML、预览图、总览图与 Figma 配置。

## 目录

| 路径 | 内容 |
| --- | --- |
| [student/](student/) | 学生端 27 屏可交互原型 |
| [staff/](staff/) | 教师端与运维端 54 屏可交互原型 |
| [screens/](screens/README.md) | 81 屏逐屏索引（标题、分组、说明与预览图） |
| [figma/](figma/) | Figma 原生源数据、归一化报告与插件配置 |

## 本地运行

学生端：

```powershell
cd .\prototype\student
$env:PORT=4173
node .\capture-server.mjs
```

- 页面：http://127.0.0.1:4173/
- 27 屏总览：http://127.0.0.1:4173/board.html

教师端与运维端：

```powershell
cd .\prototype\staff
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

## 设计口径

- 画布 `1440×900`，正文最小字号 `11px`，卡片圆角不超过 `8px`。
- 学生端主题 `#1F3864` / 主操作 `#2563EB`；教师端 `#0F766E` / 导航 `#193C3B`；运维端 `#0E7490` / 导航 `#18344B`。
- 页面内交互保留，页面之间不设置业务跳转、热点与原型反应。
- Figma 原生可编辑业务画面 81/81，整屏截图填充 0，顶层结构“总画布 / 侧边栏 / 内容区”81/81。
