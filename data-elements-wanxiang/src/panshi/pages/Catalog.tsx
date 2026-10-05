import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { App, Button, Drawer, Empty, Form, Input, InputNumber, Select, Space, Table, Tag } from 'antd';
import { FileSearchOutlined, PlusOutlined, SaveOutlined, SendOutlined, SyncOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useResource } from '../services/context';
import { Hero, Metric, NameCell, PTable, StateTag, timeText, useCommand } from '../components/ui';
import type { CatalogEntry } from '../domain/types';
import { getSession } from '../../services/api';
import { draftState, useDraftGuard } from '../app/leave';
import {
  centerSourceIds, centerTableColumns, centerTablePage, directoryDictionaries, itemsFromColumns, loadDirectory,
  type CenterTable, type CenterTablePage, type DirectoryDictionary, type DirectoryItem,
} from '../services/catalogApi';
import { resourceGet } from '../services/transport';

type CatalogForm = Record<string, unknown>;
type DictMap = Record<string, DirectoryDictionary[]>;
const dictFields = [
  'catalogType', 'shareType', 'isStatus', 'updateCycle', 'dataRegionScope',
  'dataSourceType', 'dataTableType', 'resourceSource', 'dataTheme', 'policeType',
  'dataLevel', 'storageLocation', 'tableLevel', 'colType', 'codeTable',
  'qualityRule', 'dataStandard',
];
const dictionaryCodes: Record<string, string> = {
  openType: 'isStatus', isBackflow: 'isStatus', isOnline: 'isStatus', tableType: 'dataTableType',
};
const publicSafetyRequired: Array<[string, string]> = [
  ['shareType', '共享属性'], ['openType', '是否对外开放'], ['assetDesc', '数据资源描述'],
  ['recordCount', '登记记录数'], ['updateCycle', '更新周期'], ['dataRegionScope', '数据范围'],
  ['dataSourceType', '数据资源分类'], ['tableType', '数据表类型'],
  ['resourceSource', '数据资源来源类型'], ['dataTheme', '数据主题'],
  ['policeType', '警种分类'], ['dataLevel', '数据分级分类'],
  ['manageUnit', '事权单位'], ['concatName', '事权单位联系人'],
  ['tableLevel', '表的分级'], ['concatPhone', '事权单位联系方式'],
];
const text = (value: unknown) => value == null ? '' : String(value);
const formValue = (form: CatalogForm, key: string) => text(form[key]) || undefined;
const catalogName = (entry: CatalogEntry) => entry.draft.name || '未命名目录';
const catalogState = (entry: CatalogEntry) => entry.draft.state;
const sourceName = (sources: Array<Record<string, unknown>>, id: string) =>
  text(sources.find(source => text(source.id) === id)?.name) || '未命名数据源';

function dictOptions(dicts: DictMap, code: string, current?: string) {
  const seen = new Set<string>();
  const options = (dicts[code] || []).flatMap(item => {
    const value = text(item.dictCode);
    if (!value || seen.has(value)) return [];
    seen.add(value);
    return [{ value, label: item.dictName || value }];
  });
  current?.split(',').filter(Boolean).forEach(value => {
    if (!seen.has(value)) options.unshift({ value, label: value });
  });
  return options;
}

function DictionaryField({ label, name, form, dicts, onChange, required = false, disabled = false, multiple = false }: {
  label: string; name: string; form: CatalogForm; dicts: DictMap;
  onChange: (key: string, value: unknown) => void; required?: boolean; disabled?: boolean; multiple?: boolean;
}) {
  const current = formValue(form, name);
  return <Form.Item label={label} required={required}>
    <Select allowClear disabled={disabled} showSearch optionFilterProp="label" mode={multiple ? 'multiple' : undefined}
      value={multiple ? current?.split(',').filter(Boolean) || [] : current}
      options={dictOptions(dicts, dictionaryCodes[name] || name, current)}
      onChange={value => onChange(name, multiple ? (value as string[]).join(',') : value ?? '')}
      placeholder={`请选择${label}`}/>
  </Form.Item>;
}

