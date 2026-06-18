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
    <div class="stat-label">
      <span class="stat-dot" />
      <span>{{ label }}</span>
    </div>
    <div class="stat-value">{{ value }}</div>
    <div v-if="sub" class="stat-sub">{{ sub }}</div>
  </n-card>
</template>

<style scoped>
.stat-card {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-card);
  min-height: 124px;
  box-shadow: var(--shadow-card);
  transition: box-shadow 180ms ease, transform 180ms ease;
  --stat-tone: var(--brand);
}

.stat-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.stat-card--success {
  --stat-tone: var(--success);
}

.stat-card--success .stat-value {
  color: var(--success);
}

.stat-card--warning {
  --stat-tone: var(--warning);
}

.stat-card--warning .stat-value {
  color: var(--warning);
}

.stat-card--error {
  --stat-tone: var(--error);
}

.stat-card--error .stat-value {
  color: var(--error);
}

.stat-card--info,
.stat-card--brand {
  --stat-tone: var(--brand);
}

.stat-card--info .stat-value,
.stat-card--brand .stat-value {
  color: var(--brand);
}

.stat-card--neutral {
  --stat-tone: var(--text-secondary);
}

.stat-card--neutral .stat-value {
  color: var(--text-secondary);
}

.stat-card--default {
  --stat-tone: var(--text);
}

.stat-card--default .stat-value {
  color: var(--text);
}

.stat-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 13px;
  line-height: 20px;
  color: var(--text-secondary);
}

.stat-dot {
  width: 10px;
  height: 10px;
  flex: 0 0 10px;
  border-radius: 999px;
  background: var(--stat-tone);
  box-shadow: 0 0 0 4px var(--brand-soft);
}

.stat-value {
  margin-top: var(--space-3);
  font-size: 32px;
  line-height: 38px;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--brand);
}

.stat-sub {
  margin-top: var(--space-2);
  font-size: 12px;
  line-height: 18px;
  color: var(--text-muted);
}
</style>
