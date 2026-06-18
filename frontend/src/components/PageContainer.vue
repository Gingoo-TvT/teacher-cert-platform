<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <section class="page-container">
    <header class="page-header">
      <div class="page-title-block">
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <div class="page-actions">
        <slot name="actions" />
      </div>
    </header>
    <slot />
  </section>
</template>

<style scoped>
.page-container {
  min-width: 0;
  width: 100%;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-5);
  margin-bottom: var(--space-5);
}

.page-title-block {
  min-width: 0;
}

.page-title-block h1 {
  margin: 0;
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
  color: var(--text);
  letter-spacing: 0;
}

.page-title-block p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
}

.page-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
  min-height: 34px;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
    gap: var(--space-3);
  }

  .page-actions {
    justify-content: flex-start;
    width: 100%;
  }
}
</style>