function TablePicker({ open, onClose, onChoose }: {
  open: boolean; onClose: () => void; onChoose: (table: CenterTable) => Promise<void>;
}) {
  const { data } = useResource();
  const ids = useMemo(() => centerSourceIds(data.state), [data.state]);
  const [sourceId, setSourceId] = useState('');
  const [keyword, setKeyword] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<CenterTablePage>({ rows: [], catalogs: [], total: 0, pageNo: 1, pageSize: 15 });
  const [loading, setLoading] = useState(false);
  const [choosing, setChoosing] = useState('');
  const { message } = App.useApp();
  const nav = useNavigate();
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true);
    void centerTablePage(ids, pageNo, keyword, sourceId)
      .then(result => { if (active) setPage(result); })
      .catch(error => { if (active) message.error(error instanceof Error ? error.message : String(error)); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, ids.join(','), pageNo, keyword, sourceId]);
  const choose = async (row: CenterTable) => {
    const existing = page.catalogs.find(item => item.source_table_id === row.tid);
    if (existing) { onClose(); nav('/resource/catalog/entries/' + existing.tid); return; }
    setChoosing(row.tid);
    try { await onChoose(row); onClose(); }
    catch (error) { message.error(error instanceof Error ? error.message : String(error)); }
    finally { setChoosing(''); }
  };
  return <Drawer title="选择数据中心数据表" width={960} open={open} onClose={onClose} destroyOnHidden={false}>
    <div className="ps-catalog-picker-controls">
      <Select allowClear showSearch optionFilterProp="label" placeholder="全部已规划数据源"
        value={sourceId || undefined} onChange={value => { setSourceId(value || ''); setPageNo(1); }}
        options={ids.map(id => ({ value: id, label: sourceName(data.sources, id) }))}/>
      <Input.Search allowClear placeholder="搜索表中文名或技术表名" value={keyword}
        onChange={event => { setKeyword(event.target.value); setPageNo(1); }}/>
    </div>
    {!ids.length ? <Empty description="请先在数仓规划中为分层或分库关联数据源"/> :
      <Table rowKey="tid" size="small" loading={loading} dataSource={page.rows}
        pagination={{ current: pageNo, pageSize: 15, total: page.total, showSizeChanger: false, onChange: setPageNo }}
        scroll={{ x: 820 }} columns={[
          { title: '数据表', width: 250, render: (_, row) => <NameCell name={row.table_name_cn || row.table_comment || row.table_name} code={row.table_name}/> },
          { title: '所属数据源', width: 190, render: (_, row) => sourceName(data.sources, row.datasource_id) },
          { title: '表类型', dataIndex: 'table_type', width: 110 },
          { title: '字段数', dataIndex: 'field_count', width: 90 },
          { title: '操作', width: 112, fixed: 'right', align: 'center', className: 'wx-action-column',
            render: (_, row) => <Button type="link" loading={choosing === row.tid} onClick={() => void choose(row)}>
              {page.catalogs.some(item => item.source_table_id === row.tid) ? '查看编目' : '选择编目'}
            </Button> },
        ]}/>}
  </Drawer>;
}

