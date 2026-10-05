import { useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { dispatch, readWorkspace } from '../services/workspace';
import type { Action } from '../domain/engine';
export function useWorkspace(){
 const client=useQueryClient();const q=useQuery({queryKey:['workspace'],queryFn:readWorkspace,staleTime:1000,retry:0});
 const act=async(action:Action)=>{const result=await dispatch(action);client.setQueryData(['workspace'],result);return result;};
 return {...q,act};
}
export function useWorkspaceRuntime(onError:(e:unknown)=>void){
 const client=useQueryClient();const busy=useRef(false);const handler=useRef(onError);handler.current=onError;
 useEffect(()=>{const update=()=>void client.invalidateQueries({queryKey:['workspace']});window.addEventListener('haitong:updated',update);
 const timer=setInterval(async()=>{if(busy.current||document.hidden)return;busy.current=true;try{const s=await readWorkspace();const now=Date.now();const work=s.runs.some(r=>['QUEUED','RUNNING'].includes(r.status))||s.batches.some(b=>b.status==='RUNNING')||s.tasks.some(t=>!t.paused&&!t.archived&&t.published&&t.schedule.nextAt&&Date.parse(t.schedule.nextAt)<=now);if(work){const next=await dispatch({type:'TICK'});client.setQueryData(['workspace'],next);}}catch(e){handler.current(e);}finally{busy.current=false;}},750);
 return()=>{clearInterval(timer);window.removeEventListener('haitong:updated',update);};},[client]);
}
