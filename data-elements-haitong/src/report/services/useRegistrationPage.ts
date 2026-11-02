import {useEffect,useState} from 'react';
import {getSessionRevision} from '../../nifi/api/bridgeSession';
import {loadRegistrationFilesPage,loadReportTablesPage,type RegistrationPageQuery,type RegistrationFilePage,type ReportTablePage,type UploadManifest} from './uploadWorkbench';

type Page={revision:string;total:number;list:Array<{key:string}>};
function usePage<T extends Page>(dbId:string,manifest:UploadManifest,search:string,read:(id:string,m:UploadManifest,q:RegistrationPageQuery,filter:string)=>Promise<T>,filter=''){
  const [query,setQuery]=useState<RegistrationPageQuery>({pageNo:1,pageSize:100,search:'',sortField:'updatedAt',sortDir:'desc'});
  const [result,setResult]=useState<T>(),[loading,setLoading]=useState(false),[error,setError]=useState(''),[attempt,retry]=useState(0);
  useEffect(()=>{setQuery(current=>({...current,pageNo:1,search}));},[search,dbId,filter]);
  useEffect(()=>{
    if(!dbId)return;
    let active=true;const session=getSessionRevision();setLoading(true);setError('');
    const timer=setTimeout(()=>{void read(dbId,manifest,query,filter).then(page=>{
      if(!active||session!==getSessionRevision())return;
      const last=Math.max(1,Math.ceil(page.total/query.pageSize));
      if(query.pageNo>last){setQuery(current=>({...current,pageNo:last}));return;}
      setResult(page);
    }).catch(cause=>{if(active&&session===getSessionRevision()){setResult(undefined);setError(cause.message);}})
      .finally(()=>{if(active)setLoading(false);});},180);
    return()=>{active=false;clearTimeout(timer);};
  },[dbId,manifest.revision,query,attempt,filter,read]);
  return {result:result?.revision===manifest.revision?result:undefined,total:result?.revision===manifest.revision?result.total:0,loading,error,query,setQuery,retry:()=>retry(value=>value+1)};
}
export function useRegistrationFilesPage(dbId:string,manifest:UploadManifest,search:string){
  const page=usePage<RegistrationFilePage>(dbId,manifest,search,loadRegistrationFilesPage);
  return {...page,groups:page.result?.list||[]};
}
export function useReportTablesPage(dbId:string,manifest:UploadManifest,search:string,filter:string){
  // The caller supplies the persisted manifest, never the registration editor draft.
  const saved=manifest;
  const page=usePage<ReportTablePage>(dbId,saved,search,loadReportTablesPage,filter),byKey=new Map(saved.tables.map(table=>[table.key,table]));
  return {...page,summary:page.result?.summary||{all:0,pending:0,reported:0},rows:(page.result?.list||[]).flatMap(row=>{const table=byKey.get(row.key);return table?[{...table,updatedAt:row.updatedAt}]:[];})};
}
