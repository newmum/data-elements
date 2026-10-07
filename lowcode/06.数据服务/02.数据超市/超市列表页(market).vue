<template>
  <div class="market images-market-bg">
    <div class="market__top">
      <h1 class="market__title">数据超市</h1>
      <div class="market__search">
        <!-- <u-tabs
          v-model="state.activeTop"
          class="market__tabs"
          :tabs="tabs"
          @change="handleAction('change-top')"
        ></u-tabs> -->
        <div class="market__search-box">
          <el-input
            v-model="state.keyword"
            class="market__search-input"
            placeholder="请输入数据目录关键词"
            size="large"
            clearable
          ></el-input>
          <el-button type="primary" icon="search" @click="handleAction('search')">搜索</el-button>
        </div>
      </div>
      <!-- <el-button type="primary" plain class="market__apply-btn">
        <template #icon>
          <Icon icon="apply" />
        </template>
        需求申请
      </el-button> -->
    </div>

    <div class="market__body">
      <div class="market__sidebar">
        <!--    侧边页签    -->
        <div class="sidebar-header">
          <template v-if="sidebarItems.length === 2">
            <div
              class="two-nav"
              :class="{ 'img-back': state.activeSidebar !== sidebarItems[0].title }"
            >
              <div
                v-for="(item, ind) in sidebarItems"
                :key="item.title"
                class="nav-title"
                :class="{
                  active: state.activeSidebar === item.title,
                  'left-nav': ind === 0,
                  'right-nav': ind === 1,
                }"
                @click="handleAction('change-left-tab', { title: item.title, ind })"
              >
                {{ item.title }}
              </div>
            </div>
          </template>

          <h3 v-else class="nav-title">
            <Icon icon="el-icon-menu" class="mr-2" color="var(--el-color-primary)" :size="22" />
            {{ sidebarItems.length > 0 ? sidebarItems[0].title : "分类导航" }}
          </h3>
        </div>

        <!--    侧边树    -->
        <div class="sidebar-card__list">
          <template v-for="(tree, ind) in sidebarItems" :key="state.activeTop + tree.title">
            <u-tree
              v-show="state.activeSidebar === tree.title"
              ref="treeRef"
              search
              search-placeholder="请输入搜索关键词"
              :data="tree.options"
              :auto-expand-first-node="false"
              :expand-on-click-node="false"
              :default-props="{ label: 'label' }"
              node-key="value"
              :current-node-key="state.valueSidebar"
              @node-click="(node) => handleAction('click-left', node)"
            >
              <template #node="{ node }">
                <u-tree-node
                  :label="node.label"
                  :keyword="treeRef ? treeRef[ind]?.searchValue : ''"
                >
                  <template #count>
                    {{ get(state.countMap, `${tree.es.field}.${node.data.value}`) ?? 0 }}
                  </template>
                </u-tree-node>
              </template>
            </u-tree>
          </template>
        </div>
      </div>

      <div class="market__content">
        <check-card
          v-model="state.valueRight"
          :filters="rightItems"
          @change="handleAction('change-tag')"
        />

        <div class="result-card">
          <div class="result-card__header">
            <div class="result-count">
              搜索结果
              <span>{{ state.total }}</span>
              项
              <i>
                (用时
                <span>{{ state.sTime }}</span>
                秒)
              </i>
            </div>
            <div class="sort-options">
              <template v-for="item in sorts" :key="item.title">
                <div
                  class="sort-item"
                  :class="{ active: state.sortField === item.field }"
                  @click="handleAction('change-sort', item)"
                >
                  {{ item.title }}
                  <Icon
                    class="sort-icon icon-top"
                    icon="el-icon-CaretTop"
                    :class="{
                      active: state.sortField === item.field && state.sortOrder === 'asc',
                    }"
                  />
                  <Icon
                    icon="el-icon-CaretBottom"
                    class="sort-icon icon-bottom"
                    :class="{
                      active: state.sortField === item.field && state.sortOrder === 'desc',
                    }"
                  />
                </div>
              </template>
            </div>
          </div>
          <div class="result-card__body">
            <market-list
              :loading="state.loading"
              :filters="{}"
              :filter-alias="{}"
              :display-fields="displayFields"
              :control="currentConfig.control"
              :data-list="state.list"
              @detail="handleAction('detail', $event)"
              @collect="handleAction('collect', $event)"
              @apply="(item, e) => handleAction('apply', item, e)"
            />

            <div v-if="state.total !== state.list.length" class="pagination">
              <ElPagination
                v-model:current-page="state.pageNo"
                v-model:page-size="state.pageSize"
                :total="state.total"
                :background="true"
                layout="prev, pager, next, sizes"
                @current-change="handleAction('change-page')"
                @size-change="handleAction('change-page')"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, watch, onActivated } from "vue";
