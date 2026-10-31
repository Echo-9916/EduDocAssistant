const ROLE_KEY = document.body.dataset.role;

const icon = (name, className = "") =>
  `<i data-lucide="${name}" class="${className}" aria-hidden="true"></i>`;

const esc = (value) =>
  String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");

const badge = (label, tone = "", noDot = false) =>
  `<span class="badge ${tone} ${noDot ? "no-dot" : ""}">${esc(label)}</span>`;

function button(label, options = {}) {
  const {
    variant = "",
    iconName = "",
    action = "",
    message = "",
    extra = "",
    title = "",
    disabled = false,
  } = options;
  return `<button type="button" class="button ${variant}" ${action ? `data-action="${action}"` : ""} ${message ? `data-message="${esc(message)}"` : ""} ${title ? `title="${esc(title)}"` : ""} ${disabled ? "disabled" : ""} ${extra}>
    ${iconName ? icon(iconName) : ""}<span>${esc(label)}</span>
  </button>`;
}

function iconButton(iconName, options = {}) {
  const { label = "", action = "", message = "", variant = "", disabled = false } = options;
  return `<button type="button" class="icon-button ${variant}" aria-label="${esc(label)}" title="${esc(label)}" ${action ? `data-action="${action}"` : ""} ${message ? `data-message="${esc(message)}"` : ""} ${disabled ? "disabled" : ""}>
    ${icon(iconName)}
  </button>`;
}

function pageHead(route) {
  return `<header class="page-head">
    <div>
      <div class="breadcrumb">${esc(route.group)} / ${esc(route.title)}</div>
      <h1 class="page-title">${esc(route.title)}</h1>
      ${route.lead ? `<p class="page-lead">${esc(route.lead)}</p>` : ""}
    </div>
    ${route.actions ? `<div class="head-actions">${route.actions}</div>` : ""}
  </header>`;
}

function surface(title, body, options = {}) {
  const { iconName = "", meta = "", action = "", className = "" } = options;
  return `<section class="surface ${className}">
    <div class="surface-header">
      <div class="surface-title">${iconName ? icon(iconName) : ""}${esc(title)}</div>
      ${action || (meta ? `<span class="surface-meta">${action || esc(meta)}</span>` : "")}
    </div>
    <div class="surface-body">${body}</div>
  </section>`;
}

function statGrid(items) {
  return `<section class="stats" aria-label="关键指标">
    ${items
      .map(
        (item) => `<div class="stat" style="--stat-accent:${item.color || "var(--role-accent)"}">
          <div class="stat-label">${esc(item.label)}</div>
          <div class="stat-value">${esc(item.value)}</div>
          <div class="stat-note">${esc(item.note || "")}</div>
        </div>`,
      )
      .join("")}
  </section>`;
}

function listRows(items) {
  return `<div class="list compact">
    ${items
      .map(
        (item) => `<div class="list-row">
          ${item.iconName ? `<span class="row-icon ${item.tone || ""}">${icon(item.iconName)}</span>` : ""}
          <div class="row-copy">
            <strong>${item.title}</strong>
            <span>${item.meta}</span>
          </div>
          <div class="row-aside">${item.aside || ""}</div>
        </div>`,
      )
      .join("")}
  </div>`;
}

function progressRows(items) {
  return `<div class="staff-progress-list">
    ${items
      .map(
        (item) => `<div class="staff-progress-row">
          <div class="staff-progress-copy"><strong>${esc(item.label)}</strong><span>${esc(item.value)}</span></div>
          <div class="progress"><span style="width:${item.percent}%;background:${item.color || "var(--role-accent)"}"></span></div>
          <span class="surface-meta">${esc(item.note || "")}</span>
        </div>`,
      )
      .join("")}
  </div>`;
}

function filterBar(items, group, active = 0) {
  return `<div class="segmented" data-filter-group="${group}">
    ${items
      .map(
        (item, index) => `<button type="button" class="${index === active ? "is-active" : ""}" data-filter="${item.value}" data-action="filter">${esc(item.label)}</button>`,
      )
      .join("")}
  </div>`;
}

function dataTable(columns, rows, options = {}) {
  const { filterGroup = "", compact = false } = options;
  return `<div class="staff-table-wrap ${compact ? "is-compact" : ""}">
    <table class="table">
      <colgroup>${columns.map((column) => `<col style="width:${column.width}">`).join("")}</colgroup>
      <thead><tr>${columns.map((column) => `<th style="${column.align ? `text-align:${column.align}` : ""}">${esc(column.label)}</th>`).join("")}</tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `<tr data-row-state="${esc(row.state || "")}" ${filterGroup ? `data-filter-row="${filterGroup}"` : ""}>
              ${row.cells.map((cell, index) => `<td style="${columns[index]?.align ? `text-align:${columns[index].align}` : ""}">${cell}</td>`).join("")}
            </tr>`,
          )
          .join("")}
      </tbody>
    </table>
  </div>`;
}

function docCell(name, meta, iconName = "file-text") {
  return `<div class="staff-entity-cell"><span class="row-icon">${icon(iconName)}</span><div><strong>${esc(name)}</strong><span>${esc(meta)}</span></div></div>`;
}

function personCell(name, meta, initials = name.slice(0, 1)) {
  return `<div class="staff-entity-cell"><span class="avatar staff-avatar">${esc(initials)}</span><div><strong>${esc(name)}</strong><span>${esc(meta)}</span></div></div>`;
}

function formField(label, control, hint = "") {
  return `<label class="form-field"><span>${esc(label)}</span>${control}${hint ? `<small class="form-hint">${esc(hint)}</small>` : ""}</label>`;
}

function input(value, options = {}) {
  const { placeholder = "", type = "text", readonly = false } = options;
  return `<input class="input" type="${type}" value="${esc(value)}" placeholder="${esc(placeholder)}" ${readonly ? "readonly" : ""}>`;
}

function select(options, active = 0) {
  return `<select class="select">${options
    .map((option, index) => `<option ${index === active ? "selected" : ""}>${esc(option)}</option>`)
    .join("")}</select>`;
}

function textarea(value, rows = 4) {
  return `<textarea class="textarea" rows="${rows}">${esc(value)}</textarea>`;
}

function switchControl(on, label) {
  return `<button type="button" class="switch ${on ? "is-on" : ""}" data-action="toggle-switch" aria-label="${esc(label)}" aria-pressed="${on}"></button>`;
}

function settingRow(title, description, control) {
  return `<div class="setting-row"><div class="setting-copy"><strong>${esc(title)}</strong><span>${esc(description)}</span></div>${control}</div>`;
}

function notice(text, tone = "info", iconName = "info") {
  return `<div class="notice ${tone === "warning" ? "is-warning" : tone === "danger" ? "is-danger" : ""}">${icon(iconName)}<span>${esc(text)}</span></div>`;
}

function reviewList(items) {
  return `<div class="review-list">${items
    .map(
      (item) => `<div class="review-item ${item.selected ? "is-selected" : ""}">
        <span class="check">${item.selected ? icon("check") : ""}</span>
        <span class="review-copy"><strong>${esc(item.title)}</strong><span>${esc(item.meta)}</span></span>
        ${item.aside || ""}
      </div>`,
    )
    .join("")}</div>`;
}

function panel(title, body, options = {}) {
  const { iconName = "", meta = "", action = "", className = "", span = 1 } = options;
  return `<section class="surface staff-panel ${className}" style="grid-column:span ${span}">
    <div class="surface-header">
      <div class="surface-title">${iconName ? icon(iconName) : ""}${esc(title)}</div>
      ${action || (meta ? `<span class="surface-meta">${action || esc(meta)}</span>` : "")}
    </div>
    <div class="surface-body">${body}</div>
  </section>`;
}

function panelGrid(panels) {
  if (!panels?.length) return "";
  return `<div class="staff-panel-grid">${panels
    .map((item) => panel(item.title, item.body, item))
    .join("")}</div>`;
}

function modalMarkup(modal) {
  if (!modal) return "";
  return `<div class="modal-scrim" data-modal-scrim>
    <section class="modal ${modal.wide ? "is-wide" : ""} ${modal.className || ""}" role="dialog" aria-modal="true" aria-labelledby="staff-modal-title">
      <div class="modal-header">
        <div><h2 class="modal-title" id="staff-modal-title">${esc(modal.title)}</h2><p class="modal-subtitle">${esc(modal.subtitle || "")}</p></div>
        ${iconButton("x", { label: "关闭", action: "close-modal" })}
      </div>
      <div class="modal-body">${modal.body}</div>
      ${modal.footer ? `<div class="modal-footer">${modal.footer}</div>` : ""}
    </section>
  </div>`;
}

function navItems(role, currentRoute) {
  return role.nav
    .map(
      (item) => `<button type="button" class="nav-item ${currentRoute.nav === item.key ? "is-active" : ""}" ${currentRoute.nav === item.key ? 'aria-current="page"' : ""}>
        ${icon(item.icon)}<span>${esc(item.label)}</span>
      </button>`,
    )
    .join("");
}

function topbar(role, route) {
  return `<header class="topbar">
    <div class="search-box" role="search">
      ${icon("search")}<span>${esc(role.search)}</span><kbd>Ctrl K</kbd>
    </div>
    <div class="topbar-spacer"></div>
    <span class="role-chip">${esc(role.workspace)}</span>
    <div class="top-divider"></div>
    ${iconButton("bell", { label: role.messageLabel, action: "toast", message: "当前页面的消息入口已打开。" })}
    <div class="top-person">
      <span class="avatar">${esc(role.initial)}</span>
      <div class="top-person-copy"><strong>${esc(role.user)}</strong><span>${esc(role.status)}</span></div>
    </div>
    <span class="sr-only">${esc(route.title)}</span>
  </header>`;
}

function shell(role, route, content, modal = "") {
  return `<div class="app-shell staff-shell role-${role.key}">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"><img src="./assets/logo-mark.png" alt="校园智能文档助手 Logo"></span>
        <div class="brand-copy"><strong>校园智能文档助手</strong><span>EduDocAssistant</span></div>
      </div>
      <div class="nav-section-label">${esc(role.workspace)}</div>
      <nav class="primary-nav" aria-label="${esc(role.workspace)}主导航">${navItems(role, route)}</nav>
      <div class="sidebar-footer">
        <div class="sidebar-rule"></div>
        <div class="identity">
          <span class="avatar">${esc(role.initial)}</span>
          <div class="identity-copy"><strong>${esc(role.user)}</strong><span>${esc(role.identity)}</span></div>
        </div>
      </div>
    </aside>
    <div class="workspace">${topbar(role, route)}<main class="main">${content}</main></div>
    ${modal}
  </div>`;
}

const commonColumns = {
  document: { label: "文档", width: "34%" },
  course: { label: "课程", width: "17%" },
  status: { label: "状态", width: "14%" },
  update: { label: "更新时间", width: "15%" },
  owner: { label: "负责人", width: "12%" },
  action: { label: "操作", width: "8%", align: "right" },
};

