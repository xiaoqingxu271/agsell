<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAfterSales, getAfterSalesDetail, handleAfterSales } from '@/api/admin'
import type { AdminAfterSalesListItemVO, AdminAfterSalesDetailVO, AfterSalesHandleRequest } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const afterSalesList = ref<AdminAfterSalesListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const statusFilter = ref<number | null>(null)
const afterSalesNoFilter = ref('')
const usernameFilter = ref('')

const STATUS_OPTIONS = [
  { label: '待处理', value: 0, tag: 'warning' },
  { label: '已同意', value: 1, tag: 'success' },
  { label: '已拒绝', value: 2, tag: 'danger' },
  { label: '已撤销', value: 3, tag: 'info' },
]

function statusTag(status: number): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  return (STATUS_OPTIONS.find((o) => o.value === status)?.tag ?? 'info') as 'primary' | 'success' | 'warning' | 'info' | 'danger'
}

function statusLabel(status: number): string {
  return STATUS_OPTIONS.find((o) => o.value === status)?.label ?? '未知'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listAfterSales({
      pageNum: page.value,
      pageSize: pageSize.value,
      status: statusFilter.value === null ? undefined : statusFilter.value,
      afterSalesNo: afterSalesNoFilter.value || undefined,
      username: usernameFilter.value || undefined,
    })
    afterSalesList.value = res.records
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
  afterSalesNoFilter.value = ''
  usernameFilter.value = ''
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

// ── 详情/处理弹窗 ──
const detailVisible = ref(false)
const detail = ref<AdminAfterSalesDetailVO | null>(null)
const detailLoading = ref(false)
const handleRemark = ref('')
const handleLoading = ref(false)

async function openDetail(row: AdminAfterSalesListItemVO) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  handleRemark.value = ''
  try {
    detail.value = await getAfterSalesDetail(row.afterSalesNo)
  } catch {
    // interceptor handles error
  } finally {
    detailLoading.value = false
  }
}

