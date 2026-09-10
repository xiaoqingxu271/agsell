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
 * agsell 小程序设计系统 v3.0（企业级高级感）
 * 权威来源：doc/miniprogram-design-system-v3.md
 * ============================================================ */

page {
  background-color: #F4F6F5;
  font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei',
    'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 28rpx;
  color: #1F2937;
  box-sizing: border-box;
}

/* 横屏/平板内容区居中（v3.0：CSS px 约束，rpx 随视口缩放无法做平板约束） */
.container {
  max-width: 900px;
  margin: 0 auto;
}

/* 通用卡片（v3.0：白底 + 24rpx 圆角 + 中性描边 + 分层阴影，不再用绿描边识别边界） */
.card {
  background: #FFFFFF;
  border-radius: 24rpx;
  margin: 24rpx;
  padding: 24rpx;
  box-shadow: 0 1rpx 2rpx rgba(16, 24, 40, 0.05);
  border: 1rpx solid #E3E7E5;
}

/* 按压反馈（H5 生效；微信端关键组件用 hover-class，见 ProductCard 等） */
.card:active,
.btn-primary:active,
.btn-ghost:active,
.btn-secondary:active,
.checkout-btn:active,
.submit-btn:active,
.action-btn:active {
  transform: scale(0.985);
  opacity: 0.92;
  transition: transform 120ms ease-out, opacity 120ms ease-out;
}

/* 按钮 */
.btn-primary {
  background: #15803D;
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
  background: transparent;
  color: #15803D;
  border: 1rpx solid #15803D;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 28rpx;
}

.btn-ghost::after {
  border: none;
}

/* 价格 */
.price {
  color: #A16207;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* 分割线 */
.divider {
  height: 1rpx;
  background: #EEF1EF;
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

/* 状态标签 - 浅底深字（v3.0 §2.4，圆角 12rpx） */
.tag {
  display: inline-flex;
  align-items: center;
  height: 44rpx;
  padding: 0 16rpx;
  border-radius: 12rpx;
  font-size: 24rpx;
  font-weight: 500;
  line-height: 1;
}

.tag-success {
  background: #DCFCE7;
  color: #166534;
}

.tag-warning {
  background: #FEF3C7;
  color: #92400E;
}

.tag-danger {
  background: #FEE2E2;
  color: #991B1B;
}

.tag-primary {
  background: #BBF7D0;
  color: #14532D;
}

.tag-info {
  background: #F3F5F4;
  color: #4B5563;
}

.tag-accent {
  background: #FFF7ED;
  color: #9A3412;
}

/* ============================================================
 * 宽屏适配（平板 / H5 宽窗）：内容与底部固定栏居中到 900px
 * 注意：这里必须用 CSS px（rpx 会随视口缩放，无法做平板约束）
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
