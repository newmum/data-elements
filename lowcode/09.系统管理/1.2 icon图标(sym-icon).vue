<template>
 <div class="p-5">
   <div>
     代码 {{`<Icon icon="${iconName}" />`}}
     <copy-button  :text="generateIconCode(iconName)"/>
   </div>
   <icon-select v-model="iconName" />
 </div>
</template>

<script setup lang="ts">
import {ref} from "vue";

const iconName = ref("el-icon-edit");

function generateIconCode(symbol: any) {
  return `<Icon icon="${symbol}" />`;
}
</script>