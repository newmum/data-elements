import test from 'node:test';
import assert from 'node:assert/strict';
import { productGroups, productCenterUrl, type CenterAddresses } from '../src/app/platformNavigation.ts';

test('系统切换的开发地址按实际所属工程分配，且不携带来源页面参数', () => {
    const addresses: CenterAddresses = { wanxiang: 'http://localhost:3010/', qizhi: 'http://localhost:3001/', haitong: 'http://localhost:3002/', idaas: 'http://localhost:3005/' };
    for (const group of productGroups) for (const center of group.centers) {
        const url = new URL(productCenterUrl(center, addresses, 'http://localhost:3005/#/console/public/overview?user=private'));
        assert.equal(url.origin, new URL(addresses[center.owner]).origin);
        assert.equal(url.hash, `#${center.home}`);
        assert.equal(url.search, '');
        assert.ok(!url.href.includes('private'));
    }
});

test('生产部署前缀及独立部署覆盖地址保持正确', () => {
    const addresses: CenterAddresses = { wanxiang: '/wanxiang-governance/', qizhi: '/qizhi/', haitong: '/haitong/', idaas: '/idaas/' };
    for (const group of productGroups) for (const center of group.centers) {
        const url = new URL(productCenterUrl(center, addresses, 'https://platform.example/idaas/#/console/workforce/overview'));
        assert.equal(url.origin, 'https://platform.example');
        assert.equal(url.pathname, addresses[center.owner]);
        assert.ok(!url.href.includes('localhost'));
    }
    const identity = productGroups.flatMap(group => [...group.centers]).find(center => center.id === 'idaas')!;
    assert.equal(productCenterUrl(identity, { ...addresses, idaas: 'https://identity.example/console/' }, 'https://platform.example/'), 'https://identity.example/console/#/console/workforce/overview');
});
