<script setup>
import { onLaunch } from '@dcloudio/uni-app'
import { request } from './utils/request'

onLaunch(() => {
  // 恢复上次登录的 token（依赖 uniStorage，不依赖 globalData）
  const token = uni.getStorageSync('token')
  if (token) {
    console.log('[App] 恢复登录态，token:', token ? token.substring(0, 20) + '...' : 'none')
  }
  // 拉取平台名称并缓存，供首页导航栏 / 关于弹窗等展示（管理端"系统配置"可修改）
  loadPlatformName()
})

/** 从系统配置拉取平台名称，缓存到本地；失败时静默，保留页面默认值兜底 */
async function loadPlatformName() {
  try {
    const res = await request('GET', '/system/config', null, { params: { keys: 'platform_name' } })
    if (res.code === 0 && res.data?.platform_name) {
      uni.setStorageSync('platform_name', res.data.platform_name)
    }
  } catch (e) {
    console.warn('[App] 加载平台名称失败', e)
  }
}
</script>

<style>
/* ============================================================
 * agsell 小程序设计系统 v5.0（品质电商风 · 京东/天猫超市参照）
 * 权威来源：doc/ui-design/v2/full-flow.html
 * 语义分工：品牌红=价格/CTA/选中态，服务绿=成功/自营/保障，加购橙=加入购物车
 * ============================================================ */

page {
  background-color: #F4F5F7;
  font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'Microsoft YaHei',
    'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 28rpx;
  color: #1A1A1A;
  box-sizing: border-box;

  /* wot-design-uni 主题覆写：收敛到设计系统 v5.0 */
  --wot-color-theme: #E1251B;
  --wot-color-success: #00A66A;
  --wot-color-danger: #D93026;
  --wot-color-warning: #D97706;
}

/* 横屏/平板内容区居中 */
.container {
  max-width: 900px;
  margin: 0 auto;
}

/* 通用卡片：白底 + 16rpx 圆角 + 微阴影 */
.card {
  background: #FFFFFF;
  border-radius: 16rpx;
  margin: 24rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 4rpx rgba(20, 24, 31, 0.04);
}

/* 按压反馈 */
.card:active,
.btn-primary:active,
.btn-cart:active,
.btn-ghost:active,
.btn-secondary:active,
.checkout-btn:active,
.submit-btn:active,
.action-btn:active {
  opacity: 0.85;
  transition: opacity 150ms ease-out;
}

/* 主按钮（品牌红） */
.btn-primary {
  background: #E1251B;
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
  background: #E3E5E8;
  color: #9AA0A8;
}

/* 加入购物车（橙） */
.btn-cart {
  background: #FF9200;
  color: #FFFFFF;
  border: none;
  border-radius: 44rpx;
  height: 88rpx;
  line-height: 88rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.btn-cart::after {
  border: none;
}

/* 次要按钮 ghost（品牌红描边） */
.btn-ghost {
  background: #FFFFFF;
  color: #E1251B;
  border: 1rpx solid #E1251B;
  border-radius: 40rpx;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
}

.btn-ghost::after {
  border: none;
}

/* 价格（品牌红 + 等宽数字，数字必须对齐） */
.price {
  color: #E1251B;
  font-weight: 700;
  font-family: 'DIN Alternate', 'Bahnschrift', 'Helvetica Neue', Arial, sans-serif;
  font-variant-numeric: tabular-nums;
}

/* 分割线 */
.divider {
  height: 1rpx;
  background: #EDEEF0;
  margin: 24rpx 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;
  color: #9AA0A8;
  font-size: 28rpx;
}

/* 状态标签：绿=服务承诺，红=促销，黄=券/待付，灰=属性 */
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
  background: #E8F6EF;
  color: #00A66A;
}

.tag-warning {
  background: #FFF7ED;
  color: #D97706;
}

.tag-danger {
  background: #FDEEEC;
  color: #D93026;
}

.tag-primary {
  background: #FDEEEC;
  color: #E1251B;
}

.tag-info {
  background: #F3F4F6;
  color: #666A73;
}

.tag-accent {
  background: #FDEEEC;
  color: #E1251B;
}

/* ============================================================
 * H5 宽屏适配：渲染成 375px 手机画布，与设计稿 full-flow.html 的
 * .phone{width:375px} 逐像素对齐。
 *
 * uni-app H5 把 rpx 编译成 rem，并由 JS 把 html{font-size} 设成
 * innerWidth/750*32，因此只限容器宽度会让所有字号按真实视口放大。
 * 必须同时把根字号钉到 16px（= 375/750*32，即 1rpx = 0.5px）。
 * transform 让 uni-app 成为 fixed 后代的包含块，底部操作栏自动收进列内。
 * ============================================================ */
/* #ifdef H5 */
@media (min-width: 520px) {
  html {
    /* stylelint-disable-next-line declaration-no-important */
    font-size: 16px !important;
    background: #E9EBEE;
  }

  uni-app {
    width: 375px;
    min-height: 100vh;
    margin: 0 auto;
    position: relative;
    transform: translateZ(0);
    background: #F4F5F7;
    box-shadow: 0 0 0 1px #E3E5E8, 0 16px 48px rgba(20, 24, 31, 0.16);
  }
}
/* #endif */
</style>
