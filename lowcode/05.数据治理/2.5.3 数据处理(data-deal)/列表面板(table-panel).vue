<template>
  <div class="table-panel">
    <!-- 标签页菜单 -->
    <div class="tabs-container">
      <u-tabs v-model="activeTab" :tabs="tabs" show-border @change="handleTabChange" />
    </div>

    <!-- 表格部分 -->
    <div class="table-container flex flex-col px-5 pt-5 pb-2">
      <data-table :columns="columns" :data="filteredTasks" show-index>
        <!-- 工具栏 -->
        <template #toolbar>
          <div class="flex justify-between items-center">
            <div ref="createTaskContainerRef" class="create-task-container">
              <el-button type="primary" @click="toggleCreateTaskPopup">
                <Icon :icon="'el-icon-Plus'" class="mr-1"></Icon>
                创建新任务
              </el-button>

              <!-- 创建新任务弹窗 -->
              <div v-if="showCreateTaskPopup" class="create-task-popup">
                <div class="popup-header">
                  <span>请选择开发模式</span>
                </div>
                <div class="popup-content">
                  <div
                    v-for="mode in developmentModes"
                    :key="mode.value"
                    class="mode-item"
                    @click="selectDevelopmentMode(mode)"
                  >
                    <div
                      class="mode-icon"
                      :style="{
                        color: transformedColor('color', mode.color),
                        backgroundColor: transformedColor('bgColor', mode.color),
                      }"
                    >
                      <Icon :icon="mode.icon" :size="24"></Icon>
                    </div>
                    <div class="mode-name">{{ mode.name }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div class="toolbar-right flex items-center">
              <el-input
                v-model="searchKeyword"
                placeholder="请输入任务名称"
                clearable
                class="search-input"
                @input="handleSearch"
              >
                <template #prefix>
                  <Icon :icon="'el-icon-Search'"></Icon>
                </template>
              </el-input>

              <el-button>
                <Icon :icon="'el-icon-Filter'" class="mr-1"></Icon>
                筛选
              </el-button>
            </div>
          </div>
        </template>

        <!-- 操作栏 -->
        <template #operation="{ row }">
          <div class="flex items-center">
            <el-button type="primary" link @click="handleConfigClick(row)">配置</el-button>
            <span class="button-divider"></span>
            <el-button type="primary" link style="margin-left: 0px">
              {{ row.runStatus === "run" ? "停用" : "启用" }}
            </el-button>
            <span class="button-divider"></span>
            <el-dropdown>
              <span class="el-dropdown-link flex items-center operation-buttons_more">
                更多
                <Icon :icon="'el-icon-ArrowDown'" class="ml-1"></Icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item>
                    <Icon :icon="'el-icon-Timer'" class="mr-1"></Icon>
                    日志
                  </el-dropdown-item>
                  <el-dropdown-item style="color: #ff4d4f">
                    <Icon :icon="'el-icon-Delete'" class="mr-1"></Icon>
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>

        <!-- 状态列 -->
        <template #column-runStatus="{ row }">
          <div class="status-item flex items-center" :class="row.runStatus">
            <div class="status-dot mr-1"></div>
            <span>{{ row.runStatus === "run" ? "成功" : "待运行" }}</span>
          </div>
        </template>
      </data-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from "vue";

// 定义 props
const props = defineProps<{
  selectedNodeKey: string;
  taskTreeData: any[];
}>();

// 定义 emits
const emit = defineEmits<{
  (e: "config-click", row: any): void;
  (e: "changeDevMode", data: string): void;
}>();

// 响应式变量
const showCreateTaskPopup = ref(false);
const createTaskContainerRef = ref(null);

// 开发模式配置
const developmentModes = [
  {
    name: "组件开发",
    value: "component",
    color: "blue",
    icon: "category",
  },
  {
    name: "脚本开发",
    value: "script",
    color: "green",
    icon: "code",
  },
  {
    name: "模块开发",
    value: "module",
    color: "purple",
    icon: "copy",
  },
];

// 颜色映射表
const colorMap = {
  blue: "#1890ff",
  green: "#52c41a",
  purple: "#722ed1",
};

// 转换颜色
const transformedColor = (type: "color" | "bgColor", color: string) => {
  const hexColor = colorMap[color] || "#999999";
  return type === "color" ? hexColor : hexColor + "20";
};

// 切换创建任务弹窗
const toggleCreateTaskPopup = () => {
  showCreateTaskPopup.value = !showCreateTaskPopup.value;
};

// 选择开发模式
const selectDevelopmentMode = (mode) => {
  emit("changeDevMode", mode.value);
  showCreateTaskPopup.value = false;
};

// 点击外部关闭弹窗
const handleClickOutside = (event) => {
  if (createTaskContainerRef.value && !createTaskContainerRef.value.contains(event.target)) {
    showCreateTaskPopup.value = false;
  }
};

// 处理配置按钮点击
const handleConfigClick = (row: any) => {
  emit("config-click", row);
};

// 标签页配置
const activeTab = ref("all");
const tabs = [
  { label: "全部任务", value: "all" },
  { label: "运行中", value: "running" },
  { label: "已停止", value: "stopped" },
];

// 表格列配置
const columns = [
  {
    prop: "project",
    label: "项目",
    minWidth: 120,
  },
  {
    prop: "business",
    label: "业务",
    minWidth: 120,
  },
  {
    prop: "name",
    label: "任务",
    minWidth: 150,
  },
  {
    prop: "devMode",
    label: "开发方式",
    minWidth: 100,
    formatter: (row: any) => {
      return row.devMode === "component" ? "组件开发" : "脚本开发";
    },
  },
  {
    prop: "taskType",
    label: "任务类型",
    minWidth: 100,
  },
  {
    prop: "engineType",
    label: "引擎类型",
    minWidth: 100,
  },
  {
    prop: "createTime",
    label: "创建时间",
    minWidth: 150,
  },
  {
    prop: "runStatus",
    label: "状态",
    minWidth: 100,
    slotName: "status",
  },
];

// 搜索关键词
const searchKeyword = ref("");

// 处理标签页切换
const handleTabChange = (key: string) => {
  activeTab.value = key;
};

// 处理搜索
const handleSearch = () => {
  // 搜索逻辑
};

// 计算过滤后的任务
const filteredTasks = computed(() => {
  if (!props.selectedNodeKey) return [];

  // 根据 selectedNodeKey 查找对应的节点
  const selectedNode = findNode(props.selectedNodeKey);
  if (!selectedNode) return [];

  // 递归获取所有子节点
  const getAllChildren = (node: any): any[] => {
    if (!node.children || node.children.length === 0) {
      return [node];
    }
    return node.children.flatMap((child: any) => getAllChildren(child));
  };

  let tasks = getAllChildren(selectedNode);

  // 根据标签页过滤
  if (activeTab.value === "running") {
    tasks = tasks.filter((task) => task.runStatus === "run");
  } else if (activeTab.value === "stopped") {
    tasks = tasks.filter((task) => task.runStatus === "stop");
  }

  // 根据搜索关键词过滤
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase();
    tasks = tasks.filter((task) => task.name.toLowerCase().includes(keyword));
  }

  // 格式化任务数据
  return tasks.map((task) => {
    // 提取项目、业务和任务信息
    let project = "";
    let business = "";
    const taskName = task.name;

    // 确定当前节点的层级
    // 检查是否有三级结构
    if (selectedNode.tid && selectedNode.level === 1) {
      // 一级节点（如：广电业务分析专题）
      project = selectedNode.name;

      // 查找业务层级
      if (selectedNode.children) {
        for (const child of selectedNode.children) {
          if (child.children && child.children.some((c: any) => c.tid === task.tid)) {
            business = child.name;
            break;
          }
        }
      }
    } else if (selectedNode.tid && selectedNode.level === 2) {
      // 二级节点（如：收视数据分析）
      // 查找一级节点
      const parentNode = findParentNode(selectedNode.tid);
      project = parentNode ? parentNode.name : "";
      business = selectedNode.name;
    } else if (selectedNode.tid && selectedNode.level === 3) {
      // 三级节点（如：全量频道收视数据）
      // 查找一级和二级节点
      const parentNode = findParentNode(selectedNode.tid);
      const grandParentNode = findGrandParentNode(selectedNode.tid);
      project = grandParentNode ? grandParentNode.name : "";
      business = parentNode ? parentNode.name : "";
    }

    return {
      ...task,
      project,
      business,
      name: taskName,
      taskType: "离线任务",
      engineType: "MLSQL",
    };
  });
});

