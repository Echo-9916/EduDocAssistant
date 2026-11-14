// 批注 / 协作者 / 在线状态 API（契约=后端 CollaborationController）。
import { del, get, patch, post } from './http'

export type AnnotationStatus = 'O' | 'R'
export type CollabPermission = 'VIEW' | 'COMMENT' | 'EDIT'

export interface AnnotationView {
  id: string
  documentId: string
  documentVersionId: string
  authorId: string
  authorName: string
  authorAccount: string
  charStart: number
  charEnd: number
  content: string
  status: AnnotationStatus
  resolvedBy: string | null
  resolvedByName: string | null
  resolvedTime: string | null
  createTime: string
  /** 仅批注作者可删 */
  canDelete: boolean
  /** 文档 owner 或批注作者可解决/重开 */
  canResolve: boolean
}

export interface CollaboratorView {
  id: string
  userId: string
  username: string
  displayName: string
  permission: CollabPermission
  joinedAt: string | null
}

export interface PresenceView {
  userId: string
  displayName: string
  role: string | null
  lastSeen: string
}

export const listAnnotations = (documentId: string, status?: AnnotationStatus) =>
  get<AnnotationView[]>(`/documents/${documentId}/annotations`, { params: status ? { status } : {} })

export const createAnnotation = (documentId: string,
  body: { versionId?: string; charStart: number; charEnd: number; content: string }) =>
  post<AnnotationView>(`/documents/${documentId}/annotations`, body)

export const resolveAnnotation = (annotationId: string, resolved = true) =>
  patch<AnnotationView>(`/annotations/${annotationId}/resolve`, { resolved })

export const deleteAnnotation = (annotationId: string) => del<void>(`/annotations/${annotationId}`)

export const listCollaborators = (documentId: string) =>
  get<CollaboratorView[]>(`/documents/${documentId}/collaborators`)

export const addCollaborator = (documentId: string, body: { username: string; permission: CollabPermission }) =>
  post<CollaboratorView>(`/documents/${documentId}/collaborators`, body)

export const removeCollaborator = (documentId: string, userId: string) =>
  del<void>(`/documents/${documentId}/collaborators/${userId}`)

export const heartbeatPresence = (documentId: string) =>
  post<void>(`/documents/${documentId}/presence`)

export const listPresence = (documentId: string) =>
  get<PresenceView[]>(`/documents/${documentId}/presence`)