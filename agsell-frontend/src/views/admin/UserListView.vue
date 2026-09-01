<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listUsers, updateUserStatus } from '@/api/admin'
import type { AdminUserListItemVO } from '@/types'

const loading = ref(false)
const userList = ref<AdminUserListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')

async function fetchList() {
  loading.value = true
  try {
    const res = await listUsers({
      pageNum: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
    })
    userList.value = res.records
    total.value = res.total
  } catch {
    // interceptor already shows message
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function handleReset() {
  keyword.value = ''
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

async function handleStatusChange(row: AdminUserListItemVO, status: number) {
  try {
    await updateUserStatus(row.id, { status })
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
  } catch {
    fetchList()
  }
}

function formatTime(time: string | null): string {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="{ keyword }" @submit.prevent="handleSearch">
        <el-form-item label="搜索">
          <el-input
            v-model="keyword"
            placeholder="昵称 / 手机号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">
            
            搜索
          </el-button>
          <el-button @click="handleReset">
            
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">用户列表</span>
        </div>
      </template>

      <el-table :data="userList" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loginTime" label="最后登录" width="160">
          <template #default="{ row }">
            {{ formatTime(row.loginTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="启用"
              inactive-text="禁用"
              @change="(val: boolean) => handleStatusChange(row, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <span class="total-text">共 {{ total }} 条记录</span>
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          :hide-on-single-page="true"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page { min-height: 100%; }

.search-card { margin-bottom: 1rem; }

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 1rem;
  font-weight: 600;
  color: #303133;
}

.pagination-wrap {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 1rem;
}

.total-text {
  font-size: 0.875rem;
  color: #606266;
}
</style>
