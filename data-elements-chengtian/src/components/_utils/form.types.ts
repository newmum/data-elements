import type { FormRules } from "element-plus";

export const FormComponent = {
  input: "ElInput",
  select: "DictSelect",
  number: "ElInputNumber",
  switch: "ElSwitch",
  date: "ElDatePicker",
  time: "ElTimePicker",
  title: "UTitle",
  dict: "DictSelect",
} as const;

// 表单项类型定义
export interface FormItemOption {
  label: string;
  value: string | number | boolean;
  disabled?: boolean;
}

export interface UFormItem {
  type?:
    | "slot"
    | "radio"
    | "checkbox"
    | "select-v2"
    | "text"
    | "tree-select"
    | keyof typeof FormComponent;
  prop: string;
  label?: string;
  labelWidth?: string;
  placeholder?: string;
  required?: boolean;
  rules?: FormRules[string];
  disabled?: boolean;
  clearable?: boolean;
  span?: number; // 表单项所占栅格
  style?: string | object; // 表单项样式
  hidden?: boolean; // 表单项是否隐藏
  info?: string; // 提示信息
  logo?: boolean; // 是否显示星号（即使非必填）
  offset: number;
  // input
  showPassword?: boolean;
  prefixIcon?: string;
  suffixIcon?: string;
  // number
  min?: number;
  max?: number;
  step?: number;
  precision?: number;
  controlsPosition?: "right" | "";
  // select/radio/checkbox/tree-select
  options?: FormItemOption[] | string | any[];
  multiple?: boolean;
  filterable?: boolean;
  // tree-select
  checkStrictly?: boolean;
  showCheckbox?: boolean;
  expandOnClickNode?: boolean;
  checkOnClickNode?: boolean;
  // switch
  activeText?: string;
  inactiveText?: string;
  // date/time
  dateType?: "year" | "month" | "date" | "datetime" | "week" | "datetimerange" | "daterange";
  valueFormat?: string;
  // slot
  slot?: boolean;
  slotName?: string;
  props?: Record<string, any>; // 传递给组件的属性
  // title组件传参
  name?: string;
  // 默认值
  defaultValue?: any;
}

export interface UFormProps {
  modelValue: Record<string, any>; // 表单数据
  items?: UFormItem[]; // 表单项配置
  rules?: FormRules; // 表单校验规则
  labelWidth?: string; // 表单标签宽度
  size?: "large" | "default" | "small"; // 表单大小
  itemLabelWidth?: string; // 表单项标签宽度
  labelPosition?: "left" | "right" | "top"; // 表单标签位置
  disabled?: boolean; // 表单项是否禁用
  inline?: boolean; // 表单项是否内联布局
  validateOnRuleChange?: boolean; // 规则变化时是否立即校验
  hideRequiredAsterisk?: boolean; // 是否隐藏必填字段的星号
  scrollToError?: boolean; // 是否滚动到有错误的表单项
  showButtons?: boolean; // 是否显示底部按钮
  buttonsClassName?: string; // 底部按钮的类名
  showSubmitButton?: boolean; // 是否显示提交按钮
  submitButtonText?: string; // 提交按钮的文本
  submitLoading?: boolean; // 提交按钮的加载状态
  showResetButton?: boolean; // 是否显示重置按钮
  resetButtonText?: string; // 重置按钮的文本
  showCancelButton?: boolean; // 是否显示取消按钮
  cancelButtonText?: string; // 取消按钮的文本
}

export interface UFormEmit {
  (e: "update:modelValue", value: Record<string, any>): void;
  (e: "submit", formData: Record<string, any>): void;
  (e: "reset"): void;
  (e: "cancel"): void;
  (e: "validate", isValid: boolean): void;
  (e: "download", fileName: string, index: number, prop: string): void;
}
