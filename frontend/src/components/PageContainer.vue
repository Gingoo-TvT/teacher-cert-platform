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
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title-block {
  min-width: 0;
}

.page-title-block h1 {
  margin: 0;
  font-size: 20px;
  line-height: 1.35;
  font-weight: 650;
  color: #111827;
  letter-spacing: 0;
}

.page-title-block p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-muted);
}

.page-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
