import MarkdownIt from 'markdown-it'
import katex from '@vscode/markdown-it-katex'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import java from 'highlight.js/lib/languages/java'
import json from 'highlight.js/lib/languages/json'
import python from 'highlight.js/lib/languages/python'

for (const [name, lang] of Object.entries({ bash, c, cpp, java, json, python })) {
  hljs.registerLanguage(name, lang)
}

// html: false —— 题面、题解和 AI 回答里的原始 HTML 都按文本显示，再用 DOMPurify 过滤一遍
const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  highlight(code, lang) {
    const body = lang && hljs.getLanguage(lang)
      ? hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
      : md.utils.escapeHtml(code)
    return `<pre class="hljs"><code>${body}</code></pre>`
  },
})
md.use(katex, { throwOnError: false })

// 链接在新标签页打开
const defaultLinkOpen = md.renderer.rules.link_open ?? ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

export function renderMarkdown(source: string | null | undefined): string {
  return DOMPurify.sanitize(md.render(source ?? ''), {
    USE_PROFILES: { html: true, mathMl: true, svg: true },
    ADD_ATTR: ['target'],
  })
}
