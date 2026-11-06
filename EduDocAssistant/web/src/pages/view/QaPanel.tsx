// C-12：问答面板——会话复用/三态消息流/引用 chip 跳原文/同步禁言/历史回看。
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Input, List, Select, Space, Tag, Typography, message } from 'antd'
import { AppButton } from '../../components'
import { getErrorMessage } from '../../api/http'
import { askQa, createQaSession, listQaMessages, listQaSessions } from '../../api/ai'
import type { PreviewBlock, QaCitation, QaMessageView, QaSessionView } from '../../api/ai'
import type { HighlightFn } from './types'

const MAX_QUESTION = 1000

interface Props {
  documentId: string
  blocks: PreviewBlock[]
  onHighlight: HighlightFn
  allowed: boolean
  indexing?: boolean
}

/** 引用 chip 文案：页码+片段前 40 字。 */
export function citationLabel(c: QaCitation, index: number): string {
  const page = c.pageNo != null ? `第${c.pageNo}页 ` : ''
  const quote = (c.quoteText ?? '').replace(/\s+/g, ' ').slice(0, 40)
  return `[${index + 1}] ${page}${quote}`
}

export default function QaPanel({ documentId, blocks, onHighlight, allowed, indexing = false }: Props) {
  const [sessions, setSessions] = useState<QaSessionView[]>([])
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [messages, setMessages] = useState<QaMessageView[]>([])
  const [question, setQuestion] = useState('')
  const [sending, setSending] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)

  const byChunk = useMemo(() => new Map(blocks.map((b) => [b.chunkId, b])), [blocks])

  const loadMessages = useCallback((sid: string) => {
    listQaMessages(sid).then(setMessages).catch((e) => message.error(getErrorMessage(e)))
  }, [])

  useEffect(() => {
    if (!allowed) return
    listQaSessions(documentId)
      .then((list) => {
        setSessions(list)
        const active = list.find((s) => s.status === 'A')
        if (active) {
          setSessionId(active.sessionId)
          loadMessages(active.sessionId)
        }
      })
      .catch(() => setSessions([]))
  }, [documentId, allowed, loadMessages])

  const scrollBottom = () => {
    // 1106 P1-3：jsdom 元素无 scrollTo，可选调用避免未处理异常挂掉测试门禁
    requestAnimationFrame(() => listRef.current?.scrollTo?.({ top: listRef.current.scrollHeight }))
  }

  const current = sessions.find((s) => s.sessionId === sessionId)
  const archived = current?.status === 'C'

  const newSession = async () => {
    try {
      const created = await createQaSession(documentId)
      setSessionId(created.sessionId)
      setSessions((prev) => [created, ...prev])
      setMessages([])
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const send = async () => {
    const q = question.trim()
    if (!q || q.length > MAX_QUESTION || sending || archived || indexing) return
    setSending(true)
    let sid: string | null = sessionId
    try {
      if (!sid) {
        const created = await createQaSession(documentId)
        sid = created.sessionId
        setSessionId(sid)
        setSessions((prev) => [created, ...prev])
      }
      const result = await askQa(sid, q)
      setQuestion('')
      // 拒答/答案都以服务端落库后的消息流为准（sequence/状态单源）
      await loadMessages(sid)
      scrollBottom()
      if (result.status !== 'READY') message.info('该问题未在文档中找到依据')
    } catch (e) {
      message.error(getErrorMessage(e))
      // 1106 P2-2：用本次会话（含刚新建的）回载服务端已落库的 FAILED 消息
      if (sid) loadMessages(sid)
    } finally {
      setSending(false)
    }
  }

  if (!allowed) return <Alert type="info" showIcon message="无 AI_QA 权限，问答功能不可用" />

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      <Select
        style={{ width: '100%' }}
        placeholder="历史会话回看"
        allowClear
        value={sessionId ?? undefined}
        options={sessions.map((s) => ({
          value: s.sessionId,
          label: `${s.title ?? s.sessionId.slice(0, 8)}（${s.status === 'A' ? '进行中' : '已归档'}）`,
        }))}
        onChange={(v?: string) => {
          if (v) {
            setSessionId(v)
            loadMessages(v)
          }
        }}
      />
      {archived && (
        <Alert
          type="warning"
          showIcon
          message="归档会话只读，可新建会话后继续提问"
          action={<AppButton size="small" onClick={() => void newSession()}>新建会话</AppButton>}
        />
      )}
      <div ref={listRef} style={{ maxHeight: 380, overflowY: 'auto', paddingRight: 4 }}>
        <List
          dataSource={messages}
          renderItem={(m) => (
            <List.Item style={{ justifyContent: m.role === 'USER' ? 'flex-end' : 'flex-start' }}>
              <div style={{
                maxWidth: '85%',
                padding: '6px 10px',
                borderRadius: 8,
                background: m.role === 'USER' ? '#e6f4ff' : m.status === 'REFUSED' ? '#fffbe6' : '#f5f5f5',
                border: m.status === 'REFUSED' || m.status === 'FAILED' ? '1px dashed #d48806' : '1px solid transparent',
              }}>
                <Typography.Text>{m.contentText}</Typography.Text>
                {m.status === 'FAILED' && <Tag color="error">超时</Tag>}
                {m.citations?.length > 0 && (
                  <div style={{ marginTop: 4 }}>
                    {m.citations.map((c, i) => (
                      <a key={i} style={{ marginRight: 8, fontSize: 12 }}
                        onClick={() => {
                          const block = byChunk.get(c.chunkId)
                          if (!block) return message.warning('引用块不在当前版本')
                          onHighlight(c.charStart ?? block.charStart, c.charEnd ?? block.charEnd)
                        }}>
                        {citationLabel(c, i)}
                      </a>
                    ))}
                  </div>
                )}
              </div>
            </List.Item>
          )}
        />
      </div>
      <Space.Compact style={{ width: '100%' }}>
        <Input.TextArea
          value={question}
          maxLength={MAX_QUESTION}
          autoSize={{ minRows: 1, maxRows: 3 }}
          placeholder={indexing ? '新版索引中，完成后可提问' : '就本文档提问（连续追问会自动携带最近对话）'}
          disabled={sending || archived || indexing}
          onChange={(e) => setQuestion(e.target.value)}
          onPressEnter={(e) => {
            if (!e.shiftKey) {
              e.preventDefault()
              void send()
            }
          }}
        />
        <AppButton data-testid="qa-send" loading={sending}
          disabled={!question.trim() || sending || archived || indexing} onClick={() => void send()}>
          提问
        </AppButton>
      </Space.Compact>
    </Space>
  )
}
