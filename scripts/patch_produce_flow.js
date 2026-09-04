const fs = require('fs');
const path = require('path');
const htmlPath = path.join(__dirname, '../prototype/用例地图_公司平台_P0增强原型.html');
let html = fs.readFileSync(htmlPath, 'utf8');

function replaceOnce(source, from, to) {
  if (!source.includes(from)) throw new Error('missing: ' + from.slice(0, 80));
  return source.replace(from, to);
}

// 1) CSS for produce flow
if (!html.includes('.produce-steps')) {
  html = html.replace(
    '    .path-callout {',
    `    .produce-steps {
      display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;
      margin-bottom: 14px;
    }
    .produce-step {
      border: 1px solid var(--line); background: #fff; border-radius: 12px;
      padding: 12px 14px; text-align: left; cursor: pointer;
      display: flex; flex-direction: column; gap: 2px; transition: .15s ease;
    }
    .produce-step:hover { border-color: #93c5fd; background: #f8fbff; }
    .produce-step.active {
      border-color: #93c5fd; background: var(--blue-soft);
      box-shadow: 0 0 0 1px rgba(37,99,235,.12);
    }
    .produce-step .step-index {
      font-size: 11px; color: var(--muted); font-weight: 650; letter-spacing: .3px;
    }
    .produce-step.active .step-index { color: var(--blue); }
    .produce-step strong { font-size: 14px; color: var(--text); }
    .produce-step .step-desc { font-size: 12px; color: var(--muted); }
    .produce-step .step-badge {
      margin-top: 6px; align-self: flex-start; min-width: 18px; height: 18px;
      border-radius: 999px; padding: 0 6px; background: #e2e8f0; color: #475569;
      font-size: 11px; line-height: 18px; text-align: center;
    }
    .produce-step.active .step-badge { background: var(--blue); color: #fff; }
    .produce-panel { display: none; }
    .produce-panel.active { display: block; }
    .produce-panel .path-callout { display: none; }
    .produce-panel .page-hd { margin-top: 0; }
    @media (max-width: 900px) {
      .produce-steps { grid-template-columns: 1fr; }
    }
    .path-callout {`
  );
}

// 2) Sidebar: single 资产生产 entry
html = replaceOnce(
  html,
  `      <div class="menu-group">资产生产（主流程）</div>
      <button class="menu-btn" data-view="import"><span class="menu-label">1. 资产导入</span><span class="badge" id="badgeKeywords">0</span></button>
      <button class="menu-btn" data-view="review"><span class="menu-label">2. 评审校准</span><span class="badge" id="badgeReview">0</span></button>
      <button class="menu-btn" data-view="assets"><span class="menu-label">3. 正式资产库</span><span class="badge" id="badgeAssets">0</span></button>`,
  `      <div class="menu-group">资产生产</div>
      <button class="menu-btn" data-view="produce"><span class="menu-label">资产生产</span><span class="badge" id="badgeProduce">0</span></button>
      <span class="hidden" id="badgeKeywords">0</span>
      <span class="hidden" id="badgeReview">0</span>
      <span class="hidden" id="badgeAssets">0</span>`
);

// 3) Wrap import/review/assets into produce view
const importStart = html.indexOf('      <!-- 4. 资产导入：知识库 + 历史用例必须都完成才能下一步 -->');
const syncStart = html.indexOf('      <section class="view" id="view-sync">');
if (importStart < 0 || syncStart < 0) throw new Error('import/sync markers missing');

let block = html.slice(importStart, syncStart);

// Remove keywords empty shell from middle
block = block.replace(
  /\n\s*<!-- 旧关键字确认页已并入资产导入预览，保留空壳避免历史锚点报错 -->\n\s*<section class="view hidden" id="view-keywords"[^>]*>\s*<\/section>\n/,
  '\n'
);

block = block
  .replace(
    '<section class="view" id="view-import">',
    '<section class="produce-panel active" id="view-import" data-produce-step="import">'
  )
  .replace(
    '<section class="view" id="view-review">',
    '<section class="produce-panel" id="view-review" data-produce-step="review">'
  )
  .replace(
    '<section class="view" id="view-assets">',
    '<section class="produce-panel" id="view-assets" data-produce-step="assets">'
  );

