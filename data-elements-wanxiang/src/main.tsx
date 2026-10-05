import { Component, StrictMode, useLayoutEffect } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { App as AntApp, Button, ConfigProvider, Result } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { createHashRouter, RouterProvider, useLocation } from 'react-router-dom';
import App from './app/App';
import { useStudio } from './features/er/store';
import '@xyflow/react/dist/style.css';
import 'antd/dist/reset.css';
import './features/er/styles/app.css';
import './features/er/styles/wanxiang.css';
import { productTheme } from './design/theme';
import './styles/experience.css';
import './styles/platform.css';
import './styles/refined.css';
import './styles/studio-polish.css';
import './styles/platform-integration.css';
import './styles/startup.css';
import './styles/typography.css';
import { resourceTheme } from './panshi/design/theme';
import { assetTheme } from './features/assets/theme';
import './shared/centers/switcher.css';
import './panshi/styles/resource.css';
import './panshi/styles/clarity.css';
import './panshi/styles/integration.css';
import './features/assets/assets.css';
class ErrorBoundary extends Component<{
    children: ReactNode;
}, {
    error: string | null;
}> {
    state: {
        error: string | null;
    } = { error: null };
    static getDerivedStateFromError(error: Error) { return { error: error.message }; }
    componentDidCatch(error: Error, info: ErrorInfo) { console.error('Wanxiang render error', error, info); }
    render() { return this.state.error ? <Result status="error" title="页面遇到了问题" subTitle={this.state.error} extra={<Button onClick={() => location.reload()}>重新加载本地工作区</Button>}/> : this.props.children; }
}
function CenterRoot() {
    const mode = useStudio(s => s.theme);
    const location = useLocation(), resource = /^\/resource(?:\/|$)/.test(location.pathname), asset = /^\/assets(?:\/|$)/.test(location.pathname);
    useLayoutEffect(() => { document.documentElement.dataset.center = resource ? 'resource' : asset ? 'asset' : 'governance'; document.documentElement.dataset.resourceTheme = mode; }, [resource, asset, mode]);
    return <ConfigProvider locale={zhCN} componentSize="middle" theme={resource ? resourceTheme(mode === 'dark') : asset ? assetTheme(mode) : productTheme(mode)}><AntApp><ErrorBoundary><App/></ErrorBoundary></AntApp></ConfigProvider>;
}
const router = createHashRouter([{ path: '*', element: <CenterRoot /> }]);
const root = document.getElementById('root');
if (!root)
    throw new Error('缺少 #root 挂载节点');
createRoot(root).render(<StrictMode><RouterProvider router={router}/></StrictMode>);
