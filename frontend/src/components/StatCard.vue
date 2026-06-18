<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  sub?: string | null
  color?: string | null
}>()
</script>

<template>
  <n-card :bordered="false" size="small" class="stat-card">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value" :style="{ color: color || '#111827' }">{{ value }}</div>
    <div v-if="sub" class="stat-sub">{{ sub }}</div>
  </n-card>
</template>

<style scoped>
.stat-card {
  border-radius: 8px;
  min-height: 108px;
}

.stat-label {
  font-size: 13px;
  color: var(--text-muted);
}

.stat-value {
  margin-top: 6px;
  font-size: 28px;
  line-height: 1.25;
  font-weight: 650;
  letter-spacing: 0;
}

.stat-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
