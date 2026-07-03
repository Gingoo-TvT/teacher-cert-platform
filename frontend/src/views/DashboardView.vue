<script setup lang="ts">
import { computed, onMounted, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import {
  BarChartOutline,
  ClipboardOutline,
  CloudUploadOutline,
  DocumentTextOutline,
  FolderOpenOutline,
  HomeOutline,
  IdCardOutline,
  NotificationsOutline,
  PeopleOutline,
  PlayCircleOutline,
  RibbonOutline,
  SchoolOutline,
  SettingsOutline,
  ShieldCheckmarkOutline,
  StatsChartOutline,
  VideocamOutline
} from '@vicons/ionicons5'
import { useMessage } from 'naive-ui'
import ChartBox from '@/components/ChartBox.vue'
import PageContainer from '@/components/PageContainer.vue'
import StatCard from '@/components/StatCard.vue'
import { listNotices, markNoticeRead, unreadNoticeCount, type NotificationItem } from '@/api/notice'
import { getStatsReport, type StatsReport, type StatsRow } from '@/api/stats'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import { formatDateTime } from '@/utils/format'

interface DashboardProfile {
  statType: string
  description: string
}

interface QuickEntry {
  title: string
  description: string
  routeName: string
  icon: Component
  perms?: string[]
}

const message = useMessage()
const router = useRouter()
const userStore = useUserStore()
const yearStore = useYearStore()
const loading = ref(false)
const notices = ref<NotificationItem[]>([])
const noticeTotal = ref(0)
const unreadTotal = ref(0)
const report = ref<StatsReport | null>(null)

const roleNameMap: Record<string, string> = {
  SYS_ADMIN: '系统管理员',
  ACADEMIC_ADMIN: '教务处管理员',
  COLLEGE_AUDITOR: '学院负责人',
  COLLEGE_CLERK: '学院教务员',
  REVIEW_TEACHER: '评审教师',
  STUDENT: '学生'
}

const profile = computed<DashboardProfile>(() => {
  if (hasRole('SYS_ADMIN')) return { statType: 'batches', description: '关注账号、参数审计、备份治理和基础数据维护。' }
  if (hasRole('ACADEMIC_ADMIN')) return { statType: 'certificates', description: '关注证书生成、签发、导出归档与数据交换。' }
  if (hasRole('COLLEGE_AUDITOR')) return { statType: 'videos', description: '关注复审、视频指派、复评仲裁和学院范围进度。' }
  if (hasRole('COLLEGE_CLERK')) return { statType: 'materials', description: '关注学生信息初审、材料进度和免考申请处理。' }
  if (hasRole('REVIEW_TEACHER')) return { statType: 'videos', description: '关注视频评分任务、已提交记录和最近通知。' }
  if (hasRole('STUDENT')) return { statType: 'submission', description: '关注本人信息、材料、免考、视频和证书进度。' }
  return { statType: 'submission', description: '按当前账号展示可办理事项和最近通知。' }
})

const displayName = computed(() => userStore.realName || userStore.username || '你好')
const roleText = computed(() => userStore.roles.map((role) => roleNameMap[role] || role).join(' / ') || '当前账号')
const greetingTitle = computed(() => `${greetingText()}，${displayName.value}`)
const greetingDescription = computed(() => `${roleText.value} · ${dateText()}`)
const metrics = computed(() => report.value?.metrics || [])
const rows = computed(() => report.value?.rows || [])
const canViewStats = computed(() => userStore.hasPerm('stats:view'))
const canViewNotice = computed(() => userStore.hasPerm('notice:view'))

const statCards = computed(() => {
  const icons = [StatsChartOutline, BarChartOutline, SchoolOutline, NotificationsOutline]
  const dedupedMetrics = uniqueMetricsByLabel(metrics.value).slice(0, 4)
  const cards = dedupedMetrics.map((item, index) => ({
    label: item.label,
    value: item.value,
    unit: item.unit || null,
    sub: null,
    icon: icons[index] || StatsChartOutline,
    tone: 'brand' as const
  }))
  if (cards.length) return cards
  const statsTotal = rows.value.reduce((sum, row) => sum + Number(row.count || 0), 0)
  return [
    { label: '统计汇总', value: statsTotal, unit: null, sub: null, icon: BarChartOutline, tone: 'brand' as const },
    { label: '未读通知', value: unreadTotal.value, unit: null, sub: null, icon: NotificationsOutline, tone: 'error' as const },
    { label: '通知总数', value: noticeTotal.value, unit: null, sub: null, icon: NotificationsOutline, tone: 'info' as const },
    { label: '当前学年', value: yearStore.assessmentYear, unit: null, sub: null, icon: SchoolOutline, tone: 'neutral' as const }
  ]
})

const quickEntries = computed(() => roleEntries().filter((item) => !item.perms?.length || item.perms.some((perm) => userStore.hasPerm(perm))).slice(0, 6))

// 类目 ≤8 且有数值 → 环形图看占比；否则用普通列表展示（不再画柱状图）。
const chartRows = computed(() => rows.value.filter((row) => Number(row.count || 0) > 0))
const showChart = computed(() => chartRows.value.length > 0 && chartRows.value.length <= 8)
const listRows = computed(() => rows.value.slice(0, 8))
const chartOption = computed<EChartsOption>(() => ({
  legend: { show: false },
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
      label: { formatter: '{b}\n{c} ({d}%)', color: 'inherit', fontSize: 13, lineHeight: 18 },
      labelLine: { length: 14, length2: 10 },
      data: chartRows.value.map((row) => ({ name: chartLabel(row), value: Number(row.count || 0) }))
    }
  ]
}))

