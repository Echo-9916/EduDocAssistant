# FormatTest · 04 切片测试基线（D-15 前置节，2026-11-07）

> 本文件随 D-15 联调补全（逐接口 curl 正反例、观测命令、错题集统计与 P-07 结论）。
> 当前仅登记「模板样例语料」——错题集与真实转换验证的输入面。

## 模板样例语料 `docs/test/fixtures/templates/`

| 文件 | 来源 | 形态 | 用途 |
| --- | --- | --- | --- |
| `Web开发实战课程设计报告撰写要求.docx` | `01_需求调研/需求调研原件`（只读原件拷贝） | OOXML 原文 | D-02 上传解析主样本；正文 BODY 规则语料 |
| `C语言课程设计报告格式.doc` / `.docx` | 同上 + LibreOffice 转换 | 旧 OLE + 转换件 | 标题/正文/固定行距（atLeast 23pt）多形态语料 |
| `Oracle应用项目报告模版2026.doc` / `.docx` | 同上 + LibreOffice 转换 | 旧 OLE + 转换件 | BODY 规则语料 |
| `JavaEE课程作业模板.pdf` / `.docx` | 同上 + pdf2docx 转换（2026-11-07，1.0s/3页） | PDF + 转换件 | PDF 原件=「非 docx 校验 → ERROR（J1）」反例 + D-06 真实转换链路正样本；**转换件质量评估**：标题层级/SIZE/LINE_SPACING 可信（HEADING_1..3=14/13/11pt、1.15 倍），**MARGIN 与 FONT 不可信**（top=0.0、left/right≈10mm 为版面重建产物，FONT 无产出）——错题集只用前者，页边距真值以 PDF 目视 + 人工规则录入为准 |

**转换复现命令**（可重复执行，产物已在库）：

```bash
"C:\Program Files\LibreOffice\program\soffice.exe" \
  -env:UserInstallation=file:///C:/Users/admin/AppData/Local/Temp/edudoc-lo-profile \
  --headless --norestore --convert-to docx --outdir docs/test/fixtures/templates \
  docs/test/fixtures/templates/C语言课程设计报告格式.doc
```

## 真实语料解析冒烟结论（TemplateRuleParser，2026-11-07）

- Web开发：3 规则（FONT/SIZE BODY 仿宋 10.5pt + MARGIN PAGE 含 A4 纸型）
- C语言：10 规则（H1 黑体/atLeast 23pt、H2 Arial+1.73 倍、BODY Times+仿宋/atLeast 无值已正确跳过、MARGIN PAGE）
- Oracle：3 规则（BODY 10.5pt + MARGIN PAGE）
- 首跑暴露并已修复两处契约缺陷（真实语料价值所在）：
  1. `w:rFonts` 分号回退链「黑体;SimHei」曾整串入期望 → 现拆**主名入 expect/fix + 别名入 tolerance.aliases**（比对器已支持 aliases 相等即通过）；
  2. `lineRule` 固定但缺 `w:line` 值曾产出**缺 pt 的规则**（保存闸会拒，两处契约打架）→ 解析/提取统一改为无具体值不终止继承。
  回归 `mvn test` 387/387（+2 例钉死）。

## P-07 错题集与统计（2026-11-08，已执行）

