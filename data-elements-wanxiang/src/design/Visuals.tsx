import type { ReactNode } from 'react';
import { ApartmentOutlined, ArrowRightOutlined } from '@ant-design/icons';
import { engineClass, engineLabel, finitePercent, formatCount } from '../shared/presentation';
import { ModuleIcon } from './ModuleIcon';
import type { Row } from '../services/api';
/** Shared platform icons are copied from data-elements-front/src/assets/icons; existing artwork is documented in sources.json. */
const databaseArtwork: Record<string,string> = {
 dm8:new URL('../assets/databases/dm8.png',import.meta.url).href,
 mysql:new URL('../assets/databases/mysql.svg',import.meta.url).href,
 postgresql:new URL('../assets/databases/postgresql.png',import.meta.url).href,
 oracle:new URL('../assets/databases/oracle.png',import.meta.url).href,
 sqlserver:new URL('../assets/databases/sqlserver.png',import.meta.url).href,
 mongodb:new URL('../assets/databases/mongodb.svg',import.meta.url).href,
 elasticsearch:new URL('../assets/databases/elasticsearch.svg',import.meta.url).href,
 mariadb:new URL('../assets/databases/mariadb.svg',import.meta.url).href,
 api:new URL('../assets/databases/api.svg',import.meta.url).href,
 ftp:new URL('../assets/databases/ftp.svg',import.meta.url).href,
 gaussdb:new URL('../assets/databases/gaussdb.svg',import.meta.url).href,
 gbase8a:new URL('../assets/databases/gbase8a.svg',import.meta.url).href,
 hive:new URL('../assets/databases/hive.svg',import.meta.url).href,
 kafka:new URL('../assets/databases/kafka.svg',import.meta.url).href,
 kingbase8:new URL('../assets/databases/kingbase8.svg',import.meta.url).href,
 maxcompute:new URL('../assets/databases/maxcompute.svg',import.meta.url).href,
 minio:new URL('../assets/databases/minio.svg',import.meta.url).href,
 oceanbasemysql:new URL('../assets/databases/oceanbasemysql.svg',import.meta.url).href,
 oceanbaseoracle:new URL('../assets/databases/oceanbaseoracle.svg',import.meta.url).href,
 vertica:new URL('../assets/databases/vertica.svg',import.meta.url).href,
 other:new URL('../assets/databases/db.svg',import.meta.url).href,
 };
export function SourceIcon({engine,small=false}:{engine:unknown;small?:boolean}) {
 const key=engineClass(engine),src=databaseArtwork[key];
 return <span className={`wx-source-icon source-${key}${small?' is-small':''}`} title={engineLabel(engine)}>{src?<img src={src} alt="" aria-hidden="true"/>:<ModuleIcon name="sources"/>}</span>;
}
export function Metric({ label, value, note, icon, tone = 'purple', onClick }: {
    label: string;
    value: unknown;
    note: string;
    icon: ReactNode;
    tone?: string;
    onClick?: () => void;
}) {
    const content = <><span className={`metric-icon tone-${tone}`}>{icon}</span><span className="metric-copy"><span className="metric-label">{label}</span><strong>{formatCount(value)}</strong><small>{note}</small></span>{onClick && <ArrowRightOutlined className="metric-arrow"/>}</>;
    return onClick ? <button className="wx-metric" onClick={onClick}>{content}</button> : <article className="wx-metric">{content}</article>;
}
export function Panel({ title, description, extra, children, className = '' }: {
    title: string;
    description?: string;
    extra?: ReactNode;
    children: ReactNode;
    className?: string;
}) {
    return <section className={`wx-insight-panel ${className}`}><header><div><h2>{title}</h2>{description && <p>{description}</p>}</div>{extra}</header><div className="wx-panel-body">{children}</div></section>;
}
export function CoverageRing({ value, mapped, total }: {
    value: unknown;
    mapped: unknown;
    total: unknown;
}) {
    const percent = finitePercent(value);
    return <div className="coverage-content"><div className="coverage-ring"><svg viewBox="0 0 120 120" aria-label={percent === null ? '暂无覆盖率数据' : `覆盖率 ${percent.toFixed(1)}%`}><circle className="ring-track" cx="60" cy="60" r="49"/><circle className="ring-value" cx="60" cy="60" r="49" pathLength="100" strokeDasharray={`${percent ?? 0} 100`} transform="rotate(-90 60 60)"/></svg><div><strong>{percent === null ? '—' : percent.toFixed(1)}{percent !== null && <small>%</small>}</strong><span>标准覆盖率</span></div></div><div className="coverage-facts"><p><span><i />已映射字段</span><b>{formatCount(mapped)}</b></p><p><span><i />可见字段</span><b>{formatCount(total)}</b></p><small>{percent === null ? '暂无可统计字段' : '按已发布标准统计'}</small></div></div>;
}
/** Thumbnail shows saved positions only; it does not invent relations. */
export function ModelThumbnail({ model }: {
    model: Row;
}) {
    const nodes: Row[] = (model.layout?.nodes ?? model.layout_json?.nodes ?? model.nodes ?? []).slice(0, 18);
    if (!nodes.length)
        return <div className="wx-model-thumbnail empty"><ApartmentOutlined /><span>空白模型 · 添加数据表开始设计</span></div>;
    const points = nodes.map((n, i) => ({ id: n.entityId ?? n.id ?? i, x: Number(n.position?.x ?? n.x ?? i * 320), y: Number(n.position?.y ?? n.y ?? 0) }));
    const minX = Math.min(...points.map(n => n.x)), minY = Math.min(...points.map(n => n.y)), w = Math.max(...points.map(n => n.x)) - minX + 284, h = Math.max(...points.map(n => n.y)) - minY + 204;
    return <div className="wx-model-thumbnail"><svg viewBox={`${minX - 30} ${minY - 30} ${w + 60} ${h + 60}`} aria-hidden="true">{points.map(n => <g key={n.id} transform={`translate(${n.x} ${n.y})`}><rect width="284" height="204" rx="14" className="thumb-node"/><rect width="284" height="48" rx="12" className="thumb-head"/>{[78, 113, 148, 180].map(y => <path key={y} d={`M20 ${y}h132 M190 ${y}h70`} className="thumb-line"/>)}</g>)}</svg><span className="thumbnail-badge">ER</span></div>;
}
