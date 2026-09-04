/* 报价域 · L2 领域全景图数据 */
window.__DOMAIN_PANORAMA__ = {
  domain: '家装报价',
  displayName: '家装报价领域',
  updatedAt: '2025-05-21',
  summary: {
    totalNodes: 11,
    coveredCount: 5,
    partialCount: 4,
    gapCount: 2,
    totalCases: 192,
    automationRate: 0.72
  },
  nodes: [
    {
      id: 'trigger',
      name: '报价触发与报价单生成',
      shortName: '报价触发',
      risk: '高',
      coverage: 'covered',
      caseCount: 28,
      featureCount: 4,
      gapCount: 0,
      x: 72,
      y: 108,
      featureKeys: []
    },
    {
      id: 'scene',
      name: '报价场景识别与价格来源',
      shortName: '场景识别',
      risk: '高',
      coverage: 'covered',
      caseCount: 35,
      featureCount: 6,
      gapCount: 0,
      x: 248,
      y: 108,
      featureKeys: []
    },
    {
      id: 'param',
      name: '参数解析与输入精度',
      shortName: '参数解析',
      risk: '高',
      coverage: 'partial',
      caseCount: 12,
      featureCount: 3,
      gapCount: 1,
      x: 424,
      y: 108,
      featureKeys: []
    },
    {
      id: 'material',
      name: '物料处理与自动带出',
      shortName: '物料带出',
      risk: '高',
      coverage: 'partial',
      caseCount: 16,
      featureCount: 5,
      gapCount: 2,
      x: 600,
      y: 108,
      featureKeys: []
    },
    {
      id: 'amount',
      name: '金额计算与汇总',
      shortName: '金额计算',
      risk: '高',
      coverage: 'covered',
      caseCount: 42,
      featureCount: 8,
      gapCount: 0,
      x: 776,
      y: 108,
      featureKeys: [
        '家装/报价/金额计算与汇总/数量价汇总',
        '家装/报价/金额计算与汇总/固定价汇总'
      ]
    },
    {
      id: 'design_gen',
      name: '造价单生成与明细落库',
      shortName: '造价单生成',
      risk: '高',
      coverage: 'covered',
      caseCount: 22,
      featureCount: 3,
      gapCount: 0,
      x: 952,
      y: 108,
      featureKeys: []
    },
    {
      id: 'design_audit',
      name: '造价提交与审核',
      shortName: '造价审核',
      risk: '高',
      coverage: 'covered',
      caseCount: 14,
      featureCount: 11,
      gapCount: 1,
      x: 1128,
      y: 108,
      featureKeys: [
        '家装/报价/造价提交与审核/提交审核',
        '家装/报价/造价提交与审核/自动审核判定',
        '家装/报价/造价提交与审核/人工审核通过',
        '家装/报价/造价提交与审核/人工审核驳回'
      ]
    },
    {
      id: 'survey',
      name: '实勘审核',
      shortName: '实勘审核',
      risk: '中',
      coverage: 'partial',
      caseCount: 8,
      featureCount: 2,
      gapCount: 1,
      x: 1000,
      y: 268,
      featureKeys: []
    },
    {
      id: 'appeal',
      name: '申诉审核',
      shortName: '申诉审核',
      risk: '中',
      coverage: 'gap',
      caseCount: 3,
      featureCount: 1,
      gapCount: 3,
      x: 1128,
      y: 268,
      featureKeys: []
    },
    {
      id: 'lifecycle',
      name: '生命周期与外部联动',
      shortName: '生命周期',
      risk: '高',
      coverage: 'partial',
      caseCount: 14,
      featureCount: 4,
      gapCount: 2,
      x: 776,
      y: 268,
      featureKeys: []
    },
    {
      id: 'frontend',
      name: '前端展示与金额明细',
      shortName: '前端展示',
      risk: '中',
      coverage: 'partial',
      caseCount: 10,
      featureCount: 2,
      gapCount: 1,
      x: 424,
      y: 268,
      featureKeys: []
    }
  ],
  edges: [
    { from: 'trigger', to: 'scene', type: 'main' },
    { from: 'scene', to: 'param', type: 'main' },
    { from: 'param', to: 'material', type: 'main' },
    { from: 'material', to: 'amount', type: 'main' },
    { from: 'amount', to: 'design_gen', type: 'main' },
    { from: 'design_gen', to: 'design_audit', type: 'main' },
    { from: 'design_audit', to: 'survey', type: 'branch' },
    { from: 'design_audit', to: 'appeal', type: 'branch' },
    { from: 'design_audit', to: 'lifecycle', type: 'branch' },
    { from: 'amount', to: 'frontend', type: 'branch' },
    { from: 'param', to: 'frontend', type: 'weak' }
  ]
};
