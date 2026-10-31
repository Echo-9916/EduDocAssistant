const BASE_URL = "http://localhost:4181";
const CODE_MARKER = "v2-hot";

function showStatus(text) {
  const safe = String(text).replace(/[&<>]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[char]));
  figma.showUI(
    `<div style="font-family:sans-serif;font-size:12px;line-height:1.5;white-space:pre-wrap;padding:10px;color:#111">${safe}</div>`,
    { width: 420, height: 260, title: "CIDAS Normalize" },
  );
}

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`GET failed: ${response.status} ${url}`);
  return response.json();
}

async function postJson(url, payload) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error(`POST failed: ${response.status} ${url}`);
}

function nodeRef(node) {
  return {
    id: node.id,
    name: node.name,
    type: node.type,
    visible: node.visible,
    layoutMode: "layoutMode" in node ? node.layoutMode : undefined,
    children: "children" in node ? node.children.length : undefined,
  };
}

function fullTree(node, depth = 0) {
  const entry = nodeRef(node);
  if (depth >= 8) return entry;
  if ("children" in node) {
    entry.childrenNodes = node.children.map((child) => fullTree(child, depth + 1));
  }
  return entry;
}

function shallowTree(node, depth = 2) {
  const entry = nodeRef(node);
  if (depth <= 0) return entry;
  if ("children" in node) {
    entry.childrenNodes = node.children.map((child) => shallowTree(child, depth - 1));
  }
  return entry;
}

const AUTH_FRAME_NAMES = [
  "登录",
  "登录 · 密码可见",
  "统一身份认证回调",
  "找回密码",
  "账号受限与权限申请",
];

const SCREEN_PREFIXES = ["学生路线 v3 /", "教师端 v3 /", "运维端 v3 /"];

function isScreenFrame(node) {
  return (
    node.type === "FRAME" &&
    node.width === 1440 &&
    node.height === 900 &&
    SCREEN_PREFIXES.some((prefix) => node.name.startsWith(prefix))
  );
}

async function collectDump() {
  const pages = [];
  for (const page of figma.root.children) {
    await figma.setCurrentPageAsync(page);
    const topLevel = page.children.map(nodeRef);
    const authTrees = page.children
      .filter((node) => AUTH_FRAME_NAMES.includes(node.name))
      .map((node) => fullTree(node));
    const screenTrees = page.children
      .filter(isScreenFrame)
      .sort((a, b) => a.name.localeCompare(b.name, "zh-CN"))
      .map((node) => shallowTree(node, 2));
    pages.push({
      id: page.id,
      name: page.name,
      topLevelCount: page.children.length,
      topLevel,
      authTrees,
      screenTrees,
    });
  }
  return {
    fileKey: figma.fileKey,
    pages,
  };
}

async function applyTask(task) {
  const results = [];
  const operations = Array.isArray(task.operations) ? task.operations : [];
  for (const op of operations) {
    try {
      const node = await figma.getNodeByIdAsync(op.id);
      if (!node) {
        results.push({ id: op.id, status: "missing" });
        continue;
      }
      if (op.expectedName !== undefined && node.name !== op.expectedName) {
        results.push({
          id: op.id,
          status: "guard-mismatch",
          currentName: node.name,
          expectedName: op.expectedName,
        });
        continue;
      }
      const previous = node.name;
      node.name = op.newName;
      results.push({ id: op.id, status: "renamed", from: previous, to: op.newName });
    } catch (error) {
      results.push({ id: op.id, status: "error", error: String(error) });
    }
  }
  let version = null;
  if (task.saveVersion !== false) {
    version = await figma.saveVersionHistoryAsync(
      task.versionTitle || "Normalize layer naming",
      task.versionDescription || "Applied the login-screen grouping and naming conventions across route screens.",
    );
  }
  return { results, version, applied: results.filter((item) => item.status === "renamed").length };
}

function center(node) {
  const box = node.absoluteBoundingBox;
  if (!box) return null;
  return { x: box.x + box.width / 2, y: box.y + box.height / 2, box };
}

function containsPoint(box, point) {
  return (
    point.x >= box.x &&
    point.x <= box.x + box.width &&
    point.y >= box.y &&
    point.y <= box.y + box.height
  );
}

