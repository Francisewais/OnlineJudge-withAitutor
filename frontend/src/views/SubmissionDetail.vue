<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import type { SubmissionDetail } from '@/api/types'
import { useTheme } from '@/stores/theme'
import { formatMemory, formatTime } from '@/utils/format'
import CaseResults from '@/components/CaseResults.vue'
import CodeEditor from '@/components/CodeEditor.vue'
import VerdictTag from '@/components/VerdictTag.vue'

const props = defineProps<{ id: string }>()
const theme = useTheme()
const detail = ref<SubmissionDetail | null>(null)
const notFound = ref(false)
let timer: number | undefined

async function load() {
  try {
    detail.value = await api.submission(Number(props.id))
    clearTimeout(timer)
    if (detail.value.status !== 'FINISHED') timer = window.setTimeout(load, 1000)
  } catch (e) {
    notFound.value = true
    ElMessage.error((e as Error).message)
  }
}

watch(() => props.id, load, { immediate: true })
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="page">
    <el-result v-if="notFound" icon="warning" title="提交记录不存在" />
    <el-skeleton v-else-if="!detail" :rows="8" animated />
    <template v-else>
      <h1 class="page-title">提交 #{{ detail.id }}</h1>
      <div class="card">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="题目">
            <router-link :to="`/problems/${detail.problemId}`">{{ detail.problemId }}. {{ detail.problemTitle }}</router-link>
          </el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.username }}</el-descriptions-item>
          <el-descriptions-item label="语言">{{ detail.language }}</el-descriptions-item>
          <el-descriptions-item label="结果"><VerdictTag :verdict="detail.verdict" :status="detail.status" /></el-descriptions-item>
          <el-descriptions-item label="通过测试点">{{ detail.passedCount }} / {{ detail.totalCount }}</el-descriptions-item>
          <el-descriptions-item label="用时 / 内存">
            {{ detail.timeMs ?? '-' }} ms / {{ formatMemory(detail.memoryKb) }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="评测时间">{{ formatTime(detail.judgedAt) }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.compileMessage">
          <h3>编译信息</h3>
          <pre class="error-text">{{ detail.compileMessage }}</pre>
        </template>
        <template v-if="detail.cases.length">
          <h3>测试点</h3>
          <CaseResults :cases="detail.cases" />
        </template>

        <h3>代码</h3>
        <div v-if="detail.code" class="code-box">
          <CodeEditor :model-value="detail.code" :language="detail.language" :dark="theme.dark" readonly />
        </div>
        <el-alert v-else type="info" :closable="false" title="只有提交者本人和管理员可以查看代码" />
      </div>
    </template>
  </div>
</template>

<style scoped>
h3 {
  margin: 24px 0 10px;
  font-size: 16px;
}
.code-box {
  height: 480px;
}
.error-text {
  color: var(--el-color-danger);
}
</style>
