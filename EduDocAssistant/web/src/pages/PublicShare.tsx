// /s/:token 免登录只读页：只展示当前版本标题与文本，不提供下载/文件对象。
import { useEffect, useState } from 'react'
import { Card, Result, Space, Spin, Tag, Typography } from 'antd'
import { useParams } from 'react-router-dom'
import { getErrorMessage } from '../api/http'
import { getPublicShare } from '../api/shares'
import type { PublicShare as PublicShareData } from '../api/shares'

export default function PublicShare() {
  const { token = '' } = useParams<{ token: string }>()
  const [data, setData] = useState<PublicShareData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    getPublicShare(token)
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(getErrorMessage(e)))
    return () => {
      alive = false
    }
  }, [token])

  return (
    <div style={{ minHeight: '100vh', background: '#f6f8fa', padding: '40px 16px' }}>
      <Card style={{ maxWidth: 900, margin: '0 auto' }}>
        {error ? (
          <Result status="warning" title="共享链接不可用" subTitle={error} />
        ) : !data ? (
          <Spin style={{ display: 'block', padding: 48, textAlign: 'center' }} />
        ) : (
          <>
            <Space wrap align="baseline" style={{ marginBottom: 8 }}>
              <Typography.Title level={4} style={{ margin: 0 }}>{data.title}</Typography.Title>
              <Tag color={data.permission === 'COMMENT' ? 'blue' : 'default'}>
                {data.permission === 'COMMENT' ? '可批注' : '只读'}
              </Tag>
              {data.versionNo != null && <Tag>v{data.versionNo}</Tag>}
            </Space>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
              通过共享链接查看当前版本，仅展示文本内容。
            </Typography.Paragraph>
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', lineHeight: 1.9, marginBottom: 0 }}>
              {data.text || '该版本暂无可读文本。'}
            </Typography.Paragraph>
          </>
        )}
      </Card>
    </div>
  )
}