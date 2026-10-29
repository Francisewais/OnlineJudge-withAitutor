<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { basicSetup } from 'codemirror'
import { Compartment, EditorState, type Extension } from '@codemirror/state'
import { EditorView, keymap } from '@codemirror/view'
import { indentWithTab } from '@codemirror/commands'
import { indentUnit } from '@codemirror/language'
import { cpp } from '@codemirror/lang-cpp'
import { java } from '@codemirror/lang-java'
import { python } from '@codemirror/lang-python'
import { oneDark } from '@codemirror/theme-one-dark'

const props = defineProps<{
  modelValue: string
  language?: string
  dark?: boolean
  readonly?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const host = ref<HTMLDivElement>()
let view: EditorView | null = null
const languageConf = new Compartment()
const themeConf = new Compartment()

function languageExtension(lang?: string): Extension {
  switch (lang) {
    case 'c':
    case 'cpp':
      return cpp()
    case 'java':
      return java()
    case 'python':
      return python()
    default:
      return []
  }
}

onMounted(() => {
  view = new EditorView({
    parent: host.value!,
    state: EditorState.create({
      doc: props.modelValue,
      extensions: [
        basicSetup,
        keymap.of([indentWithTab]),
        indentUnit.of('    '),
        EditorState.tabSize.of(4),
        EditorState.readOnly.of(!!props.readonly),
        languageConf.of(languageExtension(props.language)),
        themeConf.of(props.dark ? oneDark : []),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) emit('update:modelValue', update.state.doc.toString())
        }),
        EditorView.theme({
          '&': { height: '100%', fontSize: '14px' },
          '.cm-scroller': { fontFamily: 'var(--code-font)', lineHeight: '1.6' },
        }),
      ],
    }),
  })
})

watch(
  () => props.modelValue,
  (value) => {
    if (view && value !== view.state.doc.toString()) {
      view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } })
    }
  },
)
watch(
  () => props.language,
  (lang) => view?.dispatch({ effects: languageConf.reconfigure(languageExtension(lang)) }),
)
watch(
  () => props.dark,
  (dark) => view?.dispatch({ effects: themeConf.reconfigure(dark ? oneDark : []) }),
)

onBeforeUnmount(() => view?.destroy())
</script>

<template>
  <div ref="host" class="code-editor" />
</template>

<style scoped>
.code-editor {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}
</style>
