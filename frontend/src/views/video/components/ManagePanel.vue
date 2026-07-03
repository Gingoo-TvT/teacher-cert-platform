<script setup lang="ts">
import { computed, h, onMounted, ref, watch, type VNodeChild } from 'vue'
import { NButton, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import UploadVideoDrawer from './UploadVideoDrawer.vue'
import AssignReviewerModal from './AssignReviewerModal.vue'
import ArbitrateModal from './ArbitrateModal.vue'
import ReturnModal from './ReturnModal.vue'
import { statusLabel } from '@/constants/statusLabels'
import { listDictItems, type DictItem } from '@/api/dict'
import { useYearStore } from '@/stores/year'
import { renderTableActions } from '@/utils/tableActions'
import {
  confirmVideoReview,
  listReviewerCandidates,
  listReviewerGroups,
  listVideoReviews,
  playVideoReview,
  type ReviewerCandidate,
  type ReviewerGroup,
  type VideoReview
} from '@/api/video'

const props = defineProps<{
  canUpload: boolean
  canAssign: boolean
  canArbitrate: boolean
  canConfirm: boolean
  canPlay: boolean
}>()

const message = useMessage()
const yearStore = useYearStore()

const loading = ref(false)
const playerVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const statusFilter = ref<string | null>(null)
const reviews = ref<VideoReview[]>([])
const reviewers = ref<ReviewerCandidate[]>([])
const groups = ref<ReviewerGroup[]>([])
const dimensions = ref<DictItem[]>([])
const playbackUrl = ref('')
const watermarkText = ref('')
const watermarkStyle = ref({ left: '12%', top: '18%' })

const uploadRef = ref<InstanceType<typeof UploadVideoDrawer> | null>(null)
const assignRef = ref<InstanceType<typeof AssignReviewerModal> | null>(null)
const arbitrateRef = ref<InstanceType<typeof ArbitrateModal> | null>(null)
const returnRef = ref<InstanceType<typeof ReturnModal> | null>(null)

const canLoadReviews = computed(() => props.canUpload || props.canAssign || props.canArbitrate || props.canConfirm || props.canPlay)

const statusOptions: SelectOption[] = [
  { label: '校验失败', value: 'VALIDATION_FAILED' },
  { label: '待评审', value: 'WAIT_REVIEW' },
  { label: '评审中', value: 'REVIEWING' },
  { label: '需复评', value: 'NEED_REVIEW' },
  { label: '评审完成', value: 'REVIEW_COMPLETED' },
  { label: '已退回', value: 'RETURNED' },
  { label: '已确认', value: 'CONFIRMED' }
]

const statusSummary = computed(() => {
  const wait = reviews.value.filter((item) => item.status === 'WAIT_REVIEW').length
  const reviewingCount = reviews.value.filter((item) => item.status === 'REVIEWING').length
  const need = reviews.value.filter((item) => item.status === 'NEED_REVIEW').length
  const returned = reviews.value.filter((item) => item.status === 'RETURNED').length
  return { total: reviews.value.length, wait, reviewingCount, need, returned }
})

const columns: DataTableColumns<VideoReview> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '视频文件', key: 'videoFileName', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '时长', key: 'durationSeconds', width: 86, align: 'right', render: (row) => h('span', { class: 'numeric' }, `${row.durationSeconds || 0}s`) },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { value: row.status, text: row.statusLabel || statusLabel(row.status) }) },
  { title: '终分', key: 'finalScore', width: 78, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.finalScore ?? '-')) },
  { title: '结论', key: 'finalConclusion', width: 88, render: (row) => h(StatusTag, { text: conclusionText(row.finalConclusion) }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 250,
    render: (row) => {
      const actions: VNodeChild[] = []
      if (props.canPlay) actions.push(h(NButton, { size: 'small', type: 'primary', onClick: () => openPlayer(row) }, { default: () => '播放' }))
      if (props.canUpload && canReupload(row)) {
        actions.push(h(NButton, { size: 'small', quaternary: true, type: row.status === 'RETURNED' ? 'warning' : 'default', onClick: () => openUpload(row) }, { default: () => row.status === 'RETURNED' ? '重新上传' : '上传' }))
      }
      if (props.canAssign) actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openAssign(row) }, { default: () => '指派' }))
      if (props.canArbitrate && row.status === 'NEED_REVIEW') actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openArbitrate(row) }, { default: () => '复评/仲裁' }))
      if (props.canConfirm && row.status === 'REVIEW_COMPLETED') actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => confirm(row) }, { default: () => '确认' }))
      if ((props.canConfirm || props.canArbitrate) && row.status !== 'CONFIRMED') actions.push(h(NButton, { size: 'small', quaternary: true, type: 'warning', onClick: () => openReturn(row) }, { default: () => '退回' }))
      return renderTableActions(actions)
    }
  }
]

async function loadReviews() {
  if (!canLoadReviews.value) {
    reviews.value = []
    return
  }
  loading.value = true
  try {
    const res = await listVideoReviews({
      keyword: keyword.value,
      status: statusFilter.value,
      assessmentYear: assessmentYear.value
    })
    reviews.value = res.data.records
  } catch (error) {
    showError(error, '视频评审列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [dimensionRes, reviewerRes, groupRes] = await Promise.all([
    props.canArbitrate ? listDictItems('video_score_dimension', true) : Promise.resolve(null),
    props.canAssign || props.canArbitrate ? listReviewerCandidates() : Promise.resolve(null),
    props.canAssign ? listReviewerGroups() : Promise.resolve(null)
  ])
  dimensions.value = dimensionRes?.data.slice(0, 9) || []
  reviewers.value = reviewerRes?.data || []
  groups.value = groupRes?.data || []
}

