import { useEffect, useState } from 'react'
import { Card, Col, List, Row, Space, Statistic, Tag, Timeline, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { AppButton } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import { rolePath } from '../../routes'
import { ACTIVITIES, workbenchSummary } from '../../mock/domain/course'
import type { WorkbenchSummary } from '../../mock/domain/course'

const SHORTCUTS = [
  { key: 'documents', label: '上传教学文档', desc: '绑定课程与用途，上传后自动解析', to: 'documents' },
  { key: 'templates', label: '课程模板', desc: '维护模板、格式规则与版本发布', to: 'templates' },
  { key: 'publish', label: '发布作业', desc: '配置要求、截止时间与评分标准', to: 'courses' },
  { key: 'grade', label: '批改反馈', desc: '分项评分与评语，同步消息中心', to: 'courses' },
]

const TONE_COLOR = { danger: 'error', warning: 'warning', default: 'default' } as const

/** 教学工作台：汇总课程、作业、批改与协作状态，优先处理临近截止任务（教师 01 屏）。 */
export default function TeacherWorkbench() {
  const navigate = useNavigate()
  const [data, setData] = useState<WorkbenchSummary | null>(null)

  useEffect(() => {
    workbenchSummary().then(setData)
  }, [])

  const toCourses = () => navigate(`${rolePath.TEACHER}/courses`)

  return (
    <DemoPage
      title="教学工作台"
      screens="对应原型：教师 01"
      desc="汇总课程、模板、作业和文档协作状态，优先处理临近截止的批改与发布任务。"
    >
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}><Card><Statistic title="本周临近截止作业" value={data?.dueThisWeek ?? '-'} suffix="项" /></Card></Col>
        <Col span={6}><Card><Statistic title="待批改提交" value={data?.pendingGrade ?? '-'} suffix="份" valueStyle={{ color: '#b45309' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="格式异常提交" value={data?.formatIssues ?? '-'} suffix="份" valueStyle={{ color: '#b91c1c' }} /></Card></Col>
        <Col span={6}><Card><Statistic title="进行中协作文档" value={data?.collabActive ?? '-'} suffix="份" /></Card></Col>
      </Row>

      <DemoCard title="优先处理" extra={<AppButton type="link" onClick={toCourses}>前往课程与作业</AppButton>}>
        <List
          loading={!data}
          dataSource={data?.priority ?? []}
          renderItem={(item) => (
            <List.Item
              style={{ cursor: 'pointer' }}
              onClick={toCourses}
              actions={[
                <Typography.Text key="pending" type="secondary">待批改 {item.pendingGrade} 份</Typography.Text>,
                item.overdue > 0
                  ? <Typography.Text key="overdue" type="danger">逾期 {item.overdue} 份</Typography.Text>
                  : <Typography.Text key="overdue" type="secondary">无逾期</Typography.Text>,
                <Tag key="remain" color={TONE_COLOR[item.remainTone]}>{item.remain}</Tag>,
              ]}
            >
              <List.Item.Meta
                title={`${item.title} · ${item.course}`}
                description={`截止 ${item.deadline}`}
              />
            </List.Item>
          )}
        />
      </DemoCard>

      <DemoCard title="快捷入口">
        <Row gutter={[16, 16]}>
          {SHORTCUTS.map((s) => (
            <Col span={6} key={s.key}>
              <Card size="small" title={s.label}>
                <Typography.Paragraph type="secondary" style={{ fontSize: 12, minHeight: 36 }}>{s.desc}</Typography.Paragraph>
                <AppButton size="small" onClick={() => navigate(`${rolePath.TEACHER}/${s.to}`)}>前往</AppButton>
              </Card>
            </Col>
          ))}
        </Row>
      </DemoCard>

      <DemoCard title="最近动态">
        <Timeline
          items={ACTIVITIES.map((a) => ({ color: a.color, children: (
            <Space direction="vertical" size={0}>
              <span>{a.text}</span>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>{a.time}</Typography.Text>
            </Space>
          ) }))}
        />
      </DemoCard>
    </DemoPage>
  )
}
