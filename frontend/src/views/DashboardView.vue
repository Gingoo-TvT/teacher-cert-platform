<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import { useMessage, type DataTableColumns } from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import ChartBox from '@/components/ChartBox.vue'
import { listNotices, type NotificationItem } from '@/api/notice'
import { getStatsReport, type StatsReport, type StatsRow } from '@/api/stats'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'

interface DashboardProfile {
  title: string
  description: string
  statType: string
  focus: string[]
}

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()
const loading = ref(false)
const notices = ref<NotificationItem[]>([])
const report = ref<StatsReport | null>(null)

const profile = computed<DashboardProfile>(() => {
  if (userStore.roles.includes('SYS_ADMIN')) {
    return {
      title: '系统管理员工作台',
      description: '关注账号权限、系统参数、审计日志和备份治理。',
      statType: 'batches',
      focus: ['账号权限', '参数审计', '备份记录']
    }
  }
  if (userStore.roles.includes('ACADEMIC_ADMIN')) {
    return {
      title: '教务处工作台',
      description: '关注全校证书生成、签发、导出归档与数据交换。',
      statType: 'certificates',
      focus: ['证书生成', '签发归档', '导入导出']
    }
  }
  if (userStore.roles.includes('COLLEGE_AUDITOR')) {
    return {
      title: '学院负责人工作台',
      description: '关注复审、视频指派、复评仲裁和学院范围进度。',
      statType: 'videos',
      focus: ['复审处理', '评审指派', '视频复评']
    }
  }
  if (userStore.roles.includes('COLLEGE_CLERK')) {
    return {
      title: '学院教务员工作台',
      description: '关注初审处理、材料提交进度和只读导出。',
      statType: 'materials',
      focus: ['学生初审', '材料初审', '免考初审']
    }
  }
  if (userStore.roles.includes('REVIEW_TEACHER')) {
    return {
      title: '评审教师工作台',
      description: '关注待评分视频任务和已提交评审记录。',
      statType: 'videos',
      focus: ['视频评分', '独立评审', '鉴权播放']
    }
  }
  if (userStore.roles.includes('STUDENT')) {
    return {
      title: '学生工作台',
      description: '关注本人基本信息、材料、免考、视频和证书进度。',
      statType: 'submission',
      focus: ['信息确认', '材料提交', '视频上传']
    }
  }
  return {
    title: '工作台',
    description: '按当前账号权限展示可办理事项和最近通知。',
    statType: 'submission',
    focus: ['待办事项', '业务进度', '最近通知']
  }
})

const metrics = computed(() => report.value?.metrics || [])
const rows = computed(() => report.value?.rows || [])
const unread = computed(() => notices.value.filter((item) => item.readFlag === 0).length)
const canViewStats = computed(() => userStore.hasPerm('stats:view'))
const canViewNotice = computed(() => userStore.hasPerm('notice:view'))
const statCards = computed(() => {
  const base = metrics.value.slice(0, 4).map((item) => ({
    label: item.label,
    value: item.value,
    sub: item.unit || null,
    tone: 'brand' as const
  }))
  if (base.length) return base
  return [
    { label: '统计行数', value: rows.value.length, sub: null, tone: 'brand' as const },
    { label: '汇总数量', value: rows.value.reduce((sum, row) => sum + Number(row.count || 0), 0), sub: null, tone: 'success' as const },
    { label: '未读通知', value: unread.value, sub: null, tone: 'error' as const },
    { label: '当前学年', value: yearStore.assessmentYear, sub: null, tone: 'info' as const }
  ]
})

const chartOption = computed<EChartsOption>(() => {
  const chartRows = rows.value.slice(0, 12)
  return {
    xAxis: {
      type: 'category',
      data: chartRows.map((row) => chartLabel(row))
    },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: chartRows.map((row) => row.count || 0), barMaxWidth: 32 }]
  }
})

const noticeColumns: DataTableColumns<NotificationItem> = [
  { title: '状态', key: 'readFlag', width: 90, render: (row) => h(StatusTag, { text: row.readFlag === 0 ? '未读' : '已读' }) },
  { title: '标题', key: 'title', minWidth: 180, ellipsis: { tooltip: true }, render: (row) => row.title || '-' },
  { title: '内容', key: 'content', minWidth: 280, ellipsis: { tooltip: true }, render: (row) => row.content || '-' },
  { title: '时间', key: 'createdAt', width: 170, render: (row) => row.createdAt || '-' }
]

onMounted(loadDashboard)

watch(
  () => yearStore.assessmentYear,
  loadDashboard
)

async function loadDashboard() {
  loading.value = true
  try {
    const [statsRes, noticeRes] = await Promise.all([
      canViewStats.value ? getStatsReport(profile.value.statType, { assessmentYear: yearStore.assessmentYear }) : Promise.resolve(null),
      canViewNotice.value ? listNotices(null) : Promise.resolve({ data: { records: [] as NotificationItem[], total: 0 } })
    ])
    report.value = statsRes?.data || null
    notices.value = noticeRes.data.records.slice(0, 8)
  } catch (error) {
    showError(error, '工作台加载失败')
  } finally {
    loading.value = false
  }
}

function chartLabel(row: StatsRow) {
  const dimension = row.dimensionLabel || row.dimension || '-'
  const status = row.statusLabel || row.status
  return status ? `${dimension}\n${status}` : dimension
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}
</script>

<template>
  <PageContainer :title="profile.title" :description="profile.description">
    <template #actions>
      <n-button secondary :loading="loading" @click="loadDashboard">刷新</n-button>
    </template>

    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-for="item in statCards" :key="item.label">
        <StatCard :label="item.label" :value="item.value" :sub="item.sub" :tone="item.tone" />
      </n-gi>
    </n-grid>

    <n-grid :cols="3" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-for="item in profile.focus" :key="item">
        <n-card :bordered="false" size="small" class="focus-card">
          <StatusTag text="当前关注" />
          <strong>{{ item }}</strong>
        </n-card>
      </n-gi>
    </n-grid>

    <n-card v-if="canViewStats" :bordered="false" class="page-section chart-card">
      <template #header>{{ report?.title || '业务统计' }}</template>
      <ChartBox :option="chartOption" height="320px" />
    </n-card>

    <n-card :bordered="false" class="page-section">
      <template #header>最近通知</template>
      <n-data-table
        :columns="noticeColumns"
        :data="notices"
        :loading="loading"
        :row-key="(row: NotificationItem) => row.id"
        :pagination="false"
        :scroll-x="760"
        size="small"
        striped
      />
    </n-card>
  </PageContainer>
</template>

<style scoped>
.focus-card {
  min-height: 96px;
}

.focus-card :deep(.n-card__content) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.focus-card strong {
  font-size: 16px;
  color: var(--text);
}

.chart-card :deep(.n-card-header) {
  padding-bottom: 0;
}
</style>