const teacherRoutes = [
  {
    id: "workbench",
    title: "教学工作台",
    group: "教学总览",
    nav: "workbench",
    lead: "汇总课程、模板、作业和文档协作状态，优先处理临近截止的批改与发布任务。",
    actions: `${button("新建教学文档", { variant: "primary", iconName: "file-plus-2", action: "toast", message: "已打开新建教学文档入口。" })}`,
    stats: [
      { label: "本周课程", value: "6", note: "3 门课程有新增提交", color: "#0f766e" },
      { label: "待批改作业", value: "42", note: "最晚明天 18:00", color: "#b45309" },
      { label: "已发布模板", value: "8", note: "2 个待更新", color: "#2563eb" },
      { label: "格式通过率", value: "96%", note: "较上周 +4%", color: "#15803d" },
    ],
    panels: [
      {
        title: "今日优先事项",
        iconName: "list-checks",
        span: 1,
        body: listRows([
          { iconName: "clipboard-check", title: "批改 Web 开发课程设计", meta: "3 个小组待评分 · 截止明天 18:00", aside: badge("高优先", "warning"), tone: "is-warning" },
          { iconName: "file-badge", title: "更新 JavaEE 作业模板", meta: "模板 v2.1 需要同步课程成员", aside: badge("待发布", "info"), tone: "" },
          { iconName: "message-square-text", title: "回复小组协作批注", meta: "JavaEE课程作业_小组版.docx · 2 条新批注", aside: badge("未读", "violet"), tone: "is-violet" },
          { iconName: "shield-alert", title: "复核格式例外申请", meta: "Oracle课程设计_张同学_v2.docx", aside: badge("待处理", "warning"), tone: "is-warning" },
        ]),
      },
      {
        title: "课程运行状态",
        iconName: "activity",
        span: 1,
        body: `${progressRows([
          { label: "Web开发技术", value: "28 / 32 份已提交", percent: 88, note: "批改 64%" },
          { label: "JavaEE课程设计", value: "23 / 28 份已提交", percent: 82, note: "批改 38%" },
          { label: "Oracle数据库应用", value: "31 / 35 份已提交", percent: 89, note: "批改 77%" },
        ])}${notice("两个课程将在 48 小时内截止，请优先完成评分标准确认。", "warning", "clock-3")}`,
      },
      {
        title: "最近协作文档",
        iconName: "files",
        span: 2,
        body: listRows([
          { iconName: "file-text", title: "Web开发课程设计报告_优秀样例.docx", meta: "课程模板组 · 孙老师更新于 10:32", aside: `${badge("已共享", "success")}${button("查看", { small: true, variant: "ghost", action: "toast", message: "样例文档预览已打开。" })}` },
          { iconName: "file-check-2", title: "JavaEE课程作业_小组版.docx", meta: "3 人协作 · 2 条教师批注待回复", aside: `${badge("协作中", "info")}${button("处理批注", { small: true, action: "toast", message: "已定位到待回复批注。" })}` },
        ]),
      },
    ],
  },
  {
    id: "documents",
    title: "我的教学文档",
    group: "文档接入",
    nav: "documents",
    lead: "管理本人创建和课程共享的教学文档，按课程、协作状态与格式风险筛选。",
    actions: `${button("上传教学文档", { variant: "primary", iconName: "upload", action: "toast", message: "上传教学文档入口已打开。" })}`,
    stats: [
      { label: "教学文档", value: "128", note: "本人 76 / 共享 52", color: "#0f766e" },
      { label: "协作中", value: "17", note: "3 份今日有更新", color: "#2563eb" },
      { label: "待修复", value: "4", note: "阻断问题 2 项", color: "#b45309" },
      { label: "存储占用", value: "386MB", note: "团队空间 38%", color: "#0e7490" },
    ],
    panels: [
      {
        title: "教学文档",
        iconName: "files",
        span: 2,
        body: `${filterBar([{ label: "全部 128", value: "all" }, { label: "我创建的", value: "owned" }, { label: "共享给我", value: "shared" }, { label: "待修复", value: "issue" }], "teacher-docs")}
          ${dataTable(
            [commonColumns.document, commonColumns.course, commonColumns.owner, commonColumns.status, commonColumns.update, commonColumns.action],
            [
              {
                state: "owned",
                cells: [docCell("Web开发课程设计模板_v2.1.docx", "DOCX · 3.8MB"), "Web开发技术", "孙老师", badge("已发布", "success"), "今天 10:32", button("打开", { small: true, variant: "ghost", action: "toast", message: "教学文档已打开。" })],
              },
              {
                state: "shared",
                cells: [docCell("JavaEE课程作业_小组版.docx", "DOCX · 2.6MB"), "JavaEE课程设计", "李同学", badge("协作中", "info"), "今天 09:48", button("批注", { small: true, variant: "ghost", action: "toast", message: "已打开文档批注。" })],
              },
              {
                state: "issue",
                cells: [docCell("Oracle课程设计_评分说明.docx", "DOCX · 1.9MB"), "Oracle数据库应用", "孙老师", badge("2 项待修复", "warning"), "昨天 20:16", button("修复", { small: true, action: "toast", message: "格式问题定位已打开。" })],
              },
              {
                state: "shared",
                cells: [docCell("课程设计报告_批注汇总.docx", "DOCX · 840KB"), "Web开发技术", "王同学", badge("可编辑", "success"), "昨天 17:03", button("打开", { small: true, variant: "ghost", action: "toast", message: "文档已打开。" })],
              },
              {
                state: "owned",
                cells: [docCell("软件工程期末评分标准.docx", "DOCX · 620KB"), "软件工程", "孙老师", badge("草稿", ""), "10月25日", button("编辑", { small: true, variant: "ghost", action: "toast", message: "评分标准已打开。" })],
              },
            ],
            { filterGroup: "teacher-docs", compact: true },
          )}
          <div class="surface-footer"><span class="surface-meta">显示 5 / 128 份文档</span><span class="surface-meta">最近同步：今天 11:08</span></div>`,
      },
    ],
  },
  {
    id: "upload",
    title: "上传教学文档",
    group: "文档接入",
    nav: "documents",
    lead: "绑定课程与用途，选择解析和权限策略。上传后可在当前页查看校验与处理状态。",
    actions: button("返回文档列表", { iconName: "arrow-left", action: "toast", message: "当前为静态单页，页面间不跳转。" }),
    panels: [
      {
        title: "选择文件",
        iconName: "upload-cloud",
        body: `<div class="dropzone" data-action="toast" data-message="文件选择器已触发"><div>${icon("upload-cloud")}<strong>拖拽课程文档到这里</strong><span>支持 DOCX、PDF、PPTX，单文件不超过 100MB</span>${button("选择文件", { small: true, action: "toast", message: "已选择示例文件。" })}</div></div>
          <div class="selected-file">
            <span class="row-icon">${icon("file-text")}</span>
            <div class="row-copy"><strong>Web开发课程设计模板_v2.2.docx</strong><span>3.8MB · 上传完成 · 风险检查通过</span></div>
            ${badge("可上传", "success")}
            ${iconButton("x", { label: "移除文件", action: "toast", message: "已移除待上传文件。" })}
          </div>
          <div class="upload-progress"><span style="width:100%"></span></div>
          <div class="inline-note">${icon("info")}教师文档默认执行模板格式检查，并在发布前生成可追溯版本。</div>`,
      },
      {
        title: "文档设置",
        iconName: "sliders-horizontal",
        body: `<div class="form-grid">
          ${formField("所属课程", select(["Web开发技术", "JavaEE课程设计", "Oracle数据库应用"]))}
          ${formField("文档用途", select(["课程模板", "教学资料", "作业样例", "评分标准"]))}
          ${formField("适用范围", select(["本学期课程", "课程组共享", "课程成员可见"]))}
          ${formField("关联模板", select(["Web开发课程设计模板 v2.1", "不关联模板"]))}
        </div>
        ${settingRow("启用代码与图表规范化", "解析代码块、图表编号与题注。", switchControl(true, "启用代码与图表规范化"))}
        ${settingRow("完成后生成版本快照", "保留本次上传和处理结果的完整记录。", switchControl(true, "完成后生成版本快照"))}
        ${notice("“可上传”表示文件已通过类型、大小和重复文件检查。", "info", "badge-check")}`,
      },
      {
        title: "处理队列",
        iconName: "list-restart",
        span: 2,
        body: dataTable(
          [
            { label: "文件", width: "42%" },
            { label: "用途", width: "18%" },
            { label: "解析状态", width: "18%" },
            { label: "格式检查", width: "14%" },
            { label: "操作", width: "8%", align: "right" },
          ],
          [
            { cells: [docCell("Web开发课程设计模板_v2.2.docx", "3.8MB · 128 页"), "课程模板", badge("解析完成", "success"), badge("通过", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "处理详情已打开。" })] },
            { cells: [docCell("JavaEE评分标准_补充版.docx", "640KB · 18 页"), "评分标准", badge("处理中 72%", "info"), badge("待检查", ""), button("查看", { small: true, variant: "ghost", action: "toast", message: "处理详情已打开。" })] },
          ],
        ),
      },
    ],
  },
  {
    id: "preview",
    title: "文档预览",
    group: "文档接入",
    nav: "documents",
    lead: "检查解析结果、文档结构与教学批注，并进入智能处理或格式检查。",
    actions: `${button("共享设置", { iconName: "share-2", action: "toast", message: "共享设置面板已打开。" })}${button("开始格式校验", { variant: "primary", iconName: "shield-check", action: "toast", message: "格式校验任务已创建。" })}`,
    content: () => `<div class="preview-layout staff-doc-preview">
      <aside class="surface"><div class="surface-header"><div class="surface-title">${icon("list-tree")}文档目录</div><span class="surface-meta">24 页</span></div>
        <div class="outline-list">${["摘要与任务说明", "课程目标与成果", "需求分析", "系统设计", "评分标准"].map((title, index) => `<button type="button" class="outline-item ${index === 2 ? "is-active" : ""}" data-action="toast" data-message="已定位到 ${title}。"><span>${String(index + 1).padStart(2, "0")}</span>${title}</button>`).join("")}</div>
      </aside>
      <section class="surface preview-panel"><div class="editor-toolbar">${iconButton("minus", { label: "缩小" })}<span class="badge no-dot">100%</span>${iconButton("plus", { label: "放大" })}<span class="surface-meta">第 8 / 24 页</span></div>
        <div class="paper-page"><div class="paper-meta">Web开发技术 · 课程设计模板</div><h2 class="paper-title">三、系统设计</h2><p class="paper-subtitle">系统架构与权限校验</p><p>系统采用前端、服务端与文档处理服务分层设计。所有文档访问请求在服务端完成课程身份与共享范围校验，并记录关键操作日志。</p><div class="figure-box">${icon("workflow")}<span>图 3-1 系统总体架构</span></div><p>文档解析、智能摘要与格式检查通过异步任务执行，任务状态与版本信息写入文档元数据。</p></div>
      </section>
      <aside class="stack"><section class="surface"><div class="surface-header"><div class="surface-title">${icon("sparkles")}AI 阅读助手</div></div><div class="surface-body"><p class="staff-muted">已识别 5 个章节、3 张图表和 12 个代码块。</p><div class="staff-mini-grid">${button("生成摘要", { small: true, iconName: "scan-text", action: "toast", message: "摘要已生成。" })}${button("提取要点", { small: true, iconName: "list-checks", action: "toast", message: "要点已提取。" })}${button("文档问答", { small: true, iconName: "message-circle-question", action: "toast", message: "问答面板已打开。" })}${button("文本润色", { small: true, iconName: "wand-sparkles", action: "toast", message: "润色建议已生成。" })}</div></div></section>
      <section class="surface"><div class="surface-header"><div class="surface-title">${icon("message-square-text")}教学批注</div>${badge("2 条", "violet")}</div><div class="list compact">${listRows([{ title: "补充权限校验", meta: "孙老师 · 系统设计 / 第 8 页", aside: badge("待回复", "warning"), tone: "is-warning" }, { title: "评分点需要量化", meta: "孙老师 · 评分标准 / 第 22 页", aside: badge("已解决", "success"), tone: "is-success" }])}</div></section></aside>
    </div>`,
  },
  {
    id: "summary",
    title: "文本摘要",
    group: "智能处理",
    nav: "smart",
    lead: "为教学文档生成可引用的结构化摘要，并保留原文出处供批注和评分使用。",
    actions: `${button("复制摘要", { iconName: "copy", action: "toast", message: "摘要已复制。" })}${button("重新生成", { variant: "primary", iconName: "refresh-cw", action: "toast", message: "正在根据最新设置重新生成。" })}`,
    panels: [
      {
        title: "摘要设置",
        iconName: "sliders-horizontal",
        body: `<div class="stack compact">${formField("摘要用途", select(["教师备课参考", "评分要点", "课程组共享"]))}${formField("摘要长度", select(["精简", "标准", "详细"], 1))}${formField("关注重点", select(["课程目标与评分点", "结构与论证", "格式与规范"]))}${formField("补充要求", textarea("突出可量化的评分标准，并保留章节引用。", 4))}</div>
        ${settingRow("保留原文引用", "每个结论附带章节与页码。", switchControl(true, "保留原文引用"))}
        ${settingRow("自动识别评分点", "将要求转换为可勾选的评分条目。", switchControl(true, "自动识别评分点"))}
        <button type="button" class="button primary is-block" data-action="toast" data-message="摘要已按新设置生成。">${icon("sparkles")}<span>生成摘要</span></button>`,
      },
      {
        title: "摘要结果",
        iconName: "scan-text",
        meta: `${badge("已生成", "success")} · 1,286 字`,
        body: `<div class="result-summary"><strong>核心结论</strong><p>文档围绕 Web 开发课程设计，明确了系统架构、权限校验、文档处理链路与评分标准。设计要求学生提交可追溯的版本，并在提交前完成格式校验。</p></div>
        <div class="result-section"><h3>${icon("key-round")}评分关注点</h3><ul><li>系统设计需覆盖课程身份与共享范围校验。</li><li>关键任务需保留异步处理状态与版本记录。</li><li>格式要求包含图表编号、代码块和参考文献。</li></ul></div>
        <div class="citation">${icon("quote")}系统通过统一文档接入、智能处理、格式校验和版本协作，帮助学生更高效地完成课程报告。<span>第 4 页 · 项目目标</span></div>
        <div class="surface-footer"><span class="surface-meta">基于模板 v2.1 · 今天 11:18</span><span>${button("导出摘要", { small: true, variant: "ghost", iconName: "download", action: "toast", message: "摘要已导出。" })}</span></div>`,
      },
    ],
  },
  {
    id: "keypoints",
    title: "要点提取",
    group: "智能处理",
    nav: "smart",
    lead: "将文档要求转化为可勾选的教学检查项，用于备课、批注和评分标准核对。",
    actions: `${button("全选要点", { iconName: "list-checks", action: "toast", message: "已全选 6 个要点。" })}${button("导出检查清单", { variant: "primary", iconName: "download", action: "toast", message: "检查清单已导出。" })}`,
    panels: [
      {
        title: "提取结果",
        iconName: "list-checks",
        span: 1,
        body: reviewList([
          { title: "系统架构说明完整", meta: "需包含前端、服务端和文档处理任务链路。", selected: true, aside: badge("评分 15 分", "info") },
          { title: "权限校验流程可追溯", meta: "覆盖课程身份、共享范围和越权拒绝结果。", selected: true, aside: badge("评分 15 分", "info") },
          { title: "格式规范满足模板要求", meta: "检查字体、行距、题注、代码块和参考文献。", selected: true, aside: badge("评分 20 分", "info") },
          { title: "版本与提交记录完整", meta: "保留每次提交、校验结果和恢复记录。", selected: true, aside: badge("评分 10 分", "info") },
          { title: "测试结论有数据支撑", meta: "包含并发验证、异常处理与结果说明。", selected: false, aside: badge("待补充", "warning") },
        ]),
      },
      {
        title: "教学检查清单",
        iconName: "clipboard-check",
        body: `<div class="result-section"><h3>${icon("target")}覆盖度</h3><p>当前样例覆盖 5 / 6 个关键评分点，测试结论仍需补充量化数据。</p><div class="score-ring"><strong>83%</strong><span>评分点覆盖</span></div></div>
        ${settingRow("显示原文依据", "每个要点保留对应章节引用。", switchControl(true, "显示原文依据"))}
        ${notice("建议在批改反馈中引用第 5 项，并附上可量化的测试结果要求。", "warning", "lightbulb")}
        ${button("生成批改意见", { variant: "primary", iconName: "message-square-text", action: "toast", message: "批改意见草稿已生成。" })}`,
      },
    ],
  },
  {
    id: "qa",
    title: "文档问答",
    group: "智能处理",
    nav: "smart",
    lead: "围绕当前教学文档提问，回答仅引用文档内有依据的章节。",
    actions: `${button("清除对话", { iconName: "rotate-ccw", action: "toast", message: "当前页面对话已清除。" })}${button("导出对话", { variant: "primary", iconName: "download", action: "toast", message: "问答记录已导出。" })}`,
    content: () => `<div class="chat-layout staff-chat">
      <section class="surface chat-panel"><div class="surface-header"><div class="surface-title">${icon("message-circle-question")}与教学文档对话</div>${badge("引用可追溯", "success")}</div>
        <div class="chat-thread"><div class="message user"><strong>孙老师</strong><p>这份模板对系统设计章节有哪些评分要求？</p></div><div class="message assistant"><strong>${icon("sparkles")}文档助手</strong><p>系统设计章节要求说明总体架构、权限校验与异步任务链路，并给出对应图示。评分重点包括架构分层是否清晰、权限校验是否可追溯，以及版本与处理状态是否完整。</p><div class="citation">${icon("quote")}第 7–10 页 · 系统设计</div></div><div class="message user"><strong>孙老师</strong><p>还缺少哪些可量化的评分标准？</p></div><div class="message assistant"><strong>${icon("sparkles")}文档助手</strong><p>建议补充并发测试规模、异常处理覆盖率、格式校验通过率和版本恢复验证结果。当前文档只描述了目标，没有给出明确的量化阈值。</p></div></div>
        <div class="chat-compose">${input("继续询问评分标准、章节要求或文档依据…")}${button("发送", { variant: "primary", iconName: "send", action: "toast", message: "已根据当前文档生成回答。" })}</div>
      </section>
      <aside class="stack"><section class="surface"><div class="surface-header"><div class="surface-title">${icon("files")}引用来源</div><span class="surface-meta">3 个章节</span></div><div class="list compact">${listRows([{ iconName: "file-text", title: "系统设计", meta: "第 7–10 页 · 匹配度 96%", aside: button("查看", { small: true, variant: "ghost", action: "toast", message: "已定位到系统设计章节。" }) }, { iconName: "list-checks", title: "评分标准", meta: "第 19–22 页 · 匹配度 91%", aside: button("查看", { small: true, variant: "ghost", action: "toast", message: "已定位到评分标准章节。" }) }, { iconName: "bug", title: "系统测试", meta: "第 15 页 · 匹配度 78%", aside: button("查看", { small: true, variant: "ghost", action: "toast", message: "已定位到系统测试章节。" }) }])}</div></section>
      <section class="surface"><div class="surface-header"><div class="surface-title">${icon("shield-check")}回答边界</div></div><div class="surface-body">${notice("仅回答文档内有依据的内容；无依据问题会明确拒答，并提供可补充的依据入口。", "info", "shield-check")}${settingRow("显示页码与章节", "保留引用位置，便于批阅和核对。", switchControl(true, "显示页码与章节"))}</div></section></aside>
    </div>`,
  },
  {
    id: "polish",
    title: "文本润色",
    group: "智能处理",
    nav: "smart",
    lead: "在保留原意和引用关系的前提下优化教学说明，逐段比较并选择性采纳。",
    actions: `${button("还原修改", { iconName: "undo-2", action: "toast", message: "已恢复到润色前文本。" })}${button("采纳全部", { variant: "primary", iconName: "check-check", action: "toast", message: "全部润色建议已采纳。" })}`,
    panels: [
      {
        title: "原文",
        iconName: "file-text",
        body: `<div class="text-panel staff-text-panel"><p>学生需要按照模板提交课程报告。报告要有系统设计、权限说明和测试结果。格式要正确，提交之前要检查。</p><p>老师可以根据报告的内容评分，也可以留下反馈。学生修改之后可以再次提交。</p></div><div class="surface-footer"><span class="surface-meta">第 4 页 · 教学说明</span>${badge("原意锁定", "info")}</div>`,
      },
      {
        title: "建议润色",
        iconName: "wand-sparkles",
        body: `<div class="text-panel staff-text-panel"><p>学生须依据课程模板提交报告，并完整说明系统设计、权限校验与测试结果。提交前应完成格式检查，确保图表、代码块和参考文献符合规范。</p><p>教师可依据报告内容开展评分并填写反馈；学生修订后可重新提交，系统将保留每次提交与校验记录。</p></div><div class="staff-mini-grid">${button("逐段采纳", { small: true, action: "toast", message: "已采纳当前段落建议。" })}${button("复制结果", { small: true, iconName: "copy", action: "toast", message: "润色文本已复制。" })}${button("加入批注", { small: true, iconName: "message-square-plus", action: "toast", message: "已加入教学批注。" })}</div><div class="surface-footer"><span class="surface-meta">已优化 2 段 · 保留全部事实与引用</span>${badge("建议已生成", "success", true)}</div>`,
      },
    ],
  },
  {
    id: "templates",
    title: "课程模板库",
    group: "格式与模板",
    nav: "templates",
    lead: "维护课程模板、格式规则和版本状态，并控制模板在学生端的可用范围。",
    actions: `${button("导入 Word 模板", { iconName: "file-up", action: "toast", message: "模板导入入口已打开。" })}${button("新建模板", { variant: "primary", iconName: "file-plus-2", action: "toast", message: "新建模板编辑器已打开。" })}`,
    stats: [
      { label: "课程模板", value: "12", note: "已发布 8 个", color: "#0f766e" },
      { label: "使用课程", value: "6", note: "覆盖 3 个学期", color: "#2563eb" },
      { label: "待更新", value: "2", note: "格式规范发生变化", color: "#b45309" },
      { label: "模板通过率", value: "94%", note: "本周提升 3%", color: "#15803d" },
    ],
    panels: [
      {
        title: "模板列表",
        iconName: "layout-template",
        span: 2,
        body: `${filterBar([{ label: "全部 12", value: "all" }, { label: "已发布", value: "published" }, { label: "草稿", value: "draft" }, { label: "待更新", value: "update" }], "teacher-templates")}
        <div class="template-grid staff-template-grid">
          ${[
            ["Web开发课程设计模板", "v2.1 · 2026 秋", "已发布", "success", "32 名学生使用"],
            ["JavaEE课程作业模板", "v2.4 · 2026 秋", "已发布", "success", "28 名学生使用"],
            ["Oracle课程设计模板", "v1.8 · 2026 春", "待更新", "warning", "评分标准有变更"],
            ["软件工程期末报告模板", "v1.2 · 草稿", "草稿", "", "尚未发布"],
          ]
            .map(
              ([name, version, status, tone, note]) => {
                const rowState =
                  status === "已发布"
                    ? "published"
                    : status === "待更新"
                      ? "update"
                      : "draft";
                return `<article class="template-card" data-filter-row="teacher-templates" data-row-state="${rowState}">
                <div class="template-icon">${icon("layout-template")}</div>
                <div class="template-copy"><strong>${name}</strong><span>${version}</span></div>
                ${badge(status, tone)}
                <p>${note}</p>
                <div class="template-actions">${button("编辑", { small: true, variant: "ghost", action: "toast", message: "模板编辑器已打开。" })}${button("更多", { small: true, variant: "link", action: "toast", message: "模板操作菜单已打开。" })}</div>
              </article>`;
              },
            )
            .join("")}
        </div>`,
      },
    ],
  },
  {
    id: "template-editor",
    title: "编辑课程模板",
    group: "格式与模板",
    nav: "templates",
    lead: "调整章节结构、格式规则和评分要求，右侧预览会同步展示学生端效果。",
    actions: `${button("保存草稿", { iconName: "save", action: "toast", message: "模板草稿已保存。" })}${button("发布新版本", { variant: "primary", iconName: "send", action: "toast", message: "发布确认已打开。" })}`,
    content: () => `<div class="editor-shell">
      <section class="surface"><div class="editor-toolbar">${iconButton("undo-2", { label: "撤销" })}${iconButton("redo-2", { label: "重做" })}<span class="editor-toolbar-spacer"></span><span class="surface-meta">模板 v2.2 · 自动保存</span>${badge("草稿", "")}</div>
        <div class="template-editor-layout"><aside class="editor-sidebar"><div class="surface-meta">章节结构</div>${["封面与目录", "课程目标", "需求分析", "系统设计", "系统实现", "测试与结论", "参考文献"].map((item, index) => `<button type="button" class="outline-item ${index === 3 ? "is-active" : ""}" data-action="toast" data-message="已定位到 ${item}。"><span>${index + 1}</span>${item}</button>`).join("")}</aside>
        <div class="editor-paper staff-editor-paper"><div class="paper-meta">Web开发技术 · 课程设计模板 v2.2</div><h2 class="paper-title">三、系统设计</h2><p class="paper-subtitle">评分权重 20% · 必填章节</p><div class="staff-editor-block"><strong>3.1 总体架构</strong><p>说明系统前端、服务端、数据层与文档处理任务之间的关系，并至少提供一张总体架构图。</p></div><div class="staff-editor-block"><strong>3.2 权限校验</strong><p>说明课程身份、共享范围与越权拒绝流程，并给出异常处理结果。</p></div><div class="staff-editor-hint">${icon("plus")}添加章节内容或评分说明</div></div></div>
      </section>
      <aside class="stack"><section class="surface"><div class="surface-header"><div class="surface-title">${icon("settings-2")}章节属性</div></div><div class="surface-body"><div class="stack compact">${formField("章节名称", input("系统设计"))}${formField("评分权重", input("20%"))}${formField("完成要求", select(["必填章节", "选填章节"], 0))}${formField("评分说明", textarea("架构分层清晰，权限校验可追溯，图示与正文一致。", 3))}</div></div></section>
      <section class="surface"><div class="surface-header"><div class="surface-title">${icon("list-checks")}格式规则</div></div><div class="surface-body">${reviewList([{ title: "一级标题：黑体 16pt", meta: "段前 12pt / 段后 6pt", selected: true }, { title: "正文：宋体 12pt", meta: "1.5 倍行距", selected: true }, { title: "图表题注：居中", meta: "图 3-1 格式", selected: true }])}</div></section></aside>
    </div>`,
  },
  {
    id: "template-publish",
    title: "发布模板确认",
    group: "格式与模板",
    nav: "templates",
    lead: "确认版本、适用范围和变更说明。发布后将同步到关联课程的学生端。",
    content: () => `<div class="staff-modal-preview"><div class="staff-dimmed-library">${["Web开发课程设计模板", "JavaEE课程作业模板", "Oracle课程设计模板"].map((name, index) => `<div class="template-card"><div class="template-icon">${icon("layout-template")}</div><div class="template-copy"><strong>${name}</strong><span>${index === 0 ? "v2.2 · 待发布" : "已发布"}</span></div>${badge(index === 0 ? "待发布" : "已发布", index === 0 ? "warning" : "success")}</div>`).join("")}</div></div>`,
    modal: {
      title: "发布 Web开发课程设计模板 v2.2",
      subtitle: "本版本包含章节结构与格式规则更新，共影响 32 名学生。",
      wide: true,
      body: `<div class="form-grid">${formField("版本名称", input("v2.2 · 2026 秋季版"))}${formField("适用课程", select(["Web开发技术", "全部课程"]))}${formField("生效时间", input("2026-10-29 08:00", { type: "text" }))}${formField("通知方式", select(["站内消息 + 邮件", "仅站内消息"]))}</div>
        ${formField("版本说明", textarea("更新系统设计章节评分点，补充权限校验和异步任务要求；统一图表题注格式。", 4))}
        ${reviewList([{ title: "章节完整性检查", meta: "7 / 7 个必填章节已配置", selected: true }, { title: "格式规则检查", meta: "字体、行距、题注与参考文献规则通过", selected: true }, { title: "学生端兼容检查", meta: "模板预览与新建文档流程通过", selected: true }])}
        ${notice("发布后旧版本仍可用于已提交作业，新版本从生效时间起对学生可见。", "info", "history")}`,
      footer: `${button("取消", { action: "close-modal" })}${button("确认发布", { variant: "primary", iconName: "send", action: "toast", message: "模板 v2.2 已发布。" })}`,
    },
  },
  {
    id: "format-check",
    title: "格式规范校验",
    group: "格式与模板",
    nav: "format",
    lead: "按课程模板检查字体、字号、行距、页边距、题注、代码块和参考文献。",
    actions: `${button("导出校验报告", { iconName: "download", action: "toast", message: "校验报告已导出。" })}${button("重新校验", { variant: "primary", iconName: "refresh-cw", action: "toast", message: "已开始重新校验。" })}`,
    stats: [
      { label: "已检查项目", value: "36", note: "覆盖 7 类规则", color: "#0e7490" },
      { label: "阻断问题", value: "2", note: "必须修复", color: "#b91c1c" },
      { label: "警告项", value: "4", note: "可由教师确认", color: "#b45309" },
      { label: "格式得分", value: "86", note: "模板要求 90 分", color: "#2563eb" },
    ],
    panels: [
      {
        title: "检查结果",
        iconName: "shield-alert",
        span: 1,
        body: dataTable(
          [{ label: "问题", width: "42%" }, { label: "位置", width: "16%" }, { label: "级别", width: "16%" }, { label: "修复建议", width: "26%" }],
          [
            { cells: ["图表题注缺失", "第 9 页", badge("阻断", "danger"), "补充图 3-1 与图 3-2 编号"] },
            { cells: ["代码块字体不一致", "第 12 页", badge("阻断", "danger"), "统一为 Consolas 10pt"] },
            { cells: ["参考文献格式不一致", "第 22 页", badge("警告", "warning"), "按 GB/T 7714 调整"] },
            { cells: ["标题段前距偏差 2pt", "第 4 页", badge("警告", "warning"), "应用模板段落样式"] },
            { cells: ["页眉课程名称缺失", "全文", badge("警告", "warning"), "应用课程页眉模板"] },
          ],
          { compact: true },
        ),
      },
      {
        title: "规则概览",
        iconName: "ruler",
        body: `${progressRows([{ label: "字体与字号", value: "8 / 8 通过", percent: 100, color: "#15803d" }, { label: "段落与行距", value: "6 / 8 通过", percent: 75, color: "#b45309" }, { label: "图表与题注", value: "3 / 5 通过", percent: 60, color: "#b91c1c" }, { label: "参考文献", value: "4 / 5 通过", percent: 80, color: "#2563eb" }])}
        ${notice("阻断问题修复后，警告项可提交教师例外说明。", "warning", "info")}
        ${button("查看异常详情", { variant: "primary", iconName: "list-filter", action: "toast", message: "已筛选需要处理的格式问题。" })}`,
      },
    ],
  },
  {
    id: "autofix",
    title: "一键修复确认",
    group: "格式与模板",
    nav: "format",
    lead: "选择要自动修复的格式问题，确认影响范围后生成新的文档版本。",
    content: () => `<div class="staff-modal-preview">${surface(
      "格式检查结果",
      dataTable(
        [{ label: "问题", width: "44%" }, { label: "位置", width: "16%" }, { label: "级别", width: "16%" }, { label: "处理", width: "24%" }],
        [
          { cells: ["图表题注缺失", "第 9 页", badge("阻断", "danger"), badge("待修复", "warning")] },
          { cells: ["代码块字体不一致", "第 12 页", badge("阻断", "danger"), badge("待修复", "warning")] },
          { cells: ["参考文献格式不一致", "第 22 页", badge("警告", "warning"), badge("可忽略", "")] },
        ],
        { compact: true },
      ),
      { iconName: "shield-alert" },
    )}</div>`,
    modal: {
      title: "确认自动修复",
      subtitle: "本次将修改 6 个格式项，并在保存后生成 v6 版本。",
      body: `${reviewList([{ title: "统一图表题注", meta: "补充 2 处编号，保持正文引用同步。", selected: true }, { title: "统一代码块字体", meta: "将 4 个代码块调整为 Consolas 10pt。", selected: true }, { title: "调整参考文献样式", meta: "应用课程模板中的 GB/T 7714 样式。", selected: true }])}
        ${notice("自动修复只调整格式，不改变正文内容、批注和引用关系。", "info", "wand-sparkles")}
        ${formField("版本说明", textarea("按课程模板 v2.1 自动修复格式问题。", 3))}`,
      footer: `${button("取消", { action: "close-modal" })}${button("开始修复并生成新版本", { variant: "primary", iconName: "wand-sparkles", action: "toast", message: "已开始自动修复。" })}`,
    },
  },
  {
    id: "convert",
    title: "格式转换",
    group: "格式与模板",
    nav: "format",
    lead: "将教学文档转换为可分发格式，使用标准 A4 预览检查分页与页眉页脚。",
    actions: `${button("清空队列", { iconName: "eraser", action: "toast", message: "转换队列已清空。" })}${button("开始转换", { variant: "primary", iconName: "play", action: "toast", message: "转换任务已开始。" })}`,
    content: () => `<div class="conversion-layout staff-conversion">
      <aside class="surface conversion-drawer"><div class="surface-header"><div class="surface-title">${icon("file-output")}转换方式</div>${iconButton("panel-left-close", { label: "收起转换方式", action: "toggle-panel", message: "" })}</div><div class="surface-body">${filterBar([{ label: "文档", value: "document" }, { label: "教学资源", value: "resource" }, { label: "打印", value: "print" }], "teacher-conversion")}
        <div class="conversion-options">${[
          ["file-text", "DOCX → PDF", "保留分页、题注与页眉", "document"],
          ["presentation", "PPTX → PDF", "适合课堂资料分发", "resource"],
          ["file-type-2", "PDF → DOCX", "可编辑文档转换", "document"],
          ["images", "PDF → 图片", "按页导出 PNG", "resource"],
          ["sheet", "DOCX → HTML", "在线预览与分享", "resource"],
          ["printer", "打印优化 PDF", "A4、双面与页边距优化", "print"],
        ].map((item, index) => `<button class="conversion-option ${index === 0 ? "is-selected" : ""}" type="button" data-action="select-choice" data-filter-row="teacher-conversion" data-row-state="${item[3]}"><span class="format-icon">${icon(item[0])}</span><span class="conversion-option-copy"><strong>${item[1]}</strong><span>${item[2]}</span></span>${index === 0 ? icon("check") : ""}</button>`).join("")}</div></div></aside>
      <section class="surface conversion-preview-panel"><div class="surface-header"><div class="surface-title">${icon("file-scan")}A4 转换预览</div><span class="surface-meta">DOCX → PDF · 24 页</span></div><div class="conversion-preview-body"><div class="a4-preview"><div class="a4-meta">Web开发技术 · 课程设计模板 v2.1</div><h3>三、系统设计</h3><p class="a4-subtitle">3.1 总体架构</p><p>系统采用前端、服务端与文档处理服务分层设计。文档访问请求在服务端完成课程身份与共享范围校验，并记录关键操作日志。</p><div class="a4-figure">${icon("workflow")}总体架构图</div><p>文档解析、智能摘要与格式检查通过异步任务执行，任务状态与版本信息写入文档元数据。</p><div class="a4-page-number">8 / 24</div></div></div><div class="conversion-footer"><span class="surface-meta">A4 · 纵向 · 页边距 2.54cm</span><div class="row-actions">${button("下载预览", { small: true, action: "toast", message: "A4 预览已下载。" })}${button("开始转换", { small: true, variant: "primary", action: "toast", message: "转换任务已开始。" })}</div></div></section>
    </div>`,
  },
  {
    id: "collaborate",
    title: "协作编辑",
    group: "协作与版本",
    nav: "collaborate",
    lead: "与课程组成员共同维护文档，集中查看教师批注、编辑活动和当前版本状态。",
    actions: `${button("共享设置", { iconName: "share-2", action: "toast", message: "共享设置面板已打开。" })}${button("保存并生成版本", { variant: "primary", iconName: "save", action: "toast", message: "已保存并生成新版本。" })}`,
    content: () => `<div class="collab-layout">
      <section class="surface"><div class="editor-toolbar">${["bold", "italic", "underline", "align-left", "list", "image", "table-2"].map((name) => iconButton(name, { label: name })).join("")}<span class="editor-toolbar-spacer"></span><div class="avatar-stack"><span class="avatar">孙</span><span class="avatar">李</span><span class="avatar">王</span></div>${badge("3 人在线", "success")}</div>
        <div class="editor-paper staff-editor-paper"><div class="paper-meta">Web开发技术 · 课程设计模板</div><h2 class="paper-title">三、系统设计</h2><p>系统采用前端、服务端与文档处理服务分层设计。所有文档访问请求在服务端完成课程身份与共享范围校验。</p><div class="teacher-note">${icon("message-square-text")}孙老师：这里需要补充越权访问后的处理结果。<span class="live-cursor">李同学正在编辑</span></div><p>异步任务完成后，系统将解析结果、格式检查与版本号写入文档元数据，学生可在提交前查看任务状态。</p><div class="figure-box">${icon("workflow")}<span>图 3-1 系统总体架构</span></div></div>
      </section>
      <aside class="surface comment-panel"><div class="surface-header"><div class="surface-title">${icon("messages-square")}批注与动态</div>${badge("2 条待处理", "warning")}</div><div class="surface-body"><div class="comment"><strong>孙老师 · 教师批注</strong><p>补充越权访问后的处理结果，并与评分标准对应。</p><div class="row-actions">${button("定位", { small: true, variant: "link", action: "toast", message: "已定位批注位置。" })}${button("标记解决", { small: true, variant: "ghost", action: "toast", message: "批注已标记为解决。" })}</div></div><div class="comment"><strong>李同学 · 课程组</strong><p>已补充权限校验流程，待确认流程图编号。</p><div class="row-actions">${button("回复", { small: true, variant: "ghost", action: "toast", message: "回复输入框已打开。" })}</div></div></div><div class="surface-footer"><span class="surface-meta">最近保存：11:06</span><span class="surface-meta">版本 v5</span></div></aside>
    </div>`,
  },
  {
    id: "versions",
    title: "版本记录",
    group: "协作与版本",
    nav: "collaborate",
    lead: "查看课程模板与教学文档的版本链、提交说明、处理状态和恢复入口。",
    actions: button("对比当前版本", { variant: "primary", iconName: "git-compare-arrows", action: "toast", message: "版本对比视图已打开。" }),
    panels: [
      {
        title: "版本历史",
        iconName: "history",
        span: 2,
        body: dataTable(
          [{ label: "版本", width: "12%" }, { label: "提交说明", width: "35%" }, { label: "修改人", width: "15%" }, { label: "状态", width: "14%" }, { label: "时间", width: "14%" }, { label: "操作", width: "10%", align: "right" }],
          [
            { cells: [badge("v6", "success"), "补充权限校验与异步任务说明", personCell("孙老师", "课程负责人"), badge("当前", "success"), "今天 11:06", button("预览", { small: true, variant: "ghost", action: "toast", message: "当前版本预览已打开。" })] },
            { cells: [badge("v5", "info"), "更新评分标准与图表题注", personCell("孙老师", "课程负责人"), badge("已发布", "info"), "昨天 18:40", button("对比", { small: true, variant: "ghost", action: "toast", message: "已选择 v5 与当前版本对比。" })] },
            { cells: [badge("v4", ""), "补充小组协作批注", personCell("李同学", "课程组"), badge("历史", ""), "10月26日", button("恢复", { small: true, action: "toast", message: "恢复版本确认已打开。" })] },
            { cells: [badge("v3", ""), "初始课程模板发布", personCell("孙老师", "课程负责人"), badge("历史", ""), "10月20日", button("恢复", { small: true, action: "toast", message: "恢复版本确认已打开。" })] },
          ],
          { compact: true },
        ),
      },
    ],
  },
  {
    id: "compare",
    title: "版本对比",
    group: "协作与版本",
    nav: "collaborate",
    lead: "比较两个版本的内容、结构与格式规则变化，确认恢复或合并范围。",
    actions: `${button("仅看内容", { iconName: "file-diff", action: "toast", message: "已切换为内容差异。" })}${button("恢复 v3", { variant: "primary", iconName: "rotate-ccw", action: "toast", message: "恢复版本确认已打开。" })}`,
    panels: [
      {
        title: "v3 · 初始发布",
        iconName: "history",
        meta: "10月20日 14:20",
        body: `<div class="diff-text">系统采用前后端分离架构。文档访问请求在服务端完成权限校验。</div><div class="diff-text">通过异步任务执行文档解析与智能处理。</div><div class="diff-change">图表题注采用“图 3-1”格式。</div>`,
      },
      {
        title: "v6 · 当前版本",
        iconName: "git-compare-arrows",
        meta: "今天 11:06",
        body: `<div class="diff-text">系统采用前端、服务端与文档处理服务分层设计。</div><div class="diff-text is-add">访问请求需同时校验课程身份、共享范围与越权拒绝结果。</div><div class="diff-text is-add">任务状态、格式检查结果和版本号写入文档元数据。</div><div class="diff-change">图表题注统一为“图 3-1 标题”格式。</div><div class="citation">${icon("info")}共 8 处新增、3 处修改，无删除正文内容。</div>`,
      },
    ],
  },
  {
    id: "restore",
    title: "恢复版本确认",
    group: "协作与版本",
    nav: "collaborate",
    lead: "将历史版本恢复为新的当前版本，原版本链与批注记录保持可追溯。",
    content: () => `<div class="staff-modal-preview">${surface(
      "版本历史",
      listRows([
        { title: "v6 · 当前版本", meta: "补充权限校验与异步任务说明 · 今天 11:06", aside: badge("当前", "success") },
        { title: "v5 · 已发布", meta: "更新评分标准与图表题注 · 昨天 18:40", aside: button("对比", { small: true, variant: "ghost", action: "toast", message: "版本对比已打开。" }) },
        { title: "v3 · 初始发布", meta: "初始课程模板发布 · 10月20日", aside: button("选择恢复", { small: true, action: "toast", message: "已选择 v3。" }) },
      ]),
      { iconName: "history" },
    )}</div>`,
    modal: {
      title: "恢复 v3 为新版本？",
      subtitle: "恢复操作会生成 v7，当前版本不会从版本历史中删除。",
      body: `${notice("v3 与当前版本共有 11 处差异，其中 3 处来自教师批注。恢复后请重新确认评分标准。", "warning", "triangle-alert")}
        ${reviewList([{ title: "保留版本历史", meta: "v6 仍可在历史版本中查看和恢复。", selected: true }, { title: "保留批注记录", meta: "历史批注不会丢失，但需要重新定位。", selected: true }, { title: "生成恢复说明", meta: "在版本记录中标记恢复来源。", selected: true }])}`,
      footer: `${button("取消", { action: "close-modal" })}${button("确认恢复为 v7", { variant: "primary", iconName: "rotate-ccw", action: "toast", message: "已恢复并生成 v7 版本。" })}`,
    },
  },
  {
    id: "share",
    title: "共享设置",
    group: "协作与版本",
    nav: "collaborate",
    lead: "控制课程组成员、协作者和外部链接的访问范围，并保留权限变更记录。",
    actions: button("保存设置", { variant: "primary", iconName: "save", action: "toast", message: "共享设置已保存。" }),
    panels: [
      {
        title: "成员权限",
        iconName: "users",
        body: `<div class="filter-row"><div class="search-field">${icon("search")}${input("", { placeholder: "搜索成员或课程组" })}</div>${button("添加协作者", { iconName: "user-plus", action: "toast", message: "添加协作者面板已打开。" })}</div>
        ${dataTable([{ label: "成员", width: "44%" }, { label: "角色", width: "20%" }, { label: "权限", width: "22%" }, { label: "操作", width: "14%", align: "right" }], [
          { cells: [personCell("孙老师", "课程负责人", "孙"), "教师", select(["可编辑", "仅查看"], 0), button("管理", { small: true, variant: "ghost", action: "toast", message: "成员权限已打开。" })] },
          { cells: [personCell("李同学", "Web开发技术 / 第 3 组", "李"), "学生", select(["可评论", "仅查看"], 0), button("管理", { small: true, variant: "ghost", action: "toast", message: "成员权限已打开。" })] },
          { cells: [personCell("王同学", "Web开发技术 / 第 3 组", "王"), "学生", select(["可编辑", "仅查看"], 0), button("管理", { small: true, variant: "ghost", action: "toast", message: "成员权限已打开。" })] },
        ], { compact: true })}`,
      },
      {
        title: "链接与默认权限",
        iconName: "link-2",
        body: `${formField("课程成员默认权限", select(["可评论", "仅查看", "可编辑"]))}${formField("外部链接", select(["仅校内账号可访问", "拥有链接可查看", "关闭"]))}
        ${settingRow("允许下载", "拥有查看权限的成员可导出当前版本。", switchControl(true, "允许下载"))}
        ${settingRow("显示编辑历史", "课程组可查看版本记录与变更人。", switchControl(true, "显示编辑历史"))}
        ${notice("修改共享范围会写入操作日志，并通知受影响的协作者。", "info", "shield-check")}`,
      },
    ],
  },
  {
    id: "courses",
    title: "课程管理",
    group: "课程与作业",
    nav: "courses",
    lead: "维护本学期课程、课程成员和教学文档关联，查看作业运行状态。",
    actions: `${button("导入课程", { iconName: "file-up", action: "toast", message: "课程导入入口已打开。" })}${button("新建课程", { variant: "primary", iconName: "plus", action: "toast", message: "新建课程表单已打开。" })}`,
    stats: [
      { label: "进行中课程", value: "6", note: "学生 186 人", color: "#0f766e" },
      { label: "本周作业", value: "9", note: "5 份待批改", color: "#b45309" },
      { label: "课程模板", value: "8", note: "覆盖全部课程", color: "#2563eb" },
      { label: "平均提交率", value: "87%", note: "较上周 +5%", color: "#15803d" },
    ],
    panels: [
      {
        title: "课程列表",
        iconName: "book-open-check",
        span: 2,
        body: `${filterBar([{ label: "进行中 6", value: "active" }, { label: "本学期", value: "term" }, { label: "已归档", value: "archived" }], "teacher-courses")}
        ${dataTable([{ label: "课程", width: "30%" }, { label: "学期", width: "17%" }, { label: "成员", width: "16%" }, { label: "作业提交", width: "17%" }, { label: "状态", width: "12%" }, { label: "操作", width: "8%", align: "right" }], [
          { state: "active", cells: [docCell("Web开发技术", "课程代码 CS-3101 · 32 人", "book-open"), "2026 秋", "32 / 34", "28 / 32", badge("进行中", "success"), button("管理", { small: true, variant: "ghost", action: "toast", message: "课程管理已打开。" })] },
          { state: "active", cells: [docCell("JavaEE课程设计", "课程代码 CS-3204 · 28 人", "book-open"), "2026 秋", "28 / 30", "23 / 28", badge("进行中", "success"), button("管理", { small: true, variant: "ghost", action: "toast", message: "课程管理已打开。" })] },
          { state: "term", cells: [docCell("Oracle数据库应用", "课程代码 CS-3008 · 35 人", "database"), "2026 秋", "35 / 36", "31 / 35", badge("本周截止", "warning"), button("管理", { small: true, variant: "ghost", action: "toast", message: "课程管理已打开。" })] },
          { state: "archived", cells: [docCell("软件工程导论", "课程代码 CS-2102 · 41 人", "book-open"), "2026 春", "41 / 42", "已归档", badge("已归档", ""), button("查看", { small: true, variant: "ghost", action: "toast", message: "归档课程已打开。" })] },
        ], { filterGroup: "teacher-courses", compact: true })}`,
      },
    ],
  },
  {
    id: "create-course",
    title: "新建课程",
    group: "课程与作业",
    nav: "courses",
    lead: "创建课程空间，设置学期、课程组和默认文档权限。",
    actions: button("返回课程管理", { iconName: "arrow-left", action: "toast", message: "当前为静态单页，页面间不跳转。" }),
    content: () => `<div class="split-layout is-uneven">
      ${surface(
        "课程信息",
        `<div class="form-grid">${formField("课程名称", input("Web开发技术"))}${formField("课程代码", input("CS-3101"))}${formField("学期", select(["2026 秋季", "2027 春季"]))}${formField("课程组", select(["Web开发课程组", "软件工程课程组"]))}</div>${formField("课程简介", textarea("面向本科生的 Web 开发课程，包含课程设计、格式规范、协作与提交要求。", 4))}${formField("课程颜色", `<div class="color-options"><span style="--swatch:#0f766e"></span><span style="--swatch:#2563eb"></span><span style="--swatch:#7c3aed"></span><span style="--swatch:#b45309"></span><span class="is-selected" style="--swatch:#0891b2"></span></div>`)}`,
        { iconName: "book-plus" },
      )}
      <div class="stack">${surface("课程成员", listRows([{ title: "任课教师", meta: "孙老师 · 课程负责人", aside: badge("必选", "info") }, { title: "课程组教师", meta: "刘老师 · 可编辑", aside: button("移除", { small: true, variant: "ghost", action: "toast", message: "成员已移除。" }) }, { title: "教学助理", meta: "王助教 · 可评论", aside: button("移除", { small: true, variant: "ghost", action: "toast", message: "成员已移除。" }) }]), { iconName: "users", action: button("添加", { small: true, variant: "ghost", action: "toast", message: "成员邀请入口已打开。" }) })}${surface("创建检查", reviewList([{ title: "课程代码唯一", meta: "CS-3101 当前未占用", selected: true }, { title: "课程负责人已设置", meta: "孙老师", selected: true }, { title: "默认模板待选择", meta: "可创建后配置", selected: false }]), { iconName: "clipboard-check" })}</div>
    </div>`,
  },
  {
    id: "members",
    title: "课程成员导入",
    group: "课程与作业",
    nav: "courses",
    lead: "从教务名单或 CSV 文件导入课程成员，映射学号、姓名和组别。",
    actions: button("下载导入模板", { iconName: "download", action: "toast", message: "成员导入模板已下载。" }),
    content: () => `<div class="split-layout is-uneven">
      ${surface("导入文件", `<div class="dropzone" data-action="toast" data-message="成员名单文件选择器已触发"><div>${icon("file-up")}<strong>拖拽课程成员名单</strong><span>支持 CSV / XLSX，首行需包含学号、姓名和组别</span>${button("选择文件", { small: true, action: "toast", message: "已选择课程成员名单。" })}</div></div><div class="selected-file"><span class="row-icon">${icon("file-spreadsheet")}</span><div class="row-copy"><strong>Web开发技术_课程成员_2026秋.xlsx</strong><span>34 行 · 2.4MB · 文件校验通过</span></div>${badge("可导入", "success")}</div>${notice("3 名学生尚未绑定校内账号，导入后将发送课程邀请。", "warning", "user-round-check")}`, { iconName: "upload-cloud" })}
      <div class="stack">${surface("字段映射", `<div class="mapping-list">${[["学号", "student_id"], ["姓名", "display_name"], ["课程组", "group_name"], ["账号状态", "account_status"]].map(([source, target]) => `<div class="mapping-row"><span>${source}</span>${icon("arrow-right")}${select([target, "忽略此列"], 0)}${badge("已匹配", "success", true)}</div>`).join("")}</div>`, { iconName: "list-tree" })}${surface("导入预览", dataTable([{ label: "学号", width: "28%" }, { label: "姓名", width: "24%" }, { label: "组别", width: "26%" }, { label: "状态", width: "22%" }], [
        { cells: ["2023001", "张同学", "第 3 组", badge("已绑定", "success")] },
        { cells: ["2023002", "李同学", "第 3 组", badge("已绑定", "success")] },
        { cells: ["2023018", "赵同学", "第 5 组", badge("待邀请", "warning")] },
      ], { compact: true }), { iconName: "table-2" })}</div>
    </div>`,
  },
  {
    id: "assignments",
    title: "作业与提交统计",
    group: "课程与作业",
    nav: "courses",
    lead: "跟踪作业提交、格式检查、评分进度和课程组状态，定位需要提醒的学生。",
    actions: `${button("导出统计", { iconName: "download", action: "toast", message: "作业统计表已导出。" })}${button("发布作业", { variant: "primary", iconName: "send", action: "toast", message: "发布作业表单已打开。" })}`,
    stats: [
      { label: "已发布作业", value: "9", note: "本周新增 2 份", color: "#0f766e" },
      { label: "已提交", value: "218", note: "提交率 87%", color: "#2563eb" },
      { label: "待批改", value: "42", note: "3 个课程组", color: "#b45309" },
      { label: "平均得分", value: "88.6", note: "较上次 +2.1", color: "#15803d" },
    ],
    panels: [
      {
        title: "提交分布",
        iconName: "chart-no-axes-column",
        body: `<div class="staff-bars">${[["已提交", 218, 87, "#0f766e"], ["格式通过", 204, 81, "#15803d"], ["待批改", 42, 17, "#b45309"], ["逾期未交", 18, 7, "#b91c1c"]].map(([label, value, percent, color]) => `<div class="staff-bar-row"><span>${label}</span><div class="progress"><i style="width:${percent}%;background:${color}"></i></div><strong>${value}</strong></div>`).join("")}</div><div class="notice">${icon("clock-3")}Web开发技术在 24 小时后截止，仍有 4 名学生在处理文档。</div>`,
      },
      {
        title: "课程作业",
        iconName: "clipboard-list",
        body: listRows([
          { iconName: "file-check-2", title: "Web开发课程设计", meta: "28 / 32 已提交 · 评分 64%", aside: badge("即将截止", "warning") },
          { iconName: "file-check-2", title: "JavaEE小组项目", meta: "23 / 28 已提交 · 评分 38%", aside: badge("待批改 14", "info") },
          { iconName: "file-check-2", title: "Oracle数据库设计", meta: "31 / 35 已提交 · 评分 77%", aside: badge("进度正常", "success") },
        ]),
      },
      {
        title: "学生提交明细",
        iconName: "table-2",
        span: 2,
        body: dataTable(
          [{ label: "学生", width: "24%" }, { label: "课程组", width: "16%" }, { label: "提交状态", width: "16%" }, { label: "格式", width: "14%" }, { label: "得分", width: "12%" }, { label: "操作", width: "18%", align: "right" }],
          [
            { cells: [personCell("张同学", "2023001"), "第 3 组", badge("已提交", "success"), badge("通过", "success"), "92", `${button("查看", { small: true, variant: "ghost", action: "toast", message: "学生提交已打开。" })}${button("批改", { small: true, action: "toast", message: "批改面板已打开。" })}`] },
            { cells: [personCell("李同学", "2023002"), "第 3 组", badge("已提交", "success"), badge("1 项警告", "warning"), "待批改", button("批改", { small: true, action: "toast", message: "批改面板已打开。" })] },
            { cells: [personCell("赵同学", "2023018"), "第 5 组", badge("处理中", "info"), badge("待检查", ""), "未提交", button("提醒", { small: true, variant: "ghost", action: "toast", message: "已发送截止提醒。" })] },
          ],
          { compact: true },
        ),
      },
    ],
  },
  {
    id: "publish-assignment",
    title: "发布作业",
    group: "课程与作业",
    nav: "courses",
    lead: "配置作业要求、截止时间、提交格式和评分标准，发布前完成学生端预览。",
    actions: button("保存草稿", { iconName: "save", action: "toast", message: "作业草稿已保存。" }),
    content: () => `<div class="split-layout is-uneven">
      <div class="stack">${surface("作业基本信息", `<div class="form-grid">${formField("作业标题", input("Web开发课程设计报告"))}${formField("所属课程", select(["Web开发技术", "JavaEE课程设计"]))}${formField("截止时间", input("2026-10-30 23:59"))}${formField("提交方式", select(["个人提交", "小组提交"]))}</div>${formField("作业说明", textarea("提交课程设计报告，正文需包含需求分析、系统设计、系统实现、测试与结论。提交前必须完成格式校验。", 4))}`, { iconName: "clipboard-pen-line" })}${surface("提交与评分规则", `${settingRow("要求格式校验通过", "阻断问题未修复时禁止提交。", switchControl(true, "要求格式校验通过"))}${settingRow("允许截止后补交", "补交会记录延迟状态并通知教师。", switchControl(false, "允许截止后补交"))}<div class="form-grid">${formField("总分", input("100"))}${formField("评分方式", select(["按评分项", "教师自定义"]))}</div>`, { iconName: "sliders-horizontal" })}</div>
      ${surface("学生端预览", `<div class="student-preview"><span class="badge info">作业预览</span><h2>Web开发课程设计报告</h2><p>Web开发技术 · 个人提交 · 截止 10 月 30 日 23:59</p><div class="notice">${icon("shield-check")}提交前需通过课程模板格式校验。</div>${reviewList([{ title: "需求分析", meta: "评分 20 分 · 必填章节", selected: true }, { title: "系统设计", meta: "评分 20 分 · 必填章节", selected: true }, { title: "测试与结论", meta: "评分 15 分 · 必填章节", selected: true }])}<button class="button primary is-block" type="button" data-action="toast" data-message="学生端预览操作已触发。">${icon("upload")}<span>选择提交文档</span></button></div>`, { iconName: "smartphone" })}
    </div>`,
  },
  {
    id: "grade",
    title: "批改与反馈",
    group: "课程与作业",
    nav: "courses",
    lead: "对照提交内容与评分标准完成评分，学生可在消息中心查看教师反馈。",
    actions: `${button("暂存批改", { iconName: "save", action: "toast", message: "批改草稿已暂存。" })}${button("提交成绩", { variant: "primary", iconName: "check-check", action: "toast", message: "成绩与反馈已提交。" })}`,
    content: () => `<div class="split-layout is-uneven grading-layout">
      <section class="surface"><div class="editor-toolbar"><span class="surface-meta">Web开发课程设计报告_张同学_v3.docx</span><span class="editor-toolbar-spacer"></span><span class="surface-meta">第 8 / 24 页</span></div><div class="paper-page"><div class="paper-meta">Web开发技术 · 课程设计报告</div><h2 class="paper-title">三、系统设计</h2><p class="paper-subtitle">系统架构与权限校验</p><p>系统采用前端、服务端与文档处理服务分层设计。所有文档访问请求在服务端完成课程身份与共享范围校验，并记录关键操作日志。</p><div class="figure-box">${icon("workflow")}<span>图 3-1 系统总体架构</span></div><p>文档解析、智能摘要与格式检查通过异步任务执行，任务状态与版本信息写入文档元数据。</p><div class="teacher-note">${icon("message-square-text")}教师批注：权限校验流程清晰，建议补充越权访问结果。</div></div></section>
      <div class="stack"><section class="surface"><div class="surface-header"><div class="surface-title">${icon("graduation-cap")}评分与反馈</div>${badge("总分 92", "success")}</div><div class="surface-body"><div class="rubric-list">${[["需求分析", 19, 20], ["系统设计", 18, 20], ["系统实现", 20, 20], ["测试与结论", 17, 20], ["格式规范", 18, 20]].map(([label, value, max]) => `<div class="rubric-row"><span>${label}</span><div class="progress"><i style="width:${(value / max) * 100}%"></i></div><strong>${value} / ${max}</strong></div>`).join("")}</div>${formField("总体评语", textarea("系统设计完整，权限校验可追溯。建议补充越权访问后的处理结果，并在测试章节增加并发规模数据。", 4))}${formField("下一位学生", input("李同学 · 2023002", { readonly: true }))}<div class="row-actions">${button("返回列表", { small: true, variant: "ghost", action: "toast", message: "当前为静态单页，页面间不跳转。" })}${button("提交并继续", { small: true, variant: "primary", action: "toast", message: "成绩已提交，下一位学生已载入。" })}</div></div></section>${surface("评分标准", reviewList([{ title: "系统设计 20 分", meta: "架构清晰、权限流程完整", selected: true }, { title: "格式规范 20 分", meta: "模板校验通过", selected: true }]), { iconName: "list-checks" })}</div>
    </div>`,
  },
  {
    id: "messages",
    title: "消息中心",
    group: "消息与个人中心",
    nav: "messages",
    lead: "集中查看课程组更新、学生提交、格式异常和系统通知。",
    actions: button("全部标为已读", { iconName: "check-check", action: "mark-all-read" }),
    content: () => `<div class="message-layout">
      <section class="surface"><div class="surface-header"><div class="surface-title">${icon("bell")}教学消息</div><span class="surface-meta">${state.messagesRead ? "全部已读" : "3 条未读"}</span></div><div class="surface-body"><div class="message-toolbar">${filterBar([{ label: "全部", value: "all" }, { label: "提交", value: "submission" }, { label: "批注", value: "comment" }, { label: "系统", value: "system" }], "teacher-messages")}</div><div class="message-list">${[
        ["李同学提交了 Web开发课程设计报告", "第 3 组 · 格式校验通过 · 等待批改", "今天 11:02", true, "file-check-2"],
        ["孙老师，课程组补充了系统设计内容", "JavaEE课程作业_小组版.docx · 2 条新批注", "今天 10:48", true, "message-square-text"],
        ["本周有 4 份作业将在 24 小时内截止", "Web开发技术 · 仍有 4 名学生处理中", "今天 09:30", true, "clock-3"],
        ["课程模板 v2.2 草稿已更新", "章节结构与格式规则发生变化，等待发布", "昨天 18:22", false, "layout-template"],
        ["格式校验任务已完成", "Oracle课程设计_评分说明.docx · 2 个阻断问题", "昨天 16:05", false, "shield-alert"],
      ].map(([title, meta, time, unread, iconName], index) => `<div class="message-row ${unread && !state.messagesRead ? "is-unread" : ""}" data-filter-row="teacher-messages" data-row-state="${index < 2 ? "comment" : index === 2 ? "system" : "submission"}"><span class="row-icon">${icon(iconName)}</span><div class="message-copy"><strong>${title}${unread && !state.messagesRead ? '<span class="dot"></span>' : ""}</strong><p>${meta}</p></div><div><div class="message-time">${time}</div><div class="message-actions">${button(index === 0 ? "去批改" : "查看", { small: true, variant: "ghost", action: "toast", message: "消息详情已打开。" })}</div></div></div>`).join("")}</div></div></section>
      <aside class="surface"><div class="surface-header"><div class="surface-title">${icon("bell-ring")}通知设置</div></div><div class="surface-body">${settingRow("学生提交提醒", "有新提交时发送站内通知。", switchControl(true, "学生提交提醒"))}${settingRow("格式异常提醒", "阻断问题汇总后发送。", switchControl(true, "格式异常提醒"))}${settingRow("每日教学摘要", "每天 18:00 汇总待办。", switchControl(false, "每日教学摘要"))}${notice("紧急消息始终置顶，不受筛选条件影响。", "info", "pin")}</div></section>
    </div>`,
  },
  {
    id: "profile",
    title: "个人资料",
    group: "消息与个人中心",
    nav: "profile",
    lead: "维护教师账号资料、工作院系、通知偏好和账号安全设置。",
    actions: button("保存资料", { variant: "primary", iconName: "save", action: "toast", message: "个人资料已保存。" }),
    content: () => `<div class="profile-layout staff-profile-layout">
      <aside class="stack"><section class="surface profile-summary"><div class="profile-avatar">孙</div><h2>孙老师</h2><p>Web开发课程组 · 计算机学院</p>${badge("教师账号", "success")}<div class="profile-meta"><div><strong>6</strong><span>授课课程</span></div><div><strong>128</strong><span>教学文档</span></div></div></section><section class="surface"><div class="security-list">${[
        ["账号与安全", "上次登录今天 08:42", "shield-check"],
        ["通知偏好", "站内消息 + 邮件", "bell"],
        ["存储空间", "已使用 386MB / 2GB", "hard-drive"],
      ].map(([title, meta, iconName]) => `<div class="security-row"><span class="row-icon">${icon(iconName)}</span><div class="row-copy"><strong>${title}</strong><span>${meta}</span></div>${icon("chevron-right")}</div>`).join("")}</div></section></aside>
      <div class="stack">${surface("基本信息", `<div class="profile-form-grid">${formField("姓名", input("孙老师"))}${formField("工号", input("T1002", { readonly: true }))}${formField("所属院系", input("计算机学院"))}${formField("课程组", input("Web开发课程组"))}</div>${formField("联系邮箱", input("sun.teacher@university.edu.cn"))}${formField("办公地点", input("信息楼 A-410"))}`, { iconName: "user-round" })}${surface("账号与通知", `${settingRow("课程变更邮件", "发布模板或作业时发送邮件。", switchControl(true, "课程变更邮件"))}${settingRow("批注即时提醒", "学生回复教研批注时立即提醒。", switchControl(true, "批注即时提醒"))}${settingRow("每周教学摘要", "周一 09:00 汇总课程运行数据。", switchControl(false, "每周教学摘要"))}${notice("账号安全设置仅对当前教师账号生效。", "info", "lock-keyhole")}`, { iconName: "settings-2" })}</div>
    </div>`,
  },
];

