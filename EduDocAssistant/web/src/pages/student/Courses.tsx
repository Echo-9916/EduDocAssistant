import { useEffect, useMemo, useState } from 'react'
import { Alert, Descriptions, Form, Input, Segmented, Select, Space, Switch, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { AppButton, AppModal, AppTable, requiredRule } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import {
  createExport,
  downloadStatusColor,
  downloadStatusLabel,
  listDownloads,
  listStudentAssignments,
  listStudentDocs,
  submissionStatusColor,
  submissionStatusLabel,
  submitAssignment,
} from '../../mock/domain/course'
import type { DownloadTask, StudentAssignment, StudentDoc } from '../../mock/domain/course'

type Tab = 'assignments' | 'export' | 'downloads'

const COURSE_TEMPLATE_OPTIONS = ['Web开发课程模板 v3', '数据库实验报告模板 v2', '操作系统课程报告模板 v1', '编译原理课程模板 v2']

/** 课程与作业（作业与交付分区）：我的作业 24 / 提交 25 / 导出文档 22 / 下载中心 23 屏。 */
export default function StudentCourses() {
  const [tab, setTab] = useState<Tab>('assignments')
  const [loading, setLoading] = useState(true)
  const [assignments, setAssignments] = useState<StudentAssignment[]>([])
  const [docs, setDocs] = useState<StudentDoc[]>([])
  const [downloads, setDownloads] = useState<DownloadTask[]>([])

  // 我的作业筛选
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [courseFilter, setCourseFilter] = useState('ALL')
  // 下载中心筛选
  const [dlFilter, setDlFilter] = useState('ALL')

  // 提交弹窗（屏 25）
  const [submitRow, setSubmitRow] = useState<StudentAssignment | null>(null)
  const [detailRow, setDetailRow] = useState<StudentAssignment | null>(null)
  const [submitForm] = Form.useForm<{ docId: string; note?: string }>()
  const [exportForm] = Form.useForm()
  const selectedDocId = Form.useWatch('docId', submitForm)
  const selectedDoc = docs.find((d) => d.id === selectedDocId)

  useEffect(() => {
    setLoading(true)
    Promise.all([listStudentAssignments(), listStudentDocs(), listDownloads()]).then(([a, d, dl]) => {
      setAssignments(a)
      setDocs(d)
      setDownloads(dl)
      setLoading(false)
    })
  }, [])

  const courses = useMemo(() => ['ALL', ...Array.from(new Set(assignments.map((a) => a.course)))], [assignments])
  const assignmentData = useMemo(
    () =>
      assignments
        .filter((a) => statusFilter === 'ALL' || a.status === statusFilter)
        .filter((a) => courseFilter === 'ALL' || a.course === courseFilter),
    [assignments, statusFilter, courseFilter],
  )
  const downloadData = useMemo(
    () => downloads.filter((d) => dlFilter === 'ALL' || d.status === dlFilter),
    [downloads, dlFilter],
  )

  const doSubmit = async () => {
    if (!submitRow) return
    const values = await submitForm.validateFields()
    const doc = docs.find((d) => d.id === values.docId)
    setAssignments(await submitAssignment(submitRow.id, doc?.name ?? ''))
    message.success('提交成功，系统已记录当前版本、提交时间与格式校验结果')
    setSubmitRow(null)
    submitForm.resetFields()
  }

  const doExport = async () => {
    const values = await exportForm.validateFields()
    setDownloads(await createExport(values))
    message.success('导出任务已创建，可在下载中心查看进度')
    exportForm.resetFields()
    setTab('downloads')
  }

  const assignmentColumns: ColumnsType<StudentAssignment> = [
    { title: '课程', dataIndex: 'course', width: 110 },
    { title: '作业名', dataIndex: 'title', width: 200 },
    { title: '要求', dataIndex: 'requirement', ellipsis: true },
    {
      title: '截止时间',
      dataIndex: 'deadline',
      width: 190,
      render: (v: string, row) => (
        <Space direction="vertical" size={0}>
          <span>{v}</span>
          <Typography.Text type={row.status === 'OVERDUE' ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>
            {row.remain}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '提交状态',
      dataIndex: 'status',
      width: 110,
      render: (v: StudentAssignment['status'], row) => (
        <Space direction="vertical" size={2}>
          <Tag color={submissionStatusColor[v]}>{submissionStatusLabel[v]}</Tag>
          {row.status === 'GRADED' && <Tag color="success">{row.score} 分</Tag>}
        </Space>
      ),
    },
    {
      title: '格式校验',
      dataIndex: 'format',
      width: 100,
      render: (v: string, row) => <Tag color={row.formatTone}>{v}</Tag>,
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

  const downloadColumns: ColumnsType<DownloadTask> = [
    { title: '产物', dataIndex: 'name' },
    { title: '格式', dataIndex: 'format', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '大小', dataIndex: 'size', width: 90 },
    { title: '创建时间', dataIndex: 'createTime', width: 150 },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: DownloadTask['status']) => <Tag color={downloadStatusColor[v]}>{downloadStatusLabel[v]}</Tag> },
    { title: '有效期', dataIndex: 'expire', width: 130 },
    {
      title: '操作',
      width: 110,
      render: (_, row) =>
        row.status === 'READY' ? (
          <AppButton type="link" size="small" onClick={() => message.success(`已开始下载（演示）：${row.name}`)}>下载</AppButton>
        ) : row.status === 'EXPIRED' ? (
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
      desc="查看课程作业与提交状态，提交前执行格式预检；导出结果统一进入下载中心，记录保留 7 天。"
      extra={
        <Segmented<Tab>
          value={tab}
          onChange={setTab}
          options={[
            { label: `我的作业 ${assignments.length}`, value: 'assignments' },
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
                style={{ width: 130 }}
                value={courseFilter}
                onChange={setCourseFilter}
                options={courses.map((c) => ({ value: c, label: c === 'ALL' ? '全部课程' : c }))}
              />
              <Segmented
                value={statusFilter}
                onChange={(v) => setStatusFilter(v as string)}
                options={[
                  { label: '全部', value: 'ALL' },
                  ...Object.entries(submissionStatusLabel).map(([value, label]) => ({ value, label })),
                ]}
              />
            </Space>
          }
        >
          <AppTable<StudentAssignment> rowKey="id" size="middle" loading={loading} columns={assignmentColumns} dataSource={assignmentData} />
        </DemoCard>
      )}

      {tab === 'export' && (
        <DemoCard title="导出设置" extra={<Typography.Text type="secondary">导出不影响当前文档，记录仅本人可见</Typography.Text>}>
          <Form form={exportForm} layout="vertical" style={{ maxWidth: 520 }} initialValues={{ format: 'DOCX', useTemplate: true, keepComments: false }}>
            <Form.Item name="docName" label="选择文档" rules={[requiredRule('请选择要导出的文档')]}>
              <Select
                placeholder="选择要导出的文档"
                showSearch
                options={docs.map((d) => ({ value: d.name, label: `${d.name}（${d.version} · ${d.formatNote}）` }))}
              />
            </Form.Item>
            <Form.Item name="format" label="目标格式" rules={[requiredRule('请选择目标格式')]}>
              <Select options={[{ value: 'DOCX' }, { value: 'PDF' }]} />
            </Form.Item>
            <Form.Item name="useTemplate" label="套用课程模板" valuePropName="checked" extra="按课程模板重排章节样式后导出">
              <Switch />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(prev, cur) => prev.useTemplate !== cur.useTemplate}>
              {({ getFieldValue }) =>
                getFieldValue('useTemplate') && (
                  <Form.Item name="template" label="课程模板" initialValue={COURSE_TEMPLATE_OPTIONS[0]}>
                    <Select options={COURSE_TEMPLATE_OPTIONS.map((t) => ({ value: t, label: t }))} />
                  </Form.Item>
                )
              }
            </Form.Item>
            <Form.Item name="keepComments" label="保留批注" valuePropName="checked" extra="输出文件中保留教师批注与修订记录">
              <Switch />
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
            <Segmented
              value={dlFilter}
              onChange={(v) => setDlFilter(v as string)}
              options={[
                { label: '全部', value: 'ALL' },
                ...Object.entries(downloadStatusLabel).map(([value, label]) => ({ value, label })),
              ]}
            />
          }
        >
          <AppTable<DownloadTask> rowKey="id" size="middle" loading={loading} columns={downloadColumns} dataSource={downloadData} />
        </DemoCard>
      )}

      {/* 屏 25：提交作业——选已有文档 + 格式预检清单 + 确认提交 */}
      <AppModal
        title={`提交作业 · ${submitRow?.title ?? ''}`}
        open={!!submitRow}
        okText="确认提交"
        cancelText="取消"
        onOk={doSubmit}
        onCancel={() => setSubmitRow(null)}
        okButtonProps={{ disabled: selectedDoc?.formatState === 'BLOCK' }}
        width={560}
      >
        {submitRow && (
          <>
            <Descriptions size="small" column={2} style={{ marginBottom: 12 }}>
              <Descriptions.Item label="课程">{submitRow.course}</Descriptions.Item>
              <Descriptions.Item label="截止时间">{submitRow.deadline}</Descriptions.Item>
              <Descriptions.Item label="要求" span={2}>{submitRow.requirement}</Descriptions.Item>
            </Descriptions>
            <Form form={submitForm} layout="vertical">
              <Form.Item name="docId" label="选择提交文档" rules={[requiredRule('请选择要提交的文档')]}>
                <Select
                  placeholder="从我的文档中选择当前版本"
                  options={docs.map((d) => ({ value: d.id, label: `${d.name}（${d.version} · 最近修改 ${d.updateTime}）` }))}
                />
              </Form.Item>
              <Form.Item name="note" label="提交说明（可选）">
                <Input.TextArea rows={2} placeholder="向教师补充说明本次提交的重点" />
              </Form.Item>
            </Form>
            <Typography.Text strong>格式预检清单</Typography.Text>
            <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
              <Space>
                <Tag color={selectedDoc ? 'success' : 'default'}>{selectedDoc ? '通过' : '未选择'}</Tag>
                <span>文档已选择：{selectedDoc ? `${selectedDoc.name} · ${selectedDoc.version}` : '请先选择文档'}</span>
              </Space>
              <Space>
                <Tag color={selectedDoc ? (selectedDoc.formatState === 'PASS' ? 'success' : selectedDoc.formatState === 'WARN' ? 'warning' : 'error') : 'default'}>
                  {selectedDoc ? (selectedDoc.formatState === 'PASS' ? '通过' : selectedDoc.formatState === 'WARN' ? '1 项警告' : '阻塞') : '待检'}
                </Tag>
                <span>格式规范：{selectedDoc?.formatNote ?? '选择文档后自动预检'}</span>
              </Space>
              <Space>
                <Tag>可选</Tag>
                <span>提交说明：向教师补充说明本次提交的重点</span>
              </Space>
            </Space>
            {selectedDoc?.formatState === 'BLOCK' && (
              <Alert type="error" showIcon style={{ marginTop: 12 }} message="存在阻塞格式问题，请先在「格式与模板」完成一键修复后再提交。" />
            )}
            {selectedDoc?.formatState === 'WARN' && (
              <Alert type="warning" showIcon style={{ marginTop: 12 }} message="存在非阻塞警告，可继续提交，教师端会看到格式校验结论。" />
            )}
          </>
        )}
      </AppModal>

      {/* 查看提交 / 查看反馈 */}
      <AppModal title={detailRow?.status === 'GRADED' ? '批改反馈' : '提交记录'} open={!!detailRow} footer={null} onCancel={() => setDetailRow(null)}>
        {detailRow && (
          <Descriptions size="small" column={1}>
            <Descriptions.Item label="作业">{detailRow.course} · {detailRow.title}</Descriptions.Item>
            <Descriptions.Item label="提交文档">{detailRow.submittedDoc}</Descriptions.Item>
            <Descriptions.Item label="提交时间">{detailRow.submitTime}</Descriptions.Item>
            <Descriptions.Item label="格式校验"><Tag color={detailRow.formatTone}>{detailRow.format}</Tag></Descriptions.Item>
            {detailRow.status === 'GRADED' && (
              <>
                <Descriptions.Item label="得分"><Tag color="success">{detailRow.score} 分</Tag></Descriptions.Item>
                <Descriptions.Item label="教师评语">{detailRow.feedback}</Descriptions.Item>
              </>
            )}
          </Descriptions>
        )}
      </AppModal>
    </DemoPage>
  )
}
