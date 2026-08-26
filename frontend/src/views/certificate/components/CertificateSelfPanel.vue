<script setup lang="ts">
import { CalendarOutline, RibbonOutline, SchoolOutline } from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import { statusLabel } from '@/constants/statusLabels'
import { formatDate } from '@/utils/format'
import type { Certificate } from '@/api/certificate'

defineProps<{
  records: Certificate[]
  loading: boolean
  loadError: string
  hasLoadedSuccessfully: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

function certificateSubject(row: Certificate) {
  return row.teachingSubjectName || row.teachingSubjectCode || '-'
}
</script>

<template>
  <n-spin :show="loading">
    <n-card v-if="loading && !hasLoadedSuccessfully" :bordered="false" class="page-section certificate-loading-card">
      <n-skeleton text :repeat="4" />
    </n-card>
    <n-result
      v-else-if="loadError && !hasLoadedSuccessfully"
      status="error"
      title="证书加载失败"
      :description="loadError"
      class="page-section"
      role="alert"
    >
      <template #footer>
        <n-button type="primary" :loading="loading" @click="emit('refresh')">重试</n-button>
      </template>
    </n-result>
    <n-alert
      v-else-if="loadError"
      type="error"
      title="证书刷新失败"
      :bordered="false"
      class="page-section"
      role="alert"
    >
      {{ loadError }}。以下仍显示上次成功加载的证书。
      <n-button text type="error" size="small" :loading="loading" @click="emit('refresh')">重试</n-button>
    </n-alert>
    <n-empty v-if="hasLoadedSuccessfully && !records.length" description="暂无证书记录" class="page-section" />
    <n-grid v-else-if="hasLoadedSuccessfully && records.length" cols="1 440:2" :x-gap="12" :y-gap="12" responsive="self" class="page-section certificate-card-grid">
      <n-gi v-for="item in records" :key="item.id">
        <n-card :bordered="false" class="certificate-card">
          <div class="certificate-card__top">
            <div class="certificate-card__icon">
              <n-icon :component="RibbonOutline" />
            </div>
            <StatusTag :value="item.status" :text="item.statusLabel || statusLabel(item.status)" />
          </div>
          <div class="certificate-card__number mono tabular-nums">{{ item.certNo || '证书编号待生成' }}</div>
          <div class="certificate-card__meta">
            <div>
              <n-icon :component="CalendarOutline" />
              <span>有效期至</span>
              <strong class="mono tabular-nums">{{ formatDate(item.validUntil) }}</strong>
            </div>
            <div>
              <n-icon :component="SchoolOutline" />
              <span>任教学科</span>
              <strong>{{ certificateSubject(item) }}</strong>
            </div>
          </div>
          <div class="certificate-card__footer">
            <span>{{ item.studentNo || '-' }} / {{ item.studentName || '-' }}</span>
            <span>签发日期：<span class="mono tabular-nums">{{ formatDate(item.issueDate) }}</span></span>
          </div>
        </n-card>
      </n-gi>
    </n-grid>
  </n-spin>
</template>

<style scoped>
.certificate-card {
  position: relative;
  overflow: hidden;
  min-height: 260px;
  border: 1px solid var(--brand-border);
  background:
    linear-gradient(135deg, var(--brand-soft), var(--surface) 46%),
    var(--surface);
}

.certificate-loading-card {
  min-height: 180px;
}

.certificate-card :deep(.n-card__content) {
  display: flex;
  min-height: 260px;
  flex-direction: column;
  gap: var(--space-4);
}

.certificate-card__top,
.certificate-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.certificate-card__icon {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 999px;
  background: var(--brand);
  color: var(--text-inverse);
  font-size: 24px;
}

.certificate-card__number {
  color: var(--brand-pressed);
  font-size: 26px;
  font-weight: 650;
  line-height: 34px;
  word-break: break-all;
}

.certificate-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.certificate-card__meta div {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 4px var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-control);
  background: var(--surface);
}

.certificate-card__meta .n-icon {
  grid-row: span 2;
  color: var(--brand);
  font-size: 18px;
}

.certificate-card__meta span,
.certificate-card__footer {
  color: var(--text-muted);
  font-size: 12px;
}

.certificate-card__meta strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .certificate-card__meta {
    grid-template-columns: 1fr;
  }

  .certificate-card__footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
