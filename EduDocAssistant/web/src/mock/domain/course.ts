import { ago, delay, insert, nextId, update } from '../store'

// 课程与作业域演示数据（学生 22/23/24/25、教师 01/20/21/22/23/24/25 屏）。
// 世界观与 messages.ts 对齐：基准时间 2026-11-09，学期 2026-2027 秋，
// 课程《Web开发技术》《数据库原理》《操作系统》《编译原理》，教师 李老师/王老师，学生 张同学/陈同学/刘同学。
// 倒计时文案按固定基准时间写死，演示时数字不跳动。

/* ---------- 学生端：作业 / 文档 / 下载 ---------- */

export type SubmissionStatus = 'PENDING' | 'SUBMITTED' | 'GRADED' | 'OVERDUE'

export const submissionStatusLabel: Record<SubmissionStatus, string> = {
  PENDING: '待提交',
  SUBMITTED: '已提交',
  GRADED: '已批改',
  OVERDUE: '逾期',
}

export const submissionStatusColor: Record<SubmissionStatus, string> = {
  PENDING: 'warning',
  SUBMITTED: 'processing',
  GRADED: 'success',
  OVERDUE: 'error',
}

export type StudentAssignment = {
  id: string
  course: string
  title: string
  requirement: string
  deadline: string
  /** 相对基准时间 2026-11-09 的静态倒计时文案 */
  remain: string
  status: SubmissionStatus
  /** 格式校验结论（当前稿预检） */
  format: string
  formatTone: 'success' | 'warning' | 'error' | 'default'
  score?: number
  feedback?: string
  submittedDoc?: string
  submitTime?: string
}

const ASSIGNMENTS: StudentAssignment[] = [
  {
    id: 'SA-01', course: 'Web开发技术', title: 'Web开发课程设计报告',
    requirement: '正文含需求分析、系统设计、系统实现、测试与结论，套用课程模板 v3。',
    deadline: '2026-11-05 23:59', remain: '已截止', status: 'GRADED',
    format: '通过', formatTone: 'success', score: 88,
    feedback: '第三章格式已通过，参考文献需按 GB/T 7714 补齐卷期号，请在本周五前重新提交。——李老师',
    submittedDoc: 'Web开发课程设计报告_张同学_v6.docx', submitTime: ago(5, 2),
  },
  {
    id: 'SA-02', course: '数据库原理', title: '作业3 索引设计实验',
    requirement: '完成 B+ 树索引设计实验并输出实验报告，参考文献按 GB/T 7714 标注。',
    deadline: '2026-11-12 23:59', remain: '3 天后截止', status: 'PENDING',
    format: '1 项警告', formatTone: 'warning',
  },
  {
    id: 'SA-03', course: '数据库原理', title: '作业2 B+树索引实验',
    requirement: '提交实验报告与索引结构图，套用数据库实验报告模板 v2。',
    deadline: '2026-11-02 23:59', remain: '已截止 · 批改中', status: 'SUBMITTED',
    format: '通过', formatTone: 'success',
    submittedDoc: '数据库原理作业2_B+树索引实验_张同学_v2.docx', submitTime: ago(7, 4),
  },
  {
    id: 'SA-04', course: '操作系统', title: '操作系统课程报告',
    requirement: '完成进程调度专题报告，页边距、标题字号与代码块字体须符合模板。',
    deadline: '2026-11-07 23:59', remain: '已逾期 2 天', status: 'OVERDUE',
    format: '3 项阻塞', formatTone: 'error',
  },
  {
    id: 'SA-05', course: '编译原理', title: '词法分析实验报告',
    requirement: '提交词法分析器设计说明与源码清单，套用编译原理课程模板 v2。',
    deadline: '2026-11-20 23:59', remain: '11 天后截止', status: 'PENDING',
    format: '待检查', formatTone: 'default',
  },
]

export type DocFormatState = 'PASS' | 'WARN' | 'BLOCK'

