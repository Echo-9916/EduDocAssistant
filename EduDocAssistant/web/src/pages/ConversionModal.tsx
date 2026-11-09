// D-13：格式转换 Modal。批量=前端循环单任务（计划口径）；方向按源 mime 自动匹配；
// 任务进度 2s 轮询；SUCCEEDED 给签名直链下载（presigned URL 无需 Authorization，直接 window.open）；
// 单文档选中时提供「历史记录」页签（GET /conversions?documentId=）。
// 下载通道裁定（1107 P2-7）：presigned GET 直链为唯一口径（红线允许短期签名 URL），
// 转换历史记录与检索在 ConversionHistoryDrawer，本 Modal 只承载「本次任务」。
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Progress, Select, Space, Table, Tag, Typography, message } from 'antd'
import { AppButton, AppModal } from '../components'
import { getErrorMessage } from '../api/http'
import { createConversion, getConversion } from '../api/format'
import type { ConversionView } from '../api/format'
import type { DocumentSummary } from '../api/documents'

const DOCX_MIME = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
const ACTIVE = new Set(['QUEUED', 'RUNNING'])
export const POLL_MS = 2000

export function directionOf(doc: DocumentSummary): 'PDF' | 'DOCX' | null {
  if (doc.mimeType === DOCX_MIME) return 'PDF'
  if (doc.mimeType === 'application/pdf') return 'DOCX'
  return null
}

interface Props {
  open: boolean
  docs: DocumentSummary[]
  onClose: () => void
}

export default function ConversionModal({ open, docs, onClose }: Props) {
  const [tasks, setTasks] = useState<ConversionView[]>([])
  const [submitting, setSubmitting] = useState(false)
  // 1108 复审 P2-7 残余：目标格式显式可选（默认自动匹配；手选时不符者跳过提示）
  const [targetChoice, setTargetChoice] = useState<'auto' | 'PDF' | 'DOCX'>('auto')
  const alive = useRef(true)
  const timers = useRef<ReturnType<typeof setTimeout>[]>([])

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      timers.current.forEach(clearTimeout)
      timers.current = []
    }
  }, [open])

  const track = useCallback((task: ConversionView) => {
    setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    if (!ACTIVE.has(task.status)) return
    const timer = setTimeout(async () => {
      try {
        const next = await getConversion(task.id)
        if (alive.current) track(next)
      } catch {
        // 轮询失败停止该项，用户可关闭重开
      }
    }, POLL_MS)
    timers.current.push(timer)
  }, [])

  const submit = async () => {
    setSubmitting(true)
    const created: ConversionView[] = []
    const unsupported: string[] = []
    for (const doc of docs) {
      const auto = directionOf(doc)
      const target = targetChoice === 'auto' ? auto : (auto === targetChoice ? targetChoice : null)
      if (!target) {
        unsupported.push(doc.title)
        continue
      }
      try {
        created.push(await createConversion(doc.id, target))
      } catch (e) {
        message.error(`${doc.title}：${getErrorMessage(e)}`)
      }
    }
    setSubmitting(false)
    if (unsupported.length) {
      message.warning(targetChoice === 'auto'
        ? `${unsupported.length} 个文件不是 Word/PDF，已跳过`
        : `${unsupported.length} 个文件与所选目标格式不符（或不是 Word/PDF），已跳过`)
    }
    setTasks((prev) => [...created, ...prev])
    created.forEach(track)
  }

  const statusCell = (r: ConversionView) => {
    if (r.status === 'SUCCEEDED') return <Tag color="success">完成</Tag>
    if (r.status === 'FAILED') return <Tag color="error">失败</Tag>
    if (r.status === 'CANCELED') return <Tag>已取消</Tag>
    return (
      <Space>
        <Progress type="line" percent={r.status === 'RUNNING' ? 50 : 0} size="small"
          style={{ width: 80, margin: 0 }} showInfo={false} />
        <span>{r.status === 'RUNNING' ? '转换中' : '排队'}</span>
      </Space>
    )
  }

  const cols = [
    { title: '文档', key: 'doc', render: (_: unknown, r: ConversionView) =>
      docs.find((d) => d.id === r.documentId)?.title ?? r.documentId },
    { title: '目标', dataIndex: 'targetFormat' as const },
    { title: '状态', key: 'status', render: (_: unknown, r: ConversionView) => statusCell(r) },
    {
      title: '操作', key: 'act',
      render: (_: unknown, r: ConversionView) => {
        if (r.status === 'SUCCEEDED') {
          return r.downloadUrl
            ? <AppButton type="link" onClick={() => window.open(r.downloadUrl!, '_blank')}>下载</AppButton>
            : <Typography.Text type="secondary">链接已过期，重新转换</Typography.Text>
        }
        if (r.status === 'FAILED') {
          return <Typography.Text type="danger">{r.failureCode ?? ''} {r.failureMessage ?? '转换失败'}</Typography.Text>
        }
        return null
      },
    },
  ]

  return (
    <AppModal title="格式转换" open={open} width={720} footer={<AppButton onClick={onClose}>关闭</AppButton>}
      onCancel={onClose}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space wrap>
          <span>目标格式：</span>
          <Select
            data-testid="target-choice"
            style={{ width: 200 }}
            value={targetChoice}
            onChange={setTargetChoice}
            options={[
              { value: 'auto', label: '自动匹配（Word↔PDF）' },
              { value: 'PDF', label: '全部转 PDF' },
              { value: 'DOCX', label: '全部转 Word' },
            ]}
          />
        </Space>
        <Alert type="info" showIcon
          message={`已选 ${docs.length} 个文档：${targetChoice === 'auto'
            ? 'Word→PDF、PDF→Word 自动匹配方向'
            : `统一转换为 ${targetChoice}，方向不符者将跳过`}；原文件保持不变，结果保留 15 分钟有效链接。历史可在「转换记录」抽屉查询下载。`} />
        <AppButton loading={submitting} type="primary"
          disabled={!docs.some((d) => targetChoice === 'auto'
            ? directionOf(d) !== null
            : directionOf(d) === targetChoice)}
          onClick={() => void submit()}>
          开始转换
        </AppButton>
        <Table<ConversionView> size="small" rowKey="id" dataSource={tasks} columns={cols} pagination={false} />
      </Space>
    </AppModal>
  )
}
