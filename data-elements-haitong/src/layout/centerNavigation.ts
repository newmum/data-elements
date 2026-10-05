/** Keep center destinations aligned with the platform and Wanxiang switchers. */
const base = (configured: string | undefined, development: string, production: string): string =>
  configured?.trim() || (import.meta.env.DEV ? development : production);

const routeUrl = (root: string, route: string): string => {
  const url = new URL(root, window.location.href);
  url.hash = route;
  return url.href;
};

const wanxiang = base(import.meta.env.VITE_WANXIANG_GOVERNANCE_URL, 'http://localhost:3010/', '/wanxiang-governance/');
const qizhi = base(import.meta.env.VITE_QIZHI_BASE_URL, 'http://localhost:3001/', '/qizhi/');

export const centers = [
  { key: 'resource', name: '数据资源中心', description: '数仓规划、建模与资源交付', url: routeUrl(wanxiang, '/resource/overview') },
  { key: 'integration', name: '数据集成中心', description: '数据接入、同步与分发', url: routeUrl(window.location.href, '/overview') },
  { key: 'compute', name: '数据计算中心', description: '数据加工、计算与分析', url: routeUrl(qizhi, '/compute/overview') },
  { key: 'governance', name: '数据治理中心', description: '元数据、标准与数据质量', url: routeUrl(wanxiang, '/governance/overview') },
  { key: 'security', name: '数据安全中心', description: '敏感发现、授权与安全防护', url: routeUrl(qizhi, '/security/overview') },
  { key: 'asset', name: '数据资产中心', description: '资产盘点、流通与运营', url: routeUrl(wanxiang, '/assets/overview') },
  { key: 'service', name: '数据服务中心', description: '服务发布与运行交付', url: routeUrl(qizhi, '/services/overview') },
  { key: 'visualization', name: '数据可视化中心', description: '云图大屏与 BI 分析', url: routeUrl(base(import.meta.env.VITE_REPORT_APP_URL, 'http://localhost:3004/', '/report/'), '/') },
  { key: 'knowledge', name: '知识图谱中心', description: '关系探索与知识推理', url: routeUrl(qizhi, '/overview') },
  { key: 'search', name: '智能搜索中心', description: '信息检索与知识发现', url: new URL(base(import.meta.env.VITE_SEARCH_APP_URL, 'http://localhost:3300/', '/search/'), window.location.href).href },
  { key: 'identity', name: '统一身份管理平台', description: '统一账户、认证与访问管理', url: routeUrl(base(import.meta.env.VITE_IDAAS_CENTER_URL, 'http://localhost:3005/', '/idaas/'), '/console/workforce/overview') },
  { key: 'ops', name: '数据运行智能监控平台', description: '平台运行、指标与告警', url: routeUrl(qizhi, '/ops/overview') },
  { key: 'ai', name: '人工智能支撑平台', description: 'AI 编排与智能交互', url: routeUrl(qizhi, '/ai/overview') },
];

export const centerGroups: Array<[string, string[]]> = [
  ['数据建设', ['resource', 'integration', 'compute']],
  ['治理与管控', ['governance', 'security']],
  ['运营与交付', ['asset', 'service']],
  ['分析与应用', ['visualization', 'knowledge', 'search']],
  ['平台支撑', ['identity', 'ops', 'ai']],
];

export const centerMarks: Record<string, [string, string]> = {
  resource: ['磐', '#1677FF'], integration: ['海', '#08979C'], compute: ['风', '#2F54EB'],
  governance: ['万', '#531DAB'], security: ['盾', '#389E0D'], asset: ['皓', '#D48806'],
  service: ['梯', '#5B8C00'], visualization: ['图', '#D46B08'], knowledge: ['启', '#722ED1'],
  search: ['搜', '#08979C'], identity: ['鉴', '#5267F5'], ops: ['晨', '#2F54EB'], ai: ['星', '#531DAB'],
};