export type StudentDoc = {
  id: string
  name: string
  version: string
  updateTime: string
  formatState: DocFormatState
  formatNote: string
}

const DOCS: StudentDoc[] = [
  { id: 'DOC-01', name: 'Web开发课程设计报告_张同学_v6.docx', version: 'v6', updateTime: ago(0, 3), formatState: 'PASS', formatNote: '课程模板 v3 · 校验通过' },
  { id: 'DOC-02', name: '数据库原理作业3_索引设计实验_张同学_v2.docx', version: 'v2', updateTime: ago(0, 6), formatState: 'WARN', formatNote: '1 项警告：参考文献缺卷期号（GB/T 7714）' },
  { id: 'DOC-03', name: '操作系统课程报告_v2.docx', version: 'v2', updateTime: ago(1, 2), formatState: 'BLOCK', formatNote: '3 项阻塞：页边距、一级标题字号、代码块字体' },
  { id: 'DOC-04', name: '数据库原理作业2_B+树索引实验_张同学_v2.docx', version: 'v2', updateTime: ago(7, 4), formatState: 'PASS', formatNote: '课程模板 v2 · 校验通过' },
  { id: 'DOC-05', name: '编译原理词法分析实验报告_张同学_v1.docx', version: 'v1', updateTime: ago(2, 5), formatState: 'PASS', formatNote: '课程模板 v2 · 校验通过' },
]

export type DownloadStatus = 'GENERATING' | 'READY' | 'EXPIRED'

export const downloadStatusLabel: Record<DownloadStatus, string> = {
  GENERATING: '生成中',
  READY: '可下载',
  EXPIRED: '已过期',
}

export const downloadStatusColor: Record<DownloadStatus, string> = {
  GENERATING: 'processing',
  READY: 'success',
  EXPIRED: 'default',
}

export type DownloadTask = {
  id: string
  name: string
  format: 'DOCX' | 'PDF'
  size: string
  createTime: string
  status: DownloadStatus
  expire: string
}

const DOWNLOADS: DownloadTask[] = [
  { id: 'DL-01', name: 'Web开发课程设计报告_张同学_v6.docx', format: 'DOCX', size: '2.9MB', createTime: ago(0, 2), status: 'READY', expire: '7 天后过期' },
  { id: 'DL-02', name: 'Web开发课程设计报告_张同学_v6.pdf', format: 'PDF', size: '4.1MB', createTime: ago(0, 3), status: 'READY', expire: '7 天后过期' },
  { id: 'DL-03', name: '数据库原理作业2_摘要.pdf', format: 'PDF', size: '1.2MB', createTime: ago(1, 4), status: 'READY', expire: '6 天后过期' },
  { id: 'DL-04', name: '操作系统课程报告_v2.docx', format: 'DOCX', size: '2.2MB', createTime: ago(0, 1), status: 'GENERATING', expire: '生成后 7 天有效' },
  { id: 'DL-05', name: '编译原理实验指导书_节选.docx', format: 'DOCX', size: '0.8MB', createTime: ago(9, 2), status: 'EXPIRED', expire: '已过期' },
]

export function listStudentAssignments() {
  return delay(ASSIGNMENTS.map((a) => ({ ...a })))
}

export function submitAssignment(id: string, docName: string) {
  update(ASSIGNMENTS, (a) => a.id === id, {
    status: 'SUBMITTED' as SubmissionStatus, submittedDoc: docName, submitTime: ago(0), format: '通过', formatTone: 'success',
  })
  return delay(ASSIGNMENTS.map((a) => ({ ...a })), 300)
}

export function listStudentDocs() {
  return delay(DOCS.map((d) => ({ ...d })))
}

export function listDownloads() {
  return delay(DOWNLOADS.map((d) => ({ ...d })))
}

export type ExportInput = { docName: string; format: 'DOCX' | 'PDF'; useTemplate: boolean; keepComments: boolean }

