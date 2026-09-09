// 顺序重要：Element Plus 基础样式在前，项目主题覆盖在后
import 'element-plus/dist/index.css'
import './styles/admin-table.css'
import './styles/theme.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