function inside(node, container) {
  const containerBox = container.absoluteBoundingBox;
  if (!containerBox) return false;
  const point = center(node);
  return Boolean(point && containsPoint(containerBox, point));
}

function rename(node, newName, stats) {
  if (!node || node.name === newName) return false;
  node.name = newName;
  stats.renames += 1;
  return true;
}

function labelOf(surface) {
  if (!surface || !surface.absoluteBoundingBox) return null;
  const box = surface.absoluteBoundingBox;
  const candidates = surface.parent.children.filter(
    (node) => node.id !== surface.id && node.type === "TEXT" && inside(node, surface),
  );
  const text =
    candidates.find((node) => node.name.startsWith("Text / strong") && node.characters?.trim()) ||
    candidates.find((node) => node.name.startsWith("Text / div / surface-title") && node.characters?.trim()) ||
    candidates.find((node) => Boolean(node.characters && node.characters.trim()));
  return text ? text.characters.trim().slice(0, 20) : null;
}

function normalizeContentNames(nodes, stats) {
  for (const node of nodes) {
    const name = node.name;
    const variant = name.includes("::before") || name.includes("::after");
    const base = name.replace(/^Pseudo \/ /, "").replace(/ ::?(before|after)$/, "");
    let mapped = null;

    if (/^Surface \/ section \/ surface/.test(base)) {
      const title = labelOf(node);
      mapped = title ? `卡片 / ${title}` : "卡片";
    } else if (/^Surface \/ div \/ surface-header/.test(base)) {
      mapped = base.endsWith("/ bottom border") ? "卡片标题栏 / 边线" : "卡片标题栏";
    } else if (/^Text \/ div \/ surface-title/.test(base)) {
      mapped = "卡片标题";
    } else if (/^Text \/ span \/ surface-meta/.test(base)) {
      mapped = "卡片副标题";
    } else if (/^Surface \/ div \/ list-row/.test(base)) {
      if (base.endsWith("/ top border")) {
        mapped = "列表项 / 边线";
      } else {
        const title = labelOf(node);
        mapped = title ? `列表项 / ${title}` : "列表项";
      }
    } else if (/^Surface \/ span \/ row-icon/.test(base)) {
      if (base.includes("is-warning")) mapped = "行图标 / 警示";
      else if (base.includes("is-violet")) mapped = "行图标 / 强调";
      else mapped = "行图标";
    } else if (/^Surface \/ button \/ button/.test(base)) {
      const title = labelOf(node);
      const primary = base.includes(" / primary");
      mapped = `按钮 / ${title || "操作"}${primary ? " / 主按钮" : ""}`;
    } else if (/^Surface \/ button \/ quick-action/.test(base)) {
      const title = labelOf(node);
      mapped = title ? `快捷入口 / ${title}` : "快捷入口";
    } else if (/^Surface \/ div \/ stat/.test(base)) {
      mapped = "统计卡片";
    } else if (/^Text \/ div \/ stat-label/.test(base)) {
      mapped = "统计标签";
    } else if (/^Text \/ div \/ stat-value/.test(base)) {
      mapped = "统计数值";
    } else if (/^Text \/ div \/ stat-note/.test(base)) {
      mapped = "统计说明";
    } else if (/^Surface \/ div \/ notice/.test(base)) {
      mapped = "提示条";
    } else if (/^Text \/ div \/ notice/.test(base)) {
      mapped = "提示条 / 文本";
    } else if (/^Surface \/ input \/ input/.test(base)) {
      mapped = "输入框";
    } else if (/^Surface \/ td/.test(base)) {
      mapped = base.endsWith("/ top border") ? "表格单元格 / 边线" : "表格单元格";
    } else if (/^Surface \/ div \/ progress/.test(base)) {
      mapped = "进度条";
    } else if (/^Surface \/ div \/ toolbar/.test(base)) {
      mapped = "工具栏";
    } else if (/^Surface \/ span \/ check/.test(base)) {
      mapped = "勾选标记";
    } else if (/^Text \/ div \/ breadcrumb/.test(base)) {
      mapped = "面包屑";
    } else if (/^Text \/ h1 \/ page-title/.test(base)) {
      mapped = "页面标题";
    } else if (/^Text \/ p \/ page-lead/.test(base)) {
      mapped = "页面说明";
    } else if (/^Surface \/ div \/ top-divider/.test(base)) {
      mapped = "内容分隔线";
    } else if (/^Text \/ div \/ citation/.test(base)) {
      mapped = "引用说明";
    } else if (/(modal-scrim|scrim|overlay|backdrop|mask)/i.test(base)) {
      mapped = "遮罩 / scrim";
    } else if (/^Surface \/ div \/ modal/.test(base)) {
      mapped = "弹窗";
    }

    if (mapped) rename(node, variant ? `${mapped} / 装饰` : mapped, stats);
  }
}

