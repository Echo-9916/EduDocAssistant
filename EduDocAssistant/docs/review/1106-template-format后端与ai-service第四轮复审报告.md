# 1106 模板格式切片（04）后端与 ai-service 第四轮复审报告

> 复审时间：2026-11-06 22:01（Asia/Shanghai）  
> 复审基线：`HEAD=d3981d5` + 当前未提交工作区；上一轮验收基线为 `1106-template-format后端与ai-service第三轮复审报告.md`  
> 审查范围：`docs/tasks/04-template-format.md` D-01~D-07 后端，以及 ai-service `/v1/convert`  
> 审查方式：只读复核、Spec / Standards 双轴检查、全量自动化回归、JShell 同型探针  
> 总体结论：**第三轮点名的四个问题已按原症状修正，但本轮新增修改仍存在 2 个 P1、3 个 P2，暂不可按“可交付”放行。**

---

## 一、结论摘要

第三轮报告之后，以下文件和测试确实发生了新一轮修改：

- `TemplateRuleParser.java`
- `TemplateOutlineGenerator.java`
- `DocxStyleEditor.java`
- `FormatFixServiceImpl.java`
- `FixAction.java`
- `FormatRuleMatcher.java`
- 对应四个测试类
- `docs/tasks/04-template-format.md`

本轮已关闭第三轮点名的原症状：

- 生成和修复时，ASCII 与 East Asia 两槽不再被同一个 `family` 无条件同时覆盖。
- 子样式 EXACT/AT_LEAST 不再穿透父样式生成错误的 `multiplier` 规则。
- 缺页边距、缺 East Asia 实际值不再静默通过。
- `FONT` 规则契约已同步 `eastAsia`。

但同类问题仍存在于其他分支：

1. **P1：纸型错误被标为可一键修复，但修复器不写 `pgSz`。**
2. **P1：固定行距无法被规则表达；固定行距与倍数规则比对时会被直接跳过。**
3. **P2：模板只有 East Asia 字体时，解析器把中文槽复制成 ASCII 规则。**
4. **P2：FONT 的 ASCII 槽和 SIZE 实际值缺失时仍静默通过。**
5. **P2：加密 DOCX 的转换失败被统一映射为 `CORRUPTED`，与 D-06 的 `ENCRYPTED|CORRUPTED` 要求不完全一致。**

---

## 二、验证基线

| 验证项 | 命令/方式 | 实际结果 |
|---|---|---|
| 后端全量测试 | `EduDocAssistant/backend` 下 `mvn test` | **366 passed / 0 failure / 0 error** |
| 真库 IT | Maven 日志 | 两个 IT 仍为 `Tests run: 0` |
| ai-service 测试 | `EduDocAssistant/ai-service` 下 `uv run python -m pytest -q` | **28 passed / 1 warning** |
| 差异空白检查 | `git diff --check` | 通过，仅有 CRLF 提示 |
| soffice | `Get-Command soffice` | 未找到 |
| pdf2docx | Python `find_spec` | 未安装 |
| 真库凭据 | 环境变量检查 | `EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD` 缺失 |
| 服务间令牌 | 环境变量检查 | `EDUDOC_AI_INTERNAL_TOKEN` 已设置；未执行真实服务握手 |
| `FormatTest.md` | `docs/test` 检查 | 不存在 |
| D-15 e2e | 任务文档 | 未完成 |

---

## 三、第三轮问题闭环复核

| 第三轮问题 | 当前状态 | 本轮证据 |
|---|---|---|
| P1-1 生成/修复把 ASCII 写入 East Asia 槽 | **原症状已关闭** | 双槽生成/修复回读测试通过；`FixAction` 已新增 `eastAsia` |
| P1-2 子样式固定行距穿透父样式倍数 | **原症状已关闭** | 固定行距子样式不再产出父样式 `multiplier` |
| P2-1 缺页边距静默通过 | **已关闭** | 四边实际值缺失时产出 `未声明` 问题 |
| P2-2 缺 East Asia 实际值静默通过 | **已关闭** | 中文槽缺失时产出问题 |
| P3-1 FONT 契约未同步 `eastAsia` | **已关闭** | 任务文档 D-02 已回填 `family + eastAsia` |

注意：P1-2 的“错误继承”虽已关闭，但固定行距仍没有正确的规则表达和比对语义，见本轮 P1-2。

---

## 四、本轮发现

