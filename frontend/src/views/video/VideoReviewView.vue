<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import {
  NButton,
  NInputNumber,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import { listUsers, type User } from '@/api/security'
import { useUserStore } from '@/stores/user'
import {
  arbitrateVideoReview,
  assignVideoReview,
  confirmVideoReview,
  initVideoUpload,
  listMyVideoTasks,
  listVideoReviews,
  mergeVideoUpload,
  playVideoReview,
  submitVideoScore,
  thirdVideoReview,
  uploadVideoChunk,
  type VideoReview,
  type VideoScorePayload,
  type VideoTask
} from '@/api/video'

interface DimensionRow {
  code: string
  label: string
  score: number
}

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const taskLoading = ref(false)
const uploadVisible = ref(false)
const scoreVisible = ref(false)
const assignVisible = ref(false)
const arbitrateVisible = ref(false)
const playerVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref('2026')
const statusFilter = ref<string | null>(null)
const reviews = ref<VideoReview[]>([])
const tasks = ref<VideoTask[]>([])
const students = ref<Student[]>([])
const reviewers = ref<User[]>([])
const dimensions = ref<DictItem[]>([])
const fileList = ref<UploadFileInfo[]>([])
const uploadProgress = ref(0)
const uploading = ref(false)
const currentScoreTask = ref<VideoTask | null>(null)
const assigning = ref<VideoReview | null>(null)
const arbitrating = ref<VideoReview | null>(null)
const playbackUrl = ref('')
const watermarkText = ref('')
const watermarkStyle = ref({ left: '12%', top: '18%' })

const canUpload = computed(() => userStore.hasPerm('video:upload'))
const canScore = computed(() => userStore.hasPerm('video:score'))
const canAssign = computed(() => userStore.hasPerm('video:assign'))
const canArbitrate = computed(() => userStore.hasPerm('video:arbitrate'))
const canConfirm = computed(() => userStore.hasPerm('video:confirm'))
const canPlay = computed(() => userStore.hasPerm('video:play'))
const selfMode = computed(() => canUpload.value && !canAssign.value && !canScore.value)

const uploadForm = reactive({
  studentId: '',
  assessmentYear: '2026',
  durationSeconds: 900,
  chunkSize: 512 * 1024
})

const scoreForm = reactive({
  score: 60,
  conclusion: 'PASS' as 'PASS' | 'FAIL',
  comment: '',
  dimensions: [] as DimensionRow[]
})

const assignForm = reactive({
  reviewerIds: [] as string[]
})

const arbitrateForm = reactive({
  mode: 'thirdExpert' as 'thirdExpert' | 'collegeArbitrate',
  reviewerId: '',
  score: 60,
  conclusion: 'PASS' as 'PASS' | 'FAIL',
  comment: ''
})

const statusOptions: SelectOption[] = [
  { label: '校验失败', value: 'VALIDATION_FAILED' },
  { label: '待评审', value: 'WAIT_REVIEW' },
  { label: '评审中', value: 'REVIEWING' },
  { label: '需复评', value: 'NEED_REVIEW' },
  { label: '评审完成', value: 'REVIEW_COMPLETED' },
  { label: '已确认', value: 'CONFIRMED' }
]

const studentOptions = computed<SelectOption[]>(() =>
  students.value.map((item) => ({ label: `${item.studentNo} ${item.name}`, value: item.id }))
)
const reviewerOptions = computed<SelectOption[]>(() =>
  reviewers.value.map((item) => ({ label: `${item.realName} ${item.workNo || item.username}`, value: item.id }))
)

const reviewColumns: DataTableColumns<VideoReview> = [
  { title: '学号', key: 'studentNo', width: 130, ellipsis: { tooltip: true } },
  { title: '姓名', key: 'studentName', width: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 95 },
  { title: '视频', key: 'videoFileName', minWidth: 190, ellipsis: { tooltip: true } },
  { title: '时长', key: 'durationSeconds', width: 90, render: (row) => `${row.durationSeconds || 0}s` },
  { title: '状态', key: 'status', width: 110, render: (row) => statusTag(row.status, row.statusLabel) },
  { title: '终分', key: 'finalScore', width: 80, render: (row) => row.finalScore ?? '-' },
  { title: '结论', key: 'finalConclusion', width: 90, render: (row) => conclusionText(row.finalConclusion) },
  {
    title: '操作',
    key: 'actions',
    width: 330,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        canPlay.value ? h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openPlayer(row) }, { default: () => '播放' }) : null,
        canAssign.value ? h(NButton, { size: 'small', quaternary: true, onClick: () => openAssign(row) }, { default: () => '分配' }) : null,
        canArbitrate.value && row.status === 'NEED_REVIEW'
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => openArbitrate(row) }, { default: () => '复评' })
          : null,
        canConfirm.value && row.status === 'REVIEW_COMPLETED'
          ? h(NButton, { size: 'small', quaternary: true, onClick: () => confirm(row) }, { default: () => '确认' })
          : null
      ])
  }
]

