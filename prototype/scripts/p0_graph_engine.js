/* P0 功能点图谱引擎（Cytoscape）· 供详情「图谱视图」复用 */
(function initP0GraphEngine(global) {
  const TYPE_COLOR = {
    feature: '#0f172a',
    neighbor: '#475569',
    scene: '#7c3aed',
    case: '#2563eb',
    api: '#0891b2',
    rule: '#d97706',
    script: '#16a34a',
    defect: '#dc2626'
  };

  const state = {
    cy: null,
    layout: 'concentric',
    sparse: true,
    showEdgeLabel: false,
    selectedNodeId: null,
    map: null,
    hooks: {}
  };

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function safeId(prefix, raw) {
    return prefix + ':' + String(raw).replace(/[^a-zA-Z0-9\u4e00-\u9fa5_-]/g, '_');
  }

  function shortLabel(text, maxLen) {
    const value = String(text || '');
    const limit = maxLen || 10;
    if (value.length <= limit) return value;
    return value.slice(0, limit - 1) + '…';
  }

  function pushNode(nodes, options) {
    nodes.push({
      data: {
        id: options.id,
        label: shortLabel(options.label, options.maxLen || 10),
        fullLabel: options.label,
        type: options.type,
        kind: options.kind,
        payload: options.payload || {}
      }
    });
  }

  function buildElements(map) {
    const nodes = [];
    const edges = [];
    const featureId = safeId('feature', map.meta.featureKey);
    pushNode(nodes, {
      id: featureId,
      label: map.spine.featureName,
      type: 'feature',
      kind: '功能点',
      maxLen: 12,
      payload: {
        scene: map.spine.sceneName,
        tags: map.spine.valueTags || [],
        cases: map.summary && map.summary.linkedCaseCount,
        owner: map.meta.qualityOwner,
        featureKey: map.meta.featureKey
      }
    });

    (map.spine.neighborFeatures || []).forEach((item) => {
      const id = safeId('neighbor', item.featureKey);
      pushNode(nodes, {
        id: id,
        label: item.featureName,
        type: 'neighbor',
        kind: '邻接功能点',
        payload: { featureKey: item.featureKey }
      });
      edges.push({ data: { id: 'e-' + id, source: featureId, target: id, relation: '同场景邻接' } });
    });

    (map.businessView.scenarios || []).forEach((item) => {
      const id = safeId('scene', item.id);
      pushNode(nodes, { id: id, label: item.name, type: 'scene', kind: '业务场景', payload: item });
      edges.push({ data: { id: 'e-' + id, source: featureId, target: id, relation: '覆盖场景' } });
    });

    (map.businessView.cases || []).forEach((item) => {
      const id = safeId('case', item.id);
      pushNode(nodes, { id: id, label: item.name, type: 'case', kind: '用例', maxLen: 11, payload: item });
      edges.push({ data: { id: 'e-' + id, source: featureId, target: id, relation: '挂载用例' } });
      const scene = (map.businessView.scenarios || []).find((row) => row.name === item.testScenario);
      if (scene) {
        edges.push({
          data: {
            id: 'e-case-scene-' + item.id,
            source: safeId('scene', scene.id),
            target: id,
            relation: '场景含用例'
          }
        });
      }
    });

    (map.businessView.scripts || []).forEach((item) => {
      const id = safeId('script', item.id);
      pushNode(nodes, { id: id, label: item.name, type: 'script', kind: '脚本', payload: item });
      const caseId = item.linkedCaseId ? safeId('case', item.linkedCaseId) : featureId;
      edges.push({ data: { id: 'e-' + id, source: caseId, target: id, relation: '可执行脚本' } });
    });

    (map.techView.apis || []).forEach((item, index) => {
      const id = safeId('api', index + '_' + item.path);
      const full = item.label || (item.method + ' ' + item.path);
      pushNode(nodes, { id: id, label: full, type: 'api', kind: '接口', maxLen: 14, payload: item });
      edges.push({ data: { id: 'e-' + id, source: featureId, target: id, relation: item.relation || '技术映射' } });
    });

    (map.riskView.rules || []).forEach((item) => {
      const id = safeId('rule', item.id);
      pushNode(nodes, { id: id, label: item.name, type: 'rule', kind: '规则', payload: item });
      edges.push({ data: { id: 'e-' + id, source: featureId, target: id, relation: '约束规则' } });
    });

    (map.riskView.defects || []).forEach((item) => {
      const id = safeId('defect', item.id);
      pushNode(nodes, { id: id, label: item.title, type: 'defect', kind: '缺陷', payload: item });
      edges.push({ data: { id: 'e-' + id, source: featureId, target: id, relation: '关联缺陷' } });
    });

    return { nodes: nodes, edges: edges, featureId: featureId };
  }

  function layoutOptions(name, featureId) {
    if (name === 'cose') {
      return {
        name: 'cose',
        animate: true,
        animationDuration: 420,
        nodeRepulsion: state.sparse ? 14000 : 8000,
        idealEdgeLength: state.sparse ? 160 : 120,
        padding: 40
      };
    }
    if (name === 'breadthfirst') {
      return {
        name: 'breadthfirst',
        directed: true,
        roots: [featureId],
        spacingFactor: state.sparse ? 1.7 : 1.35,
        animate: true,
        animationDuration: 400,
        padding: 36
      };
    }
    if (name === 'circle') {
      return { name: 'circle', animate: true, animationDuration: 400, padding: 48 };
    }
    return {
      name: 'concentric',
      animate: true,
      animationDuration: 420,
      minNodeSpacing: state.sparse ? 48 : 28,
      padding: 48,
      concentric: function(node) {
        if (node.data('type') === 'feature') return 10;
        if (node.data('type') === 'scene' || node.data('type') === 'neighbor') return 6;
        if (node.data('type') === 'case' || node.data('type') === 'api') return 4;
        return 2;
      },
      levelWidth: function() { return 2; }
    };
  }

  function showTip(node, renderedPosition) {
    const tip = document.getElementById('p0GraphTip');
    const wrap = document.getElementById('p0GraphWrap');
    if (!tip || !wrap) return;
    const box = wrap.getBoundingClientRect();
    tip.innerHTML =
      '<div class="tip-kind">' + escapeHtml(node.data('kind') || '') + '</div>' +
      '<div>' + escapeHtml(node.data('fullLabel') || node.data('label') || '') + '</div>';
    tip.style.display = 'block';
    tip.style.left = Math.min(renderedPosition.x + 14, box.width - 200) + 'px';
    tip.style.top = Math.max(8, renderedPosition.y - 18) + 'px';
  }

  function hideTip() {
    const tip = document.getElementById('p0GraphTip');
    if (tip) tip.style.display = 'none';
  }

  function renderDrawer(node) {
    const body = document.getElementById('p0GraphDrawerBody');
    if (!body) return;
    if (!node) {
      body.innerHTML = '<div class="muted">点击图上的节点，查看类型、全名与关联。</div>';
      return;
    }
    const data = node.data();
    const payload = data.payload || {};
    const connected = node.connectedEdges().map((edge) => {
      const other = edge.source().id() === node.id() ? edge.target() : edge.source();
      return {
        relation: edge.data('relation'),
        label: other.data('fullLabel') || other.data('label'),
        type: other.data('kind')
      };
    });
    body.innerHTML =
      '<div class="p0-graph-kv">' +
      '<div><span class="k">类型</span><div class="v"><span class="tag">' + escapeHtml(data.kind) + '</span></div></div>' +
      '<div><span class="k">名称</span><div class="v">' + escapeHtml(data.fullLabel || data.label) + '</div></div>' +
      '<div><span class="k">相连</span><div class="v">' +
      (connected.length
        ? connected.map((item) => escapeHtml(item.relation + ' → ' + item.label)).join('<br>')
        : '无') +
      '</div></div></div>' +
      '<div class="flex mt-12">' +
      (data.type === 'neighbor'
        ? '<button class="btn primary" type="button" id="p0GraphJumpFeature">切换功能点</button>'
        : '') +
      '<button class="btn" type="button" id="p0GraphFocusNode">放大邻域</button>' +
      '</div>';

    const jump = document.getElementById('p0GraphJumpFeature');
    if (jump) {
      jump.onclick = function() {
        if (payload.featureKey && typeof state.hooks.openFeatureKey === 'function') {
          state.hooks.openFeatureKey(payload.featureKey);
        }
      };
    }
    const focus = document.getElementById('p0GraphFocusNode');
    if (focus) {
      focus.onclick = function() {
        state.cy.animate({
          fit: { eles: node.closedNeighborhood(), padding: 70 },
          duration: 280
        });
      };
    }
  }

  function syncEdgeLabel() {
    if (!state.cy) return;
    state.cy.style().selector('edge').style('label', state.showEdgeLabel ? 'data(relation)' : '').update();
    const btn = document.getElementById('p0GraphEdgeLabel');
    if (btn) {
      btn.textContent = state.showEdgeLabel ? '边文字：开' : '边文字：关';
      btn.classList.toggle('active', !state.showEdgeLabel);
    }
  }

  function destroy() {
    hideTip();
    if (state.cy) {
      state.cy.destroy();
      state.cy = null;
    }
    state.selectedNodeId = null;
    state.map = null;
  }

  function mount(map, hooks) {
    if (!global.cytoscape) {
      const body = document.getElementById('p0GraphDrawerBody');
      if (body) body.innerHTML = '<div class="muted">图谱引擎未加载（需联网拉取 Cytoscape）。</div>';
      return false;
    }
    state.hooks = hooks || {};
    state.map = map;
    destroy();
    const container = document.getElementById('p0GraphCy');
    if (!container || !map) return false;

    const elements = buildElements(map);
    state.cy = global.cytoscape({
      container: container,
      elements: elements.nodes.concat(elements.edges),
      style: [
        {
          selector: 'node',
          style: {
            label: 'data(label)',
            'text-wrap': 'wrap',
            'text-max-width': 110,
            'font-size': 12,
            'font-weight': 600,
            'text-valign': 'bottom',
            'text-halign': 'center',
            'text-margin-y': 8,
            color: '#0f172a',
            'text-background-color': '#ffffff',
            'text-background-opacity': 0.92,
            'text-background-padding': 3,
            'text-background-shape': 'roundrectangle',
            'background-color': '#64748b',
            width: 56,
            height: 56,
            'border-width': 2,
            'border-color': '#fff'
          }
        },
        {
          selector: 'node[type = "feature"]',
          style: {
            'background-color': TYPE_COLOR.feature,
            width: 96,
            height: 96,
            'font-size': 14,
            'font-weight': 700,
            'text-valign': 'center',
            'text-margin-y': 0,
            color: '#fff',
            'text-background-opacity': 0,
            shape: 'round-rectangle',
            'text-max-width': 84
          }
        },
        { selector: 'node[type = "neighbor"]', style: { 'background-color': TYPE_COLOR.neighbor, shape: 'round-rectangle', width: 64, height: 44 } },
        { selector: 'node[type = "scene"]', style: { 'background-color': TYPE_COLOR.scene, shape: 'ellipse', width: 58, height: 58 } },
        { selector: 'node[type = "case"]', style: { 'background-color': TYPE_COLOR.case, shape: 'round-rectangle', width: 62, height: 44 } },
        { selector: 'node[type = "api"]', style: { 'background-color': TYPE_COLOR.api, shape: 'diamond', width: 54, height: 54 } },
        { selector: 'node[type = "rule"]', style: { 'background-color': TYPE_COLOR.rule, shape: 'round-rectangle', width: 56, height: 42 } },
        { selector: 'node[type = "script"]', style: { 'background-color': TYPE_COLOR.script, shape: 'round-rectangle', width: 58, height: 42 } },
        { selector: 'node[type = "defect"]', style: { 'background-color': TYPE_COLOR.defect, shape: 'vee', width: 52, height: 52 } },
        {
          selector: 'edge',
          style: {
            width: 1.8,
            'line-color': '#cbd5e1',
            'target-arrow-color': '#94a3b8',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            label: '',
            'font-size': 10,
            color: '#64748b',
            'text-background-color': '#fff',
            'text-background-opacity': 0.9,
            'text-background-padding': 2,
            'text-rotation': 'autorotate'
          }
        },
        { selector: 'node.highlighted', style: { 'border-width': 4, 'border-color': '#2563eb', 'z-index': 999 } },
        {
          selector: 'edge.highlighted',
          style: {
            width: 3,
            'line-color': '#2563eb',
            'target-arrow-color': '#2563eb',
            label: 'data(relation)',
            color: '#1d4ed8'
          }
        },
        { selector: '.faded', style: { opacity: 0.12 } }
      ],
      layout: layoutOptions(state.layout, elements.featureId),
      minZoom: 0.3,
      maxZoom: 2.8,
      wheelSensitivity: 0.22
    });

    state.cy.on('tap', 'node', function(event) {
      const node = event.target;
      state.selectedNodeId = node.id();
      state.cy.elements().removeClass('faded highlighted');
      node.addClass('highlighted');
      node.connectedEdges().addClass('highlighted');
      renderDrawer(node);
    });
    state.cy.on('tap', function(event) {
      if (event.target === state.cy) {
        state.selectedNodeId = null;
        state.cy.elements().removeClass('faded highlighted');
        renderDrawer(null);
        hideTip();
      }
    });
    state.cy.on('mouseover', 'node', function(event) {
      showTip(event.target, event.renderedPosition || event.target.renderedPosition());
    });
    state.cy.on('mousemove', 'node', function(event) {
      showTip(event.target, event.renderedPosition || event.target.renderedPosition());
    });
    state.cy.on('mouseout', 'node', hideTip);
    state.cy.on('dbltap', 'node[type = "neighbor"]', function(event) {
      const key = event.target.data('payload').featureKey;
      if (key && typeof state.hooks.openFeatureKey === 'function') state.hooks.openFeatureKey(key);
    });

    syncEdgeLabel();
    state.cy.one('layoutstop', function() { state.cy.fit(undefined, 56); });
    renderDrawer(null);
    const hint = document.getElementById('p0GraphHint');
    if (hint) {
      hint.innerHTML = '<strong>当前：</strong>' + escapeHtml(map.spine.featureName) +
        ' · 滚轮放大 · 悬停看全名 · 可点「放大选中 / 只留邻域」';
    }
    return true;
  }

  function bindChrome() {
    const root = document.getElementById('detailGraphPanel');
    if (!root || root.dataset.bound === '1') return;
    root.dataset.bound = '1';

    const layoutSelect = document.getElementById('p0GraphLayout');
    if (layoutSelect) {
      layoutSelect.addEventListener('change', function() {
        state.layout = layoutSelect.value;
        if (!state.cy || !state.map) return;
        const elements = buildElements(state.map);
        state.cy.layout(layoutOptions(state.layout, elements.featureId)).run();
        state.cy.one('layoutstop', function() { state.cy.fit(undefined, 56); });
      });
    }

    const edgeBtn = document.getElementById('p0GraphEdgeLabel');
    if (edgeBtn) {
      edgeBtn.addEventListener('click', function() {
        state.showEdgeLabel = !state.showEdgeLabel;
        syncEdgeLabel();
      });
    }

    const sparseBtn = document.getElementById('p0GraphSparse');
    if (sparseBtn) {
      sparseBtn.addEventListener('click', function() {
        state.sparse = !state.sparse;
        sparseBtn.textContent = state.sparse ? '更疏布局' : '更密布局';
        if (!state.cy || !state.map) return;
        const elements = buildElements(state.map);
        state.cy.layout(layoutOptions(state.layout, elements.featureId)).run();
      });
    }

    const focusBtn = document.getElementById('p0GraphFocus');
    if (focusBtn) {
      focusBtn.addEventListener('click', function() {
        if (!state.cy || !state.selectedNodeId) {
          if (state.hooks.showToast) state.hooks.showToast('请先点击一个节点');
          return;
        }
        const node = state.cy.getElementById(state.selectedNodeId);
        state.cy.animate({ fit: { eles: node.closedNeighborhood(), padding: 70 }, duration: 280 });
      });
    }

    const isolateBtn = document.getElementById('p0GraphIsolate');
    if (isolateBtn) {
      isolateBtn.addEventListener('click', function() {
        if (!state.cy || !state.selectedNodeId) {
          if (state.hooks.showToast) state.hooks.showToast('请先点击一个节点');
          return;
        }
        const node = state.cy.getElementById(state.selectedNodeId);
        state.cy.elements().addClass('faded');
        node.closedNeighborhood().removeClass('faded').addClass('highlighted');
        state.cy.animate({ fit: { eles: node.closedNeighborhood(), padding: 70 }, duration: 280 });
      });
    }

    const fitBtn = document.getElementById('p0GraphFit');
    if (fitBtn) fitBtn.addEventListener('click', function() { if (state.cy) state.cy.fit(undefined, 56); });
    const zoomIn = document.getElementById('p0GraphZoomIn');
    if (zoomIn) zoomIn.addEventListener('click', function() { if (state.cy) state.cy.zoom(state.cy.zoom() * 1.18); });
    const zoomOut = document.getElementById('p0GraphZoomOut');
    if (zoomOut) zoomOut.addEventListener('click', function() { if (state.cy) state.cy.zoom(state.cy.zoom() / 1.18); });
  }

  global.P0GraphEngine = {
    mount: mount,
    destroy: destroy,
    bindChrome: bindChrome,
    resize: function() {
      if (state.cy) {
        state.cy.resize();
        state.cy.fit(undefined, 56);
      }
    },
    isReady: function() { return !!global.cytoscape; }
  };
})(window);
