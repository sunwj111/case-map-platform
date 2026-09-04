/* 仅用例模式：聚类伪节点 + 多信号置信度 + A/B/C 分流 */
(function initP0CasesOnly(global) {
  const STOP_WORDS = new Set([
    'tc', 'test', 'case', 'the', 'and', 'for', 'with',
    '用例', '测试', '验证', '检查', '是否', '可以', '进行', '然后', '以及', '或者', '需要',
    '正确', '成功', '失败', '异常', '正常', '场景', '功能', '接口', '页面', '按钮',
    '当', '若', '如果', '那么', '并且', '或者'
  ]);

  const TYPE_RULES = [
    { name: '异常流', keywords: ['失败', '异常', '拒绝', '驳回', '不允许', '为空', '非法', '错误', '拦截', '缺'] },
    { name: '边界值', keywords: ['0', '为空', '最大', '最小', '边界', '精度', '四舍五入', '上限', '下限'] },
    { name: '规则校验', keywords: ['规则', '校验', '必须', '不可', '判定', '校验'] },
    { name: '状态流转', keywords: ['状态', '流转', '提交', '审核', '通过', '作废', '失效', '取消', '回退'] },
    { name: '金额计算', keywords: ['金额', '合计', '总价', '单价', '数量', '价格', '利润', '税'] },
    { name: '主流程', keywords: ['成功', '正常', '生成', '发起', '通过', '保存', '完成'] },
    { name: '数据一致性', keywords: ['一致', '同步', '落库', '保存', '字段', '记录', '明细'] },
    { name: '展示校验', keywords: ['展示', '名称', '页面', '弹窗', 'tab', '按钮', '显示'] }
  ];

  function compact(text) {
    return String(text || '').replace(/\s+/g, ' ').trim();
  }

  function stripCaseId(name) {
    return compact(name)
      .replace(/^(TC|GAP|CASE|用例)[-_/\s]?\w*[-_\s]*/i, '')
      .replace(/^\d+[\.、\s]+/, '')
      .trim();
  }

  function tokenize(text) {
    return String(text || '')
      .split(/[\s_\-／/,，。；;：:、|（）()【】\[\]<>+\d]+/)
      .map((token) => token.trim())
      .filter((token) => {
        if (!token || token.length < 2 || token.length > 14) return false;
        if (/^\d+$/.test(token)) return false;
        if (STOP_WORDS.has(token.toLowerCase())) return false;
        return true;
      });
  }

  function bagText(caseRow) {
    return [
      caseRow.caseName,
      caseRow.feature,
      caseRow.scene,
      caseRow.step,
      caseRow.expected,
      caseRow.module
    ].map(compact).join(' ');
  }

  function seedLabel(caseRow, preferredNodes) {
    const preferred = preferredNodes || [];
    const bag = bagText(caseRow).toLowerCase().replace(/\s/g, '');
    const hitPreferred = preferred.find((node) => {
      const token = String(node || '').toLowerCase().replace(/\s/g, '');
      return token && (bag.includes(token) || (token.length >= 4 && bag.includes(token.slice(0, 4))));
    });
    if (hitPreferred) return compact(hitPreferred);
    if (compact(caseRow.feature)) return compact(caseRow.feature);
    if (compact(caseRow.scene)) return compact(caseRow.scene);
    const stripped = stripCaseId(caseRow.caseName);
    if (!stripped) return '未归类用例';
    const head = stripped.split(/[\s\-—–|｜]/)[0] || stripped;
    const zh = head.match(/[\u4e00-\u9fa5A-Za-z0-9]{2,12}/);
    if (zh && zh[0]) return zh[0].length >= 4 ? zh[0].slice(0, 10) : zh[0];
    return head.slice(0, 12) || '未归类用例';
  }

  function classifyTestType(caseRow) {
    const haystack = bagText(caseRow).toLowerCase();
    let best = { name: '主流程', score: 0 };
    TYPE_RULES.forEach((rule) => {
      const score = rule.keywords.reduce((sum, word) => sum + (haystack.includes(String(word).toLowerCase()) ? 1 : 0), 0);
      if (score > best.score) best = { name: rule.name, score: score };
    });
    return best;
  }

  function labelSimilarity(left, right) {
    const a = String(left || '');
    const b = String(right || '');
    if (!a || !b) return 0;
    if (a === b) return 1;
    if (a.includes(b) || b.includes(a)) return 0.85;
    const tokensA = new Set(tokenize(a));
    const tokensB = new Set(tokenize(b));
    if (!tokensA.size || !tokensB.size) return 0;
    let hit = 0;
    tokensA.forEach((token) => { if (tokensB.has(token)) hit += 1; });
    return hit / Math.max(tokensA.size, tokensB.size);
  }

  function mergeClusterLabels(clusters) {
    const list = clusters.slice();
    let merged = true;
    while (merged) {
      merged = false;
      for (let i = 0; i < list.length; i += 1) {
        for (let j = i + 1; j < list.length; j += 1) {
          if (labelSimilarity(list[i].name, list[j].name) < 0.72) continue;
          const keep = list[i].caseIndexes.length >= list[j].caseIndexes.length ? i : j;
          const drop = keep === i ? j : i;
          const keeper = list[keep];
          const droper = list[drop];
          keeper.caseIndexes = uniqueNums(keeper.caseIndexes.concat(droper.caseIndexes));
          if (droper.name.length > keeper.name.length && droper.name.includes(keeper.name)) {
            keeper.name = droper.name;
          }
          list.splice(drop, 1);
          merged = true;
          break;
        }
        if (merged) break;
      }
    }
    return list;
  }

  function uniqueNums(values) {
    const seen = new Set();
    const result = [];
    values.forEach((value) => {
      if (seen.has(value)) return;
      seen.add(value);
      result.push(value);
    });
    return result;
  }

  function uniqueTexts(values) {
    const seen = new Set();
    const result = [];
    values.forEach((value) => {
      const text = compact(value);
      if (!text || seen.has(text)) return;
      seen.add(text);
      result.push(text);
    });
    return result;
  }

  function clusterKeywords(caseRows, indexes) {
    const freq = {};
    indexes.forEach((index) => {
      tokenize(bagText(caseRows[index])).forEach((token) => {
        freq[token] = (freq[token] || 0) + 1;
      });
    });
    return Object.keys(freq)
      .sort((left, right) => freq[right] - freq[left] || left.localeCompare(right, 'zh'))
      .slice(0, 8);
  }

  function buildClusters(caseRows, preferredNodes) {
    const buckets = {};
    (caseRows || []).forEach((row, index) => {
      const label = seedLabel(row, preferredNodes);
      if (!buckets[label]) buckets[label] = [];
      buckets[label].push(index);
    });
    let clusters = Object.keys(buckets).map((name, order) => ({
      id: 'cluster-' + (order + 1),
      name: name,
      caseIndexes: buckets[name].slice(),
      keywords: [],
      size: 0
    }));
    clusters = mergeClusterLabels(clusters);
    clusters.forEach((cluster, order) => {
      cluster.id = 'cluster-' + (order + 1);
      cluster.keywords = clusterKeywords(caseRows, cluster.caseIndexes);
      cluster.size = cluster.caseIndexes.length;
    });
    clusters.sort((left, right) => right.size - left.size || left.name.localeCompare(right.name, 'zh'));
    return clusters;
  }

  function containsLabel(haystack, label) {
    const text = String(haystack || '').toLowerCase().replace(/\s/g, '');
    const token = String(label || '').toLowerCase().replace(/\s/g, '');
    if (!text || !token) return false;
    if (text.includes(token)) return true;
    if (token.length >= 4 && text.includes(token.slice(0, 4))) return true;
    return false;
  }

  function scoreCase(caseRow, cluster, options) {
    const opts = options || {};
    const preferredNodes = opts.preferredNodes || [];
    const reasons = [];
    let score = 48;
    const full = bagText(caseRow);
    const body = compact(caseRow.step) + ' ' + compact(caseRow.expected);
    const hasFeature = !!compact(caseRow.feature);
    const hasScene = !!compact(caseRow.scene);
    const kbAnchored = preferredNodes.some((node) =>
      compact(node) === compact(cluster.name) || containsLabel(cluster.name, node)
    );
    const explicit = (hasFeature && (
        compact(caseRow.feature) === cluster.name ||
        containsLabel(caseRow.feature, cluster.name) ||
        containsLabel(cluster.name, caseRow.feature)
      )) ||
      (hasScene && (
        compact(caseRow.scene) === cluster.name ||
        containsLabel(caseRow.scene, cluster.name)
      )) ||
      ((hasFeature || hasScene) && containsLabel(caseRow.caseName, cluster.name));
    if (kbAnchored && containsLabel(full, cluster.name)) {
      score += 18;
      reasons.push('命中知识库节点「' + cluster.name + '」');
    }
    if (explicit) {
      score += 26;
      reasons.push('功能点/场景列与簇名一致或显式命中「' + cluster.name + '」');
    } else if (containsLabel(caseRow.caseName, cluster.name)) {
      score += 10;
      reasons.push('用例名含簇名片段（弱证据）');
    }
    const keywordHits = (cluster.keywords || []).filter((word) =>
      body.toLowerCase().includes(String(word).toLowerCase()) ||
      full.toLowerCase().includes(String(word).toLowerCase())
    );
    if (keywordHits.length >= 2) {
      score += 16;
      reasons.push('步骤/预期命中簇关键词 ' + keywordHits.slice(0, 3).join('、'));
    } else if (keywordHits.length === 1) {
      score += 7;
      reasons.push('命中簇关键词「' + keywordHits[0] + '」');
    }
    if (cluster.size >= 3) {
      score += 10;
      reasons.push('同簇互证 ' + cluster.size + ' 条');
    } else if (cluster.size === 2) {
      score += 5;
      reasons.push('同簇互证 2 条');
    }
    const typeInfo = classifyTestType(caseRow);
    if (typeInfo.score > 0) {
      score += 6;
      reasons.push('类型识别为「' + typeInfo.name + '」');
    }
    if (compact(caseRow.step) && compact(caseRow.expected)) {
      score += 4;
      reasons.push('步骤与预期齐全');
    }
    const moduleName = compact(caseRow.module);
    if (moduleName && cluster.name && labelSimilarity(moduleName, cluster.name) < 0.4 &&
      !containsLabel(moduleName, cluster.name) && !containsLabel(cluster.name, moduleName)) {
      score -= 12;
      reasons.push('原模块「' + moduleName + '」与簇名弱冲突，降权');
    }
    score = Math.max(20, Math.min(96, score));
    // 同簇不足 3 条：缺少互证，禁止进入 A 池（除非功能点列强锚定 / 知识库锚定且 size>=2）
    if (cluster.size < 3) {
      const allowHigh = (cluster.size >= 2 && hasFeature && explicit) || (cluster.size >= 2 && kbAnchored);
      if (!allowHigh && score >= 80) {
        score = 79;
        reasons.push('同簇不足 3 条，置信度封顶 79');
      }
    }
    if (cluster.size < 2 && score >= 60) {
      score = Math.min(score, 59);
      reasons.push('孤立簇（仅 1 条），置信度封顶 59，需人工确认');
    }
    let pool = 'C';
    if (score >= 80) pool = 'A';
    else if (score >= 60) pool = 'B';
    return {
      confidence: score,
      pool: pool,
      testType: typeInfo.name,
      matchReason: reasons.join('；') || '证据不足，建议人工确认',
      advice: pool === 'A' ? '可确认入库' : (pool === 'B' ? '建议抽检' : '人工确认后再挂载')
    };
  }

  function poolOf(confidence) {
    if (confidence >= 80) return 'A';
    if (confidence >= 60) return 'B';
    return 'C';
  }

  function runPipeline(caseRows, options) {
    const opts = options || {};
    const target = opts.target || { domain: '未指定', app: '未指定' };
    const preferredNodes = (opts.preferredNodes || []).map(compact).filter(Boolean);
    const clusters = buildClusters(caseRows || [], preferredNodes);
    const indexToCluster = {};
    clusters.forEach((cluster) => {
      cluster.caseIndexes.forEach((index) => {
        indexToCluster[index] = cluster;
      });
    });

    const items = (caseRows || []).map((caseRow, index) => {
      const cluster = indexToCluster[index] || {
        id: 'cluster-orphan',
        name: seedLabel(caseRow, preferredNodes),
        keywords: tokenize(bagText(caseRow)).slice(0, 5),
        size: 1
      };
      const scored = scoreCase(caseRow, cluster, { preferredNodes: preferredNodes });
      const sceneName = compact(caseRow.scene) || ('伪场景·' + cluster.name);
      return {
        id: 'cases-only-' + index,
        sourceType: '历史挂载',
        produceMode: 'casesOnly',
        caseName: caseRow.caseName,
        domain: target.domain,
        app: target.app,
        scene: sceneName,
        feature: cluster.name,
        step: caseRow.step || '',
        expected: caseRow.expected || '',
        module: caseRow.module || '',
        confidence: scored.confidence,
        pool: scored.pool,
        testType: scored.testType,
        matchReason: scored.matchReason,
        advice: scored.advice,
        status: scored.pool === 'A' ? '待确认' : '待抽检',
        version: '0.9.0',
        lifecycle: '评审中',
        destination: '',
        gapReason: '',
        api: '',
        clusterId: cluster.id,
        clusterSize: cluster.size,
        clusterKeywords: (cluster.keywords || []).slice()
      };
    });

    const stats = {
      total: items.length,
      poolA: items.filter((item) => item.pool === 'A').length,
      poolB: items.filter((item) => item.pool === 'B').length,
      poolC: items.filter((item) => item.pool === 'C').length,
      clusterCount: clusters.length,
      preferredNodeCount: preferredNodes.length
    };

    return {
      clusters: clusters,
      items: items,
      stats: stats,
      flowNodes: clusters.map((cluster) => ({
        node: cluster.name,
        description: preferredNodes.includes(cluster.name)
          ? '对齐知识库节点（用例+NL骨架）'
          : '由历史用例聚类生成的伪节点（仅用例模式）',
        risk: cluster.size >= 5 ? '高' : (cluster.size >= 3 ? '中' : '低'),
        keywords: cluster.keywords.slice(),
        caseCount: cluster.size
      }))
    };
  }

  global.P0CasesOnly = {
    buildClusters: buildClusters,
    scoreCase: scoreCase,
    classifyTestType: classifyTestType,
    runPipeline: runPipeline,
    poolOf: poolOf,
    seedLabel: seedLabel
  };
})(window);
