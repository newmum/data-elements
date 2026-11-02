import test from 'node:test';
import assert from 'node:assert/strict';
import {appDocumentPath,sourcePlatformBaseUrl,sourcePlatformHomeUrl,sourcePlatformLoginUrl} from '../../src/services/platformLocation';
import {reportReturnTarget,reportSourceLocation,safeReportReturn} from '../../src/report/services/reportReturn';

const current='http://validation-platform.test:82/haitong/#/report?datasourceId=validation';

test('production home and source return share the root deployment rather than a historical prefix',()=>{
  const portal=sourcePlatformBaseUrl(current);
  assert.equal(sourcePlatformHomeUrl(portal.href),'http://validation-platform.test:82/#/');
  const source='http://validation-platform.test:82/#/datasource-manage/index?mode=upload&page=2';
  const report=reportSourceLocation(current,'validation',source);
  assert.equal(reportReturnTarget(report,portal.href),source);
  assert.equal(reportReturnTarget(reportSourceLocation(report,'validation-next',source),portal.href),source);
  assert.equal(safeReportReturn('http://validation-platform.test:82/wanxiang/#/',current,portal.href),undefined);
});

test('explicit source deployments retain their exact prefix, origin and port',()=>{
  for(const configured of ['/source-platform/#/home','/wanxiang/#/','https://validation-portal.test:8443/platform/#/']){
    const portal=sourcePlatformBaseUrl(current,configured),source=new URL(portal);
    source.hash='/datasource-manage/index?page=3';
    assert.equal(reportReturnTarget(reportSourceLocation(current,'validation',source.href),portal.href),source.href);
    const home=new URL(sourcePlatformHomeUrl(portal.href));
    assert.equal(home.origin,portal.origin);assert.equal(home.pathname,portal.pathname);assert.equal(home.hash,'#/');
  }
  assert.equal(sourcePlatformBaseUrl('http://localhost:3002/',undefined,true).origin,'http://localhost:3000');
  assert.throws(()=>sourcePlatformBaseUrl(current,'javascript:alert(1)'));
  assert.throws(()=>sourcePlatformBaseUrl(current,'https://user:password@validation-portal.test/'));
});

test('root/index.html and prefixed/index.html are the same app, without trusting arbitrary same-origin endpoints',()=>{
  assert.equal(appDocumentPath('/index.html'),'/');assert.equal(appDocumentPath('/source/index.html'),'/source');
  const portal=sourcePlatformBaseUrl(current).href;
  assert.equal(safeReportReturn('http://validation-platform.test:82/index.html#/datasource-manage/index',current,portal),'http://validation-platform.test:82/index.html#/datasource-manage/index');
  for(const target of ['http://validation-platform.test:82/prod-api/portal/logout','http://validation-platform.test:82/unknown-app/#/','https://untrusted.test/#/','//untrusted.test/#/'])assert.equal(safeReportReturn(target,current,portal),undefined);
});

test('Chrome 100 without URLSearchParams.size retains business query and removes credential parameters',t=>{
  const descriptor=Object.getOwnPropertyDescriptor(URLSearchParams.prototype,'size');
  Reflect.deleteProperty(URLSearchParams.prototype,'size');
  t.after(()=>{if(descriptor)Object.defineProperty(URLSearchParams.prototype,'size',descriptor);});
  const portal=sourcePlatformBaseUrl(current).href;
  const source='http://validation-platform.test:82/?theme=light&token=validation-secret#/datasource-manage/index?page=2&ticket=validation-ticket';
  assert.equal(safeReportReturn(source,current,portal),'http://validation-platform.test:82/?theme=light#/datasource-manage/index?page=2');
  const report=reportSourceLocation(current,'validation',safeReportReturn(source,current,portal)!);
  const login=new URL(sourcePlatformLoginUrl(report+'&access_token=validation-secret',portal));
  const redirect=new URLSearchParams(login.hash.split('?')[1]).get('redirect')!;
  assert.equal(redirect,report);assert.doesNotMatch(redirect,/validation-secret|validation-ticket/);
  assert.equal(reportReturnTarget(redirect,portal),'http://validation-platform.test:82/?theme=light#/datasource-manage/index?page=2');
});

test('a document-query return address works alongside hash-router entry parameters',()=>{
  const source='http://validation-platform.test:82/#/datasource-manage/index',entry=new URL(current);
  entry.search='?'+new URLSearchParams({returnTo:source});
  assert.equal(reportReturnTarget(entry.href,sourcePlatformBaseUrl(current).href),source);
});
