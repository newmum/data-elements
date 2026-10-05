import { useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Space, Tag } from 'antd';
import { PlusOutlined, SettingOutlined } from '@ant-design/icons';
import { api, useDatabase } from '../mock/store';
import { makeBase } from '../domain/engine';
import type { Catalog, Domain } from '../domain/types';
import { ConfirmDelete, DataTable, exportCsv, options, PageTitle, RecordEditor, StatusTag, Text, useEditable, type Field, type FormValues } from '../components/common';
import { ModuleSummary, toneFor } from '../components/visuals';
interface Config {
    title: string;
    description: string;
    category: string;
    subjectType?: string;
}
const config: Record<string, Config> = {
    'app-groups': { title: '应用分组', description: '按业务场景维护应用分组，不改变应用访问资格。', category: 'app-group' },
    'delegates': { title: '分级管理员', description: '将人员与机构管理范围关联，明确下放的职责边界。', category: 'delegate' },
    'org-settings/lines': { title: '条线管理', description: '维护跨组织的业务条线归属。', category: 'line' },
    'org-settings/dictionaries': { title: '职务岗位', description: '统一管理岗位、职务、职级、职称的标准取值。', category: 'dictionary' },
    'org-settings/fields': { title: '扩展字段', description: '定义用户、机构、应用、资源的扩展字段元数据。', category: 'extension' },
    'api-grants/apps': { title: 'API 应用授权', description: '限定应用机器身份可调用的管理接口。', category: 'api-grant', subjectType: 'app' },
    'api-grants/users': { title: 'API 用户授权', description: '限定用户身份的管理接口调用范围。', category: 'api-grant', subjectType: 'user' },
    'system/access/admins': { title: '管理员管理', description: '维护平台成员的系统角色与管理范围。', category: 'admin' },
    'system/access/roles': { title: '系统角色', description: '平台管理权限与应用业务角色分开定义。', category: 'system-role' }
};
export default function CatalogPage({ domain, path }: {
    domain: Domain;
    path: string;
}) {
    const db = useDatabase();
    const cfg = config[path];
    const editable = useEditable(path === 'app-groups' ? 'app-groups' : ['line', 'dictionary', 'extension'].includes(cfg.category) ? 'directory' : 'system');
    const [editing, setEditing] = useState<Catalog | null>(null);
    const [open, setOpen] = useState(false);
    const [detail, setDetail] = useState<Catalog | null>(null);
    const data = db.catalog.filter(c => c.domain === domain && c.category === cfg.category && (!cfg.subjectType || c.subjectType === cfg.subjectType));
    const base: Catalog = editing || { ...makeBase('', domain, 'catalog'), category: cfg.category, code: '', description: '', parentId: '', appId: '', orgId: '', userId: '', memberIds: [], permissions: [], value: '', expiresAt: '', subjectType: cfg.subjectType || '' };
    const fields: Field[] = [{ name: 'name', label: cfg.category === 'admin' ? '成员名称' : '名称', required: true, span: 12 }, { name: 'code', label: cfg.category === 'admin' ? '登录账号' : '唯一编码', required: true, span: 12, disabled: !!editing && ['line', 'dictionary', 'extension'].includes(cfg.category) }, ...(['line', 'dictionary', 'extension'].includes(cfg.category) ? [] : [{ name: 'description', label: '说明', type: 'textarea' as const }])];
    if (['delegate', 'admin'].includes(cfg.category))
        fields.push({ name: 'userId', label: '关联用户', type: 'select', required: true, options: options(db.users.filter(u => u.domain === domain)) }, { name: 'orgId', label: '管理机构范围', type: 'select', required: cfg.category === 'delegate', options: options(db.orgs.filter(o => o.domain === domain)), help: '管理范围以所选机构及下级范围为准。' });
    if (cfg.category === 'admin')
        fields.push({ name: 'permissions', label: '系统角色', type: 'multi', required: true, options: db.catalog.filter(c => c.domain === domain && c.category === 'system-role').map(c => ({ value: c.code, label: c.name })) });
    if (cfg.category === 'delegate')
        fields.push({ name: 'permissions', label: '下放的操作权限', type: 'multi', required: true, options: [{ value: 'users:write', label: '管理人员' }, { value: 'orgs:write', label: '管理机构' }, { value: 'delegate:write', label: '允许再次委派' }] }, { name: 'value', label: '下级范围', type: 'select', options: [{ value: '包含下级', label: '包含下级机构' }, { value: '仅本机构', label: '仅本机构' }] });
    if (cfg.category === 'line')
        fields.push({ name: 'parentId', label: '上级条线', type: 'select', options: options(data.filter(item => item.id !== editing?.id)), help: '机构条线归属在机构资料中维护。' });
    if (cfg.category === 'dictionary')
        fields.push({ name: 'value', label: '字典类型', type: 'select', required: true, disabled: !!editing, options: [{ value: 'POST', label: '岗位' }, { value: 'POSITION', label: '职务' }, { value: 'RANK', label: '职级' }, { value: 'TITLE', label: '职称' }] }, { name: 'parentId', label: '上级字典', type: 'select', options: options(data.filter(item => item.id !== editing?.id)) });
    if (cfg.category === 'extension')
        fields.push({ name: 'subjectType', label: '扩展对象', type: 'select', required: true, disabled: !!editing, options: [{ value: 'SUBJECT', label: '人员' }, { value: 'ORG', label: '机构' }, { value: 'RESOURCE', label: '资源' }], span: 12 }, { name: 'value', label: '数据类型', type: 'select', required: true, disabled: !!editing, options: [{ value: 'STRING', label: '文本' }, { value: 'NUMBER', label: '数字' }, { value: 'DATE', label: '日期' }, { value: 'ENUM', label: '枚举' }, { value: 'BOOLEAN', label: '布尔' }], span: 12 }, { name: 'required', label: '必填', type: 'switch', span: 12 }, { name: 'sensitive', label: '敏感字段', type: 'switch', span: 12 }, { name: 'validationText', label: '校验规则', type: 'textarea', max: 4000, help: '可填写 maxLength、min、max、options、default 的 JSON。敏感字段不允许配置默认值。' });
    if (cfg.category === 'api-grant')
        fields.push(cfg.subjectType === 'app' ? { name: 'appId', label: '被授权应用', type: 'select', required: true, options: options(db.apps.filter(a => a.domain === domain)) } : { name: 'userId', label: '被授权用户', type: 'select', required: true, options: options(db.users.filter(u => u.domain === domain && u.kind !== 'admin')) }, { name: 'permissions', label: 'API 权限范围', type: 'multi', required: true, options: ['users:read', 'orgs:read', 'roles:read', 'grants:read', 'audit:read'].map(v => ({ value: v, label: v })) }, { name: 'expiresAt', label: '到期时间', type: 'date', help: '留空为长期有效；建议按工作期限授予访问资格。' });
    if (cfg.category === 'system-role')
        fields.push({ name: 'permissions', label: '平台功能权限', type: 'multi', required: true, options: ['users:read', 'users:write', 'apps:read', 'apps:write', 'grants:read', 'grants:write', 'audit:read', 'system:write'].map(v => ({ value: v, label: v })) }, { name: 'orgId', label: '机构范围', type: 'select', options: options(db.orgs.filter(o => o.domain === domain)) }, { name: 'appId', label: '应用范围', type: 'select', options: options(db.apps.filter(a => a.domain === domain)) });
    fields.push({ name: 'status', label: '状态', type: 'select', options: [{ value: 'enabled', label: '启用' }, { value: 'disabled', label: '停用' }] });
    return <><PageTitle title={cfg.title} description={cfg.description} extra={<Button type="primary" disabled={!editable} icon={<PlusOutlined />} onClick={() => { setEditing(null); setOpen(true); }}>新建{cfg.category === 'admin' ? '管理员' : '配置'}</Button>}/><ModuleSummary domain={domain} category={cfg.category}/>{['system-role', 'admin', 'delegate', 'api-grant', 'line', 'extension'].includes(cfg.category) && <Alert title={cfg.category === 'admin' || cfg.category === 'system-role' ? '分配管理员角色前，请核对平台功能权限和管理范围。' : '请核对配置对象与关联范围，避免授予超出工作职责的权限。'} type="info" showIcon style={{ marginBottom: 16 }}/>}<DataTable data={data} searchFields={['name', 'code', 'description']} title={cfg.title} exporter={rows => exportCsv(cfg.title, ['名称', '编码', '状态', '说明'], rows.map(r => [r.name, r.code, r.status, r.description]))} columns={[{ title: '名称', dataIndex: 'name', width: 200, render: (v, r) => <Space><span className="catalog-icon" style={{ background: toneFor(r.id).bg, color: toneFor(r.id).color }}><SettingOutlined /></span><Button type="link" style={{ padding: 0 }} onClick={() => setDetail(r)}>{v}</Button></Space> }, { title: '编码', dataIndex: 'code', width: 180 }, { title: '配置摘要', width: 280, render: (_, r) => <Space wrap>{r.appId && <Tag>{db.apps.find(a => a.id === r.appId)?.name}</Tag>}{r.orgId && <Tag>{db.orgs.find(o => o.id === r.orgId)?.name}</Tag>}{r.userId && <Tag>{db.users.find(u => u.id === r.userId)?.name}</Tag>}{r.value && <Tag>{r.value}</Tag>}{r.permissions.map(p => <Tag key={p}>{p}</Tag>)}{!r.appId && !r.orgId && !r.permissions.length && !r.value && <Text type="secondary">{r.description || '—'}</Text>}</Space> }, { title: '状态', dataIndex: 'status', width: 100, render: v => <StatusTag value={v}/> }, { title: '操作', fixed: 'right', width: 140, render: (_, r) => <Space><Button type="link" disabled={!editable} onClick={() => { setEditing(r); setOpen(true); }}>编辑</Button><ConfirmDelete target={r.name} disabled={!editable || r.id.endsWith('-admin')} onConfirm={async () => {
                        if (cfg.category === 'app-group' && db.apps.some(a => a.domain === domain && a.group === r.name))
                            throw new Error('该分组仍有关联应用，请先调整应用分组');
                        await api.remove('catalog', r.id);
                    }}/></Space> }]}/><RecordEditor open={open} title={`${editing ? '编辑' : '新建'}${cfg.title}`} width={736} initial={{ ...base, validationText: JSON.stringify(base.validation || {}, null, 2) } as unknown as FormValues} fields={fields} onClose={() => setOpen(false)} onSave={async (v) => {
            const value = { ...base, ...v } as Catalog;
            if (cfg.category === 'extension') { try { value.validation = v.validationText ? JSON.parse(String(v.validationText)) : {}; } catch { throw new Error('校验规则须为有效JSON'); } }
            await api.save('catalog', value, '维护' + cfg.title);

        }}/><Drawer title="配置详情" open={!!detail} onClose={() => setDetail(null)} size={560}>{detail && <Descriptions size="small" column={1} layout="vertical" items={[{ key: 'name', label: '名称', children: detail.name }, { key: 'code', label: '唯一编码', children: <Text code copyable>{detail.code}</Text> }, { key: 'status', label: '状态', children: <StatusTag value={detail.status}/> }, { key: 'desc', label: '说明', children: detail.description || '—' }, { key: 'perms', label: '权限范围', children: detail.permissions.join('、') || '—' }]}/>}</Drawer></>;
}
