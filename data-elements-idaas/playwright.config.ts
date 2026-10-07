import { defineConfig } from '@playwright/test';
import { localOrigin } from './dev-server.config';
import { fileURLToPath } from 'node:url';

const testOutput = fileURLToPath(new URL('../logs/idaas/playwright/', import.meta.url));

const liveTests = process.env.IDAAS_LIVE_TEST === '1';
// Contract tests intercept /api/**. If a route is missed, keep the dev proxy
// pointed at a closed loopback port so the default suite cannot reach 8088.
if (!liveTests) process.env.VITE_IDAAS_API_TARGET = 'http://127.0.0.1:9';

const contractSpecs = ['**/e2e.spec.ts', '**/compact.spec.ts', '**/typography.spec.ts', '**/navigation.spec.ts', '**/console-navigation.spec.ts', '**/application-detail.spec.ts', '**/branding.spec.ts', '**/dashboard-audit.spec.ts', '**/backend-p1.spec.ts', '**/manual-sync.spec.ts', '**/organization-tree.spec.ts', '**/user-workflow.spec.ts'];
const liveSpecs = ['**/console-live.spec.ts', '**/application-detail-live.spec.ts', '**/backend-p2.spec.ts', '**/backend-stages.spec.ts'];

export default defineConfig({
    testDir: './tests', testMatch: liveTests ? [...contractSpecs, ...liveSpecs] : contractSpecs, fullyParallel: false,
    outputDir: `${testOutput}/results`,
    timeout: 30000, reporter: [['list'], ['html', { open: 'never', outputFolder: `${testOutput}/report` }]],
    use: { baseURL: localOrigin, channel: process.env.PLAYWRIGHT_CHANNEL === 'msedge' ? 'msedge' : undefined, viewport: { width: 1440, height: 1000 }, screenshot: 'only-on-failure', trace: 'retain-on-failure' },
    webServer: { command: 'npm run dev', url: localOrigin, reuseExistingServer: liveTests && !process.env.CI }
});
