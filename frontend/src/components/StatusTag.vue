<script setup lang="ts">
import { computed } from 'vue'
import { statusLabel, statusTone } from '@/constants/statusLabels'

const props = defineProps<{
  text?: string | null
  value?: string | null
}>()

const rawValue = computed(() => props.value || props.text || '')
const label = computed(() => props.text || statusLabel(props.value) || '-')

const type = computed(() => statusTone(rawValue.value || label.value, label.value))
const tagClass = computed(() => ['status-soft', `status-${type.value}`])
</script>

<template>
  <n-tag :type="type" size="small" :bordered="false" :class="tagClass">{{ label }}</n-tag>
</template>
