// B-11：上传弹窗。流程 = createUploadSession → sha256 → STS 直传(断点续传) → complete。
// 多文件串行、各自进度；失败保留输入不回空白页（详细设计 §4.2）。
import { useState } from 'react'
import { Input, Progress, Select, Space, Upload, message } from 'antd'
import { InboxOutlined } from '@ant-design/icons'
import { AppButton, AppModal } from '../components'
import { getErrorMessage } from '../api/http'
import { completeUpload, createUploadSession } from '../api/documents'
import type { CourseView } from '../api/documents'
import { DOC_TYPE_LABEL } from './Documents'
import { sha256Hex, uploadToOss, validateUploadFile } from '../upload/ossUpload'
import type { RoleCodeValue } from '../api/types'

interface FileItem {
  file: File
  percent: number
  status: 'wait' | 'uploading' | 'done' | 'error'
  error?: string
}

interface Props {
  open: boolean
  role: RoleCodeValue
  courses: CourseView[]
  onClose: () => void
  onUploaded: () => void
}

export default function DocumentUploadModal({ open, role, courses, onClose, onUploaded }: Props) {
  const [items, setItems] = useState<FileItem[]>([])
  const [title, setTitle] = useState('')
  const [courseId, setCourseId] = useState<string | undefined>()
  const [docType, setDocType] = useState<string | undefined>()
  const [running, setRunning] = useState(false)

  const isTeacher = role === 'TEACHER'

  const addFiles = (files: File[]) => {
    const accepted: FileItem[] = []
    for (const file of files) {
      const reason = validateUploadFile(file)
      if (reason) {
        message.error(reason)
      } else {
        accepted.push({ file, percent: 0, status: 'wait' })
      }
    }
    if (accepted.length) {
      setItems((prev) => [...prev, ...accepted])
      // 单文件时标题默认取文件名（去扩展名），可改
      setTitle((t) => t || accepted[0].file.name.replace(/\.[^.]+$/, ''))
    }
  }

  const patch = (index: number, part: Partial<FileItem>) =>
    setItems((prev) => prev.map((it, i) => (i === index ? { ...it, ...part } : it)))

  const submit = async () => {
    if (!items.length) {
      message.warning('请先选择文件')
      return
    }
    if (isTeacher && !courseId) {
      message.warning('教师上传须绑定课程')
      return
    }
    if (!docType) {
      message.warning('请选择文档类型')
      return
    }
    setRunning(true)
    let anyDone = false
    for (let i = 0; i < items.length; i++) {
      const { file } = items[i]
      // 多文件时标题逐文件取自身文件名，避免撞 uk_document_1（owner+课程+标题）
      const fileTitle = items.length === 1 && title.trim() ? title.trim() : file.name.replace(/\.[^.]+$/, '')
      try {
        patch(i, { status: 'uploading', percent: 0, error: undefined })
        const session = await createUploadSession({
          courseId: courseId ?? null,
          title: fileTitle,
          fileName: file.name,
          size: file.size,
          mimeType: file.type,
          docType,
        })
        const contentHash = await sha256Hex(file)
        await uploadToOss(session, file, (percent) => patch(i, { percent }))
        await completeUpload(session.sessionId, { contentHash })
        patch(i, { status: 'done', percent: 100 })
        anyDone = true
      } catch (e) {
        patch(i, { status: 'error', error: getErrorMessage(e) })
      }
    }
    setRunning(false)
    if (anyDone) onUploaded()
  }

  return (
    <AppModal
      open={open}
      title="上传文档"
      width={560}
      onCancel={onClose}
      footer={[
        <AppButton key="close" type="default" onClick={onClose} disabled={running}>
          关闭
        </AppButton>,
        <AppButton key="submit" loading={running} onClick={submit}>
          开始上传
        </AppButton>,
      ]}
    >
      <Space direction="vertical" style={{ width: '100%' }} size={12}>
        <Upload.Dragger
          multiple
          showUploadList={false}
          beforeUpload={(f) => {
            // antd 对每个文件各调一次 beforeUpload，只收当前文件避免重复添加
            addFiles([f as unknown as File])
            return false
          }}
          accept=".doc,.docx,.pdf"
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">拖放文件到这里，或从本地选择</p>
          <p className="ant-upload-hint">仅支持 Word(.doc/.docx) 与 PDF，单文件不超过 20MB，可多选</p>
        </Upload.Dragger>

        <Space wrap>
          <Input
            placeholder="文档标题"
            value={title}
            maxLength={200}
            onChange={(e) => setTitle(e.target.value)}
            style={{ width: 240 }}
            disabled={items.length > 1}
          />
          <Select
            placeholder={isTeacher ? '选择课程（必选）' : '选择课程（可选）'}
            allowClear={!isTeacher}
            style={{ width: 200 }}
            value={courseId}
            onChange={setCourseId}
            options={courses.map((c) => ({ label: c.courseName, value: c.id }))}
          />
          <Select
            placeholder="文档类型"
            style={{ width: 140 }}
            value={docType}
            onChange={setDocType}
            options={Object.entries(DOC_TYPE_LABEL).map(([value, label]) => ({ value, label }))}
          />
        </Space>
        {/* 类型必填说明：SRS“默认按文件自动识别”不可实现（R8），默认留空由用户选择 */}

        {items.map((it, i) => (
          <div key={`${it.file.name}-${i}`}>
            <Space>
              <span>{it.file.name}</span>
              {it.status === 'done' && <span style={{ color: '#52c41a' }}>完成</span>}
              {it.status === 'error' && <span style={{ color: '#ff4d4f' }}>{it.error}</span>}
            </Space>
            {(it.status === 'uploading' || it.status === 'done') && (
              <Progress percent={it.percent} size="small" status={it.status === 'done' ? 'success' : 'active'} />
            )}
          </div>
        ))}
      </Space>
    </AppModal>
  )
}
