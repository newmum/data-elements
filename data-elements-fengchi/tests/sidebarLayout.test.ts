import { describe, expect, it } from 'vitest';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ConnectionSearchHeader from '../src/ConnectionSearchHeader';
import ConnectionTreeLabel from '../src/ConnectionTreeLabel';
import app from '../src/App.tsx?raw';

describe('connection panel header', () => {
  it('uses the search field instead of the title and connection/collapse actions', () => {
    const html = renderToStaticMarkup(createElement(ConnectionSearchHeader, { value: '', onChange: () => {} }));
    expect(html).toContain('connection-search-header');
    expect(html).not.toContain('connection-navigation-spacer');
    expect(app.indexOf('tabs-strip workspace-navigation')).toBeLessThan(app.indexOf('className="main-layout"'));
    expect(app).toContain('className="inspector-toggle"');
    expect(html).toContain('aria-label="搜索数据库对象"');
    expect(html).not.toMatch(/新建连接|收起所有节点|>连接<|menu-fold|anticon-plus/);
    expect(app).not.toContain('title="收起所有节点"');
    expect(app).not.toContain('className="object-search"');
    expect(app).toContain('ConnectionSearchHeader value={search}');
  });
  it('retains the main toolbar and context-menu connection entries', () => {
    expect(app).toContain("label:'新建连接'");
    expect(app).toContain("'连接',()=>openConnection()");
  });
  it('separates shrinkable text from fixed counts and keeps the full name available', () => {
    const name = 'a_very_long_table_name';
    const html = renderToStaticMarkup(createElement(ConnectionTreeLabel, { label: name, count: 12345 }));
    expect(html).toContain(`class="connection-tree-text" title="${name}"`);
    expect(html).toContain('class="connection-tree-count">12345</span>');
    expect(html).not.toContain('<br');
    expect(app).toContain('ConnectionTreeLabel label={t.name} count={t.rows.length}');
  });
  it('retains zero counts, connection status and database badges', () => {
    expect(renderToStaticMarkup(createElement(ConnectionTreeLabel, { label: 'empty', count: 0 }))).toContain('connection-tree-count">0');
    expect(renderToStaticMarkup(createElement(ConnectionTreeLabel, { label: 'local', connected: true }))).toContain('connection-tree-status online');
    expect(renderToStaticMarkup(createElement(ConnectionTreeLabel, { label: 'commerce_db', badge: '开发' }))).toContain('connection-tree-badge">开发');
  });
});
