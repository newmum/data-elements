import type { RouteRecordRaw } from "vue-router";
import { constantRoutes, errorRoutes, rootRoute } from "@/router";
import { store } from "@/store";
import router from "@/router";

import MenuAPI, { type RouteVO } from "@/api/system/menu-api";
import { isExternal, isInternal } from "@/utils";
import { set } from "lodash-es";
const modules = import.meta.glob("../../views/**/**.vue");
const Layout = () => import("@/layouts/index.vue");
const WorkLayout = () => import("@/layouts/views/WorkLayout.vue");
const ShowComponent = () => import("@/layouts/views/ShowComponent.vue");
const ShowIframe = () => import("@/layouts/views/ShowIframe.vue");

export const usePermissionStore = defineStore("permission", () => {
  // 所有路由（静态路由 + 动态路由）
  const routes = ref<RouteRecordRaw[]>([]);
  const addRoutes = ref<RouteRecordRaw[]>([]);
  // 混合布局的左侧菜单路由
  const mixLayoutSideMenus = ref<RouteRecordRaw[]>([]);
  // 混合布局的顶部菜单路由
  const mixLayoutTopMenus = computed(() => {
    const root = routes.value.find((el) => el.name === "index");
    return root?.children || [];
  });
  // 动态路由是否已生成
  const isDynamicRoutesGenerated = ref(false);

  /**
   * 生成动态路由
   */
  async function generateRoutes(): Promise<RouteRecordRaw[]> {
    try {
      const data = await MenuAPI.getRoutes(); // 获取当前登录人拥有的菜单路由
      const dynamicRoutes = parseDynamicRoutes([
        {
          ...rootRoute,
          children: [...data, ...errorRoutes] as any,
        },
      ]);

      addRoutes.value = [...dynamicRoutes];
      routes.value = [...constantRoutes, ...dynamicRoutes];

      isDynamicRoutesGenerated.value = true;

      return dynamicRoutes;
    } catch (error) {
      console.error("❌ Failed to generate routes:", error);
      isDynamicRoutesGenerated.value = false;
      throw error;
    }
  }

  /**
   * 设置混合布局的左侧菜单
   */
  const setMixLayoutSideMenus = (parentPath: string) => {
    const parentMenu = routes.value.find((item) => item.path === parentPath);
    mixLayoutSideMenus.value = parentMenu?.children || [];
  };

  /**
   * 重置路由状态
   */
  const resetRouter = () => {
    // 移除动态路由
    const constantRouteNames = new Set(constantRoutes.map((route) => route.name).filter(Boolean));
    routes.value.forEach((route) => {
      if (route.name && !constantRouteNames.has(route.name)) {
        router.removeRoute(route.name);
      }
    });

    // 重置状态
    routes.value = [...constantRoutes];
    mixLayoutSideMenus.value = [];
    isDynamicRoutesGenerated.value = false;
  };

  return {
    routes,
    mixLayoutSideMenus,
    mixLayoutTopMenus,
    isDynamicRoutesGenerated,
    generateRoutes,
    setMixLayoutSideMenus,
    resetRouter,
  };
});

/**
 * 解析后端返回的路由数据并转换为 Vue Router 兼容的路由配置
 *
 * @param rawRoutes 后端返回的原始路由数据
 * @param rootPath
 * @returns 解析后的路由集合
 */
const parseDynamicRoutes = (rawRoutes: RouteVO[], rootPath = ""): RouteRecordRaw[] => {
  const parsedRoutes: RouteRecordRaw[] = [];

  rawRoutes.forEach((route) => {
    // 分组标题节点：保留在路由树中供侧边栏渲染，不解析组件和子路由
    if (route.meta?.type === "group") {
      const groupPath = rootPath ? `${rootPath}/${route.path || ""}` : route.path || "";
      parsedRoutes.push({ path: groupPath, meta: route.meta } as RouteRecordRaw);
      return;
    }

    const { path = "", children = [], component, name } = route;

    // path去掉路径前面的'/'，作为name
    const routeName = name || path?.replace(/^\//, "");
    let routePath = rootPath ? `${rootPath}${path}` : path;
    // Keep the granted menu/component identity while exposing a general node
    // configuration URL for extraction, push, and service nodes.
    if (route.componentName === "nifi-node-config") routePath = "/node-config";

    // 筛选出可导航的子菜单（排除分组标题、外链、内链）
    const visibleChildren = children?.filter(
      (el) => el.meta?.type !== "group" && !isExternal(el.path || "") && !isInternal(el.path || "")
    );
    const hasVisibleChildren = visibleChildren && visibleChildren.length > 0;

    // 设置重定向到第一个可导航的子菜单
    let redirect = hasVisibleChildren ? `${routePath}${visibleChildren[0].path}` : route.redirect;

    // 设置路由挂载组件
    let routeComponent: any = component;
    if (route["componentName"]) {
      /*动态组件*/
      routeComponent = ShowComponent;
      route["props"] = { name: route.componentName };
    } else if (component?.toString() === "Layout") {
      routeComponent = Layout;
    } else if (component?.toString() === "WorkLayout") {
      routeComponent = WorkLayout;
    } else if (typeof component === "string") {
      routeComponent = modules[`../../views/${component}.vue`];
    }

    // 处理外链
    if (isExternal(path)) {
      routePath = path;
      redirect = undefined;
      set(route, "meta.target", route.meta?.target ?? "_blank");
    }

    // 处理内链
    if (isInternal(path)) {
      routePath = location.origin + "/" + path;
      redirect = undefined;
      set(route, "meta.target", route.meta?.target ?? "_self");
    }

    // iframe页面处理逻辑
    if (route.meta?.openMode === "iframe") {
      routeComponent = ShowIframe;
      set(route, "meta.params", { url: route.path });
      routePath = `/${routeName}`;
    }

    const normalizedRoute = {
      ...route,
      path: routePath,
      name: routeName,
      redirect,
      component: routeComponent,
    } as RouteRecordRaw;

    // 递归解析子路由
    if (children) {
      normalizedRoute.children = parseDynamicRoutes(children, normalizedRoute.path);
    }

    parsedRoutes.push(normalizedRoute);
  });

  return parsedRoutes;
};

/**
 * 遍历路由树收集缓存路由
 * @param nodes 路由节点
 * @param path 当前路径
 * @param result 结果数组
 */
const traverseRoutes = (nodes: RouteRecordRaw[], path: string[], result: string[][]) => {
  nodes.forEach((node) => {
    const newPath: string[] = node.name ? [...path, String(node.name)] : [...path];

    // 叶子节点且需要缓存
    if (!node.children?.length && node.meta?.keepAlive) {
      result.push(newPath);
    }

    // 递归处理子节点
    if (node.children?.length) {
      traverseRoutes(node.children, newPath, result);
    }
  });
};

/**
 * 导出此hook函数用于在非组件环境(如其他store、工具函数等)中获取权限store实例
 *
 * 在组件中可直接使用usePermissionStore()，但在组件外部需要传入store实例
 * 此函数简化了这个过程，避免每次都手动传入store参数
 */
export function usePermissionStoreHook() {
  return usePermissionStore(store);
}
