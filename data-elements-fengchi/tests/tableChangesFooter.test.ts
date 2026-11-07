import { describe, expect, it, vi } from 'vitest';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import TableChangesFooter from '../src/TableChangesFooter';
import app from '../src/App.tsx?raw';

const render = (dirty: boolean, withPagination = false) => renderToStaticMarkup(createElement(TableChangesFooter, {
  dirty,
  onApply: vi.fn(),
  onRevert: vi.fn(),
  pagination: withPagination ? { current: 1, pageSize: 20, total: 24, onChange: vi.fn(), onPageSizeChange: vi.fn() } : undefined,
}));

describe('table changes footer', () => {
  it('orders apply, undo, then status, with idle actions disabled', () => {
    const html = render(false);
    expect(html.indexOf('>应用更改<')).toBeLessThan(html.indexOf('>撤销<'));
    expect(html.indexOf('>撤销<')).toBeLessThan(html.indexOf('所有更改已应用'));
    expect(html.match(/disabled=""/g)).toHaveLength(2);
    expect(html).toContain('role="status"');
    expect(html).not.toMatch(/双击|拖动选择|Enter 确认|Esc 取消/);
  });
  it('enables both actions and announces unapplied edits', () => {
    const html = render(true);
    expect(html).not.toContain('disabled=""');
    expect(html).toContain('有未应用的更改');
  });
  it('includes record count, page navigation and page size only in data view', () => {
    const html = render(false, true);
    expect(html).toContain('共 24 条');
    expect(html).toContain('title="2"');
    expect(html).toContain('aria-label="每页记录数"');
    expect(render(false)).not.toContain('table-footer-pagination');
  });
  it('removes the top row and routes all table views through the bottom actions', () => {
    expect(app).not.toContain('className="edit-actions-bar"');
    expect(app).not.toContain('className="grid-footer"');
    expect(app).toContain("position:['none']");
    expect(app).toContain('<TableChangesFooter dirty={Boolean(pending||schemaDirty||formHasDraft||editing)} onApply={saveChanges} onRevert={revert}');
    expect(app).toContain("pagination={view==='数据'?");
  });
});
