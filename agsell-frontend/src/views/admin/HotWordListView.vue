<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listHotWords,
  saveOrUpdateHotWord,
  updateHotWordStatus,
  deleteHotWord,
} from '@/api/admin'
import type { SearchHotWord, SearchHotWordRequest } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const list = ref<SearchHotWord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

async function fetchList() {
  loading.value = true
  try {
    const res = await listHotWords({ pageNum: page.value, pageSize: pageSize.value })
    list.value = res.records
    total.value = Number(res.total)
  } catch {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

// ── 新增/编辑弹窗 ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增热词')
const formLoading = ref(false)
const form = ref<SearchHotWordRequest>({ word: '', sort: 0, status: 1 })

function openAdd() {
  dialogTitle.value = '新增热词'
  form.value = { word: '', sort: 0, status: 1 }
  dialogVisible.value = true
}

function openEdit(row: SearchHotWord) {
  dialogTitle.value = '编辑热词'
  // 编辑仅允许调整排序（word 不可变更；状态在列表页开关控制）
  form.value = { id: row.id, word: row.word, sort: row.sort, status: row.status }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.word?.trim()) {
    ElMessage.warning('请输入热词')
    return
  }
  formLoading.value = true
  try {
    await saveOrUpdateHotWord(form.value)
    ElMessage.success(form.value.id ? '编辑成功' : '新增成功')
    dialogVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    formLoading.value = false
  }
}

async function handleStatusChange(row: SearchHotWord, status: number) {
  try {
    await updateHotWordStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '已展示' : '已隐藏')
  } catch {
    fetchList()
  }
}

async function handleDelete(row: SearchHotWord) {
  await ElMessageBox.confirm(`确认删除热词「${row.word}」？`, '提示', { type: 'warning' })
  try {
    await deleteHotWord(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

function formatTime(time?: string): string {
  if (!time) return '—'
  return time.replace('T', ' ').substring(0, 19)
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <PageHeader title="搜索热词" description="维护首页热门搜索词：自动统计 + 手工配置，影响小程序搜索页热词展示" />

    <!-- 表格 -->
    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">热词列表</span>
          <el-button type="primary" @click="openAdd()">新增热词</el-button>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="200" align="center" show-overflow-tooltip />
        <el-table-column prop="word" label="热词" min-width="120" align="center" show-overflow-tooltip />
        <el-table-column label="来源" width="160" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isManual === 1 ? 'primary' : 'info'" size="small">
              {{ row.isManual === 1 ? '手工配置' : '自动统计' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="searchCount" label="搜索次数" width="160" align="center" sortable />
        <el-table-column prop="sort" label="排序" width="120" align="center" />
        <el-table-column label="状态" width="180" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="展示"
              inactive-text="隐藏"
              active-color="#15803D"
              @change="(val: unknown) => handleStatusChange(row as SearchHotWord, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openEdit(row as SearchHotWord)">编辑</el-button>
            <el-divider direction="vertical" class="admin-action-divider" />
            <el-button link type="danger" size="small" class="admin-action-btn" @click="handleDelete(row as SearchHotWord)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="440px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="热词" required>
          <el-input
            v-model="form.word"
            placeholder="请输入热词"
            maxlength="50"
            show-word-limit
            :disabled="!!form.id"
          />
          <div v-if="form.id" class="form-tip">编辑时热词不可变更，仅可调整排序；状态请直接在列表页切换</div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
          <div class="form-tip">数值越大越靠前，自动统计词默认 0，按搜索次数排序</div>
        </el-form-item>
        <el-form-item v-if="!form.id" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">展示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
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
.page {
  min-height: 100%;
}

.form-tip {
  font-size: 12px;
  color: #9CA3AF;
  line-height: 1.5;
  margin-top: 4px;
  width: 100%;
}
</style>