### P1-1【修复链路】纸型问题可勾选修复，但修复器只写页边距

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:137`
  - `backend/src/main/java/com/cidas/edudoc/template/application/impl/FormatFixServiceImpl.java:179`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleEditor.java:86`
- 现象：`MARGIN` 规则同时承载页边距与 `width/height` 纸型，纸型不符时也产出一个 `auto_fixable=Y` 的 `MARGIN` 问题；但修复动作只收集 `top/bottom/left/right`，写回也只操作 `CTPageMar`，完全没有操作 `pgSz`。
- 同型证据：
  - 规则期望 `210×297`，实际 Letter `215.9×279.4`。
  - 反射检查 `toAction` 得到 `{top=25.4, bottom=25.4, left=31.75, right=25.4}`，没有 `width/height`。
  - 将包含 `width/height` 的动作交给 `DocxStyleEditor` 后回读仍为 `215.9×279.4`。
- 影响：纸型错误会被标记为“已修复”，生成新 EDIT 版本和复审任务，但复审仍会失败；与 D-05“修复后复审通过”的闭环目标冲突。
- 建议：把 `width/height` 纳入 MARGIN 修复动作并写入 `pgSz`；或者将纸型问题单独建模，禁止仅因规则可修复就把它标为 Y。补“Letter→A4 修复后回读”用例。

### P1-2【规则保真/校验准确性】固定行距无规则表达，且固定行距文档会绕过倍数校验

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:92`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleExtractor.java:240`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:108`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateOutlineGenerator.java:87`
- 现象：
  - 模板标题样式使用 EXACT/AT_LEAST 时，解析器终止继承但完全不产出 `LINE_SPACING` 规则。
  - 文档实际使用固定行距时，提取器返回 `lineMultiplier=null`；比对器看到 null 后跳过，不产问题。
  - 生成器没有固定行距规则时回退到 1.5 倍。
- 同型证据：
  - 模板 Heading 1 设置 EXACT 480：`fixedLineRuleCount=0`。
  - 用解析出的规则生成大纲：`generatedHeadingLineMultiplier=1.5`，没有保留固定 20pt。
  - 文档实际为固定行距、规则期望 1.5 倍：`fixedLineVsMultiplierIssues=0`。
- 影响：模板规则丢失；按模板新建的文档不符合模板；格式校验还会把“固定行距 vs 倍数期望”判为通过，直接威胁 P-07 准确率。
- 建议：扩展 `LINE_SPACING` 契约，至少显式区分 `MULTIPLIER`、`EXACT`、`AT_LEAST` 及对应值；提取、比对、生成、修复四处共用该模型。即使暂不支持固定行距生成/修复，也必须把“实际固定行距 vs 期望倍数”判为不符，不能以 null 跳过。

### P2-1【字体槽】只有 East Asia 字体时被复制成 ASCII 规则

- 位置：`backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:103`
- 现象：`String family = ascii != null ? ascii : eastAsia`，当模板只声明中文槽时，仍会写出 `expect.family`/`fix.family`。
- 同型证据：模板仅设置 `rFonts.eastAsia=黑体`，解析结果 `family=黑体, eastAsia=黑体`。
- 影响：违反“两个字体槽独立”的契约；实际文档 ASCII 槽使用默认西文字体时会被误报，生成器也会把黑体写入 ASCII 槽。
- 建议：ASCII 缺失时不要用 East Asia 填充规则；生成器可独立选择 ASCII 安全默认值，但不应把该默认值伪装成模板期望。

### P2-2【缺失值】FONT ASCII 槽与 SIZE 实际值缺失仍静默通过

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:68`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:96`
- 现象：只有实际值非 null 才进入比较；期望存在但实际未提取到时直接跳过。
- 同型证据：
  - 期望 ASCII=Arial、实际 ASCII 槽为 null：`missingAsciiIssues=0`。
  - 期望 16pt、实际字号为 null：`missingSizeIssues=0`。
- 影响：与第三轮已修复的 East Asia/页边距问题同属“缺失值当通过”，会造成漏报。
- 建议：统一缺失值语义：能按 Word 默认值解析的，先完成回退；不能证明实际值时，产出“未声明/无法确认”问题，不得跳过。

### P2-3【转换错误分类】加密 DOCX 被映射为损坏

