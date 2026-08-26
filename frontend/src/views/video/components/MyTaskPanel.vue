<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage, type SelectOption } from 'naive-ui'
import { CreateOutline, PlayCircleOutline, RefreshOutline } from '@vicons/ionicons5'
import StatusTag from '@/components/StatusTag.vue'
import { statusLabel } from '@/constants/statusLabels'
import { formatDateTime } from '@/utils/format'
import { listDictItems, type DictItem } from '@/api/dict'
import {
  getVideoReview,
  listMyVideoTasks,
  playVideoReview,
  submitVideoScore,
  type VideoReview,
  type VideoScorePayload,
  type VideoTask
} from '@/api/video'

interface ScoreDimension {
  code: string
  label: string
  score: number
}

const message = useMessage()

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const lastSuccessfulQueryKey = ref('')
const playerVisible = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const tasks = ref<VideoTask[]>([])
const dimensions = ref<DictItem[]>([])
const reviewMap = ref<Record<string, VideoReview>>({})
const selectedTaskId = ref('')
const playbackUrl = ref('')
const watermarkText = ref('')
const watermarkStyle = ref({ left: '12%', top: '18%' })
let loadSequence = 0

const scoreForm = reactive({
  score: 60,
  conclusion: 'PASS' as 'PASS' | 'FAIL',
  comment: '',
  dimensions: [] as ScoreDimension[]
})

const conclusionOptions: SelectOption[] = [
  { label: '合格', value: 'PASS' },
  { label: '不合格', value: 'FAIL' }
]

const selectedTask = computed(() => tasks.value.find((item) => item.id === selectedTaskId.value) || null)
const selectedReview = computed(() => selectedTask.value ? reviewMap.value[selectedTask.value.videoReviewId] || null : null)
const currentQueryKey = computed(() => `${page.value}:${size.value}`)
const hasLoadedSuccessfully = computed(() => Boolean(lastSuccessfulQueryKey.value))
const taskDataFresh = computed(() =>
  !loading.value && !loadError.value && lastSuccessfulQueryKey.value === currentQueryKey.value
)
const taskDataStale = computed(() => hasLoadedSuccessfully.value && !taskDataFresh.value)
const initialLoadFailed = computed(() => Boolean(loadError.value) && !hasLoadedSuccessfully.value)
const scoreLocked = computed(() => saving.value || !taskDataFresh.value)
// P1-1 真分页：tasks.value 现为「当页」而非全量，故 pendingCount/submittedCount 只反映当页计数，
// 不再是该评审教师的全量待评分/已提交总数。后端未提供按 submitted 分组计数的聚合接口，暂不新增
// （超出本次两端点分页改造范围），按 rollout 约定标注于此。
const pendingCount = computed(() => tasks.value.filter((item) => item.submitted === 0).length)
const submittedCount = computed(() => tasks.value.filter((item) => item.submitted === 1).length)

onMounted(loadData)

