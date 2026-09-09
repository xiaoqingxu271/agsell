<script setup lang="ts">
/**
 * KPI 指标面板（企业级 v3 规范 §6.9 修订版）
 * 一行指标由一个大面板包裹：标题行 + 内部指标块（竖分隔线），参考主流运营大盘排版
 * 用法：
 *   <KpiPanel title="用户数据" :items="[
 *     { label: '用户总数', value: 1286 },
 *     { label: '销售总额', value: '¥128,673.50', accent: true },
 *   ]" />
 */
export interface KpiItem {
  label: string
  value: string | number
  /** 金色数值（金额类，如销售总额） */
  accent?: boolean
}

const props = defineProps<{
  title: string
  /** 可选：标题右侧辅助说明（如日期/口径） */
  caption?: string
  items: KpiItem[]
}>()

/** ≥6 项时启用紧凑模式（缩小内边距与数值字号，避免金额截断） */
const isDense = () => props.items.length >= 6
</script>

<template>
  <div
    class="kpi-panel"
    :class="{ 'is-dense': isDense() }"
    :style="{ '--kpi-cols': items.length }"
  >
    <div class="kpi-head">
      <h3 class="kpi-title">{{ title }}</h3>
      <span v-if="caption" class="kpi-caption">{{ caption }}</span>
    </div>
    <div class="kpi-body">
      <div v-for="it in items" :key="it.label" class="kpi-item">
        <div class="kpi-label">{{ it.label }}</div>
        <div class="kpi-value" :class="{ 'is-accent': it.accent }">{{ it.value }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kpi-panel {
  background: #FFFFFF;
  border: 1px solid #E3E7E5;
  border-radius: 12px;
  padding: 18px 20px 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.05);
  margin-bottom: 24px;
  transition: box-shadow 0.2s ease-out, border-color 0.2s ease-out;
}
.kpi-panel:hover {
  box-shadow: 0 12px 32px rgba(16, 24, 40, 0.12);
  border-color: #D8DEDB;
}

.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.kpi-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  color: #0E3B25;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* 与列表页卡片标题一致的品牌小竖条 */
.kpi-title::before {
  content: '';
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(180deg, #22C55E, #15803D);
  flex-shrink: 0;
}

.kpi-caption {
  font-size: 12px;
  color: #9CA3AF;
  white-space: nowrap;
}

/* 指标块：≥1200px 一行铺满 + 竖分隔线；以下自适应换行 */
.kpi-body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  row-gap: 18px;
}

.kpi-item {
  padding: 10px 24px;
  min-width: 0;
}

.kpi-label {
  font-size: 13px;
  color: #6B7280;
  line-height: 1.4;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  color: #1F2937;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-value.is-accent {
  color: #A16207;
}

@media (min-width: 1200px) {
  .kpi-body {
    grid-template-columns: repeat(var(--kpi-cols, 6), minmax(0, 1fr));
  }
  .kpi-item + .kpi-item {
    border-left: 1px solid #EEF1EF;
  }
}

/* 紧凑模式（6 项及以上）：缩小内边距与数值字号，避免长金额截断 */
.kpi-panel.is-dense .kpi-item {
  padding: 8px 16px;
}
.kpi-panel.is-dense .kpi-value {
  font-size: 20px;
}
</style>
