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
const loadError = ref('')
const hasLoaded = ref(false)
const loadedQueryKey = ref('')
const notices = ref<NotificationItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const readFilter = ref<ReadFilter>('all')
const typeFilter = ref<string | null>(null)
const selectedNotice = ref<NotificationItem | null>(null)
const drawerVisible = ref(false)
let noticeTimer: number | undefined
let listRequestSequence = 0

// Phase 44e-contract（P1-1 真分页 rollout · NoticeCenterView 前端特例，见 docs/pagination-rollout-spec.md §2）：
// 真分页后 notices 只是当页数据，「未读」不能再靠本地 filter 当页统计（会漏掉其它页），改读
// noticeStore.unreadCount（全局真实未读数，来自 /notice/unread-count，标记已读/全部已读时已同步刷新）；
// 「已读」按当前 read 筛选精确推：筛未读时子集全未读→0，筛已读时子集全已读→total，筛全部时 total－全局未读。
const unreadCount = computed(() => noticeStore.unreadCount)
const listQueryKey = computed(() => JSON.stringify([
  readFilter.value,
  page.value,
  size.value
]))
const listDataFresh = computed(() =>
  hasLoaded.value
  && loadedQueryKey.value === listQueryKey.value
  && !loading.value
  && !loadError.value
)
const writeBlocked = computed(() => !listDataFresh.value)
const readCount = computed(() => {
  if (readFilter.value === 'unread') return 0
  if (readFilter.value === 'read') return total.value
  return Math.max(0, total.value - noticeStore.unreadCount)
})
const typeOptions = computed<SelectOption[]>(() => {
  const types = [...new Set(notices.value.map((item) => item.type).filter(Boolean))]
  return types.map((type) => ({ label: typeName(type), value: type }))
})
// 通知类型未下推为后端参数（后端仅接受 read/page/size）——仅筛当页，翻页后可选项/结果会变化（spec 允许的降级）。
const filteredNotices = computed(() => {
  if (!typeFilter.value) return notices.value
  return notices.value.filter((item) => item.type === typeFilter.value)
})
const hasActiveFilters = computed(() => readFilter.value !== 'all' || Boolean(typeFilter.value))

const filterOptions: SelectOption[] = [
  { label: '全部', value: 'all' },
  { label: '未读', value: 'unread' },
  { label: '已读', value: 'read' }
]

onMounted(() => {
  loadNotices()
  void noticeStore.refresh()
  noticeTimer = window.setInterval(loadNotices, 60000)
})

onBeforeUnmount(() => {
  if (noticeTimer) window.clearInterval(noticeTimer)
})

async function loadNotices() {
  const requestSequence = ++listRequestSequence
  const queryKey = listQueryKey.value
  const read = readFilter.value === 'all' ? null : readFilter.value === 'read'
  const requestedPage = page.value
  const requestedSize = size.value
  loading.value = true
  loadError.value = ''
  try {
    const res = await listNotices(read, requestedPage, requestedSize)
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    notices.value = res.data.records || []
    total.value = res.data.total || 0
    hasLoaded.value = true
    loadedQueryKey.value = queryKey
  } catch (error) {
    if (requestSequence !== listRequestSequence || queryKey !== listQueryKey.value) return
    loadError.value = errorText(error, '通知加载失败')
  } finally {
    if (requestSequence === listRequestSequence) loading.value = false
  }
}

// Phase 44e-contract：真分页配方的 search/onPageChange/onPageSizeChange 三件套（与 DataPanel 视图同名同职责）。
function search() {
  page.value = 1
  void loadNotices()
}

function onPageChange(p: number) {
  page.value = p
  void loadNotices()
}

function onPageSizeChange(s: number) {
  size.value = s
  page.value = 1
  void loadNotices()
}

async function openNotice(row: NotificationItem) {
  selectedNotice.value = row
  drawerVisible.value = true
  if (row.readFlag !== 0 || writeBlocked.value) return
  try {
    await markNoticeRead(row.id)
    row.readFlag = 1
    void noticeStore.refresh() // 同步外壳角标/菜单圆点
  } catch (error) {
    showError(error, '操作失败')
  }
}

async function handleReadAll() {
  if (writeBlocked.value) return
  try {
    await markAllNoticesRead()
    notices.value = notices.value.map((item) => ({ ...item, readFlag: 1 }))
    noticeStore.reset() // 立即清零外壳角标/菜单圆点
    message.success('已全部标记为已读')
  } catch (error) {
    showError(error, '操作失败')
  }
}

