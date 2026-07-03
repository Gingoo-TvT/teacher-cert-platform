<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch, type VNodeChild } from 'vue'
import { NButton, useMessage, type DataTableColumns, type SelectOption, type UploadFileInfo } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import StatCard from '@/components/StatCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import { statusLabel } from '@/constants/statusLabels'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import { renderTableActions } from '@/utils/tableActions'
import {
  arbitrateVideoReview,
  assignVideoReview,
  assignVideoReviewGroup,
  confirmVideoReview,
  initVideoUpload,
  listReviewerCandidates,
  listReviewerGroups,
  listVideoReviews,
  mergeVideoUpload,
  playVideoReview,
  returnVideoReview,
  thirdVideoReview,
  uploadVideoChunk,
  type ReviewerCandidate,
  type ReviewerGroup,
  type VideoReview,
  type VideoScorePayload
} from '@/api/video'

interface DimensionRow {
  code: string
  score: number
}

type AssignMode = 'person' | 'group'

const props = defineProps<{
  canUpload: boolean
  canAssign: boolean
  canArbitrate: boolean
  canConfirm: boolean
  canPlay: boolean
}>()

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const uploadVisible = ref(false)
const assignVisible = ref(false)
const arbitrateVisible = ref(false)
const returnVisible = ref(false)
const playerVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const statusFilter = ref<string | null>(null)
const reviews = ref<VideoReview[]>([])
const students = ref<Student[]>([])
const reviewers = ref<ReviewerCandidate[]>([])
const groups = ref<ReviewerGroup[]>([])
const dimensions = ref<DictItem[]>([])
const fileList = ref<UploadFileInfo[]>([])
const uploading = ref(false)
const uploadProgress = ref(0)
const assigning = ref<VideoReview | null>(null)
const arbitrating = ref<VideoReview | null>(null)
const returning = ref<VideoReview | null>(null)
const playbackUrl = ref('')
const watermarkText = ref('')
const watermarkStyle = ref({ left: '12%', top: '18%' })

const canViewStudents = computed(() => userStore.hasPerm('student:view'))
const canLoadReviews = computed(() => props.canUpload || props.canAssign || props.canArbitrate || props.canConfirm || props.canPlay)

const uploadForm = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear,
  durationSeconds: 900,
  chunkSize: 512 * 1024
})

const assignForm = reactive({
  mode: 'person' as AssignMode,
  reviewerIds: [] as string[],
  groupId: ''
})

const arbitrateForm = reactive({
  mode: 'thirdExpert' as 'thirdExpert' | 'collegeArbitrate',
  reviewerId: '',
  score: 60,
  conclusion: 'PASS' as 'PASS' | 'FAIL',
  comment: '',
  dimensions: [] as DimensionRow[]
})

const returnForm = reactive({
  comment: ''
})

const statusOptions: SelectOption[] = [
  { label: '校验失败', value: 'VALIDATION_FAILED' },
  { label: '待评审', value: 'WAIT_REVIEW' },
  { label: '评审中', value: 'REVIEWING' },
  { label: '需复评', value: 'NEED_REVIEW' },
  { label: '评审完成', value: 'REVIEW_COMPLETED' },
  { label: '已退回', value: 'RETURNED' },
  { label: '已确认', value: 'CONFIRMED' }
]

const conclusionOptions: SelectOption[] = [
  { label: '合格', value: 'PASS' },
  { label: '不合格', value: 'FAIL' }
]

const studentOptions = computed<SelectOption[]>(() =>
  students.value.map((item) => ({ label: `${item.studentNo} ${item.name}`, value: item.id }))
)
const reviewerOptions = computed<SelectOption[]>(() =>
  reviewers.value.map((item) => ({ label: `${item.realName} ${item.workNo || item.id}`, value: item.id }))
)
const groupOptions = computed<SelectOption[]>(() =>
  groups.value.filter((item) => item.status === 'ENABLED').map((item) => ({ label: `${item.name} (${item.memberCount}人)`, value: item.id }))
)
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
  { title: '视频文件', key: 'videoFileName', minWidth: 190, ellipsis: { tooltip: true } },
  { title: '时长', key: 'durationSeconds', width: 86, render: (row) => h('span', { class: 'numeric' }, `${row.durationSeconds || 0}s`) },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { value: row.status, text: row.statusLabel || statusLabel(row.status) }) },
  { title: '终分', key: 'finalScore', width: 78, render: (row) => h('span', { class: 'numeric' }, String(row.finalScore ?? '-')) },
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
  const [studentRes, dimensionRes, reviewerRes, groupRes] = await Promise.all([
    props.canUpload && canViewStudents.value ? listStudents() : Promise.resolve(null),
    props.canArbitrate ? listDictItems('video_score_dimension', true) : Promise.resolve(null),
    props.canAssign || props.canArbitrate ? listReviewerCandidates() : Promise.resolve(null),
    props.canAssign ? listReviewerGroups() : Promise.resolve(null)
  ])
  students.value = studentRes?.data.records || []
  dimensions.value = dimensionRes?.data.slice(0, 9) || []
  reviewers.value = reviewerRes?.data || []
  groups.value = groupRes?.data || []
}

