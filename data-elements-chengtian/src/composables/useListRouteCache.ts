import { onScopeDispose, ref } from "vue";
import { useRouter } from "vue-router";

/**
 * 使用完整路由地址作为缓存键，与 AppMain 的页面组件名称一致。
 * @param excludeCacheRoutes 不缓存的路由路径，不含查询参数和锚点。
 * @returns 缓存相关配置
 */
export function useListRouteCache(excludeCacheRoutes: string[] = []) {
  const router = useRouter();
  const cachedViews = ref<string[]>([]);

  /**
   * 检查路由是否为详情页（以detail结尾）
   * 支持以下形式的详情页路径：
   * - /user/asset-detail
   * - /order/123/detail
   * - /product/detail?param=1
   */
  const isDetailRoute = (path: string): boolean => {
    // 移除查询参数部分
    const pathWithoutQuery = path.split("?")[0];
    // 检查路径是否以/detail结尾
    return pathWithoutQuery.endsWith("/detail") || pathWithoutQuery.endsWith("detail");
  };

  // 检查路由是否需要排除缓存
  const isExcludeRoute = (path: string): boolean => {
    return excludeCacheRoutes.includes(path);
  };

  const addCache = (fullPath: string) => {
    if (!fullPath || cachedViews.value.includes(fullPath)) return;
    cachedViews.value.push(fullPath);
  };

  const removeCache = (fullPath: string) => {
    const index = cachedViews.value.indexOf(fullPath);
    if (index > -1) {
      cachedViews.value.splice(index, 1);
    }
  };

  // 清理所有缓存
  const clearAllCache = () => {
    cachedViews.value = [];
  };

  // 在路由切换前让 KeepAlive 缓存即将离开的组件实例。
  const removeGuard = router.beforeEach((to, from) => {
    // 处理离开的页面
    if (from.path && from.path !== to.path) {
      // 如果跳转到详情页+不在排除缓存列中，则缓存当前页面
      if (isDetailRoute(to.path) && !isExcludeRoute(from.path)) {
        addCache(from.fullPath);
      } else {
        // 否则则移除缓存
        removeCache(from.fullPath);
      }
      // 从详情页跳转到非详情页（如另一个列表页），且目标不是缓存页面
      if (
        isDetailRoute(from.path) &&
        !isDetailRoute(to.path) &&
        !cachedViews.value.includes(to.fullPath)
      ) {
        clearAllCache();
      }
    }
  });
  onScopeDispose(removeGuard);

  return {
    cachedViews,
    addCache,
    removeCache,
    isDetailRoute,
    isExcludeRoute,
  };
}
