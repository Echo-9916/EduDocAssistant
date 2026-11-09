// 智能处理门面演示数据（学生 06-10 / 教师 05-08 屏）。
// 已联调的 03 智能处理切片走真实 /api/v1（文档预览页内），本文件只服务「智能处理」分区演示页。
// 演示文档沿用原型：《Web开发课程设计报告_张同学_v3.docx》 · 《Web开发技术》。

import { delay, nextId, update } from '../store'

export const aiDoc = {
  title: 'Web开发课程设计报告_张同学_v3.docx',
  course: 'Web开发技术',
  pages: 24,
  version: 'v6',
}

/* ---------- 摘要（学生 06 / 教师 05） ---------- */

export type SummaryLength = 'short' | 'standard' | 'detailed'
export type SummaryFocus = 'structure' | 'method' | 'acceptance'

export const summaryLengthLabel: Record<SummaryLength, string> = {
  short: '精简',
  standard: '标准',
  detailed: '详细',
}

export const summaryFocusLabel: Record<SummaryFocus, string> = {
  structure: '结构与结论',
  method: '方法与实践',
  acceptance: '验收与风险',
}

export type SummaryBlock = {
  id: string
  section: string
  page: string
  /** 摘要句：长度决定句数（精简 1 句 / 标准 2 句 / 详细 3 句），侧重决定第二句口径。 */
  sentences: string[]
  source: string
}

const SUMMARY_SEED: {
  id: string
  section: string
  page: string
  base: string
  focus: Record<SummaryFocus, string>
  detail: string
  source: string
}[] = [
  {
    id: 'SUM-1',
    section: '1 摘要与目标',
    page: '第 1 页',
    base: '报告围绕校园智能文档助手，说明选题背景、课程目标与交付范围。',
    focus: {
      structure: '全文按「需求分析 → 系统设计 → 系统测试 → 验收结论」组织，章节完整。',
      method: '采用前后端分离与异步任务方法，覆盖文档接入、智能处理与格式校验。',
      acceptance: '交付目标要求提交前格式阻断问题清零，并保留完整版本记录。',
    },
    detail: '报告约定小组分工与里程碑：第 8 周完成原型，第 12 周完成联调，第 16 周提交验收。',
    source:
      '本课程设计面向校园文档场景，目标是帮助学生完成课程报告的撰写、格式校验与提交。系统覆盖文档接入、智能处理、格式与模板、协作与版本四个环节。',
  },
  {
    id: 'SUM-2',
    section: '2 需求分析',
    page: '第 2 页',
    base: '需求覆盖 Word/PDF 上传解析、摘要问答、格式校验与小组协作四类核心场景。',
    focus: {
      structure: '需求按功能性需求与非功能性需求分层陈述，并逐条编号便于追溯。',
      method: '通过用例图与业务流程图描述学生提交、教师批改两条主流程。',
      acceptance: '明确解析失败可重试、越权访问拒绝并记录两条验收约束。',
    },
    detail: '非功能性需求给出量化指标：解析成功率不低于 98%，单文档处理不超过 5 分钟。',
    source:
      '系统需支持 Word 与 PDF 上传，解析失败可重试；摘要、要点、问答与润色结果均需可定位原文；越权访问应被拒绝并写入审计日志。',
  },
  {
    id: 'SUM-3',
    section: '3 系统设计',
    page: '第 5 页',
    base: '系统采用前端、服务端与文档处理服务分层设计，任务状态写入文档元数据。',
    focus: {
      structure: '设计章节按总体架构、权限校验、异步任务、版本管理四小节展开。',
      method: '文档解析、智能摘要与格式检查通过异步任务执行，避免长请求阻塞。',
      acceptance: '所有文档访问请求在服务端完成课程身份与共享范围校验，风险可控。',
    },
    detail: '对象存储分层保存原始文件、解析结果与导出文件，避免版本覆盖。',
    source:
      '系统采用前后端分离结构。前端负责工作台、文档预览与交互状态；服务层承担权限验证、任务调度和格式处理。文档数据与处理结果通过对象存储分层保存。',
  },
  {
    id: 'SUM-4',
    section: '4 测试与验收',
    page: '第 15 页',
    base: '测试覆盖文档解析、格式校验与并发访问三类场景，共执行 42 个用例，全部通过。',
    focus: {
      structure: '测试章节按用例清单、执行记录、缺陷跟踪、验收结论四部分组织。',
      method: '采用接口自动化与页面手工验证结合的方法，保留执行截图与日志。',
      acceptance: '验收指标包括解析成功率、阻断问题清零、问答引用可定位、版本恢复可追溯。',
    },
    detail: '遗留 2 项低风险警告：目录页码偏差与图 3-2 题注格式，均已列入修复计划。',
    source:
      '格式校验失败后需先修复阻断问题再重新提交；恢复历史版本会把所选版本复制为新的当前版本，原有版本仍保留在版本记录中。',
  },
]

