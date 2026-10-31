# Figma 配置与源数据

本目录集中保存与 Figma 文件 `sVEDsRFiVY022tTAd9u5cG` 同步所必需的数据、报告与插件配置。其余历史截图、调试脚本与中间 JSON 已归档至 `../_archive/`。

## 目录说明

| 路径 | 说明 |
| --- | --- |
| `native/` | 从真实 DOM 抽取的 81 屏文本、布局、图标与样式数据（student/teacher/operations），是原生对象重建的源数据 |
| `figma-plugin-native/` | 原生导入插件（`code.js` + `manifest.json`），按 `native/*.json` 确定性重建画面 |
| `figma-plugin-normalize/` | 图层归组与命名归一化插件（`code.js` + `manifest.json`） |
| `normalize-task.json` | 归一化任务定义，与 normalize 插件配合使用 |
| `normalize-report.json` | 归一化最终报告（81/81 画面、6038 次重命名、1296 个分组） |
| `normalize-report-baseline.json` | 归一化前的干净基线导出 |
| `figma-native-routes-summary.json` | 三角色 81 屏原生对象转换汇总 |
| `NORMALIZE_DESIGN.md` | “总画布 / 侧边栏 / 内容区”图层归组与命名规范 |
| `figma-requirements-audit-2026-10-30.md` | 原型与需求合规检查报告 |

## 使用注意

- 桌面 Figma 客户端迭代插件 `code.js` 后，必须在 Development 菜单开启 “Hot reload plugin”，否则会静默执行导入时缓存的旧代码。
- `native/*.json` 是重建 81 屏的权威源数据；重建报告中的文本图层数应为学生 1514、教师 1604、运维 1785。
- 归一化已保存 Figma 版本 `2416111651714900099`。
