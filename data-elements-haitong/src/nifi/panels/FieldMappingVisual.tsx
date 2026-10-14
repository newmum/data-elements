import {useEffect,useLayoutEffect,useMemo,useRef,useState} from 'react';
import {Button,Dropdown,Empty,Input,Select,Space,Tag,Tooltip} from 'antd';
import {DeleteOutlined,PauseCircleOutlined,PlayCircleOutlined,PlusOutlined,SearchOutlined} from '@ant-design/icons';
import type {FieldMeta,FieldMappingSpec,MappingRule,UnifiedMappingDraft,UnifiedMappingRule} from '../api/fieldMapping';
import {inputSummary,stepSummary} from './unifiedRuleModel';
import {displayPath,fieldKey,fieldName,fieldsForPresentation,mergeFields,normalizePath,patchVisualField,sourceNames,toFieldInfo,visualMapping,type FieldInfo,type FieldSide,type FieldType} from './fieldMappingPresentation';
type RowKind='field'|'many'|'constant'|'expression'|'lookup'|'conditional';
interface EdgePath {id:string;d:string;state:'idle'|'active'|'disabled'}
const ADD_MENU_ITEMS=[{key:'DIRECT',label:'字段映射'},{key:'CONCAT',label:'多字段合并'},{key:'CONSTANT',label:'常量赋值'},{key:'EXPRESSION',label:'表达式映射'},{key:'LOOKUP',label:'字典查询映射'},{key:'CONDITIONAL',label:'条件映射'}];
interface Props {
 draft:UnifiedMappingDraft;sourceMeta:FieldMeta[];targetMeta:FieldMeta[];
 sourceLabels:Record<string,string>;targetLabels:Record<string,string>;
 onOpen:(rule:UnifiedMappingRule)=>void;onChange:(rule:UnifiedMappingRule)=>void;
 onCreate:(source:string,target:string)=>void;onAdd:(template:string)=>void;onDelete:(id:string)=>void;onMove:(id:string,offset:number)=>void;
}
export default function FieldMappingVisual(props:Props){
 const rootRef=useRef<HTMLDivElement>(null);
 const [pendingSource,setPendingSource]=useState<string|null>(null),[hoverField,setHoverField]=useState<string|null>(null),[hoverIndex,setHoverIndex]=useState<number|null>(null),[selected,setSelected]=useState<string|null>(null);
 const [customSources,setCustomSources]=useState<FieldInfo[]>([]),[customTargets,setCustomTargets]=useState<FieldInfo[]>([]),[edges,setEdges]=useState<EdgePath[]>([]);
 const mappings=useMemo(()=>props.draft.rules.map(visualMapping),[props.draft.rules]);
 const spec=useMemo<FieldMappingSpec>(()=>({version:'1.0',mappings}),[mappings]);
 const sourceFields=useMemo(()=>fieldsForPresentation(props.sourceMeta,props.sourceLabels,props.draft.rules.flatMap(sourceNames),customSources),[props.sourceMeta,props.sourceLabels,props.draft.rules,customSources]);
 const targetFields=useMemo(()=>fieldsForPresentation(props.targetMeta,props.targetLabels,props.draft.rules.map(r=>r.target.name),customTargets),[props.targetMeta,props.targetLabels,props.draft.rules,customTargets]);
 const dense=sourceFields.length>120||targetFields.length>120||mappings.length>120;
 const active=useMemo(()=>dense&&hoverIndex==null?new Set<number>():collectRelatedMappingIndexes(spec,hoverField??pendingSource,hoverIndex),[dense,spec,hoverField,pendingSource,hoverIndex]);
 const highlighted=useMemo(()=>dense&&hoverIndex==null?new Set<string>():collectRelatedFieldKeys(spec,hoverField??pendingSource,hoverIndex),[dense,spec,hoverField,pendingSource,hoverIndex]);
 useLayoutEffect(()=>{
  const root=rootRef.current,canvas=root?.querySelector<HTMLElement>('.fm-canvas');
  if(!root||!canvas||dense){setEdges([]);return;}
  let frame=0;
  const calculate=()=>{
   const bounds=canvas.getBoundingClientRect(),width=canvas.clientWidth,paths:EdgePath[]=[];
   const fieldRows=new Map<string,HTMLElement>();
   root.querySelectorAll<HTMLElement>('.fm-field-row').forEach(row=>fieldRows.set(row.dataset.side+':'+row.dataset.fieldPath,row));
   const cards=new Map<number,HTMLElement>();
   root.querySelectorAll<HTMLElement>('.fm-mapping-card').forEach(card=>cards.set(Number(card.dataset.mappingIndex),card));
   mappings.forEach((mapping,index)=>{
    const card=cards.get(index);if(!card)return;
    const rect=card.getBoundingClientRect(),xIn=Math.max(8,rect.left-bounds.left),xOut=Math.min(width-8,rect.right-bounds.left),y=rect.top+rect.height/2-bounds.top+canvas.scrollTop;
    pathsFromMapping(mapping).forEach((path,i)=>{const row=fieldRows.get('source:'+normalizePath(path));if(!row)return;const r=row.getBoundingClientRect();paths.push({id:'s-'+index+'-'+i,d:bezier(4,r.top+r.height/2-bounds.top+canvas.scrollTop,xIn,y),state:edgeState(mapping,index,active)});});
    const row=fieldRows.get('target:'+normalizePath(mapping.to??''));
    if(row){const r=row.getBoundingClientRect();paths.push({id:'t-'+index,d:bezier(xOut,y,width-4,r.top+r.height/2-bounds.top+canvas.scrollTop),state:edgeState(mapping,index,active)});}
   });setEdges(paths);
  };
  const schedule=()=>{cancelAnimationFrame(frame);frame=requestAnimationFrame(calculate);};
  const observer=new ResizeObserver(schedule);observer.observe(root);observer.observe(canvas);
  const scrollAreas=root.querySelectorAll<HTMLElement>('.fm-field-list,.fm-canvas');scrollAreas.forEach(el=>el.addEventListener('scroll',schedule,{passive:true}));
  schedule();return()=>{observer.disconnect();cancelAnimationFrame(frame);scrollAreas.forEach(el=>el.removeEventListener('scroll',schedule));};
 },[mappings,sourceFields,targetFields,active,dense]);
 function create(from:string,to:string){props.onCreate(displayPath(from),displayPath(to));setPendingSource(null);}
 return <div className="fm-layout fm-restored-visual" ref={rootRef}>
  <FieldPanel side="source" title="源字段" subtitle={sourceFields.length+' 个字段'} fields={sourceFields} mappings={mappings} pendingSource={pendingSource} highlightedFieldKeys={highlighted} targetFields={targetFields} onFieldClick={setPendingSource} onFieldHover={setHoverField} onAddCustom={field=>setCustomSources(items=>mergeFields(items,[field]))} onQuickMap={create}/>
  <section className="fm-canvas"><EdgeLayer paths={edges}/><div className="fm-card-list">
   {!props.draft.rules.length&&<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="从左侧选择源字段，再点击右侧目标字段创建映射"/>}
   {props.draft.rules.map((rule,index)=><MappingCard key={rule.ruleId} rule={rule} index={index} sourceFields={sourceFields} targetFields={targetFields} selected={selected===rule.ruleId} active={active.has(index)} onSelect={()=>setSelected(rule.ruleId)} onHover={value=>setHoverIndex(value?index:null)} onOpen={()=>props.onOpen(rule)} onUpdate={props.onChange} onField={(side,path)=>props.onChange(patchVisualField(rule,side,path,side==='source'?props.sourceMeta:props.targetMeta))} onDelete={()=>props.onDelete(rule.ruleId)} onMoveUp={()=>props.onMove(rule.ruleId,-1)} onMoveDown={()=>props.onMove(rule.ruleId,1)}/>)}
   <Dropdown trigger={['click']} menu={{items:ADD_MENU_ITEMS,onClick:({key})=>props.onAdd(key)}}><Button className="fm-add-card" icon={<PlusOutlined/>}>添加映射规则</Button></Dropdown>
  </div></section>
  <FieldPanel side="target" title="目标字段" subtitle={targetFields.length+' 个字段'} fields={targetFields} mappings={mappings} pendingSource={pendingSource} highlightedFieldKeys={highlighted} onFieldClick={path=>{if(pendingSource)create(pendingSource,path);else{const rule=props.draft.rules.find(r=>normalizePath(r.target.name)===path);if(rule)props.onOpen(rule);}}} onFieldHover={setHoverField} onAddCustom={field=>setCustomTargets(items=>mergeFields(items,[field]))}/>
 </div>;
}
function MappingCard({rule,index,sourceFields,targetFields,selected,active,onSelect,onHover,onOpen,onUpdate,onField,onDelete,onMoveUp,onMoveDown}:{rule:UnifiedMappingRule;index:number;sourceFields:FieldInfo[];targetFields:FieldInfo[];selected:boolean;active:boolean;onSelect:()=>void;onHover:(active:boolean)=>void;onOpen:()=>void;onUpdate:(rule:UnifiedMappingRule)=>void;onField:(side:FieldSide,path:string)=>void;onDelete:()=>void;onMoveUp:()=>void;onMoveDown:()=>void}){
 const step=rule.steps.find(s=>!s.uiRole),disabled=rule.enabled===false;
 const kind=rule.when!=null?'conditional':rule.input.kind==='LITERAL'?'constant':step?.operator.startsWith('LOOKUP')?'lookup':rule.input.kind==='EXPRESSION'||rule.input.kind==='SYSTEM'?'expression':step?.operator==='CONCAT'?'many':'field';
 const target=<FieldSelect side="target" value={normalizePath(rule.target.name)} fields={targetFields} onChange={path=>onField('target',path)}/>;
 const source=<FieldSelect side="source" value={normalizePath(rule.input.name??sourceNames(rule)[0]??'')} fields={sourceFields} onChange={path=>onField('source',path)}/>;
 const fx=<button className={step&&step.operator!=='DIRECT'?'fm-fx-badge':'fm-arrow'} title={rule.steps.length?rule.steps.map(stepSummary).join(' → '):'配置映射规则'} onClick={e=>{e.stopPropagation();onOpen();}}>{step&&step.operator!=='DIRECT'?<>fx<sup>{stepSummary(step)[0]?.toUpperCase()}</sup></>:'→'}</button>;
 return <article className={'fm-mapping-card is-'+kind+(selected?' is-selected':'')+(active?' is-active':'')+(disabled?' is-disabled':'')} data-mapping-index={index} onClick={onSelect} onDoubleClick={onOpen} onMouseEnter={()=>onHover(true)} onMouseLeave={()=>onHover(false)}>
  {kind!=='constant'&&<span className="fm-card-port fm-card-port-in" data-port="card-in"/>}
  {kind==='conditional'?<div className="fm-conditional-body">{['if','then','else'].map(label=><div className="fm-cond-line" key={label}><Tag>{label}</Tag><Button size="small" type="text" onClick={onOpen}>{label==='if'?'条件判断':label==='then'?'条件成立时的字段值':'条件不成立时的字段值'}</Button></div>)}{target}</div>
   :kind==='constant'?<><button className="fm-constant-chip" onClick={onOpen}>{rule.input.value!==null&&typeof rule.input.value==='object'?'JSON 常量':String(rule.input.value??'NULL')}</button>{fx}{target}</>
   :kind==='expression'?<><span className="fm-fx-large">fx</span>{rule.input.kind==='EXPRESSION'?<Input className="fm-expression-input" size="small" value={rule.input.expression??''} onChange={e=>onUpdate({...rule,input:{...rule.input,expression:e.target.value}})}/>:<Button type="text" size="small" onClick={onOpen}>{inputSummary(rule)}</Button>}{target}</>
   :kind==='lookup'?<>{source}<button className="fm-lookup-badge" onClick={e=>{e.stopPropagation();onOpen();}}>{step?.operator==='LOOKUP_MULTI'?'多值翻译':'码表'}</button>{target}</>
   :kind==='many'?<><span className="fm-merge-icon">⊕</span><MultiFieldSelect value={sourceNames(rule).map(normalizePath)} fields={sourceFields} onChange={names=>{const steps=rule.steps.map(s=>s===step?{...s,args:{...s.args,inputs:names.map(path=>({kind:'FIELD',name:displayPath(path)}))}}:s);onUpdate({...rule,steps});}}/>{fx}{target}</>
   :<>{source}{fx}{target}</>}
  <span className="fm-card-port fm-card-port-out" data-port="card-out"/>
  <div className="fm-card-actions"><Tooltip title={disabled?'启用此映射':'禁用此映射'}><Button size="small" type="text" icon={disabled?<PlayCircleOutlined/>:<PauseCircleOutlined/>} onClick={e=>{e.stopPropagation();onUpdate({...rule,enabled:disabled});}}/></Tooltip><Tooltip title="上移"><Button size="small" type="text" onClick={e=>{e.stopPropagation();onMoveUp();}}>↑</Button></Tooltip><Tooltip title="下移"><Button size="small" type="text" onClick={e=>{e.stopPropagation();onMoveDown();}}>↓</Button></Tooltip><Tooltip title="删除映射"><Button size="small" type="text" danger icon={<DeleteOutlined/>} onClick={e=>{e.stopPropagation();onDelete();}}/></Tooltip></div>
 </article>;
}
const TYPE_META: Record<FieldType, { label: string; bg: string; color: string }> = {
  STRING: { label: 'T', bg: '#E8F0FB', color: '#1A4FA0' },
  INT: { label: 'N', bg: '#FDF2E8', color: '#C26414' },
  DECIMAL: { label: 'N', bg: '#FDF2E8', color: '#C26414' },
  DATETIME: { label: 'D', bg: '#F0E8FB', color: '#7340C2' },
  DATE: { label: 'D', bg: '#F0E8FB', color: '#7340C2' },
  BOOL: { label: 'B', bg: '#E8F8F0', color: '#1F8A4C' },
  JSON: { label: 'J', bg: '#F4F0FB', color: '#5340C2' },
  BLOB: { label: 'X', bg: '#F0F2F6', color: '#5A6675' },
  GEO: { label: 'G', bg: '#E8F8F8', color: '#0E8A8A' },
};

