<template>
  <div class="task-finish">
    <el-result icon="success" :title="props.title">
      <template #extra>
        <div v-if="type !== 'serve'" class="task-finish-info">
          {{ type === 'quality' ? '数据质检任务' : '数据接入任务' }}
          <span class="task-finish-info_name">{{ `"${name}"` }}</span>
          已创建成功，系统将按照设定的周期自动执行
        </div>
        <div v-else class="task-finish-info">
          数据服务
          <span class="task-finish-info_name">{{ `"${name}"` }}</span>
          已创建成功
        </div>
        <div v-if="type !== 'serve'" class="task-finish-card">
          <div class="task-finish-card_item">
            <div>任务编码</div>
            <div class="task-finish-card_value">{{ `${code || "——"}` }}</div>
          </div>
          <div class="task-finish-card_item">
            <div>下次执行</div>
            <div class="task-finish-card_value">{{ `${time || "——"}` }}</div>
          </div>
        </div>
        <el-button type="primary" style="margin-top: 20px" @click="toSee">前往查看</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { inject, ref } from "vue";
// 定义props
const props = defineProps({
  name: {
    type: String,
    default: "",
  },
  code: {
    type: String,
    default: "",
  },
  time: {
    type: String,
    default: "",
  },
  title: {
    type: String,
    default: "提交成功",
  },
  type: {
    type: String,
    default: "serve",
  },
});

// 定义事件
const emit = defineEmits<{
  (e: "to-see"): void;
}>();

// 注入action引用
interface ActionRef {
  action: (type: string) => void;
}

const getActionRef = inject<Ref<ActionRef | undefined>>("getActionRef", ref(undefined));

// 前往查看按钮处理函数
const toSee = () => {
  // 触发前往查看事件
  emit("to-see");
  if (getActionRef.value?.action) {
    getActionRef.value.action("cancel");
  }
};
</script>

<style scoped lang="scss">
.task-finish {
  height: 100%;
  text-align: center;
  padding-top: 100px;
  .task-finish-info_name {
    font-weight: 600;
    color: #1b67f8;
  }
  .task-finish-card {
    display: flex;
    justify-content: center;
    align-items: center;
    margin-top: 20px;
    padding: 20px;
    border-radius: 8px;
    background-color: #f8fafc;
    border: 1px solid #e4e7ed;
    .task-finish-card_item {
      flex: 1;
      text-align: left;
      line-height: 1.6;
    }
  }
  .task-finish-card_value {
    font-weight: 600;
    color: #303133;
  }
}
.el-result {
  --el-result-icon-font-size: 72px;
  --el-result-title-font-size: 24px;

  :deep(.el-result__title) {
    font-weight: 500;
  }
}
</style>
