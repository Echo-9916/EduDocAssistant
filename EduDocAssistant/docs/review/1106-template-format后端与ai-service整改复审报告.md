# 1106 模板格式切片（04）后端与 ai-service 整改复审报告

> 复审日期：2026-11-06  
> 复审基线：`1106-template-format后端与ai-service符合性审查报告.md`  
> 审查范围：`docs/tasks/04-template-format.md` D-01~D-07 后端，以及 ai-service `/v1/convert` 的本轮整改  
> 审查方式：只读复核当前工作区、Spec / Standards 双轴检查、自动化回归、JShell 针对性探针  
> 总体结论：**主体明显改善，但仍有 2 个 P1 准确率问题尚未关闭，暂不建议按“可交付”放行。**

---

## 一、复审结论

本轮整改确实处理了上一轮的大部分问题：

- Caption 样式非匹配文本崩溃已修复。
- 模板规则读取接口已补齐。
- A4 纸型解析、校验和生成主路径已补齐。
- 转换超时码已改为 `50401`，multipart 契约已回填任务文档。
- pdf2docx 已加入可选依赖组并更新 lock。
- 格式校验去重增加了文档行锁。
- element_key 口径已在任务文档中回填，原 P3 关闭。
- 后端测试增至 **353/353**，ai-service 保持 **28/28**。

但复审仍发现：

1. **P1 字体规则仍不完全正确**：模板侧 basedOn 字体继承未解析，East Asia 中文字体槽也被忽略，错误中文字体仍可能静默通过。
2. **P1 行距继承存在回归**：段落只设置段前/段后间距时会错误覆盖样式行距为 1.0，产生误报。
3. **P2 A4 缺失值仍被当作通过**：文档没有 `pgSz` 时，期望 A4 也不会产生问题。
4. **P2 题注 pattern 存在空格边界误报**：无空格题注“图1-2系统架构图”会被生成的规则判错。
5. **P2 模板版本仍允许无 docx 文件创建**：与任务文档“上传 .docx 模板”的契约不一致。
6. **P2/P3 真实链路尚未闭合**：无 DB 凭据、无 soffice、未安装可选转换依赖，P-07 错题集和 D-15 e2e 仍未执行。

---

## 二、验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 后端全量测试 | `EduDocAssistant/backend` 下 `mvn test` | **353 passed / 0 failure / 0 error** |
| 真库 IT | Maven 日志 | 两个 IT 仍为 `Tests run: 0` |
| ai-service 测试 | `EduDocAssistant/ai-service` 下 `uv run python -m pytest -q` | **28 passed** |
| 差异空白检查 | `git diff --check` | 通过，仅 CRLF 提示 |
| soffice | `Get-Command soffice` | 未找到 |
| pdf2docx 当前环境 | Python `find_spec` | 未安装 |
| 服务间令牌 | 环境变量检查 | `EDUDOC_AI_INTERNAL_TOKEN` 缺失 |
| 真实数据库 | 环境变量检查 | `EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD` 缺失 |
| P-07 错题集 | `docs/test` 检查 | 未生成、未执行 |
| D-15 e2e | 任务文档 | 未开始 |

本轮没有修改业务代码、测试断言或任务文档，只新增本复审报告。

---

## 三、上一轮问题闭环状态

| 上轮问题 | 当前状态 | 复审证据 |
|---|---|---|
| P1-1 继承 Normal/basedOn/docDefaults 漏报 | **部分关闭** | 正文直接继承 Normal、basedOn 链和 docDefaults 已实现，但模板侧 basedOn 字体规则、East Asia 中文字体、段前段后 spacing 覆盖样式行距仍有问题 |
| P1-2 Caption 非匹配文本崩溃 | **已关闭** | 先判断 `find()`，新增 `looseStyledCaptionDoesNotCrash`；353 项测试通过 |
| P2-1 缺少规则读取接口 | **已关闭** | 新增管理端按版本 GET 和学生 `current-rules` GET，并有 4 个服务层测试 |
| P2-2 A4 未解析/校验/生成 | **部分关闭** | pgSz 解析、±3mm 比对、生成写 A4 已完成；实际文档缺少 pgSz 时仍不报错 |
| P2-3 转换契约/依赖/超时码 | **代码与文档关闭，运行未验证** | multipart 已回填、TIMEOUT→50401 已修复、pdf2docx 已进可选组；本机无 soffice/pdf2docx/token |
| P2-4 CAPTION perChapter=false 空操作 | **部分关闭** | pattern 已入规则并执行；无空格题注会被错误拦截 |
| P3-1 活跃去重非原子 | **代码关闭，真库未验证** | 事务内 `SELECT ... FOR UPDATE` 已接入；无真实 Oracle 并发验证 |
| P3-2 element_key 契约不一致 | **已关闭** | 任务文档已回填为 `{TYPE}-{bodyIndex}` |
| P3-3 真实链路覆盖不足 | **未关闭** | D-15 仍未开始 |

