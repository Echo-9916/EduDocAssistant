// D-10（1107 P2-4）：学生工作台快捷入口——设计 05 明文四项：上传文档 / 按模板新建 / 查看作业 / 教学消息。
// 本切片接线「按模板新建」到格式与模板页；其余入口跳对应一级导航；统计卡随 05/06 切片填充。
import { useEffect, useState } from 'react'
import { Card, Col, Row, Statistic } from 'antd'
import { useNavigate } from 'react-router-dom'
import { AppButton } from '../../components'
import { rolePath } from '../../routes'
import { unreadCount } from '../../api/messages'

const SHORTCUTS = [
  { key: 'upload', label: '上传文档', desc: 'Word/PDF 课件、作业、报告', to: 'documents' },
  { key: 'from-template', label: '按模板新建', desc: '选课程模板生成大纲初稿', to: 'format' },
  // 作业门面页以 mock 呈现，不再按 SUBMISSION_READ 禁用入口（该权限点语义属教师/运维，作业切片落地时重新收口）
  { key: 'assignments', label: '查看作业', desc: '课程作业与提交状态', to: 'courses' },
  { key: 'messages', label: '教学消息', desc: '提醒与反馈通知', to: 'messages' },
]

export default function StudentWorkbench() {
  const navigate = useNavigate()
  // 06 切片 2：未读角标走 GET /messages/unread-count，不再拿 mock 全量自己算
  const [unread, setUnread] = useState<number | null>(null)
  useEffect(() => {
    unreadCount().then(setUnread).catch(() => setUnread(null))
  }, [])
  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}><Card><Statistic title="我的文档" value="-" suffix="（随统计切片）" /></Card></Col>
        <Col span={6}><Card><Statistic title="待提交作业" value="-" /></Card></Col>
        <Col span={6}><Card><Statistic title="未读消息" value={unread ?? '-'} /></Card></Col>
        <Col span={6}><Card><Statistic title="进行中协作" value="-" /></Card></Col>
      </Row>
      <Card title="快捷入口">
        <Row gutter={[16, 16]}>
          {SHORTCUTS.map((s) => (
            <Col span={6} key={s.key}>
              <Card size="small" title={s.label}>
                <p>{s.desc}</p>
                {/* 验收 M-06：绝对路径直达 */}
                <AppButton onClick={() => navigate(`${rolePath.STUDENT}/${s.to}`)}>前往</AppButton>
              </Card>
            </Col>
          ))}
        </Row>
      </Card>
    </div>
  )
}
