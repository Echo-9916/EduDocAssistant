// B-11：我的文档（学生/教师共用，role 控制课程列与上传必填差异）。
// 状态徽标文字+颜色双编码（详细设计 §4.2）；存在 UPLOADING/PARSING 行时 3s 轮询。
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Input, Popconfirm, Segmented, Select, Space, Tag, message } from 'antd'
import { AppButton, AppTable } from '../components'
import { getErrorMessage } from '../api/http'
import {
  deleteDocument,
  listCourses,
  listDocuments,
  reparseDocument,
  restoreDocument,
} from '../api/documents'
import type { CourseView, DocumentListParams, DocumentSummary } from '../api/documents'
import type { PageResult, RoleCodeValue } from '../api/types'
import DocumentUploadModal from './DocumentUploadModal'
import DocumentDetailDrawer from './DocumentDetailDrawer'
import VersionDrawer from './VersionDrawer'
import ConversionModal, { directionOf } from './ConversionModal'
import ConversionHistoryDrawer from './ConversionHistoryDrawer'

/** 行级转换入口可见性：仅 Word/PDF（与批量方向匹配同口径） */
export function directionReady(row: DocumentSummary): boolean {
  return directionOf(row) !== null
}
import { useAuth } from '../auth/AuthContext'
import { PermissionCode } from '../api/types'

export const DOC_TYPE_LABEL: Record<string, string> = {
  COURSEWARE: '课件',
  HOMEWORK: '作业',
  THESIS: '论文',
  REPORT: '报告',
}

export const STATUS_META: Record<string, { label: string; color: string }> = {
  UPLOADING: { label: '上传中', color: 'processing' },
  PARSING: { label: '解析中', color: 'processing' },
  READY: { label: '就绪', color: 'success' },
  FAILED: { label: '解析失败', color: 'error' },
  SUBMITTED: { label: '已提交', color: 'blue' },
  ARCHIVED: { label: '已归档', color: 'default' },
  DELETED: { label: '已删除', color: 'default' },
}

export function formatSize(bytes: number | null): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

interface Props {
  role: RoleCodeValue
}

