<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Upload } from '@element-plus/icons-vue'
import { api } from '@/api'
import type { ProblemForm } from '@/api/types'
import MarkdownField from '@/components/MarkdownField.vue'

const props = defineProps<{ id?: string }>()
const router = useRouter()
const editing = computed(() => props.id !== undefined)
const loading = ref(false)
const saving = ref(false)
const fileInput = ref<HTMLInputElement>()
// 太大的测试数据默认折叠，避免页面卡顿
const LARGE = 20 * 1024
const expanded = ref(new Set<number>())

const form = reactive<ProblemForm>({
  title: '',
  difficulty: 'EASY',
  tags: [],
  description: '',
  inputFormat: '',
  outputFormat: '',
  samples: [{ input: '', output: '' }],
  hint: '',
  editorial: '',
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  visible: true,
  testCases: [],
})

onMounted(async () => {
  if (!editing.value) return
  loading.value = true
  try {
    const p = await api.adminProblem(Number(props.id))
    Object.assign(form, {
      title: p.title,
      difficulty: p.difficulty,
      tags: p.tags,
      description: p.description,
      inputFormat: p.inputFormat,
      outputFormat: p.outputFormat,
      samples: p.samples,
      hint: p.hint,
      editorial: p.editorial,
      timeLimitMs: p.timeLimitMs,
      memoryLimitMb: p.memoryLimitMb,
      visible: p.visible,
      testCases: p.testCases,
    })
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
})

const sizeOf = (s: string) => (s.length >= 1024 ? `${(s.length / 1024).toFixed(1)} KB` : `${s.length} B`)
const isOpen = (i: number) => expanded.value.has(i) || form.testCases[i].input.length + form.testCases[i].output.length < LARGE

function toggle(i: number) {
  if (expanded.value.has(i)) expanded.value.delete(i)
  else expanded.value.add(i)
}

function copySamples() {
  form.testCases.push(...form.samples.filter((s) => s.input || s.output).map((s) => ({ ...s })))
}

/** 选择 1.in / 1.out（或 .ans）这样成对的文件，按文件名配对后追加为测试数据 */
async function importFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files ?? [])
  const groups = new Map<string, { input?: string; output?: string }>()
  for (const file of files) {
    const match = file.name.match(/^(.*)\.(in|out|ans)$/)
    if (!match) continue
    const group = groups.get(match[1]) ?? {}
    const text = await file.text()
    if (match[2] === 'in') group.input = text
    else group.output = text
    groups.set(match[1], group)
  }
  const names = [...groups.keys()].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))
  let added = 0
  for (const name of names) {
    const g = groups.get(name)!
    if (g.input !== undefined && g.output !== undefined) {
      form.testCases.push({ input: g.input, output: g.output })
      added++
    }
  }
  ElMessage[added ? 'success' : 'warning'](added ? `导入了 ${added} 组测试数据` : '没有找到成对的 .in / .out 文件')
  if (fileInput.value) fileInput.value.value = ''
}

