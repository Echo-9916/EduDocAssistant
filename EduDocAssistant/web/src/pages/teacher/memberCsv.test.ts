import { describe, expect, it } from 'vitest'
import { parseMemberCsv } from './memberCsv'

describe('parseMemberCsv', () => {
  it('解析中文表头、BOM、CRLF 与带逗号的引号字段', () => {
    const csv = '﻿学号,姓名,课程组\r\nstudent,张同学,"演示组,一组"\r\n2023001,陈同学,课程组 3\r\n'
    expect(parseMemberCsv(csv)).toEqual([
      { accountNo: 'student', name: '张同学', group: '演示组,一组' },
      { accountNo: '2023001', name: '陈同学', group: '课程组 3' },
    ])
  })

  it('支持英文表头', () => {
    const csv = 'accountNo,name,group\nstudent,Zhang,demo\n'
    expect(parseMemberCsv(csv)).toEqual([{ accountNo: 'student', name: 'Zhang', group: 'demo' }])
  })

  it('无表头按 学号,姓名,课程组 顺序解析', () => {
    const csv = 'student,张同学,演示组\n2023002,刘同学,课程组 3\n'
    expect(parseMemberCsv(csv)).toEqual([
      { accountNo: 'student', name: '张同学', group: '演示组' },
      { accountNo: '2023002', name: '刘同学', group: '课程组 3' },
    ])
  })

  it('跳过空行、空学号、空姓名与重复学号', () => {
    const csv = '学号,姓名,课程组\n\nstudent,张同学,演示组\nstudent,张同学,重复\n,无学号,组\n2023003,,组\n'
    expect(parseMemberCsv(csv)).toEqual([{ accountNo: 'student', name: '张同学', group: '演示组' }])
  })
})