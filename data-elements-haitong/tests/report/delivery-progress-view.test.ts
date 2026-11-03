import test from 'node:test';
import assert from 'node:assert/strict';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import DeliveryProgress from '../../src/report/components/DeliveryProgress';

test('byte percentages belong to upload, waiting for server acknowledgment does not invent a percentage',()=>{
  const uploading=renderToStaticMarkup(createElement(DeliveryProgress,{progress:{tableKey:'validation',phase:'uploading',completed:50,total:100}}));
  assert.match(uploading,/上传文件/);assert.match(uploading,/50%/);
  const waiting=renderToStaticMarkup(createElement(DeliveryProgress,{progress:{tableKey:'validation',phase:'waiting',completed:100,total:100}}));
  assert.match(waiting,/等待回执/);assert.doesNotMatch(waiting,/100%|aria-valuenow/);
  const recoverable=renderToStaticMarkup(createElement(DeliveryProgress,{onRefresh:()=>{}}));
  assert.match(recoverable,/刷新状态/);assert.doesNotMatch(recoverable,/已上报/);
});
