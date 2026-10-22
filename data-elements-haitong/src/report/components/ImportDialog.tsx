import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Checkbox, Input, InputNumber, Modal, Select, Table } from 'antd';
import type { SourceBook, Region, Resource, Dictionary } from '../core/model';
import { colName, display, LIMITS } from '../core/model';
import { defaultRegion, regionErrors, resourceFromRegion, dictionaryFromRegion } from '../core/import';
import { dictionaryErrors } from '../core/engine';
import { readFile } from '../services/parser';
import { reportSheetName } from '../services/reportTemplate';
import Icon from './Icon';
import LegacyDialog from './LegacyDialog';
interface Queue {
    file: File;
    book?: SourceBook;
    error?: string;
    regions: Region[];
    encoding: string;
    delimiter: string;
}

/** A file picker may dispatch the same selection twice on older Chromium. */
function fileIdentity(file: File): string {
    return `${file.name}\u0000${file.size}\u0000${file.lastModified}`;
}

export default function ImportDialog({ open, onClose, onImport, existingCells, existingNames, registeredTable }: {
    open: boolean;
    onClose: () => void;
    onImport: (resources: Resource[], dictionaries: Dictionary[], sourceFile?: File) => void;
    existingCells: number;
    existingNames: string[];
    registeredTable?: { displayName: string; name: string; fieldCount: number };
}) {
    const [queue, setQueue] = useState<Queue[]>([]), [active, setActive] = useState(0), [regionIndex, setRegionIndex] = useState(0), [busy, setBusy] = useState(false), [step, setStep] = useState(0), [error, setError] = useState('');
    const controller = useRef<AbortController | null>(null), live = useRef(true), pendingFiles = useRef<File[]>([]), debounce = useRef<ReturnType<typeof setTimeout> | null>(null);
    useEffect(() => { live.current = open; if (open) {
        setQueue([]);
        setActive(0);
        setRegionIndex(0);
        setStep(0);
        setError('');
    } return () => { live.current = false; controller.current?.abort(); if (debounce.current)
        clearTimeout(debounce.current); pendingFiles.current = []; }; }, [open]);
    async function loadFiles(files: File[]) { if (busy)
        return;
        // A nested native file input and its drop target can both surface a
        // selection in older embedded browsers.  Treat that as one selection,
        // while continuing to reject two genuinely different report files.
        const uniqueFiles = files.filter((file, index) => files.findIndex(candidate => fileIdentity(candidate) === fileIdentity(file)) === index);
        if (!uniqueFiles.length)
            return;
        setError(''); const total = registeredTable ? uniqueFiles.length : queue.length + uniqueFiles.length; if (total > 1) {
        setError('当前上报已绑定一个登记数据表，一次只能选择一份对应的数据文件。');
        return;
    } setBusy(true); const added: Queue[] = []; for (const file of uniqueFiles) {
        const ctrl = new AbortController();
        controller.current = ctrl;
        try {
            const book = await readFile(file, 'utf-8', '', ctrl.signal);
            const normalise = (value: string) => value.replace(/\s/g, '').trim().toLowerCase();
            const expectedSheetNames = registeredTable ? [registeredTable.displayName, registeredTable.name, reportSheetName(registeredTable)].map(normalise) : [];
            const matchingSheet = registeredTable ? book.sheets.findIndex(s => s.rowCount && !s.hidden && expectedSheetNames.includes(normalise(s.name))) : -1;
            if (registeredTable && matchingSheet < 0)
                throw new Error(`未找到“${registeredTable.displayName}”对应的工作表。请使用当前页面下载的模板，且不要修改工作表名称。`);
            const index = matchingSheet >= 0 ? matchingSheet : book.sheets.findIndex(s => s.rowCount && !s.hidden && !normalise(s.name).includes('填写说明'));
            const region = index >= 0 ? defaultRegion(book.sheets[index], index) : undefined;
            // A report template names its data sheet after the registered table.
            // Its first row is the sole Chinese/English header row, so it can be read
            // directly without asking the user to configure a range.
            if (region && registeredTable && book.sheets[index].rowCount >= 2) {
                region.name = registeredTable.displayName;
                region.kind = 'business';
                region.header = 'single';
                region.startRow = 1;
                region.endRow = book.sheets[index].rowCount;
                region.startCol = 1;
                region.endCol = book.sheets[index].colCount;
                region.dataStart = 2;
            }
            added.push({ file, book, regions: region ? [region] : [], encoding: 'utf-8', delimiter: '' });
        }
        catch (e) {
            if (!live.current)
                break;
            added.push({ file, regions: [], encoding: 'utf-8', delimiter: '', error: (e as Error).message });
        }
        if (!live.current)
            break;
    } if (live.current) {
        const nextQueue = registeredTable ? added : [...queue, ...added];
        setQueue(nextQueue);
        if (registeredTable && added.length === 1 && added[0].book && added[0].regions.length === 1 && !added[0].error) {
            try {
                const resource = resourceFromRegion(added[0].book, added[0].regions[0]);
                onImport([resource], [], added[0].file);
                setStep(2);
                setBusy(false);
                onClose();
                return;
            }
            catch (e) {
                setError((e as Error).message || '读取上传文件失败');
            }
        }
        setStep(1);
        setBusy(false);
    } }
    function enqueue(file: File) { if (!pendingFiles.current.some(candidate => fileIdentity(candidate) === fileIdentity(file)))
        pendingFiles.current.push(file); if (debounce.current)
        clearTimeout(debounce.current); debounce.current = setTimeout(() => { const f = pendingFiles.current; pendingFiles.current = []; void loadFiles(f); }, 30); return false; }
    const q = queue[active], region = q?.regions[regionIndex], sheet = q?.book?.sheets[region?.sheet ?? 0];
    function patchQueue(p: Partial<Queue>) { setQueue(all => all.map((x, i) => i === active ? { ...x, ...p } : x)); setError(''); }
    function patchRegion(p: Partial<Region>) { if (q)
        patchQueue({ regions: q.regions.map((r, i) => i === regionIndex ? { ...r, ...p } : r) }); }
    async function reparse(encoding: string, delimiter: string) { if (!q)
        return; setBusy(true); const ctrl = new AbortController(); controller.current = ctrl; try {
        const book = await readFile(q.file, encoding, delimiter, ctrl.signal);
        if (live.current) {
            patchQueue({ book, encoding, delimiter, error: undefined, regions: [defaultRegion(book.sheets[0], 0)] });
            setRegionIndex(0);
        }
    }
    catch (e) {
        if (live.current)
            patchQueue({ error: (e as Error).message, encoding, delimiter });
    }
    finally {
        if (live.current)
            setBusy(false);
    } }
    function finish() { setError(''); const resources: Resource[] = [], dictionaries: Dictionary[] = []; try {
        for (const item of queue) {
            if (!item.book || item.error)
                continue;
            const errors = regionErrors(item.regions, item.book);
            if (errors.length)
                throw new Error(`${item.file.name}：${errors.join('；')}`);
            for (const reg of item.regions) {
                if (reg.kind === 'business')
                    resources.push(resourceFromRegion(item.book, reg));
                else {
                    const d = dictionaryFromRegion(item.book, reg);
                    const es = dictionaryErrors(d);
                    if (es.length)
                        throw new Error(`${d.name}：${es.join('；')}`);
                    dictionaries.push(d);
                }
            }
        }
        if (!resources.length && !dictionaries.length)
            throw new Error('请至少选择一个有效业务区域或辅助字典');
        if (existingCells + resources.reduce((n, r) => n + r.rows.length * r.schema.fields.length, 0) > LIMITS.cells)
            throw new Error('本次会话数据超过100万个单元格，请移除不需要的资源后重试');
        onImport(resources, dictionaries);
        onClose();
    }
    catch (e) {
        setError((e as Error).message);
    } }
    const preview = sheet && region ? sheet.cells.slice(region.startRow - 1, Math.min(region.endRow, region.startRow + 9)).map((cells, i) => ({ key: region.startRow + i, row: region.startRow + i, cells })) : [];
    const reportRows = region ? Math.max(0, region.endRow - region.dataStart + 1) : 0;
    const closeImport = () => { controller.current?.abort(); setBusy(false); onClose(); };
    if (registeredTable) return <LegacyDialog open={open} onClose={closeImport} title={<span className="dialog-title"><Icon name="upload"/>上传数据文件</span>} ariaLabel="上传数据文件" footer={<><span className="muted">仅在本机读取，不上传服务器</span><Button onClick={closeImport}>取消</Button></>}>
      <div className="import-steps report-import-steps">{['选择当前表数据文件', '自动读取并导入数据'].map((label, index) => <div className={busy && index === 1 ? 'active' : q && !q.error && !error ? 'done' : index === 0 ? 'active' : ''} key={label}><b>{q && !q.error && !error ? <Icon name="check" size={13}/> : index + 1}</b>{label}{index === 0 && <span className="step-line"/>}</div>)}</div>
      <NativeFilePicker disabled={busy || Boolean(q && !q.error && !error)} onPick={enqueue}><div className="upload-drop"><span className="upload-symbol"><Icon name="upload" size={27}/></span><div><strong>{busy ? '正在读取当前表数据…' : `上传“${registeredTable.displayName}”对应的数据文件`}</strong><p>系统将按已登记的 {registeredTable.fieldCount} 个字段自动读取，无需设置工作表、表头或读取范围</p></div></div></NativeFilePicker>
      {error && <Alert type="error" showIcon message={error} description="请重新下载当前表模板，确认表头未被修改、增删或调整顺序后再上传。" style={{ marginTop: 16 }}/>} 
      {q?.error && <Alert type="error" showIcon message="文件读取失败" description={q.error} style={{ marginTop: 16 }}/>} 
      {q && !q.error && !error && <Alert type="success" showIcon message="文件已读取" description={`已按“${registeredTable.displayName}”的登记字段读取 ${reportRows} 条数据，正在生成数据资源。`} style={{ marginTop: 16 }}/>} 
    </LegacyDialog>;
    return <Modal title={<span className="dialog-title"><Icon name="upload"/>上传数据文件</span>} open={open} onCancel={() => { controller.current?.abort(); setBusy(false); onClose(); }} width={1000} footer={<div className="dialog-footer"><span className="muted">只在本机读取，不上传服务器</span><div><Button onClick={() => { controller.current?.abort(); setBusy(false); onClose(); }}>取消</Button> <Button type="primary" disabled={!queue.some(x => x.book && !x.error) || busy} onClick={finish}>确认读取，生成资源 <Icon name="arrow" size={15}/></Button></div></div>} destroyOnClose>
  <div className="import-steps">{['选择文件', '确认工作表与读取范围', '生成资源草稿'].map((s, i) => <div className={i === step ? 'active' : i < step ? 'done' : ''} key={s}><b>{i < step ? <Icon name="check" size={13}/> : i + 1}</b>{s}{i < 2 && <span className="step-line"/>}</div>)}</div>
  <NativeFilePicker disabled={busy} onPick={enqueue}><div className="upload-drop"><span className="upload-symbol"><Icon name="upload" size={27}/></span><div><strong>{busy ? '正在本地读取文件…' : '上传 Excel 或 CSV 文件'}</strong><p>一次一份文件、一张业务数据表 · 单个文件不超过 10 MiB</p></div></div></NativeFilePicker>
  {window.__OFFLINE_PREVIEW__ && <p className="inline-note">独立离线体验版支持 XLSX / CSV；旧版 XLS 请在源文件中另存为 XLSX。</p>}
  {error && <Alert type="error" showIcon message={error} style={{ marginTop: 12 }}/>}
  {q && <div className="import-workspace"><div className="import-files">{queue.map((item, i) => <button key={i} className={`import-file ${active === i ? 'selected' : ''}`} disabled={busy} onClick={() => { setActive(i); setRegionIndex(0); }}><Icon name="file"/><div><strong>{item.file.name}</strong><small>{item.error ? '读取失败' : `${item.book?.sheets.length || 0} 张工作表 · ${item.regions.length} 个区域`}</small>{existingNames.includes(item.file.name) && <small className="warn-text">同名文件将另建资源，不覆盖</small>}</div></button>)}</div><div className="import-settings">
   {q.file.name.toLowerCase().endsWith('.csv') && <div className="inline-fields"><label>文件编码<Select aria-label="文件编码" value={q.encoding} options={[{ value: 'utf-8', label: 'UTF-8' }, { value: 'gb18030', label: 'GB18030 / GBK' }]} onChange={v => void reparse(v, q.delimiter)}/></label><label>分隔符<Select aria-label="分隔符" value={q.delimiter} options={[{ value: '', label: '自动识别' }, { value: ',', label: '逗号' }, { value: ';', label: '分号' }, { value: '\t', label: '制表符' }]} onChange={v => void reparse(q.encoding, v)}/></label></div>}
   {q.error ? <Alert type="error" message={q.error} description="CSV 可先尝试切换编码或分隔符。其他文件仍可导入；必要时整理源文件后重选。"/> : <>
     <div className="section-mini"><strong>工作表与数据区域</strong><Select aria-label="选择工作表" placeholder="选择需要读取的工作表" value={undefined} style={{ width: 240 }} options={q.book?.sheets.map((s, i) => ({ value: i, label: `${s.name}${s.hidden ? '（隐藏）' : ''} · ${s.rowCount} 行` }))} onChange={i => { if (i === undefined || !q.book) return; if (q.regions.length) { patchQueue({ regions: [defaultRegion(q.book.sheets[i], i)] }); setRegionIndex(0); return; } const reg = defaultRegion(q.book.sheets[i], i); if (q.regions.some(r => r.sheet === i))
                reg.name += ' · 区域' + (q.regions.filter(r => r.sheet === i).length + 1); patchQueue({ regions: [...q.regions, reg] }); setRegionIndex(q.regions.length); }}/></div>
    <div className="region-chips">{q.regions.map((r, i) => <button key={r.id} className={regionIndex === i ? 'active' : ''} onClick={() => setRegionIndex(i)}>{r.name}<span role="button" aria-label={`移除区域${r.name}`} tabIndex={0} onClick={e => { e.stopPropagation(); patchQueue({ regions: q.regions.filter((_, n) => n !== i) }); setRegionIndex(0); }}><Icon name="close" size={12}/></span></button>)}</div>
    {region && sheet ? <><div className="inline-fields"><label>资源名称<Input aria-label="导入资源名称" value={region.name} onChange={e => patchRegion({ name: e.target.value })}/></label><label>用途<Select aria-label="工作表用途" value={region.kind} options={[{ value: 'business', label: '业务数据表' }]} onChange={v => patchRegion({ kind: v })}/></label><label>表头模式<Select aria-label="表头模式" value={region.header} options={[{ value: 'single', label: '单行表头' }, { value: 'bilingual', label: '上下两行双语表头' }, { value: 'none', label: '无表头' }]} onChange={v => patchRegion({ header: v, dataStart: region.startRow + (v === 'none' ? 0 : v === 'bilingual' ? 2 : 1) })}/></label></div>
    <div className="range-controls"><span>读取范围</span>{([['startRow', '起始行', sheet.rowCount], ['endRow', '结束行', sheet.rowCount], ['startCol', '起始列', sheet.colCount], ['endCol', '结束列', sheet.colCount], ['dataStart', '数据起始行', sheet.rowCount + 1]] as const).map(([key, label, max]) => <label key={key}>{label}<InputNumber aria-label={label} min={1} max={max} value={region[key]} onChange={v => patchRegion({ [key]: v || 1 })}/></label>)}</div>
    <div className="grid-caption"><span>{sheet.name}!{colName(region.startCol - 1)}{region.startRow}:{colName(region.endCol - 1)}{region.endRow}</span><small>预览前10行。点击两个网格单元格可选择范围起点与终点。</small></div>
    <RangePreview rows={preview} startCol={region.startCol} endCol={region.endCol} onRange={(r1, c1, r2, c2) => patchRegion({ startRow: r1, endRow: r2, startCol: c1, endCol: c2, dataStart: r1 + (region.header === 'none' ? 0 : region.header === 'bilingual' ? 2 : 1) })}/>
    {region.kind === 'dictionary' && <div className="inline-fields"><label>代码列<Select aria-label="字典代码列" value={region.codeCol} onChange={v => patchRegion({ codeCol: v })} options={Array.from({ length: region.endCol - region.startCol + 1 }, (_, i) => ({ value: region.startCol + i, label: `${colName(region.startCol + i - 1)} · ${display(sheet.cells[region.startRow - 1]?.[region.startCol + i - 1]?.v)}` }))}/></label><label>名称列<Select aria-label="字典名称列" value={region.labelCol} onChange={v => patchRegion({ labelCol: v })} options={Array.from({ length: region.endCol - region.startCol + 1 }, (_, i) => ({ value: region.startCol + i, label: `${colName(region.startCol + i - 1)} · ${display(sheet.cells[region.startRow - 1]?.[region.startCol + i - 1]?.v)}` }))}/></label></div>}
    {(sheet.hidden || sheet.hiddenRows.length > 0 || sheet.hiddenCols.length > 0) && <Checkbox checked={region.hiddenConfirmed} onChange={e => patchRegion({ hiddenConfirmed: e.target.checked })}>已核对读取范围中的隐藏工作表、行或列</Checkbox>}
    <p className="inline-note">当前仅读取一个业务数据区域，并会提交到上一步选定的登记数据表；未选择的工作表不会导入。</p></> : <div className="empty-small">请添加需要导入的工作表或区域。</div>}
   </>}
  </div></div>}
 </Modal>;
}

