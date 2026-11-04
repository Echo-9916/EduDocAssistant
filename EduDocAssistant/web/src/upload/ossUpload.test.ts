import { beforeEach, describe, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'

// jsdom 缺 subtle 时补 Node webcrypto（sha256Hex 依赖）
if (!globalThis.crypto?.subtle) {
  Object.defineProperty(globalThis, 'crypto', { value: webcrypto, configurable: true })
}

const h = vi.hoisted(() => ({
  put: vi.fn(),
  multipartUpload: vi.fn(),
  opts: null as Record<string, unknown> | null,
}))
vi.mock('ali-oss', () => ({
  default: class MockOSS {
    put = h.put
    multipartUpload = h.multipartUpload
    constructor(opts: Record<string, unknown>) {
      h.opts = opts
    }
  },
}))

import { MAX_UPLOAD_SIZE, sha256Hex, uploadToOss, validateUploadFile } from './ossUpload'
import type { UploadSessionResult } from '../api/documents'

const session: UploadSessionResult = {
  sessionId: 'S1',
  objectKey: 'edudoc/uploads/202611/U1/S1-a.pdf',
  bucket: 'b',
  region: 'oss-cn-beijing',
  endpoint: 'https://oss-cn-beijing.aliyuncs.com',
  sts: { accessKeyId: 'AK', accessKeySecret: 'SK', securityToken: 'T', expiration: 'x' },
}

function file(name: string, type: string, size = 100): File {
  const f = new File(['x'], name, { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

describe('validateUploadFile', () => {
  it('放行 pdf/docx/doc 与空 MIME 的 .doc', () => {
    expect(validateUploadFile(file('a.pdf', 'application/pdf'))).toBeNull()
    expect(validateUploadFile(file('a.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'))).toBeNull()
    expect(validateUploadFile(file('legacy.doc', ''))).toBeNull()
  })
  it('拒绝 txt/超 20MB/空文件/类型不符', () => {
    expect(validateUploadFile(file('a.txt', 'text/plain'))).toContain('仅支持')
    expect(validateUploadFile(file('big.pdf', 'application/pdf', MAX_UPLOAD_SIZE + 1))).toContain('20MB')
    expect(validateUploadFile(file('e.pdf', 'application/pdf', 0))).toContain('空文件')
    expect(validateUploadFile(file('a.pdf', 'text/plain'))).toContain('不符')
  })
})

describe('uploadToOss', () => {
  beforeEach(() => {
    h.put.mockReset()
    h.multipartUpload.mockReset()
    localStorage.clear()
  })

  it('小文件走 put 且带 STS 凭证', async () => {
    h.put.mockResolvedValue({})
    await uploadToOss(session, file('a.pdf', 'application/pdf', 1024))
    expect(h.opts).toMatchObject({ accessKeyId: 'AK', stsToken: 'T', bucket: 'b', secure: true })
    expect(h.put).toHaveBeenCalledTimes(1)
    expect(h.multipartUpload).not.toHaveBeenCalled()
  })

  it('大文件走 multipart 且 checkpoint 存取清除（断点续传）', async () => {
    h.multipartUpload.mockImplementation(async (_key: string, _f: File, options: { progress: (p: number, cp?: unknown) => void }) => {
      options.progress(0.5, { name: 'ck', uploadedParts: [] })
      return {}
    })
    await uploadToOss(session, file('big.pdf', 'application/pdf', 6 * 1024 * 1024))
    expect(h.put).not.toHaveBeenCalled()
    expect(localStorage.getItem('edudoc-ckpt-S1')).toBeNull() // 成功后清除
    const call = h.multipartUpload.mock.calls[0]
    expect(call[2]).toMatchObject({ partSize: 1024 * 1024, parallel: 3 })
  })

  it('multipart 失败时 checkpoint 留存供续传', async () => {
    h.multipartUpload.mockImplementation(async (_k: string, _f: File, options: { progress: (p: number, cp?: unknown) => void }) => {
      options.progress(0.4, { name: 'ck' })
      throw new Error('network')
    })
    await expect(uploadToOss(session, file('big.pdf', 'application/pdf', 6 * 1024 * 1024))).rejects.toThrow()
    expect(JSON.parse(localStorage.getItem('edudoc-ckpt-S1')!)).toMatchObject({ name: 'ck' })
  })
})

describe('sha256Hex', () => {
  it('计算文件 SHA-256 小写 hex', async () => {
    const f = new File(['hello'], 'a.pdf', { type: 'application/pdf' })
    expect(await sha256Hex(f)).toBe('2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824')
  })
})
