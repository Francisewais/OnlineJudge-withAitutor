import { authHeaders, errorFrom } from '@/api/http'

export interface SseEvent {
  event: string
  data: string
}

/**
 * 用 POST 请求读取 text/event-stream（浏览器自带的 EventSource 只支持 GET）。
 * 返回前如果服务端以 JSON 报错（参数错误、次数用完等），抛出 ApiError。
 */
export async function postSse(path: string, body: unknown, onEvent: (e: SseEvent) => void, signal?: AbortSignal) {
  const resp = await fetch('/api' + path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream, application/json',
      ...authHeaders(),
    },
    body: JSON.stringify(body),
    signal,
  })
  if (!resp.ok || !resp.body) throw await errorFrom(resp)

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n?/g, '\n')
    let end: number
    while ((end = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, end)
      buffer = buffer.slice(end + 2)
      const parsed = parseBlock(block)
      if (parsed) onEvent(parsed)
    }
  }
}

function parseBlock(block: string): SseEvent | null {
  let event = 'message'
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  return data.length ? { event, data: data.join('\n') } : null
}