import { useDictStore } from "@/store";
import { get, cloneDeep, isEmpty, isObject, isString } from "lodash-es";
import { genEsQueryField } from "@/utils";
import { useRouter } from "vue-router";
import { useRegisterModal } from "@/composables";
import dayjs from "dayjs";

const { open } = useRegisterModal();
const router = useRouter();
const treeRef = ref();
const dictDict = useDictStore();
const state = reactive({
  // 顶部
  activeTop: "", // 索引页签
  keyword: "", // 搜索关键字

  // 侧边
  activeSidebar: "", // 侧边页签
  sidebarKeyword: "", // 侧边搜索关键字
  valueSidebar: "", // 侧边选中值

  // 右侧
  valueRight: {}, // 右侧选中值

  // 排序
  sortField: "",
  sortOrder: "desc",

  // 列表、分页
  list: [],
  total: 0,
  loading: false,
  sTime: 0,
  pageNo: 1,
  pageSize: 20,

  // 部门筛选出的业务系统
  apps: [],

  // 申请单
  open: false,
  registerData: {},

  // 聚合统计 { field: { key: count } }
  countMap: {} as Record<string, Record<string, number>>,
});
const leftValue = computed(() => {
  return state.valueSidebar ? [state.valueSidebar] : [];
});

// 当前索引配置
const currentConfig = ref({});
// 索引页签
const tabs = computed(() => {
  return config.search.map((el) => el.title).map((el) => ({ label: el, value: el }));
});
// 侧边筛选树
const sidebarItems = computed(() => {
  return (
    currentConfig.value.filter?.map((el) => ({
      ...el,
      options: isString(el.options) ? dictDict.getDictItems(el.options) : el.options,
    })) || []
  );
});
// 右侧筛选标签
const rightItems = ref([]);

// 排序
const sorts = computed(() => {
  return currentConfig.value.sort;
});
// 列表展示字段
const displayFields = computed(() => {
  return currentConfig.value.displayFields;
});

onMounted(() => {
  state.activeTop = config.search[0].title;
  handleAction("init");
});

onActivated(() => {
  handleSearch();
});

// 监听关闭登记弹框
watch(open, (newVal, oldVal) => {
  if (newVal === false && oldVal) {
    handleAction("search");
  }
});

const handleAction = (type: string, item?: any, item2?: any) => {
  switch (type) {
    case "init": {
      // 确定索引配置
      currentConfig.value = config.search.find((el) => el.title === state.activeTop);
      // 初始化排序
      const { field = "updatedTime", order = "desc" } =
        sorts.value.length > 0 ? sorts.value[0] : {};
      state.sortField = field;
      state.sortOrder = order;
      // 设置左侧激活页签
      state.activeSidebar = sidebarItems.value.length > 0 ? sidebarItems.value[0].title : "";
      // 清空筛选值
      state.valueSidebar = "";
      state.valueRight = {};
      state.sTime = 0;
      state.countMap = {};

      loadFilters();
      handleAction("search");
      break;
    }
    case "change-top":
      handleAction("init");
      break;
    case "change-left-tab":
      state.activeSidebar = item.title;
      state.valueSidebar = "";
      loadFilters();
      break;
    case "change-sort": {
      // 更新排序
      const { field, order = "desc" } = item;
      if (state.sortField === field) {
        // 切换排序方向
        state.sortOrder = state.sortOrder === "asc" ? "desc" : "asc";
      } else {
        // 切换列，默认降序
        state.sortField = field;
        state.sortOrder = order;
      }
      handleAction("search");
      break;
    }
    case "click-left":
      if (state.valueSidebar !== item.value) {
        state.valueSidebar = item.value;
      } else {
        state.valueSidebar = undefined;
      }
      // 左右筛选联动
      loadFilters();
    case "change-page":
    case "change-tag":
      handleAction("search");
      break;
    case "search":
      handleSearch();
      break;

    // 列表项操作
    case "apply":
      $common
        .post("/dws/market/shoppingCard", {
          catalogId: item.tid,
        })
        .then((res) => {
          handleAction("search");
          $common.addToCart(item2);
        });
      break;
    case "collect":
      $common
        .post("/dws/market/collect", {
          catalogId: item.tid,
        })
        .then((res) => {
          item.collected = !item.collected;
          $message.success(res ? "收藏成功" : "取消收藏");
        });
      break;
    case "detail":
      router.push({
        path: "detail",
        query: { id: item.tid, type: item.assetType, title: item.catalogName },
      });
      break;
  }
};
// 设置点击部门节点时，筛选右侧业务系统
const refreshApp = async () => {
  await $common.post("/dst/catalog/list", { assetType: "app" }).then((data) => {
    state.apps = data.map((el) => ({ label: el.appName, value: el.tid, orgId: el.orgId }));
  });
};

