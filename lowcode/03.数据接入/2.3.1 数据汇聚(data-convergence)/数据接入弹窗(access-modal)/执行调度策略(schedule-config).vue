<template>
  <div class="schedule-config">
    <div class="card-content">
      <!-- 模式选择 -->
      <div class="mode-selection">
        <div
          v-for="mode in modes"
          :key="mode.value"
          :class="['mode-card', { active: selectedMode === mode.value }]"
          @click="selectedMode = mode.value"
        >
          <div :class="['icon-wrapper', { active: selectedMode === mode.value }]">
            <Icon :icon="mode.icon" :color="mode.iconColor" :size="24"></Icon>
          </div>
          <div class="mode-info">
            <h4 :class="{ active: selectedMode === mode.value }">{{ mode.label }}</h4>
            <p>{{ mode.desc }}</p>
          </div>
          <div v-if="selectedMode === mode.value" class="check-icon flex items-center">
            <Icon icon="el-icon-CircleCheckFilled" :size="18"></Icon>
          </div>
        </div>
      </div>

      <!-- 动态配置区域 -->
      <div v-if="[1, 2].includes(selectedMode)" class="config-content">
        <!-- 周期调度配置 -->
        <div v-if="selectedMode === 1" class="periodic-config">
          <JsonForm ref="periodicFormRef" :rules="periodicFormRules" :options="formOptions" />
        </div>

        <!-- Cron表达式配置 -->
        <div v-if="selectedMode === 2" class="cron-config">
          <JsonForm ref="cronFormRef" :rules="cronFormRules" :options="cornFormOptions">
            <template #field-cronExpress="scope">
              <div class="flex items-center w-full gap-2">
                <el-input
                  v-model="scope.model.value"
                  placeholder="* * * * * ?"
                  @input="handleExpressionChange"
                  @change="handleExpressionChange"
                />
                <el-button class="predict-btn" plain @click="predictCron">最近 5 次预测</el-button>
              </div>
            </template>
          </JsonForm>
          <div class="form-extra">
            <a href="https://crontab.guru/" target="_blank" class="help-link">在线校验工具</a>
          </div>
          <div v-if="currentExpression" class="info-card flex items-center">
            <Icon icon="info" :color="'#3b82f6'"></Icon>
            <div class="info-text">
              Cron 表达式
              <span class="cron-expression">{{ currentExpression }}</span>
              表示
              <span class="execution-time">{{ cronDescription }}</span>
              触发任务。
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from "vue";
import { useDictStore } from "@/store";

const props = defineProps({
  info: {
    type: Object,
    default: () => ({}),
  },
});

// interface Mode {
//   scheduleStrategy?: number;
//   scheduleCycle?: number;
//   scheduleRunning?: string;
//   scheduleRunningStart?: string;
//   scheduleRunningEnd?: string;
//   scheduleFaildRetry?: number;
//   cronExpress?: string;
//   taskStatus?: number;
//   lastRunning?: string;
//   lastStatusValue?: string;
//   endRunning?: string;
//   tenantId?: string;
// }

const selectedMode = ref();
const periodicFormRef = ref();

const modes = useDictStore().getDictItems("scheduleStrategy");
const scheduleCycle = useDictStore().getDictItems("scheduleCycle");

const periodicFormRules = [
  {
    type: "radio",
    field: "scheduleCycle",
    title: "调度周期",
    $required: true,
    props: {
      border: true,
    },
    options: scheduleCycle,
  },
  {
    type: "datePicker",
    field: "scheduleRunning",
    title: "具体执行时间",
    $required: true,
    props: {
      type: "date",
      valueFormat: "YYYY-MM-DD HH:mm:ss",
    },
    style: {
      width: "100%",
    },
  },
  {
    type: "datePicker",
    field: "scheduleTimeRange",
    title: "生效日期起始",
    $required: true,
    props: {
      type: "daterange",
      valueFormat: "YYYY-MM-DD HH:mm:ss",
    },
  },
  {
    type: "select",
    field: "scheduleFaildRetry",
    title: "失败重试次数",
    props: {
      placeholder: "请选择重试次数",
    },
    options: [
      { label: "不重试", value: 0 },
      { label: "1 次", value: 1 },
      { label: "3 次", value: 3 },
    ],
  },
];

const formOptions = {
  col: {
    span: 12,
  },
  row: {
    gutter: 20,
  },
};

const cornFormOptions = {
  form: {
    labelPosition: "top",
  },
};

