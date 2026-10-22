import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmbeddedSession from './EmbeddedSession';
import { queryClient } from './queryClient';
import { requestToken, resolveParentOrigin, setEmbeddedToken } from '@/services/embeddedSession';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
  setEmbeddedToken(null);
  localStorage.clear();
  queryClient.clear();
});

function embedded() {
  const parent = { postMessage: vi.fn() };
  vi.stubGlobal('parent', parent);
  vi.spyOn(document, 'referrer', 'get').mockReturnValue('http://localhost:3000/');
  const loaded = vi.fn();
  function Consumer() { loaded(requestToken()); return <div>画布内容</div>; }
  render(<EmbeddedSession><Consumer /></EmbeddedSession>);
  const message = (origin: string, source: MessageEventSource | null, token: unknown) => act(() => {
    window.dispatchEvent(new MessageEvent('message', { origin, source, data: { type: 'INIT', payload: { token } } }));
  });
  return { parent, loaded, message };
}

describe('embedded authenticated session', () => {
  it('allows same-origin production and loopback development, rejects unrelated origins', () => {
    expect(resolveParentOrigin('https://platform.example/yunti/editor/1', 'https://platform.example/')).toBe('https://platform.example');
    expect(resolveParentOrigin('http://localhost:3030/editor/1', 'http://localhost:3000/')).toBe('http://localhost:3000');
    expect(resolveParentOrigin('http://localhost:3030/editor/1', 'https://attacker.example/')).toBeNull();
    expect(resolveParentOrigin('https://platform.example/yunti/editor/1', 'http://platform.example/')).toBeNull();
  });

  it('does not mount API consumers before trusted INIT and keeps credentials out of persistent storage', () => {
    const s = embedded();
    expect(s.loaded).not.toHaveBeenCalled();
    expect(s.parent.postMessage).toHaveBeenCalledWith({ type: 'YUNTI_APP_READY' }, 'http://localhost:3000');
    s.message('https://attacker.example', s.parent as unknown as Window, 'wrong-session');
    s.message('http://localhost:3000', window, 'wrong-session');
    expect(s.loaded).not.toHaveBeenCalled();
    s.message('http://localhost:3000', s.parent as unknown as Window, 'current-session');
    expect(screen.getByText('画布内容')).toBeInTheDocument();
    expect(s.loaded).toHaveBeenCalledWith('current-session');
    expect(localStorage.getItem('token')).toBeNull();
    expect(s.parent.postMessage).toHaveBeenCalledWith({ type: 'YUNTI_SESSION_READY' }, 'http://localhost:3000');
  });

  it('rejects a missing token and prevents an initialized iframe from switching identities', () => {
    const s = embedded();
    s.message('http://localhost:3000', s.parent as unknown as Window, '');
    expect(screen.getByRole('alert')).toHaveTextContent('登录已失效');
    expect(s.loaded).not.toHaveBeenCalled();
    s.message('http://localhost:3000', s.parent as unknown as Window, 'session-a');
    s.message('http://localhost:3000', s.parent as unknown as Window, 'session-b');
    expect(screen.getByRole('alert')).toHaveTextContent('登录信息已变化');
    expect(requestToken()).toBe('session-a');
    expect(screen.queryByText('画布内容')).not.toBeInTheDocument();
  });

  it('reports initialization timeout and clears credentials on unmount', () => {
    vi.useFakeTimers();
    const s = embedded();
    act(() => vi.advanceTimersByTime(12000));
    expect(screen.getByRole('alert')).toHaveTextContent('未收到主平台登录信息');
    s.message('http://localhost:3000', s.parent as unknown as Window, 'session-a');
    cleanup();
    expect(requestToken()).toBe('');
  });

  it('preserves standalone same-origin login behavior', () => {
    vi.stubGlobal('parent', window);
    localStorage.setItem('token', 'standalone-session');
    render(<EmbeddedSession><div>独立画布</div></EmbeddedSession>);
    expect(screen.getByText('独立画布')).toBeInTheDocument();
    expect(requestToken()).toBe('standalone-session');
  });
});
