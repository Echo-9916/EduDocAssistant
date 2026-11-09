import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

const navigateMock = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }))
vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/template', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/template')>()
  return {
    ...actual,
    listTemplates: vi.fn(),
    currentRules: vi.fn(),
    provisionFromTemplate: vi.fn(),
  }
})

import { currentRules, listTemplates, provisionFromTemplate } from '../../api/template'
import type { RuleView, TemplateView } from '../../api/template'
import StudentTemplates from './Templates'

const published: TemplateView = {
  id: 'T1', courseId: 'C1', courseName: 'Web开发', templateName: '报告模板', description: null,
  ownerId: 'U1', status: 'P', currentVersionId: 'V1', currentVersionNo: 2, ruleCount: 2,
  createTime: null, updateTime: null,
}

const rules: RuleView[] = [
  { id: 'R1', ruleType: 'FONT', targetSelector: 'HEADING_1', severity: 'BLOCKER', autoFixable: 'Y',
    ruleConfigJson: '{"expect":{"eastAsia":"黑体"}}', ruleOrder: 10 },
  { id: 'R2', ruleType: 'MARGIN', targetSelector: 'PAGE', severity: 'BLOCKER', autoFixable: 'Y',
    ruleConfigJson: '{"expect":{"top":25.4,"bottom":25.4,"left":31.75,"right":25.4,"width":210,"height":297}}',
    ruleOrder: 13 },
]

describe('学生课程模板只读页（D-10）', () => {
  beforeEach(() => {
    vi.mocked(listTemplates).mockResolvedValue({
      items: [published, { ...published, id: 'T0', status: 'D', templateName: '草稿模板' }],
      total: 2, page: 1, pageSize: 100,
    })
    vi.mocked(currentRules).mockResolvedValue(rules)
    vi.mocked(provisionFromTemplate).mockResolvedValue({ documentId: 'D9', versionId: 'V9', templateVersionId: 'V1' })
  })

  it('只展示已发布模板并渲染中文要求表', async () => {
    render(<StudentTemplates />)
    expect(await screen.findByText('课程要求——报告模板')).toBeTruthy()
    expect(screen.queryByText('草稿模板')).toBeNull() // 学生端仅已发布
    expect(await screen.findByText('中文 黑体')).toBeTruthy()
    expect(screen.getByText('一级标题')).toBeTruthy()
    expect(screen.getByText('页边距/纸型')).toBeTruthy()
    expect(screen.getAllByText('阻断项')).toHaveLength(2)
  })

  it('按模板新建：标题必填、提交后跳预览页', async () => {
    render(<StudentTemplates />)
    fireEvent.click(await screen.findByRole('button', { name: /按模板新建文档/ }))
    fireEvent.click(await screen.findByRole('button', { name: /创\s*建/ }))
    expect(provisionFromTemplate).not.toHaveBeenCalled()

    fireEvent.change(screen.getByPlaceholderText(/文档标题/), { target: { value: '我的报告' } })
    fireEvent.click(screen.getByRole('button', { name: /创\s*建/ }))
    await waitFor(() => expect(provisionFromTemplate)
      .toHaveBeenCalledWith('T1', '我的报告', 'REPORT'))
    expect(navigateMock).toHaveBeenCalledWith('../documents/D9/view', { relative: 'path' })
  })

  it('无任何已发布模板显示空态', async () => {
    vi.mocked(listTemplates).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 })
    render(<StudentTemplates />)
    expect(await screen.findByText('课程尚未发布模板')).toBeTruthy()
  })
})