const operationsRoutes = [
  {
    id: "workbench",
    title: "运维工作台",
    group: "总览",
    nav: "workbench",
    lead: "统一掌握课程运行、用户权限、审计安全与系统资源状态，优先处理高风险事件。",
    actions: `${button("下载运行报告", { iconName: "download", action: "toast", message: "运维运行报告已生成。" })}${button("创建处置任务", { variant: "primary", iconName: "plus", action: "toast", message: "处置任务表单已打开。" })}`,
    stats: [
      { label: "活跃账号", value: "2,418", note: "今日登录 1,206", color: "#0e7490" },
      { label: "高风险事件", value: "3", note: "2 项待复核", color: "#b91c1c" },
      { label: "课程运行", value: "96%", note: "4 个课程待同步", color: "#0f766e" },
      { label: "存储占用", value: "68%", note: "1.36TB / 2TB", color: "#2563eb" },
    ],
    panels: [
      {
        title: "需要处理",
        iconName: "shield-alert",
        body: listRows([
          { iconName: "key-round", title: "检测到越权访问尝试", meta: "账号 2024017 · Web开发技术 · 10:42", aside: badge("高风险", "danger"), tone: "is-warning" },
          { iconName: "user-round-x", title: "3 个课程成员未绑定账号", meta: "JavaEE课程设计 · 已超过 24 小时", aside: badge("待处理", "warning"), tone: "is-warning" },
          { iconName: "database-backup", title: "回收站将在今晚自动清理", meta: "128 个过期对象 · 共 4.8GB", aside: badge("待确认", "info"), tone: "" },
          { iconName: "server-cog", title: "文档解析服务延迟升高", meta: "近 15 分钟 P95 延迟 8.4 秒", aside: badge("观察中", "violet"), tone: "is-violet" },
        ]),
      },
      {
        title: "系统健康",
        iconName: "activity",
        body: `${progressRows([{ label: "身份认证服务", value: "正常", percent: 99, color: "#15803d", note: "99.99%" }, { label: "文档解析服务", value: "轻微延迟", percent: 78, color: "#b45309", note: "P95 8.4s" }, { label: "协作同步服务", value: "正常", percent: 97, color: "#15803d", note: "延迟 42ms" }, { label: "存储服务", value: "正常", percent: 92, color: "#0e7490", note: "容量 68%" }])}${notice("解析延迟已持续 12 分钟，若继续上升将自动降级非关键任务。", "warning", "triangle-alert")}`,
      },
      {
        title: "最近安全动态",
        iconName: "scan-search",
        span: 2,
        body: dataTable(
          [{ label: "时间", width: "13%" }, { label: "事件", width: "36%" }, { label: "对象", width: "20%" }, { label: "状态", width: "15%" }, { label: "处置", width: "16%", align: "right" }],
          [
            { cells: ["10:42", "越权访问被拒绝", "账号 2024017", badge("待复核", "warning"), button("查看", { small: true, variant: "ghost", action: "toast", message: "审计事件详情已打开。" })] },
            { cells: ["09:18", "管理员角色权限变更", "账号 A2003", badge("已记录", "info"), button("查看", { small: true, variant: "ghost", action: "toast", message: "权限变更记录已打开。" })] },
            { cells: ["08:55", "异常登录地检测", "账号 T1008", badge("已阻断", "success"), button("详情", { small: true, variant: "ghost", action: "toast", message: "安全事件详情已打开。" })] },
          ],
          { compact: true },
        ),
      },
    ],
  },
  {
    id: "courses",
    title: "课程治理",
    group: "教学治理",
    nav: "courses",
    lead: "统一维护课程状态、成员规模、模板关联和教学运行指标。",
    actions: `${button("导出课程清单", { iconName: "download", action: "toast", message: "课程清单已导出。" })}${button("新建课程", { variant: "primary", iconName: "plus", action: "toast", message: "新建课程表单已打开。" })}`,
    stats: [
      { label: "课程总数", value: "48", note: "本学期 26 门", color: "#0e7490" },
      { label: "参与学生", value: "2,118", note: "覆盖 12 个院系", color: "#2563eb" },
      { label: "待同步", value: "4", note: "教务名单发生变化", color: "#b45309" },
      { label: "运行异常", value: "1", note: "解析服务待观察", color: "#b91c1c" },
    ],
    panels: [
      {
        title: "课程列表",
        iconName: "book-open-check",
        span: 2,
        body: `${filterBar([{ label: "进行中 26", value: "active" }, { label: "待开课 4", value: "pending" }, { label: "已归档 18", value: "archived" }, { label: "待同步", value: "sync" }], "ops-courses")}
        ${dataTable(
          [{ label: "课程", width: "29%" }, { label: "院系", width: "16%" }, { label: "教师", width: "14%" }, { label: "学生", width: "11%" }, { label: "关联模板", width: "15%" }, { label: "状态", width: "9%" }, { label: "操作", width: "6%", align: "right" }],
          [
            { state: "active", cells: [docCell("Web开发技术", "CS-3101 · 2026 秋", "code-2"), "计算机学院", "孙老师", "34", "Web开发模板 v2.1", badge("进行中", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "课程详情已打开。" })] },
            { state: "active", cells: [docCell("JavaEE课程设计", "CS-3204 · 2026 秋", "book-open"), "计算机学院", "孙老师", "30", "JavaEE模板 v2.4", badge("进行中", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "课程详情已打开。" })] },
            { state: "sync", cells: [docCell("Oracle数据库应用", "CS-3008 · 2026 秋", "database"), "软件学院", "王老师", "36", "Oracle模板 v1.8", badge("待同步", "warning"), button("查看", { small: true, variant: "ghost", action: "toast", message: "课程详情已打开。" })] },
            { state: "pending", cells: [docCell("人工智能导论", "AI-2100 · 2027 春", "brain-circuit"), "人工智能学院", "周老师", "42", "通用报告模板", badge("待开课", "info"), button("查看", { small: true, variant: "ghost", action: "toast", message: "课程详情已打开。" })] },
            { state: "archived", cells: [docCell("软件工程导论", "CS-2102 · 2026 春", "book-open"), "计算机学院", "陈老师", "41", "软件工程模板 v1.2", badge("已归档", ""), button("查看", { small: true, variant: "ghost", action: "toast", message: "归档课程已打开。" })] },
          ],
          { filterGroup: "ops-courses", compact: true },
        )}`,
      },
    ],
  },
  {
    id: "course-detail",
    title: "课程详情",
    group: "教学治理",
    nav: "courses",
    lead: "查看课程基础信息、教师和学生规模、模板关联及最近运行状态。",
    actions: `${button("归档课程", { iconName: "archive", action: "toast", message: "归档课程确认已打开。" })}${button("编辑课程", { variant: "primary", iconName: "pencil", action: "toast", message: "课程编辑表单已打开。" })}`,
    stats: [
      { label: "课程成员", value: "34", note: "学生 32 / 教师 2", color: "#0e7490" },
      { label: "教学文档", value: "126", note: "本周新增 18", color: "#2563eb" },
      { label: "作业提交率", value: "87%", note: "28 / 32", color: "#15803d" },
      { label: "格式异常", value: "4", note: "2 项为阻断", color: "#b45309" },
    ],
    panels: [
      {
        title: "课程信息",
        iconName: "book-open",
        body: `<div class="detail-grid detail-grid-four">${[
          ["课程代码", "CS-3101"], ["学期", "2026 秋季"], ["所属院系", "计算机学院"], ["课程负责人", "孙老师"], ["课程组", "Web开发课程组"], ["默认模板", "Web开发模板 v2.1"], ["学生规模", "32 人"], ["课程状态", "进行中"],
        ].map(([label, value]) => `<div class="detail-item"><span>${label}</span><strong>${value}</strong></div>`).join("")}</div>${notice("教务名单于今天 09:12 更新，新增 1 名学生，已自动发送课程邀请。", "info", "refresh-cw")}`,
      },
      {
        title: "最近教学运行",
        iconName: "activity",
        body: listRows([
          { iconName: "file-up", title: "教师上传课程模板 v2.2 草稿", meta: "孙老师 · 今天 10:32", aside: badge("待发布", "warning") },
          { iconName: "clipboard-check", title: "作业提交检查完成", meta: "28 份提交 · 24 份格式通过", aside: badge("正常", "success") },
          { iconName: "user-round-plus", title: "新增课程成员", meta: "赵同学 · 2023018 · 第 5 组", aside: badge("已同步", "info") },
          { iconName: "message-square-text", title: "课程组协作活跃", meta: "本周 142 次编辑 · 18 条批注", aside: badge("活跃", "violet") },
        ]),
      },
      {
        title: "课程成员概览",
        iconName: "users-round",
        span: 2,
        body: dataTable(
          [{ label: "成员", width: "34%" }, { label: "角色", width: "18%" }, { label: "课程组", width: "18%" }, { label: "最近活动", width: "18%" }, { label: "状态", width: "12%" }],
          [
            { cells: [personCell("孙老师", "T1002 · 课程负责人", "孙"), "教师", "课程组", "今天 11:06", badge("正常", "success")] },
            { cells: [personCell("李同学", "2023002 · 第 3 组", "李"), "学生", "第 3 组", "今天 10:48", badge("正常", "success")] },
            { cells: [personCell("赵同学", "2023018 · 第 5 组", "赵"), "学生", "第 5 组", "尚未登录", badge("待激活", "warning")] },
          ],
          { compact: true },
        ),
      },
    ],
  },
  {
    id: "create-course",
    title: "新建课程",
    group: "教学治理",
    nav: "courses",
    lead: "创建课程空间并配置学期、院系、课程负责人和默认教学模板。",
    actions: button("保存并继续", { variant: "primary", iconName: "save", action: "toast", message: "课程信息已保存。" }),
    content: () => `<div class="split-layout is-uneven">
      ${surface("课程信息", `<div class="form-grid">${formField("课程名称", input("Web开发技术"))}${formField("课程代码", input("CS-3101"))}${formField("所属院系", select(["计算机学院", "软件学院", "人工智能学院"]))}${formField("学期", select(["2026 秋季", "2027 春季"]))}${formField("课程负责人", input("孙老师 · T1002"))}${formField("课程组", select(["Web开发课程组", "新建课程组"]))}</div>${formField("课程简介", textarea("面向本科生的 Web 开发课程，包含课程设计、格式规范、协作与提交要求。", 4))}${formField("默认教学模板", select(["Web开发课程设计模板 v2.1", "通用课程报告模板"]))}`, { iconName: "book-plus" })}
      <div class="stack">${surface("创建检查", reviewList([{ title: "课程代码唯一", meta: "CS-3101 当前未占用", selected: true }, { title: "负责人已绑定", meta: "孙老师 · 教师账号", selected: true }, { title: "默认模板已选择", meta: "Web开发课程设计模板 v2.1", selected: true }, { title: "成员名单待导入", meta: "可创建后继续配置", selected: false }]), { iconName: "clipboard-check" })}${surface("权限建议", listRows([{ title: "教师", meta: "可编辑课程、模板和作业", aside: badge("默认", "info") }, { title: "学生", meta: "可查看已发布内容并提交作业", aside: badge("受限", "") }, { title: "运维", meta: "只访问治理与审计能力", aside: badge("隔离", "success") }]), { iconName: "shield-check" })}</div>
    </div>`,
  },
  {
    id: "members",
    title: "成员导入",
    group: "教学治理",
    nav: "courses",
    lead: "同步教务课程名单，统一处理账号绑定、课程组和角色映射。",
    actions: button("下载模板", { iconName: "download", action: "toast", message: "成员导入模板已下载。" }),
    content: () => `<div class="split-layout is-uneven">
      ${surface("导入来源", `<div class="choice-row"><button type="button" class="selection-card is-selected" data-action="select-choice"><strong>${icon("refresh-cw")}从教务系统同步</strong><p>自动读取课程号、学号、姓名与院系。</p></button><button type="button" class="selection-card" data-action="select-choice"><strong>${icon("file-up")}上传成员文件</strong><p>支持 CSV / XLSX，适用于临时课程。</p></button></div><button type="button" class="dropzone" data-action="toast" data-message="成员文件选择器已触发"><div>${icon("file-spreadsheet")}<strong>拖拽成员名单</strong><span>CSV / XLSX · 最大 10MB</span></div></button><div class="selected-file"><span class="row-icon">${icon("file-spreadsheet")}</span><div class="row-copy"><strong>CS-3008_课程成员_2026秋.xlsx</strong><span>36 行 · 字段完整 · 3 个账号待绑定</span></div>${badge("可导入", "success")}</div>`, { iconName: "file-up" })}
      <div class="stack">${surface("导入规则", `<div class="form-grid">${formField("账号匹配", select(["工号 / 学号优先", "校内邮箱优先"]))}${formField("课程组", select(["读取文件中的组别", "导入后统一分组"]))}</div>${settingRow("自动发送激活邀请", "对未绑定账号发送站内与邮件邀请。", switchControl(true, "自动发送激活邀请"))}${settingRow("冲突时跳过并记录", "保留已有角色，不覆盖手工调整。", switchControl(true, "冲突时跳过并记录"))}`, { iconName: "settings-2" })}${surface("预览", dataTable([{ label: "成员", width: "46%" }, { label: "组别", width: "26%" }, { label: "匹配结果", width: "28%" }], [
        { cells: [personCell("张同学", "2023001"), "第 3 组", badge("已绑定", "success")] },
        { cells: [personCell("赵同学", "2023018"), "第 5 组", badge("待邀请", "warning")] },
        { cells: [personCell("钱同学", "2023021"), "第 6 组", badge("已绑定", "success")] },
      ], { compact: true }), { iconName: "table-2" })}</div>
    </div>`,
  },
  {
    id: "archive-course",
    title: "归档课程确认",
    group: "教学治理",
    nav: "courses",
    lead: "确认归档范围、数据保留和成员访问变化。归档后课程进入只读状态。",
    content: () => `<div class="staff-modal-preview">${surface("课程列表", dataTable([{ label: "课程", width: "38%" }, { label: "学期", width: "18%" }, { label: "成员", width: "16%" }, { label: "状态", width: "16%" }, { label: "操作", width: "12%", align: "right" }], [
      { cells: [docCell("Web开发技术", "CS-3101", "code-2"), "2026 秋", "34", badge("进行中", "success"), button("归档", { small: true, action: "toast", message: "归档确认已打开。" })] },
      { cells: [docCell("JavaEE课程设计", "CS-3204", "book-open"), "2026 秋", "30", badge("进行中", "success"), button("归档", { small: true, action: "toast", message: "归档确认已打开。" })] },
    ], { compact: true }), { iconName: "book-open-check" })}</div>`,
    modal: {
      title: "归档 Web开发技术？",
      subtitle: "课程内容将转为只读，教师和学生保留历史查看权限。",
      body: `${notice("归档后不可继续发布作业或修改课程模板，可联系教务处申请恢复。", "warning", "archive")}
        ${reviewList([{ title: "保留教学文档与模板", meta: "历史内容可供审计和重复使用。", selected: true }, { title: "保留成绩与提交记录", meta: "学生仍可查看个人历史反馈。", selected: true }, { title: "关闭协作编辑", meta: "所有成员转为只读权限。", selected: true }])}
        ${formField("归档说明", textarea("2026 秋季课程结束，完成成绩归档后关闭课程。", 3))}`,
      footer: `${button("取消", { action: "close-modal" })}${button("确认归档", { variant: "primary", iconName: "archive", action: "toast", message: "课程已归档。" })}`,
    },
  },
  {
    id: "users",
    title: "用户目录",
    group: "用户与权限",
    nav: "users",
    lead: "管理教师、学生、课程组和运维账号，查看身份状态与授权范围。",
    actions: `${button("批量导入", { iconName: "file-up", action: "toast", message: "批量导入入口已打开。" })}${button("添加用户", { variant: "primary", iconName: "user-plus", action: "toast", message: "添加用户表单已打开。" })}`,
    stats: [
      { label: "账号总数", value: "2,418", note: "教师 126 / 学生 2,292", color: "#0e7490" },
      { label: "活跃账号", value: "2,286", note: "94.5%", color: "#15803d" },
      { label: "待激活", value: "84", note: "48 个超过 7 天", color: "#b45309" },
      { label: "受限账号", value: "22", note: "冻结 14 / 停用 8", color: "#b91c1c" },
    ],
    panels: [
      {
        title: "账号列表",
        iconName: "users-round",
        span: 2,
        body: `${filterBar([{ label: "全部账号", value: "all" }, { label: "教师", value: "teacher" }, { label: "学生", value: "student" }, { label: "待激活", value: "pending" }, { label: "受限", value: "restricted" }], "ops-users")}
        ${dataTable(
          [{ label: "用户", width: "29%" }, { label: "身份", width: "15%" }, { label: "所属组织", width: "19%" }, { label: "角色", width: "16%" }, { label: "最近登录", width: "13%" }, { label: "状态", width: "8%" }],
          [
            { state: "teacher", cells: [personCell("孙老师", "T1002 · sun.teacher@university.edu.cn"), "教师", "计算机学院", "教师", "今天 08:42", badge("正常", "success")] },
            { state: "student", cells: [personCell("李同学", "2023002 · li.student@university.edu.cn"), "学生", "计算机学院 / 第 3 组", "学生", "今天 10:48", badge("正常", "success")] },
            { state: "pending", cells: [personCell("赵同学", "2023018"), "学生", "软件学院 / 第 5 组", "学生", "尚未登录", badge("待激活", "warning")] },
            { state: "restricted", cells: [personCell("账号 2024017", "2024017@university.edu.cn"), "学生", "人工智能学院", "学生", "今天 10:42", badge("已冻结", "danger")] },
            { state: "teacher", cells: [personCell("刘老师", "A2001 · liu.admin@university.edu.cn"), "运维", "教务处", "运维管理员", "今天 09:18", badge("正常", "success")] },
          ],
          { filterGroup: "ops-users", compact: true },
        )}`,
      },
    ],
  },
  {
    id: "user-detail",
    title: "用户权限详情",
    group: "用户与权限",
    nav: "users",
    lead: "查看账号身份、授权来源、课程范围和最近访问记录。",
    actions: `${button("冻结账号", { iconName: "user-round-x", action: "toast", message: "冻结账号确认已打开。" })}${button("变更角色", { variant: "primary", iconName: "key-round", action: "toast", message: "角色变更面板已打开。" })}`,
    stats: [
      { label: "当前角色", value: "学生", note: "权限来源：校内身份", color: "#0e7490" },
      { label: "课程范围", value: "4", note: "2 门进行中", color: "#2563eb" },
      { label: "访问记录", value: "86", note: "近 30 天", color: "#15803d" },
      { label: "风险事件", value: "1", note: "越权访问已阻断", color: "#b91c1c" },
    ],
    panels: [
      {
        title: "账号信息",
        iconName: "user-round",
        body: `${personCell("张同学", "2023001 · 计算机学院", "张")}<div class="detail-grid detail-grid-four">${[["账号状态", "正常"], ["身份来源", "统一身份认证"], ["最近登录", "今天 10:32"], ["注册时间", "2024-09-01"]].map(([label, value]) => `<div class="detail-item"><span>${label}</span><strong>${value}</strong></div>`).join("")}</div>${notice("该账号最近一次权限变更由教务系统同步，未发现冲突授权。", "info", "shield-check")}`,
      },
      {
        title: "课程与角色",
        iconName: "book-open-check",
        body: listRows([
          { iconName: "code-2", title: "Web开发技术", meta: "课程成员 · 第 3 组", aside: badge("学生", "info") },
          { iconName: "book-open", title: "JavaEE课程设计", meta: "课程成员 · 第 2 组", aside: badge("学生", "info") },
          { iconName: "database", title: "Oracle数据库应用", meta: "课程成员 · 第 5 组", aside: badge("学生", "info") },
        ]),
      },
      {
        title: "最近访问与权限事件",
        iconName: "logs",
        span: 2,
        body: dataTable([{ label: "时间", width: "16%" }, { label: "事件", width: "38%" }, { label: "来源", width: "20%" }, { label: "结果", width: "26%" }], [
          { cells: ["今天 10:32", "访问 Web开发课程设计报告", "Web 端 / 10.20.12.5", badge("成功", "success")] },
          { cells: ["今天 10:18", "尝试访问未加入课程", "Web 端 / 10.20.12.5", badge("已拒绝", "danger")] },
          { cells: ["昨天 16:05", "提交文档版本 v3", "Web 端 / 10.20.12.5", badge("成功", "success")] },
        ], { compact: true }),
      },
    ],
  },
  {
    id: "add-user",
    title: "添加用户",
    group: "用户与权限",
    nav: "users",
    lead: "为教师、学生或运维人员创建账号，绑定组织、角色和初始访问范围。",
    content: () => `<div class="staff-modal-preview">${surface("用户目录", dataTable([{ label: "用户", width: "45%" }, { label: "身份", width: "20%" }, { label: "组织", width: "25%" }, { label: "状态", width: "10%" }], [
      { cells: [personCell("孙老师", "T1002"), "教师", "计算机学院", badge("正常", "success")] },
      { cells: [personCell("李同学", "2023002"), "学生", "计算机学院", badge("正常", "success")] },
    ], { compact: true }), { iconName: "users-round" })}</div>`,
    modal: {
      title: "添加用户",
      subtitle: "账号将通过校内身份服务完成绑定。",
      wide: true,
      body: `<div class="choice-row"><button type="button" class="selection-card is-selected" data-action="select-choice"><strong>${icon("search")}查找校内账号</strong><p>按工号、学号或邮箱匹配。</p></button><button type="button" class="selection-card" data-action="select-choice"><strong>${icon("mail-plus")}发送邀请</strong><p>给尚未开通账号的用户发送邀请。</p></button></div><div class="form-grid">${formField("工号 / 学号", input("2024032"))}${formField("姓名", input("周同学"))}${formField("所属组织", select(["计算机学院", "软件学院", "教务处"]))}${formField("初始角色", select(["学生", "教师", "运维管理员"]))}</div>${formField("初始课程范围", select(["Web开发技术", "JavaEE课程设计", "暂不分配"]))}${notice("新账号首次登录前不会获得任何课程或文档访问权限。", "info", "shield-check")}`,
      footer: `${button("取消", { action: "close-modal" })}${button("创建并发送邀请", { variant: "primary", iconName: "send", action: "toast", message: "用户邀请已发送。" })}`,
    },
  },
  {
    id: "roles",
    title: "角色权限矩阵",
    group: "用户与权限",
    nav: "users",
    lead: "维护教师、学生和运维角色的权限边界，识别过度授权与职责冲突。",
    actions: `${button("导出权限矩阵", { iconName: "download", action: "toast", message: "权限矩阵已导出。" })}${button("新增角色", { variant: "primary", iconName: "shield-plus", action: "toast", message: "新增角色表单已打开。" })}`,
    panels: [
      {
        title: "权限矩阵",
        iconName: "table-properties",
        span: 2,
        body: `<div class="permission-matrix"><div class="permission-head">权限项</div>${["教师", "学生", "教学助理", "运维管理员"].map((roleName) => `<div class="permission-head">${roleName}</div>`).join("")}${[
          ["查看课程文档", [1, 1, 1, 1]],
          ["上传课程模板", [1, 0, 0, 1]],
          ["批改作业", [1, 0, 1, 0]],
          ["管理课程成员", [1, 0, 0, 1]],
          ["查看操作日志", [0, 0, 0, 1]],
          ["变更用户角色", [0, 0, 0, 1]],
          ["清理存储对象", [0, 0, 0, 1]],
        ].map(([permission, values]) => `<div class="permission-name">${permission}</div>${values.map((value) => `<div class="permission-cell">${value ? badge("允许", "success", true) : `<span class="permission-none">${icon("minus")}</span>`}</div>`).join("")}`).join("")}</div>`,
      },
      {
        title: "风险提示",
        iconName: "shield-alert",
        span: 2,
        body: listRows([
          { iconName: "circle-alert", title: "1 个自定义角色拥有“日志查看 + 角色变更”组合权限", meta: "可能形成独立审批闭环，建议拆分职责", aside: badge("待复核", "warning"), tone: "is-warning" },
        ]),
      },
    ],
  },
  {
    id: "create-role",
    title: "新增角色",
    group: "用户与权限",
    nav: "users",
    lead: "定义角色用途、授权范围和权限项，提交前执行职责冲突检查。",
    content: () => `<div class="staff-modal-preview">${surface("角色权限矩阵", listRows([{ title: "教师", meta: "教学文档、课程与作业", aside: badge("内置", "info") }, { title: "学生", meta: "个人文档、协作与提交", aside: badge("内置", "info") }, { title: "运维管理员", meta: "课程治理、审计、安全与存储", aside: badge("内置", "success") }]), { iconName: "shield-check" })}</div>`,
    modal: {
      title: "新增自定义角色",
      subtitle: "自定义角色仅用于授权范围受限的教学或运维协作。",
      wide: true,
      body: `<div class="form-grid">${formField("角色名称", input("院系教学审核员"))}${formField("角色类型", select(["教学治理", "运维协作"]))}${formField("有效期", select(["长期", "本学期", "90 天"]))}${formField("角色负责人", input("刘老师 · A2001"))}</div>${formField("角色说明", textarea("查看课程运行数据、复核异常提交，不可变更用户角色。", 3))}<div class="permission-picker">${["查看课程", "查看用户", "复核异常", "查看日志", "变更角色", "清理存储"].map((item, index) => `<label class="permission-option ${index < 4 ? "is-checked" : ""}"><input type="checkbox" ${index < 4 ? "checked" : ""}><span>${item}</span></label>`).join("")}</div>${notice("未选择“变更角色”和“清理存储”，当前组合未触发高风险职责冲突。", "info", "shield-check")}`,
      footer: `${button("取消", { action: "close-modal" })}${button("创建角色", { variant: "primary", iconName: "shield-plus", action: "toast", message: "自定义角色已创建。" })}`,
    },
  },
  {
    id: "change-role",
    title: "变更用户角色",
    group: "用户与权限",
    nav: "users",
    lead: "确认角色变更范围、生效时间和影响，变更会立即写入审计日志。",
    content: () => `<div class="staff-modal-preview">${surface("用户目录", dataTable([{ label: "用户", width: "36%" }, { label: "当前角色", width: "20%" }, { label: "所属组织", width: "24%" }, { label: "状态", width: "20%" }], [
      { cells: [personCell("孙老师", "T1002"), "教师", "计算机学院", badge("正常", "success")] },
      { cells: [personCell("刘老师", "A2001"), "运维管理员", "教务处", badge("正常", "success")] },
    ], { compact: true }), { iconName: "users-round" })}</div>`,
    modal: {
      title: "变更用户角色",
      subtitle: "账号：刘老师 · A2001 · 教务处",
      body: `<div class="form-grid">${formField("当前角色", input("教学审核员", { readonly: true }))}${formField("目标角色", select(["院系教学审核员", "教师", "运维管理员"]))}</div>${formField("生效时间", select(["立即生效", "下次登录时生效"]))}${formField("变更原因", textarea("参与本学期课程质量复核，需增加院系教学审核权限。", 3))}${reviewList([{ title: "权限范围增加 4 项", meta: "课程、用户、异常复核和日志查看。", selected: true }, { title: "不会获得角色变更权限", meta: "高风险职责保持分离。", selected: true }, { title: "变更写入审计日志", meta: "记录操作人、对象、原因和结果。", selected: true }])}`,
      footer: `${button("取消", { action: "close-modal" })}${button("确认变更", { variant: "primary", iconName: "key-round", action: "toast", message: "用户角色已变更。" })}`,
    },
  },
  {
    id: "access-policy",
    title: "访问策略",
    group: "审计与安全",
    nav: "security",
    lead: "配置统一身份认证、课程访问、会话安全和敏感操作验证策略。",
    actions: button("保存策略", { variant: "primary", iconName: "save", action: "toast", message: "访问策略已保存。" }),
    panels: [
      {
        title: "身份与会话",
        iconName: "fingerprint",
        body: `${settingRow("强制统一身份认证", "教师、学生和运维账号均使用校内身份服务。", switchControl(true, "强制统一身份认证"))}${settingRow("异常登录二次验证", "检测到异地或新设备登录时要求验证。", switchControl(true, "异常登录二次验证"))}${settingRow("会话空闲超时", "超过 30 分钟无操作自动退出。", switchControl(true, "会话空闲超时"))}<div class="form-grid">${formField("单次登录有效期", select(["8 小时", "12 小时", "24 小时"]))}${formField("并发会话上限", select(["1 个", "3 个", "5 个"]))}</div>`,
      },
      {
        title: "敏感操作验证",
        iconName: "shield-check",
        body: `${settingRow("角色变更", "要求二次确认并填写原因。", switchControl(true, "角色变更二次确认"))}${settingRow("账号冻结", "要求输入完整工号或学号。", switchControl(true, "账号冻结二次确认"))}${settingRow("存储清理", "要求管理员审批并保留对象清单。", switchControl(true, "存储清理二次确认"))}${notice("课程文档访问仍以课程身份和共享范围为主，不由运维角色继承。", "info", "lock-keyhole")}`,
      },
      {
        title: "策略适用范围",
        iconName: "layers-3",
        span: 2,
        body: dataTable([{ label: "策略组", width: "28%" }, { label: "适用角色", width: "25%" }, { label: "最近修改", width: "19%" }, { label: "状态", width: "14%" }, { label: "操作", width: "14%", align: "right" }], [
          { cells: ["统一身份与会话", "全部角色", "今天 09:10", badge("生效中", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "策略详情已打开。" })] },
          { cells: ["敏感操作验证", "运维管理员", "昨天 17:35", badge("生效中", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "策略详情已打开。" })] },
          { cells: ["课程访问策略", "教师 / 学生", "10月25日", badge("生效中", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "策略详情已打开。" })] },
        ], { compact: true }),
      },
    ],
  },
  {
    id: "conflicts",
    title: "权限冲突复核",
    group: "审计与安全",
    nav: "security",
    lead: "识别职责分离冲突、重复授权和过期临时权限，并记录处理结论。",
    actions: button("重新扫描", { variant: "primary", iconName: "scan-search", action: "toast", message: "权限冲突扫描已完成。" }),
    stats: [
      { label: "高风险冲突", value: "1", note: "角色变更 + 审计", color: "#b91c1c" },
      { label: "重复授权", value: "3", note: "来源不同但权限相同", color: "#b45309" },
      { label: "过期临时权限", value: "6", note: "今日到期", color: "#2563eb" },
      { label: "已处理", value: "18", note: "本月", color: "#15803d" },
    ],
    panels: [
      {
        title: "待复核冲突",
        iconName: "shield-alert",
        body: dataTable([{ label: "对象", width: "33%" }, { label: "冲突项", width: "32%" }, { label: "风险", width: "15%" }, { label: "发现时间", width: "20%" }], [
          { cells: [personCell("刘老师", "A2001 · 运维管理员", "刘"), "审计日志 + 角色变更", badge("高", "danger"), "今天 09:18"] },
          { cells: [personCell("孙老师", "T1002 · 教师", "孙"), "课程成员 + 评分复核", badge("中", "warning"), "昨天 15:22"] },
          { cells: [personCell("王助教", "T1088 · 教学助理", "王"), "临时批改权限即将过期", badge("低", "info"), "昨天 11:05"] },
        ], { compact: true }),
      },
      {
        title: "处置建议",
        iconName: "lightbulb",
        body: `${reviewList([{ title: "拆分刘老师的角色变更权限", meta: "将角色变更交给独立审批管理员。", selected: true }, { title: "保留孙老师的评分复核权限", meta: "属于课程负责人职责范围。", selected: false }, { title: "到期后自动回收王助教权限", meta: "无需额外人工处理。", selected: true }])}${notice("高风险冲突需在审计日志中记录处置原因与审批人。", "warning", "shield-check")}`,
      },
    ],
  },
  {
    id: "audit",
    title: "操作日志",
    group: "审计与安全",
    nav: "security",
    lead: "检索账号、课程、文档、权限和系统级操作，定位异常访问与关键变更。",
    actions: `${button("导出日志", { iconName: "download", action: "toast", message: "操作日志已导出。" })}${button("高级筛选", { variant: "primary", iconName: "list-filter", action: "toast", message: "高级筛选面板已展开。" })}`,
    content: () => `<div class="stack">${surface("筛选条件", `<div class="filter-row">${filterBar([{ label: "全部", value: "all" }, { label: "权限变更", value: "permission" }, { label: "文档访问", value: "access" }, { label: "登录安全", value: "login" }], "ops-audit")}<div class="search-field">${icon("search")}${input("", { placeholder: "搜索账号、课程或事件编号" })}</div>${select(["最近 24 小时", "最近 7 天", "自定义时间"])}</div>`, { iconName: "filter" })}${surface("审计事件", dataTable(
      [{ label: "时间", width: "14%" }, { label: "事件", width: "31%" }, { label: "操作者", width: "17%" }, { label: "对象", width: "17%" }, { label: "结果", width: "10%" }, { label: "详情", width: "11%", align: "right" }],
      [
        { state: "login", cells: ["10:42:18", "越权访问被拒绝", personCell("2024017", "学生账号"), "Web开发课程设计报告", badge("拒绝", "danger"), button("查看", { small: true, variant: "ghost", action: "toast", message: "审计事件详情已打开。" })] },
        { state: "permission", cells: ["09:18:02", "变更用户角色", personCell("刘老师", "运维管理员"), "孙老师 · T1002", badge("成功", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "审计事件详情已打开。" })] },
        { state: "access", cells: ["08:55:44", "课程文档导出", personCell("孙老师", "教师"), "Web开发课程设计模板.pdf", badge("成功", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "审计事件详情已打开。" })] },
        { state: "login", cells: ["08:42:10", "异地设备登录", personCell("T1008", "教师账号"), "身份认证服务", badge("二次验证", "warning"), button("查看", { small: true, variant: "ghost", action: "toast", message: "审计事件详情已打开。" })] },
        { state: "permission", cells: ["昨天 17:35", "新增自定义角色", personCell("刘老师", "运维管理员"), "院系教学审核员", badge("成功", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "审计事件详情已打开。" })] },
      ],
      { filterGroup: "ops-audit", compact: true },
    ))}</div>`,
  },
  {
    id: "audit-detail",
    title: "审计事件详情",
    group: "审计与安全",
    nav: "security",
    lead: "查看事件完整链路、请求上下文、影响对象和处置建议。",
    actions: button("导出事件报告", { iconName: "download", action: "toast", message: "审计事件报告已导出。" }),
    panels: [
      {
        title: "事件摘要",
        iconName: "file-search",
        body: `<div class="audit-summary"><div><span class="surface-meta">事件编号</span><strong>AUD-20261028-104218</strong></div><div><span class="surface-meta">风险等级</span>${badge("中风险", "warning")}</div><div><span class="surface-meta">处理结果</span>${badge("已拒绝", "danger")}</div></div><div class="detail-grid detail-grid-four">${[["发生时间", "2026-10-28 10:42:18"], ["操作者", "账号 2024017"], ["来源 IP", "10.20.12.5"], ["终端", "Chrome / Windows"]].map(([label, value]) => `<div class="detail-item"><span>${label}</span><strong>${value}</strong></div>`).join("")}</div>${notice("用户尝试访问未加入课程的文档，服务端在权限校验阶段拒绝请求。", "info", "shield-x")}`,
      },
      {
        title: "影响与建议",
        iconName: "triangle-alert",
        body: `${listRows([{ iconName: "file-lock-2", title: "目标文档未被读取", meta: "权限校验在内容返回前终止", aside: badge("无泄露", "success") }, { iconName: "user-round-x", title: "账号近期第 1 次异常访问", meta: "未触发账号冻结阈值", aside: badge("观察", "info") }, { iconName: "bell-ring", title: "建议通知课程负责人", meta: "孙老师 · Web开发技术", aside: button("通知", { small: true, action: "toast", message: "已通知课程负责人。" }) }])}${notice("若 24 小时内再次出现同类访问，将自动升级为高风险事件。", "warning", "shield-alert")}`,
      },
      {
        title: "事件链路",
        iconName: "route",
        span: 2,
        body: `<div class="timeline">${[["10:42:17", "身份校验通过", "账号状态正常，会话有效。"], ["10:42:18", "课程访问校验", "未找到课程成员关系。"], ["10:42:18", "策略引擎拒绝访问", "返回 403，内容未加载。"], ["10:42:19", "写入审计日志", "事件编号与追踪 ID 已生成。"]].map(([time, title, meta]) => `<div class="timeline-row"><span class="timeline-dot"></span><div class="timeline-copy"><strong>${title}</strong><span>${time} · ${meta}</span></div></div>`).join("")}</div>`,
      },
    ],
  },
  {
    id: "anomaly",
    title: "异常复核",
    group: "审计与安全",
    nav: "security",
    lead: "根据访问频率、权限边界和登录风险复核异常行为，记录处置结论。",
    actions: `${button("标记为误报", { iconName: "check", action: "toast", message: "异常事件已标记为误报。" })}${button("升级事件", { variant: "primary", iconName: "arrow-up-right", action: "toast", message: "异常事件已升级。" })}`,
    content: () => `<div class="split-layout is-uneven">
      <section class="surface"><div class="surface-header"><div class="surface-title">${icon("shield-alert")}AUD-20261028-104218</div>${badge("中风险", "warning")}</div><div class="surface-body"><div class="risk-summary"><div class="risk-score">72</div><div><strong>权限边界异常</strong><p>账号在 3 分钟内访问 6 个未授权课程文档，全部被拒绝。</p></div></div><div class="result-section"><h3>${icon("scan-search")}风险因子</h3>${reviewList([{ title: "未加入课程", meta: "目标文档不属于当前账号课程范围。", selected: true }, { title: "连续访问", meta: "3 分钟内 6 次同类请求。", selected: true }, { title: "可信设备", meta: "登录设备与近期常用设备一致。", selected: false }])}</div><div class="result-section"><h3>${icon("file-text")}证据摘要</h3><p>请求均来自同一会话与 IP，目标课程为 Web开发技术。内容返回前已被策略引擎终止，未发现文档读取。</p></div></div></section>
      <div class="stack">${surface("处置结论", `${formField("结论", select(["疑似越权尝试", "误报", "账号异常", "需要安全调查"]))}${formField("处置说明", textarea("账号近期首次出现同类行为，尚未达到冻结阈值，先通知课程负责人并持续观察 24 小时。", 4))}${settingRow("通知课程负责人", "发送事件摘要与文档范围。", switchControl(true, "通知课程负责人"))}${settingRow("加入观察名单", "24 小时内重复出现时升级。", switchControl(true, "加入观察名单"))}<button type="button" class="button primary is-block" data-action="toast" data-message="异常复核结论已提交。">${icon("check-check")}<span>提交复核</span></button>`, { iconName: "clipboard-check" })}${surface("关联证据", listRows([{ iconName: "logs", title: "6 条访问日志", meta: "包含时间、目标文档和拒绝结果", aside: button("展开", { small: true, variant: "ghost", action: "toast", message: "访问日志已展开。" }) }, { iconName: "monitor", title: "1 次设备校验", meta: "Windows / Chrome / 校内网络", aside: badge("可信设备", "success") }]), { iconName: "folder-search" })}</div>
    </div>`,
  },
  {
    id: "security",
    title: "安全事件中心",
    group: "审计与安全",
    nav: "security",
    lead: "实时汇总登录风险、越权尝试、异常导出和账号处置事件。",
    actions: `${button("导出事件", { iconName: "download", action: "toast", message: "安全事件已导出。" })}${button("新建处置任务", { variant: "primary", iconName: "plus", action: "toast", message: "安全处置任务已创建。" })}`,
    stats: [
      { label: "高风险事件", value: "3", note: "2 项待复核", color: "#b91c1c" },
      { label: "中风险事件", value: "8", note: "今日新增 2 项", color: "#b45309" },
      { label: "已阻断", value: "14", note: "成功率 100%", color: "#15803d" },
      { label: "平均响应", value: "6.2m", note: "目标低于 10 分钟", color: "#0e7490" },
    ],
    content: () => `<div class="dashboard-grid">${surface("事件列表", `${filterBar([{ label: "全部", value: "all" }, { label: "高风险", value: "high" }, { label: "待处理", value: "pending" }, { label: "已阻断", value: "blocked" }], "ops-security")}${[
      ["越权访问集中尝试", "账号 2024017 · Web开发技术", "高风险", "待复核", "今天 10:42"],
      ["异地设备登录", "账号 T1008 · Oracle数据库应用", "中风险", "二次验证通过", "今天 08:55"],
      ["异常批量导出", "账号 A2001 · 课程审计日志", "中风险", "已阻断", "昨天 18:20"],
      ["重复密码错误", "账号 2024017 · 身份认证", "低风险", "已自动限制", "昨天 16:42"],
    ].map(([title, meta, risk, status, time], index) => `<div class="security-event-row" data-filter-row="ops-security" data-row-state="${index === 0 ? "high" : index === 1 ? "pending" : "blocked"}"><span class="row-icon ${index === 0 ? "is-warning" : ""}">${icon(index === 0 ? "shield-alert" : index === 1 ? "monitor-smartphone" : "activity")}</span><div class="row-copy"><strong>${title}</strong><span>${meta} · ${time}</span></div>${badge(risk, index === 0 ? "danger" : index === 1 ? "warning" : "info")}${badge(status, index === 0 ? "warning" : "success")}${button("查看", { small: true, variant: "ghost", action: "toast", message: "安全事件详情已打开。" })}</div>`).join("")}`, { iconName: "shield-alert" })}<div class="stack">${surface("风险分布", `<div class="severity-grid"><div><strong>3</strong><span>高风险</span></div><div><strong>8</strong><span>中风险</span></div><div><strong>27</strong><span>低风险</span></div><div><strong>14</strong><span>已阻断</span></div></div>`, { iconName: "chart-no-axes-column" })}${surface("处置进度", progressRows([{ label: "待复核", value: "2 项", percent: 22, color: "#b91c1c" }, { label: "自动阻断", value: "14 项", percent: 62, color: "#15803d" }, { label: "已关闭", value: "22 项", percent: 84, color: "#0e7490" }]), { iconName: "circle-check-big" })}</div></div>`,
  },
  {
    id: "security-detail",
    title: "安全事件详情",
    group: "审计与安全",
    nav: "security",
    lead: "查看事件时间线、命中规则、受影响资源和应急处置操作。",
    actions: `${button("忽略事件", { iconName: "bell-off", action: "toast", message: "事件已加入忽略列表。" })}${button("冻结账号", { variant: "primary", iconName: "user-round-x", action: "toast", message: "冻结账号确认已打开。" })}`,
    panels: [
      {
        title: "事件概览",
        iconName: "shield-alert",
        body: `<div class="security-score high"><strong>86</strong><span>风险评分</span></div><div class="detail-grid detail-grid-four">${[["事件编号", "SEC-20261028-0042"], ["事件类型", "越权访问集中尝试"], ["来源", "10.20.12.5"], ["首次发现", "今天 10:39"]].map(([label, value]) => `<div class="detail-item"><span>${label}</span><strong>${value}</strong></div>`).join("")}</div>${notice("命中“短时间访问多个未授权课程文档”规则，所有请求已在内容返回前阻断。", "warning", "shield-x")}`,
      },
      {
        title: "影响范围",
        iconName: "radar",
        body: listRows([{ iconName: "user-round-x", title: "账号 2024017", meta: "学生 · 计算机学院", aside: badge("高风险", "danger") }, { iconName: "files", title: "6 份课程文档", meta: "均未返回内容", aside: badge("未泄露", "success") }, { iconName: "book-open", title: "Web开发技术", meta: "课程负责人：孙老师", aside: badge("已通知", "info") }]),
      },
      {
        title: "事件时间线",
        iconName: "route",
        span: 2,
        body: `<div class="timeline timeline-horizontal">${[["10:39", "首次异常请求", "访问未加入课程"], ["10:41", "规则命中", "3 分钟 4 次访问"], ["10:42", "自动阻断", "返回 403"], ["10:43", "事件生成", "通知运维人员"]].map(([time, title, meta]) => `<div class="timeline-step"><span>${time}</span><strong>${title}</strong><small>${meta}</small></div>`).join("")}</div>${reviewList([{ title: "保留全部请求日志", meta: "满足安全调查和审计追踪要求。", selected: true }, { title: "持续观察 24 小时", meta: "达到再次越权时自动升级。", selected: true }, { title: "通知课程负责人", meta: "告知目标课程和未发生内容泄露。", selected: true }])}`,
      },
    ],
  },
  {
    id: "freeze-account",
    title: "冻结账号确认",
    group: "审计与安全",
    nav: "security",
    lead: "执行账号冻结前确认影响范围、会话终止和恢复方式。",
    content: () => `<div class="staff-modal-preview">${surface("安全事件", dataTable([{ label: "事件", width: "35%" }, { label: "账号", width: "20%" }, { label: "风险", width: "16%" }, { label: "状态", width: "18%" }, { label: "操作", width: "11%", align: "right" }], [
      { cells: ["越权访问集中尝试", "2024017", badge("高风险", "danger"), badge("待处置", "warning"), button("冻结", { small: true, action: "toast", message: "冻结确认已打开。" })] },
      { cells: ["异地设备登录", "T1008", badge("中风险", "warning"), badge("验证通过", "success"), button("查看", { small: true, variant: "ghost", action: "toast", message: "事件详情已打开。" })] },
    ], { compact: true }), { iconName: "shield-alert" })}</div>`,
    modal: {
      title: "冻结账号 2024017？",
      subtitle: "账号将立即退出全部会话，课程和文档访问会被停止。",
      body: `${notice("冻结不会删除用户数据。账号可由运维管理员在复核后恢复。", "warning", "user-round-x")}${formField("处置原因", textarea("短时间连续访问多个未授权课程文档，达到高风险处置阈值。", 3))}${formField("输入学号确认", input("", { placeholder: "请输入 2024017" }))}${reviewList([{ title: "终止全部活动会话", meta: "当前 2 个会话将立即失效。", selected: true }, { title: "保留审计证据", meta: "访问日志与事件上下文继续保留。", selected: true }, { title: "通知账号所属院系", meta: "计算机学院教学管理员。", selected: false }])}`,
      footer: `${button("取消", { action: "close-modal" })}${button("确认冻结", { variant: "primary", iconName: "lock-keyhole", action: "toast", message: "账号已冻结。" })}`,
    },
  },
  {
    id: "storage",
    title: "存储与回收站",
    group: "存储与资源",
    nav: "storage",
    lead: "查看团队存储、过期对象和回收站容量，安排安全清理与恢复。",
    actions: `${button("导出存储清单", { iconName: "download", action: "toast", message: "存储清单已导出。" })}${button("清理存储", { variant: "primary", iconName: "trash-2", action: "toast", message: "清理确认已打开。" })}`,
    stats: [
      { label: "总容量", value: "2TB", note: "团队共享存储", color: "#0e7490" },
      { label: "已使用", value: "1.36TB", note: "占用 68%", color: "#2563eb" },
      { label: "回收站", value: "4.8GB", note: "128 个对象", color: "#b45309" },
      { label: "可释放", value: "18.6GB", note: "过期与重复对象", color: "#15803d" },
    ],
    panels: [
      {
        title: "容量分布",
        iconName: "hard-drive",
        body: `<div class="storage-meter"><strong>68%</strong><span>1.36TB / 2TB</span><div class="progress is-large"><i style="width:68%"></i></div></div>${progressRows([{ label: "课程文档", value: "820GB", percent: 60, color: "#0e7490" }, { label: "版本快照", value: "310GB", percent: 23, color: "#2563eb" }, { label: "导出与回收站", value: "230GB", percent: 17, color: "#b45309" }])}`,
      },
      {
        title: "近期存储事件",
        iconName: "activity",
        body: listRows([{ iconName: "trash-2", title: "自动清理任务", meta: "每月 1 日 02:00 · 保留 30 天", aside: badge("计划中", "info") }, { iconName: "archive-restore", title: "恢复 12 个对象", meta: "刘老师 · 昨天 16:20", aside: badge("成功", "success") }, { iconName: "triangle-alert", title: "重复文件检测", meta: "发现 38 个可合并对象", aside: badge("待复核", "warning") }]),
      },
      {
        title: "回收站对象",
        iconName: "archive",
        span: 2,
        body: dataTable([{ label: "对象", width: "34%" }, { label: "来源", width: "20%" }, { label: "大小", width: "12%" }, { label: "删除时间", width: "16%" }, { label: "剩余保留", width: "10%" }, { label: "操作", width: "8%", align: "right" }], [
          { cells: [docCell("旧版课程设计模板.docx", "模板版本 v1.5"), "Web开发技术", "42MB", "10月20日", "5 天", button("恢复", { small: true, variant: "ghost", action: "toast", message: "文件已恢复。" })] },
          { cells: [docCell("批量导出_1028.zip", "含 36 个 PDF"), "下载中心", "1.2GB", "10月25日", "10 天", button("恢复", { small: true, variant: "ghost", action: "toast", message: "导出包已恢复。" })] },
          { cells: [docCell("旧版用户导入名单.xlsx", "教务名单快照"), "用户目录", "2.4MB", "10月18日", "已过期", button("清理", { small: true, action: "toast", message: "对象已加入清理队列。" })] },
        ], { compact: true }),
      },
    ],
  },
  {
    id: "cleanup",
    title: "存储清理确认",
    group: "存储与资源",
    nav: "storage",
    lead: "确认清理对象、保留期和恢复窗口。永久删除后不可恢复。",
    content: () => `<div class="staff-modal-preview">${surface("回收站", dataTable([{ label: "对象", width: "40%" }, { label: "大小", width: "16%" }, { label: "删除时间", width: "18%" }, { label: "保留状态", width: "16%" }, { label: "操作", width: "10%", align: "right" }], [
      { cells: ["旧版课程设计模板.docx", "42MB", "10月20日", badge("剩余 5 天", "warning"), button("恢复", { small: true, variant: "ghost", action: "toast", message: "文件已恢复。" })] },
      { cells: ["旧版用户导入名单.xlsx", "2.4MB", "10月18日", badge("已过期", "danger"), button("清理", { small: true, action: "toast", message: "清理确认已打开。" })] },
    ], { compact: true }), { iconName: "archive" })}</div>`,
    modal: {
      title: "永久清理 128 个过期对象？",
      subtitle: "预计释放 4.8GB，操作完成后无法恢复。",
      body: `${notice("仅包含已超过 30 天保留期的回收站对象，不处理仍在版本链中的历史快照。", "warning", "triangle-alert")}${reviewList([{ title: "过期导出文件 84 个", meta: "共 4.1GB · 下载有效期已结束。", selected: true }, { title: "废弃导入名单 24 个", meta: "共 620MB · 仅保留最近一次成功导入。", selected: true }, { title: "失效临时预览 20 个", meta: "共 80MB · 已无关联文档。", selected: true }])}${formField("输入 CLEANUP 确认", input("", { placeholder: "请输入 CLEANUP" }))}`,
      footer: `${button("取消", { action: "close-modal" })}${button("永久清理", { variant: "primary", iconName: "trash-2", action: "toast", message: "过期对象已清理。" })}`,
    },
  },
  {
    id: "services",
    title: "系统服务状态",
    group: "存储与资源",
    nav: "storage",
    lead: "监控身份认证、文档解析、协作同步、存储与通知服务的可用性和延迟。",
    actions: `${button("查看告警", { iconName: "bell-ring", action: "toast", message: "服务告警列表已打开。" })}${button("刷新状态", { variant: "primary", iconName: "refresh-cw", action: "toast", message: "服务状态已刷新。" })}`,
    stats: [
      { label: "服务可用性", value: "99.96%", note: "过去 24 小时", color: "#15803d" },
      { label: "平均延迟", value: "168ms", note: "P95 620ms", color: "#0e7490" },
      { label: "活动告警", value: "2", note: "1 项需关注", color: "#b45309" },
      { label: "自动降级", value: "0", note: "当前未触发", color: "#2563eb" },
    ],
    content: () => `<div class="service-grid">${[
      ["身份认证", "运行正常", "99.99%", "86ms", "shield-check", "success"],
      ["文档解析", "轻微延迟", "99.72%", "8.4s", "file-scan", "warning"],
      ["协作同步", "运行正常", "99.98%", "42ms", "users", "success"],
      ["存储服务", "运行正常", "99.95%", "128ms", "database", "success"],
      ["通知服务", "运行正常", "99.96%", "210ms", "bell", "success"],
      ["导出服务", "观察中", "99.84%", "1.2s", "file-output", "warning"],
    ].map(([name, status, availability, latency, iconName, tone]) => `<section class="surface service-card"><div class="service-card-head"><span class="row-icon ${tone === "warning" ? "is-warning" : "is-success"}">${icon(iconName)}</span>${badge(status, tone)}</div><h3>${name}</h3><div class="service-metrics"><div><strong>${availability}</strong><span>可用性</span></div><div><strong>${latency}</strong><span>平均延迟</span></div></div><button class="button link" type="button" data-action="toast" data-message="${name}监控详情已打开。">查看监控${icon("arrow-up-right")}</button></section>`).join("")}</div>`,
  },
  {
    id: "config-changes",
    title: "配置变更记录",
    group: "存储与资源",
    nav: "storage",
    lead: "追踪影响系统运行和访问控制的配置变更，查看审批人与回滚范围。",
    actions: `${button("导出记录", { iconName: "download", action: "toast", message: "配置变更记录已导出。" })}${button("新建变更", { variant: "primary", iconName: "plus", action: "toast", message: "新建配置变更入口已打开。" })}`,
    panels: [
      {
        title: "变更记录",
        iconName: "list-restart",
        span: 2,
        body: dataTable([{ label: "变更", width: "31%" }, { label: "影响范围", width: "19%" }, { label: "提交人", width: "16%" }, { label: "审批人", width: "14%" }, { label: "时间", width: "12%" }, { label: "状态", width: "8%" }], [
          { cells: [docCell("文档解析并发上限调整", "并发 8 → 12"), "文档解析服务", "陈工", "刘老师", "今天 09:30", badge("已生效", "success")] },
          { cells: [docCell("异常登录阈值调整", "5 次 → 3 次 / 10 分钟"), "身份认证服务", "刘老师", "赵主管", "昨天 17:20", badge("已生效", "success")] },
          { cells: [docCell("存储清理周期调整", "自动清理 30 天 → 45 天"), "存储服务", "陈工", "刘老师", "10月25日", badge("已回滚", "warning")] },
          { cells: [docCell("通知发送限流", "每分钟 300 → 500"), "通知服务", "赵主管", "刘老师", "10月23日", badge("已生效", "success")] },
        ], { compact: true }),
      },
      {
        title: "当前变更窗口",
        iconName: "calendar-clock",
        span: 2,
        body: `${notice("今天 22:00–23:00 计划进行文档解析服务扩缩容，预计无用户可见中断。", "info", "calendar-clock")}${progressRows([{ label: "变更审批", value: "2 / 2 已通过", percent: 100, color: "#15803d" }, { label: "回滚预案", value: "已确认", percent: 100, color: "#15803d" }, { label: "通知相关角色", value: "教师 126 / 运维 8", percent: 80, color: "#2563eb" }])}`,
      },
    ],
  },
  {
    id: "messages",
    title: "消息与告警",
    group: "消息与个人中心",
    nav: "messages",
    lead: "集中处理系统告警、权限变更、课程治理和审批通知。",
    actions: button("全部标为已读", { iconName: "check-check", action: "mark-all-read" }),
    content: () => `<div class="message-layout">
      <section class="surface"><div class="surface-header"><div class="surface-title">${icon("bell-ring")}运维消息</div><span class="surface-meta">${state.messagesRead ? "全部已读" : "3 条未读"}</span></div><div class="surface-body"><div class="message-toolbar">${filterBar([{ label: "全部", value: "all" }, { label: "告警", value: "alert" }, { label: "审批", value: "approval" }, { label: "课程", value: "course" }], "ops-messages")}</div><div class="message-list">${[
        ["高风险安全事件待复核", "账号 2024017 · 3 分钟内访问 6 个未授权文档", "今天 10:43", true, "shield-alert"],
        ["角色变更等待审批", "刘老师申请增加院系教学审核权限", "今天 09:22", true, "key-round"],
        ["文档解析服务延迟升高", "P95 延迟达到 8.4 秒 · 已持续 12 分钟", "今天 09:06", true, "activity"],
        ["课程成员同步完成", "Web开发技术新增 1 名学生，已发送邀请", "今天 09:12", false, "users-round"],
        ["存储清理计划待确认", "128 个过期对象将在今晚自动清理", "昨天 18:30", false, "archive"],
      ].map(([title, meta, time, unread, iconName], index) => `<div class="message-row ${unread && !state.messagesRead ? "is-unread" : ""}" data-filter-row="ops-messages" data-row-state="${index < 3 ? "alert" : index === 3 ? "course" : "approval"}"><span class="row-icon ${index === 0 ? "is-warning" : ""}">${icon(iconName)}</span><div class="message-copy"><strong>${title}${unread && !state.messagesRead ? '<span class="dot"></span>' : ""}</strong><p>${meta}</p></div><div><div class="message-time">${time}</div><div class="message-actions">${button("查看", { small: true, variant: "ghost", action: "toast", message: "消息详情已打开。" })}</div></div></div>`).join("")}</div></div></section>
      <aside class="surface"><div class="surface-header"><div class="surface-title">${icon("bell-cog")}告警偏好</div></div><div class="surface-body">${settingRow("高风险即时电话", "同时发送给当班运维人员。", switchControl(true, "高风险即时电话"))}${settingRow("审批消息", "站内消息 + 邮件。", switchControl(true, "审批消息"))}${settingRow("课程同步摘要", "每天 18:00 汇总。", switchControl(false, "课程同步摘要"))}${notice("高风险安全事件始终置顶，并触发即时提醒。", "warning", "pin")}</div></section>
    </div>`,
  },
  {
    id: "profile",
    title: "个人资料",
    group: "消息与个人中心",
    nav: "profile",
    lead: "维护运维账号的组织信息、联系方式和个人通知偏好。",
    actions: button("保存资料", { variant: "primary", iconName: "save", action: "toast", message: "个人资料已保存。" }),
    content: () => `<div class="profile-layout staff-profile-layout">
      <aside class="stack"><section class="surface profile-summary"><div class="profile-avatar">刘</div><h2>刘老师</h2><p>教学与系统运维 · 教务处</p>${badge("运维管理员", "success")}<div class="profile-meta"><div><strong>6</strong><span>治理课程</span></div><div><strong>8</strong><span>待办事项</span></div></div></section><section class="surface"><div class="security-list">${[["账号与安全", "双因素认证已开启", "shield-check"], ["通知偏好", "告警 + 审批 + 课程摘要", "bell"], ["操作权限", "用户、审计、存储与课程治理", "key-round"]].map(([title, meta, iconName]) => `<div class="security-row"><span class="row-icon">${icon(iconName)}</span><div class="row-copy"><strong>${title}</strong><span>${meta}</span></div>${icon("chevron-right")}</div>`).join("")}</div></section></aside>
      <div class="stack">${surface("基本信息", `<div class="profile-form-grid">${formField("姓名", input("刘老师"))}${formField("工号", input("A2001", { readonly: true }))}${formField("所属部门", input("教务处"))}${formField("职责范围", input("教学运行与系统运维"))}</div>${formField("工作邮箱", input("liu.admin@university.edu.cn"))}${formField("联系电话", input("138 0000 2401"))}`, { iconName: "user-round" })}${surface("通知偏好", `${settingRow("高风险告警", "站内消息、邮件和即时电话。", switchControl(true, "高风险告警"))}${settingRow("角色变更审批", "站内消息与邮件。", switchControl(true, "角色变更审批"))}${settingRow("每日运行摘要", "每天 08:30 汇总课程与服务状态。", switchControl(true, "每日运行摘要"))}`, { iconName: "bell-cog" })}</div>
    </div>`,
  },
  {
    id: "account",
    title: "账号与安全",
    group: "消息与个人中心",
    nav: "profile",
    lead: "管理运维账号的双因素认证、会话、登录记录和敏感操作验证。",
    actions: button("保存安全设置", { variant: "primary", iconName: "shield-check", action: "toast", message: "安全设置已保存。" }),
    panels: [
      {
        title: "认证与验证",
        iconName: "shield-check",
        body: `${settingRow("双因素认证", "使用校内身份服务 + 动态验证码。", switchControl(true, "双因素认证"))}${settingRow("敏感操作二次验证", "角色变更、冻结账号和存储清理。", switchControl(true, "敏感操作二次验证"))}${settingRow("新设备登录提醒", "通过邮件和站内消息通知。", switchControl(true, "新设备登录提醒"))}${notice("运维管理员必须保持双因素认证开启。", "info", "lock-keyhole")}`,
      },
      {
        title: "活动会话",
        iconName: "monitor-smartphone",
        body: listRows([{ iconName: "monitor", title: "Windows · Chrome", meta: "10.20.8.2 · 当前会话", aside: badge("当前", "success") }, { iconName: "laptop", title: "MacBook · Safari", meta: "10.20.8.7 · 昨天 18:42", aside: button("退出", { small: true, variant: "ghost", action: "toast", message: "远程会话已退出。" }) }]),
      },
      {
        title: "最近登录记录",
        iconName: "history",
        span: 2,
        body: dataTable([{ label: "时间", width: "19%" }, { label: "设备", width: "26%" }, { label: "来源 IP", width: "18%" }, { label: "位置", width: "19%" }, { label: "结果", width: "18%" }], [
          { cells: ["今天 08:42", "Windows · Chrome", "10.20.8.2", "校内网络", badge("成功", "success")] },
          { cells: ["昨天 18:42", "MacBook · Safari", "10.20.8.7", "校内网络", badge("成功", "success")] },
          { cells: ["昨天 02:14", "未知设备", "183.12.8.46", "校外网络", badge("已阻断", "danger")] },
        ], { compact: true }),
      },
    ],
  },
];