const taskColumns: DataTableColumns<VideoTask> = [
  { title: '评审ID', key: 'videoReviewId', width: 160 },
  { title: '角色', key: 'reviewerRole', width: 120 },
  { title: '状态', key: 'submitted', width: 100, render: (row) => (row.submitted === 1 ? '已提交' : '待评分') },
  { title: '分数', key: 'score', width: 80, render: (row) => row.score ?? '-' },
  { title: '结论', key: 'conclusion', width: 90, render: (row) => conclusionText(row.conclusion) },
  {
    title: '操作',
    key: 'actions',
    width: 210,
    render: (row) =>
      h(NSpace, { size: 6 }, () => [
        h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => openPlayerByTask(row) }, { default: () => '播放' }),
        row.submitted === 0 ? h(NButton, { size: 'small', quaternary: true, onClick: () => openScore(row) }, { default: () => '评分' }) : null
      ])
  }
]

async function loadReviews() {
  loading.value = true
  try {
    const res = await listVideoReviews({
      keyword: keyword.value,
      status: statusFilter.value,
      assessmentYear: assessmentYear.value
    })
    reviews.value = res.data.records
  } finally {
    loading.value = false
  }
}

async function loadTasks() {
  if (!canScore.value) return
  taskLoading.value = true
  try {
    const res = await listMyVideoTasks()
    tasks.value = res.data.records
  } finally {
    taskLoading.value = false
  }
}

async function loadOptions() {
  const [studentRes, dimensionRes, reviewerRes] = await Promise.all([
    listStudents(),
    listDictItems('video_score_dimension', true),
    listUsers({ status: 'ENABLED' })
  ])
  students.value = selfMode.value ? studentRes.data.records.slice(0, 1) : studentRes.data.records
  dimensions.value = dimensionRes.data.slice(0, 9)
  reviewers.value = reviewerRes.data.records.filter((item) => item.roles.some((role) => role.code === 'REVIEW_TEACHER'))
}

function openUpload() {
  uploadForm.studentId = selfMode.value ? userStore.currentUser?.studentId || students.value[0]?.id || '' : ''
  uploadForm.assessmentYear = assessmentYear.value
  uploadForm.durationSeconds = 900
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
      message.success('秒传命中，校验已完成')
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
  } finally {
    uploading.value = false
  }
}

function openScore(task: VideoTask) {
  currentScoreTask.value = task
  scoreForm.score = 60
  scoreForm.conclusion = 'PASS'
  scoreForm.comment = ''
  scoreForm.dimensions = dimensions.value.map((item) => ({ code: item.itemCode, label: item.itemValue, score: 0 }))
  scoreVisible.value = true
}

async function saveScore() {
  if (!currentScoreTask.value) return
  await submitVideoScore(currentScoreTask.value.id, scorePayload())
  message.success('评分已提交')
  scoreVisible.value = false
  await Promise.all([loadTasks(), loadReviews()])
}

function openAssign(row: VideoReview) {
  assigning.value = row
  assignForm.reviewerIds = row.tasks.filter((task) => task.reviewerRole === 'REVIEWER').map((task) => task.reviewerId)
  assignVisible.value = true
}

