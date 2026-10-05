import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { App, Button, Checkbox, Input, Modal, Segmented, Tooltip } from 'antd';
import { useStudio, activeDiagram } from '../store';
import { Icon } from './Icon';
import { SourceIcon } from '../../../design/Visuals';
import { engineLabel } from '../../../shared/presentation';
import { ResizeHandle } from './ResizeHandle';
import { errorText } from '../core/model';
function Highlight({ text, query }: { text: string; query: string }) {
  const i = query ? text.toLowerCase().indexOf(query) : -1;
  return i < 0 ? <>{text}</> : <>{text.slice(0, i)}<mark>{text.slice(i, i + query.length)}</mark>{text.slice(i + query.length)}</>;
}
export function EntityLibrary() {
  const { message } = App.useApp();
  const snapshot = useStudio(s => s.data?.snapshot), diagram = useStudio(activeDiagram), selected = useStudio(s => s.selectedEntityId), width = useStudio(s => s.libraryWidth);
  const [query, setQuery] = useState(''), [closed, setClosed] = useState<string[]>([]), [selection, setSelection] = useState<string[]>([]);
  const [sourceDialog,setSourceDialog]=useState(false),[sourceQuery,setSourceQuery]=useState(''),[sourceSelection,setSourceSelection]=useState<string[]>([]),[sourceStatus,setSourceStatus]=useState<'all'|'joined'|'available'>('all');
  const q = useDeferredValue(query.trim().toLowerCase());
  const [limit,setLimit]=useState(200);useEffect(()=>setLimit(200),[q,diagram?.librarySourceIds]);
  useEffect(()=>{
    const scrollToSource=(event:Event)=>{
      const id=(event as CustomEvent<string>).detail;
      [...document.querySelectorAll<HTMLElement>('.entity-library .source-group')].find(group=>group.dataset.sourceId===id)?.scrollIntoView({block:'start'});
    };
    window.addEventListener('wanxiang-scroll-source',scrollToSource);
    return ()=>window.removeEventListener('wanxiang-scroll-source',scrollToSource);
  },[]);
  const added = useMemo(() => new Set(diagram?.nodes.map(n => n.entityId)), [diagram?.nodes]);
  const index = useMemo(() => snapshot?.entities.map(e => ({ entity: e, text: `${e.name} ${e.displayName ?? ''} ${e.comment ?? ''} ${e.fields.map(f => `${f.name} ${f.path} ${f.comment ?? ''}`).join(' ')}`.toLowerCase() })) ?? [], [snapshot]);
  if (!snapshot) return null;
  const librarySourceIds=new Set(diagram?.librarySourceIds??[]);
  const joinedSources=snapshot.sources.filter(source=>librarySourceIds.has(source.id));
  const occupiedSources=new Set(snapshot.entities.filter(entity=>added.has(entity.id)).map(entity=>entity.sourceId));
  const filteredSources=snapshot.sources.filter(source=>(sourceStatus==='all'||(sourceStatus==='joined')===librarySourceIds.has(source.id))&&source.name.toLowerCase().includes(sourceQuery.trim().toLowerCase()));
  const sourceSelectionChanged=snapshot.sources.some(source=>sourceSelection.includes(source.id)!==librarySourceIds.has(source.id));
  const scopedCount=snapshot.entities.filter(entity=>librarySourceIds.has(entity.sourceId)).length;
  const matched = index.filter(({ entity: e, text }) => librarySourceIds.has(e.sourceId) && (!q || text.includes(q))).map(x => x.entity);
  const entities=matched.slice(0,limit);
  const checked = entities.filter(e => selection.includes(e.id));
  const additions = checked.filter(e => !added.has(e.id));
  function add(ids: string[]) {
    try {
      const state = useStudio.getState(); state.setFocus(null); state.addEntities(ids);
      if (ids.length === 1) requestAnimationFrame(() => requestAnimationFrame(() => window.dispatchEvent(new CustomEvent('wanxiang-focus-entity', { detail: ids[0] }))));
      else { setSelection([]); message.success(`已添加 ${ids.length} 个实体，可用“适应画布”查看。`); }
    } catch (e) { message.error(errorText(e)); }
  }
  function openSourceDialog(){setSourceSelection([...librarySourceIds]);setSourceStatus('all');setSourceQuery('');setSourceDialog(true);}
  function closeSourceDialog(){setSourceDialog(false);setSourceSelection([]);setSourceQuery('');}
  function saveSources(){
    if(!diagram||!snapshot||!sourceSelectionChanged)return;
    useStudio.getState().updateView({librarySourceIds:snapshot.sources.filter(source=>sourceSelection.includes(source.id)||occupiedSources.has(source.id)).map(source=>source.id)});
    closeSourceDialog();
    message.success('当前视图的数据源已更新。');
  }
  return <aside className="entity-library" aria-label="数据库对象库" style={{ width, flexBasis: width }}>
    <div className="library-heading"><h2><Icon name="database" size={18} />对象库<span className="counter">{scopedCount}</span></h2><Tooltip title="收起对象库"><Button type="text" size="small" aria-label="收起对象库" icon={<Icon name="panels" size={16} />} onClick={() => useStudio.getState().setLibraryOpen(false)} /></Tooltip></div>
    <div className="library-search"><Input id="entity-search" data-testid="entity-search" placeholder="筛选数据表或字段" prefix={<Icon name="search" size={15} />} value={query} onChange={e => setQuery(e.target.value)} allowClear onPressEnter={() => { if (entities[0]) add([entities[0].id]); }} />
      <Button className="library-add-source" size="small" icon={<Icon name="plus" size={13}/>} onClick={openSourceDialog}>引入其他数据源</Button>
    </div>
    {!!entities.length && <div className="library-batch-row"><Checkbox checked={checked.length === entities.length} indeterminate={checked.length > 0 && checked.length < entities.length} onChange={e => setSelection(e.target.checked ? entities.map(x => x.id) : [])}>选择当前结果</Checkbox>{checked.length > 0 && <Button type="link" size="small" disabled={!additions.length} onClick={() => add(additions.map(e => e.id))}>添加 {additions.length} 项</Button>}</div>}
    <div className="library-scroll">
      {joinedSources.map(source => {
        const objects = entities.filter(e => e.sourceId === source.id); if (!objects.length) return null;
        return <section className="source-group" key={source.id} data-source-id={source.id}>
          <button className="source-group-title" onClick={() => setClosed(c => c.includes(source.id) ? c.filter(x => x !== source.id) : [...c, source.id])} aria-expanded={!closed.includes(source.id) || !!q}>
            <Icon name="chevron" size={12} className={closed.includes(source.id) && !q ? '' : 'chevron-open'} /><SourceIcon small engine={source.engine}/><span className="source-title" title={source.name}>{source.name.replace(/^国家广电总局[—-]/, '')}</span><span className="source-count">{objects.length}</span>
          </button>
          <div className="source-subtitle">{engineLabel(source.engine)}<span>·</span>{objects[0]?.schemaName ?? objects[0]?.catalog ?? 'metadata'}</div>
          {(!closed.includes(source.id) || !!q) && <div className="source-objects">{objects.map(e => {
            const matchingField = q ? e.fields.find(f => `${f.name} ${f.path} ${f.comment ?? ''}`.toLowerCase().includes(q)) : undefined;
            return <div key={e.id} className={`library-entity ${added.has(e.id) ? 'is-added' : ''} ${selected === e.id ? 'is-active' : ''} ${selection.includes(e.id) ? 'is-checked' : ''}`} draggable
              onDragStart={event => { event.dataTransfer.setData('application/wanxiang-entity', e.id); event.dataTransfer.effectAllowed = 'copy'; }} onDoubleClick={() => add([e.id])}>
              <Checkbox className="library-row-check" checked={selection.includes(e.id)} aria-label={`选择${e.displayName ?? e.name}`} onChange={event => setSelection(ids => event.target.checked ? [...new Set([...ids, e.id])] : ids.filter(x => x !== e.id))} />
              <button className="entity-name-button" onClick={() => useStudio.getState().selectEntity(e.id, true)} title={`${e.displayName ?? e.name} · ${e.fields.length} 个字段`}><span className="library-table-icon"><Icon name={e.kind === 'collection' ? 'braces' : 'table'} size={16} /></span><span className="library-entity-text"><strong><Highlight text={e.displayName ?? e.name} query={q} /></strong><span><Highlight text={e.name} query={q} /></span>{matchingField && <small className="field-match">字段：<Highlight text={matchingField.name} query={q} /></small>}</span></button>
              <Tooltip title={added.has(e.id) ? '定位到画布' : '添加到画布'}><button className={`add-entity-button ${added.has(e.id) ? 'added' : ''}`} aria-label={`${added.has(e.id) ? '定位' : '添加'}${e.displayName ?? e.name}`} onClick={() => add([e.id])}><Icon name={added.has(e.id) ? 'fit' : 'plus'} size={15} /></button></Tooltip>
            </div>;
          })}</div>}
        </section>;
      })}
      {matched.length>limit&&<Button type="link" block onClick={()=>setLimit(n=>n+200)}>加载更多对象（{entities.length} / {matched.length}）</Button>}
      {!entities.length && <div className="library-empty"><Icon name="search" size={26} /><p>{!joinedSources.length?'当前视图尚未引入数据源':scopedCount?'没有匹配的表或字段':'已引入的数据源暂无已采集表'}</p>{joinedSources.length>0&&!!q&&<Button type="link" onClick={() => setQuery('')}>清除搜索</Button>}{!joinedSources.length&&<Button type="link" onClick={openSourceDialog}>选择数据源</Button>}</div>}
    </div>
    <Modal title="选择当前视图的数据源" open={sourceDialog} width={560} okText="保存选择" okButtonProps={{disabled:!sourceSelectionChanged}} onOk={saveSources} onCancel={closeSourceDialog}>
      <div className="library-source-toolbar"><Input placeholder="搜索数据源名称" allowClear value={sourceQuery} onChange={e=>setSourceQuery(e.target.value)}/><Segmented value={sourceStatus} onChange={value=>setSourceStatus(value as 'all'|'joined'|'available')} options={[{label:'全部',value:'all'},{label:'已引入',value:'joined'},{label:'未引入',value:'available'}]}/></div>
      <div className="library-source-list">{filteredSources.map(source=><label key={source.id} title={occupiedSources.has(source.id)?'画布中还有此数据源的表，需先移除这些表才能取消引入':undefined}><Checkbox checked={sourceSelection.includes(source.id)} disabled={occupiedSources.has(source.id)} onChange={e=>setSourceSelection(ids=>e.target.checked?[...new Set([...ids,source.id])]:ids.filter(id=>id!==source.id))}/><SourceIcon small engine={source.engine}/><span>{source.name}</span></label>)}{!filteredSources.length&&<div className="library-source-empty">没有匹配的数据源</div>}</div>
    </Modal>
    <ResizeHandle side="library" width={width} onChange={value => useStudio.getState().setPanelWidth('library', value)} />
  </aside>;
}
