import {describe,it,expect} from 'vitest';
import app from '../src/App.tsx?raw';
import html from '../index.html?raw';
import productLogo from '../src/ProductLogo.tsx?raw';
import mark from '../public/fengchi-mark.svg?raw';
import icons from '../src/NativeIcon.tsx?raw';
import utilityIcons from '../src/UtilityIcon.tsx?raw';
describe('Fengchi branding',()=>{
 it('prevents hover popups from changing document scrollbars',()=>{expect(app).not.toMatch(/<Tooltip title="(?:对象信息|DDL|字段|新增记录|删除选中记录|导出当前结果)"/);});
 it('keeps one icon-only theme control in the footer and help in the menu',()=>{const toolbar=app.split('<nav className="main-toolbar">')[1].split('</nav>')[0];expect(toolbar).not.toMatch(/theme-toggle|QuestionCircle|setHelpOpen/);expect(app).toContain('className="status-theme-toggle"');expect(app).toContain('切换深色主题');expect(app).toContain("label:'帮助'");expect(app).not.toContain('BulbOutlined');});
 it('uses local current-color utility symbols',()=>{expect(utilityIcons).toContain('stroke="currentColor"');expect(utilityIcons).not.toMatch(/linearGradient|radialGradient|<filter|https?:/);});
 it('uses the requested browser and product names',()=>{expect(html).toContain('<title>风驰数据计算平台</title>');expect(app).toContain('风驰数据计算平台');expect(app+html).not.toMatch(/Database Studio/i);});
 it('ships a self-contained scalable mark as favicon',()=>{expect(html).toContain('href="/fengchi-mark.svg?v=4"');expect(mark).toContain('viewBox="0 0 32 32"');expect(mark).not.toMatch(/<script|foreignObject|href=|linearGradient|radialGradient|filter=/);});
 it('shares the current wind mark between product and favicon',()=>{expect(productLogo).toContain('src="/fengchi-mark.svg?v=4"');expect(mark.match(/<path /g)).toHaveLength(3);expect(mark).toMatch(/c9 0 12-9/);expect(mark).not.toMatch(/stroke-linejoin|<text|<image/);});
 it('restores the chosen historical artwork while keeping the approved plug',()=>{expect(icons).toContain('published version 2 (e9af2c0)');expect(icons).toContain('return <ConnectionIcon size={size} className={className}/>');expect(icons).not.toMatch(/https?:/);});
});