const cronFormRef = ref();
const currentExpression = ref(""); // 当前 Cron 表达式
const cronDescription = ref(""); // Cron 表达式描述
const cronFormRules = [
  {
    type: "fieldComponent",
    field: "cronExpress",
    title: "Cron 表达式",
    $required: true,
    validate: [
      {
        trigger: "change",
        test: /^\s*([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+([^\s]+)\s*$/,
        message: "无效的 Cron 表达式格式",
      },
    ],
  },
];

// 解析 Cron 表达式
const parseCronExpression = (cron: string) => {
  if (!cron || cron.trim() === "") {
    return "请输入 Cron 表达式";
  }

  try {
    const parts = cron.trim().split(/\s+/);
    if (parts.length !== 6) {
      return "无效的 Cron 表达式格式";
    }

    const [second, minute, hour, dayOfMonth, month, dayOfWeek] = parts;

    // 常见模式解析
    if (
      second === "0" &&
      minute === "0" &&
      hour === "2" &&
      dayOfMonth === "*" &&
      month === "*" &&
      (dayOfWeek === "*" || dayOfWeek === "?")
    ) {
      return "每天凌晨 02:00:00";
    }

    if (
      second === "0" &&
      minute === "0" &&
      hour === "0" &&
      dayOfMonth === "*" &&
      month === "*" &&
      (dayOfWeek === "*" || dayOfWeek === "?")
    ) {
      return "每天凌晨 00:00:00";
    }

    if (
      second === "0" &&
      minute === "0" &&
      hour === "12" &&
      dayOfMonth === "*" &&
      month === "*" &&
      (dayOfWeek === "*" || dayOfWeek === "?")
    ) {
      return "每天中午 12:00:00";
    }

    if (
      second === "0" &&
      minute === "0" &&
      hour === "*" &&
      dayOfMonth === "*" &&
      month === "*" &&
      (dayOfWeek === "*" || dayOfWeek === "?")
    ) {
      return "每小时整点";
    }

    if (
      second === "0" &&
      minute === "*" &&
      hour === "*" &&
      dayOfMonth === "*" &&
      month === "*" &&
      (dayOfWeek === "*" || dayOfWeek === "?")
    ) {
      return "每分钟";
    }

    if (
      second === "*" &&
      minute === "*" &&
      hour === "*" &&
      dayOfMonth === "*" &&
      month === "*" &&
      (dayOfWeek === "*" || dayOfWeek === "?")
    ) {
      return "每秒";
    }

    // 每周特定时间
    if (
      second === "0" &&
      minute === "0" &&
      hour === "9" &&
      dayOfMonth === "*" &&
      month === "*" &&
      dayOfWeek !== "*" &&
      dayOfWeek !== "?"
    ) {
      const dayNames = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
      const dayIndex = parseInt(dayOfWeek);
      if (dayIndex >= 1 && dayIndex <= 7) {
        return `${dayNames[dayIndex % 7]} 上午 09:00:00`;
      }
    }

    // 每月特定日期
    if (
      second === "0" &&
      minute === "0" &&
      hour === "0" &&
      dayOfMonth !== "*" &&
      month === "*" &&
      (dayOfWeek === "*" || dayOfWeek === "?")
    ) {
      const day = parseInt(dayOfMonth);
      if (day >= 1 && day <= 31) {
        return `每月 ${day} 日凌晨 00:00:00`;
      }
    }

    // 通用解析
    let description = "";

    // 时间部分
    if (second === "0" && minute !== "*" && hour !== "*") {
      const hourStr = hour.padStart(2, "0");
      const minuteStr = minute.padStart(2, "0");
      description = `${hourStr}:${minuteStr}:00`;
    } else if (second === "0" && minute === "*" && hour !== "*") {
      const hourStr = hour.padStart(2, "0");
      description = `${hourStr} 点每分钟`;
    } else if (second === "0" && minute === "0" && hour === "*") {
      description = "每小时整点";
    } else if (second === "0" && minute === "*") {
      description = "每分钟";
    } else if (second === "*") {
      description = "每秒";
    }

    // 日期部分
    if (dayOfMonth === "*" && month === "*" && (dayOfWeek === "*" || dayOfWeek === "?")) {
      description = `每天 ${description}`;
    } else if (dayOfMonth !== "*" && month === "*" && (dayOfWeek === "*" || dayOfWeek === "?")) {
      const day = parseInt(dayOfMonth);
      if (day >= 1 && day <= 31) {
        description = `每月 ${day} 日 ${description}`;
      }
    } else if (dayOfMonth === "*" && month !== "*" && (dayOfWeek === "*" || dayOfWeek === "?")) {
      const monthNames = [
        "",
        "1月",
        "2月",
        "3月",
        "4月",
        "5月",
        "6月",
        "7月",
        "8月",
        "9月",
        "10月",
        "11月",
        "12月",
      ];
      const monthIndex = parseInt(month);
      if (monthIndex >= 1 && monthIndex <= 12) {
        description = `${monthNames[monthIndex]} 每天 ${description}`;
      }
    } else if (dayOfWeek !== "*" && dayOfWeek !== "?") {
      const dayNames = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
      const dayIndex = parseInt(dayOfWeek);
      if (dayIndex >= 1 && dayIndex <= 7) {
        description = `${dayNames[dayIndex % 7]} ${description}`;
      }
    }

    return description || "无效的 Cron 表达式";
  } catch (error) {
    console.error("解析 Cron 表达式失败:", error);
    return "无效的 Cron 表达式";
  }
};

const handleExpressionChange = (val: string) => {
  currentExpression.value = val;
  cronDescription.value = parseCronExpression(val);
  cronFormRef.value?.setValue({
    cronExpress: val,
  });
};
const predictCron = () => {
  console.log("Predicting next 5 cron executions...");
};

const updateConfig = (data: any) => {
  selectedMode.value = data.scheduleStrategy || 0;
  if (selectedMode.value === 1) {
    const form = {
      ...data,
      scheduleTimeRange: [data.scheduleRunningStart, data.scheduleRunningEnd],
    };
    periodicFormRef.value?.setValue(form);
  } else if (selectedMode.value === 2) {
    cronFormRef.value?.setValue(data);
  }
};

const getConfig = async () => {
  if (selectedMode.value === 1) {
    try {
      await periodicFormRef.value?.validate();
      const data = await periodicFormRef.value?.getFormData();
      return {
        scheduleStrategy: selectedMode.value,
        ...data,
        scheduleRunningStart: data.scheduleTimeRange[0],
        scheduleRunningEnd: data.scheduleTimeRange[1],
      };
    } catch (error) {
      console.error("周期调度配置校验失败:", error);
      throw new Error("周期调度配置校验失败，请完善必填项");
    }
  } else if (selectedMode.value === 2) {
    try {
      await cronFormRef.value?.validate();
      const data = await cronFormRef.value?.getFormData();

      // 额外验证 Cron 表达式格式
      const cronPattern = /^\s*([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+([^\s]+)\s+([^\s]+)\s*$/;
      if (!cronPattern.test(data.cronExpress.trim())) {
        throw new Error("无效的 Cron 表达式格式");
      }

      return {
        scheduleStrategy: selectedMode.value,
        ...data,
      };
    } catch (error) {
      console.error("Cron表达式配置校验失败:", error);
      throw new Error("Cron表达式配置校验失败，请完善必填项");
    }
  }
  return {
    scheduleStrategy: selectedMode.value,
  };
};

watch(
  () => props.info,
  (newInfo) => {
    if (newInfo) {
      updateConfig(newInfo);
    }
  },
  { immediate: true }
);

onMounted(() => {
  updateConfig(props.info);
});

defineExpose({
  getConfig,
});
</script>

<style lang="scss" scoped>
.schedule-config {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  // animation: fadeIn 0.5s ease-in-out;

  .card-content {
    width: 100%;
    padding: 0 0 15px 0;
    display: flex;
    flex-direction: column;
    gap: 2rem;
  }

  // Mode selection
  .mode-selection {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 1.5rem;

    .mode-card {
      position: relative;
      padding: 1rem;
      border: 1px solid #f1f5f9;
      border-radius: 0.5rem;
      background: white;
      cursor: pointer;
      transition: all 0.2s ease;
      display: flex;
      align-items: center;
      gap: 1rem;

      &:hover {
        border-color: #bfdbfe;
      }

      &.active {
        border-color: #165dff;
        background: rgba(22, 93, 255, 0.05);
        box-shadow:
          0 1px 3px 0 rgba(0, 0, 0, 0.1),
          0 1px 2px 0 rgba(0, 0, 0, 0.06);
      }

      .icon-wrapper {
        width: 2.5rem;
        height: 2.5rem;
        border-radius: 0.5rem;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        transition: transform 0.2s ease;
        background: #f1f5f9;

        &:hover {
          transform: scale(1.05);
        }

        &.active {
          background: white;
          box-shadow:
            0 1px 3px 0 rgba(0, 0, 0, 0.1),
            0 1px 2px 0 rgba(0, 0, 0, 0.06);
        }

        svg {
          width: 20px;
          height: 20px;

          &.orange {
            color: #f97316;
          }

          &.blue {
            color: #3b82f6;
          }

          &.purple {
            color: #8b5cf6;
          }
        }
      }

      .mode-info {
        min-width: 0;

        h4 {
          font-size: 0.875rem;
          font-weight: 600;
          color: #1e293b;
          margin: 0 0 0.25rem 0;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;

          &.active {
            color: #165dff;
          }
        }

        p {
          font-size: 0.6875rem;
          color: #94a3b8;
          margin: 0;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .check-icon {
        margin-left: auto;
        color: #165dff;
      }
    }
  }

  // Config content
  .config-content {
    background: white;
    border: 1px solid #f1f5f9;
    border-radius: 0.5rem;
    padding: 1rem;
    // animation: fadeIn 0.5s ease-in-out;
    .cron-config {
      position: relative;
    }
    .form-extra {
      position: absolute;
      right: 0;
      top: 8px;
    }
    .form-item-label::before {
      content: "*";
      color: var(--el-color-danger);
      margin-right: 4px;
    }
    .cron-input-group {
      display: flex;
      gap: 0.5rem;
      .form-input {
        flex: 1;
      }
    }
    // Info card
    .info-card {
      padding: 1rem;
      background: rgba(22, 93, 255, 0.05);
      border: 1px solid #dbeafe;
      border-radius: 0.5rem;
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;

      .info-text {
        font-size: 0.75rem;
        color: #64748b;
        line-height: 1.4;

        .cron-expression {
          font-family: monospace;
          color: #165dff;
          font-weight: 900;
        }

        .execution-time {
          font-weight: 700;
          color: #334155;
          text-decoration: underline;
          text-decoration-thickness: 2px;
        }
      }
    }
  }
}

// Animations
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
