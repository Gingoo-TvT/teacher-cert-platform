<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, type Component } from 'vue'
import { AlertCircleOutline, CheckmarkCircleOutline, CloudUploadOutline, PlayCircleOutline, RefreshOutline, TimeOutline } from '@vicons/ionicons5'
import { useMessage, type UploadFileInfo } from 'naive-ui'
import StatusTag from '@/components/StatusTag.vue'
import { statusLabel } from '@/constants/statusLabels'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import { detectVideoDurationSeconds, formatVideoDuration } from '@/utils/videoDuration'
import { isAbortError, uploadVideoFile } from '@/utils/videoUpload'
import {
  cancelVideoUpload,
  listVideoReviews,
  playVideoReview,
  VIDEO_UPLOAD_CHUNK_SIZE,
  type VideoReview
} from '@/api/video'

const props = defineProps<{
  canPlay: boolean
}>()

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const loading = ref(false)
const loadError = ref('')
const hasLoadedSuccessfully = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const playerVisible = ref(false)
const reviews = ref<VideoReview[]>([])
const fileList = ref<UploadFileInfo[]>([])
const uploadProgress = ref(0)
const activeUploadId = ref<string | null>(null)
const initPending = ref(false)
const cancelling = ref(false)
let uploadController: AbortController | null = null
let abandonRequested = false
const durationDetected = ref(false)
const durationDetectFailed = ref(false)
const durationDetecting = ref(false)
let durationDetection: Promise<number | null> | null = null
let durationDetectionFile: File | null = null
let durationDetectionSequence = 0
const playbackUrl = ref('')
const watermarkText = ref('')
const watermarkStyle = ref({ left: '12%', top: '18%' })
const compactViewport = ref(false)
let compactViewportQuery: MediaQueryList | null = null
let reviewRequestSequence = 0

const uploadForm = reactive({
  studentId: userStore.currentUser?.studentId || '',
  assessmentYear: yearStore.assessmentYear,
  durationSeconds: 900,
  chunkSize: VIDEO_UPLOAD_CHUNK_SIZE
})

const currentReview = computed(() => reviews.value[0] || null)
const returned = computed(() => currentReview.value?.status === 'RETURNED')
const dataFresh = computed(() => hasLoadedSuccessfully.value && !loadError.value && !loading.value)
const showingStaleData = computed(() => hasLoadedSuccessfully.value && Boolean(loadError.value))
const canUploadNow = computed(() => dataFresh.value && (!currentReview.value || ['WAIT_UPLOAD', 'VALIDATION_FAILED', 'RETURNED'].includes(currentReview.value.status)))
const stepCurrent = computed(() => {
  const status = currentReview.value?.status
  if (!status || ['WAIT_UPLOAD', 'VALIDATION_FAILED', 'RETURNED'].includes(status)) return 1
  if (status === 'CONFIRMED') return 3
  return 2
})
const finalStepTitle = computed(() => returned.value ? '已退回' : currentReview.value?.status === 'CONFIRMED' ? '已确认' : '确认结果')
const statusIcon = computed<Component>(() => {
  if (!currentReview.value) return CloudUploadOutline
  if (returned.value || currentReview.value.status === 'VALIDATION_FAILED') return AlertCircleOutline
  if (currentReview.value.status === 'CONFIRMED') return CheckmarkCircleOutline
  return TimeOutline
})
const reviewMessage = computed(() => currentReview.value?.validationMessage || '')
const durationText = computed(() => `${formatVideoDuration(uploadForm.durationSeconds)} (${uploadForm.durationSeconds}s)`)
const videoTitle = computed(() => currentReview.value?.videoFileName || (showingStaleData.value ? '上次成功结果未记录视频' : '尚未上传视频'))
const videoStatusText = computed(() => {
  if (currentReview.value) return currentReview.value.statusLabel || statusLabel(currentReview.value.status)
  return showingStaleData.value ? '旧结果：待上传' : statusLabel('WAIT_UPLOAD')
})

onMounted(() => {
  compactViewportQuery = window.matchMedia('(max-width: 720px)')
  syncCompactViewport()
  compactViewportQuery.addEventListener('change', syncCompactViewport)
  void loadReviews()
})

async function loadReviews() {
  const requestSequence = ++reviewRequestSequence
  const requestedYear = yearStore.assessmentYear
  loading.value = true
  loadError.value = ''
  try {
    const res = await listVideoReviews({ assessmentYear: requestedYear })
    if (requestSequence !== reviewRequestSequence || requestedYear !== yearStore.assessmentYear) return
    reviews.value = res.data.records
    hasLoadedSuccessfully.value = true
  } catch (error) {
    if (requestSequence !== reviewRequestSequence || requestedYear !== yearStore.assessmentYear) return
    loadError.value = errorMessage(error, '视频进度加载失败')
    message.error(loadError.value)
  } finally {
    if (requestSequence === reviewRequestSequence) loading.value = false
  }
}

