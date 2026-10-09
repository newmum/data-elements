import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile, readdir } from 'node:fs/promises';
import { normalizeSourceManifestKey, sinkDbType, buildJdbcUrl } from './registeredDatasourceType.ts';

test('all shipped source manifests can be selected without substituting MySQL', async () => {
  const directory = new URL('../../../../data-elements-parent/src/main/resources/manifests/sources/', import.meta.url);
  const files = (await readdir(directory)).filter((file) => file.endsWith('.json'));
  assert.equal(files.length, 31);
  for (const file of files) {
    const manifest = JSON.parse(await readFile(new URL(file, directory), 'utf8'));
    assert.equal(normalizeSourceManifestKey(manifest.key.slice(7)), manifest.key, file);
  }
});

test('registered aliases preserve vendor and compatibility mode', () => {
  for (const [raw, expected] of Object.entries({ kingbase8: 'kingbase', openGauss: 'gaussdb',
    tdsql_mysql: 'tdsql-mysql', tdsql_pg: 'tdsql-pg', mrs_hive: 'hive', oceanbaseoracle: 'oceanbase',
    vastbase: 'hailiang', mrs_hdfs: 'hdfs', es: 'elasticsearch' })) {
    assert.equal(normalizeSourceManifestKey(raw), `source.${expected}`);
  }
  assert.equal(sinkDbType('kingbase8'), 'KINGBASE');
  assert.equal(sinkDbType('gaussdb'), 'GAUSSDB');
  assert.equal(sinkDbType('oceanbase', 'ORACLE'), 'OCEANBASE_ORACLE');
});

test('connector URLs use vendor protocols and default ports', () => {
  for (const [raw, expected] of Object.entries({ kingbase8: 'jdbc:kingbase8://db:54321/business',
    dm: 'jdbc:dm://db:5236/business', mariadb: 'jdbc:mariadb://db:3306/business',
    db2: 'jdbc:db2://db:50000/business', gbase8a: 'jdbc:gbase://db:5258/business',
    gbase8s: 'jdbc:gbasedbt-sqli://db:9088/business', oscar: 'jdbc:oscar://db:2003/business',
    highgo: 'jdbc:highgo://db:5866/business', hive: 'jdbc:hive2://db:10000/business',
    hetu: 'jdbc:trino://db:29861/business', oceanbaseoracle: 'jdbc:oceanbase:oracle://db:2881/business',
    gaussdb: 'jdbc:postgresql://db:5432/business' })) {
    assert.equal(buildJdbcUrl(raw, 'db', undefined, 'business'), expected, raw);
  }
  assert.equal(buildJdbcUrl('oracle', 'db', undefined, 'service', 'SID'), 'jdbc:oracle:thin:@db:1521:service');
});

test('unknown and non-JDBC types never synthesize MySQL URLs', () => {
  for (const raw of ['unknown-vendor', 'ftp', 'api', 'minio', 'kafka', 'hdfs', 'hbase', 'es']) {
    assert.equal(buildJdbcUrl(raw, 'db', undefined, 'business'), undefined);
  }
  for (const raw of ['unknown-vendor', 'mongodb', 'vertica', 'maxcompute', '']) {
    assert.equal(normalizeSourceManifestKey(raw), undefined);
  }
});