function makeGroup(frame, members, name, targetIndex) {
  if (members.length < 2) return null;
  const group = figma.group(members, frame);
  group.name = name;
  return group;
}

function normalizeFrame(frame, stats) {
  const snapshot = [...frame.children];
  const debug = { childCount: snapshot.length, firstChildNames: snapshot.slice(0, 3).map((node) => node.name) };

  const appShell = snapshot.find((node) => /^Frame \/ div \/ app-shell/.test(node.name));
  const sidebarRect = snapshot.find((node) => /^Frame \/ aside \/ sidebar/.test(node.name));
  const topbars = snapshot.filter((node) => /^Surface \/ header \/ topbar/.test(node.name));
  const topbarRect = topbars.find((node) => !node.name.endsWith("/ bottom border")) || topbars[0];
  const searchBox = snapshot.find((node) => /^Surface \/ div \/ search-box/.test(node.name));
  debug.appShellFound = Boolean(appShell);
  debug.sidebarFound = Boolean(sidebarRect);
  debug.topbarFound = Boolean(topbarRect);
  debug.searchBoxFound = Boolean(searchBox);

  const sidebarRight = sidebarRect
    ? sidebarRect.absoluteBoundingBox.x + sidebarRect.absoluteBoundingBox.width
    : 240;
  const inSidebarRange = (node) => {
    if (!node.absoluteBoundingBox) return false;
    return center(node).x <= sidebarRight;
  };

  rename(appShell, "总画布", stats);
  rename(sidebarRect, "侧边栏 / 底板", stats);
  for (const topbar of topbars) {
    rename(topbar, topbar.name.endsWith("/ bottom border") ? "顶部栏 / 边线" : "顶部栏 / 底板", stats);
  }

  const navItemGroups = [];
  const consumedNavIds = new Set();
  const navSurfaces = snapshot.filter((node) => /^Surface \/ button \/ nav-item/.test(node.name));
  debug.navSurfaceCount = navSurfaces.length;
  for (const surface of navSurfaces) {
    const parts = surface.name.split(" / ");
    const navLabelText = parts[parts.length - 1];
    const isActive = parts.includes("is-active");
    const icon = snapshot.find(
      (node) => node.type === "FRAME" && node.name === "Icon / svg" && inside(node, surface),
    );
    const text = snapshot.find(
      (node) => node.type === "TEXT" && inside(node, surface) && node.characters?.trim() === navLabelText,
    );
    const members = [surface, icon, text].filter(Boolean);
    members.forEach((node) => consumedNavIds.add(node.id));
    rename(surface, "导航项 / 底板", stats);
    if (icon) rename(icon, "图标", stats);
    if (text) rename(text, "导航项 / 文本", stats);
    const group = makeGroup(frame, members, `导航项 / ${navLabelText}${isActive ? " / 激活" : ""}`);
    if (group) {
      navItemGroups.push(group);
      stats.groups += 1;
    }
  }

  const navLabel = snapshot.find((node) => /^Text \/ div \/ nav-section-label/.test(node.name));
  rename(navLabel, "分区标题", stats);
  const pairNavItems = snapshot
    .filter((node) => node.type === "TEXT" && inSidebarRange(node) && /^Text \/ span/.test(node.name) && !consumedNavIds.has(node.id))
    .slice()
    .sort((first, second) => (center(first)?.y || 0) - (center(second)?.y || 0));
  for (const text of pairNavItems) {
    if (consumedNavIds.has(text.id)) continue;
    const point = center(text);
    if (!point) continue;
    const icon = snapshot.find(
      (node) =>
        node.type === "FRAME" &&
        node.name === "Icon / svg" &&
        !consumedNavIds.has(node.id) &&
        center(node) &&
        Math.abs(center(node).y - point.y) <= 4 &&
        center(node).x < point.x,
    );
    if (!icon) continue;
    const label = text.characters?.trim().slice(0, 20) || "导航项";
    consumedNavIds.add(text.id);
    consumedNavIds.add(icon.id);
    rename(icon, "图标", stats);
    rename(text, "导航项 / 文本", stats);
    const group = makeGroup(frame, [icon, text], `导航项 / ${label}`);
    if (group) {
      navItemGroups.push(group);
      stats.groups += 1;
    }
  }

  const brandMark = snapshot.find((node) => node.name === "Surface / span / brand-mark");
  const brandImg = snapshot.find((node) => node.name === "Image / img");
  const brandStrong = snapshot.find((node) => node.name === "Text / strong / 校园智能文档助手");
  const brandSub = snapshot.find((node) => node.name === "Text / span / EduDocAssistant");
  rename(brandMark, "品牌标识 / 底板", stats);
  rename(brandImg, "Logo", stats);
  rename(brandStrong, "品牌名称", stats);
  rename(brandSub, "品牌副名", stats);
  const brandGroup = makeGroup(frame, [brandMark, brandImg, brandStrong, brandSub].filter(Boolean), "品牌标识");
  if (brandGroup) stats.groups += 1;

  const navMenuMembers = [navLabel, ...navItemGroups].filter(Boolean);
  const navMenuGroup = makeGroup(frame, navMenuMembers, "导航菜单");
  if (navMenuGroup) stats.groups += 1;

  const sidebarFooter = snapshot.find((node) => /^Frame \/ div \/ sidebar-footer/.test(node.name));
  rename(sidebarFooter, "账户信息 / 底板", stats);
  const footerMembers = [sidebarFooter].filter(Boolean);
  if (sidebarFooter) {
    const footerAvatarSurface = snapshot.find(
      (node) => /^Surface \/ span \/ avatar/.test(node.name) && inside(node, sidebarFooter),
    );
    const footerAvatarText = snapshot.find(
      (node) => /^Text \/ span \/ avatar/.test(node.name) && inside(node, sidebarFooter),
    );
    const footerName = snapshot.find(
      (node) => node.type === "TEXT" && node.name.startsWith("Text / strong") && inside(node, sidebarFooter),
    );
    const footerRole = snapshot.find(
      (node) =>
        node.type === "TEXT" &&
        node.name.startsWith("Text / span") &&
        !/avatar/.test(node.name) &&
        inside(node, sidebarFooter),
    );
    rename(footerAvatarSurface, "头像 / 底板", stats);
    rename(footerAvatarText, "头像 / 文字", stats);
    footerMembers.push(footerAvatarSurface, footerAvatarText, footerName, footerRole);
  }
  const accountGroup = makeGroup(frame, footerMembers.filter(Boolean), "账户信息");
  if (accountGroup) stats.groups += 1;

  const sidebarRule = snapshot.find((node) => node.name === "Surface / div / sidebar-rule");
  rename(sidebarRule, "侧边栏分隔线", stats);

  const sidebarGroupMembers = frame.children.filter((node) => {
    if (node.id === appShell?.id || node.id === topbarRect?.id) return false;
    return inSidebarRange(node);
  });
  const sidebarGroup = makeGroup(frame, sidebarGroupMembers, "侧边栏");
  if (sidebarGroup) stats.groups += 1;

  const topbarBox = topbarRect?.absoluteBoundingBox;
  const topbarMembers = topbarBox
    ? frame.children.filter((node) => {
        if (node.id === appShell?.id || node.id === sidebarGroup?.id) return false;
        if (!node.absoluteBoundingBox) return false;
        const point = center(node);
        return point.y >= topbarBox.y - 2 && point.y <= topbarBox.y + topbarBox.height + 2;
      })
    : [];

  if (searchBox) {
    rename(searchBox, "搜索框 / 底板", stats);
    const searchIcon = snapshot.find(
      (node) => node.type === "FRAME" && node.name === "Icon / svg" && inside(node, searchBox),
    );
    const searchText = snapshot.find(
      (node) => node.type === "TEXT" && /^Text \/ span/.test(node.name) && inside(node, searchBox),
    );
    const searchKbd = snapshot.find((node) => node.name === "Text / kbd / Ctrl K");
    rename(searchIcon, "图标", stats);
    rename(searchText, "搜索提示", stats);
    rename(searchKbd, "快捷键徽标", stats);
    const searchMembers = [searchBox, searchIcon, searchText, searchKbd].filter(Boolean);
    const searchGroup = makeGroup(frame, searchMembers, "搜索框");
    if (searchGroup) {
      stats.groups += 1;
      const searchMemberIds = new Set(searchMembers.map((node) => node.id));
      const filtered = topbarMembers.filter((node) => !searchMemberIds.has(node.id));
      topbarMembers.length = 0;
      topbarMembers.push(...filtered, searchGroup);
    }
  }

  for (const node of topbarMembers) {
    if (node.type === "TEXT" && node.name === "Text / kbd / Ctrl K") rename(node, "快捷键徽标", stats);
    if (/^Surface \/ span \/ avatar/.test(node.name)) rename(node, "头像 / 底板", stats);
    if (/^Text \/ span \/ avatar/.test(node.name)) rename(node, "头像 / 文字", stats);
    if (/^Surface \/ span \/ role-chip/.test(node.name)) rename(node, "角色标签 / 底板", stats);
    if (/^Text \/ span \/ role-chip/.test(node.name)) rename(node, "角色标签 / 文本", stats);
  }
  const topbarGroup = makeGroup(frame, topbarMembers, "顶部栏");
  if (topbarGroup) stats.groups += 1;

  debug.frameName = frame.name;
  debug.functionMarker = "NF-V2";
  stats.debugLog = stats.debugLog || [];
  stats.debugLog.push(debug);

  const reservedIds = new Set(
    [appShell, sidebarGroup, topbarGroup].filter(Boolean).map((node) => node.id),
  );
  const contentMembers = frame.children.filter((node) => !reservedIds.has(node.id));
  normalizeContentNames(contentMembers, stats);
  const contentGroup = makeGroup(frame, contentMembers, "主内容");
  if (contentGroup) stats.groups += 1;

  const zoneMembers = [topbarGroup, contentGroup].filter(Boolean);
  const zone = makeGroup(frame, zoneMembers, "内容区");
  if (zone) stats.groups += 1;

  const topNodes = [appShell, sidebarGroup, zone || contentGroup].filter(Boolean);
  for (const topNode of topNodes) {
    frame.appendChild(topNode);
  }
}

