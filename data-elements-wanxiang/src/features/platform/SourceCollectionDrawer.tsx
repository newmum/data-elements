import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Drawer, Progress, Select, Space, Tabs } from 'antd';
import type { Row } from '../../services/api';
import {
  addedSourceTablesPage, collectTableFields, collectionStatus, missingSourceTablesPage,
  sourceTablesPage, startCollection, type CollectionJob, type SourceTablePage,
} from '../../services/datasources';
import { DataTable, Status, date } from './common';
import { objectKindLabel } from '../../shared/sourcePresentation';

const emptyPage: SourceTablePage = { rows: [], total: 0, page: 1, size: 15 };
const terminal = (job: CollectionJob) => ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(job.status);
const missingIdentityFunction = (error: unknown) => String(error instanceof Error ? error.message : error).includes('找不到对应函数 [/idaas/receiver-account-check]');
const fieldFailureText = (error: unknown) => missingIdentityFunction(error) ? '身份校验服务暂不可用，字段采集未执行；请稍后重试' : error instanceof Error ? error.message : String(error);

async function collectFields(tableId: string, force: boolean) {
  for (let attempt = 0; ; attempt++) {
    try { return await collectTableFields(tableId, force); }
    catch (error) {
      if (!missingIdentityFunction(error) || attempt >= 2) throw error;
      await new Promise(resolve => setTimeout(resolve, (attempt + 1) * 1000));
    }
  }
}

type FieldMode = 'none' | 'new' | 'all';
type TableView = 'added' | 'missing' | 'saved';

