<template>
  <el-dialog
    v-model="visible"
    :title="'编辑关系'"
    width="480px"
    :destroy-on-close="true"
    :close-on-click-modal="false"
  >
    <el-form ref="formRef" :model="form" label-width="150px" label-suffix="：">
      <el-form-item
        label="源节点"
        prop="sourceId"
        :rules="[{ required: true, message: '请选择源节点' }]"
      >
        <el-select v-model="form.sourceId" placeholder="请选择" class="w-full">
          <el-option
            v-for="n in nodes"
            :key="n.id"
            :label="`${n.nameCn}（${n.nameEn}）`"
            :value="n.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="目标节点"
        prop="targetId"
        :rules="[{ required: true, message: '请选择目标节点' }]"
      >
        <el-select v-model="form.targetId" placeholder="请选择" class="w-full">
          <el-option
            v-for="n in nodes"
            :key="n.id"
            :label="`${n.nameCn}（${n.nameEn}）`"
            :value="n.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item
        label="关系英文名"
        prop="relNameEn"
        :rules="[{ required: true, message: '请输入关系英文名' }]"
      >
        <el-input v-model="form.relNameEn" placeholder="如 owns" clearable />
      </el-form-item>
      <el-form-item label="关系中文名" prop="relNameCn">
        <el-input v-model="form.relNameCn" placeholder="如 持有" clearable />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleSkipOrCancel">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from "vue";
import type { FormInstance } from "element-plus";
import type { GraphEdge, GraphNode } from "./types";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    edge?: GraphEdge;
    nodes?: GraphNode[];
    defaultSourceId?: string;
  }>(),
  { nodes: () => [] }
);

const emit = defineEmits<{
  (e: "update:modelValue", val: boolean): void;
  (e: "confirm", data: { edge: Omit<GraphEdge, "id">; sourceId?: string; targetId?: string }): void;
  (e: "skip"): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
});

const formRef = ref<FormInstance>();
const loading = ref(false);

const form = ref({
  relNameEn: "",
  relNameCn: "",
  sourceId: "",
  targetId: "",
});

// 回填数据
watch(
  () => props.modelValue,
  (val) => {
    if (!val) return;
    // edit 模式：从已有 edge 回填 source/target；create-after-node 模式：从 defaultSourceId 推断
    const defaultSource = props.edge?.source ?? props.defaultSourceId ?? props.nodes?.[0]?.id ?? "";
    form.value = {
      relNameEn: props.edge?.relNameEn ?? "",
      relNameCn: props.edge?.relNameCn ?? "",
      sourceId: defaultSource,
      targetId: props.edge?.target ?? props.nodes?.find((n) => n.id !== defaultSource)?.id ?? "",
    };
  },
  { immediate: true }
);

const handleSkipOrCancel = (): void => {
  visible.value = false;
};

const handleConfirm = async (): Promise<void> => {
  await formRef.value?.validate();
  loading.value = true;
  try {
    emit("confirm", {
      edge: {
        source: form.value.sourceId,
        target: form.value.targetId,
        relNameEn: form.value.relNameEn,
        relNameCn: form.value.relNameCn,
      },
      sourceId: form.value.sourceId,
      targetId: form.value.targetId,
    });
    visible.value = false;
  } finally {
    loading.value = false;
  }
};
</script>
