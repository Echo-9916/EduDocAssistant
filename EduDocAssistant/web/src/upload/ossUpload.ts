// B-10：浏览器 STS 直传封装。ali-oss multipartUpload 自带 checkpoint 语义,
// 断点续传 = checkpoint 存 localStorage、重传时回传、成功后清除(NFR-03/P-03)。
import OSS from 'ali-oss'
import type { UploadSessionResult } from '../api/documents'

export const MAX_UPLOAD_SIZE = 20 * 1024 * 1024
export const ALLOWED_EXTENSIONS = ['doc', 'docx', 'pdf'] as const
/** 与后端 UploadSessionServiceImpl 同源(空 MIME 时后端按扩展名放行,这里同规则) */
export const ALLOWED_MIME_TYPES = [
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/pdf',
] as const

const CHECKPOINT_PREFIX = 'edudoc-ckpt-'
const MULTIPART_THRESHOLD = 5 * 1024 * 1024

/** 文件前置校验:返回 null 表示通过,否则返回用户可读原因(详细设计 §4.3 文案)。 */
export function validateUploadFile(file: File): string | null {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOWED_EXTENSIONS.includes(ext as (typeof ALLOWED_EXTENSIONS)[number])) {
    return `「${file.name}」仅支持 Word(.doc/.docx) 与 PDF 文件`
  }
  if (file.type && !ALLOWED_MIME_TYPES.includes(file.type as (typeof ALLOWED_MIME_TYPES)[number])) {
    return `「${file.name}」文件类型与扩展名不符`
  }
  if (file.size > MAX_UPLOAD_SIZE) {
    return `「${file.name}」超过 20MB 上限`
  }
  if (file.size <= 0) {
    return `「${file.name}」是空文件`
  }
  return null
}

export async function sha256Hex(file: File): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

function checkpointKey(sessionId: string): string {
  return CHECKPOINT_PREFIX + sessionId
}

function loadCheckpoint(sessionId: string): OSS.Checkpoint | undefined {
  try {
    const raw = localStorage.getItem(checkpointKey(sessionId))
    return raw ? (JSON.parse(raw) as OSS.Checkpoint) : undefined
  } catch {
    return undefined
  }
}

/** 会话 + STS 凭证直传;<5MB 走 put,≥5MB 走 multipartUpload(可断点续传)。onProgress 收 0~100。 */
export async function uploadToOss(
  session: UploadSessionResult,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<void> {
  const client = new OSS({
    region: session.region,
    accessKeyId: session.sts.accessKeyId,
    accessKeySecret: session.sts.accessKeySecret,
    stsToken: session.sts.securityToken,
    bucket: session.bucket,
    secure: true,
  })
  const headers = { 'Content-Type': file.type || 'application/octet-stream' }
  if (file.size < MULTIPART_THRESHOLD) {
    // ali-oss put 无进度回调（单请求），完成即 100%
    await client.put(session.objectKey, file, { headers })
    onProgress?.(100)
  } else {
    const key = checkpointKey(session.sessionId)
    await client.multipartUpload(session.objectKey, file, {
      partSize: 1024 * 1024,
      parallel: 3,
      headers,
      checkpoint: loadCheckpoint(session.sessionId),
      progress: (p: number, checkpoint?: OSS.Checkpoint) => {
        // 断点续传:进度即存 checkpoint,失败重传从上次分片继续
        if (checkpoint) localStorage.setItem(key, JSON.stringify(checkpoint))
        onProgress?.(Math.round(p * 100))
      },
    })
    localStorage.removeItem(key)
  }
}
