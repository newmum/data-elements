import { platformApi, platformApiGet } from '../shared/platformApi';

type LogComponent = {
  name: string;
  compile_js: string;
  compile_css?: string | null;
};

function readOnce<T>(read: () => Promise<T>) {
  let result: Promise<T> | undefined;
  return () => result ??= read().catch(error => {
    result = undefined;
    throw error;
  });
}

/** 在认证工作区内共用日志页面初始化，重新进入或重试时创建新的读取。 */
export function createLogBootstrap() {
  return {
    capabilities: readOnce(() => platformApi<string[]>('/sym/logs/capabilities')),
    components: readOnce(() => platformApiGet<LogComponent[]>('/sym/logs/components', { kind: 'auth' })),
  };
}

export type LogBootstrap = ReturnType<typeof createLogBootstrap>;
