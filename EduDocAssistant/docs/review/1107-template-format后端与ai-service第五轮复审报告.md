# 1107 模板格式切片（04）后端与 ai-service 第五轮复审报告

> 复审时间：2026-11-07 10:35（Asia/Shanghai）
> 复审基线：`HEAD=d3981d5` + 当前未提交工作区
> 审查范围：`docs/tasks/04-template-format.md` D-01~D-07 后端，以及 ai-service `/v1/convert`
> 审查方式：只读代码审查、Spec / Standards 双轴检查、全量自动化回归、JShell 最小复现探针
> 总体结论：**第四轮点名的六类原问题已关闭；本轮新增 5 个 P2 与 2 个 P3，当前不能通过完整交付验收。**

---

## 一、结论摘要

第四轮复审指出的纸型修复、固定行距、字体槽、缺失值、加密 DOCX 和转换失败键名问题，已在当前代码中看到对应修正，相关回读测试与同型探针也成立。

但当前实现仍存在一组同源问题：规则 JSON 在保存、比对、生成和修复四处分别解释，缺少统一的领域对象与不变量。结果是：

- 非法或不完整的 `expect` 可以保存为 `VALID`，运行时却直接跳过或放宽比较；
- `auto_fixable=Y` 可以没有可执行的 `fix`，前端会显示“可修复”但修复服务实际跳过；
- 只有 `pgSz`、没有 `pgMar` 的合法 DOCX 会完全丢失纸型规则；
- 模板仅通过 `docDefaults` 声明默认字体和字号时，不产出标题/正文样式规则；
- 同一段落内多个 run 样式不一致时，只检查第一个 run，后续局部错误可漏报。

因此本轮不能确认 D-02、D-04、D-05 已完整满足要求，也不能确认 P-07 的漏报口径可达标。`mvn test 377/377` 与 `pytest 31/31` 只证明现有代码级回归通过，不覆盖真库 IT、真实双向转换、错题集准确率和 D-15 端到端链路。

任务文档当前“全部 P1/P2 代码面闭环”的表述，只能理解为第四轮已点名项闭环，不能作为本轮全面验收结论。本报告未擅自修改任务文档状态。

---

## 二、验证基线

| 验证项 | 命令或方式 | 实际结果 |
|---|---|---|
| 后端全量测试 | `backend`: `mvn test` | **377 passed / 0 failure / 0 error / 0 skipped** |
| 真库 IT | Maven 日志 | 两个 IT 仍为 `Tests run: 0`，因数据库凭据缺失未执行 |
| ai-service 全量测试 | `ai-service`: `uv run python -m pytest -q` | **31 passed / 1 warning**；警告为 Starlette/httpx 第三方弃用提示 |
| 差异空白检查 | `git diff --check HEAD` | 退出码 0，仅有 CRLF 提示 |
| LibreOffice | `Get-Command soffice`、`EDUDOC_SOFFICE_BIN` | 均未提供 |
| pdf2docx | `importlib.util.find_spec` | 未安装 |
| 真库凭据 | 环境变量检查 | `EDUDOC_DB_USERNAME`、`EDUDOC_DB_PASSWORD` 缺失 |
| 服务间令牌 | 环境变量检查 | `EDUDOC_AI_INTERNAL_TOKEN` 已设置；未执行真实服务握手 |
| D-15 文档与样本 | 路径检查 | `docs/test/FormatTest.md` 与 `docs/test/fixtures/` 均不存在 |
| D-15 e2e / P-07 | 当前仓库证据 | 未执行，不能报告为通过 |

验证结论边界：

- 377 个后端测试和 31 个 Python 测试均为真实重跑结果；
- 两个真库 IT 没有执行，不应计入 377 个通过的测试路径；
- 没有真实 Office/PDF 引擎、真实数据库和错题集，不能证明转换可开、SQL 可用或 P-07 ≥95%。

---

## 三、第四轮问题闭环复核

| 第四轮问题 | 当前状态 | 本轮证据 |
|---|---|---|
| P1-1 纸型可修复但修复器不写 `pgSz` | **已关闭** | `DocxStyleEditor.applyPage` 同时写 `CTPageMar` 与 `CTPageSz`；Letter→A4 修复后回读用例存在并通过 |
| P1-2 固定行距无规则表达、比对跳过 | **已关闭** | `LineSpacing(kind,value)` 已贯通解析、比对、生成与修复；固定磅值用例通过 |
| P2-1 East Asia-only 被复制成 ASCII 规则 | **已关闭** | `TemplateRuleParser` 两槽分别成键，不再用中文槽填充西文槽 |
| P2-2 ASCII 字体与字号实际缺值静默通过 | **已关闭** | `FormatRuleMatcher` 在期望存在、实际未声明时产出问题 |
| P2-3 加密 DOCX 被归为 `CORRUPTED` | **已关闭** | OLE 加密容器魔数提前识别；stderr 加密关键字有兜底；相应用例通过 |
| P3-1 D-06 失败响应 `{reason}` / `{code,message}` 混写 | **键名已关闭** | 任务文档已回填 `{code,message}`；但枚举与测试数量仍有漂移，见 P3-2 |

