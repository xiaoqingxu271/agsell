  <script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listTraceability,
  getTraceabilityDetail,
  createTraceability,
  updateTraceability,
  deleteTraceability,
  generateTraceQr,
  createTraceRecord,
  updateTraceRecord,
  deleteTraceRecord,
  listProducts,
} from '@/api/admin'
import { uploadFile } from '@/api/upload'
import type {
  TraceabilityListItemVO,
  TraceabilityDetailVO,
  TraceabilityCreateRequest,
  ProductionRecordRequest,
  ProductionRecordVO,
  ProductListItemVO,
  CertificationType,
  RecordType,
} from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'
import { regionData, type RegionOption } from '@/assets/region-data'

// ─── 列表 ─────────────────────────────────────────────────────────────────────

const loading = ref(false)
const list = ref<TraceabilityListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const productNameFilter = ref('')
const batchNoFilter = ref('')

const CERT_TYPE_OPTIONS: { label: string; value: CertificationType; tag: 'success' | 'warning' | 'primary' | 'info' }[] = [
  { label: '有机认证', value: 'ORGANIC', tag: 'success' },
  { label: '绿色认证', value: 'GREEN', tag: 'warning' },
  { label: '地理标志', value: 'GEOGRAPHICAL', tag: 'primary' },
  { label: '无认证', value: 'NONE', tag: 'info' },
]

