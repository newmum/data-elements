import { useState } from 'react';
import { App, Button, Collapse, Descriptions, Form, Input, Modal, Tag, Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';
import type { Application } from '../domain/types';
import { foundationApi, refreshWorkspace, useDatabase, useSession } from '../mock/store';
import { dateText, formErrorText, useAction } from './common';

export function ApplicationRuntimeConnection({ app }: { app: Application }) {
    const db = useDatabase();
    const session = useSession()!;
    const [open, setOpen] = useState(false);
    const [form] = Form.useForm();
    const { message } = App.useApp();
    const { run, busy } = useAction();
    const canBind = session.appBindIds?.includes(app.id) === true;

    return <>
        <Collapse size="small" className="application-runtime-connection" style={{ marginTop: 16 }} items={[{
            key: 'connection', label: '应用连接信息', children: <>
                <Descriptions size="small" column={{ xs: 1, sm: 2 }} layout="vertical" items={[
                    { key: 'tenant', label: <>租户库标识 <Tooltip title="用于确定读取哪一个业务系统的租户数据库。"><QuestionCircleOutlined /></Tooltip></>, children: app.runtimeTenantId || '未绑定' },
                    { key: 'local', label: <>租户内权限应用标识 <Tooltip title="用于在该租户库中查找属于本应用的角色和菜单；它与租户库标识不是同一编号。"><QuestionCircleOutlined /></Tooltip></>, children: app.localPermissionAppId || '未配置' },
                    { key: 'verified', label: '关联状态', children: app.bindingStatus === 'VERIFIED' ? <Tag color="green">已检查</Tag> : <Tag>待检查</Tag> },
                    { key: 'time', label: '最近检查时间', children: app.bindingVerifiedAt ? dateText(app.bindingVerifiedAt) : '—' },
                    { key: 'auth', label: '登录方式', children: app.authMode === 'FEDERATED' ? '统一认证' : '应用本地账号' },
                    { key: 'sync', label: '中央下发', children: db.syncConfigs.some(c => c.appId === app.id && c.status === 'enabled') ? '已启用接收配置' : '未启用接收配置' },
                ]}/>
                <Button disabled={!canBind || busy} onClick={() => { form.setFieldsValue({ runtimeTenantId: app.runtimeTenantId, localPermissionAppId: app.localPermissionAppId }); setOpen(true); }}>{app.bindingStatus === 'VERIFIED' ? '重新检查关联' : '检查并保存关联'}</Button>
            </>,
        }]}/>
        <Modal title={app.bindingStatus === 'VERIFIED' ? '重新检查应用关联' : '检查并保存应用关联'} open={open} onCancel={() => setOpen(false)} okText="检查并保存" cancelText="取消" confirmLoading={busy} onOk={async () => {
            try {
                const values = await form.validateFields();
                await run(async () => { await foundationApi.write('/idaas/applications/runtime-binding', { id: app.id, version: app.version, ...values }); await refreshWorkspace(); setOpen(false); }, '应用关联已检查');
            } catch (error) { message.error(formErrorText(error)); }
        }}>
            <Form size="small" form={form} layout="vertical" onFinishFailed={failure => message.error(formErrorText(failure))}>
                <Form.Item name="runtimeTenantId" label="租户库标识" rules={[{ required: true }]} extra="确定读取哪一个租户数据库。已有绑定不能在这里切换租户库。"><Input disabled={!!app.runtimeTenantId}/></Form.Item>
                <Form.Item name="localPermissionAppId" label="租户内权限应用标识" rules={[{ required: true }, { max: 64 }]} extra="用于查找该租户库中属于本应用的角色和菜单。"><Input/></Form.Item>
            </Form>
        </Modal>
    </>;
}
