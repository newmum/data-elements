import { DomainError } from './types.ts';
/** RFC-style quoted CSV; tabs / Excel formulas are escaped on export. */
export function parseCsv(text: string): string[][] {
    const rows: string[][] = [];
    let row: string[] = [], cell = '', quoted = false;
    const source = text.replace(/^\uFEFF/, '');
    for (let i = 0; i < source.length; i++) {
        const c = source[i];
        if (c === '"') {
            if (quoted && source[i + 1] === '"') {
                cell += '"';
                i++;
            }
            else if (!quoted && cell.length > 0)
                throw new DomainError('CSV引号格式不正确');
            else
                quoted = !quoted;
        }
        else if (c === ',' && !quoted) {
            row.push(cell);
            cell = '';
        }
        else if ((c === '\n' || c === '\r') && !quoted) {
            if (c === '\r' && source[i + 1] === '\n')
                i++;
            row.push(cell);
            if (row.some(x => x.trim()))
                rows.push(row);
            row = [];
            cell = '';
        }
        else
            cell += c;
    }
    if (quoted)
        throw new DomainError('CSV包含未闭合引号');
    row.push(cell);
    if (row.some(x => x.trim()))
        rows.push(row);
    return rows;
}
export function encodeCsv(rows: unknown[][]): string {
    return '\uFEFF' + rows.map(row => row.map(value => {
        let s = String(value ?? '');
        if (/^[\s]*[=+\-@\t\r]/.test(s))
            s = "'" + s;
        return '"' + s.replaceAll('"', '""') + '"';
    }).join(',')).join('\r\n');
}
export const maskPhone = (v: string) => v.length >= 7 ? `${v.slice(0, 3)}****${v.slice(-4)}` : v || '—';
export const maskEmail = (v: string) => v.includes('@') ? `${v.slice(0, 2)}***@${v.split('@')[1]}` : v || '—';
