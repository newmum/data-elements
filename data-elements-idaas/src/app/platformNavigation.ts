import { systemName } from '../domain/branding.ts';
/** 与各能力中心现有部署地址保持一致；生产地址可通过环境变量覆盖。 */
export const productGroups = [
    { name: '数据建设', centers: [
        { id: 'resource', name: '数据资源中心', description: '数据建设、模型与仓库', mark: '磐', color: '#1677FF', owner: 'wanxiang', home: '/resource/overview' },
        { id: 'integration', name: '数据集成中心', description: '数据接入、同步与分发', mark: '海', color: '#08979C', owner: 'haitong', home: '/integration/overview' },
        { id: 'compute', name: '数据计算中心', description: '数据加工、计算与分析', mark: '风', color: '#2F54EB', owner: 'qizhi', home: '/compute/overview' },
    ] },
    { name: '治理与管控', centers: [
        { id: 'governance', name: '数据治理中心', description: '元数据、标准与数据质量', mark: '万', color: '#531DAB', owner: 'wanxiang', home: '/governance/overview' },
        { id: 'security', name: '数据安全中心', description: '敏感发现、授权与安全防护', mark: '盾', color: '#389E0D', owner: 'qizhi', home: '/security/overview' },
    ] },
    { name: '运营与交付', centers: [
        { id: 'asset', name: '数据资产中心', description: '资产盘点、流通与运营', mark: '皓', color: '#D48806', owner: 'wanxiang', home: '/assets/my' },
        { id: 'service', name: '数据服务中心', description: '服务发布与运行交付', mark: '梯', color: '#5B8C00', owner: 'qizhi', home: '/services/overview' },
    ] },
    { name: '分析与应用', centers: [
        { id: 'visualization', name: '数据可视化中心', description: '云图大屏与 BI 分析', mark: '图', color: '#D46B08', owner: 'qizhi', home: '/visualization/overview' },
        { id: 'knowledge', name: '知识图谱中心', description: '关系探索与知识推理', mark: '启', color: '#722ED1', owner: 'qizhi', home: '/overview' },
        { id: 'search', name: '智能搜索中心', description: '信息检索与知识发现', mark: '搜', color: '#08979C', owner: 'qizhi', home: '/search-center/portal' },
    ] },
    { name: '平台支撑', centers: [
        { id: 'idaas', name: systemName, description: '统一账户、认证与访问管理', mark: '鉴', color: '#5267F5', owner: 'idaas', home: '/console/workforce/overview' },
        { id: 'ops', name: '数据运行智能监控平台', description: '平台运行、指标与告警', mark: '晨', color: '#2F54EB', owner: 'qizhi', home: '/ops/overview' },
        { id: 'ai', name: '人工智能支撑平台', description: 'AI 编排与智能交互', mark: '星', color: '#531DAB', owner: 'qizhi', home: '/ai/overview' },
    ] },
] as const;

export type ProductCenter = typeof productGroups[number]['centers'][number];
type Owner = ProductCenter['owner'];
export type CenterAddresses = Record<Owner, string>;

export const centerAddresses = (): CenterAddresses => ({
    wanxiang: import.meta.env.VITE_WANXIANG_GOVERNANCE_URL?.trim() || (import.meta.env.DEV ? 'http://localhost:3010/' : '/wanxiang-governance/'),
    qizhi: import.meta.env.VITE_QIZHI_CENTER_URL?.trim() || (import.meta.env.DEV ? 'http://localhost:3001/' : '/qizhi/'),
    haitong: import.meta.env.VITE_HAITONG_CENTER_URL?.trim() || (import.meta.env.DEV ? 'http://localhost:3002/' : '/haitong/'),
    idaas: import.meta.env.VITE_IDAAS_CENTER_URL?.trim() || (import.meta.env.DEV ? 'http://localhost:3005/' : '/idaas/'),
});

/** 只构造目标路由；不传递口令、会话令牌或改变租户。 */
export function productCenterUrl(center: ProductCenter, addresses: CenterAddresses, currentUrl: string): string {
    const url = new URL(addresses[center.owner], currentUrl);
    url.hash = center.home;
    return url.href;
}
