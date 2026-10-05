import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Table, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { Domain } from '../domain/types';
import { foundationApi } from '../mock/store';
import { StatusTag } from './common';

interface LocalUser {
    id: string;
    name: string;
    account: string;
    status: string;
    orgNames: string[];
    roleNames: string[];
    authorized: boolean;
}
interface LocalOrg { id: string; name: string; code: string; parentName: string; status: string }
interface LocalRole { id: string; name: string; code: string; status: string; userCount: number; resourceCount: number }
interface LocalResource { id: string; name: string; code: string; path: string; status: string }
export interface LocalInventory {
    available: boolean;
    reason?: string;
    source?: 'TENANT_LOCAL';
    counts: { users: number; orgs: number; roles: number; resources: number; authorizedUsers: number };
    users?: LocalUser[];
    orgs?: LocalOrg[];
    roles?: LocalRole[];
    resources?: LocalResource[];
    authorizedUsers?: LocalUser[];
}
export function useLocalInventory(appId: string, domain: Domain, enabled: boolean) {
    const key = `${domain}:${appId}`;
    const currentKey = useRef(key);
    const requestNumber = useRef(0);
    currentKey.current = key;
    const [value, setValue] = useState<{ key: string; inventory: LocalInventory } | null>(null);
    const inventory = value?.key === key ? value.inventory : null;
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const load = useCallback(async () => {
        if (!enabled) return;
        const request = ++requestNumber.current;
        setLoading(true);
        try {
            const result = await foundationApi.read<LocalInventory>(`/idaas/applications/local-inventory?appId=${encodeURIComponent(appId)}&domain=${domain}`);
            if (currentKey.current === key && request === requestNumber.current) { setValue({ key, inventory: result }); setError(''); }
        } catch (cause) {
            if (currentKey.current === key && request === requestNumber.current) setError(cause instanceof Error ? cause.message : '本地资料读取失败');
        } finally { if (currentKey.current === key && request === requestNumber.current) setLoading(false); }
    }, [appId, domain, enabled, key]);
    useEffect(() => { setError(''); void load(); return () => { requestNumber.current++; }; }, [load]);
    return { inventory, loading, error, load };
}

type LocalKind = 'users' | 'orgs' | 'roles' | 'resources' | 'authorizedUsers';
const title: Record<LocalKind, string> = { users: '租户系统人员', orgs: '租户系统机构', roles: '租户系统角色', resources: '租户系统资源', authorizedUsers: '租户系统授权用户' };
export function ApplicationLocalInventory({ inventory, loading, error, kind, onLoad }: {
    inventory: LocalInventory | null;
    loading: boolean;
    error: string;
    kind: LocalKind;
    onLoad: () => void;
}) {
    const actions = <Button icon={<ReloadOutlined />} loading={loading} onClick={onLoad}>刷新列表</Button>;
    const users = kind === 'authorizedUsers' ? inventory?.authorizedUsers || [] : inventory?.users || [];
    return <Card size="small" className={kind === 'users' || kind === 'orgs' ? 'application-directory-card' : undefined} title={title[kind]} extra={actions} style={{ marginBottom: 16 }}>
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 12 }}/>} 
        {inventory?.available ? <>
            {(kind === 'users' || kind === 'authorizedUsers') && <Table<LocalUser> size="small" rowKey="id" dataSource={users} loading={loading} pagination={{ pageSize: 10 }} scroll={{ x: 700 }} columns={[
                { title: '姓名', dataIndex: 'name' }, { title: '账号', dataIndex: 'account' }, { title: '所属机构', dataIndex: 'orgNames', render: (values: string[]) => values?.join('、') || '—' }, { title: '角色', dataIndex: 'roleNames', render: (values: string[]) => values?.map(value => <Tag key={value}>{value}</Tag>) || '—' }, { title: '状态', dataIndex: 'status', render: value => <StatusTag value={value}/> },
            ]}/>}
            {kind === 'orgs' && <Table<LocalOrg> size="small" rowKey="id" dataSource={inventory.orgs || []} loading={loading} pagination={{ pageSize: 10 }} scroll={{ x: 640 }} columns={[
                { title: '机构名称', dataIndex: 'name' }, { title: '机构编码', dataIndex: 'code' }, { title: '上级机构', dataIndex: 'parentName' }, { title: '状态', dataIndex: 'status', render: value => <StatusTag value={value}/> },
            ]}/>}
            {kind === 'roles' && <Table<LocalRole> size="small" rowKey="id" dataSource={inventory.roles || []} loading={loading} pagination={{ pageSize: 10 }} scroll={{ x: 640 }} columns={[
                { title: '角色名称', dataIndex: 'name' }, { title: '本地角色编码', dataIndex: 'code' }, { title: '有效本地用户数', dataIndex: 'userCount', align: 'right' }, { title: '资源数', dataIndex: 'resourceCount', align: 'right' }, { title: '状态', dataIndex: 'status', render: value => <StatusTag value={value}/> },
            ]}/>}
            {kind === 'resources' && <Table<LocalResource> size="small" rowKey="id" dataSource={inventory.resources || []} loading={loading} pagination={{ pageSize: 10 }} scroll={{ x: 680 }} columns={[
                { title: '资源名称', dataIndex: 'name' }, { title: '本地资源编码', dataIndex: 'code' }, { title: '路径', dataIndex: 'path' }, { title: '状态', dataIndex: 'status', render: value => <StatusTag value={value}/> },
            ]}/>}
        </> : inventory && <Alert type="info" showIcon title={inventory.reason || '应用尚未核验本地连接'} />}
    </Card>;
}
