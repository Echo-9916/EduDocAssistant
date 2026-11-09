import { useEffect, useMemo, useState } from 'react'
import { Alert, Descriptions, Drawer, Form, Input, Select, Space, Tag, Timeline, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable, requiredRule } from '../../components'
import DemoPage from '../DemoPage'
import {
  MEMBER_IMPORT, archiveCourse, courseStatusColor, courseStatusLabel, createCourse, importMembers, listCourses,
} from '../../mock/domain/governance'
import type { CourseStatus, GovCourse } from '../../mock/domain/governance'

/** 教务名单 → 平台字段映射（05 屏）。 */
const FIELD_MAPPING = [
  { key: '1', from: '学号', to: '学号', note: '账号唯一匹配依据（工号 / 学号优先）' },
  { key: '2', from: '姓名', to: '姓名', note: '自动填充账号显示名' },
  { key: '3', from: '教学班', to: '课程组', note: '保留名单中的分组，可导入后统一分组' },
  { key: '4', from: '人员类别', to: '课程角色', note: '映射为 教师 / 学生 / 助教' },
]

/** 课程治理：课程状态、成员规模、模板关联与教学运行指标（对应原型：运维 02/03/04/05/06）。 */
export default function OpsCourses() {
  const [rows, setRows] = useState<GovCourse[]>([])
  const [loading, setLoading] = useState(true)
  const [term, setTerm] = useState('ALL')
  const [status, setStatus] = useState<'ALL' | CourseStatus>('ALL')
  const [detail, setDetail] = useState<GovCourse | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [importTarget, setImportTarget] = useState<GovCourse | null>(null)
  const [archiveTarget, setArchiveTarget] = useState<GovCourse | null>(null)
  const [createForm] = Form.useForm()
  const [archiveForm] = Form.useForm()

  useEffect(() => {
    listCourses().then((r) => {
      setRows(r)
      setLoading(false)
    })
  }, [])

  const data = useMemo(
    () => rows.filter((c) => term === 'ALL' || c.term === term).filter((c) => status === 'ALL' || c.status === status),
    [rows, term, status],
  )
  const terms = useMemo(() => [...new Set(rows.map((c) => c.term))], [rows])

  const onCreate = async () => {
    const values = await createForm.validateFields()
    setRows(await createCourse(values))
    setCreateOpen(false)
    createForm.resetFields()
    message.success('课程已创建，成员名单待导入')
  }

  const onImport = async () => {
    if (!importTarget) return
    const result = await importMembers(importTarget.id)
    setRows(await listCourses())
    setImportTarget(null)
    message.success(`导入完成：成功 ${result.bound} · 待激活 ${result.pending} · 冲突跳过 ${result.conflict}，学生数 +${result.added}`)
  }

  const onArchive = async () => {
    if (!archiveTarget) return
    const values = await archiveForm.validateFields()
    await archiveCourse(archiveTarget.id, values.note)
    setRows(await listCourses())
    setArchiveTarget(null)
    message.success('课程已归档，转为只读')
  }

  const columns: ColumnsType<GovCourse> = [
    {
      title: '课程', dataIndex: 'name',
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{row.code} · {row.group}</Typography.Text>
        </Space>
      ),
    },
    { title: '学期', dataIndex: 'term', width: 120 },
    { title: '负责人', dataIndex: 'owner', width: 90 },
    {
      title: '成员规模', width: 130,
      render: (_, row) => `教师 ${row.teachers} · 学生 ${row.students}`,
    },
    { title: '关联模板', dataIndex: 'template', width: 190 },
    {
      title: '运行指标', width: 160,
      render: (_, row) => (
        <span>
          本周提交 {row.weeklySubmits} ·{' '}
          <Typography.Text type={row.formatIssues > 0 ? 'warning' : 'secondary'}>格式异常 {row.formatIssues}</Typography.Text>
        </span>
      ),
    },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (v: CourseStatus) => <Tag color={courseStatusColor[v]}>{courseStatusLabel[v]}</Tag>,
    },
    {
      title: '操作', width: 180,
      render: (_, row) => {
        const archived = row.status === 'ARCHIVED'
        return (
          <Space size={0}>
            <AppButton type="link" size="small" onClick={() => setDetail(row)}>详情</AppButton>
            <AppButton type="link" size="small" disabled={archived} onClick={() => setImportTarget(row)}>成员导入</AppButton>
            <AppButton type="link" size="small" danger disabled={archived} onClick={() => setArchiveTarget(row)}>归档</AppButton>
          </Space>
        )
      },
    },
  ]

  return (
    <DemoPage
      title="课程治理"
      screens="对应原型：运维 02/03/04/05/06"
      desc="统一维护课程状态、成员规模、模板关联和教学运行指标；运维保持只读边界，不替代教师评分。"
      extra={
        <Space wrap>
          <Select
            style={{ width: 150 }}
            value={term}
            onChange={setTerm}
            options={[{ value: 'ALL', label: '全部学期' }, ...terms.map((t) => ({ value: t, label: t }))]}
          />
          <Select
            style={{ width: 130 }}
            value={status}
            onChange={setStatus}
            options={[
              { value: 'ALL', label: '全部状态' },
              ...(Object.keys(courseStatusLabel) as CourseStatus[]).map((s) => ({ value: s, label: courseStatusLabel[s] })),
            ]}
          />
          <AppButton type="default" onClick={() => message.success('课程清单已导出')}>导出课程清单</AppButton>
          <AppButton onClick={() => setCreateOpen(true)}>新建课程</AppButton>
        </Space>
      }
    >
      <AppTable<GovCourse> rowKey="id" size="middle" loading={loading} columns={columns} dataSource={data} />

      {/* 03 屏：课程详情抽屉 */}
      <Drawer open={!!detail} onClose={() => setDetail(null)} width={560} title={detail ? `课程详情 · ${detail.name}` : ''}>
        {detail && (
          <>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="课程代码">{detail.code}</Descriptions.Item>
              <Descriptions.Item label="学期">{detail.term}</Descriptions.Item>
              <Descriptions.Item label="所属院系">{detail.dept}</Descriptions.Item>
              <Descriptions.Item label="课程负责人">{detail.owner}</Descriptions.Item>
              <Descriptions.Item label="课程组">{detail.group}</Descriptions.Item>
              <Descriptions.Item label="课程状态">
                <Tag color={courseStatusColor[detail.status]}>{courseStatusLabel[detail.status]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="成员规模">{`教师 ${detail.teachers} · 学生 ${detail.students}`}</Descriptions.Item>
              <Descriptions.Item label="关联模板">{detail.template}</Descriptions.Item>
              <Descriptions.Item label="本周提交">{detail.weeklySubmits} 份</Descriptions.Item>
              <Descriptions.Item label="格式异常">{detail.formatIssues} 项</Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>{detail.createdAt}</Descriptions.Item>
            </Descriptions>
            <Typography.Title level={5} style={{ marginTop: 16 }}>最近运行状态</Typography.Title>
            <Timeline items={detail.recent.map((text) => ({ children: text }))} />
          </>
        )}
      </Drawer>

      {/* 04 屏：新建课程 */}
      <AppModal open={createOpen} title="新建课程" okText="创建课程" onOk={onCreate} onCancel={() => setCreateOpen(false)}>
        <Typography.Paragraph type="secondary">创建课程空间并配置学期、院系、课程负责人和默认教学模板。</Typography.Paragraph>
        <Form form={createForm} layout="vertical">
          <Space.Compact block>
            <Form.Item name="name" label="课程名称" rules={[requiredRule('请输入课程名称')]} style={{ flex: 1, marginRight: 8 }}>
              <Input placeholder="如：Web开发技术" />
            </Form.Item>
            <Form.Item name="code" label="课程代码" rules={[requiredRule('请输入课程代码')]} style={{ flex: 1 }}>
              <Input placeholder="如：CS-3101" />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="term" label="学期" rules={[requiredRule('请选择学期')]}>
            <Select placeholder="请选择学期" options={[{ value: '2026-2027 秋', label: '2026-2027 秋' }, { value: '2026-2027 春', label: '2026-2027 春' }]} />
          </Form.Item>
          <Form.Item name="dept" label="所属院系" rules={[requiredRule('请选择所属院系')]}>
            <Select placeholder="请选择院系" options={['计算机学院', '软件学院', '人工智能学院'].map((v) => ({ value: v, label: v }))} />
          </Form.Item>
          <Form.Item name="owner" label="课程负责人" rules={[requiredRule('请选择课程负责人')]}>
            <Select placeholder="请选择负责人" options={['李老师 · T2021001', '王老师 · T2021043'].map((v) => ({ value: v, label: v }))} />
          </Form.Item>
          <Form.Item name="template" label="默认教学模板" rules={[requiredRule('请选择默认教学模板')]}>
            <Select
              placeholder="请选择默认教学模板"
              options={['Web开发课程设计模板 v2.1', '数据库实验报告模板 v1.8', '通用课程报告模板 v1.4'].map((v) => ({ value: v, label: v }))}
            />
          </Form.Item>
        </Form>
        <Alert type="info" showIcon message="成员名单可在创建后通过「成员导入」从教务系统同步。" />
      </AppModal>

      {/* 05 屏：成员导入 */}
      <AppModal
        open={!!importTarget}
        title={importTarget ? `成员导入 · ${importTarget.name}` : ''}
        okText="确认导入"
        width={640}
        onOk={onImport}
        onCancel={() => setImportTarget(null)}
      >
        {importTarget && (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message={`导入来源：教务系统同步 · 名单「${importTarget.code}_${importTarget.term}」共 ${MEMBER_IMPORT.rosterTotal} 行，字段完整`}
            />
            <Typography.Title level={5}>字段映射</Typography.Title>
            <AppTable
              rowKey="key"
              size="small"
              pagination={false}
              dataSource={FIELD_MAPPING}
              columns={[
                { title: '教务字段', dataIndex: 'from', width: 110 },
                { title: '平台字段', dataIndex: 'to', width: 110 },
                { title: '说明', dataIndex: 'note' },
              ]}
              style={{ marginBottom: 12 }}
            />
            <Typography.Title level={5}>账号绑定结果统计</Typography.Title>
            <Space size="large">
              <Tag color="green">成功 {MEMBER_IMPORT.bound}</Tag>
              <Tag color="orange">待激活 {MEMBER_IMPORT.pending}</Tag>
              <Tag color="red">冲突 {MEMBER_IMPORT.conflict}（跳过并记录）</Tag>
            </Space>
            <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
              冲突成员保留已有角色不覆盖；待激活成员将自动发送站内与邮件激活邀请。确认后学生数 +{MEMBER_IMPORT.added}。
            </Typography.Paragraph>
          </>
        )}
      </AppModal>

      {/* 06 屏：归档课程确认 */}
      <AppModal
        open={!!archiveTarget}
        title={archiveTarget ? `归档「${archiveTarget.name}」？` : ''}
        okText="确认归档"
        okButtonProps={{ danger: true }}
        width={620}
        onOk={onArchive}
        onCancel={() => setArchiveTarget(null)}
      >
        {archiveTarget && (
          <Form
            form={archiveForm}
            layout="vertical"
            initialValues={{ retention: '长期保留（审计需要）', note: `${archiveTarget.term}课程结束，完成成绩归档后关闭课程。` }}
          >
            <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="归档后课程内容转为只读，不可继续发布作业或修改课程模板，可联系教务处申请恢复。" />
            <Descriptions column={1} size="small" style={{ marginBottom: 12 }}>
              <Descriptions.Item label="归档范围">全部课程文档、课程模板、作业与提交记录（教师 {archiveTarget.teachers} · 学生 {archiveTarget.students}）</Descriptions.Item>
              <Descriptions.Item label="成员访问变化">教师和学生保留历史查看权限，协作编辑与作业发布关闭</Descriptions.Item>
            </Descriptions>
            <Form.Item name="retention" label="数据保留期" rules={[requiredRule('请选择数据保留期')]}>
              <Select options={['长期保留（审计需要）', '保留 3 年', '保留 1 年'].map((v) => ({ value: v, label: v }))} />
            </Form.Item>
            <Form.Item name="note" label="归档说明" rules={[requiredRule('请填写归档说明')]}>
              <Input.TextArea rows={3} />
            </Form.Item>
          </Form>
        )}
      </AppModal>
    </DemoPage>
  )
}
