<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getAdminStatistics, getStatisticsTrend } from '@/api/admin'
import type { AdminStatisticsVO, StatisticsTrendVO } from '@/types'
import BaseChart from '@/components/BaseChart.vue'
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
    <div class="page-header">
      <h2 class="page-title">数据概览</h2>
      <div class="page-actions">
        <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- 第一行：用户相关 4 卡（lg 4列 / md 2列 / sm 1列） -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="用户总数" :value="stats?.userTotal ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="今日新增" :value="stats?.todayNewUsers ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="今日活跃" :value="stats?.activeTodayUsers ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="禁用用户" :value="stats?.disabledUsers ?? 0" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：商品/订单/销售 6 卡（lg 3列 / md 2列 / sm 1列） -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="商品总数" :value="stats?.productTotal ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="在售商品" :value="stats?.onSaleProducts ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="订单总数" :value="stats?.orderTotal ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="待发货订单" :value="stats?.pendingShipOrders ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="已支付订单" :value="stats?.paidOrders ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <el-card shadow="never" class="stat-card">
          <el-statistic title="销售总额" :value="fmtSales(stats?.totalSales)" class="stat-sales" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 第三块：趋势图区 2 图（lg 2列 / sm 1列） -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="stat-card chart-card">
          <div class="chart-title">新增用户趋势</div>
          <div class="chart-box">
            <BaseChart :option="userTrendOption" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="stat-card chart-card">
          <div class="chart-title">销售与订单趋势</div>
          <div class="chart-box">
            <BaseChart :option="salesTrendOption" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第四块：构成图区 2x2 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="stat-card chart-card">
          <div class="chart-title">用户构成</div>
          <div class="chart-box">
            <BaseChart :option="userPieOption" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="stat-card chart-card">
          <div class="chart-title">今日活跃占比</div>
          <div class="chart-box">
            <BaseChart :option="activePieOption" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="stat-card chart-card">
          <div class="chart-title">订单状态构成</div>
          <div class="chart-box">
            <BaseChart :option="orderPieOption" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="stat-card chart-card">
          <div class="chart-title">商品在售情况</div>
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

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.page-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  border: 1px solid #BBF7D0;
  border-radius: 12px;
}

/* 统计数值：20px 600 tabular-nums */
.stat-card :deep(.el-statistic__content) {
  font-size: 20px;
  font-weight: 600;
  color: #1F2937;
  font-variant-numeric: tabular-nums;
}

.stat-card :deep(.el-statistic__head) {
  font-size: 13px;
  color: #6B7280;
  margin-bottom: 8px;
}

/* 销售总额用丰收金 */
.stat-sales :deep(.el-statistic__content) {
  color: #A16207;
}

/* 图表卡片 */
.chart-card .chart-title {
  font-size: 13px;
  color: #6B7280;
  margin-bottom: 12px;
}

.chart-box {
  height: 240px;
}
</style>
