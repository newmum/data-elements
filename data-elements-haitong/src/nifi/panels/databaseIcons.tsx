import type { ReactNode } from 'react';
import { ApiOutlined, FolderOpenOutlined, LockOutlined } from '@ant-design/icons';
import { normalizeSourceManifestKey } from './registeredDatasourceType';
import clickhouse from '../icons/clickhouse.png';
import dameng from '../icons/dameng.svg';
import db2 from '../icons/db2.svg';
import doris from '../icons/doris.svg';
import elasticsearch from '../icons/elasticsearch.png';
import gaussdb from '../icons/gaussdb.svg';
import gbase from '../icons/gbase8a.svg';
import hailiang from '../icons/hailiang.png';
import hbase from '../icons/hbase.svg';
import hdfs from '../icons/hdfs.png';
import hetu from '../icons/hetu.svg';
import highgo from '../icons/highgo.png';
import hive from '../icons/hive.svg';
import iotdb from '../icons/iotdb.png';
import kafka from '../icons/kafka.svg';
import kingbase from '../icons/kingbase8.svg';
import mariadb from '../icons/mariadb.png';
import minio from '../icons/minio.svg';
import mysql from '../icons/mysql.svg';
import oceanbase from '../icons/oceanbasemysql.svg';
import oracle from '../icons/oracle.svg';
import oscar from '../icons/oscar.png';
import postgresql from '../icons/postgresql.svg';
import sqlserver from '../icons/sqlserver.png';
import starrocks from '../icons/starrocks.svg';
import tdsql from '../icons/tdsql.png';

interface ProcessIconSpec {
  bg: string;
  fg?: string;
  kind: 'filter' | 'mapping' | 'sql' | 'sink' | 'transform' | 'branch';
  label: string;
}

// Local assets keep vendor colors and work without an Internet connection.
// Provenance and redistribution notices live in icons/README.md.
function DatabaseBrandIcon({ src, name, cropMark = false }: { src: string; name: string; cropMark?: boolean }) {
  return (
    <span className="database-brand-icon" title={name}>
      <img src={src} alt={`${name} 图标`} draggable={false}
        className={cropMark ? 'database-brand-icon__image database-brand-icon__image--mark' : 'database-brand-icon__image'} />
    </span>
  );
}

function ProcessSvgIcon({ bg, fg = '#fff', kind, label }: ProcessIconSpec) {
  const icon = (() => {
    switch (kind) {
      case 'filter':
        return <path d="M10 11h20l-7.2 8.4v7.2l-5.6 2.8v-10L10 11Z" fill="none" stroke={fg} strokeWidth="2.4" strokeLinejoin="round" />;
      case 'mapping':
        return <path d="M10 14h7m6 0h7M10 26h7m6 0h7m-6-12-6 12m0-12 6 12" fill="none" stroke={fg} strokeWidth="2.3" strokeLinecap="round" />;
      case 'sql':
        return <path d="M10 13h20M10 20h14M10 27h20" fill="none" stroke={fg} strokeWidth="2.4" strokeLinecap="round" />;
      case 'sink':
        return <path d="M12 15c0-3 16-3 16 0v10c0 3-16 3-16 0V15Zm0 0c0 3 16 3 16 0m-16 5c0 3 16 3 16 0" fill="none" stroke={fg} strokeWidth="2.1" />;
      case 'branch':
        return <path d="M12 20h7m0 0 5-6m-5 6 5 6m0-12h4m-4 12h4" fill="none" stroke={fg} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />;
      default:
        return <path d="M12 13h16v6H12v-6Zm0 10h16v6H12v-6Z" fill="none" stroke={fg} strokeWidth="2.2" strokeLinejoin="round" />;
    }
  })();
  return (
    <svg viewBox="0 0 40 40" width="34" height="34" aria-hidden="true" focusable="false">
      <rect x="2" y="2" width="36" height="36" rx="8" fill={bg} />
      <circle cx="31" cy="9" r="4" fill="rgba(255,255,255,0.28)" />
      {icon}
      <text x="20" y="35" textAnchor="middle" fontSize="5.5" fontWeight="700" fill={fg} fontFamily="Arial, sans-serif">
        {label}
      </text>
    </svg>
  );
}

