import type { ReactNode } from 'react'
import { Card, Space, Typography } from 'antd'

type Props = {
  title: string
  desc?: string
  extra?: ReactNode
  children: ReactNode
}

/** 页面外框：标题 + 可选说明 + 扩展区。 */
export default function DemoPage({ title, desc, extra, children }: Props) {
  return (
    <div>
      <Space align="baseline" wrap style={{ marginBottom: 8 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
      </Space>
      {desc && (
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
          {desc}
        </Typography.Paragraph>
      )}
      {extra && <div style={{ marginBottom: 12 }}>{extra}</div>}
      {children}
    </div>
  )
}

/** 分区卡片：统一间距与标题样式。 */
export function DemoCard({ title, extra, children }: { title: string; extra?: ReactNode; children: ReactNode }) {
  return (
    <Card title={title} extra={extra} style={{ marginBottom: 16 }}>
      {children}
    </Card>
  )
}