import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
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
  plugins: [uni()]
})