// 加载右侧筛选
const loadFilters = async () => {
  const list = currentConfig.value.filter2?.map((el) => {
    const obj = {
      ...el,
      options: isString(el.options) ? dictDict.getDictItems(el.options) : el.options,
    };
    return obj;
  });

  // 部门筛选业务系统
  const ind = list.findIndex((el) => el.title === "业务系统");
  const flag = sidebarItems.value.find((el) => el.title === state.activeSidebar)?.flag;
  if (ind > -1) {
    !state.apps?.length && (await refreshApp());
    if (["org"].includes(flag) && !!state.valueSidebar) {
      list[ind].options = state.apps.filter((el) => el.orgId === state.valueSidebar);
    } else {
      list[ind].options = state.apps;
    }

    // 取消不存在于 新业务系统列表中的选择(考虑单选情况)
    if (state.valueRight["业务系统"]?.length === 1) {
      const appId = state.valueRight["业务系统"][0];
      const indApp = list[ind].options.find((el) => el.value === appId);
      if (!indApp) {
        state.valueRight["业务系统"] = [];
      }
    }
  }

  rightItems.value = list;
};

// 获取es查询语句
const getQueryParam = () => {
  const valueKeys = Object.keys(state.valueRight);

  if (valueKeys.length === 0) return [];

  const queryParams: {
    [key: string]: {
      [k: string]: any;
    };
  }[] = [];

  // 左侧树筛选
  if (leftValue.value.length > 0) {
    const section = currentConfig.value.filter.find(
      (section) => section.title === state.activeSidebar
    );

    // 获取es查询字段对象
    const { query, field } = section?.es || {};
    const obj = genEsQueryField(query, field, leftValue.value);
    queryParams.push(obj);
  }

  // 右侧筛选
  for (const valueKey of valueKeys) {
    // 找到字段的配置section
    const section = currentConfig.value.filter2.find((section) => section.title === valueKey);
    const esValue = state.valueRight[valueKey];
    if (!isObject(section) || esValue.length === 0) {
      continue;
    }

    // 获取es查询字段对象
    const { query, field } = section?.es || {};
    const obj = genEsQueryField(query, field, esValue);
    queryParams.push(obj);
  }

  return queryParams;
};

const handleSearch = async () => {
  state.loading = true;
  const startTime = performance.now();

  try {
    // 1、提取搜索关键词参数
    const kParam = state.keyword
      ? {
          multi_match: {
            query: state.keyword,
            fields: currentConfig.value.searchFields,
          },
        }
      : undefined;

    // 2、提取筛选条件参数
    const queryParams = getQueryParam() || {};

    // 获取列表固定参数
    const querys = [...currentConfig.value.params];
    !isEmpty(kParam) && querys.push(kParam);
    !isEmpty(queryParams) && querys.push(...queryParams);

    querys.forEach((el) => {
      if (el["range"]) {
        const fieldName = Object.keys(el["range"])[0];
        if (el["range"][fieldName]["gte"]) {
          el["range"][fieldName]["gte"] = $common.parseEsDate(el["range"][fieldName]["gte"]);
        }
        if (el["range"][fieldName]["lte"]) {
          el["range"][fieldName]["lte"] = $common.parseEsDate(el["range"][fieldName]["lte"]);
        }
      }
    });

    if (state.sTime === 0) {
      // 只加载一次
      handleCount(querys);
    }

    // 执行查询
    await list(currentConfig.value.indexType, {
      pageNum: state.pageNo ?? 1,
      pageSize: state.pageSize ?? 20,
      query: querys,
      sortField: state.sortField,
      sortDir: state.sortOrder,
    }).then((res) => {
      state.list = res.list || [];
      state.total = res.total;
    });
    return {
      list: state.list,
      total: state.total,
    };
  } finally {
    const endTime = performance.now();
    state.sTime = Number(((endTime - startTime) * 0.001).toFixed(3));
    state.loading = false;
  }
};

