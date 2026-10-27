import test from 'node:test';
import assert from 'node:assert/strict';
import { buildJdbcUrl, gaussdbConnectionProfile, connectionProductPatch, connectionFieldVisible } from './registeredDatasourceType.ts';

test('GaussDB products preserve their wire protocol and default port', () => {
  for (const [product, protocol, port] of [['gaussdb','opengauss',8000],['gaussdb-jdbc','gaussdb',8000],['opengauss','opengauss',5432],['dws','postgresql',8000],['mysql','mysql',3306]]) {
    assert.deepEqual(gaussdbConnectionProfile(product), {protocol,port});
    assert.ok(buildJdbcUrl('gaussdb','validation.invalid',undefined,'business',undefined,product).startsWith(`jdbc:${protocol}://validation.invalid:${port}/business`));
  }
  assert.throws(() => gaussdbConnectionProfile('unknown'));
});

test('changing GaussDB product clears stale URL and preserves custom port', () => {
  assert.deepEqual(connectionProductPatch({gaussdbVariant:'dws',port:8000,jdbcUrl:'old'},'gaussdbVariant','mysql'),
    {gaussdbVariant:'mysql',port:3306,jdbcUrl:undefined,jdbcURL:undefined,table:"",sourceColumns:[],sourceColumnsByTable:{}});
  assert.equal(connectionProductPatch({gaussdbVariant:'dws',port:18000},'gaussdbVariant','mysql').port,undefined);
});

test('explicit open Hive clears MRS profile and exposes direct connection fields', () => {
  const patch=connectionProductPatch({hiveProfile:'default'},'hiveConnectionMode','open-source');
  assert.equal(patch.hiveProfile,undefined);
  assert.equal(patch.metadataAccessMode,'jdbc');
  assert.equal(connectionFieldVisible('source.hive','host',patch),true);
  assert.equal(connectionFieldVisible('source.hive','hiveProfile',patch),false);
  assert.equal(connectionFieldVisible('source.hive','httpPath',{...patch,transportMode:'http'}),true);
  assert.equal(connectionFieldVisible('source.hive','host',{hiveConnectionMode:'huawei-mrs'}),false);
});
