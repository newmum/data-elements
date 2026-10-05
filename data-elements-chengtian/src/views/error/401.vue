<template>
  <main class="access-denied-page">
    <section class="access-denied-page__card">
      <div class="access-denied-page__visual" aria-hidden="true">
        <span class="access-denied-page__halo access-denied-page__halo--one" />
        <span class="access-denied-page__halo access-denied-page__halo--two" />
        <div class="access-denied-page__shield">
          <el-icon><Lock /></el-icon>
        </div>
      </div>
      <section class="access-denied-page__content">
        <span class="access-denied-page__eyebrow">权限提醒</span>
        <h1>此页面暂未授权</h1>
        <p>
          当前账号没有访问该功能的权限。登录后系统会自动进入您有权限的第一个菜单；如需访问此功能，请联系菜单授权维护人员。
        </p>
      </section>
      <div class="access-denied-page__actions">
        <el-button type="primary" :loading="navigating" @click="goFirstAccessibleRoute">
          进入可访问菜单
        </el-button>
        <el-button :disabled="navigating" @click="goBack">返回上一页</el-button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { Lock } from "@element-plus/icons-vue";
import { isExternal, isInternal } from "@/utils";
import { usePermissionStore } from "@/store/modules/permission-store";

defineOptions({ name: "Page401" });

const router = useRouter();
const permissionStore = usePermissionStore();
const navigating = ref(false);

const findFirstAccessibleRoute = (routes: any[]): string | null => {
  for (const route of routes || []) {
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
      // 一级菜单可能通过 redirect 指向子页面；即使子节点均为隐藏配置，
      // 一级菜单本身仍是可进入的首个授权菜单。
      if (path) return path;
      continue;
    }
    if (path) return path;
  }
  return null;
};

/**
 * 401 可能在页面重载后先于 Pinia 的菜单缓存恢复。此时动态路由已经由守卫
 * 注册到 Router，直接从 Router 取可见菜单作为兜底，避免明明有菜单却不能
 * 从权限提示页离开的情况。
 */
const findFirstAccessibleRouterRoute = (): string | null => {
  const route = router.getRoutes().find((item) => {
    const path = item.path || "";
    return (
      path !== "/" &&
      path !== "/401" &&
      item.name !== "notFound" &&
      item.name !== "Page401" &&
      !item.meta?.hidden &&
      !isExternal(path) &&
      !isInternal(path)
    );
  });
  return route?.path || null;
};

const goFirstAccessibleRoute = async () => {
  const target =
    findFirstAccessibleRoute(permissionStore.routes) || findFirstAccessibleRouterRoute();
  if (!target) {
    ElMessage.warning("当前账号暂无可访问的功能菜单");
    return;
  }

  navigating.value = true;
  try {
    await router.replace({ path: target });
  } finally {
    navigating.value = false;
  }
};

const goBack = async () => {
  const previousPath = String(window.history.state?.back || "");
  // 登录页、401 页或没有历史记录均不是可返回的业务页面，直接回到当前
  // 账号的首个可访问菜单，避免按钮点击后停留在原页。
  if (
    !previousPath ||
    previousPath === "/401" ||
    previousPath.startsWith("/401?") ||
    previousPath === "/login" ||
    previousPath.startsWith("/login?")
  ) {
    await goFirstAccessibleRoute();
    return;
  }

  navigating.value = true;
  let completed = false;
  const removeAfterEach = router.afterEach((to) => {
    if (completed) return;
    completed = true;
    window.clearTimeout(fallbackTimer);
    removeAfterEach();
    // 上一页在当前权限下已不可访问时，守卫会将其导向 401；此时统一兜底。
    if (to.path === "/401" || to.path === "/login") {
      void goFirstAccessibleRoute();
      return;
    }
    navigating.value = false;
  });
  const fallbackTimer = window.setTimeout(() => {
    if (completed) return;
    completed = true;
    removeAfterEach();
    void goFirstAccessibleRoute();
  }, 800);

  router.back();
};
</script>

<style scoped lang="scss">
.access-denied-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 100%;
  padding: clamp(32px, 8vw, 80px) 24px;
  background: radial-gradient(circle at 50% 0%, rgba(37, 99, 235, 0.11), transparent 42%), #f7f9fc;
}

.access-denied-page__card {
  width: min(100%, 660px);
  padding: clamp(40px, 7vw, 64px);
  text-align: center;
  background: #fff;
  border: 1px solid #e6edf8;
  border-radius: 18px;
  box-shadow: 0 18px 48px rgba(27, 70, 138, 0.09);
}

.access-denied-page__content {
  max-width: 480px;
  margin: 0 auto;

  h1 {
    margin: 16px 0 12px;
    color: #1f3b66;
    font-size: clamp(26px, 3vw, 34px);
    line-height: 1.25;
    font-weight: 700;
  }

  p {
    margin: 0;
    color: #64748b;
    font-size: 14px;
    line-height: 1.85;
  }
}

.access-denied-page__visual {
  position: relative;
  display: grid;
  place-items: center;
  width: 128px;
  height: 128px;
  margin: 0 auto 28px;
}

.access-denied-page__halo {
  position: absolute;
  border-radius: 50%;
}

.access-denied-page__halo--one {
  width: 128px;
  height: 128px;
  background: #edf4ff;
}

.access-denied-page__halo--two {
  width: 96px;
  height: 96px;
  border: 1px solid #d7e7ff;
}

.access-denied-page__shield {
  position: relative;
  display: grid;
  place-items: center;
  width: 64px;
  height: 70px;
  color: #fff;
  font-size: 30px;
  background: linear-gradient(145deg, #3b82f6, #1668dc);
  clip-path: polygon(50% 0, 94% 16%, 87% 67%, 50% 100%, 13% 67%, 6% 16%);
  box-shadow: 0 12px 20px rgba(37, 99, 235, 0.22);
}

.access-denied-page__eyebrow {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  color: #2468d9;
  font-size: 13px;
  font-weight: 600;
  background: #eff8ff;
  border-radius: 999px;
}

.access-denied-page__actions {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 32px;
}

@media (max-width: 760px) {
  .access-denied-page__card {
    padding: 40px 24px;
  }
}
</style>
