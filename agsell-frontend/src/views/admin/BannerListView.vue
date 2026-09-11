<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAllBanners, createBanner, updateBanner, deleteBanner } from '@/api/admin'
import { uploadFile } from '@/api/upload'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

async function fetchList() {
  loading.value = true
  try {
    const res = await listAllBanners()
    list.value = res
    total.value = res.length
  } catch {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

function handlePageChange(val: number) {
  page.value = val
}

function formatPagedData() {
  const start = (page.value - 1) * pageSize.value
  return list.value.slice(start, start + pageSize.value)
}

// ── 新增/编辑弹窗 ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增轮播图')
const formRef = ref()
const formLoading = ref(false)
const pendingBannerFile = ref<File | null>(null)
const pendingBannerUrl = ref<string | null>(null)

type BannerForm = {
  id?: number
  title: string
  image: string
  link: string
  sort: number
  status: number
}

const form = ref<BannerForm>({
  title: '', image: '', link: '', sort: 100, status: 1
})

function openAdd() {
  dialogTitle.value = '新增轮播图'
  form.value = { title: '', image: '', link: '', sort: 100, status: 1 }
  pendingBannerFile.value = null
  pendingBannerUrl.value = null
  dialogVisible.value = true
}

function openEdit(row: any) {
  dialogTitle.value = '编辑轮播图'
  form.value = { id: row.id, title: row.title, image: row.image, link: row.link ?? '', sort: row.sort, status: row.status }
  pendingBannerFile.value = null
  pendingBannerUrl.value = null
  dialogVisible.value = true
}

function handleBannerImageSelect(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (pendingBannerUrl.value) URL.revokeObjectURL(pendingBannerUrl.value)
  pendingBannerFile.value = file
  pendingBannerUrl.value = URL.createObjectURL(file)
}

async function handleSubmit() {
  if (!form.value.title?.trim()) { ElMessage.warning('请输入标题'); return }
  let finalImageUrl = form.value.image || ''
  if (pendingBannerFile.value) {
    finalImageUrl = await uploadFile(pendingBannerFile.value, 'banner')
    pendingBannerFile.value = null
    if (pendingBannerUrl.value) {
      URL.revokeObjectURL(pendingBannerUrl.value)
      pendingBannerUrl.value = null
    }
  }
  if (!finalImageUrl?.trim()) { ElMessage.warning('请选择或填写轮播图图片'); return }
  formLoading.value = true
  try {
    const payload = {
      ...(form.value.id ? { id: form.value.id } : {}),
      title: form.value.title,
      image: finalImageUrl,
      link: form.value.link || '/',
      sort: form.value.sort,
      status: form.value.status,
    }
    if (form.value.id) {
      await updateBanner(form.value.id!, payload)
    } else {
      await createBanner(payload)
    }
    ElMessage.success(form.value.id ? '编辑成功' : '新增成功')
    dialogVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    formLoading.value = false
  }
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确认删除轮播图「${row.title}」？`, '提示', { type: 'warning' })
  try {
    await deleteBanner(row.id!)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

async function handleStatusChange(row: any, status: number) {
  try {
    await updateBanner(row.id, { ...row, status })
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
  } catch {
    fetchList()
  }
}

function formatTime(time: string): string {
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <!-- 页头 + 操作 -->
    <PageHeader title="轮播图管理" description="维护首页轮播图与跳转链接">
      <el-button type="primary" @click="openAdd">新增轮播图</el-button>
    </PageHeader>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table class="admin-table" :data="formatPagedData()" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="220" align="center" show-overflow-tooltip />
        <el-table-column label="预览图" width="180" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.image"
              :src="row.image"
              fit="cover"
              alt="轮播图预览"
              style="width: 85px;height:48px;border-radius:6px"
              :preview-src-list="[row.image]"
              preview-teleported
            />
            <span v-else class="admin-empty">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="140" align="center" show-overflow-tooltip />
        <el-table-column prop="link" label="跳转链接" min-width="180" align="center" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="100" align="center" />
        <el-table-column label="状态" width="200" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="启用"
              inactive-text="禁用"
              active-color="#15803D"
              @change="(val: unknown) => handleStatusChange(row, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="220" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openEdit(row)">编辑</el-button>
            <el-divider direction="vertical" class="admin-action-divider" />
            <el-button link type="danger" size="small" class="admin-action-btn" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="请输入标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="轮播图" required>
          <div style="display:flex;align-items:center;gap:12px">
            <input type="file" accept="image/*" style="display:none" id="banner-img-upload"
              @change="handleBannerImageSelect" />
            <label for="banner-img-upload" class="upload-label">选择图片</label>
            <el-image
              v-if="pendingBannerUrl"
              :src="pendingBannerUrl"
              fit="cover"
              alt="轮播图预览"
              style="width:80px;height:44px;border-radius:6px"
              :preview-src-list="[pendingBannerUrl]"
              preview-teleported
            />
            <el-image
              v-else-if="form.image"
              :src="form.image"
              fit="cover"
              alt="轮播图"
              style="width:80px;height:44px;border-radius:6px"
              :preview-src-list="[form.image]"
              preview-teleported
            />
            <span v-else class="no-img">暂无图片</span>
          </div>
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="form.link" placeholder="如：/product/list?categoryId=1" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sort" :min="0" :max="999" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width:100%">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
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
</style>
