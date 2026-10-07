<template>
  <el-drawer
    title="选择数据目录"
    :size="600"
    :model-value="visible"
    :show-close="false"
    :body-class="'category-drawer_body'"
    @update:model-value="(val) => emit('update:visible', val)"
  >
    <template #header>
      <div class="category-drawer-header">
        <h3 class="title">选择数据目录</h3>
        <el-input
          v-model="searchQuery"
          placeholder="搜索目录"
          clearable
          style="width: 300px"
          @input="handleSearch"
        >
          <template #prefix>
            <Icon icon="el-icon-search"></Icon>
          </template>
        </el-input>
      </div>
    </template>

    <div class="category-drawer-content">
      <div class="category-drawer-search flex gap-2">
        <el-select
          v-model="appValue"
          placeholder="选择应用系统"
          clearable
          style="width: 180px"
          @change="handleSearch"
        >
          <el-option
            v-for="item in appSystemOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-tree-select
          v-model="orgValue"
          :data="orgOptions"
          :node-key="'value'"
          default-expand-all="true"
          check-strictly="true"
          placeholder="选择提供部门"
          clearable
          style="width: 180px"
          @change="handleSearch"
        ></el-tree-select>
        <el-select
          v-model="categoryTypeValue"
          placeholder="选择目录类型"
          clearable
          style="width: 180px"
          @change="handleSearch"
        >
          <el-option
            v-for="item in categoryTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="infinite-list-wrapper" style="overflow: auto">
        <ul v-infinite-scroll="load" class="list" :infinite-scroll-disabled="disabled">
          <li v-for="item in listData" :key="item.id" class="list-item">
            <div class="list-item-content">
              <div class="item-header">
                <el-checkbox
                  v-model="item.checked"
                  :class="{ checked: item.checked }"
                  @change="handleCheckboxChange(item)"
                ></el-checkbox>
                <Icon icon="category" class="ml-3 mr-1"></Icon>
                <span class="catalog-name">{{ item.catalogName }}</span>
              </div>
              <div class="item-resources">{{ item.resources }}</div>
            </div>
          </li>
        </ul>
        <p v-if="loading">加载中...</p>
        <p v-if="noMore">没有更多了</p>
      </div>
    </div>

    <template #footer>
      <div class="flex" style="justify-content: end">
        <el-button type="primary" @click="handleConfirm">
          <Icon icon="save" class="mr-2" />
          确定
        </el-button>
        <el-button :icon="'el-icon-Close'" @click="handleCancel">取消</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from "vue";
import { useDictStore } from "@/store";
import { ElMessage } from "element-plus";
// Props
const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  selectedData: {
    type: Array,
    default: () => [],
  },
  useTree: {
    type: Boolean,
    default: false,
  },
});

// Emits
const emit = defineEmits(["update:visible", "confirm", "close"]);

