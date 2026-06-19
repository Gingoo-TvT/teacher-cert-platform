<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import { NBadge, NButton, NSpace, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { listNotices, markAllNoticesRead, markNoticeRead, type NotificationItem } from '@/api/notice'

type ReadFilter = 'all' | 'unread' | 'read'

const message = useMessage()
const loading = ref(false)
const notices = ref<NotificationItem[]>([])
const readFilter = ref<ReadFilter>('all')
const typeFilter = ref<string | null>(null)
let noticeTimer: number | undefined

const unreadCount = computed(() => notices.value.filter((item) => item.readFlag === 0).length)
const readCount = computed(() => notices.value.filter((item) => item.readFlag === 1).length)
const typeOptions = computed<SelectOption[]>(() => {
  const types = [...new Set(notices.value.map((item) => item.type).filter(Boolean))]
  return types.map((type) => ({ label: typeName(type), value: type }))
})
const filteredNotices = computed(() => {
  if (!typeFilter.value) return notices.value
  return notices.value.filter((item) => item.type === typeFilter.value)
})

const filterOptions: SelectOption[] = [
  { label: '全部', value: 'all' },
  { label: '未读', value: 'unread' },
  { label: '已读', value: 'read' }
]

const columns: DataTableColumns<NotificationItem> = [
  {
    title: '',
    key: 'unread',
    width: 42,
    render: (row) => row.readFlag === 0 ? h('span', { class: 'notice-dot' }) : null
  },
  { title: '状态', key: 'readFlag', width: 92, render: (row) => h(StatusTag, { text: row.readFlag === 0 ? '未读' : '已读' }) },
  { title: '类型', key: 'type', width: 130, render: (row) => h(StatusTag, { text: typeName(row.type) }) },
  {
    title: '标题',
    key: 'title',
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (row) => row.readFlag === 0 ? h(NBadge, { dot: true }, { default: () => h('strong', row.title) }) : row.title
  },
  { title: '内容', key: 'content', minWidth: 320, ellipsis: { tooltip: true }, render: (row) => row.content || '-' },
  { title: '业务', key: 'bizType', width: 130, ellipsis: { tooltip: true }, render: (row) => row.bizType || '-' },
  { title: '时间', key: 'createdAt', width: 170, render: (row) => row.createdAt || '-' },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 112,
    render: (row) =>
      row.readFlag === 0
        ? h(NButton, { size: 'small', quaternary: true, onClick: () => handleRead(row) }, { default: () => '标记已读' })
        : null
  }
]

onMounted(() => {
  loadNotices()
  noticeTimer = window.setInterval(loadNotices, 60000)
})

onBeforeUnmount(() => {
  if (noticeTimer) window.clearInterval(noticeTimer)
})

async function loadNotices() {
  loading.value = true
  try {
    const read = readFilter.value === 'all' ? null : readFilter.value === 'read'
    const res = await listNotices(read)
    notices.value = res.data.records || []
  } catch (error) {
    showError(error, '通知加载失败')
  } finally {
    loading.value = false
  }
}

async function handleRead(row: NotificationItem) {
  try {
    await markNoticeRead(row.id)
    row.readFlag = 1
    message.success('已标记为已读')
  } catch (error) {
    showError(error, '操作失败')
  }
}

async function handleReadAll() {
  try {
    await markAllNoticesRead()
    notices.value = notices.value.map((item) => ({ ...item, readFlag: 1 }))
    message.success('已全部标记为已读')
  } catch (error) {
    showError(error, '操作失败')
  }
}

async function onReadFilterChange() {
  typeFilter.value = null
  await loadNotices()
}

function typeName(type?: string | null) {
  const map: Record<string, string> = {
    SUBMIT: '提交提醒',
    RETURN: '退回提醒',
    VIDEO_ASSIGN: '视频评审',
    EXPORT_DONE: '导出完成',
    SYSTEM: '系统通知'
  }
  return type ? map[type] || type : '-'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}
</script>

<template>
  <PageContainer title="通知中心" description="本人站内通知；未读在菜单与角标提示。">
    <template #actions>
      <n-space>
        <n-button secondary :loading="loading" @click="loadNotices">刷新</n-button>
        <n-button type="primary" :disabled="unreadCount === 0" @click="handleReadAll">全部已读</n-button>
      </n-space>
    </template>

    <n-grid :cols="3" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="通知总数" :value="notices.length" /></n-gi>
      <n-gi><StatCard label="未读" :value="unreadCount" tone="error" /></n-gi>
      <n-gi><StatCard label="已读" :value="readCount" tone="success" /></n-gi>
    </n-grid>

    <n-card :bordered="false" size="small" class="page-section">
      <n-space class="filters" :size="10">
        <n-segmented v-model:value="readFilter" :options="filterOptions" @update:value="onReadFilterChange" />
        <n-select v-model:value="typeFilter" clearable :options="typeOptions" placeholder="通知类型" style="width: 170px" />
      </n-space>
    </n-card>

    <n-data-table
      :loading="loading"
      :columns="columns"
      :data="filteredNotices"
      :row-key="(row: NotificationItem) => row.id"
      :scroll-x="1180"
      :pagination="{ pageSize: 12 }"
      striped
    />
  </PageContainer>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}

.notice-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--error);
  box-shadow: 0 0 0 3px var(--error-soft);
}
</style>