/**
 * A native label/input pair keeps file selection independent of component
 * library click forwarding and works with older Chromium-based browsers.
 */
function NativeFilePicker({ disabled, onPick, children }: {
    disabled: boolean;
    onPick: (file: File) => void;
    children: ReactNode;
}) {
    const inputRef = useRef<HTMLInputElement | null>(null);
    return <label
      className={`native-file-picker${disabled ? ' is-disabled' : ''}`}
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled}
      aria-label="选择 Excel 或 CSV 数据文件"
      onKeyDown={event => {
          if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              if (!disabled)
                  inputRef.current?.click();
          }
      }}
      onDragOver={event => {
          if (!disabled)
              event.preventDefault();
      }}
      onDrop={event => {
          event.preventDefault();
          if (!disabled) {
              const file = event.dataTransfer.files?.[0];
              if (file)
                  onPick(file);
          }
      }}
    >
      <input
        ref={inputRef}
        className="native-file-picker-input"
        type="file"
        accept=".xlsx,.xls,.csv"
        disabled={disabled}
        tabIndex={-1}
        aria-label="选择 Excel 或 CSV 数据文件"
        onChange={event => {
          const file = event.currentTarget.files?.[0];
          // Reset first so selecting the same corrected file again still
          // dispatches change in older Chromium versions.
          event.currentTarget.value = '';
          if (file)
              onPick(file);
        }}
      />
      {children}
    </label>;
}

