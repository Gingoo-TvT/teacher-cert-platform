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
  form.rows.push({ subject: '', basis: props.bases[0]?.itemCode || '', remark: '', fileList: [] })
}

function removeSubjectRow(index: number) {
  form.rows.splice(index, 1)
}

async function saveApply() {
  if (!form.studentId || !form.assessmentYear || !form.teachingSegment || form.rows.length === 0) {
    message.error('请选择学生、年度、学段和免考科目')
    return
  }
  const items: ExemptionApplyItem[] = []
  for (const row of form.rows) {
    if (!row.subject || !row.basis || !row.fileList[0]?.file) {
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
  <n-drawer v-model:show="drawerVisible" :width="560">
    <n-drawer-content title="免考申请" closable>
      <n-alert type="info" :bordered="false" class="page-section">
        每个免考科目独立审核，须分别上传佐证；仅复审通过科目会从应考清单中剔除。
      </n-alert>
      <n-form label-placement="top">
        <div class="form-section-title">申请信息</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="学生" :span="2">
            <StudentSelect
              v-model:value="form.studentId"
              :disabled="selfMode"
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
          <n-button size="small" @click="addSubjectRow">添加科目</n-button>
        </n-space>
        <section v-for="(row, index) in form.rows" :key="index" class="subject-row">
          <n-space vertical>
            <n-space align="center">
              <n-select v-model:value="row.subject" :options="subjectOptions" placeholder="科目" style="width: 210px" />
              <n-select v-model:value="row.basis" :options="basisOptions" placeholder="依据" style="width: 190px" />
              <n-button quaternary type="error" @click="removeSubjectRow(index)">删除</n-button>
            </n-space>
            <n-input v-model:value="row.remark" type="textarea" placeholder="说明" />
            <n-upload v-model:file-list="row.fileList" :max="1" accept=".pdf,.jpg,.jpeg,.png" :default-upload="false">
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
          <n-button @click="drawerVisible = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveApply">保存</n-button>
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

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
