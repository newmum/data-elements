/**
 * iframe Bridge — 处理父窗口通信与 token 注入
 *
 * 业务场景：外部系统通过 iframe 嵌入本应用，通过以下方式传递 token：
 *   1. URL 查询参数：?token=xxx
 *   2. postMessage：父窗口发送 { type: 'SET_TOKEN', token: 'xxx' }
 *   3. postMessage：父窗口发送 { type: 'INIT', payload: { token: 'xxx', ... } }
 *
 * token 会被自动注入到 apiClient 的请求头中（Authorization: Bearer xxx / token: xxx）
 */

import { apiClient } from './client';
import { getBridgeToken, onNifiSessionChange, setBridgeToken, signalCanvasSelectionChange } from './bridgeSession';

// ---- 独立模式检测 ----

/**
 * 判断当前应用是否处于独立运行模式（非 iframe 嵌入）
 * 当 window.self === window.top 时，说明页面是顶层窗口，属于独立运行
 * 在独立模式下，来源表和目标表的配置字段可以自由编辑
 */
export function isStandalone(): boolean {
  return window.self === window.top;
}

// ---- Token 管理 ----

type CanvasSelection = { pipelineId: string | null; tid: string | null; accessTaskId: string | null };
// Route, URL, and parent selections are kept separate so an empty in-app route
// does not erase the job selected by the embedding parent's INIT message.
let routeSelection: CanvasSelection | null = null;
let parentPipelineId: string | null = null;
let currentOrgPath: string | null = null;
let currentIsAdmin = false;

// This also covers the integrated login prompt, which updates bridgeSession
// directly rather than going through a postMessage.
onNifiSessionChange(() => {
  parentPipelineId = null;
  currentOrgPath = null;
  currentIsAdmin = false;
});

function urlSelection(): CanvasSelection | null {
  const selection = {
    pipelineId: parsePipelineIdFromUrl()?.trim() || null,
    tid: parseTidFromUrl()?.trim() || null,
    accessTaskId: parseAccessTaskIdFromUrl()?.trim() || null,
  };
  return selection.pipelineId || selection.tid || selection.accessTaskId ? selection : null;
}

function activeSelection(): CanvasSelection | null {
  return routeSelection ?? urlSelection();
}

/**
 * 将 token 注入到 apiClient 默认请求头
 */
function applyToken(token: string | null) {
  if (token === getBridgeToken()) return;

  if (token) {
    apiClient.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    apiClient.defaults.headers.common['token'] = token;
  } else {
    delete apiClient.defaults.headers.common['Authorization'];
    delete apiClient.defaults.headers.common['token'];
  }
  setBridgeToken(token);
}

/**
 * 获取当前 token（供外部模块查询）
 */
export function getToken(): string | null {
  return getBridgeToken();
}

/**
 * 获取当前 tid（供外部模块查询）
 * tid 是从 URL 参数中解析的资源目录 ID
 */
export function getTid(): string | null {
  return activeSelection()?.tid ?? null;
}

/**
 * 获取当前 accessTaskId（供外部模块查询）
 * accessTaskId 是从 URL 参数中解析的访问任务 ID
 */
export function getAccessTaskId(): string | null {
  return activeSelection()?.accessTaskId ?? null;
}

/** The job id selects a canvas only; it never establishes tenant authority. */
export function getPipelineId(): string | null {
  const selection = activeSelection();
  return selection ? selection.pipelineId : parentPipelineId;
}
export function getOrgPath(): string | null { return currentOrgPath; }
export function getIsAdmin(): boolean { return currentIsAdmin; }

/** Select the canvas from an in-app route before mounting EditorPage.
 *  A route switch does not reload the page, so it cannot rely on initIframeBridge
 *  reparsing the original URL. The server still resolves tenant access.
 */
export function setCanvasSelection(selection: {
  pipelineId?: string | null;
  tid?: string | null;
  accessTaskId?: string | null;
}) {
  routeSelection = Object.keys(selection).length ? {
    pipelineId: selection.pipelineId?.trim() || null,
    tid: selection.tid?.trim() || null,
    accessTaskId: selection.accessTaskId?.trim() || null,
  } : null;
}

// ---- URL 参数解析 ----

/**
 * 从当前页面 URL 的查询参数中提取 token
 * 支持 ?token=xxx 和 ?accessToken=xxx 两种写法
 */
