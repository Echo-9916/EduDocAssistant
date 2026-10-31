const BASE_URL = "http://localhost:4180";
const REPORT_URL = `${BASE_URL}/__native`;
const ROLE_CONFIG_URL = `${BASE_URL}/native/current-role.json`;
const ROLE_PAGES = {
  student: "校园智能文档助手 · 登录及学生端路线",
  teacher: "[V3] 教师端路线",
  operations: "[V3] 运维端路线",
};
const ROLE_LABELS = {
  student: "学生路线 v3",
  teacher: "教师端 v3",
  operations: "运维端 v3",
};
const OVERVIEW_NAMES = {
  student: "学生路线 v3 · 27 屏总览",
  teacher: "教师端 v3 / 27 屏总览",
  operations: "运维端 v3 / 27 屏总览",
};
const ROLE_ACCENTS = {
  student: "#2563EB",
  teacher: "#0F766E",
  operations: "#0E7490",
};
const ROLE_NAV = {
  student: "#1F3864",
  teacher: "#193C3B",
  operations: "#18344B",
};

let availableFonts = [];
const loadedFonts = new Map();
const imageHashes = new Map();
let activePage = null;

function colorFromValue(color, fallback = "#000000") {
  const value = color || fallback;
  const hex = value.replace("#", "");
  return {
    r: Number.parseInt(hex.slice(0, 2), 16) / 255,
    g: Number.parseInt(hex.slice(2, 4), 16) / 255,
    b: Number.parseInt(hex.slice(4, 6), 16) / 255,
  };
}

function paintFromColor(color) {
  if (!color) return null;
  return {
    type: "SOLID",
    color: { r: color.r, g: color.g, b: color.b },
    opacity: color.a ?? 1,
  };
}

function applyRadius(node, radius) {
  if (Array.isArray(radius)) {
    node.topLeftRadius = radius[0] || 0;
    node.topRightRadius = radius[1] || 0;
    node.bottomRightRadius = radius[2] || 0;
    node.bottomLeftRadius = radius[3] || 0;
  } else {
    node.cornerRadius = radius || 0;
  }
}

function getFontCandidates(family, weight) {
  const families = [
    family,
    "Microsoft YaHei",
    "Noto Sans SC",
    "Noto Sans CJK SC",
    "PingFang SC",
    "Arial",
    "Inter",
  ].filter(Boolean);
  for (const candidate of families) {
    const fonts = availableFonts.filter(
      (font) => font.fontName.family.toLowerCase() === candidate.toLowerCase(),
    );
    if (fonts.length === 0) continue;
    const preferred = weight >= 600 ? /bold|semibold|semi bold|medium/i : /regular|normal/i;
    return fonts.find((font) => preferred.test(font.fontName.style)) || fonts[0];
  }
  return availableFonts[0];
}

async function fontFor(family, weight) {
  const key = `${family}|${weight}`;
  if (loadedFonts.has(key)) return loadedFonts.get(key);
  const font = getFontCandidates(family, weight);
  if (!font) throw new Error("No usable font is available");
  await figma.loadFontAsync(font.fontName);
  loadedFonts.set(key, font.fontName);
  return font.fontName;
}

function appendToActive(node, x, y, name, opacity = 1) {
  activePage.appendChild(node);
  node.x = x;
  node.y = y;
  node.name = name;
  node.opacity = Math.max(0, Math.min(1, opacity ?? 1));
  return node;
}

async function addImageFromUrl(frame, layer) {
  let hash = imageHashes.get(layer.src);
  if (!hash) {
    const resourcePath = layer.src.replace(/^https?:\/\/[^/]+/i, "");
    const response = await fetch(`${BASE_URL}${resourcePath}`);
    if (!response.ok) throw new Error(`Image request failed: ${response.status}`);
    const image = figma.createImage(new Uint8Array(await response.arrayBuffer()));
    hash = image.hash;
    imageHashes.set(layer.src, hash);
  }
  const node = figma.createRectangle();
  node.resize(Math.max(1, layer.width), Math.max(1, layer.height));
  node.fills = [{ type: "IMAGE", imageHash: hash, scaleMode: "FILL" }];
  applyRadius(node, layer.radius);
  appendToActive(node, layer.x, layer.y, layer.name, layer.opacity);
}

