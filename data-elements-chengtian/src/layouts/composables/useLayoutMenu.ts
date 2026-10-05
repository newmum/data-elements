import { useRoute } from "vue-router";
import { usePermissionStore } from "@/store";

/**
 * 布局菜单处理逻辑
 */
export function useLayoutMenu() {
  const route = useRoute();
  const permissionStore = usePermissionStore();

  // 常规路由（左侧菜单或顶部菜单）
  const routes = computed(() => permissionStore.routes);

  // 混合布局顶部菜单路由
  const topMenuRoutes = computed(() => {
    const root = permissionStore.routes.find((el) => el.name === "index");
    return root?.children || [];
  });

  // 混合布局左侧菜单路由
  const sideMenuRoutes = computed(() => {
    const matchedPath = route.matched.map((el) => el.path);
    if (topMenuRoutes.value.length > 0) {
      return topMenuRoutes.value.find((el) => matchedPath.includes(el.path))?.children || [];
    }
    return [];
  });

  const breadCrumbRoutes = computed(() => {
    const matchedRoutes = route.matched.filter((r) => r.meta?.desc || r.meta?.title);
    matchedRoutes.shift(); // 移除首页路由，因为首页不需要显示在面包屑中
    return matchedRoutes.map((r) => ({
      ...r,
    }));
  });

  // 当前激活的菜单
  const activeMenu = computed(() => {
    const { meta, path } = route;

    // 如果设置了activeMenu，则使用
    if (meta?.activeMenu) {
      return meta.activeMenu;
    }

    return path;
  });

  // 当前激活path
  const activePaths = computed(() => {
    return route.matched?.map((el) => el.path);
  });

  // 顶部菜单激活路径
  const activeTopPath = computed(() => {
    if (route.meta.activeMenu) {
      return route.meta.activeMenu as string;
    }
    return topMenuRoutes.value.find((el) => activePaths.value.includes(el.path))?.path as string;
  });

  // 侧边菜单激活路径
  const activeSidePath = computed(() => {
    if (route.meta.activeMenu) {
      return route.meta.activeMenu as string;
    }
    return sideMenuRoutes.value.find((el) => activePaths.value.includes(el.path))?.path as string;
  });

  return {
    routes,
    topMenuRoutes,
    sideMenuRoutes,
    breadCrumbRoutes,
    activeMenu,
    activePaths,
    activeTopPath,
    activeSidePath,
  };
}