/**
 * 列表查询
 * @param indexType
 * @param param
 */
const list = async (indexType, param: any) => {
  return $common.post("/dws/market/list", param);
};

const handleCount = async (query: any) => {
  const res = await $common.post("/dws/market/count", {
    query: [...currentConfig.value.params],
    aggs: currentConfig.value?.aggs,
  });
  state.countMap = res;
};

const orgs = cloneDeep($dict.getDictItems("org"));

$common.deepTree(orgs, (node) => {
  node.value = node.serialNumber;
});

// 页面配置
const config = {
  // 页面标题
  title: "数据超市",
  // 是否只统计一次
  onceCount: true,
  // 页面筛选配置
  search: [
    {
      title: "部门目录",
      /* 固定传参 */
      params: [{ term: { publishStatus: 1 } }, { term: { assetType: "catalog" } }],
      /* 返回字段选择 */
      sourceFields: [],
      /* 用于全文搜索字段，可设置权重 */
      searchFields: ["catalogName^3", "catalogNameEn^2"],
      /* 排序，order为默认排序 */
      sort: [
        {
          title: "准入时间",
          field: "createdTime",
          order: "desc",
        },
        {
          title: "更新时间",
          field: "updatedTime",
          order: "asc",
        },
        {
          title: "数据量",
          field: "recordCount",
          order: "asc",
        },
      ],
      /* 左侧树筛选条件，筛选项包含组件类型、字段、选项值、es查询方法、图标配置 */
      filter: [
        {
          type: "tree",
          title: "行政处室",
          options: orgs[0]?.children,
          flag: "org", // 标识为部门，则会触发筛选业务系统
          es: {
            query: "terms",
            field: "orgPath",
          },
        },
        {
          type: "tree",
          title: "事业单位",
          options: orgs[1]?.children,
          flag: "org",
          es: {
            query: "terms",
            field: "orgPath",
          },
        },
      ],
      /* 右侧上方筛选条件 */
      filter2: [
        {
          type: "radio",
          title: "业务系统",
          options: "app",
          es: {
            query: "terms",
            field: "appId",
          },
        },
        {
          type: "radio",
          title: "共享类型",
          options: "shareType",
          es: {
            query: "terms",
            field: "shareType",
          },
        },
        {
          type: "radio",
          title: "加工程度",
          options: "dataProcessLevel",
          es: {
            query: "terms",
            field: "dataProcessLevel",
          },
        },
        {
          type: "radio",
          title: "发布时间",
          options: [
            {
              label: "最近十天",
              value: {
                gte: "now-10d",
                lte: "now",
              },
            },
            {
              label: "近一个月",
              value: {
                gte: "now-1M",
                lte: "now",
              },
            },
            {
              label: "近三个月",
              value: {
                gte: "now-3M",
                lte: "now",
              },
            },
            {
              label: "近六个月",
              value: {
                gte: "now-6M",
                lte: "now",
              },
            },
          ],
          es: {
            query: "range",
            field: "publishTime",
          },
        },
      ],
      /* 聚合统计 */
      aggs: {
        orgPath: {
          terms: {
            field: "orgPath",
            size: 10000,
          },
        },
      },
      /* 列表配置 */
      control: {
        showApply: "申请",
        showCollect: true,
      },
      /* 列表显示字段 */
      displayFields: [
        {
          title: "数源单位",
          field: "orgId",
          options: "org",
        },
        {
          title: "共享属性",
          field: "shareType",
          options: "shareType",
        },
        {
          title: "访问量",
          field: "viewCount",
        },
        {
          title: "更新周期",
          field: "updateCycle",
          options: "updateCycle",
        },
        {
          title: "业务系统",
          field: "appId",
          options: "app",
        },
        {
          title: "注册时间",
          field: "createdTime",
        },
      ],
    },
    {
      title: "主题目录",
      /* 固定传参 */
      params: [{ term: { publishStatus: 1 } }, { term: { assetType: "catalog" } }],
      /* 返回字段选择 */
      sourceFields: [],
      /* 用于全文搜索字段，可设置权重 */
      searchFields: ["catalogName^3", "catalogNameEn^2"],
      /* 排序，order为默认排序 */
      sort: [
        {
          title: "准入时间",
          field: "createdTime",
          order: "desc",
        },
        {
          title: "更新时间",
          field: "updatedTime",
          order: "asc",
        },
        {
          title: "数据量",
          field: "recordCount",
          order: "asc",
        },
      ],
      /* 左侧树筛选条件，筛选项包含组件类型、字段、选项值、es查询方法、图标配置 */
      filter: [
        {
          type: "tree",
          title: "警种",
          options: "policeType",
          es: {
            query: "terms",
            field: "policeType",
          },
        },
        {
          type: "tree",
          title: "部门",
          options: orgs,
          flag: "org",
          es: {
            query: "terms",
            field: "orgPath",
          },
        },
      ],
      /* 右侧上方筛选条件 */
      filter2: [
        {
          type: "radio",
          title: "业务系统",
          options: "app",
          es: {
            query: "terms",
            field: "appId",
          },
        },
        {
          type: "radio",
          title: "共享类型",
          options: "shareType",
          es: {
            query: "terms",
            field: "shareType",
          },
        },
        {
          type: "radio",
          title: "加工程度",
          options: "dataProcessLevel",
          es: {
            query: "terms",
            field: "dataProcessLevel",
          },
        },
        {
          type: "radio",
          title: "发布时间",
          options: [
            {
              label: "最近十天",
              value: {
                gte: "now-10d",
                lte: "now",
              },
            },
            {
              label: "近一个月",
              value: {
                gte: "now-1M",
                lte: "now",
              },
            },
            {
              label: "近三个月",
              value: {
                gte: "now-3M",
                lte: "now",
              },
            },
            {
              label: "近六个月",
              value: {
                gte: "now-6M",
                lte: "now",
              },
            },
          ],
          es: {
            query: "range",
            field: "publishTime",
          },
        },
      ],
      /* 聚合统计 */
      aggs: {
        orgPath: {
          terms: {
            field: "orgPath",
            size: 10000,
          },
        },
        policeType: {
          terms: {
            field: "policeType",
            size: 10000,
          },
        },
      },
      /* 列表配置 */
      control: {
        showApply: "申请",
        showCollect: true,
      },
      /* 列表显示字段 */
      displayFields: [
        {
          title: "数源单位",
          field: "orgId",
          options: "org",
        },
        {
          title: "共享属性",
          field: "shareType",
          options: "shareType",
        },
        {
          title: "访问量",
          field: "viewCount",
        },
        {
          title: "更新周期",
          field: "updateCycle",
          options: "updateCycle",
        },
        {
          title: "业务系统",
          field: "appId",
          options: "app",
        },
        {
          title: "注册时间",
          field: "createdTime",
        },
      ],
    },
    {
      title: "服务目录",
      /* 固定传参 */
      params: [{ term: { publishStatus: 1 } }, { term: { assetType: "catalog" } }],
      /* 返回字段选择 */
      sourceFields: [],
      /* 用于全文搜索字段，可设置权重 */
      searchFields: ["catalogName^3", "catalogNameEn^2"],
      /* 排序，order为默认排序 */
      sort: [
        {
          title: "准入时间",
          field: "createdTime",
          order: "desc",
        },
        {
          title: "更新时间",
          field: "updatedTime",
          order: "asc",
        },
        {
          title: "数据量",
          field: "recordCount",
          order: "asc",
        },
      ],
      /* 左侧树筛选条件，筛选项包含组件类型、字段、选项值、es查询方法、图标配置 */
      filter: [
        {
          type: "tree",
          title: "行政处室",
          options: orgs[0]?.children,
          flag: "org", // 标识为部门，则会触发筛选业务系统
          es: {
            query: "terms",
            field: "orgPath",
          },
        },
        {
          type: "tree",
          title: "事业单位",
          options: orgs[1]?.children,
          flag: "org",
          es: {
            query: "terms",
            field: "orgPath",
          },
        },
      ],
      /* 右侧上方筛选条件 */
      filter2: [
        {
          type: "radio",
          title: "业务系统",
          options: "app",
          es: {
            query: "terms",
            field: "appId",
          },
        },
        {
          type: "radio",
          title: "共享类型",
          options: "shareType",
          es: {
            query: "terms",
            field: "shareType",
          },
        },
        {
          type: "radio",
          title: "加工程度",
          options: "dataProcessLevel",
          es: {
            query: "terms",
            field: "dataProcessLevel",
          },
        },
        {
          type: "radio",
          title: "发布时间",
          options: [
            {
              label: "最近十天",
              value: {
                gte: "now-10d",
                lte: "now",
              },
            },
            {
              label: "近一个月",
              value: {
                gte: "now-1M",
                lte: "now",
              },
            },
            {
              label: "近三个月",
              value: {
                gte: "now-3M",
                lte: "now",
              },
            },
            {
              label: "近六个月",
              value: {
                gte: "now-6M",
                lte: "now",
              },
            },
          ],
          es: {
            query: "range",
            field: "publishTime",
          },
        },
      ],
      /* 聚合统计 */
      aggs: {
        orgPath: {
          terms: {
            field: "orgPath",
            size: 10000,
          },
        },
      },
      /* 列表配置 */
      control: {
        showApply: "申请",
        showCollect: true,
      },
      /* 列表显示字段 */
      displayFields: [
        {
          title: "数源单位",
          field: "orgId",
          options: "org",
        },
        {
          title: "共享属性",
          field: "shareType",
          options: "shareType",
        },
        {
          title: "访问量",
          field: "viewCount",
        },
        {
          title: "更新周期",
          field: "updateCycle",
          options: "updateCycle",
        },
        {
          title: "业务系统",
          field: "appId",
          options: "app",
        },
        {
          title: "注册时间",
          field: "createdTime",
        },
      ],
    },
  ],
};
</script>

