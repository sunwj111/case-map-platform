/* 主数据配置：按领域/应用上下文，Tab 维护流程边与技术映射 */
(function initP0ConfigPages(global) {
  const state = {
    tab: 'edges',
    domain: '家装',
    app: '报价'
  };

  const APP_OPTIONS = {
    '收房': ['评估', '收房签约', '交割'],
    '家装': ['报价', '量房', '设计', '合同'],
    '出房': ['房源', '带看', '出房签约'],
    '家服': ['保洁', '维修', '预约调度'],
    '灵之': ['智能报价', '方案推荐', '智能折扣'],
    '企信': ['消息触达', '审批流', '待办协同']
  };

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function getData() {
    return global.__QUOTE_MIN_DELIVERY__ || {
      domain: '家装', app: '报价',
      nodeEdges: [], featureApis: [], apiDownstreams: [], caseLinks: []
    };
  }

  function hasScopeData() {
    const data = getData();
    const domain = data.domain || '家装';
    const app = data.app || '报价';
    return state.domain === domain && state.app === app;
  }

  function statusTag(status) {
    if (status === '已确认') return '<span class="tag green">已确认</span>';
    if (status === '样例') return '<span class="tag blue">样例</span>';
    return '<span class="tag orange">待确认</span>';
  }

  function countByStatus(list) {
    const total = list.length;
    const confirmed = list.filter((item) => item.status === '已确认').length;
    return { total: total, confirmed: confirmed, pending: total - confirmed };
  }

  function emptyBody(message) {
    return '<tr><td colspan="9" class="muted">' + escapeHtml(message) + '</td></tr>';
  }

  function renderDomainEdges() {
    const body = document.getElementById('domainEdgeTableBody');
    const title = document.getElementById('masterEdgesTitle');
    if (title) title.textContent = state.domain + ' · ' + state.app + ' · 流程边';
    if (!body) return;
    if (!hasScopeData()) {
      body.innerHTML = emptyBody('「' + state.domain + ' / ' + state.app + '」暂无流程边主数据。先导入该应用材料后再确认。');
      return;
    }
    const rows = getData().nodeEdges || [];
    body.innerHTML = rows.map((item) =>
      '<tr data-id="' + escapeHtml(item.id) + '">' +
      '<td>' + escapeHtml(item.from) + '</td>' +
      '<td>' + escapeHtml(item.to) + '</td>' +
      '<td>' + escapeHtml(item.relation) + '</td>' +
      '<td>' + escapeHtml(item.strength) + '</td>' +
      '<td>' + escapeHtml(item.source) + '</td>' +
      '<td>' + statusTag(item.status) + '</td>' +
      '<td><button class="btn" type="button" data-confirm-edge="' + escapeHtml(item.id) + '">' +
      (item.status === '已确认' ? '取消确认' : '确认') + '</button></td></tr>'
    ).join('');
  }

  function renderFeatureApiTable() {
    const body = document.getElementById('featureApiTableBody');
    if (!body) return;
    if (!hasScopeData()) {
      body.innerHTML = emptyBody('当前领域/应用暂无功能点↔接口数据。');
      return;
    }
    const rows = getData().featureApis || [];
    body.innerHTML = rows.map((item) =>
      '<tr>' +
      '<td>' + escapeHtml(item.node) + '</td>' +
      '<td>' + escapeHtml(item.feature) + '</td>' +
      '<td>' + escapeHtml(item.method + ' ' + item.path) + '</td>' +
      '<td>' + escapeHtml(item.relation) + '</td>' +
      '<td>' + escapeHtml(item.controller) + '</td>' +
      '<td>' + statusTag(item.status) + '</td>' +
      '<td><button class="btn" type="button" data-confirm-fa="' + escapeHtml(item.id) + '">' +
      (item.status === '已确认' ? '取消确认' : '确认') + '</button></td></tr>'
    ).join('');
  }

  function renderApiDownstreamTable() {
    const body = document.getElementById('apiDownstreamTableBody');
    if (!body) return;
    if (!hasScopeData()) {
      body.innerHTML = emptyBody('当前领域/应用暂无接口下游数据。');
      return;
    }
    const rows = getData().apiDownstreams || [];
    body.innerHTML = rows.map((item) =>
      '<tr>' +
      '<td>' + escapeHtml(item.method + ' ' + item.path) + '</td>' +
      '<td>' + escapeHtml(item.system) + '</td>' +
      '<td>' + escapeHtml(item.target) + '</td>' +
      '<td>' + escapeHtml(item.callType) + '</td>' +
      '<td>' + escapeHtml(item.sync) + '</td>' +
      '<td>' + escapeHtml(item.strength) + '</td>' +
      '<td>' + escapeHtml(item.node) + '</td>' +
      '<td>' + statusTag(item.status) + '</td>' +
      '<td><button class="btn" type="button" data-confirm-ad="' + escapeHtml(item.id) + '">' +
      (item.status === '已确认' ? '取消确认' : '确认') + '</button></td></tr>'
    ).join('');
  }

  function renderCaseLinkPreview() {
    const body = document.getElementById('caseLinkPreviewBody');
    if (!body) return;
    if (!hasScopeData()) {
      body.innerHTML = emptyBody('当前领域/应用暂无用例挂载预览。');
      return;
    }
    const rows = getData().caseLinks || [];
    body.innerHTML = rows.map((item) =>
      '<tr>' +
      '<td>' + escapeHtml(item.id) + '</td>' +
      '<td>' + escapeHtml(item.name) + '</td>' +
      '<td>' + escapeHtml(item.node) + '</td>' +
      '<td>' + escapeHtml(item.feature) + '</td>' +
      '<td>' + escapeHtml(item.api) + '</td>' +
      '<td>' + statusTag(item.status) + '</td></tr>'
    ).join('');
  }

  function renderStats() {
    const statsEl = document.getElementById('masterDataStats');
    const hintEl = document.getElementById('masterScopeHint');
    if (!hasScopeData()) {
      if (statsEl) statsEl.textContent = state.domain + ' / ' + state.app + ' · 暂无主数据';
      if (hintEl) hintEl.textContent = '其他领域/应用先导入材料；菜单不会按领域膨胀。';
      return;
    }
    const data = getData();
    const edges = countByStatus(data.nodeEdges || []);
    const featureApis = countByStatus(data.featureApis || []);
    const downstream = countByStatus(data.apiDownstreams || []);
    const cases = (data.caseLinks || []).length;
    if (statsEl) {
      statsEl.innerHTML =
        escapeHtml(state.domain) + ' / ' + escapeHtml(state.app) +
        ' · 流程边 <strong>' + edges.confirmed + '/' + edges.total + '</strong>' +
        ' · 功能点接口 <strong>' + featureApis.confirmed + '/' + featureApis.total + '</strong>' +
        ' · 下游 <strong>' + downstream.confirmed + '/' + downstream.total + '</strong>' +
        ' · 用例预览 <strong>' + cases + '</strong>';
    }
    if (hintEl) {
      hintEl.textContent = (data.scope ? ('范围：' + data.scope + ' · ') : '') + '切换领域/应用不新增左侧菜单';
    }
  }

  function setMasterTab(tabName) {
    state.tab = tabName || 'edges';
    document.querySelectorAll('#masterDataTabs .tab-btn').forEach((btn) => {
      btn.classList.toggle('active', btn.getAttribute('data-master-tab') === state.tab);
    });
    const edgePanel = document.getElementById('masterTabEdges');
    const featurePanel = document.getElementById('masterTabFeatureApi');
    const downPanel = document.getElementById('masterTabDownstream');
    const casePanel = document.getElementById('masterTabCaseLink');
    if (edgePanel) edgePanel.classList.toggle('hidden', state.tab !== 'edges');
    if (featurePanel) featurePanel.classList.toggle('hidden', state.tab !== 'feature');
    if (downPanel) downPanel.classList.toggle('hidden', state.tab !== 'downstream');
    if (casePanel) casePanel.classList.toggle('hidden', state.tab !== 'case');
  }

  function fillAppOptions() {
    const appSelect = document.getElementById('masterApp');
    if (!appSelect) return;
    const apps = APP_OPTIONS[state.domain] || ['—'];
    appSelect.innerHTML = apps.map((name) =>
      '<option value="' + escapeHtml(name) + '"' + (name === state.app ? ' selected' : '') + '>' + escapeHtml(name) + '</option>'
    ).join('');
    if (apps.indexOf(state.app) < 0) state.app = apps[0];
    appSelect.value = state.app;
  }

  function toggleStatus(list, id) {
    const item = (list || []).find((row) => row.id === id);
    if (!item) return;
    item.status = item.status === '已确认' ? '待确认' : '已确认';
  }

  function renderAll() {
    renderStats();
    renderDomainEdges();
    renderFeatureApiTable();
    renderApiDownstreamTable();
    renderCaseLinkPreview();
    setMasterTab(state.tab);
  }

  function bindEvents(hooks) {
    const showToast = hooks.showToast || function(message) { global.alert(message); };
    const switchView = hooks.switchView || function() {};
    const root = document.getElementById('view-master-data');
    if (!root || root.dataset.bound === '1') return;
    root.dataset.bound = '1';

    root.addEventListener('click', (event) => {
      const tab = event.target.closest('[data-master-tab]');
      if (tab) {
        setMasterTab(tab.getAttribute('data-master-tab'));
        return;
      }
      const edgeBtn = event.target.closest('[data-confirm-edge]');
      if (edgeBtn) {
        toggleStatus(getData().nodeEdges, edgeBtn.getAttribute('data-confirm-edge'));
        renderAll();
        showToast('流程边状态已更新', { success: true });
        return;
      }
      const faBtn = event.target.closest('[data-confirm-fa]');
      if (faBtn) {
        toggleStatus(getData().featureApis, faBtn.getAttribute('data-confirm-fa'));
        renderAll();
        showToast('功能点↔接口 已更新', { success: true });
        return;
      }
      const adBtn = event.target.closest('[data-confirm-ad]');
      if (adBtn) {
        toggleStatus(getData().apiDownstreams, adBtn.getAttribute('data-confirm-ad'));
        renderAll();
        showToast('接口下游 已更新', { success: true });
      }
    });

    const domainSelect = document.getElementById('masterDomain');
    const appSelect = document.getElementById('masterApp');
    if (domainSelect) {
      domainSelect.addEventListener('change', () => {
        state.domain = domainSelect.value;
        fillAppOptions();
        renderAll();
      });
    }
    if (appSelect) {
      appSelect.addEventListener('change', () => {
        state.app = appSelect.value;
        renderAll();
      });
    }

    const confirmAll = document.getElementById('btnConfirmAllMaster');
    if (confirmAll) {
      confirmAll.addEventListener('click', () => {
        if (!hasScopeData()) {
          showToast('当前领域/应用暂无主数据可确认');
          return;
        }
        const data = getData();
        if (state.tab === 'edges') (data.nodeEdges || []).forEach((item) => { item.status = '已确认'; });
        if (state.tab === 'feature') (data.featureApis || []).forEach((item) => { item.status = '已确认'; });
        if (state.tab === 'downstream') (data.apiDownstreams || []).forEach((item) => { item.status = '已确认'; });
        if (state.tab === 'case') {
          showToast('用例正式确认请到资产生产');
          switchView('produce', { produceStep: 'review' });
          return;
        }
        renderAll();
        showToast('当前 Tab 已全部确认', { success: true });
      });
    }

    const openPanorama = document.getElementById('btnConfigOpenPanorama');
    if (openPanorama) openPanorama.addEventListener('click', () => switchView('panorama'));

    const openProduce = document.getElementById('btnConfigOpenProduce');
    if (openProduce) {
      openProduce.addEventListener('click', () => switchView('produce', { produceStep: 'review' }));
    }
  }

  global.P0ConfigPages = {
    init: function(options) {
      const data = getData();
      state.domain = data.domain || '家装';
      state.app = data.app || '报价';
      const domainSelect = document.getElementById('masterDomain');
      if (domainSelect) domainSelect.value = state.domain;
      fillAppOptions();
      bindEvents(options || {});
      setMasterTab('edges');
    },
    renderAll: renderAll,
    renderDomainEdges: renderDomainEdges,
    renderTechMap: function() {
      renderFeatureApiTable();
      renderApiDownstreamTable();
      renderCaseLinkPreview();
      renderStats();
    },
    setScope: function(domain, app) {
      if (domain) state.domain = domain;
      if (app) state.app = app;
      const domainSelect = document.getElementById('masterDomain');
      if (domainSelect) domainSelect.value = state.domain;
      fillAppOptions();
      renderAll();
    }
  };
})(window);
