// Phase 0 退回整改（U-004）：前端最小 lint 门禁（ESLint 9 flat config）。
//
// 规则取舍与后端 checkstyte 同口径：只收录「违反即真实缺陷」的客观规则（vue3 essential +
// 无未使用变量/悬空调试语句），不含格式化规则——格式统一属 WS-6 的可持续质量门禁范围，
// 在存量 74 个 .vue / 33 个 .ts 上强推格式化会制造大面积无语义 diff。
// vue3 recommended/strongly-recommended 升级同样移交 WS-6。
import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'

export default defineConfigWithVueTs(
  { ignores: ['dist/**', 'node_modules/**'] },
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,
  {
    rules: {
      // 存量代码大量使用 any 做渐进类型化（AGENTS.md §3.2 只禁「滥用」）；一刀切 error 会
      // 阻塞门禁落地。降为 off，收紧交给 WS-6 做渐进整改。
      '@typescript-eslint/no-explicit-any': 'off',
      // 未使用变量是真实缺陷信号；允许 _ 前缀显式豁免（与社区惯例一致）。
      '@typescript-eslint/no-unused-vars': ['error', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        caughtErrors: 'none',
      }],
      'no-debugger': 'error',
    },
  },
)
