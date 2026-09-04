const fs = require('fs');
const path = require('path');
const { DEMO_KB_MD, DEMO_CASES_CSV } = require('./quote_demo_data.js');

const htmlPath = path.join(__dirname, '../prototype/用例地图_公司平台_P0增强原型.html');
let html = fs.readFileSync(htmlPath, 'utf8');

function jsString(value) {
  return JSON.stringify(value);
}

function replaceOnce(source, startMarker, endMarker, replacement) {
  const start = source.indexOf(startMarker);
  if (start < 0) throw new Error('start missing: ' + startMarker.slice(0, 60));
  const end = source.indexOf(endMarker, start + startMarker.length);
  if (end < 0) throw new Error('end missing after: ' + startMarker.slice(0, 60));
  return source.slice(0, start) + replacement + source.slice(end);
}

// 1) Replace demo payloads
html = replaceOnce(
  html,
  '    const DEMO_CASES_CSV =',
  '    const CASE_STOP_WORDS',
  '    const DEMO_CASES_CSV = ' + jsString(DEMO_CASES_CSV) + ';\n\n' +
  '    const DEMO_KB_MD = ' + jsString(DEMO_KB_MD) + ';\n\n'
);

// 2) Raise parseKnowledge caps
html = html.replace(
  'scenes: uniqueList(scenes).slice(0, 12),\n        features: uniqueList(features).slice(0, 12),\n        rules: uniqueList(rules).slice(0, 12)',
  'scenes: uniqueList(scenes).slice(0, 20),\n        features: uniqueList(features).slice(0, 24),\n        rules: uniqueList(rules).slice(0, 20)'
);

// 3) Replace guess helpers + api
const guessBlock = `    function guessScene(caseRow, kbParsed, appName) {
      if (caseRow.scene) return caseRow.scene;
      const haystack = (caseRow.caseName + ' ' + caseRow.step + ' ' + caseRow.expected).toLowerCase();
      const sceneWords = activeKeywords('scene');
      const scenePool = sceneWords.length ? sceneWords : (kbParsed.scenes || []);
      const exact = scenePool.find((scene) => haystack.includes(String(scene).toLowerCase()));
      if (exact) return exact;
      if (/库存|失效|作废|预占|合同|地址|mq/.test(haystack)) return '生命周期与外部联动';
      if (/审核|驳回|回退|抽审|免审|待审核|自动审核/.test(haystack)) return '造价提交与审核';
      if (/造价|明细落库|designcost|cancel/.test(haystack)) return '造价单生成与明细落库';
      if (/总价|金额|数量价|固定价|租赁|去利润|分组|四舍五入|含税/.test(haystack)) return '金额计算与汇总';
      if (/物料|带出|配件|spu|sku|uniquecode|套餐/.test(haystack)) return '物料处理与自动带出';
      if (/参数|精度|功能区间|operatetype|必填|单价为空|数量非法/.test(haystack)) return '参数解析与输入精度';
      if (/场景|sfbj|lfbj|jzbj|价格来源|目录价|销售价/.test(haystack)) return '报价场景识别与价格来源';
      if (/生成报价|首次下新|续约|改价|报价单/.test(haystack)) return '报价触发与报价单生成';
      if (/提交/.test(haystack)) return '造价提交与审核';
      return (scenePool[0]) || (appName + '默认场景');
    }

    function guessFeature(caseRow, kbParsed, sceneName) {
      const haystack = (caseRow.caseName + ' ' + caseRow.step + ' ' + caseRow.expected).toLowerCase();
      const featureWords = activeKeywords('feature');
      const featurePool = featureWords.length ? featureWords : (kbParsed.features || []);
      const exact = featurePool.find((feature) => {
        const token = String(feature).toLowerCase().replace(/\\s/g, '');
        return token && haystack.replace(/\\s/g, '').includes(token);
      });
      if (exact) return exact;
      if (/库存|失效|作废|预占/.test(haystack)) return '库存预占与失效';
      if (/拒绝|回退/.test(haystack)) return '审核拒绝回退';
      if (/自动审核|免审|抽审/.test(haystack)) return '自动审核判定';
      if (/提交审核|待审核|审核通过|幂等/.test(haystack)) return '提交审核';
      if (/造价|明细落库|cancel/.test(haystack)) return '造价明细落库';
      if (/去利润/.test(haystack)) return '去利润价计算';
      if (/分组|硬装|软装/.test(haystack)) return '分组汇总';
      if (/租赁/.test(haystack)) return '租赁价汇总';
      if (/固定价/.test(haystack)) return '固定价汇总';
      if (/数量价|总价|金额|四舍五入|含税/.test(haystack)) return '数量价汇总';
      if (/uniquecode|去重/.test(haystack)) return 'uniqueCode去重';
      if (/配件比例|比例/.test(haystack)) return '配件比例校验';
      if (/物料|带出|套餐|spu/.test(haystack)) return '物料自动带出';
      if (/精度|四舍五入|12\\.345/.test(haystack)) return '输入精度校验';
      if (/参数|必填|功能区间|operatetype|单价为空|数量非法|二级分类/.test(haystack)) return '参数必填校验';
      if (/价格来源|sfbj|lfbj|jzbj|目录价|销售价/.test(haystack)) return '场景价格来源映射';
      if (/报价类型|新签|续约bom/.test(haystack)) return '报价类型识别';
      if (/生成报价|首次下新|改价|报价单/.test(haystack)) return '报价单生成';
      return (featurePool[0]) || (sceneName + '-功能点');
    }

    function calcConfidence(caseRow, featureName, sceneName) {
      let score = 58;
      const haystack = (caseRow.caseName + ' ' + caseRow.step + ' ' + caseRow.expected).toLowerCase();
      const hits = allActiveKeywordTexts().filter((word) => word && haystack.includes(String(word).toLowerCase()));
      score += Math.min(28, hits.length * 5);
      if (caseRow.scene) score += 8;
      if (caseRow.step) score += 4;
      if (caseRow.expected) score += 4;
      if (featureName && haystack.includes(String(featureName).toLowerCase().slice(0, 2))) score += 4;
      if (sceneName && caseRow.scene === sceneName) score += 6;
      if (/R00\\d|DesignCost|QUANTITY_PRICE|FIXED_PRICE|SFBJ|LFBJ|JZBJ/.test(caseRow.caseName + caseRow.step + caseRow.expected)) score += 4;
      return Math.min(96, score);
    }

`;

