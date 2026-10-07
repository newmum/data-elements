<template>
  <div class="property-panel">
    <el-menu v-model="activeMenu" class="property-menu" @select="handlePropertyMenuSelect">
      <el-menu-item index="task-property">
        <Icon :icon="'el-icon-Setting'" />
        <span>任务属性</span>
      </el-menu-item>
      <el-menu-item index="schedule-setting">
        <Icon :icon="'el-icon-Timer'" />
        <span>调度设置</span>
      </el-menu-item>
      <el-menu-item index="execution-log">
        <Icon :icon="'el-icon-Document'" />
        <span>执行日志</span>
      </el-menu-item>
    </el-menu>

    <!-- 属性面板内容 -->
    <div class="property-content">
      <el-drawer v-model="drawerVisible" title="属性设置" direction="rtl" size="400px">
        <div v-if="activeMenu === 'task-property'">
          <el-form label-width="80px">
            <el-form-item label="任务名称" required>
              <el-input v-model="taskForm.name" placeholder="请输入任务名称" />
            </el-form-item>
            <el-form-item label="任务描述">
              <el-input
                v-model="taskForm.description"
                type="textarea"
                :rows="3"
                placeholder="请输入任务描述"
              />
            </el-form-item>
            <el-form-item label="任务状态">
              <el-select v-model="taskForm.status" placeholder="请选择任务状态">
                <el-option label="启用" value="enabled" />
                <el-option label="禁用" value="disabled" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="activeMenu === 'schedule-setting'">
          <el-form label-width="80px">
            <el-form-item label="调度类型" required>
              <el-select v-model="scheduleForm.type" placeholder="请选择调度类型">
                <el-option label="手动" value="manual" />
                <el-option label="定时" value="scheduled" />
              </el-select>
            </el-form-item>
            <el-form-item label="调度时间" v-if="scheduleForm.type === 'scheduled'">
              <el-time-picker
                v-model="scheduleForm.time"
                placeholder="选择时间"
                format="HH:mm:ss"
              />
            </el-form-item>
          </el-form>
        </div>

        <div v-else-if="activeMenu === 'execution-log'">
          <el-table :data="executionLogs" style="width: 100%">
            <el-table-column prop="time" label="时间" width="180" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'success' ? 'success' : 'danger'">
                  {{ scope.row.status === "success" ? "成功" : "失败" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="日志信息" />
          </el-table>
        </div>
      </el-drawer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

// 定义props
// const props = defineProps<{
//   // 可以在这里添加需要从父组件传入的属性
// }>();

// 定义事件
// const emit = defineEmits<{
//   (e: "property-change", data: any): void;
// }>();

// 属性面板相关
const activeMenu = ref("task-property");
const drawerVisible = ref(false);

// 任务表单
const taskForm = ref({
  name: "",
  description: "",
  status: "enabled",
});

// 调度表单
const scheduleForm = ref({
  type: "manual",
  time: "",
});

// 执行日志
const executionLogs = ref([
  { id: "1", time: "2024-01-07 14:30:00", status: "success", message: "任务开始执行" },
  { id: "2", time: "2024-01-07 14:35:00", status: "success", message: "数据接入完成" },
  { id: "3", time: "2024-01-07 14:40:00", status: "success", message: "数据转换完成" },
  { id: "4", time: "2024-01-07 14:45:00", status: "success", message: "任务执行成功" },
]);

// 处理属性菜单选择
const handlePropertyMenuSelect = (key) => {
  activeMenu.value = key;
  drawerVisible.value = true;
};
</script>

<style scoped lang="scss">
.property-panel {
  height: 100%;
  width: 50px;
  background-color: #fff;
  border-left: 1px solid #e4e7ed;

  .property-menu {
    height: 100%;
    border-right: none;

    .el-menu-item {
      height: calc(100% / 3);
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      text-align: center;
      padding: 0 10px;
      padding-left: 10px !important;
      line-height: normal;

      .el-icon {
        margin-bottom: 8px;
        margin-right: 0px;
        font-size: 20px;
      }

      span {
        writing-mode: vertical-rl;
        letter-spacing: 2px;
      }
    }
  }
}
</style>
