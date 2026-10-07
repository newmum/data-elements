import { domainPages, authPaths } from '../src/app/navigation-data.ts';
import fs from 'node:fs';
const routes = ['workforce', 'public'].flatMap(domain => domainPages(domain).map(p => ({ domain, path: `/console/${domain}/${p.key}`, name: p.label, group: p.group })));
const report = { kind: 'source-route-inventory-not-browser-results', workspaces: routes, authentication: authPaths };
const output = new URL('../../logs/idaas/audit/', import.meta.url);
fs.mkdirSync(output, { recursive: true });
fs.writeFileSync(new URL('route-inventory.json', output), JSON.stringify(report, null, 2));
console.log('workforce', domainPages('workforce').length, 'public', domainPages('public').length, 'authentication', authPaths.length);
