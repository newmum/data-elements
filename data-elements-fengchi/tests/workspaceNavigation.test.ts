import {describe,expect,it} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {tabScrollbarGeometry} from '../src/TabOverflowScrollbar';
import PaneToggleIcon,{paneToggleDirection} from '../src/PaneToggleIcon';
import app from '../src/App.tsx?raw';

describe('overlay tab scrollbar',()=>{
 it('does not show when tabs fit',()=>{expect(tabScrollbarGeometry(400,400,0).visible).toBe(false);expect(tabScrollbarGeometry(0,0,0).visible).toBe(false)});
 it('computes proportional thumb travel independently of layout height',()=>{const a=tabScrollbarGeometry(400,800,0),b=tabScrollbarGeometry(400,800,400);expect(a.width).toBe(196);expect(a.left).toBe(0);expect(b.left).toBe(a.track-a.width);expect(b.maxScroll).toBe(400)});
 it('clamps offsets and keeps a usable thumb for many tabs',()=>{const a=tabScrollbarGeometry(400,20000,50000);expect(a.width).toBe(24);expect(a.left).toBe(a.track-a.width);expect(tabScrollbarGeometry(400,800,-100).left).toBe(0)});
 it('keeps small tracks valid',()=>{const a=tabScrollbarGeometry(20,800,40);expect(a.width).toBeLessThanOrEqual(a.track);expect(a.left).toBe(0)});
 it('places navigation across the full shell and overlay inside its viewport',()=>{expect(app.indexOf('tabs-strip workspace-navigation')).toBeLessThan(app.indexOf('className="main-layout"'));expect(app).toContain('className="document-tabs-viewport"');expect(app).toContain('<TabOverflowScrollbar viewportRef={documentTabsRef}/>')});
});
describe('pane action direction',()=>{
 it.each([['left',true,'left'],['left',false,'right'],['right',true,'right'],['right',false,'left']] as const)('%s pane expanded=%s points %s',(side,expanded,direction)=>{expect(paneToggleDirection(side,expanded)).toBe(direction);expect(renderToStaticMarkup(createElement(PaneToggleIcon,{side,expanded}))).toContain(`data-direction="${direction}"`)});
});
