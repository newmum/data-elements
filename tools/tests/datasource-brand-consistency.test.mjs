import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compareCanvasManifests } from '../../data-elements-haitong/src/nifi/panels/manifestOrder.ts';
import { normalizeSourceManifestKey } from '../../data-elements-haitong/src/nifi/panels/registeredDatasourceType.ts';

const root = new URL('../../', import.meta.url);
const registration = 'lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue';

test('画布共有来源顺序与登记探查卡片一致，其他来源排在最后', () => {
  const source = readFileSync(new URL(registration, root), 'utf8');
  const labels = source.match(/const sourceTypeLabels = \{([^}]+)\};/)[1];
  const types = [...labels.matchAll(/^\s*(\w+):/gm)].map(([, key]) => key);
  // The discovery grid excludes the API and MaxCompute options.
  const shared = [...new Set(types.filter(key => !['api', 'maxcompute'].includes(key)).map(key => {
    const manifest = normalizeSourceManifestKey(key);
    assert.ok(manifest, `Missing canvas manifest for ${key}`);
    return manifest;
  }))];
  const additional = ['source.api', 'source.clickhouse', 'source.sftp'];
  const shuffled = [...shared, ...additional].reverse().map(key => ({ key, category: 'source', label: key }));
  assert.deepEqual(shuffled.sort(compareCanvasManifests).map(({ key }) => key), [...shared, ...additional]);
});

test('澄天登记和列表与海通画布使用一致的 MongoDB、Vertica 图形', () => {
  for (const icon of ['mongodb.svg', 'vertica.svg']) {
    const chengtian = readFileSync(new URL(`data-elements-chengtian/src/assets/icons/${icon}`, root), 'utf8');
    const haitong = readFileSync(new URL(`data-elements-haitong/src/nifi/icons/${icon}`, root), 'utf8');
    assert.equal(haitong.trim(), chengtian.trim(), icon);
  }
});
