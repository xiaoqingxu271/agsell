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
page {
  background-color: #F0FDF4;
  font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei',
    'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 28rpx;
  color: #1F2937;
  box-sizing: border-box;
}

/* 横屏/平板内容区居中 */
.container {
  max-width: 900rpx;
  margin: 0 auto;
}

/* 通用卡片（MASTER v2.0 §6.2：白底 + 24rpx 圆角 + #BBF7D0 品牌描边 + shadow-sm） */
.card {
  background: #FFFFFF;
  border-radius: 24rpx;
  margin: 24rpx;
  padding: 24rpx;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
  border: 1px solid #BBF7D0;
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
  border: 1px solid #15803D;
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

.price::before {
  content: '¥';
  font-size: 24rpx;
}

/* 分割线 */
.divider {
  height: 1rpx;
  background: #E5E7EB;
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

/* 状态标签 - 浅底深字（MASTER v2.0 §7.3：圆角 12rpx） */
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
  background: #F3F4F6;
  color: #4B5563;
}

.tag-accent {
  background: #FFF7ED;
  color: #9A3412;
}
</style>