async function loadData(options: { allowWhileSaving?: boolean } = {}) {
  if (saving.value && !options.allowWhileSaving) return
  const sequence = ++loadSequence
  const queryKey = currentQueryKey.value
  loading.value = true
  loadError.value = ''
  try {
    const [taskRes, dimensionRes] = await Promise.all([
      listMyVideoTasks(undefined, page.value, size.value),
      listDictItems('video_score_dimension', true)
    ])
    const nextTasks = taskRes.data.records
    const nextDimensions = dimensionRes.data.slice(0, 9)
    const nextReviewMap = await loadReviewDetails(nextTasks)
    if (sequence !== loadSequence) return

    tasks.value = nextTasks
    total.value = taskRes.data.total
    dimensions.value = nextDimensions
    reviewMap.value = nextReviewMap
    const nextTaskId = selectedTaskId.value && nextTasks.some((item) => item.id === selectedTaskId.value)
      ? selectedTaskId.value
      : nextTasks.find((item) => item.submitted === 0)?.id || nextTasks[0]?.id || ''
    selectedTaskId.value = nextTaskId
    const nextSelectedTask = nextTasks.find((item) => item.id === nextTaskId)
    if (nextSelectedTask) applyTaskToForm(nextSelectedTask)
    lastSuccessfulQueryKey.value = queryKey
  } catch (error) {
    if (sequence !== loadSequence) return
    loadError.value = errorText(error, '评审任务加载失败')
    showError(error, '评审任务加载失败')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function onPageChange(next: number) {
  if (saving.value) return
  page.value = next
  void loadData()
}

function onPageSizeChange(next: number) {
  if (saving.value) return
  size.value = next
  page.value = 1
  void loadData()
}

async function loadReviewDetails(nextTasks: VideoTask[]) {
  const ids = Array.from(new Set(nextTasks.map((item) => item.videoReviewId)))
  const entries = await Promise.all(
    ids.map(async (id) => {
      const res = await getVideoReview(id)
      return [id, res.data] as const
    })
  )
  const next: Record<string, VideoReview> = {}
  for (const [id, review] of entries) {
    next[id] = review
  }
  return next
}

function selectTask(task: VideoTask) {
  if (saving.value || !taskDataFresh.value) return
  selectedTaskId.value = task.id
  applyTaskToForm(task)
}

function applyTaskToForm(task: VideoTask) {
  scoreForm.score = Number(task.score ?? 60)
  scoreForm.conclusion = task.conclusion || 'PASS'
  scoreForm.comment = task.comment || ''
  const savedScores = task.dimensionScores || {}
  const rows = dimensions.value.map((item) => ({
    code: item.itemCode,
    label: item.itemValue,
    score: Number(savedScores[item.itemCode] ?? 0)
  }))
  scoreForm.dimensions = rows.length
    ? rows
    : Object.entries(savedScores).map(([code, score]) => ({ code, label: code, score: Number(score ?? 0) }))
}

async function submitScore() {
  if (saving.value) return
  if (!taskDataFresh.value) {
    message.warning('任务列表尚未刷新成功，请重试后再提交')
    return
  }
  const task = selectedTask.value
  if (!task) return
  if (task.submitted === 1) {
    message.warning('该任务已提交评分')
    return
  }
  saving.value = true
  try {
    await submitVideoScore(task.id, scorePayload())
    message.success('评分已提交')
    await loadData({ allowWhileSaving: true })
  } catch (error) {
    showError(error, '评分提交失败')
  } finally {
    saving.value = false
  }
}

async function openPlayer() {
  if (saving.value || !taskDataFresh.value) return
  const task = selectedTask.value
  if (!task) return
  try {
    const res = await playVideoReview(task.videoReviewId)
    playbackUrl.value = res.data.url
    watermarkText.value = res.data.watermarkText
    moveWatermark()
    playerVisible.value = true
  } catch (error) {
    showError(error, '播放失败')
  }
}

function scorePayload(): VideoScorePayload {
  const dimensionScores: Record<string, number> = {}
  for (const item of scoreForm.dimensions) dimensionScores[item.code] = item.score
  return {
    score: scoreForm.score,
    conclusion: scoreForm.conclusion,
    comment: scoreForm.comment,
    dimensionScores
  }
}

function reviewOf(task: VideoTask) {
  return reviewMap.value[task.videoReviewId]
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

function moveWatermark() {
  const left = 8 + Math.round(Math.random() * 55)
  const top = 12 + Math.round(Math.random() * 48)
  watermarkStyle.value = { left: `${left}%`, top: `${top}%` }
}

function showError(error: unknown, fallback: string) {
  const detail = errorText(error, fallback)
  message.error(detail || fallback)
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error ? error.message || fallback : fallback
}
</script>

<template>
  <section class="review-workbench page-section">
    <n-card :bordered="false" class="task-list-card">
      <template #header>
        <div class="panel-title">
          <span>任务列表</span>
          <n-tag v-if="hasLoadedSuccessfully" size="small" :bordered="false">待评分 {{ pendingCount }}</n-tag>
        </div>
      </template>
      <template #header-extra>
        <n-button secondary size="small" :loading="loading" :disabled="saving" @click="loadData()">
          <template #icon><n-icon :component="RefreshOutline" /></template>
          刷新
        </n-button>
      </template>

      <n-alert v-if="taskDataStale" type="error" :bordered="false" class="task-stale-alert" role="alert">
        <div class="task-stale-content">
          <span>{{ loadError
            ? `${loadError}。以下仍显示上次成功加载的结果，暂不可提交评分。`
            : '任务正在刷新，以下为上次成功加载的结果，暂不可提交评分。' }}</span>
          <n-button v-if="loadError" size="small" type="error" secondary :loading="loading" :disabled="saving" @click="loadData()">重试</n-button>
        </div>
      </n-alert>
      <n-result v-if="initialLoadFailed" status="error" title="评审任务加载失败" :description="loadError" role="alert">
        <template #footer>
          <n-button type="primary" :loading="loading" :disabled="saving" @click="loadData()">重试</n-button>
        </template>
      </n-result>
      <n-spin v-else :show="loading">
        <n-skeleton v-if="loading && !tasks.length" text :repeat="6" />
        <n-empty v-else-if="!tasks.length" description="暂无评审任务" />
        <n-list v-else hoverable clickable class="task-list">
          <n-list-item
            v-for="task in tasks"
            :key="task.id"
            :class="{ 'task-item--active': task.id === selectedTaskId }"
            role="button"
            :tabindex="saving || !taskDataFresh ? -1 : 0"
            :aria-pressed="task.id === selectedTaskId"
            :aria-disabled="saving || !taskDataFresh"
            @click="selectTask(task)"
            @keydown.space.prevent
            @keyup.enter.prevent="selectTask(task)"
            @keyup.space.prevent="selectTask(task)"
          >
            <div class="task-row">
              <div class="task-main">
                <strong>{{ reviewOf(task)?.studentNo || task.videoReviewId }}</strong>
                <span>{{ reviewOf(task)?.studentName || reviewerRoleText(task.reviewerRole) }}</span>
              </div>
              <StatusTag :text="task.submitted === 1 ? '已提交' : '待评分'" />
            </div>
            <div class="task-meta">
              <span>{{ reviewOf(task)?.statusLabel || statusLabel(reviewOf(task)?.status) || '-' }}</span>
              <span class="mono tabular-nums">{{ formatDateTime(task.submitTime) }}</span>
            </div>
          </n-list-item>
        </n-list>
        <n-pagination
          v-if="total > 0"
          class="task-list-pagination"
          :page="page"
          :page-size="size"
          :item-count="total"
          :disabled="saving || loading"
          show-size-picker
          :page-sizes="[10, 20, 50, 100]"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </n-spin>
    </n-card>

    <n-card v-if="hasLoadedSuccessfully" :bordered="false" class="score-card">
      <template #header>
        <div class="panel-title">
          <n-icon :component="CreateOutline" />
          <span>评分区</span>
        </div>
      </template>
      <template #header-extra>
        <n-space size="small">
          <n-tag size="small" :bordered="false">已提交 {{ submittedCount }}</n-tag>
          <n-button secondary size="small" :disabled="!selectedTask || scoreLocked" @click="openPlayer">
            <template #icon><n-icon :component="PlayCircleOutline" /></template>
            播放
          </n-button>
        </n-space>
      </template>

      <n-empty v-if="!selectedTask" description="请选择左侧任务" />
      <div v-else class="score-layout">
        <div class="summary-strip">
          <div>
            <span>学生</span>
            <strong>{{ selectedReview?.studentNo || '-' }} / {{ selectedReview?.studentName || '-' }}</strong>
          </div>
          <div>
            <span>评审角色</span>
            <strong>{{ reviewerRoleText(selectedTask.reviewerRole) }}</strong>
          </div>
          <div>
            <span>当前状态</span>
            <StatusTag :value="selectedReview?.status" :text="selectedReview?.statusLabel || statusLabel(selectedReview?.status)" />
          </div>
        </div>

        <n-table :bordered="false" size="small" class="dimension-table">
          <thead>
            <tr>
              <th>评分维度</th>
              <th class="score-column">得分</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in scoreForm.dimensions" :key="item.code">
              <td>{{ item.label }}</td>
              <td>
                <n-input-number
                  v-model:value="item.score"
                  :min="0"
                  :max="100"
                  :disabled="selectedTask.submitted === 1 || scoreLocked"
                  :input-props="{ 'aria-label': item.label + '得分' }"
                  style="width: 140px"
                />
              </td>
            </tr>
          </tbody>
        </n-table>

        <div class="score-footer">
          <div class="score-total">
            <span>总分</span>
            <n-input-number
              v-model:value="scoreForm.score"
              :min="0"
              :max="100"
              :disabled="selectedTask.submitted === 1 || scoreLocked"
              :input-props="{ 'aria-label': '总分' }"
              class="score-total-input"
            />
          </div>
          <div class="score-fields">
            <n-radio-group v-model:value="scoreForm.conclusion" :disabled="selectedTask.submitted === 1 || scoreLocked" aria-label="评审结论">
              <n-radio-button v-for="item in conclusionOptions" :key="String(item.value)" :value="item.value">
                {{ item.label }}
              </n-radio-button>
            </n-radio-group>
            <n-input
              v-model:value="scoreForm.comment"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 6 }"
              :disabled="selectedTask.submitted === 1 || scoreLocked"
              :input-props="{ 'aria-label': '评审意见' }"
              placeholder="评审意见"
            />
            <n-space justify="end">
              <StatusTag v-if="selectedTask.submitted === 1" :text="conclusionText(selectedTask.conclusion)" />
              <n-button v-else type="primary" :loading="saving" :disabled="scoreLocked" @click="submitScore">提交评分</n-button>
            </n-space>
          </div>
        </div>
      </div>
    </n-card>

    <n-modal v-model:show="playerVisible" preset="card" title="视频播放" style="width: min(960px, 94vw)">
      <div class="player-shell">
        <video :src="playbackUrl" controls class="video-player" @play="moveWatermark" @timeupdate="moveWatermark" />
        <div class="watermark" :style="watermarkStyle">{{ watermarkText }}</div>
      </div>
    </n-modal>
  </section>
</template>

<style scoped>
.review-workbench {
  display: grid;
  grid-template-columns: minmax(280px, 34%) minmax(0, 1fr);
  gap: var(--space-4);
  align-items: start;
}

.panel-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.task-list-card,
.score-card {
  min-width: 0;
}

.task-stale-alert {
  margin-bottom: var(--space-3);
}

.task-stale-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.task-list {
  margin: calc(var(--space-3) * -1);
}

.task-list :deep(.n-list-item) {
  padding: var(--space-3);
  border-radius: var(--radius-control);
}

.task-list :deep(.n-list-item[role='button']:focus-visible) {
  outline: 2px solid var(--brand);
  outline-offset: -2px;
}

.task-list-pagination {
  justify-content: flex-end;
  margin-top: var(--space-3);
  padding: 0 var(--space-3);
}

.task-list :deep(.n-list-item.task-item--active) {
  background: var(--brand-soft);
}

.task-row,
.task-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.task-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.task-main strong,
.task-main span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-main span,
.task-meta {
  color: var(--text-muted);
  font-size: 12px;
}

.score-layout {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-card);
  background: var(--surface-muted);
}

.summary-strip div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.summary-strip span {
  color: var(--text-muted);
  font-size: 12px;
}

.summary-strip strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text);
}

.dimension-table th {
  background: var(--surface-muted);
}

.score-column {
  width: 170px;
}

.score-footer {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: var(--space-4);
  align-items: start;
}

.score-total {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
  border-radius: var(--radius-card);
  background: var(--brand-soft);
  color: var(--brand);
}

.score-total span {
  color: var(--text-secondary);
  font-size: 13px;
}

.score-total-input :deep(input) {
  height: 52px;
  font-family: var(--font-mono);
  font-size: 30px;
  font-weight: 650;
  text-align: center;
}

.score-fields {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-3);
}

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

@media (max-width: 960px) {
  .review-workbench,
  .score-footer,
  .summary-strip {
    grid-template-columns: 1fr;
  }

  .task-stale-content {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
