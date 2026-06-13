<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'

const health = ref<string>('检测中...')

onMounted(async () => {
  try {
    const res = await request.get<unknown, { data?: { status?: string } }>('/health')
    health.value = res?.data?.status ? `后端连通：${res.data.status}` : '后端连通'
  } catch {
    health.value = '后端未连通（请确认 platform-boot 已启动）'
  }
})
</script>

<template>
  <n-space vertical :size="16">
    <n-h2 style="margin: 0">欢迎使用</n-h2>
    <n-alert title="脚手架就绪" type="success">
      前端 Vue 3 + Vite + TypeScript + Naive UI 已就绪；后续按 Phase 1+ 开发字典/认证/业务页面。
    </n-alert>
    <n-card title="后端健康检查">{{ health }}</n-card>
  </n-space>
</template>