// 查找节点
const findNode = (targetTid: string): any => {
  const nodes = props.taskTreeData;

  const search = (nodeList: any[]): any => {
    for (const node of nodeList) {
      if (node.tid === targetTid) {
        return node;
      }
      if (node.children) {
        const found = search(node.children);
        if (found) {
          return found;
        }
      }
    }
    return null;
  };

  return search(nodes);
};

// 查找节点的父节点
const findParentNode = (targetTid: string): any => {
  const nodes = props.taskTreeData;

  const search = (nodeList: any[], parent: any = null): any => {
    for (const node of nodeList) {
      if (node.tid === targetTid) {
        return parent;
      }
      if (node.children) {
        const found = search(node.children, node);
        if (found) {
          return found;
        }
      }
    }
    return null;
  };

  return search(nodes);
};

// 查找父节点的父节点
const findGrandParentNode = (targetTid: string): any => {
  // 先找到目标节点
  const targetNode = findNode(targetTid);
  if (!targetNode) return null;

  // 找到目标节点的父节点
  const parentNode = findParentNode(targetTid);
  if (!parentNode) return null;

  // 找到父节点的父节点
  return findParentNode(parentNode.tid);
};

// 生命周期钩子
onMounted(() => {
  document.addEventListener("click", handleClickOutside);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", handleClickOutside);
});
</script>

