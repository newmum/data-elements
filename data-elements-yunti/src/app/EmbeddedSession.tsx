import { Button, Space, Typography } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { queryClient } from './queryClient';
import { requestToken, resolveParentOrigin, setEmbeddedToken } from '@/services/embeddedSession';

/** Mount API consumers only after the authenticated parent initializes this iframe. */
export default function EmbeddedSession({ children }: { children: ReactNode }) {
  const [context] = useState(() => ({
    embedded: window.parent !== window,
    origin: resolveParentOrigin(window.location.href, document.referrer),
  }));
  const [ready, setReady] = useState(!context.embedded);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!context.embedded) return;
    const parentOrigin = context.origin;
    if (!parentOrigin) {
      setError('无法确认主平台地址，请从数据服务列表重新打开画布。');
      return;
    }
    const timer = window.setTimeout(() => setError('未收到主平台登录信息，请关闭画布后重新打开。'), 12000);
    let initialized = false;
    const onMessage = (event: MessageEvent<unknown>) => {
      if (event.source !== window.parent || event.origin !== parentOrigin) return;
      if (!event.data || typeof event.data !== 'object' || !('type' in event.data) || event.data.type !== 'INIT') return;
      const payload = 'payload' in event.data ? event.data.payload : null;
      const token = payload && typeof payload === 'object' && 'token' in payload ? payload.token : null;
      if (typeof token !== 'string' || !token.trim()) {
        setError('主平台登录已失效，请返回主平台重新登录后打开画布。');
        return;
      }
      if (initialized && requestToken() !== token) {
        setReady(false);
        setError('登录信息已变化，请关闭并重新打开画布，以加载当前会话的数据。');
        return;
      }
      if (!initialized) {
        queryClient.clear();
        setEmbeddedToken(token);
        initialized = true;
      }
      window.clearTimeout(timer);
      setError('');
      setReady(true);
      window.parent.postMessage({ type: 'YUNTI_SESSION_READY' }, parentOrigin);
    };
    window.addEventListener('message', onMessage);
    window.parent.postMessage({ type: 'YUNTI_APP_READY' }, parentOrigin);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('message', onMessage);
      setEmbeddedToken(null);
    };
  }, [context]);

  if (ready && !error) return children;
  return <div style={{ height: '100vh', display: 'grid', placeItems: 'center', background: '#f5f7fa' }}>
    <Space orientation="vertical" align="center" size={16} role={error ? 'alert' : 'status'}>
      <Typography.Text strong>{error ? '画布暂时无法加载' : '正在连接主平台登录会话…'}</Typography.Text>
      {error && <Typography.Text type="secondary">{error}</Typography.Text>}
      {error && context.origin && <Button onClick={() => {
        if (context.origin) window.parent.postMessage({ type: 'YUNTI_APP_CLOSE' }, context.origin);
      }}>返回数据服务</Button>}
    </Space>
  </div>;
}
