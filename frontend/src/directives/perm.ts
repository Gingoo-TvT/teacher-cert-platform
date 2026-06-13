import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限指令 v-perm="'cert:generate'"：无权限则移除元素。
 */
export const permDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string>) {
    const userStore = useUserStore()
    const code = binding.value
    if (code && !userStore.hasPerm(code)) {
      el.parentNode?.removeChild(el)
    }
  }
}
