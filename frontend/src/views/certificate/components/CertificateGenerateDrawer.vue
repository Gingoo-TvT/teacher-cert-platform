<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import StudentSelect from '@/components/StudentSelect.vue'
import {
  generateCertificate,
  precheckCertificate,
  type Certificate,
  type CertificatePrecheck
} from '@/api/certificate'

const props = defineProps<{
  assessmentYear: string
}>()

const emit = defineEmits<{
  saved: []
}>()

const message = useMessage()

const generateVisible = ref(false)
const precheckVisible = ref(false)
const saving = ref(false)
const precheck = ref<CertificatePrecheck | null>(null)

const generateForm = reactive({
  studentId: '',
  assessmentYear: props.assessmentYear
})

function open() {
  generateForm.studentId = ''
  generateForm.assessmentYear = props.assessmentYear
  precheck.value = null
  generateVisible.value = true
}

async function runPrecheckForForm() {
  if (!generateForm.studentId || !generateForm.assessmentYear) {
    message.error('请选择学生并填写考核年度')
    return
  }
  try {
    const res = await precheckCertificate(generateForm.studentId, generateForm.assessmentYear)
    precheck.value = res.data
    if (res.data.passed) message.success('前置条件已满足')
  } catch (error) {
    showError(error, '前置校验失败')
  }
}

async function generate() {
  if (!generateForm.studentId || !generateForm.assessmentYear) {
    message.error('请选择学生并填写考核年度')
    return
  }
  saving.value = true
  try {
    await generateCertificate({ studentId: generateForm.studentId, assessmentYear: generateForm.assessmentYear })
    message.success('证书编号已生成')
    generateVisible.value = false
    emit('saved')
  } catch (error) {
    showError(error, '证书生成失败')
  } finally {
    saving.value = false
  }
}

async function openPrecheck(row: Certificate) {
  try {
    const res = await precheckCertificate(row.studentId, row.assessmentYear)
    precheck.value = res.data
    precheckVisible.value = true
  } catch (error) {
    showError(error, '前置校验失败')
  }
}

function missingText(items: string[]) {
  return items.length ? items.join('、') : '无'
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

defineExpose({ open, openPrecheck })
</script>

<template>
  <n-drawer v-model:show="generateVisible" :width="560">
    <n-drawer-content title="生成证书编号" closable>
      <n-alert type="info" :bordered="false" class="page-section">
        生成前会聚合基本信息、材料、测试、视频等前置条件；缺项会阻断生成。
      </n-alert>
      <n-form label-placement="top">
        <div class="form-section-title">生成信息</div>
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="学生" :span="2">
            <StudentSelect v-model:value="generateForm.studentId" placeholder="输入学号或姓名搜索" />
          </n-form-item-gi>
          <n-form-item-gi label="考核年度">
            <n-input v-model:value="generateForm.assessmentYear" placeholder="考核年度" class="mono-input" />
          </n-form-item-gi>
        </n-grid>
        <n-alert v-if="precheck" :type="precheck.passed ? 'success' : 'warning'" :bordered="false">
          {{ precheck.passed ? '前置条件已满足' : `缺失：${missingText(precheck.missingItems)}` }}
        </n-alert>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="generateVisible = false">取消</n-button>
          <n-button @click="runPrecheckForForm">前置校验</n-button>
          <n-button type="primary" :loading="saving" @click="generate">生成</n-button>
        </n-space>
      </template>
    </n-drawer-content>
  </n-drawer>

  <n-modal v-model:show="precheckVisible" preset="dialog" title="证书前置校验">
    <n-alert v-if="precheck" :type="precheck.passed ? 'success' : 'warning'" :bordered="false">
      {{ precheck.passed ? '前置条件已满足' : `缺失：${missingText(precheck.missingItems)}` }}
    </n-alert>
  </n-modal>
</template>

<style scoped>
.form-section-title {
  margin: var(--space-2) 0 var(--space-3);
  color: var(--text);
  font-size: 14px;
  font-weight: 600;
}

.mono-input :deep(input) {
  font-family: var(--font-mono);
}
</style>