// Soften page titles inside panels (less redundant with outer title)
block = block
  .replace('<h1>资产导入</h1>', '<h1>① 导入与关键字校准</h1>')
  .replace('<h1>评审校准</h1>', '<h1>② 评审校准</h1>')
  .replace('<h1>正式用例地图资产</h1>', '<h1>③ 正式资产库</h1>')
  .replace('<button class="btn" data-view="overview">回总览</button>', '')
  .replace('<button class="btn" data-view="review">回评审校准</button>', '<button class="btn" type="button" data-produce-step="review">回评审校准</button>')
  .replace(
    'id="btnGoReview" disabled title="需先确认关键字并生成地图草稿">去评审校准</button>',
    'id="btnGoReview" disabled title="需先确认关键字并生成地图草稿">下一步：评审校准</button>'
  )
  .replace(
    'id="btnOpenAssetsTop" style="margin-left:auto">查看正式资产库</button>',
    'id="btnOpenAssetsTop" style="margin-left:auto">下一步：正式资产库</button>'
  );

const produceShell =
`      <!-- 资产生产：导入 → 评审 → 正式资产（单页步骤流） -->
      <section class="view" id="view-produce">
        <div class="page-hd">
          <div>
            <h1>资产生产</h1>
            <p class="page-guide">一条链路完成导入校准、评审入库与正式资产消费，无需在多个菜单间跳转。</p>
          </div>
          <button class="btn" data-view="overview" type="button">回总览</button>
        </div>
        <div class="produce-steps" id="produceSteps">
          <button type="button" class="produce-step active" data-produce-step="import">
            <span class="step-index">STEP 01</span>
            <strong>导入校准</strong>
            <span class="step-desc">历史用例 + 知识库 · 关键字</span>
            <span class="step-badge" id="badgeProduceImport">0</span>
          </button>
          <button type="button" class="produce-step" data-produce-step="review">
            <span class="step-index">STEP 02</span>
            <strong>评审校准</strong>
            <span class="step-desc">挂载确认 · 缺口补齐</span>
            <span class="step-badge" id="badgeProduceReview">0</span>
          </button>
          <button type="button" class="produce-step" data-produce-step="assets">
            <span class="step-index">STEP 03</span>
            <strong>正式资产</strong>
            <span class="step-desc">多维检索 · 回归包</span>
            <span class="step-badge" id="badgeProduceAssets">0</span>
          </button>
        </div>
` + block +
`        <section class="view hidden" id="view-keywords" aria-hidden="true"></section>
      </section>

`;

html = html.slice(0, importStart) + produceShell + html.slice(syncStart);

// 4) Overview path + demo links
html = html
  .replace(
    `          <span>1 导入并校准关键字</span><span class="sep">→</span>
          <span>2 评审</span><span class="sep">→</span>
          <span>3 正式资产库</span>`,
    `          <span>资产生产：导入校准</span><span class="sep">→</span>
          <span>评审</span><span class="sep">→</span>
          <span>正式资产</span>`
  )
  .replace(
    `<button class="btn" type="button" data-view="import">看资产导入</button>
      <button class="btn" type="button" data-view="review">看评审队列</button>
      <button class="btn" type="button" data-view="assets">看正式资产</button>`,
    `<button class="btn" type="button" data-view="produce" data-produce-step="import">看资产生产</button>
      <button class="btn" type="button" data-view="produce" data-produce-step="review">看评审</button>
      <button class="btn" type="button" data-view="produce" data-produce-step="assets">看正式资产</button>`
  );

html = html.replace(/data-view="import"/g, 'data-view="produce" data-produce-step="import"');
html = html.replace(/data-view="review"/g, 'data-view="produce" data-produce-step="review"');
// Careful: assets appears in many places - replace data-view="assets" but not produce-step already set
html = html.replace(/data-view="assets"/g, 'data-view="produce" data-produce-step="assets"');

// Fix accidental double attributes if any
html = html.replace(/data-view="produce" data-produce-step="import" data-produce-step="import"/g, 'data-view="produce" data-produce-step="import"');
html = html.replace(/data-view="produce" data-produce-step="review" data-produce-step="review"/g, 'data-view="produce" data-produce-step="review"');
html = html.replace(/data-view="produce" data-produce-step="assets" data-produce-step="assets"/g, 'data-view="produce" data-produce-step="assets"');

