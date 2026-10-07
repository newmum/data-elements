<template>
  <div class="auth-manage-content card-container flex px-5 pt-5 pb-2">
    <div class="left-section"><AuthLeftPanel @select="handleSubjectSelect" /></div>
    <div class="right-section flex flex-col flex-1 pl-4">
      <div class="flex items-center mb-3">
        <el-button type="primary" plain icon="plus" @click="handleInsert">新增</el-button>
        <el-button type="primary" :loading="saveLoading" :disabled="!selectedSubject || resourceLoading" @click="handleSave"><Icon icon="save" mr-2 />保存</el-button>
      </div>
      <AuthPermTree ref="permTreeRef" :subject="selectedSubject" class="flex-1 min-h-0" />
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
const API = { SAVE_ROLE_RESOURCE: "/sym/auth/saveRoleResource", GET_ROLE_RESOURCE: "/sym/auth/getRoleResource" };
const selectedSubject = ref(null), permTreeRef = ref(), saveLoading = ref(false), resourceLoading = ref(false);
let resourceRequestId = 0;
const handleSubjectSelect = (subject) => { selectedSubject.value = subject; permTreeRef.value?.setCheckedPermissions?.([]); loadRoleResources(); };
const loadRoleResources = async () => {
  if (!selectedSubject.value) return;
  const requestId = ++resourceRequestId, roleId = selectedSubject.value.id;
  resourceLoading.value = true;
  try { const res = await $common.post(API.GET_ROLE_RESOURCE, { roleId }); if (requestId === resourceRequestId && selectedSubject.value?.id === roleId) permTreeRef.value?.setCheckedPermissions?.(res?.resourceIds || []); }
  catch (error) { console.error("获取角色权限失败：", error); ElMessage.warning("获取角色权限失败，请稍后重试"); }
  finally { if (requestId === resourceRequestId) resourceLoading.value = false; }
};
const handleSave = async () => {
  if (!selectedSubject.value || resourceLoading.value) return;
  try { saveLoading.value = true; await $common.post(API.SAVE_ROLE_RESOURCE, { roleId: selectedSubject.value.id, resourceIds: permTreeRef.value?.getCheckedPermissions?.() || [] }); ElMessage.success(`已保存“${selectedSubject.value.name}”的功能权限`); }
  catch (error) { console.error("保存授权失败：", error); ElMessage.error("保存授权失败，请检查后重试"); }
  finally { saveLoading.value = false; }
};
const handleInsert = () => permTreeRef.value?.handleInsert?.();
</script>

<style scoped lang="scss">
.auth-manage-content { width: 100%; height: 100%; .left-section { width: 360px; flex-shrink: 0; height: 100%; overflow: hidden; } .right-section { height: 100%; min-width: 0; overflow: hidden; } }
</style>
