/* ==========================================================================
   EduDocAssistant · 桌面端原型交互脚本
   负责屏幕切换、缩放适配、页内标签切换与键盘浏览
   ========================================================================== */

(function () {
  'use strict';

  const APP_W = 1440;
  const APP_H = 900;

  const screens = Array.from(document.querySelectorAll('.screen'));
  const navItems = Array.from(document.querySelectorAll('.nav-item[data-screen]'));
  const stage = document.querySelector('.stage-scroll');
  const fitBox = document.querySelector('.stage-fit');
  const scaleBox = document.querySelector('.scale-box');
  const nameEl = document.querySelector('.stage-name');
  const descEl = document.querySelector('.stage-desc');
  const appNavItems = Array.from(document.querySelectorAll('.app-nav .nav-item[data-nav]'));

  let zoomMode = 'fit';
  const onShow = [];

  function screenById(id) {
    return screens.find((s) => s.dataset.screen === id) || screens[0];
  }

  function show(id, push) {
    const target = screenById(id);
    screens.forEach((s) => s.classList.toggle('is-active', s === target));
    navItems.forEach((n) => n.classList.toggle('is-active', n.dataset.screen === target.dataset.screen));
    appNavItems.forEach((n) => n.classList.toggle('is-active', n.dataset.nav === target.dataset.nav));
    nameEl.textContent = target.dataset.title || target.dataset.screen;
    descEl.textContent = target.dataset.desc || '';
    if (push && location.hash.slice(1) !== target.dataset.screen) {
      location.hash = target.dataset.screen;
    }
    stage.scrollTop = 0;
    onShow.forEach((fn) => fn());
  }

  function layout() {
    const availW = stage.clientWidth - 40;
    const availH = stage.clientHeight - 40;
    const fit = Math.min(availW / APP_W, availH / APP_H, 1);
    const scale = zoomMode === 'fit' ? fit : 1;
    scaleBox.style.transform = 'scale(' + scale + ')';
    fitBox.style.width = APP_W * scale + 'px';
    fitBox.style.height = APP_H * scale + 'px';
  }

  navItems.forEach((item) => {
    item.addEventListener('click', () => show(item.dataset.screen, true));
  });

  document.querySelectorAll('[data-goto]').forEach((el) => {
    el.addEventListener('click', () => show(el.dataset.goto, true));
  });

  document.querySelectorAll('[data-zoom]').forEach((el) => {
    el.addEventListener('click', () => {
      zoomMode = el.dataset.zoom;
      document.querySelectorAll('[data-zoom]').forEach((b) => b.classList.toggle('btn-primary', b === el));
      layout();
    });
  });

  document.querySelectorAll('[data-tab]').forEach((tab) => {
    tab.addEventListener('click', () => {
      const group = tab.closest('.tabs');
      const scope = group.parentElement;
      group.querySelectorAll('[data-tab]').forEach((t) => t.classList.toggle('is-active', t === tab));
      scope.querySelectorAll('[data-tabpanel]').forEach((p) => {
        p.classList.toggle('is-active', p.dataset.tabpanel === tab.dataset.tab);
      });
    });
  });

  document.addEventListener('keydown', (e) => {
    if (e.target.closest('input, textarea, select')) return;
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
    const current = screens.findIndex((s) => s.classList.contains('is-active'));
    const next = e.key === 'ArrowRight' ? current + 1 : current - 1;
    if (next >= 0 && next < screens.length) {
      e.preventDefault();
      show(screens[next].dataset.screen, true);
    }
  });

  window.addEventListener('hashchange', () => show(location.hash.slice(1), false));
  window.addEventListener('resize', layout);

  /* 文档预览：章节结构可向左侧收缩，正文区自动填充 */
  document.querySelectorAll('[data-outline-toggle]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const screen = btn.closest('.screen');
      const outline = screen && screen.querySelector('.outline');
      if (!outline) return;
      const collapsed = outline.classList.toggle('is-collapsed');
      screen.querySelectorAll('[data-outline-toggle]').forEach((b) => {
        b.textContent = collapsed ? '›' : '‹';
        b.setAttribute('aria-label', collapsed ? '展开目录' : '收起目录');
        b.setAttribute('title', collapsed ? '展开目录' : '收起目录');
      });
    });
  });

  const params = new URLSearchParams(location.search);
  if (params.get('solo') === '1') {
    document.querySelector('.panel').style.display = 'none';
    document.querySelector('.stage-bar').style.display = 'none';
  }

  /* ======================================================================
     框选批注：拖拽框选页面区域并填写意见，可导出 Markdown 供逐条修改
     数据保存在浏览器本地（localStorage），刷新不丢失
     ====================================================================== */
  const STORE_KEY = 'cidas-anno-v1';
  const frameEl = document.querySelector('.app');

  if (frameEl) {
    const frameRect = () => frameEl.getBoundingClientRect();
    const scaleOf = () => frameRect().width / APP_W;
    const activeScreen = () => {
      const el = screens.find((s) => s.classList.contains('is-active'));
      return el ? el.dataset.screen : '';
    };
    const titleOf = (id) => {
      const el = screens.find((s) => s.dataset.screen === id);
      return el ? el.dataset.title || id : id;
    };

    let notes = [];
    try { notes = JSON.parse(localStorage.getItem(STORE_KEY) || '[]'); } catch (err) { notes = []; }
    const persist = () => { try { localStorage.setItem(STORE_KEY, JSON.stringify(notes)); } catch (err) { /* ignore */ } };

    const layer = document.createElement('div');
    layer.className = 'anno-layer';
    frameEl.appendChild(layer);

    const actions = document.querySelector('.stage-actions');
    const btnMode = document.createElement('button');
    btnMode.className = 'btn btn-sm';
    btnMode.textContent = '框选批注';
    const btnExport = document.createElement('button');
    btnExport.className = 'btn btn-sm';
    btnExport.textContent = '导出批注';
    const btnClear = document.createElement('button');
    btnClear.className = 'btn btn-sm';
    btnClear.textContent = '清空';
    if (actions) {
      actions.appendChild(btnMode);
      actions.appendChild(btnExport);
      actions.appendChild(btnClear);
    }

    const listEl = document.createElement('div');
    listEl.className = 'anno-list';
    const panelEl = document.querySelector('.panel');
    if (panelEl) panelEl.appendChild(listEl);

    function toast(text) {
      const el = document.createElement('div');
      el.className = 'anno-toast';
      el.textContent = text;
      document.body.appendChild(el);
      setTimeout(() => el.remove(), 2200);
    }

    function describe(clientX, clientY) {
      layer.style.pointerEvents = 'none';
      const el = document.elementFromPoint(clientX, clientY);
      layer.style.pointerEvents = '';
      if (!el || !frameEl.contains(el)) return { path: '', text: '' };
      const parts = [];
      let node = el;
      while (node && node !== frameEl && parts.length < 5) {
        const cls = typeof node.className === 'string' && node.className.trim()
          ? '.' + node.className.trim().split(/\s+/).slice(0, 2).join('.')
          : '';
        parts.unshift(node.tagName.toLowerCase() + cls);
        node = node.parentElement;
      }
      return {
        path: parts.join(' > '),
        text: (el.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 80)
      };
    }

    function renderNotes() {
      layer.innerHTML = '';
      const id = activeScreen();
      const mine = notes.filter((n) => n.screen === id);
      mine.forEach((n, i) => {
        const box = document.createElement('div');
        box.className = 'anno-rect';
        box.style.left = n.x + 'px';
        box.style.top = n.y + 'px';
        box.style.width = n.w + 'px';
        box.style.height = n.h + 'px';
        const no = document.createElement('span');
        no.className = 'no';
        no.textContent = String(i + 1);
        box.appendChild(no);
        layer.appendChild(box);
      });

      if (!listEl) return;
      listEl.innerHTML = '';
      const head = document.createElement('p');
      head.className = 'panel-sub';
      head.style.margin = '0 0 6px';
      head.textContent = '批注 ' + notes.length + ' 条（本页 ' + mine.length + ' 条）';
      listEl.appendChild(head);
      notes.forEach((n, i) => {
        const row = document.createElement('div');
        row.className = 'anno-item';
        const no = document.createElement('span');
        no.className = 'no';
        no.textContent = String(i + 1);
        const txt = document.createElement('div');
        txt.className = 'txt';
        const line1 = document.createElement('div');
        line1.textContent = n.comment;
        const line2 = document.createElement('div');
        line2.className = 'meta';
        line2.textContent = titleOf(n.screen) + ' · ' + n.w + '×' + n.h;
        txt.appendChild(line1);
        txt.appendChild(line2);
        const del = document.createElement('a');
        del.className = 'del';
        del.href = '#';
        del.textContent = '删除';
        del.addEventListener('click', (e) => {
          e.preventDefault();
          notes = notes.filter((x) => x.id !== n.id);
          persist();
          renderNotes();
        });
        row.appendChild(no);
        row.appendChild(txt);
        row.appendChild(del);
        listEl.appendChild(row);
      });
    }

    function setMode(on) {
      layer.classList.toggle('is-on', on);
      btnMode.classList.toggle('btn-primary', on);
      btnMode.textContent = on ? '退出批注' : '框选批注';
    }

    btnMode.addEventListener('click', () => setMode(!layer.classList.contains('is-on')));

    btnClear.addEventListener('click', () => {
      if (!notes.length) { toast('暂无批注'); return; }
      notes = [];
      persist();
      renderNotes();
      toast('已清空全部批注');
    });

    btnExport.addEventListener('click', () => {
      if (!notes.length) { toast('暂无批注'); return; }
      const lines = ['# 页面原型批注（桌面端 1440×900）', '', '共 ' + notes.length + ' 条', ''];
      notes.forEach((n, i) => {
        lines.push('## ' + (i + 1) + '. ' + titleOf(n.screen));
        lines.push('- 页面编号：' + n.screen);
        lines.push('- 选区（设计稿像素）：x=' + n.x + ' y=' + n.y + ' 宽=' + n.w + ' 高=' + n.h);
        if (n.path) lines.push('- 选区元素：' + n.path);
        if (n.text) lines.push('- 元素文本：' + n.text);
        lines.push('- 批注内容：' + n.comment);
        lines.push('');
      });
      const text = lines.join('\n');
      const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = '原型批注.md';
      link.click();
      URL.revokeObjectURL(link.href);
      if (navigator.clipboard) navigator.clipboard.writeText(text).catch(() => {});
      toast('已导出批注文件并复制到剪贴板');
    });

    let startPoint = null;
    let draftEl = null;

    layer.addEventListener('pointerdown', (e) => {
      if (!layer.classList.contains('is-on')) return;
      const r = frameRect();
      const k = scaleOf();
      startPoint = { x: (e.clientX - r.left) / k, y: (e.clientY - r.top) / k, clientX: e.clientX, clientY: e.clientY };
      draftEl = document.createElement('div');
      draftEl.className = 'anno-draft';
      layer.appendChild(draftEl);
      layer.setPointerCapture(e.pointerId);
    });

    layer.addEventListener('pointermove', (e) => {
      if (!startPoint || !draftEl) return;
      const r = frameRect();
      const k = scaleOf();
      const x = (e.clientX - r.left) / k;
      const y = (e.clientY - r.top) / k;
      const left = Math.min(startPoint.x, x);
      const top = Math.min(startPoint.y, y);
      const w = Math.abs(x - startPoint.x);
      const h = Math.abs(y - startPoint.y);
      draftEl.style.left = left + 'px';
      draftEl.style.top = top + 'px';
      draftEl.style.width = w + 'px';
      draftEl.style.height = h + 'px';
      draftEl.dataset.geo = [left, top, w, h].join(',');
      draftEl.dataset.client = [Math.min(startPoint.clientX, e.clientX), Math.min(startPoint.clientY, e.clientY)].join(',');
    });

    layer.addEventListener('pointerup', (e) => {
      if (!startPoint || !draftEl) return;
      const geo = (draftEl.dataset.geo || '').split(',').map(Number);
      const client = (draftEl.dataset.client || '').split(',').map(Number);
      draftEl.remove();
      draftEl = null;
      startPoint = null;
      if (geo.length < 4 || geo[2] < 8 || geo[3] < 8) { toast('选区太小，请重新框选'); return; }
      const target = describe(client[0] + Math.abs(e.clientX - client[0]) / 2, client[1] + Math.abs(e.clientY - client[1]) / 2);
      openEditor(geo[0], geo[1], geo[2], geo[3], target, e.clientX, e.clientY);
    });

    function openEditor(x, y, w, h, target, pointerX, pointerY) {
      const box = document.createElement('div');
      box.className = 'anno-editor';
      box.style.left = Math.max(12, Math.min(pointerX + 12, window.innerWidth - 312)) + 'px';
      box.style.top = Math.max(12, Math.min(pointerY + 12, window.innerHeight - 200)) + 'px';

      const label = document.createElement('p');
      label.className = 'hint';
      label.style.margin = '0';
      label.textContent = '选区 ' + Math.round(w) + '×' + Math.round(h) +
        '（' + (target.text ? target.text.slice(0, 20) : '空白区域') + '）';
      const area = document.createElement('textarea');
      area.className = 'textarea';
      area.placeholder = '填写修改意见，例如：这里按钮间距太大，改为 12px；标题字号改为 20px';
      const row = document.createElement('div');
      row.style.display = 'flex';
      row.style.gap = '8px';
      row.style.justifyContent = 'flex-end';
      const cancel = document.createElement('button');
      cancel.className = 'btn btn-sm';
      cancel.textContent = '取消';
      const ok = document.createElement('button');
      ok.className = 'btn btn-sm btn-primary';
      ok.textContent = '保存批注';
      row.appendChild(cancel);
      row.appendChild(ok);
      box.appendChild(label);
      box.appendChild(area);
      box.appendChild(row);
      document.body.appendChild(box);
      area.focus();

      const close = () => box.remove();
      cancel.addEventListener('click', close);
      ok.addEventListener('click', () => {
        const comment = area.value.trim();
        if (!comment) { area.focus(); return; }
        notes.push({
          id: 'a' + Date.now(),
          screen: activeScreen(),
          x: Math.round(x), y: Math.round(y), w: Math.round(w), h: Math.round(h),
          path: target.path, text: target.text, comment: comment,
          time: new Date().toLocaleString('zh-CN')
        });
        persist();
        close();
        renderNotes();
        toast('已保存第 ' + notes.length + ' 条批注');
      });
      area.addEventListener('keydown', (ev) => {
        if (ev.key === 'Enter' && !ev.shiftKey) { ev.preventDefault(); ok.click(); }
        if (ev.key === 'Escape') close();
      });
    }

    document.addEventListener('keydown', (e) => {
      if (e.target.closest('input, textarea, select')) return;
      if (e.key === 'a' || e.key === 'A') setMode(!layer.classList.contains('is-on'));
    });

    onShow.push(renderNotes);
    renderNotes();
  }

  show(location.hash.slice(1) || screens[0].dataset.screen, false);
  layout();
})();
