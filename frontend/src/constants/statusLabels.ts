export type StatusTone = 'default' | 'success' | 'warning' | 'error' | 'info'

export const STATUS_META: Record<string, { label: string; tone: StatusTone }> = {
  DRAFT: { label: '草稿', tone: 'default' },
  WAIT_SUBMIT: { label: '待提交', tone: 'default' },
  FIRST_REVIEW: { label: '待初审', tone: 'warning' },
  FIRST_REJECTED: { label: '初审退回', tone: 'warning' },
  SECOND_REVIEW: { label: '待复审', tone: 'warning' },
  SECOND_REJECTED: { label: '复审退回', tone: 'warning' },
  PASSED: { label: '已通过', tone: 'success' },
  FAILED: { label: '不合格', tone: 'error' },
  PASS: { label: '通过', tone: 'success' },
  REJECT: { label: '退回', tone: 'error' },
  FAIL: { label: '不通过', tone: 'error' },
  LOCKED: { label: '已锁定', tone: 'warning' },
  NORMAL: { label: '正常', tone: 'success' },
  CONFIRMED: { label: '已确认', tone: 'success' },
  RETURNED: { label: '已退回', tone: 'warning' },
  WAIT_UPLOAD: { label: '待上传', tone: 'default' },
  VALIDATING: { label: '校验中', tone: 'info' },
  VALIDATION_FAILED: { label: '校验未通过', tone: 'error' },
  WAIT_REVIEW: { label: '待评审', tone: 'warning' },
  REVIEWING: { label: '评审中', tone: 'info' },
  NEED_REVIEW: { label: '需复评', tone: 'warning' },
  REVIEW_COMPLETED: { label: '评审完成', tone: 'success' },
  GENERATED: { label: '已生成', tone: 'warning' },
  ISSUED: { label: '已签发', tone: 'success' },
  EXPORTED: { label: '已导出', tone: 'info' },
  ARCHIVED: { label: '已归档', tone: 'success' },
  VOIDED: { label: '已作废', tone: 'error' },
  REISSUED: { label: '已重开', tone: 'warning' },
  COMPLETED: { label: '已完成', tone: 'success' },
  RUNNING: { label: '运行中', tone: 'info' },
  PENDING: { label: '待处理', tone: 'warning' },
  FAILED_TASK: { label: '失败', tone: 'error' },
  ENABLED: { label: '启用', tone: 'success' },
  DISABLED: { label: '停用', tone: 'error' },
  UNREAD: { label: '未读', tone: 'warning' },
  READ: { label: '已读', tone: 'success' },
  IMPORTED: { label: '导入成功', tone: 'success' },
  IMPORTING: { label: '导入中', tone: 'info' },
  PREVALIDATED: { label: '预校验通过', tone: 'info' },
  ROLLED_BACK: { label: '已回滚', tone: 'warning' },
  INSERT: { label: '新增', tone: 'info' },
  UPDATE: { label: '更新', tone: 'info' },
  DELETE: { label: '删除', tone: 'error' },
  SUCCESS: { label: '成功', tone: 'success' },
  ERROR: { label: '失败', tone: 'error' },
  WARNING: { label: '警告', tone: 'warning' },
  INFO: { label: '提示', tone: 'info' },
  qualified: { label: '合格', tone: 'success' },
  unqualified: { label: '不合格', tone: 'error' },
  exempted: { label: '免考', tone: 'success' },
  pending_confirm: { label: '待确认', tone: 'warning' }
}

export const STATUS_LABELS: Record<string, string> = Object.fromEntries(
  Object.entries(STATUS_META).map(([code, meta]) => [code, meta.label])
)

const STATUS_TONE_ALIASES: Record<string, StatusTone> = {
  未开始: 'default',
  待审核: 'warning',
  待确认: 'warning',
  进行中: 'info',
  待签发: 'warning',
  待复评: 'warning',
  初审通过: 'info',
  合格: 'success',
  锁定: 'error',
  异常: 'error'
}

export const OPERATION_LABELS: Record<string, string> = {
  save: '保存',
  create: '新增',
  update: '更新',
  submit: '提交',
  firstReview: '初审',
  secondReview: '复审',
  confirm: '确认',
  return: '退回',
  reject: '退回',
  fail: '不通过',
  assign: '指派',
  score: '评分',
  generate: '生成',
  issue: '签发',
  export: '导出',
  import: '导入',
  archive: '归档',
  void: '作废',
  reissue: '重开',
  correct: '更正',
  rollback: '回滚',
  login: '登录',
  delete: '删除',
  backup: '备份',
  trigger: '触发',
  read: '已读',
  markRead: '标记已读',
  'student:create': '新增学生',
  'student:update': '更新学生',
  'student:submit': '提交学生信息',
  'student:firstReview': '学生信息初审',
  'student:secondReview': '学生信息复审',
  'training:create': '新增培养信息',
  'training:update': '更新培养信息',
  'training:submit': '提交培养信息',
  'training:firstReview': '培养信息初审',
  'training:secondReview': '培养信息复审',
  'material:submit': '提交材料',
  'material:firstReview': '材料初审',
  'material:secondReview': '材料复审',
  'exemption:submit': '提交免考申请',
  'exemption:firstReview': '免考初审',
  'exemption:secondReview': '免考复审',
  'video:assign': '视频指派',
  'video:score': '视频评分',
  'video:arbitrate': '视频仲裁',
  'video:confirm': '视频确认',
  'cert:generate': '证书生成',
  'cert:issue': '证书签发',
  'cert:void': '证书作废',
  'cert:reissue': '证书重开',
  'cert:correct': '证书更正',
  'exchange:import': '导入',
  'exchange:export': '导出',
  'exchange:rollback': '导入回滚'
}

export function statusLabel(code?: string | null) {
  if (!code) return '-'
  return STATUS_LABELS[code] || code
}

export function statusTone(codeOrLabel?: string | null, fallbackLabel?: string | null): StatusTone {
  if (!codeOrLabel) return fallbackLabel ? statusTone(fallbackLabel) : 'default'
  const direct = STATUS_META[codeOrLabel]
  if (direct) return direct.tone
  const byLabel = Object.values(STATUS_META).find((meta) => meta.label === codeOrLabel)
  if (byLabel) return byLabel.tone
  const alias = STATUS_TONE_ALIASES[codeOrLabel]
  if (alias) return alias
  return fallbackLabel && fallbackLabel !== codeOrLabel ? statusTone(fallbackLabel) : 'default'
}

export function operationLabel(code?: string | null) {
  if (!code) return '-'
  return OPERATION_LABELS[code] || code
}