export function createExport(input: ExportInput) {
  const base = input.docName.replace(/\.[^.]+$/, '')
  const task: DownloadTask = {
    id: nextId('DL'),
    name: `${base}${input.useTemplate ? '_套模板' : ''}.${input.format.toLowerCase()}`,
    format: input.format,
    size: '生成中',
    createTime: ago(0),
    status: 'GENERATING',
    expire: '生成后 7 天有效',
  }
  insert(DOWNLOADS, task)
  return delay(DOWNLOADS.map((d) => ({ ...d })), 300)
}

/* ---------- 教师端：课程 / 作业统计 / 批改 ---------- */

export type CourseStatus = 'ACTIVE' | 'ARCHIVED'

export const courseStatusLabel: Record<CourseStatus, string> = { ACTIVE: '进行中', ARCHIVED: '已归档' }

export type TeacherCourse = {
  id: string
  term: string
  name: string
  code: string
  owner: string
  groups: number
  students: number
  template: string
  status: CourseStatus
  dueWeek: number
}

const COURSES: TeacherCourse[] = [
  { id: 'TC-01', term: '2026-2027 秋', name: 'Web开发技术', code: 'CS-3101', owner: '李老师', groups: 3, students: 32, template: 'Web开发课程模板 v3', status: 'ACTIVE', dueWeek: 0 },
  { id: 'TC-02', term: '2026-2027 秋', name: '数据库原理', code: 'CS-3203', owner: '李老师', groups: 2, students: 35, template: '数据库实验报告模板 v2', status: 'ACTIVE', dueWeek: 1 },
  { id: 'TC-03', term: '2026-2027 秋', name: '操作系统', code: 'CS-3305', owner: '王老师', groups: 2, students: 28, template: '操作系统课程报告模板 v1', status: 'ACTIVE', dueWeek: 0 },
  { id: 'TC-04', term: '2026-2027 秋', name: '编译原理', code: 'CS-3407', owner: '王老师', groups: 3, students: 42, template: '编译原理课程模板 v2', status: 'ACTIVE', dueWeek: 0 },
  { id: 'TC-05', term: '2025-2026 春', name: 'Web开发技术', code: 'CS-3101', owner: '李老师', groups: 2, students: 30, template: 'Web开发课程模板 v2', status: 'ARCHIVED', dueWeek: 0 },
]

export type NewCourseInput = { name: string; code: string; term: string; groups: number; docPermission: string }

export function listCourses() {
  return delay(COURSES.map((c) => ({ ...c })))
}

export function createCourse(input: NewCourseInput) {
  insert(COURSES, {
    id: nextId('TC'), term: input.term, name: input.name, code: input.code, owner: '李老师',
    groups: input.groups, students: 0, template: '待配置', status: 'ACTIVE', dueWeek: 0,
  })
  return delay(COURSES.map((c) => ({ ...c })), 300)
}

/** 成员导入演示数据：CSV 校验结果统计 + 预览（原型 教师 22 屏）。 */
export const MEMBER_IMPORT = {
  total: 34,
  ok: 32,
  invite: 2,
  mapping: [
    { source: '学号', target: 'student_id' },
    { source: '姓名', target: 'display_name' },
    { source: '课程组', target: 'group_name' },
  ],
  preview: [
    { sid: '2023001', name: '张同学', group: '课程组 3', state: '已绑定' },
    { sid: '2023002', name: '陈同学', group: '课程组 3', state: '已绑定' },
    { sid: '2023003', name: '刘同学', group: '课程组 5', state: '待邀请' },
  ],
}

export function importMembers(courseId: string) {
  const course = COURSES.find((c) => c.id === courseId)
  if (course) update(COURSES, (c) => c.id === courseId, { students: course.students + MEMBER_IMPORT.ok })
  return delay(COURSES.map((c) => ({ ...c })), 300)
}

