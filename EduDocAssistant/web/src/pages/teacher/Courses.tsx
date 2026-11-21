import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Col, Descriptions, Empty, Form, Input, InputNumber, List, Progress, Row, Segmented, Select, Space, Switch, Tabs, Tag, Typography, Upload, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable, requiredRule } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import { getErrorMessage } from '../../api/http'
import {
  createCourse,
  gradeSubmission,
  importMembers,
  listAssignments,
  listCourseStudents,
  listSubmissions,
  listTeacherCourses,
  listUnsubmitted,
  publishAssignment,
  remindUnsubmitted,
  uploadAssignmentAttachment,
} from '../../api/coursework'
import type {
  AssignmentView,
  CourseStudentView,
  FormatState,
  ImportResult,
  ImportRow,
  RubricItem,
  SubmissionView,
  TeacherCourseView,
} from '../../api/coursework'
import { parseMemberCsv } from './memberCsv'
import { listTemplates } from '../../api/template'
import type { TemplateView } from '../../api/template'

const TERM_OPTIONS = ['2026-2027 秋', '2026-2027 春', '2025-2026 春']
const DOC_PERMISSION_OPTIONS = ['仅查看', '可评论', '可编辑']

/** 发布作业表单的默认评分标准（后端 rubric 随作业存储，批改时按作业回读）。 */
const DEFAULT_RUBRIC: RubricItem[] = [
  { name: '需求分析', maxScore: 20 },
  { name: '系统设计', maxScore: 20 },
  { name: '实现与测试', maxScore: 20 },
  { name: '格式规范', maxScore: 20 },
  { name: '结论与思考', maxScore: 20 },
]


const COURSE_STATUS: Record<string, { label: string; color: string }> = {
  A: { label: '进行中', color: 'success' },
  C: { label: '已归档', color: 'default' },
}
const ASSIGN_STATUS: Record<string, { label: string; color: string }> = {
  DRAFT: { label: '草稿', color: 'default' },
  PUBLISHED: { label: '已发布', color: 'processing' },
  CLOSED: { label: '已截止', color: 'warning' },
}
const FORMAT_LABEL: Record<FormatState, { label: string; color: string }> = {
  NONE: { label: '未校验', color: 'default' },
  PASS: { label: '通过', color: 'success' },
  WARN: { label: '警告', color: 'warning' },
  BLOCK: { label: '阻塞', color: 'error' },
}
const SUB_STATUS: Record<SubmissionView['status'], { label: string; color: string }> = {
  SUBMITTED: { label: '待批改', color: 'warning' },
  LATE: { label: '迟交 · 待批改', color: 'warning' },
  WITHDRAWN: { label: '已撤回', color: 'default' },
  GRADED: { label: '已批改', color: 'success' },
}

const COURSE_STUDENT_COLUMNS: ColumnsType<CourseStudentView> = [
  { title: '学号', dataIndex: 'accountNo', width: 120 },
  { title: '姓名', dataIndex: 'name', width: 100 },
  { title: '课程组', dataIndex: 'groupName', render: (v: string | null) => v ?? '-' },
]

/** ISO 串（带 +08:00 偏移）→ 'YYYY-MM-DD HH:mm' 展示。 */
const fmtTime = (iso: string | null | undefined) => (iso ? iso.slice(0, 16).replace('T', ' ') : '—')