onMounted(loadDashboard)

watch(
  () => yearStore.assessmentYear,
  loadDashboard
)

async function loadDashboard() {
  loading.value = true
  try {
    const [statsRes, noticeRes, unreadRes] = await Promise.all([
      canViewStats.value ? getStatsReport(profile.value.statType, { assessmentYear: yearStore.assessmentYear }) : Promise.resolve(null),
      canViewNotice.value ? listNotices(null) : Promise.resolve({ data: { records: [] as NotificationItem[], total: 0 } }),
      canViewNotice.value ? unreadNoticeCount() : Promise.resolve({ data: 0 })
    ])
    report.value = statsRes?.data || null
    notices.value = noticeRes.data.records.slice(0, 8)
    noticeTotal.value = noticeRes.data.total
    unreadTotal.value = Number(unreadRes.data || 0)
  } catch (error) {
    showError(error, '工作台加载失败')
  } finally {
    loading.value = false
  }
}

async function openNotice(item: NotificationItem) {
  if (item.readFlag === 0) {
    try {
      await markNoticeRead(item.id)
      item.readFlag = 1
      unreadTotal.value = Math.max(0, unreadTotal.value - 1)
    } catch (error) {
      showError(error, '通知状态更新失败')
    }
  }
}

function goEntry(item: QuickEntry) {
  void router.push({ name: item.routeName })
}

function chartLabel(row: StatsRow) {
  const dimension = row.dimensionLabel || row.dimension || '-'
  const status = row.statusLabel || row.status
  return status ? `${dimension} / ${status}` : dimension
}

function uniqueMetricsByLabel(items: NonNullable<StatsReport['metrics']>) {
  const seen = new Set<string>()
  return items.filter((item) => {
    const label = item.label || ''
    if (seen.has(label)) return false
    seen.add(label)
    return true
  })
}

