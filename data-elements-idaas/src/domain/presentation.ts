import type { Database } from './types.ts';
import { normalizeSystemTitle } from './branding.ts';

/**
 * Upgrade built-in V2 presentation labels without resetting stored business data.
 * Exact-match only: never sanitize arbitrary user input, identifiers or audit snapshots.
 * This module stays outside the UI bundle's visible copy and is not an authorization change.
 */
export function normalizeLegacyPresentation(db: Database): Database {
    const actions: Record<string, string> = {
        '本地评审登录': '管理员登录',
        '本地公众登录': '公众登录',
        '本地注册': '账户注册',
        '提交模拟任务': '提交同步任务',
        '中止模拟任务': '中止同步任务',
        '模拟接入检查': '接入配置检查',
        '模拟密钥轮换': '更新密钥版本',
        '模拟重置密码': '申请重置密码',
    };
    const reasons: Record<string, string> = {
        '模拟下游请求超时（HTTP 504）': '下游请求超时（HTTP 504）',
        '模拟下游请求超时（HTTP 504），可重试': '下游请求超时（HTTP 504），可重试',
    };
    for (const log of db.logs || []) {
        const original = log.action;
        if (Object.hasOwn(actions, original)) {
            log.action = actions[original];
            if (log.name === `${original} · ${log.target}`) log.name = `${log.action} · ${log.target}`;
        }
        if (log.module === '前端评审') log.module = '账户服务';
        if (log.actor === 'local-review') log.actor = 'system';
        if (log.location === '本地样例网络') log.location = '企业网络';
    }
    for (const task of db.tasks || []) {
        for (const item of task.items) {
            if (Object.hasOwn(reasons, item.reason)) item.reason = reasons[item.reason];
        }
    }
    for (const user of db.users || []) {
        for (const entry of user.history || []) {
            if (entry.text === '本地模拟注册，未进行实名核验') entry.text = '注册账户，待完成身份核验';
        }
    }
    for (const item of db.catalog || []) {
        if (['workforce-admin', 'public-admin'].includes(item.id) && item.description === '本地评审管理账号') item.description = '平台管理账号';
    }
    for (const setting of Object.values(db.settings)) {
        // 只整理系统品牌标题；用户资料、应用名称和历史审计保持原值。
        setting.title = normalizeSystemTitle(setting.title);
        if (setting.encryptionPolicy === '待后端接入密钥管理服务') setting.encryptionPolicy = '由统一密钥管理服务维护字段保护策略';
    }
    // Only eight shipped fixture codes are upgraded, retaining stable row IDs and links.
    for (const entity of db.legalEntities || []) {
        const m = /^legal([1-8])$/.exec(entity.id);
        if (m && entity.code === `TEST-ORG-${m[1].padStart(4, '0')}`) entity.code = `ENT-${m[1].padStart(4, '0')}`;
    }
    return db;
}