function openUpload(row?: VideoReview) {
  uploadForm.studentId = row?.studentId || ''
  uploadForm.assessmentYear = row?.assessmentYear || assessmentYear.value
  uploadForm.durationSeconds = row?.durationSeconds || 900
  uploadProgress.value = 0
  fileList.value = []
  uploadVisible.value = true
}

async function uploadVideo() {
  const file = fileList.value[0]?.file
  if (!file || !uploadForm.studentId || !uploadForm.assessmentYear) {
    message.error('请选择学生、年度和视频文件')
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  try {
    const fileMd5 = await quickHash(file)
    const init = await initVideoUpload({
      studentId: uploadForm.studentId,
      assessmentYear: uploadForm.assessmentYear,
      fileMd5,
      fileName: file.name,
      contentType: file.type || 'video/mp4',
      size: file.size,
      chunkSize: uploadForm.chunkSize,
      durationSeconds: uploadForm.durationSeconds
    })
    if (init.data.instantHit) {
      uploadProgress.value = 100
      message.success(init.data.validationMessage || '秒传命中')
      uploadVisible.value = false
      await loadReviews()
      return
    }
    const uploadId = init.data.uploadId
    if (!uploadId) throw new Error('上传会话为空')
    const uploaded = new Set(init.data.uploadedChunks)
    const total = Math.ceil(file.size / uploadForm.chunkSize)
    for (let index = 0; index < total; index += 1) {
      if (uploaded.has(index)) {
        uploadProgress.value = Math.round(((index + 1) / total) * 100)
        continue
      }
      const start = index * uploadForm.chunkSize
      const blob = file.slice(start, Math.min(start + uploadForm.chunkSize, file.size))
      await uploadVideoChunk({ uploadId, index, md5: await quickHash(blob), blob })
      uploadProgress.value = Math.round(((index + 1) / total) * 100)
    }
    const merged = await mergeVideoUpload(uploadId, uploadForm.durationSeconds)
    message[merged.data.status === 'VALIDATION_FAILED' ? 'warning' : 'success'](merged.data.validationMessage || '上传完成')
    uploadVisible.value = false
    await loadReviews()
  } catch (error) {
    showError(error, '上传失败')
  } finally {
    uploading.value = false
  }
}

function openAssign(row: VideoReview) {
  assigning.value = row
  assignForm.mode = 'person'
  assignForm.reviewerIds = row.tasks.filter((task) => task.reviewerRole === 'REVIEWER').map((task) => task.reviewerId)
  assignForm.groupId = ''
  assignVisible.value = true
}

async function saveAssign() {
  if (!assigning.value) return
  try {
    if (assignForm.mode === 'group') {
      if (!assignForm.groupId) {
        message.error('请选择评审组')
        return
      }
      await assignVideoReviewGroup(assigning.value.id, assignForm.groupId)
    } else {
      if (!assignForm.reviewerIds.length) {
        message.error('请选择评审教师')
        return
      }
      await assignVideoReview(assigning.value.id, assignForm.reviewerIds)
    }
    message.success('已指派评审')
    assignVisible.value = false
    await loadReviews()
  } catch (error) {
    showError(error, '指派失败')
  }
}

function openArbitrate(row: VideoReview) {
  arbitrating.value = row
  arbitrateForm.mode = 'thirdExpert'
  arbitrateForm.reviewerId = reviewers.value[0]?.id || ''
  arbitrateForm.score = 60
  arbitrateForm.conclusion = 'PASS'
  arbitrateForm.comment = ''
  arbitrateForm.dimensions = dimensions.value.map((item) => ({ code: item.itemCode, score: 0 }))
  arbitrateVisible.value = true
}

async function saveArbitrate() {
  if (!arbitrating.value) return
  try {
    if (arbitrateForm.mode === 'thirdExpert') {
      if (!arbitrateForm.reviewerId) {
        message.error('请选择第三专家')
        return
      }
      await thirdVideoReview(arbitrating.value.id, {
        ...scorePayload(),
        reviewerId: arbitrateForm.reviewerId
      })
    } else {
      await arbitrateVideoReview(arbitrating.value.id, {
        finalScore: arbitrateForm.score,
        conclusion: arbitrateForm.conclusion,
        comment: arbitrateForm.comment
      })
    }
    message.success('复评/仲裁已完成')
    arbitrateVisible.value = false
    await loadReviews()
  } catch (error) {
    showError(error, '复评/仲裁失败')
  }
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
  returning.value = row
  returnForm.comment = row.status === 'RETURNED' ? row.validationMessage || '' : ''
  returnVisible.value = true
}

async function saveReturn() {
  if (!returning.value) return
  const comment = returnForm.comment.trim()
  if (!comment) {
    message.error('请填写退回意见')
    return
  }
  try {
    await returnVideoReview(returning.value.id, comment)
    message.success('已退回，学生可重新上传')
    returnVisible.value = false
    await loadReviews()
  } catch (error) {
    showError(error, '退回失败')
  }
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

function scorePayload(): VideoScorePayload {
  const dimensionScores: Record<string, number> = {}
  for (const item of arbitrateForm.dimensions) dimensionScores[item.code] = item.score
  if (Object.keys(dimensionScores).length === 0) {
    for (const item of dimensions.value) dimensionScores[item.itemCode] = 0
  }
  return {
    score: arbitrateForm.score,
    conclusion: arbitrateForm.conclusion,
    comment: arbitrateForm.comment,
    dimensionScores
  }
}

function canReupload(row: VideoReview) {
  return ['WAIT_UPLOAD', 'VALIDATION_FAILED', 'RETURNED'].includes(row.status)
}

function conclusionText(value?: string | null) {
  if (value === 'PASS') return '合格'
  if (value === 'FAIL') return '不合格'
  return '-'
}

async function quickHash(blob: Blob) {
  const buffer = await blob.arrayBuffer()
  const bytes = new Uint8Array(buffer)
  let hash = 0
  for (const byte of bytes) hash = (hash * 31 + byte) >>> 0
  return hash.toString(16).padStart(8, '0')
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
    if (!uploadVisible.value) uploadForm.assessmentYear = year
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
      :scroll-x="1600"
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

    <n-drawer v-model:show="uploadVisible" :width="560">
      <n-drawer-content title="上传教学能力视频" closable>
        <n-space vertical>
          <n-alert type="info" :bordered="false">
            仅退回、待上传或校验失败状态显示重传入口；其他状态由校验规则禁止重传。
          </n-alert>
          <n-select v-model:value="uploadForm.studentId" :options="studentOptions" filterable placeholder="学生" />
          <n-input v-model:value="uploadForm.assessmentYear" placeholder="考核年度" class="mono-input" />
          <n-input-number v-model:value="uploadForm.durationSeconds" :min="1" style="width: 100%" placeholder="时长（秒）" />
          <n-upload v-model:file-list="fileList" :max="1" accept="video/mp4,.mp4" :default-upload="false" />
          <n-progress type="line" :percentage="uploadProgress" indicator-placement="inside" />
        </n-space>
        <template #footer>
          <n-space justify="end">
            <n-button @click="uploadVisible = false">取消</n-button>
            <n-button type="primary" :loading="uploading" @click="uploadVideo">开始上传</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="assignVisible" preset="card" title="指派评审" style="width: 620px">
      <n-space vertical>
        <n-alert v-if="assigning" type="info" :bordered="false">
          {{ assigning.studentNo }} / {{ assigning.studentName }} / 当前状态：{{ assigning.statusLabel || statusLabel(assigning.status) }}
        </n-alert>
        <n-radio-group v-model:value="assignForm.mode">
          <n-radio-button value="person">按人指派</n-radio-button>
          <n-radio-button value="group">按组指派</n-radio-button>
        </n-radio-group>
        <n-select
          v-if="assignForm.mode === 'person'"
          v-model:value="assignForm.reviewerIds"
          multiple
          filterable
          :options="reviewerOptions"
          placeholder="选择评审教师"
        />
        <n-select v-else v-model:value="assignForm.groupId" filterable :options="groupOptions" placeholder="选择评审组" />
        <n-space justify="end">
          <n-button @click="assignVisible = false">取消</n-button>
          <n-button type="primary" @click="saveAssign">保存</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal v-model:show="arbitrateVisible" preset="card" title="复评/仲裁" style="width: 580px">
      <n-space vertical>
        <n-radio-group v-model:value="arbitrateForm.mode">
          <n-radio-button value="thirdExpert">第三专家</n-radio-button>
          <n-radio-button value="collegeArbitrate">学院仲裁</n-radio-button>
        </n-radio-group>
        <n-select v-if="arbitrateForm.mode === 'thirdExpert'" v-model:value="arbitrateForm.reviewerId" :options="reviewerOptions" filterable placeholder="第三专家" />
        <n-input-number v-model:value="arbitrateForm.score" :min="0" :max="100" style="width: 100%" placeholder="分数/终分" />
        <n-select v-model:value="arbitrateForm.conclusion" :options="conclusionOptions" />
        <n-input v-model:value="arbitrateForm.comment" type="textarea" placeholder="意见" />
        <n-space justify="end">
          <n-button @click="arbitrateVisible = false">取消</n-button>
          <n-button type="primary" @click="saveArbitrate">保存</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal v-model:show="returnVisible" preset="dialog" title="退回视频">
      <n-space vertical>
        <n-alert v-if="returning" type="warning" :bordered="false">
          {{ returning.studentNo }} / {{ returning.studentName }}。已确认视频不可退回；其他状态由校验规则处理。
        </n-alert>
        <n-input v-model:value="returnForm.comment" type="textarea" placeholder="请输入退回意见，学生重传时可据此修改" />
        <n-space justify="end">
          <n-button @click="returnVisible = false">取消</n-button>
          <n-button type="warning" @click="saveReturn">退回</n-button>
        </n-space>
      </n-space>
    </n-modal>

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

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
