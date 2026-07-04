<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch, type Component } from 'vue'
import { AlertCircleOutline, CheckmarkCircleOutline, CloudUploadOutline, PlayCircleOutline, RefreshOutline, TimeOutline } from '@vicons/ionicons5'
import { useMessage, type UploadFileInfo } from 'naive-ui'
import StatusTag from '@/components/StatusTag.vue'
import { statusLabel } from '@/constants/statusLabels'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'
import { detectVideoDurationSeconds, formatVideoDuration } from '@/utils/videoDuration'
import {
  initVideoUpload,
  listVideoReviews,
  mergeVideoUpload,
  playVideoReview,
  uploadVideoChunk,
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
const uploadVisible = ref(false)
const uploading = ref(false)
const playerVisible = ref(false)
const reviews = ref<VideoReview[]>([])
const fileList = ref<UploadFileInfo[]>([])
const uploadProgress = ref(0)
const durationDetected = ref(false)
const durationDetectFailed = ref(false)
const playbackUrl = ref('')
const watermarkText = ref('')
const watermarkStyle = ref({ left: '12%', top: '18%' })

const uploadForm = reactive({
  studentId: userStore.currentUser?.studentId || '',
  assessmentYear: yearStore.assessmentYear,
  durationSeconds: 900,
  chunkSize: VIDEO_UPLOAD_CHUNK_SIZE
})

const currentReview = computed(() => reviews.value[0] || null)
const returned = computed(() => currentReview.value?.status === 'RETURNED')
const canUploadNow = computed(() => !currentReview.value || ['WAIT_UPLOAD', 'VALIDATION_FAILED', 'RETURNED'].includes(currentReview.value.status))
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

onMounted(loadReviews)

async function loadReviews() {
  loading.value = true
  try {
    const res = await listVideoReviews({ assessmentYear: yearStore.assessmentYear })
    reviews.value = res.data.records
  } catch (error) {
    showError(error, '视频进度加载失败')
  } finally {
    loading.value = false
  }
}

function openUpload() {
  uploadForm.studentId = currentReview.value?.studentId || userStore.currentUser?.studentId || ''
  uploadForm.assessmentYear = currentReview.value?.assessmentYear || yearStore.assessmentYear
  uploadForm.durationSeconds = currentReview.value?.durationSeconds || 900
  durationDetected.value = Boolean(currentReview.value?.durationSeconds)
  durationDetectFailed.value = false
  uploadProgress.value = 0
  fileList.value = []
  uploadVisible.value = true
}

async function handleFileListUpdate(next: UploadFileInfo[]) {
  fileList.value = next
  const file = next[0]?.file
  durationDetected.value = false
  durationDetectFailed.value = false
  if (!file) return
  try {
    uploadForm.durationSeconds = await detectVideoDurationSeconds(file)
    durationDetected.value = true
  } catch {
    durationDetectFailed.value = true
  }
}

async function uploadVideo() {
  const file = fileList.value[0]?.file
  if (!file || !uploadForm.studentId || !uploadForm.assessmentYear) {
    message.error('请选择年度和视频文件')
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

watch(
  () => yearStore.assessmentYear,
  async (year) => {
    uploadForm.assessmentYear = year
    await loadReviews()
  }
)
</script>

<template>
  <section>
    <n-card :bordered="false" class="page-section video-step-card">
      <template #header>视频进度</template>
      <template #header-extra>
        <n-button secondary size="small" :loading="loading" @click="loadReviews">
          <template #icon><n-icon :component="RefreshOutline" /></template>
          刷新
        </n-button>
      </template>
      <n-steps :current="stepCurrent" class="video-step">
        <n-step title="上传视频" description="选择教学能力视频并提交" />
        <n-step title="评审中" description="等待评审教师评分" />
        <n-step :title="finalStepTitle" description="查看确认或退回意见" />
      </n-steps>
    </n-card>

    <n-card :bordered="false" class="page-section video-self-card">
      <n-spin :show="loading">
        <div class="video-self-content">
          <div class="video-icon">
            <n-icon :component="statusIcon" />
          </div>
          <div class="video-info">
            <div class="video-title-row">
              <h3>{{ currentReview?.videoFileName || '尚未上传视频' }}</h3>
              <StatusTag :value="currentReview?.status || 'WAIT_UPLOAD'" :text="currentReview?.statusLabel || statusLabel(currentReview?.status || 'WAIT_UPLOAD')" />
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

    <n-drawer v-model:show="uploadVisible" :width="560">
      <n-drawer-content title="上传教学能力视频" closable>
        <n-space vertical>
          <n-alert v-if="returned" type="warning" :bordered="false">
            {{ reviewMessage || '视频已退回，请按意见重新上传。' }}
          </n-alert>
          <n-input v-model:value="uploadForm.assessmentYear" placeholder="考核年度" class="mono-input" />
          <n-alert v-if="!durationDetectFailed" type="info" :bordered="false">
            {{ fileList.length ? (durationDetected ? `时长：${durationText} · 自动识别` : '正在识别视频时长') : '选择视频后自动识别时长' }}
          </n-alert>
          <n-input-number v-else v-model:value="uploadForm.durationSeconds" :min="1" style="width: 100%" placeholder="时长（秒）" />
          <n-upload :file-list="fileList" :max="1" accept="video/mp4,.mp4" :default-upload="false" @update:file-list="handleFileListUpdate">
            <n-upload-dragger>
              <n-text>点击或拖拽视频到此处上传</n-text>
              <n-p depth="3">支持 MP4 文件，选择后自动识别时长。</n-p>
            </n-upload-dragger>
          </n-upload>
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

.video-step {
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

@media (max-width: 820px) {
  .video-self-content {
    grid-template-columns: 1fr;
  }

  .video-actions {
    justify-content: flex-start;
  }
}
</style>
