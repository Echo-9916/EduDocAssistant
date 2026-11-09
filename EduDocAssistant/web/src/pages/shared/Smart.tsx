import { useEffect, useState } from 'react'
import {
  Alert,
  Checkbox,
  Col,
  Collapse,
  Empty,
  Input,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import { useNavigate } from 'react-router-dom'
import { AppButton } from '../../components'
import DemoPage, { DemoCard } from '../DemoPage'
import { rolePath } from '../../routes'
import type { RoleCodeValue } from '../../api/types'
import {
  aiDoc,
  clearQa,
  listKeypoints,
  listPolish,
  listQa,
  askQa,
  polishStatusLabel,
  setPolishStatus,
  suggestedQuestions,
  summarize,
  summaryFocusLabel,
  summaryLengthLabel,
  unanswerableQuestion,
} from '../../mock/domain/aiDemo'
import type {
  Keypoint,
  PolishPara,
  QaTurn,
  SummaryBlock,
  SummaryFocus,
  SummaryLength,
} from '../../mock/domain/aiDemo'

/** 智能处理：摘要 / 要点提取 / 文档问答 / 文本润色（学生 06/07/08/09/10、教师 05/06/07/08 屏）。 */
export default function Smart({ role }: { role: RoleCodeValue }) {
  const navigate = useNavigate()
  const isTeacher = role === 'TEACHER'
  return (
    <DemoPage
      title="智能处理"
      screens="对应原型：学生 06/07/08/09/10、教师 05/06/07/08"
      desc={
        isTeacher
          ? '围绕教学文档生成可引用摘要、教学检查项与问答，保留原文出处供批注和评分标准核对使用。'
          : '围绕当前文档快速理解长文、勾选要点、连续追问并润色表达，所有结果均可定位原文。'
      }
      extra={
        <Alert
          type="info"
          showIcon
          message={
            <Space wrap>
              <span>已联调文档可在「我的文档 → 预览」内使用真实摘要/问答。</span>
              <AppButton size="small" onClick={() => navigate(`${rolePath[role]}/documents`)}>
                前往我的文档
              </AppButton>
            </Space>
          }
        />
      }
    >
      <div style={{ marginBottom: 12, padding: '8px 12px', background: '#f6f8fa', borderRadius: 6 }}>
        <Space wrap size={4}>
          <Tag color="blue">当前演示文档</Tag>
          <Typography.Text strong>《{aiDoc.title}》</Typography.Text>
          <Typography.Text type="secondary">
            《{aiDoc.course}》 · 已解析 {aiDoc.pages} 页 · 当前版本 {aiDoc.version} ·{' '}
            {isTeacher ? '教学批注 2 条' : '结果均可定位原文'}
          </Typography.Text>
        </Space>
      </div>
      <Tabs
        defaultActiveKey="summary"
        items={[
          { key: 'summary', label: '摘要', children: <SummaryTab isTeacher={isTeacher} /> },
          { key: 'keypoints', label: '要点提取', children: <KeypointsTab isTeacher={isTeacher} /> },
          { key: 'qa', label: '文档问答', children: <QaTab isTeacher={isTeacher} /> },
          { key: 'polish', label: '文本润色', children: <PolishTab isTeacher={isTeacher} /> },
        ]}
      />
    </DemoPage>
  )
}

/* ---------- 摘要 ---------- */

function SummaryTab({ isTeacher }: { isTeacher: boolean }) {
  const [length, setLength] = useState<SummaryLength>('standard')
  const [focus, setFocus] = useState<SummaryFocus>('structure')
  const [blocks, setBlocks] = useState<SummaryBlock[] | null>(null)

  useEffect(() => {
    let alive = true
    summarize(length, focus).then((b) => {
      if (alive) setBlocks(b)
    })
    return () => {
      alive = false
    }
  }, [length, focus])

  const sentenceCount = blocks?.reduce((n, b) => n + b.sentences.length, 0) ?? 0
  const plainText = (blocks ?? []).map((b) => `${b.section}\n${b.sentences.join('\n')}`).join('\n\n')

  return (
    <>
      <DemoCard
        title={isTeacher ? '摘要设置（教学口径）' : '摘要设置'}
        extra={
          <Space>
            <AppButton
              size="small"
              type="default"
              disabled={!blocks}
              onClick={() => {
                navigator.clipboard?.writeText(plainText).catch(() => undefined)
                message.success('摘要已复制到剪贴板')
              }}
            >
              复制摘要
            </AppButton>
            <AppButton
              size="small"
              disabled={!blocks}
              onClick={() =>
                message.success(
                  isTeacher ? '摘要已导出，可用于备课与评分参考（演示）' : '摘要已导出为 Markdown，可在下载中心查看（演示）',
                )
              }
            >
              导出摘要
            </AppButton>
          </Space>
        }
      >
        <Space wrap size="large">
          <Space>
            <Typography.Text type="secondary">摘要长度</Typography.Text>
            <Segmented
              value={length}
              onChange={(v) => setLength(v as SummaryLength)}
              options={(Object.keys(summaryLengthLabel) as SummaryLength[]).map((k) => ({
                label: summaryLengthLabel[k],
                value: k,
              }))}
            />
          </Space>
          <Space>
            <Typography.Text type="secondary">关注重点</Typography.Text>
            <Select<SummaryFocus>
              style={{ width: 150 }}
              value={focus}
              onChange={setFocus}
              options={(Object.keys(summaryFocusLabel) as SummaryFocus[]).map((k) => ({
                label: summaryFocusLabel[k],
                value: k,
              }))}
            />
          </Space>
        </Space>
      </DemoCard>
      <DemoCard
        title="分组摘要"
        extra={
          blocks && (
            <Space>
              <Tag color="green">可引用</Tag>
              <Typography.Text type="secondary">
                {summaryLengthLabel[length]} · {summaryFocusLabel[focus]} · 已生成 {blocks.length} 组 · 共{' '}
                {sentenceCount} 句 · 每句可定位原文
              </Typography.Text>
            </Space>
          )
        }
      >
        {!blocks ? (
          <Spin style={{ display: 'block', padding: 32, textAlign: 'center' }} />
        ) : (
          <Collapse
            defaultActiveKey={blocks.map((b) => b.id)}
            items={blocks.map((b) => ({
              key: b.id,
              label: (
                <Space>
                  <Typography.Text strong>{b.section}</Typography.Text>
                  <Tag color="blue">{b.page}</Tag>
                </Space>
              ),
              children: (
                <>
                  {b.sentences.map((s) => (
                    <div key={s} style={{ marginBottom: 6 }}>
                      <Typography.Text>{s}</Typography.Text>
                      <AppButton
                        type="link"
                        size="small"
                        onClick={() => message.info(`已定位到「${b.section}」原文（${b.page}）`)}
                      >
                        定位原文
                      </AppButton>
                    </div>
                  ))}
                  <div style={{ background: '#f6f8fa', borderRadius: 6, padding: '8px 12px', marginTop: 8 }}>
                    <Typography.Text type="secondary">原文节选：{b.source}</Typography.Text>
                  </div>
                </>
              ),
            }))}
          />
        )}
      </DemoCard>
    </>
  )
}

/* ---------- 要点提取 ---------- */

function KeypointsTab({ isTeacher }: { isTeacher: boolean }) {
  const [rows, setRows] = useState<Keypoint[]>([])
  const [loading, setLoading] = useState(true)
  const [checked, setChecked] = useState<string[]>([])

  useEffect(() => {
    listKeypoints().then((r) => {
      setRows(r)
      setChecked(r.slice(0, 3).map((k) => k.id))
      setLoading(false)
    })
  }, [])

  const n = checked.length
  const exportSelected = () => {
    if (n < 2) {
      message.warning(`已选 ${n} 条，不足导出下限 2 条；可继续勾选，当前选择已保留。`)
      return
    }
    message.success(isTeacher ? `已导出选中 ${n} 条教学检查项（演示）` : `已导出选中 ${n} 条要点（演示）`)
  }

  return (
    <DemoCard
      title={isTeacher ? '教学检查项（评分标准核对）' : '要点清单（按原文顺序）'}
      extra={
        <Space>
          <AppButton
            size="small"
            type="default"
            disabled={n === 0}
            onClick={() => {
              const text = rows
                .filter((k) => checked.includes(k.id))
                .map((k) => `${k.title}：${k.desc}（${k.page}）`)
                .join('\n')
              navigator.clipboard?.writeText(text).catch(() => undefined)
              message.success(`已复制 ${n} 条要点`)
            }}
          >
            复制已选
          </AppButton>
          <AppButton size="small" onClick={exportSelected}>
            导出选中 {n} 条
          </AppButton>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <div style={{ marginBottom: 8 }}>
          <Checkbox
            checked={rows.length > 0 && n === rows.length}
            indeterminate={n > 0 && n < rows.length}
            onChange={(e) => setChecked(e.target.checked ? rows.map((k) => k.id) : [])}
          >
            全选（{n}/{rows.length}）
          </Checkbox>
        </div>
        <Checkbox.Group value={checked} onChange={(v) => setChecked(v as string[])} style={{ display: 'block' }}>
          {rows.map((k) => (
            <div key={k.id} style={{ padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
              <Checkbox value={k.id}>
                <Space wrap size={4}>
                  <Typography.Text strong>{k.title}</Typography.Text>
                  <Typography.Text type="secondary">{k.desc}</Typography.Text>
                  <Tag>{k.section}</Tag>
                  <Tag color="blue">{k.page}</Tag>
                </Space>
              </Checkbox>
            </div>
          ))}
        </Checkbox.Group>
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
          {isTeacher
            ? '勾选要点可转化为批改检查项，导出清单用于备课、批注和评分标准核对；不会修改原文档。'
            : '导出只包含已选要点，并保留来源章节与页码，不会修改原文档。'}
        </Typography.Text>
      </Spin>
    </DemoCard>
  )
}

/* ---------- 文档问答（含无依据拒答，需求 5.2.3） ---------- */

function QaTab({ isTeacher }: { isTeacher: boolean }) {
  const [turns, setTurns] = useState<QaTurn[] | null>(null)
  const [asking, setAsking] = useState(false)
  const [input, setInput] = useState('')

  useEffect(() => {
    listQa().then(setTurns)
  }, [])

  const ask = async (q: string) => {
    if (!q.trim() || asking) return
    setAsking(true)
    setTurns(await askQa(q))
    setAsking(false)
    setInput('')
  }

  return (
    <>
      <DemoCard
        title="当前对话"
        extra={
          <Space>
            <AppButton
              size="small"
              type="default"
              onClick={async () => {
                await clearQa()
                setTurns([])
              }}
            >
              清除对话
            </AppButton>
            <AppButton size="small" type="default" onClick={() => message.success('问答记录已导出（演示）')}>
              导出对话
            </AppButton>
          </Space>
        }
      >
        {turns === null ? (
          <Spin style={{ display: 'block', padding: 32, textAlign: 'center' }} />
        ) : turns.length === 0 ? (
          <Empty description="当前对话为空，输入问题或点击下方推荐问题。" />
        ) : (
          turns.map((t) =>
            t.role === 'user' ? (
              <div key={t.id} style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 10 }}>
                <div style={{ background: '#e6f4ff', borderRadius: 8, padding: '8px 12px', maxWidth: '75%' }}>{t.text}</div>
              </div>
            ) : t.refusal ? (
              <Alert
                key={t.id}
                type="warning"
                showIcon
                style={{ marginBottom: 10 }}
                message="当前文档无法回答这个问题（无依据拒答）"
                description={
                  <>
                    <div>{t.text}</div>
                    {t.tip && <Typography.Text type="secondary">{t.tip}</Typography.Text>}
                  </>
                }
              />
            ) : (
              <div key={t.id} style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: 10 }}>
                <div style={{ background: '#f6f8fa', borderRadius: 8, padding: '8px 12px', maxWidth: '75%' }}>
                  <div>{t.text}</div>
                  <Space wrap size={4} style={{ marginTop: 6 }}>
                    {t.citations?.map((c) => (
                      <Tag key={c} color="blue" style={{ cursor: 'pointer' }} onClick={() => message.info(`已定位到 ${c}`)}>
                        引用：{c}
                      </Tag>
                    ))}
                  </Space>
                </div>
              </div>
            ),
          )
        )}
        <Space.Compact style={{ width: '100%', marginTop: 12 }}>
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onPressEnter={() => ask(input)}
            placeholder={isTeacher ? '围绕教学文档提问，回答仅引用文档内有依据的章节…' : '继续追问当前文档…'}
          />
          <AppButton loading={asking} onClick={() => ask(input)}>
            发送
          </AppButton>
        </Space.Compact>
        <div style={{ marginTop: 12 }}>
          <Space wrap size={4}>
            <Typography.Text type="secondary">推荐问题：</Typography.Text>
            {suggestedQuestions.map((q) => (
              <AppButton key={q} type="link" size="small" onClick={() => ask(q)}>
                {q}
              </AppButton>
            ))}
          </Space>
          <div style={{ marginTop: 6 }}>
            <AppButton size="small" danger onClick={() => ask(unanswerableQuestion)}>
              无依据示例问题：{unanswerableQuestion}
            </AppButton>
          </div>
        </div>
      </DemoCard>
      <Alert
        type="info"
        showIcon
        message="回答边界：仅回答文档内有依据的内容，引用可回跳原文；无依据问题会明确拒答并说明原因，不编造答案。"
      />
    </>
  )
}

