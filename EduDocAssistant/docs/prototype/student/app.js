const ROUTE = [
  { id: "workbench", title: "学习工作台", group: "文档接入", nav: "workbench" },
  { id: "documents", title: "我的文档", group: "文档接入", nav: "documents" },
  { id: "upload", title: "上传文档", group: "文档接入", nav: "documents" },
  { id: "processing", title: "文档处理进度", group: "文档接入", nav: "documents" },
  { id: "preview", title: "文档预览", group: "文档接入", nav: "documents" },
  { id: "summary", title: "文本摘要", group: "智能处理", nav: "smart" },
  { id: "keypoints", title: "要点提取", group: "智能处理", nav: "smart" },
  { id: "qa", title: "文档问答", group: "智能处理", nav: "smart" },
  { id: "refusal", title: "问答无依据拒答", group: "智能处理", nav: "smart" },
  { id: "polish", title: "文本润色", group: "智能处理", nav: "smart" },
  { id: "templates", title: "课程模板", group: "格式与模板", nav: "format" },
  { id: "normalize", title: "代码与图表规范化", group: "格式与模板", nav: "format" },
  { id: "format", title: "格式规范校验", group: "格式与模板", nav: "format" },
  { id: "autofix", title: "一键修复确认", group: "格式与模板", nav: "format" },
  { id: "convert", title: "格式转换", group: "格式与模板", nav: "format" },
  { id: "collaborate", title: "协作编辑", group: "协作与版本", nav: "collaborate" },
  { id: "versions", title: "版本记录", group: "协作与版本", nav: "collaborate" },
  { id: "compare", title: "版本对比", group: "协作与版本", nav: "collaborate" },
  { id: "restore", title: "恢复版本确认", group: "协作与版本", nav: "collaborate" },
  { id: "share", title: "共享设置", group: "协作与版本", nav: "collaborate" },
  { id: "collaborator", title: "添加协作者", group: "协作与版本", nav: "collaborate" },
  { id: "export", title: "导出文档", group: "作业与交付", nav: "documents" },
  { id: "downloads", title: "下载中心", group: "作业与交付", nav: "documents" },
  { id: "assignments", title: "学生作业列表", group: "作业与交付", nav: "course" },
  { id: "submit", title: "提交学生作业", group: "作业与交付", nav: "course" },
  { id: "messages", title: "消息中心", group: "消息与个人中心", nav: "messages" },
  { id: "profile", title: "个人资料", group: "消息与个人中心", nav: "profile" },
];

const DOC_TITLE = "Web开发课程设计报告_张同学_v3.docx";
const COURSE = "Web开发技术";
const state = {
  index: 0,
  toastTimer: null,
  switches: new Set([
    "source-pages",
    "show-diff",
    "open-restored",
    "course-link",
    "export-links",
    "deadline",
    "format-alerts",
    "collab-alerts",
  ]),
  fileSelected: true,
  messagesRead: false,
  qaCleared: false,
  selectedPoints: new Set([0, 1, 2, 3, 4]),
  selectedFixes: new Set([0, 1, 2, 3]),
  templateModal: null,
  qaInput: "",
  shareScope: "course",
  previewSection: 0,
  summaryLength: "standard",
  summaryFocus: "structure",
  summaryPrompt: "",
  conversionMode: "docx-pdf",
  conversionDrawerCollapsed: false,
  downloadFilter: "all",
  assignmentFilter: "all",
  messageFilter: "all",
  editingProfile: false,
  profile: {
    name: "张同学",
    email: "zhang@campus.edu.cn",
  },
  docs: {
    query: "",
    course: "all",
    status: "all",
    selected: new Set([0]),
    removed: new Set(),
  },
  conversionStarted: false,
  exportDone: false,
  submitDone: false,
  modalOverride: "",
  dismissedRouteModal: "",
};

const icon = (name, className = "") =>
  `<i data-lucide="${name}" class="${className}" aria-hidden="true"></i>`;

const esc = (value) =>
  String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");

function button(label, options = {}) {
  const {
    variant = "",
    iconName = "",
    action = "",
    extra = "",
    title = "",
    disabled = false,
  } = options;
  return `<button type="button" class="button ${variant}" ${action ? `data-action="${action}"` : ""} ${title ? `title="${esc(title)}"` : ""} ${disabled ? "disabled" : ""} ${extra}>
    ${iconName ? icon(iconName) : ""}<span>${esc(label)}</span>
  </button>`;
}

function iconButton(iconName, options = {}) {
  const { action = "", label = "", extra = "", variant = "", disabled = false } = options;
  return `<button type="button" class="icon-button ${variant}" aria-label="${esc(label)}" title="${esc(label)}" ${action ? `data-action="${action}"` : ""} ${disabled ? "disabled" : ""} ${extra}>
    ${icon(iconName)}
  </button>`;
}

function badge(label, tone = "", noDot = false) {
  return `<span class="badge ${tone} ${noDot ? "no-dot" : ""}">${esc(label)}</span>`;
}

function pageHead({ breadcrumb, title, lead, actions = "" }) {
  return `<header class="page-head">
    <div>
      <div class="breadcrumb">${esc(breadcrumb)}</div>
      <h1 class="page-title">${esc(title)}</h1>
      ${lead ? `<p class="page-lead">${esc(lead)}</p>` : ""}
    </div>
    ${actions ? `<div class="head-actions">${actions}</div>` : ""}
  </header>`;
}

function surfaceHeader(title, meta = "", iconName = "", action = "") {
  return `<div class="surface-header">
    <div class="surface-title">${iconName ? icon(iconName) : ""}${esc(title)}</div>
    ${action || (meta ? `<span class="surface-meta">${esc(meta)}</span>` : "")}
  </div>`;
}

function rowIcon(iconName, tone = "") {
  return `<span class="row-icon ${tone}">${icon(iconName)}</span>`;
}

function navItems() {
  return [
    ["workbench", "工作台", "layout-dashboard", 0],
    ["documents", "我的文档", "files", 1],
    ["smart", "智能处理", "sparkles", 5],
    ["format", "格式与模板", "layout-template", 10],
    ["collaborate", "协作与版本", "users", 15],
    ["course", "课程与作业", "book-open-check", 23],
    ["profile", "个人中心", "user-round", 26],
    ["messages", "消息通知", "bell", 25],
  ]
    .map(
      ([key, label, iconName]) => `<button type="button" class="nav-item ${current().nav === key ? "is-active" : ""}">
        ${icon(iconName)}<span>${label}</span>
      </button>`,
    )
    .join("");
}

function topbar() {
  return `<header class="topbar">
    <div class="search-box" role="search" aria-label="全局搜索">
      ${icon("search")}
      <span>搜索文档、课程、模板或成员</span>
      <kbd>Ctrl K</kbd>
    </div>
    <div class="topbar-spacer"></div>
    <div class="top-divider"></div>
    ${iconButton("bell", { action: "open-messages", label: "打开消息中心" })}
    <div class="top-person">
      <span class="avatar">张</span>
      <div class="top-person-copy"><strong>张同学</strong><span>在线</span></div>
    </div>
  </header>`;
}

function shell(content, modal = "") {
  return `<div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"><img src="./assets/logo-mark.png" alt="校园智能文档助手 Logo"></span>
        <div class="brand-copy"><strong>校园智能文档助手</strong><span>EduDocAssistant</span></div>
      </div>
      <div class="nav-section-label">学生工作区</div>
      <nav class="primary-nav" aria-label="学生端主导航">${navItems()}</nav>
      <div class="sidebar-footer">
        <div class="sidebar-rule"></div>
        <div class="identity">
          <span class="avatar">张</span>
          <div class="identity-copy"><strong>张同学</strong><span>学生 · 计算机学院</span></div>
        </div>
      </div>
    </aside>
    <div class="workspace">${topbar()}<main class="main">${content}</main></div>
    ${modal}
  </div>`;
}

function current() {
  return ROUTE[state.index];
}

function dismissModal() {
  state.modalOverride = "";
  state.dismissedRouteModal = current().id;
  document.querySelector(".modal-scrim")?.remove();
}

function openModal(type) {
  state.modalOverride = type;
  state.dismissedRouteModal = "";
  render();
}

function modalForRoute() {
  const modalFactories = {
    upload: uploadModal,
    processing: processingModal,
    autofix: autoFixModal,
    restore: restoreModal,
    collaborator: collaboratorModal,
  };
  if (state.modalOverride) return modalFactories[state.modalOverride]?.() || "";
  if (state.dismissedRouteModal === current().id) return "";
  return modalFactories[current().id]?.() || "";
}

function switchControl(label, key) {
  const isOn = state.switches.has(key);
  return `<button type="button" class="switch ${isOn ? "is-on" : ""}" data-action="toggle-switch" data-switch="${key}" aria-label="${label}" aria-pressed="${isOn}"></button>`;
}

