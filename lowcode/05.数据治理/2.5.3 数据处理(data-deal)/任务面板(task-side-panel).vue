<template>
  <div class="left-panel">
    <!-- 左侧Tab菜单 -->
    <el-segmented
      v-model="activeLeftTab"
      style="margin: 10px"
      :options="leftTabs"
      @change="handleLeftTabChange"
    />
    <div class="task-list-container">
      <!-- 任务列表Tab -->
      <template v-if="activeLeftTab === 'task-list'">
        <div class="task-list-header">
          <el-input
            v-model="taskSearchValue"
            placeholder="请输入关键词"
            clearable
            prefix-icon="Search"
          />
          <el-button type="text" icon="Plus" style="padding: 8px" @click="addTask"></el-button>
        </div>
        <u-tree
          ref="taskTreeRef"
          class="task-tree-container"
          :data="taskTreeData"
          :search="false"
          :highlight-current="true"
          :expand="false"
          :default-expand-all="true"
          :checked="false"
          :checkable="false"
          :expand-on-click-node="false"
          :default-props="{ label: 'name', children: 'children' }"
          :current-node-key="props.currentNodeKey"
          @node-click="handleTaskNodeClick"
        >
          <template #node="{ node }">
            <div
              class="custom-tree-node"
              @mouseenter="handleNodeHover(node)"
              @mouseleave="handleNodeLeave"
            >
              <div class="node-label flex items-center">
                <Icon v-if="node?.level === 3" icon="file" class="mr-1" />

                <Icon
                  v-else
                  :icon="node?.expanded ? 'folder-open' : 'folder'"
                  class="node-icon-folder"
                />
                {{ node?.label }}
              </div>
              <div class="node-actions">
                <span
                  v-if="node?.level !== 3 && hoveredNodeTid !== node?.data?.tid"
                  class="node-count"
                >
                  {{ node?.data?.count }}
                </span>
                <el-dropdown
                  v-if="hoveredNodeTid === node?.data?.tid"
                  trigger="hover"
                  @command="handleDropdownCommand($event, node)"
                >
                  <Icon
                    class="more-icon"
                    icon="el-icon-MoreFilled"
                    :class="{
                      visible:
                        hoveredNodeTid === node?.data?.tid || currentNodeKey === node?.data?.tid,
                    }"
                  ></Icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-for="item in getDropdownMenu(node)"
                        :key="item.command"
                        :command="item.command"
                        :disabled="item.disabled"
                      >
                        <Icon v-if="item.icon" :icon="item.icon" class="mr-1" />
                        {{ item.label }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </template>
        </u-tree>
      </template>
      <!-- 算子组件Tab -->
      <div v-if="activeLeftTab === 'operator-component'" class="operator-component-container">
        <el-collapse v-model="activeOperatorGroups">
          <el-collapse-item
            v-for="group in operatorGroups"
            :key="group.id"
            :title="group.name"
            :name="group.id"
          >
            <div class="operator-list">
              <div
                v-for="operator in group.children"
                :key="operator.id"
                class="operator-item"
                :class="`operator-item-${operator.color}`"
                draggable="true"
                @dragstart="handleOperatorDragStart($event, operator)"
              >
                <div
                  class="operator-icon"
                  :style="{
                    color: transformedColor('color', operator.color),
                    backgroundColor: transformedColor('bgColor', operator.color),
                  }"
                >
                  <Icon :icon="operator.icon"></Icon>
                </div>
                <span>{{ operator.name }}</span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";

interface TaskData {
  tid: string;
  name: string;
  runStatus: string;
  devMode: string;
  createTime: string;
  level: number;
  [key: string]: any;
}
// 定义 props
const props = defineProps<{
  taskTreeData: any[];
  currentNodeKey: string;
}>();

// 定义 emit 事件
const emit = defineEmits<{
  (e: "task-node-click", node: any): void;
  (e: "operator-drag-start", event: DragEvent, operator: any): void;
  (e: "add-task"): void;
  (e: "left-tab-change", tab: string): void;
}>();

