<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMessage, type SelectOption, type UploadFileInfo } from 'naive-ui'
import StudentSelect from '@/components/StudentSelect.vue'
import type { DictItem } from '@/api/dict'
import {
  applyExemption,
  uploadExemptionMaterial,
  type ExemptionApplyItem
} from '@/api/exemption'
import { useUserStore } from '@/stores/user'
import { useYearStore } from '@/stores/year'

interface SubjectRow {
  subject: string
  basis: string
  remark: string
  fileList: UploadFileInfo[]
}

const props = defineProps<{
  subjects: DictItem[]
  bases: DictItem[]
  segmentOptions: SelectOption[]
  selfMode: boolean
  assessmentYear: string
  segmentFilter: string | null
  subjectsLoading: boolean
  subjectsError: string
  subjectsLoadedSuccessfully: boolean
  subjectsLoadedSegment: string | null
}>()

const emit = defineEmits<{
  saved: []
  'load-subjects': [segment: string | null]
}>()

const message = useMessage()
const userStore = useUserStore()
const yearStore = useYearStore()

const saving = ref(false)
const drawerVisible = ref(false)
const selectedStudentLabel = ref<string | null>(null)

const form = reactive({
  studentId: '',
  assessmentYear: yearStore.assessmentYear,
  teachingSegment: '',
  rows: [] as SubjectRow[]
})

const subjectOptions = computed<SelectOption[]>(() =>
  props.subjects.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const basisOptions = computed<SelectOption[]>(() =>
  props.bases.map((item) => ({ label: item.itemValue, value: item.itemCode }))
)
const subjectsFresh = computed(() =>
  Boolean(form.teachingSegment)
  && props.subjectsLoadedSuccessfully
  && props.subjectsLoadedSegment === form.teachingSegment
  && !props.subjectsLoading
  && !props.subjectsError
)
const subjectsFeedback = computed(() => {
  if (!form.teachingSegment) return '请先选择任教学段'
  if (props.subjectsLoading) return '免考科目正在加载，请稍候'
  if (props.subjectsError) return props.subjectsError
  if (!subjectsFresh.value) return '当前学段的免考科目尚未加载成功'
  return ''
})

watch(
  () => yearStore.assessmentYear,
  (year) => {
    if (!drawerVisible.value) form.assessmentYear = year
  }
)

function open() {
  form.studentId = props.selfMode ? userStore.currentUser?.studentId || '' : ''
  selectedStudentLabel.value = props.selfMode ? '本人' : null
  form.assessmentYear = props.assessmentYear
  form.teachingSegment = props.segmentFilter || ''
  form.rows = []
  if (form.teachingSegment) emit('load-subjects', form.teachingSegment)
  drawerVisible.value = true
}

function handleSegmentChange(value: string | number | null) {
  const segment = typeof value === 'string' ? value : null
  form.teachingSegment = segment || ''
  form.rows = []
  emit('load-subjects', segment)
}

function addSubjectRow() {
  if (saving.value || !subjectsFresh.value) {
    message.error(subjectsFeedback.value || '免考科目尚未加载成功')
    return
  }
  form.rows.push({ subject: '', basis: props.bases[0]?.itemCode || '', remark: '', fileList: [] })
}

function removeSubjectRow(index: number) {
  form.rows.splice(index, 1)
}

function retrySubjects() {
  if (saving.value || props.subjectsLoading || !form.teachingSegment) return
  emit('load-subjects', form.teachingSegment)
}

async function saveApply() {
  if (saving.value) return
  if (!form.studentId || !form.assessmentYear || !form.teachingSegment || form.rows.length === 0) {
    message.error('请选择学生、年度、学段和免考科目')
    return
  }
  if (!subjectsFresh.value) {
    message.error(subjectsFeedback.value || '免考科目尚未加载成功，请重试后再保存')
    return
  }
  const currentSubjectCodes = new Set(props.subjects.map((item) => item.itemCode))
  const items: ExemptionApplyItem[] = []
  for (const row of form.rows) {
    if (!row.subject || !currentSubjectCodes.has(row.subject) || !row.basis || !row.fileList[0]?.file) {
      message.error('每科必须填写依据并上传佐证')
      return
    }
    items.push({ subject: row.subject, basis: row.basis, remark: row.remark })
  }
  saving.value = true
  try {
    const res = await applyExemption({
      studentId: form.studentId,
      assessmentYear: form.assessmentYear,
      teachingSegment: form.teachingSegment,
      items
    })
    await Promise.all(
      res.data.map((id, index) => uploadExemptionMaterial(id, form.rows[index].fileList[0].file as File))
    )
    message.success('已保存免考申请')
    drawerVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '免考申请保存失败')
  } finally {
    saving.value = false
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open, form })
</script>

