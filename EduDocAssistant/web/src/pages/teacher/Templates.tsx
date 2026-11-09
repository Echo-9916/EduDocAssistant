// D-09：教师课程模板库（原型屏 09 列表 / 10 编辑（规则分组+学生端效果示意） / 11 发布确认）。
// 前端校验镜像后端 RuleConfig 不变量（保存闸），服务端仍是最终裁判；
// 历史版本发布后规则只读回显（UT-13），编辑面仅对未发布版本开放。
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Badge, Collapse, Input, Popconfirm, Select, Space, Table, Tag, Upload, message } from 'antd'
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

/** 保存前镜像校验；返回错误列表，空=可提交 */
export function validateRules(rules: RuleView[]): string[] {
    const errors: string[] = []
    for (const r of rules) {
        const tag = `${r.ruleType}/${r.targetSelector}`
        if (!(SELECTORS_BY_TYPE[r.ruleType] ?? []).includes(r.targetSelector)) {
            errors.push(`${tag}: targetSelector 不在词表`)
            continue
        }
        if (r.autoFixable === 'Y' && !FIXABLE_TYPES.includes(r.ruleType)) {
            errors.push(`${tag}: 该类型不在修复面，autoFixable 必须为 N`)
        }
        let config: Record<string, unknown>
        try {
            config = JSON.parse(r.ruleConfigJson)
        } catch {
            errors.push(`${tag}: ruleConfigJson 非法 JSON`)
            continue
        }
        const root = config as { expect?: Record<string, unknown>; tolerance?: Record<string, unknown>; fix?: Record<string, unknown> }
        const expect = root.expect ?? {}
        const fix = root.fix ?? {}
        const num = (v: unknown) => typeof v === 'number' && v > 0
        switch (r.ruleType) {
            case 'FONT':
                if (!expect.family && !expect.eastAsia) errors.push(`${tag}: 需 family 或 eastAsia`)
                break
            case 'SIZE':
                if (!num(expect.pt) || (expect.pt as number) > 100) errors.push(`${tag}: pt 需 (0,100]`)
                break
            case 'LINE_SPACING': {
                const fixed = expect.lineRule === 'exact' || expect.lineRule === 'atLeast'
                if (!num(expect.multiplier) && !fixed) {
                    errors.push(`${tag}: 需 multiplier 或 lineRule(exact|atLeast)+pt`)
                } else if (fixed && !num(expect.pt)) {
                    errors.push(`${tag}: 固定行距必须带 pt`)
                }
                break
            }
            case 'MARGIN': {
                const keys = ['top', 'bottom', 'left', 'right', 'width', 'height']
                for (const k of keys) {
                    if (expect[k] !== undefined && !num(expect[k])) errors.push(`${tag}: ${k} 必须为正数 mm`)
                }
                if (!keys.some((k) => num(expect[k]))) errors.push(`${tag}: 需至少一个正数边距/纸型键`)
                break
            }
            case 'CAPTION': {
                const pattern = typeof expect.pattern === 'string' && expect.pattern.trim() ? expect.pattern : null
                if (!pattern && expect.perChapter !== true) {
                    errors.push(`${tag}: 需非空 pattern 或 perChapter=true（空串视为未配置）`)
                }
                if (pattern) {
                    try { new RegExp(pattern) } catch { errors.push(`${tag}: pattern 不是合法正则`) }
                }
                break
            }
            case 'CODE':
                if (typeof expect.style !== 'string' || !expect.style) errors.push(`${tag}: 需声明 expect.style`)
                break
            case 'REFERENCE':
                if (typeof expect.standard !== 'string' || !expect.standard) errors.push(`${tag}: 需声明 expect.standard`)
                break
            case 'HEADER_FOOTER':
                if (expect.present !== true) errors.push(`${tag}: present 必须为 true（JSON 布尔）`)
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
            if (!okFix) errors.push(`${tag}: auto_fixable=Y 必须携带有效 fix`)
        }
    }
    return errors
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

const EMPTY_RULE: RuleView = {
    id: '', ruleType: 'FONT', targetSelector: 'HEADING_1', severity: 'BLOCKER',
    autoFixable: 'Y', ruleConfigJson: '{"expect":{},"fix":{}}', ruleOrder: 10,
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
        { title: '严重度', dataIndex: 'severity', width: 90, render: (v: string) => v },
        { title: '可修复', dataIndex: 'autoFixable', width: 70, render: (v: string) => v },
        { title: '期望要求', key: 'expect',
            render: (_: unknown, r: RuleView) => readOnly
                ? describeExpect(r)
                : <input data-testid="rule-json" style={{
                    width: '100%', fontFamily: 'monospace', fontSize: 12,
                    border: '1px solid #d9d9d9', borderRadius: 4, padding: '2px 6px',
                }} defaultValue={r.ruleConfigJson}
                    onBlur={(ev) => patchRule(indexOfRule(r), { ruleConfigJson: ev.target.value })} /> },
        ...(readOnly ? [] : [{ title: '', key: 'del', width: 40,
            render: (_: unknown, r: RuleView) => (
                <AppButton type="link" danger onClick={() => setRules((prev) => prev.filter((x) => x.id !== r.id || x.ruleOrder !== r.ruleOrder))}>删</AppButton>
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
                footer={[
                    <AppButton key="publish" disabled={!activeVersion || activeVersion.validationStatus !== 'VALID' || !!activeVersion?.publishedAt}
                        onClick={() => setPublishOpen(true)}>
                        发布确认
                    </AppButton>,
                    <AppButton key="save" type="primary" disabled={!editable} onClick={() => void saveRules()}>保存规则</AppButton>,
                    <AppButton key="close" onClick={() => setEditing(null)}>关闭</AppButton>,
                ]}
            >
                {editing && (
                    <Space direction="vertical" style={{ width: '100%' }} size="middle">
                        <Space wrap>
                            <Upload
                                accept=".docx"
                                showUploadList={false}
                                beforeUpload={(file) => {
                                    createVersion(editing.id, file)
                                        .then(async (v) => {
                                            message.success(`新版本 v${v.versionNo} ${v.validationStatus === 'VALID' ? '解析成功' : '解析未产出规则，可手工编辑'}`)
                                            setVersions(await listVersions(editing.id))
                                            setActiveVersionId(v.id)
                                        })
                                        .catch((e) => message.error(getErrorMessage(e)))
                                    return false
                                }}
                            >
                                <AppButton>上传 .docx 新版本并解析</AppButton>
                            </Upload>
                            <Select
                                style={{ width: 300 }}
                                placeholder="选择版本"
                                value={activeVersionId ?? undefined}
                                onChange={(v) => { setActiveVersionId(v); setRuleErrors([]) }}
                                options={versions.map((v) => ({
                                    value: v.id,
                                    label: `v${v.versionNo} ${VALIDATION_META[v.validationStatus]?.label ?? ''}${v.publishedAt ? '（已发布·只读）' : ''}`,
                                }))}
                            />
                        </Space>

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
                                            columns={ruleColumns(!editable) as never}
                                        />
                                    </div>
                                ))}
                                {editable && (
                                    <Badge count={rules.length}>
                                        <AppButton onClick={() => setRules((prev) => [...prev, { ...EMPTY_RULE, id: `local-${prev.length}-${Date.now()}` }])}>
                                            添加规则
                                        </AppButton>
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
