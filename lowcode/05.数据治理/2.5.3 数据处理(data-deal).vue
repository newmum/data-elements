<template>
  <div class="data-deal card-container">
    <!-- 左侧面板：任务/算子管理 -->
    <task-side-panel
      :task-tree-data="taskTreeData"
      :current-node-key="currentNodeKey"
      @task-node-click="handleTaskNodeClick"
      @operator-drag-start="handleOperatorDragStart"
      @add-task="addTask"
    />
    <!-- 右侧面板 -->
    <div v-if="isShowTable" class="right-panel">
      <table-panel
        :selected-node-key="currentNodeKey"
        :task-tree-data="taskTreeData"
        @change-dev-mode="handleChangeDevMode"
        @config-click="handleConfigClick"
      ></table-panel>
    </div>
    <!-- 右侧面板 -->
    <div v-else class="right-panel">
      <!-- 顶部Tab菜单 -->
      <div class="top-container">
        <el-tabs
          v-model="activeTopTab"
          type="card"
          @tab-remove="handleTopTabRemove"
          @tab-change="handleTopTabChange"
        >
          <el-tab-pane
            v-for="tab in topTabs"
            :key="tab.id"
            :label="tab.name"
            :name="tab.id"
            closable
          ></el-tab-pane>
        </el-tabs>

        <!-- 操作按钮 -->
        <div class="top-operation-buttons">
          <el-button link :icon="'el-icon-Back'" @click="handleBack">返回</el-button>
        </div>
      </div>
      <!-- 中间容器 -->
      <div class="middle-container flex">
        <div class="middle-container-left">
          <!-- 工具栏 -->
          <div class="toolbar-container flex items-center justify-between">
            <!-- 左侧tabs -->
            <div v-if="currentDevMode === 'component'" class="flex items-center">
              <el-segmented
                v-model="currentToolbarMode"
                :options="[
                  { label: '组件模式', value: 'component', icon: 'category' },
                  { label: '脚本模式', value: 'script', icon: 'code' },
                ]"
                @change="handleToolbarModeChange"
              >
                <template #default="scope">
                  <div class="flex items-center gap-1">
                    <Icon :icon="scope.item.icon"></Icon>
                    <div>{{ scope.item.label }}</div>
                  </div>
                </template>
              </el-segmented>
            </div>
            <div v-else class="flex items-center">
              <Icon :icon="'code'" class="mr-1"></Icon>
              {{ `${currentTaskData?.name || "脚本名称"}.py` }}
            </div>
            <!-- 右侧操作按钮 -->
            <div class="flex items-center gap-3">
              <template v-if="currentToolbarMode === 'component'">
                <Icon
                  :icon="'el-icon-Minus'"
                  :title="'缩小'"
                  class="cursor-pointer"
                  @click="handleZoomOut"
                ></Icon>
                <span>{{ zoomLevel }}%</span>
                <Icon
                  :icon="'el-icon-Plus'"
                  :title="'放大'"
                  class="cursor-pointer"
                  @click="handleZoomIn"
                ></Icon>
                <Icon
                  :icon="'el-icon-Refresh'"
                  :title="'重置'"
                  class="cursor-pointer"
                  @click="handleResetCanvas"
                ></Icon>
              </template>
              <template v-if="isShowScript">
                <Icon :icon="'el-icon-Search'" :title="'搜索'" class="cursor-pointer"></Icon>
                <Icon
                  :icon="'el-icon-DocumentCopy'"
                  :title="'复制'"
                  class="cursor-pointer"
                  @click="scriptEditorRef.value?.copy()"
                ></Icon>
              </template>
              <span class="toolbar-divider"></span>
              <el-button type="primary" link @click="handleSave">
                <Icon class="mr-1" :icon="'save'"></Icon>
                保存
              </el-button>
              <span class="toolbar-divider"></span>
              <el-button
                type="success"
                link
                icon="el-icon-CaretRight"
                :disabled="!isSaved"
                style="margin-left: 0px"
                @click="handleRun"
              >
                运行
              </el-button>
              <span class="toolbar-divider"></span>
              <Icon
                v-if="isShowScript"
                :icon="'el-icon-Sunny'"
                :title="'切换主题'"
                class="cursor-pointer"
                @click="handleThemeToggle"
              ></Icon>
              <el-button
                size="small"
                icon="el-icon-FullScreen"
                style="margin-left: 0px; padding: 5px"
                :title="'全屏'"
                @click="handleFullscreen"
              ></el-button>
            </div>
          </div>

          <!-- 组件区域 -->
          <div
            v-if="currentDevMode === 'component' && currentToolbarMode === 'component'"
            class="content"
          >
            <flow-chart
              ref="flowChartRef"
              v-model:flow-data="currentFlowData"
              @node-selected="handleNodeSelected"
            ></flow-chart>
          </div>

          <!-- 脚本区域 -->
          <div v-if="isShowScript" class="content">
            <div
              v-if="currentDevMode === 'component' && currentToolbarMode === 'script'"
              class="script-name flex items-center"
              :class="{ 'monokai-theme': currentTheme === 'monokai' }"
            >
              <Icon :icon="'code'" class="mr-1"></Icon>
              {{ `${currentTaskData?.name || "脚本名称"}.py` }}
            </div>
            <div
              class="script-editor"
              :style="{
                height:
                  currentDevMode === 'component' && currentToolbarMode === 'script'
                    ? 'calc(100% - 32px)'
                    : '100%',
              }"
            >
              <code-editor
                ref="scriptEditorRef"
                v-model="scriptCode"
                lang="sql"
                :theme="currentTheme"
                class="script-editor"
              ></code-editor>
            </div>
          </div>
        </div>
        <property-panel></property-panel>
      </div>
      <!-- 数据预览面板 -->
      <preview-panel v-model:active-tab="activePreviewTab" :selected-node="selectedNode" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import { ElMessage } from "element-plus";
