<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NPopconfirm, useMessage, type DataTableColumns, type SelectOption } from 'naive-ui'
import DataPanel from '@/components/DataPanel.vue'
import StatusTag from '@/components/StatusTag.vue'
import { statusLabel } from '@/constants/statusLabels'
import { renderTableActions } from '@/utils/tableActions'
import {
  addReviewerGroupMember,
  createReviewerGroup,
  deleteReviewerGroup,
  listReviewerCandidates,
  listReviewerGroups,
  removeReviewerGroupMember,
  updateReviewerGroup,
  type ReviewerCandidate,
  type ReviewerGroup,
  type ReviewerGroupPayload
} from '@/api/video'

const message = useMessage()

const loading = ref(false)
const groupVisible = ref(false)
const memberVisible = ref(false)
const groups = ref<ReviewerGroup[]>([])
const reviewers = ref<ReviewerCandidate[]>([])
const editingGroup = ref<ReviewerGroup | null>(null)
const memberGroup = ref<ReviewerGroup | null>(null)
const groupSaving = ref(false)
const memberSaving = ref(false)
const removingMemberId = ref('')
const memberBusy = computed(() => memberSaving.value || Boolean(removingMemberId.value))

const groupForm = reactive<ReviewerGroupPayload>({
  name: '',
  status: 'ENABLED'
})

const memberForm = reactive({
  reviewerUserId: ''
})

const reviewerOptions = ref<SelectOption[]>([])

const columns: DataTableColumns<ReviewerGroup> = [
  { title: '组名', key: 'name', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '成员数', key: 'memberCount', width: 90, align: 'right', render: (row) => h('span', { class: 'numeric' }, String(row.memberCount)) },
  { title: '状态', key: 'status', width: 90, render: (row) => h(StatusTag, { value: row.status, text: statusLabel(row.status) }) },
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

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const [groupRes, reviewerRes] = await Promise.all([listReviewerGroups(), listReviewerCandidates()])
    groups.value = groupRes.data
    reviewers.value = reviewerRes.data
    reviewerOptions.value = reviewerRes.data.map((item) => ({ label: `${item.realName} ${item.workNo || item.id}`, value: item.id }))
  } catch (error) {
    showError(error, '评审组加载失败')
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  loading.value = true
  try {
    const res = await listReviewerGroups()
    groups.value = res.data
  } catch (error) {
    showError(error, '评审组加载失败')
  } finally {
    loading.value = false
  }
}

function openGroup(row?: ReviewerGroup) {
  editingGroup.value = row || null
  groupForm.name = row?.name || ''
  groupForm.status = row?.status || 'ENABLED'
  groupVisible.value = true
}

async function saveGroup() {
  if (groupSaving.value) return
  if (!groupForm.name.trim()) {
    message.error('请输入评审组名称')
    return
  }
  groupSaving.value = true
  try {
    if (editingGroup.value) await updateReviewerGroup(editingGroup.value.id, groupForm)
    else await createReviewerGroup(groupForm)
    message.success('评审组已保存')
    groupVisible.value = false
    await loadGroups()
  } catch (error) {
    showError(error, '评审组保存失败')
  } finally {
    groupSaving.value = false
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
  if (memberBusy.value) return
  if (!memberGroup.value || !memberForm.reviewerUserId) {
    message.error('请选择评审教师')
    return
  }
  memberSaving.value = true
  try {
    await addReviewerGroupMember(memberGroup.value.id, memberForm.reviewerUserId)
    message.success('成员已添加')
    memberForm.reviewerUserId = ''
    await loadGroups()
    memberGroup.value = groups.value.find((item) => item.id === memberGroup.value?.id) || memberGroup.value
  } catch (error) {
    showError(error, '成员添加失败')
  } finally {
    memberSaving.value = false
  }
}

async function removeMember(memberId: string) {
  if (memberBusy.value) return
  if (!memberGroup.value) return
  removingMemberId.value = memberId
  try {
    await removeReviewerGroupMember(memberGroup.value.id, memberId)
    message.success('成员已移除')
    await loadGroups()
    memberGroup.value = groups.value.find((item) => item.id === memberGroup.value?.id) || memberGroup.value
  } catch (error) {
    showError(error, '成员移除失败')
  } finally {
    removingMemberId.value = ''
  }
}

function showError(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : fallback
  message.error(detail || fallback)
}
</script>

<template>
  <section class="page-section">
    <DataPanel
      title="评审组列表"
      :columns="columns"
      :data="groups"
      :total="groups.length"
      :loading="loading"
      empty-title="暂无评审组"
      empty-description="当前学院还没有可用的评审组。"
      @refresh="loadAll"
    >
      <template #actions>
        <n-button v-if="groups.length > 0" type="primary" size="small" @click="openGroup()">新增评审组</n-button>
      </template>
      <template #emptyAction>
        <n-button type="primary" @click="openGroup()">新增评审组</n-button>
      </template>
    </DataPanel>

    <n-modal
      v-model:show="groupVisible"
      preset="dialog"
      :title="editingGroup ? '编辑评审组' : '新增评审组'"
      :closable="!groupSaving"
      :close-on-esc="!groupSaving"
      :mask-closable="!groupSaving"
    >
      <n-space vertical>
        <n-input v-model:value="groupForm.name" placeholder="组名" :disabled="groupSaving" />
        <n-select
          v-model:value="groupForm.status"
          :options="[
            { label: '启用', value: 'ENABLED' },
            { label: '停用', value: 'DISABLED' }
          ]"
          :disabled="groupSaving"
        />
        <n-space justify="end">
          <n-button :disabled="groupSaving" @click="groupVisible = false">取消</n-button>
          <n-button type="primary" :loading="groupSaving" @click="saveGroup">保存</n-button>
        </n-space>
      </n-space>
    </n-modal>

    <n-modal
      v-model:show="memberVisible"
      preset="card"
      :title="memberGroup ? `成员管理：${memberGroup.name}` : '成员管理'"
      style="width: min(var(--overlay-wide), var(--overlay-modal-max))"
      :closable="!memberBusy"
      :close-on-esc="!memberBusy"
      :mask-closable="!memberBusy"
    >
      <n-space vertical>
        <div class="member-controls">
          <n-select v-model:value="memberForm.reviewerUserId" filterable :options="reviewerOptions" placeholder="本院评审教师" :disabled="memberBusy" />
          <n-button type="primary" :loading="memberSaving" :disabled="Boolean(removingMemberId)" @click="addMember">添加</n-button>
        </div>
        <n-list bordered>
          <n-list-item v-for="member in memberGroup?.members || []" :key="member.id">
            <n-space justify="space-between" align="center" style="width: 100%">
              <span>{{ member.reviewerName || member.workNo || member.reviewerUserId }}</span>
              <n-button
                size="small"
                quaternary
                type="error"
                :loading="removingMemberId === member.id"
                :disabled="memberBusy && removingMemberId !== member.id"
                @click="removeMember(member.id)"
              >移除</n-button>
            </n-space>
          </n-list-item>
        </n-list>
      </n-space>
    </n-modal>
  </section>
</template>

<style scoped>
.member-controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-2);
}

@media (max-width: 480px) {
  .member-controls {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
