/** Static typography contract audit. This is NOT a browser or React rendering test. */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { typeScale } from '../src/app/typography.ts';
import { domainPages, authPaths } from '../src/app/navigation-data.ts';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const checks = [];
const check = (name, condition, detail = '') => checks.push({ name, passed: Boolean(condition), detail });
const theme = read('src/app/theme.ts'), css = read('src/styles/typography.css');
const app = read('src/main.tsx'), baseCSS = read('src/styles/app.css');
const common = read('src/components/common.tsx'), applications = read('src/pages/Applications.tsx');
check('紧凑算法保留', /algorithm:\s*theme\.compactAlgorithm/.test(theme));
check('全局小尺寸保留', /componentSize="small"/.test(app));
check('基础字号为14px', typeScale.body.size === 14);
check('导航不小于正文', typeScale.navigation.size >= typeScale.body.size);
check('正文大于描述', typeScale.body.size > typeScale.secondary.size);
check('卡片标题大于正文', typeScale.section.size > typeScale.body.size);
check('标题层级递增', typeScale.page.size > typeScale.panel.size && typeScale.panel.size > typeScale.section.size);
check('辅助文本不小于12px', Math.min(...Object.values(typeScale).map(x => x.size)) >= 12);
check('主文本行高保留余量', Object.values(typeScale).every(x => x.lineHeight >= x.size + 4));
check('小卡片标题不是压缩字号', theme.includes('headerFontSizeSM: typeScale.section.size'));
check('小表格正文仍为正文层级', theme.includes('cellFontSizeSM: typeScale.body.size'));
check('小按钮字号独立于高度', theme.includes('contentFontSizeSM: typeScale.body.size'));
check('小输入字号独立于高度', theme.includes('inputFontSizeSM: typeScale.body.size'));
check('导航主题显式正文级别', theme.includes('Menu: { fontSize: typeScale.navigation.size'));
check('卡片标题嵌套Text继承', /\.ant-card-head-title \.ant-typography\s*\{[^}]*font-size:\s*inherit/s.test(css));
check('应用名称不再被嵌套Typography缩小', applications.includes('className="application-title-text"') && !/className="application-title"[^>]*>[\s\n]*<Text/.test(applications));
check('页面标题使用统一角色', common.includes('level={1} className="page-heading"'));
check('指标标签使用独立角色', common.includes('className="metric-label"'));
check('最终文字样式后加载', app.indexOf('typography.css') > app.indexOf('app.css'));
check('不使用整页缩放', !/(?:^|[;{\s])zoom\s*:|transform:\s*scale\(/m.test(baseCSS + css));
check('图表使用实测宽度', read('src/pages/Dashboard.tsx').includes('useElementWidth') && read('src/pages/Dashboard.tsx').includes('chartTickIndices'));
check('环图中心使用HTML文字', read('src/components/visuals.tsx').includes('className="donut-center"'));
check('设计规范页面保持删除', !fs.existsSync(path.join(root, 'src/pages/DesignSystem.tsx')) && !read('src/app/navigation-data.ts').includes('design-system'));
check('全局样式不把所有span缩小', !/(?:^|})\s*span\s*\{[^}]*font-size/s.test(css));
const roles = ['fontSizeHeading1', 'fontSizeHeading2', 'fontSizeHeading3', 'fontSizeHeading4', 'fontSizeHeading5'];
check('完整覆盖派生标题Token', roles.every(r => theme.includes(r + ':')));
const files = [];
function walk(dir) { for (const ent of fs.readdirSync(path.join(root, dir), { withFileTypes: true })) { const p = path.posix.join(dir, ent.name); if (ent.isDirectory()) walk(p); else if (/\.tsx?$/.test(p)) files.push(p); } }
walk('src');
const inventory = files.map(file => {
 const source = ts.createSourceFile(file, read(file), ts.ScriptTarget.Latest, true, file.endsWith('tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
 const headings = [], fontOverrides = [];
 function visit(n) {
  if (ts.isJsxOpeningElement(n) || ts.isJsxSelfClosingElement(n)) {
   const tag = n.tagName.getText(source);
   if (['Title', 'Text', 'Card', 'PageTitle', 'SectionIntro', 'ModuleSummary', 'DataTable', 'RecordEditor', 'DetailDrawer', 'Drawer', 'Modal'].includes(tag) || /^h[1-6]$/.test(tag)) {
    headings.push({ component: tag, line: source.getLineAndCharacterOfPosition(n.getStart()).line + 1,
     attributes: n.attributes.properties.filter(a => ts.isJsxAttribute(a) && ['title','className','size','level'].includes(a.name.getText(source))).map(a => a.getText(source)) });
   }
  }
  if (ts.isPropertyAssignment(n) && n.name.getText(source) === 'fontSize') {
   fontOverrides.push({ line: source.getLineAndCharacterOfPosition(n.getStart()).line + 1, value: n.initializer.getText(source) });
  }
  ts.forEachChild(n, visit);
 }
 visit(source);
 return { file, sourceSyntaxErrors: source.parseDiagnostics.length, componentUses: headings, fontOverrides };
});
check('全源码语法树可解析', inventory.every(f => f.sourceSyntaxErrors === 0));
const pageFiles = inventory.filter(f => f.file.startsWith('src/pages/'));
check('全部业务页面纳入源码清单', pageFiles.length === 13 && ['Identity.tsx','IdentityAdministration.tsx'].every(name => pageFiles.some(p => p.file === 'src/pages/'+name)) && pageFiles.some(p => p.file === 'src/pages/PlatformAccess.tsx'), `${pageFiles.length}个页面实现文件，共享模板覆盖多个路由`);
function templateFor(key) {
 if (['auth-accounts','verifications'].includes(key)) return 'IdentityAdministration.tsx';
 if (['system/access/admins', 'system/access/roles', 'locked-accounts'].includes(key)) return 'PlatformAccess.tsx';
 if (['overview', 'audit/statistics'].includes(key)) return 'Dashboard.tsx';
 if (key === 'apps') return 'Applications.tsx';
 if (['organization', 'persons', 'locked-accounts'].includes(key)) return 'Organization.tsx';
 if (key === 'entities') return 'PublicEntities.tsx';
 if (key.startsWith('grants/') || key === 'permission-groups') return 'Authorization.tsx';
 if (key.startsWith('sync/')) return 'Synchronization.tsx';
 if (key.startsWith('audit/')) return 'Audit.tsx';
 if (key.startsWith('system/settings/') || key === 'profile') return 'Settings.tsx';
 return 'CatalogPage.tsx';
}
const routes = ['workforce','public'].flatMap(domain => domainPages(domain).map(p => ({ domain, key: p.key, label:p.label, route:`/console/${domain}/${p.key}`, source:`src/pages/${templateFor(p.key)}`, status:'source-reviewed; runtime-pending' })));
const report = { version:'2.2.0', kind:'static-source-audit-not-browser', checks, sourceFileCount:files.length, pageImplementationFiles:pageFiles.map(p=>p.file), workspaceCount:routes.length, routes, authRoutes:authPaths, dynamicDetails:{applications:12,tabsPerApplication:6,runtimeStatus:'pending'}, inventory };
const out=path.join(root,'docs/v2.2'); fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'typography-source-audit.json'),JSON.stringify(report,null,2));
for (const c of checks) console.log(`${c.passed ? 'PASS':'FAIL'} ${c.name}${c.detail?' — '+c.detail:''}`);
console.log(`\nStatic contract: ${checks.filter(c=>c.passed).length}/${checks.length}; ${files.length} source files; ${routes.length} workspaces; ${authPaths.length} auth routes. NOT rendered-page acceptance.`);
if(checks.some(c=>!c.passed)) process.exitCode=1;
