<!--
普通详情：（面包屑+页签）+内容
个人中心详情：面包屑+（页签+内容）

超市详情：面包屑+自定义内容+（页签+内容）
-->
<template>
  <div class="detail-container" :class="{ 'work-detail': isWorkDetail }">
    <div class="detail-container__breadcrumb">
      <breadcrumb
        v-if="isBreadcrumbs"
        :breadcrumbs="breadcrumbs"
        :class="{ 'mb-2': config.hiddenTab }"
        :back="routeBack"
      />
    </div>

    <div v-if="!config.hiddenTab" class="detail-container__tab">
      <u-tabs v-model="detailStore.currentTab" :tabs="tabs" :show-border="isWorkDetail"></u-tabs>
    </div>


    <div class="detail-container__content flex flex-col">
      <component
        :is="detailStore.current.component"
        v-bind="detailStore.current.props"
        :editable="detailStore.isEditable"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed } from "vue";
import { useDetailStore, usePermissionStore } from "@/store";
import { storeToRefs } from "pinia";
import { useRoute, useRouter, onBeforeRouteUpdate, onBeforeRouteLeave } from "vue-router";

const route = useRoute();
const router = useRouter();
const detailStore = useDetailStore();
const { state } = detailStore;
const { config } = storeToRefs(detailStore);

const props = withDefaults(
  defineProps<{
    type: string;
    tabType: string; // 指定展示页签类型，如超市详情，优先级大于type
    id: string;
    title: string;
    dataSourceType: string;
    isBreadcrumbs?: boolean;
  }>(),
  {
    isBreadcrumbs: true,
  }
);

// 详情初始化
const type = props.type || (route.query.type as string) || "db";
const title = props.title || (route.query.title as string);
const id = props.id || (route.query.id as string);
const dataSourceType = props.dataSourceType || (route.query.dataSourceType as string);

