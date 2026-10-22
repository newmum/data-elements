import type { DataType, Field, Value } from './model';
import { isBlank } from './model';
const PAIRS: [
    string,
    string
][] = [
    ['记录编号', 'record_id'], ['领用单号', 'issue_no'], ['明细行号', 'line_no'], ['装备编号', 'equipment_id'], ['装备名称', 'equipment_name'], ['装备状态', 'status_code'], ['领用时间', 'issue_time'], ['归还时间', 'return_time'], ['更新时间', 'updated_at'], ['创建时间', 'created_at'], ['业务发生时间', 'event_time'], ['备注', 'remark'], ['数量', 'quantity'], ['单位名称', 'unit_name'], ['所属单位', 'unit_name'], ['领用人', 'recipient'], ['排班编号', 'shift_id'], ['值班日期', 'duty_date'], ['值班人员', 'duty_officer'], ['班次', 'shift_name'], ['联系电话', 'phone'], ['设备ID', 'device_id'], ['IP地址', 'ip_address'], ['变更编号', 'change_id'], ['变更时间', 'change_time'], ['库存数量', 'stock_quantity'], ['代码', 'dictionary_code'], ['名称', 'dictionary_name']
];
export function normalizeEn(s: string): string { return s.trim().replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/[\s-]+/g, '_').toLowerCase(); }
export function suggestNames(headers: string[]): Pick<Field, 'en' | 'zh' | 'confirmed' | 'ambiguous'> {
    let en = '', zh = '', ambiguous = false;
    for (const raw of headers) {
        const s = raw.trim();
        const bilingual = s.match(/^(.+?)[（(]\s*([a-zA-Z][a-zA-Z0-9_ -]*)\s*[）)]$/);
        if (bilingual && /[\u3400-\u9fff]/.test(bilingual[1])) {
            zh = bilingual[1].trim();
            en = normalizeEn(bilingual[2]);
        }
        else if (/[\u3400-\u9fff]/.test(s)) {
            zh = s;
        }
        else if (/^[a-zA-Z][a-zA-Z0-9 _-]*$/.test(s)) {
            en = normalizeEn(s);
        }
    }
    const byZh = PAIRS.find(p => p[0] === zh), byEn = PAIRS.find(p => p[1] === en);
    if (en && zh && byZh && byZh[1] !== en)
        ambiguous = true;
    if (!en && byZh)
        en = byZh[1];
    if (!zh && byEn)
        zh = byEn[0];
    return { en, zh, confirmed: false, ambiguous };
}
export function fillNames(fields: Field[]): Field[] { return fields.map(f => { const n = suggestNames([f.zh, f.en, ...f.headers]); return { ...f, en: f.en || n.en, zh: f.zh || n.zh }; }); }
export function inferType(samples: Value[], header = ''): DataType {
    const v = samples.filter(x => !isBlank(x)).slice(0, 1000);
    if (!v.length)
        return 'text';
    if (/编号|单号|代码|电话|证件|(^|_)(id|no|code|phone)($|_)/i.test(header))
        return 'text';
    if (v.every(x => typeof x === 'boolean'))
        return 'boolean';
    if (v.every(x => /^\d{4}-\d{2}-\d{2}$/.test(String(x))))
        return 'date';
    if (v.every(x => /^\d{4}[-/]\d{2}[-/]\d{2}[T ]\d{2}:\d{2}/.test(String(x))))
        return 'datetime';
    if (v.every(x => typeof x === 'number' && Number.isSafeInteger(x)))
        return 'integer';
    if (v.every(x => typeof x === 'number' && Number.isFinite(x)))
        return 'decimal';
    return 'text';
}
export function acceptReliableNames(fields: Field[]): Field[] {
    return fields.map(f => ({ ...f, confirmed: f.confirmed || Boolean(f.en && f.zh && !f.ambiguous && fields.filter(x => x.en === f.en).length === 1 && fields.filter(x => x.zh === f.zh).length === 1) }));
}
