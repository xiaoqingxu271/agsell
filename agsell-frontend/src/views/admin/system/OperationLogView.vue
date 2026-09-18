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

// 游标分页 + 页码跳页混合：cursorMap.get(n) = 第 n 页的查询游标（不存在则按页码 OFFSET 查询）
// 顺序翻页（上一页/下一页相邻）走 keyset 游标，任意跳页走 OFFSET 兜底
const cursorMap = ref<Map<number, { cursorTime?: string; cursorId?: number }>>(new Map())

interface Cursor {
  cursorTime?: string
  cursorId?: number
}

async function fetchPage(targetPage: number) {
  loading.value = true
  try {
    const cursor: Cursor = cursorMap.value.get(targetPage) ?? {}
    const res = await listSysLogs({
      pageSize: pageSize.value,
      module: module.value || undefined,
      adminName: adminName.value || undefined,
      // 有游标走 keyset；无游标（首屏或跳页）走页码 OFFSET
      pageNum: cursor.cursorTime ? undefined : targetPage,
      cursorTime: cursor.cursorTime,
      cursorId: cursor.cursorId,
    })
    list.value = res.records
    // 后端 Long 序列化为字符串（防雪花 ID 精度丢失），必须转 number 否则新版 ElPagination 不渲染
    total.value = Number(res.total)
    page.value = targetPage
    // 记录下一页游标 = 本页最后一条
    const last = res.records[res.records.length - 1]
    if (last) {
      cursorMap.value.set(targetPage + 1, { cursorTime: last.createTime, cursorId: last.id })
    }
  } catch {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  cursorMap.value = new Map()
  fetchPage(1)
}

function handlePageChange(val: number) {
  // 注意：v-model:current-page 已先更新 page，此处 val 与 page 恒相等，不能再做去重判断
  fetchPage(val)
}

function formatTime(time: string): string {
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(() => fetchPage(1))
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
              style="width: 200px"
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
        <el-table-column prop="id" label="ID" width="180" align="center" show-overflow-tooltip />
        <el-table-column prop="adminName" label="操作人" width="120" align="center" show-overflow-tooltip />
        <el-table-column prop="module" label="模块" width="120" align="center" show-overflow-tooltip />
        <el-table-column prop="action" label="动作" width="120" align="center" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="10" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.content || '—' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="100" align="center" show-overflow-tooltip />
        <el-table-column label="操作时间" width="260" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>

      <!-- 游标 + 页码混合分页：相邻翻页走游标，任意跳页走 OFFSET -->
      <div class="admin-pagination">
        <span class="admin-total-text">共 {{ total }} 条记录</span>
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          :hide-on-single-page="false"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>
