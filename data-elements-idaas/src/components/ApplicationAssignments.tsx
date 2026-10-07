import { useEffect, useState } from 'react';
import { Alert, App, Button, Form, Input, Modal, Select, Space, Switch, Table, Tag } from 'antd';
import { foundationApi, useDatabase, useSession } from '../mock/store';
import { formErrorText } from './common';
import type { Domain } from '../domain/types';
interface Assignment { id: string; name: string; subject_id?: string; org_id?: string; account_alias?: string; directory_assigned?: number; assignment_mode?: string; status?: string; desired_version?: number; ack_version?: number; sync_status?: string; include_children?: number; version: number; }
const syncStatusLabel: Record<string, string> = {
    NOT_CONNECTED: '尚未启用下发', PENDING: '等待接收', IN_SYNC: '已同步', FAILED: '下发失败', CONFLICT: '需处理冲突',
};
export default function ApplicationAssignments({ appId, domain, kind }: { appId: string; domain: Domain; kind: 'subject' | 'org' }) {
    const db = useDatabase(); const session = useSession()!; const { message, modal } = App.useApp();
    const [data, setData] = useState<{ subjects: Assignment[]; orgs: Assignment[] }>({ subjects: [], orgs: [] });
    const [busy, setBusy] = useState(false); const [error, setError] = useState('');
    const [open, setOpen] = useState(false); const [editing, setEditing] = useState<Assignment | null>(null); const [form] = Form.useForm();
    const editable = session.appAssignableIds?.includes(appId) === true;
    const load = async () => { setBusy(true); try { setData(await foundationApi.read(`/idaas/assignments/list?appId=${encodeURIComponent(appId)}&domain=${domain}`)); setError(''); } catch (e) { setError(e instanceof Error ? e.message : '分配读取失败'); } finally { setBusy(false); } };
    useEffect(() => { void load(); }, [appId, domain]);
    const edit = (row: Assignment | null) => { setEditing(row); form.resetFields(); form.setFieldsValue(row ? { subjectId: row.subject_id, accountAlias: row.account_alias, orgId: row.org_id, includeChildren: row.include_children === 1 } : {}); setOpen(true); };
    const save = async () => { try { const values = await form.validateFields(); setBusy(true); await foundationApi.write('/idaas/assignments/save', { record: { ...values, id: editing?.id, version: editing?.version, appId, domain, type: kind }, creating: !editing }); message.success('资料分配已保存'); setOpen(false); await load(); } catch (e) { message.error(formErrorText(e, '保存失败')); } finally { setBusy(false); } };
    const remove = (row: Assignment) => modal.confirm({ title: `移除 ${row.name} 的资料分配？`, content: '仅移除平台对该应用的资料分配，不会删除人员或撤销独立的访问授权。', okText: '移除分配', cancelText: '取消', onOk: async () => { await foundationApi.write('/idaas/assignments/remove', { record: { id: row.id, version: row.version, type: kind, appId, domain, subjectId: row.subject_id, orgId: row.org_id } }); await load(); } });
    const actions = (row: Assignment) => <Space className="application-directory-actions" size={12}><Button type="link" disabled={!editable} onClick={() => edit(row)}>编辑</Button><Button type="link" danger disabled={!editable || (kind === 'subject' ? !row.directory_assigned : row.status === 'REVOKED')} onClick={() => remove(row)}>移除分配</Button></Space>;
    return <>
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 12 }}/>}
        <div className="application-directory-toolbar"><Space wrap size={10}><Button loading={busy} onClick={() => void load()}>刷新分配</Button><Button type="primary" disabled={!editable || busy} onClick={() => edit(null)}>{kind === 'subject' ? '分配人员' : '分配机构'}</Button></Space></div>
        {kind === 'subject' ? <Table<Assignment> size="small" rowKey="id" dataSource={data.subjects} loading={busy} pagination={{ pageSize: 10 }} scroll={{ x: 820 }} columns={[
            { title: '姓名', dataIndex: 'name' }, { title: '账号', dataIndex: 'account_alias', render: value => value || '未设置' }, { title: '资料分配', render: (_, row) => row.directory_assigned ? '已分配' : '已移除' }, { title: '平台授权', render: (_, row) => row.assignment_mode === 'GRANT_DERIVED' ? '已授权' : '未授权' }, { title: '下发状态', dataIndex: 'sync_status', render: (value: string) => <Tag>{syncStatusLabel[value] || '未知状态'}</Tag> }, { title: '操作', render: (_, row) => actions(row) },
        ]}/> : <Table<Assignment> size="small" rowKey="id" dataSource={data.orgs} loading={busy} pagination={{ pageSize: 10 }} scroll={{ x: 700 }} columns={[
            { title: '机构', dataIndex: 'name' }, { title: '包含下级', render: (_, row) => row.include_children ? '是' : '否' }, { title: '分配状态', dataIndex: 'status', render: value => value === 'ACTIVE' ? '已分配' : '已移除' }, { title: '操作', render: (_, row) => actions(row) },
        ]}/>}
        <Modal title={kind === 'subject' ? '人员资料分配' : '机构资料范围'} open={open} onCancel={() => setOpen(false)} onOk={save} okText="保存" cancelText="取消" confirmLoading={busy}>
            <Form size="small" form={form} layout="vertical" onFinishFailed={failure => message.error(formErrorText(failure))}>
                {kind === 'subject' ? <><Form.Item name="subjectId" label="平台人员" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" disabled={!!editing} options={db.users.filter(row => row.domain === domain && row.status === 'enabled').map(row => ({ value: row.id, label: row.name }))}/></Form.Item><Form.Item name="accountAlias" label="账号"><Input/></Form.Item></> : <><Form.Item name="orgId" label="平台机构" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" disabled={!!editing} options={db.orgs.filter(row => row.domain === domain && row.status === 'enabled').map(row => ({ value: row.id, label: row.name }))}/></Form.Item><Form.Item name="includeChildren" label="包含下级机构" valuePropName="checked"><Switch/></Form.Item></>}
            </Form>
        </Modal>
    </>;
}
