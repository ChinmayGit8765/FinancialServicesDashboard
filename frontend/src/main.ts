import './style.css'         // global design tokens (dark theme) — MUST load before components
import './plugins/echarts'   // registers ECharts modules + quantlens-dark theme (side-effect)
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