// Fix buttons that used data-produce-step alone with data-view produce already - the review button inside assets
// data-produce-step="review" without data-view - add handler via produce steps

// 5) Inject JS for produce state + switchView changes
if (!html.includes('function setProduceStep')) {
  const produceJs = `
    const produceState = { step: 'import' };

    function setProduceStep(stepName, options) {
      const opts = options || {};
      const allowed = ['import', 'review', 'assets'];
      const step = allowed.includes(stepName) ? stepName : 'import';
      produceState.step = step;
      document.querySelectorAll('.produce-panel').forEach((panel) => {
        panel.classList.toggle('active', panel.getAttribute('data-produce-step') === step);
      });
      document.querySelectorAll('#produceSteps .produce-step').forEach((btn) => {
        btn.classList.toggle('active', btn.getAttribute('data-produce-step') === step);
      });
      if (step === 'import' && typeof updateImportGate === 'function') updateImportGate();
      if (step === 'review' && typeof renderReviewQueue === 'function') renderReviewQueue();
      if (step === 'assets' && typeof renderAssetsPage === 'function') renderAssetsPage();
      if (!opts.silent) window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    function openProduce(stepName) {
      switchView('produce');
      setProduceStep(stepName || produceState.step || 'import', { silent: true });
    }

`;
  html = html.replace('    function switchView(viewId) {', produceJs + '    function switchView(viewId) {');
}

// Update switchView body
html = html.replace(
  `    function switchView(viewId) {
      if (viewId === 'keywords') viewId = 'import';
      document.querySelectorAll('.view').forEach((n) => n.classList.remove('active'));
      document.querySelectorAll('.menu-btn').forEach((n) => n.classList.remove('active'));
      const view = document.getElementById('view-' + viewId);
      if (view) view.classList.add('active');
      document.querySelectorAll('.menu-btn').forEach((btn) => {
        if (btn.dataset.view === viewId) btn.classList.add('active');
      });
      const globalSearch = document.getElementById('globalSearch');
      // 搜索条已内置于总览页；随 view-overview 显隐，无需再单独控制
      if (globalSearch) globalSearch.classList.toggle('hidden', viewId !== 'overview');
      refreshHint();
      if (viewId === 'import' && typeof updateImportGate === 'function') updateImportGate();
      if (viewId === 'review' && typeof renderReviewQueue === 'function') renderReviewQueue();
      if (viewId === 'assets' && typeof renderAssetsPage === 'function') renderAssetsPage();
      if (viewId === 'catalog' && typeof renderCatalogPage === 'function') renderCatalogPage();
      if (viewId === 'detail' && typeof renderDetailFromAssets === 'function') renderDetailFromAssets();
      if (viewId === 'sync' && typeof renderSyncPage === 'function') renderSyncPage();
      if (viewId === 'baseline' && typeof renderBaselinePage === 'function') renderBaselinePage();
      if (viewId === 'health' && typeof renderHealthPage === 'function') renderHealthPage();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }`,
  `    function switchView(viewId, options) {
      const opts = options || {};
      if (viewId === 'keywords' || viewId === 'import' || viewId === 'review' || viewId === 'assets') {
        const stepMap = { keywords: 'import', import: 'import', review: 'review', assets: 'assets' };
        const stepName = opts.produceStep || stepMap[viewId] || 'import';
        viewId = 'produce';
        opts.produceStep = stepName;
      }
      document.querySelectorAll('.view').forEach((n) => n.classList.remove('active'));
      document.querySelectorAll('.menu-btn').forEach((n) => n.classList.remove('active'));
      const view = document.getElementById('view-' + viewId);
      if (view) view.classList.add('active');
      document.querySelectorAll('.menu-btn').forEach((btn) => {
        if (btn.dataset.view === viewId) btn.classList.add('active');
      });
      const globalSearch = document.getElementById('globalSearch');
      if (globalSearch) globalSearch.classList.toggle('hidden', viewId !== 'overview');
      refreshHint();
      if (viewId === 'produce') setProduceStep(opts.produceStep || produceState.step || 'import', { silent: true });
      if (viewId === 'catalog' && typeof renderCatalogPage === 'function') renderCatalogPage();
      if (viewId === 'detail' && typeof renderDetailFromAssets === 'function') renderDetailFromAssets();
      if (viewId === 'sync' && typeof renderSyncPage === 'function') renderSyncPage();
      if (viewId === 'baseline' && typeof renderBaselinePage === 'function') renderBaselinePage();
      if (viewId === 'health' && typeof renderHealthPage === 'function') renderHealthPage();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }`
);

