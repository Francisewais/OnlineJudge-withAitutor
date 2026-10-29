// 与后端 *Dtos.java 对应

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface User {
  id: number
  username: string
  role: 'USER' | 'ADMIN'
  solvedCount: number
  submitCount: number
  createdAt: string
}

export interface AuthResponse {
  token: string
  user: User
}

export interface Sample {
  input: string
  output: string
}

export interface ProblemSummary {
  id: number
  title: string
  difficulty: Difficulty
  tags: string[]
  submitCount: number
  acceptedCount: number
  visible: boolean
  status: 'solved' | 'attempted' | null
}

export interface ProblemDetail {
  id: number
  title: string
  difficulty: Difficulty
  tags: string[]
  description: string
  inputFormat: string
  outputFormat: string
  samples: Sample[]
  hint: string
  timeLimitMs: number
  memoryLimitMb: number
  submitCount: number
  acceptedCount: number
  hasEditorial: boolean
  status: 'solved' | 'attempted' | null
}

export interface TestCaseForm {
  input: string
  output: string
}

export interface ProblemForm {
  title: string
  difficulty: Difficulty
  tags: string[]
  description: string
  inputFormat: string
  outputFormat: string
  samples: Sample[]
  hint: string
  editorial: string
  timeLimitMs: number
  memoryLimitMb: number
  visible: boolean
  testCases: TestCaseForm[]
}

export interface AdminProblem extends ProblemForm {
  id: number
  updatedAt: string
}

export interface CaseResult {
  verdict: string
  timeMs: number
  memoryKb: number
  exitCode: number
  signal: number
  message: string
}

export type SubmissionStatus = 'PENDING' | 'JUDGING' | 'FINISHED'

export interface SubmissionSummary {
  id: number
  problemId: number
  problemTitle: string
  username: string
  language: string
  status: SubmissionStatus
  verdict: string | null
  timeMs: number | null
  memoryKb: number | null
  passedCount: number
  totalCount: number
  createdAt: string
}

export interface SubmissionDetail extends SubmissionSummary {
  compileMessage: string | null
  cases: CaseResult[]
  code: string | null
  owner: boolean
  judgedAt: string | null
}

export interface RunResult {
  verdict: string
  stdout: string
  stderr: string
  compileMessage: string
  timeMs: number
  memoryKb: number
  exitCode: number
  signal: number
  message: string
}

export interface Language {
  id: string
  name: string
  timeFactor: number
}

export interface RankItem {
  rank: number
  username: string
  solvedCount: number
  submitCount: number
}

export type TutorMode = 'HINT' | 'DEBUG' | 'EXPLAIN' | 'CHAT'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AiStatus {
  enabled: boolean
  model: string
  dailyLimit: number
  remaining: number | null
}
