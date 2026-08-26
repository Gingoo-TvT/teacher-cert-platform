<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SelectOption } from 'naive-ui'
import { listStudents, type Student } from '@/api/student'

const props = withDefaults(defineProps<{
  value?: string | null
  disabled?: boolean
  placeholder?: string
  clearable?: boolean
  selectedLabel?: string | null
}>(), {
  placeholder: '输入学号或姓名搜索',
  clearable: true
})

const emit = defineEmits<{
  'update:value': [value: string | null]
  select: [student: Student | null]
}>()

const loading = ref(false)
const remoteOptions = ref<SelectOption[]>([])
const studentMap = ref(new Map<string, Student>())
let searchTimer: ReturnType<typeof window.setTimeout> | null = null

const currentOption = computed<SelectOption[]>(() => {
  if (!props.value || !props.selectedLabel) return []
  return [{ label: props.selectedLabel, value: props.value }]
})

const options = computed<SelectOption[]>(() => {
  const map = new Map<string | number, SelectOption>()
  for (const item of currentOption.value) map.set(item.value as string | number, item)
  for (const item of remoteOptions.value) map.set(item.value as string | number, item)
  return Array.from(map.values())
})

watch(
  () => props.value,
  (value) => {
    if (!value) remoteOptions.value = []
  }
)

function handleSearch(query: string) {
  if (searchTimer) window.clearTimeout(searchTimer)
  const keyword = query.trim()
  if (!keyword) {
    remoteOptions.value = []
    return
  }
  searchTimer = window.setTimeout(() => {
    void searchStudents(keyword)
  }, 260)
}

async function searchStudents(keyword: string) {
  loading.value = true
  try {
    const res = await listStudents({ keyword })
    const records = res.data.records.slice(0, 20)
    studentMap.value = new Map(records.map((item) => [item.id, item]))
    remoteOptions.value = records.map((item) => ({
      label: `${item.studentNo} ${item.name}`,
      value: item.id
    }))
  } finally {
    loading.value = false
  }
}

function handleUpdate(next: string | number | null) {
  const value = typeof next === 'string' ? next : null
  emit('update:value', value)
  emit('select', value ? studentMap.value.get(value) || null : null)
}
</script>

<template>
  <n-select
    :value="value"
    :options="options"
    :loading="loading"
    :disabled="disabled"
    :placeholder="placeholder"
    :clearable="clearable"
    filterable
    remote
    @search="handleSearch"
    @update:value="handleUpdate"
  />
</template>
