import type { ImportRow } from '../../api/coursework'

const HEADER_ALIASES = {
  accountNo: ['学号', '账号', 'accountno', 'studentid', 'username'],
  name: ['姓名', 'name', 'displayname'],
  group: ['课程组', '组别', 'group', 'groupname'],
}

/** 简单 CSV 行拆分：支持双引号包裹、字段内逗号和 "" 转义。 */
function splitLine(line: string): string[] {
  const cells: string[] = []
  let current = ''
  let quoted = false
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i]
    if (ch === '"') {
      if (quoted && line[i + 1] === '"') {
        current += '"'
        i += 1
      } else {
        quoted = !quoted
      }
    } else if (ch === ',' && !quoted) {
      cells.push(current)
      current = ''
    } else {
      current += ch
    }
  }
  cells.push(current)
  return cells
}

/**
 * 解析课程成员 CSV。首行包含「学号/姓名/课程组」时按列头映射，否则按 学号,姓名,课程组 顺序。
 * 空学号行跳过；同一学号只保留第一次出现。XLSX 不在此处理。
 */
export function parseMemberCsv(text: string): ImportRow[] {
  const lines = text.replace(/^﻿/, '').split(/\r?\n/).filter((line) => line.trim().length > 0)
  if (lines.length === 0) {
    return []
  }
  const header = splitLine(lines[0]).map((cell) => cell.trim().toLowerCase())
  const findColumn = (aliases: string[]) =>
    header.findIndex((cell) => aliases.some((alias) => cell === alias.toLowerCase()))
  const accountIndex = findColumn(HEADER_ALIASES.accountNo)
  const nameIndex = findColumn(HEADER_ALIASES.name)
  const groupIndex = findColumn(HEADER_ALIASES.group)
  const hasHeader = accountIndex >= 0 || nameIndex >= 0
  const dataLines = hasHeader ? lines.slice(1) : lines
  const seen = new Set<string>()
  const rows: ImportRow[] = []
  for (const line of dataLines) {
    const cells = splitLine(line)
    const accountNo = (cells[hasHeader ? accountIndex : 0] ?? '').trim()
    if (!accountNo || seen.has(accountNo)) {
      continue
    }
    seen.add(accountNo)
    const name = (cells[hasHeader ? nameIndex : 1] ?? '').trim()
    if (!name) {
      continue
    }
    const group = (cells[hasHeader && groupIndex >= 0 ? groupIndex : 2] ?? '').trim()
    rows.push({ accountNo, name, group: group || null })
  }
  return rows
}