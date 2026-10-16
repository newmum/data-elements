import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Drawer, Input, Space, Typography } from 'antd';
import { getNifiOverlayContainer } from './overlayContainer';
import { getSessionRevision, onNifiSessionChange } from '../api/bridgeSession';
import { diagnosticJson, diagnosticRequest, readDiagnosticStream, type DiagnosticEvidence, type DiagnosticSelection } from '../api/diagnostics';

type ChatMessage = { role: 'user' | 'assistant'; text: string };
export default function DiagnosticDrawer({ selection, onClose }: { selection: DiagnosticSelection | null; onClose: () => void }) {
  const [messages, setMessages] = useState<ChatMessage[]>([]), [evidence, setEvidence] = useState<DiagnosticEvidence[]>([]);
  const [question, setQuestion] = useState(''), [busy, setBusy] = useState(false), [status, setStatus] = useState(''), [failure, setFailure] = useState('');
  const active = useRef<AbortController | null>(null), session = useRef(''), generation = useRef(''), sequence = useRef(0);
  const revision = useRef(getSessionRevision()), isBusy = useRef(false);
  async function cancelRemote(id: string, requestId: string, sessionRevision: number) {
    if (!id || !requestId || sessionRevision !== getSessionRevision()) return;
    await diagnosticJson('/' + id + '/messages/' + requestId, 'DELETE', undefined, AbortSignal.timeout(5000)).catch(() => {});
  }
  function reset() {
    sequence.current++; active.current?.abort(); active.current = null;
    void cancelRemote(session.current, generation.current, revision.current);
    session.current = ''; generation.current = ''; isBusy.current = false;
  }
  async function stop() {
    sequence.current++; active.current?.abort(); active.current = null;
    setStatus('已停止，已返回内容可能不完整');
    await cancelRemote(session.current, generation.current, revision.current);
    isBusy.current = false; setBusy(false);
  }
  async function ask(initial = false) {
    if (!selection || isBusy.current) return;
    const text = initial ? '请分析这次故障，并给出有证据的排查建议。' : question.trim();
    if (!text) return;
    const version = ++sequence.current, controller = new AbortController(); active.current = controller;
    isBusy.current = true; setBusy(true); setFailure(''); setQuestion(''); setStatus('正在创建诊断会话');
    const current = () => sequence.current === version && revision.current === getSessionRevision();
    try {
      if (!session.current) {
        const result = await diagnosticJson('', 'POST', selection, controller.signal);
        if (!current()) return;
        session.current = result.sessionId;
      }
      setMessages(previous => [...previous, { role: 'user', text }, { role: 'assistant', text: '' }]);
      generation.current = Array.from(crypto.getRandomValues(new Uint8Array(16)), value => value.toString(16).padStart(2, '0')).join('');
      const response = await diagnosticRequest('/' + session.current + '/messages', 'POST', { message: text, generationId: generation.current }, controller.signal);
      await readDiagnosticStream(response, controller.signal, ({ event, data }) => {
        if (!current()) return;
        if (event === 'evidence' && Array.isArray(data)) setEvidence(data);
        if (event === 'progress' && !Array.isArray(data)) setStatus(String(data.message || ''));
        if (event === 'delta' && !Array.isArray(data)) setMessages(previous => previous.map((item, index) => index === previous.length - 1 ? { ...item, text: item.text + String(data.text || '') } : item));
        if (event === 'done') setStatus('本次分析完成，可继续追问');
      });
    } catch (error) {
      if (current() && !controller.signal.aborted) { setFailure(error instanceof Error ? error.message : '诊断请求失败'); setStatus(''); }
    } finally { if (current()) { isBusy.current = false; setBusy(false); active.current = null; } }
  }
  useEffect(() => {
    reset(); setMessages([]); setEvidence([]); setQuestion(''); setStatus(''); setFailure(''); setBusy(false);
    revision.current = getSessionRevision();
    if (selection) void ask(true);
    return reset;
  }, [selection]);
  useEffect(() => onNifiSessionChange(() => { reset(); setMessages([]); setEvidence([]); setBusy(false); onClose(); }), [onClose]);
  return <Drawer title="AI 排错" open={selection !== null} onClose={onClose} placement="right" width="min(620px, 95vw)" getContainer={getNifiOverlayContainer}
    styles={{ body: { display: 'flex', flexDirection: 'column', gap: 12 } }}>
    <Alert type="info" showIcon message="根据授权证据提供排查建议。修复请在原业务页面完成。" />
    {status && <Typography.Text type="secondary" role="status">{status}</Typography.Text>}
    {failure && <Alert type="error" showIcon message={failure} />}
    {evidence.length > 0 && <details style={{ border: '1px solid var(--border-color, #ddd)', padding: 12, borderRadius: 8 }}>
      <summary style={{ cursor: 'pointer' }}>本次证据（{evidence.length} 项）</summary>
      {evidence.map(item => <details key={item.id} style={{ marginTop: 10 }}>
        <summary style={{ cursor: 'pointer', fontSize: 13 }}>[{item.id}] {item.source} · {item.observedAt ? new Date(item.observedAt).toLocaleString() : '时间未知'}</summary>
        <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', maxHeight: '40vh', overflow: 'auto', fontSize: 12 }}>{item.content}</pre>
      </details>)}
    </details>}
    <div aria-live="polite" style={{ flex: 1 }}>
      {messages.map((item, index) => <article key={index} style={{ padding: 14, marginBottom: 12, borderRadius: 8, background: item.role === 'user' ? 'var(--bg-active, #eef5ff)' : 'var(--bg-secondary, #f6f7f9)' }}>
        <strong>{item.role === 'user' ? '我的问题' : '排错建议'}</strong>
        <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', font: 'inherit', fontSize: 13, lineHeight: 1.7 }}>{item.text || (busy ? '正在分析…' : '本轮未生成完整回答')}</pre>
      </article>)}
    </div>
    <div style={{ position: 'sticky', bottom: 0, background: 'var(--bg-primary, #fff)', paddingTop: 10 }}>
      <Input.TextArea value={question} onChange={event => setQuestion(event.target.value)} rows={3} maxLength={2000} disabled={busy} placeholder="补充现象或继续追问；请勿填写密码和业务原文" />
      <Space style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10 }}>
        {busy ? <Button onClick={() => void stop()}>停止生成</Button> : <Button type="primary" disabled={!question.trim()} onClick={() => void ask()}>发送追问</Button>}
        {!busy && failure && <Button onClick={() => void ask(true)}>重试本次诊断</Button>}
      </Space>
    </div>
  </Drawer>;
}
