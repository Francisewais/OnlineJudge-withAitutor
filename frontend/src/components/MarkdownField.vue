<script setup lang="ts">
import { ref } from 'vue'
import MarkdownView from './MarkdownView.vue'

const value = defineModel<string>({ required: true })
defineProps<{ rows?: number; placeholder?: string }>()
const tab = ref<'edit' | 'preview'>('edit')
</script>

<template>
  <div class="md-field">
    <el-segmented
      v-model="tab"
      size="small"
      :options="[
        { label: '编辑', value: 'edit' },
        { label: '预览', value: 'preview' },
      ]"
    />
    <el-input v-if="tab === 'edit'" v-model="value" type="textarea" :rows="rows ?? 6" :placeholder="placeholder" class="md-input" />
    <div v-else class="md-preview">
      <MarkdownView v-if="value" :source="value" />
      <span v-else class="muted">（空）</span>
    </div>
  </div>
</template>

<style scoped>
.md-field {
  width: 100%;
}
.md-input,
.md-preview {
  margin-top: 6px;
}
.md-input :deep(textarea) {
  font-family: var(--code-font);
}
.md-preview {
  min-height: 80px;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}
</style>
