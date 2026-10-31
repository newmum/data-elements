import type { Value, Field, Schema, Resource, Dictionary, Issue, Validation, DataRow } from './model';
import { isBlank } from './model';
import { tableNameErrors } from './tableNames';
export function fingerprint(r: Resource, dicts: Dictionary[]): string {
    const used = r.schema.fields.flatMap(f => f.dictionary ? [f.dictionary.id] : []);
    return JSON.stringify([r.id, r.dataVersion, r.schemaVersion, r.schema, dicts.filter(d => used.includes(d.id)).map(d => [d.id, d.version, d.entries])]);
}
function issue(scope: Issue['scope'], code: string, message: string, hint: string, fieldId?: string, row?: DataRow): Issue { return { scope, code, message, hint, fieldId, rowId: row?.id, sourceRow: row?.sourceRow }; }
export function dictionaryErrors(d: Dictionary): string[] { const codes = new Set<string>(); const e: string[] = []; for (const x of d.entries) {
    if (!x.code.trim() || !x.label.trim())
        e.push('代码及名称不能留空');
    if (codes.has(x.code))
        e.push('字典代码存在重复');
    codes.add(x.code);
} if (!d.entries.length)
    e.push('字典没有有效条目'); return [...new Set(e)]; }
export function validateSchema(s: Schema, dicts: Dictionary[]): Issue[] {
    const out: Issue[] = [];
    const add = (code: string, msg: string, hint: string, id?: string) => out.push(issue('schema', code, msg, hint, id));
    for (const problem of tableNameErrors(s))
        add(problem.code, problem.message, '点击表名称区域的“设置表名”完善中英文名');
    const ens = new Set<string>(), zhs = new Set<string>();
    for (const f of s.fields) {
        if (!/^[a-z][a-z0-9_]{0,62}$/.test(f.en) || ['select', 'from', 'table', 'where', 'order', 'group', 'user'].includes(f.en))
            add('EN_NAME', `${f.zh || f.headers.join(' / ') || '此字段'}的英文名未填写或不符合规则`, '使用字母开头的小写字母、数字及下划线，最多63字符', f.id);
        if (f.en && ens.has(f.en))
            add('EN_DUPLICATE', `英文名 ${f.en} 重复`, '为不同来源列填写唯一英文名', f.id);
        ens.add(f.en);
        if (!f.zh.trim() || !/[\u3400-\u9fff]/.test(f.zh) || f.zh.length > 64)
            add('ZH_NAME', `${f.en || '此字段'}缺少有效中文名`, '填写能解释业务含义的中文名称', f.id);
        if (f.zh && zhs.has(f.zh))
            add('ZH_DUPLICATE', `中文名“${f.zh}”重复`, '结合来源数据区分字段含义', f.id);
        zhs.add(f.zh);
        if (!f.confirmed)
            add('NAME_CONFIRM', `${f.zh || f.en || '此字段'}的名称尚未确认`, '核对建议或手动补全，然后确认此字段', f.id);
        if (f.dictionary) {
            const link = f.dictionary, d = dicts.find(x => x.id === link.id && x.version === link.version);
            if (!d || dictionaryErrors(d).length)
                add('DICT_INVALID', `${f.zh}关联字典无效`, '选择代码唯一、名称完整的字典版本', f.id);
            if (link.output === 'code' && !link.confirmed)
                add('DICT_CONFIRM', `${f.zh}的代码转换尚未确认`, '核对原值与拟同步值，确认转换策略', f.id);
        }
    }
    if (!s.fields.some(f => f.pk))
        add('PK_REQUIRED', '尚未选择主键', '勾选一个或多个能共同唯一识别记录的字段');
    for (const [id, label] of [[s.timestamp, '时间戳'], [s.eventTime, '业务发生时间']]) {
        const f = s.fields.find(f => f.id === id);
        if (!f)
            add('TIME_ROLE', `尚未指定${label}字段`, '在关键规则中选择真实来源时间');
        else {
            if (!f.time.format)
                add('TIME_FORMAT', `${label}字段解析格式未确认`, '在字段详情或关键规则中确认时间格式', f.id);
            const valsNeedZone = !['date'].includes(f.type) && !['unix-s', 'unix-ms'].includes(f.time.format);
            if (valsNeedZone && !f.time.timezone)
                add('TIME_ZONE', `${label}字段来源时区未确认`, '选择源数据的时区或选择原值带有时区', f.id);
        }
    }
    if (s.timestamp && s.timestamp === s.eventTime && !s.sameTimeConfirmed)
        add('TIME_SHARED', '同一字段兼任两种时间角色尚未确认', '只有源时间确实兼具两种含义时才能确认');
    if (!s.reviewed)
        add('STRUCTURE_CONFIRM', '字段含义与类型尚未核对', '勾选“已核对字段含义、数据类型与关键规则”');
    return out;
}
function validDate(y: number, m: number, d: number): boolean { return y >= 1 && y <= 9999 && m >= 1 && m <= 12 && d >= 1 && d <= new Date(Date.UTC(y, m, 0)).getUTCDate(); }
export function parseTime(v: Value, f: Field): Value {
    const s = String(v);
    const fmt = f.time.format;
    if (fmt === 'unix-s' || fmt === 'unix-ms') {
        if (!/^-?\d+$/.test(s))
            throw new Error('不是有效的整数时间值');
        const n = Number(s);
        if (!Number.isSafeInteger(n))
            throw new Error('时间数值超出安全精度');
        const date = new Date(n * (fmt === 'unix-s' ? 1000 : 1));
        if (!Number.isFinite(date.getTime()))
            throw new Error('时间数值超出范围');
        return date.toISOString();
    }
    if (!fmt)
        throw new Error('请先确认日期格式');
    let year: number, month: number, day: number, tail = '';
    if (fmt === 'dmy' || fmt === 'mdy') {
        const m = s.match(/^(\d{2})\/(\d{2})\/(\d{4})(.*)$/);
        if (!m)
            throw new Error('不符合所选日期格式');
        year = +m[3];
        month = +(fmt === 'dmy' ? m[2] : m[1]);
        day = +(fmt === 'dmy' ? m[1] : m[2]);
        tail = m[4];
    }
    else {
        const m = s.match(/^(\d{4})[-/](\d{2})[-/](\d{2})(.*)$/);
        if (!m)
            throw new Error('日期格式不明确，请指定格式');
        year = +m[1];
        month = +m[2];
        day = +m[3];
        tail = m[4];
    }
    if (!validDate(year, month, day))
        throw new Error('日期不存在或超出范围');
    const base = `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    if (!tail)
        return base;
    const t = tail.match(/^[ T](\d{2}):(\d{2})(?::(\d{2})(\.\d{1,3})?)?(Z|[+-]\d{2}:?\d{2})?$/);
    if (!t || +t[1] > 23 || +t[2] > 59 || (t[3] && +t[3] > 59))
        throw new Error('时间格式或时分秒不合法');
    let offset = t[5] || '';
    if (offset && offset !== 'Z') {
        const om = offset.match(/^([+-])(\d{2}):?(\d{2})$/)!;
        if (+om[2] > 14 || +om[3] > 59 || (+om[2] === 14 && +om[3] !== 0))
            throw new Error('时区偏移无效');
        offset = `${om[1]}${om[2]}:${om[3]}`;
    }
    if (!offset) {
        if (!f.time.timezone || f.time.timezone === 'source')
            throw new Error('源值未携带时区，请指定来源时区');
        const offsets: Record<string, string> = { 'Asia/Shanghai': '+08:00', 'Asia/Tokyo': '+09:00', UTC: 'Z' };
        offset = offsets[f.time.timezone];
        if (!offset)
            throw new Error('当前原型不支持所选时区');
    }
    // Preserve provided precision; do not invent missing seconds or a time for date-only values.
    return `${base}T${t[1]}:${t[2]}${t[3] ? ':' + t[3] : ''}${t[4] || ''}${offset}`;
}
export function matchDictionary(v: Value, f: Field, dicts: Dictionary[]): {
    code: string;
    label: string;
} | undefined {
    if (!f.dictionary || isBlank(v))
        return undefined;
    const l = f.dictionary, d = dicts.find(d => d.id === l.id && d.version === l.version);
    if (!d)
        throw new Error('字典版本不存在');
    const raw = String(v), key = l.match === 'manual' ? l.mappings[raw] : raw;
    const matches = d.entries.filter(x => l.match === 'label' ? x.label === key : x.code === key);
    if (matches.length !== 1)
        throw new Error(matches.length ? '字典名称对应多个代码，请逐项映射' : '字典中没有精确匹配项');
    return matches[0];
}
export function prepareValue(v: Value, f: Field, s: Schema, dicts: Dictionary[]): Value {
    if (isBlank(v))
        return null;
    const dict = matchDictionary(v, f, dicts);
    let value: Value = f.dictionary?.output === 'code' && dict ? dict.code : v;
    if (f.id === s.timestamp || f.id === s.eventTime || f.type === 'date' || f.type === 'datetime') {
        const prepared = parseTime(value, f);
        if (f.standardField === 'DATE') return String(prepared).slice(0, 10);
        if (f.standardField === 'DATETIME' && String(prepared).length <= 10) throw new Error('时间 DATETIME 缺少时分秒，请补齐时间或选择日期 DATE');
        return prepared;
    }
    if (f.type === 'text')
        return String(value);
    if (f.type === 'boolean') {
        if (value === true || value === 'true')
            return true;
        if (value === false || value === 'false')
            return false;
        throw new Error('不是明确的 true / false 布尔值');
    }
    const str = String(value);
    if (!/^-?(?:0|[1-9]\d*)(?:\.\d+)?$/.test(str))
        throw new Error('数值格式不合法或会丢失前导零');
    const n = Number(str);
    if (!Number.isFinite(n) || Math.abs(n) > Number.MAX_SAFE_INTEGER)
        throw new Error('数值超出安全精度，请使用文本');
    if (f.type === 'integer' && !Number.isSafeInteger(n))
        throw new Error('不是安全整数');
    if (str.replace(/[-.]/g, '').replace(/^0+/, '').length > 15)
        throw new Error('数值精度过长，请保留为文本');
    return n;
}
export function validateResource(r: Resource, dicts: Dictionary[]): Validation {
    const global = validateSchema(r.schema, dicts), issues: Issue[] = [], byRow: Record<string, Issue[]> = {}, prepared: Validation['prepared'] = {}, groups = new Map<string, DataRow[]>(), pk = r.schema.fields.filter(f => f.pk);
    let emptyKey = 0, duplicateGroups = 0;
    for (const row of r.rows) {
        const p: Record<string, Value> = {};
        const errors: Issue[] = [];
        let keyValid = true;
        for (const f of r.schema.fields) {
            const raw = row.values[f.id] ?? null;
            const required = f.pk || r.schema.timestamp === f.id || r.schema.eventTime === f.id || f.nullable === 'forbid';
            if (required && isBlank(raw)) {
                const e = issue('row', 'REQUIRED', `${f.zh || f.en}不能为空`, '在源文件中补齐真实值后重新导入', f.id, row);
                errors.push(e);
                if (f.pk) {
                    emptyKey++;
                    keyValid = false;
                }
            }
            if (row.formulaFields.includes(f.id))
                errors.push(issue('row', 'FORMULA', `${f.zh || f.en}含公式`, '请在源文件核对结果并转为值后重新导入', f.id, row));
            if (row.precisionFields.includes(f.id))
                errors.push(issue('row', 'PRECISION', `${f.zh || f.en}存在长数字精度风险`, '回到源文件核对并以文本保存，本页无法恢复已丢失的数字', f.id, row));
            try {
                p[f.id] = prepareValue(raw, f, r.schema, dicts);
            }
            catch (e) {
                p[f.id] = raw;
                errors.push(issue('row', 'VALUE_INVALID', `${f.zh || f.en}：${(e as Error).message}`, '核对解析规则、字典对应关系或源文件中的值', f.id, row));
                if (f.pk)
                    keyValid = false;
            }
        }
        if (pk.length && keyValid) {
            const tuple = JSON.stringify(pk.map(f => [typeof p[f.id], p[f.id]]));
            groups.set(tuple, [...(groups.get(tuple) || []), row]);
        }
        byRow[row.id] = errors;
        prepared[row.id] = p;
    }
    if (emptyKey)
        global.push(issue('resource', 'PK_EMPTY', `主键存在 ${emptyKey} 个空值`, '主键必须在当前资源全部记录中非空；请定位问题记录'));
    for (const group of groups.values())
        if (group.length > 1) {
            duplicateGroups++;
            for (const row of group)
                byRow[row.id].push(issue('row', 'PK_DUPLICATE', `主键组合重复，共 ${group.length} 条`, '补充能唯一标识业务记录的联合键，或核对源文件', pk[0]?.id, row));
        }
    if (duplicateGroups)
        global.push(issue('resource', 'PK_DUPLICATE', `发现 ${duplicateGroups} 组重复主键`, '检查整个资源的主键，不可仅选择重复组中的一行绕过'));
    if (pk.length && r.rows.some(row => byRow[row.id].some(e => e.code === 'VALUE_INVALID' && pk.some(f => f.id === e.fieldId))))
        global.push(issue('resource', 'PK_CONVERSION', '主键值无法按当前规则转换', '先修正主键的类型或字典转换规则'));
    for (const es of Object.values(byRow))
        issues.push(...es);
    return { fingerprint: fingerprint(r, dicts), time: new Date().toISOString(), global, issues, byRow, prepared, eligible: global.length ? [] : r.rows.filter(x => !byRow[x.id]?.length).map(x => x.id), duplicateGroups };
}
export function isValidationCurrent(r: Resource, dicts: Dictionary[]): boolean { return !r.dirty && !!r.validation && r.validation.fingerprint === fingerprint(r, dicts); }
export function selectableRows(r: Resource, dicts: Dictionary[]): Set<string> { return new Set(isValidationCurrent(r, dicts) && !r.validation?.global.length ? r.validation!.eligible.filter(id => !r.sync[id] || r.sync[id] === 'idle') : []); }
export function resourceStatus(r: Resource): {
    text: string;
    tone: string;
} { if (r.dirty)
    return { text: '草稿未保存', tone: 'warning' }; if (!r.validation)
    return { text: '待完善', tone: 'warning' }; if (r.validation.global.length)
    return { text: '有阻断', tone: 'error' }; if (!r.validation.eligible.length)
    return { text: '暂无可同步记录', tone: 'warning' }; if (r.locked)
    return { text: '结构已锁定', tone: 'blue' }; if (r.validation.issues.length)
    return { text: '部分记录异常', tone: 'warning' }; return { text: '可同步', tone: 'success' }; }