上表只说明第四轮点名问题的原症状已修正，不代表同类语义在全部入口均已收口。

---

## 四、本轮发现

### P2-1【规则保存/比对】非法或空操作期望仍可入库并静默失效

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/application/impl/TemplateVersionServiceImpl.java:256`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:122`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:219`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:229`
- 现象：`validateExpectKeys` 只判断“键存在”或“是数字/文本”，没有校验数值范围、枚举域、布尔类型和组合语义。
- 实测证据：

| 输入 | 保存校验 | 运行结果 |
|---|---|---|
| `CAPTION expect={"perChapter":false}` | 接受 | 题注问题数 0，`pattern==null && !perChapter` 直接返回 |
| `LINE_SPACING expect={"lineRule":"exact"}` | 接受 | 对 EXACT 42pt 的问题数 0；缺少 `pt` 仍可匹配任意 EXACT 值 |
| `SIZE expect={"pt":-1}` | 接受 | 问题数 0，匹配器以负数作为“未配置”哨兵直接返回 |
| `MARGIN expect={"width":0}` | 接受 | 问题数 0，宽高 `<=0` 被当作未配置 |
| `HEADER_FOOTER expect={"present":"false"}` | 接受 | 字符串被 `asBoolean(false)` 解释；匹配器只处理期望存在，不能校验不存在 |

- 影响：规则保存成功并可能使版本进入 `VALID`，但实际校验不执行。模板作者无法从保存结果发现配置无效，P-07 直接出现漏报。
- 建议：把规则 JSON 解析为按规则类型区分的领域对象，在保存期校验完整不变量；`SIZE`/尺寸值必须大于 0，`present` 必须是 JSON 布尔，`lineRule` 限定 `exact|atLeast` 且固定行距必须有 `pt`，`CAPTION` 至少有一个可执行条件。匹配器对非法配置应失败关闭，不得 `return` 当作通过。

### P2-2【修复契约】`auto_fixable=Y` 可没有可执行修复动作

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/application/impl/TemplateVersionServiceImpl.java:134`
  - `backend/src/main/java/com/cidas/edudoc/template/application/impl/FormatFixServiceImpl.java:117`
  - `backend/src/main/java/com/cidas/edudoc/template/application/impl/FormatFixServiceImpl.java:154`
- 现象：保存期只检查“规则类型属于可修复四类”，不要求 `rule_config_json.fix` 存在且包含对应类型的有效值。
- 实测证据：使用 `FONT` 规则 `{"expect":{"family":"Arial"}}`、`autoFixable=Y`，保存校验可接受；反射调用 `toAction` 返回 `null`；修复编排随后把该问题放入 `skipped`。
- 影响：前端会把问题显示为可勾选修复，用户提交后却得到空修复或“跳过”回执，违反 D-05“可修复项执行修复并自动复审”的用户契约。
- 建议：保存期强制 `autoFixable=Y ⇒ 至少一个有效 fix 变体`，并校验 fix 值域与 expect 对齐；orchestrator 对运行时缺失动作应记录契约错误，而不是静默 skipped。补充 FONT/SIZE/LINE_SPACING/MARGIN 的“可修复但缺 fix”拒绝用例。

### P2-3【模板解析】只有 `pgSz`、没有 `pgMar` 时纸型规则丢失

- 位置：`backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:181`
- 现象：`emitPageRules` 把整个纸型读取包在 `body.getSectPr().isSetPgMar()` 和 `top != null` 两个条件内。合法的 `pgSz`-only 文档不会产出任何 `MARGIN` 规则。
- 实测证据：构造仅写 `pgSz`（A4 宽高）的 DOCX，解析结果 `MARGIN` 规则数 0。
- 影响：D-02 的纸张尺寸要求丢失，按模板新建时无法保真写回纸型；也说明部分页边距缺失时会连带丢失其他已声明属性。
- 建议：`pgSz` 与 `pgMar` 独立读取，每个宽高/边距键独立判断是否存在；补 `pgSz-only`、部分 `pgMar`、两者都不存在三类用例。

### P2-4【模板解析】仅由 `docDefaults` 声明的标题/正文字体字号不产出规则

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:28`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:45`
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/TemplateRuleParser.java:75`
- 现象：模板解析器只沿显式 `CTStyle`/`basedOn` 链取值，并明确注释“不做 docDefaults 继承链推导”；而文档实际样式提取器已经按 run → 样式链 → `docDefaults` 解析。
- 实测证据：Heading 1 自身不声明字体/字号，仅 `docDefaults` 定义 Arial/12pt，模板解析出的 HEADING 规则数 0。
- 影响：Word 中合法的默认样式模板不会被保真为规则；D-07 按模板新建会退回安全默认值，D-04 校验也缺少应有期望。D-02 “正文默认样式”要求未完整实现。
- 建议：解析器与提取器共享同一套样式解析顺序，至少把 `docDefaults` 作为标题/正文链的最终回退；补 docDefaults-only、Heading basedOn Normal、Normal 为空但 docDefaults 有值等用例。