<style scoped lang="scss">
.market {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  min-height: 100%;
  padding-bottom: 16px;
  padding-top: 88px;
  background-color: var(--el-bg-color-page);
  background-size: 100% auto;
}

.market__top {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  margin-bottom: 46px;

  .market__title {
    margin: 0;
    font-family:
      Source Han Sans CN,
      sans-serif !important;
    font-weight: 600;
    font-size: 32px;
    height: 46px;
    letter-spacing: 4px;
  }

  .market__search {
    display: flex;
    flex-direction: column;
    gap: 16px;

    &-box {
      display: flex;
      align-items: center;

      .market__search-input {
        flex: 0 0 613px;
        width: 613px;
        height: 48px;

        :deep(.el-input__wrapper) {
          --el-input-border-radius: 8px 0 0 8px;
          border: none;
          box-shadow: none;
          font-size: 16px;
        }
      }

      :deep(.el-button) {
        width: 120px;
        height: 48px;
        font-size: 16px;
        border-radius: 0 8px 8px 0;
      }
    }
  }

  .market__apply-btn {
    width: 120;
    height: 48px;
    border-radius: 8px;
    font-size: 16px;
  }

  .market__tabs {
    :deep(.el-tabs__nav) {
      .el-tabs__item {
        padding: 0 26px;
        font-size: 16px;
        letter-spacing: 1px;
        font-family: Source Han Sans SC Bold;
        font-weight: 550;
        &.is-active {
          font-weight: 800;
        }
      }
      .el-tabs__item:nth-child(2) {
        padding-left: 0;
      }
    }
    :deep(.el-tabs__active-bar) {
      height: 3px;
    }
    :deep(.el-tabs__nav-wrap),
    :deep(.el-tabs__nav-scroll) {
      overflow: visible;
    }
    :deep(.el-tabs__active-bar)::after {
      position: absolute;
      top: 100%;
      left: 50%;
      width: 7px;
      aspect-ratio: 1 / 1;
      margin-inline: auto;
      content: "";
      background-color: var(--el-color-primary);
      clip-path: polygon(0% 0%, 100% 0%, 0% 100%);
      transform: translateY(calc(7px / -2)) rotate(225deg);
      transform-origin: 50%;
    }
  }
}