- **生成/评分入口**：`cd backend && mvn test -Dtest=P07ErrorSetTest`（幂等，可重跑；产物覆盖写）
- **底本**：四份真实模板 .docx（Web开发/C语言/Oracle/JavaEE 转换件），走与生产同一链路 `TemplateRuleParser → RuleConfig → DocxStyleExtractor → FormatRuleMatcher`
- **注入方式**：POI 直改格式属性/文本——FONT 换 decoy 字体（按规则 expect 槽位选 eastAsia/ascii）、SIZE 期望+2pt、LINE_SPACING 段落显式单倍（期望≠1 时）、MARGIN 左边距 +1cm、CAPTION 仅题注样式段落改全角冒号；每条注入记入 ground truth（ruleId+类型+段号）
- **结果**：`expected=20，hits=20，recall=100.0%`（口径：漏报=命中/应命中 ≥95%）；族分布 FONT6/SIZE8/LINE_SPACING2/MARGIN4
- **产物**：`docs/test/fixtures/mutated/{4 份 .mutated.docx, groundtruth.json}`（逐条 hit 标记与 skipped 原因均在 json 内）
- **边界记录**：CAPTION 族无错题样本——四底本题注全为纯文本段（提取器题注 token 字符集是规则 pattern 的真子集，纯文本破坏必然脱 selector），按「不硬造」原则 skipped 入 json；题注识别正确性由 `FormatRuleMatcherTest` 单元例覆盖。首轮实跑另暴露第三处契约打架（JavaEE 件 pgMar top=0 产 `MARGIN.top=0` 撞保存闸 (0,100]），解析端已与保存闸同值域并加回归例
- **locate 常数校准依据**（Standards 判断风险二）：`AVG_PARA_CHARS=120` 取自错题集语料实测——Web开发正文段均字符 100~140；`AVG_PAGE_CHARS=2000` 为 A4 小四 1.5 倍行距经验页容量，块密度变化时在 `web/src/pages/view/locate.ts` 回调

## D-15 进度（2026-11-08 更新）

- [x] pdf2docx 已安装（`uv sync --extra convert`，2026-11-07）；JavaEE PDF→DOCX 完成并评估：版式类规则可用、页边距/字体不可用（见上表），该模板页边距走「人工提炼规则集」路线
- [x] **真实 PDF→DOCX 转换链路已跑通**（pdf2docx 引擎本体，D-06 的 PDF→DOCX 方向引擎级验证完成；HTTP 级见下）
- [x] **DDL 迁移已执行**：`ALTER TABLE edudoc_template_version ADD parse_error VARCHAR2(1000)` 已在 oracle-dev 真库落地（jshell+ojdbc11，凭据经环境变量注入不回显；幂等先查 ALL_TAB_COLUMNS）
- [x] **真库 IT 全绿**：Rbac 1/1 + Embedding 1/1 + 新增 `TemplateFormatSqlIntegrationTest` 4/4（parse_error 往返 / selectActive 30min 窗口+markRunning CAS / selectForUpdate+updateCurrentVersionCas / JSON_VALUE 段落序）。首跑即修复既有 IT 三处从未真跑的潜伏缺陷（autoCommit 下 commit 抛 ORA-17273、oss_object.content_hash NOT NULL、ck_doc_type/version_type 枚举）
- [x] **P-07 错题集 20/20 recall=100%**（见上节）
- [x] **ai-service `/v1/convert` 引擎级 HTTP 真实双向**（2026-11-08，uvicorn:8000 + `X-Internal-Token`）：DOCX→PDF 200/94.9KB/7.9s `%PDF-`；PDF→DOCX 200/37.9KB/0.6s `PK\x03\x04`；OLE 魔数件→`ENCRYPTED` 422；随机字节→`UNSUPPORTED` 415。**注意**：`EDUDOC_SOFFICE_BIN` 路径含空格，读取时须整行提取（早期 `awk $2` 误取 "REG_SZ" 导致 503「未安装 LibreOffice」）；50401 超时反例为 120s 级观测，由 `converter.py` 单元例覆盖，不在冒烟实跑
- [x] **三服务 HTTP 全链 e2e 全绿（2026-11-09 10:15–10:16 本地时间实测，脚本 `/tmp/edudoc-e2e/e2e04.py` 纯 stdlib）**：教师建模板→上传版本→解析 3 规则（FONT/SIZE/MARGIN 族齐）→发布 → 学生按模板新建（J4）→STS 直传错题集 docx→SHA-256 校验过 complete→解析 READY → 校验→问题清单含段落定位→勾选 4 条一键修复→自动重查 → DOCX→PDF 与 PDF→DOCX 双向转换 presigned 下载魔数验证 → J1 反例
- [ ] 浏览器端手工全链路（81 屏对照）随用户验收

### e2e 执行记录（2026-11-09，逐步 PASS 直至 ALL-E2E-PASS）