function parseTokenFromUrl(): string | null {
  const params = new URLSearchParams(window.location.search);
  const hashParams = new URLSearchParams(window.location.hash.split('?')[1] ?? '');
  return params.get('token') || params.get('accessToken')
    || hashParams.get('token') || hashParams.get('accessToken')
    || null;
}

/**
 * 从当前页面 URL 的查询参数中提取 tid
 */
function parseTidFromUrl(): string | null {
  const params = new URLSearchParams(window.location.search);
  const hashParams = new URLSearchParams(window.location.hash.split('?')[1] ?? '');
  return params.get('tid') || hashParams.get('tid') || null;
}

/**
 * 从当前页面 URL 的查询参数中提取 accessTaskId
 */
function parseAccessTaskIdFromUrl(): string | null {
  const params = new URLSearchParams(window.location.search);
  const hashParams = new URLSearchParams(window.location.hash.split('?')[1] ?? '');
  return params.get('accessTaskId') || hashParams.get('accessTaskId') || null;
}

/** Extract only a canvas selection; tenant and data scope are server-owned. */
function parsePipelineIdFromUrl(): string | null {
  const params = new URLSearchParams(window.location.search);
  const hashParams = new URLSearchParams(window.location.hash.split('?')[1] ?? '');
  return params.get('jobId') || params.get('pipelineId') || hashParams.get('jobId') || hashParams.get('pipelineId') || null;
}

