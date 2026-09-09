import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // Element Plus 按需引入：模板中的 el-* 组件自动按需注册，显著减小首屏包体积。
    // importStyle: false —— 组件样式仍走 main.ts 中一次性引入的完整主题 CSS，
    // 保证 theme.css 的覆盖始终生效（避免按需样式后置覆盖主题的问题）。
    Components({
      resolvers: [ElementPlusResolver({ importStyle: false })],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // 按需引入后图表页 chunk（echarts + EP 表格/表单）仍可能接近该阈值，仅作提示用
    chunkSizeWarningLimit: 900,
  },
})
