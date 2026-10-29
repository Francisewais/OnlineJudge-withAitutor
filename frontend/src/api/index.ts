import { request } from './http'
import type {
  AdminProblem,
  AiStatus,
  AuthResponse,
  Difficulty,
  Language,
  Page,
  ProblemDetail,
  ProblemForm,
  ProblemSummary,
  RankItem,
  RunResult,
  SubmissionDetail,
  SubmissionSummary,
  User,
} from './types'

function query(params: Record<string, string | number | boolean | undefined | null>): string {
  const q = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v))
  }
  const s = q.toString()
  return s ? '?' + s : ''
}

export const api = {
  login: (username: string, password: string) =>
    request<AuthResponse>('POST', '/auth/login', { username, password }),
  register: (username: string, password: string) =>
    request<AuthResponse>('POST', '/auth/register', { username, password }),
  me: () => request<User>('GET', '/auth/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    request<void>('POST', '/auth/password', { oldPassword, newPassword }),

  problems: (p: { page: number; size: number; keyword?: string; difficulty?: Difficulty | '' }) =>
    request<Page<ProblemSummary>>('GET', '/problems' + query(p)),
  problem: (id: number) => request<ProblemDetail>('GET', `/problems/${id}`),
  editorial: (id: number) => request<{ editorial: string }>('GET', `/problems/${id}/editorial`),

  languages: () => request<Language[]>('GET', '/languages'),
  submit: (problemId: number, language: string, code: string) =>
    request<{ id: number }>('POST', '/submissions', { problemId, language, code }),
  submission: (id: number) => request<SubmissionDetail>('GET', `/submissions/${id}`),
  submissions: (p: {
    page: number
    size: number
    mine?: boolean
    problemId?: number
    username?: string
    verdict?: string
  }) => request<Page<SubmissionSummary>>('GET', '/submissions' + query(p)),
  run: (problemId: number | undefined, language: string, code: string, input: string) =>
    request<RunResult>('POST', '/run', { problemId, language, code, input }),

  ranking: (page: number, size: number) => request<Page<RankItem>>('GET', '/ranking' + query({ page, size })),
  aiStatus: () => request<AiStatus>('GET', '/ai/status'),

  adminProblems: (p: { page: number; size: number; keyword?: string }) =>
    request<Page<ProblemSummary>>('GET', '/admin/problems' + query(p)),
  adminProblem: (id: number) => request<AdminProblem>('GET', `/admin/problems/${id}`),
  createProblem: (form: ProblemForm) => request<{ id: number }>('POST', '/admin/problems', form),
  updateProblem: (id: number, form: ProblemForm) => request<void>('PUT', `/admin/problems/${id}`, form),
  deleteProblem: (id: number) => request<void>('DELETE', `/admin/problems/${id}`),
}