async function normalizeAllScreens(task) {
  const stats = { renames: 0, groups: 0, screens: 0, skippedScreens: [], screenLog: [] };
  for (const page of figma.root.children) {
    await figma.setCurrentPageAsync(page);
    const screens = page.children.filter(isScreenFrame);
    for (const screen of screens) {
      const before = { renames: stats.renames, groups: stats.groups };
      try {
        normalizeFrame(screen, stats);
        stats.screens += 1;
        stats.screenLog.push({
          page: page.name,
          frame: screen.name,
          id: screen.id,
          renames: stats.renames - before.renames,
          groups: stats.groups - before.groups,
        });
      } catch (error) {
        stats.skippedScreens.push({ page: page.name, frame: screen.name, error: String(error) });
      }
    }
  }
  return stats;
}

async function exportFrames(task) {
  const outputs = [];
  for (const entry of task.frames) {
    const page = figma.root.children.find((candidate) => candidate.name === entry.page);
    if (!page) continue;
    await figma.setCurrentPageAsync(page);
    const frame = page.children.find((node) => node.name === entry.frame);
    if (!frame) continue;
    const bytes = await frame.exportAsync({
      format: "PNG",
      constraint: { type: "SCALE", value: Number(entry.scale || 0.5) },
    });
    let binary = "";
    const chunkSize = 0x8000;
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
    }
    await postJson(`${BASE_URL}/snapshot`, {
      name: `${task.prefix || "export"}-${entry.key}`,
      base64: btoa(binary),
    });
    outputs.push({ key: entry.key, page: page.name, frame: frame.name, bytes: bytes.length });
  }
  return outputs;
}

