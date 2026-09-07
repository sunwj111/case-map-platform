/** FeatureMapDTO / MapNode 前端契约（M1-S02 / M1-S03） */

export type CoverageStatus = 'covered' | 'partial' | 'gap';

export type MapNodeType =
  | 'feature'
  | 'neighbor'
  | 'scene'
  | 'case'
  | 'api'
  | 'rule'
  | 'script'
  | 'defect'
  | 'data'
  | 'execution'
  | 'service'
  | 'module';

export type MapNodeStatus = 'ready' | 'review' | 'missing' | 'deprecated';

export interface FeatureMapMeta {
  mapId: string;
  featureKey: string;
  version: string;
  baseline?: string;
  updatedAt?: string;
  qualityOwner?: string;
  dataSources?: string[];
}

export interface NeighborFeature {
  featureKey: string;
  featureName: string;
}

export interface BusinessSpine {
  domain: string;
  app: string;
  processName?: string;
  featureName: string;
  sceneName: string;
  valueTags?: string[];
  neighborFeatures?: NeighborFeature[];
}

export interface ScenarioItem {
  id: string;
  name: string;
  coverageStatus: CoverageStatus;
  caseCount: number;
}

export interface CaseItem {
  id: string;
  name: string;
  priority?: string;
  testScenario?: string;
  confidence?: number;
  mountAdvice?: string;
  api?: string;
}

export interface ScriptItem {
  id: string;
  name: string;
  type?: string;
  status?: string;
  linkedCaseId?: string;
}

export interface DataTemplateItem {
  id: string;
  name: string;
  linkedScenarioId?: string;
}

export interface ExecutionItem {
  id: string;
  label: string;
  results?: string[];
  lastRunAt?: string;
}

export interface BusinessView {
  scenarios?: ScenarioItem[];
  cases?: CaseItem[];
  scripts?: ScriptItem[];
  dataTemplates?: DataTemplateItem[];
  executions?: ExecutionItem[];
}

export interface ServiceItem {
  name: string;
  role?: string;
}

export interface FlowNodeItem {
  name: string;
  risk?: string;
  isPrimary?: boolean;
}

export interface ApiItem {
  method: string;
  path: string;
  label?: string;
  relation?: string;
  controller?: string;
  confidence?: number;
}

export interface CodeModuleItem {
  name: string;
  type?: string;
}

export interface TechView {
  source?: string;
  services?: ServiceItem[];
  flowNodes?: FlowNodeItem[];
  apis?: ApiItem[];
  tables?: string[];
  messages?: string[];
  codeModules?: CodeModuleItem[];
}

export interface RuleItem {
  id: string;
  name: string;
  source?: string;
}

export interface DefectItem {
  id: string;
  title: string;
  severity?: string;
  openedAt?: string;
}

export interface RiskTagItem {
  type: string;
  label: string;
  level?: string;
}

export interface RiskView {
  rules?: RuleItem[];
  defects?: DefectItem[];
  tags?: RiskTagItem[];
}

export interface QualitySummary {
  linkedCaseCount: number;
  priorityDistribution?: Record<string, number>;
  automationCoverage?: number;
  defectCount30d?: number;
  gapCount?: number;
  coverageStatus?: string;
}

export interface MapConsumer {
  platform: string;
  usage: string;
}

export interface FeatureMapDto {
  meta: FeatureMapMeta;
  spine: BusinessSpine;
  businessView: BusinessView;
  techView: TechView;
  riskView: RiskView;
  summary: QualitySummary;
  consumers?: MapConsumer[];
}

export interface MapNode {
  id: string;
  type: MapNodeType;
  title: string;
  status?: MapNodeStatus;
  confidence?: number | null;
  payload?: unknown;
}
