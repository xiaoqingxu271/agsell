<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAnnouncements, createAnnouncement, updateAnnouncement, deleteAnnouncement } from '@/api/admin'
import type { SysNoticeVO } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const list = ref<SysNoticeVO[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增公告')
const formLoading = ref(false)
const form = ref<SysNoticeVO>({ id: undefined, title: '', content: '', sort: 0, status: 1 })

async function fetchList() {
  loading.value = true
  try {
    list.value = await listAnnouncements()
  } catch {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

function openAdd() {
  dialogTitle.value = '新增公告'
  form.value = { id: undefined, title: '', content: '', sort: 0, status: 1 }
  dialogVisible.value = true
}

function openEdit(row: SysNoticeVO) {
  dialogTitle.value = '编辑公告'
  form.value = { ...row }
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.value.title?.trim()) {
    ElMessage.warning('请输入公告标题')
    return
  }
  formLoading.value = true
  try {
    if (form.value.id) {
      await updateAnnouncement(form.value)
    } else {
      await createAnnouncement(form.value)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    formLoading.value = false
  }
}

async function handleDelete(row: SysNoticeVO) {
  await ElMessageBox.confirm(`确认删除公告「${row.title}」？`, '提示', { type: 'warning' })
  try {
    await deleteAnnouncement(Number(row.id))
    ElMessage.success('已删除')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

function formatTime(time: string | null): string {
  return time ? time.replace('T', ' ').substring(0, 19) : '-'
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <PageHeader title="公告管理" description="维护小程序首页公告条（展示中的条目游客可见）" />

    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">公告列表</span>
          <el-button type="primary" @click="openAdd">新增公告</el-button>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="90" align="center" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '展示中' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="150" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row as SysNoticeVO)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as SysNoticeVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="一句话公告，如：产地直发季" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="3" maxlength="500" show-word-limit
            placeholder="展开查看的详细内容（可选）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
          <span class="sort-hint">越大越前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="展示"
            inactive-text="隐藏" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sort-hint {
  margin-left: 10px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
