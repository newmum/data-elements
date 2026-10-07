import ts from 'typescript';
import fs from 'node:fs';
import path from 'node:path';
const root = process.cwd();
const failures = [], files = [], checks = [];
const check = (name, ok, detail = '') => { checks.push({ name, passed: !!ok, detail }); if (!ok) failures.push(name + (detail ? ': ' + detail : '')); };
function walk(dir) { for (const entry of fs.readdirSync(dir, { withFileTypes: true })) { const p = path.join(dir, entry.name); if (entry.isDirectory()) walk(p); else if (/\.tsx?$/.test(p)) files.push(p); } }
walk(path.join(root, 'src'));
let tables = 0, cards = 0, forms = 0, tabs = 0;
const labelPattern = /mock|演示|评审|模拟|本轮|测试账号|测试口令|测试验证码|后端接入|前端流程/i;
for (const filename of files) {
    const source = fs.readFileSync(filename, 'utf8');
    const relative = path.relative(root, filename);
    const sf = ts.createSourceFile(filename, source, ts.ScriptTarget.Latest, true, filename.endsWith('tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
    check(`parse:${relative}`, sf.parseDiagnostics.length === 0);
    function visit(node) {
        if (ts.isImportDeclaration(node)) return;
        if (ts.isJsxElement(node) || ts.isJsxSelfClosingElement(node)) {
            const opening = ts.isJsxElement(node) ? node.openingElement : node;
            const tag = opening.tagName.getText(sf);
            if (['Card', 'Table', 'Form', 'Tabs'].includes(tag)) {
                const size = opening.attributes.properties.find(p => ts.isJsxAttribute(p) && p.name.getText(sf) === 'size');
                const authControl = relative === path.join('src', 'pages', 'Auth.tsx') && ['Form', 'Tabs'].includes(tag);
                const expectedSize = authControl ? 'large' : 'small';
                check(`size:${relative}:${sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1}:${tag}`, size?.initializer && ts.isStringLiteral(size.initializer) && size.initializer.text === expectedSize);
                if (tag === 'Card') cards++; if (tag === 'Table') tables++; if (tag === 'Form') forms++; if (tag === 'Tabs') tabs++;
            }
        }
        if (/src[\/\\](app|pages|components)[\/\\]/.test(filename) && (ts.isStringLiteralLike(node) || ts.isJsxText(node))) {
            const text = ts.isJsxText(node) ? node.getText(sf) : node.text;
            check(`copy:${relative}:${sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1}`, !labelPattern.test(text), labelPattern.test(text) ? text.slice(0, 130) : '');
        }
        ts.forEachChild(node, visit);
    }
    visit(sf);
}
const main = fs.readFileSync('src/main.tsx', 'utf8');
const theme = fs.readFileSync('src/app/theme.ts', 'utf8');
const css = fs.readFileSync('src/styles/app.css', 'utf8');
const allUi = files.filter(f => /[\/\\](app|components|pages)[\/\\]/.test(f)).map(f => fs.readFileSync(f, 'utf8')).join('\n');
check('global:component-size', /componentSize="small"/.test(main));
check('global:compact-algorithm', /algorithm:\s*theme\.compactAlgorithm/.test(theme));
check('global:portal-defaults', /compactComponents/.test(main));
check('auth:isolated-standard-theme', /algorithm:\s*theme\.defaultAlgorithm/.test(theme) && /<ConfigProvider componentSize="large" theme=\{authTheme\}>/.test(fs.readFileSync('src/pages/Auth.tsx', 'utf8')));
check('removed:design-page-file', !fs.existsSync('src/pages/DesignSystem.tsx'));
check('removed:design-navigation-and-route', !/design-system|DesignSystem|设计规范/.test(allUi));
check('removed:density-toggle', !/setDensity|table-density|表格密度/.test(allUi));
check('css:no-page-scale', !/zoom\s*:|transform\s*:\s*scale\(/i.test(css));
// Windows 文件名不区分大小写，使用目录中的实际文件名检查文档命名。
const rootFiles = fs.readdirSync(root);
check('docs:lowercase-design', rootFiles.includes('design.md') && !rootFiles.includes('DESIGN.md'));
check('docs:agent-entry', fs.existsSync('AGENTS.md') && fs.readFileSync('AGENTS.md', 'utf8').includes('design.md'));
const summary = { kind: 'static-source-audit-not-browser', files: files.length, tables, cards, forms, tabs, checks: checks.length, passed: checks.length - failures.length, failures, details: checks };
const output = path.resolve(root, '../logs/idaas/audit');
fs.mkdirSync(output, { recursive: true });
fs.writeFileSync(path.join(output, 'ui-source-audit.json'), JSON.stringify(summary, null, 2));
console.log(JSON.stringify({ ...summary, details: undefined }, null, 2));
process.exitCode = failures.length ? 1 : 0;
