import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Checkbox, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Spin, Table, Tabs, TreeSelect } from 'antd';
import { DeleteOutlined, PlusOutlined, SafetyCertificateOutlined, UploadOutlined } from '@ant-design/icons';
import { platformApi } from '../../../shared/platformApi';
import { assetsApi } from '../../../services/assets';
import { catalogItemsFromColumns, catalogRegistrationApi, type CatalogChoices, type CatalogItem } from '../../../services/catalogRegistrationApi';
import { connectionFields, datasourceRegistrationApi, draftFromDetail, leafOptions, makeJdbcUrl, type SourceChoices, type SourceDraft, type SourceOption } from '../../../services/datasourceRegistrationApi';
import { asText, assetKindLabels, registrationKinds, type AssetPageResult, type AssetSummary, type RegistrationType } from './assetObjects';

type Draft = Record<string, unknown>;
type EditorItem = CatalogItem & { clientKey: string };
type Parameter = { clientKey: string; paramName: string; paramType: string; paramPosition: string; required: string; description: string; res: number; [key: string]: unknown };
const text = (value: unknown) => value == null ? '' : String(value);
function safeProperties(raw: Draft): Draft {
  return Object.fromEntries(Object.entries(raw).filter(([name]) => !/^(tenant_?id|created_?by|updated_?by|asset_?status|flow_?status|flow_?order_?id|clientSecret|privateKey|publicKey)$/i.test(name)));
}
function orgTree(options: SourceOption[]): { value: string; title: string; children: ReturnType<typeof orgTree> }[] {
  return options.flatMap(option => option.value === 'ROOT' ? orgTree(option.children || []) : [{ value: option.value, title: option.label, children: orgTree(option.children || []) }]);
}
const required = (label: string) => [{ required: true, message: `请填写${label}` }];
const itemTypeOptions = ['varchar', 'char', 'text', 'integer', 'bigint', 'decimal', 'date', 'datetime', 'timestamp', 'boolean', 'json'].map(value => ({ value, label: value }));

