<template>
  <view class="pd" hover-class="pd-hover" :hover-stay-time="120" @click="$emit('tap', product)">
    <view class="thumb">
      <image
        v-if="product.mainImage"
        class="thumb-img"
        :src="product.mainImage"
        mode="aspectFill"
        lazy-load
        :alt="product.name || '商品图片'"
      />
      <wd-icon v-else name="picture" size="140rpx" custom-class="thumb-ph-ic" />
    </view>
    <view class="in">
      <text class="nm"><text v-if="product.subtitle" class="nm-pre">{{ product.subtitle }}</text>{{ product.name }}</text>
      <view v-if="tagList.length" class="tags">
        <text v-for="(tag, i) in tagList" :key="i" class="tag" :class="tagClass(tag)">{{ tag }}</text>
      </view>
      <view v-if="product.recommendReason" class="reason">
        <text class="reason-badge">AI</text>
        <text class="reason-txt">{{ product.recommendReason }}</text>
      </view>
      <view class="prow">
        <view class="p">
          <text class="p-i">¥</text>
          <text class="p-b">{{ product.price }}</text>
          <text v-if="product.originalPrice" class="p-s">{{ product.originalPrice }}</text>
        </view>
        <view class="add" @click.stop="$emit('add', product)" role="button" aria-label="加入购物车">
          <wd-icon name="add" size="24rpx" custom-class="add-ic" />
        </view>
      </view>
      <text class="sold">已售 {{ product.sales || 0 }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  product: {
    type: Object,
    default: () => ({})
  }
})
defineEmits(['tap', 'add'])

/** subtitle 与后端 tags 字段都可能带标签，合并后按参考稿最多展示 3 个 */
const tagList = computed(() => {
  const raw = props.product.tags
  const list = Array.isArray(raw) ? raw : String(raw || '').split(/[,，\s]+/).filter(Boolean)
  return list.slice(0, 3)
})

/** 服务/物流类走绿色，券/满减类走红色，其余灰色（对应参考稿 .tg/.tr/.tk） */
function tagClass(tag) {
  if (/券|折|减|惠|抢/.test(tag)) return 'tr'
  if (/包赔|包邮|溯源|直发|冷链|发货|无理由|新鲜/.test(tag)) return 'tg'
  return 'tk'
}
</script>

<style lang="scss" scoped>
/* 参考稿 .pd：定高图 + 两行标题 + 描边标签 + 价格行（右下加购） + 销量 */
.pd {
  background: $uni-bg-color;
  border-radius: $uni-border-radius-base;
  overflow: hidden;
  box-shadow: $uni-shadow-sm;
  transition: opacity 150ms ease-out;
}

.pd-hover {
  opacity: 0.85;
}

.thumb {
  width: 100%;
  height: 304rpx;
  background: $uni-bg-color-img;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.thumb-img {
  width: 100%;
  height: 304rpx;
}

:deep(.thumb-ph-ic) {
  color: $uni-color-img-icon;
}

.in {
  padding: 16rpx 18rpx 20rpx;
}

.nm {
  font-size: 26rpx;
  line-height: 36rpx;
  height: 72rpx;
  color: $uni-text-color;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.nm-pre {
  color: $uni-color-primary;
  font-weight: 600;
  margin-right: 8rpx;
}

.tags {
  display: flex;
  gap: 8rpx;
  flex-wrap: wrap;
  margin: 12rpx 0;
}

.tag {
  font-size: 18rpx;
  line-height: 28rpx;
  padding: 0 8rpx;
  border-radius: 4rpx;
  border: 2rpx solid $uni-border-color;
}

.tag.tg {
  color: $uni-color-service;
  border-color: $uni-color-service-100;
}

.tag.tr {
  color: $uni-color-primary;
  border-color: $uni-color-primary-100;
}

.tag.tk {
  color: $uni-text-color-grey;
  border-color: $uni-border-color;
}

/* AI 可解释推荐理由（仅个性化 feed 命中时出现） */
.reason {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  margin: 4rpx 0 10rpx;
  padding: 2rpx 10rpx 2rpx 4rpx;
  border-radius: 6rpx;
  background: $uni-color-primary-50;
}

.reason-badge {
  font-size: 16rpx;
  font-weight: 700;
  line-height: 1;
  color: $uni-text-color-inverse;
  background: $uni-color-primary;
  border-radius: 4rpx;
  padding: 3rpx 6rpx;
}

.reason-txt {
  font-size: 20rpx;
  line-height: 28rpx;
  color: $uni-color-primary;
}

.prow {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
}

.p {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
  color: $uni-color-price;
  font-family: $uni-font-family-num;
  font-variant-numeric: tabular-nums;
  min-width: 0;
}

.p-i {
  font-size: 22rpx;
  font-weight: 700;
}

.p-b {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -1rpx;
}

.p-s {
  font-size: 22rpx;
  color: $uni-text-color-3;
  text-decoration: line-through;
  margin-left: 6rpx;
}

.add {
  width: 44rpx;
  height: 44rpx;
  border-radius: 22rpx;
  background: $uni-color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

:deep(.add-ic) {
  color: $uni-text-color-inverse;
}

.sold {
  display: block;
  margin-top: 10rpx;
  font-size: 20rpx;
  color: $uni-text-color-placeholder;
  font-family: $uni-font-family-num;
}
</style>
