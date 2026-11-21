// 代码块高亮：编辑态（react-simple-code-editor）与只读预览共用同一套 Prism 语法。
import Prism from 'prismjs'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-c'
import 'prismjs/components/prism-cpp'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-sql'
import type { CodeLang } from '../../api/documents'

export const CODE_LANGS: { value: CodeLang; label: string }[] = [
  { value: 'java', label: 'Java' },
  { value: 'cpp', label: 'C/C++' },
  { value: 'python', label: 'Python' },
  { value: 'sql', label: 'SQL' },
]

/** 语言别名归一（旧数据可能是 c/c++ 或 text）。 */
export function normalizeLang(lang: string | null | undefined): CodeLang {
  const value = (lang ?? '').toLowerCase()
  if (value === 'cpp' || value === 'c' || value === 'c++') return 'cpp'
  if (value === 'python' || value === 'py') return 'python'
  if (value === 'sql') return 'sql'
  return 'java'
}

export function highlightCode(code: string, lang: string | null | undefined): string {
  const normalized = normalizeLang(lang)
  const grammar = Prism.languages[normalized] ?? Prism.languages.clike
  return Prism.highlight(code, grammar, normalized)
}