<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOrders, getOrderDetail, shipOrder } from '@/api/admin'
import type { AdminOrderListItemVO, AdminOrderDetailVO, OrderShipRequest } from '@/types'

const loading = ref(false)
const orderList = ref<AdminOrderListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const statusFilter = ref<number | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const res = await listOrders({
      pageNum: page.value,
      pageSize: pageSize.value,
      status: statusFilter.value,
      orderNo: keyword.value || undefined,
    })
    orderList.value = res.records
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

function handleReset() {
  keyword.value = ''
  statusFilter.value = null
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

function formatTime(time: string | null): string {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// ── 订单详情弹窗 ──
const detailVisible = ref(false)
const detail = ref<AdminOrderDetailVO | null>(null)

async function openDetail(orderNo: string) {
  try {
    const res = await getOrderDetail(orderNo)
    detail.value = res
    detailVisible.value = true
  } catch {
    // interceptor handles error
  }
}

// ── 发货弹窗 ──
const shipVisible = ref(false)
const shipOrderNo = ref('')
const shipForm = ref<OrderShipRequest>({ logType: '', logNo: '' })
const shipLoading = ref(false)

function openShip(row: AdminOrderListItemVO) {
  shipOrderNo.value = row.orderNo
  shipForm.value = { logType: '', logNo: '' }
  shipVisible.value = true
}

async function handleShipSubmit() {
  if (!shipForm.value.logType?.trim()) {
    ElMessage.warning('请输入物流公司')
    return
  }
  if (!shipForm.value.logNo?.trim()) {
    ElMessage.warning('请输入物流单号')
    return
  }
  shipLoading.value = true
  try {
    await shipOrder(shipOrderNo.value, shipForm.value)
    ElMessage.success('发货成功')
    shipVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    shipLoading.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="{ keyword, statusFilter }" @submit.prevent="handleSearch">
        <el-form-item label="订单号">
          <el-input
            v-model="keyword"
            placeholder="请输入订单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="待付款" :value="0" />
            <el-option label="待发货" :value="1" />
            <el-option label="待收货" :value="2" />
            <el-option label="已完成" :value="3" />
            <el-option label="已取消" :value="4" />
            <el-option label="售后中" :value="5" />
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
        <div class="card-header">
          <span class="card-title">订单列表</span>
        </div>
      </template>

      <el-table :data="orderList" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="orderNo" label="订单号" width="260" />
        <el-table-column prop="username" label="用户" width="160">
          <template #default="{ row }">
            {{ row.username ?? row.phone ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="实付金额" width="100" align="right">
          <template #default="{ row }">
            <span class="price">¥{{ row.payAmount?.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="收货人" width="100">
          <template #default="{ row }">
            {{ row.receiver }}
          </template>
        </el-table-column>
        <el-table-column label="联系方式" width="130">
          <template #default="{ row }">
            {{ row.phone }}
          </template>
        </el-table-column>
        <el-table-column prop="itemCount" label="商品数" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 3 ? 'success' : row.status === 1 ? 'warning' : row.status === 0 ? 'info' : 'danger'" size="small">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row.orderNo)">详情</el-button>
            <el-divider direction="vertical" />
            <el-button
              v-if="row.status === 1"
              link
              type="success"
              size="small"
              @click="openShip(row)"
            >
              发货
            </el-button>
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

    <!-- 订单详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="订单详情"
      width="720px"
      destroy-on-close
    >
      <template v-if="detail">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.status === 3 ? 'success' : detail.status === 1 ? 'warning' : detail.status === 0 ? 'info' : 'danger'" size="small">
              {{ detail.statusText }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.username ?? detail.nickname ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.phone }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ detail.address }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">¥{{ detail.totalAmount?.toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="实付金额">¥{{ detail.payAmount?.toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="运费">¥{{ detail.freight?.toFixed(2) ?? '0.00' }}</el-descriptions-item>
          <el-descriptions-item label="优惠金额">¥{{ detail.discount?.toFixed(2) ?? '0.00' }}</el-descriptions-item>
          <el-descriptions-item label="买家备注" :span="2">{{ detail.remark ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ formatTime(detail.payTime) }}</el-descriptions-item>
          <el-descriptions-item label="发货时间">{{ formatTime(detail.deliveryTime) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatTime(detail.receiveTime) }}</el-descriptions-item>
          <el-descriptions-item label="物流公司">{{ detail.logType ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="物流单号">{{ detail.logNo ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="取消原因" :span="2">{{ detail.cancelReason ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 订单明细 -->
        <el-divider content-position="left">订单明细</el-divider>
        <el-table :data="detail.items" border size="small">
          <el-table-column label="商品图片" width="60">
            <template #default="{ row }">
              <el-image
                v-if="row.productImage"
                :src="row.productImage"
                fit="contain"
                style="width: 40px; height: 40px"
              />
              <span v-else class="no-img">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="商品名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="specName" label="规格" width="100" />
          <el-table-column label="单价" width="80" align="right">
            <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column label="小计" width="90" align="right">
            <template #default="{ row }">¥{{ row.subtotal?.toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <!-- 发货操作 -->
        <template v-if="detail.status === 1">
          <el-divider content-position="left">发货操作</el-divider>
          <el-form inline>
            <el-form-item label="物流公司">
              <el-input v-model="shipForm.logType" placeholder="如：顺丰速运" style="width: 150px" />
            </el-form-item>
            <el-form-item label="物流单号">
              <el-input v-model="shipForm.logNo" placeholder="如：SF1234567890" style="width: 200px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="shipLoading" @click="handleShipSubmit">确认发货</el-button>
            </el-form-item>
          </el-form>
        </template>
      </template>
    </el-dialog>
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

.price {
  color: #f56c6c;
  font-weight: 600;
}

.no-img {
  color: #c0c4cc;
  font-size: 0.75rem;
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
