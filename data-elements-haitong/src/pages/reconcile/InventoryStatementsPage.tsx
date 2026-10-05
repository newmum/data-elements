import { Alert, Button, Descriptions, Drawer, Select, Space } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import PageHero from '../../components/PageHero';
import { DataTable, InsightStrip, Panel, StateTag, formatTime } from '../../components/Common';
import { downloadText } from '../../services/workspace';
import { safeCsv } from '../../domain/rules';
import Icon from '../../design/Icon';
import { reconciliationLive, num, str, type ReconcileDiff, type ReconcileRun } from '../../api/reconciliationLive';

const displayStatus = (run: ReconcileRun) => {
  const status = str(run, 'status').toUpperCase();
  return status === 'CONSISTENT' ? 'MATCH' : status === 'INCONSISTENT' ? 'DIFFERENT' : status === 'FAILED' ? 'BLOCKED' : status;
};
const diffKind = (diff: ReconcileDiff) => {
  const kind = str(diff, 'diff_type', 'diffType').toUpperCase();
  return ({ MISSING_TARGET: 'MISSING', EXTRA_TARGET: 'EXTRA', VALUE_MISMATCH: 'CONTENT' } as Record<string, string>)[kind] || kind;
};

export default function InventoryStatementsPage() {
  const [records, setRecords] = useState<ReconcileRun[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [filter, setFilter] = useState('ALL');
  const [detail, setDetail] = useState<ReconcileRun>();
  const [diffs, setDiffs] = useState<ReconcileDiff[]>([]);
  const [diffTotal, setDiffTotal] = useState(0);
  const [diffPage, setDiffPage] = useState(1);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [exporting, setExporting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const backendStatus = ({ MATCH: 'CONSISTENT', DIFFERENT: 'INCONSISTENT', BLOCKED: 'FAILED' } as Record<string, string>)[filter] || '';
      const result = await reconciliationLive.runs(page, size, backendStatus);
      setRecords(result.list); setTotal(result.total); setError('');
    } catch (cause) {
      setRecords([]); setTotal(0); setError(cause instanceof Error ? cause.message : String(cause));
    } finally { setLoading(false); }
  }, [filter, page, size]);
  useEffect(() => { void load(); }, [load]);

  const openDetail = async (run: ReconcileRun) => {
    setDetail(run); setDiffs([]); setDiffPage(1); setDetailError(''); setDetailLoading(true);
    try {
      const [instance, differencePage] = await Promise.all([reconciliationLive.runDetail(run.tid), reconciliationLive.diffs(run.tid, 1, 20)]);
      setDetail(instance); setDiffs(differencePage.list); setDiffTotal(differencePage.total);
    } catch (cause) { setDetailError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setDetailLoading(false); }
  };
  const loadDiffs = async (runId: string, nextPage: number) => {
    setDetailLoading(true);
    try {
      const result = await reconciliationLive.diffs(runId, nextPage, 20);
      setDiffs(result.list); setDiffTotal(result.total); setDiffPage(nextPage); setDetailError('');
    } catch (cause) { setDetailError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setDetailLoading(false); }
  };
  const exportDiff = async (csv = false) => {
    if (!detail) return;
    setExporting(true); setDetailError('');
    try {
      const result = await reconciliationLive.exportDiffs(detail.tid);
      if (!Array.isArray(result.rows) || !Number.isSafeInteger(result.total) || result.rows.length !== result.total) {
        throw new Error('完整差异导出返回条数不一致，请重试');
      }
      const rows = result.rows.map(diff => ({
        key: String(diff.businessKey ?? ''),
        kind: String(diff.diffType ?? ''),
        field: String(diff.fieldName ?? ''),
        expected: String(diff.sourceValueMasked ?? ''),
        actual: String(diff.targetValueMasked ?? ''),
      }));
      const baseName = String(result.filename || `${detail.tid}-differences`).replace(/\.(csv|json)$/i, '').replace(/[^\w\u4e00-\u9fff.-]/g, '_');
      const filename = `${baseName}.${csv ? 'csv' : 'json'}`;
      downloadText(filename, csv ? safeCsv(rows, ['key','kind','field','expected','actual']) : JSON.stringify({ runId: result.runId, total: result.total, differences: rows }, null, 2), csv ? 'text/csv;charset=utf-8' : 'application/json');
    } catch (cause) {
      setDetailError(cause instanceof Error ? cause.message : String(cause));
    } finally { setExporting(false); }
  };
  const consistent = records.filter(run => displayStatus(run) === 'MATCH').length;
  const different = records.filter(run => ['DIFFERENT','BLOCKED'].includes(displayStatus(run))).length;

  return <div className="ht-page"><PageHero kicker="数据对账" title="盘点对账记录" description="查看每次盘点的执行结果和差异明细，按策略与时间回溯，并导出已脱敏的差异数据。" kind="statements" tags={['历史执行','差异明细','脱敏导出']}/>
    <InsightStrip items={[{label:'执行记录',value:total,icon:'statements',hint:'来自后端执行实例'},{label:'结果一致',value:consistent,icon:'check',hint:'当前页一致的实例',tone:'cyan'},{label:'存在差异 / 阻断',value:different,icon:'warning',hint:'当前页差异或失败的实例',tone:'amber'}]}/>
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={()=>void load()}>重试</Button>}/>}
    <Panel><div className="ht-toolbar"><Select value={filter} onChange={value=>{setPage(1);setFilter(value);}} style={{width:170}} options={[{value:'ALL',label:'全部结果'},{value:'MATCH',label:'一致'},{value:'DIFFERENT',label:'有差异'},{value:'BLOCKED',label:'配对阻断'}]}/><span className="helper">历史结果保存执行时的策略快照，不随策略编辑改变。</span></div>
      <DataTable<ReconcileRun> rowKey="tid" loading={loading} dataSource={records} pagination={{current:page,pageSize:size,total,showSizeChanger:true,pageSizeOptions:[10,20,50,100],showTotal:n=>`共 ${n} 条记录`}} onChange={pagination=>{setPage(pagination.current||1);setSize(pagination.pageSize||10);}} columns={[{title:'盘点策略 / 版本',width:245,render:(_,run)=><div className="entity-name"><button className="text-link strong" onClick={()=>void openDetail(run)}>{str(run,'policy_name','policyName')||'对账策略'}</button><small>{run.tid.slice(-8)}</small></div>},{title:'来源条数',width:104,render:(_,run)=>num(run,'source_count','sourceCount')},{title:'目标条数',width:104,render:(_,run)=>num(run,'target_count','targetCount')},{title:'比较结果',width:120,render:(_,run)=><StateTag state={displayStatus(run)}/>},{title:'差异项数',width:95,render:(_,run)=>num(run,'diff_count','diffCount')},{title:'创建时间',width:180,render:(_,run)=>formatTime(str(run,'created_time','createdTime'))},{title:'操作',fixed:'right',width:110,render:(_,run)=><Button type="link" onClick={()=>void openDetail(run)}>差异详情</Button>}]}/></Panel>
    <Drawer width={980} open={!!detail} onClose={()=>setDetail(undefined)} title="盘点记录 · 差异明细" extra={<Space><Button icon={<Icon name="download" size={17}/>} onClick={()=>void exportDiff(true)} loading={exporting} disabled={!detail||diffTotal===0}>完整 CSV</Button><Button onClick={()=>void exportDiff(false)} loading={exporting} disabled={!detail||diffTotal===0}>完整 JSON</Button></Space>}>
      {detail && <><Descriptions column={2} items={[{key:'name',label:'策略',children:str(detail,'policy_name','policyName')},{key:'rev',label:'执行实例',children:detail.tid},{key:'date',label:'执行时间',children:formatTime(str(detail,'created_time','createdTime'))},{key:'state',label:'结果',children:<StateTag state={displayStatus(detail)}/>},{key:'mode',label:'比较口径',children:str(detail,'compare_mode','compareMode')},{key:'keys',label:'触发方式',children:str(detail,'trigger_type','triggerType')},{key:'window',label:'时间窗口',children:`${str(detail,'lower_watermark','lowerWatermark') || '起始'} 至 ${str(detail,'upper_watermark','upperWatermark') || '结束'}`},{key:'rows',label:'来源 / 目标',children:`${num(detail,'source_count','sourceCount')} / ${num(detail,'target_count','targetCount')}`}]} />
        {detailError && <Alert type="error" showIcon message={detailError}/>}
        <DataTable<ReconcileDiff> rowKey="tid" loading={detailLoading} dataSource={diffs} pagination={{current:diffPage,pageSize:20,total:diffTotal,showTotal:n=>`共 ${n} 条差异`}} onChange={pagination=>void loadDiffs(detail.tid,pagination.current||1)} columns={[{title:'主键值',width:210,render:(_,diff)=><code className="break-all">{str(diff,'business_key','businessKey')}</code>},{title:'差异类型',width:150,render:(_,diff)=><StateTag state={diffKind(diff)}/>},{title:'字段',width:110,render:(_,diff)=>str(diff,'field_name','fieldName')},{title:'预期值',width:220,render:(_,diff)=><span className="diff-expected">{str(diff,'source_value_masked','sourceValueMasked')||'—'}</span>},{title:'实际值',width:220,render:(_,diff)=><span className="diff-actual">{str(diff,'target_value_masked','targetValueMasked')||'—'}</span>}]}/>
        <p className="helper">完整导出由服务端核验当前租户并返回脱敏差异；超过服务端上限时会明确报错，不会悄悄截断文件。</p>
      </>}
    </Drawer>
  </div>;
}
