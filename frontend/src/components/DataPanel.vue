<script setup lang="ts">
import { computed } from 'vue'
import { RefreshOutline } from '@vicons/ionicons5'
import type { DataTableColumns, DataTableRowData, DataTableRowKey, PaginationProps } from 'naive-ui'
import EmptyState from '@/components/EmptyState.vue'
import TableSkeleton from '@/components/TableSkeleton.vue'

const props = withDefaults(defineProps<{
  title: string
  columns: unknown[]
  data: object[]
  total?: number
  loading?: boolean
  initialLoading?: boolean
  scrollX?: number
  pageSize?: number
  rowHeight?: number
  size?: 'small' | 'medium' | 'large'
  striped?: boolean
  emptyTitle?: string
  emptyDescription?: string
  showRefresh?: boolean
  pagination?: false | PaginationProps
}>(), {
  pageSize: 10,
  rowHeight: 48,
  size: 'small',
  striped: true,
  emptyTitle: '暂无数据',
  emptyDescription: '当前筛选条件下没有可展示的记录。',
  showRefresh: true
})

const emit = defineEmits<{
  refresh: []
}>()

const tableColumns = computed(() => props.columns as DataTableColumns<DataTableRowData>)
const tableData = computed(() => props.data as DataTableRowData[])
const recordCount = computed(() => props.total ?? props.data.length)
const resolvedPagination = computed(() => {
  if (props.pagination === false) return false
  return {
    pageSize: props.pageSize,
    itemCount: recordCount.value,
    showSizePicker: true,
    pageSizes: [10, 20, 50, 100],
    prefix: ({ itemCount }: { itemCount?: number }) => `共 ${itemCount ?? recordCount.value} 条`,
    ...(props.pagination || {})
  }
})

function rowKey(row: DataTableRowData): DataTableRowKey {
  const id = row.id
  if (typeof id === 'string' || typeof id === 'number') return id
  return JSON.stringify(row)
}
</script>

<template>
  <n-card :bordered="false" class="data-panel">
    <div class="data-panel__header">
      <div class="data-panel__title">
        <strong>{{ title }}</strong>
        <n-tag size="small" :bordered="false" class="data-panel__count">{{ recordCount }} 条</n-tag>
      </div>
      <div class="data-panel__actions">
        <slot name="actions" />
        <n-button v-if="showRefresh" secondary size="small" :loading="loading" @click="emit('refresh')">
          <template #icon>
            <n-icon :component="RefreshOutline" />
          </template>
          刷新
        </n-button>
      </div>
    </div>

    <TableSkeleton v-if="initialLoading || (loading && !data.length)" :rows="5" :columns="Math.min(columns.length, 6)" />
    <n-data-table
      v-else
      :columns="tableColumns"
      :data="tableData"
      :loading="loading"
      :row-key="rowKey"
      :row-props="() => ({ style: { height: `${rowHeight}px` } })"
      :pagination="resolvedPagination"
      :scroll-x="scrollX"
      :size="size"
      :striped="striped"
    >
      <template #empty>
        <slot name="empty">
          <EmptyState :title="emptyTitle" :description="emptyDescription">
            <template v-if="$slots.emptyAction" #action>
              <slot name="emptyAction" />
            </template>
          </EmptyState>
        </slot>
      </template>
    </n-data-table>
  </n-card>
</template>

<style scoped>
.data-panel {
  margin-bottom: var(--space-7);
}

.data-panel :deep(.n-card__content) {
  padding: var(--space-5);
}

.data-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}

.data-panel__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.data-panel__title strong {
  font-size: 15px;
  line-height: 22px;
  color: var(--text);
}

.data-panel__count {
  color: var(--brand);
  background: var(--brand-soft);
}

.data-panel__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
}

@media (max-width: 720px) {
  .data-panel__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .data-panel__actions {
    justify-content: flex-start;
  }
}
</style>