function RangePreview({ rows, startCol, endCol, onRange }: {
    rows: {
        key: number;
        row: number;
        cells: {
            v: import('../core/model').Value;
        }[];
    }[];
    startCol: number;
    endCol: number;
    onRange: (r1: number, c1: number, r2: number, c2: number) => void;
}) {
    const [anchor, setAnchor] = useState<{
        r: number;
        c: number;
    } | null>(null);
    return <div className="source-grid"><table><thead><tr><th>行</th>{Array.from({ length: endCol - startCol + 1 }, (_, i) => <th key={i}>{colName(startCol + i - 1)}</th>)}</tr></thead><tbody>{rows.map(r => <tr key={r.key}><th>{r.row}</th>{Array.from({ length: endCol - startCol + 1 }, (_, i) => { const c = startCol + i; return <td key={c}><button className={anchor?.r === r.row && anchor.c === c ? 'range-start' : ''} onClick={() => { if (!anchor)
        setAnchor({ r: r.row, c });
    else {
        onRange(Math.min(anchor.r, r.row), Math.min(anchor.c, c), Math.max(anchor.r, r.row), Math.max(anchor.c, c));
        setAnchor(null);
    } }} title={display(r.cells[c - 1]?.v)}>{display(r.cells[c - 1]?.v)}</button></td>; })}</tr>)}</tbody></table>{anchor && <small className="grid-hint">已选择起点 {colName(anchor.c - 1)}{anchor.r}，请选择终点；完整大范围可用上方数字设置。</small>}</div>;
}
