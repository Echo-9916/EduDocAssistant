// D-08：模板域 API（契约=后端 TemplateController/TemplateVersionController，docs/test/FormatTest.md）。
import { del, get, patch, post, put } from './http'
import type { PageResult } from './types'

export interface TemplateView {
  id: string
  courseId: string | null
  courseName: string | null
  templateName: string
  description: string | null
  ownerId: string
  status: 'D' | 'P' | 'A'
  currentVersionId: string | null
  currentVersionNo: number | null
  ruleCount: number | null
  createTime: string | null
  updateTime: string | null
}

export interface TemplateVersionView {
  id: string
  templateId: string
  versionNo: number
  validationStatus: 'PENDING' | 'VALID' | 'INVALID'
  publishedAt: string | null
  changeSummary: string | null
  sourceObjectId: string | null
  ruleCount: number
  failureReason: string | null
}

/** 规则行（ruleConfigJson 为字符串契约，编辑面解析失败即拒绝保存） */
export interface RuleView {
  id: string
  ruleType: 'FONT' | 'SIZE' | 'LINE_SPACING' | 'MARGIN' | 'CAPTION' | 'CODE' | 'REFERENCE' | 'HEADER_FOOTER'
  targetSelector: string
  severity: 'BLOCKER' | 'WARNING' | 'INFO'
  autoFixable: 'Y' | 'N'
  ruleConfigJson: string
  ruleOrder: number
}

export interface ProvisionView {
  documentId: string
  versionId: string
  templateVersionId: string
}

export const listTemplates = (params: {
  courseId?: string
  status?: string
  page?: number
  pageSize?: number
}) => get<PageResult<TemplateView>>('/templates', { params })

export const createTemplate = (command: { courseId: string; templateName: string; description?: string }) =>
  post<TemplateView>('/templates', command)

/** 1107 阶段审查 P1-1：后端为 @PatchMapping，方法必须用 PATCH（曾因 POST/PATCH 漂移导致 405） */
export const changeTemplateStatus = (templateId: string, status: 'P' | 'A') =>
  patch<void>(`/templates/${templateId}/status`, { status })

export const deleteTemplate = (templateId: string) => del<void>(`/templates/${templateId}`)

export const listVersions = (templateId: string) =>
  get<TemplateVersionView[]>(`/templates/${templateId}/versions`)

/** 上传并解析新版本（multipart；file 必填=后端 D-02 契约） */
export const createVersion = (templateId: string, file: File, changeSummary?: string) => {
  const form = new FormData()
  form.append('file', file)
  return post<TemplateVersionView>(
    `/templates/${templateId}/versions${changeSummary ? `?changeSummary=${encodeURIComponent(changeSummary)}` : ''}`,
    form,
  )
}

export const listRules = (templateId: string, versionId: string) =>
  get<RuleView[]>(`/templates/${templateId}/versions/${versionId}/rules`)

export const replaceRules = (
  templateId: string,
  versionId: string,
  rules: Omit<RuleView, 'id'>[],
) => put<void>(`/templates/${templateId}/versions/${versionId}/rules`, rules)

export const publishVersion = (templateId: string, versionId: string) =>
  post<void>(`/templates/${templateId}/versions/${versionId}/publish`)

/** 学生只读要求页数据源（未发布 40901、门外 40401） */
export const currentRules = (templateId: string) =>
  get<RuleView[]>(`/templates/${templateId}/current-rules`)

export const provisionFromTemplate = (templateId: string, title: string, docType: string) =>
  post<ProvisionView>(`/templates/${templateId}/documents`, { title, docType })

/** 规则 JSON 契约的展示映射（屏 10/屏 11 只读要求表用） */
export const RULE_TYPE_LABEL: Record<RuleView['ruleType'], string> = {
  FONT: '字体',
  SIZE: '字号',
  LINE_SPACING: '行距',
  MARGIN: '页边距/纸型',
  CAPTION: '图表题注',
  CODE: '代码块',
  REFERENCE: '参考文献',
  HEADER_FOOTER: '页眉页脚',
}

export const SELECTOR_LABEL: Record<string, string> = {
  HEADING_1: '一级标题',
  HEADING_2: '二级标题',
  HEADING_3: '三级标题',
  BODY: '正文',
  PAGE: '页面',
  FIGURE_CAPTION: '图题注',
  TABLE_CAPTION: '表题注',
  CODE_BLOCK: '代码块',
  REFERENCE: '参考文献',
  HEADER: '页眉',
  FOOTER: '页脚',
}

/** 学生只读页的期望值口语化描述；解析失败回退原始 JSON 摘要 */
export function describeExpect(rule: RuleView): string {
  try {
    const expect = JSON.parse(rule.ruleConfigJson).expect ?? {}
    const parts: string[] = []
    if (expect.family) parts.push(`西文 ${expect.family}`)
    if (expect.eastAsia) parts.push(`中文 ${expect.eastAsia}`)
    // 固定行距契约复用 pt 键（=磅值），此时不作字号展示
    if (expect.pt && !expect.lineRule && !expect.multiplier) parts.push(`${expect.pt}pt`)
    if (expect.multiplier) parts.push(`${expect.multiplier} 倍行距`)
    if (expect.lineRule) parts.push(`固定行距 ${expect.pt ?? ''} 磅`.trim())
    if (expect.top || expect.left) {
      parts.push(`上下 ${expect.top ?? '-'}/${expect.bottom ?? '-'} 左右 ${expect.left ?? '-'}/${expect.right ?? '-'}mm`)
    }
    if (expect.width) parts.push(`纸张 ${expect.width}×${expect.height}mm`)
    if (expect.perChapter) parts.push('按章节编号（如 图2-1）')
    if (expect.present) parts.push('必须存在')
    if (expect.pattern) parts.push(`形式 ${expect.pattern}`)
    if (expect.standard) parts.push(expect.standard)
    if (expect.style) parts.push(`样式 ${expect.style}`)
    return parts.length ? parts.join('，') : JSON.stringify(expect)
  } catch {
    return rule.ruleConfigJson
  }
}
