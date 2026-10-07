<template>
  <main class="file-catalog-page card-container flex h-full flex-col px-5 pt-5 pb-2">
    <header class="page-toolbar">
      <h2>数据导入</h2>
      <div class="toolbar-actions">
        <el-button type="primary" icon="plus" @click="openRegister">登记数据目录</el-button>
        <el-input
          v-model.trim="state.keyword"
          class="keyword-input"
          placeholder="请输入数据目录名称、来源表或数据资源名称"
          clearable
          @clear="refresh"
          @keydown.enter="refresh"
        >
          <template #suffix>
            <span class="search-trigger" title="查询" @click="refresh"><Icon icon="search" /></span>
          </template>
        </el-input>
      </div>
    </header>

    <data-table ref="tableRef" show-index :columns="columns" :data="fetchData">
      <template #column-catalogName="{ row }">
        <el-link type="primary" :underline="false" @click="openDetail(row)">
          <Icon icon="menu-cata" class="mr-1" />{{ row.catalogName || row.tableNameCn || row.tableName }}
        </el-link>
      </template>
      <template #column-sourceTableName="{ row }">
        <span>{{ row.sourceTableName || row.tableName || '-' }}</span>
      </template>
      <template #column-assetStatus="{ row }">
        <el-tag :type="Number(row.assetStatus) === 2 ? 'success' : 'warning'" effect="light">
          {{ Number(row.assetStatus) === 2 ? '已登记' : '待登记' }}
        </el-tag>
      </template>
    </data-table>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useRegisterModal } from '@/composables';

const router = useRouter();
const tableRef = ref();
const { openRegisterModal } = useRegisterModal();
const state = reactive({ keyword: '' });

const refresh = () => tableRef.value?.refresh(true);
const openRegister = () => openRegisterModal({ registerClass: 'fileCatalog', registerData: {} });
const openDetail = (row) => {
  router.push({
    path: '/register/register-sjdr/detail',
    query: { id: row.tid || row.id, type: 'catalog', title: row.catalogName || row.tableNameCn || row.tableName },
  });
};

const fetchData = async ({ pageNo: pageNum, pageSize }) => {
  const result = await $common.post('/dst/catalog/page', {
    pageNum,
    pageSize,
    sortField: 'updatedTime',
    sortDir: 'desc',
    conditions: state.keyword
      ? [{ field: 'keyword', value: state.keyword, type: 'like' }]
      : [],
  });
  return { list: result?.list || [], total: Number(result?.total || 0) };
};

const columns = [
  { label: '数据目录名称', prop: 'catalogName', minWidth: 260 },
  { label: '关联数据资源', prop: 'sourceTableName', minWidth: 220 },
  { label: '业务类型', prop: 'businessType', width: 120 },
  { label: '登记时间', prop: 'regTime', type: 'date', width: 180 },
  { label: '状态', prop: 'assetStatus', width: 110, align: 'center' },
  {
    type: 'buttons',
    label: '操作',
    prop: 'action',
    width: 120,
    buttons: [{ type: 'primary', link: true, label: '查看', click: openDetail }],
  },
];
</script>

<style scoped lang="scss">
.file-catalog-page { min-width: 0; }
.page-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.page-toolbar h2 { margin: 0; color: #1d2939; font-size: 18px; font-weight: 600; }
.toolbar-actions { display: flex; align-items: center; gap: 12px; }
.keyword-input { width: 280px; }
.search-trigger { display: inline-flex; cursor: pointer; color: #1677ff; }
@media (max-width: 900px) { .page-toolbar { align-items: flex-start; flex-direction: column; } .toolbar-actions { width: 100%; } .keyword-input { flex: 1; width: auto; } }
</style>