export type TeacherAssignment = {
  id: string
  course: string
  title: string
  deadline: string
  remain: string
  expected: number
  submitted: number
  overdue: number
  formatIssues: number
  graded: number
  /** 未提交学生名单（演示用节选） */
  unsubmitted: string[]
}

const TASSIGNMENTS: TeacherAssignment[] = [
  { id: 'TA-01', course: 'Web开发技术', title: 'Web开发课程设计报告', deadline: '2026-11-05 23:59', remain: '已截止', expected: 32, submitted: 31, overdue: 1, formatIssues: 3, graded: 18, unsubmitted: ['刘同学（2023003）'] },
  { id: 'TA-02', course: '数据库原理', title: '作业3 索引设计实验', deadline: '2026-11-12 23:59', remain: '3 天后截止', expected: 35, submitted: 22, overdue: 0, formatIssues: 2, graded: 6, unsubmitted: ['张同学（2023001）', '刘同学（2023003）'] },
  { id: 'TA-03', course: '操作系统', title: '操作系统课程报告', deadline: '2026-11-07 23:59', remain: '已逾期 2 天', expected: 28, submitted: 25, overdue: 2, formatIssues: 5, graded: 10, unsubmitted: ['张同学（2023001）', '刘同学（2023003）'] },
  { id: 'TA-04', course: '编译原理', title: '词法分析实验报告', deadline: '2026-11-20 23:59', remain: '11 天后截止', expected: 42, submitted: 9, overdue: 0, formatIssues: 0, graded: 0, unsubmitted: ['张同学（2023001）', '陈同学（2023002）'] },
]

export type PublishInput = {
  course: string
  title: string
  requirement: string
  deadline: string
  format: string
  requireFormatCheck: boolean
  rubric: { label: string; max: number }[]
}

export function listTeacherAssignments() {
  return delay(TASSIGNMENTS.map((a) => ({ ...a })))
}

export function publishAssignment(input: PublishInput) {
  const course = COURSES.find((c) => c.name === input.course && c.status === 'ACTIVE')
  insert(TASSIGNMENTS, {
    id: nextId('TA'), course: input.course, title: input.title, deadline: input.deadline, remain: '未开始统计',
    expected: course?.students ?? 0, submitted: 0, overdue: 0, formatIssues: 0, graded: 0, unsubmitted: [],
  })
  return delay(TASSIGNMENTS.map((a) => ({ ...a })), 300)
}

/** 评分标准项：发布作业与批改共用（对齐原型 rubric）。 */
export const RUBRIC = [
  { key: 'requirement', label: '需求分析', max: 20 },
  { key: 'design', label: '系统设计', max: 20 },
  { key: 'impl', label: '实现与测试', max: 20 },
  { key: 'format', label: '格式规范', max: 20 },
  { key: 'conclusion', label: '结论与思考', max: 20 },
]

export type GradeStatus = 'PENDING_GRADE' | 'GRADED'

export const gradeStatusLabel: Record<GradeStatus, string> = { PENDING_GRADE: '待批改', GRADED: '已批改' }

export type GradeSubmission = {
  id: string
  assignment: string
  course: string
  student: string
  sid: string
  doc: string
  format: string
  formatOk: boolean
  status: GradeStatus
  score?: number
  feedback?: string
}