html = replaceOnce(
  html,
  '    function guessScene(caseRow, kbParsed, appName) {',
  '    function historyBagText(items) {',
  guessBlock
);

const apiBlock = `    function guessApi(featureName, appName) {
      const feature = String(featureName || '');
      const app = String(appName || 'quote');
      if (/数量价|固定价|租赁价|去利润|分组|金额|汇总/.test(feature)) return 'POST /api/quote/calc';
      if (/价格来源|场景|类型识别/.test(feature)) return 'GET /api/quote/price-source';
      if (/参数|精度|必填/.test(feature)) return 'POST /api/quote/validate-param';
      if (/物料|带出|配件|uniqueCode/.test(feature)) return 'POST /api/quote/materials';
      if (/造价|明细落库/.test(feature)) return 'POST /api/quote/design-cost/save';
      if (/提交审核|自动审核|审核拒绝/.test(feature)) return 'POST /api/quote/audit';
      if (/库存|失效|预占/.test(feature)) return 'POST /api/quote/lifecycle';
      if (/报价单生成/.test(feature)) return 'POST /api/quote/create';
      if (/量房/.test(app)) return 'POST /api/measure/save';
      if (/设计/.test(app)) return 'POST /api/design/save';
      return 'POST /api/' + encodeURIComponent(app) + '/action';
    }

`;

html = replaceOnce(
  html,
  '    function guessApi(featureName, appName) {',
  '    function generateReviewQueue() {',
  apiBlock
);

