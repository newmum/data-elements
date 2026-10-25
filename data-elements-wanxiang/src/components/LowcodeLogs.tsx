import { useEffect, useRef, useState } from 'react';
import * as Vue from 'vue';
import * as ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import type { Component } from 'vue';
import { platformApi } from '../shared/platformApi';
import { requirePlatformSessionToken } from '../shared/platformSession';
const apiBase=(import.meta.env.VITE_DATA_ELEMENTS_API_BASE_URL || (import.meta.env.DEV?'/dev-api':'/prod-api')).replace(/\/$/,'');
async function requestLog(method:string,path:string,data:Row,_domain?:string,_activeKind?:string):Promise<unknown> {
 if(method==='GET') {const query=new URLSearchParams(Object.entries(data).filter(([,value])=>value!==undefined&&value!==null).map(([key,value])=>[key,String(value)]));return platformApi('/sym/logs'+path+'?'+query);}
 return platformApi('/sym/logs'+path,data,{method:method as 'POST'|'PUT'});
}
async function downloadLog(id:string,_domain:string) {const token=await requirePlatformSessionToken();await saveBlob(await fetch(apiBase+'/sym/logs/exports/'+encodeURIComponent(id)+'/download',{headers:{token},cache:'no-store'}),id);}


type Row = Record<string, unknown>;
type LogClient = { request:(method:string,path:string,data?:Row)=>Promise<unknown>;download:(id:string)=>Promise<void> };
declare global { interface Window { __logClient?:LogClient; ___magic__import__?:(library:string,name:string)=>unknown; } }
const titles:Record<string,string>={management:'日志管理'};

/** Host only. The single tabbed log page is a published low-code resource. */
export default function LowcodeLogs({kind='management',domain='workforce'}:{kind?:string;domain?:string}) {
 const host=useRef<HTMLDivElement>(null);const [failure,setFailure]=useState('');const [retry,setRetry]=useState(0);
 useEffect(()=>{
  let disposed=false;let app:Vue.App|undefined;const styles:HTMLStyleElement[]=[];
  const previousClient=window.__logClient;const previousImport=window.___magic__import__;
  const client:LogClient={request:(method,path,data={})=>requestLog(method,path,data,domain,kind),download:id=>downloadLog(id,domain)};
  window.__logClient=client;setFailure('');
  void (async()=>{
   try {
    const components=await client.request('GET','/components',{kind:'auth'}) as Row[];
    if(disposed||!host.current)return;
    const libraries:Record<string,Record<string,unknown>>={vue:Vue,'element-plus':ElementPlus};
    window.___magic__import__=(library,name)=>{
     const value=libraries[library];if(!value)throw new Error('日志组件依赖不可用：'+library);
     return name==='*'?value:name==='default'?(value.default??value):value[name];
    };
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
    app.mount(host.current);
   } catch(error) {if(!disposed)setFailure(error instanceof Error?error.message:'日志页面加载失败');}
  })();
  return()=>{disposed=true;app?.unmount();styles.forEach(style=>style.remove());if(window.__logClient===client)window.__logClient=previousClient;window.___magic__import__=previousImport;};
 },[kind,domain,retry]);
 return <section aria-label={titles[kind]||'日志管理'} style={{minHeight:0,minWidth:0}}>{failure&&<div className="page-title"><h1 className="page-heading">{titles[kind]||'日志管理'}</h1></div>}{failure&&<div role="alert" style={{padding:24,color:'#b43d4b',background:'#fff5f5',borderRadius:10}}>{failure}<button type="button" onClick={()=>setRetry(value=>value+1)} style={{marginLeft:16}}>重新加载</button></div>}<div ref={host}/></section>;
}

export async function saveBlob(response:Response,id:string) {
 if(!response.ok||/^application\/(?:[\w.-]+\+)?json(?:;|$)/i.test(response.headers.get('content-type')||'')) {
  const body=await response.json().catch(()=>({}));throw new Error(body.message||body.msg||'日志文件下载失败，请刷新后重试');
 }
 const blob=await response.blob();const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='日志-'+id+(response.headers.get('content-disposition')?.includes('.jsonl')?'.jsonl':'.csv');a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
