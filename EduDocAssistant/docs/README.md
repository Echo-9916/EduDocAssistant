# EduDocAssistant 文档库

校园智能文档辅助系统（CIDAS，产品昵称 EduDocAssistant）的 Agent 可读文档库。所有文档均为 Markdown，图片存放在各目录的 `assets/` 内；Word 源文档位于仓库根目录的 `02_需求规格说明/` 与 `05_系统设计/`，本目录的 Markdown 为 1:1 复刻版本。

## 目录划分

| 目录 | 内容 |
| --- | --- |
| `requirements/` | 需求陈述说明书、需求规格说明书、原型需求合规检查 |
| `design/` | 概要设计、详细设计、数据库设计、页面设计与图层规范 |
| `prototype/` | 三端可交互 HTML、预览图、Figma 配置与 81 屏逐屏索引 |
| `tasks/` | 任务文档：foundation、auth、document 垂直切片 |
| `test/` | 接口手动测试说明（AuthTest / DocumentTest）与 Apifox 集合 |
| `review/` | 各切片代码审查与整改复审报告 |

## 快速导航

- [需求文档](requirements/README.md)
- [系统设计](design/README.md)
- [页面原型](prototype/README.md)
- [任务文档](tasks/README.md)
- [手动测试](test/DocumentTest.md)

## 当前基线

- 角色模型：学生、教师、运维（教学管理员与系统运维已合并）。
- 页面范围：三条角色路线共 81 个业务画面 + 5 个认证画面，桌面端 `1440×900`。
- 交互口径：页面之间不设置跳转与热点，页内交互保留。
- 文档版本：需求类 `1.1.0-0.0.0`，设计类 `1.1.0`，同步日期 2026-10-30。
