<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import {
  NButton,
  NPopconfirm,
  NSpace,
  useMessage,
  type DataTableColumns,
  type SelectOption,
  type UploadFileInfo
} from 'naive-ui'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import { renderTableActions } from '@/utils/tableActions'
import { listDictItems, type DictItem } from '@/api/dict'
import { listStudents, type Student } from '@/api/student'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import {
  addReviewerGroupMember,
  arbitrateVideoReview,
  assignVideoReview,
  assignVideoReviewGroup,
  confirmVideoReview,
  createReviewerGroup,
  deleteReviewerGroup,
  initVideoUpload,
  listReviewerCandidates,
  listMyVideoTasks,
  listReviewerGroups,
  listVideoReviews,
  mergeVideoUpload,
  playVideoReview,
  removeReviewerGroupMember,
  returnVideoReview,
  submitVideoScore,
  thirdVideoReview,
  updateReviewerGroup,
  uploadVideoChunk,
  type ReviewerCandidate,
  type ReviewerGroup,
  type ReviewerGroupPayload,
  type VideoReview,
  type VideoScorePayload,
  type VideoTask
} from '@/api/video'

interface DimensionRow {
  code: string
  label: string
  score: number
}

type AssignMode = 'person' | 'group'

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const taskLoading = ref(false)
const groupLoading = ref(false)
const uploadVisible = ref(false)
const scoreVisible = ref(false)
const assignVisible = ref(false)
const arbitrateVisible = ref(false)
const playerVisible = ref(false)
const returnVisible = ref(false)
const groupVisible = ref(false)
const memberVisible = ref(false)
const keyword = ref('')
const assessmentYear = ref(yearStore.assessmentYear)
const statusFilter = ref<string | null>(null)
const reviews = ref<VideoReview[]>([])
const tasks = ref<VideoTask[]>([])
const students = ref<Student[]>([])
const reviewers = ref<ReviewerCandidate[]>([])
const dimensions = ref<DictItem[]>([])
const groups = ref<ReviewerGroup[]>([])
const fileList = ref<UploadFileInfo[]>([])
const uploadProgress = ref(0)
const uploading = ref(false)
const currentScoreTask = ref<VideoTask | null>(null)
const assigning = ref<VideoReview | null>(null)
const arbitrating = ref<VideoReview | null>(null)
const returning = ref<VideoReview | null>(null)
const editingGroup = ref<ReviewerGroup | null>(null)
const memberGroup = ref<ReviewerGroup | null>(null)
const playbackUrl = ref('')
const watermarkText = ref('')
const watermarkStyle = ref({ left: '12%', top: '18%' })

const canUpload = computed(() => userStore.hasPerm('video:upload'))
const canScore = computed(() => userStore.hasPerm('video:score'))
const canAssign = computed(() => userStore.hasPerm('video:assign'))
const canArbitrate = computed(() => userStore.hasPerm('video:arbitrate'))
const canConfirm = computed(() => userStore.hasPerm('video:confirm'))
const canPlay = computed(() => userStore.hasPerm('video:play'))
const canViewStudents = computed(() => userStore.hasPerm('student:view'))
const canListReviews = computed(() => canUpload.value || canAssign.value || canArbitrate.value || canConfirm.value || canPlay.value)
const hasVisibleSection = computed(() => canListReviews.value || canScore.value || canAssign.value)
const selfMode = computed(() => canUpload.value && !canAssign.value && !canScore.value)

const uploadForm = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear,
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
  mode: 'person' as AssignMode,
  reviewerIds: [] as string[],
  groupId: ''
})

const arbitrateForm = reactive({
  mode: 'thirdExpert' as 'thirdExpert' | 'collegeArbitrate',
  reviewerId: '',
  score: 60,
  conclusion: 'PASS' as 'PASS' | 'FAIL',
  comment: ''
})

const returnForm = reactive({
  comment: ''
})

const groupForm = reactive<ReviewerGroupPayload>({
  name: '',
  status: 'ENABLED'
})

