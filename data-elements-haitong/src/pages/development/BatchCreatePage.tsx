import { Alert, App, Button, Checkbox, Form, Input, Select, Space, Tag } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHero from '../../components/PageHero';
import { DataTable, Panel, StateTag, formatTime } from '../../components/Common';
import { batchPlatformService, type BatchDatasource, type BatchTable, type BatchTarget } from '../../api/batchPlatformService';
import { batchJobService, type BatchCreateRequest, type BatchJob, type BatchJobDetail, type BatchJobItem } from '../../api/batchJobService';

type Match = { id: string; source: BatchTable; target?: BatchTarget; targetName: string; reason: string; eligible: boolean };
const idOf = (row: Record<string, unknown>) => String(row.tid ?? row.datasourceId ?? row.value ?? '');
const sourceName = (row: BatchTable) => String(row.tableName ?? '');
const sourceLabel = (row: BatchTable) => String(row.tableNameCn ?? row.tableName ?? '');
const targetId = (row: BatchTarget) => String(row.targetTableId ?? row.target_table_id ?? '');
const targetDbId = (row: BatchTarget) => String(row.targetDbId ?? row.target_db_id ?? '');
const targetName = (row: BatchTarget) => String(row.targetTableName ?? row.target_table_name ?? '');
const errorText = (error: unknown) => error instanceof Error ? error.message : String(error);
const countOf = (items: BatchJobItem[], status: BatchJobItem['status']) => items.filter(item => item.status === status).length;

