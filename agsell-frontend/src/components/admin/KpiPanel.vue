<script setup lang="ts">
/**
 * KPI 指标面板（v4.0 主卡制）
 * 用法一（主卡制，数据概览）：
 *   <KpiPanel
 *     main="{ label: '今日销售总额', value: '¥128,430.50', pill: '实时', sub: '较昨日 ↑ 8.2% · 订单 326 笔' }"
 *     :items="[
 *       { label: '今日新增用户', value: '126', icon: 'users', trend: '↑ 12.5% 较昨日', trendUp: true },
 *       ...
 *     ]" />
 * 用法二（大面板分块，兼容）：
 *   <KpiPanel title="用户数据" :items="[{ label: '用户总数', value: 1286 }]" />
 */
export interface KpiMain {
  label: string
  value: string | number
  /** 标签右侧小胶囊（如「实时」） */
  pill?: string
  /** 数值下方趋势说明 */
  sub?: string
  /** 主卡迷你趋势数据点（6~8 个数值） */
  spark?: number[]
}

export interface KpiItem {
  label: string
  value: string | number
  /** 金色数值（金额类，如销售总额） */
  accent?: boolean
  /** 图标：users / active / order / product / sales */
  icon?: 'users' | 'active' | 'order' | 'product' | 'sales'
  /** 趋势说明（如「↑ 12.5% 较昨日」） */
  trend?: string
  /** 趋势是否上行（上行绿色、下行红色） */
  trendUp?: boolean
}

const props = defineProps<{
  /** 可选：大面板标题（不传 main 时显示） */
  title?: string
  /** 可选：标题右侧辅助说明 */
  caption?: string
  /** 可选：渐变主卡（首卡突出） */
  main?: KpiMain
  items: KpiItem[]
}>()

/** ≥6 项时启用紧凑模式（缩小内边距与数值字号，避免金额截断） */
const isDense = () => props.items.length >= 6

