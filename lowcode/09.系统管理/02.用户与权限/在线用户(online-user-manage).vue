<template>
  <section class="online-user-page card-container">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="toolbar">
          <div>
            <h2>在线用户</h2>
            <p>查看当前行业租户的有效登录会话，并可强制下线非当前会话。</p>
          </div>
          <div class="toolbar-actions">
            <el-input v-model="keyword" clearable placeholder="账号、姓名或终端" @keyup.enter="load">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" height="calc(100vh - 265px)" empty-text="当前租户暂无在线用户">
        <el-table-column prop="userName" label="账号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="deviceType" label="终端类型" min-width="120"><template #default="{ row }">{{ row.deviceType || '-' }}</template></el-table-column>
        <el-table-column prop="deviceId" label="终端标识" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ row.deviceId || '-' }}</template></el-table-column>
        <el-table-column label="登录时间" min-width="175"><template #default="{ row }">{{ formatTime(row.loginTime) }}</template></el-table-column>
        <el-table-column label="最近活跃" min-width="175"><template #default="{ row }">{{ formatTime(row.lastActiveTime) }}</template></el-table-column>
        <el-table-column label="会话状态" width="105"><template #default="{ row }"><el-tag :type="row.current ? 'success' : 'info'">{{ row.current ? '当前会话' : '在线' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :disabled="row.current" @click="kickout(row)">{{ row.current ? '不可下线' : '强制下线' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><span>共 {{ total }} 个在线会话</span><el-pagination v-model:current-page="pageNum" :page-size="pageSize" layout="prev, pager, next" :total="total" @current-change="load" /></div>
    </el-card>
  </section>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = 20

const formatTime = (value) => {
  if (!value) return '-'
  const time = Number(value) < 10000000000 ? Number(value) * 1000 : Number(value)
  return Number.isNaN(time) ? '-' : new Date(time).toLocaleString('zh-CN', { hour12: false })
}

const load = async () => {
  loading.value = true
  try {
    const result = await $common.post('/sym/user/online/page', { pageNum: pageNum.value, pageSize, keyword: keyword.value })
    const data = result?.data || result || {}
    rows.value = data.list || []
    total.value = Number(data.total || 0)
  } catch (error) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const kickout = async (row) => {
  try {
    await ElMessageBox.confirm(`确认强制下线“${row.realName || row.userName}”的该终端会话吗？`, '强制下线', { type: 'warning' })
    await $common.post('/sym/user/online/kickout', { sessionKey: row.sessionKey })
    ElMessage.success('已强制下线')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  }
}

onMounted(load)
</script>

<style scoped>
.online-user-page { height: 100%; padding: 20px; }
.page-card { height: 100%; }
.toolbar { display:flex; justify-content:space-between; gap:20px; align-items:center; }
.toolbar h2 { margin:0; font-size:18px; color:#1d2a44; }
.toolbar p { margin:7px 0 0; color:#8492a6; font-size:13px; }
.toolbar-actions { display:flex; gap:12px; align-items:center; }
.toolbar-actions .el-input { width:250px; }
.pager { display:flex; justify-content:space-between; align-items:center; padding-top:14px; color:#667085; font-size:13px; }
@media (max-width: 900px) { .toolbar { align-items:flex-start; flex-direction:column; } .toolbar-actions { width:100%; } .toolbar-actions .el-input { flex:1; width:auto; } }
</style>
