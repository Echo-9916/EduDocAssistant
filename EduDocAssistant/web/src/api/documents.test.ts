import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('./http', () => ({ get: vi.fn(), post: vi.fn(), del: vi.fn() }))

import { del, get, post } from './http'
import {
  completeUpload,
  createUploadSession,
  deleteDocument,
  getDocument,
  listCourses,
  listDocuments,
  reparseDocument,
  restoreDocument,
} from './documents'

describe('documents api', () => {
  beforeEach(() => vi.clearAllMocks())

  it('listDocuments 透传筛选与分页参数', async () => {
    vi.mocked(get).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 20 })
    await listDocuments({ keyword: 'x', status: 'DELETED', page: 2, pageSize: 50 })
    expect(get).toHaveBeenCalledWith('/documents', {
      params: { keyword: 'x', status: 'DELETED', page: 2, pageSize: 50 },
    })
  })

  it('listCourses/getDocument 路径正确', async () => {
    vi.mocked(get).mockResolvedValue({})
    await listCourses()
    await getDocument('D1')
    expect(get).toHaveBeenNthCalledWith(1, '/courses')
    expect(get).toHaveBeenNthCalledWith(2, '/documents/D1')
  })

  it('写接口路径与 body', async () => {
    vi.mocked(post).mockResolvedValue({})
    vi.mocked(del).mockResolvedValue(undefined)
    await createUploadSession({ title: 't', fileName: 'a.pdf', size: 1, mimeType: 'application/pdf', docType: 'THESIS' })
    expect(post).toHaveBeenNthCalledWith(1, '/documents/upload-sessions', {
      title: 't', fileName: 'a.pdf', size: 1, mimeType: 'application/pdf', docType: 'THESIS',
    })
    await completeUpload('S1', { contentHash: 'h' })
    expect(post).toHaveBeenNthCalledWith(2, '/documents/upload-sessions/S1/complete', { contentHash: 'h' })
    await deleteDocument('D1')
    expect(del).toHaveBeenCalledWith('/documents/D1')
    await restoreDocument('D1')
    expect(post).toHaveBeenNthCalledWith(3, '/documents/D1/restore')
    await reparseDocument('D1', 'V9')
    expect(post).toHaveBeenNthCalledWith(4, '/documents/D1/parse', { versionId: 'V9' })
    await reparseDocument('D1')
    expect(post).toHaveBeenNthCalledWith(5, '/documents/D1/parse', {})
  })
})
