import type { ReactNode } from 'react';
import { Avatar, Badge, Card, Tag } from 'antd';
import { AppstoreOutlined, ApartmentOutlined, CloudServerOutlined, DatabaseOutlined, FileProtectOutlined, FundProjectionScreenOutlined, IdcardOutlined, SafetyCertificateOutlined, SolutionOutlined, TeamOutlined } from '@ant-design/icons';
import { useDatabase } from '../mock/store';
import type { Application, Domain, User } from '../domain/types';
import { Text } from './common';
export const tones = [
    { color: '#536CF5', bg: '#EDF0FF', light: '#D8E1FF' },
    { color: '#8654EB', bg: '#F3EDFF', light: '#E2D5FB' },
    { color: '#179FE7', bg: '#EAF6FF', light: '#D2EAFB' },
    { color: '#12AF86', bg: '#E8F9F3', light: '#BFEFDE' },
    { color: '#F29232', bg: '#FFF3E8', light: '#FFE1C4' },
    { color: '#EB617B', bg: '#FFF0F3', light: '#FCD8E1' },
];
export function toneFor(id: string) { return tones[Array.from(id).reduce((a, c) => a + c.charCodeAt(0), 0) % tones.length]; }
export function UserAvatar({ user, size = 32 }: {
    user: Pick<User, 'id' | 'name'>;
    size?: number;
}) {
    const t = toneFor(user.id);
    return <Avatar size={size} className="identity-avatar" style={{ background: `linear-gradient(145deg, ${t.light}, ${t.bg})`, color: t.color, fontSize: size > 48 ? 25 : 13, fontWeight: 600, border: '2px solid #fff', boxShadow: '0 2px 7px rgba(40,62,114,.07)' }}>{user.name.slice(-2)}</Avatar>;
}
export function AppIcon({ app, size = 36 }: {
    app: Pick<Application, 'id' | 'group' | 'logoUrl'>;
    size?: number;
}) {
    const t = toneFor(app.id);
    const index = Number(app.id.replace(/\D/g, '') || '0') % 7;
    const icons = [<DatabaseOutlined />, <AppstoreOutlined />, <SolutionOutlined />, <CloudServerOutlined />, <FundProjectionScreenOutlined />, <FileProtectOutlined />, <IdcardOutlined />];
    const logo = app.logoUrl && /^(data:image\/(png|jpeg|webp|x-icon|svg\+xml);base64,|https:\/\/)/i.test(app.logoUrl) ? app.logoUrl : '';
    return <span className="app-icon" style={{ width: size, height: size, fontSize: size * .48, color: t.color, background: `linear-gradient(140deg,${t.bg},${t.light}90)` }}>{icons[index]}{logo && <img src={logo} alt="" onError={event => { event.currentTarget.hidden = true; }}/>}</span>;
}
export function Sparkline({ values, color = '#6177FA' }: {
    values: number[];
    color?: string;
}) {
    const min = Math.min(...values), max = Math.max(...values), h = 30, w = 80;
    const points = values.map((v, i) => `${i * w / Math.max(1, values.length - 1)},${h - 4 - (v - min) / Math.max(1, max - min) * (h - 8)}`).join(' ');
    return <svg viewBox={`0 0 ${w} ${h}`} width="80" height="30" aria-hidden="true"><polyline points={points} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>;
}
export function Donut({ items, label = '总计' }: {
    items: {
        name: string;
        value: number;
        color?: string;
    }[];
    label?: string;
}) {
    const total = items.reduce((n, v) => n + v.value, 0);
    let start = 0;
    return <div className="donut-layout"><div className="donut-graphic" role="img" aria-label={items.map(x => `${x.name}${x.value}`).join('，')}><svg viewBox="0 0 180 180" width="100%" height="100%" aria-hidden="true"><circle cx="90" cy="90" r="66" fill="none" stroke="#F0F3FA" strokeWidth="22"/>{items.map((d, i) => { const fraction = d.value / (total || 1); const offset = start; start += fraction; return <circle key={d.name} cx="90" cy="90" r="66" fill="none" stroke={d.color || tones[i % tones.length].color} strokeWidth="22" pathLength="100" strokeDasharray={`${Math.max(0, fraction * 100 - (fraction > 0 ? 1.6 : 0))} 100`} strokeDashoffset={-offset * 100} transform="rotate(-90 90 90)"/>; })}</svg><div className="donut-center" aria-hidden="true"><span>{label}</span><strong>{total.toLocaleString()}</strong><small>当前身份域</small></div></div><div className="donut-legend">{items.map((d, i) => <div key={d.name}><span className="legend-dot" style={{ background: d.color || tones[i % tones.length].color }}/><span title={d.name}>{d.name}</span><strong>{d.value.toLocaleString()}</strong><em>{total ? Math.round(d.value / total * 100) : 0}%</em></div>)}</div></div>;
}
export function SectionIntro({ icon, title, text, extra }: {
    icon?: ReactNode;
    title: string;
    text: string;
    extra?: ReactNode;
}) {
    return <div className="section-intro"><span className="intro-icon">{icon || <SafetyCertificateOutlined />}</span><div><h2 className="section-intro-title">{title}</h2><p>{text}</p></div>{extra && <div className="intro-extra">{extra}</div>}</div>;
}
export function ModuleSummary({ domain, category }: {
    domain: Domain;
    category: string;
}) {
    const db = useDatabase();
    const table = category === 'groups' ? db.groups : category === 'entities' ? db.legalEntities : category === 'sync' ? db.syncConfigs : db.catalog.filter(c => c.category === category);
    const rows = table.filter(v => v.domain === domain);
    const configs: Record<string, [
        string,
        string
    ]> = {
        'app-group': ['有序组织应用生态', '围绕数据服务、协同办公和核心业务建立应用分组。'],
        delegate: ['分级管理，职责清晰', '将管理员与机构范围关联，明确组织管理的授权边界。'],
        line: ['连接跨组织业务条线', '集中维护条线信息、关联机构与业务归属。'],
        dictionary: ['统一组织基础字典', '标准化职务、岗位、职级和职称，让人员信息保持一致。'],
        extension: ['灵活扩展身份信息', '为用户、机构、应用和资源定义统一的扩展字段。'],
        'api-grant': ['精细控制接口访问', '围绕调用主体、权限范围和有效期限管理 API 授权。'],
        admin: ['统一管理平台成员', '区分管理职责与业务访问，避免权限混用。'],
        'system-role': ['以角色组织管理权限', '将功能范围、应用范围和机构范围组合成明确的管理职责。'],
        groups: ['一次配置，跨应用协同', '把常用应用与角色组合成权限组，按工作职责统一授予。'],
        entities: ['统一维护企业身份', '集中查看法人资料、联系人、核验状态与关联账号。'],
        sync: ['身份资料，按需下发', '统一维护组织与人员，需要时手动下发至业务应用。'],
    };
    const content = configs[category] || ['集中管理基础信息', '统一配置、持续维护，确保各应用使用一致的信息。'];
    return <SectionIntro title={content[0]} text={content[1]} icon={category === 'entities' ? <ApartmentOutlined /> : category === 'groups' ? <TeamOutlined /> : undefined} extra={<div className="intro-stats"><div><span>全部配置</span><strong>{rows.length}</strong></div><div><span>已启用</span><strong>{rows.filter(r => r.status === 'enabled').length}</strong></div><Tag color="blue">{domain === 'workforce' ? '政企身份域' : '公众身份域'}</Tag></div>}/>;
}
