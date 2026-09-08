<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listReviews, replyReview, deleteReview } from '@/api/admin'
import type { ReviewListItemVO, ReplyRequest } from '@/types'

const loading = ref(false)
const reviewList = ref<ReviewListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const productId = ref<number | undefined>()
const repliedFilter = ref<number | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const res = await listReviews({
      pageNum: page.value,
      pageSize: pageSize.value,
      productId: productId.value,
      replied: repliedFilter.value === null ? undefined : repliedFilter.value === 1,
    })
    reviewList.value = res.records
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
  productId.value = undefined
  repliedFilter.value = null
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

function formatTime(time: string): string {
  return time.replace('T', ' ').substring(0, 19)
}

function ratingStars(rating: number): string {
  return '★'.repeat(rating) + '☆'.repeat(5 - rating)
}

// ── 回复弹窗 ──
const replyVisible = ref(false)
const replyId = ref<number | null>(null)
const replyForm = ref<ReplyRequest>({ replyContent: '' })
const replyLoading = ref(false)
const replyTarget = ref<ReviewListItemVO | null>(null)

function openReply(row: ReviewListItemVO) {
  replyTarget.value = row
  replyId.value = row.id
  replyForm.value = { replyContent: row.replied ? (row as any).replyContent ?? '' : '' }
  replyVisible.value = true
}

async function handleReplySubmit() {
  if (!replyForm.value.replyContent?.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  replyLoading.value = true
  try {
    await replyReview(replyId.value!, replyForm.value)
    ElMessage.success('回复成功')
    replyVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    replyLoading.value = false
  }
}

// ── 删除 ──
async function handleDelete(row: ReviewListItemVO) {
  await ElMessageBox.confirm(`确认删除该评价？`, '提示', { type: 'warning' })
  try {
    await deleteReview(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="admin-search-card">
      <el-form :inline="true" :model="{ productId, repliedFilter }" @submit.prevent="handleSearch">
        <el-form-item label="商品ID">
          <el-input
            v-model.number="productId"
            type="number"
            placeholder="按商品筛选"
            clearable
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="回复状态">
          <el-select v-model="repliedFilter" placeholder="全部" clearable style="width: 120px">
            <el-option label="未回复" :value="0" />
            <el-option label="已回复" :value="1" />
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
        <div class="admin-card-header">
          <span class="admin-card-title">评价列表</span>
        </div>
      </template>

      <el-table class="admin-table" :data="reviewList" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="120" align="center" show-overflow-tooltip />
        <el-table-column label="商品" min-width="100" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.productName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="120" align="center">
          <template #default="{ row }">
            <span class="admin-rating">{{ ratingStars(row.rating) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="120" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="admin-content">{{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="用户" width="120" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.userName ?? (row.isAnonymous === 1 ? '匿名用户' : '-') }}
          </template>
        </el-table-column>
        <el-table-column label="回复状态" width="200" align="center">
          <template #default="{ row }">
            <el-tag :type="row.replied ? 'success' : 'info'" class="admin-status-tag" size="small">
              {{ row.replied ? '已回复' : '未回复' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openReply(row)">
              {{ row.replied ? '查看回复' : '回复' }}
            </el-button>
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

    <!-- 回复弹窗 -->
    <el-dialog
      v-model="replyVisible"
      :title="replyTarget?.replied ? '查看回复' : '回复评价'"
      width="560px"
      destroy-on-close
    >
      <template v-if="replyTarget">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 1rem">
          <el-descriptions-item label="商品">{{ replyTarget.productName }}</el-descriptions-item>
          <el-descriptions-item label="评分">
            <span class="rating">{{ ratingStars(replyTarget.rating) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="用户">
            {{ replyTarget.userName ?? (replyTarget.isAnonymous === 1 ? '匿名用户' : '-') }}
          </el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(replyTarget.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="评价内容" :span="2">
            {{ replyTarget.content }}
          </el-descriptions-item>
          <el-descriptions-item label="卖家回复" :span="2">
            <template v-if="replyTarget.replied">
              <el-tag type="success" size="small" style="margin-right: 0.5rem">已回复</el-tag>
              <span>{{ replyTarget.replyContent ?? '-' }}</span>
            </template>
            <span v-else class="text-muted">暂无回复</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-form v-if="!replyTarget.replied" label-width="70px">
          <el-form-item label="回复内容">
            <el-input
              v-model="replyForm.replyContent"
              type="textarea"
              :rows="3"
              placeholder="请输入回复内容"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="replyVisible = false">关闭</el-button>
        <el-button
          v-if="!replyTarget?.replied"
          type="primary"
          :loading="replyLoading"
          @click="handleReplySubmit"
        >
          提交回复
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { min-height: 100%; }

/* 弹窗内评分星：丰收金 #A16207 */
.rating {
  color: #A16207;
  letter-spacing: 2px;
}

.text-muted {
  color: #9CA3AF;
  font-size: 13px;
}
</style>