/** 课程与作业：课程管理 20 / 新建课程 21 / 成员导入 22 / 提交统计 23 / 发布作业 24 / 批改反馈 25 屏。 */
export default function TeacherCourses() {
  const [tab, setTab] = useState('courses')
  const [loading, setLoading] = useState(true)
  const [courses, setCourses] = useState<TeacherCourseView[]>([])
  const [assignments, setAssignments] = useState<AssignmentView[]>([])
  const [submissions, setSubmissions] = useState<SubmissionView[]>([])

  // 课程管理筛选（屏 20）
  const [courseFilter, setCourseFilter] = useState('ALL')
  const [keyword, setKeyword] = useState('')
  // 课程管理行展开：课程内全部学生
  const [expandedCourseIds, setExpandedCourseIds] = useState<string[]>([])
  const [courseStudents, setCourseStudents] = useState<Record<string, CourseStudentView[]>>({})
  const [courseStudentsLoading, setCourseStudentsLoading] = useState<Record<string, boolean>>({})
  // 成员导入（屏 22）/ 新建课程（屏 21）
  const [importCourse, setImportCourse] = useState<TeacherCourseView | null>(null)
  const [importFile, setImportFile] = useState('')
  const [importRows, setImportRows] = useState<ImportRow[]>([])
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [courseForm] = Form.useForm()
  // 作业统计（屏 23）/ 提醒 / 发布作业（屏 24）
  const [assignmentCourse, setAssignmentCourse] = useState('ALL')
  const [remindRow, setRemindRow] = useState<AssignmentView | null>(null)
  const [remindList, setRemindList] = useState<{ accountNo: string; name: string }[] | null>(null)
  const [publishOpen, setPublishOpen] = useState(false)
  const [publishTemplates, setPublishTemplates] = useState<TemplateView[]>([])
  const [publishAttachments, setPublishAttachments] = useState<{ id: string; fileName: string }[]>([])
  const [uploadingAttachment, setUploadingAttachment] = useState(false)
  const [publishForm] = Form.useForm()
  const pvTitle = Form.useWatch('title', publishForm)
  const pvCourse = Form.useWatch('courseId', publishForm)
  const pvDeadline = Form.useWatch('deadline', publishForm)
  const pvFormat = Form.useWatch('format', publishForm)
  const pvRequireCheck = Form.useWatch('requireFormatCheck', publishForm)
  // 批改与反馈（屏 25）：课程 / 学生 / 状态筛选
  const [gradeCourse, setGradeCourse] = useState('ALL')
  const [gradeStatus, setGradeStatus] = useState<'ALL' | SubmissionView['status']>('ALL')
  const [gradeKeyword, setGradeKeyword] = useState('')
  const [current, setCurrent] = useState<SubmissionView | null>(null)
  const [scores, setScores] = useState<Record<string, number>>({})
  const [feedback, setFeedback] = useState('')
  const [reminding, setReminding] = useState(false)
  const gradeFilterReady = useRef(false)

  const loadCourses = () =>
    listTeacherCourses({ page: 1, pageSize: 100 })
      .then((r) => {
        setCourses(r.items)
        setCourseStudents({})
      })
      .catch((e) => message.error(getErrorMessage(e)))
  const loadAssignments = () =>
    listAssignments({ page: 1, pageSize: 100 }).then((r) => setAssignments(r.items)).catch((e) => message.error(getErrorMessage(e)))
  const loadSubmissions = () =>
    listSubmissions({
      courseId: gradeCourse === 'ALL' ? undefined : gradeCourse,
      status: gradeStatus === 'ALL' ? undefined : gradeStatus,
      keyword: gradeKeyword.trim() || undefined,
      page: 1,
      pageSize: 100,
    }).then((r) => setSubmissions(r.items)).catch((e) => message.error(getErrorMessage(e)))

  useEffect(() => {
    setLoading(true)
    Promise.all([loadCourses(), loadAssignments(), loadSubmissions()]).finally(() => setLoading(false))
  }, [])

  // 发布作业：按选中课程加载可选模板（取当前已发布版本）
  useEffect(() => {
    if (!publishOpen || !pvCourse) {
      setPublishTemplates([])
      return
    }
    listTemplates({ courseId: pvCourse, page: 1, pageSize: 100 })
      .then((r) => setPublishTemplates(r.items.filter((t) => t.currentVersionId)))
      .catch(() => setPublishTemplates([]))
  }, [publishOpen, pvCourse])

  // 首次挂载由上面的 Promise.all 负责；之后筛选项变化才重新取提交列表
  useEffect(() => {
    if (!gradeFilterReady.current) {
      gradeFilterReady.current = true
      return
    }
    loadSubmissions()
  }, [gradeCourse, gradeStatus, gradeKeyword])

  const courseData = useMemo(
    () =>
      courses
        .filter((c) => courseFilter === 'ALL' || c.status === courseFilter)
        .filter((c) => !keyword || c.courseName.includes(keyword) || c.courseCode.includes(keyword)),
    [courses, courseFilter, keyword],
  )
  const assignmentData = useMemo(
    () => assignments.filter((a) => assignmentCourse === 'ALL' || a.courseId === assignmentCourse),
    [assignments, assignmentCourse],
  )
  /** 当前批改提交的评分标准：来自所属作业（未批改提交的 items 为空数组，不能拿它渲染表单）。 */
  const currentRubric = useMemo(
    () => (current ? assignments.find((a) => a.id === current.assignmentId)?.rubric ?? [] : []),
    [current, assignments],
  )
  const total = currentRubric.reduce((sum, r) => sum + (scores[r.name] ?? 0), 0)

  const pickSubmission = (row: SubmissionView) => {
    setCurrent(row)
    const rubric = assignments.find((a) => a.id === row.assignmentId)?.rubric ?? []
    setScores(Object.fromEntries(rubric.map((r) => [r.name, row.items.find((i) => i.name === r.name)?.score ?? 0])))
    setFeedback(row.feedback ?? '')
  }

  const doCreateCourse = async () => {
    const v = await courseForm.validateFields()
    try {
      await createCourse({ name: v.name, code: v.code, term: v.term })
      message.success(`课程《${v.name}》已创建，可通过「成员导入」添加学生`)
      setCreateOpen(false)
      courseForm.resetFields()
      await loadCourses()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const parseImportFile = async (file: File) => {
    setImportFile(file.name)
    setImportResult(null)
    setImportRows([])
    if (!file.name.toLowerCase().endsWith('.csv')) {
      message.warning('当前仅支持 CSV 名单，XLSX 待接入')
      return
    }
    try {
      const rows = parseMemberCsv(await file.text())
      if (rows.length === 0) {
        message.warning('CSV 中没有可导入的成员行')
        return
      }
      setImportRows(rows)
    } catch {
      message.error('CSV 解析失败，请检查文件编码与列头')
    }
  }

  const doImport = async () => {
    if (!importCourse || importRows.length === 0) return
    try {
      const res = await importMembers(importCourse.id, importRows)
      setImportResult(res)
      message.success(`导入完成：新建 ${res.created} · 激活 ${res.activated} · 冲突 ${res.conflicts} · 失败 ${res.failures.length}`)
      await loadCourses()
      if (expandedCourseIds.includes(importCourse.id)) {
        const items = await listCourseStudents(importCourse.id)
        setCourseStudents((prev) => ({ ...prev, [importCourse.id]: items }))
      }
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const closeImport = () => {
    setImportCourse(null)
    setImportFile('')
    setImportRows([])
    setImportResult(null)
  }

  const doUploadAttachment = async (file: File) => {
    if (!file.name.toLowerCase().endsWith('.docx')) {
      message.warning('附件格式须与课程模板一致，仅支持 .docx')
      return
    }
    setUploadingAttachment(true)
    try {
      const a = await uploadAssignmentAttachment(file)
      setPublishAttachments((prev) => [...prev, { id: a.id, fileName: a.fileName }])
      message.success(`附件《${a.fileName}》已上传`)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setUploadingAttachment(false)
    }
  }

  const doPublish = async () => {
    const v = await publishForm.validateFields()
    try {
      // datetime-local 值是本地墙上时间 'YYYY-MM-DDTHH:mm'（可能带秒），补 :00 与 +08:00 偏移拼成后端要的 ISO 串
      const raw = String(v.deadline)
      const dueTime = `${raw}${raw.length === 16 ? ':00' : ''}+08:00`
      const res = await publishAssignment({
        courseId: v.courseId,
        title: v.title,
        requirement: v.requirement,
        dueTime,
        submissionFormat: v.format,
        allowLate: v.allowLate ? 'Y' : 'N',
        requireFormatCheck: v.requireFormatCheck ? 'Y' : 'N',
        templateVersionId: v.templateVersionId,
        rubric: DEFAULT_RUBRIC.map((r) => ({ name: r.name, maxScore: v[`rubric_${r.name}`] ?? r.maxScore })),
        attachmentIds: publishAttachments.map((a) => a.id),
      })
      message.success(`作业《${v.title}》已${res.status === 'DRAFT' ? '存为草稿' : '发布'}，学生端「课程与作业」同步可见`)
      setPublishOpen(false)
      setPublishAttachments([])
      publishForm.resetFields()
      setTab('assignments')
      await loadAssignments()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const openRemind = (row: AssignmentView) => {
    setRemindRow(row)
    setRemindList(null)
    listUnsubmitted(row.id)
      .then((r) => setRemindList(r.items))
      .catch((e) => {
        message.error(getErrorMessage(e))
        setRemindRow(null)
      })
  }

  const doRemind = async () => {
    if (!remindRow) return
    setReminding(true)
    try {
      const res = await remindUnsubmitted(remindRow.id)
      message.success(res.reminded > 0
        ? `已向 ${res.reminded} 名未提交学生发布提醒（同一学生当天只提醒一次）`
        : '所有学生均已提交，无需提醒')
      setRemindRow(null)
      setRemindList(null)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setReminding(false)
    }
  }

  const toggleCourseStudents = (row: TeacherCourseView) => {
    if (expandedCourseIds.includes(row.id)) {
      setExpandedCourseIds((ids) => ids.filter((id) => id !== row.id))
      return
    }
    setExpandedCourseIds((ids) => [...ids, row.id])
    if (courseStudents[row.id]) return
    setCourseStudentsLoading((prev) => ({ ...prev, [row.id]: true }))
    listCourseStudents(row.id)
      .then((items) => setCourseStudents((prev) => ({ ...prev, [row.id]: items })))
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setCourseStudentsLoading((prev) => ({ ...prev, [row.id]: false })))
  }

  const doGrade = async () => {
    if (!current) return
    if (current.status === 'GRADED') {
      message.warning('该提交已批改，不能重复修改')
      return
    }
    if (!currentRubric.length) {
      message.warning('未找到该作业的评分标准，无法评分')
      return
    }
    try {
      const res = await gradeSubmission(current.id, {
        items: currentRubric.map((r) => ({ name: r.name, score: scores[r.name] ?? 0 })),
        feedback: feedback.trim() ? feedback : undefined,
      })
      message.success(`评分已保存：总分 ${res.score}（服务端按评分标准计算）`)
      setCurrent(null)
      await Promise.all([loadSubmissions(), loadAssignments()])
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const courseColumns: ColumnsType<TeacherCourseView> = [
    { title: '学期', dataIndex: 'termCode', width: 120 },
    {
      title: '课程名', dataIndex: 'courseName', width: 180,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>课程代码 {row.courseCode}</Typography.Text>
        </Space>
      ),
    },
    { title: '负责人', dataIndex: 'ownerName', width: 90 },
    { title: '课程组', dataIndex: 'groups', width: 110, render: (v: number) => `${v} 个` },
    { title: '学生数', dataIndex: 'students', width: 110,
      render: (v: number, row) => (
        <Space direction="vertical" size={0}>
          <span>{v} 人</span>
          <AppButton type="link" size="small" style={{ padding: 0, height: 'auto' }} onClick={() => toggleCourseStudents(row)}>
            {expandedCourseIds.includes(row.id) ? '收起' : '查看所有学生'}
          </AppButton>
        </Space>
      ),
    },
    {
      title: '运行状态', dataIndex: 'status', width: 100,
      render: (v: string) => <Tag color={COURSE_STATUS[v]?.color}>{COURSE_STATUS[v]?.label ?? v}</Tag>,
    },
    {
      title: '本周截止数', dataIndex: 'dueWeek', width: 110,
      render: (v: number) => (v > 0 ? <Tag color="warning">{v} 项</Tag> : <Typography.Text type="secondary">—</Typography.Text>),
    },
    {
      title: '操作', width: 110,
      render: (_, row) => (
        <AppButton type="link" size="small" disabled={row.status === 'C'} onClick={() => { setImportCourse(row); setImportFile(''); setImportResult(null) }}>
          成员导入
        </AppButton>
      ),
    },
  ]

  const assignmentColumns: ColumnsType<AssignmentView> = [
    { title: '课程', dataIndex: 'courseName', width: 110 },
    { title: '作业', dataIndex: 'title', width: 190 },
    {
      title: '模板', dataIndex: 'templateName', width: 150,
      render: (v: string | null, row) => v
        ? <Tag color="blue">{`${v} v${row.templateVersionNo ?? ''}`}</Tag>
        : <Typography.Text type="secondary">未指定</Typography.Text>,
    },
    {
      title: '截止', dataIndex: 'dueTime', width: 170,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{fmtTime(v)}</span>
          <Tag color={ASSIGN_STATUS[row.status]?.color}>{ASSIGN_STATUS[row.status]?.label ?? row.status}</Tag>
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
        <AppButton type="link" size="small" disabled={row.unsubmitted === 0} onClick={() => openRemind(row)}>
          提醒未提交学生
        </AppButton>
      ),
    },
  ]

  const submissionColumns: ColumnsType<SubmissionView> = [
    {
      title: '学生', width: 130,
      render: (_, row) => (
        <Space direction="vertical" size={0}>
          <span>{row.studentName}</span>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{row.accountNo}</Typography.Text>
        </Space>
      ),
    },
    { title: '文档', dataIndex: 'documentTitle', ellipsis: true },
    {
      title: '格式校验', dataIndex: 'format', width: 100,
      render: (v: FormatState) => <Tag color={FORMAT_LABEL[v]?.color}>{FORMAT_LABEL[v]?.label ?? v}</Tag>,
    },
    {
      title: '状态', width: 120,
      render: (_, row) => (
        <Tag color={SUB_STATUS[row.status]?.color}>
          {SUB_STATUS[row.status]?.label ?? row.status}{row.status === 'GRADED' && row.score != null ? ` ${row.score} 分` : ''}
        </Tag>
      ),
    },
  ]

  return (
    <DemoPage
      title="课程与作业"
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
                        { label: '进行中', value: 'A' },
                        { label: '已归档', value: 'C' },
                      ]}
                    />
                    <Input.Search allowClear placeholder="搜索课程名 / 课程代码" style={{ width: 220 }} onSearch={setKeyword} onChange={(e) => !e.target.value && setKeyword('')} />
                    <AppButton onClick={() => setCreateOpen(true)}>新建课程</AppButton>
                  </Space>
                }
              >
                <AppTable<TeacherCourseView>
                  rowKey="id"
                  size="middle"
                  loading={loading}
                  columns={courseColumns}
                  dataSource={courseData}
                  expandable={{
                    expandedRowKeys: expandedCourseIds,
                    expandIcon: () => null,
                    indentSize: 28,
                    expandedRowRender: (row) => (
                      <div style={{
                        margin: '4px 8px 12px',
                        padding: '12px 16px',
                        background: '#fafafa',
                        border: '1px solid #f0f0f0',
                        borderRadius: 8,
                      }}>
                        <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                          全部学生（{courseStudents[row.id]?.length ?? 0} 人）
                        </Typography.Text>
                        <AppTable<CourseStudentView>
                          rowKey="userId"
                          size="small"
                          pagination={false}
                          loading={!!courseStudentsLoading[row.id]}
                          columns={COURSE_STUDENT_COLUMNS}
                          dataSource={courseStudents[row.id] ?? []}
                          locale={{ emptyText: '该课程暂无学生' }}
                        />
                      </div>
                    ),
                  }}
                />
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
                      style={{ width: 160 }}
                      value={assignmentCourse}
                      onChange={setAssignmentCourse}
                      options={[
                        { value: 'ALL', label: '全部课程' },
                        ...courses.map((c) => ({ value: c.id, label: c.courseName })),
                      ]}
                    />
                    <AppButton onClick={() => setPublishOpen(true)}>发布作业</AppButton>
                  </Space>
                }
              >
                <AppTable<AssignmentView> rowKey="id" size="middle" loading={loading} columns={assignmentColumns} dataSource={assignmentData} />
              </DemoCard>
            ),
          },
          {
            key: 'grade',
            label: '批改与反馈',
            children: (
              <>
                <Space wrap style={{ marginBottom: 12 }}>
                  <Select
                    style={{ width: 180 }}
                    value={gradeCourse}
                    onChange={setGradeCourse}
                    options={[
                      { value: 'ALL', label: '全部课程' },
                      ...courses.map((c) => ({ value: c.id, label: c.courseName })),
                    ]}
                  />
                  <Input.Search
                    allowClear
                    style={{ width: 220 }}
                    placeholder="学号或姓名"
                    onSearch={setGradeKeyword}
                    onChange={(e) => { if (!e.target.value) setGradeKeyword('') }}
                  />
                  <Select
                    style={{ width: 160 }}
                    value={gradeStatus}
                    onChange={setGradeStatus}
                    options={[
                      { value: 'ALL', label: '全部状态' },
                      ...(Object.keys(SUB_STATUS) as SubmissionView['status'][]).map((status) => ({
                        value: status,
                        label: SUB_STATUS[status].label,
                      })),
                    ]}
                  />
                </Space>
                <Row gutter={16}>
                <Col span={15}>
                  <DemoCard title="提交列表" extra={<Typography.Text type="secondary">点击行载入右侧评分表单</Typography.Text>}>
                    <AppTable<SubmissionView>
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
                          <Descriptions.Item label="学生">{current.studentName}（{current.accountNo}）· {current.courseName}</Descriptions.Item>
                          <Descriptions.Item label="作业">{current.assignmentTitle}</Descriptions.Item>
                          <Descriptions.Item label="文档">{current.documentTitle}{current.versionNo != null ? `（v${current.versionNo} · 第 ${current.attemptNo} 次提交）` : ''}</Descriptions.Item>
                          <Descriptions.Item label="格式校验">
                            <Tag color={FORMAT_LABEL[current.format]?.color}>{FORMAT_LABEL[current.format]?.label ?? current.format}</Tag>
                          </Descriptions.Item>
                        </Descriptions>
                        {current.status === 'GRADED' && (
                          <Alert
                            type="warning"
                            showIcon
                            style={{ marginBottom: 12 }}
                            message={`该提交已批改${current.score != null ? `（${current.score} 分）` : ''}，不能再修改`}
                          />
                        )}
                        {!currentRubric.length ? (
                          <Alert type="warning" showIcon message="未找到该作业的评分标准（rubric），无法评分" />
                        ) : (
                          <Space direction="vertical" style={{ width: '100%' }}>
                            {currentRubric.map((r) => (
                              <Space key={r.name} style={{ width: '100%', justifyContent: 'space-between' }}>
                                <Typography.Text>{r.name}</Typography.Text>
                                <Space>
                                  <InputNumber
                                    min={0}
                                    max={r.maxScore}
                                    disabled={current.status === 'GRADED'}
                                    value={scores[r.name] ?? 0}
                                    onChange={(v) => setScores((prev) => ({ ...prev, [r.name]: v ?? 0 }))}
                                  />
                                  <Typography.Text type="secondary">/ {r.maxScore}</Typography.Text>
                                </Space>
                              </Space>
                            ))}
                          </Space>
                        )}
                        <Input.TextArea
                          rows={3}
                          style={{ marginTop: 12 }}
                          disabled={current.status === 'GRADED'}
                          placeholder="总体评语：指出亮点与改进建议，学生可在消息中心查看"
                          value={feedback}
                          onChange={(e) => setFeedback(e.target.value)}
                        />
                        <AppButton
                          block
                          style={{ marginTop: 12 }}
                          disabled={!currentRubric.length || current.status === 'GRADED'}
                          onClick={doGrade}
                        >
                          保存并反馈
                        </AppButton>
                      </>
                    )}
                  </DemoCard>
                </Col>
                </Row>
              </>
            ),
          },
        ]}
      />

      {/* 屏 21：新建课程（课程组/文档权限为演示字段，createCourse 只收 name/code/term） */}
      <AppModal title="新建课程" open={createOpen} okText="创建课程" cancelText="取消" onOk={doCreateCourse} onCancel={() => setCreateOpen(false)}>
        <Form
          form={courseForm}
          layout="vertical"
          initialValues={{ term: TERM_OPTIONS[0], docPermission: '可评论' }}
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
          <Form.Item name="docPermission" label="默认文档权限" extra="后端创建接口暂不接收，随文档权限切片接入">
            <Select options={DOC_PERMISSION_OPTIONS.map((p) => ({ value: p, label: p }))} />
          </Form.Item>
        </Form>
      </AppModal>

      {/* 屏 22：课程成员导入（预览名单即真实 rows，导入结果来自后端 ImportResult） */}
      <AppModal
        title={`课程成员导入 · ${importCourse?.courseName ?? ''}`}
        open={!!importCourse}
        width={640}
        okText={importResult ? '关闭' : '确认导入'}
        cancelText="取消"
        onOk={() => (importResult ? closeImport() : doImport())}
        onCancel={closeImport}
        okButtonProps={{ disabled: importRows.length === 0 }}
        cancelButtonProps={{ style: importResult ? { display: 'none' } : undefined }}
      >
        <Upload.Dragger
          accept=".csv"
          maxCount={1}
          disabled={!!importResult}
          beforeUpload={(file) => { void parseImportFile(file as File); return false }}
          onRemove={() => {
            setImportFile('')
            setImportRows([])
            setImportResult(null)
          }}
        >
          <p style={{ margin: '8px 0' }}>
            <strong>拖拽课程成员名单 CSV 到此处</strong>
          </p>
          <p style={{ margin: 0 }}>
            <Typography.Text type="secondary">支持 CSV，首行需包含学号、姓名和课程组</Typography.Text>
          </p>
        </Upload.Dragger>
        {importFile && !importResult && (
          <Alert
            type={importRows.length > 0 ? 'info' : 'warning'}
            showIcon
            style={{ margin: '12px 0' }}
            message={`${importFile} · 解析到 ${importRows.length} 行`}
            description="确认导入后，下方预览名单将真实写入课程成员：无账号者建未激活账号（登录与邀请投递随消息切片开放），已有账号者直接入组"
          />
        )}
        {importResult && (
          <Alert
            type={importResult.failures.length ? 'warning' : 'success'}
            showIcon
            style={{ margin: '12px 0' }}
            message={`导入结果：共 ${importResult.total} 行 · 新建账号 ${importResult.created} · 激活入组 ${importResult.activated} · 已在课程 ${importResult.conflicts}`}
            description={
              importResult.failures.length
                ? importResult.failures.map((f) => `${f.accountNo || '(空学号)'}：${f.reason}`).join('；')
                : undefined
            }
          />
        )}
        <Typography.Text strong>导入预览</Typography.Text>
        <AppTable
          rowKey="accountNo"
          size="small"
          pagination={false}
          style={{ marginTop: 8 }}
          columns={[
            { title: '学号', dataIndex: 'accountNo' },
            { title: '姓名', dataIndex: 'name' },
            { title: '课程组', dataIndex: 'group' },
          ]}
          dataSource={importRows}
          locale={{ emptyText: '上传 CSV 后显示解析结果' }}
        />
      </AppModal>

      {/* 提醒未提交学生：名单来自 listUnsubmitted；点「发布提醒」写通知 outbox */}
      <AppModal
        title={`提醒未提交学生 · ${remindRow?.title ?? ''}`}
        open={!!remindRow}
        okText="发布提醒"
        cancelText="取消"
        confirmLoading={reminding}
        onOk={doRemind}
        onCancel={() => {
          if (!reminding) {
            setRemindRow(null)
            setRemindList(null)
          }
        }}
      >
        {remindRow && (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 12 }}
              message={`截止 ${fmtTime(remindRow.dueTime)}，${remindRow.unsubmitted} 名学生尚未提交`}
              description="点击「发布提醒」会向名单内学生发送站内消息；同一学生同一作业同一天只提醒一次。"
            />
            <List
              size="small"
              bordered
              loading={!remindList}
              dataSource={remindList ?? []}
              renderItem={(s) => <List.Item>{s.name}（{s.accountNo}）</List.Item>}
              locale={{ emptyText: remindList ? '全部学生均已提交' : '加载中…' }}
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
        onCancel={() => { setPublishOpen(false); setPublishAttachments([]) }}
      >
        <Row gutter={16}>
          <Col span={14}>
            <Form
              form={publishForm}
              layout="vertical"
              initialValues={{
                courseId: courses.find((c) => c.status === 'A')?.id,
                format: 'BOTH',
                allowLate: true,
                // 默认不勾：勾上会在学生端承诺一个后端还没做的同步阻断（A4）
                requireFormatCheck: false,
                ...Object.fromEntries(DEFAULT_RUBRIC.map((r) => [`rubric_${r.name}`, r.maxScore])),
              }}
            >
              <Form.Item name="courseId" label="所属课程" rules={[requiredRule('请选择课程')]}>
                <Select options={courses.filter((c) => c.status === 'A').map((c) => ({ value: c.id, label: `${c.courseName}（${c.termCode}）` }))} />
              </Form.Item>
              <Form.Item name="templateVersionId" label="指定模板"
                extra="学生端会显示该模板；可到「格式与模板」按它新建文档">
                <Select allowClear placeholder="选择课程模板（可选）"
                  options={publishTemplates.map((t) => ({
                    value: t.currentVersionId!,
                    label: `${t.templateName}（v${t.currentVersionNo ?? '?'}）`,
                  }))} />
              </Form.Item>
              <Form.Item label="作业附件" extra="仅支持 .docx（与课程模板格式一致）；学生可下载或在附件基础上编辑">
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Upload
                    accept=".docx"
                    showUploadList={false}
                    disabled={uploadingAttachment}
                    beforeUpload={(file) => { void doUploadAttachment(file as File); return false }}
                  >
                    <AppButton loading={uploadingAttachment}>上传 .docx 附件</AppButton>
                  </Upload>
                  {publishAttachments.map((a) => (
                    <Tag key={a.id} closable onClose={() =>
                      setPublishAttachments((prev) => prev.filter((x) => x.id !== a.id))}>
                      {a.fileName}
                    </Tag>
                  ))}
                </Space>
              </Form.Item>
              <Form.Item name="title" label="作业标题" rules={[requiredRule('请输入作业标题')]}>
                <Input placeholder="如：Web开发课程设计报告" />
              </Form.Item>
              <Form.Item name="requirement" label="作业要求" rules={[requiredRule('请输入作业要求')]}>
                <Input.TextArea rows={3} placeholder="提交内容、章节结构、格式与引用要求" />
              </Form.Item>
              <Form.Item name="deadline" label="截止时间" rules={[requiredRule('请选择截止时间')]}>
                {/* 原生 datetime-local：值为本地墙上时间，提交时补 :00+08:00 拼成后端 ISO 串 */}
                <Input type="datetime-local" />
              </Form.Item>
              <Form.Item name="format" label="提交格式" rules={[requiredRule('请选择提交格式')]}>
                <Select options={[{ value: 'DOCX', label: 'DOCX' }, { value: 'PDF', label: 'PDF' }, { value: 'BOTH', label: 'DOCX / PDF' }]} />
              </Form.Item>
              <Form.Item name="allowLate" label="允许迟交" valuePropName="checked" extra="关闭后过截止提交将被拒（409）">
                <Switch />
              </Form.Item>
              {/* 后端本期不做提交前同步阻断（A4），文案不能再承诺「禁止提交」 */}
              <Form.Item name="requireFormatCheck" label="要求格式校验" valuePropName="checked" extra="提交后异步回填校验结论，暂不阻断提交">
                <Switch />
              </Form.Item>
              <Typography.Text strong>评分标准项（总分 100）</Typography.Text>
              <Row gutter={8} style={{ marginTop: 8 }}>
                {DEFAULT_RUBRIC.map((r) => (
                  <Col span={12} key={r.name}>
                    <Form.Item name={`rubric_${r.name}`} label={r.name} style={{ marginBottom: 8 }}>
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
                {courses.find((c) => c.id === pvCourse)?.courseName ?? '课程'} · 个人提交 · 截止 {fmtTime(pvDeadline) === '—' ? '（截止时间）' : fmtTime(pvDeadline)}
                {pvFormat ? ` · ${pvFormat === 'BOTH' ? 'DOCX / PDF' : pvFormat}` : ''}
              </Typography.Paragraph>
              {pvRequireCheck !== false && (
                <Alert type="info" showIcon message="提交后回填课程模板格式校验结论（本期不阻断提交）" style={{ marginBottom: 12 }} />
              )}
              <List
                size="small"
                header="评分标准"
                dataSource={DEFAULT_RUBRIC}
                renderItem={(r) => <List.Item extra={`${publishForm.getFieldValue(`rubric_${r.name}`) ?? r.maxScore} 分`}>{r.name}</List.Item>}
              />
              <AppButton block disabled>选择提交文档（学生端操作）</AppButton>
            </div>
          </Col>
        </Row>
      </AppModal>
    </DemoPage>
  )
}
