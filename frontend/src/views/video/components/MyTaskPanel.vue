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
// P1-1 真分页：tasks.value 现为「当页」而非全量，故 pendingCount/submittedCount 只反映当页计数，
// 不再是该评审教师的全量待评分/已提交总数。后端未提供按 submitted 分组计数的聚合接口，暂不新增
// （超出本次两端点分页改造范围），按 rollout 约定标注于此。
const pendingCount = computed(() => tasks.value.filter((item) => item.submitted === 0).length)
const submittedCount = computed(() => tasks.value.filter((item) => item.submitted === 1).length)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [taskRes, dimensionRes] = await Promise.all([
      listMyVideoTasks(undefined, page.value, size.value),
      listDictItems('video_score_dimension', true)
    ])
    tasks.value = taskRes.data.records
    total.value = taskRes.data.total
    dimensions.value = dimensionRes.data.slice(0, 9)
    await loadReviewDetails()
    if (!selectedTaskId.value || !tasks.value.some((item) => item.id === selectedTaskId.value)) {
      selectedTaskId.value = tasks.value.find((item) => item.submitted === 0)?.id || tasks.value[0]?.id || ''
    }
    if (selectedTask.value) selectTask(selectedTask.value)
  } catch (error) {
    showError(error, '评审任务加载失败')
  } finally {
    loading.value = false
  }
}

function onPageChange(next: number) {
  page.value = next
  void loadData()
}

function onPageSizeChange(next: number) {
  size.value = next
  page.value = 1
  void loadData()
}

async function loadReviewDetails() {
  const ids = Array.from(new Set(tasks.value.map((item) => item.videoReviewId)))
  const entries = await Promise.all(
    ids.map(async (id) => {
      try {
        const res = await getVideoReview(id)
        return [id, res.data] as const
      } catch {
        return [id, null] as const
      }
    })
  )
  const next: Record<string, VideoReview> = {}
  for (const [id, review] of entries) {
    if (review) next[id] = review
  }
  reviewMap.value = next
}

function selectTask(task: VideoTask) {
  selectedTaskId.value = task.id
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
    await loadData()
  } catch (error) {
    showError(error, '评分提交失败')
  } finally {
    saving.value = false
  }
}

async function openPlayer() {
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
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}
</script>

<template>
  <section class="review-workbench page-section">
    <n-card :bordered="false" class="task-list-card">
      <template #header>
        <div class="panel-title">
          <span>任务列表</span>
          <n-tag size="small" :bordered="false">待评分 {{ pendingCount }}</n-tag>
        </div>
      </template>
      <template #header-extra>
        <n-button secondary size="small" :loading="loading" @click="loadData">
          <template #icon><n-icon :component="RefreshOutline" /></template>
          刷新
        </n-button>
      </template>

      <n-spin :show="loading">
        <n-empty v-if="!tasks.length" description="暂无评审任务" />
        <n-list v-else hoverable clickable class="task-list">
          <n-list-item
            v-for="task in tasks"
            :key="task.id"
            :class="{ 'task-item--active': task.id === selectedTaskId }"
            @click="selectTask(task)"
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
          show-size-picker
          :page-sizes="[10, 20, 50, 100]"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </n-spin>
    </n-card>

    <n-card :bordered="false" class="score-card">
      <template #header>
        <div class="panel-title">
          <n-icon :component="CreateOutline" />
          <span>评分区</span>
        </div>
      </template>
      <template #header-extra>
        <n-space size="small">
          <n-tag size="small" :bordered="false">已提交 {{ submittedCount }}</n-tag>
          <n-button secondary size="small" :disabled="!selectedTask" @click="openPlayer">
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
                <n-input-number v-model:value="item.score" :min="0" :max="100" :disabled="selectedTask.submitted === 1" style="width: 140px" />
              </td>
            </tr>
          </tbody>
        </n-table>

        <div class="score-footer">
          <div class="score-total">
            <span>总分</span>
            <n-input-number v-model:value="scoreForm.score" :min="0" :max="100" :disabled="selectedTask.submitted === 1" class="score-total-input" />
          </div>
          <div class="score-fields">
            <n-radio-group v-model:value="scoreForm.conclusion" :disabled="selectedTask.submitted === 1">
              <n-radio-button v-for="item in conclusionOptions" :key="String(item.value)" :value="item.value">
                {{ item.label }}
              </n-radio-button>
            </n-radio-group>
            <n-input
              v-model:value="scoreForm.comment"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 6 }"
              :disabled="selectedTask.submitted === 1"
              placeholder="评审意见"
            />
            <n-space justify="end">
              <StatusTag v-if="selectedTask.submitted === 1" :text="conclusionText(selectedTask.conclusion)" />
              <n-button v-else type="primary" :loading="saving" @click="submitScore">提交评分</n-button>
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

.task-list {
  margin: calc(var(--space-3) * -1);
}

.task-list :deep(.n-list-item) {
  padding: var(--space-3);
  border-radius: var(--radius-control);
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
}
</style>