const configMap = {
  app: {
    title: "业务系统",
    tabs: [
      { title: "业务系统详情", component: "base-detail", props: { tid: id } },
      {
        title: "数据库表",
        component: "my-db",
        props: {
          appId: id,
          dataSourceType: dataSourceType,
          style: "margin: 0;border-radius: 0;box-shadow: none;",
        },
      },
      {
        title: "关联目录",
        component: "my-catalog",
        props: {
          appId: id,
          dataSourceType: dataSourceType,
          style: "margin: 0;border-radius: 0;box-shadow: none;",
        },
      },
    ],
  },
  db: {
    title: "数据库",
    tabs: [
      { title: "数据库详情", component: "base-detail", props: { tid: id } },
      { title: "数据表信息", component: "db-table", props: { dbId: id, dataSourceType: dataSourceType } },
    ],
  },
  table: {
    title: "数据表",
    tabs: [
      { title: "数据表信息", component: "base-detail", props: { tid: id } },
      { title: "数据表字段", component: "table-col", props: { tableId: id } },
      { title: "数据预览", component: "table-preview", props: { tableId: id } },
      { title: "资产图谱", component: "table-tupu" },
      { title: "血缘分析", component: "table-blood" },
    ],
  },
  catalog: {
    title: "资源目录",
    tabs: [
      { title: "资源目录详情", component: "base-detail", props: { tid: id } },
      { title: "数据项信息", component: "catalog-item", props: { catalogId: id } },
      { title: "挂接资源", component: "catalog-rela", props: { catalogId: id, dataSourceType: dataSourceType } },
      { title: "关联资源", component: "catalog-rela-table", props: { catalogId: id, dataSourceType: dataSourceType } },
      { title: "汇聚情况", component: "catalog-collect", props: { catalogId: id } },
      { title: "数据质量", component: "quality-report", props: { catalogId: id } },
    ],
  },
  fileCatalog: {
    title: "文件目录",
    tabs: [
      { title: "数据目录详情", component: "base-detail", props: { tid: id } },
      { title: "数据项信息", component: "fileCatalog-item", props: { fileCatalogId: id } },
      { title: "数据预览", component: "fileCatalog-data", props: { fileCatalogId: id } },
    ],
  },
  api: {
    title: "API服务",
    tabs: [
      { title: "服务详情", component: "api-detail", props: { tid: id } },
      // { title: "服务信息", component: "api-param", props: { apiId: id } },
      { title: "API调试", component: "api-test", props: { apiId: id } },
      { title: "使用情况", component: "api-use", props: { apiId: id } },
    ],
  },
  dwd: {
    title: "基础层详情",
    hiddenTab: true,
    tabs: [{ title: "基础层详情", component: "dwd-detail" }],
  },
  dst: {
    title: "数源层详情",
    hiddenTab: true,
    tabs: [{ title: "数源层详情", component: "dst-detail" }],
  },
  dwm: {
    title: "整合层详情",
    hiddenTab: true,
    tabs: [
      { title: "整合层详情", component: "dwm-detail" },
    ],
  },
  ods: {
    title: "贴源层详情",
    hiddenTab: true,
    tabs: [{ title: "数据汇聚", component: "data-convergence" }],
  },
  dws: {
    title: "服务层详情",
    hiddenTab: true,
    icon: "dws",
    tabs: [{ title: "数据服务详情", component: "dws-detail" }],
  },
  "tags-detail": {
    title: "服务层详情",
    icon: "dws",
    hiddenTab: true,
    tabs: [{ title: "数据标签库详情", component: "tags-detail" }],
  },
  "serve-detail": {
    title: "服务层详情",
    hiddenTab: true,
    icon: "dws",
    tabs: [{ title: "数据服务详情", component: "data-serve" }],
  },
  "market-catalog": {
    title: "超市详情-资源目录",
    tabs: [
      { title: "数据项信息", component: "catalog-market-item", props: { catalogId: id } },
      { title: "资源信息", component: "catalog-market-res", props: { catalogId: id } },
      { title: "数据预览", component: "catalog-collect", props: { catalogId: id } },
    ],
  },
  "warehouse-editor": {
    title: "应用仓库编辑页",
    hiddenTab: true,
    icon: "el-icon-edit",
    tabs: [{ title: "编辑", component: "app-editor" }],
  },
  "warehouse-page-editor": {
    title: "应用仓库编辑页-专题页",
    hiddenTab: true,
    icon: "el-icon-edit",
    tabs: [{ title: "专题页编辑", component: "sfc-component" }],
  },
  "warehouse-report-editor": {
    title: "应用仓库编辑页-报表页",
    hiddenTab: true,
    icon: "el-icon-edit",
    tabs: [{ title: "报表页编辑", component: "sfc-component" }],
  },
  "warehouse-visual-editor": {
    title: "应用仓库编辑页-大屏页",
    hiddenTab: true,
    icon: "el-icon-edit",
    tabs: [{ title: "大屏页编辑", component: "sfc-component" }],
  },
  "warehouse-form-editor": {
    title: "应用仓库编辑页-表单页",
    hiddenTab: true,
    icon: "el-icon-edit",
    tabs: [{ title: "表单页编辑", component: "u-designer" }],
  },
};

// 加载配置
detailStore.init(type, configMap[props.tabType || type]);
// 页签
const tabs = state.tabs?.map((el) => ({
  label: el.title,
  value: el.title,
}));

const permissionStore = usePermissionStore();
// 判断是否WorkLayout布局下的详情
const isWorkDetail = computed(() => {
  const matched = route.matched.filter((item) => item.meta && item.meta.title);
  return (
    permissionStore.mixLayoutTopMenus.find((el) => el.path === matched[0].path)?.component?.name ===
    "WorkLayout"
  );
}); // 方便资产详情页添加mock数据
detailStore.state["title"] = title;
const breadcrumbs = computed(() => {
  const matched = route.matched.filter((item) => item.meta && item.meta.title);
  const a = matched.filter((item) => {
    // 个人中心特殊，要显示在面包屑中
    return (item.meta && item.meta.title && !item.meta.hidden) || item.name === "workplace";
  });
  return [...a, ...detailStore.history];
});

