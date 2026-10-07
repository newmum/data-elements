<template>
  <section class="tenant-manage-page card-container flex flex-col px-5 pt-5 pb-2">
    <div class="tenant-filters">
      <el-button type="primary" :icon="Plus" @click="openEditor()">新增租户</el-button>
      <el-input v-model.trim="keyword" clearable placeholder="请输入租户编码或名称" @clear="refresh" @keyup.enter="refresh">
        <template #suffix><el-icon class="tenant-search-icon" title="点击查询" @click="refresh"><Search /></el-icon></template>
      </el-input>
    </div>

    <DataTable ref="dataTableRef" show-index :columns="columns" :data="fetchData">
      <template #column-name="{ row }"><button class="tenant-name-link" type="button" @click="openEditor(row)">{{ row.name }}</button></template>
      <template #column-status="{ row }">
        <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? '启用' : '停用' }}</el-tag>
      </template>
      <template #column-isolationMode="{ row }">{{ row.isolationMode === 'DATABASE' ? '库级隔离' : '行级隔离' }}</template>
      <template #column-updatedTime="{ row }">{{ formatTime(row.updatedTime) }}</template>
    </DataTable>

    <el-drawer v-model="editorVisible" :title="form.tid ? '编辑租户' : '新增租户'" size="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="租户编码" prop="code"><el-input v-model.trim="form.code" :disabled="Boolean(form.tid)" /></el-form-item>
        <el-form-item label="租户名称" prop="name"><el-input v-model.trim="form.name" /></el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="隔离模式"><el-select v-model="form.isolationMode" style="width:100%"><el-option label="行级隔离" value="ROW" /><el-option label="库级隔离" value="DATABASE" /></el-select></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" /></el-form-item>
        <el-form-item label="扩展配置"><el-input v-model="form.jsonConfig" type="textarea" :rows="6" placeholder='可选 JSON，例如 {"dataSourceKey":"public-security"}' /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editorVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-drawer>

    <el-drawer v-model="usersVisible" :title="`${selectedTenant?.name || ''} · 关联用户`" size="760px">
      <div class="user-toolbar"><el-input v-model="userKeyword" clearable placeholder="搜索账号或姓名" @keyup.enter="loadUsers" /><el-button type="primary" :icon="Search" @click="loadUsers">查询</el-button></div>
      <el-table v-loading="usersLoading" :data="users" height="calc(100vh - 220px)">
        <el-table-column prop="userName" label="账号" min-width="140" /><el-table-column prop="realName" label="姓名" min-width="130" /><el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column label="已关联" width="100"><template #default="{ row }"><el-switch :model-value="row.assigned" @change="(value) => changeRelation(row, value)" /></template></el-table-column>
        <el-table-column label="默认租户" width="110"><template #default="{ row }"><el-radio :model-value="defaultUserId" :label="row.userId" :disabled="!row.assigned" @change="() => saveRelation(row, { defaultTenant: true })">默认</el-radio></template></el-table-column>
      </el-table>
    </el-drawer>
  </section>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'

const saving = ref(false), editorVisible = ref(false), usersVisible = ref(false)
const dataTableRef = ref(), keyword = ref('')
const formRef = ref()
const form = reactive({ tid: '', code: '', name: '', status: 1, isolationMode: 'ROW', sortNo: 0, jsonConfig: '' })
const rules = { code: [{ required: true, message: '请输入租户编码', trigger: 'blur' }], name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }] }
const selectedTenant = ref(null), users = ref([]), usersLoading = ref(false), userKeyword = ref(''), defaultUserId = ref('')
const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'

const columns = [
  { prop: 'code', label: '租户编码', minWidth: 150 },
  { prop: 'name', label: '租户名称', minWidth: 180 },
  { prop: 'status', label: '状态', width: 100 },
  { prop: 'isolationMode', label: '隔离模式', width: 120 },
  { prop: 'sortNo', label: '排序', width: 90 },
  { prop: 'updatedTime', label: '更新时间', minWidth: 170 },
  {
    prop: 'operation', label: '操作', type: 'buttons', width: 110, fixed: 'right', buttons: [
      { label: '关联用户', type: 'primary', link: true, click: (row) => openUsers(row) }
    ]
  }
]
const fetchData = async ({ pageNo = 1, pageSize = 20 } = {}) => {
  const result = await $common.post('/sym/tenant/list', { pageNum: pageNo, pageSize, keyword: keyword.value })
  const data = result?.data || result || {}
  return { list: data.list || [], total: Number(data.total || 0) }
}
const refresh = () => {
  dataTableRef.value?.refresh()
}
const openEditor = (row) => {
  Object.assign(form, row ? { tid: row.tid, code: row.code, name: row.name, status: Number(row.status), isolationMode: row.isolationMode || 'ROW', sortNo: Number(row.sortNo || 0), jsonConfig: row.jsonConfig || '' } : { tid: '', code: '', name: '', status: 1, isolationMode: 'ROW', sortNo: 0, jsonConfig: '' })
  editorVisible.value = true
}
const save = async () => {
  await formRef.value?.validate()
  saving.value = true
  try { await $common.post('/sym/tenant/save', { ...form }); ElMessage.success('保存成功'); editorVisible.value = false; refresh() } finally { saving.value = false }
}
const openUsers = async (tenant) => { selectedTenant.value = tenant; userKeyword.value = ''; usersVisible.value = true; await loadUsers() }
const loadUsers = async () => {
  if (!selectedTenant.value) return
  usersLoading.value = true
  try { const result = await $common.post('/sym/tenant/user-page', { targetTenantId: selectedTenant.value.tid, keyword: userKeyword.value }); const data = result?.data || result || {}; users.value = data.list || []; const defaultRow = users.value.find((item) => item.defaultTenant); defaultUserId.value = defaultRow?.userId || '' } finally { usersLoading.value = false }
}
const changeRelation = async (row, assigned) => { if (assigned) await saveRelation(row, { assigned: true }); else { await $common.post('/sym/tenant/unassign-user', { userId: row.userId, targetTenantId: selectedTenant.value.tid }); ElMessage.success('已解除关联'); await loadUsers() } }
const saveRelation = async (row, changes = {}) => {
  await $common.post('/sym/tenant/assign-user', { userId: row.userId, targetTenantId: selectedTenant.value.tid, defaultTenant: changes.defaultTenant ?? row.defaultTenant ?? false })
  ElMessage.success('关联配置已保存')
  await loadUsers()
}
</script>

<style scoped>
.tenant-manage-page { height:100%; min-width:0; }.tenant-filters{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;margin-bottom:20px}.tenant-filters .el-input{width:280px;margin-left:auto}.tenant-search-icon{color:#1677ff;cursor:pointer;font-size:17px}.tenant-search-icon:hover{color:#409eff}.tenant-name-link{padding:0;border:0;background:transparent;color:inherit;cursor:pointer;font:inherit}.tenant-name-link:hover{color:#1677ff;text-decoration:underline}.user-toolbar{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:14px}.user-toolbar .el-input{width:270px}@media(max-width:900px){.tenant-filters .el-input,.user-toolbar .el-input{width:100%;flex:1 1 100%}}
</style>
