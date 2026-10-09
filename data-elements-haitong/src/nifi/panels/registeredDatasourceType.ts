const aliases: Record<string, string> = {
  kingbase8: 'kingbase', postgres: 'postgresql', opengauss: 'gaussdb', gauss: 'gaussdb', dm: 'dameng',
  mssql: 'sqlserver', mssql2008: 'sqlserver', 'mssql2012+': 'sqlserver', oracle12: 'oracle', 'oracle12+': 'oracle',
  vastbase: 'hailiang', es: 'elasticsearch', ch: 'clickhouse', trino: 'hetu', presto: 'hetu', hetuengine: 'hetu',
  apacheiotdb: 'iotdb', mrsdoris: 'doris', mrsstarrocks: 'starrocks', mrsclickhouse: 'clickhouse',
  mrshive: 'hive', mrshdfs: 'hdfs', mrshbase: 'hbase',
  oceanbasecompatiblemysql: 'oceanbasemysql', oceanbasecompatibleoracle: 'oceanbaseoracle',
};
const sourceKeys = new Set([
  'mysql', 'oracle', 'oceanbase', 'gaussdb', 'gbase8a', 'sqlserver', 'hive', 'hetu', 'doris', 'starrocks',
  'clickhouse', 'iotdb', 'hdfs', 'hbase', 'dameng', 'postgresql', 'db2', 'mariadb', 'gbase8s', 'oscar',
  'highgo', 'kingbase', 'minio', 'ftp', 'sftp', 'api', 'kafka', 'elasticsearch', 'tdsql-mysql', 'tdsql-pg', 'hailiang',
]);

function typeKey(raw?: string) {
  const value = String(raw ?? '').trim().toLowerCase().replace(/[\s_-]+/g, '');
  return aliases[value] ?? value;
}

export function normalizeSourceManifestKey(raw?: string, protocol?: string) {
  let value = typeKey(raw);
  if (['oceanbasemysql', 'oceanbaseoracle'].includes(value)) value = 'oceanbase';
  if (value === 'tdsqlmysql') value = 'tdsql-mysql';
  if (value === 'tdsqlpg') value = 'tdsql-pg';
  if (value === 'ftp' && String(protocol ?? '').trim().toLowerCase() === 'sftp') value = 'sftp';
  return sourceKeys.has(value) ? `source.${value}` : undefined;
}

export function sinkDbType(raw?: string, compatibleMode?: string) {
  const value = typeKey(raw);
  switch (value) {
    case 'mysql': case 'tdsqlmysql': return 'MySQL';
    case 'postgresql': case 'tdsqlpg': case 'hailiang': return 'PostgreSQL';
    case 'oracle': return 'Oracle';
    case 'dameng': return 'DM';
    case 'oceanbase': return compatibleMode?.toUpperCase() === 'ORACLE' ? 'OCEANBASE_ORACLE' : 'OCEANBASE_MYSQL';
    case 'oceanbasemysql': return 'OCEANBASE_MYSQL';
    case 'oceanbaseoracle': return 'OCEANBASE_ORACLE';
    default: return sourceKeys.has(value) ? value.toUpperCase() : undefined;
  }
}

const jdbcDefaults: Record<string, [string, number]> = {
  MySQL: ['mysql', 3306], Oracle: ['oracle:thin', 1521], PostgreSQL: ['postgresql', 5432],
  GAUSSDB: ['postgresql', 5432], SQLSERVER: ['sqlserver', 1433], DM: ['dm', 5236],
  KINGBASE: ['kingbase8', 54321], CLICKHOUSE: ['clickhouse', 8123], HIVE: ['hive2', 10000],
  OCEANBASE_ORACLE: ['oceanbase:oracle', 2881], OCEANBASE_MYSQL: ['mysql', 2881],
  MARIADB: ['mariadb', 3306], DB2: ['db2', 50000], GBASE8A: ['gbase', 5258],
  GBASE8S: ['gbasedbt-sqli', 9088], OSCAR: ['oscar', 2003], HIGHGO: ['highgo', 5866],
  HETU: ['trino', 29861], DORIS: ['mysql', 9030], STARROCKS: ['mysql', 9030], IOTDB: ['iotdb', 22260],
};

export function buildJdbcUrl(raw?: string, host?: string, port?: string, database?: string, connectionType?: string) {
  const type = sinkDbType(raw);
  const endpoint = type && jdbcDefaults[type];
  if (!host || !database || !endpoint) return undefined;
  const [protocol, defaultPort] = endpoint;
  const p = port || defaultPort;
  if (type === 'Oracle') return connectionType?.toUpperCase() === 'SID'
    ? `jdbc:oracle:thin:@${host}:${p}:${database}` : `jdbc:oracle:thin:@//${host}:${p}/${database}`;
  if (type === 'SQLSERVER') return `jdbc:sqlserver://${host}:${p};databaseName=${database};encrypt=true;trustServerCertificate=false`;
  if (type === 'IOTDB') return `jdbc:iotdb://${host}:${p}/`;
  const url = `jdbc:${protocol}://${host}:${p}/${database}`;
  return protocol === 'mysql' ? `${url}?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` : url;
}