<style scoped lang="scss">
.table-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #fff;
  .tabs-container {
    border-bottom: 1px solid #ebeef5;
    padding: 0 20px;
  }

  .table-container {
    flex: 1;
  }

  .toolbar-right {
    gap: 10px;
  }

  .search-input {
    width: 200px;
  }

  .operation-buttons_more {
    outline: none;
    color: #1b67f8;
  }

  .status-item {
    &.run {
      color: #67c23a;

      .status-dot {
        width: 5px;
        height: 5px;
        border-radius: 50%;
        background-color: #67c23a;
      }
    }

    &.stop {
      color: #f56c6c;

      .status-dot {
        width: 5px;
        height: 5px;
        border-radius: 50%;
        background-color: #f56c6c;
      }
    }
  }

  /* 创建新任务弹窗样式 */
  .create-task-container {
    position: relative;
    display: inline-block;
  }

  .create-task-popup {
    position: absolute;
    top: 100%;
    left: 0;
    margin-top: 8px;
    width: 350px;
    background-color: #fff;
    border: 1px solid #ebeef5;
    border-radius: 5px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    z-index: 1000;
    overflow: hidden;
  }

  .popup-header {
    padding: 15px;
    font-size: 14px;
    font-weight: 500;
    color: #333;
  }

  .popup-content {
    padding: 0 15px 15px;
    display: flex;
    justify-content: space-between;
  }

  .mode-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 8px 0;
    border-radius: 5px;
    cursor: pointer;
    transition: all 0.3s ease;

    &:hover {
      background-color: #f5f7fa;
    }
  }

  .mode-icon {
    width: 40px;
    height: 40px;
    display: flex;
    justify-content: center;
    align-items: center;
    border-radius: 5px;
    margin-bottom: 12px;
  }

  .mode-name {
    font-size: 14px;
    color: #333;
  }
}
</style>
