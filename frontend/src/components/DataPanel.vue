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
  maxHeight?: number
  defaultExpandAll?: boolean
  size?: 'small' | 'medium' | 'large'
  striped?: boolean
  emptyTitle?: string
  emptyDescription?: string
  error?: string
  errorTitle?: string
  showRefresh?: boolean
  pagination?: false | PaginationProps
  rowProps?: (row: DataTableRowData) => Record<string, unknown>
  // 服务端分页（P1-1 真分页契约）：remote=true 时 n-data-table 不再切片本地 data，
  // data 为「当前页」、total 为后端总数；翻页/改页大小经 update:page / update:pageSize 上抛父组件重新请求。
  remote?: boolean
  page?: number
}>(), {
  pageSize: 10,
  rowHeight: 48,
  size: 'small',
  striped: true,
  emptyTitle: '暂无数据',
  emptyDescription: '当前筛选条件下没有可展示的记录。',
  errorTitle: '数据加载失败',
  showRefresh: true,
  remote: false,
  page: 1
})

const emit = defineEmits<{
  refresh: []
  'update:page': [page: number]
  'update:pageSize': [size: number]
}>()

const tableColumns = computed(() => props.columns as DataTableColumns<DataTableRowData>)
const tableData = computed(() => props.data as DataTableRowData[])
const recordCount = computed(() => props.total ?? props.data.length)
const effectiveScrollX = computed(() => props.scrollX ?? inferScrollX(props.columns))
const resolvedPagination = computed(() => {
  if (props.pagination === false) return false
  const base = {
    pageSize: props.pageSize,
    itemCount: recordCount.value,
    showSizePicker: true,
    pageSizes: [10, 20, 50, 100],
    prefix: ({ itemCount }: { itemCount?: number }) => `共 ${itemCount ?? recordCount.value} 条`,
    ...(props.pagination || {})
  }
  if (!props.remote) return base
  // 服务端分页：受控 page + 翻页/改页大小事件上抛。
  return {
    ...base,
    page: props.page,
    onUpdatePage: (page: number) => emit('update:page', page),
    onUpdatePageSize: (size: number) => emit('update:pageSize', size)
  }
})

function rowKey(row: DataTableRowData): DataTableRowKey {
  const id = row.id
  if (typeof id === 'string' || typeof id === 'number') return id
  return JSON.stringify(row)
}

// 与 props.rowProps 显式区分命名：同名会在 <script setup> 作用域里遮蔽 prop 并触发 vue/no-dupe-keys。
function mergedRowProps(row: DataTableRowData) {
  const customProps = props.rowProps ? props.rowProps(row) : {}
  const style = customProps.style
  return {
    ...customProps,
    style: {
      ...(typeof style === 'object' && style !== null ? style : {}),
      height: `${props.rowHeight}px`
    }
  }
}

function inferScrollX(columns: unknown[]): number {
  return columns.reduce<number>((sum, column) => sum + columnWidth(column), 0)
}

function columnWidth(column: unknown): number {
  if (!column || typeof column !== 'object') return 120
  const item = column as Record<string, unknown>
  if (Array.isArray(item.children)) return inferScrollX(item.children)
  const width = numericWidth(item.width)
  if (width) return width
  const minWidth = numericWidth(item.minWidth)
  if (minWidth) return minWidth
  return 120
}

function numericWidth(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string') {
    const parsed = Number.parseInt(value, 10)
    if (Number.isFinite(parsed)) return parsed
  }
  return 0
}
</script>

<template>
  <n-card
    :bordered="false"
    class="data-panel"
    role="region"
    :aria-label="title"
    :aria-busy="Boolean(initialLoading || loading)"
  >
    <div class="data-panel__header">
      <div class="data-panel__title">
        <strong>{{ title }}</strong>
        <n-tag v-if="!(error && !data.length)" size="small" :bordered="false" class="data-panel__count">{{ recordCount }} 条</n-tag>
      </div>
      <div class="data-panel__actions">
        <slot name="actions" />
        <n-button v-if="showRefresh && !(error && !data.length)" secondary size="small" :loading="loading" @click="emit('refresh')">
          <template #icon>
            <n-icon :component="RefreshOutline" />
          </template>
          刷新
        </n-button>
      </div>
    </div>

    <n-alert
      v-if="error && data.length"
      type="error"
      :title="errorTitle"
      class="data-panel__stale-error"
      role="alert"
    >
      <div class="data-panel__stale-error-content">
        <span>{{ error }}。以下仍显示上次成功加载的结果。</span>
        <n-button size="small" type="error" secondary :loading="loading" @click="emit('refresh')">重试</n-button>
      </div>
    </n-alert>

    <TableSkeleton v-if="initialLoading || (loading && !data.length)" :rows="5" :columns="Math.min(columns.length, 6)" />
    <n-data-table
      v-else
      :columns="tableColumns"
      :data="tableData"
      :loading="loading"
      :remote="remote"
      :row-key="rowKey"
      :row-props="mergedRowProps"
      :pagination="resolvedPagination"
      :scroll-x="effectiveScrollX"
      :max-height="maxHeight"
      :default-expand-all="defaultExpandAll"
      :size="size"
      :striped="striped"
    >
      <template #empty>
        <slot name="empty">
          <n-result v-if="error" status="error" :title="errorTitle" :description="error" class="data-panel__error" role="alert">
            <template #footer>
              <n-button type="primary" :loading="loading" @click="emit('refresh')">重试</n-button>
            </template>
          </n-result>
          <EmptyState v-else :title="emptyTitle" :description="emptyDescription">
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
  margin-bottom: var(--space-6);
}

.data-panel :deep(.n-card__content) {
  padding: var(--space-4);
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
  font-size: var(--font-size-lg);
  line-height: 22px;
  color: var(--text);
}

.data-panel__count {
  color: var(--brand-hover);
  background: var(--brand-soft);
}

.data-panel__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.data-panel__error {
  min-height: 180px;
  padding: var(--space-6) var(--space-4);
}

.data-panel__stale-error {
  margin-bottom: var(--space-4);
}

.data-panel__stale-error-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.data-panel :deep(.n-data-table__pagination) {
  min-width: 0;
}

@media (max-width: 720px) {
  .data-panel__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .data-panel__actions {
    justify-content: flex-start;
    width: 100%;
  }

  .data-panel__stale-error-content {
    align-items: flex-start;
    flex-direction: column;
  }

  .data-panel :deep(.n-data-table__pagination) {
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: var(--space-1);
  }
}
</style>