function addSideBorder(frame, layer, side, width) {
  if (!layer.borderColor || width <= 0) return;
  const border = figma.createRectangle();
  const isVertical = side === "left" || side === "right";
  border.resize(
    isVertical ? width : Math.max(1, layer.width),
    isVertical ? Math.max(1, layer.height) : width,
  );
  border.fills = [paintFromColor(layer.borderColor)];
  const x =
    side === "right"
      ? layer.x + layer.width - width
      : layer.x;
  const y =
    side === "bottom"
      ? layer.y + layer.height - width
      : layer.y;
  appendToActive(border, x, y, `${layer.name} / ${side} border`, 1);
}

function addRect(frame, layer) {
  const node = figma.createRectangle();
  node.resize(Math.max(1, layer.width), Math.max(1, layer.height));
  const fill = paintFromColor(layer.fill);
  node.fills = fill ? [fill] : [];
  applyRadius(node, layer.radius);
  const widths = layer.borderWidths || [];
  const allBorders = widths.length === 4 && widths.every((width) => width > 0);
  if (allBorders && layer.borderColor) {
    node.strokes = [paintFromColor(layer.borderColor)];
    node.strokeWeight = Math.max(...widths);
    node.strokeAlign = "INSIDE";
  }
  if (layer.effect) {
    node.effects = [
      {
        ...layer.effect,
        color: layer.effect.color,
      },
    ];
  }
  appendToActive(node, layer.x, layer.y, layer.name, layer.opacity);
  if (!allBorders && widths.some((width) => width > 0)) {
    addSideBorder(frame, layer, "top", widths[0]);
    addSideBorder(frame, layer, "right", widths[1]);
    addSideBorder(frame, layer, "bottom", widths[2]);
    addSideBorder(frame, layer, "left", widths[3]);
  }
}

async function addText(frame, layer) {
  const node = figma.createText();
  const fontName = await fontFor(layer.style.fontFamily, layer.style.fontWeight);
  node.fontName = fontName;
  node.fontSize = layer.style.fontSize;
  node.lineHeight = layer.style.lineHeight;
  node.letterSpacing = { unit: "PIXELS", value: layer.style.letterSpacing };
  node.textAlignHorizontal =
    layer.style.textAlign === "justify"
      ? "JUSTIFIED"
      : layer.style.textAlign.toUpperCase();
  node.fills = [paintFromColor(layer.style.color)];
  node.textAutoResize = "HEIGHT";
  node.characters = layer.characters;
  node.resize(Math.max(1, layer.width + 1), Math.max(1, layer.height));
  appendToActive(node, layer.x, layer.y, layer.name, layer.opacity);
}

function addSvg(frame, layer) {
  try {
    const node = figma.createNodeFromSvg(layer.svg);
    node.name = layer.name;
    node.resize(Math.max(1, layer.width), Math.max(1, layer.height));
    activePage.appendChild(node);
    node.x = layer.x;
    node.y = layer.y;
    node.opacity = layer.opacity ?? 1;
    return true;
  } catch (error) {
    const fallback = figma.createRectangle();
    fallback.name = `${layer.name} / vector fallback`;
    fallback.resize(Math.max(1, layer.width), Math.max(1, layer.height));
    fallback.fills = [{ type: "SOLID", color: { r: 0.45, g: 0.5, b: 0.58 } }];
    appendToActive(fallback, layer.x, layer.y, fallback.name, layer.opacity);
    return false;
  }
}

