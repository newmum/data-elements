import { useState } from 'react';
import { Alert, App, Button, Modal, Space, Table, Tag, Upload } from 'antd';
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import { api } from '../mock/store';
import { parseCsv } from '../domain/csv';
import type { Domain } from '../domain/types';
import { exportCsv, Text } from './common';
interface Preview {
    id: string;
    line: number;
    name: string;
    account: string;
    email: string;
    phone: string;
    orgCode: string;
    post: string;
    error: string;
}
export default function ImportUsers({ open, domain, onClose }: {
    open: boolean;
    domain: Domain;
    onClose: () => void;
}) {
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
            const accounts = new Set<string>();
            const preview = data.slice(1).map((cells, index) => {
                const [name = '', account = '', email = '', phone = '', orgCode = '', post = ''] = cells;
                const value = { name: name.trim(), account: account.trim(), email: email.trim(), phone: phone.trim(), orgCode: orgCode.trim(), post: post.trim() };
                let err = '';
                if (cells.length !== 6) err = '列数与模板不一致';
                else if (!value.name || value.name.length > 100) err = '姓名不能为空且不能超过100字';
                else if (!/^[A-Za-z][A-Za-z0-9_.-]{2,99}$/.test(value.account)) err = '拟用账号格式不正确';
                else if (accounts.has(value.account)) err = '文件内账号重复';
                else if (domain === 'workforce' && !value.orgCode) err = '请填写机构编码';
                else if (value.email.length > 512 || value.phone.length > 256) err = '联系方式过长';
                accounts.add(value.account);
                return { id: String(index), line: index + 2, ...value, error: err };
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
        const valid = rows.filter(row => !row.error);
        try {
            const count = await api.importUsers(valid.map(({ name, account, email, phone, orgCode, post }) => ({ name, account, email, phone, orgCode, post })), domain);
            setRows(rows.filter(row => row.error));
            setResult(`已导入 ${count} 项；未导入 ${rows.length - count} 项。`);
            message.success(`已导入${count}项`);
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : '导入失败，请检查机构和岗位编码');
        } finally {
            setBusy(false);
        }
    };
    return <Modal open={open} title="导入用户" onCancel={busy ? undefined : onClose} width={900} destroyOnHidden footer={<Space><Button disabled={busy} onClick={onClose}>关闭</Button><Button type="primary" loading={busy} disabled={!rows.some(r => !r.error)} onClick={commit}>导入有效数据（{rows.filter(r => !r.error).length}）</Button></Space>}><Alert type="info" showIcon title="支持 UTF-8 CSV，最多500行。预览检查格式，提交时由服务端统一核验机构、岗位和管理范围；导入的是中央档案，不会创建登录密码。" style={{ marginBottom: 16 }}/><Button icon={<DownloadOutlined />} style={{ marginBottom: 16 }} onClick={() => exportCsv('用户导入模板', ['姓名', '账号', '邮箱', '手机号', '机构编码', '岗位'], [])}>下载模板</Button><Upload.Dragger accept=".csv" beforeUpload={file => { void parse(file); return false; }} maxCount={1} showUploadList={false} disabled={busy}><p><InboxOutlined style={{ fontSize: 28 }}/></p><p>点击选择 CSV 文件，或拖拽文件到此处</p><Text type="secondary">选择文件后将先校验字段格式与必填信息。</Text></Upload.Dragger>{error && <Alert style={{ marginTop: 16 }} type="error" title={error} showIcon/>}{result && <Alert style={{ marginTop: 16 }} type="info" title={result} showIcon/>}{rows.length > 0 && <Table style={{ marginTop: 16 }} rowKey="id" size="small" dataSource={rows} pagination={{ pageSize: 10 }} scroll={{ x: 760 }} columns={[{ title: '行', dataIndex: 'line', width: 50 }, { title: '姓名', dataIndex: 'name' }, { title: '账号', dataIndex: 'account' }, { title: '机构', dataIndex: 'orgCode' }, { title: '校验结果', dataIndex: 'error', render: v => v ? <Tag color="error">{v}</Tag> : <Tag color="success">待服务端校验</Tag> }]}/>}</Modal>;
}
