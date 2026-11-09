// 格式视图 v3（二轮回反馈 4 的根治方案）：iframe 不加载 OSS 签名链（默认域强制 attachment 弹下载、
// 且受 CORS/浏览器 PDF 插件差异影响），改为「带 Bearer 的后端字节流 → Blob URL → iframe 内嵌」：
// - docx：复用/触发既有 LibreOffice 转换链路出 PDF（与下载产物同引擎，版式 100%），取 /conversions/{id}/preview
// - pdf 原件：直接取 /documents/{id}/versions/{vid}/file
// 新鲜度：finishedAt >= doc.updateTime 才复用旧产物；修复/编辑出新版本后自动重转。
import { useEffect, useRef, useState } from 'react'
import { Alert, Progress, Space, Typography } from 'antd'
import { AppButton } from '../../components'
import { getErrorMessage } from '../../api/http'
import { createConversion, fetchConversionPreview, getConversion, listConversions } from '../../api/format'
import { fetchVersionFile, listDocumentVersions } from '../../api/documents'

const DOCX_MIME = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
const POLL_MS = 2000
const BUDGET_MS = 90000

interface Props {
  documentId: string
  mimeType?: string | null
  documentUpdateTime?: string | null
}

export default function FormatPane({ documentId, mimeType, documentUpdateTime }: Props) {
  const [phase, setPhase] = useState<'loading' | 'converting' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const urlRef = useRef<string | null>(null)
  const alive = useRef(true)

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current)
        urlRef.current = null
      }
    }
  }, [documentId])

  useEffect(() => {
    setPhase('loading'); setError(null)
    if (urlRef.current) {
      URL.revokeObjectURL(urlRef.current)
      urlRef.current = null
    }
    const show = (blob: Blob) => {
      if (!alive.current) return
      urlRef.current = URL.createObjectURL(blob)
      setPhase('ready')
    }
    void (async () => {
      try {
        if (mimeType === 'application/pdf') {
          const rows = await listDocumentVersions(documentId)
          if (!alive.current) return
          const cur = rows.find((r) => r.current) ?? rows[0]
          if (!cur) throw new Error('当前版本没有可读的源文件')
          show(await fetchVersionFile(documentId, cur.versionId))
          return
        }
        if (mimeType !== DOCX_MIME) {
          if (alive.current) { setError('该文件类型无版式还原预览，请切回文本视图'); setPhase('error') }
          return
        }
        const fresh = (t: { status: string; targetFormat: string; finishedAt: string | null }) =>
          t.status === 'SUCCEEDED' && t.targetFormat === 'PDF'
          && (!documentUpdateTime || (t.finishedAt ?? '') >= documentUpdateTime)
        const tasks = await listConversions(documentId)
        if (!alive.current) return
        const reuse = tasks.filter(fresh).sort((a, b) => (b.finishedAt ?? '').localeCompare(a.finishedAt ?? ''))[0]
        if (reuse) {
          show(await fetchConversionPreview(reuse.id))
          return
        }
        setPhase('converting')
        let task = await createConversion(documentId, 'PDF')
        const t0 = Date.now()
        while (task.status === 'QUEUED' || task.status === 'RUNNING') {
          if (Date.now() - t0 > BUDGET_MS) throw new Error('版式预览生成超时，请稍后重试')
          await new Promise((r) => setTimeout(r, POLL_MS))
          task = await getConversion(task.id)
        }
        if (!alive.current) return
        if (task.status !== 'SUCCEEDED') {
          throw new Error(`版式预览生成失败：${task.failureCode ?? ''} ${task.failureMessage ?? '转换未完成'}`)
        }
        show(await fetchConversionPreview(task.id))
      } catch (e) {
        if (alive.current) { setError(getErrorMessage(e)); setPhase('error') }
      }
    })()
  }, [documentId, mimeType, documentUpdateTime])

  if (phase === 'error') {
    return <Alert type="warning" showIcon message="格式视图不可用" description={error} />
  }
  const url = urlRef.current
  return (
    <div style={{ position: 'relative' }}>
      {(phase === 'loading' || phase === 'converting') && (
        <Space direction="vertical" style={{ width: '100%', margin: '60px 0', textAlign: 'center' }}>
          <Progress type="circle" size={56} percent={phase === 'converting' ? 60 : 10} status="active" showInfo={false} />
          <Typography.Text>
            {phase === 'converting' ? '正在用转换引擎生成版式预览（约 10 秒，一次生成后续复用）…' : '正在准备版式预览…'}
          </Typography.Text>
        </Space>
      )}
      {phase === 'ready' && url && (
        <>
          <Space style={{ marginBottom: 6 }}>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>页内内嵌渲染（与下载产物同一引擎生成）</Typography.Text>
            <AppButton size="small" type="link" onClick={() => window.open(url, '_blank')}>在新标签页打开</AppButton>
          </Space>
          <iframe title="版式预览" src={url} style={{ width: '100%', height: '66vh', border: 'none' }} />
        </>
      )}
      <Alert style={{ marginTop: 8 }} type="info" showIcon banner
        message="版式预览由 LibreOffice 转换引擎生成，忠实还原 Word 排版；问题定位与 AI 功能请使用文本视图" />
    </div>
  )
}
