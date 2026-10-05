/** Display helpers never invent server metrics. Counts may exceed JS safe integers. */
export function formatCount(value: unknown): string {
    if ((typeof value !== 'number' && typeof value !== 'string') || value === '')
        return '—';
    const v = String(value);
    if (/^-?\d+$/.test(v))
        return v.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    const n = Number(value);
    return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '—';
}
export function finitePercent(value: unknown): number | null {
    if ((typeof value !== 'number' && typeof value !== 'string') || value === '')
        return null;
    const n = Number(value);
    return Number.isFinite(n) && n >= 0 && n <= 100 ? n : null;
}
export function jobProgress(done: unknown, total: unknown): number | null {
    if (total === null || total === undefined || total === '' || done === null || done === undefined || done === '')
        return null;
    const d = Number(done), t = Number(total);
    return Number.isFinite(t) && t > 0 && Number.isFinite(d) && d >= 0 ? Math.min(100, Math.round(d / t * 100)) : null;
}
/** A run keeps its recorded task name; the dashboard adds readable context, never a raw instance ID. */
export function recentRunPresentation(row: Record<string, unknown>): { name: string; context: string } {
    const kind = row.task_kind === 'PROFILE' || row.type === 'PROFILING' ? '字段探查' : '质量检查';
    const name = String(row.task_name ?? row.name ?? '').trim() || kind;
    const subject = [row.table_name_cn, row.datasource_name]
        .find(value => typeof value === 'string' && value.trim()) as string | undefined;
    return { name, context: subject ? `${kind} · ${subject.trim()}` : kind };
}
export const engineLabels: Record<string, string> = { MYSQL: 'MySQL', POSTGRESQL: 'PostgreSQL', DM8: '达梦 DM8', MARIADB: 'MariaDB', SQLSERVER: 'SQL Server', ORACLE: 'Oracle', MONGODB: 'MongoDB', ELASTICSEARCH: 'Elasticsearch', API: 'API', FTP: 'FTP', SFTP: 'SFTP', GAUSSDB: 'GaussDB', GBASE8A: 'GBase 8a', HIVE: 'Hive', KAFKA: 'Kafka', KINGBASE8: 'KingbaseES', MAXCOMPUTE: 'MaxCompute', MINIO: 'MinIO', OCEANBASEMYSQL: 'OceanBase MySQL', OCEANBASEORACLE: 'OceanBase Oracle', VERTICA: 'Vertica', OTHER: '其他' };
const engineAliases: Record<string,string> = { DAMENG:'DM8',DM:'DM8',POSTGRES:'POSTGRESQL',PG:'POSTGRESQL',MSSQL:'SQLSERVER',ES:'ELASTICSEARCH',OCEANBASEMYSQLMODE:'OCEANBASEMYSQL',OCEANBASEORACLEMODE:'OCEANBASEORACLE' };
function canonicalEngine(v: unknown): string { const key=String(v ?? '').toUpperCase().replace(/[-_\s]/g,'');return engineAliases[key]??key; }
export function engineLabel(v: unknown): string { return engineLabels[canonicalEngine(v)] ?? String(v ?? '未知类型'); }
export function engineClass(v: unknown): string { const key=canonicalEngine(v);return Object.hasOwn(engineLabels,key)?key==='SFTP'?'ftp':key.toLowerCase():'other'; }
export function sourceDistribution(rows: Record<string, any>[]) {
    const counts = new Map<string, number>();
    for (const row of rows) {
        const key = String(row.engine ?? 'OTHER').toUpperCase();
        counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return [...counts].map(([engine, count]) => ({ engine, label: engineLabel(engine), count, percent: rows.length ? count / rows.length * 100 : 0 })).sort((a, b) => b.count - a.count);
}
export const PORTS: Record<string, number> = { DM8: 5236, MYSQL: 3306, MARIADB: 3306, POSTGRESQL: 5432, SQLSERVER: 1433, ORACLE: 1521, MONGODB: 27017, ELASTICSEARCH: 9200 };
export function nextEnginePort(oldEngine: string, newEngine: string, current: unknown): unknown {
    return current == null || current === '' || Number(current) === PORTS[oldEngine] ? PORTS[newEngine] : current;
}
export function compactPreference(value: unknown) {
    const v = value && typeof value === 'object' ? value as Record<string, unknown> : {};
    return { size: [15, 30, 50, 100].includes(Number(v.size)) ? Number(v.size) : 15, density: v.density === 'middle' ? 'middle' as const : 'small' as const, hidden: Array.isArray(v.hidden) ? v.hidden.filter((x): x is string => typeof x === 'string').slice(0, 80) : [] };
}
