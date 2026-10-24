<script setup lang="ts">
/**
 * AI 知识库管理（RAG 知识条目的唯一维护入口）
 *
 * 这一页存在的意义：此前知识库只能靠命令行维护，答辩时无法演示
 * "运营改一条知识 → 向量索引即时生效 → 客服当场答对"这条最有说服力的链路。
 *
 * 三个数分开展示是刻意的：ai_faq 里有多少条（faqCount）不等于向量库里有多少条
 * （indexedCount），也不等于商品召回索引覆盖了多少在售商品（productIndexedCount vs
 * onSaleProductCount）。历史上"知识改了 AI 还答旧话""上新商品 AI 召不到"都是这三者对不上。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createKbFaq,
  deleteKbFaq,
  kbStats,
  listKbFaqs,
  rebuildKbIndex,
  rebuildKbProductIndex,
  updateKbFaq,
  aiOpsSummary,
} from '@/api/admin'
import type { AiFaqRequest, AiFaqVO, AiKbStatsVO, AiOpsSummaryVO } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

/** Long 类字段后端统一序列化成字符串，比较/展示前先归一成数字 */
const num = (v: string | number | null | undefined): number => {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? n : 0
}

/** 与 seed_kb_extended.py 的分类一致；列表里出现过的分类会动态并进来 */
/**
 * 分类枚举取自生产库实测（select category, count(*) from ai_faq where status=1 group by category）：
 * product_knowledge 202 / category_knowledge 42 / order 10 / aftersales 6 / product 5 /
 * platform 4 / shipping 3 / account 2 / review 2 / trace 2 / seckill 2，合计 280 条。
 * 列表里若出现这里没写的分类会动态并入，筛选不会因为枚举过期而漏掉条目。
 */
const BASE_CATEGORIES = [
  'general',
  'platform',
  'shipping',
  'aftersales',
  'order',
  'product',
  'account',
  'review',
  'trace',
  'seckill',
  'product_knowledge',
  'category_knowledge',
]
const CATEGORY_LABELS: Record<string, string> = {
  general: '通用',
  platform: '平台规则',
  shipping: '物流运费',
  aftersales: '售后退款',
  order: '订单',
  product: '商品咨询',
  account: '账号',
  review: '评价',
  trace: '溯源',
  seckill: '秒杀',
  product_knowledge: '商品知识',
  category_knowledge: '类目知识',
}

const stats = ref<AiKbStatsVO | null>(null)
/** 近 7 日 AI 运营（对话量/拒答率/工具分布）：连不上 ai-service 时与状态卡一样给空态 */
const ops = ref<AiOpsSummaryVO | null>(null)
/** AI 服务（Python）连不上时给出明确提示，而不是让表格空着让人猜 */
const serviceDown = ref(false)

const loading = ref(false)
const list = ref<AiFaqVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const category = ref('')

const categoryOptions = computed(() => {
  const seen = new Set(BASE_CATEGORIES)
  list.value.forEach((f) => f.category && seen.add(f.category))
  return Array.from(seen)
})

const labelOf = (key: string) => CATEGORY_LABELS[key] ?? key

/** 工具调用条形宽度：以最大值为 100% 基准 */
function toolBarWidth(count: number): string {
  const max = Math.max(...Object.values(ops.value?.byTool ?? { a: 1 }))
  return `${Math.max(6, Math.round((count / max) * 100))}%`
}

