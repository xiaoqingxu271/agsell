<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAiTickets, handleAiTicket } from '@/api/admin'
import type { AdminAiTicketVO, AiTicketHandleRequest } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const ticketList = ref<AdminAiTicketVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const statusFilter = ref<number | null>(null)

const STATUS_OPTIONS = [
  { label: '待处理', value: 0, tag: 'warning' },
  { label: '已处理', value: 1, tag: 'success' },
]

function statusTag(status: number): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  return (STATUS_OPTIONS.find((o) => o.value === status)?.tag ?? 'info') as
    | 'primary'
    | 'success'
    | 'warning'
    | 'info'
    | 'danger'
}

function statusLabel(status: number): string {
  return STATUS_OPTIONS.find((o) => o.value === status)?.label ?? '未知'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listAiTickets({
      pageNum: page.value,
      pageSize: pageSize.value,
      status: statusFilter.value === null ? undefined : statusFilter.value,
    })
    ticketList.value = res.records
    total.value = Number(res.total)
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

function handleReset() {
  statusFilter.value = null
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

function formatTime(time: string | null): string {
  return time ? time.replace('T', ' ').substring(0, 19) : '-'
}

// ── 处理弹窗 ──
const handleVisible = ref(false)
const current = ref<AdminAiTicketVO | null>(null)
const handleRemark = ref('')
const handleLoading = ref(false)

function openHandle(row: AdminAiTicketVO) {
  current.value = row
  handleRemark.value = ''
  handleVisible.value = true
}

async function submitHandle() {
  if (!current.value) return
  await ElMessageBox.confirm(
    `确认把该工单标记为已处理？处理后会从待办徽标中消失。`,
    '工单处理',
    { type: 'warning', confirmButtonText: '确认已处理' },
  )
  const payload: AiTicketHandleRequest = {
    remark: handleRemark.value.trim() || undefined,
  }
  handleLoading.value = true
  try {
    await handleAiTicket(Number(current.value.id), payload)
    ElMessage.success('工单已处理')
    handleVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    handleLoading.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <PageHeader
      title="AI 转人工工单"
      description="AI 客服无法解决或用户明确要求人工时落的工单，处理后从待办中消失"
    />

    <!-- 搜索栏 -->
    <el-card shadow="never" class="admin-search-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">工单列表</span>
        </div>
      </template>

      <el-table class="admin-table" :data="ticketList" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="工单号" min-width="80" align="center">
          <template #default="{ row }">{{ row.id }}</template>
        </el-table-column>
        <el-table-column label="用户" min-width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.userId !== null && row.userId !== undefined">UID {{ row.userId }}</span>
            <el-tag v-else type="info" size="small">游客</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户原话" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.question }}</template>
        </el-table-column>
        <el-table-column label="转人工原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="会话号" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sessionId }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.handleRemark || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="primary"
              link
              @click="openHandle(row as AdminAiTicketVO)"
            >处理</el-button>
            <span v-else class="handled-text">{{ formatTime(row.handleTime) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="Number(total)"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleVisible" title="处理工单" width="520px">
      <template v-if="current">
        <div class="handle-detail">
          <div class="handle-row"><span class="handle-label">用户原话</span><span>{{ current.question }}</span></div>
          <div class="handle-row"><span class="handle-label">转人工原因</span><span>{{ current.reason || '-' }}</span></div>
          <div class="handle-row">
            <span class="handle-label">会话号</span><span>{{ current.sessionId }}</span>
          </div>
          <div class="handle-row" v-if="current.userId !== null && current.userId !== undefined">
            <span class="handle-label">用户</span><span>UID {{ current.userId }}</span>
          </div>
          <div class="handle-row" v-else>
            <span class="handle-label">用户</span><span>游客（未登录咨询）</span>
          </div>
        </div>
        <el-input
          v-model="handleRemark"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="处理备注：如何跟进 / 联系结果（可选）"
        />
      </template>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handleLoading" @click="submitHandle">确认已处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.handled-text {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.handle-detail {
  margin-bottom: 12px;
}

.handle-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.5;
}

.handle-label {
  flex-shrink: 0;
  width: 76px;
  color: var(--el-text-color-secondary);
}
</style>