const memberForm = reactive({
  reviewerUserId: ''
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

const reviewColumns: DataTableColumns<VideoReview> = [
  { title: '学号', key: 'studentNo', minWidth: 130, ellipsis: { tooltip: true }, render: (row) => h('span', { class: 'mono' }, row.studentNo || '-') },
  { title: '姓名', key: 'studentName', minWidth: 110, ellipsis: { tooltip: true } },
  { title: '年度', key: 'assessmentYear', width: 96, render: (row) => h('span', { class: 'mono' }, row.assessmentYear) },
  { title: '视频文件', key: 'videoFileName', minWidth: 190, ellipsis: { tooltip: true } },
  { title: '时长', key: 'durationSeconds', width: 86, render: (row) => h('span', { class: 'numeric' }, `${row.durationSeconds || 0}s`) },
  { title: '状态', key: 'status', width: 108, render: (row) => h(StatusTag, { text: row.statusLabel || row.status }) },
  { title: '终分', key: 'finalScore', width: 78, render: (row) => h('span', { class: 'numeric' }, String(row.finalScore ?? '-')) },
  { title: '结论', key: 'finalConclusion', width: 88, render: (row) => h(StatusTag, { text: conclusionText(row.finalConclusion) }) },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 240,
    render: (row) =>
      {
        const actions = []
        if (canPlay.value) {
          actions.push(h(NButton, { size: 'small', type: 'primary', onClick: () => openPlayer(row) }, { default: () => '播放' }))
        }
        if (canUpload.value && canReupload(row)) {
          actions.push(h(NButton, { size: 'small', quaternary: true, type: row.status === 'RETURNED' ? 'warning' : 'default', onClick: () => openUpload(row) }, { default: () => row.status === 'RETURNED' ? '重新上传' : '上传' }))
        }
        if (canAssign.value) {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openAssign(row) }, { default: () => '指派' }))
        }
        if (canArbitrate.value && row.status === 'NEED_REVIEW') {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => openArbitrate(row) }, { default: () => '复评/仲裁' }))
        }
        if (canConfirm.value && row.status === 'REVIEW_COMPLETED') {
          actions.push(h(NButton, { size: 'small', quaternary: true, onClick: () => confirm(row) }, { default: () => '确认' }))
        }
        if ((canConfirm.value || canArbitrate.value) && row.status !== 'CONFIRMED') {
          actions.push(h(NButton, { size: 'small', quaternary: true, type: 'warning', onClick: () => openReturn(row) }, { default: () => '退回' }))
        }
        return renderTableActions(actions)
      }
  }
]

const taskColumns: DataTableColumns<VideoTask> = [
  { title: '评审记录', key: 'videoReviewId', minWidth: 150, render: (row) => h('span', { class: 'mono' }, row.videoReviewId) },
  { title: '评审角色', key: 'reviewerRole', width: 120, render: (row) => reviewerRoleText(row.reviewerRole) },
  { title: '提交状态', key: 'submitted', width: 100, render: (row) => h(StatusTag, { text: row.submitted === 1 ? '已提交' : '待评分' }) },
  { title: '分数', key: 'score', width: 78, render: (row) => h('span', { class: 'numeric' }, String(row.score ?? '-')) },
  { title: '结论', key: 'conclusion', width: 90, render: (row) => h(StatusTag, { text: conclusionText(row.conclusion) }) },
  { title: '提交时间', key: 'submitTime', minWidth: 160, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 150,
    render: (row) =>
      renderTableActions([
        h(NButton, { size: 'small', type: 'primary', onClick: () => openPlayerByTask(row) }, { default: () => '播放' }),
        row.submitted === 0 ? h(NButton, { size: 'small', quaternary: true, onClick: () => openScore(row) }, { default: () => '评分' }) : null
      ])
  }
]

const groupColumns: DataTableColumns<ReviewerGroup> = [
  { title: '组名', key: 'name', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '成员数', key: 'memberCount', width: 90, render: (row) => h('span', { class: 'numeric' }, String(row.memberCount)) },
  { title: '状态', key: 'status', width: 90, render: (row) => h(StatusTag, { text: row.status === 'ENABLED' ? '启用' : '停用' }) },
  {
    title: '成员',
    key: 'members',
    minWidth: 260,
    render: (row) => row.members.map((item) => item.reviewerName || item.workNo || item.reviewerUserId).join('、') || '-'
  },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 190,
    render: (row) =>
      renderTableActions([
        h(NButton, { size: 'small', type: 'primary', onClick: () => openGroup(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => openMember(row) }, { default: () => '成员' }),
        h(
          NPopconfirm,
          { onPositiveClick: () => removeGroup(row) },
          {
            trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
            default: () => '确认删除该评审组？'
          }
        )
      ])
  }
]

