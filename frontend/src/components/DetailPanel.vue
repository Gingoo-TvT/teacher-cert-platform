<script setup lang="ts">
import StatusTag from '@/components/StatusTag.vue'

export interface DetailItem {
  label: string
  value?: string | number | null
  status?: string | null
  mono?: boolean
  span?: number
}

withDefaults(defineProps<{
  items: DetailItem[]
  columns?: number
}>(), {
  columns: 2
})

function text(value?: string | number | null) {
  if (value === null || typeof value === 'undefined' || value === '') return '-'
  return String(value)
}
</script>

<template>
  <n-descriptions bordered label-placement="left" :column="columns" size="small" class="detail-panel">
    <n-descriptions-item v-for="item in items" :key="item.label" :label="item.label" :span="item.span || 1">
      <StatusTag v-if="item.status" :value="item.status" />
      <span v-else :class="{ 'mono tabular-nums': item.mono }">{{ text(item.value) }}</span>
    </n-descriptions-item>
  </n-descriptions>
</template>

<style scoped>
.detail-panel :deep(.n-descriptions-table-header) {
  width: 112px;
  text-align: right;
  color: var(--text-secondary);
  background: var(--surface-muted);
}

.detail-panel :deep(.n-descriptions-table-content) {
  min-width: 0;
}
</style>
