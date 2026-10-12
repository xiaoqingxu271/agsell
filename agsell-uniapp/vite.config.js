import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig(({ command }) => {
  // 仅 H5 开发服务器生效：请求改走同源 /api 并由下方代理转发到后端，
  // 绕过后端未配置 CORS 的限制；小程序端不存在跨域，仍使用 .env 的绝对地址。
  const isH5Dev = command === 'serve' && (process.env.UNI_PLATFORM || 'h5') === 'h5'
  return {
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    build: {
      // H5 产物与 static 统一输出到 dist/build/h5（vite 默认 dist/ 会与 uni 拷贝的
      // static 目录分离，导致线上 /static/* 图标 404）。微信端构建由 uni 插件
      // 自行决定输出目录（dist/build/mp-weixin），不受此项影响。
      outDir: 'dist/build/h5'
    },
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    define: isH5Dev
      ? {
          'import.meta.env.VITE_API_BASE_URL': JSON.stringify('/api'),
          // 发行构建会按 pages.json 自动打开下拉刷新特性位，dev 下 uni-h5 原样加载、
          // 该特性位恒为假，页面级 enablePullDownRefresh 在 dev 里不生效，这里补齐口径
          __UNI_FEATURE_PULL_DOWN_REFRESH__: 'true'
        }
      : {},
    plugins: [uni()]
  }
})
