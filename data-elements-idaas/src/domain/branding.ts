/** 所有用户可见入口共用系统名称；工程标识仅用于构建与内部引用。 */
export const systemName = '统一身份管理平台';
/** 左上角系统切换入口使用短名称。 */
export const workspaceName = '统一身份管理';
export const platformTitle = systemName;

/** 旧版品牌设置可能仍由服务端返回；仅统一展示名称，不改写业务或审计资料。 */
export function normalizeSystemTitle(title: string | null | undefined): string {
    const value = title?.trim();
    return !value || /卓鉴|卓剑|data-elements-idaas/i.test(value) || value === '统一公众身份管理平台'
        ? systemName
        : title!;
}

/** 旧版登录说明仍可能保存在配置中，展示时沿用系统名称。 */
export function normalizeSystemDescription(description: string | null | undefined): string {
    return (description || '').replace(/卓[鉴剑]统一身份管理平台|卓[鉴剑]平台|卓[鉴剑]|data-elements-idaas/gi, systemName);
}