function openUpload() {
  if (!dataFresh.value) return
  uploadForm.studentId = currentReview.value?.studentId || userStore.currentUser?.studentId || ''
  uploadForm.assessmentYear = currentReview.value?.assessmentYear || yearStore.assessmentYear
  uploadForm.durationSeconds = currentReview.value?.durationSeconds || 900
  durationDetected.value = Boolean(currentReview.value?.durationSeconds)
  durationDetectFailed.value = false
  durationDetecting.value = false
  durationDetection = null
  durationDetectionFile = null
  durationDetectionSequence += 1
  uploadProgress.value = 0
  fileList.value = []
  initPending.value = false
  cancelling.value = false
  abandonRequested = false
  uploadVisible.value = true
}

async function handleFileListUpdate(next: UploadFileInfo[]) {
  fileList.value = next
  const file = next[0]?.file
  const sequence = ++durationDetectionSequence
  durationDetected.value = false
  durationDetectFailed.value = false
  durationDetectionFile = file || null
  if (!file) {
    durationDetecting.value = false
    durationDetection = null
    return
  }
  durationDetecting.value = true
  const pending = detectVideoDurationSeconds(file).then((value) => value).catch(() => null)
  durationDetection = pending
  const detected = await pending
  if (sequence !== durationDetectionSequence || fileList.value[0]?.file !== file) return
  durationDetecting.value = false
  if (detected == null) {
    durationDetectFailed.value = true
  } else {
    uploadForm.durationSeconds = detected
    durationDetected.value = true
  }
}

async function uploadVideo() {
  if (uploading.value || cancelling.value) return
  const file = fileList.value[0]?.file
  if (!file || !uploadForm.studentId || !uploadForm.assessmentYear) {
    message.error('请选择年度和视频文件')
    return
  }
  uploading.value = true
  uploadProgress.value = 0
  abandonRequested = false
  const controller = new AbortController()
  uploadController = controller
  try {
    let durationSeconds = uploadForm.durationSeconds
    if (durationDetectionFile === file && durationDetection) {
      const detected = await durationDetection
      if (fileList.value[0]?.file !== file) throw new Error('视频文件已变化，请重新开始上传')
      if (detected != null) durationSeconds = detected
    }
    const result = await uploadVideoFile({
      file,
      studentId: uploadForm.studentId,
      assessmentYear: uploadForm.assessmentYear,
      chunkSize: uploadForm.chunkSize,
      durationSeconds,
      signal: controller.signal,
      onInitPending: (pending) => {
        initPending.value = pending
      },
      shouldAbandon: () => abandonRequested,
      onSession: (uploadId) => {
        activeUploadId.value = uploadId
        if (abandonRequested) controller.abort()
      },
      onProgress: (progress) => {
        uploadProgress.value = progress.percentage
      }
    })
    if (abandonRequested) {
      activeUploadId.value = null
      message.warning(result.instantHit ? '视频已完成秒传，无法取消' : '视频已完成定稿，无法取消')
      uploadVisible.value = false
      await loadReviews()
      return
    }
    activeUploadId.value = null
    uploadController = null
    if (result.instantHit) {
      uploadProgress.value = 100
      message.success(result.validationMessage || '秒传命中')
      uploadVisible.value = false
      await loadReviews()
      return
    }
    if (!result.review) throw new Error('服务端未返回视频校验结果')
    message[result.review.status === 'VALIDATION_FAILED' ? 'warning' : 'success'](result.validationMessage || '上传完成')
    uploadVisible.value = false
    await loadReviews()
  } catch (error) {
    if (abandonRequested) {
      if (activeUploadId.value) {
        if (await cancelActiveVideoSession()) uploadVisible.value = false
      } else if (isAbortError(error)) {
        uploadVisible.value = false
      } else {
        showError(error, '无法确认服务端上传会话是否已取消，请重试')
      }
    } else if (!isAbortError(error)) {
      showError(error, '上传失败')
    }
  } finally {
    if (uploadController === controller) uploadController = null
    initPending.value = false
    cancelling.value = false
    uploading.value = false
  }
}

async function cancelOrClose() {
  if (!uploading.value && !activeUploadId.value) {
    uploadVisible.value = false
    return
  }
  abandonRequested = true
  cancelling.value = true
  if (uploading.value && initPending.value && !activeUploadId.value) return
  uploadController?.abort()
  if (await cancelActiveVideoSession()) uploadVisible.value = false
  cancelling.value = false
}

