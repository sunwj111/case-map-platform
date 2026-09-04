/* 功能点地图 + 领域全景 · P0 全局原型集成 */
(function initP0FeatureMap(global) {
  const COVERAGE_LABEL = { covered: '已覆盖', partial: '部分覆盖', gap: '缺口' };
  const NODE_SIZE = {
    domain: { w: 228, h: 148 },
    app: { w: 168, h: 92 },
    flow: { w: 132, h: 88 }
  };

  const mapState = {
    selectedScenarioId: null,
    selectedCaseId: null,
    panoramaSelectedId: null,
    panoramaLevel: 'platform',
    panoramaDomainId: null,
    panoramaAppId: null,
    panoramaZoom: 0.85,
    panoramaPanX: 24,
    panoramaPanY: 48,
    panoramaDragging: false,
    panoramaDragStart: null,
    detailViewMode: 'classic',
    currentFeatureKey: null
  };

  let hooks = {
    showToast: function(message) { global.alert(message); },
    openFeatureDetail: function() {},
    switchView: function() {},
    escapeHtml: function(value) {
      return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
    }
  };

  function getSamples() {
    return global.__FEATURE_MAP_SAMPLES__ || {};
  }

  function getPanoramaData() {
    return global.__DOMAIN_PANORAMA__ || { nodes: [], edges: [], summary: {} };
  }

  function getPlatform() {
    return global.__PLATFORM_PANORAMA__ || { domains: [], domainEdges: [], appFlows: {}, summary: {} };
  }

  function getDomainById(domainId) {
    return (getPlatform().domains || []).find((item) => item.id === domainId) || null;
  }

  function getAppById(domainId, appId) {
    const domain = getDomainById(domainId);
    return ((domain && domain.apps) || []).find((item) => item.id === appId) || null;
  }

  function getAppFlow(domainId, appId) {
    const app = getAppById(domainId, appId);
    if (!app) return getPanoramaData();
    if (app.flowKey === 'home/quote') return getPanoramaData();
    const flows = getPlatform().appFlows || {};
    return flows[app.flowKey] || { nodes: [], edges: [], displayName: app.name };
  }

  function nodeSize(kind) {
    return NODE_SIZE[kind] || NODE_SIZE.flow;
  }

  function resolveFeatureKey(featureName) {
    const samples = getSamples();
    const keys = Object.keys(samples);
    return keys.find((key) => key.endsWith('/' + featureName)) ||
      keys.find((key) => samples[key].spine && samples[key].spine.featureName === featureName) ||
      null;
  }

  function getMapByKey(featureKey) {
    if (!featureKey) return null;
    const existing = getSamples()[featureKey];
    if (existing) return existing;
    return ensureSyntheticFeatureMap(featureKey);
  }

  function collectCostAuditCasesByFeature(featureName) {
    const cost = global.__COST_AUDIT_DELIVERY__;
    if (!cost) return [];
    const rows = (cost.caseLinks && cost.caseLinks.length)
      ? cost.caseLinks
      : (cost.reviewItems || []).map((item) => ({
        id: 'CA-' + String(item.originalCaseId || '').padStart(3, '0'),
        name: item.caseName,
        priority: item.level,
        feature: item.feature,
        api: item.api,
        status: item.status,
        confidence: item.confidence
      }));
    return rows.filter((item) => {
      if (!featureName) return true;
      if (item.feature === featureName) return true;
      // 时效初审/复审都归到审核时效入口时也可匹配
      if (featureName === '审核时效' && String(item.feature || '').indexOf('审核时效') === 0) return true;
      return false;
    });
  }

  function pickPrimaryApi(featureName, cases) {
    const cost = global.__COST_AUDIT_DELIVERY__;
    const mapped = ((cost && cost.featureApis) || []).find((item) => item.feature === featureName || String(item.feature || '').indexOf(featureName) === 0);
    if (mapped) {
      return {
        method: mapped.method || 'POST',
        path: mapped.path,
        label: (mapped.method || '') + ' ' + mapped.path,
        relation: mapped.relation || '主接口',
        controller: mapped.controller || '',
        confidence: 90
      };
    }
    const firstApi = (cases[0] && cases[0].api) || '';
    if (!firstApi) return null;
    return { method: '', path: firstApi, label: firstApi, relation: '关联接口', confidence: 80 };
  }

  function ensureSyntheticFeatureMap(featureKey) {
    const parts = featureKey.split('/');
    const featureName = parts[parts.length - 1] || featureKey;
    const sceneName = parts.length >= 3 ? parts[2] : '造价提交与审核';
    const cases = collectCostAuditCasesByFeature(featureName);
    if (!cases.length) return null;

    const scenariosMap = {};
    cases.forEach((item) => {
      const scenarioName = item.feature || '默认场景';
      if (!scenariosMap[scenarioName]) {
        scenariosMap[scenarioName] = { id: 'SC-' + scenarioName, name: scenarioName, coverageStatus: 'covered', caseCount: 0 };
      }
      scenariosMap[scenarioName].caseCount += 1;
    });
    const primaryApi = pickPrimaryApi(featureName, cases);
    const map = {
      meta: {
        mapId: 'fm_synth_' + featureName,
        featureKey: featureKey,
        version: '0.9.0-cost-audit',
        updatedAt: (global.__COST_AUDIT_DELIVERY__ && global.__COST_AUDIT_DELIVERY__.updatedAt) || '',
        dataSources: ['造价审核测试用例.xlsx', '造价审核接口文档']
      },
      spine: {
        domain: '家装',
        app: '报价',
        processName: '审核流程',
        featureName: featureName,
        sceneName: sceneName,
        valueTags: ['造价审核'],
        neighborFeatures: []
      },
      businessView: {
        scenarios: Object.keys(scenariosMap).map((key) => scenariosMap[key]),
        cases: cases.map((item) => ({
          id: item.id,
          name: item.name,
          priority: item.priority || 'P3',
          testScenario: item.feature || featureName,
          confidence: item.confidence || 90,
          mountAdvice: item.advice || '自动挂载',
          api: item.api || (primaryApi && primaryApi.label) || ''
        })),
        scripts: [],
        dataTemplates: [],
        executions: []
      },
      riskView: { rules: [], defects: [], tags: [{ type: 'chain', label: '造价审核', level: 'high' }] },
      techView: {
        source: '造价审核接口文档',
        services: [{ name: 'jz_quotation', role: 'primary' }, { name: 'hddp-admin', role: 'secondary' }],
        flowNodes: [{ name: sceneName, risk: '高', isPrimary: true }],
        apis: primaryApi ? [primaryApi] : [],
        tables: ['jz_design_cost', 'jz_design_cost_detail'],
        messages: ['designCostExchange'],
        codeModules: []
      },
      summary: {
        linkedCaseCount: cases.length,
        priorityDistribution: {},
        automationCoverage: 0,
        defectCount30d: 0,
        gapCount: 0,
        coverageStatus: 'covered'
      },
      consumers: [{ platform: '用例地图', usage: '造价审核挂载演示' }]
    };
    getSamples()[featureKey] = map;
    return map;
  }

  function openFeatureFromKey(featureKey, options) {
    const opts = options || {};
    const map = getMapByKey(featureKey);
    if (!map) {
      hooks.showToast('该功能点暂无地图数据（无样例且无挂载用例）');
      return;
    }
    const featureName = map.spine.featureName;
    const sceneName = map.spine.sceneName;
    const preferredMode = opts.viewMode || 'graph';
    mapState.currentFeatureKey = featureKey;
    mapState.detailViewMode = preferredMode;
    mapState.selectedCaseId = opts.caseId || null;
    hooks.openFeatureDetail(featureName, sceneName);
    setDetailViewMode(preferredMode);
    if (preferredMode === 'graph') renderDetailGraph(featureKey);
    else renderDetailMap(featureKey);
    if (opts.caseId) {
      hooks.showToast('已打开功能点图谱，并定位用例 ' + opts.caseId, { success: true });
    }
  }

  function renderDetailGraph(featureKey) {
    const map = getMapByKey(featureKey);
    if (!map || !global.P0GraphEngine) return false;
    global.P0GraphEngine.bindChrome();
    const mounted = global.P0GraphEngine.mount(map, {
      showToast: hooks.showToast,
      openFeatureKey: function(nextKey) {
        openFeatureFromKey(nextKey, { viewMode: 'graph' });
      }
    });
    if (mounted) {
      requestAnimationFrame(function() {
        global.P0GraphEngine.resize();
      });
    }
    return mounted;
  }

  function priorityClass(priority) {
    if (priority === 'P0') return 'fm-priority-p0';
    if (priority === 'P1') return 'fm-priority-p1';
    return '';
  }

  function tagHtml(label, level) {
    const cls = level === 'high' ? 'red' : level === 'medium' ? 'orange' : 'blue';
    return '<span class="tag ' + cls + '">' + hooks.escapeHtml(label) + '</span>';
  }

  function assetNodeHtml(options) {
    const hidden = options.hidden ? ' style="display:none"' : '';
    return '<div class="fm-asset-node fm-asset-node--' + options.type + (options.extraClass || '') + '" data-type="' + options.type + '"' +
      (options.dataAttrs || '') + hidden + '>' +
      '<div class="fm-asset-badge">' + hooks.escapeHtml(options.badge) + '</div>' +
      '<div class="fm-asset-body">' +
      '<div class="fm-asset-kind">' + options.kind + '</div>' +
      '<div class="fm-asset-title">' + hooks.escapeHtml(options.title) + '</div>' +
      (options.meta ? '<div class="fm-asset-meta">' + options.meta + '</div>' : '') +
      (options.bodyExtra || '') +
      '</div></div>';
  }

  function assetChipHtml(type, icon, text, extraAttrs) {
    return '<span class="fm-asset-chip fm-asset-chip--' + type + '"' + (extraAttrs || '') + '>' +
      '<span class="fm-chip-icon">' + hooks.escapeHtml(icon) + '</span>' +
      '<span class="fm-chip-text">' + hooks.escapeHtml(text) + '</span></span>';
  }

  function getCasesForScenario(business, scenario) {
    return (business.cases || []).filter((caseItem) =>
      caseItem.testScenario === scenario.name || caseItem.testScenarioId === scenario.id
    );
  }

  function renderSceneCaseList(cases) {
    if (!cases.length) return '<div class="fm-scene-case-empty">该场景下暂无用例</div>';
    return '<div class="fm-scene-case-list">' + cases.map((caseItem) =>
      '<div class="fm-scene-case-item" data-case-id="' + hooks.escapeHtml(caseItem.id) + '" role="button">' +
      '<span class="fm-scene-case-id">' + hooks.escapeHtml(caseItem.id) + '</span>' +
      '<span class="fm-scene-case-name">' + hooks.escapeHtml(caseItem.name) + '</span>' +
      '<span class="' + priorityClass(caseItem.priority) + '">' + hooks.escapeHtml(caseItem.priority) + '</span></div>'
    ).join('') + '</div>';
  }

  function bindDetailMapInteractions(map) {
    const panel = document.getElementById('detailMapPanel');
    if (!panel) return;
    panel.querySelectorAll('.fm-scene-node').forEach((node) => {
      node.addEventListener('click', (event) => {
        if (event.target.closest('.fm-scene-case-item')) return;
        const scenarioId = node.getAttribute('data-id');
        mapState.selectedScenarioId = mapState.selectedScenarioId === scenarioId ? null : scenarioId;
        mapState.selectedCaseId = null;
        renderDetailMap(mapState.currentFeatureKey);
      });
    });
    panel.querySelectorAll('.fm-scene-case-item').forEach((item) => {
      item.addEventListener('click', (event) => {
        event.stopPropagation();
        mapState.selectedCaseId = item.getAttribute('data-case-id');
        renderDetailMap(mapState.currentFeatureKey);
      });
    });
    panel.querySelectorAll('.fm-case-node').forEach((node) => {
      node.addEventListener('click', () => {
        mapState.selectedCaseId = node.getAttribute('data-id');
        renderDetailMap(mapState.currentFeatureKey);
      });
    });
  }

  function renderDetailMap(featureKey) {
    const host = document.getElementById('detailMapPanel');
    if (!host) return false;
    const map = getMapByKey(featureKey);
    mapState.currentFeatureKey = featureKey;
    if (!map) {
      host.innerHTML = '<div class="muted" style="padding:16px">当前功能点暂无 FeatureMapDTO 样例，仍使用经典视图。</div>';
      return false;
    }
    const business = map.businessView;
    const sceneHtml = (business.scenarios || []).map((item) => {
      const linkedCases = getCasesForScenario(business, item);
      const isSelected = mapState.selectedScenarioId === item.id;
      const statusText = item.coverageStatus === 'gap' ? '缺口场景' : item.coverageStatus === 'partial' ? '部分覆盖' : '已覆盖';
      return assetNodeHtml({
        type: 'scene',
        badge: 'SC',
        kind: statusText,
        title: item.name,
        meta: item.id + ' · ' + (linkedCases.length || item.caseCount || 0) + ' 条用例',
        extraClass: ' fm-scene-node' + (isSelected ? ' active' : ''),
        dataAttrs: ' data-id="' + hooks.escapeHtml(item.id) + '"',
        bodyExtra: isSelected ? renderSceneCaseList(linkedCases) : ''
      });
    }).join('');

    const selectedScenario = (business.scenarios || []).find((item) => item.id === mapState.selectedScenarioId);
    const caseHtml = (business.cases || []).map((item) => {
      const scenarioFiltered = selectedScenario && item.testScenario !== selectedScenario.name;
      const priorityLabel = '<span class="' + priorityClass(item.priority) + '">' + hooks.escapeHtml(item.priority) + '</span>';
      return assetNodeHtml({
        type: 'case',
        badge: 'TC',
        kind: '测试用例 · ' + hooks.escapeHtml(item.testScenario || '—'),
        title: item.name,
        meta: item.id + ' · ' + priorityLabel,
        extraClass: ' fm-case-node' + (mapState.selectedCaseId === item.id ? ' active' : ''),
        hidden: scenarioFiltered,
        dataAttrs: ' data-id="' + hooks.escapeHtml(item.id) + '"'
      });
    }).join('');

    const tech = map.techView || {};
    const risk = map.riskView || {};
    const summary = map.summary || {};

    host.innerHTML =
      '<div class="fm-asset-legend">' +
      '<span><i style="background:#8b5cf6">SC</i>场景</span>' +
      '<span><i style="background:#2563eb">TC</i>用例</span>' +
      '<span><i style="background:#16a34a">JS</i>脚本</span>' +
      '<span><i style="background:#0891b2">DT</i>数据</span>' +
      '<span><i style="background:#d97706">EX</i>执行</span></div>' +
      '<div class="fm-biz-row">' +
      '<div class="fm-col-box fm-col-box--scene"><div class="fm-col-hd"><strong>业务场景</strong></div><div>' + sceneHtml + '</div></div>' +
      '<div class="fm-col-box fm-col-box--case"><div class="fm-col-hd"><strong>测试用例</strong></div><div>' + caseHtml + '</div></div>' +
      '<div class="fm-col-box fm-col-box--script"><div class="fm-col-hd"><strong>自动化脚本</strong></div><div>' +
      (business.scripts || []).map((item) => assetNodeHtml({ type: 'script', badge: 'JS', kind: item.type, title: item.name, meta: item.linkedCaseId || '—' })).join('') +
      '</div></div>' +
      '<div class="fm-col-box fm-col-box--data"><div class="fm-col-hd"><strong>测试数据</strong></div><div>' +
      (business.dataTemplates || []).map((item) => assetNodeHtml({ type: 'data', badge: 'DT', kind: '数据模板', title: item.name, meta: item.id })).join('') +
      '</div></div>' +
      '<div class="fm-col-box fm-col-box--exec"><div class="fm-col-hd"><strong>执行历史</strong></div><div>' +
      (business.executions || []).map((item) => assetNodeHtml({ type: 'exec', badge: 'EX', kind: item.label, title: '执行结果', meta: item.lastRunAt || '—' })).join('') +
      '</div></div></div>' +
      '<div class="fm-bottom-row">' +
      '<div class="card" style="padding:12px"><div class="fm-section-hd">关联关系与风险</div>' +
      '<div class="muted" style="font-size:12px;margin-bottom:6px">业务规则</div><div>' +
      (risk.rules || []).map((item) => assetChipHtml('rule', 'R', item.id + ' ' + item.name)).join('') +
      '</div><div class="muted" style="font-size:12px;margin:12px 0 6px">历史缺陷</div><div>' +
      ((risk.defects || []).map((item) => assetChipHtml('defect', 'BG', item.id + ' ' + item.title)).join('') || assetChipHtml('defect', 'BG', '近 30 天无缺陷')) +
      '</div></div>' +
      '<div class="card" style="padding:12px"><div class="fm-section-hd">技术映射</div><div class="fm-tech-flow">' +
      '<div class="fm-tech-box"><h4>关联接口</h4>' +
      (tech.apis || []).map((item) => assetChipHtml('api', 'API', item.label)).join('') +
      '</div><div class="fm-tech-box"><h4>关联应用</h4>' +
      (tech.services || []).map((item) => assetChipHtml('service', 'S', item.name)).join('') +
      '</div></div></div></div>' +
      '<div class="fm-summary-strip">关联用例 <strong>' + (summary.linkedCaseCount || 0) +
      '</strong> · 自动化 <strong>' + Math.round((summary.automationCoverage || 0) * 100) + '%</strong>' +
      ' · 缺口场景 <strong>' + (summary.gapCount || 0) + '</strong></div>';

    bindDetailMapInteractions(map);
    return true;
  }

  function setDetailViewMode(mode) {
    const hasSample = !!getMapByKey(mapState.currentFeatureKey);
    let nextMode = mode;
    if ((nextMode === 'map' || nextMode === 'graph') && !hasSample) nextMode = 'classic';
    mapState.detailViewMode = nextMode;

    const classicPanel = document.getElementById('detailClassicPanel');
    const mapPanel = document.getElementById('detailMapPanel');
    const graphPanel = document.getElementById('detailGraphPanel');
    const asidePanel = document.querySelector('#view-detail .fmap-aside');
    const classicBtn = document.getElementById('btnDetailViewClassic');
    const mapBtn = document.getElementById('btnDetailViewMap');
    const graphBtn = document.getElementById('btnDetailViewGraph');
    const showMap = nextMode === 'map' && hasSample;
    const showGraph = nextMode === 'graph' && hasSample;

    if (classicPanel) classicPanel.classList.toggle('hidden', showMap || showGraph);
    if (asidePanel) asidePanel.classList.toggle('hidden', showMap || showGraph);
    if (mapPanel) mapPanel.classList.toggle('hidden', !showMap);
    if (graphPanel) graphPanel.classList.toggle('hidden', !showGraph);
    if (classicBtn) classicBtn.classList.toggle('active', nextMode === 'classic' || !hasSample);
    if (mapBtn) {
      mapBtn.classList.toggle('active', showMap);
      mapBtn.disabled = !hasSample;
      mapBtn.title = hasSample ? '' : '当前功能点暂无地图样例数据';
    }
    if (graphBtn) {
      graphBtn.classList.toggle('active', showGraph);
      graphBtn.disabled = !hasSample;
      graphBtn.title = hasSample ? '' : '当前功能点暂无图谱样例数据';
    }
    if (!showGraph && global.P0GraphEngine) global.P0GraphEngine.destroy();
  }

  function syncDetailView(featureName) {
    const featureKey = resolveFeatureKey(featureName);
    mapState.currentFeatureKey = featureKey;
    mapState.selectedScenarioId = null;
    mapState.selectedCaseId = null;
    const mapToggle = document.getElementById('detailViewToggle');
    if (mapToggle) mapToggle.style.display = featureKey ? '' : 'none';
    if (featureKey && mapState.detailViewMode === 'graph') {
      setDetailViewMode('graph');
      renderDetailGraph(featureKey);
    } else if (featureKey && mapState.detailViewMode === 'map') {
      renderDetailMap(featureKey);
      setDetailViewMode('map');
    } else {
      setDetailViewMode('classic');
      if (featureKey) renderDetailMap(featureKey);
    }
  }

  function getNodeCenter(node) {
    const size = nodeSize(node.kind || 'flow');
    return { x: node.x + size.w / 2, y: node.y + size.h / 2 };
  }

  function buildEdgePath(fromNode, toNode) {
    const fromSize = nodeSize(fromNode.kind || 'flow');
    const toSize = nodeSize(toNode.kind || 'flow');
    const fromCenter = getNodeCenter(fromNode);
    const toCenter = getNodeCenter(toNode);
    let fromPoint;
    let toPoint;
    if (Math.abs(toCenter.y - fromCenter.y) < 40) {
      fromPoint = { x: fromNode.x + fromSize.w, y: fromCenter.y };
      toPoint = { x: toNode.x, y: toCenter.y };
    } else if (toCenter.y > fromCenter.y) {
      fromPoint = { x: fromCenter.x, y: fromNode.y + fromSize.h };
      toPoint = { x: toCenter.x, y: toNode.y };
    } else {
      fromPoint = fromCenter;
      toPoint = toCenter;
    }
    const deltaX = toPoint.x - fromPoint.x;
    const deltaY = toPoint.y - fromPoint.y;
    if (Math.abs(deltaY) < 20) {
      const midX = fromPoint.x + deltaX * 0.5;
      return 'M' + fromPoint.x + ' ' + fromPoint.y + ' C' + midX + ' ' + fromPoint.y + ',' +
        midX + ' ' + toPoint.y + ',' + toPoint.x + ' ' + toPoint.y;
    }
    const midY = fromPoint.y + deltaY * 0.5;
    return 'M' + fromPoint.x + ' ' + fromPoint.y +
      ' C' + fromPoint.x + ' ' + midY + ',' + toPoint.x + ' ' + midY + ',' + toPoint.x + ' ' + toPoint.y;
  }

  function applyPanoramaTransform() {
    const inner = document.getElementById('panoramaInner');
    if (!inner) return;
    inner.style.transform = 'translate(' + mapState.panoramaPanX + 'px,' + mapState.panoramaPanY + 'px) scale(' + mapState.panoramaZoom + ')';
  }

  function resetPanoramaCamera() {
    mapState.panoramaZoom = 0.85;
    mapState.panoramaPanX = 24;
    mapState.panoramaPanY = 36;
    mapState.panoramaSelectedId = null;
  }

  function setPanoramaLevel(level, domainId, appId) {
    mapState.panoramaLevel = level;
    mapState.panoramaDomainId = domainId || null;
    mapState.panoramaAppId = appId || null;
    resetPanoramaCamera();
    renderPanorama();
  }

  function coverageLabel(coverage) {
    return COVERAGE_LABEL[coverage] || coverage;
  }

  function renderPanoramaHeader(title, guide, statsHtml) {
    const pageTitle = document.getElementById('panoramaPageTitle');
    const pageGuide = document.getElementById('panoramaPageGuide');
    const titleEl = document.getElementById('panoramaTitle');
    const updatedEl = document.getElementById('panoramaUpdated');
    const statsEl = document.getElementById('panoramaStats');
    const platform = getPlatform();
    if (pageTitle) pageTitle.textContent = title;
    if (pageGuide) pageGuide.textContent = guide;
    if (titleEl) titleEl.textContent = title;
    if (updatedEl) updatedEl.textContent = '更新于 ' + (platform.updatedAt || '—');
    if (statsEl) statsEl.innerHTML = statsHtml;
    renderPanoramaCrumb();
  }

  function renderPanoramaCrumb() {
    const crumb = document.getElementById('panoramaCrumb');
    if (!crumb) return;
    const domain = getDomainById(mapState.panoramaDomainId);
    const app = getAppById(mapState.panoramaDomainId, mapState.panoramaAppId);
    const parts = ['<button type="button" class="panorama-crumb-btn' + (mapState.panoramaLevel === 'platform' ? ' current' : '') + '" data-level="platform">平台全景</button>'];
    if (domain) {
      parts.push('<span>/</span><button type="button" class="panorama-crumb-btn' + (mapState.panoramaLevel === 'domain' ? ' current' : '') + '" data-level="domain" data-domain="' + hooks.escapeHtml(domain.id) + '">' + hooks.escapeHtml(domain.name) + '领域</button>');
    }
    if (app) {
      parts.push('<span>/</span><span class="panorama-crumb-current">' + hooks.escapeHtml(app.name) + '应用</span>');
    }
    crumb.innerHTML = parts.join('');
    crumb.querySelectorAll('[data-level]').forEach((button) => {
      button.addEventListener('click', () => {
        const level = button.getAttribute('data-level');
        if (level === 'platform') setPanoramaLevel('platform');
        else setPanoramaLevel('domain', button.getAttribute('data-domain'));
      });
    });
  }

  function drawGraph(nodes, edges, options) {
    const opts = options || {};
    const selectedId = mapState.panoramaSelectedId;
    const svg = document.getElementById('panoramaEdges');
    const nodeMap = {};
    nodes.forEach((node) => { nodeMap[node.id] = node; });
    if (svg) {
      const paths = (edges || []).map((edge) => {
        const fromNode = nodeMap[edge.from];
        const toNode = nodeMap[edge.to];
        if (!fromNode || !toNode) return '';
        const pathData = buildEdgePath(fromNode, toNode);
        const isHighlight = selectedId && (edge.from === selectedId || edge.to === selectedId);
        return '<path class="' + (isHighlight ? 'highlight' : (edge.type || 'main')) + '" d="' + pathData + '"></path>';
      }).join('');
      svg.innerHTML = '<defs><marker id="arrowMain" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#94a3b8"></path></marker></defs>' + paths;
    }
    const relatedIds = new Set();
    if (selectedId) {
      relatedIds.add(selectedId);
      (edges || []).forEach((edge) => {
        if (edge.from === selectedId) relatedIds.add(edge.to);
        if (edge.to === selectedId) relatedIds.add(edge.from);
      });
    }
    const nodesHost = document.getElementById('panoramaNodes');
    if (!nodesHost) return nodeMap;
    nodesHost.innerHTML = nodes.map((node) => {
      const kind = node.kind || 'flow';
      const isSelected = selectedId === node.id;
      const isDimmed = selectedId && !relatedIds.has(node.id);
      return '<div class="panorama-node ' + node.coverage + ' kind-' + kind + (isSelected ? ' selected' : '') + (isDimmed ? ' dimmed' : '') + '" data-id="' + hooks.escapeHtml(node.id) + '" data-kind="' + kind + '" style="left:' + node.x + 'px;top:' + node.y + 'px">' +
        '<div class="pn-status">' + coverageLabel(node.coverage) + '</div>' +
        '<div class="pn-name">' + hooks.escapeHtml(node.shortName || node.name) + '</div>' +
        (node.extraHtml || '') +
        '<div class="pn-meta">' + (node.metaHtml || ('<span>' + (node.caseCount || 0) + ' 条用例</span>')) + '</div></div>';
    }).join('');
    nodesHost.querySelectorAll('.panorama-node').forEach((element) => {
      element.addEventListener('click', (event) => {
        event.stopPropagation();
        const nodeId = element.getAttribute('data-id');
        const kind = element.getAttribute('data-kind');
        if (kind === 'domain') {
          if (mapState.panoramaLevel === 'platform') setPanoramaLevel('domain', nodeId);
          return;
        }
        if (kind === 'app') {
          setPanoramaLevel('app', mapState.panoramaDomainId, nodeId);
          return;
        }
        mapState.panoramaSelectedId = mapState.panoramaSelectedId === nodeId ? null : nodeId;
        renderPanorama();
      });
    });
    return nodeMap;
  }

  function renderPlatformLevel() {
    const platform = getPlatform();
    const summary = platform.summary || {};
    renderPanoramaHeader(
      '平台全景图',
      '先看领域，再进应用。不要把所有流程节点摊在一张图上。',
      '<span class="panorama-stat">领域 <strong>' + (summary.domainCount || 0) + '</strong></span>' +
      '<span class="panorama-stat">应用 <strong>' + (summary.appCount || 0) + '</strong></span>' +
      '<span class="panorama-stat">用例 <strong>' + (summary.totalCases || 0) + '</strong></span>'
    );
    const nodes = (platform.domains || []).map((domain) => ({
      id: domain.id,
      name: domain.name,
      shortName: domain.name,
      kind: 'domain',
      coverage: domain.coverage,
      caseCount: domain.caseCount,
      x: domain.x,
      y: domain.y,
      extraHtml: '<div class="pn-apps">' + hooks.escapeHtml((domain.apps || []).map((app) => app.name).join(' · ')) + '</div>',
      metaHtml: '<span>' + (domain.apps || []).length + ' 个应用</span><span>' + domain.caseCount + ' 条</span>'
    }));
    drawGraph(nodes, platform.domainEdges || []);
    const drawer = document.getElementById('panoramaDrawer');
    if (drawer) {
      drawer.classList.add('show');
      document.getElementById('drawerTitle').textContent = '怎么看多领域';
      document.getElementById('drawerMeta').textContent = '点击领域节点进入该领域下的应用图谱；再点应用进入流程节点全景。';
      const drawerCases = document.getElementById('drawerCases');
      if (drawerCases) drawerCases.innerHTML = '';
      document.getElementById('drawerFeatures').innerHTML = (platform.domains || []).map((domain) =>
        '<button type="button" class="fm-feature-link" data-domain="' + hooks.escapeHtml(domain.id) + '">进入' + hooks.escapeHtml(domain.name) + ' · ' + (domain.apps || []).map((app) => app.name).join(' / ') + '</button>'
      ).join('');
      document.querySelectorAll('#drawerFeatures [data-domain]').forEach((button) => {
        button.addEventListener('click', () => setPanoramaLevel('domain', button.getAttribute('data-domain')));
      });
      const openMapBtn = document.getElementById('btnDrawerOpenMap');
      if (openMapBtn) openMapBtn.style.display = 'none';
    }
    applyPanoramaTransform();
  }

  function renderDomainLevel() {
    const domain = getDomainById(mapState.panoramaDomainId);
    if (!domain) {
      setPanoramaLevel('platform');
      return;
    }
    const apps = domain.apps || [];
    const covered = apps.filter((item) => item.coverage === 'covered').length;
    const partial = apps.filter((item) => item.coverage === 'partial').length;
    const gap = apps.filter((item) => item.coverage === 'gap').length;
    renderPanoramaHeader(
      domain.name + '领域 · 应用全景',
      '该领域下的应用各自成簇。点应用进入流程节点图。',
      '<span class="panorama-stat covered">已覆盖 <strong>' + covered + '</strong></span>' +
      '<span class="panorama-stat partial">部分覆盖 <strong>' + partial + '</strong></span>' +
      '<span class="panorama-stat gap">缺口 <strong>' + gap + '</strong></span>' +
      '<span class="panorama-stat">用例 <strong>' + domain.caseCount + '</strong></span>'
    );
    const hub = {
      id: domain.id,
      name: domain.name,
      shortName: domain.name,
      kind: 'domain',
      coverage: domain.coverage,
      caseCount: domain.caseCount,
      x: 48,
      y: 140,
      metaHtml: '<span>' + apps.length + ' 个应用</span><span>' + domain.caseCount + ' 条</span>'
    };
    const appNodes = apps.map((app, index) => ({
      id: app.id,
      name: app.name,
      shortName: app.name,
      kind: 'app',
      coverage: app.coverage,
      caseCount: app.caseCount,
      x: 360,
      y: 36 + index * 108,
      metaHtml: '<span>' + app.nodeCount + ' 节点</span><span>' + app.caseCount + ' 条</span>'
    }));
    const edges = appNodes.map((appNode) => ({ from: hub.id, to: appNode.id, type: 'main' }));
    drawGraph([hub].concat(appNodes), edges);
    const drawer = document.getElementById('panoramaDrawer');
    if (drawer) {
      drawer.classList.add('show');
      document.getElementById('drawerTitle').textContent = domain.name + ' · ' + apps.length + ' 个应用';
      document.getElementById('drawerMeta').textContent = '点击右侧应用节点，进入该应用的流程全景。';
      const drawerCases = document.getElementById('drawerCases');
      if (drawerCases) drawerCases.innerHTML = '';
      document.getElementById('drawerFeatures').innerHTML = apps.map((app) =>
        '<button type="button" class="fm-feature-link" data-app="' + hooks.escapeHtml(app.id) + '">进入应用流程 · ' + hooks.escapeHtml(app.name) + '（' + coverageLabel(app.coverage) + '）</button>'
      ).join('');
      document.querySelectorAll('#drawerFeatures [data-app]').forEach((button) => {
        button.addEventListener('click', () => setPanoramaLevel('app', domain.id, button.getAttribute('data-app')));
      });
      const openMapBtn = document.getElementById('btnDrawerOpenMap');
      if (openMapBtn) openMapBtn.style.display = 'none';
    }
    applyPanoramaTransform();
  }

  function getCasesForFlowNode(node) {
    if (!node) return [];
    if (typeof hooks.getFormalCasesForNode === 'function') {
      const formal = hooks.getFormalCasesForNode(node) || [];
      if (formal.length) return formal;
    }
    // 全景不展示未确认缺口；仅投影已有样例/已确认链路
    const nodeName = node.name || '';
    const shortName = node.shortName || '';
    const fromDelivery = [];
    const quote = global.__QUOTE_MIN_DELIVERY__;
    const pushUnique = (list, allowUnconfirmed) => {
      (list || []).forEach((item) => {
        if (!allowUnconfirmed && item.status && item.status !== '已确认' && item.status !== '样例') return;
        const itemNode = item.node || item.scene || '';
        const matched = !itemNode ||
          itemNode === nodeName ||
          itemNode === shortName ||
          (nodeName.indexOf('造价') >= 0 && (itemNode.indexOf('造价') >= 0 || itemNode.indexOf('审核') >= 0));
        if (!matched) return;
        const id = item.id || item.caseId || '';
        if (fromDelivery.some((row) => row.id === id && row.name === (item.name || item.caseName))) return;
        fromDelivery.push({
          id: id,
          name: item.name || item.caseName || '未命名用例',
          priority: item.priority || item.level || '',
          feature: item.feature || '',
          api: item.api || '',
          status: item.status || '',
          confidence: item.confidence
        });
      });
    };
    if (quote) pushUnique(quote.caseLinks, false);
    if (fromDelivery.length) return fromDelivery;

    const samples = getSamples();
    const fromSamples = [];
    (node.featureKeys || []).forEach((featureKey) => {
      const sample = samples[featureKey];
      const cases = (((sample || {}).businessView) || {}).cases || [];
      cases.forEach((caseItem) => {
        fromSamples.push({
          id: caseItem.id || '',
          name: caseItem.name || caseItem.title || '未命名用例',
          priority: caseItem.priority || '',
          feature: (sample.spine && sample.spine.featureName) || featureKey.split('/').pop(),
          api: caseItem.api || ((sample.techView || {}).primaryApi) || '',
          status: caseItem.status || '样例',
          confidence: caseItem.confidence
        });
      });
    });
    return fromSamples;
  }

  function renderDrawerCases(node) {
    const host = document.getElementById('drawerCases');
    if (!host) return;
    const cases = getCasesForFlowNode(node);
    if (!cases.length) {
      host.innerHTML = '<div class="drawer-cases-hd">节点用例</div><div class="muted">暂无已确认正式资产。请到「资产生产」导入并评审确认后再回全景查看。</div>';
      return;
    }
    const sceneName = node.name || '造价提交与审核';
    host.innerHTML =
      '<div class="drawer-cases-hd">节点用例 <span class="tag">' + cases.length + '</span><span class="muted" style="font-weight:400">点击用例进入功能点地图</span></div>' +
      '<div class="drawer-case-list">' +
      cases.map((item) =>
        '<button type="button" class="drawer-case-item" data-case-id="' + hooks.escapeHtml(item.id || '') +
        '" data-feature="' + hooks.escapeHtml(item.feature || '') +
        '" data-scene="' + hooks.escapeHtml(sceneName) + '">' +
        '<div class="dc-top"><span class="dc-id">' + hooks.escapeHtml(item.id || '-') +
        (item.priority ? ' · ' + hooks.escapeHtml(item.priority) : '') +
        '</span><span class="tag">' + hooks.escapeHtml(item.status || '待确认') + '</span></div>' +
        '<div class="dc-name">' + hooks.escapeHtml(item.name) + '</div>' +
        '<div class="dc-meta">功能点：' + hooks.escapeHtml(item.feature || '—') +
        (item.confidence != null ? ' · 置信度 ' + item.confidence : '') + '</div>' +
        (item.api ? '<div class="dc-api">' + hooks.escapeHtml(item.api) + '</div>' : '') +
        '</button>'
      ).join('') +
      '</div>';
    host.querySelectorAll('.drawer-case-item[data-feature]').forEach((button) => {
      button.addEventListener('click', () => {
        const featureName = button.getAttribute('data-feature') || '';
        const scene = button.getAttribute('data-scene') || sceneName;
        const caseId = button.getAttribute('data-case-id') || '';
        const featureKey = '家装/报价/' + scene + '/' + featureName;
        openFeatureFromKey(featureKey, { caseId: caseId });
      });
    });
  }

  function featureKeysForNode(node) {
    const keys = (node.featureKeys || []).slice();
    const cases = getCasesForFlowNode(node);
    const sceneName = node.name || '造价提交与审核';
    cases.forEach((item) => {
      if (!item.feature) return;
      const key = '家装/报价/' + sceneName + '/' + item.feature;
      if (keys.indexOf(key) < 0) keys.push(key);
    });
    return keys;
  }

  function renderAppLevel() {
    const domain = getDomainById(mapState.panoramaDomainId);
    const app = getAppById(mapState.panoramaDomainId, mapState.panoramaAppId);
    const flow = getAppFlow(mapState.panoramaDomainId, mapState.panoramaAppId);
    const nodes = (flow.nodes || []).map((node) => Object.assign({}, node, { kind: 'flow' }));
    const summary = flow.summary || {};
    const covered = nodes.filter((item) => item.coverage === 'covered').length;
    const partial = nodes.filter((item) => item.coverage === 'partial').length;
    const gap = nodes.filter((item) => item.coverage === 'gap').length;
    renderPanoramaHeader(
      (domain ? domain.name + ' · ' : '') + (app ? app.name : '应用') + ' · 流程全景',
      '应用内流程节点一张网；点击节点查看挂载用例，并可下钻功能点地图。',
      '<span class="panorama-stat covered">已覆盖 <strong>' + (summary.coveredCount || covered) + '</strong></span>' +
      '<span class="panorama-stat partial">部分覆盖 <strong>' + (summary.partialCount || partial) + '</strong></span>' +
      '<span class="panorama-stat gap">缺口 <strong>' + (summary.gapCount || gap) + '</strong></span>' +
      '<span class="panorama-stat">用例 <strong>' + (summary.totalCases || (app && app.caseCount) || 0) + '</strong></span>'
    );
    const nodeMap = drawGraph(nodes, flow.edges || []);
    const drawer = document.getElementById('panoramaDrawer');
    const selectedId = mapState.panoramaSelectedId;
    if (!drawer) {
      applyPanoramaTransform();
      return;
    }
    if (!selectedId || !nodeMap[selectedId]) {
      drawer.classList.remove('show');
      const emptyCases = document.getElementById('drawerCases');
      if (emptyCases) emptyCases.innerHTML = '';
      applyPanoramaTransform();
      return;
    }
    const node = nodeMap[selectedId];
    drawer.classList.add('show');
    document.getElementById('drawerTitle').textContent = node.name;
    const caseCount = getCasesForFlowNode(node).length || node.caseCount || 0;
    document.getElementById('drawerMeta').innerHTML =
      coverageLabel(node.coverage) + ' · ' + caseCount + ' 条用例 · ' + (node.featureCount || 0) + ' 个功能点';
    renderDrawerCases(node);
    const featureKeys = featureKeysForNode(node);
    const samples = getSamples();
    document.getElementById('drawerFeatures').innerHTML = featureKeys.length
      ? '<div class="drawer-cases-hd" style="margin-top:4px">功能点地图 <span class="muted" style="font-weight:400">按功能点看场景/用例/接口</span></div>' + featureKeys.map((featureKey) => {
        const sample = samples[featureKey] || ensureSyntheticFeatureMap(featureKey);
        const name = sample ? sample.spine.featureName : featureKey.split('/').pop();
        const count = sample && sample.businessView ? (sample.businessView.cases || []).length : collectCostAuditCasesByFeature(name).length;
        return '<button type="button" class="fm-feature-link" data-key="' + hooks.escapeHtml(featureKey) + '">看功能点图谱 · ' + hooks.escapeHtml(name) +
          (count ? '（' + count + ' 条用例）' : '') + '</button>';
      }).join('')
      : '<div class="muted" style="margin-top:8px">该流程节点暂无功能点地图。</div>';
    document.querySelectorAll('.fm-feature-link[data-key]').forEach((button) => {
      button.addEventListener('click', () => openFeatureFromKey(button.getAttribute('data-key')));
    });
    const openMapBtn = document.getElementById('btnDrawerOpenMap');
    if (openMapBtn) {
      if (featureKeys.length === 1) {
        openMapBtn.style.display = '';
        openMapBtn.onclick = () => openFeatureFromKey(featureKeys[0]);
      } else {
        openMapBtn.style.display = 'none';
      }
    }
    applyPanoramaTransform();
    try { drawer.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); } catch (error) { /* ignore */ }
  }

  function renderPanorama() {
    if (mapState.panoramaLevel === 'app') renderAppLevel();
    else if (mapState.panoramaLevel === 'domain') renderDomainLevel();
    else renderPlatformLevel();
  }

  function bindPanoramaEvents() {
    const stage = document.getElementById('panoramaStage');
    if (!stage || stage.dataset.bound === '1') return;
    stage.dataset.bound = '1';
    stage.addEventListener('mousedown', (event) => {
      if (event.target.closest('.panorama-node') || event.target.closest('.panorama-zoom-ctrl')) return;
      mapState.panoramaDragging = true;
      mapState.panoramaDragStart = { x: event.clientX - mapState.panoramaPanX, y: event.clientY - mapState.panoramaPanY };
      stage.classList.add('dragging');
    });
    global.addEventListener('mousemove', (event) => {
      if (!mapState.panoramaDragging || !mapState.panoramaDragStart) return;
      mapState.panoramaPanX = event.clientX - mapState.panoramaDragStart.x;
      mapState.panoramaPanY = event.clientY - mapState.panoramaDragStart.y;
      applyPanoramaTransform();
    });
    global.addEventListener('mouseup', () => {
      mapState.panoramaDragging = false;
      mapState.panoramaDragStart = null;
      stage.classList.remove('dragging');
    });
    stage.addEventListener('wheel', (event) => {
      event.preventDefault();
      const delta = event.deltaY > 0 ? -0.06 : 0.06;
      mapState.panoramaZoom = Math.min(1.4, Math.max(0.45, mapState.panoramaZoom + delta));
      applyPanoramaTransform();
    }, { passive: false });
    stage.addEventListener('click', (event) => {
      if (event.target === stage || event.target.id === 'panoramaInner' || event.target.id === 'panoramaEdges') {
        mapState.panoramaSelectedId = null;
        renderPanorama();
      }
    });
    const zoomIn = document.getElementById('btnZoomIn');
    const zoomOut = document.getElementById('btnZoomOut');
    const zoomReset = document.getElementById('btnZoomReset');
    if (zoomIn) zoomIn.addEventListener('click', () => { mapState.panoramaZoom = Math.min(1.4, mapState.panoramaZoom + 0.1); applyPanoramaTransform(); });
    if (zoomOut) zoomOut.addEventListener('click', () => { mapState.panoramaZoom = Math.max(0.45, mapState.panoramaZoom - 0.1); applyPanoramaTransform(); });
    if (zoomReset) zoomReset.addEventListener('click', () => {
      mapState.panoramaZoom = 0.85;
      mapState.panoramaPanX = 24;
      mapState.panoramaPanY = 36;
      applyPanoramaTransform();
    });
    const regressionBtn = document.getElementById('btnDrawerRegression');
    if (regressionBtn) {
      regressionBtn.addEventListener('click', () => {
        if (mapState.panoramaLevel === 'platform') {
          const platform = getPlatform();
          hooks.showToast('已按平台生成跨领域回归包 · ' + (platform.summary.totalCases || 0) + ' 条用例', { success: true });
          return;
        }
        if (mapState.panoramaLevel === 'domain') {
          const domain = getDomainById(mapState.panoramaDomainId);
          if (domain) hooks.showToast('已按领域「' + domain.name + '」生成回归包 · ' + domain.caseCount + ' 条用例', { success: true });
          return;
        }
        const flow = getAppFlow(mapState.panoramaDomainId, mapState.panoramaAppId);
        const node = (flow.nodes || []).find((item) => item.id === mapState.panoramaSelectedId);
        const app = getAppById(mapState.panoramaDomainId, mapState.panoramaAppId);
        if (node) hooks.showToast('已按节点「' + node.shortName + '」生成回归包 · ' + node.caseCount + ' 条用例', { success: true });
        else if (app) hooks.showToast('已按应用「' + app.name + '」生成回归包 · ' + app.caseCount + ' 条用例', { success: true });
      });
    }
  }

  function bindDetailToggle() {
    const classicBtn = document.getElementById('btnDetailViewClassic');
    const mapBtn = document.getElementById('btnDetailViewMap');
    const graphBtn = document.getElementById('btnDetailViewGraph');
    if (classicBtn && classicBtn.dataset.bound !== '1') {
      classicBtn.dataset.bound = '1';
      classicBtn.addEventListener('click', () => setDetailViewMode('classic'));
    }
    if (mapBtn && mapBtn.dataset.bound !== '1') {
      mapBtn.dataset.bound = '1';
      mapBtn.addEventListener('click', () => {
        if (!mapState.currentFeatureKey) return;
        setDetailViewMode('map');
        renderDetailMap(mapState.currentFeatureKey);
      });
    }
    if (graphBtn && graphBtn.dataset.bound !== '1') {
      graphBtn.dataset.bound = '1';
      graphBtn.addEventListener('click', () => {
        if (!mapState.currentFeatureKey) return;
        setDetailViewMode('graph');
        renderDetailGraph(mapState.currentFeatureKey);
      });
    }
  }

  global.P0FeatureMap = {
    init: function init(options) {
      hooks = Object.assign(hooks, options || {});
      bindPanoramaEvents();
      bindDetailToggle();
    },
    renderPanorama: renderPanorama,
    renderDetailMap: renderDetailMap,
    renderDetailGraph: renderDetailGraph,
    syncDetailView: syncDetailView,
    resolveFeatureKey: resolveFeatureKey,
    openFeatureFromKey: openFeatureFromKey,
    setPanoramaLevel: setPanoramaLevel
  };
})(window);