### P2-5【样式提取】多 run 段落只取第一个 run，局部格式错误漏报

- 位置：`backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStyleExtractor.java:69`
- 现象：`paragraphFact` 在每个属性第一次取得值后就停止，最终只产出一个段落级 `ParagraphFact`。同一段落后续 run 的字体、字号、East Asia 槽不会参与比较。
- 实测证据一：Heading 1 中 run A 为 Arial 16pt、run B 为 SimSun 12pt；提取结果只有 `family=Arial, pt=16.0, text=AB`，第二 run 的独立样式完全消失。
- 实测证据二：run A 为 Arial 12pt、run B 为 SimSun 16pt，规则期望 Arial 时 `font issue count=0`；首 run 符合即掩盖了后续 run 的不符合。
- 影响：混合格式段落无法准确校验，直接威胁 P-07 的问题定位准确率；缺少字符区间也使前端无法精确标出错误 run。
- 建议：事实模型增加 run/字符 span，或在段落级比较时聚合所有非空 run 并明确报告混合值；补“首 run 符合、后续 run 不符合”和“多个 run 分别错误”的回归用例。

### P3-1【结构树契约】`location_json` 只有段落索引，没有字符区间

- 位置：
  - `backend/src/main/java/com/cidas/edudoc/template/infrastructure/DocxStructureParser.java:158`
  - `backend/src/main/java/com/cidas/edudoc/template/domain/FormatRuleMatcher.java:283`
- 现象：结构元素位置实际输出 `{"paragraphIndex":0}`；格式问题位置同样只含段落索引。
- 规格：D-03 明确要求 `location_json` 为“段落索引+字符区间”，并允许 `page_no` 留空。
- 影响：D-11 只能做到段落级近似高亮，无法按 D-03/D-11 约定精确映射到文本区间；混合 run 错误尤其无法定位。
- 建议：为元素与问题位置统一增加 `charStart`/`charEnd`（或文档化的等价区间字段），并用 POI 段落文本偏移和前端定位用例验证闭区间/开区间口径。

### P3-2【转换契约/文档】`UNSUPPORTED` 未实现，测试与修复面文档仍有漂移

- 位置：
  - `ai-service/app/converter.py:6`
  - `ai-service/app/converter.py:73`
  - `ai-service/app/converter.py:104`
  - `ai-service/app/main.py:140`
  - `backend/src/main/java/com/cidas/edudoc/template/application/ConversionEngineException.java:3`
  - `docs/tasks/04-template-format.md:81`
- 现象：
  - D-06 与后端异常注释列出 `ENCRYPTED|CORRUPTED|UNSUPPORTED|TIMEOUT|ENGINE_UNAVAILABLE`，converter 文档与映射没有 `UNSUPPORTED`；不支持的输入落 `CORRUPTED`，不支持的目标格式落 `BAD_INPUT`。
  - D-06 仍写“pytest 6 例已落”，实际 `test_convert.py` 已有 11 个测试，ai-service 全量为 31 个。
  - `FormatFixServiceImpl`、`TemplateVersionServiceImpl` 与两个测试注释仍写“修复面四类”，而 D-05/R1 已升级为五类（含纸型）。
- 影响：前后端对失败码与修复面的文档理解可能继续分叉；当前不影响 31/31 通过，但不满足契约一致性。
- 建议：明确 `UNSUPPORTED` 的触发条件和 HTTP 状态并补用例；同步 D-06 测试数量、五类修复面注释和 README 错误码表。

---

## 五、Standards 轴

### 轴内结论

