import type { EChartsOption } from 'echarts'
import type { AdminStatisticsVO, StatisticsTrendVO } from '@/types'

// ─── 公共配置 ────────────────────────────────────────────────────────────────

const PIE_COLORS = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#C0C4CC']
const AXIS_LABEL_COLOR = '#6B7280'
const AXIS_LINE_COLOR = '#E5E7EB'
const SPLIT_LINE_COLOR = '#F3F4F6'
const EMPTY_COLOR = '#E5E7EB'
const PRIMARY = '#409EFF'
const SUCCESS = '#67C23A'
const WARNING = '#E6A23C'

/** 过滤掉 value<=0 的项；若全为 0 则回退为灰色"暂无数据"，避免空白图 */
function buildPieData(items: Array<{ name: string; value: number }>) {
  const data = items
    .filter((it) => it.value > 0)
    .map((it, i) => ({
      name: it.name,
      value: it.value,
      itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] ?? PIE_COLORS[0] },
    }))
  if (data.length > 0) return data
  return [{ name: '暂无数据', value: 1, itemStyle: { color: EMPTY_COLOR } }]
}

const pieCommon = {
  tooltip: { trigger: 'item' as const, formatter: '{b}: {c} ({d}%)' },
  legend: {
    bottom: 0,
    icon: 'circle',
    itemWidth: 8,
    itemHeight: 8,
    itemGap: 16,
    textStyle: { color: AXIS_LABEL_COLOR, fontSize: 12 },
  },
}

const pieSeriesBase = {
  type: 'pie' as const,
  radius: ['46%', '70%'],
  center: ['50%', '42%'],
  itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
  label: { show: false },
  emphasis: {
    label: { show: true, fontSize: 13, fontWeight: 600, formatter: '{b}\n{c} ({d}%)' },
  },
}

const emptyAxis = {
  type: 'category' as const,
  data: [] as string[],
}

// ─── 构成类图表 ───────────────────────────────────────────────────────────────

/** 用户构成（正常 / 禁用） */
export function buildUserPieOption(stats: AdminStatisticsVO | null): EChartsOption {
  const total = stats?.userTotal ?? 0
  const disabled = stats?.disabledUsers ?? 0
  return {
    ...pieCommon,
    series: [
      {
        ...pieSeriesBase,
        data: buildPieData([
          { name: '正常用户', value: Math.max(total - disabled, 0) },
          { name: '禁用用户', value: disabled },
        ]),
      },
    ],
  }
}

/** 今日活跃占比（活跃 / 未活跃） */
export function buildActivePieOption(stats: AdminStatisticsVO | null): EChartsOption {
  const total = stats?.userTotal ?? 0
  const active = stats?.activeTodayUsers ?? 0
  return {
    ...pieCommon,
    series: [
      {
        ...pieSeriesBase,
        data: buildPieData([
          { name: '今日活跃', value: active },
          { name: '今日未活跃', value: Math.max(total - active, 0) },
        ]),
      },
    ],
  }
}

/**
 * 订单状态构成（待发货 / 其他已支付 / 待付款·已取消）
 * 说明：overview 仅提供 orderTotal / paidOrders / pendingShipOrders，
 * "待付款/已取消" = orderTotal - paidOrders（近似合并值，无法拆分）
 */
export function buildOrderPieOption(stats: AdminStatisticsVO | null): EChartsOption {
  const total = stats?.orderTotal ?? 0
  const paid = stats?.paidOrders ?? 0
  const pending = stats?.pendingShipOrders ?? 0
  return {
    ...pieCommon,
    series: [
      {
        ...pieSeriesBase,
        data: buildPieData([
          { name: '待发货', value: pending },
          { name: '其他已支付', value: Math.max(paid - pending, 0) },
          { name: '待付款/已取消', value: Math.max(total - paid, 0) },
        ]),
      },
    ],
  }
}

/** 商品在售情况（在售 / 下架） */
export function buildProductBarOption(stats: AdminStatisticsVO | null): EChartsOption {
  const total = stats?.productTotal ?? 0
  const onSale = stats?.onSaleProducts ?? 0
  const offSale = Math.max(total - onSale, 0)
  return {
    tooltip: { trigger: 'axis' as const, axisPointer: { type: 'shadow' as const } },
    grid: { left: 8, right: 8, top: 24, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: ['在售商品', '已下架'],
      axisTick: { show: false },
      axisLine: { lineStyle: { color: AXIS_LINE_COLOR } },
      axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: SPLIT_LINE_COLOR } },
      axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        barWidth: 48,
        data: [
          { value: onSale, itemStyle: { color: PRIMARY, borderRadius: [4, 4, 0, 0] } },
          { value: offSale, itemStyle: { color: EMPTY_COLOR, borderRadius: [4, 4, 0, 0] } },
        ],
      },
    ],
  }
}

// ─── 趋势类图表 ───────────────────────────────────────────────────────────────

/** 近 N 天新增用户趋势（柱状） */
export function buildUserTrendOption(trend: StatisticsTrendVO | null): EChartsOption {
  const dates = trend?.dates ?? []
  return {
    tooltip: { trigger: 'axis' as const, axisPointer: { type: 'shadow' as const } },
    grid: { left: 8, right: 8, top: 24, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: AXIS_LINE_COLOR } },
      axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 12, formatter: (v: string) => v.slice(5) },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: SPLIT_LINE_COLOR } },
      axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 32,
        itemStyle: { color: PRIMARY, borderRadius: [4, 4, 0, 0] },
        data: trend?.newUsers ?? [],
      },
    ],
  }
}

/** 近 N 天销售（柱）与订单数（线）双轴趋势 */
export function buildSalesTrendOption(trend: StatisticsTrendVO | null): EChartsOption {
  const dates = trend?.dates ?? []
  const fmtMoney = (v: number) =>
    v >= 10000 ? `${(v / 10000).toFixed(1)}万` : String(v)
  return {
    tooltip: {
      trigger: 'axis' as const,
      axisPointer: { type: 'shadow' as const },
      formatter: (params: unknown) => {
        const list = params as Array<{ seriesName: string; value: number; dataIndex?: number }>
        const rows = list
          .map((p) => `${p.seriesName}：${p.seriesName.includes('销售') ? `¥${Number(p.value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}` : p.value}`)
          .join('<br/>')
        return `${dates[list[0]?.dataIndex ?? 0] ?? ''}<br/>${rows}`
      },
    },
    grid: { left: 8, right: 8, top: 24, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: AXIS_LINE_COLOR } },
      axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 12, formatter: (v: string) => v.slice(5) },
    },
    yAxis: [
      {
        type: 'value',
        splitLine: { lineStyle: { color: SPLIT_LINE_COLOR } },
        axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 12, formatter: (v: number) => fmtMoney(v) },
      },
      {
        type: 'value',
        minInterval: 1,
        splitLine: { show: false },
        axisLabel: { color: AXIS_LABEL_COLOR, fontSize: 12 },
      },
    ],
    series: [
      {
        name: '销售额',
        type: 'bar',
        barMaxWidth: 32,
        itemStyle: { color: WARNING, borderRadius: [4, 4, 0, 0] },
        data: trend?.sales ?? [],
      },
      {
        name: '订单数',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: PRIMARY, width: 2 },
        itemStyle: { color: PRIMARY },
        data: trend?.orderCounts ?? [],
      },
    ],
  }
}

/** 空趋势（数据未加载时） */
export function emptyTrendOption(): EChartsOption {
  return {
    xAxis: emptyAxis,
    yAxis: { type: 'value' },
    series: [],
  }
}
