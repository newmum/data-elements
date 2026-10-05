import { useEffect, useMemo, useState } from 'react';
import { App, Empty, Input, Modal } from 'antd';
import { activeDiagram, useStudio } from '../store';
import { searchWorkspace } from '../core/search';
import type { SearchResult } from '../core/search';
import { errorText } from '../core/model';
import { Icon } from './Icon';
export function SearchPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const data = useStudio(s => s.data), diagram = useStudio(activeDiagram);
  const { message } = App.useApp();
  const [query, setQuery] = useState(''), [index, setIndex] = useState(0);
  useEffect(() => { if (open) { setQuery(''); setIndex(0); } }, [open]);
  const results = useMemo(() => data ? searchWorkspace(data.snapshot, data.diagrams, query) : [], [data?.snapshot, data?.diagrams, query]);
  useEffect(() => { setIndex(0); }, [query]);
  useEffect(() => { document.getElementById(`search-result-${index}`)?.scrollIntoView({ block: 'nearest' }); }, [index]);
  async function pick(result: SearchResult) {
    const s = useStudio.getState();
    try {
      if (result.kind === 'diagram') await s.switchDiagram(result.id);
      else {
        s.setPage('canvas'); s.setFocus(null); s.addEntities([result.id]);
        if (result.fieldId) s.pinField(result.id, result.fieldId);
        requestAnimationFrame(() => requestAnimationFrame(() => window.dispatchEvent(new CustomEvent('wanxiang-focus-entity', { detail: result.id }))));
      }
      onClose();
    } catch (e) { message.error(errorText(e)); }
  }
  return <Modal open={open} onCancel={onClose} footer={null} closeIcon={null} width={640} className="search-palette" destroyOnHidden
    afterOpenChange={visible => { if (visible) document.getElementById('workspace-search')?.focus(); }}
    styles={{ body: { padding: 0 } }} title={null}>
    <div className="palette-input"><Icon name="search" size={21} /><Input id="workspace-search" placeholder="搜索数据表、字段或关系图" variant="borderless" value={query}
      role="combobox" aria-label="快速查找" aria-controls="workspace-search-results" aria-expanded={open} aria-activedescendant={results.length ? `search-result-${index}` : undefined}
      onChange={e => setQuery(e.target.value)} allowClear
      onKeyDown={e => { if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); setIndex(i => results.length ? (i + (e.key === 'ArrowDown' ? 1 : -1) + results.length) % results.length : 0); } if (e.key === 'Enter' && results[index]) { e.preventDefault(); pick(results[index]); } }} /><kbd>ESC</kbd></div>
    <div className="palette-caption">{query ? `匹配结果 · 最多显示 40 项` : '浏览对象与关系图'}<span>Enter 定位 · ↑ ↓ 选择</span></div>
    <div className="palette-results" id="workspace-search-results" role="listbox" aria-label="查找结果">
      {results.map((r, i) => <button key={r.key} id={`search-result-${i}`} className={`palette-result ${i === index ? 'active' : ''}`} role="option" aria-selected={i === index} onMouseEnter={() => setIndex(i)} onClick={() => pick(r)}>
        <span className="palette-result-icon"><Icon name={r.kind === 'diagram' ? 'layout' : r.kind === 'field' ? 'key' : 'table'} size={19} /></span>
        <span className="palette-result-label"><strong>{r.title}</strong><small>{r.subtitle}</small></span>
        <span className="palette-result-type">{r.kind === 'diagram' ? '关系图' : r.kind === 'field' ? '字段' : diagram?.nodes.some(n => n.entityId === r.id) ? '已在画布' : '数据表'}</span><Icon name="arrow" size={14} />
      </button>)}
      {!results.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配的表、字段或关系图" />}
    </div><div className="palette-footer"><Icon name="info" size={13} />选择字段会将其固定显示在对应节点中。</div>
  </Modal>;
}
