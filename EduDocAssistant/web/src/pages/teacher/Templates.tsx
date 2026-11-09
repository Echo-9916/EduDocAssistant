// D-09：教师课程模板库（原型屏 09 列表 / 10 编辑（规则分组+学生端效果示意） / 11 发布确认）。
// 前端校验镜像后端 RuleConfig 不变量（保存闸），服务端仍是最终裁判；
// 历史版本发布后规则只读回显（UT-13），编辑面仅对未发布版本开放。
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { Alert, Badge, Collapse, Dropdown, Input, Popconfirm, Popover, Select, Space, Table, Tag, Typography, Upload, message } from 'antd'
import { DeleteOutlined, EditOutlined } from '@ant-design/icons'
import { AppButton, AppModal } from '../../components'
import { getErrorMessage } from '../../api/http'
import { listCourses } from '../../api/documents'
import type { CourseView } from '../../api/documents'
import {
    RULE_TYPE_LABEL,
    SELECTOR_LABEL,
    changeTemplateStatus,
    createTemplate,
    createVersion,
    deleteTemplate,
    describeExpect,
    listRules,
    listTemplates,
    listVersions,
    forkTemplateVersion,
    previewTemplateRules,
    publishVersion,
    replaceRules,
} from '../../api/template'
import type { RuleView, TemplateView, TemplateVersionView } from '../../api/template'

export const RULE_TYPES = ['FONT', 'SIZE', 'LINE_SPACING', 'MARGIN', 'CAPTION', 'CODE', 'REFERENCE', 'HEADER_FOOTER'] as const

/** selector 白名单（镜像后端 TemplateVersionServiceImpl.SELECTORS） */
export const SELECTORS_BY_TYPE: Record<string, string[]> = {
    FONT: ['HEADING_1', 'HEADING_2', 'HEADING_3', 'BODY'],
    SIZE: ['HEADING_1', 'HEADING_2', 'HEADING_3', 'BODY'],
    LINE_SPACING: ['HEADING_1', 'HEADING_2', 'HEADING_3', 'BODY'],
    MARGIN: ['PAGE'],
    HEADER_FOOTER: ['HEADER', 'FOOTER'],
    CAPTION: ['FIGURE_CAPTION', 'TABLE_CAPTION'],
    CODE: ['CODE_BLOCK'],
    REFERENCE: ['REFERENCE'],
}

/** R1 修复面：仅这四类可标 auto_fixable=Y */
export const FIXABLE_TYPES = ['FONT', 'SIZE', 'LINE_SPACING', 'MARGIN']

/** 1107 P2-3：规则按 rule_type 分组（屏 10「按格式项分组」契约） */
export function groupRules(rules: RuleView[]): { type: RuleView['ruleType']; items: RuleView[] }[] {
    return RULE_TYPES
        .map((type) => ({ type, items: rules.filter((r) => r.ruleType === type) }))
        .filter((g) => g.items.length > 0)
}

/** 浏览器验收 M-03：错误信息面向教师口语化（类型/对象用中文标签；后端 RuleConfig 仍是最终裁判） */
const MARGIN_KEY_LABEL: Record<string, string> = {
    top: '上边距', bottom: '下边距', left: '左边距', right: '右边距', width: '页宽', height: '页高',
}

