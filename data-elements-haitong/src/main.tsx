import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HashRouter } from 'react-router-dom';
import { OceanTheme } from './design/theme';
import App from './app/App';
import { canonicalizeLegacyCanvasUrl } from './app/entryRoute';
import { initIframeBridge } from './nifi/api/iframeBridge';
import { registerNifiQueryClient } from './nifi/api/querySession';
import { startPlatformSessionSync } from './services/platformSession';
import './styles/global.css';
import './styles/task-table.css';
import './styles/hero.css';
// Migrate legacy document-query iframe links before HashRouter mounts. The
// bridge then strips token parameters while keeping this canvas selection.
const canonicalUrl = canonicalizeLegacyCanvasUrl(window.location.href);
if (canonicalUrl) {
 const normalized = new URL(canonicalUrl);
 window.history.replaceState(window.history.state, '', `${normalized.pathname}${normalized.search}${normalized.hash}`);
}
// Reuse the existing NiFi session bridge before any backend query mounts.
initIframeBridge();
startPlatformSessionSync();
const queryClient=new QueryClient();
registerNifiQueryClient(queryClient);
class ErrorBoundary extends React.Component<{children:React.ReactNode},{error?:Error}>{
 state:{error?:Error}={};
 static getDerivedStateFromError(error:Error){return {error};}
 render(){return this.state.error?<div className="fatal-error"><h1>页面未能完成加载</h1><p>{this.state.error.message}</p><p>工作区内容仍保存在浏览器中，不会自动清空。</p><button onClick={()=>window.location.reload()}>重新加载</button></div>:this.props.children;}
}
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><ErrorBoundary><QueryClientProvider client={queryClient}><OceanTheme><HashRouter><App/></HashRouter></OceanTheme></QueryClientProvider></ErrorBoundary></React.StrictMode>);
