import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/documents', () => ({ listCourses: vi.fn(() => Promise.resolve([{ id: 'C1', courseCode: 'CS1', courseName: '计算机导论', termCode: '2026-FALL', memberRole: 'TEACHER' }])) }))
// validateRules/RULE_TYPE_LABEL/SELECTOR_LABEL 等纯导出保留真实实现，仅 mock 网络函数
vi.mock('../../api/template', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/template')>()
  return {
    ...actual,
    listTemplates: vi.fn(), createTemplate: vi.fn(), changeTemplateStatus: vi.fn(),
    deleteTemplate: vi.fn(), listVersions: vi.fn(), createVersion: vi.fn(),
    listRules: vi.fn(), replaceRules: vi.fn(), publishVersion: vi.fn(),
    currentRules: vi.fn(), provisionFromTemplate: vi.fn(),
  }
})

import { createTemplate, listRules, listTemplates, listVersions, publishVersion, replaceRules } from '../../api/template'
import Templates, { validateRules } from './Templates'
import type { RuleView, TemplateView } from '../../api/template'

function tpl(over: Partial<TemplateView> = {}): TemplateView {
  return {
    id: 'T1', courseId: 'C1', courseName: 'Web开发', templateName: '报告模板', description: null,
    ownerId: 'U1', status: 'D', currentVersionId: null, currentVersionNo: null, ruleCount: null,
    createTime: null, updateTime: '2026-11-01T00:00:00', ...over,
  }
}

const rule = (over: Partial<RuleView> = {}): RuleView => ({
  id: 'R1', ruleType: 'FONT', targetSelector: 'HEADING_1', severity: 'BLOCKER',
  autoFixable: 'Y', ruleConfigJson: '{"expect":{"eastAsia":"黑体"},"fix":{"eastAsia":"黑体"}}',
  ruleOrder: 10, ...over,
})

describe('validateRules（镜像后端不变量）', () => {
  it('合法规则通过', () => expect(validateRules([rule()])).toEqual([]))

  it('selector 越词表 / 非修复面标 Y / 固定行距缺 pt / Y 无 fix 均拒绝', () => {
    expect(validateRules([rule({ targetSelector: 'HEADING_9' })]))
      .toContainEqual(expect.stringContaining('不在词表'))
    expect(validateRules([rule({ ruleType: 'CAPTION', targetSelector: 'FIGURE_CAPTION', autoFixable: 'Y',
      ruleConfigJson: '{"expect":{"perChapter":true}}' })]))
      .toContainEqual(expect.stringContaining('autoFixable 必须为 N'))
    expect(validateRules([rule({ ruleType: 'LINE_SPACING',
      ruleConfigJson: '{"expect":{"lineRule":"exact"},"fix":{"multiplier":1.5}}' })]))
      .toContainEqual(expect.stringContaining('必须带 pt'))
    expect(validateRules([rule({ ruleConfigJson: '{"expect":{"eastAsia":"黑体"}}' })]))
      .toContainEqual(expect.stringContaining('有效 fix'))
  })

  it('CAPTION 坏正则拒绝；present 非布尔 true 拒绝', () => {
    expect(validateRules([rule({ ruleType: 'CAPTION', targetSelector: 'FIGURE_CAPTION', autoFixable: 'N',
      ruleConfigJson: '{"expect":{"pattern":"^(("}}' })]))
      .toContainEqual(expect.stringContaining('合法正则'))
    expect(validateRules([rule({ ruleType: 'HEADER_FOOTER', targetSelector: 'HEADER', autoFixable: 'N',
      ruleConfigJson: '{"expect":{"present":"yes"}}' })]))
      .toContainEqual(expect.stringContaining('true'))
  })
})