async function addOverview(page, roleKey, scenes) {
  const label = ROLE_LABELS[roleKey];
  const accent = ROLE_ACCENTS[roleKey];
  const name = OVERVIEW_NAMES[roleKey];
  let frame = page.children.find((node) => node.name === name);
  if (!frame) {
    frame = figma.createFrame();
    page.appendChild(frame);
  }
  for (const child of [...frame.children]) child.remove();
  frame.name = name;
  frame.resize(1600, 2227);
  frame.x = 0;
  frame.y = 0;
  frame.fills = [paintFromColor({ ...colorFromValue("#F4F7FB"), a: 1 })];
  frame.clipsContent = true;
  activePage = frame;

  const header = figma.createRectangle();
  header.resize(1600, 260);
  header.fills = [paintFromColor({ ...colorFromValue(ROLE_NAV[roleKey]), a: 1 })];
  frame.appendChild(header);
  header.x = 0;
  header.y = 0;
  header.name = "Page / Header";

  const title = await fontFor("Microsoft YaHei", 700);
  const regular = await fontFor("Microsoft YaHei", 400);
  const heading = figma.createText();
  heading.fontName = title;
  heading.fontSize = 40;
  heading.lineHeight = { unit: "PIXELS", value: 52 };
  heading.fills = [paintFromColor({ ...colorFromValue("#FFFFFF"), a: 1 })];
  heading.characters = `${label} · 27 屏`;
  frame.appendChild(heading);
  heading.x = 48;
  heading.y = 54;
  heading.name = "Title";

  const subtitle = figma.createText();
  subtitle.fontName = regular;
  subtitle.fontSize = 17;
  subtitle.lineHeight = { unit: "PIXELS", value: 28 };
  subtitle.fills = [
    paintFromColor({ r: 0.88, g: 0.93, b: 0.96, a: 1 }),
  ];
  subtitle.characters = "完整原生图层 · 页面内交互状态 · 页面之间不设置跳转";
  frame.appendChild(subtitle);
  subtitle.x = 50;
  subtitle.y = 126;
  subtitle.name = "Description";

  const badge = figma.createText();
  badge.fontName = title;
  badge.fontSize = 14;
  badge.lineHeight = { unit: "PIXELS", value: 22 };
  badge.fills = [paintFromColor({ ...colorFromValue("#FFFFFF"), a: 1 })];
  badge.characters = "27 / 27 SCREENS";
  frame.appendChild(badge);
  badge.x = 1395;
  badge.y = 94;
  badge.name = "Screen count";

  const cardWidth = 360;
  const cardHeight = 220;
  const gapX = 22;
  const gapY = 24;
  const startX = 44;
  const startY = 306;
  for (let index = 0; index < scenes.length; index += 1) {
    const scene = scenes[index];
    const column = index % 4;
    const row = Math.floor(index / 4);
    const card = figma.createFrame();
    card.name = `${String(index + 1).padStart(2, "0")} ${scene.title}`;
    card.resize(cardWidth, cardHeight);
    card.x = startX + column * (cardWidth + gapX);
    card.y = startY + row * (cardHeight + gapY);
    card.fills = [paintFromColor({ ...colorFromValue("#FFFFFF"), a: 1 })];
    card.strokes = [paintFromColor({ ...colorFromValue("#DCE5EF"), a: 1 })];
    card.strokeWeight = 1;
    card.cornerRadius = 8;
    card.clipsContent = true;
    frame.appendChild(card);

    const strip = figma.createRectangle();
    strip.resize(cardWidth, 6);
    strip.fills = [paintFromColor({ ...colorFromValue(accent), a: 1 })];
    card.appendChild(strip);
    strip.x = 0;
    strip.y = 0;
    strip.name = "Accent";

    const number = figma.createText();
    number.fontName = title;
    number.fontSize = 14;
    number.lineHeight = { unit: "PIXELS", value: 22 };
    number.fills = [paintFromColor({ ...colorFromValue(accent), a: 1 })];
    number.characters = String(index + 1).padStart(2, "0");
    card.appendChild(number);
    number.x = 20;
    number.y = 24;
    number.name = "Step";

    const group = figma.createText();
    group.fontName = regular;
    group.fontSize = 12;
    group.lineHeight = { unit: "PIXELS", value: 18 };
    group.fills = [paintFromColor({ ...colorFromValue("#5B6B80"), a: 1 })];
    group.characters = scene.group;
    card.appendChild(group);
    group.x = 68;
    group.y = 26;
    group.name = "Group";

    const cardTitle = figma.createText();
    cardTitle.fontName = title;
    cardTitle.fontSize = 20;
    cardTitle.lineHeight = { unit: "PIXELS", value: 30 };
    cardTitle.fills = [paintFromColor({ ...colorFromValue("#172033"), a: 1 })];
    cardTitle.characters = scene.title;
    cardTitle.textAutoResize = "HEIGHT";
    cardTitle.resize(316, 64);
    card.appendChild(cardTitle);
    cardTitle.x = 20;
    cardTitle.y = 72;
    cardTitle.name = "Title";

    const meta = figma.createText();
    meta.fontName = regular;
    meta.fontSize = 12;
    meta.lineHeight = { unit: "PIXELS", value: 19 };
    meta.fills = [paintFromColor({ ...colorFromValue("#64748B"), a: 1 })];
    meta.characters = `完整页面 · 原生文本与图形 · ${scene.layers.length} 个可编辑图层`;
    card.appendChild(meta);
    meta.x = 20;
    meta.y = 166;
    meta.name = "Metadata";
  }
  return frame;
}

