import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Checkbox, Col, Descriptions, Drawer, Form, Input, InputNumber, Row, Select, Space, Spin, Table, Tabs, TreeSelect } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { catalogDefaults, catalogItemsFromColumns, catalogRegistrationApi, type CatalogChoices, type CatalogItem, type RegisteredCatalog } from '../../services/catalogRegistrationApi';
import { leafOptions, type SourceOption } from '../../services/datasourceRegistrationApi';
import type { CatalogTableRow, CatalogScope } from '../../services/metadataCatalog';
import type { LiveMetadataSource } from '../../services/metadata';

type EditorItem = CatalogItem & { clientKey: string };
type OptionNode = { value: string; title: string; children: OptionNode[] };
const treeOptions = (options: SourceOption[]): OptionNode[] => options.flatMap(option => option.value === 'ROOT' ? treeOptions(option.children || []) : [{ value: option.value, title: option.label, children: treeOptions(option.children || []) }]);
const itemKey = (item: CatalogItem, index: number) => item.tid || item.sourceTableColumnId || `item-${index}`;
const selectOptions = (options: SourceOption[] | undefined, value: unknown) => {
  const list = leafOptions(options || []);
  const selected = value == null ? '' : String(value);
  return selected && !list.some(option => option.value === selected) ? [...list, { value: selected, label: `${selected}（原值）` }] : list;
};

/** A table-context entry to the platform catalog workflow; all saves and data
 * items use its existing contracts. No physical table DDL or inferred ER writes. */
