import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Card, Form, Input, Modal, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { identityRequest } from '../services/identity';
import { formErrorText } from './common';

interface Entity { id: string; name: string; registrationCode: string; verificationStatus: string; memberId: string; relationStatus: string; relationType: string; }
const verificationNames: Record<string, string> = { VERIFIED: '已核验', UNVERIFIED: '未核验', PENDING: '核验中', REJECTED: '未通过', EXPIRED: '核验已到期' };
const relationNames: Record<string, string> = { PENDING: '待授权', ACTIVE: '已授权', REVOKED: '已撤销', EXPIRED: '授权已到期' };
export function SelfLegalEntities({ currentEntityId }: { currentEntityId?: string }) {
    const [rows, setRows] = useState<Entity[]>([]); const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [registering, setRegistering] = useState(false); const [verifying, setVerifying] = useState<Entity | null>(null);
    const [form] = Form.useForm(); const requestId = useRef(crypto.randomUUID()); const { message } = App.useApp(); const navigate = useNavigate();
    const load = async () => { try { setRows(await identityRequest<Entity[]>('/idaas/self-entities/mine')); } catch (e) { setError(e instanceof Error ? e.message : '无法读取法人资料'); } };
    useEffect(() => { void load(); }, []);
    const execute = async (action: () => Promise<void>) => { setBusy(true); try { await action(); } catch (e) { message.error(formErrorText(e, '操作未完成')); } finally { setBusy(false); } };
    const select = (entityId: string | null) => execute(async () => { await identityRequest('/idaas/auth-account/select-entity', { entityId }); message.success('办理身份已更新，请重新登录'); navigate('/auth/identity/login'); });
    return <Card size="small" title="法人资料与办理身份" extra={<Button disabled={busy} onClick={() => { requestId.current = crypto.randomUUID(); form.resetFields(); setRegistering(true); }}>登记法人</Button>}>
        <Alert type="info" showIcon title="核验与经办授权分别确认" description="登记资料后可以申请法人核验。只有法人核验有效、经办关系已授权且在有效期内，才能选择该法人办理身份。" />
        {error && <Alert type="error" title={error} showIcon style={{ marginTop: 16 }} />}
        {currentEntityId && <Button style={{ marginTop: 16 }} disabled={busy} onClick={() => void select(null)}>恢复自然人办理身份</Button>}
        <Table size="small" rowKey="id" dataSource={rows} scroll={{ x: 'max-content' }} columns={[
            { title: '法人名称', dataIndex: 'name' }, { title: '信用代码', dataIndex: 'registrationCode' },
            { title: '核验', dataIndex: 'verificationStatus', render: s => <Tag color={s === 'VERIFIED' ? 'success' : 'default'}>{verificationNames[s] || s}</Tag> },
            { title: '经办关系', dataIndex: 'relationStatus', render: s => relationNames[s] || s },
            { title: '操作', render: (_, row) => <Space><Button disabled={busy || row.relationStatus === 'REVOKED'} onClick={() => { requestId.current = crypto.randomUUID(); setVerifying(row); }}>申请法人核验</Button><Button disabled={busy || row.relationStatus !== 'ACTIVE' || row.verificationStatus !== 'VERIFIED' || row.id === currentEntityId} onClick={() => void select(row.id)}>{row.id === currentEntityId ? '当前办理身份' : '选择办理身份'}</Button></Space> },
        ]} />
        <Modal open={registering} title="登记法人资料" okText="同意并登记" cancelText="取消" confirmLoading={busy} onCancel={() => setRegistering(false)} onOk={() => void execute(async () => { const values = await form.validateFields(); await identityRequest('/idaas/self-entities/register', { ...values, consent: true, requestId: requestId.current }); setRegistering(false); await load(); message.success('法人资料已登记，经办关系等待授权'); })}>
            <Form size="small" form={form} layout="vertical" onFinishFailed={failure => message.error(formErrorText(failure))}><Form.Item name="name" label="法人名称" rules={[{ required: true }, { max: 200 }]}><Input /></Form.Item><Form.Item name="registrationCode" label="统一社会信用代码" rules={[{ required: true }, { pattern: /^[0-9A-HJ-NPQRTUWXY]{18}$/, message: '填写十八位统一社会信用代码' }]}><Input maxLength={18} /></Form.Item></Form>
            <p>提交表示同意为建立法人身份档案处理本次资料。登记不会直接取得应用访问权限。</p>
        </Modal>
        <Modal open={!!verifying} title="申请法人核验" okText="同意并申请核验" cancelText="取消" confirmLoading={busy} onCancel={() => setVerifying(null)} onOk={() => void execute(async () => { if (!verifying) return; const result = await identityRequest<{ status: string }>('/idaas/verifications/submit', { targetType: 'LEGAL_ENTITY', targetId: verifying.id, document: verifying.registrationCode, documentType: 'SOCIAL_CREDIT_CODE', consent: true, requestId: requestId.current }); setVerifying(null); await load(); message.info(result.status === 'VERIFIED' ? '法人核验通过' : '法人核验申请已提交'); })}>
            <p>{verifying?.name}</p><p>信用代码：{verifying?.registrationCode}</p><p>提交表示同意由已配置的核验服务处理法人资料，核验结果不会自动启用经办授权。</p>
        </Modal>
    </Card>;
}
