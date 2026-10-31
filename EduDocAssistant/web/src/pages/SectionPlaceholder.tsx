import { Empty } from 'antd'

/** 分区占位：各业务页面落地前的空态。 */
export default function SectionPlaceholder({ title }: { title: string }) {
  return (
    <div>
      <h3 style={{ marginBottom: 16 }}>{title}</h3>
      <Empty description={`「${title}」建设中`} />
    </div>
  )
}
