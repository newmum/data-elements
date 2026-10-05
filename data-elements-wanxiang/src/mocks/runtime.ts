import { LocalMockEngine } from './engine';
import { MockError, type MockRequest } from './types';
let engine:LocalMockEngine|undefined;
let started=false;
function service(){if(!engine){let storage:Storage|undefined;try{storage=typeof localStorage==='undefined'?undefined:localStorage;}catch{throw new MockError(507,'STORAGE_UNAVAILABLE','浏览器没有开放本地存储，请允许当前站点存储数据。');}engine=new LocalMockEngine({storage});}return engine;}
export function startLocalRuntime(){if(started||typeof window==='undefined')return;started=true;let running=false;const tick=async()=>{if(running)return;running=true;try{if(await service().tick())window.dispatchEvent(new Event('wanxiang:local-changed'));}catch(e){window.dispatchEvent(new CustomEvent('wanxiang:local-error',{detail:e instanceof Error?e.message:String(e)}));}finally{running=false;}};window.setInterval(()=>{void tick();},600);window.addEventListener('focus',()=>{void tick();});window.addEventListener('storage',e=>{if(e.key==='wanxiang:local-workspace:3.1')window.dispatchEvent(new Event('wanxiang:local-changed'));});}
export async function localRequest<T>(path:string,options:MockRequest={}):Promise<T>{
  if(options.signal?.aborted)throw options.signal.reason??new DOMException('Aborted','AbortError');
  // A short cancellable delay exposes actual loading states without a network dependency.
  await new Promise<void>((resolve,reject)=>{const abort=()=>{clearTimeout(timer);reject(options.signal?.reason??new DOMException('Aborted','AbortError'));};const timer=setTimeout(()=>{options.signal?.removeEventListener('abort',abort);resolve();},45);options.signal?.addEventListener('abort',abort,{once:true});});
  return service().request<T>(path,options);
}
