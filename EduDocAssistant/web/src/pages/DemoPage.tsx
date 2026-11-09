import type { ReactNode } from 'react'
import { Card, Space, Tag, Typography } from 'antd'

type Props = {
  title: string
  /** 对应原型屏号说明，如「覆盖学生端 16/17/18/19/20/21 屏」，让演示可逐屏对照原型。 */
  screens?: string
  desc?: string
  extra?: ReactNode
  children: ReactNode
}

/**
 * 演示页外框：标题 + 覆盖屏号 + 「演示数据」标记。
 * 标记是刻意保留的——门面页在未接后端前要如实说明数据来源，评审时可逐屏对照 docs/prototype/screens。
 */
export default function DemoPage({ title, screens, desc, extra, children }: Props) {
  return (
    <div>
      <Space align="baseline" wrap style={{ marginBottom: 8 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
        <Tag color="orange">演示数据 · 后端未接入</Tag>
        {screens && <Typography.Text type="secondary">{screens}</Typography.Text>}
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
export function DemoCard({ title, extra, children }: Omit<Props, 'screens' | 'desc'>) {
  return (
    <Card title={title} extra={extra} style={{ marginBottom: 16 }}>
      {children}
    </Card>
  )
}