function openUpload(row?: VideoReview) {
  uploadRef.value?.open(row)
}

function openAssign(row: VideoReview) {
  assignRef.value?.open(row)
}

function openArbitrate(row: VideoReview) {
  arbitrateRef.value?.open(row)
}

async function confirm(row: VideoReview) {
  try {
    await confirmVideoReview(row.id)
    message.success('已确认结果')
    await loadReviews()
  } catch (error) {
    showError(error, '确认失败')
  }
}

function openReturn(row: VideoReview) {
  returnRef.value?.open(row)
}

async function openPlayer(row: VideoReview) {
  try {
    const res = await playVideoReview(row.id)
    playbackUrl.value = res.data.url
    watermarkText.value = res.data.watermarkText
    moveWatermark()
    playerVisible.value = true
  } catch (error) {
    showError(error, '播放失败')
  }
}

function resetFilters() {
  keyword.value = ''
  assessmentYear.value = yearStore.assessmentYear
  statusFilter.value = null
  void loadReviews()
}

function canReupload(row: VideoReview) {
  return ['WAIT_UPLOAD', 'VALIDATION_FAILED', 'RETURNED'].includes(row.status)
}

function conclusionText(value?: string | null) {
  if (value === 'PASS') return '合格'
  if (value === 'FAIL') return '不合格'
  return '-'
}

function moveWatermark() {
  const left = 8 + Math.round(Math.random() * 55)
  const top = 12 + Math.round(Math.random() * 48)
  watermarkStyle.value = { left: `${left}%`, top: `${top}%` }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  await loadReviews()
})

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    assessmentYear.value = year
    await loadReviews()
  }
)
</script>

<template>
  <section>
    <n-grid :cols="5" :x-gap="12" responsive="screen" class="page-section">
      <n-gi><StatCard label="视频总数" :value="statusSummary.total" /></n-gi>
      <n-gi><StatCard label="待评审" :value="statusSummary.wait" tone="warning" /></n-gi>
      <n-gi><StatCard label="评审中" :value="statusSummary.reviewingCount" tone="info" /></n-gi>
      <n-gi><StatCard label="需复评" :value="statusSummary.need" tone="warning" /></n-gi>
      <n-gi><StatCard label="已退回" :value="statusSummary.returned" tone="error" /></n-gi>
    </n-grid>

    <FilterBar :loading="loading" @submit="loadReviews" @reset="resetFilters">
      <label class="filter-field">
        <span>关键词</span>
        <n-input v-model:value="keyword" clearable placeholder="学生 / 文件名 / MD5" style="width: 230px" @keyup.enter="loadReviews" />
      </label>
      <label class="filter-field">
        <span>年度</span>
        <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
      </label>
      <label class="filter-field">
        <span>状态</span>
        <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="全部状态" style="width: 150px" />
      </label>
    </FilterBar>

    <n-alert v-if="reviews.some((item) => item.status === 'RETURNED')" type="warning" :bordered="false" class="page-section">
      已退回视频可由学生重新上传；评审中、需复评、已确认等状态仍按校验规则禁止重传。
    </n-alert>

    <DataPanel
      title="视频评审列表"
      :columns="columns"
      :data="reviews"
      :total="reviews.length"
      :loading="loading"
      empty-title="暂无视频评审记录"
      empty-description="当前筛选条件下没有视频评审记录。"
      @refresh="loadReviews"
    >
      <template #actions>
        <n-button v-if="canUpload" type="primary" size="small" @click="openUpload()">上传视频</n-button>
      </template>
      <template v-if="canUpload" #emptyAction>
        <n-button type="primary" @click="openUpload()">上传视频</n-button>
      </template>
    </DataPanel>

    <UploadVideoDrawer v-if="canUpload" ref="uploadRef" :assessment-year="assessmentYear" @saved="loadReviews" />
    <AssignReviewerModal v-if="canAssign" ref="assignRef" :reviewers="reviewers" :groups="groups" @saved="loadReviews" />
    <ArbitrateModal v-if="canArbitrate" ref="arbitrateRef" :reviewers="reviewers" :dimensions="dimensions" @saved="loadReviews" />
    <ReturnModal v-if="canConfirm || canArbitrate" ref="returnRef" @saved="loadReviews" />

    <n-modal v-model:show="playerVisible" preset="card" title="视频播放" style="width: min(960px, 94vw)">
      <div class="player-shell">
        <video :src="playbackUrl" controls class="video-player" @play="moveWatermark" @timeupdate="moveWatermark" />
        <div class="watermark" :style="watermarkStyle">{{ watermarkText }}</div>
      </div>
    </n-modal>
  </section>
</template>

<style scoped>
.player-shell {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--radius-card);
  background: var(--video-bg);
}

.video-player {
  width: 100%;
  height: 100%;
}

.watermark {
  position: absolute;
  color: var(--video-watermark);
  font-size: 13px;
  pointer-events: none;
  text-shadow: 0 1px 2px var(--video-watermark-shadow);
  transition: left 0.4s ease, top 0.4s ease;
}
</style>
