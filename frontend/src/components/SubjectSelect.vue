<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { NTag, useMessage, type SelectOption } from 'naive-ui'
import {
  listRecentSubjects,
  listSubjects,
  recordRecentSubject,
  validateSubject,
  type TeachingSubject
} from '@/api/subject'

const props = withDefaults(
  defineProps<{
    value?: string | null
    segmentCode?: string | null
    yearVersion?: string | null
    disabled?: boolean
    placeholder?: string
    clearable?: boolean
  }>(),
  {
    value: null,
    segmentCode: null,
    yearVersion: 'GLOBAL',
    disabled: false,
    placeholder: '请选择任教学科',
    clearable: true
  }
)

const emit = defineEmits<{
  'update:value': [value: string | null]
  change: [subject: TeachingSubject | null]
}>()

interface SubjectOption extends SelectOption {
  label: string
  value: string
  disabled: boolean
  subject: TeachingSubject
}

const message = useMessage()
const loading = ref(false)
const recentLoading = ref(false)
const selectedCode = ref<string | null>(props.value || null)
const keyword = ref('')
const category = ref<string | null>(null)
const subjects = ref<TeachingSubject[]>([])
const baseSubjects = ref<TeachingSubject[]>([])
const recentSubjects = ref<TeachingSubject[]>([])
let mounted = false

const categoryOptions = computed<SelectOption[]>(() =>
  baseSubjects.value
    .filter((item) => item.isCategory === 1)
    .map((item) => ({
      label: `${item.subjectName} ${item.subjectCode}`,
      value: item.categoryNode || item.subjectCode
    }))
)

const subjectOptions = computed<SubjectOption[]>(() =>
  subjects.value.map((item) => ({
    label: `${item.subjectName} ${item.subjectCode}`,
    value: item.subjectCode,
    disabled: !item.selectable,
    subject: item
  }))
)

watch(
  () => props.value,
  (value) => {
    if (value !== selectedCode.value) selectedCode.value = value || null
  }
)

watch(
  () => [props.segmentCode, props.yearVersion],
  async () => {
    keyword.value = ''
    category.value = null
    subjects.value = []
    baseSubjects.value = []
    recentSubjects.value = []
    if (mounted) {
      selectedCode.value = null
      emit('update:value', null)
      emit('change', null)
    }
    await reload()
  }
)

async function reload() {
  if (!props.segmentCode) return
  await Promise.all([loadBaseSubjects(), loadSubjects(), loadRecent()])
}

async function loadBaseSubjects() {
  if (!props.segmentCode) return
  try {
    const res = await listSubjects({
      segment: props.segmentCode,
      yearVersion: props.yearVersion
    })
    baseSubjects.value = res.data
  } catch (error) {
    showError(error, '任教学科分类加载失败')
  }
}

async function loadSubjects() {
  if (!props.segmentCode) return
  loading.value = true
  try {
    const res = await listSubjects({
      segment: props.segmentCode,
      keyword: keyword.value,
      category: category.value,
      yearVersion: props.yearVersion
    })
    subjects.value = res.data
  } catch (error) {
    showError(error, '任教学科加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRecent() {
  if (!props.segmentCode) return
  recentLoading.value = true
  try {
    const res = await listRecentSubjects(props.segmentCode, props.yearVersion)
    recentSubjects.value = res.data
  } catch {
    recentSubjects.value = []
  } finally {
    recentLoading.value = false
  }
}

async function handleUpdate(value: string | number | null) {
  const code = typeof value === 'string' ? value : null
  if (!code) {
    selectedCode.value = null
    emit('update:value', null)
    emit('change', null)
    return
  }
  await selectSubject(code)
}

async function selectSubject(code: string) {
  if (!props.segmentCode) {
    message.warning('请先选择任教学段')
    return
  }
  const subject = [...subjects.value, ...baseSubjects.value, ...recentSubjects.value].find((item) => item.subjectCode === code)
  if (subject && !subject.selectable) {
    message.error('任教学科类别节点不可选择')
    return
  }
  try {
    await validateSubject({
      segmentCode: props.segmentCode,
      subjectCode: code,
      yearVersion: props.yearVersion
    })
    await recordRecentSubject({
      segmentCode: props.segmentCode,
      subjectCode: code,
      yearVersion: props.yearVersion
    })
    selectedCode.value = code
    emit('update:value', code)
    emit('change', subject || null)
    await loadRecent()
  } catch (error) {
    selectedCode.value = null
    emit('update:value', null)
    emit('change', null)
    showError(error, '任教学科校验失败')
  }
}

function renderLabel(option: SelectOption) {
  const subject = (option as SubjectOption).subject
  if (!subject) return String(option.label || '')
  return h('div', { class: 'subject-option' }, [
    h('span', { class: 'subject-option__name' }, subject.subjectName),
    h('span', { class: 'subject-option__code' }, subject.subjectCode),
    subject.selectable
      ? null
      : h(NTag, { size: 'small', type: 'warning', bordered: false }, { default: () => '类别' })
  ])
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await reload()
  mounted = true
})
</script>

<template>
  <n-space vertical :size="8" class="subject-select">
    <n-select
      v-model:value="category"
      :options="categoryOptions"
      clearable
      :disabled="disabled || !segmentCode"
      placeholder="分类"
      @update:value="loadSubjects"
    />
    <n-select
      :value="selectedCode"
      :options="subjectOptions"
      :loading="loading"
      :disabled="disabled || !segmentCode"
      :placeholder="placeholder"
      :clearable="clearable"
      filterable
      remote
      :render-label="renderLabel"
      @search="(value: string) => { keyword = value; loadSubjects() }"
      @update:value="handleUpdate"
    />
    <n-spin :show="recentLoading">
      <n-space v-if="recentSubjects.length" :size="6" class="recent-subjects">
        <n-tag
          v-for="item in recentSubjects"
          :key="item.subjectCode"
          checkable
          :checked="selectedCode === item.subjectCode"
          @click="selectSubject(item.subjectCode)"
        >
          {{ item.subjectName }}
        </n-tag>
      </n-space>
    </n-spin>
  </n-space>
</template>

<style scoped>
.subject-select {
  width: 100%;
}

.subject-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
}

.subject-option__name,
.subject-option__code {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subject-option__code {
  color: var(--text-secondary);
  font-size: 12px;
}

.recent-subjects {
  min-height: 28px;
}
</style>
