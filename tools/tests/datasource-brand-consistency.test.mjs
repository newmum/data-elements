import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
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
  const additional = ['source.api', 'source.sftp'];
  const shuffled = [...shared, ...additional].reverse().map(key => ({ key, category: 'source', label: key }));
  assert.deepEqual(shuffled.sort(compareCanvasManifests).map(({ key }) => key), [...shared, ...additional]);
  assert.equal(shared.indexOf('source.clickhouse'), shared.indexOf('source.elasticsearch') + 1);
});

test('澄天登记和列表与海通画布使用一致的 MongoDB、Vertica、ClickHouse 图形', () => {
  for (const icon of ['mongodb.svg', 'vertica.svg', 'clickhouse.svg']) {
    const chengtian = readFileSync(new URL(`data-elements-chengtian/src/assets/icons/${icon}`, root), 'utf8');
    const haitong = readFileSync(new URL(`data-elements-haitong/src/nifi/icons/${icon}`, root), 'utf8');
    assert.equal(haitong.trim(), chengtian.trim(), icon);
  }
});

test('ClickHouse品牌别名在登记、筛选与字典中统一，不再退回通用数据库图标', () => {
  const source = readFileSync(new URL('data-elements-chengtian/src/components/base/icon.vue', root), 'utf8');
  const normalize = source.match(/const normalizedIcon = computed\(\(\) => \{([\s\S]+?)\n\}\);/)[1]
    .replace('const aliases: Record<string, string>', 'const aliases');
  const resolve = (icon) => vm.runInNewContext(`(() => {${normalize}})()`, { props: { icon } });
  for (const name of ['ClickHouse', 'CLICKHOUSE', 'click-house', 'ch', 'MRS_CLICKHOUSE']) {
    assert.equal(resolve(name), 'clickhouse', name);
  }
  assert.equal(resolve('other'), 'database-network');
  for (const file of [
    'data-elements-chengtian/src/components/form/dict-select.vue',
    'lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/04.登记内容审查(register-confirm).vue',
    'lowcode/02.数据盘点/01.数据登记/2.2.2 数据探查(my-db).vue',
  ]) assert.match(readFileSync(new URL(file, root), 'utf8'), /clickhouse: "clickhouse"/);
  const canvas = readFileSync(new URL('data-elements-haitong/src/nifi/panels/databaseIcons.tsx', root), 'utf8');
  assert.match(canvas, /import clickhouse from '\.\.\/icons\/clickhouse\.svg'/);
  assert.match(canvas, /if \(key === 'sink\.jdbc' && dbType\)/);
});

test('ClickHouse登记使用HTTP JDBC端口并支持已授权的无密码账号，缺少账号或库名仍拒绝', () => {
  const source=readFileSync(new URL(registration,root),'utf8');
  const extract=(name)=>{
    const begin=source.indexOf(`function ${name}(`),end=source.indexOf('\nfunction ',begin+1);
    assert.ok(begin>=0&&end>begin); return source.slice(begin,end);
  };
  const ports=source.slice(source.indexOf('const defaultPorts ='),source.indexOf('const templateTableHeaders ='));
  const form={host:'validation-host',port:'8123',database:'default',username:'default',password:'',schema:'',jdbcType:'serviceName'};
  const context=vm.createContext({connectorForm:form,sourceType:{value:'clickhouse'},sourceTypeLabels:{clickhouse:'ClickHouse'},accessMode:{value:'explore'},connectionEnabled:{value:'1'},
    isRelationalSource:{value:true},isHuaweiMrsHiveProfile:{value:false},isApiPullRegistration:{value:false},
    gaussdbProduct:()=>({protocol:'opengauss'}),gaussdbUrlParameters:()=>'',buildHiveJdbcUrl:()=>''});
  vm.runInContext(ports+extract('refreshJdbcUrl')+extract('validateConnector')+'\nglobalThis.validation={refreshJdbcUrl,validateConnector,defaultPorts};',context);
  assert.equal(context.validation.defaultPorts.clickhouse,'8123');
  context.validation.refreshJdbcUrl();
  assert.equal(form.jdbcURL,'jdbc:clickhouse://validation-host:8123/default');
  assert.doesNotThrow(()=>context.validation.validateConnector());
  form.port='18123';context.validation.refreshJdbcUrl();assert.equal(form.jdbcURL,'jdbc:clickhouse://validation-host:18123/default');
  form.username='';assert.throws(()=>context.validation.validateConnector(),/ClickHouse 用户名/);
  form.username='default';form.database='';assert.throws(()=>context.validation.validateConnector(),/数据库信息/);
});