function roleEntries(): QuickEntry[] {
  if (hasRole('SYS_ADMIN')) {
    return [
      entry('账号管理', '维护账号、角色与可查看范围', 'securityManage', SettingsOutline, ['system:user:manage', 'system:role:manage']),
      entry('参数审计备份', '查看参数、审计和备份记录', 'systemAudit', ShieldCheckmarkOutline, ['system:param:manage', 'audit:view', 'system:backup']),
      entry('组织与专业', '维护学院、专业与培养目标', 'organizationManage', SchoolOutline, ['college:manage', 'major:manage']),
      entry('数据字典', '维护页面下拉与业务字典', 'dictManage', ClipboardOutline, ['dict:manage']),
      entry('任教学科库', '维护任教学段和学科清单', 'subjectManage', DocumentTextOutline, ['subject:manage'])
    ]
  }
  if (hasRole('ACADEMIC_ADMIN')) {
    return [
      entry('证书生成', '查看前置条件并生成证书编号', 'certificateManage', RibbonOutline, ['cert:generate', 'cert:view']),
      entry('证书签发', '处理待签发证书队列', 'certificateIssue', IdCardOutline, ['cert:issue']),
      entry('导入预校验', '上传标准数据并查看异常', 'exchangeImport', CloudUploadOutline, ['exchange:prevalidate', 'exchange:import']),
      entry('导出中心', '导出标准表和汇总材料', 'exchangeExport', FolderOpenOutline, ['exchange:export:standard', 'exchange:export:full']),
      entry('统计报表', '查看全校办理进度', 'statsReport', BarChartOutline, ['stats:view']),
      entry('通知中心', '查看最近提醒与处理结果', 'noticeCenter', NotificationsOutline, ['notice:view'])
    ]
  }
  if (hasRole('COLLEGE_AUDITOR')) {
    return [
      entry('材料复审', '处理学院过程性材料复审', 'materialManage', FolderOpenOutline, ['material:secondReview']),
      entry('免考复审', '处理免考申请复审', 'exemptionManage', ClipboardOutline, ['exemption:secondReview']),
      entry('视频指派', '分配评审教师与评审组', 'videoReview', VideocamOutline, ['video:assign']),
      entry('测试确认', '确认学院测试结果', 'testResultManage', ShieldCheckmarkOutline, ['test:confirm']),
      entry('学院统计', '查看学院范围进度', 'statsReport', BarChartOutline, ['stats:view'])
    ]
  }
  if (hasRole('COLLEGE_CLERK')) {
    return [
      entry('学生初审', '处理学生基本信息初审', 'studentManage', PeopleOutline, ['student:view']),
      entry('培养信息', '查看与维护培养信息', 'trainingManage', SchoolOutline, ['student:view']),
      entry('材料初审', '处理过程性材料初审', 'materialManage', FolderOpenOutline, ['material:firstReview']),
      entry('免考初审', '处理免考申请初审', 'exemptionManage', ClipboardOutline, ['exemption:firstReview']),
      entry('导出中心', '按学院导出办理材料', 'exchangeExport', CloudUploadOutline, ['exchange:export:standard', 'exchange:export:full'])
    ]
  }
  if (hasRole('REVIEW_TEACHER')) {
    return [
      entry('评分工作台', '进入视频任务评分区', 'videoReview', VideocamOutline, ['video:score']),
      entry('待评分视频', '优先处理未提交任务', 'videoReview', PlayCircleOutline, ['video:score']),
      entry('评审记录', '查看已提交评分记录', 'videoReview', ClipboardOutline, ['video:score']),
      entry('通知中心', '查看指派与退回提醒', 'noticeCenter', NotificationsOutline, ['notice:view'])
    ]
  }
  if (hasRole('STUDENT')) {
    return [
      entry('本人信息', '确认和补充个人信息', 'studentSelf', HomeOutline, ['student:confirm']),
      entry('过程性材料', '上传四类过程性材料', 'materialManage', FolderOpenOutline, ['material:upload']),
      entry('免考申请', '提交免考科目与佐证', 'exemptionManage', ClipboardOutline, ['exemption:apply']),
      entry('教学视频', '上传视频并查看进度', 'videoReview', VideocamOutline, ['video:upload']),
      entry('我的证书', '查看证书编号和有效期', 'certificateManage', RibbonOutline, ['cert:view']),
      entry('通知中心', '查看办理进度提醒', 'noticeCenter', NotificationsOutline, ['notice:view'])
    ]
  }
  return [
    entry('工作台', '查看可办理事项', 'dashboard', HomeOutline),
    entry('通知中心', '查看最近提醒', 'noticeCenter', NotificationsOutline, ['notice:view'])
  ]
}

function entry(title: string, description: string, routeName: string, icon: Component, perms?: string[]): QuickEntry {
  return { title, description, routeName, icon, perms }
}

function hasRole(role: string) {
  return userStore.roles.includes(role)
}

