# 1106 模板格式切片（04）后端与 ai-service 第三轮复审报告

> 复审日期：2026-11-06  
> 复审基线：`1106-template-format后端与ai-service整改复审报告.md`（第二轮）  
> 审查范围：`docs/tasks/04-template-format.md` D-01~D-07 后端，以及 ai-service `/v1/convert`  
> 审查方式：只读复核工作区、Spec / Standards 双轴检查、自动化回归、JShell 同型探针  
> 总体结论：**上一轮开放项已基本关闭，但双字体模型未贯通生成与修复，仍存在 2 个 P1、2 个 P2，暂不建议按“可交付”放行。**

---

## 一、结论摘要

本轮改动明显收敛：

- 模板侧 basedOn 字体继承已接入，East Asia 中文槽已在提取与比对中独立建模。
- 段前/段后 spacing 不再截断样式行距继承。
- 文档缺少 `pgSz` 时会按“未声明”产出 BLOCKER。
- 题注 pattern 不再强制尾随空格。
- 模板版本强制携带 `.docx`，非法题注正则保存时拒绝。
- ai-service README 已补 `/v1/convert` 与 `EDUDOC_SOFFICE_BIN`。

回归结果：后端 **361/361**、ai-service **28/28** 通过。但针对“双字体模型”的生成与修复写回链，本轮仍发现两处未闭合：

1. 按模板新建和一键修复仍把 ASCII 西文字体写入 East Asia 中文槽。
2. 模板子样式使用固定行距时，解析器仍会穿透到父样式并错误生成倍数规则。

另外，页边距和 East Asia 实际值缺失时仍被当作“符合”，以及 FONT 规则契约在任务文档中未同步。

---

## 二、验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 后端全量测试 | `EduDocAssistant/backend` 下 `mvn test` | **361 passed / 0 failure / 0 error** |
| 真库 IT | Maven 日志 | 两个 IT 仍为 `Tests run: 0` |
| ai-service 测试 | `EduDocAssistant/ai-service` 下 `uv run python -m pytest -q` | **28 passed** |
| 差异空白检查 | `git diff --check` | 通过，仅 CRLF 提示 |
| soffice | `Get-Command soffice` | 未找到 |
| pdf2docx | Python `find_spec` | 未安装 |
| 服务间令牌 | 环境变量检查 | `EDUDOC_AI_INTERNAL_TOKEN` 缺失 |
| 真库凭据 | 环境变量检查 | `EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD` 缺失 |
| `FormatTest.md` | `docs/test` 检查 | 不存在 |
| D-15 e2e | 任务文档 | 未开始 |

本轮未修改业务代码、测试断言或任务文档，只新增本报告。

---

## 三、上一轮问题闭环状态

| 上轮问题 | 当前状态 | 同型探针 |
|---|---|---|
| P1-1 模板 basedOn 字体继承 | **已关闭** | `headingFontRuleCount=1` |
| P1-1 East Asia 中文槽被忽略 | **比对侧关闭** | `eastAsiaMismatchIssues=1` |
| P1-2 段前后 spacing 截断行距继承 | **已关闭** | `beforeOnlyLine=1.5`、`lineIssues=0` |
| P2-1 缺 pgSz 时 A4 静默通过 | **已关闭** | `missingSizeIssues=1` |
| P2-2 题注 pattern 强制尾随空格 | **已关闭** | `noSpaceCaption=true` |
| P2-3 模板版本无 docx 创建 | **已关闭** | `createWithoutFileRejected` |
| P2-4 非法正则静默跳过 | **已关闭** | `replaceRulesRejectsInvalidCaptionRegex` |
| 一致性 README 缺 convert/soffice | **已关闭** | README 已补接口与环境变量 |

---

## 四、发现清单

### P1-1【生成/修复】East Asia 中文字体槽未贯通写回

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateOutlineGenerator.java:78`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleEditor.java:55`
  - `backend/src/main/java/com/cidas/edudoc/template/application/impl/FormatFixServiceImpl.java:161`
- 现象：规则已经区分 `expect.family`（ASCII）与 `expect.eastAsia`（中文槽），但生成器和修复器仍把单一 `family` 同时写入两个槽。
- 同型证据：
  - 期望 `family=Times New Roman`、`eastAsia=SimSun`，按模板新建后提取 `family=Times New Roman`、`familyEastAsia=Times New Roman`。
  - 用 `FixAction("FONT","Times New Roman")` 修复中文段后，`fixedEastAsia=Times New Roman`。
  - `FormatFixServiceImpl` 只读取 `fix.family`，没有读取 `fix.eastAsia`。
- 影响：按模板新建的中文标题字体错误；“一键修复”修复 East Asia 字体问题时会把中文槽写成西文字体，复审仍不通过。
- 建议：`FixAction` 增加 eastAsia 字段，`TemplateOutlineGenerator` 和 `DocxStyleEditor` 分别写 ascii 与 eastAsia 两槽。

### P1-2【规则保真】子样式固定行距穿透到父样式倍数

