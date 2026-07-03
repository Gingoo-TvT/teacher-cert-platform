<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageContainer from '@/components/PageContainer.vue'
import { listDictItems, type DictItem } from '@/api/dict'
import { useUserStore } from '@/stores/user'
import MajorsPanel from './components/organization/MajorsPanel.vue'
import ConfigsPanel from './components/organization/ConfigsPanel.vue'

const userStore = useUserStore()

const schoolItems = ref<DictItem[]>([])

const majorsPanel = ref<InstanceType<typeof MajorsPanel> | null>(null)
const configsPanel = ref<InstanceType<typeof ConfigsPanel> | null>(null)

const canManageCollege = computed(() => userStore.hasPerm('college:manage'))
const canManageMajor = computed(() => userStore.hasPerm('major:manage'))
const canLoadCollegeList = computed(() => canManageCollege.value || canManageMajor.value)
const hasVisibleSection = computed(() => canLoadCollegeList.value || canManageMajor.value)

const schoolText = computed(() => {
  const school = schoolItems.value[0]
  return school ? `${school.itemValue} ${school.itemCode}` : '-'
})

function refreshAll() {
  void majorsPanel.value?.refresh()
  void configsPanel.value?.refresh()
}

onMounted(async () => {
  const res = await listDictItems('school', true)
  schoolItems.value = res.data
})
</script>

<template>
  <PageContainer title="组织与专业" description="维护学校、学院、专业与培养目标配置。">
    <template #actions>
      <n-space>
        <n-tag :bordered="false">学校：{{ schoolText }}</n-tag>
        <n-button v-if="hasVisibleSection" secondary @click="refreshAll">刷新</n-button>
        <n-button v-if="canManageCollege" type="primary" @click="majorsPanel?.openCollegeDrawer()">新增学院</n-button>
      </n-space>
    </template>

    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的组织专业分区" class="page-section" />

    <n-tabs v-if="hasVisibleSection" type="line" animated>
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
</style>
