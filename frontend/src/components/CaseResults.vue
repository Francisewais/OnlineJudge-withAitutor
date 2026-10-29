<script setup lang="ts">
import { computed } from 'vue'
import type { CaseResult } from '@/api/types'
import { verdictInfo } from '@/utils/verdict'
import { formatMemory } from '@/utils/format'

const props = defineProps<{ cases: CaseResult[] }>()
// 只展示第一个出错测试点的错误信息
const firstFailure = computed(() => {
  const index = props.cases.findIndex((c) => c.verdict !== 'AC' && c.message)
  return index < 0 ? null : { index, message: props.cases[index].message }
})
</script>

<template>
  <div>
    <div class="case-grid">
      <el-tooltip
        v-for="(c, i) in cases"
        :key="i"
        :content="`${verdictInfo(c.verdict).label}，${c.timeMs} ms，${formatMemory(c.memoryKb)}`"
        placement="top"
      >
        <div class="case-cell" :class="`is-${verdictInfo(c.verdict).type}`">
          <span class="case-index">#{{ i + 1 }}</span>
          <span class="case-verdict">{{ c.verdict }}</span>
          <span class="case-time">{{ c.timeMs }} ms</span>
        </div>
      </el-tooltip>
    </div>
    <div v-if="firstFailure" class="case-message">
      <div class="label">第 {{ firstFailure.index + 1 }} 个测试点的错误信息</div>
      <pre>{{ firstFailure.message }}</pre>
    </div>
  </div>
</template>

<style scoped>
.case-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(84px, 1fr));
  gap: 8px;
}
.case-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 6px 4px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.4;
  color: #fff;
  cursor: default;
}
.case-verdict {
  font-weight: 700;
  font-size: 14px;
}
.is-success {
  background: var(--el-color-success);
}
.is-danger {
  background: var(--el-color-danger);
}
.is-warning {
  background: var(--el-color-warning);
}
.is-info,
.is-primary {
  background: var(--el-color-info);
}
.case-message {
  margin-top: 12px;
}
.case-message pre {
  margin: 4px 0 0;
  max-height: 200px;
  overflow: auto;
}
</style>