// 4) seedFullDemo confirmation strategy
const seedConfirm = `      // 按场景均匀确认历史用例入库，并保留待评审/抽检与缺口样例
      const confirmIds = [];
      const sceneBuckets = {};
      reviewState.items.forEach((item) => {
        if (item.sourceType !== '历史挂载') return;
        const sceneName = item.scene || '未命名';
        if (!sceneBuckets[sceneName]) sceneBuckets[sceneName] = [];
        sceneBuckets[sceneName].push(item);
      });
      Object.keys(sceneBuckets).forEach((sceneName) => {
        sceneBuckets[sceneName].slice(0, 3).forEach((item) => confirmIds.push(item.id));
      });
      // 额外确认金额/审核高价值用例
      reviewState.items.forEach((item) => {
        if (item.sourceType !== '历史挂载') return;
        if (/TC-AM-00[1-4]|TC-AU-00[1-4]|TC-SC-00[1-3]|TC-MT-00[1-4]/.test(item.caseName) && !confirmIds.includes(item.id)) {
          confirmIds.push(item.id);
        }
      });
      const gapCandidates = reviewState.items.filter((item) => item.sourceType === 'AI缺口');
      gapCandidates.slice(0, 3).forEach((item) => {
        if (!confirmIds.includes(item.id)) confirmIds.push(item.id);
      });

`;

html = replaceOnce(
  html,
  '      // 确认一部分进入正式资产，保留一部分待评审，便于理解两端状态\n      const confirmIds = [];\n      reviewState.items.forEach((item, index) => {\n        if (item.sourceType === \'历史挂载\' && index < 4) confirmIds.push(item.id);\n        if (item.sourceType === \'AI缺口\' && /物料|价格来源|优惠券过期/.test(item.caseName + item.gapReason + item.expected)) {\n          // 缺口里挑一条确认入库作「缺口补齐」示例\n          if (!confirmIds.includes(item.id) && confirmIds.filter((id) => String(id).indexOf(\'gap\') === 0).length < 1) {\n            confirmIds.push(item.id);\n          }\n        }\n      });\n      if (confirmIds.filter((id) => String(id).indexOf(\'gap\') === 0).length < 1) {\n        const firstGap = reviewState.items.find((item) => item.sourceType === \'AI缺口\');\n        if (firstGap) confirmIds.push(firstGap.id);\n      }\n\n',
  '      confirmIds.forEach((itemId) => {',
  seedConfirm
);

// Mark a few pending as sample review / low confidence
const pendingBlock = `      // 留若干待评审与待抽检，便于演示评审队列
      const pendingHistList = reviewState.items.filter((item) => item.sourceType === '历史挂载' && item.status !== '已确认');
      pendingHistList.slice(0, 3).forEach((item, index) => {
        item.status = '待抽检';
        item.confidence = 58 + index * 3;
        item.lifecycle = '评审中';
        item.version = '0.9.0';
      });

`;

html = replaceOnce(
  html,
  '      // 再留一条低置信待抽检（若有）\n      const pendingHist = reviewState.items.find((item) => item.sourceType === \'历史挂载\' && item.status !== \'已确认\');\n      if (pendingHist) {\n        pendingHist.status = \'待抽检\';\n        pendingHist.confidence = 62;\n        pendingHist.lifecycle = \'评审中\';\n        pendingHist.version = \'0.9.0\';\n      }\n\n',
  '      const published = reviewState.items.filter((item) => item.status === \'已确认\');',
  pendingBlock
);

// Update sync log counts to match richer demo
html = html.replace(
  '增量同步：新增 TC-QUOTE-120/121；冲突 1 条（同名用例步骤不一致）已挂起',
  '增量同步：新增 TC-AM-007/TC-AU-006；冲突 1 条（同名用例步骤不一致）已挂起'
);
html = html.replace(
  'Webhook：功能点「优惠规则」字段变更 → 触发 2 条用例待重挂载',
  'Webhook：规则 R002/R007 变更 → 触发金额计算与自动审核相关用例待重挂载'
);
html = html.replace(
  '全量校准完成：历史用例 128 · 知识库规则 36 · 映射成功率 91%',
  '全量校准完成：历史用例 41 · 知识库规则 16 · 映射成功率 93%'
);

