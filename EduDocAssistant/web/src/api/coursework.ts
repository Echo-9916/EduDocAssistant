// 06 切片 1：课程与作业 API（契约=后端 TeacherCourseworkController / StudentCourseworkController，
// 见 docs/tasks/06-backend-followup.md「落地差异」：路径按 /teacher、/student 前缀收口）。
import { get, post, put } from './http'
import type { PageResult } from './types'

/** 格式校验结论：-1 未校验 → NONE，有阻塞 → BLOCK，有警告 → WARN，否则 PASS。 */
export type FormatState = 'NONE' | 'PASS' | 'WARN' | 'BLOCK'

export interface TeacherCourseView {
  id: string
  courseCode: string
  courseName: string
  termCode: string
  ownerName: string
  groups: number
  students: number
  /** 后端 record 字段名是 templateName（不是 template），此处与 Java 侧逐字对齐 */
  templateName: string | null
  status: 'A' | 'C'
  dueWeek: number
}

export interface RubricItem {
  name: string
  maxScore: number
}

export interface ScoreItem {
  name: string
  maxScore: number
  score: number
}

export interface AssignmentView {
  id: string
  courseId: string
  courseName: string
  title: string
  description: string | null
  dueTime: string
  allowLate: 'Y' | 'N'
  submissionFormat: 'DOCX' | 'PDF' | 'BOTH'
  requireFormatCheck: 'Y' | 'N'
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
  expected: number
  submitted: number
  unsubmitted: number
  overdue: number
  graded: number
  formatIssues: number
  rubric: RubricItem[]
}

export interface SubmissionView {
  id: string
  assignmentId: string
  assignmentTitle: string
  courseName: string
  studentId: string
  studentName: string
  accountNo: string
  documentId: string
  documentTitle: string
  versionNo: number | null
  format: FormatState
  status: 'SUBMITTED' | 'LATE' | 'WITHDRAWN' | 'GRADED'
  score: number | null
  feedback: string | null
  items: ScoreItem[]
  attemptNo: number
  submittedAt: string
}

export interface ImportRow {
  accountNo: string
  name: string
  group?: string | null
}

export interface ImportResult {
  total: number
  created: number
  activated: number
  conflicts: number
  failures: { accountNo: string; reason: string }[]
}

export interface WorkbenchSummary {
  dueThisWeek: number
  pendingGrade: number
  formatIssues: number
  collabActive: number
  priority: {
    assignmentId: string
    courseName: string
    title: string
    dueTime: string
    expected: number
    submitted: number
    graded: number
    remainText: string
  }[]
}

/** 学生作业视图：状态与倒计时由服务端派生（前端不再拿 Date.now() 猜）。 */
export interface StudentAssignmentView {
  id: string
  course: string
  title: string
  requirement: string | null
  deadline: string
  remainText: string
  status: 'PENDING' | 'SUBMITTED' | 'GRADED' | 'OVERDUE'
  format: { state: FormatState; note: string }
  score: number | null
  feedback: string | null
  attemptNo: number | null
  submittedDoc: string | null
  submitTime: string | null
  documentId: string | null
  documentVersionId: string | null
  versionNo: number | null
  submissionFormat: string
  requireFormatCheck: string
}

export interface SubmissionResult {
  submissionId: string
  attemptNo: number
  status: 'SUBMITTED' | 'LATE'
}

export const listTeacherCourses = (query: { term?: string; status?: string; keyword?: string; page?: number; pageSize?: number }) =>
  get<PageResult<TeacherCourseView>>('/teacher/courses', { params: query })

export const createCourse = (cmd: { name: string; code: string; term: string }) =>
  post<TeacherCourseView>('/teacher/courses', cmd)

export const importMembers = (courseId: string, rows: ImportRow[]) =>
  post<ImportResult>(`/teacher/courses/${courseId}/member-imports`, { rows })

/** page/pageSize 必传大值：后端缺省每页 10 条，不传会静默截断（演示数据过百就会丢行） */
export const listAssignments = (query: { courseId?: string; status?: string; page?: number; pageSize?: number }) =>
  get<PageResult<AssignmentView>>('/teacher/assignments', { params: query })

export const publishAssignment = (cmd: {
  courseId: string
  title: string
  requirement?: string
  dueTime: string
  allowLate?: 'Y' | 'N'
  submissionFormat?: 'DOCX' | 'PDF' | 'BOTH'
  requireFormatCheck?: 'Y' | 'N'
  draft?: boolean
  rubric: RubricItem[]
}) => post<{ id: string; status: string }>('/teacher/assignments', cmd)

export const listUnsubmitted = (assignmentId: string) =>
  get<{ items: { accountNo: string; name: string }[] }>(`/teacher/assignments/${assignmentId}/unsubmitted`)

export const listSubmissions = (query: { assignmentId?: string; status?: string; page?: number; pageSize?: number }) =>
  get<PageResult<SubmissionView>>('/teacher/submissions', { params: query })

export const gradeSubmission = (submissionId: string, cmd: { items: { name: string; score: number }[]; feedback?: string }) =>
  put<{ score: number; status: string }>(`/teacher/submissions/${submissionId}/grade`, cmd)

export const workbenchSummary = () => get<WorkbenchSummary>('/teacher/workbench-summary')

export const listStudentAssignments = (query: { status?: string; courseId?: string; page?: number; pageSize?: number }) =>
  get<PageResult<StudentAssignmentView>>('/student/assignments', { params: query })

export const submitAssignment = (assignmentId: string, documentId: string) =>
  post<SubmissionResult>(`/student/assignments/${assignmentId}/submissions`, { documentId })
