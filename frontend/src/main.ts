import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { permDirective } from './directives/perm'
import './theme/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.directive('perm', permDirective)
app.mount('#app')
