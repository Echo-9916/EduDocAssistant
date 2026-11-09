import { useEffect, useMemo, useState } from 'react'
import { Alert, Col, Descriptions, Empty, Form, Input, InputNumber, List, Progress, Row, Segmented, Select, Space, Switch, Tabs, Tag, Typography, Upload, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable, requiredRule } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import {
  MEMBER_IMPORT,
  RUBRIC,
  courseStatusLabel,
  createCourse,
  gradeStatusLabel,
  gradeSubmission,
  importMembers,
  listCourses,
  listSubmissions,
  listTeacherAssignments,
  publishAssignment,
} from '../../mock/domain/course'
import type { GradeSubmission, TeacherAssignment, TeacherCourse } from '../../mock/domain/course'

const TERM_OPTIONS = ['2026-2027 秋', '2026-2027 春', '2025-2026 春']
const GROUP_OPTIONS = ['Web开发课程组', '数据库课程组', '操作系统课程组', '编译原理课程组']
const DOC_PERMISSION_OPTIONS = ['仅查看', '可评论', '可编辑']

/** 课程与作业：课程管理 20 / 新建课程 21 / 成员导入 22 / 提交统计 23 / 发布作业 24 / 批改反馈 25 屏。 */
export default function TeacherCourses() {
  const [tab, setTab] = useState('courses')
  const [loading, setLoading] = useState(true)
  const [courses, setCourses] = useState<TeacherCourse[]>([])
  const [assignments, setAssignments] = useState<TeacherAssignment[]>([])
  const [submissions, setSubmissions] = useState<GradeSubmission[]>([])

  // 课程管理筛选（屏 20）
  const [courseFilter, setCourseFilter] = useState('ALL')
  const [keyword, setKeyword] = useState('')
  // 成员导入（屏 22）/ 新建课程（屏 21）
  const [importCourse, setImportCourse] = useState<TeacherCourse | null>(null)
  const [importFile, setImportFile] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [courseForm] = Form.useForm()
  // 作业统计（屏 23）/ 提醒 / 发布作业（屏 24）
  const [assignmentCourse, setAssignmentCourse] = useState('ALL')
  const [remindRow, setRemindRow] = useState<TeacherAssignment | null>(null)
  const [publishOpen, setPublishOpen] = useState(false)
  const [publishForm] = Form.useForm()
  const pvTitle = Form.useWatch('title', publishForm)
  const pvCourse = Form.useWatch('course', publishForm)
  const pvDeadline = Form.useWatch('deadline', publishForm)
  const pvFormat = Form.useWatch('format', publishForm)
  const pvRequireCheck = Form.useWatch('requireFormatCheck', publishForm)
  // 批改与反馈（屏 25）
  const [current, setCurrent] = useState<GradeSubmission | null>(null)
  const [scores, setScores] = useState<Record<string, number>>({})
  const [feedback, setFeedback] = useState('')

  useEffect(() => {
    setLoading(true)
    Promise.all([listCourses(), listTeacherAssignments(), listSubmissions()]).then(([c, a, s]) => {
      setCourses(c)
      setAssignments(a)
      setSubmissions(s)
      setLoading(false)
    })
  }, [])

  const courseData = useMemo(
    () =>
      courses
        .filter((c) => courseFilter === 'ALL' || c.status === courseFilter)
        .filter((c) => !keyword || c.name.includes(keyword) || c.code.includes(keyword)),
    [courses, courseFilter, keyword],
  )
  const assignmentData = useMemo(
    () => assignments.filter((a) => assignmentCourse === 'ALL' || a.course === assignmentCourse),
    [assignments, assignmentCourse],
  )
  const assignmentCourses = useMemo(() => ['ALL', ...Array.from(new Set(assignments.map((a) => a.course)))], [assignments])
  const total = Object.values(scores).reduce((sum, v) => sum + v, 0)

  const pickSubmission = (row: GradeSubmission) => {
    setCurrent(row)
    setScores(Object.fromEntries(RUBRIC.map((r) => [r.key, 0])))
    setFeedback(row.feedback ?? '')
  }

  const doCreateCourse = async () => {
    const v = await courseForm.validateFields()
    setCourses(await createCourse({
      name: v.name, code: v.code, term: v.term,
      groups: (v.groups as string[]).length || 1, docPermission: v.docPermission,
    }))
    message.success(`课程《${v.name}》已创建，可通过「成员导入」添加学生`)
    setCreateOpen(false)
    courseForm.resetFields()
  }

  const doImport = async () => {
    if (!importCourse) return
    setCourses(await importMembers(importCourse.id))
    message.success(`导入完成：《${importCourse.name}》新增 ${MEMBER_IMPORT.ok} 名学生，${MEMBER_IMPORT.invite} 名待邀请学生将收到课程邀请`)
    setImportCourse(null)
    setImportFile('')
  }

  const doPublish = async () => {
    const v = await publishForm.validateFields()
    setAssignments(await publishAssignment({
      course: v.course, title: v.title, requirement: v.requirement, deadline: v.deadline,
      format: v.format, requireFormatCheck: v.requireFormatCheck,
      rubric: RUBRIC.map((r) => ({ label: r.label, max: v[`rubric_${r.key}`] as number ?? r.max })),
    }))
    message.success(`作业《${v.title}》已发布，学生端「课程与作业」同步可见`)
    setPublishOpen(false)
    publishForm.resetFields()
    setTab('assignments')
  }

  const doGrade = async () => {
    if (!current) return
    setSubmissions(await gradeSubmission(current.id, total, feedback))
    message.success('评分已保存，学生将在消息中心收到反馈')
    setCurrent({ ...current, status: 'GRADED', score: total, feedback })
  }

  const courseColumns: ColumnsType<TeacherCourse> = [
    { title: '学期', dataIndex: 'term', width: 120 },
    {
      title: '课程名', dataIndex: 'name', width: 180,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>课程代码 {row.code}</Typography.Text>
        </Space>
      ),
    },
    { title: '负责人', dataIndex: 'owner', width: 90 },
    { title: '班级/课程组数', dataIndex: 'groups', width: 120, render: (v: number) => `${v} 个课程组` },
    { title: '学生数', dataIndex: 'students', width: 90 },
    { title: '关联模板', dataIndex: 'template', ellipsis: true },
    {
      title: '运行状态', dataIndex: 'status', width: 100,
      render: (v: TeacherCourse['status']) => <Tag color={v === 'ACTIVE' ? 'success' : 'default'}>{courseStatusLabel[v]}</Tag>,
    },
    {
      title: '本周截止数', dataIndex: 'dueWeek', width: 110,
      render: (v: number) => (v > 0 ? <Tag color="warning">{v} 项</Tag> : <Typography.Text type="secondary">—</Typography.Text>),
    },
    {
      title: '操作', width: 110,
      render: (_, row) => (
        <AppButton type="link" size="small" disabled={row.status === 'ARCHIVED'} onClick={() => { setImportCourse(row); setImportFile('') }}>
          成员导入
        </AppButton>
      ),
    },
  ]

  const assignmentColumns: ColumnsType<TeacherAssignment> = [
    { title: '课程', dataIndex: 'course', width: 110 },
    { title: '作业', dataIndex: 'title', width: 190 },
    {
      title: '截止', dataIndex: 'deadline', width: 170,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type={row.overdue > 0 ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>{row.remain}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '应交/已交/逾期', width: 140,
      render: (_, row) => (
        <span>
          {row.expected} / {row.submitted} /{' '}
          {row.overdue > 0 ? <Typography.Text type="danger">{row.overdue}</Typography.Text> : 0}
        </span>
      ),
    },
    {
      title: '格式异常数', dataIndex: 'formatIssues', width: 110,
      render: (v: number) => (v > 0 ? <Tag color="warning">{v} 份</Tag> : <Typography.Text type="secondary">0</Typography.Text>),
    },
    { title: '已批改数', dataIndex: 'graded', width: 100 },
    {
      title: '提交率', width: 140,
      render: (_, row) => (
        <Progress percent={row.expected ? Math.round((row.submitted / row.expected) * 100) : 0} size="small" />
      ),
    },
    {
      title: '操作', width: 140,
      render: (_, row) => (
        <AppButton type="link" size="small" disabled={row.unsubmitted.length === 0} onClick={() => setRemindRow(row)}>
          提醒未提交学生
        </AppButton>
      ),
    },
  ]

  const submissionColumns: ColumnsType<GradeSubmission> = [
    {
      title: '学生', width: 130,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <span>{row.student}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{row.sid}</Typography.Text>
        </Space>
      ),
    },
    { title: '文档', dataIndex: 'doc', ellipsis: true },
    {
      title: '格式校验', dataIndex: 'format', width: 100,
      render: (v: string, row) => <Tag color={row.formatOk ? 'success' : 'warning'}>{v}</Tag>,
    },
    {
      title: '状态', width: 100,
      render: (_, row) =>
        row.status === 'GRADED'
          ? <Tag color="success">{gradeStatusLabel.GRADED} {row.score} 分</Tag>
          : <Tag color="warning">{gradeStatusLabel.PENDING_GRADE}</Tag>,
    },
  ]

  return (
    <DemoPage
      title="课程与作业"
      screens="对应原型：教师 20/21/22/23/24/25"
      desc="维护本学期课程与课程成员，跟踪作业提交与格式校验，完成批改与反馈闭环。"
    >
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          {
            key: 'courses',
            label: '课程管理',
            children: (
              <DemoCard
                title="课程列表"
                extra={
                  <Space wrap>
                    <Segmented
                      value={courseFilter}
                      onChange={(v) => setCourseFilter(v as string)}
                      options={[
                        { label: `全部 ${courses.length}`, value: 'ALL' },
                        { label: '进行中', value: 'ACTIVE' },
                        { label: '已归档', value: 'ARCHIVED' },
                      ]}
                    />
                    <Input.Search allowClear placeholder="搜索课程名 / 课程代码" style={{ width: 220 }} onSearch={setKeyword} onChange={(e) => !e.target.value && setKeyword('')} />
                    <AppButton onClick={() => setCreateOpen(true)}>新建课程</AppButton>
                  </Space>
                }
              >
                <AppTable<TeacherCourse> rowKey="id" size="middle" loading={loading} columns={courseColumns} dataSource={courseData} />
              </DemoCard>
            ),
          },
          {
            key: 'assignments',
            label: '作业与提交统计',
            children: (
              <DemoCard
                title="课程作业"
                extra={
                  <Space wrap>
                    <Select
                      style={{ width: 140 }}
                      value={assignmentCourse}
                      onChange={setAssignmentCourse}
                      options={assignmentCourses.map((c) => ({ value: c, label: c === 'ALL' ? '全部课程' : c }))}
                    />
                    <AppButton onClick={() => setPublishOpen(true)}>发布作业</AppButton>
                  </Space>
                }
              >
                <AppTable<TeacherAssignment> rowKey="id" size="middle" loading={loading} columns={assignmentColumns} dataSource={assignmentData} />
              </DemoCard>
            ),
          },
          {
            key: 'grade',
            label: '批改与反馈',
            children: (
              <Row gutter={16}>
                <Col span={15}>
                  <DemoCard title="提交列表" extra={<Typography.Text type="secondary">点击行载入右侧评分表单</Typography.Text>}>
                    <AppTable<GradeSubmission>
                      rowKey="id"
                      size="small"
                      loading={loading}
                      pagination={false}
                      columns={submissionColumns}
                      dataSource={submissions}
                      onRow={(row) => ({ onClick: () => pickSubmission(row), style: { cursor: 'pointer' } })}
                      rowClassName={(row) => (row.id === current?.id ? 'ant-table-row-selected' : '')}
                    />
                  </DemoCard>
                </Col>
                <Col span={9}>
                  <DemoCard title="评分与反馈" extra={current ? <Tag color="blue">总分 {total}</Tag> : undefined}>
                    {!current ? (
                      <Empty description="选择左侧提交开始批改" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                      <>
                        <Descriptions size="small" column={1} style={{ marginBottom: 12 }}>
                          <Descriptions.Item label="学生">{current.student}（{current.sid}）· {current.course}</Descriptions.Item>
                          <Descriptions.Item label="文档">{current.doc}</Descriptions.Item>
                          <Descriptions.Item label="格式校验">
                            <Tag color={current.formatOk ? 'success' : 'warning'}>{current.format}</Tag>
                          </Descriptions.Item>
                        </Descriptions>
                        <Space direction="vertical" style={{ width: '100%' }}>
                          {RUBRIC.map((r) => (
                            <Space key={r.key} style={{ width: '100%', justifyContent: 'space-between' }}>
                              <Typography.Text>{r.label}</Typography.Text>
                              <Space>
                                <InputNumber
                                  min={0}
                                  max={r.max}
                                  value={scores[r.key] ?? 0}
                                  onChange={(v) => setScores((prev) => ({ ...prev, [r.key]: v ?? 0 }))}
                                />
                                <Typography.Text type="secondary">/ {r.max}</Typography.Text>
                              </Space>
                            </Space>
                          ))}
                        </Space>
                        <Input.TextArea
                          rows={3}
                          style={{ marginTop: 12 }}
                          placeholder="总体评语：指出亮点与改进建议，学生可在消息中心查看"
                          value={feedback}
                          onChange={(e) => setFeedback(e.target.value)}
                        />
                        <AppButton block style={{ marginTop: 12 }} onClick={doGrade}>保存并反馈</AppButton>
                      </>
                    )}
                  </DemoCard>
                </Col>
              </Row>
            ),
          },
        ]}
      />

      {/* 屏 21：新建课程 */}
      <AppModal title="新建课程" open={createOpen} okText="创建课程" cancelText="取消" onOk={doCreateCourse} onCancel={() => setCreateOpen(false)}>
        <Form
          form={courseForm}
          layout="vertical"
          initialValues={{ term: TERM_OPTIONS[0], groups: [GROUP_OPTIONS[0]], docPermission: '可评论' }}
        >
          <Form.Item name="name" label="课程名称" rules={[requiredRule('请输入课程名称')]}>
            <Input placeholder="如：Web开发技术" />
          </Form.Item>
          <Form.Item name="code" label="课程代码" rules={[requiredRule('请输入课程代码')]}>
            <Input placeholder="如：CS-3102" />
          </Form.Item>
          <Form.Item name="term" label="学期" rules={[requiredRule('请选择学期')]}>
            <Select options={TERM_OPTIONS.map((t) => ({ value: t, label: t }))} />
          </Form.Item>
          <Form.Item name="groups" label="课程组" extra="可多选，创建后在成员导入时分配学生">
            <Select mode="multiple" options={GROUP_OPTIONS.map((g) => ({ value: g, label: g }))} />
          </Form.Item>
          <Form.Item name="docPermission" label="默认文档权限" rules={[requiredRule('请选择默认文档权限')]}>
            <Select options={DOC_PERMISSION_OPTIONS.map((p) => ({ value: p, label: p }))} />
          </Form.Item>
        </Form>
      </AppModal>

      {/* 屏 22：课程成员导入 */}
      <AppModal
        title={`课程成员导入 · ${importCourse?.name ?? ''}`}
        open={!!importCourse}
        width={640}
        okText="确认导入"
        cancelText="取消"
        onOk={doImport}
        onCancel={() => setImportCourse(null)}
        okButtonProps={{ disabled: !importFile }}
      >
        <Upload.Dragger
          accept=".csv,.xlsx"
          maxCount={1}
          beforeUpload={(file) => { setImportFile(file.name); return false }}
          onRemove={() => setImportFile('')}
        >
          <p style={{ margin: '8px 0' }}>
            <strong>拖拽课程成员名单 CSV 到此处</strong>
          </p>
          <p style={{ margin: 0 }}>
            <Typography.Text type="secondary">支持 CSV / XLSX，首行需包含学号、姓名和课程组</Typography.Text>
          </p>
        </Upload.Dragger>
        {importFile && (
          <Alert
            type="success"
            showIcon
            style={{ margin: '12px 0' }}
            message={`${importFile} · 共 ${MEMBER_IMPORT.total} 行`}
            description={`校验通过 ${MEMBER_IMPORT.ok} 行 · 待邀请 ${MEMBER_IMPORT.invite} 行（尚未绑定校内账号，导入后将发送课程邀请）`}
          />
        )}
        <Typography.Text strong>字段映射</Typography.Text>
        <AppTable
          rowKey="source"
          size="small"
          pagination={false}
          style={{ margin: '8px 0 16px' }}
          columns={[
            { title: 'CSV 列', dataIndex: 'source' },
            { title: '系统字段', dataIndex: 'target', render: (v: string) => <Typography.Text code>{v}</Typography.Text> },
            { title: '状态', width: 90, render: () => <Tag color="success">已匹配</Tag> },
          ]}
          dataSource={MEMBER_IMPORT.mapping}
        />
        <Typography.Text strong>导入预览</Typography.Text>
        <AppTable
          rowKey="sid"
          size="small"
          pagination={false}
          style={{ marginTop: 8 }}
          columns={[
            { title: '学号', dataIndex: 'sid' },
            { title: '姓名', dataIndex: 'name' },
            { title: '课程组', dataIndex: 'group' },
            { title: '状态', width: 90, render: (_, r: { state: string }) => <Tag color={r.state === '已绑定' ? 'success' : 'warning'}>{r.state}</Tag> },
          ]}
          dataSource={MEMBER_IMPORT.preview}
        />
      </AppModal>

      {/* 提醒未提交学生 */}
      <AppModal
        title={`提醒未提交学生 · ${remindRow?.title ?? ''}`}
        open={!!remindRow}
        okText="发送提醒"
        cancelText="取消"
        onOk={() => {
          message.success(`已向 ${remindRow?.unsubmitted.length ?? 0} 名学生发送截止提醒，学生将在消息中心收到通知`)
          setRemindRow(null)
        }}
        onCancel={() => setRemindRow(null)}
      >
        {remindRow && (
          <>
            <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={`截止 ${remindRow.deadline}（${remindRow.remain}），以下 ${remindRow.unsubmitted.length} 名学生尚未提交（演示节选名单）`} />
            <List
              size="small"
              bordered
              dataSource={remindRow.unsubmitted}
              renderItem={(name) => <List.Item>{name}</List.Item>}
            />
          </>
        )}
      </AppModal>

      {/* 屏 24：发布作业（含学生端预览） */}
      <AppModal
        title="发布作业"
        open={publishOpen}
        width={880}
        okText="确认发布"
        cancelText="取消"
        onOk={doPublish}
        onCancel={() => setPublishOpen(false)}
      >
        <Row gutter={16}>
          <Col span={14}>
            <Form
              form={publishForm}
              layout="vertical"
              initialValues={{
                course: courses.find((c) => c.status === 'ACTIVE')?.name,
                format: 'DOCX / PDF',
                requireFormatCheck: true,
                ...Object.fromEntries(RUBRIC.map((r) => [`rubric_${r.key}`, r.max])),
              }}
            >
              <Form.Item name="course" label="所属课程" rules={[requiredRule('请选择课程')]}>
                <Select options={courses.filter((c) => c.status === 'ACTIVE').map((c) => ({ value: c.name, label: `${c.name}（${c.term}）` }))} />
              </Form.Item>
              <Form.Item name="title" label="作业标题" rules={[requiredRule('请输入作业标题')]}>
                <Input placeholder="如：Web开发课程设计报告" />
              </Form.Item>
              <Form.Item name="requirement" label="作业要求" rules={[requiredRule('请输入作业要求')]}>
                <Input.TextArea rows={3} placeholder="提交内容、章节结构、格式与引用要求" />
              </Form.Item>
              <Form.Item name="deadline" label="截止时间" rules={[requiredRule('请输入截止时间，如 2026-11-20 23:59')]}>
                <Input placeholder="2026-11-20 23:59" />
              </Form.Item>
              <Form.Item name="format" label="提交格式" rules={[requiredRule('请选择提交格式')]}>
                <Select options={[{ value: 'DOCX' }, { value: 'PDF' }, { value: 'DOCX / PDF' }]} />
              </Form.Item>
              <Form.Item name="requireFormatCheck" label="要求格式校验通过" valuePropName="checked" extra="阻断问题未修复时禁止提交">
                <Switch />
              </Form.Item>
              <Typography.Text strong>评分标准项（总分 100）</Typography.Text>
              <Row gutter={8} style={{ marginTop: 8 }}>
                {RUBRIC.map((r) => (
                  <Col span={12} key={r.key}>
                    <Form.Item name={`rubric_${r.key}`} label={r.label} style={{ marginBottom: 8 }}>
                      <InputNumber min={0} max={100} style={{ width: '100%' }} addonAfter="分" />
                    </Form.Item>
                  </Col>
                ))}
              </Row>
            </Form>
          </Col>
          <Col span={10}>
            <Typography.Text strong>学生端预览</Typography.Text>
            <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 16, marginTop: 8 }}>
              <Tag color="blue">作业预览</Tag>
              <Typography.Title level={5} style={{ marginTop: 8 }}>{pvTitle || '（作业标题）'}</Typography.Title>
              <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
                {pvCourse || '课程'} · 个人提交 · 截止 {pvDeadline || '（截止时间）'}
              </Typography.Paragraph>
              {pvRequireCheck !== false && (
                <Alert type="info" showIcon message="提交前需通过课程模板格式校验" style={{ marginBottom: 12 }} />
              )}
              <List
                size="small"
                header="评分标准"
                dataSource={RUBRIC}
                renderItem={(r) => <List.Item extra={`${publishForm.getFieldValue(`rubric_${r.key}`) ?? r.max} 分`}>{r.label}</List.Item>}
              />
              <AppButton block disabled>选择提交文档（学生端操作）</AppButton>
            </div>
          </Col>
        </Row>
      </AppModal>
    </DemoPage>
  )
}