export function SourceCollectionDrawer({ source, onClose, onComplete }: { source: Row | null; onClose: () => void; onComplete: () => void }) {
  const [job, setJob] = useState<CollectionJob | null>(null);
  const [saved, setSaved] = useState<SourceTablePage>(emptyPage), [savedTotal, setSavedTotal] = useState(0);
  const [savedPage, setSavedPage] = useState(1), [savedSize, setSavedSize] = useState(15), [keyword, setKeyword] = useState('');
  const [savedLoading, setSavedLoading] = useState(false), [savedError, setSavedError] = useState('');
  const [tableView, setTableView] = useState<TableView>('saved'), [changes, setChanges] = useState<SourceTablePage>(emptyPage);
  const [changePage, setChangePage] = useState(1), [changeSize, setChangeSize] = useState(15);
  const [changeLoading, setChangeLoading] = useState(false), [changeError, setChangeError] = useState('');
  const [fieldMode, setFieldMode] = useState<FieldMode>('none');
  const [fieldProgress, setFieldProgress] = useState<{ done: number; total: number; failures: string[] } | null>(null);
  const [preRun, setPreRun] = useState<{ saved: number; exploredAt?: string } | null>(null);
  const [running, setRunning] = useState(false), [error, setError] = useState(''), [revision, setRevision] = useState(0);
  const sequence = useRef(0), lock = useRef(false), mounted = useRef(true);
  const { message } = App.useApp();
  const finish = useRef(onComplete); finish.current = onComplete;

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; sequence.current++; }; }, []);
  useEffect(() => {
    const current = ++sequence.current;
    setSaved(emptyPage); setSavedTotal(0); setJob(null); setError(''); setFieldProgress(null); setPreRun(null);
    setSavedPage(1); setSavedSize(15); setKeyword(''); setChangePage(1); setTableView('saved'); setFieldMode('none');
    if (!source) return;
    void collectionStatus(source.id).then(state => { if (current === sequence.current) { setJob(state.exists ? state : null); if (state.status === 'SUCCEEDED') setTableView('added'); } })
      .catch(cause => { if (current === sequence.current) setError(cause instanceof Error ? cause.message : String(cause)); });
  }, [source?.id]);

  useEffect(() => {
    if (!source) return;
    let live = true;
    setSavedLoading(true); setSavedError('');
    void sourceTablesPage(source.id, savedPage, savedSize, keyword).then(page => {
      if (!live) return;
      setSaved(page);
      if (!keyword) setSavedTotal(page.total);
    }).catch(cause => { if (live) setSavedError(cause instanceof Error ? cause.message : String(cause)); })
      .finally(() => { if (live) setSavedLoading(false); });
    return () => { live = false; };
  }, [source?.id, savedPage, savedSize, keyword, revision]);

  useEffect(() => {
    if (!source || job?.status !== 'SUCCEEDED' || tableView === 'saved') { setChanges(emptyPage); return; }
    let live = true;
    setChangeLoading(true); setChangeError('');
    const load = tableView === 'added' ? addedSourceTablesPage : missingSourceTablesPage;
    void load(source.id, changePage, changeSize).then(page => { if (live) setChanges(page); })
      .catch(cause => { if (live) setChangeError(cause instanceof Error ? cause.message : String(cause)); })
      .finally(() => { if (live) setChangeLoading(false); });
    return () => { live = false; };
  }, [source?.id, job?.jobId, job?.status, tableView, changePage, changeSize, revision]);

  // Reopening an active task follows its durable status instead of starting a second scan.
  useEffect(() => {
    if (!source || !job || terminal(job) || running) return;
    const current = sequence.current;
    const timer = setTimeout(() => {
      void collectionStatus(source.id, job.jobId).then(next => {
        if (current !== sequence.current) return;
        setJob(next);
        if (terminal(next)) { if (next.status === 'SUCCEEDED') setTableView('added'); setRevision(value => value + 1); finish.current(); }
      }).catch(cause => { if (current === sequence.current) setError(cause instanceof Error ? cause.message : String(cause)); });
    }, 1500);
    return () => clearTimeout(timer);
  }, [source?.id, job, running]);

  const collect = async () => {
    if (!source || lock.current) return;
    lock.current = true; setRunning(true); setError(''); setFieldProgress(null); setTableView('saved');
    const id = source.id, current = sequence.current;
    const valid = () => mounted.current && current === sequence.current;
    setPreRun({ saved: savedTotal, exploredAt: job?.status === 'SUCCEEDED' ? job.finishedAt : job?.previousSuccessfulAt });
    try {
      let state = await startCollection(id, savedTotal > 0 || Number(source.entityCount || 0) > 0);
      if (valid()) setJob(state);
      while (!terminal(state)) {
        if (!valid()) return;
        await new Promise(resolve => setTimeout(resolve, 1500));
        state = await collectionStatus(id, state.jobId);
        if (valid()) setJob(state);
      }
      if (state.status !== 'SUCCEEDED') throw new Error(state.error || '表清单探查失败，请检查数据源连接');
      setTableView('added'); setRevision(value => value + 1);
      if (fieldMode !== 'none') {
        const load = fieldMode === 'all' ? sourceTablesPage : addedSourceTablesPage;
        let pageNo = 1, done = 0, total = 0;
        const failures: string[] = [];
        do {
          if (!valid()) return;
          const batch = await load(id, pageNo, 50);
          if (pageNo === 1) total = batch.total;
          if (!batch.rows.length) break;
          let next = 0;
          const update = () => { if (valid()) setFieldProgress({ done, total, failures: [...failures] }); };
          update();
          const worker = async () => {
            while (valid() && next < batch.rows.length) {
              const table = batch.rows[next++];
              try { await collectFields(table.id, true); }
              catch (cause) { failures.push(table.name + '：' + fieldFailureText(cause)); }
              done++; update();
            }
          };
          await Promise.all(Array.from({ length: Math.min(3, batch.rows.length) }, worker));
          pageNo++;
        } while (done < total);
        if (failures.length) message.warning('表清单已更新，部分字段同步失败，请查看详情');
        else message.success('表清单和所选范围的字段已同步');
      } else message.success('表清单探查完成，字段未自动读取');
      if (valid()) { setRevision(value => value + 1); finish.current(); }
    } catch (cause) { if (valid()) setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { lock.current = false; if (valid()) setRunning(false); }
  };

  const active = running || Boolean(job && !terminal(job));
  const previousTime = preRun?.exploredAt || (job?.status === 'SUCCEEDED' ? job.finishedAt : job?.previousSuccessfulAt);
  const beforeCount = preRun?.saved ?? (job?.status === 'SUCCEEDED' ? job.savedBeforeCount : savedTotal);
  const discovered = job?.status === 'SUCCEEDED' || Number(job?.totalCount || 0) > 0 ? job?.totalCount ?? '—' : job && !terminal(job) ? '探查中' : '—';
  const hasSaved = savedTotal > 0 || Number(source?.entityCount || 0) > 0;
  const activeView: TableView = job?.status === 'SUCCEEDED' ? tableView : 'saved';
  const activePage = activeView === 'saved' ? saved : changes;
  const activePageNumber = activeView === 'saved' ? savedPage : changePage;
  const activePageSize = activeView === 'saved' ? savedSize : changeSize;
  const tableNote = activeView === 'added'
    ? '本次首次发现并写入平台的表，也包含在“平台保留”中。'
    : activeView === 'missing'
      ? '这些旧表仍保留在平台，但本次源库未按名称匹配到；可能是改名、删除或权限变化，需人工核对。'
      : '平台保留的完整表清单，包含本次新增和尚未核对的旧表；不等同于源库当前可见表。';
  return <Drawer className="wx-source-collection-drawer" open={!!source} width={850} title={'数据表与视图 · ' + (source?.name || '')}
    onClose={() => { if (running) { message.info('字段同步进行中，请等待完成后关闭'); return; } onClose(); }} maskClosable={!running}
    extra={<Button type="primary" loading={running} disabled={!source || source.status === 'DISABLED' || savedLoading || Boolean(savedError) || Boolean(job && !terminal(job))} onClick={collect}>{hasSaved ? '重新探查表清单' : '探查表清单'}</Button>}>
    {error && <Alert className="mb16" type="error" showIcon title="探查或读取失败" description={error} action={<Button onClick={() => { setError(''); if (source) void collectionStatus(source.id, job?.jobId).then(setJob).catch(cause => setError(String(cause))); }}>刷新进度</Button>}/>}
    <div className="wx-collection-metrics">
      <div><small>平台当前保留</small><strong>{savedTotal}</strong><span>张表 / 视图，含待核对旧表</span></div>
      <div><small>上次成功探查</small><strong className="metric-date">{date(previousTime)}</strong><span>采集前保存 {beforeCount ?? '—'} 张</span></div>
      <div><small>本次探查发现</small><strong>{discovered}</strong><span>源库可见表 / 视图</span></div>
    </div>
    <div className="wx-collection-mode"><span>字段同步范围</span><Select value={fieldMode} disabled={active} onChange={value => setFieldMode(value as FieldMode)} style={{ width: 194 }} options={[{ value: 'none', label: '仅探查表清单' }, { value: 'new', label: '同步本次新增表字段' }, { value: 'all', label: '同步全部表字段' }]}/><small>{fieldMode === 'all' ? `需要逐表访问源库；当前 ${savedTotal} 张表可能耗时较长。` : fieldMode === 'new' ? '仅新增表逐表读取字段；现有表字段变化仍需单独核查。' : '本次不逐表读取字段，无法判断字段名或类型是否变化。'}</small></div>
    {job && <div className="wx-platform-collection-summary"><Space><Status value={fieldProgress && running ? 'RUNNING' : job.status}/><span>{fieldProgress ? '字段同步' : '表清单探查'}</span></Space><Progress percent={fieldProgress ? Math.round(fieldProgress.done / Math.max(1, fieldProgress.total) * 100) : Number(job.progressPercent || 0)} status={error || fieldProgress?.failures.length ? 'exception' : active ? 'active' : 'normal'}/>{job.error && <Alert type="error" title={job.error}/>}{fieldProgress?.failures.length ? <Alert showIcon type="warning" title={fieldProgress.failures.length + ' 张表的字段同步失败'} description={<div>{fieldProgress.failures.slice(0, 20).map(value => <p key={value}>{value}</p>)}</div>}/> : null}</div>}
    <section className="wx-collection-differences wx-collection-table-panel">
      <header><strong>表 / 视图清单</strong>{job?.status === 'SUCCEEDED' && <span>本次新增 {job.addedCount ?? 0} · 旧表待核对 {job.deletedCount ?? 0} · 同名匹配 {job.unchangedCount ?? 0}</span>}</header>
      <Tabs activeKey={activeView} onChange={key => { setTableView(key as TableView); setChangePage(1); setChanges(emptyPage); }} items={job?.status === 'SUCCEEDED' ? [
        { key: 'added', label: `本次新增 ${job.addedCount ?? 0}` },
        { key: 'missing', label: `旧表待核对 ${job.deletedCount ?? 0}` },
        { key: 'saved', label: `平台保留 ${savedTotal}` },
      ] : [{ key: 'saved', label: `平台保留 ${savedTotal}` }]}/>
      <p className="wx-collection-tab-note">{tableNote}</p>
      <DataTable rows={activePage.rows} loading={activeView === 'saved' ? savedLoading : changeLoading} error={activeView === 'saved' ? savedError : changeError} titleKey="source-collection-tables" showSearch={activeView === 'saved'} showExport={false} onSearch={value => { setKeyword(value); setSavedPage(1); }} searchPlaceholder="按表名或说明查询平台保留记录" serverPagination={{ page: activePageNumber, size: activePageSize, total: activePage.total, onChange: (page, size) => { if (activeView === 'saved') { setSavedPage(page); setSavedSize(size); } else { setChangePage(page); setChangeSize(size); } } }} columns={[{ title: '表 / 视图', dataIndex: 'displayName', render: (_, row) => <div className="wx-cell-stack"><b>{row.displayName || row.name}</b><small>{row.name}</small></div> }, { title: '类型', dataIndex: 'kind', render: objectKindLabel }, { title: '已采集字段', dataIndex: 'fieldCount' }]}/>
    </section>
  </Drawer>;
}
