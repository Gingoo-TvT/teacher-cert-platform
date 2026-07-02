<script setup lang="ts">
import { computed, ref, useSlots } from 'vue'
import { FunnelOutline } from '@vicons/ionicons5'

withDefaults(defineProps<{
  loading?: boolean
  submitText?: string
  resetText?: string
}>(), {
  submitText: '查询',
  resetText: '重置'
})

const emit = defineEmits<{
  submit: []
  reset: []
}>()

const slots = useSlots()
const expanded = ref(false)
const hasMore = computed(() => Boolean(slots.more))
</script>

<template>
  <n-card :bordered="false" size="small" class="filter-card">
    <div class="filter-bar">
      <div class="filter-bar__main">
        <slot />
        <template v-if="hasMore && expanded">
          <slot name="more" />
        </template>
      </div>
      <div class="filter-bar__actions">
        <n-button v-if="hasMore" secondary @click="expanded = !expanded">
          <template #icon>
            <n-icon :component="FunnelOutline" />
          </template>
          {{ expanded ? '收起筛选' : '更多筛选' }}
        </n-button>
        <n-button :loading="loading" type="primary" @click="emit('submit')">{{ submitText }}</n-button>
        <n-button @click="emit('reset')">{{ resetText }}</n-button>
      </div>
    </div>
  </n-card>
</template>

<style scoped>
.filter-card {
  margin-bottom: var(--space-5);
}

.filter-card :deep(.n-card__content) {
  padding: var(--space-4);
}

.filter-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.filter-bar__main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 1;
  flex-wrap: wrap;
  min-width: 0;
}

.filter-bar__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  flex: 0 0 auto;
}

:deep(.filter-field) {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 34px;
}

:deep(.filter-field > span:first-child) {
  flex: 0 0 auto;
  color: var(--text-secondary);
  font-size: 13px;
  white-space: nowrap;
}

:deep(.filter-field .n-input),
:deep(.filter-field .n-select) {
  min-height: 34px;
}

@media (max-width: 860px) {
  .filter-bar {
    flex-direction: column;
  }

  .filter-bar__main,
  .filter-bar__actions {
    width: 100%;
  }

  .filter-bar__actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  :deep(.filter-field) {
    width: 100%;
    align-items: flex-start;
    flex-direction: column;
  }

  :deep(.filter-field .n-input),
  :deep(.filter-field .n-select) {
    width: 100% !important;
  }
}
</style>