async function run() {
  showStatus("CIDAS Normalize running\n\nReading task from bridge...");
  const task = await getJson(`${BASE_URL}/task`);
  if (task.mode === "normalize") {
    showStatus(`Task mode: normalize\n\nNormalizing route screens...`);
    const stats = await normalizeAllScreens(task);
    let version = null;
    if (task.saveVersion !== false) {
      version = await figma.saveVersionHistoryAsync(
        task.versionTitle || "Normalize route screen structure",
        task.versionDescription || "Applied the login-screen grouping and naming conventions to all route screens.",
      );
    }
    const exports = task.frames?.length ? await exportFrames(task) : [];
    await postJson(`${BASE_URL}/report`, {
      generatedAt: new Date().toISOString(),
      mode: "normalize",
      codeMarker: CODE_MARKER,
      ...stats,
      version,
      exports,
    });
    showStatus(`Normalize done\n\nScreens: ${stats.screens}\nRenames: ${stats.renames}\nGroups: ${stats.groups}\nExports: ${exports.length}`);
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 5000));
    figma.closePlugin(`Normalized ${stats.screens} screens`);
    return;
  }
  if (task.mode === "save-version") {
    showStatus("Task mode: save-version\n\nSaving a named version...");
    const version = await figma.saveVersionHistoryAsync(
      task.versionTitle || "Normalize route screen structure",
      task.versionDescription || "Applied the login-screen grouping and naming conventions to all route screens.",
    );
    await postJson(`${BASE_URL}/report`, {
      generatedAt: new Date().toISOString(),
      mode: "save-version",
      codeMarker: CODE_MARKER,
      version,
    });
    showStatus(`Version saved\n\n${version || "no id returned"}`);
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 3000));
    figma.closePlugin("Version saved");
    return;
  }
  if (task.mode === "export") {
    showStatus("Task mode: export\n\nExporting sample screens...");
    const outputs = await exportFrames(task);
    await postJson(`${BASE_URL}/report`, {
      generatedAt: new Date().toISOString(),
      mode: "export",
      codeMarker: CODE_MARKER,
      outputs,
    });
    showStatus(`Exported ${outputs.length} frames`);
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 3000));
    figma.closePlugin(`Exported ${outputs.length} frames`);
    return;
  }
  if (task.mode === "inspect-frames") {
    const output = [];
    const summarize = (node, depth) => {
      const entry = {
        id: node.id,
        name: node.name,
        type: node.type,
        x: Math.round(node.x),
        y: Math.round(node.y),
        w: Math.round(node.width),
        h: Math.round(node.height),
      };
      if (depth > 0 && "children" in node) {
        entry.children = node.children.map((child) => summarize(child, depth - 1));
      }
      return entry;
    };
    for (const entry of task.frames) {
      const page = figma.root.children.find((candidate) => candidate.name === entry.page);
      if (!page) continue;
      await figma.setCurrentPageAsync(page);
      const frame = page.children.find((node) => node.name === entry.frame);
      if (!frame) continue;
      output.push({
        frame: frame.name,
        id: frame.id,
        topLevel: frame.children.map((node) => summarize(node, 3)),
      });
    }
    await postJson(`${BASE_URL}/report`, {
      generatedAt: new Date().toISOString(),
      mode: "inspect-frames",
      output,
    });
    figma.closePlugin("inspect done");
    return;
  }
  if (task.mode === "apply") {
    showStatus(`Task mode: apply\n\nOperations: ${(task.operations || []).length}`);
    const outcome = await applyTask(task);
    await postJson(`${BASE_URL}/report`, {
      generatedAt: new Date().toISOString(),
      mode: "apply",
      ...outcome,
    });
    showStatus(`Applied ${outcome.applied} renames\n\nReport posted to bridge.`);
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 4000));
    figma.closePlugin(`Applied ${outcome.applied} renames`);
    return;
  }

  showStatus("Task mode: dump\n\nCollecting pages and layer trees...");
  const dump = await collectDump();
  await postJson(`${BASE_URL}/report`, {
    generatedAt: new Date().toISOString(),
    mode: "dump",
    codeMarker: CODE_MARKER,
    ...dump,
  });
  showStatus(`Dump saved\n\nPages: ${dump.pages.length}\nReport posted to bridge.`);
  await new Promise((resolvePromise) => setTimeout(resolvePromise, 4000));
  figma.closePlugin("Structure dump saved");
}

run()
  .then(() => undefined)
  .catch(async (error) => {
    showStatus(`FAILED\n\n${String(error)}\n\n${String(error?.stack || "")}`);
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 15000));
    try {
      await postJson(`${BASE_URL}/report`, {
        generatedAt: new Date().toISOString(),
        fatalError: String(error),
        stack: error?.stack,
      });
    } finally {
      figma.closePlugin(String(error));
    }
  });