async function submitHandle(agree: boolean) {
  if (!detail.value) return
  const actionText = agree ? '同意退款' : '拒绝'
  if (agree) {
    const isReturnGoods = detail.value.type === 2
    const tip = isReturnGoods
      ? `确认同意退款 ¥${detail.value.refundAmount}？同意后订单将置为已退款，货物退回后库存自动回补。`
      : `确认同意退款 ¥${detail.value.refundAmount}？同意后订单将置为已退款；仅退款不退货物，库存不回补。`
    await ElMessageBox.confirm(
      tip,
      '售后处理',
      { type: 'warning', confirmButtonText: '确认同意' }
    )
  }
  const payload: AfterSalesHandleRequest = { agree, remark: handleRemark.value.trim() || undefined }
  handleLoading.value = true
  try {
    await handleAfterSales(detail.value.afterSalesNo, payload)
    ElMessage.success(`${actionText}成功`)
    detailVisible.value = false
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
    <PageHeader title="售后管理" description="处理售后申请与退款流转" />

    <!-- 搜索栏 -->
    <el-card shadow="never" class="admin-search-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="售后单号">
          <el-input v-model="afterSalesNoFilter" placeholder="请输入单号" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="usernameFilter" placeholder="请输入用户名" clearable style="width: 140px" />
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
          <span class="admin-card-title">售后单列表</span>
        </div>
      </template>

      <el-table class="admin-table" :data="afterSalesList" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="售后单号" min-width="220" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.afterSalesNo }}</template>
        </el-table-column>
        <el-table-column label="订单号" min-width="220" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orderNo }}</template>
        </el-table-column>
        <el-table-column prop="username" label="用户" min-width="200" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.username ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="售后类型" width="130" align="center">
          <template #default="{ row }">{{ row.typeText }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="220" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reasonTypeText }}{{ row.reason ? '：' + row.reason : '' }}</template>
        </el-table-column>
        <el-table-column label="退款金额" width="110" align="center">
          <template #default="{ row }">
            <span class="refund-amount">¥{{ row.refundAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" class="admin-status-tag" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="180" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openDetail(row as AdminAfterSalesListItemVO)">
              详情
            </el-button>
            <el-divider v-if="row.status === 0" direction="vertical" class="admin-action-divider" />
            <el-button
              v-if="row.status === 0"
              link
              type="success"
              size="small"
              class="admin-action-btn"
              @click="openDetail(row as AdminAfterSalesListItemVO)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- 详情/处理弹窗 -->
    <el-dialog v-model="detailVisible" title="售后单详情" width="680px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <!-- 售后信息 -->
          <el-descriptions :column="2" border size="small" title="售后信息" style="margin-bottom: 1rem">
            <el-descriptions-item label="售后单号">{{ detail.afterSalesNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="关联订单">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="申请人">
              {{ detail.username }}（{{ detail.nickname ?? '-' }}）
            </el-descriptions-item>
            <el-descriptions-item label="售后类型">{{ detail.typeText }}</el-descriptions-item>
            <el-descriptions-item label="退款金额">
              <span class="refund-amount">¥{{ detail.refundAmount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="申请原因">{{ detail.reasonTypeText }}</el-descriptions-item>
            <el-descriptions-item label="申请时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="问题描述" :span="2">{{ detail.reason ?? '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.handleTime" label="处理时间">{{ formatTime(detail.handleTime) }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.handleRemark" label="处理意见">{{ detail.handleRemark }}</el-descriptions-item>
          </el-descriptions>

          <!-- 凭证图片 -->
          <div v-if="detail.images && detail.images.length" class="evidence-block">
            <div class="evidence-title">凭证图片</div>
            <el-image
              v-for="(img, idx) in detail.images"
              :key="img"
              :src="img"
              :preview-src-list="detail.images"
              :initial-index="idx"
              fit="cover"
              class="evidence-image"
              preview-teleported
            />
          </div>

          <!-- 订单信息 -->
          <el-descriptions :column="2" border size="small" title="订单信息" style="margin-bottom: 1rem">
            <el-descriptions-item label="订单状态">
              <el-tag type="info" size="small">{{ detail.originalStatusText }}（售后前）</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="实付金额">¥{{ detail.orderPayAmount }}</el-descriptions-item>
            <el-descriptions-item label="收件人">{{ detail.receiver }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ detail.phone }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">{{ detail.address }}</el-descriptions-item>
          </el-descriptions>

          <!-- 商品明细 -->
          <div class="item-table-wrap">
            <div class="evidence-title">商品明细</div>
            <el-table :data="detail.items" size="small" :border="false" style="width: 100%">
              <el-table-column label="商品" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.productName }}{{ row.specName ? '（' + row.specName + '）' : '' }}</template>
              </el-table-column>
              <el-table-column label="单价" width="100" align="center">
                <template #default="{ row }">¥{{ row.price }}</template>
              </el-table-column>
              <el-table-column label="数量" width="80" align="center">
                <template #default="{ row }">×{{ row.quantity }}</template>
              </el-table-column>
              <el-table-column label="小计" width="110" align="center">
                <template #default="{ row }">¥{{ row.subtotal }}</template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 处理表单（仅待处理） -->
          <el-form v-if="detail.status === 0" label-width="80px" style="margin-top: 1rem">
            <el-form-item label="处理意见">
              <el-input
                v-model="handleRemark"
                type="textarea"
                :rows="2"
                placeholder="选填，可说明同意/拒绝的原因"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <template v-if="detail && detail.status === 0">
          <el-button type="danger" :loading="handleLoading" @click="submitHandle(false)">拒绝退款</el-button>
          <el-button type="success" :loading="handleLoading" @click="submitHandle(true)">同意退款</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { min-height: 100%; }

.refund-amount {
  color: #A16207;
  font-weight: 600;
}

.detail-body { min-height: 120px; }

.evidence-block {
  margin-bottom: 1rem;
}

.evidence-title {
  font-size: 14px;
  font-weight: 600;
  color: #14532D;
  margin-bottom: 0.5rem;
}

.evidence-image {
  width: 90px;
  height: 90px;
  border-radius: 8px;
  margin-right: 0.5rem;
  border: 1px solid #E5E7EB;
}

.item-table-wrap { margin-bottom: 0.5rem; }
</style>
