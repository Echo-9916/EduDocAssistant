import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
    currentRules: vi.fn(), provisionFromTemplate: vi.fn(), previewTemplateRules: vi.fn(),
    forkTemplateVersion: vi.fn(),
  }
})

import { createTemplate, createVersion, forkTemplateVersion, listRules, listTemplates, listVersions, previewTemplateRules, publishVersion, replaceRules } from '../../api/template'
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
      .toContainEqual(expect.stringContaining('不匹配'))
    expect(validateRules([rule({ ruleType: 'CAPTION', targetSelector: 'FIGURE_CAPTION', autoFixable: 'Y',
      ruleConfigJson: '{"expect":{"perChapter":true}}' })]))
      .toContainEqual(expect.stringContaining('取消勾选'))
    expect(validateRules([rule({ ruleType: 'LINE_SPACING',
      ruleConfigJson: '{"expect":{"lineRule":"exact"},"fix":{"multiplier":1.5}}' })]))
      .toContainEqual(expect.stringContaining('固定行距必须带磅值'))
    expect(validateRules([rule({ ruleConfigJson: '{"expect":{"eastAsia":"黑体"}}' })]))
      .toContainEqual(expect.stringContaining('修复值不能为空'))
  })

  it('CAPTION 坏正则拒绝；present 非布尔 true 拒绝', () => {
    expect(validateRules([rule({ ruleType: 'CAPTION', targetSelector: 'FIGURE_CAPTION', autoFixable: 'N',
      ruleConfigJson: '{"expect":{"pattern":"^(("}}' })]))
      .toContainEqual(expect.stringContaining('正则'))
    expect(validateRules([rule({ ruleType: 'HEADER_FOOTER', targetSelector: 'HEADER', autoFixable: 'N',
      ruleConfigJson: '{"expect":{"present":"yes"}}' })]))
      .toContainEqual(expect.stringContaining('必须存在'))
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

  afterEach(() => vi.clearAllMocks())

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
    // 验收 M-01-3 + 反馈：编辑为铅笔图标弹层，先彩色只读展示，点「编辑」转 textarea
    expect((await screen.findAllByText('中文 黑体')).length).toBeGreaterThanOrEqual(2)
    fireEvent.click(screen.getByRole('button', { name: '编辑配置' }))
    expect(await screen.findByTestId('rule-json-view')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '编辑 JSON' }))
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

  // ==================== 验收反馈 1：已发布版本编辑另存新版本（fork） ====================

  it('已发布版本「基于此版本编辑另存新版本」→ 副本可编辑 → fork 提交基版本与全量规则', async () => {
    vi.mocked(listTemplates).mockResolvedValue({
      items: [tpl({ status: 'P', currentVersionId: 'V1', currentVersionNo: 1 })], total: 1, page: 1, pageSize: 20,
    })
    vi.mocked(listVersions).mockResolvedValue([{
      id: 'V1', templateId: 'T1', versionNo: 1, validationStatus: 'VALID',
      publishedAt: '2026-11-01T00:00:00', changeSummary: '发布版', sourceObjectId: 'O1',
      ruleCount: 1, failureReason: null,
    }])
    vi.mocked(forkTemplateVersion).mockResolvedValue({
      id: 'V2', templateId: 'T1', versionNo: 2, validationStatus: 'VALID', publishedAt: null,
      changeSummary: '基于 v1 编辑另存', sourceObjectId: 'O1', ruleCount: 1, failureReason: null,
    })
    render(<Templates />)
    fireEvent.click(await screen.findByText('编辑'))
    fireEvent.click(await screen.findByRole('button', { name: /基于此版本编辑另存新版本/ }))
    expect(await screen.findByText(/正在基于已发布 v1 编辑副本/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '另存为新版本' }))
    await waitFor(() => expect(forkTemplateVersion).toHaveBeenCalledWith('T1', {
      baseVersionId: 'V1',
      rules: [expect.objectContaining({ ruleType: 'FONT' })],
    }))
    expect(await screen.findByText(/已另存为新版本 v2/)).toBeTruthy()
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
      .toContainEqual(expect.stringContaining('正数毫米值'))
  })

  // ==================== 验收决议①：上传→预览→确认才落版本 ====================

  const feedFile = async () => {
    const input = (await waitFor(() => {
      const el = document.querySelector('input[type="file"]') as HTMLInputElement
      expect(el).toBeTruthy()
      return el
    })) as HTMLInputElement
    fireEvent.change(input, { target: { files: [new File(['docxbytes'], 't.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' })] } })
  }

  it('上传只做预览：不落版本；确认后 createVersion；草稿未调整则不覆盖规则', async () => {
    vi.mocked(previewTemplateRules).mockResolvedValue([rule({ id: '' })])
    vi.mocked(createVersion).mockResolvedValue({
      id: 'V2', templateId: 'T1', versionNo: 2, validationStatus: 'VALID', publishedAt: null,
      changeSummary: null, sourceObjectId: 'O2', ruleCount: 1, failureReason: null,
    })
    render(<Templates />)
    fireEvent.click(await screen.findByText('编辑'))
    await feedFile()
    expect(await screen.findByText(/预览草稿（未保存）/)).toBeTruthy()
    expect(createVersion).not.toHaveBeenCalled()
    expect(previewTemplateRules).toHaveBeenCalledWith('T1', expect.any(File))

    fireEvent.click(screen.getByRole('button', { name: /确认保存为新版本/ }))
    await waitFor(() => expect(createVersion).toHaveBeenCalledWith('T1', expect.any(File)))
    expect(replaceRules).not.toHaveBeenCalled() // 草稿原样→不二次覆盖
    expect(await screen.findByText('新版本 v2 已生成')).toBeTruthy()
  })

  it('预览可取消：恢复当前版本规则，且全程不 createVersion', async () => {
    vi.mocked(previewTemplateRules).mockResolvedValue([rule({ id: '' })])
    render(<Templates />)
    fireEvent.click(await screen.findByText('编辑'))
    await feedFile()
    expect(await screen.findByText(/预览草稿（未保存）/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /取消预览/ }))
    expect(screen.queryByText(/预览草稿（未保存）/)).toBeNull()
    expect(createVersion).not.toHaveBeenCalled()
    // 取消后回读当前版本规则（V1）
    await waitFor(() => expect(listRules).toHaveBeenCalledWith('T1', 'V1'))
  })
})