function FieldPanel({ side, title, subtitle, fields, mappings, pendingSource, highlightedFieldKeys, targetFields = [], onFieldClick, onFieldHover, onAddCustom, onQuickMap }: {
  side: FieldSide;
  title: string;
  subtitle: string;
  fields: FieldInfo[];
  mappings: MappingRule[];
  pendingSource: string | null;
  highlightedFieldKeys: Set<string>;
  targetFields?: FieldInfo[];
  onFieldClick: (path: string) => void;
  onFieldHover: (path: string | null) => void;
  onAddCustom: (field: FieldInfo) => void;
  onQuickMap?: (from: string, to: string) => void;
}) {
  const [keyword, setKeyword] = useState('');
  const [adding, setAdding] = useState(false);
  const [newField, setNewField] = useState('');
  const [newLabel, setNewLabel] = useState('');
  const filtered = fields.filter((field) => !keyword || field.name.toLowerCase().includes(keyword.toLowerCase()) || (field.label ?? '').toLowerCase().includes(keyword.toLowerCase()));
  const sourceMap = collectMappedSources(mappings);
  const targetMap = collectMappedTargets(mappings);

  const addField = () => {
    const name = newField.trim();
    if (!name) return;
    onAddCustom(toFieldInfo(normalizePath(name), newLabel.trim() || undefined));
    setNewField('');
    setNewLabel('');
    setAdding(false);
  };

  return (
    <aside className={`fm-field-panel fm-field-panel-${side}`}>
      <header className="fm-panel-header">
        <div className="fm-panel-title">{title}</div>
        <div className="fm-panel-subtitle">{subtitle}</div>
      </header>
      <div className="fm-panel-search"><Input size="small" allowClear prefix={<SearchOutlined />} placeholder="搜索字段..." value={keyword} onChange={(e) => setKeyword(e.target.value)} /></div>
      <div className="fm-field-list">
        {filtered.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无字段" /> : filtered.map((field) => (
          <FieldRow
            key={field.path}
            side={side}
            field={field}
            active={pendingSource === field.path || highlightedFieldKeys.has(fieldKey(field.path))}
            mappedNames={side === 'source' ? sourceMap[field.path] ?? [] : targetMap[field.path] ?? []}
            onClick={() => onFieldClick(field.path)}
            onMouseEnter={() => onFieldHover(field.path)}
            onMouseLeave={() => onFieldHover(null)}
            menuTargets={targetFields}
            onQuickMap={onQuickMap}
          />
        ))}
      </div>
      <div className="fm-add-custom">
        {adding ? (
          <div className="fm-add-custom-form">
            <Input size="small" autoFocus value={newField} placeholder="字段名（必填）" onChange={(e) => setNewField(e.target.value)} onPressEnter={addField} />
            <Input size="small" value={newLabel} placeholder="中文名（可选）" onChange={(e) => setNewLabel(e.target.value)} onPressEnter={addField} />
            <Space.Compact style={{ width: '100%' }}>
              <Button size="small" type="primary" block onClick={addField}>添加</Button>
              <Button size="small" block onClick={() => { setAdding(false); setNewField(''); setNewLabel(''); }}>取消</Button>
            </Space.Compact>
          </div>
        ) : (
          <Button size="small" block icon={<PlusOutlined />} onClick={() => setAdding(true)}>添加自定义{side === 'source' ? '源' : '目标'}字段</Button>
        )}
      </div>
    </aside>
  );
}

