/* 家装报价 · 最小交付四表（系统配置页主数据） */
window.__QUOTE_MIN_DELIVERY__ = {
  domain: '家装',
  app: '报价',
  updatedAt: '2025-05-21',
  nodeEdges: [
    { id: 'ne1', from: '报价触发与报价单生成', to: '报价场景识别与价格来源', relation: '主流程', strength: '强', source: 'quote.json 节点顺序', status: '待确认' },
    { id: 'ne2', from: '报价场景识别与价格来源', to: '参数解析与输入精度', relation: '主流程', strength: '强', source: 'quote.json 节点顺序', status: '待确认' },
    { id: 'ne3', from: '参数解析与输入精度', to: '物料处理与自动带出', relation: '主流程', strength: '强', source: 'quote.json 节点顺序', status: '待确认' },
    { id: 'ne4', from: '物料处理与自动带出', to: '金额计算与汇总', relation: '主流程', strength: '强', source: 'quote.json 节点顺序', status: '待确认' },
    { id: 'ne5', from: '金额计算与汇总', to: '造价单生成与明细落库', relation: '主流程', strength: '强', source: 'quote.json 节点顺序', status: '待确认' },
    { id: 'ne6', from: '造价单生成与明细落库', to: '造价提交与审核', relation: '主流程', strength: '强', source: 'quote.json 节点顺序', status: '待确认' },
    { id: 'ne7', from: '造价提交与审核', to: '实勘审核', relation: '分支', strength: '弱', source: 'domain_panorama', status: '待确认' },
    { id: 'ne8', from: '造价提交与审核', to: '申诉审核', relation: '分支', strength: '弱', source: 'domain_panorama', status: '待确认' },
    { id: 'ne9', from: '造价提交与审核', to: '生命周期与外部联动', relation: '分支', strength: '强', source: 'domain_panorama', status: '待确认' },
    { id: 'ne10', from: '金额计算与汇总', to: '前端展示与金额明细', relation: '分支', strength: '弱', source: 'domain_panorama', status: '待确认' },
    { id: 'ne11', from: '参数解析与输入精度', to: '前端展示与金额明细', relation: '弱依赖', strength: '弱', source: 'domain_panorama', status: '待确认' }
  ],
  featureApis: [
    { id: 'fa1', node: '报价触发与报价单生成', feature: '数量价汇总', method: 'POST', path: '/quotation/offer', relation: '主接口', controller: 'QuotationApi#offerQuotations', status: '待确认' },
    { id: 'fa2', node: '报价触发与报价单生成', feature: '报价单生成', method: 'POST', path: '/quotation/offer', relation: '主接口', controller: 'QuotationApi#offerQuotations', status: '待确认' },
    { id: 'fa3', node: '报价触发与报价单生成', feature: '物料自动带出', method: 'POST', path: '/quotation/bim/offer', relation: '主接口', controller: 'QuotationBimApi#offerQuotations', status: '待确认' },
    { id: 'fa4', node: '造价单生成与明细落库', feature: '造价明细落库', method: 'POST', path: '/quotation/createDesignCost', relation: '主接口', controller: 'QuotationApi#createDesignCost', status: '待确认' },
    { id: 'fa5', node: '造价提交与审核', feature: '提交审核', method: 'POST', path: '/quotation/submitDesignCost', relation: '主接口', controller: 'QuotationApi#submitDesignCost', status: '待确认' },
    { id: 'fa6', node: '造价提交与审核', feature: '自动审核判定', method: 'POST', path: '/quotation/submitDesignCost', relation: '主接口', controller: 'QuotationApi#submitDesignCost', status: '待确认' },
    { id: 'fa7', node: '参数解析与输入精度', feature: '参数必填校验', method: 'POST', path: '/quotation/checkParamBeforeSubmit', relation: '校验接口', controller: 'QuotationApi#checkParamBeforeSubmit', status: '待确认' },
    { id: 'fa8', node: '造价提交与审核', feature: '库存预占与失效', method: 'POST', path: '/quotation/confirmDesignCost', relation: '主接口', controller: 'QuotationApi#confirmDesignCost', status: '待确认' },
    { id: 'fa9', node: '实勘审核', feature: '提交审核', method: 'POST', path: '/quotation/reviewSubmit', relation: '主接口', controller: 'QuotationApi#reviewSubmit', status: '待确认' }
  ],
  apiDownstreams: [
    { id: 'ad1', method: 'POST', path: '/quotation/offer', system: 'BOM', target: 'BomOuterRpcService.findBomParams', callType: 'Dubbo', sync: '同步', strength: '强', node: '报价触发与报价单生成', status: '待确认' },
    { id: 'ad2', method: 'POST', path: '/quotation/offer', system: '房源', target: 'HouseInfoAdaptor.findHouseInfo', callType: 'HTTP', sync: '同步', strength: '强', node: '报价场景识别与价格来源', status: '待确认' },
    { id: 'ad3', method: 'POST', path: '/quotation/offer', system: '人员', target: 'PersonAdaptor.getPersonInfo', callType: 'HTTP', sync: '同步', strength: '弱', node: '报价触发与报价单生成', status: '待确认' },
    { id: 'ad4', method: 'POST', path: '/quotation/offer', system: '造价库', target: 'jz_design_cost.saveDesignCost', callType: 'DB', sync: '同步', strength: '强', node: '造价单生成与明细落库', status: '待确认' },
    { id: 'ad5', method: 'POST', path: '/quotation/bim/offer', system: 'BOM', target: 'BomInfoAdaptor.findNBomGoodsFilterStandWithStock', callType: 'HTTP', sync: '同步', strength: '强', node: '物料处理与自动带出', status: '待确认' },
    { id: 'ad6', method: 'POST', path: '/quotation/submitDesignCost', system: 'MQ', target: 'ex_design_cost', callType: 'MQ', sync: '异步', strength: '强', node: '造价提交与审核', status: '待确认' },
    { id: 'ad7', method: 'POST', path: '/quotation/confirmDesignCost', system: 'MQ', target: 'ex_design_cost 审核结果', callType: 'MQ', sync: '异步', strength: '强', node: '生命周期与外部联动', status: '待确认' },
    { id: 'ad8', method: 'POST', path: '/quotation/confirmDesignCost', system: '库存', target: 'EventBusHandler 预占库存', callType: 'RPC', sync: '异步', strength: '强', node: '生命周期与外部联动', status: '待确认' },
    { id: 'ad9', method: 'POST', path: '/quotation/reviewSubmit', system: 'MQ', target: 'EX_settle_quotation_review', callType: 'MQ', sync: '异步', strength: '强', node: '实勘审核', status: '待确认' }
  ],
  caseLinks: [
    { id: 'TC-001', name: '标准方案数量价计算正确', priority: 'P0', node: '金额计算与汇总', feature: '数量价汇总', api: 'POST /quotation/offer', status: '样例' },
    { id: 'TC-002', name: '优惠折扣后数量价正确', priority: 'P0', node: '金额计算与汇总', feature: '数量价汇总', api: 'POST /quotation/offer', status: '样例' },
    { id: 'TC-201', name: '满足免审条件自动通过', priority: 'P0', node: '造价提交与审核', feature: '自动审核判定', api: 'POST /quotation/submitDesignCost', status: '样例' },
    { id: 'TC-202', name: '抽审比例命中转人工', priority: 'P1', node: '造价提交与审核', feature: '自动审核判定', api: 'POST /quotation/submitDesignCost', status: '样例' }
  ]
};
