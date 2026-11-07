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

## D-15 待办（依赖用户环境动作）

- [x] pdf2docx 已安装（`uv sync --extra convert`，2026-11-07）；JavaEE PDF→DOCX 完成并评估：版式类规则可用、页边距/字体不可用（见上表），该模板页边距走「人工提炼规则集」路线
- [x] **真实 PDF→DOCX 转换链路已跑通**（pdf2docx 引擎本体，D-06 的 PDF→DOCX 方向引擎级验证完成；尚未过 HTTP 服务端到端）
- [ ] 注入 `EDUDOC_DB_USERNAME/EDUDOC_DB_PASSWORD` → 真库冒烟三条新 SQL（`FOR UPDATE` 行锁 / `JSON_VALUE` 排序 / 30 分钟窗口去重）+ 两个真库 IT
- [ ] 新终端启动三服务（`EDUDOC_SOFFICE_BIN`/`EDUDOC_AI_INTERNAL_TOKEN` 生效）→ 真实 DOCX↔PDF 双向转换 + 加密件反例 + 50401 超时
- [ ] 以四模板为底本生成错题集（POI 注入已知问题 + ground truth JSON）→ P-07 ≥95% 统计
- [ ] 端到端链路：上传模板→发布→按模板新建→校验→修复→复审→转换→下载
