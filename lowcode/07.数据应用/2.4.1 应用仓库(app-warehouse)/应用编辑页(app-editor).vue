<template>
  <!-- 全屏大屏设计器 iframe -->
  <div v-if="showFullScreenDesigner" class="full-screen-designer">
    <!-- 加载遮罩：iframe 未加载完成时显示 -->
    <div v-if="iframeLoading" class="designer-loading-mask">
      <div class="designer-loading-spinner">
        <el-icon class="is-loading" :size="32" color="#409eff"><el-icon-loading /></el-icon>
        <p class="designer-loading-text">设计器加载中...</p>
      </div>
    </div>
    <iframe
      ref="iframeRef"
      :src="iframeReady ? designerUrl : 'about:blank'"
      class="designer-iframe"
      frameborder="0"
      allowfullscreen
      @load="onIframeLoad"
    ></iframe>
  </div>

  <!-- 主应用编辑器 -->
  <div v-else class="app-editor card-container flex flex-col">
    <!-- 顶部导航与操作栏 -->
    <div class="editor-header flex items-center justify-between">
      <div class="flex items-center gap-4">
        <h2 class="header-title">{{ appInfo?.appName || "—" }}</h2>
        <el-tag :type="appInfo?.publishStatus === 0 ? 'warning' : 'success'">
          {{ appInfo?.publishStatus === 0 ? "待发布" : "已发布" }}
        </el-tag>
        <div class="header-meta">
          <span class="flex items-center gap-1">
            <Icon icon="el-icon-Timer" />
            更新时间: {{ appInfo?.updatedTime || "—" }}
          </span>
          <span class="flex items-center gap-1">
            <Icon icon="role" />
            更新人: {{ appInfo?.updatedBy || "—" }}
          </span>
        </div>
      </div>
      <el-button type="primary" plain @click="editVisible = true">
        <Icon icon="el-icon-edit" class="mr-1" />
        编辑修改
      </el-button>
    </div>

    <!-- 画布编辑器区域 -->
    <div v-loading="loading" class="editor-canvas">
      <div class="canvas-content">
        <el-card
          v-for="node in nodes"
          :key="node.id"
          class="node-card"
          @click="handleNodeClick(node)"
        >
          <!-- 卡片头部 -->
          <div
            class="node-header flex items-center justify-center p-4"
            :style="{
              backgroundColor: node.type === 'empty' ? 'white' : '#eff6ff80',
            }"
          >
            <!-- 空节点：新建按钮 -->
            <template v-if="node.type === 'empty'">
              <div class="create-buttons-container">
                <button
                  v-for="config in createButtonConfigs"
                  :key="config.type"
                  :class="['create-btn', `create-btn--${config.color}`]"
                  @click="handleCreatePage(config.type)"
                >
                  <Icon :icon="config.icon" :size="18" class="btn-icon" />
                  <span class="btn-text">{{ config.text }}</span>
                </button>
              </div>
            </template>

            <!-- 普通节点：缩略图或占位 -->
            <template v-else>
              <img
                v-if="node.thumbnail"
                :src="getThumbnailUrl(node.thumbnail)"
                class="node-thumbnail"
                alt="缩略图"
              />
              <el-skeleton v-else />
            </template>
          </div>

          <!-- 卡片底部 -->
          <div class="node-footer flex items-center justify-between">
            <div class="node-footer-content">
              <div class="flex items-center gap-2">
                <h4 :title="node.remark">{{ node.remark }}</h4>
                <el-tag
                  v-if="node.type !== 'empty'"
                  :type="getTagInfo(node.type, 'tagType') as any"
                  :class="node.type === '1' ? 'tag-report' : undefined"
                >
                  {{ getTagInfo(node.type, "tagName") }}
                </el-tag>
              </div>
              <p :title="node.realName">
                {{ node.realName }}
              </p>
            </div>
            <template v-if="node.type !== 'empty'">
              <el-dropdown @command="(command) => handleDropdownCommand(command, node)">
                <span class="footer-more">
                  <Icon icon="el-icon-MoreFilled" size="14" />
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="i in getFilteredMenu()"
                      :key="i.key"
                      :command="i.key"
                      :class="{ 'delete-item': i.key === 'delete' }"
                      :style="i.key === 'delete' ? { color: '#f56c6c' } : {}"
                    >
                      <Icon :icon="i.icon" class="mr-1" />
                      {{ i.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </div>
        </el-card>
      </div>
      <!-- 分页组件 -->
      <div class="editor-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
    <create-app-modal
      v-if="editVisible"
      v-model="editVisible"
      :app-data="appInfo"
      :mode="'edit'"
      @submit="handleSubmit"
      @close="editVisible = false"
    ></create-app-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, reactive, onMounted, onUnmounted, nextTick } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
// import { AuthStorage } from "@/utils/auth";
// import createAppModal from "./createAppModal.vue";

interface AppData {
  tid?: string;
  appName?: string;
  appType?: number;
  appIcon?: string;
  description?: string;
  publishStatus?: number;
  createdBy?: string;
  createdTime?: string;
  updatedBy?: string;
  updatedTime?: string;
}

const router = useRouter();
const route = useRoute();
// 全屏设计器状态管理
const showFullScreenDesigner = ref(false);
const iframeLoading = ref(false);
const designerUrl = ref("");
const pid = ref<string>("");

const editVisible = ref(false);
const createType = reactive({
  0: {
    title: "专题",
    name: "page",
  },
  1: {
    title: "报表",
    name: "report",
  },
  2: {
    title: "表单",
    name: "form",
  },
  3: {
    title: "大屏",
    name: "visual",
  },
});

// 创建按钮配置
const createButtonConfigs = [
  {
    type: "0",
    color: "blue",
    icon: "code",
    text: "新建专题页",
    tagName: "专题页",
    tagType: "primary",
  },
  {
    type: "1",
    color: "indigo",
    icon: "monitor",
    text: "新建报表页",
    tagName: "报表页",
    tagType: "primary",
  },
  {
    type: "2",
    color: "orange",
    icon: "el-icon-Film",
    text: "新建表单页",
    tagName: "表单页",
    tagType: "warning",
  },
  {
    type: "3",
    color: "emerald",
    icon: "el-icon-Monitor",
    text: "新建大屏页",
    tagName: "大屏页",
    tagType: "success",
  },
];

const contextmenu = ref([
  {
    label: "删除",
    key: "delete",
    icon: "el-icon-delete",
  },
  {
    label: "复制发布链接",
    key: "copyLink",
    icon: "el-icon-link",
  },
]);

const nodes = ref([
  {
    id: "n5",
    remark: "创建新页面",
    realName: "ADD NEW PAGE",
    type: "empty",
    thumbnail: "",
  },
]);

const API = {
  UPDATE: "/das/repository/update",
  TAB_PAGE: "/das/repository/tab/page",
  QUERY_BY_ID: "/das/repository/queryById",
  TAB_ADD: "/das/repository/tab/add",
  TAB_DELETE: "/das/repository/tab/delete",
  TAB_BIND: "/das/repository/tab/bind",
};

// 分页相关状态
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const loading = ref(false);
const appInfo = ref<AppData>();
// 获取完整的缩略图 URL
const getThumbnailUrl = (thumbnail: string) => {
  return "/fileCenter" + thumbnail;
};
const getTagInfo = (type: string, key: string) => {
  const item = createButtonConfigs.find((item: any) => item.type == type);
  return item?.[key as keyof typeof item];
};
const handleSubmit = async (formData: Record<string, any>) => {
  const res = (await $common.post(API.UPDATE, {
    ...formData,
    tid: appInfo.value?.tid,
  })) as unknown as AppData;
  if (res) {
    ElMessage.success("更新应用成功");
    appInfo.value = res;
  }
};

const handleDropdownCommand = (command: any, node: any) => {
  if (command == "delete") {
    handleDeleteNode(node);
  } else if (command == "copyLink") {
    handleCopyLink(node);
  }
};

const getFilteredMenu = () => {
  const isPublished = appInfo.value?.publishStatus === 1;
  return contextmenu.value.filter((item) => {
    if (item.key === "delete" && isPublished) {
      return false;
    }
    if (item.key === "copyLink" && !isPublished) {
      return false;
    }
    return true;
  });
};

const handleCopyLink = (node: any) => {
  let publishUrl = "";
  if (node.type == 3) {
    publishUrl = `${window.location.origin}/bigScreen/#/publish/visual/${node.relaId}`
  } else {
    publishUrl = `${window.location.origin}/#/publish-view?showNav=0&type=${node.type == 2 ? "form" : "component"}&pid=${node.pid}&tid=${node.relaId}`
  }
  if (publishUrl) {
    $common.copyText(publishUrl);
  } else {
    ElMessage.warning("没有可复制的内容");
  }
};

const handleDeleteNode = async (node: any) => {
  $common.handle({
    url: API.TAB_DELETE,
    method: "POST",
    data: {
      tid: node.tid,
    },
    done: () => {
      // 刷新页签数据
      fetchTabPage(pid.value);
    },
  });
};

// 点击节点
const handleNodeClick = (node: any) => {
  if (node.type == "empty") return;
  if (node.type !== 3) {
    const pageType = createType[node.type as keyof typeof createType];
    router.push({
      path: `editor`,
      query: {
        type: `warehouse-${pageType.name}-editor`,
        tid: node.relaId,
        tabId: node.tid,
        pid: node.type == 2 ? pid.value : undefined,
        realName: node.type !== 2 ? node.realName : undefined,
        remark: node.type !== 2 ? node.remark : undefined,
        title: `${pageType.title}页编辑`,
      },
    });
  } else {
    openFullScreenDesigner(node.relaId, node.tid);
  }
};

// 创建新页面
const handleCreatePage = async (type: any) => {
  try {
    const res = (await $common.post(API.TAB_ADD, {
      pid: pid.value,
      tabType: type,
    })) as unknown as any;
    if (!res.tid) {
      ElMessage.error("创建页面失败");
      return;
    }
    if (type == 3) {
      // 全屏打开大屏设计器
      openFullScreenDesigner("", res.tid);
      return;
    }
    const pageType = createType[type as keyof typeof createType];
    router.push({
      path: `editor`,
      query: {
        type: `warehouse-${pageType.name}-editor`,
        title: `${pageType.title}页编辑`,
        pid: pid.value, // 挂载父节点id
        tabId: res.tid,
        create: "true",
      },
    });
  } catch (err) {
    console.error("创建页面失败:", err);
    ElMessage.error("创建页面失败");
  }
};
const iframeReady = ref(false);
// 全屏打开设计器
const openFullScreenDesigner = (tid?: string, tabId?: string) => {
  // const designerBaseUrl = "http://localhost:5173/"; // 本地调试
  const designerBaseUrl = window.location.origin + "/bigScreen";

  const params = new URLSearchParams({
    pid: pid.value,
    tid: tid || "",
    tabId: tabId || "",
    communication: "postMessage",
    t: new Date().getTime()
  });

  const token = localStorage.getItem("token") || "";
  // 将 token 通过 base64 编码后传递
  if (token) {
    params.set("token", btoa(encodeURIComponent(token)));
  }

  // 设置设计器 URL
  designerUrl.value = `${designerBaseUrl}/#/?${params.toString()}`;
  // 显示全屏设计器（先显示 loading）
  iframeReady.value = false;
  iframeLoading.value = true;
  showFullScreenDesigner.value = true;
  
   // 下一帧再设置 src，确保 iframe DOM 已挂载
  nextTick(() => {
    iframeReady.value = true;
  });
};

// 关闭设计器
const closeDesigner = () => {
  showFullScreenDesigner.value = false;
  designerUrl.value = "";
  // 刷新当前页面数据
  fetchTabPage(pid.value);
};

// 设计保存成功处理
const handleDesignSaveSuccess = () => {
  closeDesigner();
  // 刷新当前页面数据
  fetchTabPage(pid.value);
};

// 获取应用信息
const fetchAppInfo = async (appId: string) => {
  try {
    loading.value = true;
    appInfo.value = (await $common.post(API.QUERY_BY_ID, { tid: appId })) as unknown as AppData;
  } catch (err) {
    console.error("获取应用信息失败:", err);
    ElMessage.error("获取应用信息失败");
  } finally {
    loading.value = false;
  }
};

// 分页查询页签
const fetchTabPage = async (pid: string) => {
  try {
    loading.value = true;
    const response = (await $common.post(API.TAB_PAGE, {
      pageNum: currentPage.value.toString(),
      pageSize: pageSize.value.toString(),
      pid,
    })) as unknown as any;
    // 转换接口返回的数据结构为节点数据
    const tabNodes =
      response.list?.map((item: any) => ({
        ...item,
        id: item.tid,
        remark: item.nameCn || "未命名",
        realName: item.name,
        type: item.tabType,
      })) || [];

    // 保持空节点在最前面
    nodes.value = [
      {
        id: "n5",
        remark: "创建新页面",
        realName: "ADD NEW PAGE",
        type: "empty",
        thumbnail: "",
      },
      ...tabNodes,
    ];

    total.value = response.total;
  } catch (err) {
    console.error("查询页签失败:", err);
  } finally {
    loading.value = false;
  }
};

// 分页变化处理
const handlePageChange = (page: number) => {
  currentPage.value = page;
  fetchTabPage(pid.value);
};

// 每页条数变化处理
const handleSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  fetchTabPage(pid.value);
};

