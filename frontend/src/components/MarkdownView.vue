<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{ source: string | null | undefined }>()
// renderMarkdown 已用 DOMPurify 过滤，可以安全地 v-html
const html = computed(() => renderMarkdown(props.source))
</script>

<template>
  <div class="markdown-body" v-html="html" />
</template>
