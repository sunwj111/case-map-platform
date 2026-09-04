#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build P0-enhanced prototype from the original HTML, keeping original intact."""

from pathlib import Path

SRC = Path("/Users/sunwenjing/case-map-platform/prototype/用例地图_家装报价_原型.html")
DST = Path("/Users/sunwenjing/case-map-platform/prototype/用例地图_公司平台_P0增强原型.html")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"missing: {label}")


def main() -> None:
    text = SRC.read_text(encoding="utf-8")

    text = text.replace(
        "<title>用例地图平台原型 · 检索优先</title>",
        "<title>用例地图平台原型 · 公司级 P0 增强</title>",
        1,
    )

    extra_css = """
    .topbar {
      display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap;
      background: #fff; border-bottom: 1px solid var(--line); padding: 10px 18px;
      position: sticky; top: 0; z-index: 40;
    }
    .topbar-left, .topbar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .topbar label { font-size: 12px; color: var(--muted); }
    .topbar select {
      border: 1px solid var(--line); border-radius: 8px; padding: 6px 10px; font-size: 12px; background: #fff;
    }
    .role-pill {
      display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px;
      background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe; font-size: 12px; font-weight: 600;
    }
    .role-pill.warn { background: #fff7ed; color: #c2410c; border-color: #fed7aa; }
    .role-pill.muted { background: #f1f5f9; color: #475569; border-color: #e2e8f0; }
    .ver-tag {
      display: inline-block; margin-left: 6px; padding: 1px 7px; border-radius: 999px;
      background: #f8fafc; border: 1px solid #e2e8f0; color: #475569; font-size: 11px;
    }
    .life-draft { background: #f1f5f9; color: #475569; }
    .life-review { background: #fff7ed; color: #c2410c; }
    .life-published { background: #ecfdf5; color: #166534; }
    .life-archived { background: #fef2f2; color: #b91c1c; }
    .sync-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
    .sync-card {
      border: 1px solid var(--line); border-radius: 12px; padding: 14px; background: #fff;
    }
    .sync-card h3 { margin: 0 0 6px; font-size: 14px; }
    .sync-meta { color: var(--muted); font-size: 12px; margin-bottom: 10px; }
    .timeline { border-left: 2px solid #e2e8f0; padding-left: 12px; margin: 0; padding-top: 0; }
    .timeline li { list-style: none; margin: 0 0 10px; color: var(--muted); font-size: 12px; }
    .timeline strong { color: var(--text); }
    .perm-banner {
      margin-bottom: 12px; padding: 10px 12px; border-radius: 10px;
      background: #fff7ed; border: 1px solid #fed7aa; color: #9a3412; font-size: 12px;
    }
    .perm-banner.ok { background: #ecfdf5; border-color: #bbf7d0; color: #166534; }
    .baseline-row {
      display: flex; justify-content: space-between; gap: 12px; align-items: center;
      padding: 12px 0; border-bottom: 1px solid var(--line);
    }
    .baseline-row:last-child { border-bottom: 0; }
    .impact-box {
      border: 1px solid #bfdbfe; background: #eff6ff; border-radius: 10px; padding: 12px; margin-top: 10px;
    }
    body > .shell { min-height: calc(100vh - 52px); }
    @media (max-width: 1100px) {
      .sync-grid { grid-template-columns: 1fr; }
    }
"""
    text = text.replace("</style>", extra_css + "\n  </style>", 1)

    old_body = """<body>
  <div class="shell">
    <aside class="sider">
      <div class="logo">用例地图平台
        <div class="logo-sub">推荐路径：导入 → 评审 → 正式资产库</div>
      </div>
      <div class="menu-group">工作台</div>
      <button class="menu-btn active" data-view="overview"><span class="menu-label">总览检索</span></button>
      <button class="menu-btn" data-view="detail"><span class="menu-label">功能点详情</span></button>
      <button class="menu-btn" data-view="search"><span class="menu-label">检索结果</span></button>
      <div class="menu-group">资产生产（主流程）</div>
      <button class="menu-btn" data-view="import"><span class="menu-label">1. 资产导入</span></button>
      <button class="menu-btn" data-view="review"><span class="menu-label">2. 评审校准</span><span class="badge" id="badgeReview">0</span></button>
      <button class="menu-btn" data-view="assets"><span class="menu-label">3. 正式资产库</span><span class="badge" id="badgeAssets">0</span></button>
    </aside>"""

    new_body = """<body>
  <div class="topbar">
    <div class="topbar-left">
      <strong>公司用例地图 · P0 增强原型</strong>
      <span class="tag blue">相对原版新增治理能力</span>
      <label>租户</label>
      <select id="tenantSelect">
        <option value="beike">贝壳家装事业线</option>
        <option value="lease">租赁事业线</option>
        <option value="aftersale">售后事业线</option>
      </select>
      <label>领域空间</label>
      <select id="scopeDomain">
        <option>家装</option>
        <option>租赁</option>
        <option>售后</option>
      </select>
    </div>
    <div class="topbar-right">
      <label>角色</label>
      <select id="roleSelect">
        <option value="admin">领域管理员</option>
        <option value="reviewer">评审人</option>
        <option value="viewer">只读消费方</option>
      </select>
      <span class="role-pill" id="rolePill">领域管理员 · 可评审/发布</span>
      <button class="btn" type="button" data-view="health">健康看板</button>
    </div>
  </div>
  <div class="shell">
    <aside class="sider">
      <div class="logo">用例地图平台
        <div class="logo-sub">原版主流程保留 · 本版补齐 P0 治理</div>
      </div>
      <div class="menu-group">工作台</div>
      <button class="menu-btn active" data-view="overview"><span class="menu-label">总览检索</span></button>
      <button class="menu-btn" data-view="detail"><span class="menu-label">功能点详情</span></button>
      <button class="menu-btn" data-view="search"><span class="menu-label">检索结果</span></button>
      <button class="menu-btn" data-view="health"><span class="menu-label">健康看板</span></button>
      <div class="menu-group">资产生产（主流程）</div>
      <button class="menu-btn" data-view="import"><span class="menu-label">1. 资产导入</span></button>
      <button class="menu-btn" data-view="review"><span class="menu-label">2. 评审校准</span><span class="badge" id="badgeReview">0</span></button>
      <button class="menu-btn" data-view="assets"><span class="menu-label">3. 正式资产库</span><span class="badge" id="badgeAssets">0</span></button>
      <div class="menu-group">P0 治理与集成</div>
      <button class="menu-btn" data-view="sync"><span class="menu-label">同步中心</span></button>
      <button class="menu-btn" data-view="baseline"><span class="menu-label">版本与基线</span></button>
      <button class="menu-btn" data-view="impact"><span class="menu-label">变更影响</span></button>
    </aside>"""
    require(text, old_body, "sidebar block")
    text = text.replace(old_body, new_body, 1)

    old_assets_hd = """          <div class="flex">
            <button class="btn" data-view="review">回评审校准</button>
            <button class="btn primary" id="btnBuildRegression" type="button">生成回归包</button>
          </div>"""
    new_assets_hd = """          <div class="flex">
            <button class="btn" data-view="review">回评审校准</button>
            <button class="btn" data-view="baseline" type="button">版本与基线</button>
            <button class="btn primary" id="btnBuildRegression" type="button">生成回归包</button>
          </div>
          <div class="perm-banner ok" id="assetsPermBanner">当前角色可导出回归包并下发执行。</div>"""
    require(text, old_assets_hd, "assets header")
    text = text.replace(old_assets_hd, new_assets_hd, 1)

    old_reg = """        <div class="card mt-12" id="regressionCard" style="display:none">
          <div class="card-hd"><span>回归包预览</span><span class="tag green" id="regressionCount">0</span></div>
          <div class="card-bd" id="regressionBody"></div>
        </div>"""
    new_reg = """        <div class="card mt-12" id="regressionCard" style="display:none">
          <div class="card-hd">
            <span>回归包预览与下发</span>
            <span class="tag green" id="regressionCount">0</span>
          </div>
          <div class="card-bd">
            <div id="regressionBody"></div>
            <div class="flex mt-12" id="regressionActions" style="display:none">
              <button class="btn primary" type="button" id="btnExportRegression">导出 Excel</button>
              <button class="btn" type="button" id="btnDispatchRegression">下发执行平台</button>
              <button class="btn" type="button" id="btnWritebackRegression">模拟失败回写</button>
              <span class="muted" id="regressionExecHint">导出 / 下发 / 回写为 P0 闭环示意</span>
            </div>
          </div>
        </div>"""
    require(text, old_reg, "regression card")
    text = text.replace(old_reg, new_reg, 1)

    new_views = """
      <section class="view" id="view-sync">
        <div class="path-callout">
          <strong>P0 新增</strong>
          <span>对接质保 / 知识库 / 需求系统的增量同步与冲突处理示意</span>
        </div>
        <div class="page-hd">
          <div>
            <h1>同步中心</h1>
            <p class="page-guide">替代「仅本地上传」。可配置 API / Webhook，查看最近增量与冲突。</p>
          </div>
          <div class="flex">
            <button class="btn primary" type="button" id="btnRunSyncAll">立即全量拉取</button>
            <button class="btn" type="button" id="btnRunSyncInc">增量同步</button>
          </div>
        </div>
        <div class="perm-banner" id="syncPermBanner"></div>
        <div class="sync-grid" id="syncCards"></div>
        <div class="card mt-12">
          <div class="card-hd"><span>最近同步日志</span><span class="tag" id="syncLogCount">0</span></div>
          <div class="card-bd"><ul class="timeline" id="syncLogList"></ul></div>
        </div>
      </section>

      <section class="view" id="view-baseline">
        <div class="path-callout">
          <strong>P0 新增</strong>
          <span>用例版本号 · 发布基线 · 生命周期（草稿→评审→已发布→归档）</span>
        </div>
        <div class="page-hd">
          <div>
            <h1>版本与基线</h1>
            <p class="page-guide">正式资产确认后进入「已发布」；可打基线供版本回归锁定。</p>
          </div>
          <div class="flex">
            <button class="btn primary" type="button" id="btnPublishBaseline">发布当前基线</button>
            <button class="btn" type="button" id="btnArchiveOld">归档旧版本</button>
          </div>
        </div>
        <div class="perm-banner" id="baselinePermBanner"></div>
        <div class="grid-4">
          <div class="stat"><div class="label">当前基线</div><div class="value" id="baselineCurrent" style="font-size:18px">未发布</div></div>
          <div class="stat"><div class="label">已发布资产</div><div class="value" id="baselinePublished">0</div></div>
          <div class="stat"><div class="label">评审中</div><div class="value" id="baselineReviewing">0</div></div>
          <div class="stat"><div class="label">已归档</div><div class="value" id="baselineArchived">0</div></div>
        </div>
        <div class="card mt-12">
          <div class="card-hd"><span>基线历史</span></div>
          <div class="card-bd" id="baselineHistory"></div>
        </div>
        <div class="card mt-12">
          <div class="card-hd"><span>资产生命周期一览</span></div>
          <div class="card-bd" id="lifecycleList"></div>
        </div>
      </section>

      <section class="view" id="view-health">
        <div class="path-callout">
          <strong>运营增强</strong>
          <span>挂载准确率、覆盖率、抽检待办与回流修正入口</span>
        </div>
        <div class="page-hd">
          <div>
            <h1>地图健康看板</h1>
            <p class="page-guide">面向领域负责人：看地图是否可信、可消费。</p>
          </div>
          <button class="btn" data-view="review" type="button">去处理抽检</button>
        </div>
        <div class="grid-4">
          <div class="stat"><div class="label">挂载准确率（抽检）</div><div class="value" id="healthAccuracy">—</div><div class="sub" id="healthAccuracySub">待计算</div></div>
          <div class="stat"><div class="label">功能点覆盖率</div><div class="value" id="healthCoverage">—</div><div class="sub">有正式用例的功能点占比</div></div>
          <div class="stat"><div class="label">缺口未关闭</div><div class="value" id="healthOpenGap">0</div><div class="sub">AI 缺口待确认/废弃</div></div>
          <div class="stat"><div class="label">低置信待抽检</div><div class="value" id="healthLowConf">0</div><div class="sub">&lt; 80%</div></div>
        </div>
        <div class="grid-2 mt-12">
          <div class="card">
            <div class="card-hd"><span>本周运营建议</span></div>
            <div class="card-bd" id="healthAdvice"></div>
          </div>
          <div class="card">
            <div class="card-hd"><span>按功能点覆盖</span></div>
            <div class="card-bd" id="healthByFeature"></div>
          </div>
        </div>
      </section>

      <section class="view" id="view-impact">
        <div class="path-callout">
          <strong>P1 预览</strong>
          <span>需求 / 接口变更 → 受影响用例与缺口推荐</span>
        </div>
        <div class="page-hd">
          <div>
            <h1>变更影响分析</h1>
            <p class="page-guide">输入变更单或接口，自动圈选受影响正式资产与建议回归包。</p>
          </div>
        </div>
        <div class="card">
          <div class="card-bd">
            <div class="form-grid">
              <div class="form-field">
                <label>变更类型</label>
                <select id="impactType">
                  <option value="api">接口变更</option>
                  <option value="req">需求变更</option>
                  <option value="code">代码提交</option>
                </select>
              </div>
              <div class="form-field full">
                <label>变更内容</label>
                <input id="impactInput" placeholder="例如：/api/quote/calc 增加优惠券字段" />
              </div>
            </div>
            <div class="flex mt-12">
              <button class="btn primary" type="button" id="btnAnalyzeImpact">分析影响面</button>
              <button class="btn" type="button" id="btnImpactToRegression">一键生成建议回归包</button>
            </div>
            <div class="impact-box" id="impactResult">
              <div class="muted">输入变更后点击分析，将基于正式资产中的接口/功能点做示意匹配。</div>
            </div>
          </div>
        </div>
      </section>
"""
    marker = "    </main>\n  </div>\n\n  <div class=\"modal-mask\" id=\"adjustModal\">"
    require(text, marker, "main close")
    text = text.replace(marker, new_views + "\n    </main>\n  </div>\n\n  <div class=\"modal-mask\" id=\"adjustModal\">", 1)

    # Append JS helpers file reference approach: inject before </script>
    helpers_path = Path("/Users/sunwenjing/case-map-platform/scripts/p0_prototype_helpers.js")
    helpers = helpers_path.read_text(encoding="utf-8")

    # Small inline patches first
    old_switch = """      if (viewId === 'review' && typeof renderReviewQueue === 'function') renderReviewQueue();
      if (viewId === 'assets' && typeof renderAssetsPage === 'function') renderAssetsPage();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }"""
    new_switch = """      if (viewId === 'review' && typeof renderReviewQueue === 'function') renderReviewQueue();
      if (viewId === 'assets' && typeof renderAssetsPage === 'function') renderAssetsPage();
      if (viewId === 'sync' && typeof renderSyncPage === 'function') renderSyncPage();
      if (viewId === 'baseline' && typeof renderBaselinePage === 'function') renderBaselinePage();
      if (viewId === 'health' && typeof renderHealthPage === 'function') renderHealthPage();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }"""
    require(text, old_switch, "switchView")
    text = text.replace(old_switch, new_switch, 1)

    old_title = """            '<div class="result-title">' + escapeHtml(item.caseName) +
              ' <span class="tag' + (item.sourceType === 'AI缺口' ? ' orange' : ' blue') + '">' +
              item.sourceType + '</span> <span class="tag">' + item.status + '</span></div>' +"""
    new_title = """            '<div class="result-title">' + escapeHtml(item.caseName) +
              ' <span class="tag' + (item.sourceType === 'AI缺口' ? ' orange' : ' blue') + '">' +
              item.sourceType + '</span> <span class="tag">' + item.status + '</span>' +
              (item.version ? (' <span class="ver-tag">v' + escapeHtml(item.version) + '</span>') : '') +
              (item.lifecycle ? (' <span class="tag ' + lifecycleClass(item.lifecycle) + '">' + escapeHtml(item.lifecycle) + '</span>') : '') +
              '</div>' +"""
    require(text, old_title, "item title")
    text = text.replace(old_title, new_title, 1)

    old_confirm = """    function confirmItem(item) {
      item.status = '已确认';
      item.destination = item.sourceType === 'AI缺口'
        ? '正式用例地图资产 · 缺口补齐入库 · 可用于回归包'
        : '正式用例地图资产 · 历史校准入库 · 可在总览检索中消费';
      reviewState.lastConfirmMsg =
        '「' + item.caseName + '」已确认，流向：' + item.destination + '。请到「正式资产库」查看。';
      reviewState.activeTab = 'confirmed';
      document.querySelectorAll('#reviewTabs .tab-btn').forEach((btn) => {
        btn.classList.toggle('active', btn.dataset.tab === 'confirmed');
      });
      showToast('已入库：' + item.caseName, {
        success: true,
        actionLabel: '去正式资产库',
        actionView: 'assets'
      });
    }"""
    new_confirm = """    function lifecycleClass(life) {
      if (life === '已发布') return 'life-published';
      if (life === '评审中') return 'life-review';
      if (life === '已归档') return 'life-archived';
      return 'life-draft';
    }

    function confirmItem(item) {
      if (!canWrite()) {
        showToast('当前角色为只读，无法确认入库');
        return;
      }
      item.status = '已确认';
      item.version = item.version || '1.0.0';
      item.lifecycle = '已发布';
      item.publishedAt = new Date().toLocaleString();
      item.destination = item.sourceType === 'AI缺口'
        ? '正式用例地图资产 · 缺口补齐入库 · v' + item.version
        : '正式用例地图资产 · 历史校准入库 · v' + item.version;
      reviewState.lastConfirmMsg =
        '「' + item.caseName + '」已确认，流向：' + item.destination + '。请到「正式资产库」查看。';
      reviewState.activeTab = 'confirmed';
      document.querySelectorAll('#reviewTabs .tab-btn').forEach((btn) => {
        btn.classList.toggle('active', btn.dataset.tab === 'confirmed');
      });
      showToast('已入库：' + item.caseName + '（v' + item.version + '）', {
        success: true,
        actionLabel: '去正式资产库',
        actionView: 'assets'
      });
      if (typeof renderBaselinePage === 'function') renderBaselinePage();
      if (typeof renderHealthPage === 'function') renderHealthPage();
    }"""
    require(text, old_confirm, "confirmItem")
    text = text.replace(old_confirm, new_confirm, 1)

    old_build = """    function buildRegressionPack() {
      const filter = currentAssetsFilter();
      const assets = formalAssets(filter);
      const card = document.getElementById('regressionCard');
      const body = document.getElementById('regressionBody');
      const count = document.getElementById('regressionCount');
      if (!assets.length) {
        card.style.display = 'block';
        count.textContent = '0';
        body.innerHTML = '<div class="muted">当前检索无正式资产，无法生成回归包。请先确认入库或放宽条件。</div>';
        return;
      }"""
    new_build = """    function buildRegressionPack() {
      const filter = currentAssetsFilter();
      const assets = formalAssets(filter);
      const card = document.getElementById('regressionCard');
      const body = document.getElementById('regressionBody');
      const count = document.getElementById('regressionCount');
      const actions = document.getElementById('regressionActions');
      platformState.lastImpact = assets.slice();
      if (!assets.length) {
        card.style.display = 'block';
        count.textContent = '0';
        body.innerHTML = '<div class="muted">当前检索无正式资产，无法生成回归包。请先确认入库或放宽条件。</div>';
        if (actions) actions.style.display = 'none';
        return;
      }
      if (actions) actions.style.display = 'flex';"""
    require(text, old_build, "buildRegressionPack")
    text = text.replace(old_build, new_build, 1)

    # Only replace pending queue render calls carefully via helper later
    text = text.replace(
        "status: '待抽检'",
        "status: '待抽检', version: '0.9.0', lifecycle: '评审中'",
    )
    text = text.replace(
        "status: '待确认'",
        "status: '待确认', version: '0.9.0', lifecycle: '评审中'",
    )

    # Inject helpers before closing script init
    tail = """    refreshHint();
    updateImportGate();
    renderReviewQueue();
    if (typeof renderAssetsPage === 'function') renderAssetsPage();
    runDetailSearch();
  </script>"""
    require(text, tail, "script tail")
    text = text.replace(
        tail,
        helpers
        + """
    refreshRoleUI();
    refreshHint();
    updateImportGate();
    renderReviewQueue();
    if (typeof renderAssetsPage === 'function') renderAssetsPage();
    renderSyncPage();
    renderBaselinePage();
    renderHealthPage();
    runDetailSearch();
  </script>""",
        1,
    )

    # Patch review queue to respect readonly - do after helpers define canWrite
    # Find unique call sites: typically itemsByTab map
    # Safer: wrap inside helpers by monkeypatching renderItemRow usage in renderReviewQueue
    # We'll patch renderReviewQueue's pending/all rendering via a small replace if present
    marker_row = "return all.map((item) => renderItemRow(item"
    # leave as is; helpers will override renderReviewQueue post-definition? Better inject after renderReviewQueue:

    DST.write_text(text, encoding="utf-8")
    print(f"wrote {DST} ({DST.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
