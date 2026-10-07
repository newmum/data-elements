<template>
  <el-drawer
    v-model="isVisible"
    :title="drawerTitle"
    size="800"
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <div class="tag-content">
      <u-title name="选择标签类型"></u-title>
      <div class="mode-selection">
        <div
          v-for="mode in modes"
          :key="mode.id"
          :class="['mode-card', { active: selectedMode === mode.id }]"
          :style="{ cursor: readOnly ? 'not-allowed' : 'pointer' }"
          @click="!readOnly && (selectedMode = mode.id)"
        >
          <div :class="['icon-wrapper', { active: selectedMode === mode.id }]">
            <Icon :icon="mode.icon" :color="mode.iconColor" :size="24"></Icon>
          </div>
          <div class="mode-info">
            <h4 :class="{ active: selectedMode === mode.id }">{{ mode.name }}</h4>
            <p :title="mode.desc">{{ mode.desc }}</p>
          </div>
          <div v-if="selectedMode === mode.id" class="check-icon flex items-center">
            <Icon icon="el-icon-CircleCheckFilled" :size="18"></Icon>
          </div>
        </div>
      </div>
      <JsonForm
        ref="jsonFormRef"
        class="mt-4 w-full"
        :options="formOptions"
        :rules="formRules"
      ></JsonForm>
      <u-title name="规则逻辑配制" class="mt-4"></u-title>
      <code-editor
        v-model="scriptCode"
        :read-only="readOnly"
        :lang="'sql'"
        style="height: 300px"
      ></code-editor>
      <u-title name="标签值" class="mt-4"></u-title>
      <div class="flex gap-2 w-full" style="flex-wrap: wrap">
        <el-tag
          v-for="tag in dynamicTags"
          :key="tag"
          :closable="!readOnly"
          :disable-transitions="false"
          @close="!readOnly && handleClose(tag)"
        >
          {{ tag }}
        </el-tag>
        <el-input
          v-if="inputVisible && !readOnly"
          ref="InputRef"
          v-model="inputValue"
          style="width: 80px"
          size="small"
          @keyup.enter="handleInputConfirm"
          @blur="handleInputConfirm"
        />
        <el-button v-else-if="!readOnly" class="button-new-tag" size="small" @click="showInput">
          + New Tag
        </el-button>
      </div>
    </div>
    <template #footer>
      <div class="flex gap-3" style="justify-content: end">
        <el-button
          v-if="props.type !== 'detail'"
          type="primary"
          :disabled="loading"
          @click="handleSave"
        >
          <Icon icon="save" class="mr-2" />
          保存
        </el-button>
        <el-button :icon="'el-icon-Close'" @click="handleCancel">取消</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, h } from "vue";
import { FormUtils } from "@/utils/form";
import type { InputInstance } from "element-plus";
import { useDictStore } from "@/store";

const tagUpdateFreq = useDictStore().getDictItems("tagUpdateFreq");
// Props
const props = defineProps<{
  visible: boolean;
  type: "add" | "detail";
  categoryId: string;
  data?: any;
  treeData: any[];
}>();

// Emits
const emit = defineEmits<{
  (e: "close"): void;
}>();

const readOnly = computed(() => props.type === "detail");
const drawerTitle = computed(() => {
  return props.type === "add" ? "创建标签" : "标签详情";
});

