<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import { api } from '@/api'
import { ApiError } from '@/api/http'
import type { Language, ProblemDetail, RunResult, SubmissionDetail, SubmissionSummary } from '@/api/types'
import { useAuth } from '@/stores/auth'
import { useTheme } from '@/stores/theme'
import { load, save } from '@/utils/storage'
import { LANGUAGE_FALLBACK, TEMPLATES } from '@/utils/templates'
import { SITE_NAME, acceptRate, formatMemory, formatTime } from '@/utils/format'
import AiTutor from '@/components/AiTutor.vue'
import CaseResults from '@/components/CaseResults.vue'
import CodeEditor from '@/components/CodeEditor.vue'
import DifficultyTag from '@/components/DifficultyTag.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import VerdictTag from '@/components/VerdictTag.vue'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const auth = useAuth()
const theme = useTheme()

const problemId = computed(() => Number(props.id))
const problem = ref<ProblemDetail | null>(null)
const notFound = ref(false)
const languages = ref<Language[]>(LANGUAGE_FALLBACK)
const language = ref(load('oj-language') ?? 'cpp')
const code = ref('')
const leftTab = ref('statement')
const consoleTab = ref('run')
const runInput = ref('')
const runResult = ref<RunResult | null>(null)
const running = ref(false)
const submission = ref<SubmissionDetail | null>(null)
const submitting = ref(false)
const editorial = ref<string | null>(null)
const loadingEditorial = ref(false)
const mySubmissions = ref<SubmissionSummary[]>([])
const tutor = ref<InstanceType<typeof AiTutor>>()
let disposed = false

const timeFactor = computed(() => languages.value.find((l) => l.id === language.value)?.timeFactor ?? 1)
const draftKey = () => `oj-code-${problemId.value}-${language.value}`

function loadDraft() {
  code.value = load(draftKey()) ?? TEMPLATES[language.value] ?? ''
}

async function loadProblem() {
  notFound.value = false
  problem.value = null
  editorial.value = null
  submission.value = null
  runResult.value = null
  try {
    const p = await api.problem(problemId.value)
    problem.value = p
    document.title = `${p.title} - ${SITE_NAME}`
    runInput.value = p.samples[0]?.input ?? ''
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound.value = true
    else ElMessage.error((e as Error).message)
  }
  loadDraft()
  loadMine()
}

async function loadMine() {
  if (!auth.loggedIn) {
    mySubmissions.value = []
    return
  }
  try {
    mySubmissions.value = (await api.submissions({ page: 1, size: 20, mine: true, problemId: problemId.value })).items
  } catch {
    // 不影响做题
  }
}

api.languages().then((list) => (languages.value = list)).catch(() => {})
watch(problemId, loadProblem, { immediate: true })
watch(language, (lang) => {
  save('oj-language', lang)
  loadDraft()
})
// 每种语言的代码分别保存草稿，刷新页面不会丢
watch(code, (value) => save(draftKey(), value))
watch(() => auth.user?.id, loadMine)
onBeforeUnmount(() => (disposed = true))

function needLogin() {
  ElMessageBox.confirm('登录后才能运行和提交代码，现在去登录吗？', '需要登录', {
    confirmButtonText: '去登录',
    cancelButtonText: '取消',
  })
    .then(() => router.push({ path: '/login', query: { redirect: route.fullPath } }))
    .catch(() => {})
}

