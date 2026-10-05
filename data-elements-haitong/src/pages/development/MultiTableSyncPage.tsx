import { Alert, App, Button, Input, Modal, Space, Tag } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { integrationPlatformService } from '../../api/integrationPlatformService';
import { multiTableService, type MultiTableCandidate, type MultiTableGroup, type MultiTableItem } from '../../api/multiTableService';
import PageHero from '../../components/PageHero';
import { DataTable, InsightStrip, Panel, formatTime } from '../../components/Common';
import Icon from '../../design/Icon';

const text = (v: unknown) => v == null ? '' : String(v).trim();
const sourceId = (s: MultiTableCandidate) => text(s.sourceTableId);
const sourceLabel = (s: MultiTableCandidate) => text(s.sourceTableNameCn || s.sourceTableName) || sourceId(s);
const newDraftId = () => {
  const bytes = new Uint8Array(16);
  if (globalThis.crypto?.getRandomValues) globalThis.crypto.getRandomValues(bytes);
  else for (let index = 0; index < bytes.length; index += 1) bytes[index] = Math.floor(Math.random() * 256);
  return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
};
const statusTag = (item: MultiTableItem) => item.taskAvailable === false ? <Tag color="warning">单表任务已失效</Tag>
  : Number(item.taskStatus) === 1 ? <Tag color="processing">运行中</Tag>
  : Number(item.taskStatus) === 2 ? <Tag color="error">运行异常</Tag> : <Tag>未启用</Tag>;

