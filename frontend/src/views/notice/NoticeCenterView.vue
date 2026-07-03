<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useMessage, type SelectOption } from 'naive-ui'
import EmptyState from '@/components/EmptyState.vue'
import FilterBar from '@/components/FilterBar.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { listNotices, markAllNoticesRead, markNoticeRead, type NotificationItem } from '@/api/notice'
import { formatDateTime } from '@/utils/format'
import { useNoticeStore } from '@/stores/notice'

type ReadFilter = 'all' | 'unread' | 'read'

const message = useMessage()
const noticeStore = useNoticeStore()
const loading = ref(false)
const notices = ref<NotificationItem[]>([])
const readFilter = ref<ReadFilter>('all')
const typeFilter = ref<string | null>(null)
const selectedNotice = ref<NotificationItem | null>(null)
const drawerVisible = ref(false)
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

async function openNotice(row: NotificationItem) {
  selectedNotice.value = row
  drawerVisible.value = true
  if (row.readFlag !== 0) return
  try {
    await markNoticeRead(row.id)
    row.readFlag = 1
    void noticeStore.refresh() // 同步外壳角标/菜单圆点
  } catch (error) {
    showError(error, '操作失败')
  }
}

async function handleReadAll() {
  try {
    await markAllNoticesRead()
    notices.value = notices.value.map((item) => ({ ...item, readFlag: 1 }))
    noticeStore.reset() // 立即清零外壳角标/菜单圆点
    message.success('已全部标记为已读')
  } catch (error) {
    showError(error, '操作失败')
  }
}

async function onReadFilterChange() {
  typeFilter.value = null
  await loadNotices()
}

function resetFilters() {
  readFilter.value = 'all'
  typeFilter.value = null
  void loadNotices()
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

    <FilterBar :loading="loading" submit-text="刷新" @submit="loadNotices" @reset="resetFilters">
      <label class="filter-field">
        <span>阅读状态</span>
        <n-segmented v-model:value="readFilter" :options="filterOptions" @update:value="onReadFilterChange" />
      </label>
      <label class="filter-field">
        <span>通知类型</span>
        <n-select v-model:value="typeFilter" clearable :options="typeOptions" placeholder="通知类型" style="width: 170px" />
      </label>
    </FilterBar>

    <n-spin :show="loading">
      <n-list v-if="filteredNotices.length" bordered class="notice-list">
        <n-list-item
          v-for="item in filteredNotices"
          :key="item.id"
          class="notice-row"
          :class="{ 'notice-row--unread': item.readFlag === 0 }"
          @click="openNotice(item)"
        >
          <div class="notice-row__dot">
            <span v-if="item.readFlag === 0" class="notice-dot" />
          </div>
          <div class="notice-row__main">
            <div class="notice-row__title-line">
              <strong>{{ item.title }}</strong>
              <StatusTag :text="typeName(item.type)" />
              <StatusTag :text="item.readFlag === 0 ? '未读' : '已读'" />
            </div>
            <div class="notice-row__content">{{ item.content || '-' }}</div>
            <div class="notice-row__meta">
              <span>{{ item.bizType || '站内通知' }}</span>
              <span class="mono tabular-nums">{{ formatDateTime(item.createdAt) }}</span>
            </div>
          </div>
        </n-list-item>
      </n-list>
      <EmptyState v-else title="暂无通知" description="当前筛选条件下没有通知。" />
    </n-spin>

    <n-drawer v-model:show="drawerVisible" :width="560">
      <n-drawer-content :title="selectedNotice?.title || '通知详情'" closable>
        <n-space v-if="selectedNotice" vertical :size="16">
          <n-space>
            <StatusTag :text="typeName(selectedNotice.type)" />
            <StatusTag :text="selectedNotice.readFlag === 0 ? '未读' : '已读'" />
            <span class="notice-time mono tabular-nums">{{ formatDateTime(selectedNotice.createdAt) }}</span>
          </n-space>
          <div class="notice-detail-content">{{ selectedNotice.content || '-' }}</div>
          <div class="notice-detail-meta">
            <span>业务类型</span>
            <strong>{{ selectedNotice.bizType || '-' }}</strong>
          </div>
        </n-space>
      </n-drawer-content>
    </n-drawer>
  </PageContainer>
</template>

<style scoped>
.notice-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--error);
  box-shadow: 0 0 0 3px var(--error-soft);
}

.notice-list {
  margin-bottom: var(--space-7);
  background: var(--surface);
}

.notice-row {
  cursor: pointer;
  transition: background 0.18s ease;
}

.notice-row:hover {
  background: var(--surface-muted);
}

.notice-row :deep(.n-list-item__main) {
  display: flex;
  min-width: 0;
  gap: var(--space-3);
}

.notice-row__dot {
  flex: 0 0 14px;
  padding-top: 8px;
}

.notice-row__main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 6px;
}

.notice-row__title-line {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.notice-row__title-line strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-row--unread .notice-row__title-line strong {
  font-weight: 700;
}

.notice-row__content {
  min-width: 0;
  overflow: hidden;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-row__meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-size: 12px;
  flex-wrap: wrap;
}

.notice-time {
  color: var(--text-muted);
}

.notice-detail-content {
  white-space: pre-wrap;
  line-height: 1.7;
  color: var(--text);
}

.notice-detail-meta {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: 1px solid var(--shell-border);
}

.notice-detail-meta span {
  color: var(--text-secondary);
}

.notice-detail-meta strong {
  font-weight: 500;
}
</style>