export function summarize(length: SummaryLength, focus: SummaryFocus) {
  const blocks = SUMMARY_SEED.map((s) => ({
    id: s.id,
    section: s.section,
    page: s.page,
    source: s.source,
    sentences:
      length === 'short'
        ? [s.base]
        : length === 'standard'
          ? [s.base, s.focus[focus]]
          : [s.base, s.focus[focus], s.detail],
  }))
  return delay(blocks, 420)
}

/* ---------- 要点提取（学生 07 / 教师 06） ---------- */

export type Keypoint = { id: string; title: string; desc: string; section: string; page: string }

const KEYPOINT_SEED: Keypoint[] = [
  { id: 'KP-1', title: '统一文档接入', desc: '支持 Word 与 PDF 上传，解析失败可重试。', section: '2 需求分析', page: '第 2 页' },
  { id: 'KP-2', title: '智能处理闭环', desc: '摘要、要点、问答与润色均可定位原文。', section: '3 系统设计', page: '第 5 页' },
  { id: 'KP-3', title: '前置格式校验', desc: '阻断问题清零后才可提交课程作业。', section: '3 系统设计', page: '第 12 页' },
  { id: 'KP-4', title: '协作与版本', desc: '保存快照、对比差异并恢复为新版本。', section: '3 系统设计', page: '第 18 页' },
  { id: 'KP-5', title: '权限与审计', desc: '越权访问拒绝并记录，下载仅本人可见。', section: '4 测试与验收', page: '第 22 页' },
]

export function listKeypoints() {
  return delay(KEYPOINT_SEED.map((k) => ({ ...k })), 300)
}

/* ---------- 文档问答与无依据拒答（学生 08/09 / 教师 07） ---------- */

export type QaTurn = {
  id: string
  role: 'user' | 'assistant'
  text: string
  /** 拒答态：明确说明文档内无依据、不编造（需求 5.2.3 硬口径）。 */
  refusal?: boolean
  citations?: string[]
  tip?: string
}

/** 无依据示例问题：演示拒答态用。 */
export const unanswerableQuestion = '报告里有没有与其他学校产品的横向对比数据？'

const REFUSAL_KEYS = ['横向对比', '其他学校', '对比数据', '排名', '哪个学校最好']

const QA_KB: { keys: string[]; text: string; citations: string[] }[] = [
  {
    keys: ['检查项', '包含哪些'],
    text: '格式校验覆盖字体、字号、行距、页边距、题注、代码块与参考文献七类检查项，其中阻断问题必须清零后才能提交。',
    citations: ['第 12 页 · 3.4 格式规范校验'],
  },
  {
    keys: ['格式', '校验', '提交'],
    text: '先修复阻断问题，再重新提交。自动修复会生成新版本并重新执行完整校验；警告项可以保留，但需要在提交确认页查看提示。',
    citations: ['第 12 页 · 3.4 格式规范校验'],
  },
  {
    keys: ['版本', '恢复', '覆盖'],
    text: '不会覆盖。恢复操作会把所选历史版本复制为新的当前版本，原有版本仍会保留在版本记录中。',
    citations: ['第 18 页 · 3.5 版本管理'],
  },
  {
    keys: ['验收', '指标', '测试'],
    text: '报告列出的验收指标包括：文档解析成功率、格式阻断问题清零、问答引用可定位、版本恢复可追溯四项。',
    citations: ['第 15 页 · 4 系统测试', '第 22 页 · 5 验收结论'],
  },
]

/** 推荐问题（有依据，可命中回答）。 */
export const suggestedQuestions = [
  '格式校验包含哪些检查项？',
  '恢复历史版本会覆盖当前版本吗？',
  '报告列出了哪些验收指标？',
]

