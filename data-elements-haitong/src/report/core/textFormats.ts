/** Text imports share the same sheet model and never coerce identifiers to numbers. */
export function detectTextDelimiter(text: string): string {
    const candidates = ['\t', ',', ';', '|'];
    const counts = candidates.map(() => 0);
    let quoted = false, meaningful = false;
    for (let i = 0; i < text.length; i++) {
        const char = text[i];
        if (char === '"') {
            if (quoted && text[i + 1] === '"') { i++; meaningful = true; }
            else quoted = !quoted;
        } else if (!quoted) {
            if (char === '\r' || char === '\n') {
                if (meaningful) break;
                counts.fill(0);
            } else {
                const index = candidates.indexOf(char);
                if (index >= 0) counts[index]++;
                else if (char.trim()) meaningful = true;
            }
        } else if (char.trim()) meaningful = true;
    }
    const highest = Math.max(...counts);
    return highest ? candidates[counts.indexOf(highest)] : ',';
}

export function validateTextMatrix(matrix: string[][]): void {
    if (matrix.length > 20001) throw new Error('单份文本文件最多 20000 条数据，请拆分后上传');
    let cells = 0;
    for (const row of matrix) {
        if (row.length > 200) throw new Error('单份文本文件最多 200 列，请拆分后上传');
        cells += row.length;
        if (cells > 500000) throw new Error('单份文本文件最多读取 500000 个单元格，请拆分后上传');
        if (row.some(value => value.length > 65535)) throw new Error('单个字段内容不能超过 65535 个字符');
    }
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function preserveNumberLiterals(source: string): string {
    // JSON.parse otherwise rounds long IDs before the normalization screen can
    // inspect them. Quote only value tokens, leaving malformed numeric keys invalid.
    const parts: string[] = [];
    const stack: string[] = [];
    let quoted = false, escaped = false, previous = '', start = 0, objects = 0, separators = 0;
    for (let i = 0; i < source.length; i++) {
        const char = source[i];
        if (quoted) {
            if (escaped) escaped = false;
            else if (char === '\\') escaped = true;
            else if (char === '"') { quoted = false; previous = '"'; }
            continue;
        }
        if (char === '"') { quoted = true; continue; }
        // Bound the object graph before JSON.parse allocates it. The allowance
        // also covers a record envelope and ordinary pagination metadata.
        if (char === '{' && ++objects > 40002) throw new Error('JSON 对象数量过多，请简化嵌套结构或拆分文件后上传');
        if (char === ',' && ++separators > 1000000) throw new Error('JSON 内容过多，请拆分文件后上传');
        if (char === '[' || char === '{') stack.push(char);
        else if (char === ']' || char === '}') stack.pop();
        const valuePosition = previous === ':' || previous === '[' || (previous === ',' && stack[stack.length - 1] === '[');
        if (valuePosition && /[-0-9]/.test(char)) {
            const token = /^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/.exec(source.slice(i))?.[0];
            if (token) {
                parts.push(source.slice(start, i), JSON.stringify(token));
                i += token.length - 1;
                start = i + 1;
                previous = '0';
                continue;
            }
        }
        if (!/\s/.test(char)) previous = char;
    }
    parts.push(source.slice(start));
    return parts.join('');
}

export function parseJsonRecords(source: string): string[][] {
    let parsed: unknown;
    const preserved = preserveNumberLiterals(source.replace(/^\uFEFF/, ''));
    try { parsed = JSON.parse(preserved); }
    catch { throw new Error('JSON 格式不正确，请检查引号、逗号和括号后重新上传'); }
    let records: unknown;
    if (Array.isArray(parsed)) records = parsed;
    else if (isRecord(parsed)) {
        const root = parsed;
        const containers = ['data', 'rows', 'list'].filter(key => Array.isArray(root[key]));
        if (containers.length > 1) throw new Error('JSON 包含多个记录数组，请只保留 data、rows 或 list 中的一个');
        records = containers.length ? parsed[containers[0]] : [parsed];
    }
    if (!Array.isArray(records) || !records.length) throw new Error('JSON 需包含至少一条对象记录，支持对象数组或 data、rows、list 记录数组');
    if (records.length > 20000) throw new Error('单份 JSON 文件最多 20000 条数据，请拆分后上传');
    const columns = new Set<string>();
    for (const [index, record] of records.entries()) {
        if (!isRecord(record)) throw new Error(`JSON 第 ${index + 1} 条记录需为对象，请使用 [{"字段名":"值"}] 格式`);
        for (const [key, value] of Object.entries(record)) {
            if (!key.trim()) throw new Error(`JSON 第 ${index + 1} 条记录包含空字段名，请补充后上传`);
            if (value !== null && typeof value === 'object') throw new Error(`JSON 第 ${index + 1} 条记录的「${key}」包含嵌套结构，请展开为普通字段后上传`);
            columns.add(key);
            if (columns.size > 200) throw new Error('单份 JSON 文件最多 200 列，请拆分后上传');
        }
    }
    const headers = [...columns];
    if (!headers.length) throw new Error('JSON 记录没有字段，请补充字段名和数据后上传');
    if ((records.length + 1) * headers.length > 500000) throw new Error('单份文本文件最多读取 500000 个单元格，请拆分后上传');
    const matrix = [headers, ...records.map(record => {
        if (!isRecord(record)) throw new Error('JSON 记录格式不正确');
        return headers.map(key => Object.prototype.hasOwnProperty.call(record, key) && record[key] !== null ? String(record[key]) : '');
    })];
    validateTextMatrix(matrix);
    return matrix;
}
