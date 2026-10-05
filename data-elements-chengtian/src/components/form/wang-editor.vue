<template>
  <form-create
    v-model="formData"
    v-model:api="fapi"
    :rule="rule"
    :option="option"
    @submit="onSubmit"
  ></form-create>
</template>

<script setup>
import { ref } from "vue";
import formCreate from "@form-create/element-ui";

const option = ref(
  formCreate.parseJson(
    '{"submitBtn":{"show":false,"innerText":"提交"},"labelPosition":"right","form":{"inline":false,"hideRequiredAsterisk":false,"labelPosition":"right","size":"default","labelWidth":"125px","labelSuffix":""},"resetBtn":{"show":false,"innerText":"重置"}}'
  )
);
const rule = ref(
  formCreate.parseJson(
    '[{"type":"fcEditor","field":"editor","title":"","info":"","$required":false,"_fc_id":"id_Ftsrmiffws83aec","name":"ref_Frmomiffws83afc","display":true,"hidden":false,"_fc_drag_tag":"fcEditor"}]'
  )
);
const fapi = ref(null);

const emits = defineEmits(["update:modelValue"]);
const props = defineProps({
  modelValue: {
    type: String,
    default: "",
  },
  height: {
    type: String,
    default: "500px",
  },
});
const formData = computed({
  get: () => ({
    editor: props.modelValue,
  }),
  set: ({ editor }) => emits("update:modelValue", editor),
});
</script>
