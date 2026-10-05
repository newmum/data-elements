/** Development-only UI harness. No API, authentication or business-data writes. */
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { App, ConfigProvider } from 'antd';
import StartupScene from '../../src/app/StartupScene';
import { productTheme } from '../../src/design/theme';
import 'antd/dist/reset.css';
import '../../src/styles/refined.css';
import '../../src/styles/startup.css';

if (!import.meta.env.DEV) throw new Error('此隔离验收页仅用于本地开发，不是业务入口');
const params = new URLSearchParams(location.search);
const mode = params.get('theme') === 'dark' ? 'dark' : 'light';
document.documentElement.dataset.theme = mode;
const scenarios: Record<string, Error> = {
  sync: new Error('登录态同步超时，请检查数据中台地址后重试'),
  network: new Error('无法连接数据中台，请检查网络或服务状态'),
  timeout: Object.assign(new Error('请求超时，请稍后重试'), { timedOut: true }),
  service: Object.assign(new Error('接口请求失败（503）'), { status: 503 }),
  permission: Object.assign(new Error('接口请求失败（403）'), { status: 403 }),
  authentication: Object.assign(new Error('登录已失效'), { status: 401 }),
};
function Preview() {
  const [retrying, setRetrying] = useState(false);
  const state = params.get('state') ?? 'loading';
  const phase = params.get('phase') === 'workspace' ? 'workspace' : params.get('phase') === 'module' ? 'module' : 'session';
  const error = scenarios[state];
  return <ConfigProvider theme={productTheme(mode)}><App>{error && !retrying ? <StartupScene state="error" error={error} onRetry={() => setRetrying(true)}/> : <StartupScene state="loading" phase={phase} contained={params.get('contained') === 'true'}/>}</App></ConfigProvider>;
}
createRoot(document.getElementById('root')!).render(<Preview/>);