<template>
  <n-drawer
    v-model:show="drawerVisible"
    width="min(var(--overlay-medium), var(--overlay-drawer-max))"
    :mask-closable="!saving"
    :close-on-esc="!saving"
  >
    <n-drawer-content title="免考申请" :closable="!saving">
      <n-alert type="info" :bordered="false" class="page-section">
        每个免考科目独立审核，须分别上传佐证；仅复审通过科目会从应考清单中剔除。
      </n-alert>
      <n-alert v-if="form.teachingSegment && !subjectsFresh" :type="subjectsError ? 'error' : 'info'" :bordered="false" class="page-section">
        {{ subjectsFeedback }}
        <n-button v-if="subjectsError || (!subjectsLoading && !subjectsFresh)" text :type="subjectsError ? 'error' : 'primary'" size="small" :loading="subjectsLoading" @click="retrySubjects">重试加载科目</n-button>
      </n-alert>
      <n-form label-placement="top" :disabled="saving">
        <div class="form-section-title">申请信息</div>
        <n-grid cols="1 480:2" responsive="self" item-responsive :x-gap="12">
          <n-form-item-gi label="学生" span="1 480:2">
            <StudentSelect
              v-model:value="form.studentId"
              :disabled="selfMode || saving"
              :selected-label="selectedStudentLabel"
              placeholder="输入学号或姓名搜索"
            />
          </n-form-item-gi>
          <n-form-item-gi label="考核年度">
            <n-input v-model:value="form.assessmentYear" placeholder="考核年度" class="mono-input" />
          </n-form-item-gi>
          <n-form-item-gi label="任教学段">
            <n-select v-model:value="form.teachingSegment" :options="segmentOptions" placeholder="任教学段" @update:value="handleSegmentChange" />
          </n-form-item-gi>
        </n-grid>
        <div class="form-section-title">免考科目</div>
        <n-space justify="space-between" align="center">
          <span>免考科目</span>
          <n-button size="small" :loading="subjectsLoading" :disabled="saving || !subjectsFresh" @click="addSubjectRow">添加科目</n-button>
        </n-space>
        <section v-for="(row, index) in form.rows" :key="index" class="subject-row">
          <n-space vertical>
            <div class="subject-row__controls">
              <n-select v-model:value="row.subject" :options="subjectOptions" placeholder="科目" :loading="subjectsLoading" :disabled="saving || !subjectsFresh" />
              <n-select v-model:value="row.basis" :options="basisOptions" placeholder="依据" :disabled="saving" />
              <n-button quaternary type="error" :disabled="saving" @click="removeSubjectRow(index)">删除</n-button>
            </div>
            <n-input v-model:value="row.remark" type="textarea" placeholder="说明" :disabled="saving" />
            <n-upload v-model:file-list="row.fileList" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false" :disabled="saving">
              <n-upload-dragger>
                <n-text>点击或拖拽佐证文件到此处上传</n-text>
                <n-p depth="3">支持 PDF、JPG、JPEG、PNG，最多 1 个文件。</n-p>
              </n-upload-dragger>
            </n-upload>
          </n-space>
        </section>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button :disabled="saving" @click="drawerVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" :disabled="!subjectsFresh" @click="saveApply">保存</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.subject-row {
  padding: var(--space-4);
  border: 1px solid var(--shell-border);
  border-radius: var(--radius-card);
}

.subject-row__controls {
  display: grid;
  grid-template-columns: minmax(0, 210px) minmax(0, 190px) auto;
  gap: var(--space-2);
  align-items: center;
}

@media (max-width: 480px) {
  .subject-row__controls {
    grid-template-columns: minmax(0, 1fr);
  }
}

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
