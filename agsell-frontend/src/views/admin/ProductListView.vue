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

function openCreate() {
  formTitle.value = '新增商品'
  form.value = {
    id: undefined, name: '', subtitle: '', categoryId: undefined,
    price: 0, originalPrice: undefined, stock: 0, mainImage: '',
    description: '', origin: '', harvestDate: '', shelfLife: '',
    storage: '', status: 1, sort: 0,
  }
  specs.value = [{ specName: '', price: 0, stock: 0 }]
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
  formDialogVisible.value = true
}

function handleAddSpec() {
  specs.value.push({ specName: '', price: 0, stock: 0 })
}

function handleRemoveSpec(index: number) {
  specs.value.splice(index, 1)
}

async function handleFormSubmit() {
  if (!form.value.name?.trim()) { ElMessage.warning('请输入商品名称'); return }
  if (!form.value.categoryId) { ElMessage.warning('请选择分类'); return }
  if (form.value.price <= 0) { ElMessage.warning('请输入正确价格'); return }
  if (form.value.stock < 0) { ElMessage.warning('库存不能为负数'); return }
  formLoading.value = true
  try {
    const validSpecs = specs.value.filter(s => s.specName?.trim())
    const payload = { ...form.value, specs: validSpecs.length > 0 ? validSpecs : undefined } as ProductCreateRequest
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
    <el-card shadow="never" class="search-card">
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
        <div class="card-header">
          <span class="card-title">商品列表</span>
          <el-button type="primary" @click="openCreate">
            
            新增商品
          </el-button>
        </div>
      </template>

      <el-table :data="productList" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="商品图片" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.mainImage"
              :src="row.mainImage"
              :preview-src-list="[row.mainImage]"
              fit="cover"
              style="width: 44px; height: 44px; border-radius: 4px"
            />
            <span v-else class="no-img">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column label="价格" width="110" align="right">
          <template #default="{ row }">
            <span class="price">¥{{ row.price.toFixed(2) }}</span>
            <span v-if="row.originalPrice" class="original-price">¥{{ row.originalPrice.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" align="center" />
        <el-table-column prop="sales" label="销量" width="80" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="上架"
              inactive-text="下架"
              @change="(val: boolean) => handleStatusChange(row, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-divider direction="vertical" />
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
          <el-input v-model="form.mainImage" placeholder="请输入主图URL" />
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
                <el-input v-model="specs[$index]!.image" placeholder="图片URL" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="handleRemoveSpec($index)">
                  
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

.price { color: #f56c6c; font-weight: 600; }
.original-price {
  color: #909399;
  font-size: 0.75rem;
  text-decoration: line-through;
  margin-left: 0.25rem;
}
.no-img { color: #c0c4cc; font-size: 0.875rem; }

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
