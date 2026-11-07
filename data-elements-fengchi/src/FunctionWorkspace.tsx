import { useMemo, useState } from 'react';
import { App, Button, Dropdown, Empty, Input, Tabs, Tooltip, type MenuProps } from 'antd';
import { CopyOutlined, DeleteOutlined, FileAddOutlined, FunctionOutlined, SaveOutlined, SearchOutlined, UndoOutlined } from '@ant-design/icons';
import CodeMirror from '@uiw/react-codemirror';
import { MySQL, sql } from '@codemirror/lang-sql';
import DraggableModal, { draggableModalRender } from './DraggableModal';
import { functionSql, nextFunctionName, parseFunctionDefinitions, validateFunction, type FunctionDefinition } from './functionDefinitions';
import './functionWorkspace.css';

const storageKey = 'navicat-workbench.functions.v1';
const extensions = [sql({ dialect: MySQL })];

export default function FunctionWorkspace({ theme }: { theme: 'light' | 'dark' }) {
  const { modal, message } = App.useApp();
  const [loaded] = useState(() => {
    try { return { items: parseFunctionDefinitions(localStorage.getItem(storageKey)), error: '' }; }
    catch { return { items: [] as FunctionDefinition[], error: '无法读取浏览器中的函数定义。原有存储未被覆盖，请检查浏览器存储设置。' }; }
  });
  const [items, setItems] = useState(loaded.items);
  const [selectedId, setSelectedId] = useState(loaded.items[0]?.id ?? '');
  // Drafts stay independent of selection, so switching rows never discards edits.
  const [drafts, setDrafts] = useState<Record<string, FunctionDefinition>>({});
  const [search, setSearch] = useState('');
  const [preview, setPreview] = useState<FunctionDefinition | null>(null);
  const selected = items.find(fn => fn.id === selectedId);
  const current = drafts[selectedId] ?? selected;
  const dirty = !!current && JSON.stringify(current) !== JSON.stringify(selected);
  const filtered = useMemo(() => items.filter(fn => `${(drafts[fn.id] ?? fn).name} ${fn.comment}`.toLowerCase().includes(search.toLowerCase())), [items, drafts, search]);

  function persist(next: FunctionDefinition[]) {
    if (loaded.error) { message.error(loaded.error); return false; }
    try { localStorage.setItem(storageKey, JSON.stringify(next)); setItems(next); return true; }
    catch { message.error('保存失败：浏览器存储不可用或空间不足。编辑内容仍保留。'); return false; }
  }
  function patch(value: Partial<FunctionDefinition>) {
    if (current) setDrafts(prev => ({ ...prev, [current.id]: { ...current, ...value } }));
  }
  function clearDraft(id: string) { setDrafts(prev => { const next = { ...prev }; delete next[id]; return next; }); }
  function save() {
    if (!current) return;
    const normalized = { ...current, name: current.name.trim(), returnType: current.returnType.trim() };
    const error = validateFunction(normalized, items);
    if (error) { message.error(error); return; }
    if (persist(items.map(fn => fn.id === current.id ? normalized : fn))) { clearDraft(current.id); message.success('函数定义已保存到当前浏览器'); }
  }
  function create(source?: FunctionDefinition) {
    const next: FunctionDefinition = {
      id: crypto.randomUUID(), name: nextFunctionName(source ? `${source.name}_copy` : 'new_function', [...items, ...Object.values(drafts)]),
      parameters: source?.parameters ?? '', returnType: source?.returnType ?? 'INT',
      comment: source?.comment ?? '', body: source?.body ?? 'BEGIN\n  RETURN 0;\nEND',
    };
    if (persist([...items, next])) { setSelectedId(next.id); setSearch(''); }
  }
  function remove(fn: FunctionDefinition) {
    modal.confirm({ title: `删除函数“${fn.name}”？`, content: '仅删除当前浏览器中的定义及其未保存编辑，不会修改数据库。', okText: '删除', cancelText: '取消', okButtonProps: { danger: true }, maskClosable: true, className: 'native-confirm-dialog', modalRender: draggableModalRender,
      onOk: () => { const next = items.filter(item => item.id !== fn.id); if (!persist(next)) return Promise.reject(new Error('保存失败')); clearDraft(fn.id); if (selectedId === fn.id) setSelectedId(next[0]?.id ?? ''); },
    });
  }
  function revert() {
    if (!current) return;
    const id = current.id;
    modal.confirm({ title: '放弃此函数的未保存修改？', content: '恢复到上次保存在当前浏览器中的定义。', okText: '放弃修改', cancelText: '继续编辑', maskClosable: true, className: 'native-confirm-dialog', modalRender: draggableModalRender, onOk: () => clearDraft(id) });
  }
  async function copy(fn: FunctionDefinition) {
    try { await navigator.clipboard.writeText(functionSql(fn)); message.success('CREATE 语句已复制'); }
    catch { setPreview(fn); message.info('无法访问剪贴板，请在定义窗口中选择并复制 SQL'); }
  }
  function menu(fn: FunctionDefinition): MenuProps {
    const value = drafts[fn.id] ?? fn;
    return { items: [
      { key: 'open', label: '设计函数' }, { key: 'preview', label: '查看 CREATE 语句' }, { key: 'copy', label: '复制 CREATE 语句' },
      { type: 'divider' }, { key: 'new', label: '新建函数' }, { key: 'duplicate', label: '复制函数' }, { type: 'divider' }, { key: 'delete', label: '删除函数', danger: true },
    ], onClick: ({ key }) => { if (key === 'open') setSelectedId(fn.id); if (key === 'preview') setPreview(value); if (key === 'copy') void copy(value); if (key === 'new') create(); if (key === 'duplicate') create(value); if (key === 'delete') remove(value); } };
  }

  return <section className="function-workspace" aria-label="函数工作区">
    <div className="function-toolbar">
      <Button size="small" icon={<FileAddOutlined />} onClick={() => create()} disabled={!!loaded.error}>新建函数</Button>
      <Button size="small" icon={<SaveOutlined />} onClick={save} disabled={!dirty}>保存</Button>
      <Button size="small" icon={<UndoOutlined />} onClick={revert} disabled={!dirty}>还原</Button>
      <Button size="small" icon={<CopyOutlined />} onClick={() => current && create(current)} disabled={!current}>复制函数</Button>
      <Button size="small" icon={<DeleteOutlined />} onClick={() => current && remove(current)} disabled={!current}>删除</Button>
      <span className="function-toolbar-note">MySQL · 函数</span>
    </div>
    {loaded.error && <div role="alert" className="function-error">{loaded.error}</div>}
    <div className="function-main">
      <aside className="function-list">
        <Input size="small" aria-label="搜索函数" prefix={<SearchOutlined />} placeholder="搜索函数" value={search} allowClear onChange={e => setSearch(e.target.value)} />
        <div className="function-list-heading">名称 <span>返回类型</span></div>
        <div className="function-list-rows">
          {filtered.map(fn => { const value = drafts[fn.id] ?? fn; const changed = JSON.stringify(value) !== JSON.stringify(fn); return <Dropdown key={fn.id} menu={menu(fn)} trigger={['contextMenu']}>
            <button className={`function-row ${fn.id === selectedId ? 'selected' : ''}`} onClick={() => setSelectedId(fn.id)} aria-pressed={fn.id === selectedId} title={value.comment || value.name}>
              <FunctionOutlined /><span className="function-row-name">{value.name}{changed ? ' *' : ''}</span><span className="function-row-type">{value.returnType}</span>
            </button>
          </Dropdown>; })}
          {!filtered.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={search ? '没有匹配的函数' : '暂无函数'} />}
        </div>
        <div className="function-list-footer">{filtered.length} 个函数</div>
      </aside>
      <div className="function-designer">
        {current ? <>
          <div className="function-title"><FunctionOutlined /> {current.name}<span>{dirty ? '未保存' : '已保存到当前浏览器'}</span></div>
          <div className="function-fields">
            <label>名称<Input size="small" value={current.name} onChange={e => patch({ name: e.target.value })} /></label>
            <label>返回类型<Input size="small" value={current.returnType} placeholder="例如 DECIMAL(12,2)" onChange={e => patch({ returnType: e.target.value })} /></label>
            <label className="function-wide">参数<Input size="small" value={current.parameters} placeholder="例如 p_id INT, p_name VARCHAR(100)；无参数可留空" onChange={e => patch({ parameters: e.target.value })} /></label>
            <label className="function-wide">注释<Input size="small" value={current.comment} onChange={e => patch({ comment: e.target.value })} /></label>
          </div>
          <Tabs className="function-editor-tabs" size="small" items={[
            { key: 'definition', label: '定义', children: <CodeMirror aria-label="函数 SQL 定义" value={current.body} onChange={body => patch({ body })} extensions={extensions} theme={theme} height="100%" /> },
            { key: 'ddl', label: 'CREATE 语句', children: <CodeMirror aria-label="函数 CREATE 语句" value={functionSql(current)} editable={false} extensions={extensions} theme={theme} height="100%" /> },
          ]} tabBarExtraContent={<Tooltip title="复制当前编辑内容的 CREATE 语句"><Button size="small" type="text" icon={<CopyOutlined />} onClick={() => void copy(current)}>复制 SQL</Button></Tooltip>} />
        </> : <Empty description="选择一个函数，或新建函数" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
      </div>
    </div>
    <div className="function-status">定义保存在当前浏览器；执行尚未启用。切换函数会保留未保存的编辑。</div>
    <DraggableModal open={!!preview} title={`函数定义 · ${preview?.name ?? ''}`} width={760} onCancel={() => setPreview(null)} footer={<Button onClick={() => setPreview(null)}>关闭</Button>}>
      <CodeMirror aria-label="查看函数 CREATE 语句" value={preview ? functionSql(preview) : ''} editable={false} theme={theme} extensions={extensions} height="360px" />
    </DraggableModal>
  </section>;
}
