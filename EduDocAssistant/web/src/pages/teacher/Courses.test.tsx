import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('../../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../../api/coursework', () => ({
  listTeacherCourses: vi.fn(),
  createCourse: vi.fn(),
  importMembers: vi.fn(),
  listAssignments: vi.fn(),
  publishAssignment: vi.fn(),
  listUnsubmitted: vi.fn(),
  listSubmissions: vi.fn(),
  gradeSubmission: vi.fn(),
}))

import {
  gradeSubmission,
  listAssignments,
  listSubmissions,
  listTeacherCourses,
  listUnsubmitted,
} from '../../api/coursework'
import type { AssignmentView, SubmissionView, TeacherCourseView } from '../../api/coursework'
import TeacherCourses from './Courses'

const COURSE: TeacherCourseView = {
  id: 'C1', courseCode: 'CS-3101', courseName: 'Web开发技术', termCode: '2026-2027 秋',
  ownerName: '李老师', groups: 3, students: 32, templateName: null, status: 'A', dueWeek: 1,
}

const RUBRIC = [
  { name: '需求分析', maxScore: 20 },
  { name: '系统设计', maxScore: 20 },
  { name: '实现与测试', maxScore: 20 },
  { name: '格式规范', maxScore: 20 },
  { name: '结论与思考', maxScore: 20 },
]

const ASSIGNMENT: AssignmentView = {
  id: 'A1', courseId: 'C1', courseName: 'Web开发技术', title: 'Web开发课程设计报告',
  description: null, dueTime: '2026-11-20T23:59:00+08:00', allowLate: 'N', submissionFormat: 'BOTH',
  requireFormatCheck: 'Y', status: 'PUBLISHED', expected: 32, submitted: 31, unsubmitted: 1,
  overdue: 0, graded: 18, formatIssues: 3, rubric: RUBRIC,
}

/** 未批改提交：items 为空数组，评分项须从作业 rubric 渲染 */
const SUBMISSION: SubmissionView = {
  id: 'S1', assignmentId: 'A1', assignmentTitle: 'Web开发课程设计报告', courseName: 'Web开发技术',
  studentId: 'U1', studentName: '陈同学', accountNo: '2023002', documentId: 'D1',
  documentTitle: 'Web开发课程设计报告_陈同学_v4.docx', versionNo: 4, format: 'WARN',
  status: 'SUBMITTED', score: null, feedback: null, items: [], attemptNo: 1,
  submittedAt: '2026-11-10T09:00:00+08:00',
}

const page = <T,>(items: T[]) => ({ items, total: items.length, page: 1, pageSize: 100 })

describe('教师课程与作业（原型 20–25 屏，真实端点）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listTeacherCourses).mockResolvedValue(page([COURSE]))
    vi.mocked(listAssignments).mockResolvedValue(page([ASSIGNMENT]))
    vi.mocked(listSubmissions).mockResolvedValue(page([SUBMISSION]))
    vi.mocked(listUnsubmitted).mockResolvedValue({ items: [{ accountNo: '2023003', name: '刘同学' }] })
    vi.mocked(gradeSubmission).mockResolvedValue({ score: 92, status: 'GRADED' })
  })

  it('课程管理表渲染后端课程行', async () => {
    render(<TeacherCourses />)
    expect(await screen.findByText('课程与作业')).toBeTruthy()
    expect(await screen.findByText('Web开发技术')).toBeTruthy()
    expect(screen.getByText('李老师')).toBeTruthy()
    expect(screen.getByText(/CS-3101/)).toBeTruthy()
    expect(screen.getByText('32')).toBeTruthy()
    // 「进行中」出现两次：Segmented 筛选项 + 状态 Tag
    expect(screen.getAllByText('进行中').length).toBeGreaterThanOrEqual(2)
    expect(listTeacherCourses).toHaveBeenCalled()
  })

  it('批改 Tab：评分项按作业 rubric 渲染，保存调用 gradeSubmission 且项名与 rubric 一致', async () => {
    render(<TeacherCourses />)
    fireEvent.click(await screen.findByText('批改与反馈'))
    // 点提交行载入评分表单
    fireEvent.click(await screen.findByText('陈同学'))
    // 5 个评分项输入框 = rubric 长度
    await waitFor(() => expect(screen.getAllByRole('spinbutton')).toHaveLength(RUBRIC.length))
    for (const r of RUBRIC) expect(screen.getByText(r.name)).toBeTruthy()

    fireEvent.click(screen.getByText('保存并反馈'))
    await waitFor(() => expect(gradeSubmission).toHaveBeenCalledTimes(1))
    const [submissionId, cmd] = vi.mocked(gradeSubmission).mock.calls[0]
    expect(submissionId).toBe('S1')
    expect(cmd.items.map((i) => i.name)).toEqual(RUBRIC.map((r) => r.name))
    // 批改后重新拉提交与作业列表（行上展示服务端总分）
    await waitFor(() => expect(listSubmissions).toHaveBeenCalledTimes(2))
    expect(listAssignments).toHaveBeenCalledTimes(2)
  })

  it('提醒未提交学生：调用 listUnsubmitted 展示名单并提示投递随消息切片开放', async () => {
    render(<TeacherCourses />)
    fireEvent.click(await screen.findByText('作业与提交统计'))
    expect(await screen.findByText('Web开发课程设计报告')).toBeTruthy()
    fireEvent.click(screen.getByText('提醒未提交学生'))
    await waitFor(() => expect(listUnsubmitted).toHaveBeenCalledWith('A1'))
    expect(await screen.findByText('刘同学（2023003）')).toBeTruthy()
    expect(screen.getByText(/投递随消息切片开放/)).toBeTruthy()
  })
})
