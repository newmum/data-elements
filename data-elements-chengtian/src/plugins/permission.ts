import type { RouteRecordRaw } from "vue-router";
import router from "@/router";
import { usePermissionStore, useSettingStore, useSettingStoreHook, useUserStore } from "@/store";
import { ROLE_ROOT } from "@/enums";
import { loadAllRuntimeSfc } from "@/plugins/compiler/runtime-sfc";
import type { App } from "vue";
import { addClass, getUrlKey, isExternal, isInternal, removeClass } from "@/utils";
import { Storage } from "@/utils/storage";
import { setupDict } from "@/plugins/dict";
import { pingaoLoginUrl, pingaoSsoEnabled } from "@/api/pingao-sso-api";

const SSO_PARAMS = ["ticket", "token", "code"] as const; // 权限认证登录标识
/**
 * 由登录页发起的“恢复上次页面”跳转标记。
 *
 * 不能只通过 `from.path === "/login"` 判断：动态菜单尚未注册时，浏览器
 * 恢复的地址可能会先经过一次重定向，导致来源不再是登录页。该标记只由
 * 登录成功后的跳转携带，并会在首次路由守卫中移除。
 */
const LOGIN_RECOVERY_QUERY = "__loginRecovery";

/**
 * 返回当前用户菜单树中的第一个可访问叶子路由。
 * 动态菜单里同时挂载了隐藏的 401/404 路由，不能把它们作为登录后的兜底首页。
 */
function findFirstAccessibleRoute(routes: RouteRecordRaw[]): string | null {
  for (const route of routes) {
    const path = route.path || "";
    if (
      route.name === "notFound" ||
      route.name === "Page401" ||
      route.meta?.hidden ||
      route.meta?.type === "group" ||
      isExternal(path) ||
      isInternal(path)
    ) {
      continue;
    }
    if (route.children?.length) {
      const child = findFirstAccessibleRoute(route.children);
      if (child) return child;
      // 某些一级菜单使用 redirect 指向运行时页面，子节点本身是隐藏页或
      // 分组时没有可见叶子节点。一级菜单仍可直接访问，不能因此遗漏。
      if (path) return path;
      continue;
    }
    if (path) return path;
  }
  return null;
}

const isNotFoundRoute = (matched: RouteRecordRaw[]) =>
  matched.some((route) => route.name === "notFound");

const isAccessDeniedRoute = (matched: RouteRecordRaw[]) =>
  matched.some((route) => route.name === "Page401");

const isLoginRecoveryNavigation = (to: any, from: any) =>
  from.path === "/login" || to.query?.[LOGIN_RECOVERY_QUERY] === "1";

const withoutLoginRecoveryQuery = (to: any) => {
  const query = { ...to.query };
  delete query[LOGIN_RECOVERY_QUERY];
  return {
    path: to.path,
    query,
    hash: to.hash,
    replace: true,
  };
};

/**
 * 跳转到登录页（支持内部路径和外部 URL）
 * 跳转前强制刷新系统设置，确保 loginUrl 为最新值
 * 外部 URL 使用 location.href 全量跳转，内部路径使用 router next
 * @returns true 表示已通过 location.href 跳转（调用方应 return，不再调用 next）
 */
async function navigateToLogin(
  redirectPath: string,
  next: ReturnType<typeof router.beforeEach> extends (...args: any[]) => any
    ? (arg?: any) => void
    : never
): Promise<boolean> {
  if (pingaoSsoEnabled()) {
    window.location.assign(pingaoLoginUrl());
    return true;
  }
  const settingsStore = useSettingStoreHook();
  await settingsStore.loadingSettings(true);
  const loginUrl = settingsStore.loginUrl || "/login";
  const redirect = encodeURIComponent(redirectPath);

  if (isExternal(loginUrl)) {
    window.location.href = `${loginUrl}`;
    return true;
  }

  next({ path: loginUrl, query: { redirect } });
  return false;
}

/**
 * 从 URL 中提取 SSO 认证参数（token  ticket  code）
 * 返回 { value, key } 或 null
 */
function extractSsoParam(
  query: Record<string, any>
): { value: string; key: "ticket" | "token" | "code" } | null {
  for (const key of SSO_PARAMS) {
    const val = query[key];
    if (val && typeof val === "string" && val.trim()) return { value: val.trim(), key };
  }
  return null;
}

