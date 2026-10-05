import { useEffect, useMemo, useState } from 'react';
import { App, Button, Descriptions, Drawer, Input, Select, Space, Table, Tabs, Tag, Tree } from 'antd';
import { FolderOutlined, ApartmentOutlined, PlayCircleOutlined, SearchOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { Row } from '../../services/api';
import { useWorkspaceQuery } from '../../services/useWorkspaceQuery';
import { listSources, collectionStatuses } from '../../services/datasources';
import { readCatalogScope, readCatalogTablesPage, catalogTableColumns, invalidateMetadata } from '../../services/metadata';
import { buildCatalogTree, findCatalogNode, searchCatalogTree, catalogTableRows, initialCatalogExpandedKeys, catalogSearchExpandedKeys, type CatalogTableRow, type CatalogNode } from '../../services/metadataCatalog';
import { catalogRegistrationApi, associateCatalogs, type RegisteredCatalog } from '../../services/catalogRegistrationApi';
import { SourceIcon } from '../../design/Visuals';
import { SourceCollectionDrawer } from './SourceCollectionDrawer';
import { SourceFieldCollectionDrawer } from './SourceFieldCollectionDrawer';
import { CatalogEditor } from './CatalogEditor';
import { buildSourceModelView } from '../../services/metadataViews';
import { refreshStudio } from '../../services/studioRefresh';
import { useStudio } from '../er/store';
import { DataTable, Page, ErrorNotice, Status, date, useAction } from './common';
import '../../styles/shared-metadata.css';
export function CollectionPage(){
 const q=useWorkspaceQuery('shared-collection-sources',s=>listSources(s));
 const [tab,setTab]=useState('sources'),[source,setSource]=useState<Row|null>(null),[fieldSource,setFieldSource]=useState<Row|null>(null),[jobs,setJobs]=useState<Row[]>([]),[jobsError,setJobsError]=useState(''),[jobsLoading,setJobsLoading]=useState(false);
 const nav=useNavigate();
 const {message}=App.useApp();const {busy,run}=useAction();
 useEffect(()=>{if(tab!=='jobs'||!q.data)return;let live=true;const controller=new AbortController();setJobsLoading(true);setJobsError('');
  void collectionStatuses(q.data.map(source=>String(source.id)),controller.signal).then(statuses=>{
   if(!live)return;
   const bySource=new Map(statuses.map(job=>[String(job.datasourceId),job]));
   setJobs(q.data!.flatMap(source=>{const job=bySource.get(String(source.id));return job?.exists?[{id:job.jobId||source.id,source,...job}]:[];}));
  }).catch(error=>{if(live){setJobs([]);setJobsError(error instanceof Error?error.message:String(error));}})
   .finally(()=>{if(live)setJobsLoading(false);});
  return()=>{live=false;controller.abort();};
 },[tab,q.data]);
 const complete=()=>{invalidateMetadata();q.refresh();};
 const buildModel=(row:Row)=>void run(async()=>{
  await useStudio.getState().save();
  if(useStudio.getState().saveState==='error')throw new Error('当前模型尚未保存，请先处理保存错误');
  const result=await buildSourceModelView(String(row.id));
  await refreshStudio();
  await useStudio.getState().switchDiagram(result.model.id);
  return result;
 },'').then(result=>{if(result)message.success(`已纳入 ${result.tableCount} 张表，展示 ${result.relationCount} 条已保存关系`);});
 return <Page title="元数据采集" actions={<Button type="primary" onClick={()=>nav('/governance/metadata/sources')}>管理数据源</Button>}><Tabs activeKey={tab} onChange={setTab} items={[
 {key:'sources',label:`采集数据源 · ${q.data?.length??0}`,children:<DataTable rows={q.data} loading={q.loading} error={q.error} onRefresh={q.refresh} titleKey="shared-collection-sources" columns={[
  {title:'数据来源',width:290,render:(_,r)=><div className="wx-object-summary"><SourceIcon small engine={r.engine}/><div><strong>{r.name}</strong><small>{r.businessSystem||'未关联业务系统'}</small></div></div>},
  {title:'连接状态',width:115,render:(_,r)=><Status value={r.status}/>},{title:'已采集表',dataIndex:'entityCount',width:110,align:'right'},{title:'已采集字段',dataIndex:'fieldCount',width:120,align:'right'},{title:'更新时间',dataIndex:'updatedAt',width:165,render:date},
  {title:'操作',width:184,render:(_,r)=><div className="wx-collection-actions"><Button type="link" icon={<PlayCircleOutlined/>} disabled={r.status==='DISABLED'||!Number(r.entityCount)} onClick={()=>setFieldSource(r)}>采集字段</Button><Button type="link" icon={<ApartmentOutlined/>} loading={busy} disabled={!Number(r.entityCount)} onClick={()=>buildModel(r)}>构建模型</Button></div>}
 ]}/>},
 {key:'jobs',label:'执行记录',children:<><div className="wx-metadata-note">共享接口提供每个数据源最近一次任务。完整历史归档与定时采集尚无对应接口。</div><DataTable rows={jobs} error={jobsError} loading={jobsLoading} onRefresh={q.refresh} titleKey="shared-collection-jobs" columns={[
  {title:'数据源',width:280,render:(_,r)=>r.source.name},{title:'状态',dataIndex:'status',render:v=><Status value={v}/>},{title:'阶段',dataIndex:'phase'},{title:'源库发现表',dataIndex:'totalCount',align:'right'},{title:'本次新增保存',dataIndex:'persistedCount',align:'right'},{title:'异常说明',dataIndex:'error',width:220},{title:'操作',width:120,render:(_,r)=><Button type="link" onClick={()=>setSource(r.source)}>进度与结构</Button>}
 ]}/></>}
 ]}/><SourceCollectionDrawer source={source} onClose={()=>setSource(null)} onComplete={complete}/><SourceFieldCollectionDrawer source={fieldSource} onClose={()=>setFieldSource(null)} onComplete={complete}/></Page>;
}
function TreeLabel({node}:{node:CatalogNode}){return <span className="wx-metadata-tree-label">{node.kind==='datasource'?<SourceIcon small engine={node.sourceType||''}/>:node.kind==='application'?<ApartmentOutlined/>:<FolderOutlined/>}<span title={node.title}>{node.title}</span></span>;}
export function CatalogPage(){
 const scope=useWorkspaceQuery('shared-catalog-scope',s=>readCatalogScope(s));

 const [params]=useSearchParams();const nav=useNavigate();
 const returnTo=params.get('returnTo');const assetReturnTo=returnTo?.startsWith('/assets/')&&!returnTo.startsWith('//')?returnTo:null;
 const [selected,setSelected]=useState('all'),[treeQuery,setTreeQuery]=useState(''),[expanded,setExpanded]=useState<React.Key[]>([]),[kind,setKind]=useState<string>();
 const [page,setPage]=useState(1),[pageSize,setPageSize]=useState(15),[keyword,setKeyword]=useState(params.get('q')?.trim()||'');
 const [details,setDetails]=useState<CatalogTableRow|null>(null),[fields,setFields]=useState<Row[]>([]),[fieldError,setFieldError]=useState(''),[fieldLoading,setFieldLoading]=useState(false);
 const tableId=params.get('tableId');
 useEffect(()=>{if(!tableId)return;let active=true;void readCatalogTablesPage(undefined,undefined,'',1,1,undefined,tableId).then(result=>{if(active&&result.rows.length)setDetails(catalogTableRows({sources:[],tables:result.rows,columns:[],relations:[]})[0]);}).catch(cause=>{if(active)message.error('无法定位数据表：'+String(cause instanceof Error?cause.message:cause));});return()=>{active=false;};},[tableId]);
 const [edit,setEdit]=useState<{table:CatalogTableRow;directory?:RegisteredCatalog}>();const {busy,run}=useAction(),{modal,message}=App.useApp();
 const tree=useMemo(()=>buildCatalogTree(scope.data?.sources||[],scope.data||undefined),[scope.data]);
 const selectedNode=findCatalogNode(tree,selected);
 const sourceIds=selected==='all'?undefined:selectedNode?.sourceIds||[];
 const pageQuery=useWorkspaceQuery(`shared-catalog-page:${scope.data?'ready':'waiting'}:${selected}:${kind||''}:${keyword}:${page}:${pageSize}`,s=>scope.data?readCatalogTablesPage(sourceIds,kind,keyword,page,pageSize,s):Promise.resolve({rows:[],catalogs:[],total:0,page,size:pageSize}));
 const sourcesById=useMemo(()=>new Map((scope.data?.sources||[]).map(source=>[source.tid,source])),[scope.data]);
 useEffect(()=>{setExpanded(initialCatalogExpandedKeys(tree));},[tree]);
 useEffect(()=>{if(params.get('source'))setSelected('source:'+params.get('source'));},[params]);
 useEffect(()=>{setKeyword(params.get('q')?.trim()||'');setPage(1);},[params.get('q')]);
 useEffect(()=>{if(!details)return;let live=true;setFields([]);setFieldError('');setFieldLoading(true);void catalogTableColumns(details.tid).then(rows=>{if(live)setFields(rows);}).catch(e=>{if(live)setFieldError(String(e instanceof Error?e.message:e));}).finally(()=>{if(live)setFieldLoading(false);});return()=>{live=false;};},[details?.tid]);
 const rows=useMemo(()=>associateCatalogs(catalogTableRows({sources:[],tables:pageQuery.data?.rows||[],columns:[],relations:[]}),pageQuery.data?.catalogs||[]),[pageQuery.data]);
 const filteredTree=useMemo(()=>searchCatalogTree(tree,treeQuery),[tree,treeQuery]);
 const visibleExpanded=treeQuery.trim()?catalogSearchExpandedKeys(filteredTree):expanded;
 const refresh=()=>{invalidateMetadata();pageQuery.refresh();scope.refresh();};
 const remove=(directory:RegisteredCatalog)=>modal.confirm({title:'删除关联的数据目录？',content:'仅删除选定的目录及数据项关联；不删除来源数据表、已采集字段或源数据库数据。',okText:'删除目录',okButtonProps:{danger:true},onOk:async()=>{const r=await catalogRegistrationApi.remove(directory.tid);if(!r.deleted)throw new Error('目录未被删除，请刷新列表核对');if(r.indexRefreshPending)message.warning('目录已删除，搜索索引刷新待重试');else message.success('目录已删除，来源数据表已保留');pageQuery.refresh();}});
 const makeTree=(nodes:CatalogNode[]):any[]=>nodes.map(node=>({key:node.key,title:<TreeLabel node={node}/>,children:makeTree(node.children)}));
 return <Page title="元数据目录" actions={assetReturnTo&&<Button icon={<ArrowLeftOutlined/>} onClick={()=>nav(assetReturnTo)}>返回皓月资产中心</Button>}><div className="wx-shared-metadata-explorer"><aside className="platform-panel wx-metadata-tree"><header><strong>组织机构与数据来源</strong></header><Input allowClear prefix={<SearchOutlined/>} placeholder="搜索机构、系统或数据源" value={treeQuery} onChange={e=>setTreeQuery(e.target.value)}/><ErrorNotice error={scope.error} retry={scope.refresh}/><Tree blockNode expandedKeys={visibleExpanded} onExpand={setExpanded} selectedKeys={[selected]} onSelect={keys=>{setSelected(String(keys[0]||'all'));setPage(1);}} treeData={makeTree(filteredTree)}/></aside><div className="wx-metadata-table"><DataTable titleKey="shared-catalog-tables" rows={rows} loading={pageQuery.loading||scope.loading} error={pageQuery.error||scope.error} onRefresh={refresh} searchPlaceholder="搜索表名或说明，按回车查询" initialSearch={params.get('q')?.trim()||''} onSearch={value=>{setKeyword(value);setPage(1);}} serverPagination={{page,size:pageSize,total:pageQuery.data?.total||0,onChange:(nextPage,nextSize)=>{setPage(nextPage);setPageSize(nextSize);}}} actions={<><Select allowClear value={kind} placeholder="全部类型" onChange={value=>{setKind(value);setPage(1);}} style={{width:110}} options={[{value:'数据表',label:'数据表'},{value:'视图',label:'视图'}]}/>{params.get('source')&&<Button onClick={()=>{nav('/governance/metadata/catalog');setSelected('all');setPage(1);}}>全部来源</Button>}</>} columns={[
  {title:'数据表名称',width:260,render:(_,r)=><div className="wx-object-summary"><SourceIcon small engine={sourcesById.get(r.datasource_id)?.db_type}/><div><Button type="link" className="directory-name-link" onClick={()=>setDetails(r as CatalogTableRow)}>{r.displayName}</Button><small>{r.table_name}</small></div></div>},
  {title:'所属数据源',width:240,render:(_,r)=>{const source=sourcesById.get(r.datasource_id),sourceName=source?.db_name||'数据源信息待补全',appName=source?.app_name||'未关联应用系统';return <div className="wx-cell-stack wx-catalog-source-stack"><strong title={sourceName}>{sourceName}</strong><small title={appName}>{appName}</small></div>;}},
  {title:'数据目录',width:230,render:(_,r)=>r.directories.length?<Space direction="vertical">{r.directories.map((d:RegisteredCatalog)=><Button key={d.tid} type="link" onClick={()=>setEdit({table:r as CatalogTableRow,directory:d})}>{d.catalogName||'未命名目录'}</Button>)}</Space>:<Tag>未编目</Tag>},
  {title:'类型',dataIndex:'kind',width:95},{title:'字段数',dataIndex:'fieldCount',align:'right',width:95},{title:'表说明',dataIndex:'table_comment',width:220},
  {title:'操作',width:100,render:(_,r)=><div className="wx-catalog-actions">{!r.directories.length?<Button type="link" disabled={Boolean(pageQuery.error)||pageQuery.loading||busy} onClick={()=>setEdit({table:r as CatalogTableRow})}>登记目录</Button>:r.directories.map((d:RegisteredCatalog)=><div className="wx-catalog-action-pair" key={d.tid}><Button type="link" title={r.directories.length>1?`编辑：${d.catalogName}`:'编辑'} onClick={()=>setEdit({table:r as CatalogTableRow,directory:d})}>编辑</Button><Button type="link" danger title={r.directories.length>1?`删除：${d.catalogName}`:'删除'} onClick={()=>remove(d)}>删除</Button></div>)}</div>}
 ]}/></div></div><Drawer open={!!details} width={850} title={(details?.displayName||'')+' · 字段结构'} extra={details&&<Button onClick={()=>nav(`/governance/metadata/lineage?kind=TABLE&id=${encodeURIComponent(details.tid)}`)}>查看表血缘</Button>} onClose={()=>setDetails(null)}>{details&&<><Descriptions column={2} items={[{key:'name',label:'物理名称',children:details.table_name},{key:'type',label:'类型',children:details.kind},{key:'source',label:'数据源',children:sourcesById.get(details.datasource_id)?.db_name},{key:'comment',label:'说明',children:details.table_comment||'—'}]}/><ErrorNotice error={fieldError} retry={()=>run(async()=>setFields(await catalogTableColumns(details.tid)),'')}/><Table rowKey="tid" loading={fieldLoading} size="small" scroll={{x:'max-content'}} dataSource={fields} columns={[{title:'字段名',dataIndex:'column_name',render:(_,r)=><Button type="link" onClick={()=>nav(`/governance/metadata/lineage?kind=FIELD&id=${encodeURIComponent(r.tid)}`)}>{r.column_name}</Button>,sorter:(a,b)=>String(a.column_name||'').localeCompare(String(b.column_name||''),'zh-CN',{numeric:true})},{title:'类型',render:(_,r)=>r.column_type||r.data_type},{title:'长度',dataIndex:'length'},{title:'主键',render:(_,r)=>['1',true,1].includes(r.primary_key)?'是':'否'},{title:'可空',render:(_,r)=>r.nullable==null?'未知':['0',false,0].includes(r.nullable)?'否':'是'},{title:'说明',dataIndex:'column_comment'}]}/>{!fields.length&&!fieldLoading&&!fieldError&&<div className="wx-metadata-note">此表还没有已采集的字段快照，请到元数据采集页面同步字段；浏览目录不会自动连接源库。</div>}</>}</Drawer><CatalogEditor table={edit?.table} directory={edit?.directory} source={edit?.table?sourcesById.get(edit.table.datasource_id):undefined} scope={scope.data||undefined} onClose={()=>setEdit(undefined)} onSaved={()=>{setEdit(undefined);message.success('数据目录已保存到共享后端');pageQuery.refresh();}} onUncertainSave={pageQuery.refresh}/></Page>;
}

