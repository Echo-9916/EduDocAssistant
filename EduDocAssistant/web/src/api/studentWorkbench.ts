// 学生工作台聚合 API（契约=后端 StudentWorkbenchController）：一次返回统计卡、待办与最近文档。
import { get } from './http'

export interface StudentWorkbenchTodo {
  assignmentId: string
  courseName: string
  title: string
  deadline: string
  remainText: string
  status: 'PENDING' | 'OVERDUE'
}

export interface StudentWorkbenchRecentDocument {
  id: string
  title: string
  status: string
  updateTime: string
}

export interface StudentWorkbenchSummary {
  documentTotal: number
  pendingAssignmentTotal: number
  unreadMessageTotal: number
  activeCollaborationTotal: number
  todos: StudentWorkbenchTodo[]
  /** 后端一并返回，当前页面不使用；后续做最近文档列表时可直接取用 */
  recentDocuments: StudentWorkbenchRecentDocument[]
}

export const studentWorkbenchSummary = () => get<StudentWorkbenchSummary>('/student/workbench-summary')