// 5) Detail page dynamic render helpers — insert before seedFullDemo
const detailHelpers = `
    const detailState = { feature: '数量价汇总', scene: '金额计算与汇总' };

    function openFeatureDetail(featureName, sceneName) {
      detailState.feature = featureName || detailState.feature;
      detailState.scene = sceneName || detailState.scene;
      switchView('detail');
      renderDetailFromAssets();
    }

    function renderDetailFromAssets() {
      const featureName = detailState.feature;
      const sceneName = detailState.scene;
      const title = document.getElementById('detailFeatureTitle');
      const detailFeature = document.getElementById('detailFeature');
      const detailScene = document.getElementById('detailScene');
      const detailApp = document.getElementById('detailApp');
      if (title) title.textContent = featureName;
      if (detailFeature) detailFeature.textContent = featureName;
      if (detailScene) detailScene.textContent = sceneName;
      if (detailApp) detailApp.textContent = '报价';
      const crumb = document.querySelector('#view-detail .crumb');
      if (crumb) {
        crumb.innerHTML = '<a data-view="catalog">用例目录</a> / 系统：报价 / 场景：' +
          escapeHtml(sceneName) + ' / <strong>功能点：' + escapeHtml(featureName) + '</strong>';
        crumb.querySelectorAll('[data-view]').forEach((el) => {
          el.addEventListener('click', () => switchView(el.dataset.view));
        });
      }
      const pool = typeof formalAssets === 'function' ? formalAssets({}) : [];
      const featureCases = pool.filter((item) => item.feature === featureName);
      const sceneCases = featureCases.length ? featureCases : pool.filter((item) => item.scene === sceneName);
      const relatedFeatures = Array.from(new Set(
        pool.filter((item) => item.scene === sceneName).map((item) => item.feature)
      )).filter((name) => name && name !== featureName).slice(0, 6);
      const relatedBox = document.getElementById('detailRelatedFeatures');
      if (relatedBox) {
        relatedBox.innerHTML = relatedFeatures.length
          ? relatedFeatures.map((name) => escapeHtml(name)).join('<br />')
          : '同场景暂无更多功能点';
      }
      const grid = document.getElementById('detailCaseGrid');
      if (grid) {
        const caseHtml = sceneCases.slice(0, 12).map((item, index) =>
          '<div class="mini' + (index === 0 ? ' active' : '') + '" data-case-name="' + escapeHtml(item.caseName) +
          '" data-case-id="' + escapeHtml(item.id) + '" data-tags="' + escapeHtml((item.testScenario || '') + ' ' + item.feature) + '">' +
          '<div class="id">' + escapeHtml(item.id) + ' · ' + escapeHtml(item.testScenario || '主路径') + '</div>' +
          '<div class="name">' + escapeHtml(item.caseName) + '</div></div>'
        ).join('') || '<div class="muted">暂无正式资产，请先确认入库或加载示例。</div>';
        const apiName = (sceneCases[0] && sceneCases[0].api) || guessApi(featureName, '报价');
        grid.innerHTML =
          '<div><div class="muted" style="margin-bottom:6px">用例</div>' + caseHtml + '</div>' +
          '<div><div class="muted" style="margin-bottom:6px">脚本</div>' +
          '<div class="mini active" data-case-name="quote_calc.json" data-tags="api"><div class="id">API</div><div class="name">quote_calc.json</div></div></div>' +
          '<div><div class="muted" style="margin-bottom:6px">数据</div>' +
          '<div class="mini active" data-case-name="家装标准报价方案" data-tags="数据"><div class="id">DT-QUOTE</div><div class="name">家装标准报价方案</div></div></div>' +
          '<div><div class="muted" style="margin-bottom:6px">执行</div>' +
          '<div class="mini active" data-case-name="近3次通过" data-tags="执行"><div class="name">近3次 ✓ ✓ ✓</div></div></div>';
        const apiChip = document.getElementById('detailApiChip');
        if (apiChip) apiChip.textContent = apiName;
        document.getElementById('detailCaseCount').textContent = sceneCases.length + ' 条';
        document.getElementById('detailLinkedCount').textContent = String(sceneCases.length);
        document.getElementById('detailSearchHint').textContent =
          '当前功能点：' + featureName + ' · 场景：' + sceneName + ' · 来自正式资产 ' + sceneCases.length + ' 条';
        grid.querySelectorAll('.mini').forEach((item) => {
          item.addEventListener('click', () => {
            const col = item.parentElement;
            col.querySelectorAll('.mini').forEach((node) => node.classList.remove('active'));
            item.classList.add('active');
          });
        });
      }
      if (typeof runDetailSearch === 'function') runDetailSearch();
    }

`;

