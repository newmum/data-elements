import { useEffect } from 'react';
export const draftState:{dirty:boolean;save?:()=>Promise<void>}={dirty:false};
export function useDraftGuard(dirty:boolean,save:()=>Promise<void>){useEffect(()=>{draftState.dirty=dirty;draftState.save=save;const unload=(e:BeforeUnloadEvent)=>{if(draftState.dirty){e.preventDefault();e.returnValue='';}};window.addEventListener('beforeunload',unload);return()=>{draftState.dirty=false;draftState.save=undefined;window.removeEventListener('beforeunload',unload);};},[dirty,save]);}
