    function deriveTestScenario(item) {
      if (item.testScenario) return item.testScenario;
      const bag = (item.caseName + ' ' + item.step + ' ' + item.expected + ' ' + (item.gapReason || '')).toLowerCase();
      if (item.sourceType === 'AI缺口') return '缺口补齐';
      if (/异常|失败|驳回|拒绝|非法|不允许|错误/.test(bag)) return '异常校验';
      if (/边界|精度|四舍五入|为空|最大|最小|0值/.test(bag)) return '边界校验';
      if (/必填|校验|拦截|幂等|重复/.test(bag)) return '规则校验';
      return '主路径';
    }

    function ensureCatalogFields(item) {
      if (!item.testScenario) item.testScenario = deriveTestScenario(item);
      if (!item.system) item.system = item.app || '未命名系统';
      return item;
    }

    const catalogState = {
      scope: 'formal',
      filter: '',
      selectedId: '',
      expanded: {},
      roots: []
    };

    const CATALOG_LEVEL_LABEL = {
      domain: '业务领域',
      system: '系统',
      scene: '场景',
      feature: '功能点',
      testScenario: '测试场景',
      case: '用例'
    };

    function catalogItemPool() {
      const formal = typeof formalAssets === 'function' ? formalAssets({}) : [];
      if (catalogState.scope === 'formal') return formal.map(ensureCatalogFields);
      return reviewState.items
        .filter((item) => item.status !== '已废弃' && item.lifecycle !== '已归档')
        .map(ensureCatalogFields);
    }

    function buildCatalogTree(items) {
      const rootMap = {};
      items.forEach((item) => {
        const domainName = item.domain || '未命名领域';
        const systemName = item.system || item.app || '未命名系统';
        const sceneName = item.scene || '未命名场景';
        const featureName = item.feature || '未命名功能点';
        const scenarioName = item.testScenario || deriveTestScenario(item);
        if (!rootMap[domainName]) {
          rootMap[domainName] = { id: 'domain:' + domainName, level: 'domain', name: domainName, children: {}, cases: [] };
        }
        const domainNode = rootMap[domainName];
        domainNode.cases.push(item);
        if (!domainNode.children[systemName]) {
          domainNode.children[systemName] = { id: 'system:' + domainName + '/' + systemName, level: 'system', name: systemName, children: {}, cases: [] };
        }
        const systemNode = domainNode.children[systemName];
        systemNode.cases.push(item);
        if (!systemNode.children[sceneName]) {
          systemNode.children[sceneName] = { id: 'scene:' + domainName + '/' + systemName + '/' + sceneName, level: 'scene', name: sceneName, children: {}, cases: [] };
        }
        const sceneNode = systemNode.children[sceneName];
        sceneNode.cases.push(item);
        if (!sceneNode.children[featureName]) {
          sceneNode.children[featureName] = { id: 'feature:' + domainName + '/' + systemName + '/' + sceneName + '/' + featureName, level: 'feature', name: featureName, children: {}, cases: [] };
        }
        const featureNode = sceneNode.children[featureName];
        featureNode.cases.push(item);
        if (!featureNode.children[scenarioName]) {
          featureNode.children[scenarioName] = { id: 'testScenario:' + domainName + '/' + systemName + '/' + sceneName + '/' + featureName + '/' + scenarioName, level: 'testScenario', name: scenarioName, children: {}, cases: [] };
        }
        const scenarioNode = featureNode.children[scenarioName];
        scenarioNode.cases.push(item);
        const caseId = 'case:' + (item.id || item.caseName);
        scenarioNode.children[caseId] = { id: caseId, level: 'case', name: item.caseName, children: {}, cases: [item], caseItem: item };
      });
      function toList(mapNode) {
        return Object.keys(mapNode).sort((left, right) => left.localeCompare(right, 'zh')).map((key) => {
          const node = mapNode[key];
          return { id: node.id, level: node.level, name: node.name, count: node.cases.length, cases: node.cases, caseItem: node.caseItem || null, children: toList(node.children || {}) };
        });
      }
      return toList(rootMap);
    }

    function filterCatalogNodes(nodes, keyword) {
      if (!keyword) return nodes;
      const lower = keyword.toLowerCase();
      function matchNode(node) {
        const selfHit = String(node.name || '').toLowerCase().includes(lower);
        const children = (node.children || []).map(matchNode).filter(Boolean);
        if (selfHit || children.length) return Object.assign({}, node, { children: children.length ? children : node.children });
        return null;
      }
      return nodes.map(matchNode).filter(Boolean);
    }

    function findCatalogNode(nodes, nodeId) {
      for (let index = 0; index < nodes.length; index += 1) {
        const node = nodes[index];
        if (node.id === nodeId) return node;
        const found = findCatalogNode(node.children || [], nodeId);
        if (found) return found;
      }
      return null;
    }

    function collectCatalogPath(nodes, nodeId, trail) {
      const path = trail || [];
      for (let index = 0; index < nodes.length; index += 1) {
        const node = nodes[index];
        const next = path.concat([node]);
        if (node.id === nodeId) return next;
        const found = collectCatalogPath(node.children || [], nodeId, next);
        if (found) return found;
      }
      return null;
    }

    function setCatalogExpandAll(expanded) {
      function walk(nodes) {
        nodes.forEach((node) => {
          if (node.level !== 'case') catalogState.expanded[node.id] = expanded;
          walk(node.children || []);
        });
      }
      walk(catalogState.roots);
    }

    function defaultExpandCatalog(roots) {
      roots.forEach((domainNode) => {
        catalogState.expanded[domainNode.id] = true;
        (domainNode.children || []).forEach((systemNode) => { catalogState.expanded[systemNode.id] = true; });
      });
    }

    function renderCatalogTreeHtml(nodes) {
      return nodes.map((node) => {
        const hasChildren = node.level !== 'case' && (node.children || []).length > 0;
        const expanded = catalogState.expanded[node.id] === true;
        const active = catalogState.selectedId === node.id ? ' active' : '';
        const levelNo = ({ domain:1, system:2, scene:3, feature:4, testScenario:5, case:6 })[node.level] || '-';
        const toggle = hasChildren ? ('<span class="tree-toggle">' + (expanded ? '−' : '+') + '</span>') : '<span class="tree-toggle leaf">·</span>';
        const childrenHtml = hasChildren ? ('<div class="tree-children' + (expanded ? '' : ' collapsed') + '">' + renderCatalogTreeHtml(node.children) + '</div>') : '';
        return '<div class="tree-node">' +
          '<button type="button" class="tree-row' + active + '" data-catalog-id="' + escapeHtml(node.id) + '">' +
            toggle + '<span class="level-pill">L' + levelNo + '</span>' +
            '<span class="tree-label">' + escapeHtml(node.name) + '</span>' +
            (node.level === 'case' ? '' : ('<span class="tree-count">' + node.count + '</span>')) +
          '</button>' + childrenHtml + '</div>';
      }).join('');
    }

    function renderCatalogDetail(node, pathNodes) {
      const title = document.getElementById('catalogDetailTitle');
      const levelTag = document.getElementById('catalogDetailLevel');
      const body = document.getElementById('catalogDetailBody');
      if (!node) {
        title.textContent = '选择左侧节点';
        levelTag.textContent = '—';
        body.innerHTML = '<div class="empty">点击左侧目录节点，查看路径、下级统计与用例列表。</div>';
        return;
      }
      title.textContent = node.name;
      levelTag.textContent = CATALOG_LEVEL_LABEL[node.level] || node.level;
      const pathHtml = (pathNodes || []).map((entry) =>
        '<span>' + escapeHtml(CATALOG_LEVEL_LABEL[entry.level]) + ' · ' + escapeHtml(entry.name) + '</span>'
      ).join(' <span class="sep">/</span> ');
      const childSummary = {};
      (node.children || []).forEach((child) => { childSummary[child.level] = (childSummary[child.level] || 0) + 1; });
      const caseList = (node.level === 'case' ? [node.caseItem] : node.cases).filter(Boolean);
      body.innerHTML =
        '<div class="catalog-path">' + pathHtml + '</div>' +
        '<div class="catalog-meta">' +
          '<span class="tag blue">' + escapeHtml(CATALOG_LEVEL_LABEL[node.level]) + '</span>' +
          '<span class="tag">用例 ' + caseList.length + '</span>' +
          Object.keys(childSummary).map((levelName) =>
            '<span class="tag">' + escapeHtml(CATALOG_LEVEL_LABEL[levelName] || levelName) + ' ' + childSummary[levelName] + '</span>'
          ).join('') +
        '</div>' +
        (node.level === 'case' && node.caseItem
          ? ('<div class="list-row"><div>' +
              '<div class="result-title">' + escapeHtml(node.caseItem.caseName) +
              ' <span class="tag">' + escapeHtml(node.caseItem.status || '') + '</span></div>' +
              '<div class="muted mt-8">步骤：' + escapeHtml(node.caseItem.step || '-') + '</div>' +
              '<div class="muted mt-8">预期：' + escapeHtml(node.caseItem.expected || '-') + '</div>' +
              (node.caseItem.api ? ('<div class="muted mt-8">接口：' + escapeHtml(node.caseItem.api) + '</div>') : '') +
            '</div><div class="actions-col">' +
              '<button class="btn" type="button" data-view="assets">去正式资产库</button>' +
              '<button class="btn" type="button" data-view="detail">看功能点详情</button>' +
            '</div></div>')
          : ('<div class="section-title">下级 / 用例</div><div class="catalog-case-list">' +
              (caseList.length
                ? caseList.slice(0, 40).map((item) =>
                    '<div class="list-row"><div><div class="result-title">' + escapeHtml(item.caseName) +
                    ' <span class="tag">' + escapeHtml(item.testScenario || deriveTestScenario(item)) + '</span></div>' +
                    '<div class="result-path">' + escapeHtml((item.domain || '') + ' / ' + (item.app || '') + ' / ' +
                      (item.scene || '') + ' / ' + (item.feature || '')) + '</div></div></div>'
                  ).join('')
                : '<div class="empty">暂无用例</div>') + '</div>'));
    }

    function renderCatalogPage() {
      const treeBox = document.getElementById('catalogTree');
      if (!treeBox) return;
      const scopeSelect = document.getElementById('catalogScopeSelect');
      const filterInput = document.getElementById('catalogFilterInput');
      const sourceTag = document.getElementById('catalogSourceTag');
      if (scopeSelect) catalogState.scope = scopeSelect.value || catalogState.scope;
      if (filterInput) catalogState.filter = filterInput.value.trim();
      const pool = catalogItemPool();
      const roots = buildCatalogTree(pool);
      catalogState.roots = filterCatalogNodes(roots, catalogState.filter);
      if (!Object.keys(catalogState.expanded).length) defaultExpandCatalog(catalogState.roots);
      if (catalogState.filter) {
        (function expandMatched(nodes) {
          nodes.forEach((node) => {
            if (node.level !== 'case') catalogState.expanded[node.id] = true;
            expandMatched(node.children || []);
          });
        })(catalogState.roots);
      }
      const badgeCatalog = document.getElementById('badgeCatalog');
      if (badgeCatalog) badgeCatalog.textContent = String(pool.length);
      if (sourceTag) {
        sourceTag.textContent = catalogState.scope === 'formal' ? ('正式资产 ' + pool.length) : ('含草稿 ' + pool.length);
        sourceTag.className = pool.length ? 'tag green' : 'tag orange';
      }
      const pageDesc = document.getElementById('catalogPageDesc');
      if (pageDesc) {
        pageDesc.textContent = pool.length
          ? ('当前按「业务领域 → 系统 → 场景 → 功能点 → 测试场景 → 用例」组装了 ' + pool.length + ' 条资产。')
          : '暂无目录数据。请先导入并确认关键字、评审入库，或切换「含待评审草稿」。';
      }
      if (!catalogState.roots.length) {
        treeBox.innerHTML = '<div class="empty">暂无目录节点</div>';
        renderCatalogDetail(null, []);
        return;
      }
      treeBox.innerHTML = renderCatalogTreeHtml(catalogState.roots);
      let selected = findCatalogNode(catalogState.roots, catalogState.selectedId);
      if (!selected) {
        selected = catalogState.roots[0];
        catalogState.selectedId = selected.id;
        treeBox.innerHTML = renderCatalogTreeHtml(catalogState.roots);
      }
      renderCatalogDetail(selected, collectCatalogPath(catalogState.roots, selected.id) || []);
    }

