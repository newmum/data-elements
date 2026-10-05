import { useEffect, useState } from 'react';
import { App, Alert, Button, Card, Col, Descriptions, Form, Input, InputNumber, Modal, Row, Space, Switch, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { CheckCircleOutlined, LockOutlined, SafetyCertificateOutlined, SaveOutlined, UserOutlined, ApiOutlined, GlobalOutlined, DatabaseOutlined, CheckCircleFilled, ApartmentOutlined } from '@ant-design/icons';
import { useDatabase, useSession, api, setSession } from '../mock/store';
import type { Domain, Settings as SettingsType } from '../domain/types';
import { PageTitle, Text, Title } from '../components/common';
import { UserAvatar, SectionIntro } from '../components/visuals';
import { BrandMark } from '../components/Brand';
import { MfaBinding } from './Identity';
const titles: Record<string, [
    string,
    string
]> = { security: ['账号安全', '统一配置密码策略、异常登录限制与多因子要求。'], sso: ['单点登录设置', '配置会话与令牌生命周期，让跨应用访问边界保持一致。'], encryption: ['字段加密', '定义敏感字段的保护方式与默认展示策略。'], api: ['API 访问控制', '设置接口访问来源与请求限制。'], branding: ['系统品牌', '统一平台名称与登录页面的品牌信息。'] };
export default function Settings({ domain, kind }: {
    domain: Domain;
    kind: string;
}) {
    const db = useDatabase();
    const session = useSession()!;
    const [form] = Form.useForm<SettingsType>();
    const [busy, setBusy] = useState(false);
    const [dirty, setDirty] = useState(false);
    const [error, setError] = useState('');
    const { message, modal } = App.useApp();
    const editable = session.permissions?.includes('settings:write') === true && domain === 'workforce' && ['security', 'branding', 'api', 'sso'].includes(kind);
    const current = db.settings[domain];
    useEffect(() => { form.resetFields(); form.setFieldsValue(current); setDirty(false); setError(''); }, [domain, kind]);
    useEffect(() => {
        const warn = (e: BeforeUnloadEvent) => {
            if (dirty) {
                e.preventDefault();
                e.returnValue = '';
            }
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [dirty]);
    const save = async () => {
        try {
            const values = await form.validateFields();
            setBusy(true);
            setError('');
            await api.settings(domain, values, kind);
            setDirty(false);
            message.success(kind === 'branding' ? '系统品牌已更新' : '配置已保存');
        }
        catch (e) {
            if (e instanceof Error)
                setError(e.message);
        }
        finally {
            setBusy(false);
        }
    };
    const number = (name: keyof SettingsType, label: string, min: number, max: number, help?: string) => <Form.Item name={name} label={label} rules={[{ required: true, message: '请填写' + label }]} extra={help}><InputNumber disabled={name === 'passwordDays'} min={min} max={max} style={{ width: 220, maxWidth: '100%' }}/></Form.Item>;
    const toggle = (name: keyof SettingsType, label: string, description: string) => <div className="setting-row"><div><Text strong className="setting-title">{label}</Text><div><Text type="secondary" className="setting-description">{description}</Text></div></div><Form.Item name={name} valuePropName="checked" noStyle><Switch  aria-label={label}/></Form.Item></div>;
    const content = kind === 'security' ? <><Card size="small" title="密码策略" className="section-gap"><Row gutter={12}><Col xs={24} sm={12}>{number('minLength', '最小密码长度', 8, 64, '建议12位及以上。')}</Col><Col xs={24} sm={12}>{number('passwordDays', '强制定期改密周期（天）', 0, 365, '0表示不按周期强制改密；泄露或风险事件应另行强制重置。')}</Col></Row>{toggle('forceChange', '首次登录要求修改初始密码', '首次登录后需先更新密码，再继续访问。')}<Form.Item label="禁止使用的弱密码" name="passwordBlacklist" extra="一行一个条目，集中维护禁止使用的弱密码。"><Input.TextArea rows={3}/></Form.Item></Card><Card size="small" title="登录保护"><Row gutter={12}><Col xs={24} sm={12}>{number('lockThreshold', '连续失败锁定阈值（次）', 3, 20)}</Col><Col xs={24} sm={12}>{number('lockMinutes', '锁定时间（分钟）', 5, 1440)}</Col></Row>{toggle('mfaRequired', '管理员要求多因子认证', '登录时要求完成第二项身份验证。')}</Card></> :
        kind === 'sso' ? <Card size="small" title="会话与令牌"><Alert type="info" showIcon title="统一认证策略" description="应用客户端的有效期与平台上限取较小值；新签发的令牌按保存后的策略生效。认证中心登录会话最长30分钟，空闲超时可进一步收紧。" style={{ marginBottom: 16 }} /><Row gutter={12}><Col xs={24} sm={12}>{number('accessTtl', '访问令牌有效期上限（秒）', 60, 3600)}</Col><Col xs={24} sm={12}>{number('refreshTtl', '刷新令牌有效期上限（秒）', 300, 604800)}</Col><Col xs={24} sm={12}>{number('idleMinutes', '认证会话空闲超时（分钟）', 5, 30)}</Col></Row>{toggle('allowConcurrent', '允许同一账号多端会话', '关闭后，新登录将替换原认证中心会话并撤销已有应用认证会话。')}<Alert type="info" showIcon title="退出认证中心将撤销该账号的应用令牌" description="业务应用仍需校验当前令牌与授权状态。接入协议、回调地址和客户端凭据在应用管理中配置。" /></Card> :
            kind === 'encryption' ? <Card size="small" title="敏感字段保护"><Alert type="info" showIcon title="敏感扩展字段默认加密且不回显原值" description="在扩展字段中标记敏感字段后，保存时使用对象绑定的AES-256-GCM保护，读取只显示受保护标记。身份核验资料、动态口令及接入密钥同样加密保存；密码保存为不可逆摘要。" /><div style={{ marginTop: 16, marginBottom: 16 }}><Link to={`/console/${domain}/org-settings/fields`}>维护扩展字段与敏感标记</Link></div><Table size="small" rowKey="id" scroll={{ x: 'max-content' }} dataSource={db.catalog.filter(c => c.category === 'extension' && c.domain === domain)} columns={[{ title: '字段名称', dataIndex: 'name' }, { title: '字段编码', dataIndex: 'code' }, { title: '适用对象', dataIndex: 'subjectType' }, { title: '保护方式', render: (_, c) => c.sensitive ? <Tag color="blue">加密保存 · 隐藏原值</Tag> : '普通资料字段' }, { title: '状态', dataIndex: 'status', render: v => v === 'enabled' ? '已启用' : '已停用' }]} /><Text type="secondary">密钥由部署配置管理。已有敏感字段调整为普通字段时，会按字段维护流程处理现有值，请先核对资料的展示范围。</Text></Card> :
                kind === 'api' ? <Card size="small" title="来源限制与配额"><Form.Item label="允许访问的IP / CIDR" name="apiWhitelist" extra="一行一项，留空表示尚未配置。"><Input.TextArea rows={3} placeholder="192.0.2.0/24"/></Form.Item><Form.Item label="拒绝访问的IP / CIDR" name="ipBlacklist" extra="发生冲突时，拒绝规则优先。"><Input.TextArea rows={3} placeholder="198.51.100.10"/></Form.Item>{number('apiRateLimit', '每分钟请求数上限', 1, 100000)}</Card> :
                    <Card size="small" title="系统标识"><Form.Item label="系统名称" name="title" rules={[{ required: true, message: '请输入系统名称' }, { max: 30, message: '最多30个字符' }]}><Input maxLength={30} showCount/></Form.Item><Form.Item label="登录页说明" name="subtitle" rules={[{ max: 100 }]}><Input maxLength={100} showCount/></Form.Item><Form.Item label="品牌Logo URL" name="logoUrl" extra="支持HTTPS图片URL。为空时使用平台默认标识；无网络时自动回退。" rules={[{ validator: (_, v) => !v || /^https:\/\/[^\s]+$/.test(v) ? Promise.resolve() : Promise.reject(new Error('请输入HTTPS图片URL')) }]}><Input placeholder="https://cdn.example.com/logo.svg"/></Form.Item><Alert type="info" title="品牌设置不会改变 Ant Design 主色、基础组件尺寸与语义状态。" showIcon/></Card>;
    const guidance: Record<string, {
        title: string;
        intro: string;
        items: string[];
    }> = {
        security: { title: '建立身份安全基线', intro: '将账号保护与应用访问分开管理，优先关注高权限账号。', items: ['密码规则统一维护，弱密码名单集中管理。', '账号锁定不等于停用，解除锁定不会恢复已撤销的授权。', '管理员二次认证策略与普通账号分别配置。'] },
        sso: { title: '一次认证，有序通行', intro: '统一认证中心负责会话与信任传递，业务应用仍按有效权限判断访问。', items: ['访问令牌与刷新令牌设置独立生命周期。', '退出认证中心后，已签发令牌需通过当前会话校验。', '各应用独立配置精确回调和认证客户端。'] },
        encryption: { title: '保护敏感身份信息', intro: '字段定义决定保护方式，资料读取遵守对应的展示范围。', items: ['敏感扩展字段只展示受保护标记。', '加密内容绑定身份域、对象和字段，不能跨对象替换。', '密钥由部署维护，字段变更保留操作审计。'] },
        api: { title: '限制接口访问边界', intro: '面向业务应用与机器身份，集中维护来源与调用限制。', items: ['记录允许与拒绝的来源范围。', '与应用 API 授权共同界定调用资格。', '调整限制前，请核对业务应用的调用需求。'] },
        branding: { title: '一致的品牌体验', intro: '名称、标识和描述在管理后台与统一登录入口中保持一致。', items: ['使用清晰的系统名称，建议控制在16字以内。', 'Logo建议提供带透明背景的HTTPS图片。', '留空或加载失败时回退到默认平台标识。'] }
    };
    const guide = guidance[kind];
    return <><PageTitle title={titles[kind][0]} description={titles[kind][1]}/><div className="settings-layout"><div className="settings-main">{error && <Alert title={error} type="error" showIcon style={{ marginBottom: 16 }}/>}<Form size="small" layout="vertical" form={form} disabled={!editable} onValuesChange={() => setDirty(true)} onFinish={save} preserve>{content}{kind !== 'encryption' && <div className="settings-footer"><Space><Button onClick={() => modal.confirm({ title: '放弃当前修改？', okText: '恢复已保存配置', cancelText: '继续编辑', onOk: () => { form.setFieldsValue(current); setDirty(false); } })}>重置修改</Button><Button type="primary" icon={<SaveOutlined />} loading={busy} disabled={!editable} htmlType="submit">保存配置</Button>{dirty && <Text type="secondary">有未保存的修改</Text>}</Space></div>}</Form></div><aside className="settings-guide"><div className="guide-icon">{kind === 'api' ? <ApiOutlined /> : kind === 'sso' ? <GlobalOutlined /> : kind === 'encryption' ? <DatabaseOutlined /> : kind === 'branding' ? <BrandMark size={40}/> : <SafetyCertificateOutlined />}</div><h3>{guide.title}</h3><p>{guide.intro}</p><div className="guide-divider"/>{guide.items.map((t, i) => <div className="guide-point" key={t}><span>{String(i + 1).padStart(2, '0')}</span><p>{t}</p></div>)}<div className="guide-footer"><CheckCircleFilled /> {kind === 'encryption' ? '按字段定义保护资料' : '平台共用策略'}</div></aside></div></>;
}
export function Profile({ domain }: {
    domain: Domain;
}) {
    const db = useDatabase();
    const session = useSession()!;
    const [form] = Form.useForm();
    const [passwordForm] = Form.useForm();
    const [open, setOpen] = useState(false);
    const { message } = App.useApp();
    const [busy, setBusy] = useState(false);
    const save = async (v: {
        name: string;
    }) => {
        setBusy(true);
        try {
            await api.profile(v.name);
            message.success('个人资料已更新');
        }
        catch (e) {
            message.error(e instanceof Error ? e.message : '保存失败');
        }
        finally {
            setBusy(false);
        }
    };
    return <><PageTitle title="个人中心" description="查看账户身份与个人安全选项。"/>{session.mustChangePassword && <Alert type="warning" showIcon title="请先修改初始密码，再继续管理平台。" style={{ marginBottom: 16 }}/>}<div className="profile-banner"><UserAvatar user={{ id: session.username, name: session.name }} size={56}/><div><h2>{session.name}</h2><p>{session.username} · {domain === 'public' ? '公众身份域' : '政企身份域'}</p><Tag color="blue">{{ admin: '系统管理员', auditor: '审计员', orgadmin: '机构管理员', appmanager: '应用管理员' }[session.role] || '平台成员'}</Tag></div><div className="profile-safety"><SafetyCertificateOutlined /><span>管理身份与业务使用身份分离</span></div></div><div className="settings-container"><Card size="small" title="基本资料" className="section-gap"><Descriptions size="small" column={{ xs: 1, sm: 2 }} items={[{ key: 'account', label: '登录账号', children: session.username }, { key: 'role', label: '管理角色', children: session.role }, { key: 'domain', label: '当前身份域', children: domain === 'public' ? '公众侧' : '政企侧' }, { key: 'store', label: '会话保存', children: '当前浏览器标签页' }]}/><Form size="small" form={form} layout="vertical" initialValues={{ name: session.name }} style={{ marginTop: 16, maxWidth: 480 }} onFinish={save}><Form.Item label="显示名称" name="name" rules={[{ required: true }, { max: 30 }]}><Input /></Form.Item><Button type="primary" htmlType="submit" loading={busy}>保存资料</Button></Form></Card><Card size="small" title="账户安全"><div className="setting-row"><div><Text strong className="setting-title">登录密码</Text><div><Text type="secondary" className="setting-description">使用符合安全策略的密码，避免在多个系统重复使用。</Text></div></div><Button onClick={() => { passwordForm.resetFields(); setOpen(true); }}>修改密码</Button></div></Card><MfaBinding/></div><Modal open={open} title="修改密码" onCancel={() => setOpen(false)} okText="修改密码" cancelText="取消" onOk={async () => {
            try {
                const values = await passwordForm.validateFields();
                await api.password(values.old, values.password);
                message.success('密码已修改，请重新登录');
                setOpen(false);
            }
            catch (error) { if (error instanceof Error) message.error(error.message); }
        }}><Form size="small" layout="vertical" form={passwordForm}><Form.Item label="当前密码" name="old" rules={[{ required: true }]}><Input.Password autoComplete="current-password"/></Form.Item><Form.Item label="新密码" name="password" rules={[{ required: true }, { min: db.settings[domain].minLength, message: `至少${db.settings[domain].minLength}位` }]}><Input.Password autoComplete="new-password"/></Form.Item><Form.Item label="确认新密码" name="confirm" dependencies={['password']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator: (_, v) => v === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error('两次密码不一致')) })]}><Input.Password autoComplete="new-password"/></Form.Item></Form></Modal></>;
}
