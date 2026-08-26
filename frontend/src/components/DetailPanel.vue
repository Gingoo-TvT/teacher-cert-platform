<script setup lang="ts">
import StatusTag from '@/components/StatusTag.vue'

export interface DetailItem {
  label: string
  value?: string | number | null
  status?: string | null
  mono?: boolean
  span?: number
}

const props = withDefaults(defineProps<{
  items: DetailItem[]
  columns?: number
}>(), {
  columns: 2
})

function text(value?: string | number | null) {
  if (value === null || typeof value === 'undefined' || value === '') return '-'
  return String(value)
}

function itemSpan(item: DetailItem) {
  return Math.min(Math.max(item.span || 1, 1), props.columns)
}
</script>

<template>
  <n-descriptions bordered label-placement="left" :column="props.columns" size="small" class="detail-panel">
    <n-descriptions-item v-for="item in props.items" :key="item.label" :label="item.label" :span="itemSpan(item)">
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
  overflow-wrap: anywhere;
}

@media (max-width: 720px) {
  .detail-panel :deep(.n-descriptions-table-header) {
    width: 92px;
  }
}
</style>
