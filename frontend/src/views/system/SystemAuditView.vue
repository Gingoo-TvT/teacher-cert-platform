<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, useMessage, type DataTableColumns, type FormInst, type FormRules, type SelectOption } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import {
  deleteAuditLog,
  listAuditLogs,
  listBackups,
  listSystemParams,
  triggerBackup,
  updateSystemParam,
  type AuditLog,
  type BackupRecord,
  type SysParam
} from '@/api/systemAudit'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import { listColleges, type College } from '@/api/organization'
import { formatDateTime } from '@/utils/format'
import { operationLabel, statusLabel } from '@/constants/statusLabels'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()

const paramLoading = ref(false)
const auditLoading = ref(false)
const backupLoading = ref(false)
const saving = ref(false)
const paramDrawerVisible = ref(false)
const backupDrawerVisible = ref(false)
const paramFormRef = ref<FormInst | null>(null)
const backupFormRef = ref<FormInst | null>(null)
const editingParam = ref<SysParam | null>(null)
const params = ref<SysParam[]>([])
const audits = ref<AuditLog[]>([])
const backups = ref<BackupRecord[]>([])
const colleges = ref<College[]>([])
const paramTotal = ref(0)
const auditTotal = ref(0)
const backupTotal = ref(0)

const paramQuery = reactive({ group: null as string | null, keyword: '' })
const auditQuery = reactive({
  bizType: '',
  operation: '',
  keyword: '',
  collegeId: '',
  studentId: '',
  batchNo: ''
})
const backupStatus = ref<string | null>(null)

const paramForm = reactive({
  paramValue: '',
  description: ''
})

const backupForm = reactive({
  backupType: 'mysql',
  scope: 'full',
  remark: ''
})

const canManageParam = computed(() => userStore.hasPerm('system:param:manage'))
const canViewAudit = computed(() => userStore.hasPerm('audit:view'))
const canBackup = computed(() => userStore.hasPerm('system:backup'))
const hasVisibleSection = computed(() => canManageParam.value || canViewAudit.value || canBackup.value)
const summary = computed(() => ({
  params: paramTotal.value,
  audits: auditTotal.value,
  backups: backupTotal.value,
  completedBackups: backups.value.filter((item) => item.status === 'COMPLETED').length
}))
const collegeOptions = computed<SelectOption[]>(() => colleges.value.map((item) => ({ label: item.name, value: item.id })))

const paramRules: FormRules = {
  paramValue: [{ required: true, message: '请输入参数值', trigger: ['blur', 'input'] }]
}

const backupRules: FormRules = {
  backupType: [{ required: true, message: '请选择备份类型', trigger: ['change'] }]
}

const paramGroupOptions = [
  { label: '证书', value: 'cert' },
  { label: '视频', value: 'video' },
  { label: '文件', value: 'file' },
  { label: '审核', value: 'review' },
  { label: '校验', value: 'validate' },
  { label: '学生', value: 'student' },
  { label: '安全', value: 'security' },
  { label: '全局', value: 'global' }
]

const paramColumns: DataTableColumns<SysParam> = [
  { title: '参数键', key: 'paramKey', minWidth: 230, ellipsis: { tooltip: true } },
  { title: '值', key: 'paramValue', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '类型', key: 'paramType', width: 88 },
  { title: '分组', key: 'paramGroup', width: 90, render: (row) => tag(row.paramGroup, 'info') },
  { title: '说明', key: 'description', minWidth: 260, ellipsis: { tooltip: true } },
  { title: '更新时间', key: 'updatedAt', minWidth: 170, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.updatedAt)) },
  {
    title: '操作',
    key: 'actions',
    width: 92,
    render: (row) =>
      h(
        NButton,
        { size: 'small', quaternary: true, disabled: row.editable !== 1, onClick: () => openParamDrawer(row) },
        { default: () => '编辑' }
      )
  }
]

