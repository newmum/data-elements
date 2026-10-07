<template>
  <div class="check-tags">
    <div v-if="props.title" class="check-tags-title">{{ props.title }}：</div>
    <div
      :id="'check-tags-' + props.title"
      ref="tagRef"
      :class="['check-tag-items', isFold ? 'check-tags-fold' : 'check-tags-unfold']"
    >
      <span v-if="props.showAll">
        <ElCheckTag
          v-for="tag in [{ value: undefined, label: '不限' }]"
          :key="tag.value"
          :checked="checkedAll"
          @change="(checked) => (checkedAll = checked)"
        >
          {{ tag.label }}
        </ElCheckTag>
      </span>
      <span v-for="tag in options" :key="JSON.stringify(tag.value)">
        <ElCheckTag
          v-if="!tag.hidden"
          :checked="!!checkedMap[JSON.stringify(tag.value)]"
          @change="(checked) => handleChange(tag.value, checked)"
        >
          <span>{{ tag.label }}</span>
        </ElCheckTag>
      </span>
    </div>
    <div v-if="visible" class="check-tags-action">
      <el-button link v-if="isFold" type="primary" @click="isFold = false">
        更多
        <Icon icon="el-icon-ArrowDown"  class="ml-1" />
      </el-button>
      <el-button v-else link type="primary" @click="isFold = true">
        折叠
        <Icon icon="el-icon-ArrowUp" class="ml-1" />
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";

const emit = defineEmits(["update:modelValue", "change"]);
type Option = { value: any; label: string; hidden?: boolean };
const props = withDefaults(
  defineProps<{
    title?: string;
    options?: Option[];
    modelValue?: any[];
    showAll?: boolean;
    multiple?: boolean;
  }>(),
  {
    options: () => [],
    modelValue: () => [],
    showAll: true,
  }
);
const tagRef = ref<HTMLElement | null>(null);
const checkedMap = ref<Record<string, boolean>>({});
const isFold = ref(true);
// 选中值
const selectTags = computed({
  get: () => props.modelValue,
  set(val) {
    const oldValue = props.modelValue;
    emit("update:modelValue", val);
    emit("change", val, oldValue);
  },
});
// 是否全选
const checkedAll = computed({
  get: () => {
    // 无其他选择时，则默认全选
    return selectTags.value?.length === 0;
  },
  set: (val) => {
    if (val) {
      selectTags.value = [];
    }
  },
});
// 生成选中状态map checkedMap
watch(
  () => selectTags.value,
  (list) => {
    checkedMap.value =
      list?.reduce<Record<string, boolean>>((acc, key) => {
        acc[JSON.stringify(key)] = true;
        return acc;
      }, {}) || {};
  },
  { immediate: true, deep: true }
);
const visible = ref();

watch(
  () => props.options,
  () => {
    nextTick(() => {
      visible.value = (tagRef.value?.scrollHeight ?? 0) > 40;
    });
  },
  { immediate: true }
);

const handleChange = (value: any, checked: boolean) => {
  if (checked) {
    selectTags.value = props.multiple ? [...selectTags.value, value] : [value];
  } else {
    selectTags.value = selectTags.value.filter((item) => item !== value);
  }
};
</script>

<style scoped lang="scss">
.check-tags {
  --tag-height: 38px;
  --color: var(--el-color-primary);
  --bg-color: rgba(240, 245, 255);

  font-size: 14px;
  display: grid;
  grid-template-columns: minmax(85px, auto) 1fr auto;
  align-items: flex-start;

  .check-tags-title {
    min-width: 85px;
    font-family: "Source Han Sans CN";
    font-weight: 400;
    font-size: 14px;
    color: #686c80;
    border: 1px solid #00000000;
  }

  .check-tag-items {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0px 8px;

    :deep(.el-check-tag) {
      cursor: pointer;
      padding: 9px 15px;
      border-radius: 8px;
      font-size: 14px;
      font-family: "Source Han Sans CN";
      font-weight: 400;
      color: #323643;
      flex: 1 0 auto;
      background-color: transparent;

      &:hover {
        color: var(--el-color-primary);
        background-color: var(--el-color-info-light-9);
      }
    }

    :deep(.el-check-tag).is-checked {
      color: var(--color);
      background-color: var(--bg-color);
    }
  }

  &-fold {
    overflow: hidden;
    height: var(--tag-height);
  }
  &-unfold {
    overflow-y: scroll;
    overflow-x: hidden;
    max-height: 250px;
    height: auto;
  }
  &-action {
    color: var(--color);
    margin-left: auto;
    // min-width: 60px;
    text-align: center;
    cursor: pointer;
  }
}
</style>
