import type { App } from "vue";
import { createRouter, createWebHashHistory, type RouteRecordRaw, RouterView } from "vue-router";
import { RouteVO } from "@/api/system/menu-api";

export const Layout = () => import("@/layouts/index.vue");

// 异常页面路由
export const errorRoutes: RouteRecordRaw[] = [
  // 404路由必须放在最后面，否则会拦截所有其他路由
  {
    path: "/:pathMatch(.*)*",
    name: "notFound",
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: "",
        component: () => import("@/views/error/404.vue"),
      },
    ],
  },
  {
    path: "/401",
    name: "Page401",
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: "",
        component: () => import("@/views/error/401.vue"),
      },
    ],
  },
];

// 主路由
export const rootRoute: RouteVO = {
  path: "",
  name: "index",
  redirect: "/",
  children: [],
};

// 静态路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: "/pingao-sso/callback",
    component: () => import("@/views/login/pingao-callback.vue"),
    meta: { hidden: true },
  },
  {
    path: "/redirect",
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: "/redirect/:path(.*)",
        component: () => import("@/views/error/redirect.vue"),
      },
    ],
  },
  {
    path: "/login",
    component: () => import("@/views/login/index.vue"),
    meta: { hidden: true },
  },
];

/**
 * 创建路由
 */
const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
  // 刷新时，滚动条位置还原
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

// 全局注册 router
export function setupRouter(app: App<Element>) {
  app.use(router);
}

export default router;