async function buildScene(page, roleKey, scene, x, y) {
  const label = ROLE_LABELS[roleKey];
  const frameName = `${label} / ${String(scene.step).padStart(2, "0")} ${scene.title}`;
  let frame = page.children.find((node) => node.name === frameName);
  if (!frame) {
    frame = figma.createFrame();
    page.appendChild(frame);
  }
  for (const child of [...frame.children]) child.remove();
  frame.name = frameName;
  frame.resize(scene.width, scene.height);
  frame.x = x;
  frame.y = y;
  frame.fills = [paintFromColor(scene.background)];
  frame.clipsContent = true;
  if ("layoutMode" in frame && frame.layoutMode !== "NONE") {
    try {
      frame.layoutMode = "NONE";
    } catch (error) {
      // Reused frames may reject layout resets; manual coordinates still apply.
    }
  }
  activePage = frame;

  let textCount = 0;
  let rectCount = 0;
  let iconCount = 0;
  let imageCount = 0;
  const errors = [];
  for (const layer of scene.layers) {
    try {
      if (layer.kind === "rect") {
        addRect(frame, layer);
        rectCount += 1;
      } else if (layer.kind === "text") {
        await addText(frame, layer);
        textCount += 1;
      } else if (layer.kind === "svg") {
        if (addSvg(frame, layer)) {
          iconCount += 1;
        } else {
          errors.push({
            layer: layer.name,
            kind: layer.kind,
            error: "SVG import failed and was rendered as a fallback rectangle",
          });
        }
      } else if (layer.kind === "image") {
        await addImageFromUrl(frame, layer);
        imageCount += 1;
      }
    } catch (error) {
      errors.push({ layer: layer.name, kind: layer.kind, error: String(error) });
    }
  }
  return {
    step: scene.step,
    id: frame.id,
    name: frame.name,
    layerCount: frame.children.length,
    rectCount,
    textCount,
    iconCount,
    imageCount,
    errors,
  };
}

async function postReport(report) {
  const response = await fetch(REPORT_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(report),
  });
  if (!response.ok) throw new Error(`Native import report failed: ${response.status}`);
}

function bytesToBase64(bytes) {
  let binary = "";
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
  }
  return btoa(binary);
}

async function postSnapshot(node, name, scale) {
  const bytes = await node.exportAsync({
    format: "PNG",
    constraint: { type: "SCALE", value: scale },
  });
  const response = await fetch(`${BASE_URL}/__snapshot`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, base64: bytesToBase64(bytes) }),
  });
  if (!response.ok) throw new Error(`Native snapshot failed: ${response.status}`);
  return { name, bytes: bytes.length };
}

async function setUrlReaction(nodeId, url) {
  const node = await figma.getNodeByIdAsync(nodeId);
  if (!node || !("setReactionsAsync" in node)) return false;
  const trigger = node.reactions[0]?.trigger || { type: "ON_CLICK" };
  await node.setReactionsAsync([
    {
      trigger,
      actions: [{ type: "URL", url, openInNewTab: true }],
    },
  ]);
  return true;
}

