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
  background-color: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 28rpx;
  color: #333;
  box-sizing: border-box;
}

/* 通用卡片 */
.card {
  background: #fff;
  border-radius: 16rpx;
  margin: 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

/* 按钮 */
.btn-primary {
  background: #4CAF50;
  color: #fff;
  border: none;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 32rpx;
}

.btn-primary::after {
  border: none;
}

/* 价格 */
.price {
  color: #FF9800;
  font-weight: bold;
}

.price::before {
  content: '¥';
  font-size: 24rpx;
}

/* 分割线 */
.divider {
  height: 1rpx;
  background: #eee;
  margin: 24rpx 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
  color: #999;
}
</style>