function FieldRow({ side, field, active, mappedNames, menuTargets, onClick, onMouseEnter, onMouseLeave, onQuickMap }: {
  side: FieldSide;
  field: FieldInfo;
  active: boolean;
  mappedNames: string[];
  menuTargets: FieldInfo[];
  onClick: () => void;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onQuickMap?: (from: string, to: string) => void;
}) {
  const menu = side === 'source' && onQuickMap ? {
    items: [
      {
        key: 'same',
        label: `映射到同名目标 ${field.name}`,
        disabled: !menuTargets.some((target) => target.name === field.name),
        onClick: () => onQuickMap(field.path, normalizePath(field.name)),
      },
      {
        key: 'target',
        label: '映射到...',
        children: menuTargets.map((target) => ({ key: target.path, label: target.name, onClick: () => onQuickMap(field.path, target.path) })),
      },
    ],
  } : undefined;
  const row = (
    <button className={`fm-field-row ${active ? 'is-active' : ''} ${mappedNames.length ? 'is-mapped' : ''}`} data-side={side} data-field-path={field.path} onClick={onClick} onMouseEnter={onMouseEnter} onMouseLeave={onMouseLeave}>
      {side === 'target' && <MappingCount count={mappedNames.length} />}
      <span className="fm-port" data-port="true" />
      <span className="fm-field-info">
        {field.label ? <span className="fm-field-label">{field.label}</span> : null}
        <span className="fm-field-name">{field.name}</span>
      </span>
      {field.isPk && <span className="fm-pk-tag">PK</span>}
      <TypeBadge field={field} />
      {side === 'source' && <MappingCheck names={mappedNames} />}
    </button>
  );
  return menu ? <Dropdown menu={menu} trigger={['contextMenu']}>{row}</Dropdown> : row;
}

