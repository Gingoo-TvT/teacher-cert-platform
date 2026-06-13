import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import App from './App.vue'
import router from './router'
import { permDirective } from './directives/perm'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(naive)
app.directive('perm', permDirective)
app.mount('#app')
