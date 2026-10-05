import type { ReactNode } from 'react';

interface DatabaseIconSpec {
  bg: string;
  fg?: string;
  text: string;
  sub?: string;
}

interface ProcessIconSpec {
  bg: string;
  fg?: string;
  kind: 'filter' | 'mapping' | 'sql' | 'sink' | 'transform' | 'branch';
  label: string;
}

function DatabaseSvgIcon({ bg, fg = '#fff', text, sub }: DatabaseIconSpec) {
  return (
    <svg viewBox="0 0 40 40" width="34" height="34" aria-hidden="true" focusable="false">
      <rect x="2" y="2" width="36" height="36" rx="8" fill={bg} />
      <ellipse cx="20" cy="12" rx="11" ry="5" fill="rgba(255,255,255,0.28)" />
      <path d="M9 12v12c0 2.8 4.9 5 11 5s11-2.2 11-5V12" fill="none" stroke="rgba(255,255,255,0.58)" strokeWidth="2" />
      <text x="20" y={sub ? 22 : 25} textAnchor="middle" fontSize={sub ? 9 : 11} fontWeight="700" fill={fg} fontFamily="Arial, sans-serif">
        {text}
      </text>
      {sub && (
        <text x="20" y="31" textAnchor="middle" fontSize="6" fontWeight="700" fill={fg} fontFamily="Arial, sans-serif">
          {sub}
        </text>
      )}
    </svg>
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
  'source.api': <DatabaseSvgIcon bg="#7c3aed" text="API" />,
  'source.clickhouse': <DatabaseSvgIcon bg="#ffcc01" fg="#111827" text="CH" />,
  'source.dameng': <DatabaseSvgIcon bg="#0b5cad" text="DM" />,
  'source.db2': <DatabaseSvgIcon bg="#1f70c1" text="Db2" />,
  'source.doris': <DatabaseSvgIcon bg="#1d4ed8" text="DOR" sub="IS" />,
  'source.elasticsearch': <DatabaseSvgIcon bg="#00bfb3" fg="#111827" text="ES" />,
  'source.gaussdb': <DatabaseSvgIcon bg="#155eef" text="G" sub="DB" />,
  'source.gbase8a': <DatabaseSvgIcon bg="#cf3f3f" text="G" sub="8A" />,
  'source.gbase8s': <DatabaseSvgIcon bg="#b83232" text="G" sub="8S" />,
  'source.hailiang': <DatabaseSvgIcon bg="#0f766e" text="HL" />,
  'source.hbase': <DatabaseSvgIcon bg="#0f766e" text="H" sub="BASE" />,
  'source.hdfs': <DatabaseSvgIcon bg="#2563eb" text="H" sub="DFS" />,
  'source.hetu': <DatabaseSvgIcon bg="#0f7490" text="HT" />,
  'source.highgo': <DatabaseSvgIcon bg="#d23f31" text="HG" />,
  'source.hive': <DatabaseSvgIcon bg="#f59e0b" fg="#3b2600" text="Hive" />,
  'source.iotdb': <DatabaseSvgIcon bg="#0284c7" text="IoT" sub="DB" />,
  'source.ftp': <DatabaseSvgIcon bg="#0891b2" text="FTP" />,
  'source.sftp': <DatabaseSvgIcon bg="#0369a1" text="SFTP" />,
  'source.kafka': <DatabaseSvgIcon bg="#231f20" text="K" />,
  'source.kingbase': <DatabaseSvgIcon bg="#c82127" text="KB" />,
  'source.mariadb': <DatabaseSvgIcon bg="#ba7257" text="Maria" sub="DB" />,
  'source.mysql': <DatabaseSvgIcon bg="#00758f" text="My" sub="SQL" />,
  'source.oceanbase': <DatabaseSvgIcon bg="#0b66ff" text="OB" />,
  'source.oracle': <DatabaseSvgIcon bg="#c74634" text="Ora" />,
  'source.oscar': <DatabaseSvgIcon bg="#2454a6" text="OS" />,
  'source.postgresql': <DatabaseSvgIcon bg="#336791" text="PG" />,
  'source.sqlserver': <DatabaseSvgIcon bg="#a91d22" text="SQL" />,
  'source.starrocks': <DatabaseSvgIcon bg="#7c3aed" text="SR" />,
  'source.tdsql-mysql': <DatabaseSvgIcon bg="#0052d9" text="TDS" sub="MY" />,
  'source.tdsql-pg': <DatabaseSvgIcon bg="#0052d9" text="TDS" sub="PG" />,
};

export const COMPONENT_ICONS: Record<string, ReactNode> = {
  ...DATA_SOURCE_ICONS,
  'transform.filter': <ProcessSvgIcon bg="#14b8a6" kind="filter" label="FILTER" />,
  'transform.field-mapping': <ProcessSvgIcon bg="#6366f1" kind="mapping" label="MAP" />,
  'transform.field-enrichment': <ProcessSvgIcon bg="#6366f1" kind="mapping" label="MAP" />,
  'transform.sql': <ProcessSvgIcon bg="#7c3aed" kind="sql" label="SQL" />,
  'sink.hive': <DatabaseSvgIcon bg="#f59e0b" fg="#3b2600" text="Hive" />,
  'sink.jdbc': <ProcessSvgIcon bg="#16a34a" kind="sink" label="JDBC" />,
  transform: <ProcessSvgIcon bg="#6366f1" kind="transform" label="TRANS" />,
  sink: <ProcessSvgIcon bg="#16a34a" kind="sink" label="SINK" />,
  branch: <ProcessSvgIcon bg="#f59e0b" fg="#111827" kind="branch" label="BRANCH" />,
};
