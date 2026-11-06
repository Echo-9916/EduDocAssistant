// C-13：润色面板——勾选块→对照→采纳/部分采纳/放弃；采纳成功提示新版本+可撤销一次；重润。
import { useMemo, useState } from 'react'
import { Alert, Input, List, Space, Tag, Typography, message } from 'antd'
import { AppButton } from '../../components'
import { getErrorMessage } from '../../api/http'
import { polishDecision, polishParagraphs } from '../../api/ai'
import type { PolishRow, PreviewBlock } from '../../api/ai'
import type { HighlightFn } from './types'

const MAX_TOTAL = 5000 // §4.3 与后端 PolishServiceImpl.MAX_TOTAL_CHARS 同值文案

interface Props {
  documentId: string
  selectedBlocks: PreviewBlock[]
  onHighlight: HighlightFn
  allowed: boolean
  /** 采纳/撤销成版后父层刷新预览与文档状态 */
  onChanged: () => void
}

/** 选中块文本合计（预检口径：与后端一致按 strip 后长度）。 */
export function totalChars(blocks: PreviewBlock[]): number {
  return blocks.reduce((sum, b) => sum + b.text.trim().length, 0)
}

/** 表内 decision 无 REVERT 值（撤销后回写 PENDING，台账裁决）；UI 本地态额外允许 REVERT 标签。 */
type RowState = Omit<PolishRow, 'decision'> & { decision: PolishRow['decision'] | 'REVERT' }

export default function PolishPanel({ documentId, selectedBlocks, onHighlight, allowed, onChanged }: Props) {
  const [rows, setRows] = useState<RowState[]>([])
  const [acceptedTexts, setAcceptedTexts] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  /** 本次会话内最近一次成功采纳（撤销仅允许一次） */
  const [lastApplied, setLastApplied] = useState<{ polishId: string; versionId: string } | null>(null)

  const total = useMemo(() => totalChars(selectedBlocks), [selectedBlocks])
  const overLimit = total > MAX_TOTAL

  const runPolish = async () => {
    setBusy(true)
    setError(null)
    try {
      const result = await polishParagraphs(
        documentId,
        selectedBlocks.map((b) => ({ chunkId: b.chunkId, text: b.text.trim() })),
      )
      setRows(result)
      setAcceptedTexts(Object.fromEntries(result.map((r) => [r.polishId, r.polishedText])))
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const decide = async (
    row: RowState,
    decision: 'ACCEPT' | 'PARTIAL' | 'REJECT' | 'REVERT',
    acceptedText?: string,
  ) => {
    const source = selectedBlocks.find((b) => b.text.trim() === row.sourceText)
    try {
      const applied = await polishDecision(row.polishId, {
        decision,
        acceptedText,
        charStart: source ? source.charStart : undefined,
      })
      if (decision === 'ACCEPT' || decision === 'PARTIAL') {
        setLastApplied({ polishId: row.polishId, versionId: applied?.versionId ?? '' })
        message.success(`已生成新版本 v${applied?.versionNo ?? '+'}，可撤销一次`)
        onChanged()
      } else if (decision === 'REVERT') {
        setLastApplied(null)
        message.success('已撤销采纳，回到上一版本')
        onChanged()
      }
      setRows((prev) => prev.map((r) => (r.polishId === row.polishId ? { ...r, decision } : r)))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  if (!allowed) return <Alert type="info" showIcon message="无 AI_POLISH 权限，润色功能不可用" />

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Alert
        type={overLimit ? 'error' : 'info'}
        showIcon
        message={`已选 ${selectedBlocks.length} 段，合计 ${total} 字（上限 ${MAX_TOTAL}，超出请分批）`}
      />
      <AppButton data-testid="run-polish" loading={busy}
        disabled={selectedBlocks.length === 0 || overLimit} onClick={() => void runPolish()}>
        润色选中段落
      </AppButton>
      {error && <Alert type="error" showIcon message={error} />}
      {rows.length > 0 && (
        <List
          size="small"
          bordered
          dataSource={rows}
          renderItem={(row) => (
            <List.Item key={row.polishId} style={{ display: 'block' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                <div>
                  <Tag>原文</Tag>
                  <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}
                    ellipsis={{ rows: 4, expandable: true }}>
                    {row.sourceText}
                  </Typography.Paragraph>
                </div>
                <div>
                  <Tag color="blue">润色</Tag>
                  {row.decision === 'PENDING' ? (
                    <Input.TextArea
                      autoSize={{ minRows: 2, maxRows: 6 }}
                      value={acceptedTexts[row.polishId] ?? ''}
                      onChange={(e) => setAcceptedTexts((m) => ({ ...m, [row.polishId]: e.target.value }))}
                    />
                  ) : (
                    <Typography.Text type="secondary">{row.decision === 'REVERT' ? '已撤销' : row.decision}</Typography.Text>
                  )}
                </div>
              </div>
              {row.decision === 'PENDING' && (
                <Space style={{ marginTop: 8 }}>
                  <AppButton size="small" data-testid={`accept-${row.polishId}`}
                    onClick={() => void decide(row, 'ACCEPT')}>采纳</AppButton>
                  <AppButton size="small" type="default"
                    disabled={(acceptedTexts[row.polishId] ?? '').trim() === row.sourceText.trim()
                      || !(acceptedTexts[row.polishId] ?? '').trim()}
                    onClick={() => void decide(row, 'PARTIAL', acceptedTexts[row.polishId].trim())}>
                    部分采纳
                  </AppButton>
                  <AppButton size="small" type="default" data-testid={`reject-${row.polishId}`}
                    onClick={() => void decide(row, 'REJECT')}>放弃</AppButton>
                  <AppButton size="small" type="link"
                    onClick={() => {
                      const b = selectedBlocks.find((x) => x.text.trim() === row.sourceText)
                      if (b) onHighlight(b.charStart, b.charEnd)
                    }}>
                    查看原文位置
                  </AppButton>
                </Space>
              )}
              {(row.decision === 'ACCEPT' || row.decision === 'PARTIAL')
                && lastApplied?.polishId === row.polishId && (
                <Space style={{ marginTop: 8 }}>
                  <AppButton size="small" type="default" danger data-testid={`revert-${row.polishId}`}
                    onClick={() => void decide(row, 'REVERT')}>撤销本次采纳</AppButton>
                </Space>
              )}
            </List.Item>
          )}
        />
      )}
      {rows.length > 0 && rows.every((r) => r.decision !== 'PENDING') && (
        <AppButton type="link" onClick={() => setRows([])}>重新润色其他段落</AppButton>
      )}
    </Space>
  )
}
