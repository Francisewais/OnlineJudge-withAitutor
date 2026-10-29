export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '-'
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function formatMemory(kb: number | null | undefined): string {
  if (kb === null || kb === undefined) return '-'
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`
}

export function formatDuration(ms: number | null | undefined): string {
  return ms === null || ms === undefined ? '-' : `${ms} ms`
}

export function acceptRate(accepted: number, total: number): string {
  return total === 0 ? '-' : `${((accepted / total) * 100).toFixed(1)}%`
}

export const SITE_NAME = import.meta.env.VITE_SITE_NAME || 'Online Judge'