// 响应式数据
const hoveredNodeTid = ref(null);
const activeLeftTab = ref("task-list");
const taskSearchValue = ref("");
const taskTreeRef = ref(null);
const activeOperatorGroups = ref(["1", "2", "3", "4"]);
const hoveredNodeId = ref(null);
const operatorGroups = ref([
  {
    id: "1",
    name: "数据输入",
    children: [
      { id: "1-1", name: "DB表输入", icon: "database-enter", color: "blue", des: "数据库表" },
      { id: "1-2", name: "API输入", icon: "api", color: "blue", des: "API接口" },
      { id: "1-3", name: "文件输入", icon: "folder-upload", color: "blue", des: "文件数据" },
      { id: "1-4", name: "MongoDB输入", icon: "database-enter", color: "blue", des: "MongoDB" },
      { id: "1-5", name: "ES输入", icon: "database-enter", color: "blue", des: "Elasticsearch" },
      { id: "1-6", name: "kafka输入", icon: "shandian", color: "blue", des: "Kafka" },
    ],
  },
  {
    id: "2",
    name: "数据处理",
    children: [
      { id: "2-1", name: "数据过滤", icon: "filter2", color: "orange", des: "条件过滤" },
      { id: "2-2", name: "列转行", icon: "pivot-table", color: "orange", des: "列转行" },
      { id: "2-3", name: "行转列", icon: "pivot-table", color: "orange", des: "行转列" },
      { id: "2-4", name: "聚合", icon: "move-in", color: "orange", des: "数据聚合" },
      { id: "2-5", name: "抽样", icon: "filter2", color: "orange", des: "数据抽样" },
      { id: "2-6", name: "SQL脚本", icon: "file-code", color: "orange", des: "SQL脚本" },
      { id: "2-7", name: "Python脚本", icon: "file-code", color: "orange", des: "Python脚本" },
      { id: "2-8", name: "Json解析", icon: "file-code", color: "orange", des: "JSON解析" },
      { id: "2-9", name: "新增计算列", icon: "repair", color: "orange", des: "计算列" },
      { id: "2-10", name: "交集", icon: "intersection", color: "orange", des: "数据交集" },
      { id: "2-11", name: "并集", icon: "merge", color: "orange", des: "数据并集" },
    ],
  },
  {
    id: "3",
    name: "数据输出",
    children: [
      { id: "3-1", name: "DB表输出", icon: "database-sync", color: "green", des: "数据库表" },
      { id: "3-2", name: "API输出", icon: "outbound", color: "green", des: "API接口" },
      {
        id: "3-3",
        name: "文件输出",
        icon: "external-transmission",
        color: "green",
        des: "文件数据",
      },
      {
        id: "3-4",
        name: "MongoDB输出",
        icon: "outbound",
        color: "green",
        des: "MongoDB",
      },
      {
        id: "3-5",
        name: "ES输出",
        icon: "external-transmission",
        color: "green",
        des: "Elasticsearch",
      },
      { id: "3-6", name: "kafka输出", icon: "outbound", color: "green", des: "Kafka" },
    ],
  },
]);

// 左侧Tab菜单
const leftTabs = ref([
  { label: "任务列表", value: "task-list" },
  { label: "处理组件", value: "operator-component" },
]);

// 颜色映射表
const colorMap: Record<string, string> = {
  orange: "#fa541c",
  blue: "#1b67f8",
  purple: "#9370DB",
  red: "#FF4D4F",
  yellow: "#FAAD14",
  green: "#06A17E",
};

// 转换颜色为16进制值
const transformedColor = (type: "color" | "bgColor", color: string) => {
  const hexColor = color.startsWith("#") ? color : colorMap[color] || "#CCCCCC";
  return type === "color" ? hexColor : hexColor + "20";
};

// 方法
const handleLeftTabChange = (key: string) => {
  activeLeftTab.value = key;
  emit("left-tab-change", key);
};

const addTask = () => {
  emit("add-task");
};

const handleTaskNodeClick = (node: TaskData) => {
  emit("task-node-click", node);
};

const handleOperatorDragStart = (event: DragEvent, operator: any) => {
  emit("operator-drag-start", event, operator);
};

// 处理节点悬停
const handleNodeHover = (node: any) => {
  hoveredNodeTid.value = node?.data?.tid || null;
};

// 处理节点离开
const handleNodeLeave = () => {
  hoveredNodeId.value = null;
};

// 检查节点是否有子节点
const hasChildren = (node: any): boolean => {
  return !!(node?.children && node.children.length > 0);
};

