// C-10：智能处理 API（契约=后端 AiGenerationController/AiTaskController/QaController/
// PolishController/AiTaskController.export + DocumentController.preview，见 docs/test/AiTest.md）。
import { get, post } from './http'
import { getToken } from './token'

// ---------- 预览（C-08） ----------

export interface PreviewBlock {
  chunkId: string
  chunkNo: number
  headingPath: string | null
  pageNo: number | null
  /** C-01 闭区间：全文偏移，块文本=全文切片 */
  charStart: number
  charEnd: number
  text: string
}

export interface Preview {
  documentId: string
  versionId: string
  versionNo: number
  blocks: PreviewBlock[]
  /** 当前版本分块是否全部完成向量索引（R3/1106 P2-1） */
  vectorReady: boolean
}

export const getPreview = (documentId: string, versionId?: string) =>
  get<Preview>(`/documents/${documentId}/preview`, { params: versionId ? { versionId } : {} })

// ---------- 任务（C-04/C-05） ----------

export interface TaskCitation {
  citationId: string
  chunkId: string
  pageNo: number | null
  charStart: number | null
  charEnd: number | null
  quoteText: string | null
  rankNo: number
}

export interface TaskResult {
  resultId: string
  resultType: 'SUMMARY' | 'KEYPOINT' | 'ANSWER'
  contentText: string | null
  structuredJson: string | null
  modelName: string | null
  promptVersion: string | null
  inputTokens: number | null
  outputTokens: number | null
  citations: TaskCitation[]
}

export interface AiTaskView {
  taskId: string
  documentId: string
  taskType: 'SUMMARY' | 'KEYPOINT'
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED'
  progress: number
  errorCode: string | null
  result: TaskResult | null
}

export const createSummaryTask = (documentId: string) =>
  post<{ taskId: string; status: string }>(`/documents/${documentId}/summaries`)

export const createKeypointTask = (documentId: string) =>
  post<{ taskId: string; status: string }>(`/documents/${documentId}/keypoints`)

export const getAiTask = (taskId: string) => get<AiTaskView>(`/ai-tasks/${taskId}`)

export const cancelAiTask = (taskId: string) => post<void>(`/ai-tasks/${taskId}/cancel`)

/** 解析 Content-Disposition 的 filename*=UTF-8''…（RFC5987）或 filename="…"；失败返回 null。 */
export function parseFilename(header: string | null): string | null {
  if (!header) return null
  const ext = /filename\*=UTF-8''([^;]+)/i.exec(header)
  if (ext?.[1]) {
    try { return decodeURIComponent(ext[1].trim()) } catch { return ext[1].trim() }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain?.[1]?.trim() ?? null
}

/** 导出走 header 鉴权，不能用直链；返回 Blob+后端文件名（1106 P3-2：文件名以 Content-Disposition 为准）。 */
export async function fetchExportBlob(
  resultId: string, format: 'txt' | 'docx',
): Promise<{ blob: Blob; filename: string | null }> {
  const res = await fetch(`/api/v1/ai-results/${resultId}/export?format=${format}`, {
    headers: { Authorization: `Bearer ${getToken() ?? ''}` },
  })
  if (!res.ok) {
    throw new Error(`导出失败（HTTP ${res.status}）`)
  }
  return { blob: await res.blob(), filename: parseFilename(res.headers.get('Content-Disposition')) }
}

/** 任务断点恢复键（C-11 §4.2：刷新后从任务接口恢复）。 */
export const taskStoreKey = (documentId: string, taskType: string) =>
  `edudoc-aitask-${documentId}-${taskType}`

export function saveTaskId(documentId: string, taskType: string, taskId: string) {
  localStorage.setItem(taskStoreKey(documentId, taskType), taskId)
}

export function loadTaskId(documentId: string, taskType: string): string | null {
  return localStorage.getItem(taskStoreKey(documentId, taskType))
}

export function clearTaskId(documentId: string, taskType: string) {
  localStorage.removeItem(taskStoreKey(documentId, taskType))
}

// ---------- 问答（C-06） ----------

export interface QaSessionView {
  sessionId: string
  documentId: string
  title: string | null
  status: 'A' | 'C'
}

export interface QaCitation {
  chunkId: string
  pageNo: number | null
  charStart: number | null
  charEnd: number | null
  quoteText: string | null
}

export interface QaAskResult {
  sessionId: string
  questionMessageId: string
  answerMessageId: string
  status: 'READY' | 'REFUSED'
  answer: string
  citations: QaCitation[]
}

export interface QaMessageView {
  messageId: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  contentText: string
  status: 'READY' | 'FAILED' | 'REFUSED'
  sequenceNo: number
  citations: QaCitation[]
}

export const createQaSession = (documentId: string, title?: string) =>
  post<QaSessionView>('/qa/sessions', { documentId, title })

export const listQaSessions = (documentId: string) =>
  get<QaSessionView[]>('/qa/sessions', { params: { documentId } })

export const askQa = (sessionId: string, question: string) =>
  post<QaAskResult>(`/qa/sessions/${sessionId}/messages`, { question })

export const listQaMessages = (sessionId: string) =>
  get<QaMessageView[]>(`/qa/sessions/${sessionId}/messages`)

// ---------- 润色（C-07） ----------

export interface PolishRow {
  polishId: string
  sourceText: string
  polishedText: string
  decision: 'PENDING' | 'ACCEPT' | 'PARTIAL' | 'REJECT'
}

export interface PolishApplied {
  polishId: string
  documentId: string
  versionId: string
  versionNo: number
  documentStatus: string
}

export interface PolishParagraph {
  chunkId?: string | null
  text: string
}

export const polishParagraphs = (documentId: string, paragraphs: PolishParagraph[]) =>
  post<PolishRow[]>(`/documents/${documentId}/polish`, { paragraphs })

/** charStart（可空）：采纳块的全文闭区间起点，重复段落消歧用（1105 P2-4）。 */
export const polishDecision = (
  polishId: string,
  body: { decision: 'ACCEPT' | 'PARTIAL' | 'REJECT' | 'REVERT'; acceptedText?: string; charStart?: number | null },
) => post<PolishApplied | null>(`/polish/${polishId}/decision`, body)