async function saveAssign() {
  if (!assigning.value) return
  await assignVideoReview(assigning.value.id, assignForm.reviewerIds)
  message.success('已分配评审教师')
  assignVisible.value = false
  await loadReviews()
}

function openArbitrate(row: VideoReview) {
  arbitrating.value = row
  arbitrateForm.mode = 'thirdExpert'
  arbitrateForm.reviewerId = reviewers.value[0]?.id || ''
  arbitrateForm.score = 60
  arbitrateForm.conclusion = 'PASS'
  arbitrateForm.comment = ''
  arbitrateVisible.value = true
}

async function saveArbitrate() {
  if (!arbitrating.value) return
  if (arbitrateForm.mode === 'thirdExpert') {
    await thirdVideoReview(arbitrating.value.id, { ...scorePayload(arbitrateForm.score, arbitrateForm.conclusion, arbitrateForm.comment), reviewerId: arbitrateForm.reviewerId })
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
}

async function confirm(row: VideoReview) {
  await confirmVideoReview(row.id)
  message.success('已确认结果')
  await loadReviews()
}

async function openPlayer(row: VideoReview) {
  const res = await playVideoReview(row.id)
  playbackUrl.value = res.data.url
  watermarkText.value = res.data.watermarkText
  moveWatermark()
  playerVisible.value = true
}

async function openPlayerByTask(task: VideoTask) {
  const row = reviews.value.find((item) => item.id === task.videoReviewId)
  if (row) await openPlayer(row)
  else {
    const res = await playVideoReview(task.videoReviewId)
    playbackUrl.value = res.data.url
    watermarkText.value = res.data.watermarkText
    moveWatermark()
    playerVisible.value = true
  }
}

function moveWatermark() {
  const left = 8 + Math.round(Math.random() * 55)
  const top = 12 + Math.round(Math.random() * 48)
  watermarkStyle.value = { left: `${left}%`, top: `${top}%` }
}

function scorePayload(score = scoreForm.score, conclusion = scoreForm.conclusion, comment = scoreForm.comment): VideoScorePayload {
  const dimensionScores: Record<string, number> = {}
  for (const item of scoreForm.dimensions) dimensionScores[item.code] = item.score
  if (Object.keys(dimensionScores).length === 0) {
    for (const item of dimensions.value) dimensionScores[item.itemCode] = 0
  }
  return { score, conclusion, comment, dimensionScores }
}

function statusTag(status: string, label: string) {
  const type = status === 'CONFIRMED' || status === 'REVIEW_COMPLETED' ? 'success' : status === 'VALIDATION_FAILED' ? 'error' : status === 'NEED_REVIEW' ? 'warning' : 'info'
  return h(NTag, { size: 'small', type, bordered: false }, { default: () => label })
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

onMounted(async () => {
  await loadOptions()
  await Promise.all([loadReviews(), loadTasks()])
})
</script>

<template>
  <n-space vertical size="large">
    <n-tabs type="line" animated>
      <n-tab-pane name="reviews" tab="视频评审">
        <n-space vertical size="large">
          <n-space justify="space-between" align="center">
            <n-space>
              <n-input v-model:value="keyword" clearable placeholder="文件名/MD5" style="width: 210px" @keyup.enter="loadReviews" />
              <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
              <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="状态" style="width: 145px" />
              <n-button type="primary" @click="loadReviews">查询</n-button>
            </n-space>
            <n-button v-if="canUpload" type="primary" @click="openUpload">上传视频</n-button>
          </n-space>
          <n-data-table :columns="reviewColumns" :data="reviews" :loading="loading" :row-key="(row: VideoReview) => row.id" :scroll-x="1180" />
        </n-space>
      </n-tab-pane>

      <n-tab-pane v-if="canScore" name="tasks" tab="我的评审">
        <n-data-table :columns="taskColumns" :data="tasks" :loading="taskLoading" :row-key="(row: VideoTask) => row.id" :scroll-x="760" />
      </n-tab-pane>
    </n-tabs>
  </n-space>

  <n-drawer v-model:show="uploadVisible" :width="560">
    <n-drawer-content title="上传教学能力视频" closable>
      <n-space vertical>
        <n-select v-model:value="uploadForm.studentId" :options="studentOptions" :disabled="selfMode" placeholder="学生" />
        <n-input v-model:value="uploadForm.assessmentYear" placeholder="考核年度" />
        <n-input-number v-model:value="uploadForm.durationSeconds" :min="1" style="width: 100%" placeholder="时长（秒）" />
        <n-upload v-model:file-list="fileList" :max="1" accept="video/mp4,.mp4" :default-upload="false" />
        <n-progress type="line" :percentage="uploadProgress" :indicator-placement="'inside'" />
      </n-space>
      <template #footer>
        <n-space justify="end">
          <n-button @click="uploadVisible = false">取消</n-button>
          <n-button type="primary" :loading="uploading" @click="uploadVideo">开始上传</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>

  <n-modal v-model:show="scoreVisible" preset="card" title="提交评分" style="width: 680px">
    <n-space vertical>
      <n-grid :cols="3" :x-gap="12" :y-gap="12">
        <n-gi v-for="item in scoreForm.dimensions" :key="item.code">
          <n-form-item :label="item.label">
            <n-input-number v-model:value="item.score" :min="0" :max="100" style="width: 100%" />
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-input-number v-model:value="scoreForm.score" :min="0" :max="100" style="width: 100%" placeholder="总分" />
      <n-select
        v-model:value="scoreForm.conclusion"
        :options="[
          { label: '合格', value: 'PASS' },
          { label: '不合格', value: 'FAIL' }
        ]"
      />
      <n-input v-model:value="scoreForm.comment" type="textarea" placeholder="评审意见" />
      <n-space justify="end">
        <n-button @click="scoreVisible = false">取消</n-button>
        <n-button type="primary" @click="saveScore">提交</n-button>
      </n-space>
    </n-space>
  </n-modal>

  <n-modal v-model:show="assignVisible" preset="dialog" title="分配评审教师">
    <n-space vertical>
      <n-select v-model:value="assignForm.reviewerIds" multiple :options="reviewerOptions" placeholder="评审教师" />
      <n-space justify="end">
        <n-button @click="assignVisible = false">取消</n-button>
        <n-button type="primary" @click="saveAssign">保存</n-button>
      </n-space>
    </n-space>
  </n-modal>

  <n-modal v-model:show="arbitrateVisible" preset="card" title="复评/仲裁" style="width: 560px">
    <n-space vertical>
      <n-radio-group v-model:value="arbitrateForm.mode">
        <n-radio-button value="thirdExpert">第三专家</n-radio-button>
        <n-radio-button value="collegeArbitrate">学院仲裁</n-radio-button>
      </n-radio-group>
      <n-select v-if="arbitrateForm.mode === 'thirdExpert'" v-model:value="arbitrateForm.reviewerId" :options="reviewerOptions" placeholder="第三专家" />
      <n-input-number v-model:value="arbitrateForm.score" :min="0" :max="100" style="width: 100%" placeholder="分数/终分" />
      <n-select
        v-model:value="arbitrateForm.conclusion"
        :options="[
          { label: '合格', value: 'PASS' },
          { label: '不合格', value: 'FAIL' }
        ]"
      />
      <n-input v-model:value="arbitrateForm.comment" type="textarea" placeholder="意见" />
      <n-space justify="end">
        <n-button @click="arbitrateVisible = false">取消</n-button>
        <n-button type="primary" @click="saveArbitrate">保存</n-button>
      </n-space>
    </n-space>
  </n-modal>

  <n-modal v-model:show="playerVisible" preset="card" title="视频播放" style="width: min(960px, 94vw)">
    <div class="player-shell">
      <video :src="playbackUrl" controls class="video-player" @play="moveWatermark" />
      <div class="watermark" :style="watermarkStyle">{{ watermarkText }}</div>
    </div>
  </n-modal>
</template>

<style scoped>
.player-shell {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #111827;
  overflow: hidden;
}

.video-player {
  width: 100%;
  height: 100%;
}

.watermark {
  position: absolute;
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
  pointer-events: none;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
  transition: left 0.4s ease, top 0.4s ease;
}
</style>