---

## 四、Spec 轴

### P1-1【准确率】中文字体槽和模板继承仍会漏报

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:71`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:197`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleExtractor.java:150`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:62`
- 现象一：模板 Heading 样式通过 basedOn 继承 Normal 字体时，模板解析器只读取当前样式直接属性，不产生 HEADING FONT 规则。
- 现象二：模板和正文都同时设置 ASCII 与 East Asia 字体时，当前实现优先取 ASCII，中文 East Asia 字体不同仍会被判为通过。
- 复现：
  - Heading 基于 Normal 继承 SimSun：`inheritedHeadingFontRules=0`。
  - 模板 ASCII=Times New Roman、EastAsia=SimSun；正文 ASCII 相同、EastAsia=SimHei；期望 SimSun，最终 `eastAsiaMismatchIssues=0`。
- 影响：中文字体错误可能完全漏报，直接威胁 P-07 ≥95%。
- 建议：规则和实际值同时保留 ascii/eastAsia 两个槽；模板解析复用 basedOn/docDefaults；文档实际值按中英文文本选择对应槽，或分别校验。

### P1-2【行距误报】段前/段后 spacing 会覆盖继承行距

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleExtractor.java:99`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleExtractor.java:206`
- 现象：段落存在 `pPr/spacing` 但只设置 `before/after`、未设置 `line` 时，`spacingLine` 返回 1.0，`resolveLine` 立即返回，不再读取样式中的 1.5 倍行距。
- 复现：Normal 样式为 1.5 倍，段落仅设置段前 240 twips；提取实际值 `lineMultiplier=1.0`，期望 1.5 时产生错误 issue。
- 影响：常见 Word 段落格式会产生行距误报，同样影响 P-07。
- 建议：只有段落显式设置 `line`/`lineRule` 时才中断继承；仅 before/after 时应继续查样式链和 docDefaults。

### P2-1【A4】缺少 pgSz 时仍被当作通过

- 位置：`backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:127`
- 现象：`checkPageSize` 仅在 `got != null` 时比较；文档没有 `pgSz`、不能证明为 A4 时不会产生问题。
- 复现：页面宽高均为 null，期望 A4 210×297，`missingPageSizeIssues=0`。
- 影响：没有纸型声明或无法提取纸型的文档可能错误通过 A4 校验。
- 建议：期望纸型存在而实际值缺失时产出 BLOCKER，至少标记为“无法确认纸型”。

### P2-2【题注】生成的 pattern 依赖尾随空格

- 位置：`backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:114`
- 现象：生成的 pattern 末尾固定追加 `\s`。题注“图1-2系统架构图”没有数字后的空格时，无法匹配同一模板中的合法题注。
- 复现：`^图\s*\d+\s*[-－—.．]\s*\d+\s` 对“图1-2系统架构图”返回 false，对“图1-2 系统架构图”返回 true。
- 影响：无空格但格式合法的题注会被误报为 WARNING。
- 建议：使用 `(?:\s|$)` 或删除强制尾随空白，并要求 pattern 与解析器的 CAPTION_START 口径一致。

