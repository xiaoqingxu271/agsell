<script setup lang="ts">
</script>

<template>
  <div class="page">
    <h2 style="margin-bottom: 1rem">数据概览</h2>
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="用户总数" :value="128" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="今日新增" :value="5" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="活跃用户" :value="42" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="禁用用户" :value="3" />
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
