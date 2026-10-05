<!--搜索表单-->
<template>
  <div class="search-form">
    <UForm
      v-if="items?.length <= 3"
      ref="queryFormRef"
      v-model="formData"
      :items="[...items, { slot: true, slotName: 'operate', span: 4 }]"
      :show-buttons="false"
      label-width=""
      v-bind="$attrs"
      @submit="emit('submit')"
      @reset="emit('reset')"
    >
      <template #operate>
        <el-form-item :class="{ 'top-btn': $attrs['label-position'] === 'top' }">
          <el-button type="primary" @click="queryFormRef.submit()">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </template>
    </UForm>

    <template v-else>
      <UForm
        ref="queryFormRef"
        v-model="formData"
        :class="['form', { folder: isFold }]"
        :items="items"
        submit-button-text="查询"
        label-width=""
        v-bind="$attrs"
        @submit="emit('submit')"
        @reset="emit('reset')"
      >
        <template #buttons>
          <div class="buttons">
            <el-button type="primary" @click="queryFormRef.submit()">查询</el-button>
            <el-button @click="handleReset">重置</el-button>

            <el-button
              v-if="items?.length > 4"
              link
              type="primary"
              class="rightBtn"
              @click="isFold = !isFold"
            >
              <el-icon>
                <Filter />
              </el-icon>
              高级查询
            </el-button>
          </div>
        </template>
      </UForm>
    </template>
  </div>
</template>

<script setup lang="ts">
import { Filter } from "@element-plus/icons-vue";
import { computed, nextTick, ref } from "vue";

const emit = defineEmits(["update:modelValue", "submit", "reset"]);

const props = withDefaults(
  defineProps<{
    modelValue: any;
    items: any[];
    show?: number;
  }>(),
  {
    show: 5,
  }
);

const queryFormRef = ref();

// 是否折叠表单项
const isFold = ref(true);

// 表单数据双向绑定
const formData = computed({
  get() {
    return props.modelValue;
  },
  set(value) {
    emit("update:modelValue", value);
  },
});

// 重置处理函数
const handleReset = () => {
  queryFormRef.value?.resetFields();
  nextTick(() => {
    emit("submit");
  });
};
</script>

<style scoped lang="scss">
.search-form {
  position: relative;
}

.form {
  :deep(.el-row .el-col) {
    opacity: 1;
    transition: all 0.3s linear;
  }
}

.folder {
  /*折叠状态下 第5个以后的元素隐藏*/
  :deep(.el-row .el-col:nth-child(n + 5)) {
    height: 0;
    overflow-y: hidden;
    opacity: 0;
  }
}

.buttons {
  position: relative;
  display: flex;
  justify-content: center;
}

.rightBtn {
  position: absolute;
  right: 0;
}

.top-btn {
  display: flex;
  height: 100%;
  margin-top: 4px;
}
</style>