// iframe 加载完成
const onIframeLoad = () => {
  iframeLoading.value = false;
};

// 监听来自 iframe 的消息
const handleMessage = (event: MessageEvent) => {
  // 验证消息来源（在生产环境中应该验证 event.origin）
  // if (event.origin !== "http://localhost:5176") return;
  // console.log("handleMessage", event);

  const { type } = event.data;

  switch (type) {
    case "DESIGN_SAVED":
      handleDesignSaveSuccess();
      break;
    case "CLOSE_DESIGNER":
      closeDesigner();
      break;
  }
};

// 挂载/卸载消息监听器
onMounted(() => {
  window.addEventListener("message", handleMessage);
});

onUnmounted(() => {
  window.removeEventListener("message", handleMessage);
});

// 监听路由变化
watch(
  () => route.query,
  (newQuery: any) => {
    if (newQuery.appId) {
      pid.value = newQuery.appId as string;
      // 获取应用信息
      fetchAppInfo(pid.value);
      // 重置分页并查询页签
      currentPage.value = 1;
      fetchTabPage(pid.value);
    }
  },
  {
    immediate: true,
  }
);
</script>

<style lang="scss" scoped>
.app-editor {
  .editor-header {
    height: 64px;
    padding: 0 32px;
    background-color: #fff;
    border-bottom: 1px solid #e5e7eb;
    border-radius: 8px 8px 0 0;
    .header-title {
      font-size: 18px;
      font-weight: bold;
      color: #1f2937;
      margin: 0;
    }

    .header-meta {
      display: flex;
      align-items: center;
      gap: 16px;
      font-size: 11px;
      color: #9ca3af;
      font-weight: bold;
      margin-left: 8px;
      span {
        line-height: 16px;
      }
    }
  }

  .editor-canvas {
    flex: 1;
    position: relative;
    overflow-y: auto;
    background-color: #f5f7fa;
    background-size: 100% 100%;
    background-image: url("data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbG5zOnhsaW5rPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIiB2ZXJzaW9uPSIxLjEiIHdpZHRoPSIxMDAlIiBoZWlnaHQ9IjEwMCUiPjxkZWZzPjxwYXR0ZXJuIGlkPSJwYXR0ZXJuXzAiIHBhdHRlcm5Vbml0cz0idXNlclNwYWNlT25Vc2UiIHg9IjAiIHk9IjAiIHdpZHRoPSIxMCIgaGVpZ2h0PSIxMCI+PHJlY3Qgd2lkdGg9IjEiIGhlaWdodD0iMSIgcng9IjEiIHJ5PSIxIiBmaWxsPSIjYWFhYWFhIi8+PC9wYXR0ZXJuPjwvZGVmcz48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSJ1cmwoI3BhdHRlcm5fMCkiLz48L3N2Zz4=");
  }

  .canvas-content {
    padding: 32px;
    z-index: 10;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 28px;

    @media (max-width: 768px) {
      grid-template-columns: repeat(2, 1fr);
    }

    @media (max-width: 1024px) {
      grid-template-columns: repeat(2, 1fr);
    }

    @media (max-width: 1280px) {
      gap: 20px;
      grid-template-columns: repeat(3, 1fr);
    }
  }

  .node-card {
    cursor: pointer;
    box-shadow: 0 0 12px #f3f4fa;
    :deep(.el-card__body) {
      padding: 0px !important;
    }
    &:hover {
      transform: translateY(-4px);
      border: 1px solid #5591ff;

      .node-footer h4 {
        color: #2563eb;
      }
    }
  }

  .node-header {
    height: 160px;
    position: relative;
    border-bottom: 1px solid #f9fafb;
    padding: 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
  }

  .node-thumbnail {
    width: 100%;
    height: 100%;
    object-fit: fill;
    // object-fit: contain;
    border-radius: 4px;
  }

  .create-buttons-container {
    width: 100%;
    height: 100%;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    // padding: 8px;
  }

  .create-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 0 12px;
    border-radius: 12px;
    border: 1px dashed #e5e7eb;
    background-color: transparent;
    transition: all 0.3s;
    cursor: pointer;

    &:hover {
      background-color: transparent !important;
    }

    .btn-icon {
      flex-shrink: 0;
      color: #686c80;
      transition: color 0.3s;
    }

    .btn-text {
      font-size: 12px;
      color: #686c80;
      font-weight: bold;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      transition: color 0.3s;
    }

    &.create-btn--blue {
      &:hover {
        border-color: #60a5fa;
        background-color: rgba(239, 246, 255, 0.5) !important;

        .btn-icon {
          color: #3b82f6;
        }

        .btn-text {
          color: #1d4ed8;
        }
      }
    }

    &.create-btn--indigo {
      &:hover {
        border-color: #818cf8;
        background-color: rgba(238, 242, 255, 0.5) !important;

        .btn-icon {
          color: #6366f1;
        }

        .btn-text {
          color: #4f46e5;
        }
      }
    }

    &.create-btn--orange {
      &:hover {
        border-color: #fb923c;
        background-color: rgba(255, 247, 237, 0.5) !important;

        .btn-icon {
          color: #f97316;
        }

        .btn-text {
          color: #ea580c;
        }
      }
    }

    &.create-btn--emerald {
      &:hover {
        border-color: #34d399;
        background-color: rgba(236, 253, 245, 0.5) !important;

        .btn-icon {
          color: #10b981;
        }

        .btn-text {
          color: #059669;
        }
      }
    }
  }

  .node-footer {
    width: 100%;
    padding: 0.5rem 1rem;
    .node-footer-content {
      width: calc(100% - 20px);
    }
    h4 {
      transition: color 0.3s;
      font-size: 14px;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    
    p {
      line-height: 1.4;
      font-size: 12px;
      color: #686c80;
      font-weight: bold;
      margin-top: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .footer-more {
      outline: none;
      cursor: pointer;
      transform: rotate(90deg);
    }
  }

  .tag-report {
    color: #4f46e5;
    border-color: #818cf8;
    background-color: rgba(238, 242, 255, 0.5) !important;
  }

  .editor-pagination {
    padding: 0 32px 15px;
    // background-color: #fff;
    // border-top: 1px solid #e5e7eb;
    // border-radius: 0 0 8px 8px;
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }
}

/* 加载状态样式 */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

/* ==================== 全屏设计器样式 ==================== */
.full-screen-designer {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: #fff;
  z-index: 9999;
}

.designer-loading-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.designer-loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.designer-loading-text {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.designer-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #f5f7fa;
}
</style>
