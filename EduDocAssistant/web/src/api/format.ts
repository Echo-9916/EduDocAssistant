// D-08：格式校验/修复/转换/结构元素 API（契约=后端 FormatCheckController/FormatFixController/
// ConversionController/DocumentElementController，见 docs/tasks/04-template-format.md）。
import { get, getBlob, post } from './http'
import type { PageResult } from './types'

export interface CheckView {
  id: string
  documentId: string
  documentVersionId: string
  templateVersionId: string
  status: 'QUEUED' | 'RUNNING' | 'PASSED' | 'FAILED' | 'ERROR'
  totalIssueCount: number
  blockingIssueCount: number
  resultSummary: string | null
}

export interface IssueView {
  id: string
  ruleId: string | null
  issueType: string
  locationType: 'PAGE' | 'PARAGRAPH' | 'ELEMENT' | 'REFERENCE'
  locationRef: string | null
  expectedValue: string | null
  actualValue: string | null
  severity: 'BLOCKER' | 'WARNING' | 'INFO'
  autoFixable: 'Y' | 'N'
  fixStatus: 'OPEN' | 'FIXED' | 'IGNORED'
  guidance: string | null
}

export interface FixView {
  newVersionId: string | null
  newVersionNo: number | null
  recheckId: string | null
  fixedIssueIds: string[]
  skippedIssueIds: string[]
}

export interface ConversionView {
  id: string
  documentId: string
  targetFormat: 'PDF' | 'DOCX'
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED'
  progress: number
  failureCode: string | null
  failureMessage: string | null
  downloadUrl: string | null
  previewUrl?: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface ElementView {
  elementKey: string
  elementType: 'HEADING' | 'FIGURE' | 'TABLE' | 'CODE' | 'EQUATION'
  chapterNo: string | null
  sequenceNo: number | null
  displayLabel: string | null
  captionText: string | null
  locationJson: string | null
}

export const createFormatCheck = (documentId: string) =>
  post<CheckView>(`/documents/${documentId}/format-checks`)

export const getFormatCheck = (checkId: string) => get<CheckView>(`/format-checks/${checkId}`)

export const listFormatIssues = (checkId: string, params?: { severity?: string; page?: number; pageSize?: number }) =>
  get<PageResult<IssueView>>(`/format-checks/${checkId}/issues`, { params })

export const fixFormatIssues = (checkId: string, issueIds: string[]) =>
  post<FixView>(`/format-checks/${checkId}/fix`, { issueIds })

export const listElements = (documentId: string) =>
  get<ElementView[]>(`/documents/${documentId}/elements`)

export const createConversion = (documentId: string, targetFormat: 'PDF' | 'DOCX') =>
  post<ConversionView>('/conversions', { documentId, targetFormat })

export const getConversion = (taskId: string) => get<ConversionView>(`/conversions/${taskId}`)

export const listConversions = (documentId: string) =>
  get<ConversionView[]>(`/conversions`, { params: { documentId } })

/** 格式视图字节流：SUCCEEDED 任务的 PDF 产物（后端代理读 OSS，绕开默认域强制下载与 CORS） */
export const fetchConversionPreview = (taskId: string) => getBlob(`/conversions/${taskId}/preview`)

/** 轮询断点恢复（沿用 C-11 localStorage 口径，key 前缀 edudoc-fmtcheck-） */
export const saveCheckId = (documentId: string, checkId: string) =>
  localStorage.setItem(`edudoc-fmtcheck-${documentId}`, checkId)
export const loadCheckId = (documentId: string): string | null =>
  localStorage.getItem(`edudoc-fmtcheck-${documentId}`)
export const clearCheckId = (documentId: string) =>
  localStorage.removeItem(`edudoc-fmtcheck-${documentId}`)

/** location_ref 解析：{paragraphIndex,charStart,charEnd}；坏值返回 null */
export function parseLocation(ref: string | null): { paragraphIndex: number; charStart: number; charEnd: number } | null {
  if (!ref) return null
  try {
    const raw = JSON.parse(ref)
    if (typeof raw.paragraphIndex === 'number') {
      return { paragraphIndex: raw.paragraphIndex, charStart: raw.charStart ?? 0, charEnd: raw.charEnd ?? 0 }
    }
    return null
  } catch {
    return null
  }
}
