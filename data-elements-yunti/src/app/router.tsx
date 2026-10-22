import { createBrowserRouter } from 'react-router-dom';
import { lazy, Suspense, type ReactNode } from 'react';
import AppLayout from './AppLayout';
import FlowListPage from '@/pages/flow-list/FlowListPage';
import DocsPage from '@/pages/docs/DocsPage';
import RuntimeNodePage from '@/pages/runtime-node/RuntimeNodePage';

const FlowEditorPage = lazy(() => import('@/pages/flow-editor/FlowEditorPage'));
const MonitorPage = lazy(() => import('@/pages/monitor/MonitorPage'));

function lazyPage(element: ReactNode) {
  return <Suspense fallback={<div style={{ padding: 24 }}>加载中...</div>}>{element}</Suspense>;
}

export const router = createBrowserRouter(
  [
    {
      path: '/',
      element: <AppLayout />,
      children: [
        { index: true, element: <FlowListPage /> },
        { path: 'runtime-nodes', element: <RuntimeNodePage /> },
        { path: 'monitor', element: lazyPage(<MonitorPage />) },
        { path: 'docs', element: <DocsPage /> },
        { path: 'docs/:flowId', element: <DocsPage /> },
      ],
    },
    // Editor is full-bleed (its own chrome), outside AppLayout.
    { path: '/editor/:flowId', element: lazyPage(<FlowEditorPage />) },
  ],
  // Honor Vite's deploy base (e.g. '/yunti/') so client routing works under a sub-path.
  { basename: import.meta.env.BASE_URL.replace(/\/$/, '') || '/' },
);