- 位置：`backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:91`
- 现象：Heading 子样式基于 Normal，并设置 EXACT/AT_LEAST 固定磅值时，`lineSpacingOf` 返回 null 表示“本层未提供倍数”，循环继续上溯，最终取父样式的 1.5 倍。
- 同型证据：Normal 1.5 倍、Heading 1 基于 Normal 且 EXACT 480，解析产出 `LINE_SPACING expect=1.5` 而非“固定 20pt”。
- 影响：模板规则失真；按模板新建会写成 1.5 倍而不是固定磅值，格式校验的期望也不对。
- 建议：EXACT/AT_LEAST 应作为“继承终止”结果，不继续向父样式取倍数。

### P2-1【缺失值】页边距缺失时仍视为通过

- 位置：`backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:116`
- 现象：`matchMargin` 只在 `got != null` 时比较；文档没有 `pgMar`（top/bottom/left/right 均为 null）时不会产生任何页边距问题。
- 同型证据：期望四边 25.4/25.4/31.75/25.4，实际四边全 null，`missingMarginIssues=0`。
- 影响：与已关闭的“缺 pgSz 静默通过”同一类缺陷，但页边距尚未覆盖。
- 建议：期望边距存在而实际值缺失时产出 BLOCKER 或“未声明”，不得静默通过。

### P2-2【缺失值】East Asia 实际值缺失时静默通过

- 位置：`backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:71`
- 现象：规则期望 `eastAsia=SimSun`，但段落没有 East Asia 槽时，`familyEastAsia()==null`，匹配器跳过该槽。
- 同型证据：段落只设 ASCII=Arial、无 East Asia，期望 `eastAsia=SimSun`，`missingEastAsiaIssues=0`。
- 影响：中文渲染实际会回退到非期望字体，但校验不报问题。
- 建议：期望 East Asia 而实际缺失时，按“未声明”处理或与 ascii 槽合并回退语义，避免漏报。

### P3-1【契约】FONT 规则 JSON 契约未同步 eastAsia

- 位置：`docs/tasks/04-template-format.md:47`
- 现状：实现已在 `expect.fix` 中写入 `eastAsia`，但 D-02 规则 JSON 契约仍写 `FONT→family`。
- 影响：前后端契约说明落后于实现，后续 D-09 表单层可能按旧键集生成规则。
- 建议：把 FONT 键集回填为 `family + eastAsia`，并说明 ASCII 与中文槽独立语义。

### P3-2【运行验收】真实链路与 P-07 仍待 D-15

- 当前环境仍缺少 soffice、pdf2docx、服务间令牌和数据库凭据。
- 两个真库 IT 仍跳过；`docs/test/FormatTest.md` 不存在；P-07 错题集与 D-15 e2e 未开始。
- 结论：代码级整改可以继续推进，但真实转换、真实 SQL 和 ≥95% 准确率仍无验收证据。

---

## 五、Standards 轴

- 本轮修改补了较多针对性单测，方向正确：字体双槽、行距三态、缺 pgSz、题注前缀、无文件拒绝、坏正则拒绝均有对应用例。
- 但写回链缺少“生成后回读”和“修复后回读”的 East Asia 断言，导致 P1-1 漏网。
- 模板规则解析对固定行距的继承终止语义没有用例，导致 P1-2 漏网。
- 缺失值只修了 pgSz，未同步处理 pgMar 和 East Asia 槽，存在同类缺陷扩散。
- 真库与真实引擎未验证，仍属 D-15 前置债务。

---

## 六、放行判断

**判断：不通过可交付验收。**

本轮关闭了上轮多数问题，但 P1-1、P1-2 都会直接破坏“生成即合规”和“修复后复审通过”的目标，P2-1、P2-2 仍是格式校验准确率风险。建议下一轮先闭合四项，再进入 D-15 真实链路。

---

## 七、审查过程与限制

- 以第二轮复审报告为基线，定位 17:38 之后的改动并复验。
- 子代理协议问题此前已多次复现，本轮直接由根 Agent 完成 Spec / Standards 双轴。
- 真实数据库、真实转换引擎与 P-07 样本集未执行，相关结论保持“未验证”。

---

## 八、相关文件

- [第二轮复审报告](1106-template-format后端与ai-service整改复审报告.md)
- [04-template-format.md](../tasks/04-template-format.md)
- `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateOutlineGenerator.java`
- `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleEditor.java`
- `backend/src/main/java/com/cidas/edudoc/template/application/impl/FormatFixServiceImpl.java`
- `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java`
- `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java`

---

## 九、复审后核验补记（2026-11-06 21:44）

- 后端重新执行 `mvn test`：361 passed，0 failure，0 error；两个真库 IT 仍因数据库凭据缺失执行 0 例。
- ai-service 重新执行 `uv run python -m pytest -q`：28 passed，1 条第三方弃用警告。
- 当前环境中 `EDUDOC_AI_INTERNAL_TOKEN` 已设置，但尚未执行服务间真实握手；不能据此认定端到端链路通过。
- `EDUDOC_DB_USERNAME`、`EDUDOC_DB_PASSWORD`、`soffice`、`pdf2docx` 仍缺失；`docs/test/FormatTest.md` 与 `docs/test/fixtures/` 仍不存在。
- 上述源码与测试结果未改变总体判断：代码级审查与问题复验基本到位，真实环境及 D-15 端到端验收仍不到位，暂不可按可交付放行。
