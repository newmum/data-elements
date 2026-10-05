import { ref } from "vue";
import { useRouter } from "vue-router";

/**
 * 路由缓存管理钩子（基于path的缓存）
 * @param excludeCacheRoutes 需要排除缓存的路由path（不会加入缓存）
 * @returns 缓存相关配置
 */
export function useListRouteCache(excludeCacheRoutes: string[] = []) {
  const router = useRouter();
  // 用path作为缓存的key
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

  // 添加路由path到缓存
  const addCache = (path: string) => {
    if (!path || cachedViews.value.includes(path)) return;
    cachedViews.value.push(path);
  };

  // 从缓存中移除路由path
  const removeCache = (path: string) => {
    const index = cachedViews.value.indexOf(path);
    if (index > -1) {
      cachedViews.value.splice(index, 1);
    }
  };

  // 清理所有缓存
  const clearAllCache = () => {
    cachedViews.value = [];
  };

  // 路由守卫 - 处理缓存逻辑
  router.beforeEach((to, from, next) => {
    // 处理离开的页面
    if (from.path && from.path !== to.path) {
      // 如果跳转到详情页+不在排除缓存列中，则缓存当前页面
      if (isDetailRoute(to.path) && !isExcludeRoute(from.path)) {
        addCache(from.path);
      } else {
        // 否则则移除缓存
        removeCache(from.path);
      }
      // 从详情页跳转到非详情页（如另一个列表页），且目标不是缓存页面
      if (
        isDetailRoute(from.path) &&
        !isDetailRoute(to.path) &&
        !cachedViews.value.includes(to.path)
      ) {
        clearAllCache();
      }
    }

    next();
  });

  return {
    cachedViews,
    addCache,
    removeCache,
    isDetailRoute,
    isExcludeRoute,
  };
}
