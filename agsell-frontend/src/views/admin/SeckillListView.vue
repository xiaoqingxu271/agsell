<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageSeckill, createSeckill, updateSeckill, deleteSeckill, updateSeckillStatus,
  listProducts, getProductDetail,
} from '@/api/admin'
import type { SeckillActivityVO, SeckillActivityRequest, ProductSpecDTO } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const list = ref<SeckillActivityVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

async function fetchList() {
  loading.value = true
  try {
    const res = await pageSeckill({
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

// ── 商品下拉数据 ──
const productOptions = ref<{ id: number; name: string; price: string }[]>([])
async function loadProducts() {
  try {
    const res = await listProducts({ pageNum: 1, pageSize: 100, status: 1 })
    productOptions.value = res.records.map(p => ({
      id: p.id, name: p.name, price: String(p.price ?? ''),
    }))
  } catch {
    // interceptor handles error
  }
}

// ── 商品规格：选中商品后按需加载；有规格才展示规格选择 ──
const productSpecs = ref<ProductSpecDTO[]>([])
const specLoading = ref(false)

async function loadProductSpecs(productId: number | undefined, pendingSpecId?: number | string | null) {
  productSpecs.value = []
  if (!productId) { form.value.productSpecId = null; return }
  specLoading.value = true
  try {
    const res = await getProductDetail(productId)
    productSpecs.value = (res.specs ?? []).filter(s => s.id != null)
    // 回显：仅在加载出的规格中能找到匹配项时才赋值，避免 el-select 显示裸 ID
    const matched = pendingSpecId != null
      ? productSpecs.value.find(s => String(s.id) === String(pendingSpecId))
      : undefined
    form.value.productSpecId = matched ? matched.id! : null
  } catch {
    form.value.productSpecId = null
  } finally {
    specLoading.value = false
  }
}

function handleProductChange(val: number) {
  loadProductSpecs(val)
}

// ── 新增/编辑弹窗 ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增秒杀活动')
const formRef = ref()
const formLoading = ref(false)
const isEditForm = ref(false)
const fixedProductLabel = ref('')

type SeckillForm = {
  productId?: number
  productSpecId?: number | null
  seckillPrice?: number
  seckillStock?: number
  seckillLimit?: number
  startTime?: string
  endTime?: string
  sort: number
  status: number
}

const form = ref<SeckillForm>({
  productId: undefined, productSpecId: null, seckillPrice: undefined, seckillStock: undefined,
  seckillLimit: 1, startTime: undefined, endTime: undefined, sort: 0, status: 1,
})

function openCreate() {
  dialogTitle.value = '新增秒杀活动'
  isEditForm.value = false
  fixedProductLabel.value = ''
  productSpecs.value = []
  form.value = {
    productId: undefined, productSpecId: null, seckillPrice: undefined, seckillStock: undefined,
    seckillLimit: 1, startTime: undefined, endTime: undefined, sort: 0, status: 1,
  }
  dialogVisible.value = true
}

function openEdit(row: SeckillActivityVO) {
  dialogTitle.value = '编辑秒杀活动'
  isEditForm.value = true
  editingId.value = row.id
  fixedProductLabel.value = `${row.productName ?? ''}`
  form.value = {
    productId: row.productId,
    productSpecId: null, // 先置空，规格加载完成后匹配回显，避免显示裸 ID
    seckillPrice: Number(row.seckillPrice),
    seckillStock: row.seckillStock,
    seckillLimit: row.seckillLimit,
    startTime: row.startTime.slice(0, 19),
    endTime: row.endTime.slice(0, 19),
    sort: row.sort,
    status: row.status,
  }
  dialogVisible.value = true
  // 异步加载该商品规格，加载完成后回显已绑定规格
  loadProductSpecs(row.productId, row.productSpecId)
}

// 时间字符串规范化（兼容 T / 空格两种格式，统一按 ISO 比较）
function normalizeTime(t?: string): string {
  return (t ?? '').replace(' ', 'T')
}

// ── 起止时间联动：选完自动收起；结束早于开始则清空并提示 ──
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
  if (!form.value.productId) { ElMessage.warning('请选择秒杀商品'); return }
  if (!form.value.seckillPrice || form.value.seckillPrice <= 0) { ElMessage.warning('请输入有效的秒杀价'); return }
  if (!form.value.seckillStock || form.value.seckillStock <= 0) { ElMessage.warning('请输入有效的秒杀库存'); return }
  if (!form.value.startTime || !form.value.endTime) { ElMessage.warning('请选择活动起止时间'); return }
  if (normalizeTime(form.value.endTime) <= normalizeTime(form.value.startTime)) { ElMessage.warning('结束时间必须晚于开始时间'); return }

  const payload: SeckillActivityRequest = {
    productId: form.value.productId,
    productSpecId: form.value.productSpecId ?? null,
    seckillPrice: form.value.seckillPrice,
    seckillStock: form.value.seckillStock,
    seckillLimit: form.value.seckillLimit ?? 1,
    startTime: form.value.startTime,
    endTime: form.value.endTime,
    sort: form.value.sort,
    status: form.value.status,
  }
  formLoading.value = true
  try {
    if (isEditForm.value) {
      await updateSeckill(editingId.value, payload)
      ElMessage.success('编辑成功')
    } else {
      await createSeckill(payload)
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

async function handleDelete(row: SeckillActivityVO) {
  await ElMessageBox.confirm(`确认删除秒杀活动「${row.productName ?? row.id}」？`, '提示', { type: 'warning' })
  try {
    await deleteSeckill(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

async function handleStatusChange(row: SeckillActivityVO, status: number) {
  try {
    await updateSeckillStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '已上架' : '已下架')
    fetchList()
  } catch {
    fetchList()
  }
}

const editingId = ref(0)

function formatTime(time: string): string {
  return time.replace('T', ' ').substring(0, 19)
}

/** 库存进度 = 剩余库存占比：有货即绿色条，售罄显示 100% 灰条 */
function stockProgress(row: SeckillActivityVO): number {
  const total = row.seckillStock
  if (!total) return 100
  const remaining = Math.min(row.remainingStock ?? total, total)
  if (remaining <= 0) return 100
  return Math.round(remaining * 100 / total)
}

/** 库存进度条颜色：售罄显示灰色，否则绿色 */
function stockColor(row: SeckillActivityVO): string {
  const remaining = row.remainingStock ?? row.seckillStock ?? 0
  return remaining <= 0 ? '#9CA3AF' : '#15803D'
}

function statusTagType(text: string): 'success' | 'warning' | 'info' | 'danger' {
  if (text === '进行中') return 'success'
  if (text === '未开始') return 'warning'
  if (text === '已结束') return 'info'
  return 'danger'
}

onMounted(() => {
  fetchList()
  loadProducts()
})
</script>

<template>
  <div class="page">
    <PageHeader title="秒杀管理" description="创建限时秒杀活动，控制秒杀价、库存与活动时段">
      <el-button type="primary" @click="openCreate">新增秒杀活动</el-button>
    </PageHeader>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="admin-search-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="商品名称">
          <el-input v-model="keyword" placeholder="请输入商品名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
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
          <span class="admin-card-title">秒杀活动列表</span>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="活动编号" min-width="180" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="activity-code">{{ row.activityCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品图片" width="110" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.productImage"
              :src="row.productImage"
              fit="cover"
              class="product-img"
              :preview-src-list="[row.productImage]"
              preview-teleported
            />
            <span v-else class="admin-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="商品名称" min-width="160" align="center" show-overflow-tooltip />
        <el-table-column label="秒杀价" width="200" align="center">
          <template #default="{ row }">
            <span class="seckill-price">¥{{ row.seckillPrice }}</span>
            <span v-if="row.productPrice" class="origin-price">¥{{ row.productPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column label="库存(剩余/总量)" width="180" align="center">
          <template #default="{ row }">
            <div class="stock-cell">
              <span class="stock-text">{{ row.remainingStock ?? row.seckillStock }} / {{ row.seckillStock }}</span>
              <el-progress
                :percentage="stockProgress(row as SeckillActivityVO)"
                :stroke-width="6"
                :show-text="false"
                :color="stockColor(row as SeckillActivityVO)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="360" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.activityStatusText)" effect="light" size="small">
              {{ row.activityStatusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openEdit(row as SeckillActivityVO)">编辑</el-button>
            <el-divider direction="vertical" class="admin-action-divider" />
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              size="small"
              class="admin-action-btn"
              @click="handleStatusChange(row as SeckillActivityVO, row.status === 1 ? 0 : 1)"
            >
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-divider direction="vertical" class="admin-action-divider" />
            <el-button link type="danger" size="small" class="admin-action-btn" @click="handleDelete(row as SeckillActivityVO)">删除</el-button>
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
        <el-form-item label="秒杀商品" required>
          <template v-if="isEditForm">
            <div class="product-fixed">{{ fixedProductLabel }}</div>
          </template>
          <el-select
            v-else
            v-model="form.productId"
            placeholder="请选择秒杀商品"
            filterable
            style="width: 100%"
            @change="handleProductChange"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.id"
              :label="`${p.name}（¥${p.price}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="productSpecs.length" label="商品规格">
          <el-select
            v-model="form.productSpecId"
            placeholder="请选择规格（不选则按商品整体秒杀）"
            clearable
            :loading="specLoading"
            style="width: 100%"
          >
            <el-option
              v-for="s in productSpecs"
              :key="s.id"
              :label="`${s.specName}（¥${s.price}）`"
              :value="s.id!"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="秒杀价" required>
              <el-input-number v-model="form.seckillPrice" :min="0.01" :precision="2" :step="0.5" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="秒杀库存" required>
              <el-input-number v-model="form.seckillStock" :min="1" :max="99999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="每人限购">
              <el-input-number v-model="form.seckillLimit" :min="1" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="活动时间" required>
          <div class="time-range">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              placeholder="开始时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
              @change="onStartTimeChange"
            />
            <span class="time-separator">至</span>
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              placeholder="结束时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
              @change="onEndTimeChange"
            />
          </div>
        </el-form-item>
        <el-form-item v-if="!isEditForm" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
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

.product-img {
  width: 64px;
  height: 64px;
  border-radius: 8px;
}

.seckill-price {
  color: #DC2626;
  font-weight: 600;
  font-size: 15px;
  margin-right: 6px;
}

.origin-price {
  color: #9CA3AF;
  font-size: 12px;
  text-decoration: line-through;
}

.stock-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  width: 100%;
  line-height: 1;
}

.stock-text {
  font-size: 13px;
  color: #1F2937;
}

.stock-cell .el-progress {
  width: 130px;
}

.product-fixed {
  width: 100%;
  padding: 0 12px;
  height: 32px;
  line-height: 32px;
  background: #F3F5F4;
  border-radius: 6px;
  font-size: 13px;
  color: #374151;
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

.activity-code {
  font-family: Consolas, Menlo, monospace;
  font-weight: 600;
  color: #14532D;
  letter-spacing: 0.02em;
  font-size: 13px;
}
</style>
