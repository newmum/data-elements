<template>
  <div class="flex-y-center gap-1">
    <div
      class="arrow-icon cursor-pointer flex-y-center p-6px color-#1890FFFF hover-bg-#e0edff rounded-5px"
      @click="back ? back() : $router.back()"
    >
      <icon icon="arrow-left" />
    </div>
    <el-breadcrumb class="flex-y-center relative top-1px">
      <el-breadcrumb-item v-for="(item, index) in displayBreadcrumbs" :key="item.path || index">
        <el-text
          v-if="item.redirect === 'noredirect' || index === displayBreadcrumbs.length - 1"
          class="font-bold flex-y-center py-1"
          truncated
        >
          <icon v-if="item.meta?.icon" :icon="item.meta?.icon" />
          {{ item.meta?.title || item.title }}
        </el-text>
        <el-text
          v-else
          truncated
          class="left-item flex-y-center gap-1"
          @click.prevent="handleLink(item)"
        >
          <icon v-if="item.meta?.icon" :icon="item.meta.icon" />
          {{ item.meta?.title || item.title }}
        </el-text>
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeMount } from "vue";
import { RouteLocationMatched } from "vue-router";

// 定义props中breadcrumbs的项结构
interface BreadcrumbItem {
  path: string;
  title: string;
  icon?: string;
  redirect?: string;
  query?: any;
  meta?: {
    title: string;
    icon?: string;
  };
}

// 定义props
const props = defineProps<{
  breadcrumbs?: BreadcrumbItem[];
  back?: () => void;
}>();

const currentRoute = useRoute();
const router = useRouter();
const routeBreadcrumbs = ref<Array<RouteLocationMatched>>([]);

// 优先使用props传入的breadcrumbs，否则使用路由生成的
const displayBreadcrumbs = computed(() => {
  if (props.breadcrumbs && props.breadcrumbs.length > 0) {
    // 标准化props传入的数据结构
    return props.breadcrumbs.map((item) => ({
      ...item,
      meta: item.meta || {
        title: item.title,
        icon: item.icon,
      },
      path: item.path || "",
      redirect: item.redirect || "",
    }));
  }
  return routeBreadcrumbs.value;
});

function getBreadcrumb() {
  const matched = currentRoute.matched.filter((item) => item.meta && item.meta.title);
  routeBreadcrumbs.value = matched.filter((item) => {
    return item.meta && item.meta.title && item.meta.breadcrumb !== false;
  });
}

function handleLink(item: any) {
  const { redirect, path, query } = item;
  if (redirect) {
    router.push(redirect).catch((err) => {
      console.warn(err);
    });
    return;
  }
  router.push({ path, query }).catch((err) => {
    console.warn(err);
  });
}

watch(
  () => currentRoute.path,
  (path) => {
    if (path.startsWith("/redirect/")) {
      return;
    }
    getBreadcrumb();
  }
);

onBeforeMount(() => {
  getBreadcrumb();
});
</script>

<style lang="scss" scoped>
// 覆盖 element-plus 的样式
.el-breadcrumb__inner,
.el-breadcrumb__inner a {
  font-weight: 400 !important;
}
.left-item {
  padding: 5px 4px;
  color: #9ca3af;
  cursor: pointer;
  border-radius: 5px;
  &:hover {
    color: #9ca3af;
    background-color: rgba(0, 0, 0, 0.06);
  }
}
:deep(.el-breadcrumb__inner) {
  max-width: 300px;
}
</style>
