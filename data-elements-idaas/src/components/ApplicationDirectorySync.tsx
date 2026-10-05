import { useState } from 'react';
import type { Key } from 'react';
import { App, Alert, Button, Card, Space, Table, Tag } from 'antd';
import { ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { Application, Domain } from '../domain/types';
import { foundationApi, provisionApi, refreshWorkspace, type ReceiverConfig } from '../mock/store';

interface Difference { type: 'SUBJECT' | 'ORG'; localId: string; localName: string; localCode: string; centralId: string | null; centralName: string | null; state: 'same' | 'different' | 'local_only' }
interface CentralOnly { id: string; name: string; code: string }
interface Comparison {
    available: boolean; reason?: string; revision: string;
    local: { users: Difference[]; orgs: Difference[] };
    centralOnly: { users: CentralOnly[]; orgs: CentralOnly[] };
    counts: { localUsers: number; localOrgs: number; centralUsers: number; centralOrgs: number };
}
type PushRow = { key: string; type: 'user' | 'org'; id: string; name: string; reason: string };
const itemKey = (row: Difference) => `${row.type}:${row.localId}`;

export function ApplicationDirectorySync({ app, domain, canSync, canImport, canPushConfig }: {
    app: Application; domain: Domain; canSync: boolean; canImport: boolean; canPushConfig: boolean;
}) {
    const { modal, message } = App.useApp();
    const nav = useNavigate();
    const [comparison, setComparison] = useState<Comparison | null>(null);
    const [configs, setConfigs] = useState<ReceiverConfig[]>([]);
    const [selectedLocal, setSelectedLocal] = useState<Key[]>([]);
    const [selectedCentral, setSelectedCentral] = useState<Key[]>([]);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const inspect = async () => {
        setBusy(true); setError('');
        try {
            const [result, receivers] = await Promise.all([
                foundationApi.read<Comparison>(`/idaas/applications/directory-compare?appId=${encodeURIComponent(app.id)}`),
                canSync ? provisionApi.configs() : Promise.resolve([]),
            ]);
            setComparison(result); setConfigs(receivers.filter(c => c.appId === app.id));
            setSelectedLocal([]); setSelectedCentral([]);
        } catch (cause) { setError(cause instanceof Error ? cause.message : '读取差异失败'); }
        finally { setBusy(false); }
    };
    const pullRows = comparison ? [...comparison.local.orgs, ...comparison.local.users].filter(row => row.state !== 'same') : [];
    const pushRows: PushRow[] = comparison ? [
        ...comparison.centralOnly.orgs.map(row => ({ key: `org:${row.id}`, type: 'org' as const, id: row.id, name: row.name, reason: '仅平台有' })),
        ...comparison.centralOnly.users.map(row => ({ key: `user:${row.id}`, type: 'user' as const, id: row.id, name: row.name, reason: '仅平台有' })),
        ...comparison.local.orgs.filter(row => row.state === 'different' && row.centralId).map(row => ({ key: `org:${row.centralId}`, type: 'org' as const, id: row.centralId!, name: row.centralName!, reason: '资料不一致' })),
        ...comparison.local.users.filter(row => row.state === 'different' && row.centralId).map(row => ({ key: `user:${row.centralId}`, type: 'user' as const, id: row.centralId!, name: row.centralName!, reason: '资料不一致' })),
    ] : [];
    const receiver = configs.find(c => c.enabled && c.lastTestResult === 'SUCCESS');
    const pull = () => {
        if (!comparison || !selectedLocal.length) return;
        const rows = pullRows.filter(row => selectedLocal.includes(itemKey(row)));
        modal.confirm({ title: `将 ${rows.length} 条本地资料导入平台？`, okText: '确认导入', cancelText: '取消',
            content: '只导入所选人员、机构的资料。所需上级机构会一并建立；不会复制本地密码、登录账号、角色或授权。已关联的中央资料会按租户名称和状态更新。',
            onOk: async () => {
                setBusy(true);
                try {
                    const result = await foundationApi.write<{ createdUsers: number; updatedUsers: number; createdOrgs: number; updatedOrgs: number }>('/idaas/applications/directory-pull', {
                        appId: app.id, revision: comparison.revision, items: rows.map(row => ({ type: row.type, localId: row.localId })),
                    });
                    message.success(`已导入：人员新增 ${result.createdUsers}、更新 ${result.updatedUsers}；机构新增 ${result.createdOrgs}、更新 ${result.updatedOrgs}`);
                    await refreshWorkspace(); await inspect();
                } catch (cause) { message.error(cause instanceof Error ? cause.message : '导入失败'); throw cause; }
                finally { setBusy(false); }
            },
        });
    };
    const push = () => {
        if (!receiver || !selectedCentral.length) return;
        const rows = pushRows.filter(row => selectedCentral.includes(row.key));
        modal.confirm({ title: `向“${app.name}”下发 ${rows.length} 条平台资料？`, okText: '确认下发', cancelText: '取消',
            content: '只由本次点击创建下发任务。接收端会同时收到所选资料所需的机构，以及应用角色和资源定义；人员不包含平台登录凭据，租户已有本地密码及独立授权仍由租户管理。',
            onOk: async () => {
                setBusy(true);
                try {
                    let count = 0;
                    for (const type of ['org', 'user'] as const) {
                        const ids = rows.filter(row => row.type === type).map(row => row.id);
                        if (!ids.length) continue;
                        const task = await provisionApi.create(receiver.id, crypto.randomUUID(), type, ids);
                        for (let round = 0; round < 100; round++) {
                            await provisionApi.execute(task.id);
                            const current = (await provisionApi.tasks()).find(entry => entry.id === task.id);
                            if (!current) throw new Error('下发任务未返回执行结果');
                            if (current.status === 'success') { count++; break; }
                            if (current.status !== 'pending') throw new Error('下发存在失败项，请在同步任务中查看回执');
                            if (round === 99) throw new Error('任务仍在处理中，请在同步任务中继续执行');
                        }
                    }
                    message.success(`已完成 ${count} 组手动下发任务`); await inspect();
                } catch (cause) { message.error(cause instanceof Error ? cause.message : '下发失败'); throw cause; }
                finally { setBusy(false); }
            },
        });
    };
    const pushPortal = () => modal.confirm({ title: `下发“${app.name}”的界面配置？`, okText: '确认下发', cancelText: '取消',
        content: '把已保存的系统名称、标识和界面项写入该应用的租户配置；不修改本地账号与权限。',
        onOk: async () => {
            setBusy(true);
            try {
                const result = await foundationApi.write<{ synced: boolean; message?: string }>('/idaas/applications/push-portal-config', { appId: app.id, appVersion: app.version });
                if (!result.synced) throw new Error(result.message || '接收端未确认配置');
                message.success('界面配置已下发');
            } catch (cause) { message.error(cause instanceof Error ? cause.message : '界面配置下发失败'); throw cause; }
            finally { setBusy(false); }
        },
    });
    return <>
        <Card size="small" title="应用界面配置" className="section-gap" extra={<Button disabled={!canPushConfig || !app.portalConfig || !Object.keys(app.portalConfig).length || busy} onClick={pushPortal}>下发界面配置</Button>}>
            已保存的应用界面配置可在这里手动下发到该应用对应的租户系统。
        </Card>
        <Card size="small" title="人员与机构资料对比" className="section-gap" extra={<Button icon={<ReloadOutlined />} loading={busy} onClick={() => void inspect()}>查看差异</Button>}>
            {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 12 }}/>} 
            {!comparison && !error && <Alert type="info" showIcon title="点击“查看差异”读取当前租户与平台资料" description="只在人工操作时读取；查看差异不会修改任何一侧的数据。"/>}
            {comparison && !comparison.available && <Alert type="warning" showIcon title={comparison.reason || '应用连接尚未核验'}/>}
            {comparison?.available && <>
                <Space wrap style={{ marginBottom: 12 }}><Tag>租户人员 {comparison.counts.localUsers}</Tag><Tag>平台人员 {comparison.counts.centralUsers}</Tag><Tag>租户机构 {comparison.counts.localOrgs}</Tag><Tag>平台机构 {comparison.counts.centralOrgs}</Tag></Space>
                <Alert type="info" showIcon title="两个登录账号体系保持隔离" description="对比仅依据本应用的显式资料映射；姓名相同不会自动合并。本地授权人数与平台授权人数也不会被当作资料差异。" style={{ marginBottom: 16 }}/>
                <Card size="small" title="租户 → 平台" extra={<Button type="primary" disabled={!canImport || !selectedLocal.length || busy} onClick={pull}>导入选中差异</Button>} style={{ marginBottom: 16 }}>
                    <Table<Difference> size="small" rowKey={itemKey} dataSource={pullRows} pagination={{ pageSize: 10 }} scroll={{ x: 650 }} rowSelection={{ selectedRowKeys: selectedLocal, onChange: setSelectedLocal }} locale={{ emptyText: '没有需要导入的人员或机构差异' }} columns={[
                        { title: '类型', dataIndex: 'type', render: value => value === 'ORG' ? '机构' : '人员' }, { title: '租户资料', dataIndex: 'localName' },
                        { title: '租户编码 / 账号', dataIndex: 'localCode' }, { title: '平台对应资料', dataIndex: 'centralName', render: value => value || '尚无对应资料' },
                        { title: '差异', dataIndex: 'state', render: value => value === 'local_only' ? <Tag color="blue">仅租户有</Tag> : <Tag color="orange">资料不一致</Tag> },
                    ]}/>
                </Card>
                <Card size="small" title="平台 → 租户" extra={<Space><Button disabled={!receiver || !canSync || !selectedCentral.length || busy} onClick={push} type="primary">下发选中差异</Button><Button onClick={() => nav(`/console/${domain}/sync/configs`)}>管理接收配置</Button></Space>}>
                    {!receiver && <Alert type="warning" showIcon title="尚无已测试并启用的应用接收配置" description="请在同步配置中填写该租户系统的接收地址，测试连接并启用后，再进行手动下发。" style={{ marginBottom: 12 }}/>} 
                    <Table<PushRow> size="small" rowKey="key" dataSource={pushRows} pagination={{ pageSize: 10 }} scroll={{ x: 500 }} rowSelection={{ selectedRowKeys: selectedCentral, onChange: setSelectedCentral }} locale={{ emptyText: '没有需要下发的人员或机构差异' }} columns={[
                        { title: '类型', dataIndex: 'type', render: value => value === 'org' ? '机构' : '人员' }, { title: '平台资料', dataIndex: 'name' }, { title: '差异', dataIndex: 'reason' },
                    ]}/>
                </Card>
            </>}
        </Card>
    </>;
}