if (!html.includes('function renderDetailFromAssets')) {
  html = html.replace(
    '    function seedFullDemo(options) {',
    detailHelpers + '    function seedFullDemo(options) {'
  );
}

// Call renderDetailFromAssets after seed and in switchView
if (!html.includes("if (viewId === 'detail'")) {
  html = html.replace(
    "if (viewId === 'catalog' && typeof renderCatalogPage === 'function') renderCatalogPage();",
    "if (viewId === 'catalog' && typeof renderCatalogPage === 'function') renderCatalogPage();\n      if (viewId === 'detail' && typeof renderDetailFromAssets === 'function') renderDetailFromAssets();"
  );
}

html = html.replace(
  'if (typeof renderCatalogPage === \'function\') renderCatalogPage();\n      refreshRoleUI();',
  "if (typeof renderCatalogPage === 'function') renderCatalogPage();\n      if (typeof renderDetailFromAssets === 'function') renderDetailFromAssets();\n      refreshRoleUI();"
);

// Catalog jump to detail with feature context
html = html.replace(
  "'<button class=\"btn\" type=\"button\" data-view=\"assets\">去正式资产库</button>' +\n" +
  "              '<button class=\"btn\" type=\"button\" data-view=\"detail\">看功能点详情</button>' +",
  "'<button class=\"btn\" type=\"button\" data-view=\"assets\">去正式资产库</button>' +\n" +
  "              '<button class=\"btn\" type=\"button\" id=\"btnCatalogToDetail\">看功能点详情</button>' +"
);

// Better: patch renderCatalogDetail case actions after inject - use simpler approach
html = html.replace(
  '<button class="btn" type="button" data-view="detail">看功能点详情</button>',
  '<button class="btn" type="button" data-catalog-detail="1">看功能点详情</button>'
);

// Add delegated handler near bindCatalogEvents end - inject into bindCatalogEvents if needed
if (!html.includes('data-catalog-detail')) {
  // already replaced above in template string maybe with escaped quotes
}

// Fix catalog detail button in JS string (escaped)
html = html.replace(
  "'<button class=\"btn\" type=\"button\" data-view=\"detail\">看功能点详情</button>' +",
  "'<button class=\"btn\" type=\"button\" data-catalog-open-detail=\"1\">看功能点详情</button>' +"
);

if (!html.includes('data-catalog-open-detail')) {
  // try unescaped version from earlier replace
}

// Add click handler for catalog detail open
if (!html.includes('data-catalog-open-detail') && html.includes('data-catalog-detail')) {
  html = html.replace(/data-catalog-detail="1"/g, 'data-catalog-open-detail="1"');
}

if (!html.includes('catalog-open-detail')) {
  console.warn('catalog detail button hook missing, will add fallback listener');
}

const catalogHandler = `
      const catalogDetailBody = document.getElementById('catalogDetailBody');
      if (catalogDetailBody && !catalogDetailBody.dataset.boundDetail) {
        catalogDetailBody.dataset.boundDetail = '1';
        catalogDetailBody.addEventListener('click', (event) => {
          const openDetail = event.target.closest('[data-catalog-open-detail], [data-catalog-detail]');
          if (!openDetail) return;
          const selected = findCatalogNode(catalogState.roots, catalogState.selectedId);
          const caseItem = selected && selected.caseItem ? selected.caseItem : (selected && selected.cases && selected.cases[0]);
          if (caseItem) openFeatureDetail(caseItem.feature, caseItem.scene);
          else switchView('detail');
        });
      }
`;

if (!html.includes('data-catalog-open-detail') && !html.includes('data-catalog-detail')) {
  // force into renderCatalogDetail string
  html = html.replace(
    '去正式资产库</button>\' +\n              \'<button class="btn" type="button" data-view="detail">看功能点详情</button>',
    '去正式资产库</button>\' +\n              \'<button class="btn" type="button" data-catalog-open-detail="1">看功能点详情</button>'
  );
}

