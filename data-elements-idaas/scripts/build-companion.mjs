// Sync the optional independent HTML's stylesheet. Does NOT build React.
import fs from 'node:fs';
const file='preview/index.html';
const paths=['src/styles/typography-tokens.css','src/styles/app.css','preview/companion.css','src/styles/typography.css','preview/typography-bridge.css'];
const css=paths.map(p=>fs.readFileSync(p,'utf8')).join('\n');
const html=fs.readFileSync(file,'utf8').replace(/<style>[\s\S]*?<\/style>/,()=>`<style>${css}</style>`);
fs.writeFileSync(file,html);console.log('Independent HTML stylesheet synced; not a React build.');
