import type { EChartsOption } from 'echarts'
import type { AdminStatisticsVO, StatisticsTrendVO } from '@/types'

// ─── 公共配置 ────────────────────────────────────────────────────────────────
// 设计系统 v3.0 §8 图表规范：系列色板 #15803D, #10B981, #D97706, #2563EB, #64748B, #DC2626

const AXIS_LABEL_COLOR = '#6B7280'
const AXIS_LINE_COLOR = '#E3E7E5'
const SPLIT_LINE_COLOR = '#EEF1EF'
const EMPTY_COLOR = '#E5E7EB'
const PRIMARY = '#15803D'
const SUCCESS = '#10B981'

/** 品牌绿柱状渐变（v3：深翡翠纵向渐变 + 圆角） */
const brandBarStyle = {
  color: {
    type: 'linear' as const,
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: '#22C55E' },
      { offset: 1, color: '#15803D' },
    ],
  },
  borderRadius: [6, 6, 0, 0] as [number, number, number, number],
}

/** 丰收金柱状渐变（销售额） */
const goldBarStyle = {
  color: {
    type: 'linear' as const,
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: '#D97706' },
      { offset: 1, color: '#A16207' },
    ],
  },
  borderRadius: [6, 6, 0, 0] as [number, number, number, number],
}

/** 统一 tooltip 深色质感 */
const tooltipStyle = {
  backgroundColor: 'rgba(10, 42, 27, 0.92)',
  borderColor: 'transparent',
  textStyle: { color: '#FFFFFF', fontSize: 12 },
  padding: [8, 12] as [number, number],
  extraCssText: 'border-radius: 8px; box-shadow: 0 8px 24px rgba(16,24,40,.18);',
}

const emptyAxis = {
  type: 'category' as const,
  data: [] as string[],
}

/**
 * 环形图通用构建（v3 清爽排版）：
 * - 不显示图上标签（避免文字拥挤），改为「中心汇总数字 + 底部图例带百分比」
 * - 颜色显式传入；全 0 时回退灰色"暂无数据"
 */
function buildDonutOption(opts: {
  centerText: string
  centerSub: string
  items: Array<{ name: string; value: number; color: string }>
}): EChartsOption {
  const data = opts.items
    .filter((it) => it.value > 0)
    .map((it) => ({ name: it.name, value: it.value, itemStyle: { color: it.color } }))
  const total = data.reduce((s, it) => s + it.value, 0)
  const finalData =
    data.length > 0 ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: EMPTY_COLOR } }]

  const legendFormatter = (name: string) => {
    const it = finalData.find((d) => d.name === name)
    if (!it) return name
    const pct = ((it.value / (total || 1)) * 100).toFixed(1)
    return `${name}  ${pct}%`
  }

  return {
    title: {
      text: opts.centerText,
      subtext: opts.centerSub,
      left: 'center',
      top: '35%',
      textStyle: { fontSize: 22, fontWeight: 700, color: '#1F2937' },
      subtextStyle: { fontSize: 12, color: '#9CA3AF', lineHeight: 18 },
      itemGap: 4,
    },
    tooltip: { ...tooltipStyle, trigger: 'item' as const, formatter: '{b}: {c} ({d}%)' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 18,
      textStyle: { color: AXIS_LABEL_COLOR, fontSize: 12 },
      formatter: legendFormatter,
    },
    series: [
      {
        type: 'pie' as const,
        radius: ['46%', '70%'],
        center: ['50%', '42%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: {
          scale: true,
          label: { show: true, fontSize: 13, fontWeight: 600, formatter: '{b}\n{c} ({d}%)' },
        },
        data: finalData,
      },
    ],
  }
}

// ─── 构成类图表 ───────────────────────────────────────────────────────────────

/** 用户构成（正常 / 禁用） */
export function buildUserPieOption(stats: AdminStatisticsVO | null): EChartsOption {
  const total = stats?.userTotal ?? 0
  const disabled = stats?.disabledUsers ?? 0
  return buildDonutOption({
    centerText: String(total),
    centerSub: '用户总数',
    items: [
      { name: '正常用户', value: Math.max(total - disabled, 0), color: PRIMARY },
      { name: '禁用用户', value: disabled, color: '#CBD5E1' },
    ],
  })
}

/** 今日活跃占比（活跃 / 未活跃） */
export function buildActivePieOption(stats: AdminStatisticsVO | null): EChartsOption {
  const total = stats?.userTotal ?? 0
  const active = stats?.activeTodayUsers ?? 0
  const pct = total > 0 ? `${((active / total) * 100).toFixed(1)}%` : '0%'
  return buildDonutOption({
    centerText: pct,
    centerSub: '今日活跃率',
    items: [
      { name: '今日活跃', value: active, color: SUCCESS },
      { name: '今日未活跃', value: Math.max(total - active, 0), color: '#E5E7EB' },
    ],
  })
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
  return buildDonutOption({
    centerText: String(total),
    centerSub: '订单总数',
    items: [
      { name: '待发货', value: pending, color: PRIMARY },
      { name: '其他已支付', value: Math.max(paid - pending, 0), color: SUCCESS },
      { name: '待付款/已取消', value: Math.max(total - paid, 0), color: '#CBD5E1' },
    ],
  })
}

/** 商品在售情况（在售 / 下架），柱状 + 顶部数值标签 */
export function buildProductBarOption(stats: AdminStatisticsVO | null): EChartsOption {
  const total = stats?.productTotal ?? 0
  const onSale = stats?.onSaleProducts ?? 0
  const offSale = Math.max(total - onSale, 0)
  return {
    tooltip: { ...tooltipStyle, trigger: 'axis' as const, axisPointer: { type: 'shadow' as const } },
    grid: { left: 8, right: 8, top: 32, bottom: 24, containLabel: true },
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
        barWidth: 56,
        label: {
          show: true,
          position: 'top',
          color: AXIS_LABEL_COLOR,
          fontSize: 12,
          fontWeight: 600,
          formatter: '{c}',
        },
        data: [
          { value: onSale, itemStyle: brandBarStyle },
          { value: offSale, itemStyle: { color: EMPTY_COLOR, borderRadius: [6, 6, 0, 0] as [number, number, number, number] } },
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
    tooltip: { ...tooltipStyle, trigger: 'axis' as const, axisPointer: { type: 'shadow' as const } },
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
        itemStyle: brandBarStyle,
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
      ...tooltipStyle,
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
        itemStyle: goldBarStyle,
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
        areaStyle: {
          color: {
            type: 'linear' as const,
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(34, 197, 94, 0.18)' },
              { offset: 1, color: 'rgba(34, 197, 94, 0.02)' },
            ],
          },
        },
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
