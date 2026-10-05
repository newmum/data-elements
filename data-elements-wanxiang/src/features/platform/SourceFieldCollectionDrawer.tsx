import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Drawer, Input, Progress, Space, Table, Tag } from 'antd';
import type { Row } from '../../services/api';
import { collectTableFields, sourceTablesPage, type SourceTablePage } from '../../services/datasources';
import { catalogTableColumns, invalidateMetadata } from '../../services/metadata';
import { diffFieldSnapshots, type FieldSnapshotDiff } from '../../services/fieldSnapshotDiff';
import { objectKindLabel } from '../../shared/sourcePresentation';

type TableResult = { status: 'done' | 'failed'; diff?: FieldSnapshotDiff; error?: string; snapshotBefore?: Row[] };
const emptyPage: SourceTablePage = { rows: [], total: 0, page: 1, size: 15 };
const errorText = (cause: unknown) => {
  const text = cause instanceof Error ? cause.message : String(cause);
  return text.includes('找不到对应函数 [/idaas/receiver-account-check]') ? '身份校验服务暂不可用，字段采集未完成' : text;
};

export function SourceFieldCollectionDrawer({ source, onClose, onComplete }: { source: Row | null; onClose: () => void; onComplete: () => void }) {
  const [page, setPage] = useState<SourceTablePage>(emptyPage);
  const [savedTotal, setSavedTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1), [pageSize, setPageSize] = useState(15), [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false), [loadError, setLoadError] = useState(''), [revision, setRevision] = useState(0);
  const [results, setResults] = useState<Record<string, TableResult>>({});
  const [progress, setProgress] = useState({ done: 0, total: 0 }), [running, setRunning] = useState(false);
  const [runError, setRunError] = useState(''), [selected, setSelected] = useState<Row | null>(null);
  const [currentFields, setCurrentFields] = useState<Row[]>([]), [fieldsLoading, setFieldsLoading] = useState(false), [fieldsError, setFieldsError] = useState('');
  const stop = useRef(false), lock = useRef(false), generation = useRef(0), resultRef = useRef<Record<string, TableResult>>({});
  const { message } = App.useApp();

  useEffect(() => {
    generation.current++; stop.current = true; resultRef.current = {};
    setResults({}); setProgress({ done: 0, total: 0 }); setRunError(''); setSelected(null);
    setPageNo(1); setPageSize(15); setKeyword(''); setPage(emptyPage); setSavedTotal(0);
  }, [source?.id]);

  useEffect(() => {
    if (!source) return;
    let live = true;
    setLoading(true); setLoadError('');
    void sourceTablesPage(String(source.id), pageNo, pageSize, keyword).then(value => { if (live) { setPage(value); if (!keyword) setSavedTotal(value.total); } })
      .catch(cause => { if (live) setLoadError(errorText(cause)); })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [source?.id, pageNo, pageSize, keyword, revision]);

  useEffect(() => {
    if (!selected) { setCurrentFields([]); setFieldsError(''); return; }
    let live = true;
    setCurrentFields([]); setFieldsLoading(true); setFieldsError('');
    void catalogTableColumns(selected.id).then(rows => { if (live) setCurrentFields(rows); })
      .catch(cause => { if (live) setFieldsError(errorText(cause)); })
      .finally(() => { if (live) setFieldsLoading(false); });
    return () => { live = false; };
  }, [selected?.id, results[selected?.id || '']?.status]);

  const collect = async () => {
    if (!source || lock.current || !savedTotal) return;
    lock.current = true; stop.current = false; setRunning(true); setRunError('');
    const current = generation.current, sourceId = String(source.id);
    const valid = () => generation.current === current;
    let done = Object.keys(resultRef.current).length, total = savedTotal;
    setProgress({ done, total });
    try {
      let batchPage = 1;
      while (!stop.current && valid()) {
        // The saved-table endpoint is backend-paged; never load all table/field rows into the browser.
        const batch = await sourceTablesPage(sourceId, batchPage, 30);
        total = batch.total;
        if (!batch.rows.length) break;
        let next = 0;
        const worker = async () => {
          while (!stop.current && valid() && next < batch.rows.length) {
            const table = batch.rows[next++];
            if (resultRef.current[table.id]?.status === 'done') continue;
            let result: TableResult;
            let before = resultRef.current[table.id]?.snapshotBefore;
            try {
              before ??= await catalogTableColumns(table.id);
              await collectTableFields(table.id, true);
              const after = await catalogTableColumns(table.id);
              result = { status: 'done', diff: diffFieldSnapshots(before, after) };
            } catch (cause) { result = { status: 'failed', error: errorText(cause), snapshotBefore: before }; }
            if (!valid()) return;
            resultRef.current[table.id] = result;
            done = Object.keys(resultRef.current).length; setProgress({ done, total });
            if (done % 10 === 0 || done === total) setResults({ ...resultRef.current });
          }
        };
        await Promise.all([worker(), worker()]);
        if (valid()) setResults({ ...resultRef.current });
        if (batchPage * 30 >= total) break;
        batchPage++;
      }
      if (valid()) {
        setResults({ ...resultRef.current }); invalidateMetadata(); setRevision(value => value + 1); onComplete();
        if (stop.current) message.info(`已停止，已处理 ${done} / ${total} 张表；再次点击可继续未完成的表`);
        else if (Object.values(resultRef.current).some(result => result.status === 'failed')) message.warning('字段采集已结束，部分表失败；请查看表格中的失败原因');
        else message.success('已保存表的字段采集完成');
      }
    } catch (cause) { if (valid()) setRunError(errorText(cause)); }
    finally { lock.current = false; if (valid()) setRunning(false); }
  };

  const values = Object.values(results), failures = values.filter(result => result.status === 'failed').length;
  const changed = values.filter(result => result.diff && (result.diff.added.length || result.diff.removed.length || result.diff.modified.length)).length;
  const added = values.reduce((n, result) => n + (result.diff?.added.length || 0), 0);
  const removed = values.reduce((n, result) => n + (result.diff?.removed.length || 0), 0);
  const modified = values.reduce((n, result) => n + (result.diff?.modified.length || 0), 0);
  const selectedResult = selected && results[selected.id];
  const completedSuccessfully = savedTotal > 0 && values.length >= savedTotal && failures === 0;
  return <Drawer className="wx-field-collection-drawer" open={!!source} width={900} title={'采集字段 · ' + (source?.name || '')} onClose={() => { if (running) { stop.current = true; message.info('已请求停止，当前正在处理的表完成后即可关闭'); } else onClose(); }}
    extra={<Space><Button disabled={!running} onClick={() => { stop.current = true; }}>停止</Button><Button type="primary" loading={running} disabled={!source || source.status === 'DISABLED' || !savedTotal || !!loadError || completedSuccessfully} onClick={collect}>{progress.done ? '继续采集未完成或失败表' : '采集全部已保存表字段'}</Button></Space>}>
    <p className="wx-field-collection-intro">以平台已保存的表 / 视图为范围逐表读取最新字段。每张表更新前后对比平台字段快照；本操作不重新探查表清单。为控制源库负载，最多同时处理两张表；表较多时可停止并继续。</p>
    <div className="wx-field-collection-metrics">
      <div><small>平台已保存表 / 视图</small><strong>{savedTotal}</strong><span>后端分页读取</span></div>
      <div><small>本次已处理</small><strong>{progress.done}</strong><span>字段有变化的表 {changed}</span></div>
      <div><small>字段差异</small><strong>{added + removed + modified}</strong><span>新增 {added} · 不再存在 {removed} · 属性变化 {modified}</span></div>
      <div><small>采集失败</small><strong>{failures}</strong><span>可查看原因并重试</span></div>
    </div>
    {running && <Progress percent={Math.round(progress.done / Math.max(progress.total, 1) * 100)} status="active" format={() => `${progress.done} / ${progress.total}`}/>}
    {runError && <Alert type="error" showIcon title="字段采集中断" description={runError} className="mb16"/>}
    {loadError && <Alert type="error" showIcon title="读取已保存表失败" description={loadError} action={<Button onClick={() => setRevision(n => n + 1)}>重试</Button>} className="mb16"/>}
    <div className="wx-field-collection-toolbar"><strong>已保存表的字段情况</strong><Input.Search allowClear placeholder="搜索表名或说明" onSearch={value => { setKeyword(value.trim()); setPageNo(1); }} style={{ width: 260 }}/></div>
    <Table<Row> rowKey="id" size="middle" loading={loading} dataSource={page.rows} scroll={{ x: 730 }} pagination={{ current: pageNo, pageSize, total: page.total, showSizeChanger: true, pageSizeOptions: [15, 30, 50], showTotal: count => `共 ${count} 张表`, onChange: (next, size) => { setPageNo(size !== pageSize ? 1 : next); setPageSize(size); } }} columns={[
      { title: '表 / 视图', dataIndex: 'displayName', sorter: (a,b) => String(a.displayName || a.name || '').localeCompare(String(b.displayName || b.name || ''),'zh-CN',{numeric:true}), showSorterTooltip: { title: '仅对当前页排序' }, render: (_, row) => <span className="wx-cell-stack"><b>{row.displayName || row.name}</b><small>{row.name}</small></span> },
      { title: '类型', dataIndex: 'kind', width: 90, render: objectKindLabel },
      { title: '已存字段', dataIndex: 'fieldCount', width: 95, align: 'right' },
      { title: '本次结果', width: 205, render: (_, row) => { const result = results[row.id]; if (!result) return <span className="muted">待采集</span>; if (result.status === 'failed') return <Tag color="error">失败</Tag>; const diff = result.diff!; return diff.added.length || diff.removed.length || diff.modified.length ? <Space size={3}><Tag color="success">+{diff.added.length}</Tag><Tag color="warning">−{diff.removed.length}</Tag><Tag color="processing">变更 {diff.modified.length}</Tag></Space> : <Tag color="default">无变化</Tag>; } },
      { title: '详情', width: 80, render: (_, row) => results[row.id] ? <Button type="link" onClick={() => setSelected(row)}>查看</Button> : '—' },
    ]}/>
    <p className="wx-field-collection-note">字段名变化会显示为“旧字段不再存在 + 新字段”，无法仅凭名称判定为重命名。失败的表未计入字段差异；再次采集可重试。</p>
    <Drawer open={!!selected} width={620} title={(selected?.displayName || selected?.name || '') + ' · 字段变化'} onClose={() => setSelected(null)}>
      {selectedResult?.status === 'failed' ? <Alert type="error" showIcon title="字段采集失败" description={selectedResult.error}/> : selectedResult?.diff && <>
        <p>采集前 {selectedResult.diff.beforeCount} 个字段，采集后 {selectedResult.diff.afterCount} 个字段。</p>
        {([['新增字段', selectedResult.diff.added], ['不再存在', selectedResult.diff.removed], ['属性变化', selectedResult.diff.modified]] as const).map(([title, items]) => <section className="wx-field-diff-section" key={title}><h3>{title} · {items.length}</h3>{items.length ? items.map(item => <div key={item.name}><b>{item.name}</b>{item.changed?.length ? item.changed.map(change => <span key={change.label}>{change.label}：{change.before || '空'} → {change.after || '空'}</span>) : <small>{String(item.after?.column_type || item.after?.data_type || item.before?.column_type || item.before?.data_type || '—')}</small>}</div>) : <p className="muted">无</p>}</section>)}
      </>}
      <section className="wx-field-current-section"><h3>当前保存的字段 · {currentFields.length}</h3>{fieldsError && <Alert type="error" showIcon title="读取当前字段失败" description={fieldsError}/>}<Table<Row> rowKey={row => String(row.tid || row.column_name)} size="small" loading={fieldsLoading} dataSource={currentFields} pagination={{ pageSize: 15, showSizeChanger: false }} columns={[{ title: '字段名', dataIndex: 'column_name', sorter: (a:Row,b:Row) => String(a.column_name||'').localeCompare(String(b.column_name||''),'zh-CN',{numeric:true}) }, { title: '类型', render: (_, row) => row.column_type || row.data_type || '—' }, { title: '可空', render: (_, row) => row.nullable == null ? '未知' : ['0', 0, false].includes(row.nullable) ? '否' : '是' }, { title: '说明', dataIndex: 'column_comment' }]}/></section>
    </Drawer>
  </Drawer>;
}
