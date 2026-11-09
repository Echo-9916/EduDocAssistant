// 1106 P1-1 回归：真实 MemoryRouter 下点「预览」必须命中 .../documents/{id}/view（此前生成双 documents 段回落 /roles）。
import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

vi.mock('../api/http', () => ({ getErrorMessage: (e: Error) => e?.message ?? 'err' }))
vi.mock('../api/documents', () => ({
  listCourses: vi.fn().mockResolvedValue([]),
  listDocuments: vi.fn().mockResolvedValue({
    items: [{
      id: 'D1', title: '课件', docType: 'COURSEWARE', status: 'READY', courseId: null, courseName: null,
      sizeBytes: 1, mimeType: null, versionNo: 1, createTime: '', updateTime: '',
    }],
    total: 1, page: 1, pageSize: 20,
  }),
  deleteDocument: vi.fn(),
  restoreDocument: vi.fn(),
  reparseDocument: vi.fn(),
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: null }) }))
vi.mock('./DocumentUploadModal', () => ({ default: () => null }))
vi.mock('./DocumentDetailDrawer', () => ({ default: () => null }))

import Documents from './Documents'

describe('预览入口路由集成', () => {
  it('点击预览解析到 /student/documents/D1/view', async () => {
    render(
      <MemoryRouter initialEntries={['/student/documents']}>
        <Routes>
          <Route path="/student">
            <Route path="documents" element={<Documents role="STUDENT" />} />
            <Route path="documents/:docId/view" element={<div data-testid="view-hit">预览页</div>} />
          </Route>
          <Route path="/roles" element={<div>roles</div>} />
          <Route path="*" element={<div>fallback</div>} />
        </Routes>
      </MemoryRouter>,
    )
    fireEvent.click(await screen.findByText('预览'))
    expect(await screen.findByTestId('view-hit')).toBeTruthy()
  })
})
