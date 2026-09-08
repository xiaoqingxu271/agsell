<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
// 按需引入 echarts（仅注册本项目用到的图表与组件，显著减小打包体积）
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts, EChartsOption } from 'echarts'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

/**
 * 通用 ECharts 封装组件
 * - onMounted 后初始化（DOM 就绪）、onBeforeUnmount 销毁实例
 * - ResizeObserver 监听容器尺寸变化，自动 resize（侧边栏折叠等场景也生效）
 * - option 变化时以替换模式重绘
 */
const props = defineProps<{
  option: EChartsOption
}>()

const el = ref<HTMLDivElement | null>(null)
const chart = shallowRef<ECharts | null>(null)
let resizeObserver: ResizeObserver | null = null

function render() {
  if (chart.value) {
    chart.value.setOption(props.option, true)
  }
}

onMounted(() => {
  if (!el.value) return
  chart.value = echarts.init(el.value)
  render()

  resizeObserver = new ResizeObserver(() => {
    chart.value?.resize()
  })
  resizeObserver.observe(el.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chart.value?.dispose()
  chart.value = null
})

watch(() => props.option, render, { deep: true })
</script>

<template>
  <div ref="el" class="base-chart" />
</template>

<style scoped>
.base-chart {
  width: 100%;
  height: 100%;
}
</style>
