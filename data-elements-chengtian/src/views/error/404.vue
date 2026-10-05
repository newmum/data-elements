<template>
  <div class="wh-full mx-auto flex-center flex-col lg:flex-row">
    <img class="min-w-[23.4375rem] sm:w-150" src="@/assets/images/404.svg" alt="404" />
    <div class="w-75">
      <div class="oops mb-5 text-[2rem] font-bold">OOPS！</div>
      <div class="info text-gray mb-7 text-[0.8125rem]">该页面无法访问。</div>
      <div class="headline mb-2.5 text-xl font-bold text-[#222]">抱歉，您访问的页面不存在。</div>
      <div class="info text-gray mb-7 text-[0.8125rem]">
        请确认您输入的网址是否正确，或者点击下方按钮返回首页。
      </div>
      <el-button round type="primary" class="btn h-9 w-28 mb-10" @click="back">返回首页</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { isExternal, isInternal } from "@/utils";
import { usePermissionStore } from "@/store/modules/permission-store";

defineOptions({ name: "Page404" });

const router = useRouter();
const permissionStore = usePermissionStore();

/**
 * 找到第一个可导航的菜单路由（非外链、非内链、有实际 path）
 * 首页若为外部链接则跳过，依次往后找
 */
function findFirstNavigableRoute(routes: any[]): string | null {
  for (const route of routes) {
    const path = route.path || "";
    if (isExternal(path) || isInternal(path) || route.meta?.type === "group") continue;
    // 有子路由则递归找第一个叶子节点
    if (route.children?.length) {
      const child = findFirstNavigableRoute(route.children);
      if (child) return child;
    } else if (path) {
      return path;
    }
  }
  return null;
}

const back = () => {
  // 从根路由的子菜单中寻找第一个可导航的路由
  const rootRoute = permissionStore.routes.find((r) => r.name === "index");
  const menus = rootRoute?.children || [];
  const target = findFirstNavigableRoute(menus);
  router.push(target || "/");
};
</script>

<style lang="scss" scoped>
@mixin slide-up-animation($delay: 0s) {
  opacity: 0;
  animation-name: slideUp;
  animation-duration: 0.5s;
  animation-delay: $delay;
  animation-fill-mode: forwards;
}

@keyframes slideUp {
  0% {
    opacity: 0;
    transform: translateY(60px);
  }

  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

.oops {
  color: var(--el-color-primary);
  @include slide-up-animation(0s);
}

.headline {
  @include slide-up-animation(0.1s);
}

.info {
  @include slide-up-animation(0.2s);
}

.btn {
  @include slide-up-animation(0.3s);
}
</style>
