<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import FilterBar from '@/components/FilterBar.vue'
import {
  deleteAuditLog,
  listAuditLogs,
  listBackups,
  listSystemParams,
  type AuditLog,
  type BackupRecord,
  type SysParam
} from '@/api/systemAudit'
import PageContainer from '@/components/PageContainer.vue'
import StatusTag from '@/components/StatusTag.vue'
import StatCard from '@/components/StatCard.vue'
import ParamDrawer from './components/audit/ParamDrawer.vue'
import BackupDrawer from './components/audit/BackupDrawer.vue'
import { listColleges, type College } from '@/api/organization'
import { formatDateTime } from '@/utils/format'
import { operationLabel, statusLabel } from '@/constants/statusLabels'
import { useUserStore } from '@/stores/user'

const message = useMessage()
const userStore = useUserStore()

const paramLoading = ref(false)
const auditLoading = ref(false)
const backupLoading = ref(false)
const paramDrawerRef = ref<InstanceType<typeof ParamDrawer> | null>(null)
const backupDrawerRef = ref<InstanceType<typeof BackupDrawer> | null>(null)
const params = ref<SysParam[]>([])
const audits = ref<AuditLog[]>([])
const backups = ref<BackupRecord[]>([])
const colleges = ref<College[]>([])
const paramTotal = ref(0)
const auditTotal = ref(0)
const backupTotal = ref(0)
const paramPage = ref(1)
const paramSize = ref(20)
const auditPage = ref(1)
const auditSize = ref(20)
const backupPage = ref(1)
const backupSize = ref(20)

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

const canManageParam = computed(() => userStore.hasPerm('system:param:manage'))
const canViewAudit = computed(() => userStore.hasPerm('audit:view'))
const canBackup = computed(() => userStore.hasPerm('system:backup'))
const hasVisibleSection = computed(() => canManageParam.value || canViewAudit.value || canBackup.value)
// Phase 44e-rollout：备份/审计改真分页后 backups/audits 数组仅为当页数据，"备份完成" 原先由
// backups.value.filter(...).length 统计、真分页后会静默退化为「仅当页完成数」而非全量总数。
// 加一次额外请求换真总数属于本次机械 rollout 之外的范围（增加网络往返且脱离"分页改造"本身），
// 故与 spec 对 listUsers/backups 等条目的既定处理一致：直接去掉该派生统计卡片，不展示误导性数字。
const summary = computed(() => ({
  params: paramTotal.value,
  audits: auditTotal.value,
  backups: backupTotal.value
}))
const collegeOptions = computed<SelectOption[]>(() => colleges.value.map((item) => ({ label: item.name, value: item.id })))

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
  { title: '意见', key: 'comment', minWidth: 200, ellipsis: { tooltip: true } },
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
    const res = await listSystemParams({ ...paramQuery, page: paramPage.value, size: paramSize.value })
    params.value = res.data.records
    paramTotal.value = res.data.total
  } catch (error) {
    showError(error, '参数加载失败')
  } finally {
    paramLoading.value = false
  }
}

// 筛选变更 → 回到第 1 页再查（真分页）。
function searchParams() {
  paramPage.value = 1
  void loadParams()
}

function onParamPageChange(next: number) {
  paramPage.value = next
  void loadParams()
}

function onParamPageSizeChange(nextSize: number) {
  paramSize.value = nextSize
  paramPage.value = 1
  void loadParams()
}

async function loadAudits() {
  if (!canViewAudit.value) {
    audits.value = []
    return
  }
  auditLoading.value = true
  try {
    const res = await listAuditLogs({ ...auditQuery, page: auditPage.value, size: auditSize.value })
    audits.value = res.data.records
    auditTotal.value = res.data.total
  } catch (error) {
    showError(error, '审计日志加载失败')
  } finally {
    auditLoading.value = false
  }
}

// 筛选变更 → 回到第 1 页再查（真分页）。
function searchAudits() {
  auditPage.value = 1
  void loadAudits()
}

function onAuditPageChange(next: number) {
  auditPage.value = next
  void loadAudits()
}

function onAuditPageSizeChange(nextSize: number) {
  auditSize.value = nextSize
  auditPage.value = 1
  void loadAudits()
}

async function loadBackups() {
  if (!canBackup.value) {
    backups.value = []
    return
  }
  backupLoading.value = true
  try {
    const res = await listBackups(backupStatus.value, backupPage.value, backupSize.value)
    backups.value = res.data.records
    backupTotal.value = res.data.total
  } catch (error) {
    showError(error, '备份记录加载失败')
  } finally {
    backupLoading.value = false
  }
}

// 筛选变更 → 回到第 1 页再查（真分页）。
function searchBackups() {
  backupPage.value = 1
  void loadBackups()
}

function onBackupPageChange(next: number) {
  backupPage.value = next
  void loadBackups()
}

function onBackupPageSizeChange(nextSize: number) {
  backupSize.value = nextSize
  backupPage.value = 1
  void loadBackups()
}

function openParamDrawer(row: SysParam) {
  paramDrawerRef.value?.open(row)
}

