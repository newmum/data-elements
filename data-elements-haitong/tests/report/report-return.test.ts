import test from 'node:test';
import assert from 'node:assert/strict';
import { reportReturnTarget, reportSourceLocation, safeReportReturn } from '../../src/report/services/reportReturn';

const current='http://localhost:3002/#/report?datasourceId=validation';
const portal='http://localhost:3000/#/home';

test('registration entry returns to the source list with its filters after refresh and datasource changes',()=>{
  const source='http://localhost:3000/#/datasource-manage/index?mode=upload&page=2';
  const entry=reportSourceLocation(current,'validation',source);
  assert.equal(reportReturnTarget(entry,portal),source);
  const switched=reportSourceLocation(entry,'validation-2',reportReturnTarget(entry,portal));
  assert.equal(reportReturnTarget(switched,portal),source);
});
test('Haitong navigation records the actual origin page and can persist it in the report address',()=>{
  const previous='http://localhost:3002/#/ops/ingress?page=3';
  assert.equal(reportReturnTarget(current,portal,{reportReturnTo:previous}),previous);
  assert.equal(reportReturnTarget(reportSourceLocation(current,'validation',previous),portal),previous);
});
test('return addresses support deployed app prefixes and local loopback aliases',()=>{
  const active='https://platform.test/haitong/#/report';
  const home='https://platform.test/wanxiang/#/home';
  assert.equal(safeReportReturn('https://platform.test/wanxiang/#/datasource-manage/index',active,home),'https://platform.test/wanxiang/#/datasource-manage/index');
  assert.equal(safeReportReturn('http://127.0.0.1:3000/#/datasource-manage/index','http://127.0.0.1:3002/#/report',portal),'http://127.0.0.1:3000/#/datasource-manage/index');
});
test('unsafe, nested report and non-application return targets cannot redirect the browser',()=>{
  for(const candidate of ['https://untrusted.test/#/home','javascript:alert(1)','http://user:pass@localhost:3000/#/home',current,'http://localhost:3002/#/%72eport','http://localhost:3002/api/delete','http://localhost:3000/#/login?redirect=https://untrusted.test']){
    assert.equal(safeReportReturn(candidate,current,portal),undefined,candidate);
  }
  assert.equal(reportReturnTarget(current,portal), 'http://localhost:3002/#/overview');
});
test('return metadata preserves business filters and removes credentials from both query locations',()=>{
  const origin='http://localhost:3000/?token=fake-token&theme=light#/datasource-manage/index?page=2&access_token=fake&ticket=fake';
  assert.equal(safeReportReturn(origin,current,portal),'http://localhost:3000/?theme=light#/datasource-manage/index?page=2');
});