async function save() {
  if (!form.title.trim() || !form.description.trim()) {
    ElMessage.warning('标题和题目描述不能为空')
    return
  }
  if (!form.testCases.length) {
    ElMessage.warning('至少需要一组测试数据')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, samples: form.samples.filter((s) => s.input || s.output) }
    if (editing.value) await api.updateProblem(Number(props.id), payload)
    else await api.createProblem(payload)
    ElMessage.success('已保存')
    router.push('/admin/problems')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page">
    <h1 class="page-title">{{ editing ? '编辑题目' : '新建题目' }}</h1>
    <div v-loading="loading" class="card">
      <el-form label-width="100px" label-position="left" @submit.prevent>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="form.difficulty">
            <el-radio-button value="EASY">简单</el-radio-button>
            <el-radio-button value="MEDIUM">中等</el-radio-button>
            <el-radio-button value="HARD">困难</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="form.tags" multiple filterable allow-create default-first-option placeholder="输入后回车添加，例如：动态规划" style="width: 100%" />
        </el-form-item>
        <el-form-item label="限制">
          <div class="limits">
            <el-input-number v-model="form.timeLimitMs" :min="100" :max="10000" :step="100" />
            <span>ms</span>
            <el-input-number v-model="form.memoryLimitMb" :min="16" :max="1024" :step="16" />
            <span>MB</span>
            <el-switch v-model="form.visible" active-text="公开" inactive-text="隐藏" />
          </div>
        </el-form-item>
        <el-form-item label="题目描述" required>
          <MarkdownField v-model="form.description" :rows="8" placeholder="支持 Markdown 和 $\LaTeX$ 公式" />
        </el-form-item>
        <el-form-item label="输入格式"><MarkdownField v-model="form.inputFormat" :rows="3" /></el-form-item>
        <el-form-item label="输出格式"><MarkdownField v-model="form.outputFormat" :rows="3" /></el-form-item>

        <el-form-item label="样例">
          <div class="pairs">
            <div v-for="(s, i) in form.samples" :key="i" class="pair">
              <el-input v-model="s.input" type="textarea" :rows="3" :placeholder="`样例输入 ${i + 1}`" class="mono" />
              <el-input v-model="s.output" type="textarea" :rows="3" :placeholder="`样例输出 ${i + 1}`" class="mono" />
              <el-button :icon="Delete" circle text @click="form.samples.splice(i, 1)" />
            </div>
            <el-button :icon="Plus" @click="form.samples.push({ input: '', output: '' })">添加样例</el-button>
          </div>
        </el-form-item>

        <el-form-item label="提示"><MarkdownField v-model="form.hint" :rows="3" placeholder="数据范围、样例解释等" /></el-form-item>
        <el-form-item label="题解">
          <MarkdownField v-model="form.editorial" :rows="10" placeholder="思路讲解和参考代码，学生做不出来时可以查看；AI 助教也会参考它" />
        </el-form-item>

        <el-form-item label="测试数据" required>
          <div class="pairs">
            <div class="case-actions">
              <el-button :icon="Plus" @click="form.testCases.push({ input: '', output: '' })">添加一组</el-button>
              <el-button @click="copySamples">把样例加入测试数据</el-button>
              <el-button :icon="Upload" @click="fileInput?.click()">导入 .in / .out 文件</el-button>
              <input ref="fileInput" type="file" multiple accept=".in,.out,.ans" hidden @change="importFiles" />
              <span class="muted">共 {{ form.testCases.length }} 组，每组的输出用来和程序输出逐行比较（忽略行尾空格）</span>
            </div>
            <div v-for="(c, i) in form.testCases" :key="i" class="case">
              <div class="case-head">
                <b>#{{ i + 1 }}</b>
                <span class="muted">输入 {{ sizeOf(c.input) }}，输出 {{ sizeOf(c.output) }}</span>
                <el-button v-if="c.input.length + c.output.length >= LARGE" link type="primary" @click="toggle(i)">
                  {{ isOpen(i) ? '收起' : '展开编辑' }}
                </el-button>
                <el-button :icon="Delete" link type="danger" @click="form.testCases.splice(i, 1)">删除</el-button>
              </div>
              <div v-if="isOpen(i)" class="pair">
                <el-input v-model="c.input" type="textarea" :rows="3" placeholder="输入" class="mono" />
                <el-input v-model="c.output" type="textarea" :rows="3" placeholder="期望输出" class="mono" />
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          <el-button @click="router.push('/admin/problems')">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.limits {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}
.pairs {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.pair {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  align-items: start;
}
.case .pair {
  grid-template-columns: 1fr 1fr;
}
.case-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.case-actions .el-button + .el-button {
  margin-left: 0;
}
.case {
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.case-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}
.mono :deep(textarea) {
  font-family: var(--code-font);
}
</style>
