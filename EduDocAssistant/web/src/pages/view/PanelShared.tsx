// C-11 共享件：生成任务生命周期（创建→localStorage 断点→2s 轮询→取消），摘要/要点面板复用。
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Progress, Space, Tag, message } from 'antd'
import { AppButton } from '../../components'
import { getErrorMessage } from '../../api/http'
import {
  cancelAiTask,
  clearTaskId,
  getAiTask,
  loadTaskId,
  saveTaskId,
} from '../../api/ai'
import type { AiTaskView } from '../../api/ai'

const ACTIVE = new Set(['QUEUED', 'RUNNING'])
export const POLL_MS = 2000 // §4.2 长任务轮询；ponytail: 换 SSE 见计划排除项

export interface TaskController {
  task: AiTaskView | null
  busy: boolean
  error: string | null
  start: () => Promise<void>
  cancel: () => Promise<void>
}

export function useGenerationTask(
  documentId: string,
  taskType: 'SUMMARY' | 'KEYPOINT',
  create: (id: string) => Promise<{ taskId: string }>,
  onFinished?: (task: AiTaskView) => void,
): TaskController {
  const [task, setTask] = useState<AiTaskView | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const alive = useRef(true)
  const timer = useRef<ReturnType<typeof setTimeout>>()

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
      clearTimeout(timer.current)
    }
  }, [documentId])

  const track = useCallback(
    async (taskId: string) => {
      try {
        const view = await getAiTask(taskId)
        if (!alive.current) return
        setTask(view)
        setError(null)
        if (ACTIVE.has(view.status)) {
          timer.current = setTimeout(() => void track(taskId), POLL_MS)
        } else {
          clearTaskId(documentId, taskType)
          if (view.status === 'SUCCEEDED') onFinished?.(view)
          if (view.status === 'FAILED') setError(view.errorCode ?? '任务失败')
        }
      } catch (e) {
        if (alive.current) setError(getErrorMessage(e))
      }
    },
    [documentId, taskType, onFinished],
  )

  // §4.2 刷新后从任务接口恢复
  useEffect(() => {
    const saved = loadTaskId(documentId, taskType)
    if (saved) void track(saved)
  }, [documentId, taskType, track])

  const start = useCallback(async () => {
    setBusy(true)
    setError(null)
    try {
      const { taskId } = await create(documentId)
      saveTaskId(documentId, taskType, taskId)
      await track(taskId)
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }, [create, documentId, taskType, track])

  const cancel = useCallback(async () => {
    if (!task) return
    try {
      await cancelAiTask(task.taskId)
      message.success('已提交取消')
      await track(task.taskId)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }, [task, track])

  return { task, busy, error, start, cancel }
}

/** 任务状态头：徽标+进度条+取消（阶段与进度回写口径 §3.2.1）；indexing=向量索引未就绪（R3）。 */
export function TaskHeader({ ctrl, label, indexing = false }: {
  ctrl: TaskController
  label: string
  indexing?: boolean
}) {
  const { task, busy, error, start, cancel } = ctrl
  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      {indexing && <Alert type="info" showIcon message="新版索引中，索引完成后可发起生成" />}
      <Space wrap>
        {/* 1106 复审 P2：创建失败也保留生成按钮，支持页面内重试 */}
        <AppButton data-testid={`gen-${label}`} onClick={() => void start()} loading={busy}
          disabled={(!!task && ACTIVE.has(task.status)) || indexing}>
          {task?.status === 'SUCCEEDED' ? `重新生成${label}` : `生成${label}`}
        </AppButton>
        {task && ACTIVE.has(task.status) && (
          <AppButton type="default" danger onClick={() => void cancel()}>
            取消
          </AppButton>
        )}
        {task && (
          <Tag color={task.status === 'SUCCEEDED' ? 'success' : ACTIVE.has(task.status) ? 'processing' : 'error'}>
            {task.status === 'QUEUED' ? '排队中' : task.status === 'RUNNING' ? '处理中'
              : task.status === 'SUCCEEDED' ? '完成' : task.status === 'CANCELED' ? '已取消' : '失败'}
          </Tag>
        )}
      </Space>
      {/* 1106 复审 P3：FAILED 终态只出结构化失败条，避免与通用 error 双重提示 */}
      {error && task?.status !== 'FAILED' && <Alert type="error" showIcon message={error} />}
      {task && ACTIVE.has(task.status) && <Progress percent={task.progress} size="small" />}
      {task?.status === 'FAILED' && (
        <Alert type="error" showIcon message={`处理失败（${task.errorCode ?? error ?? '未知错误'}），可重试或稍后再试`} />
      )}
      {task?.status === 'CANCELED' && <Alert type="warning" showIcon message="任务已取消" />}
    </Space>
  )
}

/** 下载工具：txt 客户端可自产，docx 走后端端点（token 头 → fetch blob，C-11 口径）。 */
export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export function downloadText(text: string, filename: string) {
  downloadBlob(new Blob([text], { type: 'text/plain;charset=utf-8' }), filename)
}
