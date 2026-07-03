import { defineStore } from 'pinia'
import { ref } from 'vue'
import { unreadNoticeCount } from '@/api/notice'

/**
 * 未读通知计数：菜单圆点/铃铛角标与通知中心共享同一份数据。
 * 通知中心标记已读后调用 refresh()，外壳角标立即同步（不再等 60s 轮询）。
 */
export const useNoticeStore = defineStore('notice', () => {
  const unreadCount = ref(0)

  async function refresh() {
    try {
      const res = await unreadNoticeCount()
      unreadCount.value = Number(res.data || 0)
    } catch {
      unreadCount.value = 0
    }
  }

  function reset() {
    unreadCount.value = 0
  }

  return { unreadCount, refresh, reset }
})