**存在需要收口的隐式规则契约；未发现新的命名或格式硬规范问题，但有多处可维护性与失败关闭风险。**

- **Primitive Obsession / Repeated Switches（判断项）**：`JsonNode` 原始配置在保存校验、匹配、生成和修复四处重复解释；`validateExpectKeys` 与各 `match*` 的语义互相独立，导致“保存接受、运行时跳过”。
- **Divergent Change（判断项）**：同一规则类型变更需要同步修改 `TemplateVersionServiceImpl`、`FormatRuleMatcher`、`TemplateRuleParser`、`TemplateOutlineGenerator`、`FormatFixServiceImpl` 及多处测试，缺少单一规则模型。
- **Data Clumps / 隐式不变量（判断项）**：`autoFixable` 与 `fix` 没有绑定关系；`MARGIN` 同时承载页边距和纸型，仍依赖多个可空键组合。
- **测试覆盖缺口（事实）**：现有测试覆盖 happy path、继承链和第四轮样例，但没有覆盖非法数值/枚举、布尔类型、自动修复与动作一致性、`pgSz-only`、`docDefaults-only`、混合 run 和字符区间。
- 仓库未发现 `CONTRIBUTING.md`、`CODING_STANDARDS.md` 或同类独立规范文件，因此 Standards 轴按既有架构、失效关闭原则和 Fowler smell baseline 判断，不把判断项表述为文档硬违规。

### 轴内最严重项

规则配置没有单一领域模型，导致多处语义分叉和静默失败；这不是命名问题，而是可执行契约没有被验证。

---

## 六、Spec 轴

### 轴内结论

**部分通过，D-02/D-04/D-05 仍有实现偏差，D-03/D-06 有契约缺口，D-15 未验收。**

- **D-02**：规则 JSON 的保存期校验仍过浅；`docDefaults` 和 `pgSz-only` 解析不完整，模板规则不能保证保真。
- **D-03**：`location_json` 缺少规格要求的字符区间，段落索引也不能替代字符定位。
- **D-04**：多 run 只取首值，混合格式错误可漏报；非法期望被接受后可能零问题通过。
- **D-05**：`auto_fixable=Y` 与有效 `fix` 没有保存期不变量，存在“可修复但实际 skipped”。
- **D-06**：`UNSUPPORTED` 未实现；失败响应主键名已统一，但枚举与测试文档仍漂移。
- **D-07**：受 P2-3/P2-4 影响，按模板新建不能保证纸型和 docDefaults-only 样式保真。
- **D-15**：真库 IT、真实 LibreOffice/pdf2docx、P-07 错题集和端到端链路仍无证据。

### 轴内最严重项

D-04 的混合 run 漏报与 D-02 的规则静默失效共同影响格式校验准确率，不能满足“可执行规则必须真正参与校验”的核心语义。

---

## 七、放行判断

**代码级回归通过；完整交付验收不通过。**

当前可以确认：

- D-01~D-07 的主流程、并发/权限/事务边界在现有单测覆盖下稳定；
- 第四轮点名的六类问题已闭合；
- 依据现有 happy path，后端与 ai-service 可以编译并通过全量自动化测试。

当前不能确认：

- 所有合法模板都会被完整解析；
- 所有保存为 VALID 的规则都会实际参与校验；
- 所有标记为 Y 的问题都能被修复；
- PDF→DOCX、DOCX→PDF 和真实 Oracle SQL 可用；
- P-07 ≥95% 与 D-15 e2e 达标。

建议修复顺序：

1. 建立规则领域对象，统一保存/匹配/生成/修复语义并失败关闭非法配置；
2. 强制 `autoFixable=Y` 必须有有效 `fix`；
3. 独立解析 `pgSz`/`pgMar`，补齐 `docDefaults`；
4. 改为 run/字符 span 级事实，补混合格式回归；
5. 增加字符区间、`UNSUPPORTED` 与文档漂移修正；
6. 完成 D-15 真库、真实引擎、错题集和端到端验收。

---

## 八、审查过程与限制

- 本轮实际读取经验库 `00-Index/README.md`、`00-Index/Knowledge-Index.md`、第四轮 Lesson 和子代理失败 Pattern。
- 系统本轮限制主动派发子代理；Spec / Standards 两轴由根 Agent 顺序完成，未声称经过独立双代理审查。
- 本轮未修改业务代码、测试断言或任务文档；仅新增本报告及后续经验库复盘。
- JShell 探针基于当前 `backend/target/classes` 与 `backend/target/test-classpath.txt`，所有数值均为实际输出。
- 未执行的测试和环境验证必须继续视为开放项，不能由自动化全绿替代。