/** Keep a URL-supplied token in memory, not browser history or copied links. */
function removeTokenFromUrl() {
  const url = new URL(window.location.href);
  let changed = false;
  for (const key of ['token', 'accessToken']) {
    if (url.searchParams.has(key)) {
      url.searchParams.delete(key);
      changed = true;
    }
  }
  const hash = url.hash.slice(1);
  const queryStart = hash.indexOf('?');
  if (queryStart >= 0) {
    const hashParams = new URLSearchParams(hash.slice(queryStart + 1));
    for (const key of ['token', 'accessToken']) {
      if (hashParams.has(key)) {
        hashParams.delete(key);
        changed = true;
      }
    }
    if (changed) {
      const remaining = hashParams.toString();
      url.hash = `${hash.slice(0, queryStart)}${remaining ? `?${remaining}` : ''}`;
    }
  }
  if (changed) {
    window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`);
  }
}
function parseOrgPathFromUrl(): string | null {
  const params = new URLSearchParams(window.location.search);
  const hashParams = new URLSearchParams(window.location.hash.split('?')[1] ?? '');
  return params.get('orgPath') || hashParams.get('orgPath') || null;
}
function parseIsAdminFromUrl(): boolean {
  const params = new URLSearchParams(window.location.search);
  const hashParams = new URLSearchParams(window.location.hash.split('?')[1] ?? '');
  const value = params.get('isAdmin') || hashParams.get('isAdmin');
  return value === 'true' || value === '1';
}

// ---- postMessage 监听 ----

/**
 * 支持的消息格式：
 *   { type: 'SET_TOKEN',  token: 'xxx' }
 *   { type: 'INIT',       payload: { token: 'xxx', ... } }
 *   { type: 'CLEAR_TOKEN' }
 */
const VALID_TYPES = new Set(['SET_TOKEN', 'INIT', 'CLEAR_TOKEN']);

function handleMessage(event: MessageEvent) {
  // Only the actual embedding parent can supply a session or canvas selection.
  // Standalone HaiTong pages obtain their session from the platform bridge.
  if (window.parent === window || event.source !== window.parent ||
      !parseAllowedOrigins().includes(event.origin)) {
    return;
  }

  const data = event.data;
  if (!data || typeof data !== 'object' || !data.type) return;
  if (!VALID_TYPES.has(data.type)) return;

  switch (data.type) {
    case 'SET_TOKEN':
      if (data.token && typeof data.token === 'string') {
        applyToken(data.token);
      }
      break;

    case 'INIT':
      if (data.payload?.token && typeof data.payload.token === 'string') {
        applyToken(data.payload.token);
      }
      {
        const nextParentPipelineId = typeof data.payload?.jobId === 'string'
          ? data.payload.jobId.trim() || null : null;
        if (nextParentPipelineId !== parentPipelineId) {
          parentPipelineId = nextParentPipelineId;
          signalCanvasSelectionChange();
        }
      }
      if (data.payload?.orgPath && typeof data.payload.orgPath === 'string') currentOrgPath = data.payload.orgPath;
      if ('isAdmin' in (data.payload || {})) currentIsAdmin = Boolean(data.payload.isAdmin);
      // payload 中的其他参数可在此扩展处理
      break;

    case 'CLEAR_TOKEN':
      applyToken(null);
      break;
  }
}

/**
 * The integrated editor accepts its own origin, the configured platform origin,
 * and explicitly configured embedding origins. An empty list never means '*'.
 */
function parseAllowedOrigins(): string[] {
  const origins = new Set<string>([window.location.origin]);
  const platformUrl = import.meta.env.VITE_SOURCE_PLATFORM_HOME_URL?.trim() ||
    (import.meta.env.DEV ? 'http://localhost:3000/' : '/wanxiang/');
  const meta = document.querySelector('meta[name="iframe-allowed-origins"]');
  const configured = [platformUrl,
    ...(meta?.getAttribute('content') ?? '').split(','),
    ...(import.meta.env.VITE_NIFI_EMBED_ORIGINS ?? '').split(',')];
  for (const entry of configured) {
    if (!entry.trim()) continue;
    try {
      const origin = new URL(entry.trim(), window.location.href).origin;
      if (origin !== 'null') origins.add(origin);
    } catch { /* Ignore malformed entries rather than opening the bridge. */ }
  }
  return [...origins];
}

// ---- 向父窗口发送消息 ----

/**
 * 通知父窗口本应用已就绪
 * 父窗口收到后可以发送 INIT 消息传递 token 等参数
 */
function notifyReady() {
  if (window.parent && window.parent !== window) {
    window.parent.postMessage({ type: 'NIFI_APP_READY' }, '*');
  }
}

/**
 * 通知父窗口本应用即将关闭
 * 父窗口收到后可执行清理逻辑（如移除 iframe、更新菜单状态等）
 *
 * 消息格式：{ type: 'NIFI_APP_CLOSE', timestamp: number, pipelineId?: string }
 */
export function notifyClose() {
  if (window.parent && window.parent !== window) {
    window.parent.postMessage(
      {
        type: 'NIFI_APP_CLOSE',
        timestamp: Date.now(),
      },
      '*',
    );
  }
}

/** Preserve the parent close handshake for embedded canvases. */
export function requestIntegratedCanvasClose() {
  if (window.parent !== window) {
    notifyClose();
    return;
  }
  window.dispatchEvent(new CustomEvent('haitong:nifi-close-requested'));
}

export function notifyCanvasContentReady() {
  if (window.parent && window.parent !== window) {
    window.parent.postMessage({ type: 'NIFI_CANVAS_CONTENT_READY', timestamp: Date.now() }, '*');
  }
}

export function notifyCanvasContentError(message: string) {
  if (window.parent && window.parent !== window) {
    window.parent.postMessage(
      { type: 'NIFI_CANVAS_CONTENT_ERROR', message, timestamp: Date.now() },
      '*',
    );
  }
}

// ---- 初始化 ----

let initialized = false;

/**
 * 初始化 iframe Bridge，应在应用入口尽早调用
 */
export function initIframeBridge() {
  if (initialized) return;
  initialized = true;

  // Legacy iframe links may carry a token. Top-level HaiTong always obtains
  // its own platform session, so an external link cannot fix its identity.
  const urlToken = window.parent !== window ? parseTokenFromUrl() : null;
  if (urlToken) {
    applyToken(urlToken);
  }

  // URL selection is read from the current route by the getters. The server
  // still resolves its tenant authorization independently of the selected id.
  currentOrgPath = parseOrgPathFromUrl();
  currentIsAdmin = parseIsAdminFromUrl();

  // Parse the complete selection before dropping token parameters from the URL.
  removeTokenFromUrl();

  // 6. 监听父窗口消息
  window.addEventListener('message', handleMessage);

  // 7. 通知父窗口已就绪（父窗口可在收到后发送 token）
  notifyReady();

  // 8. 监听浏览器关闭/刷新，通知父窗口
  window.addEventListener('beforeunload', notifyClose);
}

/**
 * 销毁 iframe Bridge（一般不需要调用）
 */
export function destroyIframeBridge() {
  window.removeEventListener('message', handleMessage);
  window.removeEventListener('beforeunload', notifyClose);
  applyToken(null);
  routeSelection = null;
  initialized = false;
}