function downloadText(filename, text) {
  const blob = new Blob([text], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function toast(message) {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.classList.add("is-visible");
  clearTimeout(state.toastTimer);
  state.toastTimer = window.setTimeout(() => el.classList.remove("is-visible"), 1800);
}

function action(event) {
  const target = event.target.closest("[data-action]");
  if (!target) return;

  const name = target.dataset.action;
  switch (name) {
    case "remove-file":
      state.fileSelected = false;
      render();
      toast("已移除所选文件");
      break;
    case "select-file":
      state.fileSelected = true;
      render();
      toast("已选择示例文档");
      break;
    case "upload":
      openModal("upload");
      break;
    case "processing":
      openModal("processing");
      break;
    case "autofix":
      openModal("autofix");
      break;
    case "restore":
      openModal("restore");
      break;
    case "collaborator":
      openModal("collaborator");
      break;
    case "close-upload":
    case "close-processing":
    case "close-autofix":
    case "close-restore":
    case "close-collaborator":
      dismissModal();
      break;
    case "send-invite":
      dismissModal();
      toast("协作邀请已发送");
      break;
    case "confirm-restore":
      dismissModal();
      toast("已恢复为新的 v7 版本");
      break;
    case "convert":
      dismissModal();
      toast("已提交修复，正在生成新版本");
      break;
    case "cancel-convert":
      state.conversionStarted = false;
      render();
      toast("已取消本次转换");
      break;
    case "toggle-convert-drawer":
      state.conversionDrawerCollapsed = !state.conversionDrawerCollapsed;
      render();
      break;
    case "select-conversion":
      state.conversionMode = target.dataset.mode;
      render();
      break;
    case "select-preview-section":
      state.previewSection = Number(target.dataset.index);
      render();
      break;
    case "set-summary-length":
      state.summaryLength = target.dataset.length;
      render();
      break;
    case "start-conversion":
      state.conversionStarted = true;
      render();
      toast("转换任务已开始");
      break;
    case "start-export":
      state.exportDone = true;
      render();
      toast("导出已完成，已写入下载中心");
      break;
    case "confirm-submit":
      state.submitDone = true;
      render();
      toast("作业提交成功，已保留本次版本");
      break;
    case "download":
      downloadText(`${DOC_TITLE.replace(/\.docx$/i, "")}_导出.txt`, "校园智能文档助手演示导出文件。");
      toast("已开始下载");
      break;
    case "export": {
      const routeId = current().id;
      const selectedPoints = [...document.querySelectorAll("[data-action='toggle-point'].is-selected .review-copy")]
        .map((item) => item.innerText.trim())
        .join("\n\n");
      const contentByRoute = {
        summary: "文本摘要\n\n系统通过文档接入、智能处理、格式校验与协作版本，帮助学生完成课程报告。",
        keypoints: `要点清单\n\n${selectedPoints || "尚未选择要点"}`,
        qa: state.qaCleared ? "当前对话为空。" : "文档问答记录\n\n请参阅页面中的引用回答。",
        refusal: state.qaCleared ? "当前对话为空。" : "文档问答记录\n\n当前文档无横向对比数据，已明确拒答。",
        documents: "文档列表导出\n\nWeb开发课程设计报告_张同学_v3.docx",
        downloads: "下载记录导出\n\nWeb开发课程设计报告_张同学_v6.docx",
      };
      downloadText(
        `学生端_${current().title}_导出.txt`,
        contentByRoute[routeId] || `${current().title}导出内容`,
      );
      toast("文件已导出");
      break;
    }
    case "select-choice": {
      const group = target.closest(".selection-grid, .choice-row");
      group?.querySelectorAll("[data-action='select-choice']").forEach((item) => {
        const selected = item === target;
        item.classList.toggle("is-selected", selected);
        item.setAttribute("aria-pressed", String(selected));
      });
      break;
    }
    case "mark-read": {
      const row = target.closest(".message-row");
      row?.classList.remove("is-unread");
      target.remove();
      toast("已标记为已读");
      break;
    }
    case "mark-all-read":
      state.messagesRead = true;
      render();
      toast("全部消息已标为已读");
      break;
    case "copy":
      {
        const fallback = target.closest(".result-section")?.innerText || "https://edu.example/doc/7Q4K2M";
        const text = target.dataset.copyText || fallback;
        navigator.clipboard?.writeText(text).catch(() => {});
        toast("已复制到剪贴板");
      }
      break;
    case "toggle-switch":
      if (state.switches.has(target.dataset.switch)) state.switches.delete(target.dataset.switch);
      else state.switches.add(target.dataset.switch);
      target.classList.toggle("is-on");
      target.setAttribute("aria-pressed", String(target.classList.contains("is-on")));
      break;
    case "toggle-doc":
      if (target.checked) state.docs.selected.add(Number(target.dataset.index));
      else state.docs.selected.delete(Number(target.dataset.index));
      syncDocumentSelection();
      break;
    case "select-all-docs": {
      const visibleRows = [...document.querySelectorAll("[data-doc-row]")].filter((row) => !row.hidden);
      visibleRows.forEach((row) => {
        const index = Number(row.dataset.index);
        if (target.checked) state.docs.selected.add(index);
        else state.docs.selected.delete(index);
      });
      render();
      break;
    }
    case "delete-docs":
      state.docs.selected.forEach((index) => state.docs.removed.add(index));
      state.docs.selected.clear();
      render();
      toast("已删除所选文档");
      break;
    case "toggle-point":
      if (state.selectedPoints.has(Number(target.dataset.index))) state.selectedPoints.delete(Number(target.dataset.index));
      else state.selectedPoints.add(Number(target.dataset.index));
      render();
      break;
    case "toggle-fix":
      if (state.selectedFixes.has(Number(target.dataset.index))) state.selectedFixes.delete(Number(target.dataset.index));
      else state.selectedFixes.add(Number(target.dataset.index));
      render();
      break;
    case "open-template":
      state.templateModal = Number(target.dataset.index);
      render();
      break;
    case "close-template":
      state.templateModal = null;
      render();
      break;
    case "select-scope":
      state.shareScope = target.dataset.scope;
      render();
      toast("访问范围已更新");
      break;
    case "ask-question": {
      const input = document.querySelector("[data-qa-input]");
      const question = (input?.value || state.qaInput || "").trim();
      if (!question) {
        toast("请输入问题");
        break;
      }
      state.qaInput = question;
      state.qaCleared = false;
      render();
      toast("已根据当前文档生成回答");
      break;
    }
    case "clear-chat":
      state.qaInput = "";
      state.qaCleared = true;
      render();
      toast("对话已清除");
      break;
    case "filter-downloads":
      state.downloadFilter = target.dataset.filter;
      document.querySelectorAll("[data-download-filter]").forEach((item) => item.classList.toggle("is-active", item.dataset.filter === state.downloadFilter));
      document.querySelectorAll("[data-download-row]").forEach((row) => {
        row.hidden = state.downloadFilter !== "all" && row.dataset.downloadState !== state.downloadFilter;
      });
      break;
    case "filter-assignments":
      state.assignmentFilter = target.dataset.filter;
      document.querySelectorAll("[data-assignment-filter]").forEach((item) => item.classList.toggle("is-active", item.dataset.filter === state.assignmentFilter));
      document.querySelectorAll("[data-assignment-row]").forEach((row) => {
        row.hidden = state.assignmentFilter !== "all" && row.dataset.assignmentState !== state.assignmentFilter;
      });
      break;
    case "filter-messages":
      state.messageFilter = target.dataset.filter;
      document.querySelectorAll("[data-message-filter]").forEach((item) => item.classList.toggle("is-active", item.dataset.filter === state.messageFilter));
      document.querySelectorAll("[data-message-row]").forEach((row) => {
        const unread = row.classList.contains("is-unread");
        row.hidden = state.messageFilter === "unread" ? !unread : state.messageFilter !== "all" && row.dataset.messageType !== state.messageFilter;
      });
      break;
    case "edit-profile":
      state.editingProfile = true;
      render();
      break;
    case "save-profile":
      state.profile.name = document.querySelector("#profile-name")?.value.trim() || state.profile.name;
      state.profile.email = document.querySelector("#profile-email")?.value.trim() || state.profile.email;
      state.editingProfile = false;
      render();
      toast("个人资料已保存");
      break;
    case "cancel-profile-edit":
      state.editingProfile = false;
      render();
      toast("已取消编辑");
      break;
    case "edit-password":
      toast("密码修改入口已打开");
      break;
    case "view-login-records":
      toast("已加载最近登录记录");
      break;
    case "regenerate":
      toast("正在根据最新设置重新生成");
      break;
    case "save":
      toast("设置已保存");
      break;
    case "logout":
      toast("演示环境不执行退出登录");
      break;
    default:
      if (name) toast("该目标为独立静态画面，请从页面列表打开");
      break;
  }
}

function documentRows() {
  return [
    { name: DOC_TITLE, course: COURSE, time: "今天 21:10", status: "已处理", tone: "success" },
    { name: "Oracle课程设计_张同学_v2.docx", course: "Oracle 数据库应用", time: "今天 18:42", status: "待修复", tone: "warning" },
    { name: "JavaEE课程作业_小组版.docx", course: "JavaEE 程序设计", time: "昨天 22:05", status: "协作中", tone: "violet" },
    { name: "数据库原理复习提纲.pdf", course: "数据库原理", time: "10月24日", status: "已通过", tone: "info" },
    { name: "毕业论文_第二章_初稿.docx", course: "毕业设计", time: "10月22日", status: "草稿", tone: "" },
  ];
}

function applyDocumentFilters() {
  const rows = [...document.querySelectorAll("[data-doc-row]")];
  if (!rows.length) return;
  const query = state.docs.query.trim().toLowerCase();
  let visibleCount = 0;
  rows.forEach((row) => {
    const matchesQuery = !query || row.dataset.name.toLowerCase().includes(query);
    const matchesCourse = state.docs.course === "all" || row.dataset.course === state.docs.course;
    const matchesStatus = state.docs.status === "all" || row.dataset.status === state.docs.status;
    row.hidden = !(matchesQuery && matchesCourse && matchesStatus);
    if (!row.hidden) visibleCount += 1;
  });
  const empty = document.querySelector("[data-doc-empty]");
  if (empty) empty.hidden = visibleCount > 0;
  const count = document.querySelector("[data-doc-count]");
  if (count) count.textContent = `共 ${visibleCount} 份文档`;
  const selectAll = document.querySelector("[data-select-all-docs]");
  if (selectAll) {
    const visibleIndexes = rows.filter((row) => !row.hidden).map((row) => Number(row.dataset.index));
    selectAll.checked = visibleIndexes.length > 0 && visibleIndexes.every((index) => state.docs.selected.has(index));
    selectAll.indeterminate = visibleIndexes.some((index) => state.docs.selected.has(index)) && !selectAll.checked;
  }
  syncDocumentSelection();
}

function syncDocumentSelection() {
  const selectedCount = state.docs.selected.size;
  document.querySelectorAll("[data-selected-doc-count]").forEach((node) => {
    node.textContent = `已选择 ${selectedCount} 份`;
  });
  document.querySelectorAll("[data-delete-selected-docs]").forEach((node) => {
    node.disabled = selectedCount === 0;
  });
}

function documentsTable() {
  const rows = documentRows()
    .map((row, index) => ({ ...row, index }))
    .filter((row) => !state.docs.removed.has(row.index));
  return `<div class="surface">
    <div class="toolbar">
      <label class="search-field">${icon("search")}<span class="sr-only">搜索文档</span><input value="${esc(state.docs.query)}" placeholder="搜索文档名称" data-doc-search></label>
      <select class="select" aria-label="课程筛选" data-doc-course>
        <option value="all" ${state.docs.course === "all" ? "selected" : ""}>全部课程</option>
        <option value="${COURSE}" ${state.docs.course === COURSE ? "selected" : ""}>${COURSE}</option>
        <option value="Oracle 数据库应用" ${state.docs.course === "Oracle 数据库应用" ? "selected" : ""}>Oracle 数据库应用</option>
        <option value="JavaEE 程序设计" ${state.docs.course === "JavaEE 程序设计" ? "selected" : ""}>JavaEE 程序设计</option>
      </select>
      <select class="select" aria-label="状态筛选" data-doc-status>
        <option value="all" ${state.docs.status === "all" ? "selected" : ""}>全部状态</option>
        <option value="已处理" ${state.docs.status === "已处理" ? "selected" : ""}>已处理</option>
        <option value="待修复" ${state.docs.status === "待修复" ? "selected" : ""}>待修复</option>
        <option value="协作中" ${state.docs.status === "协作中" ? "selected" : ""}>协作中</option>
        <option value="已通过" ${state.docs.status === "已通过" ? "selected" : ""}>已通过</option>
      </select>
      <div class="toolbar-spacer"></div>
      ${button("上传文档", { variant: "primary", iconName: "upload", action: "upload" })}
    </div>
    <table class="table" aria-label="我的文档列表">
      <thead><tr>
        <th style="width:42px"><input type="checkbox" aria-label="全选当前筛选文档" data-action="select-all-docs" data-select-all-docs></th>
        <th style="width:39%">文档名称</th>
        <th style="width:18%">课程</th>
        <th style="width:15%">最近更新</th>
        <th style="width:13%">状态</th>
        <th style="width:10%; text-align:right">操作</th>
      </tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `<tr data-doc-row data-index="${row.index}" data-name="${esc(row.name)}" data-course="${esc(row.course)}" data-status="${esc(row.status)}">
            <td><input type="checkbox" aria-label="选择 ${esc(row.name)}" data-action="toggle-doc" data-index="${row.index}" ${state.docs.selected.has(row.index) ? "checked" : ""}></td>
            <td><div class="doc-cell">${icon("file-text")}<span>${esc(row.name)}</span></div></td>
            <td>${esc(row.course)}</td>
            <td>${esc(row.time)}</td>
            <td>${badge(row.status, row.tone)}</td>
            <td><div class="row-actions">${iconButton("more-horizontal", { action: "save", label: `更多操作 ${row.name}` })}</div></td>
          </tr>`,
          )
          .join("")}
        <tr data-doc-empty ${rows.length ? "hidden" : ""}><td colspan="6"><div class="empty-state" style="min-height:150px">${icon("file-search")}<strong>没有匹配的文档</strong><span>调整搜索词或筛选条件后重试。</span></div></td></tr>
      </tbody>
    </table>
    <div class="toolbar"><span class="surface-meta"><span data-doc-count>共 ${rows.length} 份文档</span> · <span data-selected-doc-count>已选择 ${state.docs.selected.size} 份</span></span><div class="toolbar-spacer"></div>${button("批量导出", { iconName: "download", action: "export", variant: "ghost" })}${button("删除", { iconName: "trash-2", variant: "ghost", action: "delete-docs", extra: `data-delete-selected-docs ${state.docs.selected.size === 0 ? "disabled" : ""}` })}</div>
  </div>`;
}

function screenWorkbench() {
  const continueRows = [
    {
      iconName: "file-text",
      title: DOC_TITLE,
      meta: `预览 · ${COURSE} · 今天 21:10`,
      action: "预览",
      go: "preview",
    },
    {
      iconName: "shield-check",
      title: "Oracle课程设计_张同学_v2.docx",
      meta: "格式校验 · 4 个阻断问题待修复",
      action: "继续修复",
      go: "format",
    },
    {
      iconName: "users",
      title: "JavaEE课程作业_小组版.docx",
      meta: "协作中 · 李同学刚刚更新",
      action: "打开协作",
      go: "collaborate",
    },
  ];
  const todoRows = [
    {
      iconName: "calendar-days",
      title: "Web开发课程设计报告",
      meta: "10月30日 23:59 截止 · 还剩 3 天",
      tone: "is-warning",
      action: "去提交",
      go: "assignments",
    },
    {
      iconName: "shield-check",
      title: "Oracle 课程设计",
      meta: "格式校验发现 4 个阻断问题",
      tone: "is-warning",
      action: "去修复",
      go: "format",
    },
    {
      iconName: "message-square",
      title: "孙老师添加了 2 条批注",
      meta: "JavaEE课程作业_小组版",
      tone: "is-violet",
      action: "查看",
      go: "messages",
    },
  ];
  return `${pageHead({
    breadcrumb: "我的工作 / 工作台",
    title: "学习工作台",
    lead: "下午好，张同学。先处理临近截止的课程报告，再继续完成小组协作和格式检查。",
    actions: `${button("上传文档", { iconName: "upload", action: "upload" })}${button("查看作业", { variant: "primary", iconName: "book-open-check", action: "assignments" })}`,
  })}
  <section class="stats" aria-label="学习概览">
    <div class="stat" style="--stat-accent:#2563eb"><div class="stat-label">本周文档</div><div class="stat-value">12</div><div class="stat-note">新增 3 份</div></div>
    <div class="stat" style="--stat-accent:#b45309"><div class="stat-label">待办任务</div><div class="stat-value">4</div><div class="stat-note">作业 1 / 校验 2 / 批注 1</div></div>
    <div class="stat" style="--stat-accent:#15803d"><div class="stat-label">格式通过</div><div class="stat-value">8</div><div class="stat-note">可直接提交</div></div>
    <div class="stat" style="--stat-accent:#0e7490"><div class="stat-label">存储占用</div><div class="stat-value">126MB</div><div class="stat-note">上限 2GB</div></div>
  </section>
  <div class="dashboard-grid">
    <div class="stack">
      <section class="surface">
        ${surfaceHeader("继续处理", "最近更新", "history", button("全部文档", { variant: "link", action: "documents" }))}
        <div class="list">
          ${continueRows
            .map(
              (row) => `<div class="list-row">
                ${rowIcon(row.iconName)}
                <div class="row-copy"><strong>${esc(row.title)}</strong><span>${esc(row.meta)}</span></div>
                <div class="row-aside">${button(row.action, { small: true, action: row.go })}</div>
              </div>`,
            )
            .join("")}
        </div>
      </section>
      <section class="surface">
        ${surfaceHeader("快捷任务", "只显示学生可用操作", "sparkles")}
        <div class="surface-body">
          <div class="quick-grid">
            <button type="button" class="quick-action" data-action="upload">${icon("upload-cloud")}<strong>上传新文档</strong><span>支持 Word / PDF，完成后进入智能处理</span></button>
            <button type="button" class="quick-action" data-action="templates">${icon("file-plus-2")}<strong>按模板新建</strong><span>选择教师已发布的课程模板</span></button>
            <button type="button" class="quick-action" data-action="assignments">${icon("clipboard-check")}<strong>查看课程作业</strong><span>检查截止时间和提交状态</span></button>
            <button type="button" class="quick-action" data-action="open-messages">${icon("message-square")}<strong>查看教学消息</strong><span>处理截止提醒与教师批注</span></button>
          </div>
        </div>
      </section>
    </div>
    <section class="surface">
      ${surfaceHeader("待办与提醒", "3 条未读", "bell")}
      <div class="list">
        ${todoRows
          .map(
            (row) => `<div class="list-row">
              ${rowIcon(row.iconName, row.tone)}
              <div class="row-copy"><strong>${esc(row.title)}</strong><span>${esc(row.meta)}</span></div>
              <div class="row-aside">${button(row.action, { small: true, action: row.go, variant: "ghost" })}</div>
            </div>`,
          )
          .join("")}
      </div>
      <div class="surface-body">
        <div class="notice">${icon("info")}格式校验通过后才可提交课程作业；警告项可在教师确认后保留。</div>
      </div>
    </section>
  </div>`;
}

function screenDocuments() {
  return `${pageHead({
    breadcrumb: "我的工作 / 我的文档",
    title: "我的文档",
    lead: "管理本人文档与小组协作文档，统一查看处理状态和最近更新。",
    actions: `${button("返回工作台", { iconName: "arrow-left", action: "workbench" })}`,
  })}
  ${documentsTable()}`;
}

function uploadModal() {
  return `<div class="modal-scrim">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="upload-title">
      <div class="modal-header">
        <div><h2 class="modal-title" id="upload-title">上传文档</h2><p class="modal-subtitle">解析完成后可继续摘要、问答、润色和格式校验。</p></div>
        ${iconButton("x", { action: "close-upload", label: "关闭上传弹窗" })}
      </div>
      <div class="modal-body">
        <button type="button" class="dropzone" data-action="select-file" aria-label="选择示例文档">
          ${icon("upload-cloud")}
          <strong>拖放文件到这里，或从本地选择</strong>
          <span>支持 DOCX、DOC、PDF，单个文件不超过 100MB</span>
        </button>
        ${
          state.fileSelected
            ? `<div class="selected-file">
                ${rowIcon("file-text")}
                <div class="row-copy"><strong>${DOC_TITLE}</strong><span>2.8MB · 已通过文件类型检查</span></div>
                ${badge("待上传", "info")}
                ${iconButton("x", { action: "remove-file", label: "移除已选文件" })}
              </div>`
            : `<div class="notice warning" style="margin-top:12px">${icon("info")}尚未选择文件，请拖放或选择一份 Word/PDF 文档。</div>`
        }
        <div class="form-grid">
          <div class="form-field"><label for="upload-course">所属课程</label><select class="select" id="upload-course"><option>${COURSE}</option><option>Oracle 数据库应用</option><option>JavaEE 程序设计</option></select></div>
          <div class="form-field"><label for="upload-type">文档类型</label><select class="select" id="upload-type"><option>课程设计报告</option><option>实验报告</option><option>小组作业</option></select></div>
          <div class="form-field is-full"><label for="upload-name">文档名称</label><input class="field" id="upload-name" value="${DOC_TITLE.replace(".docx", "")}"></div>
        </div>
        <div class="form-hint" style="margin-top:10px">上传后将先做安全检查与文本解析；源文件不会被自动覆盖。</div>
      </div>
      <div class="modal-footer">${button("取消", { action: "close-upload" })}${button("开始上传", { variant: "primary", iconName: "upload", action: "processing", disabled: !state.fileSelected })}</div>
    </section>
  </div>`;
}

function processingModal() {
  const steps = [
    ["安全检查", "文件类型、大小与权限检查通过", "done"],
    ["文本解析", "已识别 24 页正文与 7 个标题", "done"],
    ["版面识别", "正在识别页眉、表格、代码与图表", "active"],
    ["内容索引", "等待版面识别完成", "pending"],
    ["生成预览", "等待内容索引完成", "pending"],
  ];
  return `<div class="modal-scrim">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="processing-title">
      <div class="modal-header">
        <div><h2 class="modal-title" id="processing-title">文档处理中</h2><p class="modal-subtitle">${DOC_TITLE}</p></div>
        ${iconButton("x", { action: "close-processing", label: "关闭处理进度" })}
      </div>
      <div class="modal-body">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:7px"><strong style="font-size:12px">整体进度</strong><span class="surface-meta">63%</span></div>
        <div class="progress"><span style="width:63%"></span></div>
        <div class="timeline">
          ${steps
            .map(
              ([title, meta, status]) => `<div class="timeline-row ${status}">
                <span class="timeline-dot">${status === "done" ? icon("check") : status === "active" ? icon("loader-circle") : ""}</span>
                <div class="timeline-copy"><strong>${title}</strong><span>${meta}</span></div>
              </div>`,
            )
            .join("")}
        </div>
        <div class="notice">${icon("info")}可以关闭此窗口，任务将在后台继续；完成后会发送站内通知。</div>
      </div>
      <div class="modal-footer">${button("后台继续", { action: "close-processing", iconName: "pause" })}${button("查看文档", { variant: "primary", iconName: "eye", action: "preview" })}</div>
    </section>
  </div>`;
}

function paperPreview() {
  return `<article class="paper-page">
    <h2 class="paper-title">基于 Web 的校园智能文档辅助系统设计</h2>
    <div class="paper-subtitle">Web开发技术 · 课程设计报告 · 张同学</div>
    <h4>1 项目背景</h4>
    <p>学生在课程报告撰写过程中需要反复处理格式、图表编号和参考文献。系统将文档接入、智能处理、格式校验和版本协作整合在同一工作流中，减少重复调整。</p>
    <h4>2 需求分析</h4>
    <p>文档需支持 Word 与 PDF 上传，并在解析完成后提供摘要、要点提取、文档问答和文本润色。所有处理结果都应能够定位到原文，越权访问需要被拒绝并记录。</p>
    <h4>3 系统设计</h4>
    <p>系统采用前后端分离结构。前端负责工作台、文档预览与交互状态；服务层承担权限验证、任务调度和格式处理；对象存储保存原始文件与生成结果。</p>
    <h4>4 关键流程</h4>
    <p>格式校验会根据课程模板检查字体、字号、行距、页边距、题注与参考文献。阻断问题修复后自动重新校验，并生成新的历史版本。</p>
  </article>`;
}

function aiTasks() {
  return [
    ["summary", "文本摘要", "提炼研究目标与关键结论", "sparkles"],
    ["keypoints", "要点提取", "按原文顺序生成要点清单", "list-checks"],
    ["qa", "文档问答", "围绕当前文档追问", "message-square-text"],
    ["polish", "文本润色", "对照改写选中的段落", "highlighter"],
    ["normalize", "代码与图表", "统一代码块、题注和编号", "code-2"],
    ["format", "格式规范校验", "按课程模板检查提交格式", "shield-check"],
  ]
    .map(
      ([id, title, meta, iconName]) => `<button type="button" class="ai-task" data-action="${id}">
        ${rowIcon(iconName)}<div><strong>${title}</strong><span>${meta}</span></div>${icon("chevron-right")}
      </button>`,
    )
    .join("");
}

function screenPreview() {
  const outline = ["项目背景", "需求分析", "系统设计", "关键流程", "测试与验收"];
  return `${pageHead({
    breadcrumb: "文档接入 / 我的文档 / 文档预览",
    title: "文档预览",
    lead: `${COURSE} · 24 页 · 最近保存于今天 21:10`,
    actions: `${button("返回文档", { iconName: "arrow-left", action: "documents" })}${button("导出文档", { iconName: "download", action: "export" })}${button("开始格式校验", { variant: "primary", iconName: "shield-check", action: "format" })}`,
  })}
  <div class="preview-layout">
    <aside class="preview-panel">
      ${surfaceHeader("文档目录", "5 个章节", "list-tree")}
      <div class="outline-list">${outline.map((item, index) => `<button type="button" class="outline-item ${index === state.previewSection ? "is-active" : ""}" data-action="select-preview-section" data-index="${index}">${icon(index === state.previewSection ? "book-open" : "bookmark")}<span>${index + 1} ${item}</span></button>`).join("")}</div>
    </aside>
    <section class="paper">${paperPreview()}</section>
    <aside class="preview-panel">
      ${surfaceHeader("智能处理", "当前文档", "sparkles")}
      <div class="ai-task-list">${aiTasks()}</div>
    </aside>
  </div>`;
}

function sourceContext(meta = "已解析 24 页 · 可定位原文") {
  return `<div class="notice" style="margin-bottom:14px">${icon("file-text")}<span><strong>${DOC_TITLE}</strong> · ${COURSE} · ${meta}</span></div>`;
}

function screenSummary() {
  const lengthLabel = { short: "精简", standard: "标准", detailed: "详细" }[state.summaryLength];
  const focusLabel = {
    structure: "结构与结论",
    method: "方法与实践",
    acceptance: "验收与风险",
  }[state.summaryFocus];
  return `${pageHead({
    breadcrumb: "智能处理 / 文本摘要",
    title: "文本摘要",
    lead: "设置摘要长度和关注重点，生成后可以定位原文、复制或导出。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("导出摘要", { variant: "primary", iconName: "download", action: "export" })}`,
  })}
  ${sourceContext()}
  <div class="split-layout is-uneven">
    <section class="surface">
      ${surfaceHeader("输入与设置", "全文 24 页", "sliders-horizontal")}
      <div class="surface-body">
        <div class="form-field">
          <label for="summary-prompt">摘要要求</label>
          <input class="field" id="summary-prompt" value="${esc(state.summaryPrompt)}" placeholder="例如：突出系统设计与验收结论" data-summary-prompt>
        </div>
        <div class="form-field" style="margin-top:14px">
          <label>摘要长度</label>
          <div class="segmented" style="align-self:flex-start">
            <button type="button" class="${state.summaryLength === "short" ? "is-active" : ""}" data-action="set-summary-length" data-length="short">精简</button>
            <button type="button" class="${state.summaryLength === "standard" ? "is-active" : ""}" data-action="set-summary-length" data-length="standard">标准</button>
            <button type="button" class="${state.summaryLength === "detailed" ? "is-active" : ""}" data-action="set-summary-length" data-length="detailed">详细</button>
          </div>
        </div>
        <div class="form-field" style="margin-top:14px">
          <label for="summary-focus">关注重点</label>
          <select class="select" id="summary-focus" data-summary-focus>
            <option value="structure" ${state.summaryFocus === "structure" ? "selected" : ""}>结构与结论</option>
            <option value="method" ${state.summaryFocus === "method" ? "selected" : ""}>方法与实践</option>
            <option value="acceptance" ${state.summaryFocus === "acceptance" ? "selected" : ""}>验收与风险</option>
          </select>
        </div>
        <div style="margin-top:14px">${button("生成摘要", { variant: "primary", iconName: "sparkles", action: "regenerate" })}</div>
        <div style="height:1px;background:var(--border);margin:16px 0"></div>
        <span class="surface-meta">当前范围：第 1–3 节 · 自动保留引用</span>
        <div class="text-panel" style="border:0;padding:0">
          <p style="margin-top:10px"><strong>原文节选</strong><br>系统采用前后端分离、异步任务、对象存储与统一权限模型，将文档接入、智能处理和格式校验串联起来。</p>
        </div>
      </div>
    </section>
    <section class="surface">
      ${surfaceHeader("生成结果", "", "sparkles", `<div class="row-aside"><span class="surface-meta">${lengthLabel} · ${focusLabel}</span>${badge("可引用", "success")}</div>`)}
      <div class="result-section">
        <h3>${icon("target")}一句话摘要</h3>
        <p>系统通过统一文档接入、智能处理、格式校验和版本协作，帮助学生更高效地完成课程报告并降低提交前的格式风险。</p>
      </div>
      <div class="result-section">
        <h3>${icon("list-checks")}关键结论</h3>
        <ul style="margin:0;padding-left:18px">
          <li>摘要与问答结果必须可以定位到原文。</li>
          <li>格式阻断问题清零后才允许提交作业。</li>
          <li>协作快照和历史版本需支持对比与恢复。</li>
        </ul>
      </div>
      <div class="result-section" style="display:flex;justify-content:space-between;align-items:center">
        <div><span class="citation">${icon("link-2")}引用 3 处原文</span><span class="surface-meta" style="margin-left:8px">生成耗时 8 秒</span></div>
        <div class="row-aside">${button("定位原文", { small: true, iconName: "book-open", action: "preview", variant: "ghost" })}${button("复制", { small: true, iconName: "copy", action: "copy", variant: "ghost", extra: `data-copy-text="${esc("系统通过统一文档接入、智能处理、格式校验和版本协作，帮助学生更高效地完成课程报告并降低提交前的格式风险。")}"` })}</div>
      </div>
    </section>
  </div>`;
}

function screenKeypoints() {
  const points = [
    ["统一文档接入", "支持 Word 与 PDF 上传，解析失败可重试。", "第 2 页"],
    ["智能处理闭环", "摘要、要点、问答与润色均需定位原文。", "第 5 页"],
    ["前置格式校验", "阻断问题清零后才可提交课程作业。", "第 12 页"],
    ["协作与版本", "保存快照、对比差异并恢复为新版本。", "第 18 页"],
    ["权限与审计", "越权访问拒绝并记录，下载仅本人可见。", "第 22 页"],
  ];
  const selectedCount = state.selectedPoints.size;
  return `${pageHead({
    breadcrumb: "智能处理 / 要点提取",
    title: "要点提取",
    lead: "按原文顺序整理关键内容，可选择需要的要点导出为清单。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("复制已选", { iconName: "copy", action: "copy", disabled: selectedCount === 0, extra: `data-copy-text="${esc(points.filter((_, index) => state.selectedPoints.has(index)).map(([title, desc]) => `${title}：${desc}`).join("\n"))}"` })}${button("导出清单", { variant: "primary", iconName: "download", action: "export", disabled: selectedCount === 0 })}`,
  })}
  ${sourceContext(`已选 ${selectedCount} 条 · 来源页码保留`)}
  <div class="split-layout is-uneven">
    <section class="surface">
      ${surfaceHeader("提取结果", "按原文顺序", "list-checks", `${badge(`${selectedCount} 条`, "info")}`)}
      <div class="surface-body">
        <div class="review-list">
          ${points
            .map(
              ([title, desc, page], index) => `<button type="button" class="review-item ${state.selectedPoints.has(index) ? "is-selected" : ""}" data-action="toggle-point" data-index="${index}">
                <span class="check">${state.selectedPoints.has(index) ? icon("check") : ""}</span>
                <span class="review-copy"><strong>${title}</strong><span>${desc}</span></span>
                <span class="citation">${page}</span>
              </button>`,
            )
            .join("")}
        </div>
      </div>
    </section>
    <section class="surface">
      ${surfaceHeader("导出设置", "PPT / Markdown / Word", "settings-2")}
      <div class="surface-body">
        <div class="form-field"><label for="point-format">导出格式</label><select class="select" id="point-format"><option>Markdown 清单</option><option>Word 文档</option><option>PPT 大纲</option></select></div>
        <div class="form-field" style="margin-top:12px"><label for="point-style">内容顺序</label><select class="select" id="point-style"><option>按原文顺序</option><option>按重要性排序</option></select></div>
        <div class="setting-row" style="padding:0;margin-top:16px;border-top:1px solid var(--border)"><div class="setting-copy"><strong>保留来源页码</strong><span>导出内容附带引用位置</span></div><button type="button" class="switch is-on" aria-label="保留来源页码" data-action="toggle-switch" data-switch="source-pages"></button></div>
        <div class="notice" style="margin-top:14px">${icon("info")}导出只包含已选要点，不会修改原文档。</div>
      </div>
    </section>
  </div>`;
}

function qaChat(refusal = false) {
  if (state.qaCleared) {
    return `<div class="chat-thread"><div class="empty-state">${icon("message-square")}<strong>当前对话已清空</strong><span>可输入新问题继续问答。</span></div></div>`;
  }
  if (refusal) {
    return `<div class="chat-thread">
      <div class="message user">报告中是否给出与其他学校产品的横向对比数据？</div>
      <div class="message assistant message-refusal"><strong>${icon("circle-slash-2")}当前文档无法回答这个问题</strong>文档只介绍了本系统的需求、设计与验收要求，没有提供其他学校产品的横向对比数据。</div>
      <div class="message assistant"><strong>可以换一种问法</strong>例如：“报告列出了哪些验收指标？”或“系统与现有手工流程相比有哪些改进？”</div>
    </div>`;
  }
  return `<div class="chat-thread">
    <div class="message user">格式校验失败后，如何继续提交课程作业？</div>
    <div class="message assistant"><strong>先修复阻断问题，再重新提交。</strong>自动修复会生成新版本并重新执行完整校验；警告项可以保留，但需要在提交确认页查看提示。<span class="citation" style="margin-left:6px">${icon("link-2")}第 12 页</span></div>
    <div class="message user">恢复历史版本会覆盖当前版本吗？</div>
    <div class="message assistant"><strong>不会覆盖。</strong>恢复操作会把所选历史版本复制为新的当前版本，原有版本仍会保留在版本记录中。<span class="citation" style="margin-left:6px">${icon("link-2")}第 18 页</span></div>
    <div class="message user">报告里有没有横向对比数据？</div>
    <div class="message assistant"><strong>没有找到可引用的内容。</strong>继续追问可能会得到无依据回答，建议改用文档中明确出现的验收指标。</div>
  </div>`;
}

function qaSidebar(refusal = false) {
  return `<section class="surface">
    ${surfaceHeader("引用与建议", "基于当前文档", "link-2")}
    <div class="surface-body">
      <div class="setting-copy"><strong>原文引用</strong><span>第 12 页 · 格式规范校验</span></div>
      <div class="text-panel" style="margin-top:10px;background:var(--surface-muted)"><p>阻断问题必须修复后才能提交；警告项可以由教师在批改时确认。</p></div>
      <div class="setting-copy" style="margin-top:18px"><strong>继续追问</strong><span>${refusal ? "换一种有原文依据的问法" : "推荐问题"}</span></div>
      <div class="suggestions" style="padding:10px 0 0">
        <button type="button" class="suggestion" data-action="${refusal ? "qa" : "refusal"}">${refusal ? "报告列出了哪些验收指标？" : "报告里有没有横向对比数据？"}</button>
        <button type="button" class="suggestion" data-action="qa">格式校验包含哪些检查项？</button>
        <button type="button" class="suggestion" data-action="qa">协作版本支持哪些恢复方式？</button>
      </div>
    </div>
  </section>`;
}

function screenQA() {
  return `${pageHead({
    breadcrumb: "智能处理 / 文档问答",
    title: "文档问答",
    lead: "围绕当前文档连续追问，每个回答都显示可回跳的原文依据。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("清除对话", { iconName: "rotate-ccw", action: "clear-chat" })}${button("导出对话", { variant: "primary", iconName: "download", action: "export" })}`,
  })}
  ${sourceContext("已解析 24 页 · 对话仅使用当前文档")}
  <div class="chat-layout">
    <section class="surface chat-panel">
      <div class="chat-head"><strong>当前对话</strong><span class="surface-meta">3 轮 · 最近生成 21:16</span></div>
      ${qaChat(false)}
      <div class="chat-compose"><label class="search-field" style="flex:1;width:auto">${icon("message-square")}<input value="${esc(state.qaInput)}" placeholder="继续追问当前文档" aria-label="输入问题" data-qa-input></label>${button("发送", { variant: "primary", iconName: "send", action: "ask-question" })}</div>
    </section>
    ${qaSidebar(false)}
  </div>`;
}

function screenRefusal() {
  return `${pageHead({
    breadcrumb: "智能处理 / 文档问答 / 无依据拒答",
    title: "文档问答",
    lead: "系统只回答有原文依据的问题；找不到依据时会明确拒答并给出替代问法。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("清除对话", { iconName: "rotate-ccw", action: "clear-chat" })}${button("导出对话", { variant: "primary", iconName: "download", action: "export" })}`,
  })}
  ${sourceContext("未找到可引用的横向对比数据")}
  <div class="chat-layout">
    <section class="surface chat-panel">
      <div class="chat-head"><strong>当前对话</strong>${badge("无依据拒答", "warning")}</div>
      ${qaChat(true)}
      <div class="chat-compose"><label class="search-field" style="flex:1;width:auto">${icon("message-square")}<input value="${esc(state.qaInput || "报告列出了哪些验收指标？")}" aria-label="替代问题" data-qa-input></label>${button("重新提问", { variant: "primary", iconName: "send", action: "ask-question" })}</div>
    </section>
    ${qaSidebar(true)}
  </div>`;
}

function screenPolish() {
  return `${pageHead({
    breadcrumb: "智能处理 / 文本润色",
    title: "文本润色",
    lead: "逐句比较原文与改写结果，可整段采纳，也可只替换选中的句子。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("重新润色", { iconName: "refresh-cw", action: "regenerate" })}${button("整体采纳", { variant: "primary", iconName: "check", action: "versions" })}`,
  })}
  ${sourceContext("第 3 节 · 已选择 1 段 · 约 126 字")}
  <div class="split-layout">
    <section class="surface">
      ${surfaceHeader("改写建议", "2 处调整", "highlighter", badge("可撤销", "info"))}
      <div class="surface-body">
        <div class="polish-grid">
          <div class="text-panel"><h4>原文</h4><p>系统需要在文档接入环节完成格式识别与保留。由于格式调整占用了较多时间，系统通过模板与自动校验减少重复的格式调整工作。</p></div>
          <div class="text-panel" style="border-color:#bbf7d0;background:#f8fff9"><h4>润色后</h4><p>系统在文档接入阶段同步识别并保留格式。针对格式调整耗时的问题，系统通过课程模板与自动校验减少重复操作，使学生能够将更多时间用于内容撰写。</p></div>
        </div>
        <div class="review-list" style="margin-top:14px">
          <label class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>表达更清晰</strong><span>明确“文档接入阶段”与“课程模板”的关系。</span></span>${button("定位", { small: true, variant: "link", action: "preview" })}</label>
          <label class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>保持原意</strong><span>没有新增系统未支持的自动排版能力。</span></span>${badge("已校验", "success")}</label>
        </div>
      </div>
    </section>
    <section class="surface">
      ${surfaceHeader("应用方式", "不覆盖历史版本", "settings-2")}
      <div class="surface-body">
        <div class="selection-grid">
          <button type="button" class="selection-card is-selected" data-action="select-choice" aria-pressed="true">${icon("check")}<strong>${icon("whole-word")}整体采纳</strong><p>使用润色后的完整段落替换原文。</p></button>
          <button type="button" class="selection-card" data-action="select-choice" aria-pressed="false">${icon("list-checks")}<strong>逐句采纳</strong><p>只替换你勾选的句子，其余保持原样。</p></button>
        </div>
        <div class="setting-row" style="padding:0;margin-top:18px"><div class="setting-copy"><strong>应用前显示差异</strong><span>确认后仍可从版本记录撤销。</span></div><button type="button" class="switch is-on" aria-label="应用前显示差异" data-action="toggle-switch" data-switch="show-diff"></button></div>
        <div class="notice" style="margin-top:14px">${icon("info")}应用后自动生成新版本 v4，原文版本保持不变。</div>
      </div>
    </section>
  </div>`;
}

function screenTemplates() {
  const templates = [
    [COURSE, "v2.1", "计算机学院课程设计报告模板，包含标准封面、章节结构与参考文献格式。", "已发布"],
    ["Oracle 数据库应用", "v1.8", "实验报告模板，预置实验目的、步骤、结果与问题分析章节。", "已发布"],
    ["JavaEE 程序设计", "v2.4", "小组作业模板，包含成员分工、系统设计和测试记录。", "已发布"],
    ["数据库原理", "v1.3", "课程论文模板，提供中英文摘要与参考文献示例。", "已发布"],
    ["毕业设计", "v1.6", "论文初稿模板，提供章节层级与图表编号规则。", "已发布"],
    ["Web开发技术", "v2.0", "实验指导书模板，包含代码块、运行结果和截图题注格式。", "已发布"],
  ];
  return `${pageHead({
    breadcrumb: "格式与模板 / 课程模板",
    title: "课程模板",
    lead: "查看教师已发布的课程要求，或按模板新建文档。学生端不提供编辑、发布和版本维护操作。",
    actions: `${button("查看格式规范", { iconName: "book-open", action: "format" })}${button("按模板新建", { variant: "primary", iconName: "file-plus-2", action: "upload" })}`,
  })}
  <div class="surface">
    <div class="toolbar">
      <label class="search-field">${icon("search")}<input placeholder="搜索课程模板"></label>
      <select class="select" aria-label="学院筛选"><option>全部学院</option><option>计算机学院</option></select>
      <select class="select" aria-label="课程筛选"><option>全部课程</option><option>${COURSE}</option><option>Oracle 数据库应用</option></select>
      <div class="toolbar-spacer"></div>
      <span class="surface-meta">6 个模板 · 均为只读</span>
    </div>
    <div class="surface-body">
      <div class="template-grid">
        ${templates
          .map(
            ([course, version, description, status], index) => `<article class="template-card">
              ${rowIcon(index === 0 ? "book-open-check" : "layout-template")}
              <div style="display:flex;align-items:center;justify-content:space-between;gap:8px"><h3>${course}</h3>${badge(status, "success")}</div>
              <p>${description}</p>
              <div class="surface-meta" style="margin-bottom:10px">${version} · 由课程教师发布</div>
              <div class="template-actions">${button("查看要求", { small: true, variant: "ghost", action: "open-template", extra: `data-index="${index}"` })}${button("按模板新建", { small: true, action: "upload" })}</div>
            </article>`,
          )
          .join("")}
      </div>
    </div>
  </div>${state.templateModal !== null ? templateRequirementsModal(templates[state.templateModal]) : ""}`;
}

function templateRequirementsModal(template) {
  const [course, version, description] = template;
  return `<div class="modal-scrim">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="template-requirements-title">
      <div class="modal-header">
        <div><h2 class="modal-title" id="template-requirements-title">${course} · ${version}</h2><p class="modal-subtitle">教师发布的课程模板要求，学生端为只读。</p></div>
        ${iconButton("x", { action: "close-template", label: "关闭模板要求" })}
      </div>
      <div class="modal-body">
        <p style="margin:0;color:var(--text-secondary);font-size:12px;line-height:19px">${description}</p>
        <div class="review-list" style="margin-top:14px">
          <div class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>页面设置</strong><span>A4 · 上下 2.5cm · 左 3cm · 右 2.5cm</span></span></div>
          <div class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>正文格式</strong><span>宋体小四 · 固定行距 22 磅 · 首行缩进 2 字符</span></span></div>
          <div class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>必备结构</strong><span>封面、摘要、目录、正文、参考文献</span></span></div>
          <div class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>图表与引用</strong><span>图表连续编号，题注位置遵循课程要求</span></span></div>
        </div>
        <div class="notice" style="margin-top:14px">${icon("info")}学生可以查看要求并按模板新建文档，不能编辑或发布模板。</div>
      </div>
      <div class="modal-footer">${button("关闭", { action: "close-template" })}${button("按此模板新建", { variant: "primary", iconName: "file-plus-2", action: "upload" })}</div>
    </section>
  </div>`;
}

function screenNormalize() {
  return `${pageHead({
    breadcrumb: "格式与模板 / 代码与图表规范化",
    title: "代码与图表规范化",
    lead: "统一代码语言、缩进、图表题注和编号，并同步更新正文中的引用。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("保存新版本", { variant: "primary", iconName: "save", action: "format" })}`,
  })}
  ${sourceContext("识别到 3 个代码块、2 个图表、4 处引用")}
  <div class="split-layout">
    <section class="surface">
      ${surfaceHeader("代码块检查", "3 项", "code-2", badge("1 项待处理", "warning"))}
      <div class="surface-body">
        <div class="form-grid" style="margin-top:0">
          <div class="form-field"><label for="code-language">代码块 1 语言</label><select class="select" id="code-language"><option>Java</option><option>JavaScript</option><option>Python</option></select></div>
          <div class="form-field"><label>缩进与换行</label><input class="field" value="4 空格 · 自动换行" readonly></div>
        </div>
        <div class="code-panel" style="margin-top:12px"><span class="kw">public class</span> DocumentService {<br>&nbsp;&nbsp;<span class="kw">public</span> Summary <span class="str">generate</span>(Document doc) {<br>&nbsp;&nbsp;&nbsp;&nbsp;<span class="kw">return</span> aiService.<span class="str">summarize</span>(doc, <span class="num">1200</span>);<br>&nbsp;&nbsp;}<br>}</div>
        <div class="notice warning" style="margin-top:12px">${icon("alert-triangle")}代码块 1 未标注语言，已建议为 Java；确认后将同步代码块样式。</div>
      </div>
    </section>
    <div class="stack">
      <section class="surface">
        ${surfaceHeader("图表与题注", "2 个图表", "image")}
        <div class="surface-body">
          <div class="figure-box">${icon("chart-no-axes-combined")}<span class="sr-only">系统架构图预览</span></div>
          <div class="figure-caption">图 3-1 系统总体架构</div>
          <div class="review-list" style="margin-top:12px">
            <label class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>统一题注位置</strong><span>图题置于图下方，表题置于表上方。</span></span></label>
            <label class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>更新正文引用</strong><span>2 处“见图”引用将同步编号。</span></span></label>
          </div>
        </div>
      </section>
      <section class="surface">
        ${surfaceHeader("保存影响", "生成 v6", "history")}
        <div class="surface-body"><div class="notice">${icon("info")}保存后创建新版本 v6，原版本仍可在版本记录中恢复。</div></div>
      </section>
    </div>
  </div>`;
}

function formatIssues() {
  return `<div class="issue-summary">
    <div class="issue-tile"><strong style="color:var(--danger)">4</strong><span>阻断问题</span></div>
    <div class="issue-tile"><strong style="color:var(--warning)">3</strong><span>格式警告</span></div>
    <div class="issue-tile"><strong style="color:var(--success)">11</strong><span>检查通过</span></div>
  </div>
  <div class="format-list">
    <div class="format-item"><span class="format-icon">${icon("alert-triangle")}</span><div class="format-copy"><strong>正文行距不符合模板</strong><span>第 4–21 页 · 当前 1.25 倍，模板要求固定值 22 磅</span></div>${badge("阻断", "danger")}</div>
    <div class="format-item"><span class="format-icon">${icon("alert-triangle")}</span><div class="format-copy"><strong>二级标题字体不一致</strong><span>第 2、3、5 章 · 当前 16px，模板要求黑体 16pt</span></div>${badge("阻断", "danger")}</div>
    <div class="format-item"><span class="format-icon">${icon("alert-triangle")}</span><div class="format-copy"><strong>图表题注格式错误</strong><span>图 3-1、图 4-2 · 图题需居中并置于图下方</span></div>${badge("阻断", "danger")}</div>
    <div class="format-item"><span class="format-icon">${icon("alert-triangle")}</span><div class="format-copy"><strong>参考文献缺少访问日期</strong><span>3 条网页引用 · 建议补全引用信息</span></div>${badge("阻断", "danger")}</div>
    <div class="format-item"><span class="format-icon warning">${icon("info")}</span><div class="format-copy"><strong>部分英文缩写未首次释义</strong><span>API、SSO · 可以保留，提交时显示提醒</span></div>${badge("警告", "warning")}</div>
  </div>`;
}

function screenFormat() {
  return `${pageHead({
    breadcrumb: "格式与模板 / 格式规范校验",
    title: "格式规范校验",
    lead: `${COURSE} · 教师模板 v2.1 · 检查字体、字号、行距、页边距、题注与参考文献`,
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("重新校验", { iconName: "refresh-cw", action: "regenerate" })}${button("修复 4 个问题", { variant: "primary", iconName: "wand-sparkles", action: "autofix" })}`,
  })}
  <div class="split-layout is-uneven">
    <section class="surface">
      ${surfaceHeader("检查结果", "校验于 21:20", "shield-check", badge("未通过", "danger"))}
      <div class="surface-body">${formatIssues()}</div>
    </section>
    <div class="stack">
      <section class="surface">
        ${surfaceHeader("课程规则", "模板 v2.1", "layout-template")}
        <div class="surface-body">
          <div class="review-list">
            <div class="review-item"><span class="check" style="border-color:var(--success);background:var(--success-bg);color:var(--success)">${icon("check")}</span><span class="review-copy"><strong>页面设置</strong><span>A4 · 上 2.5cm · 下 2.5cm · 左 3cm</span></span></div>
            <div class="review-item"><span class="check" style="border-color:var(--success);background:var(--success-bg);color:var(--success)">${icon("check")}</span><span class="review-copy"><strong>正文样式</strong><span>宋体小四 · 固定行距 22 磅</span></span></div>
            <div class="review-item"><span class="check" style="border-color:var(--success);background:var(--success-bg);color:var(--success)">${icon("check")}</span><span class="review-copy"><strong>章节结构</strong><span>封面、摘要、目录、正文、参考文献</span></span></div>
          </div>
        </div>
      </section>
      <section class="surface">
        ${surfaceHeader("提交影响", "需要修复", "info")}
        <div class="surface-body"><div class="notice danger">${icon("circle-slash-2")}4 个阻断问题清零前，不能提交课程作业。</div></div>
      </section>
    </div>
  </div>`;
}

function autoFixModal() {
  const fixes = [
    ["正文行距", "将 1.25 倍行距调整为固定值 22 磅。"],
    ["标题字体", "统一二级标题为黑体 16pt。"],
    ["图表题注", "居中并移动至图片下方，更新正文引用。"],
    ["参考文献", "为 3 条网页引用补全访问日期。"],
  ];
  const selectedCount = state.selectedFixes.size;
  return `<div class="modal-scrim">
    <section class="modal is-wide" role="dialog" aria-modal="true" aria-labelledby="autofix-title">
      <div class="modal-header">
        <div><h2 class="modal-title" id="autofix-title">一键修复 4 个问题</h2><p class="modal-subtitle">修复后将自动重新校验并生成新版本 v6。</p></div>
        ${iconButton("x", { action: "close-autofix", label: "关闭修复弹窗" })}
      </div>
      <div class="modal-body">
        <div class="review-list">
          ${fixes.map(([title, description], index) => `<button type="button" class="review-item ${state.selectedFixes.has(index) ? "is-selected" : ""}" data-action="toggle-fix" data-index="${index}"><span class="check">${state.selectedFixes.has(index) ? icon("check") : ""}</span><span class="review-copy"><strong>${title}</strong><span>${description}</span></span>${badge("自动", "info")}</button>`).join("")}
        </div>
        <div class="setting-row" style="padding:0;margin-top:16px"><div class="setting-copy"><strong>保留原版本</strong><span>修复不覆盖 v5，可随时恢复。</span></div><span class="badge success">始终开启</span></div>
      </div>
      <div class="modal-footer">${button("取消", { action: "close-autofix" })}${button(`开始修复 (${selectedCount})`, { variant: "primary", iconName: "wand-sparkles", action: "convert", disabled: selectedCount === 0 })}</div>
    </section>
  </div>`;
}

function screenConvert() {
  const options = [
    ["docx-pdf", "DOCX → PDF", "保留版式、书签与目录，适合提交归档。", "file-text"],
    ["pdf-docx", "PDF → DOCX", "将可复制文本恢复为可编辑 Word 文档。", "file-type-2"],
    ["docx-word", "DOCX → Word 兼容版", "清理旧版兼容字段，继续编辑时使用。", "file-pen-line"],
    ["docx-markdown", "DOCX → Markdown", "保留标题层级、列表与代码块，适合知识整理。", "file-code-2"],
    ["docx-html", "DOCX → HTML", "生成带目录和样式的网页版本。", "globe-2"],
    ["docx-zip", "DOCX → 图片包", "按页导出 PNG 并打包，便于分享和预览。", "images"],
  ];
  const selectedOption = options.find(([key]) => key === state.conversionMode) || options[0];
  return `${pageHead({
    breadcrumb: "格式与模板 / 格式转换",
    title: "格式转换",
    lead: "选择转换方式并预览输出页面，转换结果会保留在下载中心。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("下载中心", { iconName: "download", action: "downloads" })}`,
  })}
  ${sourceContext(state.conversionStarted ? "新版本 v6 · 格式修复已完成" : "v6 · 格式修复完成 · 可转换")}
  <div class="conversion-layout ${state.conversionDrawerCollapsed ? "is-collapsed" : ""}">
    <aside class="surface conversion-drawer">
      ${
        state.conversionDrawerCollapsed
          ? `<div class="drawer-collapsed">${iconButton("panel-left-open", { action: "toggle-convert-drawer", label: "展开转换方式" })}<span>转换方式</span></div>`
          : `${surfaceHeader("转换方式", `${options.length} 种`, "file-output", iconButton("panel-left-close", { action: "toggle-convert-drawer", label: "收起转换方式" }))}
             <div class="conversion-options">
               ${options.map(([key, title, description, iconName]) => `<button type="button" class="conversion-option ${state.conversionMode === key ? "is-selected" : ""}" data-action="select-conversion" data-mode="${key}"><span class="row-icon">${icon(iconName)}</span><span class="conversion-option-copy"><strong>${title}</strong><span>${description}</span></span>${state.conversionMode === key ? icon("check") : icon("chevron-right")}</button>`).join("")}
             </div>
             <div class="conversion-settings">
               <div class="form-field"><label for="convert-page">页面范围</label><select class="select" id="convert-page"><option>全部页面</option><option>当前章节</option></select></div>
               <div class="form-field"><label for="convert-quality">输出质量</label><select class="select" id="convert-quality"><option>标准（推荐）</option><option>高清</option></select></div>
             </div>`
      }
    </aside>
    <section class="surface conversion-preview-panel">
      ${surfaceHeader("转换预览", `${selectedOption[1]} · A4`, "eye", badge("标准 A4", "info"))}
      <div class="conversion-preview-body">
        <article class="a4-preview">
          <h3>基于 Web 的校园智能文档辅助系统设计</h3>
          <div class="a4-meta">Web开发技术 · 张同学 · 2026-10-27</div>
          <p class="a4-abstract"><strong>摘要：</strong>本系统通过文档接入、智能处理、格式校验和版本协作，减少课程报告撰写过程中的重复操作。</p>
          <h4>1 项目背景</h4>
          <p>学生需要在多个文档工具间反复处理格式、图表编号和参考文献。系统将常用处理能力集中到统一工作流中。</p>
          <h4>2 系统设计</h4>
          <p>系统采用前后端分离架构，分为文档接入、智能处理、格式校验、协作版本和导出交付五个模块。</p>
          <h4>3 关键流程</h4>
          <p>文档上传后依次完成安全检查、文本解析、版面识别和内容索引。处理结果支持摘要、问答、润色和格式修复。</p>
          <h4>4 提交前检查</h4>
          <p>系统依据课程模板检查字体、字号、行距、页边距、图表题注和参考文献。阻断问题清零后可以提交作业。</p>
          <h4>5 验收指标</h4>
          <p>处理结果可追溯，引用可定位，版本可对比恢复，导出记录可重复获取。</p>
          <div class="a4-page-number">1</div>
        </article>
        ${state.conversionStarted ? `<div class="notice" style="margin:12px 0 0">${icon("loader-circle")}转换任务已开始，完成后会出现在下载中心。</div>` : ""}
      </div>
      <div class="conversion-footer">
        <span class="surface-meta">${selectedOption[2]}</span>
        <div class="row-aside">${button("取消", { action: "cancel-convert" })}${state.conversionStarted ? button("转换中…", { variant: "primary", iconName: "loader-circle", disabled: true }) : button("开始转换", { variant: "primary", iconName: "file-output", action: "start-conversion" })}</div>
      </div>
    </section>
  </div>`;
}

function screenCollaborate() {
  return `${pageHead({
    breadcrumb: "协作与版本 / 协作编辑",
    title: "协作编辑",
    lead: `${DOC_TITLE} · 自动保存已开启 · 今天 21:26`,
    actions: `${button("共享设置", { iconName: "share-2", action: "share" })}${button("保存并查看版本", { variant: "primary", iconName: "save", action: "versions" })}`,
  })}
  <div class="collab-layout">
    <section class="surface editor-shell">
      <div class="editor-toolbar">
        ${iconButton("arrow-left", { action: "preview", label: "返回文档预览" })}
        <span class="surface-meta">正在编辑 · 自动保存于 2 秒前</span>
        <div class="editor-toolbar-spacer"></div>
        <div class="avatar-stack" aria-label="当前协作者"><span class="avatar">张</span><span class="avatar" style="background:#6d28d9">李</span><span class="avatar" style="background:#0e7490">王</span></div>
        <span class="badge success">3 人在线</span>
      </div>
      <div class="editor-page">
        <article class="editor-paper">
          <h2 class="paper-title">基于 Web 的校园智能文档辅助系统设计</h2>
          <div class="paper-subtitle">Web开发技术 · 小组协作版 · v6</div>
          <h4>3 系统设计</h4>
          <p>系统采用前后端分离结构。前端负责工作台、文档预览与交互状态；服务层承担权限验证、任务调度和格式处理。</p>
          <p style="margin-top:9px">文档数据与处理结果通过对象存储分层保存，原始文件、解析结果和导出文件分别归档，避免版本覆盖。</p>
          <p style="margin-top:9px">所有协作会话都会保留最近保存状态，并在成员离开时提示确认当前修改已提交。</p>
          <span class="live-cursor">李同学正在编辑</span>
        </article>
      </div>
    </section>
    <aside class="surface comment-panel">
      ${surfaceHeader("协作评论", "2 条未处理", "message-circle")}
      <div class="comment"><strong>孙老师 · 教师批注</strong><p>系统设计部分建议补充权限校验流程，说明越权访问的处理结果。</p><div style="margin-top:8px">${button("定位批注", { small: true, variant: "ghost", action: "preview" })}</div></div>
      <div class="comment"><strong>李同学 · 21:18</strong><p>我更新了第 3.2 节的接口说明，请确认是否与测试部分的描述一致。</p><div style="margin-top:8px">${badge("待处理", "warning")}</div></div>
      <div class="comment"><strong>王同学 · 20:54</strong><p>版本已保存，图表题注部分已交给张同学继续处理。</p><div style="margin-top:8px">${badge("已解决", "success")}</div></div>
      <div class="surface-body" style="margin-top:auto;border-top:1px solid var(--border)"><div class="notice">${icon("info")}离开前请确认已保存；未保存的编辑会保留为协作草稿。</div></div>
    </aside>
  </div>`;
}

function versionRows() {
  const versions = [
    ["v6", "当前版本", "今天 21:24", "张同学", "格式修复与图表引用更新", "current"],
    ["v5", "协作快照", "今天 20:58", "李同学", "补充系统设计与测试章节", ""],
    ["v4", "润色结果", "今天 19:36", "张同学", "应用 1 段文本润色", ""],
    ["v3", "首次上传", "今天 18:42", "张同学", "完成上传与解析", ""],
  ];
  return `<div class="version-list">
    ${versions
      .map(
        ([version, kind, time, owner, description, className]) => `<div class="version-row ${className === "current" ? "is-current" : ""}">
          <span class="version-tag">${version}</span>
          <div class="version-copy"><strong>${description}</strong><span>${kind} · ${time} · ${owner}</span></div>
          <div class="row-aside">${version === "v6" ? badge("当前", "success") : button("对比", { small: true, action: "compare", variant: "ghost" })}${version === "v3" ? button("恢复", { small: true, action: "restore" }) : ""}</div>
        </div>`,
      )
      .join("")}
  </div>`;
}

function versionHistoryView() {
  return `<div class="split-layout is-uneven">
    <section class="surface">
      ${surfaceHeader("版本记录", "4 个版本", "history", `${button("对比当前版本", { variant: "primary", small: true, action: "compare" })}`)}
      ${versionRows()}
    </section>
    <div class="stack">
      <section class="surface">
        ${surfaceHeader("版本摘要", "v6", "info")}
        <div class="surface-body">
          <div class="review-list">
            <div class="review-item"><span class="check" style="border-color:var(--success);background:var(--success-bg);color:var(--success)">${icon("check")}</span><span class="review-copy"><strong>格式问题已修复</strong><span>4 个阻断问题已清零。</span></span></div>
            <div class="review-item"><span class="check" style="border-color:var(--success);background:var(--success-bg);color:var(--success)">${icon("check")}</span><span class="review-copy"><strong>协作内容已合并</strong><span>合并李同学的第 3.2 节说明。</span></span></div>
          </div>
        </div>
      </section>
      <section class="surface">
        ${surfaceHeader("版本保留策略", "学生可见", "lock-keyhole")}
        <div class="surface-body"><div class="notice">${icon("info")}历史版本由系统保留，恢复操作会创建新版本，不会覆盖当前内容。</div></div>
      </section>
    </div>
  </div>`;
}

function screenVersions() {
  return `${pageHead({
    breadcrumb: "协作与版本 / 版本记录",
    title: "版本记录",
    lead: "查看每次保存的版本说明、协作者与生成原因。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("导出记录", { iconName: "download", action: "downloads" })}`,
  })}
  ${versionHistoryView()}`;
}

function screenCompare() {
  return `${pageHead({
    breadcrumb: "协作与版本 / 版本对比",
    title: "版本对比",
    lead: "比较两个历史版本的正文、结构与格式变化，确认后再恢复。",
    actions: `${button("返回版本记录", { iconName: "arrow-left", action: "versions" })}${button("恢复 v3", { iconName: "rotate-ccw", action: "restore" })}`,
  })}
  ${sourceContext("对比 v3 与 v6 · 新增 176 字 · 删除 42 字 · 修改 2 段")}
  <div class="surface">
    ${surfaceHeader("版本差异", "v3 → v6", "git-compare-arrows", `<div class="row-aside"><select class="select"><option>v3 → v6</option><option>v4 → v6</option></select>${button("恢复 v3", { small: true, action: "restore" })}</div>`)}
    <div class="surface-body">
      <div class="diff-grid">
        <section class="diff-side">
          <h3>版本 v3 · 今天 18:42</h3>
          <p>系统采用前后端分离结构。前端负责工作台、文档预览与交互状态；服务层承担权限验证和任务调度。</p>
          <p><span class="mark-del">格式校验在导出后执行。</span></p>
          <p>协作会话记录最近保存状态。</p>
        </section>
        <section class="diff-side">
          <h3>版本 v6 · 今天 21:24</h3>
          <p>系统采用前后端分离结构。前端负责工作台、文档预览与交互状态；服务层承担权限验证、任务调度和格式处理。</p>
          <p><span class="mark-add">格式校验在导出与作业提交前执行，阻断问题清零后才允许提交。</span></p>
          <p>所有协作会话都会保留最近保存状态，并在成员离开时提示确认修改已提交。</p>
        </section>
      </div>
      <div class="notice" style="margin-top:14px">${icon("info")}恢复 v3 会把历史内容复制为新的 v7，当前 v6 不会被删除。</div>
    </div>
  </div>`;
}

function restoreModal() {
  return `<div class="modal-scrim">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="restore-title">
      <div class="modal-header">
        <div><h2 class="modal-title" id="restore-title">恢复历史版本 v3？</h2><p class="modal-subtitle">恢复后将创建新版本 v7，当前 v6 会保留在版本记录中。</p></div>
        ${iconButton("x", { action: "close-restore", label: "关闭恢复版本弹窗" })}
      </div>
      <div class="modal-body">
        <div class="notice warning">${icon("alert-triangle")}以下内容会回退到 v3：系统设计与测试章节的 2 处修改。</div>
        <div class="selection-grid" style="margin-top:14px">
          <button type="button" class="selection-card is-selected" data-action="select-choice" aria-pressed="true"><strong>${icon("file-plus-2")}恢复为新版本</strong><p>保留所有历史版本，便于继续协作。</p></button>
          <button type="button" class="selection-card" data-action="close-restore"><strong>${icon("x")}暂不恢复</strong><p>返回版本记录继续查看差异。</p></button>
        </div>
        <div class="setting-row" style="padding:0;margin-top:14px"><div class="setting-copy"><strong>恢复后打开新版本</strong><span>可直接继续编辑或重新校验。</span></div><button type="button" class="switch is-on" aria-label="恢复后打开新版本" data-action="toggle-switch" data-switch="open-restored"></button></div>
      </div>
      <div class="modal-footer">${button("取消", { action: "close-restore" })}${button("确认恢复", { variant: "primary", iconName: "rotate-ccw", action: "confirm-restore" })}</div>
    </section>
  </div>`;
}

function screenRestore() {
  return `${pageHead({
    breadcrumb: "协作与版本 / 恢复版本确认",
    title: "版本记录",
    lead: "恢复历史内容前，确认影响范围与版本保留方式。",
    actions: `${button("返回版本记录", { iconName: "arrow-left", action: "versions" })}`,
  })}
  ${versionHistoryView()}`;
}

function membersView() {
  return `<div class="member-list">
    <div class="member-row"><span class="avatar">张</span><div class="member-copy"><strong>张同学（你）</strong><span>zhang@campus.edu.cn</span></div>${badge("所有者", "info")}</div>
    <div class="member-row"><span class="avatar" style="background:#6d28d9">李</span><div class="member-copy"><strong>李同学</strong><span>li@campus.edu.cn</span></div>${badge("可编辑", "success")}</div>
    <div class="member-row"><span class="avatar" style="background:#0e7490">王</span><div class="member-copy"><strong>王同学</strong><span>wang@campus.edu.cn</span></div>${badge("可评论", "info")}</div>
  </div>`;
}

function shareView() {
  return `<div class="sharing-grid">
    <section class="surface">
      ${surfaceHeader("访问范围", "仅课程成员可访问", "lock-keyhole", button("添加协作者", { variant: "primary", small: true, iconName: "user-plus", action: "collaborator" }))}
      <div class="surface-body">
        <button type="button" class="radio-row ${state.shareScope === "private" ? "is-selected" : ""}" data-action="select-scope" data-scope="private"><span class="radio"></span><div class="radio-copy"><strong>仅本人</strong><span>仅文档所有者可以访问。</span></div></button>
        <button type="button" class="radio-row ${state.shareScope === "members" ? "is-selected" : ""}" data-action="select-scope" data-scope="members"><span class="radio"></span><div class="radio-copy"><strong>指定成员</strong><span>仅被邀请的协作者可以访问。</span></div></button>
        <button type="button" class="radio-row ${state.shareScope === "course" ? "is-selected" : ""}" data-action="select-scope" data-scope="course"><span class="radio"></span><div class="radio-copy"><strong>课程成员</strong><span>${COURSE} 的教师和同学可以查看，只有指定成员可编辑。</span></div></button>
        <button type="button" class="radio-row ${state.shareScope === "link" ? "is-selected" : ""}" data-action="select-scope" data-scope="link"><span class="radio"></span><div class="radio-copy"><strong>拥有链接的人</strong><span>需要登录校园账号，链接可随时关闭。</span></div></button>
      </div>
      ${surfaceHeader("协作者", "3 人", "users")}
      ${membersView()}
    </section>
    <div class="stack">
      <section class="surface">
        ${surfaceHeader("链接权限", "默认关闭", "link-2")}
        <div class="setting-row"><div class="setting-copy"><strong>允许课程成员查看</strong><span>仅已选课程中的师生可查看。</span></div><button type="button" class="switch is-on" data-action="toggle-switch" data-switch="course-link" aria-label="允许课程成员查看"></button></div>
        <div class="setting-row"><div class="setting-copy"><strong>允许下载</strong><span>建议在提交前关闭下载。</span></div><button type="button" class="switch" data-action="toggle-switch" data-switch="download-link" aria-label="允许下载"></button></div>
        <div class="setting-row"><div class="setting-copy"><strong>链接有效期</strong><span>到期后自动失效。</span></div><select class="select" style="height:32px"><option>7 天</option><option>24 小时</option><option>永久</option></select></div>
      </section>
      <section class="surface">
        ${surfaceHeader("分享链接", "仅校内账号可用", "copy")}
        <div class="surface-body">
          <div class="selected-file"><div class="row-copy"><strong>https://edu.example/doc/7Q4K2M</strong><span>需要登录校园身份认证</span></div>${iconButton("copy", { action: "copy", label: "复制链接", extra: `data-copy-text="https://edu.example/doc/7Q4K2M"` })}</div>
          <button type="button" class="button primary" style="width:100%;margin-top:10px" data-action="save">${icon("save")}<span>保存共享设置</span></button>
        </div>
      </section>
    </div>
  </div>`;
}

function screenShare() {
  return `${pageHead({
    breadcrumb: "协作与版本 / 共享设置",
    title: "共享设置",
    lead: `${DOC_TITLE} · 管理访问范围、协作权限与外部链接。`,
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("保存设置", { variant: "primary", iconName: "save", action: "save" })}`,
  })}
  ${shareView()}`;
}

function collaboratorModal() {
  return `<div class="modal-scrim">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="collaborator-title">
      <div class="modal-header">
        <div><h2 class="modal-title" id="collaborator-title">添加协作者</h2><p class="modal-subtitle">只邀请课程成员参与当前文档。</p></div>
        ${iconButton("x", { action: "close-collaborator", label: "关闭添加协作者弹窗" })}
      </div>
      <div class="modal-body">
        <label class="search-field" style="width:100%">${icon("user-plus")}<input placeholder="搜索姓名、学号或校园邮箱" aria-label="搜索协作者"></label>
        <div class="selected-file" style="margin-top:12px">${rowIcon("user-round")}<div class="row-copy"><strong>陈同学</strong><span>chen@campus.edu.cn · 计算机学院</span></div>${badge("课程成员", "success")}</div>
        <div class="form-grid">
          <div class="form-field"><label for="collab-role">协作角色</label><select class="select" id="collab-role"><option>可编辑</option><option>可评论</option><option>仅查看</option></select></div>
          <div class="form-field"><label for="collab-message">邀请说明</label><input class="field" id="collab-message" value="请协助完成系统设计章节"></div>
        </div>
        <div class="notice" style="margin-top:12px">${icon("info")}邀请会发送到消息中心；对方接受后才会获得文档访问权限。</div>
      </div>
      <div class="modal-footer">${button("取消", { action: "close-collaborator" })}${button("发送邀请", { variant: "primary", iconName: "send", action: "send-invite" })}</div>
    </section>
  </div>`;
}

function screenCollaborator() {
  return `${pageHead({
    breadcrumb: "协作与版本 / 添加协作者",
    title: "共享设置",
    lead: "为课程成员分配查看、评论或编辑权限。",
    actions: `${button("返回共享设置", { iconName: "arrow-left", action: "share" })}`,
  })}
  ${shareView()}`;
}

function screenExport() {
  return `${pageHead({
    breadcrumb: "我的文档 / 导出文档",
    title: "导出文档",
    lead: "选择导出范围与格式，生成结果会写入下载中心。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}${button("下载中心", { iconName: "download", action: "downloads" })}`,
  })}
  <div class="export-summary">
    <section class="surface">
      ${surfaceHeader("导出设置", "导出不影响当前文档", "file-output")}
      <div class="surface-body">
        <div class="form-grid" style="margin-top:0">
          <div class="form-field"><label for="export-scope">导出范围</label><select class="select" id="export-scope"><option>全文</option><option>当前章节</option><option>选中内容</option></select></div>
          <div class="form-field"><label for="export-format">文件格式</label><select class="select" id="export-format"><option>DOCX</option><option>PDF</option><option>Markdown</option></select></div>
        </div>
        <div class="selection-grid" style="margin-top:14px">
          <button type="button" class="selection-card is-selected" data-action="select-choice" aria-pressed="true"><strong>${icon("file-text")}完整文档</strong><p>包含封面、目录、正文、图表和参考文献。</p></button>
          <button type="button" class="selection-card" data-action="select-choice" aria-pressed="false"><strong>${icon("book-open")}正文与附录</strong><p>不包含封面和自动目录，便于二次编辑。</p></button>
        </div>
        <div class="setting-row" style="padding:0;margin-top:14px"><div class="setting-copy"><strong>保留书签与超链接</strong><span>便于教师查看和原文定位。</span></div><button type="button" class="switch is-on" data-action="toggle-switch" data-switch="export-links" aria-label="保留书签与超链接"></button></div>
        <div class="setting-row" style="padding:0"><div class="setting-copy"><strong>保留处理标记</strong><span>输出文件中显示引用与格式修复记录。</span></div><button type="button" class="switch" data-action="toggle-switch" data-switch="export-marks" aria-label="保留处理标记"></button></div>
        ${state.exportDone ? `<div class="notice" style="margin-top:14px">${icon("check-circle-2")}导出完成：系统已生成 DOCX 文件，可在下载中心重复获取。</div>` : ""}
      </div>
      <div class="modal-footer">${button("取消", { action: "preview" })}${state.exportDone ? button("前往下载中心", { variant: "primary", iconName: "download", action: "downloads" }) : button("开始导出", { variant: "primary", iconName: "file-output", action: "start-export" })}</div>
    </section>
    <section class="surface">
      ${surfaceHeader("导出预览", "v6 · 24 页", "eye")}
      <div class="surface-body">
        <div class="summary-paper">
          <h3>基于 Web 的校园智能文档辅助系统设计</h3>
          <div class="paper-meta">Web开发技术 · 张同学 · 2026-10-27</div>
          <p><strong>摘要：</strong>本系统面向校园师生的文档处理需求，将文档接入、智能处理、格式校验和版本协作整合为统一工作流。</p>
          <p style="margin-top:10px"><strong>目录</strong></p>
          <p>1 项目背景<br>2 需求分析<br>3 系统设计<br>4 关键流程<br>5 测试与验收<br>6 参考文献</p>
          <p style="margin-top:10px"><strong>格式状态：</strong>已通过课程模板 v2.1 校验。</p>
        </div>
        <div class="notice" style="margin-top:12px">${icon("info")}导出记录仅本人可见，下载动作会写入个人操作记录。</div>
      </div>
    </section>
  </div>`;
}

function screenDownloads() {
  const rows = [
    ["Web开发课程设计报告_张同学_v6.docx", "DOCX", "2.9MB", "今天 21:31", "可下载", "success"],
    ["Web开发课程设计报告_张同学_v6.pdf", "PDF", "4.1MB", "今天 21:30", "可下载", "success"],
    ["Oracle课程设计_摘要.pdf", "PDF", "1.2MB", "今天 20:02", "可下载", "success"],
    ["JavaEE课程作业_小组版.docx", "DOCX", "3.5MB", "昨天 22:18", "已过期", ""],
  ];
  return `${pageHead({
    breadcrumb: "我的文档 / 下载中心",
    title: "下载中心",
    lead: "获取本人导出的文档与智能处理结果，记录保留 7 天。",
    actions: `${button("新建导出", { variant: "primary", iconName: "file-output", action: "export" })}`,
  })}
  <section class="surface">
    ${surfaceHeader("导出记录", "4 条记录", "download", `<div class="segmented"><button type="button" class="${state.downloadFilter === "all" ? "is-active" : ""}" data-action="filter-downloads" data-filter="all">全部</button><button type="button" class="${state.downloadFilter === "available" ? "is-active" : ""}" data-action="filter-downloads" data-filter="available">可下载</button><button type="button" class="${state.downloadFilter === "expired" ? "is-active" : ""}" data-action="filter-downloads" data-filter="expired">已过期</button></div>`)}
    <div class="download-list">
      ${rows
        .map(([name, format, size, time, status, tone]) => {
          const downloadState = status === "可下载" ? "available" : "expired";
          return `<div class="download-row" data-download-row data-download-state="${downloadState}" ${state.downloadFilter !== "all" && state.downloadFilter !== downloadState ? "hidden" : ""}>
            <div class="doc-cell">${icon(format === "PDF" ? "file-type-2" : "file-text")}<div class="row-copy"><strong>${name}</strong><span>${format} · ${size}</span></div></div>
            <span class="surface-meta">${time}</span>
            <div>${badge(status, tone)}</div>
            <div class="row-aside">${status === "可下载" ? button("下载", { small: true, iconName: "download", action: "download" }) : button("重新导出", { small: true, action: "export", variant: "ghost" })}${iconButton("more-horizontal", { action: "save", label: `更多操作 ${name}` })}</div>
          </div>`;
        })
        .join("")}
    </div>
    <div class="toolbar"><span class="surface-meta">共 4 条记录 · 已占用 11.7MB</span><div class="toolbar-spacer"></div>${button("清理过期记录", { variant: "ghost", iconName: "trash-2", action: "save" })}</div>
  </section>`;
}

function screenAssignments() {
  const assignments = [
    ["Web开发课程设计报告", COURSE, "10月30日 23:59", "3 天后截止", "待提交", "warning"],
    ["数据库实验报告", "数据库原理", "11月02日 18:00", "6 天后截止", "已提交", "success"],
    ["JavaEE 小组项目", "JavaEE 程序设计", "11月06日 23:59", "10 天后截止", "协作中", "violet"],
    ["软件工程课程论文", "软件工程", "10月20日 23:59", "已截止", "已批改", "info"],
  ];
  return `${pageHead({
    breadcrumb: "课程与作业 / 学生作业列表",
    title: "课程作业",
    lead: "查看待提交、已提交和已批改作业，进入提交前会先执行格式检查。",
    actions: `${button("查看消息", { iconName: "bell", action: "open-messages" })}${button("上传课程文档", { variant: "primary", iconName: "upload", action: "upload" })}`,
  })}
  <section class="surface">
    <div class="assignment-tabs"><button type="button" class="tab ${state.assignmentFilter === "all" ? "is-active" : ""}" data-action="filter-assignments" data-filter="all">全部 4</button><button type="button" class="tab ${state.assignmentFilter === "pending" ? "is-active" : ""}" data-action="filter-assignments" data-filter="pending">待提交 1</button><button type="button" class="tab ${state.assignmentFilter === "submitted" ? "is-active" : ""}" data-action="filter-assignments" data-filter="submitted">已提交 1</button><button type="button" class="tab ${state.assignmentFilter === "collaborating" ? "is-active" : ""}" data-action="filter-assignments" data-filter="collaborating">协作中 1</button><button type="button" class="tab ${state.assignmentFilter === "graded" ? "is-active" : ""}" data-action="filter-assignments" data-filter="graded">已批改 1</button><div class="toolbar-spacer"></div><select class="select"><option>全部课程</option><option>${COURSE}</option></select></div>
    ${assignments
      .map(
        ([title, course, deadline, remain, status, tone], index) => {
          const assignmentState = { 待提交: "pending", 已提交: "submitted", 协作中: "collaborating", 已批改: "graded" }[status];
          return `<div class="assignment-row" data-assignment-row data-assignment-state="${assignmentState}" ${state.assignmentFilter !== "all" && state.assignmentFilter !== assignmentState ? "hidden" : ""}>
          ${rowIcon(index === 3 ? "badge-check" : "clipboard-check", status === "待提交" ? "is-warning" : status === "协作中" ? "is-violet" : "is-success")}
          <div class="assignment-copy"><strong>${title}</strong><span>${course} · 截止 ${deadline}</span></div>
          <span class="surface-meta">${remain}</span>
          <div style="display:flex;align-items:center;gap:8px">${badge(status, tone)}${index === 3 ? badge("92 分", "success", true) : ""}${button(status === "待提交" ? "提交作业" : status === "已批改" ? "查看反馈" : "查看提交", { small: true, variant: status === "待提交" ? "primary" : "", action: status === "待提交" || status === "已提交" ? "submit" : "messages" })}</div>
        </div>`;
        },
      )
      .join("")}
  </section>`;
}

function submitChecklist() {
  return `<div class="review-list">
    <label class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>文档已选择</strong><span>${DOC_TITLE} · v6</span></span>${badge("通过", "success")}</label>
    <label class="review-item is-selected"><span class="check">${icon("check")}</span><span class="review-copy"><strong>格式规范已通过</strong><span>课程模板 v2.1 · 4 个阻断问题已修复</span></span>${badge("通过", "success")}</label>
    <label class="review-item"><span class="check"></span><span class="review-copy"><strong>提交说明</strong><span>可选：向教师补充说明本次提交的重点。</span></span>${badge("可选", "")}</label>
  </div>`;
}

function screenSubmit() {
  return `${pageHead({
    breadcrumb: "课程与作业 / 提交作业",
    title: "提交作业",
    lead: "Web开发课程设计报告 · 截止 10月30日 23:59 · 截止前可重新提交",
    actions: `${button("返回列表", { iconName: "arrow-left", action: "assignments" })}${button("查看模板", { iconName: "layout-template", action: "templates" })}`,
  })}
  <div class="split-layout">
    <section class="surface">
      ${surfaceHeader("提交前检查", "3 项", "shield-check", badge("可提交", "success"))}
      <div class="surface-body">
        ${submitChecklist()}
        <div class="form-field is-full" style="margin-top:14px"><label for="submit-note">提交说明（可选）</label><textarea class="field" id="submit-note" style="height:80px;padding:10px">已完成格式修复与图表引用更新，请老师查看第 3 章系统设计部分。</textarea></div>
        ${state.submitDone ? `<div class="notice" style="margin-top:14px">${icon("check-circle-2")}提交成功。系统已记录当前版本、提交时间和格式校验结果。</div>` : ""}
      </div>
      <div class="modal-footer">${button("保存草稿", { iconName: "save", action: "save" })}${state.submitDone ? button("查看提交记录", { variant: "primary", iconName: "history", action: "assignments" }) : button("确认提交", { variant: "primary", iconName: "send", action: "confirm-submit" })}</div>
    </section>
    <div class="stack">
      <section class="surface">
        ${surfaceHeader("选择提交文档", "v6", "files")}
        <div class="surface-body">
          <div class="selected-file">${rowIcon("file-text")}<div class="row-copy"><strong>${DOC_TITLE}</strong><span>最近修改今天 21:24 · 已通过格式校验</span></div>${badge("当前", "success")}</div>
          <button type="button" class="button" style="width:100%;margin-top:10px" data-action="documents">${icon("files")}<span>选择其他文档</span></button>
        </div>
      </section>
      <section class="surface">
        ${surfaceHeader("提交政策", "课程设置", "info")}
        <div class="surface-body"><div class="notice">${icon("info")}截止前可以重新提交，系统会保留每次提交的版本与校验结果。提交后会通知任课教师。</div></div>
      </section>
    </div>
  </div>`;
}

function screenMessages() {
  const messages = [
    ["bell", "作业截止提醒", "Web开发课程设计报告将在 10月30日 23:59 截止，请完成格式校验后提交。", "今天 09:00", true, "is-warning", "assignments", "task"],
    ["shield-check", "格式校验完成", `${DOC_TITLE} 的 4 个阻断问题已修复，当前版本 v6 可以提交。`, "今天 21:22", true, "is-success", "format", "task"],
    ["message-circle", "教师批注", "孙老师给 JavaEE课程作业_小组版.docx 添加了 2 条批注。", "昨天 18:20", true, "is-violet", "collaborate", "teaching"],
    ["users", "协作邀请已接受", "李同学和王同学已加入当前课程文档协作。", "昨天 16:42", false, "", "share", "teaching"],
    ["download", "导出完成", "数据库原理复习提纲_摘要.pdf 已生成，可在下载中心获取。", "10月24日", false, "is-success", "downloads", "system"],
  ];
  const unreadCount = state.messagesRead ? 0 : 3;
  return `${pageHead({
    breadcrumb: "消息通知 / 消息中心",
    title: "消息中心",
    lead: "集中查看作业提醒、格式校验、教师批注和协作通知。",
    actions: `${state.messagesRead ? badge("全部已读", "success") : button("全部标为已读", { iconName: "check", action: "mark-all-read" })}${button("通知设置", { variant: "primary", iconName: "settings-2", action: "profile" })}`,
  })}
  <div class="split-layout" style="grid-template-columns:230px minmax(0,1fr)">
    <section class="surface">
      <div class="surface-body" style="padding-bottom:10px"><div class="segmented" style="display:grid;grid-template-columns:1fr"><button type="button" class="${state.messageFilter === "all" ? "is-active" : ""}" data-action="filter-messages" data-filter="all">全部 12</button><button type="button" class="${state.messageFilter === "unread" ? "is-active" : ""}" data-action="filter-messages" data-filter="unread">未读 3</button><button type="button" class="${state.messageFilter === "task" ? "is-active" : ""}" data-action="filter-messages" data-filter="task">任务 6</button><button type="button" class="${state.messageFilter === "teaching" ? "is-active" : ""}" data-action="filter-messages" data-filter="teaching">教学 4</button><button type="button" class="${state.messageFilter === "system" ? "is-active" : ""}" data-action="filter-messages" data-filter="system">系统 2</button></div></div>
      <div class="comment"><strong>免打扰</strong><p>22:00–07:00<br>截止前 24 小时仍会提醒。</p></div>
    </section>
    <section class="surface">
      ${surfaceHeader("全部消息", "", "inbox", `<div class="row-aside"><span class="surface-meta">${unreadCount} 条未读</span>${unreadCount ? button("全部标为已读", { small: true, variant: "ghost", action: "mark-all-read" }) : badge("全部已读", "success")}</div>`)}
      <div class="message-list">
        ${messages
          .map(
            ([iconName, title, body, time, unreadRaw, tone, action, type]) => {
              const unread = unreadRaw && !state.messagesRead;
              const hidden = state.messageFilter === "unread" ? !unread : state.messageFilter !== "all" && state.messageFilter !== type;
              return `<div class="message-row ${unread ? "is-unread" : ""}" role="button" tabindex="0" data-action="${action}" data-message-row data-message-type="${type}" aria-label="查看${title}详情" ${hidden ? "hidden" : ""}>
              ${rowIcon(iconName, tone)}
              <div class="message-copy"><strong>${unread ? '<span class="dot"></span>' : ""}${title}</strong><p>${body}</p></div>
              <div class="message-time">${time}<div class="message-actions">${button("查看详情", { small: true, variant: "link", action, extra: `aria-label="查看${title}详情"` })}${unread ? button("标为已读", { small: true, variant: "link", action: "mark-read", extra: `aria-label="标为已读：${title}"` }) : ""}</div></div>
            </div>`;
            },
          )
          .join("")}
      </div>
    </section>
  </div>`;
}

function screenProfile() {
  return `${pageHead({
    breadcrumb: "个人中心 / 个人资料",
    title: "个人资料",
    lead: "维护学生账号信息、安全设置和通知偏好。",
    actions: `${state.editingProfile ? button("取消编辑", { action: "cancel-profile-edit", variant: "ghost" }) : button("编辑资料", { variant: "primary", iconName: "edit-3", action: "edit-profile" })}${state.editingProfile ? button("保存修改", { variant: "primary", iconName: "save", action: "save-profile" }) : ""}`,
  })}
  <div class="profile-layout">
    <section class="surface profile-summary">
      <div class="profile-avatar">${esc(state.profile.name.slice(0, 1))}</div>
      <h2>${esc(state.profile.name)}</h2>
      <p>学生 · 计算机学院 · 2023001</p>
      <div class="profile-meta"><div><strong>12</strong><span>本月文档</span></div><div><strong>8</strong><span>格式通过</span></div></div>
      <div class="notice" style="margin-top:16px;text-align:left">${icon("graduation-cap")}${COURSE}<br>本学期课程成员</div>
      <button type="button" class="button ghost" style="width:100%;margin-top:12px" data-action="logout">${icon("log-out")}<span>退出登录</span></button>
    </section>
    <div class="stack">
      <section class="surface">
        ${surfaceHeader("基础信息", "实名信息由学校同步", "user-round")}
        <div class="profile-form">
          <div class="profile-form-grid">
            <div class="form-field"><label for="profile-name">姓名</label><input class="field" id="profile-name" value="${esc(state.profile.name)}" ${state.editingProfile ? "" : "readonly"}></div>
            <div class="form-field"><label for="profile-student-id">学号</label><input class="field" id="profile-student-id" value="2023001" readonly></div>
            <div class="form-field"><label for="profile-email">校园邮箱</label><input class="field" id="profile-email" value="${esc(state.profile.email)}" ${state.editingProfile ? "" : "readonly"}></div>
            <div class="form-field"><label for="profile-college">学院</label><input class="field" id="profile-college" value="计算机学院" readonly></div>
          </div>
        </div>
      </section>
      <section class="surface">
        ${surfaceHeader("账号与安全", "上次登录今天 09:12", "shield-check")}
        <div class="security-list">
          <div class="security-row">${rowIcon("lock-keyhole", "is-success")}<div class="row-copy"><strong>登录密码</strong><span>90 天前更新</span></div>${button("修改", { small: true, action: "edit-password" })}</div>
          <div class="security-row">${rowIcon("smartphone", "is-success")}<div class="row-copy"><strong>手机验证</strong><span>已绑定 · 138****6721</span></div>${badge("已启用", "success")}</div>
          <div class="security-row">${rowIcon("history")}<div class="row-copy"><strong>登录记录</strong><span>最近一次来自校园网络</span></div>${button("查看", { small: true, variant: "ghost", action: "view-login-records" })}</div>
        </div>
      </section>
      <section class="surface">
        ${surfaceHeader("通知偏好", "学生账号", "bell")}
        <div class="setting-row"><div class="setting-copy"><strong>作业截止提醒</strong><span>截止前 72 小时与 24 小时提醒。</span></div><button type="button" class="switch is-on" aria-label="作业截止提醒" data-action="toggle-switch" data-switch="deadline"></button></div>
        <div class="setting-row"><div class="setting-copy"><strong>格式校验结果</strong><span>完成后发送站内消息。</span></div><button type="button" class="switch is-on" aria-label="格式校验结果" data-action="toggle-switch" data-switch="format-alerts"></button></div>
        <div class="setting-row"><div class="setting-copy"><strong>协作动态</strong><span>批注、邀请和版本更新。</span></div><button type="button" class="switch is-on" aria-label="协作动态" data-action="toggle-switch" data-switch="collab-alerts"></button></div>
      </section>
    </div>
  </div>`;
}

function screenUpload() {
  return `${pageHead({
    breadcrumb: "我的工作 / 我的文档",
    title: "我的文档",
    lead: "上传文档并指定课程，文件解析完成后会自动进入智能处理。",
    actions: `${button("返回文档列表", { iconName: "arrow-left", action: "close-upload" })}`,
  })}
  ${documentsTable()}`;
}

function screenProcessing() {
  return `${pageHead({
    breadcrumb: "我的工作 / 我的文档",
    title: "我的文档",
    lead: "当前文档正在处理中，可以等待完成或转到后台继续。",
    actions: `${button("返回文档列表", { iconName: "arrow-left", action: "close-processing" })}`,
  })}
  ${documentsTable()}`;
}

function screenAutofix() {
  return `${pageHead({
    breadcrumb: "格式与模板 / 格式规范校验",
    title: "格式规范校验",
    lead: "确认自动修复范围，修复后会重新校验并生成新版本。",
    actions: `${button("返回文档", { iconName: "arrow-left", action: "preview" })}`,
  })}
  <div class="split-layout is-uneven">
    <section class="surface">${surfaceHeader("检查结果", "校验于 21:20", "shield-check", badge("未通过", "danger"))}<div class="surface-body">${formatIssues()}</div></section>
    <section class="surface">${surfaceHeader("修复说明", "生成 v6", "wand-sparkles")}<div class="surface-body"><div class="notice">${icon("info")}只会修改选中的格式问题，原文内容不会变化。</div></div></section>
  </div>`;
}

const SCREENS = [
  screenWorkbench,
  screenDocuments,
  screenUpload,
  screenProcessing,
  screenPreview,
  screenSummary,
  screenKeypoints,
  screenQA,
  screenRefusal,
  screenPolish,
  screenTemplates,
  screenNormalize,
  screenFormat,
  screenAutofix,
  screenConvert,
  screenCollaborate,
  screenVersions,
  screenCompare,
  screenRestore,
  screenShare,
  screenCollaborator,
  screenExport,
  screenDownloads,
  screenAssignments,
  screenSubmit,
  screenMessages,
  screenProfile,
];

function render() {
  const app = document.getElementById("app");
  app.innerHTML = shell(SCREENS[state.index](), modalForRoute());
  applyDocumentFilters();
  if (window.lucide?.createIcons) {
    window.lucide.createIcons({
      attrs: {
        "stroke-width": 1.8,
        "aria-hidden": "true",
      },
    });
  }
}

function handleInput(event) {
  const target = event.target;
  if (target.matches("[data-doc-search]")) {
    state.docs.query = target.value;
    applyDocumentFilters();
  }
  if (target.matches("[data-qa-input]")) {
    state.qaInput = target.value;
  }
  if (target.matches("[data-summary-prompt]")) {
    state.summaryPrompt = target.value;
  }
}

function handleChange(event) {
  const target = event.target;
  if (target.matches("[data-doc-course]")) {
    state.docs.course = target.value;
    applyDocumentFilters();
  }
  if (target.matches("[data-doc-status]")) {
    state.docs.status = target.value;
    applyDocumentFilters();
  }
  if (target.matches("[data-summary-focus]")) {
    state.summaryFocus = target.value;
    render();
  }
}

function applyInitialRoute() {
  const id = location.hash.replace("#", "");
  const index = ROUTE.findIndex((screen) => screen.id === id);
  if (index >= 0) state.index = index;
  render();
}

document.addEventListener("click", action);
document.addEventListener("input", handleInput);
document.addEventListener("change", handleChange);
document.addEventListener("keydown", (event) => {
  const isField = event.target.closest("input, textarea, select");
  if (isField) return;
  if (event.target.matches('[role="button"][data-action]') && (event.key === "Enter" || event.key === " ")) {
    event.preventDefault();
    event.target.click();
    return;
  }
  if (event.key === "Escape" && document.querySelector(".modal-scrim")) {
    dismissModal();
    return;
  }
  if (event.key === "Escape" && state.templateModal !== null) {
    state.templateModal = null;
    render();
  }
});
applyInitialRoute();
