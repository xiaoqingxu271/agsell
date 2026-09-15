<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { listSysLogs } from '@/api/admin'
import type { SysLogVO } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const list = ref<SysLogVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const module = ref('')
const adminName = ref('')

async function fetchList() {
  loading.value = true
  try {
    const res = await listSysLogs({
      pageNum: page.value,
      pageSize: pageSize.value,
      module: module.value || undefined,
      adminName: adminName.value || undefined,
    })
    list.value = res.records
    total.value = res.total
  } catch {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

function formatTime(time: string): string {
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <PageHeader title="操作日志" description="审计管理员关键操作：登录、管理员管理、系统配置等，记录操作人与来源 IP" />

    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">日志列表</span>
          <div class="admin-card-actions">
            <el-input
              v-model="module"
              placeholder="模块（如：管理员管理）"
              clearable
              style="width: 160px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-input
              v-model="adminName"
              placeholder="操作人"
              clearable
              style="width: 140px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="handleSearch">查询</el-button>
          </div>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="190" align="center" show-overflow-tooltip />
        <el-table-column prop="adminName" label="操作人" width="110" align="center" show-overflow-tooltip />
        <el-table-column prop="module" label="模块" width="130" align="center" show-overflow-tooltip />
        <el-table-column prop="action" label="动作" width="130" align="center" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="220" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '—' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="140" align="center" show-overflow-tooltip />
        <el-table-column label="操作时间" width="170" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>

      <div class="admin-pagination">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>
