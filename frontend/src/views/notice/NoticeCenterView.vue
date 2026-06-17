<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { listNotices, markAllNoticesRead, markNoticeRead, type NotificationItem } from '@/api/notice'

type ReadFilter = 'all' | 'unread' | 'read'

const message = useMessage()
const loading = ref(false)
const notices = ref<NotificationItem[]>([])
const filter = ref<ReadFilter>('all')

const unreadCount = computed(() => notices.value.filter((item) => item.readFlag === 0).length)
const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '未读', value: 'unread' },
  { label: '已读', value: 'read' }
]

const columns: DataTableColumns<NotificationItem> = [
  {
    title: '状态',
    key: 'readFlag',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { type: row.readFlag === 0 ? 'warning' : 'default', bordered: false, size: 'small' },
        { default: () => (row.readFlag === 0 ? '未读' : '已读') }
      )
  },
  { title: '标题', key: 'title', minWidth: 180 },
  { title: '内容', key: 'content', minWidth: 300 },
  { title: '业务', key: 'bizType', minWidth: 140 },
  { title: '时间', key: 'createdAt', minWidth: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 110,
    render: (row) =>
      row.readFlag === 0
        ? h(
            NButton,
            {
              size: 'small',
              quaternary: true,
              type: 'primary',
              onClick: () => handleRead(row)
            },
            { default: () => '标记已读' }
          )
        : null
  }
]

onMounted(loadNotices)

async function loadNotices() {
  loading.value = true
  try {
    const read = filter.value === 'all' ? null : filter.value === 'read'
    const res = await listNotices(read)
    notices.value = res.data.records || []
  } catch (error) {
    message.error(error instanceof Error ? error.message : '通知加载失败')
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
    message.error(error instanceof Error ? error.message : '操作失败')
  }
}

async function handleReadAll() {
  try {
    await markAllNoticesRead()
    notices.value = notices.value.map((item) => ({ ...item, readFlag: 1 }))
    message.success('已全部标记为已读')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '操作失败')
  }
}
</script>

<template>
  <n-space vertical :size="16">
    <n-space justify="space-between" align="center">
      <n-space align="center">
        <n-segmented v-model:value="filter" :options="filterOptions" @update:value="loadNotices" />
        <n-tag size="small" type="info" bordered>未读 {{ unreadCount }}</n-tag>
      </n-space>
      <n-space>
        <n-button :loading="loading" @click="loadNotices">刷新</n-button>
        <n-button type="primary" :disabled="unreadCount === 0" @click="handleReadAll">全部已读</n-button>
      </n-space>
    </n-space>
    <n-data-table :loading="loading" :columns="columns" :data="notices" :pagination="{ pageSize: 12 }" />
  </n-space>
</template>
