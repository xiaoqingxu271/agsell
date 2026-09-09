<script setup lang="ts">
/**
 * 页面级标题头（企业级 v3 规范 §6.8 / §3.2）
 * 用法：<PageHeader title="商品管理" description="维护商品信息与上下架状态"><el-button>…</el-button></PageHeader>
 */
defineProps<{
  /** 页面主标题 */
  title: string
  /** 可选：一行辅助说明 */
  description?: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="page-header-main">
      <h2 class="page-header-title">{{ title }}</h2>
      <p v-if="description" class="page-header-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.default" class="page-header-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.page-header-main {
  min-width: 0;
}

.page-header-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  letter-spacing: 0.01em;
  color: #0A2A1B;
}

.page-header-desc {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: #6B7280;
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
