import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), del: vi.fn() }))

import { del, get, patch, post, put } from './http'
import {
  changeTemplateStatus,
  createTemplate,
  createVersion,
  currentRules,
  deleteTemplate,
  describeExpect,
  listRules,
  listTemplates,
  listVersions,
  provisionFromTemplate,
  publishVersion,
  replaceRules,
} from './template'

describe('template api（D-08 端点封装）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(get).mockResolvedValue({})
    vi.mocked(post).mockResolvedValue({})
    vi.mocked(put).mockResolvedValue({})
    vi.mocked(del).mockResolvedValue({})
  })

  it('主档端点', async () => {
    await listTemplates({ courseId: 'C1', status: 'P', page: 1, pageSize: 20 })
    expect(get).toHaveBeenNthCalledWith(1, '/templates', {
      params: { courseId: 'C1', status: 'P', page: 1, pageSize: 20 },
    })
    await createTemplate({ courseId: 'C1', templateName: 'T', description: 'd' })
    expect(post).toHaveBeenNthCalledWith(1, '/templates', { courseId: 'C1', templateName: 'T', description: 'd' })
    await changeTemplateStatus('T1', 'A')
    // P1-1：后端是 @PatchMapping——前端必须用 PATCH（防方法漂移 405）
    expect(patch).toHaveBeenNthCalledWith(1, '/templates/T1/status', { status: 'A' })
    expect(post).toHaveBeenCalledTimes(1)
    await deleteTemplate('T1')
    expect(del).toHaveBeenNthCalledWith(1, '/templates/T1')
  })

  it('版本与规则端点', async () => {
    await listVersions('T1')
    expect(get).toHaveBeenNthCalledWith(1, '/templates/T1/versions')
    const file = new File(['x'], 'a.docx')
    await createVersion('T1', file, '首版')
    expect(post).toHaveBeenCalledTimes(1)
    const [url, body] = vi.mocked(post).mock.calls[0]
    expect(url).toBe('/templates/T1/versions?changeSummary=%E9%A6%96%E7%89%88')
    expect(body).toBeInstanceOf(FormData)
    await listRules('T1', 'V1')
    expect(get).toHaveBeenNthCalledWith(2, '/templates/T1/versions/V1/rules')
    await replaceRules('T1', 'V1', [])
    expect(put).toHaveBeenNthCalledWith(1, '/templates/T1/versions/V1/rules', [])
    await publishVersion('T1', 'V1')
    expect(post).toHaveBeenNthCalledWith(2, '/templates/T1/versions/V1/publish')
    await currentRules('T1')
    expect(get).toHaveBeenNthCalledWith(3, '/templates/T1/current-rules')
    await provisionFromTemplate('T1', '报告', 'REPORT')
    expect(post).toHaveBeenNthCalledWith(3, '/templates/T1/documents', { title: '报告', docType: 'REPORT' })
  })

  it('describeExpect 口语化与坏 JSON 回退', () => {
    const rule = (json: string) => ({
      id: 'R', ruleType: 'FONT' as const, targetSelector: 'HEADING_1', severity: 'BLOCKER' as const,
      autoFixable: 'Y' as const, ruleConfigJson: json, ruleOrder: 10,
    })
    expect(describeExpect(rule('{"expect":{"eastAsia":"黑体","family":"Times"}}')))
      .toBe('西文 Times，中文 黑体')
    expect(describeExpect(rule('{"expect":{"lineRule":"exact","pt":20}}'))).toBe('固定行距 20 磅')
    expect(describeExpect(rule('broken'))).toBe('broken')
  })
})