const SUBMISSIONS: GradeSubmission[] = [
  { id: 'GS-01', assignment: 'Web开发课程设计报告', course: 'Web开发技术', student: '张同学', sid: '2023001', doc: 'Web开发课程设计报告_张同学_v6.docx', format: '通过', formatOk: true, status: 'GRADED', score: 88, feedback: '第三章格式已通过，参考文献需按 GB/T 7714 补齐卷期号，请在本周五前重新提交。' },
  { id: 'GS-02', assignment: 'Web开发课程设计报告', course: 'Web开发技术', student: '陈同学', sid: '2023002', doc: 'Web开发课程设计报告_陈同学_v4.docx', format: '1 项警告', formatOk: false, status: 'PENDING_GRADE' },
  { id: 'GS-03', assignment: '操作系统课程报告', course: '操作系统', student: '陈同学', sid: '2023002', doc: '操作系统课程报告_陈同学_v2.docx', format: '通过', formatOk: true, status: 'PENDING_GRADE' },
  { id: 'GS-04', assignment: '作业2 B+树索引实验', course: '数据库原理', student: '刘同学', sid: '2023003', doc: '数据库原理作业2_B+树索引实验_刘同学_v2.docx', format: '通过', formatOk: true, status: 'GRADED', score: 90, feedback: '索引结构图完整，实验结论清晰。' },
  { id: 'GS-05', assignment: '词法分析实验报告', course: '编译原理', student: '刘同学', sid: '2023003', doc: '编译原理词法分析实验报告_刘同学_v1.docx', format: '2 项警告', formatOk: false, status: 'PENDING_GRADE' },
]

export function listSubmissions() {
  return delay(SUBMISSIONS.map((s) => ({ ...s })))
}

export function gradeSubmission(id: string, total: number, feedback: string) {
  update(SUBMISSIONS, (s) => s.id === id, { status: 'GRADED' as GradeStatus, score: total, feedback })
  return delay(SUBMISSIONS.map((s) => ({ ...s })), 300)
}

/* ---------- 教师工作台（教师 01 屏） ---------- */

/** 与 messages.ts 教师侧动态呼应的最近活动时间线。 */
export const ACTIVITIES = [
  { time: ago(0, 1), text: '李老师 完成《Web开发课程设计报告》张同学 批改（88 分），反馈已同步消息中心', color: 'green' },
  { time: ago(0, 2), text: '《Web开发技术》作业新增 12 份提交，其中 3 份格式校验未通过', color: 'blue' },
  { time: ago(0, 8), text: '格式检查服务：课程组 3 的 5 份报告页眉缺失课程号，已生成异常清单', color: 'red' },
  { time: ago(1, 5), text: '教务同步：《数据库原理》新增学生 2 名（课程组 5）', color: 'blue' },
  { time: ago(3, 2), text: '王老师 发布《编译原理》课程模板 v2，覆盖 3 个班级共 128 名学生', color: 'gray' },
]

/** 进行中协作文档数（协作与版本切片的门面统计，暂由本域提供）。 */
export const COLLAB_ACTIVE = 5

export type PriorityItem = {
  id: string
  title: string
  course: string
  deadline: string
  remain: string
  remainTone: 'danger' | 'warning' | 'default'
  pendingGrade: number
  overdue: number
}

export type WorkbenchSummary = {
  dueThisWeek: number
  pendingGrade: number
  formatIssues: number
  collabActive: number
  priority: PriorityItem[]
}

const TODAY = '2026-11-09'
const WEEK_END = '2026-11-15 23:59'

export function workbenchSummary() {
  const summary: WorkbenchSummary = {
    dueThisWeek: TASSIGNMENTS.filter((a) => a.deadline >= TODAY && a.deadline <= WEEK_END).length,
    pendingGrade: SUBMISSIONS.filter((s) => s.status === 'PENDING_GRADE').length,
    formatIssues: SUBMISSIONS.filter((s) => !s.formatOk).length,
    collabActive: COLLAB_ACTIVE,
    priority: [...TASSIGNMENTS]
      .sort((x, y) => x.deadline.localeCompare(y.deadline))
      .map((a) => ({
        id: a.id,
        title: a.title,
        course: a.course,
        deadline: a.deadline,
        remain: a.remain,
        remainTone: a.deadline < TODAY ? 'danger' : a.deadline <= WEEK_END ? 'warning' : 'default',
        pendingGrade: Math.max(a.submitted - a.graded, 0),
        overdue: a.overdue,
      })),
  }
  return delay(summary)
}