const ROLES = {
  teacher: {
    key: "teacher",
    workspace: "教师工作区",
    user: "孙老师",
    initial: "孙",
    identity: "教师 · Web开发技术",
    status: "在线 · 课程负责人",
    search: "搜索课程、学生、模板或教学文档",
    messageLabel: "教学通知",
    nav: [
      { key: "workbench", label: "工作台", icon: "layout-dashboard" },
      { key: "documents", label: "我的文档", icon: "files" },
      { key: "smart", label: "智能处理", icon: "sparkles" },
      { key: "format", label: "格式与模板", icon: "layout-template" },
      { key: "templates", label: "课程模板", icon: "library-big" },
      { key: "collaborate", label: "协作与版本", icon: "users" },
      { key: "courses", label: "课程与作业", icon: "book-open-check" },
      { key: "messages", label: "消息通知", icon: "bell" },
      { key: "profile", label: "个人中心", icon: "user-round" },
    ],
    routes: teacherRoutes,
  },
  operations: {
    key: "operations",
    workspace: "运维工作区",
    user: "刘老师",
    initial: "刘",
    identity: "教学与系统运维 · 教务处",
    status: "在线 · 运维管理员",
    search: "搜索用户、课程、审计事件或服务",
    messageLabel: "告警与通知",
    nav: [
      { key: "workbench", label: "运维工作台", icon: "layout-dashboard" },
      { key: "courses", label: "课程治理", icon: "book-open-check" },
      { key: "users", label: "用户与权限", icon: "users-round" },
      { key: "security", label: "审计与安全", icon: "shield-check" },
      { key: "storage", label: "存储与资源", icon: "server-cog" },
      { key: "messages", label: "消息与告警", icon: "bell-ring" },
      { key: "profile", label: "个人中心", icon: "user-round" },
    ],
    routes: operationsRoutes,
  },
};

