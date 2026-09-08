<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listProducts,
  deleteProduct,
  updateProductStatus,
  saveOrUpdateProduct,
  getCategoryTree,
} from '@/api/admin'
import { uploadFile } from '@/api/upload'
import type { ProductListItemVO, ProductQueryRequest, CategoryTreeVO, ProductSpecDTO, ProductCreateRequest } from '@/types'

// ── 列表状态 ──
const loading = ref(false)
const productList = ref<ProductListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const categoryId = ref<number | undefined>()
const statusFilter = ref<number | null>(null)

// ── 分类 ──
const categoryOptions = ref<{ label: string; value: number; children?: { label: string; value: number }[] }[]>([])
const categoryLoading = ref(false)

async function fetchCategoryTree() {
  categoryLoading.value = true
  try {
    const res = await getCategoryTree()
    categoryOptions.value = res.map((cat: CategoryTreeVO) => ({
      label: cat.name,
      value: cat.id,
      children: cat.children?.map((sub: CategoryTreeVO) => ({
        label: sub.name,
        value: sub.id,
      })),
    }))
  } catch {
    // interceptor handles error
  } finally {
    categoryLoading.value = false
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params: ProductQueryRequest = {
      pageNum: page.value,
      pageSize: pageSize.value,
      name: keyword.value || undefined,
      categoryId: categoryId.value,
      status: statusFilter.value,
    }
    const res = await listProducts(params)
    productList.value = res.records
    total.value = Number(res.total)
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
  categoryId.value = undefined
  statusFilter.value = null
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

async function handleStatusChange(row: ProductListItemVO, status: number) {
  try {
    await updateProductStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '已上架' : '已下架')
  } catch {
    fetchList()
  }
}

async function handleDelete(row: ProductListItemVO) {
  await ElMessageBox.confirm(`确认删除商品「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await deleteProduct(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

// ── 新增/编辑弹窗 ──
const formDialogVisible = ref(false)
const formTitle = ref('新增商品')
const formRef = ref()
const formLoading = ref(false)

const form = ref({
  id: undefined as number | undefined,
  name: '',
  subtitle: '',
  categoryId: undefined as number | undefined,
  price: 0,
  originalPrice: undefined as number | undefined,
  stock: 0,
  mainImage: '',
  description: '',
  origin: '',
  harvestDate: '',
  shelfLife: '',
  storage: '',
  status: 1,
  sort: 0,
})

const specs = ref<ProductSpecDTO[]>([{ specName: '', price: 0, stock: 0 }])

// 待上传的图片和预览 URL
const pendingMainFile = ref<File | null>(null)
const pendingMainUrl = ref<string | null>(null)
const pendingSpecFiles = ref<(File | null)[]>([null])
const pendingSpecUrls = ref<(string | null)[]>([null])

function openCreate() {
  formTitle.value = '新增商品'
  form.value = {
    id: undefined, name: '', subtitle: '', categoryId: undefined,
    price: 0, originalPrice: undefined, stock: 0, mainImage: '',
    description: '', origin: '', harvestDate: '', shelfLife: '',
    storage: '', status: 1, sort: 0,
  }
  specs.value = [{ specName: '', price: 0, stock: 0 }]
  pendingMainFile.value = null
  pendingMainUrl.value = null
  pendingSpecFiles.value = [null]
  pendingSpecUrls.value = [null]
  formDialogVisible.value = true
}

function openEdit(row: ProductListItemVO) {
  formTitle.value = '编辑商品'
  form.value = {
    id: row.id, name: row.name, subtitle: row.subtitle ?? '',
    categoryId: row.categoryId, price: row.price,
    originalPrice: row.originalPrice ?? undefined, stock: row.stock,
    mainImage: row.mainImage ?? '', description: '', origin: '',
    harvestDate: '', shelfLife: '', storage: '',
    status: row.status, sort: 0,
  }
  specs.value = [{ specName: '', price: 0, stock: 0 }]
  pendingMainFile.value = null
  pendingMainUrl.value = null
  pendingSpecFiles.value = [null]
  pendingSpecUrls.value = [null]
  formDialogVisible.value = true
}

function handleAddSpec() {
  specs.value.push({ specName: '', price: 0, stock: 0 })
  pendingSpecFiles.value.push(null)
  pendingSpecUrls.value.push(null)
}

function handleRemoveSpec(index: number) {
  if (pendingSpecUrls.value[index]) {
    URL.revokeObjectURL(pendingSpecUrls.value[index]!)
  }
  pendingSpecFiles.value.splice(index, 1)
  pendingSpecUrls.value.splice(index, 1)
  specs.value.splice(index, 1)
}

async function handleFormSubmit() {
  if (!form.value.name?.trim()) { ElMessage.warning('请输入商品名称'); return }
  // el-cascader 返回数组路径，取最后一个元素作为 categoryId
  const categoryPath = form.value.categoryId as unknown as number[] | number | undefined
  const categoryId = Array.isArray(categoryPath) ? categoryPath[categoryPath.length - 1] : categoryPath
  if (!categoryId) { ElMessage.warning('请选择分类'); return }
  if (form.value.price <= 0) { ElMessage.warning('请输入正确价格'); return }
  if (form.value.stock < 0) { ElMessage.warning('库存不能为负数'); return }
  formLoading.value = true
  try {
    // 1. 先上传所有待上传图片到 OSS
    let finalMainImage = form.value.mainImage || ''
    if (pendingMainFile.value) {
      finalMainImage = await uploadFile(pendingMainFile.value, 'product/image')
      pendingMainFile.value = null
      if (pendingMainUrl.value) {
        URL.revokeObjectURL(pendingMainUrl.value)
        pendingMainUrl.value = null
      }
    }

    const validSpecs = specs.value.filter(s => s.specName?.trim())
    // 并行上传所有规格图片
    const finalSpecs = await Promise.all(validSpecs.map(async (spec, i) => {
      let image = spec.image || ''
      const fileIdx = specs.value.indexOf(spec)
      if (pendingSpecFiles.value[fileIdx]) {
        image = await uploadFile(pendingSpecFiles.value[fileIdx]!, 'product/spec')
        pendingSpecFiles.value[fileIdx] = null
        if (pendingSpecUrls.value[fileIdx]) {
          URL.revokeObjectURL(pendingSpecUrls.value[fileIdx]!)
          pendingSpecUrls.value[fileIdx] = null
        }
      }
      return { ...spec, image }
    }))

    const payload = { ...form.value, categoryId, mainImage: finalMainImage, specs: finalSpecs } as ProductCreateRequest
    await saveOrUpdateProduct(payload)
    ElMessage.success(form.value.id ? '编辑成功' : '新增成功')
    formDialogVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    formLoading.value = false
  }
}

// ── 图片选择 helper（只预览本地，不立即上传）──
function handleMainImageSelect(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (pendingMainUrl.value) URL.revokeObjectURL(pendingMainUrl.value)
  pendingMainFile.value = file
  pendingMainUrl.value = URL.createObjectURL(file)
}

function handleSpecImageSelect(index: number, event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  // 释放旧的预览 URL
  if (pendingSpecUrls.value[index]) URL.revokeObjectURL(pendingSpecUrls.value[index]!)
  pendingSpecFiles.value[index] = file
  pendingSpecUrls.value[index] = URL.createObjectURL(file)
}

function formatTime(time: string): string {
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  fetchCategoryTree().then(fetchList)
})
</script>

<template>
  <div class="page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="admin-search-card">
      <el-form :inline="true" :model="{ keyword, categoryId, statusFilter }" @submit.prevent="handleSearch">
        <el-form-item label="商品名称">
          <el-input v-model="keyword" placeholder="请输入关键字" clearable style="width: 180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="所属分类">
          <el-cascader
            v-model="categoryId"
            :options="categoryOptions"
            placeholder="全部分类"
            clearable
            style="width: 200px"
            :loading="categoryLoading"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
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
        <div class="admin-card-header">
          <span class="admin-card-title">商品列表</span>
          <el-button type="primary" @click="openCreate">

            新增商品
          </el-button>
        </div>
      </template>

      <el-table class="admin-table" :data="productList" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="190" align="center" show-overflow-tooltip />
        <el-table-column label="商品图片" width="120" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.mainImage"
              :src="row.mainImage"
              :preview-src-list="[row.mainImage]"
              fit="cover"
              alt="商品图片"
              style="width: 40px; height: 40px; border-radius: 4px"
            />
            <span v-else class="admin-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="180" align="center" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="100" align="center" show-overflow-tooltip />
        <el-table-column label="价格" width="150" align="center">
          <template #default="{ row }">
            <span class="admin-price">¥{{ Number(row.price ?? 0).toFixed(2) }}</span>
            <span v-if="row.originalPrice" class="admin-original-price">¥{{ Number(row.originalPrice).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" align="center" />
        <el-table-column prop="sales" label="销量" width="80" align="center" />
        <el-table-column label="状态" width="200" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="上架"
              inactive-text="下架"
              active-color="#15803D"
              @change="(val: boolean) => handleStatusChange(row, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openEdit(row)">编辑</el-button>
            <el-divider direction="vertical" class="admin-action-divider" />
            <el-button link type="danger" size="small" class="admin-action-btn" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="formTitle"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="商品名称" required>
              <el-input v-model="form.name" placeholder="请输入商品名称" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="副标题">
              <el-input v-model="form.subtitle" placeholder="请输入副标题" maxlength="200" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属分类" required>
              <el-cascader
                v-model="form.categoryId"
                :options="categoryOptions"
                placeholder="请选择分类"
                style="width: 100%"
                :loading="categoryLoading"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="售价" required>
              <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="原价">
              <el-input-number v-model="form.originalPrice" :min="0" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库存" required>
              <el-input-number v-model="form.stock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="主图">
          <div style="display:flex;align-items:center;gap:12px">
            <input type="file" accept="image/*" style="display:none" id="product-main-img"
              @change="handleMainImageSelect" />
            <label for="product-main-img" class="upload-label">选择图片</label>
            <el-image
              v-if="pendingMainUrl"
              :src="pendingMainUrl"
              fit="cover"
              alt="商品主图预览"
              style="width:80px;height:80px;border-radius:8px"
              :preview-src-list="[pendingMainUrl]"
            />
            <el-image
              v-else-if="form.mainImage"
              :src="form.mainImage"
              fit="cover"
              alt="商品主图"
              style="width:80px;height:80px;border-radius:8px"
              :preview-src-list="[form.mainImage]"
            />
            <span v-else class="no-img">暂无图片</span>
          </div>
        </el-form-item>
        <el-form-item label="产地">
          <el-input v-model="form.origin" placeholder="请输入产地" />
        </el-form-item>
        <el-form-item label="采摘日期">
          <el-input v-model="form.harvestDate" placeholder="如：2024-03-01" />
        </el-form-item>
        <el-form-item label="保质期">
          <el-input v-model="form.shelfLife" placeholder="如：7天" />
        </el-form-item>
        <el-form-item label="储存方式">
          <el-input v-model="form.storage" placeholder="如：冷藏" />
        </el-form-item>
        <el-form-item label="商品详情">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入商品详情HTML" />
        </el-form-item>
        <el-form-item label="规格">
          <el-table :data="specs" border size="small">
            <el-table-column label="规格名称" width="120">
              <template #default="{ $index }">
                <el-input v-model="specs[$index]!.specName" placeholder="如：500g装" />
              </template>
            </el-table-column>
            <el-table-column label="售价" width="100">
              <template #default="{ $index }">
                <el-input-number v-model="specs[$index]!.price" :min="0" :precision="2" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="库存" width="80">
              <template #default="{ $index }">
                <el-input-number v-model="specs[$index]!.stock" :min="0" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="图片" width="120">
              <template #default="{ $index }">
                <input type="file" accept="image/*" style="display:none"
                  :id="`spec-img-${$index}`"
                  @change="(e) => handleSpecImageSelect($index, e)" />
                <label :for="`spec-img-${$index}`" class="spec-img-upload">
                  <el-image
                    v-if="pendingSpecUrls[$index]"
                    :src="pendingSpecUrls[$index]!"
                    fit="cover"
                    alt="规格图片预览"
                    style="width:44px;height:44px;border-radius:4px"
                    :preview-src-list="[pendingSpecUrls[$index]!]"
                  />
                  <el-image
                    v-else-if="specs[$index]!.image"
                    :src="specs[$index]!.image"
                    fit="cover"
                    alt="规格图片"
                    style="width:44px;height:44px;border-radius:4px"
                    :preview-src-list="[specs[$index]!.image!]"
                  />
                  <span v-else class="spec-img-placeholder">+</span>
                </label>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="handleRemoveSpec($index)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button link type="primary" size="small" @click="handleAddSpec" style="margin-top: 8px">

            添加规格
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { min-height: 100%; }

.upload-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 36px;
  border: 1px dashed #D1D5DB;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #6B7280;
  transition: all 0.2s;
  flex-shrink: 0;
}
.upload-label:hover {
  border-color: #15803D;
  color: #15803D;
}

.no-img {
  font-size: 13px;
  color: #9CA3AF;
}

.spec-img-upload {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px dashed #D1D5DB;
  border-radius: 4px;
  cursor: pointer;
  color: #9CA3AF;
  transition: all 0.2s;
}
.spec-img-upload:hover {
  border-color: #15803D;
  color: #15803D;
}

.spec-img-placeholder {
  font-size: 20px;
  line-height: 1;
}
</style>
