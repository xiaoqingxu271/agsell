<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminStatistics } from '@/api/admin'
import type { AdminStatisticsVO } from '@/types'

const stats = ref<AdminStatisticsVO | null>(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    stats.value = await getAdminStatistics()
  } finally {
    loading.value = false
  }
})

function fmtSales(v: number | undefined): string {
  return `¥${Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
</script>

<template>
  <div class="page">
    <h2 style="margin-bottom: 1rem">数据概览</h2>
    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="用户总数" :value="stats?.userTotal ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="今日新增" :value="stats?.todayNewUsers ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="今日活跃" :value="stats?.activeTodayUsers ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="禁用用户" :value="stats?.disabledUsers ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic title="商品总数" :value="stats?.productTotal ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic title="在售商品" :value="stats?.onSaleProducts ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic title="订单总数" :value="stats?.orderTotal ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic title="待发货订单" :value="stats?.pendingShipOrders ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic title="已支付订单" :value="stats?.paidOrders ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover">
          <el-statistic title="销售总额" :value="fmtSales(stats?.totalSales)" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
}
</style>
