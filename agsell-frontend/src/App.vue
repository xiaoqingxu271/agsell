<script setup lang="ts">
import { RouterView } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <RouterView />
  </el-config-provider>
</template>

<style>
* {
  margin: 0;
  padding: 0;
}

html,
body,
#app {
  height: 100%;
  width: 100%;
}

body {
  font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei', 'Noto Sans SC',
    -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Inter', 'Plus Jakarta Sans', sans-serif;
  font-size: 14px;
  line-height: 1.5;
  color: #1F2937;
  background: #F4F6F5;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

/* 记录数等混合文本数字统一 tabular-nums */
.admin-total-text {
  font-variant-numeric: tabular-nums;
}
</style>
