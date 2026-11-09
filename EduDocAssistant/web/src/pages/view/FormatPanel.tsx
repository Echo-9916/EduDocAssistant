// D-11/D-12：预览页「格式校验」面板——发起→2s 轮询（localStorage 断点恢复）→结论横幅+问题清单
// →勾选可修复项→一键修复确认→成版+自动复审。
// 定位口径（1107 P2-5）：题注/引用类问题的期望/当前即段落文本 → 块内检索绝对偏移走 highlightRange
// 精确高亮；字体/字号类无文本锚点 → 按段落密度近似滚动并明示「近似定位」（段-块精确映射待协作切片）。
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Badge, Progress, Space, Table, Tag, Typography, message } from 'antd'
import { AppButton, AppModal } from '../../components'
import { ApiError, getErrorMessage } from '../../api/http'
import {
  createFormatCheck,
  fixFormatIssues,
  getFormatCheck,
  listFormatIssues,
  loadCheckId,
  parseLocation,
  saveCheckId,
} from '../../api/format'
import type { CheckView, IssueView } from '../../api/format'
import type { PreviewBlock } from '../../api/ai'
import { POLL_MS } from './PanelShared'
import { locateByParagraph, scrollToPageMarker } from './locate'

const ACTIVE = new Set(['QUEUED', 'RUNNING'])

const SEVERITY_META: Record<string, { label: string; color: string }> = {
  BLOCKER: { label: '阻断', color: 'error' },
  WARNING: { label: '警告', color: 'warning' },
  INFO: { label: '提示', color: 'default' },
}

const ISSUE_TYPE_LABEL: Record<string, string> = {
  FONT: '字体', SIZE: '字号', LINE_SPACING: '行距', MARGIN: '页边距/纸型',
  CAPTION: '题注', CODE: '代码块', REFERENCE: '参考文献', HEADER_FOOTER: '页眉页脚',
}

interface Props {
  documentId: string
  documentReady: boolean
  allowed: boolean
  canEdit: boolean
  /** 文本视图全部块（含 text，按 charStart 升序） */
  blocks: PreviewBlock[]
  /** 绝对字符区间高亮（DocumentView.highlightRange） */
  onHighlight: (absStart: number, absEnd: number) => void
}

/** 在块流中检索文本的绝对偏移；未命中返回 null（跨块场景退化为近似滚动） */
export function findAbsRange(blocks: PreviewBlock[], needle: string): { start: number; end: number } | null {
  for (const b of blocks) {
    const at = b.text.indexOf(needle)
    if (at >= 0) {
      return { start: b.charStart + at, end: b.charStart + at + needle.length }
    }
  }
  return null
}

