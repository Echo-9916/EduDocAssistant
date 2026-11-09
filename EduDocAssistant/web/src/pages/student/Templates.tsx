// D-10：学生课程模板只读页（原型屏 11）。
// 只读语义：仅「查看要求」「按模板新建」，不出现编辑/发布/版本维护操作（设计 05 明文）。
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Card, Empty, Input, List, Select, Space, message } from 'antd'
import { AppButton, AppModal } from '../../components'
import { getErrorMessage } from '../../api/http'
import {
  RULE_TYPE_LABEL,
  SELECTOR_LABEL,
  currentRules,
  describeExpect,
  listTemplates,
  provisionFromTemplate,
} from '../../api/template'
import type { RuleView, TemplateView } from '../../api/template'

const DOC_TYPES = [
  { value: 'REPORT', label: '课程报告' },
  { value: 'HOMEWORK', label: '作业' },
  { value: 'THESIS', label: '论文' },
  { value: 'COURSEWARE', label: '课件' },
]

export default function StudentTemplates() {
  const navigate = useNavigate()
  const [templates, setTemplates] = useState<TemplateView[]>([])
  const [active, setActive] = useState<TemplateView | null>(null)
  const [rules, setRules] = useState<RuleView[]>([])
  const [rulesError, setRulesError] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [docType, setDocType] = useState('REPORT')

  useEffect(() => {
    listTemplates({ page: 1, pageSize: 100 })
      .then((r) => {
        const published = r.items.filter((t) => t.status === 'P')
        setTemplates(published)
        if (published.length) setActive(published[0])
      })
      .catch((e) => message.error(getErrorMessage(e)))
  }, [])

  useEffect(() => {
    if (!active) {
      setRules([])
      setRulesError(null)
      return
    }
    currentRules(active.id)
      .then((rs) => { setRules(rs); setRulesError(null) })
      .catch((e) => { setRules([]); setRulesError(getErrorMessage(e)) })
  }, [active])

  const submit = async () => {
    if (!active || !title.trim()) {
      message.warning('请填写文档标题')
      return
    }
    try {
      const view = await provisionFromTemplate(active.id, title.trim(), docType)
      message.success('已按模板创建文档，正在解析')
      navigate(`../documents/${view.documentId}/view`, { relative: 'path' })
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  if (!templates.length) {
    return <Empty description="课程尚未发布模板" style={{ marginTop: 80 }} />
  }

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          value={active?.id}
          style={{ width: 320 }}
          onChange={(id) => setActive(templates.find((t) => t.id === id) ?? null)}
          options={templates.map((t) => ({ value: t.id, label: `${t.templateName}（${t.courseName ?? ''}）` }))}
        />
        <AppButton disabled={!active} onClick={() => setCreateOpen(true)}>按模板新建文档</AppButton>
      </Space>

      {rulesError && <Alert type="warning" showIcon message={rulesError} style={{ marginBottom: 12 }} />}

      <Card title={`课程要求——${active?.templateName ?? ''}`}>
        <List
          size="small"
          header={<ListHeader />}
          dataSource={rules}
          locale={{ emptyText: rulesError ? '无法读取规则' : '模板暂未配置规则' }}
          renderItem={(r) => (
            <List.Item key={`${r.ruleType}-${r.targetSelector}-${r.ruleOrder}`}>
              <div style={{ display: 'flex', gap: 12, width: '100%' }}>
                <span style={{ width: 110, fontWeight: 500 }}>{RULE_TYPE_LABEL[r.ruleType]}</span>
                <span style={{ width: 110 }}>{SELECTOR_LABEL[r.targetSelector] ?? r.targetSelector}</span>
                <span style={{ flex: 1 }}>{describeExpect(r)}</span>
                <span>{r.severity === 'BLOCKER' ? '阻断项' : '提醒项'}</span>
              </div>
            </List.Item>
          )}
        />
      </Card>

      <AppModal title="按模板新建文档" open={createOpen} okText="创建"
        onOk={() => void submit()} onCancel={() => setCreateOpen(false)}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select value={docType} style={{ width: '100%' }} options={DOC_TYPES} onChange={setDocType} />
          <Input placeholder="文档标题（1-200 字）" value={title} maxLength={200}
            onChange={(e) => setTitle(e.target.value)} />
          <Alert type="info" showIcon
            message={`将按「${active?.templateName ?? ''}」生成大纲与页面设置，创建后自动进入解析。`} />
        </Space>
      </AppModal>
    </div>
  )
}

function ListHeader() {
  return (
    <Space style={{ width: '100%' }}>
      <span style={{ width: 110, fontWeight: 500 }}>格式项</span>
      <span style={{ width: 110 }}>作用对象</span>
      <span style={{ fontWeight: 500 }}>要求</span>
    </Space>
  )
}

