<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Refresh,
} from '@element-plus/icons-vue'
import { getAdminStatistics, getStatisticsTrend } from '@/api/admin'
import type { AdminStatisticsVO, StatisticsTrendVO } from '@/types'
import BaseChart from '@/components/BaseChart.vue'
import PageHeader from '@/components/admin/PageHeader.vue'
import KpiPanel from '@/components/admin/KpiPanel.vue'
import {
  buildUserPieOption,
  buildActivePieOption,
  buildOrderPieOption,
  buildProductBarOption,
  buildUserTrendOption,
  buildSalesTrendOption,
  emptyTrendOption,
} from './dashboard-options'

const stats = ref<AdminStatisticsVO | null>(null)
const trend = ref<StatisticsTrendVO | null>(null)
const trendDays = ref(7)
const loading = ref(false)

/** 加载全部数据（概览 + 趋势），供初次进入与手动刷新使用 */
async function loadAll() {
  loading.value = true
  try {
    const [s, t] = await Promise.all([
      getAdminStatistics(),
      getStatisticsTrend(trendDays.value),
    ])
    stats.value = s
    trend.value = t
  } finally {
    loading.value = false
  }
}

/** 仅切换天数时重拉趋势，避免整页闪烁 */
async function loadTrend() {
  trend.value = await getStatisticsTrend(trendDays.value)
}

onMounted(loadAll)

function fmtSales(v: number | undefined): string {
  return `¥${Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

// ── 主卡制指标带（v4）：销售总额主卡 + 4 项次卡 ──────────────────────────────

const mainKpi = computed(() => ({
  label: '销售总额',
  value: fmtSales(stats.value?.totalSales),
  pill: '实时',
  sub: '近 7 天销售额走势',
  spark: trend.value?.sales ?? [],
}))

const bizKpis = computed(() => [
  { label: '今日新增用户', value: stats.value?.todayNewUsers ?? 0, icon: 'users' as const },
  { label: '今日活跃用户', value: stats.value?.activeTodayUsers ?? 0, icon: 'active' as const },
  { label: '订单总数', value: stats.value?.orderTotal ?? 0, icon: 'order' as const },
  { label: '在售商品', value: stats.value?.onSaleProducts ?? 0, icon: 'product' as const },
])

// ─── 图表 option（由 dashboard-options.ts 统一构建） ──────────────────────────

const userPieOption = computed(() => buildUserPieOption(stats.value))
const activePieOption = computed(() => buildActivePieOption(stats.value))
const orderPieOption = computed(() => buildOrderPieOption(stats.value))
const productBarOption = computed(() => buildProductBarOption(stats.value))
const userTrendOption = computed(() =>
  trend.value ? buildUserTrendOption(trend.value) : emptyTrendOption(),
)
const salesTrendOption = computed(() =>
  trend.value ? buildSalesTrendOption(trend.value) : emptyTrendOption(),
)
</script>

<template>
  <div class="page" v-loading="loading">
    <PageHeader title="数据概览" description="平台核心运营指标与业务趋势">
      <el-radio-group v-model="trendDays" @change="loadTrend">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
    </PageHeader>

    <!-- 指标带：主卡 + 次卡（v4 主卡制） -->
    <KpiPanel
      :main="mainKpi"
      :items="bizKpis"
    />

    <!-- 趋势图区 2 图（lg 2列 / xs 1列） -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">销售与订单趋势</div>
              <div class="chart-sub">近 {{ trendDays }} 天销售额与订单量</div>
            </div>
          </div>
          <div class="chart-box">
            <BaseChart :option="salesTrendOption" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">新增用户趋势</div>
              <div class="chart-sub">近 {{ trendDays }} 天每日新增用户</div>
            </div>
          </div>
          <div class="chart-box">
            <BaseChart :option="userTrendOption" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 构成图区 2x2（两行独立排列，行间留白） -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">用户构成</div>
              <div class="chart-sub">正常 / 禁用用户占比</div>
            </div>
          </div>
          <div class="chart-box">
            <BaseChart :option="userPieOption" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">今日活跃占比</div>
              <div class="chart-sub">今日活跃 / 未活跃用户</div>
            </div>
          </div>
          <div class="chart-box">
            <BaseChart :option="activePieOption" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">订单状态构成</div>
              <div class="chart-sub">各状态订单占比</div>
            </div>
          </div>
          <div class="chart-box">
            <BaseChart :option="orderPieOption" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="chart-card">
          <div class="chart-head">
            <div>
              <div class="chart-title">商品在售情况</div>
              <div class="chart-sub">在售 / 下架商品数量</div>
            </div>
          </div>
          <div class="chart-box">
            <BaseChart :option="productBarOption" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
}

/* 图表区块间距（行与行之间留白） */
.chart-row {
  margin-bottom: 16px;
}

/* 图表卡片 */
.chart-card {
  height: 100%;
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.chart-title {
  font-size: 15px;
  font-weight: 700;
  color: #10231A;
  line-height: 1.4;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* 标题前品牌小竖条 */
.chart-title::before {
  content: '';
  width: 4px;
  height: 15px;
  border-radius: 2px;
  background: linear-gradient(180deg, #34C77B, #15803D);
  flex-shrink: 0;
}

.chart-sub {
  font-size: 12px;
  color: #8A9A91;
  margin-top: 2px;
}

.chart-box {
  height: 260px;
}
</style>
