<script setup lang="ts">
/**
 * AI 对话审计（只读）：逐轮对话原文、意图、工具、拒答与耗时。
 *
 * 数据源 ai_chat_log 由 Python 在每轮回复下发后的收尾阶段旁路写入，本页无任何
 * 写接口——审计数据不允许被后台篡改。典型用途：客诉追溯（按会话号拉全轮次）、
 * 拒答轮复盘（AI 哪些问题答不上 → 反哺知识库）、合规抽查。
 */
import { ref, onMounted } from 'vue'
import { listAiChatLogs } from '@/api/admin'
import type { AiChatLogVO } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const list = ref<AiChatLogVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const sessionFilter = ref('')
const refusedFilter = ref<number | null>(null)

/** 展开查看完整问答的行 id（表格里默认截断） */
const expandedId = ref<string | number | null>(null)

async function fetchList() {
  loading.value = true
  try {
    const res = await listAiChatLogs({
      pageNum: page.value,
      pageSize: pageSize.value,
      sessionId: sessionFilter.value.trim() || undefined,
      refused: refusedFilter.value === null ? undefined : refusedFilter.value,
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
  sessionFilter.value = ''
  refusedFilter.value = null
  page.value = 1
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

function toggleExpand(row: AiChatLogVO) {
  expandedId.value = expandedId.value === row.id ? null : row.id
}

function formatTime(time: string | null): string {
  return time ? time.replace('T', ' ').substring(0, 19) : '-'
}

function splitTools(raw: string | null): string[] {
  return (raw ?? '').split(',').map((t) => t.trim()).filter(Boolean)
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <PageHeader
      title="AI 对话审计"
      description="逐轮对话原文（只读）：按会话号追溯客诉、复盘拒答轮、合规抽查"
    />

    <!-- 搜索栏 -->
    <el-card shadow="never" class="admin-search-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="会话号">
          <el-input
            v-model="sessionFilter"
            placeholder="精确匹配会话号"
            clearable
            style="width: 240px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="轮次类型">
          <el-select v-model="refusedFilter" placeholder="全部" clearable style="width: 130px">
            <el-option label="正常回答" :value="0" />
            <el-option label="拒答轮" :value="1" />
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
          <span class="admin-card-title">对话记录</span>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="用户" min-width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.userId !== null && row.userId !== undefined">UID {{ row.userId }}</span>
            <el-tag v-else type="info" size="small">游客</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="会话号" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="sessionFilter = row.sessionId; handleSearch()">
              {{ row.sessionId }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="用户提问" min-width="240">
          <template #default="{ row }">
            <span class="q-text">{{ row.question }}</span>
          </template>
        </el-table-column>
        <el-table-column label="AI 回复" min-width="260">
          <template #default="{ row }">
            <span class="q-text">{{ row.reply || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工具" min-width="140">
          <template #default="{ row }">
            <template v-if="splitTools(row.tools).length">
              <el-tag
                v-for="t in splitTools(row.tools)"
                :key="t"
                size="small"
                effect="plain"
                class="tool-tag"
              >{{ t }}</el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.refused === 1 ? 'warning' : 'success'" size="small">
              {{ row.refused === 1 ? '拒答' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" min-width="80" align="center">
          <template #default="{ row }">{{ row.durationMs != null ? `${row.durationMs}ms` : '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" min-width="150" align="center">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="toggleExpand(row as AiChatLogVO)">
              {{ expandedId === row.id ? '收起' : '详情' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 展开详情：完整问答原文 + 引用条目（表格里被截断的内容在这看全文） -->
      <el-card v-if="list.find((r) => r.id === expandedId)" shadow="never" class="detail-card">
        <div
          v-for="row in list.filter((r) => r.id === expandedId)"
          :key="String(row.id)"
          class="detail-body"
        >
          <div class="detail-row">
            <span class="detail-label">提问</span>
            <span class="detail-text">{{ row.question }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">回复</span>
            <span class="detail-text">{{ row.reply || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">知识引用</span>
            <span class="detail-text">{{ row.refs || '无' }}（命中 {{ row.hits }} 条）</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">意图</span>
            <span class="detail-text">{{ row.intent || '（走智能体编排，无前置标签）' }}</span>
          </div>
        </div>
      </el-card>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="Number(total)"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.q-text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.5;
}

.tool-tag {
  margin-right: 4px;
  margin-bottom: 2px;
}

.detail-card {
  margin-top: 8px;
  background: var(--el-fill-color-lighter);
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.6;
}

.detail-label {
  flex-shrink: 0;
  width: 70px;
  color: var(--el-text-color-secondary);
}

.detail-text {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
