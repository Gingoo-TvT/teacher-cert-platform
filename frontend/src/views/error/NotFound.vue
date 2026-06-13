<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div style="height: 100vh; display: flex; align-items: center; justify-content: center">
    <n-result status="404" title="404" description="页面不存在">
      <template #footer>
        <n-button @click="router.push('/')">返回首页</n-button>
      </template>
    </n-result>
  </div>
</template>