async function cancelActiveVideoSession() {
  const uploadId = activeUploadId.value
  if (!uploadId) return true
  try {
    await cancelVideoUpload(uploadId)
    activeUploadId.value = null
    return true
  } catch (error) {
    showError(error, '取消上传失败')
    return false
  }
}

async function openPlayer() {
  if (!currentReview.value) return
  try {
    const res = await playVideoReview(currentReview.value.id)
    playbackUrl.value = res.data.url
    watermarkText.value = res.data.watermarkText
    moveWatermark()
    playerVisible.value = true
  } catch (error) {
    showError(error, '播放失败')
  }
}

function moveWatermark() {
  const left = 8 + Math.round(Math.random() * 55)
  const top = 12 + Math.round(Math.random() * 48)
  watermarkStyle.value = { left: `${left}%`, top: `${top}%` }
}

function showError(error: unknown, fallback: string) {
  message.error(errorMessage(error, fallback))
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function syncCompactViewport(event?: MediaQueryListEvent) {
  compactViewport.value = event?.matches ?? compactViewportQuery?.matches ?? false
}

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    uploadForm.assessmentYear = year
    await loadReviews()
  }
)

onBeforeUnmount(() => {
  compactViewportQuery?.removeEventListener('change', syncCompactViewport)
  uploadController?.abort()
})
</script>

<template>
  <section>
    <n-card
      v-if="loading && !hasLoadedSuccessfully && !loadError"
      :bordered="false"
      class="page-section video-load-result"
      role="status"
      aria-live="polite"
    >
      <div class="video-initial-loading">
        <n-text depth="3">正在加载视频进度…</n-text>
        <n-skeleton text :repeat="3" />
      </div>
    </n-card>

    <n-card
      v-if="loadError && !hasLoadedSuccessfully"
      :bordered="false"
      class="page-section video-load-result"
      role="alert"
    >
      <n-result status="error" title="视频进度加载失败" :description="loadError">
        <template #footer>
          <n-button type="primary" :loading="loading" @click="loadReviews">重试</n-button>
        </template>
      </n-result>
    </n-card>

    <n-alert
      v-if="showingStaleData"
      type="warning"
      title="刷新失败，当前显示上次成功结果"
      :bordered="false"
      class="page-section"
      role="alert"
    >
      <div class="load-feedback">
        <span>{{ loadError }}</span>
        <n-button size="small" secondary :loading="loading" @click="loadReviews">重试</n-button>
      </div>
    </n-alert>

    <n-card v-if="hasLoadedSuccessfully" :bordered="false" class="page-section video-step-card">
      <template #header>视频进度</template>
      <template #header-extra>
        <n-button secondary size="small" :loading="loading" @click="loadReviews">
          <template #icon><n-icon :component="RefreshOutline" /></template>
          刷新
        </n-button>
      </template>
      <n-steps
        :current="stepCurrent"
        :vertical="compactViewport"
        :size="compactViewport ? 'small' : 'medium'"
        class="video-step"
      >
        <n-step title="上传视频" description="选择教学能力视频并提交" />
        <n-step title="评审中" description="等待评审教师评分" />
        <n-step :title="finalStepTitle" description="查看确认或退回意见" />
      </n-steps>
    </n-card>

    <n-card v-if="hasLoadedSuccessfully" :bordered="false" class="page-section video-self-card">
      <n-spin :show="loading">
        <div class="video-self-content">
          <div class="video-icon">
            <n-icon :component="statusIcon" />
          </div>
          <div class="video-info">
            <div class="video-title-row">
              <h3>{{ videoTitle }}</h3>
              <StatusTag :value="currentReview?.status || 'WAIT_UPLOAD'" :text="videoStatusText" />
            </div>
            <div class="video-meta">
              <span>考核年度：<span class="mono">{{ currentReview?.assessmentYear || yearStore.assessmentYear }}</span></span>
              <span>视频时长：<span class="mono">{{ currentReview?.durationSeconds || '-' }}s</span></span>
              <span>评审结论：<StatusTag :text="currentReview?.finalConclusion === 'PASS' ? '合格' : currentReview?.finalConclusion === 'FAIL' ? '不合格' : '-'" /></span>
            </div>
            <n-alert v-if="reviewMessage" :type="returned || currentReview?.status === 'VALIDATION_FAILED' ? 'warning' : 'info'" :bordered="false">
              {{ reviewMessage }}
            </n-alert>
          </div>
          <div class="video-actions">
            <n-button v-if="props.canPlay && currentReview?.videoFileId" secondary @click="openPlayer">
              <template #icon><n-icon :component="PlayCircleOutline" /></template>
              播放
            </n-button>
            <n-button v-if="canUploadNow" type="primary" @click="openUpload">
              <template #icon><n-icon :component="CloudUploadOutline" /></template>
              {{ currentReview ? '重新上传' : '上传视频' }}
            </n-button>
          </div>
        </div>
      </n-spin>
    </n-card>

    <n-drawer
      v-model:show="uploadVisible"
      width="min(var(--overlay-medium), var(--overlay-drawer-max))"
      :mask-closable="!uploading && !activeUploadId"
      :close-on-esc="!uploading && !activeUploadId"
    >
      <n-drawer-content class="video-upload-drawer" title="上传教学能力视频" :closable="!uploading && !activeUploadId">
        <n-space vertical class="video-upload-content">
          <n-alert v-if="returned" type="warning" :bordered="false">
            {{ reviewMessage || '视频已退回，请按意见重新上传。' }}
          </n-alert>
          <n-input v-model:value="uploadForm.assessmentYear" placeholder="考核年度" class="mono-input" :disabled="uploading || cancelling" />
          <n-alert v-if="!durationDetectFailed" type="info" :bordered="false">
            {{ fileList.length ? (durationDetected ? `时长：${durationText} · 自动识别` : '正在识别视频时长') : '选择视频后自动识别时长' }}
          </n-alert>
          <n-input-number v-else v-model:value="uploadForm.durationSeconds" :min="1" style="width: 100%" placeholder="时长（秒）" :disabled="uploading || cancelling" />
          <n-upload class="video-file-upload" :file-list="fileList" :max="1" accept="video/mp4,.mp4" :default-upload="false" :disabled="uploading || cancelling" @update:file-list="handleFileListUpdate">
            <n-upload-dragger>
              <n-text>点击或拖拽视频到此处上传</n-text>
              <n-p depth="3">支持 MP4 文件，选择后自动识别时长。</n-p>
            </n-upload-dragger>
          </n-upload>
        </n-space>
        <template #footer>
          <div class="video-upload-footer">
            <n-progress type="line" :percentage="uploadProgress" indicator-placement="inside" />
            <div class="video-upload-footer__actions">
              <n-button :loading="cancelling" @click="cancelOrClose">取消</n-button>
              <n-button type="primary" :loading="uploading" :disabled="durationDetecting || cancelling" @click="uploadVideo">开始上传</n-button>
            </div>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="playerVisible" preset="card" title="视频播放" style="width: min(960px, 94vw)">
      <div class="player-shell">
        <video :src="playbackUrl" controls class="video-player" @play="moveWatermark" @timeupdate="moveWatermark" />
        <div class="watermark" :style="watermarkStyle">{{ watermarkText }}</div>
      </div>
    </n-modal>
  </section>
