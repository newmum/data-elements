import { useEffect, useState } from 'react';
import { Alert, Button, Card, Table } from 'antd';
import { foundationApi } from '../services/workspace';
import type { Domain } from '../domain/types';

export interface RecoveryRequest {
    id: string;
    accountId: string;
    username: string;
    name: string;
    version: number;
    requestedAt: string;
}

export default function PasswordRecoveryRequests({ realm, domain, refreshKey = 0, onReset }: {
    realm: 'platform' | 'auth-account';
    domain: Domain;
    refreshKey?: number;
    onReset: (request: RecoveryRequest) => void;
}) {
    const [page, setPage] = useState(1);
    const [size, setSize] = useState(20);
    const [rows, setRows] = useState<RecoveryRequest[]>([]);
    const [total, setTotal] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        setLoading(true);
        const params = new URLSearchParams({ realm, domain, page: String(page), size: String(size) });
        void foundationApi.read<{ list: RecoveryRequest[]; total: number }>(`/idaas/password-recovery/requests?${params}`)
            .then(result => { if (active) { setRows(result.list); setTotal(result.total); setError(''); } })
            .catch(failure => { if (active) setError(failure instanceof Error ? failure.message : '找回申请读取失败'); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [realm, domain, page, size, refreshKey]);
    return <Card size="small" title={`待核验的密码找回申请（${total}）`} style={{ marginBottom: 16 }}>
        <Alert type="info" showIcon title="先通过既有工作联系渠道核验本人身份，再由有权限的管理员设置临时密码。系统不会向申请页面回显账号是否存在。" style={{ marginBottom: 12 }}/>
        {error && <Alert type="error" showIcon title={error}
            style={{ marginBottom: 12 }}/>} 
        <Table<RecoveryRequest> size="small" rowKey="id" loading={loading} dataSource={rows}
            pagination={{ current: page, pageSize: size, total, showSizeChanger: true, pageSizeOptions: [20, 50, 100], onChange: (next, pageSize) => { setPage(next); setSize(pageSize); } }}
            columns={[{ title: '申请时间', dataIndex: 'requestedAt' }, { title: '账号', dataIndex: 'username' },
                { title: '姓名', dataIndex: 'name' }, { title: '处理', render: (_, row) => <Button type="link" onClick={() => onReset(row)}>核验后重置</Button> }]}/>
    </Card>;
}