| 步骤 | 观测（UTC 落库时间=本地-8） |
| --- | --- |
| T-login / T-courses / T-template-create / T-version-upload / T-publish | code 200 全链通 |
| T-rules-parsed | `{FONT:1, SIZE:1, MARGIN:1}`（Web开发模板与语料冒烟节记录一致，3 规则） |
| J4-provision（学生按模板新建）| 建档→解析 READY→初验 `PASSED total=15 blocking=0`（生成大纲仅结构，无阻断） |
| S-oss-put / complete | STS 签名 PUT 成功返回 ETag；SHA-256 与后端 `sha256Of` 复核一致 |
| F-check（错题集初验）| `FAILED total=52 blocking=1`（5 注入 + 模板自身语料基线；MARGIN 为 BLOCKER）；清单含 paragraphIndex 定位行 ≥3 |
| F-fix（勾选 4 条 auto_fixable=Y）| 返回 `newVersionId`（EDIT 成版）+ `recheckId` 自动重查 |
| F-recheck（复审）| `PASSED total=48 blocking=0` —— **阻断 1→0、总数 52→48**，修复生效 |
| C-conv DOCX→PDF | worker→ai-service→OSS：SUCCEEDED 6s；presigned GET 下载 94KB+ `%PDF` 魔数 ✓ |
| X-pdf2docx PDF→DOCX | SUCCEEDED 1s；presigned 下载 `PK` 魔数 ✓（下载可打开性=魔数+字节数） |
| X-j1（PDF 无课程模板发起校验）| **40901 创建闸拒绝**：「文档不是 Word(.docx)格式, 且课程尚未发布模板, 无法发起格式校验」；任务内 ERROR 分支（有模板的 PDF 件）由 `FormatCheckWorkerTest` 覆盖 |

e2e 过程修正的脚本侧口径（均为正确契约的显式化，非产品缺陷）：成功码是 `code:200` 非 0；写接口一律携带 `Idempotency-Key`（含 multipart）；修复后重查必须复用 `FixView.recheckId`，二次手工 POST 会被 `selectActive` 三元组互斥拒绝（40901，设计内）。

### 验收决议增补实测（2026-11-09 12:4x，backend 重启后）

- 决议① `POST /templates/{id}/versions/preview`：Web开发件返回 `[FONT,SIZE,MARGIN]` 3 规则；同模板 `GET /versions` 仍 0 版本（**确认不落库**）；`t.pdf` 反例 40001「仅支持 .docx」。
- 决议④ `GET /documents/{id}/versions`：上午 e2e 修复件 4 行 `v4 EDIT current=True / v3 / v2 / v1 UPLOAD`（真库 selectListByDocument 降序），每行 presigned 900s，下载头 `PK\x03\x04`；非 owner 40401。
- 格式视图演进（两轮用户反馈）：v1 docx-preview 近似渲染（效果不佳）→ v2 iframe 加载 OSS 签名链（**实测默认域强制 attachment，`response-content-disposition=inline` 覆盖参数被云端策略忽略→弹下载不显示**）→ **v3 后端字节流+blob 内嵌（最终方案）**：`GET /conversions/{id}/preview`（PDF 产物）与 `GET /documents/{id}/versions/{vid}/file`（原件），带 Bearer 鉴权、20MB 护栏；前端 `getBlob→createObjectURL→iframe`，inline 显示与 CORS/域策略彻底解耦。**docx-preview 已移除**。HTTP 实测：preview 200 `Content-Disposition: inline` 魔数 `%PDF`；version-file 200 `%PDF`；匿名 401。旧产物按 `finishedAt≥doc.updateTime` 复用，修复/编辑后自动重转。FormatPane 5 例、后端 previewPdf 2 例+versionContent 2 例。
- fork 另存新版本 HTTP 实测（2026-11-09 16:1x）：v1 发布后 fork→v2 `VALID` 未发布、changeSummary「基于 v1 编辑另存」、**源对象引用沿用不复制**；v2 规则 `SIZE expect{pt:14} tolerance{pt:0.5} fix{pt:14}`（副本改动生效）；非法规则（pt=0）→ 40001 保存闸拦截且**版本数不变**；v1 发布态全程不动（UT-13）。
- 基线：mvn **408/408**、vitest **181/181**、build ✓、pytest 33/33。