export default function FormatPanel({ documentId, documentReady, allowed, canEdit, blocks, onHighlight }: Props) {
  const [check, setCheck] = useState<CheckView | null>(null)
  const [issues, setIssues] = useState<IssueView[]>([])
  const [selected, setSelected] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [fixOpen, setFixOpen] = useState(false)
  const [fixResult, setFixResult] = useState<string | null>(null)
  const alive = useRef(true)
  const timer = useRef<ReturnType<typeof setTimeout>>()
  const checkIdRef = useRef<string | null>(null)

  useEffect(() => {
    alive.current = true
    return () => { alive.current = false; clearTimeout(timer.current) }
  }, [documentId])

  const loadIssues = useCallback((checkId: string) => {
    listFormatIssues(checkId, { page: 1, pageSize: 200 })
      .then((r) => alive.current && setIssues(r.items))
      .catch((e) => alive.current && setError(getErrorMessage(e)))
  }, [])

  const track = useCallback(async (checkId: string) => {
    checkIdRef.current = checkId
    try {
      const view = await getFormatCheck(checkId)
      if (!alive.current) return
      setCheck(view)
      setError(null)
      if (ACTIVE.has(view.status)) {
        timer.current = setTimeout(() => void track(checkId), POLL_MS)
      } else {
        // 验收 M-07-2：终态不清断点——刷新后仍可恢复上次校验结果（重开会被新发起覆盖）
        saveCheckId(documentId, checkId)
        if (view.status !== 'QUEUED') void loadIssues(checkId)
      }
    } catch (e) {
      // 断点 id 失效只报错不清除：下次发起校验自然覆盖（保持「刷新可恢复」语义简单）
      if (alive.current) setError(getErrorMessage(e))
    }
  }, [documentId, loadIssues])

  // 刷新恢复
  useEffect(() => {
    const saved = loadCheckId(documentId)
    if (saved) void track(saved)
  }, [documentId, track])

  const start = useCallback(async () => {
    setBusy(true)
    setFixResult(null)
    setError(null)
    setIssues([])
    try {
      const view = await createFormatCheck(documentId)
      setCheck(view)
      if (ACTIVE.has(view.status)) {
        saveCheckId(documentId, view.id)
        void track(view.id)
      } else {
        // J1：PDF 等直接 ERROR 行，无事件
        saveCheckId(documentId, view.id)
        void loadIssues(view.id)
      }
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setBusy(false)
    }
  }, [documentId, track, loadIssues])

  const scrollToIssue = (issue: IssueView) => {
    if (blocks.length === 0) {
      message.info('文本视图尚未就绪，无法定位')
      return
    }
    // 题注类：违规文本本身可在块流中检索 → 精确高亮
    const anchor = issue.actualValue && (issue.issueType === 'CAPTION' || issue.issueType === 'REFERENCE')
      ? issue.actualValue
      : null
    const abs = anchor ? findAbsRange(blocks, anchor) : null
    if (abs) {
      onHighlight(abs.start, abs.end)
      return
    }
    const loc = parseLocation(issue.locationRef)
    if (!loc) {
      message.info('该问题为页面级，无正文锚点')
      return
    }
    // 1108 P2-5：段-块映射（含 charStart/charEnd 区间高亮）→ 失败退化跳页标记 → 再失败仅提示
    if (locateByParagraph(blocks, loc, onHighlight)) {
      message.info(`已跳至第 ${loc.paragraphIndex + 1} 段附近并高亮段内区间（近似定位）`)
      return
    }
    if (scrollToPageMarker(loc)) {
      message.info(`段-块映射失败，已跳至估计所在页标记`)
      return
    }
    message.info(`无法定位第 ${loc.paragraphIndex + 1} 段（文档较短或结构差异），请人工浏览`)
  }

  const conclusion = () => {
    if (!check) return null
    if (check.status === 'ERROR') {
      return <Alert type="warning" showIcon message="无法校验" description={check.resultSummary ?? '文档不可校验'} />
    }
    if (check.status === 'PASSED') {
      return <Alert type="success" showIcon message={`校验通过：${check.resultSummary ?? '未发现问题'}`} />
    }
    if (check.status === 'FAILED') {
      return (
        <Alert type="error" showIcon
          message={`校验未通过：阻断 ${check.blockingIssueCount} 项，共 ${check.totalIssueCount} 项`}
          description="存在阻断问题时提交会被课程要求拦截" />
      )
    }
    return (
      <Space>
        <Progress type="circle" size={48} percent={check.status === 'RUNNING' ? 50 : 0}
          format={() => check.status === 'RUNNING' ? '校验中' : '排队'} />
        <Typography.Text>正在按课程模板检查格式，请稍候</Typography.Text>
      </Space>
    )
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Space wrap>
        <AppButton
          disabled={!allowed || !documentReady || ACTIVE.has(check?.status ?? '')}
          loading={busy}
          onClick={() => void start()}>
          {check && !ACTIVE.has(check.status) ? '重新校验' : '开始校验'}
        </AppButton>
        {!allowed && <Typography.Text type="secondary">无格式校验权限</Typography.Text>}
        {allowed && !documentReady && <Typography.Text type="secondary">文档解析就绪后可校验</Typography.Text>}
        {canEdit && check && (check.status === 'PASSED' || check.status === 'FAILED') && selected.length > 0 && (
          <Badge count={selected.length}>
            <AppButton type="default" onClick={() => setFixOpen(true)}>一键修复</AppButton>
          </Badge>
        )}
      </Space>

      {error && <Alert type="error" showIcon message={error} />}
      {fixResult && <Alert type="info" showIcon message={fixResult} />}
      {conclusion()}

      {check && !ACTIVE.has(check.status) && check.status !== 'ERROR' && (
        <Table<IssueView>
          size="small"
          rowKey="id"
          dataSource={issues}
          pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 个问题` }}
          rowSelection={canEdit ? {
            selectedRowKeys: selected,
            onChange: (keys) => setSelected(keys as string[]),
            getCheckboxProps: (r) => ({ disabled: r.autoFixable !== 'Y' || r.fixStatus !== 'OPEN' }),
          } : undefined}
          columns={[
            { title: '类型', dataIndex: 'issueType', width: 110,
              render: (v: string) => ISSUE_TYPE_LABEL[v] ?? v },
            { title: '严重度', dataIndex: 'severity', width: 80,
              render: (v: string) => <Tag color={SEVERITY_META[v]?.color}>{SEVERITY_META[v]?.label ?? v}</Tag> },
            { title: '位置', key: 'loc', width: 130,
              render: (_, r) => {
                const loc = parseLocation(r.locationRef)
                return loc
                  ? <AppButton type="link" size="small"
                      onClick={() => scrollToIssue(r)}>第 {loc.paragraphIndex + 1} 段</AppButton>
                  : '页面级'
              } },
            { title: '期望', dataIndex: 'expectedValue', ellipsis: true },
            { title: '当前', dataIndex: 'actualValue', ellipsis: true },
            { title: '可修复', dataIndex: 'autoFixable', width: 76,
              render: (v: string, r) => v === 'Y'
                ? (r.fixStatus === 'FIXED' ? <Tag>已修复</Tag> : '是')
                : <Typography.Text type="secondary">否</Typography.Text> },
            { title: '修改指引', dataIndex: 'guidance', ellipsis: true,
              render: (v: string | null, r) => v ?? (r.autoFixable === 'N' ? '需人工调整' : '') },
          ]}
        />
      )}

      <FixModal
        open={fixOpen}
        checkId={check?.id ?? ''}
        allIssues={issues}
        selectedIds={selected}
        onClose={() => setFixOpen(false)}
        onFixed={(recheckId, summary) => {
          setFixOpen(false)
          setSelected([])
          setFixResult(summary)
          if (recheckId) {
            saveCheckId(documentId, recheckId)
            void track(recheckId)
          }
        }}
        onConflict={() => {
          // 1107 P2-6：40901 后重查当前校验状态与清单，不停留在旧列表
          setFixOpen(false)
          setSelected([])
          setFixResult('文档已被并发修改，本次修复未生效；已重新拉取校验状态，请重新校验后再修复')
          if (checkIdRef.current) void track(checkIdRef.current)
        }} />
    </Space>
  )
}

/** D-12（1107 P2-6）：修复确认弹窗——完整影响面（选中/未选可修复/不可修复三组）+ 提交 + 冲突回调 */
export function FixModal({ open, checkId, allIssues, selectedIds, onClose, onFixed, onConflict }: {
  open: boolean
  checkId: string
  allIssues: IssueView[]
  selectedIds: string[]
  onClose: () => void
  onFixed: (recheckId: string | null, summary: string) => void
  onConflict: () => void
}) {
  const [busy, setBusy] = useState(false)

  const selectedIssues = allIssues.filter((i) => selectedIds.includes(i.id))
  const fixableUnchecked = allIssues.filter((i) => i.autoFixable === 'Y' && i.fixStatus === 'OPEN'
    && !selectedIds.includes(i.id))
  const notFixable = allIssues.filter((i) => i.autoFixable !== 'Y' || i.fixStatus !== 'OPEN')

  const submit = async () => {
    setBusy(true)
    try {
      const result = await fixFormatIssues(checkId, selectedIds)
      const skippedNote = result.skippedIssueIds.length ? `，${result.skippedIssueIds.length} 项提交时已不可修复` : ''
      onFixed(result.recheckId, result.newVersionId
        ? `已生成新版本 v${result.newVersionNo}，修复 ${result.fixedIssueIds.length} 项${skippedNote}；复审进行中`
        : `所选问题均不可自动修复${skippedNote}`)
    } catch (e) {
      if (e instanceof ApiError && e.code === 40901) {
        onConflict()
      } else {
        message.error(getErrorMessage(e))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AppModal title="一键修复确认" open={open} okText="确认修复"
      confirmLoading={busy} onOk={() => void submit()} onCancel={onClose}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Alert type="info" showIcon message="修复将生成新修订版本并自动重新校验；原件与历史版本保持只读。" />
        <div>
          <Typography.Text strong>本次将修复（{selectedIssues.length}）</Typography.Text>
          <ul style={{ margin: '4px 0', paddingLeft: 20 }}>
            {selectedIssues.map((i) => (
              <li key={i.id}>{ISSUE_TYPE_LABEL[i.issueType] ?? i.issueType}：期望 {i.expectedValue}</li>
            ))}
          </ul>
        </div>
        {fixableUnchecked.length > 0 && (
          <Typography.Text type="secondary">
            另有 {fixableUnchecked.length} 项可修复但未勾选，本次不处理
          </Typography.Text>
        )}
        {notFixable.length > 0 && (
          <div>
            <Typography.Text type="secondary">
              不可自动修复（{notFixable.length}），需按指引人工处理：
            </Typography.Text>
            <ul style={{ margin: '4px 0', paddingLeft: 20 }}>
              {notFixable.slice(0, 5).map((i) => (
                <li key={i.id}>{ISSUE_TYPE_LABEL[i.issueType] ?? i.issueType}：{i.guidance ?? '见修改指引列'}</li>
              ))}
              {notFixable.length > 5 && <li>……共 {notFixable.length} 项</li>}
            </ul>
          </div>
        )}
      </Space>
    </AppModal>
  )
}
