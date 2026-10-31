import type { SourceBook, SourceSheet, Region, Resource, Field, Schema, Dictionary, SourceCell, Value } from './model';
import { LIMITS, uid, clone, colName, isBlank } from './model';
import { suggestNames, inferType } from './names';
export async function parseSource(file: File, encoding = 'utf-8', delimiter = ''): Promise<SourceBook> {
    const extension = file.name.split('.').pop()?.toLowerCase();
    if (!['xlsx', 'xls', 'csv'].includes(extension || ''))
        throw new Error('仅支持 .xlsx、.xls、.csv 文件');
    if (file.size > LIMITS.bytes)
        throw new Error('文件超过 10 MiB，请拆分后导入');
    const data = await file.arrayBuffer();
    let sheets: SourceSheet[] = [];
    if (extension === 'csv') {
        const Papa = (await import('papaparse')).default;
        const text = new TextDecoder(encoding, { fatal: true }).decode(data).replace(/^\uFEFF/, '');
        const result = Papa.parse<string[]>(text, { header: false, dynamicTyping: false, delimiter, skipEmptyLines: false });
        const fatal = result.errors.filter(e => e.code !== 'UndetectableDelimiter');
        if (fatal.length)
            throw new Error(`CSV 解析失败：${fatal[0].message}（逻辑记录 ${(fatal[0].row ?? 0) + 1}）`);
        const cells = result.data.map(row => row.map(v => ({ v })));
        const colCount = Math.max(0, ...cells.map(r => r.length));
        sheets = [{ name: file.name.replace(/\.csv$/i, ''), cells, rowCount: cells.length, colCount, hidden: false, hiddenRows: [], hiddenCols: [], merges: [], notes: result.errors.length ? ['分隔符无法可靠识别，请核对预览并手动选择。'] : [] }];
    }
    else if (typeof window !== 'undefined' && window.__OFFLINE_PREVIEW__ && window.__nativeReadWorkbook) {
        if (extension === 'xls')
            throw new Error('独立离线体验版暂不解析旧版 .xls；请另存为 .xlsx。React 工程使用 SheetJS 支持 .xls。');
        sheets = await window.__nativeReadWorkbook(data);
    }
    else {
        // The spreadsheet parser is only needed after a user selects an Excel
        // file.  Keep it out of the first-screen bundle so opening a data
        // source and checking its authority does not wait on a large parser.
        const XLSX = await import('xlsx');
        const book = XLSX.read(data, { type: 'array', cellFormula: true, cellDates: false, cellNF: true, cellStyles: true, cellText: true });
        sheets = book.SheetNames.map((name, si) => {
            const ws = book.Sheets[name], ref = ws['!ref'];
            if (!ref)
                return { name, cells: [], rowCount: 0, colCount: 0, hidden: false, hiddenRows: [], hiddenCols: [], merges: [], notes: [] };
            const range = XLSX.utils.decode_range(ref);
            if ((range.e.r + 1) * (range.e.c + 1) > LIMITS.cells * 2)
                throw new Error(`${name} 的读取区域过大，请整理空白格式区域或拆分文件`);
            const cells: SourceCell[][] = [];
            for (let r = 0; r <= range.e.r; r++) {
                const row: SourceCell[] = [];
                for (let c = 0; c <= range.e.c; c++) {
                    const cell = ws[XLSX.utils.encode_cell({ r, c })];
                    let v: Value = cell?.v ?? null;
                    if (cell?.t === 'n' && cell.z && XLSX.SSF.is_date(cell.z)) {
                        const d = XLSX.SSF.parse_date_code(Number(v), { date1904: book.Workbook?.WBProps?.date1904 });
                        if (d) {
                            const date = `${String(d.y).padStart(4, '0')}-${String(d.m).padStart(2, '0')}-${String(d.d).padStart(2, '0')}`;
                            v = /[hs]/i.test(cell.z) ? `${date} ${String(d.H).padStart(2, '0')}:${String(d.M).padStart(2, '0')}:${String(d.S).padStart(2, '0')}` : date;
                        }
                    }
                    else if (cell?.t === 'n' && cell.w && /^0{2,}$/.test(cell.z || ''))
                        v = cell.w;
                    row.push({ v, formula: !!cell?.f, precision: cell?.t === 'n' && Math.abs(Number(cell.v)) >= 1e15 });
                }
                cells.push(row);
            }
            return { name, cells, rowCount: cells.length, colCount: range.e.c + 1, hidden: !!book.Workbook?.Sheets?.[si]?.Hidden, hiddenRows: (ws['!rows'] || []).flatMap((x, i) => x?.hidden ? [i] : []), hiddenCols: (ws['!cols'] || []).flatMap((x, i) => x?.hidden ? [i] : []), merges: (ws['!merges'] || []).map(m => ({ r1: m.s.r, c1: m.s.c, r2: m.e.r, c2: m.e.c })), notes: [] };
        });
    }
    return { id: uid('file'), filename: file.name, size: file.size, sheets, importedAt: new Date().toISOString() };
}
export function defaultRegion(sheet: SourceSheet, index: number): Region { return { id: uid('region'), sheet: index, kind: 'business', startRow: 1, endRow: sheet.rowCount, startCol: 1, endCol: sheet.colCount, header: 'single', dataStart: 2, name: sheet.name, codeCol: 1, labelCol: Math.min(2, sheet.colCount), hiddenConfirmed: false }; }
export function regionErrors(regions: Region[], book: SourceBook, limits = LIMITS): string[] {
    const errors: string[] = [];
    for (const r of regions) {
        const s = book.sheets[r.sheet];
        if (!s) {
            errors.push('来源工作表不存在');
            continue;
        }
        if (r.startRow < 1 || r.endRow > s.rowCount || r.startCol < 1 || r.endCol > s.colCount || r.endRow < r.startRow || r.endCol < r.startCol)
            errors.push(`${r.name}：数据范围无效`);
        const min = r.startRow + (r.header === 'none' ? 0 : r.header === 'bilingual' ? 2 : 1);
        if (r.dataStart < min || r.dataStart > r.endRow + 1)
            errors.push(`${r.name}：数据起始行必须在表头之后`);
        if (r.endRow - r.dataStart + 1 > limits.rows || r.endCol - r.startCol + 1 > limits.cols)
            errors.push(`${r.name}：每个资源最多 ${limits.rows.toLocaleString()} 条记录、${limits.cols} 个字段`);
        if ((s.hidden || s.hiddenRows.some(i => i >= r.startRow - 1 && i < r.endRow) || s.hiddenCols.some(i => i >= r.startCol - 1 && i < r.endCol)) && !r.hiddenConfirmed)
            errors.push(`${r.name}：读取范围包含隐藏内容，请确认`);
        const heads = r.header === 'bilingual' ? 2 : r.header === 'single' ? 1 : 0;
        if (s.merges.some(m => m.r1 < r.startRow - 1 + heads && m.r2 >= r.startRow - 1 && m.c1 < r.endCol && m.c2 >= r.startCol - 1))
            errors.push(`${r.name}：表头包含合并单元格，请选择规则单行表头或整理源文件`);
    }
    for (let i = 0; i < regions.length; i++)
        for (let j = i + 1; j < regions.length; j++) {
            const a = regions[i], b = regions[j];
            if (a.sheet === b.sheet && Math.max(a.startRow, b.startRow) <= Math.min(a.endRow, b.endRow) && Math.max(a.startCol, b.startCol) <= Math.min(a.endCol, b.endCol))
                errors.push(`${a.name} 与 ${b.name} 的范围重叠`);
        }
    return [...new Set(errors)];
}
export function resourceFromRegion(book: SourceBook, region: Region, demo = false): Resource {
    const sheet = book.sheets[region.sheet], fields: Field[] = [], rows: Resource['rows'] = [], id = uid('resource');
    for (let c = region.startCol - 1; c < region.endCol; c++) {
        const headers = region.header === 'none' ? [] : [String(sheet.cells[region.startRow - 1]?.[c]?.v ?? ''), ...(region.header === 'bilingual' ? [String(sheet.cells[region.startRow]?.[c]?.v ?? '')] : [])];
        const names = suggestNames(headers);
        const samples = sheet.cells.slice(region.dataStart - 1, region.endRow).map(r => r[c]?.v ?? null);
        const type = inferType(samples, headers.join(' '));
        fields.push({ id: uid('field'), column: c, headers, ...names, type, pk: false, nullable: 'unset', time: { format: type === 'date' ? 'ymd' : type === 'datetime' ? 'iso' : '', timezone: '' } });
    }
    let skipped = 0;
    for (let r = region.dataStart - 1; r < region.endRow; r++) {
        const source = sheet.cells[r] || [];
        if (fields.every(f => isBlank(source[f.column]?.v))) {
            skipped++;
            continue;
        }
        rows.push({ id: `${id}:r${r + 1}`, sourceRow: r + 1, values: Object.fromEntries(fields.map(f => [f.id, source[f.column]?.v ?? null])), formulaFields: fields.filter(f => source[f.column]?.formula).map(f => f.id), precisionFields: fields.filter(f => source[f.column]?.precision).map(f => f.id) });
    }
    const schema: Schema = { name: region.name, englishName: /^[a-z][a-z0-9_]{0,62}$/.test(region.name) ? region.name : '', description: '', fields, timestamp: '', eventTime: '', sameTimeConfirmed: false, reviewed: false };
    return { id, source: { fileId: book.id, filename: book.filename, sheet: sheet.name, range: `${colName(region.startCol - 1)}${region.startRow}:${colName(region.endCol - 1)}${region.endRow}`, demo, importedAt: book.importedAt }, dataVersion: 1, schemaVersion: 0, schema, savedSchema: clone(schema), dirty: false, locked: false, rows, sync: {}, notes: [...sheet.notes, ...(skipped ? [`已跳过 ${skipped} 个全空记录。`] : [])] };
}
export function dictionaryFromRegion(book: SourceBook, region: Region): Dictionary { if (region.codeCol === region.labelCol)
    throw new Error('字典代码列和名称列不能相同'); if ([region.codeCol, region.labelCol].some(c => c < region.startCol || c > region.endCol))
    throw new Error('代码列和名称列必须在读取范围内'); const sheet = book.sheets[region.sheet]; return { id: uid('dictionary'), version: 1, name: region.name, source: 'upload', fileId: book.id, filename: book.filename, sheet: sheet.name, entries: sheet.cells.slice(region.dataStart - 1, region.endRow).filter(row => row.slice(region.startCol - 1, region.endCol).some(c => !isBlank(c.v))).map(row => ({ code: String(row[region.codeCol - 1]?.v ?? ''), label: String(row[region.labelCol - 1]?.v ?? '') })) }; }
