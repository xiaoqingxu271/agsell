<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageCoupon, createCoupon, updateCoupon, deleteCoupon, updateCouponStatus, listProducts,
} from '@/api/admin'
import type { CouponVO, CouponRequest, ProductListItemVO } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const list = ref<CouponVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

async function fetchList() {
  loading.value = true
  try {
    const res = await pageCoupon({
      pageNum: page.value, pageSize: pageSize.value,
      keyword: keyword.value || undefined, status: statusFilter.value,
    })
    list.value = res.records
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
  keyword.value = ''
  statusFilter.value = undefined
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

// ── 新增/编辑弹窗 ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增优惠券')
const formLoading = ref(false)
const isEditForm = ref(false)
const editingId = ref(0)
const editingStatus = ref(1)

type CouponForm = {
  couponName?: string
  couponType: number
  threshold?: number
  amount?: number
  discount?: number
  maxDiscount?: number
  productIds: number[]
  totalCount?: number
  perUserLimit: number
  validDays?: number
  startTime?: string
  endTime?: string
  sort: number
  status: number
}

const form = ref<CouponForm>({
  couponType: 1, threshold: 0, productIds: [], perUserLimit: 1, sort: 0, status: 1,
})

// 商品选择下拉（品类券用）
const productOptions = ref<ProductListItemVO[]>([])
async function loadProducts() {
  try {
    const res = await listProducts({ pageNum: 1, pageSize: 200, status: 1 } as any)
    productOptions.value = res.records || []
  } catch { /* ignore */ }
}

function openCreate() {
  dialogTitle.value = '新增优惠券'
  isEditForm.value = false
  editingId.value = 0
  form.value = {
    couponName: undefined, couponType: 1, threshold: 0, amount: undefined,
    discount: undefined, maxDiscount: undefined, productIds: [],
    totalCount: 0, perUserLimit: 1, validDays: 7, startTime: undefined,
    endTime: undefined, sort: 0, status: 1,
  }
  dialogVisible.value = true
}

function openEdit(row: CouponVO) {
  dialogTitle.value = '编辑优惠券'
  isEditForm.value = true
  editingId.value = row.id
  editingStatus.value = row.status
  form.value = {
    couponName: row.couponName,
    couponType: row.couponType,
    threshold: Number(row.threshold),
    amount: row.amount != null ? Number(row.amount) : undefined,
    discount: row.discount != null ? Number(row.discount) : undefined,
    maxDiscount: row.maxDiscount != null ? Number(row.maxDiscount) : undefined,
    productIds: row.productIds || [],
    totalCount: row.totalCount,
    perUserLimit: row.perUserLimit,
    validDays: row.validDays,
    startTime: row.startTime ? row.startTime.slice(0, 19) : undefined,
    endTime: row.endTime ? row.endTime.slice(0, 19) : undefined,
    sort: row.sort,
    status: row.status,
  }
  dialogVisible.value = true
}

// 时间字符串规范化（兼容 T / 空格两种格式）
function normalizeTime(t?: string): string {
  return (t ?? '').replace(' ', 'T')
}

function onStartTimeChange() {
  if (form.value.startTime && form.value.endTime
      && normalizeTime(form.value.endTime) <= normalizeTime(form.value.startTime)) {
    form.value.endTime = undefined
    ElMessage.warning('结束时间必须晚于开始时间')
  }
}

function onEndTimeChange() {
  if (form.value.startTime && form.value.endTime
      && normalizeTime(form.value.endTime) <= normalizeTime(form.value.startTime)) {
    form.value.endTime = undefined
    ElMessage.warning('结束时间必须晚于开始时间')
  }
}

async function handleSubmit() {
  if (!form.value.couponName?.trim()) { ElMessage.warning('请输入券名称'); return }
  if (form.value.threshold == null || form.value.threshold < 0) { ElMessage.warning('使用门槛不能为负数'); return }
  if (form.value.totalCount == null || form.value.totalCount < 0) { ElMessage.warning('发放总量不能为负数'); return }
  if (!form.value.validDays || form.value.validDays < 1) { ElMessage.warning('有效天数必须大于0'); return }

  // 按类型校验
  const isDiscount = form.value.couponType === 2
  if (isDiscount) {
    const d = form.value.discount
    if (d == null || d <= 0 || d >= 1) { ElMessage.warning('折扣率需在 0~1 之间（如 0.85 表示85折）'); return }
  } else {
    if (!form.value.amount || form.value.amount <= 0) { ElMessage.warning('请输入有效的优惠金额'); return }
  }
  if (form.value.couponType === 3 && (!form.value.productIds || form.value.productIds.length === 0)) {
    ElMessage.warning('品类券至少选择一个适用商品'); return
  }
  if (form.value.startTime && form.value.endTime
      && normalizeTime(form.value.endTime) <= normalizeTime(form.value.startTime)) {
    ElMessage.warning('结束时间必须晚于开始时间'); return
  }

  const payload: CouponRequest = {
    couponName: form.value.couponName.trim(),
    couponType: form.value.couponType,
    threshold: form.value.threshold,
    amount: isDiscount ? undefined : form.value.amount,
    discount: isDiscount ? form.value.discount : null,
    maxDiscount: isDiscount ? (form.value.maxDiscount || 0) : null,
    productIds: form.value.couponType === 3 ? form.value.productIds : undefined,
    totalCount: form.value.totalCount,
    perUserLimit: form.value.perUserLimit,
    validDays: form.value.validDays,
    startTime: form.value.startTime || null,
    endTime: form.value.endTime || null,
    sort: form.value.sort,
    status: form.value.status,
  }
  formLoading.value = true
  try {
    if (isEditForm.value) {
      await updateCoupon(editingId.value, payload)
      ElMessage.success('编辑成功')
    } else {
      await createCoupon(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    formLoading.value = false
  }
}

async function handleDelete(row: CouponVO) {
  await ElMessageBox.confirm(`确认删除优惠券「${row.couponName}」？\n已领取未使用的券将同步失效`, '提示', { type: 'warning' })
  try {
    await deleteCoupon(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

async function handleStatusChange(row: CouponVO, status: number) {
  try {
    await updateCouponStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '已上架' : '已下架')
    fetchList()
  } catch {
    fetchList()
  }
}

/** 领取进度 = 已领取/总量（不限量显示 —） */
function receiveProgress(row: CouponVO): number {
  if (!row.totalCount) return 0
  const received = Math.min(row.receivedCount ?? 0, row.totalCount)
  return Math.round(received * 100 / row.totalCount)
}

/** 进度条颜色：领完显示灰色，否则金色系 */
function receiveColor(row: CouponVO): string {
  const received = row.receivedCount ?? 0
  if (row.totalCount && received >= row.totalCount) return '#8A9A91'
  return '#B45309'
}

function formatTime(time?: string | null): string {
  return time ? time.replace('T', ' ').substring(0, 19) : '长期'
}

/** 金额展示：去掉无意义小数（如 10.00 → 10） */
function fmt(v: string | number): string {
  return Number(v).toString()
}

/** 券类型标签文案 */
function typeText(t: number): string {
  return t === 2 ? '折扣券' : t === 3 ? '品类券' : '满减券'
}

/** 面额列按类型展示 */
function amountText(row: CouponVO): string {
  if (row.couponType === 2 && row.discount != null) {
    const off = Number(row.discount) * 10
    return `${off.toFixed(1)}折`
  }
  return `¥${fmt(row.amount)}`
}

onMounted(() => {
  fetchList()
  loadProducts()
})
</script>

<template>
  <div class="page">
    <PageHeader title="优惠券管理" description="创建满减优惠券，控制面额、门槛、总量与有效期">
      <el-button type="primary" @click="openCreate">新增优惠券</el-button>
    </PageHeader>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="admin-search-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="券名称">
          <el-input v-model="keyword" placeholder="请输入券名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">优惠券列表</span>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="couponName" label="券名称" min-width="200" align="center" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag effect="light" size="small" :type="row.couponType === 2 ? 'success' : row.couponType === 3 ? 'info' : 'warning'">
              {{ typeText(row.couponType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面额 / 门槛" width="170" align="center">
          <template #default="{ row }">
            <span class="coupon-amount">{{ amountText(row as CouponVO) }}</span>
            <span class="coupon-threshold">{{ Number(row.threshold) > 0 ? `满${fmt(row.threshold)}可用` : '无门槛' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已领取 / 总量" width="190" align="center">
          <template #default="{ row }">
            <div class="receive-cell">
              <span class="receive-text">
                {{ row.receivedCount }} / {{ row.totalCount ? row.totalCount : '不限' }}
              </span>
              <el-progress
                v-if="row.totalCount"
                :percentage="receiveProgress(row as CouponVO)"
                :stroke-width="6"
                :show-text="false"
                :color="receiveColor(row as CouponVO)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="有效天数" width="120" align="center">
          <template #default="{ row }">{{ row.validDays }} 天</template>
        </el-table-column>
        <el-table-column label="领取时间" min-width="330" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" size="small">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openEdit(row as CouponVO)">编辑</el-button>
            <el-divider direction="vertical" class="admin-action-divider" />
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              size="small"
              class="admin-action-btn"
              @click="handleStatusChange(row as CouponVO, row.status === 1 ? 0 : 1)"
            >
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-divider direction="vertical" class="admin-action-divider" />
            <el-button link type="danger" size="small" class="admin-action-btn" @click="handleDelete(row as CouponVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="券名称" required>
          <el-input v-model="form.couponName" placeholder="如：满99减10 / 全场85折" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="券类型" required>
          <el-radio-group v-model="form.couponType">
            <el-radio :value="1">满减券</el-radio>
            <el-radio :value="2">折扣券</el-radio>
            <el-radio :value="3">品类券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item v-if="form.couponType === 2" label="折扣率" required>
              <el-input-number
                v-model="form.discount"
                :min="0.01"
                :max="0.99"
                :precision="2"
                :step="0.05"
                :disabled="isEditForm && editingStatus === 1"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item v-else label="优惠金额" required>
              <el-input-number
                v-model="form.amount"
                :min="0.01"
                :precision="2"
                :step="5"
                :disabled="isEditForm && editingStatus === 1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="使用门槛">
              <el-input-number
                v-model="form.threshold"
                :min="0"
                :precision="2"
                :step="10"
                :disabled="isEditForm && editingStatus === 1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="form.couponType === 2" :gutter="16">
          <el-col :span="12">
            <el-form-item label="封顶金额">
              <el-input-number
                v-model="form.maxDiscount"
                :min="0"
                :precision="2"
                :step="10"
                :disabled="isEditForm && editingStatus === 1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="form.couponType === 3" label="适用商品" required>
          <el-select
            v-model="form.productIds"
            multiple
            filterable
            placeholder="选择该券适用的商品（订单须全部命中）"
            style="width: 100%"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.id"
              :label="`${p.name}（¥${p.price}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <div class="form-tip">
            <span v-if="form.couponType === 2">折扣率 0.85 表示85折（实付85%）；门槛0=无门槛；封顶0=不封顶</span>
            <span v-else-if="form.couponType === 3">品类券：订单商品须全部在所选商品范围内才可用；门槛为0=无门槛</span>
            <span v-else>门槛为 0 表示无门槛；例如「满 99 减 10」填门槛 99、优惠金额 10</span>
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发放总量">
              <el-input-number
                v-model="form.totalCount"
                :min="0"
                :max="999999"
                :disabled="isEditForm && editingStatus === 1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效天数" required>
              <el-input-number
                v-model="form.validDays"
                :min="1"
                :max="365"
                :disabled="isEditForm && editingStatus === 1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <div class="form-tip">总量为 0 表示不限量；有效期从用户领取时刻起算，按填写的天数顺延</div>
        </el-form-item>
        <el-form-item label="领取时间">
          <div class="time-range">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              placeholder="开始时间（留空=立即）"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
              @change="onStartTimeChange"
            />
            <span class="time-separator">至</span>
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              placeholder="结束时间（留空=长期）"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
              @change="onEndTimeChange"
            />
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="!isEditForm" label="状态">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="isEditForm && editingStatus === 1">
          <div class="form-tip warning">该券已上架：面额、门槛、总量、有效期已锁定，如需修改请先下架</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { min-height: 100%; }

.coupon-amount {
  color: #B45309;
  font-weight: 600;
  font-size: 15px;
  margin-right: 6px;
}

.coupon-threshold {
  color: #8A9A91;
  font-size: 12px;
}

.receive-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 100%;
  line-height: 1;
}

.receive-text {
  font-size: 13px;
  color: #1F2937;
}

.receive-cell .el-progress {
  width: 130px;
}

.time-range {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.time-separator {
  color: #6B7280;
  font-size: 13px;
  flex-shrink: 0;
}

.form-tip {
  width: 100%;
  font-size: 12px;
  color: #8A9A91;
  line-height: 1.5;
}

.form-tip.warning {
  color: #B45309;
  background: rgba(244, 179, 147, 0.15);
  border-radius: 6px;
  padding: 6px 10px;
}
</style>
