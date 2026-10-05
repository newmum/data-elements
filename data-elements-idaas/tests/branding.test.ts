import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeSystemDescription, normalizeSystemTitle, systemName } from '../src/domain/branding.ts';

test('旧品牌配置与工程标识不会重新出现在系统标题或登录说明中', () => {
    for (const oldTitle of ['卓鉴统一身份管理平台', '卓剑统一身份管理平台', 'data-elements-idaas', '公司卓鉴平台']) {
        assert.equal(normalizeSystemTitle(oldTitle), systemName);
    }
    assert.equal(normalizeSystemDescription('卓鉴统一身份管理平台统一管理应用'), '统一身份管理平台统一管理应用');
    assert.equal(normalizeSystemDescription('data-elements-idaas 提供统一认证'), '统一身份管理平台 提供统一认证');
});

test('与旧名称无关的用户自定义系统标题保持原样', () => {
    assert.equal(normalizeSystemTitle('公司统一身份管理平台'), '公司统一身份管理平台');
    assert.equal(normalizeSystemDescription('面向本单位的身份服务'), '面向本单位的身份服务');
});