const role = ROLES[ROLE_KEY] || ROLES.teacher;
const state = {
  index: 0,
  toastTimer: null,
  switches: new Set(),
  choices: new Set(),
  messagesRead: false,
};

function current() {
  return role.routes[state.index] || role.routes[0];
}

function toast(message) {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.classList.add("is-visible");
  clearTimeout(state.toastTimer);
  state.toastTimer = window.setTimeout(() => el.classList.remove("is-visible"), 1800);
}

function renderRoute(route) {
  return `<div class="staff-page">
    ${pageHead(route)}
    ${route.stats ? statGrid(route.stats) : ""}
    ${route.content ? `<div class="staff-custom-content">${route.content()}</div>` : panelGrid(route.panels)}
  </div>`;
}

function render() {
  const route = current();
  const app = document.getElementById("app");
  app.innerHTML = shell(role, route, renderRoute(route), modalMarkup(route.modal));
  if (window.lucide?.createIcons) {
    window.lucide.createIcons({
      attrs: { "stroke-width": 1.8, "aria-hidden": "true" },
    });
  }
}

function applyFilters(group, value) {
  document.querySelectorAll(`[data-filter-group="${group}"] [data-action="filter"]`).forEach((buttonEl) => {
    buttonEl.classList.toggle("is-active", buttonEl.dataset.filter === value);
  });
  document.querySelectorAll(`[data-filter-row="${group}"]`).forEach((row) => {
    row.hidden = value !== "all" && row.dataset.rowState !== value;
  });
}