async function loadReviews() {
  if (!canListReviews.value) {
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

async function loadTasks() {
  if (!canScore.value) return
  taskLoading.value = true
  try {
    const res = await listMyVideoTasks()
    tasks.value = res.data.records
  } catch (error) {
    showError(error, '评审任务加载失败')
  } finally {
    taskLoading.value = false
  }
}

async function loadGroups() {
  if (!canAssign.value) return
  groupLoading.value = true
  try {
    const res = await listReviewerGroups()
    groups.value = res.data
  } catch (error) {
    showError(error, '评审组加载失败')
  } finally {
    groupLoading.value = false
  }
}

async function loadOptions() {
  const [studentRes, dimensionRes, reviewerRes] = await Promise.all([
    canUpload.value && canViewStudents.value ? listStudents() : Promise.resolve(null),
    canScore.value || canArbitrate.value ? listDictItems('video_score_dimension', true) : Promise.resolve(null),
    canAssign.value ? listReviewerCandidates() : Promise.resolve(null)
  ])
  students.value = studentRes ? (selfMode.value ? studentRes.data.records.slice(0, 1) : studentRes.data.records) : []
  dimensions.value = dimensionRes?.data.slice(0, 9) || []
  reviewers.value = reviewerRes?.data || []
}

function openUpload(row?: VideoReview) {
  uploadForm.studentId = row?.studentId || (selfMode.value ? userStore.currentUser?.studentId || students.value[0]?.id || '' : '')
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
  try {
    await submitVideoScore(currentScoreTask.value.id, scorePayload())
    message.success('评分已提交')
    scoreVisible.value = false
    await Promise.all([loadTasks(), loadReviews()])
  } catch (error) {
    showError(error, '评分提交失败')
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
        ...scorePayload(arbitrateForm.score, arbitrateForm.conclusion, arbitrateForm.comment),
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
    showError(error, '播放鉴权失败')
  }
}

async function openPlayerByTask(task: VideoTask) {
  const row = reviews.value.find((item) => item.id === task.videoReviewId)
  if (row) await openPlayer(row)
  else {
    try {
      const res = await playVideoReview(task.videoReviewId)
      playbackUrl.value = res.data.url
      watermarkText.value = res.data.watermarkText
      moveWatermark()
      playerVisible.value = true
    } catch (error) {
      showError(error, '播放鉴权失败')
    }
  }
}

function openGroup(row?: ReviewerGroup) {
  editingGroup.value = row || null
  groupForm.name = row?.name || ''
  groupForm.status = row?.status || 'ENABLED'
  groupVisible.value = true
}

async function saveGroup() {
  if (!groupForm.name.trim()) {
    message.error('请输入评审组名称')
    return
  }
  try {
    if (editingGroup.value) await updateReviewerGroup(editingGroup.value.id, groupForm)
    else await createReviewerGroup(groupForm)
    message.success('评审组已保存')
    groupVisible.value = false
    await loadGroups()
  } catch (error) {
    showError(error, '评审组保存失败')
  }
}

async function removeGroup(row: ReviewerGroup) {
  try {
    await deleteReviewerGroup(row.id)
    message.success('评审组已删除')
    await loadGroups()
  } catch (error) {
    showError(error, '评审组删除失败')
  }
}

function openMember(row: ReviewerGroup) {
  memberGroup.value = row
  memberForm.reviewerUserId = ''
  memberVisible.value = true
}

async function addMember() {
  if (!memberGroup.value || !memberForm.reviewerUserId) {
    message.error('请选择评审教师')
    return
  }
  try {
    await addReviewerGroupMember(memberGroup.value.id, memberForm.reviewerUserId)
    message.success('成员已添加')
    memberForm.reviewerUserId = ''
    await loadGroups()
    memberGroup.value = groups.value.find((item) => item.id === memberGroup.value?.id) || memberGroup.value
  } catch (error) {
    showError(error, '成员添加失败')
  }
}

async function removeMember(memberId: string) {
  if (!memberGroup.value) return
  try {
    await removeReviewerGroupMember(memberGroup.value.id, memberId)
    message.success('成员已移除')
    await loadGroups()
    memberGroup.value = groups.value.find((item) => item.id === memberGroup.value?.id) || memberGroup.value
  } catch (error) {
    showError(error, '成员移除失败')
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

function canReupload(row: VideoReview) {
  return ['WAIT_UPLOAD', 'VALIDATION_FAILED', 'RETURNED'].includes(row.status)
}

function reviewerRoleText(role: string) {
  if (role === 'REVIEWER') return '初评教师'
  if (role === 'THIRD_EXPERT') return '第三专家'
  return role || '-'
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

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

onMounted(async () => {
  await loadOptions()
  const tasks: Promise<void>[] = []
  if (canListReviews.value) tasks.push(loadReviews())
  if (canScore.value) tasks.push(loadTasks())
  if (canAssign.value) tasks.push(loadGroups())
  await Promise.all(tasks)
})

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    assessmentYear.value = year
    if (!uploadVisible.value) uploadForm.assessmentYear = year
    if (canListReviews.value) await loadReviews()
  }
)
</script>

<template>
  <PageContainer title="视频评审" description="教学能力视频上传、盲评评分、复评仲裁、退回重传与评审组指派。">
    <template #actions>
      <n-space>
        <n-button v-if="canListReviews" secondary @click="loadReviews">刷新</n-button>
        <n-button v-if="canUpload" type="primary" @click="openUpload()">上传视频</n-button>
      </n-space>
    </template>

    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的视频分区" class="page-section" />

    <n-tabs v-if="hasVisibleSection" type="line" animated>
      <n-tab-pane v-if="canListReviews" name="reviews" tab="评审管理">
        <n-grid :cols="5" :x-gap="12" responsive="screen" class="page-section">
          <n-gi><n-card size="small" :bordered="false"><n-statistic label="视频总数" :value="statusSummary.total" /></n-card></n-gi>
          <n-gi><n-card size="small" :bordered="false"><n-statistic label="待评审" :value="statusSummary.wait" /></n-card></n-gi>
          <n-gi><n-card size="small" :bordered="false"><n-statistic label="评审中" :value="statusSummary.reviewingCount" /></n-card></n-gi>
          <n-gi><n-card size="small" :bordered="false"><n-statistic label="需复评" :value="statusSummary.need" /></n-card></n-gi>
          <n-gi><n-card size="small" :bordered="false"><n-statistic label="已退回" :value="statusSummary.returned" /></n-card></n-gi>
        </n-grid>

        <n-card :bordered="false" size="small" class="page-section">
          <n-space class="filters" :size="10">
            <n-input v-model:value="keyword" clearable placeholder="学生 / 文件名 / MD5" style="width: 230px" @keyup.enter="loadReviews" />
            <n-input v-model:value="assessmentYear" placeholder="考核年度" style="width: 120px" />
            <n-select v-model:value="statusFilter" clearable :options="statusOptions" placeholder="状态" style="width: 150px" />
            <n-button type="primary" @click="loadReviews">查询</n-button>
          </n-space>
        </n-card>

        <n-alert v-if="reviews.some((item) => item.status === 'RETURNED')" type="warning" :bordered="false" class="page-section">
          已退回视频可由学生重新上传；评审中、需复评、已确认等状态仍按系统校验禁止重传。
        </n-alert>

        <n-data-table
          :columns="reviewColumns"
          :data="reviews"
          :loading="loading"
          :row-key="(row: VideoReview) => row.id"
          :scroll-x="1600"
          :pagination="{ pageSize: 10 }"
          striped
        />
      </n-tab-pane>

      <n-tab-pane v-if="canScore" name="tasks" tab="我的评审">
        <n-space vertical>
          <n-button secondary @click="loadTasks">刷新任务</n-button>
          <n-data-table
            :columns="taskColumns"
            :data="tasks"
            :loading="taskLoading"
            :row-key="(row: VideoTask) => row.id"
            :scroll-x="900"
            :pagination="{ pageSize: 10 }"
            striped
          />
        </n-space>
      </n-tab-pane>

      <n-tab-pane v-if="canAssign" name="groups" tab="评审组">
        <n-space vertical>
          <n-space justify="space-between">
            <n-button secondary @click="loadGroups">刷新评审组</n-button>
            <n-button type="primary" @click="openGroup()">新增评审组</n-button>
          </n-space>
          <n-data-table
            :columns="groupColumns"
            :data="groups"
            :loading="groupLoading"
            :row-key="(row: ReviewerGroup) => row.id"
            :scroll-x="980"
            :pagination="{ pageSize: 10 }"
            striped
          />
        </n-space>
      </n-tab-pane>
    </n-tabs>

    <n-drawer v-model:show="uploadVisible" :width="580">
      <n-drawer-content title="上传教学能力视频" closable>
        <n-space vertical>
          <n-alert type="info" :bordered="false">
          仅退回、待上传或校验失败状态显示重传入口；其他状态由系统校验拒绝。
          </n-alert>
          <n-select v-model:value="uploadForm.studentId" :options="studentOptions" :disabled="selfMode" filterable placeholder="学生" />
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

    <n-modal v-model:show="scoreVisible" preset="card" title="提交评分" style="width: 720px">
      <n-space vertical>
        <n-grid :cols="3" :x-gap="12" :y-gap="12" responsive="screen">
          <n-gi v-for="item in scoreForm.dimensions" :key="item.code">
            <n-form-item :label="item.label">
              <n-input-number v-model:value="item.score" :min="0" :max="100" style="width: 100%" />
            </n-form-item>
          </n-gi>
        </n-grid>
        <n-input-number v-model:value="scoreForm.score" :min="0" :max="100" style="width: 100%" placeholder="总分" />
        <n-select v-model:value="scoreForm.conclusion" :options="conclusionOptions" />
        <n-input v-model:value="scoreForm.comment" type="textarea" placeholder="评审意见" />
        <n-space justify="end">
          <n-button @click="scoreVisible = false">取消</n-button>
          <n-button type="primary" @click="saveScore">提交</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal v-model:show="assignVisible" preset="card" title="指派评审" style="width: 620px">
      <n-space vertical>
        <n-alert v-if="assigning" type="info" :bordered="false">
          {{ assigning.studentNo }} / {{ assigning.studentName }} / 当前状态：{{ assigning.statusLabel }}
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
          {{ returning.studentNo }} / {{ returning.studentName }}。已确认视频不可退回；其他状态由系统状态校验。
        </n-alert>
        <n-input v-model:value="returnForm.comment" type="textarea" placeholder="请输入退回意见，学生重传时可据此修改" />
        <n-space justify="end">
          <n-button @click="returnVisible = false">取消</n-button>
          <n-button type="warning" @click="saveReturn">退回</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal v-model:show="groupVisible" preset="dialog" :title="editingGroup ? '编辑评审组' : '新增评审组'">
      <n-space vertical>
        <n-input v-model:value="groupForm.name" placeholder="组名" />
        <n-select
          v-model:value="groupForm.status"
          :options="[
            { label: '启用', value: 'ENABLED' },
            { label: '停用', value: 'DISABLED' }
          ]"
        />
        <n-space justify="end">
          <n-button @click="groupVisible = false">取消</n-button>
          <n-button type="primary" @click="saveGroup">保存</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal v-model:show="memberVisible" preset="card" :title="memberGroup ? `成员管理：${memberGroup.name}` : '成员管理'" style="width: 680px">
      <n-space vertical>
        <n-space>
          <n-select v-model:value="memberForm.reviewerUserId" filterable :options="reviewerOptions" placeholder="本院评审教师" style="width: 320px" />
          <n-button type="primary" @click="addMember">添加</n-button>
        </n-space>
        <n-list bordered>
          <n-list-item v-for="member in memberGroup?.members || []" :key="member.id">
            <n-space justify="space-between" align="center" style="width: 100%">
              <span>{{ member.reviewerName || member.workNo || member.reviewerUserId }}</span>
              <n-button size="small" quaternary type="error" @click="removeMember(member.id)">移除</n-button>
            </n-space>
          </n-list-item>
        </n-list>
      </n-space>
    </n-modal>

    <n-modal v-model:show="playerVisible" preset="card" title="视频播放" style="width: min(960px, 94vw)">
      <div class="player-shell">
        <video :src="playbackUrl" controls class="video-player" @play="moveWatermark" @timeupdate="moveWatermark" />
        <div class="watermark" :style="watermarkStyle">{{ watermarkText }}</div>
      </div>
    </n-modal>
  </PageContainer>
</template>

<style scoped>
.filters {
  flex-wrap: wrap;
}

.player-shell {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: var(--video-bg);
  overflow: hidden;
  border-radius: var(--radius-card);
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
