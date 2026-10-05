<template>
  <el-timeline class="timeline">
    <el-timeline-item
      v-for="item in list"
      :key="item.nodeCode"
      :color="nodeColor[item.nodeType]"
      :icon="icon[item.nodeType]"
      size="large"
    >
      <div class="font-bold">
        {{ item.nodeName }}
      </div>
      <div>{{ item.approveTime }}</div>
      <div v-if="item.nodeCode != 'end'" class="card">
        <slot name="info" :item="item">
          <div>
            <span class="color-gray-500">机构：</span>
            {{ item.orgName }}
          </div>
          <div>
            <span class="color-gray-500">用户：</span>
            {{ item.userName }}
          </div>
          <div v-if="item.message">
            <span class="color-gray-500">处理意见：</span>
            {{ item.message }}
          </div>
        </slot>
      </div>
    </el-timeline-item>
  </el-timeline>
</template>
<script setup lang="ts">
import { Check, Clock, DocumentAdd, Document, CloseBold } from "@element-plus/icons-vue";

type nodeType = "PASS" | "REJECT" | "WAIT" | "END" | "UNPASS" | "REVOKE" | "ADD";
interface Item {
  nodeCode: string;
  nodeName: string;
  nodeType: nodeType;
  approveTime?: string;
  orgName?: string;
  userName?: string;
  message?: string;
}
withDefaults(
  defineProps<{
    list: Item[];
  }>(),
  {}
);
const icon = {
  WAIT: Clock,
  PASS: Check,
  REJECT: CloseBold,
  END: Check,
  SIGN: Document,
  BACK: CloseBold,
  UNPASS: CloseBold,
  REVOKE: CloseBold,
  ADD: DocumentAdd,
};

const nodeColor: Record<nodeType, string> = {
  WAIT: "var(--el-color-primary)",
  PASS: "var(--el-color-success)",
  REJECT: "var(--el-color-danger)",
  UNPASS: "var(--el-color-danger)",
  REVOKE: "var(--el-color-danger)",
  END: "var(--el-color-success)",
  ADD: "rgb(91,162,243)",
};
</script>

<style scoped lang="scss">
.timeline {
  max-width: 600px;
}
.card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 16px;
  margin-top: 8px;
  background-color: var(--el-bg-color-page);
  border-radius: 8px;
}
.timeline :deep(.el-timeline-item__tail) {
  left: 7px;
}
</style>
