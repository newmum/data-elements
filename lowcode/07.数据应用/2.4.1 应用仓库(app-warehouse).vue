<template>
  <div class="app-warehouse card-container flex flex-col px-5 pt-5 pb-2">
    <!-- 头部 -->
    <div class="header mb-5 flex-x-between">
      <el-segmented v-model="activeTab" :options="tabConfig" @change="handleTabChange" />

      <div class="flex gap-2">
        <el-input
          v-model="searchText"
          placeholder="搜索应用名称或者ID"
          class="search-input"
          clearable
          @input="handleSearch"
        >
          <template #prefix>
            <Icon icon="el-icon-search"></Icon>
          </template>
        </el-input>

        <el-button type="primary" @click="openCreateModal">
          <Icon icon="el-icon-Plus" class="mr-2"></Icon>
          新建应用
        </el-button>
      </div>
    </div>

    <!-- 中间内容区 -->
    <div class="content">
      <!-- 骨架屏 - 数据加载中显示 -->
      <div v-if="loading" class="todo-cards">
        <warehouse-card v-for="i in 12" :key="i" :loading="true"></warehouse-card>
      </div>

      <!-- 实际卡片内容 - 数据加载完成后显示 -->
      <div v-else-if="todoItems.length > 0" class="todo-cards">
        <warehouse-card
          v-for="item in todoItems"
          :key="item.tid"
          :item="item"
          class="todo-card"
          @operation-click="handleOperation"
        ></warehouse-card>
      </div>

      <!-- 空状态 - 数据加载完成但无数据时显示 -->
      <div v-else class="empty-state">
        <el-empty description="暂无数据"></el-empty>
      </div>
    </div>

    <!-- 底部分页 -->
    <div class="footer">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 36]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
    <create-app-modal
      v-if="createVisible"
      v-model="createVisible"
      mode="create"
      @submit="createApp"
      @close="handleModalClose"
    ></create-app-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
// import warehouseCard from "./warehouse-card.vue";
// import createAppModal from "./createAppModal.vue";

// 接口定义
interface AppItem {
  tid: string;
  tenantId?: string;
  appName: string;
  appType: number;
  appIcon: string;
  description: string;
  publishStatus: number; // 0-待发布 1-已发布
  createdBy?: string;
  createdTime?: string;
  updatedBy?: string;
  updatedTime?: string;
}

interface PageResult {
  total: number;
  list: AppItem[];
}

// API 路径
const API = {
  PAGE: "/das/repository/page",
  ADD: "/das/repository/add",
  DELETE: "/das/repository/delete",
  PUBLISH: "/das/repository/publish",
};

// 状态定义
const router = useRouter();
const createVisible = ref(false);

// 标签页常量配置
const tabConfig = [
  { label: "全部", value: -1 },
  { label: "待发布", value: 0 },
  { label: "已发布", value: 1 },
];

// 状态定义
const activeTab = ref(-1);
const searchText = ref("");
const currentPage = ref(1);
const pageSize = ref(12);
const total = ref(0);
const todoItems = ref<AppItem[]>([]);
const loading = ref(true); // 加载状态控制

// 加载应用列表
const loadAppList = async () => {
  loading.value = true;
  try {
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      appName: searchText.value,
      publishStatus: activeTab.value === -1 ? null : activeTab.value,
    };
    // $common.post 直接返回接口的 data 部分
    const data = (await $common.post(API.PAGE, params)) as unknown as PageResult;
    todoItems.value = data?.list || [];
    total.value = data?.total || 0;
  } catch (error) {
    console.error("加载应用列表失败:", error);
  } finally {
    loading.value = false;
  }
};

// 新增应用
const createApp = async (formData: Record<string, any>) => {
  try {
    // $common.post 直接返回接口的 data 部分（新增接口返回的是 tid 字符串）
    const data = (await $common.post(API.ADD, formData)) as unknown as AppItem;
    ElMessage.success("创建应用成功");
    handleModalClose();
    // 跳转到编辑页面;
    if (data.tid) {
      router.push({
        path: "editor",
        query: {
          type: "warehouse-editor",
          appId: data.tid,
          title: "应用编辑",
        },
      });
    }
  } catch (error) {
    console.error("创建应用失败:", error);
    ElMessage.error("创建应用失败");
  }
};

// 删除应用
const deleteApp = async (appId: string) => {
  try {
    await ElMessageBox.confirm("确定要删除该应用吗？删除后不可恢复。", "删除应用", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    // $common.post 直接返回接口的 data 部分
    await $common.post(API.DELETE, { tid: appId });
    ElMessage.success("删除应用成功");
    loadAppList();
  } catch (error: any) {
    if (error !== "cancel") {
      console.error("删除应用失败:", error);
      ElMessage.error("删除应用失败");
    }
  }
};

// 事件处理函数
const handleTabChange = (key: number) => {
  activeTab.value = key;
  currentPage.value = 1;
  loadAppList();
};

const handleSearch = () => {
  currentPage.value = 1;
  loadAppList();
};

const handleSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  loadAppList();
};

const handleCurrentChange = (current: number) => {
  currentPage.value = current;
  loadAppList();
};

// 打开创建弹窗
const openCreateModal = () => {
  createVisible.value = true;
};

// 关闭弹窗
const handleModalClose = () => {
  createVisible.value = false;
};

// 处理操作
const handleOperation = (action: string, item: AppItem) => {
  if (action === "edit") {
    // 跳到编辑应用页面
    router.push({
      path: "editor",
      query: {
        type: "warehouse-editor",
        appId: item.tid,
        title: "应用编辑",
      },
    });
  } else if (action === "delete") {
    // 删除应用
    deleteApp(item.tid);
  } else if (action === "publish") {
    $common.handle({
      url: API.PUBLISH,
      method: "POST",
      action: item.publishStatus === 0 ? "发布" : "取消发布",
      info: item.publishStatus === 0 ? "确认发布该应用？" : "确认取消发布该应用？",
      data: { tid: item.tid, publishStatus: item.publishStatus === 0 ? 1 : 0 },
      done: () => {
        loadAppList();
      },
    });
  }
};

// 页面加载时初始化数据
onMounted(() => {
  loadAppList();
});
</script>

<style scoped lang="scss">
.app-warehouse {
  .search-input {
    width: 200px;
  }

  .content {
    flex: 1;
    overflow-y: auto;
  }

  .todo-cards {
    padding-top: 5px;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 20px;
  }

  .empty-state {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 400px;
  }

  .footer {
    display: flex;
    justify-content: flex-end;
    padding-top: 20px;
  }

  /* 响应式布局 */
  @media (max-width: 1600px) {
    .todo-cards {
      grid-template-columns: repeat(3, 1fr);
    }
  }

  @media (max-width: 1280px) {
    .todo-cards {
      grid-template-columns: repeat(2, 1fr);
    }
  }

  @media (max-width: 992px) {
    .todo-cards {
      grid-template-columns: 1fr;
    }
  }
}
</style>