let qaThread: QaTurn[] = [
  { id: 'QA-S1', role: 'user', text: '格式校验失败后，如何继续提交课程作业？' },
  {
    id: 'QA-S2',
    role: 'assistant',
    text: QA_KB[1].text,
    citations: QA_KB[1].citations,
  },
  { id: 'QA-S3', role: 'user', text: '恢复历史版本会覆盖当前版本吗？' },
  {
    id: 'QA-S4',
    role: 'assistant',
    text: QA_KB[2].text,
    citations: QA_KB[2].citations,
  },
]

export function listQa() {
  return delay(qaThread.map((t) => ({ ...t })), 260)
}

export function askQa(question: string) {
  const q = question.trim()
  const hit = QA_KB.find((k) => k.keys.some((key) => q.includes(key)))
  const noEvidence = REFUSAL_KEYS.some((key) => q.includes(key))
  const answer: QaTurn =
    !hit || noEvidence
      ? {
          id: nextId('QA'),
          role: 'assistant',
          refusal: true,
          text: noEvidence
            ? '当前文档只介绍了本系统的需求、设计与验收要求，没有提供其他学校产品的横向对比数据，文档内无依据可引用。系统不会编造文档之外的内容。'
            : `未在《${aiDoc.title}》中找到与「${q}」相关的可引用内容。为避免编造，系统不作推测性回答；可就文档的需求、设计、测试或验收章节换一种问法。`,
          tip: '可以换一种有原文依据的问法，例如：「报告列出了哪些验收指标？」',
        }
      : { id: nextId('QA'), role: 'assistant', text: hit.text, citations: hit.citations }
  qaThread = [...qaThread, { id: nextId('QA'), role: 'user', text: q }, answer]
  return delay(qaThread.map((t) => ({ ...t })), 300)
}

export function clearQa() {
  qaThread = []
  return delay(true, 120)
}

/* ---------- 文本润色（学生 10 / 教师 08） ---------- */

export type PolishStatus = 'pending' | 'adopted' | 'kept'

export const polishStatusLabel: Record<PolishStatus, string> = {
  pending: '待处理',
  adopted: '已采纳',
  kept: '保留原文',
}

export type PolishPara = {
  id: string
  section: string
  original: string
  polished: string
  note: string
  status: PolishStatus
}

const POLISH_SEED: PolishPara[] = [
  {
    id: 'PL-1',
    section: '3.1 总体架构 · 第 6 页',
    original:
      '系统需要在文档接入环节完成格式识别与保留。由于格式调整占用了较多时间，系统通过模板与自动校验减少重复的格式调整工作。',
    polished:
      '系统在文档接入阶段同步识别并保留格式。针对格式调整耗时的问题，系统通过课程模板与自动校验减少重复操作，使学生能够将更多时间用于内容撰写。',
    note: '表达更清晰：明确「文档接入阶段」与「课程模板」的关系，保持原意。',
    status: 'pending',
  },
  {
    id: 'PL-2',
    section: '3.2 权限校验 · 第 9 页',
    original: '系统要对文档访问进行权限验证。用户的请求由服务端处理，记录日志。',
    polished:
      '所有文档访问请求在服务端完成课程身份与共享范围校验，越权访问将被拒绝并写入审计日志。',
    note: '补全「越权拒绝 + 审计」因果链，与 4.2 节测试结果一致。',
    status: 'pending',
  },
  {
    id: 'PL-3',
    section: '4.1 测试说明 · 第 15 页',
    original: '我们做了很多测试，系统基本没有问题，性能也还可以。',
    polished:
      '测试覆盖文档解析、格式校验与并发访问三类场景，共执行 42 个用例，全部通过；解析平均耗时 6 秒，满足课程验收要求。',
    note: '将模糊表述替换为可量化结论，数据取自第 15 页测试记录。',
    status: 'pending',
  },
]

export function listPolish() {
  return delay(POLISH_SEED.map((p) => ({ ...p })), 320)
}

/** 采纳/保留原文：写回内存数组，返回最新列表。 */
export function setPolishStatus(id: string, status: PolishStatus) {
  update(POLISH_SEED, (p) => p.id === id, { status })
  return delay(POLISH_SEED.map((p) => ({ ...p })), 150)
}