function MappingCheck({ names }: { names: string[] }) {
  if (!names.length) return <span className="fm-mapped-check" />;
  return <Tooltip title={`已映射到：${names.join(', ')}`}><span className="fm-mapped-check">✓</span></Tooltip>;
}

function MappingCount({ count }: { count: number }) {
  if (!count) return <span className="fm-source-count" />;
  return <span className={`fm-source-count ${count > 1 ? 'is-many' : ''}`}>✓ {count}</span>;
}

function TypeBadge({ field }: { field: FieldInfo }) {
  const meta = TYPE_META[field.type] ?? TYPE_META.STRING;
  return <Tooltip title={`${field.fullType ?? field.type}${field.nullable === false ? ' · NOT NULL' : ''}`}><span className="fm-type-badge" style={{ background: meta.bg, color: meta.color }}>{meta.label}</span></Tooltip>;
}

function EdgeLayer({ paths }: { paths: EdgePath[] }) {
  return (
    <svg className="fm-edge-layer">
      {paths.map((path) => <path key={path.id} className={`fm-edge is-${path.state}`} d={path.d} />)}
    </svg>
  );
}

function FieldSelect({ side, value, fields, onChange }: { side: FieldSide; value?: string; fields: FieldInfo[]; onChange: (path: string) => void }) {
  const options = useMemo(
    () => fields.map((field) => ({
      value: field.path,
      label: field.label ? `${field.label} ${field.name}` : field.name,
      searchText: `${field.path} ${field.label ?? ''} ${field.name}`.toLowerCase(),
    })),
    [fields]
  );
  return (
    <Select
      size="middle"
      showSearch
      value={value ? normalizePath(value) : undefined}
      className={`fm-field-chip fm-field-chip-${side}`}
      placeholder={side === 'source' ? '选择源字段' : '选择目标字段'}
      onChange={onChange}
      popupMatchSelectWidth={260}
      options={options}
      filterOption={(input, option) => String(option?.searchText ?? option?.value ?? '').includes(input.toLowerCase())}
    />
  );
}