async function fetchStats() {
  try {
    stats.value = await kbStats()
    serviceDown.value = false
  } catch {
    // 常见于本地只起了 Java、没起 ai-service：给一句人话，不在这里刷屏
    stats.value = null
    serviceDown.value = true
  }
  try {
    ops.value = await aiOpsSummary(7)
  } catch {
    ops.value = null
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listKbFaqs({
      category: category.value || undefined,
      pageNum: page.value,
      pageSize: pageSize.value,
    })
    list.value = res.items ?? []
    total.value = num(res.total)
    serviceDown.value = false
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  fetchStats()
  fetchList()
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

function handleCategoryChange() {
  page.value = 1
  fetchList()
}

// ── 新增/编辑 ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增知识')
const formLoading = ref(false)
const emptyForm = (): AiFaqRequest => ({
  question: '',
  answer: '',
  category: 'general',
  keywords: '',
  status: 1,
})
const form = ref<AiFaqRequest>(emptyForm())

function openAdd() {
  dialogTitle.value = '新增知识'
  form.value = emptyForm()
  dialogVisible.value = true
}

function openEdit(row: AiFaqVO) {
  dialogTitle.value = `编辑知识 #${row.id}`
  form.value = {
    id: Number(row.id),
    question: row.question,
    answer: row.answer,
    category: row.category,
    keywords: row.keywords ?? '',
    status: row.status,
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const question = form.value.question?.trim()
  const answer = form.value.answer?.trim()
  if (!question) {
    ElMessage.warning('请填写标准问法')
    return
  }
  if (!answer) {
    ElMessage.warning('请填写答案')
    return
  }
  formLoading.value = true
  try {
    if (form.value.id) {
      // 返回的是"同步是否成功"，不是"是否在索引里"：停用一条时除名也算同步成功。
      // false 才需要说出来——内容已存但向量库没跟上，AI 还会按旧答案回答。
      const syncOk = await updateKbFaq(Number(form.value.id), { ...form.value, question, answer })
      if (syncOk === false) {
        ElMessage.warning('已保存，但索引同步失败，请点击「重建 FAQ 索引」修复')
      } else {
        ElMessage.success('已保存并同步到向量索引')
      }
    } else {
      const created = await createKbFaq({ ...form.value, question, answer })
      // "已同步"这句只能在这一步真的同步成功时说：向量库挂掉时条目已落库但 AI 召不到，
      // 报成功会把问题藏起来（实测过 indexed=false 这条路）
      if (created?.indexed === false) {
        ElMessage.warning('已保存，但索引同步失败，请点击「重建 FAQ 索引」修复')
      } else {
        ElMessage.success('已新增并同步到向量索引')
      }
    }
    dialogVisible.value = false
    reload()
  } catch {
    // 拦截器已提示具体原因（含 AI 服务未启动），这里保持弹窗不关闭以便重试
  } finally {
    formLoading.value = false
  }
}

async function handleStatusChange(row: AiFaqVO, status: number) {
  const prev = row.status
  row.status = status
  try {
    const syncOk = await updateKbFaq(Number(row.id), { status })
    if (syncOk === false) {
      ElMessage.warning('状态已保存，但索引同步失败，请点击「重建 FAQ 索引」')
    } else {
      ElMessage.success(status === 1 ? '已启用并进入索引' : '已停用并从索引下架')
    }
    fetchList()
  } catch {
    row.status = prev
  }
}

async function handleDelete(row: AiFaqVO) {
  await ElMessageBox.confirm(
    `确认删除「${row.question}」？删除后会同时从向量索引下架，AI 立即不再引用它。`,
    '提示',
    { type: 'warning' },
  )
  try {
    await deleteKbFaq(Number(row.id))
    ElMessage.success('已删除')
    reload()
  } catch {
    // 拦截器已提示
  }
}

const rebuilding = ref<'faq' | 'product' | ''>('')

async function handleRebuildFaq() {
  await ElMessageBox.confirm(
    '全量重建会按 ai_faq 当前启用条目重算向量索引（几百条约几秒）。确定继续？',
    '提示',
    { type: 'warning' },
  )
  rebuilding.value = 'faq'
  try {
    const n = Number(await rebuildKbIndex())
    ElMessage.success(`FAQ 索引已重建，共 ${n} 条`)
    reload()
  } catch {
    // 拦截器已提示
  } finally {
    rebuilding.value = ''
  }
}

async function handleRebuildProduct() {
  await ElMessageBox.confirm(
    '全量重建商品召回索引：读全部在售商品重新向量化（约几秒）。商品变更后本会自动增量同步，这里只作兜底修复。确定继续？',
    '提示',
    { type: 'warning' },
  )
  rebuilding.value = 'product'
  try {
    const n = Number(await rebuildKbProductIndex())
    ElMessage.success(`商品召回索引已重建，共 ${n} 条`)
    reload()
  } catch {
    // 拦截器已提示
  } finally {
    rebuilding.value = ''
  }
}

const splitKeywords = (raw: string | null) =>
  (raw ?? '')
    .split(/[,，]/)
    .map((k) => k.trim())
    .filter(Boolean)

const formatTime = (t: string | null) => (t ? t.replace('T', ' ').slice(0, 19) : '—')

onMounted(reload)
</script>

<template>
  <div class="page">
    <PageHeader
      title="AI 知识库"
      description="维护智能客服的 RAG 知识来源：新增/修改即同步向量索引，停用与删除即刻下架"
    >
      <el-button :loading="rebuilding === 'faq'" @click="handleRebuildFaq">重建 FAQ 索引</el-button>
      <el-button :loading="rebuilding === 'product'" @click="handleRebuildProduct">重建商品索引</el-button>
    </PageHeader>

    <el-alert
      v-if="serviceDown"
      type="warning"
      show-icon
      :closable="false"
      class="kb-alert"
      title="AI 服务未连通"
      description="知识库与索引状态读不到，通常是 ai-service（8000 端口）没启动。启动后点右上角刷新或重新进入本页。"
    />
    <el-alert
      v-else-if="stats && !stats.faqSynced"
      type="warning"
      show-icon
      :closable="false"
      class="kb-alert"
      :title="`向量索引落后于知识条目（启用 ${num(stats.faqCount)} 条 / 已索引 ${num(stats.indexedCount)} 条）`"
      description="点右上角「重建 FAQ 索引」即可修复；期间智能客服对未入索引的条目会回答“知识库还未覆盖”。"
    />
    <el-alert
      v-else-if="stats && !stats.productIndexSynced"
      type="warning"
      show-icon
      :closable="false"
      class="kb-alert"
      :title="`商品召回索引未覆盖全部在售商品（在售 ${num(stats.onSaleProductCount)} / 已索引 ${num(stats.productIndexedCount)}）`"
      description="商品新增与上下架会自动增量同步；若长期不一致（例如索引服务当时没起），点「重建商品索引」兜底。"
    />

    <el-card shadow="never" class="kb-stats">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">索引状态</span>
          <el-tag v-if="stats" type="success" effect="plain" size="small">
            嵌入模型 {{ stats.embeddingModel || '—' }}
          </el-tag>
        </div>
      </template>
      <div class="kb-stats-row">
        <div class="kb-stat">
          <span class="kb-num">{{ stats ? num(stats.faqCount) : '—' }}</span>
          <span class="kb-lbl">启用知识条目</span>
        </div>
        <div class="kb-stat">
          <span class="kb-num">{{ stats ? num(stats.indexedCount) : '—' }}</span>
          <span class="kb-lbl">FAQ 向量索引条数</span>
        </div>
        <div class="kb-stat">
          <span class="kb-num">{{ stats ? num(stats.productIndexedCount) : '—' }}</span>
          <span class="kb-lbl">商品召回索引条数</span>
        </div>
        <div class="kb-stat">
          <span class="kb-num">{{ stats ? num(stats.onSaleProductCount) : '—' }}</span>
          <span class="kb-lbl">在售商品数</span>
        </div>
      </div>
      <p class="kb-hint">
        前两个数不等说明有知识改了但没进索引；后两个数不等说明有在售商品还没被 AI 学到。
      </p>
    </el-card>

    <!-- 近 7 日 AI 运营：对话量/拒答率/工具分布。数据源 chat_log（每轮一条），
         拒答率是两道闸门（知识库未覆盖 + 导购域外）命中的轮次占比。 -->
    <el-card v-if="ops && ops.totalTurns > 0" shadow="never" class="kb-ops">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">近 7 日 AI 运营</span>
          <el-tag type="info" effect="plain" size="small">对话 {{ ops.totalTurns }} 轮</el-tag>
        </div>
      </template>
      <div class="kb-ops-row">
        <div class="kb-ops-num">
          <span class="kb-num">{{ ops.totalTurns }}</span>
          <span class="kb-lbl">对话轮数</span>
        </div>
        <div class="kb-ops-num">
          <span class="kb-num">{{ (ops.refusalRate * 100).toFixed(1) }}%</span>
          <span class="kb-lbl">拒答率（{{ ops.refusedTurns }} 轮）</span>
        </div>
        <div class="kb-ops-tools">
          <div v-for="(count, tool) in ops.byTool" :key="tool" class="kb-ops-tool">
            <span class="kb-ops-tool-name">{{ tool }}</span>
            <div class="kb-ops-bar-wrap">
              <div class="kb-ops-bar" :style="{ width: toolBarWidth(count) }"></div>
            </div>
            <span class="kb-ops-tool-count">{{ count }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 近 7 日被 AI 引用 Top：知识闭环的运营侧证据——哪条知识真的被用过。
         为空说明近期没有知识问答命中（正常现象，不告警）。 -->
    <el-card v-if="stats?.recentHitTop?.length" shadow="never" class="kb-hits">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">近 7 日被 AI 引用 Top</span>
          <el-tag type="info" effect="plain" size="small">知识闭环 · 运营视图</el-tag>
        </div>
      </template>
      <div class="kb-hit-list">
        <div v-for="row in stats.recentHitTop" :key="row.faqId" class="kb-hit-row">
          <span class="kb-hit-q">{{ row.question }}</span>
          <el-tag size="small" effect="plain">{{ row.hits }} 次引用</el-tag>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="kb-list">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">知识条目</span>
          <div class="kb-tools">
            <el-select
              v-model="category"
              placeholder="全部分类"
              clearable
              size="default"
              class="kb-filter"
              @change="handleCategoryChange"
            >
              <el-option label="全部分类" value="" />
              <el-option v-for="c in categoryOptions" :key="c" :label="labelOf(c)" :value="c" />
            </el-select>
            <el-button type="primary" @click="openAdd">新增知识</el-button>
          </div>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="90" align="center" />
        <el-table-column prop="question" label="标准问法" min-width="220" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="260" show-overflow-tooltip />
        <el-table-column label="分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ labelOf(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关键词" min-width="170">
          <template #default="{ row }">
            <el-tag
              v-for="(k, i) in splitKeywords(row.keywords).slice(0, 3)"
              :key="i"
              size="small"
              type="info"
              effect="plain"
              class="kb-kw"
            >{{ k }}</el-tag>
            <span v-if="!splitKeywords(row.keywords).length" class="kb-none">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="启用"
              inactive-text="停用"
              @change="(val: unknown) => handleStatusChange(row as AiFaqVO, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" class="admin-action-btn" @click="openEdit(row as AiFaqVO)">编辑</el-button>
            <el-divider direction="vertical" class="admin-action-divider" />
            <el-button link type="danger" size="small" class="admin-action-btn" @click="handleDelete(row as AiFaqVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="admin-pagination">
        <span class="admin-total-text">共 {{ total }} 条知识</span>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="92px">
        <el-form-item label="标准问法" required>
          <el-input
            v-model="form.question"
            placeholder="用户可能怎么问，例如：运费怎么算"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input
            v-model="form.answer"
            type="textarea"
            :rows="4"
            placeholder="给出准确、可核对的答案。答案里不要写没有依据的承诺（如发货时限、赔付比例）"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" filterable allow-create default-first-option class="kb-full">
            <el-option v-for="c in categoryOptions" :key="c" :label="labelOf(c)" :value="c" />
          </el-select>
          <div class="form-tip">分类用于列表筛选，不进检索打分</div>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" placeholder="逗号分隔，如：运费,包邮,快递费" maxlength="255" />
          <div class="form-tip">双路召回里关键词命中每条 +0.5 分，是标准问法之外最重要的召回依据</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
          <div class="form-tip">停用即从向量索引下架，AI 当场不再引用这条知识</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
}

.kb-alert {
  margin-bottom: 16px;
}

.kb-stats {
  margin-bottom: 16px;
}

.kb-stats-row {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
}

.kb-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kb-num {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
}

.kb-lbl {
  font-size: 13px;
  color: #8a9a91;
}

.kb-hint {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #8a9a91;
}

.kb-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}

.kb-filter {
  width: 160px;
}

.kb-kw {
  margin: 2px 4px 2px 0;
}

.kb-none {
  color: #b6c2ba;
}

.kb-full {
  width: 100%;
}

.form-tip {
  font-size: 12px;
  color: #8a9a91;
  line-height: 1.5;
  margin-top: 4px;
  width: 100%;
}
</style>

.kb-hit-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kb-hit-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kb-hit-q {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.kb-ops-row {
  display: flex;
  gap: 28px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.kb-ops-num {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 110px;
}

.kb-ops-tools {
  flex: 1;
  min-width: 240px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kb-ops-tool {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.kb-ops-tool-name {
  width: 120px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-align: right;
}

.kb-ops-bar-wrap {
  flex: 1;
  height: 10px;
  background: var(--el-fill-color-light);
  border-radius: 5px;
  overflow: hidden;
}

.kb-ops-bar {
  height: 100%;
  background: var(--el-color-primary);
  border-radius: 5px;
}

.kb-ops-tool-count {
  width: 36px;
  color: var(--el-text-color-primary);
}
