<!-- 数据预览相关面板 -->
<template>
  <div class="data-preview-panel">
    <el-tabs :model-value="activeTab" @update:model-value="handleTabChange">
      <el-tab-pane label="数据预览" name="data-preview">
        <data-table
          v-if="previewData.length > 0"
          :columns="previewColumns"
          :data="previewData"
          :show-page="false"
          class="data-preview-table"
        />
        <div v-else class="empty-preview">
          <el-empty description="执行任务后的数据预览" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="节点信息" name="node-info">
        <div v-if="selectedNode" class="node-info-container">
          <!-- <el-descriptions :column="1" border>
            <el-descriptions-item label="节点ID">{{ selectedNode.id }}</el-descriptions-item>
            <el-descriptions-item label="节点名称">
              {{ selectedNode.name }}
            </el-descriptions-item>
            <el-descriptions-item label="节点类型">
              {{ selectedNode.type }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ selectedNode.createTime }}
            </el-descriptions-item>
          </el-descriptions> -->
        </div>
        <div v-else class="empty-node-info">
          <el-empty description="点击画布节点查看信息" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="节点配置" name="node-config">
        <div v-if="selectedNode" class="node-config-container">
          <!-- <el-form label-width="100px">
            <el-form-item label="节点名称" required>
              <el-input v-model="localNode.name" @input="handleNodeChange" placeholder="请输入节点名称" />
            </el-form-item>
            <el-form-item label="节点描述">
              <el-input
                v-model="localNode.description"
                type="textarea"
                :rows="3"
                @input="handleNodeChange"
                placeholder="请输入节点描述"
              />
            </el-form-item>
            <el-form-item label="配置参数">
              <el-input
                v-model="localNode.config"
                type="textarea"
                :rows="5"
                @input="handleNodeChange"
                placeholder="请输入配置参数"
              />
            </el-form-item>
          </el-form> -->
        </div>
        <div v-else class="empty-node-config">
          <el-empty description="点击画布节点配置信息" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { defineProps, defineEmits, ref, watch } from "vue";

// 定义组件属性
interface SelectedNode {
  id: string;
  name: string;
  type: string;
  createTime: string;
  description?: string;
  config?: string;
}

const props = defineProps<{
  activeTab: string;
  selectedNode: SelectedNode | null;
}>();

const previewData = ref([
  {
    id: "CCTV-01",
    field1: "CCTV-1 综合频道",
    field2: "《新闻联播》",
    field3: "4.8",
    field4: "18.5",
    field5: "12568000",
    field6: "2025-10-01 19:00:00",
  },
  {
    id: "HUNAN-01",
    field1: "湖南卫视",
    field2: "《快乐大本营》",
    field3: "2.3",
    field4: "9.2",
    field5: "6854000",
    field6: "2025-10-01 20:20:00",
  },
  {
    id: "SHANGHAI-01",
    field1: "东方卫视",
    field2: "《极限挑战》",
    field3: "1.9",
    field4: "7.8",
    field5: "5245000",
    field6: "2025-10-01 21:00:00",
  },
  {
    id: "ZHEJIANG-01",
    field1: "浙江卫视",
    field2: "《奔跑吧兄弟》",
    field3: "2.7",
    field4: "10.5",
    field5: "7986000",
    field6: "2025-10-01 20:00:00",
  },
  {
    id: "JIANGSU-01",
    field1: "江苏卫视",
    field2: "《非诚勿扰》",
    field3: "1.5",
    field4: "6.2",
    field5: "4321000",
    field6: "2025-10-01 20:30:00",
  },
  {
    id: "BRTV-01",
    field1: "北京卫视",
    field2: "《北京新闻》",
    field3: "0.9",
    field4: "3.8",
    field5: "2654000",
    field6: "2025-10-01 18:30:00",
  },
  {
    id: "GUANGDONG-01",
    field1: "广东卫视",
    field2: "《外来媳妇本地郎》",
    field3: "0.7",
    field4: "2.9",
    field5: "2015000",
    field6: "2025-10-01 19:30:00",
  },
  {
    id: "CCTV-05",
    field1: "CCTV-5 体育频道",
    field2: "《体育新闻》",
    field3: "1.2",
    field4: "4.8",
    field5: "3456000",
    field6: "2025-10-01 21:30:00",
  },
  {
    id: "HUBEI-01",
    field1: "湖北卫视",
    field2: "《大王小王》",
    field3: "0.5",
    field4: "2.1",
    field5: "1452000",
    field6: "2025-10-01 22:00:00",
  },
  {
    id: "SICHUAN-01",
    field1: "四川卫视",
    field2: "《我们的朋友》",
    field3: "0.4",
    field4: "1.7",
    field5: "1128000",
    field6: "2025-10-01 20:45:00",
  },
]);
const previewColumns = ref([
  { prop: "id", label: "频道ID" },
  { prop: "field1", label: "频道名称" },
  { prop: "field2", label: "节目名称" },
  { prop: "field3", label: "收视率(%)", align: "center" },
  { prop: "field4", label: "收视份额(%)", align: "center" },
  {
    prop: "field5",
    label: "触达人次",
    align: "center",
    formatter: (row: any, column: any, cellValue: number) => {
      if (cellValue === null || cellValue === undefined) {
        return "0";
      }
      return Number(cellValue).toLocaleString();
    },
  },
  { prop: "field6", label: "时间戳" },
]);

// 定义组件事件
const emit = defineEmits<{
  "update:activeTab": [value: string];
  "update:selectedNode": [value: SelectedNode];
}>();

// 创建本地节点副本
const localNode = ref<SelectedNode | null>(null);

// 监听selectedNode变化，更新本地副本
watch(
  () => props.selectedNode,
  (newNode) => {
    if (newNode) {
      localNode.value = { ...newNode };
    } else {
      localNode.value = null;
    }
  },
  { immediate: true, deep: true }
);

// 处理标签页切换
const handleTabChange = (value: string) => {
  emit("update:activeTab", value);
};

// 处理节点信息变化
// const handleNodeChange = () => {
//   if (localNode.value) {
//     emit("update:selectedNode", localNode.value);
//   }
// };
</script>

<style scoped lang="scss">
.data-preview-panel {
  //   height: 300px;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;

  :deep(.el-tabs__nav-wrap) {
    padding: 0 15px;
  }

  .empty-preview,
  .empty-node-info,
  .empty-node-config {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 250px;
  }
  .data-preview-table,
  .node-info-container,
  .node-config-container {
    height: 250px;
    overflow: auto;
    margin: 0 10px;
  }

  .node-config-container {
    .el-form-item {
      margin-bottom: 16px;
    }
  }
}
</style>