function onReadFilterChange() {
  typeFilter.value = null
  search()
}

function resetFilters() {
  readFilter.value = 'all'
  typeFilter.value = null
  search()
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
  message.error(errorText(error, fallback))
}

function errorText(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  return detail || fallback
}
</script>

<template>
  <PageContainer title="通知中心" description="本人站内通知；未读在菜单与角标提示。">
    <template #actions>
      <n-space>
        <n-button secondary :loading="loading" @click="loadNotices">刷新</n-button>
        <n-button type="primary" :disabled="writeBlocked || unreadCount === 0" @click="handleReadAll">全部已读</n-button>
      </n-space>
    </template>

    <n-grid v-if="hasLoaded" cols="1 440:2 720:3" :x-gap="12" :y-gap="12" responsive="self" class="page-section">
      <n-gi><StatCard label="通知总数" :value="total" /></n-gi>
      <n-gi><StatCard label="未读" :value="unreadCount" tone="error" /></n-gi>
      <n-gi><StatCard label="已读" :value="readCount" tone="success" /></n-gi>
    </n-grid>

    <FilterBar :loading="loading" submit-text="查询" @submit="search" @reset="resetFilters">
      <label class="filter-field">
        <span>阅读状态</span>
        <n-segmented v-model:value="readFilter" :options="filterOptions" @update:value="onReadFilterChange" />
      </label>
      <label class="filter-field">
        <span>通知类型</span>
        <n-select v-model:value="typeFilter" clearable :options="typeOptions" placeholder="通知类型" style="width: 170px" />
      </label>
    </FilterBar>

    <n-alert v-if="loadError && hasLoaded" type="error" title="通知刷新失败" class="page-section" role="alert">
      <div class="notice-feedback">
        <span>{{ loadError }}。以下仍显示上次成功加载的结果。</span>
        <n-button size="small" type="error" secondary :loading="loading" @click="loadNotices">重试</n-button>
      </div>
    </n-alert>

    <n-spin :show="loading">
      <n-result
        v-if="loadError && !hasLoaded"
        status="error"
        title="通知加载失败"
        :description="loadError"
        class="notice-load-error"
        role="alert"
      >
        <template #footer>
          <n-button type="primary" :loading="loading" @click="loadNotices">重试</n-button>
        </template>
      </n-result>
      <n-list v-else-if="filteredNotices.length" bordered class="notice-list">
        <n-list-item
          v-for="item in filteredNotices"
          :key="item.id"
          class="notice-row"
          :class="{ 'notice-row--unread': item.readFlag === 0 }"
          role="button"
          tabindex="0"
          @click="openNotice(item)"
          @keydown.space.prevent
          @keyup.enter.prevent="openNotice(item)"
          @keyup.space.prevent="openNotice(item)"
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
      <EmptyState
        v-else
        :title="hasActiveFilters ? '未找到匹配通知' : '暂无通知'"
        :description="hasActiveFilters ? '请调整或清除筛选条件后重试。' : '当前还没有站内通知。'"
      >
        <template v-if="hasActiveFilters" #action>
          <n-button type="primary" secondary @click="resetFilters">清除筛选</n-button>
        </template>
      </EmptyState>
    </n-spin>

    <!-- Phase 44e-contract（P1-1 真分页 · n-list 特例）：n-list 无内置分页，DataPanel 的 remote 分页配方
         不适用于此处，改用独立 n-pagination 绑定后端 total / page / size，翻页与改页大小回抛后端重新查询。 -->
    <n-pagination
      v-if="hasLoaded && total > 0"
      class="notice-pagination"
      :page="page"
      :page-size="size"
      :item-count="total"
      :page-sizes="[10, 20, 50, 100]"
      show-size-picker
      @update:page="onPageChange"
      @update:page-size="onPageSizeChange"
    >
      <template #prefix="{ itemCount }">共 {{ itemCount }} 条</template>
    </n-pagination>

    <n-drawer v-model:show="drawerVisible" width="min(var(--overlay-medium), var(--overlay-drawer-max))">
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

.notice-pagination {
  display: flex;
  justify-content: flex-end;
  margin-bottom: var(--space-7);
}

.notice-row {
  cursor: pointer;
  transition: background 0.18s ease;
}

.notice-row:hover {
  background: var(--surface-muted);
}

.notice-row:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: -2px;
}

.notice-feedback {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.notice-load-error {
  min-height: 220px;
  padding: var(--space-8) var(--space-4);
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

@media (max-width: 720px) {
  .notice-feedback {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