export function CatalogEntriesPage() {
  const { data, act, refresh } = useResource();
  const s = data.state, nav = useNavigate(), [params] = useSearchParams();
  const { run } = useCommand();
  const [status, setStatus] = useState('');
  const [sourceId, setSourceId] = useState('');
  const ids = centerSourceIds(s);
  const rows = s.catalogs.filter(entry => (!status || catalogState(entry) === status) &&
    (!sourceId || text((entry.draft as unknown as Record<string, unknown>).sourceId) === sourceId));
  const count = (state: string) => s.catalogs.filter(entry => catalogState(entry) === state).length;
  const tableCount = ids.reduce((total, id) => total + Math.max(0, ...[...s.layers, ...s.databases]
    .filter(item => item.sourceId === id).map(item => Number(item.tableCount) || 0)), 0);
  return <>
    <Hero page="catalog-entries" actions={<Button type="primary" icon={<PlusOutlined/>}
      onClick={() => nav('/resource/catalog/entries/new')}>选择数据表编目</Button>}/>
    <div className="ps-metrics">
      <Metric icon="warehouse" label="数据中心表" value={tableCount} note="已关联到数仓规划"/>
      <Metric icon="catalog-entries" label="待编目" value={count('DRAFT') + count('RETURNED')} note="保存后提交审核"/>
      <Metric icon="catalog-reviews" label="待审核" value={count('PENDING_REVIEW')} note="等待目录审核"/>
      <Metric icon="catalog-reviews" label="已审核" value={count('APPROVED')}
        note={`${s.catalogs.filter(entry => catalogState(entry) === 'APPROVED' && !entry.draft.fieldIds.length).length} 份需补充数据项`}/>
    </div>
    <PTable rows={rows} onRefresh={refresh} initialSearch={params.get('q') ?? ''}
      searchText={entry => `${entry.draft.name} ${entry.draft.code} ${(entry.draft as unknown as Record<string, unknown>).resourceName || ''}`}
      tools={<>
        <Select allowClear placeholder="全部审核状态" value={status || undefined} onChange={value => setStatus(value || '')}
          options={['DRAFT', 'PENDING_REVIEW', 'APPROVED', 'RETURNED'].map(value => ({ value, label: <StateTag state={value}/> }))}/>
        <Select allowClear showSearch optionFilterProp="label" placeholder="全部数据源" value={sourceId || undefined}
          onChange={value => setSourceId(value || '')}
          options={ids.map(id => ({ value: id, label: sourceName(data.sources, id) }))}/>
      </>}
      columns={[
        { title: '数据目录名称', width: 230, render: (_, entry) => <button className="ps-text-button"
          onClick={() => nav('/resource/catalog/entries/' + entry.id)}>
          <NameCell name={catalogName(entry)} code={entry.draft.code}/></button> },
        { title: '关联数据表', width: 220, render: (_, entry) =>
          <NameCell name={text((entry.draft as unknown as Record<string, unknown>).resourceName) || '已选表'}
            code={entry.draft.resourceId}/> },
        { title: '所属数据源', width: 170, render: (_, entry) =>
          sourceName(data.sources, text((entry.draft as unknown as Record<string, unknown>).sourceId)) },
        { title: '数据项', width: 110, render: (_, entry) => entry.draft.fieldIds.length
          ? `${entry.draft.fieldIds.length} 项` : <Tag color="orange">待补充</Tag> },
        { title: '审核状态', width: 110, render: (_, entry) => <StateTag state={catalogState(entry)}/> },
        { title: '更新时间', width: 170, render: (_, entry) => timeText(entry.updatedAt) },
        { title: '操作', width: 150, fixed: 'right', align: 'center', className: 'wx-action-column', render: (_, entry) => <Space size={8}>
          <Button type="link" onClick={() => nav('/resource/catalog/entries/' + entry.id)}>
            {['DRAFT', 'RETURNED'].includes(catalogState(entry)) ? '编目' :
              entry.draft.fieldIds.length ? '查看' : '编辑'}
          </Button>
          {['DRAFT', 'RETURNED'].includes(catalogState(entry)) &&
            <Button type="link" onClick={() => void run(
              () => act(`catalogs/${entry.id}/submit`, {}, entry.version), '目录已提交审核',
            )}>提交审核</Button>}
        </Space> },
      ]}/>
  </>;
}

function CatalogItemEditor({ items, dicts, standards, disabled, onChange }: {
  items: DirectoryItem[]; dicts: DictMap; standards: Array<Record<string, unknown>>;
  disabled: boolean; onChange: (items: DirectoryItem[]) => void;
}) {
  const patch = (index: number, value: Partial<DirectoryItem>) =>
    onChange(items.map((item, position) => position === index ? { ...item, ...value } : item));
  return <Table<DirectoryItem> rowKey={(item, index) => item.sourceTableColumnId || item.tid || `manual-${index}`}
    size="small" pagination={{ pageSize: 10 }} scroll={{ x: 1260 }} dataSource={items}
    columns={[
      { title: '序号', width: 60, render: (_, __, index) => index + 1 },
      { title: '数据项名称', width: 220, render: (_, item, index) => <Input disabled={disabled} value={item.colName}
        onChange={event => patch(index, { colName: event.target.value })}/> },
      { title: '英文名', width: 200, render: (_, item, index) => <Input disabled={disabled} value={item.colEn}
        onChange={event => patch(index, { colEn: event.target.value })}/> },
      { title: '类型', width: 170, render: (_, item, index) => <Select disabled={disabled} showSearch optionFilterProp="label"
        value={item.colType || undefined} options={dictOptions(dicts, 'colType', item.colType)} style={{ width: '100%' }}
        onChange={value => patch(index, { colType: value })}/> },
      { title: '长度', width: 95, render: (_, item, index) => <InputNumber disabled={disabled} min={0} max={999999}
        value={item.colLength} style={{ width: '100%' }} onChange={value => patch(index, { colLength: value ?? undefined })}/> },
      { title: '主键', width: 65, render: (_, item) => item.isPk === '1' ? <Tag color="gold">是</Tag> : '否' },
      { title: '数据标准', width: 185, render: (_, item, index) => <Select disabled={disabled} allowClear showSearch optionFilterProp="label"
        value={item.dataStandardId || undefined} style={{ width: '100%' }}
        options={dictOptions(dicts, 'dataStandard', item.dataStandardId).length
          ? dictOptions(dicts, 'dataStandard', item.dataStandardId)
          : standards.filter(standard => standard.status === 'PUBLISHED').map(standard => ({
            value: text(standard.id), label: `${text(standard.name)} · ${text(standard.code)}`,
          }))}
        onChange={value => patch(index, { dataStandardId: value || '' })}/> },
      { title: '质量规则', width: 155, render: (_, item, index) => <Select disabled={disabled} allowClear showSearch optionFilterProp="label"
        mode="multiple" value={item.qualityRule?.split(',').filter(Boolean) || []} style={{ width: '100%' }}
        options={dictOptions(dicts, 'qualityRule', item.qualityRule)}
        onChange={value => patch(index, { qualityRule: value.join(',') })}/> },
      { title: '码表转换', width: 155, render: (_, item, index) => <Select disabled={disabled} allowClear showSearch optionFilterProp="label"
        value={item.codeTableId || undefined} style={{ width: '100%' }}
        options={dictOptions(dicts, 'codeTable', item.codeTableId)}
        onChange={value => patch(index, { codeTableId: value || '', enableCodeTable: value ? 1 : 0 })}/> },
      { title: '操作', width: 80, fixed: 'right', align: 'center', className: 'wx-action-column',
        render: (_, __, index) => !disabled && <Button type="link" danger onClick={() =>
          onChange(items.filter((_, position) => position !== index))}>移除</Button> },
    ]}/>;
}