// Refs
const appValue = ref("");
const orgValue = ref("");
const categoryTypeValue = ref("");
const appSystemOptions = ref([
  { label: "电视剧管理系统", value: "1" },
  { label: "政务服务门户", value: "2" },
  { label: "政务服务运行管理系统", value: "3" },
  { label: "合作制作电视剧审批系统", value: "4" },
  { label: "接收设施进口证明核发系统", value: "5" },
]);
const categoryTypeOptions = ref([
  { label: "部门目录", value: "1" },
  { label: "主题目录", value: "2" },
]);
const orgOptions = useDictStore().getDictItems("org");
const searchQuery = ref("");
const selectedRows = ref([]);
const listData = ref([
  {
    id: "1",
    catalogName: "一般题材备案数据目录",
    resources: "ods_general_subject_data",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "general_subject_data",
    dataType: "1",
    authorityUnit: "国家广播电视总局 / 电视剧司",
    recordCount: "——",
    updateCycle: "——",
    latestUpdateTime: "——",
    latestBusinessTime: "——",
    relatedTask: "去接入>",
  },
  {
    id: "2",
    catalogName: "重大题材立项数据目录",
    resources: "ods_major_subject_data",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "major_subject_data",
    dataType: "2",
    authorityUnit: "国家广播电视总局 / 电视剧司",
    recordCount: 30000,
    updateCycle: "实时",
    latestUpdateTime: "2025-12-26 11:30:00",
    latestBusinessTime: "2025-12-26 10:20:00",
    relatedTask: "重大题材立项数据增量取",
  },
  {
    id: "3",
    catalogName: "完成剧申报数据目录",
    resources: "ods_completed_drama_data",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "completed_drama_data",
    dataType: "1",
    authorityUnit: "国家广播电视总局 / 电视剧司",
    recordCount: 5000,
    updateCycle: "每周",
    latestUpdateTime: "2025-12-24 08:00:00",
    latestBusinessTime: "2025-12-20 22:00:00",
    relatedTask: "完成剧申报据增量取",
  },
  {
    id: "4",
    catalogName: "完成剧审查结果数据目录",
    resources: "ods_completed_drama_review_data",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "log_radio_behavior",
    dataType: "1",
    authorityUnit: "国家广播电视总局 / 电视剧司",
    recordCount: 500000,
    updateCycle: "每日",
    latestUpdateTime: "2025-12-26 07:00:00",
    latestBusinessTime: "2025-12-25 23:45:00",
    relatedTask: "完成剧审查结果增量取",
  },
  {
    id: "5",
    catalogName: "剧目变更管理数据目录",
    resources: "ods_drama_change mana_data",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "drama_change mana_data",
    dataType: "1",
    authorityUnit: "国家广播电视总局 / 电视剧司",
    recordCount: 8000,
    updateCycle: "实时",
    latestUpdateTime: "2025-12-26 10:15:00",
    latestBusinessTime: "2025-12-26 09:50:00",
    relatedTask: "剧目变更管理数据增量取",
  },
  {
    id: "6",
    catalogName: "一般题材备案审核数据目录",
    resources: "ods_general_subject_review_data",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "general_subject_review_data",
    dataType: "1",
    authorityUnit: "国家广播电视总局 / 电视剧司",
    recordCount: 15000,
    updateCycle: "每日",
    latestUpdateTime: "2025-12-26 08:30:00",
    latestBusinessTime: "2025-12-25 22:30:00",
    relatedTask: "一般题材备案审核数全量取",
  },
  {
    id: "7",
    catalogName: "付费节目订阅信息",
    resources: "pay_program_sub",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "t_pay_sub",
    dataType: "2",
    authorityUnit: "国家广播电视总局 / 电视剧司",
    recordCount: 12000,
    updateCycle: "实时",
    latestUpdateTime: "2025-12-26 12:00:00",
    latestBusinessTime: "2025-12-26 11:40:00",
    relatedTask: "订阅信息增量取",
  },
  {
    id: "8",
    catalogName: "内容审核记录",
    resources: "ods_radio_content_audit",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "t_content_audit",
    dataType: "1",
    authorityUnit: "国家广播电视总局 / 电视剧",
    recordCount: 3000,
    updateCycle: "每周",
    latestUpdateTime: "2025-12-23 09:00:00",
    latestBusinessTime: "2025-12-20 18:00:00",
    relatedTask: "审核记录增量取",
  },
  {
    id: "9",
    catalogName: "用户注册管理数据目录",
    resources: "ods_user_register_data",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "user_register_data",
    dataType: "1",
    authorityUnit: "国家广播电视总局 / 电视剧",
    recordCount: 2000,
    updateCycle: "每日",
    latestUpdateTime: "2025-12-26 09:30:00",
    latestBusinessTime: "2025-12-25 21:10:00",
    relatedTask: "用户注册管理数据增量取",
  },
  {
    id: "10",
    catalogName: "节目编排数据",
    resources: "radio_channel_schedule",
    sourceAppSystem: "电视剧管理系统",
    sourceTableName: "t_channel_schedule",
    dataType: "1",
    authorityUnit: "国家广播电视总局 / 电视剧",
    recordCount: 1000,
    updateCycle: "每日",
    latestUpdateTime: "2025-12-26 07:30:00",
    latestBusinessTime: "2025-12-26 06:50:00",
    relatedTask: "节目编排数据全量取",
  },
]);
// const listData = ref([
//   {
//     id: 1,
//     catalogName: "广电用户基础信息",
//     resources: "广电用户核心表",
//     shareProperty: "1",
//     category: "用户管理",
//     sourceDept: "办公厅",
//     recordCount: 180000,
//     updateCycle: "每天",
//     registerTime: "2025-02-15 09:30:00",
//     updateTime: "2025-12-28 16:00:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 2,
//     catalogName: "广电节目播放日志数据",
//     resources: "节目播放行为表",
//     shareProperty: "2",
//     category: "内容运营",
//     sourceDept: "办公厅",
//     recordCount: 950000,
//     updateCycle: "实时",
//     registerTime: "2025-03-20 11:10:00",
//     updateTime: "2025-12-28 17:15:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 3,
//     catalogName: "广电付费套餐配置信息",
//     resources: "基础套餐表",
//     shareProperty: "1",
//     category: "产品管理",
//     sourceDept: "办公厅",
//     recordCount: 68,
//     updateCycle: "每周",
//     registerTime: "2025-01-25 14:20:00",
//     updateTime: "2025-12-28 14:40:00",
//     status: "1",
//     checked: false,
//   },
//   {
//     id: 4,
//     catalogName: "广电计费订单明细数据",
//     resources: "用户付费订单表",
//     shareProperty: "2",
//     category: "财务管理",
//     sourceDept: "规划财务司",
//     recordCount: 260000,
//     updateCycle: "每天",
//     registerTime: "2025-04-05 08:50:00",
//     updateTime: "2025-12-28 10:20:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 5,
//     catalogName: "广电终端设备基础信息",
//     resources: "机顶盒信息表",
//     shareProperty: "1",
//     category: "设备管理",
//     sourceDept: "公共服务司",
//     recordCount: 320000,
//     updateCycle: "每月",
//     registerTime: "2025-05-12 15:10:00",
//     updateTime: "2025-12-28 09:50:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 6,
//     catalogName: "广电用户欠费信息",
//     resources: "用户欠费明细表",
//     shareProperty: "2",
//     category: "财务管理",
//     sourceDept: "规划财务司",
//     recordCount: 45000,
//     updateCycle: "每天",
//     registerTime: "2025-06-18 13:30:00",
//     updateTime: "2025-12-28 11:45:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 7,
//     catalogName: "广电高清节目资源信息",
//     resources: "高清节目表",
//     shareProperty: "1",
//     category: "内容运营",
//     sourceDept: "网络视听节目管理司",
//     recordCount: 12000,
//     updateCycle: "每周",
//     registerTime: "2025-07-22 10:15:00",
//     updateTime: "2025-12-28 16:30:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 8,
//     catalogName: "广电广告投放数据",
//     resources: "广告投放计划表",
//     shareProperty: "2",
//     category: "广告运营",
//     sourceDept: "媒体融合发展司",
//     recordCount: 89000,
//     updateCycle: "每天",
//     registerTime: "2025-08-05 14:50:00",
//     updateTime: "2025-12-28 13:20:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 9,
//     catalogName: "广电用户节目订阅信息",
//     resources: "节目订阅关系表",
//     shareProperty: "2",
//     category: "用户管理",
//     sourceDept: "电视剧司",
//     recordCount: 76000,
//     updateCycle: "实时",
//     registerTime: "2025-09-10 09:20:00",
//     updateTime: "2025-12-28 15:10:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 10,
//     catalogName: "广电终端故障维修记录",
//     resources: "故障申报记录表",
//     shareProperty: "2",
//     category: "设备管理",
//     sourceDept: "安全传输保障司",
//     recordCount: 32000,
//     updateCycle: "每天",
//     registerTime: "2025-10-15 11:30:00",
//     updateTime: "2025-12-28 12:40:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 11,
//     catalogName: "广电广播节目频率信息",
//     resources: "广播频率配置表",
//     shareProperty: "1",
//     category: "内容运营",
//     sourceDept: "科技司",
//     recordCount: 156,
//     updateCycle: "每月",
//     registerTime: "2025-11-20 16:40:00",
//     updateTime: "2025-12-28 08:30:00",
//     status: "1",
//     checked: false,
//   },
//   {
//     id: 12,
//     catalogName: "广电用户流失预警数据",
//     resources: "用户流失风险表",
//     shareProperty: "2",
//     category: "用户管理",
//     sourceDept: "公共服务司",
//     recordCount: 28000,
//     updateCycle: "每周",
//     registerTime: "2025-12-01 13:10:00",
//     updateTime: "2025-12-28 14:15:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 13,
//     catalogName: "广电网络带宽使用数据",
//     resources: "用户带宽配置表",
//     shareProperty: "2",
//     category: "技术管理",
//     sourceDept: "数据提供部门",
//     recordCount: 190000,
//     updateCycle: "实时",
//     registerTime: "2025-02-28 10:50:00",
//     updateTime: "2025-12-28 17:30:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 14,
//     catalogName: "广电月度经营汇总数据",
//     resources: "经营收入统计表",
//     shareProperty: "2",
//     category: "经营管理",
//     sourceDept: "数据提供部门",
//     recordCount: 120,
//     updateCycle: "每月",
//     registerTime: "2025-03-05 15:20:00",
//     updateTime: "2025-12-28 09:10:00",
//     status: "0",
//     checked: false,
//   },
//   {
//     id: 15,
//     catalogName: "广电公益节目投放记录",
//     resources: "公益节目计划表",
//     shareProperty: "1",
//     category: "内容运营",
//     sourceDept: "数据提供部门",
//     recordCount: 480,
//     updateCycle: "每周",
//     registerTime: "2025-04-12 14:30:00",
//     updateTime: "2025-12-28 11:15:00",
//     status: "0",
//     checked: false,
//   },
// ]);
const loading = ref(false);
const noMore = computed(() => listData.value.length >= 20);
const disabled = computed(() => loading.value || noMore.value);
const load = () => {
  // loading.value = true;
  // setTimeout(() => {
  //   count.value += 2;
  //   loading.value = false;
  // }, 2000);
};