/** Registration writes canonical /dst and gateway records; publishing is a separate action. */
export function RegistrationEditor({ type, id, onClose, onSaved }: { type: RegistrationType; id?: string; onClose: () => void; onSaved: (id: string) => void }) {
  const { message, modal } = App.useApp(); const [form] = Form.useForm<Draft>();
  const [choices, setChoices] = useState<SourceChoices>(); const [dict, setDict] = useState<CatalogChoices>({});
  const [loading, setLoading] = useState(true), [saving, setSaving] = useState(false), [testing, setTesting] = useState(false), [error, setError] = useState(''), [loadError, setLoadError] = useState('');
  const [importing, setImporting] = useState(false);
  const [revision, setRevision] = useState(0), [tab, setTab] = useState('basic'), [uncertain, setUncertain] = useState(false);
  const [items, setItems] = useState<EditorItem[]>([]), [parameters, setParameters] = useState<Parameter[]>([]);
  const [tableOptions, setTableOptions] = useState<AssetSummary[]>([]), [tableLoading, setTableLoading] = useState(false), [tableKeyword, setTableKeyword] = useState('');
  const existing = useRef<Draft>({}), dirty = useRef(false), saveLock = useRef(false), request = useRef(0), fileInput = useRef<HTMLInputElement>(null);
  const engine = text(Form.useWatch('dbType', form));
  const label = assetKindLabels[registrationKinds[type]];
  useEffect(() => {
    const seq = ++request.current; dirty.current = false; existing.current = {}; form.resetFields(); setItems([]); setParameters([]); setError(''); setLoadError(''); setUncertain(false); setLoading(true);
    const load = async () => {
      const [options, dictionary] = await Promise.all([datasourceRegistrationApi.choices(), type === 'catalogs' ? catalogRegistrationApi.choices() : Promise.resolve({})]);
      let record: Draft = {}; let dataItems: CatalogItem[] = [];
      if (id) {
        if (type === 'applications') record = await platformApi<Draft>('/dst/application/detail', { tid: id });
        if (type === 'databases') record = draftFromDetail(await datasourceRegistrationApi.detail(id));
        if (type === 'catalogs') { const result = await catalogRegistrationApi.detail(id); record = result.detail; dataItems = result.items; }
        if (type === 'apis') record = await platformApi<Draft>('/dws/flowserve/gateway/api/detail', { apiId: id });
        if (!record || typeof record !== 'object') throw new Error('登记信息未完整返回，请重试');
      }
      if (seq !== request.current) return;
      setChoices(options); setDict(dictionary); existing.current = safeProperties(record);
      const initial = { ...record };
      if (type === 'apis') {
        const extension = (record.extendInfo && typeof record.extendInfo === 'object' ? record.extendInfo : {}) as Draft;
        Object.assign(initial, { ...extension, serviceName: record.serviceName || record.name || '', method: record.method || 'GET', uri: record.uri || record.address || '', serviceDesc: record.serviceDesc || extension.apiDescription || '', responseExample: record.responseExample || extension.responseExample || '', version: record.version || '1.0', protocol: record.protocol || 'HTTP' });
        const raw = Array.isArray(record.publishParams) ? record.publishParams : [];
        setParameters(raw.map((value, index) => { const item = value as Draft; return { ...item, clientKey: text(item.tid) || `parameter-${index}`, paramName: text(item.paramName), paramType: text(item.paramType) || 'string', paramPosition: text(item.paramPosition) || 'query', required: text(item.required) || '0', description: text(item.description), res: item.res === 1 || item.paramScope === 'response' ? 1 : 0 }; }));
      }
      if (type === 'databases' && !id) Object.assign(initial, { dbType: options.types.find(option => option.value === 'mysql')?.value || options.types[0]?.value, port: 3306 });
      form.setFieldsValue(initial); setItems(dataItems.map((item, index) => ({ ...item, clientKey: item.tid || `item-${index}` })));
    };
    void load().catch(cause => { if (seq === request.current) setLoadError(cause instanceof Error ? cause.message : String(cause)); }).finally(() => { if (seq === request.current) setLoading(false); });
    return () => { request.current++; };
  }, [id, type, revision, form]);
  useEffect(() => {
    if (type !== 'catalogs') return;
    let active = true; const timer = setTimeout(() => {
      setTableLoading(true);
      void assetsApi.get<AssetPageResult>('/search', { view: 'MAP', kinds: 'TABLE', keyword: tableKeyword, pageNo: 1, pageSize: 30, sortBy: 'name', sortOrder: 'asc' }).then(value => { if (active) setTableOptions(value.items); }).catch(cause => { if (active) message.error(cause instanceof Error ? cause.message : String(cause)); }).finally(() => { if (active) setTableLoading(false); });
    }, 250); return () => { active = false; clearTimeout(timer); };
  }, [type, tableKeyword, message]);
  const disabled = loading || saving || importing || uncertain;
  const close = () => { if (saveLock.current) return; if (dirty.current && !uncertain) modal.confirm({ title: '放弃本次未保存的修改？', okText: '放弃修改', cancelText: '继续编辑', onOk: onClose }); else onClose(); };
  const optionsFor = (key: string) => { const options = leafOptions(dict[key] || []); const value = text(form.getFieldValue(key)); return value && !options.some(option => option.value === value) ? [...options, { value, label: value }] : options; };
  const input = (name: string, title: string, necessary = false, span = 12) => <Col xs={24} sm={span} key={name}><Form.Item name={name} label={title} rules={necessary ? required(title) : undefined}><Input maxLength={name === 'uri' || name === 'provinceUrl' ? 2000 : 255}/></Form.Item></Col>;
  const org = <Col xs={24} sm={12}><Form.Item name="orgId" label="提供部门" rules={required('提供部门')}><TreeSelect treeData={orgTree(choices?.organizations || [])} showSearch treeNodeFilterProp="title" placeholder="选择提供部门"/></Form.Item></Col>;
  const description = (name = 'assetDesc') => <Col span={24}><Form.Item name={name} label="业务说明"><Input.TextArea rows={3} maxLength={2000} showCount placeholder="描述业务范围、数据内容和使用场景"/></Form.Item></Col>;
  const select = (name: string, title: string, necessary = false) => <Col xs={24} sm={12} key={name}><Form.Item name={name} label={title} rules={necessary ? required(title) : undefined}><Select allowClear showSearch optionFilterProp="label" options={optionsFor(name)} /></Form.Item></Col>;
  const save = async () => {
    if (disabled || loadError || saveLock.current) return;
    let values: Draft; try { values = await form.validateFields(); } catch { setTab('basic'); return; }
    if (type === 'catalogs') {
      if (items.some(item => !item.colName.trim() || !item.colEn.trim() || !item.colType.trim())) { setTab('items'); setError('请完整填写数据项名称、英文代码和类型'); return; }
      if (new Set(items.map(item => item.colEn.trim().toLowerCase())).size !== items.length) { setTab('items'); setError('数据项英文代码不能重复'); return; }
    }
    if (type === 'apis' && parameters.some(item => !item.paramName.trim() || !item.paramType)) { setTab('parameters'); setError('请填写所有参数的名称和类型'); return; }
    saveLock.current = true; setSaving(true); setError('');
    try {
      let result: { tid?: string; id?: string; indexRefreshPending?: boolean };
      const props = safeProperties({ ...existing.current, ...values });
      if (type === 'applications') result = await platformApi('/dst/application/save', { ...(id ? { tid: id } : {}), assetType: 'app', propList: props });
      else if (type === 'databases') {
        const draft = props as SourceDraft; if (!draft.jdbcURL) draft.jdbcURL = makeJdbcUrl(draft);
        result = await datasourceRegistrationApi.save(draft, id);
      } else if (type === 'catalogs') result = await platformApi('/dst/catalog/saveOrUpdate', { ...(id ? { tid: id } : {}), assetType: 'catalog', propList: props, catalogItems: items.map(({ clientKey: _key, ...item }, index) => ({ ...item, sortNo: index + 1 })) });
      else {
        const old = existing.current; const provider = old.providerConfig && typeof old.providerConfig === 'object' ? old.providerConfig as Draft : {};
        const publish = old.publishConfig && typeof old.publishConfig === 'object' ? old.publishConfig as Draft : {};
        const extension = old.extendInfo && typeof old.extendInfo === 'object' ? old.extendInfo as Draft : {};
        result = await platformApi('/dws/flowserve/gateway/save', { ...old, ...props, ...(id ? { id } : {}), serviceName: text(values.serviceName).trim(), providerConfig: { ...provider, protocol: values.protocol || 'HTTP', address: values.uri, httpMethod: values.method }, publishConfig: { ...publish, publishAddress: values.uri, publishMethod: values.method }, publishParams: parameters.map(({ clientKey: _key, ...item }) => item), extendInfo: { ...extension, orgId: values.orgId, apiDescription: values.serviceDesc, responseExample: values.responseExample, requestExample: values.requestExample, responseDescription: values.responseDescription } });
      }
      const savedId = result?.tid || result?.id; if (!savedId) { setUncertain(true); throw new Error('保存结果尚未确认，请关闭窗口并刷新列表核对'); }
      dirty.current = false;
      if (type === 'databases') {
        try { await datasourceRegistrationApi.refreshIndex(savedId); } catch { message.warning('业务库登记已保存，列表同步尚未完成，请稍后刷新'); onSaved(savedId); return; }
      }
      message.success(`${label}登记已保存`); onSaved(savedId);
    } catch (cause) {
      const messageText = cause instanceof Error ? cause.message : String(cause);
      const unknownResult = (cause as { timedOut?: boolean })?.timedOut || messageText.includes('无法连接数据中台');
      if (unknownResult) setUncertain(true);
      setError(unknownResult ? '保存结果尚未确认，记录可能已写入。请关闭窗口并刷新列表核对后再操作。' : messageText);
    } finally { saveLock.current = false; setSaving(false); }
  };
  const importColumns = async (tableId: string) => {
    const table = tableOptions.find(row => row.object.id === tableId); if (!table) return;
    const apply = async () => {
      setTableLoading(true); setError('');
      try { const columns = await catalogRegistrationApi.columns(tableId); const generated = catalogItemsFromColumns(columns); setItems(generated.map(item => ({ ...item, clientKey: crypto.randomUUID() }))); form.setFieldsValue({ sourceTableId: tableId, sourceTableName: table.object.code, catalogName: form.getFieldValue('catalogName') || table.object.name, catalogNameEn: form.getFieldValue('catalogNameEn') || table.object.code }); dirty.current = true; message.success(`已生成 ${generated.length} 个数据项`); }
      catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
      finally { setTableLoading(false); }
    };
    if (items.length) modal.confirm({ title: '用所选数据表重新生成数据项？', content: '将替换当前尚未保存的数据项内容。', okText: '重新生成', cancelText: '取消', onOk: apply }); else void apply();
  };
  const importExcel = async (file?: File) => {
    if (fileInput.current) fileInput.current.value = '';
    if (!file || disabled) return;
    if (file.size > 5_000_000) { setError('Excel 文件不能超过 5 MB'); setTab('items'); return; }
    setImporting(true); setError(''); setTab('items');
    try {
      const parsed = await assetsApi.upload<{ items: CatalogItem[]; totalRows: number; issues: { rowNumber: number; message: string }[] }>('/items/import/parse', file);
      if (parsed.issues.length) { setError(`Excel 有 ${parsed.issues.length} 处问题：${parsed.issues.slice(0, 4).map(issue => `第 ${issue.rowNumber} 行 ${issue.message}`).join('；')}`); return; }
      if (!parsed.items.length) { setError('Excel 文件没有可导入的数据项'); return; }
      const apply = () => { setItems(parsed.items.map(item => ({ ...item, clientKey: crypto.randomUUID() }))); dirty.current = true; message.success(`已解析 ${parsed.items.length} 个数据项，保存登记后生效`); };
      if (items.length) modal.confirm({ title: '用 Excel 数据项替换当前编辑内容？', content: '将替换当前窗口中尚未保存的数据项，保存登记后才会写入目录。', okText: '替换', cancelText: '取消', onOk: apply });
      else apply();
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setImporting(false); }
  };
  const test = async () => {
    if (testing || disabled) return; setTesting(true); setError('');
    try { const values = await form.validateFields(); const draft = { ...existing.current, ...values } as SourceDraft; if (!draft.jdbcURL) draft.jdbcURL = makeJdbcUrl(draft); const result = await datasourceRegistrationApi.test(draft, id); if (result.connected !== true && result.success !== true) throw new Error(result.message || result.error || '连接测试未通过'); message.success('连接测试通过'); }
    catch (cause) { if (!(cause && typeof cause === 'object' && 'errorFields' in cause)) setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setTesting(false); }
  };
  const editItem = (key: string, patch: Partial<CatalogItem>) => { dirty.current = true; setItems(previous => previous.map(item => item.clientKey === key ? { ...item, ...patch } : item)); };
  const editParam = (key: string, patch: Partial<Parameter>) => { dirty.current = true; setParameters(previous => previous.map(item => item.clientKey === key ? { ...item, ...patch } : item)); };
  const basic = <Row gutter={20}>
    {type === 'applications' && <>{input('appName', '应用系统名称', true)}{input('appCode', '系统编码')}{org}{input('applicationClass', '系统分类')}{input('provinceUrl', '服务地址')}{input('deploymentMode', '部署方式')}{input('constructionStatus', '建设状态')}{input('operatorName', '运营单位')}{input('businessScope', '业务范围')}{input('serviceAudience', '服务对象')}{input('contactName', '负责人')}{input('contactPhone', '联系电话')}{description()}</>}
    {type === 'databases' && <>{input('dbName', '业务库名称', true)}<Col xs={24} sm={12}><Form.Item name="dbType" label="数据源类型" rules={required('数据源类型')}><Select options={choices?.types} showSearch optionFilterProp="label" disabled={Boolean(id)} onChange={() => { dirty.current = true; form.setFieldValue('jdbcURL', ''); }}/></Form.Item></Col>{org}<Col xs={24} sm={12}><Form.Item name="appId" label="所属应用系统"><Select options={choices?.applications} showSearch optionFilterProp="label" allowClear/></Form.Item></Col><Col xs={24} sm={12}><Form.Item name="storageDomain" label="所属网域"><Select options={choices?.networks} allowClear/></Form.Item></Col>{input('contactName', '联系人')}{description()}<Col span={24}><h3 className="hy-form-section">连接配置</h3></Col>{connectionFields(engine, form.getFieldValue('hiveConnectionMode')).map(field => <Col xs={24} sm={field.wide ? 24 : 12} key={field.name}><Form.Item name={field.name} label={field.label} rules={field.required && !field.secret && field.name !== 'jdbcURL' ? required(field.label) : undefined}>{field.secret ? <Input.Password autoComplete="new-password" placeholder={id ? '留空保留已保存的凭据' : `输入${field.label}`}/> : <Input autoComplete="off" placeholder={field.name === 'jdbcURL' ? '留空时根据连接信息生成' : undefined}/>}</Form.Item></Col>)}</>}
    {type === 'catalogs' && <>{input('catalogName', '资源目录名称', true)}{input('catalogNameEn', '英文名称 / 编码')}{org}{select('dataCategory', '业务分类', true)}{select('dataDomain', '业务主题')}{select('dataSecurityLevel', '数据分级')}{select('shareType', '共享类型')}{select('dataUpdateFreq', '更新周期')}{input('concatName', '部门联系人')}{input('concatPhone', '联系电话')}{description()}<Form.Item name="sourceTableId" hidden><Input/></Form.Item><Form.Item name="sourceTableName" hidden><Input/></Form.Item></>}
    {type === 'apis' && <>{input('serviceName', 'API 服务名称', true)}{input('serviceCode', '服务编码')}{org}{input('serviceType', '服务类型')}<Col xs={24} sm={12}><Form.Item name="method" label="请求方法" rules={required('请求方法')}><Select options={['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map(value => ({ value, label: value }))}/></Form.Item></Col>{input('uri', '服务地址', true)}{input('version', '服务版本')}<Col xs={24} sm={12}><Form.Item name="protocol" label="协议"><Select options={['HTTP', 'HTTPS'].map(value => ({ value, label: value }))}/></Form.Item></Col>{description('serviceDesc')}{input('responseDescription', '响应说明', false, 24)}<Col span={24}><Form.Item name="requestExample" label="请求示例"><Input.TextArea rows={3} maxLength={12000}/></Form.Item></Col><Col span={24}><Form.Item name="responseExample" label="响应示例"><Input.TextArea rows={4} maxLength={16000}/></Form.Item></Col></>}
  </Row>;
  return <Drawer className="hy-registration-editor" open title={`${id ? '编辑' : '登记'}${label}`} width="min(900px, 100vw)" onClose={close} maskClosable={!saving} keyboard={!saving} destroyOnHidden footer={<div className="hy-editor-footer">{type === 'databases' && <Button icon={<SafetyCertificateOutlined/>} loading={testing} disabled={disabled || Boolean(loadError)} onClick={() => void test()}>测试连接</Button>}<span/><Space size={12}><Button disabled={saving} onClick={close}>{uncertain ? '关闭并核对列表' : '取消'}</Button><Button type="primary" loading={saving} disabled={disabled || Boolean(loadError)} onClick={() => void save()}>保存登记</Button></Space></div>}>
    {loadError && <Alert type="error" showIcon title="登记信息读取失败" description={loadError} action={<Button onClick={() => setRevision(value => value + 1)}>重试</Button>}/>}
    {error && <Alert className="hy-editor-error" type="error" showIcon title={error}/>}
    <Spin spinning={loading}>{!loadError && <Form form={form} layout="vertical" disabled={disabled} onValuesChange={() => { dirty.current = true; }}><Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'basic', label: '基本信息', forceRender: true, children: basic },
      ...(type === 'catalogs' ? [{ key: 'items', label: `数据项 ${items.length}`, children: <><div className="hy-editor-item-toolbar"><Select<string> aria-label="从已采集表生成数据项" showSearch filterOption={false} onSearch={setTableKeyword} onChange={value => void importColumns(value)} loading={tableLoading} value={undefined} placeholder="从已采集数据表生成数据项" options={tableOptions.map(row => ({ value: row.object.id, label: `${row.object.name} · ${row.object.code || ''}` }))}/><input ref={fileInput} type="file" accept=".xlsx,.xls" hidden onChange={event => void importExcel(event.target.files?.[0])}/><Button icon={<UploadOutlined/>} loading={importing} onClick={() => fileInput.current?.click()}>Excel 导入</Button><Button icon={<PlusOutlined/>} onClick={() => { dirty.current = true; setItems(previous => [...previous, { clientKey: crypto.randomUUID(), colName: '', colEn: '', colType: 'varchar', isPk: '0', isNullable: '1' }]); }}>新增数据项</Button></div><Table<EditorItem> className="hy-asset-table" rowKey="clientKey" dataSource={items} tableLayout="fixed" pagination={{ pageSize: 10, showSizeChanger: false }} scroll={{ x: 790 }} columns={[
        { title: '数据项名称', width: 180, render: (_value, item) => <Input aria-label="数据项名称" value={item.colName} onChange={event => editItem(item.clientKey, { colName: event.target.value })}/> },
        { title: '英文代码', width: 180, render: (_value, item) => <Input aria-label="数据项英文代码" value={item.colEn} onChange={event => editItem(item.clientKey, { colEn: event.target.value })}/> },
        { title: '类型', width: 140, render: (_value, item) => <Select value={item.colType} options={itemTypeOptions.some(option => option.value === item.colType) ? itemTypeOptions : [...itemTypeOptions, { value: item.colType, label: item.colType }]} onChange={value => editItem(item.clientKey, { colType: value })}/> },
        { title: '长度', width: 95, render: (_value, item) => <InputNumber aria-label="数据项长度" min={0} precision={0} value={item.colLength} onChange={value => editItem(item.clientKey, { colLength: value ?? undefined })}/> },
        { title: '主键', width: 62, align: 'center', render: (_value, item) => <Checkbox aria-label="主键" checked={item.isPk === '1'} onChange={event => editItem(item.clientKey, { isPk: event.target.checked ? '1' : '0' })}/> },
        { title: '可空', width: 62, align: 'center', render: (_value, item) => <Checkbox aria-label="可空" checked={item.isNullable !== '0'} onChange={event => editItem(item.clientKey, { isNullable: event.target.checked ? '1' : '0' })}/> },
        { title: '操作', fixed: 'right', width: 70, align: 'center', className: 'wx-action-column', render: (_value, item) => <Button aria-label={`删除数据项 ${item.colName}`} danger type="text" icon={<DeleteOutlined/>} onClick={() => { dirty.current = true; setItems(previous => previous.filter(row => row.clientKey !== item.clientKey)); }}/> },
      ]}/></> }] : []),
      ...(type === 'apis' ? [{ key: 'parameters', label: `请求与响应参数 ${parameters.length}`, children: <><div className="hy-editor-item-toolbar"><span>请求参数与响应字段</span><Button icon={<PlusOutlined/>} onClick={() => { dirty.current = true; setParameters(previous => [...previous, { clientKey: crypto.randomUUID(), paramName: '', paramType: 'string', paramPosition: 'query', required: '0', description: '', res: 0 }]); }}>新增参数</Button></div><Table<Parameter> rowKey="clientKey" dataSource={parameters} tableLayout="fixed" pagination={{ pageSize: 10 }} scroll={{ x: 940 }} columns={[
        { title: '名称', width: 160, render: (_value, item) => <Input value={item.paramName} onChange={event => editParam(item.clientKey, { paramName: event.target.value })}/> },
        { title: '范围', width: 100, render: (_value, item) => <Select value={item.res} options={[{ value: 0, label: '请求' }, { value: 1, label: '响应' }]} onChange={value => editParam(item.clientKey, { res: value, ...(value === 1 ? { paramPosition: 'body' } : {}) })}/> },
        { title: '类型', width: 115, render: (_value, item) => <Select value={item.paramType} options={['string', 'number', 'integer', 'boolean', 'object', 'array'].map(value => ({ value, label: value }))} onChange={value => editParam(item.clientKey, { paramType: value })}/> },
        { title: '位置', width: 110, render: (_value, item) => <Select value={item.paramPosition} options={['query', 'path', 'header', 'body'].map(value => ({ value, label: value }))} onChange={value => editParam(item.clientKey, { paramPosition: value })}/> },
        { title: '必填', width: 62, align: 'center', render: (_value, item) => <Checkbox checked={item.required === '1'} onChange={event => editParam(item.clientKey, { required: event.target.checked ? '1' : '0' })}/> },
        { title: '说明', width: 210, render: (_value, item) => <Input value={item.description} onChange={event => editParam(item.clientKey, { description: event.target.value })}/> },
        { title: '操作', fixed: 'right', className: 'wx-action-column', align: 'center', width: 70, render: (_value, item) => <Button danger type="text" aria-label={`删除参数 ${asText(item.paramName)}`} icon={<DeleteOutlined/>} onClick={() => { dirty.current = true; setParameters(previous => previous.filter(row => row.clientKey !== item.clientKey)); }}/> },
      ]}/></> }] : []),
    ]}/></Form>}</Spin>
  </Drawer>;
}
