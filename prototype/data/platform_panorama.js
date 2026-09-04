/* 平台全景 · 收房 / 家装 / 出房 / 家服 / 灵之 / 企信 */
window.__PLATFORM_PANORAMA__ = {
  updatedAt: '2025-05-21',
  summary: {
    domainCount: 6,
    appCount: 19,
    totalCases: 742,
    coveredCount: 7,
    partialCount: 9,
    gapCount: 3
  },
  domainEdges: [
    { from: 'acquire', to: 'home', type: 'main', label: '收房后装修' },
    { from: 'home', to: 'rentout', type: 'main', label: '装修后出房' },
    { from: 'rentout', to: 'homesvc', type: 'branch', label: '入住后服务' },
    { from: 'lingzhi', to: 'home', type: 'branch', label: '智能能力' },
    { from: 'qixin', to: 'homesvc', type: 'branch', label: '消息触达' }
  ],
  domains: [
    {
      id: 'acquire',
      name: '收房',
      coverage: 'partial',
      caseCount: 96,
      gapCount: 5,
      x: 72,
      y: 64,
      apps: [
        { id: 'eval', name: '评估', coverage: 'covered', caseCount: 38, nodeCount: 4, gapCount: 0, flowKey: 'acquire/eval' },
        { id: 'sign', name: '收房签约', coverage: 'partial', caseCount: 34, nodeCount: 4, gapCount: 2, flowKey: 'acquire/sign' },
        { id: 'handover', name: '交割', coverage: 'partial', caseCount: 24, nodeCount: 3, gapCount: 3, flowKey: 'acquire/handover' }
      ]
    },
    {
      id: 'home',
      name: '家装',
      coverage: 'partial',
      caseCount: 286,
      gapCount: 8,
      x: 460,
      y: 64,
      apps: [
        { id: 'quote', name: '报价', coverage: 'covered', caseCount: 192, nodeCount: 11, gapCount: 2, flowKey: 'home/quote' },
        { id: 'measure', name: '量房', coverage: 'partial', caseCount: 48, nodeCount: 5, gapCount: 3, flowKey: 'home/measure' },
        { id: 'design', name: '设计', coverage: 'partial', caseCount: 36, nodeCount: 4, gapCount: 2, flowKey: 'home/design' },
        { id: 'contract', name: '合同', coverage: 'gap', caseCount: 10, nodeCount: 4, gapCount: 4, flowKey: 'home/contract' }
      ]
    },
    {
      id: 'rentout',
      name: '出房',
      coverage: 'partial',
      caseCount: 128,
      gapCount: 6,
      x: 848,
      y: 64,
      apps: [
        { id: 'listing', name: '房源', coverage: 'covered', caseCount: 52, nodeCount: 4, gapCount: 1, flowKey: 'rentout/listing' },
        { id: 'showing', name: '带看', coverage: 'partial', caseCount: 41, nodeCount: 4, gapCount: 2, flowKey: 'rentout/showing' },
        { id: 'deal', name: '出房签约', coverage: 'partial', caseCount: 35, nodeCount: 4, gapCount: 3, flowKey: 'rentout/deal' }
      ]
    },
    {
      id: 'homesvc',
      name: '家服',
      coverage: 'partial',
      caseCount: 88,
      gapCount: 7,
      x: 72,
      y: 280,
      apps: [
        { id: 'clean', name: '保洁', coverage: 'covered', caseCount: 36, nodeCount: 4, gapCount: 1, flowKey: 'homesvc/clean' },
        { id: 'repair', name: '维修', coverage: 'partial', caseCount: 32, nodeCount: 4, gapCount: 3, flowKey: 'homesvc/repair' },
        { id: 'booking', name: '预约调度', coverage: 'gap', caseCount: 20, nodeCount: 3, gapCount: 3, flowKey: 'homesvc/booking' }
      ]
    },
    {
      id: 'lingzhi',
      name: '灵之',
      coverage: 'partial',
      caseCount: 74,
      gapCount: 4,
      x: 460,
      y: 280,
      apps: [
        { id: 'ai-quote', name: '智能报价', coverage: 'covered', caseCount: 42, nodeCount: 4, gapCount: 1, flowKey: 'lingzhi/ai-quote' },
        { id: 'recommend', name: '方案推荐', coverage: 'partial', caseCount: 22, nodeCount: 3, gapCount: 2, flowKey: 'lingzhi/recommend' },
        { id: 'discount', name: '智能折扣', coverage: 'partial', caseCount: 10, nodeCount: 3, gapCount: 1, flowKey: 'lingzhi/discount' }
      ]
    },
    {
      id: 'qixin',
      name: '企信',
      coverage: 'gap',
      caseCount: 70,
      gapCount: 9,
      x: 848,
      y: 280,
      apps: [
        { id: 'notify', name: '消息触达', coverage: 'partial', caseCount: 28, nodeCount: 3, gapCount: 2, flowKey: 'qixin/notify' },
        { id: 'approve', name: '审批流', coverage: 'gap', caseCount: 24, nodeCount: 4, gapCount: 4, flowKey: 'qixin/approve' },
        { id: 'todo', name: '待办协同', coverage: 'gap', caseCount: 18, nodeCount: 3, gapCount: 3, flowKey: 'qixin/todo' }
      ]
    }
  ],
  appFlows: {
    'acquire/eval': {
      displayName: '收房 · 评估',
      nodes: [
        { id: 'ae1', name: '房源核验', shortName: '房源核验', risk: '高', coverage: 'covered', caseCount: 12, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'ae2', name: '估价测算', shortName: '估价测算', risk: '高', coverage: 'covered', caseCount: 12, featureCount: 2, gapCount: 0, x: 320, y: 140, featureKeys: [] },
        { id: 'ae3', name: '评估报告', shortName: '评估报告', risk: '中', coverage: 'partial', caseCount: 8, featureCount: 2, gapCount: 0, x: 568, y: 140, featureKeys: [] },
        { id: 'ae4', name: '业主确认', shortName: '业主确认', risk: '中', coverage: 'covered', caseCount: 6, featureCount: 1, gapCount: 0, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'ae1', to: 'ae2', type: 'main' }, { from: 'ae2', to: 'ae3', type: 'main' }, { from: 'ae3', to: 'ae4', type: 'main' }]
    },
    'acquire/sign': {
      displayName: '收房 · 收房签约',
      nodes: [
        { id: 'as1', name: '合同起草', shortName: '合同起草', risk: '高', coverage: 'partial', caseCount: 10, featureCount: 2, gapCount: 1, x: 72, y: 140, featureKeys: [] },
        { id: 'as2', name: '条款审核', shortName: '条款审核', risk: '高', coverage: 'partial', caseCount: 10, featureCount: 2, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'as3', name: '电子签署', shortName: '电子签署', risk: '高', coverage: 'covered', caseCount: 8, featureCount: 1, gapCount: 0, x: 568, y: 140, featureKeys: [] },
        { id: 'as4', name: '合同生效', shortName: '合同生效', risk: '中', coverage: 'partial', caseCount: 6, featureCount: 1, gapCount: 0, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'as1', to: 'as2', type: 'main' }, { from: 'as2', to: 'as3', type: 'main' }, { from: 'as3', to: 'as4', type: 'main' }]
    },
    'acquire/handover': {
      displayName: '收房 · 交割',
      nodes: [
        { id: 'ah1', name: '物品点交', shortName: '物品点交', risk: '高', coverage: 'partial', caseCount: 10, featureCount: 2, gapCount: 1, x: 72, y: 140, featureKeys: [] },
        { id: 'ah2', name: '钥匙交接', shortName: '钥匙交接', risk: '中', coverage: 'partial', caseCount: 8, featureCount: 1, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'ah3', name: '交割关闭', shortName: '交割关闭', risk: '中', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'ah1', to: 'ah2', type: 'main' }, { from: 'ah2', to: 'ah3', type: 'main' }]
    },
    'home/measure': {
      displayName: '家装 · 量房',
      nodes: [
        { id: 'm1', name: '预约量房', shortName: '预约量房', risk: '中', coverage: 'covered', caseCount: 12, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'm2', name: '上门采集', shortName: '上门采集', risk: '高', coverage: 'partial', caseCount: 14, featureCount: 3, gapCount: 1, x: 280, y: 140, featureKeys: [] },
        { id: 'm3', name: '户型校准', shortName: '户型校准', risk: '高', coverage: 'partial', caseCount: 10, featureCount: 2, gapCount: 1, x: 488, y: 140, featureKeys: [] },
        { id: 'm4', name: '量房报告', shortName: '量房报告', risk: '中', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 1, x: 696, y: 140, featureKeys: [] },
        { id: 'm5', name: '同步设计', shortName: '同步设计', risk: '中', coverage: 'partial', caseCount: 6, featureCount: 1, gapCount: 0, x: 904, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'm1', to: 'm2', type: 'main' }, { from: 'm2', to: 'm3', type: 'main' }, { from: 'm3', to: 'm4', type: 'main' }, { from: 'm4', to: 'm5', type: 'main' }]
    },
    'home/design': {
      displayName: '家装 · 设计',
      nodes: [
        { id: 'd1', name: '方案创建', shortName: '方案创建', risk: '中', coverage: 'covered', caseCount: 12, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'd2', name: '效果图生成', shortName: '效果图', risk: '中', coverage: 'partial', caseCount: 10, featureCount: 2, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'd3', name: '清单同步', shortName: '清单同步', risk: '高', coverage: 'partial', caseCount: 8, featureCount: 2, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'd4', name: '设计确认', shortName: '设计确认', risk: '高', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 1, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'd1', to: 'd2', type: 'main' }, { from: 'd2', to: 'd3', type: 'main' }, { from: 'd3', to: 'd4', type: 'main' }]
    },
    'home/contract': {
      displayName: '家装 · 合同',
      nodes: [
        { id: 'c1', name: '合同起草', shortName: '合同起草', risk: '高', coverage: 'partial', caseCount: 4, featureCount: 1, gapCount: 1, x: 72, y: 140, featureKeys: [] },
        { id: 'c2', name: '条款校验', shortName: '条款校验', risk: '高', coverage: 'gap', caseCount: 2, featureCount: 1, gapCount: 2, x: 320, y: 140, featureKeys: [] },
        { id: 'c3', name: '电子签署', shortName: '电子签署', risk: '高', coverage: 'gap', caseCount: 2, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'c4', name: '归档生效', shortName: '归档生效', risk: '中', coverage: 'partial', caseCount: 2, featureCount: 1, gapCount: 0, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'c1', to: 'c2', type: 'main' }, { from: 'c2', to: 'c3', type: 'main' }, { from: 'c3', to: 'c4', type: 'main' }]
    },
    'rentout/listing': {
      displayName: '出房 · 房源',
      nodes: [
        { id: 'rl1', name: '房源发布', shortName: '房源发布', risk: '中', coverage: 'covered', caseCount: 16, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'rl2', name: '定价上架', shortName: '定价上架', risk: '高', coverage: 'covered', caseCount: 14, featureCount: 2, gapCount: 0, x: 320, y: 140, featureKeys: [] },
        { id: 'rl3', name: '展示审核', shortName: '展示审核', risk: '中', coverage: 'partial', caseCount: 12, featureCount: 2, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'rl4', name: '状态流转', shortName: '状态流转', risk: '中', coverage: 'covered', caseCount: 10, featureCount: 1, gapCount: 0, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'rl1', to: 'rl2', type: 'main' }, { from: 'rl2', to: 'rl3', type: 'main' }, { from: 'rl3', to: 'rl4', type: 'main' }]
    },
    'rentout/showing': {
      displayName: '出房 · 带看',
      nodes: [
        { id: 'rs1', name: '带看预约', shortName: '带看预约', risk: '中', coverage: 'covered', caseCount: 14, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'rs2', name: '行程安排', shortName: '行程安排', risk: '中', coverage: 'partial', caseCount: 12, featureCount: 2, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'rs3', name: '带看反馈', shortName: '带看反馈', risk: '中', coverage: 'partial', caseCount: 9, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'rs4', name: '转签约', shortName: '转签约', risk: '高', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 1, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'rs1', to: 'rs2', type: 'main' }, { from: 'rs2', to: 'rs3', type: 'main' }, { from: 'rs3', to: 'rs4', type: 'branch' }]
    },
    'rentout/deal': {
      displayName: '出房 · 出房签约',
      nodes: [
        { id: 'rd1', name: '意向确认', shortName: '意向确认', risk: '中', coverage: 'covered', caseCount: 12, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'rd2', name: '合同生成', shortName: '合同生成', risk: '高', coverage: 'partial', caseCount: 10, featureCount: 2, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'rd3', name: '签署支付', shortName: '签署支付', risk: '高', coverage: 'partial', caseCount: 8, featureCount: 2, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'rd4', name: '入住办理', shortName: '入住办理', risk: '中', coverage: 'gap', caseCount: 5, featureCount: 1, gapCount: 1, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'rd1', to: 'rd2', type: 'main' }, { from: 'rd2', to: 'rd3', type: 'main' }, { from: 'rd3', to: 'rd4', type: 'main' }]
    },
    'homesvc/clean': {
      displayName: '家服 · 保洁',
      nodes: [
        { id: 'hc1', name: '下单预约', shortName: '下单预约', risk: '中', coverage: 'covered', caseCount: 12, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'hc2', name: '派单上门', shortName: '派单上门', risk: '中', coverage: 'covered', caseCount: 10, featureCount: 2, gapCount: 0, x: 320, y: 140, featureKeys: [] },
        { id: 'hc3', name: '服务验收', shortName: '服务验收', risk: '中', coverage: 'partial', caseCount: 8, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'hc4', name: '结算关闭', shortName: '结算关闭', risk: '中', coverage: 'covered', caseCount: 6, featureCount: 1, gapCount: 0, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'hc1', to: 'hc2', type: 'main' }, { from: 'hc2', to: 'hc3', type: 'main' }, { from: 'hc3', to: 'hc4', type: 'main' }]
    },
    'homesvc/repair': {
      displayName: '家服 · 维修',
      nodes: [
        { id: 'hr1', name: '报修提单', shortName: '报修提单', risk: '中', coverage: 'covered', caseCount: 10, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'hr2', name: '派单调度', shortName: '派单调度', risk: '高', coverage: 'partial', caseCount: 8, featureCount: 2, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'hr3', name: '上门处理', shortName: '上门处理', risk: '高', coverage: 'partial', caseCount: 8, featureCount: 2, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'hr4', name: '工单关闭', shortName: '工单关闭', risk: '中', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 1, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'hr1', to: 'hr2', type: 'main' }, { from: 'hr2', to: 'hr3', type: 'main' }, { from: 'hr3', to: 'hr4', type: 'main' }]
    },
    'homesvc/booking': {
      displayName: '家服 · 预约调度',
      nodes: [
        { id: 'hb1', name: '时段占用', shortName: '时段占用', risk: '高', coverage: 'gap', caseCount: 8, featureCount: 1, gapCount: 1, x: 72, y: 140, featureKeys: [] },
        { id: 'hb2', name: '师傅匹配', shortName: '师傅匹配', risk: '高', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'hb3', name: '改约取消', shortName: '改约取消', risk: '中', coverage: 'partial', caseCount: 6, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'hb1', to: 'hb2', type: 'main' }, { from: 'hb2', to: 'hb3', type: 'branch' }]
    },
    'lingzhi/ai-quote': {
      displayName: '灵之 · 智能报价',
      nodes: [
        { id: 'lq1', name: '方案识别', shortName: '方案识别', risk: '高', coverage: 'covered', caseCount: 14, featureCount: 2, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'lq2', name: '智能算价', shortName: '智能算价', risk: '高', coverage: 'covered', caseCount: 12, featureCount: 2, gapCount: 0, x: 320, y: 140, featureKeys: [] },
        { id: 'lq3', name: '结果回写', shortName: '结果回写', risk: '高', coverage: 'partial', caseCount: 10, featureCount: 2, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'lq4', name: '人工复核', shortName: '人工复核', risk: '中', coverage: 'covered', caseCount: 6, featureCount: 1, gapCount: 0, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'lq1', to: 'lq2', type: 'main' }, { from: 'lq2', to: 'lq3', type: 'main' }, { from: 'lq3', to: 'lq4', type: 'branch' }]
    },
    'lingzhi/recommend': {
      displayName: '灵之 · 方案推荐',
      nodes: [
        { id: 'lr1', name: '需求解析', shortName: '需求解析', risk: '中', coverage: 'partial', caseCount: 8, featureCount: 2, gapCount: 1, x: 72, y: 140, featureKeys: [] },
        { id: 'lr2', name: '方案召回', shortName: '方案召回', risk: '高', coverage: 'partial', caseCount: 8, featureCount: 1, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'lr3', name: '推荐排序', shortName: '推荐排序', risk: '中', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'lr1', to: 'lr2', type: 'main' }, { from: 'lr2', to: 'lr3', type: 'main' }]
    },
    'lingzhi/discount': {
      displayName: '灵之 · 智能折扣',
      nodes: [
        { id: 'ld1', name: '折扣识别', shortName: '折扣识别', risk: '高', coverage: 'partial', caseCount: 4, featureCount: 1, gapCount: 0, x: 72, y: 140, featureKeys: [] },
        { id: 'ld2', name: '规则计算', shortName: '规则计算', risk: '高', coverage: 'covered', caseCount: 4, featureCount: 1, gapCount: 0, x: 320, y: 140, featureKeys: [] },
        { id: 'ld3', name: '金额回写', shortName: '金额回写', risk: '高', coverage: 'partial', caseCount: 2, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'ld1', to: 'ld2', type: 'main' }, { from: 'ld2', to: 'ld3', type: 'main' }]
    },
    'qixin/notify': {
      displayName: '企信 · 消息触达',
      nodes: [
        { id: 'qn1', name: '事件订阅', shortName: '事件订阅', risk: '中', coverage: 'partial', caseCount: 10, featureCount: 2, gapCount: 1, x: 72, y: 140, featureKeys: [] },
        { id: 'qn2', name: '消息组装', shortName: '消息组装', risk: '中', coverage: 'partial', caseCount: 10, featureCount: 1, gapCount: 0, x: 320, y: 140, featureKeys: [] },
        { id: 'qn3', name: '多端推送', shortName: '多端推送', risk: '高', coverage: 'gap', caseCount: 8, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'qn1', to: 'qn2', type: 'main' }, { from: 'qn2', to: 'qn3', type: 'main' }]
    },
    'qixin/approve': {
      displayName: '企信 · 审批流',
      nodes: [
        { id: 'qa1', name: '提单发起', shortName: '提单发起', risk: '中', coverage: 'gap', caseCount: 8, featureCount: 1, gapCount: 1, x: 72, y: 140, featureKeys: [] },
        { id: 'qa2', name: '节点流转', shortName: '节点流转', risk: '高', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 2, x: 320, y: 140, featureKeys: [] },
        { id: 'qa3', name: '加签转办', shortName: '加签转办', risk: '高', coverage: 'gap', caseCount: 5, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] },
        { id: 'qa4', name: '办结回调', shortName: '办结回调', risk: '中', coverage: 'partial', caseCount: 5, featureCount: 1, gapCount: 0, x: 816, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'qa1', to: 'qa2', type: 'main' }, { from: 'qa2', to: 'qa3', type: 'branch' }, { from: 'qa2', to: 'qa4', type: 'main' }]
    },
    'qixin/todo': {
      displayName: '企信 · 待办协同',
      nodes: [
        { id: 'qt1', name: '待办生成', shortName: '待办生成', risk: '中', coverage: 'gap', caseCount: 8, featureCount: 1, gapCount: 1, x: 72, y: 140, featureKeys: [] },
        { id: 'qt2', name: '处理反馈', shortName: '处理反馈', risk: '中', coverage: 'gap', caseCount: 6, featureCount: 1, gapCount: 1, x: 320, y: 140, featureKeys: [] },
        { id: 'qt3', name: '超时升级', shortName: '超时升级', risk: '高', coverage: 'partial', caseCount: 4, featureCount: 1, gapCount: 1, x: 568, y: 140, featureKeys: [] }
      ],
      edges: [{ from: 'qt1', to: 'qt2', type: 'main' }, { from: 'qt2', to: 'qt3', type: 'branch' }]
    }
  }
};