export default function BatchCreatePage() {
  const { message } = App.useApp(), navigate = useNavigate();
  const mounted = useRef(true), running = useRef(false), stopRequested = useRef(false);
  const pendingSubmit = useRef<{ fingerprint: string; requestKey: string }>();
  const [sources, setSources] = useState<BatchDatasource[]>([]), [targets, setTargets] = useState<BatchDatasource[]>([]), [tables, setTables] = useState<BatchTable[]>([]);
  const [source, setSource] = useState(''), [target, setTarget] = useState(''), [prefix, setPrefix] = useState('dwd_'), [remove, setRemove] = useState(''), [suffix, setSuffix] = useState('');
  const [selectedMatches, setSelectedMatches] = useState<Map<string, Match>>(() => new Map());
  const [sourcePage, setSourcePage] = useState(1), [sourceTotal, setSourceTotal] = useState(0);
  const [jobs, setJobs] = useState<BatchJob[]>([]), [activeJobId, setActiveJobId] = useState(''), [jobDetail, setJobDetail] = useState<BatchJobDetail>();
  const [executingJobId, setExecutingJobId] = useState('');
  const [loadingOptions, setLoadingOptions] = useState(true), [loadingTables, setLoadingTables] = useState(false), [loadingJobs, setLoadingJobs] = useState(true), [submitting, setSubmitting] = useState(false), [loadError, setLoadError] = useState('');

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const refreshJobs = useCallback(async (id?: string) => {
    const rows = await batchJobService.list();
    if (!mounted.current) return;
    setJobs(rows);
    const chosen = id || activeJobId || rows[0]?.jobId || '';
    setActiveJobId(chosen);
    if (chosen) {
      const nextDetail = await batchJobService.detail(chosen);
      if (mounted.current) setJobDetail(nextDetail);
    }
    else setJobDetail(undefined);
  }, [activeJobId]);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoadingOptions(true); setLoadError('');
      const [sourceResult, targetResult] = await Promise.allSettled([batchPlatformService.sourceDatabases(), batchPlatformService.targetDatabases()]);
      if (cancelled) return;
      if (sourceResult.status === 'rejected') { setLoadError(errorText(sourceResult.reason)); setLoadingOptions(false); return; }
      const sourceRows = sourceResult.value.filter(row => idOf(row));
      const targetRows = targetResult.status === 'fulfilled' ? targetResult.value : [];
      const mergedTargets = [...targetRows, ...sourceRows.filter(row => !targetRows.some(targetRow => idOf(targetRow) === idOf(row)))];
      setSources(sourceRows); setTargets(mergedTargets); setSource(current => current || idOf(sourceRows[0] ?? {})); setTarget(current => current || idOf(mergedTargets[0] ?? {}));
      if (targetResult.status === 'rejected') setLoadError(`目标库选项接口失败：${errorText(targetResult.reason)}；仅显示已登记数据库`);
      setLoadingOptions(false);
    };
    void load();
    return () => { cancelled = true; };
  }, []);
  useEffect(() => {
    setLoadingJobs(true);
    void batchJobService.list().then(rows => {
      if (!mounted.current) return;
      setJobs(rows); setActiveJobId(current => current || rows[0]?.jobId || '');
    }).catch(error => { if (mounted.current) setLoadError(`读取批量作业失败：${errorText(error)}`); }).finally(() => { if (mounted.current) setLoadingJobs(false); });
  }, []);
  useEffect(() => {
    if (!activeJobId) { setJobDetail(undefined); return; }
    let cancelled = false;
    void batchJobService.detail(activeJobId).then(detail => { if (!cancelled) setJobDetail(detail); }).catch(error => { if (!cancelled) setLoadError(`读取作业详情失败：${errorText(error)}`); });
    return () => { cancelled = true; };
  }, [activeJobId]);
  useEffect(() => {
    if (!source) { setTables([]); setSourceTotal(0); return; }
    let cancelled = false;
    setLoadingTables(true); setTables([]);
    void batchPlatformService.sourceTables(source, sourcePage).then(result => {
      if (!cancelled) {
        setTables(result.list.filter(row => String(row.datasourceId ?? '') === source));
        setSourceTotal(result.total); setLoadingTables(false);
      }
    }).catch(error => { if (!cancelled) { setLoadError(errorText(error)); setLoadingTables(false); } });
    return () => { cancelled = true; };
  }, [source, sourcePage]);
  useEffect(() => { setSelectedMatches(new Map()); pendingSubmit.current = undefined; }, [source, target, prefix, remove, suffix]);

  const matches = useMemo<Match[]>(() => tables.map(row => {
    const original = sourceName(row), stripped = remove && original.startsWith(remove) ? original.slice(remove.length) : original;
    const expected = prefix + stripped + suffix, linked = Array.isArray(row.targetTables) ? row.targetTables : [];
    const chosen = linked.filter(candidate => targetDbId(candidate) === target && targetName(candidate).toLowerCase() === expected.toLowerCase());
    const existing = Boolean(row.accessTaskId) || Number(row.taskCount ?? 0) > 0;
    const reason = !target ? '请选择目标库' : !original ? '来源表缺少表名' : row.relationAmbiguous || linked.length > 1 ? '该来源关联多个目标表，不能按单目标批量创建' : !linked.length ? '尚未物化可访问的目标表' : !chosen.length ? '所选目标库中没有已物化的匹配表' : existing ? '接入任务已存在' : '';
    return { id: String(row.tid ?? row.sourceTableId ?? ''), source: row, target: chosen[0], targetName: expected, reason, eligible: !reason && Boolean(chosen[0]) };
  }), [tables, target, prefix, remove, suffix]);
  const eligible = matches.filter(match => match.eligible);
  const job = jobDetail?.job, items = jobDetail?.items ?? [];
  const jobState = job?.status === 'RUNNING'
    ? <Tag color={executingJobId === job.jobId ? 'processing' : 'warning'}>
        {executingJobId === job.jobId ? '本页执行中' : items.some(item => item.status === 'RUNNING') ? '等待单项结果或租约' : '待续跑'}
      </Tag>
    : job ? <StateTag state={job.status} /> : null;
  const sourceOptions = sources.map(row => ({ value: idOf(row), label: `${String(row.dbName ?? row.db_name ?? idOf(row))} / ${String(row.dbType ?? row.db_type ?? '')}` }));
  const targetOptions = targets.map(row => ({ value: idOf(row), label: `${String(row.dbName ?? row.db_name ?? idOf(row))} / ${String(row.dbType ?? row.db_type ?? '')}` }));

  const runOne = async (id: string) => {
    if (running.current) return;
    running.current = true; stopRequested.current = false; setSubmitting(true); setExecutingJobId(id);
    try {
      const claim = await batchJobService.claim(id);
      if (claim.busy) message.info('此作业已有领取中的单项；失联租约 10 分钟后可重试');
      else if (claim.done) message.info('当前没有待创建项');
      else {
        if (!claim.item || !claim.leaseToken) throw new Error('服务端未返回创建项或执行租约');
        let result: { success: boolean; taskId?: string; existing?: boolean; error?: string };
        try {
          const ensured = await batchPlatformService.ensureTask(claim.item.sourceTableId, claim.item.sourceCatalogId ?? '');
          if (ensured.repairRequired) throw new Error(`已有任务需要修复：${(ensured.repairReasons ?? []).join('；') || '请在任务管理中检查画布与字段映射'}`);
          const taskId = String(ensured.taskId ?? (ensured.task as Record<string, unknown> | undefined)?.tid ?? '');
          if (!taskId) throw new Error('创建接口未返回接入任务 ID，请到任务管理核实后重试');
          result = { success: true, taskId, existing: ensured.created === false };
        } catch (error) { result = { success: false, error: errorText(error) }; }
        // 用户主动执行一个单项；离开页面时尽量把该项结果回报到服务端。
        const detail = await batchJobService.complete(id, claim.item.itemId, claim.leaseToken, result);
        if (mounted.current) setJobDetail(detail);
      }
      if (mounted.current) {
        await refreshJobs(id);
        try { if (source) {
          const result = await batchPlatformService.sourceTables(source, sourcePage);
          if (mounted.current) { setTables(result.list.filter(row => String(row.datasourceId ?? '') === source)); setSourceTotal(result.total); }
        } }
        catch (error) { setLoadError(`作业已保存，但刷新匹配预览失败：${errorText(error)}`); }
      }
    } catch (error) {
      if (mounted.current) { setLoadError(`批量作业执行暂停：${errorText(error)}。已完成项保存在服务端，可稍后续跑。`); try { await refreshJobs(id); } catch { /* 保留首要错误 */ } }
    } finally { running.current = false; if (mounted.current) { setSubmitting(false); setExecutingJobId(''); } }
  };
  const submit = async (rows: Match[]) => {
    if (running.current || !rows.length) return;
    if (rows.length > 500) { message.warning('每个作业最多 500 张表，请分批选择'); return; }
    const fingerprint = JSON.stringify({ source, target, prefix, remove, suffix, ids: rows.map(row => row.id) });
    if (pendingSubmit.current?.fingerprint !== fingerprint) pendingSubmit.current = { fingerprint, requestKey: crypto.randomUUID() };
    const request: BatchCreateRequest = {
      requestKey: pendingSubmit.current.requestKey,
      jobName: `批量创建任务 · ${new Date().toLocaleDateString('zh-CN')}`,
      sourceDbId: source, targetDbId: target,
      matchRule: { removePrefix: remove, addPrefix: prefix, addSuffix: suffix },
      items: rows.map(row => ({ sourceTableId: row.id, targetTableId: targetId(row.target!) })),
    };
    setSubmitting(true);
    try {
      const detail = await batchJobService.create(request);
      pendingSubmit.current = undefined;
      setJobDetail(detail); setActiveJobId(detail.job.jobId); setSelectedMatches(new Map());
      await refreshJobs(detail.job.jobId);
    } catch (error) { setLoadError(`创建批量作业失败：${errorText(error)}`); }
    finally { if (!running.current) setSubmitting(false); }
  };
  const stop = async () => {
    if (!job) return;
    stopRequested.current = true;
    try { setJobDetail(await batchJobService.stop(job.jobId)); await refreshJobs(job.jobId); message.info('已停止领取后续项；当前执行的单项会回报结果'); }
    catch (error) { stopRequested.current = false; setLoadError(`停止作业失败：${errorText(error)}`); }
  };
  const retry = async () => {
    if (!job) return;
    try { setJobDetail(await batchJobService.retry(job.jobId)); await refreshJobs(job.jobId); }
    catch (error) { setLoadError(`重试未完成项失败：${errorText(error)}`); }
  };

  return <div className="ht-page">
    <PageHero kicker="任务开发" title="批量创建任务" description="批量核验并保存待创建队列；现有确保接口仅支持单表，需在此页逐项主动创建。作业和结果按当前租户持久保存。" kind="batch" tags={['表级批量匹配', '作业持久记录', '逐项创建']} />
    {loadError && <Alert type="error" showIcon message="批量任务接口异常" description={loadError} closable onClose={() => setLoadError('')} style={{ marginBottom: 16 }} />}
    <div className="two-col-config"><Panel title="匹配策略"><Form layout="vertical">
      <Form.Item label="来源库" extra="按当前角色数据范围读取，最多展示 200 个已登记来源库；超出上限时请先缩小范围。"><Select value={source || undefined} loading={loadingOptions} options={sourceOptions} placeholder="选择已登记来源库" onChange={value => { setSourcePage(1); setSource(value); }} /></Form.Item>
      <Form.Item label="目标库"><Select value={target || undefined} loading={loadingOptions} options={targetOptions} placeholder="选择目标库" onChange={setTarget} /></Form.Item>
      <div className="form-grid"><Form.Item label="去掉来源前缀"><Input value={remove} placeholder="例如 ods_" onChange={event => setRemove(event.target.value)} /></Form.Item><Form.Item label="添加目标前缀"><Input value={prefix} onChange={event => setPrefix(event.target.value)} /></Form.Item></div>
      <Form.Item label="添加目标后缀"><Input value={suffix} placeholder="选填" onChange={event => setSuffix(event.target.value)} /></Form.Item>
      <Form.Item label="运行集群"><Select value="platform" disabled options={[{ value: 'platform', label: '由数据中台的 NiFi 配置决定' }]} /></Form.Item>
    </Form><p className="helper">此页只创建待发布草稿，不自动运行 NiFi 流程。集群与每日相位由数据中台现有配置决定。</p></Panel>
      <Panel title="匹配预览" extra={<Tag color="blue">本页 {eligible.length} 组可创建，已选 {selectedMatches.size} 组</Tag>}>
        <div className="ht-toolbar"><Checkbox checked={eligible.length > 0 && eligible.every(match => selectedMatches.has(match.id))} onChange={event => setSelectedMatches(previous => {
          const next = new Map(previous);
          for (const match of eligible) { if (event.target.checked) next.set(match.id, match); else next.delete(match.id); }
          return next;
        })}>选择本页匹配项</Checkbox><span className="toolbar-spacer" /><Button type="primary" loading={submitting} disabled={!selectedMatches.size || loadingTables || submitting} onClick={() => void submit([...selectedMatches.values()])}>保存 {selectedMatches.size} 项待创建队列</Button></div>
        <DataTable<Match> rowKey="id" loading={loadingTables} dataSource={matches}
          pagination={{ current: sourcePage, pageSize: 20, total: sourceTotal, showSizeChanger: false,
            onChange: nextPage => setSourcePage(nextPage) }} columns={[
          { title: '选择', width: 60, render: (_, match) => <Checkbox checked={selectedMatches.has(match.id)} disabled={!match.eligible || submitting} onChange={event => setSelectedMatches(previous => {
            const next = new Map(previous);
            if (event.target.checked) next.set(match.id, match); else next.delete(match.id);
            return next;
          })} /> },
          { title: '来源表', render: (_, match) => <div className="entity-name"><b>{sourceLabel(match.source)}</b><small>{sourceName(match.source)}</small></div> },
          { title: '目标表', render: (_, match) => match.target ? <div className="entity-name"><b>{targetName(match.target)}</b><small>{String(match.target.targetDbName ?? '')}{match.reason ? ` · ${match.reason}` : ''}</small></div> : <Tag color="warning">{match.targetName} {match.reason || '未找到'}</Tag> },
          { title: '字段映射', render: (_, match) => match.eligible ? '由现有创建接口生成' : '—' },
        ]} />
        <Alert type="info" message="仅提交已物化、唯一关联且表名匹配的目标表。" description="保存队列时服务端按当前租户和角色数据范围批量复核。现有单表创建接口尚无真正批量契约，因此不会自动连续创建；请逐项点击创建，后续批量执行能力待接口改造。失联单项的租约满 10 分钟后可重试。" style={{ marginTop: 16 }} />
      </Panel></div>
    <Panel title="批量作业与逐项结果" extra={<Space><Select value={activeJobId || undefined} loading={loadingJobs} placeholder="暂无作业" style={{ width: 300 }} onChange={setActiveJobId} options={jobs.map(row => ({ value: row.jobId, label: `${row.jobName} / ${formatTime(row.createdTime)}${row.status === 'RUNNING' && executingJobId !== row.jobId ? ' / 待逐项创建' : ''}` }))} />{jobState}{job?.status === 'RUNNING' && <><Button disabled={submitting} onClick={() => void runOne(job.jobId)}>创建下一项</Button><Button onClick={() => void stop()}>停止后续创建</Button></>}{job && job.status !== 'RUNNING' && items.some(item => item.status !== 'SUCCEEDED') && <Button disabled={submitting} onClick={() => void retry()}>重试未完成项</Button>}<Button onClick={() => void refreshJobs(activeJobId)}>刷新</Button></Space>}>
      {job ? <><div className="inline-stats"><span>成功 <b>{countOf(items, 'SUCCEEDED')}</b></span><span>失败 <b>{countOf(items, 'FAILED')}</b></span><span>已领取待回报 <b>{countOf(items, 'RUNNING')}</b></span><span>待创建 <b>{countOf(items, 'PENDING')}</b></span></div>
        <DataTable<BatchJobItem> rowKey="itemId" dataSource={items} pagination={false} columns={[
          { title: '来源表', render: (_, item) => item.sourceTableName },
          { title: '目标表', render: (_, item) => item.targetTableName },
          { title: '尝试次数', dataIndex: 'attemptCount', width: 90 },
          { title: '创建状态', render: (_, item) => item.status === 'RUNNING' && executingJobId !== job.jobId ? <Tag color="warning">等待单项结果或租约</Tag> : <StateTag state={item.status} /> },
          { title: '结果', render: (_, item) => item.taskId ? <Button type="link" onClick={() => navigate('/development/tasks')}>{item.wasExisting ? '查看已有任务' : '查看任务'} · {item.taskId}</Button> : item.errorMessage || '等待创建' },
        ]} /><div className="run-log">{items.filter(item => item.completedTime || item.claimedTime).map(item => <p key={item.itemId}><time>{formatTime(item.completedTime || item.claimedTime || '')}</time><b>{item.status}</b>{item.sourceTableName}{item.errorMessage ? `：${item.errorMessage}` : item.taskId ? `：${item.taskId}` : ''}</p>)}</div>
      </> : <p className="helper">当前租户还没有批量作业。创建后可在这里跨页面查看和续跑。</p>}
    </Panel>
  </div>;
}
