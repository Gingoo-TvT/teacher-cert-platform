<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  sub?: string | null
  tone?: 'default' | 'brand' | 'success' | 'warning' | 'error' | 'info' | 'neutral'
}>()
</script>

<template>
  <n-card :bordered="false" size="small" class="stat-card" :class="`stat-card--${tone || 'brand'}`">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value">{{ value }}</div>
    <div v-if="sub" class="stat-sub">{{ sub }}</div>
  </n-card>
</template>

<style scoped>
.stat-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-card);
  min-height: 108px;
  box-shadow: var(--shadow-card);
}

.stat-card::before {
  position: absolute;
  left: 0;
  top: 16px;
  bottom: 16px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--brand);
  content: "";
}

.stat-card--success::before {
  background: var(--success);
}

.stat-card--success .stat-value {
  color: var(--success);
}

.stat-card--warning::before {
  background: var(--warning);
}

.stat-card--warning .stat-value {
  color: var(--warning);
}

.stat-card--error::before {
  background: var(--error);
}

.stat-card--error .stat-value {
  color: var(--error);
}

.stat-card--info::before,
.stat-card--brand::before {
  background: var(--brand);
}

.stat-card--info .stat-value,
.stat-card--brand .stat-value {
  color: var(--brand);
}

.stat-card--neutral::before {
  background: var(--text-secondary);
}

.stat-card--neutral .stat-value {
  color: var(--text-secondary);
}

.stat-card--default::before {
  background: var(--text);
}

.stat-card--default .stat-value {
  color: var(--text);
}

.stat-label {
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
}

.stat-value {
  margin-top: 6px;
  font-size: 29px;
  line-height: 34px;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--brand);
}

.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-muted);
}
</style>
