import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Checkbox, Drawer, Select, Table } from 'antd';
import type { Dictionary, DictionaryLink, Field, Resource } from '../core/model';
import { clone, display, isBlank } from '../core/model';
import { matchDictionary } from '../core/engine';
import Icon from './Icon';
export default function DictionaryDialog({ field, resource, dictionaries, onClose, onSave }: {
    field?: Field;
    resource: Resource;
    dictionaries: Dictionary[];
    onClose: () => void;
    onSave: (link?: DictionaryLink) => void;
}) {
    const [link, setLink] = useState<DictionaryLink | undefined>(undefined);
    useEffect(() => { setLink(field?.dictionary ? clone(field.dictionary) : undefined); }, [field?.id]);
    const options = dictionaries.map(d => ({ value: d.id, label: `${d.name} · v${d.version}${d.source === 'upload' ? '（本次上传）' : '（系统字典）'}` }));
    const values = useMemo(() => { const m = new Map<string, number>(); if (field)
        for (const row of resource.rows) {
            const v = row.values[field.id];
            if (!isBlank(v))
                m.set(String(v), (m.get(String(v)) || 0) + 1);
        } return [...m].map(([raw, count]) => { let code = '', label = '', error = ''; try {
        const result = matchDictionary(raw, { ...field!, dictionary: link }, dictionaries);
        if (result) {
            code = result.code;
            label = result.label;
        }
    }
    catch (e) {
        error = (e as Error).message;
    } return { key: raw, raw, count, code, label, error, output: link?.output === 'code' ? code : raw }; }); }, [resource, field, link, dictionaries]);
    function patch(p: Partial<DictionaryLink>) { if (link)
        setLink({ ...link, ...p, ...(p.output || p.id || p.match ? { confirmed: false } : {}) }); }
    const d = dictionaries.find(d => d.id === link?.id);
    return <Drawer title={<span className="dialog-title"><Icon name="book"/>字典关联 · {field?.zh || field?.en || '字段'}</span>} open={!!field} onClose={onClose} width={800} footer={<div className="dialog-footer"><Button disabled={!link || resource.locked} onClick={() => { onSave(undefined); onClose(); }}>解除关联</Button><div><Button onClick={onClose}>取消</Button> <Button type="primary" disabled={resource.locked || !link?.id || (link.output === 'code' && !link.confirmed)} onClick={() => { onSave(link); onClose(); }}>保存关联规则</Button></div></div>}>
  <Alert type="info" showIcon message="关联按需配置，不会自动改变原始值" description="启用后，非空值必须精确匹配字典。没有匹配或存在多个代码的记录将被阻止同步。"/>
  <div className="form-block"><label>关联字典<Select aria-label="关联字典" placeholder="请选择字典" value={link?.id} options={options} onChange={id => { const d = dictionaries.find(d => d.id === id)!; setLink({ id, version: d.version, match: 'code', output: 'preserve', confirmed: false, mappings: {} }); }}/></label></div>
  {link && <><div className="inline-fields"><label>源值如何匹配<Select aria-label="源值匹配方式" value={link.match} options={[{ value: 'code', label: '原值是代码 · 精确匹配' }, { value: 'label', label: '原值是名称 · 精确匹配' }, { value: 'manual', label: '自定义值 · 人工逐项映射' }]} onChange={v => patch({ match: v })}/></label><label>同步处理方式<Select aria-label="字典同步处理" value={link.output} options={[{ value: 'preserve', label: '保留原值，仅关联解释' }, { value: 'code', label: '转换为字典代码' }]} onChange={v => patch({ output: v })}/></label></div>
  <div className="section-heading"><h3>匹配预览</h3><span className="muted">{values.length} 个不同原值 · {values.filter(x => x.error).length} 项待处理</span></div>
  <Table size="small" rowKey="key" pagination={{ pageSize: 8, size: 'small' }} dataSource={values} scroll={{ x: 600 }} columns={[{ title: '原始值', dataIndex: 'raw', width: 95 }, { title: '条数', dataIndex: 'count', width: 60 }, { title: '对应代码', width: 150, render: (_, r) => link.match === 'manual' ? <Select aria-label={`映射${r.raw}`} style={{ width: '100%' }} allowClear value={link.mappings[r.raw]} options={d?.entries.map(x => ({ value: x.code, label: `${x.code} · ${x.label}` }))} onChange={v => patch({ mappings: { ...link.mappings, [r.raw]: v || '' } })}/> : r.code || '—' }, { title: '字典名称', dataIndex: 'label', width: 100 }, { title: '拟同步值', dataIndex: 'output', width: 100, render: v => display(v) }, { title: '匹配结果', render: (_, r) => r.error ? <span className="error-text">{r.error}</span> : <span className="success-text">匹配成功</span> }]}/>
  {link.output === 'code' && <div className="confirm-box"><Checkbox checked={link.confirmed} onChange={e => patch({ confirmed: e.target.checked })}>已核对转换前后差异，同意将匹配成功的原值转换为字典代码</Checkbox></div>}
  <p className="inline-note">代码按原始文本匹配：01 不等于 1。不会自动补零、去空格、忽略大小写或映射为“其他”。</p></>}
 </Drawer>;
}