async function run() {
  if (!auth.loggedIn) return needLogin()
  running.value = true
  consoleTab.value = 'run'
  try {
    runResult.value = await api.run(problemId.value, language.value, code.value, runInput.value)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    running.value = false
  }
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

async function submitCode() {
  if (!auth.loggedIn) return needLogin()
  if (!code.value.trim()) {
    ElMessage.warning('代码不能为空')
    return
  }
  submitting.value = true
  try {
    const { id } = await api.submit(problemId.value, language.value, code.value)
    consoleTab.value = 'result'
    for (let i = 0; i < 600 && !disposed; i++) {
      const s = await api.submission(id)
      submission.value = s
      if (s.status === 'FINISHED') {
        if (s.verdict === 'AC') ElMessage.success('恭喜，答案正确！')
        loadMine()
        refreshStats()
        break
      }
      await sleep(i < 10 ? 500 : 1000)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}

async function refreshStats() {
  try {
    const p = await api.problem(problemId.value)
    if (problem.value) Object.assign(problem.value, { status: p.status, submitCount: p.submitCount, acceptedCount: p.acceptedCount })
  } catch {
    // 忽略
  }
  auth.restore()
}

async function openSubmission(row: SubmissionSummary) {
  try {
    submission.value = await api.submission(row.id)
    consoleTab.value = 'result'
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function loadSubmissionCode() {
  const s = submission.value
  if (!s?.code) return
  ElMessageBox.confirm('用这次提交的代码替换编辑器里的内容吗？', '载入代码', { type: 'warning' })
    .then(() => {
      language.value = s.language
      nextTick(() => (code.value = s.code!))
    })
    .catch(() => {})
}

async function showEditorial() {
  try {
    await ElMessageBox.confirm('建议先自己想一想，或者让 AI 助教给一点提示。确定要直接看题解吗？', '查看题解', {
      confirmButtonText: '看题解',
      cancelButtonText: '再想想',
      type: 'info',
    })
  } catch {
    return
  }
  loadingEditorial.value = true
  try {
    editorial.value = (await api.editorial(problemId.value)).editorial
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loadingEditorial.value = false
  }
}

function askTutor(mode: 'HINT' | 'DEBUG') {
  leftTab.value = 'ai'
  nextTick(() => tutor.value?.ask(mode))
}

function resetCode() {
  ElMessageBox.confirm('恢复成初始模板？当前代码会被覆盖。', '重置代码', { type: 'warning' })
    .then(() => (code.value = TEMPLATES[language.value] ?? ''))
    .catch(() => {})
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // 非 https 页面没有剪贴板 API，退回到旧方法
    const el = document.createElement('textarea')
    el.value = text
    document.body.appendChild(el)
    el.select()
    document.execCommand('copy')
    el.remove()
  }
  ElMessage.success('已复制')
}
</script>

<template>
  <div v-if="notFound" class="page">
    <el-result icon="warning" title="题目不存在">
      <template #extra><el-button type="primary" @click="router.push('/problems')">返回题库</el-button></template>
    </el-result>
  </div>

  <div v-else class="workspace">
    <section class="pane pane-left card">
      <el-skeleton v-if="!problem" :rows="10" animated />
      <el-tabs v-else v-model="leftTab" class="pane-tabs">
        <el-tab-pane label="题目" name="statement">
          <div class="statement">
            <h2 class="problem-heading">
              {{ problem.id }}. {{ problem.title }}
              <el-tag v-if="problem.status === 'solved'" type="success" size="small" disable-transitions>已通过</el-tag>
            </h2>
            <div class="problem-meta">
              <DifficultyTag :difficulty="problem.difficulty" />
              <el-tag v-for="t in problem.tags" :key="t" size="small" type="info" effect="plain" disable-transitions>{{ t }}</el-tag>
              <span>时间限制 {{ problem.timeLimitMs }} ms</span>
              <span>内存限制 {{ problem.memoryLimitMb }} MB</span>
              <span>通过率 {{ acceptRate(problem.acceptedCount, problem.submitCount) }}</span>
            </div>

            <h3>题目描述</h3>
            <MarkdownView :source="problem.description" />
            <template v-if="problem.inputFormat">
              <h3>输入格式</h3>
              <MarkdownView :source="problem.inputFormat" />
            </template>
            <template v-if="problem.outputFormat">
              <h3>输出格式</h3>
              <MarkdownView :source="problem.outputFormat" />
            </template>

            <div v-for="(s, i) in problem.samples" :key="i" class="sample">
              <div>
                <div class="sample-head">
                  样例输入 {{ i + 1 }}
                  <el-button text size="small" :icon="CopyDocument" @click="copy(s.input)">复制</el-button>
                  <el-button text size="small" @click="runInput = s.input; consoleTab = 'run'">用作自测输入</el-button>
                </div>
                <pre>{{ s.input }}</pre>
              </div>
              <div>
                <div class="sample-head">样例输出 {{ i + 1 }}</div>
                <pre>{{ s.output }}</pre>
              </div>
            </div>

            <template v-if="problem.hint">
              <h3>提示</h3>
              <MarkdownView :source="problem.hint" />
            </template>
          </div>
        </el-tab-pane>

        <el-tab-pane label="题解" name="editorial">
          <el-empty v-if="!problem.hasEditorial" description="这道题还没有题解，可以问问 AI 助教">
            <el-button type="primary" @click="askTutor('HINT')">💡 让 AI 给点思路</el-button>
          </el-empty>
          <MarkdownView v-else-if="editorial !== null" :source="editorial" />
          <div v-else class="editorial-gate">
            <p>做不出来也没关系。看题解之前，可以先让 AI 助教给你一点提示，自己再试试。</p>
            <div class="gate-actions">
              <el-button @click="askTutor('HINT')">💡 先要一点提示</el-button>
              <el-button type="primary" :loading="loadingEditorial" @click="showEditorial">查看题解</el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane name="ai">
          <template #label><span class="ai-tab">✨ AI 助教</span></template>
          <AiTutor ref="tutor" :problem-id="problemId" :language="language" :code="code" :submission-id="submission?.id" />
        </el-tab-pane>

        <el-tab-pane label="我的提交" name="submissions">
          <el-empty v-if="!auth.loggedIn" description="登录后可以看到自己的提交记录" />
          <el-empty v-else-if="!mySubmissions.length" description="还没有提交过这道题" />
          <el-table v-else :data="mySubmissions" row-class-name="clickable" @row-click="openSubmission">
            <el-table-column label="结果" width="110">
              <template #default="{ row }"><VerdictTag :verdict="row.verdict" :status="row.status" size="small" /></template>
            </el-table-column>
            <el-table-column label="语言" prop="language" width="80" />
            <el-table-column label="用时" width="90">
              <template #default="{ row }">{{ row.timeMs ?? '-' }} ms</template>
            </el-table-column>
            <el-table-column label="提交时间" min-width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <section class="pane pane-right">
      <div class="editor-toolbar">
        <el-select v-model="language" style="width: 210px">
          <el-option v-for="l in languages" :key="l.id" :label="l.name" :value="l.id" />
        </el-select>
        <el-tooltip v-if="timeFactor > 1" :content="`这门语言运行较慢，时间限制放宽为 ${timeFactor} 倍`">
          <span class="muted factor">时限 ×{{ timeFactor }}</span>
        </el-tooltip>
        <div class="spacer" />
        <el-button text @click="resetCode">重置代码</el-button>
        <el-button :loading="running" @click="run">运行</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCode">{{ submitting ? '评测中' : '提交' }}</el-button>
      </div>

      <CodeEditor v-model="code" :language="language" :dark="theme.dark" class="editor" />

      <div class="console card">
        <el-tabs v-model="consoleTab">
          <el-tab-pane label="自测运行" name="run">
            <div class="run-grid">
              <div>
                <div class="label">输入</div>
                <el-input v-model="runInput" type="textarea" :rows="6" resize="none" class="mono" placeholder="在这里输入数据，点击「运行」查看程序输出" />
              </div>
              <div>
                <div class="label run-label">
                  输出
                  <template v-if="runResult">
                    <VerdictTag :verdict="runResult.verdict" size="small" />
                    <span v-if="runResult.verdict !== 'CE'" class="muted">{{ runResult.timeMs }} ms · {{ formatMemory(runResult.memoryKb) }}</span>
                  </template>
                </div>
                <div class="run-output">
                  <span v-if="!runResult" class="muted">点击「运行」，用左边的输入运行你的代码（不计入提交记录）</span>
                  <template v-else>
                    <pre v-if="runResult.compileMessage" class="error-text">{{ runResult.compileMessage }}</pre>
                    <pre v-if="runResult.message" class="error-text">{{ runResult.message }}</pre>
                    <pre v-if="runResult.stdout">{{ runResult.stdout }}</pre>
                    <pre v-if="runResult.stderr" class="error-text">{{ runResult.stderr }}</pre>
                    <span v-if="runResult.verdict === 'OK' && !runResult.stdout && !runResult.stderr" class="muted">（没有输出）</span>
                  </template>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="评测结果" name="result">
            <span v-if="!submission" class="muted">提交后在这里查看评测结果</span>
            <div v-else>
              <div class="result-head">
                <VerdictTag :verdict="submission.verdict" :status="submission.status" size="large" />
                <template v-if="submission.status === 'FINISHED' && submission.verdict !== 'CE'">
                  <span>通过 {{ submission.passedCount }} / {{ submission.totalCount }}</span>
                  <span class="muted">{{ submission.timeMs }} ms · {{ formatMemory(submission.memoryKb) }}</span>
                </template>
                <div class="spacer" />
                <el-button v-if="submission.code" text size="small" @click="loadSubmissionCode">载入这份代码</el-button>
                <el-button text size="small" @click="router.push(`/submissions/${submission.id}`)">详情</el-button>
                <el-button
                  v-if="submission.status === 'FINISHED' && submission.verdict !== 'AC'"
                  type="warning"
                  plain
                  size="small"
                  @click="askTutor('DEBUG')"
                >
                  🐞 让 AI 帮我找错
                </el-button>
              </div>
              <pre v-if="submission.compileMessage" class="error-text">{{ submission.compileMessage }}</pre>
              <CaseResults v-if="submission.cases.length" :cases="submission.cases" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>
  </div>
</template>

<style scoped>
.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 12px;
  height: calc(100vh - var(--header-height));
  padding: 12px;
}
.pane {
  display: flex;
  flex-direction: column;
  /* 允许网格列比内容（如标签页导航）更窄，否则手机上会撑出横向滚动 */
  min-width: 0;
  min-height: 0;
}
.pane-left {
  padding: 0 20px 20px;
  overflow: hidden;
}
.pane-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.pane-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
}
.pane-tabs :deep(.el-tab-pane) {
  height: 100%;
}
.problem-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 8px;
}
.problem-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.statement h3 {
  margin: 20px 0 8px;
  font-size: 16px;
}
.sample {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 16px;
}
.sample-head {
  display: flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  font-weight: 600;
  font-size: 14px;
}
.editorial-gate {
  padding: 32px 12px;
  text-align: center;
  line-height: 1.8;
}
.gate-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
.ai-tab {
  font-weight: 600;
}
.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.editor-toolbar .el-button + .el-button {
  margin-left: 0;
}
.factor {
  font-size: 13px;
}
.spacer {
  flex: 1;
}
.editor {
  flex: 1;
  min-height: 240px;
  background: var(--el-bg-color);
}
.console {
  height: 300px;
  margin-top: 8px;
  padding: 0 16px 12px;
  overflow-y: auto;
}
.run-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.run-label {
  display: flex;
  align-items: center;
  gap: 8px;
}
.run-output {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 180px;
  overflow-y: auto;
}
.mono :deep(textarea) {
  font-family: var(--code-font);
}
.result-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.result-head .el-button + .el-button {
  margin-left: 0;
}
.error-text {
  color: var(--el-color-danger);
}
@media (max-width: 1000px) {
  .workspace {
    grid-template-columns: 1fr;
    height: auto;
  }
  .pane-left {
    max-height: 70vh;
  }
  .editor {
    min-height: 420px;
  }
  .sample,
  .run-grid {
    grid-template-columns: 1fr;
  }
}
</style>
