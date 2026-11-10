import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'

vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/coursework', () => ({
  listStudentAssignments: vi.fn(),
  submitAssignment: vi.fn(),
}))
vi.mock('../../api/documents', () => ({
  listCourses: vi.fn(),
  listDocuments: vi.fn(),
}))
vi.mock('../../api/format', () => ({
  createConversion: vi.fn(),
  listConversions: vi.fn(),
}))

import { listStudentAssignments, submitAssignment } from '../../api/coursework'
import type { StudentAssignmentView } from '../../api/coursework'
import { listCourses, listDocuments } from '../../api/documents'
import type { DocumentSummary } from '../../api/documents'
import StudentCourses from './Courses'

/** 后端真实形状样本（GET /student/assignments 实跑口径：ISO+08:00、未提交字段为 null） */
function row(over: Partial<StudentAssignmentView> = {}): StudentAssignmentView {
  return {
    id: 'A1', course: 'Web开发技术', title: 'Web开发课程设计报告', requirement: '套用课程模板 v3',
    deadline: '2026-11-05T23:59:00+08:00', remainText: '已截止', status: 'PENDING',
    format: { state: 'NONE', note: '' }, score: null, feedback: null, attemptNo: null,
    submittedDoc: null, submitTime: null, documentId: null, documentVersionId: null,
    versionNo: null, submissionFormat: 'DOCX', requireFormatCheck: 'Y', ...over,
  }
}

const GRADED = row({
  status: 'GRADED', score: 88, feedback: '参考文献需按 GB/T 7714 补齐', attemptNo: 1,
  format: { state: 'PASS', note: '校验通过' },
  submittedDoc: 'Web开发课程设计报告_v6.docx', submitTime: '2026-11-04T20:00:00+08:00',
})
const OVERDUE = row({
  id: 'A2', course: '操作系统', title: '操作系统课程报告', status: 'OVERDUE',
  deadline: '2026-11-07T23:59:00+08:00', remainText: '已逾期 2 天',
  format: { state: 'BLOCK', note: '3 项阻塞：页边距、标题字号、代码块字体' },
})
const PENDING = row({
  id: 'A3', course: '数据库原理', title: '作业3 索引设计实验', remainText: '3 天后截止',
  format: { state: 'WARN', note: '1 项警告：参考文献缺卷期号' },
})

const DOC: DocumentSummary = {
  id: 'DOC-1', title: '操作系统课程报告_v2.docx', docType: 'HOMEWORK', status: 'READY',
  courseId: 'C1', courseName: '操作系统', sizeBytes: 2048,
  mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  versionNo: 2, createTime: '2026-11-08T10:00:00+08:00', updateTime: '2026-11-09T10:00:00+08:00',
}

describe('学生课程与作业（06 切片 1 · 真实接口）', () => {
  beforeEach(() => {
    vi.mocked(listCourses).mockResolvedValue([
      { id: 'C1', courseCode: 'CS3305', courseName: '操作系统', termCode: '2026-FALL', memberRole: 'STUDENT' },
    ])
    vi.mocked(listDocuments).mockResolvedValue({ items: [DOC], total: 1, page: 1, pageSize: 100 })
    vi.mocked(listStudentAssignments).mockResolvedValue({ items: [GRADED, OVERDUE, PENDING], total: 3, page: 1, pageSize: 100 })
  })
  afterEach(() => vi.clearAllMocks())

  it('渲染标题与三行作业：remainText/得分/格式结论直出后端字段，不再挂演示标记', async () => {
    render(<StudentCourses />)
    expect(await screen.findByText('课程与作业')).toBeTruthy()
    expect(screen.queryByText('演示数据 · 后端未接入')).toBeNull()
    expect(await screen.findByText('Web开发课程设计报告', { selector: 'td' })).toBeTruthy()
    expect(screen.getByText('操作系统课程报告', { selector: 'td' })).toBeTruthy()
    expect(screen.getByText('作业3 索引设计实验', { selector: 'td' })).toBeTruthy()
    expect(screen.getByText('88 分')).toBeTruthy()
    expect(screen.getByText('已逾期 2 天')).toBeTruthy()
    expect(screen.getByText('阻塞')).toBeTruthy()
    expect(listStudentAssignments).toHaveBeenCalledWith(expect.objectContaining({ pageSize: 100 }))
  })

  it('逾期行「提交」打开屏 25 弹窗，选就绪文档后 submitAssignment 带 documentId，LATE 回显迟交', async () => {
    vi.mocked(submitAssignment).mockResolvedValue({ submissionId: 'S1', attemptNo: 2, status: 'LATE' })
    render(<StudentCourses />)
    const cell = await screen.findByText('操作系统课程报告', { selector: 'td' })
    const tr = cell.closest('tr')!
    // antd 会给两字按钮插空格（「提 交」），直接取该行唯一按钮；逾期行为 danger
    const submitBtn = within(tr).getByRole('button')
    expect(submitBtn.className).toContain('ant-btn-dangerous')
    fireEvent.click(submitBtn)

    expect(await screen.findByText('提交作业 · 操作系统课程报告')).toBeTruthy()
    // 文档下拉来自 listDocuments({status:'READY'}) 真接口样本
    const modal = document.querySelector('.ant-modal')!
    fireEvent.mouseDown(modal.querySelector('.ant-select-selector')!)
    fireEvent.click(await screen.findByText('操作系统课程报告_v2.docx（v2）', { selector: '.ant-select-item-option-content' }))
    fireEvent.click(screen.getByText('确认提交'))

    await waitFor(() => expect(submitAssignment).toHaveBeenCalledWith('A2', 'DOC-1'))
    expect(await screen.findByText(/已记为迟交/)).toBeTruthy()
    // 提交成功后重新拉列表
    await waitFor(() => expect(listStudentAssignments).toHaveBeenCalledTimes(2))
  })

  it('状态筛选不再本地过滤：点「待提交」把 status=PENDING 传给后端', async () => {
    render(<StudentCourses />)
    await screen.findByText('作业3 索引设计实验', { selector: 'td' })
    // Segmented 选项与表格 Tag 文案重复，用 item-label 选择器限定
    const label = screen.getByText('待提交', { selector: '.ant-segmented-item-label' }).closest('label')!
    fireEvent.click(label.querySelector('input')!)
    await waitFor(() =>
      expect(listStudentAssignments).toHaveBeenCalledWith(expect.objectContaining({ status: 'PENDING' })),
    )
  })
})
