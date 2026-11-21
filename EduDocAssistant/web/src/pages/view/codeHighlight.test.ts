import { describe, expect, it } from 'vitest'
import { highlightCode, normalizeLang } from './codeHighlight'

describe('codeHighlight', () => {
  it('四种语言都能产出 Prism token', () => {
    expect(highlightCode('public class A {}', 'java')).toContain('token')
    expect(highlightCode('int main() { return 0; }', 'cpp')).toContain('token')
    expect(highlightCode('def f():\n  return 1', 'python')).toContain('token')
    expect(highlightCode('SELECT * FROM t', 'sql')).toContain('token')
  })

  it('历史语言别名归一', () => {
    expect(normalizeLang('c++')).toBe('cpp')
    expect(normalizeLang('c')).toBe('cpp')
    expect(normalizeLang('py')).toBe('python')
    expect(normalizeLang(null)).toBe('java')
  })
})