// Update data-view click handler to pass produce step
html = html.replace(
  `    document.querySelectorAll('[data-view]').forEach((el) => {
      el.addEventListener('click', () => switchView(el.dataset.view));
    });`,
  `    document.querySelectorAll('[data-view]').forEach((el) => {
      el.addEventListener('click', () => {
        const step = el.getAttribute('data-produce-step');
        switchView(el.dataset.view, step ? { produceStep: step } : {});
      });
    });

    const produceSteps = document.getElementById('produceSteps');
    if (produceSteps) {
      produceSteps.addEventListener('click', (event) => {
        const btn = event.target.closest('[data-produce-step]');
        if (!btn) return;
        openProduce(btn.getAttribute('data-produce-step'));
      });
    }
    document.querySelectorAll('[data-produce-step]:not([data-view])').forEach((el) => {
      el.addEventListener('click', () => openProduce(el.getAttribute('data-produce-step')));
    });`
);

// Redirect openFormalAssets / switchView calls
html = html.replace(
  `    function openFormalAssets() {
      switchView('assets');
    }`,
  `    function openFormalAssets() {
      openProduce('assets');
    }`
);

html = html.replace(/switchView\('import'\)/g, "openProduce('import')");
html = html.replace(/switchView\('review'\)/g, "openProduce('review')");
html = html.replace(/switchView\('assets'\)/g, "openProduce('assets')");

// updateNavBadges to sync produce badges
html = html.replace(
  `      if (badgeReview) badgeReview.textContent = String(pending);
      if (badgeAssets) badgeAssets.textContent = String(assets);
      if (badgeCatalog) badgeCatalog.textContent = String(assets || pending);`,
  `      if (badgeReview) badgeReview.textContent = String(pending);
      if (badgeAssets) badgeAssets.textContent = String(assets);
      if (badgeCatalog) badgeCatalog.textContent = String(assets || pending);
      const badgeProduce = document.getElementById('badgeProduce');
      const badgeProduceImport = document.getElementById('badgeProduceImport');
      const badgeProduceReview = document.getElementById('badgeProduceReview');
      const badgeProduceAssets = document.getElementById('badgeProduceAssets');
      const keywordCountForBadge = allActiveKeywordTexts().length;
      if (badgeProduce) badgeProduce.textContent = String(pending || assets || keywordCountForBadge);
      if (badgeProduceImport) badgeProduceImport.textContent = String(keywordCountForBadge);
      if (badgeProduceReview) badgeProduceReview.textContent = String(pending);
      if (badgeProduceAssets) badgeProduceAssets.textContent = String(assets);`
);

// Fix generate map / go review to use openProduce
html = html.replace(
  `    document.getElementById('btnGoReview').addEventListener('click', () => {
      if (!reviewState.generated) return;
      switchView('review');
    });`,
  `    document.getElementById('btnGoReview').addEventListener('click', () => {
      if (!reviewState.generated) return;
      openProduce('review');
    });`
);

// In case previous replace already changed switchView to openProduce for btnGoReview
if (!html.includes("btnGoReview').addEventListener") || !html.includes("openProduce('review')")) {
  // try alternate
  html = html.replace(
    /document\.getElementById\('btnGoReview'\)\.addEventListener\('click', \(\) => \{\s*if \(!reviewState\.generated\) return;\s*openProduce\('review'\);\s*\}\);/,
    `document.getElementById('btnGoReview').addEventListener('click', () => {
      if (!reviewState.generated) return;
      openProduce('review');
    });`
  );
}

fs.writeFileSync(htmlPath, html);
console.log('produce flow patched', {
  hasProduceView: html.includes('id="view-produce"'),
  hasProduceSteps: html.includes('produce-steps'),
  hasSetProduceStep: html.includes('function setProduceStep'),
  menuProduce: html.includes('data-view="produce"'),
  oldImportMenu: html.includes('1. 资产导入'),
  viewImportIsPanel: html.includes('produce-panel active" id="view-import"')
});