function certTag(type: string | null) {
  return CERT_TYPE_OPTIONS.find((o) => o.value === type)?.tag ?? 'info'
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listTraceability({
      pageNum: page.value,
      pageSize: pageSize.value,
      productName: productNameFilter.value || undefined,
      batchNo: batchNoFilter.value || undefined,
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
  productNameFilter.value = ''
  batchNoFilter.value = ''
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

// ─── 新增/编辑溯源 ─────────────────────────────────────────────────────────────

const formVisible = ref(false)
const formTitle = ref('新增溯源信息')
const formLoading = ref(false)
const productOptions = ref<ProductListItemVO[]>([])
const isEditForm = ref(false)
const fixedProductLabel = ref('')
const regionValue = ref<string[]>([])

const form = ref<TraceabilityCreateRequest>({
  productId: undefined,
  farmerName: '',
  farmerPhone: '',
  originProvince: '',
  originCity: '',
  originDistrict: '',
  plantingDate: '',
  harvestDate: '',
  qualityCheckResult: '',
  pesticideTest: '',
  certificationType: '',
  certificationUrls: [],
})

// 级联回显宽松匹配：精确优先，退而求其次用前缀匹配（兼容库中带"县/市"后缀与手填旧数据）
function matchRegionLevel(options: RegionOption[], value: string | null | undefined): string {
  if (!value) return ''
  const exact = options.find((o) => o.value === value)
  if (exact) return exact.value
  const prefix = options.find((o) => o.value.startsWith(value))
  return prefix ? prefix.value : ''
}

function syncRegionValue() {
  const p = matchRegionLevel(regionData, form.value.originProvince)
  const cityOptions = regionData.find((o) => o.value === p)?.children || []
  const c = matchRegionLevel(cityOptions, form.value.originCity)
  const districtOptions = cityOptions.find((o) => o.value === c)?.children || []
  const d = matchRegionLevel(districtOptions, form.value.originDistrict)
  regionValue.value = [p, c, d].filter(Boolean)
}

function handleRegionChange(v: unknown) {
  const arr = Array.isArray(v) ? (v as (string | number)[]) : []
  form.value.originProvince = String(arr[0] ?? '')
  form.value.originCity = String(arr[1] ?? '')
  form.value.originDistrict = String(arr[2] ?? '')
}

// 证书图片：待上传文件 + 已上传 URL + 本地预览 URL
const certPendingFiles = ref<File[]>([])
const certPreviewUrls = ref<string[]>([])

function resetCertImages() {
  certPendingFiles.value = []
  certPreviewUrls.value.forEach((u) => URL.revokeObjectURL(u))
  certPreviewUrls.value = []
}

function handleCertSelect(event: Event) {
  const files = (event.target as HTMLInputElement).files
  if (!files || !files.length) return
  const file = files[0]
  if (!file) return
  certPendingFiles.value.push(file)
  certPreviewUrls.value.push(URL.createObjectURL(file))
  ;(event.target as HTMLInputElement).value = ''
}

function removeCertImage(index: number) {
  certPendingFiles.value.splice(index, 1)
  if (certPreviewUrls.value[index]) URL.revokeObjectURL(certPreviewUrls.value[index]!)
  certPreviewUrls.value.splice(index, 1)
  ;(form.value.certificationUrls || []).splice(index, 1)
}

function openCreate() {
  formTitle.value = '新增溯源信息'
  isEditForm.value = false
  fixedProductLabel.value = ''
  regionValue.value = []
  form.value = {
    productId: undefined,
    farmerName: '',
    farmerPhone: '',
    originProvince: '',
    originCity: '',
    originDistrict: '',
    plantingDate: '',
    harvestDate: '',
    qualityCheckResult: '',
    pesticideTest: '',
    certificationType: '',
    certificationUrls: [],
  }
  resetCertImages()
  formVisible.value = true
}

function openEdit(row: TraceabilityListItemVO) {
  editingId.value = row.id
  isEditForm.value = true
  fixedProductLabel.value = `${row.productName}（ID:${row.productId}）`
  formTitle.value = `编辑溯源 - ${row.batchNo}`
  form.value = {
    productId: row.productId,
    farmerName: row.farmerName,
    farmerPhone: '',
    originProvince: '',
    originCity: '',
    originDistrict: '',
    plantingDate: '',
    harvestDate: row.harvestDate ?? '',
    qualityCheckResult: '',
    pesticideTest: '',
    certificationType: (row.certificationType as CertificationType) || '',
    certificationUrls: [],
  }
  regionValue.value = []
  resetCertImages()
  formVisible.value = true
  // 异步回填详情字段
  getTraceabilityDetail(row.id)
    .then((d) => {
      form.value.farmerPhone = d.farmerPhone ?? ''
      form.value.originProvince = d.originProvince ?? ''
      form.value.originCity = d.originCity ?? ''
      form.value.originDistrict = d.originDistrict ?? ''
      form.value.plantingDate = d.plantingDate ?? ''
      form.value.qualityCheckResult = d.qualityCheckResult ?? ''
      form.value.pesticideTest = d.pesticideTest ?? ''
      form.value.certificationType = (d.certificationType as CertificationType) || ''
      form.value.certificationUrls = [...(d.certificationUrls || [])]
      syncRegionValue()
    })
    .catch(() => {
      // interceptor handles error
    })
}

async function handleFormSubmit() {
  if (!form.value.productId) { ElMessage.warning('请选择商品'); return }
  if (!form.value.farmerName?.trim()) { ElMessage.warning('请输入种植户姓名'); return }
  if (form.value.harvestDate && form.value.plantingDate && form.value.harvestDate < form.value.plantingDate) {
    ElMessage.warning('采摘日期不能早于种植日期'); return
  }
  formLoading.value = true
  try {
    // 上传证书图片（追加到已存 URL 后面，顺序对应表单顺序）
    const uploadedUrls: string[] = []
    for (const file of certPendingFiles.value) {
      uploadedUrls.push(await uploadFile(file, 'trace/cert'))
    }
    const payload: TraceabilityCreateRequest = {
      ...form.value,
      certificationUrls: [...(form.value.certificationUrls || []), ...uploadedUrls],
      certificationType: (form.value.certificationType || 'NONE') as CertificationType,
    }
    if (formTitle.value.startsWith('新增')) {
      await createTraceability(payload)
      ElMessage.success('新增成功')
    } else {
      await updateTraceability(editingId.value, payload)
      ElMessage.success('编辑成功')
    }
    formVisible.value = false
    resetCertImages()
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    formLoading.value = false
  }
}

// ─── 详情 + 二维码 + 生产记录 ──────────────────────────────────────────────────

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<TraceabilityDetailVO | null>(null)
const editingId = ref(0)
const qrLoading = ref(false)

async function openDetail(row: TraceabilityListItemVO) {
  editingId.value = row.id
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getTraceabilityDetail(row.id)
  } catch {
    // interceptor handles error
  } finally {
    detailLoading.value = false
  }
}

async function handleGenerateQr() {
  if (!detail.value) return
  await ElMessageBox.confirm('确认生成该批次的溯源二维码？', '生成二维码', { type: 'info' })
  qrLoading.value = true
  try {
    await generateTraceQr(detail.value.id)
    ElMessage.success('二维码生成成功')
    detail.value = await getTraceabilityDetail(detail.value.id)
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    qrLoading.value = false
  }
}

async function handleDelete(row: TraceabilityListItemVO) {
  await ElMessageBox.confirm(
    `确认删除批次「${row.batchNo}」的溯源信息？其下生产记录将一并删除。`,
    '删除溯源信息',
    { type: 'warning', confirmButtonText: '确认删除' }
  )
  try {
    await deleteTraceability(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

// ─── 生产记录维护 ─────────────────────────────────────────────────────────────

const RECORD_TYPE_OPTIONS: { label: string; value: RecordType }[] = [
  { label: '播种', value: 'SEEDING' },
  { label: '施肥', value: 'FERTILIZING' },
  { label: '浇水', value: 'WATERING' },
  { label: '病虫害防治', value: 'PEST_CONTROL' },
  { label: '采收', value: 'HARVEST' },
  { label: '其他', value: 'OTHER' },
]

const recordVisible = ref(false)
const recordLoading = ref(false)
const recordForm = ref<ProductionRecordRequest>({
  traceabilityId: 0,
  recordType: 'SEEDING',
  recordDate: '',
  content: '',
  images: [],
  operator: '',
})
const editingRecordId = ref(0)

const recPendingFiles = ref<File[]>([])
const recPreviewUrls = ref<string[]>([])

function resetRecImages() {
  recPendingFiles.value = []
  recPreviewUrls.value.forEach((u) => URL.revokeObjectURL(u))
  recPreviewUrls.value = []
}

function handleRecImageSelect(event: Event) {
  const files = (event.target as HTMLInputElement).files
  if (!files || !files.length) return
  const file = files[0]
  if (!file) return
  recPendingFiles.value.push(file)
  recPreviewUrls.value.push(URL.createObjectURL(file))
  ;(event.target as HTMLInputElement).value = ''
}

function removeRecImage(index: number) {
  recPendingFiles.value.splice(index, 1)
  if (recPreviewUrls.value[index]) URL.revokeObjectURL(recPreviewUrls.value[index]!)
  recPreviewUrls.value.splice(index, 1)
  ;(recordForm.value.images || []).splice(index, 1)
}

function openRecordCreate() {
  if (!detail.value) return
  editingRecordId.value = 0
  recordForm.value = {
    traceabilityId: detail.value.id,
    recordType: 'SEEDING',
    recordDate: '',
    content: '',
    images: [],
    operator: '',
  }
  resetRecImages()
  recordVisible.value = true
}

function openRecordEdit(record: ProductionRecordVO) {
  editingRecordId.value = record.id
  recordForm.value = {
    traceabilityId: detail.value?.id ?? 0,
    recordType: (record.recordType as RecordType) || 'OTHER',
    recordDate: record.recordDate,
    content: record.content ?? '',
    images: [...(record.images || [])],
    operator: record.operator ?? '',
  }
  resetRecImages()
  recordVisible.value = true
}

async function handleRecordSubmit() {
  if (!recordForm.value.recordDate) { ElMessage.warning('请选择记录日期'); return }
  if (!recordForm.value.content?.trim()) { ElMessage.warning('请输入记录内容'); return }
  recordLoading.value = true
  try {
    const uploadedUrls: string[] = []
    for (const file of recPendingFiles.value) {
      uploadedUrls.push(await uploadFile(file, 'trace/record'))
    }
    const payload: ProductionRecordRequest = {
      ...recordForm.value,
      images: [...(recordForm.value.images || []), ...uploadedUrls],
    }
    if (editingRecordId.value) {
      await updateTraceRecord(editingRecordId.value, payload)
      ElMessage.success('记录更新成功')
    } else {
      await createTraceRecord(payload)
      ElMessage.success('记录添加成功')
    }
    recordVisible.value = false
    resetRecImages()
    detail.value = await getTraceabilityDetail(detail.value!.id)
  } catch {
    // interceptor handles error
  } finally {
    recordLoading.value = false
  }
}

async function handleRecordDelete(record: ProductionRecordVO) {
  await ElMessageBox.confirm('确认删除这条生产记录？', '删除记录', { type: 'warning' })
  try {
    await deleteTraceRecord(record.id)
    ElMessage.success('删除成功')
    detail.value = await getTraceabilityDetail(detail.value!.id)
  } catch {
    // interceptor handles error
  }
}

onMounted(async () => {
  fetchList()
  // 商品下拉：上架中的商品
  try {
    const res = await listProducts({ pageNum: 1, pageSize: 1000, status: 1 })
    productOptions.value = res.records
  } catch {
    // interceptor handles error
  }
})
</script>

<template>
  <div class="page">
    <PageHeader title="产地溯源" description="为商品批次建立溯源档案，生成溯源二维码">
      <el-button type="primary" @click="openCreate">新增溯源信息</el-button>
    </PageHeader>

    <!-- 搜索栏 -->
    <el-card shadow="never" class="admin-search-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="商品名称">
          <el-input v-model="productNameFilter" placeholder="请输入商品名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="batchNoFilter" placeholder="请输入批次号" clearable style="width: 180px" />
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
          <span class="admin-card-title">溯源批次列表</span>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="批次号" min-width="180" align="center">
          <template #default="{ row }">
            <span class="batch-no">{{ row.batchNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品图片" width="120" align="center">
          <template #default="{ row }">
            <el-image v-if="row.productImage" :src="row.productImage" fit="cover" class="product-img" :preview-src-list="[row.productImage]" preview-teleported />
            <div v-else class="product-img product-img-empty" />
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="商品名称" min-width="160" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.productName }}</template>
        </el-table-column>
        <el-table-column prop="origin" label="产地" min-width="180" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.origin || '-' }}</template>
        </el-table-column>
        <el-table-column prop="farmerName" label="种植户" width="180" align="center">
          <template #default="{ row }">{{ row.farmerName }}</template>
        </el-table-column>
        <el-table-column label="采摘日期" width="140" align="center">
          <template #default="{ row }">{{ row.harvestDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="认证类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.certificationType" :type="certTag(row.certificationType)" class="admin-status-tag" size="small">
              {{ row.certificationTypeText || row.certificationType }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="二维码" width="120" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.qrCodeUrl"
              :src="row.qrCodeUrl"
              fit="contain"
              class="qr-thumb"
              :preview-src-list="[row.qrCodeUrl]"
              preview-teleported
            />
            <span v-else class="admin-empty">未生成</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openDetail(row as TraceabilityListItemVO)">详情</el-button>
            <el-button link type="success" size="small" class="admin-action-btn" @click="openEdit(row as TraceabilityListItemVO)">编辑</el-button>
            <el-button link type="warning" size="small" class="admin-action-btn" :disabled="!!row.qrCodeUrl" @click="openDetail(row as TraceabilityListItemVO)">
              {{ row.qrCodeUrl ? '已生成' : '生成二维码' }}
            </el-button>
            <el-button link type="danger" size="small" class="admin-action-btn" @click="handleDelete(row as TraceabilityListItemVO)">删除</el-button>
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

    <!-- 新增/编辑溯源弹窗 -->
    <el-dialog v-model="formVisible" :title="formTitle" width="680px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <el-form-item label="关联商品" required>
          <template v-if="isEditForm">
            <div class="product-fixed">{{ fixedProductLabel }}</div>
          </template>
          <el-select v-else v-model="form.productId" placeholder="请选择商品" filterable style="width: 100%">
            <el-option
              v-for="p in productOptions"
              :key="p.id"
              :label="`${p.name}（ID:${p.id}）`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="种植户" required>
              <el-input v-model="form.farmerName" placeholder="请输入种植户姓名" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="form.farmerPhone" placeholder="选填" maxlength="20" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="24">
            <el-form-item label="原产地区">
              <el-cascader
                v-model="regionValue"
                :options="regionData"
                :props="{ expandTrigger: 'hover' }"
                placeholder="请选择省 / 市 / 区县"
                clearable
                style="width: 100%"
                @change="handleRegionChange"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="种植日期">
              <el-date-picker v-model="form.plantingDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采摘日期">
              <el-date-picker v-model="form.harvestDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="质检结果">
          <el-input v-model="form.qualityCheckResult" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="如：农残检测合格、糖度 13.5°Brix" />
        </el-form-item>
        <el-form-item label="农残检测">
          <el-input v-model="form.pesticideTest" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="如：GB 2763 全项合格" />
        </el-form-item>
        <el-form-item label="认证类型">
          <el-select v-model="form.certificationType" placeholder="无认证" clearable style="width: 100%">
            <el-option v-for="opt in CERT_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="认证证书">
          <div class="img-uploader">
            <template v-for="(url, idx) in (form.certificationUrls || [])" :key="'saved-' + idx">
              <div class="img-item">
                <el-image :src="url" fit="cover" class="img-preview" :preview-src-list="form.certificationUrls" :initial-index="idx" preview-teleported />
                <button type="button" class="img-remove" aria-label="移除图片" @click="removeCertImage(idx)">×</button>
              </div>
            </template>
            <template v-for="(url, idx) in certPreviewUrls" :key="'local-' + idx">
              <div class="img-item">
                <el-image :src="url" fit="cover" class="img-preview" />
                <button type="button" class="img-remove" aria-label="移除图片" @click="removeCertImage(idx)">×</button>
              </div>
            </template>
            <label for="trace-cert-input" class="img-add" title="上传证书图片">
              <span class="img-add-icon">＋</span>
              <span class="img-add-text">添加图片</span>
            </label>
            <input id="trace-cert-input" type="file" accept="image/*" hidden @change="handleCertSelect" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleFormSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="溯源档案详情" width="760px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small" title="基本信息" style="margin-bottom: 1rem">
            <el-descriptions-item label="批次号">
              <span class="batch-no">{{ detail.batchNo }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="关联商品">{{ detail.productName }}</el-descriptions-item>
            <el-descriptions-item label="种植户">{{ detail.farmerName }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detail.farmerPhone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="产地">
              {{ [detail.originProvince, detail.originCity, detail.originDistrict].filter(Boolean).join(' ') || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="认证类型">
              <el-tag v-if="detail.certificationType" :type="certTag(detail.certificationType)" class="admin-status-tag" size="small">
                {{ detail.certificationTypeText || detail.certificationType }}
              </el-tag>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="种植日期">{{ detail.plantingDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="采摘日期">{{ detail.harvestDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="质检结果" :span="2">{{ detail.qualityCheckResult || '-' }}</el-descriptions-item>
            <el-descriptions-item label="农残检测" :span="2">{{ detail.pesticideTest || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 溯源二维码 -->
          <div class="qr-block">
            <div class="block-title">溯源二维码</div>
            <template v-if="detail.qrCodeUrl">
              <el-image :src="detail.qrCodeUrl" fit="contain" class="qr-large" :preview-src-list="[detail.qrCodeUrl]" preview-teleported />
              <div class="qr-hint">扫描二维码可在小程序端查看本批次溯源档案</div>
            </template>
            <template v-else>
              <div class="qr-placeholder">尚未生成二维码</div>
              <el-button type="primary" size="small" :loading="qrLoading" @click="handleGenerateQr">生成二维码</el-button>
            </template>
          </div>

          <!-- 认证证书 -->
          <div v-if="detail.certificationUrls && detail.certificationUrls.length" class="evidence-block">
            <div class="block-title">认证证书</div>
            <el-image
              v-for="(img, idx) in detail.certificationUrls"
              :key="img"
              :src="img"
              :preview-src-list="detail.certificationUrls"
              :initial-index="idx"
              fit="cover"
              class="evidence-image"
              preview-teleported
            />
          </div>

          <!-- 生产记录 -->
          <div class="records-block">
            <div class="block-title record-title">
              <span>生产记录（{{ detail.productionRecords.length }}）</span>
              <el-button type="primary" size="small" @click="openRecordCreate">添加记录</el-button>
            </div>
            <el-table :data="detail.productionRecords" size="small" style="width: 100%">
              <el-table-column label="类型" width="110" align="center">
                <template #default="{ row }">{{ row.recordTypeText || row.recordType }}</template>
              </el-table-column>
              <el-table-column label="日期" width="110" align="center">
                <template #default="{ row }">{{ row.recordDate }}</template>
              </el-table-column>
              <el-table-column label="记录内容" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">{{ row.content }}</template>
              </el-table-column>
              <el-table-column label="操作人" width="100" align="center">
                <template #default="{ row }">{{ row.operator || '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openRecordEdit(row as ProductionRecordVO)">编辑</el-button>
                  <el-button link type="danger" size="small" @click="handleRecordDelete(row as ProductionRecordVO)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 生产记录弹窗 -->
    <el-dialog v-model="recordVisible" :title="editingRecordId ? '编辑生产记录' : '添加生产记录'" width="560px" destroy-on-close>
      <el-form :model="recordForm" label-width="88px">
        <el-form-item label="记录类型">
          <el-select v-model="recordForm.recordType" style="width: 100%">
            <el-option v-for="opt in RECORD_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="记录日期" required>
          <el-date-picker v-model="recordForm.recordDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="记录内容" required>
          <el-input v-model="recordForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="如：施用有机肥 200kg，追肥 20kg" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="recordForm.operator" placeholder="选填" maxlength="30" />
        </el-form-item>
        <el-form-item label="记录图片">
          <div class="img-uploader">
            <template v-for="(url, idx) in (recordForm.images || [])" :key="'saved-' + idx">
              <div class="img-item">
                <el-image :src="url" fit="cover" class="img-preview" :preview-src-list="recordForm.images" :initial-index="idx" preview-teleported />
                <button type="button" class="img-remove" aria-label="移除图片" @click="removeRecImage(idx)">×</button>
              </div>
            </template>
            <template v-for="(url, idx) in recPreviewUrls" :key="'local-' + idx">
              <div class="img-item">
                <el-image :src="url" fit="cover" class="img-preview" />
                <button type="button" class="img-remove" aria-label="移除图片" @click="removeRecImage(idx)">×</button>
              </div>
            </template>
            <label for="trace-rec-input" class="img-add" title="上传记录图片">
              <span class="img-add-icon">＋</span>
              <span class="img-add-text">添加图片</span>
            </label>
            <input id="trace-rec-input" type="file" accept="image/*" hidden @change="handleRecImageSelect" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordVisible = false">取消</el-button>
        <el-button type="primary" :loading="recordLoading" @click="handleRecordSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { min-height: 100%; }

.batch-no {
  font-family: Consolas, Menlo, monospace;
  font-weight: 600;
  color: #14532D;
  letter-spacing: 0.02em;
}

.product-fixed {
  display: flex;
  align-items: center;
  min-height: 32px;
  padding: 4px 12px;
  width: 100%;
  box-sizing: border-box;
  background: #F8FAF9;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  font-size: 14px;
  color: #374151;
}

.product-img {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  flex-shrink: 0;
}

.product-img-empty {
  background: #F0FDF4;
  border: 1px dashed #BBF7D0;
}

.qr-thumb {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  border: 1px solid #E5E7EB;
  cursor: pointer;
}

.detail-body { min-height: 120px; }

.qr-block {
  margin-bottom: 1rem;
}

.block-title {
  font-size: 14px;
  font-weight: 600;
  color: #14532D;
  margin-bottom: 0.5rem;
}

.record-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.qr-large {
  width: 140px;
  height: 140px;
  border: 1px solid #E5E7EB;
  border-radius: 10px;
  padding: 6px;
  cursor: pointer;
}

.qr-placeholder {
  display: inline-block;
  padding: 8px 14px;
  margin-bottom: 8px;
  background: #FAFBFA;
  border: 1px dashed #D1D5DB;
  border-radius: 8px;
  font-size: 13px;
  color: #6B7280;
}

.qr-hint {
  font-size: 12px;
  color: #9CA3AF;
  margin-top: 6px;
}

.evidence-block {
  margin-bottom: 1rem;
}

.evidence-image {
  width: 90px;
  height: 90px;
  border-radius: 8px;
  margin-right: 0.5rem;
  border: 1px solid #E5E7EB;
}

.records-block { margin-top: 1rem; }

/* ── 图片上传器 ── */
.img-uploader {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
  width: 100%;
}

.img-item {
  position: relative;
  width: 72px;
  height: 72px;
}

.img-preview {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  border: 1px solid #E5E7EB;
  display: block;
}

.img-remove {
  position: absolute;
  top: -7px;
  right: -7px;
  width: 20px;
  height: 20px;
  line-height: 18px;
  border: none;
  border-radius: 50%;
  background: #EF4444;
  color: #FFFFFF;
  font-size: 14px;
  cursor: pointer;
  padding: 0;
}

.img-add {
  width: 72px;
  height: 72px;
  border: 1px dashed #BBF7D0;
  border-radius: 8px;
  background: #F0FDF4;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
  color: #15803D;
  transition: background-color 0.15s ease-out;
}

.img-add:hover {
  background: #DCFCE7;
}

.img-add-icon {
  font-size: 20px;
  line-height: 1;
}

.img-add-text {
  font-size: 11px;
}
</style>
