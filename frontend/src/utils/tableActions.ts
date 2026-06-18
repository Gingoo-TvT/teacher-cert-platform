import { h, type VNodeChild } from 'vue'
import { NButton, NPopover, NSpace } from 'naive-ui'

export function renderTableActions(actions: VNodeChild[]) {
  const visibleActions = actions.filter(Boolean)
  if (visibleActions.length <= 3) {
    return h(NSpace, { class: 'table-actions', size: 4, justify: 'end' }, () => visibleActions)
  }
  return h(NSpace, { class: 'table-actions', size: 4, justify: 'end' }, () => [
    ...visibleActions.slice(0, 2),
    h(
      NPopover,
      { trigger: 'click', placement: 'bottom-end' },
      {
        trigger: () => h(NButton, { size: 'small', quaternary: true }, { default: () => '更多' }),
        default: () => h(NSpace, { vertical: true, size: 4, class: 'table-actions-popover' }, () => visibleActions.slice(2))
      }
    )
  ])
}