/** Persisted tenant grouping of independent single-table tasks, not a native NiFi flow. */
export default function MultiTableSyncPage() {
  const navigate = useNavigate(), { message, modal } = App.useApp(), client = useQueryClient();
  const [searchValue, setSearchValue] = useState(''), [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1), [size, setSize] = useState(20);
  const [createOpen, setCreateOpen] = useState(false), [groupName, setGroupName] = useState('');
  const [sourceSearch, setSourceSearch] = useState(''), [sourceKeyword, setSourceKeyword] = useState('');
  const [sourcePage, setSourcePage] = useState(1), [selected, setSelected] = useState<MultiTableCandidate[]>([]);
  const [draftId, setDraftId] = useState(newDraftId), [working, setWorking] = useState(false);
  const [retryKey, setRetryKey] = useState('');
  const groupsQuery = useQuery({ queryKey: ['platform', 'multi-table-groups', page, size, keyword],
    queryFn: () => multiTableService.page({ page, size, keyword }) });
  const sourcesQuery = useQuery({ queryKey: ['platform', 'multi-table-sources', sourcePage, sourceKeyword],
    queryFn: () => multiTableService.candidates({ page: sourcePage, size: 10, keyword: sourceKeyword }), enabled: createOpen });
  const groups = groupsQuery.data?.list || [], items = groups.flatMap(group => group.items || []);
  const refresh = () => client.invalidateQueries({ queryKey: ['platform', 'multi-table-groups'] });
  const resetDraft = () => { setSelected([]); setGroupName(''); setDraftId(newDraftId()); };
  const move = (index: number, offset: number) => { const next = [...selected], target = index + offset;
    if (target < 0 || target >= next.length) return; [next[index], next[target]] = [next[target], next[index]]; setSelected(next); };
  const create = async () => {
    if (!groupName.trim() || selected.length < 2) { message.warning('请填写名称并选择至少两个已有单表任务'); return; }
    setWorking(true);
    try {
      const saved = await multiTableService.save(draftId, groupName.trim(), selected.map(row => row.taskId));
      setCreateOpen(false); resetDraft(); setPage(1); await refresh();
      message.success(saved.created ? `已关联 ${saved.tableCount} 个独立单表任务` : '已找到相同请求的编组');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建失败');
    } finally { setWorking(false); }
  };
  const remove = (group: MultiTableGroup) => modal.confirm({ title: `删除编组“${group.groupName}”？`,
    content: '只删除编组关联，单表任务、NiFi 画布和已有数据都会保留。', okText: '删除编组', okButtonProps: { danger: true },
    onOk: async () => { await multiTableService.remove(group.groupId); await refresh(); message.success('编组已删除，单表任务保留'); } });
  const retry = async (group: MultiTableGroup, item: MultiTableItem) => {
    const key = `${group.groupId}:${item.sourceTableId}`; setRetryKey(key);
    try {
      const ensured = await integrationPlatformService.ensureTask({ sourceTableId: item.sourceTableId });
      if (ensured.repairRequired) throw new Error((ensured.repairReasons || []).join('；') || '单表任务需先修复');
      const taskId = text(ensured.task?.tid || ensured.taskId);
      if (!taskId) throw new Error('单表任务未返回标识');
      await multiTableService.relink(group.groupId, item.sourceTableId, taskId);
      await refresh(); message.success('单表任务关联已补齐');
    } catch (error) { message.error(error instanceof Error ? error.message : '补齐失败'); }
    finally { setRetryKey(''); }
  };
  const openCanvas = (item: MultiTableItem) => { const params = new URLSearchParams();
    if (text(item.pipelineId)) params.set('pipelineId', text(item.pipelineId));
    if (text(item.taskId)) params.set('accessTaskId', text(item.taskId));
    if (!params.size) { message.error('单表任务缺少画布标识'); return; }
    navigate(`/development/canvas?${params}`, { state: { returnTo: '/development/multi-table' } }); };

  return <div className="ht-page">
    <PageHero kicker="任务开发" title="多表同步任务" kind="multi" tags={['真实单表任务编组', '逐表状态', '独立 NiFi 画布']}
      description="把已有单表接入任务编为一组，按顺序管理并查看各自运行状态。"
      primaryAction={<Button type="primary" icon={<Icon name="plus" size={17}/>} onClick={() => setCreateOpen(true)}>新建多表编组</Button>} />
    <Alert type="info" showIcon style={{ marginBottom: 18 }} message="编组是管理视图，不会合并成一个 NiFi 流程，也不会统一启动所有表。"
      description="每张表保留独立画布。先建立单表任务再加入编组；重复提交同一草稿会安全复用，删除编组不删除单表任务。" />
    <InsightStrip items={[
      { label: '可见编组数', value: groupsQuery.data?.total ?? '—', icon: 'tasks', hint: '当前租户及数据范围' },
      { label: '当前页运行中单表', value: items.filter(row => row.taskAvailable !== false && Number(row.taskStatus) === 1).length, icon: 'check', hint: '来自单表任务状态', tone: 'cyan' },
      { label: '当前页异常或失效', value: items.filter(row => row.taskAvailable === false || Number(row.taskStatus) === 2).length, icon: 'warning', hint: '可逐表检查', tone: 'amber' },
    ]} />
    <Panel><div className="ht-toolbar"><Input allowClear prefix={<Icon name="search" size={17}/>} value={searchValue}
      onChange={event => setSearchValue(event.target.value)} onPressEnter={() => { setPage(1); setKeyword(searchValue.trim()); }}
      placeholder="搜索编组名称" style={{ width: 310, maxWidth: '100%' }} />
      <Button onClick={() => { setPage(1); setKeyword(searchValue.trim()); }}>查询</Button><span className="toolbar-spacer" />
      <Button onClick={() => void refresh()} loading={groupsQuery.isFetching}>刷新状态</Button></div>
      {groupsQuery.isError && <Alert type="error" showIcon message="多表编组读取失败"
        description={groupsQuery.error instanceof Error ? groupsQuery.error.message : '请检查数据中台会话与接口'}
        action={<Button size="small" onClick={() => void groupsQuery.refetch()}>重试</Button>} style={{ marginBottom: 16 }} />}
      <DataTable<MultiTableGroup> rowKey="groupId" dataSource={groups} loading={groupsQuery.isLoading || groupsQuery.isFetching}
        pagination={{ current: page, pageSize: size, total: groupsQuery.data?.total || 0, showSizeChanger: true,
          pageSizeOptions: [10, 20], onChange: (nextPage, nextSize) => { setPage(nextPage); setSize(nextSize); } }}
        expandable={{ expandedRowRender: group => <DataTable<MultiTableItem> rowKey="itemId" dataSource={group.items || []} pagination={false} columns={[
          { title: '顺序', dataIndex: 'sortNo', width: 68 },
          { title: '来源表', width: 230, render: (_, row) => <div className="entity-name"><b>{text(row.sourceTableNameCn || row.sourceTableName) || '来源表已失效'}</b><small>{text(row.sourceTableName) || row.sourceTableId}</small></div> },
          { title: '单表接入任务', width: 250, render: (_, row) => <div className="entity-name"><b>{text(row.taskName) || (row.taskAvailable === false ? '任务已失效' : '未命名任务')}</b><small>{row.taskId}</small></div> },
          { title: '状态', width: 125, render: (_, row) => statusTag(row) },
          { title: '监控', width: 160, render: (_, row) => text(row.monitorMessage || row.monitorStatus) || '—' },
          { title: '最近运行', width: 150, render: (_, row) => row.lastRunning ? formatTime(row.lastRunning) : '—' },
          { title: '操作', width: 235, render: (_, row) => <Space><Button type="link" disabled={row.taskAvailable === false} onClick={() => openCanvas(row)}>单表 NiFi 画布</Button>
            <Button type="link" disabled={!group.canManage} loading={retryKey === `${group.groupId}:${row.sourceTableId}`} onClick={() => void retry(group, row)}>检查并补齐</Button></Space> },
        ]} /> }} columns={[
          { title: '编组名称', dataIndex: 'groupName', width: 280, render: (name: string) => <strong>{name}</strong> },
          { title: '来源表数', width: 115, render: (_, row) => `${row.items?.length || 0} 张` },
          { title: '运行中', width: 100, render: (_, row) => row.items?.filter(item => item.taskAvailable !== false && Number(item.taskStatus) === 1).length || 0 },
          { title: '异常/失效', width: 110, render: (_, row) => row.items?.filter(item => item.taskAvailable === false || Number(item.taskStatus) === 2).length || 0 },
          { title: '创建时间', width: 170, render: (_, row) => row.createdTime ? formatTime(row.createdTime) : '—' },
          { title: '说明', width: 225, render: () => <span className="muted">独立单表任务的管理编组</span> },
          { title: '操作', width: 140, render: (_, row) => <Button type="link" danger disabled={!row.canManage} onClick={() => remove(row)}>删除编组</Button> },
        ]} />
    </Panel>
    <Modal title="新建多表任务编组" width={930} open={createOpen} onCancel={() => { if (!working) setCreateOpen(false); }}
      maskClosable={!working} closable={!working} destroyOnClose
      footer={<Space><Button disabled={working} onClick={resetDraft}>重置草稿</Button><Button disabled={working} onClick={() => setCreateOpen(false)}>关闭</Button>
        <Button type="primary" loading={working} disabled={selected.length < 2 || !groupName.trim()} onClick={() => void create()}>保存任务编组</Button></Space>}>
      <Alert type="info" showIcon style={{ marginBottom: 14 }} message="选择至少两个已有单表接入任务；每张来源表只能选一个任务。" description="候选列表由服务端按当前数据权限分页返回。未创建单表任务的表，请先在单表接入页面创建。" />
      <Input value={groupName} onChange={event => setGroupName(event.target.value)} maxLength={200} disabled={working}
        placeholder="编组名称，例如：案件主题多表接入" style={{ marginBottom: 12 }} />
      <div className="ht-toolbar"><Input allowClear value={sourceSearch} onChange={event => setSourceSearch(event.target.value)}
        onPressEnter={() => { setSourcePage(1); setSourceKeyword(sourceSearch.trim()); }} placeholder="搜索单表任务或来源表" style={{ width: 290 }} />
        <Button onClick={() => { setSourcePage(1); setSourceKeyword(sourceSearch.trim()); }}>查询单表任务</Button>
        <span className="toolbar-spacer" /><span className="helper">已选 {selected.length} 个任务</span></div>
      {sourcesQuery.isError && <Alert type="error" showIcon message="单表任务读取失败"
        description={sourcesQuery.error instanceof Error ? sourcesQuery.error.message : '请检查会话与接口'} style={{ marginBottom: 10 }} />}
      <DataTable<MultiTableCandidate> rowKey="taskId" dataSource={sourcesQuery.data?.list || []} loading={sourcesQuery.isLoading}
        pagination={{ current: sourcePage, pageSize: 10, total: sourcesQuery.data?.total || 0, onChange: setSourcePage }} columns={[
          { title: '单表任务', width: 270, render: (_, row) => <div className="entity-name"><b>{text(row.taskName) || row.taskId}</b><small>{row.taskId}</small></div> },
          { title: '来源表', width: 260, render: (_, row) => <div className="entity-name"><b>{sourceLabel(row)}</b><small>{text(row.sourceTableName)}</small></div> },
          { title: '数据源', render: (_, row) => text(row.datasourceName) || '—' },
          { title: '操作', width: 125, render: (_, row) => <Button type="link" disabled={working || selected.length >= 100 || selected.some(item => sourceId(item) === sourceId(row))}
            onClick={() => { if (sourceId(row)) setSelected(items => [...items, row]); else message.error('来源表缺少标识'); }}>
            {selected.some(item => sourceId(item) === sourceId(row)) ? '已添加' : '加入编组'}</Button> },
        ]} />
      {selected.length > 0 && <div style={{ marginTop: 14 }}><strong>编组显示顺序</strong>{selected.map((row, index) => <div key={row.taskId}
        style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '6px 0', borderBottom: '1px solid #e7eaf0' }}>
        <span style={{ minWidth: 30 }}>{index + 1}.</span><span style={{ flex: 1 }}>{sourceLabel(row)} · {text(row.taskName) || row.taskId}</span>
        <Button size="small" disabled={working || index === 0} onClick={() => move(index, -1)}>上移</Button>
        <Button size="small" disabled={working || index === selected.length - 1} onClick={() => move(index, 1)}>下移</Button>
        <Button size="small" danger disabled={working} onClick={() => setSelected(items => items.filter(item => item.taskId !== row.taskId))}>移除</Button>
      </div>)}</div>}
    </Modal>
  </div>;
}
