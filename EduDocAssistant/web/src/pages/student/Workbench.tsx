// D-10（1107 P2-4）：学生工作台快捷入口——设计 05 明文四项：上传文档 / 按模板新建 / 查看作业 / 教学消息。
// 本切片接线「按模板新建」到格式与模板页；其余入口跳对应一级导航；统计卡随 05/06 切片填充。
import { Card, Col, Row, Statistic } from 'antd'
import { useNavigate } from 'react-router-dom'
import { AppButton } from '../../components'
import { rolePath } from '../../routes'

const SHORTCUTS = [
  { key: 'upload', label: '上传文档', desc: 'Word/PDF 课件、作业、报告', to: 'documents' },
  { key: 'from-template', label: '按模板新建', desc: '选课程模板生成大纲初稿', to: 'format' },
  { key: 'assignments', label: '查看作业', desc: '课程作业与提交状态', to: 'courses' },
  { key: 'messages', label: '教学消息', desc: '提醒与反馈通知', to: 'messages' },
]

export default function StudentWorkbench() {
  const navigate = useNavigate()
  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}><Card><Statistic title="我的文档" value="-" suffix="（随统计切片）" /></Card></Col>
        <Col span={6}><Card><Statistic title="待提交作业" value="-" /></Card></Col>
        <Col span={6}><Card><Statistic title="未读消息" value="-" /></Card></Col>
        <Col span={6}><Card><Statistic title="进行中协作" value="-" /></Card></Col>
      </Row>
      <Card title="快捷入口">
        <Row gutter={[16, 16]}>
          {SHORTCUTS.map((s) => (
            <Col span={6} key={s.key}>
              <Card size="small" title={s.label}>
                <p>{s.desc}</p>
                {/* 验收 M-06：绝对路径直达，杜绝相对解析在不同进入方式下的落空 */}
                <AppButton onClick={() => navigate(`${rolePath.STUDENT}/${s.to}`)}>前往</AppButton>
              </Card>
            </Col>
          ))}
        </Row>
      </Card>
    </div>
  )
}