const auditColumns: DataTableColumns<AuditLog> = [
  { title: '时间', key: 'operateTime', minWidth: 168, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.operateTime)) },
  { title: '业务', key: 'bizType', width: 118, ellipsis: { tooltip: true } },
  { title: '操作', key: 'operation', width: 126, ellipsis: { tooltip: true }, render: (row) => operationLabel(row.operation) },
  { title: '对象', key: 'target', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '旧状态', key: 'oldStatus', width: 126, ellipsis: { tooltip: true }, render: (row) => row.oldStatus ? h(StatusTag, { value: row.oldStatus, text: statusLabel(row.oldStatus) }) : '-' },
  { title: '新状态', key: 'newStatus', width: 126, ellipsis: { tooltip: true }, render: (row) => row.newStatus ? h(StatusTag, { value: row.newStatus, text: statusLabel(row.newStatus) }) : '-' },
  { title: '意见', key: 'comment', minWidth: 190, ellipsis: { tooltip: true } },
  { title: '操作人', key: 'operatorName', width: 140, ellipsis: { tooltip: true } },
  { title: 'IP', key: 'ip', width: 128, ellipsis: { tooltip: true } },
  {
    title: '删除',
    key: 'delete',
    width: 86,
    render: (row) =>
      h(
        NPopconfirm,
        { onPositiveClick: () => tryDeleteAudit(row) },
        {
          trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
          default: () => '审计日志不可物理删除，此操作会被服务端拒绝并留痕。'
        }
      )
  }
]

