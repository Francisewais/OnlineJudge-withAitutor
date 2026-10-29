<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import type { AiStatus, ChatMessage, TutorMode } from '@/api/types'
import { postSse } from '@/utils/sse'
import { useAuth } from '@/stores/auth'
import MarkdownView from './MarkdownView.vue'

const props = defineProps<{
  problemId: number
  language: string
  code: string
  submissionId?: number | null
}>()

interface Turn {
  role: 'user' | 'assistant'
  content: string
  reasoning?: string
  error?: boolean
}

const MODES: { mode: TutorMode; icon: string; label: string; desc: string }[] = [
  { mode: 'HINT', icon: '💡', label: '给点思路', desc: '只提示解题方向，不给代码' },
  { mode: 'DEBUG', icon: '🐞', label: '帮我找错', desc: '结合你的代码和最近一次评测结果找问题' },
  { mode: 'EXPLAIN', icon: '📖', label: '讲解题解', desc: '完整讲解思路，并给出参考代码' },
]

const auth = useAuth()
const status = ref<AiStatus | null>(null)
const turns = ref<Turn[]>([])
const question = ref('')
const streaming = ref(false)
const list = ref<HTMLElement>()
let controller: AbortController | null = null

async function loadStatus() {
  try {
    status.value = await api.aiStatus()
  } catch {
    status.value = null
  }
}
onMounted(loadStatus)
watch(() => auth.user?.id, loadStatus)

function scrollToBottom() {
  nextTick(() => list.value?.scrollTo({ top: list.value.scrollHeight }))
}

/** 只把成功的问答对作为上下文发给后端 */
function history(): ChatMessage[] {
  const result: ChatMessage[] = []
  for (let i = 0; i + 1 < turns.value.length; i += 2) {
    const [q, a] = [turns.value[i], turns.value[i + 1]]
    if (!a.error && a.content) result.push({ role: 'user', content: q.content }, { role: 'assistant', content: a.content })
  }
  return result.slice(-10)
}

async function ask(mode: TutorMode, text?: string) {
  if (streaming.value) return
  if (!auth.loggedIn) {
    ElMessage.warning('登录后才能使用 AI 助教')
    return
  }
  const preset = MODES.find((m) => m.mode === mode)
  const context = history()
  turns.value.push({ role: 'user', content: text ?? `${preset?.icon} ${preset?.label}` })
  turns.value.push({ role: 'assistant', content: '', reasoning: '' })
  const answer = turns.value[turns.value.length - 1]
  streaming.value = true
  controller = new AbortController()
  scrollToBottom()
  try {
    await postSse(
      '/ai/tutor',
      {
        problemId: props.problemId,
        mode,
        language: props.language,
        code: props.code,
        submissionId: props.submissionId ?? undefined,
        history: context,
        question: mode === 'CHAT' ? text : undefined,
      },
      (e) => {
        const data = JSON.parse(e.data)
        if (e.event === 'delta') answer.content += data.text
        else if (e.event === 'reasoning') answer.reasoning += data.text
        else if (e.event === 'error') {
          answer.error = true
          answer.content += (answer.content ? '\n\n' : '') + `⚠️ ${data.message}`
        }
        scrollToBottom()
      },
      controller.signal,
    )
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      answer.content += answer.content ? '\n\n（已停止）' : '（已停止）'
    } else {
      answer.error = true
      answer.content = `⚠️ ${(e as Error).message}`
    }
  } finally {
    streaming.value = false
    controller = null
    if (!answer.content) answer.content = '（没有收到回答）'
    loadStatus()
  }
}

function send() {
  const text = question.value.trim()
  if (!text || streaming.value) return
  question.value = ''
  ask('CHAT', text)
}

function stop() {
  controller?.abort()
}

defineExpose({ ask })
</script>

<template>
  <div class="ai-tutor">
    <el-alert
      v-if="status && !status.enabled"
      type="info"
      :closable="false"
      show-icon
      title="AI 助教还没有开启"
      description="管理员在 .env 里填写 OJ_AI_API_KEY（例如 DeepSeek 的 API Key）并重启后端后即可使用。"
    />
    <template v-else>
      <div class="ai-head">
        <div class="ai-modes">
          <el-tooltip v-for="m in MODES" :key="m.mode" :content="m.desc" placement="bottom">
            <el-button :disabled="streaming" @click="ask(m.mode)">{{ m.icon }} {{ m.label }}</el-button>
          </el-tooltip>
        </div>
        <div class="ai-meta">
          <span v-if="status && status.remaining !== null && auth.loggedIn">今天还能问 {{ status.remaining }} 次</span>
          <el-button v-if="turns.length" text size="small" :disabled="streaming" @click="turns = []">清空对话</el-button>
        </div>
      </div>

      <div ref="list" class="ai-messages">
        <div v-if="!turns.length" class="ai-empty">
          <p>卡住了？AI 助教会结合<b>题目</b>、<b>你现在的代码</b>和<b>最近一次评测结果</b>来帮你。</p>
          <p class="muted">建议先点「给点思路」，自己动手写；实在想不出来再看完整讲解。</p>
        </div>
        <div v-for="(t, i) in turns" :key="i" class="ai-turn" :class="t.role">
          <div v-if="t.role === 'user'" class="bubble user">{{ t.content }}</div>
          <template v-else>
            <details v-if="t.reasoning" class="reasoning">
              <summary>思考过程</summary>
              <div class="reasoning-text">{{ t.reasoning }}</div>
            </details>
            <div class="bubble assistant" :class="{ error: t.error }">
              <MarkdownView v-if="t.content" :source="t.content" />
              <span v-else class="muted">AI 正在思考…</span>
            </div>
          </template>
        </div>
      </div>

      <div class="ai-input">
        <el-input
          v-model="question"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          resize="none"
          placeholder="继续追问，例如：为什么这里要用二分？（Enter 发送，Shift + Enter 换行）"
          :disabled="streaming"
          @keydown.enter.exact.prevent="send"
        />
        <el-button v-if="streaming" type="danger" plain @click="stop">停止</el-button>
        <el-button v-else type="primary" :disabled="!question.trim()" @click="send">发送</el-button>
      </div>
      <p class="ai-disclaimer">AI 的回答可能有错误，请结合自己的理解判断。</p>
    </template>
  </div>
</template>

<style scoped>
.ai-tutor {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 360px;
}
.ai-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ai-modes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.ai-modes .el-button + .el-button {
  margin-left: 0;
}
.ai-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.ai-messages {
  flex: 1;
  min-height: 200px;
  overflow-y: auto;
  margin: 12px 0;
  padding-right: 4px;
}
.ai-empty {
  padding: 24px 8px;
  text-align: center;
  line-height: 1.8;
}
.ai-turn {
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
}
.ai-turn.user {
  align-items: flex-end;
}
.bubble {
  max-width: 100%;
  padding: 8px 12px;
  border-radius: 10px;
  overflow-wrap: anywhere;
}
.bubble.user {
  max-width: 85%;
  background: var(--el-color-primary);
  color: #fff;
  white-space: pre-wrap;
}
.bubble.assistant {
  background: var(--el-fill-color-light);
}
.bubble.assistant.error {
  background: var(--el-color-danger-light-9);
}
.reasoning {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.reasoning summary {
  cursor: pointer;
}
.reasoning-text {
  margin-top: 4px;
  padding-left: 10px;
  border-left: 3px solid var(--el-border-color);
  white-space: pre-wrap;
  max-height: 240px;
  overflow-y: auto;
}
.ai-input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.ai-disclaimer {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
