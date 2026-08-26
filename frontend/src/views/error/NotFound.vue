<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  void router.replace('/')
}
</script>

<template>
  <main class="error-page">
    <div class="error-brand" aria-label="教师证书平台">
      <span class="error-brand__mark">师</span>
      <span>教师证书平台</span>
    </div>
    <section class="error-surface">
      <n-result status="404" title="页面未找到" description="访问地址可能已失效，请返回工作台继续办理。">
        <template #footer>
          <n-button type="primary" @click="goHome">返回工作台</n-button>
        </template>
      </n-result>
    </section>
  </main>
</template>

<style scoped>
.error-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  place-content: center;
  gap: var(--space-6);
  padding: var(--space-6);
  background: var(--page-bg);
}

.error-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.error-brand__mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid var(--brand-border);
  border-radius: var(--radius-control);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: var(--font-size-lg);
  font-weight: 600;
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
    padding: var(--space-4);
  }

  .error-surface {
    padding: var(--space-6) var(--space-4);
  }
}
</style>