// import previewPanel from "./preview-panel.vue";
// import propertyPanel from "./property-panel.vue";
// import tablePanel from "./table-panel.vue";
// import flowChart from "@/components/business/FlowChart/index.vue";
// import taskSidePanel from "./task-side-panel.vue";

interface TaskData {
  tid: string;
  name: string;
  runStatus: string;
  devMode: string;
  createTime: string;
  level: number;
  [key: string]: any;
}

const scriptEditorRef = ref(null); // 脚本编辑器组件
const flowChartRef = ref(null); // 流程图组件
const zoomLevel = ref(100); // 缩放级别
const isShowTable = ref<boolean>(true); // 是否显示表格
const currentFlowData = ref(); // 当前激活的流程图数据
const activePreviewTab = ref("data-preview"); // 数据预览相关
const selectedNode = ref(null); // 流程图中选中的节点信息
const currentNodeKey = ref(""); // 当前选中的节点的key
const currentTaskData = ref<TaskData>(null); // 当前选中的任务信息
const currentDevMode = ref("component"); // 当前开发模式
const currentToolbarMode = ref("component"); // 当前工具栏模式
const currentTheme = ref("monokai"); // 当前主题 monokai深色主题 chrome浅色主题
const isSaved = ref(false); // 是否已保存
const isFullscreen = ref(false); // 是否全屏
const scriptCode = ref(""); // 脚本代码
const topTabs = ref([]); // 顶部Tab菜单
const activeTopTab = ref(); // 顶部面板状态
const flowChartData = ref({
  "1-1-1": {
    component: {
      nodes: [
        {
          id: "node-1",
          color: "blue",
          des: "general_subject_series",
          icon: "database-enter",
          name: "DB表输入",
          x: 100,
          y: 100,
          shape: "flow-node",
        },
        {
          id: "node-2",
          name: "SQL脚本",
          icon: "file-code",
          color: "orange",
          des: "dwd_general_subject_series",
          x: 400,
          y: 100,
          shape: "flow-node",
        },
        {
          id: "node-3",
          name: "文件输出",
          icon: "external-transmission",
          color: "green",
          des: "dwd_general_subject_series",
          x: 700,
          y: 100,
          shape: "flow-node",
        },
      ],
      edges: [
        {
          id: "edge-1",
          source: { cell: "node-1", port: "out" },
          target: { cell: "node-2", port: "in" },
          attrs: {
            line: {
              stroke: "#828691",
              strokeWidth: 2,
              targetMarker: { name: "block", width: 12, height: 8 },
            },
          },
        },
        {
          id: "edge-2",
          source: { cell: "node-2", port: "out" },
          target: { cell: "node-3", port: "in" },
          attrs: {
            line: {
              stroke: "#828691",
              strokeWidth: 2,
              targetMarker: { name: "block", width: 12, height: 8 },
            },
          },
        },
      ],
    },
    script:
      "import mlsql as ml\n\n# 全量频道收视数据处理\n# 从Kafka读取原始频道数据\noriginal_data = ml.sql('SELECT channel_id, channel_name, view_count, view_duration, timestamp FROM kafka_channel_data')\n\n# 数据清洗和转换\ncleaned_data = ml.sql('''\nSELECT \n  channel_id, \n  channel_name, \n  SUM(view_count) as total_views, \n  AVG(view_duration) as avg_view_duration, \n  MAX(timestamp) as last_update_time \nFROM original_data \nGROUP BY channel_id, channel_name \nORDER BY total_views DESC\n''')\n\n# 写入ClickHouse\nml.sql('INSERT INTO channel_view_data SELECT * FROM cleaned_data')",
  },
  "1-1-2": {
    component: "",
    script:
      "import mlsql as ml\n\n# 用户收视行为数据处理\n# 从Kafka读取用户行为数据\nuser_behavior = ml.sql('''\nSELECT user_id, channel_id, program_id, start_time, end_time, duration, action_type \nFROM kafka_user_behavior\n''')\n\n# 计算用户观看时长和频率\nuser_stats = ml.sql('''\nSELECT \n  user_id, \n  COUNT(*) as watch_count, \n  SUM(duration) as total_watch_time, \n  COUNT(DISTINCT channel_id) as channel_count, \n  MAX(end_time) as last_watch_time \nFROM user_behavior \nGROUP BY user_id \nORDER BY total_watch_time DESC\n''')\n\n# 分析用户偏好频道\nuser_preferences = ml.sql('''\nSELECT \n  user_id, \n  channel_id, \n  COUNT(*) as watch_count, \n  SUM(duration) as watch_duration \nFROM user_behavior \nGROUP BY user_id, channel_id \nORDER BY user_id, watch_duration DESC\n''')\n\n# 写入ClickHouse\nml.sql('INSERT INTO user_watch_stats SELECT * FROM user_stats')\nml.sql('INSERT INTO user_channel_preferences SELECT * FROM user_preferences')",
  },
}); // 流程图数据mock
const taskTreeData = ref([
  {
    tid: "1",
    name: "全域感知",
    level: 1,
    count: 56,
    children: [
      {
        tid: "1-1",
        name: "天域",
        level: 2,
        count: 1,
        children: [
          {
            tid: "1-1-1",
            name: "卫星",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
        ],
      },
      {
        tid: "1-2",
        name: "空域",
        level: 2,
        count: 1,
        children: [
          {
            tid: "1-2-1",
            name: "无人机",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
        ],
      },
      {
        tid: "1-3",
        name: "陆域",
        level: 2,
        count: 17,
        children: [
          {
            tid: "1-3-1",
            name: "地波雷达",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "雷达",
          },
          {
            tid: "1-3-2",
            name: "海岸雷达",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-3",
            name: "渔港视频",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "视频监控\n（9个厂家设备）",
          },
          {
            tid: "1-3-4",
            name: "船检站视频",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-5",
            name: "执法船视频",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-6",
            name: "潮位站（观测站）",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "验潮站",
          },
          {
            tid: "1-3-7",
            name: "简易站",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-8",
            name: "船舶修造经营主体",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "修造主体",
          },
          {
            tid: "1-3-9",
            name: "海岸防护工程",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "工程实施",
          },
          {
            tid: "1-3-10",
            name: "避灾点",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-11",
            name: "中心渔港",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "港岙口",
          },
          {
            tid: "1-3-12",
            name: "一级渔港",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-13",
            name: "二级渔港",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-14",
            name: "三级渔港",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-15",
            name: "内陆渔港",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-16",
            name: "避风锚地",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-3-17",
            name: "岙口",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
        ],
      },
      {
        tid: "1-4",
        name: "海域",
        level: 2,
        count: 35,
        children: [
          {
            tid: "1-4-1",
            name: "大浮标",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-2",
            name: "小浮标",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-3",
            name: "渔排基",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-4",
            name: "综合探测设备",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-5",
            name: "船基",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-6",
            name: "生物与化学采样设备",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-7",
            name: "海岛基",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-8",
            name: "风电基",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-9",
            name: "在册渔船",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "船舶",
          },
          {
            tid: "1-4-10",
            name: "乡镇船舶",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-11",
            name: "执法艇（船）",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-12",
            name: "无人船",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-13",
            name: "省外船舶",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-14",
            name: "渔场",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "海洋空间",
          },
          {
            tid: "1-4-15",
            name: "渔区",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-16",
            name: "养殖现状",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-17",
            name: "海洋功能区划",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-18",
            name: "大陆领海基线",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-19",
            name: "缓冲水域",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-20",
            name: "风场",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "网格预报",
          },
          {
            tid: "1-4-21",
            name: "浪场",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-22",
            name: "流场",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-23",
            name: "温度场",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-24",
            name: "盐场",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-25",
            name: "台风浪场",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-26",
            name: "台风路径",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
            deviceCategory: "预报产品",
          },
          {
            tid: "1-4-27",
            name: "海浪预报",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-28",
            name: "潮汐预报",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-29",
            name: "航线预报",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-30",
            name: "城市预报",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-31",
            name: "卫星云图",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-32",
            name: "渔业海况",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-33",
            name: "海水浴场",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-34",
            name: "养殖区",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-4-35",
            name: "渔港群",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
        ],
      },
      {
        tid: "1-5",
        name: "潜域",
        level: 2,
        count: 2,
        children: [
          {
            tid: "1-5-1",
            name: "海床基",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
          {
            tid: "1-5-2",
            name: "水下机器人",
            runStatus: "stop",
            devMode: "component",
            createTime: "2024-01-07 8:40:00",
            level: 3,
          },
        ],
      },
    ],
  },
]); // 任务树数据mock

// 是否显示脚本编辑器面板
const isShowScript = computed(() => {
  return currentDevMode.value === "script" || currentToolbarMode.value === "script";
});

watch(
  () => currentTaskData.value,
  (newTask) => {
    if (newTask) {
      currentDevMode.value = newTask.devMode;
      currentToolbarMode.value = newTask.devMode === "component" ? "component" : "script";
      currentTheme.value = newTask.devMode === "component" ? "chrome" : "monokai";
      currentNodeKey.value = newTask.tid;
    }
  },
  { immediate: true }
);

// 新建任务
const handleChangeDevMode = (mode) => {
  activePreviewTab.value = "node-config";
  currentDevMode.value = mode;
};

// 处理流程图节点选中事件
const handleNodeSelected = (nodeData) => {
  selectedNode.value = nodeData;
  activePreviewTab.value = nodeData ? "node-info" : "data-preview";
};

// 处理配置按钮点击
const handleConfigClick = (row) => {
  console.log("处理配置按钮点击", row);
  // 更新当前任务
  currentTaskData.value = row;
  // 切换到非表格模式
  isShowTable.value = false;

  updateTopTabs(row);
  updateTaskData(row.tid);
};

// 更新顶部标签页
const updateTopTabs = (row: TaskData) => {
  const existingTabIndex = topTabs.value.findIndex((tab) => tab.id === row.tid);
  if (existingTabIndex === -1) {
    // 添加新标签页
    topTabs.value.push({
      id: row.tid,
      closable: true,
      ...row,
    });
  }
  // 激活当前标签页
  activeTopTab.value = row.tid;
};

// 更新当前中间区域流程图或者编辑器数据
const updateTaskData = (tid: string) => {
  const currentTaskData = flowChartData.value[tid];
  currentFlowData.value = currentTaskData?.component || { nodes: [], edges: [] };
  scriptCode.value = currentTaskData?.script || "";
};

// 处理工具栏模式切换
const handleToolbarModeChange = (mode) => {
  currentToolbarMode.value = mode;
  updateTaskData(currentNodeKey.value);
};

// 处理缩小
const handleZoomOut = () => {
  flowChartRef.value.zoomOut();
  zoomLevel.value = flowChartRef.value.zoomLevel;
};

// 处理放大
const handleZoomIn = () => {
  flowChartRef.value.zoomIn();
  zoomLevel.value = flowChartRef.value.zoomLevel;
};

// 处理重置画布
const handleResetCanvas = () => {
  flowChartRef.value.resetCanvas();
  zoomLevel.value = 100;
};

// 处理保存
const handleSave = () => {
  // 这里可以添加保存的逻辑
  isSaved.value = true;
  ElMessage.success("保存成功");
};

// 处理运行
const handleRun = () => {
  // 这里可以添加运行的逻辑
  if (isSaved.value) {
    ElMessage.success("运行成功");
  }
};

// 处理全屏
const handleFullscreen = () => {
  // 这里可以添加全屏的逻辑
  isFullscreen.value = !isFullscreen.value;
  console.log("全屏切换:", isFullscreen.value);
};

// 处理主题切换
const handleThemeToggle = () => {
  currentTheme.value = currentTheme.value === "monokai" ? "chrome" : "monokai";
};

// 处理返回
const handleBack = () => {
  // 切换到表格列表模式
  isShowTable.value = true;
};

// 添加任务
const addTask = () => {
  ElMessage.success("添加任务功能待实现");
};

// 处理任务节点点击
const handleTaskNodeClick = (node: TaskData) => {
  console.log("任务节点点击", node);

  if (isShowTable.value) {
    // 更新当前选中的树节点
    currentNodeKey.value = node.tid;
  } else {
    // 只有叶子节点才能打开新的Tab面板
    if (node.level !== 3) return;

    currentTaskData.value = node;
    updateTopTabs(node);
    updateTaskData(node.tid);
  }
};

// 处理算子拖拽开始
const handleOperatorDragStart = (event, operator) => {
  event.dataTransfer.effectAllowed = "move";
  event.dataTransfer.setData("application/reactflow", JSON.stringify(operator));
};

// 处理顶部Tab关闭
const handleTopTabRemove = (key) => {
  const index = topTabs.value.findIndex((tab) => tab.id === key);
  if (index > -1) {
    topTabs.value.splice(index, 1);

    // 如果关闭的是当前激活的Tab，切换到第一个Tab
    if (activeTopTab.value === key && topTabs.value.length > 0) {
      const id = topTabs.value[0].id;
      handleTopTabChange(id);
    }
  }
  // 如果关闭的是最后一个tab，则显示表格列表模式
  if (topTabs.value.length === 0) {
    handleBack();
  }
};

// 处理顶部Tab切换
const handleTopTabChange = (key) => {
  activeTopTab.value = key;
  currentTaskData.value = topTabs.value.find((tab) => tab.id === key);
  updateTaskData(key);
};

onMounted(() => {
  // 默认选中第一个树节点
  if (taskTreeData.value && taskTreeData.value.length > 0) {
    const firstTreeNode = taskTreeData.value[0];
    currentNodeKey.value = firstTreeNode.tid;
  }
});
</script>

<style scoped lang="scss">
.data-deal {
  display: flex;
  width: 100%;
  height: 100%;
  background-color: #f5f7fa;

  .right-panel {
    height: 100%;
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .top-container {
    width: 100%;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0px 15px;
    background-color: #fff;
    border-bottom: 1px solid #e4e7ed;
    :deep(.el-tabs) {
      width: calc(100% - 60px);
      .el-tabs__header {
        margin: 10px 0;
      }
    }

    .top-operation-buttons {
      display: flex;
      gap: 8px;
    }
  }

  :deep(.el-segmented) {
    border: 1px solid #e4e7ed;
    background-color: var(--el-segmented-item-hover-bg-color);
    .el-segmented__item-selected {
      background-color: #fff;
    }
    .el-segmented__item.is-selected {
      color: var(--el-color-primary);
    }
    .el-segmented__item {
      border-radius: 2px;
      background-color: var(--el-segmented-item-hover-bg-color);
    }
  }

  /* 工具栏样式 */
  .toolbar-container {
    width: 100%;
    padding: 5px 15px;
    background-color: #f5f7fa;
    border-bottom: 1px solid #e4e7ed;
    transition: all 0.3s ease;
  }

  .toolbar-divider {
    width: 1px;
    height: 18px;
    background-color: #e4e7ed;
    margin: 0 0px;
  }

  .script-name {
    padding: 8px 10px;
  }

  .script-editor {
    height: 100%;
  }

  .monokai-theme {
    background-color: #1f1f1f;
    border-bottom: 1px solid #333;
    color: #ebedf0;
    :deep(.el-icon) {
      color: #ebedf0;
    }
  }

  /* 中间容器样式 */
  .middle-container {
    width: 100%;
    height: calc(100% - 305px - 61px);
    // overflow: hidden;
    .middle-container-left {
      width: calc(100% - 55px);
      height: 100%;
      .content {
        height: calc(100% - 35px);
        width: 100%;
      }
    }
  }
}
</style>