/** spark 折线路径（0~1 归一化到 96×34 视框） */
function sparkPath(points: number[]): string {
  if (!points.length) return ''
  const min = Math.min(...points)
  const max = Math.max(...points)
  const range = max - min || 1
  const stepX = 94 / (points.length - 1 || 1)
  return points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${(2 + i * stepX).toFixed(1)} ${(30 - ((p - min) / range) * 24).toFixed(1)}`)
    .join(' ')
}
</script>

<template>
  <div class="kpi-panel" :class="{ 'has-main': !!main, 'is-dense': isDense() }">
    <!-- 渐变主卡 -->
    <div v-if="main" class="kpi-main">
      <div class="k-label">
        {{ main.label }}
        <span v-if="main.pill" class="pill">{{ main.pill }}</span>
      </div>
      <div class="k-value">{{ main.value }}</div>
      <div v-if="main.sub" class="k-sub">{{ main.sub }}</div>
      <svg v-if="main.spark?.length" class="k-spark" width="96" height="34" viewBox="0 0 96 34" fill="none" aria-hidden="true">
        <defs>
          <linearGradient id="kpi-spark-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="rgba(255,255,255,0.28)" />
            <stop offset="1" stop-color="rgba(255,255,255,0)" />
          </linearGradient>
        </defs>
        <path
          :d="sparkPath(main.spark)"
          stroke="rgba(255,255,255,0.9)"
          stroke-width="2"
          stroke-linecap="round"
          fill="none"
        />
        <path
          :d="`${sparkPath(main.spark)} V34 H2 Z`"
          fill="url(#kpi-spark-fill)"
        />
      </svg>
    </div>

    <!-- 大面板标题（非主卡模式） -->
    <div v-else class="kpi-head">
      <h3 class="kpi-title">{{ title }}</h3>
      <span v-if="caption" class="kpi-caption">{{ caption }}</span>
    </div>

    <!-- 指标区 -->
    <div class="kpi-body" :class="{ 'with-main': !!main }">
      <div
        v-for="(it, idx) in items"
        :key="it.label"
        class="kpi-item"
        :class="{ 'is-card': !!main }"
      >
        <template v-if="main">
          <div class="k-top">
            <span class="k-ic" :class="it.icon ?? 'green'">
              <!-- 图标 -->
              <svg v-if="it.icon === 'users'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
              <svg v-else-if="it.icon === 'active'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 12h-4l-3 9L9 3l-3 9H2" /></svg>
              <svg v-else-if="it.icon === 'order'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" /><path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6" /></svg>
              <svg v-else-if="it.icon === 'product'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" /><path d="M3 6h18" /><path d="M16 10a4 4 0 0 1-8 0" /></svg>
              <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10" /><path d="M12 6v6l4 2" /></svg>
            </span>
            <span class="k-label">{{ it.label }}</span>
          </div>
          <div class="k-value" :class="{ 'is-accent': it.accent }">
            {{ it.value }}
            <small v-if="it.trend" class="k-trend" :class="{ up: it.trendUp, down: it.trendUp === false }">{{ it.trend }}</small>
          </div>
        </template>
        <template v-else>
          <div class="kpi-label">{{ it.label }}</div>
          <div class="kpi-value" :class="{ 'is-accent': it.accent }">{{ it.value }}</div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kpi-panel {
  background: #FFFFFF;
  border: 1px solid #E7ECE9;
  border-radius: 16px;
  padding: 16px 18px 18px;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.04);
  margin-bottom: 16px;
  transition: box-shadow 0.2s ease-out, border-color 0.2s ease-out;
}

/* ── 主卡制：横向布局 ── */
.kpi-panel.has-main {
  display: flex;
  gap: 16px;
  padding: 0;
  overflow: hidden;
}

.kpi-main {
  flex: 0 0 300px;
  border-radius: 16px 0 0 16px;
  padding: 20px 24px;
  color: #fff;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #0E3B25 0%, #15803D 62%, #22A55A 100%);
  box-shadow: 0 8px 24px rgba(21, 128, 61, 0.22);
}

.kpi-main::after {
  content: '';
  position: absolute;
  right: -70px;
  top: -70px;
  width: 210px;
  height: 210px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: 0 0 0 26px rgba(255, 255, 255, 0.05), 0 0 0 52px rgba(255, 255, 255, 0.03);
}

.kpi-main .k-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  z-index: 1;
}

.kpi-main .pill {
  font-size: 10.5px;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  padding: 2px 8px;
}

.kpi-main .k-value {
  font-size: 31px;
  font-weight: 700;
  margin: 9px 0 5px;
  letter-spacing: -0.01em;
  font-variant-numeric: tabular-nums;
  position: relative;
  z-index: 1;
}

.kpi-main .k-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  position: relative;
  z-index: 1;
}

.kpi-main .k-spark {
  position: absolute;
  right: 20px;
  bottom: 16px;
  opacity: 0.92;
  z-index: 1;
}

/* ── 指标区（主卡制：独立次卡）── */
.kpi-body.with-main {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
  padding: 16px 18px 16px 0;
}

.kpi-item.is-card {
  background: #fff;
  border: 1px solid #E7ECE9;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.04);
  transition: box-shadow 0.2s ease-out, transform 0.2s ease-out, border-color 0.2s ease-out;
  min-width: 0;
}

.kpi-item.is-card:hover {
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.08);
  transform: translateY(-2px);
  border-color: #DDE4DF;
}

.kpi-item.is-card .k-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.k-ic {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.k-ic.green { background: #EAF6EF; color: #15803D; }
.k-ic.gold { background: #FFF4E0; color: #D97706; }
.k-ic.blue { background: #E8F1FB; color: #2563EB; }
.k-ic.violet { background: #F1EDFC; color: #7C3AED; }

.kpi-item.is-card .k-label {
  font-size: 12.5px;
  color: #5B6B63;
}

.kpi-item.is-card .k-value {
  font-size: 23px;
  font-weight: 700;
  color: #10231A;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-item.is-card .k-value.is-accent {
  color: #D97706;
}

.k-trend {
  display: block;
  font-size: 11.5px;
  font-weight: 600;
  margin-top: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.k-trend.up { color: #15803D; }
.k-trend.down { color: #DC2626; }

/* ── 大面板分块模式（兼容）── */
.kpi-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.kpi-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  color: #10231A;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.kpi-title::before {
  content: '';
  width: 4px;
  height: 15px;
  border-radius: 2px;
  background: linear-gradient(180deg, #34C77B, #15803D);
  flex-shrink: 0;
}

.kpi-caption {
  font-size: 12px;
  color: #8A9A91;
  white-space: nowrap;
}

.kpi-body {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  row-gap: 14px;
}

.kpi-item {
  padding: 8px 20px;
  min-width: 0;
}

.kpi-label {
  font-size: 13px;
  color: #5B6B63;
  line-height: 1.4;
  margin-bottom: 5px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-value {
  font-size: 23px;
  font-weight: 700;
  line-height: 1.3;
  color: #10231A;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kpi-value.is-accent {
  color: #D97706;
}

@media (min-width: 1200px) {
  .kpi-body:not(.with-main) {
    grid-template-columns: repeat(var(--kpi-cols, 6), minmax(0, 1fr));
  }
  .kpi-item:not(.is-card) + .kpi-item:not(.is-card) {
    border-left: 1px solid #F0F3F1;
  }
}

/* 紧凑模式（6 项及以上）：缩小数值字号 */
.kpi-panel.is-dense .kpi-item.is-card .k-value {
  font-size: 19px;
}
</style>
