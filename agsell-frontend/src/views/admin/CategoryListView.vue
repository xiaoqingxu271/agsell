<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCategoryItems,
  saveOrUpdateCategory,
  deleteCategory,
  updateCategoryStatus,
} from '@/api/admin'
import { uploadFile } from '@/api/upload'
import type { CategoryListItemVO, CategoryCreateRequest } from '@/types'

const loading = ref(false)
const list = ref<CategoryListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')

async function fetchList() {
  loading.value = true
  try {
    const all = await listCategoryItems()
    const filtered = keyword.value
      ? all.filter(c => c.name.includes(keyword.value))
      : all
    const start = (page.value - 1) * pageSize.value
    list.value = filtered.slice(start, start + pageSize.value)
    total.value = filtered.length
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
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

// ── 新增/编辑弹窗 ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增分类')
const formRef = ref()
const formLoading = ref(false)
const form = ref<CategoryCreateRequest>({ name: '', parentId: 0, sort: 0 })
const pendingIconFile = ref<File | null>(null)
const pendingIconUrl = ref<string | null>(null)

function openAdd() {
  dialogTitle.value = '新增分类'
  form.value = { name: '', parentId: 0, sort: 0 }
  pendingIconFile.value = null
  pendingIconUrl.value = null
  dialogVisible.value = true
}

function openEdit(row: CategoryListItemVO) {
  dialogTitle.value = '编辑分类'
  form.value = { id: row.id, name: row.name, icon: row.icon ?? '', parentId: row.parentId, sort: row.sort }
  pendingIconFile.value = null
  pendingIconUrl.value = null
  dialogVisible.value = true
}

function handleIconSelect(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  // 释放旧的预览 URL
  if (pendingIconUrl.value) URL.revokeObjectURL(pendingIconUrl.value)
  pendingIconFile.value = file
  pendingIconUrl.value = URL.createObjectURL(file)
}

async function handleSubmit() {
  if (!form.value.name?.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  formLoading.value = true
  try {
    // 如果有待上传的图片，先上传到 OSS
    let iconUrl = form.value.icon || ''
    if (pendingIconFile.value) {
      iconUrl = await uploadFile(pendingIconFile.value, 'category/icon')
      pendingIconFile.value = null
      if (pendingIconUrl.value) {
        URL.revokeObjectURL(pendingIconUrl.value)
        pendingIconUrl.value = null
      }
    }
    await saveOrUpdateCategory({ ...form.value, icon: iconUrl })
    ElMessage.success(form.value.id ? '编辑成功' : '新增成功')
    dialogVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    formLoading.value = false
  }
}

async function handleStatusChange(row: CategoryListItemVO, status: number) {
  try {
    await updateCategoryStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
  } catch {
    fetchList()
  }
}

async function handleDelete(row: CategoryListItemVO) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await deleteCategory(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

// ── 图标上传 helper（已移除，改为在 handleSubmit 中统一上传）──

function formatTime(time: string): string {
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="{ keyword }" style="height: 30px" @submit.prevent="handleSearch">
        <el-form-item label="分类名称">
          <el-input
            v-model="keyword"
            placeholder="请输入分类名称"
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
        <div class="admin-card-header">
        <span class="admin-card-title">分类列表</span>
          <el-button type="primary" @click="openAdd()">

            新增分类
          </el-button>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="180" align="center" show-overflow-tooltip />
        <el-table-column prop="name" label="分类名称" min-width="100" align="center" show-overflow-tooltip />
        <el-table-column label="分类图标" width="120" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.icon"
              :src="row.icon"
              fit="contain"
              alt="分类图标"
              style="width: 32px; height: 32px"
            />
            <span v-else class="admin-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="140" align="center" />
        <el-table-column label="状态" width="220" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="启用"
              inactive-text="禁用"
              active-color="#15803D"
              @change="(val: boolean) => handleStatusChange(row, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="120" align="center" show-overflow-tooltip>
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
      v-model="dialogVisible"
      :title="dialogTitle"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="分类名称" required>
          <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="分类图标">
          <div style="display:flex;align-items:center;gap:12px">
            <input type="file" accept="image/*" style="display:none" id="cat-icon-upload"
              @change="handleIconSelect" />
            <label for="cat-icon-upload" class="icon-upload-label">选择图标</label>
            <el-image
              v-if="pendingIconUrl"
              :src="pendingIconUrl"
              fit="contain"
              alt="分类图标预览"
              style="width:48px;height:48px;border-radius:8px"
              :preview-src-list="[pendingIconUrl]"
            />
            <el-image
              v-else-if="form.icon"
              :src="form.icon"
              fit="contain"
              alt="分类图标"
              style="width:48px;height:48px;border-radius:8px"
              :preview-src-list="[form.icon]"
            />
            <span v-else class="no-icon">暂无图标</span>
          </div>
        </el-form-item>
        <el-form-item label="上级分类">
          <el-select v-model="form.parentId" placeholder="选择上级分类" style="width: 100%">
            <el-option label="顶级分类" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
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
.page {
  min-height: 100%;
}

.search-card {
  margin-bottom: 1rem;
}

.icon-upload-label {
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
.icon-upload-label:hover {
  border-color: #15803D;
  color: #15803D;
}

.no-icon {
  font-size: 13px;
  color: #9CA3AF;
}
</style>
