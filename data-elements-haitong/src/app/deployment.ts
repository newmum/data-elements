import { routes } from './routes';

declare global {
  interface Window {
    haitongDeployment?: {
      enabledRoutes?: string[];
      tenantId?: string;
      appId?: string;
      nifiUiUrl?: string;
    };
  }
}

const deployment = typeof window === 'undefined' ? undefined : window.haitongDeployment;
const enabledRoutes = Array.isArray(deployment?.enabledRoutes) ? new Set(deployment.enabledRoutes) : undefined;
export const standaloneDeployment = Boolean(deployment?.tenantId && deployment.appId);
export const deploymentHome = '/development/nifi-flows';
export const deploymentLanding = enabledRoutes?.has(deploymentHome)
  ? deploymentHome
  : routes.find(route => enabledRoutes?.has(route.path))?.path;

/** Only applies to the standalone menu profile. The server still authorizes each request. */
export function deploymentAllows(path: string): boolean {
  if (!enabledRoutes) return true;
  if (path === '/development/canvas') return enabledRoutes.has(deploymentHome);
  if (path.startsWith('/management/logs/')) return enabledRoutes.has('/management/logs');
  return enabledRoutes.has(path);
}
