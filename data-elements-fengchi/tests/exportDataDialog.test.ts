import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import type { ModalProps } from 'antd';
import ExportDataDialog, { type ExportDataDialogProps } from '../src/ExportDataDialog';

// The shared draggable modal is tested separately. Render its content inline so
// this suite checks the export controls and actual confirmation callbacks in Node.
const modal = vi.hoisted(() => ({ props: null as ModalProps | null }));
vi.mock('../src/DraggableModal', () => ({ default: (props: ModalProps) => {
  modal.props = props;
  return createElement('section', { role: 'dialog' }, props.title, props.children,
    createElement('button', { ...props.okButtonProps }, props.okText),
    createElement('button', null, props.cancelText));
} }));

const defaults = (): ExportDataDialogProps => ({
  open: true, title: 'users', columns: ['id', 'name'],
  sources: [
    { key: 'filtered', label: '筛选结果', rows: [{ id: 2, name: 'Two' }, { id: 1, name: 'One' }] },
    { key: 'all', label: '全部记录', rows: [{ id: 1, name: 'One' }, { id: 2, name: 'Two' }, { id: 3, name: 'Three' }] },
    { key: 'selected', label: '已选记录', rows: [] },
    { key: 'current-page', label: '当前页', rows: [{ id: 2, name: 'Two' }] },
  ], onCancel: vi.fn(), onConfirm: vi.fn(),
});
const render = (props = defaults()) => renderToStaticMarkup(createElement(ExportDataDialog, props));
const confirm = () => modal.props?.onOk?.({} as Parameters<NonNullable<ModalProps['onOk']>>[0]);
const cancel = () => modal.props?.onCancel?.({} as Parameters<NonNullable<ModalProps['onCancel']>>[0]);

beforeEach(() => { modal.props = null; });

describe('export confirmation dialog', () => {
  it('renders scope counts, quantity, all real formats, preview and encoding policies', () => {
    const html = render();
    for (const label of ['导出数据', '导出范围', '筛选结果（2 条）', '全部记录（3 条）', '已选记录（0 条）', '当前页（1 条）', '导出数量', '前 N 条', '文件格式', 'CSV（逗号分隔）', 'TSV（制表符分隔）', 'JSON（保留数据类型）', '确认导出', '取消', 'UTF-8', 'NULL 输出为空单元格', '单引号保护']) expect(html).toContain(label);
    expect(html).toContain('将导出 2 条记录 · 2 列');
    expect(html).toContain('users.csv');
    expect(html).toContain('aria-label="导出条数"');
    expect(html).toContain('包含已确认但未应用的修改，不访问真实数据库');
    expect(html).toContain('min="1" max="2" step="1"');
    expect(modal.props?.okButtonProps?.disabled).toBe(false);
  });
  it('does nothing while closed and never confirms on render or cancel', () => {
    const props = defaults();
    expect(render({ ...props, open: false })).toBe('');
    expect(modal.props).toBe(null);
    render(props);
    expect(props.onConfirm).not.toHaveBeenCalled();
    cancel();
    expect(props.onCancel).toHaveBeenCalledOnce();
    expect(props.onConfirm).not.toHaveBeenCalled();
  });
  it('prepares the selected snapshot only on confirmation, and ignores a repeated click', () => {
    const props = defaults();
    render({ ...props, initialScope: 'current-page' });
    expect(props.onConfirm).not.toHaveBeenCalled();
    confirm();
    confirm();
    expect(props.onConfirm).toHaveBeenCalledOnce();
    expect(props.onConfirm).toHaveBeenCalledWith({ filename: 'users.csv', mime: 'text/csv;charset=utf-8', format: 'csv', scope: 'current-page', count: 1, content: '\uFEFFid,name\r\n2,Two\r\n' });
  });
  it('keeps an explicitly empty selection and disables confirmation rather than exporting all rows', () => {
    const props = { ...defaults(), initialScope: 'selected' };
    const html = render(props);
    expect(html).toContain('value="selected" selected=""');
    expect(html).toContain('此范围内没有可导出的记录');
    expect(html).toContain('将导出 0 条记录');
    expect(modal.props?.okButtonProps?.disabled).toBe(true);
    confirm();
    expect(props.onConfirm).not.toHaveBeenCalled();
  });
  it('handles missing sources or columns as disabled empty states', () => {
    for (const overrides of [{ sources: [] }, { columns: [] }]) {
      const props = { ...defaults(), ...overrides };
      render(props);
      expect(modal.props?.okButtonProps?.disabled).toBe(true);
      confirm();
      expect(props.onConfirm).not.toHaveBeenCalled();
    }
  });
  it('pins opened rows, then takes a fresh snapshot and resets defaults when reopened', () => {
    const props = defaults();
    render(props);
    props.sources[0].rows[0].name = 'changed';
    confirm();
    expect(vi.mocked(props.onConfirm).mock.calls[0][0].content).toContain('2,Two');
    render({ ...props, open: false });
    const html = render(props);
    expect(html).toContain('users.csv');
    expect(html).toContain('全部 2 条');
    confirm();
    expect(props.onConfirm).toHaveBeenCalledTimes(2);
    expect(vi.mocked(props.onConfirm).mock.calls[1][0].content).toContain('2,changed');
  });
  it('does not call the download callback for invalid source numbers', () => {
    const props = { ...defaults(), sources: [{ key: 'all', label: '全部记录', rows: [{ id: Infinity }] }] };
    render(props);
    confirm();
    expect(props.onConfirm).not.toHaveBeenCalled();
  });
  it('allows an explicit retry if the download callback reports a failure', () => {
    const props = defaults();
    vi.mocked(props.onConfirm).mockImplementationOnce(() => { throw new Error('download blocked'); });
    render(props);
    confirm();
    confirm();
    expect(props.onConfirm).toHaveBeenCalledTimes(2);
  });
});
