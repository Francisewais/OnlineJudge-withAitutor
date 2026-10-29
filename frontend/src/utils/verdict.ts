export type TagType = 'success' | 'warning' | 'danger' | 'info' | 'primary'

const VERDICTS: Record<string, { label: string; type: TagType }> = {
  AC: { label: '答案正确', type: 'success' },
  WA: { label: '答案错误', type: 'danger' },
  TLE: { label: '运行超时', type: 'warning' },
  MLE: { label: '内存超限', type: 'warning' },
  RE: { label: '运行出错', type: 'danger' },
  OLE: { label: '输出超限', type: 'warning' },
  CE: { label: '编译错误', type: 'info' },
  SE: { label: '系统错误', type: 'info' },
  OK: { label: '运行完成', type: 'success' },
}

export function verdictInfo(verdict: string | null | undefined, status?: string): { label: string; type: TagType } {
  if (status === 'PENDING') return { label: '等待评测', type: 'info' }
  if (status === 'JUDGING') return { label: '评测中', type: 'primary' }
  return VERDICTS[verdict ?? ''] ?? { label: verdict ?? '-', type: 'info' }
}

export const VERDICT_OPTIONS = ['AC', 'WA', 'TLE', 'MLE', 'RE', 'OLE', 'CE', 'SE'].map((v) => ({
  value: v,
  label: `${v} ${VERDICTS[v].label}`,
}))

export const DIFFICULTIES = {
  EASY: { label: '简单', type: 'success' as TagType },
  MEDIUM: { label: '中等', type: 'warning' as TagType },
  HARD: { label: '困难', type: 'danger' as TagType },
}