### P2-3【模板版本】仍允许无 docx 文件创建版本

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/api/TemplateVersionController.java:44`
  - `backend/src/main/java/com/cidas/edudoc/template/application/impl/TemplateVersionServiceImpl.java:71`
  - `backend/src/test/java/com/cidas/edudoc/template/application/TemplateVersionServiceTest.java:109`
- 事实：`file required=false`；无文件时创建 `source_object_id=null` 的 PENDING 版本，测试明确锁定该行为。
- 规格：D-02 写的是 `multipart 上传 .docx 模板`。
- 影响：缺少源模板对象仍可编辑规则、转为 VALID 并发布，模板版本可追溯性降低。
- 建议：二选一：要求 file 必填；或明确支持“规则草稿版本”并在任务文档与数据库语义中定义。

### P2-4【规则防线】非法正则被静默跳过

- 位置：`backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:153`
- 现象：CAPTION pattern 无法编译时直接 `return`，不生成 issue；`replaceRules` 只校验 JSON 语法，不校验正则。
- 影响：无效题注规则会被标记为 VALID，校验时静默失效。
- 建议：规则保存阶段编译校验正则；运行时无法编译应产生 ERROR 或明确 issue，而不是静默通过。

### P2-5【运行验收】D-06 仍无法在当前环境完成

- 已修复：multipart 契约文档、50401 超时码、pdf2docx 可选依赖。
- 未验证：真实 soffice、真实 pdf2docx、真实 DOCX↔PDF、真实加密 PDF 反例。
- 当前环境：soffice 缺失、pdf2docx 未安装、internal token 缺失。
- 结论：代码路径可判为改善，D-06 运行能力仍未验收。

---

## 五、Standards 轴

### 做得较好的部分

- 样式提取器已明显向 Word 渲染语义靠近：run → 样式链 → docDefaults → 默认行距。
- Caption 崩溃修复包含明确的回归测试，没有仅做防御性 try/catch。
- 规则读取接口有明确的 MANAGE / READ 权限分层。
- 生成器使用解析器同一份宽度/高度规则，并有生成→提取往返测试。
- 活跃校验通过文档行锁保证事务内去重。
- 转换超时与错误码映射有专门单测。

### 仍需处理

- 当前测试数量增加，但没有覆盖：
  - 模板 Heading 的 basedOn 字体继承；
  - East Asia 与 ASCII 字体冲突；
  - 仅 before/after spacing 的段落；
  - 缺失 pgSz 的 A4 校验；
  - 无空格题注；
  - 非法 CAPTION 正则。
- 真库 IT 仍全部跳过，`selectForUpdate`、JSON 读取和真实事务未验证。
- Controller 仍以薄委托为主，没有 HTTP 契约级测试；任务文档称随 D-15 闭合，因此本轮列为验收债务而非新增代码缺陷。
- ai-service README 已补 `uv sync --extra convert`，但接口列表仍没有列出 `/v1/convert`，环境变量表也没有 `EDUDOC_SOFFICE_BIN`，文档一致性仍有小缺口。

---

## 六、放行判断

**判断：有条件通过，不建议标记为可交付。**

- 作为“整改实现”可以继续推进，353/353 与 28/28 说明主路径没有明显编译或回归问题。
- P1-1、P1-2 都会直接影响格式问题定位准确率，必须在 D-15 前修复。
- P2-1～P2-4 属于规格边界或静默失效风险，建议本轮一并处理。
- D-06 当前只能证明代码路径和错误映射，不能证明真实转换能力。
- P-07 95%、真实数据库、真实 OSS/Redis 和端到端流程仍未验收。

建议下一轮最小整改范围：

1. 字体事实模型同时保存 ascii/eastAsia；模板解析复用 basedOn/docDefaults。
2. 行距只在显式设置 `line` 时读取，否则继续继承。
3. 实际纸型缺失时产出问题。
4. 修正题注 pattern 的尾随空格。
5. 明确无文件模板版本策略，或强制 file 必填。
6. 非法正则保存时拒绝。

---

## 七、审查过程与限制

- 上一轮报告作为基线，本轮重点是整改是否真正关闭问题。
- 子代理失败模式已在历史知识中复现多次；本次没有重复派发，Spec / Standards 双轴由根 Agent 完成。
- 本轮没有修改业务代码，只新增复审报告。
- 真实数据库、真实转换引擎和 P-07 样本集未执行，因此相关结论保持为“未验证”。
- 当前工作区仍未提交，template 域实现和测试大量仍是未跟踪文件，不能称为稳定分支基线。

---

## 八、相关文件

- [上一轮审查报告](1106-template-format后端与ai-service符合性审查报告.md)
- [04-template-format.md](../tasks/04-template-format.md)
- `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleExtractor.java`
- `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java`
- `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java`
- `backend/src/main/java/com/cidas/edudoc/template/api/TemplateVersionController.java`
- `backend/src/main/java/com/cidas/edudoc/template/application/impl/TemplateVersionServiceImpl.java`
- `ai-service/app/converter.py`
- `ai-service/pyproject.toml`
