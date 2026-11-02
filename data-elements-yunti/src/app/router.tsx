import OrchestrationState from '@/components/OrchestrationState';
import { ApiClientError } from '@/services/client';
import { resolveParentOrigin } from '@/services/embeddedSession';
import { createBrowserRouter, useRouteError, useNavigate } from 'react-router-dom';
import { lazy, Suspense, type ReactNode } from 'react';
import AppLayout from './AppLayout';
import FlowListPage from '@/pages/flow-list/FlowListPage';
import DocsPage from '@/pages/docs/DocsPage';
import RuntimeNodePage from '@/pages/runtime-node/RuntimeNodePage';

const FlowEditorPage = lazy(() => import('@/pages/flow-editor/FlowEditorPage'));
const MonitorPage = lazy(() => import('@/pages/monitor/MonitorPage'));

function lazyPage(element: ReactNode) {
  return <Suspense fallback={<OrchestrationState />}>{element}</Suspense>;
}

function RouteFailure({ notFound = false }: { notFound?: boolean }) {
  const error = useRouteError();
  const navigate = useNavigate();
  return <OrchestrationState error={notFound ? new ApiClientError(404, '当前编排页面不存在') : error || new Error('页面暂时无法显示')}
    onRetry={() => window.location.reload()} onReturn={() => {
      const origin = resolveParentOrigin(window.location.href, document.referrer);
      if (window.parent !== window && origin) window.parent.postMessage({ type: 'YUNTI_APP_CLOSE' }, origin);
      else navigate('/');
    }} />;
}

export const router = createBrowserRouter(
  [
    {
      path: '/',
      element: <AppLayout />,
      errorElement: <RouteFailure />,
      children: [
        { index: true, element: <FlowListPage /> },
        { path: 'runtime-nodes', element: <RuntimeNodePage /> },
        { path: 'monitor', element: lazyPage(<MonitorPage />) },
        { path: 'docs', element: <DocsPage /> },
        { path: 'docs/:flowId', element: <DocsPage /> },
      ],
    },
    // Editor is full-bleed (its own chrome), outside AppLayout.
    { path: '/editor/:flowId', element: lazyPage(<FlowEditorPage />), errorElement: <RouteFailure /> },
    { path: '*', element: <RouteFailure notFound /> },
  ],
  // Honor Vite's deploy base (e.g. '/yunti/') so client routing works under a sub-path.
  { basename: import.meta.env.BASE_URL.replace(/\/$/, '') || '/' },
);