// 获取下拉菜单配置
const getDropdownMenu = (node: any) => {
  const menuConfig = {
    1: [
      { command: "add-sibling", label: "新增层级", icon: "el-icon-FolderAdd" },
      { command: "add-child", label: "新增子层级", icon: "el-icon-DocumentAdd" },
      { command: "edit", label: "编辑层级", icon: "el-icon-Edit" },
      { command: "delete", label: "删除层级", icon: "el-icon-Delete", disabled: hasChildren(node) },
    ],
    2: [
      { command: "add-sibling", label: "新增层级", icon: "el-icon-DocumentAdd" },
      { command: "edit", label: "编辑层级", icon: "el-icon-Edit" },
      { command: "delete", label: "删除层级", icon: "el-icon-Delete", disabled: hasChildren(node) },
    ],
    3: [
      { command: "task-property", label: "任务属性" },
      { command: "combine-task", label: "组合任务" },
      { command: "delete-task", label: "删除任务" },
    ],
  };
  return menuConfig[node?.level] || [];
};

// 处理下拉菜单命令
const handleDropdownCommand = (command: string, node: any) => {
  console.log("Dropdown command:", command, "Node:", node);

  switch (command) {
    case "add-sibling":
      console.log("新增同级节点:", node);
      ElMessage.success("新增同级节点功能待实现");
      break;
    case "add-child":
      console.log("新增子节点:", node);
      ElMessage.success("新增子节点功能待实现");
      break;
    case "edit":
      console.log("编辑节点:", node);
      ElMessage.success("编辑节点功能待实现");
      break;
    case "delete":
      console.log("删除节点:", node);
      ElMessage.success("删除节点功能待实现");
      break;
    case "task-property":
      handleTaskProperty(node);
      break;
    case "combine-task":
      handleCombineTask(node);
      break;
    case "delete-task":
      handleDeleteTask(node);
      break;
    default:
      break;
  }
};
</script>

<style scoped lang="scss">
.left-panel {
  height: 100%;
  width: 270px;
  background-color: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.task-list-container {
  display: flex;
  flex-direction: column;
  height: calc(100% - 52px);

  .task-list-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    padding: 0 10px 10px;
  }
  .task-tree-container {
    padding: 0 10px 10px;
    height: calc(100% - 42px);
    overflow: auto;
  }
}

.custom-tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 10px;
  .node-label {
    flex: 1;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .node-count {
    font-size: 12px;
    color: #999;
  }
  .node-icon-folder {
    color: #0b8bf9;
    margin-right: 6px;
  }
  .node-icon-folder :deep(.icon) {
    width: 23px !important;
    height: 23px !important;
  }

  .more-icon {
    opacity: 0;
    cursor: pointer;
    transition: opacity 0.2s;
    font-size: 16px;
    color: #909399;
    padding: 4px;
    border-radius: 4px;
    outline: none;

    &:hover {
      color: var(--el-color-primary);
      background-color: var(--el-color-primary-light-9);
      outline: none;
    }

    &:focus {
      outline: none;
    }

    &.visible {
      opacity: 1;
    }
  }
  :deep(.el-tree-node__content) {
    &:hover {
      .more-icon {
        opacity: 1;
      }
    }

    &.is-current {
      background-color: var(--el-color-primary-light-9) !important;
      color: var(--el-color-primary);
    }
  }

  :deep(.el-dropdown-menu__item.delete-item) {
    &:hover {
      background-color: #fef0f0;
    }
  }
}

.operator-component-container {
  padding: 12px;
  height: 100%;
  overflow: auto;

  .operator-list {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .operator-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      border: 1px solid #dee6f3;
      border-radius: 4px;
      cursor: move;
      transition: all 0.3s ease;
      .operator-icon {
        width: 25px;
        height: 25px;
        display: flex;
        justify-content: center;
        align-items: center;
        border-radius: 4px;
        .el-icon {
          font-size: 18px !important;
        }
      }
    }
    .operator-item {
      &.operator-item-blue:hover {
        background-color: #1b67f810;
        border-color: #1b67f8;
      }
      &.operator-item-orange:hover {
        background-color: #fa541c10;
        border-color: #fa541c;
      }
      &.operator-item-green:hover {
        background-color: #06a17e10;
        border-color: #06a17e;
      }
    }
  }
}
</style>
