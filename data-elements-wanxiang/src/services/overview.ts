import { type Row } from './api';
import { listSources } from './datasources';
import { platformApi } from '../shared/platformApi';
import { listModelViews } from './metadataViews';
import { qualityApi } from './quality';
/** Dashboard projections use the same authoritative services as their destination menus. */
export async function overviewSummary(signal?:AbortSignal):Promise<Row>{
 const [summary,quality]=await Promise.all([platformApi<Row>('/dwm/metadata-governance/summary',{}, {signal}),qualityApi.summary(signal)]);
 return {...summary,models:listModelViews().length,issues:quality.violation_total};
}
export async function overviewDetails(signal?:AbortSignal):Promise<Row[][]>{
 const [sources,runs]=await Promise.all([listSources(signal),qualityApi.runPage({page:1,size:100},signal)]);
 const status:Record<string,string>={PENDING:'QUEUED',INITIALIZING:'RUNNING',RUNNING:'RUNNING',COMPLETED:'SUCCEEDED',COMPLETED_WITH_ISSUES:'PARTIAL',FAILED:'FAILED',CANCELLED:'CANCELLED'};
 return [sources,runs.list.map(r=>({...r,name:r.task_name,type:r.task_kind==='PROFILE'?'PROFILING':'QUALITY',status:status[r.status]||r.status,created_at:r.started_time||r.created_time,done_count:r.shard_completed,total_count:r.shard_total,phase:r.table_name})),listModelViews()];
}
