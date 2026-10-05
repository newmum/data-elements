<template>
  <el-dialog
    v-model="visible"
    v-bind="{
      width,
      ...$attrs,
    }"
    @open="handleOpen"
    @close="handleClose"
  >
    <!-- 内容插槽 -->
    <slot>这是一个对话框</slot>

    <template v-if="showFooter" #footer>
      <slot name="footer">
        <el-button v-if="showCancelButton" @click="handleClose">
          {{ cancelButtonText }}
        </el-button>
        <el-button
          v-if="showConfirmButton"
          type="primary"
          :loading="confirmLoading"
          @click="handleConfirm"
        >
          {{ confirmButtonText }}
        </el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
interface DialogProps {
  modelValue: boolean;
  width?: string;
  showFooter?: boolean;
  cancelButtonText?: string;
  confirmButtonText?: string;
  showCancelButton?: boolean;
  showConfirmButton?: boolean;
  confirmLoading?: boolean;
}

const props = withDefaults(defineProps<DialogProps>(), {
  width: "50%",
  showFooter: true,
  cancelButtonText: "取消",
  confirmButtonText: "确定",
  showCancelButton: true,
  showConfirmButton: true,
});

const emit = defineEmits(["update:modelValue", "open", "close", "confirm"]);

const visible = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit("update:modelValue", value);
  },
});

const handleOpen = () => {
  emit("open");
};

const handleClose = () => {
  visible.value = false;
  emit("close");
};

// 确认按钮点击
const handleConfirm = () => {
  emit("confirm");
};
</script>

<style scoped></style>