async function run() {
  const configResponse = await fetch(ROLE_CONFIG_URL, { cache: "no-store" });
  if (!configResponse.ok) throw new Error(`Role config failed: ${configResponse.status}`);
  const config = await configResponse.json();
  const roleKey = config.role;
  await postReport({
    generatedAt: new Date().toISOString(),
    role: roleKey,
    stage: "config-loaded",
    config,
  });
  const pageName = ROLE_PAGES[roleKey];
  if (!pageName) throw new Error(`Unsupported role: ${roleKey}`);
  const sceneResponse = await fetch(`${BASE_URL}/native/${roleKey}.json`, {
    cache: "no-store",
  });
  if (!sceneResponse.ok) throw new Error(`Scene data failed: ${sceneResponse.status}`);
  const source = await sceneResponse.json();
  const scenes = source.scenes;

  const page = figma.root.children.find((candidate) => candidate.name === pageName);
  if (!page) throw new Error(`Page not found: ${pageName}`);
  await figma.setCurrentPageAsync(page);
  availableFonts = await figma.listAvailableFontsAsync();
  let cleanedTopLevelArtifacts = 0;
  if (roleKey === "teacher" || roleKey === "operations") {
    const incidentalNodes = page.children.filter(
      (node) =>
        (node.type === "TEXT" || node.type === "LINE") &&
        !node.name?.startsWith(`${ROLE_LABELS[roleKey]} /`),
    );
    for (const node of incidentalNodes) node.remove();
    cleanedTopLevelArtifacts = incidentalNodes.length;
  }
  await postReport({
    generatedAt: new Date().toISOString(),
    role: roleKey,
    stage: "page-loaded",
    pageId: page.id,
    pageName: page.name,
    frameCount: page.children.length,
  });

  const overview =
    config.includeOverview === false
      ? page.children.find((node) => node.name === OVERVIEW_NAMES[roleKey])
      : await addOverview(page, roleKey, source.scenes);
  await postReport({
    generatedAt: new Date().toISOString(),
    role: roleKey,
    stage: "overview-ready",
    pageId: page.id,
    overviewId: overview?.id,
  });
  const offset = Number(config.offset || 0);
  const limit = Number(config.limit || scenes.length);
  const batchScenes = scenes.slice(offset, offset + limit);
  const screenResults = [];
  for (let index = 0; index < batchScenes.length; index += 1) {
    const scene = batchScenes[index];
    const sceneIndex = scene.step - 1;
    const column = sceneIndex % 5;
    const row = Math.floor(sceneIndex / 5);
    screenResults.push(
      await buildScene(page, roleKey, scene, column * 1520, 2380 + row * 980),
    );
  }
  await postReport({
    generatedAt: new Date().toISOString(),
    role: roleKey,
    stage: "screens-ready",
    pageId: page.id,
    offset,
    sceneCount: screenResults.length,
  });

  const remains = await figma.getNodeByIdAsync(overview.id);
  if (remains) figma.currentPage.selection = [remains];
  const report = {
    generatedAt: new Date().toISOString(),
    role: roleKey,
    pageId: page.id,
    pageName: page.name,
    offset,
    sceneCount: screenResults.length,
    cleanedTopLevelArtifacts,
    overview,
    screens: screenResults,
    snapshots: [],
    totals: {
      text: screenResults.reduce((sum, item) => sum + item.textCount, 0),
      rectangles: screenResults.reduce((sum, item) => sum + item.rectCount, 0),
      icons: screenResults.reduce((sum, item) => sum + item.iconCount, 0),
      images: screenResults.reduce((sum, item) => sum + item.imageCount, 0),
      errors: screenResults.reduce((sum, item) => sum + item.errors.length, 0),
    },
  };
  report.version = await figma.saveVersionHistoryAsync(
    `Native ${roleKey} route import`,
    `Replaced screenshot frames with editable Figma text, shapes and vector icons for ${screenResults.length} screens.`,
  );
  await postReport(report);
  if (config.capture) {
    if (overview) {
      report.snapshots.push(
        await postSnapshot(overview, `native-${roleKey}-overview.png`, 0.5),
      );
    }
    for (const result of screenResults) {
      const frame = await figma.getNodeByIdAsync(result.id);
      if (frame) {
        report.snapshots.push(
          await postSnapshot(
            frame,
            `native-${roleKey}-${String(result.step).padStart(2, "0")}.png`,
            1,
          ),
        );
      }
    }
    await postReport(report);
  }
}

run()
  .then(() => figma.closePlugin("Native route import complete"))
  .catch(async (error) => {
    try {
      await postReport({
        generatedAt: new Date().toISOString(),
        role: "unknown",
        fatalError: String(error),
        stack: error?.stack,
      });
    } finally {
      figma.closePlugin(String(error));
    }
  });
