<script setup lang="ts">
/**
 * 页面级标题头（v4.0 自然丰盈规范）
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
  margin-bottom: 18px;
}

.page-header-main {
  min-width: 0;
}

.page-header-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.01em;
  color: #10231A;
}

.page-header-desc {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: #8A9A91;
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