if (html.includes('function bindCatalogEvents') && !html.includes('catalogDetailBody.dataset.boundDetail')) {
  html = html.replace(
    '    bindCatalogEvents();',
    catalogHandler + '\n    bindCatalogEvents();'
  );
}

// Overview drill table - update static scene names lightly
html = html.replace(
  `<td>获取报价</td>
                      <td>计算报价、识别价格来源</td>`,
  `<td>金额计算与汇总</td>
                      <td>数量价汇总、去利润价计算、分组汇总</td>`
);
html = html.replace(
  `<td>提交报价</td>`,
  `<td>参数解析与输入精度</td>`
);
html = html.replace(
  `参数校验、联系人必填`,
  `参数必填校验、输入精度校验`
);
html = html.replace(
  `<td>报价审核</td>`,
  `<td>造价提交与审核</td>`
);
html = html.replace(
  `提交审核、驳回回退`,
  `提交审核、审核拒绝回退、自动审核判定`
);
html = html.replace(
  '打开计算报价',
  '打开数量价汇总'
);

// Wire overview detail button to openFeatureDetail - change data-view detail buttons that say 打开
html = html.replace(
  '<button class="link" data-view="detail">打开数量价汇总</button>',
  '<button class="link" type="button" id="btnOpenQuoteDetail">打开数量价汇总</button>'
);

if (!html.includes('btnOpenQuoteDetail')) {
  // ok if missing
} else if (!html.includes("getElementById('btnOpenQuoteDetail')")) {
  html = html.replace(
    'document.getElementById(\'btnLoadDemo\').addEventListener(\'click\', () => seedFullDemo());',
    `const btnOpenQuoteDetail = document.getElementById('btnOpenQuoteDetail');
    if (btnOpenQuoteDetail) btnOpenQuoteDetail.addEventListener('click', () => openFeatureDetail('数量价汇总', '金额计算与汇总'));
    document.getElementById('btnLoadDemo').addEventListener('click', () => seedFullDemo());`
  );
}

// Default detail labels in HTML
html = html.replace(
  '功能点详情 · <span id="detailFeatureTitle">计算报价</span>',
  '功能点详情 · <span id="detailFeatureTitle">数量价汇总</span>'
);
html = html.replace(
  '<div class="h-label">应用</div><div class="h-value" id="detailApp">报价</div></div>\n            <div class="h-item"><div class="h-label">业务场景</div><div class="h-value" id="detailScene">获取报价</div></div>\n            <div class="h-item active"><div class="h-label">功能点</div><div class="h-value" style="color:var(--blue)" id="detailFeature">计算报价</div></div>',
  '<div class="h-label">系统</div><div class="h-value" id="detailApp">报价</div></div>\n            <div class="h-item"><div class="h-label">业务场景</div><div class="h-value" id="detailScene">金额计算与汇总</div></div>\n            <div class="h-item active"><div class="h-label">功能点</div><div class="h-value" style="color:var(--blue)" id="detailFeature">数量价汇总</div></div>'
);
html = html.replace(
  '当前功能点：计算报价。搜索会过滤本页用例，并提示正式资产库中的相关命中。',
  '当前功能点：数量价汇总。搜索会过滤本页用例，并提示正式资产库中的相关命中。'
);
html = html.replace(
  '识别价格来源<br />参数解析校验<br />物料自动带出',
  '固定价汇总<br />去利润价计算<br />分组汇总'
);

// Demo banner text
html = html.replace(
  '已预置「家装 / 报价」导入、评审、正式资产、基线与同步日志，可直接点右侧入口体验。',
  '已预置报价主链 8 场景 / 41 条历史用例 / 16 条规则，可从用例目录与正式资产库体验。'
);

fs.writeFileSync(htmlPath, html);
console.log('patched', {
  cases: DEMO_CASES_CSV.split('\n').length - 1,
  kbLen: DEMO_KB_MD.length,
  hasDetail: html.includes('renderDetailFromAssets'),
  hasGuess: html.includes('报价触发与报价单生成'),
  hasSeedBuckets: html.includes('sceneBuckets')
});
