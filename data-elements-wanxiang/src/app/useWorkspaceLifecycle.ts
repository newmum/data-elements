import { useEffect } from 'react';
import { useStudio } from '../features/er/store';
import { useLocation } from 'react-router-dom';
/** Persist only model layout changes. Semantic mutations use their own server transactions. */
export function useWorkspaceLifecycle() {
 const ready=useStudio(s=>s.ready),revision=useStudio(s=>s.revision),dragging=useStudio(s=>s.dragging),saveState=useStudio(s=>s.saveState),theme=useStudio(s=>s.theme);
 const location=useLocation();const needsMetadata=location.pathname.includes('/metadata/er')||location.pathname.includes('/metadata/models');
 useEffect(()=>{if(needsMetadata)void useStudio.getState().initialize();},[needsMetadata]);
 useEffect(()=>{if(!ready||dragging||saveState!=='dirty')return;const id=setTimeout(()=>{void useStudio.getState().save();},900);return()=>clearTimeout(id);},[ready,dragging,saveState,revision]);
 useEffect(()=>{document.documentElement.dataset.theme=theme;document.documentElement.style.colorScheme=theme;document.querySelector('meta[name="theme-color"]')?.setAttribute('content',theme==='dark'?'#131923':'#f5f7fa');},[theme]);
 useEffect(()=>{const handler=(event:BeforeUnloadEvent)=>{const s=useStudio.getState();if(['dirty','saving','error'].includes(s.saveState)||s.editor){event.preventDefault();event.returnValue='';}};window.addEventListener('beforeunload',handler);return()=>window.removeEventListener('beforeunload',handler);},[]);
}