function openBackupDrawer() {
  backupDrawerRef.value?.open()
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

function tag(text: string | null | undefined, _type: 'default' | 'info' | 'success' | 'warning' | 'error' = 'default') {
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
  searchParams()
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
  searchAudits()
}

function resetBackupQuery() {
  backupStatus.value = null
  searchBackups()
}

onMounted(loadVisibleSections)
</script>

<template>
  <PageContainer title="参数审计备份" description="系统参数、审计日志与备份记录管理。">
    <template #actions>
      <n-button v-if="canBackup" type="primary" @click="openBackupDrawer">记录备份演练</n-button>
    </template>

    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的系统治理分区" class="page-section" />

    <n-grid v-if="hasVisibleSection" :cols="3" :x-gap="12" responsive="screen" class="page-section">
      <n-gi v-if="canManageParam"><StatCard label="系统参数" :value="summary.params" /></n-gi>
      <n-gi v-if="canViewAudit"><StatCard label="审计记录" :value="summary.audits" tone="info" /></n-gi>
      <n-gi v-if="canBackup"><StatCard label="备份记录" :value="summary.backups" tone="neutral" /></n-gi>
    </n-grid>

    <n-tabs v-if="hasVisibleSection" type="line" animated>
      <n-tab-pane v-if="canManageParam" name="params" tab="系统参数">
        <FilterBar :loading="paramLoading" @submit="searchParams" @reset="resetParamQuery">
          <label class="filter-field">
            <span>分组</span>
            <n-select v-model:value="paramQuery.group" clearable placeholder="全部分组" :options="paramGroupOptions" style="width: 150px" />
          </label>
          <label class="filter-field">
            <span>关键词</span>
            <n-input v-model:value="paramQuery.keyword" clearable placeholder="参数键 / 说明" style="width: 240px" @keyup.enter="searchParams" />
          </label>
        </FilterBar>
        <DataPanel
          title="系统参数"
          :columns="paramColumns"
          :data="params"
          :total="paramTotal"
          :loading="paramLoading"
          remote
          :page="paramPage"
          :page-size="paramSize"
          empty-title="暂无系统参数"
          empty-description="当前筛选条件下没有系统参数记录。"
          @update:page="onParamPageChange"
          @update:page-size="onParamPageSizeChange"
          @refresh="loadParams"
        />
      </n-tab-pane>

      <n-tab-pane v-if="canViewAudit" name="audit" tab="审计日志">
        <FilterBar :loading="auditLoading" @submit="searchAudits" @reset="resetAuditQuery">
          <label class="filter-field">
            <span>业务</span>
            <n-input v-model:value="auditQuery.bizType" clearable placeholder="业务类型" style="width: 140px" @keyup.enter="searchAudits" />
          </label>
          <label class="filter-field">
            <span>操作</span>
            <n-input v-model:value="auditQuery.operation" clearable placeholder="操作名称" style="width: 140px" @keyup.enter="searchAudits" />
          </label>
          <label class="filter-field">
            <span>学院</span>
            <n-select v-model:value="auditQuery.collegeId" clearable filterable placeholder="全部学院" :options="collegeOptions" style="width: 220px" />
          </label>
          <label class="filter-field">
            <span>关键词</span>
            <n-input v-model:value="auditQuery.keyword" clearable placeholder="对象 / 意见 / 关键词" style="width: 220px" @keyup.enter="searchAudits" />
          </label>
          <template #more>
            <label class="filter-field">
              <span>学生</span>
              <div class="filter-control">
                <n-input v-model:value="auditQuery.studentId" clearable placeholder="学生ID（数字）" style="width: 150px" @keyup.enter="searchAudits" />
                <span class="filter-help">请填写数字编号，用于精确定位学生记录。</span>
              </div>
            </label>
            <label class="filter-field">
              <span>批次</span>
              <n-input v-model:value="auditQuery.batchNo" clearable placeholder="批次号" style="width: 150px" @keyup.enter="searchAudits" />
            </label>
          </template>
        </FilterBar>
        <DataPanel
          title="审计日志"
          :columns="auditColumns"
          :data="audits"
          :total="auditTotal"
          :loading="auditLoading"
          remote
          :page="auditPage"
          :page-size="auditSize"
          empty-title="暂无审计日志"
          empty-description="当前筛选条件下没有审计记录。"
          @update:page="onAuditPageChange"
          @update:page-size="onAuditPageSizeChange"
          @refresh="loadAudits"
        />
      </n-tab-pane>

      <n-tab-pane v-if="canBackup" name="backup" tab="备份记录">
        <FilterBar :loading="backupLoading" @submit="searchBackups" @reset="resetBackupQuery">
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
          :loading="backupLoading"
          remote
          :page="backupPage"
          :page-size="backupSize"
          empty-title="暂无备份记录"
          empty-description="当前筛选条件下没有备份演练记录。"
          @update:page="onBackupPageChange"
          @update:page-size="onBackupPageSizeChange"
          @refresh="loadBackups"
        />
      </n-tab-pane>
    </n-tabs>

    <ParamDrawer ref="paramDrawerRef" @saved="loadParams" />

    <BackupDrawer ref="backupDrawerRef" @saved="loadBackups" />
  </PageContainer>
</template>

<style scoped>
.n-tabs {
  margin-top: var(--space-2);
}

.filter-control {
  display: grid;
  gap: 4px;
}

.filter-help {
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 16px;
}
</style>
