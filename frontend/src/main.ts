import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// vue-echarts / echarts are installed as dependencies (imported in chart components in Phase 3)
// Importing here ensures the package is bundled and the import path is tested at build time

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
