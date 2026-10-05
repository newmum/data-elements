import { useSyncExternalStore } from 'react';

let currentToken: string | null = null;
let sessionRevision = 0;
let canvasSelectionRevision = 0;
const tokenListeners = new Set<() => void>();
const sessionChangeListeners = new Set<() => void>();
const canvasSelectionListeners = new Set<() => void>();

function subscribeToken(listener: () => void): () => void {
  tokenListeners.add(listener);
  return () => { tokenListeners.delete(listener); };
}

export function getBridgeToken(): string | null {
  return currentToken;
}

/** Monotonic session identity; never exposes the token to query keys or URLs. */
export function getSessionRevision(): number {
  return sessionRevision;
}

export function useSessionRevision(): number {
  return useSyncExternalStore(subscribeToken, getSessionRevision, () => 0);
}

function subscribeCanvasSelection(listener: () => void): () => void {
  canvasSelectionListeners.add(listener);
  return () => { canvasSelectionListeners.delete(listener); };
}

/** A parent INIT may select another job while reusing the same token. */
export function getCanvasSelectionRevision(): number {
  return canvasSelectionRevision;
}

export function useCanvasSelectionRevision(): number {
  return useSyncExternalStore(subscribeCanvasSelection, getCanvasSelectionRevision, () => 0);
}

export function signalCanvasSelectionChange(): void {
  canvasSelectionRevision += 1;
  canvasSelectionListeners.forEach((listener) => listener());
}

export function onNifiSessionChange(listener: () => void): () => void {
  sessionChangeListeners.add(listener);
  return () => { sessionChangeListeners.delete(listener); };
}

export function setBridgeToken(token: string | null) {
  if (token === currentToken) return;
  currentToken = token;
  sessionRevision += 1;
  // Clear session-scoped caches before React subscribers can use the new token.
  sessionChangeListeners.forEach((listener) => listener());
  tokenListeners.forEach((listener) => listener());
}

/** React queries start when the parent supplies its authenticated session. */
export function useTokenReady(): boolean {
  return useSyncExternalStore(subscribeToken, () => Boolean(currentToken), () => false);
}

/** Direct canvas requests also wait for the existing iframe token handshake. */
export function waitForToken(timeoutMs = 15000): Promise<string> {
  if (currentToken) return Promise.resolve(currentToken);
  return new Promise((resolve, reject) => {
    const unsubscribe = subscribeToken(() => {
      if (!currentToken) return;
      clearTimeout(timeout);
      unsubscribe();
      resolve(currentToken);
    });
    const timeout = setTimeout(() => {
      unsubscribe();
      reject(new Error('尚未收到登录信息，请从数据中台重新打开画布'));
    }, timeoutMs);
  });
}
