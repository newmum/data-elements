import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react';
import StartupScene from './StartupScene';
import { loadSession, type Session } from '../services/api';
import { redirectToPlatformLogin } from '../shared/platformSession';
import { useStudio } from '../features/er/store';
export function clearWorkspaceView() { useStudio.setState({ data:null,ready:false,loadError:null,panel:null,editor:null,past:[],future:[],saveState:'saved',initialized:false,job:null,jobController:null,selectedEntityId:null,selectedRelationshipId:null }); }
/** The real platform session gate covers every route, not just the migrated page. */
export default function AuthGate({ children }: { children:ReactNode }) {
 const [session,setSession]=useState<Session|null>(null),[error,setError]=useState<Error|null>(null);
 const sessionContext=useRef(''),loadingSession=useRef(false);
 const mode=useStudio(s=>s.theme);
 const contextKey=(value:Session)=>JSON.stringify([value.principalId,value.tenant?.tid||value.tenant?.tenantId,value.name,value.department,value.user?.roles,value.user?.perms]);
 const reload=async()=>{if(loadingSession.current)return;loadingSession.current=true;setError(null);try{const next=await loadSession(),context=contextKey(next);if(sessionContext.current&&context!==sessionContext.current)clearWorkspaceView();sessionContext.current=context;setSession(next);}catch(e){setSession(null);setError(e instanceof Error?e:new Error(String(e)));}finally{loadingSession.current=false;}};
 useEffect(()=>{void reload();const expired=()=>{setSession(null);clearWorkspaceView();redirectToPlatformLogin();};const focus=()=>{void reload();};window.addEventListener('wanxiang:session-expired',expired);window.addEventListener('focus',focus);return()=>{window.removeEventListener('wanxiang:session-expired',expired);window.removeEventListener('focus',focus);};},[]);
 useEffect(()=>{document.documentElement.dataset.theme=mode;document.documentElement.dataset.colorScheme=mode;},[mode]);
 if(error)return <StartupScene state="error" error={error} onRetry={()=>void reload()}/>;
 if(!session)return <StartupScene state="loading" phase="session"/>;
 // A platform account/tenant switch must discard the former page's queries,
 // pending edits and permission presentation, not merely replace the header.
 return <Fragment key={contextKey(session)}>{children}</Fragment>;
}