export const DATA_SOURCE_ICONS: Record<string, ReactNode> = {
  'source.api': <span className="datasource-protocol-icon" title="HTTP API"><ApiOutlined /></span>,
  'source.ftp': <span className="datasource-protocol-icon" title="FTP"><FolderOpenOutlined /></span>,
  'source.sftp': <span className="datasource-protocol-icon" title="SFTP"><LockOutlined /></span>,
  'source.clickhouse': <DatabaseBrandIcon src={clickhouse} name="ClickHouse" />,
  'source.dameng': <DatabaseBrandIcon src={dameng} name="达梦" />,
  'source.db2': <DatabaseBrandIcon src={db2} name="IBM Db2" />,
  'source.doris': <DatabaseBrandIcon src={doris} name="Apache Doris" />,
  'source.elasticsearch': <DatabaseBrandIcon src={elasticsearch} name="Elasticsearch" />,
  'source.gaussdb': <DatabaseBrandIcon src={gaussdb} name="GaussDB" />,
  'source.gbase8a': <DatabaseBrandIcon src={gbase} name="GBase 8a" />,
  'source.gbase8s': <DatabaseBrandIcon src={gbase} name="GBase 8s" />,
  'source.hailiang': <DatabaseBrandIcon src={hailiang} name="海量 Vastbase" />,
  'source.hbase': <DatabaseBrandIcon src={hbase} name="Apache HBase" />,
  'source.hdfs': <DatabaseBrandIcon src={hdfs} name="Apache Hadoop HDFS" />,
  'source.hetu': <DatabaseBrandIcon src={hetu} name="Huawei HetuEngine" />,
  'source.highgo': <DatabaseBrandIcon src={highgo} name="瀚高" />,
  'source.hive': <DatabaseBrandIcon src={hive} name="Apache Hive" />,
  'source.iotdb': <DatabaseBrandIcon src={iotdb} name="Apache IoTDB" />,
  'source.kafka': <DatabaseBrandIcon src={kafka} name="Apache Kafka" />,
  'source.kingbase': <DatabaseBrandIcon src={kingbase} name="人大金仓" />,
  'source.mariadb': <DatabaseBrandIcon src={mariadb} name="MariaDB" />,
  'source.minio': <DatabaseBrandIcon src={minio} name="MinIO" />,
  'source.mysql': <DatabaseBrandIcon src={mysql} name="MySQL" />,
  'source.oceanbase': <DatabaseBrandIcon src={oceanbase} name="OceanBase" />,
  'source.oracle': <DatabaseBrandIcon src={oracle} name="Oracle" />,
  'source.oscar': <DatabaseBrandIcon src={oscar} name="神通" cropMark />,
  'source.postgresql': <DatabaseBrandIcon src={postgresql} name="PostgreSQL" />,
  'source.sqlserver': <DatabaseBrandIcon src={sqlserver} name="Microsoft SQL Server" />,
  'source.starrocks': <DatabaseBrandIcon src={starrocks} name="StarRocks" />,
  'source.tdsql-mysql': <DatabaseBrandIcon src={tdsql} name="Tencent TDSQL MySQL" />,
  'source.tdsql-pg': <DatabaseBrandIcon src={tdsql} name="Tencent TDSQL PostgreSQL" />,
};

export const COMPONENT_ICONS: Record<string, ReactNode> = {
  ...DATA_SOURCE_ICONS,
  'transform.filter': <ProcessSvgIcon bg="#14b8a6" kind="filter" label="FILTER" />,
  'transform.field-mapping': <ProcessSvgIcon bg="#6366f1" kind="mapping" label="MAP" />,
  'transform.field-enrichment': <ProcessSvgIcon bg="#6366f1" kind="mapping" label="MAP" />,
  'transform.sql': <ProcessSvgIcon bg="#7c3aed" kind="sql" label="SQL" />,
  'sink.hive': <DatabaseBrandIcon src={hive} name="Apache Hive" />,
  'sink.jdbc': <ProcessSvgIcon bg="#16a34a" kind="sink" label="JDBC" />,
  transform: <ProcessSvgIcon bg="#6366f1" kind="transform" label="TRANS" />,
  sink: <ProcessSvgIcon bg="#16a34a" kind="sink" label="SINK" />,
  branch: <ProcessSvgIcon bg="#f59e0b" fg="#111827" kind="branch" label="BRANCH" />,
};

export function getComponentIcon(key: string, dbType?: unknown): ReactNode {
  if (key === 'sink.jdbc' && dbType) {
    const sourceKey = normalizeSourceManifestKey(String(dbType));
    if (sourceKey && DATA_SOURCE_ICONS[sourceKey]) return DATA_SOURCE_ICONS[sourceKey];
  }
  return COMPONENT_ICONS[key];
}