- 位置：`ai-service/app/converter.py:53`
- 现象：DOCX→PDF 时，只要 soffice 未产出文件，就统一抛 `CORRUPTED`，没有检查 stderr/文件签名中的加密或密码提示。
- 同型证据：模拟 soffice 返回 `source file is encrypted/password protected` 且无产出，实际映射为 `encryptedDocxMappedCode=CORRUPTED`。
- 影响：D-06 要求加密/损坏分别落 `ENCRYPTED|CORRUPTED`；当前用户看到的原因不准确，测试也无法覆盖真实加密 DOCX 反例。
- 建议：在调用 soffice 前识别 OOXML 加密容器，或至少基于结构化 stderr 映射 `ENCRYPTED`；补加密 DOCX 用例。

### P3-1【契约文档】D-06 错误响应键名不一致

- 位置：`docs/tasks/04-template-format.md:79`
- 现象：同一验收句先写失败响应 `{code,message}`，随后写 `{reason: ENCRYPTED|CORRUPTED}`；实际实现和 README 使用 `code/message`。
- 影响：后端、ai-service 和 D-13 前端可能对失败响应字段产生不同实现。
- 建议：统一为 `{code,message}`，将 `reason` 说明为任务查询字段，不再作为 `/v1/convert` 响应键。

### P3-2【运行验收】真实链路和 P-07 仍未验证

- 当前环境仍缺 `soffice`、`pdf2docx`、Oracle 凭据；`docs/test/FormatTest.md`、fixtures、P-07 错题集和 D-15 e2e 均未完成。
- `EDUDOC_AI_INTERNAL_TOKEN` 已设置，但没有真实服务握手证据，不能据此认定转换链路通过。
- 因此自动化单测通过仅代表代码级回归稳定，不代表真实转换质量、数据库 SQL、Redis/OSS 链路或 ≥95% 准确率达标。

---

## 五、Standards 轴

- 本轮测试方向正确，新增了生成后回读、修复后回读以及缺失值用例，能够拦住上一轮的表面修复。
- 但 `FixAction` 使用多个可空参数承载不同动作，属于可能的 Data Clumps / Primitive Obsession；本轮 P1-1 正是“新增纸型字段后遗漏另一个动作分支”的直接后果。建议后续用按动作类型区分的参数对象收口。
- 固定行距用 null 同时表示“固定值不可比”和“没有值”，是当前的语义混淆点，也是 P1-2 漏报的根源。
- 当前仓库未发现独立 `CONTRIBUTING.md` / `CODING_STANDARDS.md`；Standards 轴按既有代码风格、测试覆盖和上述可维护性风险判断。

---

## 六、Spec 轴

- D-02：模板行距和双字体槽仍未完整保真。
- D-04：缺失实际值及固定行距仍可能漏报。
- D-05：纸型问题可被标记为修复，但修复面不完整。
- D-06：加密 DOCX 分类不符合要求，任务文档失败响应契约自相矛盾。
- D-07：固定行距按模板新建时被替换为 1.5 倍，未满足“按模板生成”。
- D-15：仍无真实环境验收证据。

---

## 七、放行判断

**不通过交付验收。**

第三轮点名的原症状已经修正，自动化回归也全绿；但 P1-1 会制造“已修复但复审仍失败”的假闭环，P1-2 会把固定行距误判为通过并破坏按模板生成。P2-1/P2-2 继续影响 P-07 准确率，P2-3 影响转换失败原因判别。

建议下一轮依次处理：

1. 纸型修复贯通 `toAction → DocxStyleEditor → pgSz`。
2. 扩展并贯通固定行距规则模型，禁止 null 跳过不符。
3. 分离 East Asia-only 规则与 ASCII 规则。
4. 统一缺失实际值语义。
5. 修正加密 DOCX 分类与 D-06 文档契约。
6. 完成 D-15 真库、真实 LibreOffice/pdf2docx 和 P-07 样本验收。

---

## 八、审查过程与限制

- 已读取经验库 `00-Index/README.md`、`Knowledge-Index.md`、第三轮 Lesson 和子代理失败 Pattern。
- 该 Pattern 已记录两次 `unsupported_encrypted_agent_content` 失败，本轮按预防动作由根 Agent 完成 Spec / Standards 双轴，未声称经过独立双子代理。
- 本轮未修改业务代码、测试断言或任务文档；仅新增本报告。
- JShell 探针基于当前 `target/classes` 和 Maven Surefire 类路径执行。
