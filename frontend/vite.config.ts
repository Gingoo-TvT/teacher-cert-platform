import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: 'src/components.d.ts',
      resolvers: [NaiveUiResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    },
    // 开发期首次进入某页时 vite 才按需转换该页及其子组件；Phase 34 拆分后模块数增多，
    // 首次导航因此卡顿。启动即预热 布局/共享组件/全部视图，使导航更顺滑。
    warmup: {
      clientFiles: [
        './src/layouts/MainLayout.vue',
        './src/components/*.vue',
        './src/views/**/*.vue'
      ]
    }
  },
  build: {
    manifest: true,
    chunkSizeWarningLimit: 550,
    rollupOptions: {
      output: {
        // 图表代码只由异步 ChartBox 请求；Naive UI 交给组件按需导入与路由分包，避免整库进入登录首屏。
        manualChunks(id) {
          const normalized = id.replace(/\\/g, '/')
          if (normalized.includes('/node_modules/echarts/') || normalized.includes('/node_modules/zrender/')) {
            return 'charts'
          }
          if (
            normalized.includes('/node_modules/vue/')
            || normalized.includes('/node_modules/vue-router/')
            || normalized.includes('/node_modules/pinia/')
          ) {
            return 'vue'
          }
        }
      }
    }
  }
})
