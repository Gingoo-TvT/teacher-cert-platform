<script setup lang="ts">
withDefaults(defineProps<{
  rows?: number
  columns?: number
}>(), {
  rows: 5,
  columns: 5
})
</script>

<template>
  <div class="table-skeleton" role="status" aria-live="polite" aria-label="表格加载中">
    <div class="table-skeleton__head">
      <n-skeleton v-for="column in columns" :key="column" text :width="column === columns ? '68%' : '82%'" />
    </div>
    <div v-for="row in rows" :key="row" class="table-skeleton__row">
      <n-skeleton v-for="column in columns" :key="column" text :width="column === columns ? '62%' : '76%'" />
    </div>
  </div>
</template>

<style scoped>
.table-skeleton {
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-card);
  background: var(--surface);
}

.table-skeleton__head,
.table-skeleton__row {
  display: grid;
  grid-template-columns: repeat(v-bind(columns), minmax(80px, 1fr));
  gap: var(--space-4);
  align-items: center;
  min-height: 48px;
  padding: 0 var(--space-4);
}

.table-skeleton__head {
  background: var(--table-head-bg);
}

.table-skeleton__row + .table-skeleton__row {
  border-top: 1px solid var(--border);
}
</style>
