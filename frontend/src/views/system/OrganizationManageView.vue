<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageContainer from '@/components/PageContainer.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { useUserStore } from '@/stores/user'
import MajorsPanel from './components/organization/MajorsPanel.vue'
import ConfigsPanel from './components/organization/ConfigsPanel.vue'

const userStore = useUserStore()

const schoolItems = ref<DictItem[]>([])
const schoolLoading = ref(false)
const schoolError = ref('')
const refreshing = ref(false)

const majorsPanel = ref<InstanceType<typeof MajorsPanel> | null>(null)
const configsPanel = ref<InstanceType<typeof ConfigsPanel> | null>(null)

const canManageCollege = computed(() => userStore.hasPerm('college:manage'))
const canManageMajor = computed(() => userStore.hasPerm('major:manage'))
const canLoadCollegeList = computed(() => canManageCollege.value || canManageMajor.value)
const hasVisibleSection = computed(() => canLoadCollegeList.value || canManageMajor.value)

const schoolText = computed(() => {
  const school = schoolItems.value[0]
  if (school) return `${school.itemValue} ${school.itemCode}`
  if (schoolLoading.value) return '加载中…'
  if (schoolError.value) return '加载失败'
  return '暂无学校信息'
})

function errorText(error: unknown, fallback: string) {
  const detail = error instanceof Error ? error.message : ''
  return detail || fallback
}

async function loadSchool() {
  schoolLoading.value = true
  try {
    const res = await listDictItems('school', true)
    schoolItems.value = res.data
    schoolError.value = ''
  } catch (error) {
    schoolError.value = errorText(error, '学校信息加载失败')
  } finally {
    schoolLoading.value = false
  }
}

async function refreshAll() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await Promise.all([
      loadSchool(),
      majorsPanel.value?.refresh() ?? Promise.resolve(),
      configsPanel.value?.refresh() ?? Promise.resolve()
    ])
  } finally {
    refreshing.value = false
  }
}

onMounted(() => {
  void loadSchool()
})
</script>

<template>
  <PageContainer title="组织与专业" description="维护学校、学院、专业与培养目标配置。">
    <template #actions>
      <n-space class="page-actions">
        <n-tag :bordered="false">学校：{{ schoolText }}</n-tag>
        <n-button v-if="hasVisibleSection" secondary :loading="refreshing" @click="refreshAll">刷新</n-button>
        <n-button
          v-if="canManageCollege"
          type="primary"
          :disabled="!majorsPanel?.canCreateCollege"
          @click="majorsPanel?.openCollegeDrawer()"
        >
          新增学院
        </n-button>
      </n-space>
    </template>

    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的组织专业分区" class="page-section" />

    <n-alert v-if="schoolError" type="warning" title="学校信息加载失败" class="school-alert" role="alert">
      <div class="alert-content">
        <span>{{ schoolError }}。下方组织与专业列表仍可独立使用。</span>
        <n-button size="small" secondary :loading="schoolLoading" @click="loadSchool">重试</n-button>
      </div>
    </n-alert>

    <n-tabs v-if="hasVisibleSection" type="line" animated class="organization-tabs">
      <n-tab-pane v-if="canLoadCollegeList" name="majors" tab="学院与专业" display-directive="show">
        <MajorsPanel ref="majorsPanel" />
      </n-tab-pane>

      <n-tab-pane v-if="canManageMajor" name="configs" tab="联动配置" display-directive="show">
        <ConfigsPanel ref="configsPanel" />
      </n-tab-pane>
    </n-tabs>
  </PageContainer>
</template>

<style scoped>
.page-section {
  min-width: 0;
}

.page-actions {
  max-width: 100%;
}

.school-alert {
  margin-bottom: var(--space-4);
}

.alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.organization-tabs {
  min-width: 0;
}

@media (max-width: 720px) {
  .alert-content {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