function MultiFieldSelect({ value, fields, onChange }: { value: string[]; fields: FieldInfo[]; onChange: (paths: string[]) => void }) {
  const options = useMemo(
    () => fields.map((field) => ({
      value: field.path,
      label: field.label ? `${field.label} ${field.name}` : field.name,
      searchText: `${field.path} ${field.label ?? ''} ${field.name}`.toLowerCase(),
    })),
    [fields]
  );
  return <Select mode="multiple" size="middle" value={value.filter(Boolean).map(normalizePath)} className="fm-field-chip fm-field-chip-source is-multi" placeholder="选择多个源字段" onChange={onChange} options={options} filterOption={(input, option) => String(option?.searchText ?? option?.value ?? '').includes(input.toLowerCase())} />;
}

function pathsFromMapping(mapping: MappingRule) {
  const paths = new Set<string>();
  if (mapping.from) paths.add(normalizePath(mapping.from));
  mapping.fromList?.forEach((path) => path && paths.add(normalizePath(path)));
  extractExpressionFields(mapping.expression).forEach((path) => paths.add(path));
  extractExpressionFields(mapping.lookup?.sql).forEach((path) => paths.add(path));
  extractExpressionFields(mapping.when).forEach((path) => paths.add(path));
  return Array.from(paths);
}