// State
const isVisible = ref(false);
const jsonFormRef = ref<any>(null);
const scriptCode = ref("");
const selectedMode = ref("0");
const modes = [
  {
    id: "0",
    name: "基础属性标签",
    desc: "基于原始元数据属性直接映射，适用于性别、地域等静态特征",
    icon: "db",
    iconColor: "#3b82f6",
    // iconColor: "#f97316",
  },
  {
    id: "1",
    name: "行为特征标签",
    desc: "基于多维收祝行为事件进行统计计算，适用于活跃度、偏好等动态特征",
    icon: "shandian",
    iconColor: "#f97316",
    // iconColor: "#3b82f6",
  },
];
const inputValue = ref("");
const dynamicTags = ref([]);
const inputVisible = ref(false);
const InputRef = ref<InputInstance>();
const loading = ref(false);
const formOptions = {
  form: {
    labelWidth: "100px",
  },
};
const formRules = FormUtils.fixJson([
  {
    type: "UTitle",
    props: {
      name: "基础信息配置",
    },
  },
  {
    type: "input",
    field: "tagName",
    title: "标签名称",
    $required: true,
    props: {
      placeholder: "如：高潜付费受众",
      disabled: readOnly.value,
    },
  },
  {
    field: "tagCode",
    title: "标签标识",
    type: "input",
    $required: true,
    props: {
      disabled: readOnly.value,
    },
    renderSlots: {
      append() {
        return h("div", {
          class: "i-svg:idea cursor-pointer",
          style: "font-size: 18px;",
          title: "点击生成随机标识",
          onClick: async () => {
            jsonFormRef.value.setValue({
              tagCode: `tag_${Math.random().toString(36).substring(2, 10)}`,
            });
          },
        });
      },
    },
  },
  {
    field: "category",
    title: "所属类目",
    type: "cascader",
    props: {
      options: props.treeData,
      props: {
        label: "name",
        value: "tid",
        checkStrictly: true,
        checkOnClickNode: true,
      },
      disabled: readOnly.value,
    },
    style: {
      width: "100%",
    },
  },
  {
    field: "updateFrequency",
    title: "更新频率",
    type: "select",
    props: {
      options: tagUpdateFreq,
      disabled: readOnly.value,
    },
  },
  {
    type: "input",
    field: "description",
    title: "业务描述说明",
    props: {
      type: "textarea",
      maxlength: 255,
      rows: 4,
      disabled: readOnly.value,
      placeholder: "描述该标签的业务意义，核心算法逻辅及建议应用场景...",
    },
  },
]);

const handleClose = (tag: string) => {
  dynamicTags.value.splice(dynamicTags.value.indexOf(tag), 1);
};

const showInput = () => {
  inputVisible.value = true;
  nextTick(() => {
    InputRef.value!.input!.focus();
  });
};

const handleInputConfirm = () => {
  if (inputValue.value) {
    dynamicTags.value.push(inputValue.value);
  }
  inputVisible.value = false;
  inputValue.value = "";
};

// Initialize form data
const initFormData = () => {
  if (props.type === "add") {
    nextTick(() => {
      jsonFormRef.value?.clearValue();
      jsonFormRef.value?.setValue({
        category: props.categoryId,
      });
    });
  } else if (props.type === "detail" && props.data) {
    nextTick(() => {
      jsonFormRef.value?.setValue(props.data);
      jsonFormRef.value?.setValue({
        category: props.data.categoryId,
      });
      dynamicTags.value = props.data.tags || [];
      selectedMode.value = props.data.tagType || "0";
      inputVisible.value = false;
    });
  }
};

// Watch for prop changes
watch(
  () => props.visible,
  (newVisible) => {
    isVisible.value = newVisible;
    if (newVisible) {
      initFormData();
    }
  },
  { immediate: true }
);
// Handle cancel
const handleCancel = () => {
  emit("close");
};

// Handle save
const handleSave = async () => {
  try {
    loading.value = true;
    const formData = await jsonFormRef.value?.validate().then(() => {
      return jsonFormRef.value?.formData();
    });
    console.log(formData, selectedMode.value, scriptCode.value, dynamicTags.value);
    emit("close", true);
  } catch (error) {
    console.error("保存标签失败:", error);
  } finally {
    loading.value = false;
  }
};
</script>

<style lang="scss" scoped>
.tag-content {
  width: 100%;
  .mode-selection {
    display: flex;
    justify-content: space-between;
    width: 100%; /* 确保不超出父容器宽度 */
    box-sizing: border-box; /* 确保内边距和边框不增加总宽度 */

    .mode-card {
      width: 48.5%;
      box-sizing: border-box; /* 确保内边距和边框不增加总宽度 */
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
}
</style>
