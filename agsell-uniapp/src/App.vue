<script setup>
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // 恢复上次登录的 token（依赖 uniStorage，不依赖 globalData）
  const token = uni.getStorageSync('token')
  if (token) {
    console.log('[App] 恢复登录态，token:', token ? token.substring(0, 20) + '...' : 'none')
  }
})
</script>

<style>
/* ============================================================
 * agsell 小程序设计系统 v4.0（生鲜电商标准版）
 * 权威来源：doc/miniprogram-design-system-v4.md
 * ============================================================ */

page {
  background-color: #F5F5F5;
  font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei',
    'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 28rpx;
  color: #111827;
  box-sizing: border-box;
}

/* 横屏/平板内容区居中 */
.container {
  max-width: 900px;
  margin: 0 auto;
}

/* 通用卡片：白底 + 24rpx 圆角 + 微阴影 */
.card {
  background: #FFFFFF;
  border-radius: 24rpx;
  margin: 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

/* 按压反馈 */
.card:active,
.btn-primary:active,
.btn-ghost:active,
.btn-secondary:active,
.checkout-btn:active,
.submit-btn:active,
.action-btn:active {
  opacity: 0.85;
  transition: opacity 150ms ease-out;
}

/* 主按钮（绿） */
.btn-primary {
  background: #00B578;
  color: #FFFFFF;
  border: none;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.btn-primary::after {
  border: none;
}

.btn-primary[disabled] {
  background: #E5E7EB;
  color: #9CA3AF;
}

/* 次要按钮 ghost */
.btn-ghost {
  background: #FFFFFF;
  color: #00B578;
  border: 1rpx solid #00B578;
  border-radius: 40rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
}

.btn-ghost::after {
  border: none;
}

/* 价格（红色） */
.price {
  color: #E63946;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* 分割线 */
.divider {
  height: 1rpx;
  background: #F0F0F0;
  margin: 24rpx 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
  color: #9CA3AF;
  font-size: 28rpx;
}

/* 状态标签 */
.tag {
  display: inline-flex;
  align-items: center;
  height: 40rpx;
  padding: 0 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: 500;
  line-height: 1;
}

.tag-success {
  background: #ECFDF5;
  color: #00B578;
}

.tag-warning {
  background: #FFF7ED;
  color: #D97706;
}

.tag-danger {
  background: #FEF2F2;
  color: #DC2626;
}

.tag-primary {
  background: #ECFDF5;
  color: #00B578;
}

.tag-info {
  background: #F3F4F6;
  color: #4B5563;
}

.tag-accent {
  background: #FEE2E2;
  color: #E63946;
}

/* ============================================================
 * 宽屏适配（平板 / H5 宽窗）
 * ============================================================ */
@media (min-width: 1000px) {
  .index-page,
  .category-page,
  .cart-page,
  .mine-page,
  .product-page,
  .order-list-page,
  .order-detail-page,
  .confirm-page,
  .pay-success-page,
  .review-list-page,
  .review-write-page,
  .apply-page,
  .as-detail-page,
  .as-list-page,
  .address-page {
    max-width: 900px;
    margin-left: auto;
    margin-right: auto;
  }

  .bottom-bar,
  .app-footer {
    left: 50%;
    transform: translateX(-50%);
    width: 900px;
    max-width: 100%;
  }
}
</style>
