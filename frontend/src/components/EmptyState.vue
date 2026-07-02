<script setup lang="ts">
import { DocumentTextOutline } from '@vicons/ionicons5'

withDefaults(defineProps<{
  title?: string
  description?: string
}>(), {
  title: '暂无数据',
  description: '当前筛选条件下没有可展示的记录。'
})
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon">
      <slot name="icon">
        <n-icon :component="DocumentTextOutline" />
      </slot>
    </div>
    <div class="empty-state__title">{{ title }}</div>
    <div v-if="description" class="empty-state__description">{{ description }}</div>
    <div v-if="$slots.action" class="empty-state__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  padding: var(--space-10) var(--space-4);
  text-align: center;
  color: var(--text-secondary);
}

.empty-state__icon {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 26px;
}

.empty-state__title {
  margin-top: var(--space-4);
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.empty-state__description {
  max-width: 360px;
  margin-top: var(--space-2);
  font-size: 13px;
  line-height: 20px;
  color: var(--text-muted);
}

.empty-state__action {
  margin-top: var(--space-5);
}
</style>
