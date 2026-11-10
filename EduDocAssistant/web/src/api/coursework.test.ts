import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({
  get: vi.fn(() => Promise.resolve({ items: [], total: 0, page: 1, pageSize: 10 })),
  post: vi.fn(() => Promise.resolve({})),
  put: vi.fn(() => Promise.resolve({})),
}))

import { get, post, put } from './http'
import {
  createCourse,
  gradeSubmission,
  importMembers,
  listAssignments,
  listStudentAssignments,
  listSubmissions,
  listTeacherCourses,
  listUnsubmitted,
  publishAssignment,
  submitAssignment,
  workbenchSummary,
} from './coursework'

const g = vi.mocked(get)
const p = vi.mocked(post)
const u = vi.mocked(put)

describe('coursework API 契约（06 切片1，路径按 /teacher 与 /student 前缀）', () => {
  beforeEach(() => vi.clearAllMocks())

  it('教师侧路径与参数', async () => {
    await listTeacherCourses({ term: '2026-FALL', keyword: 'Web', page: 1, pageSize: 10 })
    expect(g).toHaveBeenCalledWith('/teacher/courses', {
      params: { term: '2026-FALL', keyword: 'Web', page: 1, pageSize: 10 },
    })
    await listAssignments({ courseId: 'C1', status: 'PUBLISHED' })
    expect(g).toHaveBeenCalledWith('/teacher/assignments', { params: { courseId: 'C1', status: 'PUBLISHED' } })
    await listUnsubmitted('A1')
    expect(g).toHaveBeenCalledWith('/teacher/assignments/A1/unsubmitted')
    await listSubmissions({ assignmentId: 'A1' })
    expect(g).toHaveBeenCalledWith('/teacher/submissions', { params: { assignmentId: 'A1' } })
    await workbenchSummary()
    expect(g).toHaveBeenCalledWith('/teacher/workbench-summary')
  })

  it('写操作带上后端要求的请求体字段', async () => {
    await createCourse({ name: '编译原理', code: 'CS401', term: '2026-FALL' })
    expect(p).toHaveBeenCalledWith('/teacher/courses', { name: '编译原理', code: 'CS401', term: '2026-FALL' })
    await importMembers('C1', [{ accountNo: '2024099', name: '刘同学', group: '数据库-1组' }])
    expect(p).toHaveBeenCalledWith('/teacher/courses/C1/member-imports', {
      rows: [{ accountNo: '2024099', name: '刘同学', group: '数据库-1组' }],
    })
    await publishAssignment({
      courseId: 'C1',
      title: 'T',
      dueTime: '2026-11-15T23:59:59+08:00',
      rubric: [{ name: '需求分析', maxScore: 20 }],
    })
    expect(p).toHaveBeenCalledWith('/teacher/assignments', expect.objectContaining({ courseId: 'C1' }))
    await gradeSubmission('S1', { items: [{ name: '需求分析', score: 16 }], feedback: '好' })
    expect(u).toHaveBeenCalledWith('/teacher/submissions/S1/grade', {
      items: [{ name: '需求分析', score: 16 }],
      feedback: '好',
    })
  })

  it('学生侧路径', async () => {
    await listStudentAssignments({ status: 'PENDING' })
    expect(g).toHaveBeenCalledWith('/student/assignments', { params: { status: 'PENDING' } })
    await submitAssignment('A1', 'D1')
    expect(p).toHaveBeenCalledWith('/student/assignments/A1/submissions', { documentId: 'D1' })
  })
})
