<script setup lang="ts">
import { useRouter } from 'vue-router'

defineOptions({ name: 'ForbiddenView' })

const router = useRouter()

function goHome() {
  void router.replace('/')
}
</script>

<template>
  <main class="error-page">
    <section class="error-surface">
      <n-result status="403" title="无权访问" description="当前账号没有此功能权限，可返回工作台选择其他可办理事项。">
        <template #footer>
          <n-button type="primary" @click="goHome">返回工作台</n-button>
        </template>
      </n-result>
    </section>
  </main>
</template>

<style scoped>
.error-page {
  min-height: calc(100vh - 116px);
  min-height: calc(100dvh - 116px);
  display: grid;
  place-content: center;
  padding: var(--space-6);
}

.error-surface {
  width: min(100%, 620px);
  padding: var(--space-8) var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius-card);
  background: var(--surface);
  box-shadow: var(--shadow-card);
}

@media (max-width: 520px) {
  .error-page {
    padding: var(--space-2) 0;
  }

  .error-surface {
    padding: var(--space-6) var(--space-4);
  }
}
</style>