function action(event) {
  const target = event.target.closest("[data-action]");
  if (!target) return;
  const name = target.dataset.action;

  if (name === "toast") {
    toast(target.dataset.message || "操作已受理。");
    return;
  }

  if (name === "toggle-switch") {
    target.classList.toggle("is-on");
    const key = target.getAttribute("aria-label") || "switch";
    if (state.switches.has(key)) state.switches.delete(key);
    else state.switches.add(key);
    target.setAttribute("aria-pressed", String(target.classList.contains("is-on")));
    return;
  }

  if (name === "filter") {
    const group = target.closest("[data-filter-group]")?.dataset.filterGroup;
    if (group) applyFilters(group, target.dataset.filter);
    return;
  }

  if (name === "select-choice") {
    const scope = target.closest(".selection-grid, .conversion-options, .choice-row");
    scope?.querySelectorAll("[data-action='select-choice']").forEach((item) => {
      item.classList.toggle("is-selected", item === target);
    });
    return;
  }

  if (name === "mark-all-read") {
    state.messagesRead = true;
    render();
    toast("全部消息已标为已读");
    return;
  }

  if (name === "close-modal") {
    document.querySelector("[data-modal-scrim]")?.setAttribute("hidden", "");
    return;
  }

  if (name === "toggle-panel") {
    target.closest(".conversion-layout")?.classList.toggle("is-collapsed");
    return;
  }
}

function applyInitialRoute() {
  const id = location.hash.replace("#", "");
  const index = role.routes.findIndex((route) => route.id === id);
  if (index >= 0) state.index = index;
  render();
}

document.addEventListener("click", action);
window.STAFF_ROUTE_META = {
  teacher: teacherRoutes.map(({ id, title, group, nav }) => ({ id, title, group, nav })),
  operations: operationsRoutes.map(({ id, title, group, nav }) => ({ id, title, group, nav })),
};
applyInitialRoute();