/** 保存前镜像校验；返回错误列表，空=可提交 */
export function validateRules(rules: RuleView[]): string[] {
    const errors: string[] = []
    for (const r of rules) {
        const tag = `${RULE_TYPE_LABEL[r.ruleType] ?? r.ruleType}（${SELECTOR_LABEL[r.targetSelector] ?? r.targetSelector}）`
        if (!(SELECTORS_BY_TYPE[r.ruleType] ?? []).includes(r.targetSelector)) {
            errors.push(`${tag}：作用对象与格式类型不匹配`)
            continue
        }
        if (r.autoFixable === 'Y' && !FIXABLE_TYPES.includes(r.ruleType)) {
            errors.push(`${tag}：该格式不支持自动修复，请取消勾选“可自动修复”`)
        }
        let config: Record<string, unknown>
        try {
            config = JSON.parse(r.ruleConfigJson)
        } catch {
            errors.push(`${tag}：规则配置格式有误（不是有效 JSON），点“改配置”修正`)
            continue
        }
        const root = config as { expect?: Record<string, unknown>; tolerance?: Record<string, unknown>; fix?: Record<string, unknown> }
        const expect = root.expect ?? {}
        const fix = root.fix ?? {}
        const num = (v: unknown) => typeof v === 'number' && v > 0
        switch (r.ruleType) {
            case 'FONT':
                if (!expect.family && !expect.eastAsia) errors.push(`${tag}：请至少填写西文字体或中文字体之一`)
                break
            case 'SIZE':
                if (!num(expect.pt) || (expect.pt as number) > 100) errors.push(`${tag}：字号需为大于 0 且不超过 100 的磅值（如 12）`)
                break
            case 'LINE_SPACING': {
                const fixed = expect.lineRule === 'exact' || expect.lineRule === 'atLeast'
                if (!num(expect.multiplier) && !fixed) {
                    errors.push(`${tag}：请填行距倍数（如 1.5）或固定磅值（lineRule 选 exact/atLeast 并配 pt）`)
                } else if (fixed && !num(expect.pt)) {
                    errors.push(`${tag}：固定行距必须带磅值 pt（如 23）`)
                }
                break
            }
            case 'MARGIN': {
                const keys = ['top', 'bottom', 'left', 'right', 'width', 'height']
                for (const k of keys) {
                    if (expect[k] !== undefined && !num(expect[k])) errors.push(`${tag}：${MARGIN_KEY_LABEL[k]}需为正数毫米值`)
                }
                if (!keys.some((k) => num(expect[k]))) errors.push(`${tag}：请至少填写一项页边距或纸张尺寸`)
                break
            }
            case 'CAPTION': {
                const pattern = typeof expect.pattern === 'string' && expect.pattern.trim() ? expect.pattern : null
                if (!pattern && expect.perChapter !== true) {
                    errors.push(`${tag}：请填写编号格式（正则）或开启“按章编号”`)
                }
                if (pattern) {
                    try { new RegExp(pattern) } catch { errors.push(`${tag}：编号格式不是有效正则`) }
                }
                break
            }
            case 'CODE':
                if (typeof expect.style !== 'string' || !expect.style) errors.push(`${tag}：请填写代码样式名称`)
                break
            case 'REFERENCE':
                if (typeof expect.standard !== 'string' || !expect.standard) errors.push(`${tag}：请填写参考文献标准（如 GB/T 7714）`)
                break
            case 'HEADER_FOOTER':
                if (expect.present !== true) errors.push(`${tag}：请开启“必须存在”要求`)
                break
            default:
                break
        }
        if (r.autoFixable === 'Y') {
            const fixKeys: Record<string, string[]> = {
                FONT: ['family', 'eastAsia'], SIZE: ['pt'], LINE_SPACING: ['multiplier', 'lineRule'],
                MARGIN: ['top', 'bottom', 'left', 'right', 'width', 'height'],
            }
            const keys = fixKeys[r.ruleType] ?? []
            const okFix = keys.some((k) => (typeof expect[k] === 'string' || k === 'lineRule')
                ? Boolean(fix[k]) : num(fix[k]))
            if (!okFix) errors.push(`${tag}：开启自动修复时，修复值不能为空`)
        }
    }
    return errors
}

// ==================== 规则 JSON 编辑弹层（验收反馈：编辑图标 + 美观 JSON 展示） ====================

function prettyJson(raw: string): string {
    try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
}