export default function Documents({ role }: Props) {
  const navigate = useNavigate()
  const [view, setView] = useState<'mine' | 'trash'>('mine')
  const [courses, setCourses] = useState<CourseView[]>([])
  const [filters, setFilters] = useState<{ courseId?: string; type?: string; status?: string; keyword?: string }>({})
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [data, setData] = useState<PageResult<DocumentSummary>>({ items: [], total: 0, page: 1, pageSize: 20 })
  const [loading, setLoading] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [detailId, setDetailId] = useState<string | null>(null)
  // 验收决议④：版本历史 Drawer（owner 可见）
  const [versionsDoc, setVersionsDoc] = useState<DocumentSummary | null>(null)
  // D-13：批量转换（多选 + Modal）
  const [selectedKeys, setSelectedKeys] = useState<string[]>([])
  const [convertOpen, setConvertOpen] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)
  const { user } = useAuth()
  const canConvert = !!user?.permissions.includes(PermissionCode.FORMAT_CONVERT)

  const reload = useCallback(() => setReloadKey((k) => k + 1), [])

  const params = useMemo<DocumentListParams>(
    () => ({
      // 回收站模式强制 DELETED；我的文档模式下状态筛选可选（1104 审查 P2-1）
      status: view === 'trash' ? 'DELETED' : filters.status || undefined,
      courseId: filters.courseId || undefined,
      type: filters.type || undefined,
      keyword: filters.keyword || undefined,
      page,
      pageSize,
    }),
    [view, filters, page, pageSize],
  )

  useEffect(() => {
    listCourses().then(setCourses).catch(() => setCourses([]))
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    listDocuments(params)
      .then((r) => !cancelled && setData(r))
      .catch((e) => !cancelled && message.error(getErrorMessage(e)))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [params, reloadKey])

  // ponytail: 单机 3s 轮询够用，并发大了换 SSE
  const hasPending = data.items.some((i) => i.status === 'UPLOADING' || i.status === 'PARSING')
  useEffect(() => {
    if (!hasPending) return
    const timer = setInterval(() => {
      listDocuments(params).then(setData).catch(() => undefined)
    }, 3000)
    return () => clearInterval(timer)
  }, [hasPending, params])

  const onDelete = async (id: string) => {
    try {
      await deleteDocument(id)
      message.success('已移入回收站')
      reload()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const onRestore = async (id: string) => {
    try {
      await restoreDocument(id)
      message.success('已恢复')
      reload()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const onReparse = async (id: string) => {
    try {
      await reparseDocument(id)
      message.success('已提交重新解析')
      reload()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Segmented
          value={view}
          onChange={(v) => {
            setView(v as 'mine' | 'trash')
            // 回收站只看 DELETED，切视图时清掉状态筛选避免互相污染
            setFilters((f) => ({ ...f, status: undefined }))
            setPage(1)
          }}
          options={[
            { label: '我的文档', value: 'mine' },
            { label: '回收站', value: 'trash' },
          ]}
        />
        <Select
          allowClear
          placeholder="按课程筛选"
          style={{ width: 200 }}
          value={filters.courseId}
          onChange={(v) => {
            setFilters((f) => ({ ...f, courseId: v }))
            setPage(1)
          }}
          options={courses.map((c) => ({ label: c.courseName, value: c.id }))}
        />
        <Select
          allowClear
          placeholder="按类型筛选"
          style={{ width: 140 }}
          value={filters.type}
          onChange={(v) => {
            setFilters((f) => ({ ...f, type: v }))
            setPage(1)
          }}
          options={Object.entries(DOC_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
        />
        {view === 'mine' && (
          <Select
            allowClear
            placeholder="按状态筛选"
            style={{ width: 140 }}
            value={filters.status}
            onChange={(v) => {
              setFilters((f) => ({ ...f, status: v }))
              setPage(1)
            }}
            options={(['PARSING', 'READY', 'FAILED'] as const).map((value) => ({
              value,
              label: STATUS_META[value].label,
            }))}
          />
        )}
        <Input.Search
          allowClear
          placeholder="搜索标题"
          style={{ width: 220 }}
          onSearch={(v) => {
            setFilters((f) => ({ ...f, keyword: v.trim() || undefined }))
            setPage(1)
          }}
        />
        {view === 'mine' && <AppButton onClick={() => setUploadOpen(true)}>上传文档</AppButton>}
        {view === 'mine' && canConvert && (
          <AppButton type="default" disabled={selectedKeys.length === 0}
            onClick={() => setConvertOpen(true)}>
            格式转换
          </AppButton>
        )}
        {view === 'mine' && canConvert && (
          <AppButton type="text" onClick={() => setHistoryOpen(true)}>转换记录</AppButton>
        )}
      </Space>

      <AppTable<DocumentSummary>
        rowKey="id"
        loading={loading}
        dataSource={data.items}
        rowSelection={view === 'mine' && canConvert ? {
          selectedRowKeys: selectedKeys,
          onChange: (keys) => setSelectedKeys(keys as string[]),
        } : undefined}
        pagination={{
          current: data.page,
          pageSize: data.pageSize,
          total: data.total,
          onChange: (p, ps) => {
            setPage(p)
            setPageSize(ps)
          },
        }}
        columns={[
          { title: '标题', dataIndex: 'title', ellipsis: true },
          { title: '类型', dataIndex: 'docType', width: 90, render: (v: string) => DOC_TYPE_LABEL[v] ?? v },
          {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (v: string) => {
              const meta = STATUS_META[v] ?? { label: v, color: 'default' }
              return <Tag color={meta.color}>{meta.label}</Tag>
            },
          },
          ...(role === 'TEACHER'
            ? [{ title: '课程', dataIndex: 'courseName', width: 140, render: (v: string | null) => v ?? '-' }]
            : []),
          { title: '大小', dataIndex: 'sizeBytes', width: 100, render: formatSize },
          { title: '版本', dataIndex: 'versionNo', width: 70, render: (v: number | null) => v ?? '-' },
          { title: '更新时间', dataIndex: 'updateTime', width: 170, render: (v: string) => v?.replace('T', ' ') },
          {
            title: '操作',
            key: 'actions',
            width: 260,
            render: (_: unknown, row: DocumentSummary) => (
              <Space>
                <a onClick={() => setDetailId(row.id)}>详情</a>
                <a onClick={() => setVersionsDoc(row)}>版本</a>
                {row.status === 'READY' && (
                  // 1106 P1-1：当前路由已是 .../documents，相对段只补 `{id}/view`
                  <a onClick={() => navigate(`${row.id}/view`)}>预览</a>
                )}
                {view === 'trash' ? (
                  <a onClick={() => onRestore(row.id)}>恢复</a>
                ) : (
                  <>
                    {row.status === 'FAILED' && <a onClick={() => onReparse(row.id)}>重新解析</a>}
                    {canConvert && directionReady(row) && (
                      // 1107 P2-7：行级转换入口（单选走同一 Modal）
                      <a onClick={() => {
                        setSelectedKeys([row.id])
                        setConvertOpen(true)
                      }}>转换</a>
                    )}
                    <Popconfirm title="移入回收站？" onConfirm={() => onDelete(row.id)}>
                      <a>删除</a>
                    </Popconfirm>
                  </>
                )}
              </Space>
            ),
          },
        ]}
      />

      <DocumentUploadModal
        open={uploadOpen}
        role={role}
        courses={courses}
        onClose={() => setUploadOpen(false)}
        onUploaded={reload}
      />
      <DocumentDetailDrawer docId={detailId} onClose={() => setDetailId(null)} onChanged={reload} />
      <VersionDrawer open={!!versionsDoc} documentId={versionsDoc?.id ?? null}
        documentTitle={versionsDoc?.title} onClose={() => setVersionsDoc(null)} onRestored={reload} />
      <ConversionModal open={convertOpen}
        docs={data.items.filter((d) => selectedKeys.includes(d.id))}
        onClose={() => setConvertOpen(false)} />
      <ConversionHistoryDrawer open={historyOpen} onClose={() => setHistoryOpen(false)}
        initialDocumentId={selectedKeys.length === 1 ? selectedKeys[0] : null} />
    </div>
  )
}
