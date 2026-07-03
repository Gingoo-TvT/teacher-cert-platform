import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
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
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        // 生产构建把大依赖拆成独立可缓存 chunk，减小主包、加快后续导航
        manualChunks: {
          echarts: ['echarts'],
          naive: ['naive-ui'],
          vue: ['vue', 'vue-router', 'pinia']
        }
      }
    }
  }
})