describe('Templates 页（D-09）', () => {
  beforeEach(() => {
    vi.mocked(listTemplates).mockResolvedValue({ items: [tpl()], total: 1, page: 1, pageSize: 20 })
    vi.mocked(listVersions).mockResolvedValue([
      { id: 'V1', templateId: 'T1', versionNo: 1, validationStatus: 'VALID', publishedAt: null,
        changeSummary: '首版', sourceObjectId: 'O1', ruleCount: 1, failureReason: null },
    ])
    vi.mocked(listRules).mockResolvedValue([rule()])
    vi.mocked(replaceRules).mockResolvedValue(undefined)
    vi.mocked(publishVersion).mockResolvedValue(undefined)
    vi.mocked(createTemplate).mockResolvedValue(tpl())
  })

  it('列表渲染：状态徽标与编辑入口', async () => {
    render(<Templates />)
    expect(await screen.findByText('报告模板')).toBeTruthy()
    expect(screen.getByText('草稿')).toBeTruthy()
  })

  it('新建模板：课程+名称必填后提交', async () => {
    render(<Templates />)
    fireEvent.click(await screen.findByText('新建模板'))
    fireEvent.click(await screen.findByRole('button', { name: /确\s*定/ }))
    expect(createTemplate).not.toHaveBeenCalled()

    fireEvent.change(await screen.findByPlaceholderText(/模板名称/), { target: { value: 'C语言模板' } })
    // 弹窗内课程 Select：mouseDown 展开后选第一项（限定在 modal 容器内找 selector）
    const modal = document.querySelector('.ant-modal') as HTMLElement
    fireEvent.mouseDown(modal.querySelector('.ant-select-selector') as Element)
    fireEvent.click(await screen.findByText('计算机导论（2026-FALL）', { selector: '.ant-select-item-option-content' }))
    fireEvent.click(screen.getByRole('button', { name: /确\s*定/ }))
    await waitFor(() => expect(createTemplate).toHaveBeenCalledWith(
      expect.objectContaining({ templateName: 'C语言模板' })))
  })

  it('完整链：版本加载→规则分组回显→保存→发布确认弹窗→确认发布', async () => {
    render(<Templates />)
    fireEvent.click(await screen.findByText('编辑'))
    // P2-3：规则按类型分组，出现分组标题「字体」与 JSON 编辑器
    expect(await screen.findByTestId('rule-json')).toBeTruthy()
    expect(screen.getByText('字体')).toBeTruthy()
    expect(screen.getByText(/学生端效果预览/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '保存规则' }))
    await waitFor(() => expect(replaceRules).toHaveBeenCalledWith('T1', 'V1',
      [expect.objectContaining({ ruleType: 'FONT' })]))
    // P2-3：发布先走确认弹窗（屏 11），确认后才调用接口
    fireEvent.click(await screen.findByRole('button', { name: /发布确认/ }))
    expect(await screen.findByText(/适用范围/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /确认发布/ }))
    await waitFor(() => expect(publishVersion).toHaveBeenCalledWith('T1', 'V1'))
  })

  it('已发布版本规则只读回显而非隐藏（P2-3）', async () => {
    vi.mocked(listTemplates).mockResolvedValue({
      items: [tpl({ status: 'P', currentVersionId: 'V1', currentVersionNo: 1 })],
      total: 1, page: 1, pageSize: 20,
    })
    vi.mocked(listVersions).mockResolvedValue([
      { id: 'V1', templateId: 'T1', versionNo: 1, validationStatus: 'VALID',
        publishedAt: '2026-11-01T00:00:00', changeSummary: '发布版', sourceObjectId: 'O1',
        ruleCount: 1, failureReason: null },
    ])
    render(<Templates />)
    fireEvent.click(await screen.findByText('编辑'))
    expect(await screen.findByText('已发布版本规则只读；变更请上传新版本或编辑最新未发布版本')).toBeTruthy()
    // 只读回显：无 JSON 编辑框，但有要求的口语化展示
    expect(screen.queryByTestId('rule-json')).toBeNull()
    expect(screen.getAllByText('中文 黑体').length).toBeGreaterThanOrEqual(2) // 左只读表+右示意卡
  })

  it('MARGIN 字符串类型键被前端校验拒绝（P2-1 镜像）', () => {
    expect(validateRules([rule({ ruleType: 'MARGIN', targetSelector: 'PAGE', autoFixable: 'N',
      ruleConfigJson: '{"expect":{"top":"bad","left":20}}' })]))
      .toContainEqual(expect.stringContaining('必须为正数 mm'))
  })
})
