/** Old task DSLs keep sourceDbId/targetDbId; manual selection uses the first two keys. */
export function registeredDatasourceId(config: Record<string, unknown>): string {
  for (const key of ['selectedDatabaseId', 'registeredDatasourceId', 'targetDbId', 'sourceDbId', 'datasourceId', 'dataSourceId', 'dbId']) {
    const value = String(config[key] ?? '').trim();
    if (value) return value;
  }
  return '';
}

type RegisteredDatabase = {
  value?: string; id?: string; jdbcUrl?: string; jdbcURL?: string;
  host?: string; port?: string; database?: string; dbName?: string; username?: string; schema?: string;
};

/** Never infer an identity when two registered connections match. */
export function resolveDatasourceId(config: Record<string, unknown>, databases: RegisteredDatabase[]): string {
  const existing = registeredDatasourceId(config);
  if (existing) return existing;
  const value = (v: unknown) => String(v ?? '').trim();
  const url = value(config.jdbcUrl || config.jdbcURL);
  const user = value(config.username || config.user);
  if (!user) return '';
  const matches = databases.filter((db) => {
    if (value(db.username) !== user) return false;
    if (config.schema && value(db.schema) !== value(config.schema)) return false;
    if (url) return value(db.jdbcUrl || db.jdbcURL) === url;
    return Boolean(config.host && config.database && config.port)
      && value(db.host).toLowerCase() === value(config.host).toLowerCase()
      && value(db.port) === value(config.port)
      && value(db.database || db.dbName) === value(config.database);
  });
  return matches.length === 1 ? value(matches[0].value || matches[0].id) : '';
}
