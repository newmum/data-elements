import { useEffect } from 'react';
import { ApiClientError } from '@/services/client';
import { resolveParentOrigin } from '@/services/embeddedSession';
import './OrchestrationState.css';

export function notifyEditorState(type: 'YUNTI_EDITOR_READY' | 'YUNTI_EDITOR_ERROR', payload?: { code?: number; message: string }) {
  if (window.parent === window) return;
  const origin = resolveParentOrigin(window.location.href, document.referrer);
  if (origin) window.parent.postMessage({ type, payload: { entryId: new URLSearchParams(window.location.search).get('entryId'), ...payload } }, origin);
}

export function describeOrchestrationError(error: unknown) {
  const code = error instanceof ApiClientError ? error.code : 0;
  const raw = error instanceof Error ? error.message : String(error || '');
  const details = raw.slice(0, 2000)
    .replace(/(Bearer\s+)[\w.\-]+/gi, '$1[已隐藏]')
    .replace(/((?:token|password|secret|api[_-]?key)\s*[:=]\s*)[^\s&,;]+/gi, '$1[已隐藏]');
  if (code === 404) return { code, title: '暂时无法访问这个流程', description: '流程可能已移除，或不在当前账号的数据范围内。请返回数据服务重新选择。', details };
  if (code === 401 || code === 403 || code === 100120) return { code, title: '请重新确认登录状态', description: '当前登录已失效或没有访问权限，请返回数据服务重新登录后打开。', details };
  if (code === 409) return { code, title: '编排内容需要检查', description: '当前服务的流程关联或编排内容存在冲突，请返回数据服务检查后重试。', details };
  return { code, title: '服务编排暂时无法打开', description: '连接暂时中断或服务未能及时响应。请稍后重试，或返回数据服务。', details };
}

export default function OrchestrationState({ error, onRetry, onReturn }: {
  error?: unknown; onRetry?: () => void; onReturn?: () => void;
}) {
  const failure = error ? describeOrchestrationError(error) : null;
  useEffect(() => {
    if (failure) notifyEditorState('YUNTI_EDITOR_ERROR', { code: failure.code, message: failure.details || failure.description });
  }, [failure?.code, failure?.details, failure?.description]);
  return <section className={`flow-entry${failure ? ' flow-entry--error' : ''}`} role={failure ? 'alert' : 'status'} aria-live="polite" aria-busy={!failure}>
    <div className="flow-entry__card">
      <div className="flow-entry__eyebrow"><i />云梯 · 服务编排</div>
      <div className="flow-entry__visual" aria-hidden="true">
        <div className="flow-entry__guide" />
        <div className="flow-entry__wire flow-entry__wire--first"><i /></div>
        <div className="flow-entry__wire flow-entry__wire--second"><i /></div>
        <div className="flow-entry__node flow-entry__node--input"><svg viewBox="0 0 24 24"><path d="M5 5h14v14H5zM9 9l3 3-3 3m5-3h2" /></svg><span>请求</span></div>
        <div className="flow-entry__node flow-entry__node--core"><svg viewBox="0 0 24 24"><path d="M5 7h14M5 17h14M8 4v6m8 4v6M5 12h14m-3-3v6" /></svg><span>编排</span></div>
        <div className="flow-entry__node flow-entry__node--output"><svg viewBox="0 0 24 24"><path d="M5 5h14v14H5zM8 12l3 3 5-6" /></svg><span>响应</span></div>
        {failure && <span className="flow-entry__notice">!</span>}
      </div>
      <h1>{failure?.title || '正在打开服务编排'}</h1>
      <p className="flow-entry__description">{failure?.description || '连接服务节点，准备你的编排画布'}</p>
      {failure ? <>
        <div className="flow-entry__actions">
          {onRetry && <button className="flow-entry__button flow-entry__button--primary" onClick={onRetry}>重新加载</button>}
          {onReturn && <button className="flow-entry__button" onClick={onReturn}>返回数据服务</button>}
        </div>
        {failure.details && <details className="flow-entry__details"><summary>查看错误详情</summary><pre>{failure.details}</pre></details>}
      </> : <div className="flow-entry__progress" aria-hidden="true"><i /><i /><i /></div>}
      <div className="flow-entry__footer">连接数据，让服务有序流转</div>
    </div>
  </section>;
}
