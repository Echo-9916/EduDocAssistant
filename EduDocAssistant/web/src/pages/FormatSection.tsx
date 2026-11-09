// 「格式与模板」一级导航容器（原型分组：学生 11-15 屏中的只读面）。
// 格式校验/一键修复的操作面在文档预览页（D-11/D-12），转换在文档列表（D-13），
// 本页聚合：课程模板（只读要求+按模板新建）与 代码与图表规范化。
import { Tabs } from 'antd'
import StudentTemplates from './student/Templates'
import NormalizeSection from './NormalizeSection'

export default function FormatSection() {
  return (
    <Tabs
      defaultActiveKey="templates"
      items={[
        { key: 'templates', label: '课程模板', children: <StudentTemplates /> },
        { key: 'normalize', label: '代码与图表规范化', children: <NormalizeSection /> },
      ]}
    />
  )
}
