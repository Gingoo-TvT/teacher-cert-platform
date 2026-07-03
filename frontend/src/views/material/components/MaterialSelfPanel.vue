<script setup lang="ts">
import { computed, type Component } from 'vue'
import { AlertCircleOutline, CheckmarkCircleOutline, CloudUploadOutline, DocumentTextOutline } from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import { statusLabel } from '@/constants/statusLabels'
import { formatFileSize } from '@/utils/format'
import type { DictItem } from '@/api/dict'
import type { ProcessMaterial } from '@/api/material'

interface MaterialCard {
  code: string
  label: string
  icon: Component
  record: ProcessMaterial | null
}

const props = defineProps<{
  records: ProcessMaterial[]
  categories: DictItem[]
  loading: boolean
  assessmentYear: string
}>()

const emit = defineEmits<{
  refresh: []
  status: []
  preview: [record: ProcessMaterial]
  upload: [code: string]
  replace: [record: ProcessMaterial]
  submit: [record: ProcessMaterial]
  'update:assessmentYear': [value: string]
}>()

const assessmentYearModel = computed({
  get: () => props.assessmentYear,
  set: (value: string) => emit('update:assessmentYear', value)
})

const materialCards = computed<MaterialCard[]>(() => {
  const icons = [DocumentTextOutline, CloudUploadOutline, CheckmarkCircleOutline, AlertCircleOutline]
  const source = props.categories.length
    ? props.categories
    : props.records.map((item) => ({ itemCode: item.category, itemValue: item.categoryLabel }) as DictItem)
  return source.slice(0, 4).map((item, index) => ({
    code: item.itemCode,
    label: item.itemValue,
    icon: icons[index] || DocumentTextOutline,
    record: latestMaterial(item.itemCode)
  }))
})

function latestMaterial(category: string) {
  return props.records
    .filter((item) => item.category === category)
    .sort((a, b) => String(b.uploadTime || '').localeCompare(String(a.uploadTime || '')))[0] || null
}

function rejectComment(row?: ProcessMaterial | null) {
  if (!row) return ''
  if (!row.status.includes('REJECTED') && row.status !== 'FAILED') return ''
  return row.secondReviewComment || row.firstReviewComment || ''
}
</script>

<template>
  <n-card :bordered="false" class="page-section material-self-toolbar">
    <n-space justify="space-between" align="center">
      <n-space align="center">
        <span class="toolbar-label">考核年度</span>
        <n-input v-model:value="assessmentYearModel" placeholder="考核年度" class="mono-input" style="width: 130px" />
        <n-button secondary :loading="loading" @click="emit('refresh')">刷新</n-button>
      </n-space>
      <n-button @click="emit('status')">合格判定</n-button>
    </n-space>
  </n-card>

  <n-grid :cols="4" :x-gap="12" :y-gap="12" responsive="screen" class="page-section material-card-grid">
    <n-gi v-for="card in materialCards" :key="card.code">
      <n-card :bordered="false" class="material-card">
        <div class="material-card__head">
          <div class="material-card__icon">
            <n-icon :component="card.icon" />
          </div>
          <div class="material-card__title">
            <strong>{{ card.label }}</strong>
            <StatusTag
              :value="card.record?.status || 'WAIT_UPLOAD'"
              :text="card.record?.statusLabel || statusLabel(card.record?.status || 'WAIT_UPLOAD')"
            />
          </div>
        </div>
        <div class="material-card__file">
          <span>{{ card.record?.fileName || '尚未上传材料' }}</span>
          <small>{{ formatFileSize(card.record?.fileSize) }}</small>
        </div>
        <n-alert v-if="rejectComment(card.record)" type="warning" :bordered="false">
          退回意见：{{ rejectComment(card.record) }}
        </n-alert>
        <n-space class="material-card__actions">
          <n-button v-if="card.record" secondary size="small" @click="emit('preview', card.record)">预览</n-button>
          <n-button size="small" @click="card.record ? emit('replace', card.record) : emit('upload', card.code)">
            {{ card.record ? '替换' : '上传' }}
          </n-button>
          <n-button v-if="card.record" type="primary" size="small" @click="emit('submit', card.record)">提交</n-button>
        </n-space>
      </n-card>
    </n-gi>
  </n-grid>
</template>

<style scoped>
.mono-input :deep(input) {
  font-family: var(--font-mono);
}

.material-self-toolbar :deep(.n-card__content) {
  padding: var(--space-4);
}

.toolbar-label {
  color: var(--text-secondary);
  font-size: 13px;
}

.material-card {
  min-height: 236px;
}

.material-card :deep(.n-card__content) {
  display: flex;
  min-height: 236px;
  flex-direction: column;
  gap: var(--space-3);
}

.material-card__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.material-card__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 22px;
}

.material-card__title {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.material-card__title strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-card__file {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-3);
  border-radius: var(--radius-control);
  background: var(--surface-muted);
}

.material-card__file span,
.material-card__file small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-card__file small {
  color: var(--text-muted);
}

.material-card__actions {
  margin-top: auto;
}
</style>
