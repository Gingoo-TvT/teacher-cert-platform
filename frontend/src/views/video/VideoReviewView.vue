<script setup lang="ts">
import { computed } from 'vue'
import PageContainer from '@/components/PageContainer.vue'
import { useUserStore } from '@/stores/user'
import GroupPanel from '@/views/video/components/GroupPanel.vue'
import ManagePanel from '@/views/video/components/ManagePanel.vue'
import MyTaskPanel from '@/views/video/components/MyTaskPanel.vue'
import UploadPanel from '@/views/video/components/UploadPanel.vue'

const userStore = useUserStore()

const canUpload = computed(() => userStore.hasPerm('video:upload'))
const canScore = computed(() => userStore.hasPerm('video:score'))
const canAssign = computed(() => userStore.hasPerm('video:assign'))
const canArbitrate = computed(() => userStore.hasPerm('video:arbitrate'))
const canConfirm = computed(() => userStore.hasPerm('video:confirm'))
const canPlay = computed(() => userStore.hasPerm('video:play'))
const canListReviews = computed(() => canUpload.value || canAssign.value || canArbitrate.value || canConfirm.value || canPlay.value)
const selfMode = computed(() => canUpload.value && !canAssign.value && !canScore.value)
const hasVisibleSection = computed(() => canListReviews.value || canScore.value || canAssign.value)
</script>

<template>
  <PageContainer title="视频评审" description="教学能力视频上传、评审评分、复评仲裁、退回重传与评审组指派。">
    <n-empty v-if="!hasVisibleSection" description="当前账号没有可访问的视频分区" class="page-section" />

    <n-tabs v-else type="line" animated>
      <n-tab-pane v-if="canListReviews" name="reviews" :tab="selfMode ? '我的视频' : '评审管理'">
        <UploadPanel v-if="selfMode" :can-play="canPlay" />
        <ManagePanel
          v-else
          :can-upload="canUpload"
          :can-assign="canAssign"
          :can-arbitrate="canArbitrate"
          :can-confirm="canConfirm"
          :can-play="canPlay"
        />
      </n-tab-pane>

      <n-tab-pane v-if="canScore" name="tasks" tab="我的评审">
        <MyTaskPanel />
      </n-tab-pane>

      <n-tab-pane v-if="canAssign" name="groups" tab="评审组">
        <GroupPanel />
      </n-tab-pane>
    </n-tabs>
  </PageContainer>
</template>
