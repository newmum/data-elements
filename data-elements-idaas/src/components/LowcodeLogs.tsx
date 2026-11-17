import { useLayoutEffect, useRef, useState } from 'react';
import * as Vue from 'vue';
import * as ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import type { Component } from 'vue';
import { foundationApi } from '../services/workspace';
import { WorkspaceLoading } from './WorkspaceLoading';
type Row = Record<string, unknown>;
type LogClient = {
 domain: string;
 request: (method: 'GET' | 'POST', url: string, data: Row) => Promise<unknown>;
 download: (id: string) => Promise<void>;
};

function requestLog(method: 'GET' | 'POST', url: string, data: Row): Promise<unknown> {
 if (method === 'POST') return foundationApi.write(url, data);
 const query = new URLSearchParams();
 for (const [key, value] of Object.entries(data)) {
  if (value !== undefined && value !== null) query.set(key, String(value));
 }
 return foundationApi.read(query.size ? url + '?' + query : url);
}
declare global { interface Window { __logClient?:LogClient; ___magic__import__?:(library:string,name:string)=>unknown; } }
const titles:Record<string,string>={management:'日志管理'};

/** Host only. The single tabbed log page is a published low-code resource. */
export default function LowcodeLogs({kind='management',domain='workforce'}:{kind?:string;domain?:string}) {
 const host=useRef<HTMLDivElement>(null);
 const pending=useRef<{domain:string;result:Promise<unknown>} | null>(null);
 const [failure,setFailure]=useState('');const [retry,setRetry]=useState(0);const [loading,setLoading]=useState(true);
 useLayoutEffect(()=>{
  const container=host.current;
  if(!container)return;
  let disposed=false;let app:Vue.App|undefined;const styles:HTMLStyleElement[]=[];
  const previousClient=window.__logClient;const previousImport=window.___magic__import__;
  const client:LogClient={domain,request:requestLog,download:id=>foundationApi.downloadLog(id,domain)};
  const libraries:Record<string,Record<string,unknown>>={vue:Vue,'element-plus':ElementPlus};
  const importLibrary=(library:string,name:string)=>{
   const value=libraries[library];if(!value)throw new Error('日志组件依赖不可用：'+library);
   return name==='*'?value:name==='default'?(value.default??value):value[name];
  };
  window.__logClient=client;setFailure('');setLoading(true);
  void (async()=>{
   try {
    // React may detach and reattach this host while the route loads. Only reuse its in-flight read.
    if(pending.current?.domain!==domain) {
     const read={domain,result:client.request('GET','/idaas/logs/query',{domain,kind:'auth',operation:'components'})};
     pending.current=read;
     const complete=()=>{if(pending.current===read)pending.current=null;};
     void read.result.then(complete,complete);
    }
    const components=await pending.current.result as Row[];
    if(disposed)return;
    window.___magic__import__=importLibrary;
    const compiled=new Map<string,Component>();
    for(const resource of components) {
     if(!String(resource.name).startsWith('log-')||!String(resource.compile_js||'').includes('return __sfc__'))throw new Error('日志组件尚未发布完整，请联系管理员');
     compiled.set(String(resource.name),new Function(String(resource.compile_js))() as Component);
     const style=document.createElement('style');style.dataset.logRuntime=String(resource.name);style.textContent=String(resource.compile_css||'');document.head.append(style);styles.push(style);
    }
    const selected=compiled.get('log-management');if(!selected)throw new Error('日志模块尚未发布');
    app=Vue.createApp({render:()=>Vue.h(selected)});app.use(ElementPlus.default,{locale:zhCn});
    for(const [name,component]of compiled)app.component(name,component);
    app.config.errorHandler=(error)=>{if(!disposed)setFailure(error instanceof Error?error.message:'日志页面加载失败');};
    app.mount(container);
   } catch(error) {if(!disposed)setFailure(error instanceof Error?error.message:'日志页面加载失败');}
   finally {if(!disposed)setLoading(false);}
  })();
  return()=>{disposed=true;app?.unmount();styles.forEach(style=>style.remove());if(window.__logClient===client)window.__logClient=previousClient;if(window.___magic__import__===importLibrary)window.___magic__import__=previousImport;};
 },[kind,domain,retry]);
 return <section aria-label={titles[kind]||'日志管理'} style={{minHeight:0,minWidth:0}}>{loading&&<WorkspaceLoading label="正在载入日志管理"/>}{failure&&<div className="page-title"><h1 className="page-heading">{titles[kind]||'日志管理'}</h1></div>}{failure&&<div role="alert" style={{padding:24,color:'#b43d4b',background:'#fff5f5',borderRadius:10}}>{failure}<button type="button" onClick={()=>setRetry(value=>value+1)} style={{marginLeft:16}}>重新加载</button></div>}<div ref={host}/></section>;
}

export async function saveBlob(response:Response,id:string) {
 if(!response.ok||/^application\/(?:[\w.-]+\+)?json(?:;|$)/i.test(response.headers.get('content-type')||'')) {
  const body=await response.json().catch(()=>({}));throw new Error(body.message||body.msg||'日志文件下载失败，请刷新后重试');
 }
 const blob=await response.blob();const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='日志-'+id+(response.headers.get('content-disposition')?.includes('.jsonl')?'.jsonl':'.csv');a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