function extractExpressionFields(expression?: string) {
  const paths: string[] = [];
  if (!expression) return paths;
  const re = /\$\{field:([^}]+)}/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(expression))) paths.push(normalizePath(match[1]));
  return paths;
}

function inferSourceFields(spec: FieldMappingSpec) {
  const paths = new Set<string>();
  spec.mappings.forEach((mapping) => pathsFromMapping(mapping).forEach((path) => paths.add(path)));
  return Array.from(paths);
}

function inferTargetFields(spec: FieldMappingSpec) {
  return spec.mappings.map((mapping) => mapping.to).filter(Boolean).map((path) => normalizePath(path as string));
}

function collectMappedSources(mappings: MappingRule[]) {
  const out: Record<string, string[]> = {};
  mappings.forEach((mapping) => {
    pathsFromMapping(mapping).forEach((from) => {
      if (!mapping.to) return;
      out[from] = [...(out[from] ?? []), displayPath(mapping.to)];
    });
  });
  return out;
}

function collectMappedTargets(mappings: MappingRule[]) {
  const out: Record<string, string[]> = {};
  mappings.forEach((mapping) => {
    if (!mapping.to) return;
    const to = normalizePath(mapping.to);
    const sources = pathsFromMapping(mapping).map(displayPath);
    out[to] = [...(out[to] ?? []), ...(sources.length ? sources : ['常量'])];
  });
  return out;
}

function edgeState(mapping: MappingRule, index: number, activeMappingIndexes: Set<number>): EdgePath['state'] {
  if (mapping.enabled === false) return 'disabled';
  return activeMappingIndexes.has(index) ? 'active' : 'idle';
}

function collectRelatedMappingIndexes(spec: FieldMappingSpec, path: string | null, mappingIndex: number | null) {
  const indexes = new Set<number>();
  if (mappingIndex != null) indexes.add(mappingIndex);
  if (!path) return indexes;
  const normalized = normalizePath(path);
  spec.mappings.forEach((mapping, index) => {
    const sources = pathsFromMapping(mapping).map(normalizePath);
    const target = mapping.to ? normalizePath(mapping.to) : '';
    const touchesSource = sources.some((source) => source === normalized || fieldKey(source) === fieldKey(normalized));
    const touchesTarget = target && (target === normalized || fieldKey(target) === fieldKey(normalized));
    if (touchesSource || touchesTarget) indexes.add(index);
  });
  return indexes;
}

function collectRelatedFieldKeys(spec: FieldMappingSpec, path: string | null, mappingIndex: number | null) {
  const keys = new Set<string>();
  const normalized = path ? normalizePath(path) : '';
  if (normalized) keys.add(fieldKey(normalized));
  spec.mappings.forEach((mapping, index) => {
    const sources = pathsFromMapping(mapping).map(normalizePath);
    const target = mapping.to ? normalizePath(mapping.to) : '';
    const touchesSource = normalized && sources.some((source) => source === normalized || fieldKey(source) === fieldKey(normalized));
    const touchesTarget = normalized && target && (target === normalized || fieldKey(target) === fieldKey(normalized));
    if (touchesSource || touchesTarget || index === mappingIndex) {
      sources.forEach((source) => keys.add(fieldKey(source)));
      if (target) keys.add(fieldKey(target));
    }
  });
  return keys;
}

function bezier(x1: number, y1: number, x2: number, y2: number) {
  const dx = x2 - x1;
  const direction = dx >= 0 ? 1 : -1;
  const offset = Math.min(Math.max(Math.abs(dx) * 0.38, 24), Math.max(Math.abs(dx) / 2, 1));
  return `M ${x1},${y1} C ${x1 + direction * offset},${y1} ${x2 - direction * offset},${y2} ${x2},${y2}`;
}