export function CatalogEditor({ table, directory, source, scope, onClose, onSaved, onUncertainSave }: {
  table?: CatalogTableRow; directory?: RegisteredCatalog; source?: LiveMetadataSource; scope?: CatalogScope;
  onClose: () => void; onSaved: (tid: string, name: string) => void; onUncertainSave?: () => void;
}) {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<Record<string, unknown>>();
  const [items, setItems] = useState<EditorItem[]>([]);
  const [choices, setChoices] = useState<CatalogChoices>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [loadError, setLoadError] = useState('');
  const [loadRevision, setLoadRevision] = useState(0);
  const [saveUncertain, setSaveUncertain] = useState(false);
  const [activeTab, setActiveTab] = useState('info');
  const sequence = useRef(0), dirty = useRef(false), savingRef = useRef(false);
  const catalogName = String(Form.useWatch('catalogName', form) || '');
  const open = Boolean(table);
  useEffect(() => {
    const seq = ++sequence.current;
    dirty.current = false; setError(''); setLoadError(''); setSaveUncertain(false); setChoices(undefined); setItems([]); setSaving(false); setActiveTab('info'); form.resetFields();
    if (!table) { setLoading(false); return; }
    setLoading(true);
    const load = async () => {
      const [dictionary, record] = await Promise.all([
        catalogRegistrationApi.choices(),
        directory ? catalogRegistrationApi.detail(directory.tid) : catalogRegistrationApi.columns(table.tid).then(columns => ({ detail: catalogDefaults(table, source), items: catalogItemsFromColumns(columns) })),
      ]);
      if (seq !== sequence.current) return;
      setChoices(dictionary);
      // Existing directory metadata is authoritative. Unexposed extension
      // properties remain unchanged because the save route performs a patch.
      const values = { ...record.detail, orgId: record.detail.orgId || source?.org_id || '' };
      form.setFieldsValue(values);
      setItems(record.items.map((item, index) => ({ ...item, clientKey: itemKey(item, index) })));
    };
    void load().catch(cause => { if (seq === sequence.current) setLoadError(cause instanceof Error ? cause.message : String(cause)); }).finally(() => { if (seq === sequence.current) setLoading(false); });
    return () => { sequence.current++; };
    // Scope refreshes must not replace a user's unsaved directory edits.
  }, [table?.tid, directory?.tid, form, loadRevision]);
  const close = () => {
    if (savingRef.current) return;
    if (saveUncertain) { onClose(); return; }
    if (dirty.current) modal.confirm({ title: '放弃未保存的目录修改？', content: '关闭后，本次修改不会保存；已有目录和源数据表不受影响。', okText: '放弃修改', cancelText: '继续编辑', onOk: onClose });
    else onClose();
  };
  const changeItem = (key: string, patch: Partial<CatalogItem>) => {
    if (saveUncertain) return;
    dirty.current = true; setError(''); setItems(previous => previous.map(item => item.clientKey === key ? { ...item, ...patch } : item));
  };
  const editingDisabled = loading || saving || saveUncertain;
  const save = async () => {
    if (!table || loading || savingRef.current || loadError || saveUncertain) return;
    let values: Record<string, unknown>;
    try { values = await form.validateFields(); }
    catch { setActiveTab('info'); return; }
    const invalid = items.findIndex(item => !item.colName.trim() || !item.colEn.trim() || !item.colType.trim());
    if (invalid !== -1) { setActiveTab('items'); message.warning(`请填写第 ${invalid + 1} 项的名称、英文代码和类型`); return; }
    const names = items.map(item => item.colEn.trim().toLocaleLowerCase());
    if (new Set(names).size !== names.length) { setActiveTab('items'); message.warning('数据项英文代码不能重复'); return; }
    const seq = sequence.current;
    savingRef.current = true; setSaving(true); setError('');
    try {
      const defaults = directory ? {} : catalogDefaults(table, source);
      const result = await catalogRegistrationApi.save({ ...defaults, ...values }, items, table, directory?.tid);
      if (!result.tid) throw new Error('保存接口未返回目录标识，请刷新列表核对后再操作');
      if (seq === sequence.current) { dirty.current = false; onSaved(result.tid, result.catalogName || String(values.catalogName || '')); }
    } catch (cause) {
      if (seq === sequence.current) {
        const errorText = cause instanceof Error ? cause.message : String(cause);
        const uncertain = Boolean((cause as { timedOut?: boolean })?.timedOut) || errorText.includes('无法连接数据中台') || errorText.includes('保存接口未返回目录标识');
        setSaveUncertain(uncertain);
        setError(uncertain ? '保存结果尚未确认，目录可能已经写入。请关闭此窗口，刷新列表核对目录后再编辑；本次不允许再次提交，以免重复编目。' : errorText);
        if (uncertain) onUncertainSave?.();
      }
    }
    finally { savingRef.current = false; if (seq === sequence.current) setSaving(false); }
  };
  const field = (name: string, label: string, required = false) => <Col xs={24} sm={12} key={name}><Form.Item name={name} label={label} rules={required ? [{ required: true, message: `请选择${label}` }] : undefined}><Select showSearch optionFilterProp="label" allowClear options={selectOptions(choices?.[name], form.getFieldValue(name))} placeholder={`请选择${label}`}/></Form.Item></Col>;
  return <Drawer className="catalog-registration-drawer" width="min(980px, 100vw)" title={directory ? '编辑数据目录' : '登记数据目录'} open={open} onClose={close} destroyOnHidden maskClosable={!saving} keyboard={!saving}
    footer={<Space className="catalog-editor-footer"><Button disabled={saving} onClick={close}>{saveUncertain ? '关闭并核对列表' : '取消'}</Button><Button type="primary" loading={saving} disabled={loading || Boolean(loadError) || saveUncertain} onClick={() => void save()}>保存目录</Button></Space>}>
    {loadError && <Alert className="catalog-editor-feedback" showIcon type="error" title="目录信息加载失败" description={loadError} action={<Button onClick={() => setLoadRevision(revision => revision + 1)}>重试</Button>}/>}
    {error && <Alert className="catalog-editor-feedback" showIcon type="error" title="目录操作失败" description={error}/>}
    <Spin spinning={loading}>
      {!loadError && <>
      <Descriptions className="catalog-editor-context" size="small" column={{ xs:1, sm:2 }} items={[
        {key:'org',label:'组织机构',children:source?.org_name || '—'}, {key:'app',label:'应用系统',children:source?.app_name || scope?.applications.find(app => app.value === source?.app_id)?.label || '未关联业务系统'},
        {key:'source',label:'数据源',children:source?.db_name || '—'}, {key:'table',label:'来源表 / 视图',children:table?.table_name || '—'},
      ]}/>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {key:'info',label:'目录信息',forceRender:true,children:<Form form={form} layout="vertical" disabled={loading || saving || saveUncertain} onValuesChange={() => { dirty.current = true; if (!saveUncertain) setError(''); }}>
          <Row gutter={20}>
            <Col xs={24} sm={12}><Form.Item name="catalogName" label="数据目录名称" rules={[{ required:true, whitespace:true, message:'请输入数据目录名称' }]}><Input maxLength={100} placeholder="请输入数据目录名称"/></Form.Item></Col>
            <Col xs={24} sm={12}><Form.Item name="catalogNameEn" label="数据目录英文名"><Input maxLength={255} placeholder="可沿用来源表英文名"/></Form.Item></Col>
            <Col xs={24} sm={12}><Form.Item name="dataCategory" label="数据所属分类" rules={[{ required:true, message:'请选择数据所属分类' }]}><TreeSelect treeData={treeOptions(choices?.dataCategory || [])} showSearch treeNodeFilterProp="title" allowClear placeholder="请选择数据所属分类"/></Form.Item></Col>
            {field('dataDomain','数据所属领域')}{field('dataLevel','数据所在层级')}{field('dataSecurityLevel','数据分级')}
            <Col span={24}><Form.Item name="assetDesc" label="数据摘要" rules={[{ required:true, whitespace:true, message:'请填写数据摘要' }]}><Input.TextArea rows={3} maxLength={500} showCount placeholder="说明数据资源的核心内容、覆盖范围及用途"/></Form.Item></Col>
            {field('dataProcessLevel','数据加工程度')}{field('dataRegionScope','数据区域范围')}{field('shareType','共享类型')}{field('dataUpdateFreq','更新周期')}
            <Col xs={24} sm={12}><Form.Item name="orgId" label="目录来源部门" rules={[{ required:true, message:'请选择目录来源部门' }]}><TreeSelect treeData={treeOptions(scope?.organizations.flatMap(option => option.value === 'ROOT' ? option.children || [] : [option]) || [])} showSearch treeNodeFilterProp="title" placeholder="请选择目录来源部门"/></Form.Item></Col>
            <Col xs={24} sm={12}><Form.Item name="concatName" label="来源部门联系人"><Input maxLength={100}/></Form.Item></Col>
          </Row>
        </Form>},
        {key:'items',label:`数据项（${items.length}）`,children:<div className="catalog-editor-items"><div className="catalog-items-toolbar"><span>{catalogName || '数据目录'} · 数据项</span><Button icon={<PlusOutlined/>} disabled={editingDisabled} onClick={() => { if (editingDisabled) return; dirty.current = true; setError(''); setItems(previous => [...previous, {clientKey:crypto.randomUUID(),colName:'',colEn:'',colType:'varchar',isPk:'0',isNullable:'1'}]); }}>新增数据项</Button></div>
          <Table<EditorItem> className="standard-data-table" rowKey="clientKey" size="small" pagination={{pageSize:20,showSizeChanger:true}} dataSource={items} scroll={{x:1120}} columns={[
            {title:'数据项名称',width:180,render:(_value,item) => <Input aria-label={`数据项名称 ${item.colEn}`} value={item.colName} maxLength={255} disabled={editingDisabled} onChange={event => changeItem(item.clientKey,{colName:event.target.value})}/>},
            {title:'英文代码',width:180,render:(_value,item) => <Input aria-label={`英文代码 ${item.colEn}`} value={item.colEn} maxLength={255} disabled={editingDisabled} onChange={event => changeItem(item.clientKey,{colEn:event.target.value})}/>},
            {title:'类型',width:160,render:(_value,item) => <Select showSearch allowClear={false} value={item.colType} style={{width:'100%'}} options={selectOptions(choices?.colType,item.colType)} disabled={editingDisabled} onChange={value => changeItem(item.clientKey,{colType:value})}/>},
            {title:'长度',width:100,render:(_value,item) => <InputNumber aria-label={`长度 ${item.colEn}`} min={0} precision={0} value={item.colLength} disabled={editingDisabled} onChange={value => changeItem(item.clientKey,{colLength:value ?? undefined})}/>},
            {title:'主键',width:65,align:'center',render:(_value,item) => <Checkbox aria-label={`主键 ${item.colEn}`} checked={item.isPk==='1'} disabled={editingDisabled} onChange={event => changeItem(item.clientKey,{isPk:event.target.checked?'1':'0'})}/>},
            {title:'可空',width:65,align:'center',render:(_value,item) => <Checkbox aria-label={`可空 ${item.colEn}`} checked={item.isNullable!=='0'} disabled={editingDisabled} onChange={event => changeItem(item.clientKey,{isNullable:event.target.checked?'1':'0'})}/>},
            {title:'数据标准',width:170,render:(_value,item) => <Select showSearch allowClear optionFilterProp="label" style={{width:'100%'}} value={item.dataStandardId ? String(item.dataStandardId) : undefined} options={selectOptions(choices?.dataStandard,item.dataStandardId)} disabled={editingDisabled} onChange={value => changeItem(item.clientKey,{dataStandardId:value || ''})}/>},
            {title:'操作',className:'wx-action-column',width:70,fixed:'right',align:'center',render:(_value,item) => <Button type="text" danger aria-label={`移除数据项 ${item.colEn}`} icon={<DeleteOutlined/>} disabled={editingDisabled} onClick={() => { if (editingDisabled) return; dirty.current = true; setError(''); setItems(previous => previous.filter(row => row.clientKey!==item.clientKey)); }}/>},
          ]}/></div>},
      ]}/>
      </>}
    </Spin>
  </Drawer>;
}