.market__body {
  display: grid;
  grid-template-columns: 320px 1022px;
  gap: 24px;
}

.market__sidebar {
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px #00000014;

  .sidebar-header {
    color: #1b1e26;
    margin-bottom: 10px;

    h3 {
      margin-top: 20px !important;
      margin-left: 20px !important;
    }
    .nav-title {
      display: flex;
      align-items: center;
      margin-left: 8px;
      font-family: Source Han Sans SC Bold;
      font-size: 18px;
      font-weight: 700;
      text-align: left;
    }

    .img-back.two-nav::before {
      transform: scaleX(-1); /* 水平镜像 */
    }

    .two-nav {
      position: relative;
      height: 58px;
      &::before {
        position: absolute;
        top: 0;
        left: 0;
        z-index: 1;
        width: 100%;
        height: 100%;
        content: "";
        background-image: var(--images-market-tab);
        transition: all 0.2s;
      }
    }

    .left-nav,
    .right-nav {
      position: absolute;
      z-index: 1;
      justify-content: center;
      width: 50%;
      height: 100%;
      cursor: pointer;
      color: #454750;
    }
    .left-nav {
      left: 0;
    }

    .right-nav {
      left: 50%;
    }

    .active {
      color: var(--el-color-primary);
    }
  }
  .sidebar-card__search {
    padding: 0 16px 16px;
  }
  .sidebar-card__list {
    min-height: 444px;
    padding: 0 16px;

    :deep(.el-tree) {
      --el-tree-node-content-height: 100%;
      --tree-node-checked-color: var(--el-color-primary);

      .el-tree-node__content > .el-tree-node__expand-icon {
        --el-tree-expand-icon-color: #000000e0;
        font-size: 16px;
        padding: 0;
        margin-top: 2px;
        margin-right: 4px;
        width: 24px;
      }

      .el-icon.is-leaf {
        width: 10px;
      }

      .el-tree-node__content {
        padding: 6px 0;
        margin-bottom: 5px;
        border-radius: 6px;
        align-items: flex-start;
        color: #000000e0;

        &:hover {
          color: var(--el-color-primary);
          .el-tree-node__expand-icon {
            color: var(--el-color-primary);
          }
        }
      }
    }

    :deep(.node-title) {
      gap: 16px;
      align-items: flex-start;
      justify-content: space-between;
    }
  }
}

