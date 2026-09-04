/* FeatureMapDTO 样例数据 · 报价域 */
window.__FEATURE_MAP_SAMPLES__ = {
  '家装/报价/金额计算与汇总/数量价汇总': {
    meta: {
      mapId: 'fm_quantity_price',
      featureKey: '家装/报价/金额计算与汇总/数量价汇总',
      version: '1.0.0',
      baseline: 'BL-20260720-01',
      updatedAt: '2025-05-21',
      qualityOwner: '张三',
      dataSources: ['正式资产库', '家装报价核心接口清单.md', 'quote.json']
    },
    spine: {
      domain: '家装',
      app: '报价',
      processName: '报价流程',
      featureName: '数量价汇总',
      sceneName: '金额计算与汇总',
      valueTags: ['核心链路', '金额计算', '高风险'],
      neighborFeatures: [
        { featureKey: '家装/报价/金额计算与汇总/固定价汇总', featureName: '固定价汇总' },
        { featureKey: '家装/报价/金额计算与汇总/去利润价计算', featureName: '去利润价计算' },
        { featureKey: '家装/报价/金额计算与汇总/分组汇总', featureName: '分组汇总' }
      ]
    },
    businessView: {
      scenarios: [
        { id: 'SC-001', name: '正常报价', coverageStatus: 'covered', caseCount: 2 },
        { id: 'SC-002', name: '优惠报价', coverageStatus: 'covered', caseCount: 1 },
        { id: 'SC-003', name: '边界校验', coverageStatus: 'partial', caseCount: 2 }
      ],
      cases: [
        { id: 'TC-001', name: '标准方案数量价计算正确', priority: 'P0', testScenario: '正常报价', confidence: 92, mountAdvice: '自动挂载', api: 'POST /quotation/offer' },
        { id: 'TC-002', name: '优惠折扣后数量价正确', priority: 'P0', testScenario: '优惠报价', confidence: 88, mountAdvice: '自动挂载', api: 'POST /quotation/offer' },
        { id: 'TC-003', name: '金额为负拦截', priority: 'P1', testScenario: '边界校验', confidence: 85, mountAdvice: '自动挂载', api: 'POST /quotation/offer' },
        { id: 'TC-004', name: '数量为0边界', priority: 'P1', testScenario: '边界校验', confidence: 72, mountAdvice: '待抽检', api: 'POST /quotation/offer' }
      ],
      scripts: [
        { id: 'SCRIPT-001', name: 'quotation_offer.json', type: 'API', status: 'executable', linkedCaseId: 'TC-001' },
        { id: 'SCRIPT-002', name: 'quotation_discount.json', type: 'API', status: 'executable', linkedCaseId: 'TC-002' }
      ],
      dataTemplates: [
        { id: 'DT-001', name: '标准装修方案', linkedScenarioId: 'SC-001' },
        { id: 'DT-002', name: '优惠活动数据', linkedScenarioId: 'SC-002' }
      ],
      executions: [
        { id: 'EXEC-001', label: '近 3 次', results: ['pass', 'pass', 'pass'], lastRunAt: '2025-05-20' },
        { id: 'EXEC-002', label: '近 3 次', results: ['pass', 'fail', 'pass'], lastRunAt: '2025-05-18' }
      ]
    },
    riskView: {
      rules: [
        { id: 'R002', name: '数量价汇总规则', source: '知识库' },
        { id: 'R014', name: '四舍五入规则', source: '知识库' },
        { id: 'R018', name: '金额不可为负', source: '判定表' }
      ],
      defects: [
        { id: 'BUG-231', title: '优惠叠加重复扣减', severity: 'P1', openedAt: '2025-05-10' },
        { id: 'BUG-188', title: '边界金额溢出', severity: 'P2', openedAt: '2025-05-05' }
      ],
      tags: [
        { type: 'risk', label: '高风险', level: 'high' },
        { type: 'usage', label: '高频', level: 'medium' },
        { type: 'chain', label: '核心链路', level: 'high' }
      ]
    },
    techView: {
      source: '知识库 · 家装报价核心接口清单',
      services: [
        { name: 'jz_quotation', role: 'primary' },
        { name: 'promotion-svc', role: 'secondary' }
      ],
      flowNodes: [
        { name: '金额计算与汇总', risk: '高', isPrimary: true },
        { name: '报价触发与报价单生成', risk: '高', isPrimary: false }
      ],
      apis: [
        { method: 'POST', path: '/quotation/offer', label: 'POST /quotation/offer', relation: '主接口', controller: 'QuotationApi#offerQuotations', confidence: 95 },
        { method: 'POST', path: '/quotation/bim/offer', label: 'POST /quotation/bim/offer', relation: '主接口', controller: 'QuotationBimApi#offerQuotations', confidence: 90 }
      ],
      tables: ['t_quote', 't_quote_item', 'jz_design_cost_detail'],
      messages: ['quote.created', 'design.cost.submitted'],
      codeModules: [
        { name: 'QuoteService#calcQuantityPrice', type: 'service' },
        { name: 'PriceCalculator', type: 'calculator' }
      ]
    },
    summary: {
      linkedCaseCount: 42,
      priorityDistribution: { P0: 12, P1: 18, P2: 8, P3: 4, P0Rate: 0.28, P1Rate: 0.43, P2Rate: 0.19, P3Rate: 0.10 },
      automationCoverage: 0.78,
      defectCount30d: 8,
      gapCount: 2,
      coverageStatus: 'partial'
    },
    consumers: [
      { platform: '自动化测试平台', usage: '按功能点拉取回归包' },
      { platform: '造数 / 数据平台', usage: '绑定测试数据模板' },
      { platform: '研发代码分析', usage: '接口 / 模块影响反查' },
      { platform: '质量报告', usage: '覆盖率与缺陷趋势' }
    ]
  },
  '家装/报价/金额计算与汇总/固定价汇总': {
    meta: {
      mapId: 'fm_fixed_price',
      featureKey: '家装/报价/金额计算与汇总/固定价汇总',
      version: '1.0.0',
      baseline: 'BL-20260720-01',
      updatedAt: '2025-05-20',
      qualityOwner: '李四',
      dataSources: ['正式资产库', '家装报价核心接口清单.md']
    },
    spine: {
      domain: '家装',
      app: '报价',
      processName: '报价流程',
      featureName: '固定价汇总',
      sceneName: '金额计算与汇总',
      valueTags: ['金额计算', '高风险'],
      neighborFeatures: [
        { featureKey: '家装/报价/金额计算与汇总/数量价汇总', featureName: '数量价汇总' },
        { featureKey: '家装/报价/金额计算与汇总/分组汇总', featureName: '分组汇总' }
      ]
    },
    businessView: {
      scenarios: [
        { id: 'SC-101', name: '固定价正常汇总', coverageStatus: 'covered', caseCount: 2 },
        { id: 'SC-102', name: '固定价边界', coverageStatus: 'gap', caseCount: 0 }
      ],
      cases: [
        { id: 'TC-101', name: '固定价项汇总正确', priority: 'P0', testScenario: '固定价正常汇总', confidence: 90, mountAdvice: '自动挂载', api: 'POST /quotation/offer' },
        { id: 'TC-102', name: '固定价与数量价混合', priority: 'P1', testScenario: '固定价正常汇总', confidence: 80, mountAdvice: '自动挂载', api: 'POST /quotation/offer' }
      ],
      scripts: [
        { id: 'SCRIPT-101', name: 'fixed_price.json', type: 'API', status: 'executable', linkedCaseId: 'TC-101' }
      ],
      dataTemplates: [
        { id: 'DT-101', name: '固定价物料方案', linkedScenarioId: 'SC-101' }
      ],
      executions: [
        { id: 'EXEC-101', label: '近 3 次', results: ['pass', 'pass', 'fail'], lastRunAt: '2025-05-19' }
      ]
    },
    riskView: {
      rules: [
        { id: 'R003', name: '固定价汇总规则', source: '知识库' },
        { id: 'R014', name: '四舍五入规则', source: '知识库' }
      ],
      defects: [
        { id: 'BUG-156', title: '固定价项重复计入', severity: 'P1', openedAt: '2025-05-08' }
      ],
      tags: [
        { type: 'risk', label: '高风险', level: 'high' },
        { type: 'gap', label: '有缺口', level: 'medium' }
      ]
    },
    techView: {
      source: '知识库 · 家装报价核心接口清单',
      services: [{ name: 'jz_quotation', role: 'primary' }],
      flowNodes: [{ name: '金额计算与汇总', risk: '高', isPrimary: true }],
      apis: [
        { method: 'POST', path: '/quotation/offer', label: 'POST /quotation/offer', relation: '主接口', confidence: 92 }
      ],
      tables: ['t_quote', 't_quote_item'],
      messages: ['quote.created'],
      codeModules: [{ name: 'FixedPriceCalculator', type: 'calculator' }]
    },
    summary: {
      linkedCaseCount: 18,
      priorityDistribution: { P0: 6, P1: 8, P2: 3, P3: 1, P0Rate: 0.33, P1Rate: 0.44, P2Rate: 0.17, P3Rate: 0.06 },
      automationCoverage: 0.65,
      defectCount30d: 3,
      gapCount: 1,
      coverageStatus: 'partial'
    },
    consumers: [
      { platform: '自动化测试平台', usage: '按功能点拉取回归包' },
      { platform: '质量报告', usage: '覆盖率与缺陷趋势' }
    ]
  },
  '家装/报价/造价提交与审核/自动审核判定': {
    meta: {
      mapId: 'fm_auto_audit',
      featureKey: '家装/报价/造价提交与审核/自动审核判定',
      version: '1.0.0',
      baseline: 'BL-20260720-01',
      updatedAt: '2025-05-18',
      qualityOwner: '王五',
      dataSources: ['正式资产库', '家装报价核心接口清单.md']
    },
    spine: {
      domain: '家装',
      app: '报价',
      processName: '审核流程',
      featureName: '自动审核判定',
      sceneName: '造价提交与审核',
      valueTags: ['核心链路', '审核'],
      neighborFeatures: [
        { featureKey: '家装/报价/造价提交与审核/提交审核', featureName: '提交审核' },
        { featureKey: '家装/报价/造价提交与审核/审核拒绝回退', featureName: '审核拒绝回退' }
      ]
    },
    businessView: {
      scenarios: [
        { id: 'SC-201', name: '免审通过', coverageStatus: 'covered', caseCount: 1 },
        { id: 'SC-202', name: '抽审命中', coverageStatus: 'covered', caseCount: 1 },
        { id: 'SC-203', name: '需人工审核', coverageStatus: 'gap', caseCount: 0 }
      ],
      cases: [
        { id: 'TC-201', name: '满足免审条件自动通过', priority: 'P0', testScenario: '免审通过', confidence: 95, mountAdvice: '自动挂载', api: 'POST /quotation/submitDesignCost' },
        { id: 'TC-202', name: '抽审比例命中转人工', priority: 'P1', testScenario: '抽审命中', confidence: 82, mountAdvice: '自动挂载', api: 'POST /quotation/submitDesignCost' }
      ],
      scripts: [
        { id: 'SCRIPT-201', name: 'submit_design_cost.json', type: 'API', status: 'executable', linkedCaseId: 'TC-201' }
      ],
      dataTemplates: [
        { id: 'DT-201', name: '待审核造价单', linkedScenarioId: 'SC-201' }
      ],
      executions: [
        { id: 'EXEC-201', label: '近 3 次', results: ['pass', 'pass', 'pass'], lastRunAt: '2025-05-17' }
      ]
    },
    riskView: {
      rules: [
        { id: 'R021', name: '自动审核判定规则', source: '知识库' },
        { id: 'R022', name: '抽审比例规则', source: '判定表' }
      ],
      defects: [],
      tags: [
        { type: 'chain', label: '核心链路', level: 'high' },
        { type: 'gap', label: '场景缺口', level: 'medium' }
      ]
    },
    techView: {
      source: '知识库 · 家装报价核心接口清单',
      services: [{ name: 'jz_quotation', role: 'primary' }],
      flowNodes: [{ name: '造价提交与审核', risk: '高', isPrimary: true }],
      apis: [
        { method: 'POST', path: '/quotation/submitDesignCost', label: 'POST /quotation/submitDesignCost', relation: '主接口', confidence: 96 },
        { method: 'POST', path: '/quotation/checkParamBeforeSubmit', label: 'POST /quotation/checkParamBeforeSubmit', relation: '校验接口', confidence: 88 }
      ],
      tables: ['jz_design_cost', 'jz_design_cost_audit'],
      messages: ['design.cost.submitted', 'design.cost.audited'],
      codeModules: [
        { name: 'AuditService#autoAudit', type: 'service' },
        { name: 'SamplingRuleEngine', type: 'engine' }
      ]
    },
    summary: {
      linkedCaseCount: 12,
      priorityDistribution: { P0: 4, P1: 6, P2: 2, P3: 0, P0Rate: 0.33, P1Rate: 0.50, P2Rate: 0.17, P3Rate: 0 },
      automationCoverage: 0.85,
      defectCount30d: 1,
      gapCount: 1,
      coverageStatus: 'partial'
    },
    consumers: [
      { platform: '自动化测试平台', usage: '审核链路回归' },
      { platform: '研发代码分析', usage: '审核规则变更影响分析' }
    ]
  }
};

window.__FEATURE_MAP_INDEX__ = [
  { featureKey: '家装/报价/金额计算与汇总/数量价汇总', featureName: '数量价汇总', sceneName: '金额计算与汇总', caseCount: 42, risk: '高' },
  { featureKey: '家装/报价/金额计算与汇总/固定价汇总', featureName: '固定价汇总', sceneName: '金额计算与汇总', caseCount: 18, risk: '高' },
  { featureKey: '家装/报价/造价提交与审核/自动审核判定', featureName: '自动审核判定', sceneName: '造价提交与审核', caseCount: 12, risk: '中' }
];
