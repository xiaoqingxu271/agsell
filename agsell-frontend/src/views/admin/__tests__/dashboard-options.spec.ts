import { describe, expect, it } from 'vitest'
import {
  buildActivePieOption,
  buildOrderPieOption,
  buildProductBarOption,
  buildSentimentPieOption,
  buildUserPieOption,
  buildUserTrendOption,
  emptyTrendOption,
} from '../dashboard-options'
import type { AdminStatisticsVO, StatisticsTrendVO } from '@/types'

/** 从环形图 option 中取出系列数据项（name/value 对） */
function donutItems(option: ReturnType<typeof buildUserPieOption>): Array<{ name: string; value: number }> {
  const series = option.series as Array<{ data: Array<{ name: string; value: number }> }>
  return series[0].data.map(({ name, value }) => ({ name, value }))
}

const baseStats: AdminStatisticsVO = {
  userTotal: 200,
  todayNewUsers: 36,
  activeTodayUsers: 50,
  disabledUsers: 20,
  productTotal: 86,
  onSaleProducts: 72,
  orderTotal: 100,
  paidOrders: 60,
  pendingShipOrders: 30,
  totalSales: 128430.5,
}

describe('buildUserPieOption', () => {
  it('统计为空时回退灰色「暂无数据」', () => {
    const items = donutItems(buildUserPieOption(null))
    expect(items).toHaveLength(1)
    expect(items[0].name).toBe('暂无数据')
  })

  it('正常用户 = 总数 - 禁用数', () => {
    const items = donutItems(buildUserPieOption(baseStats))
    expect(items).toContainEqual({ name: '正常用户', value: 180 })
    expect(items).toContainEqual({ name: '禁用用户', value: 20 })
  })
})

describe('buildActivePieOption', () => {
  it('中心数字为活跃率百分比', () => {
    const option = buildActivePieOption(baseStats)
    const title = option.title as { text: string }
    expect(title.text).toBe('25.0%')
  })

  it('用户总数为 0 时活跃率显示 0%', () => {
    const option = buildActivePieOption({ ...baseStats, userTotal: 0, activeTodayUsers: 0 })
    const title = option.title as { text: string }
    expect(title.text).toBe('0%')
  })
})

describe('buildOrderPieOption', () => {
  it('待付款/已取消 = 总数 - 已支付', () => {
    const items = donutItems(buildOrderPieOption(baseStats))
    expect(items).toContainEqual({ name: '待发货', value: 30 })
    expect(items).toContainEqual({ name: '其他已支付', value: 30 })
    expect(items).toContainEqual({ name: '待付款/已取消', value: 40 })
  })

  it('其他已支付不会出现负数（已支付 < 待发货时）', () => {
    const items = donutItems(buildOrderPieOption({ ...baseStats, paidOrders: 10, pendingShipOrders: 30 }))
    expect(items.every((i) => i.value >= 0)).toBe(true)
  })
})

describe('buildSentimentPieOption', () => {
  it('中心数字为好评率', () => {
    const option = buildSentimentPieOption({ total: 100, positiveCount: 70, neutralCount: 20, negativeCount: 10 })
    const title = option.title as { text: string }
    expect(title.text).toBe('70.0%')
  })

  it('无评价数据时好评率 0% 且全量回退', () => {
    const option = buildSentimentPieOption(null)
    const title = option.title as { text: string }
    expect(title.text).toBe('0%')
    expect(donutItems(option)[0].name).toBe('暂无数据')
  })
})

describe('buildProductBarOption', () => {
  it('柱值为在售数与下架数', () => {
    const option = buildProductBarOption(baseStats)
    const series = option.series as Array<{ data: Array<{ value: number }> }>
    expect(series[0].data.map((d) => d.value)).toEqual([72, 14])
  })
})

describe('趋势图', () => {
  it('buildUserTrendOption 使用日期与新增用户序列', () => {
    const trend: StatisticsTrendVO = {
      dates: ['2026-10-01', '2026-10-02'],
      newUsers: [10, 20],
      orderCounts: [1, 2],
      sales: [100, 200],
    }
    const option = buildUserTrendOption(trend)
    const xAxis = option.xAxis as { data: string[] }
    const series = option.series as Array<{ data: number[] }>
    expect(xAxis.data).toEqual(trend.dates)
    expect(series[0].data).toEqual([10, 20])
  })

  it('emptyTrendOption 返回空序列', () => {
    const option = emptyTrendOption()
    const xAxis = option.xAxis as { data: string[] }
    expect(xAxis.data).toEqual([])
    expect(option.series).toEqual([])
  })
})
