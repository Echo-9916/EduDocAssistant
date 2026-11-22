// B-09：文档接入 API（契约=后端 DocumentController/UploadController/CourseController，
// 见 docs/test/DocumentTest.md 与 apifox/document-access.postman_collection.json）
import { del, get, getBlob, post } from './http'
import type { DocStatusValue, DocTypeValue, PageResult } from './types'

export interface CourseView {
  id: string
  courseCode: string
  courseName: string
  termCode: string
  memberRole: 'STUDENT' | 'TEACHER' | 'ASSISTANT'
}

export interface DocumentSummary {
  id: string
  title: string
  docType: DocTypeValue
  status: DocStatusValue
  courseId: string | null
  courseName: string | null
  sizeBytes: number | null
  mimeType: string | null
  versionNo: number | null
  createTime: string
  updateTime: string
  /** 列表接口返回：文档所有者显示名（详情接口可能为空） */
  ownerName?: string | null
  /** 列表接口返回：OWNER=本人文档，COLLAB=当前用户是被协作方 */
  relation?: 'OWNER' | 'COLLAB'
}

export interface DocumentDetail extends DocumentSummary {
  currentVersionId: string | null
  contentHash: string | null
  chunkCount: number | null
  deletedAt: string | null
  purgeAt: string | null
}

export interface UploadSessionResult {
  sessionId: string
  objectKey: string
  bucket: string
  region: string
  endpoint: string
  sts: {
    accessKeyId: string
    accessKeySecret: string
    securityToken: string
    expiration: string
  }
}

export interface DocumentListParams {
  courseId?: string
  type?: string
  status?: string
  keyword?: string
  page?: number
  pageSize?: number
}

export const listCourses = () => get<CourseView[]>('/courses')

export const listDocuments = (params: DocumentListParams) =>
  get<PageResult<DocumentSummary>>('/documents', { params })

export const getDocument = (id: string) => get<DocumentDetail>(`/documents/${id}`)

export const createUploadSession = (body: {
  courseId?: string | null
  title: string
  fileName: string
  size: number
  mimeType: string
  docType: string
}) => post<UploadSessionResult>('/documents/upload-sessions', body)

export const completeUpload = (sessionId: string, body: { etag?: string; contentHash: string }) =>
  post<{ documentId: string; versionId: string }>(
    `/documents/upload-sessions/${sessionId}/complete`,
    body,
  )

export const deleteDocument = (id: string) => del<void>(`/documents/${id}`)

export const restoreDocument = (id: string) => post<void>(`/documents/${id}/restore`)

export const reparseDocument = (id: string, versionId?: string) =>
  post<{ taskId: string }>(`/documents/${id}/parse`, versionId ? { versionId } : {})

/** 只重发当前版本向量化事件，不重解析、不改变文档结构。 */
export const reindexDocument = (id: string) =>
  post<{ taskId: string }>(`/documents/${id}/reindex`)

/** 验收决议④：文档版本历史行（版本号降序；current=文档当前版本；downloadUrl 900s 短期签名） */
export interface DocumentVersionRow {
  versionId: string
  versionNo: number
  versionType: 'UPLOAD' | 'EDIT' | 'COLLABORATION' | 'RESTORE' | 'SUBMISSION'
  createTime: string
  changeSummary: string | null
  downloadUrl: string | null
  previewUrl?: string | null
  current: boolean
}

export const listDocumentVersions = (id: string) =>
  get<DocumentVersionRow[]>(`/documents/${id}/versions`)

/** 恢复历史版本：服务端复制为新版本（RESTORE）并前移当前版本，返回新版本定位。 */
export const restoreDocumentVersion = (id: string, versionId: string) =>
  post<{ versionId: string; versionNo: number }>(`/documents/${id}/versions/${versionId}/restore`)

/** 格式视图字节流：某版本源文件（PDF 原件内嵌显示用） */
export const fetchVersionFile = (id: string, versionId: string) =>
  getBlob(`/documents/${id}/versions/${versionId}/file`)

/** 代码块语言（与 Prism 语言名一致）。 */
export type CodeLang = 'java' | 'cpp' | 'python' | 'sql'

/** 块编辑：整篇块列表保存为新 EDIT 版本（baseVersionId 乐观锁）。 */
export interface EditableBlock {
  type: 'H1' | 'H2' | 'BODY' | 'CODE' | 'IMAGE'
  text: string
  /** 仅 CODE 块使用；缺省 java */
  lang?: CodeLang
  /** 仅 IMAGE 块使用：先调用 uploadDocumentImage 拿到 id */
  imageId?: string
  imageName?: string
}

export const saveDocumentBlocks = (id: string, body: { baseVersionId: string; blocks: EditableBlock[] }) =>
  post<{ versionId: string; versionNo: number }>(`/documents/${id}/block-edits`, body)

export interface DocumentImage {
  id: string
  fileName: string
  mimeType: string
  sizeBytes: number
}

/** 块编辑插入图片：multipart 到后端，落私有 OSS 后返回 imageId。 */
export const uploadDocumentImage = (id: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return post<DocumentImage>(`/documents/${id}/images`, form)
}

export const getDocumentImage = (id: string, imageId: string) =>
  getBlob(`/documents/${id}/images/${imageId}`)

/** 当前版本导出 Word（模板样式优先；只还原语义结构）。 */
export const exportDocumentDocx = (id: string) =>
  getBlob(`/documents/${id}/exports/docx`)
