// 06 切片 1：课程与作业（学生）——全部接真实后端。
// 我的作业 = GET /student/assignments（status/courseId 服务端筛选，状态与倒计时后端派生）；
// 提交 = POST /student/assignments/{id}/submissions；
// 导出/下载中心 = 复用转换接口 POST /conversions、GET /conversions?documentId（06 计划：无独立下载端点）。
import { useEffect, useState } from 'react'
import { Descriptions, Form, Segmented, Select, Space, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable, requiredRule } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import { getErrorMessage } from '../../api/http'
import { listStudentAssignments, submitAssignment } from '../../api/coursework'
import type { FormatState, StudentAssignmentView } from '../../api/coursework'
import { listCourses, listDocuments } from '../../api/documents'
import type { CourseView, DocumentSummary } from '../../api/documents'
import { createConversion, listConversions } from '../../api/format'
import type { ConversionView } from '../../api/format'

type Tab = 'assignments' | 'export' | 'downloads'

const STATUS_META: Record<StudentAssignmentView['status'], { label: string; color: string }> = {
  PENDING: { label: '待提交', color: 'warning' },
  SUBMITTED: { label: '已提交', color: 'processing' },
  GRADED: { label: '已批改', color: 'success' },
  OVERDUE: { label: '逾期', color: 'error' },
}

const FORMAT_META: Record<FormatState, { label: string; color: string }> = {
  NONE: { label: '未校验', color: 'default' },
  PASS: { label: '通过', color: 'green' },
  WARN: { label: '警告', color: 'orange' },
  BLOCK: { label: '阻塞', color: 'red' },
}

