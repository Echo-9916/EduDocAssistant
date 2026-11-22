// 文档内图片：私有 OSS 不能直接给 <img src>，带鉴权取 Blob 后本地 objectURL 展示。
import { useEffect, useState } from 'react'
import { Spin, Typography } from 'antd'
import { getDocumentImage } from '../../api/documents'
import { getPublicShareImage } from '../../api/shares'

interface Props {
  documentId?: string
  /** 免登录共享页使用：与 documentId 二选一 */
  publicToken?: string
  imageId: string
  alt?: string
  maxHeight?: number
}

export default function DocumentImage({ documentId, publicToken, imageId, alt, maxHeight = 420 }: Props) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let alive = true
    let objectUrl: string | undefined
    setUrl(null)
    setFailed(false)
    const request = publicToken
      ? getPublicShareImage(publicToken, imageId)
      : documentId ? getDocumentImage(documentId, imageId) : Promise.reject(new Error('缺少文档上下文'))
    request
      .then((blob) => {
        if (!alive) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => alive && setFailed(true))
    return () => {
      alive = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [documentId, publicToken, imageId])

  if (failed) return <Typography.Text type="secondary">图片加载失败</Typography.Text>
  if (!url) return <Spin size="small" style={{ display: 'block', margin: '12px 0' }} />
  return (
    <img
      src={url}
      alt={alt ?? '文档图片'}
      style={{ display: 'block', maxWidth: '100%', maxHeight, margin: '10px 0', borderRadius: 4 }}
    />
  )
}