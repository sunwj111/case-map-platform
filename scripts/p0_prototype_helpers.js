    const platformState = {
      tenant: 'beike',
      role: 'admin',
      baseline: null,
      baselines: [],
      syncLogs: [
        { time: '今天 10:12', source: '质保用例库', detail: '增量 12 条，冲突 1 条已挂起' },
        { time: '昨天 18:40', source: '开发知识库', detail: 'Webhook 推送规则变更 3 条' },
        { time: '昨天 09:05', source: '需求系统', detail: '关联需求 RQ-2481 变更待分析' }
      ],
      syncSources: [
        { id: 'qa', name: '质保用例库', mode: 'API 增量', status: '已连接', last: '今天 10:12', conflict: 1 },
        { id: 'kb', name: '开发知识库', mode: 'Webhook', status: '已连接', last: '昨天 18:40', conflict: 0 },
        { id: 'req', name: '需求系统', mode: 'API 轮询', status: '待配置 Token', last: '—', conflict: 0 }
      ],
      lastImpact: []
    };

    function currentRole() { return platformState.role; }
    function canReview() { return currentRole() === 'admin' || currentRole() === 'reviewer'; }
    function canPublish() { return currentRole() === 'admin'; }
    function canWrite() { return currentRole() !== 'viewer'; }

    function roleLabel() {
      if (currentRole() === 'admin') return '领域管理员 · 可评审/发布';
      if (currentRole() === 'reviewer') return '评审人 · 可确认/调整，不可发基线';
      return '只读消费方 · 可检索/导出，不可改写';
    }

    function refreshRoleUI() {
      const pill = document.getElementById('rolePill');
      if (pill) {
        pill.textContent = roleLabel();
        pill.className = 'role-pill' + (currentRole() === 'viewer' ? ' muted' : (currentRole() === 'reviewer' ? ' warn' : ''));
      }
      const syncBanner = document.getElementById('syncPermBanner');
      if (syncBanner) {
        syncBanner.className = 'perm-banner' + (canPublish() ? ' ok' : '');
        syncBanner.textContent = canPublish()
          ? '当前角色可配置同步源并执行拉取。'
          : (canReview() ? '评审人可查看同步日志，配置与拉取需管理员。' : '只读角色仅可查看同步状态。');
      }
      const baseBanner = document.getElementById('baselinePermBanner');
      if (baseBanner) {
        baseBanner.className = 'perm-banner' + (canPublish() ? ' ok' : '');
        baseBanner.textContent = canPublish()
          ? '当前角色可发布基线与归档。'
          : '当前角色不可发布基线，仅可查看版本信息。';
      }
      const assetsBanner = document.getElementById('assetsPermBanner');
      if (assetsBanner) {
        assetsBanner.className = 'perm-banner' + (canWrite() ? ' ok' : '');
        assetsBanner.textContent = canWrite()
          ? '当前角色可导出回归包并下发执行。'
          : '只读角色可导出回归包，不可下发写回执行平台。';
      }
      const btnPublish = document.getElementById('btnPublishBaseline');
      const btnArchive = document.getElementById('btnArchiveOld');
      const btnSyncAll = document.getElementById('btnRunSyncAll');
      const btnSyncInc = document.getElementById('btnRunSyncInc');
      if (btnPublish) btnPublish.disabled = !canPublish();
      if (btnArchive) btnArchive.disabled = !canPublish();
      if (btnSyncAll) btnSyncAll.disabled = !canPublish();
      if (btnSyncInc) btnSyncInc.disabled = !canPublish();
      if (typeof renderReviewQueue === 'function') renderReviewQueue();
      if (typeof renderBaselinePage === 'function') renderBaselinePage();
      if (typeof renderSyncPage === 'function') renderSyncPage();
    }

    const _origRenderItemRow = typeof renderItemRow === 'function' ? renderItemRow : null;
    if (_origRenderItemRow) {
      // keep original; confirmItem already gated
    }

    function renderSyncPage() {
      const cards = document.getElementById('syncCards');
      const logList = document.getElementById('syncLogList');
      const logCount = document.getElementById('syncLogCount');
      if (!cards) return;
      cards.innerHTML = platformState.syncSources.map((source) =>
        '<div class="sync-card">' +
          '<h3>' + escapeHtml(source.name) + '</h3>' +
          '<div class="sync-meta">' + escapeHtml(source.mode) + ' · 上次 ' + escapeHtml(source.last) + '</div>' +
          '<div class="flex">' +
            '<span class="tag ' + (source.status.indexOf('已连接') >= 0 ? 'green' : 'orange') + '">' + escapeHtml(source.status) + '</span>' +
            (source.conflict ? ('<span class="tag orange">冲突 ' + source.conflict + '</span>') : '') +
          '</div>' +
          '<div class="flex mt-12">' +
            '<button class="btn" type="button" data-sync-id="' + source.id + '" ' + (canPublish() ? '' : 'disabled') + '>同步</button>' +
            '<button class="btn" type="button" data-view="impact">查看关联变更</button>' +
          '</div>' +
        '</div>'
      ).join('');
      logCount.textContent = String(platformState.syncLogs.length);
      logList.innerHTML = platformState.syncLogs.map((entry) =>
        '<li><strong>' + escapeHtml(entry.time) + '</strong> · ' + escapeHtml(entry.source) +
        '<br/>' + escapeHtml(entry.detail) + '</li>'
      ).join('');
    }

    function pushSyncLog(source, detail) {
      platformState.syncLogs.unshift({
        time: new Date().toLocaleString(),
        source: source,
        detail: detail
      });
      renderSyncPage();
    }

    function renderBaselinePage() {
      if (!document.getElementById('baselineCurrent')) return;
      const all = reviewState.items;
      const published = all.filter((item) => item.status === '已确认' && item.lifecycle !== '已归档');
      const reviewing = all.filter((item) => item.status !== '已确认' && item.status !== '已废弃');
      const archived = all.filter((item) => item.lifecycle === '已归档');
      document.getElementById('baselineCurrent').textContent = platformState.baseline
        ? platformState.baseline.name
        : '未发布';
      document.getElementById('baselinePublished').textContent = String(published.length);
      document.getElementById('baselineReviewing').textContent = String(reviewing.length);
      document.getElementById('baselineArchived').textContent = String(archived.length);
      const history = document.getElementById('baselineHistory');
      history.innerHTML = platformState.baselines.length
        ? platformState.baselines.map((base) =>
            '<div class="baseline-row">' +
              '<div><strong>' + escapeHtml(base.name) + '</strong>' +
              '<div class="muted">锁定 ' + base.count + ' 条 · ' + escapeHtml(base.at) + ' · ' + escapeHtml(base.by) + '</div></div>' +
              '<span class="tag green">已发布</span></div>'
          ).join('')
        : '<div class="muted">尚无基线。确认资产后点「发布当前基线」。</div>';
      const lifeList = document.getElementById('lifecycleList');
      const rows = all.filter((item) => item.status === '已确认' || item.lifecycle);
      lifeList.innerHTML = rows.length
        ? rows.slice(0, 30).map((item) =>
            '<div class="list-row"><div><div class="result-title">' + escapeHtml(item.caseName) +
            ' <span class="ver-tag">v' + escapeHtml(item.version || '—') + '</span>' +
            ' <span class="tag ' + lifecycleClass(item.lifecycle || '草稿') + '">' + escapeHtml(item.lifecycle || '草稿') + '</span></div>' +
            '<div class="result-path">' + escapeHtml(item.domain + ' / ' + item.app + ' / ' + item.feature) + '</div></div></div>'
          ).join('')
        : '<div class="muted">确认入库后可在此查看版本与生命周期。</div>';
    }

    function renderHealthPage() {
      if (!document.getElementById('healthAccuracy')) return;
      const confirmed = reviewState.items.filter((item) => item.status === '已确认');
      const pending = reviewState.items.filter((item) => item.status !== '已确认' && item.status !== '已废弃');
      const openGap = pending.filter((item) => item.sourceType === 'AI缺口').length;
      const lowConf = pending.filter((item) => {
        const confidence = Number(String(item.confidence || '0').replace('%', ''));
        return !Number.isNaN(confidence) && confidence > 0 && confidence < 80;
      }).length;
      const features = {};
      reviewState.items.forEach((item) => {
        const key = item.feature || '未挂载';
        if (!features[key]) features[key] = { total: 0, confirmed: 0 };
        features[key].total += 1;
        if (item.status === '已确认') features[key].confirmed += 1;
      });
      const featureKeys = Object.keys(features);
      const covered = featureKeys.filter((key) => features[key].confirmed > 0).length;
      const coverage = featureKeys.length ? Math.round(covered / featureKeys.length * 100) : 0;
      const accuracy = confirmed.length + pending.length
        ? Math.round(confirmed.length / (confirmed.length + Math.max(lowConf, 1)) * 100)
        : 0;
      document.getElementById('healthAccuracy').textContent = (confirmed.length ? accuracy : '—') + (confirmed.length ? '%' : '');
      document.getElementById('healthAccuracySub').textContent = confirmed.length
        ? ('已确认 ' + confirmed.length + ' · 低置信待抽检 ' + lowConf)
        : '生成并确认资产后计算';
      document.getElementById('healthCoverage').textContent = featureKeys.length ? (coverage + '%') : '—';
      document.getElementById('healthOpenGap').textContent = String(openGap);
      document.getElementById('healthLowConf').textContent = String(lowConf);
      const advice = [];
      if (!reviewState.generated) advice.push('尚未生成地图草稿，请先完成双导入。');
      if (openGap) advice.push('有 ' + openGap + ' 条 AI 缺口未关闭，建议本周清零。');
      if (lowConf) advice.push('有 ' + lowConf + ' 条低置信挂载待抽检。');
      if (platformState.baseline) advice.push('当前基线「' + platformState.baseline.name + '」可用于版本回归锁定。');
      else if (confirmed.length) advice.push('已有正式资产，建议发布一条基线供回归锁定。');
      if (!advice.length) advice.push('暂无运营风险，保持增量同步即可。');
      document.getElementById('healthAdvice').innerHTML = '<ul style="margin:0;padding-left:18px">' +
        advice.map((line) => '<li style="margin-bottom:6px">' + escapeHtml(line) + '</li>').join('') + '</ul>';
      document.getElementById('healthByFeature').innerHTML = featureKeys.length
        ? featureKeys.map((key) => {
            const row = features[key];
            return '<div class="baseline-row"><div><strong>' + escapeHtml(key) + '</strong>' +
              '<div class="muted">正式 ' + row.confirmed + ' / 总量 ' + row.total + '</div></div>' +
              '<span class="tag ' + (row.confirmed ? 'green' : 'orange') + '">' +
              (row.confirmed ? '已覆盖' : '未覆盖') + '</span></div>';
          }).join('')
        : '<div class="muted">暂无功能点数据。</div>';
    }

    function analyzeImpact() {
      const raw = document.getElementById('impactInput').value.trim();
      const type = document.getElementById('impactType').value;
      const box = document.getElementById('impactResult');
      if (!raw) {
        box.innerHTML = '<div class="muted">请输入变更内容。</div>';
        return;
      }
      const keyword = raw.toLowerCase();
      const assets = formalAssets({});
      const hit = assets.filter((item) => {
        const bag = (item.api + ' ' + item.feature + ' ' + item.scene + ' ' + item.caseName).toLowerCase();
        return bag.includes(keyword) || keyword.split(/[\s,/]+/).some((token) => token && bag.includes(token));
      });
      const fallback = hit.length ? hit : assets.filter((item) => (item.api || '').includes('/api/') || item.feature).slice(0, 5);
      platformState.lastImpact = fallback;
      box.innerHTML =
        '<div><strong>变更类型：</strong>' + escapeHtml(type) +
        '　<strong>输入：</strong>' + escapeHtml(raw) + '</div>' +
        '<div class="mt-8"><strong>受影响正式资产：</strong>' + fallback.length + ' 条（示意匹配）</div>' +
        (fallback.length
          ? '<div class="mt-8">' + fallback.map((item) =>
              '<div class="chip">' + escapeHtml(item.caseName) + ' · ' + escapeHtml(item.feature || '-') +
              (item.api ? (' · ' + escapeHtml(item.api)) : '') + '</div>'
            ).join('') + '</div>'
          : '<div class="muted mt-8">暂无正式资产可匹配，请先完成评审入库。</div>');
      showToast('已完成影响分析：' + fallback.length + ' 条', { success: true });
    }

    document.getElementById('roleSelect').addEventListener('change', (event) => {
      platformState.role = event.target.value;
      refreshRoleUI();
      showToast('已切换角色：' + roleLabel());
    });
    document.getElementById('tenantSelect').addEventListener('change', (event) => {
      platformState.tenant = event.target.value;
      showToast('已切换租户空间（数据隔离示意）');
    });
    document.getElementById('scopeDomain').addEventListener('change', (event) => {
      const domain = event.target.value;
      const filterDomain = document.getElementById('filterDomain');
      const importDomain = document.getElementById('importDomain');
      const assetsDomain = document.getElementById('assetsDomain');
      if (filterDomain) filterDomain.value = domain;
      if (importDomain) importDomain.value = domain;
      if (assetsDomain) assetsDomain.value = domain;
      refreshHint();
      if (typeof renderAssetsPage === 'function') renderAssetsPage();
      showToast('领域空间已切到「' + domain + '」');
    });

    document.getElementById('btnRunSyncAll').addEventListener('click', () => {
      if (!canPublish()) return;
      platformState.syncSources.forEach((source) => {
        source.last = new Date().toLocaleString();
        source.status = '已连接';
      });
      pushSyncLog('同步中心', '全量拉取完成：新增 8 · 更新 3 · 冲突 1');
      showToast('全量同步完成', { success: true });
    });
    document.getElementById('btnRunSyncInc').addEventListener('click', () => {
      if (!canPublish()) return;
      platformState.syncSources[0].last = new Date().toLocaleString();
      platformState.syncSources[0].conflict = 0;
      pushSyncLog('质保用例库', '增量同步完成：新增 2 条历史用例');
      showToast('增量同步完成', { success: true });
    });
    document.getElementById('syncCards').addEventListener('click', (event) => {
      const syncBtn = event.target.closest('[data-sync-id]');
      if (!syncBtn || !canPublish()) return;
      const sourceId = syncBtn.getAttribute('data-sync-id');
      const source = platformState.syncSources.find((row) => row.id === sourceId);
      if (!source) return;
      source.last = new Date().toLocaleString();
      source.status = '已连接';
      pushSyncLog(source.name, '单源同步成功');
      showToast(source.name + ' 同步成功', { success: true });
    });

    document.getElementById('btnPublishBaseline').addEventListener('click', () => {
      if (!canPublish()) return;
      const published = reviewState.items.filter((item) => item.status === '已确认' && item.lifecycle !== '已归档');
      if (!published.length) {
        showToast('没有可锁定的正式资产，请先评审确认');
        return;
      }
      const name = 'BL-' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '-' +
        String(platformState.baselines.length + 1).padStart(2, '0');
      const baseline = {
        name: name,
        count: published.length,
        at: new Date().toLocaleString(),
        by: '领域管理员',
        assetIds: published.map((item) => item.id)
      };
      platformState.baseline = baseline;
      platformState.baselines.unshift(baseline);
      published.forEach((item) => {
        item.baseline = name;
        item.lifecycle = '已发布';
      });
      renderBaselinePage();
      renderHealthPage();
      showToast('已发布基线 ' + name + '（' + published.length + ' 条）', { success: true });
    });
    document.getElementById('btnArchiveOld').addEventListener('click', () => {
      if (!canPublish()) return;
      const targets = reviewState.items.filter((item) =>
        item.status === '已确认' && item.baseline && platformState.baseline &&
        item.baseline !== platformState.baseline.name
      );
      if (!targets.length) {
        showToast('没有可归档的旧基线资产（示意：仅归档非当前基线）');
        return;
      }
      targets.forEach((item) => { item.lifecycle = '已归档'; });
      renderBaselinePage();
      showToast('已归档 ' + targets.length + ' 条旧基线资产', { success: true });
    });

    document.getElementById('btnAnalyzeImpact').addEventListener('click', analyzeImpact);
    document.getElementById('btnImpactToRegression').addEventListener('click', () => {
      if (!platformState.lastImpact.length) analyzeImpact();
      const assets = platformState.lastImpact || [];
      switchView('assets');
      const card = document.getElementById('regressionCard');
      const body = document.getElementById('regressionBody');
      const count = document.getElementById('regressionCount');
      const actions = document.getElementById('regressionActions');
      card.style.display = 'block';
      count.textContent = String(assets.length);
      if (actions) actions.style.display = assets.length ? 'flex' : 'none';
      body.innerHTML = assets.length
        ? ('<div class="hint">来自变更影响分析的建议回归包</div>' +
          assets.map((item) => '<div class="list-row"><div><div class="result-title">' +
            escapeHtml(item.caseName) + '</div><div class="result-path">' +
            escapeHtml(item.feature + ' · ' + (item.api || '-')) + '</div></div></div>').join(''))
        : '<div class="muted">暂无建议用例。</div>';
      showToast('已生成建议回归包 ' + assets.length + ' 条', { success: true });
    });

    document.getElementById('btnExportRegression').addEventListener('click', () => {
      const assets = platformState.lastImpact.length ? platformState.lastImpact : formalAssets(currentAssetsFilter());
      if (!assets.length) {
        showToast('没有可导出的回归包');
        return;
      }
      const header = ['用例名称', '领域', '应用', '场景', '功能点', '接口', '版本', '生命周期'];
      const lines = [header.join(',')].concat(assets.map((item) =>
        [item.caseName, item.domain, item.app, item.scene, item.feature, item.api, item.version || '', item.lifecycle || '']
          .map((cell) => '"' + String(cell || '').replace(/"/g, '""') + '"').join(',')
      ));
      const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'regression_pack_' + Date.now() + '.csv';
      link.click();
      URL.revokeObjectURL(url);
      document.getElementById('regressionExecHint').textContent = '已导出 CSV · ' + assets.length + ' 条';
      showToast('回归包已导出', { success: true });
    });
    document.getElementById('btnDispatchRegression').addEventListener('click', () => {
      if (!canWrite()) {
        showToast('只读角色不可下发执行');
        return;
      }
      const assets = platformState.lastImpact.length ? platformState.lastImpact : formalAssets(currentAssetsFilter());
      if (!assets.length) {
        showToast('没有可下发的回归包');
        return;
      }
      document.getElementById('regressionExecHint').textContent =
        '已下发执行平台 · 任务 REG-' + String(Date.now()).slice(-6) + ' · ' + assets.length + ' 条排队中';
      showToast('已下发执行（示意）', { success: true });
    });
    document.getElementById('btnWritebackRegression').addEventListener('click', () => {
      if (!canWrite()) {
        showToast('只读角色不可回写');
        return;
      }
      const assets = platformState.lastImpact.length ? platformState.lastImpact : formalAssets(currentAssetsFilter());
      if (!assets.length) {
        showToast('没有可回写的用例');
        return;
      }
      const failed = assets[0];
      failed.lastExec = '失败';
      failed.execNote = '断言失败：优惠券字段为空';
      document.getElementById('regressionExecHint').textContent =
        '回写完成：1 失败 / ' + Math.max(assets.length - 1, 0) + ' 通过 · 失败用例已标记待修正';
      showToast('已模拟失败回写：' + failed.caseName, {
        success: true,
        actionLabel: '去评审',
        actionView: 'review'
      });
    });
