<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  label: string
  value: string | number
  unit?: string | null
  sub?: string | null
  icon?: Component
  tone?: 'default' | 'brand' | 'success' | 'warning' | 'error' | 'info' | 'neutral'
}>()
</script>

<template>
  <n-card :bordered="false" size="small" class="stat-card" :class="`stat-card--${tone || 'brand'}`">
    <div class="stat-head">
      <div class="stat-icon">
        <slot name="icon">
          <n-icon v-if="icon" :component="icon" />
        </slot>
      </div>
      <div class="stat-label">{{ label }}</div>
    </div>
    <div class="stat-value tabular-nums">
      <span>{{ value }}</span>
      <span v-if="unit" class="stat-unit">{{ unit }}</span>
    </div>
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
  --stat-tone-soft: var(--brand-soft);
}

.stat-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.stat-card--success {
  --stat-tone: var(--success);
  --stat-tone-soft: var(--success-soft);
}

.stat-card--success .stat-value {
  color: var(--success);
}

.stat-card--warning {
  --stat-tone: var(--warning);
  --stat-tone-soft: var(--warning-soft);
}

.stat-card--warning .stat-value {
  color: var(--warning);
}

.stat-card--error {
  --stat-tone: var(--error);
  --stat-tone-soft: var(--error-soft);
}

.stat-card--error .stat-value {
  color: var(--error);
}

.stat-card--info,
.stat-card--brand {
  --stat-tone: var(--brand);
  --stat-tone-soft: var(--brand-soft);
}

.stat-card--info .stat-value,
.stat-card--brand .stat-value {
  color: var(--brand);
}

.stat-card--neutral {
  --stat-tone: var(--text-secondary);
  --stat-tone-soft: var(--surface-muted);
}

.stat-card--neutral .stat-value {
  color: var(--text-secondary);
}

.stat-card--default {
  --stat-tone: var(--text);
  --stat-tone-soft: var(--surface-muted);
}

.stat-card--default .stat-value {
  color: var(--text);
}

.stat-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.stat-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--stat-tone-soft);
  color: var(--stat-tone);
  font-size: 19px;
}

.stat-icon:empty {
  display: none;
}

.stat-label {
  font-size: var(--font-size-sm);
  line-height: var(--line-height-sm);
  color: var(--text-secondary);
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: var(--space-3);
  font-size: 30px;
  line-height: 38px;
  font-weight: 650;
  letter-spacing: 0;
  color: var(--brand);
}

.stat-unit {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: var(--line-height-sm);
}

.stat-sub {
  margin-top: var(--space-2);
  font-size: var(--font-size-xs);
  line-height: 18px;
  color: var(--text-muted);
}
</style>