/* ---------- 文本润色 ---------- */

const polishStatusColor = { pending: 'default', adopted: 'green', kept: 'orange' } as const

function PolishTab({ isTeacher }: { isTeacher: boolean }) {
  const [rows, setRows] = useState<PolishPara[] | null>(null)

  useEffect(() => {
    listPolish().then(setRows)
  }, [])

  const act = async (id: string, status: 'adopted' | 'kept') => {
    setRows(await setPolishStatus(id, status))
    message.success(
      status === 'adopted'
        ? isTeacher
          ? '已采纳润色结果，可加入教学批注并与评分标准核对（演示）'
          : '已采纳润色结果；应用后将生成新版本 v7，原文版本保持不变'
        : '已保留原文，本段不采纳润色建议',
    )
  }

  if (!rows) return <Spin style={{ display: 'block', padding: 32, textAlign: 'center' }} />

  const adopted = rows.filter((p) => p.status === 'adopted').length
  return (
    <DemoCard
      title={isTeacher ? '逐段对照（保留原意与引用关系）' : '逐段对照（原文 / 润色后）'}
      extra={
        <Typography.Text type="secondary">
          已采纳 {adopted}/{rows.length} 段 · 采纳结果已写回演示数据
        </Typography.Text>
      }
    >
      {rows.map((p) => (
        <div key={p.id} style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 12, marginBottom: 12 }}>
          <Space wrap style={{ marginBottom: 8 }}>
            <Tag color="blue">{p.section}</Tag>
            <Tag color={polishStatusColor[p.status]}>{polishStatusLabel[p.status]}</Tag>
            <Typography.Text type="secondary">{p.note}</Typography.Text>
          </Space>
          <Row gutter={12}>
            <Col span={12}>
              <Typography.Text type="secondary">原文</Typography.Text>
              <div style={{ background: '#f6f8fa', borderRadius: 6, padding: '8px 12px', marginTop: 4 }}>{p.original}</div>
            </Col>
            <Col span={12}>
              <Typography.Text type="secondary">润色后</Typography.Text>
              <div
                style={{ background: '#f6ffed', border: '1px solid #bbf7d0', borderRadius: 6, padding: '8px 12px', marginTop: 4 }}
              >
                {p.polished}
              </div>
            </Col>
          </Row>
          <Space style={{ marginTop: 10 }}>
            <AppButton size="small" disabled={p.status === 'adopted'} onClick={() => act(p.id, 'adopted')}>
              采纳
            </AppButton>
            <AppButton size="small" type="default" disabled={p.status === 'kept'} onClick={() => act(p.id, 'kept')}>
              保留原文
            </AppButton>
          </Space>
        </div>
      ))}
      <Typography.Text type="secondary">
        {isTeacher
          ? '全部采纳后可生成新版本并同步课程组；历史版本不被覆盖，学生端批注保持可追溯。'
          : '采纳不覆盖历史版本：应用后自动生成新版本，可在「协作与版本 → 版本记录」中对比或恢复。'}
      </Typography.Text>
    </DemoCard>
  )
}