export function CatalogEditorPage() {
  const { id = 'new' } = useParams(), [params] = useSearchParams(), nav = useNavigate();
  const { data, act } = useResource(), { message, modal } = App.useApp();
  const existing = data.state.catalogs.find(entry => entry.id === id);
  const [form, setForm] = useState<CatalogForm>({});
  const [items, setItems] = useState<DirectoryItem[]>([]);
  const [dicts, setDicts] = useState<DictMap>({});
  const [pickerOpen, setPickerOpen] = useState(id === 'new' && !params.get('resource'));
  const [loading, setLoading] = useState(id !== 'new');
  const [busy, setBusy] = useState(false);
  const [dirty, setDirty] = useState(false);
  const currentId = useRef(id);
  const version = useRef(existing?.version);
  const locked = !!existing && !['DRAFT', 'RETURNED'].includes(existing.draft.state);
  const publicSafety = text(getSession()?.tenant?.code).toUpperCase() === 'PUBLIC_SECURITY';
  const ids = useMemo(() => centerSourceIds(data.state), [data.state]);
  useEffect(() => {
    let active = true;
    void directoryDictionaries(dictFields)
      .then(result => { if (active) setDicts(result); })
      .catch(error => { if (active) message.error(error instanceof Error ? error.message : String(error)); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    let active = true;
    currentId.current = id;
    version.current = existing?.version;
    setDirty(false);
    if (id === 'new') {
      setForm({ orgId: getSession()?.user?.orgId || getSession()?.user?.org_id || '',
        manageUnit: getSession()?.user?.orgId || getSession()?.user?.org_id || '',
        concatName: getSession()?.name || '',
        concatPhone: getSession()?.user?.phone || getSession()?.user?.mobile || '' });
      setItems([]);
      setLoading(false);
      const linkedTableId = params.get('resource') || params.get('table');
      if (linkedTableId && ids.length) {
        void resourceGet<CenterTablePage>('/dwm/metadata-governance/tables/page',
          { sourceIds: ids.join(','), tableId: linkedTableId, pageNo: 1, pageSize: 1 })
          .then(page => { if (active && page.rows[0]) return chooseTable(page.rows[0]); })
          .catch(error => { if (active) message.error(error instanceof Error ? error.message : String(error)); });
      } else setPickerOpen(true);
    } else {
      setLoading(true);
      void loadDirectory(id).then(async ({ detail, items: savedItems }) => {
        if (!active) return;
        setForm({ ...(detail.props || {}),
          catalogName: detail.catalogName, catalogNameEn: detail.catalogNameEn || '',
          assetDesc: detail.assetDesc || '', sourceTableId: detail.sourceTableId || '',
          sourceTableName: detail.sourceTableName || '', dbId: detail.dbId || '',
          orgId: detail.orgId || '' });
        if (!savedItems.length && detail.sourceTableId &&
          ['DRAFT', 'RETURNED'].includes(existing?.draft.state || '')) {
          const columns = await centerTableColumns(detail.sourceTableId);
          if (!active) return;
          setItems(itemsFromColumns(columns));
          if (columns.length) setDirty(true);
        } else setItems(savedItems);
      }).catch(error => { if (active) message.error(error instanceof Error ? error.message : String(error)); })
        .finally(() => { if (active) setLoading(false); });
    }
    return () => { active = false; };
  }, [id, existing?.draft.state]);
  const patch = (key: string, value: unknown) => { setForm(current => ({ ...current, [key]: value })); setDirty(true); };
  const chooseTable = async (table: CenterTable) => {
    if (!ids.includes(table.datasource_id)) throw new Error('这张表不属于已规划的数据中心数据源');
    const columns = await centerTableColumns(table.tid);
    const source = data.sources.find(item => item.id === table.datasource_id);
    const layer = data.state.layers.find(item => item.sourceId === table.datasource_id)
      || data.state.layers.find(item => data.state.databases.some(domain =>
        domain.layerId === item.id && domain.sourceId === table.datasource_id));
    setForm(current => ({
      ...current,
      sourceTableId: table.tid,
      sourceTableName: table.table_name,
      catalogName: text(current.catalogName) || table.table_name_cn || table.table_comment || table.table_name,
      catalogNameEn: text(current.catalogNameEn) || table.table_name,
      assetDesc: text(current.assetDesc) || table.table_comment || table.table_name_cn || '',
      dbId: table.datasource_id,
      appId: source?.applicationId || '',
      orgId: text(current.orgId) || source?.orgId || '',
      dataSourceType: layer?.code.toLowerCase() || text(current.dataSourceType) || 'ods',
    }));
    setItems(itemsFromColumns(columns));
    setDirty(true);
  };
  const setItemRows = (value: DirectoryItem[]) => { setItems(value); setDirty(true); };
  const save = useCallback(async () => {
    if (locked) throw new Error('请先将已审核目录转为重新编目状态');
    if (!formValue(form, 'sourceTableId') || !ids.includes(text(form.dbId))) throw new Error('请先选择数据中心的数据表');
    if (!formValue(form, 'catalogName')) throw new Error('请填写数据目录名称');
    if (!formValue(form, 'orgId')) throw new Error('请填写来源部门');
    if (!items.length || items.some(item => !item.colName.trim() || !item.colEn.trim() || !item.colType.trim()))
      throw new Error('请完善目录数据项名称、英文名和类型');
    setBusy(true);
    try {
      const props = { ...form };
      delete props.resourceCenterDraft;
      delete props.resourceCenterAuditOpinion;
      delete props.assetStatus;
      delete props.flowStatus;
      const saved = await act<CatalogEntry>(
        `catalogs${currentId.current === 'new' ? '' : '/' + currentId.current}`,
        { propList: props, catalogItems: items.map((item, index) => ({ ...item, sortNo: index + 1 })) },
        version.current,
        currentId.current === 'new' ? 'POST' : 'PUT',
      );
      version.current = saved.version;
      currentId.current = saved.id;
      setDirty(false);
      draftState.dirty = false;
      if (id === 'new') nav('/resource/catalog/entries/' + saved.id, { replace: true });
      return saved;
    } finally { setBusy(false); }
  }, [form, items, locked, ids.join(','), id, act, nav]);
  useDraftGuard(dirty, async () => { await save(); });
  const submit = async () => {
    try {
      if (publicSafety) {
        const missing = publicSafetyRequired.find(([key]) => formValue(form, key) == null);
        if (missing) throw new Error(`请填写${missing[1]}后再提交审核`);
      }
      const saved = dirty || currentId.current === 'new' ? await save() : existing;
      if (!saved) return;
      await act(`catalogs/${saved.id}/submit`, {}, saved.version);
      setDirty(false); draftState.dirty = false;
      message.success('目录已提交审核');
      nav('/resource/catalog/reviews');
    } catch (error) { message.error(error instanceof Error ? error.message : String(error)); }
  };
  const syncFields = () => {
    const tableId = formValue(form, 'sourceTableId');
    if (!tableId) return;
    modal.confirm({ title: '重新同步表字段？', content: '将用当前采集的表结构替换正在编辑的数据项。', onOk: async () => {
      const columns = await centerTableColumns(tableId);
      setItemRows(itemsFromColumns(columns));
    } });
  };
  const fillDefaults = () => {
    const values: CatalogForm = {};
    for (const [key] of publicSafetyRequired) {
      const code = dictionaryCodes[key] || key;
      if (!formValue(form, key) && dicts[code]?.length) values[key] = dicts[code][0].dictCode;
    }
    if (!Object.keys(values).length) { message.info('没有可填充的必填下拉项'); return; }
    modal.confirm({ title: '填充未填写的必填下拉项？', content: '只填充当前为空的选项，保存前仍可逐项修改。',
      onOk: () => { setForm(current => ({ ...current, ...values })); setDirty(true);
        message.success(`已填充 ${Object.keys(values).length} 个选项`); } });
  };
  const matchStandards = () => {
    const standards = dicts.dataStandard || [];
    if (!standards.length) { message.info('暂无可用的数据标准'); return; }
    const normalized = (value: string) => value.trim().toLocaleLowerCase();
    let matched = 0;
    const next = items.map(item => {
      if (item.dataStandardId) return item;
      const candidates = [...new Set(standards.filter(standard =>
        normalized(standard.dictName) === normalized(item.colName) ||
        normalized(standard.dictCode) === normalized(item.colEn)).map(standard => standard.dictCode))];
      if (candidates.length !== 1) return item;
      matched += 1;
      return { ...item, dataStandardId: candidates[0] };
    });
    if (!matched) { message.info('没有找到与数据项名称完全一致的标准'); return; }
    setItemRows(next);
    message.success(`已匹配 ${matched} 个数据项`);
  };
  const field = (label: string, key: string, required = false) =>
    <Form.Item label={label} required={required}><Input value={text(form[key])} disabled={locked}
      onChange={event => patch(key, event.target.value)}/></Form.Item>;
  const dictionary = (label: string, key: string, required = false, multiple = false) =>
    <DictionaryField label={label} name={key} form={form} dicts={dicts}
      required={required} disabled={locked} multiple={multiple}
      onChange={patch}/>;
  const required = (key: string) => publicSafety && publicSafetyRequired.some(([fieldKey]) => fieldKey === key);
  return <>
    <Hero page="catalog-entries" actions={<Button onClick={() => nav('/resource/catalog/entries')}>返回目录列表</Button>}/>
    <div className="ps-composer-toolbar"><span>{existing ? <StateTag state={existing.draft.state}/> : '新建数据目录'}
      <small>{dirty ? '有未保存修改' : loading ? '正在读取目录' : '已保存'}</small></span>
      <Space>
        {!locked && <>
          <Button icon={<SaveOutlined/>} loading={busy} onClick={() => void save().then(() => message.success('目录草稿已保存'))
            .catch(error => message.error(error instanceof Error ? error.message : String(error)))}>保存草稿</Button>
          <Button type="primary" icon={<SendOutlined/>} loading={busy} onClick={() => void submit()}>提交审核</Button>
        </>}
        {existing?.draft.state === 'APPROVED' && <Button type="primary" onClick={() =>
          modal.confirm({ title: '重新编目这份目录？', content: '目录将转为待提交审核，原目录信息与数据项会保留。',
            onOk: async () => { await act(`catalogs/${existing.id}/new-version`, {}, existing.version); message.success('可以重新编目'); } })}>
          重新编目</Button>}
        {existing?.draft.state === 'PENDING_REVIEW' && existing.draft.submittedBy === getSession()?.principalId &&
          <Button onClick={() => void act(`catalogs/${existing.id}/withdraw`, {}, existing.version)
            .then(() => message.success('已撤回审核')).catch(error => message.error(String(error)))}>撤回提交</Button>}
      </Space>
    </div>
    <div className="ps-catalog-form">
      <div className="ps-catalog-form-header">
        <div><h2>编目数据表</h2><p>选择数仓规划中的数据源与表，按元数据管理的目录字段编制。</p></div>
        <Space>{publicSafety && <Button disabled={locked} onClick={fillDefaults}>快捷填充</Button>}
          <Button icon={<FileSearchOutlined/>} disabled={locked} onClick={() => setPickerOpen(true)}>
            {formValue(form, 'sourceTableId') ? '更换数据表' : '选择数据表'}</Button></Space>
      </div>
      {formValue(form, 'sourceTableId') && <div className="ps-catalog-selected-table">
        <NameCell name={formValue(form, 'sourceTableName') || '已选数据表'}
          code={sourceName(data.sources, text(form.dbId))}/>
        <span>{items.length} 个数据项</span>
      </div>}
      <Form layout="vertical" className="ps-form" aria-busy={loading}>
        <h3 className="ps-section-title">基本要素</h3>
        <div className="ps-form-two">
          {field('数据资源名称', 'catalogName', true)}
          {field('信息资源英文名', 'catalogNameEn')}
          {dictionary('数据目录类型', 'catalogType')}
          {dictionary('共享属性', 'shareType', required('shareType'))}
          {dictionary('是否对外开放', 'openType', required('openType'))}
          {dictionary('数据资源分类', 'dataSourceType', required('dataSourceType'))}
          {dictionary('数据表类型', 'tableType', required('tableType'))}
          <Form.Item label="来源部门" required><Select disabled={locked} showSearch optionFilterProp="label"
            value={formValue(form, 'orgId')} onChange={value => patch('orgId', value)}
            options={data.organizations.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
          <Form.Item label="登记记录数" required={required('recordCount')}><InputNumber disabled={locked} min={0}
            value={form.recordCount === '' || form.recordCount == null ? undefined : Number(form.recordCount)}
            style={{ width: '100%' }} onChange={value => patch('recordCount', value ?? '')}/></Form.Item>
        </div>
        <Form.Item label="数据资源描述" required={required('assetDesc')}><Input.TextArea disabled={locked} maxLength={500} rows={3}
          value={text(form.assetDesc)} onChange={event => patch('assetDesc', event.target.value)}/></Form.Item>
        <h3 className="ps-section-title">业务与扩展要素</h3>
        <div className="ps-form-two">
          {dictionary('更新周期', 'updateCycle', required('updateCycle'))}
          {dictionary('数据范围', 'dataRegionScope', required('dataRegionScope'))}
          {dictionary('是否回流', 'isBackflow')}
          {dictionary('是否条线上传', 'isOnline')}
          {dictionary('数据资源来源类型', 'resourceSource', required('resourceSource'))}
          {dictionary('数据主题', 'dataTheme', required('dataTheme'), true)}
          {dictionary('警种分类', 'policeType', required('policeType'))}
          {dictionary('数据分级分类', 'dataLevel', required('dataLevel'))}
          {dictionary('数据资源位置', 'storageLocation')}
        </div>
        <h3 className="ps-section-title">管理信息</h3>
        <div className="ps-form-two">
          <Form.Item label="事权单位" required={required('manageUnit')}><Select disabled={locked} allowClear showSearch optionFilterProp="label"
            value={formValue(form, 'manageUnit')} onChange={value => patch('manageUnit', value || '')}
            options={data.organizations.map(item => ({ value: item.value, label: item.label }))}/></Form.Item>
          {field('事权单位联系人', 'concatName', required('concatName'))}
          {dictionary('表的分级', 'tableLevel', required('tableLevel'))}
          {field('事权单位联系方式', 'concatPhone', required('concatPhone'))}
        </div>
      </Form>
      <div className="ps-catalog-form-header"><div><h2>目录数据项</h2>
        <p>从已采集的表字段生成，可调整业务名称、类型、标准和码表。</p></div>
        {!locked && <Space><Button onClick={matchStandards}>匹配同名标准</Button>
          <Button icon={<SyncOutlined/>} disabled={!formValue(form, 'sourceTableId')} onClick={syncFields}>从表同步</Button>
          <Button icon={<PlusOutlined/>} onClick={() => setItemRows([...items, { colName: '', colEn: '', colType: 'VARCHAR' }])}>新增数据项</Button></Space>}
      </div>
      <CatalogItemEditor items={items} dicts={dicts} standards={data.standards} disabled={locked} onChange={setItemRows}/>
    </div>
    <TablePicker open={pickerOpen} onClose={() => setPickerOpen(false)} onChoose={chooseTable}/>
  </>;
}

export function CatalogReviewsPage() {
  const { data, act, refresh } = useResource(), { message } = App.useApp();
  const [filter, setFilter] = useState('PENDING_REVIEW');
  const [entryId, setEntryId] = useState('');
  const [items, setItems] = useState<DirectoryItem[]>([]);
  const [opinion, setOpinion] = useState('');
  const [busy, setBusy] = useState(false);
  const entry = data.state.catalogs.find(item => item.id === entryId);
  const selfSubmitted = !!entry && entry.draft.submittedBy === getSession()?.principalId;
  useEffect(() => {
    if (!entryId) return;
    let active = true;
    void loadDirectory(entryId).then(result => { if (active) setItems(result.items); })
      .catch(error => { if (active) message.error(error instanceof Error ? error.message : String(error)); });
    return () => { active = false; };
  }, [entryId]);
  const decide = async (approve: boolean) => {
    if (!entry) return;
    if (!approve && !opinion.trim()) { message.warning('退回时请填写原因'); return; }
    setBusy(true);
    try {
      await act(`catalogs/${entry.id}/review`, { approve, opinion }, entry.version);
      message.success(approve ? '目录审核已通过' : '目录已退回');
      setEntryId('');
    } catch (error) { message.error(error instanceof Error ? error.message : String(error)); }
    finally { setBusy(false); }
  };
  return <>
    <Hero page="catalog-reviews"/>
    <PTable rows={data.state.catalogs.filter(item => !filter || catalogState(item) === filter)}
      onRefresh={refresh} emptyTitle={filter === 'PENDING_REVIEW' ? '暂无待审核目录' : '暂无符合条件的目录'}
      emptyDescription="目录提交审核后，会在此按目录主表的审核字段显示。"
      searchText={item => item.draft.name + ' ' + item.draft.code}
      tools={<Select value={filter} onChange={setFilter} options={[
        { value: 'PENDING_REVIEW', label: '待审核' }, { value: 'APPROVED', label: '审核通过' },
        { value: 'RETURNED', label: '已退回' }, { value: '', label: '全部目录' },
      ]}/>}
      columns={[
        { title: '目录名称', width: 240, render: (_, item) => <NameCell name={item.draft.name} code={item.draft.code}/> },
        { title: '关联数据表', width: 220, render: (_, item) =>
          text((item.draft as unknown as Record<string, unknown>).resourceName) || item.draft.resourceId },
        { title: '数据项', width: 100, render: (_, item) => `${item.draft.fieldIds.length} 项` },
        { title: '审核状态', width: 120, render: (_, item) => <StateTag state={item.draft.state}/> },
        { title: '更新时间', width: 170, render: (_, item) => timeText(item.updatedAt) },
        { title: '操作', width: 95, fixed: 'right', align: 'center', className: 'wx-action-column', render: (_, item) =>
          <Button type="link" onClick={() => { setEntryId(item.id); setOpinion(''); setItems([]); }}>
            {item.draft.state === 'PENDING_REVIEW' ? '审核' : '查看'}</Button> },
      ]}/>
    <Drawer open={!!entry} width={980} title="目录审核" onClose={() => setEntryId('')}
      footer={entry?.draft.state === 'PENDING_REVIEW' && <div className="ps-dialog-footer">
        <span>{selfSubmitted ? '请由其他审核人员处理' : '请核对目录信息和数据项'}</span>
        <Space><Button danger disabled={selfSubmitted} loading={busy} onClick={() => void decide(false)}>退回</Button>
          <Button type="primary" disabled={selfSubmitted} loading={busy} onClick={() => void decide(true)}>审核通过</Button></Space>
      </div>}>
      {entry && <>
        <div className="ps-catalog-review-summary"><h2>{entry.draft.name}</h2><StateTag state={entry.draft.state}/></div>
        <p className="ps-muted">{entry.draft.code} · {text((entry.draft as unknown as Record<string, unknown>).resourceName)}</p>
        <p>{entry.draft.summary || '暂无目录描述'}</p>
        <h3 className="ps-section-title">目录数据项</h3>
        <Table rowKey={(item, index) => item.tid || item.sourceTableColumnId || String(index)} size="small"
          dataSource={items} pagination={{ pageSize: 10 }} scroll={{ x: 800 }} columns={[
            { title: '名称', dataIndex: 'colName', width: 190 },
            { title: '英文名', dataIndex: 'colEn', width: 170 },
            { title: '类型', dataIndex: 'colType', width: 130 },
            { title: '长度', dataIndex: 'colLength', width: 80 },
            { title: '数据标准', dataIndex: 'dataStandardId', width: 160,
              render: value => text(data.standards.find(item => text(item.id) === text(value))?.name) || text(value) || '—' },
          ]}/>
        {entry.draft.state === 'PENDING_REVIEW' && <Form.Item label="审核意见">
          <Input.TextArea value={opinion} maxLength={1000} showCount rows={3}
            onChange={event => setOpinion(event.target.value)} placeholder="通过可选填；退回请说明原因"/>
        </Form.Item>}
      </>}
    </Drawer>
  </>;
}