function greetingText() {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

function dateText() {
  const date = new Date()
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${weekdays[date.getDay()]}`
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}
</script>

<template>
  <PageContainer :title="greetingTitle" :description="greetingDescription">
    <template #actions>
      <n-button secondary :loading="loading" @click="loadDashboard">刷新</n-button>
    </template>

    <n-grid :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-for="item in statCards" :key="item.label">
        <StatCard :label="item.label" :value="item.value" :unit="item.unit" :sub="item.sub" :tone="item.tone" :icon="item.icon" />
      </n-gi>
    </n-grid>

    <n-card :bordered="false" class="page-section quick-card">
      <template #header>快捷入口</template>
      <n-grid :cols="3" :x-gap="12" :y-gap="12" responsive="screen">
        <n-gi v-for="item in quickEntries" :key="item.title">
          <button class="quick-entry" type="button" @click="goEntry(item)">
            <span class="quick-entry__icon"><n-icon :component="item.icon" /></span>
            <span class="quick-entry__text">
              <strong>{{ item.title }}</strong>
              <span>{{ item.description }}</span>
            </span>
          </button>
        </n-gi>
      </n-grid>
    </n-card>

    <n-grid :cols="2" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-if="canViewStats">
        <n-card :bordered="false" class="chart-card">
          <template #header>{{ report?.title || '业务统计' }}</template>
          <ChartBox v-if="showChart" :option="chartOption" height="320px" />
          <div v-else-if="listRows.length" class="stat-list">
            <div v-for="row in listRows" :key="chartLabel(row)" class="stat-list__row">
              <span class="stat-list__name">{{ chartLabel(row) }}</span>
              <span class="stat-list__count numeric tabular-nums">{{ row.count || 0 }}</span>
            </div>
          </div>
          <n-empty v-else description="暂无统计数据" />
        </n-card>
      </n-gi>

      <n-gi>
        <n-card :bordered="false" class="notice-card">
          <template #header>最近通知</template>
          <template #header-extra>
            <n-button v-if="canViewNotice" text @click="router.push({ name: 'noticeCenter' })">查看全部</n-button>
          </template>
          <n-spin :show="loading">
            <n-empty v-if="!notices.length" description="暂无通知" />
            <n-list v-else hoverable clickable class="notice-list">
              <n-list-item v-for="item in notices" :key="item.id" @click="openNotice(item)">
                <div class="notice-row">
                  <span class="notice-dot" :class="{ 'notice-dot--read': item.readFlag === 1 }" />
                  <div class="notice-main">
                    <strong :class="{ 'notice-title--read': item.readFlag === 1 }">{{ item.title || '-' }}</strong>
                    <span>{{ item.content || '-' }}</span>
                  </div>
                  <span class="notice-time mono tabular-nums">{{ formatDateTime(item.createdAt) }}</span>
                </div>
              </n-list-item>
            </n-list>
          </n-spin>
        </n-card>
      </n-gi>
    </n-grid>
  </PageContainer>
</template>

<style scoped>
.quick-card :deep(.n-card-header),
.chart-card :deep(.n-card-header),
.notice-card :deep(.n-card-header) {
  padding-bottom: var(--space-3);
}

.stat-list {
  display: flex;
  flex-direction: column;
}

.stat-list__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 10px 2px;
  border-bottom: 1px solid var(--shell-border);
}

.stat-list__row:last-child {
  border-bottom: none;
}

.stat-list__name {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-list__count {
  flex: none;
  width: auto;
  color: var(--brand);
  font-weight: 600;
}

.quick-entry {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: center;
  width: 100%;
  min-height: 92px;
  padding: var(--space-4);
  border: 0;
  border-radius: var(--radius-card);
  background: var(--surface-muted);
  color: var(--text);
  cursor: pointer;
  text-align: left;
  transition: background 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.quick-entry:hover {
  background: var(--brand-soft);
  box-shadow: var(--shadow-card);
}

.quick-entry__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 999px;
  background: var(--brand-soft-strong);
  color: var(--brand);
  font-size: 22px;
}

.quick-entry__text {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.quick-entry__text strong,
.quick-entry__text span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-entry__text strong {
  font-size: 15px;
  line-height: 22px;
}

.quick-entry__text span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 18px;
}

.notice-list {
  margin: calc(var(--space-3) * -1);
}

.notice-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: start;
  width: 100%;
}

.notice-dot {
  width: 8px;
  height: 8px;
  margin-top: 7px;
  border-radius: 999px;
  background: var(--brand);
}

.notice-dot--read {
  background: var(--border-strong);
}

.notice-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.notice-main strong,
.notice-main span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-main strong {
  color: var(--text);
  font-size: 14px;
}

.notice-title--read {
  font-weight: 500;
}

.notice-main span,
.notice-time {
  color: var(--text-muted);
  font-size: 12px;
}

@media (max-width: 720px) {
  .notice-row {
    grid-template-columns: 10px minmax(0, 1fr);
  }

  .notice-time {
    grid-column: 2;
  }
}
</style>