const backupColumns: DataTableColumns<BackupRecord> = [
  { title: '类型', key: 'backupType', width: 100, render: (row) => tag(row.backupType, 'info') },
  { title: '状态', key: 'status', width: 110, render: (row) => backupStatusTag(row.status) },
  { title: '范围', key: 'scope', width: 120, ellipsis: { tooltip: true } },
  { title: '位置', key: 'storageUri', minWidth: 260, ellipsis: { tooltip: true } },
  { title: '开始', key: 'startedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.startedAt)) },
  { title: '完成', key: 'finishedAt', minWidth: 166, render: (row) => h('span', { class: 'mono tabular-nums' }, formatDateTime(row.finishedAt)) },
  { title: '备注', key: 'remark', minWidth: 200, ellipsis: { tooltip: true } }
]

async function loadParams() {
  if (!canManageParam.value) {
    params.value = []
    return
  }
  paramLoading.value = true
  try {
    const res = await listSystemParams(paramQuery)
    params.value = res.data.records
    paramTotal.value = res.data.total
  } catch (error) {
    showError(error, '参数加载失败')
  } finally {
    paramLoading.value = false
  }
}

async function loadAudits() {
  if (!canViewAudit.value) {
    audits.value = []
    return
  }
  auditLoading.value = true
  try {
    const res = await listAuditLogs(auditQuery)
    audits.value = res.data.records
    auditTotal.value = res.data.total
  } catch (error) {
    showError(error, '审计日志加载失败')
  } finally {
    auditLoading.value = false
  }
}

async function loadBackups() {
  if (!canBackup.value) {
    backups.value = []
    return
  }
  backupLoading.value = true
  try {
    const res = await listBackups(backupStatus.value)
    backups.value = res.data.records
    backupTotal.value = res.data.total
  } catch (error) {
    showError(error, '备份记录加载失败')
  } finally {
    backupLoading.value = false
  }
}

function openParamDrawer(row: SysParam) {
  editingParam.value = row
  paramForm.paramValue = row.paramValue || ''
  paramForm.description = row.description || ''
  paramDrawerVisible.value = true
}

async function saveParam() {
  if (!editingParam.value) return
  await paramFormRef.value?.validate()
  saving.value = true
  try {
    await updateSystemParam(editingParam.value.id, {
      paramValue: paramForm.paramValue.trim(),
      description: paramForm.description.trim() || null
    })
    message.success('参数已更新')
    paramDrawerVisible.value = false
    await loadParams()
  } catch (error) {
    showError(error, '参数保存失败')
  } finally {
    saving.value = false
  }
}

function openBackupDrawer() {
  backupForm.backupType = 'mysql'
  backupForm.scope = 'full'
  backupForm.remark = ''
  backupDrawerVisible.value = true
}

async function saveBackup() {
  await backupFormRef.value?.validate()
  saving.value = true
  try {
    await triggerBackup({
      backupType: backupForm.backupType,
      scope: backupForm.scope.trim() || null,
      remark: backupForm.remark.trim() || null
    })
    message.success('备份演练记录已写入')
    backupDrawerVisible.value = false
    await loadBackups()
  } catch (error) {
    showError(error, '备份记录写入失败')
  } finally {
    saving.value = false
  }
}

async function tryDeleteAudit(row: AuditLog) {
  try {
    await deleteAuditLog(row.id)
    message.warning('服务端未拒绝删除，请复核权限配置')
    await loadAudits()
  } catch (error) {
    showError(error, '审计日志不可删除')
  }
}

function backupStatusTag(status: string) {
  const text = status === 'FAILED' ? '失败' : statusLabel(status)
  return h(StatusTag, { text, value: status })
}

function tag(text: string | null | undefined, type: 'default' | 'info' | 'success' | 'warning' | 'error' = 'default') {
  return h(StatusTag, { text: text || '-' })
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}

async function loadVisibleSections() {
  const tasks: Promise<void>[] = []
  if (canViewAudit.value) tasks.push(loadColleges())
  if (canManageParam.value) tasks.push(loadParams())
  if (canViewAudit.value) tasks.push(loadAudits())
  if (canBackup.value) tasks.push(loadBackups())
  await Promise.all(tasks)
}

async function loadColleges() {
  const res = await listColleges()
  colleges.value = res.data
}

function resetParamQuery() {
  paramQuery.group = null
  paramQuery.keyword = ''
  void loadParams()
}

function resetAuditQuery() {
  Object.assign(auditQuery, {
    bizType: '',
    operation: '',
    keyword: '',
    collegeId: '',
    studentId: '',
    batchNo: ''
  })
  void loadAudits()
}

function resetBackupQuery() {
  backupStatus.value = null
  void loadBackups()
}

onMounted(loadVisibleSections)
</script>

<template>
  <PageContainer title="参数审计备份" description="系统参数、审计日志与备份记录管理。">
    <template #actions>
      <n-space>
        <n-button v-if="canManageParam" secondary @click="loadParams">刷新参数</n-button>
        <n-button v-if="canViewAudit" secondary @click="loadAudits">刷新审计</n-button>
        <n-button v-if="canBackup" type="primary" @click="openBackupDrawer">记录备份演练</n-button>
      </n-space>
    </template>

    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的系统治理分区" class="page-section" />

    <n-grid v-if="hasVisibleSection" :cols="4" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-if="canManageParam"><StatCard label="系统参数" :value="summary.params" /></n-gi>
      <n-gi v-if="canViewAudit"><StatCard label="审计记录" :value="summary.audits" tone="info" /></n-gi>
      <n-gi v-if="canBackup"><StatCard label="备份记录" :value="summary.backups" tone="neutral" /></n-gi>
      <n-gi v-if="canBackup"><StatCard label="备份完成" :value="summary.completedBackups" tone="success" /></n-gi>
    </n-grid>

    <n-tabs v-if="hasVisibleSection" type="line" animated>
      <n-tab-pane v-if="canManageParam" name="params" tab="系统参数">
        <FilterBar :loading="paramLoading" @submit="loadParams" @reset="resetParamQuery">
          <label class="filter-field">
            <span>分组</span>
            <n-select v-model:value="paramQuery.group" clearable placeholder="全部分组" :options="paramGroupOptions" style="width: 150px" />
          </label>
          <label class="filter-field">
            <span>关键词</span>
            <n-input v-model:value="paramQuery.keyword" clearable placeholder="参数键 / 说明" style="width: 240px" @keyup.enter="loadParams" />
          </label>
        </FilterBar>
        <DataPanel
          title="系统参数"
          :columns="paramColumns"
          :data="params"
          :total="paramTotal"
          :scroll-x="960"
          :loading="paramLoading"
          empty-title="暂无系统参数"
          empty-description="当前筛选条件下没有系统参数记录。"
          @refresh="loadParams"
        />
      </n-tab-pane>

      <n-tab-pane v-if="canViewAudit" name="audit" tab="审计日志">
        <FilterBar :loading="auditLoading" @submit="loadAudits" @reset="resetAuditQuery">
          <label class="filter-field">
            <span>业务</span>
            <n-input v-model:value="auditQuery.bizType" clearable placeholder="业务类型" style="width: 140px" @keyup.enter="loadAudits" />
          </label>
          <label class="filter-field">
            <span>操作</span>
            <n-input v-model:value="auditQuery.operation" clearable placeholder="操作名称" style="width: 140px" @keyup.enter="loadAudits" />
          </label>
          <label class="filter-field">
            <span>学院</span>
            <n-select v-model:value="auditQuery.collegeId" clearable filterable placeholder="全部学院" :options="collegeOptions" style="width: 220px" />
          </label>
          <label class="filter-field">
            <span>关键词</span>
            <n-input v-model:value="auditQuery.keyword" clearable placeholder="对象 / 意见 / 关键词" style="width: 220px" @keyup.enter="loadAudits" />
          </label>
          <template #more>
            <label class="filter-field">
              <span>学生</span>
              <n-input v-model:value="auditQuery.studentId" clearable placeholder="学生ID（数字）" style="width: 150px" @keyup.enter="loadAudits" />
            </label>
            <label class="filter-field">
              <span>批次</span>
              <n-input v-model:value="auditQuery.batchNo" clearable placeholder="批次号" style="width: 150px" @keyup.enter="loadAudits" />
            </label>
          </template>
        </FilterBar>
        <DataPanel
          title="审计日志"
          :columns="auditColumns"
          :data="audits"
          :total="auditTotal"
          :scroll-x="1280"
          :loading="auditLoading"
          empty-title="暂无审计日志"
          empty-description="当前筛选条件下没有审计记录。"
          @refresh="loadAudits"
        />
      </n-tab-pane>

      <n-tab-pane v-if="canBackup" name="backup" tab="备份记录">
        <FilterBar :loading="backupLoading" @submit="loadBackups" @reset="resetBackupQuery">
          <label class="filter-field">
            <span>状态</span>
            <n-select
              v-model:value="backupStatus"
              clearable
              placeholder="全部状态"
              style="width: 150px"
              :options="[
                { label: '已完成', value: 'COMPLETED' },
                { label: '失败', value: 'FAILED' },
                { label: '运行中', value: 'RUNNING' }
              ]"
            />
          </label>
        </FilterBar>
        <DataPanel
          title="备份记录"
          :columns="backupColumns"
          :data="backups"
          :total="backupTotal"
          :scroll-x="1120"
          :loading="backupLoading"
          empty-title="暂无备份记录"
          empty-description="当前筛选条件下没有备份演练记录。"
          @refresh="loadBackups"
        >
          <template #actions>
            <n-button type="primary" size="small" @click="openBackupDrawer">记录备份演练</n-button>
          </template>
        </DataPanel>
      </n-tab-pane>
    </n-tabs>

    <n-drawer v-model:show="paramDrawerVisible" :width="520" placement="right">
      <n-drawer-content :title="editingParam ? editingParam.paramKey : '编辑参数'">
        <n-form ref="paramFormRef" :model="paramForm" :rules="paramRules" label-placement="top">
          <n-form-item label="参数值" path="paramValue">
            <n-input v-model:value="paramForm.paramValue" maxlength="512" show-count />
          </n-form-item>
          <n-form-item label="说明">
            <n-input v-model:value="paramForm.description" type="textarea" maxlength="255" show-count />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="paramDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveParam">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="backupDrawerVisible" :width="480" placement="right">
      <n-drawer-content title="记录备份演练">
        <n-form ref="backupFormRef" :model="backupForm" :rules="backupRules" label-placement="top">
          <n-form-item label="备份类型" path="backupType">
            <n-select
              v-model:value="backupForm.backupType"
              :options="[
                { label: 'MySQL', value: 'mysql' },
                { label: 'MinIO', value: 'minio' },
                { label: '全量', value: 'full' }
              ]"
            />
          </n-form-item>
          <n-form-item label="范围">
            <n-input v-model:value="backupForm.scope" maxlength="128" />
          </n-form-item>
          <n-form-item label="备注">
            <n-input v-model:value="backupForm.remark" type="textarea" maxlength="500" show-count />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="backupDrawerVisible = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="saveBackup">保存</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>
  </PageContainer>
</template>

<style scoped>
.n-tabs {
  margin-top: var(--space-2);
}
</style>
