import { load, save } from '@/utils/storage'

const TOKEN_KEY = 'oj-token'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

export function getToken(): string | null {
  return load(TOKEN_KEY)
}

export function setToken(token: string | null) {
  save(TOKEN_KEY, token)
}

const STATUS_MESSAGES: Record<number, string> = {
  401: '请先登录',
  403: '没有权限',
  404: '内容不存在',
  429: '操作太频繁了，请稍后再试',
  502: '服务暂时不可用，请稍后再试',
  503: '服务暂时不可用，请稍后再试',
}

export async function errorFrom(resp: Response): Promise<ApiError> {
  let message = STATUS_MESSAGES[resp.status] ?? `请求失败（HTTP ${resp.status}）`
  try {
    const data = await resp.json()
    if (data && typeof data.message === 'string') message = data.message
  } catch {
    // 不是 JSON
  }
  return new ApiError(resp.status, message)
}

export function authHeaders(): Record<string, string> {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** 登录过期后清掉 token；GET 请求再匿名重试一次，公开页面不受影响 */
export async function request<T>(method: string, path: string, body?: unknown, retry = true): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...authHeaders() }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  let resp: Response
  try {
    resp = await fetch('/api' + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, '网络连接失败，请检查网络')
  }
  if (resp.status === 401 && headers.Authorization) {
    setToken(null)
    window.dispatchEvent(new Event('oj:logout'))
    if (method === 'GET' && retry) return request<T>(method, path, body, false)
  }
  if (!resp.ok) throw await errorFrom(resp)
  const text = await resp.text()
  return (text ? JSON.parse(text) : undefined) as T
}
