/** Presentation only: never changes session validation, redirects or API retries. */
export type StartupPhase = 'session' | 'workspace' | 'module' | 'redirect';
export type StartupFailureKind = 'session-sync' | 'timeout' | 'network' | 'service' | 'permission' | 'unknown' | 'authentication';
export interface StartupFailure {
  kind: StartupFailureKind;
  title: string;
  description: string;
  reason: string;
  advice: string;
  detail: string;
}

export const startupPhases = {
  session: { title: '正在准备治理工作空间', description: '正在建立安全连接，读取您的账户与租户信息。', activity: '正在连接身份与后端服务', step: 0 },
  workspace: { title: '正在打开治理工作空间', description: '正在读取工作区配置，准备元数据、标准与质量治理能力。', activity: '正在读取工作区配置', step: 2 },
  module: { title: '正在准备功能工作台', description: '正在加载页面组件与工作区视图，请稍候。', activity: '正在加载功能视图', step: 2 },
  redirect: { title: '正在返回登录页面', description: '当前登录状态已失效，请在主平台重新登录后继续。', activity: '正在前往统一登录入口', step: -1 },
} satisfies Record<StartupPhase, { title: string; description: string; activity: string; step: number }>;

function safeDetail(message: string): string {
  return message
    .replace(/\b(Bearer\s+)\S+/gi, '$1[已隐藏]')
    .replace(/(["']?(?:password|passwd|pwd|access_token|refresh_token|token|authorization)["']?\s*[:=]\s*)["']?[^\s&,;"'}]+["']?/gi, '$1[已隐藏]')
    .replace(/(https?:\/\/)[^/@\s]+:[^/@\s]+@/gi, '$1[已隐藏]@')
    .replaceAll('数据中台', '主平台').replace(/\s+/g, ' ').slice(0, 240);
}

/** A bridge timeout is not proof that the backend is down. Keep that distinction. */
export function startupFailure(error: unknown): StartupFailure {
  const value = error && typeof error === 'object' ? error as { message?: unknown; status?: unknown; timedOut?: boolean } : undefined;
  const message = error instanceof Error ? error.message : String(value?.message ?? error ?? '未获得有效的服务响应');
  const detail = safeDetail(message);
  const status = Number(value?.status);
  const result = (kind: StartupFailureKind, title: string, description: string, reason: string, advice: string): StartupFailure => ({ kind, title, description, reason, advice, detail });
  if (status === 401 || status === 100120 || /user_no_login|not[ _-]?login|未登录|尚未登录|当前登录账号无效|正在前往.*登录|登录.*(?:失效|过期)|token.*(?:expired|invalid)/i.test(message)) {
    return result('authentication', '正在返回登录页面', '当前登录状态需要重新验证。', '登录状态已失效', '请在统一登录入口重新登录。');
  }
  if (/登录态同步|登录(?:服务|信息同步)|会话同步/i.test(message)) {
    return result('session-sync', '后端连接尚未就绪', '登录信息同步未完成，暂时无法继续访问后端服务。', '登录信息同步未完成', '请确认主平台与后端服务均可访问，再重新连接。');
  }
  if (status === 403) {
    return result('permission', '当前账号暂时无法访问', '服务已响应，但当前账号没有访问该工作空间的权限。', '访问权限不足', '请联系管理员确认账户与租户的访问权限。');
  }
  if (value?.timedOut || /timeout|超时/i.test(message)) {
    return result('timeout', '后端服务响应超时', '暂时没有收到后端服务的有效响应，治理工作空间尚未加载完成。', '服务响应超时', '请检查网络连接或后端服务状态，稍后重新连接。');
  }
  if (status >= 500 && status < 600) {
    return result('service', '后端服务暂时无法访问', '后端服务返回了异常响应，当前无法加载治理工作空间。', `服务异常 · HTTP ${status}`, '请稍后重新连接；如果持续发生，请联系服务管理员。');
  }
  if (/无法连接|failed to fetch|network|网络|fetch failed|connection refused/i.test(message)) {
    return result('network', '暂时无法连接后端服务', '服务连接未能建立，元数据、数据标准与质量治理暂时无法加载。', '服务连接未能建立', '请确认网络连接与后端服务状态，再重新连接。');
  }
  return result('unknown', '治理工作空间暂时未能加载', '暂时无法读取有效的初始化信息，请查看连接详情后重试。', '初始化响应异常', '请重新连接；如果仍未恢复，请联系管理员检查服务响应。');
}