const JSON_TOKEN = /("(?:\\.|[^"\\])*")(\s*:)|("(?:\\.|[^"\\])*")|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|\b(true|false|null)\b|([{}[\],])/g

/** 轻量语法着色：键=蓝、字符串值=绿、数字=橙、布尔/null=紫、标点=灰（无第三方依赖）。 */
function JsonTokens({ text }: { text: string }) {
    const nodes: ReactNode[] = []
    let last = 0
    let m: RegExpExecArray | null
    JSON_TOKEN.lastIndex = 0
    while ((m = JSON_TOKEN.exec(text)) !== null) {
        if (m.index > last) nodes.push(<span key={last} style={{ color: '#333' }}>{text.slice(last, m.index)}</span>)
        if (m[1]) {
            nodes.push(<span key={m.index} style={{ color: '#1677ff', fontWeight: 500 }}>{m[1]}</span>)
            nodes.push(<span key={m.index + 1} style={{ color: '#999' }}>{m[2]}</span>)
        } else if (m[3]) nodes.push(<span key={m.index} style={{ color: '#389e0d' }}>{m[3]}</span>)
        else if (m[4]) nodes.push(<span key={m.index} style={{ color: '#d46b08' }}>{m[4]}</span>)
        else if (m[5]) nodes.push(<span key={m.index} style={{ color: '#722ed1' }}>{m[5]}</span>)
        else nodes.push(<span key={m.index} style={{ color: '#999' }}>{m[6]}</span>)
        last = m.index + m[0].length
    }
    if (last < text.length) nodes.push(<span key={last} style={{ color: '#333' }}>{text.slice(last)}</span>)
    return <>{nodes}</>
}

function RuleJsonEditor({ rule, onCommit }: { rule: RuleView; onCommit: (json: string) => void }) {
    const [editing, setEditing] = useState(false)
    const text = prettyJson(rule.ruleConfigJson)
    if (editing) {
        return (
            <Input.TextArea
                data-testid="rule-json" autoFocus defaultValue={text}
                autoSize={{ minRows: 5, maxRows: 16 }}
                style={{ width: 420, fontFamily: 'monospace', fontSize: 12, lineHeight: 1.7 }}
                onBlur={(ev) => {
                    const raw = ev.target.value
                    let compact = raw
                    try { compact = JSON.stringify(JSON.parse(raw)) } catch { /* 原文交回，validateRules 负责红字拦截 */ }
                    onCommit(compact)
                    setEditing(false)
                }} />
        )
    }
    return (
        <div style={{ width: 420 }}>
            <pre data-testid="rule-json-view"
                style={{ margin: 0, padding: '10px 14px', background: '#fafafa', border: '1px solid #f0f0f0', borderRadius: 8, fontFamily: 'monospace', fontSize: 12, lineHeight: 1.7, maxHeight: 340, overflow: 'auto' }}>
                <JsonTokens text={text} />
            </pre>
            <Space style={{ marginTop: 8 }}>
                <AppButton size="small" onClick={() => setEditing(true)}>编辑 JSON</AppButton>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>失焦即生效</Typography.Text>
            </Space>
        </div>
    )
}

const STATUS_META: Record<string, { label: string; color: string }> = {
    D: { label: '草稿', color: 'default' },
    P: { label: '已发布', color: 'success' },
    A: { label: '已停用', color: 'warning' },
}

const VALIDATION_META: Record<string, { label: string; color: string }> = {
    PENDING: { label: '待解析/待编辑', color: 'default' },
    VALID: { label: '可发布', color: 'success' },
    INVALID: { label: '解析失败', color: 'error' },
}

/** 验收 M-01-4：按族给可保存的默认配置（修复面四族默认带 fix；其余族不可自动修复） */
const RULE_DEFAULTS: Record<string, { selector: string; severity: string; fixable: string; config: string; order: number }> = {
    FONT: { selector: 'HEADING_1', severity: 'WARNING', fixable: 'Y', config: '{"expect":{"eastAsia":"黑体"},"fix":{"eastAsia":"黑体"}}', order: 10 },
    SIZE: { selector: 'HEADING_1', severity: 'WARNING', fixable: 'Y', config: '{"expect":{"pt":14},"fix":{"pt":14}}', order: 11 },
    LINE_SPACING: { selector: 'HEADING_1', severity: 'WARNING', fixable: 'Y', config: '{"expect":{"multiplier":1.5},"fix":{"multiplier":1.5}}', order: 12 },
    MARGIN: { selector: 'PAGE', severity: 'BLOCKER', fixable: 'Y', config: '{"expect":{"top":25,"bottom":25,"left":30,"right":25,"unit":"mm"},"fix":{"top":25,"bottom":25,"left":30,"right":25}}', order: 13 },
    CAPTION: { selector: 'FIGURE_CAPTION', severity: 'WARNING', fixable: 'N', config: '{"expect":{"perChapter":true,"pattern":"^图\\\\s*\\\\d+\\\\s*[-－]\\\\s*\\\\d+"}}', order: 20 },
    CODE: { selector: 'CODE_BLOCK', severity: 'WARNING', fixable: 'N', config: '{"expect":{"style":"代码","keepIndent":true}}', order: 30 },
    REFERENCE: { selector: 'REFERENCE', severity: 'WARNING', fixable: 'N', config: '{"expect":{"standard":"GB/T 7714"}}', order: 40 },
    HEADER_FOOTER: { selector: 'FOOTER', severity: 'WARNING', fixable: 'N', config: '{"expect":{"present":true}}', order: 14 },
}

function newRuleOfType(type: string): RuleView {
    const d = RULE_DEFAULTS[type]
    return {
        id: `local-${type}-${Date.now()}`, ruleType: type as RuleView['ruleType'],
        targetSelector: d.selector, severity: d.severity as RuleView['severity'],
        autoFixable: d.fixable as RuleView['autoFixable'],
        ruleConfigJson: d.config, ruleOrder: d.order,
    }
}

export default function Templates() {
    const [courses, setCourses] = useState<CourseView[]>([])
    const [courseFilter, setCourseFilter] = useState<string | undefined>()
    const [items, setItems] = useState<TemplateView[]>([])
    const [total, setTotal] = useState(0)
    const [page, setPage] = useState(1)
    const [loading, setLoading] = useState(false)
    const [reloadKey, setReloadKey] = useState(0)

    const [createOpen, setCreateOpen] = useState(false)
    const [draft, setDraft] = useState({ courseId: '', templateName: '', description: '' })

    const [editing, setEditing] = useState<TemplateView | null>(null)
    const [versions, setVersions] = useState<TemplateVersionView[]>([])
    const [activeVersionId, setActiveVersionId] = useState<string | null>(null)
    const [rules, setRules] = useState<RuleView[]>([])
    const [ruleErrors, setRuleErrors] = useState<string[]>([])
    // 1107 P2-3：发布确认弹窗（屏 11）
    const [publishOpen, setPublishOpen] = useState(false)
    // 验收决议①：上传→预览草稿→确认才落版本（pending 期间规则表编辑的是未落库草稿）
    const [pending, setPending] = useState<{ file: File; drafts: RuleView[] } | null>(null)
    // 验收反馈：已发布版本→「基于此版本编辑另存新版本」（forkDraft=基版本，rules 为可编辑副本）
    const [forkDraft, setForkDraft] = useState<TemplateVersionView | null>(null)

    const reload = useCallback(() => setReloadKey((k) => k + 1), [])

    useEffect(() => {
        listCourses().then(setCourses).catch(() => setCourses([]))
    }, [])

    useEffect(() => {
        let cancelled = false
        setLoading(true)
        listTemplates({ courseId: courseFilter, page, pageSize: 20 })
            .then((r) => !cancelled && (setItems(r.items), setTotal(r.total)))
            .catch((e) => !cancelled && message.error(getErrorMessage(e)))
            .finally(() => !cancelled && setLoading(false))
        return () => { cancelled = true }
    }, [courseFilter, page, reloadKey])

    const openEditor = useCallback(async (tpl: TemplateView) => {
        setEditing(tpl)
        setRuleErrors([])
        setPending(null)
        setForkDraft(null)
        try {
            const vs = await listVersions(tpl.id)
            setVersions(vs)
            const editableV = vs.find((v) => !v.publishedAt) ?? vs.find((v) => v.id === tpl.currentVersionId)
            setActiveVersionId(editableV?.id ?? null)
        } catch (e) {
            message.error(getErrorMessage(e))
            setVersions([])
            setActiveVersionId(null)
        }
    }, [])

    useEffect(() => {
        if (!editing || !activeVersionId) {
            setRules([])
            return
        }
        listRules(editing.id, activeVersionId)
            .then(setRules)
            .catch((e) => { setRules([]); message.error(getErrorMessage(e)) })
    }, [editing, activeVersionId])

    const activeVersion = useMemo(
        () => versions.find((v) => v.id === activeVersionId) ?? null,
        [versions, activeVersionId],
    )
    const editable = !!activeVersion && !activeVersion.publishedAt
    const inDraftMode = editable || !!pending || !!forkDraft

    const enterFork = () => {
        if (!activeVersion) return
        setForkDraft(activeVersion)
        setRules(activeRulesSnapshot())
        setRuleErrors([])
    }
    /** 当前展示规则的可编辑副本（fork 进入时取已发布版本规则快照） */
    const activeRulesSnapshot = () => rules.map((r, i) => ({ ...r, id: `fork-${i}-${Date.now()}` }))

    const submitFork = async () => {
        if (!editing || !forkDraft) return
        const errors = validateRules(rules)
        setRuleErrors(errors)
        if (errors.length) {
            message.error('规则校验未通过，请修正或退出编辑')
            return
        }
        try {
            const v = await forkTemplateVersion(editing.id, {
                baseVersionId: forkDraft.id,
                rules: rules.map(({ id, ...rest }) => rest),
            })
            message.success(`已另存为新版本 v${v.versionNo}（未发布，不影响 v${forkDraft.versionNo}）`)
            setForkDraft(null)
            setVersions(await listVersions(editing.id))
            setActiveVersionId(v.id)
        } catch (e) {
            message.error(getErrorMessage(e))
        }
    }

    const exitFork = () => {
        setForkDraft(null)
        if (editing && activeVersionId) {
            listRules(editing.id, activeVersionId).then(setRules).catch(() => setRules([]))
        }
    }

    const patchRule = (index: number, patch: Partial<RuleView>) => {
        setRules((prev) => prev.map((r, i) => {
            if (i !== index) return r
            const next = { ...r, ...patch }
            const allowed = SELECTORS_BY_TYPE[next.ruleType] ?? []
            if (!allowed.includes(next.targetSelector)) next.targetSelector = allowed[0] ?? next.targetSelector
            if (next.autoFixable === 'Y' && !FIXABLE_TYPES.includes(next.ruleType)) next.autoFixable = 'N'
            return next
        }))
    }

    const saveRules = async () => {
        if (!editing || !activeVersionId || !editable) return
        const errors = validateRules(rules)
        setRuleErrors(errors)
        if (errors.length) {
            message.error('规则校验未通过，请修正后再保存')
            return
        }
        try {
            await replaceRules(editing.id, activeVersionId, rules.map(({ id, ...rest }) => rest))
            message.success('规则已保存，发布前学生端不可见')
            setVersions(await listVersions(editing.id))
        } catch (e) {
            message.error(getErrorMessage(e))
        }
    }

    /** 决议①：确认预览草稿→createVersion 落版本；手工改过草稿则再 replaceRules 覆盖解析结果 */
    const confirmPending = async () => {
        if (!editing || !pending) return
        const errors = validateRules(rules)
        setRuleErrors(errors)
        if (errors.length) {
            message.error('规则校验未通过，请修正或取消预览')
            return
        }
        try {
            const v = await createVersion(editing.id, pending.file)
            const edited = JSON.stringify(rules) !== JSON.stringify(pending.drafts)
            if (edited) {
                await replaceRules(editing.id, v.id, rules.map(({ id, ...rest }) => rest))
            }
            message.success(`新版本 v${v.versionNo} 已生成${edited ? '（含手工调整）' : ''}`)
            setPending(null)
            setVersions(await listVersions(editing.id))
            setActiveVersionId(v.id)
        } catch (e) {
            message.error(getErrorMessage(e))
        }
    }

    const cancelPending = () => {
        setPending(null)
        if (editing && activeVersionId) {
            listRules(editing.id, activeVersionId).then(setRules).catch(() => setRules([]))
        } else {
            setRules([])
        }
    }

    const confirmPublish = async () => {
        if (!editing || !activeVersionId) return
        try {
            await publishVersion(editing.id, activeVersionId)
            message.success('已发布，学生端同步最新版本')
            setPublishOpen(false)
            setEditing(null)
            reload()
        } catch (e) {
            message.error(getErrorMessage(e))
        }
    }

    const submitCreate = async () => {
        if (!draft.courseId || !draft.templateName.trim()) {
            message.warning('请选择课程并填写模板名称')
            return
        }
        try {
            await createTemplate({ courseId: draft.courseId, templateName: draft.templateName.trim(), description: draft.description || undefined })
            message.success('草稿已创建，请上传 .docx 模板文件解析规则')
            setCreateOpen(false)
            setDraft({ courseId: '', templateName: '', description: '' })
            reload()
        } catch (e) {
            message.error(getErrorMessage(e))
        }
    }

    const ruleColumns = (readOnly: boolean) => ([
        { title: '作用对象', dataIndex: 'targetSelector', width: 130,
            render: (v: string, r: RuleView, i: number) => readOnly
                ? (SELECTOR_LABEL[v] ?? v)
                : <Select size="small" value={v} style={{ width: '100%' }}
                    options={(SELECTORS_BY_TYPE[r.ruleType] ?? []).map((s) => ({ value: s, label: SELECTOR_LABEL[s] ?? s }))}
                    onChange={(nv) => patchRule(indexOfRule(r), { targetSelector: nv })} /> },
        // 验收 M-01-3：严重度/可修复由格式类型自动决定，不再作为教师编辑列
        { title: '期望要求', key: 'expect',
            render: (_: unknown, r: RuleView) => describeExpect(r) },
        ...(readOnly ? [] : [{ title: '操作', key: 'acts', width: 76,
            render: (_: unknown, r: RuleView) => (
                <Space size={0}>
                    <Popover
                        trigger="click" placement="left"
                        title={`${RULE_TYPE_LABEL[r.ruleType] ?? ''} · ${SELECTOR_LABEL[r.targetSelector] ?? r.targetSelector} 配置`}
                        content={<RuleJsonEditor rule={r} onCommit={(json) => patchRule(indexOfRule(r), { ruleConfigJson: json })} />}
                    >
                        <AppButton type="text" aria-label="编辑配置" icon={<EditOutlined />} />
                    </Popover>
                    <AppButton type="text" danger aria-label="删除规则" icon={<DeleteOutlined />}
                        onClick={() => setRules((prev) => prev.filter((x) => x.id !== r.id || x.ruleOrder !== r.ruleOrder))} />
                </Space>
            ) }]),
    ])
    const indexOfRule = (r: RuleView) => rules.indexOf(r)

    return (
        <div>
            <Space style={{ marginBottom: 16 }} wrap>
                <Select
                    allowClear
                    placeholder="按课程筛选"
                    style={{ width: 260 }}
                    value={courseFilter}
                    onChange={setCourseFilter}
                    options={courses.map((c) => ({ value: c.id, label: `${c.courseName}（${c.termCode}）` }))}
                />
                <AppButton onClick={() => setCreateOpen(true)}>新建模板</AppButton>
            </Space>

            <Table<TemplateView>
                rowKey="id"
                loading={loading}
                dataSource={items}
                pagination={{ current: page, pageSize: 20, total, onChange: setPage }}
                columns={[
                    { title: '模板名称', dataIndex: 'templateName' },
                    { title: '课程', dataIndex: 'courseName', render: (v: string | null) => v ?? '-' },
                    {
                        title: '状态', dataIndex: 'status',
                        render: (v: string) => <Tag color={STATUS_META[v]?.color}>{STATUS_META[v]?.label ?? v}</Tag>,
                    },
                    {
                        title: '当前版本', dataIndex: 'currentVersionNo',
                        render: (v: number | null, r) => (v ? `v${v}（${r.ruleCount ?? 0} 条规则）` : '未发布'),
                    },
                    { title: '更新时间', dataIndex: 'updateTime', render: (v: string | null) => v ?? '-' },
                    {
                        title: '操作', key: 'ops',
                        render: (_, r) => (
                            <Space>
                                <AppButton type="link" onClick={() => void openEditor(r)}>编辑</AppButton>
                                {r.status === 'P' && (
                                    <Popconfirm title="停用后学生不可再选用，历史绑定不受影响"
                                        onConfirm={() => changeTemplateStatus(r.id, 'A').then(reload).catch((e) => message.error(getErrorMessage(e)))}>
                                        <AppButton type="link" danger>停用</AppButton>
                                    </Popconfirm>
                                )}
                                {r.status === 'A' && (
                                    <AppButton type="link"
                                        onClick={() => changeTemplateStatus(r.id, 'P').then(reload).catch((e) => message.error(getErrorMessage(e)))}>
                                        重新启用
                                    </AppButton>
                                )}
                                {r.status === 'D' && (
                                    <Popconfirm title="删除草稿？"
                                        onConfirm={() => deleteTemplate(r.id).then(reload).catch((e) => message.error(getErrorMessage(e)))}>
                                        <AppButton type="link" danger>删除</AppButton>
                                    </Popconfirm>
                                )}
                            </Space>
                        ),
                    },
                ]}
            />

            <AppModal title="新建模板" open={createOpen} okText="确定"
                onOk={() => void submitCreate()} onCancel={() => setCreateOpen(false)}>
                <Space direction="vertical" style={{ width: '100%' }}>
                    <Select
                        placeholder="选择课程"
                        style={{ width: '100%' }}
                        value={draft.courseId || undefined}
                        onChange={(v) => setDraft((d) => ({ ...d, courseId: v }))}
                        options={courses.map((c) => ({ value: c.id, label: `${c.courseName}（${c.termCode}）` }))}
                    />
                    <Input placeholder="模板名称（如：C语言课程设计报告模板）" maxLength={200}
                        value={draft.templateName} onChange={(e) => setDraft((d) => ({ ...d, templateName: e.target.value }))} />
                    <Input.TextArea placeholder="模板说明（可选）" rows={2} maxLength={1000}
                        value={draft.description} onChange={(e) => setDraft((d) => ({ ...d, description: e.target.value }))} />
                </Space>
            </AppModal>

            <AppModal
                title={`编辑模板：${editing?.templateName ?? ''}`}
                open={!!editing}
                width={960}
                onCancel={() => setEditing(null)}
                footer={pending ? [
                    <AppButton key="confirm" type="primary" onClick={() => void confirmPending()}>确认保存为新版本</AppButton>,
                    <AppButton key="close" onClick={() => setEditing(null)}>关闭</AppButton>,
                ] : forkDraft ? [
                    <AppButton key="forksave" type="primary" onClick={() => void submitFork()}>另存为新版本</AppButton>,
                    <AppButton key="exfork" onClick={exitFork}>退出编辑（不保存）</AppButton>,
                    <AppButton key="close" onClick={() => setEditing(null)}>关闭</AppButton>,
                ] : [
                    <AppButton key="publish" disabled={!activeVersion || activeVersion.validationStatus !== 'VALID' || !!activeVersion?.publishedAt}
                        onClick={() => setPublishOpen(true)}>
                        发布确认
                    </AppButton>,
                    <AppButton key="save" type="primary" disabled={!editable} onClick={() => void saveRules()}>保存规则</AppButton>,
                    // 验收反馈：已发布版本不改原件——基于它编辑另存新版本
                    ...(activeVersion?.publishedAt
                        ? [<AppButton key="fork" onClick={enterFork}>基于此版本编辑另存新版本</AppButton>]
                        : []),
                    <AppButton key="close" onClick={() => setEditing(null)}>关闭</AppButton>,
                ]}
            >
                {editing && (
                    <Space direction="vertical" style={{ width: '100%' }} size="middle">
                        <Space wrap>
                            <Upload
                                accept=".docx"
                                showUploadList={false}
                                disabled={!!pending}
                                beforeUpload={(file) => {
                                    // 决议①：上传只做解析预览，不落版本；确认后才 createVersion
                                    previewTemplateRules(editing.id, file)
                                        .then((drafts) => {
                                            const withIds = drafts.map((d, i) => ({ ...d, id: `draft-${i}-${Date.now()}` }))
                                            setPending({ file, drafts: withIds })
                                            setRules(withIds)
                                            setRuleErrors([])
                                            message.success(withIds.length
                                                ? `解析出 ${withIds.length} 条规则，确认后才会生成新版本`
                                                : '未解析出规则，可手工添加后确认保存为新版本')
                                        })
                                        .catch((e) => message.error(getErrorMessage(e)))
                                    return false
                                }}
                            >
                                <AppButton>上传 .docx 并预览解析结果</AppButton>
                            </Upload>
                            <Select
                                style={{ width: 300 }}
                                placeholder="选择版本"
                                disabled={!!pending || !!forkDraft}
                                value={activeVersionId ?? undefined}
                                onChange={(v) => { setActiveVersionId(v); setRuleErrors([]) }}
                                options={versions.map((v) => ({
                                    value: v.id,
                                    label: `v${v.versionNo} ${VALIDATION_META[v.validationStatus]?.label ?? ''}${v.publishedAt ? '（已发布·只读）' : ''}`,
                                }))}
                            />
                        </Space>

                        {pending && (
                            <Alert type="info" showIcon
                                message={`预览草稿（未保存）：${pending.drafts.length} 条规则——可直接编辑，点「确认保存为新版本」才落版本；此期间不会修改任何已存在版本`}
                                action={<AppButton size="small" onClick={cancelPending}>取消预览</AppButton>} />
                        )}
                        {forkDraft && (
                            <Alert type="warning" showIcon
                                message={`正在基于已发布 v${forkDraft.versionNo} 编辑副本——原发布版本保持不变，点「另存为新版本」生成未发布的下一版；学生端不受影响`} />
                        )}
                        {activeVersion?.validationStatus === 'INVALID' && (
                            <Alert type="error" showIcon message="模板解析失败"
                                description={activeVersion.failureReason ?? '文件无法解析，可手工编辑规则后保存'} />
                        )}
                        {activeVersion?.publishedAt && (
                            <Alert type="info" showIcon message="已发布版本规则只读；变更请上传新版本或编辑最新未发布版本" />
                        )}

                        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 280px', gap: 16, alignItems: 'start' }}>
                            <div>
                                {/* P2-3：按 rule_type 分组展示/编辑；已发布版本同结构只读回显 */}
                                {groupRules(rules).map((g) => (
                                    <div key={g.type} style={{ marginBottom: 12 }}>
                                        <Tag color="blue">{RULE_TYPE_LABEL[g.type]}</Tag>
                                        <Table<RuleView>
                                            size="small"
                                            rowKey={(r) => `${r.ruleType}-${r.targetSelector}-${r.ruleOrder}`}
                                            dataSource={g.items}
                                            pagination={false}
                                            columns={ruleColumns(!inDraftMode) as never}
                                        />
                                    </div>
                                ))}
                                {inDraftMode && (
                                    <Badge count={rules.length}>
                                        <Dropdown
                                            menu={{
                                                items: RULE_TYPES.map((t) => ({ key: t, label: RULE_TYPE_LABEL[t] ?? t })),
                                                onClick: ({ key }) => setRules((prev) => [...prev, newRuleOfType(key)]),
                                            }}
                                        >
                                            <AppButton>添加规则</AppButton>
                                        </Dropdown>
                                    </Badge>
                                )}
                                {ruleErrors.length > 0 && (
                                    <Alert style={{ marginTop: 12 }} type="error" showIcon message="保存前请修正"
                                        description={<ul style={{ margin: 0, paddingLeft: 18 }}>{ruleErrors.map((e) => <li key={e}>{e}</li>)}</ul>} />
                                )}
                            </div>
                            {/* P2-3：右侧学生端效果静态示意（屏 10 契约） */}
                            <div>
                                <Tag>学生端效果预览（示意）</Tag>
                                <Table
                                    size="small"
                                    rowKey="targetSelector"
                                    pagination={false}
                                    dataSource={groupRules(rules).flatMap((g) => g.items.map((r) => ({
                                        targetSelector: `${RULE_TYPE_LABEL[g.type]}·${SELECTOR_LABEL[r.targetSelector] ?? r.targetSelector}`,
                                        requirement: describeExpect(r),
                                    })))}
                                    columns={[
                                        { title: '格式项', dataIndex: 'targetSelector' },
                                        { title: '要求', dataIndex: 'requirement' },
                                    ]}
                                />
                            </div>
                        </div>

                        {versions.length > 0 && (
                            <Collapse
                                items={[{
                                    key: 'history',
                                    label: `版本历史（${versions.length}）`,
                                    children: (
                                        <ul style={{ margin: 0, paddingLeft: 18 }}>
                                            {versions.map((v) => (
                                                <li key={v.id}>
                                                    v{v.versionNo}
                                                    <Tag color={VALIDATION_META[v.validationStatus]?.color}>
                                                        {VALIDATION_META[v.validationStatus]?.label}
                                                    </Tag>
                                                    {v.publishedAt ? `发布于 ${v.publishedAt}` : '未发布'}
                                                    {v.changeSummary ? ` · ${v.changeSummary}` : ''}
                                                </li>
                                            ))}
                                        </ul>
                                    ),
                                }]} />
                        )}
                    </Space>
                )}
            </AppModal>

            {/* 屏 11：发布确认——版本、适用范围、变更说明与影响提示 */}
            <AppModal title="发布模板确认" open={publishOpen} okText="确认发布"
                onOk={() => void confirmPublish()} onCancel={() => setPublishOpen(false)}>
                {editing && activeVersion && (
                    <Space direction="vertical">
                        <p>模板：<b>{editing.templateName}</b></p>
                        <p>版本：<b>v{activeVersion.versionNo}</b>（规则 {activeVersion.ruleCount} 条）</p>
                        <p>适用范围：课程「{editing.courseName ?? '-'}」的全部学生</p>
                        <p>变更说明：{activeVersion.changeSummary ?? '（上传版本时未填写）'}</p>
                        <Alert type="warning" showIcon
                            message="发布后将同步到关联课程学生端；历史已发布版本不受影响，文档绑定保持原版本（UT-13）。" />
                    </Space>
                )}
            </AppModal>
        </div>
    )
}