// Watch
watch(
  () => props.visible,
  (newVal) => {
    if (newVal) {
      // Reset search when drawer opens
      searchQuery.value = "";
      categoryTypeValue.value = "";
      appValue.value = "";
      orgValue.value = "";
      // Set default selection if provided
      updateCheckedStatus();
    }
  }
);

watch(
  () => props.selectedData,
  () => {
    updateCheckedStatus();
  },
  { deep: true }
);

// Methods
const handleSearch = () => {
  // Search logic is handled by computed property
};

const handleConfirm = () => {
  if (!selectedRows.value.length) {
    ElMessage.warning("请选择数据目录");
    return;
  }
  emit("confirm", selectedRows.value);
  emit("update:visible", false);
};

const handleCancel = () => {
  emit("update:visible", false);
  emit("close");
};

// Methods
const handleCheckboxChange = (item) => {
  if (item.checked) {
    // 添加到选中列表
    if (!selectedRows.value.find((row) => row.id === item.id)) {
      selectedRows.value.push(item);
    }
  } else {
    // 从选中列表移除
    selectedRows.value = selectedRows.value.filter((row) => row.id !== item.id);
  }
};

const updateCheckedStatus = () => {
  selectedRows.value = props.selectedData;
  listData.value.forEach((item) => {
    item.checked = props.selectedData.some((selected) => selected.id === item.id);
  });
};
onMounted(() => {
  // 设置默认选中
  updateCheckedStatus();
});
</script>

<style lang="scss" scoped>
// :deep(.category-drawer_body) {
//   padding: 0 !important;
// }
.category-drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  .title {
    font-size: 16px;
    font-weight: 600;
    margin: 0;
  }
}
.category-drawer-content {
  height: 100%;
  .infinite-list-wrapper {
    margin-top: 15px;
    height: calc(100% - 32px);
  }
  .list {
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .list-item {
    padding: 12px 10px;
    border-top: 1px solid #e4e7ed;
  }

  // .list-item:first-child {
  //   border-top: none;
  // }

  .list-item-content {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  .item-header {
    display: flex;
    align-items: center;
  }

  .item-icon {
    color: #409eff;
    font-size: 16px;
  }

  .catalog-name {
    font-size: 14px;
    font-weight: 500;
    color: rgba(0, 0, 0, 0.88);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .item-resources {
    font-size: 14px;
    color: rgba(0, 0, 0, 0.45);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    padding-left: 25px;
  }

  .checked {
    color: #409eff;
  }
}
</style>