export function setupPermission(app: App<Element>) {
  const whiteList = ["/login", "/pingao-sso/callback"]; // 无需登录的页面

  // 加载系统设置
  router.beforeEach(async (to, from, next) => {
    // NProgress.start();

    /** 判断是否隐藏导航栏*/
    const showNav = getUrlKey("showNav");
    if (showNav === "0" || Storage.sessionGet("showNav") === false) {
      Storage.sessionSet("showNav", false);
      addClass(document.body, "hidden-nav");
    }
    if (showNav === "1") {
      Storage.sessionSet("showNav", true);
      removeClass(document.body, "hidden-nav");
    }

    try {
      if (whiteList.includes(to.path)) {
        if (to.path === "/login" && pingaoSsoEnabled()) {
          window.location.assign(pingaoLoginUrl());
          return;
        }
        next();
        return;
      }

      // ====== SSO 统一认证拦截 ======
      const ssoParam = pingaoSsoEnabled() ? null : extractSsoParam(to.query);
      if (ssoParam) {
        const userStore = useUserStore();
        console.log("[SSO] 检测到认证参数，开始自动登录...", ssoParam.key);

        try {
          await userStore.ssoLogin(ssoParam.value, ssoParam.key as "ticket" | "code");
          console.log("[SSO] 认证成功，清除 URL 参数");
          // 构建清除 SSO 参数后的干净路由，一次性 next 跳转，避免重复触发守卫
          const cleanQuery = { ...to.query };
          delete cleanQuery[ssoParam.key];
          const cleanRoute = { ...to, query: cleanQuery, replace: true };
          next(cleanRoute);
        } catch (error) {
          console.warn("[SSO] 认证失败:", error);
          if (await navigateToLogin(to.path, next)) return;
        }
        return;
      }

      // 使用 store 暴露的登录态，便于后续扩展（如基于过期时间等）
      const isLoggedIn = useUserStore().isLoggedIn();

      // 未登录处理
      if (!isLoggedIn && useSettingStore().needLogin) {
        if (await navigateToLogin(to.fullPath, next)) return;
        return;
      }

      // 已登录用户的正常访问
      const permissionStore = usePermissionStore();
      const userStore = useUserStore();

      // 生成动态路由
      if (!permissionStore.isDynamicRoutesGenerated) {
        if (!userStore.userInfo?.roles?.length) {
          await userStore.getUserInfo();
        }

        // 一次性加载所有字典
        setupDict();

        console.time(`[loadAllRuntimeSfc]   `);
        await loadAllRuntimeSfc(app);
        console.timeEnd(`[loadAllRuntimeSfc]   `);

        try {
          $form.loadAll();
        } catch (error) {
          console.error("❌ 加载动态表单失败:", error);
        }

        const dynamicRoutes = await permissionStore.generateRoutes();
        dynamicRoutes.forEach((route: RouteRecordRaw) => {
          router.addRoute(route);
        });

        // 登录后优先恢复上次页面；若该页面不属于当前用户菜单，改为该用户
        // 的第一个可访问菜单，而不是进入通配的 404 页。
        const firstAccessibleRoute = findFirstAccessibleRoute(permissionStore.routes);
        // `to.matched` 是注册动态路由前生成的，需重新解析一次才能准确识别
        // 刚注册的通配路由是否接住了当前用户无权访问的旧地址。
        const resolvedTarget = router.resolve(to.fullPath);
        const isLoginRecovery = isLoginRecoveryNavigation(to, from);
        if (
          isLoginRecovery &&
          (to.path === "/" ||
            to.path === "/401" ||
            isNotFoundRoute(resolvedTarget.matched as RouteRecordRaw[]) ||
            isAccessDeniedRoute(resolvedTarget.matched as RouteRecordRaw[]))
        ) {
          next({ path: firstAccessibleRoute || "/401", replace: true });
          return;
        }
        // 登录恢复标记只用于首次权限判断，不能留在业务页面 URL 中。
        next(isLoginRecovery ? withoutLoginRecoveryQuery(to) : { ...to, replace: true });
        return;
      }

      // 动态路由未命中时，区分两种场景：
      // 1. 刚登录恢复的旧页面无权限：进入当前用户第一个菜单；
      // 2. 已登录后主动访问非授权地址：展示无权限提示，不伪装成 404。
      if (to.matched.length === 0 || isNotFoundRoute(to.matched as RouteRecordRaw[])) {
        if (isLoginRecoveryNavigation(to, from)) {
          const firstAccessibleRoute = findFirstAccessibleRoute(permissionStore.routes);
          next({ path: firstAccessibleRoute || "/401", replace: true });
          return;
        }
        next({ path: "/401", query: { from: to.fullPath }, replace: true });
        return;
      }

      // 设置页面标题
      const title = (to.params.title as string) || (to.query.title as string);
      if (title) {
        to.meta.title = title;
      }

      next();
    } catch (error) {
      console.error("❌ 路由守卫异常:", error);
      // 出错时清理状态并跳转登录页，避免用户卡在空白/僵尸状态
      try {
        await useUserStore().resetAllState();
      } catch (resetError) {
        console.error("❌ 无法重置用户信息:", resetError);
      }
      if (await navigateToLogin(to.fullPath, next)) return;
      return;
    }
  });

  router.afterEach(() => {});
}

/** 判断是否有权限 */
export function hasAuth(value: string | string[], type: "button" | "role" = "button") {
  const { roles, perms } = useUserStore().userInfo;

  // 超级管理员 拥有所有权限
  if (type === "button" && roles.includes(ROLE_ROOT)) {
    return true;
  }

  const auths = type === "button" ? perms : roles;
  return typeof value === "string"
    ? auths.includes(value)
    : value.some((perm) => auths.includes(perm));
}