/**
 * 以下代码，实现详情面包屑嵌套
 */
const loadInitialDetail = (assetType: string, assetId: string) => {
  if (assetType === "app") {
    return $common.post("/dst/application/detail", { tid: assetId });
  }
  if (assetType === "db") {
    return $common.post("/dst/database/detail", { tid: assetId });
  }
  if (assetType === "catalog" || assetType === "fileCatalog") {
    return $common.post("/dst/catalog/detail", { tid: assetId });
  }
  // 表和 API 详情由各自的详情组件加载；这里不再错误地按目录查询。
  return Promise.resolve(null);
};

onMounted(async () => {
  if (id) {
    try {
      const data = await loadInitialDetail(type, id);
      detailStore.setData(data || {});
    } catch {
      detailStore.setData({});
    }
  }

  // 原有逻辑不变
  const findIndex = detailStore.findHistory(route.query);
  if (findIndex === -1) {
    detailStore.history.push({
      path: route.path,
      query: route.query,
      meta: {
        title: route.query.title,
        icon: configMap[route.query.type]?.icon ?? route.query.type,
      },
    });
  }
  if (route.query.tab) {
    detailStore.currentTab = (route.query.tab as string) || "";
  } else {
    detailStore.initTab();
  }
});

// 面包屑返回方法重写
const routeBack = () => {
  // 有两个及以上详情页
  const { path, query } = breadcrumbs.value[breadcrumbs.value.length - 2] as any;
  router.push({ path, query });
};

// 详情页间的跳转，则缓存或移除当前详情
onBeforeRouteUpdate((to, from, next) => {
  const findIndex = detailStore.findHistory(to.query);
  // 若前往新详情则添加记录
  if (findIndex === -1) {
    // 缓存当前详情页签
    const last = detailStore.history[detailStore.history.length - 1];
    last.query = Object.assign({ tab: detailStore.currentTab }, last.query);

    detailStore.history.push({
      path: to.path,
      query: to.query,
      meta: { title: to.query.title, icon: configMap[to.query.type]?.icon ?? to.query.type },
    });
  }
  // 若前往历史详情，则移除后面的详情页
  else {
    detailStore.history = detailStore.history.slice(0, findIndex + 1);
  }
  // 清除详情
  detailStore.data = {};
  next();
});

// 离开详情页，则销毁记录
onBeforeRouteLeave((to, from, next) => {
  next();
  setTimeout(() => {
    detailStore.$destroy();
  }, 200);
});
</script>

<style scoped lang="scss">
.detail-container {
  display: flex;
  flex: 1;
  height: 100%;
  flex-direction: column;
  margin: -16px -16px 0;

  .detail-container__breadcrumb,
  .detail-container__tab {
    background-color: #fff;
    padding: 0 24px;

    :deep(.el-tabs__header) {
      margin: 0;
    }
    :deep(.el-tabs__nav-wrap:after) {
      --el-border-color-light: transparent;
    }
  }

  .detail-container__breadcrumb {
    padding-top: 6px;
    padding-bottom: 4px;
  }

  .detail-container__content {
    margin: 16px 16px 0;
    flex: 1;
    border-radius: 8px;
    // 内部滚动条
    overflow-y: auto;
  }
}

// 在WorkLayout布局下的详情页样式
.work-detail {
  margin-top: 4px;
  height: calc(100% - 4px);
  .detail-container__breadcrumb {
    background-color: transparent !important;
    padding: 0 16px;
    margin-bottom: 8px;
  }

  .detail-container__tab {
    padding: 8px 0 0 20px;
    border-radius: 8px 8px 0 0;
    margin: 0 16px;
  }

  .detail-container__alert {
    margin: 0 16px;
    width: calc(100% - 32px);
    background-color: #fff;
  }

  .detail-container__content {
    flex: 1;
    padding: 0;
    margin-top: 0;
    overflow-y: visible;
    & > div {
      border-radius: 0 0 8px 8px;
    }
  }
}
</style>
