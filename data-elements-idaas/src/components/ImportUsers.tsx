import { useState } from 'react';
import { Alert, App, Button, Modal, Space, Table, Tag, Upload } from 'antd';
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import { useDatabase, api } from '../mock/store';
import { parseCsv } from '../domain/csv';
import { makeBase, validateEntity } from '../domain/engine';
import type { Domain, User } from '../domain/types';
import { exportCsv, Text } from './common';
interface Preview {
    id: string;
    line: number;
    name: string;
    account: string;
    email: string;
    phone: string;
    orgId: string;
    post: string;
    error: string;
    entity: User;
}
export default function ImportUsers({ open, domain, onClose }: {
    open: boolean;
    domain: Domain;
    onClose: () => void;
}) {
    const db = useDatabase();
    const [rows, setRows] = useState<Preview[]>([]);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [result, setResult] = useState('');
    const { message } = App.useApp();
    const parse = async (file: File) => {
        setError('');
        setResult('');
        setRows([]);
        try {
            if (file.size > 2 * 1024 * 1024)
                throw new Error('文件不能超过2MB');
            const data = parseCsv(await file.text());
            if (!data.length)
                throw new Error('文件为空');
            const expected = ['姓名', '账号', '邮箱', '手机号', '机构编码', '岗位'];
            if (data[0].join(',') !== expected.join(','))
                throw new Error('表头不匹配，请使用模板：' + expected.join('、'));
            if (data.length > 501)
                throw new Error('本次最多导入500行');
            const next = structuredClone(db);
            const preview = data.slice(1).map((cells, index) => {
                const [name = '', account = '', email = '', phone = '', orgCode = '', post = ''] = cells;
                const org = next.orgs.find(o => o.code === orgCode.trim());
                const entity: User = { ...makeBase(name.trim(), domain, 'import-user'), account: account.trim(), email: email.trim(), phone: phone.trim(), orgId: org?.id || '', post, kind: domain === 'public' ? 'citizen' : 'person', locked: false, verified: false, appointments: domain === 'public' ? [] : [{ orgId: org?.id || '', post, primary: true }], history: [] };
                let err = '';
                try {
                    if (cells.length !== 6)
                        throw new Error('列数与模板不一致');
                    validateEntity(next, 'users', entity);
                    next.users.push(entity);
                }
                catch (e) {
                    err = e instanceof Error ? e.message : '该行数据无效';
                }
                return { id: entity.id, line: index + 2, name, account, email, phone, orgId: org?.name || orgCode, post, error: err, entity };
            });
            setRows(preview);
        }
        catch (e) {
            setError(e instanceof Error ? e.message : '无法解析CSV');
        }
        return false;
    };
    const commit = async () => {
        setBusy(true);
        let count = 0;
        const errors: Preview[] = [];
        for (const r of rows) {
            if (r.error) {
                errors.push(r);
                continue;
            }
            try {
                await api.save('users', r.entity, 'CSV导入用户');
                count++;
            }
            catch (e) {
                errors.push({ ...r, error: e instanceof Error ? e.message : '导入失败' });
            }
        }
        setRows(errors);
        setResult(`已导入 ${count} 项；未导入 ${errors.length} 项。`);
        setBusy(false);
        if (!errors.length)
            message.success(`已导入${count}项`);
    };
    return <Modal open={open} title="导入用户" onCancel={busy ? undefined : onClose} width={900} destroyOnHidden footer={<Space><Button disabled={busy} onClick={onClose}>关闭</Button><Button type="primary" loading={busy} disabled={!rows.some(r => !r.error)} onClick={commit}>导入有效数据（{rows.filter(r => !r.error).length}）</Button></Space>}><Alert type="info" showIcon title="支持 UTF-8 CSV，最多500行。请按模板填写；无效行不会导入。" style={{ marginBottom: 16 }}/><Button icon={<DownloadOutlined />} style={{ marginBottom: 16 }} onClick={() => exportCsv('用户导入模板', ['姓名', '账号', '邮箱', '手机号', '机构编码', '岗位'], [])}>下载模板</Button><Upload.Dragger accept=".csv" beforeUpload={file => { void parse(file); return false; }} maxCount={1} showUploadList={false} disabled={busy}><p><InboxOutlined style={{ fontSize: 28 }}/></p><p>点击选择 CSV 文件，或拖拽文件到此处</p><Text type="secondary">选择文件后将先校验字段格式与必填信息。</Text></Upload.Dragger>{error && <Alert style={{ marginTop: 16 }} type="error" title={error} showIcon/>}{result && <Alert style={{ marginTop: 16 }} type="info" title={result} showIcon/>}{rows.length > 0 && <Table style={{ marginTop: 16 }} rowKey="id" size="small" dataSource={rows} pagination={{ pageSize: 10 }} scroll={{ x: 760 }} columns={[{ title: '行', dataIndex: 'line', width: 50 }, { title: '姓名', dataIndex: 'name' }, { title: '账号', dataIndex: 'account' }, { title: '机构', dataIndex: 'orgId' }, { title: '校验结果', dataIndex: 'error', render: v => v ? <Tag color="error">{v}</Tag> : <Tag color="success">可导入</Tag> }]}/>}</Modal>;
}