const CONV_META: Record<ConversionView['status'], { label: string; color: string }> = {
  QUEUED: { label: '排队', color: 'default' },
  RUNNING: { label: '生成中', color: 'processing' },
  SUCCEEDED: { label: '可下载', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  CANCELED: { label: '已取消', color: 'warning' },
}

/** 后端 ISO 串（+08:00）→「YYYY-MM-DD HH:mm」 */
const dt = (v: string | null) => (v ? v.slice(0, 16).replace('T', ' ') : '-')

/** 课程与作业（作业与交付分区）：我的作业 24 / 提交 25 / 导出文档 22 / 下载中心 23 屏。 */
export default function StudentCourses() {
  const [tab, setTab] = useState<Tab>('assignments')
  // 我的作业（屏 24）：筛选值直接传后端，点击即重新请求
  const [loading, setLoading] = useState(true)
  const [items, setItems] = useState<StudentAssignmentView[]>([])
  const [total, setTotal] = useState(0)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [courseFilter, setCourseFilter] = useState('')
  const [courses, setCourses] = useState<CourseView[]>([])
  const [reloadKey, setReloadKey] = useState(0)
  // 就绪文档：提交弹窗 / 导出表单 / 下载中心共用一份
  const [docs, setDocs] = useState<DocumentSummary[]>([])
  // 提交（屏 25）/ 详情弹窗
  const [submitRow, setSubmitRow] = useState<StudentAssignmentView | null>(null)
  const [detailRow, setDetailRow] = useState<StudentAssignmentView | null>(null)
  const [submitForm] = Form.useForm<{ docId: string }>()
  const [exportForm] = Form.useForm<{ documentId: string; targetFormat: 'PDF' | 'DOCX' }>()
  // 下载中心（屏 23）：listConversions 按文档查询，先选文档再看记录
  const [dlDocId, setDlDocId] = useState<string>()
  const [convs, setConvs] = useState<ConversionView[]>([])
  const [dlLoading, setDlLoading] = useState(false)
  const [dlFilter, setDlFilter] = useState('ALL')

  useEffect(() => {
    listCourses().then(setCourses).catch(() => setCourses([]))
    listDocuments({ status: 'READY', pageSize: 100 })
      .then((r) => setDocs(r.items))
      .catch((e) => message.error(getErrorMessage(e)))
  }, [])

  // ponytail: pageSize=100 一页拉全，作业量超百再接服务端分页参数
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    listStudentAssignments({
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      courseId: courseFilter || undefined,
      pageSize: 100,
    })
      .then((r) => { if (!cancelled) { setItems(r.items); setTotal(r.total) } })
      .catch((e) => !cancelled && message.error(getErrorMessage(e)))
      .finally(() => !cancelled && setLoading(false))
    return () => { cancelled = true }
  }, [statusFilter, courseFilter, reloadKey])

  useEffect(() => {
    if (tab !== 'downloads' || !dlDocId) { setConvs([]); return }
    let cancelled = false
    setDlLoading(true)
    listConversions(dlDocId)
      .then((rs) => !cancelled && setConvs(rs))
      .catch((e) => { if (!cancelled) { setConvs([]); message.error(getErrorMessage(e)) } })
      .finally(() => !cancelled && setDlLoading(false))
    return () => { cancelled = true }
  }, [dlDocId, tab])

  const doSubmit = async () => {
    if (!submitRow) return
    const values = await submitForm.validateFields()
    try {
      const res = await submitAssignment(submitRow.id, values.docId)
      message.success(res.status === 'LATE'
        ? `第 ${res.attemptNo} 次提交成功，已记为迟交`
        : `第 ${res.attemptNo} 次提交成功`)
      setSubmitRow(null)
      submitForm.resetFields()
      setReloadKey((k) => k + 1)
    } catch (e) {
      // 草稿未发布 409 / 未选修 40301 / 文档非本人或未就绪 40401 / 截止禁迟交 40901，如实弹出
      message.error(getErrorMessage(e))
    }
  }

  const doExport = async () => {
    const values = await exportForm.validateFields()
    try {
      await createConversion(values.documentId, values.targetFormat)
      message.success('导出任务已创建，可在下载中心查看进度')
      exportForm.resetFields()
      setDlDocId(values.documentId)
      setTab('downloads')
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const docLabel = (d: DocumentSummary) => `${d.title}（v${d.versionNo ?? '-'}）`

  const assignmentColumns: ColumnsType<StudentAssignmentView> = [
    { title: '课程', dataIndex: 'course', width: 110 },
    { title: '作业名', dataIndex: 'title', width: 200 },
    { title: '要求', dataIndex: 'requirement', ellipsis: true, render: (v: string | null) => v ?? '-' },
    {
      title: '截止时间',
      dataIndex: 'deadline',
      width: 190,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{dt(v)}</span>
          <Typography.Text type={row.status === 'OVERDUE' ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>
            {row.remainText}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '提交状态',
      dataIndex: 'status',
      width: 110,
      render: (v: StudentAssignmentView['status'], row) => (
        <Space direction="vertical" size={2}>
          <Tag color={STATUS_META[v].color}>{STATUS_META[v].label}</Tag>
          {row.status === 'GRADED' && row.score != null && <Tag color="success">{row.score} 分</Tag>}
        </Space>
      ),
    },
    {
      title: '格式校验',
      dataIndex: 'format',
      width: 100,
      render: (_, row) => (
        <Tag color={FORMAT_META[row.format.state].color} title={row.format.note}>
          {FORMAT_META[row.format.state].label}
        </Tag>
      ),
    },
    {
      title: '操作',
      width: 100,
      render: (_, row) =>
        row.status === 'PENDING' || row.status === 'OVERDUE' ? (
          <AppButton size="small" danger={row.status === 'OVERDUE'} onClick={() => { setSubmitRow(row); submitForm.resetFields() }}>
            提交
          </AppButton>
        ) : (
          <AppButton type="link" size="small" onClick={() => setDetailRow(row)}>
            {row.status === 'GRADED' ? '查看反馈' : '查看提交'}
          </AppButton>
        ),
    },
  ]

  const convColumns: ColumnsType<ConversionView> = [
    { title: '产物', key: 'src', ellipsis: true, render: (_, t) => docs.find((d) => d.id === t.documentId)?.title ?? t.documentId },
    { title: '格式', dataIndex: 'targetFormat', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: ConversionView['status']) => <Tag color={CONV_META[v].color}>{CONV_META[v].label}</Tag> },
    { title: '开始时间', dataIndex: 'startedAt', width: 150, render: dt },
    { title: '完成时间', dataIndex: 'finishedAt', width: 150, render: dt },
    {
      title: '操作',
      width: 110,
      render: (_, t) =>
        t.status === 'SUCCEEDED' && t.downloadUrl ? (
          <AppButton type="link" size="small" onClick={() => window.open(t.downloadUrl!, '_blank')}>下载</AppButton>
        ) : t.status === 'FAILED' || t.status === 'CANCELED' ? (
          <AppButton type="link" size="small" onClick={() => setTab('export')}>重新导出</AppButton>
        ) : (
          <Typography.Text type="secondary">生成中…</Typography.Text>
        ),
    },
  ]

  return (
    <DemoPage
      title="课程与作业"
      screens="对应原型：学生 22/23/24/25"
      desc="查看课程作业与提交状态，状态与格式结论由服务端派生；导出走格式转换服务，产物在下载中心按文档查看。"
      live
      extra={
        <Segmented<Tab>
          value={tab}
          onChange={setTab}
          options={[
            { label: `我的作业 ${total}`, value: 'assignments' },
            { label: '导出文档', value: 'export' },
            { label: '下载中心', value: 'downloads' },
          ]}
        />
      }
    >
      {tab === 'assignments' && (
        <DemoCard
          title="课程作业"
          extra={
            <Space wrap>
              <Select
                style={{ width: 150 }}
                value={courseFilter}
                onChange={(v) => setCourseFilter(v)}
                options={[{ value: '', label: '全部课程' }, ...courses.map((c) => ({ value: c.id, label: c.courseName }))]}
              />
              <Segmented
                value={statusFilter}
                onChange={(v) => setStatusFilter(v as string)}
                options={[
                  { label: '全部', value: 'ALL' },
                  ...Object.entries(STATUS_META).map(([value, m]) => ({ value, label: m.label })),
                ]}
              />
            </Space>
          }
        >
          <AppTable<StudentAssignmentView> rowKey="id" size="middle" loading={loading} columns={assignmentColumns} dataSource={items} />
        </DemoCard>
      )}

      {tab === 'export' && (
        <DemoCard title="导出设置" extra={<Typography.Text type="secondary">导出走格式转换服务，记录仅本人可见</Typography.Text>}>
          <Form form={exportForm} layout="vertical" style={{ maxWidth: 520 }} initialValues={{ targetFormat: 'PDF' }}>
            <Form.Item name="documentId" label="选择文档" rules={[requiredRule('请选择要导出的文档')]}>
              <Select
                placeholder="选择要导出的文档"
                showSearch
                optionFilterProp="label"
                options={docs.map((d) => ({ value: d.id, label: docLabel(d) }))}
              />
            </Form.Item>
            <Form.Item name="targetFormat" label="目标格式" rules={[requiredRule('请选择目标格式')]}>
              <Select options={[{ value: 'PDF' }, { value: 'DOCX' }]} />
            </Form.Item>
            <Space>
              <AppButton onClick={doExport}>开始导出</AppButton>
              <AppButton type="default" onClick={() => setTab('downloads')}>下载中心</AppButton>
            </Space>
          </Form>
        </DemoCard>
      )}

      {tab === 'downloads' && (
        <DemoCard
          title="导出记录"
          extra={
            <Space wrap>
              <Select
                style={{ width: 220 }}
                placeholder="选择文档查看导出记录"
                value={dlDocId}
                onChange={(v) => setDlDocId(v)}
                showSearch
                optionFilterProp="label"
                options={docs.map((d) => ({ value: d.id, label: d.title }))}
              />
              <Segmented
                value={dlFilter}
                onChange={(v) => setDlFilter(v as string)}
                options={[
                  { label: '全部', value: 'ALL' },
                  ...Object.entries(CONV_META).map(([value, m]) => ({ value, label: m.label })),
                ]}
              />
            </Space>
          }
        >
          <AppTable<ConversionView>
            rowKey="id"
            size="middle"
            loading={dlLoading}
            columns={convColumns}
            dataSource={convs.filter((c) => dlFilter === 'ALL' || c.status === dlFilter)}
          />
        </DemoCard>
      )}

      {/* 屏 25：提交作业——选已有就绪文档，格式/资格校验由后端在提交时执行，拒绝原因如实弹出 */}
      <AppModal
        title={`提交作业 · ${submitRow?.title ?? ''}`}
        open={!!submitRow}
        okText="确认提交"
        cancelText="取消"
        onOk={doSubmit}
        onCancel={() => setSubmitRow(null)}
        width={560}
      >
        {submitRow && (
          <>
            <Descriptions size="small" column={2} style={{ marginBottom: 12 }}>
              <Descriptions.Item label="课程">{submitRow.course}</Descriptions.Item>
              <Descriptions.Item label="截止时间">{dt(submitRow.deadline)}（{submitRow.remainText}）</Descriptions.Item>
              <Descriptions.Item label="要求" span={2}>{submitRow.requirement ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="提交格式" span={2}>
                {submitRow.submissionFormat === 'BOTH' ? 'DOCX / PDF' : submitRow.submissionFormat}
                {submitRow.requireFormatCheck === 'Y' ? '，提交时执行格式校验' : ''}
              </Descriptions.Item>
            </Descriptions>
            <Form form={submitForm} layout="vertical">
              <Form.Item name="docId" label="选择提交文档" rules={[requiredRule('请选择要提交的文档')]}>
                <Select
                  placeholder="从我的就绪文档中选择当前版本"
                  showSearch
                  optionFilterProp="label"
                  options={docs.map((d) => ({ value: d.id, label: docLabel(d) }))}
                />
              </Form.Item>
            </Form>
          </>
        )}
      </AppModal>

      {/* 查看提交 / 查看反馈 */}
      <AppModal title={detailRow?.status === 'GRADED' ? '批改反馈' : '提交记录'} open={!!detailRow} footer={null} onCancel={() => setDetailRow(null)}>
        {detailRow && (
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="作业">{detailRow.course} · {detailRow.title}</Descriptions.Item>
            <Descriptions.Item label="提交文档">{detailRow.submittedDoc ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="提交时间">{dt(detailRow.submitTime)}</Descriptions.Item>
            <Descriptions.Item label="格式校验">
              <Tag color={FORMAT_META[detailRow.format.state].color}>{FORMAT_META[detailRow.format.state].label}</Tag>
              {detailRow.format.note && <Typography.Text type="secondary">{detailRow.format.note}</Typography.Text>}
            </Descriptions.Item>
            {detailRow.status === 'GRADED' && (
              <>
                <Descriptions.Item label="得分"><Tag color="success">{detailRow.score} 分</Tag></Descriptions.Item>
                <Descriptions.Item label="教师评语">{detailRow.feedback ?? '-'}</Descriptions.Item>
              </>
            )}
          </Descriptions>
        )}
      </AppModal>
    </DemoPage>
  )
}
