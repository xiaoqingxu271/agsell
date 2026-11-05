<script setup lang="ts">
/**
 * AI 运营配置：检索条数/向量闸门/重排开关/编排上限等运行参数，改完立即生效（无需重启）。
 *
 * 参数存 sys_config 的 ai_* 白名单键（Python 运行时 60s TTL 读取 + PUT 主动失效），
 * 读失败自动回落代码默认值——调参通道故障绝不能影响对话主链路。
 * 每项都展示默认值与"已自定义"标记，避免运营调飞后不知道怎么回去。
 */
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAiConfig, updateAiConfig } from '@/api/admin'
import type { AiConfigItemVO } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const saving = ref(false)
const items = ref<AiConfigItemVO[]>([])

/** 草稿：key → 编辑中的字符串（bool 用 'true'/'false'，数值用输入框的字符串） */
const draft = ref<Record<string, string>>({})

function toDraftValue(item: AiConfigItemVO): string {
  if (item.type === 'bool') return String(item.value === true)
  return String(item.value ?? '')
}

function boolOn(item: AiConfigItemVO): boolean {
  return (draft.value[item.key] ?? toDraftValue(item)) === 'true'
}

function onBoolChange(item: AiConfigItemVO, val: boolean) {
  draft.value[item.key] = String(val)
}

function onNumberChange(item: AiConfigItemVO, val: number) {
  draft.value[item.key] = String(val ?? '')
}

function isDirty(item: AiConfigItemVO): boolean {
  return (draft.value[item.key] ?? toDraftValue(item)) !== toDraftValue(item)
}

function dirtyCount(): number {
  return items.value.filter(isDirty).length
}

async function fetchConfig() {
  loading.value = true
  try {
    items.value = (await getAiConfig()) ?? []
    const d: Record<string, string> = {}
    for (const it of items.value) d[it.key] = toDraftValue(it)
    draft.value = d
  } catch {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  const values: Record<string, string> = {}
  for (const it of items.value) {
    if (isDirty(it)) values[it.key] = draft.value[it.key] ?? toDraftValue(it)
  }
  if (!Object.keys(values).length) {
    ElMessage.info('没有修改')
    return
  }
  await ElMessageBox.confirm(
    `保存后 ${Object.keys(values).length} 项参数立即生效（正在进行的对话不受影响）。确定继续？`,
    '更新 AI 运行参数',
    { type: 'warning' },
  )
  saving.value = true
  try {
    items.value = (await updateAiConfig(values)) ?? items.value
    const d: Record<string, string> = {}
    for (const it of items.value) d[it.key] = toDraftValue(it)
    draft.value = d
    ElMessage.success('AI 运行参数已更新并立即生效')
  } catch {
    // interceptor handles error
  } finally {
    saving.value = false
  }
}

async function handleResetOne(item: AiConfigItemVO) {
  draft.value[item.key] = item.type === 'bool' ? String(item.default === true) : String(item.default ?? '')
  ElMessage.success(`已按默认值回填（${item.description}），点「保存」生效`)
}

onMounted(fetchConfig)
</script>

<template>
  <div class="page">
    <PageHeader
      title="AI 运营配置"
      description="检索与编排的运行参数，保存后立即生效（无需重启 AI 服务）；参数异常时自动回落代码默认值"
    />

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">运行参数</span>
          <div class="cfg-actions">
            <span v-if="dirtyCount() > 0" class="cfg-dirty">有 {{ dirtyCount() }} 项未保存</span>
            <el-button type="primary" :loading="saving" :disabled="dirtyCount() === 0" @click="handleSave">
              保存并生效
            </el-button>
          </div>
        </div>
      </template>

      <el-table class="admin-table" :data="items" style="width: 100%">
        <el-table-column label="参数" min-width="260">
          <template #default="{ row }">
            <div class="cfg-name">
              <span class="cfg-key">{{ row.key }}</span>
              <el-tag v-if="row.overridden" type="warning" size="small" effect="plain">已自定义</el-tag>
            </div>
            <div class="cfg-desc">{{ row.description }}</div>
          </template>
        </el-table-column>
        <el-table-column label="当前值" min-width="200">
          <template #default="{ row }">
            <el-switch
              v-if="row.type === 'bool'"
              :model-value="boolOn(row as AiConfigItemVO)"
              @change="(v: string | number | boolean) => onBoolChange(row as AiConfigItemVO, v === true)"
            />
            <el-input-number
              v-else
              :model-value="Number(draft[(row as AiConfigItemVO).key] ?? '')"
              :min="(row as AiConfigItemVO).min ?? undefined"
              :max="(row as AiConfigItemVO).max ?? undefined"
              :step="row.type === 'float' ? 0.01 : 1"
              :precision="row.type === 'float' ? 2 : 0"
              controls-position="right"
              style="width: 160px"
              @update:model-value="(v: number | undefined) => onNumberChange(row as AiConfigItemVO, v ?? 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="默认值" min-width="110" align="center">
          <template #default="{ row }">{{ row.type === 'bool' ? (row.default ? '开' : '关') : row.default }}</template>
        </el-table-column>
        <el-table-column label="范围" min-width="110" align="center">
          <template #default="{ row }">{{ row.min ?? '—' }} ~ {{ row.max ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleResetOne(row as AiConfigItemVO)">恢复默认</el-button>
          </template>
        </el-table-column>
      </el-table>

      <p class="cfg-hint">
        说明：检索条数与闸门阈值决定 RAG 的召回口径（阈值越高拒答越保守）；重排开关影响回答质量与延迟的取舍；
        编排上限影响多诉求场景的覆盖率与响应时长的平衡。调参后可在「AI 对话审计」页观察拒答率变化。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.cfg-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cfg-dirty {
  font-size: 12px;
  color: var(--el-color-warning);
}

.cfg-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cfg-key {
  font-family: monospace;
  font-size: 13px;
}

.cfg-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.cfg-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}
</style>
