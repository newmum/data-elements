import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import { typeScale, typeColor } from '../src/app/typography.ts';
const kebab = v => v.replace(/[A-Z]/g, m => '-' + m.toLowerCase());
const lines = ['/* Generated from src/app/typography.ts. Run npm run tokens:generate. */', ':root {'];
for (const [role, spec] of Object.entries(typeScale)) {
  const name = kebab(role);
  lines.push(`  --iam-font-${name}: ${spec.size}px;`, `  --iam-leading-${name}: ${spec.lineHeight}px;`, `  --iam-weight-${name}: ${spec.weight};`);
}
for (const [name, value] of Object.entries(typeColor)) lines.push(`  --iam-type-${kebab(name)}: ${value};`);
lines.push('}', '');
const content = lines.join('\n');
const path = fileURLToPath(new URL('../src/styles/typography-tokens.css', import.meta.url));
if (process.argv.includes('--check')) {
  if (!fs.existsSync(path) || fs.readFileSync(path, 'utf8') !== content) {
    console.error('Typography CSS does not match typography.ts. Run npm run tokens:generate.');
    process.exit(1);
  }
  console.log('Typography CSS and semantic tokens are in sync.');
} else { fs.writeFileSync(path, content); console.log('Generated semantic typography CSS.'); }