.result-card {
  padding: 16px 24px 24px 24px;
  margin-top: 24px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px #00000014;

  .result-card__header {
    padding-bottom: 16px;
    padding-left: 7px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #edf0f4;
    line-height: 20px;

    .result-count {
      font-size: 14px;
      color: #323643;
      & > span {
        color: #0090ff;
        padding: 0 3px;
      }
      i {
        font-style: normal;
        color: rgb(147, 153, 167);
        margin-left: 5px;
      }
    }

    .sort-options {
      display: flex;
      gap: 16px;

      .sort-item {
        cursor: pointer;
        display: flex;
        align-items: center;
        position: relative;
        margin-right: 15px;
        font-size: 14px;
        font-weight: 400;
        color: #686c80;

        &.active {
          color: var(--el-color-primary);
        }

        .sort-icon {
          position: absolute;
          right: -18px;
          cursor: pointer;
          color: #c1c5d0;
          &.active {
            color: var(--el-color-primary);
          }
        }
        .icon-top {
          top: -2px;
        }
        .icon-bottom {
          top: 5px;
        }
      }
    }
  }

  .result-card__header .left .time {
    margin-left: 8px;
    color: var(--el-text-color-secondary);
  }

  &__body {
    .pagination {
      text-align: center;
      margin-top: 24px;
      background: #fff;
      padding: 16px;
      border-radius: 8px;
      display: flex;
      justify-content: center;
      :deep(.el-pagination).is-background {
        .el-pager li.is-active {
          background-color: transparent;
          border-radius: 6px;
          border: 1px solid var(--el-color-primary);
          color: var(--el-color-primary);
        }
        .btn-prev:disabled {
          background-color: transparent;
        }
        .btn-next:disabled {
          background-color: transparent;
        }
        .el-select__wrapper {
          width: 100px;
        }
      }
    }
  }
}
</style>