</template>

<style scoped>
.video-step-card :deep(.n-card__content) {
  padding-top: var(--space-3);
}

.video-load-result :deep(.n-card__content) {
  padding: var(--space-4);
}

.video-initial-loading {
  display: grid;
  gap: var(--space-3);
}

.load-feedback {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.video-step {
  width: 100%;
  max-width: 860px;
}

.video-self-content {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) auto;
  gap: var(--space-4);
  align-items: start;
}

.video-icon {
  display: grid;
  width: 54px;
  height: 54px;
  place-items: center;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 28px;
}

.video-info {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: var(--space-3);
}

.video-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  flex-wrap: wrap;
}

.video-title-row h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--text);
  font-size: 17px;
  font-weight: 650;
  line-height: 24px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-meta {
  display: flex;
  gap: var(--space-4);
  color: var(--text-secondary);
  flex-wrap: wrap;
}

.video-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
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

.mono-input :deep(input) {
  font-family: var(--font-mono);
}

.video-upload-content,
.video-file-upload {
  width: 100%;
  min-width: 0;
}

.video-file-upload :deep(.n-upload-trigger),
.video-file-upload :deep(.n-upload-dragger) {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.video-file-upload :deep(.n-upload-dragger) {
  overflow-wrap: anywhere;
}

.video-upload-footer {
  display: grid;
  width: 100%;
  min-width: 0;
  gap: var(--space-3);
}

.video-upload-footer__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.video-upload-drawer :deep(.n-drawer-body-content-wrapper) {
  overflow-x: hidden;
}

@media (max-width: 820px) {
  .video-self-content {
    grid-template-columns: 1fr;
  }

  .video-actions {
    display: grid;
    width: 100%;
    grid-template-columns: minmax(0, 1fr);
  }

  .video-actions .n-button {
    width: 100%;
  }

  .video-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--space-2);
  }

  .video-upload-footer__actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }

  .video-upload-footer__actions .n-button {
    width: 100%;
  }
}
</style>
